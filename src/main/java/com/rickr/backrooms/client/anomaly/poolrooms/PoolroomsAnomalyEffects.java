package com.rickr.backrooms.client.anomaly.poolrooms;

import com.rickr.backrooms.client.anomaly.AnomalyClientEffects;
import com.rickr.backrooms.world.layout.PoolroomsAnomalyPlan;

/**
 * The client effects of the Poolrooms' anomalies. Two more run on their own, outside any site:
 * {@link PoolAcoustics} (the sound of both pool levels) and {@link SanguineReservoirClient} (the
 * look, sounds and heavy water of the Sanguine Reservoir).
 */
public final class PoolroomsAnomalyEffects {
    public static void register() {
        AnomalyClientEffects.register(PoolroomsAnomalyPlan.DARK_CORNER, new DarkCornerEffect());
        AnomalyClientEffects.register(PoolroomsAnomalyPlan.STAIR_PIT, new StairPitEffect());
    }

    private PoolroomsAnomalyEffects() {}
}
