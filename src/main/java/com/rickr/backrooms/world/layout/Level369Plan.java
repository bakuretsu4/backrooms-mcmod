package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Hash;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The floor plan of Level 369 ("Comely") as plain numbers: for any column (x, z) it says what goes
 * at every height as small integer codes (lawn, plaster of some colour, a flower of some kind...).
 * {@link Level369Layout} turns the codes into blocks. A pure function of (seed, x, z).
 *
 * <h2>Floor plan</h2>
 * A grid of 32 x 32 cells, one room each. The 1-thick wall between two cells lies on a cell's west
 * and north edge (cell-local 0). Every floor is lawn (y40) carpeted with grass tufts and flowers.
 * Rooms come in zones of 4 x 4 cells sharing one kind, so a pillar hall opens out about 128 blocks
 * across and a hallway runs about 128 blocks straight. Now and then (about one block of 3 x 3 zones
 * in eight) a huge area takes all of its zones: one pillar hall 384 across, or hallways 384 long.
 * The kinds:
 * <ul>
 *   <li><b>hallway</b> (white): a 5-wide corridor straight through the cell; a zone's hallways all
 *       run the same way, so they join into long corridors. Tall windows full of pale light down
 *       one side, closed doors down the other, light panels in the ceiling; blue flowers in the
 *       grass. Side passages lead off it now and then.</li>
 *   <li><b>pillar hall</b> (peach): low, wide halls of cream pillars under a salmon acoustic ceiling
 *       with light panels; a zone's cells open into one big hall; red flowers, and winding brick
 *       paths (the contour lines of a smooth noise).</li>
 *   <li><b>skylit room</b> (mint, lavender, butter, rose or white): tall rooms under skylight shafts
 *       (one big one, four small ones, or a long strip), bushes along the walls, sometimes a young
 *       tree under the light; flowers of every colour in patches. Neighbouring rooms sometimes open
 *       into each other.</li>
 *   <li><b>dark room</b> (sage, about 1 cell in 11): unlit, a single doorway, vines with small
 *       glowing flowers hanging from the ceiling and glowing plants in the grass.</li>
 * </ul>
 * The way out (one per 160 x 160 blocks, never in a region's outer cells): a skylit room with a
 * great arch of vines against its north wall, filled with a curtain of hanging flowers (the veil).
 * Walk into the veil and you come out in Level 7 ({@code Level369Rules}).
 *
 * <h2>Heights</h2>
 * Soil y36-39, lawn y40, feet y41. Doorways y41-43. Ceilings: pillar halls y45, hallways and dark
 * rooms y46, skylit rooms y48 with shafts up to the skylight glass at y52. Everything above the
 * ceilings is solid up to y53.
 */
public final class Level369Plan {
    // ==========================================================================================
    // Codes (bits 0-4 the type, the rest data)
    // ==========================================================================================
    public static final int AIR = 0;
    public static final int GRASS = 1;
    public static final int SOIL = 2;
    /** Plaster: data = colour ({@link #WHITE} ... {@link #DUSK}). */
    public static final int PLASTER = 3;
    /** Acoustic ceiling tile: data 0 white, 1 salmon. */
    public static final int CEIL = 4;
    public static final int PANEL = 5;
    public static final int SKYLIGHT = 6;
    public static final int WINDOW = 7;
    public static final int BRICK = 8;
    /** Flower: data = kind (0 blue, 1 red, 2 pink, 3 white). */
    public static final int FLOWER = 9;
    /** Grass tuft: data = kind. */
    public static final int TUFT = 10;
    /** Glowing plant: data = kind. */
    public static final int GLOW = 11;
    /** Hanging glowing vine: data 1 = the tip. */
    public static final int VINE = 12;
    public static final int LEAVES = 13;
    public static final int LOG = 14;
    /** Hallway door: data = facing (2D index) | upper half << 2. */
    public static final int DOOR = 15;
    /** Invisible vanilla light block: data = light level. */
    public static final int LIGHT = 16;
    public static final int VINE_WALL = 17;
    public static final int VEIL = 18;

    public static int type(int code) {
        return code & 31;
    }

    public static int data(int code) {
        return code >>> 5;
    }

    static int code(int type, int data) {
        return type | (data << 5);
    }

    // Plaster colours.
    public static final int WHITE = 0;
    public static final int PEACH = 1;
    public static final int MINT = 2;
    public static final int LAVENDER = 3;
    public static final int BUTTER = 4;
    public static final int ROSE = 5;
    public static final int DUSK = 6;
    private static final int[] GARDEN_COLOURS = {MINT, LAVENDER, BUTTER, ROSE, WHITE};

    // 2D direction indices, as Minecraft numbers them (Direction.get2DDataValue).
    private static final int S2 = 0;
    private static final int E2 = 3;

    // ==========================================================================================
    // Heights
    // ==========================================================================================
    static final int FLOOR_Y = LevelLayout.FLOOR_Y;
    static final int FEET_Y = FLOOR_Y + 1;
    private static final int SOIL_FROM = 36;
    /** Doorways and side passages are open up to here (3 high); the lintel is the block above. */
    public static final int DOOR_TOP = FEET_Y + 2;
    /** The skylight glass at the top of a shaft. */
    private static final int SKYLIGHT_Y = 52;
    /** Everything above the ceilings is solid up to here. */
    private static final int ROOF_TOP = 53;
    /** The highest block the plan ever places. */
    public static final int HIGHEST_Y = ROOF_TOP;
    /** Where someone falling in through a skylight from Level 37 appears (feet), high in a shaft. */
    public static final int SKYLIGHT_ARRIVAL_Y = 50;

    // ==========================================================================================
    // Grid
    // ==========================================================================================
    public static final int CELL = 32;
    /** The middle of a cell's interior (cell-local 1..31). */
    public static final int MID = 16;
    /** Rooms come in zones of ZONE x ZONE cells. */
    private static final int ZONE = 4;
    /** A huge area is HUGE x HUGE zones of one kind (see {@link #huge}). */
    private static final int HUGE = 3;
    private static final double HUGE_SHARE = 0.12;
    private static final double DARK_SHARE = 0.09;
    /** A hallway's corridor: cell-local MID-2..MID+2 across. */
    private static final int CORRIDOR_LO = MID - 2;
    private static final int CORRIDOR_HI = MID + 2;

    static final int HALL = 0;
    static final int PILLAR = 1;
    static final int GARDEN = 2;
    static final int DARK = 3;

    // Edges between cells: the one on a cell's west side, and on its north side.
    public static final int WEST = 0;
    public static final int NORTH = 1;
    // Edge results (anything >= 0 is a doorway at that cell-local offset along the edge).
    static final int E_SOLID = -1;
    /** No wall: the two rooms are one. */
    static final int E_OPEN = -2;
    /** Two hallways meeting end to end: the corridor runs on through. */
    static final int E_JOIN = -3;

    /** One way out per region of EXIT_CELLS x EXIT_CELLS cells (160 x 160 blocks), never in its outer cells. */
    private static final int EXIT_CELLS = 5;
    static final ExitLocator EXITS = new ExitLocator(EXIT_CELLS * CELL, 40, 3690);

    // ==========================================================================================
    // Public queries
    // ==========================================================================================

    /** Fills {@code out} (indexed by y, 0..63) with the codes of column (x, z). */
    public void column(long seed, int x, int z, int[] out) {
        Arrays.fill(out, AIR);
        for (int y = SOIL_FROM; y < FLOOR_Y; y++) {
            out[y] = SOIL;
        }
        out[FLOOR_Y] = GRASS;
        int cx = Math.floorDiv(x, CELL);
        int cz = Math.floorDiv(z, CELL);
        int lx = Math.floorMod(x, CELL);
        int lz = Math.floorMod(z, CELL);
        if (lx == 0 || lz == 0) {
            band(seed, x, z, cx, cz, lx, lz, out);
        } else {
            interior(seed, x, z, cx, cz, kind(seed, cx, cz), lx, lz, out);
        }
    }

    /** True when you can stand at feet level (y41) in column (x, z): lawn or brick, two free blocks. */
    public boolean isWalkable(long seed, int x, int z) {
        int[] out = new int[64];
        column(seed, x, z, out);
        int floor = type(out[FLOOR_Y]);
        return (floor == GRASS || floor == BRICK) && passable(out[FEET_Y]) && passable(out[FEET_Y + 1]);
    }

    private static boolean passable(int code) {
        int t = type(code);
        return t == AIR || t == FLOWER || t == TUFT || t == GLOW || t == LIGHT;
    }

    /** The cell the exit (the arch of vines) nearest to (x, z) is in: {cellX, cellZ}. */
    public int[] exitCell(long seed, int x, int z) {
        int[] exit = EXITS.nearest(seed, x, z);
        return new int[] {Math.floorDiv(exit[0], CELL), Math.floorDiv(exit[1], CELL)};
    }

    /** Where to stand in front of the nearest arch of vines: {x, z}. */
    public int[] archFront(long seed, int x, int z) {
        int[] cell = exitCell(seed, x, z);
        return new int[] {cell[0] * CELL + MID, cell[1] * CELL + ARCH_FACE + 1};
    }

    /**
     * Where someone coming down Level 37's pit falls in: high in the skylight shaft of the skylit
     * room nearest to (x, z) (not the one with the way out), or null if there is none close by.
     */
    @Nullable
    public BlockPos skylightArrival(long seed, int x, int z) {
        int cx0 = Math.floorDiv(x, CELL);
        int cz0 = Math.floorDiv(z, CELL);
        for (int r = 0; r <= 4; r++) {
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    if (Math.max(Math.abs(ox), Math.abs(oz)) != r) {
                        continue;
                    }
                    int cx = cx0 + ox;
                    int cz = cz0 + oz;
                    if (kind(seed, cx, cz) == GARDEN && !isExitCell(seed, cx, cz)) {
                        int[] spot = ARRIVAL[skylightVariant(seed, cx, cz)];
                        return new BlockPos(cx * CELL + spot[0], SKYLIGHT_ARRIVAL_Y, cz * CELL + spot[1]);
                    }
                }
            }
        }
        return null;
    }

    // ==========================================================================================
    // Cells
    // ==========================================================================================

    static boolean isExitCell(long seed, int cx, int cz) {
        int size = EXIT_CELLS * CELL;
        int rx = Math.floorDiv(cx * CELL, size);
        int rz = Math.floorDiv(cz * CELL, size);
        return Math.floorDiv(EXITS.exitX(seed, rx, rz), CELL) == cx && Math.floorDiv(EXITS.exitZ(seed, rx, rz), CELL) == cz;
    }

    /** True if the block of HUGE x HUGE zones (hx, hz) is one huge area. */
    private static boolean huge(long seed, int hx, int hz) {
        return Hash.unit(seed, hx, hz, 3710) < HUGE_SHARE;
    }

    private static int zoneX(int cx) {
        return Math.floorDiv(cx, ZONE);
    }

    private static int hugeX(int cx) {
        return Math.floorDiv(zoneX(cx), HUGE);
    }

    /** The kind its zone gives a cell (hallway, pillar hall or skylit room); a huge area's is a pillar hall or hallways. */
    private static int zoneKind(long seed, int cx, int cz) {
        int hx = hugeX(cx);
        int hz = hugeX(cz);
        if (huge(seed, hx, hz)) {
            return Hash.unit(seed, hx, hz, 3711) < 0.6 ? PILLAR : HALL;
        }
        double zone = Hash.unit(seed, zoneX(cx), zoneX(cz), 3691);
        return zone < 0.3 ? HALL : zone < 0.6 ? PILLAR : GARDEN;
    }

    static int kind(long seed, int cx, int cz) {
        if (isExitCell(seed, cx, cz)) {
            return GARDEN;
        }
        if (darkPick(seed, cx, cz) && !darkPick(seed, cx - 1, cz) && !darkPick(seed, cx, cz - 1)
                && !huge(seed, hugeX(cx), hugeX(cz))) {
            // Never inside a huge area (it stays one open space), and never next to another dark
            // room: a dark room's one doorway always opens onto a lit room.
            return DARK;
        }
        return zoneKind(seed, cx, cz);
    }

    /** The dice for a dark room; a cell whose west or north neighbour also rolled one stays lit. */
    private static boolean darkPick(long seed, int cx, int cz) {
        return Hash.unit(seed, cx, cz, 3692) < DARK_SHARE;
    }

    /** Two cells of one zone, or of one huge area. */
    private static boolean sameZone(long seed, int ax, int az, int bx, int bz) {
        if (zoneX(ax) == zoneX(bx) && zoneX(az) == zoneX(bz)) {
            return true;
        }
        return hugeX(ax) == hugeX(bx) && hugeX(az) == hugeX(bz) && huge(seed, hugeX(ax), hugeX(az));
    }

    /** A zone's hallways all run along X, or all along Z (a huge area's all one way). */
    private static boolean hallAlongX(long seed, int cx, int cz) {
        int hx = hugeX(cx);
        int hz = hugeX(cz);
        if (huge(seed, hx, hz)) {
            return Hash.unit(seed, hx, hz, 3712) < 0.5;
        }
        return Hash.unit(seed, zoneX(cx), zoneX(cz), 3693) < 0.5;
    }

    private static int colour(long seed, int cx, int cz, int kind) {
        return switch (kind) {
            case HALL -> WHITE;
            case PILLAR -> PEACH;
            case DARK -> DUSK;
            default -> GARDEN_COLOURS[Hash.range(Hash.of(seed, cx, cz, 3694), GARDEN_COLOURS.length)];
        };
    }

    /** Which wall of a dark room has its one doorway: 0 west, 1 north, 2 east, 3 south. */
    private static int darkDoorSide(long seed, int cx, int cz) {
        return Hash.range(Hash.of(seed, cx, cz, 3696), 4);
    }

    /** Does the hallway in cell (cx, cz) run through its {@code side} edge (its end, not its side)? */
    private static boolean crosses(long seed, int cx, int cz, int side) {
        return hallAlongX(seed, cx, cz) == (side == WEST);
    }

    /**
     * The edge on the {@code side} (WEST or NORTH) of cell (cx, cz): {@link #E_SOLID},
     * {@link #E_OPEN}, {@link #E_JOIN}, or a doorway at that cell-local offset along it.
     */
    static int edge(long seed, int cx, int cz, int side) {
        int ax = side == WEST ? cx - 1 : cx;
        int az = side == WEST ? cz : cz - 1;
        if (side == NORTH && isExitCell(seed, cx, cz)) {
            return E_SOLID; // the arch of vines stands against it
        }
        int ka = kind(seed, ax, az);
        int kb = kind(seed, cx, cz);
        long h = Hash.of(seed, cx * 2 + side, cz, 3695);
        boolean crossA = ka == HALL && crosses(seed, ax, az, side);
        boolean crossB = kb == HALL && crosses(seed, cx, cz, side);
        if (ka == DARK || kb == DARK) {
            if (crossA || crossB) {
                // A corridor ending at a dark room always runs on into it (a second doorway, in line
                // with the corridor), so no corridor is ever a dead end.
                return MID;
            }
            // Otherwise a dark room has a single doorway (its neighbours are never dark). Seen from
            // cell b this edge is its west (0) or north (1) wall; seen from cell a, its east (2) or
            // south (3) wall.
            boolean match = kb == DARK ? darkDoorSide(seed, cx, cz) == side : darkDoorSide(seed, ax, az) == side + 2;
            return match ? doorOffset(h) : E_SOLID;
        }
        if (crossA && crossB) {
            return E_JOIN;
        }
        if (crossA || crossB) {
            return MID; // the end of a corridor: a doorway in line with it
        }
        if (ka == HALL || kb == HALL) {
            // Along the side of a corridor: now and then a side passage leads off it.
            return Hash.unit(h) < (ka == HALL && kb == HALL ? 0.3 : 0.5) ? doorOffset(h) : E_SOLID;
        }
        boolean zone = sameZone(seed, ax, az, cx, cz);
        if (ka == PILLAR && kb == PILLAR && zone) {
            return E_OPEN;
        }
        if (ka == GARDEN && kb == GARDEN && zone && Hash.unit(Hash.mix(h)) < 0.35) {
            return E_OPEN;
        }
        return doorOffset(h);
    }

    /**
     * For world/Level369Forgetting: the offset of the plain doorway on the {@code side} edge (WEST
     * or NORTH) of cell (cx, cz), or -1 if that edge has none (a wall, an open seam, two corridors
     * joining) or is a hallway's (whose doorways line up with its corridor or side passages).
     */
    public static int plainDoorway(long seed, int cx, int cz, int side) {
        int e = edge(seed, cx, cz, side);
        if (e < 0) {
            return -1;
        }
        int ax = side == WEST ? cx - 1 : cx;
        int az = side == WEST ? cz : cz - 1;
        return kind(seed, cx, cz) == HALL || kind(seed, ax, az) == HALL ? -1 : e;
    }

    /** A doorway somewhere along the middle of an edge (cell-local 4..CELL-4). */
    private static int doorOffset(long h) {
        return 4 + Hash.range(Hash.mix(h + 1), CELL - 7);
    }

    private static int ceilingY(int kind) {
        return switch (kind) {
            case PILLAR -> 45;
            case GARDEN -> 48;
            default -> 46;
        };
    }

    // ==========================================================================================
    // Columns
    // ==========================================================================================

    /** A column on a cell's west or north edge: wall, doorway, or open into the room. */
    private void band(long seed, int x, int z, int cx, int cz, int lx, int lz, int[] out) {
        int kind = kind(seed, cx, cz);
        if (lx == 0 && lz == 0) {
            // The corner post: open only where all four rooms around it are one.
            if (edge(seed, cx, cz, WEST) == E_OPEN && edge(seed, cx, cz, NORTH) == E_OPEN
                    && edge(seed, cx, cz - 1, WEST) == E_OPEN && edge(seed, cx - 1, cz, NORTH) == E_OPEN) {
                seam(seed, x, z, cx, cz, kind, out);
            } else {
                wall(out, colour(seed, cx, cz, kind));
            }
            return;
        }
        int side = lx == 0 ? WEST : NORTH;
        int along = lx == 0 ? lz : lx;
        int nx = side == WEST ? cx - 1 : cx;
        int nz = side == WEST ? cz : cz - 1;
        int e = edge(seed, cx, cz, side);
        if (e == E_OPEN) {
            seam(seed, x, z, cx, cz, kind, out);
            return;
        }
        if (e == E_JOIN) {
            hallway(seed, x, z, cx, cz, lx, lz, out); // two corridors end to end: it runs on through
            return;
        }
        // The wall takes the colour of the room it matters most to: a hallway's white, a pillar
        // hall's peach, a dark room's sage, else this cell's pastel.
        int neighbour = kind(seed, nx, nz);
        boolean mine = WALL_PRIORITY[kind] >= WALL_PRIORITY[neighbour];
        wall(out, mine ? colour(seed, cx, cz, kind) : colour(seed, nx, nz, neighbour));
        if (e >= 0 && Math.abs(along - e) <= 1) {
            doorway(out);
        }
    }

    /** Indexed by kind (HALL, PILLAR, GARDEN, DARK). */
    private static final int[] WALL_PRIORITY = {3, 2, 0, 1};

    /**
     * A column of the wall line between two rooms that are open into each other (pillar halls of
     * one zone, merged skylit rooms): just more of the room, without its skylights, tree or bushes.
     */
    private void seam(long seed, int x, int z, int cx, int cz, int kind, int[] out) {
        if (kind == PILLAR) {
            pillarHall(seed, x, z, cx, cz, out);
            return;
        }
        int ceiling = ceilingY(GARDEN);
        out[ceiling] = code(CEIL, 0);
        for (int y = ceiling + 1; y <= ROOF_TOP; y++) {
            out[y] = code(PLASTER, colour(seed, cx, cz, GARDEN));
        }
        plant(seed, x, z, GARDEN, out);
    }

    private static void wall(int[] out, int colour) {
        for (int y = FEET_Y; y <= ROOF_TOP; y++) {
            out[y] = code(PLASTER, colour);
        }
    }

    /** Cuts a 3-high opening (brick threshold) out of a wall column. */
    private static void doorway(int[] out) {
        out[FLOOR_Y] = BRICK;
        for (int y = FEET_Y; y <= DOOR_TOP; y++) {
            out[y] = AIR;
        }
    }

    private void interior(long seed, int x, int z, int cx, int cz, int kind, int lx, int lz, int[] out) {
        switch (kind) {
            case HALL -> hallway(seed, x, z, cx, cz, lx, lz, out);
            case PILLAR -> pillarHall(seed, x, z, cx, cz, out);
            case DARK -> darkRoom(seed, x, z, out);
            default -> garden(seed, x, z, cx, cz, lx, lz, out);
        }
    }

    // ---- Hallway --------------------------------------------------------------------------------

    private void hallway(long seed, int x, int z, int cx, int cz, int lx, int lz, int[] out) {
        boolean alongX = hallAlongX(seed, cx, cz);
        int across = alongX ? lz : lx;
        int along = alongX ? lx : lz;
        int ceiling = ceilingY(HALL);
        if (across >= CORRIDOR_LO && across <= CORRIDOR_HI) {
            plant(seed, x, z, HALL, out);
            out[ceiling] = across == MID && Math.floorMod(along, 4) == 2 ? PANEL : code(CEIL, 0);
            for (int y = ceiling + 1; y <= ROOF_TOP; y++) {
                out[y] = code(PLASTER, WHITE);
            }
            return;
        }
        // A side passage through to the next room?
        int e = across < CORRIDOR_LO
                ? edge(seed, cx, cz, alongX ? NORTH : WEST)
                : edge(seed, alongX ? cx : cx + 1, alongX ? cz + 1 : cz, alongX ? NORTH : WEST);
        wall(out, WHITE);
        if (e >= 0 && Math.abs(along - e) <= 1) {
            doorway(out);
            return;
        }
        if (across == CORRIDOR_LO - 1 && Math.floorMod(along, 4) < 2 && along > 0) {
            // Tall windows full of light down one side.
            for (int y = FEET_Y + 1; y <= FEET_Y + 3; y++) {
                out[y] = WINDOW;
            }
        } else if (across == CORRIDOR_HI + 1 && Math.floorMod(along, 8) == 5) {
            // Closed doors down the other, facing out of the corridor wall.
            int facing = alongX ? S2 : E2;
            out[FEET_Y] = code(DOOR, facing);
            out[FEET_Y + 1] = code(DOOR, facing | 4);
        }
    }

    // ---- Pillar hall ----------------------------------------------------------------------------

    private void pillarHall(long seed, int x, int z, int cx, int cz, int[] out) {
        int ceiling = ceilingY(PILLAR);
        int px = Math.floorMod(x, 8);
        int pz = Math.floorMod(z, 8);
        for (int y = ceiling + 1; y <= ROOF_TOP; y++) {
            out[y] = code(PLASTER, PEACH);
        }
        if ((px == 3 || px == 4) && (pz == 3 || pz == 4)) {
            for (int y = FEET_Y; y <= ceiling; y++) {
                out[y] = code(PLASTER, BUTTER); // a cream pillar
            }
            return;
        }
        out[ceiling] = px == 0 && (pz == 7 || pz == 0) ? PANEL : code(CEIL, 1);
        if (onPath(seed, x, z)) {
            out[FLOOR_Y] = BRICK;
        } else {
            plant(seed, x, z, PILLAR, out);
        }
    }

    /**
     * The winding brick paths: a band about 3-4 wide along one contour line of a smooth noise. The
     * distance to the line is the noise's offset divided by its slope, so the path keeps an even
     * width instead of swelling into blobs where the noise flattens out.
     */
    private static boolean onPath(long seed, int x, int z) {
        double v = smooth(seed, x, z, 30.0, 3703) - 0.5;
        if (Math.abs(v) > 0.08) {
            return false;
        }
        double gx = (smooth(seed, x + 1, z, 30.0, 3703) - smooth(seed, x - 1, z, 30.0, 3703)) * 0.5;
        double gz = (smooth(seed, x, z + 1, 30.0, 3703) - smooth(seed, x, z - 1, 30.0, 3703)) * 0.5;
        return Math.abs(v) < 1.7 * Math.max(Math.hypot(gx, gz), 0.004);
    }

    // ---- Skylit room ----------------------------------------------------------------------------

    /** Where to fall in, per skylight layout: a shaft column clear of the young tree. */
    private static final int[][] ARRIVAL = {{MID, MID}, {8, 8}, {24, MID}};
    /** Where the young tree stands, per skylight layout (none under the one big skylight). */
    private static final int[][] TREE = {null, {24, 24}, {8, MID}};

    private static int skylightVariant(long seed, int cx, int cz) {
        return isExitCell(seed, cx, cz) ? 0 : Hash.range(Hash.of(seed, cx, cz, 3697), 3);
    }

    /** One big skylight (9 x 9), four small ones (4 x 4), or a long strip (21 x 5). */
    private static boolean inShaft(int variant, int lx, int lz) {
        return switch (variant) {
            case 0 -> Math.abs(lx - MID) <= 4 && Math.abs(lz - MID) <= 4;
            case 1 -> (lx >= 7 && lx <= 10 || lx >= 22 && lx <= 25) && (lz >= 7 && lz <= 10 || lz >= 22 && lz <= 25);
            default -> lx >= 6 && lx <= 26 && Math.abs(lz - MID) <= 2;
        };
    }

    private void garden(long seed, int x, int z, int cx, int cz, int lx, int lz, int[] out) {
        int ceiling = ceilingY(GARDEN);
        int colour = colour(seed, cx, cz, GARDEN);
        int variant = skylightVariant(seed, cx, cz);
        if (inShaft(variant, lx, lz)) {
            out[SKYLIGHT_Y] = SKYLIGHT;
            out[ROOF_TOP] = code(PLASTER, colour);
            out[ceiling - 1] = code(LIGHT, 15); // the daylight pouring down the shaft
        } else {
            out[ceiling] = code(CEIL, 0);
            for (int y = ceiling + 1; y <= ROOF_TOP; y++) {
                out[y] = code(PLASTER, colour);
            }
            if (lx % 5 == 0 && lz % 5 == 0) {
                out[ceiling - 2] = code(LIGHT, 13); // soft fill: the light bounces round the room
            }
        }
        if (isExitCell(seed, cx, cz) && arch(lx, lz, out)) {
            return;
        }
        // A young tree under the light.
        int[] tree = TREE[variant];
        if (tree != null && !isExitCell(seed, cx, cz) && Hash.unit(seed, cx, cz, 3698) < 0.7) {
            int dx = lx - tree[0];
            int dz = lz - tree[1];
            if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) {
                boolean trunk = dx == 0 && dz == 0;
                boolean any = false;
                for (int y = FEET_Y; y < ceiling; y++) {
                    if (trunk && y <= FEET_Y + 3) {
                        out[y] = LOG;
                        any = true;
                    } else if (y >= FEET_Y + 2 && dx * dx + dz * dz + (y - 45.5) * (y - 45.5) * 1.4 <= 6.5) {
                        out[y] = LEAVES;
                        any = true;
                    }
                }
                if (trunk) {
                    return;
                }
                if (any) {
                    plant(seed, x, z, GARDEN, out);
                    return;
                }
            }
        }
        // Bushes along the walls (never in front of a doorway).
        boolean nearWall = lx <= 2 || lx >= CELL - 2 || lz <= 2 || lz >= CELL - 2;
        if (nearWall && smooth(seed, x, z, 3.0, 3704) > 0.62 && !byDoorway(seed, cx, cz, lx, lz)) {
            out[FEET_Y] = LEAVES;
            if (smooth(seed, x, z, 2.0, 3705) > 0.55) {
                out[FEET_Y + 1] = LEAVES;
            }
            return;
        }
        plant(seed, x, z, GARDEN, out);
    }

    /** True within 3 blocks of a doorway in any of the cell's four walls (cell-local lx, lz). */
    private static boolean byDoorway(long seed, int cx, int cz, int lx, int lz) {
        return lx <= 3 && near(edge(seed, cx, cz, WEST), lz)
                || lz <= 3 && near(edge(seed, cx, cz, NORTH), lx)
                || lx >= CELL - 3 && near(edge(seed, cx + 1, cz, WEST), lz)
                || lz >= CELL - 3 && near(edge(seed, cx, cz + 1, NORTH), lx);
    }

    private static boolean near(int doorway, int along) {
        return doorway >= 0 && Math.abs(along - doorway) <= 3;
    }

    /** The arch's front face (cell-local z); the veil fills the opening behind it. */
    static final int ARCH_FACE = 3;

    /**
     * The great arch of vines against the exit room's north wall (cell-local x 5..15, z 1..3): a
     * round-topped opening 5 wide and 5-6 high in a vine-covered frame, filled behind its front
     * face with the veil. Returns true if (lx, lz) was part of it.
     */
    private static boolean arch(int lx, int lz, int[] out) {
        int ax = lx - MID;
        if (Math.abs(ax) > 5 || lz > ARCH_FACE) {
            return false;
        }
        for (int y = FEET_Y; y < ceilingY(GARDEN); y++) {
            double dy = y - 44.0;
            boolean frame = Math.abs(ax) <= 4 && (y <= 44 || dy * dy + ax * ax <= 20.25);
            boolean opening = Math.abs(ax) <= 2 && (y <= 44 || dy * dy + ax * ax <= 6.25);
            if (Math.abs(ax) == 5 || frame && !opening) {
                out[y] = VINE_WALL;
            } else if (opening) {
                out[y] = lz == ARCH_FACE ? AIR : VEIL;
            }
        }
        if (Math.abs(ax) <= 2 && lz == ARCH_FACE) {
            out[FEET_Y] = code(FLOWER, Math.floorMod(lx, 4));
        }
        return true;
    }

    // ---- Dark room ------------------------------------------------------------------------------

    private void darkRoom(long seed, int x, int z, int[] out) {
        int ceiling = ceilingY(DARK);
        for (int y = ceiling; y <= ROOF_TOP; y++) {
            out[y] = code(PLASTER, DUSK);
        }
        plant(seed, x, z, DARK, out);
        if (Hash.unit(seed, x, z, 3699) < 0.10) {
            int length = 1 + Hash.range(Hash.of(seed, x, z, 3706), 3);
            for (int i = 1; i <= length; i++) {
                out[ceiling - i] = code(VINE, i == length ? 1 : 0);
            }
        }
    }

    // ---- The lawn -------------------------------------------------------------------------------

    /**
     * The plant (or none) growing from the lawn at (x, z) in a room of {@code kind}. Lit rooms are
     * one dense meadow: every block carries grass or flowers, the flowers thick in drifts (a
     * smooth noise) and scattered between them, like the user's pictures.
     */
    private static void plant(long seed, int x, int z, int kind, int[] out) {
        double r = Hash.unit(seed, x, z, 3700);
        double patch = smooth(seed, x, z, 7.0, 3701);
        int tuftKind = tuftKind(Hash.unit(seed, x, z, 3707));
        switch (kind) {
            case HALL -> out[FEET_Y] = r < 0.12 + 0.5 * patch ? code(FLOWER, 0) : code(TUFT, tuftKind);
            case PILLAR -> out[FEET_Y] = r < 0.15 + 0.6 * patch ? code(FLOWER, 1) : code(TUFT, tuftKind);
            case DARK -> {
                if (r < 0.18) {
                    out[FEET_Y] = code(GLOW, Hash.range(Hash.of(seed, x, z, 3708), 4));
                } else if (r < 0.45) {
                    out[FEET_Y] = code(TUFT, tuftKind);
                }
            }
            default -> {
                if (r < 0.15 + 0.5 * patch) {
                    // Patches of one colour: the kind follows a second, slower noise.
                    int flower = Math.min(3, (int) (smooth(seed, x, z, 9.0, 3702) * 4.0));
                    out[FEET_Y] = code(FLOWER, flower);
                } else {
                    out[FEET_Y] = code(TUFT, tuftKind);
                }
            }
        }
    }

    /** Short soft grass is the most common, then tall grass, a few seed heads and buttercups. */
    private static int tuftKind(double u) {
        return u < 0.55 ? 0 : u < 0.85 ? 1 : u < 0.93 ? 2 : 3;
    }

    /** Smooth value noise in [0, 1): random values on a {@code scale}-block lattice, eased in between. */
    public static double smooth(long seed, int x, int z, double scale, int salt) {
        double fx = x / scale;
        double fz = z / scale;
        int ix = (int) Math.floor(fx);
        int iz = (int) Math.floor(fz);
        double tx = fx - ix;
        double tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = Hash.unit(seed, ix, iz, salt);
        double b = Hash.unit(seed, ix + 1, iz, salt);
        double c = Hash.unit(seed, ix, iz + 1, salt);
        double d = Hash.unit(seed, ix + 1, iz + 1, salt);
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz;
    }
}
