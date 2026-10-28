package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.FurnitureBlock;
import com.rickr.backrooms.block.HiddenNoteBlock;
import com.rickr.backrooms.block.HouseBedBlock;
import com.rickr.backrooms.block.SupplyContainerBlock;
import com.rickr.backrooms.block.WallDecalBlock;
import com.rickr.backrooms.block.Level0WallLampBlock;
import com.rickr.backrooms.block.WallDoorDrawingBlock;
import com.rickr.backrooms.registry.Level0Blocks;
import com.rickr.backrooms.registry.Level0LightBlocks;
import com.rickr.backrooms.registry.Level0HiddenBlocks;
import com.rickr.backrooms.registry.Level39Blocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * Level 0's hidden rooms: small rooms you can actually get into, if you notice the way in. From
 * the maze each one is just a big block of ordinary yellow wall with a carpeted strip (the moat)
 * around it, like the level's other thick wall masses. The way in depends on the kind:
 * <ul>
 *   <li>{@link #SLIT} "the tenant": a crack one block wide in the wall, with a kink in it so you
 *       can't see through. Behind it, a narrow room someone lived in: the bed made with care, a
 *       chair turned to face the corner, a clock ticking that isn't there (client sound).</li>
 *   <li>{@link #STOCKROOM}: a doorway blocked by a pile of boxes and filing cabinets. At one end
 *       there's a gap you can squeeze through. Behind it, rows of office chairs all facing the
 *       back wall; one, in the middle of the back row, is turned round and faces you.</li>
 *   <li>{@link #CRAWLSPACE}: a missing ceiling tile in the moat, with a ladder up the wall. Up
 *       there is the plenum: a dark crawl between ducts that leads to a low room above the
 *       ceiling, where an old field radio is still playing. Its dial saves your checkpoint.</li>
 *   <li>{@link #SEALED}: no way in at all, except a gap two blocks high in a corner of the wall,
 *       on the one side of the block where no light reaches: you only find it with the
 *       flashlight. Inside, a room nobody has been in for years, and a door someone drew on the
 *       inside of the wall, with a chair placed to watch it.</li>
 * </ul>
 * Every room has a note or two from whoever found it first ({@link HiddenNoteBlock}) and supplies.
 * The server stocks a few things in them on top of the usual loot (world/anomaly/level0/
 * HiddenRoomBehavior), the client plays each room's sound (client/anomaly/level0/HiddenRoomEffect).
 *
 * <p>Each plan is drawn once as a 19x19 character map (box plus moat) with the way in on the north
 * side, and turned to the site's facing. Local coordinates: i to the right (east when facing north),
 * j towards the back (south). The crawlspace also has a second map for the level above the ceiling.
 * Everything is a pure function of the site, and {@link #fill} allocates nothing.
 */
public final class Level0HiddenRooms {
    public static final String SLIT = "level_0/hidden_slit";
    public static final String STOCKROOM = "level_0/hidden_stockroom";
    public static final String CRAWLSPACE = "level_0/hidden_crawlspace";
    public static final String SEALED = "level_0/hidden_sealed";
    public static final List<String> KINDS = List.of(SLIT, STOCKROOM, CRAWLSPACE, SEALED);

    /** The walled block (without the moat). */
    public static final int BOX = 15;
    /** The free strip of carpet all around it. */
    public static final int MOAT = 2;
    /** Side of the whole site, moat included: the size of every map below. */
    private static final int N = BOX + 2 * MOAT;

    /** The crawlspace: air from here up to (and including) PLENUM_TOP, its roof just above. */
    private static final int PLENUM_FLOOR_ABOVE_CEILING = 1;
    private static final int PLENUM_HEIGHT = 2;

    /** Note numbers (HiddenNoteBlock.NOTE) of each kind's first note; a room's notes count up from it. */
    private static final int SLIT_NOTES = 0;
    private static final int STOCKROOM_NOTES = 2;
    private static final int CRAWLSPACE_NOTES = 4;
    private static final int SEALED_NOTES = 6;

    /*
     * Legend (floor level):
     *   m moat carpet, M moat carpet with a lit tube above, W wall (wallpaper, baseboard)
     *   . room floor, T with a lit tube above, t with a dead tube above
     *   s a gap in the wall (full height; in the sealed room only two blocks high)
     *   B cardboard boxes two high, K a filing cabinet with boxes on top
     *   h office chair facing south (the back wall), H facing north (you), w facing west
     *   f / e foot / head of a bed, X supply crate, L supply locker, C supply cabinet, d desk with a PC
     *   n a note on the wall next to this floor block, D a door drawn on this wall block
     *   l ladder up the wall (the crawlspace's way in) under a missing ceiling tile
     *   S floor with a warm bare bulb above instead of a tube (the tenant's own light: on a battery,
     *     like a safe room's, so blackouts and outages never reach it)
     */
    private static final String[] SLIT_PLAN = {
            "mmmmmmmmmmmmmmmmmmm",
            "mmmMmmmmmmmmmMmmmmm",
            "mmWWWsWWWWWWWWWWWmm",
            "mmWWWssWWWWWWWWWWmm",
            "mmWWWWsWWWWWWWWWWmm",
            "mmWWWW....neXWWWWmm",
            "mmWWWW.....f.WWWWmm",
            "mmWWWW.......WWWWmm",
            "mMWWWW...S...WWWWMm",
            "mmWWWW.......WWWWmm",
            "mmWWWWwn.....WWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmmmmmmmmMmmmmmmmmm",
            "mmmmmmmmmmmmmmmmmmm",
    };

    private static final String[] STOCKROOM_PLAN = {
            "mmmmmmmmmmmmmmmmmmm",
            "mmmmMmmmmmmmmmMmmmm",
            "mmWWWWWBBK.WWWWWWmm",
            "mmWWWWWBK..WWWWWWmm",
            "mmWW...B..B....WWmm",
            "mmWW...........WWmm",
            "mmWW..T.....T..WWmm",
            "mmWW.h.h.h.h.h.WWmm",
            "mMWW...........WWMm",
            "mmWW.h.h.h.h.h.WWmm",
            "mmWW...........WWmm",
            "mmWW.h.h.H.h.h.WWmm",
            "mmWW..t.....t..WWmm",
            "mmWWL...n.n...CWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmmmMmmmmmmmmmMmmmm",
            "mmmmmmmmmmmmmmmmmmm",
    };

    private static final String[] CRAWLSPACE_PLAN = {
            "mmmmmmmmmmmmmmmmmmm",
            "mmmmmmmmmlmmmMmmmmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mMWWWWWWWWWWWWWWWMm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmmmmMmmmmmmmMmmmmm",
            "mmmmmmmmmmmmmmmmmmm",
    };

    /*
     * The crawlspace above the ceiling tiles (the tiles are its floor). Legend:
     *   ' ' nothing (left as the empty space above Level 0's ceiling, out of reach)
     *   # plenum deck (walls and roof), = a duct in the wall
     *   . air, l the top of the ladder, a a dead camp lantern, B boxes, r the field radio,
     *   P a backpack, n a note on the wall next to it, E a battery emergency lamp someone hung
     *   on the wall (always on: the room's light, a Veil fixture; the radio's dial alone is too faint)
     */
    private static final String[] CRAWLSPACE_UPPER = {
            "   #======#        ",
            "   #.....l#        ",
            "   #.######        ",
            "   #.#             ",
            "   #.#########     ",
            "   #..a.....#      ",
            "   ##......n#      ",
            "    #.......#      ",
            "    #B.....E#      ",
            "    #B......#      ",
            "    #n..r.P.#      ",
            "    #########      ",
            "                   ",
            "                   ",
            "                   ",
            "                   ",
            "                   ",
            "                   ",
            "                   ",
    };

    private static final String[] SEALED_PLAN = {
            "mmmmmmmmmmmmmmmmmmm",
            "mmmmmmmmmmmmmmmmmmm",
            "mmWsWWWWWWWWWWWWWmm",
            "mmWsWWWWWWWWWWWWWmm",
            "mmWssWWWWWWWWWWWWmm",
            "mmWWsWWWWDWWWWWWWmm",
            "mmWWs.n.......WWWmm",
            "mmWWW.........WWWmm",
            "mMWWW..t.H....WWWMm",
            "mmWWWd........WWWmm",
            "mmWWW.........WWWmm",
            "mmWWW......t..WWWmm",
            "mmWWW.........WWWmm",
            "mmWWWL...n...CWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmWWWWWWWWWWWWWWWmm",
            "mmmmmMmmmmmmmMmmmmm",
            "mmmmmmmmmmmmmmmmmmm",
    };

    private Level0HiddenRooms() {}

    /**
     * Which kind the hidden room of a 256x256 grid square is: a Latin square, so every 2x2 block
     * of squares holds all four kinds (each kind is never more than about two squares away). The
     * seed shifts the pattern per world.
     */
    public static String kindFor(long seed, int cellX, int cellZ) {
        int shift = (int) (Hash.mix(seed ^ 0x51DE5EC2E7L) & 3);
        return KINDS.get(Math.floorMod(cellX + 2 * cellZ + shift, 4));
    }

    // ==========================================================================================
    // Turning between the site's local frame and the world
    // ==========================================================================================

    /** Clockwise quarter turns from "way in faces north" to the site's facing (2D data values: 0 S, 1 W, 2 N, 3 E). */
    private static int turns(AnomalySite site) {
        return (site.facing() + 2) & 3;
    }

    private static int localI(AnomalySite site, int x, int z) {
        int rx = x - site.minX();
        int rz = z - site.minZ();
        return switch (turns(site)) {
            case 1 -> rz;
            case 2 -> N - 1 - rx;
            case 3 -> N - 1 - rz;
            default -> rx;
        };
    }

    private static int localJ(AnomalySite site, int x, int z) {
        int rx = x - site.minX();
        int rz = z - site.minZ();
        return switch (turns(site)) {
            case 1 -> N - 1 - rx;
            case 2 -> N - 1 - rz;
            case 3 -> rx;
            default -> rz;
        };
    }

    /** World column of local cell (i, j), as {x, z}. */
    private static int worldX(AnomalySite site, int i, int j) {
        return site.minX() + switch (turns(site)) {
            case 1 -> N - 1 - j;
            case 2 -> N - 1 - i;
            case 3 -> j;
            default -> i;
        };
    }

    private static int worldZ(AnomalySite site, int i, int j) {
        return site.minZ() + switch (turns(site)) {
            case 1 -> i;
            case 2 -> N - 1 - j;
            case 3 -> N - 1 - i;
            default -> j;
        };
    }

    /**
     * The same site with its anchor moved to the moat block right in front of the way in (under
     * the missing tile for the crawlspace), so /backrooms anomaly locate points you at the crack,
     * the boxes, the ladder or the gap rather than at the middle of a solid block of wall.
     */
    public static AnomalySite withWayIn(AnomalySite site) {
        int i = switch (site.kind()) {
            case SLIT -> 5;
            case STOCKROOM -> 10;
            case CRAWLSPACE -> 9;
            default -> 3;
        };
        return new AnomalySite(site.kind(), site.minX(), site.minZ(), site.maxX(), site.maxZ(),
                worldX(site, i, 1), worldZ(site, i, 1), site.facing(), site.salt());
    }

    /**
     * A test point for /backrooms anomaly visit: {x, feetY, z, lookX, lookZ} (world), or null.
     * "entrance" stands in the moat facing the way in; "inside" stands in the room facing what
     * makes it unsettling (the bed, the chair that faces you, the radio, the chair watching the
     * drawn door).
     */
    @Nullable
    static double[] visit(AnomalySite site, String name, int floor, int ceiling) {
        double si;
        double sj;
        double ti;
        double tj;
        double y = floor + 1.0;
        if (name.equals("entrance")) {
            AnomalySite in = withWayIn(site);
            // withWayIn's anchor is the moat block (i, 1); stand one further out, on (i, 0), looking in.
            si = localI(site, in.anchorX(), in.anchorZ()) + 0.5;
            sj = 0.5;
            ti = si;
            tj = 3.0;
        } else if (name.equals("inside")) {
            switch (site.kind()) {
                case SLIT -> { // by the table lamp, looking at the made bed and the crate
                    si = 8.5;
                    sj = 8.5;
                    ti = 11.5;
                    tj = 5.5;
                }
                case STOCKROOM -> { // just past the boxes, looking down the rows to the chair that faces you
                    si = 9.5;
                    sj = 5.5;
                    ti = 9.5;
                    tj = 11.5;
                }
                case CRAWLSPACE -> { // up in the crawlspace room, looking at the radio
                    si = 8.5;
                    sj = 6.5;
                    ti = 8.5;
                    tj = 10.5;
                    y = ceiling + PLENUM_FLOOR_ABOVE_CEILING;
                }
                default -> { // behind the chair, looking past it at the drawn door
                    si = 9.5;
                    sj = 10.5;
                    ti = 9.5;
                    tj = 5.5;
                }
            }
        } else {
            return null;
        }
        return new double[] {pointX(site, si, sj), y, pointZ(site, si, sj), pointX(site, ti, tj), pointZ(site, ti, tj)};
    }

    /** A direction of the local frame (drawn facing north) turned to the world. */
    private static Direction toWorld(AnomalySite site, Direction local) {
        Direction d = local;
        for (int k = turns(site); k > 0; k--) {
            d = d.getClockWise();
        }
        return d;
    }

    @Nullable
    private static String[] plan(String kind) {
        return switch (kind) {
            case SLIT -> SLIT_PLAN;
            case STOCKROOM -> STOCKROOM_PLAN;
            case CRAWLSPACE -> CRAWLSPACE_PLAN;
            case SEALED -> SEALED_PLAN;
            default -> null;
        };
    }

    private static char at(String[] map, int i, int j) {
        if (i < 0 || i >= N || j < 0 || j >= N) {
            return 0;
        }
        return map[j].charAt(i);
    }

    /** The floor-level character at world column (x, z) (0 outside the site). */
    private static char cell(AnomalySite site, int x, int z) {
        String[] map = plan(site.kind());
        return map == null ? 0 : at(map, localI(site, x, z), localJ(site, x, z));
    }

    // ==========================================================================================
    // Questions the layout and the behaviours ask
    // ==========================================================================================

    /** Solid at eye level: walls, drawn doors and the piles of boxes. */
    public static boolean isSolid(AnomalySite site, int x, int z) {
        char c = cell(site, x, z);
        return c == 'W' || c == 'D' || c == 'B' || c == 'K';
    }

    /** Somewhere you could stand at floor level (furniture doesn't count). */
    public static boolean isWalkable(AnomalySite site, int x, int z) {
        char c = cell(site, x, z);
        return c == 'm' || c == 'M' || c == '.' || c == 's' || c == 'T' || c == 't' || c == 'S' || c == 'n' || c == 'l';
    }

    /**
     * True if a player standing at (x, feetY, z) is in the hidden room itself (past the way in):
     * the server stocks it and the client plays its sound only then.
     */
    public static boolean inRoom(AnomalySite site, int x, double feetY, int z, int ceiling) {
        int i = localI(site, x, z);
        int j = localJ(site, x, z);
        if (site.kind().equals(CRAWLSPACE)) {
            char c = at(CRAWLSPACE_UPPER, i, j);
            return feetY > ceiling && j >= 5 && (c == '.' || c == 'n' || c == 'a');
        }
        String[] map = plan(site.kind());
        if (map == null || i < MOAT || j < MOAT || i >= N - MOAT || j >= N - MOAT) {
            return false;
        }
        char c = at(map, i, j);
        return c == '.' || c == 'T' || c == 't' || c == 'S' || c == 'n';
    }

    /** Middle of the hidden room (where its sound comes from): {x, y, z}. */
    public static double[] roomCentre(AnomalySite site, int floor, int ceiling) {
        double i;
        double j;
        double y = floor + 2.0;
        switch (site.kind()) {
            case SLIT -> {
                i = 9.5;
                j = 8.0;
            }
            case STOCKROOM -> {
                i = 9.5;
                j = 9.0;
            }
            case CRAWLSPACE -> {
                i = 8.5;
                j = 8.0;
                y = ceiling + PLENUM_FLOOR_ABOVE_CEILING + 1.0;
            }
            default -> {
                i = 9.5;
                j = 9.5;
            }
        }
        return new double[] {pointX(site, i, j), y, pointZ(site, i, j)};
    }

    /** Exact world point of a local position (continuous, cell corners at whole numbers). */
    private static double pointX(AnomalySite site, double i, double j) {
        return site.minX() + switch (turns(site)) {
            case 1 -> N - j;
            case 2 -> N - i;
            case 3 -> j;
            default -> i;
        };
    }

    private static double pointZ(AnomalySite site, double i, double j) {
        return site.minZ() + switch (turns(site)) {
            case 1 -> i;
            case 2 -> N - j;
            case 3 -> N - i;
            default -> j;
        };
    }

    /** One supply container of a room and the kind of thing the finder put in it (see HiddenRoomBehavior). */
    public record Stash(BlockPos pos, char what) {}

    /** Every supply container of the site (crate X, locker L, cabinet C, backpack P). Allocates: server only. */
    public static List<Stash> stashes(AnomalySite site, int floor, int ceiling) {
        List<Stash> out = new ArrayList<>();
        String[] map = plan(site.kind());
        if (map == null) {
            return out;
        }
        boolean upper = site.kind().equals(CRAWLSPACE);
        String[] grid = upper ? CRAWLSPACE_UPPER : map;
        int y = upper ? ceiling + PLENUM_FLOOR_ABOVE_CEILING : floor + 1;
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                char c = at(grid, i, j);
                if (c == 'X' || c == 'L' || c == 'C' || c == 'P') {
                    out.add(new Stash(new BlockPos(worldX(site, i, j), y, worldZ(site, i, j)), c));
                }
            }
        }
        return out;
    }

    // ==========================================================================================
    // Blocks
    // ==========================================================================================

    public static void fill(AnomalySite site, int x, int z, int floor, int ceiling, LevelLayout.Column out) {
        String[] map = plan(site.kind());
        if (map == null) {
            return;
        }
        int i = localI(site, x, z);
        int j = localJ(site, x, z);
        char c = at(map, i, j);
        out.set(floor, ModBlocks.LOBBY_CARPET.get().defaultBlockState());
        out.set(ceiling, ModBlocks.CEILING_TILE.get().defaultBlockState());
        switch (c) {
            case 'W' -> {
                out.set(floor + 1, ModBlocks.YELLOW_WALLPAPER_BASEBOARD.get().defaultBlockState());
                for (int y = floor + 2; y < ceiling; y++) {
                    out.set(y, ModBlocks.YELLOW_WALLPAPER.get().defaultBlockState());
                }
            }
            case 'D' -> {
                // A door drawn on the inside of the wall, on the side facing the room.
                Direction face = toWorld(site, openSide(map, i, j));
                BlockState door = ModBlocks.WALL_DOOR_DRAWING.get().defaultBlockState().setValue(WallDecalBlock.FACING, face);
                out.set(floor + 1, door.setValue(WallDoorDrawingBlock.HALF, DoubleBlockHalf.LOWER));
                out.set(floor + 2, door.setValue(WallDoorDrawingBlock.HALF, DoubleBlockHalf.UPPER));
                for (int y = floor + 3; y < ceiling; y++) {
                    out.set(y, ModBlocks.YELLOW_WALLPAPER.get().defaultBlockState());
                }
            }
            case 's' -> {
                // The sealed room's gap is only two blocks high: it reads as a crack, not a doorway.
                if (site.kind().equals(SEALED)) {
                    for (int y = floor + 3; y < ceiling; y++) {
                        out.set(y, ModBlocks.YELLOW_WALLPAPER.get().defaultBlockState());
                    }
                }
            }
            case 'M', 'T' -> out.set(ceiling, Level0Tubes.pick(site.salt(), x, z, false, false));
            case 't' -> out.set(ceiling, Level0Tubes.pick(site.salt(), x, z, true, false));
            case 'S' -> out.set(ceiling, ModBlocks.SAFE_ROOM_LAMP.get().defaultBlockState());
            case 'B' -> {
                BlockState boxes = ModBlocks.CARDBOARD_BOXES.get().defaultBlockState()
                        .setValue(FurnitureBlock.FACING, Direction.from2DDataValue(Hash.range(Hash.mix(site.salt() ^ (i * 31L + j)), 4)));
                out.set(floor + 1, boxes);
                out.set(floor + 2, boxes.setValue(FurnitureBlock.FACING, boxes.getValue(FurnitureBlock.FACING).getClockWise()));
            }
            case 'K' -> {
                out.set(floor + 1, ModBlocks.FILING_CABINET.get().defaultBlockState()
                        .setValue(FurnitureBlock.FACING, toWorld(site, Direction.NORTH)));
                out.set(floor + 2, ModBlocks.CARDBOARD_BOXES.get().defaultBlockState()
                        .setValue(FurnitureBlock.FACING, toWorld(site, Direction.EAST)));
            }
            case 'h', 'H', 'w' -> {
                Direction local = c == 'h' ? Direction.SOUTH : c == 'H' ? Direction.NORTH : Direction.WEST;
                out.set(floor + 1, ModBlocks.OFFICE_CHAIR.get().defaultBlockState().setValue(FurnitureBlock.FACING, toWorld(site, local)));
            }
            case 'f', 'e' -> {
                // FACING points from the foot to the head.
                boolean head = c == 'e';
                Direction local = towards(map, i, j, head ? 'f' : 'e');
                Direction footToHead = head ? local.getOpposite() : local;
                out.set(floor + 1, ModBlocks.BED.get().defaultBlockState()
                        .setValue(FurnitureBlock.FACING, toWorld(site, footToHead))
                        .setValue(HouseBedBlock.HEAD, head));
            }
            case 'X', 'L', 'C' -> {
                BlockState box = c == 'X' ? ModBlocks.SUPPLY_CRATE.get().defaultBlockState()
                        : c == 'L' ? ModBlocks.SUPPLY_LOCKER.get().defaultBlockState() : ModBlocks.SUPPLY_CABINET.get().defaultBlockState();
                out.set(floor + 1, box.setValue(SupplyContainerBlock.FACING, toWorld(site, openSide(map, i, j))));
            }
            // The desk stands with its back to the wall, so whoever sat at it faced the wall.
            case 'd' -> out.set(floor + 1, ModBlocks.OFFICE_DESK_PC.get().defaultBlockState()
                    .setValue(FurnitureBlock.FACING, toWorld(site, wallSide(map, i, j).getOpposite())));
            case 'n' -> out.set(floor + 2, note(site, map, i, j, firstNote(site.kind())));
            case 'l' -> {
                // A ladder up the wall and through a missing ceiling tile into the crawlspace.
                BlockState ladder = Level0Blocks.LADDER.get().defaultBlockState()
                        .setValue(LadderBlock.FACING, toWorld(site, wallSide(map, i, j).getOpposite()));
                for (int y = floor + 1; y <= ceiling + PLENUM_FLOOR_ABOVE_CEILING; y++) {
                    out.set(y, ladder);
                }
            }
            default -> {
                // Plain floor ('.', 'm'): just carpet and ceiling; the air above is left empty.
            }
        }
        if (site.kind().equals(CRAWLSPACE)) {
            fillUpper(site, i, j, ceiling, out);
        }
    }

    /** The crawlspace's level above the ceiling: deck walls, ducts, the room and what's in it. */
    private static void fillUpper(AnomalySite site, int i, int j, int ceiling, LevelLayout.Column out) {
        char c = at(CRAWLSPACE_UPPER, i, j);
        if (c == ' ' || c == 0) {
            return;
        }
        int bottom = ceiling + PLENUM_FLOOR_ABOVE_CEILING;
        int roof = bottom + PLENUM_HEIGHT;
        BlockState deck = Level0HiddenBlocks.PLENUM.get().defaultBlockState();
        out.set(roof, deck);
        switch (c) {
            case '#' -> {
                for (int y = bottom; y < roof; y++) {
                    out.set(y, deck);
                }
            }
            case '=' -> {
                out.set(bottom, Level0HiddenBlocks.DUCT.get().defaultBlockState());
                out.set(bottom + 1, deck);
            }
            case 'a' -> out.set(bottom, Level39Blocks.DEAD_LAMP.get().defaultBlockState()
                    .setValue(FurnitureBlock.FACING, toWorld(site, Direction.SOUTH)));
            case 'B' -> out.set(bottom, ModBlocks.CARDBOARD_BOXES.get().defaultBlockState()
                    .setValue(FurnitureBlock.FACING, toWorld(site, Direction.EAST)));
            case 'r' -> out.set(bottom, ModBlocks.CHECKPOINT_RADIO.get().defaultBlockState()
                    .setValue(FurnitureBlock.FACING, toWorld(site, openSide(CRAWLSPACE_UPPER, i, j))));
            case 'P' -> out.set(bottom, ModBlocks.BACKPACK.get().defaultBlockState()
                    .setValue(SupplyContainerBlock.FACING, toWorld(site, openSide(CRAWLSPACE_UPPER, i, j))));
            case 'n' -> out.set(bottom + 1, note(site, CRAWLSPACE_UPPER, i, j, CRAWLSPACE_NOTES));
            // Just under the roof, on the wall, lit for good (a battery lamp: outages don't touch it).
            case 'E' -> out.set(bottom + 1, Level0LightBlocks.EMERGENCY_LAMP.get().defaultBlockState()
                    .setValue(Level0WallLampBlock.FACING, toWorld(site, wallSide(CRAWLSPACE_UPPER, i, j).getOpposite()))
                    .setValue(Level0WallLampBlock.LIT, true));
            default -> {
                // '.' and the ladder top: air under the roof.
            }
        }
    }

    private static int firstNote(String kind) {
        return switch (kind) {
            case SLIT -> SLIT_NOTES;
            case STOCKROOM -> STOCKROOM_NOTES;
            case CRAWLSPACE -> CRAWLSPACE_NOTES;
            default -> SEALED_NOTES;
        };
    }

    /**
     * The note pinned on the wall beside floor cell (i, j). A room's notes are numbered from
     * {@code first} in reading order (row by row), so each one always says the same thing.
     */
    private static BlockState note(AnomalySite site, String[] map, int i, int j, int first) {
        int index = first;
        for (int jj = 0; jj < j; jj++) {
            index += count(map[jj], 'n');
        }
        for (int ii = 0; ii < i; ii++) {
            if (map[j].charAt(ii) == 'n') {
                index++;
            }
        }
        index = Math.min(index, HiddenNoteBlock.NOTES - 1);
        return Level0HiddenBlocks.NOTE.get().defaultBlockState()
                .setValue(HiddenNoteBlock.FACING, toWorld(site, wallSide(map, i, j).getOpposite()))
                .setValue(HiddenNoteBlock.NOTE, index);
    }

    private static int count(String row, char c) {
        int n = 0;
        for (int k = 0; k < row.length(); k++) {
            if (row.charAt(k) == c) {
                n++;
            }
        }
        return n;
    }

    private static boolean isWallChar(char c) {
        return c == 'W' || c == 'D' || c == '#' || c == '=';
    }

    /** The first side (local N, E, S, W) of cell (i, j) with a wall on it. */
    private static Direction wallSide(String[] map, int i, int j) {
        for (Direction d : LOCAL_ORDER) {
            if (isWallChar(at(map, i + d.getStepX(), j + d.getStepZ()))) {
                return d;
            }
        }
        return Direction.NORTH;
    }

    /** The first side (local S, N, E, W... see OPEN_ORDER) of cell (i, j) facing open floor: where a front looks. */
    private static Direction openSide(String[] map, int i, int j) {
        for (Direction d : OPEN_ORDER) {
            char n = at(map, i + d.getStepX(), j + d.getStepZ());
            if (n == '.' || n == 'T' || n == 't' || n == 'S') {
                return d;
            }
        }
        return Direction.SOUTH;
    }

    /** The side of (i, j) where character {@code target} is. */
    private static Direction towards(String[] map, int i, int j, char target) {
        for (Direction d : LOCAL_ORDER) {
            if (at(map, i + d.getStepX(), j + d.getStepZ()) == target) {
                return d;
            }
        }
        return Direction.NORTH;
    }

    private static final Direction[] LOCAL_ORDER = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final Direction[] OPEN_ORDER = {Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST};
}
