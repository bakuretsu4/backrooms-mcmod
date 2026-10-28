package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.CeilingTubeBlock;
import com.rickr.backrooms.block.ExitDoorBlock;
import com.rickr.backrooms.block.Level2BulkheadFrameBlock.Part;
import com.rickr.backrooms.block.Level2ExitBulkheadBlock;
import com.rickr.backrooms.block.Level2PipeBlock;
import com.rickr.backrooms.block.Level2PipeBlock.Kind;
import com.rickr.backrooms.block.Level2PipeBlock.Size;
import com.rickr.backrooms.block.Level2StencilBlock.Variant;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.registry.Level2Blocks;
import com.rickr.backrooms.registry.Level2LightBlocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level2.Level2Sites;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * Level 2, "Pipe Dreams": one long, straight, dim maintenance corridor that never ends, its walls
 * and ceiling buried in pipework. You can only go forwards or back; doors along the way lead into
 * small dead-end rooms, and every few hundred blocks a heavy EXIT bulkhead waits in a bay at the side.
 *
 * <p><b>Floor plan</b> ({@link Level2Line}): a corridor along X every 48 blocks of Z, solid concrete
 * in between, so parallel corridors never meet. Seen down the corridor:
 * <pre>
 *   ceiling  y45  # # # # # # # # #
 *            y44  # t = = T = . t #     band under the ceiling, in layers: I-beams across the top every
 *                                       6 blocks; hung below them T cable tray, = pipe runs, the fat
 *                                       mains (the south lane run only along some segments: see steelRun)
 *            y43  # H * . . . . H #     H high rack (some runs), * caged lamp on a concrete pier
 *            y42  # L . . . . . L #     L low rack (some runs); . clear: the 3-wide lane is never blocked
 *            y41  # D . . . . . D #     D insulated main along the bottom of each chase
 *   floor    y40  # # g - . - . # #     g drainage gutter, - yellow lane lines
 *            dz  -4-3-2-1 0+1+2+3+4
 * </pre>
 * Each chase has a rhythm (see {@link #fillChase}): a concrete pier every 12 blocks with a caged lamp
 * on it (the two walls take turns, so a lamp every 6 blocks), a riser halfway between, and 5-block
 * runs between them that are full racks, low or high racks only, or bare wall. Every 24-48 blocks the corridor
 * changes character (a valve manifold, a pump bay, a low squeeze, a grated trench, a bulkhead ring,
 * a leak, a dark stretch); side rooms (maintenance, ventilation, boiler, storage, safe room) come
 * every 60-120 blocks on alternating sides; an exit bay every 320-420 blocks.
 *
 * <p><b>Anomalies</b> ({@link Level2AnomalyPlan}, world/anomaly/level2): brownout stretches where the
 * Blackout never ended, a hot zone in some boiler rooms, a door onto the void and a door onto a
 * looping office hallway, each in a little hall off the corridor. The caged lamps also hang on
 * series circuits that fail a whole section at a time (client side, see client/light/FixturePower).
 *
 * <p><b>Exit.</b> The EXIT bulkhead takes you (and anyone standing next to you) on to Level 4.
 */
public final class Level2Layout extends LevelLayout {
    // ---- What one column is --------------------------------------------------------------------
    /** Solid concrete (the walls and everything between corridors). */
    static final int ROCK = 0;
    /** Corridor floor you can walk on (lane, shoulder, a bay or alcove floor). */
    static final int OPEN = 1;
    /** Corridor column taken up by pipes, frames or machinery. */
    static final int CHASE = 2;
    /** A side room's doorway in the corridor wall (a steel door or open). */
    static final int DOOR = 3;
    static final int ROOM_OPEN = 4;
    static final int ROOM_SOLID = 5;
    static final int SAFE_OPEN = 6;
    static final int SAFE_SOLID = 7;
    /** The little hall in front of a void or loop door (built by {@link Level2AnomalyPlan#fillHall}). */
    static final int HALL = 8;
    /** Part of an anomaly's structure you can walk in (built by {@link Level2AnomalyPlan}). */
    static final int SITE_OPEN = 9;
    /** Part of an anomaly's structure that is solid (built by {@link Level2AnomalyPlan}). */
    static final int SITE_SOLID = 10;

    /**
     * The base class wants an exit locator. Level 2's real exits are the bays along each corridor
     * ({@link #exitPosition}); this one only picks where on Level 4 you arrive after using one.
     */
    private static final ExitLocator EXITS = new ExitLocator(320, 40, 3000);

    /** The anomaly kinds plus the segment, room and exit names /backrooms anomaly locate can find. */
    private static final List<String> LOCATE_KINDS;

    static {
        List<String> kinds = new ArrayList<>(Level2Sites.KINDS);
        kinds.addAll(Level2Finder.KINDS);
        LOCATE_KINDS = Collections.unmodifiableList(kinds);
    }

    /** The safe rooms' blocks, made on first use (blocks aren't registered yet when this class loads). */
    @Nullable
    private static volatile SafeRoomPlan.Style safeStyle;

    public Level2Layout() {
        super(4, EXITS);
    }

    // ==========================================================================================
    // Public queries
    // ==========================================================================================

    @Override
    public boolean isWalkable(long seed, int x, int z) {
        int code = column(seed, x, z);
        return code == OPEN || code == DOOR || code == ROOM_OPEN || code == SAFE_OPEN || code == HALL || code == SITE_OPEN;
    }

    /** Nobody arrives inside an anomaly: the void door's passage would drop you into the void. */
    @Override
    public boolean isSafeArrival(long seed, int x, int z) {
        int code = column(seed, x, z);
        return code == OPEN || code == DOOR || code == ROOM_OPEN || code == SAFE_OPEN || code == HALL;
    }

    /**
     * Arrivals land right on the centre line of the nearest corridor, where nothing ever stands,
     * except in the odd detour (a dog-leg's column, a bypass's sealed section): then a few blocks
     * along the line, the nearest spot that is open again.
     */
    @Override
    public BlockPos preferredArrival(long seed, int x, int z, @Nullable BackroomsLevel from) {
        int c = Level2Line.centre(seed, Level2Line.band(z));
        for (int d = 0; d <= 2 * Level2Detours.BYPASS_HALF; d++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                if (column(seed, x + sign * d, c) == OPEN) {
                    return new BlockPos(x + sign * d, FLOOR_Y + 1, c);
                }
                if (d == 0) {
                    break;
                }
            }
        }
        return new BlockPos(x, FLOOR_Y + 1, c);
    }

    /** Everything inside a safe room, walls and doorway included. */
    @Override
    public boolean isSafeRoom(long seed, int x, int z) {
        if (Level2Line.outsideReach(z)) {
            return false;
        }
        SafeRoomPlan plan = Level2Line.safePlan(seed, Level2Line.band(z), Level2Line.roomCell(x));
        return plan != null && plan.contains(x, z);
    }

    /**
     * The nearest exit door on your own corridor: the one /backrooms locate, the exit's sound, the
     * NightShot marks and the wanderers' notes all point at.
     */
    @Override
    public int[] exitPosition(long seed, int x, int z) {
        int band = Level2Line.band(z);
        int j = Level2Line.nearestExit(seed, band, x);
        return new int[] {Level2Line.exitX(seed, band, j),
                Level2Line.centre(seed, band) + Level2Line.exitSide(seed, band, j) * Level2Line.BAY_BACK};
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

    /**
     * The nearest anomaly of {@code kind}, or (for the debug names in {@link Level2Finder#KINDS},
     * like {@code level_2/seg/pump} or {@code level_2/room/safe}) the nearest segment, room or exit
     * of that kind, so the same command finds them.
     */
    @Override
    @Nullable
    public AnomalySite nearestAnomaly(long seed, int x, int z, String kind) {
        if (Level2Finder.KINDS.contains(kind)) {
            return Level2Finder.nearest(seed, x, z, kind);
        }
        return Level2AnomalyPlan.nearest(seed, x, z, kind);
    }

    @Override
    public List<String> anomalyKinds() {
        return LOCATE_KINDS;
    }

    /** The looping hallway's ceiling lights hang above the corridors (y51). */
    @Override
    public int highestCeilingY() {
        return ceilingY() + Level2Sites.LOOP_RISE;
    }

    /** The trench lamps hang under the floor, and the lamp beside the void's copy of a void door far below it. */
    @Override
    public int lowestFixtureY() {
        return floorY() - Level2Sites.VOID_DROP;
    }

    // ==========================================================================================
    // Columns
    // ==========================================================================================

    int column(long seed, int x, int z) {
        return column(seed, x, z, null, false);
    }

    /**
     * What column (x, z) is (see the codes at the top). {@code site} is the anomaly there when the
     * caller already knows it ({@code siteKnown}), so it isn't looked up twice.
     */
    private int column(long seed, int x, int z, @Nullable AnomalySite site, boolean siteKnown) {
        if (Level2Line.outsideReach(z)) {
            return ROCK;
        }
        int band = Level2Line.band(z);
        int dz = z - Level2Line.centre(seed, band);
        int a = Math.abs(dz);
        int side = dz < 0 ? -1 : 1;
        // Detours (Level2Detours, L2Chambers): a dog-leg's column, a bypass, a catwalk's trench.
        if (a <= Level2Line.WALL + 3) {
            int detour = Level2Detours.column(seed, band, x, dz);
            if (detour >= 0) {
                return detour;
            }
        }
        if (a <= Level2Line.LANE) {
            return OPEN;
        }
        if (a == Level2Line.SHOULDER) {
            int n = Level2Line.segment(seed, band, x);
            return shoulderSolid(seed, band, n, x, side) ? CHASE : OPEN;
        }
        // Anomaly structures (hot rooms, void and loop doors) start at the chase.
        int anomaly = Level2AnomalyPlan.column(siteKnown ? site : Level2AnomalyPlan.at(seed, x, z), x, z);
        if (anomaly >= 0) {
            return anomaly;
        }
        int feature = Level2Line.feature(seed, band, x, side);
        if (a == Level2Line.CHASE) {
            return feature == Level2Line.F_NONE || feature == Level2Line.F_HOT ? CHASE : OPEN;
        }
        if (a == Level2Line.WALL) {
            switch (feature) {
                case Level2Line.F_DOOR:
                    return DOOR;
                case Level2Line.F_MOUTH:
                case Level2Line.F_BAY:
                    return OPEN;
                case Level2Line.F_ALCOVE:
                    return pumpCell(seed, band, x) ? CHASE : OPEN;
                default:
                    break; // safe room door wall, or plain wall: see below
            }
        } else if (feature == Level2Line.F_BAY && a <= Level2Line.BAY_BACK) {
            int ex = bayExit(seed, band, x);
            return a == Level2Line.BAY_BACK || Math.abs(x - ex) >= Level2Line.BAY_HALF - 1 ? CHASE : OPEN;
        } else if (feature == Level2Line.F_ALCOVE && a == Level2Line.WALL + 1) {
            return CHASE;
        }
        return roomColumn(seed, band, x, z, a, side);
    }

    /** A column of a side room (or the rock around it) beyond the corridor wall. */
    private static int roomColumn(long seed, int band, int x, int z, int a, int side) {
        int k = Level2Line.roomCell(x);
        int type = Level2Line.roomType(seed, band, k);
        if (type == Level2Line.ROOM_NONE || type == Level2Line.ROOM_HOT || Level2Line.slotSide(seed, band, k) != side) {
            return ROCK;
        }
        if (type == Level2Line.ROOM_SAFE) {
            SafeRoomPlan plan = Level2Line.safePlan(seed, band, k);
            if (plan == null || !plan.contains(x, z) || plan.isOuterWall(x, z)) {
                return ROCK; // its outer layer is ordinary wall
            }
            return plan.isWalkable(x, z) ? SAFE_OPEN : SAFE_SOLID;
        }
        int i = x - Level2Line.slotX(seed, band, k);
        int j = a - Level2Line.WALL - 1;
        if (Math.abs(i) > Level2Line.roomHalfWidth(type) || j < 0 || j >= Level2Line.roomDepth(type)) {
            return ROCK;
        }
        return Level2Rooms.isFree(type, i, j) ? ROOM_OPEN : ROOM_SOLID;
    }

    /** True where a shoulder column is taken by a bulkhead ring or a squeeze's grown chase. */
    private static boolean shoulderSolid(long seed, int band, int n, int x, int side) {
        if (Level2Line.segType(seed, band, n) == Level2Line.SEG_BULKHEAD && Level2Line.frameX(seed, band, n) == x) {
            return true;
        }
        return Level2Line.segType(seed, band, n) == Level2Line.SEG_SQUEEZE
                && Level2Line.feature(seed, band, x, side) == Level2Line.F_NONE; // the way into a doorway stays clear
    }

    /** X of the exit door whose bay column x is in. */
    private static int bayExit(long seed, int band, int x) {
        return Level2Line.exitX(seed, band, Level2Line.nearestExit(seed, band, x));
    }

    /** True for the two columns of a pump bay's alcove taken by the pump (its front row). */
    private static boolean pumpCell(long seed, int band, int x) {
        int m = x - Level2Line.alcoveMid(seed, band, Level2Line.segment(seed, band, x));
        return m == 0 || m == 1;
    }

    // ==========================================================================================
    // Blocks
    // ==========================================================================================

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        AnomalySite site = Level2AnomalyPlan.at(seed, x, z); // null almost everywhere
        fillLevelColumn(seed, x, z, column(seed, x, z, site, true), site, out);
        if (site != null) {
            // The parts of an anomaly above or below the level: the void and its copy of the
            // door, or the looping hallway over the corridors.
            Level2AnomalyPlan.fillOutside(this, seed, site, x, z, out);
        }
    }

    /**
     * The blocks of one column between floor and ceiling, for column code {@code code}. Also used
     * (through a shifted {@code out}) to build the exact copies of the hall in front of a void or
     * loop door, so they always match the real thing.
     *
     * @param site the anomaly at (x, z), or null
     */
    void fillLevelColumn(long seed, int x, int z, int code, @Nullable AnomalySite site, Column out) {
        switch (code) {
            case ROCK -> fillRock(out);
            case SITE_OPEN, SITE_SOLID -> Level2AnomalyPlan.fillSiteColumn(this, seed, site, x, z, out);
            case HALL -> Level2AnomalyPlan.fillHall(this, site, x, z, out);
            case SAFE_OPEN, SAFE_SOLID -> {
                SafeRoomPlan plan = Level2Line.safePlan(seed, Level2Line.band(z), Level2Line.roomCell(x));
                if (plan != null) {
                    plan.fill(x, z, floorY(), ceilingY(), safeStyle(), out);
                }
            }
            case ROOM_OPEN, ROOM_SOLID, DOOR -> {
                int band = Level2Line.band(z);
                int k = Level2Line.roomCell(x);
                int type = Level2Line.roomType(seed, band, k);
                int side = Level2Line.slotSide(seed, band, k);
                if (code == DOOR) {
                    Level2Rooms.fillDoorway(this, type, side, out);
                } else {
                    int a = Math.abs(z - Level2Line.centre(seed, band));
                    Level2Rooms.fill(this, type, side, Hash.of(seed, band, k, 515),
                            x - Level2Line.slotX(seed, band, k), a - Level2Line.WALL - 1, out);
                }
            }
            default -> fillCorridor(seed, x, z, site, out);
        }
    }

    /** Solid concrete: the scuffed base row, then plain wall up to the ceiling. */
    private void fillRock(Column out) {
        int floor = floorY();
        int ceiling = ceilingY();
        out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        out.set(ceiling, ModBlocks.PIPE_CEILING.get().defaultBlockState());
        out.set(floor + 1, Level2Blocks.WALL_BASE.get().defaultBlockState());
        BlockState wall = ModBlocks.PIPE_WALL.get().defaultBlockState();
        for (int y = floor + 2; y < ceiling; y++) {
            out.set(y, wall);
        }
    }

    private static SafeRoomPlan.Style safeStyle() {
        SafeRoomPlan.Style style = safeStyle;
        if (style == null) {
            BlockState wall = ModBlocks.PIPE_WALL.get().defaultBlockState();
            style = new SafeRoomPlan.Style(
                    ModBlocks.BEIGE_CARPET.get().defaultBlockState(),
                    ModBlocks.PIPE_CEILING.get().defaultBlockState(),
                    Level2Blocks.WALL_BASE.get().defaultBlockState(),
                    wall,
                    ModBlocks.WOOD_FLOOR.get().defaultBlockState(),    // wood panelling below the dado line
                    ModBlocks.DIM_WALLPAPER.get().defaultBlockState(), // old wallpaper above it
                    null);
            safeStyle = style;
        }
        return style;
    }

    // ---- The corridor --------------------------------------------------------------------------

    /** One column of the corridor itself (lane, shoulder, chase, and the bays beyond the chase). */
    private void fillCorridor(long seed, int x, int z, @Nullable AnomalySite site, Column out) {
        int band = Level2Line.band(z);
        int dz = z - Level2Line.centre(seed, band);
        int a = Math.abs(dz);
        int side = dz < 0 ? -1 : 1;
        int n = Level2Line.segment(seed, band, x);
        int type = Level2Line.segType(seed, band, n);
        boolean brownout = site != null && site.kind().equals(Level2Sites.BROWNOUT);
        out.set(ceilingY(), ModBlocks.PIPE_CEILING.get().defaultBlockState());
        // Detours (Level2Detours, L2Chambers): the odd stretch builds some columns itself.
        if (Level2Detours.fill(this, seed, band, n, type, x, dz, out)) {
            return;
        }
        if (a <= Level2Line.LANE) {
            fillLane(seed, band, n, type, x, dz, out);
        } else if (a == Level2Line.SHOULDER) {
            fillShoulder(seed, band, n, type, x, z, side, brownout ? site : null, out);
        } else {
            int feature = Level2Line.feature(seed, band, x, side);
            if (a == Level2Line.CHASE) {
                fillChase(seed, band, n, type, x, side, feature, brownout ? site : null, out);
            } else if (feature == Level2Line.F_BAY) {
                fillBay(seed, band, x, a, side, out);
            } else if (feature == Level2Line.F_ALCOVE) {
                fillAlcove(seed, band, n, x, a, side, out);
            } else if (feature == Level2Line.F_MOUTH) {
                // The way into a door's little hall: an opening in the wall, lintel above.
                out.set(floorY(), ModBlocks.PIPE_FLOOR.get().defaultBlockState());
                out.set(ceilingY() - 1, ModBlocks.PIPE_WALL.get().defaultBlockState());
            } else {
                fillRock(out);
            }
        }
        // Small changes on top: a detour's grating and soffit, section numbers, the corridor oddities.
        Level2Detours.after(this, seed, band, n, type, x, dz, out);
        Level2Oddities.decorate(this, seed, x, z, site, out);
    }

    /**
     * The lane: floor (or the trench's grating), and the ceiling band over it, in layers you can
     * tell apart: a steel I-beam across the corridor at every lamp spot right under the slab, and
     * hung below on rods the cable tray in the middle with a painted pipe run beside it and, along
     * some segments, a steel pair on its other side (see {@link #steelRun}).
     */
    private void fillLane(long seed, int band, int n, int type, int x, int dz, Column out) {
        int floor = floorY();
        int top = ceilingY() - 1;
        boolean trench = type == Level2Line.SEG_TRENCH && Level2Line.hasTrench(seed, band, n)
                && fillTrench(seed, band, n, x, dz, out);
        if (!trench) {
            out.set(floor, dz == 0 ? ModBlocks.PIPE_FLOOR.get().defaultBlockState()
                    : Level2Parts.floorLine(Level2Line.wallOf(dz)));
            // A leak over the gutter: the water spreads out of the gutter onto the lane beside it.
            int side = dz < 0 ? -1 : 1;
            if (dz != 0 && Level2Line.gutterSide(seed, band) == side && leakAt(seed, band, x, side)) {
                out.set(floor + 1, Level2Parts.puddle());
            }
        }
        if (type == Level2Line.SEG_BULKHEAD && Level2Line.frameX(seed, band, n) == x) {
            out.set(top, Level2Parts.frame(Direction.NORTH, Part.TOP));
            if (dz != 0) {
                // An arrow on the inside of the ring, pointing the way to the nearest exit.
                int ex = Level2Line.exitX(seed, band, Level2Line.nearestExit(seed, band, x));
                boolean east = ex > x;
                // Seen from the lane, east is on your right on the north side, on your left on the south side.
                Variant arrow = (dz < 0) == east ? Variant.ARROW_RIGHT : Variant.ARROW_LEFT;
                out.set(floor + 2, Level2Parts.stencil(Level2Line.inwardOf(dz), arrow));
            }
            return;
        }
        boolean beam = beamAt(seed, band, n, type, x);
        if (dz == 0) {
            out.set(top, Level2Parts.cableTray(Direction.Axis.X, beam, !beam && droopAt(seed, band, x)));
        } else if (dz < 0) {
            out.set(top, Level2Parts.hung(Direction.Axis.X, servicePaint(seed, band), Size.SMALL,
                    !beam && Math.floorMod(x, 5) == 0, beam));
        } else {
            BlockState steel = steelRun(seed, band, n, x, beam);
            if (steel != null) {
                out.set(top, steel);
            }
        }
        if (type == Level2Line.SEG_SQUEEZE) {
            if (x == Level2Line.segStart(seed, band, n) || x == Level2Line.segEnd(seed, band, n)) {
                // A low concrete beam at each end of the squeeze: the sagging pipes run into it (2 blocks clear under it).
                out.set(top - 1, ModBlocks.PIPE_WALL.get().defaultBlockState());
                out.set(top, ModBlocks.PIPE_WALL.get().defaultBlockState());
                return;
            }
            // The bundle sags: pipes in the top half of the block below, leaving 2.5 blocks to walk in.
            out.set(top - 1, dz == 0
                    ? Level2Parts.pipe(Direction.Axis.X, Direction.UP, Kind.INSULATED, Size.MEDIUM, Math.floorMod(x + 1, 5) == 0)
                    : Level2Parts.pipe(Direction.Axis.X, Direction.UP, Kind.STEEL, Size.SMALL, Math.floorMod(x + 3 + dz, 5) == 0));
        }
    }

    /** Chance that a segment carries the steel pair over the south half of its lane. */
    private static final double STEEL_RUN_CHANCE = 0.45;

    private static boolean steelRunIn(long seed, int band, int n) {
        int type = Level2Line.segType(seed, band, n);
        return type == Level2Line.SEG_SQUEEZE || type == Level2Line.SEG_BULKHEAD
                || Hash.unit(seed, band, n, 534) < STEEL_RUN_CHANCE;
    }

    /**
     * The ceiling over the south half of the lane at x (null: nothing). A steel pair runs there
     * along some segments only: where it doesn't you look up past the tray at the beams, the
     * hanger rods and the dark slab, which is what lets the ceiling read in layers. Where the pair
     * stops its two pipes bend up into the slab (never under a beam, so the beam stays whole).
     */
    @Nullable
    private static BlockState steelRun(long seed, int band, int n, int x, boolean beam) {
        BlockState bare = beam ? Level2Parts.beam(Direction.Axis.Z) : null;
        if (!steelRunIn(seed, band, n)) {
            return bare;
        }
        boolean openWest = !steelRunIn(seed, band, n - 1);
        boolean openEast = !steelRunIn(seed, band, n + 1);
        int first = Level2Line.segStart(seed, band, n);
        int last = Level2Line.segEnd(seed, band, n);
        if (openWest && Level2Line.lampSide(seed, band, first) != 0) {
            first++;
        }
        if (openEast && Level2Line.lampSide(seed, band, last) != 0) {
            last--;
        }
        if (x < first || x > last) {
            return bare;
        }
        if (openWest && x == first) {
            return Level2Parts.hungEnd(Direction.NORTH, Direction.EAST, Kind.STEEL, Size.MEDIUM);
        }
        if (openEast && x == last) {
            return Level2Parts.hungEnd(Direction.NORTH, Direction.WEST, Kind.STEEL, Size.MEDIUM);
        }
        return Level2Parts.hung(Direction.Axis.X, Kind.STEEL, Size.MEDIUM, !beam && Math.floorMod(x + 2, 5) == 0, beam);
    }

    /**
     * Under the lane of a trench segment: a grating over a 2-deep trench of pipes with a dim lamp
     * now and then. Returns false outside the trench (its two ends are solid).
     */
    private boolean fillTrench(long seed, int band, int n, int x, int dz, Column out) {
        int floor = floorY();
        int first = Level2Line.segStart(seed, band, n) + 3;
        int last = Level2Line.segEnd(seed, band, n) - 3;
        if (x == first - 1 || x == last + 1) {
            for (int y = floor - 3; y < floor; y++) {
                out.set(y, ModBlocks.PIPE_WALL.get().defaultBlockState()); // the trench's end walls
            }
            return false;
        }
        if (x < first || x > last) {
            return false;
        }
        out.set(floor, Level2Parts.grating(Direction.Axis.X));
        out.set(floor - 3, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        out.set(floor - 2, switch (dz) {
            case -1 -> Level2Parts.pipe(Direction.Axis.X, Direction.DOWN, Kind.INSULATED, Size.LARGE, Math.floorMod(x, 4) == 0);
            case 0 -> Level2Parts.pipe(Direction.Axis.X, Direction.DOWN, Kind.STEEL, Size.MEDIUM, Math.floorMod(x + 1, 4) == 0);
            default -> Level2Parts.pipe(Direction.Axis.X, Direction.DOWN, Kind.COPPER, Size.SMALL, Math.floorMod(x + 2, 4) == 0);
        });
        // A dim lamp on the trench wall every 12 blocks, alternating sides.
        int rel = x - first;
        if (dz != 0 && Math.floorMod(rel, 12) == 6 && ((Math.floorDiv(rel, 12) & 1) == 0) == (dz < 0)) {
            out.set(floor - 1, Level2Parts.lamp(Level2Line.inwardOf(dz), false));
        }
        return true;
    }

    /**
     * The shoulder: gutter or plain floor, the fat insulated main hung in the ceiling band (one
     * unbroken run), and the caged lamp hung just under it on the face of the chase's concrete pier.
     * Now and then a joint of the main weeps: a drip under it and a puddle on the floor.
     */
    private void fillShoulder(long seed, int band, int n, int type, int x, int z, int side,
                              @Nullable AnomalySite brownout, Column out) {
        int floor = floorY();
        int top = ceilingY() - 1;
        Direction in = Level2Line.inwardOf(side);
        Direction wall = Level2Line.wallOf(side);
        boolean gutter = Level2Line.gutterSide(seed, band) == side;
        out.set(floor, gutter ? Level2Parts.gutter(Direction.Axis.X) : ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        if (type == Level2Line.SEG_TRENCH && Level2Line.hasTrench(seed, band, n)
                && x >= Level2Line.segStart(seed, band, n) + 2 && x <= Level2Line.segEnd(seed, band, n) - 2) {
            for (int y = floor - 3; y < floor; y++) {
                out.set(y, ModBlocks.PIPE_WALL.get().defaultBlockState()); // the trench's side walls
            }
        }
        if (type == Level2Line.SEG_BULKHEAD && Level2Line.frameX(seed, band, n) == x) {
            fillFrameSide(in, out);
            return;
        }
        boolean plain = Level2Line.feature(seed, band, x, side) == Level2Line.F_NONE;
        boolean squeeze = type == Level2Line.SEG_SQUEEZE;
        if (squeeze && plain && (x == Level2Line.segStart(seed, band, n) || x == Level2Line.segEnd(seed, band, n))) {
            // The squeeze's end portal: solid concrete, the main and the grown chase run into it.
            fillPier(out, true);
            return;
        }
        boolean beam = beamAt(seed, band, n, type, x);
        out.set(top, Level2Parts.hung(Direction.Axis.X, Kind.INSULATED, Size.LARGE, !beam && Math.floorMod(x + 4, 5) == 0, beam));
        if (!plain) {
            return; // the way into a doorway, bay or alcove stays clear
        }
        if (squeeze && (Level2Line.feature(seed, band, x - 1, side) != Level2Line.F_NONE
                || Level2Line.feature(seed, band, x + 1, side) != Level2Line.F_NONE)) {
            fillPier(out, false); // the grown chase ends against a pier beside a doorway
            return;
        }
        boolean lamp = Level2Line.chaseSlot(seed, band, x, side) == Level2Line.CHASE_PIER;
        if (lamp) {
            out.set(floor + 3, Level2Parts.lamp(in, lampDead(seed, band, n, type, x, z, brownout)));
            if (brownout != null && Level2AnomalyPlan.emergencyLight(seed, x, z)) {
                out.set(floor + 2, Level2Parts.emergencyLight(in)); // the violet battery light under the dead lamp
            }
        }
        if (squeeze) {
            // The chases grow: a main lying on the floor and a rack against the chase, under the lamps.
            out.set(floor + 1, Level2Parts.pipe(Direction.Axis.X, Direction.DOWN, Kind.INSULATED, Size.LARGE, Math.floorMod(x, 5) == 0));
            out.set(floor + 2, Level2Parts.pipe(Direction.Axis.X, wall, Kind.STEEL, Size.SMALL, Math.floorMod(x + 2, 5) == 0));
            return;
        }
        if (type == Level2Line.SEG_BULKHEAD) {
            // The ring's section number, on the faces of its shoulder pieces.
            int frame = Level2Line.frameX(seed, band, n);
            if (frame != Integer.MIN_VALUE && Math.abs(x - frame) == 1) {
                out.set(floor + 2, Level2Parts.stencil(x < frame ? Direction.WEST : Direction.EAST,
                        Level2Oddities.section(seed, band, n)));
            }
            if (frame != Integer.MIN_VALUE && !lamp && x == frame + emergencyOffset(seed, band, n)
                    && side == emergencySide(seed, band, frame + emergencyOffset(seed, band, n))) {
                // One red battery lamp on the ring's face, dark until this section's circuit dies.
                out.set(floor + 3, Level2LightBlocks.emergency(x < frame ? Direction.WEST : Direction.EAST, true, false));
            }
        }
        boolean leak = leakAt(seed, band, x, side);
        if (leak) {
            out.set(floor + 3, Level2Blocks.DRIP.get().defaultBlockState()); // under the weeping joint of the main
        }
        if (!gutter && (leak || leakAt(seed, band, x - 1, side) || leakAt(seed, band, x + 1, side))) {
            out.set(floor + 1, Level2Parts.puddle()); // (over the gutter the water runs off: see fillLane)
        }
        if (type == Level2Line.SEG_LEAK && gutter) {
            int mid = Level2Line.segMid(seed, band, n);
            if (Math.abs(x - mid) <= 2) {
                out.set(floor + 1, ModBlocks.L2_BLACK_OOZE.get().defaultBlockState());
                if (((x - mid) & 1) == 0 && !lamp) {
                    out.set(floor + 3, Level2Blocks.DRIP.get().defaultBlockState()); // right under the main
                }
            }
        }
        if (brownout != null && gutter) {
            int burst = Level2AnomalyPlan.burstX(seed, brownout, x);
            if (burst != Integer.MIN_VALUE && Math.abs(x - burst) <= 2) {
                out.set(floor + 1, ModBlocks.L2_BLACK_OOZE.get().defaultBlockState());
            }
        }
    }

    /** A concrete pier (the scuffed base, then wall), up to the ceiling if {@code full}, else to head height. */
    private void fillPier(Column out, boolean full) {
        int floor = floorY();
        out.set(floor + 1, Level2Blocks.WALL_BASE.get().defaultBlockState());
        BlockState wall = ModBlocks.PIPE_WALL.get().defaultBlockState();
        for (int y = floor + 2; y < (full ? ceilingY() : ceilingY() - 1); y++) {
            out.set(y, wall);
        }
    }

    /** A dead bulb: in a dark segment's dark run (all but one), or in a brownout (most). */
    private static boolean lampDead(long seed, int band, int n, int type, int x, int z, @Nullable AnomalySite brownout) {
        if (brownout != null && !Level2AnomalyPlan.lampSurvives(seed, x, z)) {
            return true;
        }
        return type == Level2Line.SEG_DARK && x >= Level2Line.darkFrom(seed, band, n) && x <= Level2Line.darkTo(seed, band, n)
                && x != Level2Line.survivorX(seed, band, n);
    }

    /** One side post of a bulkhead ring (shoulder or chase), its lips facing the lane. */
    private void fillFrameSide(Direction in, Column out) {
        int floor = floorY();
        for (int y = floor + 1; y < ceilingY() - 1; y++) {
            out.set(y, Level2Parts.frame(in, Part.SIDE));
        }
        out.set(ceilingY() - 1, Level2Parts.frame(in, Part.CORNER));
    }

    /**
     * The pipe chase along a wall. It has a rhythm: a concrete pier every 12 blocks (the lamp hangs
     * on its face), a riser halfway between (pipes climbing from the low main and bending into the
     * wall), and between them 5-block runs that each pick their own look: two racks, only the low
     * one, only the high one, or bare wall with a junction box, a breaker panel, a valve, a gauge or
     * a stencil. Racks never end in mid-air: at each end the pipes bend into the wall, or run into
     * the pier through a bolted wall collar. The insulated main runs along the bottom and a thin
     * pipe along the top, hung under the ceiling, the whole way; along a valve manifold the top pipe
     * is the manifold's header and a branch drops from it into every valve.
     */
    private void fillChase(long seed, int band, int n, int type, int x, int side, int feature,
                           @Nullable AnomalySite brownout, Column out) {
        int floor = floorY();
        int top = ceilingY() - 1;
        Direction in = Level2Line.inwardOf(side);
        Direction wall = Level2Line.wallOf(side);
        out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        if (type == Level2Line.SEG_BULKHEAD && Level2Line.frameX(seed, band, n) == x) {
            fillFrameSide(in, out);
            return;
        }
        out.set(top, chaseTop(seed, band, x, side, beamAt(seed, band, n, type, x)));
        if (feature != Level2Line.F_NONE) {
            return; // a doorway, bay or alcove: only the top pipe carries on over it
        }
        int slot = Level2Line.chaseSlot(seed, band, x, side);
        if (slot == Level2Line.CHASE_PIER || Level2Line.feature(seed, band, x - 1, side) != Level2Line.F_NONE
                || Level2Line.feature(seed, band, x + 1, side) != Level2Line.F_NONE) {
            fillPier(out, false);
            return;
        }
        boolean pocket = brownout != null && Level2Line.gutterSide(seed, band) == side
                && Level2AnomalyPlan.burstX(seed, brownout, x) == x;
        out.set(floor + 1, pocket ? ModBlocks.L2_BLACK_OOZE.get().defaultBlockState() // a burst main, ooze pooled in its gap
                : Level2Parts.duct(in, Math.floorMod(x + 3 * side, 6) == 0));
        int run = Level2Line.chaseRun(seed, band, x, side);
        long h = Hash.of(seed, 2 * band + (side < 0 ? 0 : 1), run, 519);
        if (slot == Level2Line.CHASE_RISER) {
            // Pipes climbing from the main, bending into the wall at the top; the racks either side end beside them.
            Kind kind = Hash.unit(h) < 0.5 ? Kind.STEEL : Kind.COPPER;
            out.set(floor + 2, Level2Parts.pipe(Direction.Axis.Y, wall, kind, Size.MEDIUM, false));
            out.set(floor + 3, Level2Parts.riserTop(in, kind, Size.MEDIUM));
            return;
        }
        // What this run looks like.
        double look = Hash.unit(Hash.mix(h));
        boolean hasLow = look < 0.52;                 // both racks (34%) or only the low one (18%)
        boolean hasHigh = look < 0.34 || (look >= 0.52 && look < 0.70); // ... or only the high one (18%)
        int decor = Hash.range(Hash.mix(h ^ 0x5DEECE66DL), 5); // for bare runs (30%)
        int mid = Level2Line.segMid(seed, band, n);
        // Special runs: the valve manifold, the leak's steam jet, the hot zone's approach.
        boolean valves = manifoldAt(seed, band, x, side);
        boolean jet = type == Level2Line.SEG_LEAK && side != Level2Line.gutterSide(seed, band)
                && run == Level2Line.chaseRun(seed, band, mid + 6, side);
        int k = Level2Line.roomCell(x);
        boolean hot = Level2Line.roomType(seed, band, k) == Level2Line.ROOM_HOT
                && Math.abs(x - Level2Line.slotX(seed, band, k)) <= 12;
        BlockState low = null;
        BlockState high = null;
        if (valves) {
            // The valve manifold: from the header under the ceiling a branch drops into a valve at
            // every other column (and on into the main below), a gauge between them, the label in the middle.
            Kind header = headerKind(seed, band, n);
            if (slot == 2 && Math.abs(x - mid) <= 3) {
                low = Level2Parts.stencil(in, LABELS[Hash.range(Hash.of(seed, band, n, 517), LABELS.length)]);
                high = Level2Parts.gauge(in);
            } else if ((slot & 1) == 0) {
                high = Level2Parts.feedPipe(in, header);
                long hv = Hash.of(seed, band, x, 533);
                low = header != Kind.PAINTED_RED && Hash.unit(hv) < 0.45
                        ? Level2Parts.leverValve(in, Hash.unit(Hash.mix(hv)) < 0.8, true)
                        : Level2Parts.valve(in, header == Kind.PAINTED_RED, true);
            } else if (slot == 1) {
                high = Level2Parts.gauge(in);
            }
        } else if (jet || hot) {
            // Bare wall: a hissing vent beside a red valve (and the warning on the hot room's side).
            if (slot == 2) {
                low = jet ? Level2Parts.valve(in, true) : null;
                high = Level2Parts.steamVent(in);
            } else if (slot == 3) {
                high = jet ? Level2Parts.stencil(in, Variant.STEAM)
                        : Level2Line.slotSide(seed, band, k) == side ? Level2Parts.stencil(in, Variant.DANGER_HOT) : null;
            }
        } else if (hasLow || hasHigh) {
            // Where does this run stop? Beside a pier (solid) or a riser, or another kind of run.
            int west = chaseNext(seed, band, x, side, -1);
            int east = chaseNext(seed, band, x, side, 1);
            if (hasLow) {
                Kind kind = LOW_KINDS[Hash.range(h, LOW_KINDS.length)];
                Size size = Hash.unit(h ^ 1) < 0.5 ? Size.SMALL : Size.MEDIUM;
                low = rackPiece(wall, in, kind, size, slot == 1, west, east, Hash.mix(h ^ 7)); // its support clamp mid-run
            }
            if (hasHigh) {
                Kind kind = HIGH_KINDS[Hash.range(Hash.mix(h ^ 3), HIGH_KINDS.length)];
                Size size = Hash.unit(h ^ 4) < 0.6 ? Size.SMALL : Size.MEDIUM;
                high = rackPiece(wall, in, kind, size, slot == 3, west, east, Hash.mix(h ^ 8));
            }
        } else {
            // A bare stretch of wall, with something on it.
            switch (decor) {
                case 0 -> {
                    if (slot == 2) {
                        // A junction box with its conduit running up into the ceiling band.
                        low = Level2Blocks.JUNCTION_BOX.get().defaultBlockState().setValue(WallMountBlock.FACING, in);
                        high = Level2Parts.pipe(Direction.Axis.Y, wall, Kind.CONDUIT, Size.SMALL, false);
                    }
                }
                case 1 -> {
                    if (slot == 2) {
                        if (side < 0) {
                            // A ball valve on a branch dropping from the painted pipe above.
                            low = Level2Parts.leverValve(in, Hash.unit(h ^ 9) < 0.75, true);
                            high = Level2Parts.feedPipe(in, chaseTopKind(seed, band, x, side));
                        } else {
                            low = Level2Parts.valve(in, false);
                            high = Level2Parts.gauge(in);
                        }
                    }
                }
                case 2 -> {
                    if (slot == 2) {
                        high = Level2Parts.stencil(in, WALL_STENCILS[Hash.range(Hash.mix(h ^ 6), WALL_STENCILS.length)]);
                    }
                }
                case 3 -> {
                    if (slot == 2 && side > 0) {
                        // A breaker panel, its conduits rising into the conduit run along the top.
                        BlockState panel = Level2Blocks.BREAKER_PANEL.get().defaultBlockState();
                        low = Level2Parts.tall(panel, in, false);
                        high = Level2Parts.tall(panel, in, true);
                    } else if (slot == 2) {
                        low = Level2Blocks.JUNCTION_BOX.get().defaultBlockState().setValue(WallMountBlock.FACING, in);
                        high = Level2Parts.pipe(Direction.Axis.Y, wall, Kind.CONDUIT, Size.SMALL, false);
                    }
                }
                default -> {
                    if (slot == 1) {
                        high = Level2Parts.gauge(in);
                    } else if (slot == 3) {
                        low = Level2Blocks.JUNCTION_BOX.get().defaultBlockState().setValue(WallMountBlock.FACING, in);
                        high = Level2Parts.pipe(Direction.Axis.Y, wall, Kind.CONDUIT, Size.SMALL, false);
                    }
                }
            }
        }
        if (low != null) {
            out.set(floor + 2, low);
        }
        if (high != null) {
            out.set(floor + 3, high);
        }
    }

    /** What lies beside a chase column along the wall (see {@link #chaseNext}). */
    private static final int NEXT_RUN = 0;
    private static final int NEXT_PIER = 1;
    private static final int NEXT_OTHER = 2;

    /**
     * What is next to rack column x on the wall, one step {@code dir} (-1 west, +1 east): more of
     * the same run (NEXT_RUN), a solid pier or bulkhead post (NEXT_PIER), or a riser or a stretch
     * of a different kind, where the pipes have to turn into the wall (NEXT_OTHER). Column x itself
     * is a rack column, so its own neighbours have no doorway (that would make x a pier).
     */
    private static int chaseNext(long seed, int band, int x, int side, int dir) {
        int nx = x + dir;
        int slot = Level2Line.chaseSlot(seed, band, nx, side);
        if (slot == Level2Line.CHASE_RISER) {
            return NEXT_OTHER;
        }
        if (slot == Level2Line.CHASE_PIER || Level2Line.feature(seed, band, nx + dir, side) != Level2Line.F_NONE) {
            return NEXT_PIER;
        }
        int n = Level2Line.segment(seed, band, nx);
        if (Level2Line.segType(seed, band, n) == Level2Line.SEG_BULKHEAD && Level2Line.frameX(seed, band, n) == nx) {
            return NEXT_PIER;
        }
        return manifoldAt(seed, band, nx, side) ? NEXT_OTHER : NEXT_RUN;
    }

    /**
     * One block of a wall rack: a plain length (or its joint, with the support, when
     * {@code joint}), or, at the end of the run, the piece where the pipes bend into the wall or
     * pass into the pier through a collar. {@code west}/{@code east} come from {@link #chaseNext}.
     */
    private static BlockState rackPiece(Direction wall, Direction in, Kind kind, Size size, boolean joint,
                                        int west, int east, long h) {
        int stop = west != NEXT_RUN && east == NEXT_RUN ? west : east != NEXT_RUN && west == NEXT_RUN ? east : NEXT_RUN;
        if (stop == NEXT_RUN) {
            return Level2Parts.pipe(Direction.Axis.X, wall, kind, size, joint); // a run's middle (or a lone column)
        }
        // The pipes arrive from the side the run carries on; against a riser they must bend away.
        Direction from = west == NEXT_RUN ? Direction.WEST : Direction.EAST;
        boolean bend = stop == NEXT_OTHER || Hash.unit(h) < 0.45;
        return Level2Parts.pipeEnd(in, from, bend, kind, size);
    }

    /** True along the valve manifold: on its side of a valve segment, clear of the segment's two ends. */
    private static boolean manifoldAt(long seed, int band, int x, int side) {
        int n = Level2Line.segment(seed, band, x);
        return Level2Line.segType(seed, band, n) == Level2Line.SEG_VALVES && side == Level2Line.featureSide(seed, band, n)
                && x > Level2Line.segStart(seed, band, n) + 1 && x < Level2Line.segEnd(seed, band, n) - 1;
    }

    /** The colour of a valve segment's header, matching the label stencilled on the manifold. */
    private static Kind headerKind(long seed, int band, int n) {
        return HEADERS[Hash.range(Hash.of(seed, band, n, 517), HEADERS.length)];
    }

    /** The thin pipe run along the top of each chase (see the 5-argument version), with no beam. */
    static BlockState chaseTop(long seed, int band, int x, int side) {
        return chaseTop(seed, band, x, side, false);
    }

    /**
     * The pipe run along the top of each chase, hung under the ceiling: painted on the north wall,
     * conduit on the south. Along a valve segment's manifold side it is the manifold's header
     * instead: a fatter pair in the colour of what it carries, with a flange where the paint
     * changes at each end of the segment.
     */
    private static BlockState chaseTop(long seed, int band, int x, int side, boolean beam) {
        int n = Level2Line.segment(seed, band, x);
        if (Level2Line.segType(seed, band, n) == Level2Line.SEG_VALVES && side == Level2Line.featureSide(seed, band, n)) {
            boolean end = x == Level2Line.segStart(seed, band, n) || x == Level2Line.segEnd(seed, band, n);
            return Level2Parts.hung(Direction.Axis.X, headerKind(seed, band, n), Size.MEDIUM,
                    end || (!beam && Math.floorMod(x + 1, 5) == 0), beam);
        }
        return Level2Parts.hung(Direction.Axis.X, side < 0 ? PAINTS[Hash.range(Hash.of(seed, band, 7, 520), PAINTS.length)] : Kind.CONDUIT,
                Size.SMALL, !beam && Math.floorMod(x + (side < 0 ? 0 : 2), 5) == 0, beam);
    }

    /** The kind of the top pipe at x (what a branch dropping from it carries). */
    private static Kind chaseTopKind(long seed, int band, int x, int side) {
        return chaseTop(seed, band, x, side, false).getValue(Level2PipeBlock.KIND);
    }

    // ---- The ceiling band's beams, leaks, the bulkhead's battery lamp ---------------------------

    /**
     * True where a steel I-beam crosses the corridor under the slab: at every lamp spot (every 6
     * blocks, so each beam has a lamp under one end and they read as one rhythm), except where a
     * doorway, bay or alcove opens on either side (no wall to carry it), at a bulkhead ring (the
     * ring is the beam there) and at a squeeze's concrete end beams.
     */
    private static boolean beamAt(long seed, int band, int n, int type, int x) {
        if (Level2Line.lampSide(seed, band, x) == 0) {
            return false;
        }
        if (type == Level2Line.SEG_BULKHEAD && Level2Line.frameX(seed, band, n) == x) {
            return false;
        }
        if (type == Level2Line.SEG_SQUEEZE && (x == Level2Line.segStart(seed, band, n) || x == Level2Line.segEnd(seed, band, n))) {
            return false;
        }
        return Level2Line.feature(seed, band, x, -1) == Level2Line.F_NONE && Level2Line.feature(seed, band, x, 1) == Level2Line.F_NONE;
    }

    /** About one tray block in 16 (never under a beam) has a loop of loose cable sagging out of it. */
    private static boolean droopAt(long seed, int band, int x) {
        return Hash.unit(seed, band, x, 531) < 0.06;
    }

    /** Leaks are picked per stretch of this many blocks of corridor ... */
    private static final int LEAK_CELL = 16;
    /** ... with this chance of a candidate ... */
    private static final double LEAK_CHANCE = 0.7;
    /** ... which leaks for sure where the concrete is damp (Level2Damp), else only this often. */
    private static final double DRY_LEAK_CHANCE = 0.25;

    /**
     * True if the insulated main over the shoulder on {@code side} weeps at x: a drip hangs under
     * the joint and a puddle spreads on the floor. Leaks gather where the walls and floor are damp
     * (Level2Damp), sit at one of the main's joints, and keep clear of the lamps, the doorways and
     * the segments that already have water of their own (the leak segment) or no room for it.
     */
    private static boolean leakAt(long seed, int band, int x, int side) {
        int cell = Math.floorDiv(x, LEAK_CELL);
        long h = Hash.of(seed, band, cell, 530);
        if (Hash.unit(h) >= LEAK_CHANCE || (((h >>> 20) & 1) == 0 ? -1 : 1) != side) {
            return false;
        }
        int lx = cell * LEAK_CELL + 2 + Hash.range(Hash.mix(h), LEAK_CELL - 4);
        lx -= Math.floorMod(lx + 4, 5); // back to a joint of the main (see fillShoulder)
        if (x != lx || Level2Line.lampSide(seed, band, x) == side) {
            return false;
        }
        int type = Level2Line.segType(seed, band, Level2Line.segment(seed, band, x));
        if (type == Level2Line.SEG_SQUEEZE || type == Level2Line.SEG_LEAK || type == Level2Line.SEG_BULKHEAD
                || Level2Line.feature(seed, band, x, side) != Level2Line.F_NONE) {
            return false;
        }
        int z = Level2Line.centre(seed, band) + side * Level2Line.SHOULDER;
        return Level2Damp.isDamp(seed, x, z) || Hash.unit(Hash.mix(h ^ 5)) < DRY_LEAK_CHANCE;
    }

    /** Which side of a bulkhead ring (-1 west, +1 east) its battery lamp hangs on. */
    private static int emergencyOffset(long seed, int band, int n) {
        return (Hash.of(seed, band, n, 532) & 1) == 0 ? -1 : 1;
    }

    /** Which wall's shoulder the ring's battery lamp is on at x: away from the gutter, unless a caged lamp is there. */
    private static int emergencySide(long seed, int band, int x) {
        int side = -Level2Line.gutterSide(seed, band);
        return Level2Line.lampSide(seed, band, x) == side ? -side : side;
    }

    private static final Variant[] LABELS = {Variant.STEAM, Variant.RETURN, Variant.H2O};
    /** A manifold header's paint for each label above: red steam, grey return, green water. */
    private static final Kind[] HEADERS = {Kind.PAINTED_RED, Kind.PAINTED_BLUE, Kind.PAINTED_GREEN};
    /**
     * What may be stencilled on a bare stretch of chase wall (no section numbers: those run in
     * order along the corridor, see Level2Oddities.section).
     */
    private static final Variant[] WALL_STENCILS = {Variant.NO_SMOKING, Variant.H2O, Variant.RETURN, Variant.STEAM,
            Variant.DANGER_HOT};
    /** The low rack's pipes: grey steel, copper, insulated, painted. */
    private static final Kind[] LOW_KINDS = {Kind.STEEL, Kind.STEEL, Kind.COPPER, Kind.INSULATED, Kind.PAINTED_BLUE, Kind.PAINTED_GREEN};
    /** The high rack's pipes. */
    private static final Kind[] HIGH_KINDS = {Kind.COPPER, Kind.STEEL, Kind.PAINTED_RED, Kind.PAINTED_YELLOW, Kind.CONDUIT, Kind.INSULATED};

    private static final Kind[] PAINTS = {Kind.PAINTED_RED, Kind.PAINTED_BLUE, Kind.PAINTED_YELLOW, Kind.PAINTED_GREEN};

    /** The painted service pipe over the north half of the lane (one colour per corridor). */
    private static Kind servicePaint(long seed, int band) {
        return PAINTS[Hash.range(Hash.of(seed, band, 6, 518), PAINTS.length)];
    }

    /**
     * The exit bay beyond the chase (|dz| 4-6): grated floor, two tube lights, and in its back wall
     * the EXIT bulkhead in a striped steel frame under a lit EXIT sign, with an amber battery lamp
     * beside it that never goes out.
     */
    private void fillBay(long seed, int band, int x, int a, int side, Column out) {
        int floor = floorY();
        int top = ceilingY() - 1;
        Direction in = Level2Line.inwardOf(side);
        int m = x - bayExit(seed, band, x);
        if (a == Level2Line.BAY_BACK) {
            fillRock(out);
            if (m == 0) {
                BlockState door = Level2Blocks.EXIT_BULKHEAD.get().defaultBlockState().setValue(Level2ExitBulkheadBlock.FACING, in);
                out.set(floor + 1, door.setValue(ExitDoorBlock.HALF, DoubleBlockHalf.LOWER));
                out.set(floor + 2, door.setValue(ExitDoorBlock.HALF, DoubleBlockHalf.UPPER));
                out.set(floor + 3, ModBlocks.EXIT_SIGN.get().defaultBlockState());
                out.set(top, Level2Parts.frame(in, Part.TOP));
            } else if (Math.abs(m) == 1) {
                Direction toDoor = m < 0 ? Direction.EAST : Direction.WEST;
                for (int y = floor + 1; y < top; y++) {
                    out.set(y, Level2Parts.frame(toDoor, Part.SIDE));
                }
                out.set(top, Level2Parts.frame(toDoor, Part.CORNER));
            }
            return;
        }
        if (a == Level2Line.BAY_BACK - 1 && Math.abs(m) >= Level2Line.BAY_HALF - 1) {
            fillRock(out); // the bay's back corners are solid, so it reads as a clean recess
            return;
        }
        if (Math.abs(m) <= 1) {
            // A drain grate, with a dark sump under it (so nothing shows through if the grate is see-through).
            out.set(floor, ModBlocks.FLOOR_GRATE.get().defaultBlockState());
            out.set(floor - 1, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        } else {
            out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        }
        if (a == Level2Line.WALL && Math.abs(m) == 3) {
            out.set(top, ModBlocks.CEILING_TUBE.get().defaultBlockState().setValue(CeilingTubeBlock.AXIS, Direction.Axis.X));
        } else if (a == Level2Line.BAY_BACK - 1 && Math.abs(m) == 2) {
            out.set(floor + 3, Level2Parts.stencil(in, Variant.EXIT));
        } else if (a == Level2Line.BAY_BACK - 1 && m == 3) {
            out.set(floor + 3, Level2LightBlocks.emergency(in, false, true));
        }
    }

    /**
     * A pump bay's alcove beyond the chase (|dz| 4-5): the 2x2x2 pump set with its discharge pipe
     * rising into the ceiling, a short chase of its own along the back wall whose pipes run into
     * the pump's side and the alcove's end walls through collars, a tube light, and a cold mercury
     * lamp over the machine.
     */
    private void fillAlcove(long seed, int band, int n, int x, int a, int side, Column out) {
        int floor = floorY();
        int top = ceilingY() - 1;
        Direction in = Level2Line.inwardOf(side);
        Direction wall = Level2Line.wallOf(side);
        int m = x - Level2Line.alcoveMid(seed, band, n);
        out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        boolean back = a == Level2Line.WALL + 1;
        if (m == 0 || m == 1) {
            // wx/wz count from the pump's smallest-x, smallest-z corner (the back row is the smaller z on the north side).
            int wz = back == (side < 0) ? 0 : 1;
            out.set(floor + 1, Level2Blocks.PUMP.get().partAt(in, m, 0, wz));
            out.set(floor + 2, Level2Blocks.PUMP.get().partAt(in, m, 1, wz));
            if (Level2Parts.pumpOutlet(in, m, wz)) {
                out.set(floor + 3, Level2Parts.centrePipe(Direction.Axis.Y, Kind.STEEL, Size.LARGE));
                out.set(top, Level2Parts.centrePipe(Direction.Axis.Y, Kind.STEEL, Size.LARGE));
            }
            return;
        }
        if (!back) {
            if (Math.abs(m) == 3) {
                out.set(top, ModBlocks.CEILING_TUBE.get().defaultBlockState().setValue(CeilingTubeBlock.AXIS, Direction.Axis.X));
            }
            return;
        }
        out.set(floor + 1, Level2Parts.duct(in, Math.floorMod(x, 4) == 0));
        // A small steel line: from the west end wall to a valve, and from the pump's side to the east
        // end wall. A pipe end's direction is the side its pipes arrive from (the rest of the run).
        out.set(floor + 2, m == -2 ? Level2Parts.valve(in, true)
                : m == -1 ? Level2Parts.stencil(in, Variant.DANGER_HOT)
                : m == -3 ? Level2Parts.pipeEnd(in, Direction.WEST, true, Kind.STEEL, Size.SMALL)
                : m == 2 ? Level2Parts.pipeEnd(in, Direction.EAST, false, Kind.STEEL, Size.SMALL) // into the pump's casing
                : Math.abs(m) == Level2Line.ALCOVE_HALF
                        ? Level2Parts.pipeEnd(in, m < 0 ? Direction.EAST : Direction.WEST, false, Kind.STEEL, Size.SMALL)
                : Level2Parts.pipe(Direction.Axis.X, wall, Kind.STEEL, Size.SMALL, Math.floorMod(x + 1, 4) == 0));
        // Higher up a copper pair in two runs, bending into the wall at the gap round the pump.
        out.set(floor + 3, m == -1 ? Level2LightBlocks.mercury(in)
                : m == 2 ? Level2Parts.gauge(in)
                : m == 3 ? Level2Parts.steamVent(in)
                : m == -2 ? Level2Parts.pipeEnd(in, Direction.WEST, true, Kind.COPPER, Size.MEDIUM)
                : m == 4 ? Level2Parts.pipeEnd(in, Direction.EAST, true, Kind.COPPER, Size.MEDIUM)
                : Math.abs(m) == Level2Line.ALCOVE_HALF
                        ? Level2Parts.pipeEnd(in, m < 0 ? Direction.EAST : Direction.WEST, false, Kind.COPPER, Size.MEDIUM)
                : Level2Parts.pipe(Direction.Axis.X, wall, Kind.COPPER, Size.MEDIUM, Math.floorMod(x + 2, 5) == 0));
        out.set(top, Level2Parts.rack(in, Math.floorMod(x, 3) == 0));
    }
}
