package com.rickr.backrooms.client.light;

import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The buzz of one fixture: a looping sound placed at the lamp (the fluorescent tube hum, or a
 * Level 9 streetlamp's sodium ballast hum). It fades with the lamp's power (so it cuts out with
 * the flicker) and stops itself once released and silent. Minecraft's linear attenuation makes
 * it fade out over the sound's attenuation distance (16 blocks; 12 for the sodium hum; 14 for a
 * Level 0 tube).
 *
 * <p>Level 0's tubes each sound a little different, like real ballasts of different makes and
 * ages: the sound event has four buzzes and the tube's position picks one (always the same one
 * for the same tube), plus its own pitch and loudness; worn tubes buzz a little louder. Faulty
 * tubes get a rougher, unsteady buzz with a rattle and faint arcing in it.
 */
final class FixtureHumSound extends AbstractTickableSoundInstance {
    static final float MAX_VOLUME = 0.4f;
    /** The sodium hum is meant to be barely there: you notice it only standing under the lamp. */
    static final float SODIUM_MAX_VOLUME = 0.22f;
    /**
     * A Level 0 tube: louder than other lamps' hum, because it also fades away much faster (see
     * FixtureSounds) and the level's room tone is kept low, so under a tube you hear it clearly
     * and between lit areas it is gone.
     */
    static final float L0_MAX_VOLUME = 0.55f;

    /** 0..1 how loud we want to be (the lamp's power). */
    float target;
    private final float maxVolume;
    private boolean released;

    /**
     * @param level0Tube a Level 0 ceiling tube (its own buzz, see the class doc)
     * @param faulty     a Level 0 tube that stutters, restarts or is dying: the rough buzz
     */
    FixtureHumSound(FixtureLight light, float target, boolean level0Tube, boolean faulty) {
        // The sound's random source picks which of the event's files plays. Seeded with a hash of
        // the position (not the raw position: neighbouring raw seeds give near-identical first
        // picks), so each tube always gets the same buzz and neighbours mostly differ.
        super(soundFor(light, level0Tube, faulty), SoundSource.BLOCKS, RandomSource.create(FixturePower.mix(light.key ^ 0x51ED2701L)));
        this.x = light.soundX;
        this.y = light.soundY;
        this.z = light.soundZ;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.relative = false;
        // Each lamp buzzes at a slightly different pitch, like real ballasts.
        this.pitch = 0.94f + (float) ((FixturePower.mix(light.key) >>> 40) & 0xFF) / 255.0f * 0.12f;
        if (light.type.hum == FixtureType.Hum.SODIUM) {
            this.maxVolume = SODIUM_MAX_VOLUME;
        } else if (level0Tube) {
            // Some ballasts are simply louder than others (0.8-1.1), and a worn tube (lower glow,
            // see FixtureLight#tubeGlow) buzzes a little more than a fresh one.
            float own = 0.8f + (float) ((FixturePower.mix(light.key) >>> 16) & 0xFF) / 255.0f * 0.3f;
            float age = Math.clamp(1.0f + (1.0f - light.tubeGlow) * 0.6f, 0.95f, 1.15f);
            this.maxVolume = L0_MAX_VOLUME * own * age;
        } else {
            this.maxVolume = MAX_VOLUME;
        }
        this.target = target;
        this.volume = maxVolume * target;
    }

    private static SoundEvent soundFor(FixtureLight light, boolean level0Tube, boolean faulty) {
        if (light.type.hum == FixtureType.Hum.SODIUM) {
            return ModSounds.LIGHT_SODIUM_HUM.get();
        }
        if (level0Tube) {
            return faulty ? ModSounds.L0_TUBE_HUM_FAULTY.get() : ModSounds.L0_TUBE_HUM.get();
        }
        return ModSounds.LIGHT_HUM.get();
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
