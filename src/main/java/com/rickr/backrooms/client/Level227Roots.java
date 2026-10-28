package com.rickr.backrooms.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The roots of the upside-down tree: everything above the top of the world (y 768), so only the
 * far renderer ever draws them. Like the wiki drawing, the trunk swells into a flared crown above
 * the world and a brush of long roots fans out of it, outward and ever more steeply upward, thinning
 * and splitting as they climb into the high haze toward the Sun (y 9000, which they never reach).
 *
 * <p>The shape is fixed (no world seed), like the rest of the tree, so every player sees the same
 * roots. Each root is a smooth polyline of points {x, y, z, radius}; {@link Level227FarMesh} turns
 * them into tubes.
 */
final class Level227Roots {
    /** How many great roots leave the crown. */
    private static final int MAINS = 30;
    /** Where they leave it: the band of the flare (see {@link Level227FarMesh#trunkScale}). */
    private static final double BASE_Y_MIN = 1380.0;
    private static final double BASE_Y_SPAN = 260.0;
    /** The roots flatten out near this height so none climbs into the Sun's light. */
    private static final double CEILING_Y = 7200.0;
    /** Roots thinner than this don't split any more (too thin to see from the branches). */
    private static final double SPLIT_RADIUS = 16.0;

    private Level227Roots() {}

    /** Every root, as packed {x, y, z, r} points from base to tip. */
    static List<float[]> build() {
        Random rnd = new Random(0x227_5007L);
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < MAINS; i++) {
            double yaw = i * Math.PI * 2.0 / MAINS + (rnd.nextDouble() - 0.5) * 0.16;
            double y = BASE_Y_MIN + rnd.nextDouble() * BASE_Y_SPAN;
            // Start well inside the flare so the root grows out of the wood.
            double start = Level227FarMesh.trunkBaseRadius() * Level227FarMesh.trunkScale(y, yaw) * 0.8;
            double r0 = 150.0 + rnd.nextDouble() * 90.0;
            double length = 4300.0 + rnd.nextDouble() * 3300.0;
            double rise0 = Math.toRadians(14.0 + rnd.nextDouble() * 22.0);
            double rise1 = Math.toRadians(55.0 + rnd.nextDouble() * 25.0);
            grow(out, rnd, Math.cos(yaw) * start, y, Math.sin(yaw) * start, yaw, rise0, rise1, r0, length, 0);
        }
        return out;
    }

    /**
     * One root from (x, y, z): it leaves at {@code rise0} above the horizontal and bends up to
     * {@code rise1}, wandering a little sideways; it tapers to a hair-thin tip and sprouts
     * smaller roots on the way (two generations).
     */
    private static void grow(List<float[]> out, Random rnd, double x, double y, double z, double yaw,
                             double rise0, double rise1, double r0, double length, int depth) {
        double step = Math.max(60.0, Math.min(180.0, r0 * 1.1));
        int n = Math.max(3, (int) Math.ceil(length / step));
        step = length / n;
        float[] pts = new float[(n + 1) * 4];
        double drift = (rnd.nextDouble() - 0.5) * 0.7;
        double phase = rnd.nextDouble() * Math.PI * 2.0;
        double nextSplit = length * (0.12 + rnd.nextDouble() * 0.1);
        for (int k = 0; k <= n; k++) {
            double f = k / (double) n;
            double r = Math.max(2.0, r0 * Math.pow(1.0 - f, 1.3));
            pts[k * 4] = (float) x;
            pts[k * 4 + 1] = (float) y;
            pts[k * 4 + 2] = (float) z;
            pts[k * 4 + 3] = (float) r;
            double s = f * length;
            double rise = rise0 + (rise1 - rise0) * f * f * (3.0 - 2.0 * f) + 0.08 * Math.sin(f * 9.0 + phase);
            if (y > CEILING_Y - 600.0) {
                // Near the top they spread sideways instead of climbing on.
                rise *= Math.max(0.1, (CEILING_Y - y) / 600.0);
            }
            double heading = yaw + drift * f + 0.12 * Math.sin(f * 7.0 + phase * 1.7);
            if (depth < 2 && s >= nextSplit && r > SPLIT_RADIUS && k < n - 2
                    && (depth == 0 || rnd.nextDouble() < 0.6)) {
                double side = rnd.nextBoolean() ? 1.0 : -1.0;
                double childLength = (length - s) * (0.35 + rnd.nextDouble() * 0.35) + 300.0;
                grow(out, rnd, x, y, z, heading + side * (0.35 + rnd.nextDouble() * 0.45), rise + 0.1,
                        Math.min(1.45, rise1 + 0.15), r * 0.55, childLength, depth + 1);
                nextSplit += length * (0.10 + rnd.nextDouble() * 0.1) * (depth == 0 ? 1.0 : 1.6);
            }
            x += Math.cos(rise) * Math.cos(heading) * step;
            y += Math.sin(rise) * step;
            z += Math.cos(rise) * Math.sin(heading) * step;
        }
        out.add(pts);
    }
}
