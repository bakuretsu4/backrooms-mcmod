package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A crude painting or drawing hung on a wall (Level 1's painting rooms). FACING points away from
 * the wall, like every {@link WallMountBlock}; VARIANT picks which of the pictures it shows. The
 * anomaly swaps VARIANT (or removes the painting) while nobody is looking, so the same spot shows
 * a different picture every time you turn back to it. You walk through it (no collision).
 */
public class CrudePaintingBlock extends WallMountBlock {
    /** How many different pictures there are (textures l1_painting_0 .. 7). */
    public static final int VARIANTS = 8;
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, VARIANTS - 1);

    public CrudePaintingBlock(Properties properties) {
        // A sheet of paper or canvas, one pixel thick, flat against the wall.
        super(properties, 0, 0, 15, 16, 16, 16);
        registerDefaultState(defaultBlockState().setValue(VARIANT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VARIANT);
    }
}
