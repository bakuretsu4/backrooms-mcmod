package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A corner block of a Level 1 pillar, with its outer edge bevelled off (chamfered, like a cast
 * concrete column). FACING says which corner: NORTH = north-west, EAST = north-east, SOUTH =
 * south-east, WEST = south-west. BAND is the paint at this height: see {@link #PLAIN} etc.
 */
public class PillarCornerBlock extends WallDecalBlock {
    public static final int PLAIN = 0;
    /** The dark base band of the parking sectors. */
    public static final int BASE_DARK = 1;
    /** The hazard-striped base band of the warehouse sector. */
    public static final int BASE_HAZARD = 2;
    /** The sector-colour band behind the numbers: 3 + the sector (Level1Areas.AQUILA/GILD/RUST). */
    public static final int SECTOR_BAND = 3;
    public static final IntegerProperty BAND = IntegerProperty.create("band", 0, 5);

    public PillarCornerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BAND, PLAIN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BAND);
    }
}
