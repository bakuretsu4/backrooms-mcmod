package com.rickr.backrooms.block.level9;

import com.rickr.backrooms.item.WatchFlyerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A Neighborhood Watch flyer pinned to a porch post or beside a front door on Level 9. FLYER
 * picks the text ({@code flyer.backrooms.l9.N} in the language file), the same texts as the
 * paper you find in mailboxes and supply crates ({@link WatchFlyerItem}).
 *
 * <p>Right-click: read it (the client opens the page, see {@link PinnedPaperBlock}).
 * Sneak + right-click with an empty hand: pull it off and keep it.
 */
public class WatchFlyerBlock extends PinnedPaperBlock {
    public static final IntegerProperty FLYER = IntegerProperty.create("flyer", 0, WatchFlyerItem.FLYERS - 1);

    /** A letter-sized sheet: on a wall, a lamp pole, or a porch post. */
    private static final double[][] SHEETS = {
            {4, 2, 12, 14},
            {4.5, 3, 11.5, 13},
            {5, 3, 11, 12}};

    public WatchFlyerBlock(Properties properties) {
        super(properties, SHEETS);
        registerDefaultState(defaultBlockState().setValue(FLYER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FLYER);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            return super.useWithoutItem(state, level, pos, player, hit); // read it (client side)
        }
        if (!level.isClientSide) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.6f, 1.2f);
            net.minecraft.world.item.ItemStack flyer = WatchFlyerItem.create(state.getValue(FLYER));
            if (!player.getInventory().add(flyer)) {
                player.drop(flyer, false); // inventory full: it falls at your feet
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
