package com.rickr.backrooms.entity;

import net.minecraft.util.Mth;

/**
 * The Bacteria's private clocks, shared by the model (which poses it) and the entity (which plays
 * the little clicks at the same moments). Pure arithmetic: the same entity id and time always give
 * the same answer, on every client, and nothing here creates objects.
 *
 * <p>A "slot" is a stretch of about {@code period} ticks whose start is moved by up to
 * {@code jitter} ticks either way, so the rhythm is never regular. Something happens at the start
 * of each slot (a new head direction, a glitch...).
 */
public final class BacteriaRhythm {
    /** The rare glitches while calm (and the re-grips in the cage): one every 3-7 s. */
    public static final float CALM_GLITCH_PERIOD = 100.0f;
    public static final float CALM_GLITCH_JITTER = 40.0f;
    /** While it runs at you: one every 1.5-3 s. */
    public static final float CHASE_GLITCH_PERIOD = 45.0f;
    public static final float CHASE_GLITCH_JITTER = 15.0f;
    /** Which single thing a calm glitch moves. */
    public static final int HEAD_ROLL = 0;
    public static final int CLENCH = 1;
    public static final int SHOULDER = 2;
    public static final int SPINE = 3;
    /** Seed offset of the glitch clock, so it doesn't line up with the other clocks. */
    public static final int GLITCH_SEED = 0x6C1;

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

    /** Which channel calm glitch number {@code b} moves (HEAD_ROLL, CLENCH, SHOULDER or SPINE). */
    public static int glitchChannel(int seed, int b) {
        return Math.min(3, (int) (unit(seed + 200, b) * 4.0f));
    }

    private BacteriaRhythm() {}
}
