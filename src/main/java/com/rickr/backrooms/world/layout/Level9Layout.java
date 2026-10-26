package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.HighwaySignBlock;
import com.rickr.backrooms.block.HouseBedBlock;
import com.rickr.backrooms.block.HouseDoorBlock;
import com.rickr.backrooms.block.LampMode;
import com.rickr.backrooms.block.SignKind;
import com.rickr.backrooms.block.StreetSignBlock;
import com.rickr.backrooms.block.StreetlampHeadBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level9.Level9Sites;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.Nullable;

/**
 * Level 9, "The Suburbs": an endless 1950s-70s suburb at eternal midnight.
 *
 * <p><b>Streets.</b> Long main streets run east-west, one every ~78 blocks (each one 7 or 9 wide,
 * with 2-wide sidewalks). Between two main streets, side streets run north-south every ~64-104
 * blocks, at different places in every row of blocks, so crossings are offset like bricks and
 * most junctions are T-junctions. A main street shifts sideways by a few blocks at every side
 * street that leaves it, so it never runs perfectly straight. Some side streets are short
 * cul-de-sacs ending in a turning circle, some junctions are roundabouts (a grass circle with a
 * dead tree, or a dry fountain).
 *
 * <p><b>Lots.</b> Along both sides of every main street lie 18-wide lots, 21 deep: a front lawn,
 * a path to the porch, a mailbox, maybe a picket fence, a house (1 or 2 storeys, gabled roof,
 * sometimes an attached garage with a driveway), a backyard with a fence or hedge at the back.
 * Behind the backyards, between the two rows of lots, is a strip of dark forest: tall pines with
 * stacked rings of needles, big leafy trees with limbs and lumpy crowns, and bare dead trees. Some
 * lawn verges carry a street tree whose crown leans out over the sidewalk and the road edge.
 * Lots that would touch a side street, roundabout or the exit stay empty lawn and
 * woods instead. About 30% of the front doors are unlocked: inside it's dark, with a living room,
 * a kitchen and (upstairs) a bedroom.
 *
 * <p><b>Heights</b> (players can't jump, so every step is at most half a block):
 * <pre>
 *   road surface      block y40 -> you stand at 41.0
 *   sidewalk          slab  y41 -> 41.5
 *   lawn, path, porch, house floor, forest   block y41 -> 42.0
 *   upper floor       block y46 -> 47.0 (reached by stairs inside)
 * </pre>
 * Roads only ever touch sidewalks (the road shapes are always wrapped in a 2-wide sidewalk), so
 * nowhere does a lawn sit right next to the road.
 *
 * <p><b>Exit.</b> One per 512x512 region: a concrete highway embankment crosses a main street,
 * which runs through it in a 29-block-long dark underpass. Walk deep enough into it (the middle 11
 * blocks, where the road is worn concrete) and you are out ({@code Level9Rules}).
 *
 * <p>Streetlamps: 30% dead, 15% flickering, 55% working (1 in 6 of those a cold mercury lamp).
 * Street signs at junctions point toward the nearest exit (they lie about one time in seven);
 * Neighborhood Watch notices and warnings stand in front of empty lots.
 *
 * <p><b>Safe houses.</b> Each 96x96 square of the suburb picks one random point; if it falls on
 * a lot with a suitable house, that house is a safe house: porch light on, a SAFE sign over the
 * front door (outside and inside), the door unlocked, and in the living room a checkpoint radio,
 * a supply crate, a made cot and a warm lamp. The whole house counts as the safe room. Now and
 * then someone's backpack lies on a porch or a driveway.
 *
 * <p><b>Anomalies</b> ({@link Level9AnomalyPlan}, kinds in {@link Level9Sites}): clipped houses
 * (two houses grown into each other), twin houses (leave one by its back door and you are in the
 * other one's front hall), and rare grass walkways between houses that lead out of the suburb to
 * Level 9.1, the Crimson Field. The field is part of this same dimension, far to the east
 * (x &gt;= {@link Level9Sites#FIELD_MIN_X}): {@link #fillColumn} builds endless grass there.
 */
public final class Level9Layout extends LevelLayout {
    // ---- Streets -----------------------------------------------------------------------------
    /** Distance between main streets (north-south), plus/minus ROW_JITTER. */
    private static final int ROW = 78;
    private static final int ROW_JITTER = 4;
    /** Distance between side streets along a row, plus/minus COL_JITTER. */
    private static final int COL = 84;
    private static final int COL_JITTER = 10;
    /** Side streets: road half-width (7 wide). Main streets are 7 or 9 wide. */
    private static final int SIDE_HALF = 3;
    /** Sidewalk width on each side of every road. */
    static final int SW = 2;
    private static final int STUB_LEN = 26;
    private static final int BULB_R = 8;
    /** Roundabouts: island radius (curb ring) and road radius. */
    private static final int ISLAND_R = 5;
    private static final int RB_R = 11;
    /** No roundabout where another side street joins the same main street this close by. */
    private static final int RB_CLEAR = 20;

    // ---- Lots --------------------------------------------------------------------------------
    /** Lot width along the street and depth away from it. */
    public static final int LW = 18;
    public static final int LD = 21;
    /** Lots keep this far from side streets (full / cul-de-sac), jogs and roundabouts. */
    private static final int VERT_MARGIN = 7;
    private static final int STUB_MARGIN = 11;
    private static final int JOG_MARGIN = 4;
    private static final int RB_LOT_MARGIN = 14;
    /** Empty lots: lawn this deep from the sidewalk, then woods. */
    private static final int EMPTY_LAWN = 7;

    // ---- Exit --------------------------------------------------------------------------------
    private static final int EXIT_REGION = 512;
    /** Half the length of the underpass (it is 2 * EXIT_HALF + 1 long). */
    private static final int EXIT_HALF = 14;
    /** Side streets keep away from the underpass by this much. */
    private static final int EXIT_CLEAR = 30;
    /** Walking within this many blocks of the underpass middle is escaping. */
    private static final int EXIT_TRIGGER = 5;
    /** The underpass roof runs from here up to the deck surface block. */
    private static final int TUNNEL_CEILING_Y = 48;
    private static final int DECK_TOP_Y = 51;
    /** The EXIT sign's lower row; its upper row is one above. */
    private static final int SIGN_LOW_Y = 49;
    /** Lit streetlamps stand this far from the underpass middle, framing each mouth with light. */
    private static final int MOUTH_LAMP_OFFSET = EXIT_HALF + 3;
    /** The one flickering light inside the tunnel hangs this far west of the middle. */
    private static final int TUNNEL_LIGHT_OFFSET = 7;
    /** Highway lamps up on the deck: how far from the road, and the height of their heads. */
    private static final int DECK_LAMP_OFFSET = 12;
    private static final int DECK_LAMP_Y = 56;

    // ---- Lights and trees --------------------------------------------------------------------
    /** Streetlamps every LAMP_SPACING / 2 blocks, alternating sides of the street. */
    private static final int LAMP_SPACING = 42;
    private static final int LAMP_Y = 47;
    /** Arrivals: a sidewalk with a working streetlamp at most this far along the curb. */
    private static final int ARRIVAL_LAMP_REACH = 8;
    private static final int TREE_CELL = 6;

    // ---- Safe houses ---------------------------------------------------------------------------
    /** One safe-house point per SAFE_CELL x SAFE_CELL square (lots cover about a third of the ground). */
    private static final int SAFE_CELL = 96;

    // ---- Side street kinds -------------------------------------------------------------------
    private static final int NONE = 0;
    private static final int STUB = 1;
    private static final int FULL = 2;

    // ---- What a column is ----------------------------------------------------------------------
    private static final int ROAD = 0;
    private static final int SIDEWALK = 1;
    private static final int CURB = 2;
    private static final int ISLAND = 3;
    private static final int ISLAND_CURB = 4;
    private static final int EMBANKMENT = 5;
    private static final int TUNNEL_ROAD = 6;
    private static final int TUNNEL_WALK = 7;
    private static final int TUNNEL_CURB = 8;
    /** Not street: a lot, empty lot or woods. */
    static final int OTHER = 9;
    /** Level 9.1: the Crimson Field region far to the east (no streets, lots or woods). */
    private static final int FIELD = 10;

    /** A lot's front faces the street: side 0 lots lie south of their street, side 1 north. */
    static final int SOUTH_OF_STREET = 0;

    static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** Where the anomalies are and how their blocks look. */
    private final Level9AnomalyPlan plan;

    public Level9Layout() {
        // The ExitLocator picks one point per 512x512 region; the underpass goes where the main
        // street nearest that point crosses its x.
        super(8, new ExitLocator(EXIT_REGION, 96, 9000));
        this.plan = new Level9AnomalyPlan(this);
    }

    // ==========================================================================================
    // Public queries
    // ==========================================================================================

    /** Street lamps hang at y47, house ceilings reach y51, the highway lamps' heads y56. */
    @Override
    public int highestCeilingY() {
        return DECK_LAMP_Y + 1;
    }

    /** Road surface (where Watchers patrol): asphalt of streets, roundabouts and the underpass. */
    public boolean isRoad(long seed, int x, int z) {
        int c = classify(seed, x, z);
        return c == ROAD || c == TUNNEL_ROAD;
    }

    /**
     * Plain open street (no underpass, curb or sidewalk): the only ground the Mangled fog's
     * pillar may stand on and blast its crater into.
     */
    public boolean isPlainRoad(long seed, int x, int z) {
        return classify(seed, x, z) == ROAD;
    }

    /** Inside the footprint of a house or garage. */
    public boolean isIndoors(long seed, int x, int z) {
        if (classify(seed, x, z) != OTHER) {
            return false;
        }
        House h = houseAt(seed, x, z);
        return h != null && h.indoors(h.u, h.v);
    }

    /** The whole footprint of a safe house, walls included. */
    @Override
    public boolean isSafeRoom(long seed, int x, int z) {
        if (classify(seed, x, z) != OTHER) {
            return false;
        }
        House h = houseAt(seed, x, z);
        return h != null && h.safe && h.inHouse(h.u, h.v);
    }

    // ---- Anomalies (see Level9AnomalyPlan) --------------------------------------------------------

    @Override
    @Nullable
    public AnomalySite anomalyAt(long seed, int x, int z) {
        return plan.siteAt(seed, x, z);
    }

    @Override
    public void anomaliesNear(long seed, int x, int z, int radius, Consumer<AnomalySite> out) {
        plan.sitesNear(seed, x, z, radius, out);
    }

    @Override
    @Nullable
    public AnomalySite nearestAnomaly(long seed, int x, int z, String kind) {
        return plan.nearest(seed, x, z, kind);
    }

    @Override
    public List<String> anomalyKinds() {
        return Level9AnomalyPlan.KINDS;
    }

    /** Deep inside the underpass: walking here escapes the Backrooms. */
    public boolean isEscapeZone(long seed, int x, int z) {
        int c = classify(seed, x, z);
        if (c != TUNNEL_ROAD && c != TUNNEL_WALK && c != TUNNEL_CURB) {
            return false;
        }
        return Math.abs(x - exitX(seed, x, z)) <= EXIT_TRIGGER;
    }

    @Override
    public boolean isWalkable(long seed, int x, int z) {
        return groundSurface(seed, x, z) > 0;
    }

    @Override
    public int standY(long seed, int x, int z) {
        double feet = groundSurface(seed, x, z);
        return feet > 0 ? (int) Math.floor(feet) : FLOOR_Y + 2;
    }

    @Override
    public double feetY(long seed, int x, int z) {
        double feet = groundSurface(seed, x, z);
        return feet > 0 ? feet : FLOOR_Y + 2;
    }

    /** You arrive on a sidewalk within a few blocks of a streetlamp that works. */
    @Override
    public boolean isSafeArrival(long seed, int x, int z) {
        if (classify(seed, x, z) != SIDEWALK) {
            return false;
        }
        // Lamp poles stand in the curb right beside the sidewalk: look along it both ways.
        for (int d = -ARRIVAL_LAMP_REACH; d <= ARRIVAL_LAMP_REACH; d++) {
            if (litPole(seed, x + d, z - 1) || litPole(seed, x + d, z + 1)
                    || litPole(seed, x - 1, z + d) || litPole(seed, x + 1, z + d)) {
                return true;
            }
        }
        return false;
    }

    /** Sidewalks are only 2 wide, so never ask for walkable space all around. */
    @Override
    public boolean arrivalNeedsSpace() {
        return false;
    }

