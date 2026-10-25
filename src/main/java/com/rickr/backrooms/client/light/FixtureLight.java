package com.rickr.backrooms.client.light;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * One light source made from one or more neighbouring fixture blocks (a strip of tubes emits as a
 * single light). Plain data, no Veil types, so it also drives the hum sounds when Veil is absent.
 */
public final class FixtureLight {
    /** Stable id: the packed position of the strip's first block. Also seeds its flicker pattern. */
    public final long key;
    public final FixtureType type;
    /** Where the light itself sits (always inside an air cell so Veil's shadow march works). */
    public final double x;
    public final double y;
    public final double z;
    /** Where the hum comes from: the middle of the fixture blocks. */
    public final double soundX;
    public final double soundY;
    public final double soundZ;
    /** Wall lamps: the direction pointing into the room. Null for ceiling lights. */
    @Nullable
    public final Direction facing;
    /**
     * A Level 9 lamp whose block says mode=flicker: it stutters on its own broken rhythm all the
     * time (see {@link FixturePower#brokenLampOn}).
     */
    public final boolean broken;

    // Runtime state, updated every client tick by BackroomsLights.
    /** 0..1 power from events (blackout fade, flicker, alarm dimming). */
    public float level = 1.0f;
    /** Whether the flicker currently has it on (used to detect toggles for the blink sound). */
    public boolean on = true;
    /** Squared distance to the camera, for picking the nearest lights. */
    public double distSq;

    public FixtureLight(long key, FixtureType type, double x, double y, double z,
                        double soundX, double soundY, double soundZ, @Nullable Direction facing, boolean broken) {
        this.key = key;
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.soundX = soundX;
        this.soundY = soundY;
        this.soundZ = soundZ;
        this.facing = facing;
        this.broken = broken;
    }

    /** Same light (same key) but rebuilt at a different spot, e.g. after its strip grew. */
    public boolean samePlace(FixtureLight other) {
        return other.type == type && other.x == x && other.y == y && other.z == z;
    }
}
