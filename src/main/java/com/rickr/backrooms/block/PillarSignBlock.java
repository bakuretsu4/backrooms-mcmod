package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A garage sign on a Level 1 pillar (see {@link WallDecalBlock}): the floor's "P1" plate, the
 * headroom limit, NO PARKING, the 5 km/h limit, and the sector's own zone letter.
 */
public class PillarSignBlock extends WallDecalBlock {
    public static final int VARIANTS = 6;
    public static final int LEVEL = 0;
    public static final int HEADROOM = 1;
    public static final int NO_PARKING = 2;
    public static final int SPEED = 3;
    /** 4 + ... : the zone plates, one per sector colour scheme (only two are drawn: blue and orange; rust reuses blue). */
    public static final int ZONE = 4;
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, VARIANTS - 1);

    public PillarSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(VARIANT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VARIANT);
    }
}
