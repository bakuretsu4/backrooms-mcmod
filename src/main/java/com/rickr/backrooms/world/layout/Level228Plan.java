package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.world.layout.Level228Maze.Room;

/**
 * Level 228 v2, "Hall of Mirrors": the floor plan of the one abandoned carnival mirror maze of a
 * world, shared by the chunk generator ({@link Level228Layout}), the server rules
 * ({@code world/Level228Rules}), the props, reflections, lights and the imitator on the client.
 * Pure (a function of the layout seed and the shared {@link ShiftState}); every query is
 * allocation-free (caller-provided output arrays; the model itself is built once per seed,
 * {@link Level228Maze}).
 *
 * <h2>Reflections are real space</h2>
 * Under Iris a second render pass is impossible, so every reflection is geometry the game draws
 * anyway. Most mirrors stand on the symmetry lines of a kaleidoscope room (the room's content is
 * mirror-symmetric across them), so what you see through one is the real twin space beyond; the
 * rest (room perimeters, the three final-chamber mirrors, the arrival and hallway mirrors) are
 * backed by sealed, unreachable volumes built block for block as the image of the space in front.
 * People are the one thing the world cannot contain: the client hides real players standing
 * beyond a pane ({@link #behindMirror}) and draws stand-ins at every image ({@link #imagesOf}).
 *
 * <h2>The maze</h2>
 * Foyer -> Crystal Maze -> Alhambra Hall -> Funhouse -> Glass Labyrinth -> final chamber, with
 * narrow backstage connectors between; the exit hallway lies behind the correct chamber mirror.
 * The room layout is seed-varied (the strip mazes, glass and mirror placement, shifting classes,
 * hidden passages, phantom rooms, the puzzle's answer); the sequence never changes.
 */
public final class Level228Plan {
    /** Cell pitch: a column every PITCH blocks, 2-block panels between them. */
    public static final int PITCH = 3;
    public static final int FLOOR_Y = LevelLayout.FLOOR_Y;
    public static final int FEET_Y = FLOOR_Y + 1;
    /** Top block of a mirror/glass panel (panels are FEET_Y..PANE_TOP_Y, 3 tall). */
    public static final int PANE_TOP_Y = FEET_Y + 2;
    /** The frieze/arch/fretwork band over every panel and opening. */
    public static final int BAND_Y = FEET_Y + 3;
    public static final int CEILING_Y = FEET_Y + 4;
    public static final int MAX_DEPTH = 4;
    /** How deep sealed images run behind perimeter mirrors (blocks); the fog swallows the rest. */
    public static final int FOG_DEPTH = Level228Maze.FOG;

    public enum Zone { FOYER, CRYSTAL, ALHAMBRA, FUNHOUSE, GLASS, CHAMBER, EXIT_HALL, BACKSTAGE, NONE }

    // Mirror kinds
    public static final int K_TWIN = 0;
    public static final int K_SEALED = 1;
    public static final int K_EXIT_A = 2;
    public static final int K_EXIT_B = 3;
    public static final int K_EXIT_C = 4;
    public static final int K_DISTORT = 5;
    // Mirror surfaces
    public static final int S_CLEAN = 0;
    public static final int S_TARNISHED = 1;
    public static final int S_CRACKED = 2;
    public static final int S_SHATTERED = 3;
    public static final int S_DISTORT_CONCAVE = 4;
    public static final int S_DISTORT_CONVEX = 5;
    public static final int S_DISTORT_WAVE = 6;
    // Mirror flags
    /** The pane itself stands in sealed (image) space. */
    public static final int MF_IMAGE = 1;
    public static final int MF_PERIMETER = 1 << 1;
    /** Its image shows a room that is not there. */
    public static final int MF_PHANTOM = 1 << 2;
    /** Through it the twin side shows an opening where the real wall is a secret panel. */
    public static final int MF_HIDDEN = 1 << 3;
    public static final int MF_ARRIVAL = 1 << 4;
    public static final int MF_CHECKPOINT = 1 << 5;
    public static final int MF_HALL = 1 << 6;
    public static final int MF_SHIFTING = 1 << 7;

    // Exit-puzzle flaws
    public static final int FLAW_DELAY = 0;
    public static final int FLAW_WRONG_HAND = 1;
    public static final int FLAW_NO_ARMOUR = 2;
    public static final int FLAW_NOD = 3;
    public static final int FLAW_UNMIRRORED = 4;
    /** The candle in the image is out (built into the chamber's image props here). */
    public static final int FLAW_CANDLE_UNLIT = 5;
    /** The ticket stub in front of the mirror is missing from its image (built here). */
    public static final int FLAW_MISSING_DETAIL = 6;

