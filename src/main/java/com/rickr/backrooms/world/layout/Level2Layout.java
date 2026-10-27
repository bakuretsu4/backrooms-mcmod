package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.CeilingTubeBlock;
import com.rickr.backrooms.block.DuctBlock;
import com.rickr.backrooms.block.ExitDoorBlock;
import com.rickr.backrooms.block.HouseDoorBlock;
import com.rickr.backrooms.block.PipeRackBlock;
import com.rickr.backrooms.block.WallLampBlock;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level2.Level2Sites;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * Level 2, "Pipe Dreams": endless narrow maintenance corridors in pale lavender concrete, 3 wide
 * and 4 tall, with real 3D pipes stacked along one wall.
 *
 * <p><b>Floor plan.</b> Corridors follow a hidden 16-block grid. Every grid point is a "node"
 * (a 3x3 floor patch); the corridor between two neighbouring nodes is either there or not.
 * Corridors come in long straight runs (2-5 grid steps, 32-80 blocks), and on top of that every
 * node is always joined to the node west or north of it. Following those links always leads on,
 * so no part of the level is ever sealed off; where runs cross you get L-turns, T-junctions,
 * crossings and dead ends. Some busy nodes widen into 7x7 junction rooms. Side doorways in the
 * plain wall lead into small dark utility rooms, sometimes with a crate.
 *
 * <p><b>A corridor</b>, seen down its length (pipe side varies per run):
 * <pre>
 *   ceiling  y45  #########
 *            y44  # = .*#     = pipe rack (thin rusty pipes on brackets)   * caged wall lamp
 *            y43  # = . .#     = pipe rack
 *            y42  # v . .#     v plain wall (now and then a valve wheel)
 *            y41  # O . .#     O big pale duct (a bolted flange every 4 blocks)
 *   floor    y40  #########
 *                   pipe  2-wide walkway
 * </pre>
 * The duct is solid, so you walk on the 2-wide walkway. Lamps hang on the plain wall just under
 * the ceiling, one every 12 blocks in lit stretches; about a third of the length is pitch dark.
 *
 * <p><b>Exit.</b> One emergency door per 320x320 region, standing in a short wall in the middle
 * of a 15x15 room with tube lights and pipes along its walls. Opening it takes you (and anyone
 * standing next to you) on to the Poolrooms.
 *
 * <p><b>Safe rooms.</b> About 60% of the 128x128 squares (8x8 grid squares) have one: a
 * carpeted, wood-panelled utility room carved into the rock between corridors, its doorway in the
 * plain wall of a corridor. Utility rooms may hold a metal supply locker, and now and then a
 * dropped backpack lies in a corridor.
 *
 * <p><b>Anomalies</b> (see {@link Level2AnomalyPlan} and world/anomaly/level2): brownout sections
 * where the Blackout never ended, hot machinery rooms at dead ends, locked doors with black windows
 * (the rare unlocked one opens on the void below the level) and doors onto a looping office
 * hallway above it. The caged lamps also hang on series circuits that fail now and then (client
 * side, see client/light/FixturePower).
 */
public final class Level2Layout extends LevelLayout {
    // ---- Grid ----------------------------------------------------------------------------------
    /** Distance between grid nodes (and so between parallel corridors). */
    static final int GRID = 16;
    /** Corridors are 3 wide: local 0..2 across. */
    static final int WIDTH = 3;
    /** Straight runs are planned in stretches of this many grid steps. */
    private static final int RUN = 6;
    /** Junction rooms span node-local -2..4 (7x7, centred on the 3x3 node). */
    private static final int JUNCTION_LOW = -2;
    private static final int JUNCTION_HIGH = 4;
    static final int EXIT_ROOM_RADIUS = 7;
    /** Lamps every this many blocks along a lit corridor ... */
    static final int LAMP_SPACING = 12;
    /** ... and lit/dark stretches change every this many blocks. */
    private static final int LIGHT_ZONE = 48;
    private static final double DARK_SHARE = 0.35;

    // ---- What one column is (packed: kind in bits 0-3, FACING of pipes in bits 4-6) ----------
    static final int ROCK = 0;
    static final int WALK = 1;
    /** The pipe stack along a wall: duct, valve or plain wall, two pipe racks. */
    static final int PIPES = 2;
    /** A doorway into a utility room: 3 tall, wall above. */
    private static final int DOOR = 3;
    private static final int CRATE = 4;
    private static final int EXIT_WALL = 5;
    private static final int EXIT_DOOR = 6;
    /** Inside a safe room: floor you can stand on (doorway, free floor) ... */
    private static final int SAFE_OPEN = 7;
    /** ... or its panelled walls and furniture. */
    private static final int SAFE_SOLID = 8;
    /** A dropped backpack on the corridor floor (FACING in bits 4-6). */
    static final int BACKPACK = 9;
    /** A supply locker against a utility room wall (FACING in bits 4-6). */
    private static final int LOCKER = 10;
    /** A locked steel door with a black window in a plain wall (FACING = into the rock, bits 4-6). */
    private static final int LOCKED_DOOR = 11;
    /** Part of an anomaly's structure you can walk in (built by {@link Level2AnomalyPlan}). */
    static final int SITE_OPEN = 12;
    /** Part of an anomaly's structure that is solid (built by {@link Level2AnomalyPlan}). */
    static final int SITE_SOLID = 13;
    /** Out of 1: share of the free door spots in plain walls that hold a locked black-window door. */
    private static final double LOCKED_DOOR_CHANCE = 0.14;

