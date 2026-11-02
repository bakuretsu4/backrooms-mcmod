package com.rickr.backrooms.item;

import com.rickr.backrooms.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * A Neighborhood Watch flyer from Level 9: a photocopied sheet of "rules" for living on the
 * street, found in mailboxes, pinned on porches and in supply crates. Right-click to read it (the
 * client opens the page, see client/level9/Level9StoryClient; this class only says "handled").
 *
 * <p>Which text it has (0..{@link #FLYERS}-1, {@code flyer.backrooms.l9.N} in the language file)
 * is kept in its custom data as "flyer". A flyer that has none yet (from a loot table or from
 * /give without data) picks one the first time it sits in a player's inventory.
 */
public class WatchFlyerItem extends Item {
    public static final int FLYERS = 10;
    public static final String FLYER = "flyer";

    public WatchFlyerItem(Properties properties) {
        super(properties);
    }

    /** A flyer with text {@code flyer}. */
    public static ItemStack create(int flyer) {
        ItemStack stack = new ItemStack(ModItems.WATCH_FLYER.get());
        CompoundTag tag = new CompoundTag();
        tag.putInt(FLYER, flyer);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** The flyer's text number, or -1 if it has not picked one yet. */
    public static int flyerOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(FLYER)) {
            return -1;
        }
        return Math.floorMod(data.copyTag().getInt(FLYER), FLYERS);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && flyerOf(stack) < 0) {
            CompoundTag tag = new CompoundTag();
            tag.putInt(FLYER, level.random.nextInt(FLYERS));
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
