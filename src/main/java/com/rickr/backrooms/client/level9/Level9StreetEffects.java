package com.rickr.backrooms.client.level9;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.yard.ParkedCarBlock;
import com.rickr.backrooms.network.Level9StreetPayload;
import com.rickr.backrooms.registry.ModBlockEntities;
import com.rickr.backrooms.registry.ModBlocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Client setup for Level 9's street life (mod-bus events, routed automatically): the swing
 * set's seat renderer and the parked cars' paint colour. Also receives the test command's
 * {@link Level9StreetPayload} and starts the effect it asks for.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level9StreetEffects {
    private Level9StreetEffects() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.L9_SWING_SET.get(), SwingSetRenderer::new);
    }

    /** The car models' paint is grey with tint index 0: multiply it by the PAINT colour. */
    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> tintIndex == 0 ? state.getValue(ParkedCarBlock.PAINT).rgb : 0xFFFFFF,
                ModBlocks.L9_PARKED_CAR.get());
    }

    /** From {@code /backrooms event lampsbehind|dogs} (command/Level9StreetCommand). */
    public static void receive(int kind) {
        if (kind == Level9StreetPayload.LAMPS_BEHIND) {
            LampsBehind.forceRun();
        } else if (kind == Level9StreetPayload.DOGS) {
            DogsGoQuiet.start();
        }
    }
}
