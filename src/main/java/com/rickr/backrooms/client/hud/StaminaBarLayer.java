package com.rickr.backrooms.client.hud;

import com.rickr.backrooms.client.CamcorderEffect;
import com.rickr.backrooms.config.ClientSettings;
import com.rickr.backrooms.player.EnergyClientState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;

/**
 * Your sprint stamina as one thin, faint line in the slot where the XP bar would be (the XP bar
 * itself is hidden in the Backrooms), between the hotbar and your hearts. Drawn in the shared
 * camcorder look ({@link OsdStyle}): warm off-white, fainter than the hearts, shorter than the
 * hotbar. It shrinks toward the middle as you run, turns a soft red while you're out of breath,
 * and fades away once you're rested again, so most of the time there is nothing on screen.
 * Can be turned off on the SETTINGS screen.
 */
public final class StaminaBarLayer implements LayeredDraw.Layer {
    /** Two thirds of the hotbar's width, centred: enough to read, small enough to ignore. */
    private static final int WIDTH = 120;
    /** Vanilla draws the XP bar 29 GUI pixels above the bottom; this sits in the middle of that slot. */
    private static final int FROM_BOTTOM = 27;
    /** Fainter than the hearts and hunger (BackroomsHudEvents.STATUS_ALPHA). */
    private static final float ALPHA = 0.28f;
    /** The empty part, so you can see how much is missing. */
    private static final float TRACK_ALPHA = 0.06f;
    /** Out of breath matters more, so the red line is a little stronger. */
    private static final float EXHAUSTED_ALPHA = 0.4f;
    /** Fade out over 1 s once full; fade in over 0.25 s when you start using stamina. */
    private static final float FADE_OUT_S = 1.0f;
    private static final float FADE_IN_S = 0.25f;

    private float visible;
    private long lastFrameNs;

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        long nowNs = System.nanoTime();
        float dt = lastFrameNs == 0L ? 0.0f : Math.min((nowNs - lastFrameNs) / 1.0e9f, 0.1f);
        lastFrameNs = nowNs;
        if (!ClientSettings.STAMINA_BAR.get() || !CamcorderEffect.wanted(mc) || mc.options.hideGui
                || mc.player.isSpectator()) {
            visible = 0.0f;
            return;
        }

        float energy = Mth.clamp(EnergyClientState.energy, 0.0f, 1.0f);
        boolean full = energy >= 0.999f && !EnergyClientState.exhausted;
        visible = full ? Math.max(0.0f, visible - dt / FADE_OUT_S) : Math.min(1.0f, visible + dt / FADE_IN_S);
        if (visible <= 0.0f) {
            return;
        }

        boolean exhausted = EnergyClientState.exhausted;
        int rgb = exhausted ? OsdStyle.ALERT : OsdStyle.TEXT;
        float alpha = exhausted ? EXHAUSTED_ALPHA : ALPHA;
        int x0 = g.guiWidth() / 2 - WIDTH / 2;
        int x1 = x0 + WIDTH;
        int y = g.guiHeight() - FROM_BOTTOM;
        int half = Math.round(WIDTH / 2.0f * energy);
        int mid = g.guiWidth() / 2;
        OsdStyle.box(g, x0, y, x1, y + 1, rgb, TRACK_ALPHA * visible);
        if (half > 0) {
            OsdStyle.box(g, mid - half, y, mid + half, y + 1, rgb, alpha * visible);
        }
    }
}
