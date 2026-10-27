package com.rickr.backrooms.world.layout;

import static com.rickr.backrooms.world.layout.Level39HousesBlocks.*;

import com.rickr.backrooms.block.Level39HouseWallBlock;
import com.rickr.backrooms.block.Level39PairFurnitureBlock;
import com.rickr.backrooms.world.Hash;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;

/**
 * Draws one Level 39 house ({@link Level39HouseSpec}) into a small 3D grid of block codes
 * ({@link Level39HousesBlocks}), once; {@link Level39HousesPlan} then keeps it as a compact
 * cache that {@link Level39Houses#fillColumn} reads back column by column.
 *
 * <p><b>Local grid.</b> A house is drawn in its own coordinates: u runs along the front, left to
 * right as you look at the house from the path; v runs from the front edge of the footprint (0)
 * to the back (D - 1); y is the real height. The front looks out toward -v ({@link #F}).
 *
 * <p><b>How a house is put together</b> (see {@link #buildSub}):
 * <ol>
 *   <li>the style template lays out rooms (rectangles that include their walls; rooms of the same
 *       storey share walls), interior doors, roof shapes, porch, chimneys and dormers;</li>
 *   <li>every storey is drawn: floors, outside walls (clapboard outside, the room's wallpaper inside),
 *       inside walls, doors, windows, the popcorn ceilings;</li>
 *   <li>the roofs are drawn from "planes" (each one rises from an eave line at a fixed pitch; a gable
 *       is two planes, a hip four), using shingle stairs and slabs so the slopes are smooth; walls
 *       grow up into the gables, and whatever sticks out above a roof is cut away, which is what
 *       makes the low upper rooms of a Cape Cod or a saltbox;</li>
 *   <li>then chimneys, porch, stairs, cellar, the knockable wall sections, furniture and bulbs;</li>
 *   <li>last the anomaly, if any: merged with a second house, cut in half, turned upside down.</li>
 * </ol>
 */
final class Level39HousesBuilder {
    /** Lowest y a house may touch (the town's rule: tunnels run under the houses). */
    static final int Y0 = Level39Layout.GROUND_Y - 8;
    /** Height of the drawing grid. */
    static final int CH = 80;

    // ---- room types ----------------------------------------------------------------------------
    static final int HALL = 0;
    static final int LIVING = 1;
    static final int PARLOR = 2;
    static final int DINING = 3;
    static final int KITCHEN = 4;
    static final int BED = 5;
    static final int BATH = 6;
    static final int GARAGE = 7;
    static final int TURRET = 8;
    static final int LANDING = 9;
    static final int MUDROOM = 10;

    // ---- storey grid cells ---------------------------------------------------------------------
    private static final byte OUT = 0;
    private static final byte WALL = 1;
    private static final byte FLOOR = 2;
    private static final byte DOORWAY = 3;

    private static final int MAX_STOREYS = 4;

    // ---- the house and its frame --------------------------------------------------------------
    final long seed;
    final Level39HouseSpec h;
    /** Footprint size along the front (u) and front to back (v). */
    final int W;
    final int D;
    /** World directions of local front (-v), back (+v), right (+u) and left (-u). */
    final Direction F;
    final Direction B;
    final Direction R;
    final Direction L;
    /** World position of local (0, 0). */
    final int ox;
    final int oz;
    /** The drawing: one block code per cell, index ((u * D) + v) * CH + (y - Y0). */
    final int[] c;

    // ---- the sub-house being drawn (merged houses draw two into separate grids) -------------
    int style;
    int s0;
    int s1;
    int du;
    int F0;
    int P;
    int fullStoreys;
    int storeys;
    boolean halfTop;
    int shade;
    long hb;
    boolean primary;
    boolean stairsWanted;
    int fv;
    int bodyU0;
    int bodyU1;
    int bodyV1;
    boolean basement;
    int paperGround;
    int paperUpper;
    boolean old;
    int floorMain;
    boolean belt;

    final List<Room> rooms = new ArrayList<>();
    final List<int[]> doorSpecs = new ArrayList<>();
    final List<Prim> prims = new ArrayList<>();
    final List<int[]> chimneys = new ArrayList<>();
    final List<int[]> dormerWindows = new ArrayList<>();
    final List<Prim> dormerCores = new ArrayList<>();
    final List<int[]> stairsList = new ArrayList<>();
    final List<int[]> porchFloors = new ArrayList<>();
    final List<int[]> porchPosts = new ArrayList<>();
    final List<int[]> porchRails = new ArrayList<>();
    final List<int[]> porchSteps = new ArrayList<>();
    final List<int[]> finials = new ArrayList<>();
    int porchPostKind = POST_WHITE;
    int porchBeamY = Integer.MIN_VALUE;
    int garageDoorU = Integer.MIN_VALUE;

    byte[][] type;
    short[][] roomOf;
    boolean[][] reserved;
    int[] rTop;
    int[] rBot;
    int[] topPrim;
    /** Level (half blocks) of the main group-0 roof without its dormers, per column. */
    int[] mainLevel;
    int litWindowsLeft;

    Level39HousesBuilder(long seed, Level39HouseSpec h) {
        this.seed = seed;
        this.h = h;
        F = h.facing();
        B = F.getOpposite();
        R = F.getClockWise();
        L = R.getOpposite();
        boolean uAlongX = R.getAxis() == Direction.Axis.X;
        W = uAlongX ? h.width() : h.depth();
        D = uAlongX ? h.depth() : h.width();
        int sx = R.getStepX() + B.getStepX();
        int sz = R.getStepZ() + B.getStepZ();
        ox = sx > 0 ? h.x0() : h.x1();
        oz = sz > 0 ? h.z0() : h.z1();
        c = new int[W * D * CH];
    }

    // ==========================================================================================
    // Small helpers
    // ==========================================================================================

    int u(int x, int z) {
        return (x - ox) * R.getStepX() + (z - oz) * R.getStepZ();
    }

    int v(int x, int z) {
        return (x - ox) * B.getStepX() + (z - oz) * B.getStepZ();
    }

    int worldX(int u, int v) {
        return ox + R.getStepX() * u + B.getStepX() * v;
    }

    int worldZ(int u, int v) {
        return oz + R.getStepZ() * u + B.getStepZ() * v;
    }

    boolean inside(int u, int v) {
        return u >= 0 && u < W && v >= 0 && v < D;
    }

    int at(int u, int v, int y) {
        if (!inside(u, v) || y < Y0 || y >= Y0 + CH) return EMPTY;
        return c[((u * D) + v) * CH + (y - Y0)];
    }

    void put(int u, int v, int y, int code) {
        if (!inside(u, v) || y < Y0 || y >= Y0 + CH) return;
        c[((u * D) + v) * CH + (y - Y0)] = code;
    }

    /** Writes only into a cell nothing has claimed yet. */
    void putIfEmpty(int u, int v, int y, int code) {
        if (at(u, v, y) == EMPTY) put(u, v, y, code);
    }

    long bits(int a, int b, int salt) {
        return Hash.of(hb, a, b, salt);
    }

    double unit(int a, int b, int salt) {
        return Hash.unit(bits(a, b, salt));
    }

    int fy(int k) {
        return F0 + k * P;
    }

    /** World direction of a local step (du, dv) (one of them 0). */
    Direction dirOf(int dU, int dV) {
        if (dV < 0) return F;
        if (dV > 0) return B;
        return dU > 0 ? R : L;
    }

    /** Local u step of a world direction. */
    int stepU(Direction d) {
        return d == R ? 1 : d == L ? -1 : 0;
    }

    int stepV(Direction d) {
        return d == B ? 1 : d == F ? -1 : 0;
    }

    int gridIndex(int u, int v) {
        return u * D + v;
    }

    byte cell(int k, int u, int v) {
        if (k < 0 || k >= MAX_STOREYS || type[k] == null || !inside(u, v)) return OUT;
        return type[k][gridIndex(u, v)];
    }

    // ==========================================================================================
    // Rooms
    // ==========================================================================================

    /** One room of one storey: one or more rectangles (each including its walls) and its finishes. */
    static final class Room {
        final int id;
        final int type;
        final int storey;
        final List<int[]> rects = new ArrayList<>();
        int floor;
        int paper;
        int lower;

        Room(int id, int type, int storey) {
            this.id = id;
            this.type = type;
            this.storey = storey;
        }

        int[] main() {
            return rects.get(0);
        }
    }

    Room room(int k, int roomType, int u0, int v0, int u1, int v1) {
        Room r = new Room(rooms.size(), roomType, k);
        r.rects.add(new int[] {Math.min(u0, u1), Math.min(v0, v1), Math.max(u0, u1), Math.max(v0, v1)});
        long rb = bits(r.id, k, 401);
        int paper = k == 0 ? paperGround : paperUpper;
        switch (roomType) {
            case KITCHEN -> {
                r.floor = LINO;
                r.paper = (paper + 1 + Hash.range(rb, 2)) % 5;
                r.lower = Level39HouseWallBlock.BEADBOARD;
            }
            case BATH -> {
                r.floor = BATH_TILE;
                r.paper = Hash.range(rb, 2) == 0 ? 2 : 4;
                r.lower = Level39HouseWallBlock.BEADBOARD;
            }
            case BED -> {
                r.floor = floorMain;
                r.paper = Hash.range(rb, 5);
                r.lower = Level39HouseWallBlock.BASE + r.paper;
            }
            case GARAGE, MUDROOM -> {
                r.floor = roomType == GARAGE ? CONCRETE : LINO;
                r.paper = Level39HouseWallBlock.PLASTER;
                r.lower = roomType == GARAGE ? Level39HouseWallBlock.PLASTER : Level39HouseWallBlock.BEADBOARD;
            }
            case PARLOR, DINING, HALL, LANDING -> {
                r.floor = floorMain;
                r.paper = paper;
                r.lower = old ? Level39HouseWallBlock.WAINSCOT : Level39HouseWallBlock.BASE + paper;
            }
            default -> {
                r.floor = floorMain;
                r.paper = paper;
                r.lower = Level39HouseWallBlock.BASE + paper;
            }
        }
        rooms.add(r);
        return r;
    }

    void door(int k, int u, int v) {
        doorSpecs.add(new int[] {k, u, v});
    }

    /**
     * Lays out one storey around a hall at the front door: the hall (with the staircase along one
     * side) runs from the front wall toward the back; to its left and right a front and a back
     * room; behind the hall one more small room. Zones too narrow for a room join the hall.
     *
     * @return the hall room
     */
    Room zoned(int k, int bu0, int bu1, int fvv, int bv, int hallEnd, int lf, int lb, int rf, int rb, int hbType) {
        int hu0 = du - 2;
        int hu1 = du + 2;
        boolean left = hu0 - bu0 - 1 >= 2;
        boolean right = bu1 - hu1 - 1 >= 2;
        int hallU0 = left ? hu0 : bu0;
        int hallU1 = right ? hu1 : bu1;
        hallEnd = Math.min(hallEnd, bv);
        if (bv - hallEnd < 3) hallEnd = bv;
        Room hall = room(k, k == 0 ? HALL : LANDING, hallU0, fvv, hallU1, hallEnd);
        int su = stairColumn();
        if (left) zone(k, bu0, hu0, fvv, bv, hallEnd, lf, lb, hu0, su < du, 0);
        if (right) zone(k, hu1, bu1, fvv, bv, hallEnd, rf, rb, hu1, su > du, 1);
        if (hallEnd < bv && hbType >= 0) {
            room(k, hbType, hallU0, hallEnd, hallU1, bv);
            door(k, du, hallEnd);
        }
        return hall;
    }

