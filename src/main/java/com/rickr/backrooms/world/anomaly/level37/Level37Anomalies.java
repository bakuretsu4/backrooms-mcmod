package com.rickr.backrooms.world.anomaly.level37;

import com.rickr.backrooms.world.anomaly.Anomalies;
import com.rickr.backrooms.world.layout.Level37AnomalyPlan;

/**
 * Server behaviours of Level 37's anomalous areas (where they are: {@link Level37AnomalyPlan}).
 * The healing water ({@link HealingWater}) is the whole level's water, not a site, and runs on its own.
 */
public final class Level37Anomalies {
    public static void register() {
        Anomalies.register(Level37AnomalyPlan.VOID_OPENING, new VoidOpening());
        Anomalies.register(Level37AnomalyPlan.DARK_OPENING, new DarkOpening());
        Anomalies.register(Level37AnomalyPlan.LOOPING_TUNNEL, new LoopingTunnel());
    }

    private Level37Anomalies() {}
}
