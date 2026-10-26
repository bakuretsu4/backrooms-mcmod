package com.rickr.backrooms.client.anomaly.poolrooms;

import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.AnomalyClientEffect;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.PoolroomsAnomalyPlan;
import com.rickr.backrooms.world.layout.PoolroomsLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * A stair pit on the client (the server side is StairPitBehavior): the light fades the deeper you
 * go, until near the bottom only your flashlight shows the next step.
 *
 * <p>Canon: both wikis' Level 37 (https://backrooms-wiki.wikidot.com/level-37): the deep pits the
 * staircases run into are dark, and dangerous to swim without equipment.
 */
final class StairPitEffect implements AnomalyClientEffect {
    /** Deep down in a pit: dark, colourless, a thick green-black haze close around you. */
    static final AtmosphereGrade.Mood DEPTHS = new AtmosphereGrade.Mood(0.30f, 1.05f, 0.55f,
            0.85f, 0.95f, 1.0f, 0.95f, 1.0f, 1.0f,
            0.0f, 0.004f, 0.006f, 0.0f, 0.02f, 0.03f, 1.5f, 0.15f, 0.9f, 0.55f, 0.05f, 0.010f);

    @Override
    public void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside) {
        if (!inside || mc.player == null) {
            return;
        }
        AtmosphereGrade.setAnomalyMood(DEPTHS, depth(mc));
    }

    /**
     * 0 with your eyes above the water, 1 at the bottom of the pit: how far down the shaft you are.
     * Also used by the Sanguine Reservoir's own look.
     */
    static float depth(Minecraft mc) {
        if (mc.player == null) {
            return 0.0f;
        }
        double surface = PoolroomsLayout.SURFACE_Y + 0.9;
        double bottom = PoolroomsAnomalyPlan.PIT_FLOOR_Y + 2.6; // eyes of someone standing on the bottom
        return Mth.clamp((float) ((surface - mc.player.getEyeY()) / (surface - bottom)), 0.0f, 1.0f);
    }
}
