package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Hash;

/**
 * The floor plan of Level 4 ("The Abandoned Office") as plain numbers: for every column (x, z) it
 * says WHAT stands there (a wall, a table, a window, a door, open carpet...), which way it faces,
 * what the floor and the ceiling look like and what hangs on the wall. {@link Level4Layout} turns
 * these numbers into blocks.
 *
 * <p>This class uses no Minecraft classes at all, only {@link Hash} and {@link ExitLocator}. That
 * keeps it a pure function of (seed, x, z), and it means it could be tested without starting the
 * game (for example with a flood fill that checks every floor column can be reached).
 *
 * <h2>The canon this follows</h2>
 * Level 4 is an empty office building, almost completely devoid of furniture: white walls, cheap
 * grey carpet, most lights dead, lit by blacked-out windows that still glow. Its rooms are roughly
 * 45% empty rooms, 20% conference rooms (one identical long table, chairs at even intervals,
 * papers, pencils, mugs of Almond Water), 30% lobbies and cafeterias (often merged: big empty
 * spaces, rows of the same tables, the odd bottle of Almond Water) and 5% cubicle clusters. Almond
 * Water is found here more often than anywhere else: water coolers, vending machines, fountains.
 * Because the rooms are nearly empty, the detail lives on the building itself: door frames, room
 * signs, the pale marks where pictures and whiteboards hung, cable trunking, floor boxes, switches,
 * thermostats, fire equipment, signs, clocks.
 *
 * <h2>Zones</h2>
 * The world is cut into square zones of {@link #CELL} x {@link #CELL} blocks. Each zone is one of:
 * <ul>
 *   <li>{@link #OPEN} (28%): a huge empty open-plan floor with square white columns every 8
 *       blocks: only the dents of desk rows that were carried out and their floor boxes, a water
 *       cooler and an extinguisher on some columns, rarely a desk or a moving-day pile. Some have a
 *       row of small rooms along one side.</li>
 *   <li>{@link #CUBICLES} (6%): a cluster of 54 complete grey cubicles (desk, computer, chair, the
 *       same desk set in every one), and at the far end glass conference rooms or more open floor.</li>
 *   <li>{@link #OFFICES} (36%): two corridors lined with doors into small rooms (mostly empty, many
 *       conference rooms) and a row of big rooms (conference rooms, cafeterias, empty lobbies).</li>
 *   <li>{@link #LOBBY} (14%): an elevator lobby through the middle (terrazzo, lit coffers, a
 *       directory board, fountains), big rooms on both sides.</li>
 *   <li>{@link #CAFETERIA} (16%): a lobby that runs straight into a cafeteria: a terrazzo lobby with
 *       columns and lit coffers, then rows of identical tables under pendant lamps, and a serving
 *       counter with vending machines, coolers and fountains along the far wall.</li>
 * </ul>
 *
 * <p>Inside its border wall every zone has a 3-wide "ring" aisle that is always open floor; the
 * zone's content (its "core") only fills the middle. So whatever the core is, you can always walk
 * around it, and every room, corridor and aisle of the core opens onto the ring.
 *
 * <h2>Zone borders</h2>
 * The line between two zones (x or z a multiple of {@link #CELL}) is one of: OPEN (no wall at all,
 * the floors run together; only between open-plan, cubicle and cafeteria zones), DOORS (a drywall
 * partition with two or three framed openings/double doors), or WINDOW (a ribbon of windows on one
 * side, no way through). A window run is 15 bays of 3 panes (mullions only between bays); most bays
 * are blacked out (blinds down, half down, or taped over), a few just glow, and a perimeter casing
 * runs along their foot (a slim radiator under every bay, a socket every third bay) with a slot
 * diffuser above it. Every zone keeps its west OR north border passable (never a window), so
 * following those borders always leads on: no zone is ever sealed off.
 *
 * <h2>Ceiling</h2>
 * Light panels hang on a regular 4-block lattice, most of them dead (whole dark patches); square
 * diffusers, sprinklers, smoke detectors, return grilles and the odd speaker sit on a sparser 8-block
 * grid between them; corridors have access hatches. Lobbies have big lit coffers, cafeteria tables
 * pendant lamps, conference rooms a projector mount. Tiles are only missing in a few water-damaged
 * patches (stained tiles, holes, a lifted tile, a dangling cable, sometimes an open hatch).
 *
 * <h2>Packed columns</h2>
 * {@link #column} returns a {@code long} with several fields (read them with the getters below):
 * kind, facing, a variant, the floor, the ceiling, the wall style, an optional wall decoration, an
 * optional item on a table and an optional flat floor detail (floor box, threshold strip, stain).
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
    /** The core is local CORE_LO..CORE_HI (inclusive): 41 x 41 blocks. (The anomalies plan uses it too.) */
    static final int CORE_LO = RING + 1;
    static final int CORE_HI = CELL - RING - 1;
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
    static final int CAFETERIA = 4;

    // Border types.
    private static final int B_OPEN = 0;
    private static final int B_DOORS = 1;
    private static final int B_WINDOW = 2;

    // ==========================================================================================
    // Column kinds (what stands in the column between floor and ceiling)
    // ==========================================================================================

    /** Open floor you can walk on (it may still have a ceiling light or a wall decoration). */
    static final int FLOOR = 0;
    /**
     * A drywall wall from floor to ceiling ({@link #style} says how its bottom block looks; the
     * variant says whether it is the frame of an opening beside it, see {@link #JAMB_LEFT}).
     */
    static final int WALL = 1;
    /** Solid filling of a locked closet or an elevator shaft: never seen, never entered. */
    static final int FILL = 2;
    /** A square white column (with a dark base). */
    static final int COLUMN = 3;
    /** A doorway without a door: 3 blocks tall, a frame head above (variant: {@link #FRAME_AXIS_Z}). */
    static final int DOORWAY = 4;
    /** A door in a wall (variant: door type, open, hinge, locked). Facing = the way the door faces. */
    static final int DOOR = 5;
    /**
     * Sill, two blocks of window glass and a head. Facing = the side the glass looks into; variant =
     * which pane of its 3-pane bay this is (bits 0-1, {@link #PANE_LEFT}...) and how the bay is
     * covered (bits 2-3, {@link #COVER_NONE}...).
     */
    static final int WINDOW = 6;
    /**
     * The low perimeter casing in front of a window run (variant: {@link #PART_TRUNK} and friends).
     * Facing = toward the room, away from the window.
     */
    static final int PERIMETER = 7;
    /** One block-column of an elevator door set into a wall (variant: right half). Facing = out. */
    static final int ELEVATOR = 8;
    // Furniture. Facing = the way its front looks.
    static final int DESK = 9;
    static final int DESK_PC = 10;
    static final int CHAIR = 11;
    static final int CABINET = 12;
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
    /** Water cooler (variant = cups of Almond Water left). */
    static final int COOLER = 22;
    /** Kitchen counter (variant 1 = with a microwave on top). */
    static final int COUNTER = 23;
    static final int FRIDGE = 24;
    /** One block of the conference table, the same everywhere (conference rooms and cafeterias). */
    static final int CONF_TABLE = 26;
    static final int SOFA = 27;
    /** Server rack, two blocks tall. */
    static final int SERVER_RACK = 28;
    /** Heavy red stage curtain hanging floor to ceiling (the "curtain room"). */
    static final int CURTAIN = 29;
    /** Cubicle partition (variant = which of the 4 sides it connects to, bit = 2D data value). */
    static final int PARTITION = 30;
    /** Wall-mounted drinking fountain of Almond Water (variant = drinks left). Back against the wall. */
    static final int FOUNTAIN = 31;

    // Window panes (bits 0-1 of a WINDOW's variant), in the same order as OfficeWindowBlock.Pane.
    // LEFT is the pane at the bay's left end as seen from the room, looking at the glass.
    static final int PANE_SINGLE = 0;
    static final int PANE_LEFT = 1;
    static final int PANE_MID = 2;
    static final int PANE_RIGHT = 3;
    // Window bay covers (bits 2-3 of a WINDOW's variant), as OfficeWindowBlock.Cover.
    static final int COVER_NONE = 0;
    /** Blinds down over the upper pane only. */
    static final int COVER_HALF = 1;
    static final int COVER_BLINDS = 2;
    static final int COVER_TAPED = 3;

    // Perimeter parts (variant of PERIMETER), in the same order as OfficePerimeterBlock.Part.
    static final int PART_TRUNK = 0;
    static final int PART_RADIATOR = 1;
    static final int PART_SOCKET = 2;
    static final int PART_END = 3;

    // Door frames. A WALL right beside an opening is its jamb (variant bits 0-1: LEFT = the wall on
    // the lower-coordinate side of the opening, RIGHT = the higher side; bit 2: the opening is a
    // 3-tall doorway, not a 2-tall door). Bit 3 (also used by DOORWAY) = the wall runs along Z.
    static final int JAMB_LEFT = 1;
    static final int JAMB_RIGHT = 2;
    static final int JAMB_TALL = 4;
    static final int FRAME_AXIS_Z = 8;

    // Door types (variant bits 0-1).
    static final int DOOR_OFFICE = 0;
    /** The office door with a brass "6" on it: always locked, faint music behind it. */
    static final int DOOR_SIX = 1;
    static final int DOOR_GLASS = 2;
    // Door variant flags.
    static final int DOOR_OPEN = 4;
    static final int DOOR_HINGE_RIGHT = 8;
    static final int DOOR_LOCKED = 16;

    // Floors: a material (bits 0-2) and an indent flag.
    static final int CARPET_GREIGE = 0;
    static final int CARPET_BLUE = 1;
    static final int CONCRETE = 2;
    /** Cafeteria vinyl. */
    static final int VINYL = 3;
    /** Lobby terrazzo. */
    static final int TERRAZZO = 4;
    /** Entrance matting at the ends of a lobby hall. */
    static final int ENTRANCE_MAT = 5;
    /** Added to a carpet: the darker, unfaded patch where furniture stood for years. */
    static final int INDENT = 8;

    // Flat floor details lying on the floor (in the cell above it), as OfficeFloorDetailBlock.Kind.
    static final int DETAIL_NONE = 0;
    /** A flush power/data box in the carpet where desks once stood. */
    static final int DETAIL_FLOOR_BOX = 1;
    /** An aluminium strip where one floor meets another (along the detail's facing edge). */
    static final int DETAIL_THRESHOLD = 2;
    /** A rare old stain on the carpet. */
    static final int DETAIL_STAIN = 3;

    // Ceilings.
    static final int TILE = 0;
    static final int TILE_MISSING = 1;
    static final int HATCH = 2;
    static final int PANEL_LIT = 3;
    static final int PANEL_DEAD = 4;
    static final int PANEL_FLICKER = 5;
    /** Square 4-way air diffuser. */
    static final int DIFFUSER = 6;
    /** Linear slot diffuser over a window run; the ceiling facing says which way the slots run. */
    static final int SLOT = 7;
    /** Return-air grille. */
    static final int RETURN_GRILLE = 8;
    static final int SPRINKLER = 9;
    static final int SMOKE = 10;
    static final int SPEAKER = 11;
    /** A tile with a brown water stain (around the holes of a leak). */
    static final int STAINED = 12;
    /** Projector mount over a conference table. */
    static final int PROJECTOR = 13;
    /** A missing tile with a cable stub dangling out of it (leaks). */
    static final int CABLE = 14;
    /** A tile pushed up out of its grid, a dark gap round it (leaks). */
    static final int LIFTED = 15;
    /** Big lit ceiling coffers of a lobby (laid in 3 x 3 groups). */
    static final int COFFER_LIT = 16;
    static final int COFFER_FLICKER = 17;
    static final int COFFER_DEAD = 18;

    // Wall styles (only used by WALL; says what the bottom block looks like, or glass).
    static final int STYLE_BASE = 0;
    static final int STYLE_RAIL = 1;
    static final int STYLE_GLASS = 2;
    static final int STYLE_BLINDS = 3;
    /** An office front: skirting at the bottom, glass with blinds above it, drywall over that. */
    static final int STYLE_GLAZED = 4;
    /** A cafeteria's servery wall: skirting, a shuttered serving hatch above (facing = its front). */
    static final int STYLE_HATCH = 5;

    // Wall decorations hung in the air in front of a wall (each at the height its model is made for).
    static final int DECO_NONE = 0;
    static final int DECO_WHITEBOARD = 1;
    static final int DECO_ELEVATOR_PANEL = 2;
    static final int DECO_CCTV = 3;
    /** The lobby's directory board (OFFICE_PLAQUE). */
    static final int DECO_DIRECTORY = 4;
    /** Green exit sign on a wall (deco variant: arrow, and whether it hangs one block higher). */
    static final int DECO_EXIT = 5;
    static final int DECO_EXTINGUISHER = 6;
    static final int DECO_SWITCH = 7;
    static final int DECO_THERMOSTAT = 8;
    static final int DECO_CALL_POINT = 9;
    static final int DECO_SOCKET = 10;
    static final int DECO_NOTICE = 11;
    static final int DECO_CLOCK = 12;
    /** Pale unfaded mark where something hung (deco variant: MARK_PICTURE...). */
    static final int DECO_MARK = 13;
    /** Dado cable trunking along a wall (deco variant 1 = with outlets). */
    static final int DECO_DADO = 14;
    static final int DECO_DOOR_STOP = 15;
    static final int DECO_TV_BRACKET = 16;
    static final int DECO_COAT_HOOKS = 17;
    static final int DECO_SANITISER = 18;
    static final int DECO_FIRST_AID = 19;
    /** "You are here" fire evacuation plan. */
    static final int DECO_EVAC_PLAN = 20;
    static final int DECO_NO_SMOKING = 21;
    static final int DECO_FIRE_DOOR_SIGN = 22;
    /** Room sign beside a conference room door. */
    static final int DECO_SIGN_MEETING = 23;
    /** Room sign beside any other room's door. */
    static final int DECO_SIGN_OFFICE = 24;
    /** Double-sided exit sign hanging from the ceiling in a big space (deco variant: arrow). */
    static final int DECO_HANGING_EXIT = 25;
    /** Pendant lamp hanging under the ceiling (deco variant: LAMP_LIT / LAMP_FLICKER / LAMP_DEAD). */
    static final int DECO_PENDANT = 26;

    // Exit sign variants: the arrow in bits 0-1 (left/right as seen by someone reading the sign) ...
    static final int ARROW_NONE = 0;
    static final int ARROW_LEFT = 1;
    static final int ARROW_RIGHT = 2;
    /** ... and this bit when a wall sign hangs over a 3-tall doorway (one block higher than over a door). */
    static final int EXIT_HIGH = 4;
    // Wall mark variants, as OfficeWallMarkBlock.Kind.
    static final int MARK_PICTURE = 0;
    static final int MARK_BOARD_LEFT = 1;
    static final int MARK_BOARD_RIGHT = 2;
    static final int MARK_HOLES = 3;
    // Lamp states of a pendant (deco variant).
    static final int LAMP_LIT = 0;
    static final int LAMP_FLICKER = 1;
    static final int LAMP_DEAD = 2;

    // Things lying on a table or desk (in the block above it), as OfficeTableItemsBlock.Kind + 1.
    static final int TOP_NONE = 0;
    static final int TOP_PAPERS = 1;
    static final int TOP_PENCILS = 2;
    static final int TOP_MUG = 3;
    static final int TOP_PAPERS_MUG = 4;
    /** A bottle of Almond Water (right-click to take). */
    static final int TOP_BOTTLE = 5;
    /** Pencils, notebooks, post-its, a mug of Almond Water and the same little desk sign in every cubicle. */
    static final int TOP_CUBICLE_SET = 6;
    /** Extremely rare: a double cheeseburger on a tray (right-click to take). */
    static final int TOP_BURGER = 7;

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

    private static final int KIND_SHIFT = 0;           // 8 bits
    private static final int FACING_SHIFT = 8;         // 2 bits
    private static final int VARIANT_SHIFT = 10;       // 8 bits
    private static final int FLOOR_SHIFT = 18;         // 4 bits
    private static final int CEIL_SHIFT = 22;          // 5 bits
    private static final int CEIL_FACING_SHIFT = 27;   // 2 bits
    private static final int STYLE_SHIFT = 29;         // 3 bits
    private static final int DECO_SHIFT = 32;          // 6 bits
    private static final int DECO_FACING_SHIFT = 38;   // 2 bits
    private static final int DECO_VARIANT_SHIFT = 40;  // 3 bits
    private static final int TOP_SHIFT = 43;           // 3 bits
    private static final int DETAIL_SHIFT = 46;        // 2 bits
    private static final int DETAIL_FACING_SHIFT = 48; // 2 bits

    static int kind(long c) { return (int) (c >>> KIND_SHIFT) & 0xFF; }
    static int facing(long c) { return (int) (c >>> FACING_SHIFT) & 3; }
    static int variant(long c) { return (int) (c >>> VARIANT_SHIFT) & 0xFF; }
    static int floor(long c) { return (int) (c >>> FLOOR_SHIFT) & 0xF; }
    /** The floor's material without the indent flag. */
    static int floorMaterial(long c) { return floor(c) & 7; }
    static int ceiling(long c) { return (int) (c >>> CEIL_SHIFT) & 0x1F; }
    static int ceilingFacing(long c) { return (int) (c >>> CEIL_FACING_SHIFT) & 3; }
    static int style(long c) { return (int) (c >>> STYLE_SHIFT) & 7; }
    static int deco(long c) { return (int) (c >>> DECO_SHIFT) & 0x3F; }
    static int decoFacing(long c) { return (int) (c >>> DECO_FACING_SHIFT) & 3; }
    static int decoVariant(long c) { return (int) (c >>> DECO_VARIANT_SHIFT) & 7; }
    static int top(long c) { return (int) (c >>> TOP_SHIFT) & 7; }
    static int detail(long c) { return (int) (c >>> DETAIL_SHIFT) & 3; }
    static int detailFacing(long c) { return (int) (c >>> DETAIL_FACING_SHIFT) & 3; }

    private static long pack(int kind, int facing, int variant) {
        return ((long) kind << KIND_SHIFT) | ((long) (facing & 3) << FACING_SHIFT) | ((long) (variant & 0xFF) << VARIANT_SHIFT);
    }

    private static long withFloor(long c, int floor) {
        return (c & ~(0xFL << FLOOR_SHIFT)) | ((long) floor << FLOOR_SHIFT);
    }

    private static long withCeiling(long c, int ceiling, int facing) {
        c &= ~((0x1FL << CEIL_SHIFT) | (3L << CEIL_FACING_SHIFT));
        return c | ((long) ceiling << CEIL_SHIFT) | ((long) (facing & 3) << CEIL_FACING_SHIFT);
    }

    private static long withStyle(long c, int style) {
        return (c & ~(7L << STYLE_SHIFT)) | ((long) style << STYLE_SHIFT);
    }

    private static long withDeco(long c, int deco, int facing, int variant) {
        c &= ~((0x3FL << DECO_SHIFT) | (3L << DECO_FACING_SHIFT) | (7L << DECO_VARIANT_SHIFT));
        return c | ((long) deco << DECO_SHIFT) | ((long) (facing & 3) << DECO_FACING_SHIFT) | ((long) (variant & 7) << DECO_VARIANT_SHIFT);
    }

    private static long withDeco(long c, int deco, int facing) {
        return withDeco(c, deco, facing, 0);
    }

    private static long withTop(long c, int top) {
        return (c & ~(7L << TOP_SHIFT)) | ((long) top << TOP_SHIFT);
    }

    private static long withDetail(long c, int detail, int facing) {
        c &= ~((3L << DETAIL_SHIFT) | (3L << DETAIL_FACING_SHIFT));
        return c | ((long) detail << DETAIL_SHIFT) | ((long) (facing & 3) << DETAIL_FACING_SHIFT);
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

    static int counterClockwise(int dir) {
        return (dir + 3) & 3;
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
        if (r < 0.28) return OPEN;
        if (r < 0.34) return CUBICLES;
        if (r < 0.70) return OFFICES;
        if (r < 0.84) return LOBBY;
        return CAFETERIA;
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

    /** Rows of the zone run along X (false) or along Z (true). (The anomalies plan uses it too.) */
    static boolean alongZ(long seed, int cx, int cz) {
        return Hash.unit(seed, cx, cz, 7003) < 0.5;
    }

    /** Carpet colour of a zone: canon's cheap grey, or (20%) a muted slate blue. */
    private static int zoneCarpet(long seed, int cx, int cz) {
        return Hash.unit(seed, cx, cz, 7004) < 0.2 ? CARPET_BLUE : CARPET_GREIGE;
    }

    /**
     * Open zones: 0 = plain open floor, 1 = with a row of small rooms along the far side.
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

    /** Zones whose floors may run together with no wall between them. */
    private static boolean openKind(int kind) {
        return kind == OPEN || kind == CUBICLES || kind == CAFETERIA;
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
     * True when the west ({@code west}) or north border of zone (cx, cz) is a run of windows that
     * look INTO this zone. Both lines belong to the zone itself (local coordinate 0), so the
     * window and the floor in front of it are in the same zone. Used to place the Window anomaly.
     */
    static boolean windowsInto(long seed, int cx, int cz, boolean west) {
        int b = border(seed, cx, cz, west);
        return borderType(b) == B_WINDOW && windowsFaceHigh(b);
    }

    /**
     * The opening of a DOORS border that position {@code along} (1..47) lies in, or -1 for wall.
     * Two or three openings: one near each end, sometimes one in the middle; each is a 3-wide
     * doorway or a pair of double doors. Packed: index k in bits 16+, first position in bits 8-15,
     * width in bits 1-7, bit 0 set for double doors.
     */
    private static int borderOpening(long seed, int cx, int cz, boolean vertical, int along) {
        long h = Hash.of(seed, cx, cz, vertical ? 7020 : 7021);
        for (int k = 0; k < 3; k++) {
            long hk = Hash.mix(h + k * 0x9E3779B97F4A7C15L);
            if (k == 2 && Hash.unit(hk) < 0.5) {
                break;
            }
            int start = k == 0 ? 6 + Hash.range(hk, 10) : k == 1 ? 31 + Hash.range(hk, 10) : 21 + Hash.range(hk, 4);
            boolean doors = Hash.unit(Hash.mix(hk ^ 1)) < 0.5;
            int width = doors ? 2 : 3;
            if (along >= start && along < start + width) {
                return k << 16 | start << 8 | width << 1 | (doors ? 1 : 0);
            }
        }
        return -1;
    }

    /**
     * One column of a DOORS border at position {@code along} (1..47 along the border). Returns
     * WALL (with its door-frame jamb when an opening is right beside it), DOORWAY, or DOOR packed
     * with its variant (hinge/open) and the door facing.
     *
     * @param plusDir  direction of increasing {@code along}
     * @param crossDir direction across the border (into the higher zone)
     */
    private static long doorsBorderColumn(long seed, int cx, int cz, boolean vertical, int along, int plusDir, int crossDir) {
        int axis = vertical ? FRAME_AXIS_Z : 0;
        int opening = borderOpening(seed, cx, cz, vertical, along);
        if (opening < 0) {
            int jamb = 0;
            int next = borderOpening(seed, cx, cz, vertical, along + 1);
            int prev = borderOpening(seed, cx, cz, vertical, along - 1);
            if (next >= 0) {
                jamb = JAMB_LEFT | ((next & 1) == 0 ? JAMB_TALL : 0);
            } else if (prev >= 0) {
                jamb = JAMB_RIGHT | ((prev & 1) == 0 ? JAMB_TALL : 0);
            }
            return withStyle(pack(WALL, 0, jamb == 0 ? 0 : jamb | axis), STYLE_BASE);
        }
        if ((opening & 1) == 0) {
            long c = pack(DOORWAY, 0, axis);
            // A threshold strip where one zone's carpet meets the other's.
            if (zoneCarpet(seed, cx, cz) != (vertical ? zoneCarpet(seed, cx - 1, cz) : zoneCarpet(seed, cx, cz - 1))) {
                c = withDetail(c, DETAIL_THRESHOLD, opposite(crossDir));
            }
            return c;
        }
        // Double wooden doors meeting in the middle, open or shut.
        long h = Hash.of(seed, cx, cz, vertical ? 7020 : 7021);
        long hk = Hash.mix(h + (opening >>> 16) * 0x9E3779B97F4A7C15L);
        boolean open = Hash.unit(Hash.mix(hk ^ 2)) < 0.55;
        boolean first = along == ((opening >>> 8) & 0xFF);
        int facing = crossDir;
        // The door whose partner is on its clockwise side hinges LEFT (see DoorBlock#getHinge).
        boolean partnerClockwise = first == (clockwise(facing) == plusDir);
        int variant = DOOR_OFFICE | (open ? DOOR_OPEN : 0) | (partnerClockwise ? 0 : DOOR_HINGE_RIGHT);
        return pack(DOOR, facing, variant);
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
                c = stain(seed, x, z, c);
            }
            return withFloor(c, carpet);
        }
        c = perimeter(seed, cx, cz, lx, lz);
        if (c >= 0) {
            return withFloor(c, carpet); // it brings its own slot diffuser overhead
        }
        boolean z_ = alongZ(seed, cx, cz);
        int u = z_ ? lz : lx;
        int v = z_ ? lx : lz;
        int plusU = z_ ? SOUTH : EAST;
        int plusV = z_ ? EAST : SOUTH;
        boolean ring = u < CORE_LO || u > CORE_HI || v < CORE_LO || v > CORE_HI;
        if (ring) {
            c = withFloor(pack(FLOOR, 0, 0), carpet);
            c = withCeiling(c, latticeCeiling(seed, x, z, cx, cz, true), latticeFacing(seed, cx, cz, x, z));
            return stain(seed, x, z, ringDetail(seed, x, z, cx, cz, lx, lz, c));
        }
        Frame f = new Frame(seed, cx, cz, u, v, plusU, plusV, carpet, x, z);
        return switch (zoneKind(seed, cx, cz)) {
            case OPEN -> openCore(f);
            case CUBICLES -> cubicleCore(f);
            case OFFICES -> officeCore(f);
            case LOBBY -> lobbyCore(f);
            default -> cafeteriaCore(f);
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

        /** FRAME_AXIS_Z when walls running along u run along world Z. */
        int axisU() {
            return plusU == SOUTH ? FRAME_AXIS_Z : 0;
        }
    }

    /** A rare old stain on a plain carpet floor (about 1 column in 400). */
    private static long stain(long seed, int x, int z, long c) {
        if (kind(c) == FLOOR && deco(c) == DECO_NONE && detail(c) == DETAIL_NONE && Hash.unit(seed, x, z, 7031) < 0.0025) {
            return withDetail(c, DETAIL_STAIN, Hash.range(Hash.of(seed, x, z, 7032), 4));
        }
        return c;
    }

    // ------------------------------------------------------------------------------------------
    // Border columns and window runs
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
                int cover = bayCover(seed, cx, cz, vertical, (along - 2) / 3);
                return pack(WINDOW, facing, windowPane(along, facing, plusDir) | cover << 2);
            }
            default:
                return doorsBorderColumn(seed, cx, cz, vertical, along, plusDir, crossDir);
        }
    }

    /**
     * How bay {@code bay} (0..14) of a window run is blacked out, the same for its three panes.
     * Canon: most windows are blacked out, yet still glow. Blinds right down 45%, taped over 30%,
     * blinds half down 10%, and only 15% bare frosted glass.
     */
    private static int bayCover(long seed, int cx, int cz, boolean vertical, int bay) {
        double r = Hash.unit(seed, cx * 32 + bay, cz * 2 + (vertical ? 1 : 0), 7015);
        if (r < 0.45) return COVER_BLINDS;
        if (r < 0.75) return COVER_TAPED;
        if (r < 0.85) return COVER_HALF;
        return COVER_NONE;
    }

    /**
     * Which pane of its bay a window at {@code along} (2..46) is. The run is exactly 15 bays of 3
     * panes, so a mullion only stands between two bays, like real ribbon windows (not on every
     * block), and both ends of the run finish on a bay edge. LEFT is the bay end on your left when
     * you stand in the room looking at the glass: world direction clockwise of the window's facing.
     */
    private static int windowPane(int along, int facing, int plusDir) {
        int pos = (along - 2) % 3;
        if (pos == 1) {
            return PANE_MID;
        }
        boolean lowEndIsLeft = clockwise(facing) == opposite(plusDir);
        return (pos == 0) == lowEndIsLeft ? PANE_LEFT : PANE_RIGHT;
    }

    /**
     * The perimeter casing stands in the first column in front of a window run, all along it
     * (positions 2..46, the same as the windows, so the ring corners stay free). Returns the packed
     * column, or -1.
     */
    private static long perimeter(long seed, int cx, int cz, int lx, int lz) {
        if (lx == 1 || lx == CELL - 1) {
            // Next to the vertical border at the west (lx == 1) or east (lx == 47) of this zone.
            int b = border(seed, lx == 1 ? cx : cx + 1, cz, true);
            boolean facesThisSide = lx == 1 ? windowsFaceHigh(b) : !windowsFaceHigh(b);
            if (borderType(b) == B_WINDOW && facesThisSide && lz >= 2 && lz <= CELL - 2) {
                return perimeterPart(lz, lx == 1 ? EAST : WEST, SOUTH);
            }
        }
        if (lz == 1 || lz == CELL - 1) {
            int b = border(seed, cx, lz == 1 ? cz : cz + 1, false);
            boolean facesThisSide = lz == 1 ? windowsFaceHigh(b) : !windowsFaceHigh(b);
            if (borderType(b) == B_WINDOW && facesThisSide && lx >= 2 && lx <= CELL - 2) {
                return perimeterPart(lx, lz == 1 ? SOUTH : NORTH, EAST);
            }
        }
        return -1;
    }

    /**
     * One column of a perimeter run at {@code along} (2..46): a cap at both ends, a slim radiator
     * under the middle pane of every bay, a double socket under the first pane of every third bay,
     * trunking everywhere else. Overhead runs a slot diffuser (its slots along {@code runDir}).
     */
    private static long perimeterPart(int along, int facing, int runDir) {
        int bay = (along - 2) / 3;
        int pos = (along - 2) % 3;
        int part;
        if (along == 2 || along == CELL - 2) {
            part = PART_END;
        } else if (pos == 1) {
            part = PART_RADIATOR;
        } else if (pos == 0 && bay % 3 == 1) {
            part = PART_SOCKET;
        } else {
            part = PART_TRUNK;
        }
        return withCeiling(pack(PERIMETER, facing, part), SLOT, runDir);
    }

    /** Ring aisle details: a dead plant now and then in a corner, things on the border walls. */
    private static long ringDetail(long seed, int x, int z, int cx, int cz, int lx, int lz, long c) {
        boolean westOrEast = lx == 1 || lx == CELL - 1;
        boolean northOrSouth = lz == 1 || lz == CELL - 1;
        if (westOrEast && northOrSouth) {
            if (Hash.unit(seed, x, z, 7030) < 0.35) {
                return furniture(c, PLANT, lx == 1 ? EAST : WEST, 0);
            }
            return c;
        }
        if (westOrEast) {
            return ringWall(seed, x, z, lx == 1 ? cx : cx + 1, cz, true, lz, lx == 1 ? EAST : WEST, c);
        }
        if (northOrSouth) {
            return ringWall(seed, x, z, cx, lz == 1 ? cz : cz + 1, false, lx, lz == 1 ? SOUTH : NORTH, c);
        }
        return c;
    }

    /**
     * Details on a DOORS border wall, for the ring column in front of it at {@code along}: a green
     * exit sign over each opening that leads toward the nearest exit stairwell (on the side you come
     * from), an extinguisher with a fire-alarm call point beside it at along 12/13 and 36/37 (about
     * every 24 blocks), and an Almond Water fountain in the middle (along 24), wherever the wall
     * there is solid.
     *
     * @param bcx    zone owning the border line (its local coordinate 0)
     * @param inward the way from the wall into this ring aisle (what hangs on the wall faces this way)
     */
    private static long ringWall(long seed, int x, int z, int bcx, int bcz, boolean vertical, int along, int inward, long c) {
        if (borderType(border(seed, bcx, bcz, vertical)) != B_DOORS) {
            return c;
        }
        int opening = borderOpening(seed, bcx, bcz, vertical, along);
        if (opening >= 0) {
            boolean doors = (opening & 1) != 0;
            int start = (opening >>> 8) & 0xFF;
            if (along != (doors ? start : start + 1)) {
                return c; // one sign per opening: over the first door, or the middle of a doorway
            }
            // Only on the side facing away from the exit: the sign says "go through here".
            int[] exit = EXITS.nearest(seed, x, z);
            int line = vertical ? bcx * CELL : bcz * CELL;
            int toExit = (vertical ? exit[0] : exit[1]) - line;
            int ourSide = vertical ? DX[inward] : DZ[inward];
            if (toExit * ourSide >= 0) {
                return c;
            }
            return withDeco(c, DECO_EXIT, inward, ARROW_NONE | (doors ? 0 : EXIT_HIGH));
        }
        if ((along == 12 || along == 36) && borderOpening(seed, bcx, bcz, vertical, along + 1) < 0) {
            return withDeco(c, DECO_EXTINGUISHER, inward);
        }
        if ((along == 13 || along == 37) && borderOpening(seed, bcx, bcz, vertical, along - 1) < 0) {
            return withDeco(c, DECO_CALL_POINT, inward);
        }
        if (along == 24) {
            return furniture(c, FOUNTAIN, inward, 3);
        }
        return c;
    }

    // ------------------------------------------------------------------------------------------
    // The ceiling: lights on the regular grid, fixtures between them, water damage
    // ------------------------------------------------------------------------------------------

    /**
     * The 1x2 light panels hang on a 4-block grid. A panel's two blocks lie along the zone's rows:
     * along X at x%4 in {1, 2}, z%4 == 2 (or the same turned for zones along Z). That grid never
     * touches a border wall or a column, and both halves of a panel always lie in the same zone.
     * Between the panels: {@link #gridFixture}.
     *
     * @param open true for open-plan areas (rings, open floors): about 80% of panels are dark there,
     *             in whole dark patches (canon: most of the lights don't work)
     */
    private static int latticeCeiling(long seed, int x, int z, int cx, int cz, boolean open) {
        boolean z_ = alongZ(seed, cx, cz);
        int along = z_ ? z : x;
        int across = z_ ? x : z;
        int a = Math.floorMod(along, 4);
        boolean panel = Math.floorMod(across, 4) == 2 && (a == 1 || a == 2);
        // The first pair beside a window wall would be cut in half by the slot diffuser over the
        // perimeter run there: leave that pair out.
        if (panel && Math.floorMod(along, CELL) <= 2 && windowsInto(seed, cx, cz, !z_)) {
            panel = false;
        }
        if (!panel) {
            int damage = damage(seed, x, z);
            return damage >= 0 ? damage : gridFixture(along, across);
        }
        int facing = latticeFacing(seed, cx, cz, x, z);
        if (damage(seed, x, z) >= 0 || damage(seed, x + DX[facing], z + DZ[facing]) >= 0) {
            return PANEL_DEAD; // the leak shorted it (both halves check both blocks, so they agree)
        }
        // Light comes in islands: 12x12 patches are lit or dark as a whole, a few panels dead.
        int px = Math.floorDiv(x, 12);
        int pz = Math.floorDiv(z, 12);
        double share = open ? 0.12 + 0.18 * Hash.unit(seed, cx, cz, 7040) : 0.45; // open: 12-30% of patches lit
        if (Hash.unit(seed, px, pz, 7041) >= share) {
            return PANEL_DEAD;
        }
        int ax = a == 1 ? 0 : -1; // key the panel by its first block so both halves agree
        int kx = z_ ? x : x + ax;
        int kz = z_ ? z + ax : z;
        double roll = Hash.unit(seed, kx, kz, 7042);
        if (roll < 0.10) return PANEL_DEAD;
        if (roll < 0.125) return PANEL_FLICKER;
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

    /**
     * The fixtures between the lattice lights, on an 8-block grid ({@code along}/{@code across} =
     * world coordinates along/across the zone's rows; lights are at along%4 1-2, across%4 2, so
     * these never meet one): a square diffuser and a sprinkler on every across%8 0 row, a smoke
     * detector or a return grille (alternating) and now and then a speaker on the rows halfway.
     */
    private static int gridFixture(int along, int across) {
        int a8 = Math.floorMod(along, 8);
        int b8 = Math.floorMod(across, 8);
        int cell = Math.floorDiv(along, 8) + Math.floorDiv(across, 8);
        if (b8 == 0) {
            if (a8 == 0) return DIFFUSER;
            if (a8 == 4) return SPRINKLER;
        } else if (b8 == 4) {
            if (a8 == 0) return Math.floorMod(cell, 2) == 0 ? SMOKE : RETURN_GRILLE;
            if (a8 == 4 && Math.floorMod(cell, 3) == 0) return SPEAKER;
        }
        return TILE;
    }

    /**
     * Water damage: about one 16 x 16 square in 20 has a leak somewhere in it. Around the leak a
     * small cluster of tiles has fallen out or been pushed up, a cable stub dangles from one hole,
     * the tiles around those are stained brown, and sometimes the access hatch in the middle stands
     * open where someone went looking for the leak. Returns the ceiling there, or -1 when (x, z) is
     * not in a patch.
     */
    private static int damage(long seed, int x, int z) {
        int sx = Math.floorDiv(x, 16);
        int sz = Math.floorDiv(z, 16);
        long h = Hash.of(seed, sx, sz, 7047);
        if (Hash.unit(h) >= 0.05) {
            return -1;
        }
        int dx = x - (sx * 16 + 4 + Hash.range(Hash.mix(h ^ 1), 8));
        int dz = z - (sz * 16 + 4 + Hash.range(Hash.mix(h ^ 2), 8));
        int d2 = dx * dx + dz * dz;
        int r2 = 2 + Hash.range(Hash.mix(h ^ 3), 4); // 2..5: a hole of 5 to 13 tiles
        if (d2 > r2 + 4) {
            return -1;
        }
        double roll = Hash.unit(seed, x, z, 7048);
        if (d2 > r2) {
            return roll < 0.55 ? STAINED : -1; // the stained fringe
        }
        if (d2 == 0) {
            return Hash.unit(Hash.mix(h ^ 4)) < 0.4 ? HATCH : CABLE;
        }
        if (roll < 0.55) return TILE_MISSING;
        if (roll < 0.70) return LIFTED;
        return STAINED;
    }

    /**
     * Ceiling of a corridor: a panel pair every 6 blocks along its centre line (1 = centre), a
     * sprinkler or smoke detector and a diffuser or grille between the pairs, and an access hatch
     * by the wall at u 12 and 36.
     */
    private static long corridorCeiling(Frame f, long c, int across, double litShare) {
        int a = Math.floorMod(f.u, 6);
        if (across != 1 || (a != 1 && a != 2)) {
            int damage = damage(f.seed, f.x, f.z);
            int fixture = TILE;
            if (across == 1 && a == 4) {
                fixture = (f.u / 6) % 2 == 0 ? SPRINKLER : SMOKE;
            } else if (across == 1 && a == 0) {
                fixture = (f.u / 6) % 2 == 0 ? DIFFUSER : RETURN_GRILLE;
            } else if (across == 0 && (f.u == 12 || f.u == 36)) {
                fixture = HATCH;
            }
            return withCeiling(c, damage >= 0 ? damage : fixture, 0);
        }
        int facing = a == 1 ? f.plusU : f.minusU();
        if (damage(f.seed, f.x, f.z) >= 0 || damage(f.seed, f.x + DX[facing], f.z + DZ[facing]) >= 0) {
            return withCeiling(c, PANEL_DEAD, facing);
        }
        int first = a == 1 ? f.u : f.u - 1;
        long h = Hash.of(f.seed, f.cx * 64 + first, f.cz * 64 + f.v, 7046);
        int state = Hash.unit(h) < litShare ? (Hash.unit(Hash.mix(h)) < 0.03 ? PANEL_FLICKER : PANEL_LIT) : PANEL_DEAD;
        return withCeiling(c, state, facing);
    }

    /**
     * A 3 x 3 group of lobby coffers covering zone coordinates u0..u0+2, c0..c0+2 (u0 % 4 == 0, so a
     * lattice panel pair is always wholly inside or wholly outside it). Lit as a whole: 60% lit, 5%
     * flickering, the rest dead. Returns the ceiling, or -1 outside the group.
     */
    private static int coffer(Frame f, int c, int u0, int c0) {
        if (f.u < u0 || f.u > u0 + 2 || c < c0 || c > c0 + 2) {
            return -1;
        }
        double r = Hash.unit(f.seed, f.cx * 64 + u0, f.cz * 64 + c0, 7049);
        return r < 0.60 ? COFFER_LIT : r < 0.65 ? COFFER_FLICKER : COFFER_DEAD;
    }

    /**
     * A double-sided exit sign hanging from the ceiling, turned to face people walking toward the
     * nearest exit stairwell (so its front greets them; no arrow: "straight on").
     */
    private static long hangingExit(long seed, int x, int z, long c) {
        int[] exit = EXITS.nearest(seed, x, z);
        int dx = exit[0] - x;
        int dz = exit[1] - z;
        int toward = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? EAST : WEST) : (dz >= 0 ? SOUTH : NORTH);
        return withDeco(c, DECO_HANGING_EXIT, opposite(toward), ARROW_NONE);
    }

    // ------------------------------------------------------------------------------------------
    // OPEN zones
    // ------------------------------------------------------------------------------------------

    private static long openCore(Frame f) {
        int c = f.v - CORE_LO;
        if (zoneVariant(f.seed, f.cx, f.cz) == 1 && c >= 34) {
            // A row of small rooms along the far side, doors facing the open floor.
            return roomRow(f, 34, 40, false, 9, false);
        }
        return openFloor(f, true);
    }

    /**
     * Open-plan floor: square columns every 8 blocks, empty carpet between them, an exit sign
     * hanging from the ceiling every 24 blocks each way.
     */
    private static long openFloor(Frame f, boolean islands) {
        long seed = f.seed;
        int x = f.x;
        int z = f.z;
        long base = withFloor(pack(FLOOR, 0, 0), f.carpet);
        if (Math.floorMod(x, 8) == 4 && Math.floorMod(z, 8) == 4) {
            return withFloor(pack(COLUMN, 0, 0), f.carpet);
        }
        long c = islands ? island(f, base) : base;
        if (kind(c) == FLOOR) {
            c = columnSide(f, c);
        }
        if (kind(c) == FLOOR && deco(c) == DECO_NONE && Math.floorMod(x, 24) == 0 && Math.floorMod(z, 24) == 0) {
            c = hangingExit(seed, x, z, c);
        }
        c = stain(seed, x, z, c);
        return withCeiling(c, latticeCeiling(seed, x, z, f.cx, f.cz, true), latticeFacing(seed, f.cx, f.cz, x, z));
    }

    /**
     * Things against the square columns. An extinguisher hangs on one side of every third column
     * each way (about every 24 blocks, like a real fire plan); a water cooler stands against one
     * side of the columns halfway between those (Level 4 is where Almond Water is found most); a
     * CCTV camera looks out from about one column in 25; and rarely (7% of the others) one side of
     * a column has a "moving day" pile: stacked boxes, a filing cabinet and another box or a supply
     * crate, pushed against it in a neat line, waiting for a removal van that never came.
     */
    private static long columnSide(Frame f, long c) {
        long seed = f.seed;
        for (int d = 0; d < 4; d++) {
            int p = clockwise(d);
            for (int k = -1; k <= 1; k++) {
                // Is there a column whose side d this cell is on (k = sideways along that side)?
                int colX = f.x - DX[d] - k * DX[p];
                int colZ = f.z - DZ[d] - k * DZ[p];
                if (Math.floorMod(colX, 8) != 4 || Math.floorMod(colZ, 8) != 4) {
                    continue;
                }
                if (k == 0 && extinguisherSide(seed, colX, colZ) == d) {
                    return withDeco(c, DECO_EXTINGUISHER, d);
                }
                if (k == 0 && coolerSide(seed, colX, colZ) == d && !besideRoomDoors(f, colX, colZ)) {
                    return furniture(c, COOLER, d, 3);
                }
                if (movingDaySide(f, colX, colZ) == d) {
                    if (k == 0) return furniture(c, CABINET, d, 0);
                    if (k == -1) return furniture(c, BOXES, d, 1);
                    double s = Hash.unit(seed, colX, colZ, 7053);
                    if (s < 0.35) return furniture(c, CRATE, d, 0);
                    if (s < 0.45) return furniture(c, LADDER, d, 0);
                    return furniture(c, BOXES, d, 0);
                }
            }
        }
        return cctv(seed, f.x, f.z, c);
    }

    /** The side of column (colX, colZ) with an extinguisher, or -1 (every third column each way). */
    private static int extinguisherSide(long seed, int colX, int colZ) {
        if (Math.floorMod(Math.floorDiv(colX - 4, 8), 3) != 0 || Math.floorMod(Math.floorDiv(colZ - 4, 8), 3) != 0) {
            return -1;
        }
        return Hash.range(Hash.of(seed, colX, colZ, 7051), 4);
    }

    /** The side of column (colX, colZ) with a water cooler, or -1 (the columns halfway between the extinguishers). */
    private static int coolerSide(long seed, int colX, int colZ) {
        if (Math.floorMod(Math.floorDiv(colX - 4, 8), 3) != 1 || Math.floorMod(Math.floorDiv(colZ - 4, 8), 3) != 1) {
            return -1;
        }
        return Hash.range(Hash.of(seed, colX, colZ, 7054), 4);
    }

    /**
     * The side of column (colX, colZ) with a moving-day pile, or -1. Never on a column with an
     * extinguisher or a cooler, and never in front of the doors of a room row ({@link #besideRoomDoors}).
     */
    private static int movingDaySide(Frame f, int colX, int colZ) {
        if (extinguisherSide(f.seed, colX, colZ) >= 0 || coolerSide(f.seed, colX, colZ) >= 0) {
            return -1;
        }
        long h = Hash.of(f.seed, colX, colZ, 7052);
        if (Hash.unit(h) >= 0.07 || besideRoomDoors(f, colX, colZ)) {
            return -1;
        }
        return Hash.range(Hash.mix(h), 4);
    }

    /**
     * True for the columns of the last column row (local v 36) of an open floor that has a row of
     * small rooms along its far side: their doors open onto local v 37, right beside these columns,
     * so nothing may stand against them (it could block a room's only door).
     */
    private static boolean besideRoomDoors(Frame f, int colX, int colZ) {
        int colV = f.plusV == EAST ? colX - f.cx * CELL : colZ - f.cz * CELL;
        return colV >= 36 && zoneKind(f.seed, f.cx, f.cz) == OPEN && zoneVariant(f.seed, f.cx, f.cz) == 1;
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
     * What is left in each 8x8 square between four columns (offsets 2..6 from the column lines, so
     * a free walkway always runs along the columns). Canon says these floors are nearly empty, so
     * mostly nothing is: every square keeps the flush floor box the desks were plugged into; half
     * the squares also show the dents of two facing rows of five desks that were carried out, some
     * of those still have one desk and its chair, or one office chair standing alone, very rarely a
     * forgotten backpack; the rest is bare carpet. Only squares fully inside a zone's core get anything.
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
        // ... and inside the open part of the zone (not the room row / cubicle rows).
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
        boolean flip = Hash.unit(Hash.mix(h ^ 1)) < 0.5; // which axis the desk rows line up with
        int a = flip ? oz : ox;   // along the rows
        int b = flip ? ox : oz;   // across them
        int plusB = flip ? EAST : SOUTH;
        boolean deskRow = (b == 3 || b == 4) && a >= 2 && a <= 6;
        if (r >= 0.50 && r < 0.58) {
            // One desk was never taken: it still stands in its row, its chair pushed back.
            int keepA = 2 + Hash.range(Hash.mix(h ^ 2), 5);
            int keepB = Hash.unit(Hash.mix(h ^ 3)) < 0.5 ? 3 : 4;
            if (a == keepA && b == keepB) {
                boolean pc = Hash.unit(Hash.mix(h ^ 4)) < 0.5;
                return furniture(base, pc ? DESK_PC : DESK, b == 3 ? opposite(plusB) : plusB, 0);
            }
            if (a == keepA && b == (keepB == 3 ? 2 : 5)) {
                return furniture(base, CHAIR, Hash.range(Hash.mix(h ^ 5), 4), 0);
            }
        } else if (r >= 0.58 && r < 0.63) {
            // A single office chair standing in the aisle beside the dents, turned away.
            if (a == 2 + Hash.range(Hash.mix(h ^ 6), 5) && b == 2) {
                return furniture(base, CHAIR, Hash.range(Hash.mix(h ^ 7), 4), 0);
            }
        } else if (r >= 0.63 && r < 0.645) {
            // Someone's backpack, left on the floor.
            if (a == 4 && b == 2) {
                return furniture(base, BACKPACK, Hash.range(Hash.mix(h ^ 8), 4), 0);
            }
        }
        long c = base;
        if (r < 0.645 && deskRow && Hash.unit(seed, f.x, f.z, 7061) < 0.85) {
            c = withFloor(c, f.carpet | INDENT);
        }
        if (a == 4 && b == 3) {
            c = withDetail(c, DETAIL_FLOOR_BOX, plusB); // between the two desk rows
        }
        return c;
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
        // Canon: every cubicle is complete and the same: a desk with the computer in the middle, a
        // chair, and on the left desk the same set of pencils, notebooks, post-its, a mug of Almond
        // Water and the little "keep your workspace tidy" sign.
        if (depth == 2) {
            long desk = furniture(base, iu == 2 ? DESK_PC : DESK, toFront, 0);
            return iu == 1 ? withTop(desk, TOP_CUBICLE_SET) : desk;
        }
        if (depth == 1 && iu == 2) {
            return furniture(base, CHAIR, opposite(toFront), 0); // looking at the desk
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
            base = corridorCeiling(f, base, c == 8 || c == 24 ? 1 : 0, 0.35);
            return stain(f.seed, f.x, f.z, corridorWall(f, base, c));
        }
        if (c <= 6) return roomRow(f, 0, 6, true, 1, false);
        if (c <= 16 && c >= 10 && c != 16) return roomRow(f, 10, 16, false, 2, false);
        if (c <= 22) return roomRow(f, 16, 22, true, 3, false);
        if (c <= 32 && c != 32) return roomRow(f, 26, 32, false, 4, false);
        return roomRow(f, 32, 40, true, 5, true);
    }

    /**
     * Details on the corridor walls, all on fixed spots so they read as planned. Beside every door a
     * room sign. On the wall of rows A and C (corridor rows c 7 and 23): an exit sign near each end
     * of the corridor, its arrow pointing along the corridor toward the nearest exit stairwell, a
     * first-aid box in the middle (u 24) and in half the corridors a notice board further on. On the
     * wall of rows B and D (c 9 and 25): an extinguisher with a call point beside it at u 14/15 and
     * 34/35 and an Almond Water fountain in the middle. Each only where the wall behind it is plain
     * drywall (not a door, glass or an office front).
     */
    private static long corridorWall(Frame f, long base, int c) {
        int rowId = switch (c) {
            case 7 -> 1;
            case 9 -> 2;
            case 23 -> 3;
            case 25 -> 4;
            default -> 0; // the middle of the corridor: nothing on the walls from here
        };
        if (rowId == 0) {
            return base;
        }
        int u = f.u;
        boolean lowWall = c == 7 || c == 23;
        int facing = lowWall ? f.plusV : f.minusV(); // away from the wall, into the corridor
        if (lowWall && (u <= 8 || u >= 40)) {
            // The first plain spot from each end: u 6, 7, 8 and 42, 41, 40.
            int spot = -1;
            int end = u <= 8 ? 6 : 42;
            int step = u <= 8 ? 1 : -1;
            for (int i = 0; i < 3 && spot < 0; i++) {
                if (rowWallPlain(f, rowId, end + i * step)) {
                    spot = end + i * step;
                }
            }
            if (u == spot) {
                int[] exit = EXITS.nearest(f.seed, f.x, f.z);
                int along = (exit[0] - f.x) * DX[f.plusU] + (exit[1] - f.z) * DZ[f.plusU];
                int toward = along >= 0 ? f.plusU : f.minusU();
                // Left as seen by someone reading the sign = clockwise of the way it faces.
                return withDeco(base, DECO_EXIT, facing, toward == clockwise(facing) ? ARROW_LEFT : ARROW_RIGHT);
            }
        }
        if (!lowWall) {
            if ((u == 14 || u == 34) && rowWallPlain(f, rowId, u) && rowWallPlain(f, rowId, u + 1)) {
                return withDeco(base, DECO_EXTINGUISHER, facing);
            }
            if ((u == 15 || u == 35) && rowWallPlain(f, rowId, u - 1) && rowWallPlain(f, rowId, u)) {
                return withDeco(base, DECO_CALL_POINT, facing);
            }
            if (u == 24 && rowWallPlain(f, rowId, u)) {
                return furniture(base, FOUNTAIN, facing, 3);
            }
        }
        // A room sign just before each door (seen from the corridor).
        int nextKind = rowDoorKind(f, rowId, u + 1);
        if (nextKind >= 0 && rowDoorKind(f, rowId, u) < 0) {
            return withDeco(base, nextKind == R_CONFERENCE ? DECO_SIGN_MEETING : DECO_SIGN_OFFICE, facing);
        }
        if (lowWall && u == 24 && rowWallPlain(f, rowId, u)) {
            return withDeco(base, DECO_FIRST_AID, facing);
        }
        long h = Hash.of(f.seed, f.cx, f.cz, 7090 + c);
        if (lowWall && Hash.unit(h) < 0.5 && u == 29 + Hash.range(Hash.mix(h), 8) && rowWallPlain(f, rowId, u)) {
            return withDeco(base, DECO_NOTICE, facing);
        }
        return base;
    }

    /**
     * The kind of the room whose door (or opening) is at u in the door wall of small-room row
     * {@code rowId} (1..4), or -1 when u is no door.
     */
    private static int rowDoorKind(Frame f, int rowId, int u) {
        if (u <= CORE_LO || u >= CORE_HI) {
            return -1;
        }
        long room = roomAt(f.seed, f.cx, f.cz, rowId, u, 4, 7);
        if (room < 0) {
            return -1;
        }
        RoomKey k = roomKey(f.seed, f.cx, f.cz, rowId, false, room);
        return k.isOpening(u) ? k.kind : -1;
    }

    /**
     * True when the door wall of small-room row {@code rowId} (1..4) is plain drywall at u: a
     * partition end, a jamb, or a stretch of wall that is neither a door nor glass.
     */
    private static boolean rowWallPlain(Frame f, int rowId, int u) {
        if (u <= CORE_LO || u >= CORE_HI) {
            return true;
        }
        long room = roomAt(f.seed, f.cx, f.cz, rowId, u, 4, 7);
        if (room < 0) {
            return true;
        }
        RoomKey k = roomKey(f.seed, f.cx, f.cz, rowId, false, room);
        return !k.isOpening(u) && !k.glassFront() && !glazedFront(k.h, k.kind);
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

    /**
     * The lobby hall: a terrazzo floor with entrance matting at both ends, two groups of lit coffers,
     * hanging exit signs near the ends, call buttons beside each elevator, plants at the ends of the
     * elevator core, a water cooler and a sofa, and on the core walls between the elevators: an
     * extinguisher, a call point, the directory board, a fire evacuation plan and a hand sanitiser
     * (row A side); two Almond Water fountains, a first-aid box and a stopped clock (row B side).
     */
    private static long lobbyHall(Frame f, int c) {
        boolean mat = (f.u <= CORE_LO + 1 || f.u >= CORE_HI - 1) && c >= 18 && c <= 22;
        long base = withFloor(pack(FLOOR, 0, 0), mat ? ENTRANCE_MAT : TERRAZZO);
        int coffer = Math.max(coffer(f, c, 12, 19), coffer(f, c, 32, 19));
        if (coffer >= 0) {
            base = withCeiling(base, coffer, 0);
        } else {
            base = withCeiling(base, latticeCeiling(f.seed, f.x, f.z, f.cx, f.cz, false), latticeFacing(f.seed, f.cx, f.cz, f.x, f.z));
        }
        if (c == 20 && (f.u == 7 || f.u == 41)) {
            return hangingExit(f.seed, f.x, f.z, base);
        }
        boolean nearA = c == 16;  // first row in front of row A's wall
        boolean nearB = c == 24;
        if (!nearA && !nearB) {
            return base;
        }
        int awayFromWall = nearA ? f.plusV : f.minusV();
        int k = f.u - 19;
        if (k >= 0 && k <= 12 && k % 4 == 0) {
            return withDeco(base, DECO_ELEVATOR_PANEL, awayFromWall);
        }
        if (nearA) {
            switch (f.u) {
                case 16 -> {
                    return withDeco(base, DECO_EXTINGUISHER, awayFromWall);
                }
                case 20 -> {
                    return withDeco(base, DECO_CALL_POINT, awayFromWall);
                }
                case 24 -> {
                    return withDeco(base, DECO_DIRECTORY, awayFromWall);
                }
                case 28 -> {
                    return withDeco(base, DECO_EVAC_PLAN, awayFromWall);
                }
                case 32 -> {
                    return withDeco(base, DECO_SANITISER, awayFromWall);
                }
                default -> {
                }
            }
        } else {
            switch (f.u) {
                case 16, 32 -> {
                    return furniture(base, FOUNTAIN, awayFromWall, 3);
                }
                case 20 -> {
                    return withDeco(base, DECO_FIRST_AID, awayFromWall);
                }
                case 24 -> {
                    return withDeco(base, DECO_CLOCK, awayFromWall);
                }
                default -> {
                }
            }
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
        return base;
    }

    // ------------------------------------------------------------------------------------------
    // CAFETERIA zones: a lobby merged into a cafeteria
    // ------------------------------------------------------------------------------------------

    /*
     * Rows across the core (c = v - 4, 0..40), all one big space:
     *   c 0-13   the lobby: terrazzo, square columns on the usual 8-block grid, two groups of lit coffers
     *   c 14     a threshold strip where the terrazzo meets the cafeteria's vinyl
     *   c 15-33  the cafeteria, nearly empty as canon wants: two rows (at c 20, 28) or three (c 18,
     *            24, 30) of 3 or 4 identical 3-block tables spread wide along u, about half the places
     *            with a chair (some turned away), some tables bare, a pendant lamp over each table
     *   c 34-39  in front of the servery: a counter run (c 39, u 9-39), vending machines, coolers and
     *            fountains at its ends (u 5-8, 40-43)
     *   c 40     the servery wall: skirting with shuttered serving hatches behind the counter
     * Both ends (u 4 and 44) open onto the ring aisle, so it all joins the floors around it.
     */
    private static long cafeteriaCore(Frame f) {
        int c = f.v - CORE_LO;
        int u = f.u;
        if (c <= 14) {
            long base = withFloor(pack(FLOOR, 0, 0), TERRAZZO);
            if (c <= 12 && Math.floorMod(f.x, 8) == 4 && Math.floorMod(f.z, 8) == 4) {
                return withFloor(pack(COLUMN, 0, 0), TERRAZZO);
            }
            // Coffer groups between the columns (u 16-18 and 32-34; columns stand at u 4, 12, 20...).
            int coffer = Math.max(coffer(f, c, 16, 6), coffer(f, c, 32, 6));
            base = coffer >= 0 ? withCeiling(base, coffer, 0)
                    : withCeiling(base, latticeCeiling(f.seed, f.x, f.z, f.cx, f.cz, true), latticeFacing(f.seed, f.cx, f.cz, f.x, f.z));
            if (c == 14) {
                return withDetail(base, DETAIL_THRESHOLD, f.plusV);
            }
            if (c <= 12) {
                long col = columnSide(f, base);
                if (kind(col) != FLOOR || deco(col) != DECO_NONE) {
                    return col;
                }
            }
            if (c == 10 && u == 24) {
                return hangingExit(f.seed, f.x, f.z, base);
            }
            return base;
        }
        long base = withFloor(pack(FLOOR, 0, 0), VINYL);
        base = withCeiling(base, latticeCeiling(f.seed, f.x, f.z, f.cx, f.cz, true), latticeFacing(f.seed, f.cx, f.cz, f.x, f.z));
        int out = f.minusV(); // from the servery back toward the lobby
        if (c == 40) {
            // The servery wall: a shuttered hatch over each stretch of counter.
            boolean hatch = u >= 9 && u <= 39 && (u - 9) % 6 >= 1 && (u - 9) % 6 <= 4;
            return withFloor(withStyle(pack(WALL, hatch ? out : 0, 0), hatch ? STYLE_HATCH : STYLE_BASE), VINYL);
        }
        if (c == 39) {
            if (u >= 9 && u <= 39) {
                long counter = furniture(base, COUNTER, out, (u - 9) % 6 == 0 ? 1 : 0);
                if (u == 24) return withDeco(counter, DECO_CLOCK, out);
                return counter;
            }
            return switch (u) {
                case 5, 6, 42, 43 -> furniture(base, VENDING, out, 2 + Hash.range(Hash.of(f.seed, f.x, f.z, 7083), 5));
                case 7, 41 -> furniture(base, COOLER, out, 3);
                case 8, 40 -> furniture(base, FOUNTAIN, out, 3);
                default -> base;
            };
        }
        if (c >= 15 && c <= 33) {
            // A few widely spaced rows of a few tables, lots of empty floor round them.
            int rows = Hash.unit(f.seed, f.cx, f.cz, 7088) < 0.5 ? 3 : 2;
            for (int k = 0; k < rows; k++) {
                int side = c - (rows == 3 ? 18 + 6 * k : 20 + 8 * k); // -1 chairs, 0 tables, +1 chairs
                if (side < -1 || side > 1) {
                    continue;
                }
                int n = Hash.unit(f.seed, f.cx * 8 + k, f.cz, 7089) < 0.5 ? 4 : 3;
                int step = n == 4 ? 9 : 11;  // 4 tables at u 8, 17, 26, 35; 3 at u 11, 22, 33
                int p = u - (n == 4 ? 8 : 11);
                if (p < 0 || p % step > 2 || p / step >= n) {
                    continue;
                }
                long key = Hash.of(f.seed, f.cx * 64 + k * 8 + p / step, f.cz, 7090);
                if (side == 0) {
                    return diningTable(f.seed, f.x, f.z, furniture(base, CONF_TABLE, f.plusU, 0), p % step);
                }
                return diningChair(f.seed, f.x, f.z, key, base, side < 0 ? f.plusV : f.minusV());
            }
        }
        if (c == 33 && u == 24) {
            return hangingExit(f.seed, f.x, f.z, base);
        }
        return base;
    }

    /**
     * One block of a cafeteria table (the same conference table as everywhere): a pendant lamp over
     * its middle block (about half still work), now and then a bottle of Almond Water on it, and
     * extremely rarely a double cheeseburger on a tray.
     */
    private static long diningTable(long seed, int x, int z, long table, int along) {
        if (along == 1) {
            table = withDeco(table, DECO_PENDANT, 0, lampState(seed, x, z));
        }
        double r = Hash.unit(seed, x, z, 7084);
        if (r < 0.005) return withTop(table, TOP_BURGER);
        if (r < 0.08) return withTop(table, TOP_BOTTLE);
        return table;
    }

    /**
     * A place beside a cafeteria table ({@code key}: the table's hash): a quarter of the tables have
     * no chairs at all; at the others about half the places have one, and a third of those are
     * pushed back and turned away.
     */
    private static long diningChair(long seed, int x, int z, long key, long base, int towardTable) {
        if (Hash.unit(key) < 0.25 || Hash.unit(seed, x, z, 7086) >= 0.5) {
            return base;
        }
        int facing = Hash.unit(seed, x, z, 7087) < 0.3 ? Hash.range(Hash.of(seed, x, z, 7091), 4) : towardTable;
        return furniture(base, CHAIR, facing, 0);
    }

    /** A pendant lamp's state: about half still work, a few flicker. */
    private static int lampState(long seed, int x, int z) {
        double r = Hash.unit(seed, x, z, 7085);
        return r < 0.5 ? LAMP_LIT : r < 0.55 ? LAMP_FLICKER : LAMP_DEAD;
    }

    // ==========================================================================================
    // Rows of rooms
    // ==========================================================================================

    // Room kinds.
    /** A former private office, cleared out: dents, a floor box, cable trunking, marks on the walls. */
    private static final int R_OFFICE = 0;
    /** Truly empty (big ones are small lobbies: terrazzo, a fountain, two plants). */
    private static final int R_EMPTY = 1;
    private static final int R_STORAGE = 2;
    private static final int R_SERVER = 3;
    private static final int R_CLOSET = 4;
    /** A cafeteria: rows of the conference tables, a counter, vending machines, cooler and fountain. */
    private static final int R_BREAK = 5;
    private static final int R_CONFERENCE = 6;
    private static final int R_CURTAIN = 7;

    /** What a room is and where its door is (from the room's own hash). */
    private record RoomKey(long h, int kind, int u0, int w, int doorU, boolean doubleDoor) {
        /** True for the door (or opening) columns of this room's door wall. */
        boolean isOpening(int u) {
            return u == doorU || (doubleDoor && u == doorU + 1 && doorU + 1 < u0 + w);
        }

        /** Conference rooms have glass fronts. */
        boolean glassFront() {
            return kind == R_CONFERENCE;
        }
    }

    /** The key of {@code room} (as returned by {@link #roomAt}) in row {@code rowId}. */
    private static RoomKey roomKey(long seed, int cx, int cz, int rowId, boolean big, long room) {
        int u0 = (int) (room >>> 16) & 0xFF;
        int w = (int) (room >>> 8) & 0xFF;
        int index = (int) room & 0xFF;
        long h = Hash.of(seed, cx, cz, 7200 + rowId * 64 + index);
        int kind = roomKind(h, big, w);
        int doorU = u0 + 1 + Hash.range(Hash.mix(h ^ 0x11), Math.max(1, w - 2));
        return new RoomKey(h, kind, u0, w, doorU, kind == R_CONFERENCE || kind == R_BREAK);
    }

    /**
     * Canon's mix. Small rooms: 47% empty, 32% conference rooms (only 5+ wide; narrower ones stay
     * empty), 8% cleared-out offices, 4% storerooms, 5% locked closets, 4% server rooms. Big rooms:
     * 32% conference rooms, 30% cafeterias, 26% big empty rooms (lobbies), 5% storerooms, 4% the
     * curtain room, 3% server rooms.
     */
    private static int roomKind(long h, boolean big, int w) {
        double r = Hash.unit(Hash.mix(h ^ 0x10));
        if (!big) {
            if (r < 0.47) return R_EMPTY;
            if (r < 0.79) return w >= 5 ? R_CONFERENCE : R_EMPTY;
            if (r < 0.87) return R_OFFICE;
            if (r < 0.91) return R_STORAGE;
            if (r < 0.96) return R_CLOSET;
            return R_SERVER;
        }
        if (r < 0.32) return R_CONFERENCE;
        if (r < 0.62) return R_BREAK;
        if (r < 0.88) return R_EMPTY;
        if (r < 0.93) return R_STORAGE;
        if (r < 0.97) return R_CURTAIN;
        return R_SERVER;
    }

    /** Office fronts with glass and blinds instead of plain drywall: about a third of the empty rooms and offices. */
    private static boolean glazedFront(long h, int kind) {
        return (kind == R_OFFICE || kind == R_EMPTY) && Hash.unit(Hash.mix(h ^ 0x16)) < 0.35;
    }

    /**
     * One column of a row of rooms between two walls running along u: wallLo (row c) and wallHi.
     * The rooms' doors are in wallHi ({@code doorHigh}) or wallLo. Rooms are split along u by
     * 1-block partitions with hashed widths (4-7 for small rooms, 8-12 for big rooms); the row
     * ends with walls at u = 4 and u = 44, facing the ring aisle.
     *
     * @param rowId a number unique to this row within the zone (keys its random choices)
     * @param big   big rooms (cafeterias, conference rooms...) instead of small ones
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
        RoomKey k = room < 0 ? null : roomKey(f.seed, f.cx, f.cz, rowId, big, room);
        int in = doorHigh ? f.minusV() : f.plusV;   // from the door into the room
        int out = opposite(in);
        if (c == doorWall && (k == null || !k.isOpening(u))) {
            if (k != null && k.glassFront()) {
                boolean blinds = Hash.unit(Hash.mix(k.h ^ 0x12)) < 0.5;
                return withStyle(wallBase, blinds ? STYLE_BLINDS : STYLE_GLASS);
            }
            if (k != null && glazedFront(k.h, k.kind)) {
                return withStyle(wallBase, STYLE_GLAZED);
            }
            // Plain drywall; right beside a door or opening it is the door's frame.
            int next = rowOpening(f, rowId, big, u + 1);
            int prev = rowOpening(f, rowId, big, u - 1);
            int jamb = next > 0 ? JAMB_LEFT | (next == 2 ? JAMB_TALL : 0)
                    : prev > 0 ? JAMB_RIGHT | (prev == 2 ? JAMB_TALL : 0) : 0;
            return jamb == 0 ? wallBase : furniture(wallBase, WALL, 0, jamb | f.axisU());
        }
        // Partition between two rooms, or the back wall. (The door wall's own openings must get past
        // this: they are built just below. Testing c == wallLo/wallHi alone walled every door up.)
        if (k == null || (c != doorWall && (c == wallLo || c == wallHi))) {
            return wallBase;
        }
        int kind = k.kind;
        int depth = wallHi - wallLo - 1;
        boolean hingeRight = !k.doubleDoor && Hash.unit(Hash.mix(k.h ^ 0x15)) < 0.5;

        if (c == doorWall) {
            if (kind == R_BREAK) {
                // Cafeterias: a wide framed opening, a threshold strip where the carpet meets the vinyl.
                return withDetail(withFloor(pack(DOORWAY, 0, f.axisU()), f.carpet), DETAIL_THRESHOLD, in);
            }
            boolean open = Hash.unit(Hash.mix(k.h ^ 0x13)) < (kind == R_CLOSET ? 0.0 : 0.45);
            int type = kind == R_CONFERENCE ? DOOR_GLASS : DOOR_OFFICE;
            int variant = type | (open ? DOOR_OPEN : 0);
            if (kind == R_CLOSET) {
                variant = (Hash.unit(Hash.mix(k.h ^ 0x14)) < 0.12 ? DOOR_SIX : DOOR_OFFICE) | DOOR_LOCKED;
            }
            if (k.doubleDoor) {
                boolean first = u == k.doorU;
                boolean partnerClockwise = first == (clockwise(out) == f.plusU);
                variant |= partnerClockwise ? 0 : DOOR_HINGE_RIGHT;
            } else if (hingeRight) {
                variant |= DOOR_HINGE_RIGHT;
            }
            return withFloor(pack(DOOR, out, variant), f.carpet);
        }

        // Inside the room. iu: 0..w-1 along u; iv: 0 (by the door) .. depth-1 (back wall).
        int iu = u - k.u0;
        int iv = doorHigh ? wallHi - 1 - c : c - wallLo - 1;
        if (kind == R_CLOSET) {
            return pack(FILL, 0, 0);
        }
        int floor = switch (kind) {
            case R_SERVER -> CONCRETE;
            case R_BREAK -> VINYL;
            case R_EMPTY -> big ? TERRAZZO : f.carpet;
            default -> f.carpet;
        };
        long base = withFloor(pack(FLOOR, 0, 0), floor);
        int panelFacing = roomPanelFacing(f, iu, k.w);
        Room r = new Room(f, k.h, kind, iu, iv, k.w, depth, in, out, k.doorU - k.u0, big);
        base = withCeiling(base, roomCeiling(r, panelFacing), panelFacing);
        long col = roomFurniture(r, base);
        if (iv != 0 || kind(col) != FLOOR || deco(col) != DECO_NONE) {
            return col;
        }
        // On the inside of the door wall: the light switch on the latch side, a door stop on the
        // hinge side; beside a double door coat hooks (conference) or a hand sanitiser (cafeteria).
        int latch = hingeRight ? counterClockwise(out) : clockwise(out);
        int latchIu = r.doorIu + (latch == f.plusU ? 1 : -1);
        if (k.doubleDoor) {
            if (iu == r.doorIu - 1) return withDeco(col, DECO_SWITCH, in);
            if (iu == r.doorIu + 2) return withDeco(col, kind == R_CONFERENCE ? DECO_COAT_HOOKS : DECO_SANITISER, in);
            return col;
        }
        if (iu == latchIu) return withDeco(col, DECO_SWITCH, in);
        if (iu == 2 * r.doorIu - latchIu) return withDeco(col, DECO_DOOR_STOP, in);
        return col;
    }

    /** What the door wall of row {@code rowId} has at u: 0 wall, 1 a door, 2 a 3-tall doorway. */
    private static int rowOpening(Frame f, int rowId, boolean big, int u) {
        if (u <= CORE_LO || u >= CORE_HI) {
            return 0;
        }
        long room = roomAt(f.seed, f.cx, f.cz, rowId, u, big ? 8 : 4, big ? 12 : 7);
        if (room < 0) {
            return 0;
        }
        RoomKey k = roomKey(f.seed, f.cx, f.cz, rowId, big, room);
        return !k.isOpening(u) ? 0 : k.kind == R_BREAK ? 2 : 1;
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

    /** One room column in the room's own frame (see {@link #roomRow}). */
    private record Room(Frame f, long h, int kind, int iu, int iv, int w, int depth, int in, int out, int doorIu, boolean big) {
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

        /** The conference table's row and first column (it is 3 long, centred). */
        int tableRow() {
            return depth / 2;
        }

        int tableU0() {
            return (w - 3) / 2;
        }
    }

    /**
     * The ceiling of a room. Small rooms: one panel pair in the middle (in a small conference room
     * at the back, the projector hangs over the table), a diffuser and a smoke detector. Big rooms:
     * pairs on a 4-block grid, diffusers and sprinklers between them. Whether the room's lights
     * still work depends on what it was: empty rooms and storerooms are mostly dark, cafeterias and
     * server rooms mostly lit.
     *
     * @param panelFacing the facing of a panel half here (toward its partner)
     */
    private static int roomCeiling(Room r, int panelFacing) {
        Frame f = r.f;
        double litChance = switch (r.kind) {
            case R_EMPTY -> 0.25;
            case R_STORAGE -> 0.30;
            case R_CURTAIN -> 0.35;
            case R_OFFICE -> 0.35;
            case R_CONFERENCE -> 0.50;
            default -> 0.60;
        };
        boolean lit = Hash.unit(Hash.mix(r.h ^ 0x20)) < litChance;
        boolean conference = r.kind == R_CONFERENCE;
        boolean panel;
        if (r.w <= 7) {
            int mid = (r.w - 1) / 2;
            panel = (r.iu == mid || r.iu == mid + 1) && r.iv == (conference ? r.depth - 1 : r.depth / 2);
        } else {
            panel = (r.iu % 4 == 1 || r.iu % 4 == 2) && r.iu + (r.iu % 4 == 1 ? 1 : 0) < r.w && r.iv % 4 == 2 && r.iv < r.depth;
        }
        if (!panel) {
            if (conference && r.iv == r.tableRow() && r.iu == r.tableU0() + 1) {
                return PROJECTOR;
            }
            int damage = damage(f.seed, f.x, f.z);
            return damage >= 0 ? damage : roomFixture(r);
        }
        if (!lit || damage(f.seed, f.x, f.z) >= 0
                || damage(f.seed, f.x + DX[panelFacing], f.z + DZ[panelFacing]) >= 0) {
            return PANEL_DEAD;
        }
        int first = r.w <= 7 ? (r.w - 1) / 2 : r.iu % 4 == 1 ? r.iu : r.iu - 1; // both halves of a panel roll the same
        double roll = Hash.unit(Hash.mix(r.h ^ (0x300L + first * 32L + r.iv)));
        return roll < 0.08 ? PANEL_DEAD : roll < 0.11 ? PANEL_FLICKER : PANEL_LIT;
    }

    /** The fixtures of a room's ceiling away from its panels. */
    private static int roomFixture(Room r) {
        if (r.w <= 7) {
            int mid = (r.w - 1) / 2;
            if (r.iu == mid && r.iv == r.depth - 1 && r.kind != R_CONFERENCE) return DIFFUSER;
            if (r.iu == mid && r.iv == 0) return SMOKE;
            return TILE;
        }
        if (r.iu % 4 == 3 && r.iv % 4 == 0) {
            return (r.iu / 4 + r.iv / 4) % 2 == 0 ? DIFFUSER : SPRINKLER;
        }
        if (r.iu == r.w / 2 && r.iv == r.depth - 1) {
            return SMOKE;
        }
        return TILE;
    }

    /** Room panels lie along u; the first half points +u at its partner. */
    private static int roomPanelFacing(Frame f, int iu, int w) {
        boolean first = w <= 7 ? iu == (w - 1) / 2 : iu % 4 == 1;
        return first ? f.plusU : f.minusU();
    }

    private static long roomFurniture(Room r, long base) {
        if (r.byDoor()) {
            return base;
        }
        return switch (r.kind) {
            case R_OFFICE -> formerOffice(r, base);
            case R_EMPTY -> emptyRoom(r, base);
            case R_STORAGE -> storageRoom(r, base);
            case R_SERVER -> serverRoom(r, base);
            case R_BREAK -> cafeteriaRoom(r, base);
            case R_CONFERENCE -> conferenceRoom(r, base);
            case R_CURTAIN -> curtainRoom(r, base);
            default -> base;
        };
    }

    /**
     * A 2-wide pale whiteboard mark across columns {@code iuA} and {@code iuA + 1} of the back wall:
     * BOARD_LEFT is the half on the viewer's left (clockwise of the way the mark faces).
     */
    private static long boardMark(Room r, long base, int iuA) {
        boolean higherIsLeft = clockwise(r.out) == r.f.plusU;
        boolean left = (r.iu == iuA + 1) == higherIsLeft;
        return withDeco(base, DECO_MARK, r.out, left ? MARK_BOARD_LEFT : MARK_BOARD_RIGHT);
    }

    /**
     * Canon's empty room: nothing at all, only the ghosts of what was there: the pale rectangle of a
     * whiteboard or a picture on the back wall (or just the nail holes); very rarely a supply crate
     * left in a back corner. Big empty rooms are small lobbies: terrazzo floor, an Almond Water
     * fountain on the right-hand wall, a plant in each back corner, picture marks along the left wall.
     */
    private static long emptyRoom(Room r, long base) {
        int w = r.w;
        int d = r.depth;
        if (r.big) {
            if (r.iu == w - 1 && r.iv == d / 2) return furniture(base, FOUNTAIN, r.left(), 3);
            if (r.iv == d - 1 && (r.iu == 0 || r.iu == w - 1)) return furniture(base, PLANT, r.out, 0);
            if (r.iu == 0 && r.iv % 4 == 2 && r.iv < d - 1) {
                return withDeco(base, DECO_MARK, r.right(), r.rand(10 + r.iv) < 0.7 ? MARK_PICTURE : MARK_HOLES);
            }
            return base;
        }
        int mid = w / 2;
        if (r.iv == d - 1) {
            if (r.iu == 0 && r.rand(1) < 0.03) return furniture(base, CRATE, r.out, 0);
            double s = r.rand(2);
            if (s < 0.40 && w >= 5 && (r.iu == mid - 1 || r.iu == mid)) return boardMark(r, base, mid - 1);
            if (s >= 0.40 && s < 0.60 && r.iu == mid) return withDeco(base, DECO_MARK, r.out, MARK_PICTURE);
            if (s >= 0.60 && s < 0.80 && r.iu == mid) return withDeco(base, DECO_MARK, r.out, MARK_HOLES);
        }
        return base;
    }

    /**
     * A former private office, cleared out: the dents of the desk and the filing cabinet in the
     * carpet, the floor box the desk was plugged into, cable trunking with outlets along the back
     * wall, the pale mark of a picture (or its nail holes) on the left wall, the thermostat on the
     * right wall; now and then the chair left behind, rarely a supply cabinet nobody took.
     */
    private static long formerOffice(Room r, long base) {
        int w = r.w;
        int d = r.depth;
        int mid = w / 2;
        int deskRow = Math.min(2, d - 3);
        if (r.iv == deskRow && (r.iu == mid - 1 || r.iu == mid)) {
            long c = withFloor(base, floor(base) | INDENT);
            return r.iu == mid ? withDetail(c, DETAIL_FLOOR_BOX, r.in) : c;
        }
        if (r.iv == deskRow + 1 && r.iu == mid && r.rand(2) < 0.2) {
            return furniture(base, CHAIR, Hash.range(Hash.mix(r.h ^ 3), 4), 0);
        }
        if (r.iv == d - 1) {
            if (r.iu == w - 1 && r.rand(4) < 0.08) return furniture(base, SUPPLY_CABINET, r.out, 0);
            long c = r.iu == 0 ? withFloor(base, floor(base) | INDENT) : base;
            return withDeco(c, DECO_DADO, r.out, r.iu % 3 == 1 ? 1 : 0);
        }
        if (r.iu == 0 && r.iv == deskRow) {
            return withDeco(base, DECO_MARK, r.right(), r.rand(6) < 0.6 ? MARK_PICTURE : MARK_HOLES);
        }
        if (r.iu == w - 1 && r.iv == deskRow) {
            return withDeco(base, DECO_THERMOSTAT, r.left());
        }
        return base;
    }

    /**
     * Storage: boxes and crates along the walls, a ladder, the middle kept clear. Six in ten were
     * emptied: only the boxes in the back corners are left.
     */
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
        if (r.rand(9) < 0.6) {
            return base;
        }
        double s = Hash.unit(r.f.seed, r.f.x, r.f.z, 7300);
        if (s < 0.12) return furniture(base, CRATE, facing, s < 0.04 ? 1 : 0);
        if (s < 0.17) return furniture(base, LOCKER, facing, 0);
        if (s < 0.62) return furniture(base, BOXES, Hash.range(Hash.of(r.f.seed, r.f.x, r.f.z, 7301), 4), s < 0.35 ? 1 : 0);
        if (s < 0.72) return furniture(base, CABINET, facing, 0);
        if (s < 0.75) return furniture(base, LADDER, facing, 0);
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
     * A cafeteria room: vinyl floor, a counter with a microwave and a fridge along the back wall and
     * a stopped clock over it, vending machines on the left-hand wall, a water cooler, a notice board
     * and an Almond Water fountain on the right-hand wall, and in the middle at most three of the
     * identical conference tables, spread out: one or two in a row across the middle (a second row of
     * one in deep rooms), about half the places with a chair, a pendant lamp over each table; now and
     * then a bottle of Almond Water on a table, extremely rarely a double cheeseburger.
     */
    private static long cafeteriaRoom(Room r, long base) {
        int w = r.w;
        int d = r.depth;
        if (r.iv == d - 1) {
            if (r.iu == w - 1) return furniture(base, FRIDGE, r.out, 0);
            if (r.iu >= 1 && r.iu <= Math.min(w - 2, 5)) {
                long counter = furniture(base, COUNTER, r.out, r.iu == 2 ? 1 : 0);
                return r.iu == 4 ? withDeco(counter, DECO_CLOCK, r.out) : counter;
            }
            return base;
        }
        if (r.iu == 0 && (r.iv == d - 3 || r.iv == d - 4)) {
            int stock = 2 + Hash.range(Hash.mix(r.h ^ (31L + r.iv)), 5);
            return furniture(base, VENDING, r.right(), stock);
        }
        if (r.iu == w - 1) {
            if (r.iv == 1) return furniture(base, COOLER, r.left(), 3);
            if (r.iv == 3) return withDeco(base, DECO_NOTICE, r.left());
            if (r.iv == d - 2 && d - 2 > 3) return furniture(base, FOUNTAIN, r.left(), 3);
            return base;
        }
        // Tables: a row of one or two (a 2-block gap between) across the middle, and in deep rooms
        // one more, nearer the door: never more than three.
        int rowA = d / 2;
        int countA = Math.max(1, Math.min(2, (w - 2) / 5));
        // The second row keeps a free row between its chairs and the first row's: chairs back to
        // back would box in the places between them.
        int rowB = d >= 12 ? rowA - 4 : -10;
        for (int k = 0; k < 2; k++) {
            int side = r.iv - (k == 0 ? rowA : rowB); // -1 / +1 chairs, 0 tables
            if (side < -1 || side > 1) {
                continue;
            }
            int count = k == 0 ? countA : 1;
            int p = r.iu - (w - (5 * count - 2)) / 2;
            if (p < 0 || p % 5 > 2 || p / 5 >= count) {
                continue;
            }
            if (side == 0) {
                return diningTable(r.f.seed, r.f.x, r.f.z, furniture(base, CONF_TABLE, r.out, 0), p % 5);
            }
            long key = Hash.mix(r.h ^ (0x500L + k * 16L + p / 5));
            return diningChair(r.f.seed, r.f.x, r.f.z, key, base, side < 0 ? r.in : r.out);
        }
        return base;
    }

    /**
     * Conference room, as canon describes them: the one identical table (3 blocks long) centred in
     * the room; from 1 to 8 chairs at even intervals round it (pushed in square), and in big rooms
     * sometimes a row of extra chairs along the back wall (up to 20 in all); papers, pencils and mugs
     * of Almond Water on the table, a projector mount over it, a whiteboard on the back wall, an
     * empty TV bracket on the left-hand wall and a stopped clock on the right-hand one.
     */
    private static long conferenceRoom(Room r, long base) {
        int w = r.w;
        int d = r.depth;
        int row = r.tableRow();
        int t0 = r.tableU0();
        int along = r.iu - t0;
        if (r.iv == row && along >= 0 && along <= 2) {
            long table = furniture(base, CONF_TABLE, r.out, 0);
            double s = Hash.unit(r.f.seed, r.f.x, r.f.z, 7320);
            int top = s < 0.30 ? TOP_PAPERS : s < 0.45 ? TOP_PENCILS : s < 0.60 ? TOP_MUG : s < 0.75 ? TOP_PAPERS_MUG : TOP_NONE;
            return withTop(table, top);
        }
        // How many chairs, spread evenly: up to 3 per side, then one at each end when there is
        // room to walk round them (rooms 7+ wide).
        boolean ends = t0 >= 2;
        int n = 1 + Hash.range(Hash.mix(r.h ^ 0x30), ends ? 8 : 6);
        int near = Math.min(3, (n + 1) / 2);
        int far = Math.min(3, n / 2);
        int endChairs = Math.max(0, n - 6);
        if (along >= 0 && along <= 2 && (r.iv == row - 1 || r.iv == row + 1)) {
            int count = r.iv == row - 1 ? near : far;
            if (seat(count, along)) {
                return furniture(base, CHAIR, r.iv == row - 1 ? r.in : r.out, 0); // looking at the table
            }
        }
        if (r.iv == row && ((along == -1 && endChairs >= 1) || (along == 3 && endChairs >= 2))) {
            return furniture(base, CHAIR, along == -1 ? r.right() : r.left(), 0);
        }
        if (r.iv == d - 1) {
            long c = base;
            // Big rooms: half have a row of spare chairs along the back wall (every other place).
            if (r.big && r.rand(3) < 0.5 && r.iu % 2 == 1 && r.iu <= w - 2) {
                c = furniture(c, CHAIR, r.out, 0);
            } else if (r.iu == 0 && r.rand(1) < 0.6) {
                return furniture(c, PLANT, r.out, 0);
            }
            if (r.iu == w / 2 || r.iu == w / 2 - 1) {
                return withDeco(c, DECO_WHITEBOARD, r.out); // on the back wall
            }
            return c;
        }
        if (r.iu == 0 && r.iv == row) {
            return withDeco(base, DECO_TV_BRACKET, r.right());
        }
        if (r.iu == w - 1 && r.iv == row) {
            return withDeco(base, DECO_CLOCK, r.left());
        }
        return base;
    }

    /** Whether seat {@code along} (0..2) of a table side with {@code count} chairs has one: 1 = middle, 2 = both ends, 3 = all. */
    private static boolean seat(int count, int along) {
        return switch (count) {
            case 1 -> along == 1;
            case 2 -> along != 1;
            case 3 -> true;
            default -> false;
        };
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
    // For the anomalies plan
    // ==========================================================================================

    /** The four rows of small rooms of an OFFICES zone (rowId 1..4): {wallLo, wallHi, doorHigh}. */
    private static final int[][] OFFICE_ROWS = {{0, 6, 1}, {10, 16, 0}, {16, 22, 1}, {26, 32, 0}};

    /**
     * Room {@code index} of small-room row {@code rowId} (1..4) in OFFICES zone (cx, cz), when it is
     * an empty room or a former office at least 5 wide: {minX, minZ, maxX, maxZ, doorX, doorZ, in} =
     * its floor inside the walls (inclusive), the door column in its wall, and the way in through
     * that door. Otherwise null.
     */
    static int[] officeRowRoom(long seed, int cx, int cz, int rowId, int index) {
        RoomKey k = null;
        for (int u = CORE_LO + 1; u < CORE_HI; ) {
            long room = roomAt(seed, cx, cz, rowId, u, 4, 7);
            if (room < 0) {
                break; // never happens: u always lands on the first column of a room
            }
            if (((int) room & 0xFF) == index) {
                k = roomKey(seed, cx, cz, rowId, false, room);
                break;
            }
            u = ((int) (room >>> 16) & 0xFF) + ((int) (room >>> 8) & 0xFF) + 1;
        }
        if (k == null || k.w < 5 || (k.kind != R_OFFICE && k.kind != R_EMPTY)) {
            return null;
        }
        int[] row = OFFICE_ROWS[rowId - 1];
        boolean doorHigh = row[2] == 1;
        int vLo = row[0] + 1 + CORE_LO;
        int vHi = row[1] - 1 + CORE_LO;
        int doorV = (doorHigh ? row[1] : row[0]) + CORE_LO;
        boolean z_ = alongZ(seed, cx, cz);
        int plusV = z_ ? EAST : SOUTH;
        int in = doorHigh ? opposite(plusV) : plusV;
        int x0 = cx * CELL;
        int z0 = cz * CELL;
        int u0 = k.u0;
        int u1 = k.u0 + k.w - 1;
        // Zone coordinates (u, v) -> world: rows along Z have u on z and v on x.
        return z_
                ? new int[] {x0 + vLo, z0 + u0, x0 + vHi, z0 + u1, x0 + doorV, z0 + k.doorU, in}
                : new int[] {x0 + u0, z0 + vLo, x0 + u1, z0 + vHi, x0 + k.doorU, z0 + doorV, in};
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
