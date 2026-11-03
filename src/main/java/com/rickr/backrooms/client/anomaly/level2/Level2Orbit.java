package com.rickr.backrooms.client.anomaly.level2;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.config.ClientSettings;
import com.rickr.backrooms.registry.Level2Audio;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.level2.Level2Sites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The sound of Level 2 slowly turning around you. Nothing here is a literal rotation of the
 * world: a few quiet sounds are simply carried around your head, in step with the level's shared
 * "virtual rotation" ({@link Level2Spin#phase}), so the ear keeps telling you the corridor is
 * rolling while your eyes say it is standing still.
 * <ul>
 *   <li><b>Three loops orbit you</b> on circles a few blocks out, each following the spin at its
 *       own speed and offset, so they never line up into one obvious moving point:
 *       <ul>
 *         <li>the structure's groan, in the corridor's cross-section (up over the ceiling, down
 *             under the floor and back), so it reads as the corridor rolling, not a sound panning;</li>
 *         <li>a faint bracket rattle, faster, on a circle leaning partly along the corridor (the
 *             high sound the ear follows best);</li>
 *         <li>the plant's hum pitched down, slowest, on a flat circle round you (the level
 *             turning under your feet).</li>
 *       </ul>
 *       They grow louder (and the groan strains a little higher) while the spin is fast, sink when
 *       it slows, swell and close in where the spin is strong ({@link Level2Spin#strength}).</li>
 *   <li><b>Rolling creaks</b> every 15-40 s: a long creak of steel (or a pipe straining, or a
 *       run shuddering) that travels 180-360 degrees around your head over 3-6 s, the same way
 *       the level turns, easing in and out like a heavy mass rolling over.</li>
 * </ul>
 * It runs whenever {@link Level2Spin#active()} (from the first tick after you arrive), fades
 * over a second when you die, and stops at once when you leave Level 2 or disconnect. Nothing
 * plays in the void below the floor (it asks for silence).
 *
 * <p>Motion comfort: how far the sounds travel follows {@code ClientSettings.tilt()} (capped at
 * 100%): at 50% they circle half as fast and roll half as far, at 0% they stay where they are
 * (still heard, never moving). Loudness is set in tools/anomaly_assets/level2_audio.py; the
 * volumes below keep everything well under the level's emitters and music.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level2Orbit {
    /** The spin's mean speed: at this rate the loops sit at their "normal" level and pitch. */
    private static final float NOMINAL_RATE = (float) Level2Spin.baseRate();
    /** The spin speed (as a multiple of NOMINAL_RATE) is followed this quickly per tick (~2.5 s) ... */
    private static final float MOTION_EASE = 0.02f;
    /** ... and capped here, so a sudden lurch of the spin can't make the loops jump. */
    private static final float MOTION_CAP = 1.5f;
    /** Fading in or out takes about a second. */
    private static final float FADE_STEP = 1.0f / 20.0f;
    /** Sources sit this much further out where the spin is weak and closer in where it is strong:
     *  radius * (CLOSE_BASE - CLOSE_DEPTH * strength), i.e. x1.08 in a safe room, x0.99 normally, x0.85 at full. */
    private static final float CLOSE_BASE = 1.2f;
    private static final float CLOSE_DEPTH = 0.35f;
    /** If the sound engine refuses to (re)start a loop (master volume at zero, a reload), try again after this long. */
    private static final int RETRY_TICKS = 40;

    // ---- The loops ---------------------------------------------------------------------------
    // Angles grow clockwise as you look along +x, the way the environment turns when the spin's
    // phase grows (the same sense as Level2Twist.bank; Level2Twist.helix counts the other way):
    // 0 = the +z side (right wall), PI/2 = under the floor, PI = the -z side, 3PI/2 = overhead.
    // "tilt" leans a circle's upper half from overhead toward +x (along the corridor):
    // 0 = the corridor's cross-section, 90 = flat round you.
    private static final int GROAN = 0;
    private static final int RATTLE = 1;
    private static final int HUM = 2;
    private static final int LOOP_COUNT = 3;
    private static final float[] LOOP_VOLUME = {0.7f, 0.6f, 0.3f};
    private static final double[] LOOP_RADIUS = {4.5, 3.6, 5.5};
    /** How many times faster than the spin each circle turns, and where on it each one starts. */
    private static final double[] LOOP_SPEED = {1.0, 1.7, 0.6};
    private static final double[] LOOP_OFFSET = {0.0, 2.1, Math.PI};
    private static final double[] LOOP_TILT = {0.0, Math.toRadians(35.0), Math.toRadians(90.0)};
    /** Base pitch; the plant hum is taken down to sound like something far bigger than one plant room. */
    private static final float[] LOOP_PITCH = {1.0f, 1.0f, 0.75f};
    /** How far each one's pitch follows the spin speed (the groan strains as the spin hurries). */
    private static final float[] LOOP_BEND = {0.06f, 0.03f, 0.04f};
    /** How much of each one's volume comes and goes with the spin speed. */
    private static final float[] LOOP_MOTION = {0.35f, 0.4f, 0.2f};

    // ---- Rolling creaks ----------------------------------------------------------------------
    /** One every 15-40 s; the first 8-18 s after arriving. */
    private static final int ROLL_MIN = 300;
    private static final int ROLL_SPREAD = 500;
    private static final int FIRST_ROLL_MIN = 160;
    private static final int FIRST_ROLL_SPREAD = 200;
    /** Carried around you 3.5-5 blocks out (before closing in with the strength) ... */
    private static final double ROLL_RADIUS_MIN = 3.5;
    private static final double ROLL_RADIUS_SPREAD = 1.5;
    /** ... on a circle leaning up to this far off the cross-section, so no two roll the same way. */
    private static final double ROLL_TILT_MAX = Math.toRadians(30.0);
    /** Below this spin speed (x NOMINAL_RATE) the level is barely turning: a roll goes either way. */
    private static final float STILL = 0.15f;

    private static final RandomSource RANDOM = RandomSource.create();

    // What the sounds read every tick (written once per tick here, before the sound engine ticks them).
    private static double eyeX;
    private static double eyeY;
    private static double eyeZ;
    /**
     * The angle the sounds turn by (radians, unbounded): the spin's phase, followed step by step
     * with each step scaled by the comfort setting, so moving the slider never makes the sounds
     * leap (at 100% it is exactly the angle the camera and lamps use).
     */
    private static double phase;
    /** The last raw Level2Spin.phase, and whether there is one yet. */
    private static double lastSpin;
    private static boolean spinKnown;
    /** 0..1: ClientSettings.tilt(), capped. */
    private static float comfort = 1.0f;
    /** 0..1 eased: on (in Level 2, alive, not in the void) or fading. */
    private static float fade;
    /** 0..1: Level2Spin.strength() now. */
    private static float strength;
    /** |spin speed| as a multiple of NOMINAL_RATE, eased, 0..MOTION_CAP. */
    private static float motion = 1.0f;
    /** Which way the level is turning (+1 with the phase growing). */
    private static float turning = 1.0f;

    private static final OrbitLoop[] LOOPS = new OrbitLoop[LOOP_COUNT];
    private static RollingSound rolling;
    private static boolean running;
    private static int ticksToRoll;
    private static int retry;

    /** Runs before the sound engine's own tick, so the sounds move to where you are this tick. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_2) {
            if (running) {
                stopAll(mc.getSoundManager()); // left Level 2: cut at once (the screen is changing anyway)
            }
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        eyeX = eye.x;
        eyeY = eye.y;
        eyeZ = eye.z;
        boolean on = Level2Spin.active() && eye.y > Level2Sites.FLOOR_Y;
        fade = on ? Math.min(1.0f, fade + FADE_STEP) : Math.max(0.0f, fade - FADE_STEP);
        if (!on && fade <= 0.0f) {
            if (running) {
                stopAll(mc.getSoundManager()); // dead (or in the void): faded out, let the instances go
            }
            return;
        }
        if (on) {
            // Kept as it was while fading out: Level2Spin drops it to 0 the moment you die.
            strength = Level2Spin.strength();
        }
        comfort = Mth.clamp(ClientSettings.tilt(), 0.0f, 1.0f);
        double spin = Level2Spin.phase(0.0f);
        if (spinKnown) {
            phase += (spin - lastSpin) * comfort;
        } else {
            phase = spin; // the same angle the camera and lamps use
            spinKnown = true;
        }
        lastSpin = spin;
        float rate = (float) Level2Spin.spinRate(0.0f) / NOMINAL_RATE;
        motion += (Math.min(Math.abs(rate), MOTION_CAP) - motion) * MOTION_EASE;
        if (Math.abs(rate) > STILL) {
            turning = Math.signum(rate);
        }
        if (!on) {
            return; // fading out: the sounds follow `fade` down by themselves
        }
        if (!running) {
            running = true;
            ticksToRoll = FIRST_ROLL_MIN + RANDOM.nextInt(FIRST_ROLL_SPREAD);
        }
        SoundManager sounds = mc.getSoundManager();
        keepLoops(sounds);
        if (--ticksToRoll <= 0) {
            ticksToRoll = ROLL_MIN + RANDOM.nextInt(ROLL_SPREAD);
            if (rolling == null || rolling.isStopped() || !sounds.isActive(rolling)) {
                rolling = startRoll();
                sounds.play(rolling);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        stopAll(Minecraft.getInstance().getSoundManager());
    }

    /** Starts any loop that isn't playing (first tick here, or the engine dropped it). */
    private static void keepLoops(SoundManager sounds) {
        if (retry > 0) {
            retry--;
            return;
        }
        for (int i = 0; i < LOOP_COUNT; i++) {
            OrbitLoop loop = LOOPS[i];
            if (loop == null || loop.isStopped() || !sounds.isActive(loop)) {
                loop = new OrbitLoop(i);
                LOOPS[i] = loop;
                sounds.play(loop);
                // Checked again only after a pause: if the engine won't play it, don't make a new one every tick.
                retry = RETRY_TICKS;
            }
        }
    }

    /** A rolling one-shot: which sound, how far round and how fast are chosen now. */
    private static RollingSound startRoll() {
        int pick = RANDOM.nextInt(20);
        SoundEvent sound;
        float volume;
        float pitch;
        double sweepDeg;
        int ticks;
        if (pick < 12) {
            // The new rolling creak (~5 s): most of a turn.
            sound = Level2Audio.ROLL_CREAK.get();
            volume = 0.75f;
            pitch = 0.9f + RANDOM.nextFloat() * 0.15f;
            sweepDeg = 200.0 + RANDOM.nextDouble() * 160.0;
            ticks = 70 + RANDOM.nextInt(41);
        } else if (pick < 17) {
            // A pipe straining in its hanger, slowed down to sound bigger (~3.2 s): half a turn or a bit more.
            sound = Level2Audio.PIPE_CREAK.get();
            volume = 0.7f;
            pitch = 0.7f + RANDOM.nextFloat() * 0.12f;
            sweepDeg = 180.0 + RANDOM.nextDouble() * 80.0;
            ticks = 60 + RANDOM.nextInt(16);
        } else {
            // A long shudder (~7 s) running all the way round you.
            sound = Level2Audio.PIPE_SHUDDER.get();
            volume = 0.5f;
            pitch = 0.85f + RANDOM.nextFloat() * 0.1f;
            sweepDeg = 270.0 + RANDOM.nextDouble() * 90.0;
            ticks = 100 + RANDOM.nextInt(21);
        }
        float way = motion < STILL ? (RANDOM.nextBoolean() ? 1.0f : -1.0f) : turning;
        double radius = ROLL_RADIUS_MIN + RANDOM.nextDouble() * ROLL_RADIUS_SPREAD;
        double tilt = (RANDOM.nextDouble() * 2.0 - 1.0) * ROLL_TILT_MAX;
        return new RollingSound(sound, volume, pitch, radius, RANDOM.nextDouble() * Mth.TWO_PI,
                way * comfort * Math.toRadians(sweepDeg), ticks, tilt);
    }

    private static void stopAll(SoundManager sounds) {
        for (int i = 0; i < LOOP_COUNT; i++) {
            if (LOOPS[i] != null) {
                LOOPS[i].halt();
                sounds.stop(LOOPS[i]);
                LOOPS[i] = null;
            }
        }
        if (rolling != null) {
            rolling.halt();
            sounds.stop(rolling);
            rolling = null;
        }
        running = false;
        fade = 0.0f;
        motion = 1.0f;
        retry = 0;
        spinKnown = false;
    }

    /** Sources come closer where the spin is strong. */
    private static double close() {
        return CLOSE_BASE - CLOSE_DEPTH * strength;
    }

    /**
     * A sound carried on a circle round your head. Its circle lies across the corridor
     * (+z and up) with the upper half leaning toward +x by {@code tilt}.
     */
    private abstract static class Orbiting extends AbstractTickableSoundInstance {
        private final double upY;
        private final double upX;

        Orbiting(SoundEvent sound, double tilt) {
            super(sound, SoundSource.AMBIENT, RandomSource.create(RANDOM.nextLong()));
            this.upY = Math.cos(tilt);
            this.upX = Math.sin(tilt);
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
            this.delay = 0;
        }

        /** Puts the sound at clockwise angle {@code angle} (0 = +z, PI/2 = underfoot, 3PI/2 = overhead), {@code radius} out. */
        final void place(double angle, double radius) {
            double across = Math.cos(angle) * radius;
            double up = -Math.sin(angle) * radius; // clockwise looking along +x: +z first, then down
            x = eyeX + up * upX;
            y = eyeY + up * upY;
            z = eyeZ + across;
        }

        final void halt() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    /** One of the three loops, following the spin for as long as it plays. */
    private static final class OrbitLoop extends Orbiting {
        private final int index;

        OrbitLoop(int index) {
            super(sound(index), LOOP_TILT[index]);
            this.index = index;
            this.looping = true;
            this.volume = 0.0f;
            this.pitch = LOOP_PITCH[index];
            follow();
        }

        private static SoundEvent sound(int index) {
            return switch (index) {
                case GROAN -> Level2Audio.SPIN_GROAN.get();
                case RATTLE -> Level2Audio.SPIN_RATTLE.get();
                default -> Level2Audio.PLANT_HUM.get();
            };
        }

        private void follow() {
            place(LOOP_SPEED[index] * phase + LOOP_OFFSET[index], LOOP_RADIUS[index] * close());
            float m = LOOP_MOTION[index];
            volume = LOOP_VOLUME[index] * fade * strength * (1.0f - m + m * Math.min(motion, 1.0f));
            pitch = LOOP_PITCH[index] * (1.0f + LOOP_BEND[index] * (motion - 1.0f));
        }

        @Override
        public void tick() {
            if (LOOPS[index] != this) {
                stop(); // replaced or released
                return;
            }
            follow();
        }
    }

    /**
     * A one-shot carried {@code sweep} radians round you over {@code ticks} ticks (easing in and
     * out like a heavy thing rolling over), then left where it ended until the file runs out.
     */
    private static final class RollingSound extends Orbiting {
        private final float baseVolume;
        private final double radius;
        private final double start;
        private final double sweep;
        private final int ticks;
        private int age;

        RollingSound(SoundEvent sound, float volume, float pitch, double radius, double start, double sweep,
                     int ticks, double tilt) {
            super(sound, tilt);
            this.baseVolume = volume;
            this.radius = radius;
            this.start = start;
            this.sweep = sweep;
            this.ticks = ticks;
            this.looping = false;
            this.pitch = pitch;
            follow();
        }

        private void follow() {
            float s = Math.min(1.0f, age / (float) ticks);
            place(start + sweep * (s * s * (3.0f - 2.0f * s)), radius * close());
            volume = baseVolume * fade * strength;
        }

        @Override
        public void tick() {
            if (rolling != this) {
                stop();
                return;
            }
            age++;
            follow();
        }
    }

    private Level2Orbit() {}
}
