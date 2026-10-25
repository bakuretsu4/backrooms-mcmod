package com.rickr.backrooms.block;

import java.util.EnumMap;
import java.util.Map;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A light fixture screwed onto a wall: the Level 1 tube light, the Level 2 caged bulkhead lamp
 * and the red alarm lamp. It sits in the AIR block in front of the wall, like a wall torch.
 * FACING points away from the wall, into the room. You walk straight through it (no collision),
 * so fixtures never change where players can go.
 */
public class WallLampBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    /**
     * @param width  fixture width along the wall, in pixels (1/16 block)
     * @param bottom lowest pixel of the fixture
     * @param top    highest pixel of the fixture
     * @param depth  how far it sticks out of the wall, in pixels
     */
    public WallLampBlock(Properties properties, int width, int bottom, int top, int depth) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        double lo = 8 - width / 2.0;
        double hi = 8 + width / 2.0;
        // The wall is behind the lamp, i.e. on the side opposite to FACING.
        shapes.put(Direction.NORTH, Block.box(lo, bottom, 16 - depth, hi, top, 16));
        shapes.put(Direction.SOUTH, Block.box(lo, bottom, 0, hi, top, depth));
        shapes.put(Direction.WEST, Block.box(16 - depth, bottom, lo, 16, top, hi));
        shapes.put(Direction.EAST, Block.box(0, bottom, lo, depth, top, hi));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    /** In creative: click the side of a wall and the lamp points out of that side. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
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
