package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
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
 * A pipe tee: one pipe straight through the middle of the block along AXIS, and a branch of the
 * same pipe out through the FACING side (a riser dropping from a ceiling run, a branch off a riser
 * into the wall). Joins {@link Level2PipeBlock} pipes with SLOT = CENTER and the same KIND and SIZE.
 * A FACING along AXIS has no side to branch to and is drawn as a straight pipe.
 */
public class Level2PipeTeeBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<Level2PipeBlock.Kind> KIND = Level2PipeBlock.KIND;
    public static final EnumProperty<Level2PipeBlock.Size> SIZE = Level2PipeBlock.SIZE;

    // [axis][facing][size]
    private static final VoxelShape[][][] SHAPES = new VoxelShape[3][6][Level2PipeBlock.Size.values().length];

    static {
        for (Direction.Axis axis : Direction.Axis.values()) {
            for (Direction facing : Direction.values()) {
                for (Level2PipeBlock.Size size : Level2PipeBlock.Size.values()) {
                    VoxelShape run = Level2PipeBlock.pipeBox(axis, 8, 8, 8, size.diameter);
                    SHAPES[axis.ordinal()][facing.ordinal()][size.ordinal()] = facing.getAxis() == axis
                            ? run : Shapes.or(run, Level2PipeElbowBlock.leg(facing, size.diameter));
                }
            }
        }
    }

    public Level2PipeTeeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(FACING, Direction.DOWN)
                .setValue(KIND, Level2PipeBlock.Kind.STEEL).setValue(SIZE, Level2PipeBlock.Size.MEDIUM));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, FACING, KIND, SIZE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(AXIS).ordinal()][state.getValue(FACING).ordinal()][state.getValue(SIZE).ordinal()];
    }

    /** In creative: the branch points back at the face you clicked, the run goes the way you look. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction branch = context.getClickedFace().getOpposite();
        Direction.Axis run = context.getHorizontalDirection().getAxis();
        if (run == branch.getAxis()) {
            run = Direction.Axis.Y;
        }
        return defaultBlockState().setValue(AXIS, run).setValue(FACING, branch);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        state = state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        Direction.Axis axis = state.getValue(AXIS);
        if (axis != Direction.Axis.Y && (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)) {
            state = state.setValue(AXIS, axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        return state;
    }
}
