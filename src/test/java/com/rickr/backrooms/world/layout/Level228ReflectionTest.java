package com.rickr.backrooms.world.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.layout.Level228Maze.Room;
import com.rickr.backrooms.world.layout.Level228Plan.ShiftState;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the reflections drawn by client/level228/Level228Reflections, checked
 * against an independent model: a ray bounced physically, pane by pane, from the camera towards
 * each reported image must arrive at the real point unobstructed. That is exactly the condition
 * for a stand-in to be honest; anything else is a ghost (e.g. someone standing behind an opaque
 * mirror showing up in it).
 *
 * <p>{@link #REVIEW_SEED} is the layout seed of the in-game review world (world seed
 * 61574245043016289, level_228 positional random), where the reports came from.
 */
class Level228ReflectionTest {
    /** The review world's Level 228 layout seed (B accurate, A nods, C's candle is out). */
    static final long REVIEW_SEED = 3421255727507504381L;
    private static final long[] SEEDS = {REVIEW_SEED, 1L, 42L, 0x5EEDL, -7L, 123456789L, 2024L, 0xBACC00L};
    private static final double EYE = Level228Plan.FEET_Y + 1.62;
    /** A body can't be nearer a pane's plane than this (half the 0.6 hitbox plus half the 2 px pane). */
    private static final double BODY_TO_PLANE = 0.3 + 1.0 / 16.0;

    private final Level228Plan.Image[] out = images(64);
    private final Ray ray = new Ray();

    private static Level228Plan.Image[] images(int n) {
        Level228Plan.Image[] a = new Level228Plan.Image[n];
        for (int i = 0; i < n; i++) {
            a[i] = new Level228Plan.Image();
        }
        return a;
    }

    @Test
    void theReviewSeedIsTheReviewWorld() {
        assertEquals(1, Level228Plan.correctExitMirror(REVIEW_SEED));
        assertEquals(Level228Plan.FLAW_NOD, Level228Plan.exitFlaw(REVIEW_SEED, 0));
        assertEquals(Level228Plan.FLAW_CANDLE_UNLIT, Level228Plan.exitFlaw(REVIEW_SEED, 2));
    }

