package com.rickr.backrooms.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Level 490's sky (the reference stills: heavy storm clouds at night, the moon breaking through).
 * <ul>
 *   <li><b>The vault:</b> a near-black storm overhead, the fog colour at the horizon.</li>
 *   <li><b>The moon:</b> low in one direction, now and then hidden as the clouds pass over it.</li>
 *   <li><b>The storm:</b> two layers of dark cloud far overhead, drifting, their edges silvered
 *       toward the moon.</li>
 *   <li><b>The cloud sea:</b> two layers far below the platforms, their tops grey in the moonlight;
 *       there is nothing else down there to see.</li>
 *   <li><b>The whirlwinds</b> ({@link Level490Whirlwinds}) and <b>the colossal chains</b> beyond
 *       render distance ({@link Level490SkyChains}), drawn over the storm layer (their tops fade
 *       out where they reach it) and under the cloud sea, which swallows their feet.</li>
 *   <li><b>The clouds as volumes</b> ({@link Level490VolumeClouds}): the cloud sea and the storm
 *       traced per pixel by a shader; the flat layers below are only the fallback if it fails.</li>
 *   <li><b>The hung ruins</b> ({@link Level490SkyHung}): ruins on rock hanging from chains far off
 *       in the haze, falling now and then.</li>
 *   <li><b>Lightning</b> ({@link Level490Lightning}): the bolts, and the clouds glowing round each strike.</li>
 * </ul>
 * The clouds use Level 39's cloud texture.
 */
final class Level490Sky {
    private static final ResourceLocation CLOUDS = Backrooms.id("textures/environment/l39_clouds.png");
    private static final ResourceLocation MOON = Backrooms.id("textures/environment/l490_moon.png");
    private static final float RADIUS = 100.0f;
    /** The storm overhead: near black, a little blue. */
    private static final float TOP_R = 0.060f;
    private static final float TOP_G = 0.066f;
    private static final float TOP_B = 0.085f;
    private static final float DARK_ELEVATION = 45.0f;

    /** Where the moon hangs (degrees) and how big it is (blocks at the vault's radius). */
    private static final float MOON_AZIMUTH = 35.0f;
    private static final float MOON_ELEVATION = 24.0f;
    private static final float MOON_SIZE = 11.0f;
    private static final Vector3f MOON_DIR = new Vector3f(
            Mth.cos(MOON_ELEVATION * Mth.DEG_TO_RAD) * Mth.cos(MOON_AZIMUTH * Mth.DEG_TO_RAD),
            Mth.sin(MOON_ELEVATION * Mth.DEG_TO_RAD),
            Mth.cos(MOON_ELEVATION * Mth.DEG_TO_RAD) * Mth.sin(MOON_AZIMUTH * Mth.DEG_TO_RAD));

    // The cloud layers, storm first (highest first) then the cloud sea (lowest last): height,
    // drift (blocks per second), texture size (blocks), opacity, colour and how much the moon lights them.
    private static final float[] LAYER_Y = {760.0f, 640.0f, 126.0f, 148.0f};
    private static final float[] DRIFT_X = {0.9f, 1.4f, 0.25f, -0.35f};
    private static final float[] DRIFT_Z = {0.5f, -0.6f, 0.15f, 0.2f};
    private static final float[] LAYER_TILE = {900.0f, 700.0f, 700.0f, 520.0f};
    private static final float[] LAYER_ALPHA = {0.8f, 0.85f, 1.0f, 0.9f};
    // Lighter than the black storm behind them, so the clouds read as shapes against it (the stills).
    private static final float[][] LAYER_RGB = {{0.24f, 0.25f, 0.285f}, {0.29f, 0.30f, 0.34f},
            {0.27f, 0.28f, 0.31f}, {0.31f, 0.32f, 0.36f}};
    private static final float[] MOONLIT = {0.50f, 0.60f, 0.22f, 0.30f};
    /** The clouds cover this many blocks around the camera, in a grid of this many quads a side. */
    private static final float CLOUD_REACH = 2400.0f;
    private static final int CLOUD_GRID = 18;
    /** Clouds fade into the haze with their distance across the ground (e-folding, blocks). */
    private static final float[] CLOUD_FADE = {2600.0f, 2200.0f, 1300.0f, 1500.0f};
    private static final float MAX_REACH = 3400.0f;

    private static final float[] FOG = new float[3];

