package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.CeilingTubeBlock;
import com.rickr.backrooms.block.DuctBlock;
import com.rickr.backrooms.block.ExitDoorBlock;
import com.rickr.backrooms.block.PipeRackBlock;
import com.rickr.backrooms.block.WallLampBlock;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Hash;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
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
 */
public final class Level2Layout extends LevelLayout {
    // ---- Grid ----------------------------------------------------------------------------------
    /** Distance between grid nodes (and so between parallel corridors). */
    private static final int GRID = 16;
    /** Corridors are 3 wide: local 0..2 across. */
    private static final int WIDTH = 3;
    /** Straight runs are planned in stretches of this many grid steps. */
    private static final int RUN = 6;
    /** Junction rooms span node-local -2..4 (7x7, centred on the 3x3 node). */
    private static final int JUNCTION_LOW = -2;
    private static final int JUNCTION_HIGH = 4;
    private static final int EXIT_ROOM_RADIUS = 7;
    /** Lamps every this many blocks along a lit corridor ... */
    private static final int LAMP_SPACING = 12;
    /** ... and lit/dark stretches change every this many blocks. */
    private static final int LIGHT_ZONE = 48;
    private static final double DARK_SHARE = 0.35;

    // ---- What one column is (packed: kind in bits 0-3, FACING of pipes in bits 4-6) ----------
    private static final int ROCK = 0;
    private static final int WALK = 1;
    /** The pipe stack along a wall: duct, valve or plain wall, two pipe racks. */
    private static final int PIPES = 2;
    /** A doorway into a utility room: 3 tall, wall above. */
    private static final int DOOR = 3;
    private static final int CRATE = 4;
    private static final int EXIT_WALL = 5;
    private static final int EXIT_DOOR = 6;

    public Level2Layout() {
        super(4, new ExitLocator(320, 40, 3000));
    }

    // ==========================================================================================
    // Public queries
    // ==========================================================================================

    @Override
    public boolean isWalkable(long seed, int x, int z) {
        int kind = kindOf(column(seed, x, z));
        return kind == WALK || kind == DOOR;
    }

    // ==========================================================================================
    // The corridor network
    // ==========================================================================================

    /** Each node's guaranteed link: true = to the node west of it, false = to the node north. */
    private static boolean linksWest(long seed, int gx, int gz) {
        return Hash.unit(seed, gx, gz, 300) < 0.5;
    }

    /** True if a corridor runs east from node (gx, gz) to node (gx + 1, gz). */
    private static boolean hasEast(long seed, int gx, int gz) {
        return linksWest(seed, gx + 1, gz) || inRun(seed, gx, gz, 301);
    }

