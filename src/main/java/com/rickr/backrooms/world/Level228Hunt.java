package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.Level228HuntPayload;
import com.rickr.backrooms.player.SanityService;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.layout.Level228Plan;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Level 228's hunter, "the Other": a tall, thin, pale figure that lives only in the reflections.
 *
 * <p><b>Where it is.</b> It is never in real space. The server keeps a virtual position in REAL
 * maze coordinates and walks it through the corridors (Level228Plan#canStep) toward one hunted
 * player at a time. Clients draw it only at the images of that position inside the mirrors'
 * image volumes (client/level228/Level228Other): in the glass you see it standing in the
 * reflected corridor behind your reflection; turn round to the real corridor and nothing is there.
 * One Other per level (not per player), so friends see the same figure in the same mirrors.
 *
 * <p><b>How it moves.</b> Only while nobody is looking at one of its images: a client that has it
 * on screen in a mirror says so (network/Level228SeenPayload) and it freezes until the reports
 * stop. It starts {@value #SPAWN_MIN}-{@value #SPAWN_MAX} blocks off, first comes for a player
 * after {@value #FIRST_DELAY_S} s in the level, and is slow at first (it walks faster the longer
 * it has been stalking). Paths come from a breadth-first search over a bounded window of the maze
 * around it and its target, into preallocated arrays, at most once a second.
 *
 * <p><b>The catch.</b> When it reaches you: the world holds its breath ({@value #HOLD_TICKS}
 * ticks of silence, and your reflection is gone from every mirror), the glass cracks and the
 * picture whites out, and you are "taken into the mirror": moved to a real corridor at least
 * {@value #FARTHER} blocks farther from the nearest exit than you were (a safe arrival cell, so a
 * path to an exit always exists, never image space), with a sanity hit. Then it retreats for 2-4
 * minutes. No death.
 *
 * <p>{@code /backrooms other <near|far|catch|off>} drives it for testing (BackroomsCommand).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level228Hunt {
    /** Not anywhere (retreated, waiting, or switched off). */
    public static final int ABSENT = 0;
    /** Walking the maze toward its target (or frozen while someone watches it). */
    public static final int STALK = 1;
    /** It has reached you: the silence before it takes you. */
    public static final int HOLD = 2;
    /** The crack and white-out; you are moved partway through. */
    public static final int TAKE = 3;

    /** A player must have been in the level this long before it first comes for them. */
    private static final int FIRST_DELAY_S = 180;
    /** Straight-line distance from its target where it shows up. */
    private static final int SPAWN_MIN = 64;
    private static final int SPAWN_MAX = 96;
    /** After a catch (or losing its target) it stays away this long (ticks). */
    private static final int RETREAT_MIN = 2 * 60 * 20;
    private static final int RETREAT_MAX = 4 * 60 * 20;
    /** Losing its target without a catch (they left, died, went creative): a shorter wait. */
    private static final int RETREAT_LOST = 30 * 20;
    /** Walking speed in blocks per tick: at first, after {@link #RAMP_TICKS} of stalking (you walk 0.216). */
    private static final double SPEED_START = 0.9 / 20.0;
    private static final double SPEED_FULL = 2.8 / 20.0;
    private static final int RAMP_TICKS = 10 * 60 * 20;
    /** Farther from its target than this it strides faster, so it never falls hopelessly behind. */
    private static final double FAR_STRIDE = 40.0;
    private static final double FAR_STRIDE_FACTOR = 1.5;
    /** Its target got this far away (a long run, a teleport): it re-enters the mirrors near them. */
    private static final double LEASH = 140.0;
    /** Horizontal / vertical reach of a catch. */
    private static final double CATCH_RANGE = 0.9;
    private static final double CATCH_RANGE_Y = 2.0;
    /** The held breath before it takes you, then the take (white-out) and when in it you move. */
    private static final int HOLD_TICKS = 36;
    private static final int TAKE_TICKS = 60;
    private static final int TAKE_MOVE_AT = 12;
    /** A "seen" report keeps it frozen this long (clients repeat it every 4 ticks while it holds). */
    private static final int SEEN_GRACE = 12;
    /** Reports from farther than this from it are ignored (nobody sees an image that far in the fog). */
    private static final double SEEN_RANGE = 72.0;
    /** How often the path is planned again (ticks), and how often clients are told where it is. */
    private static final int PATH_EVERY = 20;
    private static final int SEND_EVERY = 4;
    /** Failed plans in a row before it gives up on where it is and re-enters near its target. */
    private static final int MAX_FAILED_PLANS = 3;
    /** Within this of its target it drains sanity a little each second (your heart rate climbs). */
    private static final double DREAD_RANGE = 12.0;
    private static final float DREAD_DRAIN = 0.15f;
    private static final float CATCH_SANITY = 20.0f;
    /** The catch puts you at least this much farther from the nearest exit than you were. */
    private static final int FARTHER = 80;

    /**
     * Pathfinding window: a square of this many cells (odd) centred between it and its target.
     * Spawn distance plus detours fit; anything outside is treated as wall.
     */
    private static final int WINDOW = 225;
    private static final int HALF = WINDOW / 2;
    private static final int CELLS = WINDOW * WINDOW;
    private static final int MAX_PATH = 2048;
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};

    // BFS scratch, allocated once (first use): a visit stamp per cell (no clearing between
    // searches), the direction we came in by, and the queue.
    private static int[] visited;
    private static byte[] cameFrom;
    private static int[] queue;
    private static int stamp;

    // ---- the Other (one per level; there is one Level 228 dimension) ----
    private static int state = ABSENT;
    private static double x;
    private static double y;
    private static double z;
    private static float yaw;
    @Nullable
    private static UUID target;
    private static long stateAt;
    private static long retreatUntil;
    private static long seenUntil;
    /** Ticks spent stalking (moving or frozen) since the level last emptied: makes it faster. */
    private static long stalkTicks;
    /** Switched off by {@code /backrooms other off} until another debug command. */
    private static boolean off;
    /** Set by {@code /backrooms other near|far}: ignore the first-appearance delay. */
    private static boolean forced;
    private static float dreadAccum;

    // Planned path: the cells in order, the next one to walk to, and when to plan again.
    // (Package-private, with plan(), for Level228HuntTest.)
    static final int[] PATH_X = new int[MAX_PATH];
    static final int[] PATH_Z = new int[MAX_PATH];
    static int pathLen;
    private static int pathIdx;
    private static long nextPlan;
    private static int failedPlans;
    private static int plannedGoalX = Integer.MIN_VALUE;
    private static int plannedGoalZ = Integer.MIN_VALUE;

    /** When each player in the level arrived (game time), for the first-appearance delay. */
    private static final Map<UUID, Long> ENTERED = new HashMap<>();
    /** Retreat lengths are random; no level at hand there, so its own source. */
    private static final RandomSource RANDOM = RandomSource.create();
    private static long lastSent;

    private Level228Hunt() {}

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_228) {
            return;
        }
        List<ServerPlayer> players = level.players();
        long t = level.getGameTime();
        if (players.isEmpty()) {
            if (!ENTERED.isEmpty() || state != ABSENT) {
                reset();
            }
            return;
        }
        boolean someoneNew = trackArrivals(players, t);
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_228);
        int before = state;
        switch (state) {
            case ABSENT -> tickAbsent(level, seed, players, t);
            case STALK -> tickStalk(level, seed, t);
            case HOLD -> tickHold(level, t);
            case TAKE -> tickTake(level, seed, t);
            default -> state = ABSENT;
        }
        if (someoneNew || state != before || state != ABSENT && t - lastSent >= SEND_EVERY) {
            broadcast(level, seed, players, t);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Static state would otherwise leak from one singleplayer world into the next.
        reset();
        off = false;
    }

    private static void reset() {
        state = ABSENT;
        target = null;
        ENTERED.clear();
        stalkTicks = 0;
        retreatUntil = 0;
        seenUntil = 0;
        forced = false;
        pathLen = 0;
    }

    /** Notes arrivals (true if someone new came, so they get told where it is) and forgets leavers. */
    private static boolean trackArrivals(List<ServerPlayer> players, long t) {
        boolean someoneNew = false;
        for (int i = 0; i < players.size(); i++) {
            if (ENTERED.putIfAbsent(players.get(i).getUUID(), t) == null) {
                someoneNew = true;
            }
        }
        if (t % 20 == 0 && ENTERED.size() > players.size()) {
            Iterator<UUID> it = ENTERED.keySet().iterator();
            while (it.hasNext()) {
                if (find(players, it.next()) == null) {
                    it.remove();
                }
            }
        }
        return someoneNew;
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

    /** Players it may hunt: playing (not creative/spectator), alive, not mid-transition. */
    private static boolean huntable(ServerPlayer p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator() && !Travel.isMoving(p);
    }

    // ---- ABSENT: waiting out its retreat, then picking someone ----

    private static void tickAbsent(ServerLevel level, long seed, List<ServerPlayer> players, long t) {
        if (off || t < retreatUntil || t % 20 != 0) {
            return;
        }
        // The one longest in the level (who has been there long enough) is hunted first.
        ServerPlayer pick = null;
        long earliest = Long.MAX_VALUE;
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            Long at = ENTERED.get(p.getUUID());
            if (at == null || !huntable(p) || !forced && t - at < FIRST_DELAY_S * 20L) {
                continue;
            }
            if (at < earliest) {
                earliest = at;
                pick = p;
            }
        }
        if (pick != null) {
            enter(level, seed, pick, SPAWN_MIN, SPAWN_MAX, t);
        }
    }

    /**
     * Puts it in the maze {@code min}-{@code max} blocks (straight line) from {@code who}, on a
     * real corridor cell it can walk to them from. False if no such cell was found (tried again later).
     */
    private static boolean enter(ServerLevel level, long seed, ServerPlayer who, int min, int max, long t) {
        RandomSource random = level.getRandom();
        int gx = who.getBlockX();
        int gz = who.getBlockZ();
        int searches = 0;
        for (int attempt = 0; attempt < 32 && searches < 6; attempt++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = min + random.nextDouble() * (max - min);
            int cx = gx + (int) Math.round(Math.cos(a) * r);
            int cz = gz + (int) Math.round(Math.sin(a) * r);
            // Nearest real corridor cell within a few blocks of the point.
            int fx = Integer.MIN_VALUE;
            int fz = 0;
            search:
            for (int d = 0; d <= 3; d++) {
                for (int ox = -d; ox <= d; ox++) {
                    for (int oz = -d; oz <= d; oz++) {
                        if (Math.max(Math.abs(ox), Math.abs(oz)) == d && walkable(seed, cx + ox, cz + oz)) {
                            fx = cx + ox;
                            fz = cz + oz;
                            break search;
                        }
                    }
                }
            }
            if (fx == Integer.MIN_VALUE) {
                continue;
            }
            searches++;
            int start = plan(seed, fx, fz, gx, gz, 0.5, 0.5);
            if (start < 0) {
                continue; // can't reach them from there (within the window)
            }
            x = fx + 0.5;
            z = fz + 0.5;
            y = Level228Plan.FEET_Y;
            yaw = (float) Math.toDegrees(Math.atan2(-(gx - fx), gz - fz));
            target = who.getUUID();
            setState(STALK, t);
            seenUntil = 0;
            failedPlans = 0;
            pathIdx = start;
            nextPlan = t + PATH_EVERY;
            return true;
        }
        return false;
    }

    /** A real corridor cell (never image space) the Other can stand in. */
    private static boolean walkable(long seed, int cx, int cz) {
        return Level228Plan.isWalkable(seed, cx, cz) && !Level228Plan.isImageSpace(seed, cx, cz);
    }

    // ---- STALK ----

    private static void tickStalk(ServerLevel level, long seed, long t) {
        ServerPlayer prey = find(level.players(), target);
        if (prey == null || !huntable(prey)) {
            retreat(t, RETREAT_LOST, RETREAT_LOST);
            return;
        }
        stalkTicks++;
        double px = prey.getX();
        double pz = prey.getZ();
        double dist = Math.hypot(px - x, pz - z);
        if (dist > LEASH) {
            // You outran it or were moved: it slips back into the mirrors somewhere nearer you.
            if (!enter(level, seed, prey, SPAWN_MIN, SPAWN_MAX, t)) {
                retreat(t, RETREAT_LOST, RETREAT_LOST);
            }
            return;
        }
        if (dist < DREAD_RANGE) {
            // Close: your nerves feel it before you see it (the HUD heart rate follows sanity).
            dreadAccum += DREAD_DRAIN / 20.0f;
            if (dreadAccum >= 0.5f) {
                SanityService.drain(prey, dreadAccum);
                dreadAccum = 0.0f;
            }
        }
        if (dist < CATCH_RANGE && Math.abs(prey.getY() - y) < CATCH_RANGE_Y) {
            beginHold(prey, t);
            return;
        }
        if (t < seenUntil) {
            return; // watched: it does not move
        }
        int gx = prey.getBlockX();
        int gz = prey.getBlockZ();
        if (t >= nextPlan || pathIdx >= pathLen || Math.abs(gx - plannedGoalX) + Math.abs(gz - plannedGoalZ) > 2) {
            nextPlan = t + PATH_EVERY;
            int start = plan(seed, Mth.floor(x), Mth.floor(z), gx, gz, x - Math.floor(x), z - Math.floor(z));
            if (start < 0) {
                if (++failedPlans >= MAX_FAILED_PLANS && !enter(level, seed, prey, SPAWN_MIN, SPAWN_MAX, t)) {
                    retreat(t, RETREAT_LOST, RETREAT_LOST);
                }
                return;
            }
            failedPlans = 0;
            pathIdx = start;
        }
        double ramp = Math.min(1.0, stalkTicks / (double) RAMP_TICKS);
        double step = Mth.lerp(ramp, SPEED_START, SPEED_FULL) * (dist > FAR_STRIDE ? FAR_STRIDE_FACTOR : 1.0);
        while (step > 1.0e-4 && pathIdx < pathLen) {
            double tx = PATH_X[pathIdx] + 0.5;
            double tz = PATH_Z[pathIdx] + 0.5;
            double ddx = tx - x;
            double ddz = tz - z;
            double d = Math.sqrt(ddx * ddx + ddz * ddz);
            if (d > 1.0e-4) {
                yaw = (float) Math.toDegrees(Math.atan2(-ddx, ddz));
            }
            if (d <= step) {
                x = tx;
                z = tz;
                step -= d;
                pathIdx++;
            } else {
                x += ddx / d * step;
                z += ddz / d * step;
                step = 0;
            }
        }
        // The last stretch: the path ends at your cell's middle; you are rarely exactly there.
        if (pathIdx >= pathLen && step > 1.0e-4) {
            double ddx = px - x;
            double ddz = pz - z;
            double d = Math.sqrt(ddx * ddx + ddz * ddz);
            if (d > 1.0e-4) {
                double s = Math.min(step, d);
                x += ddx / d * s;
                z += ddz / d * s;
                yaw = (float) Math.toDegrees(Math.atan2(-ddx, ddz));
            }
        }
    }

    /**
     * Breadth-first search from (sx, sz) to (gx, gz) over the window centred between them, using
     * Level228Plan#canStep. Fills PATH_X/PATH_Z with the cells from the start (inclusive) toward
     * the goal and returns the index to walk to first, or -1 if the goal can't be reached. If the
     * goal cell itself isn't reachable (you are standing on an edge) the reachable cell nearest it,
     * within 2 cells, will do. {@code fx}/{@code fz} are where in the start cell it is (0..1): if it
     * is already on the line toward the second cell it walks straight on instead of re-centring.
     */
    static int plan(long seed, int sx, int sz, int gx, int gz, double fx, double fz) {
        if (visited == null) {
            visited = new int[CELLS];
            cameFrom = new byte[CELLS];
            queue = new int[CELLS];
        }
        if (Math.abs(gx - sx) >= WINDOW - 2 || Math.abs(gz - sz) >= WINDOW - 2) {
            return -1;
        }
        if (++stamp == Integer.MAX_VALUE) {
            java.util.Arrays.fill(visited, 0);
            stamp = 1;
        }
        int ox = (sx + gx) / 2 - HALF;
        int oz = (sz + gz) / 2 - HALF;
        int startIdx = (sx - ox) + (sz - oz) * WINDOW;
        int goalIdx = (gx - ox) + (gz - oz) * WINDOW;
        visited[startIdx] = stamp;
        cameFrom[startIdx] = -1;
        int head = 0;
        int tail = 0;
        queue[tail++] = startIdx;
        int best = startIdx;
        int bestDist = Math.abs(gx - sx) + Math.abs(gz - sz);
        boolean found = false;
        while (head < tail) {
            int idx = queue[head++];
            int cx = idx % WINDOW + ox;
            int cz = idx / WINDOW + oz;
            int md = Math.abs(gx - cx) + Math.abs(gz - cz);
            if (md < bestDist) {
                bestDist = md;
                best = idx;
            }
            if (idx == goalIdx) {
                found = true;
                break;
            }
            for (int k = 0; k < 4; k++) {
                int nx = cx + DX[k];
                int nz = cz + DZ[k];
                int lx = nx - ox;
                int lz = nz - oz;
                if (lx < 0 || lz < 0 || lx >= WINDOW || lz >= WINDOW) {
                    continue;
                }
                int n = lx + lz * WINDOW;
                if (visited[n] == stamp || !Level228Plan.canStep(seed, cx, cz, DX[k], DZ[k])) {
                    continue;
                }
                visited[n] = stamp;
                cameFrom[n] = (byte) k;
                queue[tail++] = n;
            }
        }
        int end = found ? goalIdx : bestDist <= 2 ? best : -1;
        if (end < 0) {
            return -1;
        }
        // Walk back once to count, then again to write the first MAX_PATH cells in order.
        int length = 1;
        for (int i = end; cameFrom[i] >= 0; length++) {
            i = back(i, cameFrom[i]);
        }
        int i = end;
        for (int k = length - 1; k >= 0; k--) {
            if (k < MAX_PATH) {
                PATH_X[k] = i % WINDOW + ox;
                PATH_Z[k] = i / WINDOW + oz;
            }
            if (cameFrom[i] >= 0) {
                i = back(i, cameFrom[i]);
            }
        }
        pathLen = Math.min(length, MAX_PATH);
        plannedGoalX = gx;
        plannedGoalZ = gz;
        if (pathLen > 1) {
            // Already off the start cell's middle along the way to the next cell: carry straight on.
            int ax = PATH_X[1] - PATH_X[0];
            double lateral = ax != 0 ? fz - 0.5 : fx - 0.5;
            if (Math.abs(lateral) < 0.15) {
                return 1;
            }
        }
        return 0;
    }

    /** The cell we came from, given the direction k we stepped in to reach cell idx. */
    private static int back(int idx, int k) {
        return idx - DX[k] - DZ[k] * WINDOW;
    }

    // ---- HOLD and TAKE ----

    private static void beginHold(ServerPlayer prey, long t) {
        // It stands where you stand: in the glass its image is exactly where yours should be.
        x = prey.getX();
        y = prey.getY();
        z = prey.getZ();
        target = prey.getUUID();
        setState(HOLD, t);
    }

    private static void tickHold(ServerLevel level, long t) {
        ServerPlayer prey = find(level.players(), target);
        if (prey == null || !prey.isAlive() || prey.isSpectator()) {
            retreat(t, RETREAT_LOST, RETREAT_LOST);
            return;
        }
        // It holds on: wherever you step, it is in your place.
        x = prey.getX();
        y = prey.getY();
        z = prey.getZ();
        if (t - stateAt >= HOLD_TICKS) {
            setState(TAKE, t);
        }
    }

    private static void tickTake(ServerLevel level, long seed, long t) {
        ServerPlayer prey = find(level.players(), target);
        if (prey == null) {
            retreat(t, RETREAT_LOST, RETREAT_LOST);
            return;
        }
        long in = t - stateAt;
        if (in == TAKE_MOVE_AT) {
            takeIntoMirror(prey, seed, level.getRandom());
        }
        if (in >= TAKE_TICKS) {
            retreat(t, RETREAT_MIN, RETREAT_MAX);
        }
    }

    /**
     * "Taken into the mirror": under the white-out, moved to a real corridor at least
     * {@link #FARTHER} blocks farther from the nearest exit, with a sanity hit.
     */
    private static void takeIntoMirror(ServerPlayer player, long seed, RandomSource random) {
        BlockPos spot = farther(seed, player.getBlockX(), player.getBlockZ(), random);
        if (spot == null) {
            spot = Travel.findSafeSpot(player.serverLevel(), BackroomsLevel.LEVEL_228, player.getBlockX(), player.getBlockZ());
        }
        player.setDeltaMovement(Vec3.ZERO);
        Teleports.to(player, spot.getX() + 0.5, Level228Plan.FEET_Y, spot.getZ() + 0.5,
                random.nextInt(4) * 90.0f, 0.0f);
        SanityService.drain(player, CATCH_SANITY);
    }

    /**
     * A real corridor cell (walkable, never image space; the maze graph is connected, so every such
     * cell has a path to an exit)
     * whose nearest exit is at least {@link #FARTHER} blocks farther than (px, pz)'s. Tries points
     * pushed away from the nearest exit, fanning out at random; if none is that far (you were
     * already deep in), the farthest one found. Null only if no safe cell was found at all.
     */
    @Nullable
    static BlockPos farther(long seed, int px, int pz, RandomSource random) {
        LevelLayout layout = BackroomsLevel.LEVEL_228.layout();
        int[] exit = layout.exitPosition(seed, px, pz);
        double from = Math.hypot(px - exit[0], pz - exit[1]);
        double want = from + FARTHER;
        double away = Math.atan2(pz - exit[1], px - exit[0]);
        BlockPos best = null;
        double bestDistance = -1;
        for (int attempt = 0; attempt < 24; attempt++) {
            double spread = attempt < 12 ? Math.PI * 0.9 : Math.PI * 2.0;
            double a = away + (random.nextDouble() - 0.5) * spread;
            double r = want + 8 + random.nextInt(48);
            int cx = exit[0] + (int) Math.round(Math.cos(a) * r);
            int cz = exit[1] + (int) Math.round(Math.sin(a) * r);
            BlockPos spot = nearestWalkable(seed, cx, cz, 12);
            if (spot == null) {
                continue;
            }
            int[] e = layout.exitPosition(seed, spot.getX(), spot.getZ());
            double d = Math.hypot(spot.getX() - e[0], spot.getZ() - e[1]);
            if (d >= want) {
                return spot;
            }
            if (d > bestDistance) {
                bestDistance = d;
                best = spot;
            }
        }
        return best;
    }

    /** The walkable real cell nearest (x, z) within {@code radius} (square rings), feet height, or null. */
    @Nullable
    private static BlockPos nearestWalkable(long seed, int x, int z, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    if (Math.max(Math.abs(ox), Math.abs(oz)) == r && walkable(seed, x + ox, z + oz)) {
                        return new BlockPos(x + ox, Level228Plan.FEET_Y, z + oz);
                    }
                }
            }
        }
        return null;
    }

    private static void retreat(long t, int min, int max) {
        setState(ABSENT, t);
        target = null;
        pathLen = 0;
        retreatUntil = t + min + (max > min ? RANDOM.nextInt(max - min) : 0);
    }

    private static void setState(int s, long t) {
        state = s;
        stateAt = t;
    }

    private static void broadcast(ServerLevel level, long seed, List<ServerPlayer> players, long t) {
        lastSent = t;
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            boolean hunted = state != ABSENT && p.getUUID().equals(target);
            PacketDistributor.sendToPlayer(p, new Level228HuntPayload(seed, state, (float) x, (float) y, (float) z, yaw, hunted));
        }
    }

    // ---- network ----

    /** A client says it has the Other on screen in a mirror (network/Level228SeenPayload). */
    public static void seen(ServerPlayer player) {
        if (state != STALK || BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_228) {
            return;
        }
        if (Math.hypot(player.getX() - x, player.getZ() - z) > SEEN_RANGE) {
            return;
        }
        seenUntil = player.serverLevel().getGameTime() + SEEN_GRACE;
    }

    // ---- debug (/backrooms other ...) ----

    /** {@code near}/{@code far}: it enters the maze that far from {@code player}, now. */
    public static boolean debugEnter(ServerPlayer player, boolean near) {
        ServerLevel level = player.serverLevel();
        long t = level.getGameTime();
        off = false;
        forced = true;
        retreatUntil = 0;
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_228);
        boolean ok = near ? enter(level, seed, player, 9, 15, t) : enter(level, seed, player, SPAWN_MIN, SPAWN_MAX, t);
        if (ok) {
            lastSent = Long.MIN_VALUE / 2; // tell everyone at once
        }
        return ok;
    }

    /** {@code catch}: it is on {@code player} now (the silence, then the take). */
    public static void debugCatch(ServerPlayer player) {
        off = false;
        beginHold(player, player.serverLevel().getGameTime());
        lastSent = Long.MIN_VALUE / 2;
    }

    /** {@code off}: gone, and stays gone until another debug command. */
    public static void debugOff(ServerPlayer player) {
        off = true;
        forced = false;
        retreat(player.serverLevel().getGameTime(), 0, 0);
    }

    /** For the command's feedback: how far it is from {@code player}, or -1 if absent. */
    public static double distanceTo(ServerPlayer player) {
        return state == ABSENT ? -1 : Math.hypot(player.getX() - x, player.getZ() - z);
    }
}
