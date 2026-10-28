package com.rickr.backrooms.client.hud;

import com.rickr.backrooms.Backrooms;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * One shared look for everything the camcorder draws on screen (REC, battery, signal, clock, the
 * heart rate, SAFE ROOM, HIDING, the stamina line, the faded hearts, the arrival card): a thin VHS font, small,
 * warm off-white and a little see-through, the same margins everywhere, and one fade rule.
 *
 * <p>The idea is real camcorder footage: the readouts are there when something changes or when
 * you bring the camera up, then sink back to a faint ghost (or vanish) so you look at the
 * picture, not at the HUD. Warnings (low battery, low health, a racing heart) use a soft red that
 * pulses instead of a neon colour that blinks.
 */
public final class OsdStyle {
    /** The VT323 "VHS" font the menus use (assets/backrooms/font/vhs.json): thin, like a real OSD. */
    private static final ResourceLocation FONT = Backrooms.id("vhs");

    // ---- Colours (RGB only; the opacity comes from the fade) ------------------------------------
    /** Warm off-white, the colour of an old camcorder's character generator. */
    public static final int TEXT = 0xE8E3D4;
    /** Muted amber: "pay attention" (hiding, a fast heart). */
    public static final int WARN = 0xD9B36E;
    /** Soft brick red: real warnings (low battery, panic). Never pure red. */
    public static final int ALERT = 0xD65A4A;
    /** Dusty sage for "you're safe": a hint of green without the neon. */
    public static final int CALM = 0xB4C4A8;
    /** The REC lamp: a real camcorder's dull red LED. */
    public static final int REC = 0xC8382C;
    /**
     * Near-black, a touch warm: the edge round the arrival card's letters (and its faint backing),
     * like the black outline a camcorder's title generator puts round its text. It keeps the text
     * readable on Level 37's white tiles without needing a heavy box in Level 2's dark.
     */
    public static final int EDGE = 0x0B0A08;
    /** The edge is this much of the text's own opacity. */
    private static final float EDGE_ALPHA = 0.7f;

    // ---- Opacity --------------------------------------------------------------------------------
    /** A readout just after it changed: clearly readable but still see-through. */
    public static final float FULL = 0.78f;
    /** The same readout once it has sat unchanged for a while: a faint ghost you can still read. */
    public static final float REST = 0.24f;
    /** A warning never fades below this (it pulses between this and {@link #FULL}+). */
    public static final float WARN_MIN = 0.45f;

    // ---- Timing ---------------------------------------------------------------------------------
    /** How long a readout stays at full after it changed. */
    public static final long HOLD_MS = 4000L;
    /** Time for a whole 0-to-1 fade in... */
    private static final float IN_MS = 250.0f;
    /** ...and out (FULL to REST takes about 1.3 s). */
    private static final float OUT_MS = 2500.0f;

    /** Text sizes (VT323 is drawn 10 GUI pixels tall; its capitals are about 6). */
    public static final float SMALL = 1.0f;
    public static final float LARGE = 1.25f;

    /**
     * Distance from the screen edge to every readout, like the "title safe" area on a TV: 4% of the
     * width at the sides, 5% of the height at the top and bottom. Scales with the window, so the
     * readouts never crowd the corners on a wide screen or drift to the middle on a small one.
     */
    public static int marginX(GuiGraphics g) {
        return Math.max(10, Math.round(g.guiWidth() * 0.04f));
    }

    public static int marginY(GuiGraphics g) {
        return Math.max(8, Math.round(g.guiHeight() * 0.05f));
    }

