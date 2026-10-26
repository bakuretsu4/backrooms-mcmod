package com.rickr.backrooms.world.anomaly.level94;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModItems;
import com.rickr.backrooms.world.BackroomsLevel;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The town fountain of Level 94 is "flowing with Almond Water"
 * (https://backrooms-wiki.wikidot.com/level-94). Right-click its stone rim (or the king statue)
 * with whatever you are holding to fill a bottle. It never runs dry, but the flow needs a little
 * while between bottles for each person ({@link #COOLDOWN_TICKS}).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level94Fountain {
    private static final long COOLDOWN_TICKS = 20 * 45;
    /** Game time from which each player may fill the next bottle. */
    private static final Map<UUID, Long> NEXT = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Level level = event.getLevel();
        if (BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_94) {
            return;
        }
        BlockState state = level.getBlockState(event.getPos());
        if (!state.is(ModBlocks.L94_FOUNTAIN_STONE.get()) && !state.is(ModBlocks.L94_STATUE.get())) {
            return;
        }
        // Both sides take over the click (so the hand swings and nothing gets placed on the rim).
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long now = level.getGameTime();
        Long next = NEXT.get(player.getUUID());
        if (next != null && now < next) {
            player.displayClientMessage(Component.translatable("message.backrooms.l94.fountain_wait"), true);
            return;
        }
        NEXT.put(player.getUUID(), now + COOLDOWN_TICKS);
        ItemStack bottle = new ItemStack(ModItems.ALMOND_WATER.get());
        if (!player.addItem(bottle)) {
            player.drop(bottle, false);
        }
        level.playSound(null, event.getPos(), SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.9f, 0.9f);
        player.displayClientMessage(Component.translatable("message.backrooms.l94.fountain"), true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        NEXT.remove(event.getEntity().getUUID());
    }

    private Level94Fountain() {}
}
