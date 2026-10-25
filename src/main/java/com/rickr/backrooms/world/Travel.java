package com.rickr.backrooms.world;

import com.rickr.backrooms.camera.CameraService;
import com.rickr.backrooms.network.TransitionPayload;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Moving players between reality and the Backrooms floors.
 *
 * <p>Arrival points are computed from the floor plan (no chunk loading needed) by spiralling out
 * from a start column to the nearest walkable spot. Friends who use the same exit start the search
 * from the same exit centre, so they land in the same spot.
 */
public final class Travel {
    private static final int SEARCH_RADIUS = 96;

    /**
     * Noclip out of reality into Level 0. Plays the intro first (filming, glitch, fall); the
     * actual teleport happens a moment later in {@link NoclipIntro}.
     */
    public static void enterBackrooms(ServerPlayer player) {
        NoclipIntro.start(player);
    }

    /** Sends the player to {@code target}, searching for floor near (x, z), with the tape-glitch transition. */
    public static void sendTo(ServerPlayer player, BackroomsLevel target, int x, int z) {
        if (sendToQuietly(player, target, x, z)) {
            PacketDistributor.sendToPlayer(player, new TransitionPayload(TransitionPayload.NOCLIP));
            player.playNotifySound(ModSounds.NOCLIP.get(), SoundSource.MASTER, 1.0f, 1.0f);
        }
    }

    /** Teleport only, no transition effect (the intro cutscene brings its own). Returns false if the level is missing. */
    public static boolean sendToQuietly(ServerPlayer player, BackroomsLevel target, int x, int z) {
        ServerLevel level = player.server.getLevel(target.dimension());
        if (level == null) {
            return false;
        }
        BlockPos spot = findSafeSpot(level, target, x, z);
        CameraService.startTape(player);
        teleport(player, level, Vec3.atBottomCenterOf(spot));
        return true;
    }

    /** Used the exit of {@code from}: go one floor deeper, or escape if it was the last floor. */
    public static void useExit(ServerPlayer player, BackroomsLevel from) {
        BackroomsLevel next = from.next();
        if (next == null) {
            escape(player);
            return;
        }
        ServerLevel current = player.serverLevel();
        long seed = BackroomsSeeds.layoutSeed(current, from);
        int[] exit = from.layout().exits().nearest(seed, player.getBlockX(), player.getBlockZ());
        sendTo(player, next, exit[0], exit[1]);
        player.sendSystemMessage(Component.translatable("message.backrooms.level_" + next.number()).withStyle(s -> s.withItalic(true)));
    }

    /** Back to reality at world spawn. "...or did you?" */
    public static void escape(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        BlockPos top = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
        teleport(player, overworld, Vec3.atBottomCenterOf(top));
        PacketDistributor.sendToPlayer(player, new TransitionPayload(TransitionPayload.ESCAPE));
        player.playNotifySound(ModSounds.ESCAPE.get(), SoundSource.MASTER, 1.0f, 1.0f);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 30));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.backrooms.escaped")));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("title.backrooms.escaped.subtitle")));
    }

    /** Everyone within {@code radius} of {@code origin} in the same level goes through together. */
    public static void useExitAsGroup(ServerPlayer opener, BackroomsLevel from, double radius) {
        List<ServerPlayer> group = opener.serverLevel().getPlayers(p -> p == opener || p.distanceTo(opener) <= radius);
        for (ServerPlayer member : group) {
            useExit(member, from);
        }
    }

    private static void teleport(ServerPlayer player, ServerLevel level, Vec3 pos) {
        player.fallDistance = 0;
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(level, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
        player.fallDistance = 0;
    }

    /** Nearest safe column to (x, z), searched in growing squares. */
    public static BlockPos findSafeSpot(ServerLevel level, BackroomsLevel target, int x, int z) {
        LevelLayout layout = target.layout();
        long seed = BackroomsSeeds.layoutSeed(level, target);
        BlockPos found = searchWalkable(layout, seed, x, z, SEARCH_RADIUS);
        return found != null ? found : new BlockPos(x, layout.standY(seed, x, z), z);
    }

    /**
     * Spiral search for a column that is a safe arrival spot along with its four neighbours (so
     * you never land squeezed against a wall or at the edge of a pool). Returns the position of
     * the player's feet there, which on the Poolrooms' raised walkways is one block higher.
     */
    @Nullable
    public static BlockPos searchWalkable(LevelLayout layout, long seed, int x, int z, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    if (Math.max(Math.abs(ox), Math.abs(oz)) != r) {
                        continue;
                    }
                    int cx = x + ox;
                    int cz = z + oz;
                    if (layout.isSafeArrival(seed, cx, cz)
                            && layout.isSafeArrival(seed, cx + 1, cz) && layout.isSafeArrival(seed, cx - 1, cz)
                            && layout.isSafeArrival(seed, cx, cz + 1) && layout.isSafeArrival(seed, cx, cz - 1)) {
                        return new BlockPos(cx, layout.standY(seed, cx, cz), cz);
                    }
                }
            }
        }
        return null;
    }

    public static boolean inBackrooms(Level level) {
        return BackroomsLevel.fromDimension(level.dimension()) != null;
    }

    private Travel() {}
}