    // ---- Safe rooms ----------------------------------------------------------------------------
    /** Grid squares per side of a safe-room square (128 / 16). */
    private static final int SAFE_SQUARES = SafeRoomPlan.CELL / GRID;
    /** Safe rooms keep at least this far (chessboard distance from their square's centre) from an exit. */
    private static final int SAFE_EXIT_DISTANCE = 24;
    /** The exits, kept in a static field so the static floor-plan helpers can reach them. */
    static final ExitLocator EXITS = new ExitLocator(320, 40, 3000);

    public Level2Layout() {
        super(4, EXITS);
    }

    // ==========================================================================================
    // Public queries
    // ==========================================================================================

    @Override
    public boolean isWalkable(long seed, int x, int z) {
        int kind = kindOf(column(seed, x, z));
        return kind == WALK || kind == DOOR || kind == SAFE_OPEN || kind == SITE_OPEN;
    }

    /**
     * Nobody arrives inside an anomaly: the void door's passage would drop you straight into the
     * void, and a hot zone would greet you with heatstroke.
     */
    @Override
    public boolean isSafeArrival(long seed, int x, int z) {
        int kind = kindOf(column(seed, x, z));
        return kind == WALK || kind == DOOR || kind == SAFE_OPEN;
    }

    /** Everything inside a safe room, walls and doorway included. */
    @Override
    public boolean isSafeRoom(long seed, int x, int z) {
        SafeRoomPlan plan = safeRoomIn(seed, Math.floorDiv(x, GRID), Math.floorDiv(z, GRID));
        return plan != null && plan.contains(x, z);
    }

    @Override
    @Nullable
    public AnomalySite anomalyAt(long seed, int x, int z) {
        return Level2AnomalyPlan.at(seed, x, z);
    }

    @Override
    public void anomaliesNear(long seed, int x, int z, int radius, Consumer<AnomalySite> out) {
        Level2AnomalyPlan.near(seed, x, z, radius, out);
    }

    @Override
    @Nullable
    public AnomalySite nearestAnomaly(long seed, int x, int z, String kind) {
        return Level2AnomalyPlan.nearest(seed, x, z, kind);
    }

    @Override
    public List<String> anomalyKinds() {
        return Level2Sites.KINDS;
    }

    /** The looping hallway's ceiling lights hang above the tunnels (y51). */
    @Override
    public int highestCeilingY() {
        return ceilingY() + Level2Sites.LOOP_RISE;
    }

    /** The lamp beside the void's copy of a void door hangs far below the floor. */
    @Override
    public int lowestFixtureY() {
        return floorY() - Level2Sites.VOID_DROP;
    }

    // ==========================================================================================
    // Safe rooms
    // ==========================================================================================

    /**
     * The safe room in grid square (gx, gz) (the rock between the corridors of nodes gx..gx+1,
     * gz..gz+1), or null. Each 8x8 block of squares tries up to three random squares and uses the
     * first one that works; a square works when one of its corridors has its plain wall on the
     * square's side (the doorway can't open behind the pipes) and no exit is near.
     */
    @Nullable
    static SafeRoomPlan safeRoomIn(long seed, int gx, int gz) {
        int cellX = Math.floorDiv(gx, SAFE_SQUARES);
        int cellZ = Math.floorDiv(gz, SAFE_SQUARES);
        long h = SafeRoomPlan.cellHash(seed, cellX, cellZ, 420);
        if (!SafeRoomPlan.rolled(h)) {
            return null;
        }
        // Quick answer for most squares: not one of the three candidates at all.
        int match = -1;
        for (int k = 0; k < 3 && match < 0; k++) {
            long hk = Hash.mix(h + k);
            if (cellX * SAFE_SQUARES + Hash.range(hk, SAFE_SQUARES) == gx
                    && cellZ * SAFE_SQUARES + Hash.range(Hash.mix(hk), SAFE_SQUARES) == gz) {
                match = k;
            }
        }
        if (match < 0) {
            return null;
        }
        for (int k = 0; k <= match; k++) {
            long hk = Hash.mix(h + k);
            int sx = cellX * SAFE_SQUARES + Hash.range(hk, SAFE_SQUARES);
            int sz = cellZ * SAFE_SQUARES + Hash.range(Hash.mix(hk), SAFE_SQUARES);
            Direction door = safeDoorSide(seed, sx, sz, hk);
            if (door == null || nearExit(seed, sx, sz)) {
                continue; // this candidate doesn't work: try the next one
            }
            return k == match ? safeRoomPlan(sx, sz, door) : null; // an earlier candidate won
        }
        return null;
    }

