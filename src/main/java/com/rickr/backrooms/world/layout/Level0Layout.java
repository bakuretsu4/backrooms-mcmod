package com.rickr.backrooms.world.layout;

import static com.rickr.backrooms.world.layout.Level0Zones.BUILDERS;
import static com.rickr.backrooms.world.layout.Level0Zones.CORRIDOR;
import static com.rickr.backrooms.world.layout.Level0Zones.DARK;
import static com.rickr.backrooms.world.layout.Level0Zones.FIELD;
import static com.rickr.backrooms.world.layout.Level0Zones.HALL;

import com.rickr.backrooms.block.AxisShapeBlock;
import com.rickr.backrooms.block.FloorArrowBlock;
import com.rickr.backrooms.block.Level0ConduitBlock;
import com.rickr.backrooms.block.Level0EmbeddedBlock;
import com.rickr.backrooms.block.Level0GraffitiBlock;
import com.rickr.backrooms.block.Level0MannequinBlock;
import com.rickr.backrooms.block.Level0TapeBlock;
import com.rickr.backrooms.block.WallDecalBlock;
import com.rickr.backrooms.block.WallDoorDrawingBlock;
import com.rickr.backrooms.block.WallLampBlock;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.registry.Level0Blocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * Level 0, "the Lobby", as in Kane Pixels' series and the A24 film: a cramped, low-ceilinged maze
 * of yellow rooms and narrow passages under a dropped ceiling, broken up by thick wall blocks,
 * corridors, odd half-built rooms, the odd pillared hall, and whole wings where the lights are
 * dead.
 *
 * <p><b>Height.</b> Three blocks of air between carpet and ceiling (see the constructor). Nothing
 * ever hangs below the ceiling over a walkable spot (no lintels, soffits or raised floors; the Red
 * Rooms' crawlway is the one deliberate exception), so Bacteria (2.9 tall) fits everywhere a
 * player can walk; a change of level only ever goes down.
 *
 * <p><b>The skeleton.</b> Both axes are cut by {@link Bands} into wall bands (1-3 blocks thick) and
 * gaps (3-5 wide). That gives four kinds of "unit": a room (gap x gap), a wall segment (a band
 * across a gap, in either direction) and a junction (band x band). Every unit decides what it is
 * from its own hash, so neighbouring columns always agree and walls have clean, blunt ends.
 *
 * <p><b>Zones.</b> Each unit asks {@link Level0Zones} which kind of neighbourhood it is in (big
 * irregular areas 64-96 blocks across), and builds itself accordingly:
 * <ul>
 *   <li>{@link Level0Zones#HALL} pillar halls (~22%): about a third of the wall segments are
 *       missing and many more are only stubs, the junctions stay as square pillars on a loose,
 *       uneven grid, and most bays have a pillar of their own. Open, but never more than about
 *       a dozen blocks across.</li>
 *   <li>{@link Level0Zones#FIELD} wall-block fields (~38%): the classic thick-walled maze of small
 *       rooms joined by frameless 2-3 wide openings. The openings of a few parallel walls line up
 *       (they depend on the row, not the wall), so you get short vistas through them.</li>
 *   <li>{@link Level0Zones#CORRIDOR} corridor runs (~22%): long narrow corridors carved out of
 *       solid wall, 2 wide (some 3), stepping sideways every 32 blocks, with small side rooms: Kane's
 *       with a troffer every 3 blocks down the middle, the film's with one continuous light strip.
 *       Some branch, some dead-end, some go dark.</li>
 *   <li>{@link Level0Zones#BUILDERS} the film's builders' rooms (~8%): rooms 7-12 across on their
 *       own coarser grid, each with a few deliberate half-walls, window or niche series, or a
 *       step down into a sunken passage, in the chevron "Classic" paper. See
 *       {@link Level0Builders}; the ordinary skeleton never builds them itself.</li>
 *   <li>{@link Level0Zones#DARK} dark wings (~10%): an enclosed maze where every troffer is dead.
 *       Rooms just outside a wing switch off their panels near it, so the dark starts at the
 *       doorway.</li>
 * </ul>
 * Near a border between a hall and a field, units randomly take the neighbouring kind more and
 * more often, so one fades into the other over a dozen blocks.
 *
 * <p><b>Lights.</b> One square troffer every 3 blocks in both directions over open floor, the
 * rows sometimes shifted by one; about 8% dead. See {@link #roomLight} and {@link #corridorLight}.
 *
 * <p><b>Set pieces</b> (rare, by hash, see {@link #roomPiece}, {@link #roomWallPiece} and
 * {@link #solidFeature}): furniture heaps and the throne in pillar halls, exit-sign alcoves, switch
 * boxes with conduit, graffiti, teal tape lines, a ladder up to a hole in the wall, missing ceiling
 * tiles (some with a dangling cable), an abandoned hazmat case, and furniture "noclipped" into the
 * walls, floor and ceiling (see {@link #embeddedAt}). {@link Level0Finder} finds the nearest of each
 * for {@code /backrooms anomaly locate}.
 *
 * <p><b>Exit.</b> One per 512x512 region: a wooden door, recessed between green and white hazard
 * stripes under a lit EXIT sign, into a white stairwell going down.
 *
 * <p><b>Safe rooms.</b> About 60% of the 128x128 squares hide one somewhere in the maze: a cosy
 * office room behind thick walls with one doorway, and a 1-wide strip of plain floor all around it
 * so the doorway always leads back into the maze. See {@link SafeRoomPlan}.
 *
 * <p><b>Anomalies.</b> Five kinds of anomalous area, placed by {@link Level0AnomalyPlan}: the Red
 * Rooms ({@link Level0RedRooms}), Blackout Zones ({@link Level0Blackout}, the maze in place with
 * no lights, rough walls and fluid on the carpet), the Manila Room ({@link Level0ManilaRoom}), a
 * pillar hall that rearranges itself ({@link Level0PillarHall}) and Room 14D's grid of pits
 * ({@link Level0Pitfalls}).
 *
 * <p><b>Speed.</b> Every answer is computed from hashes (a few dozen per column) and packed into
 * ints, so {@link #fillColumn} allocates nothing; the zone answers are memoised by
 * {@link Level0Zones}.
 */
public final class Level0Layout extends LevelLayout {
    /** Length of the blocks the wall bands are drawn in (see {@link Bands}); each block starts with a wall band. */
    private static final int WALL_MACRO = 32;
    /**
     * Wall bands running north-south (cut along X) and east-west (cut along Z). The gaps between
     * walls (the rooms) are 3-5 wide: small enough that most spaces are cramped little rooms, so
     * the maze feels closed in, but never so narrow that two people can't pass.
     */
    static final Bands X_WALLS = new Bands(WALL_MACRO, 1, 3, 3, 5, 101);
    static final Bands Z_WALLS = new Bands(WALL_MACRO, 1, 3, 3, 5, 102);
    /** Columns within this distance of the exit (outside the stairwell) are an open lobby. */
    private static final int CLEARING_RADIUS = 8;
    private static final int ARROW_RANGE = 320;
    private static final int OFFICE_GRID = 96;
    /** Safe rooms: a free strip this wide runs all around each one, joining it to the maze. */
    private static final int SAFE_MOAT = 1;
    /** Safe rooms keep at least this far (chessboard distance from their centre) from an exit. */
    private static final int SAFE_EXIT_DISTANCE = 24;
    /** Two zone kinds mix over this many blocks on each side of their border. */
    private static final int BLEND = 12;
    /**
     * Share of single dead troffers in lit areas (darkness otherwise comes by zone). About one
     * panel in twelve is dead, so even a lit room often has a gap in its light and a dim corner.
     */
    private static final double DEAD_LIGHT = 0.08;

    // What a maze column is: the low 4 bits of a packed column (see mazeColumn).
    static final int OPEN = 0;
    static final int WALL = 1;
    static final int DOORWAY = 2;      // walkable, full height (frameless: the ceiling just carries on)
    static final int HALF = 3;         // chest-high partition with a wood cap rail
    static final int WINDOW = 4;       // wall with a frameless 2-wide slot at eye height, right through
    static final int NICHE_HIGH = 5;   // wall face with an empty 1-deep recess at eye height
    static final int NICHE_LOW = 6;    // wall face with a walk-in recess, floor to ceiling
    static final int ALCOVE = 7;       // the recess in front of an exit-sign door
    static final int ALCOVE_DOOR = 8;  // the door at the back of it
    static final int ALCOVE_FRAME = 9; // a hazard-striped jamb beside it
    static final int GRAFFITI = 10;    // wall with words smeared on one face
    static final int WALL_HOLE = 11;   // wall with a dark square hole at the top of a ladder

    // What a wall segment is.
    static final int SOLID = 0;
    private static final int DOOR = 1;
    private static final int MISSING = 2;
    private static final int STUB_LOW = 3;   // only the half touching the lower-coordinate junction
    private static final int STUB_HIGH = 4;  // only the half touching the higher-coordinate junction
    /**
     * Chances of SOLID, DOOR and MISSING per zone kind; the rest are stubs. Every kind keeps more
     * of its walls than it used to, so open space comes in small pieces: halls are now broken up by
     * wall stubs and part-walls between their pillars, and fields are mostly closed rooms joined by
     * doorways.
     */
    private static final int[] ODDS_STATES = {SOLID, DOOR, MISSING};
    private static final double[][] SEGMENT_ODDS = {
            {0.12, 0.28, 0.32}, // HALL: a third of the walls gone; pillars, stubs and wall blocks stay
            {0.26, 0.60, 0.04}, // FIELD: small rooms, nearly all joined by doorways
            {1.00, 0.00, 0.00}, // CORRIDOR (corridors are carved separately)
            {1.00, 0.00, 0.00}, // BUILDERS (never used: see Level0Builders)
            {0.24, 0.60, 0.06}, // DARK: more enclosed, so the dark starts at doorways
    };
    /** Chance that a junction no wall reaches still stands as a pillar, per zone kind. */
    private static final double[] JUNCTION_PILLAR = {1.0, 0.6, 1.0, 0.4, 0.5};

    // Features of a solid wall segment (see solidFeature).
    static final int F_NONE = 0;
    static final int F_ALCOVE = 4;
    static final int F_GRAFFITI = 5;

    // Corridor flags (bits 12+ of a packed corridor column).
    private static final int C_MAIN = 1;
    private static final int C_CROSS = 2;
    private static final int C_MAIN_CENTRE = 4;
    private static final int C_CROSS_CENTRE = 8;
    private static final int C_DIM = 16;
    private static final int C_ALONG_X = 32;
    private static final int C_FILM = 64;

    // Things stood in a room (see roomPiece / propAt).
    static final int R_NONE = 0;
    static final int R_HEAP = 1;
    static final int R_THRONE = 2;
    static final int R_HAZMAT = 4;
    static final int R_CABLE = 5;
    static final int R_HOLE = 6;
    private static final int P_NONE = 0;
    private static final int P_HEAP = 1;
    private static final int P_THRONE = 2;
    private static final int P_LITTER = 3;
    private static final int P_HAZMAT = 5;
    private static final int P_CABLE = 6;
    private static final int P_HOLE = 7;

    // Things on a room's wall (see roomWallPiece).
    static final int W_NONE = 0;
    static final int W_TAPE = 1;
    static final int W_SWITCHES = 2;
    static final int W_LADDER = 3;

    // Noclipped furniture (see embeddedAt): one chance per EMBED_CELL x EMBED_CELL square.
    private static final int EMBED_CELL = 14;
    private static final double EMBED_CHANCE = 0.8;
    private static final int MOUNT_WALL = 1;
    private static final int MOUNT_FLOOR = 2;
    private static final int MOUNT_CEILING = 3;
    /** Cached once: values() makes a new array on every call. */
    private static final Level0EmbeddedBlock.Prop[] EMBED_PROPS = Level0EmbeddedBlock.Prop.values();
    /**
     * The four horizontal directions, in the same order as Direction.Plane.HORIZONTAL. Looping over
     * that makes a new iterator object every time; this array is made once, so wall columns (which
     * look for wall drawings in all four directions) allocate nothing.
     */
    private static final Direction[] HORIZONTALS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    // The exit door's alcove (see exitAlcove).
    private static final int STAIR_DOOR = 1;
    private static final int JAMB = 2;
    private static final int MOUTH = 3;

    // What a "front office" column is.
    private static final int NO_OFFICE = -1;
    private static final int OFFICE_MOAT = 0;
    private static final int OFFICE_INSIDE = 1;
    private static final int OFFICE_WALL = 2;
    private static final int OFFICE_YELLOW_WALL = 3;
    private static final int OFFICE_DOORWAY = 4;

    /** The anomaly kinds plus the zone and set-piece names /backrooms anomaly locate can find. */
    private static final List<String> LOCATE_KINDS;

    static {
        List<String> kinds = new ArrayList<>(Level0AnomalyPlan.KINDS);
        kinds.addAll(Level0Finder.KINDS);
        LOCATE_KINDS = Collections.unmodifiableList(kinds);
    }

    /** Where the anomalous areas are (see {@link Level0AnomalyPlan}). */
    private final Level0AnomalyPlan anomalies;

    public Level0Layout() {
        // Three blocks of air between carpet (y40) and ceiling (y44): the ceiling sits just over a
        // player's head, which is what makes the Lobby feel low and oppressive. It is also exactly
        // enough for Bacteria (2.9 tall), so nothing may ever hang lower than the ceiling over a
        // walkable spot.
        super(3, new ExitLocator(512, 64, 1000));
        this.anomalies = new Level0AnomalyPlan(this);
    }

    // ==========================================================================================
    // Zones
    // ==========================================================================================

    /**
     * The zone sample of the unit whose first corner is (repX, repZ), with border mixing: close to
     * the border between a hall and a field, a unit sometimes takes the other side's kind (more
     * often the closer it is). Corridors and dark wings keep a crisp edge. The ordinary skeleton
     * never builds a builders' zone (its big rooms come from {@link Level0Builders}, which claims
     * its columns first): wherever no builders' room does, the neighbouring zone's kind fills in.
     */
    private static long unit(long seed, int repX, int repZ) {
        long sample = Level0Zones.sample(seed, repX, repZ);
        int kind = Level0Zones.kind(sample);
        int other = Level0Zones.secondKind(sample);
        if (kind == BUILDERS) {
            kind = other == BUILDERS ? HALL : other;
            other = kind;
        } else if (other == BUILDERS) {
            other = kind;
        }
        int edge = Level0Zones.edge(sample);
        if (kind != other && edge < BLEND && blends(kind) && blends(other)
                && Hash.unit(seed, repX, repZ, 516) < 0.5 * (1.0 - edge / (double) BLEND)) {
            kind = other;
        }
        return (sample & ~7L) | kind;
    }

    private static boolean blends(int kind) {
        return kind == HALL || kind == FIELD;
    }

    /** Zone kind of the unit made of spans sx and sz. */
    static int unitKind(long seed, long sx, long sz) {
        return Level0Zones.kind(unit(seed, X_WALLS.start(sx), Z_WALLS.start(sz)));
    }

    /** Zone kind of the unit containing column (x, z). */
    static int unitKindAt(long seed, int x, int z) {
        return unitKind(seed, X_WALLS.span(seed, x), Z_WALLS.span(seed, z));
    }

    // ==========================================================================================
    // Maze
    // ==========================================================================================

    /**
     * What column (x, z) of the maze is, packed: bits 0-3 the column code ({@link #OPEN},
     * {@link #WALL}, ...), bits 4-6 the zone kind, bits 8-9 a horizontal direction (the way a wall
     * feature, a partition rail, a mannequin or ramp stairs face), bits 12+ extra data (graffiti
     * variant/part, corridor flags, builders' room flags).
     */
    static int mazeColumn(long seed, int x, int z, long sx, long sz) {
        int builders = Level0Builders.column(seed, x, z);
        if (builders >= 0) {
            return builders | BUILDERS << 4;
        }
        long u = unit(seed, X_WALLS.start(sx), Z_WALLS.start(sz));
        int kind = Level0Zones.kind(u);
        boolean bandX = Bands.isBand(sx);
        boolean bandZ = Bands.isBand(sz);
        int column;
        if (kind == CORRIDOR) {
            column = corridorColumn(seed, x, z, sx, sz, Level0Zones.zoneHash(u));
        } else if (!bandX && !bandZ) {
            column = roomPillar(seed, kind, x, z, sx, sz) ? WALL : OPEN;
        } else if (bandX && !bandZ) {
            column = segmentColumn(seed, kind, sx, sz, z - Z_WALLS.start(sz), x - X_WALLS.start(sx), 0);
        } else if (!bandX) {
            column = segmentColumn(seed, kind, sz, sx, x - X_WALLS.start(sx), z - Z_WALLS.start(sz), 1);
        } else {
            column = junctionColumn(seed, kind, sx, sz);
        }
        return column | kind << 4;
    }

    private static int mazeColumn(long seed, int x, int z) {
        return mazeColumn(seed, x, z, X_WALLS.span(seed, x), Z_WALLS.span(seed, z));
    }

    static int segmentState(long seed, int kind, int wallIndex, int gapIndex, int axis) {
        if (kind == CORRIDOR) {
            return SOLID; // only asked by neighbours: corridor zones are mostly solid wall
        }
        double r = Hash.unit(seed, wallIndex, gapIndex, 200 + axis);
        double[] odds = SEGMENT_ODDS[kind];
        for (int i = 0; i < odds.length; i++) {
            r -= odds[i];
            if (r < 0) {
                return ODDS_STATES[i];
            }
        }
        return Hash.unit(seed, wallIndex, gapIndex, 202 + axis) < 0.5 ? STUB_LOW : STUB_HIGH;
    }

    /** State of the segment of X wall band {@code xWall} that spans Z gap {@code zGap}. */
    static int xSegment(long seed, long xWall, long zGap) {
        return segmentState(seed, unitKind(seed, xWall, zGap), Bands.index(xWall), Bands.index(zGap), 0);
    }

    /** State of the segment of Z wall band {@code zWall} that spans X gap {@code xGap}. */
    static int zSegment(long seed, long zWall, long xGap) {
        return segmentState(seed, unitKind(seed, xGap, zWall), Bands.index(zWall), Bands.index(xGap), 1);
    }

    /**
     * One column inside a wall segment: {@code along} runs along the wall (0 at the gap's start),
     * {@code across} through its thickness (0 at the low-coordinate face). Axis 0: a north-south
     * wall (an X band); axis 1: an east-west wall.
     */
    private static int segmentColumn(long seed, int kind, long wall, long gap, int along, int across, int axis) {
        int wallIndex = Bands.index(wall);
        int gapIndex = Bands.index(gap);
        int length = Bands.length(gap);
        int state = segmentState(seed, kind, wallIndex, gapIndex, axis);
        return switch (state) {
            case SOLID -> solidColumn(seed, kind, wall, gap, along, across, axis);
            case MISSING -> OPEN;
            case STUB_LOW -> along < length / 2 ? WALL : OPEN;
            case STUB_HIGH -> along >= length - length / 2 ? WALL : OPEN;
            default -> inDoorway(seed, wallIndex, gapIndex, length, along, axis) ? DOORWAY : WALL;
        };
    }

    /**
     * Doorway position: its width and offset depend on the gap ("row") and on a lane of 4 walls,
     * not on the individual wall, so the openings of a few neighbouring parallel walls line up into
     * the vistas Level 0 is known for, but no view runs much more than 20 blocks before a wall
     * closes it off.
     */
    private static boolean inDoorway(long seed, int wallIndex, int gapIndex, int length, int along, int axis) {
        int lane = Math.floorDiv(wallIndex, 4);
        long h = Hash.of(seed, gapIndex, lane, 210 + axis);
        double r = Hash.unit(h);
        int width = length <= 2 ? length : length >= 6 && r < 0.25 ? 4 : length >= 5 && r < 0.5 ? 3 : 2;
        int offset = Hash.range(Hash.mix(h), length - width + 1);
        return along >= offset && along < offset + width;
    }

    /** The way a wall's face looks: side 0 is the low-coordinate face, side 1 the high one. */
    static Direction faceDirection(int axis, int side) {
        if (axis == 0) {
            return side == 0 ? Direction.WEST : Direction.EAST;
        }
        return side == 0 ? Direction.NORTH : Direction.SOUTH;
    }

    /**
     * The one special thing about a solid wall segment, packed: bits 0-2 the feature (F_*), bit 3
     * which face, bits 4-11 where along the wall, bits 12-13 its width, bits 14-15 a variant.
     * <ul>
     *   <li>Rarely (1.2% of 2-thick walls) an exit-sign alcove: a door recessed one block between
     *       hazard stripes, with a lit EXIT sign above. It is a real door, but only into whatever
     *       room lies behind: a false hope, a few times commoner than real exits.</li>
     *   <li>Graffiti on one face (3%).</li>
     * </ul>
     */
    static int solidFeature(long seed, int kind, int wallIndex, int gapIndex, int axis, int length, int thickness) {
        long h = Hash.of(seed, wallIndex, gapIndex, 232 + axis);
        double r = Hash.unit(h);
        long bits = Hash.mix(h);
        int type = F_NONE;
        int width = 1;
        if (r < 0.012 && thickness == 2 && length >= 3) {
            type = F_ALCOVE;
        } else if (r >= 0.97 && length >= 3) {
            type = F_GRAFFITI;
            width = 2;
        }
        if (type == F_NONE) {
            return F_NONE;
        }
        int side = (int) (bits >>> 40) & 1;
        int offset = type == F_ALCOVE
                ? 1 + Hash.range(Hash.mix(bits), length - 2)
                : Hash.range(Hash.mix(bits), length - width + 1);
        int variant = (int) (bits >>> 44) & 3;
        return type | side << 3 | offset << 4 | width << 12 | variant << 14;
    }

    private static int solidColumn(long seed, int kind, long wall, long gap, int along, int across, int axis) {
        int length = Bands.length(gap);
        int thickness = Bands.length(wall);
        int feature = solidFeature(seed, kind, Bands.index(wall), Bands.index(gap), axis, length, thickness);
        int type = feature & 7;
        if (type != F_NONE) {
            int side = feature >>> 3 & 1;
            int offset = feature >>> 4 & 0xFF;
            int width = feature >>> 12 & 3;
            int face = side == 0 ? 0 : thickness - 1;
            Direction out = faceDirection(axis, side);
            int facing = out.get2DDataValue() << 8;
            switch (type) {
                case F_ALCOVE -> {
                    if (along == offset) return (across == face ? ALCOVE : ALCOVE_DOOR) | facing;
                    if (across == face && Math.abs(along - offset) == 1) return ALCOVE_FRAME | facing;
                }
                default -> {
                    // Graffiti, two blocks wide. Part 0 is the left half as seen from the room; the
                    // viewer's left is the face direction turned clockwise.
                    if (across == face && along >= offset && along < offset + 2) {
                        Direction left = out.getClockWise();
                        int leftStep = axis == 0 ? left.getStepZ() : left.getStepX();
                        int part = (along == offset) == (leftStep > 0) ? 1 : 0;
                        int variant = feature >>> 14 & 3;
                        return GRAFFITI | facing | (variant | part << 2) << 12;
                    }
                }
            }
            return WALL;
        }
        // A plain wall: maybe the hole a ladder in the room in front of it leads up to.
        for (int side = 0; side < 2; side++) {
            if (across == (side == 0 ? 0 : thickness - 1) && ladderHole(seed, wall, gap, axis, side, along)) {
                return WALL_HOLE | faceDirection(axis, side).get2DDataValue() << 8;
            }
        }
        return WALL;
    }

    /** True if the room on {@code side} of this wall has its ladder against it at {@code along}. */
    private static boolean ladderHole(long seed, long wall, long gap, int axis, int side, int along) {
        Bands wallBands = axis == 0 ? X_WALLS : Z_WALLS;
        long room = wallBands.span(seed, side == 0 ? wallBands.start(wall) - 1 : wallBands.end(wall));
        int piece = axis == 0 ? roomWallPiece(seed, room, gap) : roomWallPiece(seed, gap, room);
        // The room sees this wall on its east/west (axis 0) or south/north (axis 1) side.
        int roomSide = axis == 0 ? (side == 0 ? 1 : 0) : (side == 0 ? 3 : 2);
        return (piece & 3) == W_LADDER && (piece >>> 4 & 3) == roomSide && (piece >>> 8 & 0xFF) == along;
    }

    /** Where two wall bands cross: solid if a wall reaches it, otherwise maybe a lone pillar. */
    private static int junctionColumn(long seed, int kind, long sx, long sz) {
        long zBefore = Z_WALLS.span(seed, Z_WALLS.start(sz) - 1);
        long zAfter = Z_WALLS.span(seed, Z_WALLS.end(sz));
        long xBefore = X_WALLS.span(seed, X_WALLS.start(sx) - 1);
        long xAfter = X_WALLS.span(seed, X_WALLS.end(sx));
        int north = xSegment(seed, sx, zBefore);
        int south = xSegment(seed, sx, zAfter);
        int west = zSegment(seed, sz, xBefore);
        int east = zSegment(seed, sz, xAfter);
        if (reachesHighEnd(north) || reachesLowEnd(south) || reachesHighEnd(west) || reachesLowEnd(east)) {
            return WALL;
        }
        return Hash.unit(seed, Bands.index(sx), Bands.index(sz), 220) < JUNCTION_PILLAR[kind] ? WALL : OPEN;
    }

    private static boolean reachesHighEnd(int state) {
        return state == SOLID || state == DOOR || state == STUB_HIGH;
    }

    private static boolean reachesLowEnd(int state) {
        return state == SOLID || state == DOOR || state == STUB_LOW;
    }

    /** Side length of the pillar in the middle of a room (0 = none): 1x1 or 2x2 in halls, 2x2 in the biggest maze rooms. */
    private static int pillarSize(long seed, int kind, long sx, long sz) {
        int lenX = Bands.length(sx);
        int lenZ = Bands.length(sz);
        long h = Hash.of(seed, Bands.index(sx), Bands.index(sz), 230);
        if (kind == HALL) {
            // Most hall bays get a pillar (2x2 only where there is room round it), so no stretch of
            // a hall stays wide open.
            if (lenX < 4 || lenZ < 4 || Hash.unit(h) >= 0.6) {
                return 0;
            }
            return lenX >= 5 && lenZ >= 5 && Hash.unit(Hash.mix(h)) >= 0.4 ? 2 : 1;
        }
        if (kind == FIELD || kind == DARK) {
            return lenX >= 6 && lenZ >= 6 && Hash.unit(h) < 0.45 ? 2 : 0;
        }
        return 0;
    }

    private static boolean roomPillar(long seed, int kind, int x, int z, long sx, long sz) {
        int size = pillarSize(seed, kind, sx, sz);
        if (size == 0) {
            return false;
        }
        int px = X_WALLS.start(sx) + (Bands.length(sx) - size) / 2;
        int pz = Z_WALLS.start(sz) + (Bands.length(sz) - size) / 2;
        return x >= px && x < px + size && z >= pz && z < pz + size;
    }

    // ==========================================================================================
    // Corridor zones
    // ==========================================================================================

    /**
     * A corridor zone is solid wall with narrow corridors cut through it: one along most gap "rows"
     * of the zone's axis (a quarter of the rows stay solid), cross corridors along some gaps of the
     * other axis, and now and then a plug of wall where a corridor crosses a wall band (a dead
     * end). A corridor does not run dead straight: at the start of every 32-block stretch it steps
     * sideways, so you never see far down it. Now and then it opens out into a small side room
     * (the whole room-sized cell around it is left open). The zone point's hash picks the axis and
     * Kane's or the film's style.
     */
    private static int corridorColumn(long seed, int x, int z, long sx, long sz, int zoneHash) {
        boolean alongX = (zoneHash & 1) == 0;
        boolean film = (zoneHash >>> 8 & 0xFF) < 115; // about 45%
        long row = alongX ? sz : sx;
        long run = alongX ? sx : sz;
        Bands rowBands = alongX ? Z_WALLS : X_WALLS;
        Bands runBands = alongX ? X_WALLS : Z_WALLS;
        int across = alongX ? z : x;
        int along = alongX ? x : z;
        int flags = 0;
        boolean bay = false;
        if (!Bands.isBand(row) && Hash.unit(seed, Bands.index(row), zoneHash, 525) >= 0.25) {
            // Each 32-block stretch ("leg") of the corridor has its own sideways position.
            int leg = Math.floorDiv(along, WALL_MACRO);
            int strip = strip(seed, row, zoneHash ^ leg * 0x61C88647, film);
            int pos = across - rowBands.start(row);
            int from = strip & 15;
            int to = from + (strip >>> 4);
            // The wall band at the start of a leg joins the two legs: open across both of them.
            boolean jog = Bands.isBand(run) && runBands.start(run) == leg * WALL_MACRO;
            if (jog) {
                int before = strip(seed, row, zoneHash ^ (leg - 1) * 0x61C88647, film);
                from = Math.min(from, before & 15);
                to = Math.max(to, (before & 15) + (before >>> 4));
            }
            boolean plugged = Bands.isBand(run) && Hash.unit(seed, Bands.index(run), Bands.index(row), 527) < 0.05;
            if (pos >= from && pos < to && !plugged) {
                flags |= C_MAIN;
                if (!jog && pos == (strip & 15) + ((strip >>> 4) - 1) / 2) flags |= C_MAIN_CENTRE;
                if (Hash.unit(seed, Math.floorDiv(along, 24), Bands.index(row), 528) < 0.12) flags |= C_DIM;
            }
            // A small side room: the whole room-sized cell the corridor passes through stays open.
            bay = !Bands.isBand(run) && Hash.unit(seed, Bands.index(row), Bands.index(run), 530) < 0.2;
        }
        if (!Bands.isBand(run) && Hash.unit(seed, Bands.index(run), zoneHash, 526) < 0.35) {
            int strip = strip(seed, run, zoneHash ^ 0x5BD1E995, film);
            int pos = along - runBands.start(run) - (strip & 15);
            if (pos >= 0 && pos < strip >>> 4) {
                flags |= C_CROSS;
                if (pos == ((strip >>> 4) - 1) / 2) flags |= C_CROSS_CENTRE;
                if ((flags & C_MAIN) == 0 && Hash.unit(seed, Math.floorDiv(across, 24), Bands.index(run), 529) < 0.12) {
                    flags |= C_DIM;
                }
            }
        }
        if ((flags & (C_MAIN | C_CROSS)) == 0 && !bay) {
            return WALL;
        }
        if (alongX) flags |= C_ALONG_X;
        if (film) flags |= C_FILM;
        return OPEN | flags << 12;
    }

    /**
     * Where a corridor runs inside a gap: its offset from the gap's start (bits 0-3) and its width
     * (bits 4-7). Corridors are 2 wide, just enough to pass someone; a few (more often Kane's than
     * the film's) are 3.
     */
    private static int strip(long seed, long gap, int salt, boolean film) {
        int length = Bands.length(gap);
        long h = Hash.of(seed, Bands.index(gap), salt, 522);
        int width = Math.min(length, Hash.unit(h) < (film ? 0.10 : 0.25) ? 3 : 2);
        int offset = Hash.range(Hash.mix(h), length - width + 1);
        return offset | width << 4;
    }

    /**
     * Corridor lights run down the middle: Kane's a square troffer every 3 blocks (pools of light
     * with dimmer stretches between them under the low ceiling), the film's one continuous strip.
     * Some stretches (about one in eight) are dead and dark.
     */
    @Nullable
    private static BlockState corridorLight(long seed, int x, int z, int column) {
        int flags = column >>> 12;
        boolean main = (flags & C_MAIN_CENTRE) != 0;
        if (!main && (flags & C_CROSS_CENTRE) == 0) {
            return null;
        }
        boolean alongX = (flags & C_ALONG_X) != 0;
        boolean runsX = main == alongX; // a cross corridor runs the other way
        boolean dim = (flags & C_DIM) != 0;
        if ((flags & C_FILM) != 0) {
            BlockState strip = (dim ? Level0Blocks.TROFFER_STRIP_OFF : Level0Blocks.TROFFER_STRIP).get().defaultBlockState();
            return strip.setValue(AxisShapeBlock.AXIS, runsX ? Direction.Axis.X : Direction.Axis.Z);
        }
        if (Math.floorMod(runsX ? x : z, 3) != 0) {
            return null;
        }
        boolean dead = dim || Hash.unit(seed, x, z, 532) < DEAD_LIGHT;
        return (dead ? ModBlocks.FLUORESCENT_LIGHT_OFF : ModBlocks.FLUORESCENT_LIGHT).get().defaultBlockState();
    }

    // ==========================================================================================
    // Rooms: lights, ramps, set pieces
    // ==========================================================================================

    /**
     * The troffer grid: one every 3 blocks in both directions over open floor (some 24-block
     * stretches of a row shifted by one). Dead in dark wings, and in lit areas within 3 blocks of
     * one, so a wing goes black right at its doorway; otherwise {@link #DEAD_LIGHT} (9%) dead.
     */
    @Nullable
    private static BlockState roomLight(long seed, int kind, int x, int z) {
        if (!onLightGrid(seed, x, z)) {
            return null;
        }
        boolean dead = kind == DARK || Hash.unit(seed, x, z, 532) < DEAD_LIGHT || nearDarkWing(seed, x, z);
        return (dead ? ModBlocks.FLUORESCENT_LIGHT_OFF : ModBlocks.FLUORESCENT_LIGHT).get().defaultBlockState();
    }

    /** True where the troffer grid puts a panel (lit or dead), if the floor below is open. */
    private static boolean onLightGrid(long seed, int x, int z) {
        if (Math.floorMod(z, 3) != 1) {
            return false;
        }
        int shift = Hash.unit(seed, Math.floorDiv(x, 24), Math.floorDiv(z, 3), 531) < 0.2 ? 1 : 0;
        return Math.floorMod(x - shift, 3) == 1;
    }

    private static boolean nearDarkWing(long seed, int x, int z) {
        return unitKindAt(seed, x + 3, z) == DARK || unitKindAt(seed, x - 3, z) == DARK
                || unitKindAt(seed, x, z + 3) == DARK || unitKindAt(seed, x, z - 3) == DARK;
    }

    /** True if column (x, z) belongs to a builders' room or its walls (see {@link Level0Builders}). */
    private static boolean inBuilders(long seed, int x, int z) {
        return Level0Builders.column(seed, x, z) >= 0;
    }

    /**
     * True if a builders' room claims part of this ordinary room. Builders' walls are straight and
     * their rooms far bigger than this one, so whatever reaches in covers one of its corners.
     */
    private static boolean touchesBuilders(long seed, long sx, long sz) {
        int x0 = X_WALLS.start(sx);
        int x1 = X_WALLS.end(sx) - 1;
        int z0 = Z_WALLS.start(sz);
        int z1 = Z_WALLS.end(sz) - 1;
        return inBuilders(seed, x0, z0) || inBuilders(seed, x1, z0) || inBuilders(seed, x0, z1) || inBuilders(seed, x1, z1);
    }

    /**
     * The set piece standing in a room, if any (R_*): in pillar halls a heap of furniture (3% of
     * rooms 5x5 and up) or the lone throne (1%); anywhere but corridors an abandoned hazmat case
     * (1.2%), a missing ceiling tile on its own (1.5%) or one with a cable hanging out (3%).
     */
    static int roomPiece(long seed, int kind, long sx, long sz) {
        int lenX = Bands.length(sx);
        int lenZ = Bands.length(sz);
        double r = Hash.unit(seed, Bands.index(sx), Bands.index(sz), 600);
        if (r >= 0.04 && r < 0.90 || r >= 0.957) {
            return R_NONE;
        }
        int piece;
        if (r < 0.04) {
            if (kind != HALL || lenX < 5 || lenZ < 5 || pillarSize(seed, kind, sx, sz) != 0) {
                return R_NONE;
            }
            piece = r < 0.03 ? R_HEAP : R_THRONE;
        } else {
            if (pillarSize(seed, kind, sx, sz) != 0) {
                return R_NONE; // (a pillar could stand on the hazmat case's or the hole's spot)
            }
            piece = r < 0.912 ? R_HAZMAT : r < 0.927 ? R_HOLE : R_CABLE;
        }
        return touchesBuilders(seed, sx, sz) ? R_NONE : piece;
    }

    /** What of the room's set piece is on column (x, z): P_* in bits 0-3, a heap's height in bits 4+. */
    private static int propAt(long seed, int kind, int x, int z, long sx, long sz) {
        int piece = roomPiece(seed, kind, sx, sz);
        if (piece == R_NONE) {
            return P_NONE;
        }
        int x0 = X_WALLS.start(sx);
        int z0 = Z_WALLS.start(sz);
        int lenX = Bands.length(sx);
        int lenZ = Bands.length(sz);
        int d = Math.max(Math.abs(x - (x0 + lenX / 2)), Math.abs(z - (z0 + lenZ / 2)));
        switch (piece) {
            case R_HEAP -> {
                // A rough mound: two high in the middle (a gap still shows under the low ceiling),
                // lower and patchier further out.
                if (d > 2) return P_NONE;
                double r = Hash.unit(seed, x, z, 641);
                int height = d == 0 ? 2 : d == 1 ? (r < 0.45 ? 2 : 1) : (r < 0.45 ? 1 : 0);
                return height == 0 ? P_NONE : P_HEAP | height << 4;
            }
            case R_THRONE -> {
                if (d == 0) return P_THRONE;
                return d >= 2 && Hash.unit(seed, x, z, 607) < 0.08 ? P_LITTER : P_NONE;
            }
            default -> {
                long h = Hash.of(seed, Bands.index(sx), Bands.index(sz), 601);
                int px = x0 + 1 + Hash.range(h, Math.max(1, lenX - 2));
                int pz = z0 + 1 + Hash.range(Hash.mix(h), Math.max(1, lenZ - 2));
                if (x != px || z != pz) return P_NONE;
                return piece == R_HAZMAT ? P_HAZMAT : piece == R_HOLE ? P_HOLE : P_CABLE;
            }
        }
    }

    /**
     * Something on one wall of a room, packed: bits 0-1 the kind (W_*), bits 4-5 the wall (0 west,
     * 1 east, 2 north, 3 south), bits 8-15 where along it, bits 16+ how many. Only on a plain solid
     * wall. Teal tape (3.5% of rooms) runs along the wall, up it and across the ceiling; switch boxes
     * with conduit (5%); a ladder up to a hole in the wall (1.5%).
     */
    static int roomWallPiece(long seed, long sx, long sz) {
        long h = Hash.of(seed, Bands.index(sx), Bands.index(sz), 610);
        double r = Hash.unit(h);
        int type = r < 0.035 ? W_TAPE : r < 0.085 ? W_SWITCHES : r < 0.10 ? W_LADDER : W_NONE;
        if (type == W_NONE) {
            return 0;
        }
        int kind = unitKind(seed, sx, sz);
        if (kind == CORRIDOR || touchesBuilders(seed, sx, sz)) {
            return 0;
        }
        long bits = Hash.mix(h);
        int side = Hash.range(bits, 4);
        long wall;
        long gap;
        int axis;
        int segmentKind;
        if (side < 2) {
            wall = X_WALLS.span(seed, side == 0 ? X_WALLS.start(sx) - 1 : X_WALLS.end(sx));
            gap = sz;
            axis = 0;
            segmentKind = unitKind(seed, wall, sz);
        } else {
            wall = Z_WALLS.span(seed, side == 2 ? Z_WALLS.start(sz) - 1 : Z_WALLS.end(sz));
            gap = sx;
            axis = 1;
            segmentKind = unitKind(seed, sx, wall);
        }
        int length = Bands.length(gap);
        if (segmentKind == CORRIDOR || segmentState(seed, segmentKind, Bands.index(wall), Bands.index(gap), axis) != SOLID
                || solidFeature(seed, segmentKind, Bands.index(wall), Bands.index(gap), axis, length, Bands.length(wall)) != F_NONE) {
            return 0;
        }
        // The wall itself must be ordinary wall all along (not a builders' room's wall or doorway).
        Bands wallBands = axis == 0 ? X_WALLS : Z_WALLS;
        Bands gapBands = axis == 0 ? Z_WALLS : X_WALLS;
        int face = (side & 1) == 0 ? wallBands.end(wall) - 1 : wallBands.start(wall);
        int first = gapBands.start(gap);
        int last = gapBands.end(gap) - 1;
        if (axis == 0 ? inBuilders(seed, face, first) || inBuilders(seed, face, last)
                : inBuilders(seed, first, face) || inBuilders(seed, last, face)) {
            return 0;
        }
        int count = type == W_SWITCHES ? Math.min(length, 1 + Hash.range(Hash.mix(bits ^ 5), 3)) : 1;
        int offset = Hash.range(Hash.mix(bits ^ 1), length - count + 1);
        return type | side << 4 | offset << 8 | count << 16;
    }

    // ==========================================================================================
    // Front office: a rare large grey room with red alarm lamps and a drawn "fake exit"
    // ==========================================================================================

    /**
     * The office of a 96x96 grid cell, packed as (x0 offset, z0 offset, width, depth), or 0 if
     * that cell has no office.
     */
    private static long officeRect(long seed, int cellX, int cellZ) {
        long h = Hash.of(seed, cellX, cellZ, 300);
        if (Hash.unit(h) >= 0.2) {
            return 0;
        }
        // 12-16 by 9-12 with its walls: still the biggest plain room around, but no open hall.
        int width = 12 + Hash.range(Hash.mix(h ^ 1), 5);
        int depth = 9 + Hash.range(Hash.mix(h ^ 2), 4);
        int x0 = 6 + Hash.range(Hash.mix(h ^ 3), OFFICE_GRID - 12 - width);
        int z0 = 6 + Hash.range(Hash.mix(h ^ 4), OFFICE_GRID - 12 - depth);
        return 1L << 40 | (long) x0 << 24 | (long) z0 << 16 | (long) width << 8 | depth;
    }

    private int officeAt(long seed, int x, int z) {
        int cellX = Math.floorDiv(x, OFFICE_GRID);
        int cellZ = Math.floorDiv(z, OFFICE_GRID);
        long rect = officeRect(seed, cellX, cellZ);
        if (rect == 0) {
            return NO_OFFICE;
        }
        int width = (int) (rect >>> 8) & 0xFF;
        int depth = (int) rect & 0xFF;
        int rx = x - (cellX * OFFICE_GRID + ((int) (rect >>> 24) & 0xFF));
        int rz = z - (cellZ * OFFICE_GRID + ((int) (rect >>> 16) & 0xFF));
        if (rx < -2 || rx >= width + 2 || rz < -2 || rz >= depth + 2) {
            return NO_OFFICE;
        }
        // Never build an office near the exit stairwell. Measured from the office centre so every
        // column of the same office gets the same answer.
        int centerX = x - rx + width / 2;
        int centerZ = z - rz + depth / 2;
        int[] exit = exits().nearest(seed, centerX, centerZ);
        if (Math.max(Math.abs(exit[0] - centerX), Math.abs(exit[1] - centerZ)) < 48) {
            return NO_OFFICE;
        }
        return officeKind(rx, rz, width, depth);
    }

    private static int officeKind(int rx, int rz, int width, int depth) {
        if (rx < 0 || rx >= width || rz < 0 || rz >= depth) {
            return OFFICE_MOAT; // a 2-wide walkway all around, so the room is always reachable
        }
        if (rx > 0 && rx < width - 1 && rz > 0 && rz < depth - 1) {
            return OFFICE_INSIDE;
        }
        boolean southDoor = rz == depth - 1 && (rx == width / 2 - 1 || rx == width / 2);
        boolean sideDoor = (rx == 0 || rx == width - 1) && (rz == depth / 2 - 1 || rz == depth / 2);
        if (southDoor || sideDoor) {
            return OFFICE_DOORWAY;
        }
        return rz == 0 ? OFFICE_YELLOW_WALL : OFFICE_WALL;
    }

    private void fillOffice(long seed, int x, int z, int kind, Column out) {
        int floor = floorY();
        int ceiling = ceilingY();
        BlockState grey = ModBlocks.CONCRETE_WALL.get().defaultBlockState();
        out.set(floor, ModBlocks.LOBBY_CARPET.get().defaultBlockState());
        out.set(ceiling, ModBlocks.CEILING_TILE.get().defaultBlockState());

        int cellX = Math.floorDiv(x, OFFICE_GRID);
        int cellZ = Math.floorDiv(z, OFFICE_GRID);
        long rect = officeRect(seed, cellX, cellZ);
        int width = (int) (rect >>> 8) & 0xFF;
        int depth = (int) rect & 0xFF;
        int rx = x - (cellX * OFFICE_GRID + ((int) (rect >>> 24) & 0xFF));
        int rz = z - (cellZ * OFFICE_GRID + ((int) (rect >>> 16) & 0xFF));

        switch (kind) {
            case OFFICE_WALL -> {
                for (int y = floor + 1; y < ceiling; y++) {
                    out.set(y, grey);
                }
            }
            case OFFICE_YELLOW_WALL -> {
                // The one yellow wall, with the drawn door and window facing into the room.
                out.set(floor + 1, ModBlocks.YELLOW_WALLPAPER_BASEBOARD.get().defaultBlockState());
                for (int y = floor + 2; y < ceiling; y++) {
                    out.set(y, ModBlocks.YELLOW_WALLPAPER.get().defaultBlockState());
                }
                if (rx == width / 2) {
                    BlockState door = ModBlocks.WALL_DOOR_DRAWING.get().defaultBlockState().setValue(WallDecalBlock.FACING, Direction.SOUTH);
                    out.set(floor + 1, door.setValue(WallDoorDrawingBlock.HALF, DoubleBlockHalf.LOWER));
                    out.set(floor + 2, door.setValue(WallDoorDrawingBlock.HALF, DoubleBlockHalf.UPPER));
                } else if (rx == width / 2 + 2) {
                    out.set(floor + 2, ModBlocks.WALL_WINDOW_DRAWING.get().defaultBlockState().setValue(WallDecalBlock.FACING, Direction.SOUTH));
                }
            }
            case OFFICE_DOORWAY -> {
                // A plain full-height opening: under the low ceiling a lintel would leave too little headroom.
            }
            case OFFICE_INSIDE -> {
                Direction lamp = officeLamp(rx, rz, width, depth);
                if (lamp != null) {
                    // High on the wall, right under the ceiling.
                    out.set(ceiling - 1, ModBlocks.RED_ALARM_LAMP.get().defaultBlockState().setValue(WallLampBlock.FACING, lamp));
                }
                int cx = width / 2;
                int cz = depth / 2;
                if (rx == cx && rz == cz) {
                    out.set(ceiling, ModBlocks.FLUORESCENT_LIGHT.get().defaultBlockState()); // the one small white lamp
                }
                if (officeCrate(seed, x, z, rx, rz, width)) {
                    out.set(floor + 1, ModBlocks.SUPPLY_CRATE.get().defaultBlockState());
                }
            }
            default -> {
                // OFFICE_MOAT: plain dark carpet.
            }
        }
    }

    /** Red alarm lamps every 4 blocks on the grey inner walls; returns the lamp's facing or null. */
    @Nullable
    private static Direction officeLamp(int rx, int rz, int width, int depth) {
        if (rx == 1 && Math.floorMod(rz, 4) == 2 && officeKind(0, rz, width, depth) == OFFICE_WALL) return Direction.EAST;
        if (rx == width - 2 && Math.floorMod(rz, 4) == 2 && officeKind(width - 1, rz, width, depth) == OFFICE_WALL) return Direction.WEST;
        if (rz == depth - 2 && Math.floorMod(rx, 4) == 2 && officeKind(rx, depth - 1, width, depth) == OFFICE_WALL) return Direction.NORTH;
        return null;
    }

    /** A crate in each of the two corners by the yellow wall, sometimes. */
    private static boolean officeCrate(long seed, int x, int z, int rx, int rz, int width) {
        return rz == 1 && (rx == 1 || rx == width - 2) && Hash.unit(seed, x, z, 310) < 0.6;
    }

    // ==========================================================================================
    // Safe rooms
    // ==========================================================================================

    /**
     * The safe room whose footprint (the room plus the free strip around it) contains (x, z), or
     * null. Each 128x128 square has at most one, so only one square needs checking.
     */
    @Nullable
    private SafeRoomPlan safeRoomNear(long seed, int x, int z) {
        SafeRoomPlan plan = safeRoomPlan(seed, Math.floorDiv(x, SafeRoomPlan.CELL), Math.floorDiv(z, SafeRoomPlan.CELL));
        if (plan == null || !plan.near(x, z, SAFE_MOAT) || !safeRoomAllowed(seed, plan)) {
            return null;
        }
        return plan;
    }

    /**
     * The safe-room plan this thread looked up last. Every column asks for the plan of its 128x128
     * square, and worldgen fills column after column of the same square, so keeping the last answer
     * saves building a new plan object (and its hashes) for nearly every column.
     */
    private static final class SafeMemo {
        boolean valid;
        long seed;
        int cellX;
        int cellZ;
        @Nullable
        SafeRoomPlan plan;
    }

    private static final ThreadLocal<SafeMemo> SAFE_MEMO = ThreadLocal.withInitial(SafeMemo::new);

    /** Where the safe room of a 128x128 square would go (before checking it's allowed there), or null. */
    @Nullable
    private static SafeRoomPlan safeRoomPlan(long seed, int cellX, int cellZ) {
        SafeMemo memo = SAFE_MEMO.get();
        if (memo.valid && memo.seed == seed && memo.cellX == cellX && memo.cellZ == cellZ) {
            return memo.plan;
        }
        // 7x7 or 9x9 inside, with walls two blocks thick (11 or 13 across).
        int size = Hash.unit(seed, cellX, cellZ, 401) < 0.5 ? 11 : 13;
        SafeRoomPlan plan = SafeRoomPlan.jittered(seed, cellX, cellZ, 400, size, SAFE_MOAT, 2);
        memo.valid = true;
        memo.seed = seed;
        memo.cellX = cellX;
        memo.cellZ = cellZ;
        memo.plan = plan;
        return plan;
    }

    /** Never near an exit stairwell, never overlapping a front office (or the walkway around it). */
    private boolean safeRoomAllowed(long seed, SafeRoomPlan plan) {
        int[] exit = exits().nearest(seed, plan.centerX(), plan.centerZ());
        if (Math.max(Math.abs(exit[0] - plan.centerX()), Math.abs(exit[1] - plan.centerZ())) < SAFE_EXIT_DISTANCE) {
            return false;
        }
        int fx0 = plan.x0 - SAFE_MOAT;
        int fz0 = plan.z0 - SAFE_MOAT;
        int fx1 = plan.x0 + plan.sizeX - 1 + SAFE_MOAT;
        int fz1 = plan.z0 + plan.sizeZ - 1 + SAFE_MOAT;
        for (int cellX = Math.floorDiv(fx0, OFFICE_GRID); cellX <= Math.floorDiv(fx1, OFFICE_GRID); cellX++) {
            for (int cellZ = Math.floorDiv(fz0, OFFICE_GRID); cellZ <= Math.floorDiv(fz1, OFFICE_GRID); cellZ++) {
                long rect = officeRect(seed, cellX, cellZ);
                if (rect == 0) {
                    continue;
                }
                // The office plus its 2-wide walkway, as in officeAt.
                int ox0 = cellX * OFFICE_GRID + ((int) (rect >>> 24) & 0xFF) - 2;
                int oz0 = cellZ * OFFICE_GRID + ((int) (rect >>> 16) & 0xFF) - 2;
                int ox1 = ox0 + ((int) (rect >>> 8) & 0xFF) + 3;
                int oz1 = oz0 + ((int) rect & 0xFF) + 3;
                if (ox0 <= fx1 && ox1 >= fx0 && oz0 <= fz1 && oz1 >= fz0) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Everything inside a safe room, walls and doorway included. */
    @Override
    public boolean isSafeRoom(long seed, int x, int z) {
        SafeRoomPlan plan = safeRoomNear(seed, x, z);
        return plan != null && plan.contains(x, z);
    }

    private void fillSafeRoom(SafeRoomPlan plan, int x, int z, Column out) {
        BlockState tile = ModBlocks.CEILING_TILE.get().defaultBlockState();
        if (!plan.contains(x, z)) {
            // The free strip around the room: plain carpet, no lights.
            out.set(floorY(), ModBlocks.LOBBY_CARPET.get().defaultBlockState());
            out.set(ceilingY(), tile);
            return;
        }
        BlockState wallpaper = ModBlocks.YELLOW_WALLPAPER.get().defaultBlockState();
        plan.fill(x, z, floorY(), ceilingY(), new SafeRoomPlan.Style(
                ModBlocks.BEIGE_CARPET.get().defaultBlockState(),
                tile,
                ModBlocks.YELLOW_WALLPAPER_BASEBOARD.get().defaultBlockState(),
                wallpaper,
                ModBlocks.WOOD_FLOOR.get().defaultBlockState(), // wood panelling below the dado line
                wallpaper,
                null,
                false), out); // plain carpet: no round rug on Level 0
    }

    // ==========================================================================================
    // Anomalies (where they are: Level0AnomalyPlan; what they look like: the Level0* plan classes)
    // ==========================================================================================

    @Override
    @Nullable
    public AnomalySite anomalyAt(long seed, int x, int z) {
        return anomalies.at(seed, x, z);
    }

    @Override
    public void anomaliesNear(long seed, int x, int z, int radius, Consumer<AnomalySite> out) {
        anomalies.near(seed, x, z, radius, out);
    }

    /**
     * The nearest anomaly of {@code kind}, or (for the debug names in {@link Level0Finder#KINDS},
     * like {@code level_0/zone/pillar_hall} or {@code level_0/set/furniture_heap}) the nearest
     * zone or set piece of that kind, so the same command finds them.
     */
    @Override
    @Nullable
    public AnomalySite nearestAnomaly(long seed, int x, int z, String kind) {
        if (Level0Finder.KINDS.contains(kind)) {
            return Level0Finder.nearest(this, seed, x, z, kind);
        }
        return anomalies.nearest(seed, x, z, kind);
    }

    @Override
    public List<String> anomalyKinds() {
        return LOCATE_KINDS;
    }

    /** Nobody arrives (or respawns) inside an anomalous structure or on a ramp: you have to walk in. */
    @Override
    public boolean isSafeArrival(long seed, int x, int z) {
        return isWalkable(seed, x, z) && anomalies.structureAt(seed, x, z) == null && rampAt(seed, x, z) == 0;
    }

    /** One block lower in the sunken passage of a builders' room (and half a block on its step). */
    @Override
    public int standY(long seed, int x, int z) {
        int part = rampAt(seed, x, z);
        return FLOOR_Y + (part == Level0Builders.RAMP_SUNK || part == Level0Builders.RAMP_SLAB ? 0 : 1);
    }

    @Override
    public double feetY(long seed, int x, int z) {
        return rampAt(seed, x, z) == Level0Builders.RAMP_SLAB ? FLOOR_Y + 0.5 : standY(seed, x, z);
    }

    /**
     * True if a rectangle is clear of exit stairwells (and the lobby around them), safe rooms (with
     * their free strip) and front offices (with their walkway). The anomaly planner only puts sites
     * where this holds. Front offices count even where they are switched off near an exit, to keep
     * this simple and safe.
     */
    boolean rectFree(long seed, int x0, int z0, int x1, int z1) {
        int keep = CLEARING_RADIUS + 2;
        int[][] probes = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, {(x0 + x1) / 2, (z0 + z1) / 2}};
        for (int[] probe : probes) {
            int[] exit = exits().nearest(seed, probe[0], probe[1]);
            if (exit[0] >= x0 - keep && exit[0] <= x1 + keep && exit[1] >= z0 - keep && exit[1] <= z1 + keep) {
                return false;
            }
        }
        for (int cellX = Math.floorDiv(x0, SafeRoomPlan.CELL); cellX <= Math.floorDiv(x1, SafeRoomPlan.CELL); cellX++) {
            for (int cellZ = Math.floorDiv(z0, SafeRoomPlan.CELL); cellZ <= Math.floorDiv(z1, SafeRoomPlan.CELL); cellZ++) {
                SafeRoomPlan plan = safeRoomPlan(seed, cellX, cellZ);
                if (plan != null && safeRoomAllowed(seed, plan)
                        && plan.x0 - SAFE_MOAT <= x1 && plan.x0 + plan.sizeX - 1 + SAFE_MOAT >= x0
                        && plan.z0 - SAFE_MOAT <= z1 && plan.z0 + plan.sizeZ - 1 + SAFE_MOAT >= z0) {
                    return false;
                }
            }
        }
        for (int cellX = Math.floorDiv(x0, OFFICE_GRID); cellX <= Math.floorDiv(x1, OFFICE_GRID); cellX++) {
            for (int cellZ = Math.floorDiv(z0, OFFICE_GRID); cellZ <= Math.floorDiv(z1, OFFICE_GRID); cellZ++) {
                long rect = officeRect(seed, cellX, cellZ);
                if (rect == 0) {
                    continue;
                }
                // The office plus its 2-wide walkway, as in officeAt.
                int ox0 = cellX * OFFICE_GRID + ((int) (rect >>> 24) & 0xFF) - 2;
                int oz0 = cellZ * OFFICE_GRID + ((int) (rect >>> 16) & 0xFF) - 2;
                int ox1 = ox0 + ((int) (rect >>> 8) & 0xFF) + 3;
                int oz1 = oz0 + ((int) rect & 0xFF) + 3;
                if (ox0 <= x1 && ox1 >= x0 && oz0 <= z1 && oz1 >= z0) {
                    return false;
                }
            }
        }
        return true;
    }

    /** True where the column is plain maze: outside exits, offices and safe rooms (never asks the anomaly plan). */
    private boolean plainMaze(long seed, int x, int z) {
        return exits().distance(seed, x, z) > CLEARING_RADIUS && officeAt(seed, x, z) == NO_OFFICE
                && safeRoomNear(seed, x, z) == null;
    }

    /** Plain maze, and not inside an anomalous structure either (Level0Finder only reports these). */
    boolean inMaze(long seed, int x, int z) {
        return plainMaze(seed, x, z) && anomalies.structureAt(seed, x, z) == null;
    }

    /** A plain full-height maze wall outside exits, offices and safe rooms (never asks the anomaly plan: the planner uses it). */
    boolean plainMazeWall(long seed, int x, int z) {
        return (mazeColumn(seed, x, z) & 15) == WALL && plainMaze(seed, x, z);
    }

    /** Open maze floor (or a doorway) at floor level outside exits, offices and safe rooms. */
    boolean plainMazeOpen(long seed, int x, int z) {
        long sx = X_WALLS.span(seed, x);
        long sz = Z_WALLS.span(seed, z);
        int column = mazeColumn(seed, x, z, sx, sz);
        int code = column & 15;
        if (code == DOORWAY) {
            return plainMaze(seed, x, z);
        }
        return code == OPEN && rampPart(column) == 0 && plainMaze(seed, x, z);
    }

    /** The ramp piece (Level0Builders.RAMP_*) of a packed maze column, or 0. */
    private static int rampPart(int column) {
        if ((column >>> 4 & 7) != BUILDERS) {
            return 0; // the extra bits mean something else elsewhere
        }
        return column >>> 12 >>> Level0Builders.RAMP_SHIFT & 3;
    }

    /**
     * Does the spot suit an anomaly that likes {@code zone}: 0 the tight maze (wall fields), 1
     * ordinary rooms (fields and builders' rooms), 2 big halls, 3 the dark wings.
     */
    boolean zonePrefers(long seed, int x, int z, int zone) {
        int kind = Level0Zones.kindAt(seed, x, z);
        return switch (zone) {
            case 0 -> kind == FIELD;
            case 1 -> kind == FIELD || kind == BUILDERS;
            case 2 -> kind == HALL;
            default -> kind == DARK;
        };
    }

    /** True in a dark wing, where every ceiling panel is dead (see {@link Level0Zones#DARK}). */
    public boolean isUnlitZone(long seed, int x, int z) {
        return unitKindAt(seed, x, z) == DARK;
    }

    private void fillStructure(AnomalySite site, int x, int z, Column out) {
        switch (site.kind()) {
            case Level0AnomalyPlan.RED_ROOMS -> Level0RedRooms.fill(site, x, z, floorY(), ceilingY(), out);
            case Level0AnomalyPlan.MANILA_ROOM -> Level0ManilaRoom.fill(site, x, z, floorY(), ceilingY(), out);
            case Level0AnomalyPlan.PITFALLS -> Level0Pitfalls.fill(site, x, z, floorY(), ceilingY(), out);
            default -> Level0PillarHall.fill(site, x, z, floorY(), ceilingY(), out);
        }
    }

    private static boolean structureSolid(AnomalySite site, int x, int z) {
        return switch (site.kind()) {
            case Level0AnomalyPlan.RED_ROOMS -> Level0RedRooms.isSolid(site, x, z);
            case Level0AnomalyPlan.MANILA_ROOM -> Level0ManilaRoom.isSolid(site, x, z);
            case Level0AnomalyPlan.PITFALLS -> Level0Pitfalls.isSolid(site, x, z);
            default -> Level0PillarHall.isSolid(site, x, z);
        };
    }

    private static boolean structureWalkable(AnomalySite site, int x, int z) {
        return switch (site.kind()) {
            case Level0AnomalyPlan.RED_ROOMS -> Level0RedRooms.isWalkable(site, x, z);
            case Level0AnomalyPlan.MANILA_ROOM -> Level0ManilaRoom.isWalkable(site, x, z);
            case Level0AnomalyPlan.PITFALLS -> Level0Pitfalls.isWalkable(site, x, z);
            default -> Level0PillarHall.isWalkable(site, x, z);
        };
    }

    /**
     * Warning signs of the Red Rooms in the maze around them: how close (blocks) column (x, z) is to
     * a Red Rooms site, or -1 if it is out of range (see {@link Level0AnomalyPlan#RED_RING}).
     */
    private double redRoomsDistance(long seed, int x, int z) {
        AnomalySite red = anomalies.redRoomsOfSquare(seed, x, z);
        if (red == null) {
            return -1;
        }
        double d = red.distanceTo(x + 0.5, z + 0.5);
        return d <= Level0AnomalyPlan.RED_RING ? d : -1;
    }

    // ==========================================================================================
    // Column queries shared by fillColumn / isWalkable / decals
    // ==========================================================================================

    /**
     * The exit door's alcove, seen from column (x, z) at offset (dx, dz) from the exit: the door
     * column of the stairwell wall (STAIR_DOOR), the hazard-striped jambs one block out on each side
     * of it (JAMB), the mouth between them (MOUTH), or 0.
     */
    private static int exitAlcove(long seed, int x, int z, int dx, int dz) {
        int ring = Math.max(Math.abs(dx), Math.abs(dz));
        if (ring != STAIRWELL_RADIUS && ring != STAIRWELL_RADIUS + 1) {
            return 0;
        }
        // The same local frame as LevelLayout.stairwell: turned so the door is on the north side.
        Direction doorSide = stairwellDoorSide(seed, x - dx, z - dz);
        int quarterTurns = (doorSide.get2DDataValue() + 2) % 4;
        int lx = dx;
        int lz = dz;
        for (int i = 0; i < quarterTurns; i++) {
            int t = lx;
            lx = lz;
            lz = -t;
        }
        if (lz != -ring) {
            return 0;
        }
        if (ring == STAIRWELL_RADIUS) {
            return lx == 0 ? STAIR_DOOR : 0;
        }
        return lx == 0 ? MOUTH : Math.abs(lx) == 1 ? JAMB : 0;
    }

    /** True if the column is solid at eye level (walls of the maze, office or stairwell). */
    private boolean solidAtEyeLevel(long seed, int x, int z) {
        int dx = exits().dx(seed, x, z);
        int dz = exits().dz(seed, x, z);
        int d = Math.max(Math.abs(dx), Math.abs(dz));
        if (d <= STAIRWELL_RADIUS) return true;
        if (d <= CLEARING_RADIUS) return exitAlcove(seed, x, z, dx, dz) == JAMB;
        SafeRoomPlan safe = safeRoomNear(seed, x, z);
        if (safe != null) return safe.role(x, z) == SafeRoomPlan.WALL;
        int office = officeAt(seed, x, z);
        if (office != NO_OFFICE) return office == OFFICE_WALL || office == OFFICE_YELLOW_WALL;
        AnomalySite site = anomalies.structureAt(seed, x, z);
        if (site != null) return structureSolid(site, x, z);
        int code = mazeColumn(seed, x, z) & 15;
        return code == WALL || code == GRAFFITI || code == WALL_HOLE || code == ALCOVE_FRAME || code == ALCOVE_DOOR;
    }

    /**
     * A plain yellow maze wall column (where the door drawing may go): not part of an anomaly, not
     * rough blackout wall, not peeling wallpaper near the Red Rooms, not the Manila Room's exit.
     */
    private boolean isMazeWall(long seed, int x, int z) {
        return plainMazeWall(seed, x, z) && anomalies.structureAt(seed, x, z) == null
                && anomalies.blackoutAt(seed, x, z) == null && redRoomsDistance(seed, x, z) < 0
                && !anomalies.isManilaExit(seed, x, z);
    }

    /** The ramp piece at (x, z) (Level0Builders.RAMP_*), or 0 anywhere that is not a ramp. */
    private int rampAt(long seed, int x, int z) {
        int column = Level0Builders.column(seed, x, z);
        if (column < 0 || (column & 15) != OPEN || !inMaze(seed, x, z)) {
            return 0;
        }
        return column >>> 12 >>> Level0Builders.RAMP_SHIFT & 3;
    }

    @Override
    public boolean isWalkable(long seed, int x, int z) {
        int dx = exits().dx(seed, x, z);
        int dz = exits().dz(seed, x, z);
        int d = Math.max(Math.abs(dx), Math.abs(dz));
        if (d <= STAIRWELL_RADIUS) return false;
        if (d <= CLEARING_RADIUS) return exitAlcove(seed, x, z, dx, dz) != JAMB;
        SafeRoomPlan safe = safeRoomNear(seed, x, z);
        if (safe != null) {
            return !safe.contains(x, z) || safe.isWalkable(x, z);
        }
        int office = officeAt(seed, x, z);
        if (office != NO_OFFICE) {
            if (office == OFFICE_WALL || office == OFFICE_YELLOW_WALL) return false;
            if (office == OFFICE_INSIDE) {
                int cellX = Math.floorDiv(x, OFFICE_GRID);
                int cellZ = Math.floorDiv(z, OFFICE_GRID);
                long rect = officeRect(seed, cellX, cellZ);
                int width = (int) (rect >>> 8) & 0xFF;
                int rx = x - (cellX * OFFICE_GRID + ((int) (rect >>> 24) & 0xFF));
                int rz = z - (cellZ * OFFICE_GRID + ((int) (rect >>> 16) & 0xFF));
                return !officeCrate(seed, x, z, rx, rz, width);
            }
            return true;
        }
        AnomalySite site = anomalies.structureAt(seed, x, z);
        if (site != null) {
            return structureWalkable(site, x, z);
        }
        long sx = X_WALLS.span(seed, x);
        long sz = Z_WALLS.span(seed, z);
        int column = mazeColumn(seed, x, z, sx, sz);
        int code = column & 15;
        if (code == DOORWAY || code == ALCOVE || code == NICHE_LOW) return true;
        if (code != OPEN) return false;
        int kind = column >>> 4 & 7;
        if (kind == BUILDERS) {
            return (column >>> 12 & Level0Builders.MANNEQUIN) == 0;
        }
        if (kind == CORRIDOR) {
            return true;
        }
        boolean room = !Bands.isBand(sx) && !Bands.isBand(sz);
        if (room) {
            int prop = propAt(seed, kind, x, z, sx, sz) & 15;
            if (prop != P_NONE && prop != P_CABLE && prop != P_HOLE) return false;
            if (crateHeight(seed, x, z, sx, sz) != 0 || backpackFacing(seed, x, z, sx, sz) != null) return false;
        }
        // Any noclipped prop is in the way: even one hanging out of the ceiling leaves only two
        // blocks under it, too low for anything taller than a person.
        return embeddedAt(seed, x, z, kind, sx, sz) == 0;
    }

    // ==========================================================================================
    // Generation
    // ==========================================================================================

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        BlockState wallpaper = ModBlocks.YELLOW_WALLPAPER.get().defaultBlockState();
        BlockState baseboard = ModBlocks.YELLOW_WALLPAPER_BASEBOARD.get().defaultBlockState();
        BlockState carpet = ModBlocks.LOBBY_CARPET.get().defaultBlockState();
        BlockState tile = ModBlocks.CEILING_TILE.get().defaultBlockState();
        BlockState hazard = Level0Blocks.HAZARD_FRAME.get().defaultBlockState();
        int floor = floorY();
        int ceiling = ceilingY();
        int dx = exits().dx(seed, x, z);
        int dz = exits().dz(seed, x, z);
        int exitDistance = Math.max(Math.abs(dx), Math.abs(dz));

        if (stairwell(seed, dx, dz, x - dx, z - dz, wallpaper, baseboard, true, out)) {
            if (exitAlcove(seed, x, z, dx, dz) == STAIR_DOOR) {
                out.set(floor + 3, hazard); // a striped header over the door (the lit sign hangs in front)
            }
            return;
        }
        if (exitDistance <= CLEARING_RADIUS) {
            // Open, well-lit lobby around the stairwell so the door is easy to spot. The door sits in
            // an alcove between hazard-striped jambs, under a lit EXIT sign.
            out.set(floor, carpet);
            int ax = Math.abs(dx);
            int az = Math.abs(dz);
            boolean panel = (ax == 7 && (az == 3 || az == 4)) || (az == 7 && (ax == 3 || ax == 4));
            out.set(ceiling, panel ? ModBlocks.FLUORESCENT_LIGHT.get().defaultBlockState() : tile);
            int alcove = exitAlcove(seed, x, z, dx, dz);
            if (alcove == JAMB) {
                for (int y = floor + 1; y <= floor + 3; y++) {
                    out.set(y, hazard);
                }
                for (int y = floor + 4; y < ceiling; y++) {
                    out.set(y, wallpaper);
                }
            } else if (alcove == MOUTH) {
                Direction outward = stairwellDoorSide(seed, x - dx, z - dz);
                out.set(floor + 3, Level0Blocks.EXIT_SIGN.get().defaultBlockState().setValue(WallMountBlock.FACING, outward));
                for (int y = floor + 4; y < ceiling; y++) {
                    out.set(y, hazard);
                }
            }
            return;
        }
        SafeRoomPlan safe = safeRoomNear(seed, x, z);
        if (safe != null) {
            fillSafeRoom(safe, x, z, out);
            return;
        }
        int office = officeAt(seed, x, z);
        if (office != NO_OFFICE) {
            fillOffice(seed, x, z, office, out);
            return;
        }
        AnomalySite structure = anomalies.structureAt(seed, x, z);
        if (structure != null) {
            fillStructure(structure, x, z, out);
            return;
        }

        long sx = X_WALLS.span(seed, x);
        long sz = Z_WALLS.span(seed, z);
        int column = mazeColumn(seed, x, z, sx, sz);
        int code = column & 15;
        int kind = column >>> 4 & 7;
        Direction facing = Direction.from2DDataValue(column >>> 8 & 3);
        // A blackout zone is the same maze with no lights, rough dark walls and fluid on the carpet.
        boolean blackout = anomalies.blackoutAt(seed, x, z) != null;
        // Near the Red Rooms the wallpaper peels off crimson and the carpet turns sticky, more and
        // more of it the closer you get.
        double red = blackout ? -1 : redRoomsDistance(seed, x, z);
        BlockState rough = ModBlocks.BLACKOUT_WALLPAPER.get().defaultBlockState();
        BlockState fluid = ModBlocks.CARPET_FLUID.get().defaultBlockState();
        // The film's builders' rooms have the chevron "Classic" paper; everywhere else Kane's.
        BlockState paper = blackout ? rough : kind == BUILDERS ? Level0Blocks.CHEVRON_WALLPAPER.get().defaultBlockState() : wallpaper;
        BlockState base = blackout ? rough : kind == BUILDERS ? Level0Blocks.CHEVRON_WALLPAPER_BASEBOARD.get().defaultBlockState() : baseboard;
        out.set(ceiling, tile);
        out.set(floor, carpet);
        switch (code) {
            case WALL, GRAFFITI, WALL_HOLE -> {
                fillWall(seed, x, z, code, column, facing, blackout, red, paper, base, out);
                return;
            }
            case ALCOVE_FRAME -> {
                for (int y = floor + 1; y <= floor + 3; y++) {
                    out.set(y, hazard);
                }
                fillUp(floor + 4, ceiling, paper, out);
                return;
            }
            case WINDOW, NICHE_HIGH -> {
                // A frameless slot at eye height (right through the wall, or one block deep), one
                // block tall so that wall still shows above it under the low ceiling.
                out.set(floor + 1, base);
                fillUp(floor + 3, ceiling, paper, out);
                return;
            }
            case NICHE_LOW -> {
                fillUp(floor + 4, ceiling, paper, out);
                return;
            }
            case HALF -> {
                // A builders' room partition: wallpaper below, a dark wood cap rail on top.
                out.set(floor + 1, blackout ? rough : baseboard);
                out.set(floor + 2, Level0Blocks.HALF_WALL_CAP.get().defaultBlockState().setValue(WallMountBlock.FACING, facing));
                return;
            }
            case ALCOVE -> {
                // The lit EXIT sign hangs on the wall above the door, right under the low ceiling
                // (on a taller level a hazard-striped header would fill the space above it).
                out.set(floor + 3, Level0Blocks.EXIT_SIGN.get().defaultBlockState().setValue(WallMountBlock.FACING, facing));
                fillUp(floor + 4, ceiling, hazard, out);
                return;
            }
            case ALCOVE_DOOR -> {
                BlockState door = ModBlocks.LOBBY_DOOR.get().defaultBlockState()
                        .setValue(DoorBlock.FACING, facing)
                        .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                        .setValue(DoorBlock.OPEN, false);
                out.set(floor + 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                out.set(floor + 2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                fillUp(floor + 3, ceiling, paper, out);
                return;
            }
            default -> {
                // OPEN or DOORWAY, below.
            }
        }
        boolean sticky = red >= 0 && Hash.unit(seed, x, z, 3291) < 1.0 - red / 7.0;
        BlockState stickyCarpet = ModBlocks.STICKY_CARPET.get().defaultBlockState();
        if (code == DOORWAY) {
            // Full height and frameless: a lintel under the low ceiling would leave too little
            // headroom, so the ceiling just carries on through every opening.
            out.set(floor, sticky ? stickyCarpet : carpet);
            if (blackout) {
                out.set(floor + 1, fluid);
            }
            return;
        }
        if (kind == BUILDERS) {
            fillBuilders(seed, x, z, column, blackout, sticky ? stickyCarpet : carpet, out);
            return;
        }
        if (kind == CORRIDOR) {
            out.set(floor, sticky ? stickyCarpet : carpet);
            if (blackout) {
                out.set(floor + 1, fluid);
                return;
            }
            BlockState light = corridorLight(seed, x, z, column);
            if (light != null) {
                out.set(ceiling, light);
            }
            return;
        }
        boolean room = !Bands.isBand(sx) && !Bands.isBand(sz);
        if (!room) {
            // Open floor where a wall segment or junction is missing: carpet, the light grid, and
            // now and then a piece of furniture fused into a pillar, the floor or the ceiling.
            out.set(floor, sticky ? stickyCarpet : carpet);
            if (blackout) {
                out.set(floor + 1, fluid);
                return;
            }
            BlockState light = roomLight(seed, kind, x, z);
            if (light != null) {
                out.set(ceiling, light);
            }
            placeEmbedded(seed, x, z, kind, sx, sz, out);
            return;
        }
        fillRoom(seed, kind, x, z, sx, sz, sticky ? stickyCarpet : carpet, blackout, out);
    }

    private static void fillUp(int from, int ceiling, BlockState state, Column out) {
        for (int y = from; y < ceiling; y++) {
            out.set(y, state);
        }
    }

    /** A full-height wall column, with its decal, graffiti or ladder hole. */
    private void fillWall(long seed, int x, int z, int code, int column, Direction facing, boolean blackout, double red,
                          BlockState paper, BlockState base, Column out) {
        int floor = floorY();
        int ceiling = ceilingY();
        if (code == WALL && anomalies.isManilaExit(seed, x, z)) {
            // The flickering wall the Manila Room's volunteers write about: walk into it to reach Level 1.
            for (int y = floor + 1; y <= floor + 3; y++) {
                out.set(y, ModBlocks.FLICKERING_WALL.get().defaultBlockState());
            }
            fillUp(floor + 4, ceiling, paper, out);
            return;
        }
        if (blackout) {
            fillUp(floor + 1, ceiling, paper, out);
            return;
        }
        boolean peeling = red >= 0 && Hash.unit(seed, x, z, 3290) < 1.0 - red / (Level0AnomalyPlan.RED_RING + 1);
        out.set(floor + 1, base);
        fillUp(floor + 2, ceiling, peeling ? ModBlocks.PEELING_WALLPAPER.get().defaultBlockState() : paper, out);
        if (peeling) {
            return;
        }
        if (code == GRAFFITI) {
            int extra = column >>> 12;
            out.set(floor + 2, Level0Blocks.GRAFFITI.get().defaultBlockState()
                    .setValue(WallDecalBlock.FACING, facing)
                    .setValue(Level0GraffitiBlock.VARIANT, extra & 3)
                    .setValue(Level0GraffitiBlock.PART, extra >>> 2 & 1));
        } else if (code == WALL_HOLE) {
            out.set(ceiling - 1, Level0Blocks.WALL_HOLE.get().defaultBlockState().setValue(WallDecalBlock.FACING, facing));
        } else {
            drawDecals(seed, x, z, out);
        }
    }

    /**
     * An open column of a builders' room (see {@link Level0Builders}): the step and floor of the
     * sunken passage, a mannequin, and the light grid.
     */
    private void fillBuilders(long seed, int x, int z, int column, boolean blackout, BlockState carpet, Column out) {
        int floor = floorY();
        int ceiling = ceilingY();
        int extra = column >>> 12;
        Direction facing = Direction.from2DDataValue(column >>> 8 & 3);
        int part = extra >>> Level0Builders.RAMP_SHIFT & 3;
        boolean mannequin = (extra & Level0Builders.MANNEQUIN) != 0;
        if (part == Level0Builders.RAMP_SLAB) {
            // The step down: a carpet slab where the floor would be, half a block lower.
            out.set(floor, Level0Blocks.CARPET_SLAB.get().defaultBlockState());
        } else if (part == Level0Builders.RAMP_SUNK) {
            // The sunken passage: the carpet one block down, so it keeps a full 4 blocks of headroom.
            out.set(floor, Blocks.AIR.defaultBlockState());
            out.set(floor - 1, carpet);
        } else {
            out.set(floor, carpet);
        }
        if (mannequin) {
            BlockState state = Level0Blocks.MANNEQUIN.get().defaultBlockState().setValue(WallMountBlock.FACING, facing);
            out.set(floor + 1, state.setValue(Level0MannequinBlock.HALF, DoubleBlockHalf.LOWER));
            out.set(floor + 2, state.setValue(Level0MannequinBlock.HALF, DoubleBlockHalf.UPPER));
        }
        if (blackout) {
            if (part == 0 && !mannequin) {
                out.set(floor + 1, ModBlocks.CARPET_FLUID.get().defaultBlockState());
            }
            return;
        }
        BlockState light = roomLight(seed, BUILDERS, x, z);
        if (light != null) {
            out.set(ceiling, light);
        }
    }

    /** An open column inside a room: floor, supplies, set pieces, wall pieces, noclipped furniture and lights. */
    private void fillRoom(long seed, int kind, int x, int z, long sx, long sz, BlockState carpet, boolean blackout, Column out) {
        int floor = floorY();
        int ceiling = ceilingY();
        out.set(floor, floorBlock(seed, x, z, sx, sz, carpet));
        int prop = propAt(seed, kind, x, z, sx, sz);
        int propType = prop & 15;
        boolean blocked = propType != P_NONE && propType != P_CABLE && propType != P_HOLE;
        boolean floorUsed = blocked;
        if (blocked) {
            placeProp(seed, x, z, sx, sz, prop, out);
        } else {
            int crates = crateHeight(seed, x, z, sx, sz);
            for (int i = 0; i < crates; i++) {
                out.set(floor + 1 + i, ModBlocks.SUPPLY_CRATE.get().defaultBlockState());
            }
            Direction backpack = backpackFacing(seed, x, z, sx, sz);
            if (backpack != null) {
                out.set(floor + 1, ModBlocks.BACKPACK.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, backpack));
            }
            floorUsed = crates > 0 || backpack != null;
        }
        if (blackout) {
            if (!floorUsed) {
                out.set(floor + 1, ModBlocks.CARPET_FLUID.get().defaultBlockState()); // ankle-deep, murky
            }
            return; // and not a single working lamp
        }
        BlockState light = null;
        boolean hole = propType == P_CABLE || propType == P_HOLE;
        if (hole) {
            // A missing ceiling tile: a dark recess, sometimes with a loose cable hanging out of it.
            out.set(ceiling, Level0Blocks.CEILING_HOLE.get().defaultBlockState());
            if (propType == P_CABLE) {
                out.set(ceiling - 1, Level0Blocks.DANGLING_CABLE.get().defaultBlockState()
                        .setValue(WallMountBlock.FACING, Direction.from2DDataValue(Hash.range(Hash.of(seed, x, z, 602), 4))));
            }
        } else {
            light = roomLight(seed, kind, x, z);
            if (light != null) {
                out.set(ceiling, light);
            }
        }
        placeWallPieces(seed, x, z, sx, sz, light != null || hole, floorUsed, out);
        placeEmbedded(seed, x, z, kind, sx, sz, out);
    }

    /** Puts down the part of a room's set piece that stands on (x, z). */
    private void placeProp(long seed, int x, int z, long sx, long sz, int prop, Column out) {
        int floor = floorY();
        long room = Hash.of(seed, Bands.index(sx), Bands.index(sz), 603);
        Direction roomFacing = Direction.from2DDataValue(Hash.range(room, 4));
        switch (prop & 15) {
            case P_HEAP -> {
                int height = prop >>> 4;
                for (int i = 0; i < height; i++) {
                    out.set(floor + 1 + i, Level0Props.heapPiece(seed, x, floor + 1 + i, z));
                }
            }
            case P_THRONE -> out.set(floor + 1, Level0Blocks.THRONE.get().defaultBlockState().setValue(WallMountBlock.FACING, roomFacing));
            case P_LITTER -> out.set(floor + 1, Level0Props.litter(seed, x, z));
            default -> out.set(floor + 1, Level0Blocks.HAZMAT_CASE.get().defaultBlockState()
                    .setValue(WallMountBlock.FACING, Direction.from2DDataValue(Hash.range(Hash.of(seed, x, z, 608), 4))));
        }
    }

    /**
     * The room's wall piece (see {@link #roomWallPiece}) where it touches column (x, z): tape along
     * the wall, up it and across the ceiling; switch boxes with conduit; a ladder.
     *
     * @param ceilingBusy true if the ceiling here has a light or a hole (no tape on it)
     * @param floorUsed   true if something already stands on the floor here (no ladder or switches)
     */
    private void placeWallPieces(long seed, int x, int z, long sx, long sz, boolean ceilingBusy, boolean floorUsed, Column out) {
        int piece = roomWallPiece(seed, sx, sz);
        if (piece == 0) {
            return;
        }
        int floor = floorY();
        int ceiling = ceilingY();
        int type = piece & 3;
        int side = piece >>> 4 & 3;
        int offset = piece >>> 8 & 0xFF;
        int count = piece >>> 16 & 0xFF;
        int x0 = X_WALLS.start(sx);
        int z0 = Z_WALLS.start(sz);
        Direction away = side == 0 ? Direction.EAST : side == 1 ? Direction.WEST : side == 2 ? Direction.SOUTH : Direction.NORTH;
        boolean edge = side == 0 ? x == x0 : side == 1 ? x == X_WALLS.end(sx) - 1 : side == 2 ? z == z0 : z == Z_WALLS.end(sz) - 1;
        int along = side < 2 ? z - z0 : x - x0;
        switch (type) {
            case W_TAPE -> {
                BlockState tape = Level0Blocks.TAPE.get().defaultBlockState();
                if (edge) {
                    out.set(floor + 2, tape.setValue(Level0TapeBlock.FACING, away).setValue(Level0TapeBlock.SHAPE, Level0TapeBlock.Shape.HORIZONTAL));
                    if (along == offset) {
                        BlockState up = tape.setValue(Level0TapeBlock.FACING, away).setValue(Level0TapeBlock.SHAPE, Level0TapeBlock.Shape.VERTICAL);
                        for (int y = floor + 3; y < ceiling - 1; y++) {
                            out.set(y, up);
                        }
                        out.set(ceiling - 1, ceilingBusy ? up : tape.setValue(Level0TapeBlock.FACING, away).setValue(Level0TapeBlock.SHAPE, Level0TapeBlock.Shape.CORNER));
                    }
                } else if (along == offset && !ceilingBusy) {
                    // ...and on across the ceiling, straight out from the wall.
                    out.set(ceiling - 1, tape.setValue(Level0TapeBlock.FACING, Direction.DOWN)
                            .setValue(Level0TapeBlock.SHAPE, side < 2 ? Level0TapeBlock.Shape.HORIZONTAL : Level0TapeBlock.Shape.VERTICAL));
                }
            }
            case W_SWITCHES -> {
                if (edge && !floorUsed && along >= offset && along < offset + count) {
                    out.set(floor + 2, Level0Blocks.SWITCH_BOX.get().defaultBlockState().setValue(WallMountBlock.FACING, away));
                    BlockState conduit = Level0Blocks.CONDUIT.get().defaultBlockState()
                            .setValue(WallMountBlock.FACING, away).setValue(Level0ConduitBlock.VERTICAL, true);
                    out.set(floor + 1, conduit);
                    if (along == offset) {
                        fillUp(floor + 3, ceiling, conduit, out); // the feed comes down from the ceiling
                    }
                }
            }
            case W_LADDER -> {
                if (edge && !floorUsed && along == offset) {
                    BlockState ladder = Level0Blocks.LADDER.get().defaultBlockState().setValue(LadderBlock.FACING, away);
                    for (int y = floor + 1; y < ceiling - 1; y++) {
                        out.set(y, ladder);
                    }
                }
            }
            default -> {
            }
        }
    }

    // ==========================================================================================
    // Noclipped furniture
    // ==========================================================================================

    /** "No spot": past the last spot of a square. Never a packed spot (those leave bit 63 clear). */
    private static final long NO_SPOT = Long.MIN_VALUE;
    /** Share of spots moved onto a nearby wall or pillar face (where they are seen from afar). */
    private static final double WALL_BIAS = 0.3;
    /** Of the spots left in the open: this share sink into the floor, the rest hang from the ceiling. */
    private static final double FLOOR_SHARE = 0.8;

    /** A spot packed as x (bits 31-61), z (bits 0-30) and "against a wall" (bit 62). */
    private static long packSpot(int x, int z, boolean wall) {
        return (long) (x & 0x7FFFFFFF) << 31 | (z & 0x7FFFFFFFL) | (wall ? 1L << 62 : 0L);
    }

    private static int spotX(long spot) {
        return (int) (spot >>> 31 & 0x7FFFFFFF) << 1 >> 1;
    }

    private static int spotZ(long spot) {
        return (int) (spot & 0x7FFFFFFF) << 1 >> 1;
    }

    private static boolean spotOnWall(long spot) {
        return (spot >>> 62 & 1) != 0;
    }

    /**
     * Raw spot {@code k} of the noclipped furniture of embed square (cellX, cellZ) with hash
     * {@code h}, or {@link #NO_SPOT} past the last one. Spot 0 lies 2+ blocks inside the square;
     * three squares in ten add 1-3 more within 2 blocks of it (a little pile that phased through
     * together).
     */
    private static long embedSpot(long h, int cellX, int cellZ, int k) {
        int ax = cellX * EMBED_CELL + 2 + Hash.range(Hash.mix(h), EMBED_CELL - 4);
        int az = cellZ * EMBED_CELL + 2 + Hash.range(Hash.mix(h ^ 1), EMBED_CELL - 4);
        int count = Hash.unit(Hash.mix(h ^ 2)) < 0.3 ? 2 + Hash.range(Hash.mix(h ^ 3), 3) : 1;
        if (k >= count) {
            return NO_SPOT;
        }
        if (k > 0) {
            long hk = Hash.mix(h + k * 0x9E3779B97F4A7C15L);
            ax += Hash.range(hk, 5) - 2;
            az += Hash.range(Hash.mix(hk), 5) - 2;
        }
        return packSpot(ax, az, false);
    }

    /** Per-thread memo of {@link #spotTarget}: moving a spot looks at a dozen columns around it. */
    private static final class SpotMemo {
        long seed;
        final long[] keys = new long[256];
        final long[] values = new long[256];

        SpotMemo() {
            java.util.Arrays.fill(keys, NO_SPOT);
        }
    }

    private static final ThreadLocal<SpotMemo> SPOT_MEMO = ThreadLocal.withInitial(SpotMemo::new);

    /**
     * Where spot {@code k} really goes: half the spots ({@link #WALL_BIAS}) move to the nearest
     * open column within 2 blocks (and inside their square) that stands against a wall or pillar
     * face, searched straight out from the raw spot in a hashed order, and are then marked
     * "against a wall"; the rest stay where they are.
     */
    private long spotTarget(long seed, long h, int cellX, int cellZ, int k) {
        long raw = embedSpot(h, cellX, cellZ, k);
        if (raw == NO_SPOT) {
            return NO_SPOT;
        }
        SpotMemo memo = SPOT_MEMO.get();
        if (memo.seed != seed) {
            java.util.Arrays.fill(memo.keys, NO_SPOT);
            memo.seed = seed;
        }
        long key = Hash.mix(h + k) & ~NO_SPOT;
        int slot = (int) key & 255;
        if (memo.keys[slot] == key) {
            return memo.values[slot];
        }
        long target = raw;
        long hk = Hash.mix(h ^ (k + 11) * 0x632BE59BD9B4E019L);
        if (Hash.unit(hk) < WALL_BIAS) {
            int rx = spotX(raw);
            int rz = spotZ(raw);
            int start = Hash.range(Hash.mix(hk), 4);
            search:
            for (int step = 0; step <= 2; step++) {
                for (int i = 0; i < (step == 0 ? 1 : 4); i++) {
                    Direction d = Direction.from2DDataValue((start + i) & 3);
                    int x = rx + d.getStepX() * step;
                    int z = rz + d.getStepZ() * step;
                    boolean inSquare = Math.floorDiv(x, EMBED_CELL) == cellX && Math.floorDiv(z, EMBED_CELL) == cellZ;
                    if (inSquare && wallFace(seed, x, z) != null) {
                        target = packSpot(x, z, true);
                        break search;
                    }
                }
            }
        }
        memo.keys[slot] = key;
        memo.values[slot] = target;
        return target;
    }

    /**
     * If (x, z) is open hall or field floor against a plain wall or pillar, the direction of that
     * wall (the first in a hashed order), else null.
     */
    @Nullable
    private Direction wallFace(long seed, int x, int z) {
        int column = mazeColumn(seed, x, z);
        int kind = column >>> 4 & 7;
        if ((column & 15) != OPEN || (kind != HALL && kind != FIELD)) {
            return null;
        }
        int start = Hash.range(Hash.of(seed, x, z, 721), 4);
        for (int i = 0; i < 4; i++) {
            Direction d = Direction.from2DDataValue((start + i) & 3);
            if (plainMazeWall(seed, x + d.getStepX(), z + d.getStepZ())) {
                return d;
            }
        }
        return null;
    }

    /** True if (x, z) is where one of the square's spots ends up. */
    private boolean isSpot(long seed, long h, int cellX, int cellZ, int x, int z) {
        for (int k = 0; k < 4; k++) {
            long raw = embedSpot(h, cellX, cellZ, k);
            if (raw == NO_SPOT) {
                return false;
            }
            if (Math.abs(spotX(raw) - x) <= 2 && Math.abs(spotZ(raw) - z) <= 2) {
                long target = spotTarget(seed, h, cellX, cellZ, k);
                if (spotX(target) == x && spotZ(target) == z) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Furniture that has "noclipped" into the building (block/Level0EmbeddedBlock): a chair half
     * sunk into a wall or pillar, a table halfway through the floor, a bookshelf hanging out of the
     * ceiling. Pillar halls and wall fields only: eight in ten 14x14 squares have one spot (or a
     * little pile of 2-4, see {@link #embedSpot}). Some spots move onto a nearby wall or pillar
     * face, where they are seen from across the hall; the others sink into the floor or, now and
     * then, hang from the ceiling. Roughly wall 40%, floor 45%, ceiling 15%, about one prop per 270
     * floor blocks.
     *
     * <p>It never blocks the way: a floor prop needs two clear blocks all round, a wall prop two
     * clear blocks in front, neither may stand beside a doorway or right in front of another prop,
     * and a spot that fails is simply left empty. A ceiling prop only hangs from a plain tile, never
     * a troffer. It keeps clear of the room's other set pieces, supplies and wall pieces (so nothing
     * hangs above a tall prop), and of blackouts.
     *
     * @return 0, or the mount (bits 0-1, MOUNT_*), facing (bits 2-3), prop (bits 4-7) and, for a
     *     wall prop sticking out one block higher (a lamp or a television), bit 8
     */
    private int embeddedAt(long seed, int x, int z, int kind, long sx, long sz) {
        if (kind != HALL && kind != FIELD) {
            return 0;
        }
        int cellX = Math.floorDiv(x, EMBED_CELL);
        int cellZ = Math.floorDiv(z, EMBED_CELL);
        long h = Hash.of(seed, cellX, cellZ, 720);
        if (Hash.unit(h) >= EMBED_CHANCE) {
            return 0;
        }
        long target = NO_SPOT;
        for (int k = 0; k < 4; k++) {
            long raw = embedSpot(h, cellX, cellZ, k);
            if (raw == NO_SPOT) {
                break;
            }
            if (Math.abs(spotX(raw) - x) <= 2 && Math.abs(spotZ(raw) - z) <= 2) {
                long t = spotTarget(seed, h, cellX, cellZ, k);
                if (spotX(t) == x && spotZ(t) == z) {
                    target = t;
                    break;
                }
            }
        }
        if (target == NO_SPOT || anomalies.blackoutAt(seed, x, z) != null) {
            return 0;
        }
        if (!Bands.isBand(sx) && !Bands.isBand(sz)
                && (propAt(seed, kind, x, z, sx, sz) != P_NONE || crateHeight(seed, x, z, sx, sz) != 0
                || backpackFacing(seed, x, z, sx, sz) != null || roomWallPiece(seed, sx, sz) != 0)) {
            return 0;
        }
        long p = Hash.of(seed, x, z, 722);
        int mount;
        Direction facing;
        if (spotOnWall(target)) {
            mount = MOUNT_WALL;
            facing = wallFace(seed, x, z).getOpposite(); // away from the wall it is fused with
        } else {
            mount = Hash.unit(p) < FLOOR_SHARE ? MOUNT_FLOOR : MOUNT_CEILING;
            facing = Direction.from2DDataValue(Hash.range(Hash.mix(p ^ 1), 4));
        }
        if (mount == MOUNT_CEILING) {
            if (onLightGrid(seed, x, z)) {
                return 0;
            }
        } else if (besideDoorway(seed, x, z) || !clearAhead(seed, x, z, facing, mount == MOUNT_FLOOR)) {
            return 0;
        }
        // The full-size props reach about a block into the air beside them (and the tall wall ones
        // into the block above, which in this column is always air): keep every horizontal
        // neighbour clear, except the wall a wall prop is fused with.
        if (!airAround(seed, x, z, mount, facing)) {
            return 0;
        }
        int prop = Hash.range(Hash.mix(p ^ 2), EMBED_PROPS.length);
        Level0EmbeddedBlock.Prop piece = EMBED_PROPS[prop];
        boolean high = mount == MOUNT_WALL && (piece == Level0EmbeddedBlock.Prop.LAMP || piece == Level0EmbeddedBlock.Prop.CRT_TV)
                && Hash.unit(Hash.mix(p ^ 3)) < 0.5;
        return mount | facing.get2DDataValue() << 2 | prop << 4 | (high ? 1 << 8 : 0);
    }

    /** True if a doorway (or a walk-in recess) is right next to (x, z). */
    private boolean besideDoorway(long seed, int x, int z) {
        for (Direction d : HORIZONTALS) {
            int code = mazeColumn(seed, x + d.getStepX(), z + d.getStepZ()) & 15;
            if (code == DOORWAY || code == ALCOVE || code == NICHE_LOW) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if the two blocks in front of (x, z) are clear (all four ways round when
     * {@code allRound}): no wall or pillar, so even the bigger props leave at least a 2-wide way
     * past.
     */
    private boolean clearAhead(long seed, int x, int z, Direction facing, boolean allRound) {
        for (int i = 0; i < 4; i++) {
            Direction d = allRound ? Direction.from2DDataValue(i) : facing;
            for (int step = 1; step <= 2; step++) {
                if (solidAtEyeLevel(seed, x + d.getStepX() * step, z + d.getStepZ() * step)) {
                    return false;
                }
            }
            if (!allRound) {
                break;
            }
        }
        return true;
    }

    /**
     * True if all 8 neighbours of (x, z) are clear air at the prop's height: no wall or pillar, no
     * other set piece, crate or backpack, and no other noclipped prop. A wall prop skips the wall
     * it is fused with; beside that wall (round a pillar's corner) it still checks for props, since
     * two props on neighbouring faces of a pillar would meet at the corner. Of two props that would
     * touch, the one with the lower hash gives way, so one of them always stays. A ceiling prop also
     * needs plain open hall or field around it (no tape or hanging cable).
     */
    private boolean airAround(long seed, int x, int z, int mount, Direction facing) {
        long mine = Hash.of(seed, x, z, 723);
        boolean ceiling = mount == MOUNT_CEILING;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int nx = x + dx;
                int nz = z + dz;
                if (mount == MOUNT_WALL && dx * facing.getStepX() + dz * facing.getStepZ() < 0) {
                    // On the wall's side: the wall itself, or the floor round the corner of a pillar.
                    if (propOutranks(seed, nx, nz, mine)) {
                        return false;
                    }
                    continue;
                }
                if (solidAtEyeLevel(seed, nx, nz) || cluttered(seed, nx, nz, ceiling) || propOutranks(seed, nx, nz, mine)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** True if something stands at (x, z) a prop must not touch (see {@link #airAround}). */
    private boolean cluttered(long seed, int x, int z, boolean ceiling) {
        long sx = X_WALLS.span(seed, x);
        long sz = Z_WALLS.span(seed, z);
        int column = mazeColumn(seed, x, z, sx, sz);
        int code = column & 15;
        int kind = column >>> 4 & 7;
        if (code != OPEN) {
            // Doorways are open floor to ceiling; anything else is in the way.
            return code != DOORWAY;
        }
        if (kind == BUILDERS) {
            return ceiling || (column >>> 12 & Level0Builders.MANNEQUIN) != 0;
        }
        if (kind == CORRIDOR || Bands.isBand(sx) || Bands.isBand(sz)) {
            return false;
        }
        int prop = propAt(seed, kind, x, z, sx, sz) & 15;
        if (prop != P_NONE && (ceiling || prop != P_CABLE && prop != P_HOLE)) {
            return true;
        }
        if (ceiling && (roomWallPiece(seed, sx, sz) & 3) == W_TAPE) {
            return true;
        }
        return crateHeight(seed, x, z, sx, sz) != 0 || backpackFacing(seed, x, z, sx, sz) != null;
    }

    /** True if another noclipped prop's spot is at (x, z) and outranks the prop with hash {@code mine}. */
    private boolean propOutranks(long seed, int x, int z, long mine) {
        int cellX = Math.floorDiv(x, EMBED_CELL);
        int cellZ = Math.floorDiv(z, EMBED_CELL);
        long h = Hash.of(seed, cellX, cellZ, 720);
        return Hash.unit(h) < EMBED_CHANCE && isSpot(seed, h, cellX, cellZ, x, z) && Hash.of(seed, x, z, 723) > mine;
    }

    /** Puts down the noclipped prop of column (x, z), if it has one. */
    private void placeEmbedded(long seed, int x, int z, int kind, long sx, long sz, Column out) {
        int embedded = embeddedAt(seed, x, z, kind, sx, sz);
        if (embedded == 0) {
            return;
        }
        int mount = embedded & 3;
        Level0EmbeddedBlock.Mount fused = mount == MOUNT_WALL ? Level0EmbeddedBlock.Mount.WALL
                : mount == MOUNT_FLOOR ? Level0EmbeddedBlock.Mount.FLOOR : Level0EmbeddedBlock.Mount.CEILING;
        BlockState state = Level0Blocks.EMBEDDED.get().defaultBlockState()
                .setValue(Level0EmbeddedBlock.FACING, Direction.from2DDataValue(embedded >>> 2 & 3))
                .setValue(Level0EmbeddedBlock.MOUNT, fused)
                .setValue(Level0EmbeddedBlock.PROP, EMBED_PROPS[embedded >>> 4 & 15]);
        int y = mount == MOUNT_CEILING ? ceilingY() - 1 : floorY() + 1 + (embedded >>> 8 & 1);
        out.set(y, state);
    }

    /** For Level0Finder: the first noclipped prop of embed square (cellX, cellZ), as {x, z}, or null. */
    @Nullable
    int[] noclippedIn(long seed, int cellX, int cellZ) {
        long h = Hash.of(seed, cellX, cellZ, 720);
        if (Hash.unit(h) >= EMBED_CHANCE) {
            return null;
        }
        for (int k = 0; k < 4; k++) {
            long s = spotTarget(seed, h, cellX, cellZ, k);
            if (s == NO_SPOT) {
                return null;
            }
            int x = spotX(s);
            int z = spotZ(s);
            if (!inMaze(seed, x, z)) {
                continue;
            }
            long sx = X_WALLS.span(seed, x);
            long sz = Z_WALLS.span(seed, z);
            int column = mazeColumn(seed, x, z, sx, sz);
            if ((column & 15) == OPEN && embeddedAt(seed, x, z, column >>> 4 & 7, sx, sz) != 0) {
                return new int[] {x, z};
            }
        }
        return null;
    }

    /** Size of the squares noclipped furniture is placed in (for Level0Finder). */
    static int embedCell() {
        return EMBED_CELL;
    }

    /** Carpet, or (rarely) a scuffed arrow painted on the carpet. */
    private BlockState floorBlock(long seed, int x, int z, long sx, long sz, BlockState carpet) {
        int lenX = Bands.length(sx);
        int lenZ = Bands.length(sz);
        int ix = Bands.index(sx);
        int iz = Bands.index(sz);
        int x0 = X_WALLS.start(sx);
        int z0 = Z_WALLS.start(sz);
        if (x == x0 + lenX / 2 && z == z0 + lenZ / 2 && Hash.unit(seed, ix, iz, 241) < 0.04) {
            int[] exit = exits().nearest(seed, x, z);
            int ex = exit[0] - x;
            int ez = exit[1] - z;
            if (Math.abs(ex) <= ARROW_RANGE && Math.abs(ez) <= ARROW_RANGE) {
                Direction direction = Math.abs(ex) > Math.abs(ez)
                        ? (ex > 0 ? Direction.EAST : Direction.WEST)
                        : (ez > 0 ? Direction.SOUTH : Direction.NORTH);
                if (Hash.unit(seed, ix, iz, 242) < 0.2) {
                    direction = Direction.from2DDataValue(Hash.range(Hash.of(seed, ix, iz, 243), 4));
                }
                return ModBlocks.LOBBY_CARPET_ARROW.get().defaultBlockState().setValue(FloorArrowBlock.FACING, direction);
            }
        }
        return carpet;
    }

    /**
     * Crates hide in the back corner of dead-end rooms (exactly one way in). Returns how many are
     * stacked on this column (0, 1 or 2).
     */
    private static int crateHeight(long seed, int x, int z, long sx, long sz) {
        int ix = Bands.index(sx);
        int iz = Bands.index(sz);
        if (Hash.unit(seed, ix, iz, 250) >= 0.55) {
            return 0;
        }
        int x0 = X_WALLS.start(sx);
        int x1 = X_WALLS.end(sx) - 1;
        int z0 = Z_WALLS.start(sz);
        int z1 = Z_WALLS.end(sz) - 1;
        if (x != x0 && x != x1 || z != z0 && z != z1) {
            return 0; // crates only ever stand in a corner: skip the wall lookups everywhere else
        }
        boolean west = open(xSegment(seed, X_WALLS.span(seed, x0 - 1), sz));
        boolean east = open(xSegment(seed, X_WALLS.span(seed, x1 + 1), sz));
        boolean north = open(zSegment(seed, Z_WALLS.span(seed, z0 - 1), sx));
        boolean south = open(zSegment(seed, Z_WALLS.span(seed, z1 + 1), sx));
        int ways = (west ? 1 : 0) + (east ? 1 : 0) + (north ? 1 : 0) + (south ? 1 : 0);
        if (ways != 1) {
            return 0;
        }
        boolean pick = Hash.unit(seed, ix, iz, 251) < 0.5;
        int cx;
        int cz;
        if (west || east) {
            cx = west ? x1 : x0;          // far from the opening
            cz = pick ? z0 : z1;
        } else {
            cz = north ? z1 : z0;
            cx = pick ? x0 : x1;
        }
        if (x != cx || z != cz) {
            return 0;
        }
        return Hash.unit(seed, ix, iz, 252) < 0.3 ? 2 : 1;
    }

    private static boolean open(int segmentState) {
        return segmentState != SOLID;
    }

    /**
     * Now and then someone's backpack lies on the floor of a room: rare (about one per 800 floor
     * blocks), at most one per room. Returns the way it faces, or null for no backpack here.
     */
    @Nullable
    private static Direction backpackFacing(long seed, int x, int z, long sx, long sz) {
        if (Bands.isBand(sx) || Bands.isBand(sz)) {
            return null; // rooms only, not the gaps in walls
        }
        long h = Hash.of(seed, Bands.index(sx), Bands.index(sz), 255);
        if (Hash.unit(h) >= 0.03) {
            return null;
        }
        int bx = X_WALLS.start(sx) + Hash.range(Hash.mix(h ^ 1), Bands.length(sx));
        int bz = Z_WALLS.start(sz) + Hash.range(Hash.mix(h ^ 2), Bands.length(sz));
        if (x != bx || z != bz || crateHeight(seed, x, z, sx, sz) > 0) {
            return null;
        }
        return Direction.from2DDataValue(Hash.range(Hash.mix(h ^ 3), 4));
    }

    // ==========================================================================================
    // Wall drawings
    // ==========================================================================================

    private void drawDecals(long seed, int x, int z, Column out) {
        int floor = floorY();
        Direction door = doorDrawingFace(seed, x, z);
        if (door != null) {
            BlockState state = ModBlocks.WALL_DOOR_DRAWING.get().defaultBlockState().setValue(WallDecalBlock.FACING, door);
            out.set(floor + 1, state.setValue(WallDoorDrawingBlock.HALF, DoubleBlockHalf.LOWER));
            out.set(floor + 2, state.setValue(WallDoorDrawingBlock.HALF, DoubleBlockHalf.UPPER));
            return;
        }
        Direction window = windowFace(seed, x, z);
        if (window != null) {
            out.set(floor + 2, ModBlocks.WALL_WINDOW_DRAWING.get().defaultBlockState().setValue(WallDecalBlock.FACING, window));
            return;
        }
        if (Hash.unit(seed, x, z, 270) >= 0.03) {
            return;
        }
        Direction face = openFace(seed, x, z, 271);
        if (face == null) {
            return;
        }
        int[] exit = exits().nearest(seed, x, z);
        int ex = exit[0] - x;
        int ez = exit[1] - z;
        if (Math.abs(ex) <= ARROW_RANGE && Math.abs(ez) <= ARROW_RANGE && Hash.unit(seed, x, z, 272) < 0.7) {
            // Someone facing this wall has FACING behind them; their right hand points FACING's counter-clockwise.
            Direction right = face.getCounterClockWise();
            boolean pointsRight = ex * right.getStepX() + ez * right.getStepZ() >= 0;
            if (Hash.unit(seed, x, z, 273) < 0.25) {
                pointsRight = !pointsRight; // a lie
            }
            BlockState arrow = (pointsRight ? ModBlocks.WALL_ARROW_RIGHT : ModBlocks.WALL_ARROW_LEFT).get().defaultBlockState();
            out.set(floor + 2, arrow.setValue(WallDecalBlock.FACING, face));
        } else {
            int y = Hash.unit(seed, x, z, 274) < 0.5 ? floor + 2 : floor + 3;
            out.set(y, ModBlocks.WALL_HANDPRINT.get().defaultBlockState().setValue(WallDecalBlock.FACING, face));
        }
    }

    /** A very rare "fake exit" door drawn on a maze wall; returns the face it is drawn on. */
    @Nullable
    private Direction doorDrawingFace(long seed, int x, int z) {
        if (Hash.unit(seed, x, z, 260) >= 0.004 || !isMazeWall(seed, x, z)) {
            return null;
        }
        return openFace(seed, x, z, 261);
    }

    /** The window doodle goes one block to the right of a door drawing (as seen by the viewer). */
    @Nullable
    private Direction windowFace(long seed, int x, int z) {
        for (Direction face : HORIZONTALS) {
            Direction toDoor = face.getClockWise();
            int doorX = x + toDoor.getStepX();
            int doorZ = z + toDoor.getStepZ();
            if (Hash.unit(seed, doorX, doorZ, 260) < 0.004 && doorDrawingFace(seed, doorX, doorZ) == face
                    && !solidAtEyeLevel(seed, x + face.getStepX(), z + face.getStepZ())) {
                return face;
            }
        }
        return null;
    }

    /** A side of this wall column that looks into open space, picked in a random order. */
    @Nullable
    private Direction openFace(long seed, int x, int z, int salt) {
        int start = Hash.range(Hash.of(seed, x, z, salt), 4);
        for (int i = 0; i < 4; i++) {
            Direction face = Direction.from2DDataValue((start + i) & 3);
            if (!solidAtEyeLevel(seed, x + face.getStepX(), z + face.getStepZ())) {
                return face;
            }
        }
        return null;
    }
}
