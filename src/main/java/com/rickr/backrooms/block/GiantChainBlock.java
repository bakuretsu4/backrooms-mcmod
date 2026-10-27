package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One link of the giant chains that hold Level 490's platforms and bridges up. The chains rise
 * straight into the storm, one link per block; TWIST turns every other link a quarter turn, as in
 * a real chain. You can't climb them (the climb comes with the structure).
 */
public class GiantChainBlock extends Block {
    public static final BooleanProperty TWIST = BooleanProperty.create("twist");
    private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);

    public GiantChainBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TWIST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TWIST);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
