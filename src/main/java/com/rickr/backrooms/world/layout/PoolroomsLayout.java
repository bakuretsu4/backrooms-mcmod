package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.CeilingTubeBlock;
import com.rickr.backrooms.block.CurveOrientation;
import com.rickr.backrooms.block.CurveShape;
import com.rickr.backrooms.block.PoolLampBlock;
import com.rickr.backrooms.block.SunlitVoidBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * The Poolrooms: white tile rooms, corridors and long water channels, mostly dim, lit by sparse
 * fluorescent tubes, with cyan water glowing from lamps in the pool walls. Now and then a tall
 * atrium opens up under daylight skylights: a dazzling white court around a big pool. The
 * contrast between the dim halls and the bright courts is the whole look.
 *
 * <p><b>Floor plan.</b> A grid of 14x14 cells, each one a room. The wall between two cells (on a
 * cell's west and north edge, 1 block thick) is open, has a rectangular doorway (2-4 wide, 4-5
 * tall) or is solid (dead ends). Every cell is guaranteed one way out to the west or north, so no
 * room is ever sealed. Kinds of room:
 * <ul>
 *   <li><b>channel</b>: the iconic one. A 3-wide water channel (5 deep or ankle deep) between two
 *       2-wide dry walkways, running through thick walls. Channel cells come in runs of up to 5 in
 *       a row, so the channels go on and on; side doorways cut through the walls.</li>
 *   <li><b>corridor</b>: the same shape, all dry.</li>
 *   <li><b>pool</b>: a deep pool with dark lane lines on its floor and a lamp at each end, in a 2-3
 *       wide dry deck, sometimes with a shallow step around it.</li>
 *   <li><b>shallow</b>: ankle-deep water everywhere, with a 1-2 wide dry rim.</li>
 *   <li><b>dry</b>: a plain tiled room.</li>
 *   <li><b>pillared hall</b>: zones of 3x3 cells with no walls inside: shallow water and a square
 *       column every 7 blocks.</li>
 *   <li><b>atrium</b> (rare): zones of 2x2 cells with a ceiling 8 blocks higher, full of daylight
 *       skylights, around a 19x19 deep pool with four tall pillars standing in it.</li>
 * </ul>
 * Top view, generated from this class (# wall, . dry tile, ~ shallow water, D deep water, blank =
 * doorway): a deep channel running east-west through thick walls (side doorways cut through
 * them), ending in an opening on the right; below it a pool room (no step) open to a shallow room.
 * <pre>
 *   #####   ###########  ########.
 *   #####...###########..########.
 *   ............................#.
 *   ............................#.
 *   DDDDDDDDDDDDDDDDDDDDDDDDDDD. .
 *   DDDDDDDDDDDDDDDDDDDDDDDDDDD. .
 *   DDDDDDDDDDDDDDDDDDDDDDDDDDD. .
 *   ............................ .
 *   ............................#.
 *   ####....###########....######.
 *   ####    ###########    #######
 *   #...........................#.
 *   #...............~~~~~~~~~~~.#.
 *   #..DDDDDDDDD....~~~~~~~~~~~.#.
 *   #..DDDDDDDDD....~~~~~~~~~~~. .
 *   #..DDDDDDDDD....~~~~~~~~~~~.#.
 *   #...............~~~~~~~~~~~.#.
 *   #...........................#.
 *   ########  #############  #####
 * </pre>
 * (Shortened top to bottom; the real rooms are 13x13 inside.)
 *
 * <p><b>Heights</b> (the floor layer is y40 as on every level):
 * <ul>
 *   <li>y41 is the "surface" layer: shallow water (you wade, feet at y41) or a solid tile walkway
 *       (you stand on top, feet at y42). Jumping is allowed in water so you can climb out; a
 *       walkway is exactly at water level, so every deep pool can be climbed out of.</li>
 *   <li>Deep water: floor at y36, water from y37 to y41. Everything under water is
 *       {@code pool_floor} (animated light ripples).</li>
 *   <li>Ceiling layer y48 (atriums: y56). Tubes hang just under it.</li>
 * </ul>
 *
 * <p><b>The exit: the drain.</b> One per 384x384 region, in the middle of its own dim square room:
 * a 13x13 deep pool on a dry deck, inside a wall with a doorway on each side and a dry walkway
 * ring outside. Dark tile bands on the deck lead from each doorway to the pool, four lamps in the
 * pool's edge light the water, and at the very centre a 5x5 shaft lined with dark tile drops from
 * the pool floor down to y24. Sink into it and the current ({@code PoolroomsRules}) pulls you
 * below y30: you have escaped.
 *
 * <p><b>Water never leaks.</b> Every column is solid from the deep-pool floor (y36) up to y40
 * unless it holds water there, and at y41 every column is either water or solid tile: there is no
 * air anywhere at the water's level. Below y36 only the drain shaft holds water, and its dark
 * lining surrounds it all the way down. Lamps are full blocks that replace solid wall blocks. So
 * no water block ever touches air at its side or below, and nothing flows when the chunk loads.
 *
 * <p><b>Safe rooms.</b> About 60% of the 9x9-cell blocks (126x126) turn one ordinary cell into a
 * safe room: a dry walkway runs around the inside of the cell, and in the middle stands a small
 * carpeted, tiled room (dark tile dado band) with one doorway onto that walkway. No water inside.
 * Elsewhere, backpacks and the odd supply crate lie on dry walkways (never in the water).
 *
 * <p><b>The sunlit archway.</b> About once per 308 x 308 blocks one ordinary room (never an atrium,
 * a hall, a safe room or near a drain) is dry and dim, with a 5-deep block of wall across its north
 * side. Through it runs a 4-wide round-arched passage tiled in Level 37's fine white mosaic, ending
 * in a wall of dazzling warm daylight that spills out into the room. Walk into the light (the back
 * half of the passage) and {@code Level37Rules} takes you ahead to Level 39's flooded tunnels.
 *
 * <p><b>Anomalies</b> ({@link PoolroomsAnomalyPlan}): lightless nooks in the corners of some lit
 * rooms (dark corners) and rooms that are one deep, dark flooded shaft with a staircase running
 * down into it (stair pits). Both are cut out of ordinary dry, shallow or pool rooms.
 *
 * <p><b>The Sanguine Reservoir.</b> Far to the east (x from 200,000 to 600,000) the level repeats
 * itself exactly, {@link #RESERVOIR_OFFSET} blocks from the original: same rooms, same drains (the
 * {@link ExitLocator} copies them too). But all white light is gone: no tubes, no skylights, the
 * daylight archways are walled up, the tile is dim, the pool lamps glow red and red sprouts grow
 * on the walkways. Lingering in a dark corner moves you there (world/anomaly/poolrooms). There are
 * no dark corners in the Reservoir itself: the only way out is a drain, down to Level 37.
 */
public final class PoolroomsLayout extends LevelLayout {
    // ---- Heights -----------------------------------------------------------------------------
    /** The water's level: shallow water and dry walkways both sit in this layer. */
    public static final int SURFACE_Y = FLOOR_Y + 1;
    /** Floor of the deep water; it fills the five layers above it. */
    public static final int DEEP_FLOOR_Y = FLOOR_Y - 4;
    /** Bottom of the drain shaft (dark tile); its water starts one block above. */
    public static final int DRAIN_FLOOR_Y = 23;
    /** Pool lamps sit in the pool wall here, two blocks under the water's surface. */
    private static final int LAMP_Y = FLOOR_Y - 1;
    /** Air from y41 to y47, ceiling layer at y48. */
    private static final int INTERIOR_HEIGHT = 7;
    /** Ceiling layer of the tall atriums. */
    public static final int ATRIUM_CEILING_Y = 56;

    // ---- Room grid ---------------------------------------------------------------------------
    /** One room per CELL x CELL square; walls on the cell's west and north edges. */
    static final int CELL = 14;
    /** Pillared halls cover zones of this many cells on a side ... */
    private static final int HALL_ZONE_CELLS = 3;
    /** ... and this fraction of all zones. */
    private static final double HALL_SHARE = 0.15;
    /** Atriums cover zones of 2x2 cells (28x28 blocks) ... */
    private static final int ATRIUM_ZONE_CELLS = 2;
    private static final int ATRIUM_SIZE = ATRIUM_ZONE_CELLS * CELL;
    /** ... and this fraction of all atrium zones. */
    private static final double ATRIUM_SHARE = 0.10;
    /** Columns in the pillared halls stand on this grid. */
    private static final int PILLAR_GRID = 7;
    /** Channels come in runs: rows (or columns) of cells are split into stretches this long ... */
    private static final int RUN = 5;
    /** ... and this fraction of the stretches are channels. */
    private static final double RUN_SHARE = 0.16;
    /** Rooms without any tube (dark). */
    private static final double DARK_ROOM_SHARE = 0.30;
    /** Lit rooms with two tube strips instead of one. */
    private static final double TWO_TUBES_SHARE = 0.35;

