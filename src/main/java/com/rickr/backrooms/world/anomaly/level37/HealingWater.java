package com.rickr.backrooms.world.anomaly.level37;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.Flooding;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Level 37's water heals. Canon (both wikis' Level 37 page,
 * https://backrooms-wiki.wikidot.com/level-37 and https://backrooms.fandom.com/wiki/Level_37):
 * the water holds Epsom salt and unknown compounds that make its relaxing, pain-easing effect
 * anomalously strong; "You feel a calming sensation."
 *
 * <ul>
 *   <li><b>Sanity</b> ({@link #sanityPerSecond}, asked by {@code SanityService} once a second):
 *       only a small trickle back on dry tile, more while you wade in the ankle-deep water, and a
 *       lot while you are in the deep water.</li>
 *   <li><b>Regeneration:</b> standing or swimming in the water gives a slow Regeneration (about
 *       one heart every five seconds). Dry tile gives nothing.</li>
 *   <li>The first time you go into deep water on a visit you read the canon line once.</li>
 * </ul>
 * Not a place on the map, so it isn't an anomaly site: it is the whole level's water.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class HealingWater {
    /** On dry tile: the calm of the place, nothing more. */
    private static final float DRY_RECOVERY = 0.1f;
    /** Wading in the ankle-deep water of the flooded floors. */
    private static final float WADING_RECOVERY = 0.4f;
    /** In the deep water (pools, the pit): soaking in it. */
    private static final float SOAKING_RECOVERY = 1.5f;
    /**
     * Regeneration is topped up this often (ticks), each time lasting a little longer. Level I heals
     * whenever its remaining time passes a multiple of 50 ticks, so this heals once per top-up.
     */
    private static final int REGEN_EVERY = 50;

    /** Players who have read "You feel a calming sensation." on this visit. */
    private static final Set<UUID> CALMED = new HashSet<>();

    /** Sanity per second in Level 37 (positive: it only ever comes back here). */
    public static float sanityPerSecond(ServerPlayer player) {
        if (soaking(player)) {
            return SOAKING_RECOVERY;
        }
        return wading(player) ? WADING_RECOVERY : DRY_RECOVERY;
    }

    /** In vanilla water: a deep pool or the pit. */
    private static boolean soaking(Player player) {
        return player.isInWater();
    }

    /** Feet in the ankle-deep water of a flooded floor (or a pillar foot / tread standing in it). */
    private static boolean wading(Player player) {
        BlockState feet = player.level().getBlockState(player.blockPosition());
        return feet.is(ModBlocks.SHALLOW_WATER.get()) || Flooding.isFlooded(feet);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        if (BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_37) {
            CALMED.remove(player.getUUID());
            return;
        }
        if (player.tickCount % REGEN_EVERY != 0) {
            return;
        }
        boolean soaking = soaking(player);
        if (!soaking && !wading(player)) {
            return;
        }
        // Ambient (like a beacon's), no particles: the water is just doing what it does.
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_EVERY + 10, 0, true, false, true));
        if (soaking && CALMED.add(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.backrooms.level37.calm")
                    .withStyle(ChatFormatting.ITALIC), true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CALMED.remove(event.getEntity().getUUID());
    }

    private HealingWater() {}
}
