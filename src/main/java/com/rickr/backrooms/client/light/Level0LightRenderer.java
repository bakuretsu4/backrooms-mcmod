package com.rickr.backrooms.client.light;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import foundry.veil.api.client.registry.LightTypeRegistry;
import foundry.veil.api.client.render.VeilRenderSystem;
import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.ClientLighting;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.framebuffer.VeilFramebuffers;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.InstancedLightRenderer;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import foundry.veil.api.client.render.light.renderer.LightTypeRenderer;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.vertex.VertexArray;
import foundry.veil.api.client.render.vertex.VertexArrayBuilder;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

/**
 * Veil renderer for Level 0's lamps, and (a second instance) Level 2's: our own light types, so
 * the lamps can use our shaders.
 *
 * <p>Each lamp is drawn like Veil's point lights (a box around its reach, shading the pixels in
 * it; same data layout), but the shader (pinwheel/shaders/program/light/level0_point, or
 * level2_point for Level 2) takes its shadows from the wall map ({@link Level0WallGrid}) instead
 * of Veil's 3D voxel grid: a walk of ~10 block columns from above instead of up to 128 voxel
 * steps, reaching well past the voxel grid's 32 blocks, block-exact. Level 2's shader also adds
 * the gloss highlights on wet and metal surfaces (Level2Gloss, Level2Shading).
 *
 * <p>Level 0 only: after the lamps it draws one full-screen pass (level0_screen) that darkens
 * corners and wall feet and removes the vanilla bounce fill where the power is out (see that
 * shader's comment). The pass needs this renderer to run even when no lamp is on screen, so it
 * counts itself as one visible light while Level 0's shading is wanted ({@link Level0Shading#wanted});
 * an invisible "anchor" lamp (VeilFixtures) keeps the renderer alive. Level 2 has no screen pass:
 * its darkness is set by the lightmap (ClientLighting) and the grade (AtmosphereGrade).
 *
 * <p>Only used when Veil is loaded: nothing outside client/light touches this class.
 */
final class Level0LightRenderer extends InstancedLightRenderer<Level0LightData> {
    static final ResourceLocation RENDER_TYPE = Backrooms.id("light/level0_point");
    static final ResourceLocation LEVEL2_RENDER_TYPE = Backrooms.id("light/level2_point");
    static final ResourceLocation SCREEN_SHADER = Backrooms.id("light/level0_screen");
    /** Our light types. Not in Veil's registry (that is only for its editor): Veil keys renderers by these objects. */
    static final LightTypeRegistry.LightType<Level0LightData> TYPE = new LightTypeRegistry.LightType<>(() -> new Level0LightRenderer(false), null);
    static final LightTypeRegistry.LightType<Level0LightData> LEVEL2_TYPE = new LightTypeRegistry.LightType<>(() -> new Level0LightRenderer(true), null);

    /** True for the instance that draws Level 2's lamps. */
    private final boolean level2;

    Level0LightRenderer(boolean level2) {
        super(Float.BYTES * 9);
        this.level2 = level2;
    }

