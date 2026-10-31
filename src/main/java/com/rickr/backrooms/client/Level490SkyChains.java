package com.rickr.backrooms.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.world.Hash;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The colossal chains of Level 490 where the world's blocks don't reach: every chain beyond render
 * distance, drawn in the sky where its blocks hang (the same links as world/layout/Level490Layout),
 * and a few far bigger ones, miles off, that are only ever sky. They fade into the haze with
 * distance; lightning lights up the ones near the strike, so a flash shows more of them than you
 * thought were there. Each link is drawn along its centre line as a ribbon facing the camera, as
 * thick as the link's bar: face on it reads as a ring, edge on as a bar.
 *
 * <p>The chains within render distance are blocks, but blocks stop at the top of the world (y 511);
 * from there to the storm's floor {@link Level490ChainTops} draws them solid, and from there on up
 * into the storm they are drawn here.
 */
final class Level490SkyChains {
    // The built chains' links (world/layout/Level490Layout).
    private static final float LINK_ARC = 6.0f;
    private static final float LINK_STRAIGHT = 8.0f;
    private static final float BAR = 3.0f;
    private static final float PITCH = 22.0f;
    /** Built chains show this far beyond render distance ... */
    private static final double SHOW = 1000.0;
    /** ... fading over this many blocks (e-folding): mostly it takes a flash to see them. */
    private static final double FADE = 300.0;
    /** The giant sky-only chains: this many times bigger, one per cell this big, seen this far. */
    private static final float GIANT = 9.0f;
    private static final int GIANT_CELL = 3000;
    private static final double GIANT_SHOW = 11000.0;
    private static final float[][] LOOP = loop();
    /** Drawn up to the storm layer (Level490Sky's lowest at y 640), which they vanish into. */
    private static final float TOP = 640.0f;
    /** And down to the cloud sea's top, which they vanish into the same way. */
    private static final float SEA = 130.0f;
    /** Near chains are solid up to the storm's floor (Level490ChainTops); a link's half length below that the sky takes over. */
    private static final float BUILT_TOP = Level490ChainTops.SOLID_TOP - 17.0f;

    private Level490SkyChains() {}

    /** The centre line of a link as a closed loop (u across, v up), from world/layout/Level490Layout. */
    private static float[][] loop() {
        int arcSteps = 5;
        java.util.List<float[]> pts = new java.util.ArrayList<>();
        for (int k = 0; k <= arcSteps; k++) {                     // top arc, right to left
            double a = Math.PI * k / arcSteps;
            pts.add(new float[] {(float) (LINK_ARC * Math.cos(a)), (float) (LINK_STRAIGHT + LINK_ARC * Math.sin(a))});
        }
        for (int k = 0; k <= arcSteps; k++) {                     // bottom arc, left to right
            double a = Math.PI + Math.PI * k / arcSteps;
            pts.add(new float[] {(float) (LINK_ARC * Math.cos(a)), (float) (-LINK_STRAIGHT + LINK_ARC * Math.sin(a))});
        }
        pts.add(pts.get(0));
        return pts.toArray(new float[0][]);
    }

