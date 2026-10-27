package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;

/**
 * The plan of one Level 39 cell: one town of {@value Level39Layout#TOWN_HALF}-block half size,
 * walled in by the superstructure. Worked out once per cell (and cached), then read back by the
 * tiles ({@link Level39Tile}) and by the queries other code asks (poles, holes, tunnels).
 *
 * <p>What it holds, all in world coordinates:
 * <ul>
 *   <li><b>Paths</b>: north-south and east-west dirt paths on a jittered grid, 56-80 blocks
 *       apart and 4-6 wide, with wobbly edges ({@link #pathAt}). Around the town, in front of
 *       the mill wall, runs a wide gravel apron.</li>
 *   <li><b>Lots</b> in the blocks between the paths, most with a house ({@link Level39HouseSpec}),
 *       their picket fences, trees and bushes. Rarely a lot holds an abandoned lattice telephone
 *       tower or an M.E.G. survey camp, and very rarely a whole block is a park.</li>
 *   <li><b>Utility poles</b> along the paths, every ~28 blocks, linked for the wires.</li>
 *   <li><b>Tunnels</b> under the town: straight flooded runs on a jittered 96-block lattice
 *       ({@link Seg}), domed chambers where three or four meet, dead ends with two white doors,
 *       and the holes and access shafts that connect them to the town.</li>
 * </ul>
 * Everything is a pure function of the seed and the cell, so neighbouring chunks, threads and the
 * client all agree.
 */
final class Level39Cell {
    /** Lots, houses and paths stay inside |local| <= this; beyond it is the gravel apron. */
    static final int APRON = 434;
    /** Tunnel lattice: nodes every 96 blocks, -4..4 each way (never under the superstructure). */
    static final int TUNNEL_STEP = 96;
    static final int TUNNEL_NODES = 9;

    // What the plan knows about a thing standing on the ground (see Thing.kind).
    static final int HOUSE = 0;
    static final int TREE_BARE = 1;
    static final int TREE_LEAFY = 2;
    static final int TOWER = 3;
    static final int PARK = 4;
    static final int CAMP = 5;

    /**
     * Something standing in the town, with the rectangle (inclusive) it may build in. Trees carry
     * their grown shape in {@code parts} (see {@link Level39Trees}); everything else has null.
     */
    record Thing(int kind, int x0, int z0, int x1, int z1, int cx, int cz, int size, int height, long salt,
                 Level39HouseSpec house, int[] parts) {
    }

    /**
     * A straight run along a lot edge: along x when {@code alongX}, at the fixed coordinate. A
     * picket fence, or an overgrown hedge when {@code hedge}.
     */
    record Fence(boolean alongX, int fixed, int from, int to, int gapFrom, int gapTo, long salt, boolean hedge) {
    }

    /** A utility pole; {@code next} is the index of the pole the wires run on to, or -1. */
    record Pole(int x, int z, boolean wiresAlongX, int next) {
    }

    /**
     * One straight tunnel run between two lattice nodes: along x when {@code alongX} (then
     * {@code fixed} is its z), from {@code a} to {@code b} (a < b). The ends reach past the nodes
     * by {@link #extA}/{@link #extB}: 3 blocks into a bend or junction, or the dry platform and
     * door wall of a dead end ({@code deadA}/{@code deadB}).
     */
    record Seg(boolean alongX, int fixed, int a, int b, boolean deadA, boolean deadB, boolean stagnant, long salt) {
    }

    /** A tunnel lattice node used by at least one run; {@code degree} = how many runs meet there. */
    record Node(int x, int z, int degree) {
    }

    /**
     * A hole from the town into a tunnel run: centred on the run, about 4-6 wide, ragged. Its
     * outline is a wobbly circle ({@link #holeRadius}); the shaft narrows and bulges with depth.
     */
    record Hole(int x, int z, float radius, int seg, float phase1, float phase2, float phase3) {
    }

    /** How far the hole reaches from its centre toward (x, z) at height y (blocks). */
    static float holeRadius(Hole hole, int x, int z, int y) {
        double a = Math.atan2(z - hole.z(), x - hole.x());
        double r = hole.radius() * (1 + 0.22 * Math.sin(3 * a + hole.phase1()) + 0.12 * Math.sin(5 * a + hole.phase2()));
        // The shaft bulges and pinches a little with depth (soft soil caved in more).
        r += 0.45 * Math.sin(y * 0.55 + hole.phase3() + a);
        return (float) r;
    }

    /** An access shaft: rungs up the wall from the rim to a hatch in a town path. */
    record Shaft(int x, int z, Direction ladderFacing, int seg) {
    }

    final long seed;
    final int cellX;
    final int cellZ;
    /** World coordinates of the town's centre (local 0, 0). */
    final int ox;
    final int oz;

    // ---- Paths (local coordinates): xLine = north-south paths at local x, zLine = east-west at local z.
    final int[] xLine;
    final float[] xHalf;
    final int[] zLine;
    final float[] zHalf;

    final Level39HouseSpec[] houses;
    final Thing[] things;
    final Fence[] fences;
    final Pole[] poles;
    /** Bushes: x, z, height (1 or 2) per bush. */
    final int[] bushes;
    /** Yard clutter: x, z, kind (YARD_*), direction/axis per entry. */
    final int[] yardProps;

    final Seg[] segs;
    final Node[] nodes;
    final Hole[] holes;
    final Shaft[] shafts;

