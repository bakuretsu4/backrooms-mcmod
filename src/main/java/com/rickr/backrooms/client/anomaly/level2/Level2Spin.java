package com.rickr.backrooms.client.anomaly.level2;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.Level2ClientState;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.layout.Level2Twist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Level 2's "the whole place is slowly turning around you" clock: one shared virtual rotation that
 * the camera (client/camera/HandheldCamera), the FOV, the camcorder shader's peripheral swirl
 * (CamcorderEffect), the lamps (client/light/VeilFixtures, ClientLighting) and the sound all follow,
 * so every cue agrees about which way the corridor is turning.
 *
 * <p><b>Phase.</b> Computed from the level's game time, so it is the same for every player in the
 * level (two people filming each other see the same turn). The rate is never constant: a mean of
 * one turn per {@value #BASE_TURN_SECONDS} s, a slow swell faster and slower (~35-70 s a turn), a
 * little flutter, and every ~{@value #SURGE_PERIOD_S} s a lurch where it slows, stops and briefly
 * drifts back before catching up. Everything is a sum of closed-form terms, so the phase and its
 * exact rate come straight from the clock with no state to drift apart between players. Direction:
 * phase increasing means the environment turns clockwise as you look along +x (the same sense as
 * a positive {@link Level2Twist#bank}).
 *
 * <p><b>Strength.</b> How much of the illusion the local player gets: 1 everywhere in Level 2's
 * corridor from the first frame after arrival; only at the exit recess and its approach, where
 * the corridor stops twisting ({@link Level2Twist#amount} falls to 0), does it ease down to
 * {@value #EXIT_CALM}, so the way out feels like solid ground. Eased over about two seconds; 0
 * outside Level 2. The comfort scale (ClientSettings.tilt) is applied by each effect, not here,
 * because the sound uses it differently from the camera.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level2Spin {
    // --- Phase ---------------------------------------------------------------------------------
    /** Mean time for one full virtual turn. */
    private static final double BASE_TURN_SECONDS = 52.0;
    private static final double BASE_RATE = Math.PI * 2.0 / BASE_TURN_SECONDS;
    /**
     * The lurch: a smooth, lopsided sawtooth (atan2(k sin x, 1 - k cos x)) subtracted from the
     * phase. For most of its period it adds a little speed (up to +{@code SURGE_AMOUNT*k/(1+k)} of
     * the mean rate), then for a short stretch it takes it all back at once, so the turn slows,
     * stops and drifts backwards for ~15 s before carrying on. Shape k and size are chosen so the
     * backward drift peaks at about 30% of the mean rate.
     */
    private static final double SURGE_PERIOD_S = 200.0;
    private static final double SURGE_SHAPE = 0.6;
    private static final double SURGE_AMOUNT = 0.867;
    /** A slow swell of +/-25% of the mean rate. */
    private static final double SWELL_PERIOD_S = 41.0;
    private static final double SWELL_AMOUNT = 0.25;
    /** A faint flutter on top (+/-8%), so even the swell is never a clean sine. */
    private static final double FLUTTER_PERIOD_S = 17.3;
    private static final double FLUTTER_AMOUNT = 0.08;

    private static final double SURGE_FREQ = Math.PI * 2.0 / SURGE_PERIOD_S;
    private static final double SWELL_FREQ = Math.PI * 2.0 / SWELL_PERIOD_S;
    private static final double FLUTTER_FREQ = Math.PI * 2.0 / FLUTTER_PERIOD_S;
    // Phase-term amplitudes (radians) that give the rate changes above.
    private static final double SURGE_RADIANS = SURGE_AMOUNT * BASE_RATE / SURGE_FREQ;
    private static final double SWELL_RADIANS = SWELL_AMOUNT * BASE_RATE / SWELL_FREQ;
    private static final double FLUTTER_RADIANS = FLUTTER_AMOUNT * BASE_RATE / FLUTTER_FREQ;

    // --- Strength ------------------------------------------------------------------------------
    /** At the exit and its approach (no twist): the turning eases off, but never stops. */
    public static final float EXIT_CALM = 0.5f;
    /** Strength eases toward its target leaving this much of the gap per tick (95% in ~2 s). */
    private static final float STRENGTH_LEFT_PER_TICK = 0.928f;

    // --- The full roll (the room turning you upside down) -------------------------------------
    /**
     * Further than this from the nearest exit (blocks) the camera rolls right over with the spin;
     * within {@link #FLIP_NONE_WITHIN} it is back upright, so the last stretch to the exit and the
     * bulkhead itself are seen the right way up. In between it unwinds.
     */
    private static final float FLIP_FULL_FROM = 115.0f;
    private static final float FLIP_NONE_WITHIN = 100.0f;
    /** The flip weight eases toward its target leaving this much of the gap per tick (~1.5 s). */
    private static final float FLIP_LEFT_PER_TICK = 0.9f;

    private static float flip;
    private static float prevFlip;

    private static float strength;
    private static float prevStrength;
    private static boolean wasActive;

    /** True while the local player is in Level 2, alive and not spectating. */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        return mc.level != null && player != null && player.isAlive() && !player.isSpectator()
                && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_2;
    }

    /**
     * The environment's virtual rotation in radians: continuous (never wrapped, so any multiple of
     * it, whole or not, is smooth) and the same for every player in the level at the same moment.
     * It grows by about 0.1 per second; in double precision that stays exact for any world age.
     */
    public static double phase(float partialTick) {
        double t = seconds(partialTick);
        return BASE_RATE * t - SURGE_RADIANS * sawtooth(SURGE_FREQ * t + 0.7)
                + SWELL_RADIANS * Math.sin(SWELL_FREQ * t + 2.1)
                + FLUTTER_RADIANS * Math.sin(FLUTTER_FREQ * t + 4.4);
    }

    /** d(phase)/dt in radians per second (exact, from the same terms as {@link #phase}). */
    public static double spinRate(float partialTick) {
        double t = seconds(partialTick);
        return BASE_RATE - SURGE_RADIANS * SURGE_FREQ * sawtoothSlope(SURGE_FREQ * t + 0.7)
                + SWELL_RADIANS * SWELL_FREQ * Math.cos(SWELL_FREQ * t + 2.1)
                + FLUTTER_RADIANS * FLUTTER_FREQ * Math.cos(FLUTTER_FREQ * t + 4.4);
    }

    /** The mean rate (radians per second), for effects that want the rate relative to normal. */
    public static double baseRate() {
        return BASE_RATE;
    }

    /** 0..1 how much of the illusion the local player gets (see the class comment); 0 outside Level 2. */
    public static float strength() {
        if (strength == 0.0f && prevStrength == 0.0f) {
            return 0.0f;
        }
        return Mth.lerp(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false), prevStrength, strength);
    }

    /**
     * 0..1: how much the camera follows the spin all the way round (1 = rolls right over, through
     * upside down; 0 = upright). 1 from {@value #FLIP_FULL_FROM} blocks from the nearest exit, 0
     * within {@value #FLIP_NONE_WITHIN}; 0 outside Level 2.
     */
    public static float flipWeight() {
        return Mth.lerp(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false), prevFlip, flip);
    }

    public static long seed() {
        return Level2ClientState.seed();
    }

    public static boolean hasSeed() {
        return Level2ClientState.hasSeed();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        prevStrength = strength;
        prevFlip = flip;
        if (!active()) {
            strength = prevStrength = 0.0f;
            flip = prevFlip = 0.0f;
            wasActive = false;
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        float target = target(mc.player);
        float flipGoal = flipTarget(mc.player);
        if (!wasActive) {
            // Arrival: the turning is already under way, not fading in from nothing.
            strength = prevStrength = target;
            flip = prevFlip = flipGoal;
            wasActive = true;
            return;
        }
        strength += (target - strength) * (1.0f - STRENGTH_LEFT_PER_TICK);
        flip += (flipGoal - flip) * (1.0f - FLIP_LEFT_PER_TICK);
    }

    /** 1 well away from every exit, 0 within FLIP_NONE_WITHIN of the nearest (once a tick, so the lookup is cheap). */
    private static float flipTarget(LocalPlayer player) {
        if (!hasSeed()) {
            return 1.0f; // arrivals are 500+ blocks from an exit
        }
        int[] exit = BackroomsLevel.LEVEL_2.layout().exitPosition(seed(), Mth.floor(player.getX()), Mth.floor(player.getZ()));
        if (exit == null) {
            return 1.0f;
        }
        double d = Math.hypot(player.getX() - exit[0] - 0.5, player.getZ() - exit[1] - 0.5);
        float u = Mth.clamp((float) ((d - FLIP_NONE_WITHIN) / (FLIP_FULL_FROM - FLIP_NONE_WITHIN)), 0.0f, 1.0f);
        return u * u * (3.0f - 2.0f * u);
    }

    private static float target(LocalPlayer player) {
        if (!hasSeed()) {
            return 1.0f; // seed not here yet: assume the corridor (exits are hundreds of blocks off)
        }
        float twist = Level2Twist.amount(seed(), Mth.floor(player.getX()), Mth.floor(player.getZ()));
        return Mth.lerp(Mth.clamp(twist, 0.0f, 1.0f), EXIT_CALM, 1.0f);
    }

    private static double seconds(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0.0 : (mc.level.getGameTime() + partialTick) / 20.0;
    }

    /** The lopsided smooth sawtooth: a long slow fall, then a quick climb; bounded by +/-asin(k). */
    private static double sawtooth(double x) {
        return Math.atan2(SURGE_SHAPE * Math.sin(x), 1.0 - SURGE_SHAPE * Math.cos(x));
    }

    private static double sawtoothSlope(double x) {
        double c = Math.cos(x);
        return (SURGE_SHAPE * c - SURGE_SHAPE * SURGE_SHAPE) / (1.0 - 2.0 * SURGE_SHAPE * c + SURGE_SHAPE * SURGE_SHAPE);
    }

    private Level2Spin() {}
}
