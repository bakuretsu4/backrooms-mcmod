package com.rickr.backrooms.world.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * The canonical plan of Level 227 ("Megaflora"): ONE planet-sized tree hanging upside down over a
 * black void. Its roots reach up out of the top of the world; its branches spread out and slowly
 * downhill below them, and a whole rainforest grows on top of every branch.
 *
 * <p>The tree is the same in every world (it does not use the world seed). That way the server's
 * terrain ({@link Level227Layout}) and the client's far renderer, which draws the parts of the tree
 * beyond the loaded chunks, both read this one plan and always line up.
 *
 * <p>The tree is built once, at class load, out of straight pieces ({@link Seg}): a vertical trunk
 * and chains of slightly tapering tubes for the branches. Each tube has an elliptical cross-section
 * (wider than tall, {@link #VERTICAL_SQUASH}), with a flattened top where the forest grows. Pieces
 * that touch simply merge, which is how branches fork, fuse and join the trunk.
 *
 * <p>Coordinates are level block coordinates; the trunk stands on (0, 0).
 */
public final class Level227Tree {
    /** World height (the dimension_type's "height"). Must match megaflora.json. */
    public static final int WORLD_HEIGHT = 768;
    /** Falling below this Y means the void has you (see BackroomsLevel.LEVEL_227). */
    public static final int VOID_DEATH_Y = 24;
    /** Radius of the trunk in blocks (before its bark flutes). */
    public static final double TRUNK_RADIUS = 900;
    /** The "higher sections" of the tree, where the wolves hunt. */
    public static final double HIGH_Y = 520;
    /** The Sun over the Void: far above the roots, straight above the trunk. */
    public static final double SUN_X = 0, SUN_Y = 9000, SUN_Z = 0;
    /**
     * Branch cross-sections are ellipses this much flatter than they are wide (vertical half-height
     * = radius * this). Real big branches sag into that shape, and it gives a wider forest on top.
     */
    public static final double VERTICAL_SQUASH = 0.8;

    /** Piece kinds ({@link Seg#order}). */
    public static final int TRUNK = 0, MAIN = 1, SUB = 2, LIMB = 3, BRIDGE = 4;

    /**
     * One straight piece of the tree, a tube from (x0, y0, z0) to (x1, y1, z1) whose radius goes
     * from r0 to r1. {@code order}: 0 trunk, 1 main branch, 2 sub-branch (including the fusions that
     * grow from one main branch into the next), 3 limb, 4 bridging limb. {@code id} is its index in
     * {@link #segments()}.
     *
     * <p>Horizontal radius is r; vertical half-height is r * {@link #VERTICAL_SQUASH}. The pieces of
     * a branch overlap at their joints (each end is rounded), except a broken tip
     * ({@link #brokenTip}), which ends in a flat, splintered cut face showing growth rings.
     */
    public record Seg(double x0, double y0, double z0, double x1, double y1, double z1,
                      double r0, double r1, int order, int id) {
        public double length() {
            double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /** A canopy mass (or a hanging curtain of fern foliage) for the far renderer: an ellipsoid. */
    public record Blob(double x, double y, double z, double rx, double ry, double rz) {}

    /**
     * A sunbeam: a vertical shaft of the Sun's light, a cylinder of {@code radius} around (x, z)
     * from {@code topY} (where it comes through the gap in the canopy / out of the open sky) down
     * to the ground at {@link #topSurfaceY}(x, z). The forest leaves a gap for it and the ground
     * under it is burnt to ash.
     */
    public record Beam(double x, double z, double radius, double topY) {}

    /** A planned arrival point on a main branch top (the middle of the forest, far from edges). */
    public record Arrival(int x, int z, int branch) {}

    // ==========================================================================================
    // The plan (built once)
    // ==========================================================================================

    private static final List<Seg> SEGS;
    private static final List<Seg> FAR_SKELETON;
    private static final List<Blob> FAR_FOLIAGE;
    private static final List<Beam> BEAMS;
    private static final List<Arrival> ARRIVALS;
    private static final int MAIN_COUNT;

    // Per-piece data the hot paths need, by Seg.id (arrays, so the terrain code never allocates).
    private static final int[] FAMILY;     // which main branch (0..MAIN_COUNT-1), -1 for the trunk
    private static final boolean[] BROKEN; // ends in a broken, cut face instead of a rounded tip
    static double[] sx0, sy0, sz0, sy1, sr0, sr1;
    static double[] ux, uz, hlen, invHlen, invCosPitch;

    // Spatial index: for each CELL x CELL square, the pieces whose tube can cover a column in it.
    static final int CELL = 64;
    private static final int GRID_HALF = 128; // cells each way from the trunk: +-8192 blocks
    private static final int[][] GRID;
    private static final int[] NONE = new int[0];
    // Declared before the static block below, which already uses them while it places the beams.
    private static final ThreadLocal<Span> SCRATCH_SPAN = ThreadLocal.withInitial(Span::new);
    private static final ThreadLocal<int[]> MARKS = ThreadLocal.withInitial(() -> new int[Level227Tree.SEGS.size() + 1]);
    private static final int[][] BEAM_GRID;

    static {
        Builder b = new Builder();
        b.build();
        SEGS = Collections.unmodifiableList(b.segs);
        MAIN_COUNT = b.mainCount;
        int n = SEGS.size();
        FAMILY = new int[n];
        BROKEN = new boolean[n];
        sx0 = new double[n]; sy0 = new double[n]; sz0 = new double[n]; sy1 = new double[n];
        sr0 = new double[n]; sr1 = new double[n];
        ux = new double[n]; uz = new double[n]; hlen = new double[n]; invHlen = new double[n];
        invCosPitch = new double[n];
        for (int i = 0; i < n; i++) {
            Seg s = SEGS.get(i);
            FAMILY[i] = b.family.get(i);
            BROKEN[i] = b.broken.get(i);
            sx0[i] = s.x0(); sy0[i] = s.y0(); sz0[i] = s.z0(); sy1[i] = s.y1();
            sr0[i] = s.r0(); sr1[i] = s.r1();
            double dx = s.x1() - s.x0(), dz = s.z1() - s.z0();
            double h = Math.sqrt(dx * dx + dz * dz);
            hlen[i] = h;
            invHlen[i] = h > 1e-6 ? 1.0 / h : 0;
            ux[i] = h > 1e-6 ? dx / h : 1;
            uz[i] = h > 1e-6 ? dz / h : 0;
            double len = s.length();
            invCosPitch[i] = len > 1e-6 && h > 1e-6 ? len / h : 1;
        }
        GRID = buildGrid();
        BEAMS = Collections.unmodifiableList(b.placeBeams());
        BEAM_GRID = buildBeamGrid();
        ARRIVALS = Collections.unmodifiableList(b.placeArrivals());
        List<Seg> far = new ArrayList<>();
        for (Seg s : SEGS) {
            if (Math.max(s.r0(), s.r1()) >= 10) {
                far.add(s);
            }
        }
        FAR_SKELETON = Collections.unmodifiableList(far);
        FAR_FOLIAGE = Collections.unmodifiableList(b.foliage());
    }

    private Level227Tree() {}

    // ==========================================================================================
    // Public API
    // ==========================================================================================

    /** Every piece of the tree, indexed by {@link Seg#id}. */
    public static List<Seg> segments() {
        return SEGS;
    }

    /** How many main branches the tree has (families 0..n-1). */
    public static int mainBranchCount() {
        return MAIN_COUNT;
    }

    /** Which main branch a piece belongs to (sub-branches and limbs count as their main branch's), -1 for the trunk. */
    public static int familyOf(Seg seg) {
        return FAMILY[seg.id()];
    }

    /** True when the piece ends in a broken stump (a flat cut face with growth rings) rather than a tip. */
    public static boolean brokenTip(Seg seg) {
        return BROKEN[seg.id()];
    }

    /**
     * Every piece whose tube comes within about {@code radius} blocks of column (x, z), the trunk
     * included when near. Allocates a small list: use {@link #forEachSegmentNear} in hot paths.
     */
    public static List<Seg> segmentsNear(double x, double z, double radius) {
        List<Seg> out = new ArrayList<>();
        forEachSegmentNear(x, z, radius, out::add);
        return out;
    }

    /** Same as {@link #segmentsNear}, without building a list (each piece reported once). */
    public static void forEachSegmentNear(double x, double z, double radius, Consumer<Seg> out) {
        int[] marks = MARKS.get();
        int stamp = ++marks[marks.length - 1];
        if (stamp == Integer.MAX_VALUE) {
            java.util.Arrays.fill(marks, 0);
            stamp = marks[marks.length - 1] = 1;
        }
        double trunkReach = TRUNK_RADIUS + 40 + radius;
        if (x * x + z * z < trunkReach * trunkReach) {
            out.accept(SEGS.get(0));
        }
        int cx0 = cellOf(x - radius), cx1 = cellOf(x + radius);
        int cz0 = cellOf(z - radius), cz1 = cellOf(z + radius);
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                for (int id : cell(cx, cz)) {
                    if (marks[id] == stamp) {
                        continue;
                    }
                    marks[id] = stamp;
                    if (axisDistance(id, x, z) <= Math.max(sr0[id], sr1[id]) * 1.1 + radius) {
                        out.accept(SEGS.get(id));
                    }
                }
            }
        }
    }

    /** Every piece with radius >= 10, for far rendering (the trunk is the first). Computed once. */
    public static List<Seg> farSkeleton() {
        return FAR_SKELETON;
    }

    /** The canopy masses along the branches (and the fern curtains under limb tips), for far rendering. */
    public static List<Blob> farFoliage() {
        return FAR_FOLIAGE;
    }

    /**
     * Where a player's feet stand on the highest branch top at column (x, z): the first air block
     * above the forest floor (plants and the giant forest trees are ignored), or
     * {@link Integer#MIN_VALUE} over the void, on a steep flank, or against the trunk.
     */
    public static int topSurfaceY(double x, double z) {
        if (x * x + z * z < (TRUNK_RADIUS + 30) * (TRUNK_RADIUS + 30)) {
            return Integer.MIN_VALUE;
        }
        Span span = SCRATCH_SPAN.get();
        double best = Double.NEGATIVE_INFINITY;
        for (int id : cellAt(x, z)) {
            if (span(id, x, z, span) && span.nd < WALKABLE_ND && span.top > best) {
                best = span.top;
            }
        }
        return best == Double.NEGATIVE_INFINITY ? Integer.MIN_VALUE : (int) Math.floor(best) + 1;
    }

    /**
     * The first air block under the branch a player stands on: take the branch top nearest at or
     * below {@code feetY} at column (x, z) and go down through all the wood joined to it (fused
     * pieces included) to its underside. Integer.MIN_VALUE when there is no branch there. The shell
     * along an underside is solid for at least 12 blocks, so this is real bark, never a hollow.
     * (The doe drops players through here.)
     */
    public static int undersideY(double x, double z, double feetY) {
        Span span = SCRATCH_SPAN.get();
        int[] ids = cellAt(x, z);
        double top = Double.NEGATIVE_INFINITY, bot = 0;
        for (int id : ids) {
            if (span(id, x, z, span) && span.top < feetY + 0.5 && span.top > top) {
                top = span.top;
                bot = span.bot;
            }
        }
        if (top == Double.NEGATIVE_INFINITY) {
            return Integer.MIN_VALUE;
        }
        // Pieces overlapping this stack of wood carry the underside further down.
        for (boolean grew = true; grew; ) {
            grew = false;
            for (int id : ids) {
                if (span(id, x, z, span) && span.bot < bot - 0.01 && span.top >= bot - 1) {
                    bot = span.bot;
                    grew = true;
                }
            }
        }
        return (int) Math.ceil(bot) - 1;
    }

    /** The sunbeam covering column (x, z), or null. */
    @Nullable
    public static Beam beamAt(double x, double z) {
        int cx = cellOf(x) + GRID_HALF, cz = cellOf(z) + GRID_HALF;
        if (cx < 0 || cz < 0 || cx >= 2 * GRID_HALF || cz >= 2 * GRID_HALF) {
            return null;
        }
        for (int i : BEAM_GRID[cx * 2 * GRID_HALF + cz]) {
            Beam b = BEAMS.get(i);
            double dx = x - b.x(), dz = z - b.z();
            if (dx * dx + dz * dz <= b.radius() * b.radius()) {
                return b;
            }
        }
        return null;
    }

    /** Every sunbeam whose centre is within {@code radius} of (x, z). No allocation. */
    public static void forEachBeamNear(double x, double z, double radius, Consumer<Beam> out) {
        for (Beam b : BEAMS) {
            double dx = x - b.x(), dz = z - b.z();
            double reach = radius + b.radius();
            if (dx * dx + dz * dz <= reach * reach) {
                out.accept(b);
            }
        }
    }

    /** All sunbeams of the tree (a couple of dozen). */
    public static List<Beam> beams() {
        return BEAMS;
    }

    /** The planned arrival points: the middle of the forest on main branches, never near a beam. */
    public static List<Arrival> arrivals() {
        return ARRIVALS;
    }

    /** True in the "higher sections" of the tree (wolf country). */
    public static boolean isLevelHigh(double y) {
        return y >= HIGH_Y;
    }

    /**
     * The trunk's bark surface: its distance from the axis at compass angle {@code angle} (radians,
     * atan2(z, x)) and height y. Deep flutes like buttresses run up it, twisting slowly, with a
     * rough wobble on top. The terrain and the far renderer both use this, so they line up.
     */
    public static double trunkRadius(double angle, double y) {
        double flute = Math.abs(Math.sin(angle * 22 + y * 0.0025 + 0.6 * Math.sin(angle * 3)));
        double rough = noise(Math.cos(angle) * 60 + y * 0.012, Math.sin(angle) * 60, 41);
        return TRUNK_RADIUS - 6 + 18 * flute * flute * flute + 4 * rough;
    }

    // ==========================================================================================
    // Column maths shared by the terrain and the queries above
    // ==========================================================================================

    /** Beyond this (lateral distance / half-width) a branch top is too steep to count as walkable. */
    static final double WALKABLE_ND = 0.86;

    /** Where one piece crosses a column: filled in by {@link #span}. Reused, never allocated per call. */
    public static final class Span {
        /** Forest floor height (the top of the topmost solid block is floor(top)). */
        public double top;
        /** Underside height (the bottom solid block is ceil(bot)). */
        public double bot;
        /** Centre-line height of the tube at this column. */
        public double yc;
        /** Lateral distance / half-width: 0 on the ridge line, 1 at the very edge. */
        public double nd;
        /** Half-width (horizontal radius, bark relief included) and vertical half-height here. */
        public double a, b;
        /** Lateral offset from the axis, signed (positive to the right of the direction of growth). */
        public double side;
        /** Distance along the piece (blocks from its start). */
        public double along;
        /** Blocks this column is from a broken tip's cut face (0 on the face), or +inf. */
        public double cut;
        /** Piece id. */
        public int id;
    }

    /** The pieces that can cover column (x, z) (the trunk is never listed: it is handled apart). */
    static int[] cellAt(double x, double z) {
        return cell(cellOf(x), cellOf(z));
    }

    private static int cellOf(double v) {
        return Math.floorDiv((int) Math.floor(v), CELL);
    }

    private static int[] cell(int cx, int cz) {
        cx += GRID_HALF;
        cz += GRID_HALF;
        if (cx < 0 || cz < 0 || cx >= 2 * GRID_HALF || cz >= 2 * GRID_HALF) {
            return NONE;
        }
        int[] c = GRID[cx * 2 * GRID_HALF + cz];
        return c == null ? NONE : c;
    }

    /**
     * How piece {@code id} crosses column (x, z): its top (with the forest floor's bumps, root
     * ridges and puddle dips), its underside (with the long bark grooves along it), and where the
     * column sits across it. Returns false when the tube misses the column.
     */
    public static boolean span(int id, double x, double z, Span out) {
        double dx = x - sx0[id], dz = z - sz0[id];
        double h = hlen[id];
        double s = dx * ux[id] + dz * uz[id];
        double t = s * invHlen[id];
        double tc = t < 0 ? 0 : t > 1 ? 1 : t;
        double px = sx0[id] + ux[id] * h * tc, pz = sz0[id] + uz[id] * h * tc;
        double ex = x - px, ez = z - pz;
        double side = -dx * uz[id] + dz * ux[id];
        double d = Math.sqrt(ex * ex + ez * ez);
        double r = sr0[id] + (sr1[id] - sr0[id]) * tc;
        // Broken tips end in a ragged cut: splinters stick out up to ~6 blocks past the face.
        double cut = Double.POSITIVE_INFINITY;
        if (BROKEN[id]) {
            double jag = 1.5 + 4.5 * Math.abs(noise(side * 0.35, id * 7.0, 91));
            double face = h - jag;
            if (s > face) {
                return false;
            }
            cut = face - s;
            if (t > 1) {
                return false;
            }
        }
        // Bark relief: the tube swells and pinches gently along its length.
        double a = r * (1 + 0.05 * noise(px * 0.013, pz * 0.013, 17 + id % 5));
        if (d >= a) {
            return false;
        }
        double nd = d / a;
        double b = r * VERTICAL_SQUASH * invCosPitch[id];
        double yc = sy0[id] + (sy1[id] - sy0[id]) * tc;
        // Superellipse top (|nd|^3): a broad, gently domed forest floor that rolls off at the edges.
        double nd2 = nd * nd;
        double top = yc + b * Math.sqrt(1 - nd2 * nd);
        double bot = yc - b * Math.sqrt(1 - nd2);
        // Long bark ridges and grooves run along the underside.
        double groove = Math.abs(Math.sin(side * 0.21 + 3.0 * noise(px * 0.01, pz * 0.01, 23)));
        bot += b * 0.07 * groove * groove;
        // The forest floor (fading out towards the edges, where it thins to bare bark).
        double forest = nd < 0.55 ? 1 : nd > 0.85 ? 0 : 1 - (nd - 0.55) / 0.3;
        if (forest > 0) {
            top += forest * floorRelief(x, z);
        }
        out.top = top;
        out.bot = bot;
        out.yc = yc;
        out.nd = nd;
        out.a = a;
        out.b = b;
        out.side = side;
        out.along = s;
        out.cut = cut;
        out.id = id;
        return true;
    }

    /**
     * The forest floor's own shape on top of the branch: low rolling ground, sharp root ridges
     * (the giant roots of the forest snaking over the bark) and shallow dips where puddles sit.
     */
    static double floorRelief(double x, double z) {
        double rolling = 2.2 * noise(x * 0.027, z * 0.027, 31) + 1.0 * noise(x * 0.071, z * 0.071, 32);
        double ridge = 1 - Math.abs(noise(x * 0.043, z * 0.043, 33)) * 9;
        double roots = ridge > 0 ? 2.2 * ridge * ridge : 0;
        return rolling + roots - (isPuddle(x, z) ? 1 : 0);
    }

    /** Puddle dips: in the low parts of the rolling ground, away from root ridges. */
    static boolean isPuddle(double x, double z) {
        return noise(x * 0.06, z * 0.06, 34) < -0.52 && noise(x * 0.027, z * 0.027, 31) < -0.15;
    }

    /** Distance (horizontal) from (x, z) to piece {@code id}'s axis line. */
    static double axisDistance(int id, double x, double z) {
        double dx = x - sx0[id], dz = z - sz0[id];
        double t = (dx * ux[id] + dz * uz[id]) * invHlen[id];
        t = t < 0 ? 0 : t > 1 ? 1 : t;
        double ex = x - (sx0[id] + ux[id] * hlen[id] * t), ez = z - (sz0[id] + uz[id] * hlen[id] * t);
        return Math.sqrt(ex * ex + ez * ez);
    }

    /** Smooth value noise in about [-1, 1], independent of the world seed (the tree is canonical). */
    public static double noise(double x, double z, int salt) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = lattice(ix, iz, salt), b = lattice(ix + 1, iz, salt);
        double c = lattice(ix, iz + 1, salt), d = lattice(ix + 1, iz + 1, salt);
        double ab = a + (b - a) * fx, cd = c + (d - c) * fx;
        return ab + (cd - ab) * fz;
    }

    private static double lattice(int x, int z, int salt) {
        long h = x * 0x632BE59BD9B4E019L + z * 0x85157AF5L + salt * 0xC2B2AE3D27D4EB4FL + 0x2270227L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return ((h >>> 11) * 0x1.0p-53) * 2 - 1;
    }

    // ==========================================================================================
    // Index
    // ==========================================================================================

    private static int[][] buildGrid() {
        int size = 2 * GRID_HALF;
        List<List<Integer>> cells = new ArrayList<>(size * size);
        for (int i = 0; i < size * size; i++) {
            cells.add(null);
        }
        double halfDiag = CELL * 0.7072;
        for (int id = 1; id < SEGS.size(); id++) {
            Seg s = SEGS.get(id);
            double reach = Math.max(s.r0(), s.r1()) * 1.1 + 2;
            int cx0 = cellOf(Math.min(s.x0(), s.x1()) - reach), cx1 = cellOf(Math.max(s.x0(), s.x1()) + reach);
            int cz0 = cellOf(Math.min(s.z0(), s.z1()) - reach), cz1 = cellOf(Math.max(s.z0(), s.z1()) + reach);
            for (int cx = cx0; cx <= cx1; cx++) {
                for (int cz = cz0; cz <= cz1; cz++) {
                    int gx = cx + GRID_HALF, gz = cz + GRID_HALF;
                    if (gx < 0 || gz < 0 || gx >= size || gz >= size) {
                        continue;
                    }
                    double mx = (cx + 0.5) * CELL, mz = (cz + 0.5) * CELL;
                    if (axisDistance(id, mx, mz) > reach + halfDiag) {
                        continue;
                    }
                    List<Integer> list = cells.get(gx * size + gz);
                    if (list == null) {
                        list = new ArrayList<>(4);
                        cells.set(gx * size + gz, list);
                    }
                    list.add(id);
                }
            }
        }
        int[][] grid = new int[size * size][];
        for (int i = 0; i < grid.length; i++) {
            List<Integer> list = cells.get(i);
            if (list != null) {
                grid[i] = list.stream().mapToInt(Integer::intValue).toArray();
            }
        }
        return grid;
    }

    private static int[][] buildBeamGrid() {
        int size = 2 * GRID_HALF;
        int[][] grid = new int[size * size][];
        java.util.Arrays.fill(grid, NONE);
        for (int i = 0; i < BEAMS.size(); i++) {
            Beam b = BEAMS.get(i);
            for (int cx = cellOf(b.x() - b.radius()); cx <= cellOf(b.x() + b.radius()); cx++) {
                for (int cz = cellOf(b.z() - b.radius()); cz <= cellOf(b.z() + b.radius()); cz++) {
                    int gx = cx + GRID_HALF, gz = cz + GRID_HALF;
                    if (gx >= 0 && gz >= 0 && gx < size && gz < size) {
                        int[] old = grid[gx * size + gz];
                        int[] grown = java.util.Arrays.copyOf(old, old.length + 1);
                        grown[old.length] = i;
                        grid[gx * size + gz] = grown;
                    }
                }
            }
        }
        return grid;
    }

    // ==========================================================================================
    // Growing the tree (deterministic: a fixed random sequence, not the world seed)
    // ==========================================================================================

    /** A branch as a chain of points, kept while building so children can sprout from it. */
    private static final class Chain {
        final List<double[]> pts = new ArrayList<>(); // {x, y, z, r, yaw, s}
        final int family;
        final int order;

        Chain(int family, int order) {
            this.family = family;
            this.order = order;
        }

        double length() {
            return pts.get(pts.size() - 1)[5];
        }

        /** {x, y, z, r, yaw} at distance s along the chain. */
        double[] at(double s) {
            for (int i = 1; i < pts.size(); i++) {
                double[] a = pts.get(i - 1), b = pts.get(i);
                if (s <= b[5] || i == pts.size() - 1) {
                    double f = Math.max(0, Math.min(1, (s - a[5]) / Math.max(1e-6, b[5] - a[5])));
                    return new double[] {a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f,
                            a[2] + (b[2] - a[2]) * f, a[3] + (b[3] - a[3]) * f, b[4]};
                }
            }
            return pts.get(0);
        }
    }

    private static final class Builder {
        final List<Seg> segs = new ArrayList<>();
        final List<Integer> family = new ArrayList<>();
        final List<Boolean> broken = new ArrayList<>();
        final List<Chain> chains = new ArrayList<>();
        final List<Chain> mains = new ArrayList<>();
        final SplittableRandom rnd = new SplittableRandom(0x2270_A1DE_0FF5L);
        int mainCount;

        double range(double lo, double hi) {
            return lo + rnd.nextDouble() * (hi - lo);
        }

        int add(double[] a, double[] b, int order, int fam, boolean brokenEnd) {
            int id = segs.size();
            segs.add(new Seg(a[0], a[1], a[2], b[0], b[1], b[2], a[3], b[3], order, id));
            family.add(fam);
            broken.add(brokenEnd);
            return id;
        }

        void build() {
            // The trunk: from below the void up through the whole world (the far renderer carries
            // it on into the roots above).
            segs.add(new Seg(0, -400, 0, 0, WORLD_HEIGHT + 600, 0, TRUNK_RADIUS, TRUNK_RADIUS, TRUNK, 0));
            family.add(-1);
            broken.add(false);

            mainCount = 12 + rnd.nextInt(3);
            // Main branches alternate high and low around the trunk, so neighbours sit at different
            // heights and the forest is layered.
            for (int i = 0; i < mainCount; i++) {
                double baseYaw = i * 2 * Math.PI / mainCount + range(-0.12, 0.12);
                double r0 = range(75, 118);
                double top0 = (i % 2 == 0) ? range(630, 680) : range(565, 620);
                double y0 = top0 - r0 * VERTICAL_SQUASH;
                double start = 620; // inside the trunk, so the branch grows out of solid wood
                double[] p = {Math.cos(baseYaw) * start, y0, Math.sin(baseYaw) * start};
                Chain c = grow(p, r0, baseYaw, 0.0, range(7, 12), range(3, 5), 900,
                        range(4200, 6200), 0.62, 380, 210, i, MAIN, rnd.nextDouble() < 0.3, 0.07);
                mains.add(c);
            }
            // Sub-branches off every main branch, alternating sides, then limbs off everything.
            List<Chain> subs = new ArrayList<>();
            for (Chain m : mains) {
                double len = m.length();
                int side = rnd.nextBoolean() ? 1 : -1;
                for (double s = range(650, 950); s < len - 450; s += range(420, 820)) {
                    double[] at = m.at(s);
                    double rs = Math.max(30, Math.min(60, Math.min(at[3] * range(0.45, 0.62), at[3] * 0.72)));
                    if (rs > at[3] * 0.8) {
                        continue;
                    }
                    double yaw = at[4] + side * range(0.5, 1.0);
                    side = -side;
                    double[] p = {at[0], at[1] + (at[3] - rs) * VERTICAL_SQUASH * 0.85, at[2]};
                    subs.add(grow(p, rs, yaw, 0.0, range(10, 16), range(3, 6), 380,
                            range(900, 2200), 0.65, 300, 140, m.family, SUB, rnd.nextDouble() < 0.35, 0.05));
                }
            }
            fusions();
            List<Chain> parents = new ArrayList<>(subs);
            parents.addAll(mains);
            for (Chain parent : parents) {
                double len = parent.length();
                int side = rnd.nextBoolean() ? 1 : -1;
                double gap = parent.order == MAIN ? 700 : 340;
                for (double s = range(220, 420); s < len - 160; s += range(gap * 0.7, gap * 1.3)) {
                    double[] at = parent.at(s);
                    double rl = Math.max(12, Math.min(28, at[3] * range(0.38, 0.6)));
                    if (rl > at[3] * 0.85) {
                        continue;
                    }
                    double yaw = at[4] + side * range(0.6, 1.25);
                    side = -side;
                    double[] p = {at[0], at[1] + (at[3] - rl) * VERTICAL_SQUASH * 0.85, at[2]};
                    grow(p, rl, yaw, 0.0, range(12, 22), range(6, 12), 200,
                            range(220, 720), rnd.nextDouble() < 0.45 ? 0.55 : 0.85, 150, 95,
                            parent.family, LIMB, rnd.nextDouble() < 0.45, 0.09);
                }
            }
            bridges();
        }

        /**
         * Grows one branch: a chain of straight pieces of about {@code step} blocks that leaves at
         * {@code yaw}, starts steep ({@code pitchStart} degrees down) and levels off towards
         * {@code pitchEnd}, wanders a little from side to side and tapers by {@code taper}. It stops
         * early when its top would sink under {@code minTop}, so the whole tree stays in the world.
         */
        Chain grow(double[] start, double r0, double yaw, double ignored, double pitchStart, double pitchEnd,
                   double pitchFade, double length, double taper, double step, double minTop,
                   int fam, int order, boolean brokenEnd, double wander) {
            Chain c = new Chain(fam, order);
            double x = start[0], y = start[1], z = start[2];
            double baseYaw = yaw, yawVel = 0;
            c.pts.add(new double[] {x, y, z, r0, yaw, 0});
            double s = 0;
            while (s < length) {
                double ds = Math.min(step, length - s);
                if (ds < step * 0.3 && s > 0) {
                    break;
                }
                double pitch = Math.toRadians(pitchEnd + (pitchStart - pitchEnd) * Math.exp(-s / pitchFade));
                yawVel = yawVel * 0.7 + rnd.nextGaussian() * wander;
                yaw += yawVel + (baseYaw - yaw) * 0.15;
                double nx = x + Math.cos(yaw) * Math.cos(pitch) * ds;
                double ny = y - Math.sin(pitch) * ds;
                double nz = z + Math.sin(yaw) * Math.cos(pitch) * ds;
                double ns = s + ds;
                double nr = r0 * (1 - taper * Math.pow(ns / length, 1.15));
                if (ny + nr * VERTICAL_SQUASH < minTop) {
                    break;
                }
                c.pts.add(new double[] {nx, ny, nz, nr, yaw, ns});
                x = nx;
                y = ny;
                z = nz;
                s = ns;
            }
            if (c.pts.size() < 2) {
                // Too low to grow at all: a short stub so the chain is never empty.
                c.pts.add(new double[] {x + Math.cos(yaw) * 20, y, z + Math.sin(yaw) * 20, r0 * 0.8, yaw, 20});
            }
            // Tapered tips end in a short point; broken ones end where they stop.
            if (!brokenEnd) {
                double[] last = c.pts.get(c.pts.size() - 1);
                double tipLen = Math.max(20, last[3] * 2.2);
                double pitch = Math.toRadians(pitchEnd + 4);
                c.pts.add(new double[] {last[0] + Math.cos(yaw) * tipLen, last[1] - Math.sin(pitch) * tipLen,
                        last[2] + Math.sin(yaw) * tipLen, Math.max(3, last[3] * 0.2), yaw, last[5] + tipLen});
            }
            for (int i = 1; i < c.pts.size(); i++) {
                add(c.pts.get(i - 1), c.pts.get(i), order, fam, brokenEnd && i == c.pts.size() - 1);
            }
            chains.add(c);
            return c;
        }

        /**
         * A few neighbouring main branches grow into each other: a thick sub-branch leaves one and
         * fuses into the other further out. These are the big crossings between branches.
         */
        void fusions() {
            int fusions = 3 + rnd.nextInt(2);
            int first = rnd.nextInt(mainCount);
            for (int k = 0; k < fusions; k++) {
                int i = (first + k * (mainCount / fusions)) % mainCount;
                Chain a = mains.get(i), b = mains.get((i + 1) % mainCount);
                double sa = range(900, 1500);
                double[] pa = a.at(sa);
                // Meet the other branch a little further out than we leave this one.
                double best = Double.MAX_VALUE, sb = 0;
                for (double s = 600; s < b.length() - 300; s += 25) {
                    double[] q = b.at(s);
                    double d = Math.hypot(q[0] - pa[0], q[2] - pa[2]) - (s - sa) * 0.35;
                    if (d < best) {
                        best = d;
                        sb = s;
                    }
                }
                double[] pb = b.at(sb);
                double rf = Math.max(34, Math.min(50, Math.min(pa[3], pb[3]) * 0.55));
                double[] p0 = {pa[0], pa[1] + (pa[3] - rf) * VERTICAL_SQUASH * 0.85, pa[2], rf};
                double[] p3 = {pb[0], pb[1] + (pb[3] - rf) * VERTICAL_SQUASH * 0.85, pb[2], rf};
                // A gentle sag in the middle, as a long horizontal bough would have.
                int pieces = 4;
                double sag = Math.hypot(p3[0] - p0[0], p3[2] - p0[2]) * 0.04;
                double[] prev = p0;
                Chain c = new Chain(a.family, SUB);
                c.pts.add(new double[] {p0[0], p0[1], p0[2], rf, Math.atan2(p3[2] - p0[2], p3[0] - p0[0]), 0});
                double run = 0;
                for (int j = 1; j <= pieces; j++) {
                    double f = j / (double) pieces;
                    double[] q = {p0[0] + (p3[0] - p0[0]) * f, p0[1] + (p3[1] - p0[1]) * f - sag * Math.sin(Math.PI * f),
                            p0[2] + (p3[2] - p0[2]) * f, rf * (1 - 0.12 * Math.sin(Math.PI * f))};
                    add(prev, q, SUB, a.family, false);
                    run += Math.hypot(q[0] - prev[0], q[2] - prev[2]);
                    c.pts.add(new double[] {q[0], q[1], q[2], q[3], Math.atan2(p3[2] - p0[2], p3[0] - p0[0]), run});
                    prev = q;
                }
                chains.add(c);
            }
        }

        /**
         * Thin bridging limbs between neighbouring branch systems, only where two of them pass
         * close at about the same height. There are few, so the way across matters.
         */
        void bridges() {
            int n = segs.size();
            for (int fa = 0; fa < mainCount; fa++) {
                int fb = (fa + 1) % mainCount;
                List<double[]> picked = new ArrayList<>();
                List<double[]> candidates = new ArrayList<>();
                for (int i = 1; i < n; i++) {
                    Seg a = segs.get(i);
                    if (family.get(i) != fa || a.order() < SUB || a.order() > LIMB || broken.get(i)) {
                        continue;
                    }
                    for (double f = 0.1; f < 1; f += 0.2) {
                        double ax = a.x0() + (a.x1() - a.x0()) * f, ay = a.y0() + (a.y1() - a.y0()) * f;
                        double az = a.z0() + (a.z1() - a.z0()) * f, ar = a.r0() + (a.r1() - a.r0()) * f;
                        for (int j = 1; j < n; j++) {
                            Seg b = segs.get(j);
                            if (family.get(j) != fb || b.order() < MAIN || b.order() > LIMB) {
                                continue;
                            }
                            double[] q = closest(b, ax, az);
                            double gap = Math.hypot(q[0] - ax, q[2] - az) - ar - q[3];
                            double topA = ay + ar * VERTICAL_SQUASH, topB = q[1] + q[3] * VERTICAL_SQUASH;
                            if (gap > 20 && gap < 150 && Math.abs(topA - topB) < 30) {
                                candidates.add(new double[] {ax, ay, az, ar, q[0], q[1], q[2], q[3], gap});
                            }
                        }
                    }
                }
                candidates.sort((u, v) -> Double.compare(u[8], v[8]));
                for (double[] c : candidates) {
                    if (picked.size() >= 2) {
                        break;
                    }
                    boolean far = true;
                    for (double[] p : picked) {
                        far &= Math.hypot(p[0] - c[0], p[2] - c[2]) > 900;
                    }
                    if (!far) {
                        continue;
                    }
                    picked.add(c);
                    double rb = range(8, 13);
                    double dx = c[4] - c[0], dz = c[6] - c[2], dl = Math.hypot(dx, dz);
                    dx /= dl;
                    dz /= dl;
                    // Each end comes out of the upper flank, so its top meets the forest floor.
                    double[] e0 = {c[0] + dx * c[3] * 0.5, c[1] + 0.93 * c[3] * VERTICAL_SQUASH - rb * VERTICAL_SQUASH, c[2] + dz * c[3] * 0.5, rb};
                    double[] e1 = {c[4] - dx * c[7] * 0.5, c[5] + 0.93 * c[7] * VERTICAL_SQUASH - rb * VERTICAL_SQUASH, c[6] - dz * c[7] * 0.5, rb};
                    double[] mid = {(e0[0] + e1[0]) / 2, (e0[1] + e1[1]) / 2 - dl * 0.03, (e0[2] + e1[2]) / 2, rb * 0.85};
                    add(e0, mid, BRIDGE, fa, false);
                    add(mid, e1, BRIDGE, fa, false);
                }
            }
        }

        /** The closest point of a piece's axis to column (x, z): {x, y, z, r}. */
        static double[] closest(Seg s, double x, double z) {
            double dx = s.x1() - s.x0(), dz = s.z1() - s.z0();
            double l2 = dx * dx + dz * dz;
            double t = l2 < 1e-9 ? 0 : ((x - s.x0()) * dx + (z - s.z0()) * dz) / l2;
            t = Math.max(0, Math.min(1, t));
            return new double[] {s.x0() + dx * t, s.y0() + (s.y1() - s.y0()) * t, s.z0() + dz * t, s.r0() + (s.r1() - s.r0()) * t};
        }

        /**
         * A couple of dozen sunbeams in forest clearings on main and sub-branches, only where
         * nothing of the tree hangs overhead (the Sun is straight above the roots).
         */
        List<Beam> placeBeams() {
            List<Beam> beams = new ArrayList<>();
            Span span = new Span();
            for (int fam = 0; fam < mainCount; fam++) {
                int placed = 0;
                List<Integer> pieces = new ArrayList<>();
                for (int id = 1; id < segs.size(); id++) {
                    Seg s = segs.get(id);
                    if (family.get(id) == fam && (s.order() == MAIN || s.order() == SUB) && Math.min(s.r0(), s.r1()) >= 30) {
                        pieces.add(id);
                    }
                }
                for (int tries = 0; tries < 60 && placed < 2 && !pieces.isEmpty(); tries++) {
                    int id = pieces.get(rnd.nextInt(pieces.size()));
                    Seg s = segs.get(id);
                    double f = range(0.2, 0.8);
                    double side = range(-0.3, 0.3) * Math.min(s.r0(), s.r1());
                    double ax = s.x0() + (s.x1() - s.x0()) * f - uz[id] * side;
                    double az = s.z0() + (s.z1() - s.z0()) * f + ux[id] * side;
                    if (Math.hypot(ax, az) < TRUNK_RADIUS + 400) {
                        continue;
                    }
                    double radius = range(4, 7);
                    int ground = topSurfaceY(ax, az);
                    if (ground == Integer.MIN_VALUE || clearAbove(ax, az, radius + 6, ground, span) == false) {
                        continue;
                    }
                    boolean apart = true;
                    for (Beam b : beams) {
                        apart &= Math.hypot(b.x() - ax, b.z() - az) > 600;
                    }
                    if (!apart) {
                        continue;
                    }
                    beams.add(new Beam(Math.floor(ax) + 0.5, Math.floor(az) + 0.5, radius, WORLD_HEIGHT));
                    placed++;
                }
            }
            return beams;
        }

        /** True when no piece of the tree is above {@code ground} within {@code radius} of (x, z). */
        static boolean clearAbove(double x, double z, double radius, int ground, Span span) {
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    double cx = x + ox * radius, cz = z + oz * radius;
                    for (int id : cellAt(cx, cz)) {
                        if (span(id, cx, cz, span) && span.bot > ground + 2) {
                            return false;
                        }
                    }
                    int top = topSurfaceY(cx, cz);
                    if (top == Integer.MIN_VALUE || Math.abs(top - ground) > 6) {
                        return false; // the clearing must be on level forest floor
                    }
                }
            }
            return true;
        }

        /**
         * Arrival points: along the ridge of every main branch, every few hundred blocks, at least
         * 40 blocks from any edge, with open forest overhead, and well away from every sunbeam.
         */
        List<Arrival> placeArrivals() {
            List<Arrival> out = new ArrayList<>();
            Span span = new Span();
            for (Chain m : mains) {
                double len = m.length();
                for (double s = 500; s < len - 500; s += 420) {
                    double[] at = m.at(s);
                    if (at[3] < 48 || Math.hypot(at[0], at[2]) < TRUNK_RADIUS + 120) {
                        continue;
                    }
                    int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[2]);
                    int ground = topSurfaceY(x + 0.5, z + 0.5);
                    if (ground == Integer.MIN_VALUE) {
                        continue;
                    }
                    boolean ok = !isPuddle(x + 0.5, z + 0.5); // never arrive standing in a puddle
                    for (Beam b : BEAMS) {
                        ok &= Math.hypot(b.x() - x, b.z() - z) > 120;
                    }
                    // Nothing else of the tree right over the spot (an arrival under a branch would be dark and odd).
                    for (int id : cellAt(x + 0.5, z + 0.5)) {
                        ok &= !(span(id, x + 0.5, z + 0.5, span) && span.bot > ground && span.bot < ground + 40);
                    }
                    if (ok) {
                        out.add(new Arrival(x, z, m.family));
                    }
                }
            }
            return out;
        }

        /** Canopy masses above every branch, and fern curtains hanging under the limb tips. */
        List<Blob> foliage() {
            List<Blob> out = new ArrayList<>();
            for (Seg s : segs) {
                if (s.order() == TRUNK || s.order() == BRIDGE) {
                    continue;
                }
                double len = Math.hypot(s.x1() - s.x0(), s.z1() - s.z0());
                double r = Math.max(s.r0(), s.r1());
                double step = Math.max(60, r * 1.2);
                for (double d = step * 0.5; d < len; d += step) {
                    double f = d / len;
                    double x = s.x0() + (s.x1() - s.x0()) * f, z = s.z0() + (s.z1() - s.z0()) * f;
                    double y = s.y0() + (s.y1() - s.y0()) * f;
                    double rr = s.r0() + (s.r1() - s.r0()) * f;
                    out.add(new Blob(x, y + rr * VERTICAL_SQUASH + 22 + rr * 0.15, z, rr * 0.95 + 10, 14 + rr * 0.08, rr * 0.95 + 10));
                }
                if (s.order() == LIMB && !broken.get(s.id()) && s.r1() < 6) {
                    out.add(new Blob(s.x1(), s.y1() - 26, s.z1(), 13, 24, 13));
                }
            }
            return out;
        }
    }
}
