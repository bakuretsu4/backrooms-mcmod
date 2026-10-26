package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.layout.Level7Layout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Level 7 on the client, apart from its light and fog ({@link Level7Atmosphere}):
 * <ul>
 *   <li><b>Depth:</b> how far under the surface the camera is, which darkens the light, the fog
 *       and the picture grade.</li>
 *   <li><b>The lurch:</b> drop through the hatch and the world swings round you for a moment, as if
 *       "down" had changed (the wikis: gravity feels wrong when you go through).</li>
 *   <li><b>The Thing:</b> something enormous lives in the sea. You never meet it, you only feel
 *       it: a groan from far below every half minute or so; near the surface now and then the
 *       water heaves under you, as if something huge just passed beneath; and in the twilight
 *       water, once in a while, a vast dark shape glides past at the edge of what you can see.
 *       It never attacks. (Being in the water drains sanity on the server, more the deeper you go.)</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level7Client {
    /** Depth (blocks under the surface) where the water starts getting darker, and where it is darkest. */
    private static final double DARK_FROM = 4.0;
    private static final double DARK_AT = 110.0;

    // --- The lurch -------------------------------------------------------------------------------
    private static final int LURCH_TICKS = 22;
    private static final float LURCH_ROLL = 38.0f;
    private static final float LURCH_PITCH = 18.0f;

    // --- The Thing -------------------------------------------------------------------------------
    private static final int GROAN_MIN_TICKS = 30 * 20;
    private static final int GROAN_MAX_TICKS = 80 * 20;
    private static final int HEAVE_MIN_TICKS = 45 * 20;
    private static final int HEAVE_MAX_TICKS = 100 * 20;
    private static final int HEAVE_LENGTH = 100;
    /** Upward push per tick at the top of a swell (the water lifts you, then lets you down). */
    private static final double HEAVE_PUSH = 0.03;
    private static final int PASS_MIN_TICKS = 60 * 20;
    private static final int PASS_MAX_TICKS = 150 * 20;
    /** Only seen between these depths: higher up it would show too clearly, deeper it is all black anyway. */
    private static final double PASS_MIN_DEPTH = 12.0;
    private static final double PASS_MAX_DEPTH = 110.0;
    /** The body: length, greatest radius, speed (blocks per tick), and how far ahead of you it crosses. */
    private static final float BODY_LENGTH = 120.0f;
    private static final float BODY_RADIUS = 8.0f;
    private static final double BODY_SPEED = 0.55;
    private static final double PASS_AHEAD = 20.0;
    private static final double PASS_START = 90.0;
    private static final int RINGS = 28;
    private static final int SIDES = 10;
    private static final ResourceLocation SKIN = Backrooms.id("textures/entity/l7_thing.png");

    private static final RandomSource RANDOM = RandomSource.create();

    private static boolean underwater;
    private static float deepness;
    private static boolean wasInRoom;
    private static int lurchTick = -1;
    private static float lurchSide = 1.0f;
    private static int groanIn = -1;
    private static int heaveIn = -1;
    private static int heaveTick = -1;
    private static int passIn = -1;
    @Nullable
    private static Pass pass;

    /** One crossing of the Thing: the point where it is closest to you, its heading, and when it started. */
    private record Pass(Vec3 centre, Vec3 dir, long start) {}

    private Level7Client() {}

    /** True while the camera is under the water in Level 7. */
    public static boolean underwater() {
        return underwater;
    }

    /** 0 near the surface (or above it) to 1 deep down. */
    public static float deepness() {
        return deepness;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !Level7Atmosphere.inLevel7()) {
            reset();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        double depth = Level7Layout.SURFACE_Y + 1 - eye.y;
        underwater = mc.gameRenderer.getMainCamera().getFluidInCamera() == FogType.WATER;
        // Darkening on a curve: the upper water stays murky-light for a while (the twilight where
        // the Thing can be glimpsed), then it falls off fast toward the bottom.
        deepness = (float) Math.pow(Mth.clamp((depth - DARK_FROM) / (DARK_AT - DARK_FROM), 0.0, 1.0), 1.5);

        tickLurch(mc, player);
        tickGroans(mc, eye);
        tickHeave(mc, player, depth);
        tickPass(mc, player, eye, depth);
    }

    private static void reset() {
        underwater = false;
        deepness = 0.0f;
        wasInRoom = false;
        lurchTick = -1;
        groanIn = -1;
        heaveIn = -1;
        heaveTick = -1;
        passIn = -1;
        pass = null;
    }

    /** Up in the room, then falling below the ceiling without holding the rope: lurch. */
    private static void tickLurch(Minecraft mc, Player player) {
        if (player.getY() >= Level7Layout.ROOM_FLOOR_Y) {
            wasInRoom = true;
        } else if (wasInRoom && player.getY() < Level7Layout.CEILING_BOTTOM_Y) {
            wasInRoom = false;
            if (!player.onClimbable()) {
                lurchTick = 0;
                lurchSide = RANDOM.nextBoolean() ? 1.0f : -1.0f;
                playFlat(mc, ModSounds.L7_LURCH.get(), 0.9f, 0.95f + RANDOM.nextFloat() * 0.1f);
            }
        }
        if (lurchTick >= 0 && ++lurchTick > LURCH_TICKS) {
            lurchTick = -1;
        }
    }

    /** A groan from somewhere far below, a little louder under water. */
    private static void tickGroans(Minecraft mc, Vec3 eye) {
        if (groanIn < 0) {
            groanIn = Mth.nextInt(RANDOM, GROAN_MIN_TICKS / 2, GROAN_MAX_TICKS);
        }
        if (--groanIn > 0) {
            return;
        }
        groanIn = Mth.nextInt(RANDOM, GROAN_MIN_TICKS, GROAN_MAX_TICKS);
        float angle = RANDOM.nextFloat() * Mth.TWO_PI;
        double distance = 40.0 + RANDOM.nextDouble() * 20.0;
        playAt(mc, ModSounds.L7_GROAN.get(), eye.add(Mth.cos(angle) * distance, -20.0 - RANDOM.nextDouble() * 30.0,
                Mth.sin(angle) * distance), underwater ? 6.0f : 4.0f, 0.65f + RANDOM.nextFloat() * 0.3f);
    }

    /** Near the surface: now and then the water swells under you. */
    private static void tickHeave(Minecraft mc, Player player, double depth) {
        if (heaveTick >= 0) {
            if (player.isInWater()) {
                double swell = Mth.sin((float) (heaveTick * Mth.TWO_PI / (HEAVE_LENGTH / 2.0)));
                player.setDeltaMovement(player.getDeltaMovement().add(0.0, HEAVE_PUSH * swell, 0.0));
            }
            if (++heaveTick > HEAVE_LENGTH) {
                heaveTick = -1;
            }
            return;
        }
        if (depth > 8.0) {
            return; // only felt near the top
        }
        if (heaveIn < 0) {
            heaveIn = Mth.nextInt(RANDOM, HEAVE_MIN_TICKS / 2, HEAVE_MAX_TICKS);
        }
        if (--heaveIn <= 0) {
            heaveIn = Mth.nextInt(RANDOM, HEAVE_MIN_TICKS, HEAVE_MAX_TICKS);
            heaveTick = 0;
            playFlat(mc, ModSounds.L7_HEAVE.get(), 1.0f, 0.85f + RANDOM.nextFloat() * 0.2f);
        }
    }

    /** In the twilight water: once in a while the Thing glides across in front of you. */
    private static void tickPass(Minecraft mc, Player player, Vec3 eye, double depth) {
        long now = mc.level.getGameTime();
        if (pass != null) {
            if ((now - pass.start) * BODY_SPEED > 2 * PASS_START + BODY_LENGTH || !underwater) {
                pass = null;
            }
            return;
        }
        if (!underwater || depth < PASS_MIN_DEPTH || depth > PASS_MAX_DEPTH) {
            return;
        }
        if (passIn < 0) {
            passIn = Mth.nextInt(RANDOM, PASS_MIN_TICKS / 3, PASS_MAX_TICKS / 2);
        }
        if (--passIn > 0) {
            return;
        }
        passIn = Mth.nextInt(RANDOM, PASS_MIN_TICKS, PASS_MAX_TICKS);
        // Across your view, a little below you, about as far off as the murk lets you see.
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x).scale(RANDOM.nextBoolean() ? 1.0 : -1.0);
        double below = 4.0 + RANDOM.nextDouble() * 5.0;
        double y = Math.max(eye.y - below, Level7Layout.DEEP_TOP_Y + BODY_RADIUS + 2.0);
        Vec3 centre = new Vec3(eye.x + forward.x * PASS_AHEAD, y, eye.z + forward.z * PASS_AHEAD);
        pass = new Pass(centre, right, now);
        playAt(mc, ModSounds.L7_GROAN.get(), centre.subtract(right.scale(PASS_START * 0.5)), 6.0f, 0.55f);
        playFlat(mc, ModSounds.L7_HEAVE.get(), 0.7f, 0.6f);
    }

    /** Camera: the lurch through the hatch, and the slow roll of a swell. */
    public static void modifyAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!Level7Atmosphere.inLevel7()) {
            return;
        }
        float partial = (float) event.getPartialTick();
        if (lurchTick >= 0) {
            float t = Mth.clamp((lurchTick + partial) / LURCH_TICKS, 0.0f, 1.0f);
            // Swings out fast, then eases back: sin over the first half, a long settle after.
            float swing = Mth.sin(t * Mth.PI) * (1.0f - 0.4f * t);
            event.setRoll(event.getRoll() + lurchSide * LURCH_ROLL * swing);
            event.setPitch(event.getPitch() + LURCH_PITCH * swing);
        }
        if (heaveTick >= 0) {
            float t = (heaveTick + partial) / HEAVE_LENGTH;
            event.setRoll(event.getRoll() + 3.0f * Mth.sin(t * Mth.PI) * Mth.sin(t * Mth.TWO_PI * 2.0f));
        }
    }

    /**
     * The Thing's body: rings of quads along a long, blunt-headed, tapering shape that sways as it
     * swims. Drawn like an entity, so the fog (and the picture grade's haze) swallow it the same
     * way they swallow everything else: it shows as a darker mass in the murk, never clearly.
     */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || pass == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float time = (mc.level.getGameTime() - pass.start) + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        Vec3 dir = pass.dir;
        Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
        double headAlong = -PASS_START + time * BODY_SPEED;

        double[][] centres = new double[RINGS + 1][3];
        float[] radius = new float[RINGS + 1];
        for (int i = 0; i <= RINGS; i++) {
            float s = i / (float) RINGS;
            double along = headAlong - s * BODY_LENGTH;
            // A slow side-to-side wave that grows toward the tail, and a gentle rise and fall.
            double sway = 3.0 * s * Mth.sin(s * 9.0f - time * 0.05f);
            double rise = 1.5 * Mth.sin(s * 4.0f - time * 0.03f);
            centres[i][0] = pass.centre.x + dir.x * along + side.x * sway - cam.x;
            centres[i][1] = pass.centre.y + rise - cam.y;
            centres[i][2] = pass.centre.z + dir.z * along + side.z * sway - cam.z;
            radius[i] = BODY_RADIUS * profile(s);
        }

        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // No culling: the inside of the body is never seen anyway, and it saves caring which way round the quads wind.
        RenderType type = RenderType.entityCutoutNoCull(SKIN);
        VertexConsumer out = buffers.getBuffer(type);
        for (int i = 0; i < RINGS; i++) {
            for (int j = 0; j < SIDES; j++) {
                float a0 = j * Mth.TWO_PI / SIDES;
                float a1 = (j + 1) * Mth.TWO_PI / SIDES;
                ring(out, pose, centres[i], radius[i], side, a0, i / (float) RINGS, j / (float) SIDES);
                ring(out, pose, centres[i + 1], radius[i + 1], side, a0, (i + 1) / (float) RINGS, j / (float) SIDES);
                ring(out, pose, centres[i + 1], radius[i + 1], side, a1, (i + 1) / (float) RINGS, (j + 1) / (float) SIDES);
                ring(out, pose, centres[i], radius[i], side, a1, i / (float) RINGS, (j + 1) / (float) SIDES);
            }
        }
        // The tail: one tall flat fluke behind the last ring.
        double[] tail = centres[RINGS];
        float fx = (float) (tail[0] - dir.x * 6.0);
        float fz = (float) (tail[2] - dir.z * 6.0);
        float nx = (float) side.x;
        float nz = (float) side.z;
        vertex(out, pose, (float) tail[0], (float) tail[1] - 1.0f, (float) tail[2], 0.0f, 0.0f, nx, 0.0f, nz);
        vertex(out, pose, fx, (float) tail[1] - 7.0f, fz, 1.0f, 0.0f, nx, 0.0f, nz);
        vertex(out, pose, fx, (float) tail[1] + 7.0f, fz, 1.0f, 1.0f, nx, 0.0f, nz);
        vertex(out, pose, (float) tail[0], (float) tail[1] + 1.0f, (float) tail[2], 0.0f, 1.0f, nx, 0.0f, nz);
        buffers.endBatch(type);
    }

    /** Body thickness along its length (0 = head, 1 = tail): a blunt head, then a long taper. */
    private static float profile(float s) {
        if (s < 0.15f) {
            return 0.35f + 0.65f * Mth.sin(s / 0.15f * Mth.HALF_PI);
        }
        return 1.0f - 0.9f * (float) Math.pow((s - 0.15f) / 0.85f, 1.6);
    }

    /** One vertex on a ring: {@code angle} round the body (a slightly flattened oval). */
    private static void ring(VertexConsumer out, PoseStack.Pose pose, double[] c, float r, Vec3 side, float angle,
                             float u, float v) {
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        float nx = (float) side.x * cos;
        float nz = (float) side.z * cos;
        vertex(out, pose, (float) c[0] + nx * r, (float) c[1] + sin * r * 0.75f, (float) c[2] + nz * r,
                u * 6.0f, v, nx, sin, nz);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz) {
        out.addVertex(pose, x, y, z)
                .setColor(50, 54, 56, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.pack(0, 0))
                .setNormal(pose, nx, ny, nz);
    }

    /** A sound with no position (inside your head, or all around you). */
    private static void playFlat(Minecraft mc, SoundEvent sound, float volume, float pitch) {
        mc.getSoundManager().play(new SimpleSoundInstance(sound.getLocation(), SoundSource.AMBIENT, volume, pitch,
                RANDOM, false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
    }

    private static void playAt(Minecraft mc, SoundEvent sound, Vec3 pos, float volume, float pitch) {
        mc.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.AMBIENT, volume, pitch, RANDOM,
                pos.x, pos.y, pos.z));
    }
}
