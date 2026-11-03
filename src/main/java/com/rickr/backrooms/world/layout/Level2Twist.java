package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;

/**
 * Where Level 2's corridor "twists": the stretches where its dressing (the pipe bundle, the light
 * panels, the painted band) slowly migrates round the cross-section and the floor and ceiling roll
 * a little, so the corridor seems to turn around you as you walk. The server builds the blocks from
 * these numbers ({@link Level2Layout}), the client tunes the camera, the light and the sound to the
 * same numbers (client/anomaly/level2/Level2Spin), so what you see and what you feel agree.
 *
 * <p>Along each corridor ({@link Level2Line} band) the x axis is cut into cells of {@link #CELL}
 * blocks, each holding one twist stretch 30-38 blocks long a couple of blocks into the cell: about
 * 45% of the corridor twists, and no column is more than 24 blocks from a stretch, so one is always
 * in sight of wherever you arrive. Between stretches the corridor is plain (and still cramped).
 *
 * <p>Inside a stretch the bundle's place round the cross-section ({@link #helix}) turns half a turn,
 * easing in and out, so from one plain stretch to the next it swaps walls: over the ceiling on one
 * twist, under the floor grating on the next. Most of the twists over the ceiling are also banked
 * ({@link #bank}): the roll swells to its peak in the middle of the stretch and dies away again at
 * its ends (the twists under the floor stay level: their grating is the floor).
 *
 * <p>Pure functions of (seed, x, z) built from a few hashes and one sine: no allocation, no tables,
 * cheap enough to call every frame.
 */
public final class Level2Twist {
    /** One twist stretch per this many blocks of corridor. */
    static final int CELL = 76;
    /** A stretch is this long at least ... */
    static final int MIN_LENGTH = 30;
    /** ... plus up to this many blocks less one. */
    private static final int EXTRA_LENGTH = 9;
    /** It starts up to this many blocks less one into its cell (so the gap between two is at most 48). */
    private static final int JITTER = 3;
    /** {@link #amount} rises from 0 to 1 over this many blocks at each end of a stretch. */
    static final int RAMP = 8;
    /** This share of the twists over the ceiling is also banked (so about 4 stretches in 10 overall). */
    private static final double BANKED_CHANCE = 0.85;
    /** A banked stretch peaks at a roll between these (degrees). */
    private static final double BANK_MIN = 12.0;
    private static final double BANK_MAX = 25.0;
    /**
     * The built floor rises one half step past this roll (degrees) and two (a full block) past
     * {@link #BANK_FULL}; the roll changes by under 3 degrees a block, so neighbouring columns
     * never differ by more than one half step.
     */
    private static final double BANK_HALF = 7.0;
    private static final double BANK_FULL = 16.0;

    private Level2Twist() {}

    // ==========================================================================================
    // The contract (see the Level 2 revamp spec)
    // ==========================================================================================

    /** 0..1: how strongly column (x, z) sits in a twist stretch (0 = plain corridor, or not in a corridor). */
    public static float amount(long seed, int x, int z) {
        if (!inCorridor(seed, z)) {
            return 0.0f;
        }
        return (float) amountAlong(seed, Level2Line.band(z), x);
    }

    /**
     * Degrees, signed, about -25..+25: the roll the geometry implies at (x, z). Positive = the
     * corridor appears rolled clockwise as you look along +x: its floor rises on the left (north,
     * smaller z) and its ceiling drops on the right (south).
     */
    public static float bank(long seed, int x, int z) {
        if (!inCorridor(seed, z)) {
            return 0.0f;
        }
        return (float) bankAlong(seed, Level2Line.band(z), x);
    }

    /**
     * Radians in [0, 2 PI): where round the corridor's cross-section the pipe bundle sits at x
     * (0 = right wall, PI/2 = ceiling, PI = left wall, 3 PI/2 = under the floor grating, looking
     * along +x). Constant between stretches (0 or PI), turning half a turn through each stretch.
     * 0 outside the corridors.
     */
    public static float helix(long seed, int x, int z) {
        if (!inCorridor(seed, z)) {
            return 0.0f;
        }
        return (float) helixAlong(seed, Level2Line.band(z), x);
    }

