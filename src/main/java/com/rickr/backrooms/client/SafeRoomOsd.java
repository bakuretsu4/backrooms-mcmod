package com.rickr.backrooms.client;

import com.rickr.backrooms.player.SafeRoomClientState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The safe-room part of the camcorder's on-screen display (drawn by {@link CamcorderHud}):
 * <ul>
 *   <li>"SAFE ROOM" in the top-left corner, small and steady, while you stand in one. It shows
 *       even with the rest of the OSD hidden; with the OSD on it sits under REC and the counter.</li>
 *   <li>"CHECKPOINT SAVED" in the lower middle for 3 seconds after you use a checkpoint radio.</li>
 * </ul>
 * Both states come from the server ({@link SafeRoomClientState}).
 */
public final class SafeRoomOsd {
    private static final int GREEN = 0x7CFF8A;
    private static final int WHITE = 0xF2F2E8;
    private static final long CHECKPOINT_SHOW_MS = 3000L;
    /** The message fades out over the last half second. */
    private static final long CHECKPOINT_FADE_MS = 500L;

    /**
     * @param x        left margin (with the OSD's jitter already added)
     * @param y        top margin (same)
     * @param osdShown true when REC, the tape counter and NIGHTSHOT are drawn at the top left
     */
    public static void render(GuiGraphics g, Font font, int x, int y, boolean osdShown, long now) {
        if (SafeRoomClientState.inSafeRoom) {
            int top = osdShown ? y + 42 : y; // below "NIGHTSHOT" when the full OSD is on
            g.fill(x, top + 1, x + 6, top + 7, 0xFF000000 | GREEN); // a small steady square, like a status lamp
            g.drawString(font, "SAFE ROOM", x + 10, top, 0xFF000000 | GREEN, true);
        }

        long since = now - SafeRoomClientState.checkpointSavedMs;
        if (SafeRoomClientState.checkpointSavedMs != 0 && since >= 0 && since < CHECKPOINT_SHOW_MS) {
            float alpha = Math.min(1.0f, (CHECKPOINT_SHOW_MS - since) / (float) CHECKPOINT_FADE_MS);
            int a = Math.max(8, (int) (alpha * 255.0f)); // text with alpha below ~4 isn't drawn at all
            String text = "CHECKPOINT SAVED";
            float scale = 1.5f;
            g.pose().pushPose();
            g.pose().translate(g.guiWidth() / 2.0f, g.guiHeight() * 0.72f, 0.0f);
            g.pose().scale(scale, scale, 1.0f);
            g.drawString(font, text, -font.width(text) / 2, 0, (a << 24) | WHITE, true);
            g.pose().popPose();
        }
    }

    private SafeRoomOsd() {}
}
