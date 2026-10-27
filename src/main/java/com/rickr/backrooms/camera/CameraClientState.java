package com.rickr.backrooms.camera;

/**
 * The local player's camcorder state as last sent by the server. Plain fields with no client
 * imports, so packet handlers can write to it without loading client classes on a server.
 */
public final class CameraClientState {
    public static volatile float battery = CameraData.MAX_BATTERY;
    public static volatile boolean nightShot;
    public static volatile boolean flashlight;
    public static volatile float exitSignal;
    public static volatile long tapeTicks;
    /** System time (ms) when {@link #tapeTicks} arrived, so the HUD counter keeps running between syncs. */
    public static volatile long tapeSyncMs;
    /** System time (ms) when you last escaped back to reality (the tape glitches and stops), or 0. */
    public static volatile long escapeStartMs;
    /** Current floor event ({@code LevelEventPayload} constant) and when it ends (system ms). */
    public static volatile int eventKind;
    public static volatile long eventEndMs;
    /** When the current event started (system ms), for fade-ins like the blackout or the twist roll. */
    public static volatile long eventStartMs;
    /** Filming the real world with a camcorder (the camcorder look and REC overlay on outside the Backrooms). */
    public static volatile boolean realRecording;
    /** 0..1 how thin reality has worn around you (alone in the dark; filming it): the hum, the glitches. */
    public static volatile float realThin;
    /** Last noclip step ({@code IntroCutscenePayload} constant) and when it arrived (system ms, 0 = none). */
    public static volatile int introPhase;
    public static volatile long introStartMs;

    private CameraClientState() {}
}
