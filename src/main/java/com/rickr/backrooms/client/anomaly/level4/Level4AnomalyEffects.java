package com.rickr.backrooms.client.anomaly.level4;

import com.rickr.backrooms.client.anomaly.AnomalyClientEffects;
import com.rickr.backrooms.world.layout.Level4AnomalyPlan;

/**
 * Level 4's anomalies on the client: what you hear and see. (The vanishing elevator needs nothing
 * here: its doors, light, chime and ride are all done by the server.)
 */
public final class Level4AnomalyEffects {
    /** Called from {@link AnomalyClientEffects#bootstrap}. */
    public static void register() {
        AnomalyClientEffects.register(Level4AnomalyPlan.LOOPING_STAIRWELL, new StairwellEchoEffect());
        AnomalyClientEffects.register(Level4AnomalyPlan.WINDOW, new WindowEffect());
        AnomalyClientEffects.register(Level4AnomalyPlan.CUBICLES, new CubiclesEffect());
    }

    private Level4AnomalyEffects() {}
}
