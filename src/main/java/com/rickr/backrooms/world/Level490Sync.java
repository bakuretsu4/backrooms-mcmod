package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.Level490StatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tells every client that arrives in Level 490 the level's layout seed, so its sky can place the
 * far colossal chains (client/Level490ClientState). Same arrival points as {@link Level39Sync}.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level490Sync {
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendIfInLevel490(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendIfInLevel490(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendIfInLevel490(player);
        }
    }

    private static void sendIfInLevel490(ServerPlayer player) {
        if (BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_490) {
            return;
        }
        long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), BackroomsLevel.LEVEL_490);
        PacketDistributor.sendToPlayer(player, new Level490StatePayload(seed));
    }

    private Level490Sync() {}
}
