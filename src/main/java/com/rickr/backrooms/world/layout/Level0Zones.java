package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;
import java.util.Arrays;

/**
 * The big "neighbourhoods" of Level 0: which kind of space you are walking through. Kane Pixels'
 * Level 0 is mostly huge pillared halls, broken up by mazes of thick wall blocks, long corridors,
 * the film's odd half-built rooms, and whole wings where every light is dead.
 *
 * <p><b>How the zones are drawn.</b> The world is cut into 80x80 squares and each square gets one
 * random point somewhere inside it. Every column belongs to the nearest point (a "Voronoi"
 * pattern), so the zones are irregular polygons about 64-96 blocks across instead of a grid of
 * squares. Before measuring distances the position is pushed around a little by smooth noise
 * ("domain warp"), which bends the straight polygon edges into wavy borders. Each point picks its
 * kind from {@link #KIND_WEIGHTS}; neighbouring points of the same kind merge into bigger areas.
 *
 * <p><b>Soft borders.</b> {@link #sample} also returns the second-nearest zone and how far the
 * column is from the border between the two, so the layout can mix the two kinds near a border
 * (see {@code Level0Layout.unitKind}): a hall thickens into a maze over a dozen blocks instead of
 * stopping at a line.
 *
 * <p><b>Speed.</b> The answer is a pure function of (seed, x, z), but it costs about twenty hashes.
 * The layout asks it once per wall segment / room / pillar (not per block), and those answers are
 * kept in a small per-thread table, so chunk generation does not slow down and nothing is
 * allocated per column.
 */
public final class Level0Zones {
    /** Kane's open pillar halls: the dominant space. */
    public static final int HALL = 0;
    /** Wall-block fields: the classic thick-walled maze with lined-up doorways. */
    public static final int FIELD = 1;
    /** Long straight corridors (Kane: 3-4 wide; film: 2-3 wide with a centre light strip). */
    public static final int CORRIDOR = 2;
    /** The film's builders' rooms: big halls with a few deliberate half-walls (see Level0Builders). */
    public static final int BUILDERS = 3;
    /** Dark wings: the same maze, every light dead from the doorway in. */
    public static final int DARK = 4;
    public static final int KINDS = 5;

    /** Names for /backrooms anomaly locate (see Level0Finder). */
    public static final String[] NAMES = {"pillar_hall", "wall_field", "corridors", "builders_rooms", "dark_wing"};

    /** Share of the zone points of each kind (they add up to 1). */
    static final double[] KIND_WEIGHTS = {0.40, 0.25, 0.15, 0.10, 0.10};

    /** Size of the squares that each hold one zone point. */
    static final int CELL = 80;
    /** Keeps each point this far inside its square, so zones never get thinner than ~20 blocks. */
    private static final int INSET = 10;
    /** How far (blocks) the domain warp may push a position, and the scale of its noise. */
    private static final double WARP = 14.0;
    private static final int WARP_SCALE = 48;

    // Packing of a sample (a long, so nothing is allocated):
    // bits 0-2 kind, bits 3-5 second kind, bits 8-15 distance to the border (blocks, max 255),
    // bits 32-63 a hash of the winning zone point (per-zone random choices, e.g. corridor axis).
    private static final int EDGE_SHIFT = 8;

    // Per-thread memo of recent samples. Worldgen runs on several threads, so each one gets its own.
    private static final int CACHE_BITS = 12;
    private static final int CACHE_MASK = (1 << CACHE_BITS) - 1;
    private static final long EMPTY = Long.MIN_VALUE;
    private static final ThreadLocal<Memo> MEMO = ThreadLocal.withInitial(Memo::new);

    private static final class Memo {
        long seed;
        final long[] keys = new long[1 << CACHE_BITS];
        final long[] values = new long[1 << CACHE_BITS];

        Memo() {
            Arrays.fill(keys, EMPTY);
        }
    }

    private Level0Zones() {}

