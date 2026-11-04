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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A pane of Level 228's mirror glass (and, without collision, its exit mirror): a thin sheet
 * through the middle of the block. The "reflection" is real geometry, a mirrored copy of the maze
 * built behind the glass (world/layout/Level228Plan); the pane only has to be see-through.
 *
 * <p>AXIS is the pane's NORMAL: {@code axis=x} is a pane in the plane x = blockX + 0.5 (it runs
 * north-south). Mirrored across either axis the pane stays the same pane, which is what lets an
 * image volume hold mirrored copies of the panes in front of it (the facing-mirror corridors).
 */
public class Level228MirrorBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    /** 2 px thick, centred: the same sheet the model draws, so you bump into what you see. */
    private static final VoxelShape SHAPE_X = Block.box(7, 0, 0, 9, 16, 16);
    private static final VoxelShape SHAPE_Z = Block.box(0, 0, 7, 16, 16, 9);

    public Level228MirrorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
    }

    /** In creative the glass faces you. */
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

    /** Light and the view pass through the sheet; seen edge-on nothing behind it should be culled. */
    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
