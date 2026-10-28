package com.rickr.backrooms.client.anomaly.level0;

import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.client.audio.Level0Soundscape;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level0Pitfalls;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;

/**
 * The pits on the client: before you see the room you hear it, cold air breathing slowly up out
 * of the shafts (heard ~20 blocks off, through the walls), the warning that the floor ahead isn't
 * all floor. Once you drop below the floor the picture goes almost black ("light only reaches a
 * few feet down", https://backrooms-wiki.wikidot.com/level-0) and the air rushes past you, as in
 * Kane Pixels' "Pitfalls". The fall itself and where you land is the server's business.
 */
final class PitfallsEffect implements AnomalyClientEffect {
    private static final AtmosphereGrade.Mood FALLING = new AtmosphereGrade.Mood(0.30f, 1.2f, 0.6f,
            0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f,
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 0.4f, 0.98f, 0.6f, 0.0f, 0.03f);

    private boolean falling;

    @Override
    public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        // The draught comes from the middle of the room, a little below the floor (out of the shafts).
        double y = BackroomsLevel.LEVEL_0.layout().floorY() - 1.0;
        Level0Soundscape.loop(BlockPos.asLong(site.anchorX(), (int) y, site.anchorZ()), ModSounds.L0_PIT_DRAUGHT.get(),
                site.anchorX() + 0.5, y, site.anchorZ() + 0.5, 1.0f);
        if (!inside) {
            falling = false;
            return;
        }
        boolean nowFalling = player.getY() < BackroomsLevel.LEVEL_0.layout().floorY()
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
