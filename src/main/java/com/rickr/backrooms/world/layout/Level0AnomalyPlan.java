package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalyGrid;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * WHERE Level 0's anomalous areas are (see {@code world/anomaly} for the framework), and the one
 * place that knows which plan class builds each kind ({@link #fill}, {@link #isSolid},
 * {@link #isWalkable}: Level0Layout just asks here). The kinds, each with its own {@link AnomalyGrid}:
 * <ul>
 *   <li>{@link #MANILA_ROOM}: one per 512x512 square (90%): very rare, like the wiki's "15% chance
 *       after a day of walking".</li>
 *   <li>{@link #RED_ROOMS}: one per 384x384 square (90%).</li>
 *   <li>{@link #PITFALLS} and {@link #PERIPHERAL_SHIFT}: one each per 256x256 square (80%).</li>
 *   <li>The three set pieces, {@link #ATRIUM}, {@link #SHAFT_HALL} and {@link #FRONT_OFFICE}:
 *       together one in EVERY 256x256 square (they share one grid; which of the three a square
 *       gets follows a fixed pattern, so the same kind never sits in three squares in a row).
 *       This is what keeps a set piece within reach of any spot (see "Findability").</li>
 *   <li>The hidden rooms ({@link Level0HiddenRooms}): one per 256x256 square (90%), small.</li>
 *   <li>{@link #BLACKOUT_ZONE}: one per 128x128 square (50%), the most common.</li>
 * </ul>
 * Altogether a player walking normally meets one of them every couple of minutes, while each kind
 * stays special.
 *
 * <p><b>No overlaps.</b> Every site sits entirely inside one 128x128 "sub-square" (the same squares
 * safe rooms use), with a margin to its edges. Two kinds clash only if they pick the same
 * sub-square; then the rarer kind wins (order: Manila Room, Red Rooms, Pitfalls, pillar hall, set
 * pieces, hidden rooms, blackout). A site also never covers an exit stairwell (and its lobby), a
 * safe room or a small grey office: the planner tries a few spots and gives up if none is free.
 *
 * <p><b>Sensible spots.</b> Each kind prefers the part of the maze it belongs in: the pillar hall a
 * "big open hall" zone, the pit room an ordinary-rooms zone, the Red Rooms the tight maze (you turn
 * a corner and you're in), blackouts the half of the level that is already dark. The set pieces
 * are buildings in their own right and go anywhere.
 *
 * <p><b>Findability.</b> With a set piece in (nearly) every 256x256 square (placed in a free
 * 128x128 quarter of it, up to 32 tries) on top of the pit rooms and pillar halls, no spot of
 * Level 0 is far from a big anomalous building. Measured when this was written (4 seeds, a
 * 2048x2048 area each, a point every 16 blocks): median 39 blocks to the nearest one, 99% of
 * spots within 116, the worst spot 183.
 *
 * <p>Everything here is a pure function of (seed, x, z), cached per square by the grids, so the
 * layout can ask it for every column and the server/client get the same answers.
 */
public final class Level0AnomalyPlan {
    public static final String RED_ROOMS = "level_0/red_rooms";
    public static final String BLACKOUT_ZONE = "level_0/blackout_zone";
    public static final String MANILA_ROOM = "level_0/manila_room";
    public static final String PERIPHERAL_SHIFT = "level_0/peripheral_shift";
    public static final String PITFALLS = "level_0/pitfalls";
    /** The two-storey void with pendant lights and a balcony you can climb to ({@link Level0Atrium}). */
    public static final String ATRIUM = "level_0/atrium";
    /** The low dark hall with holes into tall shafts ({@link Level0ShaftHall}). */
    public static final String SHAFT_HALL = "level_0/shaft_hall";
    /** The dark alarm room with the drawn door that is sometimes real ({@link Level0FrontOffice}). */
    public static final String FRONT_OFFICE = "level_0/front_office";
    /** The three set pieces, in the order of the pattern that spreads them over the 256x256 squares. */
    private static final String[] SET_PIECES = {ATRIUM, SHAFT_HALL, FRONT_OFFICE};
    public static final List<String> KINDS;

    static {
        List<String> kinds = new ArrayList<>(List.of(RED_ROOMS, BLACKOUT_ZONE, MANILA_ROOM, PERIPHERAL_SHIFT, PITFALLS,
                ATRIUM, SHAFT_HALL, FRONT_OFFICE));
        kinds.addAll(Level0HiddenRooms.KINDS);
        KINDS = Collections.unmodifiableList(kinds);
    }

    /** Sites live inside one of these squares each (same grid as the safe rooms). */
    static final int SUB = SafeRoomPlan.CELL;

    /**
     * The highest block any anomaly builds (the set pieces' roof, with lights just under it).
     * Level0Layout reports it as its highest ceiling, so the client looks for lights up there.
     */
    public static final int HIGHEST_Y = Level0SetPieces.ROOF_Y;

    // Priority = index in STRUCTURES (lower wins a shared sub-square); blackouts come last.
    private static final int P_MANILA = 0;
    private static final int P_RED = 1;
    private static final int P_PITS = 2;
    private static final int P_PILLARS = 3;
    private static final int P_SET_PIECE = 4;
    private static final int P_HIDDEN = 5;
    private static final int P_BLACKOUT = 6;

    /** Red Rooms: the warning signs reach this far (blocks) beyond the site; must fit in the margin. */
    public static final int RED_RING = 8;
    /** The Manila Room's flickering wall is searched up to this far (blocks) outside the site. */
    private static final int MANILA_EXIT_SEARCH = 8;
    /** The set pieces' grid: one per square this size. */
    private static final int SET_PIECE_CELL = 256;
    /**
     * Front offices count as "nearby" this much further than the other kinds (blocks), so the
     * server tells clients about them early enough to hear the alarm from about 80 blocks away.
     */
    private static final int FRONT_OFFICE_REACH = 56;

    private final Level0Layout host;
    private final AnomalyGrid manila;
    private final AnomalyGrid red;
    private final AnomalyGrid pits;
    private final AnomalyGrid pillars;
    private final AnomalyGrid setPieces;
    private final AnomalyGrid hidden;
    private final AnomalyGrid blackout;
    /** The "carved out" kinds (replace the maze completely), in priority order. */
    private final AnomalyGrid[] structures;

    Level0AnomalyPlan(Level0Layout host) {
        this.host = host;
        // reach 0: every site stays inside its own grid square (see "No overlaps" above).
        this.manila = new AnomalyGrid(512, 0, this::planManila);
        this.red = new AnomalyGrid(384, 0, this::planRed);
        this.pits = new AnomalyGrid(256, 0, this::planPits);
        this.pillars = new AnomalyGrid(256, 0, this::planPillars);
        this.setPieces = new AnomalyGrid(SET_PIECE_CELL, 0, this::planSetPiece);
        this.hidden = new AnomalyGrid(256, 0, this::planHidden);
        this.blackout = new AnomalyGrid(SUB, 0, this::planBlackout);
        this.structures = new AnomalyGrid[] {manila, red, pits, pillars, setPieces, hidden};
    }

    // ==========================================================================================
    // Lookups (used by Level0Layout for every column, so keep them cheap)
    // ==========================================================================================

    /** The site covering (x, z): a structure, or a blackout zone (only inside its irregular shape). */
    @Nullable
    public AnomalySite at(long seed, int x, int z) {
        AnomalySite site = structureAt(seed, x, z);
        if (site != null) {
            return site;
        }
        return blackoutAt(seed, x, z);
    }

    /** The structure (Red Rooms, Manila Room, pit room, pillar hall, set piece, hidden room) covering (x, z) (moat included), or null. */
    @Nullable
    public AnomalySite structureAt(long seed, int x, int z) {
        for (AnomalyGrid grid : structures) {
            AnomalySite site = grid.at(seed, x, z);
            if (site != null) {
                return site;
            }
        }
        return null;
    }

    /** The blackout zone whose irregular shape covers (x, z), or null. */
    @Nullable
    public AnomalySite blackoutAt(long seed, int x, int z) {
        AnomalySite site = blackout.at(seed, x, z);
        return site != null && Level0Blackout.inside(site, x, z) ? site : null;
    }

    /** The Red Rooms of the 384x384 square containing (x, z), or null (for the warning signs around them). */
    @Nullable
    public AnomalySite redRoomsOfSquare(long seed, int x, int z) {
        return red.site(seed, Math.floorDiv(x, red.cellSize()), Math.floorDiv(z, red.cellSize()));
    }

    /** True if (x, z) is the maze wall the Manila Room's volunteers point to: the flickering wall to Level 1. */
    public boolean isManilaExit(long seed, int x, int z) {
        AnomalySite site = manila.site(seed, Math.floorDiv(x, manila.cellSize()), Math.floorDiv(z, manila.cellSize()));
        return site != null && site.anchorX() == x && site.anchorZ() == z && !site.contains(x, z);
    }

    public void near(long seed, int x, int z, int radius, Consumer<AnomalySite> out) {
        for (AnomalyGrid grid : structures) {
            if (grid == setPieces) {
                // Front offices are "near" from further away: their alarm carries ~80 blocks.
                grid.near(seed, x, z, radius + FRONT_OFFICE_REACH, site -> {
                    if (site.kind().equals(FRONT_OFFICE) || site.near(x, z, radius)) {
                        out.accept(site);
                    }
                });
            } else {
                grid.near(seed, x, z, radius, out);
            }
        }
        blackout.near(seed, x, z, radius, out);
    }

    @Nullable
    public AnomalySite nearest(long seed, int x, int z, String kind) {
        return switch (kind) {
            case MANILA_ROOM -> manila.nearest(seed, x, z, s -> true, 8);
            case RED_ROOMS -> red.nearest(seed, x, z, s -> true, 10);
            case PITFALLS -> pits.nearest(seed, x, z, s -> true, 12);
            case PERIPHERAL_SHIFT -> pillars.nearest(seed, x, z, s -> true, 12);
            case ATRIUM, SHAFT_HALL, FRONT_OFFICE -> setPieces.nearest(seed, x, z, s -> s.kind().equals(kind), 10);
            case BLACKOUT_ZONE -> blackout.nearest(seed, x, z, s -> true, 16);
            default -> Level0HiddenRooms.KINDS.contains(kind) ? hidden.nearest(seed, x, z, s -> s.kind().equals(kind), 12) : null;
        };
    }

    /**
     * The nearest big anomalous building of any kind (Red Rooms, Manila Room, pit room, pillar
     * hall or set piece; not the hidden rooms or blackouts), or null within the search. Used to
     * measure findability.
     */
    @Nullable
    AnomalySite nearestBuilding(long seed, int x, int z) {
        AnomalySite best = null;
        double bestDistance = Double.MAX_VALUE;
        for (AnomalyGrid grid : new AnomalyGrid[] {manila, red, pits, pillars, setPieces}) {
            AnomalySite site = grid.nearest(seed, x, z, s -> true, 6);
            if (site != null && site.distanceTo(x, z) < bestDistance) {
                bestDistance = site.distanceTo(x, z);
                best = site;
            }
        }
        return best;
    }

    /**
     * Where you arrive when the Front Office's EXIT door lets you out: in the lit lobby of the exit
     * stairwell nearest (x, z), a few steps in front of its door. Returns {x, z, the 2D data value
     * of the direction to face (towards the door)}. Static, with the layout passed in, because the
     * server behaviour has the layout but not this plan.
     */
    public static int[] exitLobby(LevelLayout layout, long seed, int x, int z) {
        int[] exit = layout.exits().nearest(seed, x, z);
        Direction doorSide = LevelLayout.stairwellDoorSide(seed, exit[0], exit[1]);
        int out = LevelLayout.STAIRWELL_RADIUS + 2;
        return new int[] {exit[0] + doorSide.getStepX() * out, exit[1] + doorSide.getStepZ() * out,
                doorSide.getOpposite().get2DDataValue()};
    }

    // ==========================================================================================
    // Building them (Level0Layout asks here for every column inside a structure)
    // ==========================================================================================

    /** Writes the blocks of a structure column. Allocation-free (the plan classes pack each column into an int). */
    void fill(long seed, AnomalySite site, int x, int z, int floor, int ceiling, LevelLayout.Column out) {
        switch (site.kind()) {
            case RED_ROOMS -> Level0RedRooms.fill(site, x, z, floor, ceiling, out);
            case MANILA_ROOM -> Level0ManilaRoom.fill(site, x, z, floor, ceiling, out);
            case PITFALLS -> Level0Pitfalls.fill(site, x, z, floor, ceiling, out);
            case PERIPHERAL_SHIFT -> Level0PillarHall.fill(site, x, z, floor, ceiling, out);
            case ATRIUM -> {
                int word = Level0Atrium.column(site, x, z);
                Direction facing = Level0SetPieces.toWorld(site, Level0SetPieces.localDir(Level0Atrium.facingBits(word)));
                if (Level0Atrium.isArrow(word)) {
                    facing = exitDirection(seed, site); // the balcony's arrows tell the truth
                }
                for (int y = floor; y <= Level0SetPieces.ROOF_Y; y++) {
                    put(Level0Atrium.piece(word, y, floor, ceiling), facing, 0, site, x, y, z, out);
                }
            }
            case SHAFT_HALL -> {
                int word = Level0ShaftHall.column(site, x, z);
                for (int y = floor; y <= Level0SetPieces.ROOF_Y; y++) {
                    put(Level0ShaftHall.piece(word, y, floor, ceiling), Direction.NORTH, 0, site, x, y, z, out);
                }
            }
            case FRONT_OFFICE -> {
                int word = Level0FrontOffice.column(site, x, z);
                Direction facing = Level0SetPieces.toWorld(site, Level0SetPieces.localDir(Level0FrontOffice.facingBits(word)));
                for (int y = floor; y <= Level0FrontOffice.ROOM_CEILING; y++) {
                    put(Level0FrontOffice.piece(word, y, floor, ceiling), facing, Level0FrontOffice.rugPart(word, y, floor),
                            site, x, y, z, out);
                }
            }
            default -> Level0HiddenRooms.fill(site, x, z, floor, ceiling, out);
        }
    }

    private static void put(int code, Direction facing, int extra, AnomalySite site, int x, int y, int z, LevelLayout.Column out) {
        if (code != Level0SetPieces.AIR) {
            BlockState state = Level0SetPieces.state(code, facing, extra, site.salt(), x, z);
            out.set(y, state);
        }
    }

    /** The way from the middle of a site to the nearest exit, rounded to one of four directions. */
    private Direction exitDirection(long seed, AnomalySite site) {
        int cx = (int) Math.floor(site.centerX());
        int cz = (int) Math.floor(site.centerZ());
        int[] exit = host.exits().nearest(seed, cx, cz);
        int dx = exit[0] - cx;
        int dz = exit[1] - cz;
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    // ==========================================================================================
    // Named spots for /backrooms anomaly visit (testing: jump straight to the mechanic)
    // ==========================================================================================

    /** The named spots of a kind (the set pieces' here, everyone else's in Level0VisitPoints). */
    public static List<String> visitPoints(String kind) {
        return switch (kind) {
            case ATRIUM -> Level0Atrium.VISIT_POINTS;
            case SHAFT_HALL -> Level0ShaftHall.VISIT_POINTS;
            case FRONT_OFFICE -> Level0FrontOffice.VISIT_POINTS;
            default -> Level0VisitPoints.names(kind);
        };
    }

    /** {x, feet y, z, yaw[, pitch]} of a named spot of a site, or null if the kind has no such spot. */
    @Nullable
    public static double[] visitPoint(Level0Layout layout, long seed, AnomalySite site, String name) {
        return switch (site.kind()) {
            case ATRIUM -> Level0Atrium.visit(site, name, layout.floorY());
            case SHAFT_HALL -> Level0ShaftHall.visit(site, name, layout.floorY());
            case FRONT_OFFICE -> Level0FrontOffice.visit(site, name, layout.floorY());
            default -> Level0VisitPoints.point(layout, seed, site, name);
        };
    }

    /** True if a structure column is a wall at eye level. */
    static boolean isSolid(AnomalySite site, int x, int z) {
        return switch (site.kind()) {
            case RED_ROOMS -> Level0RedRooms.isSolid(site, x, z);
            case MANILA_ROOM -> Level0ManilaRoom.isSolid(site, x, z);
            case PITFALLS -> Level0Pitfalls.isSolid(site, x, z);
            case PERIPHERAL_SHIFT -> Level0PillarHall.isSolid(site, x, z);
            case ATRIUM -> Level0Atrium.isSolid(site, x, z);
            case SHAFT_HALL -> Level0ShaftHall.isSolid(site, x, z);
            case FRONT_OFFICE -> Level0FrontOffice.isSolid(site, x, z);
            default -> Level0HiddenRooms.isSolid(site, x, z);
        };
    }

    /** True if you can stand on the ground floor of a structure column. */
    static boolean isWalkable(AnomalySite site, int x, int z) {
        return switch (site.kind()) {
            case RED_ROOMS -> Level0RedRooms.isWalkable(site, x, z);
            case MANILA_ROOM -> Level0ManilaRoom.isWalkable(site, x, z);
            case PITFALLS -> Level0Pitfalls.isWalkable(site, x, z);
            case PERIPHERAL_SHIFT -> Level0PillarHall.isWalkable(site, x, z);
            case ATRIUM -> Level0Atrium.isWalkable(site, x, z);
            case SHAFT_HALL -> Level0ShaftHall.isWalkable(site, x, z);
            case FRONT_OFFICE -> Level0FrontOffice.isWalkable(site, x, z);
            default -> Level0HiddenRooms.isWalkable(site, x, z);
        };
    }

    // ==========================================================================================
    // Planners (pure: hashing, arithmetic and the layout's own pure floor-plan functions)
    // ==========================================================================================

    @Nullable
    private AnomalySite planManila(long seed, int cellX, int cellZ) {
        int size = Level0ManilaRoom.BOX + 2 * Level0ManilaRoom.MOAT;
        // A wide margin: the flickering wall is searched for in the maze around the room.
        Spot spot = pickSpot(seed, cellX, cellZ, manila, 3101, 0.9, P_MANILA, size, size, 14, -1);
        if (spot == null) {
            return null;
        }
        int x1 = spot.x0 + size - 1;
        int z1 = spot.z0 + size - 1;
        long[] exit = manilaExit(seed, spot.x0, spot.z0, x1, z1, spot.salt);
        int ax = exit == null ? spot.x0 + size / 2 : (int) exit[0];
        int az = exit == null ? spot.z0 + size / 2 : (int) exit[1];
        int facing = exit == null ? 0 : (int) exit[2];
        return new AnomalySite(MANILA_ROOM, spot.x0, spot.z0, x1, z1, ax, az, facing, spot.salt);
    }

    /**
     * A plain maze wall near the Manila Room with open floor on one side: the "flickering wall"
     * the volunteers' notes talk about. Returns {x, z, facing of the open side} or null.
     */
    @Nullable
    private long[] manilaExit(long seed, int x0, int z0, int x1, int z1, long salt) {
        for (int r = 3; r <= MANILA_EXIT_SEARCH; r++) {
            int ax0 = x0 - r;
            int az0 = z0 - r;
            int side = x1 - x0 + 2 * r;          // steps along each side of the ring
            int perimeter = side * 4;
            int start = Hash.range(Hash.mix(salt + r), perimeter);
            for (int i = 0; i < perimeter; i++) {
                int k = (start + i) % perimeter;
                int s = k / side;
                int t = k % side;
                int x = s == 0 ? ax0 + t : s == 1 ? ax0 + side : s == 2 ? ax0 + side - t : ax0;
                int z = s == 0 ? az0 : s == 1 ? az0 + t : s == 2 ? az0 + side : az0 + side - t;
                if (!host.plainMazeWall(seed, x, z)) {
                    continue;
                }
                for (Direction face : Direction.Plane.HORIZONTAL) {
                    int nx = x + face.getStepX();
                    int nz = z + face.getStepZ();
                    if (host.plainMazeOpen(seed, nx, nz) && host.plainMazeOpen(seed, nx + face.getStepX(), nz + face.getStepZ())) {
                        return new long[] {x, z, face.get2DDataValue()};
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private AnomalySite planRed(long seed, int cellX, int cellZ) {
        long h = Hash.of(seed, cellX, cellZ, 3201);
        Direction facing = Direction.from2DDataValue(Hash.range(Hash.mix(h ^ 7), 4));
        boolean sideways = facing.getAxis() == Direction.Axis.X;
        int w = (sideways ? Level0RedRooms.DEPTH : Level0RedRooms.WIDTH) + 2 * Level0RedRooms.MOAT;
        int d = (sideways ? Level0RedRooms.WIDTH : Level0RedRooms.DEPTH) + 2 * Level0RedRooms.MOAT;
        Spot spot = pickSpot(seed, cellX, cellZ, red, 3201, 0.9, P_RED, w, d, RED_RING + 4, 0);
        if (spot == null) {
            return null;
        }
        // The anchor is the opening in the outer wall: where you walk in.
        AnomalySite site = new AnomalySite(RED_ROOMS, spot.x0, spot.z0, spot.x0 + w - 1, spot.z0 + d - 1,
                0, 0, facing.get2DDataValue(), spot.salt);
        int[] door = Level0RedRooms.entrance(site);
        return new AnomalySite(RED_ROOMS, site.minX(), site.minZ(), site.maxX(), site.maxZ(), door[0], door[1],
                site.facing(), site.salt());
    }

    @Nullable
    private AnomalySite planPits(long seed, int cellX, int cellZ) {
        int size = Level0Pitfalls.BOX + 2 * Level0Pitfalls.MOAT;
        Spot spot = pickSpot(seed, cellX, cellZ, pits, 3301, 0.8, P_PITS, size, size, 6, 1);
        if (spot == null) {
            return null;
        }
        return new AnomalySite(PITFALLS, spot.x0, spot.z0, spot.x0 + size - 1, spot.z0 + size - 1,
                spot.x0 + size / 2, spot.z0 + size / 2, 0, spot.salt);
    }

    @Nullable
    private AnomalySite planPillars(long seed, int cellX, int cellZ) {
        int size = Level0PillarHall.BOX + 2 * Level0PillarHall.MOAT;
        Spot spot = pickSpot(seed, cellX, cellZ, pillars, 3401, 0.8, P_PILLARS, size, size, 6, 2);
        if (spot == null) {
            return null;
        }
        // facing 0/1: the arch wall runs east-west / north-south.
        int orientation = (int) (spot.salt >>> 61) & 1;
        return new AnomalySite(PERIPHERAL_SHIFT, spot.x0, spot.z0, spot.x0 + size - 1, spot.z0 + size - 1,
                spot.x0 + size / 2, spot.z0 + size / 2, orientation, spot.salt);
    }

    /**
     * One set piece per 256x256 square. Which one follows the pattern (cellX + 2 cellZ) mod 3, so
     * any three squares in a row (or a 2x2 block) hold at least two different kinds; the pattern
     * is shifted per world. 32 tries (instead of four) to find a free spot in a free quarter: a
     * square with no set piece would leave a gap in the findability promise (with 8 tries about
     * one square in seven stayed empty, with 32 about one in thirty).
     */
    @Nullable
    private AnomalySite planSetPiece(long seed, int cellX, int cellZ) {
        int shift = Hash.range(Hash.mix(seed ^ 0x5E7B1ECE5L), SET_PIECES.length);
        String kind = SET_PIECES[Math.floorMod(cellX + 2 * cellZ + shift, SET_PIECES.length)];
        int size = switch (kind) {
            case ATRIUM -> Level0Atrium.BOX + 2 * Level0Atrium.MOAT;
            case SHAFT_HALL -> Level0ShaftHall.BOX + 2 * Level0ShaftHall.MOAT;
            default -> Level0FrontOffice.BOX + 2 * Level0FrontOffice.MOAT;
        };
        Spot spot = pickSpot(seed, cellX, cellZ, setPieces, 3701, 1.0, P_SET_PIECE, size, size, 6, -1, 32);
        if (spot == null) {
            return null;
        }
        // facing = how many quarter turns the plan is turned (Level0SetPieces): four looks per kind.
        int turns = (int) (spot.salt >>> 60) & 3;
        return new AnomalySite(kind, spot.x0, spot.z0, spot.x0 + size - 1, spot.z0 + size - 1,
                spot.x0 + size / 2, spot.z0 + size / 2, turns, spot.salt);
    }

    /** The small hidden rooms (Level0HiddenRooms picks the kind in a pattern, like the set pieces). */
    @Nullable
    private AnomalySite planHidden(long seed, int cellX, int cellZ) {
        int size = Level0HiddenRooms.BOX + 2 * Level0HiddenRooms.MOAT;
        Spot spot = pickSpot(seed, cellX, cellZ, hidden, 3601, 0.9, P_HIDDEN, size, size, 6, -1);
        if (spot == null) {
            return null;
        }
        int facing = (int) (spot.salt >>> 60) & 3;
        // The anchor goes to the moat in front of the way in, so /backrooms anomaly locate points at the entrance.
        return Level0HiddenRooms.withWayIn(new AnomalySite(Level0HiddenRooms.kindFor(seed, cellX, cellZ), spot.x0, spot.z0,
                spot.x0 + size - 1, spot.z0 + size - 1, spot.x0 + size / 2, spot.z0 + size / 2, facing, spot.salt));
    }

    @Nullable
    private AnomalySite planBlackout(long seed, int cellX, int cellZ) {
        long h = Hash.of(seed, cellX, cellZ, 3501);
        // Radii of the ellipse the irregular shape is built around: roughly 20-36 blocks across.
        int rx = 10 + Hash.range(Hash.mix(h ^ 11), 9);
        int rz = 10 + Hash.range(Hash.mix(h ^ 12), 9);
        int hx = Level0Blackout.halfExtent(rx);
        int hz = Level0Blackout.halfExtent(rz);
        Spot spot = pickSpot(seed, cellX, cellZ, blackout, 3501, 0.5, P_BLACKOUT, 2 * hx + 1, 2 * hz + 1, 4, 3);
        if (spot == null) {
            return null;
        }
        return new AnomalySite(BLACKOUT_ZONE, spot.x0, spot.z0, spot.x0 + 2 * hx, spot.z0 + 2 * hz,
                spot.x0 + hx, spot.z0 + hz, 0, spot.salt);
    }

    /** A free spot: north-west corner of the site bounds, plus per-site random bits. */
    private record Spot(int x0, int z0, long salt) {}

    /**
     * Picks where a {@code w} x {@code d} site goes in one grid square: one of its 128x128
     * sub-squares that no rarer kind has taken, at least {@code margin} blocks from the
     * sub-square's edges, and clear of exits, safe rooms and offices. Up to four tries; a try in
     * the kind's favourite kind of zone wins over the first free one.
     *
     * @param prefer which zone the kind likes: 0 tight maze, 1 rooms, 2 halls, 3 an unlit zone, -1 any
     */
    @Nullable
    private Spot pickSpot(long seed, int cellX, int cellZ, AnomalyGrid grid, int salt, double chance,
                          int priority, int w, int d, int margin, int prefer) {
        return pickSpot(seed, cellX, cellZ, grid, salt, chance, priority, w, d, margin, prefer, 4);
    }

    /** {@link #pickSpot} with a chosen number of tries. */
    @Nullable
    private Spot pickSpot(long seed, int cellX, int cellZ, AnomalyGrid grid, int salt, double chance,
                          int priority, int w, int d, int margin, int prefer, int tries) {
        long h = Hash.of(seed, cellX, cellZ, salt);
        if (Hash.unit(h) >= chance) {
            return null;
        }
        int subs = grid.cellSize() / SUB;
        Spot first = null;
        for (int attempt = 0; attempt < tries; attempt++) {
            long a = Hash.mix(h + 0x9E3779B97F4A7C15L * (attempt + 1));
            int subX = cellX * subs + Hash.range(a, subs);
            int subZ = cellZ * subs + Hash.range(Hash.mix(a ^ 1), subs);
            if (claimedByRarer(seed, priority, subX, subZ)) {
                continue;
            }
            int x0 = subX * SUB + margin + Hash.range(Hash.mix(a ^ 2), SUB - 2 * margin - w + 1);
            int z0 = subZ * SUB + margin + Hash.range(Hash.mix(a ^ 3), SUB - 2 * margin - d + 1);
            if (!host.rectFree(seed, x0 - 2, z0 - 2, x0 + w + 1, z0 + d + 1)) {
                continue;
            }
            Spot spot = new Spot(x0, z0, Hash.mix(a ^ 4));
            if (prefer < 0 || host.zonePrefers(seed, x0 + w / 2, z0 + d / 2, prefer)) {
                return spot;
            }
            if (first == null) {
                first = spot;
            }
        }
        return first;
    }

    /** True if a kind rarer than {@code priority} already has a site in sub-square (subX, subZ). */
    private boolean claimedByRarer(long seed, int priority, int subX, int subZ) {
        for (int i = 0; i < priority && i < structures.length; i++) {
            AnomalyGrid grid = structures[i];
            int c = grid.cellSize();
            AnomalySite site = grid.site(seed, Math.floorDiv(subX * SUB, c), Math.floorDiv(subZ * SUB, c));
            if (site != null && Math.floorDiv(site.minX(), SUB) == subX && Math.floorDiv(site.minZ(), SUB) == subZ) {
                return true;
            }
        }
        return false;
    }
}
