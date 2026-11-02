package com.rickr.backrooms.block;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Level 4's emergency exit sign: a slim lit box, white running man and door on green, with an
 * arrow when the way out is to one side. On a wall (FACING points away from the wall), or HANGING:
 * a double-sided sign on two rods from the ceiling, in the air block just under it, for big open
 * spaces (FACING is the way its front faces; the back shows the same sign mirrored the right way).
 *
 * <p>ARROW: NONE (the exit is right here, e.g. over the stairwell door), LEFT or RIGHT as seen by
 * someone reading the front. The reader's left is the world direction {@code FACING.getClockWise()}.
 */
public class OfficeExitSignBlock extends WallMountBlock {
    public static final EnumProperty<Arrow> ARROW = EnumProperty.create("arrow", Arrow.class);
    public static final BooleanProperty HANGING = BooleanProperty.create("hanging");

    /** Which way the sign points (see the class comment). */
    public enum Arrow implements StringRepresentable {
        NONE, LEFT, RIGHT;

        /** The lowercase name used in blockstate files ("arrow=left"). */
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Outline of the hanging sign (sign and rods), per facing. */
    private final Map<Direction, VoxelShape> hangingShapes = new EnumMap<>(Direction.class);

    public OfficeExitSignBlock(Properties properties) {
        super(properties, Block.box(3, 5, 14, 13, 11, 16));
        registerDefaultState(defaultBlockState().setValue(ARROW, Arrow.NONE).setValue(HANGING, false));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            hangingShapes.put(facing, turn(Block.box(2.5, 4, 7, 13.5, 16, 9), facing));
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ARROW, HANGING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HANGING) ? hangingShapes.get(state.getValue(FACING)) : super.getShape(state, level, pos, context);
    }

    /** In a mirror the arrow points the other way round (left and right swap). */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState turned = super.mirror(state, mirror);
        if (mirror == Mirror.NONE) {
            return turned;
        }
        Arrow arrow = state.getValue(ARROW);
        return turned.setValue(ARROW, arrow == Arrow.LEFT ? Arrow.RIGHT : arrow == Arrow.RIGHT ? Arrow.LEFT : arrow);
    }
}
