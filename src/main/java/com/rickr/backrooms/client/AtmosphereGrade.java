package com.rickr.backrooms.client;

import com.rickr.backrooms.client.light.FixturePower;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Level94Clock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import org.jetbrains.annotations.Nullable;

/**
 * The "mood" of each level, fed to the camcorder shader as a handful of numbers. Every effect here
 * works the same over the WHOLE picture (nothing is stronger in the middle than at the edges):
 * <ul>
 *   <li><b>Grade:</b> exposure, contrast, saturation, a tint for the shadows and one for the
 *       highlights ("split toning"), and a lifted black (the tape never goes fully black).</li>
 *   <li><b>Haze:</b> the air itself has a colour; the further away something is, the more of it
 *       sits between you and it. Based on real distance (the depth buffer), not screen position.</li>
 *   <li><b>Contact shadows:</b> a soft darkening where walls meet floors and in corners
 *       (screen-space ambient occlusion), so rooms feel solid and a little dirty.</li>
 *   <li><b>Glow:</b> bright areas bleed softly into their surroundings, like old camcorder optics.
 *       This is what makes it feel dreamlike without blurring what you need to see.</li>
 *   <li><b>Breath:</b> the exposure drifts by a percent or two, very slowly, as if the camera's
 *       auto-iris can't settle. You barely notice it; it just feels wrong.</li>
 * </ul>
 * Switching level fades from one mood to the next over about two seconds instead of snapping.
 */
public final class AtmosphereGrade {
    /**
     * One mood. Tints are multipliers (1, 1, 1 = no change); lift is the darkest colour. Public so
     * anomaly effects can ask for their own (see {@link #setAnomalyMood}).
     */
    public record Mood(float exposure, float contrast, float saturation,
                       float shR, float shG, float shB, float hiR, float hiG, float hiB,
                       float liftR, float liftG, float liftB,
                       float fogR, float fogG, float fogB, float fogStart, float fogDensity, float fogMax,
                       float ao, float glow, float breath) {
        public Mood lerp(Mood o, float t) {
            return new Mood(l(exposure, o.exposure, t), l(contrast, o.contrast, t), l(saturation, o.saturation, t),
                    l(shR, o.shR, t), l(shG, o.shG, t), l(shB, o.shB, t), l(hiR, o.hiR, t), l(hiG, o.hiG, t), l(hiB, o.hiB, t),
                    l(liftR, o.liftR, t), l(liftG, o.liftG, t), l(liftB, o.liftB, t),
                    l(fogR, o.fogR, t), l(fogG, o.fogG, t), l(fogB, o.fogB, t),
                    l(fogStart, o.fogStart, t), l(fogDensity, o.fogDensity, t), l(fogMax, o.fogMax, t),
                    l(ao, o.ao, t), l(glow, o.glow, t), l(breath, o.breath, t));
        }

        private static float l(float a, float b, float t) {
            return a + (b - a) * t;
        }
    }