    /**
     * Review bug 1: every chamber mirror must show a player standing in front of it (and, for its
     * flaw, nothing but its flaw). Standing in front of a pier between two mirrors (e.g. z = 46.5,
     * the pier between B and C) shows nothing in either: that is the optics, not a bug.
     */
    @Test
    void eachChamberMirrorReflectsAPlayerInFrontOfIt() {
        ShiftState s = ShiftState.INITIAL;
        for (long seed : SEEDS) {
            Level228Maze m = Level228Plan.maze(seed);
            for (int mi = 0; mi < 3; mi++) {
                int z0 = Level228Maze.CHAMBER_MIRROR_Z + 3 * mi;
                for (double pz = z0 + 0.35; pz <= z0 + 1.65; pz += 0.1) {
                    for (double px : new double[] {9.5, 12.5, 14.5}) {
                        // You, looking at yourself (first person: the camera is at your eyes).
                        int n = Level228Plan.imagesOf(seed, s, px, EYE, pz, px, Level228Plan.FEET_Y, pz, 28, out);
                        assertTrue(hasImageThrough(seed, s, n, Level228Plan.K_EXIT_A + mi),
                                "seed " + seed + ": mirror " + (char) ('A' + mi) + " shows no reflection of you at " + px + "," + pz);
                        // A teammate in front of it, seen from the middle of the chamber: through this
                        // mirror whenever the sightline to the image meets the glass within its span.
                        n = Level228Plan.imagesOf(seed, s, 8.5, EYE, 44.5, px, Level228Plan.FEET_Y, pz, 28, out);
                        double ix = 2 * (Level228Maze.CHAMBER_LINE + 0.5) - px;
                        // Where the sightline enters the line block (x = 15) and meets the glass (15.5):
                        // both within the mirror's span, or the pier's thickness hides it.
                        double slope = (pz - 44.5) / (ix - 8.5);
                        double zIn = 44.5 + slope * (Level228Maze.CHAMBER_LINE - 8.5);
                        double zc = 44.5 + slope * (Level228Maze.CHAMBER_LINE + 0.5 - 8.5);
                        if (Math.min(zIn, zc) > z0 + 0.05 && Math.max(zIn, zc) < z0 + 1.95) {
                            assertTrue(hasImageThrough(seed, s, n, Level228Plan.K_EXIT_A + mi),
                                    "seed " + seed + ": mirror " + (char) ('A' + mi) + " shows no teammate at " + px + "," + pz);
                        }
                        assertAllPhysical(m, seed, s, 8.5, 44.5, px, pz, n);
                    }
                }
            }
            // The review position: in front of the pier between B and C, the image is behind the pier.
            int n = Level228Plan.imagesOf(seed, s, 12.5, EYE, 46.5, 12.5, Level228Plan.FEET_Y, 46.5, 28, out);
            assertEquals(0, n, "seed " + seed + ": an image through the pier");
            // Edging towards C, your shoulder passes the pier first: the renderer's sideways probe
            // (centre + 0.3) finds the image through C while the centre's is still behind the pier.
            n = Level228Plan.imagesOf(seed, s, 12.5, EYE, 46.95, 12.5, Level228Plan.FEET_Y, 46.95, 28, out);
            assertEquals(0, n, "seed " + seed + ": the centre at 46.95 is behind the pier");
            n = Level228Plan.imagesOf(seed, s, 12.5, EYE, 46.95, 12.5, Level228Plan.FEET_Y, 47.25, 28, out);
            assertTrue(hasImageThrough(seed, s, n, Level228Plan.K_EXIT_C), "seed " + seed + ": the shoulder at 47.25 is not seen in C");
        }
    }

    private boolean hasImageThrough(long seed, ShiftState s, int n, int kind) {
        Level228Plan.Mirror mirror = new Level228Plan.Mirror();
        for (int k = 0; k < n; k++) {
            if (Level228Plan.mirror(seed, s, out[k].lastMirror, mirror) && mirror.kind == kind) {
                return true;
            }
        }
        return false;
    }

    /**
     * Review bug 2: camera (56, 5) looking +z, a teammate beyond the pane on the z = 6 line (plane
     * 6.5). Anywhere a body can stand behind that opaque mirror he must be hidden, and every image
     * of him must be physically real (none of them is in that pane: he is on its back side).
     */
    @Test
    void nothingBehindAnOpaqueMirrorShowsInIt() {
        ShiftState s = ShiftState.INITIAL;
        Level228Maze m = Level228Plan.maze(REVIEW_SEED);
        assertEquals(Level228Maze.T_MIRROR, m.typeAt(0, 56, 6));
        for (double pz = 6.5 + BODY_TO_PLANE; pz <= 12.5 - BODY_TO_PLANE; pz += 0.05) {
            for (double px = 54.8; px <= 56.2; px += 0.1) {
                assertTrue(Level228Plan.behindMirror(REVIEW_SEED, s, 56.0, EYE, 5.0, px, EYE - 0.7, pz),
                        "the teammate at " + px + "," + pz + " is not hidden");
                int n = Level228Plan.imagesOf(REVIEW_SEED, s, 56.0, EYE, 5.0, px, Level228Plan.FEET_Y, pz, 28, out);
                assertAllPhysical(m, REVIEW_SEED, s, 56.0, 5.0, px, pz, n);
            }
        }
        // Teleported into the glass (the review's z = 6.5): the centre is on the plane, so the
        // renderer tests 0.35 beyond it (Level228Reflections.STRADDLE) and hides the body...
        assertTrue(!Level228Plan.behindMirror(REVIEW_SEED, s, 56.0, EYE, 5.0, 56.0, EYE - 0.7, 6.5));
        assertTrue(Level228Plan.behindMirror(REVIEW_SEED, s, 56.0, EYE, 5.0, 56.0, EYE - 0.7, 6.5 + 0.35));
        // ...while someone standing right in front of the glass stays visible after the same push.
        assertTrue(!Level228Plan.behindMirror(REVIEW_SEED, s, 56.0, EYE, 5.0, 56.0, EYE - 0.7, 6.5 - BODY_TO_PLANE + 0.35));
    }