    /** {@code rgb} with a 0..1 opacity, as the ARGB number Minecraft's drawing calls want. */
    public static int argb(int rgb, float alpha) {
        return (Mth.clamp(Math.round(alpha * 255.0f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Text in the VHS font. */
    public static MutableComponent text(String s) {
        return Component.literal(s).withStyle(style -> style.withFont(FONT));
    }

    /** Width in GUI pixels once drawn at {@code scale}. */
    public static int width(Font font, Component text, float scale) {
        return Mth.ceil(font.width(text) * scale);
    }

    /** Width in GUI pixels of an already wrapped line once drawn at {@code scale}. */
    public static int width(Font font, FormattedCharSequence text, float scale) {
        return Mth.ceil(font.width(text) * scale);
    }

    /**
     * Draws a wrapped line with its top-left at (x, y) and a one-GUI-pixel {@link #EDGE} round it
     * (four dark copies nudged left, right, up and down; the text goes on top), so it reads on
     * bright and dark pictures alike. Same "nearly invisible" rule as {@link #draw}.
     */
    public static void drawEdged(GuiGraphics g, Font font, FormattedCharSequence text, float x, float y, float scale, int rgb, float alpha) {
        if (alpha < 0.02f) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0f);
        g.pose().scale(scale, scale, 1.0f);
        float edgeAlpha = alpha * EDGE_ALPHA;
        if (edgeAlpha >= 0.02f) {
            int edge = argb(EDGE, edgeAlpha);
            float e = 1.0f / scale; // one GUI pixel, whatever size the text is drawn at
            g.drawString(font, text, -e, 0.0f, edge, false);
            g.drawString(font, text, e, 0.0f, edge, false);
            g.drawString(font, text, 0.0f, -e, edge, false);
            g.drawString(font, text, 0.0f, e, edge, false);
        }
        g.drawString(font, text, 0.0f, 0.0f, argb(rgb, alpha), false);
        g.pose().popPose();
    }

    /**
     * Draws text with its top-left at (x, y). Skips nearly invisible text: Minecraft treats a
     * text alpha below 4/255 as "no alpha given" and would draw it fully opaque instead.
     */
    public static void draw(GuiGraphics g, Font font, Component text, float x, float y, float scale, int rgb, float alpha) {
        if (alpha < 0.02f) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0f);
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(font, text, 0, 0, argb(rgb, alpha), true);
        g.pose().popPose();
    }

    /** A filled box, skipped when invisible (saves a draw call). */
    public static void box(GuiGraphics g, int x0, int y0, int x1, int y1, int rgb, float alpha) {
        if (alpha >= 0.02f) {
            g.fill(x0, y0, x1, y1, argb(rgb, alpha));
        }
    }

    /**
     * A slow, soft pulse for warnings: 0..1, one breath every {@code periodMs}. Used instead of a
     * hard on/off blink, which reads as a game UI and pulls the eye too much.
     */
    public static float pulse(long now, long periodMs) {
        float phase = (now % periodMs) / (float) periodMs;
        return 0.5f + 0.5f * Mth.cos(phase * Mth.TWO_PI);
    }

    /** Warning opacity: breathes between {@link #WARN_MIN} and a bit above {@link #FULL}. */
    public static float warning(long now, long periodMs) {
        return Mth.lerp(pulse(now, periodMs), WARN_MIN, 0.92f);
    }

    /**
     * One readout's opacity over time. Call {@link #wake} when what it shows changes (or the camera
     * comes up); {@link #update} then gives full opacity for {@link #HOLD_MS} and afterwards eases
     * down to whatever "resting" opacity you ask for (a faint ghost, or 0 to vanish). Changes are
     * rate-limited, so nothing ever pops: a quarter second to come up, a second or so to sink back.
     */
    public static final class Fade {
        private long wokenMs;
        private long lastMs;
        private float shown;

        public void wake(long now) {
            this.wokenMs = now;
        }

        /** True while the readout is still in its "just changed" hold. */
        public boolean fresh(long now) {
            return this.wokenMs != 0L && now - this.wokenMs < HOLD_MS;
        }

        /** The opacity to draw with this frame. Call it every frame the readout could be visible. */
        public float update(long now, float full, float rest) {
            float target = fresh(now) ? Math.max(full, rest) : rest;
            long gap = now - this.lastMs;
            this.lastMs = now;
            if (gap > 500L) {
                // Not drawn for a while (OSD off, another level): start from nothing and fade in,
                // rather than continuing from a stale value. (500 ms, so a slow frame doesn't count.)
                this.shown = 0.0f;
                gap = 0L;
            }
            if (target > this.shown) {
                this.shown = Math.min(target, this.shown + gap / IN_MS);
            } else {
                this.shown = Math.max(target, this.shown - gap / OUT_MS);
            }
            return this.shown;
        }
    }

    private OsdStyle() {}
}
