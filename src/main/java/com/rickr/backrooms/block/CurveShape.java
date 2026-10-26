package com.rickr.backrooms.block;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * Which piece of a circle a {@link TileCurveBlock} is. A circle of radius R centred on a block
 * corner cuts through some of the cells around it; each such cell is one piece. Pieces are named
 * by the cell's position in the quarter "x >= 0, z >= 0" around the centre (i along x, j along z;
 * {@link CurveOrientation} turns that quarter into the other three, or onto its side for arches).
 * Cells fully inside or outside the circle are plain air or plain tile and have no piece.
 *
 * <ul>
 *   <li><b>convex</b>: solid inside the circle (round pillars, a wall bulging into a room);</li>
 *   <li><b>concave</b>: solid outside it (rounded room corners, round rooms and pools, arches).</li>
 * </ul>
 *
 * <p>The list (radii 1-3 convex, 1, 2, 3, 4 and 6 concave) must match {@code tools/gen_assets.py}
 * ({@code tex_l37_curve_models}), which writes one OBJ model per piece and orientation.
 */
public enum CurveShape implements StringRepresentable {
    CONVEX_R1_0_0(true, 1, 0, 0),
    CONVEX_R2_0_1(true, 2, 0, 1),
    CONVEX_R2_1_0(true, 2, 1, 0),
    CONVEX_R2_1_1(true, 2, 1, 1),
    CONVEX_R3_0_2(true, 3, 0, 2),
    CONVEX_R3_1_2(true, 3, 1, 2),
    CONVEX_R3_2_0(true, 3, 2, 0),
    CONVEX_R3_2_1(true, 3, 2, 1),
    CONVEX_R3_2_2(true, 3, 2, 2),
    CONCAVE_R1_0_0(false, 1, 0, 0),
    CONCAVE_R2_0_1(false, 2, 0, 1),
    CONCAVE_R2_1_0(false, 2, 1, 0),
    CONCAVE_R2_1_1(false, 2, 1, 1),
    CONCAVE_R3_0_2(false, 3, 0, 2),
    CONCAVE_R3_1_2(false, 3, 1, 2),
    CONCAVE_R3_2_0(false, 3, 2, 0),
    CONCAVE_R3_2_1(false, 3, 2, 1),
    CONCAVE_R3_2_2(false, 3, 2, 2),
    CONCAVE_R4_0_3(false, 4, 0, 3),
    CONCAVE_R4_1_3(false, 4, 1, 3),
    CONCAVE_R4_2_2(false, 4, 2, 2),
    CONCAVE_R4_2_3(false, 4, 2, 3),
    CONCAVE_R4_3_0(false, 4, 3, 0),
    CONCAVE_R4_3_1(false, 4, 3, 1),
    CONCAVE_R4_3_2(false, 4, 3, 2),
    CONCAVE_R6_0_5(false, 6, 0, 5),
    CONCAVE_R6_1_5(false, 6, 1, 5),
    CONCAVE_R6_2_5(false, 6, 2, 5),
    CONCAVE_R6_3_4(false, 6, 3, 4),
    CONCAVE_R6_3_5(false, 6, 3, 5),
    CONCAVE_R6_4_3(false, 6, 4, 3),
    CONCAVE_R6_4_4(false, 6, 4, 4),
    CONCAVE_R6_5_0(false, 6, 5, 0),
    CONCAVE_R6_5_1(false, 6, 5, 1),
    CONCAVE_R6_5_2(false, 6, 5, 2),
    CONCAVE_R6_5_3(false, 6, 5, 3);

    /** Solid inside the circle (true) or outside it (false). */
    public final boolean convex;
    public final int radius;
    /** The cell's position in the quarter around the centre: i along x, j along z (0 = touching the centre lines). */
    public final int i;
    public final int j;
    private final String serializedName;

    CurveShape(boolean convex, int radius, int i, int j) {
        this.convex = convex;
        this.radius = radius;
        this.i = i;
        this.j = j;
        this.serializedName = name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /** True when canonical point (x, z) of this piece's cell (0..1 each) is solid. */
    public boolean isSolidAt(double x, double z) {
        double d = Math.hypot(i + x, j + z);
        return convex ? d <= radius : d >= radius;
    }

    /** The piece for cell (i, j) of the quarter around a circle of this radius, or null if that cell isn't cut. */
    @Nullable
    public static CurveShape of(boolean convex, int radius, int i, int j) {
        if (radius < 0 || radius >= MAX || i < 0 || i >= MAX || j < 0 || j >= MAX) {
            return null;
        }
        return LOOKUP[convex ? 1 : 0][radius][i][j];
    }

    /** Biggest radius + 1; the table below is indexed [convex][radius][i][j]. */
    private static final int MAX = 7;
    private static final CurveShape[][][][] LOOKUP = new CurveShape[2][MAX][MAX][MAX];

    static {
        for (CurveShape shape : values()) {
            LOOKUP[shape.convex ? 1 : 0][shape.radius][shape.i][shape.j] = shape;
        }
    }
}
