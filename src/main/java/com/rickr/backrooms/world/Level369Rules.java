package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.Level369Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server rules of Level 369 ("Comely"), in and out:
 * <ul>
 *   <li><b>In:</b> through the black exit mirror of Level 228; you fall out of a skylight
 *       ({@code Level369Layout#preferredArrival}) into the flowers. Nothing up here can hurt you
 *       by falling: every landing is soft.</li>
 *   <li><b>Out:</b> walk into the curtain of flowers hanging in an arch of vines
 *       ({@code l369_veil}) and you come out in Level 490 ({@code BackroomsLevel.next}).</li>
 *   <li><b>The light:</b> the warm light drifting through the rooms ({@link Level369Light}) heals
 *       you while you stand in it (and gives back your sanity, see {@code SanityService}).</li>
 * </ul>
 * Level 369 is never left by falling (its transferBelowY is unreachable). What it does to your
 * memory is in {@link Level369Forgetting}.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level369Rules {
    /** In the drifting light you heal half a heart this often (ticks) ... */
    private static final int LIGHT_HEAL_TICKS = 60;
    /** ... where it is at least this strong. */
    private static final float LIGHT_HEAL_FROM = 0.35f;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()
                || BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_369) {
            return;
        }
        BlockPos feet = player.blockPosition();
        if (player.level().getBlockState(feet).is(Level369Blocks.VEIL.get())
                || player.level().getBlockState(feet.above()).is(Level369Blocks.VEIL.get())) {
            Travel.useExit(player, BackroomsLevel.LEVEL_369);
            return;
        }
        // The drifting light is kind: standing in it you slowly heal (half a heart every 3 seconds
        // in the heart of it). Its sanity is in SanityService.
        if (player.tickCount % LIGHT_HEAL_TICKS == 0 && player.getHealth() < player.getMaxHealth()) {
            long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), BackroomsLevel.LEVEL_369);
            if (Level369Light.strength(seed, player.getX(), player.getZ(), player.level().getGameTime()) > LIGHT_HEAL_FROM) {
                player.heal(1.0f);
            }
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && BackroomsLevel.fromDimension(player.level().dimension()) == BackroomsLevel.LEVEL_369) {
            event.setCanceled(true); // into the lawn and the flowers: a soft landing
        }
    }

    private Level369Rules() {}
}
