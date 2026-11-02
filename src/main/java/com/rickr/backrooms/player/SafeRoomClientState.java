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
    /** Who saved the last shared checkpoint we were told about ("" = nobody yet)... */
    public static volatile String sharedSavedName = "";
    /** ...and when that message arrived (system ms, 0 = never). */
    public static volatile long sharedSavedMs;
    /**
     * After you die: the name of the friend whose shared checkpoint you may wake up at instead of
     * your own (the death screen then shows two extra buttons). Null = no choice; cleared again
     * when you respawn or leave the server.
     */
    public static volatile String offeredSharedName;

    private SafeRoomClientState() {}
}