    /** The middle of the nearest underpass. */
    @Override
    public int[] exitPosition(long seed, int x, int z) {
        int rx = Math.floorDiv(x, EXIT_REGION);
        int rz = Math.floorDiv(z, EXIT_REGION);
        int[] best = null;
        long bestDist = Long.MAX_VALUE;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int ex = exits().exitX(seed, rx + ox, rz + oz);
                int street = exitStreet(seed, exits().exitZ(seed, rx + ox, rz + oz));
                int ez = streetZ(seed, street, ex);
                long d = (long) (ex - x) * (ex - x) + (long) (ez - z) * (ez - z);
                if (d < bestDist) {
                    bestDist = d;
                    best = new int[] {ex, ez};
                }
            }
        }
        return best;
    }

    /**
     * Height of the feet of someone standing at street level in column (x, z), or -1 if there is
     * nowhere to stand (a wall, fence, tree, house...). Read from the blocks the column really has.
     */
    private double groundSurface(long seed, int x, int z) {
        BlockState[] col = new BlockState[16];
        fillColumn(seed, x, z, (y, state) -> {
            if (y >= FLOOR_Y && y < FLOOR_Y + 16) {
                col[y - FLOOR_Y] = state;
            }
        });
        // Only street level counts: ground blocks y40 (road, sidewalk base) and y41 (lawn, floors).
        for (int i = 0; i <= 1; i++) {
            BlockState ground = col[i];
            if (isPassable(ground) || !isPassable(col[i + 1]) && !isBottomSlab(col[i + 1])) {
                continue;
            }
            if (isBottomSlab(col[i + 1])) {
                if (isPassable(col[i + 2]) && isPassable(col[i + 3])) {
                    return FLOOR_Y + i + 1.5;
                }
                continue;
            }
            if (isPassable(col[i + 1]) && isPassable(col[i + 2])) {
                return FLOOR_Y + i + 1;
            }
        }
        return -1;
    }

    private static boolean isPassable(@Nullable BlockState state) {
        return state == null || state.isAir() || state.is(ModBlocks.LEAF_LITTER.get()) || state.is(ModBlocks.DUST_PILE.get())
                || state.is(ModBlocks.L9_FIELD_GRASS.get());
    }

    private static boolean isBottomSlab(@Nullable BlockState state) {
        return state != null && state.hasProperty(SlabBlock.TYPE) && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    // ==========================================================================================
    // Street network
    // ==========================================================================================

    /** Base Z of main street r (before its per-piece shifts). */
    private static int streetZ0(long seed, int r) {
        return r * ROW + Hash.range(Hash.of(seed, r, 0, 900), 2 * ROW_JITTER + 1) - ROW_JITTER;
    }

    /** Main street r is 7 (half 3) or 9 (half 4) wide. */
    private static int roadHalf(long seed, int r) {
        return 3 + Hash.range(Hash.of(seed, r, 0, 901), 2);
    }

    private static int rowOffset(long seed, int r) {
        return Hash.range(Hash.of(seed, r, 0, 902), COL);
    }

    /** X of side street k in the row between main streets r and r + 1. */
    private static int sideX(long seed, int r, int k) {
        return k * COL + rowOffset(seed, r) + Hash.range(Hash.of(seed, r, k, 903), 2 * COL_JITTER + 1) - COL_JITTER;
    }

    private static int kGuess(long seed, int r, int x) {
        return Math.floorDiv(x - rowOffset(seed, r), COL);
    }

    /** NONE (no street here), STUB (cul-de-sac) or FULL (reaches the next main street). */
    private int sideKind(long seed, int r, int k) {
        int sx = sideX(seed, r, k);
        if (nearExit(seed, r, sx)) {
            return NONE; // keep the underpass street straight and clear
        }
        double u = Hash.unit(seed, r, k, 904);
        return u < 0.12 ? NONE : u < 0.30 ? STUB : FULL;
    }

    /** The exit's main street: the one whose base line is nearest the exit point's z. */
    private static int exitStreet(long seed, int ez) {
        int r = Math.floorDiv(ez + ROW / 2, ROW);
        int best = r;
        int bestDist = Math.abs(streetZ0(seed, r) - ez);
        for (int c = r - 1; c <= r + 1; c += 2) {
            int d = Math.abs(streetZ0(seed, c) - ez);
            if (d < bestDist) {
                best = c;
                bestDist = d;
            }
        }
        return best;
    }

    /** True if side street x in row r would come close to an underpass on main street r or r + 1. */
    private boolean nearExit(long seed, int r, int sx) {
        int z = streetZ0(seed, r);
        for (int dz = 0; dz <= ROW; dz += ROW) {
            int rx = Math.floorDiv(sx, EXIT_REGION);
            int rz = Math.floorDiv(z + dz, EXIT_REGION);
            int ex = exits().exitX(seed, rx, rz);
            int street = exitStreet(seed, exits().exitZ(seed, rx, rz));
            if ((r == street || r == street - 1) && Math.abs(sx - ex) < EXIT_CLEAR) {
                return true;
            }
        }
        return false;
    }

    /** The nearest side street at or west of x in row r (main street r shifts at each of them). */
    private int leftSide(long seed, int r, int x) {
        int k = kGuess(seed, r, x) + 1;
        while (true) {
            if (sideX(seed, r, k) <= x && sideKind(seed, r, k) != NONE) {
                return k;
            }
            k--;
        }
    }

    /** Centre z of main street r at x: its base line shifted by -3..3 per piece. */
    private int streetZ(long seed, int r, int x) {
        int k = leftSide(seed, r, x);
        return streetZ0(seed, r) + Hash.range(Hash.of(seed, r, k, 906), 7) - 3;
    }

    /** Where side street k of row r starts: the far edge of the higher main-street piece it joins. */
    private int sideTop(long seed, int r, int sx) {
        return Math.min(streetZ(seed, r, sx - 1), streetZ(seed, r, sx)) - roadHalf(seed, r);
    }

    private boolean isRoundabout(long seed, int r, int k) {
        if (sideKind(seed, r, k) != FULL || Hash.unit(seed, r, k, 905) >= 0.22) {
            return false;
        }
        int sx = sideX(seed, r, k);
        int k2 = kGuess(seed, r + 1, sx);
        for (int kk = k2 - 1; kk <= k2 + 2; kk++) {
            if (sideKind(seed, r + 1, kk) != NONE && Math.abs(sideX(seed, r + 1, kk) - sx) < RB_CLEAR) {
                return false;
            }
        }
        return true;
    }

    /** The row of blocks (between main streets r and r + 1) that z falls in at x. */
    private int blockRow(long seed, int x, int z) {
        int r = Math.floorDiv(z + ROW / 2, ROW);
        while (z < streetZ(seed, r, x)) {
            r--;
        }
        while (z >= streetZ(seed, r + 1, x)) {
            r++;
        }
        return r;
    }

    // ---- The exit ------------------------------------------------------------------------------

    private int exitX(long seed, int x, int z) {
        return exits().exitX(seed, Math.floorDiv(x, EXIT_REGION), Math.floorDiv(z, EXIT_REGION));
    }

    /**
     * Inside the exit structure? Returns the centre z of the underpass street, or Integer.MIN_VALUE.
     * The embankment covers the lots on both sides of the street over the underpass length.
     */
    private int exitStreetZ(long seed, int x, int z) {
        int rx = Math.floorDiv(x, EXIT_REGION);
        int rz = Math.floorDiv(z, EXIT_REGION);
        int ex = exits().exitX(seed, rx, rz);
        if (Math.abs(x - ex) > EXIT_HALF) {
            return Integer.MIN_VALUE;
        }
        int street = exitStreet(seed, exits().exitZ(seed, rx, rz));
        int cz = streetZ(seed, street, ex);
        int reach = roadHalf(seed, street) + SW + 1 + LD;
        return Math.abs(z - cz) <= reach ? cz : Integer.MIN_VALUE;
    }

    // ---- Classifying a column ---------------------------------------------------------------

    /** What column (x, z) is: road, sidewalk, island, underpass, embankment or OTHER. */
    int classify(long seed, int x, int z) {
        if (Level9Sites.inField(x)) {
            return FIELD; // the Crimson Field: no streets or houses out there
        }
        int cz = exitStreetZ(seed, x, z);
        if (cz != Integer.MIN_VALUE) {
            int street = exitStreet(seed, exits().exitZ(seed, Math.floorDiv(x, EXIT_REGION), Math.floorDiv(z, EXIT_REGION)));
            int rh = roadHalf(seed, street);
            int d = Math.abs(z - cz);
            if (d <= rh) return TUNNEL_ROAD;
            if (d == rh + 1) return TUNNEL_CURB;
            if (d <= rh + SW) return TUNNEL_WALK;
            return EMBANKMENT;
        }
        int r = blockRow(seed, x, z);
        boolean road = false;
        boolean side = false;
        boolean curb = false;
        int island = -1;
        // Main streets.
        for (int rr = r - 1; rr <= r + 1; rr++) {
            int d = Math.abs(z - streetZ(seed, rr, x));
            int rh = roadHalf(seed, rr);
            if (d <= rh) {
                road = true;
            } else if (d <= rh + SW) {
                side = true;
                curb |= d == rh + 1;
            }
        }
        // Side streets, cul-de-sacs and roundabouts of the three rows around.
        for (int rr = r - 1; rr <= r + 1; rr++) {
            int k0 = kGuess(seed, rr, x);
            for (int k = k0 - 1; k <= k0 + 2; k++) {
                int sx = sideX(seed, rr, k);
                if (Math.abs(x - sx) > RB_R + SW + 1) {
                    continue;
                }
                int kind = sideKind(seed, rr, k);
                if (kind == NONE) {
                    continue;
                }
                int top = sideTop(seed, rr, sx);
                int bottom = kind == FULL ? streetZ(seed, rr + 1, sx) : top + STUB_LEN;
                int dx = Math.abs(x - sx);
                if (z >= top && z <= bottom && dx <= SIDE_HALF) {
                    road = true;
                } else if (z >= top - SW && z <= bottom && dx <= SIDE_HALF + SW) {
                    side = true;
                    curb |= dx == SIDE_HALF + 1;
                }
                double dist = Math.hypot(x - sx, z - bottom);
                if (kind == STUB) {
                    if (dist <= BULB_R + 0.5) {
                        road = true;
                    } else if (dist <= BULB_R + SW + 0.5) {
                        side = true;
                        curb |= dist <= BULB_R + 1.5;
                    }
                } else if (isRoundabout(seed, rr, k)) {
                    if (dist <= ISLAND_R - 0.5) {
                        island = ISLAND;
                    } else if (dist <= ISLAND_R + 0.5) {
                        if (island < 0) island = ISLAND_CURB;
                    } else if (dist <= RB_R + 0.5) {
                        road = true;
                    } else if (dist <= RB_R + SW + 0.5) {
                        side = true;
                        curb |= dist <= RB_R + 1.5;
                    }
                }
            }
        }
        if (island >= 0) return island;
        if (road) return ROAD;
        if (curb) return CURB;
        if (side) return SIDEWALK;
        return OTHER;
    }

    // ==========================================================================================
    // Lots and houses
    // ==========================================================================================

    private static int lotPhase(long seed, int r, int side) {
        return Hash.range(Hash.of(seed, r, side, 910), LW);
    }

    /**
     * A lot gets a house unless it would touch a side street, a jog, a roundabout or the exit, or
     * a grass walkway runs across it (then it stays an empty lot with the walkway on it).
     */
    private boolean lotValid(long seed, int r, int side, int idx) {
        return lotValidBase(seed, r, side, idx) && !plan.isWalkwayLot(seed, r, side, idx);
    }

    /** x of lot idx's first column (u = 0). */
    int lotX0(long seed, int r, int side, int idx) {
        return idx * LW + lotPhase(seed, r, side);
    }

    /**
     * z of the lot's front row (v = 0, next to the sidewalk). Lots never straddle a jog of their
     * street, so this holds for the whole lot.
     */
    int lotFrontZ(long seed, int r, int side, int x0) {
        if (side == SOUTH_OF_STREET) {
            return streetZ(seed, r, x0) + roadHalf(seed, r) + SW + 1;
        }
        return streetZ(seed, r + 1, x0) - roadHalf(seed, r + 1) - SW - 1;
    }

    /** How deep the woods between the two rows of lots of block row r are at x (blocks). */
    int woodsDepth(long seed, int r, int x) {
        int front = streetZ(seed, r, x) + roadHalf(seed, r) + SW + 1;
        int back = streetZ(seed, r + 1, x) - roadHalf(seed, r + 1) - SW - 1;
        return back - front + 1 - 2 * LD;
    }

    /** The plain lot rule (the anomaly plan's own checks use this, so they never ask themselves). */
    boolean lotValidBase(long seed, int r, int side, int idx) {
        int x0 = idx * LW + lotPhase(seed, r, side);
        int x1 = x0 + LW - 1;
        int k0 = kGuess(seed, r, x0);
        for (int k = k0 - 1; k <= k0 + 2; k++) {
            int kind = sideKind(seed, r, k);
            if (kind == NONE) continue;
            int sx = sideX(seed, r, k);
            int m = kind == STUB ? STUB_MARGIN : VERT_MARGIN;
            if (sx >= x0 - m && sx <= x1 + m) return false;
        }
        if (side != SOUTH_OF_STREET) {
            // Lots north of main street r + 1: its jogs happen at the side streets of row r + 1.
            int k1 = kGuess(seed, r + 1, x0);
            for (int k = k1 - 1; k <= k1 + 2; k++) {
                if (sideKind(seed, r + 1, k) == NONE) continue;
                int sx = sideX(seed, r + 1, k);
                if (sx >= x0 - JOG_MARGIN && sx <= x1 + JOG_MARGIN) return false;
            }
        }
        int rbRow = side == SOUTH_OF_STREET ? r - 1 : r;
        int k2 = kGuess(seed, rbRow, x0);
        for (int k = k2 - 1; k <= k2 + 2; k++) {
            int sx = sideX(seed, rbRow, k);
            if (sx >= x0 - RB_LOT_MARGIN && sx <= x1 + RB_LOT_MARGIN && isRoundabout(seed, rbRow, k)) return false;
        }
        int street = side == SOUTH_OF_STREET ? r : r + 1;
        int mid = (x0 + x1) / 2;
        int rx = Math.floorDiv(mid, EXIT_REGION);
        int rz = Math.floorDiv(streetZ0(seed, street), EXIT_REGION);
        int ex = exits().exitX(seed, rx, rz);
        if (exitStreet(seed, exits().exitZ(seed, rx, rz)) == street && x0 - 2 <= ex + EXIT_HALF && x1 + 2 >= ex - EXIT_HALF) {
            return false;
        }
        return true;
    }

    /**
     * Where (x, z) lies among the lots of its block, packed: side (bit 0), u (bits 1-8), v (bits
     * 9-16), lot index (bits 17-40, offset), row (bits 41-63, offset). Returns -1 in the woods.
     */
    long lotInfo(long seed, int x, int z) {
        int r = blockRow(seed, x, z);
        int front = streetZ(seed, r, x) + roadHalf(seed, r) + SW + 1;
        int back = streetZ(seed, r + 1, x) - roadHalf(seed, r + 1) - SW - 1;
        int side;
        int v;
        if (z - front < LD) {
            side = SOUTH_OF_STREET;
            v = Math.max(0, z - front);
        } else if (back - z < LD) {
            side = 1;
            v = Math.max(0, back - z);
        } else {
            return -1;
        }
        int phase = lotPhase(seed, r, side);
        int idx = Math.floorDiv(x - phase, LW);
        int u = x - (idx * LW + phase);
        return side | ((long) u << 1) | ((long) v << 9) | ((long) (idx + (1 << 23)) << 17) | ((long) (r + (1 << 21)) << 41);
    }

    static int infoSide(long info) { return (int) (info & 1); }
    static int infoU(long info) { return (int) ((info >> 1) & 0xFF); }
    static int infoV(long info) { return (int) ((info >> 9) & 0xFF); }
    static int infoIdx(long info) { return (int) ((info >> 17) & 0xFFFFFF) - (1 << 23); }
    static int infoRow(long info) { return (int) (info >>> 41) - (1 << 21); }

    /** The house standing on the lot that contains (x, z), with (u, v) filled in, or null. */
    @Nullable
    private House houseAt(long seed, int x, int z) {
        long info = lotInfo(seed, x, z);
        if (info < 0) return null;
        int r = infoRow(info);
        int side = infoSide(info);
        int idx = infoIdx(info);
        if (!lotValid(seed, r, side, idx)) return null;
        House h = houseOf(seed, r, side, idx);
        h.u = infoU(info);
        h.v = infoV(info);
        return h;
    }

    /**
     * The house of a valid lot. The first time a house is looked at on this thread, also works
     * out whether it is a safe house; if so it gets its porch light switched on and its door
     * unlocked (the House object is cached, so this happens once).
     */
    private House houseOf(long seed, int r, int side, int idx) {
        House h = House.of(seed, r, side, idx);
        if (!h.safeKnown) {
            h.safeKnown = true;
            // Twin and clipped houses are dressed by the anomaly plan and are never safe houses.
            boolean odd = plan.dressHouse(seed, r, side, idx, h);
            if (!odd && h.livingRoomFits() && !h.glitch && safeHousePicked(seed, r, side, idx)) {
                h.safe = true;
                h.unlocked = true;
                h.porchLight = true;
            }
        }
        return h;
    }

    /**
     * True when the safe-house point of one of the 96x96 squares falls on lot (r, side, idx).
     * Lots never straddle a jog of their street (lotValid keeps them away from side streets),
     * so the lot is a plain LW x LD rectangle.
     */
    boolean safeHousePicked(long seed, int r, int side, int idx) {
        int x0 = idx * LW + lotPhase(seed, r, side);
        int z0;
        if (side == SOUTH_OF_STREET) {
            z0 = streetZ(seed, r, x0) + roadHalf(seed, r) + SW + 1;        // the lot's front edge
        } else {
            z0 = streetZ(seed, r + 1, x0) - roadHalf(seed, r + 1) - SW - LD; // its back edge (the woods side)
        }
        for (int cx = Math.floorDiv(x0, SAFE_CELL); cx <= Math.floorDiv(x0 + LW - 1, SAFE_CELL); cx++) {
            for (int cz = Math.floorDiv(z0, SAFE_CELL); cz <= Math.floorDiv(z0 + LD - 1, SAFE_CELL); cz++) {
                long h = SafeRoomPlan.cellHash(seed, cx, cz, 440);
                int px = cx * SAFE_CELL + Hash.range(Hash.mix(h ^ 1), SAFE_CELL);
                int pz = cz * SAFE_CELL + Hash.range(Hash.mix(h ^ 2), SAFE_CELL);
                if (px >= x0 && px < x0 + LW && pz >= z0 && pz < z0 + LD) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True for woods: deep in the middle of a block, or the back of an empty lot. */
    private boolean isWoods(long seed, int x, int z) {
        if (classify(seed, x, z) != OTHER) return false;
        if (plan.blocksTrees(seed, x, z)) return false; // keep trunks off a grass walkway and its clearing
        long info = lotInfo(seed, x, z);
        if (info < 0) return true;
        return infoV(info) >= EMPTY_LAWN && !lotValid(seed, infoRow(info), infoSide(info), infoIdx(info));
    }

    /**
     * One house on one lot, in lot coordinates: u runs along the street (0..17, with x), v away
     * from it (0 = next to the sidewalk). All numbers come from the lot's hash.
     */
    static final class House {
        int storeys;
        boolean garage;
        int width;
        int depth;
        int hu0;
        int hu1;
        int gu0;
        int garageDepth;
        /** Distance from the sidewalk to the front wall. */
        int setback;
        int colour;
        /** True: the roof ridge runs along u (gables on the side walls). */
        boolean ridgeAlongU;
        int doorU;
        int porchU0;
        int porchU1;
        boolean unlocked;
        /** Level 94: about 1 in 40 enterable houses has a moulded clay front door that leads there. */
        boolean clayDoor;
        boolean porchLight;
        boolean frontFence;
        /** 0 none, 1 hedge, 2 picket fence. */
        int sideFence;
        int backFence;
        boolean glitch;
        /** One of a pair of identical twin houses (Level9AnomalyPlan): back door, front and back hall. */
        boolean twin;
        /**
         * Clipped houses: the two houses grown into each other (drawn in canonical lot coordinates,
         * mirrored when clipMirror), and the lot column u of the second house's front path.
         */
        @Nullable
        House clipA;
        @Nullable
        House clipB;
        boolean clipMirror;
        int clipDoorBU = Integer.MIN_VALUE;
        boolean crate;
        /** Someone's backpack lies on the porch / on the driveway; it faces backpackFacing. */
        boolean porchBackpack;
        boolean drivewayBackpack;
        Direction backpackFacing;
        /** A safe house (see safeHousePicked); only valid once safeKnown is true (set by houseOf). */
        boolean safe;
        boolean safeKnown;
        int roofBase;
        /** Interior partition column, or Integer.MIN_VALUE for none. */
        int partitionU;
        /** World direction of +v (away from the street) and of the street. */
        Direction inward;
        Direction front;
        // Filled in by houseAt for the column being built.
        int u;
        int v;

        private static final ThreadLocal<House> LAST = new ThreadLocal<>();
        private long key = Long.MIN_VALUE;
        private long keySeed;

        /**
         * The house of lot (r, side, idx). Neighbouring columns usually belong to the same lot,
         * so the last one built on this thread is reused instead of recomputed.
         */
        static House of(long seed, int r, int side, int idx) {
            long key = ((long) r << 33) ^ ((long) side << 32) ^ (idx & 0xFFFFFFFFL);
            House h = LAST.get();
            if (h != null && h.key == key && h.keySeed == seed) {
                return h;
            }
            h = new House();
            h.build(seed, r, side, idx);
            h.key = key;
            h.keySeed = seed;
            LAST.set(h);
            return h;
        }

        private void build(long seed, int r, int side, int idx) {
            int k = r * 2 + side;
            storeys = Hash.unit(seed, idx, k, 940) < 0.45 ? 2 : 1;
            garage = Hash.unit(seed, idx, k, 941) < 0.5;
            width = 9 + Hash.range(Hash.of(seed, idx, k, 942), 5);
            depth = 8 + Hash.range(Hash.of(seed, idx, k, 943), 4);
            if (garage && width + 5 > 16) width = 11;
            int total = width + (garage ? 5 : 0);
            int free = 17 - total; // columns 1..17 are usable (0 is the boundary fence)
            int u0 = 1 + Hash.range(Hash.of(seed, idx, k, 944), free + 1);
            if (garage && Hash.unit(seed, idx, k, 945) < 0.5) {
                gu0 = u0;
                hu0 = u0 + 5;
            } else {
                hu0 = u0;
                gu0 = u0 + width;
            }
            hu1 = hu0 + width - 1;
            setback = 5 + Hash.range(Hash.of(seed, idx, k, 946), 3);
            garageDepth = 6 + Hash.range(Hash.of(seed, idx, k, 947), 2);
            colour = Hash.range(Hash.of(seed, idx, k, 948), 5);
            boolean longerAlongU = width >= depth;
            ridgeAlongU = Hash.unit(seed, idx, k, 949) < 0.7 ? longerAlongU : !longerAlongU;
            if ((ridgeAlongU ? depth : width) + 2 > 13) ridgeAlongU = !ridgeAlongU; // keep roofs low enough
            doorU = hu0 + 2 + Hash.range(Hash.of(seed, idx, k, 950), width - 4);
            int porchWidth = 3 + Hash.range(Hash.of(seed, idx, k, 951), 3);
            porchU0 = Math.min(Math.max(doorU - porchWidth / 2, hu0), hu1 - porchWidth + 1);
            porchU1 = porchU0 + porchWidth - 1;
            unlocked = Hash.unit(seed, idx, k, 952) < 0.30;
            porchLight = Hash.unit(seed, idx, k, 953) < 0.20;
            frontFence = Hash.unit(seed, idx, k, 954) < 0.35;
            double sk = Hash.unit(seed, idx, k, 955);
            sideFence = sk < 0.4 ? 1 : sk < 0.7 ? 2 : 0;
            backFence = Hash.unit(seed, idx, k, 956) < 0.6 ? 2 : 1;
            glitch = !unlocked && Hash.unit(seed, idx, k, 957) < 0.04;
            crate = Hash.unit(seed, idx, k, 958) < 0.3;
            porchBackpack = Hash.unit(seed, idx, k, 961) < 0.06;
            drivewayBackpack = garage && Hash.unit(seed, idx, k, 962) < 0.08;
            backpackFacing = Direction.from2DDataValue(Hash.range(Hash.of(seed, idx, k, 963), 4));
            clayDoor = unlocked && Hash.unit(seed, idx, k, 964) < 1.0 / 40.0;
            // The top storey's wall plate / ceiling layer; the roof's first stairs sit at this height
            // on the overhang and climb from there, so there is no gap above the walls.
            roofBase = 41 + 5 * storeys;
            // Interior wall: near the middle, never in line with the front door and never in the
            // two columns the staircase needs (stairs along hu1 - 1, walked onto from hu1 - 2).
            partitionU = Integer.MIN_VALUE;
            int mid = hu0 + width / 2;
            for (int off : new int[] {0, -1, 1, -2, 2, -3, 3}) {
                int p = mid + off;
                if (p >= hu0 + 2 && p <= hu1 - 3 && Math.abs(p - doorU) >= 2) {
                    partitionU = p;
                    break;
                }
            }
            inward = side == SOUTH_OF_STREET ? Direction.SOUTH : Direction.NORTH;
            front = inward.getOpposite();
        }

        /** A smaller copy shifted into the first one: the rare "clipped houses" glitch. */
        House ghost() {
            House g = new House();
            g.storeys = storeys;
            g.garage = false;
            g.width = Math.max(7, width - 3);
            g.depth = Math.max(6, depth - 3);
            g.hu0 = hu0 + 2;
            g.hu1 = g.hu0 + g.width - 1;
            g.setback = setback + 2;
            g.colour = (colour + 2) % 5;
            g.ridgeAlongU = !ridgeAlongU;
            g.doorU = Integer.MIN_VALUE;
            g.roofBase = roofBase;
            g.inward = inward;
            g.front = front;
            g.partitionU = Integer.MIN_VALUE;
            return g;
        }

        boolean inHouse(int u, int v) {
            return u >= hu0 && u <= hu1 && v >= setback && v <= setback + depth - 1;
        }

        boolean inGarage(int u, int v) {
            return garage && u >= gu0 && u <= gu0 + 4 && v >= setback && v <= setback + garageDepth - 1;
        }

        /** The house with its roof overhang and porch, or the garage: no tree crowns in here. */
        boolean nearBuilding(int u, int v) {
            if (clipA != null) {
                int cu = clipMirror ? LW - 1 - u : u;
                return clipA.nearBuilding(cu, v) || clipB.nearBuilding(cu, v);
            }
            if (u >= hu0 - 1 && u <= hu1 + 1 && v >= setback - 2 && v <= setback + depth) return true;
            return garage && u >= gu0 - 1 && u <= gu0 + 5 && v >= setback - 1 && v <= setback + garageDepth;
        }

        /** Clipped houses: inside either of the two houses (walls included). Others: house or garage. */
        boolean indoors(int u, int v) {
            if (clipA != null) {
                int cu = clipMirror ? LW - 1 - u : u;
                return clipA.inHouse(cu, v) || clipB.inHouse(cu, v);
            }
            return inHouse(u, v) || inGarage(u, v);
        }

        boolean inPorch(int u, int v) {
            return u >= porchU0 && u <= porchU1 && v >= setback - 2 && v <= setback - 1;
        }

        int stairU() {
            return hu1 - 1;
        }

        /** A living room (left of the interior wall) wide enough for the safe-house furniture. */
        boolean livingRoomFits() {
            return partitionU != Integer.MIN_VALUE && partitionU >= hu0 + 4;
        }

        /** Direction meaning "+u" (east) or "-u" (west) in the world. */
        static Direction alongU(boolean plus) {
            return plus ? Direction.EAST : Direction.WEST;
        }
    }

    // ==========================================================================================
    // Street furniture
    // ==========================================================================================

    /** Streetlamps go dark near the underpass, so the walk into it is pitch black. */
    private boolean lampDarkZone(long seed, int x, int z, int street) {
        int rx = Math.floorDiv(x, EXIT_REGION);
        int rz = Math.floorDiv(z, EXIT_REGION);
        return exitStreet(seed, exits().exitZ(seed, rx, rz)) == street && Math.abs(x - exits().exitX(seed, rx, rz)) <= EXIT_HALF + 12;
    }

    /**
     * If the curb column (x, z) is on the streetlamp spacing pattern, the direction the lamp's arm
     * points (toward the road), else null. Main streets: alternating sides every 21 blocks; side
     * streets the same along z.
     */
    @Nullable
    private Direction poleCandidate(long seed, int x, int z) {
        Direction mouth = mouthLamp(seed, x, z);
        if (mouth != null) {
            return mouth;
        }
        int r = blockRow(seed, x, z);
        for (int rr = r - 1; rr <= r + 1; rr++) {
            int cz = streetZ(seed, rr, x);
            int rh = roadHalf(seed, rr);
            int phase = Hash.range(Hash.of(seed, rr, 0, 965), LAMP_SPACING);
            if (z == cz - rh - 1 && Math.floorMod(x + phase, LAMP_SPACING) == 0 && !lampDarkZone(seed, x, z, rr)) {
                return Direction.SOUTH;
            }
            if (z == cz + rh + 1 && Math.floorMod(x + phase, LAMP_SPACING) == LAMP_SPACING / 2 && !lampDarkZone(seed, x, z, rr)) {
                return Direction.NORTH;
            }
        }
        for (int rr = r - 1; rr <= r + 1; rr++) {
            int k0 = kGuess(seed, rr, x);
            for (int k = k0 - 1; k <= k0 + 2; k++) {
                int sx = sideX(seed, rr, k);
                boolean west = x == sx - SIDE_HALF - 1;
                boolean east = x == sx + SIDE_HALF + 1;
                if (!west && !east) continue;
                int kind = sideKind(seed, rr, k);
                if (kind == NONE) continue;
                int top = sideTop(seed, rr, sx);
                int bottom = kind == FULL ? streetZ(seed, rr + 1, sx) : top + STUB_LEN - 4;
                if (z < top + 9 || z > bottom - 9) continue;
                int phase = Hash.range(Hash.of(seed, rr, k, 966), LAMP_SPACING);
                if (west && Math.floorMod(z + phase, LAMP_SPACING) == 0) return Direction.EAST;
                if (east && Math.floorMod(z + phase, LAMP_SPACING) == LAMP_SPACING / 2) return Direction.WEST;
            }
        }
        return null;
    }

    /**
     * A streetlamp pole stands in curb column (x, z) if it is on the pattern, it really is a curb,
     * and its arm reaches over road for its whole length. Returns the arm direction or null.
     */
    @Nullable
    private Direction poleAt(long seed, int x, int z) {
        Direction d = poleCandidate(seed, x, z);
        if (d == null || classify(seed, x, z) != CURB) return null;
        for (int k = 1; k <= 3; k++) {
            if (classify(seed, x + d.getStepX() * k, z + d.getStepZ() * k) != ROAD) return null;
        }
        return d;
    }

    /**
     * The lamps framing each underpass mouth: just outside it, one on each curb of the road. They
     * always work, so the dark opening can be spotted from far down the street. Returns the arm
     * direction if (x, z) is one of their poles.
     */
    @Nullable
    private Direction mouthLamp(long seed, int x, int z) {
        int ex = exitX(seed, x, z);
        if (Math.abs(x - ex) != MOUTH_LAMP_OFFSET) return null;
        int street = exitStreetOf(seed, x, z);
        int cz = streetZ(seed, street, x);
        int rh = roadHalf(seed, street);
        if (z == cz - rh - 1) return Direction.SOUTH;
        if (z == cz + rh + 1) return Direction.NORTH;
        return null;
    }

    /** 30% of streetlamps are dead, 15% flicker, the rest (55%) work. The underpass lamps always work. */
    private LampMode lampMode(long seed, int x, int z) {
        if (mouthLamp(seed, x, z) != null) return LampMode.LIT;
        double u = Hash.unit(seed, x, z, 970);
        return u < 0.30 ? LampMode.DEAD : u < 0.45 ? LampMode.FLICKER : LampMode.LIT;
    }

    /** About one in six working lamps is an old mercury lamp with a cold green-white light (never at the underpass). */
    private boolean mercury(long seed, int x, int z) {
        return Hash.unit(seed, x, z, 971) < 1.0 / 6.0 && mouthLamp(seed, x, z) == null;
    }

    private boolean litPole(long seed, int x, int z) {
        return lampMode(seed, x, z) == LampMode.LIT && poleAt(seed, x, z) != null;
    }

    /** Arm or lamp head hanging over road column (x, z), or null. */
    @Nullable
    private BlockState lampPart(long seed, int x, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int k = 1; k <= 3; k++) {
                int px = x - d.getStepX() * k;
                int pz = z - d.getStepZ() * k;
                if (poleCandidate(seed, px, pz) != d || poleAt(seed, px, pz) != d) continue;
                if (k < 3) {
                    return ModBlocks.STREETLAMP_ARM.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, d);
                }
                LampMode mode = lampMode(seed, px, pz);
                boolean cold = mode != LampMode.DEAD && mercury(seed, px, pz);
                BlockState head = (cold ? ModBlocks.STREETLAMP_HEAD_MERCURY : ModBlocks.STREETLAMP_HEAD).get().defaultBlockState();
                return head.setValue(BlockStateProperties.HORIZONTAL_FACING, d).setValue(StreetlampHeadBlock.MODE, mode);
            }
        }
        return null;
    }

    // ---- Signs ---------------------------------------------------------------------------------

    /** Reader's left when looking toward {@code looking}. */
    private static Direction leftOf(Direction looking) {
        return looking.getCounterClockWise();
    }

    /**
     * Arrow pointing from (x, z) toward the nearest underpass, for a reader looking toward
     * {@code reading}. The Neighborhood Watch lies about one time in seven.
     */
    private SignKind arrowKind(long seed, int x, int z, Direction reading) {
        int[] exit = exitPosition(seed, x, z);
        int dx = exit[0] - x;
        int dz = exit[1] - z;
        Direction want = Math.abs(dx) >= Math.abs(dz)
                ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        SignKind kind;
        if (want == reading) {
            kind = SignKind.ARROW_AHEAD;
        } else if (want == leftOf(reading)) {
            kind = SignKind.ARROW_LEFT;
        } else if (want == leftOf(reading).getOpposite()) {
            kind = SignKind.ARROW_RIGHT;
        } else {
            // The exit is behind you: turn toward the side it leans to.
            Direction sideways = reading.getAxis() == Direction.Axis.Z
                    ? (dx > 0 ? Direction.EAST : Direction.WEST)
                    : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            kind = sideways == leftOf(reading) ? SignKind.ARROW_LEFT : SignKind.ARROW_RIGHT;
        }
        if (Hash.unit(seed, x, z, 976) < 0.15) {
            SignKind[] arrows = {SignKind.ARROW_LEFT, SignKind.ARROW_RIGHT, SignKind.ARROW_AHEAD};
            kind = arrows[Hash.range(Hash.of(seed, x, z, 977), 3)];
        }
        return kind;
    }

    /**
     * An arrow sign on lawn column (x, z)? They stand at the corner where a side street meets a
     * main street (facing people walking up or down the side street) and on roundabout islands.
     */
    @Nullable
    private BlockState junctionSign(long seed, int x, int z) {
        int r = blockRow(seed, x, z);
        for (int rr = r - 1; rr <= r + 1; rr++) {
            int k0 = kGuess(seed, rr, x);
            for (int k = k0 - 1; k <= k0 + 2; k++) {
                int sx = sideX(seed, rr, k);
                if (Math.abs(x - sx) > SIDE_HALF + SW + 1) continue;
                int kind = sideKind(seed, rr, k);
                if (kind == NONE) continue;
                int tx = sx + SIDE_HALF + SW + 1;
                if (x == tx && z == streetZ(seed, rr, tx) + roadHalf(seed, rr) + SW + 1) {
                    return sign(Direction.SOUTH, arrowKind(seed, x, z, Direction.NORTH));
                }
                if (kind != FULL) continue;
                int bottom = streetZ(seed, rr + 1, sx);
                if (isRoundabout(seed, rr, k)) {
                    if (x == sx && z == bottom - 2) {
                        return sign(Direction.NORTH, arrowKind(seed, x, z, Direction.SOUTH));
                    }
                } else {
                    int bx = sx - SIDE_HALF - SW - 1;
                    if (x == bx && z == streetZ(seed, rr + 1, bx) - roadHalf(seed, rr + 1) - SW - 1) {
                        return sign(Direction.NORTH, arrowKind(seed, x, z, Direction.SOUTH));
                    }
                }
            }
        }
        return null;
    }

    private static final SignKind[] NOTICES = {
            SignKind.WATCH_NOTICE, SignKind.WATCH_NOTICE, SignKind.QUIET_NOTICE,
            SignKind.QUIET_NOTICE, SignKind.WARNING, SignKind.NONSENSE};

    /** Neighborhood Watch notices, warnings and nonsense signs in front of empty lots. */
    @Nullable
    private static BlockState notice(long seed, int x, int z, long info) {
        if (infoV(info) != 0 || Math.floorMod(x + 3 * infoRow(info), 11) != 0 || Hash.unit(seed, x, z, 975) >= 0.3) {
            return null;
        }
        Direction facing = infoSide(info) == SOUTH_OF_STREET ? Direction.NORTH : Direction.SOUTH;
        return sign(facing, NOTICES[Hash.range(Hash.of(seed, x, z, 978), NOTICES.length)]);
    }

    private static BlockState sign(Direction facing, SignKind kind) {
        return ModBlocks.STREET_SIGN.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(StreetSignBlock.KIND, kind);
    }

    // ---- Road surface ----------------------------------------------------------------------

    /** Round puddles of wet asphalt. */
    private static boolean puddle(long seed, int x, int z) {
        int px = Math.floorDiv(x, 9);
        int pz = Math.floorDiv(z, 9);
        long h = Hash.of(seed, px, pz, 990);
        if (Hash.unit(h) >= 0.3) return false;
        int ox = x - (px * 9 + 2 + Hash.range(Hash.mix(h), 5));
        int oz = z - (pz * 9 + 2 + Hash.range(Hash.mix(h ^ 7), 5));
        return ox * ox + oz * oz <= 2 + Hash.range(Hash.mix(h ^ 9), 6);
    }

    /**
     * Faded road paint that makes no sense: dashes in the wrong lane, a solid line off centre,
     * crosswalks to nowhere, stray stripes. Returns the stripe axis or null.
     */
    @Nullable
    private Direction.Axis marking(long seed, int x, int z) {
        int r = blockRow(seed, x, z);
        for (int rr = r; rr <= r + 1; rr++) {
            int cz = streetZ(seed, rr, x);
            if (Math.abs(z - cz) > roadHalf(seed, rr)) continue;
            int k = leftSide(seed, rr, x);
            int style = Hash.range(Hash.of(seed, rr, k, 985), 4);
            int phase = Hash.range(Hash.of(seed, rr, k, 986), 7);
            if (style == 1 && z == cz && Math.floorMod(x + phase, 7) < 3) return Direction.Axis.X;
            if (style == 2 && z == cz + 1 && Math.floorMod(x + phase, 5) < 2) return Direction.Axis.X;
            if (style == 3 && z == cz - 1) return Direction.Axis.X;
            if (Hash.unit(seed, rr, k, 987) < 0.4) {
                int crosswalk = sideX(seed, rr, k) + 10 + Hash.range(Hash.of(seed, rr, k, 988), 40);
                if (x >= crosswalk && x < crosswalk + 4 && Math.floorMod(z - cz, 2) == 0) return Direction.Axis.X;
            }
        }
        if (Hash.unit(seed, x, z, 989) < 0.004) {
            return Hash.unit(seed, x, z, 984) < 0.5 ? Direction.Axis.X : Direction.Axis.Z;
        }
        return null;
    }

    /** A road column right next to the curb (the gutter, where leaves pile up). */
    private boolean isGutter(long seed, int x, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int c = classify(seed, x + d.getStepX(), z + d.getStepZ());
            if (c == CURB || c == SIDEWALK) return true;
        }
        return false;
    }

    // ==========================================================================================
    // Blocks
    // ==========================================================================================

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        if (Level9Sites.inField(x)) {
            plan.fillField(seed, x, z, out); // Level 9.1, the Crimson Field
            return;
        }
        int c = classify(seed, x, z);
        switch (c) {
            case ROAD, TUNNEL_ROAD -> fillRoad(seed, x, z, c, out);
            case SIDEWALK, CURB, TUNNEL_WALK, TUNNEL_CURB, ISLAND_CURB -> fillSidewalk(seed, x, z, c, out);
            case EMBANKMENT -> fillEmbankment(seed, x, z, out);
            case ISLAND -> {
                lawn(out);
                fillIsland(seed, x, z, out);
            }
            default -> fillLand(seed, x, z, out);
        }
    }

    private void fillRoad(long seed, int x, int z, int c, Column out) {
        BlockState surface;
        if (c == TUNNEL_ROAD) {
            fillTunnelRoad(seed, x, z, out);
            return;
        }
        boolean gutter = isGutter(seed, x, z);
        Direction.Axis paint;
        if (gutter && Hash.unit(seed, x, z, 981) < 0.04) {
            surface = ModBlocks.STORM_DRAIN.get().defaultBlockState();
        } else if ((paint = marking(seed, x, z)) != null) {
            surface = ModBlocks.ASPHALT_MARKING.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, paint);
        } else if (puddle(seed, x, z)) {
            surface = ModBlocks.ASPHALT_WET.get().defaultBlockState();
        } else if (Hash.unit(seed, x, z, 982) < 0.003) {
            surface = ModBlocks.MANHOLE.get().defaultBlockState();
        } else {
            surface = ModBlocks.ASPHALT.get().defaultBlockState();
        }
        out.set(FLOOR_Y, surface);
        if (gutter && Hash.unit(seed, x, z, 980) < 0.35) {
            out.set(FLOOR_Y + 1, ModBlocks.LEAF_LITTER.get().defaultBlockState()); // dead leaves along the curb
        }
        BlockState lamp = lampPart(seed, x, z);
        if (lamp != null) {
            out.set(LAMP_Y, lamp);
        }
        // Crowns of street trees reach over the road edge (never into a lamp).
        applyTrees(seed, x, z, lamp == null, out);
    }

    private void fillSidewalk(long seed, int x, int z, int c, Column out) {
        boolean curb = c == CURB || c == TUNNEL_CURB || c == ISLAND_CURB;
        out.set(FLOOR_Y, ModBlocks.SIDEWALK_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
        out.set(FLOOR_Y + 1, (curb ? ModBlocks.SIDEWALK_CURB : ModBlocks.SIDEWALK_SLAB).get().defaultBlockState());
        boolean pole = c == CURB && poleAt(seed, x, z) != null;
        if (pole) {
            BlockState poleBlock = ModBlocks.STREETLAMP_POLE.get().defaultBlockState();
            for (int y = FLOOR_Y + 1; y <= LAMP_Y; y++) {
                out.set(y, poleBlock);
            }
        }
        if (c == CURB || c == SIDEWALK) {
            applyTrees(seed, x, z, !pole, out);
        }
        if (c == TUNNEL_WALK || c == TUNNEL_CURB) {
            tunnelRoof(seed, x, z, out);
        }
    }

    // ---- The exit underpass --------------------------------------------------------------------

    /** The main street that runs through the underpass of the region containing (x, z). */
    private int exitStreetOf(long seed, int x, int z) {
        return exitStreet(seed, exits().exitZ(seed, Math.floorDiv(x, EXIT_REGION), Math.floorDiv(z, EXIT_REGION)));
    }

    /**
     * The road inside the underpass: a line of drain grates along the north gutter, a faded
     * dashed centre line, damp patches, leaves blown in near the mouths, worn concrete where the
     * exit is, and one sick mercury light flickering under the roof on the way in.
     */
    private void fillTunnelRoad(long seed, int x, int z, Column out) {
        int ex = exitX(seed, x, z);
        int cz = exitStreetZ(seed, x, z);
        int rh = roadHalf(seed, exitStreetOf(seed, x, z));
        int fromMiddle = Math.abs(x - ex);
        BlockState surface;
        if (z == cz - rh) {
            surface = ModBlocks.STORM_DRAIN.get().defaultBlockState();
        } else if (fromMiddle <= EXIT_TRIGGER) {
            surface = ModBlocks.EXIT_TUNNEL_MARKER.get().defaultBlockState();
        } else if (z == cz && Math.floorMod(x, 4) < 2) {
            surface = ModBlocks.ASPHALT_MARKING.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.X);
        } else if (puddle(seed, x, z)) {
            surface = ModBlocks.ASPHALT_WET.get().defaultBlockState(); // water seeping through
        } else {
            surface = ModBlocks.ASPHALT.get().defaultBlockState();
        }
        out.set(FLOOR_Y, surface);
        if (fromMiddle >= EXIT_HALF - 3 && Hash.unit(seed, x, z, 979) < 0.2) {
            out.set(FLOOR_Y + 1, ModBlocks.LEAF_LITTER.get().defaultBlockState());
        }
        if (x == ex - TUNNEL_LIGHT_OFFSET && z == cz) {
            out.set(LAMP_Y, ModBlocks.STREETLAMP_HEAD_MERCURY.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                    .setValue(StreetlampHeadBlock.MODE, LampMode.FLICKER));
        }
        tunnelRoof(seed, x, z, out);
    }

    /**
     * The underpass roof (y48-51) with the deck parapet on its outer edges, and the big green EXIT
     * sign set into the concrete face above each mouth: 4 tiles wide, 2 high, centred on the road.
     */
    private void tunnelRoof(long seed, int x, int z, Column out) {
        BlockState concrete = ModBlocks.TUNNEL_CONCRETE.get().defaultBlockState();
        for (int y = TUNNEL_CEILING_Y; y <= DECK_TOP_Y; y++) {
            out.set(y, concrete);
        }
        int ex = exitX(seed, x, z);
        if (Math.abs(x - ex) == EXIT_HALF) {
            out.set(DECK_TOP_Y + 1, concrete); // parapet
            int cz = exitStreetZ(seed, x, z);
            if (z >= cz - 2 && z <= cz + 1) {
                boolean west = x < ex;
                // Column 0 is the reader's left: north on the west face, south on the east face.
                int column = west ? z - (cz - 2) : (cz + 1) - z;
                BlockState sign = ModBlocks.HIGHWAY_SIGN_EXIT.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, west ? Direction.WEST : Direction.EAST);
                out.set(SIGN_LOW_Y + 1, sign.setValue(HighwaySignBlock.PART, column));
                out.set(SIGN_LOW_Y, sign.setValue(HighwaySignBlock.PART, HighwaySignBlock.COLUMNS + column));
            }
        }
    }

    /**
     * The highway embankment: solid concrete up to the deck (y51), a parapet along both edges, and
     * two highway lamps up on the deck standing out against the fog: one lit, one flickering.
     */
    private void fillEmbankment(long seed, int x, int z, Column out) {
        BlockState concrete = ModBlocks.TUNNEL_CONCRETE.get().defaultBlockState();
        for (int y = FLOOR_Y; y <= DECK_TOP_Y; y++) {
            out.set(y, concrete);
        }
        int ex = exitX(seed, x, z);
        if (Math.abs(x - ex) == EXIT_HALF) {
            out.set(DECK_TOP_Y + 1, concrete); // parapet
        }
        int cz = exitStreetZ(seed, x, z);
        // West deck lamp: pole near the west parapet north of the road, arm reaching east.
        deckLamp(x, z, ex - EXIT_HALF + 1, cz - DECK_LAMP_OFFSET, Direction.EAST, LampMode.LIT, out);
        // East deck lamp: near the east parapet south of the road, arm reaching west.
        deckLamp(x, z, ex + EXIT_HALF - 1, cz + DECK_LAMP_OFFSET, Direction.WEST, LampMode.FLICKER, out);
    }

    /** Pole, arm and head of a deck lamp whose pole stands at (px, pz), if (x, z) is part of it. */
    private static void deckLamp(int x, int z, int px, int pz, Direction arm, LampMode mode, Column out) {
        if (z != pz) return;
        int k = (x - px) * arm.getStepX();
        if (k < 0 || k > 3 || x != px + arm.getStepX() * k) return;
        int top = DECK_LAMP_Y;
        if (k == 0) {
            BlockState pole = ModBlocks.STREETLAMP_POLE.get().defaultBlockState();
            for (int y = DECK_TOP_Y + 1; y <= top; y++) out.set(y, pole);
        } else if (k < 3) {
            out.set(top, ModBlocks.STREETLAMP_ARM.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, arm));
        } else {
            out.set(top, ModBlocks.STREETLAMP_HEAD.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, arm)
                    .setValue(StreetlampHeadBlock.MODE, mode));
        }
    }

    static void lawn(Column out) {
        BlockState grass = ModBlocks.LAWN_GRASS.get().defaultBlockState();
        out.set(FLOOR_Y, grass);
        out.set(FLOOR_Y + 1, grass);
    }

    /** Roundabout island: a dry fountain or a grass circle with one dead tree, plus its sign. */
    private void fillIsland(long seed, int x, int z, Column out) {
        int r = blockRow(seed, x, z);
        for (int rr = r - 1; rr <= r; rr++) {
            int k0 = kGuess(seed, rr, x);
            for (int k = k0 - 1; k <= k0 + 2; k++) {
                int sx = sideX(seed, rr, k);
                if (Math.abs(x - sx) > ISLAND_R + 1 || !isRoundabout(seed, rr, k)) continue;
                int cz = streetZ(seed, rr + 1, sx);
                double d = Math.hypot(x - sx, z - cz);
                if (d > ISLAND_R + 0.5) continue;
                BlockState concrete = ModBlocks.TUNNEL_CONCRETE.get().defaultBlockState();
                if (Hash.unit(seed, rr, k, 907) < 0.5) {
                    // Dry fountain: a pedestal in a basin; the rim is a half slab you can step on.
                    if (d <= 1.5) {
                        for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 4; y++) out.set(y, concrete);
                    } else if (d <= 2.5) {
                        out.set(FLOOR_Y + 1, concrete);
                        out.set(FLOOR_Y + 2, ModBlocks.LEAF_LITTER.get().defaultBlockState());
                    } else if (d <= 3.5) {
                        out.set(FLOOR_Y + 1, concrete);
                        out.set(FLOOR_Y + 2, ModBlocks.SIDEWALK_SLAB.get().defaultBlockState());
                    }
                } else if (x == sx && z == cz) {
                    BlockState log = ModBlocks.NIGHT_LOG.get().defaultBlockState();
                    for (int y = FLOOR_Y + 2; y <= FLOOR_Y + 7; y++) out.set(y, log);
                }
                BlockState sign = junctionSign(seed, x, z);
                if (sign != null) {
                    out.set(FLOOR_Y + 2, ModBlocks.SIGN_POST.get().defaultBlockState());
                    out.set(FLOOR_Y + 3, sign);
                }
                return;
            }
        }
    }

    /** Lots, empty lots and woods. */
    private void fillLand(long seed, int x, int z, Column out) {
        lawn(out);
        // A grass walkway (its lot and the path through the woods) is drawn by the plan.
        if (plan.fillWalkway(seed, x, z, out)) {
            applyTrees(seed, x, z, !plan.inWalkwayClearing(seed, x, z), out);
            return;
        }
        long info = lotInfo(seed, x, z);
        if (info >= 0) {
            int r = infoRow(info);
            int side = infoSide(info);
            int idx = infoIdx(info);
            if (lotValid(seed, r, side, idx)) {
                House h = houseOf(seed, r, side, idx);
                int u = infoU(info);
                int v = infoV(info);
                if (h.clipA != null) {
                    plan.fillClipped(seed, x, z, h, u, v, out); // two houses grown into each other
                    applyTrees(seed, x, z, !h.nearBuilding(u, v), out);
                    return;
                }
                yard(seed, x, z, h, u, v, out);
                house(h, u, v, out, false);
                // A slab roof over the porch, unless the house roof itself already sits at that height.
                boolean roofAt46 = roofAt(h, u, v) != null && h.roofBase + roofLayer(h, u, v) == 46;
                if (h.inPorch(u, v) && !roofAt46) {
                    out.set(46, slab(ModBlocks.ROOF_SHINGLES_SLAB.get().defaultBlockState()));
                }
                if (h.glitch) {
                    house(h.ghost(), u, v, out, true);
                }
                applyTrees(seed, x, z, !h.nearBuilding(u, v), out);
                return;
            }
        }
        BlockState sign = junctionSign(seed, x, z);
        if (sign == null && info >= 0) {
            sign = notice(seed, x, z, info);
        }
        if (sign != null) {
            out.set(FLOOR_Y + 2, ModBlocks.SIGN_POST.get().defaultBlockState());
            out.set(FLOOR_Y + 3, sign);
            applyTrees(seed, x, z, true, out);
            return;
        }
        boolean trunkHere = applyTrees(seed, x, z, true, out);
        if (!trunkHere && Hash.unit(seed, x, z, 991) < 0.25) {
            out.set(FLOOR_Y + 2, ModBlocks.LEAF_LITTER.get().defaultBlockState());
        }
    }

    static BlockState slab(BlockState state) {
        return state.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    static BlockState facing(BlockState state, Direction d) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
    }

    // ---- The yard ----------------------------------------------------------------------------

    /** Porch, path, driveway, fences and hedges, mailbox, porch light: everything but the house. */
    private void yard(long seed, int x, int z, House h, int u, int v, Column out) {
        int s = h.setback;
        if (h.inPorch(u, v)) {
            out.set(FLOOR_Y + 1, ModBlocks.PORCH_PLANKS.get().defaultBlockState());
            if (v == s - 2 && (u == h.porchU0 || u == h.porchU1)) {
                for (int y = 42; y <= 45; y++) out.set(y, ModBlocks.PORCH_POST.get().defaultBlockState());
            }
            if (h.porchLight && v == s - 1 && u == h.doorU + 1) {
                out.set(44, facing(ModBlocks.PORCH_LIGHT.get().defaultBlockState(), h.front));
            }
            if (h.safe && v == s - 1 && u == h.doorU) {
                out.set(44, facing(ModBlocks.SAFE_ROOM_SIGN.get().defaultBlockState(), h.front)); // over the front door
            }
            if (h.porchBackpack && v == s - 1 && u == h.porchU0) {
                out.set(42, facing(ModBlocks.BACKPACK.get().defaultBlockState(), h.backpackFacing));
            }
            return;
        }
        BlockState concrete = ModBlocks.SIDEWALK_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        if (v <= s - 3 && (u == h.doorU || u == h.doorU + 1)) {
            out.set(FLOOR_Y + 1, concrete); // path to the porch
            return;
        }
        if (h.garage && v <= s - 1 && u >= h.gu0 + 1 && u <= h.gu0 + 3) {
            out.set(FLOOR_Y + 1, concrete); // driveway
            if (h.drivewayBackpack && v == s - 2 && u == h.gu0 + 1) {
                out.set(42, facing(ModBlocks.BACKPACK.get().defaultBlockState(), h.backpackFacing));
            }
            return;
        }
        int fence = fenceKind(h, u, v);
        if (fence == 1) {
            out.set(42, ModBlocks.HEDGE.get().defaultBlockState());
            out.set(43, ModBlocks.HEDGE.get().defaultBlockState());
        } else if (fence == 2) {
            out.set(42, picket(seed, x, z));
        } else if (u == h.doorU - 1 && v == (h.frontFence ? 1 : 0)) {
            out.set(42, facing(ModBlocks.MAILBOX.get().defaultBlockState(), h.front));
        }
    }

    /** 0 none, 1 hedge, 2 picket fence on lot column (u, v). */
    static int fenceKind(House h, int u, int v) {
        if (v == LD - 1) return h.backFence;
        if (u == 0 && v >= h.setback) return h.sideFence;
        if (h.frontFence && v == 0) {
            if (u == h.doorU || u == h.doorU + 1) return 0;
            if (h.garage && u >= h.gu0 + 1 && u <= h.gu0 + 3) return 0;
            return 2;
        }
        return 0;
    }

    /** A picket fence piece, joined to the fence pieces beside it. */
    BlockState picket(long seed, int x, int z) {
        return ModBlocks.PICKET_FENCE.get().defaultBlockState()
                .setValue(FenceBlock.NORTH, isPicket(seed, x, z - 1))
                .setValue(FenceBlock.SOUTH, isPicket(seed, x, z + 1))
                .setValue(FenceBlock.EAST, isPicket(seed, x + 1, z))
                .setValue(FenceBlock.WEST, isPicket(seed, x - 1, z));
    }

    private boolean isPicket(long seed, int x, int z) {
        if (classify(seed, x, z) != OTHER) return false;
        House h = houseAt(seed, x, z);
        return h != null && !h.inPorch(h.u, h.v) && fenceKind(h, h.u, h.v) == 2
                && !(h.v <= h.setback - 3 && (h.u == h.doorU || h.u == h.doorU + 1));
    }

    // ---- The house ---------------------------------------------------------------------------

    static BlockState siding(int colour) {
        return switch (colour) {
            case 0 -> ModBlocks.SIDING_GREEN.get().defaultBlockState();
            case 1 -> ModBlocks.SIDING_GREY.get().defaultBlockState();
            case 2 -> ModBlocks.SIDING_BLUE.get().defaultBlockState();
            case 3 -> ModBlocks.SIDING_YELLOW.get().defaultBlockState();
            default -> ModBlocks.SIDING_WHITE.get().defaultBlockState();
        };
    }

    /**
     * Writes house, garage and roof blocks for lot column (u, v). {@code ghost} draws only the
     * outer shell of the glitch copy over whatever is there.
     */
    private void house(House h, int u, int v, Column out, boolean ghost) {
        BlockState wallBlock = siding(h.colour);
        BlockState trim = ModBlocks.HOUSE_TRIM.get().defaultBlockState();
        int s = h.setback;
        int d = h.depth;
        if (h.inHouse(u, v)) {
            boolean wallU = u == h.hu0 || u == h.hu1;
            boolean wallV = v == s || v == s + d - 1;
            if (wallU || wallV) {
                boolean corner = wallU && wallV;
                out.set(41, ModBlocks.BRICK_FOUNDATION.get().defaultBlockState());
                for (int st = 0; st < h.storeys; st++) {
                    int base = 42 + 5 * st;
                    for (int y = base; y < base + 4; y++) out.set(y, corner ? trim : wallBlock);
                    if (!corner && windowHere(h, u, v)) {
                        BlockState window = facing(ModBlocks.HOUSE_WINDOW.get().defaultBlockState(), outward(h, u, v));
                        out.set(base + 1, window);
                        out.set(base + 2, window);
                    }
                    if (h.twin && st == 0 && v == s + d - 1 && u == h.doorU) {
                        // Twin houses also have a back door, straight behind the front one.
                        BlockState back = ModBlocks.HOUSE_DOOR.get().defaultBlockState()
                                .setValue(DoorBlock.FACING, h.inward)
                                .setValue(HouseDoorBlock.LOCKED, false);
                        out.set(42, back.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                        out.set(43, back.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                    }
                    if (st == 0 && v == s && u == h.doorU) {
                        BlockState door = h.clayDoor
                                ? ModBlocks.L94_CLAY_DOOR.get().defaultBlockState().setValue(DoorBlock.FACING, h.inward)
                                : ModBlocks.HOUSE_DOOR.get().defaultBlockState()
                                        .setValue(DoorBlock.FACING, h.inward)
                                        .setValue(HouseDoorBlock.LOCKED, !h.unlocked);
                        out.set(42, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                        out.set(43, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                    }
                    // A white trim band between the storeys; the top one is the wall plate.
                    out.set(base + 4, st < h.storeys - 1 || corner ? trim : wallBlock);
                }
                int gable = gableLayers(h, u, v);
                for (int i = 1; i < gable; i++) out.set(h.roofBase + i, wallBlock);
            } else {
                boolean open = h.unlocked && !ghost;
                out.set(41, (!open || u > h.partitionU ? ModBlocks.WOOD_FLOOR : ModBlocks.BEIGE_CARPET).get().defaultBlockState());
                if (h.storeys == 2) {
                    boolean stairwell = open && u == h.stairU() && v >= s + 1 && v <= s + 4;
                    if (!stairwell) out.set(46, ModBlocks.WOOD_FLOOR.get().defaultBlockState());
                }
                out.set(h.roofBase, trim); // the ceiling
                if (!ghost) {
                    for (int st = 0; st < h.storeys; st++) {
                        for (int y = 42 + 5 * st; y < 46 + 5 * st; y++) out.set(y, AIR);
                    }
                    if (open) interior(h, u, v, out);
                }
            }
        } else if (h.inGarage(u, v) && !ghost) {
            boolean wall = u == h.gu0 || u == h.gu0 + 4 || v == s || v == s + h.garageDepth - 1;
            out.set(41, ModBlocks.SIDEWALK_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
            if (wall) {
                for (int y = 42; y <= 46; y++) out.set(y, wallBlock);
                if (v == s && u >= h.gu0 + 1 && u <= h.gu0 + 3) {
                    BlockState door = facing(ModBlocks.GARAGE_DOOR.get().defaultBlockState(), h.front);
                    out.set(42, door);
                    out.set(43, door);
                }
            }
            out.set(47, slab(ModBlocks.ROOF_SHINGLES_SLAB.get().defaultBlockState()));
        }
        BlockState roof = roofAt(h, u, v);
        if (roof != null) {
            out.set(h.roofBase + roofLayer(h, u, v), roof);
        }
    }

    /** Which way a wall column's window looks: straight out of the house. */
    private static Direction outward(House h, int u, int v) {
        if (v == h.setback) return h.front;
        if (v == h.setback + h.depth - 1) return h.inward;
        return House.alongU(u == h.hu1);
    }

    /** Windows every third column along each wall (never right beside the front door). */
    private static boolean windowHere(House h, int u, int v) {
        int s = h.setback;
        if (v == s || v == s + h.depth - 1) {
            if (v == s && Math.abs(u - h.doorU) <= 1) return false;
            if (h.twin && v == s + h.depth - 1 && Math.abs(u - h.doorU) <= 1) return false; // the back door
            int w = u - h.hu0;
            return w % 3 == 2 && w > 0 && w < h.width - 1;
        }
        int w = v - s;
        return w % 3 == 2 && w > 0 && w < h.depth - 1;
    }

    /**
     * The gable roof. The roof is made of stair blocks in layers: layer i sits at roofBase + i,
     * one block further in from each eave (eaves overhang the walls by one). Where the two slopes
     * meet in one column there is a ridge slab. Returns null where the roof doesn't cover.
     */
    @Nullable
    static BlockState roofAt(House h, int u, int v) {
        int a;
        int b;
        int pos;
        if (h.ridgeAlongU) {
            if (u < h.hu0 - 1 || u > h.hu1 + 1) return null;
            a = h.setback - 1;
            b = h.setback + h.depth;
            pos = v;
        } else {
            if (v < h.setback - 1 || v > h.setback + h.depth) return null;
            a = h.hu0 - 1;
            b = h.hu1 + 1;
            pos = u;
        }
        if (pos < a || pos > b) return null;
        int fromStart = pos - a;
        int fromEnd = b - pos;
        if (fromStart == fromEnd) {
            return slab(ModBlocks.ROOF_SHINGLES_SLAB.get().defaultBlockState());
        }
        // Stairs climb toward the ridge.
        boolean risingPositive = fromStart < fromEnd;
        Direction climb = h.ridgeAlongU ? (risingPositive ? h.inward : h.front) : House.alongU(risingPositive);
        return ModBlocks.ROOF_SHINGLES_STAIRS.get().defaultBlockState().setValue(StairBlock.FACING, climb);
    }

    static int roofLayer(House h, int u, int v) {
        if (h.ridgeAlongU) {
            return Math.min(v - (h.setback - 1), h.setback + h.depth - v);
        }
        return Math.min(u - (h.hu0 - 1), h.hu1 + 1 - u);
    }

    /** On a gable-end wall, how many layers above the wall plate are still wall (the triangle). */
    static int gableLayers(House h, int u, int v) {
        if (h.ridgeAlongU) {
            if (u != h.hu0 && u != h.hu1) return 0;
            return Math.min(v - (h.setback - 1), h.setback + h.depth - v);
        }
        if (v != h.setback && v != h.setback + h.depth - 1) return 0;
        return Math.min(u - (h.hu0 - 1), h.hu1 + 1 - u);
    }

    /**
     * Inside an unlocked house: an interior wall with a doorway, stairs up (2 storeys), and a few
     * pieces of furniture standing against the walls, facing into the room.
     */
    private void interior(House h, int u, int v, Column out) {
        if (h.twin) {
            Level9AnomalyPlan.twinInterior(h, u, v, out); // halls front and back, and no upstairs
            return;
        }
        int s = h.setback;
        int d = h.depth;
        int back = s + d - 2;
        BlockState paper = ModBlocks.DIM_WALLPAPER.get().defaultBlockState();
        if (u == h.partitionU && v >= s + 1 && v <= s + d - 2) {
            int doorway = s + d / 2;
            for (int y = 42; y <= 45; y++) {
                if (!(v == doorway && y <= 43)) out.set(y, paper);
            }
            return;
        }
        if (h.storeys == 2 && u == h.stairU() && v >= s + 1 && v <= s + 5) {
            int step = v - (s + 1);
            for (int y = 42; y < 42 + step; y++) out.set(y, paper); // solid under the stairs
            out.set(42 + step, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, h.inward));
            return;
        }
        if (h.storeys == 2 && u == h.stairU() - 1 && v >= s + 1 && v <= s + 4) {
            out.set(47, ModBlocks.PORCH_POST.get().defaultBlockState()); // banister by the stairwell
        }
        if (h.safe) {
            safeHouseExtras(h, u, v, out);
        }
        BlockState piece = h.safe ? safeHousePiece(h, u, v) : null;
        int pu = h.partitionU;
        int tvU = h.hu0 + 1 != h.doorU ? h.hu0 + 1 : h.hu0 + 2;
        int kitchenEnd = Math.min(pu + 3, h.hu1 - (h.storeys == 2 ? 3 : 1));
        if (piece != null) {
            // a safe-house piece already stands here
        } else if (v == back && (u == h.hu0 + 1 || u == h.hu0 + 2)) {
            piece = facing(ModBlocks.SOFA.get().defaultBlockState(), h.front);
        } else if (v == s + 1 && u == tvU && tvU < pu) {
            piece = facing(ModBlocks.TV.get().defaultBlockState(), h.inward);
        } else if (v == back && u >= pu + 1 && u < kitchenEnd) {
            piece = facing(ModBlocks.KITCHEN_COUNTER.get().defaultBlockState(), h.front);
        } else if (v == back && u == kitchenEnd && u > pu) {
            piece = facing(ModBlocks.FRIDGE.get().defaultBlockState(), h.front);
        } else if (v == s + d / 2 - 1 && u == pu + 2 && u < h.hu1 && !(h.storeys == 2 && u == h.stairU())) {
            piece = facing(ModBlocks.DINING_TABLE.get().defaultBlockState(), h.front);
        } else if (h.crate && v == s + 1 && u == pu + 1 && u != h.doorU && !(h.storeys == 2 && u >= h.stairU() - 1)) {
            // (never in the column you step onto the stairs from)
            piece = ModBlocks.SUPPLY_CRATE.get().defaultBlockState();
        }
        if (piece != null) out.set(42, piece);
        if (h.storeys == 2 && u == h.hu0 + 2 && (v == back - 1 || v == back)) {
            out.set(47, facing(ModBlocks.BED.get().defaultBlockState(), h.inward).setValue(HouseBedBlock.HEAD, v == back));
        }
    }

    /**
     * Safe-house furniture in the living room (left of the interior wall), or null: the checkpoint
     * radio against the side wall facing into the room (the spot in front of it stays free), a
     * supply crate by the TV, and a made cot along the interior wall.
     */
    @Nullable
    private static BlockState safeHousePiece(House h, int u, int v) {
        int s = h.setback;
        int back = s + h.depth - 2;
        if (u == h.hu0 + 1 && v == s + h.depth / 2) {
            return facing(ModBlocks.CHECKPOINT_RADIO.get().defaultBlockState(), House.alongU(true));
        }
        if (u == h.hu0 + 1 && v == s + 2) {
            return ModBlocks.SUPPLY_CRATE.get().defaultBlockState();
        }
        if (u == h.partitionU - 1 && (v == back - 1 || v == back)) {
            // HouseBedBlock: FACING points from the foot to the head (at the back wall).
            return facing(ModBlocks.BED.get().defaultBlockState(), h.inward).setValue(HouseBedBlock.HEAD, v == back);
        }
        return null;
    }

    /** The safe house's warm ceiling lamp over the living room, and the SAFE sign inside the front door. */
    private static void safeHouseExtras(House h, int u, int v, Column out) {
        int s = h.setback;
        if (u == (h.hu0 + h.partitionU) / 2 && v == s + (h.depth - 1) / 2) {
            out.set(46, ModBlocks.SAFE_ROOM_LAMP.get().defaultBlockState()); // the ground floor's ceiling layer
        }
        if (u == h.doorU && v == s + 1) {
            out.set(44, facing(ModBlocks.SAFE_ROOM_SIGN.get().defaultBlockState(), h.inward)); // above the door, inside
        }
    }

    // ---- Trees -------------------------------------------------------------------------------

    private static final int CONIFER = 0;
    private static final int DECIDUOUS = 1;
    private static final int DEAD = 2;
    /** Street trees stand on the lawn verge, on a 12-block pattern along each side of a street. */
    private static final int STREET_TREE_SPACING = 12;
    /** No tree reaches further than this from its trunk (crowns, limbs, branches). */
    private static final int TREE_REACH = 6;

    /**
     * One tree, everything about it worked out from its trunk position: kind, height, and the
     * pieces of its crown. Built once and cached, because every column near it needs it again.
     */
    private static final class Tree {
        int tx;
        int tz;
        int kind;
        /** Big deciduous trees stand on a 2x2 trunk (tx..tx+1, tz..tz+1). */
        boolean big;
        int trunkTop;
        /** Conifers: the tip, where the needles start, and the widest needle ring. */
        int top;
        int needlesFrom;
        double maxRadius;
        /** Deciduous: log limbs angling out, and the leaf blobs of the crown. */
        int[] limbX = new int[0];
        int[] limbZ = new int[0];
        int[] limbY = new int[0];
        double[] blobX = new double[0];
        double[] blobZ = new double[0];
        double[] blobY = new double[0];
        double[] blobR = new double[0];
        /** Dead trees: the bare branch network (dead_branch pieces and which sides they join). */
        int[] branchX = new int[0];
        int[] branchZ = new int[0];
        int[] branchY = new int[0];
        int[] branchJoins = new int[0];

        boolean isTrunk(int x, int z) {
            int size = big ? 2 : 1;
            return x >= tx && x < tx + size && z >= tz && z < tz + size;
        }
    }

    /** Marks "no tree here" in the caches. */
    private static final Tree NO_TREE = new Tree();
    private static final ThreadLocal<TreeCache> TREE_CACHE = ThreadLocal.withInitial(TreeCache::new);

    /** Per-thread tree cache; neighbouring columns keep asking about the same few trees. */
    private static final class TreeCache {
        final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Tree> trees = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        /** Reused list of the trees near the column being built (avoids a new array per column). */
        final Tree[] near = new Tree[9 + 2 * 2 * (2 * TREE_REACH + 1)];
        long seed;

        Tree get(long seed, long key) {
            if (this.seed != seed || trees.size() > 4096) {
                trees.clear();
                this.seed = seed;
            }
            return trees.get(key);
        }
    }

    private static final Direction[] BRANCH_DIRS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    /** Builds the shape of a tree with its trunk at (tx, tz). {@code street} faces street trees' limbs toward the road. */
    private static Tree makeTree(long seed, int tx, int tz, int kind, boolean big, @Nullable Direction street) {
        Tree t = new Tree();
        t.tx = tx;
        t.tz = tz;
        t.kind = kind;
        t.big = big;
        long h = Hash.of(seed, tx, tz, 995);
        if (kind == CONIFER) {
            int height = 10 + Hash.range(h, 7);
            t.top = 42 + height - 1;
            t.trunkTop = t.top - 1;
            t.needlesFrom = 44 + Hash.range(Hash.mix(h), 2); // 2-3 blocks up: you walk under them
            t.maxRadius = 2.6 + 0.9 * Hash.unit(Hash.mix(h ^ 1));
        } else if (kind == DECIDUOUS) {
            int height = 7 + Hash.range(h, 6);
            t.trunkTop = 42 + height - 3;
            int n = 2 + Hash.range(Hash.mix(h), 3);
            double cx = tx + 0.5 + (big ? 0.5 : 0.0);
            double cz = tz + 0.5 + (big ? 0.5 : 0.0);
            double base;
            double spread;
            if (street == null) {
                base = Hash.unit(Hash.mix(h ^ 2)) * 2 * Math.PI;
                spread = 2 * Math.PI / n;
            } else {
                base = Math.atan2(street.getStepZ(), street.getStepX()); // lean out over the road
                spread = 0.9;
            }
            int limbCount = 0;
            int[] lengths = new int[n];
            for (int i = 0; i < n; i++) {
                lengths[i] = 2 + Hash.range(Hash.mix(h + 10 + i), 2);
                limbCount += lengths[i];
            }
            t.limbX = new int[limbCount];
            t.limbZ = new int[limbCount];
            t.limbY = new int[limbCount];
            t.blobX = new double[n + 1];
            t.blobZ = new double[n + 1];
            t.blobY = new double[n + 1];
            t.blobR = new double[n + 1];
            int li = 0;
            for (int i = 0; i < n; i++) {
                double angle = base + (i - (n - 1) / 2.0) * spread;
                int length = lengths[i];
                int startY = Math.min(45 + (i % 2), t.trunkTop);
                double ca = Math.cos(angle);
                double sa = Math.sin(angle);
                for (int k = 1; k <= length; k++) {
                    t.limbX[li] = (int) Math.floor(cx + ca * k);
                    t.limbZ[li] = (int) Math.floor(cz + sa * k);
                    t.limbY[li] = startY + (k == length ? 1 : 0);
                    li++;
                }
                t.blobX[i] = cx + ca * length;
                t.blobZ[i] = cz + sa * length;
                t.blobY[i] = startY + 2.0;
                t.blobR[i] = 2.2 + 0.8 * Hash.unit(Hash.mix(h + 20 + i));
            }
            t.blobX[n] = cx;
            t.blobZ[n] = cz;
            t.blobY[n] = t.trunkTop + 1.5;
            t.blobR[n] = 2.5 + 0.5 * Hash.unit(Hash.mix(h ^ 3));
        } else {
            int height = 6 + Hash.range(h, 5);
            t.trunkTop = 42 + height - 2;
            int n = 2 + Hash.range(Hash.mix(h), 2);
            int[] bx = new int[16]; // up to 3 branches of 3 pieces + a 2-piece fork each
            int[] bz = new int[16];
            int[] by = new int[16];
            int[] bj = new int[16];
            int count = 0;
            for (int i = 0; i < n; i++) {
                Direction d = BRANCH_DIRS[Hash.range(Hash.mix(h + 30 + i), 4)];
                int y = 45 + Hash.range(Hash.mix(h + 40 + i), Math.max(1, t.trunkTop - 45 + 1));
                int length = 2 + Hash.range(Hash.mix(h + 50 + i), 2);
                Direction fork = Hash.unit(Hash.mix(h + 60 + i)) < 0.5 ? d.getCounterClockWise() : d.getClockWise();
                int forkLength = 1 + Hash.range(Hash.mix(h + 70 + i), 2);
                // The main branch: each piece joins the one toward the trunk and the next one out.
                for (int k = 1; k <= length; k++) {
                    bx[count] = tx + d.getStepX() * k;
                    bz[count] = tz + d.getStepZ() * k;
                    by[count] = y;
                    bj[count] = joinBit(d.getOpposite()) | joinBit(k < length ? d : fork);
                    count++;
                }
                // A fork turning off at its end.
                int ex = tx + d.getStepX() * length;
                int ez = tz + d.getStepZ() * length;
                for (int j = 1; j <= forkLength; j++) {
                    bx[count] = ex + fork.getStepX() * j;
                    bz[count] = ez + fork.getStepZ() * j;
                    by[count] = y;
                    bj[count] = joinBit(fork.getOpposite()) | (j < forkLength ? joinBit(fork) : 0);
                    count++;
                }
            }
            t.branchX = java.util.Arrays.copyOf(bx, count);
            t.branchZ = java.util.Arrays.copyOf(bz, count);
            t.branchY = java.util.Arrays.copyOf(by, count);
            t.branchJoins = java.util.Arrays.copyOf(bj, count);
        }
        return t;
    }

    /** The forest tree of tree-grid cell (cx, cz), or null. Mix: 45% pines, 35% leafy trees, 20% dead. */
    @Nullable
    private Tree woodsTree(long seed, int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        TreeCache cache = TREE_CACHE.get();
        Tree t = cache.get(seed, key);
        if (t == null) {
            t = buildWoodsTree(seed, cx, cz);
            cache.trees.put(key, t == null ? NO_TREE : t);
        }
        return t == NO_TREE ? null : t;
    }

    @Nullable
    private Tree buildWoodsTree(long seed, int cx, int cz) {
        long h = Hash.of(seed, cx, cz, 960);
        if (Hash.unit(h) >= 0.7) return null;
        int tx = cx * TREE_CELL + 1 + Hash.range(Hash.mix(h), TREE_CELL - 2);
        int tz = cz * TREE_CELL + 1 + Hash.range(Hash.mix(h ^ 0x51ED), TREE_CELL - 2);
        double u = Hash.unit(Hash.mix(h ^ 0xBEEF));
        int kind = u < 0.45 ? CONIFER : u < 0.80 ? DECIDUOUS : DEAD;
        boolean big = kind == DECIDUOUS && Hash.unit(Hash.mix(h ^ 0xFACE)) < 0.3;
        int size = big ? 2 : 1;
        for (int ox = 0; ox < size; ox++) {
            for (int oz = 0; oz < size; oz++) {
                if (!isWoods(seed, tx + ox, tz + oz)) return null;
            }
        }
        // Keep clear of houses so a crown is never sliced flat against a wall.
        if (!canopyAllowed(seed, tx + 4, tz) || !canopyAllowed(seed, tx - 4, tz)
                || !canopyAllowed(seed, tx, tz + 4) || !canopyAllowed(seed, tx, tz - 4)) {
            return null;
        }
        return makeTree(seed, tx, tz, kind, big, null);
    }

    /**
     * A street tree on the lawn verge beside main street {@code street}: side 0 north of it,
     * side 1 south of it, trunk at x = {@code tx}, one block in from the sidewalk. Mostly leafy
     * trees leaning over the road, some dead. Never on a path, driveway or by the mailbox.
     */
    @Nullable
    private Tree streetTree(long seed, int street, int side, int tx) {
        int phase = Hash.range(Hash.of(seed, street, 0, 992), STREET_TREE_SPACING);
        if (Math.floorMod(tx + phase, STREET_TREE_SPACING) != (side == 0 ? 0 : STREET_TREE_SPACING / 2)) return null;
        if (Hash.unit(seed, tx, street * 2 + side, 993) >= 0.45) return null;
        long key = ((long) street << 33) ^ ((long) side << 32) ^ (tx & 0xFFFFFFFFL) ^ Long.MIN_VALUE;
        TreeCache cache = TREE_CACHE.get();
        Tree t = cache.get(seed, key);
        if (t == null) {
            t = buildStreetTree(seed, street, side, tx);
            cache.trees.put(key, t == null ? NO_TREE : t);
        }
        return t == NO_TREE ? null : t;
    }

    @Nullable
    private Tree buildStreetTree(long seed, int street, int side, int tx) {
        int cz = streetZ(seed, street, tx);
        int rh = roadHalf(seed, street);
        int tz = side == 0 ? cz - rh - SW - 2 : cz + rh + SW + 2;
        if (classify(seed, tx, tz) != OTHER) return null;
        long info = lotInfo(seed, tx, tz);
        if (info < 0 || infoV(info) != 1) return null; // only where the verge is a plain straight strip
        if (plan.blocksTrees(seed, tx, tz)) return null; // never across the mouth of a grass walkway
        if (lotValid(seed, infoRow(info), infoSide(info), infoIdx(info))) {
            House h = houseOf(seed, infoRow(info), infoSide(info), infoIdx(info));
            int u = infoU(info);
            if (u == 0 || (u >= h.doorU - 1 && u <= h.doorU + 1)) return null; // path and mailbox
            if (h.clipA != null && Math.abs(u - h.clipDoorBU) <= 1) return null; // the second house's path
            if (h.garage && u >= h.gu0 && u <= h.gu0 + 4) return null; // driveway
        }
        int inward = side == 1 ? 1 : -1;
        if (!canopyAllowed(seed, tx, tz + 3 * inward)) return null;
        int kind = Hash.unit(seed, tx, street * 2 + side, 994) < 0.75 ? DECIDUOUS : DEAD;
        return makeTree(seed, tx, tz, kind, false, side == 0 ? Direction.SOUTH : Direction.NORTH);
    }

    /** May leaves and branches go in column (x, z)? Not into houses, lamps, the underpass or islands. */
    private boolean canopyAllowed(long seed, int x, int z) {
        int c = classify(seed, x, z);
        switch (c) {
            case ROAD:
                return lampPart(seed, x, z) == null;
            case CURB:
                return poleAt(seed, x, z) == null;
            case SIDEWALK:
                return true;
            case OTHER: {
                House h = houseAt(seed, x, z);
                return (h == null || !h.nearBuilding(h.u, h.v)) && !plan.inWalkwayClearing(seed, x, z);
            }
            default:
                return false;
        }
    }

    /**
     * Adds the trees around column (x, z): forest trees from the nearby tree-grid cells and street
     * trees from the verges of the nearest main streets. {@code canopyHere} says whether crowns may
     * reach into this column. Needles first, then wood (limbs, branches), then trunks, so
     * overlapping trees always come out the same. Returns true if a trunk stands here.
     */
    private boolean applyTrees(long seed, int x, int z, boolean canopyHere, Column out) {
        Tree[] near = TREE_CACHE.get().near;
        int count = 0;
        int cx = Math.floorDiv(x, TREE_CELL);
        int cz = Math.floorDiv(z, TREE_CELL);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                Tree t = woodsTree(seed, cx + ox, cz + oz);
                if (t != null) near[count++] = t;
            }
        }
        int r = blockRow(seed, x, z);
        for (int street = r; street <= r + 1; street++) {
            for (int side = 0; side <= 1; side++) {
                for (int tx = x - TREE_REACH; tx <= x + TREE_REACH; tx++) {
                    Tree t = streetTree(seed, street, side, tx);
                    if (t != null && Math.abs(t.tz - z) <= TREE_REACH) near[count++] = t;
                }
            }
        }
        if (count == 0) return false;
        BlockState leaves = ModBlocks.NIGHT_LEAVES.get().defaultBlockState();
        BlockState log = ModBlocks.NIGHT_LOG.get().defaultBlockState();
        if (canopyHere) {
            for (int i = 0; i < count; i++) {
                needles(seed, near[i], x, z, leaves, out);
            }
            for (int i = 0; i < count; i++) {
                wood(near[i], x, z, log, out);
            }
        }
        boolean trunk = false;
        for (int i = 0; i < count; i++) {
            Tree t = near[i];
            if (!t.isTrunk(x, z)) continue;
            for (int y = 42; y <= t.trunkTop; y++) out.set(y, log);
            if (t.kind == CONIFER) {
                out.set(t.top, leaves);
                out.set(t.top + 1, leaves);
            } else if (t.kind == DEAD) {
                out.set(t.trunkTop + 1, ModBlocks.DEAD_BRANCH.get().defaultBlockState()); // a bare spike on top
            }
            trunk = true;
        }
        return trunk;
    }

    /** Needles of a pine (a cone of stacked rings) or the lumpy leaf crown of a leafy tree. */
    private static void needles(long seed, Tree t, int x, int z, BlockState leaves, Column out) {
        if (t.kind == CONIFER) {
            int dx = x - t.tx;
            int dz = z - t.tz;
            int d2 = dx * dx + dz * dz;
            for (int y = t.needlesFrom; y <= t.top; y++) {
                double frac = (t.top - y) / (double) Math.max(1, t.top - t.needlesFrom);
                double radius = t.maxRadius * frac + 0.5;
                if ((t.top - y) % 3 == 2) radius -= 0.7; // every third layer tucks in: stacked rings
                if (d2 > radius * radius) continue;
                boolean edge = d2 > (radius - 1) * (radius - 1);
                if (edge && Hash.unit(seed, x * 31 + y, z, 972) < 0.07) continue; // ragged gaps
                out.set(y, leaves);
            }
        } else if (t.kind == DECIDUOUS) {
            for (int b = 0; b < t.blobX.length; b++) {
                double hx = x + 0.5 - t.blobX[b];
                double hz = z + 0.5 - t.blobZ[b];
                double radius = t.blobR[b];
                int topY = (int) (t.blobY[b] + radius) + 1;
                for (int y = 44; y <= topY; y++) {
                    double vy = (y + 0.5 - t.blobY[b]) / 0.7; // blobs are a little flattened
                    if (hx * hx + hz * hz + vy * vy <= radius * radius && Hash.unit(seed, x * 31 + y, z, 996) >= 0.12) {
                        out.set(y, leaves); // (12% holes so the crown isn't a solid lump)
                    }
                }
            }
        }
    }

    /** Limbs of a leafy tree (logs) and the bare branches of a dead one. */
    private static void wood(Tree t, int x, int z, BlockState log, Column out) {
        for (int i = 0; i < t.limbX.length; i++) {
            if (t.limbX[i] == x && t.limbZ[i] == z) out.set(t.limbY[i], log);
        }
        for (int i = 0; i < t.branchX.length; i++) {
            if (t.branchX[i] != x || t.branchZ[i] != z) continue;
            int joins = 0;
            for (int j = 0; j < t.branchX.length; j++) {
                if (t.branchX[j] == x && t.branchZ[j] == z && t.branchY[j] == t.branchY[i]) joins |= t.branchJoins[j];
            }
            BlockState branch = ModBlocks.DEAD_BRANCH.get().defaultBlockState();
            for (Direction d : BRANCH_DIRS) {
                if ((joins & joinBit(d)) != 0) branch = branch.setValue(fenceSide(d), true);
            }
            out.set(t.branchY[i], branch);
        }
    }

    private static int joinBit(Direction d) {
        return switch (d) {
            case NORTH -> 1;
            case SOUTH -> 2;
            case EAST -> 4;
            default -> 8;
        };
    }

    private static net.minecraft.world.level.block.state.properties.BooleanProperty fenceSide(Direction d) {
        return switch (d) {
            case NORTH -> FenceBlock.NORTH;
            case SOUTH -> FenceBlock.SOUTH;
            case EAST -> FenceBlock.EAST;
            default -> FenceBlock.WEST;
        };
    }
}
