package com.rickr.backrooms.world.layout;

/**
 * The cramped cross-section of Level 2's corridor, and how its dressing turns round it in the
 * twist stretches ({@link Level2Twist}). Only numbers here: {@link Level2Layout} turns them into
 * blocks, and its column codes (where you can walk) come from the same answers.
 *
 * <p><b>The ring.</b> Seen down the corridor (looking along +x), the lane is three blocks wide
 * (dz -1..1) and three high (y41-43), under a lowered ceiling at y44. Round it runs a ring of 16
 * cells, each 22.5 degrees of the turn, numbered anticlockwise from the middle of the right wall
 * (south, +z) the way {@link Level2Twist#helix} counts its angle:
 * <pre>
 *            6   5   4   3   2          y44  ceiling (lowered)
 *            7               1          y43
 *            8               0          y42
 *            9               15         y41
 *            10  11  12  13  14         y40  floor
 *   dz      -2  -1   0  +1  +2
 * </pre>
 * The pipe bundle fills the cells within {@link #BUNDLE_HALF} of the helix angle: a dense rack up
 * one inner wall from floor to ceiling, or a mat of hung pipes and cable tray overhead, or (half
 * way through every other twist) a bed of pipes under a floor grating. While the bundle sits on a
 * wall its front pipes also stick out into the lane column beside that wall (the photo's rack), so
 * the clear lane is two blocks wide. The light panel rides a quarter turn ahead of the bundle (on
 * the ceiling between twists, then down the far wall), and the painted band sits straight across
 * from it, so as you walk through a stretch the bundle, the lights and the band all turn together.
 *
 * <p><b>The roll.</b> In a banked stretch the floor rises in half steps on one side of the lane and
 * the ceiling drops on the other ({@link #bankSteps}); never where a doorway, a detour or anything
 * else needs a flat floor, and never more than half a block between neighbouring columns.
 *
 * <p>Pure functions of (seed, band, x) built on {@link Level2Twist} and {@link Level2Line}; nothing
 * allocates.
 */
final class Level2Section {
    /** Cells round the ring (22.5 degrees each). */
    static final int SLOTS = 16;
    /** The bundle fills the cells this close to its angle (3 cells: 67.5 degrees either way). */
    private static final double BUNDLE_HALF = 3.0;
    /** A wall carries front pipes into the lane while the bundle's angle is this close to the wall's middle ... */
    private static final double FRONT_SIDE_REACH = 3.0;
    /** ... on each row whose own cell is this close to the bundle's angle. */
    private static final double FRONT_REACH = 2.5;
    /** Overhead, the bundle sags into the lane's top row (y43) under the cells this close to its angle. */
    private static final double SAG_REACH = 1.0;
    /** The light panel follows the bundle from the ceiling down the far wall at most to this cell (low on the wall). */
    private static final int LAMP_LAST = 9;

    /** Ring cell of (dz, row) for dz -2..2 (index dz + 2) and row 0..4 above the floor (y40..y44); -1 inside the lane. */
    private static final int[][] SLOT_AT = {
            {10, 9, 8, 7, 6},
            {11, -1, -1, -1, 5},
            {12, -1, -1, -1, 4},
            {13, -1, -1, -1, 3},
            {14, 15, 0, 1, 2},
    };

    private Level2Section() {}

    // ==========================================================================================
    // The ring
    // ==========================================================================================

    /** The ring cell at (dz, row), or -1 (inside the lane, or off the ring). */
    static int slot(int dz, int row) {
        if (dz < -2 || dz > 2 || row < 0 || row > 4) {
            return -1;
        }
        return SLOT_AT[dz + 2][row];
    }

    /** The bundle's angle at x in ring cells, 0..16 (helix / 22.5 degrees). */
    static double ring(long seed, int band, int x) {
        return Level2Twist.helixAlong(seed, band, x) * (SLOTS / (2.0 * Math.PI));
    }

    /** How many cells round the ring cell i is from angle s (0..8). */
    static double distance(double s, int i) {
        double d = Math.abs(i - s) % SLOTS;
        return Math.min(d, SLOTS - d);
    }

