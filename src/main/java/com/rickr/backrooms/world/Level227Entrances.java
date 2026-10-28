package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.Level227Payload;
import com.rickr.backrooms.player.HidingService;
import com.rickr.backrooms.registry.Level227Atmos;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.anomaly.level9.Level9Sites;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The way into Level 227. Canon: you enter "at random while exploring a nature-based level, as if
 * dragged by a mysterious force". So, in the Backrooms' own nature:
 * <ul>
 *   <li><b>Level 9's woods</b> behind the backyards (standing on the forest floor among the pines
 *       and dead trees, not on a lawn by the street),</li>
 *   <li><b>Level 9.1</b>, the Crimson Field,</li>
 *   <li><b>Level 94's open hills</b> (on the grass, away from every road, path and town square),</li>
 * </ul>
 * after a full minute out there, every {@value #CHECK_EVERY} ticks there is a 1 in {@value #CHANCE}
 * chance (on average one pull per ~15 minutes spent in nature) that the ground takes hold of you:
 * {@value #DRAG_TICKS} ticks of leaves rushing toward one point, a groan from under the ground and
 * a pull you can't quite walk against, then the filmed noclip onto a branch of Level 227.
 *
 * <p>Never while hidden, riding something (the gondola), in a safe room, as a spectator, or while
 * another noclip is already under way.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level227Entrances {
    private static final int CHECK_EVERY = 100;
    /** You must have been out in nature this long (ticks) before the pull can come. */
    private static final int SETTLE_TICKS = 1200;
    private static final int CHANCE = 180;
    /** How long the pull lasts before the noclip. */
    public static final int DRAG_TICKS = 80;
    /** How hard the ground pulls (blocks per tick added, up to this at the end). */
    private static final double PULL = 0.07;
    /** Level 9's woods: at least this many tree trunks within {@value #WOODS_RADIUS} blocks. */
    private static final int WOODS_TRUNKS = 3;
    private static final int WOODS_RADIUS = 6;
    /** Level 94's hills: no road, path or paving within this many blocks. */
    private static final int HILLS_CLEAR = 14;

    private static final RandomSource RANDOM = RandomSource.create();
    /** How long each player has been out in nature (ticks, counted in CHECK_EVERY steps). */
    private static final Map<UUID, Integer> NATURE = new HashMap<>();

    private record Drag(int ticksLeft, double dx, double dz) {}

    private static final Map<UUID, Drag> DRAGGING = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        Drag drag = DRAGGING.get(id);
        if (drag != null) {
            tickDrag(player, drag);
            return;
        }
        if (player.tickCount % CHECK_EVERY != 0) {
            return;
        }
        if (!inNature(player) || player.isSpectator() || !player.isAlive() || player.isPassenger()
                || HidingService.isHidden(player) || SafeRooms.isSafe(player) || Level227Noclip.isPending(player)) {
            NATURE.remove(id);
            return;
        }
        int out = NATURE.merge(id, CHECK_EVERY, Integer::sum);
        if (out >= SETTLE_TICKS && RANDOM.nextInt(CHANCE) == 0) {
            NATURE.remove(id);
            drag(player);
        }
    }

    /** Starts the pull now (also used by {@code /backrooms l227 drag}). */
    public static void drag(ServerPlayer player) {
        float angle = RANDOM.nextFloat() * Mth.TWO_PI;
        DRAGGING.put(player.getUUID(), new Drag(DRAG_TICKS, Mth.cos(angle), Mth.sin(angle)));
        PacketDistributor.sendToPlayer(player, new Level227Payload(Level227Payload.DRAG, DRAG_TICKS, 1.0f));
        player.playNotifySound(Level227Atmos.DRAG.get(), SoundSource.AMBIENT, 1.0f, 1.0f);
    }

    /** Each tick of the pull: a growing tug in one direction; at the end, the noclip. */
    private static void tickDrag(ServerPlayer player, Drag drag) {
        UUID id = player.getUUID();
        if (!player.isAlive() || player.isSpectator()) {
            DRAGGING.remove(id);
            return;
        }
        int left = drag.ticksLeft() - 1;
        if (left <= 0) {
            DRAGGING.remove(id);
            Level227Noclip.start(player, BackroomsLevel.LEVEL_227, Level227Noclip.Cause.DRAGGED);
            return;
        }
        DRAGGING.put(id, new Drag(left, drag.dx(), drag.dz()));
        double share = 1.0 - (double) left / DRAG_TICKS;
        double pull = PULL * share * share;
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x + drag.dx() * pull, motion.y, motion.z + drag.dz() * pull);
        player.hurtMarked = true; // send the new motion to the client (players move themselves)
    }

    /** Is the player out in one of the nature areas that can drag them into Level 227? */
    private static boolean inNature(ServerPlayer player) {
        BackroomsLevel floor = BackroomsLevel.fromDimension(player.level().dimension());
        if (floor == BackroomsLevel.LEVEL_9) {
            return Level9Sites.inField(player.getX()) || inWoods(player.serverLevel(), player.blockPosition());
        }
        return floor == BackroomsLevel.LEVEL_94 && onOpenHills(player.serverLevel(), player.blockPosition());
    }

    /**
     * Level 9's woods: standing on the forest floor (grass or leaf litter) with several tree
     * trunks around you. A lawn by the street has at most a street tree or two; the strip of forest
     * behind the backyards has a trunk every few blocks.
     */
    private static boolean inWoods(ServerLevel level, BlockPos feet) {
        BlockState under = level.getBlockState(feet.below());
        BlockState at = level.getBlockState(feet);
        if (!under.is(ModBlocks.LAWN_GRASS.get()) && !at.is(ModBlocks.LEAF_LITTER.get())) {
            return false;
        }
        Block log = ModBlocks.NIGHT_LOG.get();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int trunks = 0;
        for (int dx = -WOODS_RADIUS; dx <= WOODS_RADIUS; dx++) {
            for (int dz = -WOODS_RADIUS; dz <= WOODS_RADIUS; dz++) {
                // A trunk stands from the ground up: one block at head height is enough to find it.
                if (level.getBlockState(pos.set(feet.getX() + dx, feet.getY() + 1, feet.getZ() + dz)).is(log)
                        && ++trunks >= WOODS_TRUNKS) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Level 94's hills: on the grass, with no road, path or paving anywhere near. */
    private static boolean onOpenHills(ServerLevel level, BlockPos feet) {
        if (!isHillGrass(level.getBlockState(feet.below())) && !isHillGrass(level.getBlockState(feet))) {
            return false;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int ring = HILLS_CLEAR / 2; ring <= HILLS_CLEAR; ring += HILLS_CLEAR / 2) {
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4.0;
                int x = feet.getX() + (int) Math.round(Math.cos(a) * ring);
                int z = feet.getZ() + (int) Math.round(Math.sin(a) * ring);
                if (!level.hasChunkAt(pos.set(x, feet.getY(), z))) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (isMadeGround(level.getBlockState(pos.set(x, top, z)))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isHillGrass(BlockState state) {
        return state.is(ModBlocks.L94_GRASS_LIGHT.get()) || state.is(ModBlocks.L94_GRASS_MID.get()) || state.is(ModBlocks.L94_GRASS_DARK.get())
                || state.is(ModBlocks.L94_GRASS_SLAB_LIGHT.get()) || state.is(ModBlocks.L94_GRASS_SLAB_MID.get())
                || state.is(ModBlocks.L94_GRASS_SLAB_DARK.get());
    }

    /** Roads, kerbs, paths and paving: where people go, not nature. */
    private static boolean isMadeGround(BlockState state) {
        return state.is(ModBlocks.L94_ASPHALT.get()) || state.is(ModBlocks.L94_CURB.get()) || state.is(ModBlocks.L94_PATH.get())
                || state.is(ModBlocks.L94_PATH_SLAB.get()) || state.is(ModBlocks.L94_PAVING.get());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        NATURE.remove(event.getEntity().getUUID());
        DRAGGING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        NATURE.remove(event.getEntity().getUUID());
        DRAGGING.remove(event.getEntity().getUUID());
    }

    private Level227Entrances() {}
}
