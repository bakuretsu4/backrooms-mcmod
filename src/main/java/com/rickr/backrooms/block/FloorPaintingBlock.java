package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Paint and chalk drawn straight onto the floor (Level 1's painting rooms): a thin layer like
 * {@link ThinLayerBlock}, with VARIANT picking the drawing. Like the wall paintings it changes or
 * disappears while nobody is looking.
 */
public class FloorPaintingBlock extends ThinLayerBlock {
    /** How many different drawings there are (textures l1_floor_painting_0 .. 3). */
    public static final int VARIANTS = 4;
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, VARIANTS - 1);

    public FloorPaintingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(VARIANT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VARIANT);
    }
}
