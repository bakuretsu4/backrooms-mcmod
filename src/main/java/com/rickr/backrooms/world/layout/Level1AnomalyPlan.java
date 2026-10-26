package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.CeilingTubeBlock;
import com.rickr.backrooms.block.CrudePaintingBlock;
import com.rickr.backrooms.block.FloorPaintingBlock;
import com.rickr.backrooms.block.SupplyContainerBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.Nullable;

/**
 * The shapes of Level 1's four anomalous areas, as pure functions of their {@link AnomalySite}.
 * {@link Level1Layout} decides WHERE the sites go (its planners need the garage's own floor plan)
 * and builds them from these functions; the server behaviours ({@code world/anomaly/level1}) and the
 * client effects ({@code client/anomaly/level1}) use the very same functions to find the crate
 * spots, the painting spots, the puddles and where you are in the corridor. Everything here only
 * needs the site (its bounds, anchor, facing and salt), so the client can use it too.
 *
 * <ul>
 *   <li><b>Crate hall</b> ({@value #CRATE_HALL}): a 3x3-bay storage hall with racks in an open
 *       garage hall, where crates turn up behind you.</li>
 *   <li><b>Painting room</b> ({@value #PAINTING_ROOM}): a small closed room off the maze with
 *       crude paintings on its walls and floor.</li>
 *   <li><b>Fog puddles</b> ({@value #FOG_PUDDLES}): a patch of low fog with condensation
 *       puddles on the floor.</li>
 *   <li><b>Corrupted Corridor</b> ({@value #CORRUPTED_CORRIDOR}, Level 1.1): a 138-block-long
 *       straight corridor in five sectors.</li>
 * </ul>
 */
public final class Level1AnomalyPlan {
    public static final String CRATE_HALL = "level_1/crate_hall";
    public static final String PAINTING_ROOM = "level_1/painting_room";
    public static final String FOG_PUDDLES = "level_1/fog_puddles";
    public static final String CORRUPTED_CORRIDOR = "level_1/corrupted_corridor";
    public static final List<String> KINDS = List.of(CRATE_HALL, PAINTING_ROOM, FOG_PUDDLES, CORRUPTED_CORRIDOR);

    /** The crate halls, painting rooms and fog patches: at most one per 96x96 square, always inside it. */
    public static final int ROOM_CELL = 96;
    /** The Corrupted Corridor: at most one per 512x512 square (it is rare). */
    public static final int CORRIDOR_CELL = 512;

    private static final int GRID = 8;
    private static final int FLOOR = LevelLayout.FLOOR_Y;

    // ==========================================================================================
    // Crate hall
    // ==========================================================================================

    /** Bays along each side of a crate hall. */
    public static final int CRATE_HALL_CELLS = 3;
    /** Its footprint: 3 bays plus the pillar rows on both sides, 26 blocks. */
    public static final int CRATE_HALL_SIZE = CRATE_HALL_CELLS * GRID + 2;
    /** Floor spots (mx, mz inside the bay) where crates can turn up; never blocking an aisle completely. */
    private static final int[][] CRATE_FLOOR_SPOTS = {{2, 3}, {7, 3}, {2, 6}, {7, 6}, {4, 4}, {6, 4}};

    public static boolean isCrateHall(@Nullable AnomalySite site) {
        return site != null && CRATE_HALL.equals(site.kind());
    }

    /**
     * True for the columns inside a crate hall's outer pillar rows. There every partition is open
     * (one big storage hall) and the bays hold racks; the outer rows stay as the garage built them,
     * so the hall joins the rest of the level the same way any hall does.
     */
    public static boolean inCrateHall(@Nullable AnomalySite site, int x, int z) {
        if (!isCrateHall(site)) {
            return false;
        }
        int lx = x - site.minX();
        int lz = z - site.minZ();
        return lx >= 2 && lx < CRATE_HALL_SIZE - 2 && lz >= 2 && lz < CRATE_HALL_SIZE - 2;
    }

    /** The two rows of racks across each bay (mx, mz = position inside the 8-block grid cell). */
    public static boolean crateHallShelf(int mx, int mz) {
        return (mz == 3 || mz == 6) && mx >= 3 && mx <= 6;
    }

