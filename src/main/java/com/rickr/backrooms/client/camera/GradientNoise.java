package com.rickr.backrooms.client.camera;

/**
 * Tiny 1D Perlin-style gradient noise for the handheld camera shake.
 *
 * <p>Unlike {@code Math.sin}, gradient noise never repeats and wanders smoothly, so the shake looks
 * like a person's hands instead of a machine. Each whole number gets a pseudo-random slope; between
 * whole numbers we blend the two slopes with a smooth curve. Different {@code channel} values give
 * independent curves (one for pitch, one for yaw, one for roll).
 */
public final class GradientNoise {
    /**
     * @param channel which independent curve to read
     * @param x       position along the curve; moving x by about 1 gives one gentle wiggle
     * @return a smooth value roughly in -1..1
     */
    public static float sample(int channel, float x) {
        int i = (int) Math.floor(x);
        float f = x - i;
        float a = gradient(channel, i) * f;
        float b = gradient(channel, i + 1) * (f - 1.0f);
        // Quintic fade (6f^5 - 15f^4 + 10f^3): no visible kinks where segments meet.
        float fade = f * f * f * (f * (f * 6.0f - 15.0f) + 10.0f);
        // Raw 1D gradient noise peaks around +/-0.5, so double it.
        return (a + (b - a) * fade) * 2.0f;
    }

    /** A repeatable pseudo-random slope in -1..1 for one lattice point. */
    private static float gradient(int channel, int i) {
        int h = i * 0x27D4EB2D + channel * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return ((h & 0xFFFF) / 32767.5f) - 1.0f;
    }

    private GradientNoise() {}
}
