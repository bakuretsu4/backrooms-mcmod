package com.rickr.backrooms.client;

import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.hud.OsdStyle;
import com.rickr.backrooms.world.Travel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The camcorder's on-screen display, drawn on top of the shader so it stays sharp. Everything uses
 * the shared camcorder look in {@link OsdStyle}: a thin VHS font, warm off-white, see-through.
 * <ul>
 *   <li>With the OSD on (H key, or filming the real world): REC + tape counter (top left), battery
 *       (top right), clock and 1990 date (bottom left), exit signal bars (bottom right) and thin
 *       viewfinder corner marks. They show clearly when you bring the OSD up or when what they show
 *       changes, then sink to a faint ghost after a few seconds.</li>
 *   <li>Always, even with the OSD off: a low battery (15% or less) pulses soft red at the top right,
 *       and an empty one says NO BATTERY in the middle.</li>
 *   <li>Your heart rate (sanity) at the top right in the Backrooms, only when it matters: while it's
 *       raised (100 BPM or more) or for a few seconds after it changes; otherwise it fades away.</li>
 * </ul>
 * When something is close the OSD itself starts to jitter. The date always reads 1990, like the
 * Kane Pixels tapes.
 */
public final class CamcorderHud implements LayeredDraw.Layer {
    private static final float TRANSITION_MS = 2500.0f;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM. dd 1990", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("a h:mm:ss", Locale.ENGLISH);

    /** Line spacing of the small VHS text, in GUI pixels. */
    private static final int LINE = 10;

    /** Battery at or below this is a warning; matches the server's first low-battery beep. */
    private static final float LOW_BATTERY = 15.0f;
    /** ...and at or below this the warning pulses faster (the second beep). */
    private static final float CRITICAL_BATTERY = 5.0f;

    /**
     * Heart rate at or above this stays on screen (faintly) instead of fading away. 100 BPM is
     * sanity ~67: you've started hearing things. Calm (70) up to here, it only shows after a change.
     */
    private static final int ELEVATED_BPM = 100;
    /** A heart-rate change this big since it was last shown wakes it up for a few seconds. */
    private static final int BPM_STEP = 4;
    /** How faint a raised heart rate rests. */
    private static final float BPM_REST = 0.5f;
    /** Sanity below which the heart rate turns amber, and below which it pulses red (your heartbeat is audible). */
    private static final float UNEASY_SANITY = 50.0f;
    private static final float PANIC_SANITY = 30.0f;

    /** The corner marks are drawn this much fainter than the text they frame. */
    private static final float BRACKET_ALPHA = 0.6f;

    private static final Component REC = OsdStyle.text("REC");
    private static final Component SIG = OsdStyle.text("SIG");
    private static final Component NIGHTSHOT = OsdStyle.text("NIGHTSHOT");
    private static final Component NO_BATTERY = OsdStyle.text("NO BATTERY");
    private static final Component STOP = OsdStyle.text("STOP");

    private final RandomSource glitch = RandomSource.create();
    /**
     * H key: show our REC/clock/battery overlay. Off by default - the reference footage has no
     * overlay at all - but "STOP" transitions, low battery and "NO BATTERY" always show.
     */
    private static boolean osdVisible = false;

    // Each readout fades on its own, so only the one that changed comes back up.
    /** REC, tape counter, clock, date and corner marks: these only come up with the camera. */
    private final OsdStyle.Fade tape = new OsdStyle.Fade();
    private final OsdStyle.Fade battery = new OsdStyle.Fade();
    private final OsdStyle.Fade signal = new OsdStyle.Fade();
    private final OsdStyle.Fade heart = new OsdStyle.Fade();
    /** What each readout showed last frame, to notice changes. */
    private int lastBatteryKey = -1;
    private int lastSignalBars = -1;
    private int heartAnchor = -1;
    private boolean wasShown;
    private boolean wasInBackrooms;