    // ==========================================================================================
    // Along one band (the layout calls these directly)
    // ==========================================================================================

    /** True if z lies inside a corridor, between its walls (side rooms and the rock between bands are not). */
    private static boolean inCorridor(long seed, int z) {
        if (Level2Line.outsideReach(z)) {
            return false;
        }
        return Math.abs(z - Level2Line.centre(seed, Level2Line.band(z))) <= Level2Line.WALL;
    }

    private static int phase(long seed, int band) {
        return Hash.range(Hash.of(seed, band, 8, 540), CELL);
    }

    /** The twist cell column x belongs to. */
    static int cell(long seed, int band, int x) {
        return Math.floorDiv(x - phase(seed, band), CELL);
    }

    /** First x of the stretch of cell k. */
    static int start(long seed, int band, int k) {
        return k * CELL + phase(seed, band) + Hash.range(Hash.of(seed, band, k, 541), JITTER);
    }

    /** Length of the stretch of cell k. */
    static int length(long seed, int band, int k) {
        return MIN_LENGTH + Hash.range(Hash.of(seed, band, k, 542), EXTRA_LENGTH);
    }

    /** True if x lies inside a twist stretch. */
    static boolean inStretch(long seed, int band, int x) {
        int k = cell(seed, band, x);
        int s = start(seed, band, k);
        return x >= s && x < s + length(seed, band, k);
    }

    /** {@link #amount} along band {@code band}. */
    static double amountAlong(long seed, int band, int x) {
        int k = cell(seed, band, x);
        int s = start(seed, band, k);
        int len = length(seed, band, k);
        if (x < s || x >= s + len) {
            return 0.0;
        }
        double edge = Math.min(x - s + 1, s + len - x) / (double) RAMP;
        return smooth(Math.min(1.0, edge));
    }

    /** {@link #helix} along band {@code band}. */
    static double helixAlong(long seed, int band, int x) {
        int k = cell(seed, band, x);
        int s = start(seed, band, k);
        int len = length(seed, band, k);
        double p = x < s ? 0.0 : x >= s + len ? 1.0 : smooth((x - s + 0.5) / len);
        double h = Math.PI * (turns(seed, band, k) + p);
        return h >= 2.0 * Math.PI ? h - 2.0 * Math.PI : h;
    }

    /**
     * 0 if the bundle of cell k's stretch turns from the right wall over the ceiling to the left
     * wall, 1 if it turns from the left wall under the floor grating back to the right. Each band
     * starts on its own wall, so neighbouring corridors don't match.
     */
    static int turns(long seed, int band, int k) {
        return Math.floorMod(k + Hash.range(Hash.of(seed, band, 9, 544), 2), 2);
    }

    /** {@link #bank} along band {@code band}. */
    static double bankAlong(long seed, int band, int x) {
        int k = cell(seed, band, x);
        int s = start(seed, band, k);
        int len = length(seed, band, k);
        if (x < s || x >= s + len || turns(seed, band, k) != 0) {
            return 0.0; // only the twists over the ceiling roll: the others have their pipes under the floor grating
        }
        long h = Hash.of(seed, band, k, 543);
        if (Hash.unit(h) >= BANKED_CHANCE) {
            return 0.0;
        }
        double peak = BANK_MIN + (BANK_MAX - BANK_MIN) * Hash.unit(Hash.mix(h));
        double sign = (h & 1) == 0 ? 1.0 : -1.0;
        return sign * peak * Math.sin(Math.PI * (x - s + 0.5) / len);
    }

    /**
     * The roll at x as half steps the layout builds: 0, +-1 (half a block) or +-2 (a full block),
     * signed like {@link #bank}. The layout may build less where a doorway or a detour needs a flat
     * floor (see Level2Layout), never more.
     */
    static int bankSteps(long seed, int band, int x) {
        double b = bankAlong(seed, band, x);
        double a = Math.abs(b);
        int steps = a >= BANK_FULL ? 2 : a >= BANK_HALF ? 1 : 0;
        return b < 0 ? -steps : steps;
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }
}
