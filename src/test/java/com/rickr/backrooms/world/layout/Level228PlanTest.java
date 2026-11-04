package com.rickr.backrooms.world.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.layout.Level228Maze.Room;
import com.rickr.backrooms.world.layout.Level228Plan.ShiftState;
import com.rickr.backrooms.world.layout.Level228Plan.Zone;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;

/**
 * Level 228 v2's floor plan (pure, runs without Minecraft):
 * <ul>
 *   <li>symmetry: around every twin mirror, the room is exactly mirror-symmetric to fog depth,
 *       and every sealed image is the exact reflection of the space it shows;</li>
 *   <li>sealing: walking from the foyer (through every opening, curtain and empty frame) never
 *       reaches image space, and nothing walkable is cut off;</li>
 *   <li>reflections: for sampled cameras, rays traced physically (bouncing off the panes) and
 *       {@link Level228Plan#imagesOf} agree both ways;</li>
 *   <li>escape: foyer -> the three chamber mirrors in every shift state, the checkpoint -> the
 *       chamber, hallway entry -> vestibule; every room's way through stays long.</li>
 * </ul>
 */
class Level228PlanTest {
    private static final long[] SEEDS = {1L, 42L, 0x5EEDL, -7L, 123456789L, 2024L, Long.MIN_VALUE + 1, 0xBACC00L};
    private static final int ALL_SHIFTS = 1 << 8;
    private static final long ALL_SECRETS = 0xFL << 8;

    private static Level228Maze maze(long seed) {
        return Level228Plan.maze(seed);
    }

    // ------------------------------------------------------------------------------------------
    // Symmetry
    // ------------------------------------------------------------------------------------------

    @Test
    void everyTwinMirrorsNeighbourhoodIsSymmetricToFogDepth() {
        for (long seed : SEEDS) {
            Level228Maze m = maze(seed);
            int twins = 0;
            for (int id = 0; id < m.mirrorCount; id++) {
                if (m.mKind[id] != Level228Plan.K_TWIN || (m.mFlags[id] & Level228Plan.MF_IMAGE) != 0) {
                    continue;
                }
                twins++;
                int axis = m.mAxis[id];
                int line = (int) Math.floor(m.mPlane[id]);
                int roomIndex = m.room[Level228Maze.idx(axis == 0 ? line : (int) m.mMin[id], axis == 0 ? (int) m.mMin[id] : line)];
                for (int d = 1; d <= Level228Plan.FOG_DEPTH; d++) {
                    for (int a = Level228Maze.MIN_X; a < Level228Maze.MIN_X + Level228Maze.SIZE_X; a++) {
                        int x1 = axis == 0 ? line - d : a;
                        int z1 = axis == 0 ? a : line - d;
                        int x2 = axis == 0 ? line + d : a;
                        int z2 = axis == 0 ? a : line + d;
                        if (!Level228Maze.inGrid(x1, z1) || !Level228Maze.inGrid(x2, z2)) {
                            continue;
                        }
                        int i = Level228Maze.idx(x1, z1);
                        int j = Level228Maze.idx(x2, z2);
                        if (m.room[i] != roomIndex || m.room[j] != roomIndex || skip(m, i) || skip(m, j)) {
                            continue;
                        }
                        String where = "seed " + seed + " mirror " + id + " at " + x1 + "," + z1 + " vs " + x2 + "," + z2;
                        Room room = m.rooms[roomIndex];
                        boolean per = perimeter(room, x1, z1) || perimeter(room, x2, z2);
                        assertEquals(norm(m.type[i], per), norm(m.type[j], per), "type differs across a twin mirror: " + where);
                        assertEquals(m.bit[i], m.bit[j], "shift bit differs across a twin mirror: " + where);
                        assertEquals(norm(m.alt[i], per), norm(m.alt[j], per), "shifted type differs across a twin mirror: " + where);
                        assertEquals(m.light[i], m.light[j], "light differs across a twin mirror: " + where);
                    }
                }
            }
            assertTrue(twins > 40, "too few twin mirrors for seed " + seed + ": " + twins);
        }
    }

    private static boolean skip(Level228Maze m, int i) {
        return m.type[i] == Level228Maze.T_VOID || (m.flags[i] & (Level228Maze.C_EDGE | Level228Maze.C_ANOMALY)) != 0;
    }

