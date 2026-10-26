package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalyGrid;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * Where the anomalous areas of Level 94 are (see {@code world/anomaly}). All three come from the
 * M.E.G.'s log of the discovery of Level 94
 * (https://backrooms-wiki.wikidot.com/m-e-g-the-discovery-of-level-94) and the level's page
 * (https://backrooms-wiki.wikidot.com/level-94):
 * <ul>
 *   <li><b>{@value #FURNITURE_DRIFT}</b>: the hilltop houses come apart the nearer they are to a
 *       castle: whole houses far out, then single rooms, then only piles of furniture
 *       ({@link Level94Plan#driftStage}). Every room or pile in that band is a site; the server
 *       turns and nudges its furniture towards the castle while nobody watches.</li>
 *   <li><b>{@value #WANDERING_DOOR}</b>: one per castle. A door in a frame with no wall stands at
 *       one of {@link #DOOR_SPOTS} on the plateau or in the castle and moves to another when nobody
 *       looks. Through it, you come out in the throne room. The site is the whole plateau.</li>
 *   <li><b>{@value #BLINDING_HALL}</b>: one per castle, the room of blinding light on the upper
 *       floor (built by {@link Level94CastlePlan}). The site is its columns; it only counts at the
 *       hall's own height ({@link #inHall}).</li>
 * </ul>
 * Everything here is a pure function of the seed and cheap to ask; sites are cached by their grids.
 */
public final class Level94AnomalyPlan {
    public static final String FURNITURE_DRIFT = "level_94/furniture_drift";
    public static final String WANDERING_DOOR = "level_94/wandering_door";
    public static final String BLINDING_HALL = "level_94/blinding_hall";
    public static final List<String> KINDS = List.of(FURNITURE_DRIFT, WANDERING_DOOR, BLINDING_HALL);

    /** Feet height on the blinding hall's floor (template layer 10 above the plateau). */
    public static final int HALL_FEET_Y = Level94Castle.MOUNTAIN_TOP_Y + 10;
    /** The hall is 7 blocks tall: feet from {@link #HALL_FEET_Y} up to (not including) this. */
    public static final int HALL_TOP_Y = Level94Castle.MOUNTAIN_TOP_Y + 17;

    /** Hilltop rooms and piles: one hill (at most) per hill cell, so one site per cell at most. */
    private static final AnomalyGrid DRIFT = new AnomalyGrid(Level94Terrain.HILL_CELL, 0, Level94AnomalyPlan::planDrift);
    /** One door and one hall per castle region (castles keep well away from their region's edges). */
    private static final AnomalyGrid DOORS = new AnomalyGrid(Level94Castle.REGION, 0, Level94AnomalyPlan::planDoor);
    private static final AnomalyGrid HALLS = new AnomalyGrid(Level94Castle.REGION, 0, Level94AnomalyPlan::planHall);

    private static final int S = Direction.SOUTH.get2DDataValue();
    private static final int W = Direction.WEST.get2DDataValue();
    private static final int N = Direction.NORTH.get2DDataValue();
    private static final int E = Direction.EAST.get2DDataValue();

    /**
     * Where the wandering door can stand, in castle template coordinates (see
     * {@link Level94CastlePlan}): {x, z, feet layer, the door's FACING as a 2D value}. Each spot has
     * room for the frame (one block either side of the door, a lintel on top) and a clear block in
     * front and behind. Inside: both ends of the entrance hall, across the gallery's striped path,
     * in the animation studio upstairs. Outside on the plateau: east, west, behind the castle and
     * beside the gate towers.
     */
    private static final int[][] DOOR_SPOTS = {
            {-12, -12, 1, E}, {12, -12, 1, W}, {0, 2, 1, N}, {-8, -12, 10, N},
            {19, 0, 1, E}, {-19, 0, 1, W}, {0, 20, 1, S}, {-10, -19, 1, N}};
    /** Everything above stays this close to the castle centre (so the worldgen check stays cheap). */
    private static final int DOOR_REACH = 21;
    /** Where people come out of the door: on the red carpet inside the throne room, facing the throne. */
    private static final int THRONE_X = 0;
    private static final int THRONE_Z = 8;

    private Level94AnomalyPlan() {}

    // ==========================================================================================
    // The layout's questions
    // ==========================================================================================

    /** The site covering column (x, z), or null. The hall wins over the door's plateau around it. */
    @Nullable
    public static AnomalySite at(long seed, int x, int z) {
        AnomalySite site = HALLS.at(seed, x, z);
        if (site == null) {
            site = DOORS.at(seed, x, z);
        }
        if (site == null) {
            site = DRIFT.at(seed, x, z);
        }
        return site;
    }

    public static void near(long seed, int x, int z, int radius, Consumer<AnomalySite> out) {
        HALLS.near(seed, x, z, radius, out);
        DOORS.near(seed, x, z, radius, out);
        DRIFT.near(seed, x, z, radius, out);
    }

    @Nullable
    public static AnomalySite nearest(long seed, int x, int z, String kind) {
        return switch (kind) {
            case FURNITURE_DRIFT -> DRIFT.nearest(seed, x, z, site -> true, 40);
            case WANDERING_DOOR -> DOORS.nearest(seed, x, z, site -> true, 2);
            case BLINDING_HALL -> HALLS.nearest(seed, x, z, site -> true, 2);
            default -> null;
        };
    }

    // ==========================================================================================
    // Planners
    // ==========================================================================================

    /**
     * The drifting room or pile on the hill of cell (ci, cj), or null. Cheap checks first (is
     * there a hill that could hold a house, is it in the drift band at all); only then the
     * square's plan, to see which building actually won the hilltop.
     */
    @Nullable
    private static AnomalySite planDrift(long seed, int ci, int cj) {
        long h = Level94Terrain.cellHash(seed, ci, cj);
        if (!Level94Terrain.mayHoldHouse(h)) {
            return null;
        }
        int cx = Level94Terrain.centreX(h, ci);
        int cz = Level94Terrain.centreZ(h, cj);
        double castle = Level94Roads.castleDistance(seed, cx + 0.5, cz + 0.5);
        if (castle > Level94Plan.ROOM_RANGE + Level94Plan.DRIFT_JITTER / 2.0 || castle < Level94Castle.CLEAR_RADIUS) {
            return null;
        }
        Level94Plan plan = Level94Plan.of(seed, cx, cz);
        for (Level94Builds.Thing t : plan.things) {
            // Hilltop buildings use their hill's bits as key; a = drift stage (0 = an ordinary house).
            if (t.key == h && t.x == cx && t.z == cz && t.kind == Level94Builds.HOUSE && t.house != null && t.a > 0) {
                int[] b = t.box;
                int[] c = Level94Castle.nearest(seed, cx, cz);
                Direction toCastle = Level94Plan.toward(c[0] - cx, c[1] - cz);
                return new AnomalySite(FURNITURE_DRIFT, b[0] - 1, b[1] - 1, b[2] + 1, b[3] + 1, cx, cz,
                        toCastle.get2DDataValue(), h);
            }
        }
        return null;
    }

    /** The plateau of the castle of region (rx, rz): the door's wandering ground. Facing = the gate's direction. */
    private static AnomalySite planDoor(long seed, int rx, int rz) {
        int[] c = Level94Castle.centre(seed, rx, rz);
        int r = Level94Castle.PLATEAU_RADIUS;
        int turns = Level94Castle.turnsAt(seed, c[0], c[1]);
        return new AnomalySite(WANDERING_DOOR, c[0] - r, c[1] - r, c[0] + r, c[1] + r, c[0], c[1],
                turn(Direction.NORTH, turns).get2DDataValue(), doorSalt(seed, c[0], c[1]));
    }

    /** The blinding hall's columns (template x 6..14, z -16..-8); anchor = its doorway from the looping hall. */
    private static AnomalySite planHall(long seed, int rx, int rz) {
        int[] c = Level94Castle.centre(seed, rx, rz);
        int turns = Level94Castle.turnsAt(seed, c[0], c[1]);
        int ax = c[0] + worldDX(6, -16, turns);
        int az = c[1] + worldDZ(6, -16, turns);
        int bx = c[0] + worldDX(14, -8, turns);
        int bz = c[1] + worldDZ(14, -8, turns);
        return new AnomalySite(BLINDING_HALL, Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz),
                c[0] + worldDX(5, -12, turns), c[1] + worldDZ(5, -12, turns),
                turn(Direction.NORTH, turns).get2DDataValue(), Hash.of(seed, rx, rz, 9498));
    }

    private static long doorSalt(long seed, int cx, int cz) {
        return Hash.of(seed, Math.floorDiv(cx, Level94Castle.REGION), Math.floorDiv(cz, Level94Castle.REGION), 9497);
    }

    // ==========================================================================================
    // Castle template <-> world
    // ==========================================================================================

    /** World x offset of template (lx, lz) for a castle turned {@code turns} quarter turns clockwise. */
    private static int worldDX(int lx, int lz, int turns) {
        return switch (turns) {
            case 0 -> lx;
            case 1 -> -lz;
            case 2 -> -lx;
            default -> lz;
        };
    }

    /** World z offset of template (lx, lz). */
    private static int worldDZ(int lx, int lz, int turns) {
        return switch (turns) {
            case 0 -> lz;
            case 1 -> lx;
            case 2 -> -lz;
            default -> -lx;
        };
    }

    private static Direction turn(Direction d, int turns) {
        for (int i = 0; i < turns; i++) {
            d = d.getClockWise();
        }
        return d;
    }

    // ==========================================================================================
    // The blinding hall
    // ==========================================================================================

    /** True if a body at (x, y, z) (feet) is inside the blinding hall of {@code site}, not on another floor of those columns. */
    public static boolean inHall(AnomalySite site, double x, double y, double z) {
        return site.kind().equals(BLINDING_HALL) && site.contains(x, z) && y >= HALL_FEET_Y - 0.5 && y < HALL_TOP_Y;
    }

    // ==========================================================================================
    // The wandering door
    // ==========================================================================================

    /** One place the door can stand: the cell of its lower half and the way it faces. */
    public record DoorSpot(BlockPos feet, Direction facing) {
        /** The cell {@code along} blocks to the side of the door (-1, 0, 1) and {@code up} above its lower half. */
        public BlockPos part(int along, int up) {
            Direction side = facing.getClockWise();
            return feet.offset(side.getStepX() * along, up, side.getStepZ() * along);
        }

        /** Centre of the doorway, for "is anybody looking at it". */
        public net.minecraft.world.phys.Vec3 centre() {
            return new net.minecraft.world.phys.Vec3(feet.getX() + 0.5, feet.getY() + 1.0, feet.getZ() + 0.5);
        }
    }

    /** Every spot the door of {@code site} (a {@value #WANDERING_DOOR} site) can stand at, in world coordinates. */
    public static List<DoorSpot> doorSpots(long seed, AnomalySite site) {
        int cx = site.anchorX();
        int cz = site.anchorZ();
        int turns = Level94Castle.turnsAt(seed, cx, cz);
        List<DoorSpot> out = new ArrayList<>(DOOR_SPOTS.length);
        for (int[] s : DOOR_SPOTS) {
            out.add(new DoorSpot(new BlockPos(cx + worldDX(s[0], s[1], turns), Level94Castle.MOUNTAIN_TOP_Y + s[2],
                    cz + worldDZ(s[0], s[1], turns)), turn(Direction.from2DDataValue(s[3]), turns)));
        }
        return out;
    }

    /** Index into {@link #doorSpots} of where the world generator first puts the door. */
    public static int firstDoorSpot(AnomalySite site) {
        return Hash.range(site.salt(), DOOR_SPOTS.length);
    }

    private static int firstDoorSpot(long seed, int cx, int cz) {
        return Hash.range(doorSalt(seed, cx, cz), DOOR_SPOTS.length);
    }

    /**
     * The block of the door and its free-standing frame at {@code along} (-1, 0, 1 across the
     * doorway) and {@code up} (0..2 above the lower half), or null where nothing goes. White trim
     * posts either side, a trim lintel on top, a closed plain house door in between.
     */
    @Nullable
    public static BlockState doorPart(int along, int up, Direction facing) {
        if (up == 2) {
            return ModBlocks.L94_TRIM.get().defaultBlockState();
        }
        if (along != 0) {
            return up <= 1 ? ModBlocks.L94_TRIM.get().defaultBlockState() : null;
        }
        if (up < 0 || up > 1) {
            return null;
        }
        return ModBlocks.L94_WANDERING_DOOR.get().defaultBlockState()
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.OPEN, false)
                .setValue(DoorBlock.HALF, up == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
    }

    /** World generation: the door in its first spot, if column (x, z) of a castle zone holds part of it. */
    static void castleColumn(long seed, int x, int z, LevelLayout.Column out) {
        long packed = Level94Castle.nearestPacked(seed, x, z);
        int cx = Level94Castle.unpackX(packed);
        int cz = Level94Castle.unpackZ(packed);
        int dx = x - cx;
        int dz = z - cz;
        if (Math.abs(dx) > DOOR_REACH + 1 || Math.abs(dz) > DOOR_REACH + 1) {
            return;
        }
        int turns = Level94Castle.turnsAt(seed, cx, cz);
        int lx = Level94Castle.localX(dx, dz, turns);
        int lz = Level94Castle.localZ(dx, dz, turns);
        int[] spot = DOOR_SPOTS[firstDoorSpot(seed, cx, cz)];
        Direction templateFacing = Direction.from2DDataValue(spot[3]);
        Direction side = templateFacing.getClockWise();
        int along = (lx - spot[0]) * side.getStepX() + (lz - spot[1]) * side.getStepZ();
        int across = (lx - spot[0]) * templateFacing.getStepX() + (lz - spot[1]) * templateFacing.getStepZ();
        if (across != 0 || Math.abs(along) > 1) {
            return;
        }
        // Template "along" runs the same way as the world one, because both sides turn together.
        Direction facing = turn(templateFacing, turns);
        int y = Level94Castle.MOUNTAIN_TOP_Y + spot[2];
        for (int up = 0; up <= 2; up++) {
            BlockState state = doorPart(along, up, facing);
            if (state != null) {
                out.set(y + up, state);
            }
        }
    }

    /**
     * Where people come out of the wandering door: {x, y, z, yaw} on the throne room carpet of the
     * castle nearest (x, z), facing the throne.
     */
    public static double[] throneArrival(long seed, int x, int z) {
        long packed = Level94Castle.nearestPacked(seed, x, z);
        int cx = Level94Castle.unpackX(packed);
        int cz = Level94Castle.unpackZ(packed);
        int turns = Level94Castle.turnsAt(seed, cx, cz);
        return new double[] {cx + worldDX(THRONE_X, THRONE_Z, turns) + 0.5, Level94Castle.MOUNTAIN_TOP_Y + 1,
                cz + worldDZ(THRONE_X, THRONE_Z, turns) + 0.5, turn(Direction.SOUTH, turns).toYRot()};
    }

    // ==========================================================================================
    // Furniture drift
    // ==========================================================================================

    /** Y of the ground block a drift site's furniture stands on (its hilltop is flattened to one height). */
    public static int driftGroundY(long seed, AnomalySite site) {
        Level94Plan plan = Level94Plan.of(seed, site.anchorX(), site.anchorZ());
        return LevelLayout.FLOOR_Y + (plan.top[plan.index(site.anchorX(), site.anchorZ())] >> 1);
    }
}
