package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;
import net.minecraft.core.Direction;

/**
 * The room maze of Level 0 ({@link Level0Zones#ROOMS}): our answer to the found-footage maze where
 * every 16x16 cell is a room behind thick walls and the doorways line up into a "tunnel of nested
 * door frames". Theirs is one room size on one fixed grid, so after a few minutes the grid shows.
 * Ours is built on its own irregular grid ({@link #X_CELLS}, {@link #Z_CELLS}):
 * <ul>
 *   <li><b>Rooms of very different sizes:</b> most 8-13 across, one in eight only 6, one in five
 *       14-22, and the odd merged pair where the wall between two rooms is missing (its ends stay
 *       behind as square posts).</li>
 *   <li><b>Thick wall masses:</b> walls are 2-3 thick (so every doorway is a 2-3 deep frame), and
 *       most rooms also have solid blocks filling some of their corners, making walls up to 7
 *       thick, L-shaped rooms, and "tunnel rooms" where four corner masses leave only a corridor
 *       the width of the doorways through the room.</li>
 *   <li><b>Doorways 2, 3, 4 or 6 wide</b>, placed off-centre. Most (85%) line up with the
 *       doorways of the next few walls of the same row of rooms, so you look through a run of
 *       2-deep frames; the rest are out of line, so a vista is a find, not a rule.</li>
 *   <li><b>Posts and pillars:</b> every place four rooms meet is a solid post; some big rooms have
 *       one big pillar or four posts standing in them.</li>
 *   <li><b>1-wide slits</b> instead of doorways now and then, and <b>dead ends</b> (rooms with one
 *       way in, ~11%), over half of them holding something: supply crates (always, behind a slit), a
 *       hazmat case, a backpack and litter, or a heap of furniture.</li>
 * </ul>
 *
 * <p><b>Always connected.</b> Every room links to the room west of it or the room north of it
 * (a coin toss per room) through a doorway, a slit or a missing wall, so following those links you
 * always walk north-west until you leave the district through one of its border doorways. Extra
 * doorways on top of that make loops. Corner masses never touch a doorway's lane and always leave a
 * 3-wide way through the middle of the room, so they never cut a room in two.
 *
 * <p><b>Who owns which column.</b> Like {@link Level0Builders}, a room belongs to the district if
 * its centre lies in a ROOMS zone, and it claims its own columns and its walls (a wall between a
 * district room and anything else still belongs to the district and mostly has a doorway). A
 * doorway out of the district is carried a few blocks on into the maze next door as a "porch", so
 * it never opens onto the solid back of a corridor zone.
 *
 * <p><b>Lights.</b> Each room is lit as a whole or not at all (a switched-off room is a dark room
 * between lit ones, and a lit room seen from a dark one is a bright rectangle through the door
 * frames). How likely a room is lit and how many tubes are dead depends on the light district
 * ({@link Level0Lights}). A lit room gets a group of panels at its middle (the corners stay
 * darker); bright districts get a panel every 4 blocks.
 *
 * <p>Everything is a pure function of (seed, x, z). The plan of a room (its walls, doorways, masses,
 * pillar, dressing, lights) is worked out once and kept per thread, so filling column after column
 * of the same room costs nothing extra and allocates nothing.
 */
final class Level0RoomMaze {
    /**
     * The room grid: walls 2-3 thick; rooms mostly 8-13, 12% only 6 and 20% 14-22 across (the last
     * room of each 64-block stretch takes whatever is left, so it can be longer still).
     */
    static final Bands X_CELLS = new Bands(64, 2, 3, 8, 13, 6, 0.12, 22, 0.20, 105);
    static final Bands Z_CELLS = new Bands(64, 2, 3, 8, 13, 6, 0.12, 22, 0.20, 106);

    // What a wall between two rooms is.
    static final int SOLID = 0;
    static final int DOOR = 1;
    static final int MISSING = 2;
    static final int SLIT = 3;

    // The walls of a room, in plan order.
    private static final int WEST = 0;
    private static final int EAST = 1;
    private static final int NORTH = 2;
    private static final int SOUTH = 3;

    // The corners of a room (corner masses), in plan order.
    private static final int NW = 0;
    private static final int NE = 1;
    private static final int SW = 2;
    private static final int SE = 3;

    // Room types.
    private static final int T_PLAIN = 0;
    private static final int T_MASSY = 1;
    private static final int T_TUNNEL = 2;
    private static final int T_PILLAR = 3;