    private Level39Cell(long seed, int cellX, int cellZ) {
        this.seed = seed;
        this.cellX = cellX;
        this.cellZ = cellZ;
        this.ox = cellX * Level39Layout.CELL;
        this.oz = cellZ * Level39Layout.CELL;

        // ---- Paths ---------------------------------------------------------------------------
        List<int[]> xs = lines(seed, cellX, cellZ, 101);
        List<int[]> zs = lines(seed, cellX, cellZ, 202);
        xLine = new int[xs.size()];
        xHalf = new float[xs.size()];
        for (int i = 0; i < xs.size(); i++) {
            xLine[i] = xs.get(i)[0];
            xHalf[i] = xs.get(i)[1] / 2.0f;
        }
        zLine = new int[zs.size()];
        zHalf = new float[zs.size()];
        for (int i = 0; i < zs.size(); i++) {
            zLine[i] = zs.get(i)[0];
            zHalf[i] = zs.get(i)[1] / 2.0f;
        }

        // ---- Tunnels (first: houses keep clear of the holes) ----------------------------------
        List<Seg> segList = new ArrayList<>();
        List<Node> nodeList = new ArrayList<>();
        buildTunnels(segList, nodeList);
        segs = segList.toArray(new Seg[0]);
        nodes = nodeList.toArray(new Node[0]);
        List<Hole> holeList = new ArrayList<>();
        List<Shaft> shaftList = new ArrayList<>();
        placeHolesAndShafts(holeList, shaftList);
        holes = holeList.toArray(new Hole[0]);
        shafts = shaftList.toArray(new Shaft[0]);

        // ---- Blocks, lots, houses and the rest ------------------------------------------------
        List<Level39HouseSpec> houseList = new ArrayList<>();
        List<Thing> thingList = new ArrayList<>();
        List<Fence> fenceList = new ArrayList<>();
        List<int[]> bushList = new ArrayList<>();
        List<int[]> yardList = new ArrayList<>();
        buildBlocks(houseList, thingList, fenceList, bushList, yardList);
        houses = houseList.toArray(new Level39HouseSpec[0]);
        // Houses first in the thing list: when two things claim a column, the earlier one wins.
        List<Thing> ordered = new ArrayList<>();
        for (Thing t : thingList) {
            if (t.kind == HOUSE) ordered.add(t);
        }
        for (Thing t : thingList) {
            if (t.kind != HOUSE) ordered.add(t);
        }
        things = ordered.toArray(new Thing[0]);
        fences = fenceList.toArray(new Fence[0]);
        bushes = new int[bushList.size() * 3];
        for (int i = 0; i < bushList.size(); i++) {
            System.arraycopy(bushList.get(i), 0, bushes, i * 3, 3);
        }
        yardProps = new int[yardList.size() * 4];
        for (int i = 0; i < yardList.size(); i++) {
            System.arraycopy(yardList.get(i), 0, yardProps, i * 4, 4);
        }
        poles = placePoles();
    }

    // ==========================================================================================
    // Cache (like Level94Plan: shared by all world-generation threads and the client)
    // ==========================================================================================

    private static final ConcurrentHashMap<Long, Level39Cell> CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Level39Cell> LAST = new ThreadLocal<>();

    /** The plan of the cell containing (x, z). */
    static Level39Cell of(long seed, int x, int z) {
        return byIndex(seed, cellIndex(x), cellIndex(z));
    }

    static int cellIndex(int v) {
        return Math.floorDiv(v + Level39Layout.CELL / 2, Level39Layout.CELL);
    }

    static Level39Cell byIndex(long seed, int cellX, int cellZ) {
        Level39Cell c = LAST.get();
        if (c != null && c.cellX == cellX && c.cellZ == cellZ && c.seed == seed) {
            return c;
        }
        long key = Hash.of(seed, cellX, cellZ, 3901);
        c = CACHE.get(key);
        if (c == null || c.seed != seed || c.cellX != cellX || c.cellZ != cellZ) {
            if (CACHE.size() > 24) {
                CACHE.clear();
            }
            // Built inside computeIfAbsent so two threads never build the same cell twice.
            c = CACHE.computeIfAbsent(key, k -> new Level39Cell(seed, cellX, cellZ));
        }
        LAST.set(c);
        return c;
    }

    // ==========================================================================================
    // Paths
    // ==========================================================================================

    /**
     * Distance (blocks) from local column (lx, lz) to the centre line of the path it is deepest
     * in. Used for the cart ruts, which run a block or so either side of the middle.
     */
    float pathOffset(int lx, int lz) {
        float best = -99;
        float offset = 99;
        for (int i = 0; i < xLine.length; i++) {
            float off = Math.abs(lx - (xLine[i] + wobble(i, lz)));
            float d = xHalf[i] + widthWobble(i, lz) - off;
            if (d > best) {
                best = d;
                offset = off;
            }
        }
        for (int j = 0; j < zLine.length; j++) {
            int id = 100 + j;
            float off = Math.abs(lz - (zLine[j] + wobble(id, lx)));
            float d = zHalf[j] + widthWobble(id, lx) - off;
            if (d > best) {
                best = d;
                offset = off;
            }
        }
        return offset;
    }

    /**
     * Centre lines of the paths along one axis, {local position, width}: blocks of 56-80 blocks
     * between paths of 4-6, from one side of the town to the other.
     */
    private static List<int[]> lines(long seed, int cellX, int cellZ, int salt) {
        List<int[]> out = new ArrayList<>();
        int p = -APRON + 1;
        for (int k = 0; ; k++) {
            long h = Hash.of(seed, cellX * 64 + k, cellZ, salt);
            int block = 56 + Hash.range(h, 25);
            int width = 4 + Hash.range(Hash.mix(h), 3);
            if (p + block + width + 50 > APRON) {
                // The last block runs on to the apron; split it if it would be far too long.
                int rest = APRON - p;
                if (rest > 96) {
                    out.add(new int[] {p + rest / 2, width});
                }
                break;
            }
            p += block;
            out.add(new int[] {p + width / 2, width});
            p += width;
        }
        return out;
    }

    /** Smooth sideways wobble of path {@code line} at position {@code t} along it (about +-1.5). */
    float wobble(int line, int t) {
        int k = Math.floorDiv(t, 20);
        float f = (t - k * 20) / 20.0f;
        f = f * f * (3 - 2 * f);
        float a = (float) Hash.unit(seed, line, k, 3911) * 3 - 1.5f;
        float b = (float) Hash.unit(seed, line, k + 1, 3911) * 3 - 1.5f;
        return a + (b - a) * f;
    }

