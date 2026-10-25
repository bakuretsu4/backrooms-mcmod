package com.rickr.backrooms.client.light;

import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The buzz of one fixture: a looping sound placed at the lamp (the fluorescent tube hum, or a
 * Level 9 streetlamp's sodium ballast hum). It fades with the lamp's power (so it cuts out with
 * the flicker) and stops itself once released and silent. Minecraft's linear attenuation makes
 * it fade out over the sound's attenuation distance (16 blocks; 12 for the sodium hum).
 */
final class FixtureHumSound extends AbstractTickableSoundInstance {
    static final float MAX_VOLUME = 0.4f;
    /** The sodium hum is meant to be barely there: you notice it only standing under the lamp. */
    static final float SODIUM_MAX_VOLUME = 0.22f;

    /** 0..1 how loud we want to be (the lamp's power). */
    float target;
    private final float maxVolume;
    private boolean released;

    FixtureHumSound(FixtureLight light, float target) {
        super(light.type.hum == FixtureType.Hum.SODIUM ? ModSounds.LIGHT_SODIUM_HUM.get() : ModSounds.LIGHT_HUM.get(),
                SoundSource.BLOCKS, RandomSource.create(light.key));
        this.x = light.soundX;
        this.y = light.soundY;
        this.z = light.soundZ;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.relative = false;
        // Each lamp buzzes at a slightly different pitch, like real ballasts.
        this.pitch = 0.94f + (float) ((FixturePower.mix(light.key) >>> 40) & 0xFF) / 255.0f * 0.12f;
        this.maxVolume = light.type.hum == FixtureType.Hum.SODIUM ? SODIUM_MAX_VOLUME : MAX_VOLUME;
        this.target = target;
        this.volume = maxVolume * target;
    }

    /** Fade out and stop. */
    void release() {
        released = true;
    }

    boolean released() {
        return released;
    }

    @Override
    public boolean canStartSilent() {
        return true; // so a lamp that starts mid-flicker (volume 0) still gets its channel
    }

    @Override
    public void tick() {
        float goal = released ? 0.0f : maxVolume * target;
        // Fast fade (about 2 ticks) so the hum cuts with the flicker but never clicks.
        volume += (goal - volume) * 0.6f;
        if (released && volume < 0.005f) {
            stop();
        }
    }
}
