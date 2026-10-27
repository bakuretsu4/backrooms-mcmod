package com.rickr.backrooms.client.anomaly.level0;

import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level0PillarHall;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * The shifting pillar hall on the client. The wiki says pillar rooms "can sometimes stretch for
 * miles" (https://backrooms-wiki.wikidot.com/level-0): inside, a pale yellow haze (the colour
 * of a lit wall) appears, the one place in Level 0 that has any, so the far walls melt away and
 * the lattice seems to go on forever. (The pillars themselves are moved by the server while
 * you're not looking.)
 */
final class PeripheralShiftEffect implements AnomalyClientEffect {
    /** Level 0's grade, plus the haze and a slightly restless auto-iris. */
    private static final AtmosphereGrade.Mood ENDLESS = new AtmosphereGrade.Mood(1.08f, 1.02f, 0.92f,
            0.97f, 1.0f, 0.72f, 0.99f, 1.0f, 0.88f,
            0.006f, 0.006f, 0.003f, 0.40f, 0.37f, 0.18f, 6.0f, 0.050f, 0.55f, 0.30f, 0.18f, 0.018f);

    @Override
    public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        LocalPlayer player = mc.player;
        if (player != null && inside && Level0PillarHall.isInside(site, player.getBlockX(), player.getBlockZ())) {
            // Eases in over a few seconds after you walk in.
            AtmosphereGrade.setAnomalyMood(ENDLESS, Math.min(0.85f, 0.3f + secondsInside * 0.1f));
        }
    }
}
