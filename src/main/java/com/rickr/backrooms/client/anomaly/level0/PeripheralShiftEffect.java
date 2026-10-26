package com.rickr.backrooms.client.anomaly.level0;

import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level0PillarHall;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * The shifting pillar hall on the client. The wiki says pillar rooms "can sometimes stretch for
 * miles" (https://backrooms-wiki.wikidot.com/level-0): inside, the yellow haze thickens and starts
 * closer, so the far walls melt away and the lattice seems to go on forever. (The pillars
 * themselves are moved by the server while you're not looking.)
 */
final class PeripheralShiftEffect implements AnomalyClientEffect {
    private static final AtmosphereGrade.Mood ENDLESS = new AtmosphereGrade.Mood(1.02f, 1.02f, 0.86f,
            0.94f, 0.98f, 0.86f, 1.04f, 1.0f, 0.88f,
            0.018f, 0.016f, 0.012f, 0.24f, 0.22f, 0.13f, 3.0f, 0.065f, 0.62f, 0.36f, 0.18f, 0.018f);

    @Override
    public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        LocalPlayer player = mc.player;
        if (player != null && inside && Level0PillarHall.isInside(site, player.getBlockX(), player.getBlockZ())) {
            // Eases in over a few seconds after you walk in.
            AtmosphereGrade.setAnomalyMood(ENDLESS, Math.min(0.85f, 0.3f + secondsInside * 0.1f));
        }
    }
}
