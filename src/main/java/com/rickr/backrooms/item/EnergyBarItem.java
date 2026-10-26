package com.rickr.backrooms.item;

import com.rickr.backrooms.player.PlayerGameplay;
import com.rickr.backrooms.registry.ModAttachments;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Energy bar: a quick snack (3 hunger, see {@link ModFoods#ENERGY_BAR}) that also gives you your
 * breath back: the sprint energy bar is filled up at once, even if you were out of breath.
 */
public class EnergyBarItem extends Item {
    public EnergyBarItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            player.getData(ModAttachments.ENERGY).refill();
            // Tell the client right away so the HUD stamina line fills up.
            PlayerGameplay.forceSync(player);
        }
        // The normal food behaviour: hunger, saturation, and one bar used up.
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.backrooms.energy_bar.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
