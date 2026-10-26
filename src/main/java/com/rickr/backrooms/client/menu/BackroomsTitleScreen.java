package com.rickr.backrooms.client.menu;

import com.mojang.authlib.minecraft.BanDetails;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rickr.backrooms.Backrooms;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.ModListScreen;
import org.jetbrains.annotations.Nullable;

/**
 * Our main menu, shown instead of the vanilla title screen (see {@link MenuEvents}). It looks like
 * camcorder footage being played back: the level screenshots slideshow behind everything
 * ({@link FootageBackground}), the camcorder's on-screen display (REC, tape counter, battery,
 * date), the logo and a camcorder-style menu on the left, and a taped note on the right.
 * <p>
 * Layout is recalculated in {@link #init()}, which Minecraft calls again whenever the window is
 * resized or the GUI scale changes.
 */
public class BackroomsTitleScreen extends Screen {
    private static final ResourceLocation LOGO = FootageBackground.texture("logo");
    private static final ResourceLocation NOTE = FootageBackground.texture("note");
    private static final int LOGO_W = 1024;
    private static final int LOGO_H = 384;
    /** Where the "T" of the logo starts inside logo.png (it has a transparent border). */
    private static final int LOGO_INSET = 22;
    private static final int NOTE_W = 256;
    private static final int NOTE_H = 320;
    /** The ruled lines on note.png: the first one at y=52, then every 22 pixels, 12 lines in all. */
    private static final int NOTE_FIRST_LINE = 52;
    private static final int NOTE_LINE_STEP = 22;
    private static final int NOTE_ROWS = 12;
    /** The writing starts right of the red margin line and stops short of the right edge. */
    private static final int NOTE_TEXT_LEFT = 38;
    private static final int NOTE_TEXT_RIGHT = 240;
    private static final int NOTE_INK = 0xFF2A2F45;
    /** The note is hidden on narrow windows (in GUI pixels; 1080p at GUI scale 4 is 480 wide). */
    private static final int MIN_WIDTH_FOR_NOTE = 400;

    private static final int TAGLINE_COUNT = 8;
    private static final int NOTE_LINE_COUNT = 8;
    private static final long FADE_IN_MS = 1500L;
    /** Distance of the OSD text from the window edges. */
    private static final int MARGIN = 14;

    /** The fade from black only plays the first time the menu shows after the game starts. */
    private static boolean introFadeDone;

    private final RandomSource random = RandomSource.create();
    /** The tape counter runs from when this menu was opened. */
    private final long openedMs = Util.getMillis();
    private final Component tagline;
    private final Component modVersion;
    private final Component loaderVersion;
    private long fadeStartMs = -1L;

    // Layout, rebuilt by init().
    private int left;
    private float osdScale;
    private int logoX;
    private int logoY;
    private int logoW;
    private int logoH;
    private float taglineScale;
    private int taglineY;
    private List<FormattedCharSequence> taglineLines = List.of();
    private int noteX;
    private int noteY;
    private int noteW;
    private int noteH;
    private float noteTextScale;
    private List<FormattedCharSequence> noteRows = List.of();

    // Glitch timers for the logo and the tagline.
    private long nextLogoGlitchMs;
    private long logoGlitchUntilMs;
    private long taglineDropUntilMs;

    public BackroomsTitleScreen() {
        super(Component.translatable("menu.backrooms.title"));
        this.tagline = VhsText.translatable("menu.backrooms.tagline." + this.random.nextInt(TAGLINE_COUNT));
        this.modVersion = VhsText.translatable("menu.backrooms.version.mod", modVersion(Backrooms.MOD_ID));
        this.loaderVersion = VhsText.translatable("menu.backrooms.version.loader",
                SharedConstants.getCurrentVersion().getName(), modVersion("neoforge"));
        this.nextLogoGlitchMs = this.openedMs + 2000L + this.random.nextInt(4000);
    }

