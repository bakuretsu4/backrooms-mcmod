package com.rickr.backrooms.world.layout;

import static com.rickr.backrooms.world.layout.Level0SetPieces.*;

import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.ArrayList;
import java.util.List;

/**
 * The Shaft Hall: a big, low, mostly dark hall whose ceiling is full of holes. Each hole (1x1,
 * 2x2 or the odd 3x3) opens into a tile-lined shaft that rises 18 blocks to the top of the world.
 * About a third of the shafts have a working tube right at the top, so from below you see small
 * glowing squares far overhead and notice the holes; the rest are black. Things move up there:
 * the client plays scrapes and knocks from the top of a shaft now and then, and once in a while a
 * ceiling tile works loose and falls the whole way down (the server, ShaftHallBehavior). A few
 * broken tiles already lie on the carpet, and the carpet is damp under some shafts.
 *
 * <p>Plan (local frame, see {@link Level0SetPieces}):
 * <ul>
 *   <li>a 44x44 box, outer wall 2 thick with two 4-wide doorways per side;</li>
 *   <li>four 2x2 posts holding the roof (the only walls inside: it is one open room);</li>
 *   <li>shafts on a loose 7-block lattice (6x6 slots, about 70% used, each shifted a block at
 *       random), so the holes look scattered, never like a grid;</li>
 *   <li>three small lit islands at the 3-high ceiling (2x2 troffers, 4 apart); the rest of the
 *       hall is lit only by what spills down the lit shafts, which is almost nothing.</li>
 * </ul>
 *
 * <p>Pure function of (site, x, z); {@link #column} packs a column into an int and
 * {@link #piece} reads one block of it.
 */
public final class Level0ShaftHall {
    public static final int BOX = 44;
    public static final int MOAT = 2;
    private static final int HALF = BOX / 2;
    /** Shaft slots: the first at local 4, then every 7 blocks, six per axis. */
    private static final int SLOT_FROM = 4;
    private static final int SLOT_PITCH = 7;
    private static final int SLOTS = 6;
    /** Post columns (two per axis, 2 thick): between the shaft slots. */
    private static final int POST_A = 15;
    private static final int POST_B = 29;

    // Roles (bits 0-3).
    private static final int R_MOAT = 0;
    private static final int R_FLOOR = 1;
    private static final int R_WALL = 2;
    private static final int R_DOORWAY = 3;
    private static final int R_POST = 4;
    private static final int R_HOLE = 5;
    /** Floor under the 3-high ceiling right next to a hole: its tile lining runs up to the roof. */
    private static final int R_LINING = 6;

    private static final int LIGHT_BIT = 1 << 4;      // a troffer in the 3-high ceiling
    private static final int DEAD_BIT = 1 << 5;       // ... that is dead
    private static final int TOP_LIGHT_BIT = 1 << 6;  // a hole with a lit tube at the top of its shaft
    private static final int CABLE_BIT = 1 << 7;      // a cable hanging near the top of a dark shaft
    private static final int DEBRIS_BIT = 1 << 8;     // a fallen tile lying on the carpet
    private static final int WET_BIT = 1 << 9;

    private Level0ShaftHall() {}

    /** Per slot (kx, kz): 0 = no shaft, else size (1-3) | shift along u (0/1) << 2 | shift along v << 3. */
    private static int slot(AnomalySite site, int kx, int kz) {
        long h = Hash.of(site.salt(), kx, kz, 81);
        if (Hash.unit(h) >= 0.7) {
            return 0;
        }
        double r = Hash.unit(Hash.mix(h ^ 1));
        int size = r < 0.55 ? 1 : r < 0.9 ? 2 : 3;
        if (kx == SLOTS - 1 || kz == SLOTS - 1) {
            size = Math.min(size, 2); // a 3x3 shifted hole in the last slot would reach into the outer wall
        }
        return size | Hash.range(Hash.mix(h ^ 2), 2) << 2 | Hash.range(Hash.mix(h ^ 3), 2) << 3;
    }

    /** The slot index of local coordinate {@code u} and its offset in the slot, packed (index << 3 | offset), or -1. */
    private static int slotOf(int u) {
        if (u < SLOT_FROM) {
            return -1;
        }
        int k = (u - SLOT_FROM) / SLOT_PITCH;
        if (k >= SLOTS) {
            return -1;
        }
        return k << 3 | (u - SLOT_FROM - k * SLOT_PITCH);
    }

