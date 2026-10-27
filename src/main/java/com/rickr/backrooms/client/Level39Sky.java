package com.rickr.backrooms.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.layout.Level39Layout;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Level 39's "sky": there is none. Above the town hangs the inside of the superstructure, and
 * under its ceiling the superstructure's own clouds.
 *
 * <ul>
 *   <li><b>The vault:</b> the fog colour at the horizon darkening to a near-black grey overhead.</li>
 *   <li><b>The far facades</b> ({@link Level39FarFacade}), drawn here first so all terrain covers them.</li>
 *   <li><b>The cloud ceiling:</b> three layers of dark, slowly drifting cloud at y
 *       {@link #LAYER_Y}, lit from below near the facades (the windows' glow on their undersides)
 *       and fading into the fog with distance. It is where the indoor storm's rain comes from.</li>
 * </ul>
 * In the tunnels only the plain fog colour is drawn (no sky is visible down there anyway).
 */
final class Level39Sky {
    private static final ResourceLocation CLOUDS = Backrooms.id("textures/environment/l39_clouds.png");
    private static final float RADIUS = 100.0f;
    /**
     * The overhead colour of the vault: a dull grey, darker than the fog but never black (the
     * mill's light hangs in the air all the way up, as in the photo).
     */
    private static final float VAULT_R = 0.10f;
    private static final float VAULT_G = 0.105f;
    private static final float VAULT_B = 0.115f;
    /** Elevation (degrees) above which the vault is at its darkest. */
    private static final float DARK_ELEVATION = 50.0f;

    /** Heights of the three cloud layers (lowest first), their drift (blocks per second, x and z) ... */
    private static final float[] LAYER_Y = {252.0f, 266.0f, 281.0f};
    private static final float[] DRIFT_X = {0.55f, -0.35f, 0.22f};
    private static final float[] DRIFT_Z = {0.20f, 0.42f, -0.15f};
    /** ... how big their texture is in blocks, and how opaque each layer is. */
    private static final float[] LAYER_TILE = {320.0f, 420.0f, 560.0f};
    private static final float[] LAYER_ALPHA = {0.85f, 0.75f, 0.9f};
    /** The clouds cover this many blocks around the camera, in a grid of this many quads a side. */
    private static final float CLOUD_REACH = 1100.0f;
    private static final int CLOUD_GRID = 12;
    /** Cloud colour: mid grey, and how much the facades' glow lights their undersides. */
    private static final float CLOUD_R = 0.15f;
    private static final float CLOUD_G = 0.155f;
    private static final float CLOUD_B = 0.165f;
    /** The glow of the whole town in the fog lights every cloud a little from below ... */
    private static final float UNDERLIGHT_BASE = 0.10f;
    /** ... and the facades light them strongly, fading over this many blocks away from the walls. */
    private static final float UNDERLIGHT = 0.50f;
    private static final float UNDERLIGHT_REACH = 200.0f;
    /** Clouds fade into the fog over this many blocks (e-folding distance). */
    private static final float CLOUD_FOG = 520.0f;
    /** The furthest cloud point drawn (for shrinking it inside the far plane). */
    private static final float MAX_REACH = 1600.0f;

    private static final float[] FOG = new float[3];

    static void render(Matrix4f view, Camera camera, float partialTick) {
        Level39Atmosphere.fogColour(FOG);
        FogRenderer.setupNoFog();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        Vec3 eye = camera.getPosition();
        boolean town = !Level39ClientState.inTunnels(eye);
        drawVault(view, town);
        if (town) {
            Level39FarFacade.renderSky(view, camera);
            drawClouds(view, eye);
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    /** Fog colour at and below the horizon, darkening to the vault colour overhead. */
    private static void drawVault(Matrix4f view, boolean town) {
        float topR = town ? VAULT_R : FOG[0];
        float topG = town ? VAULT_G : FOG[1];
        float topB = town ? VAULT_B : FOG[2];
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

    /**
     * The cloud layers: flat textured grids that drift slowly, darkest far from the facades and
     * lit from below near them. Each grid point's colour and fade is worked out from its real
     * distance; the whole layer is then shrunk toward the camera to fit inside the far plane.
     */
    private static void drawClouds(Matrix4f view, Vec3 eye) {
        float far = Minecraft.getInstance().gameRenderer.getDepthFar();
        float scale = Math.min(1.0f, 0.9f * far / MAX_REACH);
        double seconds = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
        float rain = Level39Weather.rain();
        float step = 2.0f * CLOUD_REACH / CLOUD_GRID;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, CLOUDS);
        // Highest layer first, so the lower ones lie over it.
        for (int layer = LAYER_Y.length - 1; layer >= 0; layer--) {
            float ry = (float) (LAYER_Y[layer] - eye.y);
            // Storm clouds are a little thicker.
            float layerAlpha = Math.min(1.0f, LAYER_ALPHA[layer] * (1.0f + 0.2f * rain));
            double offX = seconds * DRIFT_X[layer];
            double offZ = seconds * DRIFT_Z[layer];
            // Texture coordinates follow the world and the wind. Measured from a whole tile near
            // the camera, so the numbers stay small and precise (whole-tile jumps don't show).
            offX += Math.floor((eye.x - offX) / LAYER_TILE[layer]) * LAYER_TILE[layer];
            offZ += Math.floor((eye.z - offZ) / LAYER_TILE[layer]) * LAYER_TILE[layer];
            BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int i = 0; i < CLOUD_GRID; i++) {
                for (int j = 0; j < CLOUD_GRID; j++) {
                    float x0 = -CLOUD_REACH + i * step;
                    float z0 = -CLOUD_REACH + j * step;
                    cloudVertex(quads, view, eye, x0, ry, z0, offX, offZ, layer, layerAlpha, scale, rain);
                    cloudVertex(quads, view, eye, x0 + step, ry, z0, offX, offZ, layer, layerAlpha, scale, rain);
                    cloudVertex(quads, view, eye, x0 + step, ry, z0 + step, offX, offZ, layer, layerAlpha, scale, rain);
                    cloudVertex(quads, view, eye, x0, ry, z0 + step, offX, offZ, layer, layerAlpha, scale, rain);
                }
            }
            BufferUploader.drawWithShader(quads.buildOrThrow());
        }
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private static void cloudVertex(BufferBuilder quads, Matrix4f view, Vec3 eye, float rx, float ry, float rz,
                                    double offX, double offZ, int layer, float layerAlpha, float scale, float rain) {
        double wx = eye.x + rx;
        double wz = eye.z + rz;
        float u = (float) ((wx - offX) / LAYER_TILE[layer]);
        float v = (float) ((wz - offZ) / LAYER_TILE[layer]);
        // Underlight: how near this point is to the glowing facades (lower layers catch more).
        int facade = Level39Layout.facadeDistance(Mth.floor(wx), Mth.floor(wz));
        float near = (float) Math.exp(Math.min(0, facade) / UNDERLIGHT_REACH);
        float glow = (UNDERLIGHT_BASE + UNDERLIGHT * near) * (1.0f - 0.25f * layer) * (1.0f - 0.4f * rain);
        float r = CLOUD_R + glow * 0.86f;
        float g = CLOUD_G + glow * 0.90f;
        float b = CLOUD_B + glow;
        float d = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        float alpha = layerAlpha * (float) Math.exp(-d / CLOUD_FOG);
        quads.addVertex(view, rx * scale, ry * scale, rz * scale).setUv(u, v).setColor(r, g, b, alpha);
    }

    private Level39Sky() {}
}
