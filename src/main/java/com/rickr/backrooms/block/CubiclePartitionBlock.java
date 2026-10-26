package com.rickr.backrooms.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A grey fabric cubicle partition with an aluminium cap, 1.5 blocks tall. It joins its neighbours
 * like a glass pane: a thin post, plus an arm towards every side (NORTH/EAST/SOUTH/WEST) where
 * another partition or a solid wall is. You can't climb over it: its collision is 1.5 blocks
 * high, like a fence.
 *
 * <p>Level 4 sets the four sides directly when it builds the cubicle rows; the update code below
 * only matters for partitions placed by hand in creative.
 */
public class CubiclePartitionBlock extends CrossCollisionBlock {
    public static final MapCodec<CubiclePartitionBlock> CODEC = simpleCodec(CubiclePartitionBlock::new);

    public CubiclePartitionBlock(Properties properties) {
        // Post and arms 2 px thick (the panel), 24 px (1.5 blocks) tall, collision 24 px tall.
        super(2.0F, 2.0F, 24.0F, 24.0F, 24.0F, properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends CrossCollisionBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, WEST, SOUTH, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = pos.relative(side);
            state = state.setValue(PROPERTY_BY_DIRECTION.get(side), joins(level.getBlockState(next), level, next, side));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (side.getAxis().isHorizontal()) {
            return state.setValue(PROPERTY_BY_DIRECTION.get(side), joins(neighbor, level, neighborPos, side));
        }
        return super.updateShape(state, side, neighbor, level, pos, neighborPos);
    }

    /** Joins other partitions and solid walls. */
    private boolean joins(BlockState neighbor, BlockGetter level, BlockPos neighborPos, Direction side) {
        return neighbor.getBlock() instanceof CubiclePartitionBlock || neighbor.isFaceSturdy(level, neighborPos, side.getOpposite());
    }
}
