package com.rickr.backrooms.world.layout;

import static com.rickr.backrooms.world.layout.Level0SetPieces.*;

import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.List;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * The Atrium: a big, low, dark hall round the foot of a square shaft 22 blocks across that rises
 * to the roof of Level 0 (21 blocks of air over the carpet, seven rooms high). Look up from the
 * middle and the shaft's papered walls run up into the dark to a ring of small square downlights
 * round its top edge, far up and dim; the floor under it gets almost nothing. Round the foot of
 * the shaft squat pillars carry its walls, and between them lit bays open into the low hall,
 * from which corridors lead away into the maze in every direction.
 *
 * <p>Plan (local frame, see {@link Level0SetPieces}; m = rings from the middle, 0..27):
 * <pre>
 *   m 17-27  solid wall, cut by twelve corridors out to the maze: two 2-wide ones straight out
 *            from two of the four bays of each side (a short strip light halfway along, m 21-23),
 *            and a diagonal one from each corner (a downlight halfway, m 22)
 *   m 13-16  the low hall round the shaft (Level 0's 3-high ceiling), shallow, so each bay
 *            between two pillars is a lit recess: a pair of downlights a block behind the rim
 *            lights the wall at its back. The hall's corners stay dark
 *   m 11-12  the rim the shaft stands on: 2x2 pillars, five per side counting the corners, with
 *            4-wide bays open between them; from the ceiling up the inner ring (m 11) is the
 *            shaft's wall, papered up to the roof
 *   m  0-10  under the shaft: open up to the roof (y62), whose downlights sit at m 9
 * </pre>
 * Along a side (t = 0..25 across the rim): pillars at t 0-1, 6-7, 12-13, 18-19 and 24-25, bays at
 * t 2-5, 8-11, 14-17 and 20-23.
 *
 * <p>Pure function of (site, x, z): {@link #column} packs a column into an int and {@link #piece}
 * reads one block of it, so filling a column allocates nothing.
 */
public final class Level0Atrium {
    public static final int BOX = 56;
    public static final int MOAT = 2;
    /** Half the box: local u/v of the middle line (columns 27 and 28 straddle it). */
    private static final int HALF = BOX / 2;

    // Rings (m = the larger of the two distances from the middle, 0 for the two middle columns).
    private static final int SHAFT_MAX = 10;
    private static final int RIM_INNER = 11;
    private static final int RIM_OUTER = 12;
    private static final int HALL_MAX = 16;
    /** The ring of the roof with the downlights: a block in from the shaft's wall. */
    private static final int ROOF_RING = SHAFT_MAX - 1;
    /** The ring of the hall's ceiling with the bays' strip lights: a block behind the rim. */
    private static final int BAY_RING = RIM_OUTER + 2;
    /** The ring halfway along the corridors, with their one light each. */
    private static final int CORRIDOR_LIGHT = 22;
    /** t (position along a side) = the signed along-the-side offset + this; 0 at the rim's outer corner. */
    private static final int T0 = RIM_OUTER + 1;
    private static final int PILLAR_PITCH = 6;
    private static final int BAYS = 4;
    /** Which two of a side's four bays have a corridor: one pattern per side, picked per site. */
    private static final int[] CORRIDOR_PATTERNS = {0b0101, 0b1010, 0b1001, 0b0110};

    // Roles (bits 0-3 of a column word).
    private static final int R_MOAT = 0;
    /** The hall, its bays and the corridors. */
    private static final int R_FLOOR = 1;
    /** Under the shaft: open up to the roof. */
    private static final int R_SHAFT = 2;
    /** The rim between two pillars (open at floor level). */
    private static final int R_RIM = 3;
    private static final int R_PILLAR = 4;
    private static final int R_WALL = 5;

    // The fixture in this column's ceiling (or, under the shaft, in the roof): bits 4-5.
    private static final int LIGHT_SHIFT = 4;
    private static final int L_DOWN = 1;
    private static final int L_STRIP = 2;
    private static final int L_STRIP_DEAD = 3;
    /** A strip runs along local v (else along u). */
    private static final int ALONG_V_BIT = 1 << 6;
    /** The inner ring of the rim: the shaft's wall, from the ceiling up to the roof. */
    private static final int SHAFT_WALL_BIT = 1 << 7;

    private Level0Atrium() {}

    // ==========================================================================================
    // Plan
    // ==========================================================================================

    /** Distance from the middle line: 0 for both middle columns, 27 at the outer edge. */
    private static int ring(int d) {
        return d >= 0 ? d : -d - 1;
    }

    private static boolean isPillar(int t) {
        return t >= 0 && t < BAYS * PILLAR_PITCH + 2 && t % PILLAR_PITCH <= 1;
    }

    /** The bay (0..3) that position {@code t} along a side is in, or -1 (a pillar, or past the rim). */
    private static int bayOf(int t) {
        return t >= 2 && t < BAYS * PILLAR_PITCH && t % PILLAR_PITCH >= 2 ? t / PILLAR_PITCH : -1;
    }

    /** True for the two middle columns of a bay (where its lights and its corridor are). */
    private static boolean bayMiddle(int t) {
        int k = t % PILLAR_PITCH;
        return bayOf(t) >= 0 && (k == 3 || k == 4);
    }

    /** Which bays of local side {@code side} (N, E, S, W) have a corridor out: one bit per bay. */
    private static int corridorBays(AnomalySite site, int side) {
        return CORRIDOR_PATTERNS[Hash.range(Hash.mix(site.salt() + 31L * side), CORRIDOR_PATTERNS.length)];
    }

    /** The packed description of world column (x, z) of an atrium site (role | bits). */
    static int column(AnomalySite site, int x, int z) {
        int u = Level0SetPieces.u(site, MOAT, x, z);
        int v = Level0SetPieces.v(site, MOAT, x, z);
        if (u < 0 || u >= BOX || v < 0 || v >= BOX) {
            return R_MOAT;
        }
        int dx = u - HALF;
        int dz = v - HALF;
        int ax = ring(dx);
        int az = ring(dz);
        int m = Math.max(ax, az);
        // Which side of the hall this column is on, and where along that side.
        int side = ax >= az ? (dx < 0 ? W : E) : (dz < 0 ? N : S);
        int along = ax >= az ? dz : dx;
        int t = along + T0;
        if (m <= SHAFT_MAX) {
            // The roof's ring of downlights, every other block.
            boolean light = m == ROOF_RING && (ring(along) & 1) == 1 && Hash.unit(site.salt(), x, z, 91) < 0.9;
            return R_SHAFT | (light ? L_DOWN << LIGHT_SHIFT : 0);
        }
        if (m <= RIM_OUTER) {
            return (isPillar(t) ? R_PILLAR : R_RIM) | (m == RIM_INNER ? SHAFT_WALL_BIT : 0);
        }
        boolean sideways = side == W || side == E;
        if (m <= HALL_MAX) {
            // A pair of downlights in the middle of each bay.
            if (m == BAY_RING && bayMiddle(t) && Hash.unit(site.salt(), x, z, 92) < 0.85) {
                return R_FLOOR | L_DOWN << LIGHT_SHIFT;
            }
            return R_FLOOR;
        }
        // The outer wall and the corridors through it: a diagonal from each corner of the hall ...
        if (Math.min(ax, az) >= HALL_MAX - 1 && Math.abs(ax - az) <= 1) {
            boolean light = ax == CORRIDOR_LIGHT && az == CORRIDOR_LIGHT && Hash.unit(site.salt(), x, z, 93) < 0.8;
            return R_FLOOR | (light ? L_DOWN << LIGHT_SHIFT : 0);
        }
        // ... and straight ones out of two bays per side, a short strip down the middle of each.
        int bay = bayOf(t);
        if (bayMiddle(t) && (corridorBays(site, side) >> bay & 1) != 0) {
            if (Math.abs(m - CORRIDOR_LIGHT) <= 1 && t % PILLAR_PITCH == 3) {
                return R_FLOOR | strip(site, side, bay) | (sideways ? 0 : ALONG_V_BIT);
            }
            return R_FLOOR;
        }
        return R_WALL;
    }

    /** Light bits of the strip in the corridor out of bay {@code bay} of side {@code side}: lit or (one in five) dead. */
    private static int strip(AnomalySite site, int side, int bay) {
        return (Hash.unit(site.salt(), side, bay, 94) < 0.8 ? L_STRIP : L_STRIP_DEAD) << LIGHT_SHIFT;
    }

    /** The world direction whose axis a strip in this column runs along. */
    static Direction stripFacing(AnomalySite site, int word) {
        return Level0SetPieces.toWorld(site, (word & ALONG_V_BIT) != 0 ? Direction.SOUTH : Direction.EAST);
    }

    /** The piece code at height y of a column (floor = carpet y, ceiling = Level 0's 3-high ceiling y). */
    static int piece(int word, int y, int floor, int ceiling) {
        int role = word & 15;
        if (y == floor) {
            return CARPET;
        }
        if (y < ceiling) {
            if (role == R_WALL || role == R_PILLAR) {
                return y == floor + 1 ? BASEBOARD : WALLPAPER;
            }
            return AIR;
        }
        if ((word & SHAFT_WALL_BIT) != 0) {
            return y <= ROOF_Y ? WALLPAPER : AIR;
        }
        int lid = role == R_SHAFT ? ROOF_Y : ceiling;
        if (y == lid) {
            return switch (word >>> LIGHT_SHIFT & 3) {
                case L_DOWN -> DOWNLIGHT;
                case L_STRIP -> STRIP;
                case L_STRIP_DEAD -> STRIP_DEAD;
                default -> TILE;
            };
        }
        return AIR;
    }

    /** True if column (x, z) is a wall at eye level. */
    static boolean isSolid(AnomalySite site, int x, int z) {
        int role = column(site, x, z) & 15;
        return role == R_WALL || role == R_PILLAR;
    }

    /** True if you can stand on the floor here. */
    static boolean isWalkable(AnomalySite site, int x, int z) {
        return !isSolid(site, x, z);
    }

    // ==========================================================================================
    // For the client effects and testing
    // ==========================================================================================

    /** True if (x, z) is under the shaft (where you can look straight up into the dark). */
    public static boolean underShaft(AnomalySite site, int x, int z) {
        int u = Level0SetPieces.u(site, MOAT, x, z);
        int v = Level0SetPieces.v(site, MOAT, x, z);
        return u >= 0 && v >= 0 && u < BOX && v < BOX && Math.max(ring(u - HALF), ring(v - HALF)) <= SHAFT_MAX;
    }

    /** Named spots for /backrooms anomaly visit (testing). */
    static final List<String> VISIT_POINTS = List.of("centre", "edge", "corridor");

    /**
     * {x, feet y, z, yaw, pitch} of a named spot: "centre" = under the middle of the shaft, looking
     * up it; "edge" = in the mouth of a corridor, looking a little up, across the hall and the
     * floor under the shaft to the pillars, the bays and the shaft's wall on the far side; "corridor" =
     * in a bay, looking out down that corridor. Null for an unknown name.
     */
    @Nullable
    static double[] visit(AnomalySite site, String name, int floor) {
        // The first bay with a corridor on the local south side: u of its corridor's first column.
        int bay = Integer.numberOfTrailingZeros(corridorBays(site, S));
        int u = HALF - T0 + bay * PILLAR_PITCH + 3;
        return switch (name) {
            case "centre" -> Level0SetPieces.spot(site, MOAT, HALF, HALF, floor + 1, Direction.NORTH, -80.0f);
            case "edge" -> Level0SetPieces.spot(site, MOAT, u, HALF + HALL_MAX + 1, floor + 1, Direction.NORTH, -8.0f);
            case "corridor" -> Level0SetPieces.spot(site, MOAT, u, HALF + BAY_RING, floor + 1, Direction.SOUTH, 0.0f);
            default -> null;
        };
    }
}
