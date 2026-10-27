package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.CurveOrientation;
import com.rickr.backrooms.block.CurveShape;
import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.Arrays;
import org.jetbrains.annotations.Nullable;

/**
 * The floor plan of Level 37 ("Sublimity") as plain numbers: for any column (x, z) it says what
 * goes at every height, as small integer codes (tile, water, a curved piece, a window of light...).
 * {@link Level37Layout} turns the codes into blocks. Keeping the plan free of block states keeps
 * it a pure function of (seed, x, z), and lets it be checked on its own (connectivity, water that
 * never leaks) without starting the game.
 *
 * <h2>Floor plan</h2>
 * A grid of 24 x 24 cells, one room each. The 3-thick wall between two cells (on a cell's west and
 * north edges) is solid or pierced by a round-arched passage (2 or 4 wide); passages always lead
 * on into the room, cutting their own arched tunnel through any thick wall in the way ("lanes").
 * Every cell keeps one way out to the west or north, so no room is ever sealed. Rooms:
 * <ul>
 *   <li><b>room</b>: a flooded tile room with rounded corners; sometimes a wavy wall, tall slit
 *       windows, a row of round pillars, a deep plunge pool, skylights, a handrail.</li>
 *   <li><b>corridor</b>: runs of cells make long, 4-wide flooded corridors: lit by slit windows
 *       (stripes of sun across the water), by a slot in the ceiling, or a dark round tunnel.</li>
 *   <li><b>hall</b> (2 x 2 cells): a 10-high forest of round pillars standing in water, with deep
 *       pools between them and sun pouring through ceiling slots; or a dry walkway beside a big
 *       deep pool full of pillars, lit through a tall arcade.</li>
 *   <li><b>rotunda</b>: a round room (12 across) with a round pool, a stepped dome with an oculus,
 *       and glowing arched alcoves.</li>
 *   <li><b>tunnel</b>: a hall of receding round arches, lit at the near end, ending in a dark
 *       round tunnel.</li>
 *   <li><b>spiral</b>: a spiral stair of floating treads around a round pillar, climbing through
 *       a hole in the ceiling up a light well toward the sun.</li>
 *   <li><b>pool</b>: a dry deck around a deep pool with rounded corners and chrome ladders,
 *       sometimes a colonnade standing in it, square windows high on one wall.</li>
 *   <li><b>pit</b> (the exit, one per 168 x 168 blocks): a rotunda whose middle is a round shaft
 *       of deep dark water; treads spiral down its wall into the water. Swim down and the current
 *       ({@code Level37Rules}) takes you below {@link Level37Layout#TRANSFER_BELOW_Y}.</li>
 * </ul>
 * A few rooms and corridors are anomalous areas ({@link Level37AnomalyPlan}): a void opening in
 * a slit-window wall, a dark opening in a pool room, a looping stretch of round tunnel. Their
 * changes are made on top of the normal plan, column by column, from the same site answer.
 *
 * <h2>Heights</h2>
 * Floor layer y40, feet y41 (ankle-deep {@code SHALLOW_WATER} there on flooded floors). Deep
 * pools: floor y35, water y36-y40 (topped by shallow water in flooded rooms, so the surface runs
 * on without a ledge). The pit: floor y14, water y15-y40. Ceilings: corridors y46, rooms y47-48,
 * tunnel hall y49, halls y51; the spiral light well reaches y59.
 *
 * <h2>Water never leaks</h2>
 * Every column is solid from y33 (y12 in a pit room) up to the floor unless it holds pool water,
 * and pool water stops at the floor layer: no water block ever has air or shallow water beside or
 * below it. Pieces of a pool's curved wall and submerged treads and ladders are waterlogged.
 */
public final class Level37Plan {
    // ==========================================================================================
    // Codes (bits 0-4 the type, the rest data)
    // ==========================================================================================
    public static final int AIR = 0;
    /** Ankle-deep water at feet level (SHALLOW_WATER). */
    public static final int SHALLOW = 1;
    /** Vanilla water (deep pools, the pit). */
    public static final int WATER = 2;
    public static final int WALL = 3;
    public static final int FLOOR = 4;
    public static final int CEIL = 5;
    /** Dark sea-green tile (the pit's floor). */
    public static final int DEEP = 6;
    /** Curved tile piece: data = shape | orientation << 6 | waterlogged << 9 | flooded << 10. */
    public static final int PIECE = 7;
    /** Sunlit void: data = direction the light travels (3D index: down 0, up 1, north 2, south 3, west 4, east 5). */
    public static final int VOID = 8;
    /** Tile slab: data bit 0 top half, bit 1 waterlogged, bit 2 flooded. */
    public static final int SLAB = 9;
    /** Pool ladder: data = facing toward the deck (2D index: south 0, west 1, north 2, east 3) | waterlogged << 2. */
    public static final int LADDER = 10;
    /** Handrail: data = facing away from the wall (2D index) | post << 2. */
    public static final int RAIL = 11;
    /** Invisible vanilla light block (soft fill light): data = light level. */
    public static final int LIGHT = 12;
    /** Backpack of supplies: data = facing (2D index). */
    public static final int BACKPACK = 13;
    /** Pitch-black walk-in darkness (a dark opening, see {@link Level37AnomalyPlan}). */
    public static final int DARK = 14;

    public static int type(int code) {
        return code & 31;
    }

    public static int data(int code) {
        return code >>> 5;
    }

    static int code(int type, int data) {
        return type | (data << 5);
    }

    private static final CurveShape[] SHAPES = CurveShape.values();
    private static final CurveOrientation[] ORIENTATIONS = CurveOrientation.values();

    public static CurveShape pieceShape(int data) {
        return SHAPES[data & 63];
    }

    public static CurveOrientation pieceOrientation(int data) {
        return ORIENTATIONS[(data >> 6) & 7];
    }

    public static boolean pieceWaterlogged(int data) {
        return (data & 512) != 0;
    }

    public static boolean pieceFlooded(int data) {
        return (data & 1024) != 0;
    }

    // 2D / 3D direction indices, as Minecraft numbers them (Direction.get2DDataValue / get3DDataValue).
    private static final int S2 = 0;
    private static final int W2 = 1;
    private static final int N2 = 2;
    private static final int E2 = 3;
    private static final int DOWN3 = 0;
    private static final int NORTH3 = 2;
    static final int SOUTH3 = 3;
    private static final int WEST3 = 4;
    static final int EAST3 = 5;

    // ==========================================================================================
    // Heights
    // ==========================================================================================
    static final int FLOOR_Y = LevelLayout.FLOOR_Y;
    static final int FEET_Y = FLOOR_Y + 1;
    /** Solid tile from here up to the floor everywhere (deep pools reach down to y35). */
    private static final int FOUNDATION_Y = 33;
    /** Floor layer of the deep pools; their water fills y36-y40. */
    public static final int POOL_BOTTOM_Y = 35;
    /** Floor layer of the exit pit; its water fills y15-y40. */
    public static final int PIT_BOTTOM_Y = 14;
    /** Pit rooms are solid from here, so the pit's walls go all the way down. */
    private static final int PIT_FOUNDATION_Y = 12;
    /** Walls between rooms (and the solid fill around round rooms) reach this high. */
    private static final int TOP_Y = 51;
    /** The sunlit cap of the spiral room's light well. */
    private static final int WELL_CAP_Y = 59;
    /** The highest block the plan ever places. */
    public static final int HIGHEST_Y = WELL_CAP_Y;
    /** Ceiling of the corridors (5 high). */
    private static final int CORRIDOR_CEIL_Y = FEET_Y + 5;
    /** Spring line of the round corridor vaults: 2 straight blocks, then a half circle of radius 2. */
    private static final int CORRIDOR_SPRING_Y = 43;

    // ==========================================================================================
    // Grid
    // ==========================================================================================
    public static final int CELL = 24;
    /** Walls between cells are this thick (cell-local 0..2 on the west and north edges). */
    static final int BAND = 3;
    /**
     * The middle of a cell, as a block CORNER coordinate (between cells 12 and 13): every circle
     * is centred on a block corner. Centred passages are 4 wide: cells 11..14.
     */
    public static final int MID = 13;
    /** Halls are 2 x 2 cells. */
    private static final int HALL_SIZE = 2 * CELL;
    private static final double HALL_SHARE = 0.14;
    /** Corridor runs: rows (or columns) of cells split into stretches of RUN ... */
    private static final int RUN = 4;
    /** ... of which this share are corridors. */
    private static final double RUN_SHARE = 0.16;
    /** Share of the walls between two ordinary rooms that are solid. */
    private static final double SOLID_EDGE_SHARE = 0.3;

    // Room kinds (packed: bits 0-3 kind, bit 4 "along X", bits 5-8 variant, bits 9-13 height).
    static final int K_ROOM = 0;
    static final int K_POOL = 1;
    static final int K_CORRIDOR = 2;
    private static final int K_HALL = 3;
    private static final int K_ROTUNDA = 4;
    private static final int K_TUNNEL = 5;
    private static final int K_SPIRAL = 6;
    private static final int K_PIT = 7;
    private static final int COR_SLITS = 0;
    private static final int COR_SKY = 1;
    static final int COR_VAULT = 2;
    private static final int HALL_FOREST = 0;
    private static final int HALL_COLONNADE = 1;

