package com.rickr.backrooms.player;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.Flooding;
import com.rickr.backrooms.block.ShallowWaterBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The sounds of the pool water in the Poolrooms and Level 37: wading steps through shallow water,
 * and a soft splash instead of vanilla's loud one when something drops into a pool.
 * Common code (runs on both sides, like {@link PlayerGameplay}); touches no client classes.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class PoolWaterSounds {
    /** Vanilla splashes are loud; ours is softer still on top of being a gentler sound. */
    private static final float SPLASH_VOLUME = 0.7f;

    /**
     * Height of the water surface the player is wading in, or NaN if they aren't wading. Shallow
     * water is Level 37's {@code shallow_water} (or a flooded block), or real water you stand in
     * without swimming (the Poolrooms' knee-deep shallows). Real water only counts with a floor
     * right under it: at the edge of a deep pool you are not wading, you are about to swim.
     */
    private static double waterLine(Player player) {
        Level level = player.level();
        BlockPos feet = player.blockPosition();
        BlockState state = level.getBlockState(feet);
        if (state.is(ModBlocks.SHALLOW_WATER.get()) || Flooding.isFlooded(state)) {
            return feet.getY() + ShallowWaterBlock.SURFACE;
        }
        FluidState fluid = level.getFluidState(feet);
        if (!fluid.is(FluidTags.WATER) || player.isSwimming() || player.isUnderWater()
                || level.getFluidState(feet.below()).is(FluidTags.WATER)) {
            return Double.NaN;
        }
        return feet.getY() + fluid.getOwnHeight();
    }

    /** True while the player walks or runs through shallow water (not swimming). */
    public static boolean isWading(Player player) {
        return !Double.isNaN(waterLine(player));
    }

    /**
     * Wading in the Poolrooms or Level 37 (not in a river in the real world). Used by
     * {@code mixin/client/LocalPlayerMixin} so you can run through the knee-deep shallows.
     */
    public static boolean wadingInPoolLevel(Player player) {
        return hasPoolWater(player.level()) && isWading(player);
    }

    /**
     * The wading footstep when the player's feet are in shallow water, else null. On the client
     * it also kicks up a few droplets around your feet.
     */
    @Nullable
    public static SoundEvent wadeStep(Player player, boolean running) {
        double waterLine = waterLine(player);
        if (Double.isNaN(waterLine)) {
            return null;
        }
        if (player.level().isClientSide) {
            splashDroplets(player, waterLine, running ? 6 : 3);
        }
        return running ? ModSounds.STEP_WATER_WADE_RUN.get() : ModSounds.STEP_WATER_WADE_WALK.get();
    }

    /** A few drops flicked up where the foot leaves the water (vanilla's splash particle). */
    private static void splashDroplets(Player player, double waterLine, int count) {
        Level level = player.level();
        for (int i = 0; i < count; i++) {
            double x = player.getX() + (player.getRandom().nextDouble() - 0.5) * 0.6;
            double z = player.getZ() + (player.getRandom().nextDouble() - 0.5) * 0.6;
            level.addParticle(ParticleTypes.SPLASH, x, waterLine, z, 0.0, 0.05, 0.0);
        }
    }

    /**
     * Swaps the vanilla splash (falling or jumping into water) for our soft one in the two
     * water levels, and drops vanilla's swimming stroke for players who are only wading.
     * Fires on both sides, like the footstep filter in {@link PlayerGameplay}.
     */
    @SubscribeEvent
    public static void onPlaySound(PlayLevelSoundEvent.AtPosition event) {
        Holder<SoundEvent> sound = event.getSound();
        if (sound == null || !hasPoolWater(event.getLevel())) {
            return;
        }
        if (sound.value() == SoundEvents.PLAYER_SWIM && wadingPlayerAt(event.getLevel(), event.getPosition())) {
            event.setCanceled(true); // another player's wading: their steps already make the sound
            return;
        }
        if (isSplash(sound.value())) {
            event.setSound(ModSounds.WATER_SPLASH);
            event.setNewVolume(Math.min(1.0f, event.getOriginalVolume() * 2.0f) * SPLASH_VOLUME);
        }
    }

    /**
     * Vanilla plays its swimming stroke sound for every step you take in water, even in water
     * that only reaches your knees. While wading, our wading steps are the sound instead.
     * (Your own sounds go through this event; other players' through the one above.)
     */
    @SubscribeEvent
    public static void onPlaySoundAtEntity(PlayLevelSoundEvent.AtEntity event) {
        Holder<SoundEvent> sound = event.getSound();
        if (sound != null && sound.value() == SoundEvents.PLAYER_SWIM && event.getEntity() instanceof Player player
                && hasPoolWater(event.getLevel()) && isWading(player)) {
            event.setCanceled(true);
        }
    }

    /** Is a wading player standing exactly where this sound plays? (Players play sounds at their feet.) */
    private static boolean wadingPlayerAt(Level level, Vec3 pos) {
        for (Player player : level.players()) {
            if (player.position().distanceToSqr(pos) < 1.0e-4 && isWading(player)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSplash(SoundEvent sound) {
        return sound == SoundEvents.PLAYER_SPLASH || sound == SoundEvents.PLAYER_SPLASH_HIGH_SPEED
                || sound == SoundEvents.GENERIC_SPLASH;
    }

    /** The levels with the custom pool water (see client/water/WaterStyle). */
    private static boolean hasPoolWater(Level level) {
        BackroomsLevel floor = BackroomsLevel.fromDimension(level.dimension());
        return floor == BackroomsLevel.POOLROOMS || floor == BackroomsLevel.LEVEL_37;
    }

    private PoolWaterSounds() {}
}
