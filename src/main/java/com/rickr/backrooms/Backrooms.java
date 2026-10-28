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
        // Level 2 revamp: pipework, bulkhead rings, maintenance-room furniture, stencils and the exit bulkhead.
        com.rickr.backrooms.registry.Level2Blocks.bootstrap();
        // Level 39 Hounds/Facelings (and Clumps, Skin-stealers, Wretches): loads their registrations.
        com.rickr.backrooms.registry.Level39Mobs.bootstrap();
        // Level 39 "Megalophobia": the town, superstructure and tunnel blocks (and their items).
        com.rickr.backrooms.registry.Level39Blocks.bootstrap();
        // Level 39: the town/tunnel ambience, the indoor storm, echo quakes and far-off sounds.
        com.rickr.backrooms.registry.Level39Sounds.bootstrap();
        // Level 2: the pipe corridor's knocks, clanks, creaks, steam leaks, pump hum and drips.
        com.rickr.backrooms.registry.Level2Audio.bootstrap();
        // Level 39 houses: wallpapers, floors, popcorn ceilings and bulbs, knock walls, period furniture.
        com.rickr.backrooms.registry.Level39HouseBlocks.bootstrap();
        // Level 39 anomalies: Window Shadows, the Hydrolitis plague and their sounds.
        com.rickr.backrooms.world.anomaly.level39.Level39AnomalyRegistry.bootstrap(modBus);
        // The side entrance to Level 39: the chairlift in Level 9's Crimson Field (blocks, seat, sounds).
        com.rickr.backrooms.world.anomaly.level39.gondola.GondolaRegistry.bootstrap();
        // Chalk: the chalk stick and the marks players draw with it on walls, floors and ceilings.
        com.rickr.backrooms.registry.ChalkRegistry.bootstrap();
        // Flares and glowsticks: the items, the thrown light entities and their sounds.
        com.rickr.backrooms.registry.LightSupplies.bootstrap();
        // Hiding in furniture: getting in and out of lockers and wardrobes, crawling under desks and beds, gasping.
        com.rickr.backrooms.registry.HidingAudio.bootstrap();
        // Separate realities: Level 1's dead lamps for the "lights out" drift, and the snap-back thump.
        com.rickr.backrooms.registry.DriftRegistry.bootstrap();
        // Level 227 "Megaflora" flora: the giant tree's wood, the forest floor and plants, GammaPali,
        // the Palette, Lestron, the giant Venus flytrap, and their sounds.
        com.rickr.backrooms.registry.Level227Blocks.bootstrap();
        com.rickr.backrooms.registry.Level227Flora.bootstrap();
        com.rickr.backrooms.registry.Level227FloraSounds.bootstrap();
        // Level 227 atmosphere: the forest/void/sun sounds, the tree's lurches, its music, the fallen void bubble.
        com.rickr.backrooms.registry.Level227Atmos.bootstrap();
        // Level 227 predators: the wolves of the high branches, the stags and the squirrel swarms.
        com.rickr.backrooms.registry.Level227Predators.bootstrap();
        // Level 227 grazers: the fifteen-eyed bears, the scarred does, the giant boars and boar meat.
        com.rickr.backrooms.registry.Level227Grazers.bootstrap();
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