    /** Small slow change of a path's width along it (about +-0.6). */
    float widthWobble(int line, int t) {
        int k = Math.floorDiv(t, 13);
        float f = (t - k * 13) / 13.0f;
        float a = (float) Hash.unit(seed, line, k, 3912) * 1.2f - 0.6f;
        float b = (float) Hash.unit(seed, line, k + 1, 3912) * 1.2f - 0.6f;
        return a + (b - a) * f;
    }

    /**
     * How far inside a path (local column lx, lz) is, in blocks: positive inside, negative
     * outside. Edges are irregular: the line wobbles, the width breathes and every column has a
     * bit of its own jitter, so the paths never look ruled.
     */
    float pathDepth(int lx, int lz) {
        float best = -99;
        for (int i = 0; i < xLine.length; i++) {
            float d = xHalf[i] + widthWobble(i, lz) - Math.abs(lx - (xLine[i] + wobble(i, lz)));
            if (d > best) best = d;
        }
        for (int j = 0; j < zLine.length; j++) {
            int id = 100 + j;
            float d = zHalf[j] + widthWobble(id, lx) - Math.abs(lz - (zLine[j] + wobble(id, lx)));
            if (d > best) best = d;
        }
        long h = Hash.of(seed, lx, lz, 3913);
        return best - (float) Hash.unit(h) * 0.7f + 0.35f;
    }

    /** True when local column (lx, lz) is on a path (the apron included). */
    boolean pathAt(int lx, int lz) {
        return Math.max(Math.abs(lx), Math.abs(lz)) >= APRON || pathDepth(lx, lz) > 0;
    }

    // ==========================================================================================
    // Blocks and lots
    // ==========================================================================================

    /** Block edges along one axis: {start, end} (local, inclusive) of each block, inside the paths. */
    private static int[][] blockSpans(int[] line, float[] half) {
        int n = line.length + 1;
        int[][] spans = new int[n][2];
        for (int i = 0; i < n; i++) {
            int lo = i == 0 ? -APRON + 3 : line[i - 1] + (int) Math.ceil(half[i - 1]) + 3;
            int hi = i == n - 1 ? APRON - 3 : line[i] - (int) Math.ceil(half[i]) - 3;
            spans[i][0] = lo;
            spans[i][1] = hi;
        }
        return spans;
    }

    /** Lot frontages along a row: 16-22 blocks, like a real small town's narrow lots. */
    private static final int LOT_MIN = 16;
    private static final int LOT_SPREAD = 7;

    /**
     * Every block between the paths is split into one or two rows of lots back to back, each row
     * facing its own path, and each row into narrow lots side by side, so the houses stand close
     * together and close to the paths with their fences in front (as in the photo).
     */
    private void buildBlocks(List<Level39HouseSpec> houseList, List<Thing> thingList, List<Fence> fenceList,
                             List<int[]> bushList, List<int[]> yardList) {
        int[][] bxs = blockSpans(xLine, xHalf);
        int[][] bzs = blockSpans(zLine, zHalf);
        long cellHash = Hash.of(seed, cellX, cellZ, 3920);
        // Very rare parks: about one town in three has one, never on the outer ring of blocks.
        int parkI = -1;
        int parkJ = -1;
        if (Hash.unit(cellHash) < 0.38 && bxs.length > 2 && bzs.length > 2) {
            parkI = 1 + Hash.range(Hash.mix(cellHash + 1), bxs.length - 2);
            parkJ = 1 + Hash.range(Hash.mix(cellHash + 2), bzs.length - 2);
        }
        int towersLeft = Hash.unit(Hash.mix(cellHash + 3)) < 0.7 ? 1 + Hash.range(Hash.mix(cellHash + 4), 2) : 0;
        int campsLeft = Hash.unit(Hash.mix(cellHash + 7)) < 0.55 ? 1 : 0;

        for (int i = 0; i < bxs.length; i++) {
            for (int j = 0; j < bzs.length; j++) {
                int bx0 = ox + bxs[i][0];
                int bx1 = ox + bxs[i][1];
                int bz0 = oz + bzs[j][0];
                int bz1 = oz + bzs[j][1];
                if (bx1 - bx0 < 20 || bz1 - bz0 < 20) {
                    continue;
                }
                if (i == parkI && j == parkJ) {
                    // Bare trees near the park's four corners (before the park, so they claim their spots).
                    int pcx = (bx0 + bx1) / 2;
                    int pcz = (bz0 + bz1) / 2;
                    int hw = Math.max(6, (bx1 - bx0) / 2 - 6);
                    int hd = Math.max(6, (bz1 - bz0) / 2 - 6);
                    for (int c = 0; c < 4; c++) {
                        int tx = pcx + ((c & 1) == 0 ? -hw : hw);
                        int tz = pcz + ((c & 2) == 0 ? -hd : hd);
                        int[] parts = Level39Trees.grow(Hash.of(seed, tx, tz, 3924), false);
                        int reach = Level39Trees.reach(parts);
                        thingList.add(new Thing(TREE_BARE, tx - reach, tz - reach, tx + reach, tz + reach, tx, tz, reach,
                                Level39Trees.height(parts), Hash.of(seed, tx, tz, 3925), null, parts));
                    }
                    thingList.add(new Thing(PARK, bx0, bz0, bx1, bz1, pcx, pcz,
                            0, 0, Hash.of(seed, bx0, bz0, 3921), null, null));
                    continue;
                }
                boolean longX = bx1 - bx0 >= bz1 - bz0;
                int longLo = longX ? bx0 : bz0;
                int longHi = longX ? bx1 : bz1;
                int shortLo = longX ? bz0 : bx0;
                int shortHi = longX ? bz1 : bx1;
                int shortLen = shortHi - shortLo + 1;
                long bh = Hash.of(seed, bx0, bz0, 3922);
                int rows = shortLen >= 40 ? 2 : 1;
                int split = shortLo + shortLen / 2 + Hash.range(bh, 5) - 2;
                for (int row = 0; row < rows; row++) {
                    int sLo = rows == 1 || row == 0 ? shortLo : split + 1;
                    int sHi = rows == 1 ? shortHi : row == 0 ? split : shortHi;
                    // Row 0 faces the path on its low side (north or west), row 1 the high side.
                    boolean frontLow = rows == 1 ? (bh & 64) == 0 : row == 0;
                    Direction rowFront = longX ? (frontLow ? Direction.NORTH : Direction.SOUTH)
                            : (frontLow ? Direction.WEST : Direction.EAST);
                    int p = longLo;
                    while (p <= longHi) {
                        long lh = Hash.of(seed, p, sLo, 3923);
                        boolean merged = Hash.unit(Hash.mix(lh + 1)) < 0.05;
                        int width = merged ? 2 * LOT_MIN + 2 + Hash.range(lh, 6) : LOT_MIN + Hash.range(lh, LOT_SPREAD);
                        int end = p + width - 1;
                        if (longHi - end < 12) {
                            end = longHi; // the last lot takes what is left
                        }
                        int lx0 = longX ? p : sLo;
                        int lx1 = longX ? end : sHi;
                        int lz0 = longX ? sLo : p;
                        int lz1 = longX ? sHi : end;
                        // End lots sometimes face the side path instead.
                        Direction front = rowFront;
                        if ((p == longLo || end == longHi) && Hash.unit(Hash.mix(lh + 2)) < 0.25) {
                            boolean low = p == longLo;
                            front = longX ? (low ? Direction.WEST : Direction.EAST) : (low ? Direction.NORTH : Direction.SOUTH);
                        }
                        int kind = HOUSE;
                        if (!merged && towersLeft > 0 && Hash.unit(Hash.mix(lh + 3)) < 0.008) {
                            kind = TOWER;
                            towersLeft--;
                        } else if (!merged && campsLeft > 0 && Hash.unit(Hash.mix(lh + 4)) < 0.006) {
                            kind = CAMP;
                            campsLeft--;
                        }
                        lot(lx0, lz0, lx1, lz1, lx0 == bx0, lx1 == bx1, lz0 == bz0, lz1 == bz1, front, kind, merged,
                                houseList, thingList, fenceList, bushList, yardList);
                        p = end + 1;
                    }
                }
            }
        }
    }

