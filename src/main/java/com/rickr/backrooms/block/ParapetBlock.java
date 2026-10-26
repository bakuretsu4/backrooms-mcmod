package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A low stone railing along the edge of the glass mountain's ramp (Level 94). It LOOKS knee-high,
 * but it collides like a fence (1.5 blocks), so nobody can step or jump over it and fall off.
 */
public class ParapetBlock extends Block {
    private static final VoxelShape LOOKS = Block.box(0, 0, 0, 16, 10, 16);
    private static final VoxelShape BLOCKS = Block.box(0, 0, 0, 16, 24, 16);

    public ParapetBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return LOOKS;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BLOCKS;
    }
}