    /** One bay column of a crate hall (the caller lays the floor and the ceiling). */
    public static void fillCrateHallBay(int mx, int mz, int floor, int ceiling, LevelLayout.Column out) {
        if (crateHallShelf(mx, mz)) {
            BlockState shelf = ModBlocks.L1_STORAGE_SHELF.get().defaultBlockState();
            out.set(floor + 1, shelf);
            out.set(floor + 2, shelf);
        }
        // The crate-rich Gild sector is "better lit" than the rest: a tube over every middle aisle.
        if (mz == 4 && mx >= 3 && mx <= 6) {
            out.set(ceiling - 1, tube(Direction.Axis.X));
        }
    }

    /**
     * Every spot where a crate may appear: on top of each rack (they are two blocks tall) and a
     * few spots on the floor. Nothing else is ever built on these spots, so any crate found on one
     * was put there by the anomaly (that's how the server knows which crates it may take away).
     */
    public static List<BlockPos> crateSpots(AnomalySite site) {
        List<BlockPos> spots = new ArrayList<>();
        for (int bx = 0; bx < CRATE_HALL_CELLS; bx++) {
            for (int bz = 0; bz < CRATE_HALL_CELLS; bz++) {
                int x0 = site.minX() + bx * GRID;
                int z0 = site.minZ() + bz * GRID;
                for (int mx = 3; mx <= 6; mx++) {
                    spots.add(new BlockPos(x0 + mx, FLOOR + 3, z0 + 3));
                    spots.add(new BlockPos(x0 + mx, FLOOR + 3, z0 + 6));
                }
                for (int[] spot : CRATE_FLOOR_SPOTS) {
                    spots.add(new BlockPos(x0 + spot[0], FLOOR + 1, z0 + spot[1]));
                }
            }
        }
        return spots;
    }

    // ==========================================================================================
    // Painting room
    // ==========================================================================================

    /** One grid cell plus the pillar rows around it: 10 x 10 blocks, walls included. */
    public static final int PAINTING_ROOM_SIZE = GRID + 2;

    /** One spot for a painting: where, and which way a wall painting faces (null on the floor). */
    public record PaintingSpot(BlockPos pos, @Nullable Direction facing) {}

    /** The wall with the doorway (the site's facing). */
    public static Direction paintingDoorSide(AnomalySite site) {
        return Direction.from2DDataValue(site.facing());
    }

    /** True for the doorway: 2 wide, in the middle of the door wall ({@code lx, lz} = from the site's corner). */
    public static boolean paintingDoorway(AnomalySite site, int lx, int lz) {
        return switch (paintingDoorSide(site)) {
            case NORTH -> lz <= 1 && (lx == 4 || lx == 5);
            case SOUTH -> lz >= GRID && (lx == 4 || lx == 5);
            case WEST -> lx <= 1 && (lz == 4 || lz == 5);
            default -> lx >= GRID && (lz == 4 || lz == 5);
        };
    }

    /**
     * Which way the painting at local (lx, lz), height y faces, or null if that is not a painting
     * spot. Two pictures on each side wall and either side of the door, a row of four (and two
     * more above them) on the wall you face as you walk in, all at eye height.
     */
    @Nullable
    public static Direction wallPaintingFacing(AnomalySite site, int lx, int lz, int y) {
        Direction wall;
        int along;
        if (lz == 2 && lx >= 3 && lx <= 6) {
            wall = Direction.NORTH;
            along = lx;
        } else if (lz == GRID - 1 && lx >= 3 && lx <= 6) {
            wall = Direction.SOUTH;
            along = lx;
        } else if (lx == 2 && lz >= 3 && lz <= 6) {
            wall = Direction.WEST;
            along = lz;
        } else if (lx == GRID - 1 && lz >= 3 && lz <= 6) {
            wall = Direction.EAST;
            along = lz;
        } else {
            return null;
        }
        boolean spot;
        if (wall == paintingDoorSide(site).getOpposite()) {
            spot = y == FLOOR + 2 || y == FLOOR + 3 && (along == 4 || along == 5);
        } else {
            spot = y == FLOOR + 2 && (along == 3 || along == 6);
        }
        return spot ? wall.getOpposite() : null; // a painting faces away from its wall
    }

