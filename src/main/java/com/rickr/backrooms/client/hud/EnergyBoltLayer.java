package com.rickr.backrooms.client.hud;

import com.rickr.backrooms.client.CamcorderEffect;
import com.rickr.backrooms.player.EnergyClientState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;

/**
 * A faint lightning bolt where the crosshair would be, showing your sprint energy. It fills from
 * the bottom up, turns reddish while you're out of breath, and fades away once you're fully rested,
 * so most of the time the screen is clean. Drawn from a tiny pixel mask, no texture needed.
 */
public final class EnergyBoltLayer implements LayeredDraw.Layer {
    /** '#' = part of the bolt. 8 x 11 GUI pixels. */
    private static final String[] MASK = {
            ".....###",
            "....###.",
            "...###..",
            "..###...",
            ".#######",
            "....###.",
            "...###..",
            "..###...",
            ".###....",
            ".##.....",
            "#.......",
    };
    /** Opacity of the filled part (the reference uses ~25%). */
    private static final float ALPHA = 0.25f;
    /** The empty part of the bolt is this fraction of {@link #ALPHA}, so you can see the outline. */
    private static final float EMPTY_ALPHA = 0.3f;
    /** Fade out over 1 s once full; fade in over 0.25 s when you start using energy. */
    private static final float FADE_OUT_S = 1.0f;
    private static final float FADE_IN_S = 0.25f;
    private static final int RGB_NORMAL = 0xF2F2E8;
    private static final int RGB_EXHAUSTED = 0xE05A4A;

    private float visible;
    private long lastFrameNs;

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        long nowNs = System.nanoTime();
        float dt = lastFrameNs == 0L ? 0.0f : Math.min((nowNs - lastFrameNs) / 1.0e9f, 0.1f);
        lastFrameNs = nowNs;
        if (!CamcorderEffect.wanted(mc) || mc.options.hideGui || mc.player.isSpectator()) {
            visible = 0.0f;
            return;
        }

        float energy = Mth.clamp(EnergyClientState.energy, 0.0f, 1.0f);
        boolean full = energy >= 0.999f && !EnergyClientState.exhausted;
        visible = full ? Math.max(0.0f, visible - dt / FADE_OUT_S) : Math.min(1.0f, visible + dt / FADE_IN_S);
        if (visible <= 0.0f) {
            return;
        }

        int rgb = EnergyClientState.exhausted ? RGB_EXHAUSTED : RGB_NORMAL;
        int filled = argb(ALPHA * visible, rgb);
        int empty = argb(ALPHA * EMPTY_ALPHA * visible, rgb);
        int rows = MASK.length;
        int cols = MASK[0].length();
        int x0 = g.guiWidth() / 2 - cols / 2;
        int y0 = g.guiHeight() / 2 - rows / 2;
        for (int r = 0; r < rows; r++) {
            // Row r is filled if the energy level reaches its middle (rows count from the bottom).
            float rowLevel = (rows - 1 - r + 0.5f) / rows;
            int color = rowLevel <= energy ? filled : empty;
            String line = MASK[r];
            // Draw each horizontal run of '#' as one rectangle (no overlaps = even transparency).
            int c = 0;
            while (c < cols) {
                if (line.charAt(c) != '#') {
                    c++;
                    continue;
                }
                int start = c;
                while (c < cols && line.charAt(c) == '#') {
                    c++;
                }
                g.fill(x0 + start, y0 + r, x0 + c, y0 + r + 1, color);
            }
        }
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp((int) (alpha * 255.0f), 0, 255) << 24) | rgb;
    }
}