    /** A side of square (gx, gz) whose corridor has its plain wall facing the square, or null. */
    @Nullable
    private static Direction safeDoorSide(long seed, int gx, int gz, long hash) {
        int start = Hash.range(Hash.mix(hash ^ 7), 4);
        for (int i = 0; i < 4; i++) {
            Direction side = Direction.from2DDataValue((start + i) & 3);
            boolean ok = switch (side) {
                case NORTH -> hasEast(seed, gx, gz) && pipesNorth(seed, gx, gz);          // pipes on its far side
                case SOUTH -> hasEast(seed, gx, gz + 1) && !pipesNorth(seed, gx, gz + 1);
                case WEST -> hasSouth(seed, gx, gz) && pipesWest(seed, gx, gz);
                default -> hasSouth(seed, gx + 1, gz) && !pipesWest(seed, gx + 1, gz);   // EAST
            };
            if (ok) {
                return side;
            }
        }
        return null;
    }

    private static boolean nearExit(long seed, int gx, int gz) {
        int cx = gx * GRID + GRID / 2;
        int cz = gz * GRID + GRID / 2;
        int[] exit = EXITS.nearest(seed, cx, cz);
        return Math.max(Math.abs(exit[0] - cx), Math.abs(exit[1] - cz)) < SAFE_EXIT_DISTANCE;
    }

    /**
     * The room inside square (gx, gz): 7 wide inside, 8 deep, its door wall right against the
     * corridor (local 3..15 is rock; the 2x2 corners stay free for junction rooms).
     */
    private static SafeRoomPlan safeRoomPlan(int gx, int gz, Direction door) {
        int bx = gx * GRID;
        int bz = gz * GRID;
        return switch (door) {
            case NORTH -> new SafeRoomPlan(bx + 5, bz + 3, 9, 11, door, 1);
            case SOUTH -> new SafeRoomPlan(bx + 5, bz + 5, 9, 11, door, 1);
            case WEST -> new SafeRoomPlan(bx + 3, bz + 5, 11, 9, door, 1);
            default -> new SafeRoomPlan(bx + 5, bz + 5, 11, 9, door, 1); // EAST
        };
    }

    /** Column code of a safe-room column. Its outer wall layer is ordinary rock (lamps may hang on it). */
    private static int safeColumn(SafeRoomPlan plan, int x, int z) {
        if (plan.isOuterWall(x, z)) {
            return ROCK;
        }
        return plan.isWalkable(x, z) ? SAFE_OPEN : SAFE_SOLID;
    }

    private void fillSafeRoom(long seed, int x, int z, Column out) {
        SafeRoomPlan plan = safeRoomIn(seed, Math.floorDiv(x, GRID), Math.floorDiv(z, GRID));
        BlockState wall = ModBlocks.PIPE_WALL.get().defaultBlockState();
        plan.fill(x, z, floorY(), ceilingY(), new SafeRoomPlan.Style(
                ModBlocks.BEIGE_CARPET.get().defaultBlockState(),
                ModBlocks.PIPE_CEILING.get().defaultBlockState(),
                wall,
                wall,
                ModBlocks.WOOD_FLOOR.get().defaultBlockState(),    // wood panelling below the dado line
                ModBlocks.DIM_WALLPAPER.get().defaultBlockState(), // old wallpaper above it
                null), out);
    }

    // ==========================================================================================
    // The corridor network
    // ==========================================================================================

    /** Each node's guaranteed link: true = to the node west of it, false = to the node north. */
    private static boolean linksWest(long seed, int gx, int gz) {
        return Hash.unit(seed, gx, gz, 300) < 0.5;
    }

    /** True if a corridor runs east from node (gx, gz) to node (gx + 1, gz). */
    static boolean hasEast(long seed, int gx, int gz) {
        return linksWest(seed, gx + 1, gz) || inRun(seed, gx, gz, 301);
    }

    /** True if a corridor runs south from node (gx, gz) to node (gx, gz + 1). */
    static boolean hasSouth(long seed, int gx, int gz) {
        return !linksWest(seed, gx, gz + 1) || inRun(seed, gz, gx, 302);
    }

    /**
     * Long straights: the line of nodes {@code line} is cut into stretches of RUN steps; about
     * half the stretches carry one unbroken corridor 2-5 steps long. {@code step} is the position
     * along the line.
     */
    private static boolean inRun(long seed, int step, int line, int salt) {
        int stretch = Math.floorDiv(step, RUN);
        long h = Hash.of(seed, stretch, line, salt);
        if (Hash.unit(h) >= 0.55) {
            return false;
        }
        int start = Hash.range(Hash.mix(h), 3);
        int length = 2 + Hash.range(Hash.mix(h ^ 0x3C6EF372FE94F82BL), 4);
        int i = step - stretch * RUN;
        return i >= start && i < start + length;
    }

