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
 * A pane of Level 228 (mirror, clear glass or painted panel; v1's exit mirror without
 * collision): a thin collidable sheet through the middle of the block. In v2 it draws nothing:
 * the silvering, the glass, the frame and the painted panels are props
 * (client/level228 prop renderer, world/layout/Level228Plan#propsInChunk), and the "reflection"
 * is real geometry built behind the glass.
 *
 * <p>AXIS is the pane's NORMAL: {@code axis=x} is a pane in the plane x = blockX + 0.5 (it runs
 * north-south). Mirrored across either axis the pane stays the same pane, which is what lets an
 * image volume hold mirrored copies of the panes in front of it.
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

    /** The props draw the pane (aged silvering, glass or paint), so the block itself is invisible. */
    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.INVISIBLE;
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
