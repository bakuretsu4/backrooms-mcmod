package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A thin upright post in the middle of its block: the streetlamp poles and sign posts of Level 9.
 * Stack several for a tall pole. Unlike a fence it never connects sideways, so a pole next to a
 * wall or another pole stays a plain post.
 */
public class PoleBlock extends Block {
    private final VoxelShape shape;

    /** @param radius half the pole's width, in pixels (a 5 px wide pole has radius 2.5) */
    public PoleBlock(Properties properties, double radius) {
        super(properties);
        shape = Block.box(8 - radius, 0, 8 - radius, 8 + radius, 16, 8 + radius);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }
}
