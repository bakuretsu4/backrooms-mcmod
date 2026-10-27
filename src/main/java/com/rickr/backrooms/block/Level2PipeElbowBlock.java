package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A 90 degree pipe bend, where a riser coming up (or down) turns to run along the corridor.
 * One pipe through the middle of the block, so it joins {@link Level2PipeBlock} pipes with
 * SLOT = CENTER and the same KIND and SIZE (for SMALL and MEDIUM that is one thin pipe, not a rack).
 *
 * <p>FACING = the side the horizontal leg leaves through; VERTICAL = the side the vertical leg
 * leaves through (UP: the pipe comes along and turns up, DOWN: it comes along and turns down).
 */
public class Level2PipeElbowBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final DirectionProperty VERTICAL = BlockStateProperties.VERTICAL_DIRECTION;
    public static final EnumProperty<Level2PipeBlock.Kind> KIND = Level2PipeBlock.KIND;
    public static final EnumProperty<Level2PipeBlock.Size> SIZE = Level2PipeBlock.SIZE;

    // [facing 2D index][up?][size]
    private static final VoxelShape[][][] SHAPES = new VoxelShape[4][2][Level2PipeBlock.Size.values().length];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (Direction vertical : new Direction[] {Direction.UP, Direction.DOWN}) {
                for (Level2PipeBlock.Size size : Level2PipeBlock.Size.values()) {
                    SHAPES[facing.get2DDataValue()][vertical == Direction.UP ? 1 : 0][size.ordinal()] =
                            Shapes.or(leg(facing, size.diameter), leg(vertical, size.diameter));
                }
            }
        }
    }

    public Level2PipeElbowBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(VERTICAL, Direction.DOWN)
                .setValue(KIND, Level2PipeBlock.Kind.STEEL).setValue(SIZE, Level2PipeBlock.Size.MEDIUM));
    }

    /** A pipe of the given diameter from the middle of the block out through one face. */
    static VoxelShape leg(Direction side, double diameter) {
        double r = diameter / 2;
        double[] lo = {8 - r, 8 - r, 8 - r};
        double[] hi = {8 + r, 8 + r, 8 + r};
        int i = side.getAxis().ordinal();
        if (side.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            hi[i] = 16;
        } else {
            lo[i] = 0;
        }
        return Block.box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, VERTICAL, KIND, SIZE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()][state.getValue(VERTICAL) == Direction.UP ? 1 : 0][state.getValue(SIZE).ordinal()];
    }

    /** In creative: the horizontal leg points at you; on a floor the other leg goes down into it, otherwise up. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(VERTICAL, context.getClickedFace() == Direction.UP ? Direction.DOWN : Direction.UP);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