    // Footprint sizes per style {front min, front max, depth min, depth max}.
    private static final int[][] STYLE_SIZE = {
            {13, 15, 9, 11},   // colonial saltbox
            {11, 14, 13, 15},  // Victorian
            {11, 14, 13, 16},  // farmhouse with side wing
            {11, 13, 14, 16},  // Craftsman bungalow (deep porch)
            {11, 13, 9, 11},   // Cape Cod
            {18, 22, 9, 11},   // ranch with garage
    };
    private static final int[] STYLE_WEIGHT = {15, 15, 25, 15, 15, 15};
    private static final int[] STYLE_STOREYS = {2, 2, 2, 1, 1, 1};

    private void lot(int lx0, int lz0, int lx1, int lz1, boolean pathW, boolean pathE, boolean pathN, boolean pathS,
                     Direction facing, int kind, boolean merged, List<Level39HouseSpec> houseList, List<Thing> thingList,
                     List<Fence> fenceList, List<int[]> bushList, List<int[]> yardList) {
        long h = Hash.of(seed, lx0, lz0, 3930);
        Level39HouseSpec house = null;
        if (kind == HOUSE && Hash.unit(Hash.mix(h + 1)) >= 0.04) {
            house = house(lx0, lz0, lx1, lz1, facing, merged, h);
        }
        if (house != null && nearHole(house.x0() - 3, house.z0() - 3, house.x1() + 3, house.z1() + 3)) {
            house = null; // the ground here fell into a tunnel: an empty yard round the hole
        }
        if (house != null) {
            houseList.add(house);
            thingList.add(new Thing(HOUSE, house.x0(), house.z0(), house.x1(), house.z1(),
                    (house.x0() + house.x1()) / 2, (house.z0() + house.z1()) / 2, 0, 0, house.hash(), house, null));
        }

        // Rare lots: an abandoned lattice telephone tower or an M.E.G. survey camp.
        if (kind == TOWER) {
            int cx = (lx0 + lx1) / 2;
            int cz = (lz0 + lz1) / 2;
            if (!nearHole(cx - 6, cz - 6, cx + 6, cz + 6)) {
                int height = 24 + Hash.range(Hash.mix(h + 4), 9);
                thingList.add(new Thing(TOWER, cx - 4, cz - 4, cx + 4, cz + 4, cx, cz, 2, height, Hash.mix(h + 5), null, null));
            }
        } else if (kind == CAMP) {
            int cx = (lx0 + lx1) / 2;
            int cz = (lz0 + lz1) / 2;
            if (!nearHole(cx - 8, cz - 8, cx + 8, cz + 8)) {
                thingList.add(new Thing(CAMP, cx - 7, cz - 7, cx + 7, cz + 7, cx, cz, 7, 0, Hash.mix(h + 6), null, null));
            }
        }

        // Trees in the yard (one now and then; empty lots get a little stand of them).
        double treeRoll = Hash.unit(Hash.mix(h + 7));
        int trees = house == null && kind == HOUSE ? 2 + Hash.range(Hash.mix(h + 8), 2) : treeRoll < 0.45 ? 1 : treeRoll < 0.55 ? 2 : 0;
        int placed = 0;
        for (int t = 0; t < trees * 4 && placed < trees; t++) {
            long th = Hash.of(seed, lx0 + t * 31, lz0, 3931);
            int tx = lx0 + 2 + Hash.range(th, Math.max(1, lx1 - lx0 - 3));
            int tz = lz0 + 2 + Hash.range(Hash.mix(th), Math.max(1, lz1 - lz0 - 3));
            boolean leafy = Hash.unit(Hash.mix(th + 1)) < 0.3;
            int[] parts = Level39Trees.grow(Hash.mix(th + 3), leafy);
            int reach = Level39Trees.reach(parts);
            int height = Level39Trees.height(parts);
            int bx0 = tx - reach;
            int bz0 = tz - reach;
            int bx1 = tx + reach;
            int bz1 = tz + reach;
            if (house != null && bx1 >= house.x0() - 1 && bx0 <= house.x1() + 1 && bz1 >= house.z0() - 1 && bz0 <= house.z1() + 1) {
                continue;
            }
            if (nearThing(thingList, bx0, bz0, bx1, bz1) || nearHole(tx - 3, tz - 3, tx + 3, tz + 3)) {
                continue;
            }
            placed++;
            thingList.add(new Thing(leafy ? TREE_LEAFY : TREE_BARE, bx0, bz0, bx1, bz1, tx, tz, reach, height,
                    Hash.mix(th + 4), null, parts));
        }

        if (house != null) {
            houseYard(house, lx0, lz0, lx1, lz1, h, bushList, yardList);
        }

        // Fences and hedges on the lot's edges. Edges shared with the lot to the east or south
        // are drawn by this lot; its west/north edges only when they face a path.
        long fh = Hash.mix(h + 9);
        int doorAlong = house == null ? Integer.MIN_VALUE
                : facing.getAxis() == Direction.Axis.Z ? house.doorX() : house.doorZ();
        if (pathN) edge(true, lz0, lx0, lx1, facing == Direction.NORTH ? doorAlong : Integer.MIN_VALUE, true, fh + 1, fenceList);
        edge(true, lz1, lx0, lx1, facing == Direction.SOUTH ? doorAlong : Integer.MIN_VALUE, pathS, fh + 2, fenceList);
        if (pathW) edge(false, lx0, lz0, lz1, facing == Direction.WEST ? doorAlong : Integer.MIN_VALUE, true, fh + 3, fenceList);
        edge(false, lx1, lz0, lz1, facing == Direction.EAST ? doorAlong : Integer.MIN_VALUE, pathE, fh + 4, fenceList);
    }

