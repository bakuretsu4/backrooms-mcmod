package com.rickr.backrooms.client.anomaly.level1;

import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level1AnomalyPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * The look of the Corrupted Corridor, Level 1.1 (https://backrooms-wiki.wikidot.com/level-1-1),
 * sector by sector. Which sector you are in comes from your position along the corridor (the
 * site's anchor and facing), so the client needs nothing else from the server.
 * <ol>
 *   <li>Clean, cold white fluorescent light.</li>
 *   <li>"Colours shift to abnormal hues": the tints of shadows, highlights and air slowly cycle
 *       through wrong colours, stronger the deeper you go.</li>
 *   <li>Darkness that lights can't break: very low exposure, black air close in; only the odd
 *       bright patches still glow.</li>
 *   <li>Black and white: no colour at all, hard contrast, the picture "breathing".</li>
 *   <li>The beckoning light: everything washes out to white as you walk up to it.</li>
 * </ol>
 */
final class CorruptedCorridorEffect implements AnomalyClientEffect {
    private static final AtmosphereGrade.Mood CLEAN = new AtmosphereGrade.Mood(1.04f, 1.04f, 0.85f,
            0.98f, 1.0f, 1.04f, 1.0f, 1.0f, 1.02f,
            0.012f, 0.013f, 0.016f, 0.20f, 0.21f, 0.24f, 8.0f, 0.028f, 0.40f, 0.40f, 0.14f, 0.008f);
    private static final AtmosphereGrade.Mood DARK = new AtmosphereGrade.Mood(0.45f, 1.15f, 0.5f,
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.18f, 0.9f, 0.55f, 0.40f, 0.010f);
    private static final AtmosphereGrade.Mood MONO = new AtmosphereGrade.Mood(1.0f, 1.45f, 0.0f,
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
            0.010f, 0.010f, 0.010f, 0.08f, 0.08f, 0.08f, 3.0f, 0.06f, 0.6f, 0.60f, 0.12f, 0.060f);

    @Override
    public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        if (!inside) {
            return;
        }
        double t = Level1AnomalyPlan.corridorTExact(site, mc.player.getX(), mc.player.getZ());
        switch (Level1AnomalyPlan.corridorSector((int) Math.floor(t))) {
            case 1 -> AtmosphereGrade.setAnomalyMood(CLEAN, 0.6f);
            case 2 -> {
                float depth = (float) Mth.clamp((t - Level1AnomalyPlan.S2) / 16.0, 0.0, 1.0);
                AtmosphereGrade.setAnomalyMood(wrongHues(mc.level.getGameTime() / 20.0f), 0.4f + 0.5f * depth);
            }
            case 3 -> AtmosphereGrade.setAnomalyMood(DARK, 1.0f);
            case 4 -> AtmosphereGrade.setAnomalyMood(MONO, 1.0f);
            default -> {
                float close = (float) Mth.clamp((t - Level1AnomalyPlan.S5) / (Level1AnomalyPlan.LIGHT_T - Level1AnomalyPlan.S5), 0.0, 1.0);
                AtmosphereGrade.setAnomalyMood(light(close), 1.0f);
            }
        }
    }

    /** Shadow, highlight and air tints chasing each other round the colour wheel (a ~12 s cycle). */
    private static AtmosphereGrade.Mood wrongHues(float seconds) {
        float w = seconds * 0.5f;
        float r = 1.0f + 0.2f * Mth.sin(w);
        float g = 1.0f + 0.2f * Mth.sin(w + 2.094f);
        float b = 1.0f + 0.2f * Mth.sin(w + 4.189f);
        return new AtmosphereGrade.Mood(1.0f, 1.1f, 1.35f, r, g, b, b, r, g,
                0.012f, 0.012f, 0.016f, 0.20f * g, 0.18f * b, 0.22f * r, 6.0f, 0.03f, 0.40f, 0.40f, 0.16f, 0.020f);
    }

    /** Washing out to white as {@code close} goes from 0 (Sector 5 starts) to 1 (in the light). */
    private static AtmosphereGrade.Mood light(float close) {
        return new AtmosphereGrade.Mood(1.0f + 0.7f * close, 0.95f, 0.8f - 0.4f * close,
                1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.98f,
                0.02f + 0.1f * close, 0.02f + 0.1f * close, 0.02f + 0.1f * close,
                0.92f, 0.92f, 0.90f, 2.0f, 0.02f + 0.1f * close, 0.3f + 0.6f * close, 0.30f, 0.2f + 0.5f * close, 0.010f);
    }
}
