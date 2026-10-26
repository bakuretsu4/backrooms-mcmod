package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server rules of Level 7 ("Thalassophobia"):
 * <ul>
 *   <li><b>Long breath:</b> the wikis say the air above Level 7's sea lets you hold your breath far
 *       longer than anywhere else. Under water your air runs out ten times slower than normal
 *       (about 2.5 minutes instead of 15 seconds), just enough to reach the bottom and back.</li>
 *   <li><b>Out:</b> the wooden door in the stone platform on the sea floor, 150 blocks west of the
 *       entrance room ({@code block/SeaDoorBlock} tells us). Swim into it: on to Level 9.</li>
 * </ul>
 * The way in is a flooded house in Level 94 ({@link Level94Rules}).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level7Rules {
    /** Under water in Level 7, air drains once every this many ticks instead of every tick. */
    private static final int AIR_DRAIN_EVERY = 10;
    /** Players who swam into the exit door this tick; they travel on their next tick. */
    private static final Set<UUID> THROUGH_DOOR = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        if (BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_7) {
            THROUGH_DOOR.remove(player.getUUID());
            return;
        }
        if (THROUGH_DOOR.remove(player.getUUID())) {
            Travel.useExit(player, BackroomsLevel.LEVEL_7);
            return;
        }
        // Vanilla has just taken one point of air for this tick; give it back on all but one tick
        // in AIR_DRAIN_EVERY. (Surfacing refills it the normal way.)
        if (player.isEyeInFluid(FluidTags.WATER) && player.getAirSupply() < player.getMaxAirSupply()
                && player.tickCount % AIR_DRAIN_EVERY != 0) {
            player.setAirSupply(player.getAirSupply() + 1);
        }
    }

    /** Called by the exit door when a player swims into it. */
    public static void throughExitDoor(ServerPlayer player) {
        THROUGH_DOOR.add(player.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        THROUGH_DOOR.remove(event.getEntity().getUUID());
    }

    private Level7Rules() {}
}
