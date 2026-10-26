package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.WallLampBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * A floor plan for one Backrooms level, expressed as pure functions of (seed, x, z).
 *
 * <p>The chunk generator asks for one vertical column at a time. Because nothing depends on
 * neighbouring chunks or on generation order, the level is effectively infinite: it extends until
 * Minecraft's world border (~30 million blocks from the centre).
 */
public abstract class LevelLayout {
    public static final int FLOOR_Y = 40;

    /** Receives the blocks of one column. Unset Y levels stay air. */
    @FunctionalInterface
    public interface Column {
        void set(int y, BlockState state);
    }

    private final int interiorHeight;
    private final ExitLocator exits;

    protected LevelLayout(int interiorHeight, ExitLocator exits) {
        this.interiorHeight = interiorHeight;
        this.exits = exits;
    }

    public final int floorY() {
        return FLOOR_Y;
    }

    /** Y of the ceiling block layer (the first solid block above the walkable space). */
    public final int ceilingY() {
        return FLOOR_Y + interiorHeight + 1;
    }

    /**
     * The highest ceiling layer anywhere on this level (light fixtures can be up there). Usually
     * the same as {@link #ceilingY()}; the Poolrooms' atriums are taller than the rest.
     */
    public int highestCeilingY() {
        return ceilingY();
    }

    /**
     * The lowest block layer that can hold a light fixture (the client only looks for lights
     * between this and {@link #highestCeilingY()}). Usually just under the floor; Level 2 hangs a
     * lamp far below it, at the lit doorway of its void.
     */
    public int lowestFixtureY() {
        return floorY() - 1;
    }

    public final int interiorHeight() {
        return interiorHeight;
    }

    /**
     * How tall this level's world is (blocks from y0). Must match "height" in its dimension_type
     * JSON. 64 for the indoor levels; Level 94 needs room for hills and a castle on a mountain.
     */
    public int worldHeight() {
        return 64;
    }

    public final ExitLocator exits() {
        return exits;
    }

    /** Writes every non-air block of column (x, z). */
    public abstract void fillColumn(long seed, int x, int z, Column out);

    /** True when a player or entity can stand at (x, floorY + 1, z): no wall, no hole. */
    public abstract boolean isWalkable(long seed, int x, int z);

    /**
     * Y of a player's feet when standing in column (x, z). Most levels have one flat floor, so
     * this is just floorY + 1; the Poolrooms override it because their dry walkways are raised.
     */
    public int standY(long seed, int x, int z) {
        return FLOOR_Y + 1;
    }

    /**
     * True when it is safe to teleport a player into column (x, z) (arrivals, respawn spots).
     * By default any walkable column; the Poolrooms only allow dry walkways, never water.
     */
    public boolean isSafeArrival(long seed, int x, int z) {
        return isWalkable(seed, x, z);
    }

    /**
     * Where to start looking for an arrival spot when someone is sent to (x, z). Most levels have
     * somewhere to stand everywhere, so (x, z) itself. Level 7 is open sea except for one entrance
     * room per region, so it points at the nearest room (arrivals only search ~96 blocks around).
     */
    public int[] arrivalHint(long seed, int x, int z) {
        return new int[] {x, z};
    }

    /**
     * Exact height of a player's feet in column (x, z). Usually {@link #standY}; Level 9 overrides
     * it because its sidewalks are half slabs (you stand at y + 0.5).
     */
    public double feetY(long seed, int x, int z) {
        return standY(seed, x, z);
    }

    /**
     * True when an arrival spot also needs its four neighbours to be safe (room to move, not
     * squeezed against a wall). Level 9 arrives on a 2-wide sidewalk, where that can never hold.
     */
    public boolean arrivalNeedsSpace() {
        return true;
    }

    /**
     * True for every column of a safe room, its walls and doorway included (see
     * {@link SafeRoomPlan}). Spawners, monsters and the sanity meter ask this every tick, so each
     * level answers it cheaply from the same plan its {@link #fillColumn} builds from. Levels
     * without safe rooms keep this default.
     */
    public boolean isSafeRoom(long seed, int x, int z) {
        return false;
    }

    /**
     * The anomalous area covering column (x, z), or null (see {@code world/anomaly}). Each level
     * builds its anomalies' blocks in {@link #fillColumn} from the same answer, and the server
     * makes them behave ({@code AnomalyService}). Levels without anomalies keep this default.
     */
    @Nullable
    public AnomalySite anomalyAt(long seed, int x, int z) {
        return null;
    }

    /** Every anomalous area within {@code radius} blocks of column (x, z) (for nearby sounds). */
    public void anomaliesNear(long seed, int x, int z, int radius, Consumer<AnomalySite> out) {
    }

    /** The nearest anomalous area of {@code kind} to (x, z), or null (for /backrooms anomaly locate). */
    @Nullable
    public AnomalySite nearestAnomaly(long seed, int x, int z, String kind) {
        return null;
    }