    private static String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("?");
    }

    @Override
    protected void init() {
        this.left = Math.max(10, Math.round(this.width * 0.06f));
        this.osdScale = this.height >= 360 ? 1.5f : 1.0f;
        float textScale = this.height >= 400 ? 2.0f : this.height >= 280 ? 1.5f : 1.0f;
        int rowH = Math.round(this.font.lineHeight * textScale) + 6;
        int gap = Math.max(6, Math.round(4.0f * textScale));

        // The menu entries: label + what happens when chosen.
        Component reason = this.multiplayerDisabledReason();
        List<Component> labels = List.of(
                VhsText.translatable("menu.backrooms.play"),
                VhsText.translatable("menu.backrooms.multiplayer"),
                VhsText.translatable("menu.backrooms.options"),
                VhsText.translatable("menu.backrooms.settings"),
                VhsText.translatable("menu.backrooms.mods"),
                VhsText.translatable("menu.backrooms.field_notes"),
                VhsText.translatable("menu.backrooms.quit"));
        List<Runnable> actions = List.of(
                () -> this.minecraft.setScreen(new SelectWorldScreen(this)),
                () -> this.minecraft.setScreen(this.minecraft.options.skipMultiplayerWarning
                        ? new JoinMultiplayerScreen(this) : new SafetyScreen(this)),
                () -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options)),
                () -> this.minecraft.setScreen(new SettingsScreen(this)),
                () -> this.minecraft.setScreen(new ModListScreen(this)),
                () -> this.minecraft.setScreen(new FieldNotesScreen(this)),
                () -> this.minecraft.stop());
        int buttonW = 0;
        for (Component label : labels) {
            buttonW = Math.max(buttonW, VhsButton.widthFor(this.font, label, textScale));
        }

        // The tagline wraps to at most half the window.
        this.taglineScale = textScale >= 2.0f ? 1.25f : 1.0f;
        int taglineMaxW = Math.max(100, Math.min(Math.round(this.width * 0.5f), this.width - this.left - MARGIN));
        this.taglineLines = this.font.split(this.tagline, Math.max(20, (int) (taglineMaxW / this.taglineScale)));
        int taglineLineH = Math.round((this.font.lineHeight + 1) * this.taglineScale);
        int taglineH = this.taglineLines.size() * taglineLineH;

        // Fit logo + menu + tagline between the caption at the top and the version text at the bottom.
        int top = MARGIN + Math.round(12 * this.osdScale);
        int bottom = this.height - MARGIN - 24;
        int fixedH = rowH * labels.size() + gap + taglineH;
        int maxLogoH = bottom - top - fixedH - gap;
        this.logoW = Math.round(this.width * 0.42f);
        this.logoH = this.logoW * LOGO_H / LOGO_W;
        if (this.logoH > maxLogoH) {
            this.logoH = Math.max(0, maxLogoH);
            this.logoW = this.logoH * LOGO_W / LOGO_H;
        }
        if (this.logoH < 20) {
            this.logoW = this.logoH = 0; // a tiny window: no room for the logo
        }
        int blockH = (this.logoH > 0 ? this.logoH + gap : 0) + fixedH;
        int y = top + Math.max(0, (bottom - top - blockH) * 2 / 5); // a little above the middle

        this.logoX = this.left - this.logoW * LOGO_INSET / LOGO_W;
        this.logoY = y;
        if (this.logoH > 0) {
            y += this.logoH + gap;
        }

        // The cursor arrow hangs left of the column so the labels line up with the logo.
        int buttonX = Math.max(2, this.left - VhsButton.textInset(textScale));
        for (int i = 0; i < labels.size(); i++) {
            VhsButton button = this.addRenderableWidget(new VhsButton(buttonX, y, buttonW, rowH, labels.get(i), textScale, actions.get(i)));
            if (i == 1 && reason != null) {
                button.active = false;
                button.setTooltip(Tooltip.create(reason));
            }
            y += rowH;
        }
        this.taglineY = y + gap;

        this.addCopyright();
        this.layoutNote(Math.max(buttonX + buttonW, Math.max(this.logoX + this.logoW, this.left + taglineMaxW)));
    }

    /** The "Copyright Mojang AB. Do not distribute!" line vanilla shows (click it for the credits). */
    private void addCopyright() {
        Component copyright = VhsText.translatable("title.credits").withStyle(style -> style.withColor(VhsText.GREY & 0xFFFFFF));
        int w = this.font.width(copyright);
        int dateH = Math.round(this.font.lineHeight * this.osdScale);
        int x = this.width - MARGIN - w;
        int y = this.height - MARGIN - dateH - 11;
        // On narrow windows it would run into the version text: move it up a line.
        int versionRight = MARGIN + Math.max(this.font.width(this.modVersion), this.font.width(this.loaderVersion));
        if (x < versionRight + 8) {
            y -= 22;
        }
        this.addRenderableWidget(new PlainTextButton(x, y, w, 10, copyright,
                button -> this.minecraft.setScreen(new CreditsAndAttributionScreen(this)), this.font));
    }

    /** Places the taped note under the top-right OSD and wraps its text onto the ruled lines. */
    private void layoutNote(int menuRight) {
        this.noteW = 0;
        this.noteRows = List.of();
        if (this.width < MIN_WIDTH_FOR_NOTE) {
            return;
        }
        int top = MARGIN + Math.round(36 * this.osdScale);
        int w = Math.round(this.width * 0.25f);
        int h = w * NOTE_H / NOTE_W;
        int maxH = this.height - top - MARGIN - 40; // keep clear of the date and copyright
        if (h > maxH) {
            h = maxH;
            w = h * NOTE_W / NOTE_H;
        }
        int x = this.width - MARGIN - 8 - w;
        if (w < 110 || x < menuRight + 10) {
            return;
        }
        this.noteX = x;
        this.noteY = top;
        this.noteW = w;
        this.noteH = h;

        // Biggest text size that still fits the 12 ruled lines (and isn't taller than a line).
        float rowSpacing = h * (float) NOTE_LINE_STEP / NOTE_H;
        int areaW = w * (NOTE_TEXT_RIGHT - NOTE_TEXT_LEFT) / NOTE_W;
        List<FormattedCharSequence> rows = new ArrayList<>();
        float scale = Math.min(1.5f, rowSpacing * 0.85f / this.font.lineHeight);
        for (; scale >= 0.5f; scale -= 0.05f) {
            rows = new ArrayList<>();
            for (int i = 0; i < NOTE_LINE_COUNT; i++) {
                rows.addAll(this.font.split(VhsText.translatable("menu.backrooms.note." + i), Math.max(10, (int) (areaW / scale))));
            }
            if (rows.size() <= NOTE_ROWS) {
                break;
            }
        }
        this.noteTextScale = Math.max(0.5f, scale);
        this.noteRows = rows.size() > NOTE_ROWS ? rows.subList(0, NOTE_ROWS) : rows;
    }

    /** Same checks as the vanilla title screen: multiplayer can be off (e.g. a banned account). */
    @Nullable
    private Component multiplayerDisabledReason() {
        if (this.minecraft.allowsMultiplayer()) {
            return null;
        }
        if (this.minecraft.isNameBanned()) {
            return Component.translatable("title.multiplayer.disabled.banned.name");
        }
        BanDetails ban = this.minecraft.multiplayerBan();
        if (ban != null) {
            return ban.expires() != null
                    ? Component.translatable("title.multiplayer.disabled.banned.temporary")
                    : Component.translatable("title.multiplayer.disabled.banned.permanent");
        }
        return Component.translatable("title.multiplayer.disabled");
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        FootageBackground.render(g, this.width, this.height);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        super.render(g, mouseX, mouseY, partialTick); // the footage + the menu buttons
        this.renderLogo(g, now);
        this.renderTagline(g, now);
        this.renderNote(g);
        this.renderOsd(g, now);
        this.renderVersion(g);
        this.renderFadeIn(g, now);
    }

    /** The logo with a faint red/cyan double image (like a worn tape) that jumps now and then. */
    private void renderLogo(GuiGraphics g, long now) {
        if (this.logoW <= 0) {
            return;
        }
        if (now >= this.nextLogoGlitchMs) {
            this.logoGlitchUntilMs = now + 120L + this.random.nextInt(160);
            this.nextLogoGlitchMs = now + 3000L + this.random.nextInt(5000);
        }
        boolean glitching = now < this.logoGlitchUntilMs;
        int split = glitching ? 2 + this.random.nextInt(2) : 1;
        int jitter = glitching ? this.random.nextInt(3) - 1 : 0;

        FootageBackground.prepare(LOGO, true, false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1.0f, 0.2f, 0.2f, 0.35f);
        this.blitLogo(g, this.logoX - split, this.logoY);
        g.setColor(0.2f, 1.0f, 1.0f, 0.35f);
        this.blitLogo(g, this.logoX + split, this.logoY + (glitching ? 1 : 0));
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        this.blitLogo(g, this.logoX + jitter, this.logoY);
        RenderSystem.disableBlend();
    }

    private void blitLogo(GuiGraphics g, int x, int y) {
        g.blit(LOGO, x, y, this.logoW, this.logoH, 0.0f, 0.0f, LOGO_W, LOGO_H, LOGO_W, LOGO_H);
    }

    /** The tagline under the menu, flickering like a dying tube light. */
    private void renderTagline(GuiGraphics g, long now) {
        if (now >= this.taglineDropUntilMs && this.random.nextFloat() < 0.02f) {
            this.taglineDropUntilMs = now + 50L + this.random.nextInt(90);
        }
        float alpha = now < this.taglineDropUntilMs ? 0.25f : 0.72f + 0.13f * Mth.sin(now / 700.0f);
        int color = VhsText.withAlpha(VhsText.DIM_YELLOW, alpha);
        int y = this.taglineY;
        for (FormattedCharSequence line : this.taglineLines) {
            VhsText.draw(g, this.font, line, this.left, y, this.taglineScale, color, true);
            y += Math.round((this.font.lineHeight + 1) * this.taglineScale);
        }
    }

    /** The taped note with the lore written on its ruled lines. */
    private void renderNote(GuiGraphics g) {
        if (this.noteW <= 0) {
            return;
        }
        g.fill(this.noteX + 3, this.noteY + 4, this.noteX + this.noteW + 3, this.noteY + this.noteH + 4, 0x66000000);
        FootageBackground.prepare(NOTE, true, false);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        g.blit(NOTE, this.noteX, this.noteY, this.noteW, this.noteH, 0.0f, 0.0f, NOTE_W, NOTE_H, NOTE_W, NOTE_H);

        float textX = this.noteX + this.noteW * (float) NOTE_TEXT_LEFT / NOTE_W;
        for (int row = 0; row < this.noteRows.size(); row++) {
            float lineY = this.noteY + (NOTE_FIRST_LINE + NOTE_LINE_STEP * row) * (float) this.noteH / NOTE_H;
            float textY = lineY - (this.font.lineHeight - 1) * this.noteTextScale;
            int wobble = (row * 5) % 3 - 1; // hand-written lines never start in quite the same place
            VhsText.draw(g, this.font, this.noteRows.get(row), textX + wobble, textY, this.noteTextScale, NOTE_INK, false);
        }
    }

    /** The camcorder's on-screen display: viewfinder marks, PLAY + scene, REC, tape counter, battery, date. */
    private void renderOsd(GuiGraphics g, long now) {
        float s = this.osdScale;
        int lineH = Math.round(this.font.lineHeight * s);
        VhsText.brackets(g, 6, 6, this.width - 6, this.height - 6, Math.round(12 * s), 0xCCF2F2E8);

        // Top left: "> PLAY  LEVEL 0".
        int triangle = Math.max(5, Math.round(6 * s)) | 1;
        VhsText.playTriangle(g, MARGIN, MARGIN + (lineH - triangle) / 2, triangle, VhsText.WHITE);
        Component caption = VhsText.translatable("menu.backrooms.osd.play").append("  ").append(FootageBackground.caption());
        VhsText.draw(g, this.font, caption, MARGIN + triangle + 4, MARGIN, s, VhsText.WHITE, true);

        // Top right: blinking REC, the tape counter and the battery.
        int right = this.width - MARGIN;
        Component rec = VhsText.translatable("menu.backrooms.osd.rec");
        int recW = VhsText.width(this.font, rec, s);
        VhsText.draw(g, this.font, rec, right - recW, MARGIN, s, VhsText.WHITE, true);
        if ((now - this.openedMs) % 1000L < 500L) {
            int dot = Math.round(6 * s);
            VhsText.dot(g, right - recW - dot - Math.round(3 * s), MARGIN + (lineH - dot) / 2, dot, VhsText.RED);
        }
        Component counter = VhsText.translatable("menu.backrooms.osd.sp").append("  " + timecode(now - this.openedMs));
        VhsText.draw(g, this.font, counter, right - VhsText.width(this.font, counter, s), MARGIN + lineH + 2, s, VhsText.WHITE, true);
        this.battery(g, right, MARGIN + 2 * (lineH + 2) + 1, s);

        // Bottom right: camcorder date stamp, set to 1996.
        String date = dateStamp(LocalDateTime.now());
        Component dateText = VhsText.literal(date);
        VhsText.draw(g, this.font, dateText, right - VhsText.width(this.font, dateText, s), this.height - MARGIN - lineH, s, VhsText.WHITE, true);
    }

    /** A small battery icon (3 of 4 bars) with its right edge at {@code right}. */
    private void battery(GuiGraphics g, int right, int y, float s) {
        int w = Math.round(16 * s);
        int h = Math.round(8 * s);
        int nub = Math.max(1, Math.round(2 * s));
        int x = right - nub - w;
        int c = VhsText.WHITE;
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
        g.fill(x + w, y + h / 4, x + w + nub, y + h - h / 4, c);
        int barW = (w - 4 - 3) / 4;
        for (int i = 0; i < 3; i++) {
            int bx = x + 2 + i * (barW + 1);
            g.fill(bx, y + 2, bx + barW, y + h - 2, c);
        }
    }

    /** Bottom left: mod, Minecraft and NeoForge versions. */
    private void renderVersion(GuiGraphics g) {
        int y = this.height - MARGIN - this.font.lineHeight;
        g.drawString(this.font, this.loaderVersion, MARGIN, y, VhsText.GREY, true);
        g.drawString(this.font, this.modVersion, MARGIN, y - this.font.lineHeight - 2, VhsText.IDLE, true);
    }

    /** The first time the menu appears it fades in from black. */
    private void renderFadeIn(GuiGraphics g, long now) {
        if (introFadeDone) {
            return;
        }
        if (this.fadeStartMs < 0L) {
            this.fadeStartMs = now;
        }
        float t = (now - this.fadeStartMs) / (float) FADE_IN_MS;
        if (t >= 1.0f) {
            introFadeDone = true;
            return;
        }
        g.fill(0, 0, this.width, this.height, VhsText.withAlpha(0x000000, 1.0f - t));
    }

    /** Tape counter, H:MM:SS. */
    private static String timecode(long ms) {
        long seconds = ms / 1000L;
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600L, (seconds / 60L) % 60L, seconds % 60L);
    }

    /** Today's month and day, the year 1996 and the real time, e.g. "OCT 25 1996  3:07 AM". */
    private static String dateStamp(LocalDateTime now) {
        String month = now.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ENGLISH);
        int hour = now.getHour() % 12 == 0 ? 12 : now.getHour() % 12;
        return String.format(Locale.ROOT, "%s %d 1996  %d:%02d %s",
                month, now.getDayOfMonth(), hour, now.getMinute(), now.getHour() < 12 ? "AM" : "PM");
    }

    /** It's the root screen: Esc does nothing. */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
