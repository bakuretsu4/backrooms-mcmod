package com.rickr.backrooms.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.world.Hash;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Level 490's clouds with depth (they used to be flat sheets): the cloud sea's top is a drifting
 * height field, rolling mounds and billows with long canyons cut into it down to the darker layer
 * underneath, and here and there a cloud tower rising out of it far off; the storm overhead hangs
 * down in lumps (like mammatus clouds) that lightning lights from inside. Both are shaded by their
 * own slopes (moonlit faces, dark hollows), so they read as shapes rather than paint. Cloud towers
 * far off are stacks of soft puffs (a height field can't draw anything that steep). On the
 * horizon stand giant anvil-headed storm clouds flickering with lightning inside, and pale shafts of
 * moonlight slant down through gaps in the storm.
 *
 * <p>Heights live in "cloud space" (the world minus the wind's drift), so each point is worked out
 * once and kept; the mesh is fine near the camera and coarse far off, the fine part's border
 * following the coarse cells exactly so no seams open. No depth buffer up here, so the cells are
 * drawn farthest first.
 */
final class Level490Clouds {
    /** The cloud sea's top and the storm's underside (Level490Sky's layers 3 and 1). */
    static final float SEA_Y = 148.0f;
    static final float STORM_Y = 640.0f;
    private static final float REACH = 2400.0f;
    /** Fine cells near the camera, coarse farther out (three fine to a coarse). */
    private static final int FINE = 40;
    private static final int COARSE = FINE * 3;
    /** Half the fine square, in coarse cells. */
    private static final int INNER = 7;
    private static final int OUTER = (int) Math.ceil(REACH / COARSE);
    /** The storm's lumps show within this far of the camera (the coarse far cells are too big for them). */
    private static final double LUMPS_FULL = 500.0;
    private static final double LUMPS_GONE = 950.0;
    /** Cloud towers show from this far off (you could stand in one otherwise). */
    private static final double TOWER_NEAR = 450.0;
    private static final double TOWER_FULL = 850.0;
    private static final ResourceLocation PUFF = com.rickr.backrooms.Backrooms.id("textures/environment/l490_puff.png");
    private static final Vector3f MOON = new Vector3f((float) (Math.cos(Math.toRadians(24)) * Math.cos(Math.toRadians(35))),
            (float) Math.sin(Math.toRadians(24)), (float) (Math.cos(Math.toRadians(24)) * Math.sin(Math.toRadians(35))));
    private static final ResourceLocation CLOUDS = com.rickr.backrooms.Backrooms.id("textures/environment/l39_clouds.png");

    /** A cloud-space point's heights: {base, detail} (the storm's lumps, which fade out far off). */
    private static final Long2ObjectOpenHashMap<float[]> SEA = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<float[]> STORM = new Long2ObjectOpenHashMap<>();

    private Level490Clouds() {}

    // ==========================================================================================
    // Heights
    // ==========================================================================================

    private static double value(double x, double z, int salt) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double tx = x - x0;
        double tz = z - z0;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = Hash.unit(4900L, x0, z0, salt);
        double b = Hash.unit(4900L, x0 + 1, z0, salt);
        double c = Hash.unit(4900L, x0, z0 + 1, salt);
        double d = Hash.unit(4900L, x0 + 1, z0 + 1, salt);
        return a + (b - a) * tx + (c - a) * tz + (a - b - c + d) * tx * tz;
    }

    private static double fbm(double x, double z, int salt) {
        return (value(x, z, salt) - 0.5) + 0.5 * (value(x * 2.1, z * 2.1, salt + 1) - 0.5) + 0.25 * (value(x * 4.3, z * 4.3, salt + 2) - 0.5);
    }

    /** The cloud sea's top at cloud-space (x, z): {mounds and canyons, tower}. */
    private static float[] seaHeight(double x, double z) {
        // Low rolling mounds (the islands are blocks and always draw over the sky, so the clouds can't
        // rise past their rock undersides without showing it).
        double h = 24 * fbm(x / 260, z / 260, 4970) + 8 * fbm(x / 70, z / 70, 4973);
        // Long canyons: where a broad noise crosses its middle the clouds part (drawn see-through, so
        // the darker layer below shows).
        double n = value(x / 1100, z / 1100, 4976) - 0.5;
        double band = Math.abs(n) / 0.055;
        if (band < 1) {
            h -= 30 * (1 - band) * (1 - band);
        }
        return new float[] {(float) h, 0};
    }

    /** How far the storm hangs down at cloud-space (x, z): broad rolls and rounded lumps. */
    private static float[] stormHeight(double x, double z) {
        double h = 30 * fbm(x / 320, z / 320, 4980) + 30;
        int ci = (int) Math.floor(x / 90);
        int cj = (int) Math.floor(z / 90);
        double lumps = 0;
        for (int i = ci - 1; i <= ci + 1; i++) {
            for (int j = cj - 1; j <= cj + 1; j++) {
                long t = Hash.of(4900L, i, j, 4981);
                if (Hash.unit(t) > 0.7) {
                    continue;
                }
                double lx = (i + Hash.unit(Hash.mix(t))) * 90;
                double lz = (j + Hash.unit(Hash.mix(t + 1))) * 90;
                double r = 30 + 18 * Hash.unit(Hash.mix(t + 2));
                double d = Math.hypot(x - lx, z - lz) / r;
                lumps = Math.max(lumps, (20 + 40 * Hash.unit(Hash.mix(t + 3))) * Math.exp(-d * d * 1.6));
            }
        }
        return new float[] {(float) h, (float) lumps};
    }

    private static float[] height(Long2ObjectOpenHashMap<float[]> cache, boolean sea, int i, int j, int step) {
        long key = ((long) (i * step) << 32) ^ ((j * step) & 0xffffffffL);
        float[] h = cache.get(key);
        if (h == null) {
            if (cache.size() > 300_000) {
                cache.clear();
            }
            h = sea ? seaHeight((double) i * step, (double) j * step) : stormHeight((double) i * step, (double) j * step);
            cache.put(key, h);
        }
        return h;
    }

    // ==========================================================================================
    // Drawing a field
    // ==========================================================================================

    /** One quad of a field: its four corners (cloud-space grid, step), and how far its middle is. */
    private record Cell(int i, int j, int step, double dist) {}

    /**
     * Draws the cloud sea's top (sea) or the storm's underside, drifting by (offX, offZ), lit by the
     * moon (moon 0..1) and lightning. {@code base} is the layer's colour, {@code fade} how fast it
     * fades with distance.
     */
    static void drawField(Matrix4f view, Vec3 eye, boolean sea, double offX, double offZ, float[] base, float moon,
                          float scale, float alphaMax, float fade) {
        Long2ObjectOpenHashMap<float[]> cache = sea ? SEA : STORM;
        double cx = eye.x - offX;
        double cz = eye.z - offZ;
        int ci = (int) Math.floor(cx / COARSE);
        int cj = (int) Math.floor(cz / COARSE);
        List<Cell> cells = new ArrayList<>();
        for (int i = ci - OUTER; i < ci + OUTER; i++) {
            for (int j = cj - OUTER; j < cj + OUTER; j++) {
                boolean inner = i >= ci - INNER && i < ci + INNER && j >= cj - INNER && j < cj + INNER;
                if (inner) {
                    for (int a = 0; a < 3; a++) {
                        for (int b = 0; b < 3; b++) {
                            int fi = i * 3 + a;
                            int fj = j * 3 + b;
                            cells.add(new Cell(fi, fj, FINE, Math.hypot((fi + 0.5) * FINE - cx, (fj + 0.5) * FINE - cz)));
                        }
                    }
                } else {
                    double d = Math.hypot((i + 0.5) * COARSE - cx, (j + 0.5) * COARSE - cz);
                    if (d < REACH + COARSE) {
                        cells.add(new Cell(i, j, COARSE, d));
                    }
                }
            }
        }
        cells.sort((p, q) -> Double.compare(q.dist, p.dist));
        int fineLo = (ci - INNER) * 3;
        int fineHi = (ci + INNER) * 3;
        int fjLo = (cj - INNER) * 3;
        int fjHi = (cj + INNER) * 3;
        // Each grid point worked out once: the fine square's and the coarse ring's.
        int fn = fineHi - fineLo + 1;
        float[][] fine = new float[fn * fn][];
        int cn = 2 * OUTER + 1;
        float[][] coarse = new float[cn * cn][];
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, CLOUDS);
        BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (Cell c : cells) {
            for (int[] k : corners) {
                int gi = c.i + k[0];
                int gj = c.j + k[1];
                float[] v;
                if (c.step == FINE) {
                    int idx = (gi - fineLo) * fn + (gj - fjLo);
                    v = fine[idx];
                    if (v == null) {
                        v = new float[6];
                        vertex(cache, sea, gi, gj, FINE, cx, cz, fineLo, fineHi, fjLo, fjHi, v);
                        fine[idx] = v;
                    }
                } else {
                    int idx = (gi - (ci - OUTER)) * cn + (gj - (cj - OUTER));
                    v = coarse[idx];
                    if (v == null) {
                        v = new float[6];
                        vertex(cache, sea, gi, gj, COARSE, cx, cz, fineLo, fineHi, fjLo, fjHi, v);
                        coarse[idx] = v;
                    }
                }
                emit(quads, view, eye, offX, offZ, v, sea, base, moon, scale, alphaMax, fade);
            }
        }
        BufferUploader.drawWithShader(quads.buildOrThrow());
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    /**
     * A grid point's cloud-space position and height, and its normal (out = {x, y, z, nx, ny, nz}).
     * Fine points on the fine square's border take the coarse edge's height there, so no gap opens
     * between the two.
     */
    private static void vertex(Long2ObjectOpenHashMap<float[]> cache, boolean sea, int i, int j, int step, double cx, double cz,
                               int fineLo, int fineHi, int fjLo, int fjHi, float[] out) {
        double x = (double) i * step;
        double z = (double) j * step;
        double camD = Math.hypot(x - cx, z - cz);
        float h;
        float dx;
        float dz;
        boolean border = step == FINE && (i == fineLo || i == fineHi || j == fjLo || j == fjHi)
                && (Math.floorMod(i, 3) != 0 || Math.floorMod(j, 3) != 0);
        if (border) {
            // On a coarse edge: the line between its two coarse corners.
            int i0 = Math.floorDiv(i, 3);
            int j0 = Math.floorDiv(j, 3);
            float t = (Math.floorMod(i, 3) + Math.floorMod(j, 3)) / 3.0f;
            boolean alongI = Math.floorMod(i, 3) != 0;
            float h0 = total(height(cache, sea, i0, j0, COARSE), camD);
            float h1 = total(height(cache, sea, alongI ? i0 + 1 : i0, alongI ? j0 : j0 + 1, COARSE), camD);
            h = h0 + (h1 - h0) * t;
        } else {
            h = total(height(cache, sea, i, j, step), camD);
        }
        float hx = total(height(cache, sea, i + 1, j, step), camD) - total(height(cache, sea, i - 1, j, step), camD);
        float hz = total(height(cache, sea, i, j + 1, step), camD) - total(height(cache, sea, i, j - 1, step), camD);
        dx = hx / (2.0f * step);
        dz = hz / (2.0f * step);
        out[0] = (float) x;
        out[1] = h;
        out[2] = (float) z;
        float len = (float) Math.sqrt(dx * dx + 1 + dz * dz);
        out[3] = -dx / len;
        out[4] = 1 / len;
        out[5] = -dz / len;
    }

    private static float total(float[] h, double camD) {
        if (h[1] == 0) {
            return h[0];
        }
        double t = Math.max(0, Math.min(1, (LUMPS_GONE - camD) / (LUMPS_GONE - LUMPS_FULL)));
        return h[0] + h[1] * (float) (t * t * (3 - 2 * t));
    }

    private static void emit(BufferBuilder quads, Matrix4f view, Vec3 eye, double offX, double offZ, float[] v, boolean sea,
                             float[] base, float moon, float scale, float alphaMax, float fade) {
        double wx = v[0] + offX;
        double wz = v[2] + offZ;
        float rx = (float) (wx - eye.x);
        float rz = (float) (wz - eye.z);
        // The sea's top rises from its layer; the storm hangs down from its own.
        float ry = sea ? (float) (SEA_Y + v[1] - eye.y) : (float) (STORM_Y - v[1] - eye.y);
        float nx = v[3];
        float ny = sea ? v[4] : -v[4];
        float nz = v[5];
        float horiz = (float) Math.sqrt(rx * rx + rz * rz) + 1e-3f;
        float d = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        float glow = Level490Lightning.cloudGlow(wx, wz);
        float light;
        if (sea) {
            // Tops pale, hollows and canyon walls dark; faces toward the moon catch its light.
            float up = ny;
            light = 0.55f + 0.45f * up + 0.004f * Math.max(-40, Math.min(60, v[1]));
            light += 0.55f * moon * Math.max(0.0f, nx * MOON.x + ny * MOON.y + nz * MOON.z) * 0.5f;
        } else {
            // Seen from below: the lumps' undersides darker, their flanks catching the moon; lightning lights them from inside.
            light = 0.62f + 0.38f * (-ny) * 0.5f + 0.25f * moon * Math.max(0.0f, nx * MOON.x + nz * MOON.z);
            light += 0.006f * (v[1] - 30);
        }
        // The moon's sheen on what lies toward it (as before).
        float toMoon = Math.max(0.0f, (rx * MOON.x + ry * MOON.y + rz * MOON.z) / d);
        float sheen = (sea ? 0.26f : 0.3f) * moon * (float) Math.pow(toMoon, sea ? 2.0 : 6.0);
        float bolt = glow * (sea ? 0.6f : 1.1f);
        float r = base[0] * light + sheen * 0.86f + bolt * 0.5f;
        float g = base[1] * light + sheen * 0.90f + bolt * 0.53f;
        float b = base[2] * light + sheen * 1.00f + bolt * 0.62f;
        float alpha = alphaMax * (float) Math.exp(-horiz / fade);
        if (sea && v[1] < -6) {
            alpha *= Math.max(0.15f, 1.0f - (-6 - v[1]) / 22.0f); // a canyon: the layer below shows through
        }
        float u = (float) (v[0] / 700.0);
        float tv = (float) (v[2] / 700.0);
        quads.addVertex(view, rx * scale, ry * scale, rz * scale).setUv(u, tv).setColor(r, g, b, alpha);
    }

    // ==========================================================================================
    // Anvil clouds on the horizon, shafts of moonlight
    // ==========================================================================================

    /**
     * Giant anvil-headed storm clouds far off round the horizon: a dark column of cloud spreading
     * into a wide flat head, each flickering now and then with lightning deep inside.
     */
    static void drawAnvils(Matrix4f view, Vec3 eye, float scale, float[] fog) {
        long seed = Level490ClientState.hasSeed() ? Level490ClientState.seed() : 0L;
        double seconds = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, CLOUDS);
        BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int k = 0; k < 7; k++) {
            long h = Hash.of(seed, k, 0, 4985);
            double az = Math.PI * 2 * (k + 0.2 + 0.6 * Hash.unit(h)) / 7;
            float dist = (float) (7000 + 2000 * Hash.unit(Hash.mix(h)));
            float width = (float) (900 + 700 * Hash.unit(Hash.mix(h + 1)));
            float top = (float) (1500 + 600 * Hash.unit(Hash.mix(h + 2)));
            // Lightning inside it: short bright flickers every several seconds.
            double cycle = 4 + 7 * Hash.unit(Hash.mix(h + 3));
            double phase = (seconds + 13 * k) % cycle;
            float flicker = phase < 0.5 ? (float) (Math.sin(phase * 40) * 0.5 + 0.5) * (float) (1 - phase / 0.5) : 0;
            float cx = (float) (Math.cos(az) * dist);
            float cz = (float) (Math.sin(az) * dist);
            float sx = (float) -Math.sin(az);
            float sz = (float) Math.cos(az);
            float y0 = (float) (SEA_Y - eye.y);
            // Darker than the haze round it (a silhouette), its head a little paler; bright in a flicker.
            float c = fog[0] * 0.55f + 0.5f * flicker;
            float ch = fog[0] * 0.7f + 0.6f * flicker;
            // The column: narrow at the foot, bulging, then the anvil head spreading out flat.
            float[][] profile = {{0.35f, 0}, {0.45f, 0.25f}, {0.40f, 0.55f}, {0.55f, 0.72f}, {1.0f, 0.86f}, {1.05f, 0.93f}, {0.7f, 1.0f}};
            for (int p = 0; p + 1 < profile.length; p++) {
                float w0 = width * profile[p][0];
                float w1 = width * profile[p + 1][0];
                float ya = y0 + top * profile[p][1];
                float yb = y0 + top * profile[p + 1][1];
                float ca = p >= 4 ? ch : c;
                float a = 0.95f;
                quads.addVertex(view, (cx - sx * w0) * scale, ya * scale, (cz - sz * w0) * scale).setUv(k * 0.3f, p * 0.2f).setColor(ca, ca, ca * 1.12f, a);
                quads.addVertex(view, (cx + sx * w0) * scale, ya * scale, (cz + sz * w0) * scale).setUv(k * 0.3f + 1.2f, p * 0.2f).setColor(ca, ca, ca * 1.12f, a);
                quads.addVertex(view, (cx + sx * w1) * scale, yb * scale, (cz + sz * w1) * scale).setUv(k * 0.3f + 1.2f, p * 0.2f + 0.2f).setColor(ca, ca, ca * 1.12f, a);
                quads.addVertex(view, (cx - sx * w1) * scale, yb * scale, (cz - sz * w1) * scale).setUv(k * 0.3f, p * 0.2f + 0.2f).setColor(ca, ca, ca * 1.12f, a);
            }
        }
        BufferUploader.drawWithShader(quads.buildOrThrow());
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    /**
     * Cloud towers rising out of the cloud sea far off (never near you: you could be standing in
     * one): in some 700-block cells a column of soft puffs widening into a billowing head, its moon
     * side paler, lit up by lightning near it.
     */
    static void drawTowers(Matrix4f view, Vec3 eye, double offX, double offZ, float moon, float scale, float[] base) {
        double cx = eye.x - offX;
        double cz = eye.z - offZ;
        int ci = (int) Math.floor(cx / 700);
        int cj = (int) Math.floor(cz / 700);
        int reach = (int) Math.ceil(REACH / 700) + 1;
        List<float[]> puffs = new ArrayList<>();
        for (int i = ci - reach; i <= ci + reach; i++) {
            for (int j = cj - reach; j <= cj + reach; j++) {
                long t = Hash.of(4900L, i, j, 4977);
                if (Hash.unit(t) > 0.3) {
                    continue;
                }
                double tx = (i + 0.2 + 0.6 * Hash.unit(Hash.mix(t))) * 700 + offX;
                double tz = (j + 0.2 + 0.6 * Hash.unit(Hash.mix(t + 1))) * 700 + offZ;
                double d = Math.hypot(tx - eye.x, tz - eye.z);
                double near = Math.max(0, Math.min(1, (d - TOWER_NEAR) / (TOWER_FULL - TOWER_NEAR)));
                float vis = (float) (near * near * (3 - 2 * near) * Math.exp(-d / 1900.0));
                if (vis < 0.02f || d > REACH + 300) {
                    continue;
                }
                float r = (float) (55 + 45 * Hash.unit(Hash.mix(t + 2)));
                float top = (float) (150 + 150 * Hash.unit(Hash.mix(t + 3)));
                float glow = Level490Lightning.cloudGlow(tx, tz);
                int n = 7;
                for (int k = 0; k < n + 3; k++) {
                    float f = Math.min(1.0f, k / (float) (n - 1));
                    long hp = Hash.of(t, k, 0, 4979);
                    // The column, then a head of a few bigger puffs spreading out at the top.
                    float spread = k >= n ? r * 0.8f : r * 0.18f;
                    float px = (float) (tx + (Hash.unit(hp) - 0.5) * 2 * spread);
                    float pz = (float) (tz + (Hash.unit(Hash.mix(hp)) - 0.5) * 2 * spread);
                    float py = SEA_Y + top * (k >= n ? 0.95f : f) + (float) (Hash.unit(Hash.mix(hp + 1)) * 12);
                    float size = r * (k >= n ? 1.0f : 0.75f + 0.45f * f * f);
                    float dist = (float) Math.hypot(px - eye.x, pz - eye.z);
                    puffs.add(new float[] {px, py, pz, size, vis, glow, dist});
                }
            }
        }
        if (puffs.isEmpty()) {
            return;
        }
        puffs.sort((p, q) -> Float.compare(q[6], p[6]));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, PUFF);
        BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Vector3f dir = new Vector3f();
        Vector3f right = new Vector3f();
        Vector3f up = new Vector3f();
        for (float[] p : puffs) {
            float rx = (float) (p[0] - eye.x);
            float ry = (float) (p[1] - eye.y);
            float rz = (float) (p[2] - eye.z);
            dir.set(rx, ry, rz).normalize();
            right.set(-dir.z, 0, dir.x).normalize();
            right.cross(dir, up).normalize();
            float s = p[3];
            // The side toward the moon paler.
            float moonSide = right.x * MOON.x + right.z * MOON.z;
            float lit = 0.75f + 0.12f * moon;
            float a = p[4] * 0.9f;
            float glow = p[5];
            for (int c = 0; c < 4; c++) {
                float sx = (c == 1 || c == 2) ? 1 : -1;
                float sy = c >= 2 ? 1 : -1;
                float shade = lit + 0.18f * moon * moonSide * sx + 0.06f * sy;
                float x = rx + (right.x * sx + up.x * sy) * s;
                float y = ry + (right.y * sx + up.y * sy) * s;
                float z = rz + (right.z * sx + up.z * sy) * s;
                quads.addVertex(view, x * scale, y * scale, z * scale).setUv(sx > 0 ? 1 : 0, sy > 0 ? 0 : 1)
                        .setColor(base[0] * shade + glow * 0.5f, base[1] * shade + glow * 0.53f, base[2] * shade + glow * 0.62f, a);
            }
        }
        BufferUploader.drawWithShader(quads.buildOrThrow());
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    /** Pale shafts of moonlight slanting down from the storm toward the moon's side. */
    static void drawShafts(Matrix4f view, Vec3 eye, float scale, float moon) {
        if (moon < 0.2f) {
            return;
        }
        double seconds = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float mx = MOON.x / (float) Math.hypot(MOON.x, MOON.z);
        float mz = MOON.z / (float) Math.hypot(MOON.x, MOON.z);
        // Light comes down from the moon's side: the top of a shaft leans toward it.
        float lean = (float) ((STORM_Y - SEA_Y) / Math.tan(Math.toRadians(24)));
        for (int k = 0; k < 5; k++) {
            float along = 900 + 350 * k;
            float side = (float) ((k - 2) * 420 + 120 * Math.sin(seconds * 0.05 + k));
            float bx = mx * along - mz * side;
            float bz = mz * along + mx * side;
            float width = 60 + 40 * (k % 3);
            float a = 0.05f * moon * (0.6f + 0.4f * (float) Math.sin(seconds * 0.3 + k * 1.7));
            float y0 = (float) (SEA_Y - eye.y);
            float y1 = (float) (STORM_Y - eye.y);
            float tx = bx + mx * lean * 0.25f;
            float tz = bz + mz * lean * 0.25f;
            // Facing the camera across its width.
            float len = (float) Math.hypot(bx, bz);
            float sx = -bz / len * width;
            float sz = bx / len * width;
            quads.addVertex(view, (bx - sx) * scale, y0 * scale, (bz - sz) * scale).setColor(0.55f, 0.6f, 0.72f, 0.0f);
            quads.addVertex(view, (bx + sx) * scale, y0 * scale, (bz + sz) * scale).setColor(0.55f, 0.6f, 0.72f, 0.0f);
            quads.addVertex(view, (tx + sx) * scale, y1 * scale, (tz + sz) * scale).setColor(0.55f, 0.6f, 0.72f, a);
            quads.addVertex(view, (tx - sx) * scale, y1 * scale, (tz - sz) * scale).setColor(0.55f, 0.6f, 0.72f, a);
        }
        BufferUploader.drawWithShader(quads.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
}
