package com.rickr.backrooms.block;

import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Travel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The emergency exit on Level 2. Right-click either half: you and every player within 6 blocks
 * leave the Backrooms together. Placed anywhere outside the Backrooms, it leads in instead.
 */
public class ExitDoorBlock extends Block {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final double GROUP_RADIUS = 6.0;

    public ExitDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            BackroomsLevel from = BackroomsLevel.fromDimension(level.dimension());
            if (from != null) {
                Travel.useExitAsGroup(serverPlayer, from, GROUP_RADIUS);
            } else {
                Travel.enterBackrooms(serverPlayer);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