    private void zone(int k, int zu0, int zu1, int fvv, int bv, int hallEnd, int frontType, int backType, int hallWallU,
                      boolean stairSide, int salt) {
        int depth = bv - fvv - 1;
        int zoneMid = (zu0 + zu1) / 2;
        if (depth >= 7 && backType >= 0) {
            int m = fvv + 1 + depth / 2 + Hash.range(bits(k, salt, 402), 3) - 1;
            m = Math.max(fvv + 4, Math.min(bv - 4, m));
            Room front = room(k, frontType, zu0, fvv, zu1, m);
            Room back = room(k, backType, zu0, m, zu1, bv);
            hallDoor(k, front, hallWallU, fvv, hallEnd, stairSide);
            if (m + 1 <= hallEnd - 1) {
                hallDoor(k, back, hallWallU, fvv, hallEnd, stairSide);
            } else {
                door(k, zoneMid, m);
            }
            if (Hash.unit(bits(k, salt, 403)) < 0.5 && m + 1 <= hallEnd - 1) {
                door(k, zoneMid, m); // a second way through, between the two rooms
            }
        } else {
            Room only = room(k, frontType, zu0, fvv, zu1, bv);
            hallDoor(k, only, hallWallU, fvv, hallEnd, stairSide);
        }
    }

    /** A door in the hall wall into room r, where the stairs don't block it. */
    private void hallDoor(int k, Room r, int wallU, int fvv, int hallEnd, boolean stairSide) {
        int[] rr = r.main();
        int lo = Math.max(rr[1] + 1, fvv + 1);
        int hi = Math.min(rr[3] - 1, hallEnd - 1);
        if (lo > hi) return;
        int want = (rr[1] + rr[3]) / 2;
        int best = Integer.MIN_VALUE;
        for (int v = lo; v <= hi; v++) {
            if (stairSide && stairsWanted && v > fvv + 1 && v < fvv + P + 2) continue;
            if (best == Integer.MIN_VALUE || Math.abs(v - want) < Math.abs(best - want)) best = v;
        }
        if (best != Integer.MIN_VALUE) door(k, wallU, best);
    }

    int stairColumn() {
        return du + (((hb >>> 7) & 1) == 0 ? 1 : -1);
    }

    // ==========================================================================================
    // Roofs
    // ==========================================================================================

    /** One roof plane: rises from its eave line (where d = 0) inward at pitch2 half-blocks per block. */
    record Plane(int side, int line, int eaveY, int pitch2) {
        /** Sides: 0 = eave at the front (-v), 1 = right (+u), 2 = back (+v), 3 = left (-u). */
        int d(int u, int v) {
            return switch (side) {
                case 0 -> v - line;
                case 1 -> line - u;
                case 2 -> line - v;
                default -> u - line;
            };
        }
    }

    /**
     * A roof shape over a rectangle: the lowest of its planes at each column (a gable is two
     * planes, a hip four, a shed one). Group 0 roofs (the main roof, cross gables, dormers, the
     * turret) melt into each other (the highest wins at every column). Other groups (porches,
     * lean-tos, bays, the wing) are drawn only where nothing else is.
     */
    static final class Prim {
        final int u0;
        final int v0;
        final int u1;
        final int v1;
        final int group;
        final Plane[] planes;

        Prim(int u0, int v0, int u1, int v1, int group, Plane... planes) {
            this.u0 = Math.min(u0, u1);
            this.v0 = Math.min(v0, v1);
            this.u1 = Math.max(u0, u1);
            this.v1 = Math.max(v0, v1);
            this.group = group;
            this.planes = planes;
        }
    }

    Prim prim(int u0, int v0, int u1, int v1, int group, Plane... planes) {
        Prim p = new Prim(u0, v0, u1, v1, group, planes);
        prims.add(p);
        return p;
    }

    Direction planeFacing(Plane p) {
        return switch (p.side()) {
            case 0 -> B;
            case 1 -> L;
            case 2 -> F;
            default -> R;
        };
    }

    /** Level of a prim at a column in half blocks; fills tie[0] with a bit per lowest plane. */
    private int primLevel(Prim p, int u, int v, int[] tie) {
        int best = Integer.MAX_VALUE;
        int mask = 0;
        for (int i = 0; i < p.planes.length; i++) {
            Plane pl = p.planes[i];
            int l = 2 * pl.eaveY() + pl.pitch2() * pl.d(u, v);
            if (l < best) {
                best = l;
                mask = 1 << i;
            } else if (l == best) {
                mask |= 1 << i;
            }
        }
        tie[0] = mask;
        return best;
    }

    void drawRoofs() {
        int n = W * D;
        rTop = new int[n];
        rBot = new int[n];
        topPrim = new int[n];
        java.util.Arrays.fill(rTop, Integer.MIN_VALUE);
        java.util.Arrays.fill(rBot, Integer.MIN_VALUE);
        java.util.Arrays.fill(topPrim, -1);
        int[] tie = new int[1];
        int[] level0 = new int[n];
        int[] tie0 = new int[n];
        java.util.Arrays.fill(level0, Integer.MIN_VALUE);
        mainLevel = new int[n];
        java.util.Arrays.fill(mainLevel, Integer.MIN_VALUE);
        valley = new Direction[n];
        // Group 0: highest wins.
        for (int pi = 0; pi < prims.size(); pi++) {
            Prim p = prims.get(pi);
            if (p.group != 0) continue;
            boolean dormer = dormerCores.contains(p);
            for (int u = Math.max(0, p.u0); u <= Math.min(W - 1, p.u1); u++) {
                for (int v = Math.max(0, p.v0); v <= Math.min(D - 1, p.v1); v++) {
                    int l = primLevel(p, u, v, tie);
                    int i = gridIndex(u, v);
                    if (l > level0[i]) {
                        level0[i] = l;
                        tie0[i] = tie[0];
                        topPrim[i] = pi;
                        valley[i] = null;
                    } else if (l == level0[i] && topPrim[i] != pi && Integer.bitCount(tie[0]) == 1) {
                        // Two roofs meet at the same height: a valley runs through this column.
                        valley[i] = planeFacing(p.planes[Integer.numberOfTrailingZeros(tie[0])]);
                    }
                    if (!dormer && l > mainLevel[i]) mainLevel[i] = l;
                }
            }
        }
        for (int u = 0; u < W; u++) {
            for (int v = 0; v < D; v++) {
                int i = gridIndex(u, v);
                if (topPrim[i] >= 0) {
                    renderRoofColumn(u, v, prims.get(topPrim[i]), level0[i], tie0[i], false, valley[i]);
                }
            }
        }
        // Other groups: only where nothing else stands.
        for (Prim p : prims) {
            if (p.group == 0) continue;
            for (int u = Math.max(0, p.u0); u <= Math.min(W - 1, p.u1); u++) {
                for (int v = Math.max(0, p.v0); v <= Math.min(D - 1, p.v1); v++) {
                    int l = primLevel(p, u, v, tie);
                    renderRoofColumn(u, v, p, l, tie[0], true, null);
                }
            }
        }
    }

    /** Per column: the other roof's slope direction where two group-0 roofs meet in a valley. */
    private Direction[] valley;

    /**
     * Draws the roof piece of one column: which smooth piece (plain slope, hip corner, valley,
     * ridge, ridge end, pyramid top) follows from which slopes are lowest here (see
     * {@link com.rickr.backrooms.block.Level39RoofBlock}). Its height and, for the low roof,
     * which half of the block, come from the level in half blocks.
     */
    private void renderRoofColumn(int u, int v, Prim p, int level2, int mask, boolean onlyEmpty, Direction valleyOther) {
        int first = Integer.numberOfTrailingZeros(mask);
        Plane p0 = p.planes[first];
        Direction f = planeFacing(p0);
        int ties = Integer.bitCount(mask);
        int shape = SHAPE_STRAIGHT;
        if (ties == 2) {
            Plane p1 = p.planes[Integer.numberOfTrailingZeros(mask & ~(1 << first))];
            Direction f1 = planeFacing(p1);
            if (f1 == f.getOpposite()) {
                shape = SHAPE_RIDGE;
            } else {
                shape = SHAPE_OUTER;
                // OUTER means "the slope rising toward FACING meets the one rising toward its left".
                if (f1 != f.getCounterClockWise()) f = f1;
            }
        } else if (ties == 3) {
            shape = SHAPE_RIDGE_END;
            int seen = 0;
            for (int i = 0; i < p.planes.length; i++) {
                if ((mask & (1 << i)) != 0) seen |= 1 << planeFacing(p.planes[i]).get2DDataValue();
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if ((seen & (1 << d.get2DDataValue())) == 0) f = d.getOpposite();
            }
        } else if (ties > 3) {
            shape = SHAPE_APEX;
        } else if (valleyOther != null && valleyOther.getAxis() != f.getAxis()) {
            shape = SHAPE_INNER;
            if (valleyOther != f.getCounterClockWise()) f = valleyOther;
        }
        boolean low = p0.pitch2() == 1;
        int y = low ? level2 >> 1 : level2 / 2;
        boolean upper = low && (level2 & 1) != 0;
        boolean eave = p0.d(u, v) == 0 && (shape == SHAPE_STRAIGHT || shape == SHAPE_OUTER);
        int code = withShade(withParam(code(low ? ROOF_LOW : ROOF, f), shape), shade)
                | (upper ? SECOND : 0) | ((eave ? TRIM_EAVE : TRIM_NONE) << TRIM_SHIFT);
        if (onlyEmpty) {
            int old = at(u, v, y);
            if (old != EMPTY && old != AIR) return;
        }
        put(u, v, y, code);
        int gi = gridIndex(u, v);
        if (y > rTop[gi]) {
            rTop[gi] = y;
            rBot[gi] = y;
        }
    }

    // Level39RoofBlock.Shape and Trim ordinals.
    static final int SHAPE_STRAIGHT = 0;
    static final int SHAPE_OUTER = 1;
    static final int SHAPE_INNER = 2;
    static final int SHAPE_RIDGE = 3;
    static final int SHAPE_RIDGE_END = 4;
    static final int SHAPE_APEX = 5;
    static final int TRIM_NONE = 0;
    static final int TRIM_EAVE = 1;
    static final int TRIM_GABLE = 2;

    /** The topmost storey drawn at a column, or -1. */
    int topStorey(int u, int v) {
        for (int k = storeys - 1; k >= 0; k--) {
            if (cell(k, u, v) != OUT) return k;
        }
        return -1;
    }

    /**
     * Roof pieces sitting on a wall get the clapboard filling under their slope (clean gable
     * triangles, dormer cheeks and closed eaves) and a white rake board.
     */
    void gableRoofPieces() {
        for (int u = 0; u < W; u++) {
            for (int v = 0; v < D; v++) {
                for (int y = Y0 + 1; y < Y0 + CH; y++) {
                    int code = at(u, v, y);
                    int k = kind(code);
                    if (k != ROOF && k != ROOF_LOW) continue;
                    int shapeOrd = param(code);
                    if (shapeOrd != SHAPE_STRAIGHT && shapeOrd != SHAPE_RIDGE) continue;
                    int below = kind(at(u, v, y - 1));
                    if (below != EXTWALL && below != TRIM && below != CLAP && below != KNOCK) continue;
                    put(u, v, y, (code & ~(3 << TRIM_SHIFT)) | (TRIM_GABLE << TRIM_SHIFT));
                }
            }
        }
    }

