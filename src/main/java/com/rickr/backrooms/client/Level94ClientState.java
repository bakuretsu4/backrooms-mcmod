package com.rickr.backrooms.client;

/**
 * What the client knows about Level 94 from the server ({@code network/Level94StatePayload}):
 * where your nearest castle stands, so the sky can draw it far away on the horizon. The clock
 * start goes straight into {@code world/Level94Clock.setClientStart}.
 *
 * <p>Plain fields only (no Minecraft client classes), so the packet handler in ModNetwork can set
 * them without dragging client code onto a dedicated server.
 */
public final class Level94ClientState {
    /** Centre of the nearest castle (block x/z). */
    public static volatile int castleX;
    public static volatile int castleZ;
    /** False until the server has told us where the castle is; the sky draws no castle until then. */
    public static volatile boolean castleKnown;

    private Level94ClientState() {}
}
