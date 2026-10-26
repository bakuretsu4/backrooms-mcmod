package com.rickr.backrooms.client.menu;

import com.rickr.backrooms.config.ClientSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * SETTINGS: the camcorder feel, adjustable per player (saved in config/backrooms-client.toml).
 * Opened from the main menu, the pause menu and the Mods list. Every change applies at once, so
 * from the pause menu you can close it and see; BACK (or Esc) saves.
 *
 * <p>To add a setting: define it in {@link ClientSettings}, add a row in {@link #init}, and give it
 * a name and a one-line description in the lang file (menu.backrooms.settings.*).
 */
public class SettingsScreen extends Screen {
    /** A row that can explain itself at the bottom of the panel when you point at it. */
    public interface Described {
        Component description();
    }

    private static final int PANEL_COLOR = 0xC80C0C0A;
    private static final int BORDER_COLOR = 0x50F2F2E8;
    private static final int PADDING = 8;
    /** Lines reserved under the rows for the description; longer ones are cut off (keep them short). */
    private static final int DESCRIPTION_LINES = 3;

    private final Screen parent;

    // Layout, rebuilt by init().
    private float titleScale;
    private float textScale;
    private int panelX0;
    private int panelX1;
    private int panelY0;
    private int titleY;
    private int panelY1;
    private int descriptionY;
    private final List<Heading> headings = new ArrayList<>();

    public SettingsScreen(Screen parent) {
        super(Component.translatable("menu.backrooms.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.headings.clear();
        // Short windows (1080p at the automatic GUI scale is only 270 tall) get a tighter layout.
        boolean compact = this.height < 300;
        this.titleScale = compact ? 1.5f : 2.0f;
        this.titleY = compact ? 6 : 12;
        // Big text on big windows; small windows get small text so everything still fits.
        this.textScale = this.height >= 360 ? 1.5f : 1.0f;
        int lineH = Math.round(this.font.lineHeight * this.textScale);
        int sliderH = lineH + (compact ? 8 : 10);
        int toggleH = lineH + (compact ? 4 : 6);
        int headingH = this.font.lineHeight + (compact ? 3 : 8);
        int buttonScaleH = lineH + 6;

        int panelW = Math.min(this.width - 24, Math.max(280, Math.round(this.width * 0.5f)));
        this.panelX0 = (this.width - panelW) / 2;
        this.panelX1 = this.panelX0 + panelW;
        this.panelY0 = this.titleY + Math.round(this.font.lineHeight * this.titleScale) + (compact ? 5 : 8);
        int rowX = this.panelX0 + 4;
        int rowW = panelW - 8;
        int y = this.panelY0 + (compact ? 4 : PADDING);

        // --- Camera --------------------------------------------------------------------------
        this.headings.add(new Heading(VhsText.translatable("menu.backrooms.settings.camera"), y));
        y += headingH;
        // Ranges match ClientSettings (smoothing 0-100, the others 0-200 percent).
        y = this.slider(rowX, y, rowW, sliderH, "smoothing", ClientSettings.CAMERA_SMOOTHING, 0, 100, 5);
        y = this.slider(rowX, y, rowW, sliderH, "shake", ClientSettings.HANDHELD_SHAKE, 0, 200, 10);
        y = this.slider(rowX, y, rowW, sliderH, "tilt", ClientSettings.VIEW_TILT, 0, 200, 10);
        y = this.slider(rowX, y, rowW, sliderH, "motion_blur", ClientSettings.MOTION_BLUR, 0, 200, 10);
        y = this.toggle(rowX, y, rowW, toggleH, "wide_lens", ClientSettings.WIDE_LENS);

        // --- HUD -----------------------------------------------------------------------------
        y += compact ? 2 : 4;
        this.headings.add(new Heading(VhsText.translatable("menu.backrooms.settings.hud"), y));
        y += headingH;
        y = this.toggle(rowX, y, rowW, toggleH, "stamina_bar", ClientSettings.STAMINA_BAR);

        // Room for a description of up to three lines under the rows.
        this.descriptionY = y + (compact ? 3 : 6);
        this.panelY1 = this.descriptionY + DESCRIPTION_LINES * (this.font.lineHeight + 2) + (compact ? 3 : PADDING);

        // RESET on the left, BACK on the right, under the panel.
        int buttonY = Math.min(this.height - 4 - buttonScaleH, this.panelY1 + (compact ? 2 : 6));
        Component reset = VhsText.translatable("menu.backrooms.settings.reset");
        Component back = VhsText.translatable("menu.backrooms.back");
        this.addRenderableWidget(new VhsButton(this.panelX0, buttonY, VhsButton.widthFor(this.font, reset, this.textScale),
                buttonScaleH, reset, this.textScale, this::resetToDefaults));
        int backW = VhsButton.widthFor(this.font, back, this.textScale);
        this.addRenderableWidget(new VhsButton(this.panelX1 - backW, buttonY, backW, buttonScaleH, back, this.textScale,
                this::onClose));
    }

    private int slider(int x, int y, int w, int h, String key, ModConfigSpec.IntValue setting, int min, int max, int step) {
        this.addRenderableWidget(new VhsSlider(x, y, w, h, this.textScale,
                VhsText.translatable("menu.backrooms.settings." + key),
                Component.translatable("menu.backrooms.settings." + key + ".info"),
                min, max, step, setting::get, setting::set,
                v -> v == 0 ? VhsText.translatable("menu.backrooms.settings.off") : VhsText.literal(v + "%")));
        return y + h;
    }

    private int toggle(int x, int y, int w, int h, String key, ModConfigSpec.BooleanValue setting) {
        this.addRenderableWidget(new VhsToggle(x, y, w, h, this.textScale,
                VhsText.translatable("menu.backrooms.settings." + key),
                Component.translatable("menu.backrooms.settings." + key + ".info"),
                setting::get, setting::set));
        return y + h;
    }

    private void resetToDefaults() {
        ClientSettings.CAMERA_SMOOTHING.set(ClientSettings.CAMERA_SMOOTHING.getDefault());
        ClientSettings.HANDHELD_SHAKE.set(ClientSettings.HANDHELD_SHAKE.getDefault());
        ClientSettings.VIEW_TILT.set(ClientSettings.VIEW_TILT.getDefault());
        ClientSettings.MOTION_BLUR.set(ClientSettings.MOTION_BLUR.getDefault());
        ClientSettings.WIDE_LENS.set(ClientSettings.WIDE_LENS.getDefault());
        ClientSettings.STAMINA_BAR.set(ClientSettings.STAMINA_BAR.getDefault());
        this.rebuildWidgets(); // the sliders read their position when they are made
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft.level == null) {
            FootageBackground.render(g, this.width, this.height); // main menu: the same footage
        } else {
            this.renderTransparentBackground(g); // in a world: darken, but keep it visible
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        Component heading = VhsText.translatable("menu.backrooms.settings.title");
        int titleW = VhsText.width(this.font, heading, this.titleScale);
        VhsText.draw(g, this.font, heading, (this.width - titleW) / 2.0f, this.titleY, this.titleScale, VhsText.YELLOW, true);

        // Dark panel with a thin light border (like FIELD NOTES).
        g.fill(this.panelX0, this.panelY0, this.panelX1, this.panelY1, PANEL_COLOR);
        g.fill(this.panelX0, this.panelY0, this.panelX1, this.panelY0 + 1, BORDER_COLOR);
        g.fill(this.panelX0, this.panelY1 - 1, this.panelX1, this.panelY1, BORDER_COLOR);
        g.fill(this.panelX0, this.panelY0, this.panelX0 + 1, this.panelY1, BORDER_COLOR);
        g.fill(this.panelX1 - 1, this.panelY0, this.panelX1, this.panelY1, BORDER_COLOR);
        for (Heading h : this.headings) {
            VhsText.draw(g, this.font, h.text(), this.panelX0 + PADDING, h.y() + 3, 1.0f, VhsText.DIM_YELLOW, true);
        }

        // The rows and buttons (not super.render: that would draw the background a second time).
        for (var renderable : this.renderables) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }

        // What the row under the mouse (or the selected one) does.
        Described described = null;
        for (GuiEventListener child : this.children()) {
            if (child instanceof Described d && child.isMouseOver(mouseX, mouseY)) {
                described = d;
            }
        }
        if (described == null && this.getFocused() instanceof Described d) {
            described = d;
        }
        if (described != null) {
            int textW = this.panelX1 - this.panelX0 - 2 * PADDING;
            int y = this.descriptionY;
            List<FormattedCharSequence> lines = this.font.split(described.description(), textW);
            for (int i = 0; i < Math.min(lines.size(), DESCRIPTION_LINES); i++) {
                g.drawString(this.font, lines.get(i), this.panelX0 + PADDING, y, VhsText.GREY, true);
                y += this.font.lineHeight + 2;
            }
        }
    }

    /** BACK / Esc: write the settings to disk and go back. */
    @Override
    public void onClose() {
        ClientSettings.SPEC.save();
        this.minecraft.setScreen(this.parent);
    }

    private record Heading(Component text, int y) {}
}
