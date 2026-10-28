package com.rickr.backrooms.world.layout;

import static com.rickr.backrooms.world.layout.Level0SetPieces.*;

import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The Atrium: a two-storey void in Level 0. The 3-high ceiling of a big hall opens into a black
 * 14x14 shaft that rises to the top of the world, with nine warm pendant lights hanging in it on
 * long chains, and an upper balcony runs all the way round the void. Unlike the atrium people
 * film in other Backrooms games, you CAN get up there.
 *
 * <p>Plan (local frame, see {@link Level0SetPieces}; m = rings from the middle, 0..21):
 * <pre>
 *   m 20-21  outer wall, 2 thick, with 3-wide doorways in some alcoves (5-6 ways in)
 *   m 16-19  2-thick fins every 7 blocks along the wall make alcoves; each has a ceiling light
 *   m  8-15  open floor; four 2x2 posts stand at the corners of the void
 *   m  0- 6  the floor under the void: look up and the ceiling is gone
 * </pre>
 * Upstairs (balcony floor y52, ceiling y56; the void goes on up to the roof at y62):
 * <pre>
 *   m 7      the lip of the void: a chest-high parapet; above the balcony the void is lined with tile
 *   m 8-13   the balcony, lit mostly by the pendants hanging just beyond the parapet
 *   m 14     the balcony's outer wall, with one narrow doorway into the service corridor
 * </pre>
 *
 * <p><b>The way up.</b> One alcove on the west side (local) has no light, only an emergency lamp
 * on standby, and a steel ladder going up a 1x1 hole in the outer wall. It climbs 13 blocks in the
 * dark to a narrow service corridor above the ceiling, which leads to the balcony.
 *
 * <p><b>On the balcony</b>: a supply crate, a row of scuffed carpet arrows pointing the true way
 * to the nearest exit (someone climbed up here to look), an office chair at the parapet facing
 * the void, and a mannequin standing at the edge, looking down. From the floor below it is a
 * figure watching you. The server (AtriumBehavior) turns it to face whoever is nearest, but only
 * while nobody is looking at it.
 *
 * <p>Pure function of (site, x, z): {@link #column} packs everything a column needs into one int,
 * {@link #piece} reads one block of it, so filling a column allocates nothing.
 */
public final class Level0Atrium {
    public static final int BOX = 44;
    public static final int MOAT = 2;
    /** Half the box: local u/v of the middle line (columns 21 and 22 straddle it). */
    private static final int HALF = BOX / 2;

    // Rings (m = the larger of the two distances from the middle, 0 for the two middle columns).
    private static final int VOID_MAX = 6;
    private static final int LIP = 7;
    private static final int BAND_MAX = 13;
    private static final int RING_WALL = 14;
    private static final int FIN_FROM = 16;
    private static final int FIN_TO = 19;
    private static final int WALL_FROM = 20;
    private static final int BACK_WALL = 21;

    /** Balcony carpet and its ceiling (you stand at y53). */
    public static final int BALCONY_FLOOR = 52;
    public static final int BALCONY_CEILING = 56;

    /** Centres of the four alcoves along each side (signed, from the middle line). */
    private static final int[] ALCOVES = {-10, -3, 4, 11};

    // Lower-storey roles (bits 0-3 of a column word).
    private static final int R_MOAT = 0;
    private static final int R_FLOOR = 1;
    private static final int R_WALL = 2;
    private static final int R_DOORWAY = 3;
    private static final int R_FIN = 4;
    private static final int R_POST = 5;
    private static final int R_LADDER = 6;

    // Upper roles (bits 4-7).
    private static final int U_NONE = 0;
    private static final int U_VOID = 1;
    private static final int U_LIP = 2;
    private static final int U_BAND = 3;
    private static final int U_RING_WALL = 4;
    private static final int U_RING_DOOR = 5;
    private static final int U_CORRIDOR = 6;
    private static final int U_CORRIDOR_WALL = 7;
    private static final int U_SHAFT_BACK = 8;

    // Fixture states (2 bits each).
    private static final int FIX_NONE = 0;
    private static final int FIX_LIT = 1;
    private static final int FIX_DEAD = 2;

    // Bit layout of a column word.
    private static final int UPPER_SHIFT = 4;
    private static final int LOW_FIX_SHIFT = 8;
    private static final int HIGH_FIX_SHIFT = 10;
    private static final int STANDBY_BIT = 1 << 12;
    private static final int WET_BIT = 1 << 13;
    /** The ladder shaft's inner wall above the ceiling (y45-51 solid). */
    private static final int BOX_BIT = 1 << 14;
    private static final int THING_SHIFT = 15; // 6 bits: piece code standing on the balcony
    private static final int PENDANT_SHIFT = 21; // 5 bits: pendant height above the floor, 0 = none
    private static final int FACING_SHIFT = 26; // 2 bits: local facing

    private Level0Atrium() {}

    // ==========================================================================================
    // Plan
    // ==========================================================================================

    /** Signed offset of local u (or v) from the middle line: -22..21. */
    private static int signed(int u) {
        return u - HALF;
    }

    /** Distance from the middle line: 0 for both middle columns, 21 at the outer edge. */
    private static int ring(int d) {
        return d >= 0 ? d : -d - 1;
    }

    /** Which alcoves of local side {@code side} (N, E, S, W) are doorways: one bit per alcove. */
    private static int doorways(AnomalySite site, int side) {
        int first = Hash.range(Hash.mix(site.salt() + 31L * side), ALCOVES.length);
        // North and south get two ways in, east and west one (the west wall also holds the ladder).
        if (side == N || side == S) {
            return 1 << first | 1 << (first + 2) % ALCOVES.length;
        }
        return 1 << first;
    }

    /** The alcove on the west side (local) with the ladder: across from that side's doorway. */
    private static int ladderAlcove(AnomalySite site) {
        return (Integer.numberOfTrailingZeros(doorways(site, W)) + 2) % ALCOVES.length;
    }

    /** Signed along-the-wall offset of the ladder (the middle of its alcove). */
    private static int ladderAlong(AnomalySite site) {
        return ALCOVES[ladderAlcove(site)];
    }

    /** Index of the alcove whose middle is {@code along}, or -1. */
    private static int alcoveAt(int along) {
        for (int i = 0; i < ALCOVES.length; i++) {
            if (ALCOVES[i] == along) {
                return i;
            }
        }
        return -1;
    }

    /** Index of the alcove that {@code along} lies in (within 2 of its middle), or -1. */
    private static int alcoveAround(int along) {
        for (int i = 0; i < ALCOVES.length; i++) {
            if (Math.abs(along - ALCOVES[i]) <= 2) {
                return i;
            }
        }
        return -1;
    }

    /** Fins sit right after each alcove (2 thick) and one before the first. */
    private static boolean finAt(int along) {
        return along == -14 || along == -13 || along == -7 || along == -6 || along == 0 || along == 1
                || along == 7 || along == 8 || along == 14 || along == 15;
    }

    /** The packed description of world column (x, z) of an atrium site (see the bit layout above). */
    static int column(AnomalySite site, int x, int z) {
        int u = Level0SetPieces.u(site, MOAT, x, z);
        int v = Level0SetPieces.v(site, MOAT, x, z);
        if (u < 0 || u >= BOX || v < 0 || v >= BOX) {
            return R_MOAT;
        }
        int dx = signed(u);
        int dz = signed(v);
        int ax = ring(dx);
        int az = ring(dz);
        int m = Math.max(ax, az);
        // Which side of the hall this column is on, and where along that side.
        int side = ax >= az ? (dx < 0 ? W : E) : (dz < 0 ? N : S);
        int along = ax >= az ? dz : dx;
        int lc = ladderAlong(site);
        boolean westArm = side == W && ax >= az;

        // ---- The lower storey ----
        int role;
        int word = 0;
        if (m >= WALL_FROM) {
            int alcove = alcoveAround(along);
            boolean corner = Math.min(ax, az) > FIN_TO;
            if (!corner && alcove >= 0 && Math.abs(along - ALCOVES[alcove]) <= 1 && (doorways(site, side) >> alcove & 1) != 0) {
                role = R_DOORWAY;
            } else if (westArm && m == WALL_FROM && along == lc) {
                role = R_LADDER;
                word |= E << FACING_SHIFT; // fixed to the wall behind it, facing into the alcove
            } else {
                role = R_WALL;
            }
        } else if (m >= FIN_FROM && Math.min(ax, az) < FIN_FROM && finAt(along)) {
            role = R_FIN;
        } else if (ax >= LIP && ax <= LIP + 1 && az >= LIP && az <= LIP + 1) {
            role = R_POST;
        } else {
            role = R_FLOOR;
        }
        word |= role;

        // Ceiling lights downstairs: one in the middle of each alcove (a doorway alcove's is nearly
        // always on, so lit doorways draw people in), and a sparse ring round the void.
        if (role == R_FLOOR && m == FIN_FROM + 2 && Math.min(ax, az) < FIN_FROM) {
            int alcove = alcoveAt(along);
            if (alcove >= 0) {
                boolean door = (doorways(site, side) >> alcove & 1) != 0;
                boolean ladder = westArm && along == lc;
                double alive = ladder ? 0.0 : door ? 0.85 : 0.6;
                word |= (Hash.unit(site.salt(), x, z, 71) < alive ? FIX_LIT : FIX_DEAD) << LOW_FIX_SHIFT;
            }
        }
        if (role == R_FLOOR && m == LIP + 2 && (Math.min(ax, az) == 2 || Math.min(ax, az) == LIP + 2)) {
            word |= (Hash.unit(site.salt(), x, z, 72) < 0.55 ? FIX_LIT : FIX_DEAD) << LOW_FIX_SHIFT;
        }
        // The ladder alcove's emergency lamp, on the outer wall beside the ladder: dark until the
        // power fails, then it comes on by itself and shows the ladder.
        if (westArm && ax == FIN_TO && along == lc + 1) {
            word |= STANDBY_BIT | E << FACING_SHIFT;
        }
        // Damp carpet under the void: whatever is up there drips.
        if (m <= 3 && Hash.unit(site.salt(), x, z, 73) < 0.22) {
            word |= WET_BIT;
        }

        // ---- The upper storey ----
        int upper;
        if (m <= VOID_MAX) {
            upper = U_VOID;
            word |= pendantHeight(dx, dz) << PENDANT_SHIFT;
        } else if (m == LIP) {
            upper = U_LIP;
            // The parapet's rail runs along the edge of the void.
            word |= (side == W ? E : side == E ? W : side == N ? S : N) << FACING_SHIFT;
        } else if (m <= BAND_MAX) {
            upper = U_BAND;
            if (ax == BAND_MAX - 2 && az == BAND_MAX - 2) {
                word |= (Hash.unit(site.salt(), x, z, 74) < 0.5 ? FIX_LIT : FIX_DEAD) << HIGH_FIX_SHIFT;
            }
            word |= balconyThing(side, ax, along);
        } else if (m == RING_WALL) {
            upper = westArm && along == lc ? U_RING_DOOR : U_RING_WALL;
        } else if (westArm && along == lc && ax < WALL_FROM) {
            upper = U_CORRIDOR;
            if (ax == FIN_TO) {
                word |= BOX_BIT; // the ladder hole's inner side
            }
        } else if (westArm && Math.abs(along - lc) == 1 && ax <= WALL_FROM) {
            upper = U_CORRIDOR_WALL;
            if (ax >= FIN_TO) {
                word |= BOX_BIT;
            }
        } else if (westArm && along == lc && ax == BACK_WALL) {
            upper = U_SHAFT_BACK;
        } else {
            upper = U_NONE;
        }
        return word | upper << UPPER_SHIFT;
    }

    /** Height above the floor of the pendant hanging in void column (dx, dz), or 0 for none. */
    private static int pendantHeight(int dx, int dz) {
        boolean cornerX = dx == -5 || dx == 4;
        boolean cornerZ = dz == -5 || dz == 4;
        if (cornerX && cornerZ) {
            return 14; // y54: at eye level for someone on the balcony
        }
        if ((dx == 5 && dz == 0) || (dx == -6 && dz == -1) || (dx == -1 && dz == 5) || (dx == 0 && dz == -6)) {
            return 11; // y51
        }
        if (dx == 0 && dz == 0) {
            return 7; // y47: the one that hangs lowest, a lone bulb seen from far below
        }
        return 0;
    }

    /** What stands on the balcony's east side (local): the reward and the watcher. */
    private static int balconyThing(int side, int ax, int along) {
        if (side != E) {
            return 0;
        }
        if (ax == LIP + 1 && along == -2) {
            return MANNEQUIN_LOWER << THING_SHIFT | W << FACING_SHIFT;
        }
        if (ax == LIP + 1 && along == 1) {
            return CHAIR << THING_SHIFT | W << FACING_SHIFT;
        }
        if (ax == BAND_MAX - 1 && along == -4) {
            return CRATE << THING_SHIFT;
        }
        if (ax == LIP + 3 && along >= -1 && along <= 1) {
            return ARROW << THING_SHIFT; // which way it points is the layout's job (the nearest exit)
        }
        return 0;
    }

    // ==========================================================================================
    // Blocks
    // ==========================================================================================

    /** True for the 3 arrow columns (their facing must come from the nearest exit, see {@link Level0AnomalyPlan#fill}). */
    static boolean isArrow(int word) {
        return (word >>> THING_SHIFT & 63) == ARROW;
    }

    /** The local facing packed into a column word. */
    static int facingBits(int word) {
        return word >>> FACING_SHIFT & 3;
    }

    /** The piece code at height y of a column (floor = carpet y, ceiling = Level 0's 3-high ceiling y). */
    static int piece(int word, int y, int floor, int ceiling) {
        int role = word & 15;
        int upper = word >>> UPPER_SHIFT & 15;
        if (y == floor) {
            return CARPET;
        }
        if (y < ceiling) {
            return switch (role) {
                case R_WALL, R_FIN, R_POST -> y == floor + 1 ? BASEBOARD : WALLPAPER;
                case R_LADDER -> LADDER;
                default -> {
                    if (y == floor + 1 && (word & WET_BIT) != 0) {
                        yield WET_CARPET;
                    }
                    if (y == ceiling - 1 && (word & STANDBY_BIT) != 0) {
                        yield LAMP_STANDBY;
                    }
                    yield AIR;
                }
            };
        }
        if (y == ceiling) {
            if (role == R_LADDER) {
                return LADDER;
            }
            if (upper == U_VOID) {
                return hanging(word, y, floor);
            }
            return fixture(word >>> LOW_FIX_SHIFT & 3);
        }
        // Above Level 0's ordinary ceiling.
        if (role == R_LADDER) {
            if (y <= BALCONY_FLOOR + 1) {
                return LADDER; // up to one block above the corridor floor, so you can step off
            }
            return y < BALCONY_CEILING ? AIR : y == BALCONY_CEILING ? TILE : AIR;
        }
        return switch (upper) {
            case U_VOID -> y == ROOF_Y ? TILE : hanging(word, y, floor);
            case U_LIP -> {
                if (y < BALCONY_FLOOR || (y >= BALCONY_CEILING && y <= ROOF_Y)) {
                    yield TILE; // the void's lining, below and above the balcony
                }
                yield y == BALCONY_FLOOR ? CARPET : y == BALCONY_FLOOR + 1 ? PARAPET : AIR;
            }
            case U_BAND -> {
                if (y == BALCONY_FLOOR) {
                    yield (word >>> THING_SHIFT & 63) == ARROW ? ARROW : CARPET;
                }
                if (y == BALCONY_CEILING) {
                    yield fixture(word >>> HIGH_FIX_SHIFT & 3);
                }
                int thing = word >>> THING_SHIFT & 63;
                if (y == BALCONY_FLOOR + 1 && thing != ARROW) {
                    yield thing;
                }
                yield y == BALCONY_FLOOR + 2 && thing == MANNEQUIN_LOWER ? MANNEQUIN_UPPER : AIR;
            }
            case U_RING_WALL, U_CORRIDOR_WALL -> {
                if (y < BALCONY_FLOOR) {
                    yield (word & BOX_BIT) != 0 ? WALLPAPER : AIR;
                }
                yield y == BALCONY_FLOOR ? CARPET : y == BALCONY_FLOOR + 1 ? BASEBOARD : y < BALCONY_CEILING ? WALLPAPER
                        : y == BALCONY_CEILING ? TILE : AIR;
            }
            case U_RING_DOOR, U_CORRIDOR -> {
                if (y < BALCONY_FLOOR) {
                    yield (word & BOX_BIT) != 0 ? WALLPAPER : AIR;
                }
                yield y == BALCONY_FLOOR ? CARPET : y == BALCONY_CEILING ? TILE : AIR;
            }
            case U_SHAFT_BACK -> y < BALCONY_CEILING ? WALLPAPER : y == BALCONY_CEILING ? TILE : AIR;
            default -> AIR;
        };
    }

    /** A pendant on its chain, or empty void. */
    private static int hanging(int word, int y, int floor) {
        int height = word >>> PENDANT_SHIFT & 31;
        if (height == 0 || y < floor + height || y >= ROOF_Y) {
            return AIR;
        }
        return y == floor + height ? PENDANT : CHAIN;
    }

    private static int fixture(int state) {
        return state == FIX_LIT ? TUBE : state == FIX_DEAD ? TUBE_DEAD : TILE;
    }

    /** True if column (x, z) is a wall at eye level downstairs. */
    static boolean isSolid(AnomalySite site, int x, int z) {
        int role = column(site, x, z) & 15;
        return role == R_WALL || role == R_FIN || role == R_POST;
    }

    /** True if you can stand on the ground floor here (the ladder hole doesn't count). */
    static boolean isWalkable(AnomalySite site, int x, int z) {
        int role = column(site, x, z) & 15;
        return role == R_FLOOR || role == R_DOORWAY || role == R_MOAT;
    }

    // ==========================================================================================
    // For the server and client effects
    // ==========================================================================================

    /**
     * Every lit tube in the ground floor's ceiling (not the pendants, not the balcony's tubes), plus
     * the ladder alcove's dead one: the lights that die when someone reaches the balcony. Listing the
     * ladder alcove's fixture too is what wakes its standby emergency lamp (it switches on when the
     * power fails within a few blocks of it), which then shows the way back down.
     * Allocates: server only, once per event.
     */
    public static java.util.List<BlockPos> lowerLights(AnomalySite site, int floor, int ceiling) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (int x = site.minX(); x <= site.maxX(); x++) {
            for (int z = site.minZ(); z <= site.maxZ(); z++) {
                if (piece(column(site, x, z), ceiling, floor, ceiling) == TUBE) {
                    out.add(new BlockPos(x, ceiling, z));
                }
            }
        }
        int u = HALF - 1 - (FIN_FROM + 2); // the ladder alcove's ceiling fixture (west, m = 18)
        int v = HALF + ladderAlong(site);
        out.add(new BlockPos(Level0SetPieces.worldX(site, MOAT, u, v), ceiling, Level0SetPieces.worldZ(site, MOAT, u, v)));
        return out;
    }

    /** True if the player at (x, y, z) is up on the balcony (or in the corridor up there). */
    public static boolean upstairs(AnomalySite site, double x, double y, double z) {
        return y >= BALCONY_FLOOR + 0.5 && site.contains(x, z);
    }

    /** True if (x, z) is above the void (where you can look straight up into the dark). */
    public static boolean underVoid(AnomalySite site, int x, int z) {
        int u = Level0SetPieces.u(site, MOAT, x, z);
        int v = Level0SetPieces.v(site, MOAT, x, z);
        return Math.max(ring(signed(u)), ring(signed(v))) <= VOID_MAX && u >= 0 && v >= 0 && u < BOX && v < BOX;
    }

    /** The mannequin's lower block. */
    public static BlockPos mannequin(AnomalySite site) {
        // Local east side: dx = LIP + 1 (u = HALF + LIP + 1), along -2 (v = HALF - 2).
        int u = HALF + LIP + 1;
        int v = HALF - 2;
        return new BlockPos(Level0SetPieces.worldX(site, MOAT, u, v), BALCONY_FLOOR + 1, Level0SetPieces.worldZ(site, MOAT, u, v));
    }

    /** The middle of the floor under the void (the centre of the lower outage). */
    public static BlockPos lowerCentre(AnomalySite site, int floor) {
        return BlockPos.containing(site.centerX(), floor + 1, site.centerZ());
    }

    /** Where the ladder goes up (the hole's column, at the ground floor). */
    public static BlockPos ladder(AnomalySite site, int floor) {
        int u = HALF - 1 - WALL_FROM; // dx = -21 (west, m = 20)
        int v = HALF + ladderAlong(site);
        return new BlockPos(Level0SetPieces.worldX(site, MOAT, u, v), floor + 1, Level0SetPieces.worldZ(site, MOAT, u, v));
    }

    /** The world direction a local one points in for this site (so the arrows can be turned back). */
    static Direction world(AnomalySite site, int localBits) {
        return Level0SetPieces.toWorld(site, Level0SetPieces.localDir(localBits));
    }

    /** Named spots for /backrooms anomaly visit (testing). */
    static final java.util.List<String> VISIT_POINTS = java.util.List.of("ladder", "balcony");

    /**
     * {x, feet y, z, yaw, pitch} of a named spot: "ladder" = at its foot in the alcove, facing it;
     * "balcony" = on the balcony by the arrows, facing the void. Null for an unknown name.
     */
    @org.jetbrains.annotations.Nullable
    static double[] visit(AnomalySite site, String name, int floor) {
        return switch (name) {
            case "ladder" -> Level0SetPieces.spot(site, MOAT, HALF - 1 - FIN_TO, HALF + ladderAlong(site), floor + 1, Direction.WEST, 0.0f);
            case "balcony" -> Level0SetPieces.spot(site, MOAT, HALF + LIP + 3, HALF, BALCONY_FLOOR + 1, Direction.WEST, 20.0f);
            default -> null;
        };
    }
}
