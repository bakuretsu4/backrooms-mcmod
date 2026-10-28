package com.rickr.backrooms.client;

import com.rickr.backrooms.world.layout.Level490Layout;
import java.util.ArrayList;
import java.util.List;

/**
 * What the client knows about Level 490: its layout seed (sent on arrival by world/Level490Sync),
 * and from it the colossal chains around the camera, refreshed as the camera moves.
 */
public final class Level490ClientState {
    /** Chains are looked for this far around the camera (blocks). */
    public static final int CHAIN_SEARCH = 2600;
    /** Rescanned once the camera has moved this far. */
    private static final double RESCAN = 200.0;

    private static volatile long layoutSeed;
    private static volatile boolean hasSeed;
    private static final List<int[]> CHAINS = new ArrayList<>();
    private static double scannedX = Double.NaN;
    private static double scannedZ = Double.NaN;

    /** Network thread: the server told us the layout seed. */
    public static void receiveSeed(long seed) {
        layoutSeed = seed;
        hasSeed = true;
        scannedX = Double.NaN;
    }

    public static boolean hasSeed() {
        return hasSeed;
    }

    public static long seed() {
        return layoutSeed;
    }

    /** The colossal chains (axis {x, z}) around (x, z); render thread only. */
    public static List<int[]> chains(double x, double z) {
        if (hasSeed && (Double.isNaN(scannedX) || Math.hypot(x - scannedX, z - scannedZ) > RESCAN)) {
            CHAINS.clear();
            Level490Layout.chainsNear(layoutSeed, (int) x, (int) z, CHAIN_SEARCH, (cx, cz) -> CHAINS.add(new int[] {cx, cz}));
            scannedX = x;
            scannedZ = z;
        }
        return CHAINS;
    }

    private Level490ClientState() {}
}
