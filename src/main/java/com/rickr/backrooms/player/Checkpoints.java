package com.rickr.backrooms.player;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.CheckpointOptionsPayload;
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
 * <p>Shared checkpoints ({@link SharedCheckpoints}, game rule {@code backroomsSharedCheckpoints}):
 * a save is also shared with the saver's team. If you die on a level where you have no checkpoint
 * of your own but a teammate saved one, you wake up at theirs. If you have both, the death screen
 * lets you pick (the client sends {@code CheckpointChoicePayload}, handled by {@link #choose});
 * plain "Respawn" keeps your own.
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
        float yaw = facing.toYRot();
        player.getData(ModAttachments.CHECKPOINT).set(player.level().dimension(), spot, yaw, player.level().getGameTime());
        // Also share it with the saver's team (does nothing while the game rule is off).
        SharedCheckpoints.record(player, radioPos, spot, yaw);
        // Static crackle + chime from the radio itself (everyone nearby hears it).
        player.level().playSound(null, radioPos, ModSounds.CHECKPOINT_SAVE.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        PacketDistributor.sendToPlayer(player, CheckpointSavedPayload.INSTANCE);
    }

    /**
     * You left the level (used its exit or escaped): the checkpoint belongs to that level, so
     * forget it, and stop respawning there. A shared checkpoint you saved stays for your friends.
     */
    public static void clear(ServerPlayer player) {
        player.getData(ModAttachments.CHECKPOINT).clear();
        clearBackroomsRespawn(player);
    }

    /**
     * Points your respawn at your checkpoint if you died on its level, or at a teammate's shared
     * checkpoint there if you have none of your own; otherwise back to world spawn.
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResourceKey<Level> deathDimension = player.level().dimension();
        CheckpointData checkpoint = player.getData(ModAttachments.CHECKPOINT);
        // Null whenever the game rule is off, so then everything below works exactly as before.
        SharedCheckpoints.Entry shared = SharedCheckpoints.usableFor(player);
        if (ownCheckpointHere(player, checkpoint)) {
            respawnAtOwn(player, checkpoint);
            // A friend's checkpoint worth offering too (newer than yours, or somewhere else): the
            // death screen gets two extra buttons. Plain "Respawn" keeps yours (set just above).
            if (shared != null && (shared.gameTime() > checkpoint.savedAt() || !shared.spot().equals(checkpoint.pos()))) {
                PacketDistributor.sendToPlayer(player, new CheckpointOptionsPayload(shared.saverName()));
            }
        } else if (shared != null) {
            respawnAtShared(player, shared); // no checkpoint of your own here: use your friend's
        } else if (checkpoint.sharedRespawn() || !player.getRespawnDimension().equals(deathDimension)) {
            // The respawn point is a shared checkpoint that no longer applies, or belongs to
            // another level: back to world spawn.
            clearBackroomsRespawn(player);
        }
    }

    /**
     * The death screen's choice (see {@code CheckpointChoicePayload}). It arrives just before the
     * respawn request, so we only move the respawn point. Nothing the client says is trusted: you
     * must still be dead, and the checkpoint you picked must still be one you may use.
     */
    public static void choose(ServerPlayer player, boolean shared) {
        if (!player.isDeadOrDying()) {
            return; // already respawned (or never died): nothing to choose
        }
        if (shared) {
            SharedCheckpoints.Entry entry = SharedCheckpoints.usableFor(player);
            if (entry != null) {
                respawnAtShared(player, entry);
            }
        } else {
            CheckpointData checkpoint = player.getData(ModAttachments.CHECKPOINT);
            if (ownCheckpointHere(player, checkpoint)) {
                respawnAtOwn(player, checkpoint);
            }
        }
    }

    /** Is your own checkpoint on the Backrooms level you are on (died on)? */
    private static boolean ownCheckpointHere(ServerPlayer player, CheckpointData checkpoint) {
        return checkpoint.isSet() && player.level().dimension().equals(checkpoint.dimension()) && Travel.inBackrooms(player.level());
    }

    private static void respawnAtOwn(ServerPlayer player, CheckpointData checkpoint) {
        // forced = true: no bed needed. sendMessage = false: no "Respawn point set" chat line.
        player.setRespawnPosition(checkpoint.dimension(), checkpoint.pos(), checkpoint.yaw(), true, false);
        checkpoint.setSharedRespawn(false);
    }

    /**
     * Same kind of forced respawn point as your own checkpoint, so you also wake up on the floor in
     * front of the radio (and respawning resets your sanity, as always).
     */
    private static void respawnAtShared(ServerPlayer player, SharedCheckpoints.Entry shared) {
        player.setRespawnPosition(shared.dimension(), shared.spot(), shared.yaw(), true, false);
        player.getData(ModAttachments.CHECKPOINT).setSharedRespawn(true);
    }

    /** A respawn point inside the Backrooms is removed (back to world spawn); beds in the real world stay. */
    private static void clearBackroomsRespawn(ServerPlayer player) {
        if (player.getRespawnPosition() != null && BackroomsLevel.fromDimension(player.getRespawnDimension()) != null) {
            player.setRespawnPosition(Level.OVERWORLD, null, 0.0f, false, false);
        }
        player.getData(ModAttachments.CHECKPOINT).setSharedRespawn(false);
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
