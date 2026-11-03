package com.rickr.backrooms.world.layout;

import static com.rickr.backrooms.world.layout.Level0SetPieces.*;

import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * The warren: a patch of Level 0 where the maze gives way to slots. Corridors one block wide (and
 * Level 0's usual three high) twist, branch and dead-end at random between 1-thick walls, mostly
 * dark, with the odd troffer burning inside a slot. Here and there they open into a small room
 * (3x3, 3x5 or 5x5) with one thing in it under one small downlight: supply crates stacked in a corner,
 * a heap of boxes, a filing cabinet, a desk and its chair, a water cooler, or just a chair in the
 * middle.
 *
 * <p>Plan: a 41x41 box (inside a 2-wide moat of plain floor) holding a 20x20 lattice of nodes, a
 * node every other block. Up to ten rooms take 2x2 to 3x3 nodes each, at least one node apart.
 * A growing-tree maze (usually from the newest node, so the corridors run long and twist, now and
 * then from a random one, so they branch) joins every node and every room into one tree; a few
 * extra openings between corridors make the odd loop. Two openings per side lead out to the moat
 * and the maze beyond, so nothing is ever sealed: every corridor and room reaches all of them.
 * A room's prop never stands in front of a way in, and the rest of the room stays one piece.
 *
 * <p>The plan is computed once per site (it needs the whole maze at once) and cached; filling a
 * column reads one int of it.
 */
public final class Level0Warren {
    /** Nodes per side of the corridor lattice. */
    private static final int NODES = 20;
    public static final int BOX = 2 * NODES + 1;
    public static final int MOAT = 2;
    private static final int MAX_ROOMS = 10;
    private static final int ROOM_TRIES = 80;
    /** Share of picks that take the newest node of the growing tree (long twisting runs). */
    private static final double NEWEST = 0.75;
    /** Share of the walls left between two corridor nodes that are opened anyway (loops). */
    private static final double LOOP = 0.05;
    /** Share of corridor nodes with a troffer in the ceiling, and of those, how many are dead. */
    private static final double CORRIDOR_LAMP = 0.08;
    private static final double LAMP_DEAD = 0.25;
    /** No two corridor troffers closer than this (chessboard distance, blocks). */
    private static final int LAMP_SPACING = 4;

    // A cell of the plan: bits 0-1 what it is, 2-3 its ceiling, 4-9 the piece at floor+1,
    // 10-15 the piece at floor+2, 16-17 the facing of both (2D data value).
    private static final int C_WALL = 0;
    private static final int C_CORRIDOR = 1;
    private static final int C_ROOM = 2;
    private static final int CEIL_SHIFT = 2;
    private static final int CEIL_TUBE = 1;
    private static final int CEIL_DEAD = 2;
    private static final int CEIL_LAMP = 3;
    private static final int LOW_SHIFT = 4;
    private static final int HIGH_SHIFT = 10;
    private static final int FACING_SHIFT = 16;

    // Room props.
    private static final int P_CRATES = 0;
    private static final int P_BOXES = 1;
    private static final int P_FILING = 2;
    private static final int P_DESK = 3;
    private static final int P_COOLER = 4;
    private static final int P_CHAIR = 5;
    /** Weighted pick: crates most often (the classic), a lone chair least. */
    private static final int[] PROP_PICK = {P_CRATES, P_CRATES, P_CRATES, P_CRATES, P_BOXES, P_BOXES, P_FILING, P_FILING,
            P_DESK, P_DESK, P_COOLER, P_CHAIR};

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** One site's plan: its cells (index i * BOX + j) and the spots for /backrooms anomaly visit (null if none). */
    private record Plan(int[] cells, int[] roomSpot, int[] corridorSpot) {}

    /** Plans of the sites seen lately. Cleared when large. */
    private static final Map<AnomalySite, Plan> PLANS = new ConcurrentHashMap<>();

    private Level0Warren() {}

    private static Plan plan(AnomalySite site) {
        Plan plan = PLANS.get(site);
        if (plan == null) {
            if (PLANS.size() > 64) {
                PLANS.clear();
            }
            plan = build(site);
            PLANS.put(site, plan);
        }
        return plan;
    }

    // ==========================================================================================
    // Building the plan
    // ==========================================================================================

    private static int at(int i, int j) {
        return i * BOX + j;
    }

    private static boolean inBox(int i, int j) {
        return i >= 0 && j >= 0 && i < BOX && j < BOX;
    }

    private static boolean open(int[] cells, int i, int j) {
        return inBox(i, j) && (cells[at(i, j)] & 3) != C_WALL;
    }

    private static Plan build(AnomalySite site) {
        SplittableRandom rng = new SplittableRandom(site.salt());
        int[] cells = new int[BOX * BOX];
        int[] roomOf = new int[NODES * NODES];
        Arrays.fill(roomOf, -1);
        List<int[]> rooms = placeRooms(rng, cells, roomOf);
        carveMaze(rng, cells, roomOf, rooms);
        openLoops(rng, cells, roomOf);
        openExits(rng, cells);
        int[] roomSpot = null;
        for (int[] room : rooms) {
            int[] spot = furnish(rng, cells, room);
            if (roomSpot == null || (spot[4] == P_CRATES && roomSpot[4] != P_CRATES)) {
                roomSpot = spot;
            }
        }
        int[] corridorSpot = lightCorridors(rng, cells);
        return new Plan(cells, roomSpot, corridorSpot);
    }

    /** Rooms of 2x2 to 3x3 nodes, a node of corridor apart. Each is {first node a, first node b, nodes across a, nodes across b}. */
    private static List<int[]> placeRooms(SplittableRandom rng, int[] cells, int[] roomOf) {
        List<int[]> rooms = new ArrayList<>();
        for (int t = 0; t < ROOM_TRIES && rooms.size() < MAX_ROOMS; t++) {
            int wa = 2 + rng.nextInt(2);
            int wb = 2 + rng.nextInt(2);
            int a0 = rng.nextInt(NODES - wa + 1);
            int b0 = rng.nextInt(NODES - wb + 1);
            boolean free = true;
            for (int a = a0 - 1; a <= a0 + wa && free; a++) {
                for (int b = b0 - 1; b <= b0 + wb; b++) {
                    if (a >= 0 && b >= 0 && a < NODES && b < NODES && roomOf[a * NODES + b] >= 0) {
                        free = false;
                        break;
                    }
                }
            }
            if (!free) {
                continue;
            }
            for (int a = a0; a < a0 + wa; a++) {
                for (int b = b0; b < b0 + wb; b++) {
                    roomOf[a * NODES + b] = rooms.size();
                }
            }
            for (int i = 2 * a0 + 1; i <= 2 * (a0 + wa) - 1; i++) {
                for (int j = 2 * b0 + 1; j <= 2 * (b0 + wb) - 1; j++) {
                    cells[at(i, j)] = C_ROOM;
                }
            }
            rooms.add(new int[] {a0, b0, wa, wb});
        }
        return rooms;
    }

    /** The growing tree: every node and room joined into one tree of 1-wide corridors. */
    private static void carveMaze(SplittableRandom rng, int[] cells, int[] roomOf, List<int[]> rooms) {
        boolean[] seen = new boolean[NODES * NODES];
        int[] active = new int[NODES * NODES];
        int count = reach(rng, rng.nextInt(NODES * NODES), cells, roomOf, rooms, seen, active, 0);
        while (count > 0) {
            int k = rng.nextDouble() < NEWEST ? count - 1 : rng.nextInt(count);
            int node = active[k];
            int a = node / NODES;
            int b = node % NODES;
            int first = rng.nextInt(4);
            boolean grew = false;
            for (int s = 0; s < 4; s++) {
                int[] step = STEPS[(first + s) & 3];
                int na = a + step[0];
                int nb = b + step[1];
                if (na < 0 || nb < 0 || na >= NODES || nb >= NODES || seen[na * NODES + nb]) {
                    continue;
                }
                cells[at(2 * a + 1 + step[0], 2 * b + 1 + step[1])] = C_CORRIDOR;
                count = reach(rng, na * NODES + nb, cells, roomOf, rooms, seen, active, count);
                grew = true;
                break;
            }
            if (!grew) {
                System.arraycopy(active, k + 1, active, k, count - k - 1);
                count--;
            }
        }
    }

    /** Marks a node (or the whole room it is in) reached and adds it to the active list. */
    private static int reach(SplittableRandom rng, int node, int[] cells, int[] roomOf, List<int[]> rooms,
                             boolean[] seen, int[] active, int count) {
        int room = roomOf[node];
        if (room < 0) {
            seen[node] = true;
            cells[at(2 * (node / NODES) + 1, 2 * (node % NODES) + 1)] = C_CORRIDOR;
            active[count] = node;
            return count + 1;
        }
        // A room is reached all at once; the tree may leave it from any of its nodes.
        int[] r = rooms.get(room);
        int start = count;
        for (int a = r[0]; a < r[0] + r[2]; a++) {
            for (int b = r[1]; b < r[1] + r[3]; b++) {
                seen[a * NODES + b] = true;
                active[count++] = a * NODES + b;
            }
        }
        for (int k = count - 1; k > start; k--) {
            int swap = start + rng.nextInt(k - start + 1);
            int tmp = active[k];
            active[k] = active[swap];
            active[swap] = tmp;
        }
        return count;
    }

    /** A few extra openings between two corridor nodes: the odd loop. */
    private static void openLoops(SplittableRandom rng, int[] cells, int[] roomOf) {
        for (int a = 0; a < NODES; a++) {
            for (int b = 0; b < NODES; b++) {
                for (int[] step : new int[][] {{1, 0}, {0, 1}}) {
                    int na = a + step[0];
                    int nb = b + step[1];
                    if (na >= NODES || nb >= NODES || roomOf[a * NODES + b] >= 0 || roomOf[na * NODES + nb] >= 0) {
                        continue;
                    }
                    int wall = at(2 * a + 1 + step[0], 2 * b + 1 + step[1]);
                    if (cells[wall] == C_WALL && rng.nextDouble() < LOOP) {
                        cells[wall] = C_CORRIDOR;
                    }
                }
            }
        }
    }

    /** Two openings in each side of the box, out to the moat: one in each half of the side. */
    private static void openExits(SplittableRandom rng, int[] cells) {
        for (int side = 0; side < 4; side++) {
            for (int half = 0; half < 2; half++) {
                int node = half * (NODES / 2) + 1 + rng.nextInt(NODES / 2 - 2);
                int along = 2 * node + 1;
                switch (side) {
                    case 0 -> cells[at(along, 0)] = C_CORRIDOR;
                    case 1 -> cells[at(along, BOX - 1)] = C_CORRIDOR;
                    case 2 -> cells[at(0, along)] = C_CORRIDOR;
                    default -> cells[at(BOX - 1, along)] = C_CORRIDOR;
                }
            }
        }
    }

    /**
     * Puts one prop and one downlight over it in a room. Returns {stand i, stand j, look i, look j, prop}:
     * where to stand to see the prop (the visit point).
     */
    private static int[] furnish(SplittableRandom rng, int[] cells, int[] room) {
        int i0 = 2 * room[0] + 1;
        int j0 = 2 * room[1] + 1;
        int w = 2 * room[2] - 1;
        int d = 2 * room[3] - 1;
        // Cells just inside a way in must stay clear.
        boolean[] keep = new boolean[w * d];
        for (int p = 0; p < w; p++) {
            for (int q = 0; q < d; q++) {
                for (int[] step : STEPS) {
                    int oi = i0 + p + step[0];
                    int oj = j0 + q + step[1];
                    boolean outside = oi < i0 || oj < j0 || oi >= i0 + w || oj >= j0 + d;
                    if (outside && open(cells, oi, oj)) {
                        keep[p * d + q] = true;
                    }
                }
            }
        }
        // The picked prop first, then every other one in turn; the lone chair always fits.
        int first = PROP_PICK[rng.nextInt(PROP_PICK.length)];
        for (int option = 0; option <= P_CHAIR + 1; option++) {
            int prop = option == 0 ? first : option - 1;
            if (option > 0 && prop == first) {
                continue;
            }
            int[] spot = tryProp(rng, cells, i0, j0, w, d, keep, prop);
            if (spot != null) {
                return spot;
            }
        }
        throw new IllegalStateException("a lone chair always fits in the middle of a room");
    }

    /** Places {@code prop} if it fits (clear of the ways in, the room still one piece); its spot, or null. */
    @Nullable
    private static int[] tryProp(SplittableRandom rng, int[] cells, int i0, int j0, int w, int d, boolean[] keep, int prop) {
        int turn = rng.nextInt(4);
        for (int k = 0; k < 4; k++) {
            int corner = (turn + k) & 3;
            // Corner (ci, cj) and the two directions along the walls from it, into the room.
            int si = (corner & 1) == 0 ? 1 : -1;
            int sj = (corner & 2) == 0 ? 1 : -1;
            int ci = si > 0 ? 0 : w - 1;
            int cj = sj > 0 ? 0 : d - 1;
            int[][] pieces;    // {p, q, low piece, high piece, facing}
            int lampP;
            int lampQ;
            switch (prop) {
                case P_CRATES -> {
                    // Three crates in an L against both walls, a fourth on the one in the corner.
                    pieces = new int[][] {{ci, cj, CRATE, CRATE, 0}, {ci + si, cj, CRATE, AIR, 0}, {ci, cj + sj, CRATE, AIR, 0}};
                    lampP = ci + si;
                    lampQ = cj + sj;
                }
                case P_BOXES -> {
                    Direction face = facing(si, 0);
                    pieces = new int[][] {{ci, cj, BOXES, BOXES, face.get2DDataValue()}, {ci + si, cj, BOXES, AIR, facing(0, sj).get2DDataValue()}};
                    lampP = ci + si;
                    lampQ = cj + sj;
                }
                case P_COOLER -> {
                    pieces = new int[][] {{ci, cj, COOLER, AIR, facing(si, 0).get2DDataValue()}};
                    lampP = ci + si;
                    lampQ = cj + sj;
                }
                case P_FILING, P_DESK -> {
                    // Against the wall the corner's first direction runs along, a block from the corner
                    // (never in front of a way in: those come in at odd blocks of the side).
                    int p = ci + si;
                    int q = cj;
                    Direction into = facing(0, sj);
                    if (prop == P_FILING) {
                        pieces = new int[][] {{p, q, FILING, AIR, into.get2DDataValue()}};
                    } else {
                        pieces = new int[][] {{p, q, DESK, AIR, into.get2DDataValue()},
                                {p, q + sj, CHAIR, AIR, into.getOpposite().get2DDataValue()}};
                    }
                    lampP = p;
                    lampQ = q + sj;
                }
                default -> {
                    // A lone office chair in the middle of the room, facing a wall.
                    int p = w / 2;
                    int q = d / 2;
                    pieces = new int[][] {{p, q, CHAIR, AIR, facing(si, sj).get2DDataValue()}};
                    lampP = p;
                    lampQ = q;
                }
            }
            if (fits(pieces, w, d, keep)) {
                for (int[] piece : pieces) {
                    cells[at(i0 + piece[0], j0 + piece[1])] = C_ROOM | piece[2] << LOW_SHIFT | piece[3] << HIGH_SHIFT
                            | piece[4] << FACING_SHIFT;
                }
                cells[at(i0 + lampP, j0 + lampQ)] |= CEIL_LAMP << CEIL_SHIFT;
                // Stand two blocks diagonally out from the corner (the far corner of a 3-wide room;
                // for the lone chair always the far corner), looking at the prop.
                int[] look = pieces[0];
                int standP = prop == P_CHAIR ? w - 1 - ci : ci + 2 * si;
                int standQ = prop == P_CHAIR ? d - 1 - cj : cj + 2 * sj;
                return new int[] {i0 + standP, j0 + standQ, i0 + look[0], j0 + look[1], prop};
            }
        }
        return null;
    }

    /** The direction (as a facing) of the step (di, dj) in world terms (i = x, j = z); diagonals take the i part. */
    private static Direction facing(int di, int dj) {
        if (di != 0) {
            return di > 0 ? Direction.EAST : Direction.WEST;
        }
        return dj > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** True if every piece is inside the room and off the kept cells, and the free cells stay one piece. */
    private static boolean fits(int[][] pieces, int w, int d, boolean[] keep) {
        boolean[] taken = new boolean[w * d];
        for (int[] piece : pieces) {
            int p = piece[0];
            int q = piece[1];
            if (p < 0 || q < 0 || p >= w || q >= d || keep[p * d + q]) {
                return false;
            }
            taken[p * d + q] = true;
        }
        int free = 0;
        int start = -1;
        for (int k = 0; k < w * d; k++) {
            if (!taken[k]) {
                free++;
                start = k;
            }
        }
        int[] stack = new int[w * d];
        boolean[] seen = new boolean[w * d];
        int top = 0;
        stack[top++] = start;
        seen[start] = true;
        int reached = 0;
        while (top > 0) {
            int k = stack[--top];
            reached++;
            int p = k / d;
            int q = k % d;
            for (int[] step : STEPS) {
                int np = p + step[0];
                int nq = q + step[1];
                int n = np * d + nq;
                if (np >= 0 && nq >= 0 && np < w && nq < d && !taken[n] && !seen[n]) {
                    seen[n] = true;
                    stack[top++] = n;
                }
            }
        }
        return reached == free;
    }

    /**
     * The odd troffer in the corridors (never two close together). Returns the corridor visit
     * spot {stand i, stand j, lamp i, lamp j}: a few blocks down a straight run from a lit one.
     */
    @Nullable
    private static int[] lightCorridors(SplittableRandom rng, int[] cells) {
        List<int[]> lamps = new ArrayList<>();
        int[] best = null;
        int bestRun = 0;
        for (int a = 0; a < NODES; a++) {
            for (int b = 0; b < NODES; b++) {
                int i = 2 * a + 1;
                int j = 2 * b + 1;
                if ((cells[at(i, j)] & 3) != C_CORRIDOR || rng.nextDouble() >= CORRIDOR_LAMP) {
                    continue;
                }
                boolean crowded = false;
                for (int[] lamp : lamps) {
                    crowded |= Math.max(Math.abs(lamp[0] - i), Math.abs(lamp[1] - j)) < LAMP_SPACING;
                }
                if (crowded) {
                    continue;
                }
                boolean dead = rng.nextDouble() < LAMP_DEAD;
                cells[at(i, j)] |= (dead ? CEIL_DEAD : CEIL_TUBE) << CEIL_SHIFT;
                lamps.add(new int[] {i, j});
                if (dead) {
                    continue;
                }
                for (int[] step : STEPS) {
                    int run = 0;
                    while (run < 4 && corridorAt(cells, i + step[0] * (run + 1), j + step[1] * (run + 1))) {
                        run++;
                    }
                    if (run > bestRun) {
                        bestRun = run;
                        best = new int[] {i + step[0] * run, j + step[1] * run, i, j};
                    }
                }
            }
        }
        return best;
    }

    private static boolean corridorAt(int[] cells, int i, int j) {
        return inBox(i, j) && (cells[at(i, j)] & 3) == C_CORRIDOR;
    }

    // ==========================================================================================
    // Blocks
    // ==========================================================================================

    /** The plan cell of world column (x, z), or -1 in the moat. */
    private static int cell(AnomalySite site, int x, int z) {
        int i = x - site.minX() - MOAT;
        int j = z - site.minZ() - MOAT;
        return inBox(i, j) ? plan(site).cells[at(i, j)] : -1;
    }

    static void fill(AnomalySite site, int x, int z, int floor, int ceiling, LevelLayout.Column out) {
        int cell = cell(site, x, z);
        long salt = site.salt();
        out.set(floor, state(CARPET, Direction.NORTH, 0, salt, x, z));
        if (cell >= 0 && (cell & 3) == C_WALL) {
            out.set(floor + 1, state(BASEBOARD, Direction.NORTH, 0, salt, x, z));
            for (int y = floor + 2; y < ceiling; y++) {
                out.set(y, state(WALLPAPER, Direction.NORTH, 0, salt, x, z));
            }
            out.set(ceiling, state(TILE, Direction.NORTH, 0, salt, x, z));
            return;
        }
        int lid = cell < 0 ? 0 : cell >>> CEIL_SHIFT & 3;
        int code = switch (lid) {
            case CEIL_TUBE -> TUBE;
            case CEIL_DEAD -> TUBE_DEAD;
            case CEIL_LAMP -> DOWNLIGHT;
            default -> TILE;
        };
        out.set(ceiling, state(code, Direction.NORTH, 0, salt, x, z));
        if (cell < 0) {
            return;
        }
        Direction facing = Direction.from2DDataValue(cell >>> FACING_SHIFT & 3);
        int low = cell >>> LOW_SHIFT & 63;
        int high = cell >>> HIGH_SHIFT & 63;
        if (low != AIR) {
            out.set(floor + 1, state(low, facing, 0, salt, x, z));
        }
        if (high != AIR) {
            out.set(floor + 2, state(high, facing, 0, salt, x, z));
        }
    }

    /** True if column (x, z) is a wall at eye level. */
    static boolean isSolid(AnomalySite site, int x, int z) {
        int cell = cell(site, x, z);
        return cell >= 0 && (cell & 3) == C_WALL;
    }

    /** True if you can stand on the floor here (not a wall, nothing standing on it). */
    static boolean isWalkable(AnomalySite site, int x, int z) {
        int cell = cell(site, x, z);
        return cell < 0 || ((cell & 3) != C_WALL && (cell >>> LOW_SHIFT & 63) == AIR);
    }

    // ==========================================================================================
    // Named spots for /backrooms anomaly visit (testing)
    // ==========================================================================================

    static final List<String> VISIT_POINTS = List.of("centre", "room", "corridor");

    /**
     * {x, feet y, z, yaw, pitch}: "centre" = the open block nearest the middle, looking down its
     * longest straight run; "room" = in a room (one with crates if there is one), a couple of
     * blocks out from its prop, looking down at it; "corridor" = a few blocks down a straight slot from a lit
     * troffer, looking at it. Null for an unknown name.
     */
    @Nullable
    static double[] visit(AnomalySite site, String name, int floor) {
        Plan plan = plan(site);
        int[] cells = plan.cells();
        int[] s;
        float pitch = 0.0f;
        switch (name) {
            case "centre" -> s = centre(cells);
            case "room" -> {
                s = plan.roomSpot();
                pitch = 25.0f;
            }
            case "corridor" -> s = plan.corridorSpot();
            default -> s = null;
        }
        if (s == null) {
            return null;
        }
        double x = site.minX() + MOAT + s[0] + 0.5;
        double z = site.minZ() + MOAT + s[1] + 0.5;
        double yaw = Math.toDegrees(Math.atan2(-(s[2] - s[0]), s[3] - s[1]));
        return new double[] {x, floor + 1, z, yaw, pitch};
    }

    /** The open block nearest the middle, with the end of its longest straight run (up to 6). */
    private static int[] centre(int[] cells) {
        int mid = BOX / 2;
        for (int r = 0; r <= mid; r++) {
            for (int i = mid - r; i <= mid + r; i++) {
                for (int j = mid - r; j <= mid + r; j++) {
                    if (Math.max(Math.abs(i - mid), Math.abs(j - mid)) != r || !corridorAt(cells, i, j)) {
                        continue;
                    }
                    int[] best = {i, j, i + 1, j};
                    int bestRun = 0;
                    for (int[] step : STEPS) {
                        int run = 0;
                        while (run < 6 && open(cells, i + step[0] * (run + 1), j + step[1] * (run + 1))) {
                            run++;
                        }
                        if (run > bestRun) {
                            bestRun = run;
                            best = new int[] {i, j, i + step[0] * run, j + step[1] * run};
                        }
                    }
                    return best;
                }
            }
        }
        return new int[] {mid, mid, mid + 1, mid};
    }
}
