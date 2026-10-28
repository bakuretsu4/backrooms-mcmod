package com.rickr.backrooms.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A few sprigs of Lestron, the purple sage-like herb picked at the edges of Level 227's branches.
 * Canon says only that it is "used to create [ACCESS DENIED]", so it does nothing here either:
 * the tooltip keeps the redaction.
 */
public class LestronItem extends Item {
    public LestronItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.backrooms.lestron.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.backrooms.lestron.tooltip2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
