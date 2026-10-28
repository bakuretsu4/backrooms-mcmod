package com.rickr.backrooms.client.anomaly.level0;

import com.rickr.backrooms.client.anomaly.AnomalyClientEffects;
import com.rickr.backrooms.world.layout.Level0AnomalyPlan;
import com.rickr.backrooms.world.layout.Level0HiddenRooms;

/**
 * What Level 0's anomalous areas look and sound like on the client (the server side is
 * {@code world/anomaly/level0}):
 * <ul>
 *   <li>{@link RedRoomsEffect}: the klaxon-synth hum and the red colour shift;</li>
 *   <li>{@link BlackoutEffect}: the buzz that never stays put, and the dark;</li>
 *   <li>{@link ManilaRoomEffect}: piano from nowhere, a warmer picture;</li>
 *   <li>{@link PeripheralShiftEffect}: the pillar hall's haze that makes it look endless;</li>
 *   <li>{@link PitfallsEffect}: darkness and rushing air as you fall, and the draught before;</li>
 *   <li>{@link HiddenRoomEffect}: each hidden room's sound, faint through its walls;</li>
 *   <li>{@link SetPieceEffects}: the atrium's cold air, the shafts' noises, the Front Office's
 *       alarm and its warm hidden room.</li>
 * </ul>
 */
public final class Level0AnomalyEffects {
    public static void register() {
        AnomalyClientEffects.register(Level0AnomalyPlan.RED_ROOMS, new RedRoomsEffect());
        AnomalyClientEffects.register(Level0AnomalyPlan.BLACKOUT_ZONE, new BlackoutEffect());
        AnomalyClientEffects.register(Level0AnomalyPlan.MANILA_ROOM, new ManilaRoomEffect());
        AnomalyClientEffects.register(Level0AnomalyPlan.PERIPHERAL_SHIFT, new PeripheralShiftEffect());
        AnomalyClientEffects.register(Level0AnomalyPlan.PITFALLS, new PitfallsEffect());
        HiddenRoomEffect hidden = new HiddenRoomEffect();
        for (String kind : Level0HiddenRooms.KINDS) {
            AnomalyClientEffects.register(kind, hidden);
        }
        AnomalyClientEffects.register(Level0AnomalyPlan.ATRIUM, new SetPieceEffects.Atrium());
        AnomalyClientEffects.register(Level0AnomalyPlan.SHAFT_HALL, new SetPieceEffects.ShaftHall());
        AnomalyClientEffects.register(Level0AnomalyPlan.FRONT_OFFICE, new SetPieceEffects.FrontOffice());
    }

    /**
     * How loud the fluorescent tubes may hum right now (0..1): lower near the Manila Room, where
     * "the hum fades". Asked by client/light/FixtureSounds for every humming tube.
     */
    public static float fixtureHumScale() {
        return ManilaRoomEffect.humScale();
    }

    private Level0AnomalyEffects() {}
}