    /** Share of room-to-room walls on the "always connected" links that are a slit or missing instead of a doorway. */
    private static final double LINK_SLIT = 0.07;
    private static final double LINK_MISSING = 0.08;
    /** Extra openings on the other walls: doorway, missing wall (merged rooms), slit. Tuned so ~11% of rooms are dead ends. */
    private static final double EXTRA_DOOR = 0.16;
    private static final double EXTRA_MISSING = 0.03;
    private static final double EXTRA_SLIT = 0.03;
    /**
     * Chance a wall on the district's border (with a district room on one side only) has a doorway
     * out. High, so the rooms of the neighbouring maze cut off by the district's walls still find a
     * way out through a doorway and its porch.
     */
    private static final double BORDER_DOOR = 0.75;
    /** Share of doorways that line up with the doorways of the next walls of their row. */
    private static final double ALIGNED = 0.85;
    /** How many walls in a row share one doorway position (then the line may jog). */
    private static final int ALIGN_RUN = 5;
    /** Share of dead-end rooms that hold something. */
    private static final double DRESSED = 0.55;
    /** Per light district: chance a whole room's lights are switched off. */
    private static final double[] ROOM_OFF = {0.03, 0.10, 0.45, 0.88};
    /** How far (blocks) a border doorway is carried out into the neighbouring maze (see porch). */
    private static final int PORCH = 8;

    // Things standing in a room (bits 12-14 of an open column): see propAt.
    static final int PROP_NONE = 0;
    static final int PROP_CRATE = 1;   // one supply crate
    static final int PROP_CRATES = 2;  // two stacked
    static final int PROP_HAZMAT = 3;
    static final int PROP_BACKPACK = 4;
    static final int PROP_LITTER = 5;
    static final int PROP_HEAP = 6;    // furniture heap, one high
    static final int PROP_HEAP2 = 7;   // two high
    static final int PROP_SHIFT = 12;
    /** Bit 15 of an open column: the middle of a room (where a carpet arrow may be painted). */
    static final int ROOM_MIDDLE = 1 << 15;

    // Room dressings.
    private static final int D_NONE = 0;
    private static final int D_CRATES = 1;
    private static final int D_HAZMAT = 2;
    private static final int D_BACKPACK = 3;
    private static final int D_HEAP = 4;

    private Level0RoomMaze() {}

    // ==========================================================================================
    // Which rooms are in the district
    // ==========================================================================================

    /** True if the room made of X gap {@code gx} and Z gap {@code gz} belongs to the room maze. */
    static boolean isCell(long seed, long gx, long gz) {
        int cx = X_CELLS.start(gx) + Bands.length(gx) / 2;
        int cz = Z_CELLS.start(gz) + Bands.length(gz) / 2;
        return Level0Zones.kindAt(seed, cx, cz) == Level0Zones.ROOMS;
    }

    /**
     * The way a room's guaranteed link goes: 0 = to the room west of it, 1 = to the room north.
     * Rooms decide in runs: in about half of the stretches of {@link #ALIGN_RUN} rooms along a row (or a
     * column) every room links the same way, so a whole run of walls in a line is open, and with
     * their lined-up doorways that is a tunnel of frames through four or five rooms. The others
     * toss a coin each.
     */
    private static int linkAxis(long seed, long gx, long gz) {
        int ix = Bands.index(gx);
        int iz = Bands.index(gz);
        // Runs along X (all link west) and runs along Z (all link north) on alternating grids.
        long rowRun = Hash.of(seed, iz, Math.floorDiv(ix, ALIGN_RUN), 812);
        if (Hash.unit(rowRun) < 0.3) {
            return 0;
        }
        long columnRun = Hash.of(seed, ix, Math.floorDiv(iz, ALIGN_RUN), 813);
        if (Hash.unit(columnRun) < 0.3) {
            return 1;
        }
        return Hash.unit(seed, ix, iz, 800) < 0.5 ? 0 : 1;
    }

    /**
     * What the wall band {@code wall} is across gap {@code gap} (axis 0: a north-south wall, an X
     * band, between a west and an east room; axis 1: an east-west wall between a north and a south
     * room): SOLID, DOOR, MISSING or SLIT, or -1 if neither room beside it is in the district.
     */
    static int wallState(long seed, long wall, long gap, int axis) {
        Bands walls = axis == 0 ? X_CELLS : Z_CELLS;
        long low = walls.span(seed, walls.start(wall) - 1);
        long high = walls.span(seed, walls.end(wall));
        boolean lowIn = axis == 0 ? isCell(seed, low, gap) : isCell(seed, gap, low);
        boolean highIn = axis == 0 ? isCell(seed, high, gap) : isCell(seed, gap, high);
        if (!lowIn && !highIn) {
            return -1;
        }
        double r = Hash.unit(seed, Bands.index(wall), Bands.index(gap), 801 + axis);
        // The room on the high side links back to the low side through this wall.
        boolean link = highIn && (axis == 0 ? linkAxis(seed, high, gap) : linkAxis(seed, gap, high)) == axis;
        if (link) {
            if (!lowIn) {
                return DOOR; // out of the district: always a proper doorway
            }
            return r < LINK_SLIT ? SLIT : r < LINK_SLIT + LINK_MISSING ? MISSING : DOOR;
        }
        if (!lowIn || !highIn) {
            return r < BORDER_DOOR ? DOOR : SOLID;
        }
        if (r < EXTRA_DOOR) return DOOR;
        if (r < EXTRA_DOOR + EXTRA_MISSING) return MISSING;
        if (r < EXTRA_DOOR + EXTRA_MISSING + EXTRA_SLIT) return SLIT;
        return SOLID;
    }

