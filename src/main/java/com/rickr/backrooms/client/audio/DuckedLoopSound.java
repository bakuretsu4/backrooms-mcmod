package com.rickr.backrooms.client.audio;

import java.util.function.DoubleSupplier;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

/**
 * A level's ambient loop played at its own volume times a "duck" factor we control.
 *
 * <p>Minecraft plays a level's background loop from the biome ("ambient_sound") and offers no
 * volume knob for it. So when that loop is about to start we wrap it in one of these (see
 * {@code Level2Sounds} and {@link Level0Soundscape}): it does everything the original does (the
 * biome code's own fade in and out included), but its volume is multiplied by our factor, which
 * eases toward {@code target} by {@code ease} of the way every tick.
 */
public final class DuckedLoopSound implements TickableSoundInstance {
    private final TickableSoundInstance inner;
    private final DoubleSupplier target;
    private final float ease;
    private float duck;

    /**
     * @param target the factor wanted right now (0..1), asked every tick
     * @param ease   share of the way to the target per tick (1 = follow it exactly)
     * @param start  the factor to start at
     */
    public DuckedLoopSound(TickableSoundInstance inner, DoubleSupplier target, float ease, float start) {
        this.inner = inner;
        this.target = target;
        this.ease = ease;
        this.duck = start;
    }

    @Override
    public void tick() {
        inner.tick(); // the biome code's own fade in/out
        duck += ((float) target.getAsDouble() - duck) * ease;
    }

    @Override
    public boolean isStopped() {
        return inner.isStopped();
    }

    @Override
    public float getVolume() {
        return inner.getVolume() * duck;
    }

    @Override
    public boolean canStartSilent() {
        return true; // must start even while ducked to nothing, or it would never come back
    }

    @Override
    public ResourceLocation getLocation() {
        return inner.getLocation();
    }

    @Override
    @Nullable
    public WeighedSoundEvents resolve(SoundManager manager) {
        return inner.resolve(manager);
    }

    @Override
    public Sound getSound() {
        return inner.getSound();
    }

    @Override
    public SoundSource getSource() {
        return inner.getSource();
    }

    @Override
    public boolean isLooping() {
        return inner.isLooping();
    }

    @Override
    public boolean isRelative() {
        return inner.isRelative();
    }

    @Override
    public int getDelay() {
        return inner.getDelay();
    }

    @Override
    public float getPitch() {
        return inner.getPitch();
    }

    @Override
    public double getX() {
        return inner.getX();
    }

    @Override
    public double getY() {
        return inner.getY();
    }

    @Override
    public double getZ() {
        return inner.getZ();
    }

    @Override
    public Attenuation getAttenuation() {
        return inner.getAttenuation();
    }
}
