package com.rickr.backrooms.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * A wall-mounted stainless drinking fountain, the kind in office corridors. Canon says Level 4 has
 * "fountains containing Almond Water", so it works like the water cooler: right-click for a bottle
 * of Almond Water, {@link #MAX_USES} times (USES), then it is dry. FACING is its front; its back is
 * on the wall, in the first block above the floor.
 */
public class OfficeFountainBlock extends WaterCoolerBlock {
    public OfficeFountainBlock(Properties properties) {
        super(properties, Shapes.or(
                Block.box(3, 9, 7, 13, 13, 16),       // the bowl
                Block.box(5, 2, 12, 11, 9, 16)));     // the body under it
    }

    @Override
    protected String emptyMessageKey() {
        return "message.backrooms.office_fountain.empty";
    }
}