    /** The anomaly kinds this level can have (command suggestions). */
    public List<String> anomalyKinds() {
        return List.of();
    }

    /**
     * The {x, z} of the exit nearest to (x, z), as a player would find it (used by /backrooms
     * locate). Usually the exit locator's point; Level 9 moves it onto the street it belongs to.
     */
    public int[] exitPosition(long seed, int x, int z) {
        return exits.nearest(seed, x, z);
    }

    /**
     * Rough height of the exit at column (x, z) from {@link #exitPosition} (for the hints that point
     * at it: its sound, the NightShot marks). Usually where you would stand there; Level 7's door
     * lies on the sea floor, far below its entrance rooms.
     */
    public int exitY(long seed, int x, int z) {
        return standY(seed, x, z);
    }

    /** Low-frequency "zone" value in [0, 1) shared by blocks within the same zone. */
    protected static double zone(long seed, int x, int z, int zoneSize, int salt) {
        return Hash.unit(seed, Math.floorDiv(x, zoneSize), Math.floorDiv(z, zoneSize), salt);
    }

    // ------------------------------------------------------------------------------------------
    // Exit stairwell (Level 0, Level 1 and Level 4)
    // ------------------------------------------------------------------------------------------

    /** Columns with chessboard distance <= this from the exit belong to the stairwell. */
    public static final int STAIRWELL_RADIUS = 5;
    /** How far the stairs and shaft go below the floor. Falling below transferBelowY ends the level. */
    protected static final int STAIRWELL_DEPTH = 24;

    /** Which wall of the stairwell has the entrance; random per exit but fixed by the seed. */
    protected static Direction stairwellDoorSide(long seed, int exitX, int exitZ) {
        return Direction.from2DDataValue(Hash.range(Hash.of(seed, exitX, exitZ, 4242), 4));
    }

    /**
     * One column of the exit stairwell, a square tower 11x11 centred on the exit (offsets dx, dz):
     * <ul>
     *   <li>ring 5: the level's own wall (seen from the maze) with a single doorway, a door or
     *       plain opening, and a glowing EXIT sign above it;</li>
     *   <li>ring 4: white painted stairwell wall going all the way down the shaft;</li>
     *   <li>ring 3: a landing by the door, then yellow-nosed stairs spiralling down one step per
     *       block, with tube lights on the wall every quarter turn;</li>
     *   <li>ring 2: a chain-link cage around the central well; ring 0-1 is the open well.</li>
     * </ul>
     * Walking down the stairs (or falling) takes you below transferBelowY and on to the next level.
     *
     * @param levelWall     wall block facing the maze (ring 5)
     * @param levelWallBase same, for floor + 1 (baseboard)
     * @param woodenDoor    true: a {@code lobby_door} fills the doorway; false: an open doorway
     * @return true if this column was part of the stairwell (the caller then stops)
     */
    protected boolean stairwell(long seed, int dx, int dz, int exitX, int exitZ,
                                BlockState levelWall, BlockState levelWallBase, boolean woodenDoor, Column out) {
        return stairwell(seed, dx, dz, exitX, exitZ, levelWall, levelWallBase,
                woodenDoor ? ModBlocks.LOBBY_DOOR.get() : null, true, out);
    }

