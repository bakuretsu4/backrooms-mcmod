package com.rickr.backrooms.entity;

import net.minecraft.util.Mth;

/**
 * The Bacteria's private clocks, shared by the model (which poses it) and the entity (its stride
 * rhythm). Pure arithmetic: the same entity id and time always give the same answer, on every
 * client, and nothing here creates objects.
 *
 * <p>A "slot" is a stretch of about {@code period} ticks whose start is moved by up to
 * {@code jitter} ticks either way, so the rhythm is never regular. Something happens at the start
 * of each slot (a new head direction, an arm re-planting...).
 */
public final class BacteriaRhythm {
    // Running, the stride clock is never a metronome (after Kane Pixels' runner, where no pose
    // ever comes round twice): its rate wanders by up to RUN_RATE_SLOW (a slow swell, period
    // RUN_RATE_SLOW_TICKS) plus RUN_RATE_FAST (quicker, RUN_RATE_FAST_TICKS), so one footfall
    // comes anything from about 0.6 to 1.6 times its usual time after the last (the gaps between
    // footfalls vary by about 16 % on average; the study asks for 15-25 %).
    private static final float RUN_RATE_SLOW = 0.30f;
    private static final float RUN_RATE_SLOW_TICKS = 9.0f;
    private static final float RUN_RATE_FAST = 0.15f;
    private static final float RUN_RATE_FAST_TICKS = 3.5f;
    /**
     * Every 40-90 ticks while it runs (STUMBLE_PERIOD +- STUMBLE_JITTER) something breaks its
     * stride: a stumble (STUMBLE_TICKS: one stride 35 % longer, the next 40 % shorter) or, one
     * time in three, a lurch (LURCH_TICKS: a burst of quick steps). The entity's stride clock and
     * the model's pose both read these, so the legs and the body stumble together.
     */
    public static final float STUMBLE_PERIOD = 65.0f;
    public static final float STUMBLE_JITTER = 25.0f;
    public static final float STUMBLE_TICKS = 7.0f;
    public static final float LURCH_TICKS = 3.0f;
    private static final float LURCH_CHANCE = 1.0f / 3.0f;
    private static final float STUMBLE_LONG = 1.0f / 1.35f - 1.0f;  // the long stride: steps this much slower
    private static final float STUMBLE_SHORT = 1.0f / 0.6f - 1.0f;  // the short one: this much quicker
    private static final float LURCH_QUICK = 0.4f;
    private static final int RATE_SEED = 701;
    private static final int STUMBLE_SEED = 710;

    /** Every Bacteria's own seed, from its entity id. */
    public static int seed(int entityId) {
        return entityId * 0x632BE5AB + 0x1B873593;
    }

    /** A fixed random number from -1 to 1 for (seed, n). */
    public static float hash(int seed, int n) {
        int h = seed * 0x9E3779B1 + n * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 32767.5f - 1.0f;
    }

    /** The same, from 0 to 1. */
    public static float unit(int seed, int n) {
        return 0.5f + 0.5f * hash(seed, n);
    }

    /** When slot {@code b} starts. */
    public static float slotStart(int seed, int b, float period, float jitter) {
        return b * period + jitter * hash(seed ^ 0x5BD1E995, b);
    }

    /** Which slot time {@code t} is in (jitter must be under half the period). */
    public static int slot(int seed, float t, float period, float jitter) {
        int b = Mth.floor(t / period);
        if (t < slotStart(seed, b, period, jitter)) {
            return b - 1;
        }
        return t >= slotStart(seed, b + 1, period, jitter) ? b + 1 : b;
    }

    /**
     * Smooth noise from about -1 to 1: a new random value every {@code period} ticks, with a
     * curve through them that keeps gliding (it never stops at one and sets off again).
     */
    public static float noise(int seed, float t, float period) {
        float x = t / period;
        int b = Mth.floor(x);
        float u = x - b;
        float y0 = hash(seed, b - 1);
        float y1 = hash(seed, b);
        float y2 = hash(seed, b + 1);
        float y3 = hash(seed, b + 2);
        // A Catmull-Rom curve through the four nearest values.
        return 0.5f * (2.0f * y1 + (y2 - y0) * u + (2.0f * y0 - 5.0f * y1 + 4.0f * y2 - y3) * u * u
                + (3.0f * y1 - y0 - 3.0f * y2 + y3) * u * u * u);
    }

    /** Ticks since the latest stumble or lurch began, at time {@code t} (its tickCount, plus the partial tick in the model). */
    public static float stumbleAge(int seed, float t) {
        int b = slot(seed + STUMBLE_SEED, t, STUMBLE_PERIOD, STUMBLE_JITTER);
        return t - slotStart(seed + STUMBLE_SEED, b, STUMBLE_PERIOD, STUMBLE_JITTER);
    }

    /** True if the latest one (at time {@code t}) is a lurch rather than a stumble. */
    public static boolean isLurch(int seed, float t) {
        int b = slot(seed + STUMBLE_SEED, t, STUMBLE_PERIOD, STUMBLE_JITTER);
        return unit(seed + STUMBLE_SEED + 1, b) < LURCH_CHANCE;
    }

    /** -1 or 1: the side the latest stumble throws it to (the long stride's side). */
    public static float stumbleSide(int seed, float t) {
        int b = slot(seed + STUMBLE_SEED, t, STUMBLE_PERIOD, STUMBLE_JITTER);
        return hash(seed + STUMBLE_SEED + 2, b) > 0.0f ? 1.0f : -1.0f;
    }

    /**
     * Running: how much faster (more than 1) or slower than usual the stride clock runs at time
     * {@code t}. The stride length is divided by the same number, so the feet still travel exactly
     * as fast as the body and stay planted: a quick stride is a short one.
     */
    public static float runRate(int seed, float t) {
        float rate = 1.0f + RUN_RATE_SLOW * noise(seed + RATE_SEED, t, RUN_RATE_SLOW_TICKS)
                + RUN_RATE_FAST * noise(seed + RATE_SEED + 1, t, RUN_RATE_FAST_TICKS);
        float a = stumbleAge(seed, t);
        if (isLurch(seed, t)) {
            if (a < LURCH_TICKS) {
                rate *= 1.0f + LURCH_QUICK * Mth.sin(Mth.PI * a / LURCH_TICKS);
            }
        } else if (a < STUMBLE_TICKS) {
            // First half: the long stride (slower); second half: the short one (quicker).
            float half = STUMBLE_TICKS / 2.0f;
            rate *= a < half ? 1.0f + STUMBLE_LONG * Mth.sin(Mth.PI * a / half)
                    : 1.0f + STUMBLE_SHORT * Mth.sin(Mth.PI * (a - half) / half);
        }
        return rate;
    }

    private BacteriaRhythm() {}
}
