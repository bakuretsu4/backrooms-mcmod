package com.rickr.backrooms.world.anomaly.level1;

import com.rickr.backrooms.block.CrudePaintingBlock;
import com.rickr.backrooms.block.FloorPaintingBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.LevelEvents;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Observation;
import com.rickr.backrooms.world.layout.Level1AnomalyPlan;
import com.rickr.backrooms.world.layout.Level1AnomalyPlan.PaintingSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Painting rooms: a small side room whose walls and floor carry crude paintings and drawings that
 * "change in appearance and disappear when not in a direct line of sight or when unlit" (archived
 * Wikidot, <a href="https://backrooms-wiki.wikidot.com/archived:level-1">archived:level-1</a>); the
 * current article lists "a room with paintings on the walls and floor" among the side rooms of
 * the Corridors (<a href="https://backrooms-wiki.wikidot.com/level-1">level-1</a>).
 *
 * <p>Once a second each painting spot has a small chance to change, but only if nobody can see
 * it, or if it is in the dark (a blackout or flicker, or no light reaching it): it becomes a
 * different picture, disappears, or (if it was gone) comes back as something else. Turn your
 * back on a wall and it is not the wall you remember.
 */
public final class PaintingRoomBehavior implements AnomalyBehavior {
    /** Chance per second that a spot nobody watches changes. */
    private static final float CHANGE_CHANCE = 0.12f;
    /** Below this block light a painting counts as unlit (the room's own tube gives far more). */
    private static final int UNLIT = 3;

    @Override
    public void siteTick(ServerLevel level, AnomalySite site, long seed) {
        if (!anyoneNear(level, site)) {
            return;
        }
        boolean dark = LevelEvents.isLightsOut(BackroomsLevel.LEVEL_1);
        RandomSource random = level.getRandom();
        for (PaintingSpot spot : Level1AnomalyPlan.paintingSpots(site)) {
            if (random.nextFloat() >= CHANGE_CHANCE) {
                continue;
            }
            BlockPos pos = spot.pos();
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState now = level.getBlockState(pos);
            boolean painting = now.is(ModBlocks.L1_PAINTING.get()) || now.is(ModBlocks.L1_FLOOR_PAINTING.get());
            if (!painting && !now.isAir()) {
                continue; // something else is there (a player built something): leave it alone
            }
            boolean unlit = dark || level.getBrightness(LightLayer.BLOCK, pos) < UNLIT;
            if (!unlit && Observation.seenByAnyone(level, Vec3.atCenterOf(pos), 40.0)) {
                continue;
            }
            if (painting && random.nextFloat() < 0.3f) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            } else if (painting || random.nextFloat() < 0.6f) {
                level.setBlock(pos, otherPicture(spot, now, random), Block.UPDATE_ALL);
            }
        }
    }

    /** A picture for this spot that is not the one showing now. */
    private static BlockState otherPicture(PaintingSpot spot, BlockState now, RandomSource random) {
        if (spot.facing() == null) {
            int old = now.is(ModBlocks.L1_FLOOR_PAINTING.get()) ? now.getValue(FloorPaintingBlock.VARIANT) : -1;
            return Level1AnomalyPlan.floorPainting(different(old, FloorPaintingBlock.VARIANTS, random));
        }
        int old = now.is(ModBlocks.L1_PAINTING.get()) ? now.getValue(CrudePaintingBlock.VARIANT) : -1;
        return Level1AnomalyPlan.wallPainting(spot.facing(), different(old, CrudePaintingBlock.VARIANTS, random));
    }

    /** A random number in [0, count) other than {@code old} (any, if old is -1). */
    private static int different(int old, int count, RandomSource random) {
        if (old < 0) {
            return random.nextInt(count);
        }
        int pick = random.nextInt(count - 1);
        return pick >= old ? pick + 1 : pick;
    }

    private static boolean anyoneNear(ServerLevel level, AnomalySite site) {
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && site.distanceTo(player.getX(), player.getZ()) < 32.0) {
                return true;
            }
        }
        return false;
    }
}
