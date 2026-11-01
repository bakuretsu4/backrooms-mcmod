package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.Level490HuntPayload;
import com.rickr.backrooms.network.Level490ReachPayload;
import com.rickr.backrooms.network.Level490ShacklePayload;
import com.rickr.backrooms.network.Level490StrikePayload;
import com.rickr.backrooms.registry.Level490Blocks;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.layout.Level490Layout;
import com.rickr.backrooms.world.layout.Level490Spire;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Level 490's creature hunting you (docs/level-490-penance.md "The creature"; after the play-test:
 * "the main enemy... rising from the fog to chase you up the tower"). Like the Mind Flayer, our own:
 * a colossal shape of dark ash with long limbs and white eyes (drawn by client/Level490Creature).
 * Each player has their own; this keeps its state and tells that player's client.
 *
 * <p><b>On the walk</b> it builds up as you near the spire: first scraping and chains rattling from
 * below the edges; then (past a third of the way) its shape far off in the clouds, seen when
 * lightning flashes behind it; then (past half way) an arm rising out of the clouds over the edge
 * and slamming down where you stand. Keep moving: standing about out in the open makes it come
 * sooner. A roof over you (the shelters, the chapel) or a lit lantern near you keeps it off.
 *
 * <p><b>On the tower</b> (after the play-test: "move through the sky, not climb; start from the very
 * bottom; way too quick") it rises out of the depths below the clouds as you start up the outside
 * stair, slowly, hanging in the sky off the tower and drifting round it after you, a little slower
 * than you climb and faster whenever you stop. If it rises level with you, a hand reaches across
 * for you: keep walking and it closes on nothing. Now and then a hand slams down onto the stair
 * ahead of you: get past before it lands, or wait (and let it gain). Inside (the half-way room and
 * the stair inside) it can't reach you; it waits.
 *
 * <p><b>At the top</b> it hauls itself up and holds the top of the tower in its hands, its huge
 * head over the edge staring at you. It does nothing more: as you cross to the chain and climb it,
 * it lets go and floats in the sky, following, watching you go.
 *
 * <p><b>If it catches you</b> the picture breaks up and you wake chained to a pillar on a
 * punishment platform beside the route behind you (world/layout/Level490Layout#punishmentSpots);
 * pull at the chains (jump) until they give, then find your way back.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level490Hunt {
    public static final int NONE = 0;
    public static final int DISTANT = 1;
    public static final int CLIMB = 2;
    public static final int HOLD = 3;
    public static final int FLOAT = 4;

    /** Where along the walk the signs, the shapes and the reaches begin. */
    private static final double SIGNS_FROM = 0.12;
    private static final double SHAPES_FROM = 0.3;
    private static final double REACHES_FROM = 0.5;
    /** Ticks from a hand showing to its landing, and from landing to lifting away. */
    private static final int REACH_LEAD = 60;
    private static final int SLAM_LEAD = 50;
    private static final int REST = 50;
    /** Landing within this (horizontally) of you, and this vertically, it has you. */
    private static final double HIT = 2.6;
    private static final double HIT_Y = 3.0;
    /** On the tower: its head this close below you and a hand reaches across for you. */
    private static final double REACH_GAP = 8.0;
    /**
     * It starts from down here (deep under the cloud sea) and rises at least this fast (blocks a
     * second; faster the further it has to come) until it is pacing you.
     */
    private static final double DEPTHS_Y = 40.0;
    private static final double RISE = 8.0;
    /**
     * Then it keeps pace (play-test: "never saw it come up with me"; "not enough tension"): its head
     * this far below you while you keep moving; whenever you stop it closes the gap this fast (blocks a
     * second, more the longer you stand), and when you move on it drops back this fast.
     */
    private static final double PACE_GAP = 15.0;
    private static final double CLOSE = 0.5;
    private static final double CLOSE_STILL = 0.25;
    private static final double DROP_BACK = 0.6;
    /** The fastest it follows you up or down (you sprint the stair at about 2.5 a second). */
    private static final double FOLLOW = 4.0;
    /** How far out from the tower's axis it hangs in the sky while you climb, and how far round ahead of you (radians). */
    private static final double SKY_OUT = 70.0;
    private static final double AHEAD = 0.6;
    private static final int GRAB_LEAD = 25;
    /** A thrown chunk is in the air this long; the shake is felt coming this long and lasts this long (ticks). */
    private static final int THROW_LEAD = 40;
    private static final int SHAKE_LEAD = 40;
    private static final int SHAKE_TICKS = 70;
    /** Where the cloud sea's top is (what it rises out of). */
    private static final double SEA_Y = 140.0;
    /** The chains give after this many pulls; or on their own after this long. */
    private static final int PULLS = 12;
    private static final int SHACKLED_MAX = 600;
    /** After a catch it leaves you be this long. */
    private static final int MERCY = 1200;
    /** How far out from the tower's axis its head is when it holds the top. */
    private static final double HOLD_OUT = 42.0;

    private static final ResourceLocation STILL = Backrooms.id("level490_shackled_still");
    private static final ResourceLocation NO_JUMP = Backrooms.id("level490_shackled_jump");

    private static final class Hunt {
        int stage = -1;
        double menace;
        /** The tower: its head's height (NaN until it has risen) and its angle round the tower (radians, world). */
        double headY = Double.NaN;
        double headAngle;
        /** On the tower: how far below you it is keeping, and whether it has risen up to you yet. */
        double gap = PACE_GAP;
        boolean risen;
        long nextGroan;
        /** The top: the side it holds on from. */
        double holdAngle = Double.NaN;
        /** Where its head is (sent to the client). */
        double hx;
        double hy;
        double hz;
        /** Far off, on the walk: where it lurks, and until when. */
        double farX;
        double farZ;
        long farUntil;
        long nextSign;
        long nextFlash;
        long nextReach;
        long nextSlam;
        long mercyUntil;
        /** A hand on its way: {tx, ty, tz}, landing at {@code land}; 0 when none. */
        double tx;
        double ty;
        double tz;
        long land;
        long lift;
        /** Whether the hand coming down tears the stair out where it lands (the slams ahead; not a grab). */
        boolean tears;
        /** A chunk of masonry thrown at you: where it lands and when (0 when none). */
        double[] throwTo;
        long throwLand;
        long nextThrow;
        /** It shaking the tower: from when to when (game ticks). */
        long shakeFrom;
        long shakeUntil;
        long nextShake;
        /** Where you were last tick, and how you are moving (blocks a tick, smoothed): for aiming throws. */
        Vec3 prevTick;
        Vec3 vel = Vec3.ZERO;
        /** How you have been moving: where you were a second ago, and how long you have been still (seconds). */
        Vec3 last;
        double still;
        boolean shackled;
        int pulls;
        long shackledAt;
        int[] pillar;
        long lastSent;

        void reachClear() {
            land = 0;
            lift = 0;
            tears = false;
        }

        /** True while nothing else it does is under way (one act at a time). */
        boolean idle(long t) {
            return land == 0 && throwLand == 0 && t >= shakeUntil + 40;
        }
    }

    private static final Map<UUID, Hunt> HUNTS = new HashMap<>();

    private Level490Hunt() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean here = BackroomsLevel.fromDimension(player.level().dimension()) == BackroomsLevel.LEVEL_490;
        Hunt h = HUNTS.get(player.getUUID());
        if (!here) {
            if (h != null) {
                unshackle(player, h, false);
                HUNTS.remove(player.getUUID());
            }
            return;
        }
        if (h == null) {
            h = new Hunt();
            HUNTS.put(player.getUUID(), h);
        }
        if (h.shackled) {
            if (player.serverLevel().getGameTime() - h.shackledAt > SHACKLED_MAX) {
                unshackle(player, h, true);
            }
            return;
        }
        if (player.isCreative() || player.isSpectator() || Travel.isMoving(player)) {
            send(player, h, NONE, 0);
            return;
        }
        hunt(player, h);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Hunt h = HUNTS.remove(player.getUUID());
            if (h != null) {
                unshackle(player, h, false);
            }
        }
    }

    private static void hunt(ServerPlayer player, Hunt h) {
        ServerLevel level = player.serverLevel();
        long t = level.getGameTime();
        RandomSource random = level.getRandom();
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_490);
        Level490Spire spire = Level490Layout.spireAt(seed, player.getBlockX(), player.getBlockZ());
        double cx = spire.centreX() + 0.5;
        double cz = spire.centreZ() + 0.5;
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        double dSite = Math.hypot(x - cx, z - cz);
        // How you are moving: once a second, how far you went.
        Vec3 now = player.position();
        if (t % 20 == 0) {
            if (h.last != null && now.distanceTo(h.last) > 30) {
                h.headY = Double.NaN; // fell and woke elsewhere: it starts again from the clouds
            }
            double moved = h.last == null ? 0 : now.distanceTo(h.last);
            h.still = moved < 1.0 ? Math.min(6, h.still + 1) : Math.max(0, h.still - 2);
            h.last = now;
        }
        double outside = spire.outsideProgress(x, y, z);
        boolean inside = outside < 0 && spire.inside(x, y, z);
        boolean top = dSite < 15 && y >= Level490Spire.TOP - 1.5 && y < Level490Spire.TOP + 3;
        boolean past = !top && dSite < 75 && y >= Level490Spire.TOP - 1.5;

        // ---- the top: it holds the tower, then floats after you as you climb the chain ----
        if (top || past) {
            if (Double.isNaN(h.holdAngle)) {
                h.holdAngle = Double.isNaN(h.headY) ? Math.atan2(z - cz, x - cx) : h.headAngle;
                h.reachClear();
                sound(player, ModSounds.LEVEL490_CHAIN_JOLT.get(), cx + Math.cos(h.holdAngle) * 20, Level490Spire.TOP, cz + Math.sin(h.holdAngle) * 20,
                        2.0f, 0.55f);
                sound(player, ModSounds.LEVEL490_RUMBLE.get(), cx, Level490Spire.TOP - 20, cz, 2.0f, 0.6f);
            }
            double ax = Math.cos(h.holdAngle);
            double az = Math.sin(h.holdAngle);
            if (top) {
                h.hx = cx + ax * HOLD_OUT;
                h.hy = Level490Spire.TOP + 20;
                h.hz = cz + az * HOLD_OUT;
            } else {
                // Floating off that side of the tower at your height, keeping 70 out, drifting after you.
                double wantX = cx + ax * 95;
                double wantZ = cz + az * 95;
                double wantY = y + 6;
                h.hx += (wantX - h.hx) * 0.02;
                h.hy += (wantY - h.hy) * 0.03;
                h.hz += (wantZ - h.hz) * 0.02;
            }
            if (t >= h.nextFlash) {
                h.nextFlash = t + 120 + random.nextInt(160);
                strike(player, h.hx + ax * 120 + (random.nextDouble() - 0.5) * 120, h.hz + az * 120 + (random.nextDouble() - 0.5) * 120,
                        random.nextFloat() < 0.4f ? Level490StrikePayload.BOLT : Level490StrikePayload.CLOUD, random);
            }
            send(player, h, top ? HOLD : FLOAT, 0);
            return;
        }
        h.holdAngle = Double.NaN;

        // ---- the tower: it rises through the sky after you ----
        boolean onTower = outside >= 0 || inside && !Double.isNaN(h.headY) && y > Level490Layout.DECK_Y + 6;
        if (onTower && t >= h.mercyUntil) {
            double angle = Math.atan2(z - cz, x - cx);
            if (Double.isNaN(h.headY)) {
                // It starts from the very bottom, far below the clouds, and comes up fast.
                h.headY = DEPTHS_Y;
                h.headAngle = angle + AHEAD;
                h.gap = PACE_GAP;
                h.risen = false;
                h.nextSlam = t + 600 + random.nextInt(300);
                h.nextThrow = t + 360 + random.nextInt(200);
                h.nextShake = t + 800 + random.nextInt(300);
                h.nextFlash = t + 60;
                sound(player, ModSounds.LEVEL490_RUMBLE.get(), cx + Math.cos(angle) * 60, SEA_Y - 20, cz + Math.sin(angle) * 60, 3.0f, 0.45f);
                sound(player, ModSounds.LEVEL490_CHAIN_JOLT.get(), cx + Math.cos(angle) * 60, SEA_Y, cz + Math.sin(angle) * 60, 3.0f, 0.5f);
            }
            // Round the tower with you through the sky, a little ahead (where you are looking as you climb).
            double diff = Math.IEEEremainder(angle + AHEAD - h.headAngle, Math.PI * 2);
            h.headAngle += Math.max(-0.035, Math.min(0.035, diff));
            if (!inside && h.risen) {
                if (h.still >= 1) {
                    // Closing in, but never past you: level with you, it reaches for you.
                    h.gap = Math.max(REACH_GAP - 2, h.gap - (CLOSE + CLOSE_STILL * h.still) / 20.0);
                } else {
                    h.gap = Math.min(PACE_GAP, h.gap + DROP_BACK / 20.0);
                }
            }
            double want = y - h.gap;
            if (!h.risen) {
                // Up out of the depths: fast, faster the further it has to come (about 20 seconds from the bottom).
                double rise = Math.max(RISE, (want - h.headY) / 8.0);
                h.headY = Math.min(want, h.headY + rise / 20.0);
                h.risen = h.headY >= want - 0.5;
            } else if (!inside) {
                h.headY += Math.max(-FOLLOW / 20.0, Math.min(FOLLOW / 20.0, want - h.headY));
            }
            double gap = y - h.headY;
            // It groans and its chains drag, louder and more often the closer it is.
            float close = (float) Math.max(0, Math.min(1, (PACE_GAP + 15 - gap) / (PACE_GAP + 15 - REACH_GAP)));
            if (h.risen && t >= h.nextGroan) {
                h.nextGroan = t + 220 - (long) (150 * close) + random.nextInt(80);
                SoundEvent groan = (random.nextFloat() < 0.5f ? ModSounds.LEVEL490_CHAIN_CREAK : random.nextBoolean()
                        ? ModSounds.LEVEL490_RUMBLE : ModSounds.LEVEL490_CHAIN_JOLT).get();
                sound(player, groan, h.hx, h.hy, h.hz, 1.5f + 2.0f * close, 0.5f + random.nextFloat() * 0.15f);
            }
            h.hx = cx + Math.cos(h.headAngle) * SKY_OUT;
            h.hy = h.headY;
            h.hz = cz + Math.sin(h.headAngle) * SKY_OUT;
            if (h.prevTick != null) {
                h.vel = h.vel.scale(0.8).add(now.subtract(h.prevTick).scale(0.2));
            }
            h.prevTick = now;
            boolean exposed = !inside && outside >= 0 && h.risen;
            if (h.land == 0 && h.throwLand == 0 && exposed && gap < REACH_GAP) {
                // Level with you: a hand reaches across for where you stand.
                startReach(player, h, Level490ReachPayload.SLAM, h.hx, h.hy - 20, h.hz, x, y, z, t + GRAB_LEAD);
            } else if (h.idle(t) && exposed && t >= h.nextSlam) {
                // A hand slams onto the stair ahead of you and tears it out.
                int f = (int) Math.min(1, Math.floor(outside));
                double sweep = Level490Spire.flightSweep(f);
                double sNow = (outside - f) * sweep;
                double ahead = sNow + Math.toDegrees(11.0 / 16.0);
                if (ahead < sweep - 10) {
                    double[] p = spire.flightPoint(f, ahead, 0.5);
                    startReach(player, h, Level490ReachPayload.SLAM, h.hx, h.hy - 20, h.hz, p[0], p[1], p[2], t + SLAM_LEAD);
                    h.tears = true;
                }
                h.nextSlam = t + 400 + random.nextInt(300) - (long) (h.still * 30);
            } else if (h.idle(t) && exposed && t >= h.nextThrow) {
                // It hurls a chunk of masonry at where you are heading.
                Vec3 aim = now.add(h.vel.scale(THROW_LEAD));
                double side = random.nextBoolean() ? 1 : -1;
                double ox = Math.cos(h.headAngle);
                double oz = Math.sin(h.headAngle);
                double fx = h.hx - oz * side * 40;
                double fz = h.hz + ox * side * 40;
                h.throwTo = new double[] {aim.x, y, aim.z};
                h.throwLand = t + THROW_LEAD;
                PacketDistributor.sendToPlayer(player, new Level490ReachPayload(Level490ReachPayload.THROW, (float) fx, (float) (h.hy + 30),
                        (float) fz, (float) aim.x, (float) y, (float) aim.z, h.throwLand, h.throwLand + 30));
                sound(player, ModSounds.LEVEL490_CHAIN_JOLT.get(), fx, h.hy + 30, fz, 2.0f, 0.5f);
                h.nextThrow = t + 300 + random.nextInt(240);
            } else if (h.idle(t) && exposed && t >= h.nextShake) {
                // It seizes the tower and shakes it.
                double[] grip = spire.surface(h.hx, y - 6, h.hz);
                h.shakeFrom = t + SHAKE_LEAD;
                h.shakeUntil = h.shakeFrom + SHAKE_TICKS;
                PacketDistributor.sendToPlayer(player, new Level490ReachPayload(Level490ReachPayload.SHAKE, (float) grip[0], (float) (y - 6),
                        (float) grip[1], (float) x, (float) y, (float) z, h.shakeFrom, h.shakeUntil));
                sound(player, ModSounds.LEVEL490_CHAIN_JOLT.get(), grip[0], y - 6, grip[1], 3.0f, 0.45f);
                h.nextShake = t + 700 + random.nextInt(500);
            }
            shaking(player, h, spire, t, inside, cx, cz, random);
            throwLanding(player, h, spire, t, cx, cz, random);
            if (t >= h.nextFlash) {
                // Lightning behind it, every 5-10 seconds, so you keep catching its whole shape.
                h.nextFlash = t + 100 + random.nextInt(100);
                double back = 140 + random.nextDouble() * 80;
                double side = (random.nextDouble() - 0.5) * 120;
                double ox = Math.cos(h.headAngle);
                double oz = Math.sin(h.headAngle);
                strike(player, h.hx + ox * back - oz * side, h.hz + oz * back + ox * side,
                        random.nextFloat() < 0.35f ? Level490StrikePayload.BOLT : Level490StrikePayload.CLOUD, random);
            }
            landing(player, h, t, inside);
            send(player, h, CLIMB, h.risen ? 0.3f + 0.7f * close : 0.2f);
            return;
        }
        if (!onTower) {
            h.headY = Double.NaN;
        }

        // ---- the walk: it builds up as you near the spire ----
        double progress = Level490Layout.walkProgress(seed, x, z);
        boolean safe = sheltered(level, player);
        if (!safe) {
            h.menace = Math.min(1, h.menace + (h.still >= 2 ? 0.002 : -0.0005));
        } else {
            h.menace = Math.max(0, h.menace - 0.002);
        }
        if (progress >= SIGNS_FROM && t >= h.nextSign) {
            // Scraping and chains rattling from somewhere below the edge.
            h.nextSign = t + 500 + random.nextInt(700);
            double a = random.nextDouble() * Math.PI * 2;
            SoundEvent sound = (random.nextBoolean() ? ModSounds.LEVEL490_CHAIN_CREAK : ModSounds.LEVEL490_COLLAPSE).get();
            sound(player, sound, x + Math.cos(a) * 14, y - 18, z + Math.sin(a) * 14, 0.9f, 0.5f + random.nextFloat() * 0.2f);
        }
        if (progress >= SHAPES_FROM) {
            if (t >= h.farUntil) {
                // Somewhere off to the side of the way, 350-550 out.
                double a = Math.atan2(cz - z, cx - x) + (random.nextBoolean() ? 1 : -1) * (0.8 + random.nextDouble() * 0.9);
                double d = 350 + random.nextDouble() * 200;
                h.farX = x + Math.cos(a) * d;
                h.farZ = z + Math.sin(a) * d;
                h.farUntil = t + 2400 + random.nextInt(2400);
            }
            h.hx = h.farX;
            h.hy = SEA_Y + 40 + 130 * progress;
            h.hz = h.farZ;
            if (t >= h.nextFlash) {
                // Lightning behind it: its shape in the flash.
                h.nextFlash = t + 400 + random.nextInt(500);
                double bx = h.farX + (h.farX - x) * 0.3;
                double bz = h.farZ + (h.farZ - z) * 0.3;
                strike(player, bx, bz, random.nextFloat() < 0.5f ? Level490StrikePayload.BOLT : Level490StrikePayload.CLOUD, random);
            }
        }
        if (progress >= REACHES_FROM && h.land == 0 && t >= h.nextReach && t >= h.mercyUntil && !safe && player.onGround()) {
            double[] edge = edgeNear(level, player, random);
            if (edge != null) {
                // Where you stand now (a step ahead if you are walking): keep moving and it misses.
                Vec3 v = player.getDeltaMovement();
                startReach(player, h, Level490ReachPayload.OVER_EDGE, edge[0], edge[1], edge[2], x + v.x * 4, y, z + v.z * 4, t + REACH_LEAD);
            }
            h.nextReach = t + 1400 - (long) (900 * h.menace) + random.nextInt(600);
        }
        landing(player, h, t, safe);
        send(player, h, progress >= SHAPES_FROM ? DISTANT : NONE, (float) h.menace * 0.5f);
    }

    /** A hand comes down: does it have you? Then it lifts away. */
    private static void landing(ServerPlayer player, Hunt h, long t, boolean safe) {
        if (h.land == 0) {
            return;
        }
        if (t == h.land) {
            ServerLevel level = player.serverLevel();
            sound(player, ModSounds.LEVEL490_COLLAPSE.get(), h.tx, h.ty, h.tz, 2.0f, 0.6f);
            double dh = Math.hypot(player.getX() - h.tx, player.getZ() - h.tz);
            if (!safe && dh < HIT && Math.abs(player.getY() - h.ty) < HIT_Y && !player.isCreative() && !player.isSpectator()) {
                long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_490);
                caught(player, h, seed, h.stage == CLIMB);
                return;
            }
            // Missed: on the tower it sinks back a little before coming again; a slam tears the stair out.
            h.gap = Math.max(h.gap, REACH_GAP + 6);
            if (h.tears) {
                long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_490);
                Level490Wrecks.tear(level, Level490Layout.spireAt(seed, (int) h.tx, (int) h.tz), h.tx, h.ty, h.tz, 1.3);
            }
        }
        if (t >= h.lift) {
            h.reachClear();
        }
    }

    /** While it shakes the tower: you are shoved about, toward the edge, and the stair's edge crumbles. */
    private static void shaking(ServerPlayer player, Hunt h, Level490Spire spire, long t, boolean inside, double cx, double cz,
                                RandomSource random) {
        if (t < h.shakeFrom || t > h.shakeUntil) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (t == h.shakeFrom || t == h.shakeFrom + 35) {
            sound(player, ModSounds.LEVEL490_COLLAPSE.get(), player.getX(), player.getY() - 4, player.getZ(), 2.0f, 0.5f);
            if (!inside) {
                Level490Wrecks.crumble(level, spire, player.getX(), player.getY(), player.getZ(), t == h.shakeFrom ? 3 : 2);
            }
        }
        if (inside || (t - h.shakeFrom) % 10 != 0) {
            return;
        }
        // Out from the tower, and a little along it, either way.
        double ox = player.getX() - cx;
        double oz = player.getZ() - cz;
        double ol = Math.max(1, Math.hypot(ox, oz));
        ox /= ol;
        oz /= ol;
        double along = (random.nextDouble() - 0.5) * 0.4;
        double out = 0.18 + 0.16 * random.nextDouble();
        player.setDeltaMovement(player.getDeltaMovement().add(ox * out - oz * along, 0.06, oz * out + ox * along));
        player.hurtMarked = true;
    }

    /** A thrown chunk landing: a direct hit knocks you hard (off the stair, likely), a near miss shoves you. */
    private static void throwLanding(ServerPlayer player, Hunt h, Level490Spire spire, long t, double cx, double cz, RandomSource random) {
        if (h.throwLand == 0 || t < h.throwLand) {
            return;
        }
        double[] p = h.throwTo;
        h.throwLand = 0;
        ServerLevel level = player.serverLevel();
        sound(player, ModSounds.LEVEL490_COLLAPSE.get(), p[0], p[1], p[2], 2.0f, 0.7f);
        level.playSound(null, p[0], p[1], p[2], net.minecraft.sounds.SoundEvents.DEEPSLATE_BRICKS_BREAK, net.minecraft.sounds.SoundSource.HOSTILE,
                2.0f, 0.5f);
        double d = Math.hypot(player.getX() - p[0], player.getZ() - p[2]);
        if (Math.abs(player.getY() - p[1]) > 3 || d > 4.5 || player.isCreative() || player.isSpectator()) {
            return;
        }
        double ax = player.getX() - p[0];
        double az = player.getZ() - p[2];
        if (Math.hypot(ax, az) < 0.3) {
            ax = player.getX() - cx;
            az = player.getZ() - cz;
        }
        double al = Math.max(0.01, Math.hypot(ax, az));
        double k = d < 1.8 ? 1.0 : 0.4;
        player.setDeltaMovement(player.getDeltaMovement().add(ax / al * k, d < 1.8 ? 0.45 : 0.2, az / al * k));
        player.hurtMarked = true;
        Level490Wrecks.crumble(level, spire, p[0], p[1], p[2], 2);
    }

    private static void startReach(ServerPlayer player, Hunt h, int kind, double fx, double fy, double fz, double tx, double ty, double tz, long land) {
        h.tx = tx;
        h.ty = ty;
        h.tz = tz;
        h.land = land;
        h.lift = land + REST;
        PacketDistributor.sendToPlayer(player, new Level490ReachPayload(kind, (float) fx, (float) fy, (float) fz, (float) tx, (float) ty, (float) tz,
                land, h.lift));
        sound(player, ModSounds.LEVEL490_CHAIN_JOLT.get(), fx, fy, fz, 1.6f, 0.55f);
    }

    /** Under a roof, or near a lit lantern: it won't reach for you there. */
    private static boolean sheltered(ServerLevel level, ServerPlayer player) {
        BlockPos head = player.blockPosition().above();
        if (!level.canSeeSky(head)) {
            return true;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int r = 6;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -2; dy <= 4; dy++) {
                    p.set(head.getX() + dx, head.getY() + dy, head.getZ() + dz);
                    BlockState st = level.getBlockState(p);
                    if (st.is(Level490Blocks.LANTERN.get()) && st.getValue(com.rickr.backrooms.block.IronLanternBlock.LIT)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The nearest open edge (nothing under the deck) 4-30 blocks from you, a little below the deck: {x, y, z}. */
    private static double[] edgeNear(ServerLevel level, ServerPlayer player, RandomSource random) {
        double a0 = random.nextDouble() * Math.PI * 2;
        for (int k = 0; k < 12; k++) {
            double a = a0 + k * Math.PI / 6;
            for (int d = 4; d <= 30; d++) {
                int x = (int) Math.floor(player.getX() + Math.cos(a) * d);
                int z = (int) Math.floor(player.getZ() + Math.sin(a) * d);
                int y = player.getBlockY();
                boolean open = true;
                for (int dy = -1; dy >= -8 && open; dy--) {
                    open = level.getBlockState(new BlockPos(x, y + dy, z)).isAir();
                }
                if (open) {
                    return new double[] {x + 0.5, y - 2, z + 0.5};
                }
            }
        }
        return null;
    }

    /** It has you: the picture breaks up, you wake chained on a punishment platform behind you. */
    private static void caught(ServerPlayer player, Hunt h, long seed, boolean onTower) {
        h.reachClear();
        h.headY = Double.NaN;
        h.menace = 0;
        h.still = 0;
        h.last = null;
        List<int[]> spots = Level490Layout.punishmentSpots(seed, player.getBlockX(), player.getBlockZ());
        int[] spot;
        if (spots.isEmpty()) {
            BlockPos arrival = Travel.findSafeSpot(player.serverLevel(), BackroomsLevel.LEVEL_490, player.getBlockX(), player.getBlockZ());
            spot = new int[] {arrival.getX(), arrival.getY(), arrival.getZ(), 0};
        } else if (onTower) {
            spot = spots.get(spots.size() - 1);
        } else {
            // The last one you have passed (or the first, if none yet).
            double progress = Level490Layout.walkProgress(seed, player.getX(), player.getZ());
            spot = spots.get(0);
            for (int[] s : spots) {
                if (Level490Layout.walkProgress(seed, s[0], s[2]) <= progress) {
                    spot = s;
                }
            }
        }
        int[] pillar = Level490Layout.punishmentPillar(seed, spot);
        int[] where = spot;
        long t = player.serverLevel().getGameTime();
        h.mercyUntil = t + MERCY;
        h.nextReach = t + MERCY + 400;
        sound(player, ModSounds.LEVEL490_CHAIN_SNAP.get(), player.getX(), player.getY(), player.getZ(), 1.5f, 0.6f);
        Travel.moshTo(player, p -> {
            p.setDeltaMovement(Vec3.ZERO);
            float yaw = (float) Math.toDegrees(Math.atan2(-(pillar[0] + 0.5 - (where[0] + 0.5)), pillar[2] + 0.5 - (where[2] + 0.5)));
            Teleports.to(p, where[0] + 0.5, where[1], where[2] + 0.5, yaw, 20.0f);
            shackle(p, h, pillar);
            p.sendSystemMessage(Component.translatable("message.backrooms.level490.caught").withStyle(s -> s.withItalic(true)));
        });
    }

    private static void shackle(ServerPlayer player, Hunt h, int[] pillar) {
        h.shackled = true;
        h.pulls = 0;
        h.shackledAt = player.serverLevel().getGameTime();
        h.pillar = pillar;
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(STILL, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) {
            jump.addOrUpdateTransientModifier(new AttributeModifier(NO_JUMP, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        PacketDistributor.sendToPlayer(player, new Level490ShacklePayload(pillar[0], pillar[1], pillar[2], true));
        send(player, h, NONE, 0);
    }

    private static void unshackle(ServerPlayer player, Hunt h, boolean tellThem) {
        if (!h.shackled) {
            return;
        }
        h.shackled = false;
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(STILL);
        }
        AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) {
            jump.removeModifier(NO_JUMP);
        }
        PacketDistributor.sendToPlayer(player, new Level490ShacklePayload(0, 0, 0, false));
        if (tellThem) {
            sound(player, ModSounds.LEVEL490_CHAIN_SNAP.get(), player.getX(), player.getY() + 1, player.getZ(), 1.2f, 1.0f);
            player.sendSystemMessage(Component.translatable("message.backrooms.level490.freed").withStyle(s -> s.withItalic(true)));
        }
    }

    /** A chained player pulled at the chains (network). */
    public static void struggle(ServerPlayer player) {
        Hunt h = HUNTS.get(player.getUUID());
        if (h == null || !h.shackled) {
            return;
        }
        h.pulls++;
        sound(player, ModSounds.LEVEL490_CHAIN_CREAK.get(), player.getX(), player.getY() + 1, player.getZ(), 0.6f, 1.2f + player.getRandom().nextFloat() * 0.3f);
        if (h.pulls >= PULLS) {
            unshackle(player, h, true);
        }
    }

    /** True while the player is chained (for the falls: no falling off the platform while chained). */
    public static boolean shackled(ServerPlayer player) {
        Hunt h = HUNTS.get(player.getUUID());
        return h != null && h.shackled;
    }

    private static void send(ServerPlayer player, Hunt h, int stage, float near) {
        long t = player.serverLevel().getGameTime();
        if (stage == h.stage && stage == NONE) {
            return;
        }
        if (stage == h.stage && t - h.lastSent < 4) {
            return;
        }
        h.stage = stage;
        h.lastSent = t;
        PacketDistributor.sendToPlayer(player, new Level490HuntPayload(stage, (float) h.hx, (float) h.hy, (float) h.hz, near));
    }

    private static void strike(ServerPlayer player, double x, double z, int kind, RandomSource random) {
        PacketDistributor.sendToPlayer(player, new Level490StrikePayload(random.nextLong(), (int) x, (int) z, kind));
    }

    /** A sound only this player hears, from (x, y, z). */
    private static void sound(ServerPlayer player, SoundEvent sound, double x, double y, double z, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.HOSTILE, x, y, z,
                volume, pitch, player.getRandom().nextLong()));
    }
}
