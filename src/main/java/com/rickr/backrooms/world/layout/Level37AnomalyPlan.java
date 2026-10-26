package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalyGrid;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where Level 37's anomalous areas are, and what they change in the floor plan ({@link Level37Plan}
 * builds the normal column first, then this adds the site's own blocks on top). All of it is a
 * pure function of (seed, x, z), like the rest of the plan; what the sites DO is in
 * {@code world/anomaly/level37} (server) and {@code client/anomaly/level37} (client).
 *
 * <ul>
 *   <li><b>{@value #VOID_OPENING}</b> (Fandom, Level 37.2 "Sunlit Pools": beyond the openings
 *       there is only a white void of glowing gas, and wanderers who go into it inexplicably
 *       vanish; https://backrooms.fandom.com/wiki/Level_37.2). In a room with tall slit windows
 *       (west or north wall), two neighbouring slits are one doorway-sized gap that reaches all
 *       the way down to a knee-high lip: the white void sits right behind it, two blocks deep in
 *       the wall. The lip is a full block, so you have to climb into the gap on purpose; nobody
 *       wades in by accident. About one per 280 x 280 blocks.</li>
 *   <li><b>{@value #DARK_OPENING}</b> (Fandom, Level 37.2: "a dark opening" leads back to Level
 *       37, the main Poolrooms in this mod; https://backrooms.fandom.com/wiki/Level_37.2). The
 *       mirror of the Poolrooms' sunlit archway: on the dry deck of a pool room, a round arch
 *       framed in dark tile, filled with pitch darkness, in a corner where the fill lights are
 *       out (the canon's "lightless corners"). About one per 390 x 390 blocks.</li>
 *   <li><b>{@value #LOOPING_TUNNEL}</b> (the journal on both wikis: "looping hallways and
 *       nonsensical corridors"; https://backrooms-wiki.wikidot.com/level-37,
 *       https://backrooms.fandom.com/wiki/Level_37). Two cells of a dark round vaulted corridor
 *       that look exactly alike. Their side passages are walled up (a neighbour's passage toward
 *       it ends in a blind arch), so the server can quietly move you back one cell as you walk
 *       deeper. Only where the walled-up neighbours keep their own way out (west or north).
 *       About one per 190 x 190 blocks.</li>
 * </ul>
 * None of them is placed in a pit room, a hall, a rotunda, a spiral room or a tunnel hall.
 */
public final class Level37AnomalyPlan {
    public static final String VOID_OPENING = "level_37/void_opening";
    public static final String DARK_OPENING = "level_37/dark_opening";
    public static final String LOOPING_TUNNEL = "level_37/looping_tunnel";
    public static final List<String> KINDS = List.of(VOID_OPENING, DARK_OPENING, LOOPING_TUNNEL);

    private static final int CELL = Level37Plan.CELL;
    private static final int MID = Level37Plan.MID;
    private static final int FEET_Y = Level37Plan.FEET_Y;
    private static final int FLOOR_Y = Level37Plan.FLOOR_Y;

    // ---- Void opening --------------------------------------------------------------------------
    /** One region of this many cells (240 blocks) square has at most one void opening ... */
    private static final int VOID_REGION = 10;
    /** ... and this share of the regions have one. */
    private static final double VOID_SHARE = 0.9;
    /** The gap is this wide (two slits joined) ... */
    public static final int GAP_WIDTH = 2;
    /** ... open from the top of the lip (the lip fills the feet layer, y41) ... */
    public static final int GAP_BOTTOM_Y = FEET_Y + 1;
    /** ... up to here. */
    public static final int GAP_TOP_Y = FEET_Y + 4;

    // ---- Dark opening --------------------------------------------------------------------------
    /** One region of this many cells (288 blocks) square has at most one dark opening ... */
    private static final int DARK_REGION = 12;
    /** ... and this share of the regions have one. */
    private static final double DARK_SHARE = 0.9;
    /** The arch is 4 wide (cell-local 11..14), a half circle of radius 2 on a spring line at y43. */
    public static final int DARK_WIDTH = 4;
    private static final int DARK_FROM = MID - 2;
    private static final int DARK_SPRING_Y = FEET_Y + 2;

    // ---- Looping tunnel ------------------------------------------------------------------------
    /** Share of the eligible pairs of vaulted corridor cells that loop. */
    private static final double LOOP_SHARE = 0.6;
    /** How far the tunnel moves you back each time: one cell, so you land in the same-looking spot. */
    public static final int LOOP_SHIFT = CELL;

    /** Random cells tried per region before giving up (most cells are the wrong kind of room). */
    private static final int TRIES = 24;

    private static final AnomalyGrid VOIDS = new AnomalyGrid(VOID_REGION * CELL, 0, Level37AnomalyPlan::planVoid);
    private static final AnomalyGrid DARKS = new AnomalyGrid(DARK_REGION * CELL, 0, Level37AnomalyPlan::planDark);
    private static final AnomalyGrid LOOPS = new AnomalyGrid(CELL, 1, Level37AnomalyPlan::planLoop);

    private Level37AnomalyPlan() {}

    // ==========================================================================================
    // Lookups (Level37Layout's anomaly methods)
    // ==========================================================================================

    @Nullable
    public static AnomalySite at(long seed, int x, int z) {
        AnomalySite s = LOOPS.at(seed, x, z);
        if (s == null) {
            s = VOIDS.at(seed, x, z);
        }
        if (s == null) {
            s = DARKS.at(seed, x, z);
        }
        return s;
    }

    public static void near(long seed, int x, int z, int radius, Consumer<AnomalySite> out) {
        LOOPS.near(seed, x, z, radius, out);
        VOIDS.near(seed, x, z, radius, out);
        DARKS.near(seed, x, z, radius, out);
    }

    @Nullable
    public static AnomalySite nearest(long seed, int x, int z, String kind) {
        return switch (kind) {
            case VOID_OPENING -> VOIDS.nearest(seed, x, z, s -> true, 8);
            case DARK_OPENING -> DARKS.nearest(seed, x, z, s -> true, 6);
            case LOOPING_TUNNEL -> LOOPS.nearest(seed, x, z, s -> true, 48);
            default -> null;
        };
    }

    // ==========================================================================================
    // Shared geometry (server behaviours and client effects use the same numbers)
    // ==========================================================================================

    /**
     * How far (x, z) is in front of an opening's face, in blocks: 0 at the face (the white void,
     * or the darkness), growing into the room, negative inside it. The site's anchor is the first
     * open column in front of the face; its facing points from the room into the opening.
     */
    public static double inFront(AnomalySite s, double x, double z) {
        return switch (s.facing()) {
            case 1 -> x - s.anchorX();
            case 2 -> z - s.anchorZ();
            case 3 -> s.anchorX() + 1 - x;
            default -> s.anchorZ() + 1 - z;
        };
    }

    /** Sideways position across an opening: 0 at its first column, its width at the far side. */
    public static double across(AnomalySite s, double x, double z) {
        return s.facing() == 1 || s.facing() == 3 ? z - s.anchorZ() : x - s.anchorX();
    }

    /** Width of an opening in blocks. */
    public static int openingWidth(AnomalySite s) {
        return s.kind().equals(VOID_OPENING) ? GAP_WIDTH : DARK_WIDTH;
    }

    /** Middle of an opening's face, at head height. */
    public static Vec3 faceCentre(AnomalySite s) {
        double half = openingWidth(s) / 2.0;
        double y = s.kind().equals(VOID_OPENING) ? GAP_BOTTOM_Y + 2.0 : FEET_Y + 1.7;
        return switch (s.facing()) {
            case 1 -> new Vec3(s.anchorX(), y, s.anchorZ() + half);
            case 2 -> new Vec3(s.anchorX() + half, y, s.anchorZ());
            case 3 -> new Vec3(s.anchorX() + 1, y, s.anchorZ() + half);
            default -> new Vec3(s.anchorX() + half, y, s.anchorZ() + 1);
        };
    }

    /**
     * Distance (blocks, sideways) from (x, z) to an opening's face: 0 right at it. Off to the
     * side of the opening counts too, so the warning grows the same from any angle.
     */
    public static double distanceToOpening(AnomalySite s, double x, double z) {
        double front = Math.max(inFront(s, x, z), 0.0);
        double a = across(s, x, z);
        double side = Math.max(Math.max(-a, a - openingWidth(s)), 0.0);
        return Math.sqrt(front * front + side * side);
    }

    /** A looping tunnel runs along X (else along Z). */
    public static boolean loopAlongX(AnomalySite s) {
        return s.facing() == 3;
    }

    /** Position along a looping tunnel, from its first column (0) to its end ({@code 2 * LOOP_SHIFT}). */
    public static double loopAlong(AnomalySite s, double x, double z) {
        return loopAlongX(s) ? x - s.minX() : z - s.minZ();
    }

    // ==========================================================================================
    // Planners
    // ==========================================================================================

    /** The i-th random pick of a region (a fresh hash per try). */
    private static long pick(long h, int i) {
        return Hash.mix(h + (i + 1) * 0x9E3779B97F4A7C15L);
    }

    @Nullable
    private static AnomalySite planVoid(long seed, int rx, int rz) {
        long h = Hash.of(seed, rx, rz, 3770);
        if (Hash.unit(h) >= VOID_SHARE) {
            return null;
        }
        for (int i = 0; i < TRIES; i++) {
            long k = pick(h, i);
            int cx = rx * VOID_REGION + Hash.range(k, VOID_REGION);
            int cz = rz * VOID_REGION + Hash.range(Hash.mix(k ^ 1), VOID_REGION);
            AnomalySite site = voidIn(seed, cx, cz, Hash.mix(k ^ 2));
            if (site != null) {
                return site;
            }
        }
        return null;
    }

    /** A void opening in the slit-window wall of ordinary room (cx, cz), or null if it has none to give. */
    @Nullable
    private static AnomalySite voidIn(long seed, int cx, int cz, long salt) {
        if (Level37Plan.kind(Level37Plan.room(seed, cx, cz)) != Level37Plan.K_ROOM) {
            return null;
        }
        // West or north only: then the void (in the 3-thick wall band) is in this cell's own columns.
        int side = Level37Plan.roomSlitSide(seed, cx, cz);
        if (side != 0 && side != 2) {
            return null;
        }
        int edge = Level37Plan.edge(seed, cx, cz, side == 0 ? Level37Plan.WEST : Level37Plan.NORTH);
        int first = Hash.range(salt, 7);
        for (int i = 0; i < 7; i++) {
            int g = 9 + (first + i) % 7; // gap columns g, g + 1 within 9..16 (the slits run 7..19)
            if (!clearOfPassage(edge, g - 1, g + GAP_WIDTH)) {
                continue;
            }
            int x0 = cx * CELL;
            int z0 = cz * CELL;
            // Covers the wall and the part of the room within ~8 blocks of the gap.
            int lo = Math.max(Level37Plan.BAND, g - 5);
            int hi = Math.min(CELL - 1, g + GAP_WIDTH + 4);
            if (side == 0) {
                return new AnomalySite(VOID_OPENING, x0, z0 + lo, x0 + 12, z0 + hi, x0 + 3, z0 + g, 1, Hash.mix(salt ^ 3));
            }
            return new AnomalySite(VOID_OPENING, x0 + lo, z0, x0 + hi, z0 + 12, x0 + g, z0 + 3, 2, Hash.mix(salt ^ 3));
        }
        return null;
    }

    @Nullable
    private static AnomalySite planDark(long seed, int rx, int rz) {
        long h = Hash.of(seed, rx, rz, 3772);
        if (Hash.unit(h) >= DARK_SHARE) {
            return null;
        }
        for (int i = 0; i < TRIES; i++) {
            long k = pick(h, i);
            int cx = rx * DARK_REGION + Hash.range(k, DARK_REGION);
            int cz = rz * DARK_REGION + Hash.range(Hash.mix(k ^ 1), DARK_REGION);
            AnomalySite site = darkIn(seed, cx, cz, Hash.mix(k ^ 2));
            if (site != null) {
                return site;
            }
        }
        return null;
    }

    /** A dark opening in the west or north wall of pool room (cx, cz), or null if neither wall is free. */
    @Nullable
    private static AnomalySite darkIn(long seed, int cx, int cz, long salt) {
        if (Level37Plan.kind(Level37Plan.room(seed, cx, cz)) != Level37Plan.K_POOL) {
            return null;
        }
        int windows = Level37Plan.poolWindowSide(seed, cx, cz);
        boolean westFirst = Hash.unit(salt) < 0.5;
        for (int i = 0; i < 2; i++) {
            int side = (i == 0) == westFirst ? 0 : 2;
            if (side == windows) {
                continue; // that wall is 2 thick with windows high up: no room for an arch
            }
            int edge = Level37Plan.edge(seed, cx, cz, side == 0 ? Level37Plan.WEST : Level37Plan.NORTH);
            // The arch and its dark frame (cells 9..16) plus a column of wall either side.
            if (!clearOfPassage(edge, DARK_FROM - 3, DARK_FROM + DARK_WIDTH + 2)) {
                continue;
            }
            int x0 = cx * CELL;
            int z0 = cz * CELL;
            long s = Hash.mix(salt ^ 5);
            if (side == 0) {
                return new AnomalySite(DARK_OPENING, x0, z0 + 6, x0 + 9, z0 + 19, x0 + 2, z0 + DARK_FROM, 1, s);
            }
            return new AnomalySite(DARK_OPENING, x0 + 6, z0, x0 + 19, z0 + 9, x0 + DARK_FROM, z0 + 2, 2, s);
        }
        return null;
    }

    /** True if the passage through wall {@code edge} (if any) stays outside cells from..to. */
    private static boolean clearOfPassage(int edge, int from, int to) {
        if (Level37Plan.edgeState(edge) != Level37Plan.E_ARCH) {
            return true;
        }
        int start = Level37Plan.edgeStart(edge);
        return to < start || from > start + Level37Plan.edgeWidth(edge) - 1;
    }

    /**
     * A looping tunnel starting at cell (cx, cz): it and the next cell along its axis must both be
     * dark vaulted corridor running that way. Pairs start on even cells, so two never overlap.
     */
    @Nullable
    private static AnomalySite planLoop(long seed, int cx, int cz) {
        int a = Level37Plan.room(seed, cx, cz);
        if (!vault(a)) {
            return null;
        }
        boolean alongX = Level37Plan.alongX(a);
        if (Math.floorMod(alongX ? cx : cz, 2) != 0) {
            return null;
        }
        int bx = alongX ? cx + 1 : cx;
        int bz = alongX ? cz : cz + 1;
        int b = Level37Plan.room(seed, bx, bz);
        if (!vault(b) || Level37Plan.alongX(b) != alongX) {
            return null;
        }
        // The cells beside the tunnel on its south (or east) side own the wall we wall up; each must
        // keep its own way out to the west (or north), so no room is ever sealed.
        if (alongX ? !keepsWayOut(seed, cx, cz + 1, Level37Plan.NORTH) || !keepsWayOut(seed, bx, cz + 1, Level37Plan.NORTH)
                : !keepsWayOut(seed, cx + 1, cz, Level37Plan.WEST) || !keepsWayOut(seed, cx + 1, bz, Level37Plan.WEST)) {
            return null;
        }
        long h = Hash.of(seed, cx, cz, 3774);
        if (Hash.unit(h) >= LOOP_SHARE) {
            return null;
        }
        int x0 = cx * CELL;
        int z0 = cz * CELL;
        int x1 = bx * CELL + CELL - 1;
        int z1 = bz * CELL + CELL - 1;
        // Anchor: the middle line of the tube where it starts; facing: the way it runs (E or S).
        return alongX
                ? new AnomalySite(LOOPING_TUNNEL, x0, z0, x1, z1, x0, z0 + MID, 3, Hash.mix(h))
                : new AnomalySite(LOOPING_TUNNEL, x0, z0, x1, z1, x0 + MID, z0, 0, Hash.mix(h));
    }

    private static boolean vault(int room) {
        return Level37Plan.kind(room) == Level37Plan.K_CORRIDOR && Level37Plan.variant(room) == Level37Plan.COR_VAULT;
    }

    /**
     * Cell (cx, cz) loses its passage through wall {@code sealed} (its own west or north wall):
     * fine if there was none, or if its other own wall (north or west) is open.
     */
    private static boolean keepsWayOut(long seed, int cx, int cz, int sealed) {
        if (Level37Plan.edgeState(Level37Plan.edge(seed, cx, cz, sealed)) == Level37Plan.E_SOLID) {
            return true;
        }
        int other = sealed == Level37Plan.WEST ? Level37Plan.NORTH : Level37Plan.WEST;
        return Level37Plan.edgeState(Level37Plan.edge(seed, cx, cz, other)) != Level37Plan.E_SOLID;
    }

    // ==========================================================================================
    // Building (called by Level37Plan.column for every column inside a site)
    // ==========================================================================================

    static void build(AnomalySite site, int x, int z, int[] out) {
        // Sites are planned in one cell (void, dark) and lie on its west or north wall (facing 1 or 2).
        boolean west = site.facing() == 1;
        int depth = west ? Math.floorMod(x, CELL) : Math.floorMod(z, CELL);
        int along = west ? Math.floorMod(z, CELL) : Math.floorMod(x, CELL);
        switch (site.kind()) {
            case VOID_OPENING -> buildVoid(site, west, depth, along, out);
            case DARK_OPENING -> buildDark(west, depth, along, out);
            default -> {
                // The looping tunnel is only walled up (Level37Plan.plan); it adds no blocks.
            }
        }
    }

    /**
     * The gap: the slit wall is 2 thick (depth 3 and 4, the white void normally sits in depth 3).
     * Here both layers are open over a lip, and the void moves back into the band (depth 2).
     */
    private static void buildVoid(AnomalySite site, boolean west, int depth, int along, int[] out) {
        int g = west ? Math.floorMod(site.anchorZ(), CELL) : Math.floorMod(site.anchorX(), CELL);
        if (along < g || along >= g + GAP_WIDTH) {
            return;
        }
        int wall = Level37Plan.code(Level37Plan.WALL, 0);
        if (depth == 2) {
            // Light travels out of the void into the room (east from a west wall, south from a north one).
            int light = Level37Plan.code(Level37Plan.VOID, west ? Level37Plan.EAST3 : Level37Plan.SOUTH3);
            for (int y = GAP_BOTTOM_Y; y <= GAP_TOP_Y; y++) {
                out[y] = light;
            }
        } else if (depth == 3 || depth == 4) {
            out[FEET_Y] = wall; // the lip: a full block, so you climb in on purpose
            for (int y = GAP_BOTTOM_Y; y <= GAP_TOP_Y; y++) {
                out[y] = Level37Plan.AIR;
            }
            out[GAP_TOP_Y + 1] = wall; // (closes off a slit window that ran higher here)
        }
    }

    /**
     * The dark opening: a round arch (depth 2, the face of the wall band) framed in dark tile, the
     * darkness filling the band behind it (depth 1), solid tile behind that (depth 0). The fill
     * lights near it are left out: a lightless corner of a bright room.
     */
    private static void buildDark(boolean west, int depth, int along, int[] out) {
        for (int y = 0; y < out.length; y++) {
            if (Level37Plan.type(out[y]) == Level37Plan.LIGHT) {
                out[y] = Level37Plan.AIR;
            }
        }
        int deep = Level37Plan.code(Level37Plan.DEEP, 0);
        boolean inArch = along >= DARK_FROM && along < DARK_FROM + DARK_WIDTH;
        int a = along - MID;
        if (depth == 1 && inArch) {
            out[FLOOR_Y] = deep;
            for (int y = FEET_Y; y <= FEET_Y + 5; y++) {
                boolean open = Level37Plan.arch(a, y - DARK_SPRING_Y, 2, west) == Level37Plan.C_IN;
                out[y] = Level37Plan.code(open ? Level37Plan.DARK : Level37Plan.DEEP, 0);
            }
        } else if (depth == 2 && along >= DARK_FROM - 2 && along < DARK_FROM + DARK_WIDTH + 2) {
            for (int y = FEET_Y; y <= FEET_Y + 6; y++) {
                int v = inArch && y <= FEET_Y + 5 ? Level37Plan.arch(a, y - DARK_SPRING_Y, 2, west) : Level37Plan.C_OUT;
                out[y] = v == Level37Plan.C_IN ? Level37Plan.AIR : v == Level37Plan.C_OUT ? deep : Level37Plan.code(Level37Plan.PIECE, v);
            }
            if (inArch) {
                out[FLOOR_Y] = deep;
            }
        } else if (depth == 3 && along >= DARK_FROM - 1 && along <= DARK_FROM + DARK_WIDTH) {
            out[FLOOR_Y] = deep; // a strip of dark tile on the deck in front of it
            if (Level37Plan.type(out[FEET_Y + 1]) == Level37Plan.RAIL) {
                out[FEET_Y + 1] = Level37Plan.AIR; // no handrail across the arch
            }
        }
    }
}
