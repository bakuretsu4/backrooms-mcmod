package com.rickr.backrooms.world.anomaly.level0;

import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Observation;
import com.rickr.backrooms.world.layout.Level0PillarHall;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
 */
public final class PeripheralShiftBehavior implements AnomalyBehavior {
    private static final int MIN_SECONDS_BETWEEN_SHIFTS = 30;
    private static final int MORE_SECONDS_AT_RANDOM = 30;
    private static final double WATCH_DISTANCE = 72.0;
    /** Forget the state of halls once we track this many (it's rebuilt from the blocks when needed). */
    private static final int MAX_HALLS = 64;

    private static final class Hall {
        final List<Level0PillarHall.Slot> slots;
        int arrangement;
        long nextShift;

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
            for (int y = floor + 1; y < ceiling; y++) {
                BlockState state = !want ? air : y == floor + 1 ? baseboard : wallpaper;
                level.setBlock(new BlockPos(slot.x(), y, slot.z()), state, Block.UPDATE_ALL);
            }
        }
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
