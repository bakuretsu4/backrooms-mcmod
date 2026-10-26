package com.rickr.backrooms.world.anomaly.level0;

import com.rickr.backrooms.world.anomaly.Anomalies;
import com.rickr.backrooms.world.layout.Level0AnomalyPlan;

/**
 * Level 0's anomalous areas (placed by {@code Level0Layout}, see {@link Level0AnomalyPlan}):
 * <ul>
 *   <li>{@link RedRoomsBehavior}: the looping crimson rooms that seal behind you;</li>
 *   <li>{@link BlackoutBehavior}: a dark, flooded stretch of maze that wears on your nerves;</li>
 *   <li>{@link ManilaRoomBehavior}: the calm room the volunteers keep;</li>
 *   <li>{@link PeripheralShiftBehavior}: the pillar hall that rearranges itself when unseen;</li>
 *   <li>{@link PitfallsBehavior}: the pits that drop you somewhere else in Level 0.</li>
 * </ul>
 */
public final class Level0Anomalies {
    public static void register() {
        Anomalies.register(Level0AnomalyPlan.RED_ROOMS, new RedRoomsBehavior());
        Anomalies.register(Level0AnomalyPlan.BLACKOUT_ZONE, new BlackoutBehavior());
        Anomalies.register(Level0AnomalyPlan.MANILA_ROOM, new ManilaRoomBehavior());
        Anomalies.register(Level0AnomalyPlan.PERIPHERAL_SHIFT, new PeripheralShiftBehavior());
        Anomalies.register(Level0AnomalyPlan.PITFALLS, new PitfallsBehavior());
    }

    private Level0Anomalies() {}
}
