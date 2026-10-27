package com.rickr.backrooms.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A piece of period furniture in a Level 39 house (armchair, radio cabinet, piano, icebox ...).
 * FACING is the way its front looks, like every {@link FurnitureBlock}.
 *
 * <p>INVERTED turns it upside down. Level 39 has houses built upside down and houses whose
 * furniture hangs from the ceiling (canon: "furniture in odd or inverted positions"). The
 * blockstate file draws an inverted piece with the model turned half way round the x axis
 * ("x": 180) and then half way round the y axis, which flips it top to bottom and left to right
 * but keeps its back on the same side; {@link #flip} does exactly the same to the shape.
 */
public class Level39FurnitureBlock extends FurnitureBlock {
    public static final BooleanProperty INVERTED = BooleanProperty.create("inverted");

    private final Map<Direction, VoxelShape> invertedShapes = new EnumMap<>(Direction.class);

    /** @param shapeFacingNorth the shape when its front looks north (back on the south side), right way up */
    public Level39FurnitureBlock(Properties properties, VoxelShape shapeFacingNorth) {
        super(properties, shapeFacingNorth);
        // A BooleanProperty lists "true" first, so the default must be set or it would start upside down.
        registerDefaultState(defaultBlockState().setValue(INVERTED, false));
        VoxelShape flipped = flip(shapeFacingNorth);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            invertedShapes.put(facing, turn(flipped, facing));
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(INVERTED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(INVERTED)) {
            return invertedShapes.get(state.getValue(FACING));
        }
        return super.getShape(state, level, pos, context);
    }

    /**
     * The model's "x": 180 then "y": 180 turn: top becomes bottom and left becomes right, the
     * back stays at the back.
     */
    public static VoxelShape flip(VoxelShape shape) {
        VoxelShape[] out = {Shapes.empty()};
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) ->
                out[0] = Shapes.or(out[0], Shapes.box(1 - x1, 1 - y1, z0, 1 - x0, 1 - y0, z1)));
        return out[0].optimize();
    }
}
