package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.player.SanityClientState;
import com.rickr.backrooms.player.SanityData;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.Travel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * What low sanity does to you. Everything here is client-only and only you see/hear it; the server
 * just sends the number ({@link SanityClientState}). Only active in the Backrooms.
 * <ul>
 *   <li>below 70: now and then a whisper, a knock or a single footstep right behind you;</li>
 *   <li>below 50: the camcorder picture gets worse (see {@link #shaderSanity()}), and a black
 *       figure stands at the edge of your view... until you look at it;</li>
 *   <li>below 30: you hear your own heartbeat, faster and louder the lower you go;</li>
 *   <li>below 15: sometimes the figure breathes in and runs at you down the corridor. It's not real.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class SanityClientEffects {
    private static final float SOUNDS_BELOW = 70.0f;
    private static final float VISUALS_BELOW = 50.0f;
    private static final float HEARTBEAT_BELOW = 30.0f;
    private static final float CHASE_BELOW = 15.0f;

    /** Heart rate shown on the HUD and used for the heartbeat: 70 BPM when sane, 160 at 0. */
    private static final float CALM_BPM = 70.0f;
    private static final float PANIC_BPM = 160.0f;

    /** The silhouette's billboard size in blocks (a little taller than a player). */
    private static final float FIGURE_WIDTH = 1.3f;
    private static final float FIGURE_HEIGHT = 2.6f;
    private static final ResourceLocation FIGURE_TEXTURE = Backrooms.id("textures/misc/sanity_silhouette.png");

    /** Peripheral figure: vanishes when you look within this angle of it, or after 3 s. */
    private static final double LOOK_VANISH_DEGREES = 20.0;
    private static final long PERIPHERAL_MS = 3000;
    /** Fake chase: runs at this speed (blocks per second), vanishes this close to you. */
    private static final double CHASE_SPEED = 9.0;
    private static final double CHASE_VANISH_DISTANCE = 2.0;
    private static final long CHASE_MAX_MS = 3000;
    /** The little tape glitch when a figure vanishes. */
    private static final long GLITCH_MS = 300;

    private static final RandomSource RANDOM = RandomSource.create();

    /** Sanity as shown (smoothed, so the shader never jumps when a packet arrives). */
    private static float shown = SanityData.MAX;

    // Timers, in System.currentTimeMillis(). 0 = "not scheduled yet".
    private static long nextNoiseMs;
    private static long nextFigureMs;
    private static long nextChaseMs;

    // Heartbeat: beats so far (fraction = where we are in the current beat).
    private static double beatPhase;
    private static long lastFrameNanos;
    private static long lastBeatPlayed;

    // The hallucinated figure (at most one at a time).
    @Nullable
    private static Vec3 figurePos;
    private static boolean figureChasing;
    private static long figureStartMs;
    private static long glitchStartMs;
    private static float glitchStrength;

    // ---- Values for the shader and the HUD --------------------------------------------------

    /** True while the sanity effects should run at all (in the Backrooms, in game). */
    private static boolean active(Minecraft mc) {
        return mc.level != null && mc.player != null && Travel.inBackrooms(mc.level) && !mc.player.isSpectator();
    }

    /** The smoothed sanity 0..100 (100 outside the Backrooms effects). */
    public static float shownSanity() {
        return shown;
    }

    /** Heart rate for the HUD: 70 BPM at sanity 100 up to 160 BPM at 0. */
    public static int bpm() {
        return Math.round(Mth.lerp(1.0f - shown / SanityData.MAX, CALM_BPM, PANIC_BPM));
    }

    /** 0 = sane (picture exactly unchanged) .. 1 = broken. Starts below 50 sanity. */
    public static float shaderSanity() {
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc)) {
            return 0.0f;
        }
        return Mth.clamp((VISUALS_BELOW - shown) / VISUALS_BELOW, 0.0f, 1.0f);
    }

    /** 0..1 "lub-dub" pulse for the shader's edge darkening, in time with the heartbeat sound. */
    public static float heartbeatPulse() {
        float p = (float) (beatPhase - Math.floor(beatPhase));
        float lub = (float) Math.exp(-p * 12.0);
        float dub = p > 0.28f ? 0.6f * (float) Math.exp(-(p - 0.28f) * 14.0) : 0.0f;
        return Mth.clamp(lub + dub, 0.0f, 1.0f);
    }

    /** Advances the heartbeat once per frame, so the pulse stays smooth at any frame rate. */
    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0 ? 0.0 : Math.min(0.25, (now - lastFrameNanos) / 1.0e9);
        lastFrameNanos = now;
        beatPhase += dt * bpm() / 60.0;
    }

    /** 0..1 tiny tape glitch right after a figure vanished. */
    public static float vanishGlitch() {
        long since = System.currentTimeMillis() - glitchStartMs;
        if (since < 0 || since > GLITCH_MS) {
            return 0.0f;
        }
        return glitchStrength * (1.0f - since / (float) GLITCH_MS);
    }

    // ---- Tick: schedule the hallucinations ---------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            // Disconnected: the next world starts sane until the server says otherwise.
            SanityClientState.sanity = SanityData.MAX;
            shown = SanityData.MAX;
            reset();
            return;
        }
        float target = SanityClientState.sanity;
        shown += (target - shown) * 0.1f;
        if (Math.abs(target - shown) < 0.01f) {
            shown = target;
        }
        if (!active(mc) || mc.isPaused()) {
            if (!active(mc)) {
                reset();
            }
            return;
        }
        long now = System.currentTimeMillis();
        LocalPlayer player = mc.player;

        // Below 70: whispers, knocks and footsteps behind you.
        if (shown < SOUNDS_BELOW) {
            if (nextNoiseMs == 0) {
                nextNoiseMs = now + randomMs(40, 90);
            } else if (now >= nextNoiseMs) {
                playNoiseBehind(mc.level, player);
                nextNoiseMs = now + randomMs(40, 90);
            }
        } else {
            nextNoiseMs = 0;
        }

        // Below 30: the heartbeat, one "lub-dub" per beat (the phase is advanced per frame in onRenderFrame).
        long beat = (long) Math.floor(beatPhase);
        if (beat != lastBeatPlayed) {
            lastBeatPlayed = beat;
            if (shown < HEARTBEAT_BELOW) {
                float fear = 1.0f - shown / HEARTBEAT_BELOW; // 0 at 30 .. 1 at 0
                Vec3 eyes = player.getEyePosition();
                mc.level.playLocalSound(eyes.x, eyes.y, eyes.z, ModSounds.SANITY_HEARTBEAT.get(), SoundSource.AMBIENT,
                        0.25f + 0.55f * fear, 1.0f, false);
            }
        }

        tickFigure(player, now);

        // Below 50: the figure at the edge of your view.
        if (shown < VISUALS_BELOW) {
            if (nextFigureMs == 0) {
                nextFigureMs = now + randomMs(30, 60);
            } else if (now >= nextFigureMs && figurePos == null) {
                if (!spawnPeripheral(mc.level, player, now)) {
                    nextFigureMs = now + 2000; // no good spot right now: try again in a moment
                } else {
                    nextFigureMs = now + randomMs(30, 60);
                }
            }
        } else {
            nextFigureMs = 0;
        }

        // Below 15: the fake chase.
        if (shown < CHASE_BELOW) {
            if (nextChaseMs == 0) {
                nextChaseMs = now + randomMs(45, 90);
            } else if (now >= nextChaseMs && figurePos == null) {
                if (!spawnChase(mc.level, player, now)) {
                    nextChaseMs = now + 3000; // not facing a corridor: wait a little
                } else {
                    nextChaseMs = now + randomMs(60, 120);
                }
            }
        } else {
            nextChaseMs = 0;
        }
    }

    private static void reset() {
        nextNoiseMs = 0;
        nextFigureMs = 0;
        nextChaseMs = 0;
        figurePos = null;
    }

    private static long randomMs(int minSeconds, int maxSeconds) {
        return (minSeconds + RANDOM.nextInt(maxSeconds - minSeconds + 1)) * 1000L;
    }

    /** A whisper (most often), a knock or a single footstep, 4-8 blocks behind you. Never in front. */
    private static void playNoiseBehind(ClientLevel level, LocalPlayer player) {
        // Straight behind you, give or take 35 degrees.
        float yaw = player.getYRot() + 180.0f + (RANDOM.nextFloat() * 70.0f - 35.0f);
        double distance = 4.0 + RANDOM.nextDouble() * 4.0;
        Vec3 dir = horizontal(yaw);
        double x = player.getX() + dir.x * distance;
        double z = player.getZ() + dir.z * distance;
        float roll = RANDOM.nextFloat();
        SoundEvent sound;
        double y;
        float volume;
        if (roll < 0.5f) {
            sound = ModSounds.SANITY_WHISPER.get();
            y = player.getEyeY();
            volume = 0.6f;
        } else if (roll < 0.75f) {
            sound = ModSounds.SANITY_KNOCK.get();
            y = player.getEyeY() - 0.3;
            volume = 0.8f;
        } else {
            sound = ModSounds.STEP_CONCRETE_WALK.get(); // someone takes one step behind you
            y = player.getY();
            volume = 0.6f;
        }
        level.playLocalSound(x, y, z, sound, SoundSource.AMBIENT, volume, 0.9f + RANDOM.nextFloat() * 0.2f, false);
    }

    // ---- The figure ----------------------------------------------------------------------

    /** Moves the fake chaser and makes the figure vanish when its time is up. */
    private static void tickFigure(LocalPlayer player, long now) {
        if (figurePos == null) {
            return;
        }
        long age = now - figureStartMs;
        if (figureChasing) {
            if (age > CHASE_MAX_MS || chasePosition(player, now).distanceTo(player.position()) <= CHASE_VANISH_DISTANCE) {
                vanish(0.8f);
            }
            return;
        }
        Vec3 toFigure = figurePos.add(0.0, FIGURE_HEIGHT * 0.6, 0.0).subtract(player.getEyePosition()).normalize();
        double angle = Math.toDegrees(Math.acos(Mth.clamp(toFigure.dot(player.getLookAngle()), -1.0, 1.0)));
        if (angle < LOOK_VANISH_DEGREES || age > PERIPHERAL_MS) {
            vanish(0.35f); // you look at it... and it was never there
        }
    }

    private static void vanish(float glitch) {
        figurePos = null;
        glitchStartMs = System.currentTimeMillis();
        glitchStrength = glitch;
    }

    /**
     * Peripheral figure: 12-20 blocks away, 40-70 degrees off your crosshair (left or right), on
     * walkable floor you could actually see from here.
     */
    private static boolean spawnPeripheral(ClientLevel level, LocalPlayer player, long now) {
        for (int attempt = 0; attempt < 12; attempt++) {
            float side = RANDOM.nextBoolean() ? 1.0f : -1.0f;
            float yaw = player.getYRot() + side * (40.0f + RANDOM.nextFloat() * 30.0f);
            double distance = 12.0 + RANDOM.nextDouble() * 8.0;
            Vec3 dir = horizontal(yaw);
            Vec3 spot = findFloor(level, player.getX() + dir.x * distance, player.getY(), player.getZ() + dir.z * distance);
            if (spot != null && canSee(level, player, spot)) {
                showFigure(spot, false, now);
                return true;
            }
        }
        return false;
    }

    /** Fake chase: the figure appears ~10 blocks ahead of you down the corridor and breathes in. */
    private static boolean spawnChase(ClientLevel level, LocalPlayer player, long now) {
        Vec3 dir = horizontal(player.getYRot());
        for (double distance = 10.0; distance >= 7.0; distance -= 1.0) {
            Vec3 spot = findFloor(level, player.getX() + dir.x * distance, player.getY(), player.getZ() + dir.z * distance);
            if (spot != null && canSee(level, player, spot)) {
                showFigure(spot, true, now);
                // The Bacteria's inhale, lower and slower: something wrong with it.
                level.playLocalSound(spot.x, spot.y + 2.0, spot.z, ModSounds.BACTERIA_SCREECH.get(), SoundSource.HOSTILE,
                        1.0f, 0.7f, false);
                return true;
            }
        }
        return false;
    }

    private static void showFigure(Vec3 spot, boolean chasing, long now) {
        figurePos = spot;
        figureChasing = chasing;
        figureStartMs = now;
    }

    /** Where the running figure is right now: straight at you at {@link #CHASE_SPEED}. */
    private static Vec3 chasePosition(LocalPlayer player, long now) {
        double seconds = (now - figureStartMs) / 1000.0;
        Vec3 toPlayer = player.position().subtract(figurePos);
        Vec3 flat = new Vec3(toPlayer.x, 0.0, toPlayer.z);
        double length = flat.length();
        if (length < 1.0e-3) {
            return figurePos;
        }
        double travelled = Math.min(seconds * CHASE_SPEED, length);
        return figurePos.add(flat.scale(travelled / length));
    }

    /**
     * Standing spot near (x, y, z): solid floor below, two blocks of free space above it
     * (searched 3 blocks up and down). Returns the bottom centre of the spot, or null.
     */
    @Nullable
    private static Vec3 findFloor(ClientLevel level, double x, double y, double z) {
        BlockPos base = BlockPos.containing(x, y, z);
        for (int dy : new int[] {0, -1, 1, -2, 2, -3, 3}) { // nearest height first
            BlockPos feet = base.above(dy);
            BlockPos below = feet.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                    && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                return Vec3.atBottomCenterOf(feet);
            }
        }
        return null;
    }

    /** Clear line from your eyes to the figure's chest (no walls in between). */
    private static boolean canSee(ClientLevel level, LocalPlayer player, Vec3 spot) {
        Vec3 chest = spot.add(0.0, 1.5, 0.0);
        return level.clip(new ClipContext(player.getEyePosition(), chest, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.MISS;
    }

    /** Unit vector pointing where a Minecraft yaw looks (yaw 0 = +Z, 90 = -X). */
    private static Vec3 horizontal(float yawDegrees) {
        float rad = yawDegrees * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(rad), 0.0, Mth.cos(rad));
    }

    // ---- Rendering -----------------------------------------------------------------------

    /**
     * Draws the figure as a pitch-black cut-out that always turns to face the camera. At this stage
     * the pose stack is "camera-relative" just like for entities, so we translate by (pos - camera).
     */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || figurePos == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc)) {
            return;
        }
        long now = System.currentTimeMillis();
        Vec3 pos = figureChasing ? chasePosition(mc.player, now) : figurePos;
        Vec3 cam = event.getCamera().getPosition();
        float bob = figureChasing ? Math.abs(Mth.sin(now / 1000.0f * 14.0f)) * 0.12f : 0.0f; // running stride
        float faceYaw = (float) Math.toDegrees(Math.atan2(cam.x - pos.x, cam.z - pos.z));

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(pos.x - cam.x, pos.y - cam.y + bob, pos.z - cam.z);
        pose.mulPose(Axis.YP.rotationDegrees(faceYaw));
        if (figureChasing) {
            pose.mulPose(Axis.XP.rotationDegrees(12.0f)); // leaning into the run
        }
        PoseStack.Pose last = pose.last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityCutoutNoCull(FIGURE_TEXTURE);
        VertexConsumer out = buffers.getBuffer(type);
        float hw = FIGURE_WIDTH / 2.0f;
        vertex(out, last, -hw, 0.0f, 1.0f, 1.0f);
        vertex(out, last, hw, 0.0f, 0.0f, 1.0f);
        vertex(out, last, hw, FIGURE_HEIGHT, 0.0f, 0.0f);
        vertex(out, last, -hw, FIGURE_HEIGHT, 1.0f, 0.0f);
        pose.popPose();
        buffers.endBatch(type);
    }

    /** One corner of the billboard: pure black, so it reads as a shadow whatever the lighting. */
    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float u, float v) {
        out.addVertex(pose, x, y, 0.0f)
                .setColor(0, 0, 0, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0f, 0.0f, 1.0f);
    }

    private SanityClientEffects() {}
}
