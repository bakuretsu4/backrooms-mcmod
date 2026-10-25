package com.rickr.backrooms.player;

import java.util.UUID;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * How "waterlogged" a player's mind is in the Poolrooms (a NeoForge data attachment, server side).
 *
 * <p>Not saved to disk on purpose: logging out or leaving the Poolrooms wakes you up anyway.
 * All the tweakable numbers of the hallucination mechanic live here, in one place.
 */
public final class HallucinationData {
    /** Wet ticks at which the dreamy picture starts (30 s in the water). */
    public static final int DREAM_START = 600;
    /** Wet ticks at which the dreamy picture is at full strength (60 s in the water). */
    public static final int DREAM_FULL = 1200;
    /** Wet ticks needed before your "friend" can show up. */
    public static final int PHANTOM_WET = 1200;
    /**
     * The meter stops counting up here (90 s), so a long swim never takes forever to dry off from:
     * at most 90 s on dry tiles clears it completely.
     */
    public static final int WET_MAX = 1800;
    /** Ticks after a phantom ends before the next one may appear (2 minutes). */
    public static final int PHANTOM_COOLDOWN = 2400;

    /** +1 per tick in the water, -1 per tick out of it, 0..{@link #WET_MAX}. */
    int wet;
    /** Counts down to 0; a new phantom can only appear at 0. */
    int phantomCooldown;
    /** The phantom currently haunting this player, if any (entity UUID in the Poolrooms level). */
    @Nullable
    UUID phantomId;

    // What the client last heard, so we only send packets when the picture really changes.
    float lastSentDream = -1.0f;

    public int wet() {
        return wet;
    }

    @Nullable
    public UUID phantomId() {
        return phantomId;
    }

    /** 0..1 strength of the dream visuals: a smooth ramp from {@link #DREAM_START} to {@link #DREAM_FULL}. */
    public float dream() {
        return dreamFor(wet);
    }

    public static float dreamFor(int wetTicks) {
        float t = Mth.clamp((wetTicks - DREAM_START) / (float) (DREAM_FULL - DREAM_START), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t); // smoothstep: eases in and out, no sudden jumps
    }
}
