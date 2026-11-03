package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;

/**
 * How Level 2's corridor seems to turn around you: everywhere along it the corridor's dressing
 * (the pipe bundle, the light panels, the painted band, the fans and beacons) corkscrews round the
 * one-block lane without pause. The server builds the blocks from these numbers ({@link
 * Level2Layout}, {@link Level2Section}); the client tunes the camera, the light and the sound to
 * the same numbers (client/anomaly/level2/Level2Spin), so what you see and what you feel agree.
 *
 * <p><b>Legs.</b> Each corridor ({@link Level2Line} band) is cut into legs of 240-400 blocks. Within
 * a leg the dressing turns a whole number of times (about one turn per 40-60 blocks on average)
 * one way; the next leg turns it the other way. The turn's speed is not even: it eases to a stop
 * where the direction reverses and runs up to about twice the average in between, with a second,
 * faster swell on top, so the rate keeps changing as you walk. Because every leg turns a whole
 * number of times, the angle is continuous from leg to leg.
 *
 * <p><b>Exits.</b> Within {@link #EXIT_CALM} blocks of an exit the corridor is calm ({@link #amount}
 * eases to 0 over {@link #EXIT_RAMP} more), so the recess and its approach read plainly.
 *
 * <p>Pure functions of (seed, x, z) built from a few hashes and sines: no allocation, no tables,
 * cheap enough to call every frame.
 */
public final class Level2Twist {
    /** Legs are this long on average ... */
    static final int LEG = 320;
    /** ... with each boundary moved up to this far either way (legs 240-400 long). */
    private static final int LEG_JITTER = 40;
    /** A leg turns once per this many blocks at least ... */
    private static final int TURN_MIN = 40;
    /** ... plus up to this many more less one (about one turn per 40-60 blocks). */
    private static final int TURN_EXTRA = 21;
    /**
     * The speed profile within a leg: rate(t) = 1 - SLOW cos(2 pi t) - SWELL cos(6 pi t), which is
     * 0 at both ends (the reversal) and never negative because SLOW + SWELL = 1.
     */
    private static final double SLOW = 0.7;
    private static final double SWELL = 0.3;
    /** The corridor is calm this far either side of an exit door ... */
    static final int EXIT_CALM = 8;
    /** ... and the twist comes back over this many blocks beyond that. */
    private static final int EXIT_RAMP = 6;
    /** The roll the turning implies at the fastest point of a leg (degrees). */
    private static final double BANK_MAX = 22.0;

    private Level2Twist() {}

    // ==========================================================================================
    // The contract (see the Level 2 revamp spec)
    // ==========================================================================================

    /** 0..1: how strongly column (x, z) twists: 1 along the whole corridor, 0 at an exit and off the corridor. */
    public static float amount(long seed, int x, int z) {
        if (!inCorridor(seed, z)) {
            return 0.0f;
        }
        return (float) amountAlong(seed, Level2Line.band(z), x);
    }

    /**
     * Degrees, signed, about -22..+22: the roll the turning implies at (x, z), strongest where the
     * dressing turns fastest. Positive = the corridor appears rolled clockwise as you look along +x.
     * The floor itself stays flat.
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
     * along +x). Turns continuously along the corridor; 0 off the corridors.
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

    /** True if z lies inside a corridor: the lane, its walls, the front of an exit recess. */
    private static boolean inCorridor(long seed, int z) {
        if (Level2Line.outsideReach(z)) {
            return false;
        }
        return Math.abs(z - Level2Line.centre(seed, Level2Line.band(z))) <= Level2Line.WALL + 1;
    }

    private static int phase(long seed, int band) {
        return Hash.range(Hash.of(seed, band, 8, 540), LEG);
    }

    /** First x of leg k. */
    private static int legStart(long seed, int band, int k) {
        return k * LEG + phase(seed, band) + Hash.range(Hash.of(seed, band, k, 541), 2 * LEG_JITTER + 1) - LEG_JITTER;
    }

    /** The leg x lies in (its start moved by the jitter, so look one either side). */
    private static int leg(long seed, int band, int x) {
        int k = Math.floorDiv(x - phase(seed, band), LEG);
        if (x < legStart(seed, band, k)) {
            return k - 1;
        }
        return x >= legStart(seed, band, k + 1) ? k + 1 : k;
    }

    /** Whole turns of leg k, signed: + anticlockwise looking along +x (helix growing), - the other way. */
    private static int turns(long seed, int band, int k, int length) {
        int perTurn = TURN_MIN + Hash.range(Hash.of(seed, band, k, 542), TURN_EXTRA);
        int n = Math.max(1, Math.round(length / (float) perTurn));
        // Legs take turns: the direction reverses at every leg boundary (each band starts its own way).
        boolean anticlockwise = ((k + Hash.range(Hash.of(seed, band, 9, 544), 2)) & 1) == 0;
        return anticlockwise ? n : -n;
    }

    /** {@link #amount} along band {@code band}. */
    static double amountAlong(long seed, int band, int x) {
        int d = Math.abs(x - Level2Line.exitX(seed, band, Level2Line.nearestExit(seed, band, x)));
        if (d <= EXIT_CALM) {
            return 0.0;
        }
        return smooth(Math.min(1.0, (d - EXIT_CALM) / (double) EXIT_RAMP));
    }

    /** {@link #helix} along band {@code band}. */
    static double helixAlong(long seed, int band, int x) {
        int k = leg(seed, band, x);
        int s = legStart(seed, band, k);
        int length = legStart(seed, band, k + 1) - s;
        double t = (x - s + 0.5) / length;
        double u = 2.0 * Math.PI * t;
        // F(t): 0 at the leg's start, 1 at its end, rising at rate(t) (see SLOW, SWELL).
        double f = t - SLOW * Math.sin(u) / (2.0 * Math.PI) - SWELL * Math.sin(3.0 * u) / (6.0 * Math.PI);
        double h = 2.0 * Math.PI * turns(seed, band, k, length) * f;
        h %= 2.0 * Math.PI;
        return h < 0.0 ? h + 2.0 * Math.PI : h;
    }

    /** {@link #bank} along band {@code band}: the turning speed, signed by its direction. */
    static double bankAlong(long seed, int band, int x) {
        int k = leg(seed, band, x);
        int s = legStart(seed, band, k);
        int length = legStart(seed, band, k + 1) - s;
        double u = 2.0 * Math.PI * (x - s + 0.5) / length;
        double rate = 1.0 - SLOW * Math.cos(u) - SWELL * Math.cos(3.0 * u); // 0..2
        double sign = turns(seed, band, k, length) > 0 ? -1.0 : 1.0; // dressing anticlockwise reads as a clockwise roll
        return sign * BANK_MAX * 0.5 * rate * amountAlong(seed, band, x);
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }
}
