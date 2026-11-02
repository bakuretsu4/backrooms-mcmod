package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.ModItems;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Things left on a Level 4 table or desk, as the canon lists them. The block goes in the air
 * block just above a conference table or office desk; its model reaches down onto the 13/16-high
 * tabletop below. Walk-through. FACING is the way the table's user sat (match the desk's FACING).
 *
 * <p>KIND: PAPERS, PENCILS, MUG (a mug half full of Almond Water), PAPERS_MUG, BOTTLE (a bottle
 * of Almond Water: right-click to take it), CUBICLE_SET (what every cubicle desk has: four
 * pencils, two notebooks, three post-it pads, the mug, and the same small sign), BURGER_TRAY (the
 * extremely rare double cheeseburger on a cafeteria tray: right-click to take it). Taking the
 * bottle or the burger empties the block.
 */
public class OfficeTableItemsBlock extends FurnitureBlock {
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);

    /** What lies on the table (see the class comment). */
    public enum Kind implements StringRepresentable {
        PAPERS, PENCILS, MUG, PAPERS_MUG, BOTTLE, CUBICLE_SET, BURGER_TRAY;

        /** The lowercase name used in blockstate files ("kind=burger_tray"). */
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public OfficeTableItemsBlock(Properties properties) {
        // Lying on the tabletop, 3 px below this block's floor.
        super(properties, Block.box(1, -3, 1, 15, 2, 15));
        registerDefaultState(defaultBlockState().setValue(KIND, Kind.PAPERS));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(KIND);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Kind kind = state.getValue(KIND);
        if (kind != Kind.BOTTLE && kind != Kind.BURGER_TRAY) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack taken = new ItemStack(kind == Kind.BOTTLE ? ModItems.ALMOND_WATER.get() : ModItems.DOUBLE_CHEESEBURGER.get());
        if (!player.addItem(taken)) {
            player.drop(taken, false);
        }
        level.removeBlock(pos, false);
        level.playSound(null, pos, kind == Kind.BOTTLE ? SoundEvents.BOTTLE_FILL : SoundEvents.ITEM_PICKUP,
                SoundSource.BLOCKS, 0.6f, 1.0f);
        return InteractionResult.CONSUME;
    }
}
