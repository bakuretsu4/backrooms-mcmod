package com.rickr.backrooms.pilot;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.rickr.backrooms.pilot.WorldModel.CLIMBING;
import static com.rickr.backrooms.pilot.WorldModel.COLLIDES;
import static com.rickr.backrooms.pilot.WorldModel.SCAFFOLD;
import static com.rickr.backrooms.pilot.WorldModel.SLOW;
import static com.rickr.backrooms.pilot.WorldModel.STAND;
import static com.rickr.backrooms.pilot.WorldModel.SWIM;
import static com.rickr.backrooms.pilot.WorldModel.UNLOADED;

/**
 * Incremental A* over the client's loaded blocks. It runs in short time slices on the client
 * thread (the world is not safe to read from elsewhere) and resumes next tick, so a long search
 * never freezes the game. When the goal cannot be reached inside what is loaded, it still reports
 * the explored cell that got closest, so the follower can walk there and look again once more of
 * the world has streamed in.
 */
final class PathPlanner {
    // How the player gets from the parent node into this one.
    static final int START = 0, WALK = 1, JUMP = 2, DROP = 3, CLIMB_UP = 4, CLIMB_DOWN = 5,
            SWIM_MOVE = 6, SWIM_UP = 7, SWIM_DOWN = 8, SNEAK_DOWN = 9;
    static final String[] MOVE_NAMES = {"start", "walk", "jump", "drop", "climb_up", "climb_down",
            "swim", "swim_up", "swim_down", "sneak_down"};

    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1};
    private static final double JUMP_HEIGHT = 1.25; // vanilla jump clears just over one block
    private static final double STEP = 0.6;        // auto step-up height
    private static final int SCAN_DOWN = 40;
    private static final double HEURISTIC_WEIGHT = 1.3; // slightly greedy: much faster, paths stay sensible

    static final class Node {
        final int x, y, z;
        final double h;
        final int kind;
        int move;
        long door = Long.MIN_VALUE;
        double g = Double.POSITIVE_INFINITY, f;
        Node parent;
        int heapIndex = -1;
        boolean closed;

        Node(int x, int y, int z, double h, int kind) {
            this.x = x; this.y = y; this.z = z; this.h = h; this.kind = kind;
        }
    }

    enum Outcome { SEARCHING, FOUND, EXHAUSTED, LIMIT }

    // Settings.
    final double gx, gy, gz, radius;
    final boolean ignoreY;     // exploration: only horizontal progress counts
    final double maxDrop, maxDropSoft;
    final boolean landJump;    // false in most Backrooms levels: the mod switches the jump key off on land
    final int maxNodes;

    private final WorldModel world;
    private final Long2IntOpenHashMap visits;
    private final Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>();
    private Node[] heap = new Node[1024];
    private int heapSize;
    private final Node start;

    Outcome outcome = Outcome.SEARCHING;
    Node best;            // closest explored node to the goal (partial target)
    private double bestScore = Double.POSITIVE_INFINITY;
    Node goalNode;
    int expanded;

    PathPlanner(WorldModel world, int sx, int sy, int sz, long startNode, double gx, double gy, double gz, double radius,
                boolean ignoreY, double maxDrop, double maxDropSoft, int maxNodes, Long2IntOpenHashMap visits,
                boolean landJump) {
        this.world = world;
        this.gx = gx; this.gy = gy; this.gz = gz; this.radius = radius;
        this.ignoreY = ignoreY;
        this.maxDrop = maxDrop;
        this.maxDropSoft = maxDropSoft;
        this.maxNodes = maxNodes;
        this.visits = visits;
        this.landJump = landJump;
        start = new Node(sx, sy, sz, WorldModel.height(startNode), WorldModel.kind(startNode));
        start.g = 0;
        start.move = START;
        start.f = HEURISTIC_WEIGHT * distance(start);
        nodes.put(BlockPos.asLong(sx, sy, sz), start);
        push(start);
    }

    double distance(Node n) {
        double dx = n.x + 0.5 - gx, dz = n.z + 0.5 - gz;
        double dy = ignoreY ? 0 : n.h - gy;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    boolean isGoal(Node n) {
        return distance(n) <= radius;
    }

    /** Run until the deadline; returns true once the search has finished. */
    boolean step(long deadlineNanos) {
        if (outcome != Outcome.SEARCHING) {
            return true;
        }
        int sinceClock = 0;
        while (heapSize > 0) {
            if (++sinceClock >= 64) {
                sinceClock = 0;
                if (System.nanoTime() > deadlineNanos) {
                    return false;
                }
            }
            Node n = pop();
            n.closed = true;
            expanded++;
            if (isGoal(n)) {
                goalNode = n;
                outcome = Outcome.FOUND;
                return true;
            }
            if (n != start) {
                double score = distance(n) + penalty(n.x, n.z);
                if (score < bestScore) {
                    bestScore = score;
                    best = n;
                }
            }
            if (nodes.size() >= maxNodes) {
                outcome = Outcome.LIMIT;
                return true;
            }
            expand(n);
        }
        outcome = Outcome.EXHAUSTED;
        return true;
    }

    /** Exploration memory: places already used as partial targets look less attractive. */
    private double penalty(int x, int z) {
        if (visits == null || visits.isEmpty()) return 0;
        return 12.0 * visits.get(regionKey(x, z));
    }

    static long regionKey(int x, int z) {
        return ((long) (x >> 2) << 32) ^ ((z >> 2) & 0xFFFFFFFFL);
    }

    double startDistance(Node n) {
        double dx = n.x - start.x, dy = n.h - start.h, dz = n.z - start.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void expand(Node n) {
        final int x = n.x, c = n.y, z = n.z;
        final double h = n.h;
        // Jump still works in water and on ladders/vines even where the mod forbids it on land, so
        // only a standing start loses the jump; then the most we can climb is an auto step-up.
        final double rise = landJump || n.kind != STAND ? JUMP_HEIGHT : STEP;
        for (int d = 0; d < 8; d++) {
            int dx = DX[d], dz = DZ[d];
            boolean diag = d >= 4;
            if (diag && !(level(x + dx, c, z, h) && level(x, c, z + dz, h))) {
                continue; // never cut a corner past a wall or a hole
            }
            int tx = x + dx, tz = z + dz;
            long found = 0;
            int ty = 0;
            long upper = 0; // lowest ladder/water cell above our level, used only if nothing walkable below
            int upperY = 0;
            int cTop = Mth.floor(h + rise);
            for (int y = cTop; y >= c - SCAN_DOWN; y--) {
                int f = world.flags(tx, y, tz);
                if ((f & UNLOADED) != 0) break;
                long nd = world.node(tx, y, tz);
                if (nd != 0) {
                    if (y > c && WorldModel.kind(nd) != STAND) {
                        upper = nd;
                        upperY = y;
                    } else {
                        found = nd;
                        ty = y;
                        break;
                    }
                }
                // Blocks above our own level only rule out jumping there; at or below it they end the fall.
                if (y <= c && (f & (COLLIDES | WorldModel.LAVA | WorldModel.HAZARD)) != 0 && (f & SCAFFOLD) == 0) break;
            }
            if (upper != 0 && (found == 0 || Math.abs(WorldModel.height(found) - h) > STEP)
                    && WorldModel.height(upper) - h <= rise) {
                found = upper;
                ty = upperY;
            }
            if (found == 0) continue;
            double h2 = WorldModel.height(found);
            int k2 = WorldModel.kind(found);
            double dh = h2 - h;
            int move;
            double cost = diag ? Math.sqrt(2) : 1.0;
            if (dh > STEP) {
                if (diag || dh > rise) continue;
                move = JUMP;
                cost += 0.8;
            } else if (dh < -STEP) {
                if (diag) continue;
                double fall = -dh;
                boolean soft = k2 == SWIM || k2 == CLIMBING; // water or a ladder breaks the fall
                if (fall > (soft ? maxDropSoft : maxDrop)) continue;
                move = DROP;
                cost += 0.3 * fall;
            } else {
                move = (k2 == SWIM || n.kind == SWIM) ? SWIM_MOVE : WALK;
            }
            // The space swept while crossing: both cells' middles at the higher of the two floors.
            double top = Math.max(h, h2);
            double minX = Math.min(x, tx) + 0.2, maxX = Math.max(x, tx) + 0.8;
            double minZ = Math.min(z, tz) + 0.2, maxZ = Math.max(z, tz) + 0.8;
            if (!world.boxClear(minX, minZ, maxX, maxZ, top + 0.001, top + WorldModel.HEIGHT, !diag)) continue;
            long door = world.lastDoor;
            if (door != Long.MIN_VALUE) cost += 3.0;
            if (k2 == SWIM) cost *= (world.flags(tx, ty + 1, tz) & WorldModel.WATER) != 0 ? 3.0 : 2.0; // diving costs air
            if ((world.flags(tx, ty, tz) & SLOW) != 0 || (world.flags(tx, ty - 1, tz) & SLOW) != 0) cost += 4.0;
            relax(n, tx, ty, tz, found, move, door, cost);
        }
        // Vertical moves exist only on ladders/vines/scaffolding and in water.
        if (n.kind == CLIMBING || n.kind == SWIM) {
            long up = world.node(x, c + 1, z);
            if (up != 0 && WorldModel.kind(up) != STAND) {
                // A closed hatch over a ladder is opened like any door.
                double uh = WorldModel.height(up);
                world.boxClear(x + 0.2, z + 0.2, x + 0.8, z + 0.8, uh + 0.001, uh + WorldModel.HEIGHT, true);
                long door = world.lastDoor;
                relax(n, x, c + 1, z, up, n.kind == SWIM && WorldModel.kind(up) == SWIM ? SWIM_UP : CLIMB_UP, door,
                        door != Long.MIN_VALUE ? 4.3 : 1.3);
            }
        }
        int below = world.flags(x, c - 1, z);
        if (n.kind != STAND ? (below & COLLIDES) == 0 || (below & SCAFFOLD) != 0 : (below & SCAFFOLD) != 0) {
            long down = world.node(x, c - 1, z);
            if (down != 0 && WorldModel.kind(down) != STAND) {
                int move = WorldModel.kind(down) == SWIM ? SWIM_DOWN : (below & SCAFFOLD) != 0 ? SNEAK_DOWN : CLIMB_DOWN;
                relax(n, x, c - 1, z, down, move, Long.MIN_VALUE, 1.2);
            }
        }
    }

    /** A cell beside us that the player could stand in at about our height (corner-cutting check). */
    private boolean level(int x, int c, int z, double h) {
        for (int y = c - 1; y <= c + 1; y++) {
            long nd = world.node(x, y, z);
            if (nd != 0 && Math.abs(WorldModel.height(nd) - h) <= STEP) return true;
        }
        return false;
    }

    private void relax(Node from, int x, int y, int z, long packed, int move, long door, double cost) {
        long key = BlockPos.asLong(x, y, z);
        Node m = nodes.get(key);
        if (m == null) {
            m = new Node(x, y, z, WorldModel.height(packed), WorldModel.kind(packed));
            nodes.put(key, m);
        } else if (m.closed) {
            return;
        }
        double g = from.g + cost;
        if (g >= m.g) return;
        m.g = g;
        m.f = g + HEURISTIC_WEIGHT * distance(m);
        m.parent = from;
        m.move = move;
        m.door = door;
        if (m.heapIndex < 0) push(m);
        else siftUp(m.heapIndex);
    }

    /** Nodes from the start (exclusive) to the target (inclusive). */
    static List<Node> trace(Node target) {
        ArrayList<Node> path = new ArrayList<>();
        for (Node n = target; n != null && n.parent != null; n = n.parent) path.add(n);
        java.util.Collections.reverse(path);
        return path;
    }

    // ---- binary min-heap on f ----

    private void push(Node n) {
        if (heapSize == heap.length) heap = Arrays.copyOf(heap, heapSize * 2);
        heap[heapSize] = n;
        n.heapIndex = heapSize++;
        siftUp(n.heapIndex);
    }

    private Node pop() {
        Node top = heap[0];
        Node last = heap[--heapSize];
        heap[heapSize] = null;
        top.heapIndex = -1;
        if (heapSize > 0) {
            heap[0] = last;
            last.heapIndex = 0;
            siftDown(0);
        }
        return top;
    }

    private void siftUp(int i) {
        Node n = heap[i];
        while (i > 0) {
            int p = (i - 1) >>> 1;
            Node pn = heap[p];
            if (pn.f <= n.f) break;
            heap[i] = pn;
            pn.heapIndex = i;
            i = p;
        }
        heap[i] = n;
        n.heapIndex = i;
    }

    private void siftDown(int i) {
        Node n = heap[i];
        int half = heapSize >>> 1;
        while (i < half) {
            int c = 2 * i + 1;
            Node cn = heap[c];
            int r = c + 1;
            if (r < heapSize && heap[r].f < cn.f) {
                c = r;
                cn = heap[r];
            }
            if (n.f <= cn.f) break;
            heap[i] = cn;
            cn.heapIndex = i;
            i = c;
        }
        heap[i] = n;
        n.heapIndex = i;
    }

    int nodeCount() {
        return nodes.size();
    }
}
