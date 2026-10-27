package com.rickr.backrooms.world.anomaly;

import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * The server behaviours of every anomaly kind, by kind name ({@link AnomalySite#kind}).
 * Each level registers its own in {@link #bootstrap}.
 */
public final class Anomalies {
    private static final Map<String, AnomalyBehavior> BEHAVIORS = new HashMap<>();

    /** Called once during mod setup. One line per level. */
    public static void bootstrap() {
        com.rickr.backrooms.world.anomaly.poolrooms.PoolroomsAnomalies.register();
        com.rickr.backrooms.world.anomaly.level37.Level37Anomalies.register();
        com.rickr.backrooms.world.anomaly.level94.Level94Anomalies.register();
        com.rickr.backrooms.world.anomaly.level1.Level1Anomalies.register();
        com.rickr.backrooms.world.anomaly.level4.Level4Anomalies.register();
        com.rickr.backrooms.world.anomaly.level9.Level9Anomalies.register();
        com.rickr.backrooms.world.anomaly.level0.Level0Anomalies.register();
        com.rickr.backrooms.world.anomaly.level2.Level2Anomalies.register();
        com.rickr.backrooms.world.anomaly.level39.Level39Anomalies.register();
    }

    public static void register(String kind, AnomalyBehavior behavior) {
        if (BEHAVIORS.put(kind, behavior) != null) {
            throw new IllegalStateException("Anomaly kind registered twice: " + kind);
        }
    }

    @Nullable
    public static AnomalyBehavior get(String kind) {
        return BEHAVIORS.get(kind);
    }

    private Anomalies() {}
}
