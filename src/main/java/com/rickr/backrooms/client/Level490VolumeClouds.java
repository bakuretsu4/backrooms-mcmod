package com.rickr.backrooms.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Level 490's clouds as real volumes (shaders/core/l490_clouds): one full-screen pass in the sky
 * that follows every pixel's ray through the cloud sea and the storm, lit by the moon and by
 * lightning (see the shader for how). Drawn in the sky pass (client/Level490Sky), so the terrain
 * is drawn over it afterwards. If the shader fails to load, the sky falls back to the old flat
 * cloud layers.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level490VolumeClouds {
    private static final Logger LOGGER = LoggerFactory.getLogger(Level490VolumeClouds.class);
    private static final ResourceLocation NOISE = Backrooms.id("textures/environment/l490_cloud_noise.png");
    private static final float MOON_AZIMUTH = 35.0f;
    private static final float MOON_ELEVATION = 24.0f;
    /** Wind (blocks a second): the cloud sea drifts one way. */
    private static final float WIND_X = 2.2f;
    private static final float WIND_Z = 1.1f;

    @Nullable
    private static ShaderInstance shader;
    @Nullable
    private static VertexBuffer screenQuad;
    private static final Matrix4f INV_PROJECTION = new Matrix4f();

    private Level490VolumeClouds() {}

    /** True when the shader loaded. */
    static boolean available() {
        return shader != null;
    }

    /** Mod bus: the cloud shader. A broken shader only logs an error; the sky then keeps its flat clouds. */
    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        shader = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Backrooms.id("l490_clouds"), DefaultVertexFormat.POSITION),
                    s -> shader = s);
        } catch (Exception e) {
            LOGGER.error("[{}] Level 490 cloud shader failed to load; the sky keeps its flat clouds", Backrooms.MOD_ID, e);
        }
    }

    /** Draws the clouds over the sky drawn so far (the sky pass: depth test off). */
    static void render(Matrix4f view, Vec3 eye, float moon, float[] fog, float flash) {
        ShaderInstance s = shader;
        if (s == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (screenQuad == null) {
            BufferBuilder quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            quad.addVertex(-1.0f, -1.0f, 0.0f);
            quad.addVertex(1.0f, -1.0f, 0.0f);
            quad.addVertex(1.0f, 1.0f, 0.0f);
            quad.addVertex(-1.0f, 1.0f, 0.0f);
            screenQuad = new VertexBuffer(VertexBuffer.Usage.STATIC);
            screenQuad.bind();
            screenQuad.upload(quad.buildOrThrow());
            VertexBuffer.unbind();
        }
        AbstractTexture noise = mc.getTextureManager().getTexture(NOISE);
        noise.setFilter(true, false);
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        double seconds = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
        s.setSampler("Noise", noise.getId());
        s.setDefaultUniforms(VertexFormat.Mode.QUADS, view, projection, mc.getWindow());
        s.safeGetUniform("InvProjMat").set(INV_PROJECTION.set(projection).invert());
        s.safeGetUniform("Eye").set((float) eye.x, (float) eye.y, (float) eye.z);
        s.safeGetUniform("Wind").set((float) (seconds * WIND_X % 100_000), 0.0f, (float) (seconds * WIND_Z % 100_000));
        s.safeGetUniform("Time").set((float) (seconds % 1000.0));
        float ce = (float) Math.cos(Math.toRadians(MOON_ELEVATION));
        s.safeGetUniform("MoonDir").set(ce * (float) Math.cos(Math.toRadians(MOON_AZIMUTH)), (float) Math.sin(Math.toRadians(MOON_ELEVATION)),
                ce * (float) Math.sin(Math.toRadians(MOON_AZIMUTH)));
        s.safeGetUniform("MoonLight").set(moon);
        s.safeGetUniform("HazeColor").set(fog[0], fog[1], fog[2]);
        float[] strike = Level490Lightning.brightest();
        if (strike != null) {
            s.safeGetUniform("Strike").set(strike[0], 640.0f, strike[1], strike[2]);
        } else {
            s.safeGetUniform("Strike").set(0.0f, 0.0f, 0.0f, 0.0f);
        }
        s.safeGetUniform("Flash").set(flash);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        s.apply();
        screenQuad.bind();
        screenQuad.draw();
        VertexBuffer.unbind();
        s.clear();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
}
