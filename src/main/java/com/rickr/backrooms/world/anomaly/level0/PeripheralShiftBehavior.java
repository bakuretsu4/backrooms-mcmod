package com.rickr.backrooms.world.anomaly.level0;

import com.rickr.backrooms.block.ChalkMarkBlock;
import com.rickr.backrooms.registry.ChalkRegistry;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Observation;
import com.rickr.backrooms.world.layout.Level0PillarHall;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Peripheral Shift in a Pillar Variation hall (https://backrooms-wiki.wikidot.com/level-0):
 * "whenever not directly observed, the layout of the level can warp, stretch, or rearrange itself",
 * and in the pillar rooms "paths behind you shift into asymmetric pillar layouts". Floor plan and
 * the pillar arrangements: {@link Level0PillarHall}.
 *
 * <p>Every 30-60 seconds the hall picks another arrangement. Then, once a second, each pillar that
 * is in the wrong place moves (appears or vanishes), but only where no player can see it and
 * nobody (no player, monster or item) stands in or right next to it. Turn round and the hall
 * behind you is not the hall you walked through. The arch wall in the middle never moves.
 *
 * <p>Ways to catch it, all without ever seeing a pillar move:
 * <ul>
 *   <li>the arrangements include the whole lattice moved half a step, so after a shift the half of
 *       the hall you weren't watching is on a different grid from the half you were;</li>
 *   <li>a moving pillar makes a soft settling sound (at most one a second), so you hear the hall
 *       rearranging behind you;</li>
 *   <li>chalk marks go with their pillar: mark one, look away, and your mark is on a pillar
 *       somewhere else.</li>
 * </ul>
 */
public final class PeripheralShiftBehavior implements AnomalyBehavior {
    private static final int MIN_SECONDS_BETWEEN_SHIFTS = 30;
    private static final int MORE_SECONDS_AT_RANDOM = 30;
    private static final double WATCH_DISTANCE = 72.0;
    /** Forget the state of halls once we track this many (it's rebuilt from the blocks when needed). */
    private static final int MAX_HALLS = 64;
    /** The settling sound is only worth playing if someone is this close (blocks). */
    private static final double HEAR_SETTLE = 16.0;

    /** A chalk mark lifted off a vanished pillar, waiting for the next pillar that appears. */
    private record Mark(Direction side, int y, BlockState state) {}

    private static final class Hall {
        final List<Level0PillarHall.Slot> slots;
        int arrangement;
        long nextShift;
        /** Marks of pillars that vanished, one list per pillar, oldest first. */
        final ArrayDeque<List<Mark>> marks = new ArrayDeque<>();

        Hall(List<Level0PillarHall.Slot> slots, long nextShift) {
            this.slots = slots;
            this.nextShift = nextShift;
        }
    }

    private final Map<AnomalySite, Hall> halls = new HashMap<>();

    @Override
    public void siteTick(ServerLevel level, AnomalySite site, long seed) {
        long now = level.getGameTime();
        if (halls.size() > MAX_HALLS && !halls.containsKey(site)) {
            halls.clear();
        }
        Hall hall = halls.computeIfAbsent(site, s -> new Hall(Level0PillarHall.slots(s), now + delay(level)));
        if (now >= hall.nextShift) {
            hall.arrangement = (hall.arrangement + 1 + level.random.nextInt(Level0PillarHall.ARRANGEMENTS - 1))
                    % Level0PillarHall.ARRANGEMENTS;
            hall.nextShift = now + delay(level);
        }
        LevelLayout layout = BackroomsLevel.LEVEL_0.layout();
        int floor = layout.floorY();
        int ceiling = layout.ceilingY();
        BlockState baseboard = ModBlocks.YELLOW_WALLPAPER_BASEBOARD.get().defaultBlockState();
        BlockState wallpaper = ModBlocks.YELLOW_WALLPAPER.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos settled = null;
        double settledDistance = HEAR_SETTLE;
        for (Level0PillarHall.Slot slot : hall.slots) {
            BlockPos base = new BlockPos(slot.x(), floor + 1, slot.z());
            if (!level.isLoaded(base)) {
                continue;
            }
            boolean want = slot.filled()[hall.arrangement];
            boolean has = level.getBlockState(base).is(baseboard.getBlock());
            if (want == has || seen(level, slot, floor, ceiling) || occupied(level, slot, floor, ceiling)) {
                continue;
            }
            if (!want) {
                liftMarks(level, hall, slot, floor, ceiling);
            }
            for (int y = floor + 1; y < ceiling; y++) {
                BlockState state = !want ? air : y == floor + 1 ? baseboard : wallpaper;
                level.setBlock(new BlockPos(slot.x(), y, slot.z()), state, Block.UPDATE_ALL);
            }
            if (want) {
                dropMarks(level, hall, slot);
            }
            double d = nearestPlayer(level, base);
            if (d < settledDistance) {
                settledDistance = d;
                settled = base;
            }
        }
        if (settled != null) {
            level.playSound(null, settled, ModSounds.L0_PILLAR_SETTLE.get(), SoundSource.BLOCKS, 0.6f,
                    0.9f + level.random.nextFloat() * 0.2f);
        }
    }

    /** Takes the chalk marks off a pillar that is about to vanish (they'd fall off anyway) and keeps them. */
    private static void liftMarks(ServerLevel level, Hall hall, Level0PillarHall.Slot slot, int floor, int ceiling) {
        List<Mark> lifted = new ArrayList<>();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int y = floor + 1; y < ceiling; y++) {
                BlockPos pos = new BlockPos(slot.x() + side.getStepX(), y, slot.z() + side.getStepZ());
                BlockState state = level.getBlockState(pos);
                // A mark drawn on this pillar points away from it, on the side it's on.
                if (state.is(ChalkRegistry.CHALK_MARK.get()) && state.getValue(ChalkMarkBlock.FACING) == side) {
                    lifted.add(new Mark(side, y, state));
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        if (!lifted.isEmpty()) {
            hall.marks.addLast(lifted);
            if (hall.marks.size() > 8) {
                hall.marks.removeFirst();
            }
        }
    }

    /** Gives the oldest lifted marks to a pillar that just appeared, on the same sides and heights. */
    private static void dropMarks(ServerLevel level, Hall hall, Level0PillarHall.Slot slot) {
        List<Mark> marks = hall.marks.pollFirst();
        if (marks == null) {
            return;
        }
        for (Mark mark : marks) {
            BlockPos pos = new BlockPos(slot.x() + mark.side().getStepX(), mark.y(), slot.z() + mark.side().getStepZ());
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, mark.state(), Block.UPDATE_ALL);
            }
        }
    }

    private static double nearestPlayer(ServerLevel level, BlockPos pos) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) {
                best = Math.min(best, Math.sqrt(player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)));
            }
        }
        return best;
    }

    private static long delay(ServerLevel level) {
        return 20L * (MIN_SECONDS_BETWEEN_SHIFTS + level.random.nextInt(MORE_SECONDS_AT_RANDOM + 1));
    }

    /** Could anyone see the pillar's column (its foot or its top)? */
    private static boolean seen(ServerLevel level, Level0PillarHall.Slot slot, int floor, int ceiling) {
        double x = slot.x() + 0.5;
        double z = slot.z() + 0.5;
        return Observation.seenByAnyone(level, new Vec3(x, floor + 1.2, z), WATCH_DISTANCE)
                || Observation.seenByAnyone(level, new Vec3(x, ceiling - 0.5, z), WATCH_DISTANCE);
    }

    /** True if anything stands in the column or right beside it: a pillar never appears inside or against someone. */
    private static boolean occupied(ServerLevel level, Level0PillarHall.Slot slot, int floor, int ceiling) {
        AABB around = new AABB(slot.x() - 0.6, floor + 1, slot.z() - 0.6, slot.x() + 1.6, ceiling, slot.z() + 1.6);
        return !level.getEntities((Entity) null, around).isEmpty();
    }
}
