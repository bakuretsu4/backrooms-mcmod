package com.rickr.backrooms.block;

import com.rickr.backrooms.player.Checkpoints;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The old field radio on a small table in every safe room. Right-click it to save your
 * checkpoint: die on this level and you wake up on the floor in front of it.
 *
 * <p>Like all furniture, FACING is the way its front (dial and speaker) looks, so the radio
 * stands against the wall behind it and faces into the room. The checkpoint is the floor spot
 * right in front of it.
 */
public class CheckpointRadioBlock extends FurnitureBlock {
    /** Table and radio as they stand when the front looks north (the wall is on the south side). */
    private static final VoxelShape SHAPE_FACING_NORTH = Shapes.or(
            Block.box(1, 8, 4, 15, 10, 15),        // table top
            Block.box(2, 0, 5, 3.5, 8, 6.5),       // four legs
            Block.box(12.5, 0, 5, 14, 8, 6.5),
            Block.box(2, 0, 12.5, 3.5, 8, 14),
            Block.box(12.5, 0, 12.5, 14, 8, 14),
            Block.box(3, 10, 7, 13, 16, 13));      // the radio set

    public CheckpointRadioBlock(Properties properties) {
        super(properties, SHAPE_FACING_NORTH);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            Checkpoints.save(serverPlayer, pos, state.getValue(FACING));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
