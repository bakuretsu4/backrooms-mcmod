package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.Level39StatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tells every client that arrives in Level 39 the level's layout seed (server side), so the client
 * can work out the town plan by itself: which tunnel water is stagnant, where the facade windows
 * and the holes are. The client keeps it in {@code client/Level39ClientState}.
 *
 * <p>Same arrival points as {@link Level94Sync}: logging in, changing dimension, respawning.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level39Sync {
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendIfInLevel39(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendIfInLevel39(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendIfInLevel39(player);
        }
    }

    private static void sendIfInLevel39(ServerPlayer player) {
        if (BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_39) {
            return;
        }
        long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), BackroomsLevel.LEVEL_39);
        PacketDistributor.sendToPlayer(player, new Level39StatePayload(seed));
    }

    private Level39Sync() {}
}