    // ---- The drain room, in square "rings" around the exit centre ----------------------------
    /** Rings 0-2: the 5x5 shaft. */
    public static final int SHAFT_RADIUS = 2;
    /** Rings 3-4 have a dark tile pool floor; ring 3 also lines the shaft all the way down. */
    private static final int DARK_RING = 4;
    /** Rings up to here are deep pool. */
    private static final int DRAIN_POOL_RADIUS = 6;
    /** One ring of shallow step around the deep pool (with the four pool lamps in it). */
    private static final int DRAIN_LEDGE_RING = 7;
    /** The room's wall (rings 8-10 are the dry deck inside it). */
    private static final int DRAIN_WALL_RING = 11;
    /** Doorways in the middle of each side are 5 wide ... */
    private static final int DRAIN_DOOR_HALF_WIDTH = 2;
    /** ... and 5 tall. */
    private static final int DRAIN_DOOR_HEIGHT = 5;
    /** Ring 12 is a dry walkway all around the room, so every doorway leads somewhere. */
    private static final int CLEARING_RADIUS = 12;

    // ---- Room kinds --------------------------------------------------------------------------
    private static final int ROOM_DRY = 0;
    private static final int ROOM_SHALLOW = 1;
    private static final int ROOM_POOL = 2;
    private static final int ROOM_HALL = 3;
    private static final int ROOM_ATRIUM = 4;
    /** A water channel between two walkways. */
    private static final int ROOM_CHANNEL = 5;
    /** The same shape as a channel, but dry. */
    private static final int ROOM_CORRIDOR = 6;
    /** A safe room standing on a dry walkway that runs around the inside of the cell. */
    private static final int ROOM_SAFE = 7;
    /**
     * The secret way to Level 37: a dim, dry room whose north wall holds a round-arched passage
     * lined with Level 37's fine white tile, warm daylight pouring out of it (see the class comment).
     */
    private static final int ROOM_SUNLIT = 8;

    // ---- The sunlit archway --------------------------------------------------------------------
    /** One archway per this many cells on a side (22 x 14 = 308 blocks) ... */
    private static final int SUNLIT_REGION_CELLS = 22;
    /** ... in a thick block of wall across the north side of its room, rows 1..5 of the cell. */
    private static final int ARCH_DEPTH = 5;
    /** The passage is 4 wide (cells 5..8), a half circle of radius 2 around this corner. */
    private static final int ARCH_MIDDLE = 7;
    private static final int ARCH_RADIUS = 2;
    /** Straight sides from the walkway (y42) up to here, then the round top. */
    private static final int ARCH_SPRING_Y = 45;

    // ---- Safe rooms --------------------------------------------------------------------------
    /** Safe rooms: at most one per this many cells on a side (9 x 14 = 126 blocks). */
    private static final int SAFE_BLOCK_CELLS = 9;
    /** Safe rooms keep at least this far (chessboard distance from their cell's centre) from an exit. */
    private static final int SAFE_EXIT_DISTANCE = 24;

    /** Channels run along X (east-west) ... */
    private static final int AXIS_X = 0;
    /** ... or along Z (north-south). */
    private static final int AXIS_Z = 1;

    // ---- Wall states between two cells -------------------------------------------------------
    private static final int EDGE_OPEN = 0;
    private static final int EDGE_DOORWAY = 1;
    private static final int EDGE_SOLID = 2;
    /** Two channels meeting end to end: the channel and walkways run straight on. */
    private static final int EDGE_JOIN = 3;
    /** A channel ending at a room: an opening as wide as the channel and its walkways. */
    private static final int EDGE_OPENING = 4;
    /** Inside one atrium: no wall at all. */
    private static final int EDGE_ATRIUM = 5;

    /** The wall on a cell's west edge (runs along Z) ... */
    private static final int WEST = 0;
    /** ... and the one on its north edge (runs along X). */
    private static final int NORTH = 1;

    // ---- Channel cross-section, counted across the cell (1..13) ------------------------------
    /** Walkways and channel together span across = 4..10; the rest is thick wall. */
    private static final int CHANNEL_FROM = 4;
    private static final int CHANNEL_TO = 10;
    /** The water itself is across = 6..8. */
    private static final int WATER_FROM = 6;
    private static final int WATER_TO = 8;
    /** The middle of a cell (tubes, lamps, lane lines line up on it). */
    private static final int MIDDLE = 7;

    // ---- What one column looks like ----------------------------------------------------------
    /** Tile from y41 to the ceiling (walls, posts, pillars). */
    private static final int WALL = 0;
    /** Tile threshold at y41, then an opening, then tile up to the ceiling. */
    private static final int DOORWAY = 1;
    /** Dry tile walkway at y41: you stand at y42. */
    private static final int DRY = 2;
    /** 1-deep water at y41 over the y40 floor. */
    private static final int SHALLOW = 3;
    /** Deep water: floor y36, water y37-y41. */
    private static final int DEEP = 4;
    /** The drain shaft: water from y41 down to y24. */
    private static final int SHAFT = 5;
    /** A dry walkway with a backpack or supply crate standing on it. */
    private static final int CONTAINER = 6;
    /** The sunlit archway's passage: a dry walkway under a round arch of fine white tile. */
    private static final int ARCHWAY = 7;
    /** The back of the archway: a wall of overexposed daylight (Level 37's sunlit void). */
    private static final int SUNLIGHT = 8;
    /** Wall beside the archway, faced with Level 37's fine tile: it frames the arch. */
    private static final int LINING = 9;

    // ---- The Sanguine Reservoir ------------------------------------------------------------------
    /** The Reservoir's copy of column x is column x + RESERVOIR_OFFSET ... */
    public static final int RESERVOIR_OFFSET = 400_000;
    /**
     * ... and the Reservoir is the band RESERVOIR_OFFSET +- RESERVOIR_HALF, a copy of the band
     * -RESERVOIR_HALF..RESERVOIR_HALF around the centre (nobody walks 200,000 blocks).
     */
    static final int RESERVOIR_HALF = 200_000;
    /** Red sprouts grow on this share of the Reservoir's dry walkway blocks. */
    private static final double SPROUT_SHARE = 0.025;

    /** The exits, kept in a static field so the static floor-plan helpers can reach them. */
    private static final ExitLocator EXITS = new ExitLocator(384, 48, 5000)
            .copying(RESERVOIR_OFFSET - RESERVOIR_HALF, RESERVOIR_OFFSET + RESERVOIR_HALF, RESERVOIR_OFFSET);

    public PoolroomsLayout() {
        super(INTERIOR_HEIGHT, EXITS);
    }

    // ==========================================================================================
    // Public queries
    // ==========================================================================================

    /** Dry walkways, doorways and shallow water: anywhere you can stand without swimming. */
    @Override
    public boolean isWalkable(long seed, int x, int z) {
        int kind = columnKind(seed, home(x), z);
        return kind == DRY || kind == DOORWAY || kind == SHALLOW || kind == ARCHWAY || kind == PoolroomsAnomalyPlan.NOOK;
    }

    /** Only dry walkways: nobody should arrive in the water. */
    @Override
    public boolean isSafeArrival(long seed, int x, int z) {
        return columnKind(seed, home(x), z) == DRY;
    }

    /** Feet at y41 when wading in shallow water, y42 on the raised dry tiles (and swimming). */
    @Override
    public int standY(long seed, int x, int z) {
        int kind = columnKind(seed, home(x), z);
        return kind == SHALLOW || kind == PoolroomsAnomalyPlan.NOOK ? SURFACE_Y : SURFACE_Y + 1;
    }

    /** Everything inside a safe room, walls and doorway included (not the walkway around it). */
    @Override
    public boolean isSafeRoom(long seed, int x, int z) {
        x = home(x);
        int cx = Math.floorDiv(x, CELL);
        int cz = Math.floorDiv(z, CELL);
        return isSafeCell(seed, cx, cz) && safeRoomPlan(seed, cx, cz).contains(x, z);
    }

