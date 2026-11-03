package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.CeilingTubeBlock;
import com.rickr.backrooms.block.ExitDoorBlock;
import com.rickr.backrooms.block.Level2BulkheadFrameBlock.Part;
import com.rickr.backrooms.block.Level2ExitBulkheadBlock;
import com.rickr.backrooms.block.Level2PipeBlock.Kind;
import com.rickr.backrooms.block.Level2PipeBlock.Size;
import com.rickr.backrooms.block.Level2StencilBlock.Variant;
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
 * Level 2, "Pipe Dreams": one long, straight, cramped maintenance corridor that never ends, barely
 * wide enough to walk, one wall bare stained concrete and the other a dense rack of rusty pipes.
 * You can only go forwards or back; short slots through the walls lead into small dead-end rooms,
 * and every few hundred blocks a heavy EXIT bulkhead waits in a bay at the side.
 *
 * <p><b>Floor plan</b> ({@link Level2Line}): a corridor along X every 48 blocks of Z, solid concrete
 * in between, so parallel corridors never meet. Seen down the corridor (looking east, a plain
 * stretch with the rack on the right):
 * <pre>
 *   slab     y45  # # # # # # # # #
 *   ceiling  y44  # # # c L = h # #     c concrete ceiling, L recessed light panel (every 12 blocks),
 *                                       = the bundle's pipes hung under the slab, h its top pipes
 *            y43  # # # . . p r # #     p the rack's front: a pair of pipes at head height
 *            y42  # # B . . k r # #     k the rusty three on a strut channel, B the painted band
 *            y41  # # w . . D r # #     D the big pale main on the floor, r the rack's back pipes, w scuffed base
 *   floor    y40  # # # g . = # # #     g drainage gutter, = grating with pipes under it
 *            dz  -4-3-2-1 0+1+2+3+4
 * </pre>
 * Two clear blocks to walk in (dz -1 and 0) under a ceiling three blocks up; the front pipes take
 * dz +1. In the twist stretches ({@link Level2Twist}, built by {@link Level2Section}) the bundle,
 * the light panels and the painted band turn round the cross-section together, over the ceiling
 * onto the other wall or under a floor grating, and the floor of a banked stretch rises in half
 * steps on one side while the ceiling drops on the other. Every 24-48 blocks the corridor changes
 * character (a valve manifold, a pump bay, a low squeeze, a grated trench, a bulkhead ring, a leak,
 * a dark stretch); side rooms (maintenance, ventilation, boiler, storage, safe room) come every
 * 60-120 blocks on alternating sides; an exit bay every 320-420 blocks.
 *
 * <p><b>Anomalies</b> ({@link Level2AnomalyPlan}, world/anomaly/level2): brownout stretches where the
 * Blackout never ended, a hot zone in some boiler rooms, a door onto the void and a door onto a
 * looping office hallway, each in a little hall off the corridor. The light panels also hang on
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
     * except in the odd detour (a dog-leg's column, a bypass's sealed section) or on the raised
     * half of a banked stretch: then a few blocks along the line, the nearest spot that is open
     * and level again. Twist stretches come every 76 blocks ({@link Level2Twist}), so one is always
     * within about 30 blocks of where you land.
     */
    @Override
    public BlockPos preferredArrival(long seed, int x, int z, @Nullable BackroomsLevel from) {
        int c = Level2Line.centre(seed, Level2Line.band(z));
        for (int d = 0; d <= 2 * Level2Detours.BYPASS_HALF; d++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                int ax = x + sign * d;
                if (column(seed, ax, c) == OPEN && floorRaise(seed, ax, c) == 0) {
                    return new BlockPos(ax, FLOOR_Y + 1, c);
                }
                if (d == 0) {
                    break;
                }
            }
        }
        return new BlockPos(x, FLOOR_Y + 1, c);
    }

    /**
     * The lane is two blocks wide between bare concrete and the rack, so no spot in it has four
     * open neighbours: spawners and searches take any safe column, like Level 9's sidewalks.
     */
    @Override
    public boolean arrivalNeedsSpace() {
        return false;
    }

    /** Feet are half a block up on a banked stretch's half step, a whole block up on its high edge. */
    @Override
    public int standY(long seed, int x, int z) {
        return FLOOR_Y + 1 + floorRaise(seed, x, z) / 2;
    }

    @Override
    public double feetY(long seed, int x, int z) {
        return FLOOR_Y + 1 + 0.5 * floorRaise(seed, x, z);
    }

    /** How many half steps a banked stretch lifts the floor of column (x, z) (0 off the lane). */
    int floorRaise(long seed, int x, int z) {
        if (Level2Line.outsideReach(z)) {
            return 0;
        }
        int band = Level2Line.band(z);
        int dz = z - Level2Line.centre(seed, band);
        if (Math.abs(dz) > Level2Line.LANE || dz != 0 && Level2Section.laneBlocked(seed, band, x, dz)) {
            return 0;
        }
        return Level2Section.raise(Level2Section.bankSteps(seed, band, x), dz);
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
     * The spot right in front of the nearest exit door on your own corridor (the floor of its
     * cramped bay, one block from the bulkhead): the one /backrooms locate, the exit's sound, the
     * NightShot marks and the wanderers' notes all point at.
     */
    @Override
    public int[] exitPosition(long seed, int x, int z) {
        int band = Level2Line.band(z);
        int j = Level2Line.nearestExit(seed, band, x);
        return new int[] {Level2Line.exitX(seed, band, j),
                Level2Line.centre(seed, band) + Level2Line.exitSide(seed, band, j) * (Level2Line.BAY_BACK - 1)};
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
            // The lane: always open in the middle; beside a wall carrying the rack, its front pipes take the column.
            return a != 0 && Level2Section.laneBlocked(seed, band, x, side) ? CHASE : OPEN;
        }
        if (a == Level2Line.SHOULDER) {
            // The inner wall (bare concrete or the rack), cut by short passages at doorways and bays.
            return Level2Section.passage(seed, band, x, side) ? OPEN : CHASE;
        }
        // Anomaly structures (hot rooms, void and loop doors) start at the chase.
        int anomaly = Level2AnomalyPlan.column(siteKnown ? site : Level2AnomalyPlan.at(seed, x, z), x, z);
        if (anomaly >= 0) {
            return anomaly;
        }
        int feature = Level2Line.feature(seed, band, x, side);
        if (a == Level2Line.CHASE) {
            if (feature == Level2Line.F_BAY) {
                return bayOpen(seed, band, x) ? OPEN : CHASE; // the bay narrows behind its mouth
            }
            return feature == Level2Line.F_NONE || feature == Level2Line.F_HOT ? CHASE : OPEN;
        }
        if (a == Level2Line.WALL) {
            switch (feature) {
                case Level2Line.F_DOOR:
                    return DOOR;
                case Level2Line.F_MOUTH:
                    return OPEN;
                case Level2Line.F_BAY:
                    return bayOpen(seed, band, x) ? OPEN : CHASE;
                case Level2Line.F_ALCOVE:
                    return pumpCell(seed, band, x) ? CHASE : OPEN;
                default:
                    break; // safe room door wall, or plain wall: see below
            }
        } else if (feature == Level2Line.F_BAY && a <= Level2Line.BAY_BACK) {
            return CHASE; // the back wall round the bulkhead (and the bulkhead itself)
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

    /**
     * One column of the corridor itself: the lane, the inner walls (bare concrete or the pipe
     * rack), the concrete behind them with the passages through it, and the bays beyond.
     */
    private void fillCorridor(long seed, int x, int z, @Nullable AnomalySite site, Column out) {
        int band = Level2Line.band(z);
        int dz = z - Level2Line.centre(seed, band);
        int a = Math.abs(dz);
        int side = dz < 0 ? -1 : 1;
        int n = Level2Line.segment(seed, band, x);
        int type = Level2Line.segType(seed, band, n);
        AnomalySite brownout = site != null && site.kind().equals(Level2Sites.BROWNOUT) ? site : null;
        out.set(ceilingY(), ModBlocks.PIPE_CEILING.get().defaultBlockState());
        // Detours (Level2Detours, L2Chambers): the odd stretch builds some columns itself.
        if (Level2Detours.fill(this, seed, band, n, type, x, dz, out)) {
            return;
        }
        if (a <= Level2Line.LANE) {
            fillLane(seed, band, n, type, x, z, dz, site, brownout, out);
        } else if (a == Level2Line.SHOULDER) {
            fillInner(seed, band, n, type, x, z, side, site, brownout, out);
        } else {
            int feature = Level2Line.feature(seed, band, x, side);
            if (a == Level2Line.CHASE && feature == Level2Line.F_BAY) {
                fillBay(seed, band, x, a, side, out);
            } else if (a == Level2Line.CHASE) {
                if (Level2Section.passage(seed, band, x, side)) {
                    fillPassage(a, feature, out);
                } else {
                    fillRock(out);
                }
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
     * One lane column (dz -1..1, see {@link Level2Section}): the floor (concrete, the gutter, the
     * painted band, or a grating with the bundle under it), the lowered ceiling at y44 (concrete,
     * a light panel, the band, or the bundle hung under the slab), the rack's front pipes when the
     * bundle is on this column's wall, the roll's half steps, and the water and warnings.
     */
    private void fillLane(long seed, int band, int n, int type, int x, int z, int dz, @Nullable AnomalySite site,
                          @Nullable AnomalySite brownout, Column out) {
        int floor = floorY();
        int top = ceilingY() - 1;
        int side = dz < 0 ? -1 : 1;
        double s = Level2Section.ring(seed, band, x);
        boolean trench = type == Level2Line.SEG_TRENCH && Level2Line.hasTrench(seed, band, n)
                && fillTrench(seed, band, n, x, dz, out);
        if (!trench) {
            fillFloor(seed, band, type, x, dz, s, site, out);
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
        // The rack's front pipes, in this lane column when the bundle is on its wall.
        boolean blocked = dz != 0 && Level2Section.laneBlocked(seed, band, x, side);
        boolean frontTop = false;
        if (blocked) {
            for (int row = 1; row <= 3; row++) {
                if (Level2Section.front(seed, band, x, side, row)) {
                    out.set(floor + row, frontPiece(seed, band, x, side, row));
                    frontTop |= row == 3;
                }
            }
        }
        // The roll: the floor up in half steps on the high edge, the ceiling down on the low edge.
        int steps = blocked ? 0 : Level2Section.bankSteps(seed, band, x);
        int raise = Level2Section.raise(steps, dz);
        int drop = Level2Section.drop(steps, dz);
        if (raise > 0) {
            out.set(floor + 1, raise == 1 ? Level2Parts.floorSlab() : ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        }
        // Overhead (ring row 4).
        int lamp = Level2Line.lampAt(seed, band, x) ? Level2Section.lampSlot(s) : -1;
        if (lamp >= 3 && lamp <= 5 && Level2Section.drop(steps, 4 - lamp) > 0) {
            lamp = Level2Section.slot(0, 4); // its cell is behind the dropped ceiling: the middle of the ceiling instead
        }
        int i = Level2Section.slot(dz, 4);
        if (Level2Section.bundle(s, i)) {
            out.set(top, hungPiece(seed, band, x, dz, i));
        } else if (i == lamp) {
            out.set(top, Level2Parts.lightPanel(Direction.DOWN, lampDead(seed, band, n, type, x, z, brownout)));
        } else if (i == Level2Section.stripeSlot(s)) {
            out.set(top, Level2Parts.paintedBand(Direction.DOWN));
        } else {
            out.set(top, ModBlocks.PIPE_CEILING.get().defaultBlockState());
        }
        if (drop > 0) {
            out.set(top - 1, drop == 1 ? Level2Parts.ceilingSlab() : ModBlocks.PIPE_CEILING.get().defaultBlockState());
        }
        boolean topFree = !frontTop && drop == 0;
        if (type == Level2Line.SEG_SQUEEZE) {
            if (x == Level2Line.segStart(seed, band, n) || x == Level2Line.segEnd(seed, band, n)) {
                // A low concrete beam at each end of the squeeze: the sagging pipes run into it (2 blocks clear under it).
                out.set(top - 1, ModBlocks.PIPE_WALL.get().defaultBlockState());
                out.set(top, ModBlocks.PIPE_WALL.get().defaultBlockState());
            } else if (topFree) {
                // The whole ceiling sags: pipes in the top half of the lane's top row, 2.5 blocks to walk in.
                out.set(top - 1, sagPipe(x, dz));
            }
        } else if (topFree && raise == 0 && i >= 0 && Level2Section.bundle(s, i) && Level2Section.sags(s, dz)) {
            out.set(top - 1, sagPipe(x, dz)); // the bundle overhead hangs down into the top row
        }
        if (dz == 0 || blocked) {
            return;
        }
        // On the open floor beside a wall: leaks, the gutter's ooze, warnings, battery lights.
        boolean gutter = Level2Line.gutterSide(seed, band) == side;
        boolean leak = leakAt(seed, band, x, side);
        if (leak && topFree) {
            out.set(top - 1, Level2Blocks.DRIP.get().defaultBlockState()); // under the weeping joint overhead
        }
        if (raise == 0 && !gutter && (leak || leakAt(seed, band, x - 1, side) || leakAt(seed, band, x + 1, side))) {
            out.set(floor + 1, Level2Parts.puddle()); // (over the gutter the water runs off)
        }
        if (raise == 0 && gutter && type == Level2Line.SEG_LEAK && Math.abs(x - Level2Line.segMid(seed, band, n)) <= 2) {
            out.set(floor + 1, ModBlocks.L2_BLACK_OOZE.get().defaultBlockState());
        }
        if (raise == 0 && gutter && brownout != null) {
            int burst = Level2AnomalyPlan.burstX(seed, brownout, x);
            if (burst != Integer.MIN_VALUE && Math.abs(x - burst) <= 2) {
                out.set(floor + 1, ModBlocks.L2_BLACK_OOZE.get().defaultBlockState());
            }
        }
        Direction in = Level2Line.inwardOf(side);
        int k = Level2Line.roomCell(x);
        if (Level2Line.roomType(seed, band, k) == Level2Line.ROOM_HOT && Level2Line.slotSide(seed, band, k) == side
                && Math.abs(x - Level2Line.slotX(seed, band, k)) == 4) {
            out.set(floor + 2, Level2Parts.stencil(in, Variant.DANGER_HOT)); // on the wall each side of the hot room's way in
        }
        if (type == Level2Line.SEG_LEAK && side != Level2Line.gutterSide(seed, band) && topFree
                && x == Level2Line.segMid(seed, band, n) + JET_OFFSET + 1) {
            out.set(top - 1, Level2Parts.stencil(in, Variant.STEAM)); // beside the steam jet in the wall
        }
        if (type == Level2Line.SEG_BULKHEAD) {
            int frame = Level2Line.frameX(seed, band, n);
            if (frame != Integer.MIN_VALUE && x == frame + emergencyOffset(seed, band, n) && side == -Level2Line.gutterSide(seed, band)) {
                // One red battery lamp on the wall beside the ring, dark until this section's circuit dies.
                out.set(floor + 3, Level2LightBlocks.emergency(in, true, false));
            }
        }
        if (brownout != null && Level2Line.lampAt(seed, band, x) && side == Level2Section.bareSide(s)
                && Level2AnomalyPlan.emergencyLight(seed, x, z)) {
            out.set(floor + 2, Level2Parts.emergencyLight(in)); // the violet battery light on the bare wall
        }
    }

    /**
     * The floor of a lane column: a grating with the bundle under it where the bundle runs under the
     * floor, else the painted band, the gutter or plain concrete. Wherever the bundle is under the
     * floor at x or beside it, the lane and the inner walls get a shallow concrete trench under the
     * floor (except over a void, in a trench segment and in detours, which have their own below).
     */
    private void fillFloor(long seed, int band, int type, int x, int dz, double s, @Nullable AnomalySite site, Column out) {
        int floor = floorY();
        boolean under = underFloor(seed, band, type, x, site);
        int i = Level2Section.slot(dz, 0);
        if (under && Level2Section.bundle(s, i)) {
            out.set(floor, Level2Parts.grating(Direction.Axis.X));
            out.set(floor - 1, switch (dz) {
                case -1 -> Level2Parts.pipe(Direction.Axis.X, Direction.DOWN, Kind.INSULATED, Size.LARGE, Math.floorMod(x, 4) == 0);
                case 0 -> Level2Parts.pipe(Direction.Axis.X, Direction.DOWN, Kind.STEEL, Size.MEDIUM, Math.floorMod(x + 1, 4) == 0);
                default -> Level2Parts.pipe(Direction.Axis.X, Direction.DOWN, Kind.COPPER, Size.SMALL, Math.floorMod(x + 2, 4) == 0);
            });
            out.set(floor - 2, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
            return;
        }
        if (under) {
            out.set(floor - 1, ModBlocks.PIPE_WALL.get().defaultBlockState());
            out.set(floor - 2, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        }
        int side = dz < 0 ? -1 : 1;
        if (i == Level2Section.stripeSlot(s)) {
            out.set(floor, Level2Parts.floorLine(dz == 0 ? Direction.NORTH : Level2Line.wallOf(dz)));
        } else if (dz != 0 && Level2Line.gutterSide(seed, band) == side) {
            out.set(floor, Level2Parts.gutter(Direction.Axis.X));
        } else {
            out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        }
    }

    /** True if the shallow trench under the floor is built at x: the bundle is under a lane column at x or beside it. */
    private static boolean underFloor(long seed, int band, int type, int x, @Nullable AnomalySite site) {
        if (type == Level2Line.SEG_TRENCH || Level2Detours.isDetour(type)
                || site != null && site.kind().equals(Level2Sites.VOID_DOOR)) {
            return false; // their own trench, a lower walkway, or the void's black sky right under the floor
        }
        for (int nx = x - 1; nx <= x + 1; nx++) {
            double s = Level2Section.ring(seed, band, nx);
            for (int dz = -Level2Line.LANE; dz <= Level2Line.LANE; dz++) {
                if (Level2Section.bundle(s, Level2Section.slot(dz, 0))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * An inner wall column (|dz| 2): a passage at a doorway or bay, a bulkhead ring's post, a
     * valve manifold, or the ring's four wall cells: the bundle's back pipes against the concrete
     * behind, or bare concrete with a light panel or the painted band in it.
     */
    private void fillInner(long seed, int band, int n, int type, int x, int z, int side, @Nullable AnomalySite site,
                           @Nullable AnomalySite brownout, Column out) {
        int floor = floorY();
        Direction in = Level2Line.inwardOf(side);
        out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        if (underFloor(seed, band, type, x, site)) {
            out.set(floor - 1, ModBlocks.PIPE_WALL.get().defaultBlockState()); // the side walls of the shallow trench
            out.set(floor - 2, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        }
        if (type == Level2Line.SEG_TRENCH && Level2Line.hasTrench(seed, band, n)
                && x >= Level2Line.segStart(seed, band, n) + 2 && x <= Level2Line.segEnd(seed, band, n) - 2) {
            for (int y = floor - 3; y < floor; y++) {
                out.set(y, ModBlocks.PIPE_WALL.get().defaultBlockState()); // the trench's side walls
            }
        }
        if (Level2Section.passage(seed, band, x, side)) {
            fillPassage(Level2Line.SHOULDER, Level2Line.feature(seed, band, x, side), out);
            return;
        }
        if (type == Level2Line.SEG_BULKHEAD && Level2Line.frameX(seed, band, n) == x) {
            fillFrameSide(in, out);
            return;
        }
        if (Level2Section.manifoldAt(seed, band, x, side)) {
            fillManifold(seed, band, n, x, side, out);
            return;
        }
        double s = Level2Section.ring(seed, band, x);
        int lamp = Level2Line.lampAt(seed, band, x) ? Level2Section.lampSlot(s) : -1;
        int stripe = Level2Section.stripeSlot(s);
        for (int row = 1; row <= 4; row++) {
            int i = Level2Section.slot(2 * side, row);
            BlockState state;
            if (Level2Section.bundle(s, i)) {
                state = backPiece(seed, band, x, side, row, i);
            } else if (i == lamp) {
                state = Level2Parts.lightPanel(in, lampDead(seed, band, n, type, x, z, brownout));
            } else if (i == stripe) {
                state = Level2Parts.paintedBand(in);
            } else {
                state = row == 1 ? Level2Blocks.WALL_BASE.get().defaultBlockState() : ModBlocks.PIPE_WALL.get().defaultBlockState();
            }
            out.set(floor + row, state);
        }
        if (type == Level2Line.SEG_LEAK && side != Level2Line.gutterSide(seed, band)
                && x == Level2Line.segMid(seed, band, n) + JET_OFFSET) {
            // The leak's steam jet: a hissing vent over a red valve, set into the wall.
            out.set(floor + 2, Level2Parts.valve(in, true));
            out.set(floor + 3, Level2Parts.steamVent(in));
        }
    }

    /** The leak segment's steam jet stands this far east of the segment's middle. */
    private static final int JET_OFFSET = 6;

    /**
     * A passage through the inner wall (|dz| 2) or the concrete behind it (|dz| 3): floor, the
     * lowered ceiling carried on through, and for a side room's door a lintel at the inner wall so
     * you duck into a one-wide, two-high slot with the door at its end.
     */
    private void fillPassage(int a, int feature, Column out) {
        out.set(floorY(), ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        out.set(ceilingY() - 1, ModBlocks.PIPE_CEILING.get().defaultBlockState());
        if (a == Level2Line.SHOULDER && (feature == Level2Line.F_DOOR || feature == Level2Line.F_SAFE_DOOR)) {
            out.set(ceilingY() - 2, ModBlocks.PIPE_WALL.get().defaultBlockState());
        }
    }

    /**
     * A valve manifold column in the inner wall (|dz| 2): the insulated main low down, and from the
     * header hung in the top corner a branch drops into a valve every third column, a gauge
     * between, the label in the middle; the gaps are bare niches in the wall.
     */
    private void fillManifold(long seed, int band, int n, int x, int side, Column out) {
        int floor = floorY();
        int top = ceilingY() - 1;
        Direction in = Level2Line.inwardOf(side);
        Kind header = headerKind(seed, band, n);
        int start = Level2Line.segStart(seed, band, n);
        int end = Level2Line.segEnd(seed, band, n);
        out.set(floor + 1, Level2Parts.duct(in, Math.floorMod(x, 6) == 0));
        int r = Math.floorMod(x - start, 3);
        if (x == Level2Line.segMid(seed, band, n)) {
            out.set(floor + 2, Level2Parts.stencil(in, LABELS[Hash.range(Hash.of(seed, band, n, 517), LABELS.length)]));
            out.set(floor + 3, Level2Parts.gauge(in));
        } else if (r == 0) {
            out.set(floor + 3, Level2Parts.feedPipe(in, header));
            long hv = Hash.of(seed, band, x, 533);
            out.set(floor + 2, header != Kind.PAINTED_RED && Hash.unit(hv) < 0.45
                    ? Level2Parts.leverValve(in, Hash.unit(Hash.mix(hv)) < 0.8, true)
                    : Level2Parts.valve(in, header == Kind.PAINTED_RED, true));
        } else if (r == 1) {
            out.set(floor + 3, Level2Parts.gauge(in));
        }
        // The header: a fatter pair in the colour of what it carries, a flange at each end of the run.
        boolean flange = x == start + 2 || x == end - 2 || Math.floorMod(x + 1, 5) == 0;
        out.set(top, Level2Parts.hung(Direction.Axis.X, header, Size.MEDIUM, flange, false));
    }

    // ---- The bundle's pieces -------------------------------------------------------------------

    /**
     * One of the rack's front pipes in the lane column beside its wall: the big pale main on the
     * floor, the rusty three on a strut channel at waist height, a pair at head height. Where the
     * run stops (a doorway, the bundle turning off the wall) the pipes bend into the rack.
     */
    private static BlockState frontPiece(long seed, int band, int x, int side, int row) {
        Direction in = Level2Line.inwardOf(side);
        Direction wall = Level2Line.wallOf(side);
        boolean west = Level2Section.front(seed, band, x - 1, side, row);
        boolean east = Level2Section.front(seed, band, x + 1, side, row);
        Kind kind = row == 1 ? Kind.INSULATED : row == 2 ? Kind.STEEL : rackKind(seed, band, side);
        Size size = row == 1 ? Size.LARGE : row == 2 ? Size.SMALL : Size.MEDIUM;
        if (west != east) {
            return Level2Parts.pipeEnd(in, west ? Direction.WEST : Direction.EAST, true, kind, size);
        }
        return switch (row) {
            case 1 -> Level2Parts.duct(in, Math.floorMod(x + 3 * side, 6) == 0);
            case 2 -> Level2Parts.rack(in, Level2Line.bracketAt(seed, band, x));
            default -> Level2Parts.pipe(Direction.Axis.X, wall, kind, size, Math.floorMod(x + 2, 5) == 0);
        };
    }

    /** True if the inner wall on {@code side} carries the bundle's back pipes in ring cell i at x. */
    private static boolean backAt(long seed, int band, int x, int side, int i) {
        if (!Level2Section.bundle(Level2Section.ring(seed, band, x), i) || Level2Section.passage(seed, band, x, side)
                || Level2Section.manifoldAt(seed, band, x, side)) {
            return false;
        }
        int n = Level2Line.segment(seed, band, x);
        return !(Level2Line.segType(seed, band, n) == Level2Line.SEG_BULKHEAD && Level2Line.frameX(seed, band, n) == x);
    }

    /**
     * One of the bundle's back pipes in the inner wall column, against the concrete behind: the
     * insulated main low, a pair above it, a painted service line at head height, and a pair hung
     * in the top corner. Where the run stops the pipes bend into the wall (or up into the slab).
     */
    private static BlockState backPiece(long seed, int band, int x, int side, int row, int i) {
        Direction in = Level2Line.inwardOf(side);
        Direction wall = Level2Line.wallOf(side);
        boolean west = backAt(seed, band, x - 1, side, i);
        boolean east = backAt(seed, band, x + 1, side, i);
        Kind kind = switch (row) {
            case 1 -> Kind.INSULATED;
            case 2 -> LOW_KINDS[Hash.range(Hash.of(seed, 2 * band + (side < 0 ? 0 : 1), 0, 519), LOW_KINDS.length)];
            case 3 -> servicePaint(seed, band);
            default -> Kind.STEEL;
        };
        Size size = row == 1 ? Size.LARGE : row == 3 ? Size.SMALL : Size.MEDIUM;
        boolean flange = Math.floorMod(x + 2 * row, 5) == 0;
        if (row == 4) {
            if (west != east) {
                return Level2Parts.hungEnd(Direction.NORTH, west ? Direction.WEST : Direction.EAST, kind, size);
            }
            boolean bracket = Level2Line.bracketAt(seed, band, x);
            return Level2Parts.hung(Direction.Axis.X, kind, size, flange && !bracket, bracket);
        }
        if (west != east) {
            return Level2Parts.pipeEnd(in, west ? Direction.WEST : Direction.EAST, true, kind, size);
        }
        return Level2Parts.pipe(Direction.Axis.X, wall, kind, size, flange);
    }

    /**
     * The bundle hung under the slab over lane column dz (ring cell i): the cable tray in the
     * middle, a steel pair or the painted service line either side, on a steel channel bracket
     * every few blocks; where the run stops its pipes bend up into the slab.
     */
    private static BlockState hungPiece(long seed, int band, int x, int dz, int i) {
        boolean bracket = Level2Line.bracketAt(seed, band, x);
        if (dz == 0) {
            return Level2Parts.cableTray(Direction.Axis.X, bracket, !bracket && droopAt(seed, band, x));
        }
        Kind kind = dz > 0 ? Kind.STEEL : servicePaint(seed, band);
        Size size = dz > 0 ? Size.MEDIUM : Size.SMALL;
        boolean west = Level2Section.bundle(Level2Section.ring(seed, band, x - 1), i);
        boolean east = Level2Section.bundle(Level2Section.ring(seed, band, x + 1), i);
        if (west != east) {
            return Level2Parts.hungEnd(Direction.NORTH, west ? Direction.WEST : Direction.EAST, kind, size);
        }
        return Level2Parts.hung(Direction.Axis.X, kind, size, !bracket && Math.floorMod(x + 2 * dz, 5) == 0, bracket);
    }

    /**
     * The bundle sagging into the lane's top row (y43): pipes against the top of the block, the
     * lowest at 43.6, so even over a half step 2 blocks stay clear.
     */
    private static BlockState sagPipe(int x, int dz) {
        return dz == 0
                ? Level2Parts.pipe(Direction.Axis.X, Direction.UP, Kind.INSULATED, Size.MEDIUM, Math.floorMod(x + 1, 5) == 0)
                : Level2Parts.pipe(Direction.Axis.X, Direction.UP, Kind.STEEL, Size.SMALL, Math.floorMod(x + 3 + dz, 5) == 0);
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

    /** A dead tube: in a dark segment's dark run (all but one), or in a brownout (most). */
    private static boolean lampDead(long seed, int band, int n, int type, int x, int z, @Nullable AnomalySite brownout) {
        if (brownout != null && !Level2AnomalyPlan.lampSurvives(seed, x, z)) {
            return true;
        }
        return type == Level2Line.SEG_DARK && x >= Level2Line.darkFrom(seed, band, n) && x <= Level2Line.darkTo(seed, band, n)
                && x != Level2Line.survivorX(seed, band, n);
    }

    /** One side post of a bulkhead ring (in the inner wall), its lips facing the lane. */
    private void fillFrameSide(Direction in, Column out) {
        int floor = floorY();
        for (int y = floor + 1; y < ceilingY() - 1; y++) {
            out.set(y, Level2Parts.frame(in, Part.SIDE));
        }
        out.set(ceilingY() - 1, Level2Parts.frame(in, Part.CORNER));
    }

    /** The colour of a valve segment's header, matching the label stencilled on the manifold. */
    private static Kind headerKind(long seed, int band, int n) {
        return HEADERS[Hash.range(Hash.of(seed, band, n, 517), HEADERS.length)];
    }

    /** The pipes along the top of the rack's front on {@code side} (one kind per corridor and side). */
    private static Kind rackKind(long seed, int band, int side) {
        return HIGH_KINDS[Hash.range(Hash.of(seed, 2 * band + (side < 0 ? 0 : 1), 1, 519), HIGH_KINDS.length)];
    }

    // ---- Leaks, the bulkhead's battery lamp ------------------------------------------------------

    /** About one tray block in 16 (never at a bracket) has a loop of loose cable sagging out of it. */
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
     * True if a joint overhead weeps at x on {@code side}: a drip hangs under it and a puddle
     * spreads on the floor. Leaks gather where the walls and floor are damp (Level2Damp), and keep
     * clear of the lamps, the doorways and the segments that already have water of their own (the
     * leak segment) or no room for it.
     */
    private static boolean leakAt(long seed, int band, int x, int side) {
        int cell = Math.floorDiv(x, LEAK_CELL);
        long h = Hash.of(seed, band, cell, 530);
        if (Hash.unit(h) >= LEAK_CHANCE || (((h >>> 20) & 1) == 0 ? -1 : 1) != side) {
            return false;
        }
        int lx = cell * LEAK_CELL + 2 + Hash.range(Hash.mix(h), LEAK_CELL - 4);
        lx -= Math.floorMod(lx + 4, 5); // back to a joint of the pipes overhead
        if (x != lx || Level2Line.lampAt(seed, band, x)) {
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

    private static final Variant[] LABELS = {Variant.STEAM, Variant.RETURN, Variant.H2O};
    /** A manifold header's paint for each label above: red steam, grey return, green water. */
    private static final Kind[] HEADERS = {Kind.PAINTED_RED, Kind.PAINTED_BLUE, Kind.PAINTED_GREEN};
    /** The rack's middle pipes: grey steel, copper, insulated, painted. */
    private static final Kind[] LOW_KINDS = {Kind.STEEL, Kind.STEEL, Kind.COPPER, Kind.INSULATED, Kind.PAINTED_BLUE, Kind.PAINTED_GREEN};
    /** The pipes along the top of the rack's front. */
    private static final Kind[] HIGH_KINDS = {Kind.COPPER, Kind.STEEL, Kind.STEEL, Kind.PAINTED_RED, Kind.PAINTED_YELLOW, Kind.CONDUIT};

    private static final Kind[] PAINTS = {Kind.PAINTED_RED, Kind.PAINTED_BLUE, Kind.PAINTED_YELLOW, Kind.PAINTED_GREEN};

    /** The painted service line in the bundle (one colour per corridor). */
    private static Kind servicePaint(long seed, int band) {
        return PAINTS[Hash.range(Hash.of(seed, band, 6, 518), PAINTS.length)];
    }

    /**
     * The exit bay behind its 5-wide mouth in the inner wall (|dz| 2, a plain passage): a cramped
     * recess only the door's 3 columns wide through |dz| 3-4, under the lowered ceiling, pipes
     * standing floor to ceiling on either side and a pair hung overhead running back into the wall.
     * In its back wall (|dz| 5) the EXIT bulkhead in a striped steel frame under the lit EXIT sign,
     * a drain grate in front of it and an amber battery lamp on the frame that never goes out: the
     * only light in there.
     */
    private void fillBay(long seed, int band, int x, int a, int side, Column out) {
        int floor = floorY();
        int top = ceilingY() - 1;
        Direction in = Level2Line.inwardOf(side);
        int m = x - bayExit(seed, band, x);
        int am = Math.abs(m);
        if (a == Level2Line.BAY_BACK) {
            fillRock(out);
            if (m == 0) {
                BlockState door = Level2Blocks.EXIT_BULKHEAD.get().defaultBlockState().setValue(Level2ExitBulkheadBlock.FACING, in);
                out.set(floor + 1, door.setValue(ExitDoorBlock.HALF, DoubleBlockHalf.LOWER));
                out.set(floor + 2, door.setValue(ExitDoorBlock.HALF, DoubleBlockHalf.UPPER));
                out.set(floor + 3, ModBlocks.EXIT_SIGN.get().defaultBlockState());
                out.set(top, Level2Parts.frame(in, Part.TOP));
            } else if (am == 1) {
                Direction toDoor = m < 0 ? Direction.EAST : Direction.WEST;
                for (int y = floor + 1; y < top; y++) {
                    out.set(y, Level2Parts.frame(toDoor, Part.SIDE));
                }
                out.set(top, Level2Parts.frame(toDoor, Part.CORNER));
            }
            return;
        }
        out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        if (am > BAY_INNER_HALF) {
            // Either side of the recess: pipes standing floor to ceiling against its open side.
            Direction against = m > 0 ? Direction.WEST : Direction.EAST;
            Kind kind = a == Level2Line.CHASE ? Kind.INSULATED : Kind.STEEL;
            for (int y = floor + 1; y < top; y++) {
                out.set(y, Level2Parts.pipe(Direction.Axis.Y, against, kind, Size.MEDIUM, y == floor + 2));
            }
            out.set(top, ModBlocks.PIPE_WALL.get().defaultBlockState());
            return;
        }
        if (m == 0 && a == Level2Line.BAY_BACK - 1) {
            // A drain grate, with a dark sump under it (so nothing shows through if the grate is see-through).
            out.set(floor, ModBlocks.FLOOR_GRATE.get().defaultBlockState());
            out.set(floor - 1, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        }
        if (m == 0) {
            out.set(top, ModBlocks.PIPE_CEILING.get().defaultBlockState()); // the corridor's lowered ceiling, carried on in
        } else if (a == Level2Line.CHASE) {
            // The hung pair coming out of the back wall turns up into the slab at the bay's mouth.
            out.set(top, Level2Parts.hungEnd(Direction.EAST, Level2Line.wallOf(side), Kind.STEEL, Size.MEDIUM));
        } else {
            out.set(top, Level2Parts.hung(Direction.Axis.Z, Kind.STEEL, Size.MEDIUM, true, false));
        }
        if (a == Level2Line.BAY_BACK - 1 && m == -1) {
            out.set(floor + 3, Level2Parts.stencil(in, Variant.EXIT)); // on the bulkhead's frame
        } else if (a == Level2Line.BAY_BACK - 1 && m == 1) {
            out.set(floor + 3, Level2LightBlocks.emergency(in, false, true));
        }
    }

    /** Behind its mouth the exit bay is open this far each way from the door (3 columns). */
    private static final int BAY_INNER_HALF = 1;

    /** True for the exit bay's columns that stay open behind its mouth: the door's own 3. */
    private static boolean bayOpen(long seed, int band, int x) {
        return Math.abs(x - bayExit(seed, band, x)) <= BAY_INNER_HALF;
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