    static void render(Matrix4f view, Camera camera) {
        Level490Atmosphere.fogColour(FOG);
        FogRenderer.setupNoFog();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        float flash = Level490Atmosphere.flash();
        Vec3 eye = camera.getPosition();
        float far = Minecraft.getInstance().gameRenderer.getDepthFar();
        drawVault(view, flash);
        drawMoon(view);
        boolean volumes = Level490VolumeClouds.available();
        if (volumes) {
            // The clouds as volumes; what hangs in the sky is drawn over them, each fading out where
            // it goes down into the cloud sea or up into the storm.
            Level490VolumeClouds.render(view, eye, moonShows(), FOG, flash);
        } else {
            drawClouds(view, eye, 0, 2);
        }
        Level490Whirlwinds.render(view, eye, far);
        Level490SkyHung.render(view, eye, far);
        Level490SkyChains.render(view, eye, far);
        Level490Lightning.render(view, eye, Math.min(1.0f, 0.9f * far / MAX_REACH));
        if (!volumes) {
            drawClouds(view, eye, 2, LAYER_Y.length);
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    /** Fog colour at the horizon, darkening to the storm overhead; below the horizon the cloud sea's grey. */
    private static void drawVault(Matrix4f view, float flash) {
        float topR = TOP_R + flash * 0.20f;
        float topG = TOP_G + flash * 0.21f;
        float topB = TOP_B + flash * 0.25f;
        int segments = 24;
        float darkY = Mth.sin(DARK_ELEVATION * Mth.DEG_TO_RAD) * RADIUS;
        float darkRing = Mth.cos(DARK_ELEVATION * Mth.DEG_TO_RAD) * RADIUS;
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder cap = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        cap.addVertex(view, 0.0f, RADIUS, 0.0f).setColor(topR, topG, topB, 1.0f);
        for (int i = 0; i <= segments; i++) {
            float a = i * Mth.TWO_PI / segments;
            cap.addVertex(view, Mth.cos(a) * darkRing, darkY, Mth.sin(a) * darkRing).setColor(topR, topG, topB, 1.0f);
        }
        BufferUploader.drawWithShader(cap.buildOrThrow());

        BufferBuilder band = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < segments; i++) {
            float a0 = i * Mth.TWO_PI / segments;
            float a1 = (i + 1) * Mth.TWO_PI / segments;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            band.addVertex(view, c0 * darkRing, darkY, s0 * darkRing).setColor(topR, topG, topB, 1.0f);
            band.addVertex(view, c1 * darkRing, darkY, s1 * darkRing).setColor(topR, topG, topB, 1.0f);
            band.addVertex(view, c1 * RADIUS, 0.0f, s1 * RADIUS).setColor(FOG[0], FOG[1], FOG[2], 1.0f);
            band.addVertex(view, c0 * RADIUS, 0.0f, s0 * RADIUS).setColor(FOG[0], FOG[1], FOG[2], 1.0f);
            band.addVertex(view, c0 * RADIUS, 0.0f, s0 * RADIUS).setColor(FOG[0], FOG[1], FOG[2], 1.0f);
            band.addVertex(view, c1 * RADIUS, 0.0f, s1 * RADIUS).setColor(FOG[0], FOG[1], FOG[2], 1.0f);
            band.addVertex(view, c1 * RADIUS, -RADIUS, s1 * RADIUS).setColor(FOG[0], FOG[1], FOG[2], 1.0f);
            band.addVertex(view, c0 * RADIUS, -RADIUS, s0 * RADIUS).setColor(FOG[0], FOG[1], FOG[2], 1.0f);
        }
        BufferUploader.drawWithShader(band.buildOrThrow());
    }

    /** How much of the moon shows through the passing clouds right now (0.15..1). */
    private static float moonShows() {
        double s = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
        float cover = (float) (0.5 + 0.5 * Math.sin(s * Mth.TWO_PI / 53.0) * Math.cos(s * Mth.TWO_PI / 31.0 + 1.3));
        float t = Mth.clamp((cover - 0.25f) / 0.5f, 0.0f, 1.0f);
        return 0.15f + 0.85f * t * t * (3.0f - 2.0f * t);
    }

    /** The moon and its glow: a textured square facing the camera, added onto the vault. */
    private static void drawMoon(Matrix4f view) {
        Vector3f c = new Vector3f(MOON_DIR).mul(RADIUS);
        // Two axes across the square: horizontal, and "up" on the vault.
        Vector3f right = new Vector3f(-MOON_DIR.z, 0.0f, MOON_DIR.x).normalize().mul(MOON_SIZE);
        Vector3f up = new Vector3f(MOON_DIR).cross(right).normalize().mul(MOON_SIZE);
        float a = moonShows();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, MOON);
        BufferBuilder quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        quad.addVertex(view, c.x - right.x - up.x, c.y - right.y - up.y, c.z - right.z - up.z).setUv(0, 1).setColor(0.86f, 0.89f, 0.95f, a);
        quad.addVertex(view, c.x + right.x - up.x, c.y + right.y - up.y, c.z + right.z - up.z).setUv(1, 1).setColor(0.86f, 0.89f, 0.95f, a);
        quad.addVertex(view, c.x + right.x + up.x, c.y + right.y + up.y, c.z + right.z + up.z).setUv(1, 0).setColor(0.86f, 0.89f, 0.95f, a);
        quad.addVertex(view, c.x - right.x + up.x, c.y - right.y + up.y, c.z - right.z + up.z).setUv(0, 0).setColor(0.86f, 0.89f, 0.95f, a);
        BufferUploader.drawWithShader(quad.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    /**
     * The cloud layers: flat textured grids that drift, lit by the moon toward its side and by
     * lightning toward the strike, fading into the fog with distance. Each layer is shrunk toward
     * the camera to fit inside the far plane.
     */
    private static void drawClouds(Matrix4f view, Vec3 eye, int firstLayer, int endLayer) {
        float far = Minecraft.getInstance().gameRenderer.getDepthFar();
        float scale = Math.min(1.0f, 0.9f * far / MAX_REACH);
        double seconds = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
        float step = 2.0f * CLOUD_REACH / CLOUD_GRID;
        float moon = moonShows();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, CLOUDS);
        for (int layer = firstLayer; layer < endLayer; layer++) {
            float ry = (float) (LAYER_Y[layer] - eye.y);
            double offX = seconds * DRIFT_X[layer];
            double offZ = seconds * DRIFT_Z[layer];
            // Texture coordinates follow the world and the wind, measured from a whole tile near the
            // camera so the numbers stay small (whole-tile jumps don't show).
            offX += Math.floor((eye.x - offX) / LAYER_TILE[layer]) * LAYER_TILE[layer];
            offZ += Math.floor((eye.z - offZ) / LAYER_TILE[layer]) * LAYER_TILE[layer];
            BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int i = 0; i < CLOUD_GRID; i++) {
                for (int j = 0; j < CLOUD_GRID; j++) {
                    float x0 = -CLOUD_REACH + i * step;
                    float z0 = -CLOUD_REACH + j * step;
                    cloudVertex(quads, view, eye, x0, ry, z0, offX, offZ, layer, scale, moon);
                    cloudVertex(quads, view, eye, x0 + step, ry, z0, offX, offZ, layer, scale, moon);
                    cloudVertex(quads, view, eye, x0 + step, ry, z0 + step, offX, offZ, layer, scale, moon);
                    cloudVertex(quads, view, eye, x0, ry, z0 + step, offX, offZ, layer, scale, moon);
                }
            }
            BufferUploader.drawWithShader(quads.buildOrThrow());
        }
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private static void cloudVertex(BufferBuilder quads, Matrix4f view, Vec3 eye, float rx, float ry, float rz,
                                    double offX, double offZ, int layer, float scale, float moon) {
        double wx = eye.x + rx;
        double wz = eye.z + rz;
        float u = (float) ((wx - offX) / LAYER_TILE[layer]);
        float v = (float) ((wz - offZ) / LAYER_TILE[layer]);
        float d = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        float horiz = (float) Math.sqrt(rx * rx + rz * rz) + 1e-3f;
        // Moonlight: strongest on the clouds toward the moon (a silver lining overhead, a pale
        // sheen on the cloud sea's tops).
        float toMoon = Math.max(0.0f, (rx * MOON_DIR.x + ry * MOON_DIR.y + rz * MOON_DIR.z) / d);
        float lit = MOONLIT[layer] * moon * (float) Math.pow(toMoon, layer < 2 ? 4.0 : 2.0);
        // Lightning lights the clouds around each strike (the storm overhead most).
        float bolt = Level490Lightning.cloudGlow(wx, wz) * (layer < 2 ? 1.1f : 0.6f);
        float[] rgb = LAYER_RGB[layer];
        float r = rgb[0] + lit * 0.86f + bolt * 0.50f;
        float g = rgb[1] + lit * 0.90f + bolt * 0.53f;
        float b = rgb[2] + lit * 1.00f + bolt * 0.62f;
        float alpha = LAYER_ALPHA[layer] * (float) Math.exp(-horiz / CLOUD_FADE[layer]);
        quads.addVertex(view, rx * scale, ry * scale, rz * scale).setUv(u, v).setColor(r, g, b, alpha);
    }

    private Level490Sky() {}
}
