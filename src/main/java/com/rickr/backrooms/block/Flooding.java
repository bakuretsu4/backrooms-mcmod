package com.rickr.backrooms.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * "Ankle-deep water in this cell, too." Level 37 floods most of its floors a few centimetres deep
 * with {@code SHALLOW_WATER}, a block that fills the whole feet-level cell. Where something else
 * has to stand in that cell (the curved foot of a pillar, the first tread of a stair, a pool
 * ladder), the water would leave a dry notch around it. Blocks that can stand in the shallows
 * carry this property instead; the water renderer draws the same shallow surface over any cell
 * where it is true, and the block's own solid parts simply poke up through it.
 */
public final class Flooding {
    public static final BooleanProperty FLOODED = BooleanProperty.create("flooded");

    /** True when this block stands in ankle-deep water (it has {@link #FLOODED} and it is set). */
    public static boolean isFlooded(BlockState state) {
        return state.hasProperty(FLOODED) && state.getValue(FLOODED);
    }

    private Flooding() {}
}
