package com.rickr.backrooms;

import com.rickr.backrooms.network.ModNetwork;
import com.rickr.backrooms.registry.ModAttachments;
import com.rickr.backrooms.registry.ModBlockEntities;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModChunkGenerators;
import com.rickr.backrooms.registry.ModCreativeTab;
import com.rickr.backrooms.registry.ModEntities;
import com.rickr.backrooms.registry.ModItems;
import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * Mod entry point. NeoForge constructs this once on both the client and the dedicated server.
 * Everything here must be safe on a server: client-only code lives in the {@code client} package
 * and is wired through {@code @EventBusSubscriber(value = Dist.CLIENT)} classes.
 */
@Mod(Backrooms.MOD_ID)
public final class Backrooms {
    public static final String MOD_ID = "backrooms";

    public Backrooms(IEventBus modBus) {
        // Deferred registers: blocks, items, entities, sounds, our chunk generator type, player data.
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModChunkGenerators.CHUNK_GENERATORS.register(modBus);
        ModAttachments.ATTACHMENTS.register(modBus);
        ModCreativeTab.TABS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);

        modBus.addListener(ModEntities::registerAttributes);
        modBus.addListener(ModNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
