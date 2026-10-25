package com.rickr.backrooms.registry;

import com.mojang.serialization.MapCodec;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.BackroomsChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModChunkGenerators {
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Backrooms.MOD_ID);

    /** Referenced from data/backrooms/dimension/*.json as "type": "backrooms:backrooms". */
    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<BackroomsChunkGenerator>> BACKROOMS =
            CHUNK_GENERATORS.register("backrooms", () -> BackroomsChunkGenerator.CODEC);

    private ModChunkGenerators() {}
}
