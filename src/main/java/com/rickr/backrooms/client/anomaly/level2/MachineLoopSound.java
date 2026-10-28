package com.rickr.backrooms.client.anomaly.level2;

import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A machine loop placed in the world. By default the faulty machinery of one hot zone: a loud
 * whirring loop placed in the middle of the room, heard from about 28 blocks down the corridor
 * (sounds.json attenuation_distance). "Faulty": now and then the motor sags (pitch and volume
 * dip) and catches again. Level2Emitters also uses it for the corridor's pump hums, machinery,
 * breaker panels and steam leaks (steady, no sag). It fades out and stops by itself once whoever
 * started it stops keeping it alive (you walked away).
 *
 * <p>Machines on the mains can lose their power ({@link #powered}): the loop then winds down
 * like a motor coasting to a stop (pitch and volume sinking together over about 3 s) and spins
 * back up over about 2 s when the power returns. That is how a dead section goes quiet.
 */
final class MachineLoopSound extends AbstractTickableSoundInstance {
    /** Ticks without a keep-alive before it fades out. */
    private static final int LINGER = 10;

    private final RandomSource random = RandomSource.create();
    private final float maxVolume;
    private final boolean faulty;
    private int sinceKept;
    private float fade;
    /** Remaining ticks of the current sag, its length and how deep it goes. */
    private int sag;
    private int sagLength = 1;
    private float sagDepth;
    /** 0 (stopped) .. 1 (running at speed); eases toward {@link #poweredGoal}. */
    private float power = 1.0f;
    private boolean poweredGoal = true;
    /** False until the first {@link #powered} call: a machine already dead when you arrive starts silent. */
    private boolean powerKnown;
    private final float basePitch;

    /** The hot zone's faulty machinery. */
    MachineLoopSound(double x, double y, double z, long salt) {
        this(ModSounds.L2_MACHINE_LOOP.get(), x, y, z, salt, 1.0f, true);
    }

    /** Any loop at (x, y, z), up to {@code maxVolume}; {@code faulty} ones sag now and then. */
    MachineLoopSound(SoundEvent sound, double x, double y, double z, long salt, float maxVolume, boolean faulty) {
        super(sound, SoundSource.BLOCKS, RandomSource.create(salt));
        this.maxVolume = maxVolume;
        this.faulty = faulty;
        this.x = x;
        this.y = y;
        this.z = z;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.relative = false;
        this.volume = 0.0f;
        // Steady loops start at a slightly different pitch each, so two pumps never phase together.
        this.pitch = faulty ? 1.0f : 0.96f + (salt & 0xFF) / 255.0f * 0.08f;
        this.basePitch = this.pitch;
    }

    /** Called every tick by whoever started it while you're near. */
    void keepAlive() {
        sinceKept = 0;
    }

    /** Called every tick (with keepAlive) by loops that run on the mains: is the power on here? */
    void powered(boolean on) {
        poweredGoal = on;
        if (!powerKnown) {
            powerKnown = true;
            power = on ? 1.0f : 0.0f;
        }
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
        float s = 0.0f;
        if (faulty) {
            if (sag <= 0 && random.nextInt(140) == 0) {
                sagLength = 10 + random.nextInt(30);
                sag = sagLength;
                sagDepth = 0.1f + random.nextFloat() * 0.2f;
            }
            if (sag > 0) {
                sag--;
                s = sagDepth * Mth.sin(Mth.PI * (1.0f - sag / (float) sagLength)); // dips, then catches again
            }
        }
        // Coasting down takes 60 ticks, spinning up 40 (a motor catches faster than it stops).
        power = poweredGoal ? Math.min(1.0f, power + 1.0f / 40.0f) : Math.max(0.0f, power - 1.0f / 60.0f);
        // The motor slows as it loses power: down to half pitch (Minecraft's lowest) when stopped.
        pitch = (basePitch - s) * (0.5f + 0.5f * power);
        // Squared: most of the sound is gone by the time the motor is at half speed.
        volume = maxVolume * fade * (1.0f - s * 0.8f) * power * power;
        if (goal == 0.0f && fade < 0.01f) {
            stop();
        }
    }
}
