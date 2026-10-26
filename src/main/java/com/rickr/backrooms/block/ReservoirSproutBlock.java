package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A bright red sprout growing out of the tile in the Poolrooms' Sanguine Reservoir: a small clump
 * of glowing stalks (cross model), the only light there besides the red pool lamps. You walk
 * right through it (no collision); the outline only covers the clump.
 */
public class ReservoirSproutBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 12, 13);

    public ReservoirSproutBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
