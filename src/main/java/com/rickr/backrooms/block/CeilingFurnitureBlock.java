package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Level 9's "upside-down room": a piece of ordinary house furniture stuck to the ceiling, upside
 * down. The blockstate file reuses the normal furniture models turned over ("x": 180), so the
 * pieces look exactly like the ones on the floor in every other house.
 *
 * <p>PIECE: 0 sofa, 1 TV, 2 dining table, 3 wooden chair, 4 kitchen counter, 5 fridge,
 * 6 cardboard boxes. FACING is the way the piece's front looks, as for {@link FurnitureBlock}.
 * Nobody can reach up there, so one simple shape (hanging from the top of the block) fits them all.
 */
public class CeilingFurnitureBlock extends WallMountBlock {
    public static final int SOFA = 0;
    public static final int TV = 1;
    public static final int TABLE = 2;
    public static final int CHAIR = 3;
    public static final int COUNTER = 4;
    public static final int FRIDGE = 5;
    public static final int BOXES = 6;
    public static final IntegerProperty PIECE = IntegerProperty.create("piece", 0, 6);

    public CeilingFurnitureBlock(Properties properties) {
        super(properties, 0, 2, 0, 16, 16, 16);
        registerDefaultState(defaultBlockState().setValue(PIECE, SOFA));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PIECE);
    }
}
