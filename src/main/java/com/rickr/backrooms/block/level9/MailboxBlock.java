package com.rickr.backrooms.block.level9;

import com.rickr.backrooms.block.FurnitureBlock;
import com.rickr.backrooms.item.WatchFlyerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The mailbox on a post at the front of a Level 9 lawn. FACING = the lid side, towards the street.
 *
 * <p>FLAG: the little red flag is up (yard code sets it on some mailboxes). EMPTIED: someone has
 * already opened it. Right-click an unopened mailbox and it is opened for good: there may be a
 * Neighborhood Watch flyer inside ({@link #FLYER_CHANCE_FLAG_UP} with the flag up,
 * {@link #FLYER_CHANCE} otherwise); otherwise it is empty or holds something useless. EMPTIED is
 * part of the block state, so the world remembers it without any extra save data.
 */
public class MailboxBlock extends FurnitureBlock {
    public static final BooleanProperty FLAG = BooleanProperty.create("flag");
    public static final BooleanProperty EMPTIED = BooleanProperty.create("emptied");

    /** Share of unopened mailboxes (flag down) with a flyer inside. */
    static final float FLYER_CHANCE = 0.3f;
    /** Share of unopened mailboxes with the flag up that hold a flyer. */
    static final float FLYER_CHANCE_FLAG_UP = 0.7f;
    /** How many "nothing useful in here" lines there are (message.backrooms.l9_mailbox.empty.N). */
    private static final int EMPTY_LINES = 5;

    public MailboxBlock(Properties properties, VoxelShape shapeFacingNorth) {
        super(properties, shapeFacingNorth);
        registerDefaultState(defaultBlockState().setValue(FLAG, false).setValue(EMPTIED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FLAG, EMPTIED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // The lid squeaks open either way.
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.35f, 1.4f);
        if (state.getValue(EMPTIED)) {
            player.displayClientMessage(Component.translatable("message.backrooms.l9_mailbox.opened"), true);
            return InteractionResult.SUCCESS;
        }
        level.setBlock(pos, state.setValue(EMPTIED, true), Block.UPDATE_CLIENTS);
        float chance = state.getValue(FLAG) ? FLYER_CHANCE_FLAG_UP : FLYER_CHANCE;
        if (level.random.nextFloat() < chance) {
            net.minecraft.world.item.ItemStack flyer = WatchFlyerItem.create(level.random.nextInt(WatchFlyerItem.FLYERS));
            if (!player.getInventory().add(flyer)) {
                player.drop(flyer, false); // inventory full: it falls at your feet
            }
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.6f, 1.0f);
            player.displayClientMessage(Component.translatable("message.backrooms.l9_mailbox.flyer"), true);
        } else {
            player.displayClientMessage(Component.translatable(
                    "message.backrooms.l9_mailbox.empty." + level.random.nextInt(EMPTY_LINES)), true);
        }
        return InteractionResult.SUCCESS;
    }
}
