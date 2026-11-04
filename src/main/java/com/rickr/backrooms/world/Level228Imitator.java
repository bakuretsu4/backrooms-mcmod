package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.Level228ImitatorPayload;
import com.rickr.backrooms.player.SanityService;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.layout.Level228Plan;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Level 228's imitator: something that wears one of you and stands behind you in the mirrors.
 *
 * <p><b>What it is.</b> Never a body in the maze. The server keeps a point in REAL coordinates and
 * clients draw it only at that point's images (client/level228/Level228ImitatorClient through the
 * reflections' stand-in renderer): in the glass a figure stands a few steps behind your reflection;
 * turn round to the real corridor and nobody is there. Because the point is the server's and the
 * images come from the shared floor plan, everyone who looks into that mirror sees the same figure.
 *
 * <p><b>An episode</b> (one per slot, at most {@value #SLOTS} at once, one per group):
 * <ol>
 *   <li>{@link #COPY}: it walks your own trail {@value #FOLLOW} blocks behind you, wearing your skin
 *       (or a teammate's), copying your every movement. The phase clock only starts once you have
 *       actually had it in a mirror: an episode nobody sees ends quietly.</li>
 *   <li>{@link #WRONG}: the copy slips, one way per episode: {@link #LATE} (it moves a beat after
 *       you), {@link #STILL} (it stops where it is, where no player stands, while you walk on) or
 *       {@link #TILT} (its head leans over while the rest of it keeps copying).</li>
 *   <li>{@link #WATCH}: it stops copying altogether and turns to look at you; one faint glass tick,
 *       nothing else. Rarely ({@value #BEHIND_CHANCE_PCT}%) it is instead suddenly standing right
 *       behind you in every reflection, put there while you weren't looking at any of them.</li>
 *   <li>Gone: after its time it vanishes the next moment you are not looking at any image of it
 *       (or as soon as you turn round on it, from right behind you).</li>
 * </ol>
 * It never chases, blocks, touches or damages anyone. Seeing it wears on sanity a little
 * ({@value #SEEN_DRAIN}/s, {@value #WATCH_DRAIN}/s while it watches). The tells that keep it from
 * ever passing for a real teammate on a close look are the client's (an unmirrored image: skin
 * details and the held item on the wrong side for a reflection; eyes that catch the light).
 *
 * <p><b>When.</b> Rarely: a player becomes due {@value #FIRST_MIN_S}-{@value #FIRST_MAX_S} s after
 * arriving, and when an episode starts everyone within {@value #GROUP_RANGE} blocks of its watcher
 * (the group) is pushed {@value #COOLDOWN_MIN_S}-{@value #COOLDOWN_MAX_S} s into the future.
 * It needs a mirror close to where it would stand, and it never starts in view. Never in the exit
 * hallway (or the final chamber, where the three mirrors must stay honest about everyone).
 *
 * <p>{@code /backrooms imitator <show|off>} drives it for testing (command/Level228ImitatorCommand).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level228Imitator {
    /** Slot empty (also the payload's "gone"). */
    public static final int NONE = 0;
    public static final int COPY = 1;
    public static final int WRONG = 2;
    public static final int WATCH = 3;

    /** Ways the copy goes wrong ({@link #WRONG}). */
    public static final int LATE = 0;
    public static final int STILL = 1;
    public static final int TILT = 2;
    private static final int KINDS = 3;

    /** Concurrent episodes; matches the reflections' figure budget. */
    public static final int SLOTS = 4;

    private static final int FIRST_MIN_S = 4 * 60;
    private static final int FIRST_MAX_S = 7 * 60;
    private static final int COOLDOWN_MIN_S = 8 * 60;
    private static final int COOLDOWN_MAX_S = 15 * 60;
    /** Players this close to the watcher count as its group (they share the cooldown). */
    private static final double GROUP_RANGE = 40.0;

    /** How far behind you it walks your trail (blocks, straight line). */
    private static final double FOLLOW = 2.6;
    /** Its most stride per tick along the trail (you sprint at 0.28): it never falls behind. */
    private static final double STRIDE = 0.34;
    /** Farther from its watcher than this (you walked away from it, or travelled) and it is gone. */
    private static final double LEASH = 24.0;
    /** It must not start (or be put) closer than this to any real player: never inside someone. */
    private static final double PERSONAL = 1.2;
    /** Images farther than this from a viewer are lost in the fog: they don't count as seen. */
    private static final double SEE_RANGE = 28.0;
    /** A mirror must be this close to where it stands, or nobody would ever see it. */
    private static final double MIRROR_NEAR = 10.0;

    private static final int COPY_MIN = 10 * 20;
    private static final int COPY_MAX = 20 * 20;
    private static final int WRONG_MIN = 7 * 20;
    private static final int WRONG_MAX = 12 * 20;
    private static final int WATCH_MIN = 6 * 20;
    private static final int WATCH_MAX = 12 * 20;
    /** After its time it waits at most this long for you to look away before it goes anyway. */
    private static final int LEAVE_MAX = 15 * 20;
    /** An episode nobody has seen within this long ends quietly. */
    private static final int UNSEEN_MAX = 60 * 20;
    /** How often the watcher's view of it is checked (ticks). */
    private static final int LOOK_EVERY = 5;

    private static final int BEHIND_CHANCE_PCT = 22;
    /** Standing right behind you: this far back from your eyes' line. */
    private static final double BEHIND_DIST = 1.1;
    /** From right behind you it goes the moment you turn this far (degrees) or step this far away. */
    private static final float BEHIND_TURN = 100.0f;
    private static final double BEHIND_STEP = 1.8;
    /** Of the episodes, this share wears a teammate's face rather than the watcher's own. */
    private static final int TEAMMATE_FACE_PCT = 35;

    private static final float SEEN_DRAIN = 0.2f;
    private static final float WATCH_DRAIN = 0.35f;
    private static final float TICK_VOLUME = 0.12f;

    /** The trail: where each player in the level was, every {@value #TRAIL_EVERY} ticks. */
    private static final int TRAIL = 64;
    private static final int TRAIL_EVERY = 2;

    private static final class Trail {
        final float[] x = new float[TRAIL];
        final float[] y = new float[TRAIL];
        final float[] z = new float[TRAIL];
        /** Samples ever taken; sample n lives at n % TRAIL while n > count - TRAIL. */
        long count;

        void add(double px, double py, double pz) {
            int i = (int) (count % TRAIL);
            x[i] = (float) px;
            y[i] = (float) py;
            z[i] = (float) pz;
            count++;
        }

        boolean has(long n) {
            return n >= 0 && n < count && n > count - TRAIL;
        }
    }

    private static final class Slot {
        int phase = NONE;
        int kind;
        boolean behind;
        UUID watcher;
        UUID wearer;
        double x;
        double y;
        double z;
        float yaw;
        /** Trail sample it stands at or walks from (absolute index). */
        long follow;
        long startedAt;
        long phaseAt;
        int phaseLen;
        boolean everSeen;
        /** Its time is up: it goes the next moment the watcher can't see it. */
        boolean leaving;
        long leavingAt;
        /** Where the watcher stood and faced when it was put right behind them. */
        double behindX;
        double behindZ;
        float behindYaw;
        // What clients were last told, so position packets go out only when it moves.
        float sentX;
        float sentY;
        float sentZ;
        float sentYaw;
    }

    private static final Slot[] SLOT = new Slot[SLOTS];
    private static final Map<UUID, Trail> TRAILS = new HashMap<>();
    /** When each player in the level next may have an episode (game time). */
    private static final Map<UUID, Long> DUE = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    /** Scratch for the floor plan's queries (server thread only). */
    private static final Level228Plan.Image[] IMAGES = new Level228Plan.Image[16];
    private static final Level228Plan.Mirror[] MIRRORS = new Level228Plan.Mirror[8];
    /** Switched off by {@code /backrooms imitator off} until {@code show}. */
    private static boolean off;

    static {
        for (int i = 0; i < SLOTS; i++) {
            SLOT[i] = new Slot();
        }
        for (int i = 0; i < IMAGES.length; i++) {
            IMAGES[i] = new Level228Plan.Image();
        }
        for (int i = 0; i < MIRRORS.length; i++) {
            MIRRORS[i] = new Level228Plan.Mirror();
        }
    }

    private Level228Imitator() {}

    /**
     * A client that logs in or respawns here starts with no figures (it cleared them): tell it about
     * the ones in the mirrors now. (Arrivals from another level are caught by {@link #track}.)
     */
    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        resend(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        resend(event.getEntity());
    }

    private static void resend(Player player) {
        if (!(player instanceof ServerPlayer p) || BackroomsLevel.fromDimension(p.level().dimension()) != BackroomsLevel.LEVEL_228) {
            return;
        }
        for (int k = 0; k < SLOTS; k++) {
            if (SLOT[k].phase != NONE) {
                PacketDistributor.sendToPlayer(p, payload(SLOT[k], k));
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_228) {
            return;
        }
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            if (!TRAILS.isEmpty() || anyActive()) {
                reset();
            }
            return;
        }
        long t = level.getGameTime();
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_228);
        Level228Plan.ShiftState shift = Level228Rules.shift(level);
        track(level, players, t);
        for (int i = 0; i < SLOTS; i++) {
            Slot s = SLOT[i];
            if (s.phase != NONE) {
                tickSlot(level, seed, shift, players, s, i, t);
            }
        }
        if (t % 20 == 0) {
            drainWatchers(seed, shift, players);
            if (!off) {
                schedule(level, seed, shift, players, t);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Static state would otherwise leak from one singleplayer world into the next.
        reset();
        off = false;
    }

    private static void reset() {
        for (int i = 0; i < SLOTS; i++) {
            SLOT[i].phase = NONE;
        }
        TRAILS.clear();
        DUE.clear();
    }

    private static boolean anyActive() {
        for (int i = 0; i < SLOTS; i++) {
            if (SLOT[i].phase != NONE) {
                return true;
            }
        }
        return false;
    }

    // ---- players: trails, arrivals, leavers ----

    private static void track(ServerLevel level, List<ServerPlayer> players, long t) {
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            Trail trail = TRAILS.get(p.getUUID());
            if (trail == null) {
                // Arrival (or login, respawn): the trail starts here, the first episode comes later,
                // and they are told about any figure already in the mirrors around them.
                trail = new Trail();
                TRAILS.put(p.getUUID(), trail);
                DUE.putIfAbsent(p.getUUID(), t + 20L * (FIRST_MIN_S + RANDOM.nextInt(FIRST_MAX_S - FIRST_MIN_S + 1)));
                for (int k = 0; k < SLOTS; k++) {
                    if (SLOT[k].phase != NONE) {
                        PacketDistributor.sendToPlayer(p, payload(SLOT[k], k));
                    }
                }
            }
            if (t % TRAIL_EVERY == 0) {
                trail.add(p.getX(), p.getY(), p.getZ());
            }
        }
        if (t % 20 == 0 && TRAILS.size() > players.size()) {
            Iterator<UUID> it = TRAILS.keySet().iterator();
            while (it.hasNext()) {
                UUID id = it.next();
                if (find(players, id) == null) {
                    it.remove();
                    DUE.remove(id);
                }
            }
        }
    }

    @Nullable
    private static ServerPlayer find(List<ServerPlayer> players, @Nullable UUID id) {
        if (id == null) {
            return null;
        }
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getUUID().equals(id)) {
                return players.get(i);
            }
        }
        return null;
    }

    /** In the level and playing: alive, not a spectator, not mid-transition. */
    private static boolean present(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && !Travel.isMoving(p);
    }

    /** Where it may be at all: anywhere in the maze except the exit hallway and the final chamber. */
    static boolean allowedZone(Level228Plan.Zone zone) {
        return zone != Level228Plan.Zone.EXIT_HALL && zone != Level228Plan.Zone.CHAMBER && zone != Level228Plan.Zone.NONE;
    }

    private static boolean allowedAt(long seed, double x, double z) {
        return allowedZone(Level228Plan.zoneAt(seed, Mth.floor(x), Mth.floor(z)));
    }

    // ---- scheduling ----

    private static void schedule(ServerLevel level, long seed, Level228Plan.ShiftState shift, List<ServerPlayer> players, long t) {
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            Long due = DUE.get(p.getUUID());
            if (due == null || t < due || !present(p) || p.isCreative() || busyNear(p)) {
                continue;
            }
            int slot = freeSlot();
            if (slot < 0) {
                return;
            }
            if (start(level, seed, shift, players, p, slot, t, false)) {
                return; // one start per second is plenty
            }
        }
    }

    /** True if an episode is already running for this player or someone in their group. */
    private static boolean busyNear(ServerPlayer p) {
        for (int i = 0; i < SLOTS; i++) {
            Slot s = SLOT[i];
            if (s.phase == NONE) {
                continue;
            }
            if (p.getUUID().equals(s.watcher) || Mth.lengthSquared(p.getX() - s.x, p.getZ() - s.z) < GROUP_RANGE * GROUP_RANGE) {
                return true;
            }
        }
        return false;
    }

    private static int freeSlot() {
        for (int i = 0; i < SLOTS; i++) {
            if (SLOT[i].phase == NONE) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Starts an episode watching {@code watcher} in {@code slot}. Naturally only on a trail point
     * nobody can see now, near a mirror; {@code forced} (the command) also takes a spot it is seen
     * at, or one beside you when you haven't walked far enough for a trail.
     */
    private static boolean start(ServerLevel level, long seed, Level228Plan.ShiftState shift, List<ServerPlayer> players,
            ServerPlayer watcher, int slot, long t, boolean forced) {
        if (!allowedAt(seed, watcher.getX(), watcher.getZ())) {
            return false;
        }
        Trail trail = TRAILS.get(watcher.getUUID());
        if (trail == null) {
            return false;
        }
        long k = followSample(trail, watcher);
        double sx;
        double sy;
        double sz;
        if (k >= 0) {
            int i = (int) (k % TRAIL);
            sx = trail.x[i];
            sy = trail.y[i];
            sz = trail.z[i];
        } else if (forced) {
            k = trail.count - 1;
            double[] spot = besideSpot(seed, shift, watcher);
            if (spot == null) {
                return false;
            }
            sx = spot[0];
            sy = watcher.getY();
            sz = spot[1];
        } else {
            return false;
        }
        if (!Level228Plan.isWalkable(seed, shift, Mth.floor(sx), Mth.floor(sy), Mth.floor(sz)) || !allowedAt(seed, sx, sz)
                || nearAnyPlayer(players, sx, sz)) {
            return false;
        }
        if (!forced) {
            if (Level228Plan.mirrorsNear(seed, shift, sx, sz, MIRROR_NEAR, MIRRORS) == 0
                    || seenByAnyone(seed, shift, players, sx, sy, sz)) {
                return false;
            }
        }
        Slot s = SLOT[slot];
        s.phase = COPY;
        s.kind = RANDOM.nextInt(KINDS);
        s.behind = RANDOM.nextInt(100) < BEHIND_CHANCE_PCT;
        s.watcher = watcher.getUUID();
        s.wearer = pickWearer(players, watcher);
        s.x = sx;
        s.y = sy;
        s.z = sz;
        s.yaw = watcher.getYRot();
        s.follow = k;
        s.startedAt = t;
        s.phaseAt = t;
        s.phaseLen = COPY_MIN + RANDOM.nextInt(COPY_MAX - COPY_MIN + 1);
        s.everSeen = false;
        s.leaving = false;
        // The whole group waits out the cooldown together: one episode per group, rarely.
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            if (p == watcher || p.distanceToSqr(watcher) < GROUP_RANGE * GROUP_RANGE) {
                DUE.put(p.getUUID(), t + 20L * (COOLDOWN_MIN_S + RANDOM.nextInt(COOLDOWN_MAX_S - COOLDOWN_MIN_S + 1)));
            }
        }
        send(level, s, slot);
        return true;
    }

    /** Its face: usually the watcher's own, sometimes a teammate's from the same group. */
    private static UUID pickWearer(List<ServerPlayer> players, ServerPlayer watcher) {
        if (RANDOM.nextInt(100) >= TEAMMATE_FACE_PCT) {
            return watcher.getUUID();
        }
        int count = 0;
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            if (p != watcher && present(p) && p.distanceToSqr(watcher) < GROUP_RANGE * GROUP_RANGE) {
                count++;
            }
        }
        if (count == 0) {
            return watcher.getUUID();
        }
        int pick = RANDOM.nextInt(count);
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            if (p != watcher && present(p) && p.distanceToSqr(watcher) < GROUP_RANGE * GROUP_RANGE && pick-- == 0) {
                return p.getUUID();
            }
        }
        return watcher.getUUID();
    }

    /** The newest trail sample at least {@link #FOLLOW} from the watcher now, or -1 (they haven't walked). */
    private static long followSample(Trail trail, ServerPlayer watcher) {
        double px = watcher.getX();
        double pz = watcher.getZ();
        for (long n = trail.count - 1; trail.has(n); n--) {
            int i = (int) (n % TRAIL);
            double dx = trail.x[i] - px;
            double dz = trail.z[i] - pz;
            if (dx * dx + dz * dz >= FOLLOW * FOLLOW) {
                return n;
            }
        }
        return -1;
    }

    /**
     * For {@code show} when you haven't walked: a walkable cell centre 2-3 blocks from you,
     * reachable in a straight step line (so it doesn't stand on the far side of a pane), preferring
     * behind you.
     */
    @Nullable
    private static double[] besideSpot(long seed, Level228Plan.ShiftState shift, ServerPlayer p) {
        int fx = Mth.floor(p.getX());
        int fy = Mth.floor(p.getY());
        int fz = Mth.floor(p.getZ());
        float yaw = p.getYRot();
        // Behind first, then the sides, then ahead: eight headings, two distances each.
        for (int h = 0; h < 8; h++) {
            float a = yaw + 180.0f + (h == 0 ? 0 : ((h + 1) / 2) * 45.0f * ((h & 1) == 0 ? -1 : 1));
            double dx = -Mth.sin(a * Mth.DEG_TO_RAD);
            double dz = Mth.cos(a * Mth.DEG_TO_RAD);
            for (int d = 3; d >= 2; d--) {
                int cx = Mth.floor(p.getX() + dx * d);
                int cz = Mth.floor(p.getZ() + dz * d);
                if (straightWalk(seed, shift, fx, fz, cx, cz) && Level228Plan.isWalkable(seed, shift, cx, fy, cz)) {
                    return new double[] {cx + 0.5, cz + 0.5};
                }
            }
        }
        return null;
    }

    /** True if unit steps from (x0,z0) to (x1,z1), x first then z, are all allowed by the plan. */
    private static boolean straightWalk(long seed, Level228Plan.ShiftState shift, int x0, int z0, int x1, int z1) {
        int x = x0;
        int z = z0;
        while (x != x1) {
            int dx = Integer.signum(x1 - x);
            if (!Level228Plan.canStep(seed, shift, x, z, dx, 0)) {
                return false;
            }
            x += dx;
        }
        while (z != z1) {
            int dz = Integer.signum(z1 - z);
            if (!Level228Plan.canStep(seed, shift, x, z, 0, dz)) {
                return false;
            }
            z += dz;
        }
        return true;
    }

    private static boolean nearAnyPlayer(List<ServerPlayer> players, double x, double z) {
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            if (!p.isSpectator() && Mth.lengthSquared(p.getX() - x, p.getZ() - z) < PERSONAL * PERSONAL) {
                return true;
            }
        }
        return false;
    }

    // ---- what people see ----

    /** True if {@code p} has at least one image of the figure (chest height) in view through the glass. */
    private static boolean sees(long seed, Level228Plan.ShiftState shift, ServerPlayer p, double x, double y, double z) {
        if (Mth.lengthSquared(p.getX() - x, p.getZ() - z) > SEE_RANGE * SEE_RANGE) {
            return false;
        }
        return Level228Plan.imagesOf(seed, shift, p.getX(), p.getEyeY(), p.getZ(), x, y + 1.0, z, SEE_RANGE, IMAGES) > 0;
    }

    private static boolean seenByAnyone(long seed, Level228Plan.ShiftState shift, List<ServerPlayer> players, double x, double y, double z) {
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            if (!p.isSpectator() && sees(seed, shift, p, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /** Once a second: a little sanity off everyone who has it in a mirror. */
    private static void drainWatchers(long seed, Level228Plan.ShiftState shift, List<ServerPlayer> players) {
        for (int k = 0; k < SLOTS; k++) {
            Slot s = SLOT[k];
            if (s.phase == NONE) {
                continue;
            }
            float drain = s.phase == WATCH ? WATCH_DRAIN : SEEN_DRAIN;
            for (int i = 0; i < players.size(); i++) {
                ServerPlayer p = players.get(i);
                if (!p.isCreative() && present(p) && sees(seed, shift, p, s.x, s.y, s.z)) {
                    SanityService.drain(p, drain);
                }
            }
        }
    }

    // ---- an episode ----

    private static void tickSlot(ServerLevel level, long seed, Level228Plan.ShiftState shift, List<ServerPlayer> players,
            Slot s, int slot, long t) {
        ServerPlayer w = find(players, s.watcher);
        if (w == null || !present(w) || !allowedAt(seed, w.getX(), w.getZ())
                || Mth.lengthSquared(w.getX() - s.x, w.getZ() - s.z) > LEASH * LEASH) {
            end(level, s, slot);
            return;
        }
        boolean look = (t + slot) % LOOK_EVERY == 0;
        boolean seen = look && sees(seed, shift, w, s.x, s.y, s.z);
        if (seen && !s.everSeen) {
            s.everSeen = true;
            s.phaseAt = t; // the copy is only worth its time once you've seen it
        }
        if (!s.everSeen) {
            if (t - s.startedAt > UNSEEN_MAX) {
                end(level, s, slot);
                return;
            }
            s.phaseAt = t;
        }
        int before = s.phase;
        boolean wasBehind = s.behind;
        switch (s.phase) {
            case COPY -> {
                if (!walkTrail(seed, shift, s, w)) {
                    end(level, s, slot);
                    return;
                }
                if (t - s.phaseAt >= s.phaseLen) {
                    s.phase = WRONG;
                    s.phaseAt = t;
                    s.phaseLen = WRONG_MIN + RANDOM.nextInt(WRONG_MAX - WRONG_MIN + 1);
                }
            }
            case WRONG -> {
                // STILL stops dead where it is: from now on it stands where no player is.
                if (s.kind != STILL && !walkTrail(seed, shift, s, w)) {
                    end(level, s, slot);
                    return;
                }
                if (t - s.phaseAt >= s.phaseLen) {
                    beginWatch(level, seed, shift, players, s, w, t);
                }
            }
            case WATCH -> {
                if (!tickWatch(s, w, t, look, seen)) {
                    end(level, s, slot);
                    return;
                }
            }
            default -> {
                end(level, s, slot);
                return;
            }
        }
        if (!allowedAt(seed, s.x, s.z)) {
            end(level, s, slot);
            return;
        }
        if (s.phase != before || s.behind != wasBehind || moved(s)) {
            send(level, s, slot);
        }
    }

    /**
     * Walks it along the watcher's trail toward the newest sample {@link #FOLLOW} behind them, up to
     * {@link #STRIDE} a tick, sample to sample (the trail is where the watcher walked, so it never
     * cuts through a pane). False if it lost the trail (the watcher went too fast or too far).
     */
    private static boolean walkTrail(long seed, Level228Plan.ShiftState shift, Slot s, ServerPlayer w) {
        Trail trail = TRAILS.get(w.getUUID());
        if (trail == null || !trail.has(s.follow)) {
            return false;
        }
        long goal = followSample(trail, w);
        double budget = STRIDE;
        double ox = s.x;
        double oz = s.z;
        while (budget > 0.0 && s.follow < goal) {
            int i = (int) ((s.follow + 1) % TRAIL);
            double dx = trail.x[i] - s.x;
            double dy = trail.y[i] - s.y;
            double dz = trail.z[i] - s.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= budget) {
                s.x = trail.x[i];
                s.y = trail.y[i];
                s.z = trail.z[i];
                s.follow++;
                budget -= d;
            } else {
                double f = budget / d;
                s.x += dx * f;
                s.y += dy * f;
                s.z += dz * f;
                budget = 0.0;
            }
        }
        double mx = s.x - ox;
        double mz = s.z - oz;
        if (mx * mx + mz * mz > 1.0e-4) {
            s.yaw = (float) (Mth.atan2(mz, mx) * Mth.RAD_TO_DEG) - 90.0f;
        }
        // A shifting passage closed over the spot it stands on (shifts avoid occupied cells, but
        // it is not a player): it is simply gone.
        return Level228Plan.isWalkable(seed, shift, Mth.floor(s.x), Mth.floor(s.y), Mth.floor(s.z));
    }

    private static void beginWatch(ServerLevel level, long seed, Level228Plan.ShiftState shift, List<ServerPlayer> players,
            Slot s, ServerPlayer w, long t) {
        s.phase = WATCH;
        s.phaseAt = t;
        s.phaseLen = WATCH_MIN + RANDOM.nextInt(WATCH_MAX - WATCH_MIN + 1);
        if (s.behind) {
            s.behind = putBehind(seed, shift, players, s, w);
        }
        // The only sound it ever makes: one faint tick of glass from where it stands.
        level.playSound(null, s.x, s.y + 1.2, s.z, ModSounds.LEVEL228_GLASS_TICK.get(), SoundSource.AMBIENT,
                TICK_VOLUME, 1.15f + RANDOM.nextFloat() * 0.2f);
    }

    /**
     * The rare moment: it stands right behind the watcher, facing the way they face. Only while they
     * have no image of it in view (so it is never seen moving there), they stand still, and the spot
     * is open floor, reachable in one step line, inside the maze proper and free of other players.
     */
    private static boolean putBehind(long seed, Level228Plan.ShiftState shift, List<ServerPlayer> players, Slot s, ServerPlayer w) {
        if (sees(seed, shift, w, s.x, s.y, s.z) || w.getDeltaMovement().horizontalDistanceSqr() > 1.0e-3) {
            return false;
        }
        float yaw = w.getYRot();
        double bx = w.getX() + Mth.sin(yaw * Mth.DEG_TO_RAD) * BEHIND_DIST;
        double bz = w.getZ() - Mth.cos(yaw * Mth.DEG_TO_RAD) * BEHIND_DIST;
        if (!Level228Plan.isWalkable(seed, shift, Mth.floor(bx), Mth.floor(w.getY()), Mth.floor(bz))
                || !straightWalk(seed, shift, Mth.floor(w.getX()), Mth.floor(w.getZ()), Mth.floor(bx), Mth.floor(bz))
                || !allowedAt(seed, bx, bz)) {
            return false;
        }
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            if (p != w && !p.isSpectator() && Mth.lengthSquared(p.getX() - bx, p.getZ() - bz) < PERSONAL * PERSONAL) {
                return false;
            }
        }
        s.x = bx;
        s.y = w.getY();
        s.z = bz;
        s.yaw = yaw;
        s.behindX = w.getX();
        s.behindZ = w.getZ();
        s.behindYaw = yaw;
        return true;
    }

    /** False when it is gone. */
    private static boolean tickWatch(Slot s, ServerPlayer w, long t, boolean look, boolean seen) {
        if (s.behind) {
            // Turn round on it, or walk off, and there was never anyone there.
            if (Math.abs(Mth.wrapDegrees(w.getYRot() - s.behindYaw)) > BEHIND_TURN
                    || Mth.lengthSquared(w.getX() - s.behindX, w.getZ() - s.behindZ) > BEHIND_STEP * BEHIND_STEP) {
                return false;
            }
        } else {
            // Turns, slowly, to face you.
            float want = (float) (Mth.atan2(w.getZ() - s.z, w.getX() - s.x) * Mth.RAD_TO_DEG) - 90.0f;
            s.yaw += Mth.clamp(Mth.wrapDegrees(want - s.yaw), -2.5f, 2.5f);
        }
        if (!s.leaving && t - s.phaseAt >= s.phaseLen) {
            s.leaving = true;
            s.leavingAt = t;
        }
        // Gone the moment you look away (never while you look at it), or after waiting long enough.
        return !(s.leaving && (look && !seen || t - s.leavingAt > LEAVE_MAX));
    }

    private static boolean moved(Slot s) {
        return Math.abs(s.x - s.sentX) > 0.005 || Math.abs(s.y - s.sentY) > 0.005 || Math.abs(s.z - s.sentZ) > 0.005
                || Math.abs(Mth.wrapDegrees(s.yaw - s.sentYaw)) > 0.5f;
    }

    private static void end(ServerLevel level, Slot s, int slot) {
        s.phase = NONE;
        s.behind = false;
        send(level, s, slot);
    }

    private static Level228ImitatorPayload payload(Slot s, int slot) {
        UUID none = s.watcher != null ? s.watcher : Util.NIL_UUID;
        return new Level228ImitatorPayload(slot, s.phase, s.kind, s.behind, none, s.wearer != null ? s.wearer : none,
                (float) s.x, (float) s.y, (float) s.z, s.yaw);
    }

    private static void send(ServerLevel level, Slot s, int slot) {
        s.sentX = (float) s.x;
        s.sentY = (float) s.y;
        s.sentZ = (float) s.z;
        s.sentYaw = s.yaw;
        PacketDistributor.sendToPlayersInDimension(level, payload(s, slot));
    }

    // ---- testing (command/Level228ImitatorCommand) ----

    /** Starts an episode behind {@code player} now (any game mode); false if there is no room for it. */
    public static boolean debugShow(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        off = false;
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_228);
        List<ServerPlayer> players = level.players();
        track(level, players, level.getGameTime());
        for (int i = 0; i < SLOTS; i++) {
            if (player.getUUID().equals(SLOT[i].watcher) && SLOT[i].phase != NONE) {
                end(level, SLOT[i], i);
            }
        }
        int slot = freeSlot();
        if (slot < 0) {
            end(level, SLOT[0], 0);
            slot = 0;
        }
        return start(level, seed, Level228Rules.shift(level), players, player, slot, level.getGameTime(), true);
    }

    /** True if {@code player} stands somewhere it may appear (for the command's message). */
    public static boolean allowedFor(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        return allowedAt(BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_228), player.getX(), player.getZ());
    }

    /** Ends every episode and keeps it away until {@link #debugShow}. */
    public static void debugOff(ServerPlayer player) {
        off = true;
        if (player.level() instanceof ServerLevel level) {
            for (int i = 0; i < SLOTS; i++) {
                if (SLOT[i].phase != NONE) {
                    end(level, SLOT[i], i);
                }
            }
        }
    }
}