    /** Every image imagesOf reports, for sampled cameras and points across all rooms, is physical. */
    @Test
    void everyImageIsReachedByARealBouncingRay() {
        for (long seed : SEEDS) {
            Level228Maze m = Level228Plan.maze(seed);
            ShiftState s = new ShiftState(Hash.of(seed, 4, 5, 6) & 0xFF, 1);
            int checked = 0;
            for (int sample = 0; sample < 4000; sample++) {
                long h = Hash.of(seed, sample, 13, 0x1A70);
                Room r = m.rooms[Hash.range(h, m.rooms.length)];
                double cu = 1 + Hash.unit(Hash.mix(h + 1)) * (3 * r.w - 2);
                double cv = 1 + Hash.unit(Hash.mix(h + 2)) * (3 * r.h - 2);
                double cx = r.contX(cu, cv);
                double cz = r.contZ(cu, cv);
                double px = cx + (Hash.unit(Hash.mix(h + 3)) - 0.5) * 16;
                double pz = cz + (Hash.unit(Hash.mix(h + 4)) - 0.5) * 16;
                if (!standable(seed, s, m, cx, cz) || !standable(seed, s, m, px, pz)) {
                    continue;
                }
                int n = Level228Plan.imagesOf(seed, s, cx, EYE, cz, px, Level228Plan.FEET_Y, pz, 28, out);
                checked += assertAllPhysical(m, seed, s, cx, cz, px, pz, n);
            }
            assertTrue(checked > 300, "too few images checked for seed " + seed + ": " + checked);
        }
    }

