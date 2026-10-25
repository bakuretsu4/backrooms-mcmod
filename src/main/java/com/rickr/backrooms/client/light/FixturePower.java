package com.rickr.backrooms.client.light;

import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.network.LevelEventPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * How floor events change the lights.
 *
 * <ul>
 *   <li>BLACKOUT: every light fades to nothing over ~2 s, and back up over ~2 s when it ends.</li>
 *   <li>FLICKER: each fixture (each whole strip) toggles on and off on its own random rhythm.
 *       The pattern is a pure function of position + clock, so every player sees the same.</li>
 *   <li>ALARM: normal lights dim to 30% while the red beacons spin.</li>
 *   <li>LAMP_WAVE (Level 9): a band of stuttering sweeps across the streetlamps; behind it
 *       some lamps stay dead until shortly before the event ends.</li>
 * </ul>
 *
 * <p>Separately, Level 9 lamps whose block is in mode=flicker are "broken": they stutter on
 * their own irregular rhythm all the time ({@link #brokenLampOn}).
 *
 * <p>The fades are kept as smoothed values stepped by {@link #tick()} (once per client tick), so
 * they work no matter how the server announces the end of an event.
 */
public final class FixturePower {
    private static final float BLACKOUT_FADE_MS = 2000.0f;
    private static final float ALARM_FADE_MS = 600.0f;
    /** How dim normal lights get during the alarm. */
    private static final float ALARM_DIM = 0.3f;

    // --- Level 9 lamp wave ---------------------------------------------------------------------
    /** The wave starts this far behind you (blocks along its direction) ... */
    private static final double WAVE_LEAD = 40.0;
    /** ... and sweeps forward at this speed (blocks per second): it passes you after ~6 s. */
    private static final double WAVE_SPEED = 7.0;
    /** Lamps within this many blocks of the wave front stutter. */
    private static final double WAVE_STUTTER_WIDTH = 5.0;
    /** Out of 256: how many lamps die once the front has passed (~40%). */
    private static final int WAVE_DIE_CHANCE = 100;
    /** Dead lamps come back one by one during the last this-many ms of the event ... */
    private static final long WAVE_RECOVER_SPREAD_MS = 6000L;
    /** ... each stuttering for this long before it holds steady again. */
    private static final long WAVE_RECOVER_STUTTER_MS = 1200L;

    /** The wave event these values belong to (its start time), so a new wave gets a new origin. */
    private static long waveStartMs = Long.MIN_VALUE;
    private static double waveOriginX;
    private static double waveOriginZ;
    private static double waveDirX = 1.0;
    private static double waveDirZ;
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
        if (event == LevelEventPayload.LAMP_WAVE && CameraClientState.eventStartMs != waveStartMs) {
            startWave(CameraClientState.eventStartMs);
        }
    }

    /**
     * A new lamp wave: it starts behind you (where you stood when it began) and runs past you in
     * a random direction, so you watch the stutter roll down the street toward you.
     */
    private static void startWave(long startMs) {
        waveStartMs = startMs;
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        waveOriginX = eye.x;
        waveOriginZ = eye.z;
        double angle = ((mix(startMs) >>> 11) & 0xFFFF) / 65536.0 * Math.PI * 2.0;
        waveDirX = Math.cos(angle);
        waveDirZ = Math.sin(angle);
    }

    /** Forget any fade in progress (new dimension / logout). */
    public static void reset() {
        blackout = activeEvent(System.currentTimeMillis()) == LevelEventPayload.BLACKOUT ? 1.0f : 0.0f;
        alarm = 0.0f;
        lastTickMs = 0;
        waveStartMs = Long.MIN_VALUE;
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

    /** True while Level 9's lamp wave runs. */
    public static boolean lampWave(long nowMs) {
        return activeEvent(nowMs) == LevelEventPayload.LAMP_WAVE;
    }

    /**
     * During LAMP_WAVE: is this lamp on right now? Ahead of the front: steady. At the front:
     * stuttering. Behind it: ~40% stay dead, then each comes back (with a short stutter) at its
     * own moment in the last few seconds of the event.
     */
    public static boolean waveOn(FixtureLight light, long nowMs) {
        if (waveStartMs == Long.MIN_VALUE) {
            return true;
        }
        // Where the lamp lies along the wave's direction, and where the front is now.
        double along = (light.x - waveOriginX) * waveDirX + (light.z - waveOriginZ) * waveDirZ;
        double front = -WAVE_LEAD + WAVE_SPEED * (nowMs - waveStartMs) / 1000.0;
        if (front < along - WAVE_STUTTER_WIDTH) {
            return true; // not reached yet
        }
        if (front <= along + WAVE_STUTTER_WIDTH) {
            return stutter(light.key ^ waveStartMs, nowMs);
        }
        long seed = mix(light.key ^ waveStartMs ^ 0x2545F4914F6CDD1DL);
        if ((seed & 0xFF) >= WAVE_DIE_CHANCE) {
            return true; // survived the wave
        }
        long recoverAt = CameraClientState.eventEndMs - 300L - ((seed >>> 8) & 0xFFFF) % WAVE_RECOVER_SPREAD_MS;
        if (nowMs >= recoverAt) {
            return true;
        }
        if (nowMs >= recoverAt - WAVE_RECOVER_STUTTER_MS) {
            return stutter(seed, nowMs); // trying to strike again
        }
        return false;
    }

    /**
     * A broken (mode=flicker) Level 9 lamp, all the time. Its life is cut into "episodes" of
     * 2.5-6.5 s (each lamp its own length and offset, so no two lamps sync up). In each episode
     * the lamp is, by chance: steady (~55%), steady with one stutter burst of 0.4-1.4 s (~25%),
     * or dark with the odd short flash as it tries to strike (~20%). Pure function of position +
     * clock, like {@link #flickerOn}.
     */
    public static boolean brokenLampOn(long key, long nowMs) {
        long seed = mix(key ^ 0x6A09E667F3BCC909L);
        long episodeMs = 2500L + (seed & 0xFFFF) % 4000L;
        long t = nowMs + ((seed >>> 16) & 0xFFFFFF) % episodeMs;
        long episode = t / episodeMs;
        long within = t % episodeMs;
        long h = mix(seed + episode * 0x9E3779B97F4A7C15L);
        int roll = (int) (h & 0xFF);
        if (roll < 140) {
            return true;
        }
        if (roll < 205) {
            long burstStart = ((h >>> 8) & 0xFFFF) % (episodeMs / 2);
            long burstLength = 400L + ((h >>> 24) & 0x3FF);
            if (within < burstStart || within > burstStart + burstLength) {
                return true;
            }
            return (mix(h ^ (within / 70L)) & 1L) == 0L; // fast on/off, about half the time on
        }
        // Dark episode: one 90 ms slot in sixteen flashes on.
        return (mix(h ^ (within / 90L)) & 0xFL) == 0L;
    }

    /** Fast irregular on/off (a lamp at the wave front): toggles every 1-3 ticks, on ~55%. */
    private static boolean stutter(long seedKey, long nowMs) {
        long seed = mix(seedKey ^ 0x5DEECE66DL);
        long period = 1 + ((seed >>> 8) & 0xFFFF) % 3;
        long bucket = (nowMs / 50) / period;
        return (mix(seed + bucket * 0x9E3779B97F4A7C15L) & 0xFF) >= 115;
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
