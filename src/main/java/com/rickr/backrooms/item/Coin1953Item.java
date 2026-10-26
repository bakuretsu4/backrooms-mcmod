package com.rickr.backrooms.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A worn coin dated 1953, from the plughole of a sink in a pile of furniture near Level 94's
 * castle, just where the M.E.G. found theirs
 * (https://backrooms-wiki.wikidot.com/m-e-g-the-discovery-of-level-94). It does nothing: a
 * keepsake, and proof of how old the things in this place are.
 */
public class Coin1953Item extends Item {
    public Coin1953Item(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.backrooms.coin_1953.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.backrooms.coin_1953.tooltip2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
