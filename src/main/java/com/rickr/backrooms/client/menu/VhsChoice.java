package com.rickr.backrooms.client.menu;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * A settings row that steps through a few choices (e.g. QUALITY: LOW / MEDIUM / HIGH): click (or
 * Enter) for the next one, right-click for the previous; with the row selected the left and right
 * arrow keys step too. The current choice is shown on the right, between little arrows.
 */
public class VhsChoice<E extends Enum<E>> extends AbstractButton implements SettingsScreen.Described {
    private final Component label;
    private final Component description;
    private final float textScale;
    private final E[] values;
    private final Supplier<E> getter;
    private final Consumer<E> setter;
    private final Function<E, Component> name;
    private boolean wasLit;
    private boolean firstFrame = true;

    public VhsChoice(int x, int y, int width, int height, float textScale, Component label, Component description,
                     E[] values, Supplier<E> getter, Consumer<E> setter, Function<E, Component> name) {
        super(x, y, width, height, label);
        this.label = label;
        this.description = description;
        this.textScale = textScale;
        this.values = values;
        this.getter = getter;
        this.setter = setter;
        this.name = name;
    }

    /** Set the choice (used by RESET). */
    public void setSettingValue(E value) {
        this.setter.accept(value);
    }

    private void step(int by) {
        int i = this.getter.get().ordinal();
        this.setter.accept(this.values[Math.floorMod(i + by, this.values.length)]);
    }

    @Override
    public void onPress() {
        this.step(1);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && this.active && this.visible && this.isMouseOver(mouseX, mouseY)) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            this.step(-1);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.active && this.isFocused() && (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT)) {
            this.step(keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : -1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        Component shown = VhsText.literal("< ").append(this.name.apply(this.getter.get())).append(VhsText.literal(" >"));
        int shownW = VhsText.width(mc.font, shown, this.textScale);
        VhsText.draw(g, mc.font, shown, x + this.width - 6 - shownW, textY, this.textScale, color, true);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
