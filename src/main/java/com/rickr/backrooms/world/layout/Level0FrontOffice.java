package com.rickr.backrooms.world.layout;

import static com.rickr.backrooms.world.layout.Level0SetPieces.*;

import com.rickr.backrooms.block.RugBlock;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The Front Office: a huge dark room (40x40, its own ceiling, its troffers dead but two) with one
 * thing in the middle: a free-standing 5x3 block of yellow wallpaper with a drawn door and a
 * drawn window on it (our existing decal blocks), a white emergency lamp over each long face, a
 * red rotating beacon on its east end and a round rug in front of the door. The two tubes still
 * working hang 4 blocks out from the long faces and light the drawings, so the door reads from
 * across the room. An alarm sounds from the beacon, loud enough to hear 80 blocks away (client:
 * SetPieceEffects), and a row of empty office chairs faces the drawn door like a waiting room.
 *
 * <p><b>It matters.</b> The PA sometimes asks you to come to the front office (LevelEvents ->
 * FrontOfficeBehavior). Get there while you're expected, or simply arrive at the right time, and
 * the drawn door is real: the alarm cuts out, the beacon stops, a thin line of light shows under
 * the door, and walking into it takes you through. Behind it is a room that cannot fit in the
 * block: a small, warm, carpeted reception office with a desk, a field radio (save your
 * checkpoint), a water cooler, a supply locker and a crate, and two more drawn doors: one back
 * out, and one under an EXIT sign that really is the way out of here (it takes you to the lobby
 * of the nearest exit stairwell).
 *
 * <p>The hidden room is built for real, high above the office's ceiling (floor y50), sealed on
 * every side: the only way in or out is through the drawings.
 *
 * <p>Pure function of (site, x, z); {@link #column} packs a column into an int and {@link #piece}
 * reads one block of it.
 */
public final class Level0FrontOffice {
    public static final int BOX = 40;
    public static final int MOAT = 2;
    private static final int HALF = BOX / 2;

    /** The hidden room: carpet, three blocks of air, ceiling. */
    public static final int ROOM_FLOOR = 50;
    public static final int ROOM_CEILING = 54;
    // Its footprint (walls included), local.
    private static final int ROOM_U0 = 15;
    private static final int ROOM_U1 = 25;
    private static final int ROOM_V0 = 16;
    private static final int ROOM_V1 = 24;

    // Key places, local (u, v).
    private static final int DOOR_U = 19;
    private static final int BLOCK_FRONT_V = 19;
    private static final int RUG_U = 19;
    private static final int RUG_V = 17;
    private static final int ROOM_RUG_U = 20;
    private static final int ROOM_RUG_V = 22;
    private static final int ROOM_DOOR_U = 20;

    // Ground-floor roles (bits 0-3).
    private static final int R_MOAT = 0;
    private static final int R_FLOOR = 1;
    private static final int R_WALL = 2;
    private static final int R_DOORWAY = 3;
    private static final int R_BLOCK = 4;
    private static final int R_BLOCK_DOOR = 5;
    private static final int R_BLOCK_WINDOW = 6;

    // Hidden-room roles (bits 4-6).
    private static final int H_NONE = 0;
    private static final int H_FLOOR = 1;
    private static final int H_WALL = 2;
    private static final int H_DOOR = 3;

    private static final int HIDDEN_SHIFT = 4;
    private static final int FACING_SHIFT = 7;        // 2 bits, local
    private static final int RUG_SHIFT = 9;           // 4 bits: office rug part + 1 (0 = no rug)
    private static final int LOW_THING_SHIFT = 13;    // 6 bits: piece at y41 (chair) or y43 (lamp, beacon)
    private static final int ROOM_THING_SHIFT = 19;   // 6 bits: piece standing in the hidden room
    private static final int DEAD_TUBE_BIT = 1 << 25;
    private static final int SIGN_BIT = 1 << 26;      // EXIT sign over the room's exit door
    private static final int WARM_LAMP_BIT = 1 << 27;
    private static final int ROOM_RUG_SHIFT = 28;     // 4 bits: hidden room rug part + 1

    private Level0FrontOffice() {}

    /** The office's doorways: one 3-wide gap per side, somewhere along it. */
    private static boolean doorway(AnomalySite site, int side, int along) {
        int a = -12 + Hash.range(Hash.mix(site.salt() + 53L * side), 22); // -12..9
        return along >= a && along < a + 3;
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
        int word;
        if (Math.max(ax, az) >= HALF - 2) {
            int side = ax >= az ? (dx < 0 ? W : E) : (dz < 0 ? N : S);
            int along = ax >= az ? dz : dx;
            boolean corner = Math.min(ax, az) >= HALF - 4;
            word = !corner && doorway(site, side, along) ? R_DOORWAY : R_WALL;
        } else if (u >= 18 && u <= 22 && v >= BLOCK_FRONT_V && v <= 21) {
            if (u == DOOR_U && v == BLOCK_FRONT_V) {
                word = R_BLOCK_DOOR | N << FACING_SHIFT;
            } else if (u == 21 && v == BLOCK_FRONT_V) {
                word = R_BLOCK_WINDOW | N << FACING_SHIFT;
            } else if (u == HALF && v == 21) {
                word = R_BLOCK_WINDOW | S << FACING_SHIFT;
            } else {
                word = R_BLOCK;
            }
        } else {
            word = R_FLOOR | groundThing(u, v);
            if (Math.abs(u - RUG_U) <= 1 && Math.abs(v - RUG_V) <= 1) {
                word |= (rugPart(site, RUG_U, RUG_V, x, z) + 1) << RUG_SHIFT;
            }
            // The office's troffers are all there, all dead: the power is off in here, nothing is missing.
            if (u % 6 == 3 && v % 6 == 3) {
                word |= DEAD_TUBE_BIT;
            }
        }
        return word | hidden(site, u, v, x, z);
    }

    /** Lamps, the beacon and the waiting-room chairs on the ground floor. */
    private static int groundThing(int u, int v) {
        if (u == HALF && v == BLOCK_FRONT_V - 1) {
            return LAMP << LOW_THING_SHIFT | N << FACING_SHIFT;
        }
        // The only two troffers still working in the room, each 4 blocks out from a long face of the
        // block. They light the drawn door and windows head-on (the emergency lamps hang flat on the
        // block and only graze it), so from across the dark room you see "a door" lit up.
        if (u == HALF && (v == BLOCK_FRONT_V - 4 || v == 25)) {
            return TUBE << LOW_THING_SHIFT;
        }
        if (u == HALF && v == 22) {
            return LAMP << LOW_THING_SHIFT | S << FACING_SHIFT;
        }
        if (u == 23 && v == HALF) {
            return BEACON << LOW_THING_SHIFT | E << FACING_SHIFT;
        }
        if (v == 12 && u >= 16 && u <= 24 && u % 2 == 0) {
            return CHAIR << LOW_THING_SHIFT | S << FACING_SHIFT;
        }
        return 0;
    }

    /** The hidden room's part of a column (its walls, doors, furniture), or 0 outside it. */
    private static int hidden(AnomalySite site, int u, int v, int x, int z) {
        if (u < ROOM_U0 || u > ROOM_U1 || v < ROOM_V0 || v > ROOM_V1) {
            return 0;
        }
        if (u == ROOM_U0 || u == ROOM_U1 || v == ROOM_V0 || v == ROOM_V1) {
            if (u == ROOM_DOOR_U && v == ROOM_V1) {
                return H_DOOR << HIDDEN_SHIFT | N << FACING_SHIFT; // back to the office
            }
            if (u == ROOM_DOOR_U && v == ROOM_V0) {
                return H_DOOR << HIDDEN_SHIFT | S << FACING_SHIFT; // the way out
            }
            return H_WALL << HIDDEN_SHIFT;
        }
        int word = H_FLOOR << HIDDEN_SHIFT;
        if (Math.abs(u - ROOM_RUG_U) <= 1 && Math.abs(v - ROOM_RUG_V) <= 1) {
            word |= (rugPart(site, ROOM_RUG_U, ROOM_RUG_V, x, z) + 1) << ROOM_RUG_SHIFT;
        }
        if (u == ROOM_DOOR_U && v == ROOM_V0 + 1) {
            word |= SIGN_BIT | S << FACING_SHIFT;
        }
        if (u == HALF && v == HALF) {
            word |= WARM_LAMP_BIT;
        }
        int thing;
        int facing = 0;
        if (v == 20 && u == 19) {
            thing = DESK_PC;
            facing = N; // the receptionist sits on the north side, facing the door you came in by
        } else if (v == 20 && (u == 20 || u == 21)) {
            thing = DESK;
            facing = N;
        } else if (v == 19 && u == 20) {
            thing = CHAIR;
            facing = S;
        } else if (v == 17 && u == 23) {
            thing = RADIO;
            facing = S;
        } else if (v == 17 && u == 24) {
            thing = COOLER;
            facing = S;
        } else if (v == 21 && u == 24) {
            thing = CABINET;
            facing = W;
        } else if (v == 22 && u == 24) {
            thing = CRATE;
        } else if ((v == 18 || v == 19) && u == 16) {
            thing = FILING;
            facing = E;
        } else {
            return word;
        }
        return word | thing << ROOM_THING_SHIFT | facing << FACING_SHIFT;
    }

    /** The rug tile for world column (x, z) of a rug centred on local (cu, cv): rugs are laid in WORLD order. */
    private static int rugPart(AnomalySite site, int cu, int cv, int x, int z) {
        int ox = x - Level0SetPieces.worldX(site, MOAT, cu, cv);
        int oz = z - Level0SetPieces.worldZ(site, MOAT, cu, cv);
        return RugBlock.part(ox, oz);
    }

    static int facingBits(int word) {
        return word >>> FACING_SHIFT & 3;
    }

    /** The rug part (0..8) of a rug block at height y (the office floor or the hidden room's), for {@link Level0SetPieces#state}. */
    static int rugPart(int word, int y, int floor) {
        int bits = y == floor ? word >>> RUG_SHIFT & 15 : word >>> ROOM_RUG_SHIFT & 15;
        return Math.max(0, bits - 1);
    }

    static int piece(int word, int y, int floor, int ceiling) {
        int role = word & 15;
        int hidden = word >>> HIDDEN_SHIFT & 7;
        if (y > ceiling) {
            return hiddenPiece(word, hidden, y);
        }
        if (y == floor) {
            return (word >>> RUG_SHIFT & 15) != 0 ? RUG : CARPET;
        }
        if (y == ceiling) {
            if ((word >>> LOW_THING_SHIFT & 63) == TUBE) {
                return TUBE;
            }
            return (word & DEAD_TUBE_BIT) != 0 ? TUBE_DEAD : TILE;
        }
        int thing = word >>> LOW_THING_SHIFT & 63;
        return switch (role) {
            case R_WALL, R_BLOCK -> y == floor + 1 ? BASEBOARD : WALLPAPER;
            case R_BLOCK_DOOR -> y == floor + 1 ? DOOR_LOWER : y == floor + 2 ? DOOR_UPPER : WALLPAPER;
            case R_BLOCK_WINDOW -> y == floor + 1 ? BASEBOARD : y == floor + 2 ? WINDOW : WALLPAPER;
            case R_FLOOR -> {
                if (thing == CHAIR) {
                    yield y == floor + 1 ? CHAIR : AIR;
                }
                if (thing == LAMP || thing == BEACON) {
                    yield y == ceiling - 1 ? thing : AIR;
                }
                yield AIR;
            }
            default -> AIR;
        };
    }

    private static int hiddenPiece(int word, int hidden, int y) {
        if (hidden == H_NONE || y > ROOM_CEILING) {
            return AIR;
        }
        if (y == ROOM_FLOOR) {
            return (word >>> ROOM_RUG_SHIFT & 15) != 0 ? RUG : CARPET;
        }
        if (y == ROOM_CEILING) {
            return (word & WARM_LAMP_BIT) != 0 ? WARM_LAMP : TILE;
        }
        if (y < ROOM_FLOOR) {
            return AIR;
        }
        return switch (hidden) {
            case H_WALL -> y == ROOM_FLOOR + 1 ? BASEBOARD : WALLPAPER;
            case H_DOOR -> y == ROOM_FLOOR + 1 ? DOOR_LOWER : y == ROOM_FLOOR + 2 ? DOOR_UPPER : WALLPAPER;
            default -> {
                if (y == ROOM_FLOOR + 3 && (word & SIGN_BIT) != 0) {
                    yield EXIT_SIGN;
                }
                int thing = word >>> ROOM_THING_SHIFT & 63;
                yield y == ROOM_FLOOR + 1 && thing != 0 ? thing : AIR;
            }
        };
    }

    static boolean isSolid(AnomalySite site, int x, int z) {
        int role = column(site, x, z) & 15;
        return role == R_WALL || role == R_BLOCK || role == R_BLOCK_DOOR || role == R_BLOCK_WINDOW;
    }

    static boolean isWalkable(AnomalySite site, int x, int z) {
        int word = column(site, x, z);
        int role = word & 15;
        return (role == R_FLOOR || role == R_DOORWAY || role == R_MOAT) && (word >>> LOW_THING_SHIFT & 63) != CHAIR;
    }

    // ==========================================================================================
    // Key places, in world coordinates (for the server behaviour and the client effect)
    // ==========================================================================================

    private static BlockPos at(AnomalySite site, int u, int v, int y) {
        return new BlockPos(Level0SetPieces.worldX(site, MOAT, u, v), y, Level0SetPieces.worldZ(site, MOAT, u, v));
    }

    /** The drawn door on the block (its lower half). */
    public static BlockPos door(AnomalySite site, int floor) {
        return at(site, DOOR_U, BLOCK_FRONT_V, floor + 1);
    }

    /** The air block in front of the drawn door (where the line of light goes, and where you stand). */
    public static BlockPos doorFront(AnomalySite site, int floor) {
        return at(site, DOOR_U, BLOCK_FRONT_V - 1, floor + 1);
    }

    /** The way the drawn door faces (out of the block, towards you). */
    public static Direction doorFacing(AnomalySite site) {
        return Level0SetPieces.toWorld(site, Direction.NORTH);
    }

    /** The red beacon. */
    public static BlockPos beacon(AnomalySite site, int ceiling) {
        return at(site, 23, HALF, ceiling - 1);
    }

    /** The hidden room's door back to the office (lower half) and the way it faces (into the room). */
    public static BlockPos roomBackDoor(AnomalySite site) {
        return at(site, ROOM_DOOR_U, ROOM_V1, ROOM_FLOOR + 1);
    }

    public static Direction roomBackDoorFacing(AnomalySite site) {
        return Level0SetPieces.toWorld(site, Direction.NORTH);
    }

    /** The hidden room's door under the EXIT sign (lower half) and the way it faces. */
    public static BlockPos roomExitDoor(AnomalySite site) {
        return at(site, ROOM_DOOR_U, ROOM_V0, ROOM_FLOOR + 1);
    }

    public static Direction roomExitDoorFacing(AnomalySite site) {
        return Level0SetPieces.toWorld(site, Direction.SOUTH);
    }

    /** The middle of the hidden room, at head height (for its sounds). */
    public static BlockPos roomCentre(AnomalySite site) {
        return at(site, HALF, HALF, ROOM_FLOOR + 2);
    }

    /** True if (x, y, z) is inside the hidden room. */
    public static boolean inRoom(AnomalySite site, double x, double y, double z) {
        if (y < ROOM_FLOOR || y > ROOM_CEILING || !site.contains(x, z)) {
            return false;
        }
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int u = Level0SetPieces.u(site, MOAT, bx, bz);
        int v = Level0SetPieces.v(site, MOAT, bx, bz);
        return u > ROOM_U0 && u < ROOM_U1 && v > ROOM_V0 && v < ROOM_V1;
    }

    /** True if (x, z) is inside the office's outer walls (not the moat, not the walls). */
    public static boolean inOffice(AnomalySite site, int x, int z) {
        int u = Level0SetPieces.u(site, MOAT, x, z);
        int v = Level0SetPieces.v(site, MOAT, x, z);
        return u >= 2 && v >= 2 && u < BOX - 2 && v < BOX - 2;
    }

    /** Named spots for /backrooms anomaly visit (testing). */
    static final java.util.List<String> VISIT_POINTS = java.util.List.of("rug", "hidden_room");

    /**
     * {x, feet y, z, yaw, pitch}: "rug" = the middle of the rug, facing the drawn door (one step
     * forward and you're pressing against it); "hidden_room" = inside the hidden room, in front of
     * the door back. Null for an unknown name.
     */
    @org.jetbrains.annotations.Nullable
    static double[] visit(AnomalySite site, String name, int floor) {
        return switch (name) {
            case "rug" -> Level0SetPieces.spot(site, MOAT, RUG_U, RUG_V, floor + 1, Direction.SOUTH, 0.0f);
            case "hidden_room" -> Level0SetPieces.spot(site, MOAT, ROOM_DOOR_U, ROOM_V1 - 1, ROOM_FLOOR + 1, Direction.NORTH, 0.0f);
            default -> null;
        };
    }
}
