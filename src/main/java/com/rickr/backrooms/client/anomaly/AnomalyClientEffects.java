package com.rickr.backrooms.client.anomaly;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.world.Travel;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client effects of every anomaly kind, and the tick that drives them. */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class AnomalyClientEffects {
    private static final Map<String, AnomalyClientEffect> EFFECTS = new HashMap<>();

    /** Called once during client setup. One line per level. */
    public static void bootstrap() {
        com.rickr.backrooms.client.anomaly.poolrooms.PoolroomsAnomalyEffects.register();
        com.rickr.backrooms.client.anomaly.level94.Level94AnomalyEffects.register();
        com.rickr.backrooms.client.anomaly.level37.Level37AnomalyEffects.register();
        com.rickr.backrooms.client.anomaly.level1.Level1AnomalyEffects.register();
        com.rickr.backrooms.client.anomaly.level4.Level4AnomalyEffects.register();
        com.rickr.backrooms.client.anomaly.level9.Level9AnomalyEffects.register();
        com.rickr.backrooms.client.anomaly.level0.Level0AnomalyEffects.register();
        com.rickr.backrooms.client.anomaly.level2.Level2AnomalyEffects.register();
        com.rickr.backrooms.client.anomaly.level39.Level39AnomalyEffects.register();
    }

    public static void register(String kind, AnomalyClientEffect effect) {
        if (EFFECTS.put(kind, effect) != null) {
            throw new IllegalStateException("Anomaly client effect registered twice: " + kind);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        // Effects re-assert their picture mood every tick; whatever nobody asks for fades away.
        AtmosphereGrade.clearAnomalyMood();
        if (mc.level == null || mc.player == null || !Travel.inBackrooms(mc.level)) {
            AnomalyClientState.clear();
            return;
        }
        AnomalySite current = AnomalyClientState.current();
        boolean currentListed = false;
        for (AnomalySite site : AnomalyClientState.nearby()) {
            boolean inside = site.equals(current);
            currentListed |= inside;
            run(mc, site, inside);
        }
        if (current != null && !currentListed) {
            run(mc, current, true);
        }
    }

    private static void run(Minecraft mc, AnomalySite site, boolean inside) {
        AnomalyClientEffect effect = EFFECTS.get(site.kind());
        if (effect != null) {
            effect.tick(mc, site, inside, inside ? AnomalyClientState.secondsInside() : 0.0f);
        }
    }

    private AnomalyClientEffects() {}
}