    @Override
    public int highestCeilingY() {
        return ATRIUM_CEILING_Y;
    }

    // ---- Anomalies (PoolroomsAnomalyPlan); in the Reservoir, the copies of the stair pits --------

    @Override
    @Nullable
    public AnomalySite anomalyAt(long seed, int x, int z) {
        boolean reservoir = inReservoir(x);
        int hx = home(x);
        AnomalySite site = PoolroomsAnomalyPlan.PITS.at(seed, hx, z);
        if (site == null && !reservoir) {
            site = PoolroomsAnomalyPlan.CORNERS.at(seed, hx, z);
        }
        return site != null && reservoir ? reservoirCopy(site) : site;
    }

    @Override
    public void anomaliesNear(long seed, int x, int z, int radius, Consumer<AnomalySite> out) {
        boolean reservoir = inReservoir(x);
        int hx = home(x);
        PoolroomsAnomalyPlan.PITS.near(seed, hx, z, radius, site -> out.accept(reservoir ? reservoirCopy(site) : site));
        if (!reservoir) {
            PoolroomsAnomalyPlan.CORNERS.near(seed, hx, z, radius, out);
        }
    }

    @Override
    @Nullable
    public AnomalySite nearestAnomaly(long seed, int x, int z, String kind) {
        boolean reservoir = inReservoir(x);
        int hx = home(x);
        AnomalySite site = null;
        if (kind.equals(PoolroomsAnomalyPlan.STAIR_PIT)) {
            site = PoolroomsAnomalyPlan.PITS.nearest(seed, hx, z, s -> true, 8);
        } else if (kind.equals(PoolroomsAnomalyPlan.DARK_CORNER) && !reservoir) {
            site = PoolroomsAnomalyPlan.CORNERS.nearest(seed, hx, z, s -> true, 8);
        }
        return site != null && reservoir ? reservoirCopy(site) : site;
    }

    @Override
    public List<String> anomalyKinds() {
        return PoolroomsAnomalyPlan.KINDS;
    }

    /**
     * True in the Sanguine Reservoir, the red copy of the level far to the east (see the class
     * comment). Client code uses this too (red water, the Reservoir's sounds and look).
     */
    public static boolean inReservoir(double x) {
        return x >= RESERVOIR_OFFSET - RESERVOIR_HALF && x < RESERVOIR_OFFSET + RESERVOIR_HALF;
    }

    /** The original column that column x copies: x itself, or x - RESERVOIR_OFFSET in the Reservoir. */
    private static int home(int x) {
        return inReservoir(x) ? x - RESERVOIR_OFFSET : x;
    }

    /** A site of the original level, moved to its copy in the Reservoir. */
    private static AnomalySite reservoirCopy(AnomalySite site) {
        return new AnomalySite(site.kind(), site.minX() + RESERVOIR_OFFSET, site.minZ(), site.maxX() + RESERVOIR_OFFSET,
                site.maxZ(), site.anchorX() + RESERVOIR_OFFSET, site.anchorZ(), site.facing(), site.salt());
    }

    /**
     * True when room (cx, cz) may hold an anomaly: an ordinary dry or shallow room (also a pool room
     * for a stair pit, which replaces the whole room), well away from any drain. A dark corner also
     * needs the room to be lit: it is a lightless corner in an otherwise lit room.
     */
    static boolean anomalyRoomAllowed(long seed, int cx, int cz, boolean darkCorner) {
        int kind = kindOf(room(seed, cx, cz));
        boolean fits = darkCorner
                ? (kind == ROOM_DRY || kind == ROOM_SHALLOW) && Hash.unit(seed, cx, cz, 90) >= DARK_ROOM_SHARE
                : kind == ROOM_DRY || kind == ROOM_SHALLOW || kind == ROOM_POOL;
        if (!fits) {
            return false;
        }
        int centerX = cx * CELL + CELL / 2;
        int centerZ = cz * CELL + CELL / 2;
        int[] exit = EXITS.nearest(seed, centerX, centerZ);
        return Math.max(Math.abs(exit[0] - centerX), Math.abs(exit[1] - centerZ)) >= SAFE_EXIT_DISTANCE;
    }

    /**
     * The anomaly's column in room (cx, cz), or {@code PoolroomsAnomalyPlan.NONE}: a stair pit
     * replaces the whole room (dry deck around the shaft), a dark corner one corner of it.
     */
    private static int anomalyColumn(long seed, int cx, int cz, int room, int mx, int mz) {
        int kind = kindOf(room);
        if (kind != ROOM_DRY && kind != ROOM_SHALLOW && kind != ROOM_POOL) {
            return PoolroomsAnomalyPlan.NONE; // quick answer for most rooms
        }
        AnomalySite pit = PoolroomsAnomalyPlan.pitOfCell(seed, cx, cz);
        if (pit != null) {
            int column = PoolroomsAnomalyPlan.pitColumn(pit, mx, mz);
            return column == PoolroomsAnomalyPlan.NONE ? DRY : column;
        }
        if (kind == ROOM_POOL) {
            return PoolroomsAnomalyPlan.NONE;
        }
        AnomalySite corner = PoolroomsAnomalyPlan.cornerOfCell(seed, cx, cz);
        return corner == null ? PoolroomsAnomalyPlan.NONE : PoolroomsAnomalyPlan.cornerColumn(corner, mx, mz, WALL);
    }

    // ==========================================================================================
    // Floor plan
    // ==========================================================================================

    private int columnKind(long seed, int x, int z) {
        int dx = exits().dx(seed, x, z);
        int dz = exits().dz(seed, x, z);
        int ring = Math.max(Math.abs(dx), Math.abs(dz));
        if (ring <= CLEARING_RADIUS) {
            return drainRoomColumn(dx, dz, ring);
        }
        return gridColumn(seed, x, z);
    }

    /** The square drain room around an exit (see the class comment). */
    private static int drainRoomColumn(int dx, int dz, int ring) {
        if (ring <= SHAFT_RADIUS) {
            return SHAFT;
        }
        if (ring <= DRAIN_POOL_RADIUS) {
            return DEEP;
        }
        if (ring == DRAIN_LEDGE_RING) {
            return SHALLOW;
        }
        if (ring == DRAIN_WALL_RING) {
            boolean corner = Math.abs(dx) == Math.abs(dz);
            int along = Math.abs(dx) == DRAIN_WALL_RING ? dz : dx; // position along this side
            return !corner && Math.abs(along) <= DRAIN_DOOR_HALF_WIDTH ? DOORWAY : WALL;
        }
        return DRY; // the deck inside the room, and the walkway ring around it
    }

    /** The floor plan of the grid, plus the backpacks and crates lying on its dry walkways. */
    private static int gridColumn(long seed, int x, int z) {
        int kind = baseGridColumn(seed, x, z);
        if (kind == DRY && (crateSpot(seed, x, z) || backpackFacing(seed, x, z) != null)) {
            return CONTAINER;
        }
        return kind;
    }

    private static int baseGridColumn(long seed, int x, int z) {
        int cx = Math.floorDiv(x, CELL);
        int cz = Math.floorDiv(z, CELL);
        if (isAtrium(seed, cx, cz)) {
            int ax = Math.floorMod(x, ATRIUM_SIZE);
            int az = Math.floorMod(z, ATRIUM_SIZE);
            if (ax != 0 && az != 0) {
                return atriumColumn(ax, az); // inside the atrium; its outer walls are handled below
            }
        }
        int mx = Math.floorMod(x, CELL);
        int mz = Math.floorMod(z, CELL);
        if (mx == 0 && mz == 0) {
            return WALL; // the post at the grid corner
        }
        if (mx == 0) {
            return edgeColumn(seed, cx, cz, WEST, mz);
        }
        if (mz == 0) {
            return edgeColumn(seed, cx, cz, NORTH, mx);
        }
        int room = room(seed, cx, cz);
        int special = anomalyColumn(seed, cx, cz, room, mx, mz);
        if (special != PoolroomsAnomalyPlan.NONE) {
            return special;
        }
        // How far this column is from the nearest edge of its room (1 = right next to a wall).
        int depth = Math.min(Math.min(mx, CELL - mx), Math.min(mz, CELL - mz));
        long details = Hash.of(seed, cx, cz, 73);
        switch (kindOf(room)) {
            case ROOM_HALL:
                return hallColumn(seed, cx, cz, mx, mz);
            case ROOM_CHANNEL:
            case ROOM_CORRIDOR:
                return channelColumn(seed, cx, cz, room, mx, mz);
            case ROOM_SHALLOW: {
                int rim = 1 + Hash.range(details, 2);
                return depth <= rim ? DRY : SHALLOW;
            }
            case ROOM_POOL: {
                int deck = poolDeck(details);
                if (depth <= deck) {
                    return DRY;
                }
                return depth < poolStart(details) ? SHALLOW : DEEP; // the optional shallow step
            }
            case ROOM_SUNLIT:
                return sunlitColumn(mx, mz);
            case ROOM_SAFE:
                if (depth <= 1) {
                    return DRY; // the walkway around the safe room
                }
                return safeRoomPlan(seed, cx, cz).isWalkable(x, z) ? DRY : WALL;
            default:
                return DRY;
        }
    }

