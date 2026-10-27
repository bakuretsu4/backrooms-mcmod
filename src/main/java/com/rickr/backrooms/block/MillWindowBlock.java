package com.rickr.backrooms.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * One block of a Level 39 mill window: 15 of them (3 across, 5 up) make one tall industrial sash,
 * twelve small panes over twelve, dark muntins, a heavier frame round the outside and coal soot on
 * the lower panes. The glass is see-through: the brightness comes from the empty white gallery
 * behind it. COL and ROW say which piece of the window this is (COL as seen from the town, 0 =
 * left; ROW 0 = bottom); ARCH marks the top row of an arched top-storey window, where brick
 * curves down into the corners. FACING points toward the town.
 *
 * <p>Like vanilla glass, two window blocks side by side hide the face between them.
 */
public class MillWindowBlock extends TransparentBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty COL = IntegerProperty.create("col", 0, 2);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 4);
    public static final BooleanProperty ARCH = BooleanProperty.create("arch");

    public MillWindowBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COL, 1)
                .setValue(ROW, 2).setValue(ARCH, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COL, ROW, ARCH);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
