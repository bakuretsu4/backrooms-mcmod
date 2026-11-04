package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.layout.Level228Plan.Zone;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * The one finite Level 228 maze of a world, built once per layout seed (see {@link Level228Plan}
 * for the design and the public queries). Everything is rasterised into a column grid at build
 * time (real rooms, their sealed image volumes, the backstage core, the special mirrors' images
 * and the exit hallway), so every later query is an array lookup and allocation-free.
 *
 * <h2>Rooms</h2>
 * A mirror room is a "bay": an opaque backdrop (v = 0, facing the backstage core) and three
 * mirror sides. Its content is mirror-symmetric across every u-line at a multiple of k cells
 * (a pm kaleidoscope along the backdrop), so every interior mirror there is a twin: what you
 * see through it is the real room on the other side. The two side perimeters and the far wall
 * are mirrors backed by sealed volumes that are the room folded across them, to {@link #FOG}
 * blocks. Nothing is mirrored across lines parallel to the backdrop, because the backdrop's
 * image would then have to stand inside the room; the far mirror's image of the backdrop is
 * 2H (60) blocks away, lost in the fog. Room-local block coordinates (bu, bv) map to the world
 * by {@code (ox + ux*bu + vx*bv, oz + uz*bu + vz*bv)}.
 *
 * <h2>Core</h2>
 * The four rooms stand around a 54 x 54 backstage core in a pinwheel (each room touches one
 * core corner and keeps 24 blocks from the next), which is what lets every room's side images
 * run 24 blocks deep without meeting another room or image. The core holds the foyer, the
 * connectors, closets behind the decoy curtains, the final chamber and the sealed images of the
 * foyer's arrival mirror and the chamber's three mirrors. The exit hallway is its own building
 * further east, reached only through the correct mirror.
 */
final class Level228Maze {
    // ---- column types
    static final byte T_VOID = 0;
    /** Full opaque wall block (backstage walls, piers, the dark beyond the images). */
    static final byte T_SOLID = 1;
    static final byte T_FLOOR = 2;
    /** A column (invisible post block + column prop). */
    static final byte T_POST = 3;
    static final byte T_MIRROR = 4;
    static final byte T_GLASS = 5;
    /** Opaque painted panel (thin pane block + panel prop). */
    static final byte T_PANEL = 6;
    /** Black velvet curtain: walk-through, opaque to the view. */
    static final byte T_CURTAIN = 7;
    /** Funhouse distorting mirror: dark-backed, reflects stand-ins only. */
    static final byte T_DISTORT = 8;
    /** Open segment under an arch/fretwork. */
    static final byte T_ARCH = 9;
    /** Open segment in an empty mirror frame (the panel is gone). */
    static final byte T_FRAME = 10;
    /** Invisible collision with no column prop (the ticket booth). */
    static final byte T_BLOCKER = 11;

    // ---- column flags
    static final int C_SEALED = 1;
    /** Reflection parity: room-local u (rooms) or world x (special images). */
    static final int C_FLIP_A = 2;
    /** Reflection parity: room-local v (rooms) or world z (special images). */
    static final int C_FLIP_B = 4;
    /** Deliberate asymmetry (hidden passage, phantom room): exempt from the symmetry tests. */
    static final int C_ANOMALY = 8;
    static final int C_SECRET = 16;
    /** Pane 4 tall (FEET_Y..BAND_Y): the chamber, arrival and hallway mirrors. */
    static final int C_TALL = 32;
    /** Outer wall of a sealed image volume (where the reflection stops, in the fog). */
    static final int C_EDGE = 64;

    /** Grid origin and size: multiples of 16 so grid chunks are world chunks. */
    static final int MIN_X = -64;
    static final int MIN_Z = -64;
    static final int SIZE_X = 256;
    static final int SIZE_Z = 192;
    /** Depth of the sealed images behind perimeter mirrors (the fog hides what lies beyond). */
    static final int FOG = 24;

    // ---- the core and the special places (world block coordinates)
    static final int CORE = 54;
    static final int FOYER_X0 = 4, FOYER_X1 = 20, FOYER_Z0 = 6, FOYER_Z1 = 18;
    /** Arrival mirror: line z = 19, blocks x 11..12; its image fills z 20..37. */
    static final int ARRIVAL_LINE = 19, ARRIVAL_X = 11;
    static final int CHAMBER_X0 = 4, CHAMBER_X1 = 14, CHAMBER_Z0 = 40, CHAMBER_Z1 = 49;
    /** The chamber's mirror wall: line x = 15; mirror i occupies z = 41 + 3i .. 42 + 3i. */
    static final int CHAMBER_LINE = 15, CHAMBER_MIRROR_Z = 41;
    static final int HALL_X0 = 149, HALL_X1 = 154, HALL_Z0 = 1, HALL_Z1 = 36;
    /** Hallway entry mirror: line z = 0, blocks x 151..152. */
    static final int HALL_MIRROR_X = 151;
    /** The door at the end of the hallway (line z = 37, blocks x 151..152) and the vestibule behind it. */
    static final int DOOR_LINE = 37, VEST_X0 = 150, VEST_X1 = 153, VEST_Z0 = 38, VEST_Z1 = 41;

    static final int ROOMS = 4;
    /** ShiftState bits: room r's two shifting classes are bits 2r and 2r+1; its secret panel 8+r. */
    static final int SECRET_BIT0 = 8;
    static final int BITS = 12;

    /** One mirror room (see the class comment). */
    static final class Room {
        final int index;
        final Zone zone;
        final int ox, oz, ux, uz, vx, vz;
        final int k, n, w, h, cc;
        byte[] lineA, lineB, backdrop, far;
        byte[][] uInner, vLine;
        /** Shift class s: line class (0 A, 1 B), row, type when its bit is on. */
        final int[] shiftClass = {-1, -1};
        final int[] shiftRow = {-1, -1};
        final byte[] shiftOn = new byte[2];
        int secretCopy = -1, secretRow = -1, secretCol = -1;
        int phantomU = -1;
        int pathLength;

        Room(int index, Zone zone, int ox, int oz, int ux, int uz, int vx, int vz, int k, int n, int h, int cc) {
            this.index = index;
            this.zone = zone;
            this.ox = ox;
            this.oz = oz;
            this.ux = ux;
            this.uz = uz;
            this.vx = vx;
            this.vz = vz;
            this.k = k;
            this.n = n;
            this.w = k * n;
            this.h = h;
            this.cc = cc;
        }

        int worldX(int bu, int bv) {
            return ox + ux * bu + vx * bv;
        }

        int worldZ(int bu, int bv) {
            return oz + uz * bu + vz * bv;
        }

        int localU(int x, int z) {
            return ux * (x - ox) + uz * (z - oz);
        }

        int localV(int x, int z) {
            return vx * (x - ox) + vz * (z - oz);
        }

        /** Continuous world coordinate -> room-local (block centres map to block centres). */
        double contU(double x, double z) {
            return ux * (x - ox - 0.5) + uz * (z - oz - 0.5) + 0.5;
        }

        double contV(double x, double z) {
            return vx * (x - ox - 0.5) + vz * (z - oz - 0.5) + 0.5;
        }

        double contX(double tu, double tv) {
            return ox + 0.5 + ux * (tu - 0.5) + vx * (tv - 0.5);
        }

        double contZ(double tu, double tv) {
            return oz + 0.5 + uz * (tu - 0.5) + vz * (tv - 0.5);
        }

        /** Room-local cell u of the curtain in copy j (the same fold column in every copy). */
        int curtainU(int j) {
            return j * k + ((j & 1) == 0 ? cc : k - 1 - cc);
        }

        int shiftBit(int s) {
            return 2 * index + s;
        }

        int secretBit() {
            return SECRET_BIT0 + index;
        }
    }

    final long seed;
    final byte[] type = new byte[SIZE_X * SIZE_Z];
    final byte[] alt = new byte[SIZE_X * SIZE_Z];
    final byte[] bit = new byte[SIZE_X * SIZE_Z];
    final byte[] zone = new byte[SIZE_X * SIZE_Z];
    final byte[] flags = new byte[SIZE_X * SIZE_Z];
    final byte[] room = new byte[SIZE_X * SIZE_Z];
    final int[] mirror = new int[SIZE_X * SIZE_Z];
    final int[] canon = new int[SIZE_X * SIZE_Z];
    /** Light at this column: kind | state << 3 | phase << 5 | (y - FEET_Y) << 9, or -1. */
    final short[] light = new short[SIZE_X * SIZE_Z];
    final Room[] rooms = new Room[ROOMS];

    // ---- mirrors (struct of arrays, index = id)
    int mirrorCount;
    byte[] mAxis, mKind, mSurface, mZone, mFront, mBit;
    int[] mFlags, mNoise;
    boolean[] mOnIsMirror;
    double[] mPlane, mMin, mMax, mY1;
    /** Mirror ids per 16x16 chunk (grid-relative chunk index). */
    int[][] mirrorsByChunk;

    // ---- static props (decor, lights, special frames), bucketed by chunk
    static final int PROP_FIELDS = 9;
    int[][] propsByChunk;
    int[] propCountByChunk;

    /** Which exit mirror (0..2) is the accurate one, and each mirror's flaw (-1 for the accurate one). */
    final int answer;
    final int[] flaw = new int[3];

    static final int CHUNKS_X = (SIZE_X + 15) / 16 + 1;
    static final int CHUNKS_Z = (SIZE_Z + 15) / 16 + 1;

    private Level228Maze(long seed) {
        this.seed = seed;
        Arrays.fill(bit, (byte) -1);
        Arrays.fill(room, (byte) -1);
        Arrays.fill(mirror, -1);
        Arrays.fill(light, (short) -1);
        Arrays.fill(zone, (byte) Zone.NONE.ordinal());
        answer = Hash.range(Hash.of(seed, 228, 3, 0x5EA1), 3);
        int a = Hash.range(Hash.of(seed, 228, 4, 0x5EA1), 7);
        int b = (a + 1 + Hash.range(Hash.of(seed, 228, 5, 0x5EA1), 6)) % 7;
        int wrong = 0;
        for (int i = 0; i < 3; i++) {
            flaw[i] = i == answer ? -1 : (wrong++ == 0 ? a : b);
        }
    }

    // ==========================================================================================
    // Cache
    // ==========================================================================================

    private static volatile Level228Maze cached;

    static Level228Maze of(long seed) {
        Level228Maze m = cached;
        if (m != null && m.seed == seed) {
            return m;
        }
        synchronized (Level228Maze.class) {
            m = cached;
            if (m == null || m.seed != seed) {
                m = new Level228Maze(seed);
                m.build();
                cached = m;
            }
            return m;
        }
    }

    // ==========================================================================================
    // Grid access
    // ==========================================================================================

    static boolean inGrid(int x, int z) {
        return x >= MIN_X && z >= MIN_Z && x < MIN_X + SIZE_X && z < MIN_Z + SIZE_Z;
    }

    static int idx(int x, int z) {
        return (x - MIN_X) * SIZE_Z + (z - MIN_Z);
    }

    /** Column type at (x, z) in shift state {@code bits}. */
    byte typeAt(long bits, int x, int z) {
        if (!inGrid(x, z)) {
            return T_VOID;
        }
        int i = idx(x, z);
        int b = bit[i];
        return b >= 0 && (bits >>> b & 1L) != 0 ? alt[i] : type[i];
    }

    static boolean walkableType(byte t) {
        return t == T_FLOOR || t == T_ARCH || t == T_FRAME || t == T_CURTAIN;
    }

    static boolean isPane(byte t) {
        return t == T_MIRROR || t == T_GLASS || t == T_PANEL || t == T_DISTORT;
    }

    boolean walkable(long bits, int x, int z) {
        return walkableType(typeAt(bits, x, z)) && (flags[idx(x, z)] & C_SEALED) == 0;
    }

    Zone zoneAt(int x, int z) {
        return inGrid(x, z) ? ZONES[zone[idx(x, z)]] : Zone.NONE;
    }

    /** Zone.values() copies its array on every call; zoneAt runs on the tick and render paths. */
    static final Zone[] ZONES = Zone.values();
    /** Litter variant 7 is the chamber's ticket stub: random litter elsewhere stays below it. */
    static final int LITTER_VARIANTS = 7;

    // ==========================================================================================
    // Build
    // ==========================================================================================

    private void build() {
        rooms[0] = new Room(0, Zone.CRYSTAL, 0, 0, 1, 0, 0, -1, 3, 3, 10, 2);
        rooms[1] = new Room(1, Zone.ALHAMBRA, CORE, 0, 0, 1, 1, 0, 2, 5, 10, 1);
        rooms[2] = new Room(2, Zone.FUNHOUSE, CORE, CORE, -1, 0, 0, 1, 3, 3, 9, 0);
        rooms[3] = new Room(3, Zone.GLASS, 0, CORE, 0, -1, -1, 0, 5, 2, 10, 0);
        for (Room r : rooms) {
            generate(r);
        }
        for (Room r : rooms) {
            rasterRoom(r);
        }
        buildCore();
        buildHall();
        registerMirrors();
        buildProps();
        indexBits();
    }

    /** Columns (packed x << 16 | z & 0xFFFF) whose blocks depend on each ShiftState bit. */
    int[][] bitColumns;

    private void indexBits() {
        int[] counts = new int[BITS];
        for (byte b : bit) {
            if (b >= 0) {
                counts[b]++;
            }
        }
        bitColumns = new int[BITS][];
        for (int b = 0; b < BITS; b++) {
            bitColumns[b] = new int[counts[b]];
            counts[b] = 0;
        }
        for (int i = 0; i < bit.length; i++) {
            int b = bit[i];
            if (b >= 0) {
                int x = i / SIZE_Z + MIN_X;
                int z = i % SIZE_Z + MIN_Z;
                bitColumns[b][counts[b]++] = x << 16 | z & 0xFFFF;
            }
        }
    }

    // ---- room generation ---------------------------------------------------------------------

    /** Zone flavour: share of closed interior segments that are glass, extra loops, line glass. */
    private static double glassShare(Zone z) {
        return switch (z) {
            case GLASS -> 0.86;
            case ALHAMBRA -> 0.55;
            case FUNHOUSE -> 0.35;
            default -> 0.45;
        };
    }

    private static double loopShare(Zone z) {
        return z == Zone.ALHAMBRA ? 0.30 : 0.10;
    }

    private static double lineGlassShare(Zone z) {
        return z == Zone.GLASS ? 0.40 : 0.08;
    }

    /** A tiny deterministic generator (SplitMix64) for one room attempt. */
    private static final class Rng {
        long s;

        Rng(long s) {
            this.s = s;
        }

        long next() {
            s += 0x9E3779B97F4A7C15L;
            return Hash.mix(s);
        }

        double unit() {
            return Hash.unit(next());
        }

        int range(int n) {
            return Hash.range(next(), n);
        }
    }

    /**
     * Fills the room's fundamental strip with a maze, retrying (deterministically) until the walk
     * from the entrance curtain to the exit curtain is long in every shift state; keeps the best.
     */
    private void generate(Room r) {
        Room best = null;
        int bestLen = -2;
        for (int attempt = 0; attempt < 48; attempt++) {
            Room c = new Room(r.index, r.zone, r.ox, r.oz, r.ux, r.uz, r.vx, r.vz, r.k, r.n, r.h, r.cc);
            fillStrip(c, new Rng(Hash.of(seed, r.index, attempt, 0x228)));
            int len = shortestOverStates(c);
            if (len > bestLen) {
                bestLen = len;
                best = c;
            }
            if (len >= r.w + r.h + r.h / 2) {
                break;
            }
        }
        copyStrip(best, r);
        r.pathLength = bestLen;
        placeSecret(r, new Rng(Hash.of(seed, r.index, 99, 0x5EC)));
        if (r.zone != Zone.GLASS) {
            placePhantom(r, new Rng(Hash.of(seed, r.index, 98, 0xFA7)));
        }
    }

    private static void copyStrip(Room from, Room to) {
        to.lineA = from.lineA;
        to.lineB = from.lineB;
        to.backdrop = from.backdrop;
        to.far = from.far;
        to.uInner = from.uInner;
        to.vLine = from.vLine;
        System.arraycopy(from.shiftClass, 0, to.shiftClass, 0, 2);
        System.arraycopy(from.shiftRow, 0, to.shiftRow, 0, 2);
        System.arraycopy(from.shiftOn, 0, to.shiftOn, 0, 2);
    }

    private static void fillStrip(Room r, Rng rng) {
        int k = r.k;
        int h = r.h;
        r.lineA = new byte[h];
        r.lineB = new byte[h];
        r.uInner = new byte[Math.max(0, k - 1)][h];
        r.vLine = new byte[h + 1][k];
        r.backdrop = new byte[k];
        r.far = new byte[k];
        // Spanning tree of the strip's k x h cells by a randomised depth-first walk: long winding
        // corridors with dead ends, the shape of a real mirror maze.
        boolean[] seen = new boolean[k * h];
        boolean[] openU = new boolean[Math.max(0, k - 1) * h]; // between col i and i+1 at row
        boolean[] openV = new boolean[(h + 1) * k];            // between row j-1 and j at col
        int[] stack = new int[k * h];
        int sp = 0;
        int start = rng.range(k) + k * (h - 1 - rng.range(Math.max(1, h / 3)));
        stack[sp++] = start;
        seen[start] = true;
        int[] cand = new int[4];
        while (sp > 0) {
            int cell = stack[sp - 1];
            int c = cell % k;
            int row = cell / k;
            int nc = 0;
            if (c > 0 && !seen[cell - 1]) cand[nc++] = 0;
            if (c < k - 1 && !seen[cell + 1]) cand[nc++] = 1;
            if (row > 0 && !seen[cell - k]) cand[nc++] = 2;
            if (row < h - 1 && !seen[cell + k]) cand[nc++] = 3;
            if (nc == 0) {
                sp--;
                continue;
            }
            int d = cand[rng.range(nc)];
            int next;
            switch (d) {
                case 0 -> { next = cell - 1; openU[(c - 1) * h + row] = true; }
                case 1 -> { next = cell + 1; openU[c * h + row] = true; }
                case 2 -> { next = cell - k; openV[row * k + c] = true; }
                default -> { next = cell + k; openV[(row + 1) * k + c] = true; }
            }
            seen[next] = true;
            stack[sp++] = next;
        }
        double glass = glassShare(r.zone);
        double loop = loopShare(r.zone);
        for (int i = 0; i < k - 1; i++) {
            for (int row = 0; row < h; row++) {
                r.uInner[i][row] = segment(openU[i * h + row] || rng.unit() < loop, glass, rng);
            }
        }
        for (int j = 1; j < h; j++) {
            for (int c = 0; c < k; c++) {
                r.vLine[j][c] = segment(openV[j * k + c] || rng.unit() < loop, glass, rng);
            }
        }
        double lineGlass = lineGlassShare(r.zone);
        for (int row = 0; row < h; row++) {
            r.lineA[row] = rng.unit() < lineGlass ? T_GLASS : T_MIRROR;
            r.lineB[row] = rng.unit() < lineGlass ? T_GLASS : T_MIRROR;
        }
        // The one fixed opening of each line class lies deep in the room, so every crossing to
        // the next copy is an excursion away from the backdrop and back.
        int deep = h / 2;
        int ra = deep + rng.range(h - deep);
        int rb = deep + rng.range(h - deep);
        r.lineA[ra] = rng.unit() < 0.6 ? T_FRAME : T_ARCH;
        r.lineB[rb] = rng.unit() < 0.6 ? T_FRAME : T_ARCH;
        if (r.zone == Zone.GLASS) {
            // The checkpoint steps out of the first copy's side perimeter mirror at row 0.
            r.lineA[0] = T_MIRROR;
        }
        // Shifting classes. A two-copy room has no interior class-A line: both shift on B.
        int classS0 = r.n >= 3 ? 0 : 1;
        int fixed0 = classS0 == 0 ? ra : rb;
        int s0 = pickRow(rng, h, fixed0, -1, r.zone == Zone.GLASS && classS0 == 0 ? 0 : -1);
        r.shiftClass[0] = classS0;
        r.shiftRow[0] = s0;
        byte[] line0 = classS0 == 0 ? r.lineA : r.lineB;
        line0[s0] = T_FRAME;           // open while the bit is off ...
        r.shiftOn[0] = T_MIRROR;       // ... a mirror once it shifts
        int s1 = pickRow(rng, h, rb, classS0 == 1 ? s0 : -1, -1);
        r.shiftClass[1] = 1;
        r.shiftRow[1] = s1;
        r.lineB[s1] = T_MIRROR;        // a mirror while the bit is off ...
        r.shiftOn[1] = T_FRAME;        // ... an empty frame once it shifts
        for (int c = 0; c < k; c++) {
            r.backdrop[c] = r.zone == Zone.FUNHOUSE && c != r.cc && rng.unit() < 0.6 ? T_DISTORT : T_PANEL;
            r.far[c] = rng.unit() < 0.06 ? T_GLASS : T_MIRROR;
        }
        r.backdrop[r.cc] = T_CURTAIN;
        if (r.zone == Zone.FUNHOUSE && k > 1) {
            r.backdrop[r.cc == 0 ? 1 : 0] = T_DISTORT; // at least one funhouse mirror per copy
        }
    }

    private static int pickRow(Rng rng, int h, int avoidA, int avoidB, int avoidC) {
        for (int i = 0; i < 32; i++) {
            int row = 1 + rng.range(h - 1);
            if (row != avoidA && row != avoidB && row != avoidC) {
                return row;
            }
        }
        for (int row = 1; row < h; row++) {
            if (row != avoidA && row != avoidB && row != avoidC) {
                return row;
            }
        }
        return 1;
    }

    private static byte segment(boolean open, double glass, Rng rng) {
        if (open) {
            return rng.unit() < 0.7 ? T_ARCH : T_FRAME;
        }
        return rng.unit() < glass ? T_GLASS : T_PANEL;
    }

    /** Shortest entrance -> exit walk (cells) over all four shift states of the room; -1 if any state cuts it. */
    private int shortestOverStates(Room r) {
        int min = Integer.MAX_VALUE;
        for (int s = 0; s < 4; s++) {
            long bits = ((s & 1) != 0 ? 1L << r.shiftBit(0) : 0L) | ((s & 2) != 0 ? 1L << r.shiftBit(1) : 0L);
            int len = roomPath(r, bits, r.curtainU(0), r.curtainU(r.n - 1));
            if (len < 0) {
                return -1;
            }
            min = Math.min(min, len);
        }
        return min;
    }

    /** BFS over the room's cells (row 0 cells at u0 and u1) in shift state bits; -1 if unreachable. */
    int roomPath(Room r, long bits, int u0, int u1) {
        int w = r.w;
        int h = r.h;
        int[] dist = new int[w * h];
        Arrays.fill(dist, -1);
        int[] queue = new int[w * h];
        int head = 0;
        int tail = 0;
        queue[tail++] = u0;
        dist[u0] = 0;
        while (head < tail) {
            int cell = queue[head++];
            int u = cell % w;
            int v = cell / w;
            if (u == u1 && v == 0) {
                return dist[cell];
            }
            for (int d = 0; d < 4; d++) {
                int nu = u + (d == 0 ? -1 : d == 1 ? 1 : 0);
                int nv = v + (d == 2 ? -1 : d == 3 ? 1 : 0);
                if (nu < 0 || nv < 0 || nu >= w || nv >= h) {
                    continue;
                }
                int bu = d == 0 ? 3 * u : d == 1 ? 3 * (u + 1) : 3 * u + 1;
                int bv = d == 2 ? 3 * v : d == 3 ? 3 * (v + 1) : 3 * v + 1;
                roomContent(r, bu, bv);
                byte t = cBit >= 0 && (bits >>> cBit & 1L) != 0 ? cAlt : cType;
                int next = nv * w + nu;
                if (walkableType(t) && dist[next] < 0) {
                    dist[next] = dist[cell] + 1;
                    queue[tail++] = next;
                }
            }
        }
        return -1;
    }

    /**
     * The hidden passage: one panel next to a twin line stays a (secret) panel while its twin
     * across the line is an opening, so the "reflection" shows a way through where the real wall
     * is. Pushing the panel opens it for everyone (Level228Rules).
     */
    private void placeSecret(Room r, Rng rng) {
        if (r.n < 2) {
            return;
        }
        for (int attempt = 0; attempt < 64; attempt++) {
            int js = rng.range(r.n - 1);
            int line = js + 1;
            boolean classA = (line & 1) == 0;
            int col = (js & 1) == 0 ? r.k - 1 : 0;
            int row = 2 + rng.range(Math.max(1, r.h - 3));
            if (row >= r.h) {
                continue;
            }
            byte[] l = classA ? r.lineA : r.lineB;
            if (r.vLine[row][col] != T_PANEL || l[row - 1] != T_MIRROR || l[row] != T_MIRROR
                    || isShiftRow(r, classA ? 0 : 1, row - 1) || isShiftRow(r, classA ? 0 : 1, row)) {
                continue;
            }
            r.secretCopy = js;
            r.secretRow = row;
            r.secretCol = col;
            return;
        }
    }

    private static boolean isShiftRow(Room r, int cls, int row) {
        return r.shiftClass[0] == cls && r.shiftRow[0] == row || r.shiftClass[1] == cls && r.shiftRow[1] == row;
    }

    /** A far mirror whose image shows a small lit room that is not there. */
    private static void placePhantom(Room r, Rng rng) {
        for (int attempt = 0; attempt < 32; attempt++) {
            int u = 1 + rng.range(r.w - 2);
            if (r.far[foldCol(r, u)] == T_MIRROR && r.far[foldCol(r, u - 1)] == T_MIRROR && r.far[foldCol(r, u + 1)] == T_MIRROR) {
                r.phantomU = u;
                return;
            }
        }
    }

    /** Strip column of room cell u. */
    static int foldCol(Room r, int u) {
        int m = Math.floorMod(u, 2 * r.k);
        return m < r.k ? m : 2 * r.k - 1 - m;
    }

    // ---- room content ------------------------------------------------------------------------

    // Scratch output of roomContent (build thread only; the maze is built under a lock).
    byte cType, cAlt;
    int cBit, cFlags, cCanon;

    private int canonOf(Room r, int kind, int a, int b) {
        return (int) Hash.of(seed, r.index * 16 + kind, a, b + 0x228);
    }

    /** Content of room-local block (bu, bv) in [0, 3w] x [0, 3h] into the scratch fields. */
    void roomContent(Room r, int bu, int bv) {
        int k3 = 3 * r.k;
        int m = Math.floorMod(bu, 2 * k3);
        boolean flipU = m > k3;
        int su = flipU ? 2 * k3 - m : m;
        int copy = Math.min(bu / k3, r.n - 1);
        cFlags = flipU ? C_FLIP_A : 0;
        cAlt = T_VOID;
        cBit = -1;
        boolean uLine = su % 3 == 0;
        boolean vLine = bv % 3 == 0;
        if (uLine && vLine) {
            cType = T_POST;
            cCanon = canonOf(r, 1, su, bv);
        } else if (uLine) {
            int row = bv / 3;
            if (su == 0 || su == k3) {
                int cls = su == 0 ? 0 : 1;
                byte t = cls == 0 ? r.lineA[row] : r.lineB[row];
                for (int s = 0; s < 2; s++) {
                    if (r.shiftClass[s] == cls && r.shiftRow[s] == row) {
                        cBit = r.shiftBit(s);
                        cAlt = r.shiftOn[s];
                    }
                }
                if (bu == 0 || bu == 3 * r.w) {
                    // A perimeter opening would lead into the sealed image: clean glass there
                    // (it looks like the empty frame it is in every other copy).
                    if (walkableType(t)) {
                        t = T_GLASS;
                    }
                    if (cBit >= 0 && walkableType(cAlt)) {
                        cAlt = T_GLASS;
                    }
                }
                cType = t;
                cCanon = canonOf(r, 2 + cls, row, 0);
            } else {
                cType = r.uInner[su / 3 - 1][row];
                cCanon = canonOf(r, 4, su, row);
            }
        } else if (vLine) {
            int j = bv / 3;
            int c = su / 3;
            byte t = j == 0 ? r.backdrop[c] : j == r.h ? r.far[c] : r.vLine[j][c];
            cCanon = canonOf(r, 5, j, c);
            if (r.secretCopy >= 0 && j == r.secretRow && c == r.secretCol) {
                if (copy == r.secretCopy) {
                    t = T_PANEL;
                    cAlt = T_FRAME;
                    cBit = r.secretBit();
                    cFlags |= C_SECRET | C_ANOMALY;
                } else if (copy == r.secretCopy + 1) {
                    t = T_ARCH;
                    cFlags |= C_ANOMALY;
                }
            }
            cType = t;
        } else {
            cType = T_FLOOR;
            cCanon = canonOf(r, 6, su / 3, bv / 3);
        }
    }

    /** Room-local block (bu, bv) anywhere in the room or its sealed images, folded into the room. */
    void foldedContent(Room r, int bu, int bv) {
        int w3 = 3 * r.w;
        int h3 = 3 * r.h;
        int m = Math.floorMod(bu, 2 * w3);
        boolean fu = m > w3;
        int bu2 = fu ? 2 * w3 - m : m;
        boolean fv = bv > h3;
        int bv2 = fv ? 2 * h3 - bv : bv;
        roomContent(r, bu2, bv2);
        if (fu) {
            cFlags ^= C_FLIP_A;
        }
        if (fv) {
            cFlags ^= C_FLIP_B;
        }
    }

    private void rasterRoom(Room r) {
        int w3 = 3 * r.w;
        int h3 = 3 * r.h;
        for (int bu = -FOG; bu <= w3 + FOG; bu++) {
            for (int bv = 0; bv <= h3 + FOG; bv++) {
                int x = r.worldX(bu, bv);
                int z = r.worldZ(bu, bv);
                if (!inGrid(x, z)) {
                    continue;
                }
                int i = idx(x, z);
                boolean sealed = bu < 0 || bu > w3 || bv > h3;
                boolean edge = bu == -FOG || bu == w3 + FOG || bv == h3 + FOG;
                if (sealed && type[i] != T_VOID && (flags[i] & C_EDGE) == 0) {
                    continue; // another room's image already stands here (a shared boundary line)
                }
                if (edge) {
                    if (type[i] == T_VOID) {
                        set(i, T_SOLID, r.zone, r.index, C_SEALED | C_EDGE, 0);
                    }
                    continue;
                }
                foldedContent(r, bu, bv);
                set(i, cType, r.zone, r.index, cFlags | (sealed ? C_SEALED : 0), cCanon);
                alt[i] = cAlt;
                bit[i] = (byte) cBit;
            }
        }
        if (r.phantomU >= 0) {
            rasterPhantom(r);
        }
    }

    /**
     * The room that isn't there: right behind one far mirror the image is replaced by a small
     * closed parlour (two cells deep, three wide) with a lit bulb and a chair.
     */
    private void rasterPhantom(Room r) {
        int h3 = 3 * r.h;
        int u0 = 3 * (r.phantomU - 1);
        int u1 = 3 * (r.phantomU + 2);
        for (int bu = u0; bu <= u1; bu++) {
            for (int bv = h3 + 1; bv <= h3 + 6; bv++) {
                int x = r.worldX(bu, bv);
                int z = r.worldZ(bu, bv);
                int i = idx(x, z);
                boolean edgeU = bu == u0 || bu == u1;
                boolean edgeV = bv == h3 + 6;
                byte t;
                if ((edgeU || edgeV) && bu % 3 == 0 && bv % 3 == 0) {
                    t = T_POST;
                } else if (edgeU || edgeV) {
                    t = T_PANEL;
                } else {
                    t = T_FLOOR;
                }
                set(i, t, r.zone, r.index, C_SEALED | C_ANOMALY | (flags[i] & (C_FLIP_A | C_FLIP_B)), canonOf(r, 7, bu, bv));
                alt[i] = T_VOID;
                bit[i] = -1;
            }
        }
    }

    private void set(int i, byte t, Zone z, int roomIndex, int f, int c) {
        type[i] = t;
        zone[i] = (byte) z.ordinal();
        room[i] = (byte) roomIndex;
        flags[i] = (byte) f;
        canon[i] = c;
    }

    // ---- the backstage core ------------------------------------------------------------------

    private void fillRect(int x0, int z0, int x1, int z1, byte t, Zone z) {
        for (int x = x0; x <= x1; x++) {
            for (int zz = z0; zz <= z1; zz++) {
                int i = idx(x, zz);
                set(i, t, z, -1, 0, (int) Hash.of(seed, x, zz, 0xC0E));
                alt[i] = T_VOID;
                bit[i] = -1;
            }
        }
    }

    private void buildCore() {
        // Backstage mass wherever no room stands (the room backdrops on the core edge stay).
        for (int x = 0; x <= CORE; x++) {
            for (int z = 0; z <= CORE; z++) {
                int i = idx(x, z);
                if (type[i] == T_VOID) {
                    set(i, T_SOLID, Zone.BACKSTAGE, -1, 0, (int) Hash.of(seed, x, z, 0xC0E));
                }
            }
        }
        // Foyer, the dark queue passage behind the facade, the facade's doorway.
        fillRect(FOYER_X0, FOYER_Z0, FOYER_X1, FOYER_Z1, T_FLOOR, Zone.FOYER);
        fillRect(7, 1, 11, 4, T_FLOOR, Zone.FOYER);
        fillRect(8, 5, 9, 5, T_FLOOR, Zone.FOYER);
        // Ticket booth corners: collision only (the booth is a prop).
        fillRect(15, 7, 16, 8, T_BLOCKER, Zone.FOYER);
        // C1: Crystal's exit -> Alhambra's entrance, along the north edge; a storage room off it.
        fillRect(25, 1, 53, 2, T_FLOOR, Zone.BACKSTAGE);
        fillRect(52, 3, 53, 5, T_FLOOR, Zone.BACKSTAGE);
        fillRect(31, 4, 38, 9, T_FLOOR, Zone.BACKSTAGE);
        fillRect(34, 3, 35, 3, T_FLOOR, Zone.BACKSTAGE);
        // Closets behind the Alhambra's decoy curtains.
        for (int j = 1; j < rooms[1].n - 1; j++) {
            int u = rooms[1].curtainU(j);
            fillRect(52, 3 * u + 1, 53, 3 * u + 2, T_FLOOR, Zone.BACKSTAGE);
        }
        // C2: Alhambra's exit -> Funhouse's entrance, down the east edge; a staff room off it.
        fillRect(52, 28, 53, 53, T_FLOOR, Zone.BACKSTAGE);
        fillRect(46, 36, 50, 41, T_FLOOR, Zone.BACKSTAGE);
        fillRect(51, 38, 51, 39, T_FLOOR, Zone.BACKSTAGE);
        // Closet behind the Funhouse's decoy curtain.
        for (int j = 1; j < rooms[2].n - 1; j++) {
            int u = rooms[2].curtainU(j);
            fillRect(CORE - 3 * u - 2, 52, CORE - 3 * u - 1, 53, T_FLOOR, Zone.BACKSTAGE);
        }
        // C3: Funhouse's exit -> the Glass Labyrinth's entrance, along the south edge.
        fillRect(1, 52, 35, 53, T_FLOOR, Zone.BACKSTAGE);
        // C4: the Glass Labyrinth's exit -> the final chamber, down the west edge.
        fillRect(1, 25, 2, 46, T_FLOOR, Zone.BACKSTAGE);
        fillRect(3, 44, 3, 45, T_FLOOR, Zone.CHAMBER);
        // The final chamber: octagonal (west corners cut two deep, east corners one).
        for (int x = CHAMBER_X0; x <= CHAMBER_X1; x++) {
            for (int z = CHAMBER_Z0; z <= CHAMBER_Z1; z++) {
                int dz = Math.min(z - CHAMBER_Z0, CHAMBER_Z1 - z);
                boolean cut = x - CHAMBER_X0 + dz < 2 || CHAMBER_X1 - x + dz < 1;
                fillRect(x, z, x, z, cut ? T_SOLID : T_FLOOR, Zone.CHAMBER);
            }
        }
        for (int x = CHAMBER_X0 - 1; x <= CHAMBER_LINE; x++) {
            for (int z = CHAMBER_Z0 - 1; z <= CHAMBER_Z1 + 1; z++) {
                int i = idx(x, z);
                if (type[i] == T_SOLID) {
                    zone[i] = (byte) Zone.CHAMBER.ordinal();
                }
            }
        }
        // The three exit mirrors on the east wall, with piers between.
        for (int m = 0; m < 3; m++) {
            for (int b = 0; b < 2; b++) {
                int i = idx(CHAMBER_LINE, CHAMBER_MIRROR_Z + 3 * m + b);
                set(i, T_MIRROR, Zone.CHAMBER, -1, C_TALL, (int) Hash.of(seed, 228, m, 0xE1));
            }
        }
        // The foyer's arrival mirror (south wall), tall.
        for (int b = 0; b < 2; b++) {
            set(idx(ARRIVAL_X + b, ARRIVAL_LINE), T_MIRROR, Zone.FOYER, -1, C_TALL, (int) Hash.of(seed, 228, 7, 0xA1));
        }
        // Sealed images: the foyer behind its arrival mirror, the chamber behind its three.
        copyImage(3, 20, 21, 37, true, 2 * ARRIVAL_LINE, Zone.FOYER);
        copyImage(16, 39, 29, 50, false, 2 * CHAMBER_LINE, Zone.CHAMBER);
    }

    /**
     * Fills [x0..x1] x [z0..z1] with the reflection of the real columns across the line block
     * {@code k/2} (block b -> k - b), along z if {@code alongZ}, else along x.
     */
    private void copyImage(int x0, int z0, int x1, int z1, boolean alongZ, int k, Zone z) {
        for (int x = x0; x <= x1; x++) {
            for (int zz = z0; zz <= z1; zz++) {
                int sx = alongZ ? x : k - x;
                int sz = alongZ ? k - zz : zz;
                int s = idx(sx, sz);
                int i = idx(x, zz);
                type[i] = type[s] == T_POST ? T_BLOCKER : type[s];
                alt[i] = alt[s];
                bit[i] = bit[s];
                zone[i] = (byte) z.ordinal();
                room[i] = -1;
                flags[i] = (byte) ((flags[s] & ~(C_SEALED | C_SECRET)) | C_SEALED);
                flags[i] ^= (byte) (alongZ ? C_FLIP_B : C_FLIP_A);
                canon[i] = canon[s];
            }
        }
    }

    // ---- the exit hallway --------------------------------------------------------------------

    private void buildHall() {
        fillRect(HALL_X0 - 1, -HALL_Z1 - 1, HALL_X1 + 1, VEST_Z1 + 1, T_SOLID, Zone.EXIT_HALL);
        fillRect(HALL_X0, HALL_Z0, HALL_X1, HALL_Z1, T_FLOOR, Zone.EXIT_HALL);
        fillRect(HALL_MIRROR_X, DOOR_LINE, HALL_MIRROR_X + 1, DOOR_LINE, T_FLOOR, Zone.EXIT_HALL);
        fillRect(VEST_X0, VEST_Z0, VEST_X1, VEST_Z1, T_FLOOR, Zone.EXIT_HALL);
        for (int b = 0; b < 2; b++) {
            set(idx(HALL_MIRROR_X + b, 0), T_MIRROR, Zone.EXIT_HALL, -1, C_TALL, (int) Hash.of(seed, 228, 8, 0xA1));
        }
        copyImage(HALL_X0 - 1, -HALL_Z1, HALL_X1 + 1, -1, true, 0, Zone.EXIT_HALL);
    }

    // ==========================================================================================
    // Mirrors
    // ==========================================================================================

    private static final int MIRROR_CAP = 6000;

    private void registerMirrors() {
        mAxis = new byte[MIRROR_CAP];
        mKind = new byte[MIRROR_CAP];
        mSurface = new byte[MIRROR_CAP];
        mZone = new byte[MIRROR_CAP];
        mFront = new byte[MIRROR_CAP];
        mBit = new byte[MIRROR_CAP];
        mFlags = new int[MIRROR_CAP];
        mNoise = new int[MIRROR_CAP];
        mOnIsMirror = new boolean[MIRROR_CAP];
        mPlane = new double[MIRROR_CAP];
        mMin = new double[MIRROR_CAP];
        mMax = new double[MIRROR_CAP];
        mY1 = new double[MIRROR_CAP];
        // The special mirrors first, so their ids are small and stable.
        for (int m = 0; m < 3; m++) {
            int z0 = CHAMBER_MIRROR_Z + 3 * m;
            addMirror(0, CHAMBER_LINE, z0, Level228Plan.K_EXIT_A + m, Zone.CHAMBER, 0, -1, -1, false);
        }
        addMirror(1, ARRIVAL_LINE, ARRIVAL_X, Level228Plan.K_SEALED, Zone.FOYER, Level228Plan.MF_ARRIVAL, -1, -1, false);
        addMirror(1, 0, HALL_MIRROR_X, Level228Plan.K_SEALED, Zone.EXIT_HALL, Level228Plan.MF_HALL, 1, -1, false);
        for (int gx = 0; gx < SIZE_X; gx++) {
            for (int gz = 0; gz < SIZE_Z; gz++) {
                int i = gx * SIZE_Z + gz;
                if (room[i] < 0 || mirror[i] >= 0) {
                    continue;
                }
                byte t = type[i];
                boolean reflects = t == T_MIRROR || t == T_DISTORT || bit[i] >= 0 && alt[i] == T_MIRROR;
                if (!reflects) {
                    continue;
                }
                int x = gx + MIN_X;
                int z = gz + MIN_Z;
                registerRoomMirror(rooms[room[i]], x, z, i);
            }
        }
        mirrorsByChunk = new int[CHUNKS_X * CHUNKS_Z][];
        int[] counts = new int[CHUNKS_X * CHUNKS_Z];
        for (int id = 0; id < mirrorCount; id++) {
            counts[mirrorChunk(id)]++;
        }
        for (int c = 0; c < counts.length; c++) {
            mirrorsByChunk[c] = new int[counts[c]];
            counts[c] = 0;
        }
        for (int id = 0; id < mirrorCount; id++) {
            int c = mirrorChunk(id);
            mirrorsByChunk[c][counts[c]++] = id;
        }
    }

    private int mirrorChunk(int id) {
        double cx = mAxis[id] == 0 ? mPlane[id] : (mMin[id] + mMax[id]) * 0.5;
        double cz = mAxis[id] == 0 ? (mMin[id] + mMax[id]) * 0.5 : mPlane[id];
        return chunkIndex((int) Math.floor(cx), (int) Math.floor(cz));
    }

    static int chunkIndex(int x, int z) {
        int cx = Math.floorDiv(x - MIN_X, 16);
        int cz = Math.floorDiv(z - MIN_Z, 16);
        if (cx < 0 || cz < 0 || cx >= CHUNKS_X || cz >= CHUNKS_Z) {
            return -1;
        }
        return cx * CHUNKS_Z + cz;
    }

    /** Registers the segment through room column (x, z). */
    private void registerRoomMirror(Room r, int x, int z, int i) {
        boolean planeX = Math.floorMod(x, 3) == 0; // a line of constant x: the pane runs along z
        int axis = planeX ? 0 : 1;
        int line = planeX ? x : z;
        int first = planeX ? z - (Math.floorMod(z, 3) - 1) : x - (Math.floorMod(x, 3) - 1);
        int bu = r.localU(x, z);
        int bv = r.localV(x, z);
        boolean sealed = (flags[i] & C_SEALED) != 0;
        boolean uLine = Math.floorMod(bu, 3) == 0;
        int kind;
        int f = sealed ? Level228Plan.MF_IMAGE : 0;
        int front = 0;
        if (type[i] == T_DISTORT) {
            kind = Level228Plan.K_DISTORT;
            front = frontSign(r, axis, 0, 1);
        } else if (uLine && (bu == 0 || bu == 3 * r.w) || !uLine && bv == 3 * r.h) {
            kind = Level228Plan.K_SEALED;
            f |= Level228Plan.MF_PERIMETER;
            front = uLine ? frontSign(r, axis, bu == 0 ? 1 : -1, 0) : frontSign(r, axis, 0, -1);
        } else if (sealed && !uLine && Math.floorMod(bv, 3 * r.h) == 0) {
            kind = Level228Plan.K_SEALED; // the far line's own images
        } else if (sealed && uLine && Math.floorMod(bu, 3 * r.w) == 0) {
            kind = Level228Plan.K_SEALED; // the perimeters' images
        } else {
            kind = Level228Plan.K_TWIN;
        }
        if (!sealed) {
            if (r.zone == Zone.GLASS && uLine && bu == 0 && bv / 3 == 0) {
                f |= Level228Plan.MF_CHECKPOINT;
            }
            if (r.phantomU >= 0 && !uLine && bv == 3 * r.h && bu / 3 == r.phantomU) {
                f |= Level228Plan.MF_PHANTOM;
            }
            if (r.secretCopy >= 0 && uLine && bu == 3 * r.k * (r.secretCopy + 1)
                    && (bv / 3 == r.secretRow - 1 || bv / 3 == r.secretRow)) {
                f |= Level228Plan.MF_HIDDEN;
            }
        }
        int b = bit[i];
        boolean onIsMirror = b >= 0 && alt[i] == T_MIRROR;
        if (b >= 0) {
            f |= Level228Plan.MF_SHIFTING;
        }
        int id = mirrorCount++;
        mAxis[id] = (byte) axis;
        mPlane[id] = line + 0.5;
        mMin[id] = first;
        mMax[id] = first + 2;
        mY1[id] = Level228Plan.FEET_Y + 2.92;
        mKind[id] = (byte) kind;
        mZone[id] = (byte) r.zone.ordinal();
        mFlags[id] = f;
        mFront[id] = (byte) front;
        mBit[id] = (byte) b;
        mOnIsMirror[id] = onIsMirror;
        mNoise[id] = canon[i];
        mSurface[id] = (byte) surfaceOf(r.zone, kind, canon[i]);
        for (int s = 0; s < 2; s++) {
            int xx = planeX ? line : first + s;
            int zz = planeX ? first + s : line;
            mirror[idx(xx, zz)] = id;
        }
    }

    /** World sign (+1/-1) along the pane's normal axis of room-local direction (du, dv). */
    private static int frontSign(Room r, int axis, int du, int dv) {
        int wx = r.ux * du + r.vx * dv;
        int wz = r.uz * du + r.vz * dv;
        return axis == 0 ? Integer.signum(wx) : Integer.signum(wz);
    }

    private static int surfaceOf(Zone zone, int kind, int noise) {
        double u = Hash.unit(Hash.mix(noise * 0x9E37L + 17));
        if (kind == Level228Plan.K_DISTORT) {
            return u < 0.34 ? Level228Plan.S_DISTORT_CONCAVE : u < 0.67 ? Level228Plan.S_DISTORT_CONVEX : Level228Plan.S_DISTORT_WAVE;
        }
        if (zone == Zone.GLASS) {
            return u < 0.6 ? Level228Plan.S_CLEAN : u < 0.9 ? Level228Plan.S_TARNISHED : Level228Plan.S_CRACKED;
        }
        return u < 0.30 ? Level228Plan.S_CLEAN : u < 0.72 ? Level228Plan.S_TARNISHED : u < 0.90 ? Level228Plan.S_CRACKED : Level228Plan.S_SHATTERED;
    }

    /** A special (non-room) 2-block mirror on a line; ids in call order. */
    private void addMirror(int axis, int line, int first, int kind, Zone z, int f, int front, int b, boolean onIsMirror) {
        int id = mirrorCount++;
        mAxis[id] = (byte) axis;
        mPlane[id] = line + 0.5;
        mMin[id] = first;
        mMax[id] = first + 2;
        mY1[id] = Level228Plan.FEET_Y + 3.6;
        mKind[id] = (byte) kind;
        mZone[id] = (byte) z.ordinal();
        mFlags[id] = f;
        mFront[id] = (byte) front;
        mBit[id] = (byte) b;
        mOnIsMirror[id] = onIsMirror;
        mNoise[id] = (int) Hash.of(seed, line, first, 0x51D);
        mSurface[id] = (byte) (kind >= Level228Plan.K_EXIT_A && kind <= Level228Plan.K_EXIT_C
                ? Level228Plan.S_TARNISHED : Level228Plan.S_CLEAN);
        for (int s = 0; s < 2; s++) {
            mirror[axis == 0 ? idx(line, first + s) : idx(first + s, line)] = id;
        }
    }

    boolean mirrorActive(int id, long bits) {
        int b = mBit[id];
        if (b < 0) {
            return true;
        }
        return ((bits >>> b & 1L) != 0) == mOnIsMirror[id];
    }

    // ==========================================================================================
    // Static props
    // ==========================================================================================

    private int[] pBuf = new int[PROP_FIELDS * 4096];
    private int pCount;

    /** Footprint centre of a prop type relative to its anchor block centre, along local X / Z (x2). */
    static int footX2(int type) {
        return switch (type) {
            case Level228Plan.P_PANEL, Level228Plan.P_OPENING, Level228Plan.P_CURTAIN, Level228Plan.P_EXIT_DOOR,
                    Level228Plan.P_CHAMBER_MIRROR, Level228Plan.P_ARRIVAL_MIRROR, Level228Plan.P_TICKET_BOOTH,
                    Level228Plan.P_MIRROR_STACK, Level228Plan.P_BUNTING -> 1;
            case Level228Plan.P_FLOOR, Level228Plan.P_CEILING -> 3;
            case Level228Plan.P_FACADE -> 8;
            case Level228Plan.P_BANNER -> 2;
            default -> 0;
        };
    }

    static int footZ2(int type) {
        return switch (type) {
            case Level228Plan.P_FLOOR, Level228Plan.P_CEILING -> 3;
            case Level228Plan.P_TICKET_BOOTH -> 1;
            default -> 0;
        };
    }

    /** Rotation r applied to local (i, j): clockwise seen from above (rot1: +X -> +Z). */
    static int rotX(int r, int i, int j) {
        return switch (r & 3) {
            case 0 -> i;
            case 1 -> -j;
            case 2 -> -i;
            default -> j;
        };
    }

    static int rotZ(int r, int i, int j) {
        return switch (r & 3) {
            case 0 -> j;
            case 1 -> i;
            case 2 -> -j;
            default -> -i;
        };
    }

    /** Rotation whose front (local -Z) points along world (fx, fz). */
    static int rotOfFront(int fx, int fz) {
        if (fz < 0) return 0;
        if (fx > 0) return 1;
        if (fz > 0) return 2;
        return 3;
    }

    private void addProp(int t, int variant, int x, int y, int z, int rot, int f, int aux, int noise) {
        if (pCount * PROP_FIELDS + PROP_FIELDS > pBuf.length) {
            pBuf = Arrays.copyOf(pBuf, pBuf.length * 2);
        }
        int o = pCount++ * PROP_FIELDS;
        pBuf[o] = t;
        pBuf[o + 1] = variant;
        pBuf[o + 2] = x;
        pBuf[o + 3] = y;
        pBuf[o + 4] = z;
        pBuf[o + 5] = rot & 3;
        pBuf[o + 6] = f;
        pBuf[o + 7] = aux;
        pBuf[o + 8] = noise;
    }

    /**
     * Adds the prop {@code (t, x, z, rot, flip)} mapped by the affine map p -> A p + b on
     * doubled coordinates (A = [[a, b2],[c, d]] with entries -1/0/1; bx2, bz2 = 2 b). The FLIP bit
     * toggles with every reflection, the rotation follows the front, the anchor follows the
     * footprint centre.
     */
    private void addMapped(int t, int variant, int x, int y, int z, int rot, int f, int aux, int noise,
                           int a, int b2, int c, int d, int bx2, int bz2) {
        int fx2 = footX2(t);
        int fz2 = footZ2(t);
        // Footprint centre, doubled: 2 * (anchor + 0.5 + R(fc)).
        int px2 = 2 * x + 1 + rotX(rot, fx2, fz2);
        int pz2 = 2 * z + 1 + rotZ(rot, fx2, fz2);
        int qx2 = a * px2 + b2 * pz2 + bx2;
        int qz2 = c * px2 + d * pz2 + bz2;
        // Front (local -Z) through the rotation, then the map.
        int frx = rotX(rot, 0, -1);
        int frz = rotZ(rot, 0, -1);
        int nr = rotOfFront(a * frx + b2 * frz, c * frx + d * frz);
        boolean det = a * d - b2 * c < 0;
        int nf = det ? f ^ Level228Plan.PF_FLIP : f;
        int nx = (qx2 - 1 - rotX(nr, fx2, fz2)) >> 1;
        int nz = (qz2 - 1 - rotZ(nr, fx2, fz2)) >> 1;
        addProp(t, variant, nx, y, nz, nr, nf, aux, noise);
    }

    /** Adds the prop and its image across a world line block K/2 (x if alongX, else z): b -> K - b. */
    private void addWithImage(int t, int variant, int x, int y, int z, int rot, int f, int aux, int noise,
                              boolean imageAlongZ, int k) {
        addProp(t, variant, x, y, z, rot, f, aux, noise);
        addImage(t, variant, x, y, z, rot, f, aux, noise, imageAlongZ, k);
    }

    private void addImage(int t, int variant, int x, int y, int z, int rot, int f, int aux, int noise,
                          boolean imageAlongZ, int k) {
        // Block b -> K - b is point p -> K + 1 - p, doubled: 2p -> 2K + 2 - 2p.
        if (imageAlongZ) {
            addMapped(t, variant, x, y, z, rot, f, aux, noise, 1, 0, 0, -1, 0, 2 * k + 2);
        } else {
            addMapped(t, variant, x, y, z, rot, f, aux, noise, -1, 0, 0, 1, 2 * k + 2, 0);
        }
    }

    static int lightFlags(int kind, int state, int phase) {
        return kind << Level228Plan.PF_LIGHT_KIND_SHIFT | state << Level228Plan.PF_LIGHT_STATE_SHIFT
                | (phase & 15) << Level228Plan.PF_LIGHT_PHASE_SHIFT;
    }

    private void buildProps() {
        for (Room r : rooms) {
            roomProps(r);
        }
        foyerProps();
        backstageProps();
        chamberProps();
        hallProps();
        // Bucket by chunk, and the light blocks of every live or failing light.
        propsByChunk = new int[CHUNKS_X * CHUNKS_Z][];
        propCountByChunk = new int[CHUNKS_X * CHUNKS_Z];
        for (int p = 0; p < pCount; p++) {
            int c = chunkIndex(pBuf[p * PROP_FIELDS + 2], pBuf[p * PROP_FIELDS + 4]);
            if (c >= 0) {
                propCountByChunk[c]++;
            }
        }
        for (int c = 0; c < propsByChunk.length; c++) {
            propsByChunk[c] = new int[propCountByChunk[c] * PROP_FIELDS];
            propCountByChunk[c] = 0;
        }
        for (int p = 0; p < pCount; p++) {
            int o = p * PROP_FIELDS;
            int x = pBuf[o + 2];
            int z = pBuf[o + 4];
            int c = chunkIndex(x, z);
            if (c < 0) {
                continue;
            }
            System.arraycopy(pBuf, o, propsByChunk[c], propCountByChunk[c]++ * PROP_FIELDS, PROP_FIELDS);
            int t = pBuf[o];
            if (t == Level228Plan.P_BULB || t == Level228Plan.P_SCONCE || t == Level228Plan.P_EXIT_SIGN) {
                int f = pBuf[o + 6];
                int state = f >> Level228Plan.PF_LIGHT_STATE_SHIFT & 3;
                if (state != Level228Plan.LIGHT_DEAD && inGrid(x, z)) {
                    setLight(x, pBuf[o + 3], z, f >> Level228Plan.PF_LIGHT_KIND_SHIFT & 7, state,
                            f >> Level228Plan.PF_LIGHT_PHASE_SHIFT & 15);
                }
            }
        }
        pBuf = null;
    }

    private void setLight(int x, int y, int z, int kind, int state, int phase) {
        int i = idx(x, z);
        if (!walkableType(type[i]) && type[i] != T_FLOOR) {
            return;
        }
        light[i] = (short) (kind | state << 3 | (phase & 15) << 5 | (y - Level228Plan.FEET_Y) << 9);
    }

    // ---- room decor --------------------------------------------------------------------------

    /**
     * The decor of one room: generated once in copy 0 (room-local, canonical per strip cell) and
     * then placed in every copy and every sealed image by the same reflections the blocks went
     * through, so reflections show it exactly (mirrored: posters read backwards).
     */
    private void roomProps(Room r) {
        int start = pCount;
        int k3 = 3 * r.k;
        Zone z = r.zone;
        int zv = z.ordinal();
        // Crystal and Funhouse: a bulb over every other cell (a checkerboard of the strip), a
        // quarter dead. The Alhambra keeps its sparser, deader light. The Glass Labyrinth is
        // dark: a few dead bulbs for show, and a fixed grid of cold spills (rows 1, 5, 8, the 2nd
        // and 4th columns) so no corridor is more than ~6-7 blocks from one.
        boolean checker = z == Zone.CRYSTAL || z == Zone.FUNHOUSE;
        double bulbDensity = z == Zone.GLASS ? 0.08 : z == Zone.ALHAMBRA ? 0.30 : 0.0;
        int kind = z == Zone.GLASS ? Level228Plan.LIGHT_COLD : Level228Plan.LIGHT_BULB;
        // Local props are authored with FLIP set: the room-local frame is a reflection of the
        // world for every room (det -1), so copy 0 then comes out unflipped in the world.
        int author = Level228Plan.PF_FLIP;
        for (int c = 0; c < r.k; c++) {
            for (int row = 0; row < r.h; row++) {
                long hsh = Hash.of(seed, r.index * 64 + c, row, 0xDEC0);
                int bu = 3 * c + 1 + (int) (hsh >>> 60 & 1);
                int bv = 3 * row + 1 + (int) (hsh >>> 61 & 1);
                double u = Hash.unit(hsh);
                boolean glassSpill = z == Zone.GLASS && (row == 1 || row == 5 || row == 8) && (c == 1 || c == r.k - 2);
                if (glassSpill || (checker ? (c + row) % 2 == 0 : u < bulbDensity)) {
                    double s = Hash.unit(Hash.mix(hsh + 1));
                    int state;
                    if (glassSpill) {
                        state = s < 0.15 ? Level228Plan.LIGHT_FAILING : Level228Plan.LIGHT_LIT;
                    } else if (z == Zone.GLASS) {
                        state = Level228Plan.LIGHT_DEAD;
                    } else {
                        double dead = checker ? 0.25 : 0.40;
                        state = s < dead ? Level228Plan.LIGHT_DEAD : s < dead + 0.08 ? Level228Plan.LIGHT_FAILING : Level228Plan.LIGHT_LIT;
                    }
                    localProp(r, Level228Plan.P_BULB, zv, bu, Level228Plan.BAND_Y, bv, 0,
                            lightFlags(kind, state, Hash.range(Hash.mix(hsh + 2), 16)) | author, 0, (int) hsh);
                }
                double l = Hash.unit(Hash.mix(hsh + 3));
                if (l < 0.10) {
                    localProp(r, Level228Plan.P_LITTER, Hash.range(Hash.mix(hsh + 4), LITTER_VARIANTS), 3 * c + 1, Level228Plan.FEET_Y,
                            3 * row + 2, Hash.range(Hash.mix(hsh + 5), 4), author, 0, (int) hsh);
                } else if (l < 0.16) {
                    localProp(r, Level228Plan.P_SHARDS, Hash.range(Hash.mix(hsh + 4), 4), 3 * c + 2, Level228Plan.FEET_Y,
                            3 * row + 1, Hash.range(Hash.mix(hsh + 5), 4), author, 0, (int) hsh);
                }
                if (Hash.unit(Hash.mix(hsh + 6)) < 0.12) {
                    localProp(r, Level228Plan.P_COBWEB, Hash.range(Hash.mix(hsh + 7), 4), 3 * c + 1, Level228Plan.BAND_Y,
                            3 * row + 1, Hash.range(Hash.mix(hsh + 8), 4), author, 0, (int) hsh);
                }
                if (z == Zone.FUNHOUSE && row > 0 && Hash.unit(Hash.mix(hsh + 9)) < 0.15) {
                    // Bunting across the v-line segment in front of this cell, sagging.
                    localProp(r, Level228Plan.P_BUNTING, Hash.range(Hash.mix(hsh + 10), 4), 3 * c + 1, Level228Plan.BAND_Y,
                            3 * row, 0, author, 0, (int) hsh);
                }
            }
            // The backdrop panels (room side): posters, painted palms in the Alhambra.
            long hb = Hash.of(seed, r.index * 64 + c, -1, 0xDEC1);
            if (r.backdrop[c] == T_PANEL && Hash.unit(hb) < 0.65) {
                int t = z == Zone.ALHAMBRA && Hash.unit(Hash.mix(hb + 1)) < 0.6 ? Level228Plan.P_PALM : Level228Plan.P_POSTER;
                // Wall-mounted: anchor = the cell in front, wall on its local +Z face at rot0. The
                // backdrop is at bv = 0, the cell at bv = 1; facing +v (into the room): front
                // direction +v, i.e. local (0, +1) -> rot 2 in the local frame.
                localProp(r, t, Hash.range(Hash.mix(hb + 2), 6), 3 * c + 1 + (int) (hb >>> 62 & 1), Level228Plan.FEET_Y, 1, 2,
                        author, 0, (int) hb);
            }
        }
        int canonEnd = pCount;
        // Copy 0's props, placed in copies 1..n-1 (translations and reflections along u) and
        // then into the sealed images; each placement maps room-local -> world.
        int[] canonical = Arrays.copyOfRange(pBuf, start * PROP_FIELDS, canonEnd * PROP_FIELDS);
        pCount = start;
        int w3 = 3 * r.w;
        int h3 = 3 * r.h;
        for (int p = 0; p < canonical.length / PROP_FIELDS; p++) {
            int o = p * PROP_FIELDS;
            for (int j = 0; j < r.n; j++) {
                // copy j: u -> 3kj + u (even) or 3k(j+1) - u (odd), as a point map on doubled coords.
                int ua = (j & 1) == 0 ? 1 : -1;
                int ub2 = (j & 1) == 0 ? 2 * k3 * j : 2 * k3 * (j + 1) + 2;
                for (int fu = -1; fu <= 1; fu++) {
                    for (int fv = 0; fv <= 1; fv++) {
                        // fu: -1 image across u = 0, 0 the room, +1 image across u = 3w; fv: far image.
                        int a = ua;
                        int b2 = ub2;
                        if (fu == -1) {
                            a = -a;
                            b2 = 2 - b2;
                        } else if (fu == 1) {
                            a = -a;
                            b2 = 4 * w3 + 2 - b2;
                        }
                        int va = fv == 0 ? 1 : -1;
                        int vb2 = fv == 0 ? 0 : 4 * h3 + 2;
                        placeRoomProp(r, canonical, o, a, b2, va, vb2, fu != 0 || fv != 0);
                    }
                }
            }
        }
        // The phantom parlour's furniture and its lamp (not an image of anything).
        if (r.phantomU >= 0) {
            int bu = 3 * r.phantomU + 1;
            int bv = h3 + 3;
            placeLocal(r, Level228Plan.P_CHAIR, zv, bu, Level228Plan.FEET_Y, bv + 1, 0, author, 0, (int) Hash.of(seed, r.index, 5, 0xFA7));
            placeLocal(r, Level228Plan.P_BULB, zv, bu + 1, Level228Plan.BAND_Y, bv, 0,
                    lightFlags(Level228Plan.LIGHT_BULB, Level228Plan.LIGHT_LIT, 3) | author, 0, 0);
        }
    }

    private void localProp(Room r, int t, int variant, int bu, int y, int bv, int rot, int f, int aux, int noise) {
        addProp(t, variant, bu, y, bv, rot, f, aux, noise);
    }

    /** Places a canonical local prop through u map (a, b2), v map (va, vb2), then room-local -> world. */
    private void placeRoomProp(Room r, int[] src, int o, int a, int b2, int va, int vb2, boolean sealed) {
        int t = src[o];
        int bu = src[o + 2];
        int bv = src[o + 4];
        int rot = src[o + 5];
        int fx2 = footX2(t);
        int fz2 = footZ2(t);
        int pu2 = a * (2 * bu + 1 + rotX(rot, fx2, fz2)) + b2;
        int pv2 = va * (2 * bv + 1 + rotZ(rot, fx2, fz2)) + vb2;
        int w3 = 3 * r.w;
        int h3 = 3 * r.h;
        // Keep a placement only where its own space is: the room, or the sealed band it fell in.
        double pu = pu2 * 0.5;
        double pv = pv2 * 0.5;
        boolean inRoom = pu >= 0 && pu <= w3 + 1 && pv >= 0 && pv <= h3 + 1;
        boolean inSealed = pu >= -FOG + 1 && pu <= w3 + FOG && pv >= 0 && pv <= h3 + FOG;
        if (sealed ? inRoom || !inSealed : !inRoom) {
            return;
        }
        if (r.phantomU >= 0 && sealed && pv > h3 && pv < h3 + 6 && pu > 3 * (r.phantomU - 1) && pu < 3 * (r.phantomU + 2)) {
            return; // the phantom parlour replaces the image here
        }
        // Compose: local reflection maps, then local -> world (p_w = L p_l + 2o + 1 - L*1 in doubled coords).
        int la = r.ux;
        int lb = r.vx;
        int lc = r.uz;
        int ld = r.vz;
        int A11 = la * a;
        int A12 = lb * va;
        int A21 = lc * a;
        int A22 = ld * va;
        int bx2 = la * b2 + lb * vb2 + 2 * r.ox + 1 - la - lb;
        int bz2 = lc * b2 + ld * vb2 + 2 * r.oz + 1 - lc - ld;
        addMapped(t, src[o + 1], bu, src[o + 3], bv, rot, src[o + 6], src[o + 7], src[o + 8], A11, A12, A21, A22, bx2, bz2);
    }

    /** A single room-local prop straight to the world (no copies). */
    private void placeLocal(Room r, int t, int variant, int bu, int y, int bv, int rot, int f, int aux, int noise) {
        int la = r.ux;
        int lb = r.vx;
        int lc = r.uz;
        int ld = r.vz;
        addMapped(t, variant, bu, y, bv, rot, f, aux, noise, la, lb, lc, ld,
                2 * r.ox + 1 - la - lb, 2 * r.oz + 1 - lc - ld);
    }

    // ---- core decor --------------------------------------------------------------------------

    private void foyerProps() {
        int fy = Level228Plan.FEET_Y;
        int zv = Zone.FOYER.ordinal();
        int k = 2 * ARRIVAL_LINE; // the arrival mirror's image map, z -> 38 - z
        // The funhouse facade over the doorway to the queue passage, its marquee long dead but
        // for a few failing bulbs.
        addWithImage(Level228Plan.P_FACADE, 0, 12, fy, 6, 2, lightFlags(Level228Plan.LIGHT_MARQUEE, Level228Plan.LIGHT_FAILING, 5),
                0, 11, true, k);
        // The marquee's two failing bulbs give real light (LightAgent's blocks), in the image too.
        for (int x : new int[] {6, 11}) {
            setLight(x, Level228Plan.BAND_Y, 6, Level228Plan.LIGHT_MARQUEE, Level228Plan.LIGHT_FAILING, x);
            setLight(x, Level228Plan.BAND_Y, k - 6, Level228Plan.LIGHT_MARQUEE, Level228Plan.LIGHT_FAILING, x);
        }
        addWithImage(Level228Plan.P_TURNSTILE, 0, 8, fy, 6, 2, 0, 0, 12, true, k);
        addWithImage(Level228Plan.P_TICKET_BOOTH, 0, 16, fy, 8, 2, 0, 0, 13, true, k);
        addWithImage(Level228Plan.P_NOTE, 1, 17, fy, 7, 3, 0, 0, 14, true, k);
        // Queue stanchions, two rows; one rope down, one post fallen over (variant 1).
        for (int i = 0; i < 6; i++) {
            int x = 6 + 2 * i;
            addWithImage(Level228Plan.P_STANCHION, i == 4 ? 1 : 0, x, fy, 10, 0, i < 5 && i != 2 ? 2 : 0, 0, 20 + i, true, k);
            addWithImage(Level228Plan.P_STANCHION, 0, x, fy, 13, 0, i < 5 ? 2 : 0, 0, 30 + i, true, k);
        }
        // Posters: west wall, east wall, south wall either side of the mirror.
        int[][] posters = {{4, 8, 1, 0}, {4, 14, 1, 1}, {20, 10, 3, 2}, {20, 16, 3, 3}, {7, 18, 0, 4}, {16, 18, 0, 5}};
        for (int[] p : posters) {
            addWithImage(Level228Plan.P_POSTER, p[3], p[0], fy, p[1], p[2], 0, 0, 40 + p[3], true, k);
        }
        // Hanging bulbs: most dead, one lit over the booth, one failing over the queue.
        int[][] bulbs = {{7, 9, Level228Plan.LIGHT_DEAD}, {14, 10, Level228Plan.LIGHT_LIT}, {10, 14, Level228Plan.LIGHT_FAILING},
                {17, 15, Level228Plan.LIGHT_DEAD}, {5, 16, Level228Plan.LIGHT_DEAD}, {9, 2, Level228Plan.LIGHT_DEAD}};
        for (int[] b : bulbs) {
            addWithImage(Level228Plan.P_BULB, zv, b[0], Level228Plan.BAND_Y, b[1], 0,
                    lightFlags(Level228Plan.LIGHT_BULB, b[2], b[0] + b[1]), 0, 50 + b[0], true, k);
        }
        int[][] litter = {{6, 7}, {12, 12}, {18, 9}, {9, 17}, {15, 15}, {10, 3}};
        for (int[] l : litter) {
            addWithImage(Level228Plan.P_LITTER, (l[0] + l[1]) % LITTER_VARIANTS, l[0], fy, l[1], (l[0] * 3) & 3, 0, 0, 60 + l[0], true, k);
        }
        addWithImage(Level228Plan.P_COBWEB, 0, 4, Level228Plan.BAND_Y, 6, 0, 0, 0, 70, true, k);
        addWithImage(Level228Plan.P_COBWEB, 1, 20, Level228Plan.BAND_Y, 18, 2, 0, 0, 71, true, k);
        addWithImage(Level228Plan.P_COBWEB, 2, 7, Level228Plan.BAND_Y, 1, 3, 0, 0, 72, true, k);
        // The tall mirror you step out of (frame prop; the pane is a PANEL from the grid).
        addProp(Level228Plan.P_ARRIVAL_MIRROR, zv, ARRIVAL_X, fy, ARRIVAL_LINE, 0, 0, 3, 80);
    }

    private void backstageProps() {
        int fy = Level228Plan.FEET_Y;
        int by = Level228Plan.BAND_Y;
        int zv = Zone.BACKSTAGE.ordinal();
        int bulb = Level228Plan.LIGHT_BULB;
        // Bare work bulbs on cords along the connectors; a few lit, more failing or dead.
        int[][] bulbs = {
                {28, 1, Level228Plan.LIGHT_LIT}, {40, 2, Level228Plan.LIGHT_DEAD}, {48, 1, Level228Plan.LIGHT_FAILING},
                {34, 6, Level228Plan.LIGHT_LIT}, {52, 33, Level228Plan.LIGHT_LIT}, {53, 45, Level228Plan.LIGHT_DEAD},
                {48, 38, Level228Plan.LIGHT_FAILING}, {30, 52, Level228Plan.LIGHT_LIT}, {18, 53, Level228Plan.LIGHT_DEAD},
                {6, 52, Level228Plan.LIGHT_LIT}, {1, 34, Level228Plan.LIGHT_FAILING}, {2, 42, Level228Plan.LIGHT_DEAD}};
        for (int[] b : bulbs) {
            addProp(Level228Plan.P_BULB, zv, b[0], by, b[1], 0, lightFlags(bulb, b[2], b[0] * 7 + b[1]), 0, b[0] * 31 + b[1]);
        }
        // Emergency EXIT signs that lead nowhere in particular (wall-mounted, cell in front).
        addProp(Level228Plan.P_EXIT_SIGN, zv, 53, by, 3, 3, lightFlags(Level228Plan.LIGHT_SIGN, Level228Plan.LIGHT_LIT, 1), 0, 1);
        addProp(Level228Plan.P_EXIT_SIGN, zv, 1, by, 52, 1, lightFlags(Level228Plan.LIGHT_SIGN, Level228Plan.LIGHT_FAILING, 9), 0, 2);
        // The storage room: crates of spare mirrors, the broken mirror stack, a mop bucket, notes.
        addProp(Level228Plan.P_CRATE, 0, 31, fy, 9, 0, 0, 0, 100);
        addProp(Level228Plan.P_CRATE, 1, 32, fy, 9, 0, 0, 0, 101);
        addProp(Level228Plan.P_CRATE, 2, 38, fy, 4, 1, 0, 0, 102);
        addProp(Level228Plan.P_MIRROR_STACK, 0, 35, fy, 9, 0, 0, 0, 103);
        addProp(Level228Plan.P_SHARDS, 2, 36, fy, 8, 1, 0, 0, 104);
        addProp(Level228Plan.P_BUCKET, 0, 38, fy, 8, 2, 0, 0, 105);
        addProp(Level228Plan.P_NOTE, 2, 31, fy, 5, 1, 0, 0, 106);
        addProp(Level228Plan.P_COBWEB, 0, 38, by, 9, 1, 0, 0, 107);
        // C1, C2, C3, C4 clutter.
        addProp(Level228Plan.P_CRATE, 3, 44, fy, 1, 1, 0, 0, 110);
        addProp(Level228Plan.P_NOTE, 3, 27, fy, 1, 2, 0, 0, 111);
        addProp(Level228Plan.P_LITTER, 4, 46, fy, 2, 1, 0, 0, 112);
        addProp(Level228Plan.P_BUCKET, 1, 53, fy, 30, 0, 0, 0, 113);
        addProp(Level228Plan.P_LITTER, 2, 52, fy, 47, 2, 0, 0, 114);
        addProp(Level228Plan.P_CRATE, 4, 47, fy, 41, 0, 0, 0, 115);
        addProp(Level228Plan.P_NOTE, 4, 46, fy, 37, 1, 0, 0, 116);
        addProp(Level228Plan.P_MIRROR_STACK, 1, 49, fy, 41, 0, 0, 0, 117);
        addProp(Level228Plan.P_SHARDS, 1, 24, fy, 53, 0, 0, 0, 118);
        addProp(Level228Plan.P_LITTER, 5, 12, fy, 52, 3, 0, 0, 119);
        addProp(Level228Plan.P_NOTE, 5, 2, fy, 30, 3, 0, 0, 120);
        addProp(Level228Plan.P_COBWEB, 1, 1, by, 46, 2, 0, 0, 121);
        // Closets: a mop bucket in one, a crate in another.
        for (int j = 1; j < rooms[1].n - 1; j++) {
            int u = rooms[1].curtainU(j);
            addProp(j == 2 ? Level228Plan.P_BUCKET : Level228Plan.P_CRATE, j, 53, fy, 3 * u + 1, 3, 0, 0, 130 + j);
        }
        int su = rooms[2].curtainU(1);
        addProp(Level228Plan.P_BUCKET, 2, CORE - 3 * su - 1, fy, 52, 2, 0, 0, 135);
    }

    private void chamberProps() {
        int fy = Level228Plan.FEET_Y;
        int zv = Zone.CHAMBER.ordinal();
        int k = 2 * CHAMBER_LINE; // image map x -> 30 - x
        for (int m = 0; m < 3; m++) {
            int z0 = CHAMBER_MIRROR_Z + 3 * m;
            // The frame (on the line) and its pane prop come from the grid; the tall antique frame here.
            addProp(Level228Plan.P_CHAMBER_MIRROR, zv, CHAMBER_LINE, fy, z0 + 1, 3, 0, m, 200 + m);
            // One candle sconce above each mirror, identical for all three (same kind and state).
            int sconce = lightFlags(Level228Plan.LIGHT_CANDLE, Level228Plan.LIGHT_LIT, 4);
            addProp(Level228Plan.P_SCONCE, zv, CHAMBER_X1, Level228Plan.PANE_TOP_Y, z0, 3, sconce, m, 210);
            // The same small detail on the floor in front of each: a torn ticket stub.
            addProp(Level228Plan.P_LITTER, 7, CHAMBER_X1 - 1, fy, z0, 0, 0, m, 220);
            // Their images behind the glass, with the wrong mirrors' environmental flaws.
            int f = flaw[m];
            addImage(Level228Plan.P_SCONCE, zv, CHAMBER_X1, Level228Plan.PANE_TOP_Y, z0, 3,
                    f == Level228Plan.FLAW_CANDLE_UNLIT ? lightFlags(Level228Plan.LIGHT_CANDLE, Level228Plan.LIGHT_DEAD, 4) : sconce,
                    m, 210, false, k);
            if (f != Level228Plan.FLAW_MISSING_DETAIL) {
                addImage(Level228Plan.P_LITTER, 7, CHAMBER_X1 - 1, fy, z0, 0, 0, m, 220, false, k);
            }
        }
        // One dim candle-bulb pendant over the middle of the room, so the image space beyond
        // every mirror is lit exactly like the room (the reflections must be readable). Its light
        // is two blocks (z 44 and 45) so it stands symmetric to the middle mirror: A and C get the
        // same light. Imaged with the same phase.
        int pendant = lightFlags(Level228Plan.LIGHT_CANDLE, Level228Plan.LIGHT_LIT, 7);
        int cx = (CHAMBER_X0 + CHAMBER_X1) / 2;
        addWithImage(Level228Plan.P_BULB, zv, cx, Level228Plan.BAND_Y, 44, 0, pendant, 0, 230, false, k);
        setLight(cx, Level228Plan.BAND_Y, 45, Level228Plan.LIGHT_CANDLE, Level228Plan.LIGHT_LIT, 7);
        setLight(k - cx, Level228Plan.BAND_Y, 45, Level228Plan.LIGHT_CANDLE, Level228Plan.LIGHT_LIT, 7);
    }

    private void hallProps() {
        int fy = Level228Plan.FEET_Y;
        int by = Level228Plan.BAND_Y;
        int zv = Zone.EXIT_HALL.ordinal();
        int cold = Level228Plan.LIGHT_COLD;
        // Everything near the entry mirror is imaged behind it (z -> -z).
        addWithImage(Level228Plan.P_BANNER, 0, HALL_X0, by, 9, 1, 0, 0, 300, true, 0);
        addWithImage(Level228Plan.P_POSTER, 6, HALL_X1, fy, 5, 3, 0, 0, 301, true, 0);
        addWithImage(Level228Plan.P_BULB, zv, 151, by, 6, 0, lightFlags(cold, Level228Plan.LIGHT_DEAD, 2), 0, 302, true, 0);
        addWithImage(Level228Plan.P_BULB, zv, 152, by, 16, 0, lightFlags(cold, Level228Plan.LIGHT_FAILING, 6), 0, 303, true, 0);
        addWithImage(Level228Plan.P_BULB, zv, 151, by, 26, 0, lightFlags(cold, Level228Plan.LIGHT_LIT, 1), 0, 304, true, 0);
        addWithImage(Level228Plan.P_BULB, zv, 152, by, 34, 0, lightFlags(cold, Level228Plan.LIGHT_LIT, 8), 0, 305, true, 0);
        addProp(Level228Plan.P_BULB, zv, 152, by, 40, 0, lightFlags(cold, Level228Plan.LIGHT_LIT, 11), 0, 306);
        for (int i = 0; i < 14; i++) {
            long h = Hash.of(seed, i, 0, 0x1EAF);
            int x = HALL_X0 + Hash.range(h, HALL_X1 - HALL_X0 + 1);
            int z = HALL_Z0 + 2 + Hash.range(Hash.mix(h), HALL_Z1 - HALL_Z0 - 2);
            int t = i % 3 == 0 ? Level228Plan.P_LITTER : Level228Plan.P_LEAVES;
            addWithImage(t, t == Level228Plan.P_LITTER ? i % LITTER_VARIANTS : i & 7, x, fy, z, i & 3, 0, 0, 310 + i, true, 0);
        }
        addWithImage(Level228Plan.P_COBWEB, 0, HALL_X0, by, 1, 1, 0, 0, 330, true, 0);
        // The door at the end, the red EXIT sign over it, the mirror you came out of.
        addProp(Level228Plan.P_EXIT_DOOR, zv, HALL_MIRROR_X, fy, DOOR_LINE, 0, 0, 0, 340);
        addWithImage(Level228Plan.P_EXIT_SIGN, zv, HALL_MIRROR_X, by, DOOR_LINE - 1, 0,
                lightFlags(Level228Plan.LIGHT_SIGN, Level228Plan.LIGHT_LIT, 0), 0, 341, true, 0);
        addProp(Level228Plan.P_ARRIVAL_MIRROR, zv, HALL_MIRROR_X + 1, fy, 0, 2, 0, 4, 342);
    }
}
