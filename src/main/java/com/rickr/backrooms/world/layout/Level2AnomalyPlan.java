package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.CeilingTubeBlock;
import com.rickr.backrooms.block.HouseDoorBlock;
import com.rickr.backrooms.block.LampMode;
import com.rickr.backrooms.block.Level2PipeBlock;
import com.rickr.backrooms.block.Level2StencilBlock;
import com.rickr.backrooms.block.OfficePanelBlock;
import com.rickr.backrooms.block.PipeRackBlock;
import com.rickr.backrooms.block.WallLampBlock;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level2.Level2Sites;
import com.rickr.backrooms.world.anomaly.level2.Level2Sites.Frame;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * Where Level 2's anomalous areas are along the corridors, and their blocks. What they DO lives
 * in world/anomaly/level2 (server) and client/anomaly/level2; their shared shapes in
 * {@link Level2Sites}. Everything here is a pure function of (seed, x, z), like the rest of
 * {@link Level2Layout}.
 *
 * <p><b>Placement.</b> Each corridor is cut into stretches {@link #CELL} blocks long (the same
 * stretches on every band). A stretch holds at most one site, wholly inside it. Like the squares
 * of a chessboard, stretches take turns between two jobs, and the job flips from one band to the
 * next:
 * <ul>
 *   <li>"big" stretches: about 40% a <b>brownout</b> (30-60 blocks of corridor where the Blackout
 *       never ended), about 28% each a <b>void door</b> or a <b>loop door</b> in a side wall;</li>
 *   <li>"hot" stretches: about two in five turn one of their two side rooms into the <b>hot zone</b>'s
 *       machinery room.</li>
 * </ul>
 * The void's big square reaches across the corridor into the rock towards the next band, which is
 * why the jobs flip like a chessboard: the stretch beside it on the next band is a "hot" one, and a
 * hot room is only placed where it stays clear of it. No site comes near an exit bay.
 *
 * <p><b>Doors</b> (frame: depth from the door into the rock, lat to its right; see {@link Frame}).
 * A void or loop door stands at the back of a small hall off the corridor (depth -3..-1, lat -1..4),
 * entered from the corridor at lat 3..4, off to one side: looking back out through the door you only
 * ever see the hall's own walls. The hall and the door are copied exactly, either 30 blocks below (the
 * void: a 64-block square of black that wraps around, floor y10, sky y39) or 6 blocks above (the
 * looping hallway over the corridor ceilings). The server swaps you between the original and the copy
 * as you walk through the passage behind the door, so nothing on screen jumps.
 */
final class Level2AnomalyPlan {
    /** Length (blocks along X) of the stretches sites are planned in: two side-room slots. */
    static final int CELL = 2 * Level2Line.ROOM_CELL;
    /** Void and loop doors stand this far from the centre line (the far wall of their little hall). */
    static final int DOOR_DEPTH = 8;
    /** Sites keep this far (along the corridor) from an exit door. */
    private static final int EXIT_GAP = 24;
    /** Out of 1: share of the "hot" stretches that really get a hot zone. */
    private static final double HOT_CHANCE = 0.4;

    /** Out of 1: share of the bulkhead lamps in a brownout that still work. */
    private static final double BROWNOUT_LAMP_SURVIVES = 0.15;
    /** Out of 1: share of the emergency-light spots in a brownout that have one (most: they are its only light). */
    static final double EMERGENCY_LIGHT_CHANCE = 0.9;
    /** Out of 1: share of a brownout's 12-block stretches with a burst pipe and a pool of ooze. */
    private static final double BURST_CHANCE = 0.55;
    private static final int BURST_STRETCH = 12;

    private Level2AnomalyPlan() {}

    // ==========================================================================================
    // Queries
    // ==========================================================================================

    /** The site covering column (x, z), or null. */
    @Nullable
    static AnomalySite at(long seed, int x, int z) {
        int band = Level2Line.band(z);
        int cell = Math.floorDiv(x, CELL);
        // A void square can reach into the next band's rock, so look at both neighbours too.
        for (int b = band - 1; b <= band + 1; b++) {
            AnomalySite site = cellSite(seed, b, cell);
            if (site != null && site.contains(x, z)) {
                return site;
            }
        }
        return null;
    }

