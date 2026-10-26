package com.rickr.backrooms.client.anomaly.level4;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * A looping sound "in your head" (the same everywhere, like a level's ambience) that its owner feeds
 * a target volume every tick while it is wanted. It eases toward that volume, and once nobody has
 * fed it for a few ticks (you walked out, left the level) it fades out and stops by itself.
 */
final class Level4LoopSound extends AbstractTickableSoundInstance {
    private static final int STARVE_TICKS = 3;
    private static final float FADE_PER_TICK = 0.02f;

    private float target;
    private int unfed;

    Level4LoopSound(SoundEvent sound) {
        super(sound, SoundSource.AMBIENT, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = 0.0f;
    }

    /** Call every tick while the sound should play, with the volume it should have. */
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
        volume += Math.max(-FADE_PER_TICK, Math.min(FADE_PER_TICK, target - volume));
        if (unfed > STARVE_TICKS && volume <= 0.001f) {
            stop();
        }
    }
}