    // ---- Safe rooms ----------------------------------------------------------------------------

    /**
     * True when cell (cx, cz) is the safe room of its 9x9 block of cells. The chosen cell is never
     * on the block's edge (so two safe rooms never touch), never in an atrium or pillared hall,
     * and never near the drain room.
     */
    private static boolean isSafeCell(long seed, int cx, int cz) {
        int bx = Math.floorDiv(cx, SAFE_BLOCK_CELLS);
        int bz = Math.floorDiv(cz, SAFE_BLOCK_CELLS);
        long h = SafeRoomPlan.cellHash(seed, bx, bz, 430);
        if (!SafeRoomPlan.rolled(h)
                || cx != bx * SAFE_BLOCK_CELLS + 1 + Hash.range(Hash.mix(h ^ 1), SAFE_BLOCK_CELLS - 2)
                || cz != bz * SAFE_BLOCK_CELLS + 1 + Hash.range(Hash.mix(h ^ 2), SAFE_BLOCK_CELLS - 2)) {
            return false;
        }
        if (isAtrium(seed, cx, cz) || isHall(seed, cx, cz)) {
            return false;
        }
        int centerX = cx * CELL + CELL / 2;
        int centerZ = cz * CELL + CELL / 2;
        int[] exit = EXITS.nearest(seed, centerX, centerZ);
        return Math.max(Math.abs(exit[0] - centerX), Math.abs(exit[1] - centerZ)) >= SAFE_EXIT_DISTANCE;
    }

    /**
     * The room inside a safe cell: 11x11 with two-thick walls (7x7 inside), leaving the 1-wide dry
     * walkway (cell-local 1 and 13) all around it. The doorway can face any side.
     */
    private static SafeRoomPlan safeRoomPlan(long seed, int cx, int cz) {
        long h = SafeRoomPlan.cellHash(seed, Math.floorDiv(cx, SAFE_BLOCK_CELLS), Math.floorDiv(cz, SAFE_BLOCK_CELLS), 430);
        return new SafeRoomPlan(cx * CELL + 2, cz * CELL + 2, CELL - 3, CELL - 3, SafeRoomPlan.doorSide(h), 2);
    }

    private void fillSafeRoom(long seed, int x, int z, Column out) {
        BlockState tile = ModBlocks.POOL_TILE.get().defaultBlockState();
        // Solid tile below the floor, like every other dry column.
        for (int y = DEEP_FLOOR_Y; y < SURFACE_Y; y++) {
            out.set(y, tile);
        }
        safeRoomPlan(seed, Math.floorDiv(x, CELL), Math.floorDiv(z, CELL)).fill(x, z, SURFACE_Y, ceilingY(),
                new SafeRoomPlan.Style(
                        ModBlocks.BEIGE_CARPET.get().defaultBlockState(),
                        tile,
                        tile,
                        tile,
                        ModBlocks.POOL_TILE_DARK.get().defaultBlockState(), // a dark tile band below the dado line
                        tile,
                        null), out);
    }

    // ---- The sunlit archway (the secret shortcut to Level 39) -----------------------------------

    /**
     * True when cell (cx, cz) holds the sunlit archway of its 22 x 22-cell region. The region
     * picks a cell away from its edges; if that one is an atrium, a pillared hall, a safe room or
     * near a drain, the next of up to four cells beside it gets it instead (in the rare case none
     * fits, the region has no archway).
     */
    private static boolean isSunlitCell(long seed, int cx, int cz) {
        int rx = Math.floorDiv(cx, SUNLIT_REGION_CELLS);
        int rz = Math.floorDiv(cz, SUNLIT_REGION_CELLS);
        long h = Hash.of(seed, rx, rz, 5100);
        int bx = rx * SUNLIT_REGION_CELLS + 2 + Hash.range(h, SUNLIT_REGION_CELLS - 4);
        int bz = rz * SUNLIT_REGION_CELLS + 2 + Hash.range(Hash.mix(h), SUNLIT_REGION_CELLS - 4);
        if (cx < bx || cx > bx + 1 || cz < bz || cz > bz + 1) {
            return false; // quick answer for almost every cell
        }
        for (int k = 0; k < 4; k++) {
            int sx = bx + (k & 1);
            int sz = bz + (k >> 1);
            if (sunlitAllowed(seed, sx, sz)) {
                return sx == cx && sz == cz;
            }
        }
        return false;
    }

    private static boolean sunlitAllowed(long seed, int cx, int cz) {
        if (isAtrium(seed, cx, cz) || isHall(seed, cx, cz) || isSafeCell(seed, cx, cz)) {
            return false;
        }
        int centerX = cx * CELL + CELL / 2;
        int centerZ = cz * CELL + CELL / 2;
        int[] exit = EXITS.nearest(seed, centerX, centerZ);
        return Math.max(Math.abs(exit[0] - centerX), Math.abs(exit[1] - centerZ)) >= SAFE_EXIT_DISTANCE;
    }

    /**
     * The sunlit room: dry tile, with a 5-deep block of wall across its north side. Through the
     * middle of it runs the archway (cells 5..8), ending in a wall of daylight.
     */
    private static int sunlitColumn(int mx, int mz) {
        if (mz > ARCH_DEPTH) {
            return DRY;
        }
        if (mx >= ARCH_MIDDLE - ARCH_RADIUS && mx < ARCH_MIDDLE + ARCH_RADIUS) {
            return mz == 1 ? SUNLIGHT : ARCHWAY;
        }
        return mx == ARCH_MIDDLE - ARCH_RADIUS - 1 || mx == ARCH_MIDDLE + ARCH_RADIUS ? LINING : WALL;
    }

    /**
     * True in the back half of a sunlit archway, right in front of the light: walking in there
     * takes you to Level 37 ({@code Level37Rules}).
     */
    public boolean isSunlitThreshold(long seed, int x, int z) {
        if (inReservoir(x)) {
            return false; // the Reservoir's archways are walled up: no daylight there
        }
        int cx = Math.floorDiv(x, CELL);
        int cz = Math.floorDiv(z, CELL);
        int mx = Math.floorMod(x, CELL);
        int mz = Math.floorMod(z, CELL);
        return mz >= 2 && mz <= 3 && mx >= ARCH_MIDDLE - ARCH_RADIUS && mx < ARCH_MIDDLE + ARCH_RADIUS
                && exits().distance(seed, x, z) > CLEARING_RADIUS && isSunlitCell(seed, cx, cz);
    }

    /**
     * One column of the archway. Its floor and the arch are Level 37's fine mosaic and its curved
     * tile pieces, so the passage already looks like the place it leads to; the back is a wall of
     * sunlit void (light travelling south, out into the Poolrooms).
     */
    private static void fillArchway(int x, int kind, int ceiling, Column out) {
        BlockState tile = ModBlocks.POOL_TILE.get().defaultBlockState();
        for (int y = DEEP_FLOOR_Y; y < SURFACE_Y; y++) {
            out.set(y, tile);
        }
        out.set(SURFACE_Y, ModBlocks.L37_TILE_FLOOR.get().defaultBlockState());
        out.set(ceiling, tile);
        if (kind == LINING) {
            BlockState fine = ModBlocks.L37_TILE_WALL.get().defaultBlockState();
            for (int y = SURFACE_Y + 1; y < ceiling; y++) {
                out.set(y, fine);
            }
            return;
        }
        if (kind == SUNLIGHT) {
            BlockState light = ModBlocks.L37_SUNLIT_VOID.get().defaultBlockState().setValue(SunlitVoidBlock.FACING, Direction.SOUTH);
            for (int y = SURFACE_Y + 1; y < ceiling; y++) {
                out.set(y, y <= ARCH_SPRING_Y + ARCH_RADIUS - 1 ? light : tile);
            }
            return;
        }
        BlockState fineWall = ModBlocks.L37_TILE_WALL.get().defaultBlockState();
        int a = Math.floorMod(x, CELL) - ARCH_MIDDLE;
        int i = a >= 0 ? a : -a - 1;
        for (int y = ARCH_SPRING_Y; y < ceiling; y++) {
            int j = y - ARCH_SPRING_Y;
            if (Math.hypot(i + 1, j + 1) <= ARCH_RADIUS) {
                continue; // open
            }
            CurveShape shape = Math.hypot(i, j) < ARCH_RADIUS ? CurveShape.of(false, ARCH_RADIUS, i, j) : null;
            out.set(y, shape != null
                    ? ModBlocks.L37_TILE_CURVE.get().piece(shape, CurveOrientation.arch(false, a >= 0))
                    : j < ARCH_RADIUS ? fineWall : tile);
        }
    }

