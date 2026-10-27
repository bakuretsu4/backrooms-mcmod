package com.rickr.backrooms.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * One piece of the round, domed chamber where three or four Level 39 tunnels meet: marble whose
 * inside follows the round wall and the shallow dome (in 2/16-block steps). PIECE =
 * (a * 9 + b) * 10 + row, where (a, b) is the piece's offset from the chamber's centre turned
 * into the south-east quarter (both 0..8) and row its height above the walkway (0..9). FACING
 * turns the south-east model into the other quarters: SOUTH = south-east, WEST = south-west,
 * NORTH = north-west, EAST = north-east. See {@code Level39Vault}.
 */
public class DomeBlock extends Block {
    public static final int PIECES = 9 * 9 * 10;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PIECE = IntegerProperty.create("piece", 0, PIECES - 1);

    public DomeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(PIECE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PIECE);
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
