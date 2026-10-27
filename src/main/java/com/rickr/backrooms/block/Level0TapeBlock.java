package com.rickr.backrooms.block;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A strip of teal gaffer tape stuck on a wall, the ceiling or the floor (the film's tape lines that
 * someone ran along the walls and across the ceiling to find their way back). It sits in the AIR
 * block in front of the surface, flat against it, and you walk straight through it.
 *
 * <p>FACING points away from the surface, into the room, like {@link WallMountBlock}: NORTH = taped
 * on the wall to the south, DOWN = taped on the ceiling above, UP = on the floor below.
 * SHAPE says which way the strip runs:
 * <ul>
 *   <li>on a wall: HORIZONTAL runs along the wall, VERTICAL runs up it, CORNER runs up the wall
 *       and folds over onto the ceiling, continuing straight out from the wall (so a line can go up
 *       a wall and carry on across the ceiling in DOWN-facing blocks);</li>
 *   <li>on the ceiling or floor: HORIZONTAL runs along x, VERTICAL along z, CORNER is two strips
 *       crossing (where two lines meet).</li>
 * </ul>
 */
public class Level0TapeBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);

    /** Which way the strip runs (see the class comment). */
    public enum Shape implements StringRepresentable {
        HORIZONTAL, VERTICAL, CORNER;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    // What you see outlined when you look at the tape: a paper-thin plate against the surface.
    private static final Map<Direction, VoxelShape> PLATES = new EnumMap<>(Direction.class);

    static {
        PLATES.put(Direction.NORTH, Block.box(0, 0, 15.5, 16, 16, 16));
        PLATES.put(Direction.SOUTH, Block.box(0, 0, 0, 16, 16, 0.5));
        PLATES.put(Direction.EAST, Block.box(0, 0, 0, 0.5, 16, 16));
        PLATES.put(Direction.WEST, Block.box(15.5, 0, 0, 16, 16, 16));
        PLATES.put(Direction.DOWN, Block.box(0, 15.5, 0, 16, 16, 16));
        PLATES.put(Direction.UP, Block.box(0, 0, 0, 16, 0.5, 16));
    }

    public Level0TapeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHAPE, Shape.VERTICAL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PLATES.get(state.getValue(FACING));
    }

    /** In creative: click a wall, floor or ceiling and the tape sticks to it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Shape shape = face.getAxis().isHorizontal() ? Shape.VERTICAL
                : context.getHorizontalDirection().getAxis() == Direction.Axis.X ? Shape.HORIZONTAL : Shape.VERTICAL;
        return defaultBlockState().setValue(FACING, face).setValue(SHAPE, shape);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction facing = state.getValue(FACING);
        Shape shape = state.getValue(SHAPE);
        // On the ceiling or floor a quarter turn swaps a run along x for a run along z.
        boolean quarter = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
        if (quarter && facing.getAxis() == Direction.Axis.Y && shape != Shape.CORNER) {
            shape = shape == Shape.HORIZONTAL ? Shape.VERTICAL : Shape.HORIZONTAL;
        }
        return state.setValue(FACING, rotation.rotate(facing)).setValue(SHAPE, shape);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