    /** The drawings on the floor ("a room with paintings on the walls and floor"). */
    public static boolean floorPaintingSpot(int lx, int lz) {
        return lx == 3 && lz == 3 || lx == 6 && lz == 5 || lx == 4 && lz == 6 || lx == 5 && lz == 3;
    }

    /** What a painting spot holds when the room is generated, or null (some start out empty). */
    @Nullable
    public static BlockState initialPainting(AnomalySite site, int x, int y, int z) {
        int lx = x - site.minX();
        int lz = z - site.minZ();
        long h = Hash.of(site.salt(), x, z, 91 + y);
        if (Hash.unit(h) < 0.15) {
            return null;
        }
        if (y == FLOOR + 1) {
            return floorPaintingSpot(lx, lz) ? floorPainting(Hash.range(Hash.mix(h), FloorPaintingBlock.VARIANTS)) : null;
        }
        Direction facing = wallPaintingFacing(site, lx, lz, y);
        return facing == null ? null : wallPainting(facing, Hash.range(Hash.mix(h), CrudePaintingBlock.VARIANTS));
    }

    /** Every painting spot of the room (for the server, which changes them). */
    public static List<PaintingSpot> paintingSpots(AnomalySite site) {
        List<PaintingSpot> spots = new ArrayList<>();
        for (int lx = 2; lx < GRID; lx++) {
            for (int lz = 2; lz < GRID; lz++) {
                int x = site.minX() + lx;
                int z = site.minZ() + lz;
                if (floorPaintingSpot(lx, lz)) {
                    spots.add(new PaintingSpot(new BlockPos(x, FLOOR + 1, z), null));
                }
                for (int y = FLOOR + 2; y <= FLOOR + 3; y++) {
                    Direction facing = wallPaintingFacing(site, lx, lz, y);
                    if (facing != null) {
                        spots.add(new PaintingSpot(new BlockPos(x, y, z), facing));
                    }
                }
            }
        }
        return spots;
    }

    public static BlockState wallPainting(Direction facing, int variant) {
        return ModBlocks.L1_PAINTING.get().defaultBlockState()
                .setValue(CrudePaintingBlock.FACING, facing)
                .setValue(CrudePaintingBlock.VARIANT, variant);
    }

    public static BlockState floorPainting(int variant) {
        return ModBlocks.L1_FLOOR_PAINTING.get().defaultBlockState().setValue(FloorPaintingBlock.VARIANT, variant);
    }

    // ==========================================================================================
    // Fog puddles
    // ==========================================================================================

    /** Puddles are round blobs, at most one per 5x5 patch of the zone. */
    private static final int FOG_BLOB = 5;

    public static boolean isFog(@Nullable AnomalySite site) {
        return site != null && FOG_PUDDLES.equals(site.kind());
    }

    /**
     * Where the fog's condensation gathers. These spots have wet concrete underneath; a film of
     * water ({@code l1_condensation}) lies on some of them and comes and goes over time
     * ("puddles on the floor in inconsistent areas").
     */
    public static boolean fogPuddleSpot(@Nullable AnomalySite site, int x, int z) {
        if (!isFog(site) || !site.contains(x, z)) {
            return false;
        }
        int lx = x - site.minX();
        int lz = z - site.minZ();
        int px = Math.floorDiv(lx, FOG_BLOB);
        int pz = Math.floorDiv(lz, FOG_BLOB);
        long h = Hash.of(site.salt(), px, pz, 63);
        if (Hash.unit(h) >= 0.5) {
            return false;
        }
        int ox = lx - (px * FOG_BLOB + 1 + Hash.range(Hash.mix(h), 3));
        int oz = lz - (pz * FOG_BLOB + 1 + Hash.range(Hash.mix(h ^ 5), 3));
        int radiusSq = 1 + Hash.range(Hash.mix(h ^ 9), 4);
        return ox * ox + oz * oz <= radiusSq;
    }