    // ---- Backpacks and crates on the dry walkways -----------------------------------------------

    /** A supply crate in one corner of some dry, pool and shallow rooms (always on the dry rim). */
    private static boolean crateSpot(long seed, int x, int z) {
        int mx = Math.floorMod(x, CELL);
        int mz = Math.floorMod(z, CELL);
        if ((mx != 1 && mx != CELL - 1) || (mz != 1 && mz != CELL - 1)) {
            return false;
        }
        int cx = Math.floorDiv(x, CELL);
        int cz = Math.floorDiv(z, CELL);
        long h = Hash.of(seed, cx, cz, 78);
        if (Hash.unit(h) >= 0.2) {
            return false;
        }
        int corner = Hash.range(Hash.mix(h), 4);
        if (mx != ((corner & 1) != 0 ? CELL - 1 : 1) || mz != ((corner & 2) != 0 ? CELL - 1 : 1)) {
            return false;
        }
        int kind = kindOf(room(seed, cx, cz));
        return kind == ROOM_DRY || kind == ROOM_POOL || kind == ROOM_SHALLOW;
    }

    /**
     * Dropped backpacks: each cell has four candidate spots, each used one time in twenty, but only
     * where the spot is a dry walkway (the caller checks that). That is about one backpack per 800
     * dry blocks. Returns the backpack's facing, or null.
     */
    @Nullable
    private static Direction backpackFacing(long seed, int x, int z) {
        int mx = Math.floorMod(x, CELL);
        int mz = Math.floorMod(z, CELL);
        if (mx == 0 || mz == 0) {
            return null;
        }
        int cx = Math.floorDiv(x, CELL);
        int cz = Math.floorDiv(z, CELL);
        for (int k = 0; k < 4; k++) {
            long h = Hash.of(seed, cx, cz, 79 + k);
            if (Hash.unit(h) >= 0.05
                    || mx != 1 + Hash.range(Hash.mix(h), CELL - 1)
                    || mz != 1 + Hash.range(Hash.mix(h ^ 1), CELL - 1)) {
                continue;
            }
            if (kindOf(room(seed, cx, cz)) == ROOM_SAFE || crateSpot(seed, x, z) || gridLampFacing(seed, x, z) != null) {
                return null; // the safe room has its own supplies; never on a pool lamp
            }
            return Direction.from2DDataValue(Hash.range(Hash.mix(h ^ 2), 4));
        }
        return null;
    }

