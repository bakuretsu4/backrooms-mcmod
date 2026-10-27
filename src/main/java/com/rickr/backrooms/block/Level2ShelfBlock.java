package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Grey steel shelving in a Level 2 maintenance room, two blocks tall (see {@link Level2TallBlock}).
 * VARIANT = what is on the shelves: 0 cardboard boxes, 1 tools and a toolbox, 2 paint tins, jerry
 * cans and pipe fittings, 3 nearly bare (a few dusty leftovers). Both halves carry the same VARIANT.
 */
public class Level2ShelfBlock extends Level2TallBlock {
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 3);

    public Level2ShelfBlock(Properties properties, VoxelShape lowerFacingNorth, VoxelShape upperFacingNorth) {
        super(properties, lowerFacingNorth, upperFacingNorth);
        registerDefaultState(defaultBlockState().setValue(VARIANT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VARIANT);
    }
}
