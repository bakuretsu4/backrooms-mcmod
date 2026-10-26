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

    public static final Codec<SanityData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("sanity").forGetter(d -> d.sanity)
    ).apply(i, SanityData::new));

    float sanity;

    // What the client last heard, so we only send packets when the number really changes.
    int lastSentRounded = -1;
    /** Server tick (player.tickCount) of the last packet, for the "at least every 5 s" resend. */
    int lastSentTick;

    public SanityData() {
        this(MAX);
    }

    private SanityData(float sanity) {
        this.sanity = sanity;
    }

    public float sanity() {
        return sanity;
    }
}
