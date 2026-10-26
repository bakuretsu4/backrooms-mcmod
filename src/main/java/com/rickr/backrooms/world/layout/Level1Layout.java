package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.CeilingTubeBlock;
import com.rickr.backrooms.block.PillarNumberBlock;
import com.rickr.backrooms.block.WallLampBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Hash;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Level 1, "Habitable Zone": an endless underground concrete parking structure.
 *
 * <p>Everything hangs off a regular 8-block grid. At every grid corner stands a 2x2 concrete
 * pillar (darker base band, a big painted yellow number on each face). The 2-block strips between
 * neighbouring pillars are "partitions": open, chain-link, a wall with a doorway, or a solid wall.
 *
 * <p>Two kinds of zone (64x64 blocks each):
 * <ul>
 *   <li><b>Concrete maze</b> (most of the level): nearly every partition is a wall, most with a
 *       2-3 wide doorway at a random spot, about a third solid. That gives closed-off 6x6 rooms,
 *       winding routes and plenty of dead ends, like Level 0 in grey. Some rooms are split by a
 *       stub wall into two narrow corridors. Light comes from ceiling tubes and tubes on walls.</li>
 *   <li><b>Garage hall</b>: the wide-open, pillar-forest parking area with vertical tube lights on
 *       the pillars, chain-link fences and crate stacks.</li>
 * </ul>
 *
 * <p>Exit: one white stairwell per 384x384 region.
 *
 * <p><b>Safe rooms.</b> About 60% of the 128x128 squares have one: a concrete room built over a
 * block of 2x2 bays (so its outside lines up with the pillar rows), with a metal door frame, and
 * inside a carpet, wood panelling under white paint and a warm lamp. A 1-wide strip of floor runs
 * all around it so it always joins the garage. Maze rooms also hold the odd metal supply locker
 * against a wall, and now and then a dropped backpack.
 */
public final class Level1Layout extends LevelLayout {
    private static final int GRID = 8;
    private static final int CLEARING_RADIUS = 8;
    /** Size of the zones that are either maze or open hall. */
    private static final int HALL_ZONE = 64;
    /** Fraction of zones that are open garage halls. */
    private static final double HALL_SHARE = 0.3;

    // Partition states between two pillars.
    private static final int OPEN = 0;
    private static final int CHAIN = 1;
    private static final int DOOR = 2;  // wall with a doorway
    private static final int SOLID = 3; // wall, no way through

    /** Safe rooms: the free strip all around each one. */
    private static final int SAFE_MOAT = 1;
    /** Safe rooms keep at least this far (chessboard distance from their centre) from an exit. */
    private static final int SAFE_EXIT_DISTANCE = 24;
    /** Grid cells per safe-room square (128 / 8). */
    private static final int SAFE_GRID_CELLS = SafeRoomPlan.CELL / GRID;

    public Level1Layout() {
        super(5, new ExitLocator(384, 48, 2000));
    }

    // ==========================================================================================
    // Safe rooms
    // ==========================================================================================

    /**
     * The safe room whose footprint (room plus the free strip around it) contains (x, z), or null.
     * The footprint covers two bays by two bays, from just inside one pillar row to just before
     * the pillar row two cells further on, so it never leaves half a pillar standing.
     */
    @Nullable
    private SafeRoomPlan safeRoomNear(long seed, int x, int z) {
        int cellX = Math.floorDiv(x, SafeRoomPlan.CELL);
        int cellZ = Math.floorDiv(z, SafeRoomPlan.CELL);
        long h = SafeRoomPlan.cellHash(seed, cellX, cellZ, 410);
        if (!SafeRoomPlan.rolled(h)) {
            return null;
        }
        // Grid cells a..a+1 and b..b+1, never at the square's edge.
        int a = cellX * SAFE_GRID_CELLS + 1 + Hash.range(Hash.mix(h ^ 1), SAFE_GRID_CELLS - 3);
        int b = cellZ * SAFE_GRID_CELLS + 1 + Hash.range(Hash.mix(h ^ 2), SAFE_GRID_CELLS - 3);
        int footX = a * GRID + 2; // footprint: 14 x 14 blocks from here
        int footZ = b * GRID + 2;
        int footSize = 2 * GRID - 2;
        if (x < footX || x >= footX + footSize || z < footZ || z >= footZ + footSize) {
            return null;
        }
        SafeRoomPlan plan = new SafeRoomPlan(footX + SAFE_MOAT, footZ + SAFE_MOAT, footSize - 2 * SAFE_MOAT,
                footSize - 2 * SAFE_MOAT, SafeRoomPlan.doorSide(h), 2);
        int[] exit = exits().nearest(seed, plan.centerX(), plan.centerZ());
        if (Math.max(Math.abs(exit[0] - plan.centerX()), Math.abs(exit[1] - plan.centerZ())) < SAFE_EXIT_DISTANCE) {
            return null;
        }
        return plan;
    }

