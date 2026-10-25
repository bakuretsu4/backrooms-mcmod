package com.rickr.backrooms.player;

/**
 * The local player's hallucination as last sent by the server. Plain fields with no client
 * imports, so packet handlers and the (common) phantom entity can write them safely on any side.
 */
public final class HallucinationClientState {
    /** 0..1 strength of the dream visuals and the hallucination loop sound. */
    public static volatile float dream;
    /** System.currentTimeMillis() when your phantom friend last vanished (drives a short shader pulse). */
    public static volatile long vanishPulseMs;

    private HallucinationClientState() {}
}
