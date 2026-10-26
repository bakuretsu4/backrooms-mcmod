package com.rickr.backrooms.client.anomaly.level0;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * A looping sound that an anomaly effect keeps alive by {@link #touch touching} it every tick: the
 * Red Rooms' hum, the Manila Room's piano, a blackout's wandering buzz. It eases towards its
 * {@link #target} volume, can be moved while it plays, and once nobody touches it any more (you
 * walked away, the site dropped out of the nearby list, you left the level) it fades out and
 * stops by itself.
 */
final class Level0LoopSound extends AbstractTickableSoundInstance {
    /** Untouched for this long (ms) = forgotten: fade out and stop. */
    private static final long FORGOTTEN_MS = 400L;

    /** 0..1: how loud we want to be. */
    float target;
    /** Fraction of the way to the target volume per tick. */
    float fade = 0.08f;
    private long touchedMs;
    private long startedMs;

    /**
     * @param anywhere true: no position, heard the same everywhere ("from nowhere"); false: placed in
     *                 the world and fading with distance (sounds.json attenuation_distance)
     */
    Level0LoopSound(SoundEvent sound, SoundSource source, double x, double y, double z, boolean anywhere) {
        super(sound, source, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0f;
        if (anywhere) {
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        } else {
            this.relative = false;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.x = x;
            this.y = y;
            this.z = z;
        }
        touch();
    }

    /** Starts it (call once). */
    Level0LoopSound play() {
        startedMs = System.currentTimeMillis();
        Minecraft.getInstance().getSoundManager().play(this);
        return this;
    }

    /** True once it has stopped, or if the sound engine never really played it (too many sounds): make a new one. */
    boolean dead() {
        return isStopped() || (System.currentTimeMillis() - startedMs > 1000L
                && !Minecraft.getInstance().getSoundManager().isActive(this));
    }

    /** "Still wanted" (call every tick). */
    void touch() {
        touchedMs = System.currentTimeMillis();
    }

    void moveTo(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** The volume we are at right now (0..1, before the sound file's own volume). */
    float currentVolume() {
        return volume;
    }

    @Override
    public boolean canStartSilent() {
        return true; // it starts at volume 0 and fades in
    }

    @Override
    public void tick() {
        boolean forgotten = System.currentTimeMillis() - touchedMs > FORGOTTEN_MS;
        float goal = forgotten ? 0.0f : target;
        volume += (goal - volume) * fade;
        if (forgotten && volume < 0.004f) {
            stop();
        }
    }
}
