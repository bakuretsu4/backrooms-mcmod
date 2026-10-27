package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Level 2's insulated main: one fat off-white lagged pipe running along the bottom of the wall.
 * FLANGE = true adds the metal bands (and a torn patch of the jacket) where two lengths are
 * joined; the level puts one every few blocks.
 * The duct is solid, so it also keeps you off the pipe side of the corridor.
 */
public class DuctBlock extends WallMountBlock {
    public static final BooleanProperty FLANGE = BooleanProperty.create("flange");

    public DuctBlock(Properties properties) {
        super(properties, 0, 0, 3, 16, 13, 16);
        registerDefaultState(defaultBlockState().setValue(FLANGE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FLANGE);
    }
}
