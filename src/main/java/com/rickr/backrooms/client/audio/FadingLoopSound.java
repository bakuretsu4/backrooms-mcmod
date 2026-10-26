package com.rickr.backrooms.client.audio;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * A looping sound that its owner "feeds" a target volume every tick while it is wanted. It eases
 * toward that volume, and once nobody has fed it for a few ticks (you walked away, left the level)
 * it fades out and stops by itself, so no owner has to remember to stop it.
 *
 * <p>Either placed in the world (it fades with distance as usual) or {@code relative}: then it is
 * "in your head", the same everywhere, like the level's ambience.
 */
public final class FadingLoopSound extends AbstractTickableSoundInstance {
    /** Ticks without a feed before the sound fades away. */
    private static final int STARVE_TICKS = 3;

    private final float fadePerTick;
    private float target;
    private int unfed;

    public FadingLoopSound(SoundEvent sound, boolean relative, double x, double y, double z, float fadePerTick) {
        super(sound, SoundSource.AMBIENT, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.relative = relative;
        this.attenuation = relative ? SoundInstance.Attenuation.NONE : SoundInstance.Attenuation.LINEAR;
        this.x = x;
        this.y = y;
        this.z = z;
        this.volume = 0.0f;
        this.fadePerTick = fadePerTick;
    }

    /** Call every tick while the sound should play, with the volume it should have. */
    public void feed(float targetVolume) {
        target = targetVolume;
        unfed = 0;
    }

    @Override
    public boolean canStartSilent() {
        return true; // it starts at 0 and fades in
    }

    @Override
    public void tick() {
        if (++unfed > STARVE_TICKS) {
            target = 0.0f;
        }
        float step = Math.max(-fadePerTick, Math.min(fadePerTick, target - volume));
        volume += step;
        if (unfed > STARVE_TICKS && volume <= 0.001f) {
            stop();
        }
    }
}
