package com.rickr.backrooms.world.anomaly.level2;

import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * {@code level_2/brownout}: a stretch of tunnels where the Blackout never quite ended
 * (https://backrooms-wiki.wikidot.com/level-2, "The Blackout"/"brownout": machinery stopped, pipes
 * burst and leaked, "a thick, pungent smell similar to that of rotting meat", most fluorescents
 * burst, sparse violet emergency lights).
 *
 * <p>The dead lamps, emergency lights and black ooze are blocks (see Level2AnomalyPlan); the ooze
 * slows you by itself. This behaviour adds the stench: standing in or right next to the ooze, every
 * couple of seconds there is a chance your stomach turns. That is a short heaviness in the legs
 * (Slowness) and a little hunger, NOT vanilla Nausea: Nausea warps the whole screen like a nether
 * portal, and Level 2 keeps screen distortion for real danger only. The silent machinery is client
 * side (the level's machine drone fades out while you're here).
 */
final class BrownoutBehavior implements AnomalyBehavior {
    /** How often (ticks) the stench gets a chance at you ... */
    private static final int STENCH_EVERY = 40;
    /** ... the chance ... */
    private static final float STENCH_CHANCE = 0.35f;
    /** ... and how long the queasy heaviness lasts (ticks). */
    private static final int QUEASY_TICKS = 100;

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        if (ticksInside % STENCH_EVERY != STENCH_EVERY / 2 || player.isCreative()
                || player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || !nextToOoze(player.serverLevel(), player.blockPosition())) {
            return;
        }
        if (player.getRandom().nextFloat() < STENCH_CHANCE) {
            // Ambient (faint particles), icon shown: you can tell why your legs are heavy.
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, QUEASY_TICKS, 0, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, QUEASY_TICKS, 0, true, false, true));
        }
    }

    /** True if the black ooze lies at your feet or in any of the 8 columns around you. */
    private static boolean nextToOoze(ServerLevel level, BlockPos feet) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = feet.offset(dx, 0, dz);
                if (level.isLoaded(pos) && level.getBlockState(pos).is(ModBlocks.L2_BLACK_OOZE.get())) {
                    return true;
                }
            }
        }
        return false;
    }
}
