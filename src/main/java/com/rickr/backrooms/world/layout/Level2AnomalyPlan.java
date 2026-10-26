package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.CeilingTubeBlock;
import com.rickr.backrooms.block.HouseDoorBlock;
import com.rickr.backrooms.block.LampMode;
import com.rickr.backrooms.block.OfficePanelBlock;
import com.rickr.backrooms.block.PipeRackBlock;
import com.rickr.backrooms.block.WallLampBlock;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalyGrid;
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
 * Where Level 2's anomalous areas are, and their blocks. What they DO lives in
 * world/anomaly/level2 (server) and client/anomaly/level2; their shared shapes in
 * {@link Level2Sites}. Everything here is a pure function of (seed, x, z), like the rest of
 * {@link Level2Layout}.
 *
 * <p><b>Placement.</b> Two grids of squares, each site always wholly inside its own square, so
 * sites never overlap:
 * <ul>
 *   <li>160x160: about a third hold a <b>brownout</b> (3x3 or 4x4 grid squares of tunnels where the
 *       Blackout never ended), and about a third each a <b>void door</b> or a <b>loop door</b> (a
 *       door in the plain wall of a corridor, in a grid square with no utility rooms).</li>
 *   <li>96x96: three in four try to turn a <b>dead end</b> of the corridor network into a hot,
 *       loud machinery room (never inside or touching a 160-grid site).</li>
 * </ul>
 * No site touches an exit room or a square with a safe room.
 *
 * <p><b>Doors</b> (frame: depth into the rock from the door, lat to its right; see
 * {@link Frame}): the door stands in the corridor wall (depth 0) with a short passage behind it.
 * The passage is copied exactly, together with the door, the tunnel in front of it (depth -1..-3,
 * lat -4..4, including the one lamp beside the door) and closing walls, either 30 blocks below
 * (the void: a 64-block square of black that wraps around, floor y10, sky y39) or 6 blocks above
 * (the looping hallway over the tunnel ceilings). The server swaps you between the original and
 * the copy as you walk through the passage, so nothing on screen jumps.
 */
final class Level2AnomalyPlan {
    /** Brownouts and the two door kinds: at most one per square this big. */
    private static final int BIG_CELL = 160;
    /** Grid squares (16 blocks) per side of a big cell. */
    private static final int BIG_SQUARES = BIG_CELL / Level2Layout.GRID;
    /** Hot zones: at most one per square this big. */
    private static final int HOT_CELL = 96;
    private static final int HOT_SQUARES = HOT_CELL / Level2Layout.GRID;

    private static final AnomalyGrid BIG = new AnomalyGrid(BIG_CELL, 0, Level2AnomalyPlan::planBig);
    private static final AnomalyGrid HOT = new AnomalyGrid(HOT_CELL, 0, Level2AnomalyPlan::planHot);

    /** Out of 1: share of the bulkhead lamps in a brownout that still work. */
    private static final double BROWNOUT_LAMP_SURVIVES = 0.15;
    /** Out of 1: share of the emergency-light spots in a brownout that have one (most: they are its only light). */
    private static final double EMERGENCY_LIGHT_CHANCE = 0.9;
    /** Out of 1: share of a brownout's corridor stretches with a burst pipe and a pool of ooze. */
    private static final double BURST_CHANCE = 0.55;

    // ==========================================================================================
    // Queries
    // ==========================================================================================

    @Nullable
    static AnomalySite at(long seed, int x, int z) {
        AnomalySite site = BIG.at(seed, x, z);
        return site != null ? site : HOT.at(seed, x, z);
    }

    static void near(long seed, int x, int z, int radius, Consumer<AnomalySite> out) {
        BIG.near(seed, x, z, radius, out);
        HOT.near(seed, x, z, radius, out);
    }

    @Nullable
    static AnomalySite nearest(long seed, int x, int z, String kind) {
        if (kind.equals(Level2Sites.HOT_ZONE)) {
            return HOT.nearest(seed, x, z, s -> s.kind().equals(kind), 24);
        }
        return Level2Sites.KINDS.contains(kind) ? BIG.nearest(seed, x, z, s -> s.kind().equals(kind), 16) : null;
    }