    /**
     * After the roofs: walls grow up into the gables, everything above a roof is cut away, and
     * where an upper room is too low under the slope a knee wall closes it off.
     */
    void fitUnderRoofs() {
        for (int u = 0; u < W; u++) {
            for (int v = 0; v < D; v++) {
                int gi = gridIndex(u, v);
                if (rTop[gi] == Integer.MIN_VALUE) continue;
                int k = topStorey(u, v);
                if (k >= 0 && cell(k, u, v) == WALL) {
                    Direction out = outward(k, u, v);
                    if (out != null) {
                        int plate = fy(k + 1);
                        // Corner boards stop at the eaves: above them the gable is plain clapboard,
                        // so its edge is the smooth rake of the roof, not a white staircase.
                        for (int y = plate + 1; y < rBot[gi]; y++) {
                            if (at(u, v, y) != EMPTY) continue;
                            put(u, v, y, withShade(withParam(code(EXTWALL, out), Level39HouseWallBlock.PLASTER), shade));
                        }
                    }
                }
                // Cut away everything above the roof.
                for (int y = rTop[gi] + 1; y < Y0 + CH; y++) {
                    put(u, v, y, EMPTY);
                }
                // Knee walls where an upper room has less than two blocks under the slope.
                for (int kk = 1; kk < storeys; kk++) {
                    if (cell(kk, u, v) != FLOOR) continue;
                    int head = rBot[gi] - 1 - fy(kk);
                    if (head >= 1 && head < 2) {
                        for (int y = fy(kk) + 1; y < rBot[gi]; y++) {
                            put(u, v, y, withParam(code(INWALL), Level39HouseWallBlock.PLASTER));
                        }
                        reserved[kk][gi] = true;
                    } else if (head < 1) {
                        reserved[kk][gi] = true;
                    }
                }
            }
        }
        // Gable windows: the middle of every gable that rose at least three blocks.
        for (int u = 0; u < W; u++) {
            for (int v = 0; v < D; v++) {
                int k = topStorey(u, v);
                if (k < 0 || cell(k, u, v) != WALL || isCorner(k, u, v)) continue;
                Direction out = outward(k, u, v);
                if (out == null) continue;
                int gable = gableHeight(k, u, v);
                if (gable < 3) continue;
                int au = stepU(out) == 0 ? 1 : 0;
                int av = au == 0 ? 1 : 0;
                int a = gableHeight(k, u - au, v - av);
                int b = gableHeight(k, u + au, v + av);
                if (gable >= a && gable >= b && (gable > a || gable > b)) {
                    int y = fy(k + 1) + 2;
                    put(u, v, y, windowCode(out, u, v, y));
                }
            }
        }
        // Doors whose top was cut off by a roof are walled up.
        for (int u = 0; u < W; u++) {
            for (int v = 0; v < D; v++) {
                for (int y = Y0; y < Y0 + CH - 1; y++) {
                    int code = at(u, v, y);
                    if (isDoor(code) && (code & SECOND) == 0 && !isDoor(at(u, v, y + 1))) {
                        put(u, v, y, withParam(code(INWALL), Level39HouseWallBlock.PLASTER));
                    }
                }
            }
        }
    }

    private int gableHeight(int k, int u, int v) {
        if (!inside(u, v) || cell(k, u, v) != WALL) return -1;
        int gi = gridIndex(u, v);
        if (rBot[gi] == Integer.MIN_VALUE) return -1;
        return rBot[gi] - 1 - fy(k + 1);
    }

    // ==========================================================================================
    // Storeys
    // ==========================================================================================

    void rasterize() {
        type = new byte[MAX_STOREYS][];
        roomOf = new short[MAX_STOREYS][];
        reserved = new boolean[MAX_STOREYS][];
        for (int k = 0; k < storeys; k++) {
            type[k] = new byte[W * D];
            roomOf[k] = new short[W * D];
            reserved[k] = new boolean[W * D];
            java.util.Arrays.fill(roomOf[k], (short) -1);
        }
        for (Room r : rooms) {
            for (int[] rect : r.rects) {
                for (int u = Math.max(0, rect[0]); u <= Math.min(W - 1, rect[2]); u++) {
                    for (int v = Math.max(0, rect[1]); v <= Math.min(D - 1, rect[3]); v++) {
                        boolean edge = u == rect[0] || u == rect[2] || v == rect[1] || v == rect[3];
                        int i = gridIndex(u, v);
                        if (!edge) continue;
                        if (type[r.storey][i] == OUT) {
                            type[r.storey][i] = WALL;
                            roomOf[r.storey][i] = (short) r.id;
                        }
                    }
                }
            }
        }
        // Room insides win over walls: overlapping rectangles of one room become one space.
        for (Room r : rooms) {
            for (int[] rect : r.rects) {
                for (int u = Math.max(0, rect[0] + 1); u <= Math.min(W - 1, rect[2] - 1); u++) {
                    for (int v = Math.max(0, rect[1] + 1); v <= Math.min(D - 1, rect[3] - 1); v++) {
                        int i = gridIndex(u, v);
                        type[r.storey][i] = FLOOR;
                        roomOf[r.storey][i] = (short) r.id;
                    }
                }
            }
        }
        for (int[] d : doorSpecs) {
            int k = d[0];
            if (k >= storeys || !inside(d[1], d[2])) continue;
            int i = gridIndex(d[1], d[2]);
            if (type[k][i] != WALL || outward(k, d[1], d[2]) != null) continue;
            // Only a wall between two floors makes a doorway (not the end of a wall).
            boolean alongU = cell(k, d[1], d[2] - 1) == FLOOR && cell(k, d[1], d[2] + 1) == FLOOR;
            boolean alongV = cell(k, d[1] - 1, d[2]) == FLOOR && cell(k, d[1] + 1, d[2]) == FLOOR;
            if (!alongU && !alongV) continue;
            type[k][i] = DOORWAY;
            // Keep the floor either side of a door clear of furniture.
            if (alongU) {
                reserve(k, d[1], d[2] - 1);
                reserve(k, d[1], d[2] + 1);
            } else {
                reserve(k, d[1] - 1, d[2]);
                reserve(k, d[1] + 1, d[2]);
            }
        }
    }

    void reserve(int k, int u, int v) {
        if (k >= 0 && k < storeys && inside(u, v)) reserved[k][gridIndex(u, v)] = true;
    }

    boolean free(int k, int u, int v) {
        return cell(k, u, v) == FLOOR && !reserved[k][gridIndex(u, v)];
    }

    /** The side of an outside wall cell that faces out, or null for inside walls. */
    Direction outward(int k, int u, int v) {
        if (cell(k, u, v - 1) == OUT) return F;
        if (cell(k, u, v + 1) == OUT) return B;
        if (cell(k, u + 1, v) == OUT) return R;
        if (cell(k, u - 1, v) == OUT) return L;
        return null;
    }

    boolean isCorner(int k, int u, int v) {
        boolean alongV = cell(k, u, v - 1) == OUT || cell(k, u, v + 1) == OUT;
        boolean alongU = cell(k, u - 1, v) == OUT || cell(k, u + 1, v) == OUT;
        return alongV && alongU;
    }

    /** The room behind an outside wall cell (the floor next to it on the inside). */
    Room roomBehind(int k, int u, int v, Direction out) {
        int iu = u - stepU(out);
        int iv = v - stepV(out);
        if (cell(k, iu, iv) == FLOOR || cell(k, iu, iv) == DOORWAY) {
            short id = roomOf[k][gridIndex(iu, iv)];
            if (id >= 0) return rooms.get(id);
        }
        short id = roomOf[k][gridIndex(u, v)];
        return id >= 0 ? rooms.get(id) : null;
    }

    int finish(Room r, int row) {
        if (r == null) return Level39HouseWallBlock.PLASTER;
        return row == 1 ? r.lower : r.paper;
    }

    void drawStoreys() {
        for (int k = 0; k < storeys; k++) {
            int y0 = fy(k);
            int top = fy(k + 1);
            for (int u = 0; u < W; u++) {
                for (int v = 0; v < D; v++) {
                    byte t = cell(k, u, v);
                    if (t == OUT) continue;
                    int gi = gridIndex(u, v);
                    Room r = roomOf[k][gi] >= 0 ? rooms.get(roomOf[k][gi]) : null;
                    int floor = r != null ? r.floor : floorMain;
                    if (t == FLOOR) {
                        put(u, v, y0, code(floor));
                    } else if (t == WALL) {
                        Direction out = outward(k, u, v);
                        if (out != null) {
                            boolean corner = isCorner(k, u, v);
                            put(u, v, y0, k == 0 ? (h.anomaly() == Level39HouseSpec.Anomaly.FLOATING ? code(TRIM) : foundation())
                                    : corner || belt ? code(TRIM) : extWall(out, roomBehind(k, u, v, out), 2));
                            Room in = roomBehind(k, u, v, out);
                            for (int y = y0 + 1; y < top; y++) {
                                put(u, v, y, corner ? code(TRIM) : extWall(out, in, y - y0));
                            }
                        } else {
                            put(u, v, y0, code(floor));
                            for (int y = y0 + 1; y < top; y++) {
                                put(u, v, y, withParam(code(INWALL), finish(r, y - y0)));
                            }
                        }
                    } else {
                        // A doorway: an interior door, wall above it.
                        put(u, v, y0, code(floor));
                        boolean alongU = cell(k, u, v - 1) == FLOOR;
                        Direction facing = alongU ? B : R;
                        int door = code(INT_DOOR, facing) | (Hash.unit(bits(u, v, 404 + k)) < 0.5 ? HINGE : 0);
                        put(u, v, y0 + 1, door);
                        put(u, v, y0 + 2, door | SECOND);
                        for (int y = y0 + 3; y < top; y++) {
                            put(u, v, y, withParam(code(INWALL), finish(r, y - y0)));
                        }
                    }
                    // The ceiling, unless the storey above draws its floor here.
                    if (cell(k + 1, u, v) == OUT) {
                        if (t == WALL && outward(k, u, v) != null) {
                            put(u, v, top, code(TRIM));
                        } else {
                            put(u, v, top, code(POPCORN));
                        }
                    }
                }
            }
        }
    }

    int foundation() {
        return code(style == Level39HouseSpec.STYLE_VICTORIAN || style == Level39HouseSpec.STYLE_RANCH ? BRICK : FOUNDATION);
    }

    int extWall(Direction out, Room in, int row) {
        return withShade(withParam(code(EXTWALL, out), finish(in, row)), shade);
    }

    int windowCode(Direction out, int u, int v, int y) {
        double r = unit(u * 7 + v, y, 405);
        return code(r < 0.28 ? WINDOW_SHADE : WINDOW, out);
    }

    /** Sash windows on every outside wall, spaced to suit the room behind. */
    void drawWindows() {
        for (Room r : rooms) {
            int k = r.storey;
            if (halfTop && k == storeys - 1) continue; // the half storey only has gable and dormer windows
            if (r.type == GARAGE) continue;
            for (int[] rect : r.rects) {
                for (int side = 0; side < 4; side++) {
                    // Walk the side of the rectangle, collecting runs of plain outside wall.
                    int len = side % 2 == 0 ? rect[2] - rect[0] + 1 : rect[3] - rect[1] + 1;
                    int runStart = -1;
                    for (int i = 0; i <= len; i++) {
                        boolean ok = false;
                        int u = 0;
                        int v = 0;
                        if (i < len) {
                            u = side == 0 ? rect[0] + i : side == 2 ? rect[0] + i : side == 1 ? rect[2] : rect[0];
                            v = side == 0 ? rect[1] : side == 2 ? rect[3] : rect[1] + i;
                            ok = cell(k, u, v) == WALL && outward(k, u, v) != null && !isCorner(k, u, v)
                                    && roomBehind(k, u, v, outward(k, u, v)) == r;
                        }
                        if (ok && runStart < 0) runStart = i;
                        if (!ok && runStart >= 0) {
                            placeRunWindows(r, rect, side, runStart, i - 1);
                            runStart = -1;
                        }
                    }
                }
            }
        }
    }

