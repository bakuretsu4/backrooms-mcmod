package com.rickr.backrooms.client.menu;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * "FIELD NOTES" from the main menu: a scrollable beginner's guide to the mod (what the Backrooms
 * are, how to get in and out, the levels, the monsters, survival tips) over the same animated
 * footage as the title screen. The text lives in the lang file under {@code menu.backrooms.notes.*};
 * paragraphs starting with "- " are drawn as bullet points.
 * <p>
 * Scroll with the mouse wheel, the scrollbar, or the arrow / Page Up / Page Down / Home / End keys.
 * BACK or Esc returns to the menu.
 */
public class FieldNotesScreen extends Screen {
    /** Each section: lang key id and how many paragraphs it has (….title, ….0, ….1, …). */
    private static final List<Section> SECTIONS = List.of(
            new Section("what", 1),
            new Section("in", 1),
            new Section("goal", 3),
            new Section("levels", 9),
            new Section("monsters", 4),
            new Section("survival", 7),
            new Section("multiplayer", 1));

    private static final int ROW_H = 11;
    private static final int BULLET_INDENT = 10;
    private static final int PANEL_PADDING = 10;
    private static final int SCROLLBAR_W = 3;
    private static final int PANEL_COLOR = 0xC80C0C0A;
    private static final int BORDER_COLOR = 0x50F2F2E8;

    private final Screen parent;
    /** How far the text is scrolled, in GUI pixels (kept across resizes). */
    private double scroll;
    private boolean draggingScrollbar;

    // Layout, rebuilt by init().
    private float titleScale;
    private int panelX0;
    private int panelX1;
    private int panelY0;
    private int panelY1;
    private int viewY0;
    private int viewY1;
    private final List<Row> rows = new ArrayList<>();