    static int degree(long seed, int gx, int gz) {
        int n = 0;
        if (hasEast(seed, gx, gz)) n++;
        if (hasEast(seed, gx - 1, gz)) n++;
        if (hasSouth(seed, gx, gz)) n++;
        if (hasSouth(seed, gx, gz - 1)) n++;
        return n;
    }

    /** Some T-junctions and crossings widen into a 7x7 room. */
    private static boolean isJunctionRoom(long seed, int gx, int gz) {
        return Hash.unit(seed, gx, gz, 303) < 0.4 && degree(seed, gx, gz) >= 3;
    }

    /** East-west corridors: pipes along the north wall (true) or the south wall, per run. */
    static boolean pipesNorth(long seed, int gx, int gz) {
        return Hash.unit(seed, Math.floorDiv(gx, RUN), gz, 304) < 0.5;
    }

    /** North-south corridors: pipes along the west wall (true) or the east wall, per run. */
    static boolean pipesWest(long seed, int gx, int gz) {
        return Hash.unit(seed, gx, Math.floorDiv(gz, RUN), 305) < 0.5;
    }

    /** A utility room off the plain wall of the corridor east of (gx, gz). */
    private static boolean roomOffEast(long seed, int gx, int gz) {
        return hasEast(seed, gx, gz) && Hash.unit(seed, gx, gz, 306) < 0.18;
    }

    /** A utility room off the plain wall of the corridor south of (gx, gz). */
    private static boolean roomOffSouth(long seed, int gx, int gz) {
        return hasSouth(seed, gx, gz) && Hash.unit(seed, gx, gz, 307) < 0.18;
    }

    /** True if grid square (gx, gz) holds any utility room (see {@link #rockColumn}). */
    static boolean hasUtilityRoom(long seed, int gx, int gz) {
        return (roomOffEast(seed, gx, gz) && pipesNorth(seed, gx, gz))
                || (roomOffEast(seed, gx, gz + 1) && !pipesNorth(seed, gx, gz + 1))
                || (roomOffSouth(seed, gx, gz) && pipesWest(seed, gx, gz))
                || (roomOffSouth(seed, gx + 1, gz) && !pipesWest(seed, gx + 1, gz));
    }

    // ==========================================================================================
    // Columns
    // ==========================================================================================

    int column(long seed, int x, int z) {
        int dx = exits().dx(seed, x, z);
        int dz = exits().dz(seed, x, z);
        if (Math.max(Math.abs(dx), Math.abs(dz)) <= EXIT_ROOM_RADIUS) {
            return exitRoomColumn(seed, x, z, dx, dz);
        }
        SafeRoomPlan safe = safeRoomIn(seed, Math.floorDiv(x, GRID), Math.floorDiv(z, GRID));
        if (safe != null && safe.contains(x, z)) {
            return safeColumn(safe, x, z);
        }
        // An anomaly's own rooms and doors (anomalies never overlap exits or safe rooms).
        int site = Level2AnomalyPlan.column(seed, x, z);
        if (site >= 0) {
            return site;
        }
        return gridColumn(seed, x, z);
    }

    static int gridColumn(long seed, int x, int z) {
        // Junction rooms reach 2 blocks before their node, so look them up from (x + 2, z + 2).
        int nx = Math.floorDiv(x - JUNCTION_LOW, GRID);
        int nz = Math.floorDiv(z - JUNCTION_LOW, GRID);
        int jx = x - nx * GRID;
        int jz = z - nz * GRID;
        if (jx <= JUNCTION_HIGH && jz <= JUNCTION_HIGH && isJunctionRoom(seed, nx, nz)) {
            return junctionColumn(seed, nx, nz, jx, jz);
        }
        int gx = Math.floorDiv(x, GRID);
        int gz = Math.floorDiv(z, GRID);
        int lx = x - gx * GRID;
        int lz = z - gz * GRID;
        if (lx < WIDTH && lz < WIDTH) {
            return nodeColumn(seed, gx, gz, lx, lz);
        }
        if (lz < WIDTH) {
            if (!hasEast(seed, gx, gz)) {
                return ROCK;
            }
            boolean north = pipesNorth(seed, gx, gz);
            if (lz == (north ? 0 : WIDTH - 1)) {
                return pipes(north ? Direction.SOUTH : Direction.NORTH);
            }
            return lz == 1 ? corridorBackpack(seed, gx, gz, 0, lx) : WALK;
        }
        if (lx < WIDTH) {
            if (!hasSouth(seed, gx, gz)) {
                return ROCK;
            }
            boolean west = pipesWest(seed, gx, gz);
            if (lx == (west ? 0 : WIDTH - 1)) {
                return pipes(west ? Direction.EAST : Direction.WEST);
            }
            return lx == 1 ? corridorBackpack(seed, gx, gz, 1, lz) : WALK;
        }
        return rockColumn(seed, gx, gz, lx, lz);
    }

