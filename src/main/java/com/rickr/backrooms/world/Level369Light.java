package com.rickr.backrooms.world;

import com.rickr.backrooms.world.layout.Level369Plan;

/**
 * The warm light of Level 369 that drifts from room to room "like a wave" (the wiki), and then the
 * room falls back to its usual light. A pure function of (seed, x, z, game time), so the server
 * (sanity, healing) and every client (the glow) agree on where it is without any syncing.
 *
 * <p>Bands of light {@link #WAVELENGTH} blocks apart sweep across the whole level in one direction
 * (fixed per world), at a slow walking pace: one passes any spot every {@link #PERIOD_TICKS} ticks
 * (4 minutes) and stays about 45 seconds. The front is bent by a slow noise, so it reaches rooms
 * side by side at different moments and never reads as a straight line.
 *
 * <p>It is kind (the user: "just overall good things"): in it your sanity comes back and you heal.
 * Its lie is only the comfort: it makes you want to stay while the level makes you forget.
 */
public final class Level369Light {
    /** Blocks between two bands of light. */
    public static final double WAVELENGTH = 160.0;
    /** Ticks between two bands passing the same spot. */
    public static final long PERIOD_TICKS = 4800L;
    /** How narrow a band is (higher = narrower; 6 gives ~30 blocks across at half strength). */
    private static final double SHARPNESS = 6.0;
    /** How far (blocks) the front is bent forward and back by the noise. */
    private static final double BEND = 45.0;

    /** Sanity regained per second at full strength. */
    public static final float SANITY_PER_SECOND = 1.2f;

    private Level369Light() {}

    /** How much of the drifting light is at (x, z) at {@code gameTime}: 0 (none) .. 1 (the heart of a band). */
    public static float strength(long seed, double x, double z, long gameTime) {
        double angle = Hash.unit(seed, 0, 0, 3720) * 2.0 * Math.PI;
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        double along = x * Math.cos(angle) + z * Math.sin(angle)
                + BEND * 2.0 * (Level369Plan.smooth(seed, ix, iz, 90.0, 3721) - 0.5);
        double phase = along / WAVELENGTH - (double) Math.floorMod(gameTime, PERIOD_TICKS) / PERIOD_TICKS;
        double c = 0.5 + 0.5 * Math.cos(2.0 * Math.PI * phase);
        return (float) Math.pow(c, SHARPNESS);
    }
}
