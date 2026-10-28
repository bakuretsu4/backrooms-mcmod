package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.particle.FallingLeafParticle;
import com.rickr.backrooms.client.particle.SporeHazeParticle;
import com.rickr.backrooms.entity.VenusFlytrap;
import com.rickr.backrooms.network.FlytrapStrugglePayload;
import com.rickr.backrooms.registry.Level227Flora;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The client side of Level 227's flora, kept in one place: the giant Venus flytrap's renderer and
 * model, the GammaPali haze and falling-leaf particles, and the struggle keys.
 *
 * <p>Struggling: while you are trapped inside a flytrap, every press of attack or jump is sent to
 * the server as a struggle (network/FlytrapStrugglePayload), which loosens the trap's grip
 * (entity/VenusFlytrap). The presses are taken straight off the keys at the start of each client
 * tick, before the game handles them: the game's own attack handling skips clicks for several
 * reasons (a cool-down after swinging at air, a missing target) and those clicks must still count.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level227FloraClient {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Level227Flora.VENUS_FLYTRAP.get(), VenusFlytrapRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(VenusFlytrapModel.LAYER, VenusFlytrapModel::createLayer);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(Level227Flora.SPORE_HAZE.get(), SporeHazeParticle.Provider::new);
        event.registerSpriteSet(Level227Flora.FALLING_LEAF.get(), FallingLeafParticle.Provider::new);
    }

    private static boolean trapped(Minecraft minecraft) {
        return minecraft.player != null && minecraft.player.getVehicle() instanceof VenusFlytrap;
    }

    /** Each attack or jump press while trapped is one struggle. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!trapped(minecraft) || minecraft.screen != null) {
            return;
        }
        int presses = 0;
        while (minecraft.options.keyAttack.consumeClick()) {
            presses++;
        }
        while (minecraft.options.keyJump.consumeClick()) {
            presses++;
        }
        for (int i = 0; i < presses; i++) {
            PacketDistributor.sendToServer(FlytrapStrugglePayload.INSTANCE);
        }
    }

    /** Any attack that still gets through while trapped does nothing (no swing at the plant). */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && trapped(Minecraft.getInstance())) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private Level227FloraClient() {}
}
