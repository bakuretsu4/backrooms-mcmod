package com.rickr.backrooms.client.light;

import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.network.LevelEventPayload;
import com.rickr.backrooms.world.layout.LevelLayout;
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
 * <p>On Level 0 a few tubes are faulty all the time, chosen by position ({@link #level0Fault}):
 * most stutter in a short burst now and then ({@link #faultyTubeOn}), some have a lazy starter
 * that cuts out and re-strikes ({@link #restartTubeOn}). The layout makes a few tubes dying (dim,
 * slowly pulsing, {@link #dyingTubePulse}) and gives every tube an age (block/Level0TubeGlow);
 * each also gets a small brightness jitter ({@link #tubeBrightness}).
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

    // --- Level 0 tubes -------------------------------------------------------------------------
    // Real office ceilings are never perfectly even: every tube has aged differently, and a few
    // are failing. A tube's age (how bright it is) is part of its block (block/Level0TubeGlow), so
    // it also works without Veil. Which tubes are faulty, and when they misbehave, is a pure
    // function of the fixture's position (its key) and the clock, so it needs no block change, no
    // world seed and no network message, and every player sees the same tubes misbehave at the
    // same moment.

    /** {@link #level0Fault}: a healthy tube. */
    public static final int TUBE_OK = 0;
    /** {@link #level0Fault}: a faulty tube that stutters off in a short burst now and then. */
    public static final int TUBE_STUTTER = 1;
    /** A dying tube (its block's GLOW is dying): only glows dimly, pulsing slowly. Never toggles. */
    public static final int TUBE_DYING = 2;
    /**
     * {@link #level0Fault}: a tube with a lazy starter. Now and then it drops out, stays dark for
     * a few seconds, then tries to strike again with a few quick flashes before it holds.
     */
    public static final int TUBE_RESTART = 3;
    /**
     * Out of 1024: how many lit Level 0 tubes are faulty (about 4.5%; with the ~3% dying tubes
     * that the layout picks this keeps misbehaving tubes rare: one or two in a typical view) ...
     */
    private static final int FAULTY_TUBES = 46;
    /** ... and how many of those (out of 1024 of the faulty ones) have a lazy starter instead of stuttering (about 1 in 3). */
    private static final int RESTART_SHARE = 340;
    /**
     * A stuttering tube has one "episode" this long: 12 to 45 seconds, its own length per tube,
     * so it is steady most of the time and you are never sure when it goes next ...
     */
    private static final long STUTTER_EPISODE_MIN_MS = 12000L;
    private static final long STUTTER_EPISODE_SPREAD_MS = 33000L;
    /** ... with one burst of stuttering in it, 0.2 to 1.4 seconds long. */
    private static final long STUTTER_BURST_MIN_MS = 200L;
    private static final long STUTTER_BURST_SPREAD_MS = 1200L;
    /** A lazy-starter tube runs through one cycle this long: 40 to 90 seconds ... */
    private static final long RESTART_CYCLE_MIN_MS = 40000L;
    private static final long RESTART_CYCLE_SPREAD_MS = 50000L;
    /** ... in which it is out once, for 1.5 to 5 seconds ... */
    private static final long RESTART_OUT_MIN_MS = 1500L;
    private static final long RESTART_OUT_SPREAD_MS = 3500L;
    /** ... then tries 2 to 5 times to strike, one try every this long, each a brief flash. */
    private static final long RESTART_STRIKE_MS = 260L;
    private static final long RESTART_FLASH_MIN_MS = 60L;
    /** A dying tube's Veil light swings between these shares of its (already dim) glow, slowly. */
    private static final float DYING_PULSE_LOW = 0.75f;
    private static final float DYING_PULSE_HIGH = 1.35f;
    /**
     * On top of its age (Level0TubeGlow.Glow), each tube's Veil light lies within this much of
     * normal (0.05 = plus or minus 5%): no two tubes of the same age look exactly alike.
     */
    private static final float TUBE_BRIGHTNESS_SPREAD = 0.05f;

    /**
     * Is this Level 0 tube healthy ({@link #TUBE_OK}), stuttering ({@link #TUBE_STUTTER}) or one
     * with a lazy starter ({@link #TUBE_RESTART})? Decided by its position only. Dying tubes are
     * decided by the layout (their block), not here. BackroomsLights decides which fixtures may
     * be faulty at all (never exit or safe-room lamps).
     */
    public static int level0Fault(long key) {
        long h = mix(key ^ 0x3C6EF372FE94F82BL);
        if ((h & 0x3FF) >= FAULTY_TUBES) {
            return TUBE_OK;
        }
        return ((h >>> 10) & 0x3FF) < RESTART_SHARE ? TUBE_RESTART : TUBE_STUTTER;
    }

    /**
     * A stuttering Level 0 tube: on, except for one short burst in every episode. Each tube has
     * its own episode length (12-45 s) and offset, so no two tubes stutter in step. In a burst
     * (0.2-1.4 s, different each time) the tube clicks on and off every 60-140 ms, on a little
     * less than half of the time, like a starter that can't strike the tube.
     */
    public static boolean faultyTubeOn(long key, long nowMs) {
        long seed = mix(key ^ 0x7F4A7C159E3779B9L);
        long episodeMs = STUTTER_EPISODE_MIN_MS + (seed & 0xFFFF) % STUTTER_EPISODE_SPREAD_MS;
        long t = nowMs + ((seed >>> 16) & 0xFFFFFF) % episodeMs;
        long episode = t / episodeMs;
        long within = t % episodeMs;
        long h = mix(seed + episode * 0x9E3779B97F4A7C15L);
        long burstLength = STUTTER_BURST_MIN_MS + (h & 0xFFFF) % STUTTER_BURST_SPREAD_MS;
        long burstStart = ((h >>> 16) & 0xFFFF) % (episodeMs - burstLength);
        if (within < burstStart || within >= burstStart + burstLength) {
            return true;
        }
        long slotMs = 60L + ((seed >>> 40) & 0xFF) % 81L;
        return (mix(h ^ ((within - burstStart) / slotMs)) & 0xFF) < 110;
    }

    /**
     * A Level 0 tube with a lazy starter: on, except once per cycle (40-90 s, its own length per
     * tube) it cuts out, stays dark for 1.5-5 s, and then the starter tries again: 2-5 brief
     * flashes about a quarter of a second apart, after which the tube holds. Like
     * {@link #faultyTubeOn}, a pure function of position and clock.
     */
    public static boolean restartTubeOn(long key, long nowMs) {
        long seed = mix(key ^ 0x2B992DDFA23249D6L);
        long cycleMs = RESTART_CYCLE_MIN_MS + (seed & 0xFFFF) % RESTART_CYCLE_SPREAD_MS;
        long t = nowMs + ((seed >>> 16) & 0xFFFFFF) % cycleMs;
        long cycle = t / cycleMs;
        long within = t % cycleMs;
        long h = mix(seed + cycle * 0x9E3779B97F4A7C15L);
        long outMs = RESTART_OUT_MIN_MS + (h & 0xFFFF) % RESTART_OUT_SPREAD_MS;
        long strikes = 2 + ((h >>> 16) & 0xFF) % 4;
        long length = outMs + strikes * RESTART_STRIKE_MS;
        long start = ((h >>> 24) & 0xFFFF) % (cycleMs - length);
        long since = within - start;
        if (since < 0 || since >= length) {
            return true;
        }
        if (since < outMs) {
            return false; // out
        }
        // Striking: a flash at the start of each try, 60-123 ms long (its own length per cycle).
        long flashMs = RESTART_FLASH_MIN_MS + ((h >>> 40) & 0x3F);
        return (since - outMs) % RESTART_STRIKE_MS < flashMs;
    }

    /**
     * A dying Level 0 tube's slow pulse, a factor on its (already dim) Veil light: it swells and
     * fades slowly (each tube its own 3-7 s rhythm), so it reads as an old tube on its way out
     * rather than a broken one.
     */
    public static float dyingTubePulse(long key, long nowMs) {
        long seed = mix(key ^ 0x1B873593CC9E2D51L);
        double periodMs = 3000.0 + (seed & 0xFFFF) % 4000L;
        double phase = ((seed >>> 16) & 0xFFFF) / 65536.0;
        double wave = 0.5 + 0.5 * Math.sin((nowMs / periodMs + phase) * Math.PI * 2.0);
        // Squared, so it lingers at the dim end and only briefly swells.
        return DYING_PULSE_LOW + (DYING_PULSE_HIGH - DYING_PULSE_LOW) * (float) (wave * wave);
    }

    /** This Level 0 tube's own small brightness factor, 0.95-1.05, on top of its age. */
    public static float tubeBrightness(long key) {
        long h = mix(key ^ 0x2545F4914F6CDD1DL);
        float u = (h & 0xFFFF) / 65535.0f;
        return 1.0f + TUBE_BRIGHTNESS_SPREAD * (2.0f * u - 1.0f);
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
    // part of a circuit fails, every lamp on it goes out at once, and nobody can tell which lamps
    // share a circuit. Level 2 is one long straight corridor along X in every 48-block band of Z
    // (world/layout/Level2Layout), so a circuit is a SECTION of that corridor: the stretch between
    // two breaks, 20 to 76 blocks long (about 3 to 12 lamps). When it fails, that whole stretch of
    // the line goes dark at once and a gap opens in the row of pools receding into the distance.
    //
    // Now and then a fault runs down the line instead: the three sections on one "feeder" go out
    // one after another, half a second to a second apart, all in the same direction (toward you
    // or away from you), and come back together. Looking down the corridor you see the pools
    // drop out section by section.
    //
    // Everything is a pure function of the section and the level's game time: every player sees
    // the same section die at the same tick, and it needs no world seed (the client has none).

    /** Level 2's corridor spacing along Z (Level2Layout's band): one corridor, so one row of circuits, per band. */
    private static final int CIRCUIT_BAND = 48;
    /** Sections are this long on average (blocks along X) ... */
    private static final int CIRCUIT_SECTION = 48;
    /** ... with each break moved up to this far either way (keep it under half a section). */
    private static final int CIRCUIT_JITTER = 14;
    /** Each section's time is cut into rounds this long (ticks, 2.5 minutes) ... */
    private static final long CIRCUIT_ROUND = 3000L;
    /** ... in each round it fails with this chance (out of 256, about 1 in 5) ... */
    private static final int CIRCUIT_FAIL_CHANCE = 52;
    /** ... and stays out for 20 to 55 seconds (ticks) ... */
    private static final long CIRCUIT_OUT_MIN = 400L;
    private static final long CIRCUIT_OUT_SPREAD = 700L;
    /** ... the last 1.5 s of which its lamps stutter together as the power comes back. */
    private static final long CIRCUIT_RESTRIKE = 30L;
    /** Sections on one feeder (neighbours along the corridor). */
    private static final int FEEDER_SECTIONS = 3;
    /** Each feeder's time is cut into rounds this long (ticks, 4 minutes) ... */
    private static final long FEEDER_ROUND = 4800L;
    /** ... in each round its fault runs down the line with this chance (out of 256, about 1 in 6) ... */
    private static final int FEEDER_FAIL_CHANCE = 40;
    /** ... one section after the other, 10 to 24 ticks apart ... */
    private static final long FEEDER_STEP_MIN = 10L;
    private static final long FEEDER_STEP_SPREAD = 15L;
    /** ... and the whole feeder is out for 15 to 40 seconds (ticks). */
    private static final long FEEDER_OUT_MIN = 300L;
    private static final long FEEDER_OUT_SPREAD = 500L;
    /** A failure younger than this (ticks) still gets its pop when first seen. */
    private static final long CIRCUIT_POP_WINDOW = 6L;
    /** Failures that already popped (circuit and start mixed), so each pops once. */
    private static final LongOpenHashSet POPPED = new LongOpenHashSet();
    /** Lamps more than this far below the floor are in the void (its doorway lamp), not on a circuit. */
    private static final int CIRCUIT_LOWEST_BELOW_FLOOR = 4;

    /**
     * Is a Level 2 caged lamp at height {@code y} on the series circuits? Yes for the corridor's
     * lamps and the trench lamps just under the floor grating; no for the copy of a lamp by a void
     * door, down in the void: it is the only light there and must never go out.
     */
    public static boolean level2OnCircuit(int y) {
        return y >= LevelLayout.FLOOR_Y - CIRCUIT_LOWEST_BELOW_FLOOR;
    }

    /**
     * The circuit a Level 2 lamp at (x, z) belongs to: its corridor band (high 32 bits) and the
     * number of its section along that corridor (low 32 bits).
     */
    public static long level2Circuit(double x, double z) {
        int bx = (int) Math.floor(x);
        long band = Math.floorDiv((int) Math.floor(z) + CIRCUIT_BAND / 2, CIRCUIT_BAND);
        // Section k runs from break k to break k+1. Break k lies within CIRCUIT_JITTER of
        // k * CIRCUIT_SECTION, so the section is the plain cell or one of its two neighbours.
        int k = Math.floorDiv(bx, CIRCUIT_SECTION);
        if (bx < sectionBreak(band, k)) {
            k--;
        } else if (bx >= sectionBreak(band, k + 1)) {
            k++;
        }
        return band << 32 | (k & 0xFFFFFFFFL);
    }

    /** The x where section k of a band's corridor starts. */
    private static long sectionBreak(long band, int k) {
        long h = mix(band * 0x632BE59BD9B4E019L ^ k * 0x9E3779B97F4A7C15L ^ 0x2F1A7C3B9D5E4F61L);
        return (long) k * CIRCUIT_SECTION + (h >>> 33) % (2 * CIRCUIT_JITTER + 1) - CIRCUIT_JITTER;
    }

    /**
     * The outage of {@code circuit} at {@code gameTime}: -1 when it is up, otherwise the ticks
     * since it failed (high bits) and how long the outage lasts (low 16 bits). A section can be
     * out on its own or because its feeder failed; when both overlap, the one that lasts longer
     * counts (so the lamps stay dark until both are over).
     */
    private static long circuitOutage(long circuit, long gameTime) {
        long own = sectionOutage(circuit, gameTime);
        long feeder = feederOutage(circuit, gameTime);
        if (own < 0 || feeder < 0) {
            return own < 0 ? feeder : own;
        }
        long ownLeft = (own & 0xFFFF) - (own >>> 16);
        long feederLeft = (feeder & 0xFFFF) - (feeder >>> 16);
        return feederLeft > ownLeft ? feeder : own;
    }

    /** A single section failing on its own. */
    private static long sectionOutage(long circuit, long gameTime) {
        long id = mix(circuit ^ 0x51ED270B27D2F4A9L);
        long t = gameTime + (id >>> 20) % CIRCUIT_ROUND; // each section has its own round boundaries
        long round = Math.floorDiv(t, CIRCUIT_ROUND);
        long h = mix(id + round * 0x9E3779B97F4A7C15L);
        if ((h & 0xFF) >= CIRCUIT_FAIL_CHANCE) {
            return -1L;
        }
        long length = CIRCUIT_OUT_MIN + ((h >>> 8) & 0xFFFF) % CIRCUIT_OUT_SPREAD;
        long start = ((h >>> 24) & 0xFFFF) % (CIRCUIT_ROUND - length);
        long since = t - round * CIRCUIT_ROUND - start;
        return since >= 0 && since < length ? since << 16 | length : -1L;
    }

    /** The fault running down this section's feeder, as seen by this section (its own delay). */
    private static long feederOutage(long circuit, long gameTime) {
        long band = circuit >> 32;
        int k = (int) circuit;
        long feeder = mix(band * 0x632BE59BD9B4E019L ^ Math.floorDiv(k, FEEDER_SECTIONS) * 0xD1B54A32D192ED03L ^ 0x7A3C9E1F5B2D4C68L);
        long t = gameTime + (feeder >>> 20) % FEEDER_ROUND;
        long round = Math.floorDiv(t, FEEDER_ROUND);
        long h = mix(feeder + round * 0x9E3779B97F4A7C15L);
        if ((h & 0xFF) >= FEEDER_FAIL_CHANCE) {
            return -1L;
        }
        long length = FEEDER_OUT_MIN + ((h >>> 8) & 0xFFFF) % FEEDER_OUT_SPREAD;
        long step = FEEDER_STEP_MIN + ((h >>> 24) & 0xFF) % FEEDER_STEP_SPREAD;
        // Which way the fault runs: the first section of the feeder goes first, or the last.
        int index = Math.floorMod(k, FEEDER_SECTIONS);
        int order = ((h >>> 32) & 1L) == 0L ? index : FEEDER_SECTIONS - 1 - index;
        long delay = order * step;
        long latest = (FEEDER_SECTIONS - 1) * (FEEDER_STEP_MIN + FEEDER_STEP_SPREAD);
        long start = ((h >>> 40) & 0xFFFF) % (FEEDER_ROUND - length - latest) + delay;
        // Later sections are out for less time, so the whole feeder comes back together.
        long out = length - delay;
        long since = t - round * FEEDER_ROUND - start;
        return since >= 0 && since < out ? since << 16 | out : -1L;
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
     * ticks: that lamp plays the pop of the circuit going out. No pop when the section was
     * already dark (a second fault starting during the first).
     */
    public static boolean level2CircuitPop(long circuit, long gameTime) {
        long outage = circuitOutage(circuit, gameTime);
        if (outage < 0 || (outage >>> 16) >= CIRCUIT_POP_WINDOW) {
            return false;
        }
        long start = gameTime - (outage >>> 16);
        if (circuitOutage(circuit, start - 1) >= 0) {
            return false;
        }
        if (POPPED.size() > 256) {
            POPPED.clear();
        }
        return POPPED.add(mix(circuit ^ start));
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
