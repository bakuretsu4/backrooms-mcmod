package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.world.layout.Level227Tree;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the far picture of the whole tree once, as plain triangles in world coordinates, cut into
 * square cells so the renderer can skip what is behind you and pick a level of detail per cell.
 * It runs on a background thread when you enter Level 227 ({@link Level227Far} uploads the result).
 *
 * <p><b>What it builds</b> (everything from {@link Level227Tree}, the same plan the real blocks
 * are generated from, so the far picture lines up with them):
 * <ul>
 *   <li><b>The trunk</b>, 900 blocks across, with the exact fluted surface of the real trunk
 *       ({@link Level227Tree#trunkRadius}); below the world it tapers away into the void, above
 *       the world it swells into the root crown;</li>
 *   <li><b>every branch piece</b> ({@link Level227Tree#farSkeleton}) as a tube with the real
 *       cross-section: as wide as its radius, flatter than tall, with the domed top the forest
 *       grows on. Pieces that continue each other are stretched a little over the joint so a
 *       bend never opens a gap; free ends get the same rounded tip as the blocks, broken tips a
 *       flat cut face with growth rings;</li>
 *   <li><b>the canopy</b> ({@link Level227Tree#farFoliage}) as lumpy leaf masses reaching down to
 *       the forest floor, and the fern curtains under the limb tips;</li>
 *   <li><b>moss curtains</b> hanging off the undersides of the branches into the void (near cells only);</li>
 *   <li><b>the roots</b> above the world ({@link Level227Roots}).</li>
 * </ul>
 * Each vertex carries its colour, a material code in the colour's alpha (bark, root, leaves,
 * curtain, cut face: the shader paints each its own way) and a smooth normal.
 */
final class Level227FarMesh {
    /** Cell size (blocks). */
    static final int CELL = 1536;
    /** Cell layers: branches and canopy, roots, trunk sectors. */
    static final int TREE = 0;
    static final int ROOTS = 1;
    static final int TRUNK = 2;
    /** Levels of detail. */
    static final int HIGH = 0;
    static final int LOW = 1;
    /** Material codes (vertex colour alpha); megaflora_far.fsh decodes them. */
    static final int MAT_BARK = 255;
    static final int MAT_ROOT = 205;
    static final int MAT_LEAF = 150;
    static final int MAT_CURTAIN = 100;
    static final int MAT_CUT = 50;

    /** How many pieces the trunk is cut into round its circumference (so the back half is skipped). */
    private static final int TRUNK_SECTORS = 8;
    private static final int TRUNK_SIDES_HIGH = 352; // 8 per flute bump (44 bumps)
    private static final int TRUNK_SIDES_LOW = 176;
    private static final double TRUNK_BOTTOM = -2600.0;
    private static final double TRUNK_TOP = 1760.0;
    /** Ring spacing along a branch: never closer than this, or than its radius times this (high / low). */
    private static final double RING_MIN_HIGH = 40.0;
    private static final double RING_MIN_LOW = 150.0;
    private static final double RING_RADII_LOW = 3.0;
    /** Curtains: width of one hanging patch, and the share of patches left bare. */
    private static final double CURTAIN_WIDTH = 26.0;
    private static final double CURTAIN_GAPS = 0.42;
    /** Roots are drawn in pieces of this many points (each in its own cell). */
    private static final int ROOT_CHUNK = 14;

    /** One part (one level of detail) of one cell. */
    static final class Part {
        @Nullable
        ByteBufferBuilder bytes;
        @Nullable
        BufferBuilder builder;
        @Nullable
        MeshData data;
        int vertices;
    }

    /** One cell: both levels of detail and the box around them. */
    static final class Cell {
        final int layer;
        final Part[] parts = {new Part(), new Part()};
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;

        Cell(int layer) {
            this.layer = layer;
        }
    }

    private final Map<Long, Cell> cells = new HashMap<>();
    /** Joints: the pieces starting / ending near a point (rounded to 4 blocks). */
    private final Map<Long, List<Level227Tree.Seg>> starts = new HashMap<>();
    private final Map<Long, List<Level227Tree.Seg>> ends = new HashMap<>();
    private final Level227Tree.Span span = new Level227Tree.Span();
    // Scratch for the "is this end buried in another branch?" test.
    private double testX;
    private double testY;
    private double testZ;
    private double testR;
    private int testSelf;
    private boolean buried;

    private Level227FarMesh() {}

    /** Builds everything (call off the render thread). The caller uploads and frees the parts. */
    static List<Cell> build() {
        Level227FarMesh mesh = new Level227FarMesh();
        mesh.trunk();
        mesh.branches();
        mesh.foliage();
        mesh.roots();
        List<Cell> out = new ArrayList<>();
        for (Cell cell : mesh.cells.values()) {
            for (Part part : cell.parts) {
                if (part.builder != null) {
                    part.data = part.builder.build();
                    part.builder = null;
                }
            }
            out.add(cell);
        }
        return out;
    }

    /** Frees the CPU-side memory of a cell that was not (or no longer needs to be) uploaded. */
    static void free(Cell cell) {
        for (Part part : cell.parts) {
            if (part.data != null) {
                part.data.close();
                part.data = null;
            }
            if (part.bytes != null) {
                part.bytes.close();
                part.bytes = null;
            }
        }
    }

    // ==========================================================================================
    // Trunk
    // ==========================================================================================

    static double trunkBaseRadius() {
        return Level227Tree.TRUNK_RADIUS;
    }

    /**
     * The trunk's radius beyond the world, as a factor of the real fluted surface: exactly 1
     * inside the world (so it meets the real blocks), tapering away below y -400 into a rounded
     * end deep in the void, and above y 800 swelling into the root crown (with five great
     * buttress lobes) that closes in a dome hidden under the roots.
     */
    static double trunkScale(double y, double angle) {
        double s = 1.0;
        if (y < -400.0) {
            s = 1.0 - 0.3 * smooth((-400.0 - y) / 2000.0);
            if (y < -2300.0) {
                double k = (-2300.0 - y) / (-2300.0 - TRUNK_BOTTOM);
                s *= Math.sqrt(Math.max(0.0, 1.0 - k * k));
            }
        } else if (y > 800.0) {
            double f = smooth((y - 800.0) / 800.0);
            s = 1.0 + 0.7 * f + 0.12 * f * Math.pow(Math.abs(Math.sin(angle * 5.0 + 1.3 + y * 0.0004)), 4.0);
            if (y > 1600.0) {
                double k = (y - 1600.0) / (TRUNK_TOP - 1600.0);
                s *= Math.sqrt(Math.max(0.0, 1.0 - k * k));
            }
        }
        return s;
    }

    private void trunk() {
        for (int lod = HIGH; lod <= LOW; lod++) {
            boolean high = lod == HIGH;
            List<Double> ys = new ArrayList<>();
            for (double y = TRUNK_BOTTOM; y < -400.0; y += high ? 110.0 : 275.0) {
                ys.add(y);
            }
            for (double y = -400.0; y < 1500.0; y += high ? 24.0 : 96.0) {
                ys.add(y);
            }
            for (double y = 1500.0; y < TRUNK_TOP; y += high ? 20.0 : 52.0) {
                ys.add(y);
            }
            ys.add(TRUNK_TOP);
            int sides = high ? TRUNK_SIDES_HIGH : TRUNK_SIDES_LOW;
            Grid g = new Grid(ys.size(), sides, true);
            for (int i = 0; i < ys.size(); i++) {
                double y = ys.get(i);
                g.centre(i, 0.0, y, 0.0);
                for (int k = 0; k < sides; k++) {
                    double angle = k * Math.PI * 2.0 / sides;
                    double base = Level227Tree.trunkRadius(angle, Math.min(y, 1368.0));
                    double r = base * trunkScale(y, angle);
                    double x = Math.cos(angle) * r;
                    double z = Math.sin(angle) * r;
                    // Big patches of lighter and darker bark down the trunk ...
                    float tone = 0.85f + 0.3f * (float) Level227Tree.noise(angle * 9.0, y * 0.004, 77);
                    // ... and the flutes: deep, shadowed grooves between pale ridges. From thousands
                    // of blocks away the shape of an 18-block flute is lost, but its shadow stripes show.
                    double ridge = Math.max(0.0, Math.min(1.3, (base - (Level227Tree.TRUNK_RADIUS - 6.0)) / 18.0));
                    tone *= (float) (0.4 + 0.7 * Math.pow(ridge, 0.7));
                    g.set(i, k, x, y, z, rgba(0.17f * tone, 0.12f * tone, 0.09f * tone, MAT_BARK));
                }
            }
            g.computeNormals();
            int per = sides / TRUNK_SECTORS;
            for (int sector = 0; sector < TRUNK_SECTORS; sector++) {
                g.emit(cell(TRUNK, sector, 0), lod, sector * per, (sector + 1) * per);
            }
        }
    }

    // ==========================================================================================
    // Branches
    // ==========================================================================================

    private static final int END_ROUND = 0;
    private static final int END_JOINT = 1;
    private static final int END_NONE = 2;
    private static final int END_CUT = 3;

    private void branches() {
        List<Level227Tree.Seg> skeleton = Level227Tree.farSkeleton();
        for (Level227Tree.Seg s : skeleton) {
            if (s.order() != Level227Tree.TRUNK) {
                starts.computeIfAbsent(pointKey(s.x0(), s.y0(), s.z0()), k -> new ArrayList<>()).add(s);
                ends.computeIfAbsent(pointKey(s.x1(), s.y1(), s.z1()), k -> new ArrayList<>()).add(s);
            }
        }
        for (Level227Tree.Seg s : skeleton) {
            if (s.order() == Level227Tree.TRUNK || Math.hypot(s.x1() - s.x0(), s.z1() - s.z0()) < 1.0) {
                continue;
            }
            Level227Tree.Seg before = neighbour(ends, false, s, s.x0(), s.y0(), s.z0());
            Level227Tree.Seg after = neighbour(starts, true, s, s.x1(), s.y1(), s.z1());
            double ext0 = before == null ? 0.0 : jointExtension(before, s, s.r0());
            double ext1 = after == null ? 0.0 : jointExtension(s, after, s.r1());
            int startEnd = before != null ? END_JOINT : buried(s, s.x0(), s.y0(), s.z0(), s.r0()) ? END_NONE : END_ROUND;
            int endEnd = after != null ? END_JOINT : Level227Tree.brokenTip(s) ? END_CUT
                    : buried(s, s.x1(), s.y1(), s.z1(), s.r1()) ? END_NONE : END_ROUND;
            Cell cell = cell(TREE, Math.floorDiv((int) ((s.x0() + s.x1()) * 0.5), CELL),
                    Math.floorDiv((int) ((s.z0() + s.z1()) * 0.5), CELL));
            float tone = 0.8f + 0.35f * hash01(s.id(), 3);
            int bark = rgba(0.20f * tone, 0.14f * tone, 0.10f * tone, MAT_BARK);
            for (int lod = HIGH; lod <= LOW; lod++) {
                tube(cell, lod, s, ext0, ext1, endEnd == END_CUT, bark);
                if (startEnd == END_ROUND) {
                    cap(cell, lod, s, false, bark);
                }
                if (endEnd == END_ROUND) {
                    cap(cell, lod, s, true, bark);
                }
                if (endEnd == END_CUT) {
                    cutFace(cell, lod, s);
                }
            }
            if (s.order() != Level227Tree.TRUNK && Math.min(s.r0(), s.r1()) >= 14.0) {
                curtains(cell, s);
            }
        }
    }

    /** The piece (other than s) that starts / ends within a few blocks of (x, y, z), widest first. */
    @Nullable
    private static Level227Tree.Seg neighbour(Map<Long, List<Level227Tree.Seg>> map, boolean isStart, Level227Tree.Seg s,
                                              double x, double y, double z) {
        Level227Tree.Seg best = null;
        long kx = Math.round(x / 4.0);
        long ky = Math.round(y / 4.0);
        long kz = Math.round(z / 4.0);
        for (long i = -1; i <= 1; i++) {
            for (long j = -1; j <= 1; j++) {
                for (long k = -1; k <= 1; k++) {
                    List<Level227Tree.Seg> list = map.get(key(kx + i, ky + j, kz + k));
                    if (list == null) {
                        continue;
                    }
                    for (Level227Tree.Seg o : list) {
                        double ox = isStart ? o.x0() : o.x1();
                        double oy = isStart ? o.y0() : o.y1();
                        double oz = isStart ? o.z0() : o.z1();
                        double d = Math.abs(ox - x) + Math.abs(oy - y) + Math.abs(oz - z);
                        if (o.id() != s.id() && d < 4.0 && (best == null || Math.max(o.r0(), o.r1()) > Math.max(best.r0(), best.r1()))) {
                            best = o;
                        }
                    }
                }
            }
        }
        return best;
    }

    /**
     * How far to stretch two pieces over their joint so the outside of the bend stays closed:
     * half the bend angle's tangent times the radius, plus a little.
     */
    private static double jointExtension(Level227Tree.Seg a, Level227Tree.Seg b, double r) {
        double ax = a.x1() - a.x0();
        double ay = a.y1() - a.y0();
        double az = a.z1() - a.z0();
        double bx = b.x1() - b.x0();
        double by = b.y1() - b.y0();
        double bz = b.z1() - b.z0();
        double cos = (ax * bx + ay * by + az * bz) / Math.max(1e-6, a.length() * b.length());
        double bend = Math.acos(Math.max(-1.0, Math.min(1.0, cos)));
        return r * Math.tan(Math.min(bend, 1.2) * 0.5) + 2.0;
    }

    /** True when (x, y, z) with this radius lies deep inside the trunk or another branch (no tip to draw). */
    private boolean buried(Level227Tree.Seg self, double x, double y, double z, double r) {
        if (Math.hypot(x, z) < Level227Tree.TRUNK_RADIUS - 30.0 && y > -400.0 && y < 1368.0) {
            return true;
        }
        testX = x;
        testY = y;
        testZ = z;
        testR = r;
        testSelf = self.id();
        buried = false;
        Level227Tree.forEachSegmentNear(x, z, 0.5, this::testBuried);
        return buried;
    }

    private void testBuried(Level227Tree.Seg o) {
        if (buried || o.id() == testSelf || o.order() == Level227Tree.TRUNK || !Level227Tree.span(o.id(), testX, testZ, span)) {
            return;
        }
        double margin = testR * 0.45;
        buried = span.top - testY > margin && testY - span.bot > margin && span.a * (1.0 - span.nd) > margin;
    }

    /** Cross-section of piece s at t (0..1 along it, may run past for joint stretches). Fills SECTION. */
    private final double[] section = new double[8];

    private void section(Level227Tree.Seg s, double t) {
        double dx = s.x1() - s.x0();
        double dz = s.z1() - s.z0();
        double h = Math.sqrt(dx * dx + dz * dz);
        double ux = dx / h;
        double uz = dz / h;
        double tc = Math.max(0.0, Math.min(1.0, t));
        double px = s.x0() + dx * t;
        double pz = s.z0() + dz * t;
        double r = s.r0() + (s.r1() - s.r0()) * tc;
        // The same gentle swelling and pinching as the real bark (Level227Tree.span).
        double a = r * (1.0 + 0.05 * Level227Tree.noise(px * 0.013, pz * 0.013, 17 + s.id() % 5));
        double b = r * Level227Tree.VERTICAL_SQUASH * (s.length() / h);
        section[0] = px;
        section[1] = s.y0() + (s.y1() - s.y0()) * t;
        section[2] = pz;
        section[3] = a;
        section[4] = b;
        section[5] = -uz; // sideways (horizontal, across the piece)
        section[6] = ux;
        section[7] = r;
    }

    /** Height of the cross-section above its centre at sideways fraction c (-1..1), top or bottom. */
    private static double profileY(double c, double sin, double b) {
        double ac = Math.abs(c);
        return sin >= 0.0 ? b * Math.sqrt(Math.max(0.0, 1.0 - ac * ac * ac)) : b * sin;
    }

    private void tube(Cell cell, int lod, Level227Tree.Seg s, double ext0, double ext1, boolean cut, int colour) {
        double h = Math.hypot(s.x1() - s.x0(), s.z1() - s.z0());
        double r = Math.max(s.r0(), s.r1());
        double t0 = -ext0 / h;
        double t1 = cut ? 1.0 - Math.min(3.0, h * 0.2) / h : 1.0 + ext1 / h;
        double spacing = lod == HIGH ? Math.max(RING_MIN_HIGH, r) : Math.max(RING_MIN_LOW, r * RING_RADII_LOW);
        int rings = Math.max(2, (int) Math.ceil((t1 - t0) * h / spacing) + 1);
        int sides = sides(r, lod);
        Grid g = new Grid(rings, sides, true);
        for (int i = 0; i < rings; i++) {
            section(s, t0 + (t1 - t0) * i / (rings - 1));
            g.centre(i, section[0], section[1], section[2]);
            for (int k = 0; k < sides; k++) {
                double theta = k * Math.PI * 2.0 / sides;
                double c = Math.cos(theta);
                double side = section[3] * c;
                double y = section[1] + profileY(c, Math.sin(theta), section[4]);
                g.set(i, k, section[0] + section[5] * side, y, section[2] + section[6] * side, colour);
            }
        }
        g.computeNormals();
        g.emit(cell, lod, 0, sides);
    }

    private static int sides(double r, int lod) {
        return lod == HIGH ? clamp((int) Math.round(r * 0.28), 10, 32) : clamp((int) Math.round(r * 0.1), 6, 12);
    }

    /**
     * The rounded end of a piece, exactly as the blocks round it: the cross-section swept half a
     * turn round a vertical axis through the end point.
     */
    private void cap(Cell cell, int lod, Level227Tree.Seg s, boolean atEnd, int colour) {
        double r = atEnd ? s.r1() : s.r0();
        int n = Math.max(3, sides(r, lod) / 2);
        section(s, atEnd ? 1.0 : 0.0);
        double cx = section[0];
        double cy = section[1];
        double cz = section[2];
        double a = section[3];
        double b = section[4];
        double sx = section[5];
        double sz = section[6];
        double fx = sz; // forward = along the piece (side rotated back)
        double fz = -sx;
        Grid g = new Grid(n + 1, n + 1, false);
        for (int i = 0; i <= n; i++) {
            double theta = -Math.PI / 2.0 + Math.PI * i / n;
            double c = Math.cos(theta);
            double y = cy + profileY(c, Math.sin(theta), b);
            g.centre(i, cx, cy, cz);
            for (int j = 0; j <= n; j++) {
                double phi = (atEnd ? -Math.PI / 2.0 : Math.PI / 2.0) + Math.PI * j / n;
                double dx = fx * Math.cos(phi) + sx * Math.sin(phi);
                double dz = fz * Math.cos(phi) + sz * Math.sin(phi);
                g.set(i, j, cx + dx * a * c, y, cz + dz * a * c, colour);
            }
        }
        g.computeNormals();
        g.emit(cell, lod, 0, n);
    }

    /** A broken tip's flat cut face: the colour's red carries the distance from the pith for the rings. */
    private void cutFace(Cell cell, int lod, Level227Tree.Seg s) {
        double h = Math.hypot(s.x1() - s.x0(), s.z1() - s.z0());
        section(s, 1.0 - Math.min(3.0, h * 0.2) / h);
        int sides = sides(Math.max(s.r0(), s.r1()), lod);
        float nx = (float) section[6];
        float nz = (float) -section[5];
        int pith = rgba(0.0f, hash01(s.id(), 9), 0.0f, MAT_CUT);
        int rim = rgba(1.0f, hash01(s.id(), 9), 0.0f, MAT_CUT);
        for (int k = 0; k < sides; k++) {
            double ta = k * Math.PI * 2.0 / sides;
            double tb = (k + 1) * Math.PI * 2.0 / sides;
            double ca = Math.cos(ta);
            double cb = Math.cos(tb);
            float ax = (float) (section[0] + section[5] * section[3] * ca);
            float ay = (float) (section[1] + profileY(ca, Math.sin(ta), section[4]));
            float az = (float) (section[2] + section[6] * section[3] * ca);
            float bx = (float) (section[0] + section[5] * section[3] * cb);
            float by = (float) (section[1] + profileY(cb, Math.sin(tb), section[4]));
            float bz = (float) (section[2] + section[6] * section[3] * cb);
            float px = (float) section[0];
            float py = (float) section[1];
            float pz = (float) section[2];
            vertex(cell, lod, px, py, pz, pith, nx, 0.0f, nz);
            vertex(cell, lod, ax, ay, az, rim, nx, 0.0f, nz);
            vertex(cell, lod, bx, by, bz, rim, nx, 0.0f, nz);
            vertex(cell, lod, px, py, pz, pith, nx, 0.0f, nz);
        }
    }

    /**
     * Moss curtains hanging off both lower flanks of a branch into the void, in patches. Only the
     * near level of detail has them (they are a few blocks wide; from afar they vanish anyway).
     * The colour's red runs 0 at the top to 1 at the bottom (the shader cuts strands of varied
     * length out of the sheet with it), the green is a tone.
     */
    private void curtains(Cell cell, Level227Tree.Seg s) {
        double h = Math.hypot(s.x1() - s.x0(), s.z1() - s.z0());
        int pieces = (int) Math.floor(h / CURTAIN_WIDTH);
        for (int q = 0; q < pieces; q++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                if (hash01(s.id() * 977 + q * 2 + (sign > 0 ? 1 : 0), 5) < CURTAIN_GAPS) {
                    continue;
                }
                float tone = hash01(s.id() * 31 + q, 6 + sign);
                double length = (10.0 + 0.3 * Math.min(s.r0(), s.r1())) * (0.6 + 0.8 * hash01(s.id() * 13 + q, 8 + sign));
                section(s, q * CURTAIN_WIDTH / h);
                float x0 = (float) (section[0] + sign * section[5] * section[3] * 0.8);
                float y0 = (float) (section[1] - section[4] * 0.6);
                float z0 = (float) (section[2] + sign * section[6] * section[3] * 0.8);
                float nx = (float) (sign * section[5]);
                float nz = (float) (sign * section[6]);
                section(s, (q + 1) * CURTAIN_WIDTH / h);
                float x1 = (float) (section[0] + sign * section[5] * section[3] * 0.8);
                float y1 = (float) (section[1] - section[4] * 0.6);
                float z1 = (float) (section[2] + sign * section[6] * section[3] * 0.8);
                int top = rgba(0.0f, tone, 0.0f, MAT_CURTAIN);
                int bottom = rgba(1.0f, tone, 0.0f, MAT_CURTAIN);
                vertex(cell, HIGH, x0, y0, z0, top, nx, 0.0f, nz);
                vertex(cell, HIGH, x1, y1, z1, top, nx, 0.0f, nz);
                vertex(cell, HIGH, x1, (float) (y1 - length), z1, bottom, nx, 0.0f, nz);
                vertex(cell, HIGH, x0, (float) (y0 - length), z0, bottom, nx, 0.0f, nz);
            }
        }
    }

    // ==========================================================================================
    // Foliage
    // ==========================================================================================

    /** Leaf colours: deep green, mid green, the drawing's teal, a lime highlight. */
    private static final float[][] LEAF = {
            {0.08f, 0.20f, 0.05f}, {0.12f, 0.27f, 0.07f}, {0.06f, 0.19f, 0.11f}, {0.17f, 0.32f, 0.08f}};

    private void foliage() {
        List<Level227Tree.Blob> blobs = Level227Tree.farFoliage();
        for (int index = 0; index < blobs.size(); index++) {
            Level227Tree.Blob blob = blobs.get(index);
            boolean fern = blob.ry() > blob.rx();
            double up = fern ? blob.ry() * 0.8 : blob.ry();
            // Widen the canopy masses a little, and stretch each one toward its neighbour along
            // the branch (the plan lists them in order along each piece, spaced a little apart),
            // so they merge into one roof of leaves: from afar separate balls look like a toy.
            double wx = fern ? blob.rx() : blob.rx() * 1.15 + 4.0;
            double wz = fern ? blob.rz() : blob.rz() * 1.15 + 4.0;
            double ax = 1.0;
            double az = 0.0;
            double along = wx;
            if (!fern) {
                double best = Double.MAX_VALUE;
                for (int o = index - 1; o <= index + 1; o += 2) {
                    if (o < 0 || o >= blobs.size() || blobs.get(o).ry() > blobs.get(o).rx()) {
                        continue;
                    }
                    double dx = blobs.get(o).x() - blob.x();
                    double dz = blobs.get(o).z() - blob.z();
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > 1.0 && d < 2.6 * blob.rx() && d < best) {
                        best = d;
                        ax = dx / d;
                        az = dz / d;
                    }
                }
                if (best < Double.MAX_VALUE) {
                    along = Math.max(wx, 0.55 * best + 0.6 * wx);
                }
            }
            // Canopy masses reach down to the forest floor (the trunks and undergrowth under the
            // canopy read as one dark mass from afar); fern curtains hang a little longer.
            double gap = 22.0 + (blob.rx() - 10.0) / 0.95 * 0.15;
            double down = fern ? blob.ry() * 1.15 : Math.max(blob.ry(), gap + 3.0);
            float[] leaf = LEAF[(int) (hash01(index, 11) * LEAF.length) % LEAF.length];
            float tone = 0.85f + 0.3f * hash01(index, 12);
            int colour = rgba(leaf[0] * tone, leaf[1] * tone, leaf[2] * tone, MAT_LEAF);
            Cell cell = cell(TREE, Math.floorDiv((int) blob.x(), CELL), Math.floorDiv((int) blob.z(), CELL));
            for (int lod = HIGH; lod <= LOW; lod++) {
                int lon = lod == HIGH ? clamp((int) Math.round(along / 6.0), 8, 14) : clamp((int) Math.round(along / 16.0), 5, 8);
                int lat = lod == HIGH ? Math.max(4, lon / 2) : 3;
                Grid g = new Grid(lat + 1, lon, true);
                for (int i = 0; i <= lat; i++) {
                    double phi = -Math.PI / 2.0 + Math.PI * i / lat;
                    g.centre(i, blob.x(), blob.y(), blob.z());
                    for (int j = 0; j < lon; j++) {
                        double lambda = j * Math.PI * 2.0 / lon;
                        double dx = Math.cos(phi) * Math.cos(lambda);
                        double dy = Math.sin(phi);
                        double dz = Math.cos(phi) * Math.sin(lambda);
                        // Lumps: big soft swellings (both levels), finer clumps up close.
                        double lump = 1.0 + 0.2 * noise3(dx * 1.9, dy * 1.9, dz * 1.9, index);
                        if (lod == HIGH) {
                            lump += 0.08 * noise3(dx * 4.3, dy * 4.3, dz * 4.3, index + 7);
                        }
                        // Local axes: along the branch (ax, az) and across it.
                        double la = dx * along * lump;
                        double ls = dz * wz * lump;
                        g.set(i, j, blob.x() + ax * la - az * ls, blob.y() + dy * (dy >= 0.0 ? up : down) * lump,
                                blob.z() + az * la + ax * ls, colour);
                    }
                }
                g.computeNormals();
                g.emit(cell, lod, 0, lon);
            }
        }
    }

    // ==========================================================================================
    // Roots
    // ==========================================================================================

    private void roots() {
        int index = 0;
        for (float[] pts : Level227Roots.build()) {
            index++;
            int n = pts.length / 4;
            float r0 = pts[3];
            // Frames carried smoothly along the root (no twisting), one per point.
            double[] nrm = new double[n * 3];
            double[] bin = new double[n * 3];
            double px = 0.0;
            double py = 1.0;
            double pz = 0.0;
            for (int i = 0; i < n; i++) {
                int a = Math.max(0, i - 1);
                int b = Math.min(n - 1, i + 1);
                double tx = pts[b * 4] - pts[a * 4];
                double ty = pts[b * 4 + 1] - pts[a * 4 + 1];
                double tz = pts[b * 4 + 2] - pts[a * 4 + 2];
                double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
                tx /= tl;
                ty /= tl;
                tz /= tl;
                if (i == 0) {
                    // Any direction across the root to start with.
                    px = Math.abs(ty) < 0.9 ? 0.0 : 1.0;
                    py = Math.abs(ty) < 0.9 ? 1.0 : 0.0;
                    pz = 0.0;
                }
                double d = px * tx + py * ty + pz * tz;
                px -= tx * d;
                py -= ty * d;
                pz -= tz * d;
                double pl = Math.sqrt(px * px + py * py + pz * pz);
                px /= pl;
                py /= pl;
                pz /= pl;
                nrm[i * 3] = px;
                nrm[i * 3 + 1] = py;
                nrm[i * 3 + 2] = pz;
                bin[i * 3] = ty * pz - tz * py;
                bin[i * 3 + 1] = tz * px - tx * pz;
                bin[i * 3 + 2] = tx * py - ty * px;
            }
            float tone = 0.85f + 0.3f * hash01(index, 21);
            for (int lod = HIGH; lod <= LOW; lod++) {
                int sides = lod == HIGH ? clamp(Math.round(r0 * 0.09f), 5, 18) : clamp(Math.round(r0 * 0.035f), 4, 8);
                int stride = lod == HIGH ? 1 : 2;
                for (int c0 = 0; c0 < n - 1; c0 += ROOT_CHUNK) {
                    int c1 = Math.min(n - 1, c0 + ROOT_CHUNK);
                    List<Integer> rows = new ArrayList<>();
                    for (int i = c0; i < c1; i += stride) {
                        rows.add(i);
                    }
                    rows.add(c1);
                    int mid = (c0 + c1) / 2;
                    Cell cell = cell(ROOTS, Math.floorDiv((int) pts[mid * 4], CELL), Math.floorDiv((int) pts[mid * 4 + 2], CELL));
                    Grid g = new Grid(rows.size(), sides, true);
                    for (int ri = 0; ri < rows.size(); ri++) {
                        int i = rows.get(ri);
                        double cx = pts[i * 4];
                        double cy = pts[i * 4 + 1];
                        double cz = pts[i * 4 + 2];
                        double r = pts[i * 4 + 3];
                        // Pale and grey toward the thin tips, dark wet wood at the base.
                        float pale = (float) Math.max(0.0, Math.min(1.0, 1.0 - r / Math.max(r0, 1.0f)));
                        int colour = rgba((0.30f + 0.12f * pale) * tone, (0.26f + 0.14f * pale) * tone,
                                (0.22f + 0.14f * pale) * tone, MAT_ROOT);
                        g.centre(ri, cx, cy, cz);
                        for (int k = 0; k < sides; k++) {
                            double th = k * Math.PI * 2.0 / sides;
                            double c = Math.cos(th) * r;
                            double s = Math.sin(th) * r;
                            g.set(ri, k, cx + nrm[i * 3] * c + bin[i * 3] * s, cy + nrm[i * 3 + 1] * c + bin[i * 3 + 1] * s,
                                    cz + nrm[i * 3 + 2] * c + bin[i * 3 + 2] * s, colour);
                        }
                    }
                    g.computeNormals();
                    g.emit(cell, lod, 0, sides);
                }
            }
        }
    }

    // ==========================================================================================
    // Plumbing
    // ==========================================================================================

    private Cell cell(int layer, int cx, int cz) {
        long key = ((long) layer << 40) | ((long) (cx + 512) << 20) | (cz + 512);
        return cells.computeIfAbsent(key, k -> new Cell(layer));
    }

    private static long pointKey(double x, double y, double z) {
        return key(Math.round(x / 4.0), Math.round(y / 4.0), Math.round(z / 4.0));
    }

    private static long key(long x, long y, long z) {
        return ((x + 0x100000L) << 42) | ((y + 0x100000L) << 21) | (z + 0x100000L);
    }

    private static void vertex(Cell cell, int lod, float x, float y, float z, int rgba, float nx, float ny, float nz) {
        Part part = cell.parts[lod];
        if (part.builder == null) {
            part.bytes = new ByteBufferBuilder(64 * 1024);
            part.builder = new BufferBuilder(part.bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        }
        part.builder.addVertex(x, y, z).setColor(rgba >>> 24, (rgba >>> 16) & 255, (rgba >>> 8) & 255, rgba & 255)
                .setNormal(nx, ny, nz);
        part.vertices++;
        cell.minX = Math.min(cell.minX, x);
        cell.minY = Math.min(cell.minY, y);
        cell.minZ = Math.min(cell.minZ, z);
        cell.maxX = Math.max(cell.maxX, x);
        cell.maxY = Math.max(cell.maxY, y);
        cell.maxZ = Math.max(cell.maxZ, z);
    }

    private static int rgba(float r, float g, float b, int material) {
        return (channel(r) << 24) | (channel(g) << 16) | (channel(b) << 8) | material;
    }

    private static int channel(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255.0f)));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double smooth(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    private static long mix(long v) {
        long h = v * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 29);
    }

    /** A fixed pseudo-random number in 0..1 for (value, salt). */
    private static float hash01(long value, int salt) {
        return ((mix(value * 0x632BE59BD9B4E019L + salt) >>> 40) & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    /** Smooth 3D value noise, about -1..1. */
    private static double noise3(double x, double y, double z, int salt) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iz = (int) Math.floor(z);
        double fx = smooth(x - ix);
        double fy = smooth(y - iy);
        double fz = smooth(z - iz);
        double v = 0.0;
        for (int c = 0; c < 8; c++) {
            int dx = c & 1;
            int dy = (c >> 1) & 1;
            int dz = (c >> 2) & 1;
            double w = (dx == 1 ? fx : 1.0 - fx) * (dy == 1 ? fy : 1.0 - fy) * (dz == 1 ? fz : 1.0 - fz);
            long h = mix(((long) (ix + dx) * 73856093L) ^ ((long) (iy + dy) * 19349663L) ^ ((long) (iz + dz) * 83492791L) ^ salt * 0x5851F42DL);
            v += w * (((h >>> 40) & 0xFFFF) / 32767.5 - 1.0);
        }
        return v;
    }

    /**
     * A grid of surface points (rows x cols, the columns closing round when {@code wrap}) drawn
     * as quads with smooth normals. Each row has an inside point (the axis), so normals can be
     * turned to face outward whatever order the points come in.
     */
    private static final class Grid {
        final int rows;
        final int cols;
        final boolean wrap;
        final float[] p;
        final float[] n;
        final int[] colour;
        final float[] inside;

        Grid(int rows, int cols, boolean wrap) {
            this.rows = rows;
            this.cols = cols;
            this.wrap = wrap;
            p = new float[rows * cols * 3];
            n = new float[rows * cols * 3];
            colour = new int[rows * cols];
            inside = new float[rows * 3];
        }

        void set(int i, int j, double x, double y, double z, int rgba) {
            int o = (i * cols + j) * 3;
            p[o] = (float) x;
            p[o + 1] = (float) y;
            p[o + 2] = (float) z;
            colour[i * cols + j] = rgba;
        }

        void centre(int i, double x, double y, double z) {
            inside[i * 3] = (float) x;
            inside[i * 3 + 1] = (float) y;
            inside[i * 3 + 2] = (float) z;
        }

        void computeNormals() {
            for (int i = 0; i < rows; i++) {
                int im = Math.max(0, i - 1);
                int ip = Math.min(rows - 1, i + 1);
                for (int j = 0; j < cols; j++) {
                    int jm = wrap ? (j - 1 + cols) % cols : Math.max(0, j - 1);
                    int jp = wrap ? (j + 1) % cols : Math.min(cols - 1, j + 1);
                    int a = (i * cols + jm) * 3;
                    int b = (i * cols + jp) * 3;
                    int c = (im * cols + j) * 3;
                    int d = (ip * cols + j) * 3;
                    float ux = p[b] - p[a];
                    float uy = p[b + 1] - p[a + 1];
                    float uz = p[b + 2] - p[a + 2];
                    float vx = p[d] - p[c];
                    float vy = p[d + 1] - p[c + 1];
                    float vz = p[d + 2] - p[c + 2];
                    float nx = uy * vz - uz * vy;
                    float ny = uz * vx - ux * vz;
                    float nz = ux * vy - uy * vx;
                    int o = (i * cols + j) * 3;
                    float ox = p[o] - inside[i * 3];
                    float oy = p[o + 1] - inside[i * 3 + 1];
                    float oz = p[o + 2] - inside[i * 3 + 2];
                    float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (len < 1e-4f) {
                        // Poles and pinched tips: point straight away from the inside.
                        nx = ox;
                        ny = oy;
                        nz = oz;
                        len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                        if (len < 1e-6f) {
                            nx = 0.0f;
                            ny = 1.0f;
                            nz = 0.0f;
                            len = 1.0f;
                        }
                    }
                    if (nx * ox + ny * oy + nz * oz < 0.0f) {
                        len = -len;
                    }
                    n[o] = nx / len;
                    n[o + 1] = ny / len;
                    n[o + 2] = nz / len;
                }
            }
        }

        /** The quads between columns j0 and j1 (j1 may equal cols when wrapping). */
        void emit(Cell cell, int lod, int j0, int j1) {
            for (int i = 0; i < rows - 1; i++) {
                for (int j = j0; j < j1; j++) {
                    int jn = wrap ? (j + 1) % cols : j + 1;
                    if (jn >= cols) {
                        break;
                    }
                    put(cell, lod, i, j);
                    put(cell, lod, i + 1, j);
                    put(cell, lod, i + 1, jn);
                    put(cell, lod, i, jn);
                }
            }
        }

        private void put(Cell cell, int lod, int i, int j) {
            int o = (i * cols + j) * 3;
            vertex(cell, lod, p[o], p[o + 1], p[o + 2], colour[i * cols + j], n[o], n[o + 1], n[o + 2]);
        }
    }
}