    @Test
    void sealedImagesAreTheReflectionOfWhatTheyShow() {
        for (long seed : SEEDS) {
            Level228Maze m = maze(seed);
            // Room images: fold every sealed column back into its room.
            for (Room r : m.rooms) {
                int w3 = 3 * r.w;
                int h3 = 3 * r.h;
                for (int bu = -Level228Plan.FOG_DEPTH + 1; bu < w3 + Level228Plan.FOG_DEPTH; bu++) {
                    for (int bv = 0; bv < h3 + Level228Plan.FOG_DEPTH; bv++) {
                        if (bu >= 0 && bu <= w3 && bv <= h3) {
                            continue;
                        }
                        int i = Level228Maze.idx(r.worldX(bu, bv), r.worldZ(bu, bv));
                        if (m.room[i] != r.index || skip(m, i)) {
                            continue;
                        }
                        assertTrue((m.flags[i] & Level228Maze.C_SEALED) != 0, "image column not sealed");
                        int su = Math.floorMod(bu, 2 * w3);
                        su = su > w3 ? 2 * w3 - su : su;
                        int sv = bv > h3 ? 2 * h3 - bv : bv;
                        int j = Level228Maze.idx(r.worldX(su, sv), r.worldZ(su, sv));
                        if (skip(m, j)) {
                            continue;
                        }
                        byte real = m.type[j];
                        byte image = m.type[i];
                        if (bu == 0 || bu == w3) {
                            // the perimeter line's own images (corner): openings are glass there
                            real = Level228Maze.walkableType(real) && real != Level228Maze.T_CURTAIN ? Level228Maze.T_GLASS : real;
                        }
                        assertEquals(real, image, "seed " + seed + " room " + r.zone + " image at local " + bu + "," + bv);
                        assertEquals(m.light[j], m.light[i], "light of the image at local " + bu + "," + bv);
                    }
                }
            }
            // Special images: foyer (z -> 38 - z), chamber (x -> 30 - x), hallway (z -> -z).
            checkImage(m, 3, 20, 21, 37, true, 38);
            checkImage(m, 16, 39, 29, 50, false, 30);
            checkImage(m, Level228Maze.HALL_X0 - 1, -Level228Maze.HALL_Z1, Level228Maze.HALL_X1 + 1, -1, true, 0);
        }
    }

