package com.rickr.backrooms.block;

import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A Level 4 wall block next to a doorway, with the painted door architrave (the moulding round a
 * door opening) on both faces of the wall. Real office doors all have one; without it a door is
 * just a hole in the plaster.
 *
 * <p>AXIS is the way the wall runs. PART says where the block sits: LEFT is the wall block on the
 * lower-coordinate side of the opening (x - 1 for a wall along x, z - 1 for one along z), with the
 * architrave on its edge facing the opening; RIGHT is the block on the other side; HEAD is the
 * block above the opening, its architrave along the bottom (and running over the jambs' tops).
 * BASE adds the skirting, for the jambs in the first block above the floor.
 */
public class OfficeDoorFrameBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final BooleanProperty BASE = BooleanProperty.create("base");

    /** Where the block sits round the opening (see the class comment). */
    public enum Part implements StringRepresentable {
        LEFT, RIGHT, HEAD;

        /** The lowercase name used in blockstate files ("part=head"). */
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public OfficeDoorFrameBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(PART, Part.LEFT).setValue(BASE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, PART, BASE);
    }

    /** In creative: the wall runs across your view (you look at its face). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getClockWise().getAxis());
    }

    /** A quarter turn swaps the wall's axis; a turn that reverses the wall's direction swaps left and right. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction.Axis axis = state.getValue(AXIS);
        Direction along = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        Direction turned = rotation.rotate(along);
        BlockState out = state.setValue(AXIS, turned.getAxis());
        return turned.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? swapped(out) : out;
    }

    /** A mirror across the wall's run swaps its left and right jambs. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction.Axis axis = state.getValue(AXIS);
        boolean flips = (mirror == Mirror.FRONT_BACK && axis == Direction.Axis.X) || (mirror == Mirror.LEFT_RIGHT && axis == Direction.Axis.Z);
        return flips ? swapped(state) : state;
    }

    private static BlockState swapped(BlockState state) {
        Part part = state.getValue(PART);
        return part == Part.HEAD ? state : state.setValue(PART, part == Part.LEFT ? Part.RIGHT : Part.LEFT);
    }
}
