package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalyGrid;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Where the Poolrooms' anomalous areas are and what shape they have. Pure functions of the seed and
 * the room grid, like the rest of {@link PoolroomsLayout}, which builds the blocks from them.
 *
 * <p><b>Dark corners</b> ({@value #DARK_CORNER}): one corner of a lit dry or shallow room is closed
 * off by an L-shaped tile screen, leaving a 5x5 nook of dark, ankle-deep water with a dark tile
 * floor. Its 2-wide mouth hugs the outer wall, so the room's tubes never shine into it: a lightless
 * corner inside a lit room. Canon (Level 37's journal): "a feeling of dread... as they notice the
 * strange, lightless corners". Linger in one and you drift into the Sanguine Reservoir.
 * Top view of a north-west nook (mouth variant 0; # screen, ~ the nook, + the mouth):
 * <pre>
 *   #######
 *   #~~~~~#
 *   #~~~~~#
 *   #~~~~~#   . the rest of the room
 *   #~~~~~#
 *   #~~~~~#
 *   #++####
 * </pre>
 *
 * <p><b>Stair pits</b> ({@value #STAIR_PIT}): a whole room is a deep, dark flooded shaft. A 2-wide
 * staircase starts at the walkway and runs straight down into it, one block per step, to the
 * bottom 11 blocks below the surface; a dry deck runs around the top. No ledges, no lamps.
 * Canon (both wikis, Level 37): "staircases descend directly into deep pits of water". Side view
 * along the stairs (a = 1 is the top landing):
 * <pre>
 *   a:  1 2 3 4 ... 11 12 13
 *       . ~ ~ ~ ~ ~ ~ ~  ~  .     y41: the water's surface, level with the deck
 *       . # ~ ~ ~ ~ ~ ~  ~  .
 *       . # # ~ ~ ~ ~ ~  ~  .
 *         ...   (stairs)
 *       . # # # # # # #  ~  .     the bottom of the pit, y30
 * </pre>
 */
public final class PoolroomsAnomalyPlan {
    public static final String DARK_CORNER = "poolrooms/dark_corner";
    public static final String STAIR_PIT = "poolrooms/stair_pit";
    public static final List<String> KINDS = List.of(DARK_CORNER, STAIR_PIT);

    private static final int CELL = PoolroomsLayout.CELL;

    // ---- Stair pits ------------------------------------------------------------------------------
    /**
     * The pit's bottom block. The Poolrooms send you on to Level 9 below y30
     * ({@code BackroomsLevel.transferBelowY}), so the deepest you can sink is standing on this,
     * feet at y31: a pit is never a secret exit.
     */
    public static final int PIT_FLOOR_Y = 30;
    /** One pit per 9 x 9 rooms (126 x 126 blocks) at most ... */
    private static final int PIT_REGION_CELLS = 9;
    /** ... in this share of those squares. */
    private static final double PIT_SHARE = 0.7;

    // ---- Dark corners ----------------------------------------------------------------------------
    /** One dark corner per 7 x 7 rooms (98 x 98 blocks) at most ... */
    private static final int CORNER_REGION_CELLS = 7;
    /** ... in this share of those squares. */
    private static final double CORNER_SHARE = 0.7;
    /** The nook's water covers cell-local u, v = 1..5 of its corner ... */
    private static final int NOOK_SIZE = 5;
    /** ... and the screen walls stand on u = 6 and v = 6. */
    private static final int SCREEN = NOOK_SIZE + 1;
    /**
     * Dark corners only where their copy in the Sanguine Reservoir (RESERVOIR_OFFSET blocks east)
     * is part of the reservoir: keep this far inside the band it copies.
     */
    private static final int COPY_LIMIT = PoolroomsLayout.RESERVOIR_HALF - 64;

    /** How many rooms of a square get looked at before the square gives up (most rooms don't fit). */
    private static final int TRIES = 8;

    static final AnomalyGrid PITS = new AnomalyGrid(PIT_REGION_CELLS * CELL, 0, PoolroomsAnomalyPlan::planPit);
    static final AnomalyGrid CORNERS = new AnomalyGrid(CORNER_REGION_CELLS * CELL, 0, PoolroomsAnomalyPlan::planCorner);

    /** Column kinds this plan adds to the layout's (see PoolroomsLayout): the pit's open water ... */
    static final int PIT = 20;
    /** ... a step of the pit's staircase (tile up to {@link #stepTop}, water above) ... */
    static final int PIT_STEP = 21;
    /** ... and the dark corner's ankle-deep water (its mouth included). */
    static final int NOOK = 22;
    /** Not part of an anomaly: the room's own plan decides. */
    static final int NONE = -1;

    // ==========================================================================================
    // Planning
    // ==========================================================================================

    @Nullable
    private static AnomalySite planPit(long seed, int gx, int gz) {
        long h = Hash.of(seed, gx, gz, 6100);
        if (Hash.unit(h) >= PIT_SHARE) {
            return null;
        }
        for (int k = 0; k < TRIES; k++) {
            long hk = Hash.mix(h + k);
            // Never in the square's edge rooms, so two pits never stand side by side.
            int cx = gx * PIT_REGION_CELLS + 1 + Hash.range(hk, PIT_REGION_CELLS - 2);
            int cz = gz * PIT_REGION_CELLS + 1 + Hash.range(Hash.mix(hk ^ 1), PIT_REGION_CELLS - 2);
            if (PoolroomsLayout.anomalyRoomAllowed(seed, cx, cz, false)) {
                int facing = Hash.range(Hash.mix(hk ^ 2), 4); // the way the stairs go down
                int[] top = pitToCell(facing, 1, 3);           // the top landing, at the stairs
                return new AnomalySite(STAIR_PIT, cx * CELL + 1, cz * CELL + 1, cx * CELL + CELL - 1, cz * CELL + CELL - 1,
                        cx * CELL + top[0], cz * CELL + top[1], facing, Hash.mix(hk ^ 3));
            }
        }
        return null;
    }

    @Nullable
    private static AnomalySite planCorner(long seed, int gx, int gz) {
        long h = Hash.of(seed, gx, gz, 6200);
        if (Hash.unit(h) >= CORNER_SHARE) {
            return null;
        }
        for (int k = 0; k < TRIES; k++) {
            long hk = Hash.mix(h + k);
            int cx = gx * CORNER_REGION_CELLS + 1 + Hash.range(hk, CORNER_REGION_CELLS - 2);
            int cz = gz * CORNER_REGION_CELLS + 1 + Hash.range(Hash.mix(hk ^ 1), CORNER_REGION_CELLS - 2);
            if (Math.abs(cx * CELL) > COPY_LIMIT || Math.abs(cx * CELL + CELL) > COPY_LIMIT
                    || !PoolroomsLayout.anomalyRoomAllowed(seed, cx, cz, true) || pitOfCell(seed, cx, cz) != null) {
                continue;
            }
            long salt = Hash.mix(hk ^ 3);
            int corner = (int) (salt & 3);
            // The nook's water is u, v = 1..5; turn that into world columns for this corner.
            int[] a = nookToCell(corner, 1, 1);
            int[] b = nookToCell(corner, NOOK_SIZE, NOOK_SIZE);
            int minX = cx * CELL + Math.min(a[0], b[0]);
            int maxX = cx * CELL + Math.max(a[0], b[0]);
            int minZ = cz * CELL + Math.min(a[1], b[1]);
            int maxZ = cz * CELL + Math.max(a[1], b[1]);
            // The anchor is the innermost corner of the nook: the darkest spot, where the chants are.
            return new AnomalySite(DARK_CORNER, minX, minZ, maxX, maxZ, cx * CELL + a[0], cz * CELL + a[1],
                    mouthFacing(corner, salt), salt);
        }
        return null;
    }

    /** The pit in room (cx, cz), or null (cached, so cheap to ask for every column). */
    @Nullable
    static AnomalySite pitOfCell(long seed, int cx, int cz) {
        AnomalySite site = PITS.site(seed, Math.floorDiv(cx, PIT_REGION_CELLS), Math.floorDiv(cz, PIT_REGION_CELLS));
        return site != null && Math.floorDiv(site.minX(), CELL) == cx && Math.floorDiv(site.minZ(), CELL) == cz ? site : null;
    }

    /** The dark corner in room (cx, cz), or null. */
    @Nullable
    static AnomalySite cornerOfCell(long seed, int cx, int cz) {
        AnomalySite site = CORNERS.site(seed, Math.floorDiv(cx, CORNER_REGION_CELLS), Math.floorDiv(cz, CORNER_REGION_CELLS));
        return site != null && Math.floorDiv(site.minX(), CELL) == cx && Math.floorDiv(site.minZ(), CELL) == cz ? site : null;
    }

    // ==========================================================================================
    // Shapes. mx, mz are cell-local (1..13 inside the room).
    // ==========================================================================================

    /**
     * Pit-local coordinates: {@code a} runs down the stairs (1 = the top landing, 13 = the far
     * deck), {@code c} across them (the stairs are c = 3..4). Facing is the way the stairs go down.
     */
    static int pitA(int facing, int mx, int mz) {
        return switch (facing) {
            case 0 -> mz;          // south
            case 1 -> CELL - mx;   // west
            case 2 -> CELL - mz;   // north
            default -> mx;         // east
        };
    }

    static int pitC(int facing, int mx, int mz) {
        return switch (facing) {
            case 0 -> CELL - mx;
            case 1 -> CELL - mz;
            case 2 -> mx;
            default -> mz;
        };
    }

    /** Pit-local (a, c) back to cell-local {mx, mz}. */
    private static int[] pitToCell(int facing, int a, int c) {
        return switch (facing) {
            case 0 -> new int[] {CELL - c, a};
            case 1 -> new int[] {CELL - a, CELL - c};
            case 2 -> new int[] {c, CELL - a};
            default -> new int[] {a, c};
        };
    }

    /** The column kind of a pit room at cell-local (mx, mz): PIT, PIT_STEP, or NONE for the dry deck. */
    static int pitColumn(AnomalySite pit, int mx, int mz) {
        int a = pitA(pit.facing(), mx, mz);
        int c = pitC(pit.facing(), mx, mz);
        if (a < 2 || a > 12 || c < 3 || c > 10) {
            return NONE;
        }
        return c <= 4 ? PIT_STEP : PIT;
    }

    /** Top block of the step at cell-local (mx, mz): one block lower per step, down to the bottom. */
    static int stepTop(AnomalySite pit, int mx, int mz) {
        return PoolroomsLayout.SURFACE_Y - (pitA(pit.facing(), mx, mz) - 1);
    }

    /**
     * True for the deck columns right around the shaft: below the walkway they are dark tile all the
     * way down to the pit's bottom, so the pit's water never meets air.
     */
    static boolean pitLining(AnomalySite pit, int mx, int mz) {
        int a = pitA(pit.facing(), mx, mz);
        int c = pitC(pit.facing(), mx, mz);
        return a >= 1 && a <= 13 && c >= 2 && c <= 11;
    }

    /** True when the world column is over the pit's water or stairs (where you can sink). */
    public static boolean inShaft(AnomalySite pit, double x, double z) {
        // Cell-local from the site itself (its first column is mx = 1), so this also works for the
        // pits' copies in the Sanguine Reservoir, whose x is not a whole number of rooms away.
        int mx = (int) Math.floor(x) - pit.minX() + 1;
        int mz = (int) Math.floor(z) - pit.minZ() + 1;
        return pit.contains(x, z) && pitColumn(pit, mx, mz) != NONE;
    }

    /**
     * Nook-local coordinates: (u, v) = (1, 1) is the room's corner, growing into the room. Corner
     * bit 0: the east side of the room, bit 1: the south side.
     */
    private static int[] nookToCell(int corner, int u, int v) {
        return new int[] {(corner & 1) != 0 ? CELL - u : u, (corner & 2) != 0 ? CELL - v : v};
    }

    /** The column kind of a dark-corner room at cell-local (mx, mz): NOOK, a screen WALL, or NONE. */
    static int cornerColumn(AnomalySite corner, int mx, int mz, int wall) {
        int bits = (int) (corner.salt() & 3);
        int u = (bits & 1) != 0 ? CELL - mx : mx;
        int v = (bits & 2) != 0 ? CELL - mz : mz;
        if (u <= NOOK_SIZE && v <= NOOK_SIZE) {
            return NOOK;
        }
        if ((u == SCREEN && v <= SCREEN) || (v == SCREEN && u <= SCREEN)) {
            // The 2-wide mouth hugs the room's outer wall: along x (in the v screen) or along z.
            boolean mouthInV = ((corner.salt() >> 2) & 1) == 0;
            boolean mouth = mouthInV ? v == SCREEN && u <= 2 : u == SCREEN && v <= 2;
            return mouth ? NOOK : wall;
        }
        return NONE;
    }

    /** True where the room's tubes must not hang: over the nook and its screen. */
    static boolean overNook(AnomalySite corner, int mx, int mz) {
        int bits = (int) (corner.salt() & 3);
        int u = (bits & 1) != 0 ? CELL - mx : mx;
        int v = (bits & 2) != 0 ? CELL - mz : mz;
        return u <= SCREEN && v <= SCREEN;
    }

    /** The way the nook's mouth opens (into the room), as a 2D direction value (0 S, 1 W, 2 N, 3 E). */
    private static int mouthFacing(int corner, long salt) {
        boolean mouthInV = ((salt >> 2) & 1) == 0;
        if (mouthInV) {
            return (corner & 2) != 0 ? 2 : 0; // north out of a south nook, south out of a north one
        }
        return (corner & 1) != 0 ? 1 : 3;
    }

    /**
     * How long you must stay in a dark corner before you drift (20-30 s; the same on the server,
     * which moves you, and on the client, which fades the light in step).
     */
    public static int driftTicks(AnomalySite corner) {
        return 400 + (int) ((corner.salt() >>> 8) % 201);
    }

    private PoolroomsAnomalyPlan() {}
}
