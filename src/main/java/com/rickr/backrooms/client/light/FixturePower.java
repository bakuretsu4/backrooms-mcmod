package com.rickr.backrooms.client.light;

import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.network.LevelEventPayload;

/**
 * How floor events change the lights.
 *
 * <ul>
 *   <li>BLACKOUT: every light fades to nothing over ~2 s, and back up over ~2 s when it ends.</li>
 *   <li>FLICKER: each fixture (each whole strip) toggles on and off on its own random rhythm.
 *       The pattern is a pure function of position + clock, so every player sees the same.</li>
 *   <li>ALARM: normal lights dim to 30% while the red beacons spin.</li>
 * </ul>
 *
 * <p>The fades are kept as smoothed values stepped by {@link #tick()} (once per client tick), so
 * they work no matter how the server announces the end of an event.
 */
public final class FixturePower {
    private static final float BLACKOUT_FADE_MS = 2000.0f;
    private static final float ALARM_FADE_MS = 600.0f;
    /** How dim normal lights get during the alarm. */
    private static final float ALARM_DIM = 0.3f;

    /** 0 = lights fully on, 1 = full blackout (linear; smoothed on output). */
    private static float blackout;
    /** 0 = no alarm, 1 = alarm in full swing. */
    private static float alarm;
    private static long lastTickMs;

    /** The event that is running right now, or NONE. */
    public static int activeEvent(long nowMs) {
        return nowMs < CameraClientState.eventEndMs ? CameraClientState.eventKind : LevelEventPayload.NONE;
    }

    /** Steps the fades toward the current event. Call once per client tick. */
    public static void tick() {
        long now = System.currentTimeMillis();
        float dt = lastTickMs == 0 ? 50.0f : Math.min(now - lastTickMs, 1000L);
        lastTickMs = now;
        int event = activeEvent(now);
        blackout = approach(blackout, event == LevelEventPayload.BLACKOUT ? 1.0f : 0.0f, dt / BLACKOUT_FADE_MS);
        alarm = approach(alarm, event == LevelEventPayload.ALARM ? 1.0f : 0.0f, dt / ALARM_FADE_MS);
    }

    /** Forget any fade in progress (new dimension / logout). */
    public static void reset() {
        blackout = activeEvent(System.currentTimeMillis()) == LevelEventPayload.BLACKOUT ? 1.0f : 0.0f;
        alarm = 0.0f;
        lastTickMs = 0;
    }

    /** 1 = power on, 0 = full blackout. */
    public static float mainsPower() {
        return 1.0f - smooth(blackout);
    }

    /** 0..1 how strongly the alarm is running (red beacons, dimmed lights). */
    public static float alarm() {
        return smooth(alarm);
    }

    /** Brightness multiplier for normal lights from the alarm (1 = no alarm). */
    public static float alarmDim() {
        return 1.0f - (1.0f - ALARM_DIM) * alarm();
    }

    /** True while a FLICKER event runs. */
    public static boolean flickering(long nowMs) {
        return activeEvent(nowMs) == LevelEventPayload.FLICKER;
    }

    /**
     * During FLICKER: is this fixture on right now? A quarter of fixtures hold steady; the rest
     * toggle every 1-7 ticks (each its own rhythm) and are on ~60% of the time. Keyed by the
     * strip's head block, so a whole strip flickers together.
     */
    public static boolean flickerOn(long key, long nowMs) {
        long seed = mix(key ^ 0x5DEECE66DL);
        if ((seed & 0xFF) < 64) {
            return true;
        }
        long period = 1 + ((seed >>> 8) & 0xFFFF) % 7;
        long bucket = (nowMs / 50) / period;
        long h = mix(seed + bucket * 0x9E3779B97F4A7C15L);
        return (h & 0xFF) >= 100;
    }

    /** The 0..1 power of one fixture right now (before the camcorder battery). */
    public static float levelOf(FixtureLight light) {
        if (light.type == FixtureType.EXIT_SIGN) {
            return 1.0f; // battery-backed: stays lit even in a blackout, a little clue in the dark
        }
        float power = mainsPower();
        if (light.type == FixtureType.RED_ALARM_LAMP) {
            return power;
        }
        if (!light.on) {
            return 0.0f;
        }
        return power * alarmDim();
    }

    /** SplitMix64 finaliser: a cheap, well-mixed hash. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    private static float smooth(float t) {
        float c = Math.max(0.0f, Math.min(1.0f, t));
        return c * c * (3.0f - 2.0f * c);
    }

    private FixturePower() {}
}
