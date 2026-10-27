package com.rickr.backrooms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A featureless grey shop mannequin on a round stand, two blocks tall (the film's mannequin
 * clusters among the half-walls). It is two blocks like a door: HALF = LOWER (stand, legs and hips)
 * and HALF = UPPER (chest, arms and head) directly above it, both with the same FACING (the way
 * the mannequin looks). The world generator places both halves itself.
 */
public class Level0MannequinBlock extends FurnitureBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    // Roughly the body's outline; the same in every direction, so no turning is needed.
    private static final VoxelShape LOWER = Shapes.or(Block.box(3, 0, 3, 13, 1, 13), Block.box(4.5, 1, 5.5, 11.5, 16, 10.5));
    private static final VoxelShape UPPER = Block.box(3.5, 0, 5, 12.5, 14.5, 11);

    public Level0MannequinBlock(Properties properties) {
        super(properties, Shapes.block());
        registerDefaultState(defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HALF);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER;
    }

    /** In creative: only placed if there is room for the upper half too. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos above = context.getClickedPos().above();
        if (above.getY() >= context.getLevel().getMaxBuildHeight() || !context.getLevel().getBlockState(above).canBeReplaced(context)) {
            return null;
        }
        return super.getStateForPlacement(context);
    }

    /** ...and the upper half is added right after the lower one, as a door does. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }
}