    // Prop types
    public static final int P_COLUMN = 1;
    public static final int P_PANEL = 2;
    public static final int P_OPENING = 3;
    public static final int P_FLOOR = 4;
    public static final int P_CEILING = 5;
    public static final int P_WALL = 6;
    public static final int P_BULB = 7;
    public static final int P_SCONCE = 8;
    public static final int P_EXIT_SIGN = 9;
    public static final int P_FACADE = 10;
    public static final int P_TICKET_BOOTH = 11;
    public static final int P_TURNSTILE = 12;
    public static final int P_STANCHION = 13;
    public static final int P_POSTER = 14;
    public static final int P_BUNTING = 15;
    public static final int P_SHARDS = 16;
    public static final int P_COBWEB = 17;
    public static final int P_LITTER = 18;
    public static final int P_NOTE = 19;
    public static final int P_CRATE = 20;
    public static final int P_MIRROR_STACK = 21;
    public static final int P_BUCKET = 22;
    public static final int P_BANNER = 23;
    public static final int P_LEAVES = 24;
    public static final int P_CHAMBER_MIRROR = 25;
    public static final int P_ARRIVAL_MIRROR = 26;
    public static final int P_CURTAIN = 27;
    public static final int P_EXIT_DOOR = 28;
    public static final int P_PALM = 29;
    /** Furniture of a phantom room (a parlour chair). */
    public static final int P_CHAIR = 30;
    // Prop flags
    public static final int PF_FLIP = 1;
    public static final int PF_PANEL_SHIFT = 1;
    public static final int PF_PANEL_MASK = 3 << PF_PANEL_SHIFT;
    public static final int PANEL_MIRROR = 0;
    public static final int PANEL_GLASS = 1;
    public static final int PANEL_EMPTY_FRAME = 2;
    public static final int PANEL_SOLID = 3;
    public static final int PF_SECRET = 1 << 3;
    public static final int PF_LIGHT_KIND_SHIFT = 4;
    public static final int PF_LIGHT_STATE_SHIFT = 7;
    public static final int PF_LIGHT_PHASE_SHIFT = 9;
    public static final int LIGHT_BULB = 0;
    public static final int LIGHT_CANDLE = 1;
    public static final int LIGHT_MARQUEE = 2;
    public static final int LIGHT_SIGN = 3;
    public static final int LIGHT_COLD = 4;
    public static final int LIGHT_LIT = 0;
    public static final int LIGHT_FAILING = 1;
    public static final int LIGHT_DEAD = 2;

    /** Server-authoritative state of the shifting panels and the opened secret panels. Immutable. */
    public static final class ShiftState {
        public static final ShiftState INITIAL = new ShiftState(0L, 0);
        public final long bits;
        public final int version;

        public ShiftState(long bits, int version) {
            this.bits = bits;
            this.version = version;
        }

        public boolean on(int bit) {
            return (bits >>> bit & 1L) != 0;
        }

        public ShiftState with(int bit, boolean on) {
            long b = on ? bits | 1L << bit : bits & ~(1L << bit);
            return b == bits ? this : new ShiftState(b, version + 1);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ShiftState s && s.bits == bits && s.version == version;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(bits) * 31 + version;
        }
    }

    /** A visible image of a point through one or more mirrors, as seen from a camera. */
    public static final class Image {
        /** Where the image appears (world coords, real space). */
        public double x, y, z;
        /** +1/-1 handedness per world axis (odd total = mirrored model). */
        public int flipX, flipZ;
        /** Number of mirror bounces (1..MAX_DEPTH). */
        public int depth;
        /** Id of the mirror panel the sightline passes through first (nearest the camera). */
        public int lastMirror;
    }

    /** One mirror panel. */
    public static final class Mirror {
        public int id;
        /** 0: plane x = const (panel runs along z); 1: plane z = const. */
        public int axis;
        public double plane;
        /** Span along the panel (world, the full 2 blocks between columns). */
        public double min, max;
        /** Visible silvering height (world y). */
        public double y0, y1;
        public int kind;
        public int surface;
        public Zone zone = Zone.NONE;
        public int flags;
        /** Side the viewer stands on for a sealed mirror (+1/-1 along the normal); 0 for twins. */
        public int front;
        /** Canonical detail hash: identical for a mirror and every image of it. */
        public int noise;
    }

    /** One visual prop record (see the P_* types; conventions in PropsAgent's renderer). */
    public static final class Prop {
        public int type, variant;
        public int x, y, z;
        /** Quarter turns clockwise seen from above; front faces -Z at rot 0. */
        public int rot;
        public int flags;
        public int aux;
        /** Canonical detail hash: identical for a prop and every reflection of it. */
        public int noise;
    }

    /** Per-thread scratch for the sightline traces (render thread and server thread both ask). */
    private static final class Trace {
        final int[] axis = new int[MAX_DEPTH + 1];
        final double[] plane = new double[MAX_DEPTH + 1];
        final int[] id = new int[MAX_DEPTH + 1];
        int count;
        boolean distort;
        boolean blocked;
        final double[] planesDone = new double[8];
        final int[] axesDone = new int[8];
    }

    private static final ThreadLocal<Trace> TRACE = ThreadLocal.withInitial(Trace::new);

    private Level228Plan() {}

    static Level228Maze maze(long seed) {
        return Level228Maze.of(seed);
    }

    /**
     * Builds the maze for this seed now (a few tens of milliseconds, once), so the first query
     * on the render thread doesn't. Safe from any thread.
     */
    public static void prepare(long seed) {
        maze(seed);
    }

    // ==========================================================================================
    // Reflections
    // ==========================================================================================

