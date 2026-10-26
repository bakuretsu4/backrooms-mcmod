package com.rickr.backrooms.world.anomaly.level94;

import com.rickr.backrooms.world.anomaly.Anomalies;
import com.rickr.backrooms.world.layout.Level94AnomalyPlan;

/**
 * Server behaviours of Level 94's anomalies (where they are: {@link Level94AnomalyPlan}).
 * The Level 94 supplies (fountain, water tower taps, milk crates) live next to them: the taps and
 * crates are blocks of their own, the fountain is {@link Level94Fountain}.
 */
public final class Level94Anomalies {
    public static void register() {
        Anomalies.register(Level94AnomalyPlan.FURNITURE_DRIFT, new FurnitureDriftBehavior());
        Anomalies.register(Level94AnomalyPlan.WANDERING_DOOR, new WanderingDoorBehavior());
        Anomalies.register(Level94AnomalyPlan.BLINDING_HALL, new BlindingHallBehavior());
    }

    private Level94Anomalies() {}
}
