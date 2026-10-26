package com.rickr.backrooms.client.anomaly.level4;

import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level4AnomalyPlan;
import com.rickr.backrooms.world.layout.Level4AnomalyPlan.StairFrame;
import com.rickr.backrooms.world.layout.LevelLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The looping stairwell sounds like a real one with many floors: now and then, footsteps on the
 * concrete and a door banging somewhere far above or below you, echoing down the shaft. Nobody is
 * ever there (https://backrooms.fandom.com/wiki/Level_4: the stairwells only suggest many floors).
 */
final class StairwellEchoEffect implements AnomalyClientEffect {
    private static final long MIN_GAP_MS = 14_000L;
    private static final long MAX_GAP_MS = 32_000L;

    private final RandomSource random = RandomSource.create();
    private long nextMs;

    @Override
    public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        if (!inside || mc.isPaused()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (nextMs == 0L || secondsInside < 0.1f) {
            nextMs = now + 4_000L + random.nextInt(6_000); // the first one comes soon after you step in
            return;
        }
        if (now < nextMs) {
            return;
        }
        nextMs = now + MIN_GAP_MS + (long) (random.nextFloat() * (MAX_GAP_MS - MIN_GAP_MS));
        StairFrame f = Level4AnomalyPlan.stairFrame(site);
        double x = f.worldX(4, 5) + 0.5;
        double z = f.worldZ(4, 5) + 0.5;
        // One or two storeys away, up or down: far enough to be "somebody else's floor".
        double dy = (random.nextBoolean() ? 1 : -1) * (Level4AnomalyPlan.STAIR_PERIOD + random.nextInt(8));
        mc.level.playLocalSound(x, LevelLayout.FLOOR_Y + 2.0 + dy, z, ModSounds.L4_STAIRWELL_ECHO.get(), SoundSource.AMBIENT,
                1.4f, 0.9f + random.nextFloat() * 0.2f, false);
    }
}
