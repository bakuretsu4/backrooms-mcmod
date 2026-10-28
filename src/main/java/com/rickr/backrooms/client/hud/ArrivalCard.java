package com.rickr.backrooms.client.hud;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.intro.NoclipTransition;
import com.rickr.backrooms.network.ArrivalCardPayload;
import com.rickr.backrooms.world.Travel;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * The camcorder "title card" shown when you arrive on a level, instead of the old chat lines that
 * sat over the hearts in Minecraft's own font. In the shared camcorder look ({@link OsdStyle}),
 * lower left, above everything the HUD keeps at the bottom of the screen:
 * <pre>
 *   ■ YOU CLIPPED THROUGH REALITY.      how you got here (only some arrivals), muted amber
 *   LEVEL 0                              the level's name, large
 *   ───────
 *   Old yellow wallpaper, damp carpet    its one-line description, wrapped
 *   and the buzz of the lights.
 * </pre>
 * It waits until the datamosh between levels has finished and you've had a moment to see the new
 * place, comes up row by row, holds, and fades away: about six seconds in all. Every letter has a
 * thin dark edge and the card a very faint dark backing, so it reads on Level 37's white tiles as
 * well as in Level 2's dark. Nothing goes to chat: the card alone carries the message.
 */
public final class ArrivalCard implements LayeredDraw.Layer {
    public static final ResourceLocation LAYER_ID = Backrooms.id("arrival_card");

    // ---- Timing (ms) ---------------------------------------------------------------------------
    /** Once the picture is back from the datamosh, wait this long: you see the place first. */
    private static final long SETTLE_MS = 700L;
    /** Each row comes up this long after the one above it (note, then name, then description). */
    private static final long STAGGER_MS = 160L;
    /** One row's fade in. */
    private static final float IN_MS = 600.0f;
    /** The whole card is fully up until this long after it began... */
    private static final long HOLD_UNTIL_MS = 4300L;
    /** ...then fades out together over this long: about 5.7 s on screen in all. */
    private static final float OUT_MS = 1400.0f;
    /** A card that never got to start (the HUD hidden with F1 the whole time) is dropped after this. */
    private static final long STALE_MS = 20000L;
    /** For the first moments the card jumps a pixel side to side, like a tape settling. */
    private static final long SETTLE_JITTER_MS = 150L;

    // ---- Look ----------------------------------------------------------------------------------
    /** The level's name is drawn this big (VT323's capitals are then about 12 GUI pixels tall). */
    private static final float TITLE_SCALE = 2.0f;
    /** The card's opacity: a little stronger than a readout (it's an event, not a gauge). */
    private static final float ALPHA = 0.9f;
    /** The description sits a bit under the name. */
    private static final float LINE_ALPHA = 0.85f;
    /**
     * GUI pixels kept free above the bottom of the screen: the hotbar (22), the hearts, armour and
     * air rows above it, the held item's name (59 up) and the action-bar line (72 up).
     */
    private static final int HUD_CLEARANCE = 80;
    /** Line spacing of the small VHS text. */
    private static final int LINE = 10;
    /** Notes are indented past their small status lamp. */
    private static final int NOTE_INDENT = 8;
    /** The faint dark backing: three nested boxes, this opacity each, so its edge is soft. */
    private static final float SCRIM_STEP = 0.075f;
    private static final int SCRIM_FEATHER = 3;
    /** Text wraps at this share of the screen width (never narrower than {@link #MIN_WRAP} pixels). */
    private static final float WRAP_SHARE = 0.46f;
    private static final int MIN_WRAP = 120;

    /** The card on screen or waiting to start; null when there is none. */
    @Nullable
    private static Card card;

    /** One arrival: its text, when it can start, and its wrapped lines (re-wrapped only when the window width changes). */
    private static final class Card {
        @Nullable
        final Component note;
        final Component title;
        final Component line;
        final long receivedMs;
        final long delayMs;
        /** When the first row starts fading in; 0 until the picture is back. */
        long startMs;

        int wrapWidth = -1;
        List<FormattedCharSequence> noteRows = List.of();
        FormattedCharSequence titleRow;
        List<FormattedCharSequence> lineRows = List.of();
        int contentWidth;
        int titleWidth;

        Card(@Nullable Component note, Component title, Component line, long receivedMs, long delayMs) {
            this.note = note;
            this.title = title;
            this.line = line;
            this.receivedMs = receivedMs;
            this.delayMs = delayMs;
        }

        void wrap(Font font, int width) {
            if (width == this.wrapWidth) {
                return;
            }
            this.wrapWidth = width;
            this.noteRows = this.note == null ? List.of() : font.split(this.note, width - NOTE_INDENT);
            List<FormattedCharSequence> titleRows = font.split(this.title, (int) (width / TITLE_SCALE));
            this.titleRow = titleRows.isEmpty() ? FormattedCharSequence.EMPTY : titleRows.get(0);
            this.lineRows = font.split(this.line, width);
            this.titleWidth = OsdStyle.width(font, this.titleRow, TITLE_SCALE);
            int widest = this.titleWidth;
            for (FormattedCharSequence row : this.noteRows) {
                widest = Math.max(widest, NOTE_INDENT + OsdStyle.width(font, row, OsdStyle.SMALL));
            }
            for (FormattedCharSequence row : this.lineRows) {
                widest = Math.max(widest, OsdStyle.width(font, row, OsdStyle.SMALL));
            }
            this.contentWidth = widest;
        }
    }

