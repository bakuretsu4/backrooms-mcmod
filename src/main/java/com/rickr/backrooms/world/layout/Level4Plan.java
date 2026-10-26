package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Hash;

/**
 * The floor plan of Level 4 ("The Abandoned Office") as plain numbers: for every column (x, z) it
 * says WHAT stands there (a wall, a desk, a window, a door, open carpet...), which way it faces,
 * what the floor and the ceiling look like. {@link Level4Layout} turns these numbers into blocks.
 *
 * <p>This class uses no Minecraft classes at all, only {@link Hash} and {@link ExitLocator}. That
 * keeps it a pure function of (seed, x, z), and it means it could be tested without starting the
 * game (for example with a flood fill that checks every floor column can be reached).
 *
 * <h2>Zones</h2>
 * The world is cut into square zones of {@link #CELL} x {@link #CELL} blocks. Each zone is one of:
 * <ul>
 *   <li>{@link #OPEN}: a huge open-plan floor with square white columns every 8 blocks and
 *       "islands" of left-behind furniture between them. Some have a row of small offices along
 *       one side.</li>
 *   <li>{@link #CUBICLES}: rows of grey fabric cubicles (1.5 blocks tall) with aisles between
 *       them, and at the far end either glass conference rooms or more open floor.</li>
 *   <li>{@link #OFFICES}: two corridors lined with wooden doors into small private offices, and a
 *       row of bigger rooms (break room, conference room, copy room, storage...).</li>
 *   <li>{@link #LOBBY}: an elevator lobby (closed elevator doors, chair-rail walls) through the
 *       middle, with big rooms on both sides.</li>
 * </ul>
 *
 * <p>Inside its border wall every zone has a 3-wide "ring" aisle that is always open floor; the
 * zone's content (its "core") only fills the middle. So whatever the core is, you can always walk
 * around it, and every room, corridor and aisle of the core opens onto the ring.
 *
 * <h2>Zone borders</h2>
 * The line between two zones (x or z a multiple of {@link #CELL}) is one of: OPEN (no wall at all,
 * the floors run together; only between open-plan and cubicle zones), DOORS (a drywall partition
 * with two or three openings/double doors), or WINDOW (a ribbon of dark rainy windows on one side,
 * radiators under the sills, no way through). Every zone keeps its west OR north border passable
 * (never a window), so following those borders always leads on: no zone is ever sealed off.
 *
 * <h2>Packed columns</h2>
 * {@link #column} returns a {@code long} with several fields (read them with the getters below):
 * kind, facing, a variant, the floor, the ceiling, the wall style and an optional wall decoration.
 * Facings are Minecraft's 2D data values: 0 south, 1 west, 2 north, 3 east.
 */
final class Level4Plan {
    // ==========================================================================================
    // Sizes
    // ==========================================================================================

    /** Zones are CELL x CELL blocks; their border wall is at local coordinate 0. */
    static final int CELL = 48;
    /** The open ring aisle inside the border: local 1..3 and 45..47. */
    private static final int RING = 3;
    /** The core is local CORE_LO..CORE_HI (inclusive): 41 x 41 blocks. */
    private static final int CORE_LO = RING + 1;
    private static final int CORE_HI = CELL - RING - 1;
    /** Exit stairwells: one per 288 x 288 region (6 x 6 zones). */
    static final ExitLocator EXITS = new ExitLocator(288, 32, 4400);
    /** Around a stairwell (outside its 11 x 11 tower) everything is cleared to bright open floor. */
    static final int CLEARING_RADIUS = 8;

    // ==========================================================================================
    // Zone kinds
    // ==========================================================================================

    static final int OPEN = 0;
    static final int CUBICLES = 1;
    static final int OFFICES = 2;
    static final int LOBBY = 3;

    // Border types.
    private static final int B_OPEN = 0;
    private static final int B_DOORS = 1;
    private static final int B_WINDOW = 2;

    // ==========================================================================================
    // Column kinds (what stands in the column between floor and ceiling)
    // ==========================================================================================

    /** Open floor you can walk on (it may still have a ceiling light or a wall decoration). */
    static final int FLOOR = 0;
    /** A drywall wall from floor to ceiling ({@link #style} says how its bottom block looks). */
    static final int WALL = 1;
    /** Solid filling of a locked closet or an elevator shaft: never seen, never entered. */
    static final int FILL = 2;
    /** A square white column (with a dark base). */
    static final int COLUMN = 3;
    /** A doorway without a door: 3 blocks tall, wall above. */
    static final int DOORWAY = 4;
    /** A door in a wall (variant: door type, open, hinge, locked). Facing = the way the door faces. */
    static final int DOOR = 5;
    /** Sill, two blocks of window glass and a head. Facing = the side the glass looks into. */
    static final int WINDOW = 6;
    /** Radiator under a window sill. Facing = away from the window. */
    static final int RADIATOR = 7;
    /** One block-column of an elevator door set into a wall (variant: right half). Facing = out. */
    static final int ELEVATOR = 8;
    // Furniture. Facing = the way its front looks.
    static final int DESK = 9;
    static final int DESK_PC = 10;
    static final int CHAIR = 11;
    static final int CABINET = 12;
    static final int COPIER = 13;
    static final int PLANT = 14;
    /** Cardboard boxes (variant 1 = stacked two high). */
    static final int BOXES = 15;
    static final int LADDER = 16;
    /** Supply crate (variant 1 = two stacked). */
    static final int CRATE = 17;
    static final int LOCKER = 18;
    /** A filing cabinet that is really a supply locker (loot inside). */
    static final int SUPPLY_CABINET = 19;
    static final int BACKPACK = 20;
    /** Two-tall vending machine (variant = how many items are left). */
    static final int VENDING = 21;
    static final int COOLER = 22;
    /** Kitchen counter (variant 1 = with a microwave on top). */
    static final int COUNTER = 23;
    static final int FRIDGE = 24;
    static final int TABLE = 25;
    static final int CONF_TABLE = 26;
    static final int SOFA = 27;
    /** Server rack, two blocks tall. */
    static final int SERVER_RACK = 28;
    /** Heavy red stage curtain hanging floor to ceiling (the "curtain room"). */
    static final int CURTAIN = 29;
    /** Cubicle partition (variant = which of the 4 sides it connects to, bit = 2D data value). */
    static final int PARTITION = 30;

    // Door types (variant bits 0-1).
    static final int DOOR_OFFICE = 0;
    /** The office door with a brass "6" on it: always locked, faint music behind it. */
    static final int DOOR_SIX = 1;
    static final int DOOR_GLASS = 2;
    // Door variant flags.
    static final int DOOR_OPEN = 4;
    static final int DOOR_HINGE_RIGHT = 8;
    static final int DOOR_LOCKED = 16;

    // Floors (3 bits: carpet colour + indent flag).
    static final int CARPET_GREIGE = 0;
    static final int CARPET_BLUE = 1;
    static final int CONCRETE = 2;
    /** Added to a carpet: the darker, unfaded patch where furniture stood for years. */
    static final int INDENT = 4;

    // Ceilings.
    static final int TILE = 0;
    static final int TILE_MISSING = 1;
    static final int HATCH = 2;
    static final int PANEL_LIT = 3;
    static final int PANEL_DEAD = 4;
    static final int PANEL_FLICKER = 5;

    // Wall styles (only used by WALL; says what the bottom block looks like, or glass).
    static final int STYLE_BASE = 0;
    static final int STYLE_RAIL = 1;
    static final int STYLE_GLASS = 2;
    static final int STYLE_BLINDS = 3;

    // Wall decorations hung in the air in front of a wall (FLOOR columns only).
    static final int DECO_NONE = 0;
    static final int DECO_WHITEBOARD = 1;
    static final int DECO_ELEVATOR_PANEL = 2;
    static final int DECO_CCTV = 3;
    static final int DECO_PLAQUE = 4;

    // Directions as 2D data values (same numbers as Minecraft's Direction.get2DDataValue()).
    static final int SOUTH = 0;
    static final int WEST = 1;
    static final int NORTH = 2;
    static final int EAST = 3;
    private static final int[] DX = {0, -1, 0, 1};
    private static final int[] DZ = {1, 0, -1, 0};

    // ==========================================================================================
    // Packing
    // ==========================================================================================

