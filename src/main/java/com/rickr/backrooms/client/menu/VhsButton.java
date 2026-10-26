package com.rickr.backrooms.client.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * A text-only menu entry in the style of a camcorder's on-screen menu: light grey when idle;
 * yellow with a play-arrow cursor and a dark highlight bar when the mouse is over it or it has
 * keyboard focus. It is a real button, so Tab/arrow keys + Enter work like on any vanilla screen.
 */
public class VhsButton extends AbstractButton {
    private final Runnable action;
    private final float textScale;
    /** Was the row highlighted last frame (to play the blip only when it lights up)? */
    private boolean wasLit;
    /** No blip on the very first frame (e.g. the mouse is already resting on a row after a resize). */
    private boolean firstFrame = true;

    public VhsButton(int x, int y, int width, int height, Component label, float textScale, Runnable action) {
        super(x, y, width, height, label);
        this.textScale = textScale;
        this.action = action;
    }

    /** Space left of the label for the cursor arrow. */
    public static int textInset(float textScale) {
        return 4 + Math.round(10.0f * textScale);
    }

    /** How wide a button must be to fit this label. */
    public static int widthFor(Font font, Component label, float textScale) {
        return textInset(textScale) + VhsText.width(font, label, textScale) + 8;
    }

    @Override
    public void onPress() {
        this.action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        boolean lit = this.active && this.isHoveredOrFocused();
        if (lit && !this.wasLit && !this.firstFrame) {
            blip(mc);
        }
        this.wasLit = lit;
        this.firstFrame = false;

        int x = this.getX();
        int y = this.getY();
        if (lit) {
            drawHighlight(g, x, y, this.width, this.height, this.textScale, this.alpha);
        }

        int color = !this.active ? VhsText.DISABLED : lit ? VhsText.YELLOW : VhsText.IDLE;
        float textY = y + (this.height - mc.font.lineHeight * this.textScale) / 2.0f;
        VhsText.draw(g, mc.font, this.getMessage(), x + textInset(this.textScale), textY, this.textScale,
                VhsText.withAlpha(color, Mth.clamp(this.alpha, 0.02f, 1.0f)), true);
    }

    /** The selected-row look: dark bar, yellow edge, play-arrow cursor (also used by the settings rows). */
    static void drawHighlight(GuiGraphics g, int x, int y, int width, int height, float textScale, float alpha) {
        g.fill(x, y, x + width, y + height, VhsText.withAlpha(0x000000, 0.55f * alpha));
        g.fill(x, y, x + 2, y + height, VhsText.withAlpha(VhsText.YELLOW, alpha));
        int size = Math.max(5, Math.round(7.0f * textScale)) | 1; // odd, so the tip is one pixel
        VhsText.playTriangle(g, x + 5, y + (height - size) / 2, size, VhsText.withAlpha(VhsText.YELLOW, alpha));
    }

    /** A short, quiet, high blip as the cursor moves onto a row. */
    static void blip(Minecraft mc) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.9f, 0.18f));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
