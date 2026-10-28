package com.rickr.backrooms.client.light;

/**
 * The knobs of Level 2's Veil lamps (drawn by Level0LightRenderer with the level2_point shader):
 * shadows from the wall map, plus the gloss highlights on damp, wet and metal surfaces (see
 * {@link Level2Gloss} for which blocks are which). No Veil types in here, so the rest of the mod
 * can read it even when Veil isn't installed.
 *
 * <p>What the gloss numbers mean: a highlight's peak is about strength x (sharpness + 2) / 8 of the
 * lamp's colour at the lamp's foot, less further out. Wet with strength 0.22 and sharpness 70 peaks
 * near 2 right under a lamp at a grazing angle (a bright streak, the camcorder's glow blooms it),
 * about a third of that looking straight down; damp 0.05 / 10 is a faint broad sheen (~0.08) that
 * just tells concrete "damp" from "dry"; metal 0.16 / 28 a clear tinted glint on pipes and grating.
 */
public final class Level2Shading {
    /** Damp concrete, condensation streaks, painted pipes. */
    static final float DAMP_STRENGTH = 0.05f;
    static final float DAMP_SHARPNESS = 10.0f;
    /** Puddles, wet patches, leaks, gutters. */
    static final float WET_STRENGTH = 0.22f;
    static final float WET_SHARPNESS = 70.0f;
    /** Bare steel and copper, flanges, grating, valves. */
    static final float METAL_STRENGTH = 0.16f;
    static final float METAL_SHARPNESS = 28.0f;
    /** The highlight fades as (1 - distance / reach) to this power (the light itself uses 3). */
    static final float GLOSS_FALLOFF = 2.0f;
    /** Share of the wet highlight left when looking straight down onto it (the rest needs a grazing view). */
    static final float WET_HEAD_ON = 0.3f;

    /** Set by BackroomsLights: true while on Level 2 with Veil lighting running. */
    static boolean wanted;
    /** When Level 2's lamps last drew (ms) and how many, for the F3 line. */
    private static long lastDrawMs;
    private static int lamps;

    private Level2Shading() {}

    static void markDrawn(int visibleLamps) {
        lastDrawMs = System.currentTimeMillis();
        lamps = visibleLamps;
    }

    /** The F3 line, or null when Level 2's lamps didn't draw lately. */
    static String debugLine() {
        if (!wanted || System.currentTimeMillis() - lastDrawMs > 500L) {
            return null;
        }
        return String.format("Level 2 lights: %d lamps, GPU %.2f ms lamps+shadows+gloss", lamps, Level0Shading.lampsMs());
    }
}
