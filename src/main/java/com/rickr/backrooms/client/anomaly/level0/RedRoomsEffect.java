package com.rickr.backrooms.client.anomaly.level0;

import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level0RedRooms;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * The Red Rooms on the client (https://backrooms-wiki.wikidot.com/red-rooms):
 * <ul>
 *   <li>the buzz "sounds like a klaxon synth": a looping hum placed in each of the three rooms, so it
 *       leaks faintly through the walls as you come near;</li>
 *   <li>the warning sign "a red colour shift" (https://backrooms-wiki.wikidot.com/level-0): the
 *       picture turns redder within about 16 blocks, and inside the crimson rooms it is all
 *       darkroom red.</li>
 * </ul>
 */
final class RedRoomsEffect implements AnomalyClientEffect {
    /** Darkroom red: crimson shadows and highlights, a little dim, close red haze, heavy corners. */
    private static final AtmosphereGrade.Mood RED = new AtmosphereGrade.Mood(0.94f, 1.10f, 0.95f,
            1.12f, 0.72f, 0.70f, 1.10f, 0.80f, 0.76f,
            0.020f, 0.004f, 0.004f, 0.20f, 0.03f, 0.03f, 4.0f, 0.050f, 0.45f, 0.45f, 0.20f, 0.020f);
    private static final double WARNING_RANGE = 16.0;
    private static final float HUM_VOLUME = 0.6f;

    private final Map<AnomalySite, Level0LoopSound[]> hums = new HashMap<>();

    @Override
    public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        Level0LoopSound[] sounds = hums.get(site);
        if (sounds == null || sounds[0].dead()) {
            double[][] centres = Level0RedRooms.roomCentres(site);
            sounds = new Level0LoopSound[centres.length];
            for (int i = 0; i < centres.length; i++) {
                sounds[i] = new Level0LoopSound(ModSounds.L0_RED_ROOMS_HUM.get(), SoundSource.AMBIENT,
                        centres[i][0], LevelLayout.FLOOR_Y + 3.5, centres[i][1], false).play();
                sounds[i].target = HUM_VOLUME;
            }
            if (hums.size() > 16) {
                hums.clear();
            }
            hums.put(site, sounds);
        }
        for (Level0LoopSound sound : sounds) {
            sound.touch();
        }

        float weight;
        if (inside && Level0RedRooms.isInterior(site, player.getBlockX(), player.getBlockZ())) {
            weight = 0.9f;
        } else {
            double d = site.distanceTo(player.getX(), player.getZ());
            weight = d < WARNING_RANGE ? (float) (0.35 * (1.0 - d / WARNING_RANGE)) : 0.0f;
            if (inside) {
                weight = Math.max(weight, 0.4f); // the yellow corridor right outside
            }
        }
        if (weight > 0.0f) {
            AtmosphereGrade.setAnomalyMood(RED, weight);
        }
    }
}