    /**
     * Where the opening of a wall is: offset along the wall from the gap's start (bits 0-7) and
     * width (bits 8+). Doorways are 2 (15%), 3 (25%), 4 (45%) or 6 (15%) wide, anywhere along the
     * wall. Aligned ones take their place from the row and a run of {@link #ALIGN_RUN} walls, so
     * every wall of the run has the same opening in the same spot: a straight tunnel of frames.
     */
    static int opening(long seed, long wall, long gap, int axis, int state) {
        int length = Bands.length(gap);
        int wallIndex = Bands.index(wall);
        int gapIndex = Bands.index(gap);
        if (state == MISSING) {
            return length << 8;
        }
        if (state == SLIT) {
            return Hash.range(Hash.of(seed, wallIndex, gapIndex, 803 + axis), length) | 1 << 8;
        }
        long h;
        if (Hash.unit(seed, wallIndex, gapIndex, 805 + axis) < ALIGNED) {
            int phase = Hash.range(Hash.of(seed, gapIndex, axis, 807), ALIGN_RUN);
            h = Hash.of(seed, gapIndex, Math.floorDiv(wallIndex + phase, ALIGN_RUN), 808 + axis);
        } else {
            h = Hash.of(seed, wallIndex, gapIndex, 810 + axis);
        }
        double r = Hash.unit(h);
        int width = r < 0.15 ? 2 : r < 0.40 ? 3 : r < 0.85 ? 4 : 6;
        width = Math.min(width, length - 2);
        int offset = Hash.range(Hash.mix(h), length - width + 1);
        return offset | width << 8;
    }

    // ==========================================================================================
    // Columns
    // ==========================================================================================

    /**
     * The cell spans this thread looked up last (worldgen fills column after column of one room),
     * and the last wall and junction it worked out: every column of a wall band asks for the same
     * wall state, so it is computed once per wall instead of once per column.
     */
    private static final class LastSpans {
        boolean valid;
        long seed;
        long x;
        long z;
        boolean wallValid;
        int wallIndex;
        int wallGap;
        int wallAxis;
        int wallState;
        boolean postValid;
        int postX;
        int postZ;
        boolean post;
    }

    private static final ThreadLocal<LastSpans> LAST = ThreadLocal.withInitial(LastSpans::new);

    /**
     * Column (x, z) if it belongs to the room maze, packed like {@link Level0Layout#mazeColumn}
     * (without the zone kind), or -1 if it doesn't. Open floor carries what stands on it in bits
     * 12-14 (PROP_*) and {@link #ROOM_MIDDLE}; a prop's facing is in bits 8-9.
     */
    static int column(long seed, int x, int z) {
        LastSpans last = LAST.get();
        boolean reuse = last.valid && last.seed == seed;
        long gx = reuse && x >= X_CELLS.start(last.x) && x < X_CELLS.end(last.x) ? last.x : X_CELLS.span(seed, x);
        long gz = reuse && z >= Z_CELLS.start(last.z) && z < Z_CELLS.end(last.z) ? last.z : Z_CELLS.span(seed, z);
        if (!reuse) {
            last.wallValid = false;
            last.postValid = false;
        }
        last.valid = true;
        last.seed = seed;
        last.x = gx;
        last.z = gz;
        boolean bandX = Bands.isBand(gx);
        boolean bandZ = Bands.isBand(gz);
        if (!bandX && !bandZ) {
            if (!isCell(seed, gx, gz)) {
                return porch(seed, x, z, gx, gz) ? Level0Layout.OPEN : -1;
            }
            return roomColumn(seed, plan(seed, gx, gz), x - X_CELLS.start(gx), z - Z_CELLS.start(gz));
        }
        if (bandX && !bandZ) {
            int state = lastWallState(last, seed, gx, gz, 0);
            return state < 0 ? -1 : wallColumn(seed, gx, gz, 0, state, z - Z_CELLS.start(gz), x - X_CELLS.start(gx));
        }
        if (!bandX) {
            int state = lastWallState(last, seed, gz, gx, 1);
            return state < 0 ? -1 : wallColumn(seed, gz, gx, 1, state, x - X_CELLS.start(gx), z - Z_CELLS.start(gz));
        }
        // Where four rooms meet: a solid post, if any of the four rooms is in the district.
        int ix = Bands.index(gx);
        int iz = Bands.index(gz);
        if (!last.postValid || last.postX != ix || last.postZ != iz) {
            long west = X_CELLS.span(seed, X_CELLS.start(gx) - 1);
            long east = X_CELLS.span(seed, X_CELLS.end(gx));
            long north = Z_CELLS.span(seed, Z_CELLS.start(gz) - 1);
            long south = Z_CELLS.span(seed, Z_CELLS.end(gz));
            last.post = isCell(seed, west, north) || isCell(seed, east, north) || isCell(seed, west, south) || isCell(seed, east, south);
            last.postValid = true;
            last.postX = ix;
            last.postZ = iz;
        }
        return last.post ? Level0Layout.WALL : -1;
    }

