package com.rickr.backrooms.client;

/**
 * What the client knows about Level 2: its layout seed.
 *
 * <p>The server sends it on arrival ({@code world/Level39Sync}, Level2StatePayload); with it the
 * client can ask the same pure layout questions the world generator does (how strongly the
 * corridor twists here, which way it banks, where the lamps sit round it: world/layout/Level2Twist).
 * Until it arrives {@link #hasSeed()} is false and the spin illusion runs at its plain-corridor
 * baseline (client/anomaly/level2/Level2Spin).
 */
public final class Level2ClientState {
    private static volatile long layoutSeed;
    private static volatile boolean hasSeed;

    /** Network thread: the server told us the layout seed. */
    public static void receiveSeed(long seed) {
        layoutSeed = seed;
        hasSeed = true;
    }

    public static boolean hasSeed() {
        return hasSeed;
    }

    public static long seed() {
        return layoutSeed;
    }

    private Level2ClientState() {}
}
