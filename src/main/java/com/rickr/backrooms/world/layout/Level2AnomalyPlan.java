package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level2.Level2Sites;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * Where Level 2's anomalous stretches are along the corridors. What they DO lives in
 * world/anomaly/level2 (server) and client/anomaly/level2. Everything here is a pure function of
 * (seed, x, z), like the rest of {@link Level2Layout}.
 *
 * <p>Nothing here leads out of the corridor: Level 2 is one straight line. Each corridor is cut
 * into stretches {@link #CELL} blocks long; every other stretch (alternating from band to band)
 * may be a <b>brownout</b>, 30-60 blocks where the Blackout never ended (most panels dead, violet
 * battery lights, burst pipes and pools of black ooze). Stretches without one may hold one of the
 * quiet oddities instead ({@link Level2Oddities}). No site comes near an exit.
 */
final class Level2AnomalyPlan {
    /** Length (blocks along X) of the stretches sites are planned in. */
    static final int CELL = 180;
    /** Sites keep this far (along the corridor) from an exit door. */
    private static final int EXIT_GAP = 24;
    /** Out of 1: share of the brownout stretches that really get a brownout. */
    private static final double BROWNOUT_CHANCE = 0.4;

    /** Out of 1: share of the light panels in a brownout that still work. */
    private static final double BROWNOUT_LAMP_SURVIVES = 0.15;
    /** Out of 1: share of the emergency-light spots in a brownout that have one (most: they are its only light). */
    static final double EMERGENCY_LIGHT_CHANCE = 0.9;
    /** Out of 1: share of a brownout's 12-block stretches with a burst pipe and a pool of ooze. */
    private static final double BURST_CHANCE = 0.55;
    private static final int BURST_STRETCH = 12;

    private Level2AnomalyPlan() {}

    // ==========================================================================================
    // Queries
    // ==========================================================================================

    /** The site covering column (x, z), or null. */
    @Nullable
    static AnomalySite at(long seed, int x, int z) {
        int band = Level2Line.band(z);
        int cell = Math.floorDiv(x, CELL);
        AnomalySite site = cellSite(seed, band, cell);
        if (site != null && site.contains(x, z)) {
            return site;
        }
        AnomalySite odd = Level2Oddities.site(seed, band, cell);
        return odd != null && odd.contains(x, z) ? odd : null;
    }

    static void near(long seed, int x, int z, int radius, Consumer<AnomalySite> out) {
        int reach = radius + Level2Line.BAND;
        for (int b = Level2Line.band(z - reach); b <= Level2Line.band(z + reach); b++) {
            for (int cell = Math.floorDiv(x - radius, CELL); cell <= Math.floorDiv(x + radius, CELL); cell++) {
                AnomalySite site = cellSite(seed, b, cell);
                if (site == null) {
                    site = Level2Oddities.site(seed, b, cell);
                }
                if (site != null && site.near(x, z, radius)) {
                    out.accept(site);
                }
            }
        }
    }

    /** The nearest site of {@code kind} within about a thousand blocks, or null (for /backrooms anomaly locate). */
    @Nullable
    static AnomalySite nearest(long seed, int x, int z, String kind) {
        if (!Level2Sites.KINDS.contains(kind)) {
            return null;
        }
        int band = Level2Line.band(z);
        int cell = Math.floorDiv(x, CELL);
        AnomalySite best = null;
        double bestDist = Double.MAX_VALUE;
        for (int b = band - 10; b <= band + 10; b++) {
            for (int c = cell - 6; c <= cell + 6; c++) {
                AnomalySite site = cellSite(seed, b, c);
                if (site == null) {
                    site = Level2Oddities.site(seed, b, c);
                }
                if (site != null && site.kind().equals(kind)) {
                    double d = site.distanceTo(x, z);
                    if (d < bestDist) {
                        bestDist = d;
                        best = site;
                    }
                }
            }
        }
        return best;
    }

    // ==========================================================================================
    // Remembered sites
    // ==========================================================================================

    private static final int MEMO = 64;

    private static final class SiteMemo {
        final long[] seed = new long[MEMO];
        final int[] band = new int[MEMO];
        final int[] cell = new int[MEMO];
        final boolean[] filled = new boolean[MEMO];
        final AnomalySite[] site = new AnomalySite[MEMO];
    }

    private static final ThreadLocal<SiteMemo> SITES = ThreadLocal.withInitial(SiteMemo::new);

    /** The brownout of stretch {@code cell} of band {@code band}, or null. Remembered per thread. */
    @Nullable
    static AnomalySite cellSite(long seed, int band, int cell) {
        SiteMemo memo = SITES.get();
        int i = (int) (Hash.mix(band * 0x632BE59BD9B4E019L + cell) & (MEMO - 1));
        if (memo.filled[i] && memo.seed[i] == seed && memo.band[i] == band && memo.cell[i] == cell) {
            return memo.site[i];
        }
        AnomalySite site = ((band + cell) & 1) == 0 ? planBrownout(seed, band, cell) : null;
        memo.seed[i] = seed;
        memo.band[i] = band;
        memo.cell[i] = cell;
        memo.site[i] = site;
        memo.filled[i] = true;
        return site;
    }

    /** 30-60 blocks of the corridor itself (its full width), clear of the exits. */
    @Nullable
    private static AnomalySite planBrownout(long seed, int band, int cell) {
        long h = Hash.of(seed, band, cell, 900);
        if (Hash.unit(h) >= BROWNOUT_CHANCE) {
            return null;
        }
        int base = cell * CELL;
        int c = Level2Line.centre(seed, band);
        // A few tries at different spots; they only fail near an exit.
        for (int k = 1; k <= 6; k++) {
            long hash = Hash.mix(h + k * 0x9E3779B97F4A7C15L);
            int length = 30 + Hash.range(hash, 31);
            int x0 = base + 16 + Hash.range(Hash.mix(hash ^ 1), CELL - 32 - length + 1);
            int x1 = x0 + length - 1;
            boolean clear = true;
            for (int x = x0; x <= x1 + 8 && clear; x += 8) {
                clear = !Level2Line.exitNear(seed, band, Math.min(x, x1), EXIT_GAP);
            }
            if (clear) {
                return new AnomalySite(Level2Sites.BROWNOUT, x0, c - Level2Line.WALL, x1, c + Level2Line.WALL,
                        (x0 + x1) >> 1, c, 0, hash);
            }
        }
        return null;
    }

    // ==========================================================================================
    // Lamps and brownouts
    // ==========================================================================================

    /** In a brownout: does the light panel at (x, z) still work? */
    static boolean lampSurvives(long seed, int x, int z) {
        return Hash.unit(seed, x, z, 920) < BROWNOUT_LAMP_SURVIVES;
    }

    /** In a brownout: is the emergency-light spot at (x, z) fitted? */
    static boolean emergencyLight(long seed, int x, int z) {
        return Hash.unit(seed, x, z, 921) < EMERGENCY_LIGHT_CHANCE;
    }

    /**
     * Where a brownout's burst pipe is in the 12-block stretch around x (x of the burst), or
     * MIN_VALUE if that stretch has none. The ooze pools on the lane's floor around it.
     */
    static int burstX(long seed, AnomalySite site, int x) {
        int stretch = Math.floorDiv(x - site.minX(), BURST_STRETCH);
        long h = Hash.of(seed, site.minX(), stretch, 925);
        if (Hash.unit(h) >= BURST_CHANCE) {
            return Integer.MIN_VALUE;
        }
        int bx = site.minX() + stretch * BURST_STRETCH + 2 + Hash.range(Hash.mix(h), BURST_STRETCH - 4);
        return bx <= site.maxX() ? bx : Integer.MIN_VALUE;
    }
}