    /** {@link #wallState}, remembered for the last wall this thread asked about. */
    private static int lastWallState(LastSpans last, long seed, long wall, long gap, int axis) {
        int wallIndex = Bands.index(wall);
        int gapIndex = Bands.index(gap);
        if (!last.wallValid || last.wallIndex != wallIndex || last.wallGap != gapIndex || last.wallAxis != axis) {
            last.wallState = wallState(seed, wall, gap, axis);
            last.wallValid = true;
            last.wallIndex = wallIndex;
            last.wallGap = gapIndex;
            last.wallAxis = axis;
        }
        return last.wallState;
    }

    /**
     * One column of a wall band: {@code along} runs along the wall from the gap's start,
     * {@code across} through its thickness. A solid wall may carry graffiti or an exit-sign alcove
     * (the false-hope door) on a face that looks into a district room, as the ordinary maze's walls do.
     */
    private static int wallColumn(long seed, long wall, long gap, int axis, int state, int along, int across) {
        if (state != SOLID) {
            int open = opening(seed, wall, gap, axis, state);
            int offset = open & 0xFF;
            boolean in = along >= offset && along < offset + (open >>> 8);
            return in ? (state == MISSING ? Level0Layout.OPEN : Level0Layout.DOORWAY) : Level0Layout.WALL;
        }
        int length = Bands.length(gap);
        int thickness = Bands.length(wall);
        long h = Hash.of(seed, Bands.index(wall), Bands.index(gap), 820 + axis);
        double r = Hash.unit(h);
        boolean alcove = r < 0.012 && thickness == 2 && length >= 4;
        boolean graffiti = r >= 0.97 && length >= 4;
        if (!alcove && !graffiti) {
            return Level0Layout.WALL;
        }
        long bits = Hash.mix(h);
        int side = (int) (bits >>> 40) & 1;
        // Only on a face that looks into a district room (the other side may be solid maze).
        Bands walls = axis == 0 ? X_CELLS : Z_CELLS;
        long room = walls.span(seed, side == 0 ? walls.start(wall) - 1 : walls.end(wall));
        if (!(axis == 0 ? isCell(seed, room, gap) : isCell(seed, gap, room))) {
            side ^= 1;
        }
        int face = side == 0 ? 0 : thickness - 1;
        Direction out = Level0Layout.faceDirection(axis, side);
        int facing = out.get2DDataValue() << 8;
        if (alcove) {
            int offset = 1 + Hash.range(Hash.mix(bits), length - 2);
            if (along == offset) return (across == face ? Level0Layout.ALCOVE : Level0Layout.ALCOVE_DOOR) | facing;
            if (across == face && Math.abs(along - offset) == 1) return Level0Layout.ALCOVE_FRAME | facing;
            return Level0Layout.WALL;
        }
        int offset = Hash.range(Hash.mix(bits), length - 1);
        if (across == face && along >= offset && along < offset + 2) {
            // Part 0 is the left half as seen from the room; the viewer's left is the face direction turned clockwise.
            Direction left = out.getClockWise();
            int leftStep = axis == 0 ? left.getStepZ() : left.getStepX();
            int part = (along == offset) == (leftStep > 0) ? 1 : 0;
            int variant = (int) (bits >>> 44) & 3;
            return Level0Layout.GRAFFITI | facing | (variant | part << 2) << 12;
        }
        return Level0Layout.WALL;
    }