    public FieldNotesScreen(Screen parent) {
        super(Component.translatable("menu.backrooms.notes.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.titleScale = this.height >= 300 ? 2.0f : 1.5f;
        float buttonScale = this.height >= 300 ? 1.5f : 1.0f;
        int buttonH = Math.round(this.font.lineHeight * buttonScale) + 6;

        int panelW = Math.min(this.width - 24, Math.max(300, Math.round(this.width * 0.55f)));
        this.panelX0 = (this.width - panelW) / 2;
        this.panelX1 = this.panelX0 + panelW;
        this.panelY0 = 12 + Math.round(this.font.lineHeight * this.titleScale) + 8;
        int buttonY = this.height - 8 - buttonH;
        this.panelY1 = buttonY - 6;
        this.viewY0 = this.panelY0 + PANEL_PADDING / 2;
        this.viewY1 = this.panelY1 - PANEL_PADDING / 2;

        Component back = VhsText.translatable("menu.backrooms.back");
        int buttonX = Math.max(2, this.panelX0 - VhsButton.textInset(buttonScale));
        this.addRenderableWidget(new VhsButton(buttonX, buttonY, VhsButton.widthFor(this.font, back, buttonScale), buttonH,
                back, buttonScale, this::onClose));

        this.layoutText();
        this.setScroll(this.scroll);
    }

    /** Word-wraps all the sections to the panel width. */
    private void layoutText() {
        this.rows.clear();
        int textW = this.panelX1 - this.panelX0 - 2 * PANEL_PADDING - SCROLLBAR_W - 4;
        for (Section section : SECTIONS) {
            if (!this.rows.isEmpty()) {
                this.rows.add(Row.BLANK);
            }
            String prefix = "menu.backrooms.notes." + section.id() + ".";
            for (FormattedCharSequence line : this.font.split(VhsText.translatable(prefix + "title"), textW)) {
                this.rows.add(new Row(line, 0, VhsText.YELLOW, false));
            }
            for (int i = 0; i < section.paragraphs(); i++) {
                String text = Component.translatable(prefix + i).getString();
                boolean bullet = text.startsWith("- ");
                if (bullet) {
                    text = text.substring(2);
                }
                int indent = bullet ? BULLET_INDENT : 0;
                List<FormattedCharSequence> lines = this.font.split(VhsText.literal(text), Math.max(20, textW - indent));
                for (int l = 0; l < lines.size(); l++) {
                    this.rows.add(new Row(lines.get(l), indent, VhsText.IDLE, bullet && l == 0));
                }
            }
        }
    }

    private int contentHeight() {
        return this.rows.size() * ROW_H;
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight() - (this.viewY1 - this.viewY0));
    }

    private void setScroll(double value) {
        this.scroll = Mth.clamp(value, 0.0, this.maxScroll());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        FootageBackground.render(g, this.width, this.height);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); // the footage + the BACK button

        // Title.
        Component heading = VhsText.translatable("menu.backrooms.notes.title");
        int titleW = VhsText.width(this.font, heading, this.titleScale);
        VhsText.draw(g, this.font, heading, (this.width - titleW) / 2.0f, 12, this.titleScale, VhsText.YELLOW, true);

        // Dark panel with a thin light border.
        g.fill(this.panelX0, this.panelY0, this.panelX1, this.panelY1, PANEL_COLOR);
        g.fill(this.panelX0, this.panelY0, this.panelX1, this.panelY0 + 1, BORDER_COLOR);
        g.fill(this.panelX0, this.panelY1 - 1, this.panelX1, this.panelY1, BORDER_COLOR);
        g.fill(this.panelX0, this.panelY0, this.panelX0 + 1, this.panelY1, BORDER_COLOR);
        g.fill(this.panelX1 - 1, this.panelY0, this.panelX1, this.panelY1, BORDER_COLOR);

        // The text, clipped to the panel.
        int textX = this.panelX0 + PANEL_PADDING;
        g.enableScissor(this.panelX0 + 1, this.viewY0, this.panelX1 - 1, this.viewY1);
        int y = this.viewY0 + 2 - (int) Math.round(this.scroll);
        for (Row row : this.rows) {
            if (row.text() != null && y + ROW_H > this.viewY0 && y < this.viewY1) {
                if (row.bullet()) {
                    g.fill(textX + 2, y + 3, textX + 5, y + 6, VhsText.YELLOW);
                }
                g.drawString(this.font, row.text(), textX + row.indent(), y, row.color(), true);
            }
            y += ROW_H;
        }
        g.disableScissor();

        // Scrollbar (only when there is something to scroll).
        int max = this.maxScroll();
        if (max > 0) {
            int trackX = this.scrollbarX();
            int viewH = this.viewY1 - this.viewY0;
            int thumbH = this.thumbHeight();
            int thumbY = this.viewY0 + (int) Math.round(this.scroll / max * (viewH - thumbH));
            g.fill(trackX, this.viewY0, trackX + SCROLLBAR_W, this.viewY1, 0x40F2F2E8);
            int thumbColor = this.draggingScrollbar || this.isOverScrollbar(mouseX, mouseY) ? VhsText.YELLOW : 0xFFB8B8B0;
            g.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, thumbColor);
        }
    }

    private int scrollbarX() {
        return this.panelX1 - PANEL_PADDING / 2 - SCROLLBAR_W;
    }

    private int thumbHeight() {
        int viewH = this.viewY1 - this.viewY0;
        return Mth.clamp(viewH * viewH / Math.max(1, this.contentHeight()), 16, viewH);
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        int x = this.scrollbarX();
        return mouseX >= x - 3 && mouseX < x + SCROLLBAR_W + 3 && mouseY >= this.viewY0 && mouseY < this.viewY1;
    }

    /** Scrolls so the scrollbar thumb is centred on the mouse. */
    private void dragScrollbarTo(double mouseY) {
        int viewH = this.viewY1 - this.viewY0;
        int thumbH = this.thumbHeight();
        double fraction = (mouseY - this.viewY0 - thumbH / 2.0) / Math.max(1, viewH - thumbH);
        this.setScroll(fraction * this.maxScroll());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.setScroll(this.scroll - scrollY * ROW_H * 3);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.maxScroll() > 0 && this.isOverScrollbar(mouseX, mouseY)) {
            this.draggingScrollbar = true;
            this.dragScrollbarTo(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingScrollbar) {
            this.dragScrollbarTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.draggingScrollbar = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int page = Math.max(ROW_H, this.viewY1 - this.viewY0 - ROW_H);
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> this.setScroll(this.scroll - ROW_H);
            case GLFW.GLFW_KEY_DOWN -> this.setScroll(this.scroll + ROW_H);
            case GLFW.GLFW_KEY_PAGE_UP -> this.setScroll(this.scroll - page);
            case GLFW.GLFW_KEY_PAGE_DOWN -> this.setScroll(this.scroll + page);
            case GLFW.GLFW_KEY_HOME -> this.setScroll(0.0);
            case GLFW.GLFW_KEY_END -> this.setScroll(this.maxScroll());
            default -> {
                // Esc (back to the menu), Tab, Enter on the BACK button...
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Section(String id, int paragraphs) {}

    /** One line of text; {@code text == null} is an empty line between sections. */
    private record Row(FormattedCharSequence text, int indent, int color, boolean bullet) {
        static final Row BLANK = new Row(null, 0, 0, false);
    }
}
