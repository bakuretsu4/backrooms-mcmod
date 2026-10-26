package com.rickr.backrooms.client.anomaly;

import com.rickr.backrooms.world.anomaly.AnomalySite;
import net.minecraft.client.Minecraft;

/**
 * What an anomaly kind does on the CLIENT: sounds, the look of the picture, particles. Register one
 * per kind in {@link AnomalyClientEffects#bootstrap}.
 */
public interface AnomalyClientEffect {
    /**
     * Once per client tick for every site the server says is nearby (the one you're in included).
     *
     * @param inside        true if the local player is inside this site
     * @param secondsInside seconds since entering (0 when not inside)
     */
    void tick(Minecraft mc, AnomalySite site, boolean inside, float secondsInside);
}
