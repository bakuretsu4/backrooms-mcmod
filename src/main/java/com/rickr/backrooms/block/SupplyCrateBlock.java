package com.rickr.backrooms.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A Wooden Crate left behind by earlier wanderers. Right-click to open it like a chest; the first
 * time, it's filled with supplies (food, almond water, sometimes a camcorder battery).
 */
public class SupplyCrateBlock extends BaseEntityBlock {
    public static final MapCodec<SupplyCrateBlock> CODEC = simpleCodec(SupplyCrateBlock::new);

    public SupplyCrateBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** BaseEntityBlock defaults to invisible (for chests drawn by a renderer); we're a normal cube model. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SupplyCrateBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof SupplyCrateBlockEntity crate) {
            crate.fillLootIfNeeded(serverLevel.getRandom());
            player.openMenu(crate);
            level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.8f, 0.8f + level.getRandom().nextFloat() * 0.1f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Breaking the crate spills whatever is still inside. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        Containers.dropContentsOnDestroy(state, newState, level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
