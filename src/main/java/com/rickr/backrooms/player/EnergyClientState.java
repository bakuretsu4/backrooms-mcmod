package com.rickr.backrooms.player;

/**
 * The local player's sprint energy as last sent by the server. Plain fields with no client
 * imports, so the packet handler can write them without loading client classes on a server.
 */
public final class EnergyClientState {
    /** 0..1 fraction of a full bar. */
    public static volatile float energy = 1.0f;
    /** True while out of breath: sprinting is blocked until the bar recovers. */
    public static volatile boolean exhausted;

    private EnergyClientState() {}
}
