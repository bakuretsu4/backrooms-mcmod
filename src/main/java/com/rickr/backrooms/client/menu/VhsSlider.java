package com.rickr.backrooms.client.menu;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A settings row with a slider, in the camcorder-menu style: the name on the left, the value on
 * the right, and a thin line underneath that fills up to a small marker. Drag or click it with the
 * mouse, or select it with Tab and use the left/right arrow keys (vanilla slider behaviour).
 * The setting changes as you drag, so you can watch the effect.
 */
public class VhsSlider extends AbstractSliderButton implements SettingsScreen.Described {
    private final Component label;
    private final Component description;
    private final float textScale;
    private final int min;
    private final int max;
    private final int step;
    private final IntConsumer setter;
    private final IntFunction<Component> format;
    private boolean wasLit;
    private boolean firstFrame = true;

    public VhsSlider(int x, int y, int width, int height, float textScale, Component label, Component description,
                     int min, int max, int step, IntSupplier getter, IntConsumer setter, IntFunction<Component> format) {
        super(x, y, width, height, label, (getter.getAsInt() - min) / (double) (max - min));
        this.label = label;
        this.description = description;
        this.textScale = textScale;
        this.min = min;
        this.max = max;
        this.step = step;
        this.setter = setter;
        this.format = format;
    }

    /** The slider position as a setting value, rounded to the step (e.g. 5%). */
    private int current() {
        int raw = (int) Math.round(this.min + this.value * (this.max - this.min));
        return Mth.clamp(Math.round(raw / (float) this.step) * this.step, this.min, this.max);
    }

    /** Move the slider to a value (used by RESET). */
    public void setSettingValue(int v) {
        this.value = Mth.clamp((v - this.min) / (double) (this.max - this.min), 0.0, 1.0);
        this.applyValue();
    }

    @Override
    protected void updateMessage() {
        // The value is drawn by renderWidget; the message stays the plain name (for narration).
        this.setMessage(this.label);
    }

    @Override
    protected void applyValue() {
        this.setter.accept(this.current());
    }

    @Override
    public Component description() {
        return this.description;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        boolean lit = this.active && this.isHoveredOrFocused();
        if (lit && !this.wasLit && !this.firstFrame) {
            VhsButton.blip(mc);
        }
        this.wasLit = lit;
        this.firstFrame = false;

        int x = this.getX();
        int y = this.getY();
        if (lit) {
            VhsButton.drawHighlight(g, x, y, this.width, this.height, this.textScale, this.alpha);
        }
        int color = VhsText.withAlpha(lit ? VhsText.YELLOW : VhsText.IDLE, this.alpha);
        int textX = x + VhsButton.textInset(this.textScale);
        float textY = y + 3;
        VhsText.draw(g, mc.font, this.label, textX, textY, this.textScale, color, true);
        Component shown = this.format.apply(this.current());
        int valueW = VhsText.width(mc.font, shown, this.textScale);
        VhsText.draw(g, mc.font, shown, x + this.width - 6 - valueW, textY, this.textScale, color, true);
        // The track: dim line, brighter filled part, and a small marker at the value.
        int trackX0 = trackX0();
        int trackX1 = trackX1();
        int trackY = y + this.height - 4;
        int fillX = trackX0 + (int) Math.round(this.value * (trackX1 - trackX0));
        g.fill(trackX0, trackY, trackX1, trackY + 1, VhsText.withAlpha(0xF2F2E8, 0.2f * this.alpha));
        g.fill(trackX0, trackY, fillX, trackY + 1, VhsText.withAlpha(lit ? VhsText.YELLOW : 0xF2F2E8, 0.7f * this.alpha));
        g.fill(fillX - 1, trackY - 2, fillX + 1, trackY + 3, color);
    }

    // Vanilla maps the mouse across the whole row; ours starts the track after the cursor arrow,
    // so clicks and drags are measured along the drawn track instead.
    private int trackX0() {
        return this.getX() + VhsButton.textInset(this.textScale);
    }

    private int trackX1() {
        return this.getX() + this.width - 6;
    }

    private void setFromMouse(double mouseX) {
        double v = Mth.clamp((mouseX - trackX0()) / (double) (trackX1() - trackX0()), 0.0, 1.0);
        if (v != this.value) {
            this.value = v;
            this.applyValue();
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.setFromMouse(mouseX);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        this.setFromMouse(mouseX);
    }
}
