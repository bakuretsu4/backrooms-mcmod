package com.rickr.backrooms.item;

import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.RealWorldEntries;
import com.rickr.backrooms.world.Travel;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * An old camcorder for the real world. Right-click to start or stop recording: while it records
 * (and you hold it) you see through it, tape look and REC overlay and all. Film somewhere dark,
 * enclosed and empty for long enough and the tape starts to break up, and then so does reality
 * (see world/RealWorldEntries). In the Backrooms you already carry one that never stops.
 */
public class CamcorderItem extends Item {
    public CamcorderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (Travel.inBackrooms(level)) {
            return InteractionResultHolder.pass(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            boolean recording = RealWorldEntries.toggleRecording(serverPlayer);
            serverPlayer.playNotifySound(ModSounds.REC_BEEP.get(), SoundSource.MASTER, 0.8f, recording ? 1.2f : 0.9f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.backrooms.camcorder.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.backrooms.camcorder.tooltip2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