    static void near(long seed, int x, int z, int radius, Consumer<AnomalySite> out) {
        int reach = radius + Level2Line.BAND;
        for (int b = Level2Line.band(z - reach); b <= Level2Line.band(z + reach); b++) {
            for (int cell = Math.floorDiv(x - radius, CELL); cell <= Math.floorDiv(x + radius, CELL); cell++) {
                AnomalySite site = cellSite(seed, b, cell);
                if (site != null && site.near(x, z, radius)) {
                    out.accept(site);
                }
            }
        }
    }

    /** The nearest site of {@code kind} within about a thousand blocks, or null (for /backrooms anomaly locate). */
    @Nullable
    static AnomalySite nearest(long seed, int x, int z, String kind) {
        if (!Level2Sites.KINDS.contains(kind)) {
            return null;
        }
        int band = Level2Line.band(z);
        int cell = Math.floorDiv(x, CELL);
        AnomalySite best = null;
        double bestDist = Double.MAX_VALUE;
        for (int b = band - 10; b <= band + 10; b++) {
            for (int c = cell - 6; c <= cell + 6; c++) {
                AnomalySite site = cellSite(seed, b, c);
                if (site != null && site.kind().equals(kind)) {
                    double d = site.distanceTo(x, z);
                    if (d < bestDist) {
                        bestDist = d;
                        best = site;
                    }
                }
            }
        }
        return best;
    }

    static boolean isDoor(AnomalySite site) {
        return site.kind().equals(Level2Sites.VOID_DOOR) || site.kind().equals(Level2Sites.LOOP_HALL);
    }

    /** The corridor wall a door site (or hot room) opens off: -1 north, +1 south. */
    static int doorSide(AnomalySite site) {
        return Direction.from2DDataValue(site.facing()) == Direction.NORTH ? -1 : 1;
    }

    /** True if a void door's square on this band covers any of x0..x1 (where the corridor runs over the void). */
    static boolean voidOver(long seed, int band, int x0, int x1) {
        for (int cell = Math.floorDiv(x0, CELL); cell <= Math.floorDiv(x1, CELL); cell++) {
            AnomalySite site = cellSite(seed, band, cell);
            if (site != null && site.kind().equals(Level2Sites.VOID_DOOR) && site.maxX() >= x0 && site.minX() <= x1) {
                return true;
            }
        }
        return false;
    }

    /** True if x is one of the two columns where a door's little hall opens onto the corridor. */
    static boolean isMouth(AnomalySite site, int x) {
        Frame f = frameOf(site);
        return x == f.x(-4, 3) || x == f.x(-4, 4);
    }

    // ==========================================================================================
    // Remembered sites
    // ==========================================================================================

    private static final int MEMO = 64;

    private static final class SiteMemo {
        final long[] seed = new long[MEMO];
        final int[] band = new int[MEMO];
        final int[] cell = new int[MEMO];
        final boolean[] filled = new boolean[MEMO];
        final AnomalySite[] site = new AnomalySite[MEMO];
    }

    private static final ThreadLocal<SiteMemo> SITES = ThreadLocal.withInitial(SiteMemo::new);

    /** The site of stretch {@code cell} of band {@code band}, or null. Remembered per thread. */
    @Nullable
    static AnomalySite cellSite(long seed, int band, int cell) {
        SiteMemo memo = SITES.get();
        int i = (int) (Hash.mix(band * 0x632BE59BD9B4E019L + cell) & (MEMO - 1));
        if (memo.filled[i] && memo.seed[i] == seed && memo.band[i] == band && memo.cell[i] == cell) {
            return memo.site[i];
        }
        AnomalySite site = ((band + cell) & 1) == 0 ? planBig(seed, band, cell) : planHot(seed, band, cell);
        memo.seed[i] = seed;
        memo.band[i] = band;
        memo.cell[i] = cell;
        memo.site[i] = site;
        memo.filled[i] = true;
        return site;
    }

    // ==========================================================================================
    // Planners (pure and cheap: only the corridor plan's own hash functions)
    // ==========================================================================================

