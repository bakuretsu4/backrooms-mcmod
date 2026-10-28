package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A quarter-turn ball valve with a red lever handle, bolted against a wall (FACING points away
 * from the wall, as for every {@link WallMountBlock}).
 *
 * <ul>
 *   <li>OPEN: the handle lies along the pipe (open), or across it (shut). Anyone can read a
 *       row of these at a glance, which is what makes a row all turned the wrong way unsettling.</li>
 *   <li>FEED: the pipe comes down from a header above (a {@link Level2FeedPipeBlock}) and carries
 *       on down through the valve; otherwise it is a stub straight out of the wall.</li>
 * </ul>
 */
public class Level2LeverValveBlock extends WallMountBlock {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final BooleanProperty FEED = Level2ValveBlock.FEED;

    public Level2LeverValveBlock(Properties properties) {
        // Body, stub and handle facing north (must match _lever_elements in level2_revamp.py).
        super(properties, 3, 0, 5, 13, 15, 16);
        registerDefaultState(defaultBlockState().setValue(OPEN, true).setValue(FEED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN, FEED);
    }
}
