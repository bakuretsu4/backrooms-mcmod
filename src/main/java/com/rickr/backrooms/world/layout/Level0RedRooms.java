package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.WallDecalBlock;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Red Rooms of Level 0: the floor plan of one site. Canon:
 * https://backrooms-wiki.wikidot.com/red-rooms (and the warning signs on
 * https://backrooms-wiki.wikidot.com/level-0).
 *
 * <p>From outside it is a block of ordinary yellow maze walls, except the wallpaper is peeling off
 * them and there is crimson underneath, and the carpet gets sticky. A plain doorway leads into a
 * short yellow corridor; turn its corner and the doorway on your left opens into a crimson room.
 * Inside is the small looping sequence from the wiki's tale:
 * <pre>
 *   start room -> one-turn hall -> tally room -> two-turn hall -> empty room
 *        ^                                                            |
 *        +---- hole in the wall <-- crooked crawlway <-- hole in the wall
 * </pre>
 * The crawlway from the empty room and the one into the start room are two identical copies
 * ("exit" and "entry"). Halfway along their middle leg, where you can only see bare walls and the
 * two corners, the server quietly moves you from one copy to the other ({@link #loopShift}), so the
 * hole in the empty room leads straight back into the start room, forever.
 *
 * <p>The plan is drawn once as a character map ({@link #TEMPLATE}) for a site whose entrance faces
 * north, and turned for the other three directions. Local coordinates: u to the right (east when
 * facing north), v towards the back (south).
 */
public final class Level0RedRooms {
    /** Size of the walled block (local u x v), without the moat. */
    public static final int WIDTH = 24;
    public static final int DEPTH = 27;
    /** A free strip of sticky carpet all around the block joins it to the maze. */
    public static final int MOAT = 2;

    /**
     * Legend:
     * <ul>
     *   <li>{@code Y} outer wall (yellow wallpaper peeling off crimson), {@code R} crimson wall;</li>
     *   <li>{@code O} the opening in the outer wall, {@code v}/{@code V} the yellow corridor
     *       (V: a lamp), {@code d} its doorway through the yellow layer;</li>
     *   <li>{@code D} the doorway through the crimson layer into the start room: this is what seals;</li>
     *   <li>{@code .} crimson room floor, {@code L} with a red lamp above, {@code m} with mushrooms,
     *       {@code P} a doorway between room and hall;</li>
     *   <li>{@code T} wall with tally marks (facing into the tally room), {@code F} the stretch of
     *       tally-room wall that starts to flicker when it's time to let you go;</li>
     *   <li>{@code h} a ragged hole in a wall, {@code c} the low crawlway between the holes.</li>
     * </ul>
     */
    private static final String[] TEMPLATE = {
            "YYYYYYYYYYYYYYYYYYYYOOYY",
            "YRRRRRRRRRRRRRYvvvVvvvYY",
            "YRRRRRRRRRRRRRYvvvVvvvYY",
            "YRRRRRRRRRRRRRYddYYYYYYY",
            "YRRRRRRRRRRRRRRDDRRRRRRY",
            "YRRRRRRRRRRRRR....mRRRRY",
            "YRRRRRRRRRccch.....RRRRY",
            "YRRRRRRRRRcRRR..L..RRRRY",
            "YRRRRRRRRRcRRR.....P..RY",
            "YRRRRRRRRRcRRRm....P..RY",
            "YRRRRRRRRRcRRRRRRRRR..RY",
            "YRRRRRRRRRcRRRRRRRRR..RY",
            "YRRRRRRRcccRRRRRRRRRL.RY",
            "YRRRRRRRRRRRRRRRRRRR..RY",
            "YRRRRRRRRRcccRRRRRRR..RY",
            "YRRRRRRRRRcRRRRRRRRRPPRY",
            "YRRRRRRRRRcRRRRRT.....RY",
            "YRm....RRRcRRRRRT.....RY",
            "YR.....RRRcRRRRRF..L..RY",
            "YR..L..RRRcRRRRRT.....RY",
            "YR.....hcccRRRRRTm....RY",
            "YR....mRRRRRRRRRRRPPRRRY",
            "YRRRPPRRRRRRRRRRRR..RRRY",
            "YRRR.......L........RRRY",
            "YRRR...............mRRRY",
            "YRRRRRRRRRRRRRRRRRRRRRRY",
            "YYYYYYYYYYYYYYYYYYYYYYYY",
    };

    // The crawlways: the middle leg runs along u = CRAWL_U. The exit copy (from the empty room)
    // covers v 15..19, the entry copy (into the start room) v 7..11; one is LOOP_SHIFT behind the other.
    private static final int CRAWL_U = 10;
    private static final int EXIT_LEG_V0 = 15;
    private static final int ENTRY_LEG_V0 = 7;
    private static final int LEG_LENGTH = 5;
    private static final int LOOP_SHIFT = EXIT_LEG_V0 - ENTRY_LEG_V0;
    /** You are moved this far past the middle of the leg, so stepping back doesn't bounce you straight back. */
    private static final double HYSTERESIS = 0.3;

    /** Centres of the three rooms (local, continuous): where the klaxon hum comes from. */
    private static final double[][] ROOM_CENTRES = {{16.5, 7.5}, {19.5, 18.5}, {4.5, 19.5}};
    /** The stretch of tally-room wall that flickers ('F'). */
    private static final int FLICKER_U = 16;
    private static final int FLICKER_V = 18;
    /** The two doorway columns that seal ('D'). */
    private static final int SEAL_U = 15;
    private static final int SEAL_V = 4;
    /** The opening in the outer wall ('O'): the left of its two columns, and the column just outside. */
    private static final int ENTRANCE_U = 20;

    private Level0RedRooms() {}

    // ==========================================================================================
    // Turning between the site's local frame and the world
    // ==========================================================================================

    /** Clockwise quarter turns from "entrance faces north" to the site's facing. */
    private static int turns(AnomalySite site) {
        return (site.facing() + 2) & 3; // 2D data values: 0 S, 1 W, 2 N, 3 E -> N 0, E 1, S 2, W 3
    }

    private static int boxX(AnomalySite site) {
        return site.minX() + MOAT;
    }

    private static int boxZ(AnomalySite site) {
        return site.minZ() + MOAT;
    }

    /** Local u of world column (x, z) (may be outside 0..WIDTH-1). */
    private static int localU(AnomalySite site, int x, int z) {
        int rx = x - boxX(site);
        int rz = z - boxZ(site);
        return switch (turns(site)) {
            case 1 -> rz;
            case 2 -> WIDTH - 1 - rx;
            case 3 -> WIDTH - 1 - rz;
            default -> rx;
        };
    }

    private static int localV(AnomalySite site, int x, int z) {
        int rx = x - boxX(site);
        int rz = z - boxZ(site);
        return switch (turns(site)) {
            case 1 -> DEPTH - 1 - rx;
            case 2 -> DEPTH - 1 - rz;
            case 3 -> rx;
            default -> rz;
        };
    }

    /** World column of local cell (u, v): {x, z}. */
    private static int[] toWorld(AnomalySite site, int u, int v) {
        int bx = boxX(site);
        int bz = boxZ(site);
        return switch (turns(site)) {
            case 1 -> new int[] {bx + DEPTH - 1 - v, bz + u};
            case 2 -> new int[] {bx + WIDTH - 1 - u, bz + DEPTH - 1 - v};
            case 3 -> new int[] {bx + v, bz + WIDTH - 1 - u};
            default -> new int[] {bx + u, bz + v};
        };
    }

    /** Exact local position (u, v) of a world point (for the player, who is not on a block grid). */
    private static double[] toLocal(AnomalySite site, double x, double z) {
        double rx = x - boxX(site);
        double rz = z - boxZ(site);
        return switch (turns(site)) {
            case 1 -> new double[] {rz, DEPTH - rx};
            case 2 -> new double[] {WIDTH - rx, DEPTH - rz};
            case 3 -> new double[] {WIDTH - rz, rx};
            default -> new double[] {rx, rz};
        };
    }

    /** Exact world point {x, z} of a local position. */
    private static double[] toWorldPoint(AnomalySite site, double u, double v) {
        int bx = boxX(site);
        int bz = boxZ(site);
        return switch (turns(site)) {
            case 1 -> new double[] {bx + DEPTH - v, bz + u};
            case 2 -> new double[] {bx + WIDTH - u, bz + DEPTH - v};
            case 3 -> new double[] {bx + v, bz + WIDTH - u};
            default -> new double[] {bx + u, bz + v};
        };
    }

    /** A local step (du, dv) as a world step {dx, dz}. */
    private static double[] toWorldStep(AnomalySite site, double du, double dv) {
        return switch (turns(site)) {
            case 1 -> new double[] {-dv, du};
            case 2 -> new double[] {-du, -dv};
            case 3 -> new double[] {dv, -du};
            default -> new double[] {du, dv};
        };
    }

    /** A direction of the local frame (drawn facing north) turned to the world. */
    private static Direction toWorld(AnomalySite site, Direction local) {
        Direction d = local;
        for (int i = turns(site); i > 0; i--) {
            d = d.getClockWise();
        }
        return d;
    }

    /** The template character at world column (x, z), or 0 outside the walled block (the moat). */
    private static char cell(AnomalySite site, int x, int z) {
        int u = localU(site, x, z);
        int v = localV(site, x, z);
        return charAt(u, v);
    }

    private static char charAt(int u, int v) {
        if (u < 0 || u >= WIDTH || v < 0 || v >= DEPTH) {
            return 0;
        }
        return TEMPLATE[v].charAt(u);
    }

    // ==========================================================================================
    // Questions the layout and the behaviours ask
    // ==========================================================================================

    /** The column just outside the opening, in the moat: {x, z}. */
    static int[] entrance(AnomalySite site) {
        return toWorld(site, ENTRANCE_U, -1);
    }

    /** True for the crimson part (rooms, halls, holes, crawlways): where the place has you. */
    public static boolean isInterior(AnomalySite site, int x, int z) {
        char c = cell(site, x, z);
        return c == '.' || c == 'L' || c == 'm' || c == 'P' || c == 'h' || c == 'c';
    }

    /** True for the yellow corridor and the doorways into the start room (nobody gets sealed in a doorway). */
    public static boolean isDoorway(AnomalySite site, int x, int z) {
        char c = cell(site, x, z);
        return c == 'D' || c == 'd' || c == 'v' || c == 'V' || c == 'O';
    }

    /** The cells that are bricked up while someone is inside: the 'D' doorway, three blocks tall. */
    public static BlockPos[] sealCells(AnomalySite site, int floorY) {
        BlockPos[] cells = new BlockPos[6];
        int i = 0;
        for (int u = SEAL_U; u <= SEAL_U + 1; u++) {
            int[] w = toWorld(site, u, SEAL_V);
            for (int y = floorY + 1; y <= floorY + 3; y++) {
                cells[i++] = new BlockPos(w[0], y, w[1]);
            }
        }
        return cells;
    }

    /** The two blocks of tally-room wall that become the flickering wall. */
    public static BlockPos[] flickerCells(AnomalySite site, int floorY) {
        int[] w = toWorld(site, FLICKER_U, FLICKER_V);
        return new BlockPos[] {new BlockPos(w[0], floorY + 1, w[1]), new BlockPos(w[0], floorY + 2, w[1])};
    }

    /** World points {x, z} of the three room centres (start, tally, empty). */
    public static double[][] roomCentres(AnomalySite site) {
        double[][] out = new double[ROOM_CENTRES.length][];
        for (int i = 0; i < ROOM_CENTRES.length; i++) {
            out[i] = toWorldPoint(site, ROOM_CENTRES[i][0], ROOM_CENTRES[i][1]);
        }
        return out;
    }

    /**
     * The loop. If a player at (x, z) has walked past the middle of a crawlway, returns the world
     * step {dx, dz, forward} that puts them at the same spot in the other copy (forward = 1 when going
     * from the empty room towards the start room, the way the loop runs); otherwise null.
     */
    @Nullable
    public static double[] loopShift(AnomalySite site, double x, double z) {
        double[] local = toLocal(site, x, z);
        double u = local[0];
        double v = local[1];
        if (u < CRAWL_U || u >= CRAWL_U + 1) {
            return null;
        }
        double exitMiddle = EXIT_LEG_V0 + LEG_LENGTH / 2.0;
        double entryMiddle = ENTRY_LEG_V0 + LEG_LENGTH / 2.0;
        if (v >= EXIT_LEG_V0 && v < EXIT_LEG_V0 + LEG_LENGTH && v < exitMiddle - HYSTERESIS) {
            double[] step = toWorldStep(site, 0, -LOOP_SHIFT);
            return new double[] {step[0], step[1], 1};
        }
        if (v >= ENTRY_LEG_V0 && v < ENTRY_LEG_V0 + LEG_LENGTH && v > entryMiddle + HYSTERESIS) {
            double[] step = toWorldStep(site, 0, LOOP_SHIFT);
            return new double[] {step[0], step[1], 0};
        }
        return null;
    }

    /** Solid at eye level (walls). Holes and crawlways are open there. */
    static boolean isSolid(AnomalySite site, int x, int z) {
        char c = cell(site, x, z);
        return c == 'Y' || c == 'R' || c == 'T' || c == 'F';
    }

    static boolean isWalkable(AnomalySite site, int x, int z) {
        return !isSolid(site, x, z);
    }

    // ==========================================================================================
    // Blocks
    // ==========================================================================================

    static void fill(AnomalySite site, int x, int z, int floor, int ceiling, LevelLayout.Column out) {
        int u = localU(site, x, z);
        int v = localV(site, x, z);
        char c = charAt(u, v);
        BlockState tile = ModBlocks.CEILING_TILE.get().defaultBlockState();
        BlockState sticky = ModBlocks.STICKY_CARPET.get().defaultBlockState();
        BlockState redCarpet = ModBlocks.RED_CARPET_COARSE.get().defaultBlockState();
        BlockState red = ModBlocks.RED_WALLPAPER.get().defaultBlockState();
        BlockState peeling = ModBlocks.PEELING_WALLPAPER.get().defaultBlockState();
        out.set(ceiling, tile);
        switch (c) {
            case 0 -> out.set(floor, sticky); // the moat
            case 'Y' -> {
                out.set(floor, ModBlocks.LOBBY_CARPET.get().defaultBlockState());
                out.set(floor + 1, ModBlocks.YELLOW_WALLPAPER_BASEBOARD.get().defaultBlockState());
                for (int y = floor + 2; y < ceiling; y++) {
                    out.set(y, peeling);
                }
            }
            case 'R', 'F' -> {
                out.set(floor, redCarpet);
                BlockState wall = c == 'R' && mouldy(site, u, v) ? ModBlocks.RED_WALLPAPER_MOULD.get().defaultBlockState() : red;
                for (int y = floor + 1; y < ceiling; y++) {
                    out.set(y, wall);
                }
            }
            case 'T' -> {
                out.set(floor, redCarpet);
                for (int y = floor + 1; y < ceiling; y++) {
                    out.set(y, red);
                }
                // Tally marks at eye height, drawn on the side facing into the tally room.
                out.set(floor + 2, ModBlocks.RED_WALL_TALLY.get().defaultBlockState()
                        .setValue(WallDecalBlock.FACING, toWorld(site, Direction.EAST)));
            }
            case 'O', 'd' -> {
                out.set(floor, sticky);
                out.set(ceiling - 1, peeling); // lintel, like any maze doorway
            }
            case 'v' -> out.set(floor, sticky);
            case 'V' -> {
                out.set(floor, sticky);
                out.set(ceiling, ModBlocks.FLUORESCENT_LIGHT.get().defaultBlockState());
            }
            case 'D', 'P' -> {
                out.set(floor, redCarpet);
                out.set(ceiling - 1, red);
            }
            case 'h', 'c' -> {
                // Two blocks tall: you can just walk through, with the ceiling right above your head.
                out.set(floor, redCarpet);
                out.set(floor + 3, red);
                out.set(floor + 4, red);
            }
            case 'L' -> {
                out.set(floor, redCarpet);
                out.set(ceiling, ModBlocks.RED_ROOM_LIGHT.get().defaultBlockState());
            }
            case 'm' -> {
                out.set(floor, redCarpet);
                Direction turn = Direction.from2DDataValue(Hash.range(Hash.mix(site.salt() ^ (u * 31L + v)), 4));
                out.set(floor + 1, ModBlocks.RED_MUSHROOMS.get().defaultBlockState().setValue(WallMountBlock.FACING, turn));
            }
            default -> out.set(floor, redCarpet); // '.'
        }
    }

    /**
     * Black mould grows on some walls next to the rooms' floors. Never next to a crawlway or a hole:
     * both crawlway copies must look exactly alike or the loop would give itself away.
     */
    private static boolean mouldy(AnomalySite site, int u, int v) {
        boolean byRoom = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            char n = charAt(u + d.getStepX(), v + d.getStepZ());
            if (n == 'c' || n == 'h') {
                return false;
            }
            byRoom |= n == '.' || n == 'L' || n == 'm';
        }
        return byRoom && Hash.unit(site.salt(), u, v, 5) < 0.3;
    }
}