    /** True if ring cell i holds part of the pipe bundle at angle s. */
    static boolean bundle(double s, int i) {
        return i >= 0 && distance(s, i) <= BUNDLE_HALF;
    }

    /** The wall (-1 north, +1 south) whose rack sticks out into the lane at angle s, or 0 (bundle overhead or underfoot). */
    static int frontSide(double s) {
        if (distance(s, 0) <= FRONT_SIDE_REACH) {
            return 1;
        }
        return distance(s, 8) <= FRONT_SIDE_REACH ? -1 : 0;
    }

    /** The wall (-1 north, +1 south) the bundle is NOT on at angle s: the bare concrete (north while it is overhead or underfoot). */
    static int bareSide(double s) {
        int front = frontSide(s);
        return front != 0 ? -front : -1;
    }

    /** True if, at angle s, the rack on {@code side} has front pipes on {@code row} (1..3), before any doorway clears them. */
    static boolean frontRow(double s, int side, int row) {
        return row >= 1 && row <= 3 && frontSide(s) == side && distance(s, slot(2 * side, row)) <= FRONT_REACH;
    }

    /** True if, at angle s, the bundle overhead sags into the lane's top row over lane column dz (-1..1). */
    static boolean sags(double s, int dz) {
        return distance(s, slot(dz, 4)) <= SAG_REACH;
    }

    /**
     * The ring cell of the light panel at angle s, or -1 (it would sit in the bundle). Between the
     * twists it is the middle of the ceiling (cell 4); through a twist it turns with the bundle, a
     * quarter turn away from it, down to low on the far wall, then starts again on the ceiling.
     */
    static int lampSlot(double s) {
        double rest = s >= SLOTS / 2.0 ? SLOTS / 2.0 : 0.0;
        double p = Math.min(4.0 + (s - rest), LAMP_LAST);
        int i = (int) Math.round(p);
        if (i == 6) {
            i = p < 6.0 ? 5 : 7; // never in the corner over the inner wall (nobody would see it)
        }
        return bundle(s, i) ? -1 : i;
    }

    /** The ring cell of the painted band at angle s: straight across from the bundle, never in a corner. */
    static int stripeSlot(double s) {
        double t = s + SLOTS / 2.0;
        int i = (int) Math.round(t);
        if ((Math.floorMod(i, SLOTS) & 3) == 2) {
            i += t < i ? -1 : 1; // corners (2, 6, 10, 14) hide behind the inner walls: the next cell instead
        }
        return Math.floorMod(i, SLOTS);
    }

    // ==========================================================================================
    // Where the rack's front pipes are, and the passages through the inner walls
    // ==========================================================================================

    /** True if the rack on {@code side} has front pipes on {@code row} (1..3) at x, in the lane column beside it. */
    static boolean front(long seed, int band, int x, int side, int row) {
        return frontRow(ring(seed, band, x), side, row) && !frontCleared(seed, band, x, side);
    }

    /** True if the front pipes take the lane column on {@code side} at x (any row): nobody walks there. */
    static boolean laneBlocked(long seed, int band, int x, int side) {
        double s = ring(seed, band, x);
        return (frontRow(s, side, 1) || frontRow(s, side, 2) || frontRow(s, side, 3)) && !frontCleared(seed, band, x, side);
    }

    /**
     * True where the inner wall on {@code side} opens into a passage at x: a doorway, safe-room
     * door, bay, alcove, a void or loop door's hall, the hot zone's room, a bypass entry.
     */
    static boolean passage(long seed, int band, int x, int side) {
        if (Level2Line.feature(seed, band, x, side) != Level2Line.F_NONE) {
            return true;
        }
        int n = Level2Line.segment(seed, band, x);
        return Level2Line.segType(seed, band, n) == Level2Line.SEG_BYPASS && Level2Line.detourSide(seed, band, n) == side
                && Math.abs(x - Level2Line.segMid(seed, band, n)) == Level2Detours.BYPASS_HALF;
    }

