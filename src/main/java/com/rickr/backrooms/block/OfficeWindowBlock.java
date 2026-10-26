package com.rickr.backrooms.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A pane of Level 4's ribbon windows: dark rainy glass in an aluminium frame, with nothing
 * outside but night and rain. It is a solid block in the wall: only its FACING side shows glass
 * (rain running down it, animated), the back and the sides are plain painted wall, so a window wall
 * looks like an outer wall from the room and like an ordinary wall from behind.
 *
 * <p>HALF picks the frame: the LOWER pane has the sill rail at its bottom, the UPPER pane the head
 * rail at its top, so two stacked panes make one tall window. The block gives off block light:
 * that is the faint cold night glow on the floor and radiators near the windows, and because Level
 * 4's lighting curve turns block light into brightness, it is also what flashes white when
 * lightning strikes outside (see {@code ClientLighting} and {@code Level4Atmosphere}).
 */
public class OfficeWindowBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public OfficeWindowBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    /** In creative: the glass faces you; placed on top of another pane it becomes the upper half. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState below = context.getLevel().getBlockState(context.getClickedPos().below());
        DoubleBlockHalf half = below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER
                ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(HALF, half);
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