    /** The puddles that are already there when the zone is generated. */
    public static boolean fogPuddleAtStart(@Nullable AnomalySite site, int x, int z) {
        return fogPuddleSpot(site, x, z) && Hash.unit(Hash.of(site.salt(), x, z, 64)) < 0.6;
    }

    // ==========================================================================================
    // The Corrupted Corridor (Level 1.1)
    // ==========================================================================================
    //
    // Coordinates along the corridor: t counts blocks from the entrance end (t = 0 is the outer end
    // wall), a counts across it (a = 0 is the outer wall on the doorway side, 7 the far outer wall).
    // a = 0 and 6..7 are the garage's concrete it was "carved" into; a = 1 and 5 are its own white
    // cinderblock walls, a = 2..4 the 3-wide floor. The site's anchor is the corner t = 0, a = 0
    // and its facing is the way you walk in (east, west, south or north).

    /** 17 bays plus the closing pillar row: 138 blocks. */
    public static final int CORRIDOR_LENGTH = 17 * GRID + 2;
    /** The doorway in the side wall (t = 3..4), 3 blocks tall. */
    public static final int DOOR_T = 3;
    /** First t of each sector (Sector 1 starts at the door). */
    public static final int S2 = 24;
    public static final int S3 = 76;
    public static final int S4 = 100;
    public static final int S5 = 128;
    /** Sector 2's repeating section: three identical copies of a 12-block stretch. */
    public static final int LOOP_START = 38;
    public static final int LOOP_LENGTH = 12;
    /** Walking past the middle of the second copy quietly puts you back in the middle of the first. */
    public static final int LOOP_TRIGGER = LOOP_START + LOOP_LENGTH + LOOP_LENGTH / 2;
    /** The beckoning light fills t = 134..135; stepping into t >= 133 takes you. */
    public static final int LIGHT_T = CORRIDOR_LENGTH - 4;
    public static final int LIGHT_TRIGGER = LIGHT_T - 1;
    /** Length of one undulation of Sector 4 (a bulge, then a stretch of false floor). */
    private static final int WAVE = 7;

    public static boolean isCorridor(@Nullable AnomalySite site) {
        return site != null && CORRUPTED_CORRIDOR.equals(site.kind());
    }

    public static Direction corridorFacing(AnomalySite site) {
        return Direction.from2DDataValue(site.facing());
    }

    /** Block distance along the corridor from its entrance end. */
    public static int corridorT(AnomalySite site, int x, int z) {
        return switch (corridorFacing(site)) {
            case EAST -> x - site.anchorX();
            case WEST -> site.anchorX() - x;
            case SOUTH -> z - site.anchorZ();
            default -> site.anchorZ() - z;
        };
    }

    /** Same as {@link #corridorT} but exact (fractions of a block), for a player's position. */
    public static double corridorTExact(AnomalySite site, double x, double z) {
        return switch (corridorFacing(site)) {
            case EAST -> x - site.anchorX();
            case WEST -> site.anchorX() + 1 - x;
            case SOUTH -> z - site.anchorZ();
            default -> site.anchorZ() + 1 - z;
        };
    }

    /** Position across the corridor, 0 (doorway side) .. 7. */
    public static int corridorA(AnomalySite site, int x, int z) {
        return corridorFacing(site).getAxis() == Direction.Axis.X ? z - site.minZ() : x - site.minX();
    }

    /** World x of corridor position (t, a). */
    public static int corridorX(AnomalySite site, int t, int a) {
        return switch (corridorFacing(site)) {
            case EAST -> site.anchorX() + t;
            case WEST -> site.anchorX() - t;
            default -> site.minX() + a;
        };
    }

    /** World z of corridor position (t, a). */
    public static int corridorZ(AnomalySite site, int t, int a) {
        return switch (corridorFacing(site)) {
            case SOUTH -> site.anchorZ() + t;
            case NORTH -> site.anchorZ() - t;
            default -> site.minZ() + a;
        };
    }

    /** Which sector (1..5) a t is in. */
    public static int corridorSector(int t) {
        return t < S2 ? 1 : t < S3 ? 2 : t < S4 ? 3 : t < S5 ? 4 : 5;
    }