    /** A column inside a room, from its plan: floor, corner mass, pillar or post, or something standing there. */
    private static int roomColumn(long seed, Plan p, int rx, int rz) {
        if (p.solid(rx, rz)) {
            return Level0Layout.WALL;
        }
        int prop = p.propAt(rx, rz);
        int column = Level0Layout.OPEN | prop << PROP_SHIFT;
        if (prop != PROP_NONE) {
            column |= Hash.range(Hash.of(seed, p.x0 + rx, p.z0 + rz, 830), 4) << 8;
        }
        if (rx == p.lenX / 2 && rz == p.lenZ / 2) {
            column |= ROOM_MIDDLE;
        }
        return column;
    }

    /**
     * The ceiling fixture over open room column (x, z), packed as in {@link Level0Layout#fixture}
     * (0 = none). Only asked for open columns of the district.
     */
    static int fixture(long seed, int x, int z) {
        long gx = X_CELLS.span(seed, x);
        long gz = Z_CELLS.span(seed, z);
        if (Bands.isBand(gx) || Bands.isBand(gz) || !isCell(seed, gx, gz)) {
            return 0; // doorways, missing walls and porches: no light in the frame itself
        }
        Plan p = plan(seed, gx, gz);
        int rx = x - p.x0;
        int rz = z - p.z0;
        if (!p.lightAt(rx, rz)) {
            return 0;
        }
        boolean dead = !p.lit || Level0Lights.panelDead(seed, x, z, p.district);
        return Level0Layout.FIX_FIXTURE | (dead ? Level0Layout.FIX_DEAD : 0);
    }

    /**
     * Porches: a doorway in the district's border wall would often open straight onto the solid
     * back of the neighbouring maze (a corridor zone is mostly solid wall). So the doorway carries
     * on, door-wide, up to {@link #PORCH} blocks out into the neighbouring room-sized cell, cutting
     * through whatever wall is there, which nearly always reaches an open space. True if column
     * (x, z), in the non-district cell (gx, gz), is part of such a porch.
     */
    private static boolean porch(long seed, int x, int z, long gx, long gz) {
        int rx = x - X_CELLS.start(gx);
        int rz = z - Z_CELLS.start(gz);
        int lenX = Bands.length(gx);
        int lenZ = Bands.length(gz);
        if (rx >= PORCH && lenX - 1 - rx >= PORCH && rz >= PORCH && lenZ - 1 - rz >= PORCH) {
            return false; // the middle of the cell: no porch ever reaches it
        }
        Porches p = porches(seed, gx, gz);
        return rx < PORCH && p.covers(WEST, rz) || lenX - 1 - rx < PORCH && p.covers(EAST, rz)
                || rz < PORCH && p.covers(NORTH, rx) || lenZ - 1 - rz < PORCH && p.covers(SOUTH, rx);
    }

    /** The doorway lanes reaching into a non-district cell from each side (per thread, like the plans). */
    private static final class Porches {
        boolean valid;
        long seed;
        int ix;
        int iz;
        /** Per side (WEST, EAST, NORTH, SOUTH): the doorway's offset | width << 8, or 0 for none. */
        final int[] lane = new int[4];

        boolean covers(int side, int along) {
            int offset = lane[side] & 0xFF;
            return lane[side] != 0 && along >= offset && along < offset + (lane[side] >>> 8);
        }
    }

    private static final ThreadLocal<Porches> PORCHES = ThreadLocal.withInitial(Porches::new);

    private static Porches porches(long seed, long gx, long gz) {
        Porches p = PORCHES.get();
        int ix = Bands.index(gx);
        int iz = Bands.index(gz);
        if (p.valid && p.seed == seed && p.ix == ix && p.iz == iz) {
            return p;
        }
        p.valid = true;
        p.seed = seed;
        p.ix = ix;
        p.iz = iz;
        p.lane[WEST] = borderDoor(seed, X_CELLS.span(seed, X_CELLS.start(gx) - 1), gz, 0);
        p.lane[EAST] = borderDoor(seed, X_CELLS.span(seed, X_CELLS.end(gx)), gz, 0);
        p.lane[NORTH] = borderDoor(seed, Z_CELLS.span(seed, Z_CELLS.start(gz) - 1), gx, 1);
        p.lane[SOUTH] = borderDoor(seed, Z_CELLS.span(seed, Z_CELLS.end(gz)), gx, 1);
        return p;
    }

    /** The doorway (offset | width << 8) of a border wall, or 0 if it has none. */
    private static int borderDoor(long seed, long wall, long gap, int axis) {
        return wallState(seed, wall, gap, axis) == DOOR ? opening(seed, wall, gap, axis, DOOR) : 0;
    }

    // ==========================================================================================
    // Room plans
    // ==========================================================================================

    /** Number of openings (doorways, slits, missing walls) of the room, for the locate command and hooks. */
    static int openings(long seed, long gx, long gz) {
        return plan(seed, gx, gz).openings;
    }

