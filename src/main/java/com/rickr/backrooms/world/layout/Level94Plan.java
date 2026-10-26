package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.Level94Vehicles;
import com.rickr.backrooms.block.SignKind;
import com.rickr.backrooms.world.Hash;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Everything about one {@value #SIZE} x {@value #SIZE} square of Level 94, worked out once and
 * then read back for each of its columns: ground height, what the ground is made of (grass,
 * road, path...), the grass shade, and which house, tree or tower stands where.
 *
 * <p>Working it out needs the roads, hills and buildings around the square too. All of those are
 * pure functions of the seed, and buildings are judged against everything within a wide
 * {@link #WINDOW} around the square, so two neighbouring squares always agree about a house that
 * straddles their border.
 *
 * <p>Plans are cached (a few dozen at a time, shared by all world-generation threads).
 */
final class Level94Plan {
    static final int SIZE = 128;
    private static final int SHIFT = 7;
    /** Buildings whose ground lies within this distance of the square are considered. */
    private static final int WINDOW = 144;
    /** ...and the ones within this distance may reach into the square (garden paths are long). */
    private static final int REACH = 118;
    /** Hills this far around the square are needed (roadside houses near the window's edge check them). */
    private static final int HILL_MARGIN = WINDOW + 30;
    /** Roads this far around the square are needed (hill spacing looks 48 blocks out from a hill centre). */
    private static final int ROAD_MARGIN = HILL_MARGIN + Level94Terrain.MAX_RADIUS + (int) Level94Roads.CAP + 8;
    /** Pads raise or lower the land within this distance of themselves. */
    private static final int SHOULDER = 30;
    /** Slope of the land around a pad and down to a road: 0.9 blocks up per block across. */
    private static final float SLOPE = 0.9f;
    /** Longest garden path. */
    private static final float MAX_PATH = 110;

    // What the ground of a column is made of.
    static final byte GRASS = 0;
    static final byte ROAD = 1;
    static final byte CURB = 2;
    static final byte PATH = 3;
    static final byte PAVING = 4;
    static final byte SIDEWALK = 5;
    /** Inside a castle's clear zone: the castle code draws it. */
    static final byte CASTLE = 6;

    // Column flags.
    static final byte SAFE = 1;
    static final byte BLOCKED = 2;

    final long seed;
    final int rx;
    final int rz;
    final int x0;
    final int z0;
    /** Ground height in half blocks above the plain: feet stand at y = 41 + top / 2. */
    final short[] top = new short[SIZE * SIZE];
    final byte[] surf = new byte[SIZE * SIZE];
    final byte[] shade = new byte[SIZE * SIZE];
    final byte[] flags = new byte[SIZE * SIZE];
    /** Index into {@link #things} of what stands on the column, or -1. */
    final short[] thing = new short[SIZE * SIZE];
    /** Index into {@link #towns} of the town the column belongs to, or -1. */
    final byte[] townOf = new byte[SIZE * SIZE];
    final Level94Builds.Thing[] things;
    final Level94Roads.Town[] towns;
    /** 16 x 16 cells of 8 blocks: bit set = a house within about 32 blocks (good for arrivals). */
    private final long[] nearHouse = new long[4];

    private Level94Plan(long seed, int rx, int rz) {
        this.seed = seed;
        this.rx = rx;
        this.rz = rz;
        this.x0 = rx << SHIFT;
        this.z0 = rz << SHIFT;
        int x1 = x0 + SIZE - 1;
        int z1 = z0 + SIZE - 1;

        // Castles and towns near enough to matter.
        List<Level94Roads.Town> near = new ArrayList<>();
        List<Level94Roads.Town> touching = new ArrayList<>();
        int crx = Math.floorDiv(x0 + SIZE / 2, Level94Castle.REGION);
        int crz = Math.floorDiv(z0 + SIZE / 2, Level94Castle.REGION);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                Level94Roads.Town t = Level94Roads.town(seed, crx + ox, crz + oz);
                near.add(t);
                if (t.tx + Level94Roads.TOWN_HALF + 60 >= x0 && t.tx - Level94Roads.TOWN_HALF - 60 <= x1
                        && t.tz + Level94Roads.TOWN_HALF + 60 >= z0 && t.tz - Level94Roads.TOWN_HALF - 60 <= z1) {
                    touching.add(t);
                }
            }
        }
        towns = touching.toArray(new Level94Roads.Town[0]);

        List<Level94Roads.Road> roads = new ArrayList<>();
        Level94Roads.gather(seed, x0, z0, x1, z1, ROAD_MARGIN, roads);
        List<Level94Terrain.Hill> hills = hills(seed, x0 - HILL_MARGIN, z0 - HILL_MARGIN, x1 + HILL_MARGIN, z1 + HILL_MARGIN, roads);

        things = chooseThings(roads, hills, x1, z1);
        List<Level94Roads.Road> paths = new ArrayList<>();
        for (Level94Builds.Thing t : things) {
            if (t.path != null) {
                paths.add(new Level94Roads.Road(xs(t.path), zs(t.path), 0, 0, 0, Integer.MIN_VALUE, 0, Integer.MIN_VALUE, 0));
            }
        }

        // ---- Ground ----------------------------------------------------------------------------
        int n = SIZE * SIZE;
        float[] roadDist = new float[n];
        byte[] roadFlags = new byte[n];
        float[] pathDist = new float[n];
        float[] height = new float[n];
        java.util.Arrays.fill(roadDist, Level94Roads.CAP);
        java.util.Arrays.fill(pathDist, Level94Roads.CAP);
        stampRoads(roads, roadDist, roadFlags, Level94Roads.CAP);
        stampRoads(paths, pathDist, null, 3);
        for (Level94Terrain.Hill hill : hills) {
            int r = (int) Math.ceil(hill.radius);
            for (int x = Math.max(x0, hill.cx - r); x <= Math.min(x1, hill.cx + r); x++) {
                for (int z = Math.max(z0, hill.cz - r); z <= Math.min(z1, hill.cz + r); z++) {
                    int i = index(x, z);
                    height[i] = Math.max(height[i], hill.at(x + 0.5f, z + 0.5f));
                }
            }
        }
        java.util.Arrays.fill(townOf, (byte) -1);
        java.util.Arrays.fill(thing, (short) -1);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int i = index(x, z);
                // The land always slopes gently down to the roads, towns and castle zones.
                float edge = roadDist[i] - Level94Roads.HALF;
                float h = Math.min(height[i], Math.max(0, edge - 1) * SLOPE);
                double castle = Double.MAX_VALUE;
                for (Level94Roads.Town t : near) {
                    castle = Math.min(castle, Math.hypot(x + 0.5 - t.castleX, z + 0.5 - t.castleZ));
                }
                h = (float) Math.min(h, Math.max(0, castle - Level94Castle.CLEAR_RADIUS - 2) * SLOPE);
                for (int k = 0; k < towns.length; k++) {
                    double td = towns[k].boxDistance(x + 0.5, z + 0.5);
                    h = (float) Math.min(h, Math.max(0, td - 2) * SLOPE);
                    if (towns[k].inBox(x, z, 0)) {
                        townOf[i] = (byte) k;
                    }
                }
                height[i] = h;
                if (castle <= Level94Castle.CLEAR_RADIUS) {
                    surf[i] = CASTLE;
                }
            }
        }
        // Pads: flat ground for houses and towers, in the same order in every plan.
        for (Level94Builds.Thing t : things) {
            if (t.pad != null) {
                applyPad(t, height, x1, z1);
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int i = index(x, z);
                float edge = roadDist[i] - Level94Roads.HALF;
                float h = height[i];
                byte s;
                if (surf[i] == CASTLE) {
                    s = CASTLE;
                    h = 0;
                } else if (townOf[i] >= 0) {
                    Level94Roads.Town t = towns[townOf[i]];
                    s = switch (Level94Town.surface(x - t.tx, z - t.tz)) {
                        case Level94Town.ROAD -> ROAD;
                        case Level94Town.SIDEWALK -> SIDEWALK;
                        case Level94Town.PAVING -> PAVING;
                        default -> GRASS;
                    };
                    h = 0;
                } else if (edge <= 0) {
                    s = ROAD;
                    h = 0;
                } else if (edge <= 0.9f && (roadFlags[i] & Level94Roads.CURB) != 0) {
                    s = CURB;
                    h = 0;
                } else if (pathDist[i] <= 1.1f) {
                    s = PATH;
                } else {
                    s = GRASS;
                }
                surf[i] = s;
                // Half-block steps on grass and paths (slabs) make the domes smooth to look at and walk.
                int half = (s == GRASS || s == PATH) ? Math.round(h * 2) : 2 * Math.round(h);
                top[i] = (short) half;
                shade[i] = (byte) Level94Terrain.shade(h, edge, x, z);
            }
        }

        // ---- Buildings on the ground ----------------------------------------------------------
        for (int k = 0; k < things.length; k++) {
            int[] b = things[k].box;
            for (int x = Math.max(x0, b[0]); x <= Math.min(x1, b[2]); x++) {
                for (int z = Math.max(z0, b[1]); z <= Math.min(z1, b[3]); z++) {
                    int i = index(x, z);
                    if (thing[i] < 0 && surf[i] != CASTLE && townOf[i] < 0) {
                        thing[i] = (short) k;
                    }
                }
            }
        }
        List<Level94Builds.Thing> extras = new ArrayList<>();
        roadsideFurniture(roads, extras);
        Level94Builds.Thing[] all = things;
        if (!extras.isEmpty()) {
            all = java.util.Arrays.copyOf(things, things.length + extras.size());
            for (int k = 0; k < extras.size(); k++) {
                Level94Builds.Thing t = extras.get(k);
                all[things.length + k] = t;
                thing[index(t.x, t.z)] = (short) (things.length + k);
            }
        }
        this.thingsAll = all;
        markFlags();
    }

    /** Things from {@link #things} plus the poles and signs added along the roads. */
    private final Level94Builds.Thing[] thingsAll;

    int index(int x, int z) {
        return (x - x0) * SIZE + (z - z0);
    }

    @Nullable
    Level94Builds.Thing thingAt(int i) {
        int k = thing[i];
        return k < 0 ? null : thingsAll[k];
    }

    @Nullable
    Level94Roads.Town townAt(int i) {
        int k = townOf[i];
        return k < 0 ? null : towns[k];
    }

    boolean nearHouse(int x, int z) {
        int bit = (((z - z0) >> 3) << 4) | ((x - x0) >> 3);
        return (nearHouse[bit >> 6] & (1L << (bit & 63))) != 0;
    }

    // ==========================================================================================
    // Cache
    // ==========================================================================================

    private static final ConcurrentHashMap<Long, Level94Plan> CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Level94Plan> LAST = new ThreadLocal<>();

    /** The plan of the square containing (x, z). */
    static Level94Plan of(long seed, int x, int z) {
        int rx = x >> SHIFT;
        int rz = z >> SHIFT;
        Level94Plan p = LAST.get();
        if (p != null && p.rx == rx && p.rz == rz && p.seed == seed) {
            return p;
        }
        long key = Hash.of(seed, rx, rz, 9490);
        p = CACHE.get(key);
        if (p == null || p.seed != seed || p.rx != rx || p.rz != rz) {
            if (CACHE.size() > 160) {
                CACHE.clear();
            }
            // Built inside computeIfAbsent so two threads never build the same square twice.
            p = CACHE.computeIfAbsent(key, k -> new Level94Plan(seed, rx, rz));
        }
        LAST.set(p);
        return p;
    }

    // ==========================================================================================
    // Hills
    // ==========================================================================================

    static List<Level94Terrain.Hill> hills(long seed, int bx0, int bz0, int bx1, int bz1, List<Level94Roads.Road> roads) {
        List<Level94Terrain.Hill> out = new ArrayList<>();
        int reach = Level94Terrain.MAX_RADIUS;
        int c = Level94Terrain.HILL_CELL;
        for (int ci = Math.floorDiv(bx0 - reach, c); ci <= Math.floorDiv(bx1 + reach, c); ci++) {
            for (int cj = Math.floorDiv(bz0 - reach, c); cj <= Math.floorDiv(bz1 + reach, c); cj++) {
                Level94Terrain.Hill h = Level94Terrain.hill(seed, ci, cj, roads);
                if (h != null) out.add(h);
            }
        }
        return out;
    }

    /** Height of the bare hills (before any pads or road fades) at (x, z). */
    static float hillHeight(List<Level94Terrain.Hill> hills, float x, float z) {
        float h = 0;
        for (Level94Terrain.Hill hill : hills) {
            h = Math.max(h, hill.at(x, z));
        }
        return h;
    }

    // ==========================================================================================
    // Choosing buildings
    // ==========================================================================================

    /**
     * All candidates around the square, then the winners: a candidate loses if it overlaps one
     * that beats it (a dark house beats a hilltop house beats a roadside house beats a parked car).
     * Losing only depends on candidates, never on other winners, so every plan reaches the same answer.
     */
    private Level94Builds.Thing[] chooseThings(List<Level94Roads.Road> roads, List<Level94Terrain.Hill> hills, int x1, int z1) {
        int wx0 = x0 - WINDOW;
        int wz0 = z0 - WINDOW;
        int wx1 = x1 + WINDOW;
        int wz1 = z1 + WINDOW;
        List<Level94Builds.Thing> cands = new ArrayList<>();
        // Dark houses (exits).
        int er = Level94Layout.EXIT_REGION;
        for (int ex = Math.floorDiv(wx0 - 100, er); ex <= Math.floorDiv(wx1 + 100, er); ex++) {
            for (int ez = Math.floorDiv(wz0 - 100, er); ez <= Math.floorDiv(wz1 + 100, er); ez++) {
                Level94Builds.Thing dark = darkHouse(seed, ex, ez);
                int[] c = dark.claim();
                if (c[2] >= wx0 && c[0] <= wx1 && c[3] >= wz0 && c[1] <= wz1) cands.add(dark);
            }
        }
        // Hilltops.
        float[] q = new float[2];
        for (Level94Terrain.Hill hill : hills) {
            if (hill.cx < wx0 || hill.cx > wx1 || hill.cz < wz0 || hill.cz > wz1 || hill.feature == Level94Terrain.NOTHING) {
                continue;
            }
            // A smaller dome half buried in a bigger one has no real top to build on.
            if (hillHeight(hills, hill.cx + 0.5f, hill.cz + 0.5f) > hill.height + 1) {
                continue;
            }
            Level94Builds.Thing t = hilltop(hill, roads, q);
            if (t != null) cands.add(t);
        }
        // Roadsides.
        float[] p = new float[4];
        for (Level94Roads.Road road : roads) {
            if (road.has(Level94Roads.TOWN_STREET | Level94Roads.CASTLE_ROAD | Level94Roads.TURNING_CIRCLE)
                    || !road.near(wx0, wz0, wx1, wz1, 16)) {
                continue;
            }
            int k = 0;
            for (float s = 14; s < road.length() - 14; s += 26, k++) {
                road.pointAt(s, p);
                if (p[0] < wx0 - 16 || p[0] > wx1 + 16 || p[1] < wz0 - 16 || p[1] > wz1 + 16) continue;
                for (int side = -1; side <= 1; side += 2) {
                    long h = Hash.mix(road.id + k * 0x9E37L + side * 0x51L);
                    double roll = Hash.unit(h);
                    Level94Builds.Thing t = null;
                    if (roll < 0.22) {
                        t = roadsideHouse(road, p, side, h, roads, hills, q);
                    } else if (roll < 0.235) {
                        t = roadsideCar(p, side, h, roads, hills);
                    }
                    if (t != null) cands.add(t);
                }
            }
        }

        List<Level94Builds.Thing> winners = new ArrayList<>();
        int rx0 = x0 - REACH;
        int rz0 = z0 - REACH;
        int rx1 = x1 + REACH;
        int rz1 = z1 + REACH;
        for (Level94Builds.Thing c : cands) {
            int[] cl = c.claim();
            if (cl[2] < rx0 || cl[0] > rx1 || cl[3] < rz0 || cl[1] > rz1) continue;
            boolean lost = false;
            for (Level94Builds.Thing o : cands) {
                if (o != c && o.beats(c) && o.overlaps(c, 3)) {
                    lost = true;
                    break;
                }
            }
            if (!lost) winners.add(c);
        }
        winners.sort((a, b) -> a.priority != b.priority ? Integer.compare(a.priority, b.priority) : Long.compare(a.key, b.key));
        // Garden paths, now that we know which houses stand.
        for (Level94Builds.Thing t : winners) {
            if (t.kind == Level94Builds.HOUSE && t.house != null && !t.house.isFragment() && t.path == null) {
                t.path = gardenPath(t.house, roads, q);
            }
        }
        return winners.toArray(new Level94Builds.Thing[0]);
    }

    /** Facing toward the nearest road, as one of the four directions (north if there's none). */
    private static Direction faceRoad(List<Level94Roads.Road> roads, float x, float z, float[] q) {
        float d = Level94Roads.nearestRoadPoint(roads, x, z, q);
        if (Float.isInfinite(d)) return Direction.NORTH;
        return toward(q[0] - x, q[1] - z);
    }

    /** The one of the four directions closest to (dx, dz). */
    static Direction toward(float dx, float dz) {
        return Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST) : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
    }

    private static Direction randomDirection(long h) {
        return Direction.from2DDataValue(Hash.range(h, 4));
    }

    @Nullable
    private Level94Builds.Thing hilltop(Level94Terrain.Hill hill, List<Level94Roads.Road> roads, float[] q) {
        int topH = (int) Math.floor(hill.height);
        int g = LevelLayout.FLOOR_Y + topH;
        long h = Hash.mix(hill.hash + 77);
        Level94Builds.Thing t;
        switch (hill.feature) {
            case Level94Terrain.HOUSE -> {
                t = new Level94Builds.Thing(Level94Builds.HOUSE, 1, hill.hash, hill.cx, hill.cz);
                Direction front = faceRoad(roads, hill.cx + 0.5f, hill.cz + 0.5f, q);
                t.house = new Level94Houses.House().centred(Level94Houses.BIG, hill.cx, hill.cz, front, 7, 9, g,
                        Hash.unit(h) < 0.6, Hash.unit(Hash.mix(h)) < 0.5);
                t.box = t.house.worldReach(0);
                t.pad = t.house.worldReach(1);
            }
            case Level94Terrain.TOWER -> {
                t = new Level94Builds.Thing(Level94Builds.TOWER, 1, hill.hash, hill.cx, hill.cz);
                t.box = Level94Builds.square(hill.cx, hill.cz, 3);
                t.pad = Level94Builds.square(hill.cx, hill.cz, 4);
            }
            case Level94Terrain.TREE -> {
                t = new Level94Builds.Thing(Level94Builds.TREE, 1, hill.hash, hill.cx, hill.cz);
                t.a = 3 + Hash.range(h, 3);
                t.b = 22 + Hash.range(Hash.mix(h), 11);
                t.box = Level94Builds.square(hill.cx, hill.cz, (int) Math.ceil(t.b / 10.0));
                t.pad = Level94Builds.square(hill.cx, hill.cz, 0);
            }
            case Level94Terrain.FRAGMENT -> {
                t = new Level94Builds.Thing(Level94Builds.HOUSE, 1, hill.hash, hill.cx, hill.cz);
                boolean room = Hash.unit(h) < 0.5;
                t.house = new Level94Houses.House().centred(room ? Level94Houses.FRAGMENT_ROOM : Level94Houses.FRAGMENT_WALL,
                        hill.cx, hill.cz, randomDirection(Hash.mix(h)), 7, room ? 7 : 3, g, false, false);
                t.box = t.house.worldReach(0);
                t.pad = t.house.worldReach(1);
            }
            case Level94Terrain.CAR -> {
                t = new Level94Builds.Thing(Level94Builds.CAR, 1, hill.hash, hill.cx, hill.cz);
                t.a = Level94Town.kind(h).ordinal();
                t.facing = randomDirection(Hash.mix(h));
                t.box = Level94Builds.square(hill.cx, hill.cz, Level94Vehicles.REACH);
                t.pad = Level94Builds.square(hill.cx, hill.cz, Level94Vehicles.REACH);
            }
            default -> {
                return null;
            }
        }
        t.ground = g;
        t.padHeight = topH;
        t.padRaises = true;
        return t;
    }

    @Nullable
    private Level94Builds.Thing roadsideHouse(Level94Roads.Road road, float[] p, int side, long h,
                                              List<Level94Roads.Road> roads, List<Level94Terrain.Hill> hills, float[] q) {
        float nx = -p[3] * side;
        float nz = p[2] * side;
        int cx = (int) Math.floor(p[0] + nx * 10.5f);
        int cz = (int) Math.floor(p[1] + nz * 10.5f);
        if (!buildable(cx, cz, hills, 4)) return null;
        Direction front = toward(-nx, -nz);
        Level94Houses.House house = new Level94Houses.House().centred(Level94Houses.BIG, cx, cz, front, 7, 9,
                LevelLayout.FLOOR_Y, Hash.unit(Hash.mix(h + 1)) < 0.5, Hash.unit(Hash.mix(h + 2)) < 0.5);
        int[] pad = house.worldReach(1);
        int[] walls = house.worldReach(0);
        if (!clearOfRoads(roads, pad, 2.5f) || !clearOfRoads(roads, walls, 3.5f)) return null;
        Level94Builds.Thing t = new Level94Builds.Thing(Level94Builds.HOUSE, 2, h, cx, cz);
        t.house = house;
        t.box = walls;
        t.pad = pad;
        t.ground = LevelLayout.FLOOR_Y;
        t.padHeight = 0;
        t.padRaises = false;
        return t;
    }

    @Nullable
    private Level94Builds.Thing roadsideCar(float[] p, int side, long h, List<Level94Roads.Road> roads, List<Level94Terrain.Hill> hills) {
        float nx = -p[3] * side;
        float nz = p[2] * side;
        int cx = (int) Math.floor(p[0] + nx * 6.5f);
        int cz = (int) Math.floor(p[1] + nz * 6.5f);
        if (!buildable(cx, cz, hills, 3)) return null;
        int[] box = Level94Builds.square(cx, cz, Level94Vehicles.REACH);
        if (!clearOfRoads(roads, Level94Builds.square(cx, cz, 2), 0.5f)) return null;
        Level94Builds.Thing t = new Level94Builds.Thing(Level94Builds.CAR, 3, h, cx, cz);
        t.a = Level94Town.kind(Hash.mix(h + 3)).ordinal();
        Direction along = toward(p[2], p[3]);
        t.facing = Hash.unit(Hash.mix(h + 4)) < 0.5 ? along : along.getOpposite();
        t.box = box;
        t.pad = box;
        t.ground = LevelLayout.FLOOR_Y;
        t.padHeight = 0;
        t.padRaises = false;
        return t;
    }

    /** Away from castles and towns, and on (nearly) flat land. */
    private boolean buildable(int x, int z, List<Level94Terrain.Hill> hills, float maxHill) {
        return Level94Roads.castleDistance(seed, x, z) > Level94Castle.CLEAR_RADIUS + 10
                && Level94Roads.townDistance(seed, x, z) > 6
                && hillHeight(hills, x + 0.5f, z + 0.5f) <= maxHill;
    }

    /** True if no road edge comes within {@code gap} of the rectangle's corners and middle. */
    static boolean clearOfRoads(List<Level94Roads.Road> roads, int[] r, float gap) {
        float mx = (r[0] + r[2]) * 0.5f + 0.5f;
        float mz = (r[1] + r[3]) * 0.5f + 0.5f;
        float[][] pts = {{r[0] + 0.5f, r[1] + 0.5f}, {r[2] + 0.5f, r[1] + 0.5f}, {r[0] + 0.5f, r[3] + 0.5f},
                {r[2] + 0.5f, r[3] + 0.5f}, {mx, mz}, {mx, r[1] + 0.5f}, {mx, r[3] + 0.5f}, {r[0] + 0.5f, mz}, {r[2] + 0.5f, mz}};
        for (float[] pt : pts) {
            if (Level94Roads.roadDistance(roads, pt[0], pt[1]) < gap) return false;
        }
        return true;
    }

    /** A tan dirt path from the front door down to the nearest road, or null if the road is too far. */
    @Nullable
    static float[] gardenPath(Level94Houses.House house, List<Level94Roads.Road> roads, float[] q) {
        int mid = house.mid();
        float dx0 = house.worldX(mid, -1) + 0.5f;
        float dz0 = house.worldZ(mid, -1) + 0.5f;
        float dx1 = house.worldX(mid, -5) + 0.5f;
        float dz1 = house.worldZ(mid, -5) + 0.5f;
        float d = Level94Roads.nearestRoadPoint(roads, dx1, dz1, q);
        if (Float.isInfinite(d) || d > MAX_PATH) {
            return null;
        }
        if (Level94Roads.roadDistance(roads, dx1, dz1) < 1) {
            Level94Roads.nearestRoadPoint(roads, dx0, dz0, q);
            return new float[] {dx0, dz0, q[0], q[1]};
        }
        return new float[] {dx0, dz0, dx1, dz1, q[0], q[1]};
    }

    // ==========================================================================================
    // Dark houses (the exits)
    // ==========================================================================================

    private static final ConcurrentHashMap<Long, Level94Builds.Thing> DARK = new ConcurrentHashMap<>();
    private static final int[][] DARK_TRIES = {{0, 0}, {40, 0}, {-40, 0}, {0, 40}, {0, -40}, {40, 40}, {-40, -40},
            {40, -40}, {-40, 40}, {80, 0}, {-80, 0}, {0, 80}, {0, -80}};

    /**
     * The dark-door house of exit region (ex, ez): at the exit locator's point, or moved a little
     * if that spot is on a road, in a town or near a castle. Cached; it's a pure function of the seed.
     */
    static Level94Builds.Thing darkHouse(long seed, int ex, int ez) {
        long key = Hash.of(seed, ex, ez, 9495);
        Level94Builds.Thing t = DARK.get(key);
        if (t == null) {
            if (DARK.size() > 256) {
                DARK.clear();
            }
            t = buildDarkHouse(seed, ex, ez);
            Level94Builds.Thing prev = DARK.putIfAbsent(key, t);
            if (prev != null) t = prev;
        }
        return t;
    }

    private static Level94Builds.Thing buildDarkHouse(long seed, int ex, int ez) {
        int px = Level94Layout.EXITS.exitX(seed, ex, ez);
        int pz = Level94Layout.EXITS.exitZ(seed, ex, ez);
        List<Level94Roads.Road> roads = new ArrayList<>();
        int margin = 80 + Level94Terrain.MAX_RADIUS + (int) Level94Roads.CAP + 16;
        Level94Roads.gather(seed, px, pz, px, pz, margin, roads);
        List<Level94Terrain.Hill> hills = hills(seed, px - 90, pz - 90, px + 90, pz + 90, roads);
        float[] q = new float[2];
        long h = Hash.of(seed, ex, ez, 9496);
        int bestX = px;
        int bestZ = pz;
        float bestRoom = -Float.MAX_VALUE;
        boolean found = false;
        for (int[] off : DARK_TRIES) {
            int cx = px + off[0];
            int cz = pz + off[1];
            if (Level94Roads.castleDistance(seed, cx, cz) < Level94Castle.CLEAR_RADIUS + 40
                    || Level94Roads.townDistance(seed, cx, cz) < 30) {
                continue;
            }
            float room = Level94Roads.roadDistance(roads, cx + 0.5f, cz + 0.5f);
            Direction front = faceRoad(roads, cx + 0.5f, cz + 0.5f, q);
            Level94Houses.House trial = new Level94Houses.House().centred(Level94Houses.DARK, cx, cz, front, 7, 9, 0, false, false);
            if (room >= 12 && room < Level94Roads.CAP && clearOfRoads(roads, trial.worldReach(1), 3)) {
                bestX = cx;
                bestZ = cz;
                found = true;
                break;
            }
            if (room > bestRoom) {
                bestRoom = room;
                bestX = cx;
                bestZ = cz;
            }
        }
        int topH = (int) Math.floor(hillHeight(hills, bestX + 0.5f, bestZ + 0.5f));
        Direction front = faceRoad(roads, bestX + 0.5f, bestZ + 0.5f, q);
        Level94Builds.Thing t = new Level94Builds.Thing(Level94Builds.HOUSE, 0, h, bestX, bestZ);
        t.house = new Level94Houses.House().centred(Level94Houses.DARK, bestX, bestZ, front, 7, 9,
                LevelLayout.FLOOR_Y + topH, false, Hash.unit(Hash.mix(h)) < 0.5);
        t.box = t.house.worldReach(0);
        t.pad = t.house.worldReach(1);
        t.ground = LevelLayout.FLOOR_Y + topH;
        t.padHeight = topH;
        t.padRaises = true;
        t.path = gardenPath(t.house, roads, q);
        if (!found) {
            // No good spot: it still stands (every region has its exit), just less tidily placed.
            t.path = null;
        }
        return t;
    }

    // ==========================================================================================
    // Ground helpers
    // ==========================================================================================

    private static float[] xs(float[] pts) {
        float[] out = new float[pts.length / 2];
        for (int i = 0; i < out.length; i++) out[i] = pts[2 * i];
        return out;
    }

    private static float[] zs(float[] pts) {
        float[] out = new float[pts.length / 2];
        for (int i = 0; i < out.length; i++) out[i] = pts[2 * i + 1];
        return out;
    }

    /** Distance from each column centre to the nearest road centre line, minus the road's extra width. */
    private void stampRoads(List<Level94Roads.Road> roads, float[] dist, @Nullable byte[] nearestFlags, float cap) {
        int x1 = x0 + SIZE - 1;
        int z1 = z0 + SIZE - 1;
        for (Level94Roads.Road road : roads) {
            int segs = Math.max(1, road.px.length - 1);
            for (int s = 0; s < segs; s++) {
                float ax = road.px[s];
                float az = road.pz[s];
                float bx = road.px.length > 1 ? road.px[s + 1] : ax;
                float bz = road.pz.length > 1 ? road.pz[s + 1] : az;
                float reach = cap + road.extra + Level94Roads.HALF;
                int sx0 = Math.max(x0, (int) Math.floor(Math.min(ax, bx) - reach));
                int sx1 = Math.min(x1, (int) Math.ceil(Math.max(ax, bx) + reach));
                int sz0 = Math.max(z0, (int) Math.floor(Math.min(az, bz) - reach));
                int sz1 = Math.min(z1, (int) Math.ceil(Math.max(az, bz) + reach));
                for (int x = sx0; x <= sx1; x++) {
                    for (int z = sz0; z <= sz1; z++) {
                        float d = Level94Roads.segDist(x + 0.5f, z + 0.5f, ax, az, bx, bz) - road.extra;
                        int i = index(x, z);
                        if (d < dist[i]) {
                            dist[i] = d;
                            if (nearestFlags != null) nearestFlags[i] = (byte) road.flags;
                        }
                    }
                }
            }
        }
    }

    /** Flattens a thing's pad and blends the land around it with a gentle slope. */
    private void applyPad(Level94Builds.Thing t, float[] height, int x1, int z1) {
        int[] p = t.pad;
        float ph = t.padHeight;
        for (int x = Math.max(x0, p[0] - SHOULDER); x <= Math.min(x1, p[2] + SHOULDER); x++) {
            for (int z = Math.max(z0, p[1] - SHOULDER); z <= Math.min(z1, p[3] + SHOULDER); z++) {
                int dx = Math.max(0, Math.max(p[0] - x, x - p[2]));
                int dz = Math.max(0, Math.max(p[1] - z, z - p[3]));
                int i = index(x, z);
                if (dx == 0 && dz == 0) {
                    height[i] = ph;
                    continue;
                }
                float d = (float) Math.sqrt(dx * dx + dz * dz);
                float h = Math.min(height[i], ph + d * SLOPE);
                if (t.padRaises) {
                    h = Math.max(h, ph - d * SLOPE);
                }
                height[i] = h;
            }
        }
    }

    // ==========================================================================================
    // Siren poles and road signs
    // ==========================================================================================

    /** Columns where a pole or sign can stand: open grass or curb, nothing else there. */
    private boolean freeVerge(int x, int z) {
        if (x < x0 || x >= x0 + SIZE || z < z0 || z >= z0 + SIZE) return false;
        int i = index(x, z);
        return thing[i] < 0 && townOf[i] < 0 && (surf[i] == GRASS || surf[i] == CURB);
    }

    private void roadsideFurniture(List<Level94Roads.Road> roads, List<Level94Builds.Thing> out) {
        float[] p = new float[4];
        int x1 = x0 + SIZE - 1;
        int z1 = z0 + SIZE - 1;
        for (Level94Roads.Road road : roads) {
            if (road.has(Level94Roads.TOWN_STREET | Level94Roads.TURNING_CIRCLE) || !road.near(x0, z0, x1, z1, 10)) {
                continue;
            }
            // A siren pole about every 120 blocks, on alternating sides, horn toward the road.
            float len = road.length();
            float s0 = 40 + Hash.range(Hash.mix(road.id + 5), 80);
            int k = 0;
            for (float s = s0; s < len - 10; s += 120, k++) {
                road.pointAt(s, p);
                int side = Hash.unit(Hash.mix(road.id + 11 + k)) < 0.5 ? -1 : 1;
                float nx = -p[3] * side;
                float nz = p[2] * side;
                addPole(out, Level94Builds.SIREN, (int) Math.floor(p[0] + nx * 4.6f), (int) Math.floor(p[1] + nz * 4.6f),
                        toward(-nx, -nz), 0, Hash.mix(road.id + 13 + k));
            }
            // STOP at busy junctions, NO OUTLET at the mouth of a dead end.
            int degA = road.ai != Integer.MIN_VALUE ? Level94Roads.degree(seed, road.ai, road.aj) : 0;
            int degB = road.bi != Integer.MIN_VALUE ? Level94Roads.degree(seed, road.bi, road.bj) : 0;
            // STOP signs at about half the approaches to a crossroads: "here and there", not everywhere.
            boolean stopA = Hash.unit(Hash.mix(road.id + 17)) < 0.5;
            boolean stopB = Hash.unit(Hash.mix(road.id + 19)) < 0.5;
            if (degA >= 3 && stopA) junctionSign(road, 7, false, SignKind.STOP, p, out);
            if (degB >= 3 && stopB) junctionSign(road, len - 7, true, SignKind.STOP, p, out);
            if (degA >= 2 && degB == 1) junctionSign(road, 9, true, SignKind.NO_OUTLET, p, out);
            if (degB >= 2 && degA == 1) junctionSign(road, len - 9, false, SignKind.NO_OUTLET, p, out);
        }
    }

    /**
     * A sign on the right-hand verge at arc length s, read by drivers travelling forward along the
     * road ({@code forward}) or backward.
     */
    private void junctionSign(Level94Roads.Road road, float s, boolean forward, SignKind kind, float[] p, List<Level94Builds.Thing> out) {
        road.pointAt(s, p);
        float tx = forward ? p[2] : -p[2];
        float tz = forward ? p[3] : -p[3];
        // Right of the direction of travel (x east, z south): (-tz, tx).
        float rx = -tz;
        float rz = tx;
        addPole(out, Level94Builds.SIGN, (int) Math.floor(p[0] + rx * 4.6f), (int) Math.floor(p[1] + rz * 4.6f),
                toward(-tx, -tz), kind.ordinal(), Hash.mix(road.id + kind.ordinal() * 7L + (forward ? 1 : 0)));
    }

    private void addPole(List<Level94Builds.Thing> out, int kind, int x, int z, Direction facing, int a, long key) {
        if (!freeVerge(x, z)) return;
        for (Level94Builds.Thing t : out) {
            if (Math.abs(t.x - x) <= 1 && Math.abs(t.z - z) <= 1) return;
        }
        Level94Builds.Thing t = new Level94Builds.Thing(kind, 9, key, x, z);
        t.ground = LevelLayout.FLOOR_Y + (top[index(x, z)] >> 1);
        t.facing = facing;
        t.a = a;
        t.box = Level94Builds.square(x, z, 0);
        out.add(t);
    }

    // ==========================================================================================
    // Flags: safe rooms, blocked columns, near houses
    // ==========================================================================================

    /** Records the blocks at feet and head height while a column is drawn. */
    private static final class Probe implements LevelLayout.Column {
        int feetY;
        @Nullable
        BlockState feet;
        @Nullable
        BlockState head;

        @Override
        public void set(int y, BlockState state) {
            if (y == feetY) feet = state;
            else if (y == feetY + 1) head = state;
        }

        boolean blocked() {
            return solid(feet) || solid(head);
        }

        private static boolean solid(@Nullable BlockState s) {
            return s != null && (s.blocksMotion() || !s.getFluidState().isEmpty());
        }
    }

    private void markFlags() {
        Probe probe = new Probe();
        for (Level94Builds.Thing t : thingsAll) {
            if (t.kind == Level94Builds.HOUSE && t.house != null && !t.house.isFragment()) {
                markNear(t.box, 32);
            }
        }
        for (Level94Roads.Town t : towns) {
            markNear(new int[] {t.tx - Level94Roads.TOWN_HALF, t.tz - Level94Roads.TOWN_HALF,
                    t.tx + Level94Roads.TOWN_HALF, t.tz + Level94Roads.TOWN_HALF}, 16);
        }
        for (int x = x0; x < x0 + SIZE; x++) {
            for (int z = z0; z < z0 + SIZE; z++) {
                int i = index(x, z);
                Level94Builds.Thing t = thingAt(i);
                Level94Roads.Town town = townAt(i);
                if (t == null && town == null) continue;
                probe.feetY = LevelLayout.FLOOR_Y + 1 + (top[i] >> 1);
                probe.feet = null;
                probe.head = null;
                byte f = 0;
                if (town != null) {
                    Level94Town.column(seed, town, x, z, probe);
                    if (Level94Town.isSafe(seed, town, x, z)) f |= SAFE;
                } else {
                    Level94Builds.column(t, x, z, probe);
                    if (t.house != null && t.house.isSafe(x, z)) f |= SAFE;
                }
                if (probe.blocked()) f |= BLOCKED;
                flags[i] = f;
            }
        }
    }

    private void markNear(int[] box, int r) {
        for (int cx = 0; cx < 16; cx++) {
            for (int cz = 0; cz < 16; cz++) {
                int ax = x0 + cx * 8;
                int az = z0 + cz * 8;
                if (ax + 7 >= box[0] - r && ax <= box[2] + r && az + 7 >= box[1] - r && az <= box[3] + r) {
                    int bit = (cz << 4) | cx;
                    nearHouse[bit >> 6] |= 1L << (bit & 63);
                }
            }
        }
    }
}
