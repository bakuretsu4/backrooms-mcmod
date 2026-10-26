package com.rickr.backrooms.client.menu;

import com.rickr.backrooms.Backrooms;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The shared look of the main menu screens: the VT323 "VHS" font, the menu colours and a few
 * small drawing helpers (scaled text, the PLAY triangle, the viewfinder corner brackets).
 * The VHS font only has plain letters, so symbols like the play arrow and the REC dot are drawn
 * with filled rectangles instead of text.
 */
final class VhsText {
    /** assets/backrooms/font/vhs.json (the VT323 TTF). */
    static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath(Backrooms.MOD_ID, "vhs");

    static final int WHITE = 0xFFF2F2E8;
    static final int IDLE = 0xFFC8C8C0;
    static final int YELLOW = 0xFFF2E14C;
    static final int DIM_YELLOW = 0xFFC9B94A;
    static final int GREY = 0xFF9A9A94;
    static final int DISABLED = 0xFF6A6A66;
    static final int RED = 0xFFE0281E;

    /** Untranslated text in the VHS font. */
    static MutableComponent literal(String text) {
        return Component.literal(text).withStyle(style -> style.withFont(FONT));
    }

    /** A lang-file string (assets/backrooms/lang/en_us.json) in the VHS font. */
    static MutableComponent translatable(String key, Object... args) {
        return Component.translatable(key, args).withStyle(style -> style.withFont(FONT));
    }

    /** Width of a text in GUI pixels once scaled. */
    static int width(Font font, Component text, float scale) {
        return Mth.ceil(font.width(text) * scale);
    }

    static void draw(GuiGraphics g, Font font, Component text, float x, float y, float scale, int color, boolean shadow) {
        draw(g, font, text.getVisualOrderText(), x, y, scale, color, shadow);
    }

    /** Draws text with its top-left corner at (x, y), scaled up or down around that corner. */
    static void draw(GuiGraphics g, Font font, FormattedCharSequence text, float x, float y, float scale, int color, boolean shadow) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0f);
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(font, text, 0, 0, color, shadow);
        g.pose().popPose();
    }

    /** A right-pointing "play" triangle, {@code size} pixels tall, with its top-left corner at (x, y). */
    static void playTriangle(GuiGraphics g, int x, int y, int size, int color) {
        for (int i = 0; i <= size / 2; i++) {
            g.fill(x + i, y + i, x + i + 1, y + size - i, color);
        }
    }

    /** A round-ish dot (like the REC light), {@code size} pixels across. */
    static void dot(GuiGraphics g, int x, int y, int size, int color) {
        g.fill(x, y + 1, x + size, y + size - 1, color);
        g.fill(x + 1, y, x + size - 1, y + size, color);
    }

    /** Thin L-shaped viewfinder marks in the four corners of the rectangle. */
    static void brackets(GuiGraphics g, int x0, int y0, int x1, int y1, int length, int color) {
        g.fill(x0, y0, x0 + length, y0 + 1, color);
        g.fill(x0, y0, x0 + 1, y0 + length, color);
        g.fill(x1 - length, y0, x1, y0 + 1, color);
        g.fill(x1 - 1, y0, x1, y0 + length, color);
        g.fill(x0, y1 - 1, x0 + length, y1, color);
        g.fill(x0, y1 - length, x0 + 1, y1, color);
        g.fill(x1 - length, y1 - 1, x1, y1, color);
        g.fill(x1 - 1, y1 - length, x1, y1, color);
    }

    /** {@code rgb} with the given 0..1 opacity as an ARGB colour. */
    static int withAlpha(int rgb, float alpha) {
        return (Mth.clamp(Math.round(alpha * 255.0f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private VhsText() {}
}
