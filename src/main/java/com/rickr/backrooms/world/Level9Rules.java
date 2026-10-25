package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.layout.Level9Layout;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server rules of Level 9's exit: the highway underpass.
 *
 * <p>Somewhere in every 512x512 stretch of suburb a concrete highway embankment crosses a street,
 * which runs through it in a long, unlit underpass. Walk deep enough into the dark (the middle of
 * the tunnel, where the road surface turns to worn concrete) and you come out in the real world.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level9Rules {
    /** Only people down on the road count, not anyone somehow up on the highway deck (y49). */
    private static final double MAX_Y = 47.0;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        if (BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_9 || player.getY() >= MAX_Y) {
            return;
        }
        Level9Layout layout = (Level9Layout) BackroomsLevel.LEVEL_9.layout();
        long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), BackroomsLevel.LEVEL_9);
        if (layout.isEscapeZone(seed, player.getBlockX(), player.getBlockZ())) {
            Travel.useExit(player, BackroomsLevel.LEVEL_9); // the last floor: this escapes
        }
    }

    private Level9Rules() {}
}
