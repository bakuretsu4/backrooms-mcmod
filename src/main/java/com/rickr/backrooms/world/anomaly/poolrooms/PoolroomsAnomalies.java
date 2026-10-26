package com.rickr.backrooms.world.anomaly.poolrooms;

import com.rickr.backrooms.world.anomaly.Anomalies;
import com.rickr.backrooms.world.layout.PoolroomsAnomalyPlan;

/**
 * The server behaviours of the Poolrooms' anomalies. Where they are and what they look like is in
 * {@link PoolroomsAnomalyPlan}; the sound dampening of both pool levels and the Sanguine
 * Reservoir's look and sounds are client-side ({@code client/anomaly/poolrooms}).
 */
public final class PoolroomsAnomalies {
    public static void register() {
        Anomalies.register(PoolroomsAnomalyPlan.DARK_CORNER, new DarkCornerBehavior());
        Anomalies.register(PoolroomsAnomalyPlan.STAIR_PIT, new StairPitBehavior());
    }

    private PoolroomsAnomalies() {}
}