    public static void toggleOsd() {
        osdVisible = !osdVisible;
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        drawTransition(g, mc);
        if (!CamcorderEffect.wanted(mc) || mc.options.hideGui) {
            this.wasShown = false;
            this.wasInBackrooms = false;
            return;
        }
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();
        long now = System.currentTimeMillis();
        float danger = CamcorderEffect.danger();
        int jx = danger > 0.45f && this.glitch.nextFloat() < danger * 0.6f ? this.glitch.nextInt(7) - 3 : 0;
        int jy = danger > 0.7f && this.glitch.nextFloat() < danger * 0.3f ? this.glitch.nextInt(3) - 1 : 0;
        int left = OsdStyle.marginX(g) + jx;
        int right = w - OsdStyle.marginX(g) + jx;
        int top = OsdStyle.marginY(g) + jy;
        int bottom = h - OsdStyle.marginY(g) + jy;

        boolean shown = osdVisible || CameraClientState.realRecording; // filming the real world
        if (shown && !this.wasShown) {
            raise(now); // the camera just came up: everything shows clearly for a moment
        }
        this.wasShown = shown;

        float pct = CameraClientState.battery;
        if (pct <= 0.0f) {
            // An empty battery hides everything else; this is the one big message, and it must be clear.
            Component text = NO_BATTERY;
            float scale = 2.0f;
            OsdStyle.draw(g, font, text, w / 2.0f - OsdStyle.width(font, text, scale) / 2.0f, h / 2.0f - 8.0f,
                    scale, OsdStyle.ALERT, OsdStyle.warning(now, 1200L));
            return;
        }

        // Battery: comes up when a quarter bar drops, when it gets low, and when NightShot or the
        // flashlight (which both drain it) are switched.
        boolean low = pct <= LOW_BATTERY;
        boolean critical = pct <= CRITICAL_BATTERY;
        int batteryKey = Mth.ceil(pct / 25.0f) | (low ? 8 : 0) | (critical ? 16 : 0)
                | (CameraClientState.nightShot ? 32 : 0) | (CameraClientState.flashlight ? 64 : 0);
        if (batteryKey != this.lastBatteryKey) {
            if (this.lastBatteryKey >= 0) {
                this.battery.wake(now);
            }
            this.lastBatteryKey = batteryKey;
        }
        float batteryAlpha = this.battery.update(now, OsdStyle.FULL, shown ? OsdStyle.REST : 0.0f);
        boolean batteryDrawn = shown || low;
        if (batteryDrawn) {
            float a = low ? Math.max(batteryAlpha, OsdStyle.warning(now, critical ? 700L : 1400L)) : batteryAlpha;
            battery(g, font, right, top, pct, low ? OsdStyle.ALERT : OsdStyle.TEXT, a);
        }

        // Heart rate (your sanity), under the battery when that is showing.
        boolean inBackrooms = Travel.inBackrooms(mc.level);
        if (inBackrooms) {
            if (!this.wasInBackrooms) {
                this.heart.wake(now); // arriving: show it once so you know it's there
            }
            heartRate(g, font, right, batteryDrawn ? top + LINE + 4 : top, shown, now);
        }
        this.wasInBackrooms = inBackrooms;

        // "SAFE ROOM", "CHECKPOINT SAVED" and "HIDING" also show with the OSD hidden; with it on
        // they sit under REC, the counter and NIGHTSHOT.
        int tagTop = shown ? top + 3 * LINE + 6 : top;
        int next = SafeRoomOsd.render(g, font, left, tagTop, shown, now);
        HidingOsd.render(g, font, left, next, now);

        if (!shown) {
            return;
        }
        float tapeAlpha = this.tape.update(now, OsdStyle.FULL, OsdStyle.REST);
        brackets(g, left - 6, top - 5, right + 6, bottom + 5, tapeAlpha * BRACKET_ALPHA);

        // REC with its blinking lamp, and the tape counter.
        if ((now / 700) % 2 == 0) {
            OsdStyle.box(g, left, top + 4, left + 5, top + 8, OsdStyle.REC, tapeAlpha);
            OsdStyle.box(g, left + 1, top + 3, left + 4, top + 9, OsdStyle.REC, tapeAlpha);
        }
        OsdStyle.draw(g, font, REC, left + 8, top, OsdStyle.LARGE, OsdStyle.TEXT, tapeAlpha);
        OsdStyle.draw(g, font, OsdStyle.text("SP  " + tapeCounter()), left, top + LINE + 4, OsdStyle.SMALL, OsdStyle.TEXT, tapeAlpha);
        if (CameraClientState.nightShot) {
            // Switching NightShot wakes the battery readout, so this follows that fade.
            OsdStyle.draw(g, font, NIGHTSHOT, left, top + 2 * LINE + 4, OsdStyle.SMALL, OsdStyle.TEXT, batteryAlpha);
        }

        LocalDateTime clock = LocalDateTime.now();
        OsdStyle.draw(g, font, OsdStyle.text(clock.format(TIME).toUpperCase(Locale.ENGLISH)), left, bottom - 2 * LINE - 1,
                OsdStyle.SMALL, OsdStyle.TEXT, tapeAlpha);
        OsdStyle.draw(g, font, OsdStyle.text(clock.format(DATE).toUpperCase(Locale.ENGLISH)), left, bottom - LINE,
                OsdStyle.SMALL, OsdStyle.TEXT, tapeAlpha);

        signal(g, font, right, bottom, now);
    }

