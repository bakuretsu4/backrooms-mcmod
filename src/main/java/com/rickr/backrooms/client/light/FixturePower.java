package com.rickr.backrooms.client.light;

import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.network.LevelEventPayload;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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
 * <p>And Level 2's caged lamps hang on series circuits that fail now and then, every lamp of a
 * circuit at once ({@link #level2CircuitOn}).
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
        if (light.type == FixtureType.EXIT_SIGN || light.type == FixtureType.L0_EXIT_SIGN || light.type == FixtureType.SAFE_LAMP
                || light.type == FixtureType.L2_EMERGENCY_LIGHT) {
            // Battery-backed: stays lit even in a blackout (and through alarms, flickers and lamp
            // waves). Exit signs are a little clue in the dark; safe-room lamps a place to hide.
            return 1.0f;
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

    // --- Level 2 series circuits -----------------------------------------------------------------
    // Level 2's lamps are wired in series (https://backrooms-wiki.wikidot.com/level-2): when one
    // part of a circuit fails, every lamp on it goes out at once, roughly a 3x3 to 6x6 grid of
    // lamps, and nobody can tell which lamps share a circuit. Here a circuit is a patch of floor
    // around one jittered point per 56x56 square (so circuits are irregular, 9-36 lamps), and
    // whether it is up is a pure function of the circuit and the level's game time: every player
    // sees the same circuit die at the same tick, and neighbouring circuits don't care.

    /** One circuit centre per square this big (blocks). */
    private static final int CIRCUIT_GRID = 56;
    /** Each circuit's time is cut into rounds this long (ticks, 2.5 minutes) ... */
    private static final long CIRCUIT_ROUND = 3000L;
    /** ... in each round it fails with this chance (out of 256, so 1 in 4) ... */
    private static final int CIRCUIT_FAIL_CHANCE = 64;
    /** ... and stays out for 20 to 55 seconds (ticks) ... */
    private static final long CIRCUIT_OUT_MIN = 400L;
    private static final long CIRCUIT_OUT_SPREAD = 700L;
    /** ... the last 1.5 s of which its lamps stutter together as the power comes back. */
    private static final long CIRCUIT_RESTRIKE = 30L;
    /** A failure younger than this (ticks) still gets its pop when first seen. */
    private static final long CIRCUIT_POP_WINDOW = 6L;
    /** Failures that already popped (circuit and start mixed), so each pops once. */
    private static final LongOpenHashSet POPPED = new LongOpenHashSet();

    /** The circuit a Level 2 lamp at (x, z) belongs to: the nearest circuit centre. */
    public static long level2Circuit(double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int cx = Math.floorDiv(bx, CIRCUIT_GRID);
        int cz = Math.floorDiv(bz, CIRCUIT_GRID);
        long best = 0L;
        long bestDist = Long.MAX_VALUE;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                long id = mix((((long) (cx + ox)) << 32 ^ ((cz + oz) & 0xFFFFFFFFL)) ^ 0x2F1A7C3B9D5E4F61L);
                long px = (long) (cx + ox) * CIRCUIT_GRID + 8 + (id & 0xFFFF) % (CIRCUIT_GRID - 16);
                long pz = (long) (cz + oz) * CIRCUIT_GRID + 8 + ((id >>> 16) & 0xFFFF) % (CIRCUIT_GRID - 16);
                long dist = (px - bx) * (px - bx) + (pz - bz) * (pz - bz);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = id;
                }
            }
        }
        return best;
    }

    /**
     * The outage of {@code circuit} at {@code gameTime}: -1 when it is up, otherwise the ticks
     * since it failed (high bits) and how long the outage lasts (low 16 bits).
     */
    private static long circuitOutage(long circuit, long gameTime) {
        long t = gameTime + (circuit >>> 20) % CIRCUIT_ROUND; // each circuit has its own round boundaries
        long round = Math.floorDiv(t, CIRCUIT_ROUND);
        long h = mix(circuit + round * 0x9E3779B97F4A7C15L);
        if ((h & 0xFF) >= CIRCUIT_FAIL_CHANCE) {
            return -1L;
        }
        long length = CIRCUIT_OUT_MIN + ((h >>> 8) & 0xFFFF) % CIRCUIT_OUT_SPREAD;
        long start = ((h >>> 24) & 0xFFFF) % (CIRCUIT_ROUND - length);
        long since = t - round * CIRCUIT_ROUND - start;
        return since >= 0 && since < length ? since << 16 | length : -1L;
    }

    /** True unless this Level 2 circuit is out (the stutter as it comes back counts as out). */
    public static boolean level2CircuitPowered(long circuit, long gameTime) {
        return circuitOutage(circuit, gameTime) < 0;
    }

    /** Is this Level 2 circuit powered at this game tick? (Stutters together while it comes back.) */
    public static boolean level2CircuitOn(long circuit, long gameTime) {
        long outage = circuitOutage(circuit, gameTime);
        if (outage < 0) {
            return true;
        }
        if (level2CircuitRestriking(circuit, gameTime)) {
            return (mix(circuit ^ (gameTime / 2) * 0x632BE59BD9B4E019L) & 1L) == 0L;
        }
        return false;
    }

    /** True during the short stutter as a failed circuit's power comes back. */
    public static boolean level2CircuitRestriking(long circuit, long gameTime) {
        long outage = circuitOutage(circuit, gameTime);
        return outage >= 0 && (outage >>> 16) >= (outage & 0xFFFF) - CIRCUIT_RESTRIKE;
    }

    /**
     * True once per failure, for the first lamp of the circuit that asks within the first few
     * ticks: that lamp plays the pop of the circuit going out.
     */
    public static boolean level2CircuitPop(long circuit, long gameTime) {
        long outage = circuitOutage(circuit, gameTime);
        if (outage < 0 || (outage >>> 16) >= CIRCUIT_POP_WINDOW) {
            return false;
        }
        if (POPPED.size() > 256) {
            POPPED.clear();
        }
        return POPPED.add(mix(circuit ^ (gameTime - (outage >>> 16))));
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
