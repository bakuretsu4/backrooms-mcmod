package com.rickr.backrooms.world.anomaly.level0;

import com.rickr.backrooms.world.anomaly.Anomalies;
import com.rickr.backrooms.world.layout.Level0AnomalyPlan;
import com.rickr.backrooms.world.layout.Level0HiddenRooms;

/**
 * Level 0's anomalous areas (placed by {@code Level0Layout}, see {@link Level0AnomalyPlan}):
 * <ul>
 *   <li>{@link RedRoomsBehavior}: the looping crimson rooms that seal behind you;</li>
 *   <li>{@link BlackoutBehavior}: a dark, flooded stretch of maze that wears on your nerves;</li>
 *   <li>{@link ManilaRoomBehavior}: the calm room the volunteers keep;</li>
 *   <li>{@link PeripheralShiftBehavior}: the pillar hall that rearranges itself when unseen;</li>
 *   <li>{@link PitfallsBehavior}: the pits that drop you somewhere else in Level 0;</li>
 *   <li>{@link HiddenRoomBehavior}: the four kinds of hidden room (slit, stockroom, crawlspace, sealed);</li>
 *   <li>{@link Level0LocalEvents} (not a site): a room's lights dying as you walk in, a far district
 *       going dark, a corridor's tubes dying one by one behind you;</li>
 *   <li>{@link AtriumBehavior}, {@link ShaftHallBehavior}, {@link FrontOfficeBehavior}: the three
 *       set pieces (the watcher on the balcony, falling tiles, the drawn door that is sometimes real).</li>
 * </ul>
 */
public final class Level0Anomalies {
    public static void register() {
        Anomalies.register(Level0AnomalyPlan.RED_ROOMS, new RedRoomsBehavior());
        Anomalies.register(Level0AnomalyPlan.BLACKOUT_ZONE, new BlackoutBehavior());
        Anomalies.register(Level0AnomalyPlan.MANILA_ROOM, new ManilaRoomBehavior());
        Anomalies.register(Level0AnomalyPlan.PERIPHERAL_SHIFT, new PeripheralShiftBehavior());
        Anomalies.register(Level0AnomalyPlan.PITFALLS, new PitfallsBehavior());
        HiddenRoomBehavior hidden = new HiddenRoomBehavior();
        for (String kind : Level0HiddenRooms.KINDS) {
            Anomalies.register(kind, hidden);
        }
        Anomalies.register(Level0AnomalyPlan.ATRIUM, new AtriumBehavior());
        Anomalies.register(Level0AnomalyPlan.SHAFT_HALL, new ShaftHallBehavior());
        Anomalies.register(Level0AnomalyPlan.FRONT_OFFICE, new FrontOfficeBehavior());
        Level0LocalEvents.register();
    }

    private Level0Anomalies() {}
}
