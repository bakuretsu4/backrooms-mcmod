package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A one-pixel-thin layer lying on the ground: Level 9's dead leaves along the curbs, and the grey
 * dust a Watcher leaves behind. You walk straight through it (the registry gives it no collision),
 * so a layer never changes where players can go or trips the no-jump rule.
 */
public class ThinLayerBlock extends Block {
    /** 1 px tall: only used for the outline you see when you look at it. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public ThinLayerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
