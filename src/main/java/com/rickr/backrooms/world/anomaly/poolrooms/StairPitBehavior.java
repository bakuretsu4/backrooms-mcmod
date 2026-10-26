package com.rickr.backrooms.world.anomaly.poolrooms;

import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.PoolroomsAnomalyPlan;
import com.rickr.backrooms.world.layout.PoolroomsLayout;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectUtil;

/**
 * A stair pit (see PoolroomsAnomalyPlan): a room that is one deep, dark flooded shaft, with a
 * staircase running straight down into it.
 *
 * <p>Canon: both wikis' Level 37 (https://backrooms-wiki.wikidot.com/level-37,
 * https://backrooms.fandom.com/wiki/Level_37): "staircases descend directly into deep pits of
 * water"; these are dark with few or no ledges, and you can drown in them without equipment;
 * Fandom adds "unpredictable currents".
 *
 * <p>Down in the shaft:
 * <ul>
 *   <li>your air runs out twice as fast once your head is a few blocks under (the deeper water
 *       presses on you), so a long dive is a real drowning risk;</li>
 *   <li>every few seconds a current grabs you for a second or two and drags you sideways, up or
 *       down, with a rush of water.</li>
 * </ul>
 * The stairs are always there to climb back up, and the surface is level with the deck all
 * around, so you can always get out. The client darkens the picture the deeper you go
 * ({@code StairPitEffect}).
 */
final class StairPitBehavior implements AnomalyBehavior {
    /** Below this eye height (2+ blocks under the surface) the water takes your air twice as fast. */
    private static final double DEEP_EYES_Y = PoolroomsLayout.SURFACE_Y - 2.0;
    /** Quiet gap between two currents (ticks): 3-8 s. */
    private static final int MIN_GAP = 60;
    private static final int GAP_SPREAD = 100;
    /** A current pushes for 0.75-1.75 s ... */
    private static final int MIN_PUSH = 15;
    private static final int PUSH_SPREAD = 20;
    /** ... adding this much speed every tick (blocks/tick): enough to carry you a few blocks. */
    private static final double MIN_FORCE = 0.03;
    private static final double FORCE_SPREAD = 0.025;

    /** One player's current: how long it still pushes, which way, and the gap before the next. */
    private static final class Current {
        int pushTicks;
        int gapTicks;
        double x;
        double y;
        double z;
    }

    private static final Map<UUID, Current> CURRENTS = new HashMap<>();

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        if (!player.isInWater() || player.getY() > PoolroomsLayout.SURFACE_Y - 0.5
                || !PoolroomsAnomalyPlan.inShaft(site, player.getX(), player.getZ())) {
            return; // on the deck, or only just stepping onto the first stair
        }
        drainAir(player);
        current(player);
    }

    @Override
    public void leave(ServerPlayer player, AnomalySite site, long seed) {
        CURRENTS.remove(player.getUUID());
    }

    /**
     * Vanilla already takes one point of air per tick under water; we take one more. Only while
     * there is air left: once it is gone vanilla's own drowning takes over, exactly as usual.
     */
    private static void drainAir(ServerPlayer player) {
        if (player.getEyeY() >= DEEP_EYES_Y || !player.isUnderWater() || player.getAbilities().invulnerable
                || player.canBreatheUnderwater() || MobEffectUtil.hasWaterBreathing(player)) {
            return;
        }
        int air = player.getAirSupply();
        if (air > 0) {
            player.setAirSupply(air - 1);
        }
    }

    /** The unpredictable currents: a push now and then, in a random direction. */
    private static void current(ServerPlayer player) {
        RandomSource random = player.getRandom();
        Current current = CURRENTS.computeIfAbsent(player.getUUID(), id -> {
            Current fresh = new Current();
            fresh.gapTicks = 20 + random.nextInt(40); // the first one comes soon after you go in
            return fresh;
        });
        if (current.pushTicks > 0) {
            current.pushTicks--;
            player.setDeltaMovement(player.getDeltaMovement().add(current.x, current.y, current.z));
            // The client moves the player; this makes the server send it the new speed (like the drain).
            player.hurtMarked = true;
            return;
        }
        if (--current.gapTicks > 0) {
            return;
        }
        float angle = random.nextFloat() * Mth.TWO_PI;
        double force = MIN_FORCE + random.nextDouble() * FORCE_SPREAD;
        current.x = Mth.cos(angle) * force;
        current.z = Mth.sin(angle) * force;
        // Mostly sideways; sometimes it pulls you down a little, sometimes it lifts you.
        current.y = (random.nextDouble() - 0.6) * force;
        current.pushTicks = MIN_PUSH + random.nextInt(PUSH_SPREAD);
        current.gapTicks = MIN_GAP + random.nextInt(GAP_SPREAD);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.POOL_CURRENT.get(), SoundSource.AMBIENT, 0.8f, 0.85f + random.nextFloat() * 0.3f);
    }
}