    /**
     * Now and then a dropped backpack lies in the middle row of a corridor (next to the pipes, so
     * never right in front of a doorway): about one per 220 blocks of walkway. {@code axis} 0 =
     * the corridor east of node (gx, gz), 1 = the one south of it; {@code along} = 3..15 along it.
     * Returns the column code: BACKPACK with its facing, or WALK.
     */
    private static int corridorBackpack(long seed, int gx, int gz, int axis, int along) {
        long h = Hash.of(seed, gx, gz, 350 + axis);
        // Positions 5..13 stay clear of junction rooms (which reach 2 blocks into the corridor).
        if (Hash.unit(h) >= 0.06 || along != 5 + Hash.range(Hash.mix(h), 9)) {
            return WALK;
        }
        return withFacing(BACKPACK, Direction.from2DDataValue(Hash.range(Hash.mix(h ^ 1), 4)));
    }

    /**
     * A plain node: open floor. Where a corridor just runs straight through (no side turnings)
     * and keeps its pipes on the same side, the pipes run on through the node too.
     */
    private static int nodeColumn(long seed, int gx, int gz, int lx, int lz) {
        boolean east = hasEast(seed, gx, gz);
        boolean west = hasEast(seed, gx - 1, gz);
        boolean south = hasSouth(seed, gx, gz);
        boolean north = hasSouth(seed, gx, gz - 1);
        if (east && west && !north && !south && pipesNorth(seed, gx, gz) == pipesNorth(seed, gx - 1, gz)) {
            boolean pipeNorth = pipesNorth(seed, gx, gz);
            if (lz == (pipeNorth ? 0 : WIDTH - 1)) {
                return pipes(pipeNorth ? Direction.SOUTH : Direction.NORTH);
            }
        }
        if (north && south && !east && !west && pipesWest(seed, gx, gz) == pipesWest(seed, gx, gz - 1)) {
            boolean pipeWest = pipesWest(seed, gx, gz);
            if (lx == (pipeWest ? 0 : WIDTH - 1)) {
                return pipes(pipeWest ? Direction.EAST : Direction.WEST);
            }
        }
        return WALK;
    }

    /**
     * A 7x7 junction room (node-local -2..4). Each side with no corridor coming in gets a pipe
     * stack along it (not in the corners, so stacks never collide).
     */
    private static int junctionColumn(long seed, int gx, int gz, int jx, int jz) {
        boolean inner = jx > JUNCTION_LOW && jx < JUNCTION_HIGH;
        boolean innerZ = jz > JUNCTION_LOW && jz < JUNCTION_HIGH;
        if (jz == JUNCTION_LOW && inner && !hasSouth(seed, gx, gz - 1)) {
            return pipes(Direction.SOUTH);
        }
        if (jz == JUNCTION_HIGH && inner && !hasSouth(seed, gx, gz)) {
            return pipes(Direction.NORTH);
        }
        if (jx == JUNCTION_LOW && innerZ && !hasEast(seed, gx - 1, gz)) {
            return pipes(Direction.EAST);
        }
        if (jx == JUNCTION_HIGH && innerZ && !hasEast(seed, gx, gz)) {
            return pipes(Direction.WEST);
        }
        return WALK;
    }

    /**
     * Solid concrete between corridors, except for the small utility rooms. A grid square holds
     * up to four of them, at fixed spots chosen so they never touch each other, a corridor or a
     * junction room (local coordinates 3..15):
     * <pre>
     *   A: off the corridor along its top edge    x 9-12, z 4-7,  door (11, 3),  locker (9, 7)
     *   B: off the corridor along its bottom edge x 6-9,  z 11-14, door (7, 15), locker (9, 11)
     *   C: off the corridor along its left edge   x 4-7,  z 6-9,  door (3, 8),   locker (7, 6)
     *   D: off the corridor along its right edge  x 11-14, z 9-12, door (15, 11), locker (11, 12)
     * </pre>
     * Rooms are only on the plain side of a corridor, never behind its pipes. A square with a safe
     * room has no utility rooms (the safe room takes the rock).
     *
     * <p>A door spot without a room may instead hold a locked steel door with a pitch-black window
     * (the void doors of https://backrooms-wiki.wikidot.com/level-2): trying it only rattles the
     * handle. The rare one that opens is an anomaly site ({@link Level2AnomalyPlan}).
     */
    private static int rockColumn(long seed, int gx, int gz, int lx, int lz) {
        if (safeRoomIn(seed, gx, gz) != null) {
            return ROCK;
        }
        if (roomOffEast(seed, gx, gz) && pipesNorth(seed, gx, gz)) {
            int r = utilityRoom(seed, gx, gz, 0, lx, lz, 9, 12, 4, 7, 11, 3, 12, 7, 9, 7, Direction.NORTH);
            if (r != ROCK) return r;
        }
        if (roomOffEast(seed, gx, gz + 1) && !pipesNorth(seed, gx, gz + 1)) {
            int r = utilityRoom(seed, gx, gz, 1, lx, lz, 6, 9, 11, 14, 7, 15, 6, 11, 9, 11, Direction.SOUTH);
            if (r != ROCK) return r;
        }
        if (roomOffSouth(seed, gx, gz) && pipesWest(seed, gx, gz)) {
            int r = utilityRoom(seed, gx, gz, 2, lx, lz, 4, 7, 6, 9, 3, 8, 7, 9, 7, 6, Direction.WEST);
            if (r != ROCK) return r;
        }
        if (roomOffSouth(seed, gx + 1, gz) && !pipesWest(seed, gx + 1, gz)) {
            return utilityRoom(seed, gx, gz, 3, lx, lz, 11, 14, 9, 12, 15, 11, 11, 9, 11, 12, Direction.EAST);
        }
        return lockedDoor(seed, gx, gz, lx, lz);
    }

