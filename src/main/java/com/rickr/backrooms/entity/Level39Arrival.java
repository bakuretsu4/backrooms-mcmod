package com.rickr.backrooms.entity;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A minute's grace for anyone who has just arrived in Level 39 (same idea as Level 227's). You
 * come up out of Level 37's pits (or the Poolrooms' archway) with nothing, in the dark tunnels, often a long wade from the ladder:
 * without this a Wretch or Hound could appear and kill you before you had any chance to get your
 * bearings. Every Level 39 spawner ({@link Level39Spawner}, {@link TunnelDwellers}, the town hole)
 * asks {@link #inGrace} before placing anything near a player.
 *
 * <p>Arriving also clears out any hostile Level 39 creature already standing within
 * {@value #CLEAR_RADIUS} blocks of where you land, since the grace would mean nothing if one were
 * waiting right at the arrival spot.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level39Arrival {
    /** One minute after arriving before anything may appear. */
    public static final int ARRIVAL_GRACE_TICKS = 1200;
    /** Hostile creatures this close to the arrival spot are removed when you arrive. */
    public static final double CLEAR_RADIUS = 16.0;

    /** When each player arrived in Level 39 (game time). Server thread only. */
    private static final Map<UUID, Long> ARRIVED = new HashMap<>();

    /**
     * True while this player is still inside their arrival grace. A player we have no record of
     * (e.g. the server started with them already here) counts as arriving now.
     */
    public static boolean inGrace(ServerPlayer player) {
        long now = player.level().getGameTime();
        return withinGrace(ARRIVED.computeIfAbsent(player.getUUID(), id -> now), now);
    }

    /** The pure time check, kept separate so it can be unit-tested without a world. */
    static boolean withinGrace(long arrived, long now) {
        return now - arrived < ARRIVAL_GRACE_TICKS;
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getTo() == BackroomsLevel.LEVEL_39.dimension()) {
            arrive(event.getEntity());
        } else {
            ARRIVED.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // Logging back in counts as arriving: you get your minute again.
        arrive(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        arrive(event.getEntity());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ARRIVED.remove(event.getEntity().getUUID());
    }

    /** A world closed (singleplayer quits, servers stop): nothing carries over to the next one. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ARRIVED.clear();
    }

    private static void arrive(Player player) {
        ARRIVED.put(player.getUUID(), player.level().getGameTime());
        if (player.level() instanceof ServerLevel level
                && BackroomsLevel.fromDimension(level.dimension()) == BackroomsLevel.LEVEL_39) {
            clearAround(level, player);
        }
    }

    /** Removes hostile Level 39 creatures waiting at the arrival spot (only runs on arrival, not per tick). */
    private static void clearAround(ServerLevel level, Entity player) {
        AABB box = player.getBoundingBox().inflate(CLEAR_RADIUS);
        for (Entity e : level.getEntities(player, box, Level39Arrival::isThreat)) {
            if (e.distanceToSqr(player) <= CLEAR_RADIUS * CLEAR_RADIUS) {
                e.discard();
            }
        }
    }

    private static boolean isThreat(Entity e) {
        return e instanceof Hound || e instanceof Wretch || e instanceof SkinStealer || e instanceof Clump
                || (e instanceof Faceling faceling && faceling.isHostile());
    }

    private Level39Arrival() {}
}
