package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.Level94StatePayload;
import com.rickr.backrooms.world.layout.Level94Castle;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Keeps every client in Level 94 in step with the server (server side):
 * <ul>
 *   <li><b>The clock:</b> when someone arrives in an empty Level 94 (login, travel or respawn) the
 *       day restarts at morning, so nobody ever steps in at midnight. Arrivals are told when the
 *       current day began ({@link Level94Clock}), and so is everyone when it restarts.</li>
 *   <li><b>The castle:</b> every 2 seconds we check which castle is nearest to each player and tell
 *       them when it changes, so the far-off castle on the horizon is the right one.</li>
 * </ul>
 * The client handler is in {@code network/ModNetwork}.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level94Sync {
    /** How often (ticks) we look for a nearer castle. */
    private static final int CASTLE_CHECK_TICKS = 40;

    /** The castle centre (packed x/z) each player was last told about. Server thread only. */
    private static final Map<UUID, Long> LAST_CASTLE = new HashMap<>();

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && inLevel94(player)) {
            arrive(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && inLevel94(player)) {
            arrive(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && inLevel94(player)) {
            arrive(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_CASTLE.remove(event.getEntity().getUUID());
    }

    /** Every 2 s: has the player walked far enough that another castle is now the nearest? */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % CASTLE_CHECK_TICKS != 0
                || !inLevel94(player)) {
            return;
        }
        Long last = LAST_CASTLE.get(player.getUUID());
        if (last == null || last != pack(nearestCastle(player))) {
            send(player);
        }
    }

    /** Tells everyone in Level 94 the current state (after the clock was restarted or moved). */
    public static void sendToAll(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            send(player);
        }
    }

    private static void arrive(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        boolean alone = level.players().stream().noneMatch(other -> other != player);
        if (alone) {
            // Nobody was here: a fresh morning for the newcomer.
            Level94Clock.restartAtMorning(level);
            sendToAll(level);
        } else {
            send(player);
        }
    }

    private static void send(ServerPlayer player) {
        int[] castle = nearestCastle(player);
        LAST_CASTLE.put(player.getUUID(), pack(castle));
        PacketDistributor.sendToPlayer(player,
                new Level94StatePayload(Level94Clock.start(player.serverLevel()), castle[0], castle[1]));
    }

    private static int[] nearestCastle(ServerPlayer player) {
        long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), BackroomsLevel.LEVEL_94);
        return Level94Castle.nearest(seed, player.getBlockX(), player.getBlockZ());
    }

    private static long pack(int[] xz) {
        return ((long) xz[0] << 32) | (xz[1] & 0xFFFFFFFFL);
    }

    private static boolean inLevel94(ServerPlayer player) {
        return BackroomsLevel.fromDimension(player.level().dimension()) == BackroomsLevel.LEVEL_94;
    }

    private Level94Sync() {}
}