    /** True if local column (u, v) is inside a ceiling hole. */
    private static boolean hole(AnomalySite site, int u, int v) {
        int a = slotOf(u);
        int b = slotOf(v);
        if (a < 0 || b < 0) {
            return false;
        }
        int s = slot(site, a >> 3, b >> 3);
        if (s == 0) {
            return false;
        }
        int size = s & 3;
        int ou = (a & 7) - (s >> 2 & 1);
        int ov = (b & 7) - (s >> 3 & 1);
        return ou >= 0 && ou < size && ov >= 0 && ov < size;
    }

    /** True if local column (u, v) is the north-west column of its hole (where per-hole details go). */
    private static boolean holeCorner(AnomalySite site, int u, int v) {
        return hole(site, u, v) && !hole(site, u - 1, v) && !hole(site, u, v - 1);
    }

    private static boolean isPost(int u, int v) {
        boolean pu = u == POST_A || u == POST_A + 1 || u == POST_B || u == POST_B + 1;
        boolean pv = v == POST_A || v == POST_A + 1 || v == POST_B || v == POST_B + 1;
        return pu && pv;
    }

    /** Doorways: two per side, 4 wide, at places picked per site. */
    private static boolean doorway(AnomalySite site, int side, int along) {
        long h = Hash.mix(site.salt() + 97L * side);
        int a = -15 + Hash.range(h, 8);           // -15..-8
        int b = 4 + Hash.range(Hash.mix(h ^ 5), 8); // 4..11
        return (along >= a && along < a + 4) || (along >= b && along < b + 4);
    }

    /** The light islands: three of five candidate places (the middle and four quarters). */
    private static boolean island(AnomalySite site, int u, int v) {
        int[][] centres = {{HALF, HALF}, {9, 9}, {9, 34}, {34, 9}, {34, 34}};
        int skip1 = Hash.range(Hash.mix(site.salt() ^ 0x51), centres.length);
        int skip2 = (skip1 + 2) % centres.length;
        for (int i = 0; i < centres.length; i++) {
            if (i == skip1 || i == skip2) {
                continue;
            }
            int du = u - centres[i][0];
            int dv = v - centres[i][1];
            if ((du == -2 || du == 2) && (dv == -2 || dv == 2)) {
                return true;
            }
        }
        return false;
    }

    static int column(AnomalySite site, int x, int z) {
        int u = Level0SetPieces.u(site, MOAT, x, z);
        int v = Level0SetPieces.v(site, MOAT, x, z);
        if (u < 0 || u >= BOX || v < 0 || v >= BOX) {
            return R_MOAT;
        }
        int dx = u - HALF;
        int dz = v - HALF;
        int ax = dx >= 0 ? dx : -dx - 1;
        int az = dz >= 0 ? dz : -dz - 1;
        if (Math.max(ax, az) >= HALF - 2) {
            boolean corner = Math.min(ax, az) >= HALF - 4;
            int side = ax >= az ? (dx < 0 ? W : E) : (dz < 0 ? N : S);
            int along = ax >= az ? dz : dx;
            return !corner && doorway(site, side, along) ? R_DOORWAY : R_WALL;
        }
        if (isPost(u, v)) {
            return R_POST;
        }
        if (hole(site, u, v)) {
            int word = R_HOLE;
            if (holeCorner(site, u, v)) {
                long h = Hash.of(site.salt(), u, v, 82);
                double r = Hash.unit(h);
                if (r < 0.35) {
                    word |= TOP_LIGHT_BIT;
                } else if (r < 0.5) {
                    word |= CABLE_BIT;
                }
                double floor = Hash.unit(Hash.mix(h ^ 1));
                if (floor < 0.18) {
                    word |= DEBRIS_BIT;
                } else if (floor < 0.32) {
                    word |= WET_BIT;
                }
            } else if (topLit(site, u, v)) {
                word |= TOP_LIGHT_BIT; // every block of a lit hole's top glows
            }
            return word;
        }
        int word = R_FLOOR;
        for (int ou = -1; ou <= 1 && word == R_FLOOR; ou++) {
            for (int ov = -1; ov <= 1; ov++) {
                if ((ou != 0 || ov != 0) && hole(site, u + ou, v + ov)) {
                    word = R_LINING;
                    break;
                }
            }
        }
        if (island(site, u, v)) {
            word |= LIGHT_BIT;
            if (Hash.unit(site.salt(), x, z, 83) < 0.25) {
                word |= DEAD_BIT;
            }
        }
        return word;
    }

    /** True if the hole containing (u, v) has a lit tube at the top (decided at its corner column). */
    private static boolean topLit(AnomalySite site, int u, int v) {
        int cu = u;
        int cv = v;
        while (hole(site, cu - 1, cv)) {
            cu--;
        }
        while (hole(site, cu, cv - 1)) {
            cv--;
        }
        return Hash.unit(Hash.of(site.salt(), cu, cv, 82)) < 0.35;
    }