    static void render(Matrix4f view, Vec3 eye, float far) {
        if (!Level490ClientState.hasSeed()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double built = mc.options.getEffectiveRenderDistance() * 16.0 - 12.0;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int[] count = {0};
        for (int[] c : Level490ClientState.chains(eye.x, eye.z)) {
            double d = Math.hypot(c[0] - eye.x, c[1] - eye.z);
            if (d < built) {
                // Lit like the blocks below them, so the join doesn't show.
                chain(quads, view, eye, far, c[0], c[1], 1.0f, c[2], BUILT_TOP, TOP, 1.0f, d, 0.15f, count);
                continue;
            }
            if (d > built + SHOW) {
                continue;
            }
            float alpha = (float) Math.exp(-(d - built) / FADE);
            alpha = Math.min(1.0f, alpha + Level490Lightning.cloudGlow(c[0], c[1]) * 0.9f);
            if (alpha > 0.02f) {
                chain(quads, view, eye, far, c[0], c[1], 1.0f, c[2], SEA, TOP, alpha, d, 0.06f, count);
            }
        }
        // The giants, miles off.
        long seed = Level490ClientState.seed();
        int gx = Math.floorDiv((int) eye.x, GIANT_CELL);
        int gz = Math.floorDiv((int) eye.z, GIANT_CELL);
        for (int i = gx - 4; i <= gx + 4; i++) {
            for (int j = gz - 4; j <= gz + 4; j++) {
                long h = Hash.of(seed, i, j, 4990);
                if (Hash.unit(h) > 0.55) {
                    continue;
                }
                double x = (i + Hash.unit(Hash.mix(h))) * GIANT_CELL;
                double z = (j + Hash.unit(Hash.mix(h + 1))) * GIANT_CELL;
                double d = Math.hypot(x - eye.x, z - eye.z);
                if (d < 3000 || d > GIANT_SHOW) {
                    continue;
                }
                float alpha = (float) Math.min(1.0, 0.75 * Math.exp(-(d - 3000) / 4500.0) + Level490Lightning.cloudGlow(x, z));
                chain(quads, view, eye, far, x, z, GIANT, -1500.0f, SEA, TOP, alpha, d, 0.06f, count);
            }
        }
        if (count[0] > 0) {
            BufferUploader.drawWithShader(quads.buildOrThrow());
        } else {
            quads.build();
        }
        RenderSystem.disableBlend();
    }

    /**
     * One chain hanging from yTop down past yBottom, size times a built chain's links, iron this
     * bright; jerked down when Level490ChainEvents says it is being jolted. Level490SkyHung hangs its
     * ruins on these too.
     */
    static void chain(BufferBuilder quads, Matrix4f view, Vec3 eye, float far, double x, double z, float size,
                              float phase, float yBottom, float yTop, float alpha, double d, float shade, int[] count) {
        float scale = (float) Math.min(1.0, 0.9 * far / (d + 600.0 * size));
        // Black iron, a little moonlight on it, lit up when lightning flashes near.
        float glow = Level490Lightning.cloudGlow(x, z);
        float r = shade + 0.05f * glow;
        float g = shade * 1.03f + 0.05f * glow;
        float b = shade * 1.17f + 0.06f * glow;
        float pitch = PITCH * size;
        float pull = Level490ChainEvents.pull(x, z);
        int k0 = (int) Math.floor((yBottom - phase) / pitch);
        int k1 = (int) Math.ceil((yTop - phase) / pitch);
        for (int k = k0; k <= k1; k++) {
            float cy = phase + k * pitch + pull;
            boolean inX = (k & 1) == 0;
            // Into the storm and into the cloud sea: the end links fade out.
            float a = alpha * Math.max(0.0f, Math.min(1.0f, (yTop - cy) / (pitch * 2)))
                    * Math.max(0.0f, Math.min(1.0f, (cy - yBottom) / (pitch * 1.5f)));
            if (a < 0.02f) {
                continue;
            }
            for (int p = 0; p + 1 < LOOP.length; p++) {
                float u0 = LOOP[p][0] * size;
                float v0 = LOOP[p][1] * size;
                float u1 = LOOP[p + 1][0] * size;
                float v1 = LOOP[p + 1][1] * size;
                Vector3f p0 = new Vector3f((float) (x - eye.x) + (inX ? u0 : 0), (float) (cy + v0 - eye.y), (float) (z - eye.z) + (inX ? 0 : u0));
                Vector3f p1 = new Vector3f((float) (x - eye.x) + (inX ? u1 : 0), (float) (cy + v1 - eye.y), (float) (z - eye.z) + (inX ? 0 : u1));
                ribbon(quads, view, p0, p1, BAR * 2 * size, scale, r, g, b, a);
                count[0]++;
            }
        }
    }

    /**
     * A chain draped along a curve (points relative to the camera, unscaled), links {@code size}
     * times a built chain's, each turned a quarter from the last; an open shackle (a link half
     * again as big) at the end if {@code shackle}. For the creature's chains (Level490Creature).
     */
    static void drape(BufferBuilder quads, Matrix4f view, float[][] pts, float size, float scale, float r, float g, float b, float a,
                      boolean shackle) {
        float pitch = PITCH * size;
        float carried = 0;
        int k = 0;
        Vector3f up = new Vector3f(0, 1, 0);
        for (int i = 0; i + 1 < pts.length; i++) {
            Vector3f p0 = new Vector3f(pts[i][0], pts[i][1], pts[i][2]);
            Vector3f p1 = new Vector3f(pts[i + 1][0], pts[i + 1][1], pts[i + 1][2]);
            Vector3f t = new Vector3f(p1).sub(p0);
            float len = t.length();
            if (len < 1e-3f) {
                continue;
            }
            t.div(len);
            Vector3f n1 = new Vector3f(t).cross(Math.abs(t.y) > 0.95f ? new Vector3f(1, 0, 0) : up).normalize();
            Vector3f n2 = new Vector3f(t).cross(n1).normalize();
            for (float s = carried; s < len; s += pitch, k++) {
                boolean last = shackle && i + 2 == pts.length && s + pitch >= len;
                link(quads, view, new Vector3f(t).mul(s).add(p0), t, (k & 1) == 0 ? n1 : n2, size * (last ? 1.5f : 1.0f), scale, r, g, b, a,
                        last);
                carried = s + pitch - len;
            }
        }
    }

    /** One link at c, its long axis along t, its plane holding t and n; open at the top if a shackle. */
    private static void link(BufferBuilder quads, Matrix4f view, Vector3f c, Vector3f t, Vector3f n, float size, float scale,
                             float r, float g, float b, float a, boolean open) {
        for (int p = 0; p + 1 < LOOP.length; p++) {
            if (open && LOOP[p][1] > LINK_STRAIGHT * 0.5f && LOOP[p + 1][1] > LINK_STRAIGHT * 0.5f) {
                continue; // the shackle's gap
            }
            Vector3f p0 = new Vector3f(c).add(new Vector3f(n).mul(LOOP[p][0] * size)).add(new Vector3f(t).mul(LOOP[p][1] * size));
            Vector3f p1 = new Vector3f(c).add(new Vector3f(n).mul(LOOP[p + 1][0] * size)).add(new Vector3f(t).mul(LOOP[p + 1][1] * size));
            ribbon(quads, view, p0, p1, BAR * 2 * size, scale, r, g, b, a);
        }
    }

    private static void ribbon(BufferBuilder quads, Matrix4f view, Vector3f p0, Vector3f p1, float width, float scale,
                               float r, float g, float b, float a) {
        Vector3f dir = new Vector3f(p1).sub(p0);
        Vector3f toEye = new Vector3f(p0).add(p1).mul(-0.5f);
        Vector3f side = dir.cross(toEye, new Vector3f());
        if (side.lengthSquared() < 1e-6f) {
            side.set(1, 0, 0);
        }
        side.normalize().mul(width * 0.5f * scale);
        p0.mul(scale);
        p1.mul(scale);
        quads.addVertex(view, p0.x - side.x, p0.y - side.y, p0.z - side.z).setColor(r, g, b, a);
        quads.addVertex(view, p0.x + side.x, p0.y + side.y, p0.z + side.z).setColor(r, g, b, a);
        quads.addVertex(view, p1.x + side.x, p1.y + side.y, p1.z + side.z).setColor(r, g, b, a);
        quads.addVertex(view, p1.x - side.x, p1.y - side.y, p1.z - side.z).setColor(r, g, b, a);
    }
}
