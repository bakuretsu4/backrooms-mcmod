package com.rickr.backrooms.world.anomaly.level94;

import com.rickr.backrooms.block.FurnitureBlock;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Observation;
import com.rickr.backrooms.world.layout.Level94AnomalyPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code level_94/furniture_drift}: the single rooms and furniture piles on the hills around a
 * castle. The M.E.G. found the houses coming apart the closer they got to the castle: "random
 * sets of furniture and even entire single rooms", then "only piles of furniture"
 * (https://backrooms-wiki.wikidot.com/m-e-g-the-discovery-of-level-94,
 * https://backrooms-wiki.wikidot.com/level-94). The world generator builds that gradient
 * (Level94Plan.driftStage); this keeps it drifting.
 *
 * <p>While nobody is looking, a piece of furniture now and then turns to face the castle, and a
 * piece that already faces it shuffles one block towards it (along the ground, only into empty
 * space inside the site, never into anyone). Come back later and every chair is staring at the
 * castle, the pile a little further along. Bounded: nothing ever leaves its site.
 */
final class FurnitureDriftBehavior implements AnomalyBehavior {
    /** About one change every this many seconds per site, while unwatched. */
    private static final int EVERY_SECONDS = 5;
    private static final double WATCH_DISTANCE = 96.0;
    /** Nobody may be standing this close either (they'd hear it right beside them). */
    private static final double KEEP_AWAY = 6.0;

    @Override
    public void siteTick(ServerLevel level, AnomalySite site, long seed) {
        RandomSource random = level.getRandom();
        if (random.nextInt(EVERY_SECONDS) != 0) {
            return;
        }
        int ground = Level94AnomalyPlan.driftGroundY(seed, site);
        Vec3 centre = new Vec3(site.centerX(), ground + 1.5, site.centerZ());
        if (Observation.seenByAnyone(level, centre, WATCH_DISTANCE) || anyoneWithin(level, centre, KEEP_AWAY)) {
            return;
        }
        Direction toCastle = Direction.from2DDataValue(site.facing());
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = site.minX() + random.nextInt(site.maxX() - site.minX() + 1);
            int z = site.minZ() + random.nextInt(site.maxZ() - site.minZ() + 1);
            // Top piece first: only the top of a stack may move.
            for (int y = ground + 2; y >= ground + 1; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.isLoaded(pos)) {
                    return;
                }
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof FurnitureBlock)) {
                    continue;
                }
                if (state.getValue(FurnitureBlock.FACING) != toCastle) {
                    level.setBlock(pos, state.setValue(FurnitureBlock.FACING, toCastle), Block.UPDATE_ALL);
                    return;
                }
                if (slide(level, site, pos, state, toCastle)) {
                    return;
                }
            }
        }
    }

    /** Moves the piece at {@code pos} one block towards the castle, if that is safe. */
    private static boolean slide(ServerLevel level, AnomalySite site, BlockPos pos, BlockState state, Direction dir) {
        BlockPos to = pos.relative(dir);
        if (!site.contains(to.getX(), to.getZ()) || !level.isLoaded(to) || !level.getBlockState(to).isAir()) {
            return false;
        }
        if (!level.getBlockState(pos.above()).isAir()) {
            return false; // something is stacked on it
        }
        BlockPos below = to.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return false; // only along the ground, never off the edge of a stack
        }
        if (!level.getEntitiesOfClass(Entity.class, new AABB(to)).isEmpty()) {
            return false;
        }
        level.setBlock(to, state, Block.UPDATE_ALL);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        // A soft scrape somewhere behind you.
        level.playSound(null, to, SoundEvents.WOOD_STEP, SoundSource.BLOCKS, 0.5f, 0.55f);
        return true;
    }

    private static boolean anyoneWithin(ServerLevel level, Vec3 point, double distance) {
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.position().distanceToSqr(point) < distance * distance) {
                return true;
            }
        }
        return false;
    }
}