    private void placeRunWindows(Room r, int[] rect, int side, int from, int to) {
        int n = to - from + 1;
        int k = r.storey;
        int[] picks;
        if (n >= 7) {
            picks = new int[] {from + n / 4, to - n / 4};
        } else if (n >= 2 || r.type == BATH) {
            picks = new int[] {from + (n - 1) / 2};
        } else {
            return;
        }
        for (int i : picks) {
            int u = side == 0 || side == 2 ? rect[0] + i : side == 1 ? rect[2] : rect[0];
            int v = side == 0 ? rect[1] : side == 2 ? rect[3] : rect[1] + i;
            Direction out = outward(k, u, v);
            if (out == null) continue;
            if (k == 0 && out == F && Math.abs(u - du) <= 1 && v == fv) continue;
            boolean picture = r.type == LIVING && style == Level39HouseSpec.STYLE_RANCH && out == F;
            int y0 = fy(k);
            int rows = r.type == BATH ? 1 : 2;
            for (int y = y0 + 2; y < y0 + 2 + rows && y < fy(k + 1); y++) {
                boolean lit = litWindowsLeft > 0 && k == 0 && out == F;
                put(u, v, y, lit ? code(WINDOW_LIT, out) : windowCode(out, u, v, y0));
                if (picture) {
                    // A ranch's wide picture window: three sashes side by side.
                    for (int s = -1; s <= 1; s += 2) {
                        int pu = u + s * stepU(R) * (out.getAxis() == R.getAxis() ? 0 : 1);
                        if (cell(k, pu, v) == WALL && outward(k, pu, v) == out && !isCorner(k, pu, v)) {
                            put(pu, v, y, code(WINDOW, out));
                        }
                    }
                }
            }
            if (litWindowsLeft > 0 && k == 0 && out == F) litWindowsLeft--;
        }
    }

    /** The front door in the middle of the front wall (mostly locked, sometimes a fake). */
    void drawFrontDoor() {
        int v = fv;
        if (cell(0, du, v) != WALL) return;
        int y0 = F0;
        Level39HouseSpec.Anomaly a = h.anomaly();
        double roll = Hash.unit(bits(du, v, 406));
        boolean bigger = a == Level39HouseSpec.Anomaly.BIGGER_INSIDE && primary;
        boolean locked = !bigger && (roll < 0.68 || !primary);
        boolean fake = !bigger && !locked && roll < 0.86;
        int door = code(FRONT_DOOR, F) | (locked ? FLAG : 0) | (Hash.unit(bits(du, v, 407)) < 0.5 ? HINGE : 0);
        put(du, v, y0 + 1, door);
        put(du, v, y0 + 2, door | SECOND);
        put(du, v, y0 + 3, code(TRIM));
        // The door case: white pilasters either side.
        for (int s = -1; s <= 1; s += 2) {
            if (cell(0, du + s, v) == WALL && outward(0, du + s, v) == F && !isCorner(0, du + s, v)) {
                for (int y = y0 + 1; y <= y0 + 3; y++) put(du + s, v, y, code(TRIM));
            }
        }
        reserve(0, du, v + 1);
        reserve(0, du, v + 2);
        if (fake) {
            // It opens - onto a wall. Canon: the doors are mostly fake or locked.
            Room in = roomBehind(0, du, v, F);
            put(du, v + 1, y0 + 1, withParam(code(INWALL), finish(in, 1)));
            put(du, v + 1, y0 + 2, withParam(code(INWALL), finish(in, 2)));
        }
    }

    // ==========================================================================================
    // Stairs, chimneys, porches, dormers, cellar
    // ==========================================================================================

    /** Straight stairs up from storey k: steps toward +v from (su, sv0), the last one in the upper floor. */
    void drawStairs() {
        for (int[] s : stairsList) {
            int k = s[0];
            int su = s[1];
            int sv0 = s[2];
            int floorRoom = roomOf[k][gridIndex(su, sv0)];
            Room r = floorRoom >= 0 ? rooms.get(floorRoom) : null;
            for (int i = 0; i < P; i++) {
                int v = sv0 + i;
                int y = fy(k) + 1 + i;
                put(su, v, y, code(OAK_STAIR, B));
                // Under the stairs: closed in, papered like the hall.
                for (int yy = fy(k) + 1; yy < y; yy++) {
                    put(su, v, yy, withParam(code(INWALL), finish(r, yy - fy(k))));
                }
                for (int yy = y + 1; yy <= y + 2 && yy < fy(k + 1); yy++) {
                    put(su, v, yy, EMPTY);
                }
                reserve(k, su, v);
                if (i < P - 1) {
                    put(su, v, fy(k + 1), AIR);
                }
                reserve(k + 1, su, v);
            }
            reserve(k, su, sv0 - 1);
            reserve(k + 1, su, sv0 + P);
            // Banister along the open side of the stairwell upstairs.
            int ru = su + (su > du ? -1 : 1);
            for (int i = 0; i < P - 1; i++) {
                int v = sv0 + i;
                if (cell(k + 1, ru, v) != FLOOR) continue;
                int sides = railSides(ru, v, ru, sv0, sv0 + P - 2);
                put(ru, v, fy(k + 1) + 1, withParam(code(RAIL), sides));
                reserve(k + 1, ru, v);
            }
        }
    }

    /** Rail connections (world N/E/S/W bits) for a rail running along v from v0 to v1 at column u. */
    private int railSides(int u, int v, int railU, int v0, int v1) {
        int bitsOut = 0;
        if (v > v0) bitsOut |= worldBit(F);
        if (v < v1) bitsOut |= worldBit(B);
        return bitsOut;
    }

    static int worldBit(Direction d) {
        return switch (d) {
            case NORTH -> 1;
            case EAST -> 2;
            case SOUTH -> 4;
            default -> 8;
        };
    }