    /** Bring every readout up to full for a moment (the OSD was just switched on). */
    private void raise(long now) {
        this.tape.wake(now);
        this.battery.wake(now);
        this.signal.wake(now);
        this.heart.wake(now);
        SafeRoomOsd.raise(now);
        HidingOsd.raise(now);
    }

    /**
     * Sanity as a heart-rate readout, right-aligned at {@code right}: a tiny pixel heart that throbs
     * with each beat and "112 BPM" (70 when calm, up to 160 when you're losing it). Amber below 50
     * sanity; below 30 soft red, pulsing with your heartbeat, and never fading out.
     */
    private void heartRate(GuiGraphics g, Font font, int right, int y, boolean shown, long now) {
        int bpm = SanityClientEffects.bpm();
        if (this.heartAnchor < 0 || Math.abs(bpm - this.heartAnchor) >= BPM_STEP) {
            if (this.heartAnchor >= 0) {
                this.heart.wake(now);
            }
            this.heartAnchor = bpm;
        }
        // At rest: faint while raised, a ghost with the OSD on, gone otherwise.
        float rest = bpm >= ELEVATED_BPM ? BPM_REST : shown ? OsdStyle.REST : 0.0f;
        float alpha = this.heart.update(now, OsdStyle.FULL, rest);
        float sanity = SanityClientEffects.shownSanity();
        int rgb = OsdStyle.TEXT;
        if (sanity < PANIC_SANITY) {
            rgb = OsdStyle.ALERT;
            alpha = Math.max(alpha, Mth.lerp(SanityClientEffects.heartbeatPulse(), OsdStyle.WARN_MIN, 0.92f));
        } else if (sanity < UNEASY_SANITY) {
            rgb = OsdStyle.WARN;
        }
        if (alpha < 0.02f) {
            return;
        }
        Component text = OsdStyle.text(bpm + " BPM");
        int textX = right - OsdStyle.width(font, text, OsdStyle.SMALL);
        OsdStyle.draw(g, font, text, textX, y, OsdStyle.SMALL, rgb, alpha);
        // 5x4 pixel heart left of the number; one pixel bigger on the beat.
        int hx = textX - 8;
        int hy = y + 3;
        int grow = SanityClientEffects.heartbeatPulse() > 0.5f ? 1 : 0;
        OsdStyle.box(g, hx + 1, hy - grow, hx + 2, hy + 1, rgb, alpha);
        OsdStyle.box(g, hx + 3, hy - grow, hx + 4, hy + 1, rgb, alpha);
        OsdStyle.box(g, hx - grow, hy + 1, hx + 5 + grow, hy + 2, rgb, alpha);
        OsdStyle.box(g, hx + 1, hy + 2, hx + 4, hy + 3, rgb, alpha);
        OsdStyle.box(g, hx + 2, hy + 3, hx + 3, hy + 4 + grow, rgb, alpha);
    }

    private static String tapeCounter() {
        long ticks = CameraClientState.tapeTicks + Math.max(0, (System.currentTimeMillis() - CameraClientState.tapeSyncMs) / 50);
        long total = ticks / 20;
        return String.format(Locale.ROOT, "%d:%02d:%02d", total / 3600, (total / 60) % 60, total % 60);
    }

