package com.rickr.backrooms.item;

import com.rickr.backrooms.player.SanityService;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Almond Milk, from the crates at the back of Level 94's milk vans (the vans are "filled with
 * Almond Milk", https://backrooms-wiki.wikidot.com/level-94). A drink like Almond Water but
 * creamier: it fills you up more and heals a little, though it steadies the nerves less.
 */
public class AlmondMilkItem extends Item {
    public AlmondMilkItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
            entity.heal(2.0f);
            if (entity instanceof Player player) {
                player.getFoodData().eat(6, 0.6f);
            }
            if (entity instanceof ServerPlayer serverPlayer) {
                SanityService.restore(serverPlayer, 20.0f);
            }
        }
        stack.consume(1, entity);
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.backrooms.almond_milk.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
