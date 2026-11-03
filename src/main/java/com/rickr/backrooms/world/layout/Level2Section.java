package com.rickr.backrooms.world.layout;

/**
 * The cross-section of Level 2's one-block corridor, and how its dressing corkscrews round it
 * ({@link Level2Twist}). Only numbers here: {@link Level2Layout} turns them into blocks.
 *
 * <p><b>The ring.</b> Seen down the corridor (looking along +x) the lane is one block wide (dz 0)
 * and two high (y41-42) under the ceiling at y43. Round it runs a ring of 10 cells, numbered
 * anticlockwise from the low cell of the right wall (south, +z), each at its real angle round the
 * middle of the lane, the way {@link Level2Twist#helix} counts (0 right, 90 up, 180 left, 270 down):
 * <pre>
 *            4   3   2          y43  ceiling (and its corners)
 *            5   .   1          y42
 *            6   .   0          y41
 *            7   8   9          y40  floor (and its corners)
 *   dz      -1   0  +1
 * </pre>
 * The pipe bundle fills the cells within {@link #BUNDLE_HALF} of the helix angle: four or five
 * cells, a rack up one wall and over a corner, a mat of pipes and tray overhead, or a bed of pipes
 * under a floor grating. The light panel rides a quarter turn ahead of it (never on the floor), the
 * painted band half a turn away, the fans and beacons in what is left: so everything turns together.
 *
 * <p>Pure functions; nothing allocates.
 */
final class Level2Section {
    /** Cells round the ring. */
    static final int SLOTS = 10;
    /** The bundle fills the cells this close to its angle (radians: 67.5 degrees either way). */
    private static final double BUNDLE_HALF = Math.toRadians(67.5);

    /** Ring cell of (dz, row) for dz -1..1 (index dz + 1) and row 0..3 above the floor (y40..y43); -1 for the lane. */
    private static final int[][] SLOT_AT = {
            {7, 6, 5, 4},
            {8, -1, -1, 3},
            {9, 0, 1, 2},
    };
    /** Each cell's dz, row and angle round the middle of the lane (dz 0, y 42.0). */
    private static final int[] SLOT_DZ = {1, 1, 1, 0, -1, -1, -1, -1, 0, 1};
    private static final int[] SLOT_ROW = {1, 2, 3, 3, 3, 2, 1, 0, 0, 0};
    private static final double[] ANGLE = new double[SLOTS];

    static {
        for (int i = 0; i < SLOTS; i++) {
            // Cell centre minus the lane's middle (y 42.0 = floor 40 + 2): rows are block bottoms + 0.5.
            double dy = SLOT_ROW[i] + 0.5 - 2.0;
            double a = Math.atan2(dy, SLOT_DZ[i]);
            ANGLE[i] = a < 0 ? a + 2.0 * Math.PI : a;
        }
    }

    /** The wall cells (rows 1-2 of each wall), the ceiling and the floor: the cells you see from the lane. */
    private static final int[] VISIBLE = {0, 1, 3, 5, 6, 8};
    /** The high wall cells (row 2) and the low ones (row 1, the foot of each wall). */
    private static final int[] HIGH = {1, 5};
    private static final int[] FEET = {0, 6};
    /** The floor cell: never a lamp, fan or beacon. */
    static final int FLOOR = 8;
    /** The middle of the ceiling. */
    static final int CEILING = 3;

    private Level2Section() {}

    /** The ring cell at (dz, row), or -1 (the lane itself, or off the ring). */
    static int slot(int dz, int row) {
        if (dz < -1 || dz > 1 || row < 0 || row > 3) {
            return -1;
        }
        return SLOT_AT[dz + 1][row];
    }

    static int slotDz(int i) {
        return SLOT_DZ[i];
    }

    static int slotRow(int i) {
        return SLOT_ROW[i];
    }

    /** True for a cell in a wall (rows 1-2 of dz -1 or +1). */
    static boolean wallCell(int i) {
        return i == 0 || i == 1 || i == 5 || i == 6;
    }

    /** The bundle's angle at x (radians, 0..2 PI). */
    static double ring(long seed, int band, int x) {
        return Level2Twist.helixAlong(seed, band, x);
    }

    /** How far round the ring (radians, 0..PI) cell i is from angle a. */
    static double distance(double a, int i) {
        double d = Math.abs(ANGLE[i] - a) % (2.0 * Math.PI);
        return Math.min(d, 2.0 * Math.PI - d);
    }

    /** True if ring cell i holds part of the pipe bundle at angle a. */
    static boolean bundle(double a, int i) {
        return i >= 0 && distance(a, i) <= BUNDLE_HALF;
    }

    /** The visible cell nearest angle {@code target} that is not in the bundle at angle a, not {@code skip1}/{@code skip2}, and (unless {@code floor}) not the floor; -1 if none. */
    private static int nearestFree(double a, double target, int skip1, int skip2, boolean floor) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i : VISIBLE) {
            if (bundle(a, i) || i == skip1 || i == skip2 || (!floor && i == FLOOR)) {
                continue;
            }
            double d = distance(target, i);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** The light panel's cell at angle a: a quarter turn ahead of the bundle, never the floor. */
    static int lampSlot(double a) {
        return nearestFree(a, a + Math.PI / 2.0, -1, -1, false);
    }

    /** The painted band's cell at angle a: straight across from the bundle (the floor line when that is the floor). */
    static int stripeSlot(double a) {
        return nearestFree(a, a + Math.PI, lampSlot(a), -1, true);
    }

    /** The cell for a fan at angle a (a wall cell a quarter turn behind the bundle), or -1. */
    static int fanSlot(double a, int lamp) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i : VISIBLE) {
            if (!wallCell(i) || bundle(a, i) || i == lamp) {
                continue;
            }
            double d = distance(a - Math.PI / 2.0, i);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** The cell for a beacon at angle a: the ceiling if it is free, else a high wall cell; -1 if neither. */
    static int beaconSlot(double a, int lamp) {
        if (!bundle(a, CEILING) && lamp != CEILING) {
            return CEILING;
        }
        for (int i : HIGH) {
            if (!bundle(a, i) && i != lamp) {
                return i;
            }
        }
        return -1;
    }

    /** The low wall cell (row 1) away from the bundle, for a supply or radio, or -1. */
    static int footSlot(double a, int avoid) {
        int best = -1;
        double bestD = -1.0;
        for (int i : FEET) {
            if (bundle(a, i) || i == avoid) {
                continue;
            }
            double d = distance(a, i);
            if (d > bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** The wall (-1 north, +1 south) whose high cell is furthest from the bundle (where the bare concrete is). */
    static int bareSide(double a) {
        return distance(a, 5) >= distance(a, 1) ? -1 : 1;
    }
}