    /** True if a corridor runs south from node (gx, gz) to node (gx, gz + 1). */
    private static boolean hasSouth(long seed, int gx, int gz) {
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

    private static int degree(long seed, int gx, int gz) {
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
    private static boolean pipesNorth(long seed, int gx, int gz) {
        return Hash.unit(seed, Math.floorDiv(gx, RUN), gz, 304) < 0.5;
    }

    /** North-south corridors: pipes along the west wall (true) or the east wall, per run. */
    private static boolean pipesWest(long seed, int gx, int gz) {
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

    // ==========================================================================================
    // Columns
    // ==========================================================================================

    private int column(long seed, int x, int z) {
        int dx = exits().dx(seed, x, z);
        int dz = exits().dz(seed, x, z);
        if (Math.max(Math.abs(dx), Math.abs(dz)) <= EXIT_ROOM_RADIUS) {
            return exitRoomColumn(seed, x, z, dx, dz);
        }
        return gridColumn(seed, x, z);
    }

    private static int gridColumn(long seed, int x, int z) {
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
            return lz == (north ? 0 : WIDTH - 1) ? pipes(north ? Direction.SOUTH : Direction.NORTH) : WALK;
        }
        if (lx < WIDTH) {
            if (!hasSouth(seed, gx, gz)) {
                return ROCK;
            }
            boolean west = pipesWest(seed, gx, gz);
            return lx == (west ? 0 : WIDTH - 1) ? pipes(west ? Direction.EAST : Direction.WEST) : WALK;
        }
        return rockColumn(seed, gx, gz, lx, lz);
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
     *   A: off the corridor along its top edge    x 9-12, z 4-7,  door (11, 3)
     *   B: off the corridor along its bottom edge x 6-9,  z 11-14, door (7, 15)
     *   C: off the corridor along its left edge   x 4-7,  z 6-9,  door (3, 8)
     *   D: off the corridor along its right edge  x 11-14, z 9-12, door (15, 11)
     * </pre>
     * Rooms are only on the plain side of a corridor, never behind its pipes.
     */
    private static int rockColumn(long seed, int gx, int gz, int lx, int lz) {
        if (roomOffEast(seed, gx, gz) && pipesNorth(seed, gx, gz)) {
            int r = utilityRoom(seed, gx, gz, 0, lx, lz, 9, 12, 4, 7, 11, 3, 12, 7);
            if (r != ROCK) return r;
        }
        if (roomOffEast(seed, gx, gz + 1) && !pipesNorth(seed, gx, gz + 1)) {
            int r = utilityRoom(seed, gx, gz, 1, lx, lz, 6, 9, 11, 14, 7, 15, 6, 11);
            if (r != ROCK) return r;
        }
        if (roomOffSouth(seed, gx, gz) && pipesWest(seed, gx, gz)) {
            int r = utilityRoom(seed, gx, gz, 2, lx, lz, 4, 7, 6, 9, 3, 8, 7, 9);
            if (r != ROCK) return r;
        }
        if (roomOffSouth(seed, gx + 1, gz) && !pipesWest(seed, gx + 1, gz)) {
            return utilityRoom(seed, gx, gz, 3, lx, lz, 11, 14, 9, 12, 15, 11, 11, 9);
        }
        return ROCK;
    }

    /** One utility room: interior box, its 1-wide door, and maybe a crate in the far corner. */
    private static int utilityRoom(long seed, int gx, int gz, int which, int lx, int lz,
                                   int x0, int x1, int z0, int z1, int doorX, int doorZ, int crateX, int crateZ) {
        if (lx == doorX && lz == doorZ) {
            return DOOR;
        }
        if (lx < x0 || lx > x1 || lz < z0 || lz > z1) {
            return ROCK;
        }
        if (lx == crateX && lz == crateZ && Hash.unit(seed, gx * 4 + which, gz, 320) < 0.5) {
            return CRATE;
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
        return PIPES | (facing.get2DDataValue() << 4);
    }

    private static int kindOf(int column) {
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
        BlockState wall = ModBlocks.PIPE_WALL.get().defaultBlockState();
        int floor = floorY();
        int ceiling = ceilingY();
        int code = column(seed, x, z);
        int kind = kindOf(code);
        int dx = exits().dx(seed, x, z);
        int dz = exits().dz(seed, x, z);
        boolean inExitRoom = Math.max(Math.abs(dx), Math.abs(dz)) <= EXIT_ROOM_RADIUS;

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
            case PIPES -> fillPipes(seed, x, z, facingOf(code), out);
            default -> {
                // Open floor: maybe a caged lamp up on the wall, or a tube in a room's ceiling.
                Direction lamp = inExitRoom ? null : lampFacing(seed, x, z);
                if (lamp != null) {
                    out.set(ceiling - 1, ModBlocks.BULKHEAD_LAMP.get().defaultBlockState().setValue(WallLampBlock.FACING, lamp));
                } else if (hasTube(seed, x, z, inExitRoom, dx, dz)) {
                    out.set(ceiling - 1, ModBlocks.CEILING_TUBE.get().defaultBlockState().setValue(CeilingTubeBlock.AXIS, Direction.Axis.X));
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
            if (lz != plainRow || Math.floorMod(x + phase, LAMP_SPACING) != 0
                    || Hash.unit(seed, Math.floorDiv(x + phase, LIGHT_ZONE), gz, 331) < DARK_SHARE) {
                return null;
            }
            facing = plainRow == 0 ? Direction.SOUTH : Direction.NORTH;
            wallZ = plainRow == 0 ? z - 1 : z + 1;
        } else if (lx < WIDTH && lz >= WIDTH) {
            int plainCol = pipesWest(seed, gx, gz) ? WIDTH - 1 : 0;
            int phase = Hash.range(Hash.of(seed, gx, 0, 332), LAMP_SPACING);
            if (lx != plainCol || Math.floorMod(z + phase, LAMP_SPACING) != 0
                    || Hash.unit(seed, gx, Math.floorDiv(z + phase, LIGHT_ZONE), 333) < DARK_SHARE) {
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
