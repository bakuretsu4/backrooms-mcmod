package com.rickr.backrooms.world.anomaly.level1;

import com.rickr.backrooms.world.anomaly.Anomalies;
import com.rickr.backrooms.world.layout.Level1AnomalyPlan;

/**
 * Level 1's anomalous areas (placed by {@code Level1Layout}, shaped by {@link Level1AnomalyPlan}):
 * <ul>
 *   <li>{@link CrateHallBehavior}: crates appear behind you and vanish when nobody looks;</li>
 *   <li>{@link PaintingRoomBehavior}: crude paintings that change while unseen or unlit;</li>
 *   <li>{@link FogPuddlesBehavior}: low fog whose condensation puddles come and go;</li>
 *   <li>{@link CorruptedCorridorBehavior}: Level 1.1, the corridor that gets worse as you go.</li>
 * </ul>
 */
public final class Level1Anomalies {
    public static void register() {
        Anomalies.register(Level1AnomalyPlan.CRATE_HALL, new CrateHallBehavior());
        Anomalies.register(Level1AnomalyPlan.PAINTING_ROOM, new PaintingRoomBehavior());
        Anomalies.register(Level1AnomalyPlan.FOG_PUDDLES, new FogPuddlesBehavior());
        Anomalies.register(Level1AnomalyPlan.CORRUPTED_CORRIDOR, new CorruptedCorridorBehavior());
    }

    private Level1Anomalies() {}
}
