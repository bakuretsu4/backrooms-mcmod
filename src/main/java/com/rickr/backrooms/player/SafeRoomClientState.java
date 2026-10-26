package com.rickr.backrooms.player;

/**
 * The local player's safe-room state as last sent by the server. Plain fields with no client
 * imports, so the packet handlers can write them without loading client classes on a server.
 */
public final class SafeRoomClientState {
    /** True while you stand inside a safe room. */
    public static volatile boolean inSafeRoom;
    /** When the last "checkpoint saved" message arrived (system ms, 0 = never). */
    public static volatile long checkpointSavedMs;

    private SafeRoomClientState() {}
}
