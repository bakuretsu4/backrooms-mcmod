package com.rickr.backrooms.item;

import com.rickr.backrooms.entity.GlowstickEntity;
import com.rickr.backrooms.registry.LightSupplies;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Right-click: crack the glowstick and toss it underhand just in front of you. A glowstick that
 * was picked up again remembers how much glow it has left (stored on the item as "Life"), and
 * isn't cracked a second time.
 */
public class GlowstickItem extends Item {
    private static final String LIFE = "Life";
    /** An underhand toss: slow, and a little upward. */
    private static final double TOSS_SPEED = 0.35;
    private static final double TOSS_LIFT = 0.12;

    public GlowstickItem(Properties properties) {
        super(properties);
    }

    /** A cracked glowstick (picked up from the floor) with this many ticks of glow left. */
    public static ItemStack withLife(int ticks) {
        ItemStack stack = new ItemStack(LightSupplies.GLOWSTICK.get());
        CompoundTag tag = new CompoundTag();
        tag.putInt(LIFE, ticks);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** Ticks of glow left on a cracked glowstick, or -1 for a fresh one. */
    private static int life(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return -1;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains(LIFE) ? tag.getInt(LIFE) : -1;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int life = life(stack);
        if (life < 0) {
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), LightSupplies.GLOWSTICK_CRACK.get(),
                    SoundSource.PLAYERS, 0.8f, 0.95f + level.getRandom().nextFloat() * 0.1f);
        }
        if (!level.isClientSide) {
            GlowstickEntity glowstick = new GlowstickEntity(LightSupplies.GLOWSTICK_ENTITY.get(), level);
            glowstick.throwFrom(player, TOSS_SPEED, TOSS_LIFT);
            if (life > 0) {
                glowstick.setLife(life);
            }
            level.addFreshEntity(glowstick);
        }
        player.getCooldowns().addCooldown(this, 8);
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int life = life(stack);
        if (life > 0) {
            // Already cracked: say how much glow is left instead (still one line).
            int minutes = Math.max(1, Math.round(life / 1200.0f));
            tooltip.add(Component.translatable("item.backrooms.glowstick.tooltip_cracked", minutes).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.backrooms.glowstick.tooltip").withStyle(ChatFormatting.GRAY));
        }
    }
}
