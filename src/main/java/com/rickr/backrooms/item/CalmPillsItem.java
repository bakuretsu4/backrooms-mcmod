package com.rickr.backrooms.item;

import com.rickr.backrooms.player.SanityService;
import com.rickr.backrooms.registry.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Calm pills: a small orange bottle. Hold right-click for a second to take one and get
 * {@link #SANITY_RESTORED} sanity back.
 */
public class CalmPillsItem extends Item {
    /** How much sanity (out of 100) one dose gives back. */
    public static final float SANITY_RESTORED = 25.0f;

    public CalmPillsItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            // Shake the bottle as you open it.
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.PILLS_RATTLE.get(),
                    SoundSource.PLAYERS, 0.8f, 0.95f + level.getRandom().nextFloat() * 0.1f);
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            SanityService.restore(player, SANITY_RESTORED);
        }
        // consume() leaves the stack alone in creative mode.
        stack.consume(1, entity);
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 20; // 1 second
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.backrooms.calm_pills.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
