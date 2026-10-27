package com.rickr.backrooms.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * How sane a player still is, 0..100 (a NeoForge data attachment, server side).
 *
 * <p>Saved to disk, so logging out and back in doesn't reset it. Not copied on death: a new
 * player starts at {@link #MAX}, and {@link SanityService} sets {@link #RESPAWN} after dying.
 * All the drain/recovery numbers live in {@link SanityService}.
 */
public final class SanityData {
    public static final float MAX = 100.0f;
    /** What you wake up with after dying: shaken, but not broken. */
    public static final float RESPAWN = 70.0f;
    /**
     * Below this you hear your own heartbeat, and friends near you hear it faintly too
     * ({@link SharedHeartbeat}). One number for both, so they always start together.
     */
    public static final float HEARTBEAT_BELOW = 30.0f;
    /** Heart rate: 70 BPM when sane, 160 at 0 (the HUD readout and both heartbeats use it). */
    private static final float CALM_BPM = 70.0f;
    private static final float PANIC_BPM = 160.0f;

    public static final Codec<SanityData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("sanity").forGetter(d -> d.sanity)
    ).apply(i, SanityData::new));

    float sanity;

    // What the client last heard, so we only send packets when the number really changes.
    int lastSentRounded = -1;
    /** Server tick (player.tickCount) of the last packet, for the "at least every 5 s" resend. */
    int lastSentTick;
    /**
     * Heartbeats so far for the heartbeat friends hear (the fraction = where we are in the current
     * beat). Server only and not saved: after a restart the next beat simply starts from zero.
     */
    double heartPhase;

    public SanityData() {
        this(MAX);
    }

    private SanityData(float sanity) {
        this.sanity = sanity;
    }

    public float sanity() {
        return sanity;
    }

    /** The heart rate (BPM, rounded like the HUD shows it) for a sanity of 0..100. */
    public static int bpm(float sanity) {
        return Math.round(CALM_BPM + (PANIC_BPM - CALM_BPM) * (1.0f - sanity / MAX));
    }
}
