package com.rickr.backrooms.client.level228;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * The buffers a mirror puppet is drawn into: the frame's real buffer source, with two changes
 * the plain one can't make.
 * <ul>
 *   <li><b>Mirrored winding.</b> A puppet seen through an odd number of mirrors is drawn with a
 *       negative scale on one axis, which turns every quad inside out: back-face culling would
 *       then keep the inside of the model and drop the outside. Each quad's four corners are
 *       handed on in the opposite order (0, 3, 2, 1), which faces it the right way again without
 *       touching the render type, so the shader pack (Iris), Veil and vanilla all draw the puppet
 *       with their usual entity programs and culling.</li>
 *   <li><b>Fading.</b> A reflection that walks off into the dark fades out; every vertex's alpha
 *       is scaled (the player model's render type is translucent; cut-out layers simply vanish
 *       once they drop below the cut-out threshold).</li>
 * </ul>
 * Only formats with a normal are reordered: their vertices end with the normal (entity, item and
 * block formats), so the end of every vertex is known; anything else is passed on unchanged
 * (lines, debug shapes: drawn without culling anyway).
 *
 * <p>Everything is preallocated: a small ring of wrappers is handed out round-robin, enough for
 * the renderers that hold two consumers at once (an enchanted item's glint plus its base).
 */
final class PuppetBuffers implements MultiBufferSource {
    private static final int RING = 16;
    private static final int MAX_TYPES = 32;

    private final Flip[] ring = new Flip[RING];
    private int next;
    private final RenderType[] used = new RenderType[MAX_TYPES];
    private int usedCount;

    private MultiBufferSource.BufferSource source;
    private boolean mirrored;
    private float alpha = 1.0f;

    PuppetBuffers() {
        for (int i = 0; i < RING; i++) {
            ring[i] = new Flip();
        }
    }

    /** Draw the next puppet into {@code source}: mirrored or not, at this opacity (0..1). */
    void begin(MultiBufferSource.BufferSource source, boolean mirrored, float alpha) {
        this.source = source;
        this.mirrored = mirrored;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        VertexConsumer base = source.getBuffer(type);
        remember(type);
        boolean reorder = mirrored && type.mode() == VertexFormat.Mode.QUADS && type.format().contains(VertexFormatElement.NORMAL);
        if (!reorder && alpha >= 1.0f) {
            return base; // nothing to change: the fast path, straight into the real buffer
        }
        Flip flip = ring[next];
        next = (next + 1) % RING;
        flip.reset(base, reorder, alpha);
        return flip;
    }

    /** Draws everything the puppets put into the buffers, so it lands in this render stage. */
    void endBatches() {
        for (int i = 0; i < usedCount; i++) {
            source.endBatch(used[i]);
            used[i] = null;
        }
        usedCount = 0;
    }

    private void remember(RenderType type) {
        for (int i = 0; i < usedCount; i++) {
            if (used[i] == type) {
                return;
            }
        }
        if (usedCount < MAX_TYPES) {
            used[usedCount++] = type;
        }
    }

    /**
     * Collects a quad's four vertices and passes them on in reverse order (when {@link #reorder}),
     * scaling alpha. A vertex is complete after its bulk call, or (piece by piece) after its
     * normal, the last element of every format we reorder.
     */
    private static final class Flip implements VertexConsumer {
        private static final int HAS_COLOR = 1;
        private static final int HAS_UV = 2;
        private static final int HAS_UV1 = 4;
        private static final int HAS_UV2 = 8;

        private VertexConsumer out;
        private boolean reorder;
        private float alpha;

        private final float[] x = new float[4];
        private final float[] y = new float[4];
        private final float[] z = new float[4];
        private final int[] r = new int[4];
        private final int[] g = new int[4];
        private final int[] b = new int[4];
        private final int[] a = new int[4];
        private final float[] u = new float[4];
        private final float[] v = new float[4];
        private final int[] uv1u = new int[4];
        private final int[] uv1v = new int[4];
        private final int[] uv2u = new int[4];
        private final int[] uv2v = new int[4];
        private final float[] nx = new float[4];
        private final float[] ny = new float[4];
        private final float[] nz = new float[4];
        private final int[] has = new int[4];
        /** Vertices of the current quad started so far (the one being filled is count - 1). */
        private int count;

        void reset(VertexConsumer out, boolean reorder, float alpha) {
            this.out = out;
            this.reorder = reorder;
            this.alpha = alpha;
            this.count = 0;
        }

        private int fade(int alphaIn) {
            return alpha >= 1.0f ? alphaIn : (int) (alphaIn * alpha);
        }

        @Override
        public VertexConsumer addVertex(float px, float py, float pz) {
            if (!reorder) {
                out.addVertex(px, py, pz);
                return this;
            }
            if (count == 4) {
                count = 0; // a quad without normals slipped through: start over rather than overflow
            }
            int i = count++;
            x[i] = px;
            y[i] = py;
            z[i] = pz;
            has[i] = 0;
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alphaIn) {
            if (!reorder) {
                out.setColor(red, green, blue, fade(alphaIn));
                return this;
            }
            int i = count - 1;
            if (i >= 0) {
                r[i] = red;
                g[i] = green;
                b[i] = blue;
                a[i] = fade(alphaIn);
                has[i] |= HAS_COLOR;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float uu, float vv) {
            if (!reorder) {
                out.setUv(uu, vv);
                return this;
            }
            int i = count - 1;
            if (i >= 0) {
                u[i] = uu;
                v[i] = vv;
                has[i] |= HAS_UV;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int uu, int vv) {
            if (!reorder) {
                out.setUv1(uu, vv);
                return this;
            }
            int i = count - 1;
            if (i >= 0) {
                uv1u[i] = uu;
                uv1v[i] = vv;
                has[i] |= HAS_UV1;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv2(int uu, int vv) {
            if (!reorder) {
                out.setUv2(uu, vv);
                return this;
            }
            int i = count - 1;
            if (i >= 0) {
                uv2u[i] = uu;
                uv2v[i] = vv;
                has[i] |= HAS_UV2;
            }
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x1, float y1, float z1) {
            if (!reorder) {
                out.setNormal(x1, y1, z1);
                return this;
            }
            int i = count - 1;
            if (i >= 0) {
                nx[i] = x1;
                ny[i] = y1;
                nz[i] = z1;
                if (count == 4) {
                    emitQuad();
                }
            }
            return this;
        }

        @Override
        public void addVertex(float px, float py, float pz, int color, float uu, float vv, int overlay, int light,
                float normalX, float normalY, float normalZ) {
            if (!reorder) {
                out.addVertex(px, py, pz, fadeColor(color), uu, vv, overlay, light, normalX, normalY, normalZ);
                return;
            }
            if (count == 4) {
                count = 0;
            }
            int i = count++;
            x[i] = px;
            y[i] = py;
            z[i] = pz;
            r[i] = (color >> 16) & 0xFF;
            g[i] = (color >> 8) & 0xFF;
            b[i] = color & 0xFF;
            a[i] = fade((color >>> 24) & 0xFF);
            u[i] = uu;
            v[i] = vv;
            uv1u[i] = overlay & 0xFFFF;
            uv1v[i] = (overlay >> 16) & 0xFFFF;
            uv2u[i] = light & 0xFFFF;
            uv2v[i] = (light >> 16) & 0xFFFF;
            nx[i] = normalX;
            ny[i] = normalY;
            nz[i] = normalZ;
            has[i] = HAS_COLOR | HAS_UV | HAS_UV1 | HAS_UV2;
            if (count == 4) {
                emitQuad();
            }
        }

        private int fadeColor(int color) {
            if (alpha >= 1.0f) {
                return color;
            }
            return (fade((color >>> 24) & 0xFF) << 24) | (color & 0xFFFFFF);
        }

        /** Corner 0 stays first, then 3, 2, 1: the same quad, wound the other way round. */
        private void emitQuad() {
            emit(0);
            emit(3);
            emit(2);
            emit(1);
            count = 0;
        }

        private void emit(int i) {
            int h = has[i];
            if (h == (HAS_COLOR | HAS_UV | HAS_UV1 | HAS_UV2)) {
                // The full entity layout: one bulk call, which the real buffer writes in one go.
                int color = (a[i] << 24) | (r[i] << 16) | (g[i] << 8) | b[i];
                out.addVertex(x[i], y[i], z[i], color, u[i], v[i], uv1u[i] | (uv1v[i] << 16), uv2u[i] | (uv2v[i] << 16),
                        nx[i], ny[i], nz[i]);
                return;
            }
            out.addVertex(x[i], y[i], z[i]);
            if ((h & HAS_COLOR) != 0) {
                out.setColor(r[i], g[i], b[i], a[i]);
            }
            if ((h & HAS_UV) != 0) {
                out.setUv(u[i], v[i]);
            }
            if ((h & HAS_UV1) != 0) {
                out.setUv1(uv1u[i], uv1v[i]);
            }
            if ((h & HAS_UV2) != 0) {
                out.setUv2(uv2u[i], uv2v[i]);
            }
            out.setNormal(nx[i], ny[i], nz[i]);
        }
    }
}
