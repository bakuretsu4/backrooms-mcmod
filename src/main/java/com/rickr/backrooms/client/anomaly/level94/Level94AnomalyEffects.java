package com.rickr.backrooms.client.anomaly.level94;

import com.rickr.backrooms.client.anomaly.AnomalyClientEffects;
import com.rickr.backrooms.world.layout.Level94AnomalyPlan;

/**
 * Client effects of Level 94's anomalies. Only the blinding hall needs one: the furniture drift
 * and the wandering door happen in the world itself (blocks moved by the server while nobody is
 * looking), so there is nothing extra to draw or play.
 */
public final class Level94AnomalyEffects {
    public static void register() {
        AnomalyClientEffects.register(Level94AnomalyPlan.BLINDING_HALL, new BlindingHallEffect());
    }

    private Level94AnomalyEffects() {}
}
