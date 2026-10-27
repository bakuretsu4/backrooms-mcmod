package com.rickr.backrooms.client.intro;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.network.IntroCutscenePayload;
import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every move between places, filmed: noclipping out of reality into Level 0 and every exit from
 * one floor to the next. The picture fails like a video stream that has lost its keyframes
 * ("datamoshing"), and the old place smears and bleeds into the new one with no cut.
 * <ol>
 *   <li><b>Lag</b> (server sends {@code FREEZE}, ~0.5 s): the picture stutters, drops frames and
 *       crumbles into low-bitrate blocks. If you are lying in a bed, the view sinks down through
 *       it (through the mattress, the floor, the ground) as it does.</li>
 *   <li><b>Held</b>: the server moves you. The last broken frame stays on screen while
 *       the new world loads (no "Loading terrain" screen, no black).</li>
 *   <li><b>Mosh</b> (~2 s): the old picture is not replaced. Its blocks are dragged along with your
 *       camera's motion (the view drifts a little by itself; looking around smears it more),
 *       the new place's edges are added on top of it, and more and more blocks are coded fresh
 *       from it, until it snaps clean.</li>
 * </ol>
 * It all happens to the finished picture right after the world is drawn, before the camcorder
 * filter, so the tape look films it like everything else. The steps run at a fixed, choppy
 * "decoder" rate (about 20 a second, with dropped frames) whatever your frame rate, which is what
 * makes it feel like lag. The shader is {@code shaders/core/datamosh}.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class NoclipTransition {
    private static final Logger LOGGER = LoggerFactory.getLogger("backrooms/noclip");

    /** Lag stage length (the server waits {@code Travel.MOSH_TICKS} before moving you). */
    private static final long LAG_MS = 500;
    private static final long MOSH_MS = 2000;
    /** Decoder steps: one every this many ms, sometimes twice as long (a dropped frame). */
    private static final long STEP_MS = 50;
    private static final float DROP_CHANCE = 0.15f;
    /** Held frame: give up waiting for the new world after this, and start anyway. */
    private static final long WAIT_TIMEOUT_MS = 4000;
    /** The new world must have drawn this many chunk sections before the mosh starts. */
    private static final int MIN_SECTIONS = 20;
    /** If the server never moves us (the noclip was cancelled), give the picture back after this. */
    private static final long LAG_TIMEOUT_MS = 6000;
    /** The view drifts this much each tick during the mosh (degrees): you look around as you arrive. */
    private static final float DRIFT_DEG = 0.7f;
    /** Lying in a bed: the view sinks this far (blocks) over this long, speeding up like a fall. */
    private static final double SINK_DEPTH = 3.0;
    private static final long SINK_MS = 700;

    private enum Stage { NONE, LAG, HELD, MOSH }

    private static final RandomSource RANDOM = RandomSource.create();
    private static Stage stage = Stage.NONE;
    private static long seenPacketMs;
    private static long stageStartMs;
    private static long nextStepMs;
    private static int steps;
    /** First mosh step: Level 0's first frame is also its "previous" frame (no residual yet). */
    private static boolean prime;
    private static float lastYaw;
    private static float lastPitch;
    /**
     * The world the lag started in. The game keeps drawing behind the "Loading terrain" screen, so
     * the moment this changes we stop sampling: one more step would film the new, still empty world
     * and wipe out the frame we hold.
     */
    @Nullable
    private static ClientLevel lagLevel;
    /**
     * Sinking through the bed: we film from this client-only entity (a Marker has no model)
     * instead of your player, so the view can go down through the mattress while you lie still.
     */
    @Nullable
    private static Marker sinkCamera;
    private static Vec3 sinkFrom = Vec3.ZERO;
    private static float sinkYaw;
    private static float sinkPitch;

    @Nullable
    private static ShaderInstance shader;
    // The decoder's output (ping-pong) and the game's frames now / at the previous step.
    @Nullable
    private static TextureTarget out;
    @Nullable
    private static TextureTarget outNext;
    @Nullable
    private static TextureTarget scene;
    @Nullable
    private static TextureTarget prevScene;
    @Nullable
    private static VertexBuffer screenQuad;

    private NoclipTransition() {}

    /** Any part of the transition is on screen. */
    public static boolean active() {
        return stage != Stage.NONE;
    }

    /** Mod bus: the datamosh shader. If it fails to load, the noclip still works, just with a plain cut. */
    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        shader = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Backrooms.id("datamosh"),
                    DefaultVertexFormat.POSITION), loaded -> shader = loaded);
        } catch (Exception e) {
            LOGGER.error("[{}] Datamosh shader failed to load; noclipping will cut without it", Backrooms.MOD_ID, e);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        long packetMs = CameraClientState.introStartMs;
        if (packetMs != 0 && packetMs != seenPacketMs) {
            seenPacketMs = packetMs;
            if (CameraClientState.introPhase == IntroCutscenePayload.FREEZE && shader != null) {
                begin(Stage.LAG, now);
                lagLevel = mc.level;
                steps = 0;
                if (mc.player != null && mc.player.isSleeping()) {
                    startSink(mc);
                }
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.INTRO_GLITCH.get(), 1.0f, 1.0f));
            } else if (CameraClientState.introPhase == IntroCutscenePayload.ARRIVED && stage != Stage.NONE) {
                begin(Stage.HELD, now);
            }
        }
        long t = now - stageStartMs;
        switch (stage) {
            case LAG -> {
                if (t > LAG_TIMEOUT_MS) {
                    stage = Stage.NONE;
                    stopSink(mc);
                }
            }
            case HELD -> {
                boolean ready = mc.screen == null && mc.level != null && mc.levelRenderer.countRenderedSections() >= MIN_SECTIONS;
                if (ready || t > WAIT_TIMEOUT_MS) {
                    begin(Stage.MOSH, now);
                    prime = true;
                }
            }
            case MOSH -> {
                if (t >= MOSH_MS) {
                    stage = Stage.NONE;
                } else if (mc.player != null && !mc.isPaused()) {
                    mc.player.setYRot(mc.player.getYRot() + DRIFT_DEG);
                }
            }
            case NONE -> { }
        }
        if (stage == Stage.NONE && out != null) {
            free(); // give the four screen-sized buffers back
            lagLevel = null;
        }
    }

    private static void begin(Stage next, long now) {
        stage = next;
        stageStartMs = now;
        nextStepMs = 0;
    }

    /** After the world (and its water) is drawn, before the hand and the camcorder filter. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (stage == Stage.NONE || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || shader == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        boolean fresh = ensureTargets(main);
        long now = System.currentTimeMillis();
        if (stage == Stage.LAG && mc.level != lagLevel) {
            begin(Stage.HELD, now); // we've been moved: hold the last broken frame from here
        }
        if (stage != Stage.LAG) {
            stopSink(mc); // in the new world you are on your feet: back to your own eyes
        }
        if (fresh) {
            copy(main, out); // the first frame we hold is the real world as it is
            copy(main, prevScene);
            remember(event.getCamera());
        }
        if (stage != Stage.HELD && now >= nextStepMs) {
            step(mc, main, event.getCamera(), now);
            nextStepMs = now + (RANDOM.nextFloat() < DROP_CHANCE ? STEP_MS * 2 : STEP_MS)
                    + (stage == Stage.LAG ? RANDOM.nextInt(60) : 0);
        }
        copy(out, main); // between steps the picture simply stays: that's the lag
        main.bindWrite(false);
    }

    private static void step(Minecraft mc, RenderTarget main, Camera camera, long now) {
        copy(main, scene);
        if (prime) {
            copy(main, prevScene);
            prime = false;
        }
        float t = (now - stageStartMs) / (float) (stage == Stage.LAG ? LAG_MS : MOSH_MS);
        // How far the picture moved since the last step, in pixels, from the camera's turning.
        float vfov = mc.options.fov().get().floatValue();
        float aspect = main.width / (float) main.height;
        float hfov = (float) Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians(vfov / 2.0)) * aspect));
        float dYaw = Mth.wrapDegrees(camera.getYRot() - lastYaw);
        float dPitch = camera.getXRot() - lastPitch;
        remember(camera);

        ShaderInstance s = shader;
        s.setSampler("PrevOut", out);
        s.setSampler("Scene", scene);
        s.setSampler("PrevScene", prevScene);
        s.safeGetUniform("ScreenSize").set((float) main.width, (float) main.height);
        s.safeGetUniform("Mode").set(stage == Stage.LAG ? 0.0f : 1.0f);
        s.safeGetUniform("Progress").set(Mth.clamp(t, 0.0f, 1.0f));
        s.safeGetUniform("Seed").set((float) steps++);
        s.safeGetUniform("Shift").set(-dYaw * main.width / hfov, dPitch * main.height / vfov);
        s.safeGetUniform("BlockSize").set((float) Math.max(16, Math.round(main.height / 30.0f)));

        outNext.bindWrite(true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        screenQuad.bind();
        screenQuad.drawWithShader(new Matrix4f(), new Matrix4f(), s);
        VertexBuffer.unbind();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        TextureTarget swap = out;
        out = outNext;
        outNext = swap;
        swap = prevScene;
        prevScene = scene;
        scene = swap;
    }

    private static void remember(Camera camera) {
        lastYaw = camera.getYRot();
        lastPitch = camera.getXRot();
    }

    private static void startSink(Minecraft mc) {
        Camera camera = mc.gameRenderer.getMainCamera();
        sinkFrom = camera.getPosition();
        sinkYaw = camera.getYRot();
        sinkPitch = camera.getXRot();
        sinkCamera = new Marker(EntityType.MARKER, mc.level);
        placeSinkCamera(0.0);
        mc.setCameraEntity(sinkCamera);
    }

    private static void stopSink(Minecraft mc) {
        if (sinkCamera == null) {
            return;
        }
        if (mc.player != null && mc.getCameraEntity() == sinkCamera) {
            mc.setCameraEntity(mc.player);
        }
        sinkCamera = null;
    }

    /** The sinking view this frame; the same value for "last tick" too, so it doesn't smear. */
    private static void placeSinkCamera(double down) {
        double y = sinkFrom.y - down;
        sinkCamera.setPos(sinkFrom.x, y, sinkFrom.z);
        sinkCamera.xo = sinkCamera.xOld = sinkFrom.x;
        sinkCamera.yo = sinkCamera.yOld = y;
        sinkCamera.zo = sinkCamera.zOld = sinkFrom.z;
        sinkCamera.setYRot(sinkYaw);
        sinkCamera.yRotO = sinkYaw;
        sinkCamera.setXRot(sinkPitch);
        sinkCamera.xRotO = sinkPitch;
    }

    /** Every frame: sink a little further, speeding up (a fall, not a lift). */
    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        if (sinkCamera == null || stage != Stage.LAG) {
            return;
        }
        double t = Mth.clamp((System.currentTimeMillis() - stageStartMs) / (double) SINK_MS, 0.0, 1.0);
        placeSinkCamera(SINK_DEPTH * t * t);
    }

    /** While we film from the sinking camera, the game would draw your sleeping body in front of it. */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (sinkCamera != null && event.getEntity() == Minecraft.getInstance().player) {
            event.setCanceled(true);
        }
    }

    /** (Re)creates the buffers at the screen's size; true when they are new (nothing in them yet). */
    private static boolean ensureTargets(RenderTarget main) {
        if (out != null && out.width == main.width && out.height == main.height) {
            return false;
        }
        free();
        out = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        outNext = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        scene = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        prevScene = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        for (TextureTarget target : new TextureTarget[] {out, outNext, scene, prevScene}) {
            target.setFilterMode(GL11.GL_NEAREST); // hard block edges, no smoothing between pixels
        }
        if (screenQuad == null) {
            BufferBuilder quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            quad.addVertex(-1.0f, -1.0f, 0.0f);
            quad.addVertex(1.0f, -1.0f, 0.0f);
            quad.addVertex(1.0f, 1.0f, 0.0f);
            quad.addVertex(-1.0f, 1.0f, 0.0f);
            screenQuad = new VertexBuffer(VertexBuffer.Usage.STATIC);
            screenQuad.bind();
            screenQuad.upload(quad.buildOrThrow());
            VertexBuffer.unbind();
        }
        return true;
    }

    private static void copy(RenderTarget from, RenderTarget to) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, from.width, from.height, 0, 0, to.width, to.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    private static void free() {
        for (TextureTarget target : new TextureTarget[] {out, outNext, scene, prevScene}) {
            if (target != null) {
                target.destroyBuffers();
            }
        }
        out = outNext = scene = prevScene = null;
    }

    /** In bed, the "Leave Bed" chat screen stays open: don't draw it over the footage. */
    @SubscribeEvent
    public static void onScreenRenderBed(ScreenEvent.Render.Pre event) {
        if (stage != Stage.NONE && event.getScreen() instanceof InBedChatScreen) {
            event.setCanceled(true);
        }
    }

    /** "Loading terrain..." after the dimension change: keep showing the broken frame instead. */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Pre event) {
        if (stage != Stage.NONE && event.getScreen() instanceof ReceivingLevelScreen) {
            if (out != null) {
                RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
                copy(out, main);
                main.bindWrite(false);
            }
            event.setCanceled(true);
        }
    }

    /** Nothing over the footage: no hotbar, hearts, chat or camcorder overlay while it breaks. */
    @SubscribeEvent
    public static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (stage != Stage.NONE) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (stage != Stage.NONE) {
            event.setCanceled(true);
        }
    }

    /** You may be inside a block when it starts (the old way in): don't cover the shot. */
    @SubscribeEvent
    public static void onBlockOverlay(RenderBlockScreenEffectEvent event) {
        if (stage != Stage.NONE && event.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.BLOCK) {
            event.setCanceled(true);
        }
    }

    /** Frozen while the world breaks and loads; you can move again once Level 0 shows through. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (stage != Stage.LAG && stage != Stage.HELD) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0.0f;
        input.leftImpulse = 0.0f;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        stage = Stage.NONE;
        lagLevel = null;
        sinkCamera = null;
        seenPacketMs = CameraClientState.introStartMs = 0;
        free();
    }
}