    /** Sector 2's spots of wall you can walk into (and get thrown out somewhere else in Level 1). */
    public static boolean corridorNoclip(int t, int a) {
        return a == 5 && (t == 29 || t == 30) || a == 1 && (t == 74 || t == 75);
    }

    /** Sector 4's walls bulge into the corridor, first one side, then the other ("undulating"). */
    public static boolean corridorBulge(int t, int a) {
        if (t < S4 || t >= S5) {
            return false;
        }
        int wave = (t - S4) / WAVE;
        int off = (t - S4) % WAVE;
        return (off == 1 || off == 2) && a == (wave % 2 == 0 ? 2 : 4);
    }

    /**
     * Sector 4's floor that is not there: after each bulge, three rows where only one of the three
     * columns (a different one each time) really holds you. They look exactly like the rest.
     */
    public static boolean corridorVoid(AnomalySite site, int t, int a) {
        if (t < S4 || t >= S5 || a < 2 || a > 4 || (t - S4) % WAVE < 4) {
            return false;
        }
        int safe = 2 + Hash.range(Hash.of(site.salt(), (t - S4) / WAVE, 0, 77), 3);
        return a != safe;
    }

    /** Sector 2's slanted floor: 0 = flat, 1 = a half-block step up, 2 = a full block up. */
    private static int corridorRamp(int t, int a) {
        if (a < 2 || a > 4) {
            return 0;
        }
        if (t == 26 || t == 30) {
            return 1; // a hump along the corridor...
        }
        if (t >= 27 && t <= 29) {
            return 2;
        }
        if (t >= 33 && t <= 36) {
            return a == 3 ? 1 : a == 4 ? 2 : 0; // ...then a stretch tilted sideways
        }
        return 0;
    }

    private static boolean inLoop(int t) {
        return t >= LOOP_START && t < LOOP_START + 3 * LOOP_LENGTH;
    }

    /** Ceiling tubes down the middle of the corridor (none at all in Sector 3). */
    private static boolean corridorTube(int t) {
        if (t < S2) {
            return t >= 6 && t % 5 == 1; // Sector 1's humming lights
        }
        if (t < S3) {
            if (inLoop(t)) {
                int u = (t - LOOP_START) % LOOP_LENGTH;
                return u == 2 || u == 8; // the same in every copy, so the copies match
            }
            return t == 24 || t == 31;
        }
        if (t < S4) {
            return false;
        }
        return t < S5 && (t - S4) % WAVE == 0;
    }

    /** Sector 3's unexplained bright patches in the floor. */
    private static boolean brightFloor(int t, int a) {
        return t == 80 && a == 3 || t == 91 && a == 2 || t == 95 && a == 4;
    }

    private static boolean outerShell(int t, int a) {
        return a == 0 || a >= 6 || t <= 0 || t >= CORRIDOR_LENGTH - 1;
    }

    private static boolean innerWall(int t, int a) {
        return a == 1 || a == 5 || t == 1 || t == CORRIDOR_LENGTH - 2;
    }