    /** True if the room is a dead end that holds something (crates, a case, a backpack, a heap). */
    static boolean dressedDeadEnd(long seed, long gx, long gz) {
        return plan(seed, gx, gz).dressing != D_NONE;
    }

    /** True if the room's west and east doorways line up in one straight line through it (a frame-tunnel room). */
    static boolean throughView(long seed, long gx, long gz) {
        Plan p = plan(seed, gx, gz);
        return p.state[WEST] == DOOR && p.state[EAST] == DOOR && p.open[WEST] == p.open[EAST]
                || p.state[NORTH] == DOOR && p.state[SOUTH] == DOOR && p.open[NORTH] == p.open[SOUTH];
    }

    /**
     * Per-thread plan of the room this thread asked about last. Rooms are 6-22 blocks across, so
     * the 256 columns of a chunk touch only a handful of rooms and nearly every column reuses the
     * plan instead of redoing its ~40 hashes.
     */
    private static final ThreadLocal<Plan> PLAN = ThreadLocal.withInitial(Plan::new);

    private static Plan plan(long seed, long gx, long gz) {
        Plan p = PLAN.get();
        int ix = Bands.index(gx);
        int iz = Bands.index(gz);
        if (!p.valid || p.seed != seed || p.ix != ix || p.iz != iz) {
            p.load(seed, gx, gz);
        }
        return p;
    }

    /** Everything about one room, worked out once (mutable and reused: never kept by callers). */
    private static final class Plan {
        boolean valid;
        long seed;
        int ix;
        int iz;
        int x0;
        int z0;
        int lenX;
        int lenZ;
        /** Per wall (WEST, EAST, NORTH, SOUTH): its state, and its opening's offset | width << 8. */
        final int[] state = new int[4];
        final int[] open = new int[4];
        int openings;
        /** Corner masses (NW, NE, SW, SE): size along X and along Z (0 = none). */
        final int[] massX = new int[4];
        final int[] massZ = new int[4];
        int type;
        /** A pillar (T_PILLAR, size >= 2) or four posts (size 0): its first column and size. */
        int pillarX;
        int pillarZ;
        int pillarSize;
        int dressing;
        /** Dressing spots: room-local x, z, and what stands there (up to three). */
        final int[] spotX = new int[3];
        final int[] spotZ = new int[3];
        final int[] spotProp = new int[3];
        int district;
        boolean lit;
        /** The light layout: 0 a grid every 4 (bright), 1 a 2x2 group, 2 a pair, 3 one panel. */
        int lights;
        int lightX;
        int lightZ;
        boolean pairAlongX;

        void load(long seed, long gx, long gz) {
            this.valid = true;
            this.seed = seed;
            this.ix = Bands.index(gx);
            this.iz = Bands.index(gz);
            x0 = X_CELLS.start(gx);
            z0 = Z_CELLS.start(gz);
            lenX = Bands.length(gx);
            lenZ = Bands.length(gz);
            long west = X_CELLS.span(seed, x0 - 1);
            long east = X_CELLS.span(seed, x0 + lenX);
            long north = Z_CELLS.span(seed, z0 - 1);
            long south = Z_CELLS.span(seed, z0 + lenZ);
            state[WEST] = wallState(seed, west, gz, 0);
            state[EAST] = wallState(seed, east, gz, 0);
            state[NORTH] = wallState(seed, north, gx, 1);
            state[SOUTH] = wallState(seed, south, gx, 1);
            open[WEST] = state[WEST] == SOLID ? 0 : opening(seed, west, gz, 0, state[WEST]);
            open[EAST] = state[EAST] == SOLID ? 0 : opening(seed, east, gz, 0, state[EAST]);
            open[NORTH] = state[NORTH] == SOLID ? 0 : opening(seed, north, gx, 1, state[NORTH]);
            open[SOUTH] = state[SOUTH] == SOLID ? 0 : opening(seed, south, gx, 1, state[SOUTH]);
            openings = 0;
            for (int s : state) {
                if (s != SOLID) openings++;
            }

            long h = Hash.of(seed, ix, iz, 840);
            double r = Hash.unit(h);
            int small = Math.min(lenX, lenZ);
            type = r < 0.30 ? T_PLAIN : r < 0.75 ? T_MASSY : r < 0.88 ? T_TUNNEL : small >= 12 ? T_PILLAR : T_MASSY;
            planMasses(Hash.mix(h ^ 1));
            planPillar(Hash.mix(h ^ 2));
            planDressing(Hash.mix(h ^ 3));
            planLights(seed, Hash.mix(h ^ 4));
        }

