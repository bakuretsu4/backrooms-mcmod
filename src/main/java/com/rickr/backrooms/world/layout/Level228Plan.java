package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Hash;
import org.jetbrains.annotations.Nullable;

/**
 * The floor plan of Level 228, "Maze of Mirrors": a pure function of (seed, x, z), shared by the
 * chunk generator ({@link Level228Layout}), the server rules and the client (the reflections and
 * the thing that lives in them), which is why every query here is allocation-free.
 *
 * <h2>The trick: reflections are real space</h2>
 * A mirror is a thin pane of glass, and what you see in it is a mirrored copy of the maze built
 * behind it out of real blocks (an "image volume"), so it renders, lights and fogs right under any
 * renderer. Each image volume maps to real space by an axis-aligned isometry
 * ({@link Image}); players never get in (it is sealed by the glass and by solid walls).
 *
 * <h2>The lattice</h2>
 * Everything sits on a lattice of {@link #CELL} x {@link #CELL} cells. In cell-local coordinates:
 * <ul>
 *   <li>0..1 x 0..1: a <b>node</b>, a 2 x 2 junction of corridors;</li>
 *   <li>local x 2..17, z 0..1: the corridor ("lane") east to the next node, X(i, j); z 2..17,
 *       x 0..1: the lane south, Z(i, j). Lanes are 2 wide; their side walls are the rows/columns
 *       2 and 17; the cells at 2 and 17 inside a lane are the doorways in the nodes' faces;</li>
 *   <li>local 2/17 x 2/17: frame posts at the corners;</li>
 *   <li>local 3..16 x 3..16: an <b>interior</b>, never walked: it holds the images of the lanes
 *       around it, or is solid.</li>
 * </ul>
 * The lattice of walls has a period of 18 = 6 x 3, and the glass of a lane's two side walls sits
 * 3 blocks apart, so the planes of every side mirror fall on one family (z = 3k - 0.5): images of
 * images line up exactly, and two facing side mirrors give the classic receding corridor of
 * yourselves, one copy every 3 blocks, as deep as the interior allows (up to 4 deep, see
 * {@link #interiorRaw}).
 *
 * <h2>The maze</h2>
 * Which lanes are open: a random spanning tree per region of {@link #TREE_NODES}² nodes (every
 * node steps toward the region's centre), regions joined by two openings per side, plus a share
 * of extra openings for loops. So the real maze is connected everywhere: from any corridor there
 * is a way to every exit.
 *
 * <h2>The mirrors</h2>
 * <ul>
 *   <li><b>Side mirrors.</b> Each interior serves the lanes on two opposite sides of it (or
 *       none): their side walls facing it are mirror panes in frames, and the interior holds the
 *       stacked images of those lanes. A lane with mirrors on both sides is a corridor of
 *       yourselves receding into the coloured dark.</li>
 *   <li><b>False branches.</b> A closed lane may get a mirror in the face of the node at one end
 *       (only if the opposite face is open): the closed lane holds the reflection of the node and
 *       the corridor behind you, so the corridor seems to run straight on, and you walk into glass.
 *       At a junction two of the four branches can be reflections.</li>
 *   <li><b>Framed gaps.</b> Rarely an open doorway carries a mirror's frame lintel: you expect
 *       glass and it is open.</li>
 *   <li><b>The exit</b> is a black mirror with nothing behind it, set in the side wall of one lane
 *       per {@link #EXITS} region (about one per 1024 x 1024 blocks); both walls of that lane are
 *       plain panels, so it is never reflected.</li>
 * </ul>
 */
public final class Level228Plan {
    /** The lattice period: a node and its two lanes and interior. */
    public static final int CELL = 18;
    /** The floor layer (also the bottom of every image volume). */
    public static final int FLOOR_Y = LevelLayout.FLOOR_Y;
    /** Feet of someone standing in a corridor. */
    public static final int FEET_Y = FLOOR_Y + 1;
    /** Mirror panes are FEET_Y..PANE_TOP_Y; the frame lintel above them is at LINTEL_Y. */
    public static final int PANE_TOP_Y = FLOOR_Y + 2;
    public static final int LINTEL_Y = FLOOR_Y + 3;
    /** The ceiling layer, with the light panels (also the top of every image volume). */
    public static final int CEILING_Y = FLOOR_Y + 4;

    /** Contents of one block of a column, for y FEET_Y..LINTEL_Y ({@link #at}). */
    public static final int AIR = 0;
    public static final int PANEL = 1;
    public static final int FRAME = 2;
    /** Mirror pane in the plane x = const (in a node's east/west face, or a Z lane's side wall). */
    public static final int MIRROR_X = 3;
    /** Mirror pane in the plane z = const. */
    public static final int MIRROR_Z = 4;
    public static final int EXIT_X = 5;
    public static final int EXIT_Z = 6;

    /** Light colours ({@link #lightColor}). */
    public static final int BLUE = 0;
    public static final int GREEN = 1;
    public static final int RED = 2;

