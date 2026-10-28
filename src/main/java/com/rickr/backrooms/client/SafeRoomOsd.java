package com.rickr.backrooms.client;

import com.rickr.backrooms.client.hud.OsdStyle;
import com.rickr.backrooms.player.SafeRoomClientState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The safe-room part of the camcorder's on-screen display (drawn by {@link CamcorderHud}), in the
 * shared {@link OsdStyle}:
 * <ul>
 *   <li>A small "SAFE ROOM" tag in the top-left corner when you walk into one, with a dusty sage
 *       status lamp. It shows for a few seconds and then fades away, so it doesn't sit on screen
 *       the whole time you rest. It shows even with the rest of the OSD hidden; with the OSD on it
 *       sits under REC and the counter and stays as a faint ghost, so the H key always tells you.</li>
 *   <li>"CHECKPOINT SAVED" in the lower middle for 3 seconds after you use a checkpoint radio.</li>
 * </ul>
 * Both states come from the server ({@link SafeRoomClientState}).
 */
public final class SafeRoomOsd {
    private static final long CHECKPOINT_SHOW_MS = 3000L;
    /** The message fades out over the last half second. */
    private static final long CHECKPOINT_FADE_MS = 500L;
    /** Line spacing, so a tag drawn after this one (HIDING) lands just underneath. */
    private static final int LINE = 10;

    private static final Component LABEL = OsdStyle.text("SAFE ROOM");
    private static final Component CHECKPOINT = OsdStyle.text("CHECKPOINT SAVED");

    private static final OsdStyle.Fade FADE = new OsdStyle.Fade();
    private static boolean wasInside;

    /** The camera just came up: show the tag again for a moment (only drawn if you're inside). */
    static void raise(long now) {
        FADE.wake(now);
    }

    /**
     * @param x        left margin (with the OSD's jitter already added)
     * @param y        where the tag goes (under REC and the counter when the OSD is on)
     * @param osdShown true when the full OSD is on; then the tag rests faint instead of vanishing
     * @return the y for the next tag below: one line down while you're in a safe room (even once
     *         the tag has faded, so HIDING never jumps up), else {@code y}
     */
    public static int render(GuiGraphics g, Font font, int x, int y, boolean osdShown, long now) {
        boolean inside = SafeRoomClientState.inSafeRoom;
        if (inside && !wasInside) {
            FADE.wake(now); // just walked in
        }
        wasInside = inside;
        int next = y;
        if (inside) {
            float alpha = FADE.update(now, OsdStyle.FULL, osdShown ? OsdStyle.REST : 0.0f);
            OsdStyle.box(g, x, y + 3, x + 4, y + 7, OsdStyle.CALM, alpha); // a small steady status lamp
            OsdStyle.draw(g, font, LABEL, x + 8, y, OsdStyle.SMALL, OsdStyle.TEXT, alpha);
            next = y + LINE;
        }

        long since = now - SafeRoomClientState.checkpointSavedMs;
        if (SafeRoomClientState.checkpointSavedMs != 0 && since >= 0 && since < CHECKPOINT_SHOW_MS) {
            float fade = Math.min(1.0f, (CHECKPOINT_SHOW_MS - since) / (float) CHECKPOINT_FADE_MS);
            float scale = OsdStyle.LARGE;
            OsdStyle.draw(g, font, CHECKPOINT, g.guiWidth() / 2.0f - OsdStyle.width(font, CHECKPOINT, scale) / 2.0f,
                    g.guiHeight() * 0.72f, scale, OsdStyle.TEXT, OsdStyle.FULL * fade);
        }
        return next;
    }

    private SafeRoomOsd() {}
}