    // Edges between cells: the one on a cell's west side, and on its north side.
    static final int WEST = 0;
    static final int NORTH = 1;
    static final int E_SOLID = 0;
    static final int E_ARCH = 1;
    /** Two corridors meeting end to end: the corridor just runs on through the wall. */
    private static final int E_JOIN = 2;
    /** Inside one hall: no wall. */
    private static final int E_OPEN = 3;

    // Results of the shape tests: cell fully inside the circle / rectangle (open), fully outside
    // (solid), otherwise a curved piece id (shape | orientation << 6).
    static final int C_IN = -1;
    static final int C_OUT = -2;
    private static final int NONE = -3;

    /** One exit pit per 168 x 168 blocks (7 x 7 cells), never in a region's outer cells. */
    static final ExitLocator EXITS = new ExitLocator(7 * CELL, 36, 3700);

    // ==========================================================================================
    // Public queries
    // ==========================================================================================

    /**
     * Fills {@code out} (indexed by y, 0..63) with the codes of column (x, z). {@code site} is the
     * anomalous area covering the column (from {@link Level37AnomalyPlan}), or null.
     */
    public void column(long seed, int x, int z, @Nullable AnomalySite site, int[] out) {
        Col c = new Col();
        plan(seed, x, z, site, c);
        write(c, out);
        if (site != null) {
            Level37AnomalyPlan.build(site, x, z, out);
        }
    }

    /** True when you can stand at feet level (y41) in column (x, z): solid floor, two free blocks. */
    public boolean isWalkable(long seed, int x, int z, @Nullable AnomalySite site) {
        int[] out = new int[64];
        column(seed, x, z, site, out);
        int floor = type(out[FLOOR_Y]);
        int feet = type(out[FEET_Y]);
        int head = type(out[FEET_Y + 1]);
        // (handrails and the invisible light blocks are walk-through)
        return (floor == FLOOR || floor == WALL) && (feet == AIR || feet == SHALLOW) && (head == AIR || head == RAIL || head == LIGHT);
    }

    /** Centre {x, z} of the exit pit nearest to (x, z). */
    public int[] pitCentre(long seed, int x, int z) {
        int[] exit = EXITS.nearest(seed, x, z);
        return new int[] {Math.floorDiv(exit[0], CELL) * CELL + MID, Math.floorDiv(exit[1], CELL) * CELL + MID};
    }

    /** Radius of the pit's water shaft (blocks around {@link #pitCentre}). */
    public static final int PIT_RADIUS = 4;

    // ==========================================================================================
    // Rooms
    // ==========================================================================================

    private static int pack(int kind, boolean alongX, int variant, int height) {
        return kind | (alongX ? 16 : 0) | (variant << 5) | (height << 9);
    }

    static int kind(int room) {
        return room & 15;
    }

    static boolean alongX(int room) {
        return (room & 16) != 0;
    }

    static int variant(int room) {
        return (room >> 5) & 15;
    }

    /** Air layers between floor and ceiling (for round rooms: the height of the wall). */
    private static int height(int room) {
        return (room >> 9) & 31;
    }

    /** Ankle-deep water over the floor? Everywhere except pool decks and the colonnade walkway. */
    private static boolean flooded(int room) {
        return kind(room) != K_POOL && !(kind(room) == K_HALL && variant(room) == HALL_COLONNADE);
    }

    private static boolean isPitCell(long seed, int cx, int cz) {
        int[] exit = exitOfRegion(seed, cx * CELL + MID, cz * CELL + MID);
        return Math.floorDiv(exit[0], CELL) == cx && Math.floorDiv(exit[1], CELL) == cz;
    }

    private static int[] exitOfRegion(long seed, int x, int z) {
        int size = 7 * CELL;
        int rx = Math.floorDiv(x, size);
        int rz = Math.floorDiv(z, size);
        return new int[] {EXITS.exitX(seed, rx, rz), EXITS.exitZ(seed, rx, rz)};
    }

