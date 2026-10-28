package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.layout.Level227Tree.Beam;
import com.rickr.backrooms.world.layout.Level227Tree.Span;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything Level 227's terrain needs to know about the area around one 32x32 tile, worked out
 * once and cached (like {@link Level39Tile}): the giant forest trees, fallen logs, bear caves and
 * hollow knots, flytrap patches, sunbeam clearings and arrival points whose blocks can reach into
 * the tile. {@link Level227Layout#fillColumn} then only has to look things up per column.
 *
 * <p>Everything here, like the tree itself, ignores the world seed: Level 227 is the same
 * everywhere, so spawners can ask for spots without a seed.
 */
final class Level227Tile {
    static final int SHIFT = 5;
    static final int SIZE = 1 << SHIFT;
    /** Forest trees stand on a grid of cells this big (one tree at most per cell, jittered). */
    static final int TREE_CELL = 10;
    /** The farthest any forest tree's crown (or a fallen log) reaches from its anchor. */
    static final int REACH = 26;
    /** Bear caves and hollow knots: at most one per cell this big. */
    static final int CAVE_CELL = 112;
    static final int KNOT_CELL = 72;
    /** How far a cave's blocks (with its lining and entrance) reach from its centre. */
    static final int CAVE_REACH = 44;
    /** Flytrap patches: at most one per cell this big. */
    static final int FLY_CELL = 40;
    /** The tall grass that hides a flytrap spreads this far around it. */
    static final int FLY_PATCH = 5;
    /** No tree trunk, log, cave or flytrap this close to an arrival point. */
    static final int ARRIVAL_CLEAR = 9;

    /** Tree kinds. SHRUB: a bush of big leaves a few blocks high, filling the gaps under the trees. */
    static final int GIANT = 0, CANOPY = 1, UNDERSTORY = 2, SNAPPED = 3, SHRUB = 4;
    static final int CAVE = 0, KNOT = 1;

    /**
     * One giant forest tree. {@code ground} is the Y of the first air block above the forest floor
     * at its trunk; {@code radius} the trunk radius at the base. Its crown is one to three layers
     * (each a flattened dome: height {@code layerY}, radius {@code layerR}, thickness
     * {@code layerT}) with horizontal limbs out to them; {@code fins} buttress roots fan out at the
     * foot. A SNAPPED tree is only a broken stump of height {@code height} with its growth rings on top.
     */
    record Tree(int x, int z, int ground, int height, double radius, int kind, int fins, double finPhase,
                double finLen, double finH, double[] layerY, double[] layerR, double[] layerT, int reach, long hash) {}

    /**
     * A fallen giant log lying on the forest floor from (x0, z0) to (x1, z1), its axis at height
     * y0 at the first end and y1 at the other; radius r at the first (broken) end, tapering towards
     * the crown end; bowed sideways by {@code bend} blocks in the middle.
     */
    record Log(double x0, double z0, double x1, double z1, double y0, double y1, double r, double bend, boolean cutEnd) {}

    /**
     * A cavity inside a branch. CAVE: a bear cave, an ellipsoid (rx, ry, rz) round (x, y, z) with a
     * sloping tunnel up to the forest floor at (ex, ey, ez). KNOT: a hollow knot, a rounded hollow
     * opening out of the edge of a branch top.
     */
    record Cave(int kind, double x, double y, double z, double rx, double ry, double rz,
                double ex, double ey, double ez, double tunnelR, float yaw) {}

    /** A flytrap hiding place: the column (x, z) whose feet height is y. */
    record Fly(int x, int y, int z) {}

    final int tx;
    final int tz;
    final Tree[] trees;
    final Log[] logs;
    final Cave[] caves;
    final Fly[] flytraps;
    final Beam[] beams;

    private Level227Tile(int tx, int tz) {
        this.tx = tx;
        this.tz = tz;
        int x0 = tx << SHIFT, z0 = tz << SHIFT;
        int x1 = x0 + SIZE - 1, z1 = z0 + SIZE - 1;
        Span span = new Span();

        List<Beam> beams = new ArrayList<>();
        Level227Tree.forEachBeamNear(x0 + SIZE / 2.0, z0 + SIZE / 2.0, SIZE + REACH + 30, beams::add);
        this.beams = beams.toArray(new Beam[0]);

        List<Tree> trees = new ArrayList<>();
        List<Log> logs = new ArrayList<>();
        for (int cx = Math.floorDiv(x0 - REACH, TREE_CELL); cx <= Math.floorDiv(x1 + REACH, TREE_CELL); cx++) {
            for (int cz = Math.floorDiv(z0 - REACH, TREE_CELL); cz <= Math.floorDiv(z1 + REACH, TREE_CELL); cz++) {
                Tree tree = treeAt(cx, cz, span);
                if (tree != null) {
                    if (tree.x() + tree.reach() >= x0 && tree.x() - tree.reach() <= x1
                            && tree.z() + tree.reach() >= z0 && tree.z() - tree.reach() <= z1) {
                        trees.add(tree);
                    }
                    continue;
                }
                Log log = logAt(cx, cz, span);
                if (log != null && Math.max(log.x0(), log.x1()) + 7 >= x0 && Math.min(log.x0(), log.x1()) - 7 <= x1
                        && Math.max(log.z0(), log.z1()) + 7 >= z0 && Math.min(log.z0(), log.z1()) - 7 <= z1) {
                    logs.add(log);
                }
            }
        }
        this.trees = trees.toArray(new Tree[0]);
        this.logs = logs.toArray(new Log[0]);

        List<Cave> caves = new ArrayList<>();
        addCaves(caves, CAVE_CELL, CAVE, x0, z0, x1, z1);
        addCaves(caves, KNOT_CELL, KNOT, x0, z0, x1, z1);
        this.caves = caves.toArray(new Cave[0]);

        List<Fly> flies = new ArrayList<>();
        for (int cx = Math.floorDiv(x0 - FLY_PATCH, FLY_CELL); cx <= Math.floorDiv(x1 + FLY_PATCH, FLY_CELL); cx++) {
            for (int cz = Math.floorDiv(z0 - FLY_PATCH, FLY_CELL); cz <= Math.floorDiv(z1 + FLY_PATCH, FLY_CELL); cz++) {
                Fly fly = flytrapAt(cx, cz);
                if (fly != null) {
                    flies.add(fly);
                }
            }
        }
        this.flytraps = flies.toArray(new Fly[0]);
    }

    private static void addCaves(List<Cave> out, int cell, int kind, int x0, int z0, int x1, int z1) {
        for (int cx = Math.floorDiv(x0 - CAVE_REACH, cell); cx <= Math.floorDiv(x1 + CAVE_REACH, cell); cx++) {
            for (int cz = Math.floorDiv(z0 - CAVE_REACH, cell); cz <= Math.floorDiv(z1 + CAVE_REACH, cell); cz++) {
                Cave cave = caveAt(kind, cx, cz);
                if (cave != null) {
                    out.add(cave);
                }
            }
        }
    }

    // ==========================================================================================
    // What stands where (pure functions of the cell: neighbouring tiles agree)
    // ==========================================================================================

    private static final long SALT = 0x2270_7EEL;

    /**
     * The forest floor at column (x, z) on the highest branch top: fills {@code out} from the piece
     * that carries it and returns the feet Y, or MIN_VALUE (no top there, or another branch sits
     * right on top of it).
     */
    static int ground(double x, double z, Span out) {
        if (x * x + z * z < (Level227Tree.TRUNK_RADIUS + 30) * (Level227Tree.TRUNK_RADIUS + 30)) {
            return Integer.MIN_VALUE;
        }
        int best = -1;
        double top = Double.NEGATIVE_INFINITY;
        int[] ids = Level227Tree.cellAt(x, z);
        for (int id : ids) {
            if (Level227Tree.span(id, x, z, out) && out.nd < Level227Tree.WALKABLE_ND && out.top > top) {
                top = out.top;
                best = id;
            }
        }
        if (best < 0) {
            return Integer.MIN_VALUE;
        }
        Level227Tree.span(best, x, z, out);
        return (int) Math.floor(top) + 1;
    }

    /** Blocks of free space above feet height {@code ground} at (x, z) before the next branch above. */
    static int headroom(double x, double z, int ground, Span scratch) {
        double low = Level227Tree.WORLD_HEIGHT - 1;
        for (int id : Level227Tree.cellAt(x, z)) {
            if (Level227Tree.span(id, x, z, scratch) && scratch.bot > ground && scratch.bot < low) {
                low = scratch.bot;
            }
        }
        return (int) Math.floor(low) - ground;
    }

    private static boolean nearBeam(double x, double z, double clear) {
        for (Beam b : Level227Tree.beams()) {
            double reach = clear + b.radius();
            if (Math.abs(b.x() - x) < reach && Math.abs(b.z() - z) < reach) {
                return true;
            }
        }
        return false;
    }

    private static boolean nearArrival(double x, double z, double clear) {
        for (Level227Tree.Arrival a : Level227Tree.arrivals()) {
            if (Math.abs(a.x() + 0.5 - x) < clear && Math.abs(a.z() + 0.5 - z) < clear) {
                return true;
            }
        }
        return false;
    }

    /** True near a bear cave's mouth (its tunnel's upper half) or a hollow knot. */
    private static boolean nearCave(double x, double z, double clear) {
        for (int cx = Math.floorDiv((int) x - CAVE_REACH, CAVE_CELL); cx <= Math.floorDiv((int) x + CAVE_REACH, CAVE_CELL); cx++) {
            for (int cz = Math.floorDiv((int) z - CAVE_REACH, CAVE_CELL); cz <= Math.floorDiv((int) z + CAVE_REACH, CAVE_CELL); cz++) {
                Cave c = caveAt(CAVE, cx, cz);
                if (c != null && Level227Layout.distToSegment(x, z, (c.x() + c.ex()) / 2, (c.z() + c.ez()) / 2, c.ex(), c.ez()) < clear + c.tunnelR() + 3) {
                    return true;
                }
            }
        }
        for (int cx = Math.floorDiv((int) x - 12, KNOT_CELL); cx <= Math.floorDiv((int) x + 12, KNOT_CELL); cx++) {
            for (int cz = Math.floorDiv((int) z - 12, KNOT_CELL); cz <= Math.floorDiv((int) z + 12, KNOT_CELL); cz++) {
                Cave c = caveAt(KNOT, cx, cz);
                if (c != null && Math.hypot(c.x() - x, c.z() - z) < clear + c.rx() + 2) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The forest tree of cell (cx, cz), or null (a gap, a log, the edge, a clearing...). */
    static Tree treeAt(int cx, int cz, Span span) {
        long h = Hash.of(SALT, cx, cz, 1);
        double x = cx * TREE_CELL + 1.5 + Hash.unit(h) * (TREE_CELL - 3);
        double z = cz * TREE_CELL + 1.5 + Hash.unit(Hash.mix(h + 1)) * (TREE_CELL - 3);
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        // Patchy density: dense groves and thinner, brighter stretches. The gaps between the big
        // trees fill with shrubs, so the forest is a closed wall of green at eye height.
        double density = 0.74 + 0.22 * Level227Tree.noise(cx * 0.21, cz * 0.21, 51);
        boolean shrub = Hash.unit(Hash.mix(h + 2)) > density;
        if (shrub && Hash.unit(Hash.mix(h + 10)) > 0.7) {
            return null;
        }
        int ground = ground(bx + 0.5, bz + 0.5, span);
        if (ground == Integer.MIN_VALUE || span.nd > (shrub ? 0.66 : 0.56)) {
            return null;
        }
        double branchHalfWidth = span.a;
        double roll = Hash.unit(Hash.mix(h + 3));
        int kind = shrub ? SHRUB : roll < 0.14 ? GIANT : roll < 0.6 ? CANOPY : roll < 0.93 ? UNDERSTORY : SNAPPED;
        if (branchHalfWidth < 22 && kind == GIANT) {
            kind = CANOPY; // a slender limb carries no emergent giants
        }
        int room = headroom(bx + 0.5, bz + 0.5, ground, span);
        double u = Hash.unit(Hash.mix(h + 4));
        int height = switch (kind) {
            case GIANT -> 50 + (int) (u * 21);
            case CANOPY -> 32 + (int) (u * 17);
            case UNDERSTORY -> 25 + (int) (u * 7);
            case SHRUB -> 2 + (int) (u * 3);
            default -> 3 + (int) (u * 7);
        };
        if (kind != SNAPPED && kind != SHRUB && height > room - 4) {
            height = room - 4;
            if (height < 25) {
                kind = SNAPPED;
                height = Math.min(3 + (int) (u * 7), room - 3);
            } else if (height < 32) {
                kind = UNDERSTORY;
            } else if (height < 50 && kind == GIANT) {
                kind = CANOPY;
            }
        }
        if (height < 2 || height > room - 2) {
            return null;
        }
        double v = Hash.unit(Hash.mix(h + 5));
        double radius = switch (kind) {
            case GIANT -> 3.2 + v * 1.3;
            case CANOPY -> 2.0 + v * 1.0;
            case UNDERSTORY -> 1.2 + v * 0.6;
            case SHRUB -> 0.4;
            default -> 2.0 + v * 1.5;
        };
        double[] ly, lr, lt;
        double w = Hash.unit(Hash.mix(h + 6));
        switch (kind) {
            case GIANT -> {
                ly = new double[] {height - 4, height * 0.74, height * 0.52};
                lr = new double[] {14 + w * 6, 9 + w * 3, 6 + w * 2};
                lt = new double[] {6.0, 4.2, 3.2};
            }
            case CANOPY -> {
                ly = new double[] {height - 3, height * 0.7, height * 0.45};
                lr = new double[] {9 + w * 4, 6 + w * 2, 4 + w};
                lt = new double[] {4.8, 3.4, 2.4};
            }
            case UNDERSTORY -> {
                ly = new double[] {height - 2.5, height * 0.62};
                lr = new double[] {6 + w * 2.5, 4 + w};
                lt = new double[] {3.8, 2.6};
            }
            case SHRUB -> {
                ly = new double[] {height * 0.6};
                lr = new double[] {2.2 + w * 1.6};
                lt = new double[] {2.2 + w};
            }
            default -> {
                ly = new double[0];
                lr = new double[0];
                lt = new double[0];
            }
        }
        double crown = lr.length > 0 ? lr[0] * 1.2 + 1 : 0;
        int fins = switch (kind) {
            case GIANT -> 5 + Hash.range(Hash.mix(h + 7), 2);
            case CANOPY -> 3 + Hash.range(Hash.mix(h + 7), 3);
            case UNDERSTORY -> Hash.range(Hash.mix(h + 7), 3);
            case SHRUB -> 0;
            default -> 3 + Hash.range(Hash.mix(h + 7), 3);
        };
        // Roots: how far out they snake and how high they stand against the trunk (low, rounded).
        double finLen = radius * (2.0 + 1.2 * Hash.unit(Hash.mix(h + 8))) + 2;
        double finH = kind == SNAPPED ? 1.8 : Math.min(7, Math.max(2, height * 0.12));
        // A snapped stump's flared foot and the splinters lying round it reach further than its roots.
        double stumpReach = kind == SNAPPED ? radius * 1.6 + 8 : 0;
        int reach = (int) Math.ceil(Math.max(Math.max(crown, stumpReach), radius + finLen + 2.5));
        // Leave the sunbeam clearings open to the sky, and the arrival points clear to stand in.
        if (nearBeam(x, z, crown + 3) || nearArrival(x, z, ARRIVAL_CLEAR + radius + finLen) || nearCave(x, z, radius + finLen)) {
            return null;
        }
        return new Tree(bx, bz, ground, height, radius, kind, fins, Hash.unit(Hash.mix(h + 9)) * Math.PI * 2,
                finLen, finH, ly, lr, lt, Math.min(REACH, reach), h);
    }

    /** A fallen log in an empty tree cell, now and then. */
    static Log logAt(int cx, int cz, Span span) {
        long h = Hash.of(SALT, cx, cz, 2);
        if (Hash.unit(h) > 0.07) {
            return null;
        }
        double x = cx * TREE_CELL + TREE_CELL / 2.0, z = cz * TREE_CELL + TREE_CELL / 2.0;
        double len = 12 + Hash.unit(Hash.mix(h + 1)) * 14;
        double ang = Hash.unit(Hash.mix(h + 2)) * Math.PI * 2;
        double r = 1.8 + Hash.unit(Hash.mix(h + 3)) * 1.3;
        double hx = Math.cos(ang) * len / 2, hz = Math.sin(ang) * len / 2;
        int g0 = ground(x - hx, z - hz, span);
        double nd0 = span.nd;
        int g1 = ground(x + hx, z + hz, span);
        double nd1 = span.nd;
        int gm = ground(x, z, span);
        if (g0 == Integer.MIN_VALUE || g1 == Integer.MIN_VALUE || gm == Integer.MIN_VALUE
                || nd0 > 0.6 || nd1 > 0.6 || span.nd > 0.6 || Math.abs(g0 - g1) > 4 || Math.abs(gm - (g0 + g1) / 2) > 3) {
            return null;
        }
        if (nearBeam(x, z, len / 2 + 8) || nearArrival(x, z, ARRIVAL_CLEAR + len / 2) || nearCave(x, z, len / 2 + 2)) {
            return null;
        }
        // It lies along the ground, half sunk in, each end on the ground under it, slightly bowed.
        double bend = (Hash.unit(Hash.mix(h + 5)) - 0.5) * len * 0.16;
        return new Log(x - hx, z - hz, x + hx, z + hz, g0 + r * 0.55 - 1, g1 + r * 0.45 - 1, r, bend,
                Hash.unit(Hash.mix(h + 4)) < 0.6);
    }

    private static final ConcurrentHashMap<Long, Object> CAVES = new ConcurrentHashMap<>();
    private static final Object NO_CAVE = new Object();

    /** The bear cave (CAVE) or hollow knot (KNOT) of a cell, or null. Cached: spawners ask often. */
    static Cave caveAt(int kind, int cx, int cz) {
        long key = Hash.of(SALT, cx, cz, 30 + kind);
        Object cached = CAVES.get(key);
        if (cached == null) {
            if (CAVES.size() > 20000) {
                CAVES.clear();
            }
            Cave cave = kind == CAVE ? planCave(cx, cz) : planKnot(cx, cz);
            cached = cave == null ? NO_CAVE : cave;
            CAVES.put(key, cached);
        }
        return cached == NO_CAVE ? null : (Cave) cached;
    }

    /**
     * A bear cave: a big ellipsoid hollow about a dozen blocks under the forest floor of a thick
     * branch, with a sloping tunnel that runs along the branch and comes up through the forest
     * floor (a mossy hole in the ground, with a Palette waiting in its ceiling).
     */
    private static Cave planCave(int cx, int cz) {
        long h = Hash.of(SALT, cx, cz, 3);
        if (Hash.unit(h) > 0.75) {
            return null;
        }
        Span span = new Span();
        double x = cx * CAVE_CELL + 20 + Hash.unit(Hash.mix(h + 1)) * (CAVE_CELL - 40);
        double z = cz * CAVE_CELL + 20 + Hash.unit(Hash.mix(h + 2)) * (CAVE_CELL - 40);
        int g = ground(x, z, span);
        if (g == Integer.MIN_VALUE || span.a < 30 || span.nd > 0.45) {
            return null;
        }
        Level227Tree.Seg seg = Level227Tree.segments().get(span.id);
        double len = seg.length();
        double ux = (seg.x1() - seg.x0()) / len, uz = (seg.z1() - seg.z0()) / len;
        double sign = Hash.unit(Hash.mix(h + 3)) < 0.5 ? -1 : 1;
        double turn = (Hash.unit(Hash.mix(h + 4)) - 0.5) * 0.7;
        double ex = (ux * Math.cos(turn) - uz * Math.sin(turn)) * sign;
        double ez = (ux * Math.sin(turn) + uz * Math.cos(turn)) * sign;
        double rx = 8 + Hash.unit(Hash.mix(h + 5)) * 3, ry = 4.5 + Hash.unit(Hash.mix(h + 6)) * 1.5;
        double cy = g - 1 - 10 - ry * 0.4;
        double run = rx + 18;
        double mx = x + ex * run, mz = z + ez * run;
        int ge = ground(mx, mz, span);
        if (ge == Integer.MIN_VALUE || span.nd > 0.6 || Math.abs(ge - g) > 6) {
            return null;
        }
        if (nearBeam(x, z, 50) || nearBeam(mx, mz, 30) || nearArrival(x, z, 70) || nearArrival(mx, mz, 40)) {
            return null;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-ex, ez)); // Minecraft yaw facing out along the tunnel
        return new Cave(CAVE, x, cy, z, rx, ry, rx * (0.85 + 0.2 * Hash.unit(Hash.mix(h + 7))), mx, ge - 1.5, mz, 2.4, yaw);
    }

    /** A hollow knot: a rounded hollow at the edge of a branch top, open to the forest and to the drop. */
    private static Cave planKnot(int cx, int cz) {
        long h = Hash.of(SALT, cx, cz, 4);
        if (Hash.unit(h) > 0.6) {
            return null;
        }
        Span span = new Span();
        double x = cx * KNOT_CELL + 10 + Hash.unit(Hash.mix(h + 1)) * (KNOT_CELL - 20);
        double z = cz * KNOT_CELL + 10 + Hash.unit(Hash.mix(h + 2)) * (KNOT_CELL - 20);
        // Slide sideways to the edge of whatever branch top is here.
        int g = ground(x, z, span);
        if (g == Integer.MIN_VALUE || span.a < 16) {
            return null;
        }
        Level227Tree.Seg seg = Level227Tree.segments().get(span.id);
        double len = Math.hypot(seg.x1() - seg.x0(), seg.z1() - seg.z0());
        double ox = -(seg.z1() - seg.z0()) / len, oz = (seg.x1() - seg.x0()) / len; // the "right" side
        double sign = Math.signum(span.side) == 0 ? 1 : Math.signum(span.side);
        double shift = (0.8 * span.a - Math.abs(span.side)) * sign;
        double kx = x + ox * shift, kz = z + oz * shift;
        int gk = ground(kx, kz, span);
        if (gk == Integer.MIN_VALUE || span.nd < 0.65 || span.nd > 0.86) {
            return null;
        }
        if (nearBeam(kx, kz, 20) || nearArrival(kx, kz, 50)) {
            return null;
        }
        double r = 3.2 + Hash.unit(Hash.mix(h + 3)) * 1.6;
        return new Cave(KNOT, kx, gk - 1 - r * 0.45, kz, r, r * 0.85, r, kx, gk, kz, 0, 0);
    }

    /** The flytrap hiding place of a cell, or null: always in open forest, in a patch of tall grass. */
    static Fly flytrapAt(int cx, int cz) {
        long h = Hash.of(SALT, cx, cz, 5);
        if (Hash.unit(h) > 0.55) {
            return null;
        }
        Span span = new Span();
        int x = cx * FLY_CELL + 6 + (int) (Hash.unit(Hash.mix(h + 1)) * (FLY_CELL - 12));
        int z = cz * FLY_CELL + 6 + (int) (Hash.unit(Hash.mix(h + 2)) * (FLY_CELL - 12));
        int g = ground(x + 0.5, z + 0.5, span);
        if (g == Integer.MIN_VALUE || span.nd > 0.5 || Level227Tree.isPuddle(x + 0.5, z + 0.5)) {
            return null;
        }
        if (nearBeam(x, z, 20) || nearArrival(x, z, 48)) {
            return null;
        }
        // Not where a tree trunk or its buttresses stand.
        for (int tcx = Math.floorDiv(x - 10, TREE_CELL); tcx <= Math.floorDiv(x + 10, TREE_CELL); tcx++) {
            for (int tcz = Math.floorDiv(z - 10, TREE_CELL); tcz <= Math.floorDiv(z + 10, TREE_CELL); tcz++) {
                Tree t = treeAt(tcx, tcz, span);
                if (t != null && Math.hypot(t.x() - x, t.z() - z) < t.radius() * 1.9 + 3.5) {
                    return null;
                }
            }
        }
        return new Fly(x, g, z);
    }

    // ==========================================================================================
    // Cache
    // ==========================================================================================

    private static final ConcurrentHashMap<Long, Level227Tile> CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Level227Tile> LAST = new ThreadLocal<>();

    /** The tile containing column (x, z). */
    static Level227Tile of(int x, int z) {
        int tx = x >> SHIFT;
        int tz = z >> SHIFT;
        Level227Tile t = LAST.get();
        if (t != null && t.tx == tx && t.tz == tz) {
            return t;
        }
        long key = ((long) tx << 32) ^ (tz & 0xFFFFFFFFL);
        t = CACHE.get(key);
        if (t == null) {
            if (CACHE.size() > 2048) {
                CACHE.clear();
            }
            t = CACHE.computeIfAbsent(key, k -> new Level227Tile(tx, tz));
        }
        LAST.set(t);
        return t;
    }
}