    /** A thin battery outline with up to four bars, right-aligned at {@code right}, and the percentage left of it. */
    private static void battery(GuiGraphics g, Font font, int right, int y, float pct, int rgb, float alpha) {
        if (alpha < 0.02f) {
            return;
        }
        int x = right - 22; // 20 wide body + 2 px terminal nub
        OsdStyle.box(g, x, y + 1, x + 20, y + 2, rgb, alpha);
        OsdStyle.box(g, x, y + 8, x + 20, y + 9, rgb, alpha);
        OsdStyle.box(g, x, y + 2, x + 1, y + 8, rgb, alpha);
        OsdStyle.box(g, x + 19, y + 2, x + 20, y + 8, rgb, alpha);
        OsdStyle.box(g, x + 20, y + 3, x + 22, y + 7, rgb, alpha);
        int bars = Mth.ceil(pct / 25.0f);
        for (int i = 0; i < bars; i++) {
            OsdStyle.box(g, x + 2 + i * 4, y + 3, x + 5 + i * 4, y + 7, rgb, alpha);
        }
        Component text = OsdStyle.text((int) Math.ceil(pct) + "%");
        OsdStyle.draw(g, font, text, x - 4 - OsdStyle.width(font, text, OsdStyle.SMALL), y, OsdStyle.SMALL, rgb, alpha);
    }

    /** Exit signal: "SIG" and four thin rising bars, bottom-right. Comes up when a bar is gained or lost. */
    private void signal(GuiGraphics g, Font font, int right, int bottom, long now) {
        int bars = Mth.clamp(Mth.ceil(CameraClientState.exitSignal * 4.0f), 0, 4);
        if (bars != this.lastSignalBars) {
            if (this.lastSignalBars >= 0) {
                this.signal.wake(now);
            }
            this.lastSignalBars = bars;
        }
        float alpha = this.signal.update(now, OsdStyle.FULL, OsdStyle.REST);
        int x = right - 18; // 4 bars, 3 wide with 2 px gaps
        int base = bottom - 1;
        for (int i = 0; i < 4; i++) {
            int bh = 3 + i * 2;
            // Missing bars stay as faint stubs so you can count what you have.
            OsdStyle.box(g, x + i * 5, base - bh, x + i * 5 + 3, base, OsdStyle.TEXT, i < bars ? alpha : alpha * 0.3f);
        }
        OsdStyle.draw(g, font, SIG, x - 4 - OsdStyle.width(font, SIG, OsdStyle.SMALL), bottom - LINE, OsdStyle.SMALL, OsdStyle.TEXT, alpha);
    }

    /** Thin L-shaped viewfinder marks in the four corners. */
    private static void brackets(GuiGraphics g, int x0, int y0, int x1, int y1, float alpha) {
        int len = 12;
        int c = OsdStyle.TEXT;
        OsdStyle.box(g, x0, y0, x0 + len, y0 + 1, c, alpha);
        OsdStyle.box(g, x0, y0 + 1, x0 + 1, y0 + len, c, alpha);
        OsdStyle.box(g, x1 - len, y0, x1, y0 + 1, c, alpha);
        OsdStyle.box(g, x1 - 1, y0 + 1, x1, y0 + len, c, alpha);
        OsdStyle.box(g, x0, y1 - 1, x0 + len, y1, c, alpha);
        OsdStyle.box(g, x0, y1 - len, x0 + 1, y1 - 1, c, alpha);
        OsdStyle.box(g, x1 - len, y1 - 1, x1, y1, c, alpha);
        OsdStyle.box(g, x1 - 1, y1 - len, x1, y1 - 1, c, alpha);
    }

    /** "■ STOP" when you escape back to reality. (Moves between floors datamosh instead: NoclipTransition.) */
    private static void drawTransition(GuiGraphics g, Minecraft mc) {
        long since = System.currentTimeMillis() - CameraClientState.escapeStartMs;
        if (CameraClientState.escapeStartMs == 0 || since < 0 || since > TRANSITION_MS) {
            return;
        }
        if (since < 900) {
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0xFF000000);
        }
        float scale = 2.0f;
        int textW = OsdStyle.width(mc.font, STOP, scale);
        int square = 10;
        int x = g.guiWidth() / 2 - (square + 6 + textW) / 2;
        int y = g.guiHeight() / 2 - 8;
        // The VHS font has no symbols, so the stop square is drawn as a box.
        OsdStyle.box(g, x, y + 4, x + square, y + 4 + square, OsdStyle.TEXT, OsdStyle.FULL);
        OsdStyle.draw(g, mc.font, STOP, x + square + 6, y, scale, OsdStyle.TEXT, OsdStyle.FULL);
    }
}
