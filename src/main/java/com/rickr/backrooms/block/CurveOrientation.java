package com.rickr.backrooms.block;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * How a {@link TileCurveBlock} piece is turned. Every piece is modelled once as part of an upright
 * cylinder (axis = Y) in the quarter "x >= 0, z >= 0" around the circle's centre; this says which
 * quarter it really is.
 *
 * <ul>
 *   <li>SE, SW, NW, NE: upright (pillars, round rooms, rounded corners, wavy walls, pools). SE is
 *       the model as drawn; each next one is a further quarter turn clockwise seen from above.</li>
 *   <li>Z_EAST, X_SOUTH, Z_WEST, X_NORTH: lying on its side for arches and round tunnels, always
 *       the upper half of the circle. Z_EAST: the tunnel runs along Z and this is the piece east
 *       of its middle; X_SOUTH: runs along X, south of the middle; and so on.</li>
 * </ul>
 * Matches the blockstate rotations written by {@code tools/gen_assets.py} (horizontal = model
 * x 90, then y 90 per quarter turn).
 */
public enum CurveOrientation implements StringRepresentable {
    SE(false, 0),
    SW(false, 1),
    NW(false, 2),
    NE(false, 3),
    Z_EAST(true, 0),
    X_SOUTH(true, 1),
    Z_WEST(true, 2),
    X_NORTH(true, 3);

    /** Lying on its side (an arch piece) instead of standing upright. */
    public final boolean horizontal;
    /** Quarter turns clockwise seen from above. */
    public final int turns;
    private final String serializedName;

    CurveOrientation(boolean horizontal, int turns) {
        this.horizontal = horizontal;
        this.turns = turns;
        this.serializedName = name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /**
     * Turns a point of the drawn (SE) model into this orientation, in place. Block units, 0..1.
     * Same maths as the model generator: lying down maps +z to +y (and +y to -z), then each
     * quarter turn maps (x, z) around the block centre to (-z, x).
     */
    public void apply(double[] p) {
        if (horizontal) {
            double y = p[1];
            p[1] = p[2];
            p[2] = 1.0 - y;
        }
        for (int k = 0; k < turns; k++) {
            double x = p[0];
            p[0] = 1.0 - p[2];
            p[2] = x;
        }
    }

    /** The upright piece for the quarter on side (east?, south?) of the circle's centre. */
    public static CurveOrientation upright(boolean east, boolean south) {
        if (east) {
            return south ? SE : NE;
        }
        return south ? SW : NW;
    }

    /**
     * The arch piece of a tunnel running along X ({@code alongX}) or Z, on the positive side of
     * its middle (south for a tunnel along X, east for one along Z) or the negative side.
     */
    public static CurveOrientation arch(boolean alongX, boolean positiveSide) {
        if (alongX) {
            return positiveSide ? X_SOUTH : X_NORTH;
        }
        return positiveSide ? Z_EAST : Z_WEST;
    }
}
