package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.Level228BulbBlock;
import com.rickr.backrooms.block.Level228MirrorBlock;
import com.rickr.backrooms.registry.Level228Blocks;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.ExitLocator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Level 228, "Hall of Mirrors": the blocks of the one carnival mirror maze of a world
 * ({@link Level228Plan}, {@link Level228Maze}). Blocks are deliberately plain: a floor, a ceiling,
 * dark plywood walls backstage, invisible collision for columns and panes. Everything you look at
 * in the maze itself (columns, frames, silvering, arches, decor) is a prop drawn by the client;
 * the light comes from invisible {@code l228_bulb} blocks at every live bulb, in the images too.
 *
 * <pre>
 *   y 39        plywood under the floor (nothing shows through)
 *   y 40        the floor
 *   y 41..43    panes (mirror, glass, painted panel: the thin l228_mirror block, invisible), posts
 *   y 44        the frieze band: open over panes (fretwork props), posts, tall mirrors, bulbs
 *   y 45        the ceiling; y 46 plywood over it
 * </pre>
 *
 * <p>The chunk generator builds the shift state the server has right now ({@link #liveBits});
 * chunks saved under an older state are brought up to date by world/Level228Rules when they
 * load, so the panes the server collides with are always the ones the clients draw.
 */
public final class Level228Layout extends LevelLayout {
    /**
     * The server's current ShiftState bits, for chunks generated from now on (world gen runs on
     * worker threads, hence volatile). Set by world/Level228Rules; 0 until a server sets it.
     */
    public static volatile long liveBits;

    public Level228Layout() {
        // The exit locator only feeds the arrival point in the next level (Travel#useExit).
        super(4, new ExitLocator(1024, 64, 228));
    }

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        if (!Level228Maze.inGrid(x, z)) {
            return;
        }
        Level228Maze m = Level228Plan.maze(seed);
        int i = Level228Maze.idx(x, z);
        if (m.type[i] == Level228Maze.T_VOID) {
            return;
        }
        BlockState panel = Level228Blocks.PANEL.get().defaultBlockState();
        out.set(Level228Plan.FLOOR_Y - 1, panel);
        out.set(Level228Plan.FLOOR_Y, Level228Blocks.FLOOR.get().defaultBlockState());
        out.set(Level228Plan.CEILING_Y, Level228Blocks.CEILING.get().defaultBlockState());
        out.set(Level228Plan.CEILING_Y + 1, panel);
        long bits = liveBits;
        for (int y = Level228Plan.FEET_Y; y <= Level228Plan.BAND_Y; y++) {
            BlockState s = blockAt(m, bits, x, y, z);
            if (s != null) {
                out.set(y, s);
            }
        }
    }

    /** The block at (x, y, z), y in FEET_Y..BAND_Y, in shift state {@code bits}; null for air. */
    @Nullable
    static BlockState blockAt(Level228Maze m, long bits, int x, int y, int z) {
        int i = Level228Maze.idx(x, z);
        byte t = m.typeAt(bits, x, z);
        int f = m.flags[i];
        switch (t) {
            case Level228Maze.T_SOLID -> {
                return Level228Blocks.PANEL.get().defaultBlockState();
            }
            case Level228Maze.T_POST, Level228Maze.T_BLOCKER -> {
                return Level228Blocks.POST.get().defaultBlockState();
            }
            case Level228Maze.T_MIRROR, Level228Maze.T_GLASS, Level228Maze.T_PANEL, Level228Maze.T_DISTORT -> {
                boolean tall = (f & Level228Maze.C_TALL) != 0;
                if (y == Level228Plan.BAND_Y && !tall) {
                    return lightAt(m, i, y);
                }
                return pane(m, i, x);
            }
            case Level228Maze.T_CURTAIN -> {
                // A curtain in a sealed image (the backdrop's images along the core's edge)
                // must still stop you: an invisible pane behind the velvet.
                return (f & Level228Maze.C_SEALED) != 0 ? pane(m, i, x) : lightAt(m, i, y);
            }
            default -> {
                if (y == Level228Plan.BAND_Y && isDoorLintel(x, z)) {
                    return Level228Blocks.PANEL.get().defaultBlockState();
                }
                return lightAt(m, i, y);
            }
        }
    }

    private static BlockState pane(Level228Maze m, int i, int x) {
        int id = m.mirror[i];
        int axis = id >= 0 ? m.mAxis[id] : Math.floorMod(x, 3) == 0 ? 0 : 1;
        return Level228Blocks.MIRROR.get().defaultBlockState()
                .setValue(Level228MirrorBlock.AXIS, axis == 0 ? Direction.Axis.X : Direction.Axis.Z);
    }

    /** The exit door is 3 tall: plywood over it (the EXIT sign hangs on it). */
    private static boolean isDoorLintel(int x, int z) {
        return z == Level228Maze.DOOR_LINE && (x == Level228Maze.HALL_MIRROR_X || x == Level228Maze.HALL_MIRROR_X + 1);
    }

    @Nullable
    private static BlockState lightAt(Level228Maze m, int i, int y) {
        int l = m.light[i];
        if (l < 0 || Level228Plan.FEET_Y + (l >> 9 & 7) != y) {
            return null;
        }
        return Level228BulbBlock.state(l & 7, l >> 3 & 3, l >> 5 & 15);
    }

    /**
     * The block at (x, y, z) for state {@code bits} (air included, as Blocks.AIR): what
     * world/Level228Rules writes when a shift or a secret panel changes a column.
     */
    public static BlockState liveBlock(long seed, long bits, int x, int y, int z) {
        if (!Level228Maze.inGrid(x, z)) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockState s = blockAt(Level228Plan.maze(seed), bits, x, y, z);
        return s != null ? s : Blocks.AIR.defaultBlockState();
    }

    @Override
    public boolean isWalkable(long seed, int x, int z) {
        return Level228Maze.inGrid(x, z) && Level228Plan.maze(seed).walkable(liveBits, x, z);
    }

    /** Corridors are two wide: a spot rarely has walkable space on all four sides. */
    @Override
    public boolean arrivalNeedsSpace() {
        return false;
    }

    /** One maze per world: the exit is always as far as the whole maze. */
    @Override
    public int arrivalExitClearance() {
        return 0;
    }

    /**
     * /backrooms locate and the exit hints point at the final chamber; from the exit hallway, at
     * its door.
     */
    @Override
    public int[] exitPosition(long seed, int x, int z) {
        return Level228Plan.zoneAt(seed, x, z) == Level228Plan.Zone.EXIT_HALL
                ? Level228Plan.exitDoor(seed) : Level228Plan.chamberCentre(seed);
    }

    /** Every arrival (from Level 94, a late joiner, a respawn) steps out of the foyer's arrival mirror. */
    @Override
    public BlockPos preferredArrival(long seed, int x, int z, @Nullable BackroomsLevel from) {
        double[] a = Level228Plan.arrival(seed);
        return BlockPos.containing(a[0] - 0.5, a[1], a[2]);
    }
}
