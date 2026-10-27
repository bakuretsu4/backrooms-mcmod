package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * One slot of a steel pallet rack (Level 1's warehouse aisles and crate halls): BEAMS = the two
 * orange beams it rests on (false on the floor level), LOAD = what stands on it. FACING is the
 * aisle side, where the shrink-wrapped stacks show their labels.
 */
public class RackBayBlock extends FurnitureBlock {
    public static final int EMPTY = 0;
    /** A pallet of boxes, shrink-wrapped into one tall block. */
    public static final int WRAPPED = 1;
    /** A pallet with a few loose boxes on it. */
    public static final int LOOSE = 2;
    /** An empty pallet. */
    public static final int PALLET = 3;
    /** A stack that slumped: boxes crushed and leaning, wrap torn. */
    public static final int COLLAPSED = 4;
    public static final IntegerProperty LOAD = IntegerProperty.create("load", 0, 4);
    public static final BooleanProperty BEAMS = BooleanProperty.create("beams");

    public RackBayBlock(Properties properties) {
        super(properties, Block.box(0, 0, 0, 16, 15, 16));
        registerDefaultState(defaultBlockState().setValue(LOAD, WRAPPED).setValue(BEAMS, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LOAD, BEAMS);
    }
}
