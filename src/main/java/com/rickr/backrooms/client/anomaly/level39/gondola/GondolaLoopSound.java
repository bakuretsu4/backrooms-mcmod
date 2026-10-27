package com.rickr.backrooms.client.anomaly.level39.gondola;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * A looping lift sound (wind in the cable, the cable creaking) that its owner "feeds" every tick
 * with the volume, pitch and (unless it is in your head) position it should have. It eases toward
 * them, and once nobody feeds it for a few ticks it fades out and stops by itself, so no owner has
 * to remember to stop it when you walk away, change level or arrive in Level 39.
 */
final class GondolaLoopSound extends AbstractTickableSoundInstance {
    /** Ticks without a feed before it fades away. */
    private static final int STARVE_TICKS = 3;
    /** Volume change per tick at most: no sudden jumps. */
    private static final float FADE_PER_TICK = 0.05f;

    private float target;
    private int unfed;

    private GondolaLoopSound(SoundEvent sound, boolean relative) {
        super(sound, SoundSource.AMBIENT, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.relative = relative;
        // No distance fall-off of its own: the owner works the volume out (it knows how close the line is).
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = 0.0f;
    }

    /**
     * Feeds {@code loop}, starting a new one if it isn't playing any more; returns the sound to
     * keep. {@code relative} loops play "in your head" (x, y, z ignored).
     */
    static GondolaLoopSound feed(@Nullable GondolaLoopSound loop, SoundEvent sound, boolean relative,
                                 double x, double y, double z, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (loop == null || loop.isStopped()) {
            if (volume <= 0.001f) {
                return loop;
            }
            loop = new GondolaLoopSound(sound, relative);
            loop.x = x;
            loop.y = y;
            loop.z = z;
            mc.getSoundManager().play(loop);
        }
        loop.target = volume;
        loop.pitch = pitch;
        loop.unfed = 0;
        if (!relative) {
            loop.x = x;
            loop.y = y;
            loop.z = z;
        }
        return loop;
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