    /** A locked black-window door at a free door spot of square (gx, gz) (FACING = into the rock), or ROCK. */
    private static int lockedDoor(long seed, int gx, int gz, int lx, int lz) {
        if (lx == 11 && lz == 3 && hasEast(seed, gx, gz) && pipesNorth(seed, gx, gz)) {
            return lockedDoorRoll(seed, gx * 4, gz, Direction.SOUTH);
        }
        if (lx == 7 && lz == 15 && hasEast(seed, gx, gz + 1) && !pipesNorth(seed, gx, gz + 1)) {
            return lockedDoorRoll(seed, gx * 4 + 1, gz, Direction.NORTH);
        }
        if (lx == 3 && lz == 8 && hasSouth(seed, gx, gz) && pipesWest(seed, gx, gz)) {
            return lockedDoorRoll(seed, gx * 4 + 2, gz, Direction.EAST);
        }
        if (lx == 15 && lz == 11 && hasSouth(seed, gx + 1, gz) && !pipesWest(seed, gx + 1, gz)) {
            return lockedDoorRoll(seed, gx * 4 + 3, gz, Direction.WEST);
        }
        return ROCK;
    }

    private static int lockedDoorRoll(long seed, int spot, int gz, Direction intoRock) {
        return Hash.unit(seed, spot, gz, 322) < LOCKED_DOOR_CHANCE ? withFacing(LOCKED_DOOR, intoRock) : ROCK;
    }

    /**
     * One utility room: interior box, its 1-wide door, maybe a crate in the far corner and maybe a
     * supply locker against the back wall (facing the door side, away from the wall).
     */
    private static int utilityRoom(long seed, int gx, int gz, int which, int lx, int lz,
                                   int x0, int x1, int z0, int z1, int doorX, int doorZ, int crateX, int crateZ,
                                   int lockerX, int lockerZ, Direction lockerFacing) {
        if (lx == doorX && lz == doorZ) {
            return DOOR;
        }
        if (lx < x0 || lx > x1 || lz < z0 || lz > z1) {
            return ROCK;
        }
        if (lx == crateX && lz == crateZ && Hash.unit(seed, gx * 4 + which, gz, 320) < 0.5) {
            return CRATE;
        }
        if (lx == lockerX && lz == lockerZ && Hash.unit(seed, gx * 4 + which, gz, 321) < 0.35) {
            return withFacing(LOCKER, lockerFacing);
        }
        return WALK;
    }

    /** The exit room: a 15x15 hall with the free-standing exit wall in the middle. */
    private static int exitRoomColumn(long seed, int x, int z, int dx, int dz) {
        if (dz == 0 && Math.abs(dx) <= 2) {
            return dx == 0 ? EXIT_DOOR : EXIT_WALL;
        }
        // Pipes along the north and south walls, but never across a corridor coming in.
        if (Math.abs(dz) == EXIT_ROOM_RADIUS && Math.abs(dx) < EXIT_ROOM_RADIUS
                && kindOf(gridColumn(seed, x, z + Integer.signum(dz))) == ROCK) {
            return pipes(dz < 0 ? Direction.SOUTH : Direction.NORTH);
        }
        return WALK;
    }

    private static int pipes(Direction facing) {
        return withFacing(PIPES, facing);
    }

    /** Packs a column kind together with a facing (bits 4-6). */
    static int withFacing(int kind, Direction facing) {
        return kind | (facing.get2DDataValue() << 4);
    }

    static int kindOf(int column) {
        return column & 15;
    }

    private static Direction facingOf(int column) {
        return Direction.from2DDataValue(column >> 4);
    }