    @Override
    protected MeshData createMesh() {
        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION);
        LightTypeRenderer.createInvertedCube(builder);
        return builder.buildOrThrow();
    }

    @Override
    protected void setupBufferState(VertexArrayBuilder builder) {
        builder.setVertexAttribute(1, VertexArray.INSTANCE_BUFFER, 3, VertexArrayBuilder.DataType.FLOAT, false, 0); // position
        builder.setVertexAttribute(2, VertexArray.INSTANCE_BUFFER, 3, VertexArrayBuilder.DataType.FLOAT, false, Float.BYTES * 3); // colour
        builder.setVertexAttribute(3, VertexArray.INSTANCE_BUFFER, 3, VertexArrayBuilder.DataType.FLOAT, false, Float.BYTES * 6); // reach, shadows
    }

    @Override
    protected @Nullable RenderType getRenderType(List<? extends LightRenderHandle<Level0LightData>> lights) {
        return VeilRenderType.get(level2 ? LEVEL2_RENDER_TYPE : RENDER_TYPE);
    }

    @Override
    protected @Nullable RenderType getInscatteringRenderType(List<? extends LightRenderHandle<Level0LightData>> lights) {
        return null; // no dusty beams for ceiling tubes
    }

    @Override
    public int getVisibleLights() {
        return super.getVisibleLights() + (!level2 && Level0Shading.wanted ? 1 : 0);
    }

    @Override
    public void renderLights(LightRenderer lightRenderer) {
        VeilRenderer renderer = VeilRenderSystem.renderer();
        if (renderer == null) {
            return;
        }
        Level0Shading.timerBegin();
        // Veil leaves the light buffer unbound after a light type with dusty beams: bind it again.
        AdvancedFbo lightFbo = renderer.getFramebufferManager().getFramebuffer(VeilFramebuffers.LIGHT);
        if (lightFbo != null) {
            lightFbo.bind(true);
        }
        RenderType renderType = VeilRenderType.get(level2 ? LEVEL2_RENDER_TYPE : RENDER_TYPE);
        if (renderType != null) {
            ResourceLocation shaderId = VeilRenderType.getShards(renderType).veilShaderId();
            ShaderProgram lamps = shaderId == null ? null : renderer.getShaderManager().getShader(shaderId);
            if (lamps != null) {
                setWallGrid(lamps);
                if (level2) {
                    setGloss(lamps);
                }
            }
        }
        super.renderLights(lightRenderer);
        Level0Shading.timerSplit();
        if (level2) {
            Level2Shading.markDrawn(super.getVisibleLights());
        } else if (Level0Shading.wanted && lightFbo != null) {
            copyLightBuffer(lightFbo);
            drawScreenPass(renderer);
            Level0Shading.markDrawn(super.getVisibleLights());
        }
        Level0Shading.timerEnd();
    }

    private static void setWallGrid(ShaderProgram program) {
        program.setTexture("WallGrid", GL11.GL_TEXTURE_2D, Level0WallGrid.texture());
        program.getUniformSafe("WallGridInfo").setVector(Level0WallGrid.originX(), Level0WallGrid.baseY(), Level0WallGrid.originZ());
    }

    /** Level 2: the gloss map and the highlight strengths (Level2Shading). */
    private static void setGloss(ShaderProgram program) {
        program.setTexture("GlossGrid", GL11.GL_TEXTURE_2D, Level0WallGrid.glossTexture());
        program.getUniformSafe("GlossDampWet").setVector(Level2Shading.DAMP_STRENGTH, Level2Shading.DAMP_SHARPNESS,
                Level2Shading.WET_STRENGTH, Level2Shading.WET_SHARPNESS);
        program.getUniformSafe("GlossMetal").setVector(Level2Shading.METAL_STRENGTH, Level2Shading.METAL_SHARPNESS,
                Level2Shading.GLOSS_FALLOFF, Level2Shading.WET_HEAD_ON);
    }

    /** Our copy of the light buffer, which the screen pass reads while it rewrites the original. */
    private static int lightCopy;
    private static int copyWidth;
    private static int copyHeight;

    /** Copies the lamps drawn so far (the bound light buffer) into {@link #lightCopy}. */
    private static void copyLightBuffer(AdvancedFbo lightFbo) {
        int width = lightFbo.getWidth();
        int height = lightFbo.getHeight();
        if (lightCopy == 0) {
            lightCopy = GlStateManager._genTexture();
            GlStateManager._bindTexture(lightCopy);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        }
        GlStateManager._bindTexture(lightCopy);
        if (width != copyWidth || height != copyHeight) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGB16F, width, height, 0, GL11.GL_RGB, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
            copyWidth = width;
            copyHeight = height;
        }
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
    }

    /** The finishing pass over the whole screen (corners, power, highlight shoulder, dark tint; see level0_screen.fsh). */
    private static void drawScreenPass(VeilRenderer renderer) {
        ShaderProgram program = renderer.getShaderManager().getShader(SCREEN_SHADER);
        if (program == null || Level0PowerMap.texture() == 0) {
            return;
        }
        program.bind();
        program.setDefaultUniforms(VertexFormat.Mode.TRIANGLE_STRIP);
        setWallGrid(program);
        program.setTexture("PowerMap", GL11.GL_TEXTURE_2D, Level0PowerMap.texture());
        program.setTexture("LightSampler", GL11.GL_TEXTURE_2D, lightCopy);
        program.getUniformSafe("PowerMapRect").setVector(Level0PowerMap.originX(), Level0PowerMap.originZ(),
                Level0PowerMap.EXTENT, Level0PowerMap.far());
        program.getUniformSafe("Level0Shade").setVector(Level0Shading.FILL_AO, Level0Shading.LIGHT_AO,
                Level0Shading.AO_FADE, Level0Shading.PANEL_POWER_FROM);
        program.getUniformSafe("LightmapLift").setFloat(ClientLighting.lightmapLift());
        program.getUniformSafe("Grade").setVector(AtmosphereGrade.lastExposure(), AtmosphereGrade.lastContrast());
        program.getUniformSafe("GradeShadowTint").setVector(AtmosphereGrade.lastShadowR(), AtmosphereGrade.lastShadowG(),
                AtmosphereGrade.lastShadowB());
        program.getUniformSafe("Level0Tone").setVector(Level0Shading.SHOULDER_KNEE, Level0Shading.SHOULDER_PEAK,
                Level0Shading.DARK_TINT, Level0Shading.DARK_BELOW);
        program.getUniformSafe("Level0Irradiance").setVector(Level0Shading.LAMP_KNEE, Level0Shading.LAMP_CAP);
        program.bindSamplers(0);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        // The pass writes the final light buffer (no blending: it replaces what was there).
        RenderSystem.disableBlend();
        VeilRenderSystem.drawScreenQuad();
        ShaderProgram.unbind();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }
}

/** A Level 0 lamp: a Veil point light drawn by {@link Level0LightRenderer} (our shaders, our shadows). */
class Level0LightData extends PointLightData {
    @Override
    public LightTypeRegistry.LightType<?> getType() {
        return Level0LightRenderer.TYPE;
    }
}

/** A Level 2 lamp: the same data, drawn by Level 2's instance of {@link Level0LightRenderer} (shadows and gloss). */
final class Level2LightData extends Level0LightData {
    @Override
    public LightTypeRegistry.LightType<?> getType() {
        return Level0LightRenderer.LEVEL2_TYPE;
    }
}
