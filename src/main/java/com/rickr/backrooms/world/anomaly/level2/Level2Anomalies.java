package com.rickr.backrooms.world.anomaly.level2;

import com.rickr.backrooms.world.anomaly.Anomalies;

/**
 * Server behaviours of Level 2's anomalies (where they are: world/layout/Level2AnomalyPlan; their
 * shapes: {@link Level2Sites}). Level 2 is one straight corridor, so only the brownout does
 * anything on the server; the oddities are blocks and client effects, and the series-circuit
 * lamp failures need no server part: every client works them out from the clock
 * (client/light/FixturePower).
 */
public final class Level2Anomalies {
    public static void register() {
        Anomalies.register(Level2Sites.BROWNOUT, new BrownoutBehavior());
    }

    private Level2Anomalies() {}
}
