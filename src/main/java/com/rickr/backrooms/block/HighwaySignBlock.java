package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * One tile of the big green "EXIT" highway sign over Level 9's underpass. The sign is 4 blocks
 * wide and 2 tall; PART picks which tile this block shows (row * 4 + column, row 0 = top,
 * column 0 = the reader's left). FACING (from {@link WallDecalBlock}) is the side with the sign.
 */
public class HighwaySignBlock extends WallDecalBlock {
    public static final int COLUMNS = 4;
    public static final int ROWS = 2;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, COLUMNS * ROWS - 1);

    public HighwaySignBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }
}