        /**
         * Corner masses: each a rectangle in one corner that stays clear of the lanes in front of
         * the two walls' openings and is at most (length - 3) / 2 each way, so a 3-wide way always
         * runs through the middle of the room. A missing wall leaves its corners clear.
         */
        private void planMasses(long h) {
            int maxX = (lenX - 3) / 2;
            int maxZ = (lenZ - 3) / 2;
            for (int c = 0; c < 4; c++) {
                massX[c] = 0;
                massZ[c] = 0;
                if (type != T_MASSY && type != T_TUNNEL) {
                    continue;
                }
                boolean westSide = c == NW || c == SW;
                boolean northSide = c == NW || c == NE;
                int across = northSide ? NORTH : SOUTH; // the wall along X at this corner
                int side = westSide ? WEST : EAST;      // the wall along Z at this corner
                int limX = maxX;
                if (state[across] != SOLID) {
                    int offset = open[across] & 0xFF;
                    int end = offset + (open[across] >>> 8);
                    limX = Math.min(limX, westSide ? offset : lenX - end);
                }
                int limZ = maxZ;
                if (state[side] != SOLID) {
                    int offset = open[side] & 0xFF;
                    int end = offset + (open[side] >>> 8);
                    limZ = Math.min(limZ, northSide ? offset : lenZ - end);
                }
                if (limX < 1 || limZ < 1) {
                    continue;
                }
                long hc = Hash.mix(h + c * 0x9E3779B97F4A7C15L);
                if (type == T_TUNNEL) {
                    massX[c] = limX;
                    massZ[c] = limZ;
                } else if (Hash.unit(hc) < 0.55) {
                    // Mostly big blocks (the thick masses), sometimes a thin thickening of the wall.
                    massX[c] = Math.max(1, limX - Hash.range(Hash.mix(hc), Math.min(limX, 3)));
                    massZ[c] = Math.max(1, limZ - Hash.range(Hash.mix(hc ^ 1), Math.min(limZ, 3)));
                }
            }
        }

        /** A pillar room: one big pillar (2-4 square) near the middle, or four 2x2 posts. */
        private void planPillar(long h) {
            pillarSize = -1;
            if (type != T_PILLAR) {
                return;
            }
            if (Hash.unit(h) < 0.5) {
                pillarSize = 2 + Hash.range(Hash.mix(h), 3);
                pillarX = (lenX - pillarSize) / 2 + Hash.range(Hash.mix(h ^ 1), 3) - 1;
                pillarZ = (lenZ - pillarSize) / 2 + Hash.range(Hash.mix(h ^ 2), 3) - 1;
            } else {
                pillarSize = 0; // four posts, at about a third of the way in from each wall
                pillarX = lenX / 3 - 1;
                pillarZ = lenZ / 3 - 1;
            }
        }

        /** True if room column (rx, rz) is a corner mass, the pillar or a post. */
        boolean solid(int rx, int rz) {
            if (rx < massX[NW] && rz < massZ[NW]) return true;
            if (rx >= lenX - massX[NE] && rz < massZ[NE]) return true;
            if (rx < massX[SW] && rz >= lenZ - massZ[SW]) return true;
            if (rx >= lenX - massX[SE] && rz >= lenZ - massZ[SE]) return true;
            if (pillarSize > 0) {
                return rx >= pillarX && rx < pillarX + pillarSize && rz >= pillarZ && rz < pillarZ + pillarSize;
            }
            if (pillarSize == 0) {
                boolean px = rx >= pillarX && rx < pillarX + 2 || rx >= lenX - pillarX - 2 && rx < lenX - pillarX;
                boolean pz = rz >= pillarZ && rz < pillarZ + 2 || rz >= lenZ - pillarZ - 2 && rz < lenZ - pillarZ;
                return px && pz;
            }
            return false;
        }