    /** Network: you arrived somewhere. Replaces any card still showing. Runs on the client thread. */
    public static void receive(ArrivalCardPayload payload) {
        String level = payload.level();
        Component name = Component.translatable("level.backrooms." + level);
        Component line = Component.translatable("message.backrooms.arrive." + level);
        Component note = payload.noteKey().isEmpty() ? null : Component.translatable(payload.noteKey());
        // Resolved to plain text now, so the card can be drawn in the VHS font.
        card = new Card(
                note == null ? null : OsdStyle.text(note.getString()),
                OsdStyle.text(name.getString().toUpperCase(Locale.ROOT)),
                OsdStyle.text(line.getString()),
                System.currentTimeMillis(), Math.max(0, payload.delayMs()));
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Card c = card;
        if (c == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !Travel.inBackrooms(mc.level)) {
            card = null; // escaped or left the world: nothing to announce any more
            return;
        }
        long now = System.currentTimeMillis();
        if (c.startMs == 0L) {
            // Wait for the picture to come back (the datamosh hides the whole HUD anyway) and the HUD to be on.
            if (now - c.receivedMs > STALE_MS) {
                card = null;
                return;
            }
            if (NoclipTransition.active() || mc.options.hideGui) {
                return;
            }
            c.startMs = now + SETTLE_MS + c.delayMs;
        }
        long t = now - c.startMs;
        if (t < 0L) {
            return;
        }
        if (t > HOLD_UNTIL_MS + (long) OUT_MS) {
            card = null;
            return;
        }
        if (mc.options.hideGui) {
            return;
        }
        float out = t <= HOLD_UNTIL_MS ? 1.0f : 1.0f - smooth((t - HOLD_UNTIL_MS) / OUT_MS);

        Font font = mc.font;
        int w = g.guiWidth();
        int left = OsdStyle.marginX(g);
        c.wrap(font, Math.max(MIN_WRAP, Math.min(w - 2 * left, Math.round(w * WRAP_SHARE))));

        // Height first, so the card's bottom edge sits just above the HUD's reserved strip.
        int noteHeight = c.noteRows.isEmpty() ? 0 : c.noteRows.size() * LINE + 3;
        int titleHeight = Math.round(font.lineHeight * TITLE_SCALE) + 2;
        int height = noteHeight + titleHeight + 5 + c.lineRows.size() * LINE;
        int bottom = g.guiHeight() - HUD_CLEARANCE;
        int top = bottom - height;
        int x = left + (t < SETTLE_JITTER_MS ? ((t / 50L) % 2L == 0L ? 1 : -1) : 0);

        // Faint soft backing: barely there in the dark, just enough under bright white light.
        float first = Math.min(rise(t), out);
        for (int i = 0; i < 3; i++) {
            int f = i * SCRIM_FEATHER;
            OsdStyle.box(g, x - 8 + f, top - 6 + f, x + c.contentWidth + 10 - f, bottom + 4 - f, OsdStyle.EDGE, SCRIM_STEP * first);
        }

        int y = top;
        if (!c.noteRows.isEmpty()) {
            float a = ALPHA * first;
            OsdStyle.box(g, x - 1, y + 2, x + 5, y + 8, OsdStyle.EDGE, a * 0.7f); // the lamp's dark edge
            OsdStyle.box(g, x, y + 3, x + 4, y + 7, OsdStyle.WARN, a);
            for (FormattedCharSequence row : c.noteRows) {
                OsdStyle.drawEdged(g, font, row, x + NOTE_INDENT, y, OsdStyle.SMALL, OsdStyle.WARN, a);
                y += LINE;
            }
            y += 3;
        }
        int step = c.noteRows.isEmpty() ? 0 : 1;
        float titleAlpha = ALPHA * Math.min(rise(t - step * STAGGER_MS), out);
        OsdStyle.drawEdged(g, font, c.titleRow, x, y, TITLE_SCALE, OsdStyle.TEXT, titleAlpha);
        y += titleHeight;
        // A thin rule under the name, with its own dark edge underneath.
        int ruleEnd = x + Math.min(c.contentWidth, c.titleWidth + 14);
        OsdStyle.box(g, x, y, ruleEnd, y + 1, OsdStyle.TEXT, titleAlpha * 0.55f);
        OsdStyle.box(g, x, y + 1, ruleEnd, y + 2, OsdStyle.EDGE, titleAlpha * 0.45f);
        y += 5;
        float lineAlpha = ALPHA * LINE_ALPHA * Math.min(rise(t - (step + 1) * STAGGER_MS), out);
        for (FormattedCharSequence row : c.lineRows) {
            OsdStyle.drawEdged(g, font, row, x, y, OsdStyle.SMALL, OsdStyle.TEXT, lineAlpha);
            y += LINE;
        }
    }

    /** 0..1 fade in of a row that started {@code t} ms ago. */
    private static float rise(long t) {
        return t <= 0L ? 0.0f : smooth(t / IN_MS);
    }

    /** Smoothstep: eases in and out, so nothing starts or stops with a jerk. */
    private static float smooth(float x) {
        float v = Math.max(0.0f, Math.min(1.0f, x));
        return v * v * (3.0f - 2.0f * v);
    }
}
