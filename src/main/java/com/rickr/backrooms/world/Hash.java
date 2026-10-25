package com.rickr.backrooms.world;

/**
 * Stateless coordinate hashing. Every layout decision is {@code hash(seed, x, z, salt)}, so any
 * chunk can be generated on its own, in any order, and every player/server agrees on the result.
 * This is what makes the Backrooms endless without storing anything.
 */
public final class Hash {
    public static long of(long seed, int a, int b, int salt) {
        long h = seed ^ 0x9E3779B97F4A7C15L;
        h = mix(h + a * 0x632BE59BD9B4E019L);
        h = mix(h + b * 0x85157AF5L);
        h = mix(h + salt * 0xC2B2AE3D27D4EB4FL);
        return h;
    }

    /** SplitMix64 finalizer. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Uniform double in [0, 1). */
    public static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    public static double unit(long seed, int a, int b, int salt) {
        return unit(of(seed, a, b, salt));
    }

    /** Uniform int in [0, bound). */
    public static int range(long h, int bound) {
        return (int) ((h >>> 33) % bound);
    }

    private Hash() {}
}