    void drawChimneys() {
        for (int[] ch : chimneys) {
            int u = ch[0];
            int v = ch[1];
            if (!inside(u, v)) continue;
            int top = Integer.MIN_VALUE;
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (inside(u + a, v + b)) top = Math.max(top, rTop[gridIndex(u + a, v + b)]);
                }
            }
            if (top == Integer.MIN_VALUE) top = fy(storeys);
            for (int y = ch[2]; y <= top + 2; y++) {
                put(u, v, y, code(BRICK));
            }
            for (int k = 0; k < storeys; k++) reserve(k, u, v);
        }
    }

    void drawPorch() {
        for (int[] p : porchFloors) {
            for (int u = p[0]; u <= p[2]; u++) {
                for (int v = p[1]; v <= p[3]; v++) {
                    if (!inside(u, v) || at(u, v, F0) != EMPTY) continue;
                    put(u, v, F0, code(PORCH_PLANKS));
                }
            }
        }
        for (int[] s : porchSteps) {
            put(s[0], s[1], F0, code(PORCH_STAIR, B));
        }
        for (int[] p : porchPosts) {
            int u = p[0];
            int v = p[1];
            int yTop = p[2];
            int y = F0 + 1;
            if (porchPostKind == POST_CRAFT) {
                put(u, v, y++, code(FOUNDATION));
            }
            for (; y <= yTop; y++) {
                if (at(u, v, y) == EMPTY) put(u, v, y, code(porchPostKind));
            }
        }
        for (int[] r : porchRails) {
            int u = r[0];
            int v = r[1];
            if (at(u, v, F0 + 1) != EMPTY) continue;
            int sides = 0;
            for (Direction d : new Direction[] {F, B, L, R}) {
                int nu = u + stepU(d);
                int nv = v + stepV(d);
                if (isRailOrPost(nu, nv)) sides |= worldBit(d);
            }
            put(u, v, F0 + 1, withParam(code(RAIL), sides));
        }
        if (porchBeamY != Integer.MIN_VALUE) {
            for (int[] p : porchPosts) {
                // A beam along the front of a Craftsman porch, under the low eave.
                for (int u = 0; u < W; u++) {
                    if (at(u, p[1], porchBeamY) == EMPTY && isPorchFront(u, p[1])) put(u, p[1], porchBeamY, code(TRIM));
                }
            }
        }
    }

    private boolean isPorchFront(int u, int v) {
        for (int[] p : porchFloors) {
            if (u >= p[0] && u <= p[2] && v == p[1]) return true;
        }
        return false;
    }

    private boolean isRailOrPost(int u, int v) {
        for (int[] r : porchRails) if (r[0] == u && r[1] == v) return true;
        for (int[] p : porchPosts) if (p[0] == u && p[1] == v) return true;
        return false;
    }

    /** Dormer cheeks (the little side walls) and windows, after the roofs are drawn. */
    void drawDormers() {
        for (Prim core : dormerCores) {
            int k = storeys - 1;
            for (int v = core.v0; v <= core.v1; v++) {
                for (int u : new int[] {core.u0, core.u1}) {
                    if (!inside(u, v) || v <= fv) continue;
                    int gi = gridIndex(u, v);
                    if (topPrim[gi] < 0 || prims.get(topPrim[gi]) != core) continue;
                    if (cell(k, u, v) == OUT) continue;
                    Direction out = u == core.u0 ? L : R;
                    // Only the triangle between the main slope and the dormer roof: below the
                    // slope the room simply carries on under the main roof.
                    int from = fy(k) + 1;
                    if (mainLevel[gi] != Integer.MIN_VALUE) from = Math.max(from, mainLevel[gi] >> 1);
                    for (int y = from; y < rBot[gi]; y++) {
                        put(u, v, y, withShade(withParam(code(EXTWALL, out), Level39HouseWallBlock.PLASTER), shade));
                    }
                    reserve(k, u, v);
                }
            }
        }
        for (int[] w : dormerWindows) {
            int k = storeys - 1;
            int u = w[0];
            int v = w[1];
            if (cell(k, u, v) != WALL) continue;
            for (int y = fy(k) + 2; y <= fy(k) + 3; y++) {
                if (isWall(at(u, v, y)) && y < rBot[gridIndex(u, v)]) put(u, v, y, windowCode(F, u, v, y));
            }
        }
    }

    /** A fieldstone cellar under the main body, reached by a hatch and a ladder from the kitchen or hall. */
    void drawCellar() {
        if (!basement) return;
        int b0 = F0 - 5;
        int u0 = bodyU0;
        int u1 = bodyU1;
        int v0 = fv;
        int v1 = bodyV1;
        for (int u = u0; u <= u1; u++) {
            for (int v = v0; v <= v1; v++) {
                boolean edge = u == u0 || u == u1 || v == v0 || v == v1;
                put(u, v, b0, code(edge ? FOUNDATION : CONCRETE));
                for (int y = b0 + 1; y < F0; y++) {
                    put(u, v, y, edge ? code(FOUNDATION) : AIR);
                }
                if (!edge && at(u, v, F0) == EMPTY) put(u, v, F0, code(floorMain));
            }
        }
        // The way down: a hatch in a corner of the kitchen (or the hall) against the outside wall.
        int[] spot = null;
        for (Room r : rooms) {
            if (r.storey != 0 || (r.type != KITCHEN && r.type != MUDROOM && r.type != HALL)) continue;
            int[] rr = r.main();
            for (int u = rr[0] + 1; u <= rr[2] - 1 && spot == null; u++) {
                for (int v = rr[1] + 1; v <= rr[3] - 1 && spot == null; v++) {
                    if (!free(0, u, v) || u <= u0 || u >= u1 || v <= v0 || v >= v1) continue;
                    // next to the cellar wall: on the edge ring of the cellar
                    Direction toWall = null;
                    if (u == u0 + 1) toWall = L;
                    else if (u == u1 - 1) toWall = R;
                    else if (v == v1 - 1) toWall = B;
                    if (toWall == null) continue;
                    if (cell(0, u + stepU(toWall), v + stepV(toWall)) != WALL) continue;
                    spot = new int[] {u, v, toWall.get2DDataValue()};
                }
            }
            if (spot != null && r.type == KITCHEN) break;
        }
        if (spot == null) {
            basement = false;
            for (int u = u0; u <= u1; u++) {
                for (int v = v0; v <= v1; v++) {
                    for (int y = b0; y < F0; y++) put(u, v, y, EMPTY);
                }
            }
            return;
        }
        Direction toWall = Direction.from2DDataValue(spot[2]);
        put(spot[0], spot[1], F0, code(HATCH_TOP, toWall.getOpposite()));
        for (int y = b0 + 1; y < F0; y++) {
            put(spot[0], spot[1], y, code(LADDER, toWall.getOpposite()));
        }
        reserve(0, spot[0], spot[1]);
        cellarLadder = spot;
    }

    int[] cellarLadder;

    // ==========================================================================================
    // Knock walls
    // ==========================================================================================

    /**
     * Canon: the doors are locked or fake, and the way in is knocking a wall down. Every house gets
     * one to three rotten wall sections, two blocks high, in the walls of its bottom storey (for an
     * upside-down house, the storey that will end up at the bottom).
     */
    void placeKnockWalls(boolean forInverted) {
        int k = forInverted ? fullStoreys - 1 : 0;
        int yLow = forInverted ? fy(k + 1) - 2 : fy(k) + 1;
        List<int[]> spots = new ArrayList<>();
        for (int u = 0; u < W; u++) {
            for (int v = 0; v < D; v++) {
                if (cell(k, u, v) != WALL || isCorner(k, u, v)) continue;
                Direction out = outward(k, u, v);
                if (out == null) continue;
                if (kind(at(u, v, yLow)) != EXTWALL || kind(at(u, v, yLow + 1)) != EXTWALL) continue;
                int iu = u - stepU(out);
                int iv = v - stepV(out);
                if (!free(k, iu, iv)) continue;
                int ou = u + stepU(out);
                int ov = v + stepV(out);
                if (inside(ou, ov) && (at(ou, ov, F0 + 1) != EMPTY || at(ou, ov, yLow) != EMPTY)) continue;
                spots.add(new int[] {u, v, out.get2DDataValue()});
            }
        }
        if (spots.isEmpty()) return;
        int want = 1 + Hash.range(bits(0, 0, 408), 3);
        int start = Hash.range(bits(0, 0, 409), spots.size());
        int placed = 0;
        for (int i = 0; i < spots.size() && placed < want; i++) {
            int[] s = spots.get((start + i * 7) % spots.size());
            boolean clash = false;
            for (int j = 0; j < knockWalls.size(); j++) {
                int[] o = knockWalls.get(j);
                if (Math.abs(o[0] - s[0]) + Math.abs(o[1] - s[1]) < 4) clash = true;
            }
            if (clash || kind(at(s[0], s[1], yLow)) != EXTWALL) continue;
            Direction out = Direction.from2DDataValue(s[2]);
            int knock = withShade(code(KNOCK, out), shade);
            put(s[0], s[1], yLow, knock);
            put(s[0], s[1], yLow + 1, knock);
            reserve(k, s[0] - stepU(out), s[1] - stepV(out));
            knockWalls.add(s);
            placed++;
        }
    }

    final List<int[]> knockWalls = new ArrayList<>();

    // ==========================================================================================
    // Furniture and bulbs
    // ==========================================================================================

    /** A place against a wall: the cell, and the way furniture there looks (away from the wall). */
    record Slot(int u, int v, Direction facing) {}

    List<Slot> slots(Room r) {
        List<Slot> out = new ArrayList<>();
        int k = r.storey;
        for (int[] rect : r.rects) {
            for (int u = rect[0] + 1; u <= rect[2] - 1; u++) {
                for (int v = rect[1] + 1; v <= rect[3] - 1; v++) {
                    if (!free(k, u, v) || roomOf[k][gridIndex(u, v)] != r.id) continue;
                    for (Direction d : new Direction[] {F, R, B, L}) {
                        byte n = cell(k, u + stepU(d), v + stepV(d));
                        if (n == WALL && isWall(at(u + stepU(d), v + stepV(d), fy(k) + 1))) {
                            out.add(new Slot(u, v, d.getOpposite()));
                        }
                    }
                }
            }
        }
        return out;
    }

    boolean emptyAt(int u, int v, int y) {
        return at(u, v, y) == EMPTY;
    }

    void furnish() {
        for (Room r : rooms) {
            int[] pieces = switch (r.type) {
                case LIVING -> new int[] {SOFA, ARMCHAIR, SIDE_TABLE, RADIO, ARMCHAIR, BOOKSHELF, PICTURE, PICTURE};
                case PARLOR -> new int[] {PIANO, ARMCHAIR, SIDE_TABLE, BOOKSHELF, ARMCHAIR, PICTURE, PICTURE};
                case DINING -> new int[] {TABLE, KITCHEN_DRESSER, BOOKSHELF, PICTURE, PICTURE};
                case KITCHEN -> new int[] {KITCHEN_SINK, STOVE, COUNTER, COUNTER, ICEBOX, KITCHEN_DRESSER, SHELF, COUNTER};
                // (BED alone would be the room type here: the furniture code needs its full name.)
                case BED -> new int[] {Level39HousesBlocks.BED, WARDROBE, DRESSER, SIDE_TABLE, PICTURE};
                case BATH -> new int[] {TUB, TOILET, PED_SINK};
                case HALL, LANDING -> new int[] {PICTURE, SIDE_TABLE, PICTURE, SHELF};
                case TURRET -> new int[] {ARMCHAIR, SIDE_TABLE, PICTURE};
                case GARAGE -> new int[] {BOXES, SHELF, BOXES};
                case MUDROOM -> new int[] {SHELF, ICEBOX, BOXES};
                default -> new int[0];
            };
            List<Slot> slots = slots(r);
            int n = 0;
            for (int piece : pieces) {
                n++;
                if (piece == TABLE) {
                    diningTable(r);
                    continue;
                }
                if (r.type == KITCHEN && (piece == KITCHEN_SINK || piece == STOVE || piece == COUNTER)) {
                    kitchenLine(r, slots, piece);
                    continue;
                }
                if (slots.isEmpty()) break;
                int start = Hash.range(bits(r.id, n, 410), slots.size());
                for (int i = 0; i < slots.size(); i++) {
                    Slot s = slots.get((start + i) % slots.size());
                    if (placePiece(r, s, piece, n)) {
                        break;
                    }
                }
            }
        }
        if (basement && cellarLadder != null) furnishCellar();
    }

    /** Puts a piece at a slot (and in the odd-furniture houses, maybe somewhere odd). */
    boolean placePiece(Room r, Slot s, int piece, int n) {
        int k = r.storey;
        int y = fy(k) + 1;
        int ceiling = fy(k + 1);
        if (piece == PICTURE) {
            int py = fy(k) + 2;
            if (!emptyAt(s.u(), s.v(), py) || !emptyAt(s.u(), s.v(), py + 1) && py + 1 < ceiling) return false;
            if (unit(s.u(), s.v(), 411) < 0.25) return false;
            put(s.u(), s.v(), py, code(PICTURE, s.facing()));
            return true;
        }
        if (!free(k, s.u(), s.v()) || !emptyAt(s.u(), s.v(), y) || !emptyAt(s.u(), s.v(), y + 1)) return false;
        Level39PairFurnitureBlock.Layout layout = pairLayout(piece);
        int param = 0;
        if (piece == SHELF) {
            double a = unit(r.id, n, 412);
            param = a < 0.15 ? 0 : a < 0.35 ? 1 : a < 0.6 ? 2 : a < 0.85 ? 3 : 4;
        } else if (piece == ICEBOX) {
            double a = unit(r.id, n, 413);
            param = a < 0.75 ? 0 : a < 0.92 ? 1 : 2;
        }
        // Odd furniture (anomaly): on the ceiling, stacked, facing the wall, or up on the wall.
        int mode = 0;
        if (h.anomaly() == Level39HouseSpec.Anomaly.ODD_FURNITURE && primary) {
            double odd = unit(s.u() * 13 + n, s.v(), 414);
            mode = odd < 0.3 ? 1 : odd < 0.45 ? 2 : odd < 0.6 ? 3 : 0;
        }
        Direction facing = mode == 3 && layout == null ? s.facing().getOpposite() : s.facing();
        if (layout == null) {
            int code = withParam(code(piece, facing), param);
            if (mode == 1 && emptyAt(s.u(), s.v(), ceiling - 1)) {
                put(s.u(), s.v(), ceiling - 1, code | INV);
            } else if (mode == 2) {
                put(s.u(), s.v(), y, code);
                put(s.u(), s.v(), y + 1, withParam(code(Hash.unit(bits(s.u(), s.v(), 415)) < 0.5 ? ARMCHAIR : SIDE_TABLE,
                        Direction.from2DDataValue(Hash.range(bits(s.u(), s.v(), 416), 4))), 0));
            } else {
                put(s.u(), s.v(), y, code);
            }
            reserve(k, s.u(), s.v());
            return true;
        }
        switch (layout) {
            case TALL -> {
                if (y + 1 >= ceiling) return false;
                if (mode == 1 && y + 2 < ceiling && emptyAt(s.u(), s.v(), ceiling - 2)) {
                    // Upside down, hanging from the ceiling: the first half on top.
                    put(s.u(), s.v(), ceiling - 1, code(piece, facing) | INV);
                    put(s.u(), s.v(), ceiling - 2, code(piece, facing) | INV | SECOND);
                } else {
                    put(s.u(), s.v(), y, code(piece, facing));
                    put(s.u(), s.v(), y + 1, code(piece, facing) | SECOND);
                }
                reserve(k, s.u(), s.v());
                return true;
            }
            case WIDE -> {
                Direction side = facing.getClockWise();
                int pu = s.u() + stepU(side);
                int pv = s.v() + stepV(side);
                if (!free(k, pu, pv) || !emptyAt(pu, pv, y) || roomOf[k][gridIndex(pu, pv)] != r.id) return false;
                if (!isWall(at(pu - stepU(facing), pv - stepV(facing), y))) return false;
                if (mode == 1 && emptyAt(s.u(), s.v(), ceiling - 1) && emptyAt(pu, pv, ceiling - 1)) {
                    // Upside down the halves swap sides (see Level39HousesBlocks.flip).
                    put(pu, pv, ceiling - 1, code(piece, facing) | INV);
                    put(s.u(), s.v(), ceiling - 1, code(piece, facing) | INV | SECOND);
                } else {
                    put(s.u(), s.v(), y, code(piece, facing));
                    put(pu, pv, y, code(piece, facing) | SECOND);
                }
                reserve(k, s.u(), s.v());
                reserve(k, pu, pv);
                return true;
            }
            default -> {
                // LONG (bed, tub): the head against the wall, the foot one step into the room.
                Direction toWall = s.facing().getOpposite();
                int fu = s.u() + stepU(s.facing());
                int fvv = s.v() + stepV(s.facing());
                if (!free(k, fu, fvv) || !emptyAt(fu, fvv, y) || roomOf[k][gridIndex(fu, fvv)] != r.id) return false;
                int yy = mode == 1 && emptyAt(fu, fvv, ceiling - 1) && emptyAt(s.u(), s.v(), ceiling - 1) ? ceiling - 1 : y;
                int inv = yy != y ? INV : 0;
                put(fu, fvv, yy, code(piece, toWall) | inv);
                put(s.u(), s.v(), yy, code(piece, toWall) | SECOND | inv);
                reserve(k, s.u(), s.v());
                reserve(k, fu, fvv);
                return true;
            }
        }
    }

    /** Counters along one kitchen wall, the sink in the middle and the range at one end. */
    private void kitchenLine(Room r, List<Slot> slots, int piece) {
        for (Slot s : slots) {
            if (!free(r.storey, s.u(), s.v()) || !emptyAt(s.u(), s.v(), fy(r.storey) + 1)) continue;
            // Prefer the wall with the most room: the first slot whose neighbours along the wall are free too.
            Direction along = s.facing().getClockWise();
            boolean nextFree = free(r.storey, s.u() + stepU(along), s.v() + stepV(along))
                    || free(r.storey, s.u() - stepU(along), s.v() - stepV(along));
            if (!nextFree && piece != STOVE) continue;
            put(s.u(), s.v(), fy(r.storey) + 1, code(piece, s.facing()));
            reserve(r.storey, s.u(), s.v());
            return;
        }
    }

    private void diningTable(Room r) {
        int k = r.storey;
        int[] rr = r.main();
        int cu = (rr[0] + rr[2]) / 2;
        int cv = (rr[1] + rr[3]) / 2;
        int y = fy(k) + 1;
        boolean odd = h.anomaly() == Level39HouseSpec.Anomaly.ODD_FURNITURE && primary && unit(cu, cv, 417) < 0.5;
        int ty = odd ? fy(k + 1) - 1 : y;
        int inv = odd ? INV : 0;
        boolean two = rr[2] - rr[0] - 1 >= 4 && free(k, cu + 1, cv);
        if (!free(k, cu, cv) || !emptyAt(cu, cv, ty)) return;
        put(cu, cv, ty, code(TABLE, F) | inv);
        reserve(k, cu, cv);
        if (two) {
            put(cu + 1, cv, ty, code(TABLE, F) | inv);
            reserve(k, cu + 1, cv);
        }
        int[][] around = two
                ? new int[][] {{cu - 1, cv}, {cu + 2, cv}, {cu, cv - 1}, {cu + 1, cv - 1}, {cu, cv + 1}, {cu + 1, cv + 1}}
                : new int[][] {{cu - 1, cv}, {cu + 1, cv}, {cu, cv - 1}, {cu, cv + 1}};
        for (int[] a : around) {
            if (!free(k, a[0], a[1]) || !emptyAt(a[0], a[1], ty)) continue;
            int tu = a[0] < cu ? cu : a[0] > (two ? cu + 1 : cu) ? (two ? cu + 1 : cu) : a[0];
            Direction toTable = dirOf(Integer.signum(tu - a[0]), Integer.signum(cv - a[1]));
            put(a[0], a[1], ty, code(CHAIR, toTable) | inv);
            reserve(k, a[0], a[1]);
        }
    }

    private void furnishCellar() {
        int b0 = F0 - 5;
        int y = b0 + 1;
        int[] pieces = {BOILER, SHELF, SHELF, BOXES, SHELF, BOXES};
        int idx = 0;
        for (int u = bodyU0 + 1; u <= bodyU1 - 1 && idx < pieces.length; u++) {
            for (int v = fv + 1; v <= bodyV1 - 1 && idx < pieces.length; v++) {
                boolean edge = u == bodyU0 + 1 || u == bodyU1 - 1 || v == fv + 1 || v == bodyV1 - 1;
                if (!edge || at(u, v, y) != AIR) continue;
                if (cellarLadder != null && Math.abs(u - cellarLadder[0]) + Math.abs(v - cellarLadder[1]) <= 1) continue;
                if (unit(u, v, 418) < 0.55) continue;
                Direction facing = u == bodyU0 + 1 ? R : u == bodyU1 - 1 ? L : v == fv + 1 ? B : F;
                int piece = pieces[idx++];
                int param = piece == SHELF ? 1 + Hash.range(bits(u, v, 419), 4) : 0;
                put(u, v, y, withParam(code(piece, facing), param));
            }
        }
        // "Level 27" hint: a locked door in the cellar wall that leads nowhere you can reach.
        int du0 = (bodyU0 + bodyU1) / 2;
        if (at(du0, bodyV1, y) == code(FOUNDATION) && at(du0, bodyV1 - 1, y) == AIR) {
            int door = code(INT_DOOR, F) | FLAG;
            put(du0, bodyV1, y, door);
            put(du0, bodyV1, y + 1, door | SECOND);
        }
        // Bulbs under the ground floor.
        for (int u = bodyU0 + 1; u <= bodyU1 - 1; u++) {
            for (int v = fv + 1; v <= bodyV1 - 1; v++) {
                if (at(u, v, F0 - 1) == AIR && (u + 2 * v) % 3 == 0 && unit(u, v, 420) < 0.6) {
                    put(u, v, F0 - 1, bulbCode(u, v, 421, false));
                }
            }
        }
    }

    /**
     * The bare upside-down bulbs on the popcorn ceilings. About one living room, dining room or
     * parlour in four has the ceiling of the reference photo: a bulb in almost every block, a
     * little irregular, nearly all dead. The others have a loose grid. See {@link #bulbCode}.
     */
    void bulbs() {
        for (int k = 0; k < storeys; k++) {
            int top = fy(k + 1);
            for (int u = 0; u < W; u++) {
                for (int v = 0; v < D; v++) {
                    if (cell(k, u, v) != FLOOR) continue;
                    int ceil = kind(at(u, v, top));
                    if (ceil != POPCORN && ceil != FLOOR_OAK && ceil != FLOOR_DARK && ceil != LINO && ceil != BATH_TILE) continue;
                    if (at(u, v, top - 1) != EMPTY || top - 1 <= fy(k) + 2) continue;
                    short id = roomOf[k][gridIndex(u, v)];
                    if (id >= 0 && denseCeiling(rooms.get(id))) {
                        // The studded ceiling: one in eight blocks left bare so the rows aren't perfect.
                        if (unit(u, v, 426 + k) < 0.12) continue;
                        put(u, v, top - 1, bulbCode(u, v, 427 + k, true));
                        continue;
                    }
                    // A loose, uneven grid: roughly every other block, some left out, never two side by side.
                    boolean grid = ((u + (v % 2 == 0 ? 0 : 1)) % 2 == 0);
                    if (!grid || unit(u, v, 422 + k) < 0.3) continue;
                    if (kind(at(u - 1, v, top - 1)) == BULB || kind(at(u, v - 1, top - 1)) == BULB) continue;
                    put(u, v, top - 1, bulbCode(u, v, 423 + k, false));
                }
            }
        }
    }

    /** About one living room, dining room or parlour in four has the densely studded ceiling. */
    boolean denseCeiling(Room r) {
        return (r.type == LIVING || r.type == DINING || r.type == PARLOR) && unit(r.id, r.storey, 428) < 0.25;
    }

    /**
     * A bulb. On a loose grid about 30% still glow (warm, light 8); on a studded ceiling only about
     * one in nine, so the room is dim but the dead bulbs show. One lit bulb in six flickers.
     */
    int bulbCode(int u, int v, int salt, boolean dense) {
        double r = unit(u, v, salt);
        double lit = dense ? 0.11 : 0.3;
        if (r >= lit) return code(BULB);
        return code(BULB) | FLAG | (r < lit / 6 ? SECOND : 0);
    }

    /** Fills one room to the ceiling with water (the flooded-room anomaly). */
    void flood() {
        Room target = null;
        int best = -1;
        for (Room r : rooms) {
            if (r.storey != 0) continue;
            if (r.type != LIVING && r.type != PARLOR && r.type != DINING && r.type != KITCHEN && r.type != BATH && r.type != BED) continue;
            int score = Hash.range(bits(r.id, 0, 424), 100);
            if (score > best) {
                best = score;
                target = r;
            }
        }
        if (target == null) return;
        int k = target.storey;
        for (int u = 0; u < W; u++) {
            for (int v = 0; v < D; v++) {
                if (cell(k, u, v) != FLOOR || roomOf[k][gridIndex(u, v)] != target.id) continue;
                for (int y = fy(k) + 1; y < fy(k + 1); y++) {
                    int code = at(u, v, y);
                    if (code == EMPTY || code == AIR || kind(code) == BULB) put(u, v, y, code(WATER));
                }
            }
        }
    }

    // ==========================================================================================
    // The styles
    // ==========================================================================================

    /** Common settings of a sub-house before its template runs. */
    void begin(int style, int s0, int s1, int door, boolean primary) {
        this.style = style;
        this.s0 = s0;
        this.s1 = s1;
        this.primary = primary;
        this.hb = Hash.mix(h.hash() + (primary ? 0 : 0x5EED));
        this.du = door;
        this.F0 = h.baseY();
        this.old = style <= Level39HouseSpec.STYLE_FARMHOUSE;
        this.P = style == Level39HouseSpec.STYLE_COLONIAL || style == Level39HouseSpec.STYLE_CAPE_COD
                || style == Level39HouseSpec.STYLE_RANCH ? 4 : 5;
        int weathering = Hash.range(Hash.mix(h.hash() >>> 8), 10);
        this.shade = weathering < 6 ? 0 : weathering < 9 ? 1 : 2;
        this.paperGround = Hash.range(Hash.mix(hb + 1), 5);
        this.paperUpper = (paperGround + 1 + Hash.range(Hash.mix(hb + 2), 4)) % 5;
        this.floorMain = old || Hash.unit(Hash.mix(hb + 3)) < 0.3 ? FLOOR_DARK : FLOOR_OAK;
        Level39HouseSpec.Anomaly a = h.anomaly();
        this.stairsWanted = !(a == Level39HouseSpec.Anomaly.BIGGER_INSIDE && primary);
        this.basement = primary && a != Level39HouseSpec.Anomaly.FLOATING && a != Level39HouseSpec.Anomaly.INVERTED
                && a != Level39HouseSpec.Anomaly.BIGGER_INSIDE && Hash.unit(Hash.mix(hb + 4)) < 0.45;
        this.litWindowsLeft = primary && h.litWindow() ? 1 : 0;
        this.belt = style == Level39HouseSpec.STYLE_VICTORIAN;
    }

    void template() {
        switch (style) {
            case Level39HouseSpec.STYLE_COLONIAL -> colonial();
            case Level39HouseSpec.STYLE_VICTORIAN -> victorian();
            case Level39HouseSpec.STYLE_FARMHOUSE -> farmhouse();
            case Level39HouseSpec.STYLE_CRAFTSMAN -> craftsman();
            case Level39HouseSpec.STYLE_CAPE_COD -> capeCod();
            default -> ranch();
        }
    }

    /** Stairs up from storey k in the hall, if the hall is long enough. */
    void addStairs(int k, int fvv, int hallEnd) {
        if (!stairsWanted) return;
        if (hallEnd - 1 < fvv + P + 1) return;
        stairsList.add(new int[] {k, stairColumn(), fvv + 2});
    }

    /**
     * 1800s colonial saltbox: two and a half storeys, five bays, a centre chimney through the ridge,
     * and the long rear slope of the roof coming down to the top of the ground floor.
     */
    void colonial() {
        fullStoreys = 2;
        storeys = 2;
        fv = 1;
        bodyU0 = s0;
        bodyU1 = s1;
        bodyV1 = D - 1;
        int hallEnd = fv + P + 3;
        zoned(0, s0, s1, fv, bodyV1, hallEnd, PARLOR, DINING, LIVING, KITCHEN, BATH);
        zoned(1, s0, s1, fv, bodyV1, hallEnd, BED, BED, BED, BATH, BED);
        addStairs(0, fv, hallEnd);
        // Saltbox: the front slope from the top of the upper floor, the back from the top of the ground floor.
        prim(s0, 0, s1, D - 1, 0,
                new Plane(0, 0, fy(2), 2),
                new Plane(2, D - 1, fy(1) + 1, 2));
        int ridgeV = Math.max(fv + 1, (D - P) / 2);
        chimneys.add(new int[] {du + 2, ridgeV, F0});
        for (int s = -1; s <= 1; s++) porchSteps.add(new int[] {du + s, 0});
    }

    /** 1950s Cape Cod: one and a half storeys under a steep roof, twin dormers, centre chimney. */
    void capeCod() {
        fullStoreys = 1;
        storeys = 2;
        halfTop = true;
        fv = 1;
        bodyU0 = s0;
        bodyU1 = s1;
        bodyV1 = D - 1;
        int hallEnd = fv + P + 3;
        zoned(0, s0, s1, fv, bodyV1, hallEnd, LIVING, DINING, BED, KITCHEN, BATH);
        zoned(1, s0, s1, fv, bodyV1, hallEnd, BED, -1, BED, -1, BATH);
        addStairs(0, fv, hallEnd);
        prim(s0, 0, s1, D - 1, 0,
                new Plane(0, 0, fy(1), 2),
                new Plane(2, D - 1, fy(1) + 1, 2));
        // Twin gable dormers, symmetrical about the door, lighting the two upstairs bedrooms.
        for (int cU : new int[] {du - 3, du + 3}) {
            if (cU - 2 < s0 || cU + 2 > s1) continue;
            gableDormer(cU, fy(1) + 3);
        }
        // A 1950s Cape: the chimney stands at one gable end.
        chimneys.add(new int[] {((hb >>> 13) & 1) == 0 ? s0 : s1, Math.max(fv + 1, (D - 1) / 2), F0});
        for (int s = -1; s <= 1; s++) porchSteps.add(new int[] {du + s, 0});
    }

    /** A small gable dormer three wide on the front slope, its window over the front wall. */
    void gableDormer(int cU, int eaveY) {
        Plane l = new Plane(3, cU - 2, eaveY, 2);
        Plane r = new Plane(1, cU + 2, eaveY, 2);
        int ridgeV = (D - 1) / 2;
        Prim core = prim(cU - 1, fv - 1, cU + 1, ridgeV, 0, l, r);
        dormerCores.add(core);
        // The dormer's eaves overhang the main roof at the front: drawn where nothing else is.
        prim(cU - 2, fv - 1, cU - 2, fv + 1, 1, l, r);
        prim(cU + 2, fv - 1, cU + 2, fv + 1, 1, l, r);
        dormerWindows.add(new int[] {cU, fv});
    }

    /** 1970s/80s ranch: one long storey under a low hip roof, an attached garage, a picture window. */
    void ranch() {
        fullStoreys = 1;
        storeys = 1;
        fv = 1;
        bodyV1 = Math.min(D - 2, fv + 10);
        int hu0 = s0 + 1;
        int hu1 = s1 - 1;
        boolean garage = s1 - s0 + 1 >= 17;
        boolean garageLeft = ((hb >>> 11) & 1) == 0;
        // The garage (4 wide inside, a 3-wide door) may share its wall with the hall, never cross it.
        boolean leftFits = s0 + 6 <= du - 2;
        boolean rightFits = s1 - 6 >= du + 2;
        if (garage && (leftFits || rightFits)) {
            garageLeft = garageLeft ? leftFits : !rightFits;
            if (garageLeft) {
                Room g = room(0, GARAGE, hu0, fv, s0 + 6, bodyV1);
                garageDoorU = (hu0 + s0 + 6) / 2;
                hu0 = s0 + 6;
                door(0, hu0, bodyV1 - 2);
                g.floor = CONCRETE;
            } else {
                Room g = room(0, GARAGE, s1 - 6, fv, hu1, bodyV1);
                garageDoorU = (s1 - 6 + hu1) / 2;
                hu1 = s1 - 6;
                door(0, hu1, bodyV1 - 2);
                g.floor = CONCRETE;
            }
        }
        bodyU0 = hu0;
        bodyU1 = hu1;
        zoned(0, hu0, hu1, fv, bodyV1, fv + 5, LIVING, KITCHEN, BED, BED, BATH);
        prim(s0, 0, s1, bodyV1 + 1, 0,
                new Plane(0, 0, fy(1), 1),
                new Plane(2, bodyV1 + 1, fy(1), 1),
                new Plane(3, s0, fy(1), 1),
                new Plane(1, s1, fy(1), 1));
        chimneys.add(new int[] {garageLeft ? hu1 : hu0, (fv + bodyV1) / 2, F0});
        porchSteps.add(new int[] {du, 0});
    }

    /**
     * 1920s Craftsman bungalow: one and a half storeys, the roof sweeping down over a deep front
     * porch on tapered posts and stone piers, a wide shed dormer, an outside chimney on one end.
     */
    void craftsman() {
        fullStoreys = 1;
        storeys = 2;
        halfTop = true;
        int pd = 4;
        fv = pd;
        bodyU0 = s0;
        bodyU1 = s1;
        bodyV1 = D - 2;
        int hallEnd = fv + P + 3;
        zoned(0, s0, s1, fv, bodyV1, hallEnd, LIVING, DINING, PARLOR, KITCHEN, BATH);
        zoned(1, s0, s1, fv, bodyV1, hallEnd, BED, -1, BED, -1, BATH);
        addStairs(0, fv, hallEnd);
        prim(s0, 0, s1, D - 1, 0,
                new Plane(0, 0, fy(1), 2),
                new Plane(2, D - 1, fy(1), 2));
        // The shed dormer: a gentle slope from just in front of the wall back into the main roof.
        int a = Math.max(s0 + 1, du - 3);
        int b = Math.min(s1 - 1, du + 3);
        Prim core = prim(a, fv - 1, b, (D - 1) / 2, 0, new Plane(0, fv - 1, fy(1) + 5, 1));
        dormerCores.add(core);
        dormerWindows.add(new int[] {du - 1, fv});
        dormerWindows.add(new int[] {du + 1, fv});
        // The porch across the whole front.
        porchFloors.add(new int[] {s0, 1, s1, pd - 1});
        porchPostKind = POST_CRAFT;
        porchBeamY = fy(1);
        for (int u : new int[] {s0, s1, du - 3, du + 3}) {
            if (u >= s0 && u <= s1) porchPosts.add(new int[] {u, 1, fy(1) - 1});
        }
        for (int u = s0; u <= s1; u++) {
            if (Math.abs(u - du) <= 1 || isPost(u, 1)) continue;
            porchRails.add(new int[] {u, 1});
        }
        for (int v = 2; v < pd; v++) {
            porchRails.add(new int[] {s0, v});
            porchRails.add(new int[] {s1, v});
        }
        for (int s = -1; s <= 1; s++) porchSteps.add(new int[] {du + s, 0});
        int chimneyU = ((hb >>> 13) & 1) == 0 ? s0 : s1;
        chimneys.add(new int[] {chimneyU, fv + 3 + Hash.range(bits(1, 1, 425), Math.max(1, bodyV1 - fv - 5)), F0});
    }

    private boolean isPost(int u, int v) {
        for (int[] p : porchPosts) if (p[0] == u && p[1] == v) return true;
        return false;
    }

    /**
     * 1880s Victorian: two storeys with a white belt course, a hipped roof with a cross gable over
     * the front, a round-ish corner turret three storeys tall with a steep spire, a porch wrapping
     * round the front and one side, and a bay window.
     */
    void victorian() {
        fullStoreys = 2;
        storeys = 3;
        fv = 3;
        boolean turretRight = ((hb >>> 9) & 1) == 0;
        int wrap = 2;
        int b0 = turretRight ? s0 + wrap : s0;
        int b1 = turretRight ? s1 : s1 - wrap;
        bodyU0 = b0;
        bodyU1 = b1;
        bodyV1 = D - 2;
        if (du - 2 - b0 < 2 || b1 - du - 2 < 2) {
            // Too narrow for the wrap: the porch runs along the front only.
            b0 = s0;
            b1 = s1;
            bodyU0 = b0;
            bodyU1 = b1;
            wrap = 0;
        }
        int hallEnd = fv + P + 3;
        zoned(0, b0, b1, fv, bodyV1, hallEnd, PARLOR, DINING, LIVING, KITCHEN, BATH);
        zoned(1, b0, b1, fv, bodyV1, hallEnd, BED, BED, BED, BATH, BED);
        addStairs(0, fv, hallEnd);
        // The turret: 5 (or 4) wide and 5 deep, standing 2 blocks out of the front wall at the
        // corner, joined to the front room; it never crosses the hall.
        int t0 = turretRight ? Math.max(b1 - 4, du + 2) : b0;
        int t1 = turretRight ? b1 : Math.min(b0 + 4, du - 2);
        int tv0 = fv - 2;
        int tv1 = tv0 + 4;
        boolean turret = t1 - t0 + 1 >= 4;
        if (turret) {
            for (int k = 0; k < 2; k++) {
                Room front = frontRoomAt(k, (t0 + t1) / 2);
                if (front != null) front.rects.add(new int[] {t0, tv0, t1, tv1});
            }
            room(2, TURRET, t0, tv0, t1, tv1);
            int eave = fy(3) + 1;
            prim(t0, tv0, t1, tv1, 0,
                    new Plane(0, tv0, eave, 2), new Plane(2, tv1, eave, 2),
                    new Plane(3, t0, eave, 2), new Plane(1, t1, eave, 2));
            finials.add(new int[] {(t0 + t1) / 2, (tv0 + tv1) / 2});
        } else {
            storeys = 2;
        }
        // Main hip roof (overhanging where the footprint allows) and the front cross gable.
        int ol = b0 > s0 ? b0 - 1 : b0;
        int or = b1 < s1 ? b1 + 1 : b1;
        int eave = fy(2);
        prim(ol, fv - 1, or, D - 1, 0,
                new Plane(0, fv - 1, eave, 2), new Plane(2, D - 1, eave, 2),
                new Plane(3, ol, ol < b0 ? eave : eave + 1, 2), new Plane(1, or, or > b1 ? eave : eave + 1, 2));
        int g0 = turretRight ? Math.max(b0, du - 5) : du - 1;
        int g1 = turretRight ? du + 1 : Math.min(b1, du + 5);
        if (!turret) {
            g0 = du - 3;
            g1 = du + 3;
        }
        prim(g0, fv - 1, g1, fv + 5, 0,
                new Plane(3, g0, eave + 1, 2), new Plane(1, g1, eave + 1, 2));
        chimneys.add(new int[] {du + (turretRight ? -2 : 2), Math.min(bodyV1 - 2, fv + 6), F0});
        // The wrap-around porch.
        int pf0 = turretRight ? s0 : (turret ? t1 + 1 : s0);
        int pf1 = turretRight ? (turret ? t0 - 1 : s1) : s1;
        porchFloors.add(new int[] {pf0, 1, pf1, fv - 1});
        prim(pf0, 1, pf1, fv - 1, 2, new Plane(0, 1, F0 + 4, 1));
        for (int u = pf0; u <= pf1; u++) {
            boolean post = u == pf0 || u == pf1 || (u - pf0) % 3 == 0;
            if (Math.abs(u - du) <= 1) continue;
            if (post) porchPosts.add(new int[] {u, 1, F0 + 3});
            else porchRails.add(new int[] {u, 1});
        }
        for (int s = -1; s <= 1; s++) porchSteps.add(new int[] {du + s, 0});
        if (wrap > 0) {
            int wu0 = turretRight ? s0 : s1 - 1;
            int wu1 = wu0 + 1;
            int outerU = turretRight ? s0 : s1;
            int wv1 = Math.min(bodyV1 - 4, fv + 5);
            porchFloors.add(new int[] {wu0, fv, wu1, wv1});
            prim(wu0, fv, wu1, wv1, 2, new Plane(turretRight ? 3 : 1, outerU, F0 + 4, 1));
            for (int v = fv; v <= wv1; v++) {
                boolean post = v == wv1 || (v - fv) % 3 == 2;
                if (post) porchPosts.add(new int[] {outerU, v, F0 + 3});
                else porchRails.add(new int[] {outerU, v});
            }
            // A bay window further back on the same side.
            int bv0 = wv1 + 1;
            int bv1 = bv0 + 4;
            if (bv1 < bodyV1 - 1) {
                Room side = sideRoomAt(0, turretRight ? b0 + 1 : b1 - 1, (bv0 + bv1) / 2);
                int bu0 = turretRight ? b0 - 2 : b1;
                int bu1 = turretRight ? b0 : b1 + 2;
                if (side != null && bu0 >= s0 && bu1 <= s1) {
                    side.rects.add(new int[] {bu0, bv0, bu1, bv1});
                    int bayU = turretRight ? bu0 : bu1;
                    prim(bu0, bv0, bu1, bv1, 2,
                            new Plane(turretRight ? 3 : 1, bayU, fy(1), 1),
                            new Plane(0, bv0, fy(1), 1), new Plane(2, bv1, fy(1), 1));
                }
            }
        }
    }

    private Room frontRoomAt(int k, int u) {
        for (Room r : rooms) {
            int[] rr = r.main();
            if (r.storey == k && rr[1] == fv && u > rr[0] && u < rr[2] && r.type != HALL && r.type != LANDING) return r;
        }
        return null;
    }

    private Room sideRoomAt(int k, int u, int v) {
        for (Room r : rooms) {
            int[] rr = r.main();
            if (r.storey == k && u > rr[0] && u < rr[2] && v > rr[1] && v < rr[3]) return r;
        }
        return null;
    }

    /**
     * 1900s farmhouse, as in the photo: two storeys, the gable end facing the path, a side hall
     * with the stairs, a lower kitchen wing to one side, a lean-to across the back and a brick
     * chimney on the ridge.
     */
    void farmhouse() {
        fullStoreys = 2;
        storeys = 2;
        fv = 1;
        boolean leanTo = D >= 14;
        int mv1 = leanTo ? D - 5 : D - 1;
        bodyV1 = mv1;
        boolean wingRight = ((hb >>> 10) & 1) == 0;
        int m0 = s0;
        int m1 = s1;
        boolean wing;
        if (wingRight) {
            wing = s1 - (du + 2) >= 4;
            if (wing) m1 = du + 2;
        } else {
            wing = (du - 2) - s0 >= 4;
            if (wing) m0 = du - 2;
        }
        bodyU0 = m0;
        bodyU1 = m1;
        int hallEnd = fv + P + 3;
        zoned(0, m0, m1, fv, mv1, hallEnd, PARLOR, DINING, LIVING, KITCHEN, BATH);
        zoned(1, m0, m1, fv, mv1, hallEnd, BED, BED, BED, BED, BATH);
        addStairs(0, fv, hallEnd);
        int eave = fy(2) + 1;
        // Main roof: the gable faces the path.
        prim(m0, 0, m1, mv1, 0, new Plane(3, m0, eave, 2), new Plane(1, m1, eave, 2));
        chimneys.add(new int[] {(m0 + m1) / 2 + (((hb >>> 12) & 1) == 0 ? 1 : -1) * 0, Math.max(fv + 2, (fv + mv1) / 2 + 1), F0});
        if (wing) {
            int w0 = wingRight ? m1 : s0;
            int w1 = wingRight ? s1 : m0;
            int wv0 = fv + 2;
            int wv1 = mv1 - 1;
            room(0, KITCHEN, w0, wv0, w1, wv1);
            int wallU = wingRight ? m1 : m0;
            door(0, wallU, Math.min(wv1 - 1, Math.max(wv0 + 1, fv + P + 2)));
            prim(wingRight ? m1 + 1 : s0, wv0 - 1, wingRight ? s1 : m0 - 1, wv1 + 1, 1,
                    new Plane(0, wv0 - 1, fy(1), 2), new Plane(2, wv1 + 1, fy(1), 2));
        }
        if (leanTo) {
            int lv1 = D - 2;
            int mid = (m0 + m1) / 2;
            room(0, BATH, m0, mv1, mid, lv1);
            room(0, MUDROOM, mid, mv1, m1, lv1);
            door(0, Math.max(m0 + 1, Math.min(mid - 1, (m0 + mid) / 2)), mv1);
            door(0, Math.max(mid + 1, Math.min(m1 - 1, (mid + m1) / 2)), mv1);
            prim(m0, mv1 + 1, m1, D - 1, 1, new Plane(2, D - 1, fy(1), 1));
        }
        for (int s = -1; s <= 1; s++) porchSteps.add(new int[] {du + s, 0});
    }

    // ==========================================================================================
    // Building
    // ==========================================================================================

    /** Draws one sub-house (the whole house, or one of the two halves of a merged one). */
    void buildSub(int style, int s0, int s1, int door, boolean primary) {
        begin(style, s0, s1, door, primary);
        template();
        rasterize();
        drawStoreys();
        drawWindows();
        drawFrontDoor();
        drawRoofs();
        fitUnderRoofs();
        drawDormers();
        gableRoofPieces();
        drawChimneys();
        drawPorch();
        drawStairs();
        for (int[] fin : finials) {
            int gi = gridIndex(fin[0], fin[1]);
            if (rTop[gi] == Integer.MIN_VALUE) continue;
            put(fin[0], fin[1], rTop[gi] + 1, code(POST_WHITE));
            put(fin[0], fin[1], rTop[gi] + 2, code(POST_WHITE));
        }
        if (garageDoorU != Integer.MIN_VALUE) {
            for (int u = garageDoorU - 1; u <= garageDoorU + 1; u++) {
                if (cell(0, u, fv) != WALL || isCorner(0, u, fv)) continue;
                put(u, fv, F0 + 1, code(GARAGE_DOOR, F));
                put(u, fv, F0 + 2, code(GARAGE_DOOR, F));
            }
        }
        drawCellar();
        placeKnockWalls(h.anomaly() == Level39HouseSpec.Anomaly.INVERTED);
        furnish();
        bulbs();
        if (h.anomaly() == Level39HouseSpec.Anomaly.FLOODED_ROOM && primary) flood();
    }

    /** Builds the whole house and its anomaly; the result is a cache-ready plan. */
    static Level39HousesPlan build(long seed, Level39HouseSpec h) {
        return new Level39HousesPlan(seed, h, draw(seed, h));
    }

    /** Draws the whole house and its anomaly into a builder's grid of codes. */
    static Level39HousesBuilder draw(long seed, Level39HouseSpec h) {
        Level39HousesBuilder a = new Level39HousesBuilder(seed, h);
        int doorU = a.u(h.doorX(), h.doorZ());
        Level39HouseSpec.Anomaly anomaly = h.anomaly();
        if (anomaly == Level39HouseSpec.Anomaly.MERGED && h.style2() != h.style()) {
            // Two houses pushed into each other: each takes about 60% of the front, so a fifth of
            // the width belongs to both and their walls run straight through each other's rooms.
            int wa = Math.max(11, Math.round(a.W * 0.62f));
            int wb = Math.max(11, Math.round(a.W * 0.62f));
            wa = Math.min(wa, a.W);
            wb = Math.min(wb, a.W);
            int doorA = Math.min(doorU, wa - 4);
            a.buildSub(h.style(), 0, wa - 1, Math.max(3, doorA), true);
            Level39HousesBuilder b = new Level39HousesBuilder(seed, h);
            int b0 = a.W - wb;
            b.buildSub(h.style2(), b0, a.W - 1, b0 + (wb - 1) / 2, false);
            for (int i = 0; i < a.c.length; i++) {
                int code = b.c[i];
                if (code == EMPTY) continue;
                if (code == AIR && isSolid(a.c[i])) continue;
                a.c[i] = code;
            }
            a.knockWalls.addAll(b.knockWalls);
        } else {
            a.buildSub(h.style(), 0, a.W - 1, doorU, true);
        }
        if (anomaly == Level39HouseSpec.Anomaly.CUT_HALF) a.cutHalf();
        if (anomaly == Level39HouseSpec.Anomaly.INVERTED) a.invert();
        return a;
    }

    /** The house sliced clean through: one half simply isn't there, its rooms open like a dollhouse. */
    void cutHalf() {
        boolean acrossDepth = ((hb >>> 15) & 1) == 0;
        int cutAt = acrossDepth ? fv + Math.max(3, (D - fv) / 2) : W / 2 + (((hb >>> 16) & 1) == 0 ? 0 : -1);
        boolean keepLow = ((hb >>> 17) & 1) == 0 || acrossDepth;
        for (int u = 0; u < W; u++) {
            for (int v = 0; v < D; v++) {
                int along = acrossDepth ? v : u;
                boolean gone = keepLow ? along > cutAt : along < cutAt;
                if (!gone) continue;
                for (int y = Y0; y < Y0 + CH; y++) put(u, v, y, EMPTY);
            }
        }
        fixPairs();
    }

    /** Two-block furniture that lost its other half loses this half too. */
    void fixPairs() {
        for (int u = 0; u < W; u++) {
            for (int v = 0; v < D; v++) {
                for (int y = Y0; y < Y0 + CH; y++) {
                    int code = at(u, v, y);
                    if (!isFurniture(code)) continue;
                    Level39PairFurnitureBlock.Layout layout = pairLayout(kind(code));
                    if (layout == null) continue;
                    boolean inv = (code & INV) != 0;
                    Direction off = layout.secondOffset(facing(code), inv);
                    if ((code & SECOND) != 0) off = off.getOpposite();
                    int ou = u + stepU(off);
                    int ov = v + stepV(off);
                    int oy = y + off.getStepY();
                    int other = at(ou, ov, oy);
                    if (kind(other) != kind(code) || (other & SECOND) == (code & SECOND)) put(u, v, y, EMPTY);
                }
            }
        }
    }

    /**
     * Turned upside down and balanced on its roof: the ridge (or the turret's point) is stuck one
     * block into the ground, the roof spreads upward from it like an upturned hull, the walls stand
     * on the eaves, and the floor of the ground storey with its foundation is on top. The chimney
     * points down into the soil.
     */
    void invert() {
        int highestRoof = Integer.MIN_VALUE;
        for (int i = 0; i < c.length; i++) {
            int k = kind(c[i]);
            if (k == ROOF || k == ROOF_LOW) highestRoof = Math.max(highestRoof, Y0 + i % CH);
        }
        int plate = fy(fullStoreys);
        // Without any roof (never seen) the house simply stands on its old ceiling.
        int pivot = highestRoof == Integer.MIN_VALUE ? plate + F0 : highestRoof + Level39Layout.GROUND_Y;
        int[] flipped = new int[c.length];
        for (int u = 0; u < W; u++) {
            for (int v = 0; v < D; v++) {
                for (int y = Y0; y < Y0 + CH; y++) {
                    int code = at(u, v, y);
                    if (code == EMPTY) continue;
                    int ny = pivot - y;
                    // A spire buried deeper than the town allows simply stays in the ground (it's soil there).
                    if (ny < Y0 || ny >= Y0 + CH) continue;
                    flipped[((u * D) + v) * CH + (ny - Y0)] = flip(code);
                }
            }
        }
        System.arraycopy(flipped, 0, c, 0, c.length);
    }
}
