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
 * One piece of marble where two of Level 39's curved ceilings meet: a run's barrel vault coming
 * into a domed chamber, or two runs crossing where one turns a corner. Its inside is the true
 * meeting of the two curves (a sharp crease), which the separate vault and dome pieces could only
 * make as a saw-tooth of steps. FACING turns the piece like {@link DomeBlock}. Junction pieces:
 * FACING is the way the run leaves the chamber; PIECE = ((along - 4) * 9 + (across + 4)) * 10 +
 * row, where along (4..8) is the distance from the chamber's centre along the run, across (-4..4)
 * the offset to the side and row the height above the walkway (0..9). Corner pieces: PIECE = 450 +
 * their place in data/backrooms/level39/junction_cells.txt. See {@code Level39Vault}.
 */
public class JunctionBlock extends Block {
    public static final int PIECES = 512;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PIECE = IntegerProperty.create("piece", 0, PIECES - 1);

    public JunctionBlock(Properties properties) {
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
