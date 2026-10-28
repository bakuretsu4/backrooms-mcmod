package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.Level227FloraSounds;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A plant two blocks tall (HALF = lower / upper): Level 227's tall grass, which hides the giant
 * Venus flytraps, and the giant ferns, whose fronds arch out past their own blocks.
 *
 * <p>A plain block instead of vanilla's tall grass on purpose: vanilla plants pop off when the
 * ground under them isn't dirt, and here the ground is the mod's own moss and forest soil. The
 * world generator places both halves; in creative, placing the lower half adds the upper one.
 *
 * <p>Pushing through it rustles (a soft sound every few steps), which is the only warning you get
 * that you are wading through the grass the flytraps hide in.
 */
public class Level227TallPlantBlock extends Block {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private static final VoxelShape LOWER = Block.box(2, 0, 2, 14, 16, 14);
    private static final VoxelShape UPPER = Block.box(2, 0, 2, 14, 12, 14);

    public Level227TallPlantBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Both halves get the same nudge (vanilla's offset ignores the height), so they stay joined.
        Vec3 offset = state.getOffset(level, pos);
        VoxelShape shape = state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER;
        return shape.move(offset.x, offset.y, offset.z);
    }

    /** In creative: only if there is room for the top half too. */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return defaultBlockState();
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /**
     * Rustles when something moves through the lower half. Played by the server for everyone
     * nearby; the tick-count check spaces the sounds out to about one per stride.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || state.getValue(HALF) != DoubleBlockHalf.LOWER || !(entity instanceof LivingEntity)) {
            return;
        }
        Vec3 motion = entity.getDeltaMovement();
        if (motion.x * motion.x + motion.z * motion.z < 0.002 || (entity.tickCount + entity.getId()) % 9 != 0) {
            return;
        }
        level.playSound(null, pos, Level227FloraSounds.RUSTLE.get(), SoundSource.BLOCKS,
                0.45f, 0.85f + level.random.nextFloat() * 0.3f);
    }
}
