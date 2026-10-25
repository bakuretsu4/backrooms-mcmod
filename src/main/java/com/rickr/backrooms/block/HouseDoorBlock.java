package com.rickr.backrooms.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A painted front door of a Level 9 house. Most of them are LOCKED: using one only rattles the
 * handle. Unlocked ones are ordinary wooden doors into the dark, empty rooms.
 *
 * <p>Both halves of a door always carry the same LOCKED value, because the vanilla door code copies
 * the whole state from one half to the other.
 */
public class HouseDoorBlock extends DoorBlock {
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");

    public HouseDoorBlock(BlockSetType type, Properties properties) {
        super(type, properties);
        // Set LOCKED explicitly: otherwise the default state would pick "true" (the first value).
        registerDefaultState(defaultBlockState().setValue(LOCKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LOCKED);
    }

    /** A locked door stays shut: the handle rattles and nothing else happens. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!state.getValue(LOCKED)) {
            return super.useWithoutItem(state, level, pos, player, hitResult);
        }
        if (!level.isClientSide) {
            // The vanilla "locked chest" sound is exactly a short handle rattle.
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8f,
                    0.9f + level.getRandom().nextFloat() * 0.2f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Villagers, zombies and wind charges open doors through here; locked doors ignore them too. */
    @Override
    public void setOpen(@Nullable Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
        if (!state.is(this) || !state.getValue(LOCKED)) {
            super.setOpen(entity, level, state, pos, open);
        }
    }

    /** Redstone can't open a locked door either. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!state.getValue(LOCKED)) {
            super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        }
    }
}