    static int piece(int word, int y, int floor, int ceiling) {
        int role = word & 15;
        if (y == floor) {
            return CARPET;
        }
        if (y < ceiling) {
            if (role == R_WALL || role == R_POST) {
                return y == floor + 1 ? BASEBOARD : WALLPAPER;
            }
            if (y == floor + 1 && role == R_HOLE) {
                if ((word & DEBRIS_BIT) != 0) {
                    return DEBRIS;
                }
                if ((word & WET_BIT) != 0) {
                    return WET_CARPET;
                }
            }
            return AIR;
        }
        if (role == R_HOLE) {
            if (y == ROOF_Y) {
                return (word & TOP_LIGHT_BIT) != 0 ? TUBE : TILE;
            }
            return y == ROOF_Y - 1 && (word & CABLE_BIT) != 0 ? CABLE : AIR;
        }
        if (y == ceiling) {
            return (word & LIGHT_BIT) == 0 ? TILE : (word & DEAD_BIT) != 0 ? TUBE_DEAD : TUBE;
        }
        // The tile lining of the shafts, all the way up.
        return role == R_LINING && y <= ROOF_Y ? TILE : AIR;
    }

    static boolean isSolid(AnomalySite site, int x, int z) {
        int role = column(site, x, z) & 15;
        return role == R_WALL || role == R_POST;
    }

    static boolean isWalkable(AnomalySite site, int x, int z) {
        int role = column(site, x, z) & 15;
        return role != R_WALL && role != R_POST;
    }

    // ==========================================================================================
    // For the server and client effects
    // ==========================================================================================

    /**
     * One shaft: the world column of its north-west corner, its size, and whether it is lit at
     * the top. Computed once per site by whoever needs it (the client's sounds, the falling tiles).
     */
    public record Shaft(int x, int z, int size, boolean lit) {
        public double centerX() {
            return x + size * 0.5;
        }

        public double centerZ() {
            return z + size * 0.5;
        }
    }

    /** Every shaft of the hall. Allocates: call once per site and keep the list. */
    public static List<Shaft> shafts(AnomalySite site) {
        List<Shaft> out = new ArrayList<>();
        for (int kx = 0; kx < SLOTS; kx++) {
            for (int kz = 0; kz < SLOTS; kz++) {
                int s = slot(site, kx, kz);
                if (s == 0) {
                    continue;
                }
                int size = s & 3;
                int u = SLOT_FROM + kx * SLOT_PITCH + (s >> 2 & 1);
                int v = SLOT_FROM + kz * SLOT_PITCH + (s >> 3 & 1);
                if (isPost(u, v)) {
                    continue; // never happens with these numbers, but a post would win the column
                }
                boolean lit = Hash.unit(Hash.of(site.salt(), u, v, 82)) < 0.35;
                // The shaft's north-west corner in WORLD terms: the turned frame may flip it, so
                // take the smallest x and z of its columns.
                int x0 = Integer.MAX_VALUE;
                int z0 = Integer.MAX_VALUE;
                for (int i = 0; i < size; i++) {
                    for (int j = 0; j < size; j++) {
                        x0 = Math.min(x0, Level0SetPieces.worldX(site, MOAT, u + i, v + j));
                        z0 = Math.min(z0, Level0SetPieces.worldZ(site, MOAT, u + i, v + j));
                    }
                }
                out.add(new Shaft(x0, z0, size, lit));
            }
        }
        return out;
    }

    /** True inside the outer walls (not the moat or the walls themselves). */
    public static boolean inside(AnomalySite site, int x, int z) {
        int u = Level0SetPieces.u(site, MOAT, x, z);
        int v = Level0SetPieces.v(site, MOAT, x, z);
        return u >= 2 && v >= 2 && u < BOX - 2 && v < BOX - 2;
    }

    /** Named spots for /backrooms anomaly visit (testing). */
    static final List<String> VISIT_POINTS = List.of("lit_shaft");

    /**
     * {x, feet y, z, yaw, pitch}: "lit_shaft" = under the middle of a shaft lit at the top (the
     * first one; any shaft if none is lit), looking straight up. Null for an unknown name.
     */
    @org.jetbrains.annotations.Nullable
    static double[] visit(AnomalySite site, String name, int floor) {
        if (!name.equals("lit_shaft")) {
            return null;
        }
        Shaft pick = null;
        for (Shaft shaft : shafts(site)) {
            if (pick == null || (shaft.lit() && !pick.lit())) {
                pick = shaft;
            }
        }
        return pick == null ? null : new double[] {pick.centerX(), floor + 1, pick.centerZ(), 0.0, -90.0};
    }
}
