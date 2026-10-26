package com.rickr.backrooms.client.menu;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** A settings row that switches something ON or OFF when clicked (or Enter/Space when selected). */
public class VhsToggle extends AbstractButton implements SettingsScreen.Described {
    private final Component label;
    private final Component description;
    private final float textScale;
    private final Supplier<Boolean> getter;
    private final Consumer<Boolean> setter;
    private boolean wasLit;
    private boolean firstFrame = true;

    public VhsToggle(int x, int y, int width, int height, float textScale, Component label, Component description,
                     Supplier<Boolean> getter, Consumer<Boolean> setter) {
        super(x, y, width, height, label);
        this.label = label;
        this.description = description;
        this.textScale = textScale;
        this.getter = getter;
        this.setter = setter;
    }

    /** Set the switch (used by RESET). */
    public void setSettingValue(boolean on) {
        this.setter.accept(on);
    }

    @Override
    public void onPress() {
        this.setter.accept(!this.getter.get());
    }

    @Override
    public Component description() {
        return this.description;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
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
        float textY = y + (this.height - mc.font.lineHeight * this.textScale) / 2.0f;
        VhsText.draw(g, mc.font, this.label, x + VhsButton.textInset(this.textScale), textY, this.textScale, color, true);
        boolean on = this.getter.get();
        Component state = VhsText.translatable(on ? "menu.backrooms.settings.on" : "menu.backrooms.settings.off");
        int stateColor = on ? color : VhsText.withAlpha(VhsText.GREY, this.alpha);
        int stateW = VhsText.width(mc.font, state, this.textScale);
        VhsText.draw(g, mc.font, state, x + this.width - 6 - stateW, textY, this.textScale, stateColor, true);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
