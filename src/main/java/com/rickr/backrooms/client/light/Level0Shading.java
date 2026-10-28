package com.rickr.backrooms.client.light;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/**
 * The knobs, switches and stopwatch of Level 0's Veil lighting (Level0LightRenderer draws it).
 * No Veil types in here, so the rest of the mod can read it even when Veil isn't installed.
 *
 * <p>The stopwatch times the graphics card, not the CPU: two GL timestamps around the Level 0
 * lamps (their lighting and wall shadows) and one more after the screen pass (power and corners).
 * Results arrive a few frames late, so a ring of query objects is used and nothing ever waits.
 * Shown on the F3 screen (see BackroomsLights#onDebugText).
 */
public final class Level0Shading {
    /** How strongly corners and wall feet darken the bounce fill (0..1; the AO itself bottoms out at 0.3 per edge). */
    static final float FILL_AO = 1.0f;
    /** How strongly they darken the lamp light (softer: the lamps' own falloff and shadows do most of it). */
    static final float LIGHT_AO = 0.55f;
    /** Corner darkening fades out by this distance (blocks): the wall map ends ~48-64 blocks out. */
    static final float AO_FADE = 56.0f;
    /**
     * Ceiling panels nearer than this (blocks) go dark one by one with their own tube (the block
     * is redrawn, see BackroomsLights); farther ones follow the smooth power map instead.
     */
    static final float PANEL_POWER_FROM = 40.0f;
    /**
     * Highlight shoulder, on camera (after the camcorder grade): up to the knee nothing changes,
     * above it the brightest colour channel eases toward the peak, so a wall right beside a panel
     * peaks around 0.88 (brightness ~0.8) instead of clipping to white, and keeps its hue. The
     * panel lenses themselves are left alone.
     */
    static final float SHOULDER_KNEE = 0.45f;
    static final float SHOULDER_PEAK = 0.88f;
    /**
     * Darkness colour: below {@link #DARK_BELOW} on camera the colour is this much (0..1) the
     * reference's mauve instead of the textures' brown; the blend fades out by 3x that.
     */
    static final float DARK_TINT = 0.85f;
    static final float DARK_BELOW = 0.03f;
    /**
     * Lamp light reaching one surface, all lamps together (before the surface's own colour): a
     * single panel's pool (~0.95 right under it) passes untouched up to the knee; where panels
     * crowd (bright districts) or a wall stands right beside one, the sum eases toward the cap.
     * This is what keeps bright districts from glaring (frame mean target <= 0.35).
     */
    static final float LAMP_KNEE = 0.9f;
    static final float LAMP_CAP = 1.2f;

    /** Set by BackroomsLights: true while on Level 0 with Veil lighting running. */
    static boolean wanted;
    /** When the screen pass last drew (ms), so the lightmap knows whether to leave the power to it. */
    private static long lastDrawMs;

    private static final int FRAMES = 4;
    private static int[] queries;
    private static final boolean[] PENDING = new boolean[FRAMES];
    private static int frame;
    /** Smoothed GPU time of the lamps (with their shadows) and of the screen pass, ms. */
    private static float lampsMs;
    private static float screenMs;
    private static int lamps;

    private Level0Shading() {}

    /** True while the screen pass runs (so the lightmap fill must not also apply the power). */
    public static boolean screenPassRunning() {
        return wanted && System.currentTimeMillis() - lastDrawMs < 500L;
    }

    static void markDrawn(int visibleLamps) {
        lastDrawMs = System.currentTimeMillis();
        lamps = visibleLamps;
    }

    /** The F3 line, or null when nothing ran lately. */
    static String debugLine() {
        if (!screenPassRunning()) {
            return null;
        }
        return String.format("Level 0 lights: %d lamps, GPU %.2f ms lamps+shadows, %.2f ms power/corners", lamps, lampsMs, screenMs);
    }

    /** Smoothed GPU time of the last lamp passes (Level 0 or Level 2, whichever is drawing), ms. */
    static float lampsMs() {
        return lampsMs;
    }

    // ------------------------------------------------------------------ GPU stopwatch

    /**
     * Set once per frame before the world's lights are drawn (BackroomsLights#onRenderStage):
     * Veil draws the lights a second time for your hand, and only the world's pass is timed.
     */
    static boolean armed;
    /** True while this frame's world pass is being timed. */
    private static boolean timing;

    /** Start of the Level 0 light work this frame (render thread). */
    static void timerBegin() {
        timing = armed;
        armed = false;
        if (!timing) {
            return;
        }
        if (queries == null) {
            queries = new int[FRAMES * 3];
            GL15.glGenQueries(queries);
        }
        collect();
        GL33.glQueryCounter(queries[frame * 3], GL33.GL_TIMESTAMP);
    }

    /** The lamps are drawn; the screen pass starts. */
    static void timerSplit() {
        if (timing) {
            GL33.glQueryCounter(queries[frame * 3 + 1], GL33.GL_TIMESTAMP);
        }
    }

    /** All done this frame. */
    static void timerEnd() {
        if (!timing) {
            return;
        }
        timing = false;
        GL33.glQueryCounter(queries[frame * 3 + 2], GL33.GL_TIMESTAMP);
        PENDING[frame] = true;
        frame = (frame + 1) % FRAMES;
    }

    /** Reads the frame about to be reused, if its results are in (they are, three frames later). */
    private static void collect() {
        if (!PENDING[frame]) {
            return;
        }
        int last = queries[frame * 3 + 2];
        if (GL15.glGetQueryObjecti(last, GL15.GL_QUERY_RESULT_AVAILABLE) == 0) {
            return; // not in yet: skip this sample rather than stall
        }
        long t0 = GL33.glGetQueryObjecti64(queries[frame * 3], GL15.GL_QUERY_RESULT);
        long t1 = GL33.glGetQueryObjecti64(queries[frame * 3 + 1], GL15.GL_QUERY_RESULT);
        long t2 = GL33.glGetQueryObjecti64(last, GL15.GL_QUERY_RESULT);
        PENDING[frame] = false;
        lampsMs += ((t1 - t0) / 1.0e6f - lampsMs) * 0.05f;
        screenMs += ((t2 - t1) / 1.0e6f - screenMs) * 0.05f;
    }
}
