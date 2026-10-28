package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.Level2ChamberBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The heart_gauge oddity of Level 2 (world/anomaly/level2/Level2Sites#HEART_GAUGE): an ordinary
 * looking pressure gauge on a corridor wall, the same brass case and yellowed dial as every other
 * gauge (l2_gauge), but its needle is not painted on. It is drawn every frame by
 * client/anomaly/level2/HeartGaugeRenderer, and it follows the heart rate of whoever is looking
 * at it (the BPM on their camcorder HUD), twitching with every beat. Two players in front of it
 * each see their own pulse.
 *
 * <p>It needs a block entity only so the game will draw the needle; the block entity stores
 * nothing. World generation makes one for it (BackroomsChunkGenerator does that for any block with
 * a block entity).
 */
public class Level2HeartGaugeBlock extends WallMountBlock implements EntityBlock {
    public Level2HeartGaugeBlock(Properties properties) {
        // The same box as l2_gauge: the round case on its pigtail pipe, out of the wall.
        super(properties, 4, 2, 10, 12, 13, 16);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Level2HeartGaugeBlockEntity(pos, state);
    }

    /** The (empty) block entity that lets the renderer draw the needle. */
    public static class Level2HeartGaugeBlockEntity extends BlockEntity {
        public Level2HeartGaugeBlockEntity(BlockPos pos, BlockState state) {
            super(Level2ChamberBlocks.HEART_GAUGE_ENTITY.get(), pos, state);
        }
    }
}