    /** Everything inside a safe room, walls and doorway included. */
    @Override
    public boolean isSafeRoom(long seed, int x, int z) {
        SafeRoomPlan plan = safeRoomNear(seed, x, z);
        return plan != null && plan.contains(x, z);
    }

    private void fillSafeRoom(SafeRoomPlan plan, int x, int z, Column out) {
        BlockState concrete = ModBlocks.CONCRETE_WALL.get().defaultBlockState();
        if (!plan.contains(x, z)) {
            // The free strip around the room: bare concrete.
            out.set(floorY(), ModBlocks.CONCRETE_FLOOR.get().defaultBlockState());
            out.set(ceilingY(), concrete);
            return;
        }
        plan.fill(x, z, floorY(), ceilingY(), new SafeRoomPlan.Style(
                ModBlocks.BEIGE_CARPET.get().defaultBlockState(),
                concrete,
                ModBlocks.GARAGE_PILLAR.get().defaultBlockState(), // the dark base band outside
                concrete,
                ModBlocks.WOOD_FLOOR.get().defaultBlockState(),    // wood panelling below the dado line
                ModBlocks.STAIRWELL_WALL.get().defaultBlockState(), // white paint above it
                ModBlocks.FLOOR_GRATE.get().defaultBlockState()),   // dark metal door frame
                out);
    }

    // ==========================================================================================
    // Maze
    // ==========================================================================================

    /** Large areas where the power is out: no tubes at all. */
    private static boolean darkZone(long seed, int x, int z) {
        return zone(seed, x, z, 40, 25) < 0.25;
    }

    /** True inside an open garage hall, false inside the concrete maze. */
    private static boolean hallZone(long seed, int x, int z) {
        return zone(seed, x, z, HALL_ZONE, 22) < HALL_SHARE;
    }

    /**
     * The partition between pillar (cx, cz) and its neighbour: axis 0 = the row running east
     * (along X), axis 1 = the row running south (along Z).
     */
    private static int partition(long seed, int cx, int cz, int axis) {
        double r = Hash.unit(seed, cx, cz, 30 + axis);
        if (hallZone(seed, cx * GRID, cz * GRID)) {
            return r < 0.74 ? OPEN : r < 0.92 ? CHAIN : r < 0.98 ? DOOR : SOLID;
        }
        return r < 0.10 ? OPEN : r < 0.15 ? CHAIN : r < 0.64 ? DOOR : SOLID;
    }

    /** Partition containing a pillar-row column (mx/mz as in {@link #fillColumn}); OPEN for bays and pillars. */
    private static int partitionAt(long seed, int x, int z) {
        int mx = Math.floorMod(x, GRID);
        int mz = Math.floorMod(z, GRID);
        if (mz <= 1 && mx >= 2) return partition(seed, Math.floorDiv(x, GRID), Math.floorDiv(z, GRID), 0);
        if (mx <= 1 && mz >= 2) return partition(seed, Math.floorDiv(x, GRID), Math.floorDiv(z, GRID), 1);
        return OPEN;
    }

    /**
     * Doorway in a DOOR partition: 2 or 3 wide at a random spot along the wall. {@code along} is
     * the position along the partition, 2..7 (0..1 is the pillar).
     */
    private static boolean doorwayAt(long seed, int cx, int cz, int axis, int along) {
        long h = Hash.of(seed, cx, cz, 32 + axis);
        int width = Hash.unit(h) < 0.35 ? 3 : 2;
        int start = 2 + Hash.range(Hash.mix(h), 7 - width);
        return along >= start && along < start + width;
    }