    /** One column of the corridor (it replaces the garage completely). */
    public static void fillCorridor(AnomalySite site, int x, int z, int floor, int ceiling, LevelLayout.Column out) {
        int t = corridorT(site, x, z);
        int a = corridorA(site, x, z);
        BlockState concrete = ModBlocks.CONCRETE_WALL.get().defaultBlockState();
        out.set(ceiling, concrete);
        if (outerShell(t, a)) {
            // The concrete it was carved into, with the doorway from the garage.
            out.set(floor, ModBlocks.CONCRETE_FLOOR.get().defaultBlockState());
            boolean doorway = a == 0 && (t == DOOR_T || t == DOOR_T + 1);
            for (int y = floor + 1; y < ceiling; y++) {
                if (!(doorway && y <= floor + 3)) {
                    out.set(y, concrete);
                }
            }
            return;
        }
        boolean mono = t >= S4 && t < S5;
        BlockState wall = (mono ? ModBlocks.L1_MONOCHROME_WALL : ModBlocks.L1_CINDERBLOCK).get().defaultBlockState();
        BlockState tile = (mono ? ModBlocks.L1_MONOCHROME_FLOOR : ModBlocks.L1_BLUE_TILE).get().defaultBlockState();
        BlockState bright = ModBlocks.L1_BRIGHT_PATCH.get().defaultBlockState();
        int roof = floor + 5; // the corridor's own, lower ceiling
        out.set(roof, t == 88 && a == 3 ? bright : wall);
        if (innerWall(t, a)) {
            out.set(floor, tile);
            boolean doorway = a == 1 && (t == DOOR_T || t == DOOR_T + 1);
            BlockState face = corridorNoclip(t, a) ? ModBlocks.L1_NOCLIP_WALL.get().defaultBlockState() : wall;
            for (int y = floor + 1; y < roof; y++) {
                if (!(doorway && y <= floor + 3)) {
                    out.set(y, face);
                }
            }
            if (inLoop(t) && a == 5 && (t - LOOP_START) % LOOP_LENGTH == 10) {
                out.set(floor + 2, ModBlocks.L1_BLUE_TILE.get().defaultBlockState()); // a blue tile set in the wall, in every copy
            }
            if (t == 86 && a == 1) {
                out.set(floor + 2, bright);
            }
            if (t == 93 && a == 5) {
                out.set(floor + 3, bright);
            }
            return;
        }
        // The 3-wide floor.
        if (corridorVoid(site, t, a)) {
            out.set(floor, ModBlocks.L1_VOID_FLOOR.get().defaultBlockState());
        } else if (brightFloor(t, a)) {
            out.set(floor, bright);
        } else if (inLoop(t) && a == 3 && (t - LOOP_START) % LOOP_LENGTH == 5) {
            out.set(floor, ModBlocks.FLOOR_GRATE.get().defaultBlockState()); // a drain, in every copy
        } else {
            out.set(floor, tile);
        }
        if (corridorBulge(t, a)) {
            for (int y = floor + 1; y < roof; y++) {
                out.set(y, wall);
            }
            return;
        }
        int ramp = corridorRamp(t, a);
        if (ramp == 1) {
            out.set(floor + 1, ModBlocks.L1_BLUE_TILE_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        } else if (ramp == 2) {
            out.set(floor + 1, ModBlocks.L1_BLUE_TILE.get().defaultBlockState());
        }
        if (a == 3 && corridorTube(t)) {
            out.set(roof - 1, tube(corridorFacing(site).getAxis()));
        }
        if (t == 15 && a == 4) {
            // Sector 1's crate, as the survivors' accounts describe.
            out.set(floor + 1, ModBlocks.SUPPLY_CRATE.get().defaultBlockState()
                    .setValue(SupplyContainerBlock.FACING, corridorFacing(site).getOpposite()));
        }
        if (t >= LIGHT_T && t <= LIGHT_T + 1) {
            BlockState light = ModBlocks.L94_THRONE_LIGHT.get().defaultBlockState();
            for (int y = floor + 1; y < roof; y++) {
                out.set(y, light);
            }
        }
    }

    /** Walkable columns of the corridor (for arrivals and monster spawns). */
    public static boolean corridorWalkable(AnomalySite site, int x, int z) {
        int t = corridorT(site, x, z);
        int a = corridorA(site, x, z);
        if (a <= 1) {
            return t == DOOR_T || t == DOOR_T + 1;
        }
        if (a >= 5 || t <= 1 || t >= LIGHT_T) {
            return false;
        }
        return !corridorVoid(site, t, a) && !corridorBulge(t, a) && corridorRamp(t, a) == 0 && !(t == 15 && a == 4);
    }

    /** Where you wake up after falling through Sector 4: just inside the entrance. */
    public static BlockPos corridorEntrance(AnomalySite site) {
        return new BlockPos(corridorX(site, DOOR_T + 3, 3), FLOOR + 1, corridorZ(site, DOOR_T + 3, 3));
    }

    // ==========================================================================================

    private static BlockState tube(Direction.Axis axis) {
        return ModBlocks.CEILING_TUBE.get().defaultBlockState().setValue(CeilingTubeBlock.AXIS, axis);
    }

    private Level1AnomalyPlan() {}
}