    /** The packed zone sample at (x, z) (see the packing notes above). Cached. */
    static long sample(long seed, int x, int z) {
        Memo memo = MEMO.get();
        if (memo.seed != seed) {
            Arrays.fill(memo.keys, EMPTY);
            memo.seed = seed;
        }
        long key = (long) x << 32 | (z & 0xFFFFFFFFL);
        int slot = (int) Hash.mix(key) & CACHE_MASK;
        if (memo.keys[slot] == key) {
            return memo.values[slot];
        }
        long value = compute(seed, x, z);
        memo.keys[slot] = key;
        memo.values[slot] = value;
        return value;
    }

    /** The zone kind at (x, z), without any border mixing. */
    public static int kindAt(long seed, int x, int z) {
        return kind(sample(seed, x, z));
    }

    static int kind(long sample) {
        return (int) (sample & 7);
    }

    static int secondKind(long sample) {
        return (int) (sample >>> 3 & 7);
    }

    /** Distance (blocks) from (x, z) to the border with the second-nearest zone. */
    static int edge(long sample) {
        return (int) (sample >>> EDGE_SHIFT & 0xFF);
    }

    /** A hash of the zone point: the same for every column of one zone. */
    static int zoneHash(long sample) {
        return (int) (sample >>> 32);
    }

    private static long compute(long seed, int x, int z) {
        double px = x + (valueNoise(seed, x, z, 551) * 2.0 - 1.0) * WARP;
        double pz = z + (valueNoise(seed, x, z, 552) * 2.0 - 1.0) * WARP;
        int cx = Math.floorDiv((int) Math.floor(px), CELL);
        int cz = Math.floorDiv((int) Math.floor(pz), CELL);
        double d1 = Double.MAX_VALUE;
        double d2 = Double.MAX_VALUE;
        double f1x = 0, f1z = 0, f2x = 0, f2z = 0;
        long h1 = 0;
        long h2 = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                long h = Hash.of(seed, cx + i, cz + j, 550);
                double fx = (cx + i) * CELL + INSET + Hash.range(h, CELL - 2 * INSET);
                double fz = (cz + j) * CELL + INSET + Hash.range(Hash.mix(h), CELL - 2 * INSET);
                double d = (px - fx) * (px - fx) + (pz - fz) * (pz - fz);
                if (d < d1) {
                    d2 = d1; f2x = f1x; f2z = f1z; h2 = h1;
                    d1 = d; f1x = fx; f1z = fz; h1 = h;
                } else if (d < d2) {
                    d2 = d; f2x = fx; f2z = fz; h2 = h;
                }
            }
        }
        // Distance to the straight border (the perpendicular bisector) between the two points.
        double gap = Math.sqrt((f2x - f1x) * (f2x - f1x) + (f2z - f1z) * (f2z - f1z));
        int edge = (int) Math.min(255, (d2 - d1) / (2.0 * Math.max(1.0, gap)));
        return (h1 & 0xFFFFFFFF00000000L) | (long) edge << EDGE_SHIFT | (long) kindOf(h2) << 3 | kindOf(h1);
    }

    private static int kindOf(long pointHash) {
        double r = Hash.unit(Hash.mix(pointHash ^ 0x5A5AL));
        for (int k = 0; k < KINDS - 1; k++) {
            r -= KIND_WEIGHTS[k];
            if (r < 0) {
                return k;
            }
        }
        return KINDS - 1;
    }

    /** Smooth value noise in [0, 1): random values on a grid, blended with a smoothstep. */
    private static double valueNoise(long seed, int x, int z, int salt) {
        int gx = Math.floorDiv(x, WARP_SCALE);
        int gz = Math.floorDiv(z, WARP_SCALE);
        double tx = smooth((x - gx * WARP_SCALE) / (double) WARP_SCALE);
        double tz = smooth((z - gz * WARP_SCALE) / (double) WARP_SCALE);
        double a = Hash.unit(seed, gx, gz, salt);
        double b = Hash.unit(seed, gx + 1, gz, salt);
        double c = Hash.unit(seed, gx, gz + 1, salt);
        double d = Hash.unit(seed, gx + 1, gz + 1, salt);
        double top = a + (b - a) * tx;
        double bottom = c + (d - c) * tx;
        return top + (bottom - top) * tz;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }
}