    /**
     * Every image of point p (e.g. a player's feet) actually visible from camera c within radius
     * r: candidates are the orbit of p under the reflections of the camera's room (its
     * kaleidoscope lines, its far mirror, a funhouse backdrop) and of the special sealed mirrors
     * near the camera; each is kept only if the straight sightline c -> image crosses mirror
     * panes whose reflections, applied in order, take p to it, and nothing opaque on the way
     * (grid DDA). Returns the count written.
     */
    public static int imagesOf(long seed, ShiftState s, double cx, double cy, double cz,
                               double px, double py, double pz, double r, Image[] out) {
        Level228Maze m = maze(seed);
        long bits = s.bits;
        Trace tr = TRACE.get();
        int n = 0;
        int ix = (int) Math.floor(cx);
        int iz = (int) Math.floor(cz);
        if (!Level228Maze.inGrid(ix, iz)) {
            return 0;
        }
        double r2 = r * r;
        int ri = m.room[Level228Maze.idx(ix, iz)];
        if (ri >= 0) {
            Room room = m.rooms[ri];
            double tu = room.contU(px, pz);
            double tv = room.contV(px, pz);
            double cu = room.contU(cx, cz);
            double period = 6.0 * room.k;
            for (int refl = 0; refl < 2 && n < out.length; refl++) {
                double base = refl == 0 ? tu : 1.0 - tu;
                int lo = (int) Math.floor((cu - r - base) / period);
                int hi = (int) Math.ceil((cu + r - base) / period);
                for (int mm = lo; mm <= hi && n < out.length; mm++) {
                    double qu = base + period * mm;
                    if (Math.abs(qu - cu) > r) {
                        continue;
                    }
                    for (int vr = 0; vr < 2 && n < out.length; vr++) {
                        if (refl == 0 && mm == 0 && vr == 0) {
                            continue; // p itself
                        }
                        double qv = vr == 0 ? tv : 6.0 * room.h + 1.0 - tv;
                        double qx = room.contX(qu, qv);
                        double qz = room.contZ(qu, qv);
                        double ddx = qx - cx;
                        double ddz = qz - cz;
                        if (ddx * ddx + ddz * ddz <= r2) {
                            n = tryImage(m, bits, tr, cx, cy, cz, px, py, pz, qx, qz, out, n);
                        }
                    }
                }
            }
            if (room.zone == Zone.FUNHOUSE && n < out.length) {
                // The funhouse mirrors hang on the backdrop (plane v = 0.5): depth 1 only.
                double qx = room.contX(tu, 1.0 - tv);
                double qz = room.contZ(tu, 1.0 - tv);
                double ddx = qx - cx;
                double ddz = qz - cz;
                if (ddx * ddx + ddz * ddz <= r2) {
                    n = tryImage(m, bits, tr, cx, cy, cz, px, py, pz, qx, qz, out, n);
                }
            }
        } else {
            // Special sealed mirrors (ids 0..4: the chamber's three, the arrival, the hallway):
            // one candidate per distinct plane near the camera.
            int planes = 0;
            for (int id = 0; id < SPECIAL_MIRRORS && n < out.length; id++) {
                if (distanceToMirror(m, id, cx, cz) > r) {
                    continue;
                }
                int axis = m.mAxis[id];
                double plane = m.mPlane[id];
                boolean done = false;
                for (int k = 0; k < planes; k++) {
                    done |= tr.axesDone[k] == axis && tr.planesDone[k] == plane;
                }
                if (done) {
                    continue;
                }
                tr.axesDone[planes] = axis;
                tr.planesDone[planes++] = plane;
                double qx = axis == 0 ? 2 * plane - px : px;
                double qz = axis == 1 ? 2 * plane - pz : pz;
                n = tryImage(m, bits, tr, cx, cy, cz, px, py, pz, qx, qz, out, n);
            }
        }
        return n;
    }

    /** Ids 0..4 are the special mirrors (Level228Maze#registerMirrors). */
    private static final int SPECIAL_MIRRORS = 5;

    private static int tryImage(Level228Maze m, long bits, Trace tr, double cx, double cy, double cz,
                                double px, double py, double pz, double qx, double qz, Image[] out, int n) {
        trace(m, bits, tr, cx, cy, cz, qx, py, qz, false);
        int d = tr.count;
        if (tr.blocked || d < 1 || d > MAX_DEPTH || tr.distort && d != 1) {
            return n;
        }
        double x = px;
        double z = pz;
        int fx = 1;
        int fz = 1;
        for (int i = 0; i < d; i++) {
            if (tr.axis[i] == 0) {
                x = 2 * tr.plane[i] - x;
                fx = -fx;
            } else {
                z = 2 * tr.plane[i] - z;
                fz = -fz;
            }
        }
        if (Math.abs(x - qx) > 1e-6 || Math.abs(z - qz) > 1e-6) {
            return n;
        }
        // A point exactly on a symmetry line is its own reflection there: both orbit branches
        // then name the same image, which is reported once.
        for (int k = 0; k < n; k++) {
            if (Math.abs(out[k].x - qx) < 1e-9 && Math.abs(out[k].z - qz) < 1e-9) {
                return n;
            }
        }
        Image img = out[n];
        img.x = qx;
        img.y = py;
        img.z = qz;
        img.flipX = fx;
        img.flipZ = fz;
        img.depth = d;
        img.lastMirror = tr.id[0];
        return n + 1;
    }

    /** True if the sightline c -> q passes through at least one mirror panel (q is "behind a mirror"). */
    public static boolean behindMirror(long seed, ShiftState s, double cx, double cy, double cz, double qx, double qy, double qz) {
        Trace tr = TRACE.get();
        trace(maze(seed), s.bits, tr, cx, cy, cz, qx, qy, qz, true);
        return tr.count > 0;
    }