    // ==========================================================================================
    // Planners (pure and cheap: only the corridor network's own hash functions)
    // ==========================================================================================

    @Nullable
    private static AnomalySite planBig(long seed, int cellX, int cellZ) {
        long h = Hash.of(seed, cellX, cellZ, 900);
        double roll = Hash.unit(h);
        String kind = roll < 0.36 ? Level2Sites.BROWNOUT
                : roll < 0.66 ? Level2Sites.VOID_DOOR
                : roll < 0.94 ? Level2Sites.LOOP_HALL
                : null;
        if (kind == null) {
            return null;
        }
        // A few tries at different spots; most fail only because of a safe room or an exit.
        for (int k = 1; k <= 8; k++) {
            long hk = Hash.mix(h + k * 0x9E3779B97F4A7C15L);
            AnomalySite site = kind.equals(Level2Sites.BROWNOUT)
                    ? planBrownout(seed, cellX, cellZ, hk)
                    : planDoor(seed, cellX, cellZ, hk, kind);
            if (site != null) {
                return site;
            }
        }
        return null;
    }

    /** A block of 3x3 or 4x4 grid squares (with the corridors along its far edges). */
    @Nullable
    private static AnomalySite planBrownout(long seed, int cellX, int cellZ, long hash) {
        int n = 3 + Hash.range(hash, 2);
        int gx0 = cellX * BIG_SQUARES + Hash.range(Hash.mix(hash ^ 1), BIG_SQUARES - n);
        int gz0 = cellZ * BIG_SQUARES + Hash.range(Hash.mix(hash ^ 2), BIG_SQUARES - n);
        for (int gx = gx0; gx < gx0 + n; gx++) {
            for (int gz = gz0; gz < gz0 + n; gz++) {
                if (Level2Layout.safeRoomIn(seed, gx, gz) != null) {
                    return null;
                }
            }
        }
        int minX = gx0 * Level2Layout.GRID;
        int minZ = gz0 * Level2Layout.GRID;
        int maxX = (gx0 + n) * Level2Layout.GRID + Level2Layout.WIDTH - 1;
        int maxZ = (gz0 + n) * Level2Layout.GRID + Level2Layout.WIDTH - 1;
        if (nearExitRoom(seed, minX, minZ, maxX, maxZ)) {
            return null;
        }
        return new AnomalySite(Level2Sites.BROWNOUT, minX, minZ, maxX, maxZ, (minX + maxX) / 2, (minZ + maxZ) / 2, 0, hash);
    }

