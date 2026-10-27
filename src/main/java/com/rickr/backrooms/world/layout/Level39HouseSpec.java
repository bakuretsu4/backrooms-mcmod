package com.rickr.backrooms.world.layout;

import net.minecraft.core.Direction;

/**
 * One house of the Level 39 town, as the town plan ({@link Level39Cell}) decides it. Everything
 * is in world block coordinates, rectangles are inclusive on both ends, and the whole record is a
 * pure function of the seed, so every chunk (and the client) agrees on it.
 *
 * <p><b>For the house builder ({@link Level39Houses}).</b> The town calls
 * {@code Level39Houses.fillColumn(seed, spec, x, z, out)} for every column of the
 * {@linkplain #x0 footprint} (and only those). Rules:
 * <ul>
 *   <li>Build only inside the footprint {@code [x0..x1] x [z0..z1]} (porches, steps and bay windows
 *       included: the footprint already has room for them). The yard around it (up to the lot
 *       rectangle) belongs to the town: fences, trees, bushes and the path to the door.</li>
 *   <li>The ground (top block y = {@link Level39Layout#GROUND_Y}) is already written when you are
 *       called; later writes win, so you may replace it (cellars) or leave it. Do not write below
 *       y = {@link Level39Layout#GROUND_Y} - 8: tunnels can run under houses.</li>
 *   <li>{@link #baseY} is the y of the ground-floor blocks' feet level (GROUND_Y + 1 normally;
 *       higher for {@link Anomaly#FLOATING} houses, which hover with nothing under them).</li>
 *   <li>{@link #facing} is the side with the front door, toward the lot's path. The town keeps a
 *       gap in the front fence opposite the middle of that side ({@link #doorX}, {@link #doorZ}
 *       is the column just outside the front wall, on the ground, where a visitor would stand).</li>
 *   <li>Everything else (room layout, which windows, the dim lit window when {@link #litWindow},
 *       how an anomaly looks) is yours, derived from {@link #hash}.</li>
 * </ul>
 *
 * @param id        index of the house in its town (stable per seed)
 * @param lotX0     lot rectangle (the house's yard and fence line), west edge
 * @param lotZ0     lot rectangle, north edge
 * @param lotX1     lot rectangle, east edge
 * @param lotZ1     lot rectangle, south edge
 * @param x0        footprint, west edge
 * @param z0        footprint, north edge
 * @param x1        footprint, east edge
 * @param z1        footprint, south edge
 * @param style     0 = 1800s colonial saltbox (2.5 storeys, centre chimney, 5 bays),
 *                  1 = 1880s Victorian (bay window, corner turret, wrap porch),
 *                  2 = 1900s farmhouse (the photo: gable front, side wing, rear lean-to),
 *                  3 = 1920s Craftsman bungalow (deep porch, tapered posts, dormer),
 *                  4 = 1950s Cape Cod (twin dormers),
 *                  5 = 1970s/80s ranch (one long storey, low hip roof, garage door)
 * @param style2    for {@link Anomaly#MERGED} houses the style of the second house fused into
 *                  this one (the footprint covers both; the seam runs across the middle of the
 *                  long side); otherwise equal to {@link #style}
 * @param facing    the side with the front door (always toward a path)
 * @param storeys   full storeys (1-3) for the style; attics and half storeys come on top
 * @param anomaly   which anomaly the house carries (about 60% none)
 * @param baseY     y of the ground floor's walking level
 * @param litWindow true for about one house in twelve: one window glows dim and warm
 * @param doorX     column in front of the door, x
 * @param doorZ     column in front of the door, z
 * @param hash      a random number for every other choice the builder makes
 */
public record Level39HouseSpec(int id, int lotX0, int lotZ0, int lotX1, int lotZ1,
                               int x0, int z0, int x1, int z1,
                               int style, int style2, Direction facing, int storeys,
                               Anomaly anomaly, int baseY, boolean litWindow,
                               int doorX, int doorZ, long hash) {

    public static final int STYLE_COLONIAL = 0;
    public static final int STYLE_VICTORIAN = 1;
    public static final int STYLE_FARMHOUSE = 2;
    public static final int STYLE_CRAFTSMAN = 3;
    public static final int STYLE_CAPE_COD = 4;
    public static final int STYLE_RANCH = 5;

    /**
     * The canon house anomalies (see the Level 39 design). Chances are per house; the town plan
     * keeps about 60% of houses normal.
     */
    public enum Anomaly {
        /** An ordinary house (about 60%). */
        NONE,
        /** Built upside down: roof on the ground, foundation in the air (about 4%). */
        INVERTED,
        /** Two houses fused into one footprint, two styles meeting at a seam (about 5%). */
        MERGED,
        /** Sliced clean in half: one half simply isn't there, rooms open to the fog (about 5%). */
        CUT_HALF,
        /** No foundation: the house hovers a few blocks above its yard (about 5%). */
        FLOATING,
        /** Furniture in odd places or upside down (about 9%). */
        ODD_FURNITURE,
        /** Bigger on the inside: the door leads into a much larger interior (about 4%). */
        BIGGER_INSIDE,
        /** One room flooded to the ceiling with water or almond water (about 5%). */
        FLOODED_ROOM,
        /** A door that is a wall once you look back at it (about 3%). */
        VANISHING_DOOR
    }

    public int width() {
        return x1 - x0 + 1;
    }

    public int depth() {
        return z1 - z0 + 1;
    }

    public boolean contains(int x, int z) {
        return x >= x0 && x <= x1 && z >= z0 && z <= z1;
    }
}
