package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Concrete floor with something painted or spilled on top (Level 1's parking markings). FACING
 * turns the top: a line lies along its FACING edge, an arrow points FACING, a number reads from
 * the FACING side. VARIANT: see the constants.
 */
public class FloorMarkBlock extends WallDecalBlock {
    /** A white stall line along the FACING edge. */
    public static final int LINE = 0;
    /** A white driving arrow. */
    public static final int ARROW = 1;
    public static final int OIL = 2;
    public static final int TYRES = 3;
    public static final int DRAIN = 4;
    /** Yellow dashes down the middle of a lane (along the FACING axis). */
    public static final int DASHES = 5;
    public static final int SLOW = 6;
    /** 7 .. 7 + STALL_NUMBERS - 1: a painted stall number. */
    public static final int STALL = 7;
    public static final int STALL_NUMBERS = 6;
    public static final int VARIANTS = STALL + STALL_NUMBERS;
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, VARIANTS - 1);

    public FloorMarkBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(VARIANT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VARIANT);
    }
}
