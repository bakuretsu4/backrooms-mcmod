package com.rickr.backrooms.world.anomaly.level2;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModAttachments;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * {@code level_2/hot_zone}: a machinery room at the dead end of a corridor, where the machines are
 * loudest and the heat "can become unbearable, reaching 43 C (110 F) or higher"
 * (https://backrooms-wiki.wikidot.com/archived:level-2; the steam pipes are from
 * https://backrooms.fandom.com/wiki/Level_2). The whirring and the steam are client side.
 *
 * <p>While you are inside: you move sluggishly (Slowness I), you get hungry fast, and the heat
 * wears your breath down even standing still, so running is soon impossible. After 30 seconds the
 * heat starts to hurt: half a heart every 4 seconds. Walking out ends all of it.
 */
final class HotZoneBehavior implements AnomalyBehavior {
    public static final ResourceKey<DamageType> HEATSTROKE = ResourceKey.create(Registries.DAMAGE_TYPE, Backrooms.id("heatstroke"));
    /** Food exhaustion per tick (4.0 = one hunger point): about one point every 13 seconds. */
    private static final float EXHAUSTION_PER_TICK = 0.015f;
    /** Inside this long (ticks) before the heat starts to hurt ... */
    private static final int HURT_AFTER = 600;
    /** ... then this much damage (half a heart) ... */
    private static final float HURT_AMOUNT = 1.0f;
    /** ... every this many ticks. */
    private static final int HURT_EVERY = 80;

    @Override
    public void enter(ServerPlayer player, AnomalySite site, long seed) {
        player.displayClientMessage(Component.translatable("message.backrooms.l2.hot_zone"), true);
    }

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        if (player.isCreative()) {
            return;
        }
        if (ticksInside % 20 == 0) {
            // Ambient (faint particles), but with the icon so you know why your legs are heavy.
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 0, true, false, true));
        }
        player.causeFoodExhaustion(EXHAUSTION_PER_TICK);
        // Breath: the bar normally refills by 1 per tick when you don't run; this takes 1.5 per tick.
        player.getData(ModAttachments.ENERGY).drain(ticksInside % 2 == 0 ? 2 : 1);
        if (ticksInside >= HURT_AFTER && ticksInside % HURT_EVERY == 0) {
            DamageSource heat = new DamageSource(player.level().registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(HEATSTROKE));
            player.hurt(heat, HURT_AMOUNT);
        }
    }
}
