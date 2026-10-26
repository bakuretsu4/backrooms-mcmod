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
     * The wading footstep when the player's feet are in shallow water, else null. Shallow water
     * is Level 37's {@code shallow_water} (or a flooded block), or real water you stand in
     * without swimming (the Poolrooms' knee-deep shallows). On the client it also kicks up a few
     * droplets around your feet.
     */
    @Nullable
    public static SoundEvent wadeStep(Player player, boolean running) {
        Level level = player.level();
        BlockPos feet = player.blockPosition();
        BlockState state = level.getBlockState(feet);
        double waterLine;
        if (state.is(ModBlocks.SHALLOW_WATER.get()) || Flooding.isFlooded(state)) {
            waterLine = feet.getY() + ShallowWaterBlock.SURFACE;
        } else {
            FluidState fluid = level.getFluidState(feet);
            if (!fluid.is(FluidTags.WATER) || player.isSwimming() || player.isUnderWater()) {
                return null;
            }
            waterLine = feet.getY() + fluid.getOwnHeight();
        }
        if (level.isClientSide) {
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
     * water levels. Fires on both sides, like the footstep filter in {@link PlayerGameplay}.
     */
    @SubscribeEvent
    public static void onPlaySound(PlayLevelSoundEvent.AtPosition event) {
        Holder<SoundEvent> sound = event.getSound();
        if (sound == null || !isSplash(sound.value()) || !hasPoolWater(event.getLevel())) {
            return;
        }
        event.setSound(ModSounds.WATER_SPLASH);
        event.setNewVolume(Math.min(1.0f, event.getOriginalVolume() * 2.0f) * SPLASH_VOLUME);
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
