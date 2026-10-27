package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The low roof of the Level 39 houses (ranch hips, porches, lean-tos, shed dormers): it rises half
 * a block per block, so one block of height takes two pieces. UPPER is the second of the two:
 * its slope starts half way up the block. Shapes and trims as in {@link Level39RoofBlock}.
 */
public class Level39LowRoofBlock extends Level39RoofBlock {
    public static final BooleanProperty UPPER = BooleanProperty.create("upper");

    public Level39LowRoofBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(UPPER, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(UPPER);
    }

    @Override
    protected double base(BlockState state) {
        return state.getValue(UPPER) ? 0.5 : 0.0;
    }

    @Override
    protected double rise() {
        return 0.5;
    }
}