    // exposure, contrast, saturation | shadow tint | highlight tint | lift | haze colour, start (blocks),
    // density (per block), max | contact shadows, glow, breath
    /** Outside the Backrooms: no change at all (the old fixed blue-violet black point only). */
    private static final Mood NEUTRAL = new Mood(1.0f, 1.0f, 1.0f, 1, 1, 1, 1, 1, 1,
            2 / 255f, 0, 5 / 255f, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    /**
     * Level 0, Kane Pixels' tapes: bright, flat and muted olive-mustard. Red and green stay level
     * while blue is cut (~28% in the shadows, ~12% in the highlights: the textures already carry
     * the yellow, and the troffers must stay near white), slightly less colour than the textures,
     * gentle contrast, and blacks crushed so the dark wings are solid black. Tuned so a lit wall
     * lands near #B7A746 and the carpet near #A3893A on screen.
     * No distance haze at all (max 0): depth comes from the repetition and the dark wings. The
     * haze colour is still set so anomaly moods that add haze blend from a sensible colour.
     */
    private static final Mood LEVEL_0 = new Mood(1.08f, 1.04f, 0.95f, 0.97f, 1.0f, 0.72f, 0.99f, 1.0f, 0.88f,
            0.004f, 0.004f, 0.002f, 0.40f, 0.37f, 0.18f, 30.0f, 0.010f, 0.0f, 0.30f, 0.14f, 0.010f);
    /** Level 1: cold, damp concrete. Blue shadows, grey highlights, little colour, grey-blue air. */
    private static final Mood LEVEL_1 = new Mood(1.0f, 1.08f, 0.74f, 0.90f, 0.95f, 1.06f, 1.0f, 1.0f, 1.03f,
            0.012f, 0.013f, 0.018f, 0.18f, 0.19f, 0.23f, 7.0f, 0.032f, 0.40f, 0.45f, 0.12f, 0.010f);
    /** Level 2: hot, cramped and dirty. Rusty-brown shadows, cold bulbs, deep contact shadows, dark oily air. */
    private static final Mood LEVEL_2 = new Mood(1.0f, 1.12f, 0.80f, 1.05f, 0.96f, 0.86f, 0.98f, 1.0f, 1.04f,
            0.007f, 0.006f, 0.008f, 0.10f, 0.08f, 0.06f, 4.0f, 0.045f, 0.45f, 0.50f, 0.15f, 0.012f);
    /** Level 4: an empty office on a rainy night. Cold fluorescent blue-grey, quiet, faint blue air. */
    private static final Mood LEVEL_4 = new Mood(1.0f, 1.05f, 0.80f, 0.90f, 0.95f, 1.08f, 0.99f, 1.0f, 1.04f,
            0.012f, 0.014f, 0.022f, 0.11f, 0.12f, 0.16f, 9.0f, 0.028f, 0.36f, 0.36f, 0.14f, 0.008f);
    /** The Poolrooms: chlorine and echoes. Teal shadows, cool soft highlights, a gentle wet glow. */
    private static final Mood POOLROOMS = new Mood(1.0f, 0.98f, 0.92f, 0.88f, 0.98f, 1.06f, 0.98f, 1.02f, 1.04f,
            0.012f, 0.017f, 0.022f, 0.15f, 0.21f, 0.23f, 7.0f, 0.030f, 0.36f, 0.32f, 0.22f, 0.010f);
    /**
     * Level 37: a dream. Low contrast, milky shadows, warm overexposed highlights, warm white air
     * and the strongest glow: bright but strangely soft, like a memory of a place.
     */
    private static final Mood LEVEL_37 = new Mood(1.0f, 0.90f, 0.86f, 0.97f, 1.0f, 0.95f, 1.05f, 1.01f, 0.92f,
            0.032f, 0.032f, 0.026f, 0.86f, 0.83f, 0.74f, 12.0f, 0.020f, 0.28f, 0.26f, 0.30f, 0.014f);
    /** Level 9: midnight suburb. Deep blue shadows, sodium-orange highlights, cold dark air. */
    private static final Mood LEVEL_9 = new Mood(1.0f, 1.07f, 0.86f, 0.86f, 0.92f, 1.12f, 1.06f, 0.98f, 0.84f,
            0.008f, 0.010f, 0.020f, 0.06f, 0.07f, 0.09f, 9.0f, 0.030f, 0.40f, 0.34f, 0.20f, 0.010f);
    /**
     * Level 94 by day: a toy town that's slightly off. A touch over-saturated and soft, warm light,
     * pale blue air that turns far hills into a painted backdrop.
     */
    private static final Mood LEVEL_94_DAY = new Mood(1.0f, 0.96f, 1.08f, 0.95f, 0.97f, 1.04f, 1.03f, 1.01f, 0.95f,
            0.020f, 0.020f, 0.030f, 0.70f, 0.80f, 0.92f, 40.0f, 0.008f, 0.35f, 0.24f, 0.18f, 0.006f);
    /** Level 94 at night: the colour leaves; blue-black air and the same soft glow. */
    private static final Mood LEVEL_94_NIGHT = new Mood(1.0f, 1.04f, 0.80f, 0.86f, 0.92f, 1.12f, 1.02f, 1.0f, 0.94f,
            0.008f, 0.010f, 0.022f, 0.05f, 0.06f, 0.10f, 14.0f, 0.020f, 0.40f, 0.30f, 0.20f, 0.010f);
    /**
     * Level 39 in mid-town: the 1940s night photo. Almost no colour, cool blue-grey shadows,
     * slightly cool highlights, luminous grey air, a lifted black (moody but clearly readable)
     * and a soft glow so the windows bloom.
     */
    private static final Mood LEVEL_39_TOWN = new Mood(1.08f, 1.04f, 0.72f, 0.92f, 0.96f, 1.06f, 1.0f, 1.0f, 1.02f,
            0.028f, 0.030f, 0.036f, 0.26f, 0.27f, 0.29f, 12.0f, 0.016f, 0.34f, 0.30f, 0.22f, 0.010f);
    /**
     * Level 39 right under the superstructure: the air itself lit pale silver by the windows. Less
     * glow and haze than one would think: up close the brick, stone and muntins must stay crisp.
     */
    private static final Mood LEVEL_39_FACADE = new Mood(1.0f, 1.06f, 0.70f, 0.92f, 0.96f, 1.06f, 0.98f, 0.99f, 1.01f,
            0.030f, 0.031f, 0.035f, 0.55f, 0.57f, 0.60f, 14.0f, 0.012f, 0.30f, 0.30f, 0.12f, 0.010f);
    /**
     * Level 39's tunnels (tunnel.png): bright, clean and still. White marble that keeps its
     * blue-grey veins (a touch under neutral exposure, a little more contrast), vivid turquoise
     * water (extra colour), pale turquoise air, little bloom so the vault doesn't clip.
     */
    private static final Mood LEVEL_39_TUNNEL = new Mood(0.94f, 1.05f, 1.12f, 0.92f, 1.0f, 1.03f, 0.98f, 1.0f, 1.01f,
            0.024f, 0.030f, 0.032f, 0.46f, 0.60f, 0.62f, 18.0f, 0.010f, 0.18f, 0.26f, 0.10f, 0.006f);
    /** A stagnant (Hydrolitis) tunnel segment: orbs dead, sick green-black air. */
    private static final Mood LEVEL_39_STAGNANT = new Mood(0.98f, 1.08f, 0.80f, 0.90f, 1.04f, 0.94f, 0.98f, 1.02f, 0.94f,
            0.008f, 0.012f, 0.010f, 0.05f, 0.09f, 0.07f, 4.0f, 0.040f, 0.45f, 0.40f, 0.18f, 0.012f);
    /**
     * Level 7 above the water: the fog of bone dust. Colour almost gone, cold grey shadows, and a
     * thick pale haze that swallows the sea a few dozen blocks out.
     */
    private static final Mood LEVEL_7_AIR = new Mood(1.0f, 0.95f, 0.60f, 0.93f, 0.97f, 1.0f, 1.0f, 1.0f, 0.97f,
            0.020f, 0.022f, 0.024f, 0.40f, 0.41f, 0.40f, 6.0f, 0.030f, 0.60f, 0.30f, 0.10f, 0.012f);
    /** Level 7 just under the surface: murky dark teal closing in. */
    private static final Mood LEVEL_7_WATER = new Mood(1.0f, 1.0f, 0.68f, 0.85f, 0.98f, 1.02f, 0.95f, 1.0f, 1.0f,
            0.004f, 0.009f, 0.011f, 0.050f, 0.10f, 0.11f, 2.0f, 0.035f, 0.85f, 0.30f, 0.10f, 0.010f);
    /** Level 7 in the deep: nearly black; your flashlight reaches about a dozen blocks into the haze. */
    private static final Mood LEVEL_7_DEEP = new Mood(1.0f, 1.04f, 0.55f, 0.85f, 0.95f, 1.04f, 0.92f, 0.98f, 1.0f,
            0.002f, 0.003f, 0.004f, 0.004f, 0.007f, 0.009f, 2.0f, 0.065f, 0.95f, 0.30f, 0.08f, 0.008f);

    /** Fraction of the way to the target mood per frame at 60 fps (about two seconds to settle). */
    private static final float BLEND_PER_FRAME = 0.035f;

    @Nullable
    private static Mood current;
    private static long lastNanos;
    @Nullable
    private static Mood anomalyMood;
    private static float anomalyWeight;

    private AtmosphereGrade() {}

    /**
     * An anomaly's own mood, mixed over the level's by {@code weight} (0..1). Client anomaly
     * effects call this every tick while it should apply; it is cleared before each tick, and the
     * picture eases back to the level's mood over a couple of seconds when nobody asks any more.
     */
    public static void setAnomalyMood(Mood mood, float weight) {
        anomalyMood = mood;
        anomalyWeight = Math.max(0.0f, Math.min(1.0f, weight));
    }

    public static void clearAnomalyMood() {
        anomalyMood = null;
        anomalyWeight = 0.0f;
    }

    /** The base mood of a level (for anomalies that want "the level's look, but..."). */
    public static Mood levelMood(BackroomsLevel floor, Minecraft mc) {
        return switch (floor) {
            case LEVEL_0 -> LEVEL_0;
            case LEVEL_1 -> LEVEL_1;
            case LEVEL_2 -> LEVEL_2;
            case LEVEL_4 -> LEVEL_4;
            case POOLROOMS -> POOLROOMS;
            case LEVEL_37 -> LEVEL_37;
            case LEVEL_9 -> LEVEL_9;
            // Level 39: town (cold grey, silvering toward the facades), tunnels (bright turquoise),
            // stagnant segments (dark green), following where you are.
            case LEVEL_39 -> LEVEL_39_TOWN.lerp(LEVEL_39_FACADE, Level39Atmosphere.proximity() * Level39Atmosphere.proximity())
                    .lerp(LEVEL_39_TUNNEL.lerp(LEVEL_39_STAGNANT, Level39Atmosphere.stagnant()), Level39Atmosphere.tunnel());
            case LEVEL_94 -> mc.level == null ? LEVEL_94_DAY : LEVEL_94_NIGHT.lerp(LEVEL_94_DAY, Level94Clock.daylight(mc.level, 0.0f));
            // Level 7: the haze changes with your depth (Level7Client measures it).
            case LEVEL_7 -> !Level7Client.underwater() ? LEVEL_7_AIR : LEVEL_7_WATER.lerp(LEVEL_7_DEEP, Level7Client.deepness());
        };
    }

    /** The mood the current level wants right now (Level 94 follows its own day/night clock). */
    private static Mood target(Minecraft mc) {
        if (mc.level == null) {
            return NEUTRAL;
        }
        BackroomsLevel floor = BackroomsLevel.fromDimension(mc.level.dimension());
        if (floor == null) {
            return NEUTRAL;
        }
        Mood mood = levelMood(floor, mc);
        return anomalyMood == null ? mood : mood.lerp(anomalyMood, anomalyWeight);
    }

    /** Pushes this frame's mood into the camcorder shader. Call once per frame. */
    public static void apply(Minecraft mc, PostChain chain) {
        Mood want = target(mc);
        long now = System.nanoTime();
        float frames = lastNanos == 0L ? 1000.0f : Math.min((now - lastNanos) / 16_666_667.0f, 1000.0f);
        lastNanos = now;
        if (current == null) {
            current = want;
        } else {
            float t = 1.0f - (float) Math.pow(1.0f - BLEND_PER_FRAME, frames);
            current = current.lerp(want, t);
        }
        Mood m = current;
        // In a blackout the air stops glowing: a lit haze colour in the pitch dark would give the
        // walls away. The haze darkens with the lights (the exit signs and flashlights still show).
        float power = FixturePower.mainsPower();
        float fogLight = 0.25f + 0.75f * power;

        // Eyes adjusting: the camcorder's auto-exposure catching up after the light changes.
        chain.setUniform("Exposure", m.exposure() * EyeAdaptation.factor(mc));
        chain.setUniform("Contrast", m.contrast());
        chain.setUniform("Saturation", m.saturation());
        chain.setUniform("ShadowR", m.shR());
        chain.setUniform("ShadowG", m.shG());
        chain.setUniform("ShadowB", m.shB());
        chain.setUniform("HighlightR", m.hiR());
        chain.setUniform("HighlightG", m.hiG());
        chain.setUniform("HighlightB", m.hiB());
        chain.setUniform("LiftR", m.liftR());
        chain.setUniform("LiftG", m.liftG());
        chain.setUniform("LiftB", m.liftB());
        chain.setUniform("HazeR", m.fogR() * fogLight);
        chain.setUniform("HazeG", m.fogG() * fogLight);
        chain.setUniform("HazeB", m.fogB() * fogLight);
        chain.setUniform("HazeStart", m.fogStart());
        chain.setUniform("HazeDensity", m.fogDensity());
        chain.setUniform("HazeMax", m.fogMax());
        chain.setUniform("ContactShadow", m.ao());
        chain.setUniform("Glow", m.glow());
        chain.setUniform("Breath", m.breath());
    }
}
