package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalyGrid;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * WHERE Level 0's anomalous areas are (see {@code world/anomaly} for the framework). Five kinds,
 * each with its own {@link AnomalyGrid}:
 * <ul>
 *   <li>{@link #MANILA_ROOM}: one per 512x512 square (90%): very rare, like the wiki's "15% chance
 *       after a day of walking".</li>
 *   <li>{@link #RED_ROOMS}: one per 384x384 square (90%).</li>
 *   <li>{@link #PITFALLS} and {@link #PERIPHERAL_SHIFT}: one each per 256x256 square (80%).</li>
 *   <li>{@link #BLACKOUT_ZONE}: one per 128x128 square (50%), the most common.</li>
 * </ul>
 * Altogether a player walking normally meets one of them every couple of minutes, while each kind
 * stays special.
 *
 * <p><b>No overlaps.</b> Every site sits entirely inside one 128x128 "sub-square" (the same squares
 * safe rooms use), with a margin to its edges. Two kinds clash only if they pick the same
 * sub-square; then the rarer kind wins (order: Manila Room, Red Rooms, Pitfalls, pillar hall,
 * blackout). A site also never covers an exit stairwell (and its lobby), a safe room or a front
 * office: the planner tries a few spots and gives up if none is free.
 *
 * <p><b>Sensible spots.</b> Each kind prefers the part of the maze it belongs in: the pillar hall a
 * "big open hall" zone, the pit room an ordinary-rooms zone, the Red Rooms the tight maze (you turn
 * a corner and you're in), blackouts the half of the level that is already dark.
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
    public static final List<String> KINDS = List.of(RED_ROOMS, BLACKOUT_ZONE, MANILA_ROOM, PERIPHERAL_SHIFT, PITFALLS);

    /** Sites live inside one of these squares each (same grid as the safe rooms). */
    static final int SUB = SafeRoomPlan.CELL;

    // Priority = index in STRUCTURES (lower wins a shared sub-square); blackouts come last.
    private static final int P_MANILA = 0;
    private static final int P_RED = 1;
    private static final int P_PITS = 2;
    private static final int P_PILLARS = 3;
    private static final int P_BLACKOUT = 4;

    /** Red Rooms: the warning signs reach this far (blocks) beyond the site; must fit in the margin. */
    public static final int RED_RING = 8;
    /** The Manila Room's flickering wall is searched up to this far (blocks) outside the site. */
    private static final int MANILA_EXIT_SEARCH = 8;

    private final Level0Layout host;
    private final AnomalyGrid manila;
    private final AnomalyGrid red;
    private final AnomalyGrid pits;
    private final AnomalyGrid pillars;
    private final AnomalyGrid blackout;
    /** The four "carved out" kinds (replace the maze completely), in priority order. */
    private final AnomalyGrid[] structures;

    Level0AnomalyPlan(Level0Layout host) {
        this.host = host;
        // reach 0: every site stays inside its own grid square (see "No overlaps" above).
        this.manila = new AnomalyGrid(512, 0, this::planManila);
        this.red = new AnomalyGrid(384, 0, this::planRed);
        this.pits = new AnomalyGrid(256, 0, this::planPits);
        this.pillars = new AnomalyGrid(256, 0, this::planPillars);
        this.blackout = new AnomalyGrid(SUB, 0, this::planBlackout);
        this.structures = new AnomalyGrid[] {manila, red, pits, pillars};
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

    /** The Red Rooms / Manila Room / pit room / pillar hall covering (x, z) (moat included), or null. */
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
            grid.near(seed, x, z, radius, out);
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
            case BLACKOUT_ZONE -> blackout.nearest(seed, x, z, s -> true, 16);
            default -> null;
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
        long h = Hash.of(seed, cellX, cellZ, salt);
        if (Hash.unit(h) >= chance) {
            return null;
        }
        int subs = grid.cellSize() / SUB;
        Spot first = null;
        for (int attempt = 0; attempt < 4; attempt++) {
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
