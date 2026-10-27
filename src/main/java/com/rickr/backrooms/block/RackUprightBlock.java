package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * One block of a pallet rack's upright frame: two blue posts (aisle side and back) with bracing,
 * seen side-on from the aisle. BEAMS: the orange beams of a rack level pass through here, so the
 * beams of the slots either side meet the posts.
 */
public class RackUprightBlock extends FurnitureBlock {
    public static final BooleanProperty BEAMS = RackBayBlock.BEAMS;

    public RackUprightBlock(Properties properties) {
        super(properties, Block.box(6, 0, 0, 10, 16, 16));
        registerDefaultState(defaultBlockState().setValue(BEAMS, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BEAMS);
    }
}
