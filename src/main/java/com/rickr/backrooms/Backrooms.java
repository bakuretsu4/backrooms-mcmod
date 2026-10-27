package com.rickr.backrooms;

import com.rickr.backrooms.config.ClientSettings;
import com.rickr.backrooms.network.ModNetwork;
import com.rickr.backrooms.world.anomaly.Anomalies;
import com.rickr.backrooms.registry.ModAttachments;
import com.rickr.backrooms.registry.ModBlockEntities;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModChunkGenerators;
import com.rickr.backrooms.registry.ModCreativeTab;
import com.rickr.backrooms.registry.ModEntities;
import com.rickr.backrooms.registry.ModItems;
import com.rickr.backrooms.registry.ModParticles;
import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * Mod entry point. NeoForge constructs this once on both the client and the dedicated server.
 * Everything here must be safe on a server: client-only code lives in the {@code client} package
 * and is wired through {@code @EventBusSubscriber(value = Dist.CLIENT)} classes.
 */
@Mod(Backrooms.MOD_ID)
public final class Backrooms {
    public static final String MOD_ID = "backrooms";

    public Backrooms(IEventBus modBus, ModContainer container) {
        // Per-player camera/HUD settings (config/backrooms-client.toml). NeoForge only loads a
        // CLIENT config on clients, so this is harmless on a dedicated server.
        container.registerConfig(ModConfig.Type.CLIENT, ClientSettings.SPEC);
        // Deferred registers: blocks, items, entities, sounds, our chunk generator type, player data.
        // Level 0 revamp: the film's partitions, light strips, exit alcove, tape lines and props.
        com.rickr.backrooms.registry.Level0Blocks.bootstrap();
        // Level 39 Hounds/Facelings (and Clumps, Skin-stealers, Wretches): loads their registrations.
        com.rickr.backrooms.registry.Level39Mobs.bootstrap();
        // Level 39 "Megalophobia": the town, superstructure and tunnel blocks (and their items).
        com.rickr.backrooms.registry.Level39Blocks.bootstrap();
        // Level 39: the town/tunnel ambience, the indoor storm, echo quakes and far-off sounds.
        com.rickr.backrooms.registry.Level39Sounds.bootstrap();
        // Level 39 houses: wallpapers, floors, popcorn ceilings and bulbs, knock walls, period furniture.
        com.rickr.backrooms.registry.Level39HouseBlocks.bootstrap();
        // Level 39 anomalies: Window Shadows, the Hydrolitis plague and their sounds.
        com.rickr.backrooms.world.anomaly.level39.Level39AnomalyRegistry.bootstrap(modBus);
        // The side entrance to Level 39: the chairlift in Level 9's Crimson Field (blocks, seat, sounds).
        com.rickr.backrooms.world.anomaly.level39.gondola.GondolaRegistry.bootstrap();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModChunkGenerators.CHUNK_GENERATORS.register(modBus);
        ModAttachments.ATTACHMENTS.register(modBus);
        ModCreativeTab.TABS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);

        modBus.addListener(ModEntities::registerAttributes);
        modBus.addListener(ModNetwork::register);
        // What each level's anomalous areas do on the server (world/anomaly).
        Anomalies.bootstrap();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
