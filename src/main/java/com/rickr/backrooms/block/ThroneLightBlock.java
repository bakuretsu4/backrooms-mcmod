package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The pure white light that fills the archway behind the empty throne in Level 94's castle. It is
 * full-bright, gives light 15 and you can walk into it; stepping in is what sends you on to
 * Level 1 (see Level94Rules / Level94Castle.isThroneLight). Neighbouring light blocks hide the faces
 * between them, so the archway reads as one solid glow.
 */
public class ThroneLightBlock extends Block {
    public ThroneLightBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }

    /** Light passes as through air (it is light). */
    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
