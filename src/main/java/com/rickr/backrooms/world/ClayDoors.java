package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.player.Checkpoints;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Where a moulded-clay front door ({@code block/ClayDoorBlock}) takes you: step through the open
 * doorway and you are somewhere else.
 * <ul>
 *   <li><b>On a Level 9 house</b> (about one unlocked house in forty): into Level 94, at the
 *       crossroads nearest the door (Level94Layout.arrivalHint), so the same door always leads to
 *       the same place.</li>
 *   <li><b>On a Level 94 house, or a house in Level 39's town</b>: back into Level 9's suburbs.
 *       Level 9 sends you ahead of the path to both (this clay door to 94, the Crimson Field's
 *       gondola to 39), and a level you are sent ahead to must always have a way back to the one
 *       you left; these doors are it.</li>
 * </ul>
 * The block only notices the player; the trip happens on their next tick here (teleporting
 * someone in the middle of their own movement is asking for trouble).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class ClayDoors {
    /** Players who stepped through an open clay door this tick; they travel on their next tick. */
    private static final Set<UUID> THROUGH = ConcurrentHashMap.newKeySet();

    /** Called by the clay door when a player walks into its open doorway. */
    public static void steppedThrough(ServerPlayer player) {
        THROUGH.add(player.getUUID());
    }

    /** Where a clay door in {@code level} leads, or null if clay doors lead nowhere there. */
    @Nullable
    public static BackroomsLevel destination(@Nullable BackroomsLevel level) {
        if (level == null) {
            return null;
        }
        return switch (level) {
            case LEVEL_9 -> BackroomsLevel.LEVEL_94;
            case LEVEL_94, LEVEL_39 -> BackroomsLevel.LEVEL_9;
            default -> null;
        };
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !THROUGH.remove(player.getUUID())
                || player.isSpectator() || Travel.isMoving(player)) {
            return;
        }
        BackroomsLevel target = destination(BackroomsLevel.fromDimension(player.level().dimension()));
        if (target == null) {
            return; // a clay door placed by hand somewhere else: just a door
        }
        // A secret way, but still a move between floors: this level's checkpoint no longer applies.
        Checkpoints.clear(player);
        Travel.sendTo(player, target, player.getBlockX(), player.getBlockZ(),
                target == BackroomsLevel.LEVEL_9 ? "message.backrooms.clay_door.back" : null);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        THROUGH.remove(event.getEntity().getUUID());
    }

    private ClayDoors() {}
}
