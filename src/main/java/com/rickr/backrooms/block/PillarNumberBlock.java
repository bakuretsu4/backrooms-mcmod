package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A garage pillar block with a big yellow number painted on its FACING side (Level 1).
 * NUMBER is an index into {@link #LABELS}; the textures are named after the label
 * ({@code pillar_number_99.png} and so on).
 */
public class PillarNumberBlock extends WallDecalBlock {
    public static final IntegerProperty NUMBER = IntegerProperty.create("number", 0, 7);
    /** The painted labels, in NUMBER order. 99 is the odd one out on purpose. */
    public static final int[] LABELS = {1, 2, 3, 5, 6, 9, 12, 99};

    public PillarNumberBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(NUMBER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NUMBER);
    }
}