    /** A lot edge gets a picket fence, an overgrown hedge, or nothing. */
    private void edge(boolean alongX, int fixed, int from, int to, int door, boolean pathSide, long salt, List<Fence> fenceList) {
        double v = Hash.unit(Hash.mix(salt));
        double fence = pathSide ? 0.6 : 0.45;
        double hedge = fence + (pathSide ? 0.18 : 0.15);
        if (v < hedge) {
            fenceList.add(fence(alongX, fixed, from, to, door, v >= fence, salt));
        }
    }

    /** A fence run; a 2-3 wide gate gap opposite the door when {@code door} is set. */
    private Fence fence(boolean alongX, int fixed, int from, int to, int door, boolean hedge, long salt) {
        int gapFrom = Integer.MIN_VALUE;
        int gapTo = Integer.MIN_VALUE;
        if (door != Integer.MIN_VALUE) {
            gapFrom = door - 1;
            gapTo = door + (Hash.unit(Hash.mix(salt + 77)) < 0.5 ? 1 : 0);
        } else if (Hash.unit(Hash.mix(salt + 78)) < 0.35) {
            // Side gates and holes where a section simply fell.
            int len = to - from + 1;
            int at = from + 2 + Hash.range(Hash.mix(salt + 79), Math.max(1, len - 5));
            gapFrom = at;
            gapTo = at + 1 + Hash.range(Hash.mix(salt + 80), 3);
        }
        return new Fence(alongX, fixed, from, to, gapFrom, gapTo, Hash.mix(salt + 81), hedge);
    }

    // Yard things (Level39Tile prop codes are built from these kinds plus a direction or axis).
    static final int YARD_WOODPILE = 0;
    static final int YARD_BARREL = 1;
    static final int YARD_CLOTHES_POST = 2;
    static final int YARD_CLOTHESLINE = 3;
    static final int YARD_MAILBOX = 4;
    static final int YARD_HANDCART = 5;
    static final int YARD_WHEELBARROW = 6;