    /** Resolved-column flags ({@link #column}): an image column, and its flips. */
    public static final int F_IMAGE = 1 << 16;
    public static final int F_FLIP_X = 1 << 17;
    public static final int F_FLIP_Z = 1 << 18;
    /** A column of the real maze (corridors, doorways, their walls and posts). */
    public static final int F_REAL = 1 << 19;

    /** Nodes per side of one maze-tree region (odd, so it has a centre node). */
    static final int TREE_NODES = 9;
    /** Share of the lanes that are not part of the tree but open anyway (loops). */
    private static final double LOOPS = 0.12;
    /** Share of the possible false-branch mirrors that are built. */
    private static final double FALSE_SHARE = 0.75;
    /** Share of open doorways framed like a mirror. */
    private static final double FRAMED_GAP_SHARE = 0.08;
    /** Share of corridors pinched to one block wide at a frame post. */
    private static final double PINCH_SHARE = 0.18;

    /** Nodes per side of one exit region: 57 x 18 = 1026 blocks, about 1024. */
    static final int EXIT_NODES = 57;
    /** One exit per region, kept 40 blocks (over two cells) inside it so its lane never crosses out. */
    public static final ExitLocator EXITS = new ExitLocator(EXIT_NODES * CELL, 40, 2280);

    /** Lane axes. */
    static final int AX_X = 0;
    static final int AX_Z = 1;

    private Level228Plan() {}

    // ------------------------------------------------------------------------------------------
    // The shared contract
    // ------------------------------------------------------------------------------------------

    /**
     * One image volume: its box (inclusive block coordinates) and the map to real space,
     * {@code realBlock = flip * imgBlock + off} per horizontal axis (Y unchanged). Flip is its own
     * inverse: {@code imgBlock = (realBlock - off) * flip}. For continuous points (entity
     * positions): {@code real = flip * img + off + (flip < 0 ? 1 : 0)}. The box spans y
     * {@link #FLOOR_Y}..{@link #CEILING_Y}: floor and ceiling are mirrored too.
     */
    public static final class Image {
        public int minX, maxX, minY, maxY, minZ, maxZ;
        /** +1 or -1. Odd total flips (flipX * flipZ == -1) = mirrored handedness. */
        public int flipX, flipZ;
        public int offX, offZ;
        /** 1 = directly behind one mirror, 2 = mirror-in-mirror, ... */
        public int depth;
        /**
         * The depth-1 pane you look through to see this image: its plane axis (0: x = const,
         * 1: z = const) and the plane coordinate (the middle of the pane block).
         */
        public int paneAxis;
        public double panePlane;

        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        public int realX(int imgX) {
            return flipX * imgX + offX;
        }

        public int realZ(int imgZ) {
            return flipZ * imgZ + offZ;
        }

