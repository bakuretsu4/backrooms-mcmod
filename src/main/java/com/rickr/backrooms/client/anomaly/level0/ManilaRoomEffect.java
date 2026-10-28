package com.rickr.backrooms.client.anomaly.level0;

import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level0ManilaRoom;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * The Manila Room on the client (https://backrooms-wiki.wikidot.com/manila-room): near it "the hum
 * fades and piano music plays from nowhere". The hum fades on its own (there are no lamps around
 * the room); this plays the piano, the same in both ears (no direction: from nowhere), louder the
 * closer you get, and inside the room the picture warms up a little.
 */
final class ManilaRoomEffect implements AnomalyClientEffect {
    /**
     * Warm and calm, next to Level 0's look: the same brightness (AtmosphereGrade's LEVEL_0
     * exposure), a touch of honey (more red, more blue cut), a little more colour, softer blacks,
     * no haze, a softer glow.
     */
    private static final AtmosphereGrade.Mood WARM = new AtmosphereGrade.Mood(1.23f, 1.0f, 1.0f,
            1.02f, 0.98f, 0.74f, 1.05f, 1.0f, 0.88f,
            0.010f, 0.008f, 0.004f, 0.40f, 0.34f, 0.20f, 30.0f, 0.010f, 0.0f, 0.26f, 0.20f, 0.006f);
    /** The piano starts this far (blocks) from the middle of the room and is full volume inside. */
    private static final double HEAR_FROM = 18.0;
    private static final double FULL_WITHIN = 5.0;
    private static final float PIANO_VOLUME = 0.45f;
    /** Right by the room the tubes' hum is down to this fraction. */
    private static final float QUIETEST_HUM = 0.1f;

    /** How loud the tubes may hum (see {@link #humScale}) and when that was last worked out (ms). */
    private static float humScale = 1.0f;
    private static long humScaleMs;

    private final Map<AnomalySite, Level0LoopSound> pianos = new HashMap<>();

    /** 1 normally; lower near a Manila Room. Goes back to 1 by itself once no room is near. */
    static float humScale() {
        return System.currentTimeMillis() - humScaleMs > 250L ? 1.0f : humScale;
    }

    @Override
    public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        double[] centre = Level0ManilaRoom.centre(site);
        double d = Math.hypot(player.getX() - centre[0], player.getZ() - centre[1]);
        if (d < HEAR_FROM) {
            Level0LoopSound piano = pianos.get(site);
            if (piano == null || piano.dead()) {
                if (pianos.size() > 8) {
                    pianos.clear();
                }
                piano = new Level0LoopSound(ModSounds.L0_MANILA_PIANO.get(), SoundSource.AMBIENT, 0, 0, 0, true).play();
                piano.fade = 0.03f;
                pianos.put(site, piano);
            }
            piano.touch();
            double t = Math.min(1.0, Math.max(0.0, (HEAR_FROM - d) / (HEAR_FROM - FULL_WITHIN)));
            piano.target = (float) (PIANO_VOLUME * t);
            humScale = (float) (1.0 - (1.0 - QUIETEST_HUM) * t);
            humScaleMs = System.currentTimeMillis();
        }
        if (inside && Level0ManilaRoom.isInside(site, player.getBlockX(), player.getBlockZ())) {
            AtmosphereGrade.setAnomalyMood(WARM, 0.7f);
        }
    }
}