    /**
     * Bushes against the walls, and the clutter of a lived-in yard: a mailbox by the gate, a
     * woodpile and a rain barrel against the house, a clothesline out back, now and then an old
     * handcart or a wheelbarrow. Each entry is {x, z, kind, direction or axis (2D data value)}.
     */
    private void houseYard(Level39HouseSpec house, int lx0, int lz0, int lx1, int lz1, long h,
                           List<int[]> bushList, List<int[]> yardList) {
        int count = 2 + Hash.range(Hash.mix(h + 8), 4);
        for (int b = 0; b < count; b++) {
            long bh = Hash.of(seed, house.id(), b, 3932);
            int side = Hash.range(bh, 4);
            int bx;
            int bz;
            if (side < 2) {
                bx = house.x0() + Hash.range(Hash.mix(bh), house.width());
                bz = side == 0 ? house.z0() - 1 : house.z1() + 1;
            } else {
                bz = house.z0() + Hash.range(Hash.mix(bh), house.depth());
                bx = side == 2 ? house.x0() - 1 : house.x1() + 1;
            }
            if (Math.abs(bx - house.doorX()) + Math.abs(bz - house.doorZ()) <= 2) {
                continue; // never in front of the door
            }
            bushList.add(new int[] {bx, bz, Hash.unit(Hash.mix(bh + 1)) < 0.35 ? 2 : 1});
        }
        Direction front = house.facing();
        Direction back = front.getOpposite();
        // Mailbox: on the front fence line, beside the gate.
        if (Hash.unit(Hash.mix(h + 40)) < 0.45) {
            int mx = front.getAxis() == Direction.Axis.Z ? house.doorX() + 2 : (front == Direction.WEST ? lx0 : lx1);
            int mz = front.getAxis() == Direction.Axis.Z ? (front == Direction.NORTH ? lz0 : lz1) : house.doorZ() + 2;
            yardList.add(new int[] {mx, mz, YARD_MAILBOX, front.get2DDataValue()});
        }
        // Woodpile against the back wall.
        if (Hash.unit(Hash.mix(h + 41)) < 0.3) {
            int wx = back.getAxis() == Direction.Axis.Z ? house.x0() + 1 + Hash.range(Hash.mix(h + 42), Math.max(1, house.width() - 2))
                    : (back == Direction.WEST ? house.x0() - 1 : house.x1() + 1);
            int wz = back.getAxis() == Direction.Axis.Z ? (back == Direction.NORTH ? house.z0() - 1 : house.z1() + 1)
                    : house.z0() + 1 + Hash.range(Hash.mix(h + 42), Math.max(1, house.depth() - 2));
            yardList.add(new int[] {wx, wz, YARD_WOODPILE, back.get2DDataValue()});
        }
        // Rain barrel at a back corner.
        if (Hash.unit(Hash.mix(h + 43)) < 0.4) {
            boolean lowSide = (h & 1024) == 0;
            int rx;
            int rz;
            if (back.getAxis() == Direction.Axis.Z) {
                rx = lowSide ? house.x0() - 1 : house.x1() + 1;
                rz = back == Direction.NORTH ? house.z0() : house.z1();
            } else {
                rz = lowSide ? house.z0() - 1 : house.z1() + 1;
                rx = back == Direction.WEST ? house.x0() : house.x1();
            }
            yardList.add(new int[] {rx, rz, YARD_BARREL, 0});
        }
        // Clothesline across the back yard, if there is room.
        int backRoom = switch (back) {
            case NORTH -> house.z0() - lz0;
            case SOUTH -> lz1 - house.z1();
            case WEST -> house.x0() - lx0;
            default -> lx1 - house.x1();
        };
        if (backRoom >= 6 && Hash.unit(Hash.mix(h + 44)) < 0.3) {
            int depthOut = 2 + (backRoom - 2) / 2;
            int len = Math.min(5 + Hash.range(Hash.mix(h + 45), 3), (back.getAxis() == Direction.Axis.Z ? house.width() : house.depth()) - 1);
            boolean lineAlongX = back.getAxis() == Direction.Axis.Z;
            int start = lineAlongX ? house.x0() + 1 : house.z0() + 1;
            int across = switch (back) {
                case NORTH -> house.z0() - depthOut;
                case SOUTH -> house.z1() + depthOut;
                case WEST -> house.x0() - depthOut;
                default -> house.x1() + depthOut;
            };
            for (int t = 0; t <= len; t++) {
                int kind = t == 0 || t == len ? YARD_CLOTHES_POST : YARD_CLOTHESLINE;
                int x = lineAlongX ? start + t : across;
                int z = lineAlongX ? across : start + t;
                yardList.add(new int[] {x, z, kind, lineAlongX ? 0 : 1});
            }
        }
        // An old handcart or a wheelbarrow left somewhere in the yard.
        double cart = Hash.unit(Hash.mix(h + 46));
        if (cart < 0.2) {
            int cx = lx0 + 2 + Hash.range(Hash.mix(h + 47), Math.max(1, lx1 - lx0 - 3));
            int cz = lz0 + 2 + Hash.range(Hash.mix(h + 48), Math.max(1, lz1 - lz0 - 3));
            if (!(cx >= house.x0() - 2 && cx <= house.x1() + 2 && cz >= house.z0() - 2 && cz <= house.z1() + 2)) {
                yardList.add(new int[] {cx, cz, cart < 0.1 ? YARD_HANDCART : YARD_WHEELBARROW, Hash.range(Hash.mix(h + 49), 4)});
            }
        }
    }

    /** Chooses the style, size, position and anomaly of a lot's house, or null if nothing fits. */
    private Level39HouseSpec house(int lx0, int lz0, int lx1, int lz1, Direction facing, boolean merged, long h) {
        boolean frontAlongX = facing.getAxis() == Direction.Axis.Z;
        // Room for the house inside the lot: 2 blocks of yard to the fence everywhere.
        int availFront = (frontAlongX ? lx1 - lx0 : lz1 - lz0) + 1 - 4;
        int availDepth = (frontAlongX ? lz1 - lz0 : lx1 - lx0) + 1 - 4;
        int style = pickStyle(Hash.mix(h + 20));
        if (style == Level39HouseSpec.STYLE_RANCH && availFront < STYLE_SIZE[style][0]) {
            style = pickStyle(Hash.mix(h + 30)) % Level39HouseSpec.STYLE_RANCH; // no room for a ranch here
        }
        int style2 = merged ? pickStyle(Hash.mix(h + 21)) % Level39HouseSpec.STYLE_RANCH : style;
        // Close to the path: a small front yard of 1-3 blocks inside the fence.
        int setback = 1 + Hash.range(Hash.mix(h + 22), 3);
        int[] size = STYLE_SIZE[style];
        int front = size[0] + Hash.range(Hash.mix(h + 23), size[1] - size[0] + 1);
        int depth = size[2] + Hash.range(Hash.mix(h + 24), size[3] - size[2] + 1);
        if (merged) {
            int[] size2 = STYLE_SIZE[style2];
            front += size2[0] - 2;
            depth = Math.max(depth, size2[2]);
        }
        front = Math.min(front, availFront);
        if (depth + setback > availDepth) {
            setback = Math.max(1, availDepth - depth);
        }
        if (front < STYLE_SIZE[style][0] || depth + setback > availDepth) {
            if (merged) {
                return null;
            }
            style = STYLE_CAPE_COD;
            style2 = style;
            front = 11;
            depth = 9;
            setback = Math.min(setback, Math.max(1, availDepth - depth));
            if (front > availFront || depth + setback > availDepth) {
                return null;
            }
        }
        // Randomly placed in the lot (canon): any sideways offset, and a varying setback.
        int lateral = Hash.range(Hash.mix(h + 25), Math.max(1, availFront - front + 1));
        int x0;
        int z0;
        int x1;
        int z1;
        int inLo = 2;
        if (frontAlongX) {
            x0 = lx0 + inLo + lateral;
            x1 = x0 + front - 1;
            if (facing == Direction.NORTH) {
                z0 = lz0 + inLo + setback;
                z1 = z0 + depth - 1;
            } else {
                z1 = lz1 - inLo - setback;
                z0 = z1 - depth + 1;
            }
        } else {
            z0 = lz0 + inLo + lateral;
            z1 = z0 + front - 1;
            if (facing == Direction.WEST) {
                x0 = lx0 + inLo + setback;
                x1 = x0 + depth - 1;
            } else {
                x1 = lx1 - inLo - setback;
                x0 = x1 - depth + 1;
            }
        }
        int doorX = frontAlongX ? (x0 + x1) / 2 : facing == Direction.WEST ? x0 - 1 : x1 + 1;
        int doorZ = frontAlongX ? (facing == Direction.NORTH ? z0 - 1 : z1 + 1) : (z0 + z1) / 2;
        Level39HouseSpec.Anomaly anomaly = merged ? Level39HouseSpec.Anomaly.MERGED : pickAnomaly(Hash.mix(h + 26));
        int baseY = Level39Layout.GROUND_Y + 1;
        if (anomaly == Level39HouseSpec.Anomaly.FLOATING) {
            baseY += 3 + Hash.range(Hash.mix(h + 27), 4);
        }
        boolean lit = Hash.unit(Hash.mix(h + 28)) < 1.0 / 12;
        int storeys = Math.max(STYLE_STOREYS[style], STYLE_STOREYS[style2]);
        return new Level39HouseSpec(houseCounter++, lx0, lz0, lx1, lz1, x0, z0, x1, z1, style, style2, facing, storeys,
                anomaly, baseY, lit, doorX, doorZ, Hash.mix(h + 29));
    }

