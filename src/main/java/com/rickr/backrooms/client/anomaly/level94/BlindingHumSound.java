package com.rickr.backrooms.client.anomaly.level94;

import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

/**
 * The thin, glassy hum of the blinding hall's light, heard "in your head" (not from a place) while
 * the glare is on you. {@link BlindingHallEffect} calls {@link #keepPlaying} every tick it wants
 * it; once the calls stop, the hum fades out and stops by itself.
 */
final class BlindingHumSound extends AbstractTickableSoundInstance {
    private static final float MAX_VOLUME = 0.55f;
    /** Ticks without a keepPlaying call before the hum starts to fade. */
    private static final int STALE_TICKS = 3;

    @Nullable
    private static BlindingHumSound playing;

    private float target;
    private int sinceAsked;

    private BlindingHumSound() {
        super(ModSounds.L94_BLINDING_HUM.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = 0.0f;
    }

    /** Ask for the hum at {@code weight} (0..1) this tick, starting it if needed. */
    static void keepPlaying(Minecraft mc, float weight) {
        if (playing == null || playing.isStopped()) {
            playing = new BlindingHumSound();
            mc.getSoundManager().play(playing);
        }
        playing.target = MAX_VOLUME * weight;
        playing.sinceAsked = 0;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        sinceAsked++;
        float goal = sinceAsked > STALE_TICKS ? 0.0f : target;
        volume += (goal - volume) * 0.15f;
        if (sinceAsked > STALE_TICKS && volume < 0.005f) {
            stop();
            if (playing == this) {
                playing = null;
            }
        }
    }
}
