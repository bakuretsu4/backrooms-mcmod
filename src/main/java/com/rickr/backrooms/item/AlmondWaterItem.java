package com.rickr.backrooms.item;

import com.rickr.backrooms.player.HallucinationService;
import com.rickr.backrooms.player.SanityService;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Almond water: drink to heal a little and steady yourself (Regeneration II for 6 s, some hunger).
 * In the Poolrooms it also clears your head at once: wet meter to zero, your "friend" vanishes.
 * It also steadies your nerves: +35 sanity.
 */
public class AlmondWaterItem extends Item {
    public AlmondWaterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1));
            entity.heal(2.0f);
            if (entity instanceof Player player) {
                player.getFoodData().eat(3, 0.4f);
            }
            if (entity instanceof ServerPlayer serverPlayer) {
                HallucinationService.cure(serverPlayer);
                SanityService.restore(serverPlayer, 35.0f); // a sip of normality
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
        tooltip.add(Component.translatable("item.backrooms.almond_water.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