    private static void checkImage(Level228Maze m, int x0, int z0, int x1, int z1, boolean alongZ, int k) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int i = Level228Maze.idx(x, z);
                int j = Level228Maze.idx(alongZ ? x : k - x, alongZ ? k - z : z);
                byte real = m.type[j] == Level228Maze.T_POST ? Level228Maze.T_BLOCKER : m.type[j];
                assertEquals(real, m.type[i], "special image at " + x + "," + z);
                assertTrue((m.flags[i] & Level228Maze.C_SEALED) != 0, "special image not sealed at " + x + "," + z);
            }
        }
        // Lights of the chamber image may differ on purpose (the unlit-candle flaw); others must match.
        if (k != 30) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    int i = Level228Maze.idx(x, z);
                    int j = Level228Maze.idx(alongZ ? x : k - x, alongZ ? k - z : z);
                    assertEquals(m.light[j], m.light[i], "special image light at " + x + "," + z);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Sealing and escape
    // ------------------------------------------------------------------------------------------

    /** Columns physically passable in state bits (air: openings, curtains outside images, floors). */
    private static boolean passable(Level228Maze m, long bits, int x, int z) {
        if (!Level228Maze.inGrid(x, z)) {
            return false;
        }
        byte t = m.typeAt(bits, x, z);
        int f = m.flags[Level228Maze.idx(x, z)];
        if (t == Level228Maze.T_CURTAIN) {
            return (f & Level228Maze.C_SEALED) == 0; // a sealed curtain has a pane behind it
        }
        return t == Level228Maze.T_FLOOR || t == Level228Maze.T_ARCH || t == Level228Maze.T_FRAME;
    }

    /** Flood fill of physically passable space from (x, z). */
    private static boolean[] flood(Level228Maze m, long bits, int x, int z) {
        boolean[] seen = new boolean[Level228Maze.SIZE_X * Level228Maze.SIZE_Z];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        seen[Level228Maze.idx(x, z)] = true;
        q.add(new int[] {x, z});
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int d = 0; d < 4; d++) {
                int nx = c[0] + (d == 0 ? 1 : d == 1 ? -1 : 0);
                int nz = c[1] + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if (passable(m, bits, nx, nz) && !seen[Level228Maze.idx(nx, nz)]) {
                    seen[Level228Maze.idx(nx, nz)] = true;
                    q.add(new int[] {nx, nz});
                }
            }
        }
        return seen;
    }

    private static long[] someStates(long seed) {
        long[] states = new long[24];
        states[0] = 0;
        states[1] = 0xFF;
        states[2] = ALL_SECRETS;
        states[3] = 0xFF | ALL_SECRETS;
        for (int i = 4; i < states.length; i++) {
            states[i] = Hash.of(seed, i, 0, 0x7E57) & 0xFFF;
        }
        return states;
    }

    @Test
    void sealedVolumesAreSealedAndNothingIsCutOff() {
        for (long seed : SEEDS) {
            Level228Maze m = maze(seed);
            double[] a = Level228Plan.arrival(seed);
            for (long bits : someStates(seed)) {
                boolean[] reach = flood(m, bits, (int) Math.floor(a[0]), (int) Math.floor(a[2]));
                for (int i = 0; i < reach.length; i++) {
                    int x = i / Level228Maze.SIZE_Z + Level228Maze.MIN_X;
                    int z = i % Level228Maze.SIZE_Z + Level228Maze.MIN_Z;
                    boolean sealed = (m.flags[i] & Level228Maze.C_SEALED) != 0;
                    if (reach[i]) {
                        assertTrue(!sealed, "seed " + seed + " state " + Long.toHexString(bits) + ": walked into image space at " + x + "," + z);
                    } else if (!sealed && m.walkable(bits, x, z) && m.zoneAt(x, z) != Zone.EXIT_HALL) {
                        throw new AssertionError("seed " + seed + " state " + Long.toHexString(bits) + ": cut off at " + x + "," + z
                                + " (" + m.zoneAt(x, z) + ")");
                    }
                }
                double[] h = Level228Plan.hallEntry(seed);
                boolean[] hall = flood(m, bits, (int) Math.floor(h[0]), (int) Math.floor(h[2]));
                for (int i = 0; i < hall.length; i++) {
                    if (hall[i]) {
                        assertTrue((m.flags[i] & Level228Maze.C_SEALED) == 0, "walked into the hallway's image");
                        assertEquals(Zone.EXIT_HALL.ordinal(), m.zone[i], "the hallway leaks");
                    }
                }
            }
        }
    }

    @Test
    void foyerReachesEveryChamberMirrorInEveryShiftState() {
        for (long seed : SEEDS) {
            Level228Maze m = maze(seed);
            double[] a = Level228Plan.arrival(seed);
            double[] cp = Level228Plan.checkpoint(seed);
            for (int s = 0; s < ALL_SHIFTS; s++) {
                for (long secrets : new long[] {0, ALL_SECRETS}) {
                    long bits = s | secrets;
                    boolean[] reach = flood(m, bits, (int) Math.floor(a[0]), (int) Math.floor(a[2]));
                    boolean[] fromCheckpoint = flood(m, bits, (int) Math.floor(cp[0]), (int) Math.floor(cp[2]));
                    for (int k = 0; k < 3; k++) {
                        int front = Level228Maze.idx(Level228Maze.CHAMBER_LINE - 1, Level228Maze.CHAMBER_MIRROR_Z + 3 * k);
                        assertTrue(reach[front], "seed " + seed + " state " + Long.toHexString(bits) + ": chamber mirror " + k + " unreachable");
                        assertTrue(fromCheckpoint[front], "the checkpoint can't reach the chamber");
                    }
                }
            }
            for (int r = 0; r < 4; r++) {
                for (int s = 0; s < 4; s++) {
                    long bits = ((s & 1) != 0 ? 1L << 2 * r : 0) | ((s & 2) != 0 ? 1L << 2 * r + 1 : 0);
                    int len = Level228Plan.roomWalk(seed, new ShiftState(bits, 0), r);
                    Room room = m.rooms[r];
                    assertTrue(len >= room.w + room.h / 2, "room " + room.zone + " too easy (" + len + " cells) for seed " + seed);
                }
            }
            double[] h = Level228Plan.hallEntry(seed);
            boolean[] hall = flood(m, 0, (int) Math.floor(h[0]), (int) Math.floor(h[2]));
            assertTrue(hall[Level228Maze.idx(Level228Maze.VEST_X0 + 1, Level228Maze.VEST_Z0 + 1)], "the hallway doesn't reach its door");
            assertTrue(Level228Plan.inVestibule(seed, Level228Maze.VEST_X0 + 1.5, Level228Maze.VEST_Z0 + 1.5), "vestibule");
            assertEquals(Zone.GLASS, Level228Plan.zoneAt(seed, (int) Math.floor(cp[0]), (int) Math.floor(cp[2])), "checkpoint zone");
            assertTrue(Level228Plan.isWalkable(seed, ShiftState.INITIAL, (int) Math.floor(cp[0]), Level228Plan.FEET_Y, (int) Math.floor(cp[2])));
            assertTrue(Level228Plan.isWalkable(seed, ShiftState.INITIAL, (int) Math.floor(a[0]), Level228Plan.FEET_Y, (int) Math.floor(a[2])));
        }
    }

    @Test
    void theZoneSequenceAndThePuzzleAreFixedPerWorld() {
        for (long seed : SEEDS) {
            int answer = Level228Plan.correctExitMirror(seed);
            assertTrue(answer >= 0 && answer < 3);
            int wrongFlaws = 0;
            for (int k = 0; k < 3; k++) {
                int f = Level228Plan.exitFlaw(seed, k);
                assertEquals(k == answer, f < 0, "exactly the answer has no flaw");
                wrongFlaws += f >= 0 ? 1 : 0;
            }
            assertEquals(2, wrongFlaws);
            assertTrue(Level228Plan.exitFlaw(seed, (answer + 1) % 3) != Level228Plan.exitFlaw(seed, (answer + 2) % 3),
                    "the two wrong mirrors lie differently");
            for (Zone z : new Zone[] {Zone.FOYER, Zone.CRYSTAL, Zone.ALHAMBRA, Zone.FUNHOUSE, Zone.GLASS, Zone.CHAMBER, Zone.EXIT_HALL}) {
                double[] at = Level228Plan.zoneStart(seed, z);
                assertEquals(z, Level228Plan.zoneAt(seed, (int) Math.floor(at[0]), (int) Math.floor(at[2])), "zone start of " + z);
                assertTrue(Level228Plan.isWalkable(seed, ShiftState.INITIAL, (int) Math.floor(at[0]), Level228Plan.FEET_Y,
                        (int) Math.floor(at[2])), "zone start walkable: " + z);
                if (z.ordinal() >= Zone.CRYSTAL.ordinal() && z.ordinal() <= Zone.GLASS.ordinal()) {
                    // Facing (Minecraft yaw: 0 = +z, 90 = -x) away from the backdrop, into the hall.
                    Room r = maze(seed).rooms[z.ordinal() - Zone.CRYSTAL.ordinal()];
                    double fx = -Math.sin(Math.toRadians(at[3]));
                    double fz = Math.cos(Math.toRadians(at[3]));
                    double ahead = r.contV(at[0] + 2 * fx, at[2] + 2 * fz) - r.contV(at[0], at[2]);
                    assertEquals(2.0, ahead, 1e-6, "zone start of " + z + " doesn't face into the hall");
                    assertTrue(r.contV(at[0], at[2]) > 4, "zone start of " + z + " right against the curtain");
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Reflections
    // ------------------------------------------------------------------------------------------

    /** Result of a physical trace: bounces, or -1 if blocked / through an anomaly. */
    private static final class Ray {
        double x, z;
        int bounces;
        int firstMirror = -1;
        boolean distort;
    }

    private static final double EYE = Level228Plan.FEET_Y + 1.6;

    /**
     * A room perimeter (and its images) is clean glass where the other lines of its class have
     * an empty frame: both see-through, so for the optics they are the same.
     */
    private static boolean perimeter(Room r, int x, int z) {
        return Math.floorMod(r.localU(x, z), 3 * r.w) == 0;
    }

    private static byte norm(byte t, boolean perimeter) {
        boolean seeThrough = t == Level228Maze.T_GLASS || t == Level228Maze.T_FRAME || t == Level228Maze.T_ARCH;
        return perimeter && seeThrough ? Level228Maze.T_FRAME : t;
    }

    /** False if the straight (unfolded) segment passes a deliberate anomaly or the end of an image. */
    private static boolean clean(Level228Maze m, double ax, double az, double bx, double bz) {
        int steps = (int) Math.ceil(Math.hypot(bx - ax, bz - az) * 8) + 1;
        for (int s = 0; s <= steps; s++) {
            double t = s / (double) steps;
            int x = (int) Math.floor(ax + (bx - ax) * t);
            int z = (int) Math.floor(az + (bz - az) * t);
            if (!Level228Maze.inGrid(x, z) || (m.flags[Level228Maze.idx(x, z)] & (Level228Maze.C_ANOMALY | Level228Maze.C_EDGE)) != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Traces a horizontal ray physically: reflecting at mirror panes (plane through the block
     * centre), passing glass and openings, stopping at anything opaque (the same rules as the
     * plan). Returns false if blocked, too deep or through a deliberate anomaly.
     */
    private static boolean physical(Level228Maze m, long bits, double x, double z, double dx, double dz, double length, Ray out) {
        out.bounces = 0;
        out.firstMirror = -1;
        out.distort = false;
        double left = length;
        int lastId = -1;
        for (int seg = 0; seg < 8; seg++) {
            double ex = x + dx * left;
            double ez = z + dz * left;
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
            int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
            double ddx = ex - x;
            double ddz = ez - z;
            double tDX = stepX != 0 ? 1.0 / Math.abs(ddx) : Double.POSITIVE_INFINITY;
            double tDZ = stepZ != 0 ? 1.0 / Math.abs(ddz) : Double.POSITIVE_INFINITY;
            double tMX = stepX > 0 ? (bx + 1 - x) / ddx : stepX < 0 ? (x - bx) / -ddx : Double.POSITIVE_INFINITY;
            double tMZ = stepZ > 0 ? (bz + 1 - z) / ddz : stepZ < 0 ? (z - bz) / -ddz : Double.POSITIVE_INFINITY;
            double t0 = 0;
            boolean bounced = false;
            for (int g = 0; g < 512 && !bounced; g++) {
                double t1 = Math.min(1.0, Math.min(tMX, tMZ));
                if (!Level228Maze.inGrid(bx, bz)) {
                    return false;
                }
                int i = Level228Maze.idx(bx, bz);
                if ((m.flags[i] & (Level228Maze.C_ANOMALY | Level228Maze.C_EDGE)) != 0) {
                    return false;
                }
                byte t = m.typeAt(bits, bx, bz);
                switch (t) {
                    case Level228Maze.T_FLOOR, Level228Maze.T_ARCH, Level228Maze.T_FRAME -> { }
                    case Level228Maze.T_POST -> {
                        if (Level228Plan.crossesCentre(x, ddx, bx, t0, t1) || Level228Plan.crossesCentre(z, ddz, bz, t0, t1)) {
                            return false;
                        }
                        double cx = bx + 0.5 - x;
                        double cz = bz + 0.5 - z;
                        double len2 = ddx * ddx + ddz * ddz;
                        double tt = Math.max(t0, Math.min(t1, (cx * ddx + cz * ddz) / len2));
                        double ox = x + ddx * tt - bx - 0.5;
                        double oz = z + ddz * tt - bz - 0.5;
                        if (ox * ox + oz * oz < 0.27 * 0.27) {
                            return false;
                        }
                    }
                    case Level228Maze.T_MIRROR, Level228Maze.T_GLASS, Level228Maze.T_PANEL, Level228Maze.T_DISTORT -> {
                        int id = m.mirror[i];
                        int axis = id >= 0 ? m.mAxis[id] : Math.floorMod(bx, 3) == 0 ? 0 : 1;
                        double plane = (axis == 0 ? bx : bz) + 0.5;
                        double comp = axis == 0 ? ddx : ddz;
                        if (comp != 0) {
                            double tc = (plane - (axis == 0 ? x : z)) / comp;
                            if (tc >= t0 - 1e-9 && tc <= t1 + 1e-9 && tc > 1e-7 && tc < 1 - 1e-7) {
                                if (t == Level228Maze.T_PANEL || (t == Level228Maze.T_MIRROR || t == Level228Maze.T_DISTORT) && id < 0) {
                                    return false;
                                }
                                if (t != Level228Maze.T_GLASS) {
                                    if (out.bounces == 0) {
                                        out.firstMirror = id;
                                    }
                                    out.distort |= t == Level228Maze.T_DISTORT;
                                    out.bounces++;
                                    lastId = id;
                                    x += ddx * tc;
                                    z += ddz * tc;
                                    left -= left * tc;
                                    if (axis == 0) {
                                        dx = -dx;
                                    } else {
                                        dz = -dz;
                                    }
                                    bounced = true;
                                    if (out.bounces > Level228Plan.MAX_DEPTH) {
                                        return false;
                                    }
                                }
                            }
                        }
                    }
                    default -> {
                        return false;
                    }
                }
                if (bounced || t1 >= 1.0) {
                    break;
                }
                if (tMX < tMZ) {
                    bx += stepX;
                    t0 = tMX;
                    tMX += tDX;
                } else {
                    bz += stepZ;
                    t0 = tMZ;
                    tMZ += tDZ;
                }
            }
            if (!bounced) {
                out.x = ex;
                out.z = ez;
                return true;
            }
            // Leave the plane before tracing on, so the same pane isn't hit twice.
            x += dx * 1e-6;
            z += dz * 1e-6;
        }
        return false;
    }

    @Test
    void physicalRaysAndImagesAgree() {
        Level228Plan.Image[] out = new Level228Plan.Image[256];
        for (int i = 0; i < out.length; i++) {
            out[i] = new Level228Plan.Image();
        }
        Ray ray = new Ray();
        for (long seed : SEEDS) {
            Level228Maze m = maze(seed);
            long bits = Hash.of(seed, 1, 2, 3) & 0xFF;
            ShiftState s = new ShiftState(bits, 1);
            int checked = 0;
            int found = 0;
            for (int sample = 0; sample < 6000; sample++) {
                long h = Hash.of(seed, sample, 9, 0x1A6E);
                Room r = m.rooms[Hash.range(h, 4)];
                double cu = 1 + Hash.unit(Hash.mix(h + 1)) * (3 * r.w - 2);
                double cv = 1 + Hash.unit(Hash.mix(h + 2)) * (3 * r.h - 2);
                double cx = r.contX(cu, cv);
                double cz = r.contZ(cu, cv);
                if (!Level228Plan.isWalkable(seed, s, (int) Math.floor(cx), Level228Plan.FEET_Y, (int) Math.floor(cz))) {
                    continue;
                }
                double ang = Hash.unit(Hash.mix(h + 3)) * 2 * Math.PI;
                double dx = Math.cos(ang);
                double dz = Math.sin(ang);
                double len = 3 + Hash.unit(Hash.mix(h + 4)) * 15;
                if (!physical(m, bits, cx, cz, dx, dz, len, ray) || ray.bounces < 1 || ray.distort
                        || !clean(m, cx, cz, cx + dx * len, cz + dz * len)) {
                    continue;
                }
                // Physical -> imagesOf: the point the ray reached must be seen at the unfolded end.
                double qx = cx + dx * len;
                double qz = cz + dz * len;
                double ex = ray.x;
                double ez = ray.z;
                int n = Level228Plan.imagesOf(seed, s, cx, EYE, cz, ex, EYE, ez, 24, out);
                boolean hit = false;
                for (int k = 0; k < n; k++) {
                    if (Math.abs(out[k].x - qx) < 1e-4 && Math.abs(out[k].z - qz) < 1e-4) {
                        hit = true;
                        assertEquals(ray.bounces, out[k].depth, "depth");
                        assertEquals(ray.firstMirror, out[k].lastMirror, "first mirror");
                    }
                }
                assertTrue(hit, "seed " + seed + ": image missing for camera " + cx + "," + cz + " -> " + ray.x + "," + ray.z
                        + " (" + ray.bounces + " bounces, unfolded " + qx + "," + qz + ")");
                checked++;
                // imagesOf -> physical: every reported image must be reachable by a real bouncing ray.
                for (int k = 0; k < n; k++) {
                    double ix = out[k].x - cx;
                    double iz = out[k].z - cz;
                    double d = Math.sqrt(ix * ix + iz * iz);
                    Level228Plan.Mirror mirror = new Level228Plan.Mirror();
                    Level228Plan.mirror(seed, s, out[k].lastMirror, mirror);
                    if (mirror.kind == Level228Plan.K_DISTORT) {
                        continue;
                    }
                    if (physical(m, bits, cx, cz, ix / d, iz / d, d, ray)) {
                        assertTrue(Math.abs(ray.x - ex) < 1e-4 && Math.abs(ray.z - ez) < 1e-4,
                                "seed " + seed + ": image " + out[k].x + "," + out[k].z + " leads to " + ray.x + "," + ray.z
                                        + " not " + ex + "," + ez);
                        found++;
                    }
                }
            }
            assertTrue(checked > 200, "too few reflected rays checked for seed " + seed + ": " + checked);
            assertTrue(found > 200, "too few images confirmed for seed " + seed + ": " + found);
        }
    }

    @Test
    void imagesOfEndAtTheRealPoint() {
        Level228Plan.Image[] out = new Level228Plan.Image[256];
        for (int i = 0; i < out.length; i++) {
            out[i] = new Level228Plan.Image();
        }
        Ray ray = new Ray();
        for (long seed : SEEDS) {
            Level228Maze m = maze(seed);
            ShiftState s = ShiftState.INITIAL;
            int confirmed = 0;
            for (int sample = 0; sample < 1500; sample++) {
                long h = Hash.of(seed, sample, 11, 0x1A6F);
                Room r = m.rooms[Hash.range(h, 4)];
                double cx = r.contX(1 + Hash.unit(Hash.mix(h + 1)) * (3 * r.w - 2), 1 + Hash.unit(Hash.mix(h + 2)) * (3 * r.h - 2));
                double cz = r.contZ(1 + Hash.unit(Hash.mix(h + 1)) * (3 * r.w - 2), 1 + Hash.unit(Hash.mix(h + 2)) * (3 * r.h - 2));
                double px = cx + (Hash.unit(Hash.mix(h + 3)) - 0.5) * 12;
                double pz = cz + (Hash.unit(Hash.mix(h + 4)) - 0.5) * 12;
                if (!Level228Plan.isWalkable(seed, s, (int) Math.floor(cx), Level228Plan.FEET_Y, (int) Math.floor(cz))
                        || !Level228Plan.isWalkable(seed, s, (int) Math.floor(px), Level228Plan.FEET_Y, (int) Math.floor(pz))) {
                    continue;
                }
                int n = Level228Plan.imagesOf(seed, s, cx, EYE, cz, px, EYE, pz, 24, out);
                for (int k = 0; k < n; k++) {
                    Level228Plan.Image img = out[k];
                    assertTrue(img.depth >= 1 && img.depth <= Level228Plan.MAX_DEPTH);
                    assertTrue(Level228Plan.behindMirror(seed, s, cx, EYE, cz, img.x, EYE, img.z), "an image is behind a mirror");
                    double ix = img.x - cx;
                    double iz = img.z - cz;
                    double d = Math.sqrt(ix * ix + iz * iz);
                    if (!physical(m, 0, cx, cz, ix / d, iz / d, d, ray) || ray.distort || !clean(m, cx, cz, img.x, img.z)) {
                        continue; // through an anomaly (the world is deliberately wrong there)
                    }
                    assertEquals(img.depth, ray.bounces, "bounces");
                    assertTrue(Math.abs(ray.x - px) < 1e-4 && Math.abs(ray.z - pz) < 1e-4,
                            "seed " + seed + ": the image at " + img.x + "," + img.z + " does not lead back to " + px + "," + pz
                                    + " but to " + ray.x + "," + ray.z);
                    // Every bounce flips exactly one world axis.
                    int parity = (img.flipX < 0 ? 1 : 0) + (img.flipZ < 0 ? 1 : 0);
                    assertEquals(img.depth % 2, parity % 2, "handedness");
                    confirmed++;
                }
            }
            assertTrue(confirmed > 150, "too few images confirmed for seed " + seed + ": " + confirmed);
        }
    }

    @Test
    void propsAndMirrorsAreConsistent() {
        Level228Plan.Prop[] props = new Level228Plan.Prop[4096];
        for (int i = 0; i < props.length; i++) {
            props[i] = new Level228Plan.Prop();
        }
        Level228Plan.Mirror[] mirrors = new Level228Plan.Mirror[512];
        for (int i = 0; i < mirrors.length; i++) {
            mirrors[i] = new Level228Plan.Mirror();
        }
        long seed = SEEDS[0];
        int panels = 0;
        int columns = 0;
        for (int cx = -4; cx < 12; cx++) {
            for (int cz = -4; cz < 8; cz++) {
                int n = Level228Plan.propsInChunk(seed, ShiftState.INITIAL, cx, cz, props);
                assertTrue(n < props.length, "prop array filled up");
                for (int k = 0; k < n; k++) {
                    Level228Plan.Prop p = props[k];
                    assertTrue(p.rot >= 0 && p.rot < 4);
                    if (p.type == Level228Plan.P_PANEL && (p.flags & Level228Plan.PF_PANEL_MASK) == 0) {
                        Level228Plan.Mirror mm = new Level228Plan.Mirror();
                        assertTrue(Level228Plan.mirror(seed, ShiftState.INITIAL, p.aux, mm), "mirror panel without a mirror");
                        panels++;
                    }
                    columns += p.type == Level228Plan.P_COLUMN ? 1 : 0;
                }
            }
        }
        assertTrue(panels > 200 && columns > 500, "panels " + panels + " columns " + columns);
        // Every open column (real or imaged) lies under a floor decal: decals cover node cell N..N+3.
        Level228Maze m = maze(seed);
        boolean[] covered = new boolean[Level228Maze.SIZE_X * Level228Maze.SIZE_Z];
        for (int cx = Level228Maze.MIN_X >> 4; cx < (Level228Maze.MIN_X + Level228Maze.SIZE_X) >> 4; cx++) {
            for (int cz = Level228Maze.MIN_Z >> 4; cz < (Level228Maze.MIN_Z + Level228Maze.SIZE_Z) >> 4; cz++) {
                int n = Level228Plan.propsInChunk(seed, ShiftState.INITIAL, cx, cz, props);
                for (int k = 0; k < n; k++) {
                    Level228Plan.Prop p = props[k];
                    if (p.type == Level228Plan.P_LITTER && p.variant == 7) {
                        assertEquals(Zone.CHAMBER, Level228Plan.zoneAt(seed, p.x, p.z), "ticket stub outside the chamber at " + p.x + "," + p.z);
                    }
                    if (p.type != Level228Plan.P_FLOOR) {
                        continue;
                    }
                    // Footprint centre = anchor + 0.5 + R(1.5, 1.5); the node is 2 below it.
                    int nx = p.x + (1 + Level228Maze.rotX(p.rot, 3, 3)) / 2 - 2;
                    int nz = p.z + (1 + Level228Maze.rotZ(p.rot, 3, 3)) / 2 - 2;
                    for (int x = nx; x <= nx + 3; x++) {
                        for (int z = nz; z <= nz + 3; z++) {
                            if (Level228Maze.inGrid(x, z)) {
                                covered[Level228Maze.idx(x, z)] = true;
                            }
                        }
                    }
                }
            }
        }
        for (int i = 0; i < covered.length; i++) {
            byte t = m.type[i];
            boolean open = t == Level228Maze.T_FLOOR || t == Level228Maze.T_ARCH || t == Level228Maze.T_FRAME || t == Level228Maze.T_CURTAIN;
            assertTrue(!open || covered[i], "no floor decal over " + (i / Level228Maze.SIZE_Z + Level228Maze.MIN_X) + ","
                    + (i % Level228Maze.SIZE_Z + Level228Maze.MIN_Z));
        }
        int near = Level228Plan.mirrorsNear(seed, ShiftState.INITIAL, 10, -15, 12, mirrors);
        assertTrue(near > 10, "mirrors near the Crystal Maze: " + near);
        for (int k = 0; k < near; k++) {
            assertEquals(Zone.CRYSTAL, mirrors[k].zone);
        }
        Level228Plan.Mirror a = new Level228Plan.Mirror();
        for (int k = 0; k < 3; k++) {
            assertTrue(Level228Plan.mirror(seed, ShiftState.INITIAL, Level228Plan.exitMirrorId(k), a));
            assertEquals(Level228Plan.K_EXIT_A + k, a.kind);
        }
    }
}
