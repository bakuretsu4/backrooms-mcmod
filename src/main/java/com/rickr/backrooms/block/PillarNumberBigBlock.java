package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Half of a big pillar number on Level 1 (two blocks tall, so UPPER says which half), painted on
 * a band in the sector's colour (SECTOR = Level1Areas.AQUILA/GILD/RUST). NUMBER indexes
 * {@link PillarNumberBlock#LABELS}.
 */
public class PillarNumberBigBlock extends WallDecalBlock {
    public static final IntegerProperty NUMBER = IntegerProperty.create("number", 0, PillarNumberBlock.LABELS.length - 1);
    public static final BooleanProperty UPPER = BooleanProperty.create("upper");
    public static final IntegerProperty SECTOR = IntegerProperty.create("sector", 0, 2);

    public PillarNumberBigBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(NUMBER, 0).setValue(UPPER, false).setValue(SECTOR, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NUMBER, UPPER, SECTOR);
    }
}
