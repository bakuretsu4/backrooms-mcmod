package com.rickr.backrooms.client.anomaly.level0;

import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level0Pitfalls;
import com.rickr.backrooms.world.layout.LevelLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/**
 * The pits on the client: once you drop below the floor the picture goes almost black ("light
 * only reaches a few feet down", https://backrooms-wiki.wikidot.com/level-0) and the air rushes past
 * you, as in Kane Pixels' "Pitfalls". The fall itself and where you land is the server's business.
 */
final class PitfallsEffect implements AnomalyClientEffect {
    private static final AtmosphereGrade.Mood FALLING = new AtmosphereGrade.Mood(0.30f, 1.2f, 0.6f,
            0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f,
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 0.4f, 0.98f, 0.6f, 0.0f, 0.03f);

    private boolean falling;

    @Override
    public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        LocalPlayer player = mc.player;
        if (player == null || !inside) {
            return;
        }
        boolean nowFalling = player.getY() < LevelLayout.FLOOR_Y
                && Level0Pitfalls.isShaft(site, player.getBlockX(), player.getBlockZ());
        if (nowFalling) {
            AtmosphereGrade.setAnomalyMood(FALLING, 1.0f);
            if (!falling) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.INTRO_WIND.get(), 1.0f, 0.7f));
            }
        }
        falling = nowFalling;
    }
}
