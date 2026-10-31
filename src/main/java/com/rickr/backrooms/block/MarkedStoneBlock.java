package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Stairwell stone with tally marks scratched into it (Level 490's looping stairwells): MARKS 1-5
 * is that many strokes, 6 a wall covered in them, as if someone had been going round for years.
 */
public class MarkedStoneBlock extends Block {
    public static final IntegerProperty MARKS = IntegerProperty.create("marks", 1, 6);

    public MarkedStoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MARKS, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MARKS);
    }
}