        /**
         * A dead end (one way in) holds something about half the time, against the wall facing the
         * way in: supply crates in the two back corners (always, if the way in is a slit), a hazmat
         * case, a backpack with litter, or a heap of furniture.
         */
        private void planDressing(long h) {
            dressing = D_NONE;
            spotProp[0] = spotProp[1] = spotProp[2] = PROP_NONE;
            if (openings != 1) {
                return;
            }
            int in = 0;
            for (int w = 0; w < 4; w++) {
                if (state[w] != SOLID) in = w;
            }
            if (state[in] == SLIT) {
                dressing = D_CRATES;
            } else if (Hash.unit(h) < DRESSED) {
                double r = Hash.unit(Hash.mix(h));
                dressing = r < 0.45 ? D_CRATES : r < 0.60 ? D_HAZMAT : r < 0.80 ? D_BACKPACK : D_HEAP;
            } else {
                return;
            }
            // The back row: the room's row (or column) against the wall opposite the way in.
            int back = in ^ 1; // WEST <-> EAST, NORTH <-> SOUTH
            boolean alongX = back >= NORTH; // the back wall runs along X
            int length = alongX ? lenX : lenZ;
            int depth = alongX ? lenZ : lenX;
            int row = back == WEST || back == NORTH ? 0 : depth - 1;
            int first = -1;
            int lastFree = -1;
            for (int a = 0; a < length; a++) {
                if (!solid(alongX ? a : row, alongX ? row : a)) {
                    if (first < 0) first = a;
                    lastFree = a;
                }
            }
            if (first < 0) {
                dressing = D_NONE;
                return;
            }
            int middle = (first + lastFree) / 2;
            // Crates go in the two back corners; a heap is centred on the middle of the back wall.
            boolean heap = dressing == D_HEAP;
            setSpot(0, alongX, row, heap ? Math.max(first, middle - 1) : first);
            setSpot(1, alongX, row, heap ? Math.min(lastFree, middle + 1) : lastFree);
            setSpot(2, alongX, row, middle);
            long b = Hash.mix(h ^ 7);
            switch (dressing) {
                case D_CRATES -> {
                    spotProp[0] = Hash.unit(b) < 0.4 ? PROP_CRATES : PROP_CRATE;
                    spotProp[1] = Hash.unit(Hash.mix(b)) < 0.6 ? PROP_CRATE : PROP_NONE;
                }
                case D_HAZMAT -> spotProp[2] = PROP_HAZMAT;
                case D_BACKPACK -> {
                    spotProp[2] = PROP_BACKPACK;
                    spotProp[0] = PROP_LITTER;
                }
                default -> {
                    spotProp[2] = PROP_HEAP2;
                    spotProp[0] = PROP_HEAP;
                    spotProp[1] = PROP_HEAP;
                }
            }
            if (lastFree - first < 2) {
                spotProp[1] = PROP_NONE; // too short a row: keep one thing only
                if (spotProp[0] == PROP_NONE) spotProp[0] = spotProp[2];
                spotProp[2] = PROP_NONE;
            }
        }

        /** Puts dressing spot {@code i} at position {@code a} along the back row {@code row}. */
        private void setSpot(int i, boolean alongX, int row, int a) {
            spotX[i] = alongX ? a : row;
            spotZ[i] = alongX ? row : a;
        }

        /** What stands on room column (rx, rz) (PROP_*). */
        int propAt(int rx, int rz) {
            if (dressing == D_NONE) {
                return PROP_NONE;
            }
            for (int i = 0; i < 3; i++) {
                if (spotProp[i] != PROP_NONE && spotX[i] == rx && spotZ[i] == rz) {
                    return spotProp[i];
                }
            }
            return PROP_NONE;
        }

        /**
         * The room's lights: lit or switched off as a whole (by light district), and where its
         * panels go: a panel every 4 blocks in a bright district; otherwise a 2x2 group 4 apart at
         * the middle (a pair, or one panel in a small room), so the corners stay darker.
         */
        private void planLights(long seed, long h) {
            int cx = lenX / 2;
            int cz = lenZ / 2;
            district = Level0Lights.district(seed, x0 + cx, z0 + cz);
            lit = Hash.unit(h) >= ROOM_OFF[district];
            int small = Math.min(lenX, lenZ);
            if (district == Level0Lights.BRIGHT && small >= 7) {
                lights = 0;
                lightX = ((lenX - 1) % 4) / 2;
                lightZ = ((lenZ - 1) % 4) / 2;
                return;
            }
            pairAlongX = lenX >= lenZ;
            if (small < 8) {
                lights = Math.max(lenX, lenZ) >= 10 ? 2 : 3;
            } else {
                lights = Hash.unit(Hash.mix(h)) < 0.25 ? 2 : 1;
            }
            lightX = cx;
            lightZ = cz;
        }

        /** True if room column (rx, rz) has a panel over it (lit or dead). */
        boolean lightAt(int rx, int rz) {
            if (solid(rx, rz)) {
                return false;
            }
            return switch (lights) {
                case 0 -> Math.floorMod(rx - lightX, 4) == 0 && Math.floorMod(rz - lightZ, 4) == 0;
                case 1 -> (rx == lightX - 2 || rx == lightX + 2) && (rz == lightZ - 2 || rz == lightZ + 2);
                case 2 -> pairAlongX ? rz == lightZ && (rx == lightX - 2 || rx == lightX + 2)
                        : rx == lightX && (rz == lightZ - 2 || rz == lightZ + 2);
                default -> rx == lightX && rz == lightZ;
            };
        }
    }
}