    /** The block standing on a CONTAINER column. */
    private static BlockState containerAt(long seed, int x, int z) {
        if (crateSpot(seed, x, z)) {
            return ModBlocks.SUPPLY_CRATE.get().defaultBlockState();
        }
        return ModBlocks.BACKPACK.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, backpackFacing(seed, x, z));
    }

    /**
     * Atrium: (ax, az) are 1..27 inside the 28x28 zone. A 3-wide dry deck, a shallow step, and a
     * 19x19 deep pool with four tall square pillars standing in the water.
     */
    private static int atriumColumn(int ax, int az) {
        if ((ax == 9 || ax == 19) && (az == 9 || az == 19)) {
            return WALL;
        }
        int depth = Math.min(Math.min(ax, ATRIUM_SIZE - ax), Math.min(az, ATRIUM_SIZE - az));
        if (depth <= 3) {
            return DRY;
        }
        return depth == 4 ? SHALLOW : DEEP;
    }

    /**
     * Pillared hall: shallow water everywhere with a square column every 7 blocks. Where the hall
     * meets an ordinary room there is a wall with a doorway, with a 1-wide dry rim in front of it.
     */
    private static int hallColumn(long seed, int cx, int cz, int mx, int mz) {
        if (mx % PILLAR_GRID == 0 && mz % PILLAR_GRID == 0) {
            return WALL;
        }
        boolean rim = (mx == 1 && !isHall(seed, cx - 1, cz)) || (mx == CELL - 1 && !isHall(seed, cx + 1, cz))
                || (mz == 1 && !isHall(seed, cx, cz - 1)) || (mz == CELL - 1 && !isHall(seed, cx, cz + 1));
        return rim ? DRY : SHALLOW;
    }

    /**
     * Channel or dry corridor. {@code along} runs with the channel, {@code across} over it:
     * across 4-5 and 9-10 are walkways, 6-8 the water, the rest thick wall (with a passage cut
     * through it wherever the wall line beside it has a doorway).
     */
    private static int channelColumn(long seed, int cx, int cz, int room, int mx, int mz) {
        boolean alongX = axisOf(room) == AXIS_X;
        int along = alongX ? mx : mz;
        int across = alongX ? mz : mx;
        if (across >= CHANNEL_FROM && across <= CHANNEL_TO) {
            if (across >= WATER_FROM && across <= WATER_TO && kindOf(room) == ROOM_CHANNEL) {
                // Where the channel doesn't run on into the next cell it ends in a dry step.
                int endSide = alongX ? WEST : NORTH;
                if (along == 1 && !channelContinues(seed, cx, cz, endSide)) {
                    return DRY;
                }
                if (along == CELL - 1 && !channelContinues(seed, alongX ? cx + 1 : cx, alongX ? cz : cz + 1, endSide)) {
                    return DRY;
                }
                return isDeep(room) ? DEEP : SHALLOW;
            }
            return DRY;
        }
        // Thick wall beside the channel. The wall line on this side belongs to this cell (low side)
        // or to the next cell over (high side).
        int sideSide = alongX ? NORTH : WEST;
        int wallLine = across < CHANNEL_FROM
                ? edgeColumn(seed, cx, cz, sideSide, along)
                : edgeColumn(seed, alongX ? cx : cx + 1, alongX ? cz + 1 : cz, sideSide, along);
        return wallLine == DOORWAY ? DRY : WALL;
    }

    /** True when the water of two joined channels flows on through the wall line between them. */
    private static boolean channelContinues(long seed, int cx, int cz, int side) {
        return edgeState(seed, cx, cz, side) == EDGE_JOIN && joinedWater(seed, cx, cz, side) != DRY;
    }

    /** The water on the wall line between two joined channels: deep only if both are deep. */
    private static int joinedWater(long seed, int cx, int cz, int side) {
        int here = room(seed, cx, cz);
        int there = room(seed, side == WEST ? cx - 1 : cx, side == WEST ? cz : cz - 1);
        if (kindOf(here) != ROOM_CHANNEL || kindOf(there) != ROOM_CHANNEL) {
            return DRY; // a dry corridor on one side: the walkway just carries on
        }
        return isDeep(here) && isDeep(there) ? DEEP : SHALLOW;
    }

    /** A column on the wall line of cell (cx, cz); {@code along} = 1..13 along the wall. */
    private static int edgeColumn(long seed, int cx, int cz, int side, int along) {
        switch (edgeState(seed, cx, cz, side)) {
            case EDGE_OPEN: {
                boolean bothHalls = isHall(seed, cx, cz) && isHall(seed, side == WEST ? cx - 1 : cx, side == WEST ? cz : cz - 1);
                if (bothHalls) {
                    // Inside a hall the water runs on across the old wall line, between the columns.
                    return along == PILLAR_GRID ? WALL : SHALLOW;
                }
                return DRY; // a dry walkway joining the two rooms' rims
            }
            case EDGE_JOIN:
                if (along >= WATER_FROM && along <= WATER_TO) {
                    return joinedWater(seed, cx, cz, side);
                }
                return along >= CHANNEL_FROM && along <= CHANNEL_TO ? DRY : WALL;
            case EDGE_OPENING:
                return along >= CHANNEL_FROM && along <= CHANNEL_TO ? DOORWAY : WALL;
            case EDGE_DOORWAY:
                return isDoorway(seed, cx, cz, side, along) ? DOORWAY : WALL;
            default:
                return WALL;
        }
    }

    /** The wall between cell (cx, cz) and its neighbour to the west or north. */
    private static int edgeState(long seed, int cx, int cz, int side) {
        int ncx = side == WEST ? cx - 1 : cx;
        int ncz = side == WEST ? cz : cz - 1;
        if (side == NORTH && isSunlitCell(seed, cx, cz)) {
            return EDGE_SOLID; // the archway is built against this wall
        }
        boolean atriumHere = isAtrium(seed, cx, cz);
        boolean atriumThere = isAtrium(seed, ncx, ncz);
        if (atriumHere && atriumThere && sameAtrium(cx, cz, ncx, ncz)) {
            return EDGE_ATRIUM;
        }
        if (atriumHere || atriumThere) {
            return EDGE_DOORWAY; // atriums are walled off, with doorways
        }
        boolean hallHere = isHall(seed, cx, cz);
        boolean hallThere = isHall(seed, ncx, ncz);
        if (hallHere && hallThere) {
            return EDGE_OPEN;
        }
        if (hallHere || hallThere) {
            return EDGE_DOORWAY;
        }
        int here = room(seed, cx, cz);
        int there = room(seed, ncx, ncz);
        // A west wall is where a channel along X starts or ends; a north wall, one along Z.
        int endAxis = side == WEST ? AXIS_X : AXIS_Z;
        boolean channelEndsHere = isChannelLike(here) && axisOf(here) == endAxis;
        boolean channelEndsThere = isChannelLike(there) && axisOf(there) == endAxis;
        if (channelEndsHere && channelEndsThere) {
            return EDGE_JOIN;
        }
        if (channelEndsHere || channelEndsThere) {
            return EDGE_OPENING;
        }
        double r = Hash.unit(seed, cx, cz, 60 + side);
        int state = r < 0.25 ? EDGE_OPEN : r < 0.78 ? EDGE_DOORWAY : EDGE_SOLID;
        if (state == EDGE_OPEN && (isChannelLike(here) || isChannelLike(there))) {
            state = EDGE_DOORWAY; // the long side of a channel only ever gets doorways
        }
        // Every cell keeps one way out to the west or the north, so no room is ever sealed off
        // (follow those and you always reach the rest of the level).
        if (state == EDGE_SOLID && forcedSide(seed, cx, cz) == side) {
            state = EDGE_DOORWAY;
        }
        return state;
    }

    private static int forcedSide(long seed, int cx, int cz) {
        if (isSunlitCell(seed, cx, cz)) {
            return WEST; // its north wall holds the archway, so the way out must be west
        }
        return Hash.unit(seed, cx, cz, 64) < 0.5 ? WEST : NORTH;
    }

    private static long doorwayHash(long seed, int cx, int cz, int side) {
        return Hash.of(seed, cx, cz, 62 + side);
    }

    /** A rectangular doorway 2-4 wide, somewhere in the middle part of the wall (along = 3..10). */
    private static boolean isDoorway(long seed, int cx, int cz, int side, int along) {
        long h = doorwayHash(seed, cx, cz, side);
        int width = 2 + Hash.range(h, 3);
        int start = 3 + Hash.range(Hash.mix(h), 9 - width);
        return along >= start && along < start + width;
    }

    /** Height of the opening in a doorway column: 4 or 5 blocks (above the y41 threshold). */
    private static int doorwayHeight(long seed, int x, int z, boolean inDrainRoom) {
        if (inDrainRoom) {
            return DRAIN_DOOR_HEIGHT;
        }
        int side = Math.floorMod(x, CELL) == 0 ? WEST : NORTH;
        long h = doorwayHash(seed, Math.floorDiv(x, CELL), Math.floorDiv(z, CELL), side);
        return Hash.unit(Hash.mix(h ^ 0x51ED)) < 0.5 ? 4 : 5;
    }

    private static boolean isAtrium(long seed, int cx, int cz) {
        return Hash.unit(seed, Math.floorDiv(cx, ATRIUM_ZONE_CELLS), Math.floorDiv(cz, ATRIUM_ZONE_CELLS), 75) < ATRIUM_SHARE;
    }

    private static boolean sameAtrium(int cx, int cz, int ncx, int ncz) {
        return Math.floorDiv(cx, ATRIUM_ZONE_CELLS) == Math.floorDiv(ncx, ATRIUM_ZONE_CELLS)
                && Math.floorDiv(cz, ATRIUM_ZONE_CELLS) == Math.floorDiv(ncz, ATRIUM_ZONE_CELLS);
    }

    private static boolean isHall(long seed, int cx, int cz) {
        return !isAtrium(seed, cx, cz)
                && Hash.unit(seed, Math.floorDiv(cx, HALL_ZONE_CELLS), Math.floorDiv(cz, HALL_ZONE_CELLS), 71) < HALL_SHARE;
    }

    // ---- Rooms are packed into one int: bits 0-3 the kind, bit 4 the axis, bit 5 "deep" ------

    private static int room(long seed, int cx, int cz) {
        if (isAtrium(seed, cx, cz)) {
            return ROOM_ATRIUM;
        }
        if (isHall(seed, cx, cz)) {
            return ROOM_HALL;
        }
        if (isSafeCell(seed, cx, cz)) {
            return ROOM_SAFE;
        }
        if (isSunlitCell(seed, cx, cz)) {
            return ROOM_SUNLIT;
        }
        // Runs of channel cells: a stretch of RUN cells in a row (along X) or a column (along Z).
        boolean xRun = Hash.unit(seed, Math.floorDiv(cx, RUN), cz, 84) < RUN_SHARE;
        boolean zRun = Hash.unit(seed, cx, Math.floorDiv(cz, RUN), 85) < RUN_SHARE;
        if (xRun || zRun) {
            int axis = xRun && zRun ? (Hash.unit(seed, cx, cz, 88) < 0.5 ? AXIS_X : AXIS_Z) : xRun ? AXIS_X : AXIS_Z;
            // The whole run shares one look: wet or dry, deep or shallow.
            int a = axis == AXIS_X ? Math.floorDiv(cx, RUN) : cx;
            int b = axis == AXIS_X ? cz : Math.floorDiv(cz, RUN);
            int salt = axis == AXIS_X ? 0 : 10;
            boolean wet = Hash.unit(seed, a, b, 86 + salt) < 0.75;
            boolean deep = Hash.unit(seed, a, b, 87 + salt) < 0.6;
            return pack(wet ? ROOM_CHANNEL : ROOM_CORRIDOR, axis, deep);
        }
        double r = Hash.unit(seed, cx, cz, 70);
        if (r < 0.20) {
            return pack(ROOM_CHANNEL, Hash.unit(seed, cx, cz, 88) < 0.5 ? AXIS_X : AXIS_Z, Hash.unit(seed, cx, cz, 89) < 0.6);
        }
        return r < 0.48 ? ROOM_POOL : r < 0.74 ? ROOM_SHALLOW : ROOM_DRY;
    }

    private static int pack(int kind, int axis, boolean deep) {
        return kind | (axis << 4) | (deep ? 1 << 5 : 0);
    }

    private static int kindOf(int room) {
        return room & 15;
    }

    private static int axisOf(int room) {
        return (room >> 4) & 1;
    }

    private static boolean isDeep(int room) {
        return (room & (1 << 5)) != 0;
    }

    private static boolean isChannelLike(int room) {
        return kindOf(room) == ROOM_CHANNEL || kindOf(room) == ROOM_CORRIDOR;
    }

    /** Pool rooms: width of the dry deck (2 or 3). */
    private static int poolDeck(long details) {
        return 2 + Hash.range(details, 2);
    }

    /** Pool rooms: the depth (distance from the wall) where the deep water starts. */
    private static int poolStart(long details) {
        boolean step = Hash.unit(Hash.mix(details)) < 0.5;
        return poolDeck(details) + (step ? 2 : 1);
    }

    /** Pool rooms: the lane lines (and the lamps at their ends) run along X or along Z. */
    private static int laneAxis(long seed, int cx, int cz) {
        return Hash.unit(seed, cx, cz, 94) < 0.5 ? AXIS_X : AXIS_Z;
    }

    // ==========================================================================================
    // Blocks
    // ==========================================================================================

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        if (inReservoir(x)) {
            fillReservoirColumn(seed, x - RESERVOIR_OFFSET, z, out);
            return;
        }
        BlockState tile = ModBlocks.POOL_TILE.get().defaultBlockState();
        BlockState dark = ModBlocks.POOL_TILE_DARK.get().defaultBlockState();
        BlockState underWater = ModBlocks.POOL_FLOOR.get().defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        int floor = floorY();
        int dx = exits().dx(seed, x, z);
        int dz = exits().dz(seed, x, z);
        int ring = Math.max(Math.abs(dx), Math.abs(dz));
        boolean inDrainRoom = ring <= CLEARING_RADIUS;
        int kind = inDrainRoom ? drainRoomColumn(dx, dz, ring) : gridColumn(seed, x, z);
        if (!inDrainRoom && isSafeRoom(seed, x, z)) {
            fillSafeRoom(seed, x, z, out);
            return;
        }
        boolean tall = isTall(seed, x, z);
        int ceiling = tall ? ATRIUM_CEILING_Y : ceilingY();

        // Ceiling: plain tile, or daylight skylights in an atrium (never over the dim drain room).
        boolean skylight = tall && !inDrainRoom && kind != WALL && kind != DOORWAY && isSkylight(seed, x, z);
        out.set(ceiling, skylight ? ModBlocks.POOL_SKYLIGHT.get().defaultBlockState() : tile);
        if (inDrainRoom && !tall && hasTallNeighbour(seed, x, z)) {
            // A drain room cut into an atrium: wall off the step between the two ceiling heights.
            for (int y = ceiling + 1; y <= ATRIUM_CEILING_Y; y++) {
                out.set(y, tile);
            }
        }

        if (kind == ARCHWAY || kind == SUNLIGHT || kind == LINING) {
            fillArchway(x, kind, ceiling, out);
            return;
        }
        if (kind == SHAFT) {
            out.set(DRAIN_FLOOR_Y, dark);
            for (int y = DRAIN_FLOOR_Y + 1; y <= SURFACE_Y; y++) {
                out.set(y, water);
            }
            return;
        }
        if (kind == DEEP) {
            boolean darkFloor = inDrainRoom ? ring <= DARK_RING : isLaneLine(seed, x, z);
            out.set(DEEP_FLOOR_Y, darkFloor ? dark : underWater);
            for (int y = DEEP_FLOOR_Y + 1; y <= SURFACE_Y; y++) {
                out.set(y, water);
            }
            if (inDrainRoom && ring == SHAFT_RADIUS + 1) {
                // The shaft's dark lining, from its bottom up to the pool floor.
                for (int y = DRAIN_FLOOR_Y; y < DEEP_FLOOR_Y; y++) {
                    out.set(y, dark);
                }
            }
            tube(seed, x, z, kind, inDrainRoom, tall, ceiling, out);
            return;
        }
        if (kind == PoolroomsAnomalyPlan.PIT || kind == PoolroomsAnomalyPlan.PIT_STEP) {
            fillPit(seed, x, z, kind, tile, dark, water, out);
            return; // a stair pit's room has no tubes
        }

        // Every other column stands on solid tile from the deep-water floor up to the floor layer,
        // so the water in a neighbouring pool or channel always meets a wall, never air.
        for (int y = DEEP_FLOOR_Y; y < floor; y++) {
            out.set(y, tile);
        }
        if (!inDrainRoom && (kind == DRY || kind == CONTAINER)) {
            pitLining(seed, x, z, dark, out);
        }
        boolean wet = kind == SHALLOW || kind == PoolroomsAnomalyPlan.NOOK;
        // A dark corner's water lies on dark tile, so the nook stays black even in a flashlight beam.
        out.set(floor, kind == SHALLOW ? underWater : kind == PoolroomsAnomalyPlan.NOOK ? dark : tile);
        if (wet) {
            out.set(SURFACE_Y, water);
        } else {
            // Dark tile bands on the drain room's deck lead from each doorway to the pool.
            boolean band = inDrainRoom && (dx == 0 || dz == 0);
            out.set(SURFACE_Y, band ? dark : tile); // walkway, doorway threshold or base of a wall
        }
        if (kind == DOORWAY) {
            int top = SURFACE_Y + doorwayHeight(seed, x, z, inDrainRoom); // last air block
            for (int y = top + 1; y < ceiling; y++) {
                out.set(y, tile);
            }
        } else if (kind == WALL) {
            for (int y = SURFACE_Y + 1; y < ceiling; y++) {
                out.set(y, tile);
            }
        } else if (kind == CONTAINER) {
            out.set(SURFACE_Y + 1, containerAt(seed, x, z));
        }

        Direction lamp = poolLampFacing(seed, x, z, kind, inDrainRoom, dx, dz, ring);
        if (lamp != null) {
            out.set(LAMP_Y, ModBlocks.POOL_LAMP.get().defaultBlockState().setValue(PoolLampBlock.FACING, lamp));
        }
        tube(seed, x, z, kind, inDrainRoom, tall, ceiling, out);
    }

    /**
     * A column of the Sanguine Reservoir: the original column {@code x} (RESERVOIR_OFFSET blocks
     * west), built the usual way and then stripped of its light on the way out. The white tile turns
     * dim, the floors under the water lose their bright caustics, the pool lamps glow red, the tubes
     * are gone and the skylights and the archway's daylight become blank tile. Red sprouts grow on
     * a few dry walkway blocks: the only other light. Canon (Wikidot, Level 37.1): "the lighting
     * fades out entirely... bright red sprouts and a red tint in the water light up the area".
     */
    private void fillReservoirColumn(long seed, int x, int z, Column out) {
        Block tile = ModBlocks.POOL_TILE.get();
        Block floorTile = ModBlocks.POOL_FLOOR.get();
        Block lamp = ModBlocks.POOL_LAMP.get();
        Block tube = ModBlocks.CEILING_TUBE.get();
        Block skylight = ModBlocks.POOL_SKYLIGHT.get();
        Block daylight = ModBlocks.L37_SUNLIT_VOID.get();
        BlockState dimTile = ModBlocks.RESERVOIR_TILE.get().defaultBlockState();
        BlockState dimFloor = ModBlocks.RESERVOIR_FLOOR.get().defaultBlockState();
        BlockState redLamp = ModBlocks.RESERVOIR_LAMP.get().defaultBlockState();
        fillColumn(seed, x, z, (y, state) -> {
            Block block = state.getBlock();
            if (block == tile || block == skylight || block == daylight) {
                out.set(y, dimTile);
            } else if (block == floorTile) {
                out.set(y, dimFloor);
            } else if (block == lamp) {
                out.set(y, redLamp.setValue(PoolLampBlock.FACING, state.getValue(PoolLampBlock.FACING)));
            } else if (block != tube) {
                out.set(y, state);
            }
        });
        if (Hash.unit(seed, x, z, 6300) < SPROUT_SHARE && columnKind(seed, x, z) == DRY && !isSafeRoom(seed, x, z)) {
            out.set(SURFACE_Y + 1, ModBlocks.RESERVOIR_SPROUT.get().defaultBlockState());
        }
    }

    /**
     * A column of a stair pit's shaft: a tile step (one block lower per step down the stairs) or
     * open water, over the pit's dark tile bottom. Water fills everything above, up to the surface.
     */
    private static void fillPit(long seed, int x, int z, int kind, BlockState tile, BlockState dark, BlockState water, Column out) {
        int top = PoolroomsAnomalyPlan.PIT_FLOOR_Y;
        out.set(top, dark);
        if (kind == PoolroomsAnomalyPlan.PIT_STEP) {
            AnomalySite pit = PoolroomsAnomalyPlan.pitOfCell(seed, Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
            top = PoolroomsAnomalyPlan.stepTop(pit, Math.floorMod(x, CELL), Math.floorMod(z, CELL));
            for (int y = PoolroomsAnomalyPlan.PIT_FLOOR_Y; y <= top; y++) {
                out.set(y, tile);
            }
        }
        for (int y = top + 1; y <= SURFACE_Y; y++) {
            out.set(y, water);
        }
    }

    /**
     * The deck right around a stair pit: dark tile below the walkway all the way down to the pit's
     * bottom, so the pit's water meets a wall everywhere (the class comment's "water never leaks").
     */
    private static void pitLining(long seed, int x, int z, BlockState dark, Column out) {
        AnomalySite pit = PoolroomsAnomalyPlan.pitOfCell(seed, Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
        if (pit != null && pit.contains(x, z) && PoolroomsAnomalyPlan.pitLining(pit, Math.floorMod(x, CELL), Math.floorMod(z, CELL))) {
            for (int y = PoolroomsAnomalyPlan.PIT_FLOOR_Y; y < DEEP_FLOOR_Y; y++) {
                out.set(y, dark);
            }
        }
    }

    /** A tube hanging just under the ceiling, if this column is part of a tube strip. */
    private static void tube(long seed, int x, int z, int kind, boolean inDrainRoom, boolean tall, int ceiling, Column out) {
        if (inDrainRoom || tall || kind == WALL || kind == DOORWAY) {
            return; // the drain room stays dim; atriums have skylights instead
        }
        Direction.Axis axis = tubeAxis(seed, x, z);
        if (axis != null) {
            out.set(ceiling - 1, ModBlocks.CEILING_TUBE.get().defaultBlockState().setValue(CeilingTubeBlock.AXIS, axis));
        }
    }

    /**
     * Sparse fluorescent tubes: about 30% of rooms are dark, most of the others get one 4-5 block
     * strip down the middle, some two. Returns the direction of the strip here, or null.
     */
    @Nullable
    private static Direction.Axis tubeAxis(long seed, int x, int z) {
        int cx = Math.floorDiv(x, CELL);
        int cz = Math.floorDiv(z, CELL);
        int mx = Math.floorMod(x, CELL);
        int mz = Math.floorMod(z, CELL);
        int room = room(seed, cx, cz);
        if (kindOf(room) == ROOM_ATRIUM || kindOf(room) == ROOM_SAFE || kindOf(room) == ROOM_SUNLIT
                || Hash.unit(seed, cx, cz, 90) < DARK_ROOM_SHARE) {
            return null; // atriums have skylights, safe rooms their own lamp
        }
        int kind = kindOf(room);
        if (kind == ROOM_DRY || kind == ROOM_SHALLOW || kind == ROOM_POOL) {
            if (PoolroomsAnomalyPlan.pitOfCell(seed, cx, cz) != null) {
                return null; // a stair pit's room is dark
            }
            AnomalySite corner = PoolroomsAnomalyPlan.cornerOfCell(seed, cx, cz);
            if (corner != null && PoolroomsAnomalyPlan.overNook(corner, mx, mz)) {
                return null; // never over a dark corner (a tube beside its screen would be hidden anyway)
            }
        }
        boolean two = kindOf(room) != ROOM_HALL && Hash.unit(seed, cx, cz, 91) < TWO_TUBES_SHARE;
        if (isChannelLike(room)) {
            boolean alongX = axisOf(room) == AXIS_X;
            int along = alongX ? mx : mz;
            int across = alongX ? mz : mx;
            boolean on = across == MIDDLE && (two
                    ? (along >= 2 && along <= 5) || (along >= 9 && along <= 12)
                    : along >= 5 && along <= 9);
            return on ? (alongX ? Direction.Axis.X : Direction.Axis.Z) : null;
        }
        boolean row = kindOf(room) == ROOM_HALL ? mz == 3 : two ? (mz == 4 || mz == 10) : mz == MIDDLE;
        return row && mx >= 5 && mx <= 9 ? Direction.Axis.X : null;
    }

    /**
     * Underwater lamps, set into the wall of deep water facing into it. Returns the lens direction,
     * or null for no lamp here.
     */
    @Nullable
    private Direction poolLampFacing(long seed, int x, int z, int kind, boolean inDrainRoom, int dx, int dz, int ring) {
        if (kind != DRY && kind != SHALLOW) {
            return null;
        }
        if (inDrainRoom) {
            // The drain room: one lamp in the middle of each side of the pool, facing the shaft.
            if (ring != DRAIN_LEDGE_RING || (dx != 0 && dz != 0)) {
                return null;
            }
            if (dx == 0) {
                return dz < 0 ? Direction.SOUTH : Direction.NORTH;
            }
            return dx < 0 ? Direction.EAST : Direction.WEST;
        }
        Direction facing = gridLampFacing(seed, x, z);
        if (facing == null) {
            return null;
        }
        // Only where the water in front really is deep (a drain room may have cut the pool away).
        return columnKind(seed, x + facing.getStepX(), z + facing.getStepZ()) == DEEP ? facing : null;
    }

    @Nullable
    private static Direction gridLampFacing(long seed, int x, int z) {
        int cx = Math.floorDiv(x, CELL);
        int cz = Math.floorDiv(z, CELL);
        int mx = Math.floorMod(x, CELL);
        int mz = Math.floorMod(z, CELL);
        int room = room(seed, cx, cz);
        if (kindOf(room) == ROOM_POOL) {
            // One lamp at each end of the lane lines, in the last block before the deep water.
            int edge = poolStart(Hash.of(seed, cx, cz, 73)) - 1;
            if (laneAxis(seed, cx, cz) == AXIS_X && mz == MIDDLE) {
                return mx == edge ? Direction.EAST : mx == CELL - edge ? Direction.WEST : null;
            }
            if (laneAxis(seed, cx, cz) == AXIS_Z && mx == MIDDLE) {
                return mz == edge ? Direction.SOUTH : mz == CELL - edge ? Direction.NORTH : null;
            }
            return null;
        }
        if (kindOf(room) == ROOM_CHANNEL && isDeep(room)) {
            // One lamp per channel cell, in the middle, on one walkway or the other.
            boolean alongX = axisOf(room) == AXIS_X;
            int along = alongX ? mx : mz;
            int across = alongX ? mz : mx;
            if (along != MIDDLE) {
                return null;
            }
            boolean lowSide = Hash.unit(seed, cx, cz, 93) < 0.5;
            if (lowSide && across == WATER_FROM - 1) {
                return alongX ? Direction.SOUTH : Direction.EAST;
            }
            if (!lowSide && across == WATER_TO + 1) {
                return alongX ? Direction.NORTH : Direction.WEST;
            }
        }
        return null;
    }

    /** Dark lane lines on the floor of pool rooms and atrium pools. */
    private static boolean isLaneLine(long seed, int x, int z) {
        int cx = Math.floorDiv(x, CELL);
        int cz = Math.floorDiv(z, CELL);
        if (isAtrium(seed, cx, cz)) {
            // Three lanes running north-south, stopping short of the pool's ends.
            int ax = Math.floorMod(x, ATRIUM_SIZE);
            int az = Math.floorMod(z, ATRIUM_SIZE);
            return (ax == 11 || ax == 14 || ax == 17) && az >= 7 && az <= 21;
        }
        int room = room(seed, cx, cz);
        if (kindOf(room) != ROOM_POOL) {
            return false;
        }
        int mx = Math.floorMod(x, CELL);
        int mz = Math.floorMod(z, CELL);
        boolean alongX = laneAxis(seed, cx, cz) == AXIS_X;
        int along = alongX ? mx : mz;
        int across = alongX ? mz : mx;
        int start = poolStart(Hash.of(seed, cx, cz, 73));
        return across == MIDDLE && Math.min(along, CELL - along) > start;
    }

    /** Atrium columns, and the walls around them, reach up to the atrium ceiling. */
    private static boolean isTall(long seed, int x, int z) {
        int cx = Math.floorDiv(x, CELL);
        int cz = Math.floorDiv(z, CELL);
        int westCx = Math.floorDiv(x - 1, CELL);
        int northCz = Math.floorDiv(z - 1, CELL);
        return isAtrium(seed, cx, cz) || isAtrium(seed, westCx, cz) || isAtrium(seed, cx, northCz) || isAtrium(seed, westCx, northCz);
    }

    private static boolean hasTallNeighbour(long seed, int x, int z) {
        return isTall(seed, x + 1, z) || isTall(seed, x - 1, z) || isTall(seed, x, z + 1) || isTall(seed, x, z - 1);
    }

    /** Atrium skylights: nine 5x5 panels in a 3x3 grid (atrium-local 3-7, 12-16 and 21-25). */
    private static boolean isSkylight(long seed, int x, int z) {
        if (!isAtrium(seed, Math.floorDiv(x, CELL), Math.floorDiv(z, CELL))) {
            return false; // the atrium's surrounding walls
        }
        int px = Math.floorMod(x, ATRIUM_SIZE) % 9;
        int pz = Math.floorMod(z, ATRIUM_SIZE) % 9;
        return px >= 3 && px <= 7 && pz >= 3 && pz <= 7;
    }
}