    private static boolean isHall(long seed, int cx, int cz) {
        int zx = Math.floorDiv(cx, 2);
        int zz = Math.floorDiv(cz, 2);
        if (Hash.unit(seed, zx, zz, 3701) >= HALL_SHARE) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (isPitCell(seed, zx * 2 + (i & 1), zz * 2 + (i >> 1))) {
                return false;
            }
        }
        return true;
    }

    static int room(long seed, int cx, int cz) {
        if (isPitCell(seed, cx, cz)) {
            return pack(K_PIT, false, 0, 6);
        }
        if (isHall(seed, cx, cz)) {
            boolean forest = Hash.unit(seed, Math.floorDiv(cx, 2), Math.floorDiv(cz, 2), 3702) < 0.55;
            return pack(K_HALL, false, forest ? HALL_FOREST : HALL_COLONNADE, 10);
        }
        boolean xRun = Hash.unit(seed, Math.floorDiv(cx, RUN), cz, 3703) < RUN_SHARE;
        boolean zRun = Hash.unit(seed, cx, Math.floorDiv(cz, RUN), 3704) < RUN_SHARE;
        if (xRun || zRun) {
            boolean alongX = xRun && zRun ? Hash.unit(seed, cx, cz, 3705) < 0.5 : xRun;
            // The whole run shares one look.
            double v = alongX ? Hash.unit(seed, Math.floorDiv(cx, RUN), cz, 3706) : Hash.unit(seed, cx, Math.floorDiv(cz, RUN), 3707);
            int variant = v < 0.45 ? COR_SLITS : v < 0.7 ? COR_SKY : COR_VAULT;
            return pack(K_CORRIDOR, alongX, variant, 5);
        }
        double r = Hash.unit(seed, cx, cz, 3708);
        boolean alongX = Hash.unit(seed, cx, cz, 3709) < 0.5;
        if (r < 0.08) {
            return pack(K_ROTUNDA, alongX, 0, 6);
        }
        if (r < 0.17) {
            return pack(K_TUNNEL, alongX, 0, 8);
        }
        if (r < 0.24) {
            return pack(K_SPIRAL, alongX, 0, 7);
        }
        if (r < 0.42) {
            return pack(K_POOL, alongX, 0, 7);
        }
        return pack(K_ROOM, alongX, 0, Hash.unit(seed, cx, cz, 3710) < 0.5 ? 6 : 7);
    }

    // ==========================================================================================
    // Edges (packed: bits 0-1 state, 2-6 start, 7-9 width, 10-16 spring Y, bit 17 vault)
    // ==========================================================================================

    private static int packEdge(int state, int start, int width, int spring, boolean vault) {
        return state | (start << 2) | (width << 7) | (spring << 10) | (vault ? 1 << 17 : 0);
    }

    static int edgeState(int edge) {
        return edge & 3;
    }

    static int edgeStart(int edge) {
        return (edge >> 2) & 31;
    }

    static int edgeWidth(int edge) {
        return (edge >> 7) & 7;
    }

    private static int edgeSpring(int edge) {
        return (edge >> 10) & 127;
    }

    private static boolean edgeVault(int edge) {
        return (edge & (1 << 17)) != 0;
    }

    /**
     * Round rooms and the spiral want their passages in the middle of each side, the tunnel hall
     * at both ends of its axis (a passage through this wall runs along X if {@code crossX}).
     */
    private static boolean centred(int room, boolean crossX) {
        int k = kind(room);
        return k == K_ROTUNDA || k == K_PIT || k == K_SPIRAL || k == K_TUNNEL && alongX(room) == crossX;
    }

    /** The wall between cell (cx, cz) and its neighbour to the west or north. */
    static int edge(long seed, int cx, int cz, int side) {
        int ncx = side == WEST ? cx - 1 : cx;
        int ncz = side == WEST ? cz : cz - 1;
        int a = room(seed, cx, cz);
        int b = room(seed, ncx, ncz);
        if (kind(a) == K_HALL && kind(b) == K_HALL
                && Math.floorDiv(cx, 2) == Math.floorDiv(ncx, 2) && Math.floorDiv(cz, 2) == Math.floorDiv(ncz, 2)) {
            return packEdge(E_OPEN, 0, 0, 0, false);
        }
        boolean crossX = side == WEST; // a passage through this wall runs along X
        boolean endA = kind(a) == K_CORRIDOR && alongX(a) == crossX;
        boolean endB = kind(b) == K_CORRIDOR && alongX(b) == crossX;
        if (endA && endB) {
            boolean vault = variant(a) == COR_VAULT || variant(b) == COR_VAULT;
            return packEdge(E_JOIN, MID - 2, 4, CORRIDOR_SPRING_Y, vault);
        }
        boolean tunnelEnd = (kind(a) == K_TUNNEL && alongX(a) == crossX) || (kind(b) == K_TUNNEL && alongX(b) == crossX);
        boolean open = endA || endB || tunnelEnd
                || Hash.unit(seed, cx, cz, 3711 + side) >= SOLID_EDGE_SHARE
                // Every cell keeps one way out to the west or the north, so no room is ever sealed.
                || (Hash.unit(seed, cx, cz, 3715) < 0.5 ? WEST : NORTH) == side;
        if (!open) {
            return packEdge(E_SOLID, 0, 0, 0, false);
        }
        int start;
        int width;
        if (endA || endB || centred(a, crossX) || centred(b, crossX)) {
            start = MID - 2;
            width = 4;
        } else {
            long h = Hash.of(seed, cx, cz, 3713 + side);
            width = Hash.unit(h) < 0.4 ? 2 : 4;
            start = 6 + Hash.range(Hash.mix(h), 21 - width - 6 + 1);
        }
        int radius = width / 2;
        // As tall as the lower room allows, with at least one row of wall left above the arch.
        int spring = FEET_Y + Math.min(7, Math.min(height(a), height(b))) - 1 - radius;
        return packEdge(E_ARCH, start, width, spring, false);
    }

    // ==========================================================================================
    // Shape tests
    // ==========================================================================================

    private static int pieceId(CurveShape shape, CurveOrientation orientation) {
        return shape.ordinal() | (orientation.ordinal() << 6);
    }

    /**
     * Cell at offset (dx, dz) from a circle's centre corner (the cell spans dx..dx+1): C_IN if
     * it lies fully inside the circle, C_OUT if fully outside, else the upright piece for it.
     */
    private static int circle(int dx, int dz, int radius, boolean convex) {
        int xi = dx >= 0 ? dx : -dx - 1;
        int zi = dz >= 0 ? dz : -dz - 1;
        if (Math.hypot(xi + 1, zi + 1) <= radius) {
            return C_IN;
        }
        if (Math.hypot(xi, zi) >= radius) {
            return C_OUT;
        }
        boolean east = dx >= 0;
        boolean south = dz >= 0;
        // The SE model turned a quarter (SW) or three quarters (NE) swaps its two axes.
        boolean swap = east != south;
        CurveShape shape = CurveShape.of(convex, radius, swap ? zi : xi, swap ? xi : zi);
        return pieceId(shape, CurveOrientation.upright(east, south));
    }

    /**
     * Cell of an arch: {@code a} = the cell's offset across the opening from its middle corner,
     * {@code j} = height above the spring line. C_IN = open, C_OUT = solid, else an arch piece.
     */
    static int arch(int a, int j, int radius, boolean alongX) {
        int i = a >= 0 ? a : -a - 1;
        if (i >= radius) {
            return C_OUT;
        }
        if (j < 0) {
            return C_IN;
        }
        if (Math.hypot(i + 1, j + 1) <= radius) {
            return C_IN;
        }
        if (Math.hypot(i, j) >= radius) {
            return C_OUT;
        }
        return pieceId(CurveShape.of(false, radius, i, j), CurveOrientation.arch(alongX, a >= 0));
    }

    /** Rectangle of cells [x0..x1] x [z0..z1] with corners rounded to radius r: C_IN, C_OUT or a corner piece. */
    private static int roundedRect(int x, int z, int x0, int x1, int z0, int z1, int r) {
        if (x < x0 || x > x1 || z < z0 || z > z1) {
            return C_OUT;
        }
        if (r > 0) {
            int ccx = x < x0 + r ? x0 + r : x > x1 - r ? x1 + 1 - r : Integer.MIN_VALUE;
            int ccz = z < z0 + r ? z0 + r : z > z1 - r ? z1 + 1 - r : Integer.MIN_VALUE;
            if (ccx != Integer.MIN_VALUE && ccz != Integer.MIN_VALUE) {
                return circle(x - ccx, z - ccz, r, false);
            }
        }
        return C_IN;
    }

    /**
     * A wavy wall on one side (0 W, 1 E, 2 N, 3 S) of the rectangle [x0..x1] x [z0..z1]: half
     * circles of radius 3 bulging into the room alternating with half circles cut back into the
     * wall, a smooth S-wave 12 blocks long. NONE outside the 6-deep band it occupies.
     */
    private static int wave(int side, int x, int z, int x0, int x1, int z0, int z1, int phase) {
        boolean xSide = side < 2;
        int d = side == 0 ? x - x0 : side == 1 ? x1 - x : side == 2 ? z - z0 : z1 - z;
        if (d < 0 || d > 5) {
            return NONE;
        }
        int t = xSide ? z : x;
        int k = Math.floorDiv(t - phase + 3, 6);
        int tc = phase + 6 * k;
        int base = side == 0 ? x0 + 3 : side == 1 ? x1 - 2 : side == 2 ? z0 + 3 : z1 - 2;
        int dx = xSide ? x - base : x - tc;
        int dz = xSide ? z - tc : z - base;
        int depth = xSide ? dx : dz;
        boolean wallSide = side == 0 || side == 2 ? depth < 0 : depth >= 0;
        if (Math.floorMod(k, 2) == 0) {
            // Bulge: solid inside the circle.
            if (wallSide) {
                return C_OUT;
            }
            int c = circle(dx, dz, 3, true);
            return c == C_IN ? C_OUT : c == C_OUT ? C_IN : c;
        }
        // Recess: open inside the circle.
        if (!wallSide) {
            return C_IN;
        }
        return circle(dx, dz, 3, false);
    }

    // ==========================================================================================
    // One column
    // ==========================================================================================

    private static final int F_DRY = 0;
    private static final int F_POOL = 1;
    private static final int F_PIT = 2;
    private static final int FILL_SOLID = 0;
    private static final int FILL_OPEN = 1;
    private static final int FILL_ARCH = 2;
    private static final int FILL_DOME = 3;

    /** What one column is made of, before it is written out as codes. */
    private static final class Col {
        int foundation = FOUNDATION_Y;
        int floor = F_DRY;
        boolean flooded;
        int fill = FILL_SOLID;
        /** A wall that a passage may cut its arched tunnel through. */
        boolean cuttable;
        /** Ceiling layer (FILL_OPEN), or the top of the solid (FILL_SOLID / FILL_ARCH). */
        int ceilY = TOP_Y;
        int piece = -1;
        int pieceFrom;
        int pieceTo;
        int archA;
        int archR;
        int archSpring;
        boolean archAlongX;
        int domeDx;
        int domeDz;
        /** An opening in the ceiling, from ceilY up to openTo (upperPiece or air), capped at openTo + 1. */
        int openTo = -1;
        int upperPiece = -1;
        int cap;
        final int[] extraY = new int[40];
        final int[] extraCode = new int[40];
        int extras;

        void extra(int y, int code) {
            if (extras < extraY.length) {
                extraY[extras] = y;
                extraCode[extras] = code;
                extras++;
            }
        }

        void solid(boolean cut) {
            fill = FILL_SOLID;
            ceilY = TOP_Y;
            cuttable = cut;
        }

        void open(int ceiling, boolean water) {
            fill = FILL_OPEN;
            ceilY = ceiling;
            flooded = water;
            cuttable = false;
        }

        void archOf(int a, int radius, int spring, boolean alongX, int top, boolean water) {
            fill = FILL_ARCH;
            archA = a;
            archR = radius;
            archSpring = spring;
            archAlongX = alongX;
            ceilY = top;
            flooded = water;
            cuttable = false;
        }

        void pieceOf(int id, int from, int to) {
            piece = id;
            pieceFrom = from;
            pieceTo = to;
        }

        void skylight(int top) {
            openTo = top;
            upperPiece = -1;
            cap = code(VOID, DOWN3);
        }
    }

    private void plan(long seed, int x, int z, @Nullable AnomalySite site, Col c) {
        int cx = Math.floorDiv(x, CELL);
        int cz = Math.floorDiv(z, CELL);
        int lx = Math.floorMod(x, CELL);
        int lz = Math.floorMod(z, CELL);
        int room = room(seed, cx, cz);
        if (kind(room) == K_HALL) {
            int hx = Math.floorMod(x, HALL_SIZE);
            int hz = Math.floorMod(z, HALL_SIZE);
            if (hx >= BAND && hz >= BAND) {
                hall(seed, x, z, hx, hz, room, c);
                lanes(seed, cx, cz, lx, lz, room, c);
                return;
            }
        }
        if (lx < BAND && lz < BAND) {
            c.solid(false); // the post where four walls meet
            return;
        }
        // A looping tunnel (see Level37AnomalyPlan) is a sealed tube: no side passage may open into
        // it, or the stretches it swaps you between would not look the same. (Its side walls stay
        // solid; a neighbour's passage toward it just ends in a blind arch.)
        boolean sealed = site != null && site.kind().equals(Level37AnomalyPlan.LOOPING_TUNNEL);
        if (lx < BAND) {
            if (sealed && !alongX(room)) {
                c.solid(false);
            } else {
                band(seed, cx, cz, WEST, lz, c);
            }
            return;
        }
        if (lz < BAND) {
            if (sealed && alongX(room)) {
                c.solid(false);
            } else {
                band(seed, cx, cz, NORTH, lx, c);
            }
            return;
        }
        switch (kind(room)) {
            case K_CORRIDOR -> corridor(seed, cx, cz, lx, lz, room, c);
            case K_ROTUNDA, K_PIT -> rotunda(seed, cx, cz, lx, lz, room, c);
            case K_TUNNEL -> tunnel(seed, cx, cz, lx, lz, room, c);
            case K_SPIRAL -> spiral(seed, cx, cz, lx, lz, c);
            case K_POOL -> pool(seed, cx, cz, lx, lz, room, c);
            default -> room(seed, cx, cz, lx, lz, room, c);
        }
        if (!sealed) {
            lanes(seed, cx, cz, lx, lz, room, c);
        }
    }

    /** A column of the 3-thick wall on a cell's west or north edge. */
    private static void band(long seed, int cx, int cz, int side, int along, Col c) {
        int e = edge(seed, cx, cz, side);
        boolean alongX = side == WEST;
        int state = edgeState(e);
        if (state == E_JOIN) {
            if (along >= MID - 2 && along <= MID + 1) {
                corridorProfile(c, along - MID, edgeVault(e), alongX);
                return;
            }
        } else if (state == E_ARCH) {
            int start = edgeStart(e);
            int width = edgeWidth(e);
            if (along >= start && along < start + width) {
                int radius = width / 2;
                int a = room(seed, cx, cz);
                int b = room(seed, side == WEST ? cx - 1 : cx, side == WEST ? cz : cz - 1);
                c.archOf(along - (start + radius), radius, edgeSpring(e), alongX, TOP_Y, flooded(a) && flooded(b));
                return;
            }
        }
        c.solid(false);
    }

    /** The cross-section of a corridor at offset {@code a} (-2..1) from its middle. */
    private static void corridorProfile(Col c, int a, boolean vault, boolean alongX) {
        if (vault) {
            c.archOf(a, 2, CORRIDOR_SPRING_Y, alongX, CORRIDOR_CEIL_Y, true);
        } else {
            c.open(CORRIDOR_CEIL_Y, true);
        }
    }

    /**
     * Passages ("lanes"): a doorway in any of the cell's four walls carries on as an arched tunnel
     * through every bit of thick wall between it and the middle of the cell, so it always reaches
     * the open part of the room.
     */
    private static void lanes(long seed, int cx, int cz, int lx, int lz, int room, Col c) {
        if (!c.cuttable) {
            return;
        }
        if (lx >= BAND && lx < MID && lane(c, edge(seed, cx, cz, WEST), lz, true, room)) {
            return;
        }
        if (lx >= MID && lane(c, edge(seed, cx + 1, cz, WEST), lz, true, room)) {
            return;
        }
        if (lz >= BAND && lz < MID && lane(c, edge(seed, cx, cz, NORTH), lx, false, room)) {
            return;
        }
        if (lz >= MID) {
            lane(c, edge(seed, cx, cz + 1, NORTH), lx, false, room);
        }
    }

    private static boolean lane(Col c, int e, int along, boolean alongX, int room) {
        if (edgeState(e) != E_ARCH) {
            return false;
        }
        int start = edgeStart(e);
        int width = edgeWidth(e);
        if (along < start || along >= start + width) {
            return false;
        }
        int radius = width / 2;
        int top = Math.max(c.ceilY, edgeSpring(e) + radius);
        c.floor = F_DRY;
        c.foundation = Math.min(c.foundation, FOUNDATION_Y);
        c.piece = -1;
        c.extras = 0;
        c.openTo = -1;
        c.archOf(along - (start + radius), radius, edgeSpring(e), alongX, top, flooded(room));
        return true;
    }

    // ---- Slit windows -----------------------------------------------------------------------

    /**
     * Windows of sunlight in a 2-thick wall on side {@code side} (0 W, 1 E, 2 N, 3 S) of a room
     * whose first interior cell on that side is {@code edge}: the outer layer holds the sunlit
     * void, the inner layer is the window's recess. A window wherever
     * {@code (along - phase) mod pitch < width}, open over the heights in {@code ys}.
     *
     * @return true if (x, z) is part of that 2-thick wall (c is then filled in)
     */
    private static boolean windows(long seed, int wx, int wz, int side, int x, int z, int edge, int phase, int pitch, int width,
                                   int yFrom, int yTo, int alongMin, int alongMax, Col c) {
        boolean xSide = side < 2;
        int depth = side == 0 ? x - edge : side == 1 ? edge - x : side == 2 ? z - edge : edge - z;
        if (depth < 0 || depth > 1) {
            return false;
        }
        int along = xSide ? z : x;
        c.solid(true);
        if (along < alongMin || along > alongMax || Math.floorMod(along - phase, pitch) >= width
                || nearPassage(seed, wx, wz, side)) {
            return true;
        }
        int facing = side == 0 ? EAST3 : side == 1 ? WEST3 : side == 2 ? SOUTH3 : NORTH3;
        for (int y = yFrom; y <= yTo; y++) {
            c.extra(y, depth == 0 ? code(VOID, facing) : AIR);
        }
        return true;
    }

    // Every window range starts and ends at least 7 blocks from a room's corner: the rounded
    // corners curve up to 6 blocks in (a radius-3 curve starting at the 3-thick wall), and a slit
    // cut into the curve comes out as a sliced, slanting recess.
    /** Windows keep this many blocks of plain wall either side of a passage through the same wall. */
    private static final int WINDOW_PASSAGE_MARGIN = 2;

    /**
     * True when world column (wx, wz), in the wall on side {@code side} (0 W, 1 E, 2 N, 3 S) of its
     * cell, is within {@value #WINDOW_PASSAGE_MARGIN} blocks of the arched passage through that wall:
     * a window there would cut into the passage's arch.
     */
    private static boolean nearPassage(long seed, int wx, int wz, int side) {
        int cx = Math.floorDiv(wx, CELL);
        int cz = Math.floorDiv(wz, CELL);
        int e = side == 0 ? edge(seed, cx, cz, WEST) : side == 1 ? edge(seed, cx + 1, cz, WEST)
                : side == 2 ? edge(seed, cx, cz, NORTH) : edge(seed, cx, cz + 1, NORTH);
        if (edgeState(e) != E_ARCH) {
            return false;
        }
        int along = side < 2 ? Math.floorMod(wz, CELL) : Math.floorMod(wx, CELL);
        return along >= edgeStart(e) - WINDOW_PASSAGE_MARGIN && along < edgeStart(e) + edgeWidth(e) + WINDOW_PASSAGE_MARGIN;
    }

    // ---- Handrails ----------------------------------------------------------------------------

    /** A handrail along the inside of wall {@code side} of a room with interior [x0..x1] x [z0..z1]. */
    private static void handrail(long seed, int cx, int cz, int side, int x, int z, int x0, int x1, int z0, int z1,
                                 int margin, Col c) {
        boolean xSide = side < 2;
        int inner = side == 0 ? x0 : side == 1 ? x1 : side == 2 ? z0 : z1;
        if ((xSide ? x : z) != inner) {
            return;
        }
        int along = xSide ? z : x;
        int lo = xSide ? z0 : x0;
        int hi = xSide ? z1 : x1;
        if (along < lo + margin || along > hi - margin) {
            return;
        }
        // Not across a doorway in that wall.
        int e = side == 0 ? edge(seed, cx, cz, WEST) : side == 1 ? edge(seed, cx + 1, cz, WEST)
                : side == 2 ? edge(seed, cx, cz, NORTH) : edge(seed, cx, cz + 1, NORTH);
        if (edgeState(e) != E_SOLID && along >= edgeStart(e) - 1 && along <= edgeStart(e) + edgeWidth(e)) {
            return;
        }
        int facing = side == 0 ? E2 : side == 1 ? W2 : side == 2 ? S2 : N2;
        c.extra(FEET_Y + 1, code(RAIL, facing | (Math.floorMod(along, 3) == 0 ? 4 : 0)));
    }

    // ---- Ordinary room ------------------------------------------------------------------------

    /** The side (0 W, 1 E, 2 N, 3 S) of an ordinary room that has the tall slit windows, or -1. */
    static int roomSlitSide(long seed, int cx, int cz) {
        long h = Hash.of(seed, cx, cz, 3720);
        return Hash.unit(h) < 0.55 ? Hash.range(Hash.mix(h), 4) : -1;
    }

    private static void room(long seed, int cx, int cz, int lx, int lz, int room, Col c) {
        long h = Hash.of(seed, cx, cz, 3720);
        int ceil = FEET_Y + height(room);
        int slitSide = roomSlitSide(seed, cx, cz);
        int waveSide = Hash.unit(Hash.mix(h ^ 1)) < 0.4 ? Hash.range(Hash.mix(h ^ 2), 4) : -1;
        if (waveSide == slitSide) {
            waveSide = -1;
        }
        int corner = Hash.unit(Hash.mix(h ^ 3)) < 0.5 ? 2 : 3;
        boolean pillars = waveSide < 0 && Hash.unit(Hash.mix(h ^ 4)) < 0.35;
        boolean plunge = Hash.unit(Hash.mix(h ^ 5)) < 0.25;
        int railSide = Hash.unit(Hash.mix(h ^ 7)) < 0.3 ? Hash.range(Hash.mix(h ^ 8), 4) : -1;
        if (railSide == slitSide || railSide == waveSide) {
            railSide = -1;
        }
        boolean alongX = alongX(room);
        int x0 = slitSide == 0 ? 5 : BAND;
        int x1 = slitSide == 1 ? CELL - 3 : CELL - 1;
        int z0 = slitSide == 2 ? 5 : BAND;
        int z1 = slitSide == 3 ? CELL - 3 : CELL - 1;

        if (slitSide >= 0) {
            int edgeCell = slitSide == 0 || slitSide == 2 ? BAND : CELL - 1;
            if (windows(seed, cx * CELL + lx, cz * CELL + lz, slitSide, lx, lz, edgeCell, Hash.range(Hash.mix(h ^ 9), 4), 4, 1, FEET_Y + 1, ceil - 2,
                    7, CELL - 5, c)) {
                return;
            }
        }
        int shape = waveSide >= 0 ? wave(waveSide, lx, lz, x0, x1, z0, z1, 5 + Hash.range(Hash.mix(h ^ 10), 6)) : NONE;
        if (shape == NONE || lx < x0 || lx > x1 || lz < z0 || lz > z1) {
            shape = roundedRect(lx, lz, x0, x1, z0, z1, corner);
        }
        if (shape == C_OUT) {
            c.solid(true);
            return;
        }
        c.open(ceil, true);
        if (shape >= 0) {
            c.pieceOf(shape, FEET_Y, ceil - 1);
            c.cuttable = true;
            return;
        }
        // A deep plunge pool with rounded corners in the middle of the room (no ledge).
        if (plunge) {
            int p = roundedRect(lx, lz, 8, 18, 8, 18, 2);
            if (p != C_OUT) {
                c.floor = F_POOL;
                if (p >= 0) {
                    c.pieceOf(p, POOL_BOTTOM_Y + 1, FLOOR_Y);
                } else if (lx == MID && lz == 8) {
                    c.extra(FLOOR_Y, code(LADDER, N2 | 4));
                }
            }
        }
        // A row of round pillars down the middle.
        if (pillars) {
            int along = alongX ? lx : lz;
            int across = alongX ? lz : lx;
            for (int p = 7; p <= 19; p += 6) {
                if ((along == p - 1 || along == p) && (across == MID - 1 || across == MID)) {
                    int dx = alongX ? lx - p : lx - MID;
                    int dz = alongX ? lz - MID : lz - p;
                    c.pieceOf(circle(dx, dz, 1, true), c.floor == F_POOL ? POOL_BOTTOM_Y + 1 : FEET_Y, ceil - 1);
                    return;
                }
            }
        }
        // Skylights: two slots of sun in the ceiling.
        for (int k = 0; k < 2; k++) {
            long s = Hash.of(seed, cx, cz, 3721 + k);
            if (Hash.unit(s) < 0.45) {
                boolean wide = Hash.unit(Hash.mix(s)) < 0.5;
                int sx = 8 + Hash.range(Hash.mix(s ^ 1), 8);
                int sz = 8 + Hash.range(Hash.mix(s ^ 2), 8);
                if (lx >= sx && lx < sx + (wide ? 4 : 2) && lz >= sz && lz < sz + (wide ? 2 : 4)) {
                    c.skylight(ceil);
                }
            }
        }
        if (Math.floorMod(lx, 6) == 4 && Math.floorMod(lz, 6) == 4 && c.openTo < 0) {
            fillLights(c, ceil);
        }
        if (railSide >= 0) {
            handrail(seed, cx, cz, railSide, lx, lz, x0, x1, z0, z1, corner + 1, c);
        }
    }

    // ---- Pool room ----------------------------------------------------------------------------

    /** The side (0 W, 1 E, 2 N, 3 S) of a pool room with the square windows high up, or -1. */
    static int poolWindowSide(long seed, int cx, int cz) {
        long h = Hash.of(seed, cx, cz, 3725);
        return Hash.unit(h) < 0.6 ? Hash.range(Hash.mix(h), 4) : -1;
    }

    private static void pool(long seed, int cx, int cz, int lx, int lz, int room, Col c) {
        long h = Hash.of(seed, cx, cz, 3725);
        int ceil = FEET_Y + height(room);
        int windowSide = poolWindowSide(seed, cx, cz);
        int x0 = windowSide == 0 ? 5 : BAND;
        int x1 = windowSide == 1 ? CELL - 3 : CELL - 1;
        int z0 = windowSide == 2 ? 5 : BAND;
        int z1 = windowSide == 3 ? CELL - 3 : CELL - 1;
        if (windowSide >= 0) {
            int edgeCell = windowSide == 0 || windowSide == 2 ? BAND : CELL - 1;
            // A grid of square windows high up (two rows), every other block.
            boolean wall = windows(seed, cx * CELL + lx, cz * CELL + lz, windowSide, lx, lz, edgeCell, 0, 2, 1, FEET_Y + 3, FEET_Y + 3, 6, CELL - 4, c);
            if (wall) {
                windows(seed, cx * CELL + lx, cz * CELL + lz, windowSide, lx, lz, edgeCell, 0, 2, 1, FEET_Y + 5, FEET_Y + 5, 6, CELL - 4, c);
                return;
            }
        }
        int shape = roundedRect(lx, lz, x0, x1, z0, z1, 2);
        if (shape == C_OUT) {
            c.solid(true);
            return;
        }
        c.open(ceil, false);
        if (shape >= 0) {
            c.pieceOf(shape, FEET_Y, ceil - 1);
            c.cuttable = true;
            return;
        }
        int deck = 3 + Hash.range(Hash.mix(h ^ 1), 2);
        int px0 = x0 + deck;
        int px1 = x1 - deck;
        int pz0 = z0 + deck;
        int pz1 = z1 - deck;
        int p = roundedRect(lx, lz, px0, px1, pz0, pz1, 3);
        boolean alongX = alongX(room);
        if (p != C_OUT) {
            c.floor = F_POOL;
            if (p >= 0) {
                c.pieceOf(p, POOL_BOTTOM_Y + 1, FLOOR_Y);
                return;
            }
            if (Math.floorMod(lx, 6) == 4 && Math.floorMod(lz, 6) == 4) {
                fillLights(c, ceil); // over the water too, so a colonnade in it is evenly lit
            }
            // Chrome ladders in the middle of the two long sides.
            int mid = alongX ? (px0 + px1) / 2 : (pz0 + pz1) / 2;
            if (alongX && lx == mid && (lz == pz0 || lz == pz1)) {
                c.extra(FLOOR_Y, code(LADDER, (lz == pz0 ? N2 : S2) | 4));
            } else if (!alongX && lz == mid && (lx == px0 || lx == px1)) {
                c.extra(FLOOR_Y, code(LADDER, (lx == px0 ? W2 : E2) | 4));
            }
            // A colonnade standing in the water along one long side.
            if (Hash.unit(Hash.mix(h ^ 2)) < 0.4) {
                int along = alongX ? lx : lz;
                int across = alongX ? lz : lx;
                int line = (alongX ? pz1 : px1) - 2; // pillar centre corner, 2 cells in from the edge
                int first = (alongX ? px0 : pz0) + 3;
                int last = (alongX ? px1 : pz1) - 2;
                for (int q = first; q <= last; q += 5) {
                    if ((along == q - 1 || along == q) && (across == line - 1 || across == line)) {
                        int dx = alongX ? lx - q : lx - line;
                        int dz = alongX ? lz - line : lz - q;
                        c.extras = 0;
                        c.pieceOf(circle(dx, dz, 1, true), POOL_BOTTOM_Y + 1, ceil - 1);
                        return;
                    }
                }
            }
            return;
        }
        // The dry deck.
        if (Math.floorMod(lx, 6) == 4 && Math.floorMod(lz, 6) == 4) {
            fillLights(c, ceil);
        }
        if (lx == MID + 4 && lz == z0 + 1 && Hash.unit(Hash.mix(h ^ 3)) < 0.17) {
            c.extra(FEET_Y, code(BACKPACK, Hash.range(Hash.mix(h ^ 4), 4)));
            return;
        }
        for (int side = 0; side < 4; side++) {
            if (side != windowSide && (side < 2) == alongX) {
                handrail(seed, cx, cz, side, lx, lz, x0, x1, z0, z1, 4, c);
            }
        }
    }

    // ---- Corridor -----------------------------------------------------------------------------

    private static void corridor(long seed, int cx, int cz, int lx, int lz, int room, Col c) {
        boolean alongX = alongX(room);
        int along = alongX ? lx : lz;
        int across = alongX ? lz : lx;
        int variant = variant(room);
        // Per run: which wall has the windows, and where they fall.
        long rh = alongX ? Hash.of(seed, Math.floorDiv(cx, RUN), cz, 3730) : Hash.of(seed, cx, Math.floorDiv(cz, RUN), 3731);
        boolean lowSide = Hash.unit(rh) < 0.5;
        int phase = Hash.range(Hash.mix(rh), 12);
        if (across >= MID - 2 && across <= MID + 1) {
            corridorProfile(c, across - MID, variant == COR_VAULT, alongX);
            if (variant == COR_SKY && across == (lowSide ? MID - 2 : MID + 1) && Math.floorMod(along + phase, 6) < 4) {
                c.skylight(CORRIDOR_CEIL_Y); // a long slot of sun near one wall
            }
            if (variant != COR_VAULT) {
                // The handrail runs along the wall without windows.
                boolean railLow = variant == COR_SLITS ? !lowSide : lowSide;
                // (not across a side passage in that wall)
                int side = alongX ? (railLow ? edge(seed, cx, cz, NORTH) : edge(seed, cx, cz + 1, NORTH))
                        : (railLow ? edge(seed, cx, cz, WEST) : edge(seed, cx + 1, cz, WEST));
                boolean passage = edgeState(side) == E_ARCH && along >= edgeStart(side) - 1
                        && along <= edgeStart(side) + edgeWidth(side);
                if (!passage && across == (railLow ? MID - 2 : MID + 1)) {
                    int facing = alongX ? (railLow ? S2 : N2) : (railLow ? E2 : W2);
                    c.extra(FEET_Y + 1, code(RAIL, facing | (Math.floorMod(along, 3) == 0 ? 4 : 0)));
                }
            }
            return;
        }
        c.solid(true);
        if (variant == COR_SLITS) {
            // Tall slit windows every 4 blocks: void in the second layer of wall, recess in the first.
            int voidAt = lowSide ? MID - 4 : MID + 3;
            int recessAt = lowSide ? MID - 3 : MID + 2;
            // Window wall: the north (or west) wall when lowSide, else the south (or east) one.
            int wallSide = alongX ? (lowSide ? 2 : 3) : (lowSide ? 0 : 1);
            if ((across == voidAt || across == recessAt) && Math.floorMod(along + phase, 4) == 0
                    && !nearPassage(seed, cx * CELL + lx, cz * CELL + lz, wallSide)) {
                int facing = alongX ? (lowSide ? SOUTH3 : NORTH3) : (lowSide ? EAST3 : WEST3);
                for (int y = FEET_Y + 1; y <= FEET_Y + 3; y++) {
                    c.extra(y, across == voidAt ? code(VOID, facing) : AIR);
                }
            }
        }
    }

    // ---- Rotunda and exit pit -----------------------------------------------------------------

    /** Radius of the round room; the stepped dome above it closes in ring by ring to the oculus. */
    private static final int ROTUNDA_RADIUS = 6;
    /** Ring radii of the dome for the layers above the wall (y47, y48, ...). */
    private static final int[] DOME_RINGS = {4, 3, 2, 1};

    private static void rotunda(long seed, int cx, int cz, int lx, int lz, int room, Col c) {
        boolean pit = kind(room) == K_PIT;
        int dx = lx - MID;
        int dz = lz - MID;
        if (pit) {
            c.foundation = PIT_FOUNDATION_Y;
        }
        // Glowing arched alcoves on the sides without a passage.
        if (alcove(seed, cx, cz, lx, lz, c)) {
            return;
        }
        int wall = circle(dx, dz, ROTUNDA_RADIUS, false);
        if (wall == C_OUT) {
            c.solid(true);
            return;
        }
        c.fill = FILL_DOME;
        c.domeDx = dx;
        c.domeDz = dz;
        c.flooded = true;
        c.ceilY = TOP_Y;
        c.cuttable = wall >= 0; // passages cut through the round wall
        int basin = circle(dx, dz, PIT_RADIUS, false);
        int xi = dx >= 0 ? dx : -dx - 1;
        int zi = dz >= 0 ? dz : -dz - 1;
        if (basin != C_OUT) {
            c.floor = pit ? F_PIT : F_POOL;
            int bottom = pit ? PIT_BOTTOM_Y : POOL_BOTTOM_Y;
            if (!pit && dx == 0 && (dz == -PIT_RADIUS || dz == PIT_RADIUS - 1)) {
                // Two chrome ladders on opposite sides of the round pool, hanging on its rim (the
                // rim cell is left square for it: the circle only cuts a sliver off it).
                c.extra(FLOOR_Y, code(LADDER, (dz < 0 ? N2 : S2) | 4));
            } else if (basin >= 0) {
                c.pieceOf(basin, bottom + 1, FLOOR_Y);
            } else if (pit) {
                pitTreads(dx, dz, c);
            }
        } else if (xi == 4 && zi == 0 || zi == 4 && xi == 0) {
            fillLights(c, FEET_Y + 6); // soft fill around the deck, under the dome
        }
    }

    /** Arched alcove 2 wide in the round wall, glowing at the back. False if (lx, lz) isn't part of one. */
    private static boolean alcove(long seed, int cx, int cz, int lx, int lz, Col c) {
        int side;
        int depth;
        int across;
        if (lz == MID - 1 || lz == MID) {
            across = lz;
            if (lx >= BAND && lx <= MID - ROTUNDA_RADIUS) {
                side = 0;
                depth = lx;
            } else if (lx >= MID + ROTUNDA_RADIUS - 1 && lx <= CELL - 1) {
                side = 1;
                depth = lx;
            } else {
                return false;
            }
        } else if (lx == MID - 1 || lx == MID) {
            across = lx;
            if (lz >= BAND && lz <= MID - ROTUNDA_RADIUS) {
                side = 2;
                depth = lz;
            } else if (lz >= MID + ROTUNDA_RADIUS - 1 && lz <= CELL - 1) {
                side = 3;
                depth = lz;
            } else {
                return false;
            }
        } else {
            return false;
        }
        int e = side == 0 ? edge(seed, cx, cz, WEST) : side == 1 ? edge(seed, cx + 1, cz, WEST)
                : side == 2 ? edge(seed, cx, cz, NORTH) : edge(seed, cx, cz + 1, NORTH);
        if (edgeState(e) != E_SOLID) {
            return false; // a passage comes in on this side instead
        }
        boolean back = side == 0 || side == 2 ? depth == BAND : depth == CELL - 1;
        int spring = FEET_Y + 3;
        if (back) {
            c.solid(false);
            int facing = side == 0 ? EAST3 : side == 1 ? WEST3 : side == 2 ? SOUTH3 : NORTH3;
            for (int y = FEET_Y; y <= spring; y++) {
                c.extra(y, code(VOID, facing));
            }
            return true;
        }
        c.archOf(across - MID, 1, spring, side < 2, TOP_Y, true);
        return true;
    }

    /** Ring of cells around the pit's open middle that carry the descending treads, in walking order. */
    private static final int[][] PIT_RING = ring(PIT_RADIUS, 3.0);
    /** Treads per pit column at most: 1.5 turns down. */
    private static final int PIT_TREADS = 24;

    /** Cells fully inside a circle of radius r whose far corner lies beyond {@code inner}, ordered by angle. */
    private static int[][] ring(int r, double inner) {
        java.util.List<int[]> cells = new java.util.ArrayList<>();
        for (int dx = -r; dx < r; dx++) {
            for (int dz = -r; dz < r; dz++) {
                int xi = dx >= 0 ? dx : -dx - 1;
                int zi = dz >= 0 ? dz : -dz - 1;
                double far = Math.hypot(xi + 1, zi + 1);
                if (far <= r && far > inner) {
                    cells.add(new int[] {dx, dz});
                }
            }
        }
        cells.sort((p, q) -> Double.compare(Math.atan2(p[1] + 0.5, p[0] + 0.5), Math.atan2(q[1] + 0.5, q[0] + 0.5)));
        return cells.toArray(new int[0][]);
    }

    private static int ringIndex(int[][] ring, int dx, int dz) {
        for (int i = 0; i < ring.length; i++) {
            if (ring[i][0] == dx && ring[i][1] == dz) {
                return i;
            }
        }
        return -1;
    }

    /** Floating tile treads spiralling down the pit wall into the water, half a block per step. */
    private static void pitTreads(int dx, int dz, Col c) {
        int k0 = ringIndex(PIT_RING, dx, dz);
        if (k0 < 0) {
            return;
        }
        for (int k = k0 + 1; k <= PIT_TREADS; k += PIT_RING.length) {
            int twice = 2 * FEET_Y - k; // twice the tread's surface height
            tread(twice, c, true);
        }
    }

    /** A slab whose top surface is at twice/2 (a half-block step). */
    private static void tread(int twice, Col c, boolean waterlogged) {
        int y = (twice & 1) != 0 ? (twice - 1) / 2 : twice / 2 - 1;
        boolean top = (twice & 1) == 0;
        boolean wet = waterlogged && y <= FLOOR_Y;
        c.extra(y, code(SLAB, (top ? 1 : 0) | (wet ? 2 : 0) | (y == FEET_Y && !wet ? 4 : 0)));
    }

    // ---- Tunnel of receding arches ------------------------------------------------------------

    private static final int[] RING_ALONG = {7, 10, 13, 16};
    private static final int[] RING_RADIUS = {4, 4, 3, 3};
    private static final int RING_SPRING_Y = FEET_Y + 3;

    private static void tunnel(long seed, int cx, int cz, int lx, int lz, int room, Col c) {
        boolean alongX = alongX(room);
        int along = alongX ? lx : lz;
        int across = alongX ? lz : lx;
        int ceil = FEET_Y + height(room);
        // Windows along the low side wall light the near half; the far end stays dark.
        if (across <= BAND + 1) {
            if (windows(seed, cx * CELL + lx, cz * CELL + lz, alongX ? 2 : 0, lx, lz, BAND, 2, 3, 1, FEET_Y + 1, FEET_Y + 5, 4, 12, c)) {
                return;
            }
        }
        for (int i = 0; i < RING_ALONG.length; i++) {
            if (along == RING_ALONG[i]) {
                int a = across - MID;
                c.archOf(a, RING_RADIUS[i], RING_SPRING_Y, alongX, ceil, true);
                // A side passage may cut through the solid part of a ring wall, never its arch.
                c.cuttable = (a >= 0 ? a : -a - 1) >= RING_RADIUS[i];
                return;
            }
        }
        if (along > RING_ALONG[RING_ALONG.length - 1] + 2) {
            // The dark round tunnel at the far end, lined up with the passage out.
            int e = alongX ? edge(seed, cx + 1, cz, WEST) : edge(seed, cx, cz + 1, NORTH);
            if (across >= MID - 2 && across <= MID + 1) {
                int spring = edgeState(e) == E_ARCH ? edgeSpring(e) : RING_SPRING_Y;
                c.archOf(across - MID, 2, spring, alongX, ceil, true);
            } else {
                c.solid(true);
            }
            return;
        }
        // The hall itself (the low side wall is 2 thick for the windows).
        int shape = roundedRect(lx, lz, alongX ? BAND : BAND + 2, CELL - 1, alongX ? BAND + 2 : BAND, CELL - 1, 2);
        if (shape == C_OUT) {
            c.solid(true);
            return;
        }
        c.open(ceil, true);
        if (shape >= 0) {
            c.pieceOf(shape, FEET_Y, ceil - 1);
            c.cuttable = true;
            return;
        }
        if (along < 13 && Math.floorMod(across, 6) == 4 && Math.floorMod(along, 4) == 0) {
            fillLights(c, ceil); // the near half is lit; the round tunnel at the far end is not
        }
    }

    // ---- Spiral stair ----------------------------------------------------------------------------

    /** The 12 cells around the 2 x 2 pillar, in climbing order. */
    private static final int[][] SPIRAL_RING = spiralRing();
    /** Treads climb half a block per cell: 27 treads, then a landing of 4 at y55. */
    private static final int SPIRAL_TREADS = 27;
    private static final int SPIRAL_LANDING = 4;
    private static final int WELL_RADIUS = 3;

    private static int[][] spiralRing() {
        java.util.List<int[]> cells = new java.util.ArrayList<>();
        for (int dx = -2; dx <= 1; dx++) {
            for (int dz = -2; dz <= 1; dz++) {
                if (dx < -1 || dx > 0 || dz < -1 || dz > 0) {
                    cells.add(new int[] {dx, dz});
                }
            }
        }
        cells.sort((p, q) -> Double.compare(Math.atan2(p[1] + 0.5, p[0] + 0.5), Math.atan2(q[1] + 0.5, q[0] + 0.5)));
        return cells.toArray(new int[0][]);
    }

    private static void spiral(long seed, int cx, int cz, int lx, int lz, Col c) {
        long h = Hash.of(seed, cx, cz, 3735);
        int ceil = FEET_Y + 7;
        int slitSide = Hash.unit(h) < 0.3 ? Hash.range(Hash.mix(h), 4) : -1;
        int x0 = slitSide == 0 ? 5 : BAND;
        int x1 = slitSide == 1 ? CELL - 3 : CELL - 1;
        int z0 = slitSide == 2 ? 5 : BAND;
        int z1 = slitSide == 3 ? CELL - 3 : CELL - 1;
        if (slitSide >= 0) {
            int edgeCell = slitSide == 0 || slitSide == 2 ? BAND : CELL - 1;
            if (windows(seed, cx * CELL + lx, cz * CELL + lz, slitSide, lx, lz, edgeCell, 1, 4, 1, FEET_Y + 1, ceil - 2, 7, CELL - 5, c)) {
                return;
            }
        }
        int shape = roundedRect(lx, lz, x0, x1, z0, z1, 3);
        if (shape == C_OUT) {
            c.solid(true);
            return;
        }
        c.open(ceil, true);
        if (shape >= 0) {
            c.pieceOf(shape, FEET_Y, ceil - 1);
            c.cuttable = true;
            return;
        }
        int dx = lx - MID;
        int dz = lz - MID;
        // The light well: a round hole in the ceiling, a tiled shaft up to the sun.
        int well = circle(dx, dz, WELL_RADIUS, false);
        if (well != C_OUT) {
            c.openTo = WELL_CAP_Y - 1;
            c.upperPiece = well >= 0 ? well : -1;
            c.cap = code(VOID, DOWN3);
        }
        if (dx >= -1 && dx <= 0 && dz >= -1 && dz <= 0) {
            c.pieceOf(circle(dx, dz, 1, true), FEET_Y, WELL_CAP_Y - 1);
            return;
        }
        int k0 = ringIndex(SPIRAL_RING, dx, dz);
        if (k0 >= 0) {
            for (int k = k0; k <= SPIRAL_TREADS + SPIRAL_LANDING; k += SPIRAL_RING.length) {
                if (k == 0) {
                    continue; // the first cell is the flooded floor itself
                }
                int step = Math.min(k, SPIRAL_TREADS + 1);
                tread(2 * FEET_Y + step, c, false);
                if (k == SPIRAL_TREADS + 3 && Hash.unit(Hash.mix(h ^ 1)) < 0.25) {
                    c.extra(FEET_Y + (SPIRAL_TREADS + 1) / 2, code(BACKPACK, Hash.range(Hash.mix(h ^ 2), 4)));
                }
            }
            return;
        }
        if (well == C_OUT && Math.floorMod(lx, 6) == 4 && Math.floorMod(lz, 6) == 4) {
            fillLights(c, ceil);
        }
    }

    // ---- Halls --------------------------------------------------------------------------------

    /** Pillar centres (block corners) across a hall, every 6 blocks. */
    private static final int[] HALL_PILLARS = {8, 14, 20, 26, 32, 38, 44};

    private static void hall(long seed, int x, int z, int hx, int hz, int room, Col c) {
        int zx = Math.floorDiv(x, HALL_SIZE);
        int zz = Math.floorDiv(z, HALL_SIZE);
        long h = Hash.of(seed, zx, zz, 3740);
        int ceil = FEET_Y + 10;
        boolean forest = variant(room) == HALL_FOREST;
        int side = Hash.range(h, 4);
        int last = HALL_SIZE - 1;
        int x0 = side == 0 ? 5 : BAND;
        int x1 = side == 1 ? last - 2 : last;
        int z0 = side == 2 ? 5 : BAND;
        int z1 = side == 3 ? last - 2 : last;
        int edgeCell = side == 0 || side == 2 ? BAND : last;
        if (forest) {
            if (windows(seed, x, z, side, hx, hz, edgeCell, 2, 4, 1, FEET_Y + 1, ceil - 2, 7, last - 5, c)) {
                return;
            }
        } else if (windows(seed, x, z, side, hx, hz, edgeCell, 1, 5, 2, FEET_Y + 1, ceil - 3, 7, last - 6, c)) {
            return; // the tall arcade beside the walkway
        }
        int shape = roundedRect(hx, hz, x0, x1, z0, z1, 3);
        if (shape == C_OUT) {
            c.solid(true);
            return;
        }
        c.open(ceil, forest);
        // Deep water: pools between the pillars (forest) or everything beyond the walkway (colonnade).
        int pool;
        int px0Pool = 0;
        int px1Pool = -1;
        int pz0Pool = 0;
        int pz1Pool = -1;
        if (forest) {
            pool = C_OUT;
            for (int k = 0; k < 2 && pool == C_OUT; k++) {
                long p = Hash.of(seed, zx, zz, 3741 + k);
                if (Hash.unit(p) < 0.7) {
                    int i0 = 1 + Hash.range(Hash.mix(p), 3);
                    int j0 = 1 + Hash.range(Hash.mix(p ^ 1), 3);
                    pool = roundedRect(hx, hz, HALL_PILLARS[i0] - 3, HALL_PILLARS[i0 + 2] + 2,
                            HALL_PILLARS[j0] - 3, HALL_PILLARS[j0 + 2] + 2, 3);
                }
            }
        } else {
            int px0 = side == 0 ? x0 + 6 : x0;
            int px1 = side == 1 ? x1 - 6 : x1;
            int pz0 = side == 2 ? z0 + 6 : z0;
            int pz1 = side == 3 ? z1 - 6 : z1;
            pool = roundedRect(hx, hz, px0, px1, pz0, pz1, 3);
            px0Pool = px0;
            px1Pool = px1;
            pz0Pool = pz0;
            pz1Pool = pz1;
            // Ladders out of the pool onto the walkway, every 12 blocks.
            boolean xSide = side < 2;
            int along = xSide ? hz : hx;
            int edgeRow = side == 0 ? px0 : side == 1 ? px1 : side == 2 ? pz0 : pz1;
            if (pool == C_IN && (xSide ? hx : hz) == edgeRow && Math.floorMod(along, 12) == 6) {
                int facing = side == 0 ? W2 : side == 1 ? E2 : side == 2 ? N2 : S2;
                c.extra(FLOOR_Y, code(LADDER, facing | 4));
            }
        }
        if (pool != C_OUT) {
            c.floor = F_POOL;
        }
        if (shape >= 0) {
            c.pieceOf(shape, c.floor == F_POOL ? POOL_BOTTOM_Y + 1 : FEET_Y, ceil - 1);
            c.cuttable = true;
            return;
        }
        if (pool >= 0) {
            c.pieceOf(pool, POOL_BOTTOM_Y + 1, FLOOR_Y);
            return;
        }
        // Round pillars on a 6-block grid (in the colonnade only where they stand in the pool).
        for (int px : HALL_PILLARS) {
            if (hx != px - 1 && hx != px) {
                continue;
            }
            for (int pz : HALL_PILLARS) {
                if (hz != pz - 1 && hz != pz) {
                    continue;
                }
                if (px - 1 < x0 + 3 || px > x1 - 3 || pz - 1 < z0 + 3 || pz > z1 - 3) {
                    continue; // clear of the walls, so it never crowds an arch or a window
                }
                if (!forest && !pillarInPool(px - 1, pz - 1, px0Pool, px1Pool, pz0Pool, pz1Pool)) {
                    continue; // only whole pillars: never one half on the deck, half over the deep water
                }
                if (inFrontOfPassage(seed, x - hx, z - hz, px, pz, x0, x1, z0, z1)) {
                    continue; // nothing standing in a passage's way
                }
                c.extras = 0;
                c.pieceOf(circle(hx - px, hz - pz, 1, true), c.floor == F_POOL ? POOL_BOTTOM_Y + 1 : FEET_Y, ceil - 1);
                return;
            }
        }
        if (forest) {
            // Slots of sun in the ceiling between the pillars.
            for (int i = 0; i + 1 < HALL_PILLARS.length; i++) {
                int ax = HALL_PILLARS[i];
                if (hx <= ax || hx > ax + 4) {
                    continue;
                }
                for (int j = 0; j + 1 < HALL_PILLARS.length; j++) {
                    int az = HALL_PILLARS[j];
                    if (hz <= az || hz > az + 4) {
                        continue;
                    }
                    long s = Hash.of(seed, zx * 8 + i, zz * 8 + j, 3743);
                    if (Hash.unit(s) < 0.3) {
                        boolean wide = Hash.unit(Hash.mix(s)) < 0.5;
                        int ox = hx - ax - 1;
                        int oz = hz - az - 1;
                        if (wide ? oz >= 1 && oz <= 2 : ox >= 1 && ox <= 2) {
                            c.skylight(ceil);
                        }
                    }
                }
            }
        }
        if (c.openTo < 0 && Math.floorMod(hx, 6) == 5 && Math.floorMod(hz, 6) == 5) {
            fillLights(c, ceil);
        }
    }

    /** True when all four cells of the pillar whose north-west cell is (px, pz) are inside the pool. */
    private static boolean pillarInPool(int px, int pz, int x0, int x1, int z0, int z1) {
        return roundedRect(px, pz, x0, x1, z0, z1, 3) == C_IN && roundedRect(px + 1, pz, x0, x1, z0, z1, 3) == C_IN
                && roundedRect(px, pz + 1, x0, x1, z0, z1, 3) == C_IN && roundedRect(px + 1, pz + 1, x0, x1, z0, z1, 3) == C_IN;
    }

    /**
     * True when the hall pillar centred on corner (px, pz) (hall-local; the hall starts at world
     * (ox, oz)) stands within 4 blocks of one of the hall's walls and in line with a passage
     * through that wall: it would stand in the way and cut into the passage's arch.
     */
    private static boolean inFrontOfPassage(long seed, int ox, int oz, int px, int pz, int x0, int x1, int z0, int z1) {
        int reach = 4;
        for (int side = 0; side < 4; side++) {
            int gap = side == 0 ? px - 1 - x0 : side == 1 ? x1 - px : side == 2 ? pz - 1 - z0 : z1 - pz;
            if (gap > reach) {
                continue;
            }
            // Test both cells of the pillar along that wall, as world columns in the wall itself.
            for (int k = -1; k <= 0; k++) {
                // (A column inside the cell that owns that wall: west/north just outside the
                // interior, east/south on its last row, since a cell owns its east/south wall
                // through its neighbour's edge.)
                int wx = side == 0 ? ox + x0 - 1 : side == 1 ? ox + x1 : ox + px + k;
                int wz = side == 2 ? oz + z0 - 1 : side == 3 ? oz + z1 : oz + pz + k;
                if (nearPassage(seed, wx, wz, side)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Invisible vanilla light blocks (level 15) stacked up a column every 3 blocks, from just
     * under the ceiling down to knee height. Spread over the whole height like this, every spot
     * of a lit room stays at light 6 or more, where Level 37's fill is flat (see
     * {@code Level37Atmosphere.applyFill}): no light-level steps from block to block up a pillar.
     */
    private static void fillLights(Col c, int ceil) {
        for (int y = FEET_Y + 1; y < ceil - 1; y += 3) {
            c.extra(y, code(LIGHT, 15));
        }
        c.extra(ceil - 1, code(LIGHT, 15));
    }

    // ==========================================================================================
    // Writing the codes
    // ==========================================================================================

    private static void write(Col c, int[] out) {
        Arrays.fill(out, AIR);
        int wall = code(WALL, 0);
        int bottom = c.floor == F_POOL ? POOL_BOTTOM_Y : c.floor == F_PIT ? PIT_BOTTOM_Y : FLOOR_Y;
        for (int y = c.foundation; y < bottom; y++) {
            out[y] = wall;
        }
        out[bottom] = code(c.floor == F_PIT ? DEEP : FLOOR, 0);
        for (int y = bottom + 1; y <= FLOOR_Y; y++) {
            out[y] = code(WATER, 0);
        }
        int feet = c.flooded ? code(SHALLOW, 0) : AIR;
        switch (c.fill) {
            case FILL_SOLID -> {
                for (int y = FEET_Y; y <= c.ceilY; y++) {
                    out[y] = wall;
                }
            }
            case FILL_OPEN -> {
                out[FEET_Y] = feet;
                if (c.openTo >= c.ceilY) {
                    for (int y = c.ceilY; y <= c.openTo; y++) {
                        out[y] = c.upperPiece >= 0 ? code(PIECE, c.upperPiece) : AIR;
                    }
                    out[c.openTo + 1] = c.cap;
                } else {
                    out[c.ceilY] = code(CEIL, 0);
                }
            }
            case FILL_ARCH -> {
                for (int y = FEET_Y; y <= c.ceilY; y++) {
                    int a = arch(c.archA, y - c.archSpring, c.archR, c.archAlongX);
                    out[y] = a == C_IN ? (y == FEET_Y ? feet : AIR) : a == C_OUT ? wall : code(PIECE, a);
                }
            }
            case FILL_DOME -> dome(c, out, feet);
            default -> {
            }
        }
        if (c.piece >= 0) {
            for (int y = c.pieceFrom; y <= c.pieceTo; y++) {
                boolean waterlogged = y <= FLOOR_Y && c.floor != F_DRY;
                boolean flooded = y == FEET_Y && c.flooded;
                out[y] = code(PIECE, c.piece | (waterlogged ? 512 : 0) | (flooded ? 1024 : 0));
            }
        }
        for (int i = 0; i < c.extras; i++) {
            out[c.extraY[i]] = c.extraCode[i];
        }
    }

    /** A rotunda column: the round wall, then the stepped dome closing in to the oculus. */
    private static void dome(Col c, int[] out, int feet) {
        int dx = c.domeDx;
        int dz = c.domeDz;
        int wall = circle(dx, dz, ROTUNDA_RADIUS, false);
        int top = FEET_Y + 5;
        for (int y = FEET_Y; y <= top; y++) {
            out[y] = wall == C_IN ? (y == FEET_Y ? feet : AIR)
                    : code(PIECE, wall | (y == FEET_Y && c.flooded ? 1024 : 0));
        }
        int ceil = code(CEIL, 0);
        for (int i = 0; i < DOME_RINGS.length; i++) {
            int ring = circle(dx, dz, DOME_RINGS[i], false);
            out[top + 1 + i] = ring == C_IN ? AIR : ring == C_OUT ? ceil : code(PIECE, ring);
        }
        int oculusY = top + 1 + DOME_RINGS.length;
        out[oculusY] = circle(dx, dz, 1, false) != C_OUT ? code(VOID, DOWN3) : ceil;
    }
}
