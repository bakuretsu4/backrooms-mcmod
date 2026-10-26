package com.rickr.backrooms.client.anomaly.level37;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A looping sound coming from one spot of an anomaly (the face of an opening). Its effect "feeds"
 * it the volume it should have every tick (the effect works out the distance itself, so the sound
 * grows exactly as fast as the glare or the cold); once nobody feeds it any more it fades out and
 * stops by itself.
 *
 * <p>It is placed at the spot, so you hear which way it is, but has no distance fall-off of its own.
 */
final class SiteLoopSound extends AbstractTickableSoundInstance {
    /** Ticks without a feed before it fades away. */
    private static final int STARVE_TICKS = 3;
    /** Volume change per tick at most: no sudden jumps. */
    private static final float FADE_PER_TICK = 0.04f;

    private float target;
    private int unfed;

    SiteLoopSound(SoundEvent sound, Vec3 at) {
        super(sound, SoundSource.AMBIENT, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.relative = false;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.x = at.x;
        this.y = at.y;
        this.z = at.z;
        this.volume = 0.0f;
    }

    /** Call every tick while it should play, with the volume (0..1) and pitch it should have. */
    void feed(float targetVolume, float targetPitch) {
        target = targetVolume;
        pitch = targetPitch;
        unfed = 0;
    }

    @Override
    public boolean canStartSilent() {
        return true; // starts at 0 and fades in
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
