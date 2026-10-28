package com.rickr.backrooms.client;

import com.rickr.backrooms.client.hud.OsdStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The hiding part of the camcorder's on-screen display (drawn by {@link CamcorderHud}): a small
 * muted-amber "HIDING" tag in the top-left corner while you hide in furniture, in the same place
 * and {@link OsdStyle} as "SAFE ROOM" (just under it while that shows). It comes up clearly when you
 * get in or start/stop holding your breath, then rests faint, but never disappears while you hide:
 * it's how you know you're stuck in the furniture. Shows even with the OSD hidden.
 */
public final class HidingOsd {
    /** Resting opacity while you stay hidden: fainter than a fresh tag, still readable. */
    private static final float HIDING_REST = 0.4f;

    private static final Component LABEL = OsdStyle.text("HIDING");

    private static final OsdStyle.Fade FADE = new OsdStyle.Fade();
    private static boolean wasHiding;
    private static boolean wasHolding;

    /** The camera just came up: show the tag clearly for a moment (only drawn while hiding). */
    static void raise(long now) {
        FADE.wake(now);
    }

    /**
     * @param x left margin (with the OSD's jitter already added)
     * @param y where the tag goes (from {@link SafeRoomOsd#render})
     */
    public static void render(GuiGraphics g, Font font, int x, int y, long now) {
        if (!HidingClient.hiding()) {
            wasHiding = false;
            return;
        }
        boolean holding = HidingClient.holdingBreath();
        if (!wasHiding || holding != wasHolding) {
            FADE.wake(now);
        }
        wasHiding = true;
        wasHolding = holding;
        float alpha = FADE.update(now, OsdStyle.FULL, HIDING_REST);
        // The status lamp goes hollow while you hold your breath.
        if (holding) {
            if (alpha >= 0.02f) {
                g.renderOutline(x, y + 3, 4, 4, OsdStyle.argb(OsdStyle.WARN, alpha));
            }
        } else {
            OsdStyle.box(g, x, y + 3, x + 4, y + 7, OsdStyle.WARN, alpha);
        }
        OsdStyle.draw(g, font, LABEL, x + 8, y, OsdStyle.SMALL, OsdStyle.WARN, alpha);
    }

    private HidingOsd() {}
}
