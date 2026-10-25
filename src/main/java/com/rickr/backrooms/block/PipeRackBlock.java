package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Level 2's rusty pipe rack: a bundle of thin pipes running along the wall high up, resting on
 * metal shelf brackets. BRACKET = true draws the bracket in this block; the level places one every
 * few blocks so the pipes look supported.
 */
public class PipeRackBlock extends WallMountBlock {
    public static final BooleanProperty BRACKET = BooleanProperty.create("bracket");

    public PipeRackBlock(Properties properties) {
        super(properties, 0, 1, 5, 16, 14, 16);
        registerDefaultState(defaultBlockState().setValue(BRACKET, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BRACKET);
    }
}
