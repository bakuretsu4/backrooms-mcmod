package com.rickr.backrooms.client.anomaly.level2;

import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The faulty machinery of one hot zone: a loud whirring loop placed in the middle of the room,
 * heard from about 28 blocks down the corridor (sounds.json attenuation_distance). "Faulty": now
 * and then the motor sags (pitch and volume dip) and catches again. It fades out and stops by
 * itself once its effect stops keeping it alive (you walked away).
 */
final class MachineLoopSound extends AbstractTickableSoundInstance {
    private static final float MAX_VOLUME = 1.0f;
    /** Ticks without a keep-alive before it fades out. */
    private static final int LINGER = 10;

    private final RandomSource random = RandomSource.create();
    private int sinceKept;
    private float fade;
    /** Remaining ticks of the current sag, its length and how deep it goes. */
    private int sag;
    private int sagLength = 1;
    private float sagDepth;

    MachineLoopSound(double x, double y, double z, long salt) {
        super(ModSounds.L2_MACHINE_LOOP.get(), SoundSource.BLOCKS, RandomSource.create(salt));
        this.x = x;
        this.y = y;
        this.z = z;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.relative = false;
        this.volume = 0.0f;
        this.pitch = 1.0f;
    }

    /** Called every tick by the hot zone effect while you're near. */
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
        if (sag <= 0 && random.nextInt(140) == 0) {
            sagLength = 10 + random.nextInt(30);
            sag = sagLength;
            sagDepth = 0.1f + random.nextFloat() * 0.2f;
        }
        float s = 0.0f;
        if (sag > 0) {
            sag--;
            s = sagDepth * Mth.sin(Mth.PI * (1.0f - sag / (float) sagLength)); // dips, then catches again
        }
        pitch = 1.0f - s;
        volume = MAX_VOLUME * fade * (1.0f - s * 0.8f);
        if (goal == 0.0f && fade < 0.01f) {
            stop();
        }
    }
}