    /**
     * Some maze rooms have a 2-thick stub wall down the middle, turning the room into two narrow
     * corridors joined by 1-wide gaps at each end. Returns true for the stub wall's columns.
     */
    private static boolean splitWall(long seed, int x, int z) {
        int mx = Math.floorMod(x, GRID);
        int mz = Math.floorMod(z, GRID);
        if (mx < 2 || mz < 2) {
            return false;
        }
        int kind = splitKind(seed, Math.floorDiv(x, GRID), Math.floorDiv(z, GRID));
        if (kind == 0) {
            return false;
        }
        int across = kind == 1 ? mx : mz;
        int along = kind == 1 ? mz : mx;
        return (across == 4 || across == 5) && along >= 3 && along <= 6;
    }

    /** 0 = no split in this cell, 1 = stub wall runs along Z, 2 = along X. */
    private static int splitKind(long seed, int cx, int cz) {
        if (hallZone(seed, cx * GRID, cz * GRID)) {
            return 0;
        }
        long h = Hash.of(seed, cx, cz, 50);
        if (Hash.unit(h) >= 0.15) {
            return 0;
        }
        return Hash.unit(Hash.mix(h)) < 0.5 ? 1 : 2;
    }

    /**
     * A vertical tube light on a solid maze wall, facing into the room. Returns the direction
     * the lamp faces, or null. Each solid wall gets one in the middle of each side, 40% of the time.
     */
    @Nullable
    private static Direction wallLampFace(long seed, int x, int z) {
        int mx = Math.floorMod(x, GRID);
        int mz = Math.floorMod(z, GRID);
        int cx = Math.floorDiv(x, GRID);
        int cz = Math.floorDiv(z, GRID);
        Direction face;
        int px;
        int pz;
        int axis;
        if (mz == 2 && mx == 4) { face = Direction.SOUTH; px = cx; pz = cz; axis = 0; }          // wall to the north
        else if (mz == 7 && mx == 5) { face = Direction.NORTH; px = cx; pz = cz + 1; axis = 0; } // wall to the south
        else if (mx == 2 && mz == 5) { face = Direction.EAST; px = cx; pz = cz; axis = 1; }      // wall to the west
        else if (mx == 7 && mz == 4) { face = Direction.WEST; px = cx + 1; pz = cz; axis = 1; }  // wall to the east
        else return null;
        if (partition(seed, px, pz, axis) != SOLID || darkZone(seed, x, z)) {
            return null;
        }
        return Hash.unit(seed, x, z, 52) < 0.4 ? face : null;
    }

    @Override
    public boolean isWalkable(long seed, int x, int z) {
        int d = exits().distance(seed, x, z);
        if (d <= STAIRWELL_RADIUS) return false;
        if (d <= CLEARING_RADIUS) return true;
        SafeRoomPlan safe = safeRoomNear(seed, x, z);
        if (safe != null) {
            return !safe.contains(x, z) || safe.isWalkable(x, z);
        }
        int mx = Math.floorMod(x, GRID);
        int mz = Math.floorMod(z, GRID);
        int cx = Math.floorDiv(x, GRID);
        int cz = Math.floorDiv(z, GRID);
        if (mx <= 1 && mz <= 1) return false; // pillar
        if (mz <= 1 || mx <= 1) {
            int axis = mz <= 1 ? 0 : 1;
            int state = partition(seed, cx, cz, axis);
            int row = axis == 0 ? mz : mx;
            int along = axis == 0 ? mx : mz;
            if (state == SOLID) return false;
            if (state == DOOR) return doorwayAt(seed, cx, cz, axis, along);
            if (state == CHAIN) return row == 1;
            return true;
        }
        if (splitWall(seed, x, z)) return false;
        return crateHeight(seed, x, z) == 0 && lockerFacing(seed, x, z) == null && backpackFacing(seed, x, z) == null;
    }

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        BlockState concrete = ModBlocks.CONCRETE_WALL.get().defaultBlockState();
        BlockState floorBlock = ModBlocks.CONCRETE_FLOOR.get().defaultBlockState();
        int floor = floorY();
        int ceiling = ceilingY();
        int dx = exits().dx(seed, x, z);
        int dz = exits().dz(seed, x, z);
        int exitDistance = Math.max(Math.abs(dx), Math.abs(dz));

