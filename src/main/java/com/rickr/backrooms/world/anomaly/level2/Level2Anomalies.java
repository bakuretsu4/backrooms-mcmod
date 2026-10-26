package com.rickr.backrooms.world.anomaly.level2;

import com.rickr.backrooms.world.anomaly.Anomalies;

/**
 * Server behaviours of Level 2's anomalies (where they are: world/layout/Level2AnomalyPlan; their
 * shapes: {@link Level2Sites}). The series-circuit lamp failures need no server part: every
 * client works them out from the clock (client/light/FixturePower).
 */
public final class Level2Anomalies {
    public static void register() {
        Anomalies.register(Level2Sites.BROWNOUT, new BrownoutBehavior());
        Anomalies.register(Level2Sites.HOT_ZONE, new HotZoneBehavior());
        Anomalies.register(Level2Sites.VOID_DOOR, new VoidDoorBehavior());
        Anomalies.register(Level2Sites.LOOP_HALL, new LoopHallBehavior());
    }

    private Level2Anomalies() {}
}