    /**
     * Walks the straight segment a -> b through the column grid (Amanatides-Woo), recording the
     * mirror panes it crosses (plane through the block centre, within the pane's height) and
     * stopping at anything opaque: walls, posts (a 0.27 radius shaft), painted panels, curtains.
     * Glass and openings pass. A distorting mirror is dark-backed: nothing behind it is checked.
     */
    private static void trace(Level228Maze m, long bits, Trace tr, double ax, double ay, double az,
                              double bx, double by, double bz, boolean firstMirror) {
        tr.count = 0;
        tr.distort = false;
        tr.blocked = false;
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        int x = (int) Math.floor(ax);
        int z = (int) Math.floor(az);
        int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tDeltaX = stepX != 0 ? 1.0 / Math.abs(dx) : Double.POSITIVE_INFINITY;
        double tDeltaZ = stepZ != 0 ? 1.0 / Math.abs(dz) : Double.POSITIVE_INFINITY;
        double tMaxX = stepX > 0 ? (x + 1 - ax) / dx : stepX < 0 ? (ax - x) / -dx : Double.POSITIVE_INFINITY;
        double tMaxZ = stepZ > 0 ? (z + 1 - az) / dz : stepZ < 0 ? (az - z) / -dz : Double.POSITIVE_INFINITY;
        double t0 = 0.0;
        for (int guard = 0; guard < 1024; guard++) {
            double t1 = Math.min(1.0, Math.min(tMaxX, tMaxZ));
            if (!inspect(m, bits, tr, x, z, t0, t1, ax, ay, az, dx, dy, dz)) {
                tr.blocked = true;
                return;
            }
            if (tr.distort || firstMirror && tr.count > 0) {
                return;
            }
            if (t1 >= 1.0) {
                return;
            }
            if (tMaxX < tMaxZ) {
                x += stepX;
                t0 = tMaxX;
                tMaxX += tDeltaX;
            } else {
                z += stepZ;
                t0 = tMaxZ;
                tMaxZ += tDeltaZ;
            }
        }
    }

    /** False if the block stops the sightline over [t0, t1]. */
    private static boolean inspect(Level228Maze m, long bits, Trace tr, int x, int z, double t0, double t1,
                                   double ax, double ay, double az, double dx, double dy, double dz) {
        if (!Level228Maze.inGrid(x, z)) {
            return false;
        }
        int i = Level228Maze.idx(x, z);
        byte t = m.typeAt(bits, x, z);
        switch (t) {
            case Level228Maze.T_FLOOR, Level228Maze.T_ARCH, Level228Maze.T_FRAME -> {
                return true;
            }
            case Level228Maze.T_POST -> {
                // The panels' frames are tucked behind every column, so a sightline crossing the
                // grid line inside a column's block meets wood, not a gap beside the shaft.
                if (crossesCentre(ax, dx, x, t0, t1) || crossesCentre(az, dz, z, t0, t1)) {
                    return false;
                }
                double ex = x + 0.5 - ax;
                double ez = z + 0.5 - az;
                double len2 = dx * dx + dz * dz;
                double tt = len2 > 0 ? (ex * dx + ez * dz) / len2 : 0;
                tt = Math.max(t0, Math.min(t1, tt));
                double ox = ax + dx * tt - x - 0.5;
                double oz = az + dz * tt - z - 0.5;
                return ox * ox + oz * oz >= 0.27 * 0.27;
            }
            case Level228Maze.T_MIRROR, Level228Maze.T_GLASS, Level228Maze.T_PANEL, Level228Maze.T_DISTORT -> {
                int id = m.mirror[i];
                int axis = id >= 0 ? m.mAxis[id] : Math.floorMod(x, 3) == 0 ? 0 : 1;
                double plane = (axis == 0 ? x : z) + 0.5;
                double comp = axis == 0 ? dx : dz;
                if (comp == 0) {
                    return true;
                }
                double tc = (plane - (axis == 0 ? ax : az)) / comp;
                if (tc < t0 - 1e-9 || tc > t1 + 1e-9 || tc <= 1e-7 || tc >= 1.0 - 1e-7) {
                    return true;
                }
                double y = ay + dy * tc;
                double top = (m.flags[i] & Level228Maze.C_TALL) != 0 ? BAND_Y + 1 : PANE_TOP_Y + 1;
                if (y < FEET_Y || y > top) {
                    return true; // over the pane: the fretwork band is open
                }
                if (t == Level228Maze.T_GLASS) {
                    return true;
                }
                if (t == Level228Maze.T_PANEL || id < 0) {
                    return false;
                }
                if (tr.count >= MAX_DEPTH) {
                    return false;
                }
                tr.axis[tr.count] = axis;
                tr.plane[tr.count] = plane;
                tr.id[tr.count] = id;
                tr.count++;
                if (t == Level228Maze.T_DISTORT) {
                    tr.distort = true;
                }
                return true;
            }
            default -> {
                return false; // walls, curtains, the booth, the void
            }
        }
    }

    /** True if a + d t crosses the block centre plane (b + 0.5) for t in [t0, t1]. */
    static boolean crossesCentre(double a, double d, int b, double t0, double t1) {
        if (d == 0) {
            return false;
        }
        double t = (b + 0.5 - a) / d;
        return t >= t0 && t <= t1;
    }

    private static double distanceToMirror(Level228Maze m, int id, double x, double z) {
        double along = m.mAxis[id] == 0 ? z : x;
        double across = m.mAxis[id] == 0 ? x : z;
        double a = Math.max(m.mMin[id], Math.min(m.mMax[id], along));
        double da = along - a;
        double dc = across - m.mPlane[id];
        return Math.sqrt(da * da + dc * dc);
    }

    // ==========================================================================================
    // Space
    // ==========================================================================================

