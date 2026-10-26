package com.rickr.backrooms.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A chrome handrail along a tiled wall, about a metre above the floor, like the rails in the
 * flooded Level 37 corridors. It is placed in the cell one above the feet (the tube hangs at the
 * bottom of that cell, the posts reach down to the floor through the water); {@link #FACING}
 * points away from the wall. With {@link #POST} a post stands under the rail, otherwise a small
 * bracket holds it to the wall. You walk through it.
 */
public class HandrailBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<HandrailBlock> CODEC = simpleCodec(HandrailBlock::new);
    public static final BooleanProperty POST = BooleanProperty.create("post");

    private static final VoxelShape SOUTH_SHAPE = Block.box(0, 0, 0, 16, 2, 6);
    private static final VoxelShape WEST_SHAPE = Block.box(10, 0, 0, 16, 2, 16);
    private static final VoxelShape NORTH_SHAPE = Block.box(0, 0, 10, 16, 2, 16);
    private static final VoxelShape EAST_SHAPE = Block.box(0, 0, 0, 6, 2, 16);

    public HandrailBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(POST, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POST);
    }

    /** In creative: on the wall you clicked, sticking out toward you. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    /** Only for aiming at it: the tube along the wall. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case WEST -> WEST_SHAPE;
            case NORTH -> NORTH_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> SOUTH_SHAPE;
        };
    }
}
