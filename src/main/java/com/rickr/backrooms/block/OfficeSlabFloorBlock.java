package com.rickr.backrooms.block;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Level 4's lobby terrazzo, laid in large-format slabs two blocks square. One texture shows a
 * whole slab; each block shows the quarter of it that matches its position (VARIANT = which
 * quarter: x even/odd + 2 * z even/odd), so the joints fall every two blocks in a perfect grid.
 * The world generator sets VARIANT through {@link #withVariant} like any surface; inside anomalous
 * areas it stays 0 (see SurfaceBlock), where the slabs simply look one block square.
 */
public class OfficeSlabFloorBlock extends SurfaceBlock {
    public OfficeSlabFloorBlock(Properties properties) {
        super(properties, 1);
    }

    @Override
    public BlockState withVariant(BlockState state, long seed, int x, int y, int z) {
        return state.setValue(VARIANT, (x & 1) + 2 * (z & 1));
    }
}
