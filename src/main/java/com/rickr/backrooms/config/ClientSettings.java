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

    static {
        BUILDER.pop();
        BUILDER.push("hud");
    }

    public static final ModConfigSpec.BooleanValue STAMINA_BAR = BUILDER
            .comment("Show the thin stamina line above your hearts while you're using or recovering sprint energy.")
            .define("staminaBar", true);

    static {
        BUILDER.pop();
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
