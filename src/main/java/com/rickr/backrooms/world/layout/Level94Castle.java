package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The castle of Level 94 on its transparent glass mountain, "visible from everywhere".
 *
 * <p>There is one castle in every {@value #REGION} x {@value #REGION} square ("region"), at a
 * spot picked by hashing the region. Around it:
 * <ul>
 *   <li><b>the glass mountain</b>: a smooth glass hill {@value #MOUNTAIN_RADIUS} blocks in radius at
 *       the ground, curving up to a flat plateau (radius {@value #PLATEAU_RADIUS}) whose surface is
 *       y{@value #MOUNTAIN_TOP_Y};</li>
 *   <li><b>the ramp</b>: a 3-wide opal path that spirals up the outside of the mountain, about one
 *       block up for every 3-4 blocks walked, in half-block steps so nobody has to jump. A low
 *       railing runs along its outer edge. A short straight path joins it to {@link #rampFoot},
 *       where the World slice's road arrives;</li>
 *   <li><b>the castle</b> on the plateau, its gate facing the top of the ramp. Its blocks come from
 *       a template worked out once ({@link Level94CastlePlan}).</li>
 * </ul>
 *
 * <p>Everything here is a pure function of the seed and the coordinates, and cheap: the World
 * slice calls {@link #fillColumn} for every column near a castle, so the shapes are looked up in
 * tables made once when the class loads, and nothing is allocated per column.
 */
public final class Level94Castle {
    public static final int REGION = 1024;          // one castle per 1024x1024 region
    public static final int MOUNTAIN_RADIUS = 64;   // glass mountain base radius at ground level
    public static final int CLEAR_RADIUS = 84;      // World keeps ground flat (grass top y40) and builds nothing of its own within this distance of a castle centre, except the castle road
    public static final int MOUNTAIN_TOP_Y = 150;   // plateau surface
    public static final int CASTLE_TOP_Y = 215;     // highest spire tip

    /** Radius of the flat top of the mountain. */
    public static final int PLATEAU_RADIUS = 24;

    /** Top block of the flat grass around the mountain (the mountain starts on top of it). */
    private static final int GROUND_Y = LevelLayout.FLOOR_Y;
    /** How far a castle centre stays from its region's edges, so neighbouring castles never crowd. */
    private static final int EDGE_MARGIN = CLEAR_RADIUS + 64;

    /** Unit steps for "quarter" 0..3 = east, south, west, north (the angle is quarter * 90 degrees). */
    private static final int[] QUARTER_X = {1, 0, -1, 0};
    private static final int[] QUARTER_Z = {0, 1, 0, -1};

    private static final double HALF_PI = Math.PI / 2;
    private static final double TWO_PI = Math.PI * 2;

    // ==========================================================================================
    // Where the castles are
    // ==========================================================================================

    /** {x, z} of the castle of region (regionX, regionZ). */
    public static int[] centre(long seed, int regionX, int regionZ) {
        return new int[] {centreX(seed, regionX, regionZ), centreZ(seed, regionX, regionZ)};
    }

    /** {x, z} of the castle nearest to (x, z). Only the 3x3 regions around (x, z) can hold it. */
    public static int[] nearest(long seed, int x, int z) {
        long packed = nearestPacked(seed, x, z);
        return new int[] {unpackX(packed), unpackZ(packed)};
    }

    /**
     * Where the castle road meets the castle's own approach path: {x, z, dirX, dirZ}. It lies just
     * outside {@link #CLEAR_RADIUS}, straight out from the centre in one of the four compass
     * directions (picked per castle), and (dirX, dirZ) is that outward direction.
     */
    public static int[] rampFoot(long seed, int cx, int cz) {
        int q = footQuarter(seed, cx, cz);
        int reach = CLEAR_RADIUS + 1;
        return new int[] {cx + QUARTER_X[q] * reach, cz + QUARTER_Z[q] * reach, QUARTER_X[q], QUARTER_Z[q]};
    }

    private static int centreX(long seed, int regionX, int regionZ) {
        return regionX * REGION + EDGE_MARGIN + Hash.range(Hash.of(seed, regionX, regionZ, 94_001), REGION - 2 * EDGE_MARGIN + 1);
    }

    private static int centreZ(long seed, int regionX, int regionZ) {
        return regionZ * REGION + EDGE_MARGIN + Hash.range(Hash.of(seed, regionX, regionZ, 94_002), REGION - 2 * EDGE_MARGIN + 1);
    }

    /** Same as {@link #nearest}, but packs x and z into one long so the per-column code allocates nothing. */
    static long nearestPacked(long seed, int x, int z) {
        int rx = Math.floorDiv(x, REGION);
        int rz = Math.floorDiv(z, REGION);
        long best = Long.MAX_VALUE;
        int bestX = 0;
        int bestZ = 0;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int cx = centreX(seed, rx + ox, rz + oz);
                int cz = centreZ(seed, rx + ox, rz + oz);
                long dx = x - cx;
                long dz = z - cz;
                long d2 = dx * dx + dz * dz;
                if (d2 < best) {
                    best = d2;
                    bestX = cx;
                    bestZ = cz;
                }
            }
        }
        return ((long) bestX << 32) | (bestZ & 0xFFFFFFFFL);
    }

    static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    static int unpackZ(long packed) {
        return (int) packed;
    }

    /** Which way (quarter 0..3) the ramp starts from the castle: the direction of the ramp foot. */
    private static int footQuarter(long seed, int cx, int cz) {
        return Hash.range(Hash.of(seed, cx, cz, 94_003), 4);
    }

    /** +1: the ramp winds clockwise seen from above (angle growing), -1: counter-clockwise. */
    private static int spin(long seed, int cx, int cz) {
        return (Hash.of(seed, cx, cz, 94_004) & 1) == 0 ? 1 : -1;
    }

    /** The quarter (0..3) the top of the ramp arrives at, which the castle gate faces. */
    private static int arrivalQuarter(int footQuarter, int spin) {
        return Math.floorMod(footQuarter + spin * RAMP_QUARTERS, 4);
    }

    /**
     * Quarter turns (clockwise from above) that take the castle template (drawn with its gate on
     * the north side) to the world, so the gate faces the arrival quarter.
     */
    private static int castleTurns(int arrivalQuarter) {
        return (arrivalQuarter + 1) & 3; // north (3) -> 0 turns, east (0) -> 1, south (1) -> 2, west (2) -> 3
    }

    /** Quarter turns (clockwise from above) of the castle centred at (cx, cz): template to world. */
    static int turnsAt(long seed, int cx, int cz) {
        return castleTurns(arrivalQuarter(footQuarter(seed, cx, cz), spin(seed, cx, cz)));
    }

    /** Template x of the world offset (dx, dz), undoing {@code turns} clockwise quarter turns. */
    static int localX(int dx, int dz, int turns) {
        return switch (turns) {
            case 0 -> dx;
            case 1 -> dz;
            case 2 -> -dx;
            default -> -dz;
        };
    }

    /** Template z of the world offset (dx, dz). */
    static int localZ(int dx, int dz, int turns) {
        return switch (turns) {
            case 0 -> dz;
            case 1 -> -dx;
            case 2 -> -dz;
            default -> dx;
        };
    }

    // ==========================================================================================
    // The mountain's shape and the ramp (tables made once)
    // ==========================================================================================

    /** Higher = the sides curve more: wide gentle foot, steep top (like cc3). */
    private static final double PROFILE_POWER = 1.6;
    /** Aim for this many blocks walked per block climbed; rounded so the ramp ends on a quarter turn. */
    private static final double TARGET_RUN = 3.5;
    /** Feet height on the flat ground and on the plateau. */
    private static final int GROUND_FEET = GROUND_Y + 1;
    private static final int PLATEAU_FEET = MOUNTAIN_TOP_Y + 1;

    /** Highest glass block for each squared distance from the centre (GROUND_Y = no glass). */
    private static final int[] GLASS_TOP = new int[MOUNTAIN_RADIUS * MOUNTAIN_RADIUS + 1];

    /** The ramp, sampled every RAMP_ANGLE / RAMP_STEPS radians from its foot: feet height and centre-line radius. */
    private static final int RAMP_STEPS = 4096;
    private static final float[] RAMP_FEET = new float[RAMP_STEPS + 1];
    private static final float[] RAMP_RADIUS = new float[RAMP_STEPS + 1];
    /** How far the ramp turns from foot to top, in quarter turns and radians. */
    private static final int RAMP_QUARTERS;
    private static final double RAMP_ANGLE;
    /** Gap in the plateau's rim railing where the ramp arrives: this many blocks of arc back from the top (the last half-steps). */
    private static final double ARRIVAL_GAP = 2.5;

    static {
        for (int d2 = 0; d2 < GLASS_TOP.length; d2++) {
            GLASS_TOP[d2] = (int) Math.floor(glassTop(Math.sqrt(d2)));
        }

        // The ramp is climbed at a steady slope: d(angle) = run * d(height) / radius. Integrate
        // 1/radius over the climb, then pick the run so the ramp turns a whole number of quarter
        // turns (so the top arrives square in front of the castle gate).
        double dh = 0.005;
        double integral = 0;
        for (double h = GROUND_FEET; h < PLATEAU_FEET; h += dh) {
            integral += dh / rampRadius(h + dh / 2);
        }
        RAMP_QUARTERS = (int) Math.ceil(TARGET_RUN * integral / HALF_PI);
        RAMP_ANGLE = RAMP_QUARTERS * HALF_PI;
        double run = RAMP_ANGLE / integral;

        double theta = 0;
        double h = GROUND_FEET;
        for (int i = 0; i <= RAMP_STEPS; i++) {
            double target = i * RAMP_ANGLE / RAMP_STEPS;
            while (theta < target && h < PLATEAU_FEET) {
                theta += run * dh / rampRadius(h + dh / 2);
                h += dh;
            }
            double feet = i == RAMP_STEPS ? PLATEAU_FEET : Math.min(h, PLATEAU_FEET);
            RAMP_FEET[i] = (float) feet;
            RAMP_RADIUS[i] = (float) rampRadius(feet);
        }
    }

    /**
     * Radius of the mountain at the height of glass top y (GROUND_Y..MOUNTAIN_TOP_Y): 64 at the
     * ground, 24 at the plateau, curving like cc3's glass hill.
     */
    private static double mountainRadius(double y) {
        double t = Math.min(1, Math.max(0, (y - GROUND_Y) / (MOUNTAIN_TOP_Y - GROUND_Y)));
        return PLATEAU_RADIUS + (MOUNTAIN_RADIUS - PLATEAU_RADIUS) * Math.pow(1 - t, PROFILE_POWER);
    }

    /** Glass top height at distance d: the inverse of {@link #mountainRadius}. */
    private static double glassTop(double d) {
        if (d <= PLATEAU_RADIUS) {
            return MOUNTAIN_TOP_Y;
        }
        if (d >= MOUNTAIN_RADIUS) {
            return GROUND_Y;
        }
        double s = (d - PLATEAU_RADIUS) / (MOUNTAIN_RADIUS - PLATEAU_RADIUS);
        return GROUND_Y + (MOUNTAIN_TOP_Y - GROUND_Y) * (1 - Math.pow(s, 1 / PROFILE_POWER));
    }

    /**
     * Centre line of the 3-wide ramp at a given feet height: its inner edge is where the glass
     * surface is level with the ramp's floor, so the glass rises as a wall on the inside and the
     * ramp is built out over the slope on the outside.
     */
    private static double rampRadius(double feet) {
        return mountainRadius(feet - 1) + 1.5;
    }

    /** What a column of the ramp holds (see {@link #rampAt}). */
    private static final int RAMP_NONE = 0;
    private static final int RAMP_PATH = 1;
    private static final int RAMP_RAIL = 2;

    /**
     * Is world offset (dx, dz) on the ramp? Returns {@code kind + 4 * (feet * 2)}: kind is
     * RAMP_PATH or RAMP_RAIL (the outer railing), feet the ramp's walking height there, rounded to
     * half blocks. RAMP_NONE (0) when it is neither.
     */
    private static int rampAt(int dx, int dz, double d, int footQuarter, int spin) {
        double rel = spin * (Math.atan2(dz, dx) - footQuarter * HALF_PI);
        rel -= Math.floor(rel / TWO_PI) * TWO_PI; // angle from the ramp foot, 0..2pi, in the climbing direction
        // The ramp goes round more than once, so at this angle it may pass several times (each
        // time higher and nearer the centre).
        for (double theta = rel; ; theta += TWO_PI) {
            if (theta > RAMP_ANGLE) {
                // Just past the top: a railing closes the end of the ramp, so you turn onto the plateau.
                double past = (theta - RAMP_ANGLE) * RAMP_RADIUS[RAMP_STEPS];
                double off = d - RAMP_RADIUS[RAMP_STEPS];
                if (past <= 2 && off >= -1.5 && off <= 2.5) {
                    return RAMP_RAIL + 4 * (PLATEAU_FEET * 2);
                }
                return RAMP_NONE;
            }
            int i = (int) Math.round(theta / RAMP_ANGLE * RAMP_STEPS);
            double off = d - RAMP_RADIUS[i];
            int feet2 = Math.round(RAMP_FEET[i] * 2);
            if (Math.abs(off) <= 1.5) {
                return RAMP_PATH + 4 * feet2;
            }
            // The railing starts once the ramp is a block and a half off the ground (not across the approach).
            if (off > 1.5 && off <= 2.5 && feet2 >= 2 * GROUND_FEET + 3) {
                return RAMP_RAIL + 4 * feet2;
            }
        }
    }

    /** Signed arc (blocks, along the plateau rim) from the top of the ramp to angle a; negative = before it. */
    private static double arcFromArrival(int dx, int dz, int arrivalQuarter, int spin) {
        double rel = spin * (Math.atan2(dz, dx) - arrivalQuarter * HALF_PI);
        rel -= Math.floor(rel / TWO_PI + 0.5) * TWO_PI; // -pi..pi
        return rel * PLATEAU_RADIUS;
    }

    // ==========================================================================================
    // Writing columns
    // ==========================================================================================

    /** Writes the mountain, ramp, approach and castle for column (x, z); false if no castle is near. */
    public static boolean fillColumn(long seed, int x, int z, LevelLayout.Column out) {
        long packed = nearestPacked(seed, x, z);
        int cx = unpackX(packed);
        int cz = unpackZ(packed);
        int dx = x - cx;
        int dz = z - cz;
        int d2 = dx * dx + dz * dz;
        if (d2 > CLEAR_RADIUS * CLEAR_RADIUS) {
            return false;
        }
        int foot = footQuarter(seed, cx, cz);
        int spin = spin(seed, cx, cz);
        BlockState glass = ModBlocks.L94_GLASS_MOUNTAIN.get().defaultBlockState();
        BlockState path = ModBlocks.L94_OPAL_PATH.get().defaultBlockState();

        // The plateau: glass all the way up, opal paving on top, a railing round the rim, the castle.
        if (d2 < PLATEAU_RADIUS * PLATEAU_RADIUS) {
            fill(out, GROUND_Y + 1, MOUNTAIN_TOP_Y - 1, glass);
            out.set(MOUNTAIN_TOP_Y, path);
            int arrival = arrivalQuarter(foot, spin);
            if (d2 >= (PLATEAU_RADIUS - 1) * (PLATEAU_RADIUS - 1)) {
                double arc = arcFromArrival(dx, dz, arrival, spin);
                if (arc < -ARRIVAL_GAP || arc > 0.5) {
                    out.set(PLATEAU_FEET, ModBlocks.L94_OPAL_RAIL.get().defaultBlockState());
                }
            }
            int turns = castleTurns(arrival);
            int lx = localX(dx, dz, turns);
            int lz = localZ(dx, dz, turns);
            if (Level94CastlePlan.covers(lx, lz)) {
                for (int ly = Level94CastlePlan.MIN_LY; ly <= Level94CastlePlan.MAX_LY; ly++) {
                    BlockState state = Level94CastlePlan.state(lx, ly, lz, turns);
                    if (state != null) {
                        out.set(MOUNTAIN_TOP_Y + ly, state);
                    }
                }
            }
            return true;
        }

        double d = Math.sqrt(d2);
        // The spiral ramp (and its railing), built on a glass footing.
        if (d < MOUNTAIN_RADIUS + 4) {
            int ramp = rampAt(dx, dz, d, foot, spin);
            if (ramp != RAMP_NONE) {
                int feet2 = ramp >> 2;
                int floorY = feet2 / 2 - 1;           // the full block under the feet (or under the half step)
                fill(out, GROUND_Y + 1, floorY - 1, glass);
                if ((ramp & 3) == RAMP_PATH) {
                    out.set(floorY, path);
                    if ((feet2 & 1) == 1) {
                        out.set(floorY + 1, ModBlocks.L94_OPAL_PATH_SLAB.get().defaultBlockState()
                                .setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                    }
                } else {
                    int railY = (feet2 + 1) / 2;      // sits on top of a full block at (or just above) the walking height
                    fill(out, floorY, railY - 1, path);
                    out.set(railY, ModBlocks.L94_OPAL_RAIL.get().defaultBlockState());
                }
                return true;
            }
        }

        // The straight 3-wide approach path from the ramp foot to the start of the ramp, at ground level.
        int along = dx * QUARTER_X[foot] + dz * QUARTER_Z[foot];
        int across = dx * QUARTER_Z[foot] - dz * QUARTER_X[foot];
        if (Math.abs(across) <= 1 && along >= MOUNTAIN_RADIUS) {
            out.set(GROUND_Y, path);
            return true;
        }

        // Plain glass mountainside.
        if (d2 < GLASS_TOP.length) {
            fill(out, GROUND_Y + 1, GLASS_TOP[d2], glass);
        }
        return true;
    }

    private static void fill(LevelLayout.Column out, int y0, int y1, BlockState state) {
        for (int y = y0; y <= y1; y++) {
            out.set(y, state);
        }
    }

    // ==========================================================================================
    // Questions other code asks
    // ==========================================================================================

    /** True inside the white light behind the throne: stepping in sends you to Level 1. */
    public static boolean isThroneLight(long seed, int x, int y, int z) {
        return Level94CastlePlan.isThroneLight(templateCode(seed, x, y, z));
    }

    /** True inside the castle building (rooms, walls, towers, roofs). No Smilers in here. */
    public static boolean isInsideCastle(long seed, int x, int y, int z) {
        return templateCode(seed, x, y, z) != Level94CastlePlan.OUT;
    }

    /** The template code at world (x, y, z), or OUT when that is not part of a castle. */
    private static int templateCode(long seed, int x, int y, int z) {
        int ly = y - MOUNTAIN_TOP_Y;
        if (ly < Level94CastlePlan.MIN_LY || ly > Level94CastlePlan.MAX_LY) {
            return Level94CastlePlan.OUT;
        }
        long packed = nearestPacked(seed, x, z);
        int dx = x - unpackX(packed);
        int dz = z - unpackZ(packed);
        if (dx * dx + dz * dz >= PLATEAU_RADIUS * PLATEAU_RADIUS) {
            return Level94CastlePlan.OUT;
        }
        int cx = unpackX(packed);
        int cz = unpackZ(packed);
        int turns = castleTurns(arrivalQuarter(footQuarter(seed, cx, cz), spin(seed, cx, cz)));
        return Level94CastlePlan.code(localX(dx, dz, turns), ly, localZ(dx, dz, turns));
    }

    /**
     * Feet height in column (x, z) on the approach, the ramp, the plateau or the castle's ground
     * floor; NaN anywhere else (glass slopes, railings, walls, the ball pit, far from a castle).
     */
    public static double standY(long seed, int x, int z) {
        long packed = nearestPacked(seed, x, z);
        int cx = unpackX(packed);
        int cz = unpackZ(packed);
        int dx = x - cx;
        int dz = z - cz;
        int d2 = dx * dx + dz * dz;
        if (d2 > CLEAR_RADIUS * CLEAR_RADIUS) {
            return Double.NaN;
        }
        int foot = footQuarter(seed, cx, cz);
        int spin = spin(seed, cx, cz);
        if (d2 < PLATEAU_RADIUS * PLATEAU_RADIUS) {
            int arrival = arrivalQuarter(foot, spin);
            int turns = castleTurns(arrival);
            int lx = localX(dx, dz, turns);
            int lz = localZ(dx, dz, turns);
            if (Level94CastlePlan.covers(lx, lz) && Level94CastlePlan.code(lx, 0, lz) != Level94CastlePlan.OUT) {
                return Level94CastlePlan.isStandable(lx, lz) ? PLATEAU_FEET : Double.NaN;
            }
            if (d2 >= (PLATEAU_RADIUS - 1) * (PLATEAU_RADIUS - 1)) {
                double arc = arcFromArrival(dx, dz, arrival, spin);
                if (arc < -ARRIVAL_GAP || arc > 0.5) {
                    return Double.NaN; // the rim railing
                }
            }
            return PLATEAU_FEET;
        }
        double d = Math.sqrt(d2);
        if (d < MOUNTAIN_RADIUS + 4) {
            int ramp = rampAt(dx, dz, d, foot, spin);
            if (ramp != RAMP_NONE) {
                return (ramp & 3) == RAMP_PATH ? (ramp >> 2) / 2.0 : Double.NaN;
            }
        }
        int along = dx * QUARTER_X[foot] + dz * QUARTER_Z[foot];
        int across = dx * QUARTER_Z[foot] - dz * QUARTER_X[foot];
        if (Math.abs(across) <= 1 && along >= MOUNTAIN_RADIUS) {
            return GROUND_FEET;
        }
        return Double.NaN;
    }

    private Level94Castle() {}
}
