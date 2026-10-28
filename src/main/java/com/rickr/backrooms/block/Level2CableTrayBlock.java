package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Level 2's ladder cable tray, hung on two threaded rods a little below the ceiling and full of
 * cables, running along AXIS. It hangs lower than it used to so the steel beams above it and the
 * pipes beside it read as separate layers.
 *
 * <ul>
 *   <li>BEAM: a steel I-beam crosses the block above the tray (see {@link Level2PipeBlock#BEAM}).</li>
 *   <li>DROOP: a few cables have slipped out of the tray and sag in a loop below it.</li>
 * </ul>
 */
public class Level2CableTrayBlock extends AxisShapeBlock {
    public static final BooleanProperty BEAM = Level2PipeBlock.BEAM;
    public static final BooleanProperty DROOP = BooleanProperty.create("droop");

    public Level2CableTrayBlock(Properties properties) {
        // The trapeze, tray and cables from 2 px up, the rods to the ceiling (must match the model).
        super(properties, Block.box(0, 2, 1, 16, 16, 15));
        registerDefaultState(defaultBlockState().setValue(BEAM, false).setValue(DROOP, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BEAM, DROOP);
    }
}
