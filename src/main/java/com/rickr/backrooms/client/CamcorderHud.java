package com.rickr.backrooms.client;

import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.network.TransitionPayload;
import com.rickr.backrooms.world.Travel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The camcorder's on-screen display, drawn on top of the shader so it stays sharp:
 * REC + tape counter (top-left), battery (top-right), clock and date (bottom-left), exit signal
 * bars (bottom-right) and viewfinder corner brackets. The date always reads 1990, like the
 * Kane Pixels tapes. When something is close the OSD itself starts to jitter. Your heart rate
 * (sanity) shows top-right whenever you're in the Backrooms, even with the OSD hidden.
 */
public final class CamcorderHud implements LayeredDraw.Layer {
    private static final int WHITE = 0xFFF2F2E8;
    private static final int RED = 0xFFE0281E;
    private static final int GREEN = 0xFF7CFF8A;
    private static final int AMBER = 0xFFFFB020;
    private static final float TRANSITION_MS = 2500.0f;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM. dd 1990", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("a h:mm:ss", Locale.ENGLISH);

    private final RandomSource glitch = RandomSource.create();
    /**
     * H key: show our REC/clock/battery overlay. Off by default - the reference footage has no
     * overlay at all - but "PLAY"/"STOP" transitions and "NO BATTERY" always show.
     */
    private static boolean osdVisible = false;

    public static void toggleOsd() {
        osdVisible = !osdVisible;
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        drawTransition(g, mc);
        if (!CamcorderEffect.wanted(mc) || mc.options.hideGui) {
            return;
        }
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();
        long now = System.currentTimeMillis();
        float danger = CamcorderEffect.danger();
        int jx = danger > 0.45f && glitch.nextFloat() < danger * 0.6f ? glitch.nextInt(7) - 3 : 0;
        int jy = danger > 0.7f && glitch.nextFloat() < danger * 0.3f ? glitch.nextInt(3) - 1 : 0;
        int m = 14; // margin

        boolean dead = CameraClientState.battery <= 0.0f;
        if (dead) {
            if ((now / 600) % 2 == 0) {
                drawScaled(g, font, "NO BATTERY", w / 2, h / 2 - 6, 2.0f, RED, true);
            }
            return;
        }

        // Heart rate (your sanity) always shows in the Backrooms, even with the OSD hidden.
        if (Travel.inBackrooms(mc.level)) {
            heartRate(g, font, w - m + jx, m + 18 + jy, now);
        }
        // "SAFE ROOM" and "CHECKPOINT SAVED" also show with the OSD hidden.
        SafeRoomOsd.render(g, font, m + jx, m + jy, osdVisible || CameraClientState.realRecording, now);

        boolean showOsd = osdVisible || CameraClientState.realRecording; // filming the real world
        if (!showOsd) {
            return;
        }
        brackets(g, m - 6 + jx, m - 6 + jy, w - m + 6 + jx, h - m + 6 + jy);

        // REC with blinking dot, and the tape counter.
        if ((now / 700) % 2 == 0) {
            g.fill(m + jx, m + 2 + jy, m + 7 + jx, m + 9 + jy, RED);
            g.fill(m + 1 + jx, m + 1 + jy, m + 6 + jx, m + 10 + jy, RED);
        }
        drawScaled(g, font, "REC", m + 11 + jx, m + jy, 1.5f, WHITE, false);
        drawScaled(g, font, "SP  " + tapeCounter(), m + jx, m + 18 + jy, 1.0f, WHITE, false);
        if (CameraClientState.nightShot) {
            drawScaled(g, font, "NIGHTSHOT", m + jx, m + 30 + jy, 1.0f, GREEN, false);
        }

        battery(g, font, w - m - 30 + jx, m + jy, now);

        LocalDateTime clock = LocalDateTime.now();
        drawScaled(g, font, clock.format(TIME).toUpperCase(Locale.ENGLISH), m + jx, h - m - 26 + jy, 1.25f, WHITE, false);
        drawScaled(g, font, clock.format(DATE).toUpperCase(Locale.ENGLISH), m + jx, h - m - 12 + jy, 1.25f, WHITE, false);

        signal(g, font, w - m - 34 + jx, h - m - 14 + jy);
    }