    /** Walkable and at least a body's half-width from any pane plane (where a body can stand). */
    private static boolean standable(long seed, ShiftState s, Level228Maze m, double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!Level228Plan.isWalkable(seed, s, bx, Level228Plan.FEET_Y, bz)) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                byte t = m.typeAt(s.bits, bx + dx, bz + dz);
                if (t == Level228Maze.T_MIRROR || t == Level228Maze.T_GLASS || t == Level228Maze.T_PANEL || t == Level228Maze.T_DISTORT
                        || t == Level228Maze.T_POST) {
                    if (Math.abs(x - (bx + dx + 0.5)) < BODY_TO_PLANE + 0.5 && Math.abs(z - (bz + dz + 0.5)) < BODY_TO_PLANE + 0.5
                            && (Math.abs(x - (bx + dx + 0.5)) < BODY_TO_PLANE || Math.abs(z - (bz + dz + 0.5)) < BODY_TO_PLANE)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Asserts every image is a real bounce path c -> p; returns how many were checked (not exempt). */
    private int assertAllPhysical(Level228Maze m, long seed, ShiftState s, double cx, double cz, double px, double pz, int n) {
        int checked = 0;
        Level228Plan.Mirror mirror = new Level228Plan.Mirror();
        for (int k = 0; k < n; k++) {
            Level228Plan.Image img = out[k];
            Level228Plan.mirror(seed, s, img.lastMirror, mirror);
            if (mirror.kind == Level228Plan.K_DISTORT || !clean(m, cx, cz, img.x, img.z)) {
                continue; // dark-backed funhouse glass, or a deliberate anomaly (phantom room, hidden passage)
            }
            double ix = img.x - cx;
            double iz = img.z - cz;
            double d = Math.sqrt(ix * ix + iz * iz);
            boolean ok = bounce(m, s.bits, cx, cz, ix / d, iz / d, d);
            assertTrue(ok && Math.abs(ray.x - px) < 1e-4 && Math.abs(ray.z - pz) < 1e-4,
                    "seed " + seed + ": ghost image at " + img.x + "," + img.z + " (depth " + img.depth + ") of " + px + "," + pz
                            + " seen from " + cx + "," + cz + ": a real ray " + (ok ? "ends at " + ray.x + "," + ray.z : "is blocked"));
            assertEquals(ray.bounces, img.depth, "bounces");
            checked++;
        }
        return checked;
    }

    /** False if the unfolded segment passes a deliberate anomaly or the end of a sealed image. */
    private static boolean clean(Level228Maze m, double ax, double az, double bx, double bz) {
        int steps = (int) Math.ceil(Math.hypot(bx - ax, bz - az) * 8) + 1;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.floor(ax + (bx - ax) * t);
            int z = (int) Math.floor(az + (bz - az) * t);
            if (!Level228Maze.inGrid(x, z) || (m.flags[Level228Maze.idx(x, z)] & (Level228Maze.C_ANOMALY | Level228Maze.C_EDGE)) != 0) {
                return false;
            }
        }
        return true;
    }

    private static final class Ray {
        double x;
        double z;
        int bounces;
    }

    /**
     * Marches a horizontal ray in small steps through the real maze, reflecting it at every mirror
     * pane it crosses (the plane through the line block's centre) and stopping at anything opaque:
     * an independent model of the optics (no unfolding, no orbit). Leaves the end in {@link #ray}.
     */
    private boolean bounce(Level228Maze m, long bits, double x, double z, double dx, double dz, double length) {
        ray.bounces = 0;
        final double step = 1.0 / 256.0;
        double left = length;
        while (left > 1e-9) {
            double h = Math.min(step, left);
            double nx = x + dx * h;
            double nz = z + dz * h;
            int bx = (int) Math.floor(nx);
            int bz = (int) Math.floor(nz);
            byte t = m.typeAt(bits, bx, bz);
            switch (t) {
                case Level228Maze.T_FLOOR, Level228Maze.T_ARCH, Level228Maze.T_FRAME -> { }
                case Level228Maze.T_POST -> {
                    double ox = nx - bx - 0.5;
                    double oz = nz - bz - 0.5;
                    if (ox * ox + oz * oz < 0.27 * 0.27 || crosses(x, nx, bx + 0.5) || crosses(z, nz, bz + 0.5)) {
                        return false;
                    }
                }
                case Level228Maze.T_MIRROR, Level228Maze.T_GLASS, Level228Maze.T_PANEL, Level228Maze.T_DISTORT -> {
                    int id = m.mirror[Level228Maze.idx(bx, bz)];
                    int axis = id >= 0 ? m.mAxis[id] : Math.floorMod(bx, 3) == 0 ? 0 : 1;
                    double plane = (axis == 0 ? bx : bz) + 0.5;
                    double a = axis == 0 ? x : z;
                    double b = axis == 0 ? nx : nz;
                    if (t != Level228Maze.T_GLASS && crosses(a, b, plane) && Math.abs(b - plane) > 1e-12) {
                        if (t == Level228Maze.T_PANEL || id < 0) {
                            return false;
                        }
                        {
                            // Reflect in the plane: the part of the step beyond it comes back.
                            if (axis == 0) {
                                nx = 2 * plane - nx;
                                dx = -dx;
                            } else {
                                nz = 2 * plane - nz;
                                dz = -dz;
                            }
                            ray.bounces++;
                            if (ray.bounces > Level228Plan.MAX_DEPTH) {
                                return false;
                            }
                        }
                    }
                }
                default -> {
                    return false;
                }
            }
            x = nx;
            z = nz;
            left -= h;
        }
        ray.x = x;
        ray.z = z;
        return true;
    }

    private static boolean crosses(double a, double b, double c) {
        return (a - c) * (b - c) < 0 || b == c;
    }
}
