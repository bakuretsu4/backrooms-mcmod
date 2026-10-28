package com.rickr.backrooms.world.anomaly.level0;

import com.rickr.backrooms.registry.Level0Blocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.Travel;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.layout.Level0Layout;
import com.rickr.backrooms.world.layout.Level0Pitfalls;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * The pits (Level 0's "Hole Variation", https://backrooms-wiki.wikidot.com/level-0, as in Kane
 * Pixels' "Pitfalls", https://kane-pixels-backrooms.fandom.com/wiki/The_Backrooms, where Marvin
 * falls into a shaft and lands in another part of the Backrooms). Floor plan: {@link Level0Pitfalls}.
 *
 * <p>Fall into a shaft and the light is gone after a couple of blocks. The shaft is only nine
 * blocks deep (any deeper and Level 0 would send you on to Level 1), so twice on the way down the
 * server quietly lifts you back up six blocks ({@link Teleports#shift} keeps your speed): in the
 * dark it simply feels like a very long fall, about twenty blocks. Then you land, hurt (never
 * killed) and half-blind, in a dark spot of Level 0 150-400 blocks away, having come down through
 * its ceiling: the tile above you is missing, a black hole you could never climb back into. The
 * hole stays, so you can find the spot again (and wonder how a pit leads to a ceiling).
 * Players flying in creative are left alone.
 */
public final class PitfallsBehavior implements AnomalyBehavior {
    /** Below this feet height the shaft "goes on": lift the player or land them. */
    private static final double DEEP = Level0Pitfalls.SHAFT_BOTTOM + 3.0;
    private static final double LIFT = 6.0;
    private static final int LIFTS = 2;
    private static final float LANDING_DAMAGE = 6.0f;
    private static final int MIN_DISTANCE = 150;
    private static final int MORE_DISTANCE = 250;

    /** How many times each falling player has been lifted already. */
    private final Map<UUID, Integer> lifts = new HashMap<>();

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        int floor = BackroomsLevel.LEVEL_0.layout().floorY();
        if (player.getY() >= floor || player.getAbilities().flying
                || !Level0Pitfalls.isShaft(site, player.getBlockX(), player.getBlockZ())) {
            return;
        }
        if (player.getY() < floor - 1.0 && !player.hasEffect(MobEffects.BLINDNESS)) {
            // "Light only reaches a few feet down."
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
        }
        if (player.getY() >= DEEP && !player.onGround()) {
            return;
        }
        int done = lifts.getOrDefault(player.getUUID(), 0);
        if (done < LIFTS && !player.onGround()) {
            Teleports.shift(player, 0.0, LIFT, 0.0);
            lifts.put(player.getUUID(), done + 1);
            return;
        }
        lifts.remove(player.getUUID());
        land(player, seed);
    }

    @Override
    public void leave(ServerPlayer player, AnomalySite site, long seed) {
        lifts.remove(player.getUUID());
    }

    /** The end of the fall: somewhere dark and far away, a hard landing. */
    private static void land(ServerPlayer player, long seed) {
        ServerLevel level = player.serverLevel();
        Level0Layout layout = (Level0Layout) BackroomsLevel.LEVEL_0.layout();
        BlockPos spot = landingSpot(level, layout, seed, player);
        double feet = layout.feetY(seed, spot.getX(), spot.getZ());
        player.setDeltaMovement(Vec3.ZERO);
        Teleports.to(player, spot.getX() + 0.5, feet, spot.getZ() + 0.5, player.getYRot(), player.getXRot());
        float damage = Math.min(LANDING_DAMAGE, player.getHealth() - 1.0f);
        if (damage > 0.0f) {
            player.hurt(player.damageSources().fall(), damage);
        }
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1, false, false));
        player.playNotifySound(ModSounds.INTRO_IMPACT.get(), SoundSource.PLAYERS, 0.8f, 0.9f);
        // You came down through the ceiling: the tile right above you is gone.
        BlockPos above = new BlockPos(spot.getX(), layout.ceilingY(), spot.getZ());
        if (level.getBlockState(above).is(ModBlocks.CEILING_TILE.get())) {
            level.setBlock(above, Level0Blocks.CEILING_HOLE.get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * A safe spot 150-400 blocks away in a random direction, preferably in the unlit half of the
     * level and never inside another anomaly's structure (safe arrivals exclude those).
     */
    private static BlockPos landingSpot(ServerLevel level, Level0Layout layout, long seed, ServerPlayer player) {
        int px = player.getBlockX();
        int pz = player.getBlockZ();
        long h = Hash.of(seed, px, pz, (int) level.getGameTime());
        for (int attempt = 0; attempt < 12; attempt++) {
            long a = Hash.mix(h + attempt);
            double angle = Hash.unit(a) * Math.PI * 2.0;
            int distance = MIN_DISTANCE + Hash.range(Hash.mix(a ^ 1), MORE_DISTANCE + 1);
            int tx = px + (int) Math.round(Math.cos(angle) * distance);
            int tz = pz + (int) Math.round(Math.sin(angle) * distance);
            BlockPos spot = Travel.searchWalkable(layout, seed, tx, tz, 24);
            if (spot == null || (attempt < 8 && !layout.isUnlitZone(seed, spot.getX(), spot.getZ()))) {
                continue;
            }
            return spot;
        }
        return Travel.findSafeSpot(level, BackroomsLevel.LEVEL_0, px + MIN_DISTANCE + 50, pz);
    }
}
