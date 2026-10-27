package com.rickr.backrooms.client.particle;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModParticles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Mod bus: how our particles are drawn. */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class ParticleSetup {
    @SubscribeEvent
    public static void registerProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.STEAM.get(), SteamParticle.Provider::new);
        event.registerSpriteSet(ModParticles.DUST_MOTE.get(), DustMoteParticle.Provider::new);
    }

    private ParticleSetup() {}
}