    private static final int KIND_SHIFT = 0;      // 8 bits
    private static final int FACING_SHIFT = 8;    // 2 bits
    private static final int VARIANT_SHIFT = 10;  // 8 bits
    private static final int FLOOR_SHIFT = 18;    // 3 bits
    private static final int CEIL_SHIFT = 21;     // 3 bits
    private static final int CEIL_FACING_SHIFT = 24; // 2 bits
    private static final int STYLE_SHIFT = 26;    // 3 bits
    private static final int DECO_SHIFT = 29;     // 3 bits
    private static final int DECO_FACING_SHIFT = 32; // 2 bits

    static int kind(long c) { return (int) (c >>> KIND_SHIFT) & 0xFF; }
    static int facing(long c) { return (int) (c >>> FACING_SHIFT) & 3; }
    static int variant(long c) { return (int) (c >>> VARIANT_SHIFT) & 0xFF; }
    static int floor(long c) { return (int) (c >>> FLOOR_SHIFT) & 7; }
    static int ceiling(long c) { return (int) (c >>> CEIL_SHIFT) & 7; }
    static int ceilingFacing(long c) { return (int) (c >>> CEIL_FACING_SHIFT) & 3; }
    static int style(long c) { return (int) (c >>> STYLE_SHIFT) & 7; }
    static int deco(long c) { return (int) (c >>> DECO_SHIFT) & 7; }
    static int decoFacing(long c) { return (int) (c >>> DECO_FACING_SHIFT) & 3; }

    private static long pack(int kind, int facing, int variant) {
        return ((long) kind << KIND_SHIFT) | ((long) (facing & 3) << FACING_SHIFT) | ((long) (variant & 0xFF) << VARIANT_SHIFT);
    }

    private static long withFloor(long c, int floor) {
        return (c & ~(7L << FLOOR_SHIFT)) | ((long) floor << FLOOR_SHIFT);
    }

    private static long withCeiling(long c, int ceiling, int facing) {
        c &= ~((7L << CEIL_SHIFT) | (3L << CEIL_FACING_SHIFT));
        return c | ((long) ceiling << CEIL_SHIFT) | ((long) (facing & 3) << CEIL_FACING_SHIFT);
    }

    private static long withStyle(long c, int style) {
        return (c & ~(7L << STYLE_SHIFT)) | ((long) style << STYLE_SHIFT);
    }

    private static long withDeco(long c, int deco, int facing) {
        c &= ~((7L << DECO_SHIFT) | (3L << DECO_FACING_SHIFT));
        return c | ((long) deco << DECO_SHIFT) | ((long) (facing & 3) << DECO_FACING_SHIFT);
    }

    /** True where a player can stand (and walk through): open floor, doorways, unlocked doors. */
    static boolean walkable(long c) {
        int kind = kind(c);
        return kind == FLOOR || kind == DOORWAY || kind == DOOR && (variant(c) & DOOR_LOCKED) == 0;
    }

    static int opposite(int dir) {
        return (dir + 2) & 3;
    }

    static int clockwise(int dir) {
        return (dir + 1) & 3;
    }

    // ==========================================================================================
    // Zones
    // ==========================================================================================

    /**
     * What kind of zone cell (cx, cz) is. Zones touching an exit's clearing, and zones that hold a
     * safe room, are always plain open floor.
     */
    static int zoneKind(long seed, int cx, int cz) {
        if (touchesExitClearing(seed, cx, cz) || safeRoom(seed, cx, cz) >= 0) {
            return OPEN;
        }
        double r = Hash.unit(seed, cx, cz, 7000);
        if (r < 0.30) return OPEN;
        if (r < 0.55) return CUBICLES;
        if (r < 0.85) return OFFICES;
        return LOBBY;
    }

