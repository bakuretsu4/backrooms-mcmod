package com.rickr.backrooms.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Per-player settings for the camcorder feel, saved in {@code config/backrooms-client.toml} and
 * changed in-game on the SETTINGS screen (main menu, pause menu, or the Mods list).
 *
 * <p>Only the client ever reads these (NeoForge never loads a CLIENT config on a dedicated
 * server), and they only change what you see: your real aim, the server and other players are
 * never affected. Values are read every frame, so a slider takes effect immediately.
 */
public final class ClientSettings {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("camera");
    }

    /** 0 = the view follows the mouse instantly; 100 = very heavy lag. The original feel was 88. */
    public static final ModConfigSpec.IntValue CAMERA_SMOOTHING = BUILDER
            .comment("How much the view lags behind the mouse, like a heavy camcorder (0 = none, 100 = very heavy).",
                    "The original setting was 88.")
            .defineInRange("smoothing", 35, 0, 100);

    public static final ModConfigSpec.IntValue HANDHELD_SHAKE = BUILDER
            .comment("Strength of the handheld wobble and the shaky hands when something is near (percent, 100 = normal).")
            .defineInRange("shake", 100, 0, 200);

    public static final ModConfigSpec.IntValue VIEW_TILT = BUILDER
            .comment("How far the view tilts into turns and sideways steps (percent, 100 = normal).")
            .defineInRange("tilt", 100, 0, 200);

    public static final ModConfigSpec.IntValue MOTION_BLUR = BUILDER
            .comment("Smear when you turn quickly (percent, 100 = normal).")
            .defineInRange("motionBlur", 100, 0, 200);

    public static final ModConfigSpec.BooleanValue WIDE_LENS = BUILDER
            .comment("Widen the field of view by 20% in the Backrooms, like a camcorder lens.")
            .define("wideLens", true);

    /** The picture looks like home-video footage (shaders/program/camcorder.fsh, the footage stage). */
    public static final ModConfigSpec.BooleanValue FOOTAGE_LOOK = BUILDER
            .comment("Make the picture look like footage from a cheap old camera: warm, soft, colour bleeding,",
                    "over-sharpened edges, blown-out whites, noise in the dark and an exposure that hunts.")
            .define("footageLook", true);

    public static final ModConfigSpec.BooleanValue FRAME_4_3 = BUILDER
            .comment("Show the footage in a 4:3 frame with black bars at the sides, like the old camera's picture.",
                    "Only with the footage look on.")
            .define("frame43", true);

    static {
        BUILDER.pop();
        BUILDER.push("hud");
    }

    public static final ModConfigSpec.BooleanValue STAMINA_BAR = BUILDER
            .comment("Show the thin stamina line above your hearts while you're using or recovering sprint energy.")
            .define("staminaBar", true);

    static {
        BUILDER.pop();
        BUILDER.push("graphics");
    }

    public static final ModConfigSpec.EnumValue<Quality> QUALITY = BUILDER
            .comment("Graphics quality: HIGH = everything; MEDIUM and LOW are for weaker computers",
                    "(fewer dynamic lamp lights, less glow around lamps, simpler pool water reflections).")
            .defineEnum("quality", Quality.HIGH);

    static {
        BUILDER.pop();
    }

    /**
     * What each quality level turns down. The costly parts, measured: every Veil lamp light is a
     * shadowed light the graphics card draws each frame; the glow is extra full-screen blur passes;
     * the pool water traces a reflection for every water pixel.
     */
    public enum Quality {
        //   lamp lights, light range, glow chain,          water reflection steps, sun beams, opening glows,
        //   Level 4 window lights, Level 4 troffer downlights
        LOW(12, 20, "camcorder_low", 0, 6, 3, 4, 4),
        MEDIUM(20, 24, "camcorder_medium", 10, 12, 6, 7, 8),
        HIGH(32, 28, "camcorder", 18, 20, 12, 10, 14);

        /** Most Veil lamp lights at once. */
        public final int maxLights;
        /** Furthest lamp that gets a Veil light (blocks). */
        public final int lightRange;
        /** The camcorder post-processing chain (assets/backrooms/shaders/post/NAME.json); they differ only in the glow passes. */
        public final String postChain;
        /** Ray-march steps of the pool water reflection; 0 = no traced reflection, just the mirror colour. */
        public final int reflectionSteps;
        /** Most Level 37 / Poolrooms sun beams (shadowed area lights) at once. */
        public final int maxSunBeams;
        /**
         * How many of the nearest openings also get their two small hidden glows (the wash inside
         * the niche and the soft bounce in front of it, client/light/SunBeams): two more lights each.
         */
        public final int maxSunGlows;
        /**
         * Most Level 4 window-run lights at once (shadowed area lights: the soft daylight falling
         * in from the ribbon windows, client/light/Level4Lights).
         */
        public final int maxL4Windows;
        /** Most Level 4 lit-troffer downlights at once (small shadowed area lights under the lit panels). */
        public final int maxL4Troffers;

        Quality(int maxLights, int lightRange, String postChain, int reflectionSteps, int maxSunBeams, int maxSunGlows,
                int maxL4Windows, int maxL4Troffers) {
            this.maxLights = maxLights;
            this.lightRange = lightRange;
            this.postChain = postChain;
            this.reflectionSteps = reflectionSteps;
            this.maxSunBeams = maxSunBeams;
            this.maxSunGlows = maxSunGlows;
            this.maxL4Windows = maxL4Windows;
            this.maxL4Troffers = maxL4Troffers;
        }
    }

    public static Quality quality() {
        return QUALITY.get();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    /**
     * The look smoothing as "fraction of the gap still left after one tick" (see HandheldCamera):
     * 0 at 0%, 0.8 at 100%. The original fixed value was 0.7.
     */
    public static float lookSmoothing() {
        return 0.8f * CAMERA_SMOOTHING.get() / 100.0f;
    }

    public static float shake() {
        return HANDHELD_SHAKE.get() / 100.0f;
    }

    public static float tilt() {
        return VIEW_TILT.get() / 100.0f;
    }

    public static float motionBlur() {
        return MOTION_BLUR.get() / 100.0f;
    }

    private ClientSettings() {}
}
