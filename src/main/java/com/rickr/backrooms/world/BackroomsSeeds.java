package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.RandomState;

/** Derives each level's layout seed from the world seed, so every world gets its own Backrooms. */
public final class BackroomsSeeds {
    public static long layoutSeed(RandomState randomState, BackroomsLevel level) {
        return randomState.getOrCreateRandomFactory(Backrooms.id("layout/" + level.getSerializedName()))
                .at(0, 0, 0)
                .nextLong();
    }

    public static long layoutSeed(ServerLevel serverLevel, BackroomsLevel level) {
        return layoutSeed(serverLevel.getChunkSource().randomState(), level);
    }

    private BackroomsSeeds() {}
}