    /**
     * True where the rack's front pipes on {@code side} make way at x: in front of a passage, at a
     * bulkhead ring (and the column either side of it), along a valve manifold (its valves stand
     * in the rack), and on the side a detour bends to (its way round must stay two wide).
     */
    static boolean frontCleared(long seed, int band, int x, int side) {
        if (passage(seed, band, x, side)) {
            return true;
        }
        int n = Level2Line.segment(seed, band, x);
        int type = Level2Line.segType(seed, band, n);
        if (type == Level2Line.SEG_BULKHEAD) {
            int frame = Level2Line.frameX(seed, band, n);
            if (frame != Integer.MIN_VALUE && Math.abs(x - frame) <= 1) {
                return true;
            }
        }
        if (Level2Line.detourSide(seed, band, n) == side) {
            int reach = type == Level2Line.SEG_DOGLEG ? Level2Detours.DOG_HALF
                    : type == Level2Line.SEG_CATWALK ? Level2Detours.CAT_HALF + 1
                    : Level2Detours.BYPASS_HALF;
            if (Math.abs(x - Level2Line.segMid(seed, band, n)) <= reach) {
                return true;
            }
        }
        return manifoldAt(seed, band, x, side);
    }

    /** True along a valve manifold: on its side of a valve segment, clear of the segment's two ends. */
    static boolean manifoldAt(long seed, int band, int x, int side) {
        int n = Level2Line.segment(seed, band, x);
        return Level2Line.segType(seed, band, n) == Level2Line.SEG_VALVES && side == Level2Line.featureSide(seed, band, n)
                && x > Level2Line.segStart(seed, band, n) + 1 && x < Level2Line.segEnd(seed, band, n) - 1;
    }

    // ==========================================================================================
    // The roll
    // ==========================================================================================

    /**
     * The roll built at x, in half steps, signed like {@link Level2Twist#bank}: {@link Level2Twist}'s
     * steps, cut down to the distance (in blocks) to the nearest column that needs a flat floor, so
     * the floor always steps down again half a block at a time before it.
     */
    static int bankSteps(long seed, int band, int x) {
        int b = Level2Twist.bankSteps(seed, band, x);
        if (b == 0) {
            return 0;
        }
        int steps = Math.abs(b);
        for (int d = 0; d < steps; d++) {
            if (flatAt(seed, band, x - d) || flatAt(seed, band, x + d)) {
                steps = d;
                break;
            }
        }
        return b < 0 ? -steps : steps;
    }

    /**
     * True where the floor must stay flat: a doorway or bay on either side, a bulkhead ring, and
     * the segments that do their own thing with the floor or the headroom (squeeze, trench, the
     * detours, the duct crossing).
     */
    private static boolean flatAt(long seed, int band, int x) {
        if (Level2Line.feature(seed, band, x, -1) != Level2Line.F_NONE || Level2Line.feature(seed, band, x, 1) != Level2Line.F_NONE) {
            return true;
        }
        int n = Level2Line.segment(seed, band, x);
        int type = Level2Line.segType(seed, band, n);
        if (type == Level2Line.SEG_BULKHEAD) {
            int frame = Level2Line.frameX(seed, band, n);
            return frame != Integer.MIN_VALUE && Math.abs(x - frame) <= 1;
        }
        return type == Level2Line.SEG_SQUEEZE || type == Level2Line.SEG_TRENCH || type == Level2Line.SEG_DUCT
                || Level2Detours.isDetour(type);
    }

    /**
     * How many half steps the floor of lane column dz (-1..1) rises for a roll of {@code steps}:
     * the full amount on the high edge, one less in the middle, none on the low edge. A positive
     * roll (clockwise looking along +x) lifts the north (left) edge.
     */
    static int raise(int steps, int dz) {
        if (steps == 0 || dz < -1 || dz > 1) {
            return 0;
        }
        int t = steps > 0 ? -dz : dz;
        int amount = Math.abs(steps);
        return t == 1 ? amount : t == 0 ? amount - 1 : 0;
    }

    /**
     * How many half steps the ceiling over lane column dz drops for a roll of {@code steps}: only
     * on the low edge, where the floor stays down, so 2 blocks of headroom remain everywhere.
     */
    static int drop(int steps, int dz) {
        if (steps == 0 || dz < -1 || dz > 1) {
            return 0;
        }
        int t = steps > 0 ? -dz : dz;
        return t == -1 ? Math.abs(steps) : 0;
    }
}
