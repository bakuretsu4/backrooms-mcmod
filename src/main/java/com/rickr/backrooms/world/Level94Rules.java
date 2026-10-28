package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.player.Checkpoints;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.layout.Level94Castle;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server rules of Level 94 ("Motion"), in and out:
 * <ul>
 *   <li><b>In:</b> a rare clay door on a Level 9 house. Open it and step through
 *       ({@code block/ClayDoorBlock} tells us), and you arrive on a road among the hills.</li>
 *   <li><b>Out to Level 9:</b> a dark-door house. It looks like every other house, but behind the
 *       door is solid black ({@code l94_dark_void}). Walk into the dark and you are back in the suburbs.</li>
 *   <li><b>Out to Level 7:</b> a flooded house: knee-deep water inside and steps going down into
 *       murky dark ({@code l94_flood_dark}). Wade down into it and you come up in Level 7.</li>
 *   <li><b>Out to Level 1:</b> the light behind the empty throne in the castle.</li>
 * </ul>
 * Level 94 is never left by falling (its transferBelowY is unreachable).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level94Rules {
    /** Players who stepped through an open clay door this tick; they travel on their next tick. */
    private static final Set<UUID> THROUGH_CLAY_DOOR = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        BackroomsLevel floor = BackroomsLevel.fromDimension(player.level().dimension());
        if (floor == BackroomsLevel.LEVEL_9) {
            if (THROUGH_CLAY_DOOR.remove(player.getUUID())) {
                enterThroughClayDoor(player);
            }
            return;
        }
        THROUGH_CLAY_DOOR.remove(player.getUUID());
        if (floor != BackroomsLevel.LEVEL_94) {
            return;
        }
        BlockPos feet = player.blockPosition();
        long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), BackroomsLevel.LEVEL_94);
        if (Level94Castle.isThroneLight(seed, feet.getX(), feet.getY(), feet.getZ())) {
            throneExit(player);
            return;
        }
        // Into the dark of a dark-door house (feet or head in it): back to Level 9.
        if (player.level().getBlockState(feet).is(ModBlocks.L94_DARK_VOID.get())
                || player.level().getBlockState(feet.above()).is(ModBlocks.L94_DARK_VOID.get())) {
            Travel.useExit(player, BackroomsLevel.LEVEL_94);
            return;
        }
        // Down the flooded house's stairs into the murk: Level 7.
        if (player.level().getBlockState(feet).is(ModBlocks.L94_FLOOD_DARK.get())
                || player.level().getBlockState(feet.above()).is(ModBlocks.L94_FLOOD_DARK.get())) {
            Checkpoints.clear(player);
            // Level 7 sends arrivals to its nearest entrance room (Level7Layout.arrivalHint).
            Travel.sendTo(player, BackroomsLevel.LEVEL_7, player.getBlockX(), player.getBlockZ());
        }
    }

    /** Called by the clay door when a player walks into its open doorway. */
    public static void steppedThroughClayDoor(ServerPlayer player) {
        THROUGH_CLAY_DOOR.add(player.getUUID());
    }

    /**
     * Level 9 to Level 94. You come out at the crossroads nearest the door's coordinates
     * (Level94Layout.arrivalHint), so the same door always leads to the same place.
     */
    private static void enterThroughClayDoor(ServerPlayer player) {
        // A secret way in, but still a move between floors: the Level 9 checkpoint no longer applies.
        Checkpoints.clear(player);
        Travel.sendTo(player, BackroomsLevel.LEVEL_94, player.getBlockX(), player.getBlockZ());
    }

    /** Into the light behind the throne: on to Level 1, with the warm swell of light. */
    private static void throneExit(ServerPlayer player) {
        Checkpoints.clear(player);
        Travel.moshTo(player, p -> {
            if (Travel.sendToQuietly(p, BackroomsLevel.LEVEL_1, p.getBlockX(), p.getBlockZ())) {
                p.playNotifySound(ModSounds.LEVEL37_ARRIVE.get(), SoundSource.MASTER, 1.0f, 1.2f);
                Travel.showArrival(p, BackroomsLevel.LEVEL_1, null);
            }
        });
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        THROUGH_CLAY_DOOR.remove(event.getEntity().getUUID());
    }

    private Level94Rules() {}
}