    @Nullable
    private static AnomalySite planBig(long seed, int band, int cell) {
        long h = Hash.of(seed, band, cell, 900);
        double roll = Hash.unit(h);
        String kind = roll < 0.40 ? Level2Sites.BROWNOUT
                : roll < 0.68 ? Level2Sites.VOID_DOOR
                : roll < 0.96 ? Level2Sites.LOOP_HALL
                : null;
        if (kind == null) {
            return null;
        }
        int base = cell * CELL;
        int c = Level2Line.centre(seed, band);
        // A few tries at different spots; they only fail near an exit bay.
        for (int k = 1; k <= 6; k++) {
            long hk = Hash.mix(h + k * 0x9E3779B97F4A7C15L);
            AnomalySite site = kind.equals(Level2Sites.BROWNOUT)
                    ? planBrownout(seed, band, base, c, hk)
                    : planDoor(seed, band, base, c, hk, kind);
            if (site != null) {
                return site;
            }
        }
        return null;
    }

    /** 30-60 blocks of the corridor itself (its full width, chases included). */
    @Nullable
    private static AnomalySite planBrownout(long seed, int band, int base, int c, long hash) {
        int length = 30 + Hash.range(hash, 31);
        int x0 = base + 16 + Hash.range(Hash.mix(hash ^ 1), CELL - 32 - length + 1);
        int x1 = x0 + length - 1;
        for (int x = x0; x <= x1; x += 8) {
            if (Level2Line.exitNear(seed, band, x, 16)) {
                return null;
            }
        }
        if (Level2Line.exitNear(seed, band, x1, 16)) {
            return null;
        }
        return new AnomalySite(Level2Sites.BROWNOUT, x0, c - Level2Line.CHASE, x1, c + Level2Line.CHASE,
                (x0 + x1) >> 1, c, 0, hash);
    }