        if (stairwell(seed, dx, dz, x - dx, z - dz, concrete, concrete, false, out)) {
            return;
        }
        SafeRoomPlan safe = safeRoomNear(seed, x, z);
        if (safe != null) {
            fillSafeRoom(safe, x, z, out);
            return;
        }
        out.set(ceiling, concrete);
        out.set(floor, puddle(seed, x, z) ? ModBlocks.WET_CONCRETE_FLOOR.get().defaultBlockState() : floorBlock);
        if (exitDistance <= CLEARING_RADIUS) {
            // Open apron around the stairwell, lit by tube strips on all four sides.
            int ax = Math.abs(dx);
            int az = Math.abs(dz);
            if (az == 7 && ax <= 3) {
                out.set(ceiling - 1, tube(Direction.Axis.X));
            } else if (ax == 7 && az <= 3) {
                out.set(ceiling - 1, tube(Direction.Axis.Z));
            }
            return;
        }

        int mx = Math.floorMod(x, GRID);
        int mz = Math.floorMod(z, GRID);
        int cx = Math.floorDiv(x, GRID);
        int cz = Math.floorDiv(z, GRID);

        // Concrete beams under the ceiling along every east-west pillar row.
        if (mz <= 1) {
            out.set(ceiling - 1, ModBlocks.CEILING_BEAM.get().defaultBlockState());
        }

        if (mx <= 1 && mz <= 1) {
            fillPillar(seed, cx, cz, mx, mz, out);
            return;
        }

        if (mz <= 1 || mx <= 1) {
            int axis = mz <= 1 ? 0 : 1;
            int state = partition(seed, cx, cz, axis);
            int row = axis == 0 ? mz : mx;
            int along = axis == 0 ? mx : mz;
            if (state == SOLID || state == DOOR) {
                int top = mz <= 1 ? ceiling - 1 : ceiling; // leave the beam on east-west rows
                boolean doorway = state == DOOR && doorwayAt(seed, cx, cz, axis, along);
                for (int y = floor + 1; y < top; y++) {
                    if (doorway && y <= floor + 3) {
                        continue; // the doorway, with a lintel above
                    }
                    out.set(y, concrete);
                }
                return;
            }
            if (state == CHAIN && row == 0) {
                BlockState fence = ModBlocks.CHAIN_LINK.get().defaultBlockState()
                        .setValue(IronBarsBlock.EAST, axis == 0).setValue(IronBarsBlock.WEST, axis == 0)
                        .setValue(IronBarsBlock.NORTH, axis == 1).setValue(IronBarsBlock.SOUTH, axis == 1);
                for (int y = floor + 1; y <= floor + 3; y++) {
                    out.set(y, fence);
                }
                return;
            }
        }

        // Fixtures on the faces of nearby pillars (only on open rows, never inside a fence or wall).
        if (partitionAt(seed, x, z) == OPEN) {
            Direction tubeFace = pillarTubeFace(seed, x, z);
            if (tubeFace != null) {
                BlockState tube = ModBlocks.WAREHOUSE_LIGHT.get().defaultBlockState().setValue(WallLampBlock.FACING, tubeFace);
                out.set(floor + 2, tube);
                out.set(floor + 3, tube);
            }
            Direction alarmFace = alarmLampFace(seed, x, z);
            if (alarmFace != null) {
                out.set(floor + 4, ModBlocks.RED_ALARM_LAMP.get().defaultBlockState().setValue(WallLampBlock.FACING, alarmFace));
            }
        }

