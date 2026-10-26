package com.rickr.backrooms.item;

import com.rickr.backrooms.registry.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
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
 * Bandage: hold right-click for 1.5 s to wrap a wound. Heals 2 hearts at once and gives
 * Regeneration I for 5 seconds.
 */
public class BandageItem extends Item {
    public BandageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BANDAGE_WRAP.get(),
                    SoundSource.PLAYERS, 0.8f, 0.95f + level.getRandom().nextFloat() * 0.1f);
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            entity.heal(4.0f);
            // 100 ticks = 5 s; amplifier 0 = Regeneration I.
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
        }
        // consume() leaves the stack alone in creative mode.
        stack.consume(1, entity);
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 30; // 1.5 seconds
    }

    /** Held up in front of you like a drawn bow, as if wrapping your arm. */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.backrooms.bandage.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
