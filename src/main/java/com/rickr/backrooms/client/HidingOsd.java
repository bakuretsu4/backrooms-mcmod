package com.rickr.backrooms.client;

import com.rickr.backrooms.player.SafeRoomClientState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The hiding part of the camcorder's on-screen display (drawn by {@link CamcorderHud}): a small
 * "HIDING" tag in the top-left corner while you hide in furniture, in the same place and style as
 * "SAFE ROOM" (just under it if you hide inside a safe room). Shows even with the OSD hidden.
 */
public final class HidingOsd {
    private static final int AMBER = 0xE8C26A;

    /** Same arguments as {@link SafeRoomOsd#render}. */
    public static void render(GuiGraphics g, Font font, int x, int y, boolean osdShown) {
        if (!HidingClient.hiding()) {
            return;
        }
        int top = osdShown ? y + 42 : y; // below "NIGHTSHOT" when the full OSD is on
        if (SafeRoomClientState.inSafeRoom) {
            top += 12; // under "SAFE ROOM"
        }
        // The status lamp goes hollow while you hold your breath.
        if (HidingClient.holdingBreath()) {
            g.renderOutline(x, top + 1, 6, 6, 0xFF000000 | AMBER);
        } else {
            g.fill(x, top + 1, x + 6, top + 7, 0xFF000000 | AMBER);
        }
        g.drawString(font, "HIDING", x + 10, top, 0xFF000000 | AMBER, true);
    }

    private HidingOsd() {}
}
