package com.rickr.backrooms.item;

import com.rickr.backrooms.entity.FlareEntity;
import com.rickr.backrooms.registry.LightSupplies;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Right-click: strike the flare and throw it a short way (a bit shorter than a snowball). */
public class FlareItem extends Item {
    /**
     * Throw speed in blocks per tick (a snowball is 1.5) and a small lift. Thrown level it lands
     * about 8 blocks away and rises only a few centimetres, so it stays under a 3-high ceiling.
     */
    private static final double THROW_SPEED = 0.7;
    private static final double THROW_LIFT = 0.05;

    public FlareItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), LightSupplies.FLARE_STRIKE.get(),
                SoundSource.PLAYERS, 0.9f, 0.9f + level.getRandom().nextFloat() * 0.2f);
        if (!level.isClientSide) {
            FlareEntity flare = new FlareEntity(LightSupplies.FLARE_ENTITY.get(), level);
            flare.throwFrom(player, THROW_SPEED, THROW_LIFT);
            level.addFreshEntity(flare);
        }
        // A short pause so one click doesn't throw the whole stack.
        player.getCooldowns().addCooldown(this, 10);
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.backrooms.flare.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
