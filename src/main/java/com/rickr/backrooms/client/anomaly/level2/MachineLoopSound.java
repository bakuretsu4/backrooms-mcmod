package com.rickr.backrooms.client.anomaly.level2;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * A loop placed on something in the corridor (a wall fan's drone, a leaking valve's hiss),
 * started by Level2Emitters. It fades in, and fades out and stops by itself once whoever started
 * it stops keeping it alive (you walked away).
 */
final class MachineLoopSound extends AbstractTickableSoundInstance {
    /** Ticks without a keep-alive before it fades out. */
    private static final int LINGER = 10;

    private final float maxVolume;
    private int sinceKept;
    private float fade;

    /** A loop at (x, y, z), up to {@code maxVolume}. */
    MachineLoopSound(SoundEvent sound, double x, double y, double z, long salt, float maxVolume) {
        super(sound, SoundSource.BLOCKS, RandomSource.create(salt));
        this.maxVolume = maxVolume;
        this.x = x;
        this.y = y;
        this.z = z;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.relative = false;
        this.volume = 0.0f;
        // Each starts at a slightly different pitch, so two of the same loop never phase together.
        this.pitch = 0.96f + (salt & 0xFF) / 255.0f * 0.08f;
    }

    /** Called every tick by whoever started it while you're near. */
    void keepAlive() {
        sinceKept = 0;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        sinceKept++;
        float goal = sinceKept > LINGER ? 0.0f : 1.0f;
        fade += (goal - fade) * 0.1f;
        volume = maxVolume * fade;
        if (goal == 0.0f && fade < 0.01f) {
            stop();
        }
    }
}