    /**
     * True when the clearing around an exit stairwell reaches into zone (cx, cz). Exits sit at
     * least 32 blocks inside their 288-block region, so only the regions overlapping the zone
     * (grown by the clearing) need checking.
     */
    static boolean touchesExitClearing(long seed, int cx, int cz) {
        int reach = CLEARING_RADIUS + 1;
        int x0 = cx * CELL - reach;
        int x1 = cx * CELL + CELL - 1 + reach;
        int z0 = cz * CELL - reach;
        int z1 = cz * CELL + CELL - 1 + reach;
        for (int rx = Math.floorDiv(x0, 288); rx <= Math.floorDiv(x1, 288); rx++) {
            for (int rz = Math.floorDiv(z0, 288); rz <= Math.floorDiv(z1, 288); rz++) {
                int ex = EXITS.exitX(seed, rx, rz);
                int ez = EXITS.exitZ(seed, rx, rz);
                if (ex >= x0 && ex <= x1 && ez >= z0 && ez <= z1) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Rows of the zone run along X (false) or along Z (true). */
    private static boolean alongZ(long seed, int cx, int cz) {
        return Hash.unit(seed, cx, cz, 7003) < 0.5;
    }

    /** Carpet colour of a zone: greige-taupe or (40%) blue-grey. */
    private static int zoneCarpet(long seed, int cx, int cz) {
        return Hash.unit(seed, cx, cz, 7004) < 0.4 ? CARPET_BLUE : CARPET_GREIGE;
    }

    /**
     * Open zones: 0 = plain open floor, 1 = with a row of small offices along the far side.
     * Cubicle zones: 0 = glass conference rooms at the far end, 1 = open floor there instead.
     * Zones next to an exit are always plain.
     */
    private static int zoneVariant(long seed, int cx, int cz) {
        if (touchesExitClearing(seed, cx, cz) || safeRoom(seed, cx, cz) >= 0) {
            return 0;
        }
        return Hash.unit(seed, cx, cz, 7005) < 0.45 ? 1 : 0;
    }

    // ==========================================================================================
    // Safe rooms
    // ==========================================================================================

    /** Same square size and chance as every level's safe rooms (SafeRoomPlan.CELL / CHANCE). */
    private static final int SAFE_SQUARE = 128;
    private static final double SAFE_CHANCE = 0.6;
    /** A 1-block strip of plain floor runs all around a safe room. */
    static final int SAFE_MOAT = 1;
    /** Safe rooms keep at least this far (chessboard distance) from an exit. */
    private static final int SAFE_EXIT_DISTANCE = 24;

    /**
     * The safe room standing in zone (cx, cz), packed (x0, z0, size, door side) or -1. Each
     * 128 x 128 square of the world may have one (60%); it stands in the zone that contains the
     * square's centre (a 48-block zone never contains two centres, and always lies inside its
     * square), and that zone is then always a plain open floor, so the room is a free-standing
     * office box surrounded by carpet. Read the result with {@link #safeX0} and friends.
     */
    static long safeRoom(long seed, int cx, int cz) {
        int sqX = squareWithCentreIn(cx);
        int sqZ = squareWithCentreIn(cz);
        if (sqX == Integer.MIN_VALUE || sqZ == Integer.MIN_VALUE) {
            return -1;
        }
        long h = Hash.of(seed, sqX, sqZ, 7400);
        if (Hash.unit(h) >= SAFE_CHANCE || touchesExitClearing(seed, cx, cz)) {
            return -1;
        }
        int size = Hash.unit(Hash.mix(h ^ 1)) < 0.5 ? 11 : 13;
        int free = CORE_HI - CORE_LO + 1 - size - 2 * SAFE_MOAT;
        int x0 = cx * CELL + CORE_LO + SAFE_MOAT + Hash.range(Hash.mix(h ^ 2), free + 1);
        int z0 = cz * CELL + CORE_LO + SAFE_MOAT + Hash.range(Hash.mix(h ^ 3), free + 1);
        int[] exit = EXITS.nearest(seed, x0 + size / 2, z0 + size / 2);
        if (Math.max(Math.abs(exit[0] - x0 - size / 2), Math.abs(exit[1] - z0 - size / 2)) < SAFE_EXIT_DISTANCE) {
            return -1;
        }
        int door = Hash.range(Hash.mix(h ^ 4), 4);
        // Corner as an offset inside the zone (0..47), so the packed value is always small and positive.
        return ((long) (x0 - cx * CELL) << 16) | ((long) (z0 - cz * CELL) << 8) | ((long) size << 2) | door;
    }

    /** The square (along one axis) whose centre lies in zone {@code c}, or MIN_VALUE if none. */
    private static int squareWithCentreIn(int c) {
        int k = Math.floorDiv(c * CELL + CELL - 1 - SAFE_SQUARE / 2, SAFE_SQUARE);
        int centre = k * SAFE_SQUARE + SAFE_SQUARE / 2;
        return centre >= c * CELL ? k : Integer.MIN_VALUE;
    }

    /** North-west corner X of a packed safe room (see {@link #safeRoom}) in zone column cx. */
    static int safeX0(long room, int cx) {
        return cx * CELL + ((int) (room >>> 16) & 0xFF);
    }

    static int safeZ0(long room, int cz) {
        return cz * CELL + ((int) (room >>> 8) & 0xFF);
    }

    static int safeSize(long room) {
        return (int) (room >>> 2) & 0x3F;
    }

    /** Door side as a 2D data value. */
    static int safeDoor(long room) {
        return (int) room & 3;
    }


    private static boolean openKind(int kind) {
        return kind == OPEN || kind == CUBICLES;
    }

    // ==========================================================================================
    // Borders between zones
    // ==========================================================================================

    /** Every zone keeps one of its west/north borders passable: true = the west one. */
    private static boolean keepsWestOpen(long seed, int cx, int cz) {
        return Hash.unit(seed, cx, cz, 7006) < 0.5;
    }

    /**
     * The border on line x = cx * CELL between zones (cx - 1, cz) and (cx, cz) (vertical = true),
     * or on line z = cz * CELL between (cx, cz - 1) and (cx, cz). Packed: type in bits 0-1, bit 2 set
     * when the windows look into the higher-coordinate zone (cx, cz).
     */
    private static int border(long seed, int cx, int cz, boolean vertical) {
        int low = vertical ? zoneKind(seed, cx - 1, cz) : zoneKind(seed, cx, cz - 1);
        int high = zoneKind(seed, cx, cz);
        double r = Hash.unit(seed, cx, cz, vertical ? 7010 : 7011);
        int type;
        if (openKind(low) && openKind(high)) {
            type = r < 0.5 ? B_OPEN : r < 0.8 ? B_WINDOW : B_DOORS;
        } else {
            type = r < 0.3 ? B_WINDOW : B_DOORS;
        }
        boolean kept = vertical == keepsWestOpen(seed, cx, cz);
        if (type == B_WINDOW && kept) {
            type = B_DOORS;
        }
        if (type != B_WINDOW) {
            return type;
        }
        // Windows look into the open-plan side if there is one (that's where they look best).
        boolean facesHigh;
        if (openKind(high) != openKind(low)) {
            facesHigh = openKind(high);
        } else {
            facesHigh = Hash.unit(seed, cx, cz, vertical ? 7012 : 7013) < 0.5;
        }
        return type | (facesHigh ? 4 : 0);
    }

    private static int borderType(int border) {
        return border & 3;
    }

    private static boolean windowsFaceHigh(int border) {
        return (border & 4) != 0;
    }

    /**
     * One column of a DOORS border at position {@code along} (1..47 along the border). Returns
     * WALL, DOORWAY, or DOOR packed with its variant (hinge/open) and the door facing.
     *
     * @param plusDir  direction of increasing {@code along}
     * @param crossDir direction across the border (into the higher zone)
     */
    private static long doorsBorderColumn(long seed, int cx, int cz, boolean vertical, int along, int plusDir, int crossDir) {
        long h = Hash.of(seed, cx, cz, vertical ? 7020 : 7021);
        // Two or three openings: one near each end, sometimes one in the middle.
        for (int k = 0; k < 3; k++) {
            long hk = Hash.mix(h + k * 0x9E3779B97F4A7C15L);
            if (k == 2 && Hash.unit(hk) < 0.5) {
                break;
            }
            int start = k == 0 ? 6 + Hash.range(hk, 10) : k == 1 ? 31 + Hash.range(hk, 10) : 21 + Hash.range(hk, 4);
            boolean doors = Hash.unit(Hash.mix(hk ^ 1)) < 0.5;
            int width = doors ? 2 : 3;
            if (along < start || along >= start + width) {
                continue;
            }
            if (!doors) {
                return pack(DOORWAY, 0, 0);
            }
            // Double wooden doors meeting in the middle, open or shut.
            boolean open = Hash.unit(Hash.mix(hk ^ 2)) < 0.55;
            boolean first = along == start;
            int facing = crossDir;
            // The door whose partner is on its clockwise side hinges LEFT (see DoorBlock#getHinge).
            boolean partnerClockwise = first == (clockwise(facing) == plusDir);
            int variant = DOOR_OFFICE | (open ? DOOR_OPEN : 0) | (partnerClockwise ? 0 : DOOR_HINGE_RIGHT);
            return pack(DOOR, facing, variant);
        }
        return withStyle(pack(WALL, 0, 0), STYLE_BASE);
    }

    // ==========================================================================================
    // The column
    // ==========================================================================================

    /**
     * Everything about column (x, z), packed (see the class comment). Exit stairwells and safe
     * rooms are not included: {@link Level4Layout} handles those first.
     */
    static long column(long seed, int x, int z) {
        int cx = Math.floorDiv(x, CELL);
        int cz = Math.floorDiv(z, CELL);
        int lx = x - cx * CELL;
        int lz = z - cz * CELL;
        int carpet = zoneCarpet(seed, cx, cz);
        long c;
        if (lx == 0 || lz == 0) {
            c = borderColumn(seed, cx, cz, lx, lz);
            if (kind(c) == FLOOR) {
                c = withCeiling(c, latticeCeiling(seed, x, z, cx, cz, true), latticeFacing(seed, cx, cz, x, z));
            }
            return withFloor(c, carpet);
        }
        c = radiator(seed, cx, cz, lx, lz);
        if (c >= 0) {
            return withCeiling(withFloor(c, carpet), latticeCeiling(seed, x, z, cx, cz, true), latticeFacing(seed, cx, cz, x, z));
        }
        boolean z_ = alongZ(seed, cx, cz);
        int u = z_ ? lz : lx;
        int v = z_ ? lx : lz;
        int plusU = z_ ? SOUTH : EAST;
        int plusV = z_ ? EAST : SOUTH;
        boolean ring = u < CORE_LO || u > CORE_HI || v < CORE_LO || v > CORE_HI;
        if (ring) {
            c = withFloor(pack(FLOOR, 0, 0), carpet);
            c = ringDetail(seed, x, z, lx, lz, c);
            return withCeiling(c, latticeCeiling(seed, x, z, cx, cz, true), latticeFacing(seed, cx, cz, x, z));
        }
        Frame f = new Frame(seed, cx, cz, u, v, plusU, plusV, carpet, x, z);
        return switch (zoneKind(seed, cx, cz)) {
            case OPEN -> openCore(f);
            case CUBICLES -> cubicleCore(f);
            case OFFICES -> officeCore(f);
            default -> lobbyCore(f);
        };
    }

    /**
     * The zone-relative view of one core column: u runs along the zone's rows, v across them;
     * {@code plusU}/{@code plusV} are the world directions in which u and v grow.
     */
    private record Frame(long seed, int cx, int cz, int u, int v, int plusU, int plusV, int carpet, int x, int z) {
        int minusU() {
            return opposite(plusU);
        }

        int minusV() {
            return opposite(plusV);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Border columns
    // ------------------------------------------------------------------------------------------

    private static long borderColumn(long seed, int cx, int cz, int lx, int lz) {
        if (lx == 0 && lz == 0) {
            // Where four borders meet: solid whenever any of them is a wall.
            boolean open = borderType(border(seed, cx, cz, true)) == B_OPEN
                    && borderType(border(seed, cx, cz - 1, true)) == B_OPEN
                    && borderType(border(seed, cx, cz, false)) == B_OPEN
                    && borderType(border(seed, cx - 1, cz, false)) == B_OPEN;
            return open ? pack(FLOOR, 0, 0) : withStyle(pack(WALL, 0, 0), STYLE_BASE);
        }
        boolean vertical = lx == 0;
        int along = vertical ? lz : lx;
        int b = border(seed, cx, cz, vertical);
        int plusDir = vertical ? SOUTH : EAST;
        int crossDir = vertical ? EAST : SOUTH;
        switch (borderType(b)) {
            case B_OPEN:
                return pack(FLOOR, 0, 0);
            case B_WINDOW: {
                if (along <= 1 || along >= CELL - 1) {
                    return withStyle(pack(WALL, 0, 0), STYLE_BASE); // piers framing the window run
                }
                int facing = windowsFaceHigh(b) ? crossDir : opposite(crossDir);
                return pack(WINDOW, facing, 0);
            }
            default:
                return doorsBorderColumn(seed, cx, cz, vertical, along, plusDir, crossDir);
        }
    }

    /**
     * Radiators stand in the first column in front of a window run, in units of three with a gap
     * (local positions 3..45, skipping every fourth, so the corners stay free). Returns the packed
     * column, or -1.
     */
    private static long radiator(long seed, int cx, int cz, int lx, int lz) {
        if (lx == 1 || lx == CELL - 1) {
            // Next to the vertical border at the west (lx == 1) or east (lx == 47) of this zone.
            int bcx = lx == 1 ? cx : cx + 1;
            int b = border(seed, bcx, cz, true);
            boolean facesThisSide = lx == 1 ? windowsFaceHigh(b) : !windowsFaceHigh(b);
            if (borderType(b) == B_WINDOW && facesThisSide && lz >= 3 && lz <= CELL - 3 && lz % 4 != 0) {
                return pack(RADIATOR, lx == 1 ? EAST : WEST, 0);
            }
        }
        if (lz == 1 || lz == CELL - 1) {
            int bcz = lz == 1 ? cz : cz + 1;
            int b = border(seed, cx, bcz, false);
            boolean facesThisSide = lz == 1 ? windowsFaceHigh(b) : !windowsFaceHigh(b);
            if (borderType(b) == B_WINDOW && facesThisSide && lx >= 3 && lx <= CELL - 3 && lx % 4 != 0) {
                return pack(RADIATOR, lz == 1 ? SOUTH : NORTH, 0);
            }
        }
        return -1;
    }

    /** A dead plant now and then in a corner of the ring aisle. */
    private static long ringDetail(long seed, int x, int z, int lx, int lz, long c) {
        boolean corner = (lx == 1 || lx == CELL - 1) && (lz == 1 || lz == CELL - 1);
        if (corner && Hash.unit(seed, x, z, 7030) < 0.35) {
            int facing = lx == 1 ? EAST : WEST;
            return furniture(c, PLANT, facing, 0);
        }
        return c;
    }

    // ------------------------------------------------------------------------------------------
    // Ceiling lights on the regular grid (open floors, rings, cubicles, the lobby hall)
    // ------------------------------------------------------------------------------------------

    /**
     * The 1x2 light panels hang on a 4-block grid. A panel's two blocks lie along the zone's rows:
     * along X at x%4 in {1, 2}, z%4 == 2 (or the same turned for zones along Z). That grid never
     * touches a border wall or a column, and both halves of a panel always lie in the same zone.
     *
     * @param open true for open-plan areas (rings, open floors): 60-80% of panels are dark there
     */
    private static int latticeCeiling(long seed, int x, int z, int cx, int cz, boolean open) {
        boolean z_ = alongZ(seed, cx, cz);
        int a = Math.floorMod(z_ ? z : x, 4);
        int b = Math.floorMod(z_ ? x : z, 4);
        if (b != 2 || (a != 1 && a != 2)) {
            return missingTile(seed, x, z, 0.012);
        }
        // Light comes in islands: 8x8 patches are lit or dark as a whole, a few panels dead.
        int px = Math.floorDiv(x, 8);
        int pz = Math.floorDiv(z, 8);
        double darkness = 0.18 + 0.22 * Hash.unit(seed, cx, cz, 7040); // per zone: 18-40% of patches lit
        double share = open ? darkness : 0.75;
        if (Hash.unit(seed, px, pz, 7041) >= share) {
            return PANEL_DEAD;
        }
        int ax = a == 1 ? 0 : -1; // key the panel by its first block so both halves agree
        int kx = z_ ? x : x + ax;
        int kz = z_ ? z + ax : z;
        double roll = Hash.unit(seed, kx, kz, 7042);
        if (roll < 0.12) return PANEL_DEAD;
        if (roll < 0.145) return PANEL_FLICKER;
        return PANEL_LIT;
    }

    /** Facing of a lattice panel half: toward its partner block. */
    private static int latticeFacing(long seed, int cx, int cz, int x, int z) {
        boolean z_ = alongZ(seed, cx, cz);
        int a = Math.floorMod(z_ ? z : x, 4);
        if (z_) {
            return a == 1 ? SOUTH : NORTH;
        }
        return a == 1 ? EAST : WEST;
    }

    private static int missingTile(long seed, int x, int z, double chance) {
        double r = Hash.unit(seed, x, z, 7045);
        if (r < chance) return TILE_MISSING;
        if (r < chance + 0.002) return HATCH;
        return TILE;
    }

    /** Ceiling of a corridor: a panel pair every 6 blocks along its centre line (1 = centre). */
    private static long corridorCeiling(Frame f, long c, int across, double litShare) {
        int a = Math.floorMod(f.u, 6);
        if (across != 1 || (a != 1 && a != 2)) {
            return withCeiling(c, missingTile(f.seed, f.x, f.z, 0.008), 0);
        }
        int first = a == 1 ? f.u : f.u - 1;
        long h = Hash.of(f.seed, f.cx * 64 + first, f.cz * 64 + f.v, 7046);
        int state = Hash.unit(h) < litShare ? (Hash.unit(Hash.mix(h)) < 0.03 ? PANEL_FLICKER : PANEL_LIT) : PANEL_DEAD;
        return withCeiling(c, state, a == 1 ? f.plusU : f.minusU());
    }

    // ------------------------------------------------------------------------------------------
    // OPEN zones
    // ------------------------------------------------------------------------------------------

    private static long openCore(Frame f) {
        int c = f.v - CORE_LO;
        if (zoneVariant(f.seed, f.cx, f.cz) == 1 && c >= 34) {
            // A row of small offices along the far side, doors facing the open floor.
            return roomRow(f, 34, 40, false, 9, false);
        }
        return openFloor(f, true);
    }

    /** Open-plan floor: square columns every 8 blocks, furniture islands between them. */
    private static long openFloor(Frame f, boolean islands) {
        long seed = f.seed;
        int x = f.x;
        int z = f.z;
        long base = withFloor(pack(FLOOR, 0, 0), f.carpet);
        if (Math.floorMod(x, 8) == 4 && Math.floorMod(z, 8) == 4) {
            return withFloor(pack(COLUMN, 0, 0), f.carpet);
        }
        long c = islands ? island(f, base) : base;
        c = withCeiling(c, latticeCeiling(seed, x, z, f.cx, f.cz, true), latticeFacing(seed, f.cx, f.cz, x, z));
        if (kind(c) == FLOOR) {
            c = cctv(seed, x, z, c);
        }
        return c;
    }

    /** A CCTV camera high on a column, looking out over the floor (about 1 column in 25). */
    private static long cctv(long seed, int x, int z, long c) {
        for (int d = 0; d < 4; d++) {
            int colX = x - DX[d];
            int colZ = z - DZ[d];
            if (Math.floorMod(colX, 8) != 4 || Math.floorMod(colZ, 8) != 4) {
                continue;
            }
            long h = Hash.of(seed, colX, colZ, 7050);
            if (Hash.unit(h) < 0.04 && Hash.range(Hash.mix(h), 4) == d) {
                return withDeco(c, DECO_CCTV, d);
            }
        }
        return c;
    }

    /**
     * Furniture islands: each 8x8 square between four columns may hold a cluster in its middle
     * (offsets 2..6 from the column line), so a free walkway always runs along the columns.
     * Only squares fully inside a zone's core (not the ring) get one.
     */
    private static long island(Frame f, long base) {
        long seed = f.seed;
        int sx = Math.floorDiv(f.x - 4, 8);
        int sz = Math.floorDiv(f.z - 4, 8);
        int ox = f.x - 4 - sx * 8;
        int oz = f.z - 4 - sz * 8;
        if (ox < 2 || ox > 6 || oz < 2 || oz > 6) {
            return base;
        }
        // The square must lie inside the core (local 5..43 on both axes) ...
        int localX = Math.floorMod(f.x, CELL) - ox;
        int localZ = Math.floorMod(f.z, CELL) - oz;
        if (localX < 4 || localX > 36 || localZ < 4 || localZ > 36) {
            return base;
        }
        // ... and inside the open part of the zone (not the office row / cubicle rows).
        int vStart = alongZ(seed, f.cx, f.cz) ? localX : localZ;
        int kind = zoneKind(seed, f.cx, f.cz);
        if (kind == OPEN && zoneVariant(seed, f.cx, f.cz) == 1 && vStart + 7 >= 38) {
            return base;
        }
        if (kind == CUBICLES && vStart + 1 < 33) {
            return base;
        }
        long h = Hash.of(seed, sx, sz, 7060);
        double r = Hash.unit(h);
        boolean flip = Hash.unit(Hash.mix(h ^ 1)) < 0.5; // which axis the cluster lines up with
        int a = flip ? oz : ox;   // along the cluster
        int b = flip ? ox : oz;   // across it
        int plusA = flip ? SOUTH : EAST;
        int plusB = flip ? EAST : SOUTH;
        if (r < 0.28) {
            // Nothing left but the dents in the carpet where a desk pair stood.
            if (Hash.unit(Hash.mix(h ^ 2)) < 0.6 && a >= 3 && a <= 5 && (b == 3 || b == 4)) {
                return withFloor(base, f.carpet | INDENT);
            }
            return base;
        }
        if (r < 0.50) {
            // Two rows of desks facing each other, chairs on the outside.
            boolean missing = Hash.unit(Hash.mix(h ^ 3)) < 0.3; // one desk carried away
            if ((b == 3 || b == 4) && a >= 3 && a <= 5) {
                if (missing && b == 4 && a == 5) {
                    return withFloor(base, f.carpet | INDENT);
                }
                int facing = b == 3 ? opposite(plusB) : plusB;
                boolean pc = Hash.unit(seed, f.x, f.z, 7061) < 0.5;
                return furniture(base, pc ? DESK_PC : DESK, facing, 0);
            }
            if ((b == 2 || b == 5) && a >= 3 && a <= 5 && Hash.unit(seed, f.x, f.z, 7062) < 0.55) {
                int facing = b == 2 ? plusB : opposite(plusB); // looking at the desk
                if (Hash.unit(seed, f.x, f.z, 7063) < 0.3) {
                    facing = Hash.range(Hash.of(seed, f.x, f.z, 7064), 4); // pushed back, turned away
                }
                return furniture(base, CHAIR, facing, 0);
            }
            return base;
        }
        if (r < 0.62) {
            // Filing cabinets standing back to back.
            if (a >= 3 && a <= 5 && (b == 3 || (b == 4 && Hash.unit(Hash.mix(h ^ 4)) < 0.6))) {
                return furniture(base, CABINET, b == 3 ? opposite(plusB) : plusB, 0);
            }
            return base;
        }
        if (r < 0.72) {
            // A few piles of cardboard boxes.
            // Only on every other row and column, so no free spot is ever boxed in.
            if (a % 2 == 0 && b % 2 == 0 && Hash.unit(seed, f.x, f.z, 7065) < 0.45) {
                return furniture(base, BOXES, Hash.range(Hash.of(seed, f.x, f.z, 7066), 4),
                        Hash.unit(seed, f.x, f.z, 7067) < 0.35 ? 1 : 0);
            }
            return base;
        }
        if (r < 0.78) {
            // A lone copier with paper boxes beside it.
            if (a == 4 && b == 4) return furniture(base, COPIER, plusB, 0);
            if (a == 5 && b == 4) return furniture(base, BOXES, plusB, 0);
            return base;
        }
        if (r < 0.84) {
            // A little waiting corner: a sofa and a dead plant.
            if (b == 4 && (a == 3 || a == 4)) return furniture(base, SOFA, opposite(plusB), 0);
            if (b == 4 && a == 5) return furniture(base, PLANT, opposite(plusB), 0);
            return base;
        }
        if (r < 0.90) {
            // Abandoned renovation: a step ladder and boxes, a missing ceiling tile above.
            if (a == 4 && b == 4) return furniture(base, LADDER, plusA, 0);
            if (a == 3 && b == 5) return furniture(base, BOXES, plusB, 1);
            return base;
        }
        if (r < 0.96) {
            // Office chairs left in a huddle, turned every which way.
            if (a >= 3 && a <= 5 && b >= 3 && b <= 4 && Hash.unit(seed, f.x, f.z, 7068) < 0.5) {
                return furniture(base, CHAIR, Hash.range(Hash.of(seed, f.x, f.z, 7069), 4), 0);
            }
            return base;
        }
        // Supplies: a crate (or two), sometimes a backpack someone dropped.
        if (a == 4 && b == 4) {
            double s = Hash.unit(Hash.mix(h ^ 5));
            if (s < 0.6) return furniture(base, CRATE, plusB, s < 0.2 ? 1 : 0);
            return furniture(base, BACKPACK, Hash.range(Hash.mix(h ^ 6), 4), 0);
        }
        return base;
    }

    /** Puts a piece of furniture on a floor column, keeping its floor and ceiling. */
    private static long furniture(long base, int kind, int facing, int variant) {
        return (base & ~((0xFFL << KIND_SHIFT) | (3L << FACING_SHIFT) | (0xFFL << VARIANT_SHIFT))) | pack(kind, facing, variant);
    }

    // ------------------------------------------------------------------------------------------
    // CUBICLE zones
    // ------------------------------------------------------------------------------------------

    /*
     * Rows across the core (c = v - 4, 0..40):
     *   c 0-1 aisle | pods 2-4 | spine 5 | pods 6-8 | aisle 9-10 | pods 11-13 | spine 14 | pods 15-17
     *   | aisle 18-19 | pods 20-22 | spine 23 | pods 24-26 | aisle 27-28 | far end 29-40
     * Along the rows partitions stand every 4 blocks (u = 4, 8, ... 44), so each pod is 3 x 3
     * inside, its open front on an aisle. The pods at u 25..27 are left out: a cross aisle.
     */
    private static long cubicleCore(Frame f) {
        int c = f.v - CORE_LO;
        if (c >= 29) {
            if (zoneVariant(f.seed, f.cx, f.cz) == 0) {
                return roomRow(f, 29, 40, false, 20, true);
            }
            return openFloor(f, true);
        }
        long base = withFloor(pack(FLOOR, 0, 0), f.carpet);
        base = withCeiling(base, latticeCeiling(f.seed, f.x, f.z, f.cx, f.cz, false), latticeFacing(f.seed, f.cx, f.cz, f.x, f.z));
        if (cubiclePartition(f.u, c)) {
            int bits = 0;
            for (int d = 0; d < 4; d++) {
                // Neighbour along this world direction, in zone coordinates.
                int du = d == f.plusU ? 1 : d == f.minusU() ? -1 : 0;
                int dv = d == f.plusV ? 1 : d == f.minusV() ? -1 : 0;
                int nu = f.u + du;
                int nc = c + dv;
                if (nu >= CORE_LO && nu <= CORE_HI && nc >= 0 && nc < 29 && cubiclePartition(nu, nc)) {
                    bits |= 1 << d;
                }
            }
            return furniture(base, PARTITION, 0, bits);
        }
        int group = podGroup(c);
        if (group < 0) {
            return base; // an aisle
        }
        int pod = (f.u - CORE_LO) / 4;
        if (pod == 5) {
            return base; // the cross aisle
        }
        int b = 2 + 9 * group;
        boolean sideA = c <= b + 2;      // pod A: front at c = b, desks against the spine at b + 2
        int depth = sideA ? c - b : b + 6 - c;   // 0 = front row, 2 = desk row
        int iu = (f.u - CORE_LO) % 4;    // 1..3 inside the pod
        int toFront = sideA ? f.minusV() : f.plusV;
        long h = Hash.of(f.seed, f.cx * 16 + pod, f.cz * 16 + group * 2 + (sideA ? 0 : 1), 7070);
        double r = Hash.unit(h);
        if (depth == 2) {
            if (r < 0.15) {
                return withFloor(base, f.carpet | INDENT); // desk taken away: only the dents remain
            }
            boolean pc = iu == 2 && Hash.unit(Hash.mix(h ^ 1)) < 0.7;
            return furniture(base, pc ? DESK_PC : DESK, toFront, 0);
        }
        if (depth == 1 && iu == 2 && Hash.unit(Hash.mix(h ^ 2)) < 0.75) {
            int facing = opposite(toFront); // looking at the desk
            if (r < 0.15 || Hash.unit(Hash.mix(h ^ 3)) < 0.25) {
                facing = Hash.range(Hash.mix(h ^ 4), 4);
            }
            return furniture(base, CHAIR, facing, 0);
        }
        if (depth == 1 && iu == 3) {
            // Beside the chair, against the side partition (the front row stays free to walk in).
            if (r >= 0.15 && r < 0.27) return furniture(base, BOXES, toFront, Hash.unit(Hash.mix(h ^ 5)) < 0.3 ? 1 : 0);
            if (r >= 0.27 && r < 0.29) return furniture(base, BACKPACK, Hash.range(Hash.mix(h ^ 6), 4), 0);
        }
        return base;
    }

    /** Which group of back-to-back pods row c belongs to (0..2), or -1 for an aisle row. */
    private static int podGroup(int c) {
        for (int g = 0; g < 3; g++) {
            int b = 2 + 9 * g;
            if (c >= b && c <= b + 6) {
                return g;
            }
        }
        return -1;
    }

    /** True where a cubicle partition stands (zone coordinates u, row c). */
    private static boolean cubiclePartition(int u, int c) {
        int g = podGroup(c);
        if (g < 0 || u < CORE_LO || u > CORE_HI) {
            return false;
        }
        int b = 2 + 9 * g;
        boolean crossAisle = u >= 25 && u <= 27;
        if (c == b + 3) {
            return !crossAisle; // the spine between the two pod rows
        }
        return (u - CORE_LO) % 4 == 0;
    }

    // ------------------------------------------------------------------------------------------
    // OFFICE zones
    // ------------------------------------------------------------------------------------------

    /*
     * Rows across the core (c = v - 4, 0..40):
     *   A rooms 0-6 (door wall 6) | corridor 7-9 | B rooms 10-16 (door wall 10) | C rooms 16-22
     *   (door wall 22) | corridor 23-25 | D rooms 26-32 (door wall 26) | E big rooms 32-40 (door
     *   wall 40, onto the ring aisle).
     * The corridors run the whole length of the core and open onto the ring at both ends.
     */
    private static long officeCore(Frame f) {
        int c = f.v - CORE_LO;
        if ((c >= 7 && c <= 9) || (c >= 23 && c <= 25)) {
            long base = withFloor(pack(FLOOR, 0, 0), f.carpet);
            return corridorCeiling(f, base, c == 8 || c == 24 ? 1 : 0, 0.5);
        }
        if (c <= 6) return roomRow(f, 0, 6, true, 1, false);
        if (c <= 16 && c >= 10 && c != 16) return roomRow(f, 10, 16, false, 2, false);
        if (c <= 22) return roomRow(f, 16, 22, true, 3, false);
        if (c <= 32 && c != 32) return roomRow(f, 26, 32, false, 4, false);
        return roomRow(f, 32, 40, true, 5, true);
    }

    // ------------------------------------------------------------------------------------------
    // LOBBY zones
    // ------------------------------------------------------------------------------------------

    /*
     * Rows across the core (c = v - 4, 0..40):
     *   A big rooms 0-15 (door wall 15) | lobby hall 16-24 | B big rooms 25-40 (door wall 25).
     * In both rows u 16..32 is the solid elevator core: four elevator doors per side face the hall.
     */
    private static long lobbyCore(Frame f) {
        int c = f.v - CORE_LO;
        if (c >= 16 && c <= 24) {
            return lobbyHall(f, c);
        }
        boolean rowA = c <= 15;
        int wallLo = rowA ? 0 : 25;
        int wallHi = rowA ? 15 : 40;
        boolean doorHigh = rowA;
        int doorWall = doorHigh ? wallHi : wallLo;
        boolean coreSpan = f.u >= 15 && f.u <= 33;
        if (!coreSpan) {
            return roomRow(f, wallLo, wallHi, doorHigh, rowA ? 6 : 7, true);
        }
        // The elevator core. Its walls take the lobby's chair rail; elevators face the hall.
        if (c == doorWall) {
            int k = f.u - 17;
            if (k >= 0 && k < 16 && k % 4 < 2) {
                int out = doorHigh ? f.plusV : f.minusV();
                boolean right = (k % 4 == 1) == (clockwise(out) == f.plusU);
                return withFloor(pack(ELEVATOR, out, right ? 1 : 0), f.carpet);
            }
            return withFloor(withStyle(pack(WALL, 0, 0), STYLE_RAIL), f.carpet);
        }
        if (f.u == 15 || f.u == 33 || c == wallLo || c == wallHi) {
            return withFloor(withStyle(pack(WALL, 0, 0), STYLE_BASE), f.carpet);
        }
        return pack(FILL, 0, 0);
    }

    private static long lobbyHall(Frame f, int c) {
        long base = withFloor(pack(FLOOR, 0, 0), f.carpet);
        base = withCeiling(base, latticeCeiling(f.seed, f.x, f.z, f.cx, f.cz, false), latticeFacing(f.seed, f.cx, f.cz, f.x, f.z));
        boolean nearA = c == 16;  // first row in front of row A's wall
        boolean nearB = c == 24;
        if (nearA || nearB) {
            int awayFromWall = nearA ? f.plusV : f.minusV();
            // Call buttons beside each elevator, a directory plaque in the middle.
            int k = f.u - 19;
            if (k >= 0 && k <= 12 && k % 4 == 0) {
                return withDeco(base, DECO_ELEVATOR_PANEL, awayFromWall);
            }
            if (nearA && f.u == 24) {
                return withDeco(base, DECO_PLAQUE, awayFromWall);
            }
            if (f.u == 15 || f.u == 33) {
                return furniture(base, PLANT, awayFromWall, 0);
            }
            if (nearB && f.u == 14 && Hash.unit(f.seed, f.cx, f.cz, 7080) < 0.8) {
                return furniture(base, COOLER, awayFromWall, 3);
            }
            if (nearA && f.u == 34 && Hash.unit(f.seed, f.cx, f.cz, 7081) < 0.6) {
                return furniture(base, SOFA, awayFromWall, 0);
            }
        }
        return base;
    }

    // ==========================================================================================
    // Rows of rooms
    // ==========================================================================================

    // Room kinds.
    private static final int R_OFFICE = 0;
    private static final int R_EMPTY = 1;
    private static final int R_STORAGE = 2;
    private static final int R_COPY = 3;
    private static final int R_SERVER = 4;
    private static final int R_CLOSET = 5;
    private static final int R_BREAK = 6;
    private static final int R_CONFERENCE = 7;
    private static final int R_CURTAIN = 8;

    /**
     * One column of a row of rooms between two walls running along u: wallLo (row c) and wallHi.
     * The rooms' doors are in wallHi ({@code doorHigh}) or wallLo. Rooms are split along u by
     * 1-block partitions with hashed widths (4-7 for small offices, 8-12 for big rooms); the row
     * ends with walls at u = 4 and u = 44, facing the ring aisle.
     *
     * @param rowId a number unique to this row within the zone (keys its random choices)
     * @param big   big rooms (break rooms, conference rooms...) instead of small offices
     */
    private static long roomRow(Frame f, int wallLo, int wallHi, boolean doorHigh, int rowId, boolean big) {
        int c = f.v - CORE_LO;
        int u = f.u;
        int doorWall = doorHigh ? wallHi : wallLo;
        long wallBase = withFloor(withStyle(pack(WALL, 0, 0), STYLE_BASE), f.carpet);
        if (u == CORE_LO || u == CORE_HI) {
            return wallBase;
        }
        long room = roomAt(f.seed, f.cx, f.cz, rowId, u, big ? 8 : 4, big ? 12 : 7);
        if (room < 0) {
            return wallBase; // partition between two rooms
        }
        int u0 = (int) (room >>> 16) & 0xFF;
        int w = (int) (room >>> 8) & 0xFF;
        int index = (int) room & 0xFF;
        long h = Hash.of(f.seed, f.cx, f.cz, 7200 + rowId * 64 + index);
        int kind = roomKind(h, big, rowId);
        int depth = wallHi - wallLo - 1;
        int in = doorHigh ? f.minusV() : f.plusV;   // from the door into the room
        int out = opposite(in);
        int doorU = u0 + 1 + Hash.range(Hash.mix(h ^ 0x11), Math.max(1, w - 2));
        boolean doubleDoor = kind == R_CONFERENCE || kind == R_BREAK;
        boolean glass = kind == R_CONFERENCE;

        if (c == wallLo || c == wallHi) {
            if (c != doorWall) {
                return wallBase;
            }
            boolean isDoor = u == doorU || (doubleDoor && u == doorU + 1 && doorU + 1 < u0 + w);
            if (!isDoor) {
                if (glass) {
                    boolean blinds = Hash.unit(Hash.mix(h ^ 0x12)) < 0.5;
                    return withStyle(wallBase, blinds ? STYLE_BLINDS : STYLE_GLASS);
                }
                return wallBase;
            }
            if (kind == R_BREAK) {
                return withFloor(pack(DOORWAY, 0, 0), f.carpet); // break rooms: just a wide opening
            }
            boolean open = Hash.unit(Hash.mix(h ^ 0x13)) < (kind == R_CLOSET ? 0.0 : 0.45);
            int type = glass ? DOOR_GLASS : DOOR_OFFICE;
            int variant = type | (open ? DOOR_OPEN : 0);
            if (kind == R_CLOSET) {
                variant = (Hash.unit(Hash.mix(h ^ 0x14)) < 0.12 ? DOOR_SIX : DOOR_OFFICE) | DOOR_LOCKED;
            }
            if (doubleDoor) {
                boolean first = u == doorU;
                boolean partnerClockwise = first == (clockwise(out) == f.plusU);
                variant |= partnerClockwise ? 0 : DOOR_HINGE_RIGHT;
            } else if (Hash.unit(Hash.mix(h ^ 0x15)) < 0.5) {
                variant |= DOOR_HINGE_RIGHT;
            }
            return withFloor(pack(DOOR, out, variant), f.carpet);
        }

        // Inside the room. iu: 0..w-1 along u; iv: 0 (by the door) .. depth-1 (back wall).
        int iu = u - u0;
        int iv = doorHigh ? wallHi - 1 - c : c - wallLo - 1;
        if (kind == R_CLOSET) {
            return pack(FILL, 0, 0);
        }
        int floor = kind == R_SERVER ? CONCRETE : f.carpet;
        long base = withFloor(pack(FLOOR, 0, 0), floor);
        base = withCeiling(base, roomCeiling(f, h, kind, iu, iv, w, depth, in, out), roomPanelFacing(f, iu, w));
        Room r = new Room(f, h, kind, iu, iv, w, depth, in, out, doorU - u0);
        return roomFurniture(r, base);
    }

    /**
     * The room of row {@code rowId} containing u, packed (u0 << 16 | width << 8 | index), or -1
     * when u is a partition wall. Rooms fill u = 5..43 left to right.
     */
    private static long roomAt(long seed, int cx, int cz, int rowId, int u, int minW, int maxW) {
        if (rowId == 6 || rowId == 7) {
            // Lobby rows: one 10-wide room on each side of the elevator core (u 15..33).
            if (u >= 5 && u <= 14) return (5L << 16) | (10L << 8);
            if (u >= 34 && u <= 43) return (34L << 16) | (10L << 8) | 1;
            return -1;
        }
        int pos = CORE_LO + 1;
        long h = Hash.of(seed, cx, cz, 7100 + rowId);
        for (int index = 0; index < 16; index++) {
            int remaining = CORE_HI - pos; // free columns pos .. CORE_HI - 1
            int w;
            if (remaining <= maxW || remaining < 2 * minW + 1) {
                w = remaining; // the last room takes the rest
            } else {
                w = minW + Hash.range(Hash.mix(h + index * 0x9E3779B97F4A7C15L), maxW - minW + 1);
                w = Math.min(w, remaining - minW - 1);
            }
            if (u < pos + w) {
                return ((long) pos << 16) | ((long) w << 8) | index;
            }
            if (u == pos + w) {
                return -1;
            }
            pos += w + 1;
        }
        return -1;
    }

    private static int roomKind(long h, boolean big, int rowId) {
        double r = Hash.unit(Hash.mix(h ^ 0x10));
        if (!big) {
            if (r < 0.58) return R_OFFICE;
            if (r < 0.70) return R_EMPTY;
            if (r < 0.80) return R_STORAGE;
            if (r < 0.87) return R_COPY;
            if (r < 0.93) return R_CLOSET;
            return R_SERVER;
        }
        if (r < 0.30) return R_BREAK;
        if (r < 0.58) return R_CONFERENCE;
        if (r < 0.70) return R_COPY;
        if (r < 0.82) return R_STORAGE;
        if (r < 0.90) return R_SERVER;
        if (r < 0.94) return R_CURTAIN;
        return R_EMPTY;
    }

    /** Small rooms: one panel pair in the middle. Big rooms: pairs on a 4-block grid. Mostly lit. */
    private static int roomCeiling(Frame f, long h, int kind, int iu, int iv, int w, int depth, int in, int out) {
        boolean lit = Hash.unit(Hash.mix(h ^ 0x20)) < (kind == R_EMPTY || kind == R_STORAGE ? 0.5 : 0.8);
        boolean panel;
        if (w <= 7) {
            int mid = (w - 1) / 2;
            panel = (iu == mid || iu == mid + 1) && iv == depth / 2;
        } else {
            panel = (iu % 4 == 1 || iu % 4 == 2) && iu + (iu % 4 == 1 ? 1 : 0) < w && iv % 4 == 2 && iv < depth;
        }
        if (!panel) {
            return missingTile(f.seed, f.x, f.z, kind == R_STORAGE || kind == R_EMPTY ? 0.02 : 0.004);
        }
        if (!lit) {
            return PANEL_DEAD;
        }
        int first = w <= 7 ? (w - 1) / 2 : iu % 4 == 1 ? iu : iu - 1; // both halves of a panel roll the same
        double roll = Hash.unit(Hash.mix(h ^ (0x300L + first * 32L + iv)));
        return roll < 0.08 ? PANEL_DEAD : roll < 0.11 ? PANEL_FLICKER : PANEL_LIT;
    }

    /** Room panels lie along u; the first half points +u at its partner. */
    private static int roomPanelFacing(Frame f, int iu, int w) {
        boolean first = w <= 7 ? iu == (w - 1) / 2 : iu % 4 == 1;
        return first ? f.plusU : f.minusU();
    }

    /** One room column in the room's own frame (see {@link #roomRow}). */
    private record Room(Frame f, long h, int kind, int iu, int iv, int w, int depth, int in, int out, int doorIu) {
        int left() {
            return f.minusU();
        }

        int right() {
            return f.plusU;
        }

        double rand(int salt) {
            return Hash.unit(Hash.mix(h ^ (0x100L + salt)));
        }

        /** True for the column just inside the door (always kept free). */
        boolean byDoor() {
            return iv == 0 && (iu == doorIu || iu == doorIu + 1);
        }
    }

    private static long roomFurniture(Room r, long base) {
        if (r.byDoor()) {
            return base;
        }
        return switch (r.kind) {
            case R_OFFICE -> officeRoom(r, base, false);
            case R_EMPTY -> officeRoom(r, base, true);
            case R_STORAGE -> storageRoom(r, base);
            case R_COPY -> copyRoom(r, base);
            case R_SERVER -> serverRoom(r, base);
            case R_BREAK -> breakRoom(r, base);
            case R_CONFERENCE -> conferenceRoom(r, base);
            case R_CURTAIN -> curtainRoom(r, base);
            default -> base;
        };
    }

    /**
     * A small private office (or, when {@code empty}, one that was cleared out: dents in the
     * carpet, a forgotten chair, boxes). Back row: filing cabinet and plant (or a supply locker);
     * a desk across the room facing the door, its chair behind it; a whiteboard on the left wall.
     */
    private static long officeRoom(Room r, long base, boolean empty) {
        int w = r.w;
        int d = r.depth;
        int mid = w / 2;
        int deskRow = Math.min(2, d - 3);
        if (r.iv == deskRow && (r.iu == mid - 1 || r.iu == mid)) {
            if (empty) {
                return withFloor(base, floor(base) | INDENT);
            }
            boolean pc = r.iu == mid && r.rand(1) < 0.75;
            return furniture(base, pc ? DESK_PC : DESK, r.in, 0); // the user sits on the far side
        }
        if (r.iv == deskRow + 1 && r.iu == mid && (!empty || r.rand(2) < 0.4)) {
            int facing = empty ? Hash.range(Hash.mix(r.h ^ 3), 4) : r.out;
            return furniture(base, CHAIR, facing, 0);
        }
        if (r.iv == d - 1) {
            if (r.iu == 0) {
                if (empty) return withFloor(base, floor(base) | INDENT);
                return furniture(base, CABINET, r.out, 0);
            }
            if (r.iu == w - 1) {
                double s = r.rand(4);
                if (s < 0.14) return furniture(base, LOCKER, r.out, 0);
                if (s < 0.22) return furniture(base, SUPPLY_CABINET, r.out, 0);
                if (s < 0.55) return furniture(base, PLANT, r.out, 0);
            }
        }
        // Boxes or a ladder just inside the door, on the right; only in rooms 5+ wide, where the
        // column beside the desk keeps the right-hand side of the room reachable.
        if (r.iu == w - 1 && r.iv == 1 && w >= 5) {
            double s = r.rand(5);
            if (s < (empty ? 0.6 : 0.25)) return furniture(base, BOXES, r.left(), s < 0.12 ? 1 : 0);
            if (s < (empty ? 0.75 : 0.31)) return furniture(base, LADDER, r.left(), 0);
        }
        if (r.iu == 0 && r.iv == deskRow && r.rand(6) < 0.6) {
            return withDeco(base, DECO_WHITEBOARD, r.right()); // on the left wall, facing into the room
        }
        return base;
    }

    /** Storage: boxes and crates along the walls, a ladder, the middle kept clear. */
    private static long storageRoom(Room r, long base) {
        boolean back = r.iv == r.depth - 1;
        boolean side = r.iu == 0 || r.iu == r.w - 1;
        if (!back && !side) {
            return base;
        }
        if (r.iv == 0) {
            return base; // the door row stays free
        }
        int facing = back ? r.out : r.iu == 0 ? r.right() : r.left();
        if (back && side) {
            // Back corners are always piled with boxes: a free corner would be a pocket you can't
            // reach, and a crate there could never be opened.
            return furniture(base, BOXES, facing, 1);
        }
        double s = Hash.unit(r.f.seed, r.f.x, r.f.z, 7300);
        if (s < 0.12) return furniture(base, CRATE, facing, s < 0.04 ? 1 : 0);
        if (s < 0.17) return furniture(base, LOCKER, facing, 0);
        if (s < 0.62) return furniture(base, BOXES, Hash.range(Hash.of(r.f.seed, r.f.x, r.f.z, 7301), 4), s < 0.35 ? 1 : 0);
        if (s < 0.72) return furniture(base, CABINET, facing, 0);
        if (s < 0.75) return furniture(base, LADDER, facing, 0);
        return base;
    }

    /** Copy room: copiers against the back wall, filing cabinets along one side, paper boxes. */
    private static long copyRoom(Room r, long base) {
        // Copiers along the back wall; the last two places stay free so nothing gets walled in.
        if (r.iv == r.depth - 1 && r.iu >= 1 && r.iu <= r.w - 3) {
            if (r.iu % 3 == 1) return furniture(base, COPIER, r.out, 0);
            if (r.iu % 3 == 2) return furniture(base, BOXES, r.out, 0);
            return base;
        }
        if (r.iu == 0 && r.iv >= 1 && r.iv <= r.depth - 1) {
            if (r.iv == 2 && r.rand(1) < 0.35) return furniture(base, SUPPLY_CABINET, r.right(), 0);
            return furniture(base, CABINET, r.right(), 0);
        }
        if (r.iu == r.w - 1 && r.iv == r.depth - 2 && r.rand(2) < 0.5) {
            return furniture(base, BOXES, r.left(), 1);
        }
        return base;
    }

    /** Server room: bare concrete, rows of racks humming with dead LEDs. */
    private static long serverRoom(Room r, long base) {
        if (r.iv >= 2 && r.iv % 2 == 0 && r.iv <= r.depth - 1 && r.iu >= 1 && r.iu <= r.w - 2) {
            return furniture(base, SERVER_RACK, r.out, 0);
        }
        return base;
    }

    /**
     * Break room: a counter with a microwave and a fridge along the back wall, vending machines
     * and a water cooler along the side walls, small tables with chairs in the middle.
     */
    private static long breakRoom(Room r, long base) {
        int w = r.w;
        int d = r.depth;
        if (r.iv == d - 1) {
            if (r.iu == w - 1) return furniture(base, FRIDGE, r.out, 0);
            if (r.iu >= 1 && r.iu <= Math.min(w - 2, 5)) {
                return furniture(base, COUNTER, r.out, r.iu == 2 ? 1 : 0);
            }
            return base;
        }
        if (r.iu == 0 && (r.iv == d - 3 || r.iv == d - 4)) {
            int stock = 2 + Hash.range(Hash.mix(r.h ^ (31L + r.iv)), 5);
            return furniture(base, VENDING, r.right(), stock);
        }
        if (r.iu == w - 1 && r.iv == 1) {
            return furniture(base, COOLER, r.left(), 3);
        }
        // Tables: one block each, on a 3-block grid, chairs on the door side and the back side.
        if (r.iu >= 2 && r.iu <= w - 3 && r.iv >= 2 && r.iv <= d - 3) {
            int ti = (r.iu - 2) % 3;
            int tj = (r.iv - 2) % 3;
            if (ti == 1 && tj == 1) {
                return furniture(base, TABLE, r.out, 0);
            }
            if (ti == 1 && (tj == 0 || tj == 2) && Hash.unit(r.f.seed, r.f.x, r.f.z, 7310) < 0.7) {
                int facing = tj == 0 ? r.in : r.out; // looking at the table
                if (Hash.unit(r.f.seed, r.f.x, r.f.z, 7311) < 0.3) facing = clockwise(facing);
                return furniture(base, CHAIR, facing, 0);
            }
        }
        return base;
    }

    /** Conference room: one long table down the middle, chairs along both sides, a whiteboard. */
    private static long conferenceRoom(Room r, long base) {
        int w = r.w;
        int d = r.depth;
        int tableLo = d / 2 - (d >= 8 ? 1 : 0);
        int tableHi = d / 2;
        boolean alongTable = r.iu >= 2 && r.iu <= w - 3;
        if (alongTable && r.iv >= tableLo && r.iv <= tableHi) {
            return furniture(base, CONF_TABLE, r.out, 0);
        }
        if (alongTable && (r.iv == tableLo - 1 || r.iv == tableHi + 1) && (r.iu - 2) % 2 == 0
                && Hash.unit(r.f.seed, r.f.x, r.f.z, 7320) < 0.8) {
            int facing = r.iv == tableLo - 1 ? r.in : r.out;
            return furniture(base, CHAIR, facing, 0);
        }
        if (r.iv == d - 1 && (r.iu == w / 2 || r.iu == w / 2 - 1)) {
            return withDeco(base, DECO_WHITEBOARD, r.out); // on the back wall
        }
        if (r.iv == d - 1 && r.iu == 0 && r.rand(1) < 0.6) {
            return furniture(base, PLANT, r.out, 0);
        }
        return base;
    }

    /**
     * The curtain room: heavy red curtains hang along every wall, a few chairs face the back as if
     * waiting for a show. It is only decoration.
     */
    private static long curtainRoom(Room r, long base) {
        boolean edge = r.iu == 0 || r.iu == r.w - 1 || r.iv == r.depth - 1;
        if (edge && r.iv >= 1) {
            return furniture(base, CURTAIN, r.out, 0);
        }
        if (r.iv == r.depth / 2 && r.iu >= 2 && r.iu <= r.w - 3 && r.iu % 2 == 0) {
            return furniture(base, CHAIR, r.in, 0);
        }
        return base;
    }

    // ==========================================================================================
    // Small helpers for Level4Layout
    // ==========================================================================================

    /** One step in direction {@code dir} (a 2D data value): {dx, dz}. */
    static int stepX(int dir) {
        return DX[dir & 3];
    }

    static int stepZ(int dir) {
        return DZ[dir & 3];
    }

    private Level4Plan() {}
}
