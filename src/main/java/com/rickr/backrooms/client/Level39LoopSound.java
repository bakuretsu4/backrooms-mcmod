package com.rickr.backrooms.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * One of Level 39's ambient loops ({@link Level39Audio}): plays "in your head" (the same
 * everywhere, like a biome's ambience) and eases toward whatever volume its owner feeds it each
 * tick. When nobody feeds it any more (you left the level) it fades out and stops by itself.
 *
 * <p>Several of these run at once and cross-fade: the town and tunnel loops swap as you drop
 * down a hole, and the rain loop swells in and out with the indoor storm.
 */
final class Level39LoopSound extends AbstractTickableSoundInstance {
    /** Ticks without a feed before the sound fades away. */
    private static final int STARVE_TICKS = 3;

    private final float fadePerTick;
    private float target;
    private int unfed;

    Level39LoopSound(SoundEvent sound, float fadePerTick) {
        super(sound, SoundSource.AMBIENT, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = 0.0f;
        this.fadePerTick = fadePerTick;
    }

    /** Call every tick while the sound should play, with the volume it should have now. */
    void feed(float targetVolume) {
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
