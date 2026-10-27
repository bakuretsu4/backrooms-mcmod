package com.rickr.backrooms.world;

import com.rickr.backrooms.camera.CameraService;
import com.rickr.backrooms.network.TransitionPayload;
import com.rickr.backrooms.player.Checkpoints;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.layout.Level39AnomalyPlan;
import com.rickr.backrooms.world.layout.Level39Layout;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
        sendTo(player, target, x, z, TransitionPayload.NOCLIP);
    }

    /** {@code kind}: NOCLIP (glitch, PLAY, the noclip sound) or STAIRS (a quiet fade in from the dark). */
    private static void sendTo(ServerPlayer player, BackroomsLevel target, int x, int z, int kind) {
        if (sendToQuietly(player, target, x, z)) {
            PacketDistributor.sendToPlayer(player, new TransitionPayload(kind));
            if (kind == TransitionPayload.NOCLIP) {
                player.playNotifySound(ModSounds.NOCLIP.get(), SoundSource.MASTER, 1.0f, 1.0f);
            }
        }
    }

    /** Teleport only, no transition effect (the intro cutscene brings its own). Returns false if the level is missing. */
    public static boolean sendToQuietly(ServerPlayer player, BackroomsLevel target, int x, int z) {
        ServerLevel level = player.server.getLevel(target.dimension());
        if (level == null) {
            return false;
        }
        long seed = BackroomsSeeds.layoutSeed(level, target);
        // Some floors pick the spot themselves depending on where you come from (Level 39: the
        // Poolrooms' drain empties into its tunnels, not onto its streets).
        BackroomsLevel from = BackroomsLevel.fromDimension(player.level().dimension());
        BlockPos preferred = target.layout().preferredArrival(seed, x, z, from);
        BlockPos spot = preferred != null ? preferred : findSafeSpot(level, target, x, z);
        // Feet exactly on the surface: Level 9's sidewalks are half slabs (feet at y + 0.5).
        double feet = preferred != null ? preferred.getY() : target.layout().feetY(seed, spot.getX(), spot.getZ());
        CameraService.startTape(player);
        teleport(player, level, new Vec3(spot.getX() + 0.5, feet, spot.getZ() + 0.5));
        return true;
    }

    /** How high above the town a gondola rider appears in Level 39 (under the rendered cloud ceiling). */
    private static final double LEVEL39_SKY_Y = 250.0;
    /** Slow Falling for the drift down through the clouds: about 25 seconds. */
    private static final int LEVEL39_SKY_FALL_TICKS = 25 * 20;

    /**
     * The side entrance to Level 39: the gondola in Level 9's Crimson Field carries you up into the
     * whiteout, and you noclip through the ceiling of the superstructure. You appear high above the
     * town near (x, z) and drift down on Slow Falling; {@link Level39Rules} makes sure the landing
     * never hurts. Returns false if Level 39 isn't loaded.
     */
    public static boolean sendToLevel39Sky(ServerPlayer player, int x, int z) {
        BackroomsLevel target = BackroomsLevel.LEVEL_39;
        ServerLevel level = player.server.getLevel(target.dimension());
        if (level == null) {
            return false;
        }
        Checkpoints.clear(player); // a move between floors: the old checkpoint no longer applies
        // Far east lies the bigger-inside copy region, not the town: come down over an ordinary town.
        int townX = Level39AnomalyPlan.isCopy(x) ? Level39Layout.local(x) : x;
        BlockPos ground = findSafeSpot(level, target, townX, z);
        CameraService.startTape(player);
        Level39Rules.markSkyArrival(player);
        teleport(player, level, new Vec3(ground.getX() + 0.5, LEVEL39_SKY_Y, ground.getZ() + 0.5));
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, LEVEL39_SKY_FALL_TICKS, 0, false, false, true));
        PacketDistributor.sendToPlayer(player, new TransitionPayload(TransitionPayload.NOCLIP));
        player.playNotifySound(ModSounds.NOCLIP.get(), SoundSource.MASTER, 1.0f, 1.0f);
        player.sendSystemMessage(Component.translatable("message.backrooms.arrive.level_39").withStyle(s -> s.withItalic(true)));
        return true;
    }

    /**
     * Used the exit of {@code from}: go one floor deeper, or escape if it was the last floor.
     * Leaving a floor forgets its checkpoint (you can't respawn back up there).
     */
    public static void useExit(ServerPlayer player, BackroomsLevel from) {
        useExit(player, from, TransitionPayload.NOCLIP);
    }

    /**
     * The player went below the level's bottom: down an exit stairwell (a quiet, seamless step into
     * the next level) or by falling anywhere else (noclip).
     */
    public static void useExitDown(ServerPlayer player, BackroomsLevel from) {
        long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), from);
        boolean stairs = from.layout().exits().distance(seed, player.getBlockX(), player.getBlockZ()) <= LevelLayout.STAIRWELL_RADIUS;
        useExit(player, from, stairs ? TransitionPayload.STAIRS : TransitionPayload.NOCLIP);
    }

    private static void useExit(ServerPlayer player, BackroomsLevel from, int kind) {
        Checkpoints.clear(player);
        BackroomsLevel next = from.next();
        if (next == null) {
            escape(player);
            return;
        }
        ServerLevel current = player.serverLevel();
        long seed = BackroomsSeeds.layoutSeed(current, from);
        int[] exit = from.layout().exits().nearest(seed, player.getBlockX(), player.getBlockZ());
        sendTo(player, next, exit[0], exit[1], kind);
        player.sendSystemMessage(Component.translatable("message.backrooms.arrive." + next.getSerializedName()).withStyle(s -> s.withItalic(true)));
    }

    /** Back to reality at world spawn. "...or did you?" */
    public static void escape(ServerPlayer player) {
        Checkpoints.clear(player); // you left the Backrooms: checkpoints no longer apply
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
        // Travel often runs inside the player's tick (falling out of a level, anomalies).
        Teleports.settle(player);
    }

    /** Nearest safe column to (x, z) (or to the level's hint for it), searched in growing squares. */
    public static BlockPos findSafeSpot(ServerLevel level, BackroomsLevel target, int x, int z) {
        LevelLayout layout = target.layout();
        long seed = BackroomsSeeds.layoutSeed(level, target);
        int[] hint = layout.arrivalHint(seed, x, z);
        BlockPos found = searchWalkable(layout, seed, hint[0], hint[1], SEARCH_RADIUS);
        return found != null ? found : new BlockPos(hint[0], layout.standY(seed, hint[0], hint[1]), hint[1]);
    }

    /**
     * Spiral search for a safe arrival column. Most levels also want its four neighbours safe (so
     * you never land squeezed against a wall or at the edge of a pool); Level 9 picks a spot on a
     * narrow sidewalk, where that can't hold ({@link LevelLayout#arrivalNeedsSpace()}). Returns the
     * block the player's feet are in, which on the Poolrooms' raised walkways is one block higher.
     */
    @Nullable
    public static BlockPos searchWalkable(LevelLayout layout, long seed, int x, int z, int radius) {
        boolean needsSpace = layout.arrivalNeedsSpace();
        for (int r = 0; r <= radius; r++) {
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    if (Math.max(Math.abs(ox), Math.abs(oz)) != r) {
                        continue;
                    }
                    int cx = x + ox;
                    int cz = z + oz;
                    if (layout.isSafeArrival(seed, cx, cz) && (!needsSpace
                            || layout.isSafeArrival(seed, cx + 1, cz) && layout.isSafeArrival(seed, cx - 1, cz)
                            && layout.isSafeArrival(seed, cx, cz + 1) && layout.isSafeArrival(seed, cx, cz - 1))) {
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
