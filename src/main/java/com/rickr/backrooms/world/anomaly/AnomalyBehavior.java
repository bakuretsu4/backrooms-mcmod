package com.rickr.backrooms.world.anomaly;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * What an anomaly kind DOES on the server. Register one per kind in {@link Anomalies#bootstrap}.
 * Everything defaults to doing nothing, so a kind only implements what it needs.
 *
 * <p>Called by {@link AnomalyService} for players in survival/adventure/creative (not spectators).
 */
public interface AnomalyBehavior {
    /** The player just walked into the site. */
    default void enter(ServerPlayer player, AnomalySite site, long seed) {
    }

    /** Every tick while the player is inside; {@code ticksInside} counts from 0 at entry. */
    default void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
    }

    /** The player left the site (walked out, teleported away, changed level or logged out). */
    default void leave(ServerPlayer player, AnomalySite site, long seed) {
    }

    /**
     * Once a second for every site that has a player within {@link AnomalyService#SITE_TICK_RANGE}
     * blocks, inside or not: for things the place does on its own (rearranging itself when unseen,
     * moving a door, spawning crates behind people...).
     */
    default void siteTick(ServerLevel level, AnomalySite site, long seed) {
    }
}
