package com.rickr.backrooms.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A tile slab that can stand in Level 37's ankle-deep water ({@link Flooding#FLOODED}): the
 * floating treads of the spiral stairs start right on the flooded floor, and the water must run
 * on around and under them. Under real pool water it uses the usual {@code waterlogged} instead.
 */
public class FloodableSlabBlock extends SlabBlock {
    public static final MapCodec<FloodableSlabBlock> CODEC = simpleCodec(FloodableSlabBlock::new);

    public FloodableSlabBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(Flooding.FLOODED, false));
    }

    @Override
    public MapCodec<? extends SlabBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(Flooding.FLOODED);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(Flooding.FLOODED)) {
            ShallowWaterBlock.applyDrag(entity, pos);
        }
    }
}
