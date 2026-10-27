package com.rickr.backrooms.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Walkable steel bar grating over Level 2's pipe trenches: you stand on it like a floor (full
 * block collision) but see the pipes and dim light below through the bars. The bars run along
 * AXIS. Faces between two gratings are skipped (like glass), so a long run doesn't draw a wall
 * of grating edges inside the trench.
 */
public class Level2GratingBlock extends AxisShapeBlock {

    public Level2GratingBlock(Properties properties) {
        super(properties, Shapes.block());
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }
}
