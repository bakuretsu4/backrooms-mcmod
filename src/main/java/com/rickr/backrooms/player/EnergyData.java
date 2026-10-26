package com.rickr.backrooms.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Sprint energy stored on each player (a NeoForge data attachment). Counted in ticks:
 * a full bar lets you run for {@link #MAX} ticks (15 s) before you are out of breath.
 */
public final class EnergyData {
    public static final int MAX = 300;
    /** Once exhausted you can't sprint again until the bar refills past this. */
    public static final int RECOVER_AT = 200;

    public static final Codec<EnergyData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("energy").forGetter(d -> d.energy),
            Codec.BOOL.fieldOf("exhausted").forGetter(d -> d.exhausted)
    ).apply(i, EnergyData::new));

    int energy;
    boolean exhausted;

    // What the client last heard about, so we only send packets when something changed.
    int lastSentEnergy = -1;
    boolean lastSentExhausted;

    public EnergyData() {
        this(MAX, false);
    }

    private EnergyData(int energy, boolean exhausted) {
        this.energy = energy;
        this.exhausted = exhausted;
    }

    public int energy() {
        return energy;
    }

    public boolean exhausted() {
        return exhausted;
    }

    /** Full bar and breath back at once (the Energy Bar). Server side; call PlayerGameplay.forceSync after. */
    public void refill() {
        energy = MAX;
        exhausted = false;
    }

    /**
     * Uses up {@code amount} ticks of breath, the way running does (Level 2's hot zones wear you
     * out even standing still). An empty bar means out of breath. Server side.
     */
    public void drain(int amount) {
        energy = Math.max(0, energy - amount);
        if (energy == 0) {
            exhausted = true;
        }
    }
}
