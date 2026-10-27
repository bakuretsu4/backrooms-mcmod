package com.rickr.backrooms.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A part that runs along a horizontal AXIS (x or z) and only fills some of its block: Level 39's
 * utility-pole crossarms, the bar of the rusty swing set, the picket fences. The shape is given
 * once for AXIS = X and turned a quarter for Z, the same quarter turn the blockstate file gives the
 * model ("y": 90), so shape and model always agree.
 */
public class AxisShapeBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    private final Map<Direction.Axis, VoxelShape> shapes = new EnumMap<>(Direction.Axis.class);
    private final Map<Direction.Axis, VoxelShape> collisions = new EnumMap<>(Direction.Axis.class);

    /**
     * @param shapeAlongX     what you see outlined (and click), for a part running east-west
     * @param collisionAlongX what you bump into, for the same part (often taller than it looks,
     *                        like a vanilla fence, so nobody hops over)
     */
    public AxisShapeBlock(Properties properties, VoxelShape shapeAlongX, VoxelShape collisionAlongX) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
        shapes.put(Direction.Axis.X, shapeAlongX);
        shapes.put(Direction.Axis.Z, turn(shapeAlongX));
        collisions.put(Direction.Axis.X, collisionAlongX);
        collisions.put(Direction.Axis.Z, turn(collisionAlongX));
    }

    public AxisShapeBlock(Properties properties, VoxelShape shapeAlongX) {
        this(properties, shapeAlongX, shapeAlongX);
    }

    /** A quarter turn: x becomes z (the same turn as "y": 90 in a blockstate file). */
    private static VoxelShape turn(VoxelShape alongX) {
        VoxelShape[] turned = {Shapes.empty()};
        alongX.forAllBoxes((x0, y0, z0, x1, y1, z1) -> turned[0] = Shapes.or(turned[0], Shapes.box(1 - z1, y0, x0, 1 - z0, y1, x1)));
        return turned[0].optimize();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(AXIS));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return collisions.get(state.getValue(AXIS));
    }

    /** In creative the part runs the way you are looking. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        return state;
    }
}
