package com.rickr.backrooms.client.anomaly.level39.gondola;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.anomaly.level39.gondola.GondolaRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client wiring of the Crimson Field chairlift: the seat entity's renderer. */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class GondolaClientSetup {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GondolaRegistry.GONDOLA_CHAIR.get(), GondolaChairRenderer::new);
    }

    private GondolaClientSetup() {}
}