    /**
     * A void or loop door at the back of its little hall off one side of the corridor, placed so
     * the void (65 blocks across) or the hallway (129 long) fits in the stretch.
     */
    @Nullable
    private static AnomalySite planDoor(long seed, int band, int base, int c, long hash, String kind) {
        boolean isVoid = kind.equals(Level2Sites.VOID_DOOR);
        int lHalf = isVoid ? Level2Sites.VOID_HALF : Level2Sites.LOOP_HALF + 1;
        int doorX = base + lHalf + Hash.range(hash, CELL - 2 * lHalf - 1);
        if (Level2Line.exitNear(seed, band, doorX, EXIT_GAP)) {
            return null;
        }
        int side = Hash.unit(Hash.mix(hash ^ 2)) < 0.5 ? -1 : 1;
        Frame door = new Frame(doorX, c + side * DOOR_DEPTH, Level2Line.wallOf(side));
        int dMin = isVoid ? -Level2Sites.VOID_HALF : -(Level2Sites.STUB_HALF + 1);
        int dMax = isVoid ? Level2Sites.VOID_HALF : 6;
        int x0 = door.x(dMin, -lHalf);
        int z0 = door.z(dMin, -lHalf);
        int x1 = door.x(dMax, lHalf);
        int z1 = door.z(dMax, lHalf);
        return new AnomalySite(kind, Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1),
                door.ax(), door.az(), door.facing().get2DDataValue(), hash);
    }

    /**
     * One of the stretch's two side rooms becomes the hot zone: a machinery room 3 wide between
     * machines, opening straight off the corridor. It must stay clear of exit bays and of the
     * void squares of the bands next door.
     */
    @Nullable
    private static AnomalySite planHot(long seed, int band, int cell) {
        long h = Hash.of(seed, band, cell, 910);
        if (Hash.unit(h) >= HOT_CHANCE) {
            return null;
        }
        int first = Hash.range(Hash.mix(h), 2);
        for (int i = 0; i < 2; i++) {
            int k = 2 * cell + ((first + i) & 1);
            int slot = Level2Line.slotX(seed, band, k);
            int side = Level2Line.slotSide(seed, band, k);
            if (Level2Line.exitNear(seed, band, slot, EXIT_GAP)) {
                continue;
            }
            int c = Level2Line.centre(seed, band);
            Direction f = Level2Line.wallOf(side);
            // Frame origin: the chase column at the right-hand end of the opening (u 0, v 0), so
            // u runs into the rock from the chase and v 0..2 is the 3-wide opening.
            Frame frame = new Frame(slot - f.getClockWise().getStepX(), c + side * Level2Line.CHASE, f);
            // The room with its walls: u 0..10, v -2..4.
            int wx0 = frame.x(0, -2);
            int wz0 = frame.z(0, -2);
            int wx1 = frame.x(Level2Sites.HOT_LENGTH, 4);
            int wz1 = frame.z(Level2Sites.HOT_LENGTH, 4);
            if (bigSiteTouches(seed, band - 1, cell, Math.min(wx0, wx1) - 2, Math.min(wz0, wz1) - 2, Math.max(wx0, wx1) + 2, Math.max(wz0, wz1) + 2)
                    || bigSiteTouches(seed, band + 1, cell, Math.min(wx0, wx1) - 2, Math.min(wz0, wz1) - 2, Math.max(wx0, wx1) + 2, Math.max(wz0, wz1) + 2)) {
                continue;
            }
            // The site itself: the room without its walls (u 0..9, v -1..3); anchor in its middle.
            int sx0 = frame.x(0, -1);
            int sz0 = frame.z(0, -1);
            int sx1 = frame.x(Level2Sites.HOT_LENGTH - 1, 3);
            int sz1 = frame.z(Level2Sites.HOT_LENGTH - 1, 3);
            return new AnomalySite(Level2Sites.HOT_ZONE, Math.min(sx0, sx1), Math.min(sz0, sz1), Math.max(sx0, sx1), Math.max(sz0, sz1),
                    frame.x(5, 1), frame.z(5, 1), f.get2DDataValue(), h ^ k);
        }
        return null;
    }

    /** True if the big site of stretch {@code cell} on band {@code band} touches the rectangle. */
    private static boolean bigSiteTouches(long seed, int band, int cell, int minX, int minZ, int maxX, int maxZ) {
        AnomalySite s = cellSite(seed, band, cell);
        return s != null && s.maxX() >= minX && s.minX() <= maxX && s.maxZ() >= minZ && s.minZ() <= maxZ;
    }

    // ==========================================================================================
    // Columns at the level's own height
    // ==========================================================================================

    /**
     * Column code (see {@link Level2Layout}) of an anomaly's own structure at (x, z), or -1 where
     * no anomaly changes the level there.
     */
    static int column(@Nullable AnomalySite site, int x, int z) {
        if (site == null) {
            return -1;
        }
        if (site.kind().equals(Level2Sites.HOT_ZONE)) {
            Frame f = hotFrameOf(site);
            return hotOpen(f.depth(x, z), f.lat(x, z)) ? Level2Layout.SITE_OPEN : Level2Layout.SITE_SOLID;
        }
        if (!isDoor(site)) {
            return -1; // a brownout only changes lamps and floors (see Level2Layout)
        }
        Frame f = frameOf(site);
        int d = f.depth(x, z);
        int l = f.lat(x, z);
        int absL = Math.abs(l);
        if (d == 0 && l == 0) {
            return Level2Layout.SITE_SOLID; // the door
        }
        if (absL <= Level2Sites.STUB_HALF) {
            if (d == 0) {
                return Level2Layout.ROCK; // plain wall beside the door (no other doors: the copy must match)
            }
            if (d >= -3 && d <= -1) {
                return hallOpen(l) ? Level2Layout.HALL : Level2Layout.ROCK;
            }
        }
        if (site.kind().equals(Level2Sites.VOID_DOOR)) {
            if (d >= 1 && d <= 7 && absL <= 2) {
                return voidPassageOpen(d, l) ? Level2Layout.SITE_OPEN : Level2Layout.SITE_SOLID;
            }
        } else if (d >= 1 && d <= 6 && absL <= 4) {
            return loopOpen(d, l, false) ? Level2Layout.SITE_OPEN : Level2Layout.SITE_SOLID;
        }
        return -1;
    }

    /** The little hall in front of a void/loop door: lat -1..4 (it opens onto the corridor at lat 3..4). */
    private static boolean hallOpen(int lat) {
        return lat >= -1 && lat <= 4;
    }

    /**
     * Hot zone room (u from the corridor's chase into the rock, v across): a 3-wide walkway between
     * the machines along both sides, up to the machines across the back at u 9.
     */
    private static boolean hotOpen(int u, int v) {
        return u <= Level2Sites.HOT_LENGTH - 2 && v >= 0 && v <= 2;
    }

    /** The void door's passage at the level: 3 blocks to a small black chamber that hides its end. */
    private static boolean voidPassageOpen(int d, int l) {
        return (d <= 3 && l == 0) || (d >= 4 && d <= 6 && Math.abs(l) <= 1);
    }

    /**
     * The loop door's passage (d 1..3) and its hallway (d 4..5). At the level the hallway is only
     * a stub (lat -3..3, unseen from the passage); {@code upper} is the real hallway above, with
     * look-alike copies of the passage every LOOP_PERIOD blocks (never reached: you are moved back
     * before you get there).
     */
    private static boolean loopOpen(int d, int l, boolean upper) {
        if (d >= 1 && d <= 3) {
            return l == 0 || (upper && Math.abs(l) == Level2Sites.LOOP_PERIOD);
        }
        return d >= 4 && d <= 5 && Math.abs(l) <= (upper ? Level2Sites.LOOP_HALF : 3);
    }

    /** Blocks of a SITE_OPEN / SITE_SOLID column (also called, shifted, for the copies). */
    static void fillSiteColumn(Level2Layout layout, long seed, @Nullable AnomalySite site, int x, int z, LevelLayout.Column out) {
        if (site == null) {
            return;
        }
        if (site.kind().equals(Level2Sites.HOT_ZONE)) {
            fillHot(layout, seed, site, x, z, out);
            return;
        }
        Frame f = frameOf(site);
        int d = f.depth(x, z);
        int l = f.lat(x, z);
        if (d == 0 && l == 0) {
            fillDoor(layout, site, f, out);
        } else if (site.kind().equals(Level2Sites.VOID_DOOR)) {
            fillVoidPassage(layout, d, l, out);
        } else {
            fillLoop(layout, f, d, l, false, out);
        }
    }

    /**
     * A column of the little hall in front of a void/loop door: bare floor, a conduit along the
     * ceiling and two caged lamps: one on the door wall beside the door, one on the wall facing it
     * (the copy has the same two, so nothing changes when you are swapped over).
     */
    static void fillHall(Level2Layout layout, AnomalySite site, int x, int z, LevelLayout.Column out) {
        int floor = layout.floorY();
        int ceiling = layout.ceilingY();
        out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        out.set(ceiling, ModBlocks.PIPE_CEILING.get().defaultBlockState());
        Frame f = frameOf(site);
        if (f.depth(x, z) == -3 && f.lat(x, z) == 0) {
            // Lights the door from across the hall.
            out.set(floor + 3, ModBlocks.BULKHEAD_LAMP.get().defaultBlockState().setValue(WallLampBlock.FACING, f.facing()));
        }
        Direction lamp = doorLamp(site, x, z);
        if (lamp != null) {
            out.set(ceiling - 1, ModBlocks.BULKHEAD_LAMP.get().defaultBlockState().setValue(WallLampBlock.FACING, lamp));
        } else if (frameOf(site).depth(x, z) == -3) {
            // A thin conduit along the hall's corridor-side wall, hung under the ceiling.
            out.set(ceiling - 1, Level2Parts.pipe(Direction.Axis.X, Level2Line.wallOf(-doorSide(site)),
                    Level2PipeBlock.Kind.CONDUIT, Level2PipeBlock.Size.SMALL, false));
        }
    }

    /** The door, closed, flush with the hall's back wall (its slab on the hall side), wall above. */
    private static void fillDoor(Level2Layout layout, AnomalySite site, Frame f, LevelLayout.Column out) {
        int floor = layout.floorY();
        BlockState wall = ModBlocks.PIPE_WALL.get().defaultBlockState();
        BlockState door = (site.kind().equals(Level2Sites.VOID_DOOR) ? ModBlocks.L2_VOID_DOOR : ModBlocks.OFFICE_DOOR).get()
                .defaultBlockState()
                .setValue(DoorBlock.FACING, f.facing())
                .setValue(HouseDoorBlock.LOCKED, false);
        out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        out.set(floor + 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        out.set(floor + 2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        out.set(floor + 3, wall);
        out.set(floor + 4, wall);
        out.set(layout.ceilingY(), ModBlocks.PIPE_CEILING.get().defaultBlockState());
    }

    /** Matte black all round: the light from the door dies a few blocks in. */
    private static void fillVoidPassage(Level2Layout layout, int d, int l, LevelLayout.Column out) {
        BlockState black = ModBlocks.L2_VOID_BLACK.get().defaultBlockState();
        int floor = layout.floorY();
        int ceiling = layout.ceilingY();
        out.set(ceiling, ModBlocks.PIPE_CEILING.get().defaultBlockState());
        out.set(floor, black);
        if (voidPassageOpen(d, l)) {
            out.set(ceiling - 1, black); // 3 tall inside
        } else {
            for (int y = floor + 1; y < ceiling; y++) {
                out.set(y, black);
            }
        }
    }

    /**
     * The office-like passage and hallway behind a loop door: grey carpet, off-white walls with a
     * skirting, a drop ceiling with a fluorescent panel every 16 blocks, and closed (locked) office
     * doors along the far wall between the lights. Every detail repeats every 16 blocks, which
     * divides the hallway's 32-block loop, so the jump back is invisible. {@code d} 0 (upper only,
     * beyond the copy of the hall) is the wall behind the passage copies, each with a closed door.
     */
    private static void fillLoop(Level2Layout layout, Frame f, int d, int l, boolean upper, LevelLayout.Column out) {
        int floor = layout.floorY();
        int ceiling = layout.ceilingY();
        BlockState carpet = ModBlocks.OFFICE_CARPET.get().defaultBlockState();
        BlockState officeCeiling = ModBlocks.OFFICE_CEILING.get().defaultBlockState();
        BlockState wall = ModBlocks.OFFICE_WALL.get().defaultBlockState();
        out.set(ceiling, ModBlocks.PIPE_CEILING.get().defaultBlockState());
        if (d == 0) {
            // Looks like the real door's column seen from its passage: the door, hall wall above.
            BlockState pipeWall = ModBlocks.PIPE_WALL.get().defaultBlockState();
            out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
            if (Math.abs(l) == Level2Sites.LOOP_PERIOD) {
                BlockState door = ModBlocks.OFFICE_DOOR.get().defaultBlockState()
                        .setValue(DoorBlock.FACING, f.facing())
                        .setValue(HouseDoorBlock.LOCKED, true);
                out.set(floor + 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                out.set(floor + 2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            } else {
                out.set(floor + 1, pipeWall);
                out.set(floor + 2, pipeWall);
            }
            for (int y = floor + 3; y < ceiling; y++) {
                out.set(y, pipeWall);
            }
            return;
        }
        if (loopOpen(d, l, upper)) {
            out.set(floor, carpet);
            if (d <= 3) {
                out.set(ceiling - 1, officeCeiling); // the passage is 3 tall, like the door frame plus a bit
                out.set(ceiling, officeCeiling);
            } else if (Math.floorMod(l, Level2Sites.LOOP_LIGHT_SPACING) == 0) {
                // A 1x2 panel across the hallway: each half points at the other.
                out.set(ceiling, ModBlocks.OFFICE_PANEL.get().defaultBlockState()
                        .setValue(OfficePanelBlock.FACING, d == 4 ? f.facing() : f.facing().getOpposite())
                        .setValue(OfficePanelBlock.MODE, LampMode.LIT));
            } else {
                out.set(ceiling, officeCeiling);
            }
            return;
        }
        out.set(floor, ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        if (upper && d == 6 && Math.abs(l) <= Level2Sites.LOOP_HALF
                && Math.floorMod(l, Level2Sites.LOOP_LIGHT_SPACING) == Level2Sites.LOOP_LIGHT_SPACING / 2) {
            BlockState door = ModBlocks.OFFICE_DOOR.get().defaultBlockState()
                    .setValue(DoorBlock.FACING, f.facing())
                    .setValue(HouseDoorBlock.LOCKED, true);
            out.set(floor + 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            out.set(floor + 2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        } else {
            out.set(floor + 1, ModBlocks.OFFICE_WALL_BASE.get().defaultBlockState());
            out.set(floor + 2, wall);
        }
        for (int y = floor + 3; y < ceiling; y++) {
            out.set(y, wall);
        }
    }

    /**
     * The machinery room off the corridor: machine cabinets two high along both side walls and
     * three high across the back, rusty pipe racks above them with leaking steam vents, drain
     * grates down the middle and one hanging tube light.
     */
    private static void fillHot(Level2Layout layout, long seed, AnomalySite site, int x, int z, LevelLayout.Column out) {
        Frame f = hotFrameOf(site);
        int u = f.depth(x, z);
        int v = f.lat(x, z);
        int floor = layout.floorY();
        int ceiling = layout.ceilingY();
        out.set(ceiling, ModBlocks.PIPE_CEILING.get().defaultBlockState());
        boolean open = hotOpen(u, v);
        boolean grate = open && v == 1 && u >= 3;
        out.set(floor, grate ? ModBlocks.FLOOR_GRATE.get().defaultBlockState() : ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        if (grate) {
            out.set(floor - 1, ModBlocks.PIPE_FLOOR.get().defaultBlockState()); // the sump under the drain
        }
        if (open) {
            if (u == 3 && v == 0) {
                // A caged lamp on the machines along the left wall (on the series circuits).
                out.set(floor + 3, ModBlocks.BULKHEAD_LAMP.get().defaultBlockState().setValue(WallLampBlock.FACING, f.right()));
            }
            if (u == 5 && v == 1) {
                out.set(ceiling - 1, ModBlocks.CEILING_TUBE.get().defaultBlockState()
                        .setValue(CeilingTubeBlock.AXIS, f.facing().getAxis()));
            } else if (u == 1) {
                out.set(ceiling - 1, ModBlocks.PIPE_WALL.get().defaultBlockState()); // the lintel over the way in
            } else if (u == 0) {
                // The corridor chase's top pipe carries on over the way in.
                out.set(ceiling - 1, Level2Layout.chaseTop(seed, Level2Line.band(z), x, doorSide(site)));
            }
            return;
        }
        // Machines face into the room.
        Direction front = u >= Level2Sites.HOT_LENGTH - 1 ? f.facing().getOpposite() : v < 1 ? f.right() : f.right().getOpposite();
        BlockState machine = ModBlocks.L2_MACHINE.get().defaultBlockState().setValue(WallMountBlock.FACING, front);
        int along = front.getAxis() == Direction.Axis.Z ? x : z;
        BlockState rack = ModBlocks.RUSTY_PIPE.get().defaultBlockState()
                .setValue(WallMountBlock.FACING, front)
                .setValue(PipeRackBlock.BRACKET, Math.floorMod(along, 3) == 0);
        out.set(floor + 1, machine);
        out.set(floor + 2, machine);
        out.set(floor + 3, u >= Level2Sites.HOT_LENGTH - 1 ? machine : rack);
        out.set(floor + 4, u == 0 ? Level2Layout.chaseTop(seed, Level2Line.band(z), x, doorSide(site)) // the corridor's top pipe
                : Level2Sites.isVent(u, v) ? ModBlocks.L2_STEAM_VENT.get().defaultBlockState().setValue(WallMountBlock.FACING, front)
                : rack);
    }

    // ==========================================================================================
    // Lamps and brownouts
    // ==========================================================================================

    /**
     * The one lamp in the hall in front of a void or loop door: on the door wall beside the door,
     * so the copy (which has the same hall) looks the same. Null for every other column.
     */
    @Nullable
    static Direction doorLamp(AnomalySite site, int x, int z) {
        Frame f = frameOf(site);
        return f.depth(x, z) == -1 && f.lat(x, z) == Level2Sites.STUB_LAMP_LAT ? f.facing().getOpposite() : null;
    }

    /** In a brownout: does the bulkhead lamp at (x, z) still work? */
    static boolean lampSurvives(long seed, int x, int z) {
        return Hash.unit(seed, x, z, 920) < BROWNOUT_LAMP_SURVIVES;
    }

    /** In a brownout: is the emergency-light spot at (x, z) fitted? */
    static boolean emergencyLight(long seed, int x, int z) {
        return Hash.unit(seed, x, z, 921) < EMERGENCY_LIGHT_CHANCE;
    }

    /**
     * Where a brownout's burst pipe is in the 12-block stretch around x (x of the burst), or
     * MIN_VALUE if that stretch has none. The ooze pools in the gutter around it and in the
     * broken chase right at it, never in the lane.
     */
    static int burstX(long seed, AnomalySite site, int x) {
        int stretch = Math.floorDiv(x - site.minX(), BURST_STRETCH);
        long h = Hash.of(seed, site.minX(), stretch, 925);
        if (Hash.unit(h) >= BURST_CHANCE) {
            return Integer.MIN_VALUE;
        }
        int bx = site.minX() + stretch * BURST_STRETCH + 2 + Hash.range(Hash.mix(h), BURST_STRETCH - 4);
        return bx <= site.maxX() ? bx : Integer.MIN_VALUE;
    }

    // ==========================================================================================
    // Above and below the level: the void and the looping hallway
    // ==========================================================================================

    /** Blocks of an anomaly outside the level's own floor-to-ceiling slice (void, copies, hallway). */
    static void fillOutside(Level2Layout layout, long seed, AnomalySite site, int x, int z, LevelLayout.Column out) {
        boolean isVoid = site.kind().equals(Level2Sites.VOID_DOOR);
        if (!isVoid && !site.kind().equals(Level2Sites.LOOP_HALL)) {
            return;
        }
        Frame f = frameOf(site);
        int d = f.depth(x, z);
        int l = f.lat(x, z);
        boolean stub = d >= -(Level2Sites.STUB_HALF) && d <= 0 && Math.abs(l) <= Level2Sites.STUB_HALF + 1;
        if (isVoid) {
            BlockState black = ModBlocks.L2_VOID_BLACK.get().defaultBlockState();
            // The void: black floor, black sky right under the level's floor, nothing in between.
            out.set(Level2Sites.FLOOR_Y - Level2Sites.VOID_DROP, black);
            out.set(Level2Sites.FLOOR_Y - 1, black);
            LevelLayout.Column low = shifted(out, -Level2Sites.VOID_DROP);
            if (stub) {
                copyStub(layout, seed, site, x, z, d, l, low);
            } else if (d >= 1 && d <= 3 && Math.abs(l) <= 1) {
                // The copy of the black passage, open at its far end onto the void.
                int floor = layout.floorY();
                low.set(floor, black);
                if (l == 0) {
                    low.set(layout.ceilingY() - 1, black);
                } else {
                    for (int y = floor + 1; y < layout.ceilingY(); y++) {
                        low.set(y, black);
                    }
                }
            }
        } else {
            LevelLayout.Column high = shifted(out, Level2Sites.LOOP_RISE);
            if (stub) {
                copyStub(layout, seed, site, x, z, d, l, high);
            } else if (d >= 0 && d <= 6 && Math.abs(l) <= Level2Sites.LOOP_HALF + 1) {
                fillLoop(layout, f, d, l, true, high);
            }
        }
    }

    /**
     * One column of the copy of the door and the hall in front of it: built by the layout's own
     * column code from the real column's code, so it matches exactly; the outer ring (depth -4,
     * lat +-5) is plain wall that closes the copy off.
     */
    private static void copyStub(Level2Layout layout, long seed, AnomalySite site, int x, int z, int d, int l,
                                 LevelLayout.Column out) {
        int code = d == -(Level2Sites.STUB_HALF) || Math.abs(l) == Level2Sites.STUB_HALF + 1
                ? Level2Layout.ROCK
                : column(site, x, z);
        layout.fillLevelColumn(seed, x, z, code, site, out);
    }

    // ==========================================================================================
    // Allocation-free helpers (every column of a big site asks for these)
    // ==========================================================================================

    /** Per thread: the last site asked about and its frames, plus one reusable shifted column. */
    private static final class Scratch implements LevelLayout.Column {
        @Nullable
        AnomalySite site;
        @Nullable
        Frame frame;
        @Nullable
        AnomalySite hotSite;
        @Nullable
        Frame hotFrame;
        @Nullable
        LevelLayout.Column target;
        int dy;

        @Override
        public void set(int y, BlockState state) {
            target.set(y + dy, state);
        }
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    /** The door frame of a site (the same object for every column of the same site). */
    private static Frame frameOf(AnomalySite site) {
        Scratch s = SCRATCH.get();
        if (s.site != site) {
            s.frame = Frame.of(site);
            s.site = site;
        }
        return s.frame;
    }

    /** The hot zone frame of a site, remembered the same way. */
    private static Frame hotFrameOf(AnomalySite site) {
        Scratch s = SCRATCH.get();
        if (s.hotSite != site) {
            s.hotFrame = Frame.hotZone(site);
            s.hotSite = site;
        }
        return s.hotFrame;
    }

    /** {@code out}, but every block lands {@code dy} higher (the copies above and below the level). */
    private static LevelLayout.Column shifted(LevelLayout.Column out, int dy) {
        Scratch s = SCRATCH.get();
        s.target = out;
        s.dy = dy;
        return s;
    }
}