        if (mx >= 2 && mz >= 2) {
            if (splitWall(seed, x, z)) {
                for (int y = floor + 1; y < ceiling; y++) {
                    out.set(y, concrete);
                }
                return;
            }
            // Tube strip across the room. A room split along X gets one in each corridor instead.
            boolean split = splitKind(seed, cx, cz) == 2;
            boolean tubeRow = split ? (mz == 2 || mz == 7) : mz == 4;
            if (tubeRow && mx >= 3 && mx <= 6 && !darkZone(seed, x, z) && Hash.unit(seed, cx, cz, 27) > 0.3) {
                out.set(ceiling - 1, tube(Direction.Axis.X));
            }
            Direction lampFace = wallLampFace(seed, x, z);
            if (lampFace != null) {
                BlockState lamp = ModBlocks.WAREHOUSE_LIGHT.get().defaultBlockState().setValue(WallLampBlock.FACING, lampFace);
                out.set(floor + 2, lamp);
                out.set(floor + 3, lamp);
            }
            int crates = crateHeight(seed, x, z);
            for (int i = 0; i < crates; i++) {
                out.set(floor + 1 + i, ModBlocks.SUPPLY_CRATE.get().defaultBlockState());
            }
            Direction locker = lockerFacing(seed, x, z);
            if (locker != null) {
                out.set(floor + 1, ModBlocks.SUPPLY_LOCKER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, locker));
            }
            Direction backpack = backpackFacing(seed, x, z);
            if (backpack != null) {
                out.set(floor + 1, ModBlocks.BACKPACK.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, backpack));
            }
        }
    }

    /**
     * A metal supply locker standing against one wall of a maze room (about one room in eight).
     * Returns the way its door faces (into the room), or null for no locker here. Only against a
     * real wall (never in front of a doorway), never in a split room, and clear of the crate corner
     * and the wall lamps.
     */
    @Nullable
    private static Direction lockerFacing(long seed, int x, int z) {
        int mx = Math.floorMod(x, GRID);
        int mz = Math.floorMod(z, GRID);
        if (mx < 2 || mz < 2) {
            return null;
        }
        int cx = Math.floorDiv(x, GRID);
        int cz = Math.floorDiv(z, GRID);
        long h = Hash.of(seed, cx, cz, 53);
        if (Hash.unit(h) >= 0.12 || hallZone(seed, cx * GRID, cz * GRID) || splitKind(seed, cx, cz) != 0) {
            return null;
        }
        int along = 6 + Hash.range(Hash.mix(h ^ 1), 2); // 6 or 7 along the wall
        // The wall it stands against: the partition just outside the room on that side.
        int px;
        int pz;
        int axis;
        Direction facing;
        switch (Hash.range(Hash.mix(h), 4)) {
            case 0 -> { if (mx != along || mz != 2) return null; px = cx; pz = cz; axis = 0; facing = Direction.SOUTH; }
            case 1 -> { if (mx != 2 || mz != along) return null; px = cx; pz = cz; axis = 1; facing = Direction.EAST; }
            case 2 -> { if (mx != along || mz != 7) return null; px = cx; pz = cz + 1; axis = 0; facing = Direction.NORTH; }
            default -> { if (mx != 7 || mz != along) return null; px = cx + 1; pz = cz; axis = 1; facing = Direction.WEST; }
        }
        int state = partition(seed, px, pz, axis);
        boolean wallBehind = state == SOLID || state == DOOR && !doorwayAt(seed, px, pz, axis, along);
        return wallBehind ? facing : null;
    }

    /**
     * Now and then a backpack someone dropped lies on the floor of a bay: rare (about one per 400
     * floor blocks). Returns the way it faces, or null for no backpack here.
     */
    @Nullable
    private Direction backpackFacing(long seed, int x, int z) {
        int mx = Math.floorMod(x, GRID);
        int mz = Math.floorMod(z, GRID);
        if (mx < 2 || mz < 2) {
            return null;
        }
        int cx = Math.floorDiv(x, GRID);
        int cz = Math.floorDiv(z, GRID);
        long h = Hash.of(seed, cx, cz, 54);
        if (Hash.unit(h) >= 0.08 || splitKind(seed, cx, cz) != 0) {
            return null;
        }
        if (mx != 2 + Hash.range(Hash.mix(h ^ 1), GRID - 2) || mz != 2 + Hash.range(Hash.mix(h ^ 2), GRID - 2)) {
            return null;
        }
        if (crateHeight(seed, x, z) > 0 || lockerFacing(seed, x, z) != null) {
            return null;
        }
        return Direction.from2DDataValue(Hash.range(Hash.mix(h ^ 3), 4));
    }

    private static BlockState tube(Direction.Axis axis) {
        return ModBlocks.CEILING_TUBE.get().defaultBlockState().setValue(CeilingTubeBlock.AXIS, axis);
    }

    /**
     * A 2x2 pillar. Each of its four corner blocks carries the painted number on one face, in a
     * pinwheel: (0,0) north, (1,0) east, (1,1) south, (0,1) west. The tube light then fits on the
     * other half of each face.
     */
    private void fillPillar(long seed, int cx, int cz, int mx, int mz, Column out) {
        int floor = floorY();
        int ceiling = ceilingY();
        BlockState concrete = ModBlocks.CONCRETE_WALL.get().defaultBlockState();
        out.set(floor + 1, ModBlocks.GARAGE_PILLAR.get().defaultBlockState());
        for (int y = floor + 2; y < ceiling; y++) {
            out.set(y, concrete);
        }
        Direction face = mz == 0 ? (mx == 0 ? Direction.NORTH : Direction.EAST) : (mx == 1 ? Direction.SOUTH : Direction.WEST);
        int number = Hash.range(Hash.of(seed, cx, cz, 34), PillarNumberBlock.LABELS.length);
        out.set(floor + 2, ModBlocks.PILLAR_NUMBER.get().defaultBlockState()
                .setValue(PillarNumberBlock.FACING, face)
                .setValue(PillarNumberBlock.NUMBER, number));
    }

    /**
     * If (x, z) is the spot in front of a pillar face where its tube light hangs, returns the
     * face direction (the tube's FACING), else null. Pillar base = its north-west block.
     */
    @Nullable
    private static Direction pillarTubeFace(long seed, int x, int z) {
        int mx = Math.floorMod(x, GRID);
        int mz = Math.floorMod(z, GRID);
        Direction face;
        int baseX;
        int baseZ;
        if (mx == 1 && mz == 7) { face = Direction.NORTH; baseX = x - 1; baseZ = z + 1; }
        else if (mx == 2 && mz == 1) { face = Direction.EAST; baseX = x - 2; baseZ = z - 1; }
        else if (mx == 0 && mz == 2) { face = Direction.SOUTH; baseX = x; baseZ = z - 2; }
        else if (mx == 7 && mz == 0) { face = Direction.WEST; baseX = x + 1; baseZ = z; }
        else return null;
        if (darkZone(seed, baseX, baseZ)) return null;
        // Some tubes are simply dead.
        boolean lit = Hash.unit(seed, Math.floorDiv(baseX, GRID), Math.floorDiv(baseZ, GRID), 40 + face.get2DDataValue()) > 0.2;
        return lit ? face : null;
    }

    /** A rare red alarm lamp high on the numbered half of a pillar face. */
    @Nullable
    private static Direction alarmLampFace(long seed, int x, int z) {
        int mx = Math.floorMod(x, GRID);
        int mz = Math.floorMod(z, GRID);
        Direction face;
        int baseX;
        int baseZ;
        if (mx == 0 && mz == 7) { face = Direction.NORTH; baseX = x; baseZ = z + 1; }
        else if (mx == 2 && mz == 0) { face = Direction.EAST; baseX = x - 2; baseZ = z; }
        else if (mx == 1 && mz == 2) { face = Direction.SOUTH; baseX = x - 1; baseZ = z - 2; }
        else if (mx == 7 && mz == 1) { face = Direction.WEST; baseX = x + 1; baseZ = z - 1; }
        else return null;
        boolean alarm = Hash.unit(seed, Math.floorDiv(baseX, GRID), Math.floorDiv(baseZ, GRID), 45 + face.get2DDataValue()) < 0.04;
        return alarm ? face : null;
    }

    /** Round wet puddles: some 6x6 cells hold one, centred with a random radius. */
    private static boolean puddle(long seed, int x, int z) {
        int px = Math.floorDiv(x, 6);
        int pz = Math.floorDiv(z, 6);
        long h = Hash.of(seed, px, pz, 23);
        if (Hash.unit(h) >= 0.22) {
            return false;
        }
        int ox = x - (px * 6 + 2 + Hash.range(Hash.mix(h), 2));
        int oz = z - (pz * 6 + 2 + Hash.range(Hash.mix(h ^ 7), 2));
        int radiusSq = 2 + Hash.range(Hash.mix(h ^ 9), 5);
        return ox * ox + oz * oz <= radiusSq;
    }

    /** Stacks of 1-3 crates in the north-west corner of some bays (2x2 footprint). */
    private int crateHeight(long seed, int x, int z) {
        int mx = Math.floorMod(x, GRID);
        int mz = Math.floorMod(z, GRID);
        if (mx < 2 || mx > 3 || mz < 2 || mz > 3) {
            return 0;
        }
        if (Hash.unit(seed, Math.floorDiv(x, GRID), Math.floorDiv(z, GRID), 29) >= 0.1
                || exits().distance(seed, x, z) <= CLEARING_RADIUS) {
            return 0;
        }
        return 1 + Hash.range(Hash.of(seed, x, z, 28), 3);
    }
}
