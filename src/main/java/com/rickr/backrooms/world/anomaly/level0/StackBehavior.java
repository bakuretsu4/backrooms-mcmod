package com.rickr.backrooms.world.anomaly.level0;

import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level0Stack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * The stack (world/layout/Level0Stack): fall into the void, or off the lowest real floor, and the
 * dark closes in as you drop past the floors; then you land somewhere else on Level 0, hurt, the
 * same as falling into a pit (PitfallsBehavior#land). Caught well above the height where falling
 * would take you on to Level 1.
 */
public final class StackBehavior implements AnomalyBehavior {
    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        if (player.getAbilities().flying || player.isCreative()) {
            return;
        }
        double y = player.getY();
        if (y < Level0Stack.LOWEST_REAL + 4 && !player.onGround() && player.getDeltaMovement().y < -0.3
                && Level0Stack.overVoid(site, player.getBlockX(), player.getBlockZ()) && !player.hasEffect(MobEffects.BLINDNESS)) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
        }
        if (y < Level0Stack.FALL_BELOW) {
            PitfallsBehavior.land(player, seed);
        }
    }
}
