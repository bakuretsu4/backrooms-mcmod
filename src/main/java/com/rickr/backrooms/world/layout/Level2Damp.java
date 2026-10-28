package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;

/**
 * Where Level 2's concrete is damp: the stretches of corridor whose walls, floor and ceiling get
 * their wet looks (condensation running down from the ceiling, tide lines, wet patches on the
 * floor, damp blooms on the slab; see block/Level2SurfaceBlock).
 *
 * <p>Water in a real service tunnel isn't sprinkled evenly: it collects round a leak and in long
 * low stretches where the cold pipes sweat. So a block is damp if it is near the middle of a leak
 * segment (where the burst main drips), or inside one of the patches of a soft "damp field" that
 * is stretched along the corridor (patches come out roughly 12 blocks long and 5 wide, about a
 * fifth of the corridor).
 *
 * <p>Pure arithmetic on (seed, x, z): it is called for every surface block while a chunk is
 * generated, so it must not allocate.
 */
public final class Level2Damp {
    /** Size of one cell of the damp field in blocks, along the corridor (x) and across it (z). */
    private static final int CELL_X = 12;
    private static final int CELL_Z = 5;
    /** Field values above this are damp (about 18% of the map). */
    private static final double DAMP_FROM = 0.72;
    /** How far from the middle of a leak segment (where the drips are) the wet reaches, in blocks. */
    private static final int LEAK_REACH = 9;
    /** Hash salt of the damp field (its own, so it doesn't line up with any other pattern). */
    private static final int SALT = 7351;

    private Level2Damp() {
    }

    /** True if the surfaces at (x, z) should look damp. */
    public static boolean isDamp(long seed, int x, int z) {
        if (!Level2Line.outsideReach(z)) {
            int band = Level2Line.band(z);
            int n = Level2Line.segment(seed, band, x);
            if (Level2Line.segType(seed, band, n) == Level2Line.SEG_LEAK
                    && Math.abs(x - Level2Line.segMid(seed, band, n)) <= LEAK_REACH) {
                return true;
            }
        }
        return field(seed, x, z) > DAMP_FROM;
    }

    /** The damp field at (x, z), 0..1: random values on a coarse grid, blended smoothly between. */
    private static double field(long seed, int x, int z) {
        int cx = Math.floorDiv(x, CELL_X);
        int cz = Math.floorDiv(z, CELL_Z);
        double fx = smooth((x - cx * CELL_X + 0.5) / CELL_X);
        double fz = smooth((z - cz * CELL_Z + 0.5) / CELL_Z);
        double a = Hash.unit(seed, cx, cz, SALT);
        double b = Hash.unit(seed, cx + 1, cz, SALT);
        double c = Hash.unit(seed, cx, cz + 1, SALT);
        double d = Hash.unit(seed, cx + 1, cz + 1, SALT);
        double top = a + (b - a) * fx;
        double bottom = c + (d - c) * fx;
        return top + (bottom - top) * fz;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }
}
