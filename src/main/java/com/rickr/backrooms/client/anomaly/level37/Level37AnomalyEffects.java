package com.rickr.backrooms.client.anomaly.level37;

import com.rickr.backrooms.client.anomaly.AnomalyClientEffects;
import com.rickr.backrooms.world.layout.Level37AnomalyPlan;

/**
 * Client effects of Level 37's anomalous areas: the void opening's glare and tone, the dark
 * opening's cold, the looping tunnel's dimming light. The fades to white / black and the arrival
 * fades after them are drawn by {@link Level37AnomalyOverlay}.
 */
public final class Level37AnomalyEffects {
    public static void register() {
        AnomalyClientEffects.register(Level37AnomalyPlan.VOID_OPENING, new VoidOpeningEffect());
        AnomalyClientEffects.register(Level37AnomalyPlan.DARK_OPENING, new DarkOpeningEffect());
        AnomalyClientEffects.register(Level37AnomalyPlan.LOOPING_TUNNEL, new LoopingTunnelEffect());
    }

    private Level37AnomalyEffects() {}
}
