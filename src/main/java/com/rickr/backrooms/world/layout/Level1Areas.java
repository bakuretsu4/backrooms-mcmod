package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.AxisBlock;
import com.rickr.backrooms.block.CeilingTubeBlock;
import com.rickr.backrooms.block.FurnitureBlock;
import com.rickr.backrooms.block.WallDecalBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * The parts of Level 1 that aren't the open halls themselves (those are {@link Level1Layout}):
 * <ul>
 *   <li><b>Sectors</b> (160-block regions, wikidot): the grey parking sector ("Aquila"), the warm
 *       warehouse sector ("Gild": hazard-striped pillars, far more crates) and a decrepit one
 *       (cracked concrete, puddles, ceiling pipes, rebar, dead lights).</li>
 *   <li><b>Corridor blocks</b> (wikidot's "corridors"): a quarter of the 64x64 zones are walled
 *       off. From the halls you get in through push-bar fire doors under a glowing EXIT sign; inside
 *       is a grid of tight white corridors lined with small rooms (mattresses, a supply locker, a
 *       few crates, a dropped backpack). Neighbouring corridor blocks open into each other.</li>
 *   <li><b>Storage lockers</b> (fandom): a walled hallway of roll-up shutters off the halls. Most
 *       are shut (behind a few, something growls), some are rolled up with supplies inside, and
 *       at the far end a white door that never opens.</li>
 *   <li><b>Ramps</b>: now and then a car ramp leads down from the hall into a lower floor, a 5x5
 *       bay pillar hall under the main floor, dimmer and damper.</li>
 * </ul>
 * Everything here is pure geometry from the seed; where lockers and ramps may go is decided by
 * {@link Level1Layout} (it knows about exits, safe rooms and anomalies).
 */
final class Level1Areas {
    static final int GRID = Level1Layout.GRID;
    private static final int PILLAR = Level1Layout.PILLAR;

    // ------------------------------------------------------------------------------------------
    // Sectors
    // ------------------------------------------------------------------------------------------

    static final int AQUILA = 0;
    static final int GILD = 1;
    static final int RUST = 2;
    private static final int SECTOR_SIZE = 160;

    static int sector(long seed, int x, int z) {
        double s = LevelLayout.zone(seed, x, z, SECTOR_SIZE, 77);
        return s < 0.45 ? AQUILA : s < 0.75 ? GILD : RUST;
    }

    /**
     * A sign for a pillar in this sector: English everywhere, plus Spanish and French in the
     * warehouse sector and German in the decrepit one (fandom: "signs changing languages").
     * Variants: 0/1 EXIT, 2/3 SALIDA, 4/5 SORTIE, 6/7 AUSGANG; even = arrow right, odd = left.
     */
    static int signVariant(int sector, long h) {
        int arrow = (int) (h & 1);
        double r = Hash.unit(Hash.mix(h));
        int word = switch (sector) {
            case GILD -> r < 0.3 ? 0 : r < 0.65 ? 1 : 2;
            case RUST -> r < 0.4 ? 0 : 3;
            default -> 0;
        };
        return word * 2 + arrow;
    }

    // ------------------------------------------------------------------------------------------
    // Corridor blocks
    // ------------------------------------------------------------------------------------------

    static final int ZONE = 64;
    private static final double CORRIDOR_SHARE = 0.25;
    /** Share of corridor ends on a hall-facing side that have an EXIT door (the rest are wall). */
    private static final double EXIT_DOOR_SHARE = 0.5;
    /** Share of corridor stretches blocked halfway by a wall (dead ends). */
    private static final double CLOSURE_SHARE = 0.12;

    static final int C_FLOOR = 0;
    static final int C_WALL = 1;
    static final int C_PERIMETER = 2;
    static final int C_EXIT_DOOR = 3;
    static final int C_ROOM = 4;
    static final int C_DOORWAY = 5;

    static boolean corridorZone(long seed, int x, int z) {
        return LevelLayout.zone(seed, x, z, ZONE, 22) < CORRIDOR_SHARE;
    }

    /** -1 off the corridor lanes; else the lane (0 or 1) of the 2-wide corridor at inner position l (0..61). */
    private static int lane(int l) {
        if (l < 0 || l > ZONE - 3) {
            return -1;
        }
        int m = l % 12;
        return m == 4 ? 0 : m == 5 ? 1 : -1;
    }

    /** What a column of a corridor block is (C_* constant). */
    static int corridorCell(long seed, int x, int z) {
        int zx = Math.floorDiv(x, ZONE);
        int zz = Math.floorDiv(z, ZONE);
        int lx = Math.floorMod(x, ZONE);
        int lz = Math.floorMod(z, ZONE);
        boolean edgeX = lx == 0 || lx == ZONE - 1;
        boolean edgeZ = lz == 0 || lz == ZONE - 1;
        if (edgeX && edgeZ) {
            return C_PERIMETER;
        }
        if (edgeX || edgeZ) {
            int inner = (edgeX ? lz : lx) - 1;
            if (lane(inner) < 0) {
                return C_PERIMETER;
            }
            int nx = zx + (edgeX ? (lx == 0 ? -1 : 1) : 0);
            int nz = zz + (edgeZ ? (lz == 0 ? -1 : 1) : 0);
            if (corridorZone(seed, nx * ZONE, nz * ZONE)) {
                return C_FLOOR; // the corridor runs on into the next block
            }
            int side = edgeX ? (lx == 0 ? 0 : 1) : (lz == 0 ? 2 : 3);
            return Hash.unit(seed, zx * 4 + side, zz, 7400 + inner / 12) < EXIT_DOOR_SHARE ? C_EXIT_DOOR : C_PERIMETER;
        }
        int u = lx - 1;
        int v = lz - 1;
        boolean cu = lane(u) >= 0;
        boolean cv = lane(v) >= 0;
        if (cu && cv) {
            return C_FLOOR;
        }
        if (cu) {
            return closed(seed, zx, zz, 0, u / 12, v) ? C_WALL : C_FLOOR;
        }
        if (cv) {
            return closed(seed, zx, zz, 1, v / 12, u) ? C_WALL : C_FLOOR;
        }
        int bu = Math.floorMod(u % 12 - 6, 12);
        int bv = Math.floorMod(v % 12 - 6, 12);
        boolean wu = bu == 0 || bu == 4 || bu == 9;
        boolean wv = bv == 0 || bv == 4 || bv == 9;
        if (!wu && !wv) {
            return C_ROOM;
        }
        if (wu && wv) {
            return C_WALL;
        }
        if (wu && bu != 4 && isDoorway(seed, zx, zz, u, v, true)) {
            return C_DOORWAY;
        }
        if (wv && bv != 4 && isDoorway(seed, zx, zz, v, u, false)) {
            return C_DOORWAY;
        }
        return C_WALL;
    }

    /** A wall across the middle of one corridor stretch (between two crossings). */
    private static boolean closed(long seed, int zx, int zz, int axis, int laneIndex, int t) {
        return t % 12 == 10 && Hash.unit(seed, zx * 8 + laneIndex * 2 + axis, zz * 8 + Math.floorDiv(t - 6, 12), 7401) < CLOSURE_SHARE;
    }

    /**
     * A small room between the corridors: block index (bu: which 10x10 block between corridor
     * lanes along u, from -1) and quadrant (q: 0 = the half next to the lower corridor), per axis.
     */
    private record Room(int zx, int zz, int blockU, int blockV, int qU, int qV) {
        int start(boolean u) {
            return 6 + 12 * (u ? blockU : blockV);
        }

        /** Inner cells of the room along one axis (inclusive), clamped to the block's inside. */
        int lo(boolean u) {
            return Math.max(0, start(u) + ((u ? qU : qV) == 0 ? 1 : 5));
        }

        int hi(boolean u) {
            return Math.min(ZONE - 3, start(u) + ((u ? qU : qV) == 0 ? 3 : 8));
        }

        /** The wall between this room and its corridor on one axis. */
        int corridorWall(boolean u) {
            return start(u) + ((u ? qU : qV) == 0 ? 0 : 9);
        }

        /** The room's own cells next to its middle wall (furthest from both corridors). */
        int far(boolean u) {
            return (u ? qU : qV) == 0 ? hi(u) : lo(u);
        }

        long hash(long seed) {
            return Hash.of(seed, zx * 64 + blockU * 2 + qU + 8, zz * 64 + blockV * 2 + qV + 8, 7410);
        }
    }

    private static Room roomAt(int zx, int zz, int u, int v) {
        int bu = Math.floorMod(u % 12 - 6, 12);
        int bv = Math.floorMod(v % 12 - 6, 12);
        return new Room(zx, zz, Math.floorDiv(u - 6, 12), Math.floorDiv(v - 6, 12), bu < 4 ? 0 : 1, bv < 4 ? 0 : 1);
    }

    /**
     * The doorway of a room: which of its two corridor walls (true = the one across u) and where
     * along it, or null when neither wall reaches a corridor (a room cut off by the block's edge).
     */
    @Nullable
    private static int[] door(long seed, Room room) {
        boolean validU = room.corridorWall(true) >= 0 && room.corridorWall(true) <= ZONE - 3 && room.lo(false) <= room.hi(false);
        boolean validV = room.corridorWall(false) >= 0 && room.corridorWall(false) <= ZONE - 3 && room.lo(true) <= room.hi(true);
        long h = room.hash(seed);
        boolean useU = validU && (!validV || Hash.unit(h) < 0.5);
        if (!useU && !validV) {
            return null;
        }
        boolean alongU = !useU; // the door's position runs along the other axis
        int lo = room.lo(alongU);
        int hi = room.hi(alongU);
        return new int[] {useU ? 0 : 1, useU ? room.corridorWall(true) : room.corridorWall(false), lo + Hash.range(Hash.mix(h ^ 5), hi - lo + 1)};
    }

    /** True if wall cell (wallCoord along its axis, other) is the doorway of the room it closes. */
    private static boolean isDoorway(long seed, int zx, int zz, int wallCoord, int other, boolean wallAcrossU) {
        int bw = Math.floorMod(wallCoord % 12 - 6, 12);
        // The room on the inside of this corridor wall.
        int roomCoord = bw == 0 ? wallCoord + 1 : wallCoord - 1;
        Room room = wallAcrossU ? roomAt(zx, zz, roomCoord, other) : roomAt(zx, zz, other, roomCoord);
        int[] d = door(seed, room);
        return d != null && d[0] == (wallAcrossU ? 0 : 1) && d[1] == wallCoord && d[2] == other;
    }

    /** What stands in a room cell (null for nothing): mattresses, a locker, crates or a backpack. */
    @Nullable
    private static BlockState roomThing(long seed, int x, int z, int u, int v, int floor, int[] height) {
        Room room = roomAt(Math.floorDiv(x, ZONE), Math.floorDiv(z, ZONE), u, v);
        long h = room.hash(seed);
        double r = Hash.unit(Hash.mix(h ^ 9));
        int fu = room.far(true);
        int fv = room.far(false);
        // Facing away from the middle walls, into the room.
        Direction intoU = room.qU() == 0 ? Direction.WEST : Direction.EAST;
        Direction intoV = room.qV() == 0 ? Direction.NORTH : Direction.SOUTH;
        height[0] = 1;
        if (r < 0.28) {
            // One or two mattresses along the far wall.
            boolean second = Hash.unit(Hash.mix(h ^ 11)) < 0.6;
            int nextV = fv + (room.qV() == 0 ? -1 : 1);
            if (u == fu && (v == fv || second && v == nextV)) {
                return ModBlocks.L1_MATTRESS.get().defaultBlockState().setValue(FurnitureBlock.FACING, intoU);
            }
        } else if (r < 0.38) {
            if (u == fu && v == fv) {
                return ModBlocks.SUPPLY_LOCKER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, intoU);
            }
        } else if (r < 0.47) {
            if (u == fu && v == fv) {
                height[0] = 1 + Hash.range(Hash.mix(h ^ 13), 2);
                return ModBlocks.SUPPLY_CRATE.get().defaultBlockState();
            }
        } else if (r < 0.495) {
            if (u == fu && v == fv) {
                return ModBlocks.BACKPACK.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, intoV);
            }
        }
        return null;
    }

    static boolean corridorWalkable(long seed, int x, int z) {
        int cell = corridorCell(seed, x, z);
        if (cell == C_FLOOR || cell == C_DOORWAY) {
            return true;
        }
        if (cell != C_ROOM) {
            return false;
        }
        return roomThing(seed, x, z, Math.floorMod(x, ZONE) - 1, Math.floorMod(z, ZONE) - 1, 0, new int[1]) == null;
    }

    static void fillCorridors(long seed, int x, int z, int floor, int ceiling, LevelLayout.Column out) {
        BlockState concrete = ModBlocks.CONCRETE_WALL.get().defaultBlockState();
        BlockState white = ModBlocks.STAIRWELL_WALL.get().defaultBlockState();
        out.set(floor, ModBlocks.CONCRETE_FLOOR.get().defaultBlockState());
        out.set(ceiling, concrete);
        int cell = corridorCell(seed, x, z);
        int lx = Math.floorMod(x, ZONE);
        int lz = Math.floorMod(z, ZONE);
        int u = lx - 1;
        int v = lz - 1;
        switch (cell) {
            case C_PERIMETER -> fill(out, floor + 1, ceiling - 1, concrete);
            case C_WALL -> fill(out, floor + 1, ceiling - 1, white);
            case C_EXIT_DOOR -> {
                boolean edgeX = lx == 0 || lx == ZONE - 1;
                Direction out1 = edgeX ? (lx == 0 ? Direction.WEST : Direction.EAST) : (lz == 0 ? Direction.NORTH : Direction.SOUTH);
                int laneIndex = lane((edgeX ? lz : lx) - 1);
                BlockState door = ModBlocks.FIRE_DOOR.get().defaultBlockState()
                        .setValue(DoorBlock.FACING, out1)
                        .setValue(DoorBlock.HINGE, laneIndex == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
                out.set(floor + 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                out.set(floor + 2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                out.set(floor + 3, ModBlocks.EXIT_SIGN.get().defaultBlockState());
                fill(out, floor + 4, ceiling - 1, concrete);
            }
            case C_ROOM -> {
                int[] height = new int[1];
                BlockState thing = roomThing(seed, x, z, u, v, floor, height);
                if (thing != null) {
                    for (int i = 0; i < height[0]; i++) {
                        out.set(floor + 1 + i, thing);
                    }
                }
                // Half the rooms have a tube in the middle.
                Room room = roomAt(Math.floorDiv(x, ZONE), Math.floorDiv(z, ZONE), u, v);
                if (u == (room.lo(true) + room.hi(true)) / 2 && v == (room.lo(false) + room.hi(false)) / 2
                        && Hash.unit(Hash.mix(room.hash(seed) ^ 17)) < 0.5) {
                    out.set(ceiling - 1, tube(Direction.Axis.X));
                }
            }
            case C_FLOOR -> {
                // Dim tubes down the corridors, one every six blocks, some dead.
                boolean cu = lane(u) == 0;
                boolean cv = lane(v) == 0;
                int along = cu ? v : u;
                if ((cu ^ cv) && (along % 12 == 8 || along % 12 == 2) && Hash.unit(seed, x, z, 7402) < 0.7) {
                    out.set(ceiling - 1, tube(cu ? Direction.Axis.Z : Direction.Axis.X));
                }
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Storage lockers
    // ------------------------------------------------------------------------------------------

    /** One storage-locker hallway: LENGTH along its axis, WIDTH across; the entrance is at the min end. */
    record LockerSite(int minX, int minZ, boolean alongX, long salt) {
        static final int LENGTH = 37;
        static final int WIDTH = 11;

        int maxX() {
            return minX + (alongX ? LENGTH : WIDTH) - 1;
        }

        int maxZ() {
            return minZ + (alongX ? WIDTH : LENGTH) - 1;
        }

        boolean contains(int x, int z) {
            return x >= minX && x <= maxX() && z >= minZ && z <= maxZ();
        }

        int along(int x, int z) {
            return alongX ? x - minX : z - minZ;
        }

        int across(int x, int z) {
            return alongX ? z - minZ : x - minX;
        }
    }

    private static final int AISLE_LO = 4;
    private static final int AISLE_HI = 6;

    /** A locker unit: 0 = closed, 1 = closed and growling, 2 = rolled up. */
    private static int lockerUnit(LockerSite site, int unit, int side) {
        long h = Hash.mix(site.salt() ^ (unit * 31L + side * 7L + 1));
        if (Hash.unit(h) < 0.3) {
            return 2;
        }
        return Hash.unit(Hash.mix(h ^ 3)) < 0.14 ? 1 : 0;
    }

    static boolean lockersWalkable(LockerSite site, int x, int z) {
        int a = site.along(x, z);
        int w = site.across(x, z);
        if (w >= AISLE_LO && w <= AISLE_HI) {
            return a < LockerSite.LENGTH - 1;
        }
        return false;
    }

    static void fillLockers(LockerSite site, int x, int z, int floor, int ceiling, LevelLayout.Column out) {
        BlockState concrete = ModBlocks.CONCRETE_WALL.get().defaultBlockState();
        out.set(floor, ModBlocks.CONCRETE_FLOOR.get().defaultBlockState());
        out.set(ceiling, concrete);
        int a = site.along(x, z);
        int w = site.across(x, z);
        int last = LockerSite.LENGTH - 1;
        boolean aisle = w >= AISLE_LO && w <= AISLE_HI;
        if (w == 0 || w == LockerSite.WIDTH - 1) {
            fill(out, floor + 1, ceiling - 1, concrete);
            return;
        }
        if (a == last) {
            fill(out, floor + 1, ceiling - 1, concrete);
            if (w == 5) {
                // The white door at the end: it never opens.
                Direction back = site.alongX() ? Direction.WEST : Direction.NORTH;
                out.set(floor + 1, ModBlocks.L1_LOCKED_DOOR.get().defaultBlockState().setValue(WallDecalBlock.FACING, back));
                out.set(floor + 2, ModBlocks.L1_LOCKED_DOOR_TOP.get().defaultBlockState().setValue(WallDecalBlock.FACING, back));
            }
            return;
        }
        if (a == 0 && !aisle) {
            fill(out, floor + 1, ceiling - 1, concrete); // the entrance is just the aisle
            return;
        }
        if (aisle) {
            if (w == 5 && a % 6 == 3) {
                out.set(ceiling - 1, tube(site.alongX() ? Direction.Axis.X : Direction.Axis.Z));
            }
            return;
        }
        // The locker rows: units 3 wide with a concrete divider between them.
        if (a % 4 == 0) {
            fill(out, floor + 1, ceiling - 1, concrete);
            return;
        }
        int unit = a / 4;
        int side = w < AISLE_LO ? 0 : 1;
        int kind = lockerUnit(site, unit, side);
        boolean front = w == AISLE_LO - 1 || w == AISLE_HI + 1;
        if (front) {
            fill(out, floor + 4, ceiling - 1, concrete);
            if (kind == 2) {
                out.set(floor + 3, ModBlocks.L1_SHUTTER_ROLLED.get().defaultBlockState());
            } else {
                BlockState shutter = ModBlocks.L1_SHUTTER.get().defaultBlockState();
                out.set(floor + 1, shutter);
                out.set(floor + 2, kind == 1 && a % 4 == 2 ? ModBlocks.L1_SHUTTER_GROWL.get().defaultBlockState() : shutter);
                out.set(floor + 3, shutter);
            }
            return;
        }
        // Inside a unit: rolled-up ones may hold something.
        fill(out, floor + 4, ceiling - 1, concrete);
        boolean back = w == 1 || w == LockerSite.WIDTH - 2;
        if (kind == 2 && back && a % 4 == 2) {
            double r = Hash.unit(Hash.mix(site.salt() ^ (unit * 131L + side)));
            if (r < 0.4) {
                out.set(floor + 1, ModBlocks.SUPPLY_CRATE.get().defaultBlockState());
            } else if (r < 0.52) {
                Direction toAisle = site.alongX() ? (side == 0 ? Direction.SOUTH : Direction.NORTH) : (side == 0 ? Direction.EAST : Direction.WEST);
                out.set(floor + 1, ModBlocks.BACKPACK.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, toAisle));
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Ramps down to the lower floor
    // ------------------------------------------------------------------------------------------

    /** A lower floor of 5x5 bays under the hall, its north-west corner on the pillar grid. */
    record RampSite(int minX, int minZ, long salt) {
        static final int SIZE = 5 * GRID;

        boolean contains(int x, int z) {
            return x >= minX && x < minX + SIZE && z >= minZ && z < minZ + SIZE;
        }

        /** Ramp position 0..14 down the slope (toward +x), or -1 off the ramp. It fills the width of the middle bay row. */
        int rampIndex(int x, int z) {
            int a = x - (minX + PILLAR);
            int w = z - (minZ + 2 * GRID + PILLAR);
            return a >= 0 && a <= 14 && w >= 0 && w < GRID - PILLAR ? a : -1;
        }

        /** The curb along both sides of the ramp's opening in the floor above. */
        boolean curb(int x, int z) {
            int a = x - (minX + PILLAR);
            int lz = z - minZ;
            return a >= 1 && a <= 12 && (lz == 2 * GRID + PILLAR - 1 || lz == 3 * GRID);
        }
    }

    /** The lower floor's height: its floor block (the main floor block above is its ceiling). */
    static int lowerFloor(int floor) {
        return floor - 7;
    }

    /** The lower floor under the hall (everything below the main floor's own block). */
    static void fillLowerFloor(long seed, RampSite site, int x, int z, int floor, LevelLayout.Column out) {
        int low = lowerFloor(floor);
        BlockState concrete = ModBlocks.CONCRETE_WALL.get().defaultBlockState();
        boolean wet = Hash.unit(seed, Math.floorDiv(x, 5), Math.floorDiv(z, 5), 7420) < 0.3;
        out.set(low, wet ? ModBlocks.WET_CONCRETE_FLOOR.get().defaultBlockState() : ModBlocks.CONCRETE_FLOOR.get().defaultBlockState());
        int lx = x - site.minX();
        int lz = z - site.minZ();
        if (lx == 0 || lz == 0 || lx == RampSite.SIZE - 1 || lz == RampSite.SIZE - 1) {
            fill(out, low + 1, floor - 1, concrete);
            return;
        }
        int mx = Math.floorMod(x, GRID);
        int mz = Math.floorMod(z, GRID);
        if (mx < PILLAR && mz < PILLAR) {
            out.set(low + 1, ModBlocks.GARAGE_PILLAR.get().defaultBlockState());
            fill(out, low + 2, floor - 1, concrete);
            return;
        }
        // Fewer lights down here, hung under the floor above.
        if (mz == 7 && mx >= 6 && mx <= 8 && Hash.unit(seed, Math.floorDiv(x, GRID), Math.floorDiv(z, GRID), 7421) < 0.45) {
            out.set(floor - 1, hanging(Direction.Axis.X));
        }
    }

    /**
     * One column of the ramp: a solid concrete wedge stepping down one block every two (stairs
     * where it rises, so it walks like a slope), from the main floor to the lower floor.
     */
    static void fillRamp(int a, int floor, int ceiling, LevelLayout.Column out) {
        BlockState concrete = ModBlocks.CONCRETE_WALL.get().defaultBlockState();
        int top = floor - (a + 1) / 2;
        int low = lowerFloor(floor);
        for (int y = low; y < top; y++) {
            out.set(y, concrete);
        }
        out.set(top, a % 2 == 0 && top > low
                ? ModBlocks.CONCRETE_STAIRS.get().defaultBlockState().setValue(StairBlock.FACING, Direction.WEST)
                : ModBlocks.CONCRETE_FLOOR.get().defaultBlockState());
        out.set(ceiling, concrete);
        if (a % 4 == 1) {
            out.set(ceiling - 1, hanging(Direction.Axis.Z));
        }
    }

    // ------------------------------------------------------------------------------------------

    private static void fill(LevelLayout.Column out, int from, int to, BlockState state) {
        for (int y = from; y <= to; y++) {
            out.set(y, state);
        }
    }

    /** The flat tube strips of the corridors and the lockers (low ceilings). */
    static BlockState tube(Direction.Axis axis) {
        return ModBlocks.CEILING_TUBE.get().defaultBlockState().setValue(CeilingTubeBlock.AXIS, axis);
    }

    /** The fixtures hanging in the tall halls, on the ramps and over the lower floor. */
    static BlockState hanging(Direction.Axis axis) {
        return ModBlocks.L1_HANGING_LIGHT.get().defaultBlockState().setValue(AxisBlock.AXIS, axis);
    }

    private Level1Areas() {}
}