    /**
     * A void or loop door in the plain wall of one of the corridors around a grid square near the
     * middle of the cell, so the void (65 blocks across) or the hallway (129 long) fits in the cell
     * (a loop door's square is one of the middle 2x2). The square must have no utility room: the
     * door's passage takes its rock.
     */
    @Nullable
    private static AnomalySite planDoor(long seed, int cellX, int cellZ, long hash, String kind) {
        boolean isVoid = kind.equals(Level2Sites.VOID_DOOR);
        int first = isVoid ? 3 : 4;
        int choices = isVoid ? 4 : 2;
        int gx = cellX * BIG_SQUARES + first + Hash.range(hash, choices);
        int gz = cellZ * BIG_SQUARES + first + Hash.range(Hash.mix(hash), choices);
        if (Level2Layout.safeRoomIn(seed, gx, gz) != null || Level2Layout.hasUtilityRoom(seed, gx, gz)) {
            return null;
        }
        int bx = gx * Level2Layout.GRID;
        int bz = gz * Level2Layout.GRID;
        int start = Hash.range(Hash.mix(hash ^ 3), 4);
        for (int i = 0; i < 4; i++) {
            Frame door = switch ((start + i) & 3) {
                // The corridor along the square's top edge, its plain wall on our side: same test as
                // the utility rooms and safe rooms use.
                case 0 -> Level2Layout.hasEast(seed, gx, gz) && Level2Layout.pipesNorth(seed, gx, gz)
                        ? new Frame(bx + 9, bz + 3, Direction.SOUTH) : null;
                case 1 -> Level2Layout.hasEast(seed, gx, gz + 1) && !Level2Layout.pipesNorth(seed, gx, gz + 1)
                        ? new Frame(bx + 9, bz + 15, Direction.NORTH) : null;
                case 2 -> Level2Layout.hasSouth(seed, gx, gz) && Level2Layout.pipesWest(seed, gx, gz)
                        ? new Frame(bx + 3, bz + 9, Direction.EAST) : null;
                default -> Level2Layout.hasSouth(seed, gx + 1, gz) && !Level2Layout.pipesWest(seed, gx + 1, gz)
                        ? new Frame(bx + 15, bz + 9, Direction.WEST) : null;
            };
            if (door == null) {
                continue;
            }
            int dMin = isVoid ? -Level2Sites.VOID_HALF : -(Level2Sites.STUB_HALF + 1);
            int dMax = isVoid ? Level2Sites.VOID_HALF : 6;
            int lHalf = isVoid ? Level2Sites.VOID_HALF : Level2Sites.LOOP_HALF + 1;
            int x0 = door.x(dMin, -lHalf);
            int z0 = door.z(dMin, -lHalf);
            int x1 = door.x(dMax, lHalf);
            int z1 = door.z(dMax, lHalf);
            int minX = Math.min(x0, x1);
            int maxX = Math.max(x0, x1);
            int minZ = Math.min(z0, z1);
            int maxZ = Math.max(z0, z1);
            if (safeRoomIn(seed, minX, minZ, maxX, maxZ) || nearExitRoom(seed, minX, minZ, maxX, maxZ)) {
                continue;
            }
            return new AnomalySite(kind, minX, minZ, maxX, maxZ, door.ax(), door.az(), door.facing().get2DDataValue(), hash);
        }
        return null;
    }

    /** A dead end (a node with a single corridor) near the middle of the cell, turned into a machinery room. */
    @Nullable
    private static AnomalySite planHot(long seed, int cellX, int cellZ) {
        long h = Hash.of(seed, cellX, cellZ, 910);
        if (Hash.unit(h) >= 0.75) {
            return null;
        }
        // Nodes 1..4 of the cell's 6x6, so the room (up to 10 blocks past its node) stays inside.
        int start = Hash.range(Hash.mix(h), 16);
        for (int i = 0; i < 16; i++) {
            int k = (start + i * 7) & 15; // steps of 7 visit all 16 in a scrambled order
            AnomalySite site = planHotAt(seed, cellX * HOT_SQUARES + 1 + (k & 3), cellZ * HOT_SQUARES + 1 + (k >> 2), h ^ k);
            if (site != null) {
                return site;
            }
        }
        return null;
    }

    @Nullable
    private static AnomalySite planHotAt(long seed, int gx, int gz, long salt) {
        if (Level2Layout.degree(seed, gx, gz) != 1) {
            return null;
        }
        // The room continues the way the only corridor was heading.
        Direction f = Level2Layout.hasEast(seed, gx - 1, gz) ? Direction.EAST
                : Level2Layout.hasEast(seed, gx, gz) ? Direction.WEST
                : Level2Layout.hasSouth(seed, gx, gz - 1) ? Direction.SOUTH
                : Direction.NORTH;
        Direction r = f.getClockWise();
        int bx = gx * Level2Layout.GRID;
        int bz = gz * Level2Layout.GRID;
        // Frame origin: the node corner from which depth (u) 0..2 and lat (v) 0..2 cover the node.
        Frame frame = new Frame(bx + (f.getStepX() < 0 || r.getStepX() < 0 ? 2 : 0),
                bz + (f.getStepZ() < 0 || r.getStepZ() < 0 ? 2 : 0), f);
        // The room with its walls: u 0..10, v -2..4.
        int wx0 = frame.x(0, -2);
        int wz0 = frame.z(0, -2);
        int wx1 = frame.x(Level2Sites.HOT_LENGTH, 4);
        int wz1 = frame.z(Level2Sites.HOT_LENGTH, 4);
        int minX = Math.min(wx0, wx1);
        int maxX = Math.max(wx0, wx1);
        int minZ = Math.min(wz0, wz1);
        int maxZ = Math.max(wz0, wz1);
        if (safeRoomIn(seed, minX, minZ, maxX, maxZ) || nearExitRoom(seed, minX, minZ, maxX, maxZ)
                || bigSiteNear(seed, minX - 2, minZ - 2, maxX + 2, maxZ + 2)) {
            return null;
        }
        // The site itself: the room without its walls (u 0..9, v -1..3); anchor in its middle.
        int sx0 = frame.x(0, -1);
        int sz0 = frame.z(0, -1);
        int sx1 = frame.x(Level2Sites.HOT_LENGTH - 1, 3);
        int sz1 = frame.z(Level2Sites.HOT_LENGTH - 1, 3);
        return new AnomalySite(Level2Sites.HOT_ZONE, Math.min(sx0, sx1), Math.min(sz0, sz1), Math.max(sx0, sx1), Math.max(sz0, sz1),
                frame.x(5, 1), frame.z(5, 1), f.get2DDataValue(), salt);
    }

