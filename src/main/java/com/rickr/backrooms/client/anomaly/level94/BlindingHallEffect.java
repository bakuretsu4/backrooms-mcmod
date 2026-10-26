package com.rickr.backrooms.client.anomaly.level94;

import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level94AnomalyPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * The look and sound of the blinding hall ({@code level_94/blinding_hall}): the M.E.G. log's hall
 * of "unnaturally bright, blinding levels of light"
 * (https://backrooms-wiki.wikidot.com/m-e-g-the-discovery-of-level-94). Its ceiling is all light
 * panels (Veil lights, see FixtureType.L94_GLARE_PANEL); on top of that the picture whites out:
 * blown exposure, washed-out colour, lifted blacks, a white haze a few blocks deep and a heavy
 * glow, plus a thin high hum of the light. Just outside the doorways the glare spills out a little,
 * a warning before you step in. The first seconds are the worst; then your eyes adjust a bit.
 */
final class BlindingHallEffect implements AnomalyClientEffect {
    // exposure, contrast, saturation | shadow tint | highlight tint | lift | haze colour, start, density, max | ao, glow, breath
    private static final AtmosphereGrade.Mood WHITE_OUT = new AtmosphereGrade.Mood(2.3f, 0.62f, 0.35f,
            1.10f, 1.10f, 1.12f, 1.0f, 1.0f, 1.02f,
            0.30f, 0.30f, 0.31f,
            0.98f, 0.98f, 1.0f, 1.0f, 0.14f, 0.80f,
            0.05f, 0.95f, 0.03f);
    /** How far (blocks) outside the hall's columns the spilled glare reaches at its height. */
    private static final double SPILL = 7.0;
    private static final float SPILL_WEIGHT = 0.45f;
    /** Seconds of full blindness before the eyes adjust a little. */
    private static final float ADJUST_SECONDS = 3.0f;

    @Override
    public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        Player player = mc.player;
        if (player == null || player.getY() < Level94AnomalyPlan.HALL_FEET_Y - 0.5 || player.getY() >= Level94AnomalyPlan.HALL_TOP_Y) {
            return;
        }
        float weight;
        if (inside) {
            weight = secondsInside < ADJUST_SECONDS ? 1.0f : 0.85f;
        } else {
            double d = site.distanceTo(player.getX(), player.getZ());
            weight = (float) Math.max(0.0, 1.0 - d / SPILL) * SPILL_WEIGHT;
        }
        if (weight <= 0.0f) {
            return;
        }
        AtmosphereGrade.setAnomalyMood(WHITE_OUT, weight);
        BlindingHumSound.keepPlaying(mc, weight);
    }
}
