package com.rickr.backrooms.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.water.WaterRenderer;
import com.rickr.backrooms.config.ClientSettings;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.layout.Level7Layout;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

/**
 * The Thing in Level 7's sea: something enormous that swims past you, never at you. It never
 * touches you and never hurts you; the point is that you know it is there.
 *
 * <p><b>Dread.</b> The longer you stay in the sea (faster the deeper you are) the more it takes
 * an interest: {@link #dread} climbs from 0 to 1 over about 5 minutes in deep water (12 at the
 * surface) and drains away in about a minute once you are out of the water. With it the groans
 * come more often and from closer, and the passes come more often, closer, and in bolder ways.
 *
 * <p><b>Passes</b> (every 40-80 s at first, every 14-30 s at full dread; the first one 12-25 s
 * after you swim out over open water):
 * <ul>
 *   <li><b>Under you at the surface</b>: a huge shadow glides through the water right under you
 *       (it steers to stay under you until it has passed), the sea heaves as it goes by, and
 *       bubbles boil up around you. Seen from above the water as a dark shape on the surface.</li>
 *   <li><b>Across in front of you</b> once you are under water: a vast dark body crossing your
 *       view, 12-27 blocks off. Its two pale eyes and a row of faint lights along its flanks
 *       catch what little light there is, so it reads even in the black deep.</li>
 *   <li><b>Beneath you</b> (from a third of the way up the dread): it overtakes you from behind,
 *       a few blocks under your feet, and swims on ahead.</li>
 *   <li><b>Over you</b> (deep down, at high dread): it blots out the dimmer black above.</li>
 * </ul>
 * At high dread its head turns toward you as it passes. Its body is heard all the way: a loop of
 * shoved water and slow tail beats placed on the part of the body nearest you (so it moves across
 * and pans), then a surge of water and a rush of bubbles when it is closest, and its wake pushes
 * you and rolls the view a little (camera motion scaled by the view-tilt setting).
 *
 * <p><b>Why it reads under shader packs too:</b> the body is plain entity geometry (Iris draws it
 * with the pack's entity program); the eyes and flank lights are a depth-writing blended core
 * plus an additive halo ({@code RenderType.text} / {@code RenderType.eyes}, which packs treat as
 * emissive); the bubbles and splashes are vanilla particles; and the shadow on the surface is
 * drawn over the water after it (after our own water renderer when it runs, else after vanilla's
 * or the pack's translucent water).
 *
 * <p>Owned by {@link Level7Client}, which measures depth and calls {@link #tick} and
 * {@link #modifyAngles}. {@code /backrooms thing} (command/Level7ThingCommand) forces a pass now.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level7Thing {
    // --- Dread ---------------------------------------------------------------------------------
    /** Per tick in the sea: from 0 to 1 in 5 minutes deep down (60+ blocks). */
    private static final float DREAD_RISE = 1.0f / (5 * 60 * 20);
    /** Share of {@link #DREAD_RISE} right at the surface (so 12.5 minutes there). */
    private static final float DREAD_SURFACE_SHARE = 0.4f;
    private static final double DREAD_FULL_DEPTH = 60.0;
    /** Per tick out of the water: gone in a minute. */
    private static final float DREAD_FALL = 1.0f / (60 * 20);

    // --- Timing (ticks) ------------------------------------------------------------------------
    private static final int GROAN_MIN_CALM = 30 * 20;
    private static final int GROAN_MAX_CALM = 80 * 20;
    private static final int GROAN_MIN_DREAD = 12 * 20;
    private static final int GROAN_MAX_DREAD = 30 * 20;
    private static final int PASS_MIN_CALM = 40 * 20;
    private static final int PASS_MAX_CALM = 80 * 20;
    private static final int PASS_MIN_DREAD = 14 * 20;
    private static final int PASS_MAX_DREAD = 30 * 20;
    private static final int FIRST_PASS_MIN = 12 * 20;
    private static final int FIRST_PASS_MAX = 25 * 20;
    /** No room for it here (the shelf, a rock stack): look again in this long. */
    private static final int PASS_RETRY = 40;
    private static final int HEAVE_MIN_TICKS = 45 * 20;
    private static final int HEAVE_MAX_TICKS = 100 * 20;
    private static final int HEAVE_LENGTH = 100;
    /** Upward push per tick at the top of a swell (the water lifts you, then lets you down). */
    private static final double HEAVE_PUSH = 0.03;
    private static final int BUBBLE_TICKS = 70;

    // --- The body ------------------------------------------------------------------------------
    private static final float BODY_LENGTH = 120.0f;
    private static final float BODY_RADIUS = 8.0f;
    private static final int RINGS = 28;
    private static final int SIDES = 10;
    /** Camera depth from which it crosses in front of you (above it, it passes under you instead). */
    private static final double CROSS_MIN_DEPTH = 7.0;
    /** How far in front of you it crosses: calm, at full dread, plus up to {@link #AHEAD_JITTER}. */
    private static final double AHEAD_CALM = 22.0;
    private static final double AHEAD_DREAD = 12.0;
    private static final double AHEAD_JITTER = 5.0;
    /** Depth of its axis under the surface when it passes under you: calm, at full dread. */
    private static final double UNDER_DEPTH_CALM = 16.0;
    private static final double UNDER_DEPTH_DREAD = 11.0;
    /** Where along its line a pass starts (and ends, past you). */
    private static final double PASS_START = 90.0;
    private static final double SPEED_CROSS = 0.55;
    private static final double SPEED_UNDER = 0.42;
    /** Most it shifts sideways per tick to stay under you / clear of you. */
    private static final double STEER = 0.12;
    /** How far the head swings toward you at full dread. */
    private static final float LOOK_MAX = 5.0f;

    // --- Wake ----------------------------------------------------------------------------------
    /** Blocks beyond its skin within which the wake reaches you. */
    private static final double WAKE_RANGE = 12.0;
    private static final double WAKE_PUSH = 0.03;
    private static final float WAKE_ROLL = 4.0f;
    private static final float WAKE_PITCH = 1.5f;

    // --- Look ----------------------------------------------------------------------------------
    private static final ResourceLocation SKIN = Backrooms.id("textures/entity/l7_thing.png");
    private static final ResourceLocation GLOW = Backrooms.id("textures/entity/l7_thing_glow.png");
    /** Eyes: core and halo half-sizes (blocks), and where on the head (ring, angle round the body). */
    private static final float EYE_CORE = 0.45f;
    private static final float EYE_HALO = 1.5f;
    private static final int EYE_RING = 2;
    private static final float EYE_ANGLE = 0.4f;
    /** Flank lights: every other ring from {@link #LIGHT_FIRST} to {@link #LIGHT_LAST}, a little below the middle. */
    private static final float LIGHT_CORE = 0.2f;
    private static final float LIGHT_HALO = 0.7f;
    private static final int LIGHT_FIRST = 4;
    private static final int LIGHT_LAST = 22;
    private static final float LIGHT_ANGLE = -0.25f;
    /** Glints show fully up to this far, then fade out by {@link #GLINT_FADE_END}. */
    private static final double GLINT_FADE_START = 32.0;
    private static final double GLINT_FADE_END = 56.0;
    /** The shadow on the surface: strongest darkening, and how deep the body may be to cast it at all. */
    private static final float SHADOW_ALPHA = 0.62f;
    private static final double SHADOW_MAX_DEPTH = 26.0;
    /** Drawn just over the water's top (199.89 for a full source block), under any wave a pack adds. */
    private static final float SHADOW_Y = Level7Layout.SURFACE_Y + 0.95f;

    private static final RandomSource RANDOM = RandomSource.create();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    /** Ring centres relative to the camera, and radii, for this frame (reused: no garbage per frame). */
    private static final double[] RX = new double[RINGS + 1];
    private static final double[] RY = new double[RINGS + 1];
    private static final double[] RZ = new double[RINGS + 1];
    private static final float[] RR = new float[RINGS + 1];
    private static final PoseStack LATE_POSE = new PoseStack();

    private static float dread;
    private static int groanIn = -1;
    private static int passIn = -1;
    private static int heaveIn = -1;
    private static int heaveTick = -1;
    private static int bubbleTicks;
    /** 0..1 how hard the wake is pushing you right now (eased), and its rocking phase. */
    private static float wake;
    private static float wakePhase;
    @Nullable
    private static Pass pass;

    private enum Kind {
        /** Under you while you swim at the surface; casts the shadow, heaves the water. */
        UNDER,
        /** Across your view, in front of you. */
        CROSS,
        /** From behind, under your feet, on ahead. */
        BENEATH,
        /** Across, over your head. */
        ABOVE
    }

    /** One pass. Its line runs through (cx, cy, cz) along (dirX, dirZ); the head starts {@link #PASS_START} before it. */
    private static final class Pass {
        final Kind kind;
        final double dirX;
        final double dirZ;
        final double speed;
        final long start;
        double cx;
        double cy;
        double cz;
        /** Sideways swing of the head toward you (blocks, signed along the side vector). */
        float look;
        boolean closestDone;
        final SwimSound sound;

        Pass(Kind kind, double cx, double cy, double cz, double dirX, double dirZ, double speed, long start) {
            this.kind = kind;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.dirX = dirX;
            this.dirZ = dirZ;
            this.speed = speed;
            this.start = start;
            this.sound = new SwimSound(cx, cy, cz);
        }

        double headAlong(float time) {
            return -PASS_START + time * speed;
        }

        boolean over(float time) {
            return headAlong(time) - BODY_LENGTH > PASS_START;
        }
    }

    private Level7Thing() {}

    /** 0..1: how interested in you it is (rises the longer you stay in the sea). */
    public static float dread() {
        return dread;
    }

    static void reset() {
        dread = 0.0f;
        groanIn = -1;
        passIn = -1;
        heaveIn = -1;
        heaveTick = -1;
        bubbleTicks = 0;
        wake = 0.0f;
        endPass();
    }

    private static void endPass() {
        if (pass != null) {
            pass.sound.release();
            pass = null;
        }
    }

    /**
     * Test command ({@code /backrooms thing}): {@code dreadOnly} jumps to full dread; otherwise a
     * pass of whatever kind fits where you are starts right now, even if its path is not clear.
     */
    public static void debug(boolean dreadOnly) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !Level7Atmosphere.inLevel7()) {
            return;
        }
        dread = 1.0f;
        if (!dreadOnly) {
            endPass();
            Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
            startPass(mc, mc.player, eye, Level7Layout.SURFACE_Y + 1 - eye.y, Level7Client.underwater(), true);
        }
    }

    /** Every client tick in Level 7 (not paused). */
    static void tick(Minecraft mc, Player player, Vec3 eye, double depth, boolean underwater) {
        boolean inSea = player.isInWater() && player.getY() < Level7Layout.CEILING_BOTTOM_Y;
        if (inSea) {
            float share = Mth.lerp((float) Mth.clamp(depth / DREAD_FULL_DEPTH, 0.0, 1.0), DREAD_SURFACE_SHARE, 1.0f);
            dread = Math.min(1.0f, dread + DREAD_RISE * share);
        } else {
            dread = Math.max(0.0f, dread - DREAD_FALL);
        }
        tickGroans(mc, eye, underwater);
        tickHeave(mc, player);
        if (pass != null) {
            tickPass(mc, player, eye);
        } else if (inSea) {
            schedulePass(mc, player, eye, depth, underwater);
        }
        tickBubbles(mc, eye);
        wake *= 0.96f;
        // Wrapped after 10 turns: the pitch rocks at 0.7x, and 7 of its turns fit exactly, so no jump.
        wakePhase = (wakePhase + 0.12f) % (Mth.TWO_PI * 10.0f);
    }

    // ==========================================================================================
    // Sounds of its own, apart from passes
    // ==========================================================================================

    /** A groan from somewhere below: closer and more often as the dread grows. */
    private static void tickGroans(Minecraft mc, Vec3 eye, boolean underwater) {
        if (groanIn < 0) {
            groanIn = Mth.nextInt(RANDOM, groanMin() / 2, groanMax());
        }
        if (--groanIn > 0) {
            return;
        }
        groanIn = Mth.nextInt(RANDOM, groanMin(), groanMax());
        float angle = RANDOM.nextFloat() * Mth.TWO_PI;
        double distance = Mth.lerp(dread, 45.0, 22.0) + RANDOM.nextDouble() * 15.0;
        double below = Mth.lerp(dread, 25.0, 10.0) + RANDOM.nextDouble() * 25.0;
        playAt(mc, ModSounds.L7_GROAN.get(), eye.x + Mth.cos(angle) * distance, eye.y - below, eye.z + Mth.sin(angle) * distance,
                underwater ? 6.0f : 4.0f, 0.65f + RANDOM.nextFloat() * 0.3f);
    }

    private static int groanMin() {
        return (int) Mth.lerp(dread, GROAN_MIN_CALM, GROAN_MIN_DREAD);
    }

    private static int groanMax() {
        return (int) Mth.lerp(dread, GROAN_MAX_CALM, GROAN_MAX_DREAD);
    }

    /**
     * The swell: started by a pass under you, or now and then by itself near the top while no pass
     * is on (it went by just out of sight; on the shallow shelf round the room, where it can't
     * pass under you, this is all you feel of it).
     */
    private static void tickHeave(Minecraft mc, Player player) {
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
        double depth = Level7Layout.SURFACE_Y + 1 - player.getY();
        if (!player.isInWater() || depth > 8.0 || pass != null) {
            return;
        }
        if (heaveIn < 0) {
            heaveIn = Mth.nextInt(RANDOM, HEAVE_MIN_TICKS / 2, HEAVE_MAX_TICKS);
        }
        if (--heaveIn <= 0) {
            startHeave(mc);
        }
    }

    private static void startHeave(Minecraft mc) {
        heaveIn = Mth.nextInt(RANDOM, HEAVE_MIN_TICKS, HEAVE_MAX_TICKS);
        heaveTick = 0;
        playFlat(mc, ModSounds.L7_HEAVE.get(), 1.0f, 0.85f + RANDOM.nextFloat() * 0.2f);
    }

    // ==========================================================================================
    // Passes
    // ==========================================================================================

    private static void schedulePass(Minecraft mc, Player player, Vec3 eye, double depth, boolean underwater) {
        if (passIn < 0) {
            passIn = Mth.nextInt(RANDOM, FIRST_PASS_MIN, FIRST_PASS_MAX);
        }
        if (--passIn > 0) {
            return;
        }
        if (startPass(mc, player, eye, depth, underwater, false)) {
            passIn = Mth.nextInt(RANDOM, (int) Mth.lerp(dread, PASS_MIN_CALM, PASS_MIN_DREAD),
                    (int) Mth.lerp(dread, PASS_MAX_CALM, PASS_MAX_DREAD));
        } else {
            passIn = PASS_RETRY;
        }
    }

    /** Picks a kind and a line for a pass; false if no line has open water (unless {@code forced}). */
    private static boolean startPass(Minecraft mc, Player player, Vec3 eye, double depth, boolean underwater, boolean forced) {
        ClientLevel level = mc.level;
        if (level == null) {
            return false;
        }
        boolean deep = underwater && depth >= CROSS_MIN_DEPTH;
        float roll = RANDOM.nextFloat();
        Kind kind;
        if (!deep) {
            kind = Kind.UNDER;
        } else if (dread > 0.35f && roll < 0.3f) {
            kind = Kind.BENEATH;
        } else if (dread > 0.6f && depth > 40.0 && roll < 0.45f) {
            kind = Kind.ABOVE;
        } else {
            kind = Kind.CROSS;
        }
        double lowest = Level7Layout.DEEP_TOP_Y + BODY_RADIUS + 3.0;
        double highest = Level7Layout.SURFACE_Y - BODY_RADIUS - 2.0;
        // Up to four tries, each turned a quarter further round, to find open water for its line.
        for (int attempt = 0; attempt < 4; attempt++) {
            float yaw = (player.getYRot() + attempt * 90.0f) * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw);
            double fz = Mth.cos(yaw);
            double side = RANDOM.nextBoolean() ? 1.0 : -1.0;
            double dirX;
            double dirZ;
            double cx = eye.x;
            double cz = eye.z;
            double cy;
            double ahead = Mth.lerp(dread, AHEAD_CALM, AHEAD_DREAD) + RANDOM.nextDouble() * AHEAD_JITTER;
            switch (kind) {
                case UNDER -> {
                    float a = RANDOM.nextFloat() * Mth.TWO_PI;
                    dirX = Mth.cos(a);
                    dirZ = Mth.sin(a);
                    cy = Level7Layout.SURFACE_Y - Mth.lerp(dread, UNDER_DEPTH_CALM, UNDER_DEPTH_DREAD);
                }
                case BENEATH -> {
                    dirX = fx;
                    dirZ = fz;
                    cy = eye.y - (BODY_RADIUS + 3.0 + RANDOM.nextDouble() * 4.0);
                }
                case ABOVE -> {
                    dirX = -fz * side;
                    dirZ = fx * side;
                    cx += fx * ahead * 0.5;
                    cz += fz * ahead * 0.5;
                    cy = eye.y + BODY_RADIUS + 4.0 + RANDOM.nextDouble() * 3.0;
                }
                default -> {
                    dirX = -fz * side;
                    dirZ = fx * side;
                    cx += fx * ahead;
                    cz += fz * ahead;
                    cy = eye.y - (-3.0 + RANDOM.nextDouble() * 10.0);
                }
            }
            cy = Mth.clamp(cy, lowest, highest);
            if (forced || openWater(level, cx, cy, cz, dirX, dirZ)) {
                double speed = kind == Kind.UNDER ? SPEED_UNDER : SPEED_CROSS;
                pass = new Pass(kind, cx, cy, cz, dirX, dirZ, speed, level.getGameTime());
                mc.getSoundManager().play(pass.sound);
                if (kind != Kind.UNDER && RANDOM.nextFloat() < 0.5f) {
                    // It announces itself from where its head is, far off along its line.
                    playAt(mc, ModSounds.L7_GROAN.get(), cx - dirX * PASS_START * 0.6, cy, cz - dirZ * PASS_START * 0.6, 6.0f, 0.55f);
                }
                return true;
            }
        }
        return false;
    }

    /** Water all along the line (and at the top of the body) where it will be near you. */
    private static boolean openWater(ClientLevel level, double cx, double cy, double cz, double dirX, double dirZ) {
        for (int k = -2; k <= 2; k++) {
            double x = cx + dirX * k * 30.0;
            double z = cz + dirZ * k * 30.0;
            if (!water(level, x, cy, z) || !water(level, x, cy + BODY_RADIUS * 0.6, z) || !water(level, x, cy - BODY_RADIUS * 0.6, z)) {
                return false;
            }
        }
        return true;
    }

    private static boolean water(ClientLevel level, double x, double y, double z) {
        CURSOR.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        return level.getFluidState(CURSOR).is(FluidTags.WATER);
    }

    private static void tickPass(Minecraft mc, Player player, Vec3 eye) {
        Pass p = pass;
        ClientLevel level = mc.level;
        if (p == null || level == null) {
            return;
        }
        float time = level.getGameTime() - p.start;
        if (p.over(time) || player.getY() >= Level7Layout.CEILING_BOTTOM_Y) {
            endPass();
            return;
        }
        double sideX = -p.dirZ;
        double sideZ = p.dirX;
        double head = p.headAlong(time);
        double ex = eye.x - p.cx;
        double ez = eye.z - p.cz;
        double along = ex * p.dirX + ez * p.dirZ;
        double lateral = ex * sideX + ez * sideZ;
        // Under you / beneath you: it keeps under you until its head has gone by.
        if ((p.kind == Kind.UNDER || p.kind == Kind.BENEATH) && head < along + 20.0) {
            double shift = Mth.clamp(lateral, -STEER, STEER);
            p.cx += sideX * shift;
            p.cz += sideZ * shift;
            lateral -= shift;
        }
        // The nearest part of the body to you.
        double nearAlong = Mth.clamp(along, head - BODY_LENGTH, head);
        float s = (float) ((head - nearAlong) / BODY_LENGTH);
        double nx = p.cx + p.dirX * nearAlong;
        double nz = p.cz + p.dirZ * nearAlong;
        double radius = BODY_RADIUS * profile(s);
        double dy = eye.y - p.cy;
        double dist = Math.sqrt((eye.x - nx) * (eye.x - nx) + dy * dy + (eye.z - nz) * (eye.z - nz));
        // It never swims into you: if you swim at it, it sinks or rises out of your way.
        if (dist < radius + 3.0) {
            double room = dy >= 0.0 ? -0.4 : 0.4;
            p.cy = Mth.clamp(p.cy + room, Level7Layout.DEEP_TOP_Y + BODY_RADIUS + 3.0, Level7Layout.SURFACE_Y - BODY_RADIUS - 2.0);
        }
        p.sound.feed(nx, p.cy, nz, 1.0f);

        // At high dread the head swings toward you while it is near.
        float lookTarget = 0.0f;
        if (dread > 0.5f && dist < 40.0 && Math.abs(lateral) > radius + 4.0) {
            lookTarget = (float) (Math.signum(lateral) * Math.min(LOOK_MAX, Math.abs(lateral) - radius - 4.0)) * (dread - 0.5f) * 2.0f;
        }
        p.look += (lookTarget - p.look) * 0.05f;

        if (!p.closestDone && head >= along - 6.0) {
            p.closestDone = true;
            onClosest(mc, p, nx, nz, dist, radius);
        }
        // Its wake: pushes you along with it and away, and rocks you.
        double skin = dist - radius;
        if (skin < WAKE_RANGE && player.isInWater()) {
            float k = (float) (1.0 - Math.max(0.0, skin) / WAKE_RANGE);
            double away = Math.signum(lateral);
            player.setDeltaMovement(player.getDeltaMovement().add(
                    (p.dirX + sideX * away * 0.5) * WAKE_PUSH * k, 0.0, (p.dirZ + sideZ * away * 0.5) * WAKE_PUSH * k));
            wake = Math.max(wake, k);
        }
        // A trail of bubbles off its back while you can see it.
        if (dist < 30.0) {
            for (int i = 0; i < 3; i++) {
                double a = nearAlong + (RANDOM.nextDouble() - 0.5) * 16.0;
                double sx = (RANDOM.nextDouble() - 0.5) * radius;
                spawn(level, ParticleTypes.BUBBLE_COLUMN_UP, p.cx + p.dirX * a + sideX * sx, p.cy + radius * 0.75,
                        p.cz + p.dirZ * a + sideZ * sx, 0.0, 0.05, 0.0);
            }
        }
    }

    /** Its head has reached you: the surge of water, the bubbles, the swell. */
    private static void onClosest(Minecraft mc, Pass p, double nx, double nz, double dist, double radius) {
        if (dist < 50.0) {
            playAt(mc, ModSounds.L7_THING_NEAR.get(), nx, p.cy, nz, 1.0f, 0.9f + RANDOM.nextFloat() * 0.15f);
        }
        if (dist < 30.0 || p.kind == Kind.UNDER) {
            bubbleTicks = BUBBLE_TICKS;
            playAt(mc, ModSounds.L7_THING_BUBBLES.get(), nx, p.cy + radius, nz, 1.0f, 0.85f + RANDOM.nextFloat() * 0.2f);
        }
        if (p.kind == Kind.UNDER) {
            startHeave(mc);
        } else if (dread > 0.5f && RANDOM.nextFloat() < 0.4f) {
            playAt(mc, ModSounds.L7_GROAN.get(), nx, p.cy, nz, 4.0f, 0.5f + RANDOM.nextFloat() * 0.15f);
        }
    }

    /** Bubbles boiling up around you after it went by close (and popping on the surface over it). */
    private static void tickBubbles(Minecraft mc, Vec3 eye) {
        ClientLevel level = mc.level;
        if (bubbleTicks <= 0 || level == null) {
            return;
        }
        bubbleTicks--;
        float strength = bubbleTicks / (float) BUBBLE_TICKS;
        int count = 2 + (int) (10 * strength);
        Pass p = pass;
        double bottom = p != null ? p.cy : eye.y - 8.0;
        for (int i = 0; i < count; i++) {
            double x = eye.x + (RANDOM.nextDouble() - 0.5) * 10.0;
            double z = eye.z + (RANDOM.nextDouble() - 0.5) * 10.0;
            double y = Mth.lerp(RANDOM.nextDouble(), bottom, Math.min(eye.y + 2.0, Level7Layout.SURFACE_Y + 0.5));
            spawn(level, i % 3 == 0 ? ParticleTypes.BUBBLE : ParticleTypes.BUBBLE_COLUMN_UP, x, y, z,
                    (RANDOM.nextDouble() - 0.5) * 0.05, 0.06, (RANDOM.nextDouble() - 0.5) * 0.05);
        }
        // Near the top, the surface boils: pops and splashes you can see from above the water.
        if (eye.y > Level7Layout.SURFACE_Y - 8.0) {
            for (int i = 0; i < 1 + (int) (5 * strength); i++) {
                double x = eye.x + (RANDOM.nextDouble() - 0.5) * 12.0;
                double z = eye.z + (RANDOM.nextDouble() - 0.5) * 12.0;
                spawn(level, ParticleTypes.BUBBLE_POP, x, Level7Layout.SURFACE_Y + 0.9, z, 0.0, 0.02, 0.0);
                if (RANDOM.nextFloat() < 0.5f) {
                    spawn(level, ParticleTypes.SPLASH, x, Level7Layout.SURFACE_Y + 1.0, z, 0.0, 0.1, 0.0);
                }
            }
        }
    }

    private static void spawn(ClientLevel level, ParticleOptions type, double x, double y, double z, double vx, double vy, double vz) {
        level.addParticle(type, x, y, z, vx, vy, vz);
    }

    // ==========================================================================================
    // Camera
    // ==========================================================================================

    /** The slow roll of a swell and the rocking of its wake, scaled by the view-tilt setting. */
    static void modifyAngles(ViewportEvent.ComputeCameraAngles event, float partial) {
        float tilt = ClientSettings.tilt();
        if (tilt <= 0.0f) {
            return;
        }
        if (heaveTick >= 0) {
            float t = (heaveTick + partial) / HEAVE_LENGTH;
            event.setRoll(event.getRoll() + tilt * 3.0f * Mth.sin(t * Mth.PI) * Mth.sin(t * Mth.TWO_PI * 2.0f));
        }
        if (wake > 0.01f) {
            float phase = wakePhase + partial * 0.12f;
            event.setRoll(event.getRoll() + tilt * WAKE_ROLL * wake * Mth.sin(phase));
            event.setPitch(event.getPitch() + tilt * WAKE_PITCH * wake * Mth.sin(phase * 0.7f + 1.0f));
        }
    }

    // ==========================================================================================
    // Drawing
    // ==========================================================================================

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        Pass p = pass;
        if (p == null) {
            return;
        }
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            drawBody(event, p);
        } else if (stage == (WaterRenderer.drawingWater() ? RenderLevelStageEvent.Stage.AFTER_LEVEL
                : RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)) {
            drawShadow(event, p);
        }
    }

    private static float passTime(RenderLevelStageEvent event, Pass p) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0.0f
                : (mc.level.getGameTime() - p.start) + event.getPartialTick().getGameTimeDeltaPartialTick(false);
    }

    /** Fills {@link #RX}.. with the ring centres (relative to the camera) and radii at {@code time}. */
    private static void computeRings(Pass p, float time, Vec3 cam) {
        double sideX = -p.dirZ;
        double sideZ = p.dirX;
        double head = p.headAlong(time);
        for (int i = 0; i <= RINGS; i++) {
            float s = i / (float) RINGS;
            double along = head - s * BODY_LENGTH;
            // A slow side-to-side wave that grows toward the tail, a gentle rise and fall, and the
            // head turned toward you (fading out down the neck).
            double sway = 3.0 * s * Mth.sin(s * 9.0f - time * 0.05f);
            if (s < 0.35f) {
                float neck = 1.0f - s / 0.35f;
                sway += p.look * neck * neck;
            }
            double rise = 1.5 * Mth.sin(s * 4.0f - time * 0.03f);
            RX[i] = p.cx + p.dirX * along + sideX * sway - cam.x;
            RY[i] = p.cy + rise - cam.y;
            RZ[i] = p.cz + p.dirZ * along + sideZ * sway - cam.z;
            RR[i] = BODY_RADIUS * profile(s);
        }
    }

    /**
     * The body: rings of quads along a long, blunt-headed, tapering shape that sways as it swims,
     * drawn like an entity so the murk swallows it with distance the same way as everything else
     * (a darker mass against the teal near the top). Then its eyes and flank lights, which the
     * fog is kept off so they still show as pale points where the body itself is lost in the black.
     */
    private static void drawBody(RenderLevelStageEvent event, Pass p) {
        Minecraft mc = Minecraft.getInstance();
        float time = passTime(event, p);
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        computeRings(p, time, cam);
        float sideX = (float) -p.dirZ;
        float sideZ = (float) p.dirX;

        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // No culling: the inside of the body is never seen anyway, and it saves caring which way round the quads wind.
        RenderType bodyType = RenderType.entityCutoutNoCull(SKIN);
        VertexConsumer out = buffers.getBuffer(bodyType);
        for (int i = 0; i < RINGS; i++) {
            for (int j = 0; j < SIDES; j++) {
                float a0 = j * Mth.TWO_PI / SIDES;
                float a1 = (j + 1) * Mth.TWO_PI / SIDES;
                ring(out, pose, i, sideX, sideZ, a0, i / (float) RINGS, j / (float) SIDES);
                ring(out, pose, i + 1, sideX, sideZ, a0, (i + 1) / (float) RINGS, j / (float) SIDES);
                ring(out, pose, i + 1, sideX, sideZ, a1, (i + 1) / (float) RINGS, (j + 1) / (float) SIDES);
                ring(out, pose, i, sideX, sideZ, a1, i / (float) RINGS, (j + 1) / (float) SIDES);
            }
        }
        // The tail: one tall flat fluke behind the last ring.
        float tx = (float) RX[RINGS];
        float ty = (float) RY[RINGS];
        float tz = (float) RZ[RINGS];
        float fx = (float) (tx - p.dirX * 6.0);
        float fz = (float) (tz - p.dirZ * 6.0);
        vertex(out, pose, tx, ty - 1.0f, tz, 0.0f, 0.0f, sideX, 0.0f, sideZ);
        vertex(out, pose, fx, ty - 7.0f, fz, 1.0f, 0.0f, sideX, 0.0f, sideZ);
        vertex(out, pose, fx, ty + 7.0f, fz, 1.0f, 1.0f, sideX, 0.0f, sideZ);
        vertex(out, pose, tx, ty + 1.0f, tz, 0.0f, 1.0f, sideX, 0.0f, sideZ);
        buffers.endBatch(bodyType);

        // Glints, faded out far off (beyond the murk a pair of eyes alone would be too much).
        double headDist = Math.sqrt(RX[EYE_RING] * RX[EYE_RING] + RY[EYE_RING] * RY[EYE_RING] + RZ[EYE_RING] * RZ[EYE_RING]);
        float fade = (float) Mth.clamp((GLINT_FADE_END - headDist) / (GLINT_FADE_END - GLINT_FADE_START), 0.0, 1.0);
        if (fade <= 0.0f) {
            return;
        }
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        RenderType coreType = RenderType.text(GLOW);
        RenderType haloType = RenderType.eyes(GLOW);
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        // The fog would take them with the body; their brightness falls off with the camcorder's
        // haze instead (the core writes depth, so the haze sees them as near).
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        VertexConsumer core = buffers.getBuffer(coreType);
        glints(core, pose, sideX, sideZ, left, up, time, fade, false);
        buffers.endBatch(coreType);
        VertexConsumer halo = buffers.getBuffer(haloType);
        glints(halo, pose, sideX, sideZ, left, up, time, fade, true);
        buffers.endBatch(haloType);
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
    }

    /** Both eyes, then the two rows of flank lights (a slow wave of brightness running down them). */
    private static void glints(VertexConsumer out, PoseStack.Pose pose, float sideX, float sideZ, Vector3f left, Vector3f up,
                               float time, float fade, boolean halo) {
        float eye = fade * (halo ? 0.55f : 0.95f);
        for (int k = 0; k < 2; k++) {
            float angle = k == 0 ? EYE_ANGLE : Mth.PI - EYE_ANGLE;
            glint(out, pose, EYE_RING, angle, sideX, sideZ, left, up, halo ? EYE_HALO : EYE_CORE,
                    0.92f * eye, 0.90f * eye, 0.70f * eye, halo);
        }
        for (int i = LIGHT_FIRST; i <= LIGHT_LAST; i += 2) {
            float b = fade * (halo ? 0.18f : 0.32f) * (0.55f + 0.45f * Mth.sin(time * 0.08f - i * 0.5f));
            glint(out, pose, i, LIGHT_ANGLE, sideX, sideZ, left, up, halo ? LIGHT_HALO : LIGHT_CORE, 0.65f * b, 0.85f * b, 0.80f * b, halo);
            glint(out, pose, i, Mth.PI - LIGHT_ANGLE, sideX, sideZ, left, up, halo ? LIGHT_HALO : LIGHT_CORE, 0.65f * b, 0.85f * b, 0.80f * b, halo);
        }
    }

    /** One camera-facing glow on the skin at ring {@code i}, {@code angle} round the body, nudged toward the camera. */
    private static void glint(VertexConsumer out, PoseStack.Pose pose, int i, float angle, float sideX, float sideZ,
                              Vector3f left, Vector3f up, float size, float r, float g, float b, boolean halo) {
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        float rad = RR[i] * 1.02f;
        float x = (float) RX[i] + sideX * cos * rad;
        float y = (float) RY[i] + sin * rad * 0.75f;
        float z = (float) RZ[i] + sideZ * cos * rad;
        // Pull it a little toward the camera (which is at 0,0,0 here) so the skin doesn't cut it.
        float len = Mth.sqrt(x * x + y * y + z * z);
        if (len > 1.0e-3f) {
            float pull = Math.min(0.8f, len * 0.5f) / len;
            x -= x * pull;
            y -= y * pull;
            z -= z * pull;
        }
        float lx = left.x() * size;
        float ly = left.y() * size;
        float lz = left.z() * size;
        float ux = up.x() * size;
        float uy = up.y() * size;
        float uz = up.z() * size;
        int ri = (int) (Mth.clamp(r, 0.0f, 1.0f) * 255.0f);
        int gi = (int) (Mth.clamp(g, 0.0f, 1.0f) * 255.0f);
        int bi = (int) (Mth.clamp(b, 0.0f, 1.0f) * 255.0f);
        glintVertex(out, pose, x + lx - ux, y + ly - uy, z + lz - uz, 0.0f, 1.0f, ri, gi, bi, halo);
        glintVertex(out, pose, x - lx - ux, y - ly - uy, z - lz - uz, 1.0f, 1.0f, ri, gi, bi, halo);
        glintVertex(out, pose, x - lx + ux, y - ly + uy, z - lz + uz, 1.0f, 0.0f, ri, gi, bi, halo);
        glintVertex(out, pose, x + lx + ux, y + ly + uy, z + lz + uz, 0.0f, 0.0f, ri, gi, bi, halo);
    }

    private static void glintVertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                                    int r, int g, int b, boolean halo) {
        out.addVertex(pose, x, y, z).setColor(r, g, b, 255).setUv(u, v);
        if (halo) {
            // RenderType.eyes is the full entity format.
            out.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0f, 1.0f, 0.0f);
        } else {
            out.setLight(LightTexture.FULL_BRIGHT);
        }
    }

    /**
     * Its shadow on the surface: the body's outline seen from above, widened and soft-edged as if
     * blurred by the water, darkening the surface as it glides under you. Only drawn with the
     * camera above the water (from below you see the body itself), and only while the body is
     * near enough to the top to cast one. The matrices are set explicitly: after our own water
     * (AFTER_LEVEL) the level's view matrix is no longer on the stack.
     */
    private static void drawShadow(RenderLevelStageEvent event, Pass p) {
        Vec3 cam = event.getCamera().getPosition();
        if (Level7Client.underwater() || cam.y < Level7Layout.SURFACE_Y + 0.9) {
            return;
        }
        double depthBelow = Level7Layout.SURFACE_Y + 1 - p.cy;
        float strength = SHADOW_ALPHA * (float) Mth.clamp((SHADOW_MAX_DEPTH - depthBelow) / 10.0, 0.0, 1.0);
        if (strength <= 0.0f) {
            return;
        }
        float time = passTime(event, p);
        computeRings(p, time, cam);
        float sideX = (float) -p.dirZ;
        float sideZ = (float) p.dirX;
        float y = (float) (SHADOW_Y - cam.y);
        // Deeper = wider and softer.
        float widen = 1.25f + (float) depthBelow * 0.02f;

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        // The level's projection is normally still in place here; only replace it (which copies
        // the matrix) if something changed it.
        boolean ownProjection = !RenderSystem.getProjectionMatrix().equals(event.getProjectionMatrix());
        if (ownProjection) {
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        }

        PoseStack.Pose pose = LATE_POSE.last();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucent(SKIN);
        VertexConsumer out = buffers.getBuffer(type);
        for (int i = 0; i < RINGS; i++) {
            float a0 = strength * lengthFade(i / (float) RINGS);
            float a1 = strength * lengthFade((i + 1) / (float) RINGS);
            float w0 = RR[i] * widen + 1.5f;
            float w1 = RR[i + 1] * widen + 1.5f;
            // Three strips across: soft edge, solid middle, soft edge.
            shadowStrip(out, pose, i, y, sideX, sideZ, -w0, -w1, 0.0f, 0.0f, -0.45f * w0, -0.45f * w1, a0, a1);
            shadowStrip(out, pose, i, y, sideX, sideZ, -0.45f * w0, -0.45f * w1, a0, a1, 0.45f * w0, 0.45f * w1, a0, a1);
            shadowStrip(out, pose, i, y, sideX, sideZ, 0.45f * w0, 0.45f * w1, a0, a1, w0, w1, 0.0f, 0.0f);
        }
        buffers.endBatch(type);

        if (ownProjection) {
            RenderSystem.restoreProjectionMatrix();
        }
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    /** Shadow strength along the body: fades in over the snout and out toward the tail. */
    private static float lengthFade(float s) {
        return Mth.clamp(s / 0.06f, 0.0f, 1.0f) * Mth.clamp((1.0f - s) / 0.25f, 0.0f, 1.0f);
    }

    /** One quad of the shadow between ring i and i+1, from offset (o0 at ring i, o1 at i+1) to (p0, p1) sideways. */
    private static void shadowStrip(VertexConsumer out, PoseStack.Pose pose, int i, float y, float sideX, float sideZ,
                                    float o0, float o1, float oa0, float oa1, float p0, float p1, float pa0, float pa1) {
        float x0 = (float) RX[i];
        float z0 = (float) RZ[i];
        float x1 = (float) RX[i + 1];
        float z1 = (float) RZ[i + 1];
        shadowVertex(out, pose, x0 + sideX * o0, y, z0 + sideZ * o0, oa0);
        shadowVertex(out, pose, x1 + sideX * o1, y, z1 + sideZ * o1, oa1);
        shadowVertex(out, pose, x1 + sideX * p1, y, z1 + sideZ * p1, pa1);
        shadowVertex(out, pose, x0 + sideX * p0, y, z0 + sideZ * p0, pa0);
    }

    private static void shadowVertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, float alpha) {
        out.addVertex(pose, x, y, z)
                .setColor(0, 0, 0, (int) (Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f))
                .setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.pack(0, 0))
                .setNormal(pose, 0.0f, 1.0f, 0.0f);
    }

    /** Body thickness along its length (0 = head, 1 = tail): a blunt head, then a long taper. */
    private static float profile(float s) {
        if (s < 0.15f) {
            return 0.35f + 0.65f * Mth.sin(s / 0.15f * Mth.HALF_PI);
        }
        return 1.0f - 0.9f * (float) Math.pow((s - 0.15f) / 0.85f, 1.6);
    }

    /** One vertex on ring {@code i}: {@code angle} round the body (a slightly flattened oval). */
    private static void ring(VertexConsumer out, PoseStack.Pose pose, int i, float sideX, float sideZ, float angle,
                             float u, float v) {
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        float nx = sideX * cos;
        float nz = sideZ * cos;
        float r = RR[i];
        vertex(out, pose, (float) RX[i] + nx * r, (float) RY[i] + sin * r * 0.75f, (float) RZ[i] + nz * r,
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

    // ==========================================================================================
    // Sound helpers
    // ==========================================================================================

    /** A sound with no position (inside your head, or all around you). */
    static void playFlat(Minecraft mc, SoundEvent sound, float volume, float pitch) {
        mc.getSoundManager().play(new SimpleSoundInstance(sound.getLocation(), SoundSource.AMBIENT, volume, pitch,
                RANDOM, false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
    }

    private static void playAt(Minecraft mc, SoundEvent sound, double x, double y, double z, float volume, float pitch) {
        mc.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.AMBIENT, volume, pitch, RANDOM, x, y, z));
    }

    /**
     * The loop of its body swimming, kept on the part of the body nearest you so it moves across
     * and pans as it goes by (its reach, 64 blocks, is set in sounds.json, so it can start silent
     * far off and the distance alone brings it in). Fades out by itself when no longer fed.
     */
    private static final class SwimSound extends AbstractTickableSoundInstance {
        private static final int STARVE_TICKS = 3;
        private float target;
        private int unfed;
        private boolean released;

        SwimSound(double x, double y, double z) {
            super(ModSounds.L7_THING_SWIM.get(), SoundSource.AMBIENT, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.volume = 0.0f;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        void feed(double x, double y, double z, float volume) {
            this.x = x;
            this.y = y;
            this.z = z;
            target = volume;
            unfed = 0;
        }

        void release() {
            released = true;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (released || ++unfed > STARVE_TICKS) {
                target = 0.0f;
            }
            volume += Mth.clamp(target - volume, -0.03f, 0.05f);
            if (target == 0.0f && volume <= 0.001f) {
                stop();
            }
        }
    }
}
