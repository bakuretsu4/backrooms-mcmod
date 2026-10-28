package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;

/**
 * Where Level 0 is bright and where it is dark, at the scale of whole neighbourhoods, plus the rare
 * "liar" neighbourhoods whose exit arrows all point the wrong way.
 *
 * <p><b>Light districts.</b> A real office building is never evenly lit: some floors were
 * re-fitted and blaze with light, most are ordinary, some were half switched off to save money and
 * a few were simply abandoned. Two layers of smooth random noise (one ~130 blocks across, one ~55)
 * are added together and cut into four bands, so each district is an irregular blob a few rooms to
 * a hundred blocks across, and darkness comes in whole areas instead of a checkerboard:
 * <ul>
 *   <li>{@link #BRIGHT} (~15% of the floor): the full troffer grid, almost nothing dead.</li>
 *   <li>{@link #NORMAL} (~48%): about one panel every 6 blocks, the odd dead one, and now and then a
 *       whole bank of panels switched off, so pools of light with dark gaps between them.</li>
 *   <li>{@link #DIM} (~26%): the same panels, but nearly half dead and many banks off: mostly dark,
 *       with the odd lit pool you walk towards.</li>
 *   <li>{@link #ABANDONED} (~11%): almost every panel dead: flashlight country.</li>
 * </ul>
 * The layout keeps every panel where it was (so the ceiling grid still looks like an office
 * ceiling); this class only says which ones exist, which are dead, and which banks are off.
 *
 * <p><b>Banks.</b> Panels are wired in banks of 12x12 blocks. A switched-off bank is a contiguous
 * dark patch even inside a lit district, which is what makes a lit doorway far down a dark run read
 * as a beacon.
 *
 * <p>All of this is a pure function of (seed, x, z) and allocates nothing (a few hashes per panel).
 */
public final class Level0Lights {
    public static final int BRIGHT = 0;
    public static final int NORMAL = 1;
    public static final int DIM = 2;
    public static final int ABANDONED = 3;

    /** Size of the two noise layers (blocks between random values) and their mix. */
    private static final int BIG_SCALE = 128;
    private static final int SMALL_SCALE = 56;
    private static final double SMALL_WEIGHT = 0.35;
    /**
     * Cuts of the mixed noise (it is bunched around 0.5, so the cuts are not evenly spaced). Measured
     * shares over many 256x256 windows: bright ~15%, normal ~48%, dim ~26%, abandoned ~11%.
     */
    private static final double BRIGHT_ABOVE = 0.665;
    private static final double DIM_BELOW = 0.44;
    private static final double ABANDONED_BELOW = 0.31;

    /** Panels are switched in banks this many blocks square. */
    private static final int BANK = 12;
    /** Per district: chance a whole bank is off, and chance a single panel in a live bank is dead. */
    private static final double[] BANK_OFF = {0.02, 0.08, 0.35, 0.70};
    private static final double[] PANEL_DEAD = {0.03, 0.08, 0.45, 0.85};

    /**
     * Liar districts: rare blobs (~7% of the floor) where every exit arrow lies, always pointing
     * away from the exit, and arrows are drawn twice as often. Outside them most arrows tell the
     * truth, so a player can learn the rule: "where the arrows crowd together, do the opposite".
     */
    private static final int LIAR_SCALE = 96;
    private static final double LIAR_ABOVE = 0.82;

    private static final int SALT_BIG = 5201;
    private static final int SALT_SMALL = 5202;
    private static final int SALT_BANK = 5203;
    private static final int SALT_DEAD = 5204;
    private static final int SALT_LIAR = 5205;

    private Level0Lights() {}

    /** The light district (BRIGHT, NORMAL, DIM or ABANDONED) at column (x, z). */
    public static int district(long seed, int x, int z) {
        double n = (1.0 - SMALL_WEIGHT) * noise(seed, x, z, BIG_SCALE, SALT_BIG) + SMALL_WEIGHT * noise(seed, x, z, SMALL_SCALE, SALT_SMALL);
        if (n >= BRIGHT_ABOVE) {
            return BRIGHT;
        }
        if (n >= DIM_BELOW) {
            return NORMAL;
        }
        return n >= ABANDONED_BELOW ? DIM : ABANDONED;
    }

    /**
     * True if the panel (or strip piece) at (x, z) in a district is dead: its bank is switched off,
     * or the tube itself is out.
     */
    static boolean dead(long seed, int x, int z, int district) {
        if (Hash.unit(seed, Math.floorDiv(x, BANK), Math.floorDiv(z, BANK), SALT_BANK) < BANK_OFF[district]) {
            return true;
        }
        return Hash.unit(seed, x, z, SALT_DEAD) < PANEL_DEAD[district];
    }

    /** True if the single tube at (x, z) is dead (no bank check: for lights switched per room). */
    static boolean panelDead(long seed, int x, int z, int district) {
        return Hash.unit(seed, x, z, SALT_DEAD) < PANEL_DEAD[district];
    }

    /**
     * True if a 3x3 troffer-grid cell keeps its panel in this district. Bright districts keep every
     * cell (a panel every 3 blocks); everywhere else one cell in each 2x2 block of cells (a panel
     * about every 6 blocks), and in three in five of those blocks a second one beside it, making a pair.
     */
    static boolean keepsCell(long seed, int cellX, int cellZ, int district) {
        if (district == BRIGHT) {
            return true;
        }
        int bx = Math.floorDiv(cellX, 2);
        int bz = Math.floorDiv(cellZ, 2);
        long h = Hash.of(seed, bx, bz, 5206);
        int pick = (int) (h & 3);
        int mine = (cellX - 2 * bx) | (cellZ - 2 * bz) << 1;
        if (mine == pick) {
            return true;
        }
        // The pair partner: the cell beside the picked one along X.
        return Hash.unit(Hash.mix(h)) < 0.6 && mine == (pick ^ 1);
    }

    /** True inside a liar district: every exit arrow there points away from the exit. */
    public static boolean liar(long seed, int x, int z) {
        return noise(seed, x, z, LIAR_SCALE, SALT_LIAR) >= LIAR_ABOVE;
    }

    /** Smooth value noise in [0, 1): random values on a grid, blended with a smoothstep. */
    private static double noise(long seed, int x, int z, int scale, int salt) {
        int gx = Math.floorDiv(x, scale);
        int gz = Math.floorDiv(z, scale);
        double tx = smooth((x - gx * scale) / (double) scale);
        double tz = smooth((z - gz * scale) / (double) scale);
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
