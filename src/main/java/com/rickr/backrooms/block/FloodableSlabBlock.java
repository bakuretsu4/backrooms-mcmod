package com.rickr.backrooms.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A floating tile tread of Level 37's spiral stairs: a slab's walking height (half or full
 * block) but only a quarter block thick, so a flight reads as thin plates hanging in the air, not
 * a stack of half cubes. It can stand in the ankle-deep water ({@link Flooding#FLOODED}): the
 * treads start right on the flooded floor, and the water must run on around and under them.
 * Under real pool water it uses the usual {@code waterlogged} instead.
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

    /** Bottom: a plate under the half-block line; top: a plate under the block's top. */
    private static final VoxelShape BOTTOM = Block.box(0, 4, 0, 16, 8, 16);
    private static final VoxelShape TOP = Block.box(0, 12, 0, 16, 16, 16);

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(TYPE)) {
            case BOTTOM -> BOTTOM;
            case TOP -> TOP;
            case DOUBLE -> Shapes.block();
        };
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(Flooding.FLOODED)) {
            ShallowWaterBlock.applyDrag(entity, pos);
        }
    }
}
