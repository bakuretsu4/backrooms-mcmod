package com.rickr.backrooms.player;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.CheckpointSavedPayload;
import com.rickr.backrooms.network.SafeRoomPayload;
import com.rickr.backrooms.registry.ModAttachments;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.SafeRooms;
import com.rickr.backrooms.world.Travel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Checkpoints (server side). Using the old field radio in a safe room saves a checkpoint; if you
 * then die on that same level, you wake up on the floor in front of that radio instead of back
 * in the real world. Leaving the level through its exit forgets the checkpoint.
 *
 * <p>How the respawn works: when you die we point your vanilla respawn position at the
 * checkpoint, with "forced" on. A forced respawn point needs no bed or respawn anchor; Minecraft
 * only checks that the two blocks there are free to stand in (air). Dying anywhere else clears a
 * Backrooms respawn point again, so you go back to world spawn as before.
 *
 * <p>Also tells each player's camcorder whether they are standing in a safe room.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Checkpoints {
    /** How often (ticks) we check whether a player is in a safe room. */
    private static final int SAFE_ROOM_CHECK_TICKS = 5;

    /**
     * Someone used the radio at {@code radioPos}, whose front faces {@code facing} (into the
     * room). The checkpoint is the floor spot right in front of it, and you'll face into the room.
     * Using the same radio again simply saves again.
     */
    public static void save(ServerPlayer player, BlockPos radioPos, Direction facing) {
        if (!Travel.inBackrooms(player.level())) {
            return; // the radios only pick up the Backrooms' own frequency
        }
        BlockPos spot = radioPos.relative(facing);
        player.getData(ModAttachments.CHECKPOINT).set(player.level().dimension(), spot, facing.toYRot());
        // Static crackle + chime from the radio itself (everyone nearby hears it).
        player.level().playSound(null, radioPos, ModSounds.CHECKPOINT_SAVE.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        PacketDistributor.sendToPlayer(player, CheckpointSavedPayload.INSTANCE);
    }

    /**
     * You left the level (used its exit or escaped): the checkpoint belongs to that level, so
     * forget it, and stop respawning there.
     */
    public static void clear(ServerPlayer player) {
        player.getData(ModAttachments.CHECKPOINT).clear();
        clearBackroomsRespawn(player);
    }

    /** Points your respawn at the checkpoint if you died on its level; otherwise back to world spawn. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResourceKey<Level> deathDimension = player.level().dimension();
        CheckpointData checkpoint = player.getData(ModAttachments.CHECKPOINT);
        if (checkpoint.isSet() && deathDimension.equals(checkpoint.dimension()) && Travel.inBackrooms(player.level())) {
            // forced = true: no bed needed. sendMessage = false: no "Respawn point set" chat line.
            player.setRespawnPosition(deathDimension, checkpoint.pos(), checkpoint.yaw(), true, false);
        } else if (!player.getRespawnDimension().equals(deathDimension)) {
            clearBackroomsRespawn(player);
        }
    }

    /** A respawn point inside the Backrooms is removed (back to world spawn); beds in the real world stay. */
    private static void clearBackroomsRespawn(ServerPlayer player) {
        if (player.getRespawnPosition() != null && BackroomsLevel.fromDimension(player.getRespawnDimension()) != null) {
            player.setRespawnPosition(Level.OVERWORLD, null, 0.0f, false, false);
        }
    }

    /** Sends "you're in a safe room: yes/no" to the camcorder whenever it changes. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % SAFE_ROOM_CHECK_TICKS != 0) {
            return;
        }
        CheckpointData data = player.getData(ModAttachments.CHECKPOINT);
        int safe = SafeRooms.isSafe(player) ? 1 : 0;
        // lastSentSafe starts at -1 after login and respawn (it isn't saved), so the client is
        // always told once at the start.
        if (safe != data.lastSentSafe) {
            data.lastSentSafe = safe;
            PacketDistributor.sendToPlayer(player, new SafeRoomPayload(safe == 1));
        }
    }

    private Checkpoints() {}
}