    /** Walkable real space (for pathing, tests, relocation): never sealed image space. */
    public static boolean isWalkable(long seed, ShiftState s, int x, int y, int z) {
        if (y < FEET_Y || y > BAND_Y || !Level228Maze.inGrid(x, z)) {
            return false;
        }
        return maze(seed).walkable(s.bits, x, z);
    }

    /** One step from (x, z) by (dx, dz) (each -1..1); a diagonal needs both sides clear. */
    public static boolean canStep(long seed, ShiftState s, int x, int z, int dx, int dz) {
        Level228Maze m = maze(seed);
        long b = s.bits;
        if (!Level228Maze.inGrid(x, z) || !Level228Maze.inGrid(x + dx, z + dz)
                || !m.walkable(b, x, z) || !m.walkable(b, x + dx, z + dz)) {
            return false;
        }
        return dx == 0 || dz == 0 || m.walkable(b, x + dx, z) && m.walkable(b, x, z + dz);
    }

    /** Zone at a point; sealed images carry the zone they reflect. */
    public static Zone zoneAt(long seed, int x, int z) {
        return maze(seed).zoneAt(x, z);
    }

    /** True for sealed image space (unreachable, built only to be seen through glass). */
    public static boolean isSealed(long seed, int x, int z) {
        return Level228Maze.inGrid(x, z) && (maze(seed).flags[Level228Maze.idx(x, z)] & Level228Maze.C_SEALED) != 0;
    }

    // ==========================================================================================
    // Mirrors
    // ==========================================================================================

    /** Mirror panels within r of (x, z) that are mirrors in state s. Returns the count written. */
    public static int mirrorsNear(long seed, ShiftState s, double x, double z, double r, Mirror[] out) {
        Level228Maze m = maze(seed);
        int n = 0;
        int cx0 = Math.floorDiv((int) Math.floor(x - r) - Level228Maze.MIN_X, 16);
        int cx1 = Math.floorDiv((int) Math.floor(x + r) - Level228Maze.MIN_X, 16);
        int cz0 = Math.floorDiv((int) Math.floor(z - r) - Level228Maze.MIN_Z, 16);
        int cz1 = Math.floorDiv((int) Math.floor(z + r) - Level228Maze.MIN_Z, 16);
        for (int cx = Math.max(0, cx0); cx <= Math.min(Level228Maze.CHUNKS_X - 1, cx1); cx++) {
            for (int cz = Math.max(0, cz0); cz <= Math.min(Level228Maze.CHUNKS_Z - 1, cz1); cz++) {
                int[] ids = m.mirrorsByChunk[cx * Level228Maze.CHUNKS_Z + cz];
                for (int id : ids) {
                    if (n >= out.length) {
                        return n;
                    }
                    if (m.mirrorActive(id, s.bits) && distanceToMirror(m, id, x, z) <= r) {
                        fill(m, id, out[n++]);
                    }
                }
            }
        }
        return n;
    }

    /** The mirror with this id, if it is a mirror in state s. */
    public static boolean mirror(long seed, ShiftState s, int id, Mirror out) {
        Level228Maze m = maze(seed);
        if (id < 0 || id >= m.mirrorCount || !m.mirrorActive(id, s.bits)) {
            return false;
        }
        fill(m, id, out);
        return true;
    }

    private static void fill(Level228Maze m, int id, Mirror out) {
        out.id = id;
        out.axis = m.mAxis[id];
        out.plane = m.mPlane[id];
        out.min = m.mMin[id];
        out.max = m.mMax[id];
        out.y0 = FEET_Y + 0.10;
        out.y1 = m.mY1[id];
        out.kind = m.mKind[id];
        out.surface = m.mSurface[id];
        out.zone = Level228Maze.ZONES[m.mZone[id]];
        out.flags = m.mFlags[id];
        out.front = m.mFront[id];
        out.noise = m.mNoise[id];
    }

    /** Which of the three chamber mirrors (0 = A, the northernmost, .. 2 = C) is the accurate one. Fixed per world. */
    public static int correctExitMirror(long seed) {
        return maze(seed).answer;
    }

    /** The flaw of chamber mirror {@code which} (FLAW_*), or -1 for the accurate one. */
    public static int exitFlaw(long seed, int which) {
        return which < 0 || which > 2 ? -1 : maze(seed).flaw[which];
    }

    /** Mirror id of chamber mirror {@code which} (0..2). */
    public static int exitMirrorId(int which) {
        return which;
    }

    // ==========================================================================================
    // Props
    // ==========================================================================================