    /**
     * Sanity as a heart-rate readout, right-aligned at x: a pixel heart that throbs with each beat
     * and "112 BPM" (70 when calm, up to 160 when you're losing it). Amber below 50, blinking red below 30.
     */
    private static void heartRate(GuiGraphics g, Font font, int right, int y, long now) {
        float sanity = SanityClientEffects.shownSanity();
        int color = sanity < 30.0f ? ((now / 400) % 2 == 0 ? RED : 0x00000000) : sanity < 50.0f ? AMBER : WHITE;
        if ((color >>> 24) == 0) {
            return;
        }
        String text = SanityClientEffects.bpm() + " BPM";
        int textX = right - font.width(text);
        g.drawString(font, text, textX, y + 2, color, true);
        // 7x6 pixel heart left of the number; one pixel bigger on the beat.
        int hx = textX - 11;
        int hy = y + 2;
        int grow = SanityClientEffects.heartbeatPulse() > 0.5f ? 1 : 0;
        g.fill(hx + 1, hy - grow, hx + 3, hy + 1, color);
        g.fill(hx + 4, hy - grow, hx + 6, hy + 1, color);
        g.fill(hx - grow, hy + 1, hx + 7 + grow, hy + 3, color);
        g.fill(hx + 1, hy + 3, hx + 6, hy + 4, color);
        g.fill(hx + 2, hy + 4, hx + 5, hy + 5, color);
        g.fill(hx + 3, hy + 5, hx + 4, hy + 6 + grow, color);
    }

    private static String tapeCounter() {
        long ticks = CameraClientState.tapeTicks + Math.max(0, (System.currentTimeMillis() - CameraClientState.tapeSyncMs) / 50);
        long total = ticks / 20;
        return String.format(Locale.ROOT, "%d:%02d:%02d", total / 3600, (total / 60) % 60, total % 60);
    }

    private static void battery(GuiGraphics g, Font font, int x, int y, long now) {
        float pct = CameraClientState.battery;
        int color = pct <= 15.0f ? ((now / 400) % 2 == 0 ? RED : 0x00000000) : WHITE;
        if ((color >>> 24) == 0) {
            return;
        }
        // Outline + terminal nub.
        g.fill(x, y, x + 26, y + 1, color);
        g.fill(x, y + 11, x + 26, y + 12, color);
        g.fill(x, y, x + 1, y + 12, color);
        g.fill(x + 25, y, x + 26, y + 12, color);
        g.fill(x + 26, y + 3, x + 29, y + 9, color);
        int bars = Mth.ceil(pct / 25.0f);
        for (int i = 0; i < bars; i++) {
            g.fill(x + 3 + i * 5 + i, y + 3, x + 3 + i * 5 + i + 5, y + 9, color);
        }
        g.drawString(font, (int) Math.ceil(pct) + "%", x - font.width("100%") - 4, y + 2, color, true);
    }

    private static void signal(GuiGraphics g, Font font, int x, int y) {
        int bars = Mth.clamp(Mth.ceil(CameraClientState.exitSignal * 4.0f), 0, 4);
        g.drawString(font, "SIG", x - 20, y + 2, WHITE, true);
        for (int i = 0; i < 4; i++) {
            int bh = 3 + i * 3;
            int color = i < bars ? (bars == 4 ? GREEN : WHITE) : 0x55FFFFFF;
            g.fill(x + i * 6, y + 12 - bh, x + i * 6 + 4, y + 12, color);
        }
    }

    private static void brackets(GuiGraphics g, int x0, int y0, int x1, int y1) {
        int len = 18;
        int c = 0xCCF2F2E8;
        g.fill(x0, y0, x0 + len, y0 + 2, c);
        g.fill(x0, y0, x0 + 2, y0 + len, c);
        g.fill(x1 - len, y0, x1, y0 + 2, c);
        g.fill(x1 - 2, y0, x1, y0 + len, c);
        g.fill(x0, y1 - 2, x0 + len, y1, c);
        g.fill(x0, y1 - len, x0 + 2, y1, c);
        g.fill(x1 - len, y1 - 2, x1, y1, c);
        g.fill(x1 - 2, y1 - len, x1, y1, c);
    }

    /** "PLAY" when you noclip into a level, "STOP" when you escape. */
    private static void drawTransition(GuiGraphics g, Minecraft mc) {
        long since = System.currentTimeMillis() - CameraClientState.transitionStartMs;
        if (CameraClientState.transitionStartMs == 0 || since < 0 || since > TRANSITION_MS) {
            return;
        }
        boolean escape = CameraClientState.transitionKind == TransitionPayload.ESCAPE;
        if (since < 900) {
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0xFF000000);
        }
        String text = escape ? "\u25A0 STOP" : "\u25B6 PLAY";
        drawScaled(g, mc.font, text, g.guiWidth() / 2, g.guiHeight() / 2 - 8, 2.5f, escape ? AMBER : WHITE, true);
    }

    private static void drawScaled(GuiGraphics g, Font font, String text, int x, int y, float scale, int color, boolean centered) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0f);
        int dx = centered ? -font.width(text) / 2 : 0;
        g.drawString(font, text, dx, 0, color, true);
        g.pose().popPose();
    }
}
