package com.rickr.backrooms.client.intro;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.network.IntroCutscenePayload;
import com.rickr.backrooms.network.TransitionPayload;
import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.Input;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The noclip intro, in the style of Kane Pixels' first video: you are filming in the real world
 * (camcorder look and REC overlay switch on), the footage starts to break up, the camera dips
 * toward the ground as if it gives way, everything cuts to black - and then you are falling
 * through darkness, crash through a Level 0 ceiling and land on the carpet, where the usual
 * "waking up" shot takes over.
 *
 * <p>The server sends {@code FILMING} when you start to noclip and {@code FALLING} right after it
 * has moved you to Level 0. Everything else (timing, camera, sounds, black frames) happens here.
 *
 * <p>For the fall we film from a tiny client-only entity (a {@link Marker}, which has no model)
 * instead of your player: {@code Minecraft.setCameraEntity} makes the game render from it, so we
 * can move the camera through the ceiling without moving you.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class IntroCutscene implements LayeredDraw.Layer {
    public static final ResourceLocation LAYER_ID = Backrooms.id("intro_overlay");
    private static final ResourceLocation CAMCORDER_HUD = Backrooms.id("camcorder_hud");

    // --- FILMING timeline (ms after the packet). Must add up to the server's FILMING_TICKS (2.6 s). ---
    /** Footage starts breaking up. */
    private static final long GLITCH_START_MS = 1700;
    /** The camera tips down toward the ground. */
    private static final long DROP_START_MS = 2050;
    /** Fade to black. */
    private static final long BLACK_START_MS = 2300;
    private static final long BLACK_FULL_MS = 2600;
    /** If the server never moves us (e.g. it was cancelled), give the camera back after this. */
    private static final long FILMING_TIMEOUT_MS = 8000;

    // --- FALLING timeline (ms after the loading screen is gone). ---
    private static final long FALL_FADE_IN_MS = 250;
    private static final long FALL_MS = 1600;
    /** Black after the crash, before you "wake up". */
    private static final long LANDED_BLACK_MS = 450;
    /** Blocks above the floor where the fall starts (Level 0's ceiling is 4 blocks up). */
    private static final double FALL_HEIGHT = 14.0;
    /** Camera height above your feet when you land: lying on the carpet. */
    private static final double LAND_EYE = 0.3;

    private enum Stage { NONE, FILMING, WAITING_FOR_WORLD, FALLING, LANDED }

    private static Stage stage = Stage.NONE;
    private static long seenPacketMs;
    private static long stageStartMs;
    private static boolean glitchPlayed;
    private static Marker camera;
    private static double fallTopY;
    private static double fallBottomY;
    private static float fallYaw;
    private static SoundInstance wind;

    // ------------------------------------------------------------------------------------------
    // Queries used by the camcorder effect, HUD and camera code.
    // ------------------------------------------------------------------------------------------

    public static boolean active() {
        return stage != Stage.NONE;
    }

    /** True while filming in the real world: the camcorder look and REC overlay must be on. */
    public static boolean filming() {
        return stage == Stage.FILMING;
    }

    /** 0..1 strength of the tape break-up, fed to the camcorder shader's "Transition" glitch. */
    public static float glitch() {
        if (stage != Stage.FILMING) {
            return 0.0f;
        }
        long t = System.currentTimeMillis() - stageStartMs;
        return Mth.clamp((t - GLITCH_START_MS) / (float) (BLACK_FULL_MS - GLITCH_START_MS), 0.0f, 1.0f);
    }

    // ------------------------------------------------------------------------------------------
    // Stage machine
    // ------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long packetMs = CameraClientState.introStartMs;
        if (packetMs != 0 && packetMs != seenPacketMs) {
            seenPacketMs = packetMs;
            if (CameraClientState.introPhase == IntroCutscenePayload.FILMING) {
                begin(Stage.FILMING, now);
                glitchPlayed = false;
            } else if (CameraClientState.introPhase == IntroCutscenePayload.FALLING) {
                begin(Stage.WAITING_FOR_WORLD, now);
            }
        }

        long t = now - stageStartMs;
        switch (stage) {
            case FILMING -> {
                if (!glitchPlayed && t >= GLITCH_START_MS) {
                    glitchPlayed = true;
                    play(mc, ModSounds.INTRO_GLITCH.get(), 1.0f);
                }
                if (t > FILMING_TIMEOUT_MS) {
                    finish(mc, false);
                }
            }
            // The "Loading terrain" screen shows after a dimension change; start the fall once it's gone.
            case WAITING_FOR_WORLD -> {
                if (mc.screen == null) {
                    startFall(mc, now);
                } else if (t > 15000) {
                    finish(mc, true);
                }
            }
            case FALLING -> {
                if (t >= FALL_MS) {
                    land(mc, now);
                }
            }
            case LANDED -> {
                if (t >= LANDED_BLACK_MS) {
                    finish(mc, true);
                }
            }
            case NONE -> { }
        }
    }

    private static void begin(Stage next, long now) {
        stage = next;
        stageStartMs = now;
    }

    private static void startFall(Minecraft mc, long now) {
        fallBottomY = mc.player.getY() + LAND_EYE;
        fallTopY = fallBottomY + FALL_HEIGHT;
        fallYaw = mc.player.getYRot();
        camera = new Marker(EntityType.MARKER, mc.level);
        placeCamera(fallTopY, fallYaw, 80.0f);
        mc.setCameraEntity(camera);
        wind = play(mc, ModSounds.INTRO_WIND.get(), 1.0f);
        begin(Stage.FALLING, now);
    }

    private static void land(Minecraft mc, long now) {
        if (wind != null) {
            mc.getSoundManager().stop(wind);
            wind = null;
        }
        play(mc, ModSounds.INTRO_IMPACT.get(), 1.0f);
        begin(Stage.LANDED, now);
    }

    /** Hand the camera back to the player; after a fall, start the "waking up on the carpet" shot. */
    private static void finish(Minecraft mc, boolean wakeUp) {
        if (camera != null) {
            if (mc.player != null && mc.getCameraEntity() == camera) {
                mc.setCameraEntity(mc.player);
            }
            camera = null;
        }
        if (wind != null) {
            mc.getSoundManager().stop(wind);
            wind = null;
        }
        stage = Stage.NONE;
        if (wakeUp) {
            CameraClientState.transitionKind = TransitionPayload.WAKE_UP;
            CameraClientState.transitionStartMs = System.currentTimeMillis();
            play(mc, ModSounds.WAKE_UP.get(), 1.0f);
        }
    }

    private static SoundInstance play(Minecraft mc, SoundEvent sound, float volume) {
        SoundInstance instance = SimpleSoundInstance.forUI(sound, 1.0f, volume);
        mc.getSoundManager().play(instance);
        return instance;
    }

    /** Puts the camera entity at this height above the landing spot (same value for "last tick" so it doesn't smear). */
    private static void placeCamera(double y, float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        double x = mc.player.getX();
        double z = mc.player.getZ();
        camera.setPos(x, y, z);
        camera.xo = camera.xOld = x;
        camera.yo = camera.yOld = y;
        camera.zo = camera.zOld = z;
        camera.setYRot(yaw);
        camera.setXRot(pitch);
        camera.yRotO = yaw;
        camera.xRotO = pitch;
    }

    /** Every frame during the fall: accelerate down like a real drop (distance grows with time squared). */
    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        if (stage != Stage.FALLING || camera == null) {
            return;
        }
        float k = Mth.clamp((System.currentTimeMillis() - stageStartMs) / (float) FALL_MS, 0.0f, 1.0f);
        placeCamera(Mth.lerp(k * k, fallTopY, fallBottomY), fallYaw, 80.0f);
    }

    /** Runs after the handheld camera; overrides the angles for the scripted parts. */
    public static void modifyAngles(ViewportEvent.ComputeCameraAngles event) {
        long t = System.currentTimeMillis() - stageStartMs;
        float s = t / 1000.0f;
        if (stage == Stage.FILMING) {
            float g = glitch();
            // Shaky hands as reality comes apart.
            event.setYaw(event.getYaw() + Mth.sin(s * 37.0f) * 2.5f * g);
            event.setPitch(event.getPitch() + Mth.cos(s * 29.0f) * 2.0f * g);
            event.setRoll(event.getRoll() + Mth.sin(s * 23.0f) * 3.0f * g);
            // Then the ground gives way: the camera tips down and rolls as you go through.
            float drop = Mth.clamp((t - DROP_START_MS) / (float) (BLACK_FULL_MS - DROP_START_MS), 0.0f, 1.0f);
            drop = drop * drop * (3.0f - 2.0f * drop);
            event.setPitch(Mth.lerp(drop, event.getPitch(), 75.0f));
            event.setRoll(event.getRoll() + drop * 35.0f);
        } else if (stage == Stage.FALLING) {
            // Tumbling slowly while looking down into the dark.
            event.setYaw(fallYaw + s * 35.0f);
            event.setPitch(80.0f + Mth.sin(s * 5.0f) * 4.0f);
            event.setRoll(Mth.sin(s * 2.7f) * 18.0f);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Keeping the shot clean
    // ------------------------------------------------------------------------------------------

    /** No walking during the cutscene (looking around is fine while filming). */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (stage == Stage.NONE || (stage == Stage.FILMING && System.currentTimeMillis() - stageStartMs < GLITCH_START_MS)) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0.0f;
        input.leftImpulse = 0.0f;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    /** While filming you are usually buried in sand: don't cover the shot with the in-block texture. */
    @SubscribeEvent
    public static void onBlockOverlay(RenderBlockScreenEffectEvent event) {
        if (stage != Stage.NONE && event.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.BLOCK) {
            event.setCanceled(true);
        }
    }

    /** During the fall the camera is not your player, so the game would draw your body below us. */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (camera != null && event.getEntity() == mc.player) {
            event.setCanceled(true);
        }
    }

    /** Nothing but the camcorder overlay on screen: no hotbar, hearts or chat over the footage. */
    @SubscribeEvent
    public static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (stage == Stage.NONE) {
            return;
        }
        ResourceLocation name = event.getName();
        boolean keep = name.equals(LAYER_ID) || (stage == Stage.FILMING && name.equals(CAMCORDER_HUD));
        if (!keep) {
            event.setCanceled(true);
        }
    }

    /** No hand in the shot: it's a camcorder, not your eyes. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (stage != Stage.NONE) {
            event.setCanceled(true);
        }
    }

    /** The dimension change shows "Loading terrain..." for a moment; keep the screen black instead. */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Pre event) {
        if (stage != Stage.NONE && event.getScreen() instanceof ReceivingLevelScreen) {
            GuiGraphics g = event.getGuiGraphics();
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0xFF000000);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        stage = Stage.NONE;
        camera = null;
        wind = null;
        seenPacketMs = CameraClientState.introStartMs = 0;
    }

    // ------------------------------------------------------------------------------------------
    // Black frames
    // ------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        float alpha = blackness(System.currentTimeMillis() - stageStartMs);
        if (alpha > 0.0f) {
            int a = Mth.clamp((int) (alpha * 255.0f), 0, 255);
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), a << 24);
        }
    }

    private static float blackness(long t) {
        return switch (stage) {
            case FILMING -> Mth.clamp((t - BLACK_START_MS) / (float) (BLACK_FULL_MS - BLACK_START_MS), 0.0f, 1.0f);
            case WAITING_FOR_WORLD, LANDED -> 1.0f;
            case FALLING -> 1.0f - Mth.clamp(t / (float) FALL_FADE_IN_MS, 0.0f, 1.0f);
            case NONE -> 0.0f;
        };
    }
}