    private int houseCounter;

    private static final int STYLE_CAPE_COD = Level39HouseSpec.STYLE_CAPE_COD;

    private static int pickStyle(long h) {
        int total = 0;
        for (int w : STYLE_WEIGHT) total += w;
        int pick = Hash.range(h, total);
        for (int s = 0; s < STYLE_WEIGHT.length; s++) {
            pick -= STYLE_WEIGHT[s];
            if (pick < 0) return s;
        }
        return 0;
    }

    // Chances out of 100 for the non-merged anomalies (MERGED is decided by the lots themselves).
    private static final Level39HouseSpec.Anomaly[] ANOMALIES = {
            Level39HouseSpec.Anomaly.NONE, Level39HouseSpec.Anomaly.INVERTED, Level39HouseSpec.Anomaly.CUT_HALF,
            Level39HouseSpec.Anomaly.FLOATING, Level39HouseSpec.Anomaly.ODD_FURNITURE, Level39HouseSpec.Anomaly.BIGGER_INSIDE,
            Level39HouseSpec.Anomaly.FLOODED_ROOM, Level39HouseSpec.Anomaly.VANISHING_DOOR};
    private static final int[] ANOMALY_WEIGHT = {63, 4, 5, 5, 9, 4, 5, 3};

    private static Level39HouseSpec.Anomaly pickAnomaly(long h) {
        int pick = Hash.range(h, 98);
        for (int i = 0; i < ANOMALIES.length; i++) {
            pick -= ANOMALY_WEIGHT[i];
            if (pick < 0) return ANOMALIES[i];
        }
        return Level39HouseSpec.Anomaly.NONE;
    }

    private static boolean nearThing(List<Thing> things, int x0, int z0, int x1, int z1) {
        for (Thing t : things) {
            if (t.x1 >= x0 && t.x0 <= x1 && t.z1 >= z0 && t.z0 <= z1) {
                return true;
            }
        }
        return false;
    }

    private boolean nearHole(int x0, int z0, int x1, int z1) {
        for (Hole hole : holes) {
            int r = (int) Math.ceil(hole.radius) + 1;
            if (hole.x + r >= x0 && hole.x - r <= x1 && hole.z + r >= z0 && hole.z - r <= z1) {
                return true;
            }
        }
        for (Shaft s : shafts) {
            if (s.x >= x0 - 1 && s.x <= x1 + 1 && s.z >= z0 - 1 && s.z <= z1 + 1) {
                return true;
            }
        }
        return false;
    }

    // ==========================================================================================
    // Utility poles
    // ==========================================================================================

    /** Poles along one side of every path, about 28 blocks apart; each is wired to the next. */
    private Pole[] placePoles() {
        List<int[]> raw = new ArrayList<>(); // x, z, alongX (1/0), line id
        for (int i = 0; i < xLine.length; i++) {
            linePoles(i, false, xLine[i], xHalf[i], raw);
        }
        for (int j = 0; j < zLine.length; j++) {
            linePoles(100 + j, true, zLine[j], zHalf[j], raw);
        }
        Pole[] out = new Pole[raw.size()];
        for (int k = raw.size() - 1; k >= 0; k--) {
            int[] p = raw.get(k);
            int next = -1;
            if (k + 1 < raw.size()) {
                int[] q = raw.get(k + 1);
                if (q[3] == p[3] && Math.abs(q[0] - p[0]) + Math.abs(q[1] - p[1]) <= 40) {
                    next = k + 1;
                }
            }
            out[k] = new Pole(p[0], p[1], p[2] == 1, next);
        }
        return out;
    }

    /**
     * Poles beside path {@code id}: when {@code alongX} the path runs east-west at local z =
     * {@code centre}, else north-south at local x = {@code centre}.
     */
    private void linePoles(int id, boolean alongX, int centre, float half, List<int[]> out) {
        long h = Hash.of(seed, cellX * 512 + id, cellZ, 3940);
        int side = Hash.unit(h) < 0.5 ? -1 : 1;
        int start = -APRON + 6 + Hash.range(Hash.mix(h), 20);
        for (int t = start; t < APRON - 4; t += 26 + Hash.range(Hash.of(seed, id, t, 3941), 5)) {
            int across = centre + Math.round(wobble(id, t)) + side * ((int) Math.ceil(half) + 2);
            int lx = alongX ? t : across;
            int lz = alongX ? across : t;
            // Not in the middle of a crossing path, a hole or a shaft hatch.
            if (pathDepth(lx, lz) > 0.5f || nearHole(ox + lx - 2, oz + lz - 2, ox + lx + 2, oz + lz + 2)) {
                continue;
            }
            out.add(new int[] {ox + lx, oz + lz, alongX ? 1 : 0, id});
        }
    }

