package com.rickr.backrooms.player;

/**
 * The local player's sanity as last sent by the server (0..100). A plain field with no client
 * imports, so the packet handler can write it safely on any side.
 */
public final class SanityClientState {
    public static volatile float sanity = SanityData.MAX;

    private SanityClientState() {}
}