        void set(int minX, int maxX, int minZ, int maxZ, int flipX, int offX, int flipZ, int offZ,
                 int depth, int paneAxis, double panePlane) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = FLOOR_Y;
            this.maxY = CEILING_Y;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.flipX = flipX;
            this.offX = offX;
            this.flipZ = flipZ;
            this.offZ = offZ;
            this.depth = depth;
            this.paneAxis = paneAxis;
            this.panePlane = panePlane;
        }
    }

    /**
     * Fills {@code out} with the image volumes intersecting the square of {@code radius} around
     * (x, z), nearest cells first; returns the count (at most {@code out.length}). No allocation.
     */
    public static int imagesNear(long seed, double x, double z, double radius, Image[] out) {
        int minX = (int) Math.floor(x - radius);
        int maxX = (int) Math.floor(x + radius);
        int minZ = (int) Math.floor(z - radius);
        int maxZ = (int) Math.floor(z + radius);
        int ci = Math.floorDiv((int) Math.floor(x), CELL);
        int cj = Math.floorDiv((int) Math.floor(z), CELL);
        // A cell's images reach one block outside it (a lane's wall row), hence the +1/-1.
        int rings = (int) Math.ceil((radius + 2) / CELL) + 1;
        int n = 0;
        for (int ring = 0; ring <= rings && n < out.length; ring++) {
            for (int di = -ring; di <= ring; di++) {
                for (int dj = -ring; dj <= ring; dj++) {
                    if (Math.max(Math.abs(di), Math.abs(dj)) != ring) {
                        continue;
                    }
                    n = cellImages(seed, ci + di, cj + dj, minX, maxX, minZ, maxZ, out, n);
                    if (n >= out.length) {
                        return n;
                    }
                }
            }
        }
        return n;
    }

    /** True for blocks of real, walkable maze space (the air you can stand in). */
    public static boolean isRealSpace(long seed, int x, int y, int z) {
        if (y < FEET_Y || y > LINTEL_Y) {
            return false;
        }
        int c = realColumn(seed, x, z);
        return (c & F_REAL) != 0 && at(c, y) == AIR;
    }

    /** True inside any image volume. */
    public static boolean isImageSpace(long seed, int x, int z) {
        return (column(seed, x, z) & F_IMAGE) != 0;
    }

    /**
     * For walking and pathing: true if a real corridor cell (x, z) connects to (x + dx, z + dz)
     * (dx, dz in -1..1; a diagonal step needs both of its sides clear, as a body needs).
     */
    public static boolean canStep(long seed, int x, int z, int dx, int dz) {
        if (!isWalkable(seed, x, z) || !isWalkable(seed, x + dx, z + dz)) {
            return false;
        }
        return dx == 0 || dz == 0 || isWalkable(seed, x + dx, z) && isWalkable(seed, x, z + dz);
    }

    /** A column you can stand in: real maze, feet and head clear (a lintel at LINTEL_Y is fine). */
    public static boolean isWalkable(long seed, int x, int z) {
        int c = realColumn(seed, x, z);
        return (c & F_REAL) != 0 && at(c, FEET_Y) == AIR && at(c, FEET_Y + 1) == AIR;
    }

    // ------------------------------------------------------------------------------------------
    // Columns
    // ------------------------------------------------------------------------------------------

    /** What is at {@code y} (FEET_Y..LINTEL_Y) in a column code: {@link #AIR}, {@link #PANEL} ... */
    public static int at(int column, int y) {
        return (column >> (3 * (y - FEET_Y))) & 7;
    }

    /** The ceiling light of a column code: 0 none, else 1 + colour * 4 + shade. */
    public static int light(int column) {
        return (column >> 9) & 15;
    }

    public static int lightColor(int light) {
        return (light - 1) >> 2;
    }

    public static int lightShade(int light) {
        return (light - 1) & 3;
    }

    private static int code(int y41, int y42, int y43) {
        return y41 | y42 << 3 | y43 << 6;
    }

    private static final int SOLID = code(PANEL, PANEL, PANEL);
    private static final int OPEN = code(AIR, AIR, AIR);
    private static final int POST = code(FRAME, FRAME, FRAME);

    /**
     * The column at (x, z) as built: real maze columns ({@link #F_REAL}), image columns (the real
     * column they map to, with {@link #F_IMAGE} and the flips), or solid fill (neither flag).
     */
    public static int column(long seed, int x, int z) {
        int i = Math.floorDiv(x, CELL);
        int j = Math.floorDiv(z, CELL);
        int lx = x - i * CELL;
        int lz = z - j * CELL;
        boolean midX = lx >= 3 && lx <= 16;
        boolean midZ = lz >= 3 && lz <= 16;
        if (midX && midZ) {
            return interiorColumn(seed, i, j, x, z, lx, lz);
        }
        if (midX && (lz <= 2 || lz == 17)) {
            // A body cell of lane X(i, j) (rows -1..2 around z = jP), or of X(i, j + 1) (row 17).
            int lj = lz == 17 ? j + 1 : j;
            if (!open(seed, AX_X, i, lj)) {
                return falseColumn(seed, AX_X, i, lj, x, z);
            }
        } else if (midZ && (lx <= 2 || lx == 17)) {
            int li = lx == 17 ? i + 1 : i;
            if (!open(seed, AX_Z, li, j)) {
                return falseColumn(seed, AX_Z, li, j, x, z);
            }
        }
        return realColumn(seed, x, z);
    }

    /** Image or fill column of an interior. */
    private static int interiorColumn(long seed, int i, int j, int x, int z, int lx, int lz) {
        int raw = interiorRaw(seed, i, j);
        boolean zOriented = (raw & 1) != 0;
        int across = zOriented ? lx : lz;
        int lo = lowRows(seed, i, j, raw);
        int hi = highRows(seed, i, j, raw);
        int o;
        boolean low;
        if (across - 3 < lo) {
            o = across - 3;
            low = true;
        } else if (16 - across < hi) {
            o = 16 - across;
            low = false;
        } else {
            return SOLID;
        }
        int depth = o / 3 + 1;
        int base = zOriented ? (low ? i * CELL : (i + 1) * CELL) : (low ? j * CELL : (j + 1) * CELL);
        int flip = (depth & 1) == 1 ? -1 : 1;
        int off = stripeOffset(base, low, depth);
        if (zOriented) {
            return image(seed, flip * x + off, z, flip < 0 ? F_FLIP_X : 0);
        }
        return image(seed, x, flip * z + off, flip < 0 ? F_FLIP_Z : 0);
    }

    /**
     * The offset of the depth-{@code depth} image of a lane whose corridor starts at {@code base}
     * (its first row), seen through its {@code low} (+ side, wall at base + 2) or high (- side,
     * wall at base - 1) mirrors. Odd depths flip, even depths are the lane moved 3 * depth over.
     */
    private static int stripeOffset(int base, boolean low, int depth) {
        if ((depth & 1) == 1) {
            return low ? 2 * base + 4 + 3 * (depth - 1) : 2 * base - 2 - 3 * (depth - 1);
        }
        return low ? -3 * depth : 3 * depth;
    }

    private static int image(long seed, int rx, int rz, int flips) {
        return (realColumn(seed, rx, rz) & ~F_REAL) | F_IMAGE | flips;
    }

    /** Image (false branch) or fill column in the body of the closed lane (axis, a, b). */
    private static int falseColumn(long seed, int axis, int a, int b, int x, int z) {
        int end = falseMirror(seed, axis, a, b);
        if (end == 0) {
            return SOLID;
        }
        int off = falseOffset(axis, a, b, end);
        return axis == AX_X ? image(seed, off - x, z, F_FLIP_X) : image(seed, x, off - z, F_FLIP_Z);
    }

    /** realAlong = off - imgAlong for the false-branch image of lane (axis, a, b) seen from end 1 (low) or 2. */
    private static int falseOffset(int axis, int a, int b, int end) {
        int base = (axis == AX_X ? a : b) * CELL;
        return end == 1 ? 2 * base + 4 : 2 * base + 34;
    }

    /**
     * The real column at (x, z) with {@link #F_REAL}, if it belongs to the real maze (nodes,
     * doorways, posts, open lanes and their walls); otherwise solid fill, no flag. Image space
     * maps to here, so this never looks at images.
     */
    static int realColumn(long seed, int x, int z) {
        int i = Math.floorDiv(x, CELL);
        int j = Math.floorDiv(z, CELL);
        int lx = x - i * CELL;
        int lz = z - j * CELL;
        boolean laneX = lx <= 1;   // in the column band of the Z lanes / nodes
        boolean laneZ = lz <= 1;   // in the row band of the X lanes / nodes
        boolean wallX = lx == 2 || lx == 17;
        boolean wallZ = lz == 2 || lz == 17;
        if (laneX && laneZ) {
            return F_REAL | OPEN | nodeLight(seed, i, j, lx, lz) << 9;
        }
        if (wallX && wallZ) {
            return F_REAL | POST;
        }
        if (laneZ) {
            // Row band of lane X(i, j): a doorway (lx 2 / 17) or the corridor.
            if (wallX) {
                return F_REAL | doorway(seed, AX_X, i, j, lx == 2 ? 1 : 2);
            }
            if (lx >= 3 && lx <= 16) {
                return open(seed, AX_X, i, j) ? F_REAL | corridor(seed, AX_X, i, j, lx, lz) : SOLID;
            }
        } else if (laneX) {
            if (wallZ) {
                return F_REAL | doorway(seed, AX_Z, i, j, lz == 2 ? 1 : 2);
            }
            if (lz >= 3 && lz <= 16) {
                return open(seed, AX_Z, i, j) ? F_REAL | corridor(seed, AX_Z, i, j, lz, lx) : SOLID;
            }
        } else if (wallZ && lx >= 3 && lx <= 16) {
            // A side wall of an X lane: south wall of X(i, j) (lz 2) or north wall of X(i, j + 1).
            int b = lz == 2 ? j : j + 1;
            return open(seed, AX_X, i, b) ? F_REAL | sideWall(seed, AX_X, i, b, lz == 2, lx) : SOLID;
        } else if (wallX && lz >= 3 && lz <= 16) {
            int a = lx == 2 ? i : i + 1;
            return open(seed, AX_Z, a, j) ? F_REAL | sideWall(seed, AX_Z, a, j, lx == 2, lz) : SOLID;
        }
        return SOLID;
    }

    /** A doorway of lane (axis, a, b) at its low end (1, the face of node (a, b)) or high end (2). */
    private static int doorway(long seed, int axis, int a, int b, int end) {
        int mirror = axis == AX_X ? MIRROR_X : MIRROR_Z;
        if (open(seed, axis, a, b)) {
            boolean framed = Hash.unit(seed, a * 2 + end, b * 2 + axis, 2291) < FRAMED_GAP_SHARE;
            return framed ? code(AIR, AIR, FRAME) : OPEN;
        }
        return falseMirror(seed, axis, a, b) == end ? code(mirror, mirror, FRAME) : SOLID;
    }

    /**
     * A corridor cell of the open lane (axis, a, b), {@code along} 3..16 cells from its low node,
     * {@code across} 0..1. Mostly air; sometimes a frame post pinches it to one wide; and a strip of
     * light panels down one side of the ceiling: three strips of two (along 4-5, 9-10, 14-15), so
     * every stretch of corridor is lit and no stretch is more than two blocks from a panel. (The
     * first version had one panel in about half the corridors and the maze read as pitch black in
     * both renderers: dark glass, dark panels and a black floor need light everywhere.) The strips
     * share the lane's shade, so a corridor is one tone; image volumes copy them with the rest.
     */
    private static int corridor(long seed, int axis, int a, int b, int along, int across) {
        long h = Hash.of(seed, a, b, 2292 + axis);
        if (Hash.unit(h) < PINCH_SHARE && !isExitSegment(seed, axis, a, b)
                && along == (Hash.range(h, 2) == 0 ? 7 : 12) && across == Hash.range(Hash.mix(h), 2)) {
            return POST;
        }
        long l = Hash.of(seed, a, b, 2294 + axis);
        int strip = along - 4;
        if (strip >= 0 && strip % 5 <= 1 && strip <= 11 && across == Hash.range(Hash.mix(l), 2)) {
            return OPEN | lightCode(seed, a, b, Hash.mix(Hash.mix(l))) << 9;
        }
        return OPEN;
    }

    /** Every node is lit: one of its 2 x 2 cells carries the node's light. */
    private static int nodeLight(long seed, int i, int j, int lx, int lz) {
        long h = Hash.of(seed, i, j, 2296);
        if (lx == Hash.range(h, 2) && lz == Hash.range(Hash.mix(h), 2)) {
            return lightCode(seed, i, j, Hash.mix(Hash.mix(h)));
        }
        return 0;
    }

    /** Colour sectors of 4 x 4 nodes, each light its own shade. */
    private static int lightCode(long seed, int i, int j, long h) {
        int colour = Hash.range(Hash.of(seed, Math.floorDiv(i, 4), Math.floorDiv(j, 4), 2297), 3);
        return 1 + colour * 4 + Hash.range(h, 4);
    }

    /**
     * A side wall cell of the open lane (axis, a, b): the + side wall ({@code plus}: the wall at
     * row/column 2 of its cell) or the - side. Mirror panes in frames where an interior serves it
     * (frame posts at 7 and 12 between three panes of four), the exit mirror, or dark panels.
     */
    private static int sideWall(long seed, int axis, int a, int b, boolean plus, int along) {
        if (along == 7 || along == 12) {
            return POST;
        }
        boolean served = plus ? servesLow(seed, axis, a, b) : servesHigh(seed, axis, a, b);
        if (served) {
            int m = axis == AX_X ? MIRROR_Z : MIRROR_X;
            return code(m, m, FRAME);
        }
        if (plus && along >= 8 && along <= 11 && isExitSegment(seed, axis, a, b)) {
            int e = axis == AX_X ? EXIT_Z : EXIT_X;
            return code(e, e, FRAME);
        }
        return SOLID;
    }

    // ------------------------------------------------------------------------------------------
    // The maze
    // ------------------------------------------------------------------------------------------

    /** The node's step toward its tree region's centre: 0 the root, 1 +x, 2 -x, 3 +z, 4 -z. */
    static int parent(long seed, int i, int j) {
        int ci = Math.floorDiv(i, TREE_NODES) * TREE_NODES + TREE_NODES / 2;
        int cj = Math.floorDiv(j, TREE_NODES) * TREE_NODES + TREE_NODES / 2;
        int dx = ci - i;
        int dz = cj - j;
        if (dx == 0 && dz == 0) {
            return 0;
        }
        // Either way at random while both are left: paths to the centre turn often, a maze, not spokes.
        boolean byX = dz == 0 || dx != 0 && Hash.unit(seed, i, j, 2281) < 0.5;
        return byX ? (dx > 0 ? 1 : 2) : (dz > 0 ? 3 : 4);
    }

    /** True if lane X(a, b) (node (a, b) to (a + 1, b)) or Z(a, b) (to (a, b + 1)) is open. */
    static boolean open(long seed, int axis, int a, int b) {
        int a2 = axis == AX_X ? a + 1 : a;
        int b2 = axis == AX_X ? b : b + 1;
        if (Math.floorDiv(a, TREE_NODES) == Math.floorDiv(a2, TREE_NODES)
                && Math.floorDiv(b, TREE_NODES) == Math.floorDiv(b2, TREE_NODES)) {
            int up = axis == AX_X ? 1 : 3;
            if (parent(seed, a, b) == up || parent(seed, a2, b2) == up + 1) {
                return true;
            }
        } else {
            // Two openings between neighbouring tree regions, so the trees join into one maze.
            int along = axis == AX_X ? b : a;
            int r = Math.floorDiv(along, TREE_NODES);
            int ra = Math.floorDiv(axis == AX_X ? a : b, TREE_NODES);
            int first = r * TREE_NODES + Hash.range(Hash.of(seed, ra, r, 2284 + axis * 2), TREE_NODES);
            int second = r * TREE_NODES + Hash.range(Hash.of(seed, ra, r, 2285 + axis * 2), TREE_NODES);
            if (along == first || along == second) {
                return true;
            }
        }
        return Hash.unit(seed, a * 2 + axis, b, 2283) < LOOPS;
    }

    // ------------------------------------------------------------------------------------------
    // Exits
    // ------------------------------------------------------------------------------------------

    /**
     * The exit lane of exit region (rx, rz), packed: {@code ((a * 2 + axis) << 32) | b}. It is the
     * first open lane (east, south, west, north) of the node at the region's exit point; the exit
     * mirror is in its + side wall (south for an X lane, east for a Z lane).
     */
    static long exitLane(long seed, int rx, int rz) {
        int ni = Math.floorDiv(EXITS.exitX(seed, rx, rz), CELL);
        int nj = Math.floorDiv(EXITS.exitZ(seed, rx, rz), CELL);
        int axis;
        int a = ni;
        int b = nj;
        if (open(seed, AX_X, ni, nj)) {
            axis = AX_X;
        } else if (open(seed, AX_Z, ni, nj)) {
            axis = AX_Z;
        } else if (open(seed, AX_X, ni - 1, nj)) {
            axis = AX_X;
            a = ni - 1;
        } else {
            // Every node of a spanning tree has an open lane; this is the last one left.
            axis = AX_Z;
            b = nj - 1;
        }
        return ((long) (a * 2 + axis) << 32) | (b & 0xFFFFFFFFL);
    }

    static boolean isExitSegment(long seed, int axis, int a, int b) {
        int rx = Math.floorDiv(a, EXIT_NODES);
        int rz = Math.floorDiv(b, EXIT_NODES);
        // Cheap test first: the exit lane always touches the node at the exit point.
        int ni = Math.floorDiv(EXITS.exitX(seed, rx, rz), CELL);
        int nj = Math.floorDiv(EXITS.exitZ(seed, rx, rz), CELL);
        if (Math.abs(a - ni) > 1 || Math.abs(b - nj) > 1) {
            return false;
        }
        long lane = exitLane(seed, rx, rz);
        return (int) (lane >> 32) == a * 2 + axis && (int) lane == b;
    }

    /**
     * The corridor block in front of the exit mirror of the exit region containing (x, z):
     * {x, z}. Where /backrooms locate and the exit hints point.
     */
    public static int[] exitFront(long seed, int x, int z) {
        int rx = Math.floorDiv(x, EXIT_NODES * CELL);
        int rz = Math.floorDiv(z, EXIT_NODES * CELL);
        long lane = exitLane(seed, rx, rz);
        int hi = (int) (lane >> 32);
        int axis = Math.floorMod(hi, 2);
        int a = Math.floorDiv(hi, 2);
        int b = (int) lane;
        return axis == AX_X
                ? new int[] {a * CELL + 9, b * CELL + 1}
                : new int[] {a * CELL + 1, b * CELL + 9};
    }

    /** The exit front nearest to (x, z), looking at the surrounding exit regions. */
    public static int[] nearestExit(long seed, int x, int z) {
        int[] raw = EXITS.nearest(seed, x, z);
        return exitFront(seed, raw[0], raw[1]);
    }

    // ------------------------------------------------------------------------------------------
    // Interiors: which lanes' images they hold
    // ------------------------------------------------------------------------------------------

    /** A lane that may have mirrors in its side walls: open, and not the exit lane. */
    private static boolean eligible(long seed, int axis, int a, int b) {
        return open(seed, axis, a, b) && !isExitSegment(seed, axis, a, b);
    }

    /**
     * What interior (i, j) serves, packed: bit 0 orientation (0: the X lanes north and south of
     * it, X(i, j) "low" and X(i, j + 1) "high"; 1: the Z lanes west and east, Z(i, j) and
     * Z(i + 1, j)); bits 1-4 rows given to the low lane's images; bits 5-8 to the high lane's. 14
     * rows in all; a row left between two lanes' images stays solid. The interior behind the exit
     * mirror is solid (nothing behind that glass).
     */
    static int interiorRaw(long seed, int i, int j) {
        int rx = Math.floorDiv(i, EXIT_NODES);
        int rz = Math.floorDiv(j, EXIT_NODES);
        long lane = exitLane(seed, rx, rz);
        if (Math.floorDiv((int) (lane >> 32), 2) == i && (int) lane == j) {
            return 0;
        }
        boolean xLo = eligible(seed, AX_X, i, j);
        boolean xHi = eligible(seed, AX_X, i, j + 1);
        boolean zLo = eligible(seed, AX_Z, i, j);
        boolean zHi = eligible(seed, AX_Z, i + 1, j);
        boolean zOriented = Hash.unit(seed, i, j, 2288) < 0.5;
        if (zOriented ? !(zLo || zHi) : !(xLo || xHi)) {
            zOriented = !zOriented;
        }
        boolean lo = zOriented ? zLo : xLo;
        boolean hi = zOriented ? zHi : xHi;
        double r = Hash.unit(seed, i, j, 2289);
        int low = 0;
        int high = 0;
        if (lo && hi) {
            if (r < 0.3) {
                low = 14;
            } else if (r < 0.6) {
                high = 14;
            } else if (r < 0.725) {
                low = 8;
                high = 5;
            } else if (r < 0.85) {
                low = 5;
                high = 8;
            }
        } else if (lo && r < 0.8) {
            low = 14;
        } else if (hi && r < 0.8) {
            high = 14;
        }
        return (zOriented ? 1 : 0) | low << 1 | high << 5;
    }

    private static boolean zOriented(int raw) {
        return (raw & 1) != 0;
    }

    private static int rawLow(int raw) {
        return (raw >> 1) & 15;
    }

    private static int rawHigh(int raw) {
        return (raw >> 5) & 15;
    }

    /**
     * Rows of interior (i, j) holding its low lane's images. Deeper than one only when that lane's
     * other wall is a mirror too (facing mirrors); one mirror only ever shows one reflection.
     */
    private static int lowRows(long seed, int i, int j, int raw) {
        int n = rawLow(raw);
        if (n == 0) {
            return 0;
        }
        boolean z = zOriented(raw);
        int other = z ? interiorRaw(seed, i - 1, j) : interiorRaw(seed, i, j - 1);
        return zOriented(other) == z && rawHigh(other) > 0 ? n : Math.min(n, 3);
    }

    private static int highRows(long seed, int i, int j, int raw) {
        int n = rawHigh(raw);
        if (n == 0) {
            return 0;
        }
        boolean z = zOriented(raw);
        int other = z ? interiorRaw(seed, i + 1, j) : interiorRaw(seed, i, j + 1);
        return zOriented(other) == z && rawLow(other) > 0 ? n : Math.min(n, 3);
    }

    /** True if lane (axis, a, b)'s + side wall (row/column 2 of its cell) is mirrors. */
    static boolean servesLow(long seed, int axis, int a, int b) {
        int raw = interiorRaw(seed, a, b);
        return zOriented(raw) == (axis == AX_Z) && rawLow(raw) > 0;
    }

    /** True if lane (axis, a, b)'s - side wall is mirrors (served by the interior before it). */
    static boolean servesHigh(long seed, int axis, int a, int b) {
        int raw = axis == AX_X ? interiorRaw(seed, a, b - 1) : interiorRaw(seed, a - 1, b);
        return zOriented(raw) == (axis == AX_Z) && rawHigh(raw) > 0;
    }

    /** Depth of the receding images on each side of lane (axis, a, b): min of both sides, 0 if not facing. */
    static int facingDepth(long seed, int axis, int a, int b) {
        if (!eligible(seed, axis, a, b)) {
            return 0;
        }
        if (!servesLow(seed, axis, a, b) || !servesHigh(seed, axis, a, b)) {
            return 0;
        }
        // The + side images are interior (a, b)'s low rows, the - side ones the high rows of the interior before.
        int ia = axis == AX_X ? a : a - 1;
        int ib = axis == AX_X ? b - 1 : b;
        int plus = (lowRows(seed, a, b, interiorRaw(seed, a, b)) + 2) / 3;
        int minus = (highRows(seed, ia, ib, interiorRaw(seed, ia, ib)) + 2) / 3;
        return Math.min(plus, minus);
    }

    // ------------------------------------------------------------------------------------------
    // False branches
    // ------------------------------------------------------------------------------------------

    /**
     * The false-branch mirror of the closed lane (axis, a, b): 0 none, 1 in the face of its low
     * node (looking + along the axis), 2 in the face of its high node. Only where the face across
     * the node is open (the image is the node and the corridor behind you), and never reflecting
     * the exit lane.
     */
    static int falseMirror(long seed, int axis, int a, int b) {
        if (open(seed, axis, a, b)) {
            return 0;
        }
        long h = Hash.of(seed, a * 2 + axis, b, 2298);
        if (Hash.unit(h) >= FALSE_SHARE) {
            return 0;
        }
        int da = axis == AX_X ? 1 : 0;
        int db = axis == AX_Z ? 1 : 0;
        boolean low = eligibleBehind(seed, axis, a - da, b - db);
        boolean high = eligibleBehind(seed, axis, a + da, b + db);
        if (low && high) {
            return Hash.range(h, 2) == 0 ? 1 : 2;
        }
        return low ? 1 : high ? 2 : 0;
    }

    private static boolean eligibleBehind(long seed, int axis, int a, int b) {
        return open(seed, axis, a, b) && !isExitSegment(seed, axis, a, b);
    }

    // ------------------------------------------------------------------------------------------
    // Image boxes
    // ------------------------------------------------------------------------------------------

    /** Appends the image volumes of cell (i, j) (its interior, the false branches in its two lanes). */
    private static int cellImages(long seed, int i, int j, int minX, int maxX, int minZ, int maxZ, Image[] out, int n) {
        int x0 = i * CELL;
        int z0 = j * CELL;
        int raw = interiorRaw(seed, i, j);
        boolean zo = zOriented(raw);
        for (int side = 0; side < 2; side++) {
            boolean low = side == 0;
            int rows = low ? lowRows(seed, i, j, raw) : highRows(seed, i, j, raw);
            for (int depth = 1; (depth - 1) * 3 < rows; depth++) {
                int o0 = (depth - 1) * 3;
                int o1 = Math.min(depth * 3, rows) - 1;
                int a0 = low ? 3 + o0 : 16 - o1;
                int a1 = low ? 3 + o1 : 16 - o0;
                int base = zo ? (low ? x0 : x0 + CELL) : (low ? z0 : z0 + CELL);
                int flip = (depth & 1) == 1 ? -1 : 1;
                int off = stripeOffset(base, low, depth);
                double plane = low ? base + 2.5 : base - 0.5;
                int bx0 = zo ? x0 + a0 : x0 + 3;
                int bx1 = zo ? x0 + a1 : x0 + 16;
                int bz0 = zo ? z0 + 3 : z0 + a0;
                int bz1 = zo ? z0 + 16 : z0 + a1;
                if (bx1 < minX || bx0 > maxX || bz1 < minZ || bz0 > maxZ) {
                    continue;
                }
                if (n >= out.length) {
                    return n;
                }
                if (zo) {
                    out[n++].set(bx0, bx1, bz0, bz1, flip, off, 1, 0, depth, 0, plane);
                } else {
                    out[n++].set(bx0, bx1, bz0, bz1, 1, 0, flip, off, depth, 1, plane);
                }
            }
        }
        n = falseImage(seed, AX_X, i, j, minX, maxX, minZ, maxZ, out, n);
        return falseImage(seed, AX_Z, i, j, minX, maxX, minZ, maxZ, out, n);
    }

    private static int falseImage(long seed, int axis, int a, int b, int minX, int maxX, int minZ, int maxZ, Image[] out, int n) {
        if (n >= out.length) {
            return n;
        }
        int end = falseMirror(seed, axis, a, b);
        if (end == 0) {
            return n;
        }
        int x0 = a * CELL;
        int z0 = b * CELL;
        int bx0 = axis == AX_X ? x0 + 3 : x0 - 1;
        int bx1 = axis == AX_X ? x0 + 16 : x0 + 2;
        int bz0 = axis == AX_X ? z0 - 1 : z0 + 3;
        int bz1 = axis == AX_X ? z0 + 2 : z0 + 16;
        if (bx1 < minX || bx0 > maxX || bz1 < minZ || bz0 > maxZ) {
            return n;
        }
        int off = falseOffset(axis, a, b, end);
        int base = axis == AX_X ? x0 : z0;
        double plane = end == 1 ? base + 2.5 : base + 17.5;
        if (axis == AX_X) {
            out[n++].set(bx0, bx1, bz0, bz1, -1, off, 1, 0, 1, 0, plane);
        } else {
            out[n++].set(bx0, bx1, bz0, bz1, 1, 0, -1, off, 1, 1, plane);
        }
        return n;
    }

    // ------------------------------------------------------------------------------------------
    // Arrivals and test helpers
    // ------------------------------------------------------------------------------------------

    /**
     * Where someone steps out of a mirror near (x, z): the corridor block against a pane of
     * mirrors (the middle pane of a mirrored side wall), nearest lanes first; {x, z}, or null if
     * there is none within {@code rings} cells.
     */
    @Nullable
    public static int[] mirrorArrival(long seed, int x, int z, int rings) {
        int ci = Math.floorDiv(x, CELL);
        int cj = Math.floorDiv(z, CELL);
        for (int ring = 0; ring <= rings; ring++) {
            for (int di = -ring; di <= ring; di++) {
                for (int dj = -ring; dj <= ring; dj++) {
                    if (Math.max(Math.abs(di), Math.abs(dj)) != ring) {
                        continue;
                    }
                    int a = ci + di;
                    int b = cj + dj;
                    for (int axis = AX_X; axis <= AX_Z; axis++) {
                        if (!open(seed, axis, a, b)) {
                            continue;
                        }
                        int across;
                        if (servesLow(seed, axis, a, b)) {
                            across = 1;
                        } else if (servesHigh(seed, axis, a, b)) {
                            across = 0;
                        } else {
                            continue;
                        }
                        return axis == AX_X
                                ? new int[] {a * CELL + 9, b * CELL + across}
                                : new int[] {a * CELL + across, b * CELL + 9};
                    }
                }
            }
        }
        return null;
    }

    /**
     * The middle of the nearest lane between two facing mirrors at least {@code minDepth} images
     * deep on both sides, within {@code rings} cells of (x, z): {x, z}, or null. For /backrooms
     * and the tests.
     */
    @Nullable
    public static int[] nearestFacingMirrors(long seed, int x, int z, int minDepth, int rings) {
        int ci = Math.floorDiv(x, CELL);
        int cj = Math.floorDiv(z, CELL);
        for (int ring = 0; ring <= rings; ring++) {
            for (int di = -ring; di <= ring; di++) {
                for (int dj = -ring; dj <= ring; dj++) {
                    if (Math.max(Math.abs(di), Math.abs(dj)) != ring) {
                        continue;
                    }
                    int a = ci + di;
                    int b = cj + dj;
                    for (int axis = AX_X; axis <= AX_Z; axis++) {
                        if (facingDepth(seed, axis, a, b) >= minDepth) {
                            return axis == AX_X ? new int[] {a * CELL + 9, b * CELL} : new int[] {a * CELL, b * CELL + 9};
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * The node in front of the nearest false-branch mirror within {@code rings} cells: {x, z} of
     * the node cell facing the glass, or null. For /backrooms and the tests.
     */
    @Nullable
    public static int[] nearestFalseBranch(long seed, int x, int z, int rings) {
        int ci = Math.floorDiv(x, CELL);
        int cj = Math.floorDiv(z, CELL);
        for (int ring = 0; ring <= rings; ring++) {
            for (int di = -ring; di <= ring; di++) {
                for (int dj = -ring; dj <= ring; dj++) {
                    if (Math.max(Math.abs(di), Math.abs(dj)) != ring) {
                        continue;
                    }
                    int a = ci + di;
                    int b = cj + dj;
                    for (int axis = AX_X; axis <= AX_Z; axis++) {
                        int end = falseMirror(seed, axis, a, b);
                        if (end == 0) {
                            continue;
                        }
                        int na = end == 1 ? a : axis == AX_X ? a + 1 : a;
                        int nb = end == 1 ? b : axis == AX_Z ? b + 1 : b;
                        return new int[] {na * CELL, nb * CELL};
                    }
                }
            }
        }
        return null;
    }
}
