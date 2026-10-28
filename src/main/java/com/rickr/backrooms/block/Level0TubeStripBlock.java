package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Level 0 corridor light strip ({@code l0_troffer_strip}): like {@link AxisShapeBlock} (it
 * runs along x or z), plus the tube's age ({@link Level0TubeGlow#GLOW}) so older stretches give
 * less light. The default is a full-strength tube.
 */
public class Level0TubeStripBlock extends AxisShapeBlock {
    public Level0TubeStripBlock(Properties properties, VoxelShape shapeAlongX) {
        super(properties, shapeAlongX);
        registerDefaultState(defaultBlockState().setValue(Level0TubeGlow.GLOW, Level0TubeGlow.Glow.FULL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(Level0TubeGlow.GLOW);
    }
}
