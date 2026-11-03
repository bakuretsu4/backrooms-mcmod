package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.ExitDoorBlock;
import com.rickr.backrooms.block.Level2BulkheadFrameBlock.Part;
import com.rickr.backrooms.block.Level2ExitBulkheadBlock;
import com.rickr.backrooms.block.Level2PipeBlock.Kind;
import com.rickr.backrooms.block.Level2PipeBlock.Size;
import com.rickr.backrooms.block.Level2StencilBlock.Variant;
import com.rickr.backrooms.block.SupplyContainerBlock;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.registry.Level2Blocks;
import com.rickr.backrooms.registry.Level2LightBlocks;
import com.rickr.backrooms.registry.Level2SpinBlocks;
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
 * Level 2, "Pipe Dreams": one endless, straight, one-block-wide maintenance corridor, its walls
 * almost brushing your shoulders and its ceiling just over your head, its pipework slowly
 * corkscrewing round you as you walk. Nothing leads off it: the only way out is the EXIT bulkhead
 * at the back of a tiny recess, one per 1300-1600 blocks, and you always arrive at least 500
 * blocks from the nearest one.
 *
 * <p><b>Floor plan</b> ({@link Level2Line}): a corridor along X every 48 blocks of Z, solid concrete
 * in between. Seen down the corridor (looking east, the bundle on the right wall):
 * <pre>
 *   slab     y45  # # # # #
 *            y44  # # # # #     solid over the ring
 *   ceiling  y43  # c L h #     c concrete corner, L a recessed light panel (the ceiling cell), h hung pipes
 *            y42  # B . P #     P a fat pipe against the far side of the wall cell, B the painted band
 *            y41  # w . D #     D the pale main against the far side, w scuffed concrete base
 *   floor    y40  # # . = #     = grating with pipes under it
 *            y39  # # # p #     p the pipes under the grating (a shallow concrete trench)
 *            dz  -2-1 0+1+2
 * </pre>
 * The lane (dz 0, y41-42) is always clear: 1.0 to about 1.3 blocks of free width (a pipe against
 * the far side of a wall cell leaves a hand's breadth beside it) and 2 blocks of headroom. The ring
 * of cells round it (see {@link Level2Section}) carries the pipe bundle, the light panels, the
 * painted band, the wall fans and spinning beacons, all turning round the lane together
 * ({@link Level2Twist}). Supplies (a backpack or a crate) sit at the foot of a wall now and then,
 * and a checkpoint radio is set into the wall every 250-350 blocks. Every 24-48 blocks the
 * corridor changes character (a valve manifold, a bulkhead ring, a leak, a dark stretch).
 *
 * <p><b>Anomalies</b> ({@link Level2AnomalyPlan}, {@link Level2Oddities}): brownout stretches where
 * the Blackout never ended, and the quiet oddities in the walls. The light panels also hang on
 * series circuits that fail a whole section at a time (client side, see client/light/FixturePower).
 *
 * <p><b>Exit.</b> The EXIT bulkhead takes you (and anyone standing next to you) on to Level 4.
 */
public final class Level2Layout extends LevelLayout {
    // ---- What one column is --------------------------------------------------------------------
    /** Solid concrete (everything off the corridor). */
    static final int ROCK = 0;
    /** The lane (and the exit recess): where you walk. */
    static final int OPEN = 1;
    /** A wall cell beside the lane, full of dressing (or the exit door). */
    static final int CHASE = 2;

    /**
     * The base class wants an exit locator. Level 2's real exits are the recesses along each
     * corridor ({@link #exitPosition}); this one only picks where on Level 4 you arrive after using one.
     */
    private static final ExitLocator EXITS = new ExitLocator(320, 40, 3000);

    /** The anomaly kinds plus the segment and exit names /backrooms anomaly locate can find. */
    private static final List<String> LOCATE_KINDS;

    static {
        List<String> kinds = new ArrayList<>(Level2Sites.KINDS);
        kinds.addAll(Level2Finder.KINDS);
        LOCATE_KINDS = Collections.unmodifiableList(kinds);
    }

    public Level2Layout() {
        super(4, EXITS);
    }

    /**
     * Arrivals from another level must start at least 500 blocks from an exit (the user's rule for
     * this level). preferredArrival already lands halfway between two exits (545+ blocks), so
     * Travel's push-away check only ever confirms it.
     */
    @Override
    public int arrivalExitClearance() {
        return 500;
    }

    // ==========================================================================================
    // Public queries
    // ==========================================================================================

    @Override
    public boolean isWalkable(long seed, int x, int z) {
        return column(seed, x, z) == OPEN;
    }

    @Override
    public boolean isSafeArrival(long seed, int x, int z) {
        return column(seed, x, z) == OPEN;
    }

    /** The lane is one block wide: no spot in it has four open neighbours. */
    @Override
    public boolean arrivalNeedsSpace() {
        return false;
    }

    /**
     * Searches for an arrival start halfway between two exits of the nearest corridor
     * ({@link Level2Line#arrivalX}), so even the fallback lands far from any exit.
     */
    @Override
    public int[] arrivalHint(long seed, int x, int z) {
        int band = Level2Line.band(z);
        return new int[] {Level2Line.arrivalX(seed, band, x), Level2Line.centre(seed, band)};
    }

    /**
     * Every arrival lands on the centre line of the nearest corridor, halfway between two exits
     * (within {@link Level2Line#ARRIVAL_SPREAD} blocks): at least 500 blocks from the nearest exit,
     * either way along the corridor.
     */
    @Override
    public BlockPos preferredArrival(long seed, int x, int z, @Nullable BackroomsLevel from) {
        int band = Level2Line.band(z);
        int c = Level2Line.centre(seed, band);
        int ax = Level2Line.arrivalX(seed, band, x);
        return new BlockPos(ax, FLOOR_Y + 1, c);
    }

    /**
     * The spot right in front of the nearest exit door on your own corridor (the floor of its
     * recess, one block from the bulkhead): the one /backrooms locate, the exit's sound, the
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
     * like {@code level_2/seg/valves} or {@code level_2/exit}) the nearest segment or exit of that
     * kind, so the same command finds them.
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

    // ==========================================================================================
    // Columns
    // ==========================================================================================

    /** What column (x, z) is (see the codes at the top). */
    int column(long seed, int x, int z) {
        if (Level2Line.outsideReach(z)) {
            return ROCK;
        }
        int band = Level2Line.band(z);
        int dz = z - Level2Line.centre(seed, band);
        int a = Math.abs(dz);
        if (a == 0) {
            return OPEN;
        }
        if (a <= Level2Line.BAY_BACK && Level2Line.feature(seed, band, x, dz < 0 ? -1 : 1) == Level2Line.F_BAY) {
            return a < Level2Line.BAY_BACK ? OPEN : CHASE; // the exit recess, the bulkhead at its back
        }
        return a == Level2Line.WALL ? CHASE : ROCK;
    }

    /** True if a prop (light panel, fan, beacon, supply, radio) belongs at x: the oddities keep clear of them. */
    static boolean propAt(long seed, int band, int x) {
        return Level2Line.lampAt(seed, band, x) || Level2Line.fanAt(seed, band, x) || Level2Line.beaconAt(seed, band, x)
                || Level2Line.supplyAt(seed, band, x) || Level2Line.radioAt(seed, band, x);
    }

    // ==========================================================================================
    // Blocks
    // ==========================================================================================

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        if (Level2Line.outsideReach(z)) {
            fillRock(out);
            return;
        }
        int band = Level2Line.band(z);
        int dz = z - Level2Line.centre(seed, band);
        int a = Math.abs(dz);
        int side = dz < 0 ? -1 : 1;
        if (a > Level2Line.BAY_BACK) {
            fillRock(out);
            return;
        }
        int floor = floorY();
        out.set(floor - 2, ModBlocks.PIPE_FLOOR.get().defaultBlockState()); // the shallow trench under the grating
        out.set(floor - 1, ModBlocks.PIPE_WALL.get().defaultBlockState());
        if (a > 0 && Level2Line.feature(seed, band, x, side) == Level2Line.F_BAY) {
            fillRecess(a, side, out);
            return;
        }
        if (a > Level2Line.WALL) {
            fillRock(out);
            return;
        }
        AnomalySite site = Level2AnomalyPlan.at(seed, x, z); // null almost everywhere
        AnomalySite brownout = site != null && site.kind().equals(Level2Sites.BROWNOUT) ? site : null;
        int n = Level2Line.segment(seed, band, x);
        int type = Level2Line.segType(seed, band, n);
        out.set(ceilingY(), ModBlocks.PIPE_CEILING.get().defaultBlockState());
        out.set(ceilingY() - 1, ModBlocks.PIPE_CEILING.get().defaultBlockState()); // solid over the ring
        if (a == 0) {
            fillLane(seed, band, n, type, x, z, brownout, site, out);
        } else {
            fillWall(seed, band, n, type, x, z, side, brownout, site, out);
        }
        Level2Oddities.decorate(this, seed, x, z, site, out);
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

    // ---- The lane --------------------------------------------------------------------------------

    /**
     * The lane (dz 0): its floor (concrete, the painted band, or a grating with the bundle under
     * it), its two clear blocks (only paint, water and battery lamps, nothing you could bump into)
     * and its ceiling cell at y43 (concrete, the bundle hung under it, a light panel, a beacon or
     * the band).
     */
    private void fillLane(long seed, int band, int n, int type, int x, int z, @Nullable AnomalySite brownout,
                          @Nullable AnomalySite site, Column out) {
        int floor = floorY();
        int top = floor + 3;
        double h = Level2Section.ring(seed, band, x);
        int lamp = Level2Line.lampAt(seed, band, x) ? Level2Section.lampSlot(h) : -1;
        int stripe = Level2Section.stripeSlot(h);
        fillFloor(h, Level2Section.FLOOR, stripe, x, Direction.NORTH, out);
        int bare = Level2Section.bareSide(h);
        Direction toBare = Level2Line.inwardOf(bare);
        if (type == Level2Line.SEG_BULKHEAD && Level2Line.frameX(seed, band, n) == x) {
            out.set(top, Level2Parts.frame(Direction.NORTH, Part.TOP));
            // An arrow on the inside of the ring (on its north post), pointing the way to the nearest exit.
            boolean east = Level2Line.exitX(seed, band, Level2Line.nearestExit(seed, band, x)) > x;
            out.set(floor + 2, Level2Parts.stencil(Direction.SOUTH, east ? Variant.ARROW_RIGHT : Variant.ARROW_LEFT));
            return;
        }
        int ceiling = Level2Section.CEILING;
        if (Level2Section.bundle(h, ceiling)) {
            out.set(top, hungPiece(seed, band, x, 0, ceiling));
        } else if (ceiling == lamp) {
            out.set(top, Level2Parts.lightPanel(Direction.DOWN, lampDead(seed, band, n, type, x, z, brownout)));
        } else if (Level2Line.beaconAt(seed, band, x) && Level2Section.beaconSlot(h, lamp) == ceiling) {
            out.set(top, Level2SpinBlocks.beacon(Direction.DOWN));
        } else if (ceiling == stripe) {
            out.set(top, Level2Parts.paintedBand(Direction.DOWN));
        } else {
            out.set(top, ModBlocks.PIPE_CEILING.get().defaultBlockState());
        }
        // In the lane itself only things with no collision: water, ooze, paint, battery lamps.
        if (leakAt(seed, band, type, x)) {
            out.set(floor + 2, Level2Blocks.DRIP.get().defaultBlockState()); // hanging just under the ceiling
        }
        if (leakAt(seed, band, type, x) || leakAt(seed, band, type, x - 1) || leakAt(seed, band, type, x + 1)) {
            out.set(floor + 1, Level2Parts.puddle());
        }
        if (type == Level2Line.SEG_LEAK) {
            int mid = Level2Line.segMid(seed, band, n);
            if (Math.abs(x - mid) <= 2) {
                out.set(floor + 1, ModBlocks.L2_BLACK_OOZE.get().defaultBlockState());
                if (((x - mid) & 1) == 0) {
                    out.set(floor + 2, Level2Blocks.DRIP.get().defaultBlockState());
                }
            } else if (x == mid + JET_OFFSET + 1) {
                out.set(floor + 2, Level2Parts.stencil(toBare, Variant.STEAM)); // beside the steam jet in the wall
            }
        }
        if (brownout != null) {
            int burst = Level2AnomalyPlan.burstX(seed, brownout, x);
            if (burst != Integer.MIN_VALUE && Math.abs(x - burst) <= 2) {
                out.set(floor + 1, ModBlocks.L2_BLACK_OOZE.get().defaultBlockState());
            }
            if (Level2Line.lampAt(seed, band, x) && Level2AnomalyPlan.emergencyLight(seed, x, z)) {
                out.set(floor + 2, Level2Parts.emergencyLight(toBare)); // the violet battery light on the bare wall
            }
        }
        if (type == Level2Line.SEG_BULKHEAD) {
            int frame = Level2Line.frameX(seed, band, n);
            if (frame != Integer.MIN_VALUE && x == frame + emergencyOffset(seed, band, n)) {
                // One red battery lamp on the wall beside the ring, dark until this section's circuit dies.
                out.set(floor + 2, Level2LightBlocks.emergency(toBare, true, false));
            }
        }
    }

    /**
     * A floor cell (the lane's, or a wall's floor corner): a grating with a pipe under it where the
     * bundle runs under the floor, else concrete (or, in the lane, the painted band as a floor line).
     */
    private void fillFloor(double h, int cell, int stripe, int x, Direction line, Column out) {
        int floor = floorY();
        if (Level2Section.bundle(h, cell)) {
            out.set(floor, Level2Parts.grating(Direction.Axis.X));
            out.set(floor - 1, cell == Level2Section.FLOOR
                    ? Level2Parts.pipe(Direction.Axis.X, Direction.DOWN, Kind.INSULATED, Size.LARGE, Math.floorMod(x, 4) == 0)
                    : Level2Parts.pipe(Direction.Axis.X, Direction.DOWN, Kind.STEEL, Size.MEDIUM, Math.floorMod(x + 1, 4) == 0));
        } else if (cell == stripe) {
            out.set(floor, Level2Parts.floorLine(line));
        } else {
            out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        }
    }

    // ---- The walls -------------------------------------------------------------------------------

    /**
     * A wall column (dz -1 or +1): its floor corner, its two wall cells right beside the lane and
     * its top corner. Each cell is part of the pipe bundle, or bare concrete with what the helix
     * puts there (a light panel, a fan, a beacon, the painted band), or a supply or a checkpoint
     * radio at the wall's foot; a valve manifold or a bulkhead ring's post overrides them all.
     */
    private void fillWall(long seed, int band, int n, int type, int x, int z, int side, @Nullable AnomalySite brownout,
                          @Nullable AnomalySite site, Column out) {
        int floor = floorY();
        Direction in = Level2Line.inwardOf(side);
        double h = Level2Section.ring(seed, band, x);
        fillFloor(h, Level2Section.slot(side, 0), -1, x, in, out);
        if (type == Level2Line.SEG_BULKHEAD && Level2Line.frameX(seed, band, n) == x) {
            out.set(floor + 1, Level2Parts.frame(in, Part.SIDE));
            out.set(floor + 2, Level2Parts.frame(in, Part.SIDE));
            out.set(floor + 3, Level2Parts.frame(in, Part.CORNER));
            return;
        }
        if (manifoldAt(seed, band, x, side)) {
            fillManifold(seed, band, n, x, side, site, out);
            return;
        }
        int lamp = Level2Line.lampAt(seed, band, x) ? Level2Section.lampSlot(h) : -1;
        int stripe = Level2Section.stripeSlot(h);
        int beacon = Level2Line.beaconAt(seed, band, x) ? Level2Section.beaconSlot(h, lamp) : -1;
        int fan = Level2Line.fanAt(seed, band, x) ? Level2Section.fanSlot(h, lamp) : -1;
        // A supply or radio stands at the foot of a wall only under a bare cell, so the row above
        // keeps the lane tight beside it (they leave room at the foot).
        int foot = Level2Section.footSlot(h, -1);
        boolean footFree = foot >= 0 && !Level2Section.bundle(h, foot + (foot == 0 ? 1 : -1));
        boolean radio = footFree && Level2Line.radioAt(seed, band, x);
        boolean supply = footFree && !radio && Level2Line.supplyAt(seed, band, x);
        for (int row = 1; row <= 3; row++) {
            int i = Level2Section.slot(side, row);
            BlockState state;
            if (Level2Section.bundle(h, i)) {
                state = bundlePiece(seed, band, x, side, row, i);
            } else if (i == foot && radio) {
                state = ModBlocks.CHECKPOINT_RADIO.get().defaultBlockState().setValue(WallMountBlock.FACING, in);
            } else if (i == foot && supply) {
                long hs = Hash.of(seed, band, x, 552);
                state = (Hash.unit(hs) < BACKPACK_SHARE ? ModBlocks.BACKPACK : ModBlocks.SUPPLY_CRATE).get().defaultBlockState()
                        .setValue(SupplyContainerBlock.FACING, in);
            } else if (i == lamp) {
                state = Level2Parts.lightPanel(in, lampDead(seed, band, n, type, x, z, brownout));
            } else if (i == beacon) {
                state = Level2SpinBlocks.beacon(in);
            } else if (i == fan) {
                state = Level2SpinBlocks.fan(in);
            } else if (i == stripe) {
                state = Level2Parts.paintedBand(in);
            } else {
                state = row == 1 ? Level2Blocks.WALL_BASE.get().defaultBlockState() : ModBlocks.PIPE_WALL.get().defaultBlockState();
            }
            out.set(floor + row, state);
        }
        if (type == Level2Line.SEG_LEAK && side == Level2Section.bareSide(h) && x == Level2Line.segMid(seed, band, n) + JET_OFFSET
                && !Level2Section.bundle(h, Level2Section.slot(side, 1))) {
            // The leak's steam jet: a hissing vent set into the bare wall at head height.
            out.set(floor + 2, Level2Parts.steamVent(in));
        }
    }

    /** Share of the supply spots that hold a backpack (the rest a crate). */
    private static final double BACKPACK_SHARE = 0.65;
    /** The leak segment's steam jet stands this far east of the segment's middle. */
    private static final int JET_OFFSET = 6;

    /** True along a valve manifold: on its side of a valve segment, clear of the segment's two ends. */
    static boolean manifoldAt(long seed, int band, int x, int side) {
        int n = Level2Line.segment(seed, band, x);
        return Level2Line.segType(seed, band, n) == Level2Line.SEG_VALVES && side == Level2Line.featureSide(seed, band, n)
                && x > Level2Line.segStart(seed, band, n) + 1 && x < Level2Line.segEnd(seed, band, n) - 1;
    }

    /**
     * A valve manifold column in the wall: from the header hung in the top corner a branch drops
     * into a valve every third column, a gauge between, the label in the middle, over plain
     * concrete (so the row at your feet keeps the lane tight). In a wrong_valves oddity every
     * quarter-turn valve is shut.
     */
    private void fillManifold(long seed, int band, int n, int x, int side, @Nullable AnomalySite site, Column out) {
        int floor = floorY();
        Direction in = Level2Line.inwardOf(side);
        Kind header = headerKind(seed, band, n);
        int start = Level2Line.segStart(seed, band, n);
        int end = Level2Line.segEnd(seed, band, n);
        boolean wrong = site != null && site.kind().equals(Level2Sites.WRONG_VALVES);
        out.set(floor + 1, Level2Blocks.WALL_BASE.get().defaultBlockState());
        int r = Math.floorMod(x - start, 3);
        if (x == Level2Line.segMid(seed, band, n)) {
            out.set(floor + 2, Level2Parts.stencil(in, LABELS[Hash.range(Hash.of(seed, band, n, 517), LABELS.length)]));
        } else if (r == 0) {
            long hv = Hash.of(seed, band, x, 533);
            out.set(floor + 2, wrong || header != Kind.PAINTED_RED && Hash.unit(hv) < 0.45
                    ? Level2Parts.leverValve(in, !wrong && Hash.unit(Hash.mix(hv)) < 0.8, true)
                    : Level2Parts.valve(in, header == Kind.PAINTED_RED, true));
        } else if (r == 1) {
            out.set(floor + 2, Level2Parts.gauge(in));
        } else {
            out.set(floor + 2, ModBlocks.PIPE_WALL.get().defaultBlockState());
        }
        // The header: a fatter pair in the colour of what it carries, a flange at each end of the run.
        boolean flange = x == start + 2 || x == end - 2 || Math.floorMod(x + 1, 5) == 0;
        out.set(floor + 3, Level2Parts.hung(Direction.Axis.X, header, Size.MEDIUM, flange, false));
    }

    // ---- The exit recess -------------------------------------------------------------------------

    /**
     * The exit recess, as wide as the lane (|dz| 1-2 at the exit's x): concrete all round, the low
     * ceiling carried on in, an amber battery lamp on its side wall that never goes out, and at its
     * back (|dz| 3) the EXIT bulkhead under the lit EXIT sign.
     */
    private void fillRecess(int a, int side, Column out) {
        int floor = floorY();
        Direction in = Level2Line.inwardOf(side);
        if (a == Level2Line.BAY_BACK) {
            fillRock(out);
            BlockState door = Level2Blocks.EXIT_BULKHEAD.get().defaultBlockState().setValue(Level2ExitBulkheadBlock.FACING, in);
            out.set(floor + 1, door.setValue(ExitDoorBlock.HALF, DoubleBlockHalf.LOWER));
            out.set(floor + 2, door.setValue(ExitDoorBlock.HALF, DoubleBlockHalf.UPPER));
            out.set(floor + 3, ModBlocks.EXIT_SIGN.get().defaultBlockState());
            return;
        }
        out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        for (int y = floor + 3; y <= ceilingY(); y++) {
            out.set(y, ModBlocks.PIPE_CEILING.get().defaultBlockState());
        }
        if (a == Level2Line.BAY_BACK - 1) {
            out.set(floor + 2, Level2LightBlocks.emergency(Direction.EAST, false, true)); // on the recess's west wall
        }
    }

    // ---- The bundle's pieces ---------------------------------------------------------------------

    /** True if the wall on {@code side} carries the bundle in ring cell i at x (not a manifold, ring post or exit). */
    private static boolean bundleAt(long seed, int band, int x, int side, int i) {
        if (!Level2Section.bundle(Level2Section.ring(seed, band, x), i) || manifoldAt(seed, band, x, side)
                || Level2Line.feature(seed, band, x, side) != Level2Line.F_NONE) {
            return false;
        }
        int n = Level2Line.segment(seed, band, x);
        return !(Level2Line.segType(seed, band, n) == Level2Line.SEG_BULKHEAD && Level2Line.frameX(seed, band, n) == x);
    }

    /**
     * One piece of the bundle in a wall cell, against the far side (the concrete behind), so the
     * lane keeps its full block and only gains a hand's breadth beside it: the pale main low, a fat
     * pipe at head height (where a run starts or stops its capped end shows), and a pair hung in
     * the top corner on a steel channel every few blocks, bending up into the slab where it stops.
     */
    private static BlockState bundlePiece(long seed, int band, int x, int side, int row, int i) {
        Direction in = Level2Line.inwardOf(side);
        Direction wall = Level2Line.wallOf(side);
        boolean flange = Math.floorMod(x + 2 * row, 5) == 0;
        if (row == 3) {
            Kind kind = side < 0 ? servicePaint(seed, band) : Kind.STEEL;
            boolean west = bundleAt(seed, band, x - 1, side, i);
            boolean east = bundleAt(seed, band, x + 1, side, i);
            if (west != east) {
                return Level2Parts.hungEnd(Direction.NORTH, west ? Direction.WEST : Direction.EAST, kind, Size.MEDIUM);
            }
            boolean bracket = Level2Line.bracketAt(seed, band, x);
            return Level2Parts.hung(Direction.Axis.X, kind, Size.MEDIUM, flange && !bracket, bracket);
        }
        if (row == 1) {
            return Level2Parts.duct(in, Math.floorMod(x + 3 * side, 6) == 0);
        }
        Kind kind = HEAD_KINDS[Hash.range(Hash.of(seed, 2 * band + (side < 0 ? 0 : 1), 0, 519), HEAD_KINDS.length)];
        return Level2Parts.pipe(Direction.Axis.X, wall, kind, Size.LARGE, flange);
    }

    /**
     * The bundle hung under the slab over the lane (ring cell i): the cable tray, on a steel channel
     * bracket every few blocks; where the run stops it ends.
     */
    private static BlockState hungPiece(long seed, int band, int x, int dz, int i) {
        boolean bracket = Level2Line.bracketAt(seed, band, x);
        return Level2Parts.cableTray(Direction.Axis.X, bracket, !bracket && droopAt(seed, band, x));
    }

    /** A dead tube: in a dark segment's dark run (all but one), or in a brownout (most). */
    private static boolean lampDead(long seed, int band, int n, int type, int x, int z, @Nullable AnomalySite brownout) {
        if (brownout != null && !Level2AnomalyPlan.lampSurvives(seed, x, z)) {
            return true;
        }
        return type == Level2Line.SEG_DARK && x >= Level2Line.darkFrom(seed, band, n) && x <= Level2Line.darkTo(seed, band, n)
                && x != Level2Line.survivorX(seed, band, n);
    }

    /** The colour of a valve segment's header, matching the label stencilled on the manifold. */
    private static Kind headerKind(long seed, int band, int n) {
        return HEADERS[Hash.range(Hash.of(seed, band, n, 517), HEADERS.length)];
    }

    // ---- Leaks, the bulkhead's battery lamp ------------------------------------------------------

    /** About one tray block in 16 (never at a bracket) has a loop of loose cable sagging out of it. */
    private static boolean droopAt(long seed, int band, int x) {
        return Hash.unit(seed, band, x, 531) < 0.06;
    }

    /** Leaks are picked per stretch of this many blocks of corridor ... */
    private static final int LEAK_CELL = 16;
    /** ... with this chance of a candidate ... */
    private static final double LEAK_CHANCE = 0.6;
    /** ... which leaks for sure where the concrete is damp (Level2Damp), else only this often. */
    private static final double DRY_LEAK_CHANCE = 0.25;

    /**
     * True if a joint overhead weeps at x: a drip hangs over the lane and a puddle spreads on its
     * floor. Leaks gather where the concrete is damp (Level2Damp), and keep clear of the lamps and
     * the segments that already have water of their own (the leak segment) or a ring.
     */
    private static boolean leakAt(long seed, int band, int type, int x) {
        int cell = Math.floorDiv(x, LEAK_CELL);
        long h = Hash.of(seed, band, cell, 530);
        if (Hash.unit(h) >= LEAK_CHANCE) {
            return false;
        }
        int lx = cell * LEAK_CELL + 2 + Hash.range(Hash.mix(h), LEAK_CELL - 4);
        if (x != lx || Level2Line.lampAt(seed, band, x)) {
            return false;
        }
        int t = Level2Line.segType(seed, band, Level2Line.segment(seed, band, x));
        if (t == Level2Line.SEG_LEAK || t == Level2Line.SEG_BULKHEAD || t == Level2Line.SEG_EXIT) {
            return false;
        }
        return Level2Damp.isDamp(seed, x, Level2Line.centre(seed, band)) || Hash.unit(Hash.mix(h ^ 5)) < DRY_LEAK_CHANCE;
    }

    /** Which side of a bulkhead ring (-1 west, +1 east) its battery lamp hangs on. */
    private static int emergencyOffset(long seed, int band, int n) {
        return (Hash.of(seed, band, n, 532) & 1) == 0 ? -1 : 1;
    }

    private static final Variant[] LABELS = {Variant.STEAM, Variant.RETURN, Variant.H2O};
    /** A manifold header's paint for each label above: red steam, grey return, green water. */
    private static final Kind[] HEADERS = {Kind.PAINTED_RED, Kind.PAINTED_BLUE, Kind.PAINTED_GREEN};
    /** The fat pipe at head height in each wall (one kind per corridor and wall). */
    private static final Kind[] HEAD_KINDS = {Kind.STEEL, Kind.STEEL, Kind.COPPER, Kind.PAINTED_RED, Kind.PAINTED_BLUE,
            Kind.PAINTED_GREEN};

    private static final Kind[] PAINTS = {Kind.PAINTED_RED, Kind.PAINTED_BLUE, Kind.PAINTED_YELLOW, Kind.PAINTED_GREEN};

    /** The painted service line in the bundle (one colour per corridor). */
    private static Kind servicePaint(long seed, int band) {
        return PAINTS[Hash.range(Hash.of(seed, band, 6, 518), PAINTS.length)];
    }
}
