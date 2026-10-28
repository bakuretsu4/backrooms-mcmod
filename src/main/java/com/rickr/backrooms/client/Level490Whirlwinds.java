package com.rickr.backrooms.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.Hash;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Level 490's whirlwinds (after Farum Azula's): gigantic pillars of turning cloud far off in the
 * storm, rising out of the cloud sea into the clouds overhead, flaring a little toward the top. Drawn in the sky pass as stacked rings of Level 39's cloud texture that turn around the
 * pillar's axis, see-through toward their edges so they read as soft and round; lightning near one
 * lights it from inside. Dark against the storm behind it. One per 2600-block cell (most cells),
 * never closer than 1000 blocks.
 */
final class Level490Whirlwinds {
    private static final ResourceLocation CLOUDS = Backrooms.id("textures/environment/l39_clouds.png");
    private static final int CELL = 2600;
    private static final double MIN_DISTANCE = 1000.0;
    private static final double MAX_DISTANCE = 7500.0;
    private static final float Y_BOTTOM = 60.0f;
    /** Up to the storm layer (Level490Sky's lowest at y 640): the top bands fade into it. */
    private static final float Y_TOP = 680.0f;
    private static final int BANDS = 20;
    private static final int SIDES = 24;

    private Level490Whirlwinds() {}

    static void render(Matrix4f view, Vec3 eye, float far) {
        if (!Level490ClientState.hasSeed()) {
            return;
        }
        long seed = Level490ClientState.seed();
        double t = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, CLOUDS);
        BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        boolean any = false;
        int ci = Math.floorDiv((int) eye.x, CELL);
        int cj = Math.floorDiv((int) eye.z, CELL);
        for (int i = ci - 3; i <= ci + 3; i++) {
            for (int j = cj - 3; j <= cj + 3; j++) {
                long h = Hash.of(seed, i, j, 4991);
                if (Hash.unit(h) > 0.7) {
                    continue;
                }
                double x = (i + 0.15 + 0.7 * Hash.unit(Hash.mix(h))) * CELL;
                double z = (j + 0.15 + 0.7 * Hash.unit(Hash.mix(h + 1))) * CELL;
                double d = Math.hypot(x - eye.x, z - eye.z);
                if (d < MIN_DISTANCE || d > MAX_DISTANCE) {
                    continue;
                }
                pillar(quads, view, eye, far, x, z, d, t, (float) Hash.unit(Hash.mix(h + 2)) * 6.28f, 0.8f + 0.5f * (float) Hash.unit(Hash.mix(h + 3)));
                any = true;
            }
        }
        if (any) {
            BufferUploader.drawWithShader(quads.buildOrThrow());
        } else {
            quads.build();
        }
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private static float radius(float y, double t, float phase, float size) {
        float f = (y - Y_BOTTOM) / (Y_TOP - Y_BOTTOM);
        return size * (70.0f + 120.0f * f * f + 18.0f * Mth.sin((float) (y / 85.0 + t * 0.35 + phase)));
    }

    private static void pillar(BufferBuilder quads, Matrix4f view, Vec3 eye, double far, double x, double z, double d, double t,
                               float phase, float size) {
        float scale = (float) Math.min(1.0, 0.9 * far / (d + 500.0 * size));
        float fade = (float) Math.exp(-(d - MIN_DISTANCE) / 5000.0);
        float glow = Level490Lightning.cloudGlow(x, z);
        float spin = (float) (t * 0.05);
        for (int band = 0; band < BANDS; band++) {
            float y0 = Y_BOTTOM + (Y_TOP - Y_BOTTOM) * band / BANDS;
            float y1 = Y_BOTTOM + (Y_TOP - Y_BOTTOM) * (band + 1) / BANDS;
            float r0 = radius(y0, t, phase, size);
            float r1 = radius(y1, t, phase, size);
            // Thinner toward the very bottom and top, where the clouds take it.
            float ends0 = Math.min(1.0f, Math.min(band / 2.0f, (BANDS - band) / 2.0f));
            float ends1 = Math.min(1.0f, Math.min((band + 1) / 2.0f, (BANDS - band - 1) / 2.0f));
            for (int s = 0; s < SIDES; s++) {
                float a0 = Mth.TWO_PI * s / SIDES;
                float a1 = Mth.TWO_PI * (s + 1) / SIDES;
                vertex(quads, view, eye, x, z, a0, y0, r0, s, y0, spin, scale, fade * ends0, glow, size);
                vertex(quads, view, eye, x, z, a1, y0, r0, s + 1, y0, spin, scale, fade * ends0, glow, size);
                vertex(quads, view, eye, x, z, a1, y1, r1, s + 1, y1, spin, scale, fade * ends1, glow, size);
                vertex(quads, view, eye, x, z, a0, y1, r1, s, y1, spin, scale, fade * ends1, glow, size);
            }
        }
    }

    private static void vertex(BufferBuilder quads, Matrix4f view, Vec3 eye, double x, double z, float a, float y, float r, int s,
                               float v, float spin, float scale, float fade, float glow, float size) {
        float px = (float) (x - eye.x) + Mth.cos(a) * r;
        float py = (float) (y - eye.y);
        float pz = (float) (z - eye.z) + Mth.sin(a) * r;
        // See-through toward the silhouette: how squarely this side faces the camera.
        float nx = Mth.cos(a);
        float nz = Mth.sin(a);
        float len = Mth.sqrt(px * px + pz * pz) + 1e-3f;
        float facing = Math.abs(-(nx * px + nz * pz) / len);
        float alpha = 0.97f * (float) Math.pow(facing, 0.45) * fade;
        // Dark against the lighter storm behind it (as Farum Azula's are), lit from inside by lightning.
        float c = 0.075f + 0.6f * glow;
        quads.addVertex(view, px * scale, py * scale, pz * scale)
                .setUv((float) s / SIDES * 3.0f + spin, v / (300.0f * size))
                .setColor(c, c * 1.04f, c * 1.15f, alpha);
    }
}
