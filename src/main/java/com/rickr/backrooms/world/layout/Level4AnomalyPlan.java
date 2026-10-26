package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalyGrid;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Where Level 4's anomalous areas are, and their shapes, as plain numbers (like {@link Level4Plan}).
 * {@link Level4Layout} builds their blocks from these answers (through {@link Level4AnomalyBlocks}),
 * and the server behaviours ({@code world/anomaly/level4}) and client effects
 * ({@code client/anomaly/level4}) read the same shapes back from the {@link AnomalySite}, so the
 * three always agree.
 *
 * <p>Every 48 x 48 zone of {@link Level4Plan} can hold at most one site, chosen by the zone's own
 * hash, and each kind lives where it makes sense in an office building:
 * <ul>
 *   <li><b>{@link #LOOPING_STAIRWELL}</b> (about 1 open-plan zone in 7): a grey stair core standing
 *       in an open-plan floor. Its stairs lead up and down, but every flight comes back to the same
 *       landing (Fandom, Level 4). Built as a shaft that repeats every {@link #STAIR_PERIOD} blocks
 *       of height, so the server can quietly move you back one storey.</li>
 *   <li><b>{@link #WINDOW}</b> (rare): one clear window in a run of rainy windows, the panes around
 *       it covered with cardboard and tape (Wikidot, Entity 2 "Windows").</li>
 *   <li><b>{@link #VANISHING_ELEVATOR}</b> (about 1 lobby in 3): one elevator of the lobby stands
 *       open and lit. The car behind each of the eight doors is carved out of the lobby's solid
 *       elevator core here, so any of them can be the open one.</li>
 *   <li><b>{@link #CUBICLES}</b> (about 1 cubicle zone in 16): the zone's core becomes the way into
 *       Level 4.3, a dim white series of hallways that opens onto an endless cubicle field.</li>
 * </ul>
 * Zones with a safe room or near an exit stairwell never get one (they stay plain open floor), and
 * every site stays inside its own zone, so the zone's ring aisle and borders (and with them every
 * way through the level) are never touched.
 *
 * <p>Directions are Minecraft's 2D data values (0 south, 1 west, 2 north, 3 east), as in Level4Plan.
 */
public final class Level4AnomalyPlan {
    public static final String LOOPING_STAIRWELL = "level_4/looping_stairwell";
    public static final String WINDOW = "level_4/window";
    public static final String VANISHING_ELEVATOR = "level_4/vanishing_elevator";
    public static final String CUBICLES = "level_4/cubicles";
    public static final List<String> KINDS = List.of(LOOPING_STAIRWELL, WINDOW, VANISHING_ELEVATOR, CUBICLES);

    // Chances, per zone of the right kind.
    private static final double ELEVATOR_CHANCE = 0.35;   // of lobby zones (15% of all zones)
    private static final double CUBICLES_CHANCE = 0.06;   // of cubicle zones (25%): ~1 zone in 65 overall
    private static final double STAIRWELL_CHANCE = 0.15;  // of open-plan zones (30%)
    private static final double WINDOW_CHANCE = 0.15;     // of zones with a window run looking in

    private static final int CELL = Level4Plan.CELL;
    private static final int CORE_LO = Level4Plan.CORE_LO;
    private static final int CORE_HI = Level4Plan.CORE_HI;

    public static final int SOUTH = 0;
    public static final int WEST = 1;
    public static final int NORTH = 2;
    public static final int EAST = 3;
    private static final int[] DX = {0, -1, 0, 1};
    private static final int[] DZ = {1, 0, -1, 0};

    /** One site per zone, entirely inside it (reach 0: lookups only ask the zone itself). */
    static final AnomalyGrid GRID = new AnomalyGrid(CELL, 0, Level4AnomalyPlan::plan);

    public static int stepX(int dir) {
        return DX[dir & 3];
    }

    public static int stepZ(int dir) {
        return DZ[dir & 3];
    }

    public static int opposite(int dir) {
        return (dir + 2) & 3;
    }

    public static int clockwise(int dir) {
        return (dir + 1) & 3;
    }

    public static int counterClockwise(int dir) {
        return (dir + 3) & 3;
    }

    // ==========================================================================================
    // Placement
    // ==========================================================================================

    /** The site of zone (cx, cz), or null. Pure: only hashes and Level4Plan's own pure functions. */
    @Nullable
    static AnomalySite plan(long seed, int cx, int cz) {
        if (Level4Plan.touchesExitClearing(seed, cx, cz) || Level4Plan.safeRoom(seed, cx, cz) >= 0) {
            return null;
        }
        long h = Hash.of(seed, cx, cz, 7900);
        double r = Hash.unit(h);
        long salt = Hash.mix(h ^ 0x5A17_C0DEL);
        int x0 = cx * CELL;
        int z0 = cz * CELL;
        switch (Level4Plan.zoneKind(seed, cx, cz)) {
            case Level4Plan.LOBBY -> {
                if (r < ELEVATOR_CHANCE) {
                    return elevatorSite(seed, cx, cz, salt);
                }
            }
            case Level4Plan.CUBICLES -> {
                if (r < CUBICLES_CHANCE) {
                    return cubiclesSite(x0, z0, salt);
                }
            }
            case Level4Plan.OPEN -> {
                if (r < STAIRWELL_CHANCE) {
                    return stairwellSite(x0, z0, salt);
                }
            }
            default -> {
            }
        }
        // Any zone may instead have the Window, on a window run of its own west or north border.
        if (Hash.unit(Hash.mix(h ^ 0x77)) < WINDOW_CHANCE) {
            boolean west = Level4Plan.windowsInto(seed, cx, cz, true);
            boolean north = Level4Plan.windowsInto(seed, cx, cz, false);
            if (west && north) {
                north = Hash.unit(Hash.mix(h ^ 0x78)) < 0.5;
                west = !north;
            }
            if (west || north) {
                return windowSite(x0, z0, west, salt);
            }
        }
        return null;
    }

    // ==========================================================================================
    // Looping stairwell
    // ==========================================================================================

    /*
     * The stair core in its own frame: the door is on the "north" side (lz = 0), lx runs to the
     * right as you walk in. Two wall rings: the outer one (lx 0/8, lz 0/10) is office drywall where
     * the office can see it, the inner one (lx 1/7, lz 1/9) the grey painted shaft. Inside (5 x 7):
     *   lz 2-3  landing (y40 + 6k), the door's opening in the inner ring at (4, 1)
     *   lz 4-6  two flights side by side: lane UP lx 2-3 (stairs y41..43 + 6k, rising away from the
     *           door), a solid centre wall lx 4, lane DOWN lx 5-6 (stairs y44..46 + 6k, rising
     *           toward the door)
     *   lz 7-8  half landing (y43 + 6k)
     * A landing is at the SAME height as the last stair block of the flight that leads up to it:
     * the top of a stair block is a full block, so a landing one higher could only be jumped onto.
     * Every storey of the shaft (6 blocks) is the same, so climbing a whole storey and being moved
     * back down 6 blocks looks like nothing happened. Going up from the landing: lane UP to the half
     * landing, back along lane DOWN to the landing above. Going down: lane DOWN away from the door
     * to the half landing below, then lane UP back toward the landing below.
     */

    /** Size of the stair core (both wall rings included): STAIR_W across, STAIR_D from the door back. */
    public static final int STAIR_W = 9;
    public static final int STAIR_D = 11;
    /** Height of one storey of the shaft: walking up or down one storey brings you back here. */
    public static final int STAIR_PERIOD = 6;
    /** Solid caps closing the shaft below and above (nobody ever sees them). */
    public static final int STAIR_BOTTOM = 24;
    public static final int STAIR_TOP = 61;

    // Stair cells (what a column of the stair core is).
    public static final int S_OUTER = 0;
    public static final int S_INNER = 1;
    public static final int S_DOOR = 2;
    public static final int S_OPENING = 3;
    public static final int S_LANDING = 4;
    public static final int S_HALF = 5;
    public static final int S_UP = 6;
    public static final int S_DOWN = 7;
    public static final int S_CENTRE = 8;
    /** Outside the core: the 1-block strip of plain floor around it. */
    public static final int S_MOAT = 9;

    private static AnomalySite stairwellSite(int x0, int z0, long salt) {
        int facing = Hash.range(Hash.mix(salt ^ 1), 4);  // the way the door looks out
        boolean northSouth = facing == NORTH || facing == SOUTH;
        int w = northSouth ? STAIR_W : STAIR_D;   // size along x
        int d = northSouth ? STAIR_D : STAIR_W;   // size along z
        // Core plus its strip of floor within local 6..34: clear of the ring aisle and of the row of
        // small offices some open floors have along their far side (local 38+).
        int bx = x0 + 7 + Hash.range(Hash.mix(salt ^ 2), 34 - w - 7 + 1);
        int bz = z0 + 7 + Hash.range(Hash.mix(salt ^ 3), 34 - d - 7 + 1);
        StairFrame frame = StairFrame.of(bx, bz, bx + w - 1, bz + d - 1, facing);
        int doorX = frame.worldX(4, 0);
        int doorZ = frame.worldZ(4, 0);
        return new AnomalySite(LOOPING_STAIRWELL, bx - 1, bz - 1, bx + w, bz + d, doorX, doorZ, facing, salt);
    }

    /**
     * The local frame of a stair core. {@code side} is the direction of +lx, {@code in} of +lz (from
     * the door into the core); (originX, originZ) is local (0, 0).
     */
    public record StairFrame(int originX, int originZ, int side, int in) {
        static StairFrame of(int bx0, int bz0, int bx1, int bz1, int facing) {
            int in = opposite(facing);
            int side = clockwise(in);
            int ox = stepX(side) + stepX(in) > 0 ? bx0 : bx1;
            int oz = stepZ(side) + stepZ(in) > 0 ? bz0 : bz1;
            return new StairFrame(ox, oz, side, in);
        }

        public int lx(int x, int z) {
            return (x - originX) * stepX(side) + (z - originZ) * stepZ(side);
        }

        public int lz(int x, int z) {
            return (x - originX) * stepX(in) + (z - originZ) * stepZ(in);
        }

        public int worldX(int lx, int lz) {
            return originX + lx * stepX(side) + lz * stepX(in);
        }

        public int worldZ(int lx, int lz) {
            return originZ + lx * stepZ(side) + lz * stepZ(in);
        }

        /** The way the door looks out of the core. */
        public int out() {
            return opposite(in);
        }
    }

    /** The stair core's frame (the site is the core plus a 1-block strip around it). */
    public static StairFrame stairFrame(AnomalySite site) {
        return StairFrame.of(site.minX() + 1, site.minZ() + 1, site.maxX() - 1, site.maxZ() - 1, site.facing());
    }

    /** Which part of the stair core local column (lx, lz) is (see the frame comment above). */
    public static int stairCell(int lx, int lz) {
        if (lx < 0 || lx >= STAIR_W || lz < 0 || lz >= STAIR_D) return S_MOAT;
        if (lx == 4 && lz == 0) return S_DOOR;
        if (lx == 4 && lz == 1) return S_OPENING;
        if (lx == 0 || lx == STAIR_W - 1 || lz == 0 || lz == STAIR_D - 1) return S_OUTER;
        if (lx == 1 || lx == STAIR_W - 2 || lz == 1 || lz == STAIR_D - 2) return S_INNER;
        if (lz <= 3) return S_LANDING;
        if (lz >= 7) return S_HALF;
        if (lx <= 3) return S_UP;
        if (lx == 4) return S_CENTRE;
        return S_DOWN;
    }

    /**
     * Height (mod STAIR_PERIOD, relative to the floor y40) of the one solid tread or landing in an
     * interior column, or -1 for the centre wall and walls. UP rows rise 1..3 away from the door,
     * DOWN rows 4..6 toward it (the top one, 6, is the same height as the landing above: 0).
     */
    public static int stairTread(int lx, int lz) {
        return switch (stairCell(lx, lz)) {
            case S_LANDING, S_OPENING, S_DOOR -> 0;
            case S_HALF -> 3;
            case S_UP -> lz - 3;                       // lz 4, 5, 6 -> 1, 2, 3
            case S_DOWN -> Math.floorMod(10 - lz, STAIR_PERIOD); // lz 6, 5, 4 -> 4, 5, 0 (= 6)
            default -> -1;
        };
    }

    // ==========================================================================================
    // The Window
    // ==========================================================================================

    /** How many panes on each side of the Window are covered up. */
    public static final int WINDOW_COVERED = 4;
    /** How far the Window's site reaches along the window run (each way) and into the room. */
    private static final int WINDOW_HALF_WIDTH = 6;
    private static final int WINDOW_DEPTH = 10;

    private static AnomalySite windowSite(int x0, int z0, boolean west, long salt) {
        // A multiple of 4 along the run: the radiators leave a gap there (Level4Plan#radiator), so
        // nothing stands between you and the glass.
        int along = 12 + 4 * Hash.range(Hash.mix(salt ^ 4), 7);
        if (west) {
            return new AnomalySite(WINDOW, x0, z0 + along - WINDOW_HALF_WIDTH, x0 + WINDOW_DEPTH, z0 + along + WINDOW_HALF_WIDTH,
                    x0, z0 + along, EAST, salt);
        }
        return new AnomalySite(WINDOW, x0 + along - WINDOW_HALF_WIDTH, z0, x0 + along + WINDOW_HALF_WIDTH, z0 + WINDOW_DEPTH,
                x0 + along, z0, SOUTH, salt);
    }

    /**
     * For a column of a Window site: 0 = the Window itself, 1 = a covered pane beside it, -1 = an
     * ordinary column (leave it to the plan).
     */
    public static int windowPane(AnomalySite site, int x, int z) {
        boolean alongZ = site.facing() == EAST; // on a west border the run goes along z
        int line = alongZ ? x : z;
        int anchorLine = alongZ ? site.anchorX() : site.anchorZ();
        if (line != anchorLine) {
            return -1;
        }
        int offset = alongZ ? z - site.anchorZ() : x - site.anchorX();
        if (offset == 0) return 0;
        return Math.abs(offset) <= WINDOW_COVERED ? 1 : -1;
    }

    // ==========================================================================================
    // Vanishing elevator
    // ==========================================================================================

    /** Elevators per lobby: four on each side of the hall (see Level4Plan#lobbyCore). */
    public static final int ELEVATOR_DOORS = 8;

    private static AnomalySite elevatorSite(long seed, int cx, int cz, long salt) {
        int x0 = cx * CELL;
        int z0 = cz * CELL;
        boolean alongZ = Level4Plan.alongZ(seed, cx, cz);
        LobbyFrame frame = new LobbyFrame(x0, z0, alongZ);
        // The anchor is the middle of the hall; facing records which way the lobby's rows run.
        return new AnomalySite(VANISHING_ELEVATOR, x0 + CORE_LO, z0 + CORE_LO, x0 + CORE_HI, z0 + CORE_HI,
                frame.worldX(24, 24), frame.worldZ(24, 24), frame.plusV(), salt);
    }

    /**
     * A lobby zone in Level4Plan's zone coordinates: u runs along the elevator walls, v across the
     * hall (both 0..47 inside the zone, the core is 4..44).
     */
    public record LobbyFrame(int x0, int z0, boolean alongZ) {
        public int worldX(int u, int v) {
            return x0 + (alongZ ? v : u);
        }

        public int worldZ(int u, int v) {
            return z0 + (alongZ ? u : v);
        }

        public int u(int x, int z) {
            return alongZ ? z - z0 : x - x0;
        }

        public int v(int x, int z) {
            return alongZ ? x - x0 : z - z0;
        }

        public int plusU() {
            return alongZ ? SOUTH : EAST;
        }

        public int plusV() {
            return alongZ ? EAST : SOUTH;
        }

        /** u of the first of the two columns of elevator {@code i} (0..7). */
        public int doorU(int i) {
            return 17 + 4 * (i % 4);
        }

        /** v of the wall line the doors of elevator {@code i} are set in. */
        public int doorV(int i) {
            return i < 4 ? CORE_LO + 15 : CORE_LO + 25;
        }

        /** The way elevator {@code i} opens: into the hall. */
        public int out(int i) {
            return i < 4 ? plusV() : opposite(plusV());
        }

        /** The car's three rows of floor, from the door back: v0..v1 (v0 <= v1). */
        public int carV0(int i) {
            return i < 4 ? doorV(i) - 3 : doorV(i) + 1;
        }

        public int carV1(int i) {
            return i < 4 ? doorV(i) - 1 : doorV(i) + 3;
        }

        /** The row of the car with the light panel (its middle). */
        public int carLightV(int i) {
            return i < 4 ? doorV(i) - 2 : doorV(i) + 2;
        }

        /** v of the car's back wall. */
        public int carBackV(int i) {
            return i < 4 ? doorV(i) - 4 : doorV(i) + 4;
        }

        /** True for the door column seen as the right-hand one from the hall (ElevatorDoorBlock.RIGHT). */
        public boolean right(int i, int column) {
            return (column == 1) == (clockwise(out(i)) == plusU());
        }

        /** Which elevator's car floor (u, v) is part of, or -1. */
        public int carAt(int u, int v) {
            for (int i = 0; i < ELEVATOR_DOORS; i++) {
                if (u >= doorU(i) && u <= doorU(i) + 1 && v >= carV0(i) && v <= carV1(i)) {
                    return i;
                }
            }
            return -1;
        }

        /** Which elevator's car WALL (sides and back, not the door wall) (u, v) is, or -1. */
        public int carWallAt(int u, int v) {
            for (int i = 0; i < ELEVATOR_DOORS; i++) {
                int u0 = doorU(i) - 1;
                int u1 = doorU(i) + 2;
                int vLo = Math.min(carBackV(i), carV0(i));
                int vHi = Math.max(carBackV(i), carV1(i));
                if (u >= u0 && u <= u1 && v >= vLo && v <= vHi && carAt(u, v) < 0) {
                    return i;
                }
            }
            return -1;
        }

        /** Which elevator's door (u, v) is a column of, or -1. */
        public int doorAt(int u, int v) {
            for (int i = 0; i < ELEVATOR_DOORS; i++) {
                if (v == doorV(i) && (u == doorU(i) || u == doorU(i) + 1)) {
                    return i;
                }
            }
            return -1;
        }
    }

    public static LobbyFrame lobbyFrame(AnomalySite site) {
        return new LobbyFrame(site.minX() - CORE_LO, site.minZ() - CORE_LO, site.facing() == EAST);
    }

    /** The elevator that stands open when the lobby is first built. */
    public static int initialOpenElevator(AnomalySite site) {
        return Hash.range(Hash.mix(site.salt() ^ 9), ELEVATOR_DOORS);
    }

    // ==========================================================================================
    // Level 4.3, the Cubicles
    // ==========================================================================================

    /*
     * The zone's core (local 4..44) in its own frame: u along the entrance side, v away from it
     * (+v = the site's facing). Outer walls at u/v 4 and 44 (office drywall, seen from the ring).
     *   (6, 4)-(6, 5)  the doorway in from the ring aisle
     *   v 5            white wall lining the outer one
     *   v 6-7          hallway 1, u 6..42           (white, dim)
     *   v 8            white wall, open at u 41-42 (the turn)
     *   v 9-10         hallway 2, u 6..42
     *   v 11           white wall, open at u = 11-12, 23-24, 35-36 (every 12 blocks)
     *   v 12-43        the cubicle field, u 5..43
     * The field repeats every FIELD_PERIOD_U x FIELD_PERIOD_V blocks, so the server can move you back
     * a whole pattern when you get near its far or side edges: it never ends, except the way you
     * came in. The fog hides everything further than ~10 blocks, the walls included.
     */
    public static final int FIELD_V0 = 12;
    public static final int FIELD_U0 = 5;
    public static final int FIELD_PERIOD_U = 12;
    public static final int FIELD_PERIOD_V = 9;

    // Cubicle cells.
    public static final int C_OUTSIDE = 0;
    /** Outer wall of the core (office drywall). */
    public static final int C_OUTER = 1;
    /** White hallway wall. */
    public static final int C_WHITE = 2;
    /** The doorway in from the ring aisle (3 tall, drywall lintel). */
    public static final int C_DOORWAY = 3;
    /** Floor of the white hallways (and the openings into the field). */
    public static final int C_HALL = 4;
    public static final int C_FLOOR = 5;
    public static final int C_PARTITION = 6;
    public static final int C_DESK = 7;
    public static final int C_DESK_PC = 8;
    public static final int C_CHAIR = 9;
    public static final int C_FRIDGE = 10;

    private static AnomalySite cubiclesSite(int x0, int z0, long salt) {
        int facing = Hash.range(Hash.mix(salt ^ 5), 4);
        CubicleFrame frame = new CubicleFrame(x0, z0, facing);
        return new AnomalySite(CUBICLES, x0 + CORE_LO, z0 + CORE_LO, x0 + CORE_HI, z0 + CORE_HI,
                frame.worldX(6, CORE_LO), frame.worldZ(6, CORE_LO), facing, salt);
    }

    /** The cubicles frame: +v = {@code facing}, +u = counter-clockwise of it; u, v are 4..44 on the core. */
    public record CubicleFrame(int x0, int z0, int facing) {
        public int u(int x, int z) {
            int lx = x - x0;
            int lz = z - z0;
            return switch (facing) {
                case SOUTH -> lx;
                case NORTH -> CELL - lx;
                case EAST -> CELL - lz;
                default -> lz;          // WEST
            };
        }

        public int v(int x, int z) {
            int lx = x - x0;
            int lz = z - z0;
            return switch (facing) {
                case SOUTH -> lz;
                case NORTH -> CELL - lz;
                case EAST -> lx;
                default -> CELL - lx;   // WEST
            };
        }

        public int worldX(int u, int v) {
            return x0 + switch (facing) {
                case SOUTH -> u;
                case NORTH -> CELL - u;
                case EAST -> v;
                default -> CELL - v;
            };
        }

        public int worldZ(int u, int v) {
            return z0 + switch (facing) {
                case SOUTH -> v;
                case NORTH -> CELL - v;
                case EAST -> CELL - u;
                default -> u;
            };
        }

        /** World direction of +u. */
        public int plusU() {
            return counterClockwise(facing);
        }

        /** World direction of +v (deeper in). */
        public int plusV() {
            return facing;
        }
    }

    public static CubicleFrame cubicleFrame(AnomalySite site) {
        return new CubicleFrame(site.minX() - CORE_LO, site.minZ() - CORE_LO, site.facing());
    }

    /**
     * What stands at (u, v) of a cubicles site, packed: cell in bits 0-7, facing in bits 8-9 as a
     * FRAME direction (0 = +v, 1 = -u, 2 = -v, 3 = +u, i.e. the 2D values of a site facing south).
     */
    public static int cubicleCell(int u, int v) {
        if (u < CORE_LO || u > CORE_HI || v < CORE_LO || v > CORE_HI) return C_OUTSIDE;
        if (u == 6 && (v == CORE_LO || v == CORE_LO + 1)) return C_DOORWAY;
        if (u == CORE_LO || u == CORE_HI || v == CORE_LO || v == CORE_HI) return C_OUTER;
        if (v < FIELD_V0) {
            // The hallways and the walls between them.
            if (u == 5 || u == 43 || v == 5) return C_WHITE;
            if (v == 8) return u == 41 || u == 42 ? C_HALL : C_WHITE;
            if (v == 11) {
                int a = Math.floorMod(u - FIELD_U0, FIELD_PERIOD_U);
                return a == 6 || a == 7 ? C_HALL : C_WHITE;
            }
            return C_HALL;
        }
        return fieldCell(Math.floorMod(u - FIELD_U0, FIELD_PERIOD_U), Math.floorMod(v - FIELD_V0, FIELD_PERIOD_V));
    }

    /*
     * One 12 x 9 tile of the field (a along u, b along v):
     *   b 0-1   aisle (the openings from the hallway lead into one)
     *   b 2-4   pods A: open front at b 2, chair at b 3, desks against the spine at b 4
     *   b 5     spine partition
     *   b 6-8   pods B: desks at b 6, chair at b 7, open front at b 8 (onto the next tile's aisle)
     *   a 0, 4, 8 partitions between pods (pods are a 1-3 and 5-7); a 9-11 a cross aisle.
     * In one pod of each tile a fridge stands beside the chair, its door toward the pod's open front.
     */
    private static int fieldCell(int a, int b) {
        if (b <= 1 || a >= 9) return C_FLOOR;
        if (a == 0 || a == 4 || a == 8 || b == 5) return C_PARTITION;
        boolean podA = b <= 4;
        int toFront = podA ? 2 : 0;     // frame directions: -v (2) or +v (0)
        int desk = podA ? 4 : 6;
        int chair = podA ? 3 : 7;
        int mid = a <= 3 ? 2 : 6;
        if (b == desk) {
            return (a == mid ? C_DESK_PC : C_DESK) | toFront << 8;
        }
        if (b == chair && a == mid) {
            return C_CHAIR | opposite(toFront) << 8;     // looking at the desk
        }
        if (podA && b == 3 && a == 7) {
            return C_FRIDGE | 2 << 8;                    // door toward the open front (-v), where you stand
        }
        return C_FLOOR;
    }

    /** World direction of a FRAME direction stored by {@link #cubicleCell}. */
    public static int cubicleWorldDir(CubicleFrame frame, int frameDir) {
        return switch (frameDir & 3) {
            case 0 -> frame.plusV();
            case 2 -> opposite(frame.plusV());
            case 3 -> frame.plusU();
            default -> opposite(frame.plusU());
        };
    }

    public static boolean cubicleWalkable(int cell) {
        return cell == C_DOORWAY || cell == C_HALL || cell == C_FLOOR;
    }

    /** Where someone the stairs let out "somewhere else" arrives: in a cross aisle, mid-field. */
    public static int cubicleArrivalU() {
        return FIELD_U0 + 2 * FIELD_PERIOD_U - 2; // a = 10, the middle of a cross aisle
    }

    public static int cubicleArrivalV() {
        return FIELD_V0 + FIELD_PERIOD_V + 3;
    }

    // ==========================================================================================
    // Walking
    // ==========================================================================================

    /** 1 / 0 when a site decides whether column (x, z) is walkable, -1 to leave it to the plan. */
    static int walkable(AnomalySite site, int x, int z) {
        switch (site.kind()) {
            case LOOPING_STAIRWELL -> {
                StairFrame f = stairFrame(site);
                int cell = stairCell(f.lx(x, z), f.lz(x, z));
                if (cell == S_MOAT) return 1;
                return cell == S_DOOR || cell == S_OPENING || cell == S_LANDING ? 1 : 0;
            }
            case VANISHING_ELEVATOR -> {
                LobbyFrame f = lobbyFrame(site);
                int u = f.u(x, z);
                int v = f.v(x, z);
                if (f.carAt(u, v) >= 0) return 1;
                if (f.carWallAt(u, v) >= 0) return 0;
                return -1;
            }
            case CUBICLES -> {
                CubicleFrame f = cubicleFrame(site);
                return cubicleWalkable(cubicleCell(f.u(x, z), f.v(x, z)) & 0xFF) ? 1 : 0;
            }
            default -> {
                return -1;
            }
        }
    }

    /** Arrivals (level changes, respawns) never land inside a stairwell, elevator car or Level 4.3. */
    static boolean blocksArrival(AnomalySite site, int x, int z) {
        return switch (site.kind()) {
            case LOOPING_STAIRWELL, CUBICLES -> true;
            case VANISHING_ELEVATOR -> walkable(site, x, z) >= 0;
            default -> false;
        };
    }

    private Level4AnomalyPlan() {}
}
