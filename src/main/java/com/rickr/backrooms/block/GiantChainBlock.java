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
 * One link of the small iron chains that dangle under Level 490's bridges and from its ruins (the
 * colossal chains are built of {@code l490_chain_iron} blocks instead). One link per block; TWIST
 * turns every other link a quarter turn, as in a real chain.
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