    // ==========================================================================================
    // Tunnels
    // ==========================================================================================

    /** Local x of tunnel lattice column i (0..8) or z of row j: 96 apart, jittered by up to 12. */
    private int latticeX(int i) {
        return (i - 4) * TUNNEL_STEP + Hash.range(Hash.of(seed, cellX * 16 + i, cellZ, 3950), 25) - 12;
    }

    private int latticeZ(int j) {
        return (j - 4) * TUNNEL_STEP + Hash.range(Hash.of(seed, cellX, cellZ * 16 + j, 3951), 25) - 12;
    }

    private boolean hEdge(int i, int j) {
        return i >= 0 && i < TUNNEL_NODES - 1 && j >= 0 && j < TUNNEL_NODES
                && Hash.unit(seed, cellX * 16 + i, cellZ * 16 + j, 3952) < 0.5;
    }

    private boolean vEdge(int i, int j) {
        return i >= 0 && i < TUNNEL_NODES && j >= 0 && j < TUNNEL_NODES - 1
                && Hash.unit(seed, cellX * 16 + i, cellZ * 16 + j, 3953) < 0.5;
    }

    private int degree(int i, int j) {
        return (hEdge(i - 1, j) ? 1 : 0) + (hEdge(i, j) ? 1 : 0) + (vEdge(i, j - 1) ? 1 : 0) + (vEdge(i, j) ? 1 : 0);
    }

    private void buildTunnels(List<Seg> segList, List<Node> nodeList) {
        int[] lxs = new int[TUNNEL_NODES];
        int[] lzs = new int[TUNNEL_NODES];
        for (int k = 0; k < TUNNEL_NODES; k++) {
            lxs[k] = latticeX(k);
            lzs[k] = latticeZ(k);
        }
        for (int i = 0; i < TUNNEL_NODES; i++) {
            for (int j = 0; j < TUNNEL_NODES; j++) {
                int d = degree(i, j);
                if (d > 0) {
                    nodeList.add(new Node(ox + lxs[i], oz + lzs[j], d));
                }
                long sh = Hash.of(seed, cellX * 16 + i, cellZ * 16 + j, 3954);
                if (hEdge(i, j)) {
                    segList.add(new Seg(true, oz + lzs[j], ox + lxs[i], ox + lxs[i + 1],
                            degree(i, j) == 1, degree(i + 1, j) == 1, Hash.unit(sh) < 0.14, Hash.mix(sh)));
                }
                if (vEdge(i, j)) {
                    segList.add(new Seg(false, ox + lxs[i], oz + lzs[j], oz + lzs[j + 1],
                            degree(i, j) == 1, degree(i, j + 1) == 1, Hash.unit(Hash.mix(sh + 1)) < 0.14, Hash.mix(sh + 2)));
                }
            }
        }
    }

    /**
     * Holes (about one per 150 blocks of tunnel) right over the water of a run, and access
     * shafts (the way back up: at three in four places where a town path crosses over a run).
     */
    private void placeHolesAndShafts(List<Hole> holeList, List<Shaft> shaftList) {
        for (int s = 0; s < segs.length; s++) {
            Seg seg = segs[s];
            int len = seg.b - seg.a;
            long h = seg.salt;
            if (Hash.unit(Hash.mix(h + 1)) < len / 150.0 && len > 40) {
                int t = seg.a + 18 + Hash.range(Hash.mix(h + 2), len - 36);
                float radius = 1.9f + (float) Hash.unit(Hash.mix(h + 3)) * 0.6f;
                float p1 = (float) (Hash.unit(Hash.mix(h + 4)) * Math.PI * 2);
                float p2 = (float) (Hash.unit(Hash.mix(h + 5)) * Math.PI * 2);
                float p3 = (float) (Hash.unit(Hash.mix(h + 6)) * Math.PI * 2);
                holeList.add(new Hole(seg.alongX ? t : seg.fixed, seg.alongX ? seg.fixed : t, radius, s, p1, p2, p3));
            }
            // Paths crossing over this run.
            int[] lines = seg.alongX ? xLine : zLine;
            int off = seg.alongX ? ox : oz;
            for (int k = 0; k < lines.length; k++) {
                int at = off + lines[k];
                if (at < seg.a + 12 || at > seg.b - 12) {
                    continue;
                }
                long kh = Hash.of(seed, at, seg.fixed, 3955);
                // Most path crossings get a shaft: it is the way back up to the town.
                if (Hash.unit(kh) >= 0.75) {
                    continue;
                }
                boolean tooClose = false;
                for (Hole hole : holeList) {
                    if (Math.abs((seg.alongX ? hole.x : hole.z) - at) < 8 && hole.seg == s) {
                        tooClose = true;
                    }
                }
                if (tooClose) {
                    continue;
                }
                int side = Hash.unit(Hash.mix(kh)) < 0.5 ? -1 : 1;
                int x = seg.alongX ? at : seg.fixed + side * (Level39Layout.TUNNEL_HALF + 1);
                int z = seg.alongX ? seg.fixed + side * (Level39Layout.TUNNEL_HALF + 1) : at;
                // The rungs hang on the far wall of the shaft and face back toward the tunnel.
                Direction facing = seg.alongX ? (side > 0 ? Direction.NORTH : Direction.SOUTH)
                        : (side > 0 ? Direction.WEST : Direction.EAST);
                shaftList.add(new Shaft(x, z, facing, s));
            }
        }
    }
}
