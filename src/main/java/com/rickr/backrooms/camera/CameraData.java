package com.rickr.backrooms.camera;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-player camcorder state, stored on the player as a NeoForge data attachment (saved with the
 * player file, kept through death). The server owns it; clients get copies via {@code CameraSyncPayload}.
 */
public final class CameraData {
    public static final float MAX_BATTERY = 100.0f;

    public static final Codec<CameraData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("battery").forGetter(CameraData::battery),
            Codec.BOOL.fieldOf("night_shot").forGetter(CameraData::nightShot),
            Codec.BOOL.optionalFieldOf("flashlight", false).forGetter(CameraData::flashlight),
            Codec.LONG.optionalFieldOf("tape_ticks", 0L).forGetter(CameraData::tapeTicks)
    ).apply(i, CameraData::new));

    private float battery;
    private boolean nightShot;
    private boolean flashlight;
    /** Ticks recorded since this player last entered the Backrooms; drives the HUD tape counter. */
    private long tapeTicks;

    // Server-side bookkeeping for syncing; not saved.
    public int lastSentBatteryTenths = -1;
    public boolean lastSentNightShot;
    public boolean lastSentFlashlight;
    public int lastSentSignal = -1;
    public boolean warned15;
    public boolean warned5;

    public CameraData() {
        this(MAX_BATTERY, false, false, 0L);
    }

    public CameraData(float battery, boolean nightShot, boolean flashlight, long tapeTicks) {
        this.battery = battery;
        this.nightShot = nightShot;
        this.flashlight = flashlight;
        this.tapeTicks = tapeTicks;
    }

    public float battery() {
        return battery;
    }

    public boolean nightShot() {
        return nightShot;
    }

    /** The head-mounted flashlight (shares the camcorder battery). */
    public boolean flashlight() {
        return flashlight;
    }

    public long tapeTicks() {
        return tapeTicks;
    }

    public boolean dead() {
        return battery <= 0.0f;
    }

    public void setBattery(float value) {
        battery = Math.max(0.0f, Math.min(MAX_BATTERY, value));
        if (battery > 15.0f) warned15 = false;
        if (battery > 5.0f) warned5 = false;
        if (battery <= 0.0f) {
            nightShot = false;
            flashlight = false;
        }
    }

    public void setNightShot(boolean value) {
        nightShot = value && !dead();
    }

    public void setFlashlight(boolean value) {
        flashlight = value && !dead();
    }

    public void setTapeTicks(long value) {
        tapeTicks = value;
    }
}
