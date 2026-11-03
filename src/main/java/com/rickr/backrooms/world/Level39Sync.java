package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.Level2StatePayload;
import com.rickr.backrooms.network.Level39StatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tells every client that arrives in Level 39 or Level 2 that level's layout seed (server side), so
 * the client can work out the plan by itself. Level 39: which tunnel water is stagnant, where the
 * facade windows and the holes are ({@code client/Level39ClientState}). Level 2: where the corridor
 * twists and banks, for the spin illusion ({@code client/Level2ClientState}).
 *
 * <p>Same arrival points as {@link Level94Sync}: logging in, changing dimension, respawning.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level39Sync {
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendSeed(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendSeed(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendSeed(player);
        }
    }

    private static void sendSeed(ServerPlayer player) {
        BackroomsLevel floor = BackroomsLevel.fromDimension(player.level().dimension());
        if (floor == BackroomsLevel.LEVEL_39) {
            long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), BackroomsLevel.LEVEL_39);
            PacketDistributor.sendToPlayer(player, new Level39StatePayload(seed));
        } else if (floor == BackroomsLevel.LEVEL_2) {
            long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), BackroomsLevel.LEVEL_2);
            PacketDistributor.sendToPlayer(player, new Level2StatePayload(seed));
        }
    }

    private Level39Sync() {}
}
