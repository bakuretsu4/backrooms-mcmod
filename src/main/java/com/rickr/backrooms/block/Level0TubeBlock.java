package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The Level 0 ceiling troffer ({@code fluorescent_light}): a full block that glows, with the
 * tube's age as a block state ({@link Level0TubeGlow#GLOW}) so older tubes give less light. The
 * default is a full-strength tube, which is what every other level and every old save gets.
 */
public class Level0TubeBlock extends Block {
    public Level0TubeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(Level0TubeGlow.GLOW, Level0TubeGlow.Glow.FULL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Level0TubeGlow.GLOW);
    }
}
