package com.rickr.backrooms.client.anomaly.level1;

import com.rickr.backrooms.client.anomaly.AnomalyClientEffects;
import com.rickr.backrooms.world.layout.Level1AnomalyPlan;

/**
 * Client side of Level 1's anomalies: the low fog over the puddle patches and the picture of the
 * Corrupted Corridor's sectors. (The crate halls and painting rooms need nothing on the client:
 * the server moves their blocks.)
 */
public final class Level1AnomalyEffects {
    public static void register() {
        AnomalyClientEffects.register(Level1AnomalyPlan.FOG_PUDDLES, new FogPuddlesEffect());
        AnomalyClientEffects.register(Level1AnomalyPlan.CORRUPTED_CORRIDOR, new CorruptedCorridorEffect());
    }

    private Level1AnomalyEffects() {}
}