    /**
     * Same stairwell with any door (Level 4 uses a steel fire door with a push bar).
     *
     * @param door     the door block in the doorway, or null for an open doorway
     * @param doorOpen whether that door starts open
     */
    protected boolean stairwell(long seed, int dx, int dz, int exitX, int exitZ, BlockState levelWall,
                                BlockState levelWallBase, @Nullable Block door, boolean doorOpen, Column out) {
        int ring = Math.max(Math.abs(dx), Math.abs(dz));
        if (ring > STAIRWELL_RADIUS) {
            return false;
        }
        int floor = floorY();
        int ceiling = ceilingY();
        int bottom = floor - STAIRWELL_DEPTH;
        BlockState white = ModBlocks.STAIRWELL_WALL.get().defaultBlockState();
        BlockState landing = ModBlocks.CONCRETE_FLOOR.get().defaultBlockState();

        // Work in a "local" frame where the door is always on the north side (negative z), so
        // the stair maths only has to be written once. quarterTurns rotates local -> world.
        Direction doorSide = stairwellDoorSide(seed, exitX, exitZ);
        int quarterTurns = (doorSide.get2DDataValue() + 2) % 4; // NORTH 0, EAST 1, SOUTH 2, WEST 3
        int lx = dx;
        int lz = dz;
        for (int i = 0; i < quarterTurns; i++) { // world -> local is a counter-clockwise turn
            int t = lx;
            lx = lz;
            lz = -t;
        }

        if (ring == 5) {
            out.set(floor, landing);
            out.set(ceiling, levelWall);
            if (lx == 0 && lz == -5) {
                if (door != null) {
                    BlockState state = door.defaultBlockState()
                            .setValue(DoorBlock.FACING, doorSide)
                            .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                            .setValue(DoorBlock.OPEN, doorOpen);
                    out.set(floor + 1, state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                    out.set(floor + 2, state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                }
                out.set(floor + 3, ModBlocks.EXIT_SIGN.get().defaultBlockState());
                for (int y = floor + 4; y < ceiling; y++) {
                    out.set(y, levelWall);
                }
            } else {
                out.set(floor + 1, levelWallBase);
                for (int y = floor + 2; y < ceiling; y++) {
                    out.set(y, levelWall);
                }
            }
            return true;
        }

        out.set(ceiling, white);
        if (ring == 4) {
            boolean doorway = lx == 0 && lz == -4;
            for (int y = bottom; y < ceiling; y++) {
                if (doorway && (y == floor + 1 || y == floor + 2)) {
                    continue; // the opening from the door into the shaft stays air
                }
                out.set(y, doorway && y == floor ? landing : white);
            }
            return true;
        }

        out.set(bottom, white);
        if (ring == 3) {
            int k = ringIndex(lx, lz);
            int stepY;
            if (k <= 1) {
                stepY = floor; // flat landing in front of the door
                out.set(floor, landing);
            } else {
                stepY = floor - (k - 1);
                // Stairs "face" the way you walk to go UP, i.e. against the downward travel direction.
                Direction down = rotate(travelDirection(lx, lz), quarterTurns);
                out.set(stepY, ModBlocks.CONCRETE_STAIRS.get().defaultBlockState()
                        .setValue(StairBlock.FACING, down.getOpposite()));
            }
            // A tube light on the shaft wall every few steps, at about head height: the reference
            // stairwells are the brightest places in the Backrooms.
            if (k == 1 || (k > 1 && k % 4 == 2)) {
                Direction inward = rotate(inwardDirection(lx, lz), quarterTurns);
                out.set(stepY + 2, ModBlocks.WAREHOUSE_LIGHT.get().defaultBlockState().setValue(WallLampBlock.FACING, inward));
            }
            return true;
        }

        if (ring == 2) {
            // Chain-link cage around the well. Each pane links to its ring neighbours.
            BlockState mesh = ModBlocks.CHAIN_LINK.get().defaultBlockState()
                    .setValue(IronBarsBlock.NORTH, Math.max(Math.abs(dx), Math.abs(dz - 1)) == 2)
                    .setValue(IronBarsBlock.SOUTH, Math.max(Math.abs(dx), Math.abs(dz + 1)) == 2)
                    .setValue(IronBarsBlock.WEST, Math.max(Math.abs(dx - 1), Math.abs(dz)) == 2)
                    .setValue(IronBarsBlock.EAST, Math.max(Math.abs(dx + 1), Math.abs(dz)) == 2);
            for (int y = bottom + 1; y < ceiling; y++) {
                out.set(y, mesh);
            }
            return true;
        }

        // Rings 0-1: the open well. One cool strip light in the middle of the stairwell ceiling.
        if (dx == 0 && dz == 0) {
            out.set(ceiling, ModBlocks.PIPE_LIGHT.get().defaultBlockState());
        }
        return true;
    }

    /**
     * Position of a ring-3 tile along the spiral, 0..23, counting clockwise from the tile in
     * front of the door (local (0, -3)).
     */
    private static int ringIndex(int lx, int lz) {
        if (lz == -3) {
            return lx >= 0 ? lx : 24 + lx;     // north side: 0..3, and 21..23 left of the door
        }
        if (lx == 3) {
            return 6 + lz;                      // east side: 4..9
        }
        if (lz == 3) {
            return 9 + (3 - lx);                // south side: 10..15
        }
        return 15 + (3 - lz);                   // west side: 16..20
    }

    /** Direction from this ring-3 tile to the next (lower) one, in the local frame. */
    private static Direction travelDirection(int lx, int lz) {
        if (lz == -3 && lx < 3) return Direction.EAST;
        if (lx == 3 && lz < 3) return Direction.SOUTH;
        if (lz == 3 && lx > -3) return Direction.WEST;
        return Direction.NORTH;
    }

    /** From the shaft wall toward the centre, for a ring-3 tile in the middle of a side. */
    private static Direction inwardDirection(int lx, int lz) {
        if (lz == -3) return Direction.SOUTH;
        if (lz == 3) return Direction.NORTH;
        if (lx == 3) return Direction.WEST;
        return Direction.EAST;
    }

    /** Turns a local-frame direction clockwise {@code quarterTurns} times to get the world direction. */
    private static Direction rotate(Direction direction, int quarterTurns) {
        for (int i = 0; i < quarterTurns; i++) {
            direction = direction.getClockWise();
        }
        return direction;
    }
}