    /** True if any grid square touching the rectangle has a safe room (the whole square counts). */
    private static boolean safeRoomIn(long seed, int minX, int minZ, int maxX, int maxZ) {
        for (int gx = Math.floorDiv(minX, Level2Layout.GRID); gx <= Math.floorDiv(maxX, Level2Layout.GRID); gx++) {
            for (int gz = Math.floorDiv(minZ, Level2Layout.GRID); gz <= Math.floorDiv(maxZ, Level2Layout.GRID); gz++) {
                if (Level2Layout.safeRoomIn(seed, gx, gz) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True if an exit room (15x15 around each exit) comes within 2 blocks of the rectangle. */
    private static boolean nearExitRoom(long seed, int minX, int minZ, int maxX, int maxZ) {
        int reach = Level2Layout.EXIT_ROOM_RADIUS + 2;
        int region = 320; // Level2Layout.EXITS region size
        for (int rx = Math.floorDiv(minX - reach, region); rx <= Math.floorDiv(maxX + reach, region); rx++) {
            for (int rz = Math.floorDiv(minZ - reach, region); rz <= Math.floorDiv(maxZ + reach, region); rz++) {
                int ex = Level2Layout.EXITS.exitX(seed, rx, rz);
                int ez = Level2Layout.EXITS.exitZ(seed, rx, rz);
                if (ex + reach >= minX && ex - reach <= maxX && ez + reach >= minZ && ez - reach <= maxZ) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True if a brownout or door site of the big grid touches the rectangle. */
    private static boolean bigSiteNear(long seed, int minX, int minZ, int maxX, int maxZ) {
        for (int cx = Math.floorDiv(minX, BIG_CELL); cx <= Math.floorDiv(maxX, BIG_CELL); cx++) {
            for (int cz = Math.floorDiv(minZ, BIG_CELL); cz <= Math.floorDiv(maxZ, BIG_CELL); cz++) {
                AnomalySite s = BIG.site(seed, cx, cz);
                if (s != null && s.maxX() >= minX && s.minX() <= maxX && s.maxZ() >= minZ && s.minZ() <= maxZ) {
                    return true;
                }
            }
        }
        return false;
    }

    // ==========================================================================================
    // Columns at the level's own height
    // ==========================================================================================

    private static boolean isDoor(AnomalySite site) {
        return site.kind().equals(Level2Sites.VOID_DOOR) || site.kind().equals(Level2Sites.LOOP_HALL);
    }

    /**
     * Column code (see {@link Level2Layout}) of an anomaly's own structure at (x, z), or -1 where
     * no anomaly changes the level there.
     */
    static int column(long seed, int x, int z) {
        AnomalySite site = at(seed, x, z);
        if (site == null) {
            return -1;
        }
        if (site.kind().equals(Level2Sites.HOT_ZONE)) {
            Frame f = Frame.hotZone(site);
            return hotOpen(f.depth(x, z), f.lat(x, z)) ? Level2Layout.SITE_OPEN : Level2Layout.SITE_SOLID;
        }
        if (!isDoor(site)) {
            return -1; // a brownout only changes lamps and floors (see fillBrownout)
        }
        Frame f = Frame.of(site);
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
                // The tunnel in front of the door, as usual but without a dropped backpack (its copy has none).
                int grid = Level2Layout.gridColumn(seed, x, z);
                return Level2Layout.kindOf(grid) == Level2Layout.BACKPACK ? Level2Layout.WALK : grid;
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

    /**
     * Hot zone room (u along the dead end, v across): a 3-wide walkway (the node and on) between
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
    static void fillSiteColumn(Level2Layout layout, @Nullable AnomalySite site, int x, int z, LevelLayout.Column out) {
        if (site == null) {
            return;
        }
        if (site.kind().equals(Level2Sites.HOT_ZONE)) {
            fillHot(layout, site, x, z, out);
            return;
        }
        Frame f = Frame.of(site);
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

    /** The door, closed, flush with the corridor wall (its slab on the corridor side), wall above. */
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
     * beyond the copy of the tunnel) is the wall behind the passage copies, each with a closed door.
     */
    private static void fillLoop(Level2Layout layout, Frame f, int d, int l, boolean upper, LevelLayout.Column out) {
        int floor = layout.floorY();
        int ceiling = layout.ceilingY();
        BlockState carpet = ModBlocks.OFFICE_CARPET.get().defaultBlockState();
        BlockState officeCeiling = ModBlocks.OFFICE_CEILING.get().defaultBlockState();
        BlockState wall = ModBlocks.OFFICE_WALL.get().defaultBlockState();
        out.set(ceiling, ModBlocks.PIPE_CEILING.get().defaultBlockState());
        if (d == 0) {
            // Looks like the real door's column seen from its passage: the door, tunnel wall above.
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
     * The machinery room at a dead end: machine cabinets two high along both side walls and three
     * high across the back, rusty pipe racks above them with leaking steam vents, drain grates down
     * the middle and one hanging tube light.
     */
    private static void fillHot(Level2Layout layout, AnomalySite site, int x, int z, LevelLayout.Column out) {
        Frame f = Frame.hotZone(site);
        int u = f.depth(x, z);
        int v = f.lat(x, z);
        int floor = layout.floorY();
        int ceiling = layout.ceilingY();
        out.set(ceiling, ModBlocks.PIPE_CEILING.get().defaultBlockState());
        boolean open = hotOpen(u, v);
        out.set(floor, open && v == 1 && u >= 3 ? ModBlocks.FLOOR_GRATE.get().defaultBlockState()
                : ModBlocks.PIPE_FLOOR.get().defaultBlockState());
        if (open) {
            if (u == 5 && v == 1) {
                out.set(ceiling - 1, ModBlocks.CEILING_TUBE.get().defaultBlockState()
                        .setValue(CeilingTubeBlock.AXIS, f.facing().getAxis()));
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
        out.set(floor + 4, Level2Sites.isVent(u, v)
                ? ModBlocks.L2_STEAM_VENT.get().defaultBlockState().setValue(WallMountBlock.FACING, front)
                : rack);
    }

    // ==========================================================================================
    // Lamps and brownouts
    // ==========================================================================================

    /**
     * Lamps in the tunnel in front of a void or loop door: exactly one, beside the door, so the
     * copy of that bit of tunnel (which only has this one) looks the same. Elsewhere unchanged.
     */
    @Nullable
    static Direction doorLamp(@Nullable AnomalySite site, int x, int z, @Nullable Direction lamp) {
        if (site == null || !isDoor(site)) {
            return lamp;
        }
        Frame f = Frame.of(site);
        int d = f.depth(x, z);
        int l = f.lat(x, z);
        if (d < -3 || d > -1 || Math.abs(l) > Level2Sites.STUB_HALF) {
            return lamp;
        }
        return d == -1 && l == Level2Sites.STUB_LAMP_LAT ? f.facing().getOpposite() : null;
    }

    /** In a brownout: does the bulkhead lamp at (x, z) still work? */
    static boolean lampSurvives(long seed, int x, int z) {
        return Hash.unit(seed, x, z, 920) < BROWNOUT_LAMP_SURVIVES;
    }

    /**
     * The extras of an open corridor column in a brownout: a violet emergency light on the plain
     * wall now and then (halfway between lamp spots, lower down), and the black ooze pooled under
     * a burst pipe.
     */
    static void fillBrownout(Level2Layout layout, long seed, int x, int z, LevelLayout.Column out) {
        int floor = layout.floorY();
        if (oozeAt(seed, x, z)) {
            out.set(floor + 1, ModBlocks.L2_BLACK_OOZE.get().defaultBlockState());
        }
        Direction emergency = layout.wallFixtureFacing(seed, x, z, Level2Layout.LAMP_SPACING / 2, false);
        if (emergency != null && Hash.unit(seed, x, z, 921) < EMERGENCY_LIGHT_CHANCE) {
            out.set(floor + 3, ModBlocks.L2_EMERGENCY_LIGHT.get().defaultBlockState().setValue(WallLampBlock.FACING, emergency));
        }
    }

    /**
     * Ooze in a corridor stretch with a burst pipe: a pool 3 long across the walkway, running a
     * little further along the middle row (the one beside the pipes, which is where it drips).
     */
    private static boolean oozeAt(long seed, int x, int z) {
        int gx = Math.floorDiv(x, Level2Layout.GRID);
        int gz = Math.floorDiv(z, Level2Layout.GRID);
        int lx = x - gx * Level2Layout.GRID;
        int lz = z - gz * Level2Layout.GRID;
        int axis;
        int along;
        int across;
        if (lz < Level2Layout.WIDTH && lx >= Level2Layout.WIDTH) {
            axis = 0;
            along = lx;
            across = lz;
        } else if (lx < Level2Layout.WIDTH && lz >= Level2Layout.WIDTH) {
            axis = 1;
            along = lz;
            across = lx;
        } else {
            return false;
        }
        long h = Hash.of(seed, gx, gz, 925 + axis);
        if (Hash.unit(h) >= BURST_CHANCE) {
            return false;
        }
        int burst = 5 + Hash.range(Hash.mix(h), 9);
        int off = Math.abs(along - burst);
        if (off <= 1) {
            return true;
        }
        return across == 1 && (off == 2 || (off == 3 && Hash.unit(seed, x, z, 926) < 0.5));
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
        Frame f = Frame.of(site);
        int d = f.depth(x, z);
        int l = f.lat(x, z);
        boolean stub = d >= -(Level2Sites.STUB_HALF) && d <= 0 && Math.abs(l) <= Level2Sites.STUB_HALF + 1;
        if (isVoid) {
            BlockState black = ModBlocks.L2_VOID_BLACK.get().defaultBlockState();
            // The void: black floor, black sky right under the level's floor, nothing in between.
            out.set(Level2Sites.FLOOR_Y - Level2Sites.VOID_DROP, black);
            out.set(Level2Sites.FLOOR_Y - 1, black);
            LevelLayout.Column low = (y, state) -> out.set(y - Level2Sites.VOID_DROP, state);
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
            LevelLayout.Column high = (y, state) -> out.set(y + Level2Sites.LOOP_RISE, state);
            if (stub) {
                copyStub(layout, seed, site, x, z, d, l, high);
            } else if (d >= 0 && d <= 6 && Math.abs(l) <= Level2Sites.LOOP_HALF + 1) {
                fillLoop(layout, f, d, l, true, high);
            }
        }
    }

    /**
     * One column of the copy of the door and the tunnel in front of it: built by the layout's own
     * column code from the real column's code, so it matches exactly; the outer ring (depth -4,
     * lat +-5) is plain wall that closes the copy off.
     */
    private static void copyStub(Level2Layout layout, long seed, AnomalySite site, int x, int z, int d, int l,
                                 LevelLayout.Column out) {
        int code = d == -(Level2Sites.STUB_HALF) || Math.abs(l) == Level2Sites.STUB_HALF + 1
                ? Level2Layout.ROCK
                : layout.column(seed, x, z);
        layout.fillLevelColumn(seed, x, z, code, site, out);
    }

    private Level2AnomalyPlan() {}
}