    // ==========================================================================================
    // Blocks
    // ==========================================================================================

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        AnomalySite site = Level2AnomalyPlan.at(seed, x, z); // null almost everywhere
        fillLevelColumn(seed, x, z, column(seed, x, z), site, out);
        if (site != null) {
            // The parts of an anomaly above or below the level: the void and its copy of the
            // door, or the looping hallway over the tunnels.
            Level2AnomalyPlan.fillOutside(this, seed, site, x, z, out);
        }
    }

    /**
     * The blocks of one column between floor and ceiling, for column code {@code code}. Also used
     * (through a shifted {@code out}) to build the exact copies of the tunnel beside a void or loop
     * door, so they always match the real thing.
     *
     * @param site the anomaly at (x, z), or null
     */
    void fillLevelColumn(long seed, int x, int z, int code, @Nullable AnomalySite site, Column out) {
        BlockState wall = ModBlocks.PIPE_WALL.get().defaultBlockState();
        int floor = floorY();
        int ceiling = ceilingY();
        int kind = kindOf(code);
        if (kind == SAFE_OPEN || kind == SAFE_SOLID) {
            fillSafeRoom(seed, x, z, out);
            return;
        }
        if (kind == SITE_OPEN || kind == SITE_SOLID) {
            Level2AnomalyPlan.fillSiteColumn(this, site, x, z, out);
            return;
        }
        int dx = exits().dx(seed, x, z);
        int dz = exits().dz(seed, x, z);
        boolean inExitRoom = Math.max(Math.abs(dx), Math.abs(dz)) <= EXIT_ROOM_RADIUS;
        boolean brownout = site != null && site.kind().equals(Level2Sites.BROWNOUT);

        out.set(ceiling, ModBlocks.PIPE_CEILING.get().defaultBlockState());
        out.set(floor, hasGrate(seed, x, z, kind, inExitRoom, dx, dz)
                ? ModBlocks.FLOOR_GRATE.get().defaultBlockState()
                : ModBlocks.PIPE_FLOOR.get().defaultBlockState());

        switch (kind) {
            case ROCK, EXIT_WALL -> {
                for (int y = floor + 1; y < ceiling; y++) {
                    out.set(y, wall);
                }
            }
            case EXIT_DOOR -> {
                out.set(floor + 1, ModBlocks.EXIT_DOOR.get().defaultBlockState().setValue(ExitDoorBlock.HALF, DoubleBlockHalf.LOWER));
                out.set(floor + 2, ModBlocks.EXIT_DOOR.get().defaultBlockState().setValue(ExitDoorBlock.HALF, DoubleBlockHalf.UPPER));
                out.set(floor + 3, ModBlocks.EXIT_SIGN.get().defaultBlockState());
                out.set(floor + 4, wall);
            }
            case DOOR -> out.set(floor + 4, wall); // the lintel over a utility room doorway
            case CRATE -> out.set(floor + 1, ModBlocks.SUPPLY_CRATE.get().defaultBlockState());
            case BACKPACK -> out.set(floor + 1, ModBlocks.BACKPACK.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, facingOf(code)));
            case LOCKER -> out.set(floor + 1, ModBlocks.SUPPLY_LOCKER.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, facingOf(code)));
            case LOCKED_DOOR -> {
                // Flush with the corridor wall (the door's slab sits on the corridor side), wall above.
                BlockState door = ModBlocks.L2_VOID_DOOR.get().defaultBlockState()
                        .setValue(DoorBlock.FACING, facingOf(code))
                        .setValue(HouseDoorBlock.LOCKED, true);
                out.set(floor + 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                out.set(floor + 2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                out.set(floor + 3, wall);
                out.set(floor + 4, wall);
            }
            case PIPES -> fillPipes(seed, x, z, facingOf(code), out);
            default -> {
                // Open floor: maybe a caged lamp up on the wall, or a tube in a room's ceiling.
                Direction lamp = inExitRoom ? null : lampFacing(seed, x, z);
                lamp = Level2AnomalyPlan.doorLamp(site, x, z, lamp); // a void/loop door keeps one lamp beside it
                if (lamp != null) {
                    // In a brownout nearly every bulb burst in the Blackout.
                    BlockState bulb = brownout && !Level2AnomalyPlan.lampSurvives(seed, x, z)
                            ? ModBlocks.L2_DEAD_LAMP.get().defaultBlockState()
                            : ModBlocks.BULKHEAD_LAMP.get().defaultBlockState();
                    out.set(ceiling - 1, bulb.setValue(WallLampBlock.FACING, lamp));
                } else if (!brownout && hasTube(seed, x, z, inExitRoom, dx, dz)) {
                    out.set(ceiling - 1, ModBlocks.CEILING_TUBE.get().defaultBlockState().setValue(CeilingTubeBlock.AXIS, Direction.Axis.X));
                }
                if (brownout) {
                    Level2AnomalyPlan.fillBrownout(this, seed, x, z, out);
                }
            }
        }
    }

    /**
     * The pipe stack against a wall: the big duct at floor level (a flange every 4 blocks), then
     * bare wall or now and then a valve wheel, then two pipe racks (a bracket every 3 blocks).
     */
    private void fillPipes(long seed, int x, int z, Direction facing, Column out) {
        int floor = floorY();
        int along = facing.getAxis() == Direction.Axis.Z ? x : z; // pipes run across FACING
        out.set(floor + 1, ModBlocks.PALE_DUCT.get().defaultBlockState()
                .setValue(WallMountBlock.FACING, facing)
                .setValue(DuctBlock.FLANGE, Math.floorMod(along, 4) == 0));
        if (Hash.unit(seed, x, z, 341) < 0.06) {
            out.set(floor + 2, ModBlocks.PIPE_VALVE.get().defaultBlockState().setValue(WallMountBlock.FACING, facing));
        }
        BlockState rack = ModBlocks.RUSTY_PIPE.get().defaultBlockState()
                .setValue(WallMountBlock.FACING, facing)
                .setValue(PipeRackBlock.BRACKET, Math.floorMod(along, 3) == 0);
        out.set(floor + 3, rack);
        out.set(floor + 4, rack);
    }

    /**
     * Caged lamps hang on the plain wall of corridors, one every LAMP_SPACING blocks, just under
     * the ceiling, in lit stretches only. Returns the way the lamp faces, or null.
     */
    @Nullable
    private Direction lampFacing(long seed, int x, int z) {
        return wallFixtureFacing(seed, x, z, 0, true);
    }

    /**
     * Where something hangs on the plain wall of a corridor, one every LAMP_SPACING blocks: the
     * way it faces, or null. {@code shift} slides the spots along the corridor (the brownout's
     * emergency lights hang halfway between lamp spots); {@code litOnly} skips the dark stretches.
     */
    @Nullable
    Direction wallFixtureFacing(long seed, int x, int z, int shift, boolean litOnly) {
        int gx = Math.floorDiv(x, GRID);
        int gz = Math.floorDiv(z, GRID);
        int lx = x - gx * GRID;
        int lz = z - gz * GRID;
        Direction facing;
        int wallX = x;
        int wallZ = z;
        if (lz < WIDTH && lx >= WIDTH) {
            // East-west corridor: the plain wall is the side without pipes.
            int plainRow = pipesNorth(seed, gx, gz) ? WIDTH - 1 : 0;
            int phase = Hash.range(Hash.of(seed, gz, 0, 330), LAMP_SPACING);
            if (lz != plainRow || Math.floorMod(x + phase + shift, LAMP_SPACING) != 0
                    || (litOnly && Hash.unit(seed, Math.floorDiv(x + phase, LIGHT_ZONE), gz, 331) < DARK_SHARE)) {
                return null;
            }
            facing = plainRow == 0 ? Direction.SOUTH : Direction.NORTH;
            wallZ = plainRow == 0 ? z - 1 : z + 1;
        } else if (lx < WIDTH && lz >= WIDTH) {
            int plainCol = pipesWest(seed, gx, gz) ? WIDTH - 1 : 0;
            int phase = Hash.range(Hash.of(seed, gx, 0, 332), LAMP_SPACING);
            if (lx != plainCol || Math.floorMod(z + phase + shift, LAMP_SPACING) != 0
                    || (litOnly && Hash.unit(seed, gx, Math.floorDiv(z + phase, LIGHT_ZONE), 333) < DARK_SHARE)) {
                return null;
            }
            facing = plainCol == 0 ? Direction.EAST : Direction.WEST;
            wallX = plainCol == 0 ? x - 1 : x + 1;
        } else {
            return null;
        }
        // Only on a real wall (not over a doorway, a junction room or the exit room).
        return kindOf(column(seed, wallX, wallZ)) == ROCK && kindOf(gridColumn(seed, x, z)) == WALK ? facing : null;
    }

    /** Tube strips: one across the middle of each lit junction room, two in the exit room. */
    private static boolean hasTube(long seed, int x, int z, boolean inExitRoom, int dx, int dz) {
        if (inExitRoom) {
            return Math.abs(dz) == 4 && Math.abs(dx) <= 2;
        }
        int nx = Math.floorDiv(x - JUNCTION_LOW, GRID);
        int nz = Math.floorDiv(z - JUNCTION_LOW, GRID);
        int jx = x - nx * GRID;
        int jz = z - nz * GRID;
        return jz == 1 && jx >= 0 && jx <= 2 && isJunctionRoom(seed, nx, nz)
                && Hash.unit(seed, nx, nz, 342) >= 0.25;
    }

    /** Drain grates: in the middle of junction rooms, beside the exit wall, and now and then in a corridor. */
    private static boolean hasGrate(long seed, int x, int z, int kind, boolean inExitRoom, int dx, int dz) {
        if (kind != WALK) {
            return false;
        }
        if (inExitRoom) {
            return dz == 0 && Math.abs(dx) == 5;
        }
        int nx = Math.floorDiv(x - JUNCTION_LOW, GRID);
        int nz = Math.floorDiv(z - JUNCTION_LOW, GRID);
        if (x - nx * GRID == 1 && z - nz * GRID == 1 && isJunctionRoom(seed, nx, nz)) {
            return true;
        }
        return Hash.unit(seed, x, z, 340) < 0.02;
    }
}
