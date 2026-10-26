package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One cube of a play-centre climbing frame: padded coloured bars around an open middle. You walk
 * into it (no collision) and climb it like a ladder, which is how the castle's playground takes you
 * up to the upper floor.
 */
public class ClimbingFrameBlock extends Block {
    public ClimbingFrameBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return true;
    }
}
