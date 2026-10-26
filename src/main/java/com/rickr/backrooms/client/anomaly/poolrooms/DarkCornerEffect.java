package com.rickr.backrooms.client.anomaly.poolrooms;

import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.PoolroomsAnomalyPlan;
import com.rickr.backrooms.world.layout.PoolroomsLayout;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * A dark corner on the client (the server side is DarkCornerBehavior): the faint chorus of chants
 * that draws you in, and the light fading away while you linger.
 *
 * <p>Canon: Wikidot Level 37.1 (https://backrooms-wiki.wikidot.com/level-37-1): wanderers are
 * drawn in by "the faintest chorus of chants in the distance", and on the way "all lighting fades
 * away". The Level 37 journal (https://backrooms-wiki.wikidot.com/level-37): dread as they notice
 * "the strange, lightless corners".
 * <ul>
 *   <li>Within 16 blocks of a dark corner you hear the chants, very faintly, from its darkest
 *       spot: a warning, or a lure.</li>
 *   <li>Inside, they grow, and the picture slowly loses its light and colour, sinking into a dim
 *       red-black, in step with the server's drift timer (20-30 s). Step out and it all eases
 *       back.</li>
 * </ul>
 */
final class DarkCornerEffect implements AnomalyClientEffect {
    /** Chants are heard from this far away (blocks). */
    private static final float HEAR_RANGE = 16.0f;
    /** Their volume at the edge of the nook ... */
    private static final float NEAR_VOLUME = 0.14f;
    /** ... and right before you drift. */
    private static final float DRIFT_VOLUME = 0.7f;

    /**
     * Where the light goes: almost black, colour drained, shadows sliding toward dark red, a thick
     * dark haze from a couple of blocks on.
     */
    static final AtmosphereGrade.Mood FADED = new AtmosphereGrade.Mood(0.22f, 1.10f, 0.45f,
            1.10f, 0.78f, 0.78f, 1.04f, 0.86f, 0.86f,
            0.004f, 0.0f, 0.0f, 0.05f, 0.0f, 0.0f, 2.0f, 0.12f, 0.85f, 0.60f, 0.04f, 0.020f);

    /** One chant loop per nearby corner, by site. */
    private final Map<AnomalySite, FadingLoopSound> chants = new HashMap<>();

    @Override
    public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        chants.values().removeIf(FadingLoopSound::isStopped);
        if (mc.player == null || PoolroomsLayout.inReservoir(mc.player.getX())) {
            return;
        }
        double ax = site.anchorX() + 0.5;
        double az = site.anchorZ() + 0.5;
        float distance = (float) Math.sqrt(mc.player.distanceToSqr(ax, mc.player.getY(), az));
        float driftSeconds = PoolroomsAnomalyPlan.driftTicks(site) / 20.0f;
        float progress = inside ? Mth.clamp(secondsInside / driftSeconds, 0.0f, 1.0f) : 0.0f;

        float volume = inside ? Mth.lerp(progress, NEAR_VOLUME, DRIFT_VOLUME)
                : NEAR_VOLUME * Mth.clamp(1.0f - distance / HEAR_RANGE, 0.0f, 1.0f);
        if (volume > 0.0f) {
            FadingLoopSound chant = chants.get(site);
            if (chant == null || !mc.getSoundManager().isActive(chant)) {
                chant = new FadingLoopSound(ModSounds.POOL_CHANT.get(), false, ax, PoolroomsLayout.SURFACE_Y + 1.5, az, 0.02f);
                mc.getSoundManager().play(chant);
                chants.put(site, chant);
            }
            chant.feed(volume);
        }

        if (inside) {
            // The first couple of seconds nothing seems wrong; then the light drains away steadily.
            float fade = Mth.clamp((secondsInside - 2.0f) / Math.max(1.0f, driftSeconds - 4.0f), 0.0f, 1.0f);
            AtmosphereGrade.setAnomalyMood(FADED, fade);
        }
    }
}