    /**
     * Visual props intersecting the 16 x 16 chunk column: the architecture (columns, panels,
     * openings, curtains, floors, ceilings, wall faces) read from the grid in state s, then the
     * static decor. Deterministic; a prop's reflections carry PF_FLIP and the same noise.
     * Returns the count written (at most out.length; 4096 is always enough).
     */
    public static int propsInChunk(long seed, ShiftState s, int chunkX, int chunkZ, Prop[] out) {
        Level228Maze m = maze(seed);
        long bits = s.bits;
        int n = 0;
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                if (!Level228Maze.inGrid(x, z)) {
                    continue;
                }
                n = architecture(m, bits, x, z, out, n);
            }
        }
        int c = Level228Maze.chunkIndex(x0, z0);
        if (c >= 0) {
            int[] p = m.propsByChunk[c];
            int count = m.propCountByChunk[c];
            for (int k = 0; k < count && n < out.length; k++) {
                int o = k * Level228Maze.PROP_FIELDS;
                put(out, n++, p[o], p[o + 1], p[o + 2], p[o + 3], p[o + 4], p[o + 5], p[o + 6], p[o + 7], p[o + 8]);
            }
        }
        return n;
    }

    private static void put(Prop[] out, int n, int type, int variant, int x, int y, int z, int rot, int flags, int aux, int noise) {
        Prop p = out[n];
        p.type = type;
        p.variant = variant;
        p.x = x;
        p.y = y;
        p.z = z;
        p.rot = rot & 3;
        p.flags = flags;
        p.aux = aux;
        p.noise = noise;
    }

    private static boolean open(byte t) {
        return t == Level228Maze.T_FLOOR || t == Level228Maze.T_ARCH || t == Level228Maze.T_FRAME
                || t == Level228Maze.T_CURTAIN || t == Level228Maze.T_BLOCKER;
    }

    private static int architecture(Level228Maze m, long bits, int x, int z, Prop[] out, int n) {
        if (n >= out.length - 8) {
            return n;
        }
        int i = Level228Maze.idx(x, z);
        byte t = m.typeAt(bits, x, z);
        // (A node in the void can still anchor the decal of a cell whose other blocks are open:
        // the hallway starts one block past a node line, so check decals before skipping it.)
        int f = m.flags[i];
        int zv = m.zone[i];
        int ri = m.room[i];
        Room room = ri >= 0 ? m.rooms[ri] : null;
        boolean lineX = Math.floorMod(x, 3) == 0;
        boolean lineZ = Math.floorMod(z, 3) == 0;
        // Floor and ceiling decals, one per 3 x 3 cell, anchored at its node.
        if (lineX && lineZ) {
            int cell = -1;
            for (int ax = 0; ax <= 3 && cell < 0; ax++) {
                for (int az = 0; az <= 3 && cell < 0; az++) {
                    if (Level228Maze.inGrid(x + ax, z + az) && open(m.typeAt(bits, x + ax, z + az))) {
                        cell = Level228Maze.idx(x + ax, z + az);
                    }
                }
            }
            if (cell >= 0) {
                int inner = Level228Maze.inGrid(x + 1, z + 1) && open(m.typeAt(bits, x + 1, z + 1))
                        ? Level228Maze.idx(x + 1, z + 1) : cell;
                int cf = m.flags[inner];
                int cz = m.zone[inner];
                Room cr = m.room[inner] >= 0 ? m.rooms[m.room[inner]] : null;
                int rot = orientRot(cr, cf, 0, 1, 0);
                int fl = orientFlip(cf);
                int cx2 = 2 * x + 4;
                int cz2 = 2 * z + 4;
                int nx = (cx2 - 1 - Level228Maze.rotX(rot, 3, 3)) >> 1;
                int nz = (cz2 - 1 - Level228Maze.rotZ(rot, 3, 3)) >> 1;
                put(out, n++, P_FLOOR, cz, nx, FEET_Y, nz, rot, fl, 0, m.canon[inner]);
                put(out, n++, P_CEILING, cz, nx, CEILING_Y, nz, rot, fl, 0, m.canon[inner] * 31 + 7);
            }
        }
        if (t == Level228Maze.T_VOID) {
            return n;
        }
        if (t == Level228Maze.T_POST) {
            put(out, n++, P_COLUMN, zv, x, FEET_Y, z, orientRot(room, f, 0, 1, 0), orientFlip(f), 0, m.canon[i]);
            return n;
        }
        if (t == Level228Maze.T_SOLID) {
            // Wall faces toward open space (backstage plywood, foyer paint, the hallway's tiles).
            for (int d = 0; d < 4; d++) {
                int nx = x + (d == 1 ? 1 : d == 3 ? -1 : 0);
                int nz = z + (d == 0 ? -1 : d == 2 ? 1 : 0);
                if (!Level228Maze.inGrid(nx, nz)) {
                    continue;
                }
                byte nt = m.typeAt(bits, nx, nz);
                if (nt != Level228Maze.T_FLOOR && nt != Level228Maze.T_CURTAIN && nt != Level228Maze.T_ARCH
                        && nt != Level228Maze.T_FRAME) {
                    continue;
                }
                int ni = Level228Maze.idx(nx, nz);
                int nf = m.flags[ni];
                int face = d;
                if ((nf & Level228Maze.C_FLIP_A) != 0 && m.room[ni] < 0 && (face & 1) == 1) {
                    face ^= 2;
                }
                if ((nf & Level228Maze.C_FLIP_B) != 0 && m.room[ni] < 0 && (face & 1) == 0) {
                    face ^= 2;
                }
                put(out, n++, P_WALL, m.zone[ni], x, FEET_Y, z, d, m.room[ni] < 0 ? orientFlip(nf) : 0, 0,
                        m.canon[i] * 4 + face);
            }
            return n;
        }
        // Segments: emitted once, at the segment's first block.
        if (room != null && lineX != lineZ) {
            boolean alongZ = lineX; // a line of constant x carries a pane running along z
            int along = alongZ ? z : x;
            if (Math.floorMod(along, 3) != 1) {
                return n;
            }
            int bu = room.localU(x, z);
            boolean uLine = Math.floorMod(bu, 3) == 0;
            int rot = orientRot(room, f, uLine ? 1 : 0, uLine ? 0 : 1, 0);
            int fl = orientFlip(f);
            return segment(m, t, i, x, z, alongZ, rot, fl, zv, out, n);
        }
        if (room == null && t == Level228Maze.T_MIRROR) {
            int id = m.mirror[i];
            if (id < 0 || m.mMin[id] != (m.mAxis[id] == 0 ? z : x)) {
                return n;
            }
            boolean alongZ = m.mAxis[id] == 0;
            int front = m.mFront[id];
            int rot = alongZ ? Level228Maze.rotOfFront(front, 0) : Level228Maze.rotOfFront(0, front);
            return segment(m, t, i, x, z, alongZ, rot, 0, zv, out, n);
        }
        return n;
    }

    private static int segment(Level228Maze m, byte t, int i, int x, int z, boolean alongZ, int rot, int fl, int zv,
                               Prop[] out, int n) {
        int type;
        int kind = 0;
        int aux = -1;
        switch (t) {
            case Level228Maze.T_MIRROR, Level228Maze.T_DISTORT -> {
                type = P_PANEL;
                kind = PANEL_MIRROR;
                aux = m.mirror[i];
            }
            case Level228Maze.T_GLASS -> {
                type = P_PANEL;
                kind = PANEL_GLASS;
            }
            case Level228Maze.T_PANEL -> {
                type = P_PANEL;
                kind = PANEL_SOLID;
            }
            case Level228Maze.T_FRAME -> {
                type = P_PANEL;
                kind = PANEL_EMPTY_FRAME;
            }
            case Level228Maze.T_ARCH -> type = P_OPENING;
            case Level228Maze.T_CURTAIN -> type = P_CURTAIN;
            default -> {
                return n;
            }
        }
        int flags = fl | kind << PF_PANEL_SHIFT;
        if ((m.flags[i] & Level228Maze.C_SECRET) != 0 && t == Level228Maze.T_PANEL) {
            flags |= PF_SECRET;
        }
        // Footprint centre of the 2-block segment, doubled, then the anchor for this rotation.
        int cx2 = alongZ ? 2 * x + 1 : 2 * x + 2;
        int cz2 = alongZ ? 2 * z + 2 : 2 * z + 1;
        int ax = (cx2 - 1 - Level228Maze.rotX(rot, 1, 0)) >> 1;
        int az = (cz2 - 1 - Level228Maze.rotZ(rot, 1, 0)) >> 1;
        put(out, n++, type, zv, ax, FEET_Y, az, rot, flags, aux, m.canon[i]);
        return n;
    }

    /**
     * World rotation of a prop whose canonical (copy 0) front is room-local (lu, lv), seen at a
     * column with reflection parity flags f; outside rooms canonical rotation r0 in world terms.
     */
    private static int orientRot(Room room, int f, int lu, int lv, int r0) {
        if (room == null) {
            int rot = r0;
            if ((f & Level228Maze.C_FLIP_A) != 0) {
                rot = (4 - rot) & 3;
            }
            if ((f & Level228Maze.C_FLIP_B) != 0) {
                rot = (2 - rot) & 3;
            }
            return rot;
        }
        int fu = (f & Level228Maze.C_FLIP_A) != 0 ? -lu : lu;
        int fv = (f & Level228Maze.C_FLIP_B) != 0 ? -lv : lv;
        return Level228Maze.rotOfFront(room.ux * fu + room.vx * fv, room.uz * fu + room.vz * fv);
    }

    /** FLIP of a reflected copy: one per reflection (a double reflection is a half turn). */
    private static int orientFlip(int f) {
        int a = (f & Level228Maze.C_FLIP_A) != 0 ? 1 : 0;
        int b = (f & Level228Maze.C_FLIP_B) != 0 ? 1 : 0;
        return (a ^ b) != 0 ? PF_FLIP : 0;
    }

    // ==========================================================================================
    // Places (for the rules, commands, locate and tests)
    // ==========================================================================================

    /** Where you step out of the foyer's arrival mirror: {x, y, z, yaw}. */
    public static double[] arrival(long seed) {
        return new double[] {Level228Maze.ARRIVAL_X + 1.0, FEET_Y, Level228Maze.ARRIVAL_LINE - 0.7, 180.0};
    }

    /**
     * The wrong mirrors' checkpoint: the first cell of the Glass Labyrinth, against its side
     * perimeter mirror (you step out of it): {x, y, z, yaw}.
     */
    public static double[] checkpoint(long seed) {
        Room r = maze(seed).rooms[3];
        double x = r.contX(2.0, 2.0);
        double z = r.contZ(2.0, 2.0);
        return new double[] {x, FEET_Y, z, yawOf(r.ux, r.uz)};
    }

    /** Where the correct mirror lets you out: in front of the hallway's entry mirror: {x, y, z, yaw}. */
    public static double[] hallEntry(long seed) {
        return new double[] {Level228Maze.HALL_MIRROR_X + 1.0, FEET_Y, 1.7, 0.0};
    }

    /** A spot to start in each zone (debug teleports): {x, y, z, yaw}. */
    public static double[] zoneStart(long seed, Zone zone) {
        Level228Maze m = maze(seed);
        return switch (zone) {
            case FOYER -> arrival(seed);
            case CRYSTAL, ALHAMBRA, FUNHOUSE, GLASS -> {
                Room r = m.rooms[zone.ordinal() - Zone.CRYSTAL.ordinal()];
                // Past the entrance curtain: the nearest cell (by walking) at least two rows
                // into the hall, facing into it, away from the backdrop.
                int[] cell = startCell(m, r);
                double u = 3.0 * cell[0] + 2.0;
                double v = 3.0 * cell[1] + 2.0;
                yield new double[] {r.contX(u, v), FEET_Y, r.contZ(u, v), yawOf(r.vx, r.vz)};
            }
            case CHAMBER -> new double[] {9.5, FEET_Y, 45.0, -90.0};
            case EXIT_HALL -> hallEntry(seed);
            default -> new double[] {27.5, FEET_Y, 2.0, -90.0};
        };
    }

    /** BFS from the room's entrance cell to the nearest cell two or more rows in: {u, v}. */
    private static int[] startCell(Level228Maze m, Room r) {
        int w = r.w;
        int h = r.h;
        int[] dist = new int[w * h];
        java.util.Arrays.fill(dist, -1);
        int[] queue = new int[w * h];
        int head = 0;
        int tail = 0;
        int start = r.curtainU(0);
        queue[tail++] = start;
        dist[start] = 0;
        int best = start;
        while (head < tail) {
            int c = queue[head++];
            int cu = c % w;
            int cv = c / w;
            if (cv >= 2) {
                return new int[] {cu, cv};
            }
            if (cv > best / w) {
                best = c;
            }
            for (int d = 0; d < 4; d++) {
                int nu = cu + (d == 0 ? -1 : d == 1 ? 1 : 0);
                int nv = cv + (d == 2 ? -1 : d == 3 ? 1 : 0);
                if (nu < 0 || nv < 0 || nu >= w || nv >= h || dist[nv * w + nu] >= 0) {
                    continue;
                }
                int bu = d == 0 ? 3 * cu : d == 1 ? 3 * (cu + 1) : 3 * cu + 1;
                int bv = d == 2 ? 3 * cv : d == 3 ? 3 * (cv + 1) : 3 * cv + 1;
                if (m.walkable(0, r.worldX(bu, bv), r.worldZ(bu, bv))) {
                    dist[nv * w + nu] = dist[c] + 1;
                    queue[tail++] = nv * w + nu;
                }
            }
        }
        return new int[] {best % w, best / w};
    }

    /** The centre of the final chamber {x, z} (what /backrooms locate points at). */
    public static int[] chamberCentre(long seed) {
        return new int[] {9, 45};
    }

    /** The exit door at the end of the hallway {x, z}. */
    public static int[] exitDoor(long seed) {
        return new int[] {Level228Maze.HALL_MIRROR_X + 1, Level228Maze.DOOR_LINE};
    }

    /** True inside the door's vestibule (standing there = ready to leave). */
    public static boolean inVestibule(long seed, double x, double z) {
        return x >= Level228Maze.VEST_X0 && x < Level228Maze.VEST_X1 + 1 && z >= Level228Maze.VEST_Z0 && z < Level228Maze.VEST_Z1 + 1;
    }

    /** The closed secret panel's bit at column (x, z) in state s, or -1. */
    public static int secretAt(long seed, ShiftState s, int x, int z) {
        if (!Level228Maze.inGrid(x, z)) {
            return -1;
        }
        Level228Maze m = maze(seed);
        int i = Level228Maze.idx(x, z);
        if ((m.flags[i] & (Level228Maze.C_SECRET | Level228Maze.C_SEALED)) != Level228Maze.C_SECRET || s.on(m.bit[i])) {
            return -1;
        }
        return m.bit[i];
    }

    /**
     * Every column (packed {@code x << 16 | z & 0xFFFF}) whose blocks change with this ShiftState
     * bit, real and imaged. Shared array: do not modify.
     */
    public static int[] columnsOfBit(long seed, int bit) {
        return maze(seed).bitColumns[bit];
    }

    /** Number of ShiftState bits in use (shift classes 0..7, secret panels 8..11). */
    public static int bitCount() {
        return Level228Maze.BITS;
    }

    /** True for the bits of shifting classes (the rest are secret panels). */
    public static boolean isShiftBit(int bit) {
        return bit >= 0 && bit < Level228Maze.SECRET_BIT0;
    }

    /** The real room a bit belongs to: {minX, minZ, maxX, maxZ} (world blocks, inclusive). */
    public static int[] bitRoomBounds(long seed, int bit) {
        Room r = maze(seed).rooms[bit < Level228Maze.SECRET_BIT0 ? bit / 2 : bit - Level228Maze.SECRET_BIT0];
        int xa = r.worldX(0, 0);
        int za = r.worldZ(0, 0);
        int xb = r.worldX(3 * r.w, 3 * r.h);
        int zb = r.worldZ(3 * r.w, 3 * r.h);
        return new int[] {Math.min(xa, xb), Math.min(za, zb), Math.max(xa, xb), Math.max(za, zb)};
    }

    /** True if column (x, z) changes with the shift state (a shifting pane or a secret panel, real or imaged). */
    public static boolean isDynamic(long seed, int x, int z) {
        return Level228Maze.inGrid(x, z) && maze(seed).bit[Level228Maze.idx(x, z)] >= 0;
    }

    /**
     * True if the room's entrance -> exit walk exists in state s (BFS over its cells); with the
     * room index 0..3 (Crystal, Alhambra, Funhouse, Glass). For the tests and the scheduler.
     */
    public static int roomWalk(long seed, ShiftState s, int roomIndex) {
        Level228Maze m = maze(seed);
        Room r = m.rooms[roomIndex];
        synchronized (Level228Maze.class) {
            return m.roomPath(r, s.bits, r.curtainU(0), r.curtainU(r.n - 1));
        }
    }

    /** Yaw (degrees, Minecraft) of facing world direction (dx, dz). */
    static double yawOf(int dx, int dz) {
        return Math.toDegrees(Math.atan2(-dx, dz));
    }
}
