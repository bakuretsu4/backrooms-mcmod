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

/**
 * A full block of Level 2's corridor concrete with something on ONE face: the painted band that
 * runs round the cross-section, or (see {@link Level2LightPanelBlock}) a recessed light panel.
 * FACING is the face that shows it, pointing out of the wall, ceiling or floor into the corridor,
 * so the same block works on either wall, overhead and underfoot (the twist stretches move these
 * round the corridor, see world/layout/Level2Twist). Solid like the concrete it replaces.
 */
public class Level2FaceBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public Level2FaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** In creative: the face you click on shows the paint (or the panel). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
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
