package com.rickr.backrooms.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.layout.Level227Tree;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * How the air of Level 227 ("Megaflora") looks: the dimension effects {@code backrooms:megaflora}
 * (the level's dimension type points at it), the fog and the colours the far renderer
 * ({@link Level227Far}) paints the sky, the void and the distant tree with.
 *
 * <p><b>The mist.</b> The forest photo is thick grey-green mist: trees 40 blocks away are pale
 * ghosts. But the level also needs the trunk and the other branches, thousands of blocks away, to
 * loom through it. Plain fog can't do both (anything past the fog's end is flat fog colour), so
 * the fog here never closes completely: its colour has an alpha below 1 ({@link #mistAlpha}),
 * which vanilla's fog shader uses as "how much fog at most". Near you the mist thickens fast, and
 * then everything further away keeps a faint silhouette. The far renderer uses exactly the same
 * curve (it reads the same fog numbers), so where the real blocks end and the far picture begins
 * the mist matches, and beyond that it adds the open air's own slow haze.
 *
 * <p><b>Where you are changes it.</b> The mist lives in the forest: on a branch top, under the
 * canopy, it is thick ({@link #mistiness} 1). At an edge, looking out over the void, or falling
 * through it, it thins, and the black void below shows. The colour is darker on the low branches
 * (shaded by everything above) and paler high up; near a sunbeam it bleaches toward white.
 *
 * <p><b>Light</b> is Atmos' lightmap ({@link Level227Lighting#adjustLightmap}).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level227Atmosphere extends DimensionSpecialEffects {
    // --- Mist colours (r, g, b) ------------------------------------------------------------------
    /** The mist on the lowest branches (y ~80): shaded by the whole tree above. */
    private static final float[] MIST_LOW_BRANCH = {0.40f, 0.46f, 0.42f};
    /** The mist on the highest branches (y ~720): paler, nearer the light. */
    private static final float[] MIST_HIGH_BRANCH = {0.64f, 0.70f, 0.66f};
    /** Inside the forest the mist is a little darker than out at the edge. */
    private static final float FOREST_DARKEN = 0.9f;
    /** Near a sunbeam the mist bleaches toward this. */
    private static final float[] GLARE_MIST = {0.95f, 0.96f, 0.92f};
    private static final float GLARE_MIX = 0.45f;
    /**
     * High sunlit haze among the roots and the bright sky overhead (slightly luminous, the Sun's
     * glow through the roots), and the black void below (#040605), which the mist far under your
     * branch darkens into too.
     */
    private static final float[] HIGH_HAZE = {0.88f, 0.91f, 0.86f};
    private static final float[] ZENITH = {0.97f, 0.98f, 0.94f};
    private static final float[] VOID = {0.016f, 0.024f, 0.020f};

    // --- Mist thickness --------------------------------------------------------------------------
    /** How much mist there is at most (fog colour alpha): deep in the forest, and out in open air. */
    private static final float FOREST_ALPHA = 0.70f;
    private static final float OPEN_ALPHA = 0.36f;
    /** Distance (blocks) by which the mist has reached that amount: forest, open air. */
    private static final float FOREST_END = 96.0f;
    private static final float OPEN_END = 260.0f;
    /** The forest mist starts a little behind you, so it is already there at arm's length. */
    private static final float FOREST_START = -8.0f;
    /** How fast the mist follows you between forest and open air (per tick). */
    private static final float EASE = 0.03f;
    /** Forest test: sample points this far round the camera, and the forest layer above a branch top. */
    private static final double SAMPLE_RADIUS = 24.0;
    private static final double FOREST_BELOW = 4.0;
    private static final double FOREST_ABOVE = 75.0;

    private static float mistiness = 1.0f;
    private static float fogR = MIST_LOW_BRANCH[0];
    private static float fogG = MIST_LOW_BRANCH[1];
    private static float fogB = MIST_LOW_BRANCH[2];
    private static float alpha = FOREST_ALPHA;

    public Level227Atmosphere() {
        // No clouds (NaN cloud height), has ground, a sky drawn by renderSky / the far renderer.
        super(Float.NaN, true, SkyType.NORMAL, false, false);
    }

    /** Mod bus: Level 227's dimension type uses {@code "effects": "backrooms:megaflora"}. */
    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Backrooms.id("megaflora"), new Level227Atmosphere());
    }

    /** True when the client is in Level 227. */
    public static boolean inLevel227() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_227;
    }

    // --- Colours of the moment (the far renderer paints with these) ------------------------------

    public static float fogR() {
        return fogR;
    }

    public static float fogG() {
        return fogG;
    }

    public static float fogB() {
        return fogB;
    }

    /** How much mist there is at most (0..1): the fog colour's alpha. */
    public static float mistAlpha() {
        return alpha;
    }

    /** 0..1: how deep in the forest's mist the camera is (1 on a branch top, 0 out over the void). */
    public static float mistiness() {
        return mistiness;
    }

    static float[] highHaze() {
        return HIGH_HAZE;
    }

    static float[] zenith() {
        return ZENITH;
    }

    static float[] voidColor() {
        return VOID;
    }

    // ==========================================================================================
    // Dimension effects
    // ==========================================================================================

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor; // the colour comes from onFogColor below
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    @Nullable
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return null;
    }

    /**
     * The far renderer paints the whole sky, void and sun itself, later in the frame (it has to
     * know where the terrain is). Only if its shaders failed to load do we draw a plain gradient
     * here, so the level never shows garbage.
     */
    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        if (!Level227Far.available()) {
            drawFallbackSky(modelViewMatrix);
            setupFog.run();
        }
        return true;
    }

    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker,
                                     float skyLight, int pixelX, int pixelY, Vector3f colors) {
        Level227Lighting.adjustLightmap(level, partialTicks, pixelX, pixelY, colors);
    }

    /** A simple dome: bright overhead, the mist at the horizon, black void below. */
    private static void drawFallbackSky(Matrix4f view) {
        FogRenderer.setupNoFog();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        float[] rings = {90.0f, 50.0f, 25.0f, 10.0f, 0.0f, -8.0f, -25.0f, -90.0f};
        BufferBuilder band = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int segments = 24;
        for (int r = 0; r < rings.length - 1; r++) {
            for (int i = 0; i < segments; i++) {
                float a0 = i * Mth.TWO_PI / segments;
                float a1 = (i + 1) * Mth.TWO_PI / segments;
                skyVertex(band, view, a0, rings[r]);
                skyVertex(band, view, a1, rings[r]);
                skyVertex(band, view, a1, rings[r + 1]);
                skyVertex(band, view, a0, rings[r + 1]);
            }
        }
        BufferUploader.drawWithShader(band.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    private static void skyVertex(BufferBuilder buffer, Matrix4f view, float azimuth, float elevationDeg) {
        float e = elevationDeg * Mth.DEG_TO_RAD;
        float ring = Mth.cos(e) * 100.0f;
        float r;
        float g;
        float b;
        if (elevationDeg >= 0.0f) {
            float t = Mth.sin(e);
            r = Mth.lerp(t, fogR, ZENITH[0]);
            g = Mth.lerp(t, fogG, ZENITH[1]);
            b = Mth.lerp(t, fogB, ZENITH[2]);
        } else {
            float t = Mth.clamp(-elevationDeg / 30.0f, 0.0f, 1.0f);
            r = Mth.lerp(t, fogR, VOID[0]);
            g = Mth.lerp(t, fogG, VOID[1]);
            b = Mth.lerp(t, fogB, VOID[2]);
        }
        buffer.addVertex(view, Mth.cos(azimuth) * ring, Mth.sin(e) * 100.0f, Mth.sin(azimuth) * ring).setColor(r, g, b, 1.0f);
    }

    // ==========================================================================================
    // Fog
    // ==========================================================================================

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        // Vanilla keeps water / lava / powder snow fog, the sky pass and blindness/darkness.
        if (event.getType() != FogType.NONE || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN
                || !inLevel227() || blinded(event.getCamera())) {
            return;
        }
        event.setNearPlaneDistance(Mth.lerp(mistiness, 0.0f, FOREST_START));
        float end = Mth.lerp(mistiness, OPEN_END, FOREST_END);
        event.setFarPlaneDistance(Level227Far.available() ? end : Math.min(end, event.getFarPlaneDistance()));
        // Round, like the far renderer's own fade, which measures straight-line distance.
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true); // required for NeoForge to use our distances
        // The alpha caps the mist: everything far keeps a faint silhouette. The fog colour was
        // set (with alpha 1) just before this, by FogRenderer.levelFogColor(). Without the far
        // renderer (shaders failed) the mist must close completely, or the world's edge would show.
        RenderSystem.setShaderFogColor(fogR, fogG, fogB, Level227Far.available() ? alpha : 1.0f);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inLevel227() || event.getCamera().getFluidInCamera() != FogType.NONE || blinded(event.getCamera())) {
            return;
        }
        event.setRed(fogR);
        event.setGreen(fogG);
        event.setBlue(fogB);
    }

    private static boolean blinded(Camera camera) {
        return camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    /** Follows the camera between the forest's mist and the open air, and recolours the mist. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused() || !inLevel227()) {
            return;
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        // How many of 9 columns round the camera have a branch top just below it (the forest layer).
        int forest = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int top = Level227Tree.topSurfaceY(eye.x + i * SAMPLE_RADIUS, eye.z + j * SAMPLE_RADIUS);
                if (top != Integer.MIN_VALUE && eye.y > top - FOREST_BELOW && eye.y < top + FOREST_ABOVE) {
                    forest++;
                }
            }
        }
        mistiness += (forest / 9.0f - mistiness) * EASE;
        alpha = Mth.lerp(mistiness, OPEN_ALPHA, FOREST_ALPHA);

        float height = Mth.clamp((float) ((eye.y - 80.0) / 640.0), 0.0f, 1.0f);
        float shade = Mth.lerp(mistiness, 1.0f, FOREST_DARKEN);
        // Falling below the branches, the light dies: the mist darkens toward the void.
        float voidDark = 0.15f + 0.85f * Mth.clamp((float) ((eye.y + 200.0) / 280.0), 0.0f, 1.0f);
        float glare = Level227Lighting.sunGlare() * GLARE_MIX;
        fogR = Mth.lerp(glare, Mth.lerp(height, MIST_LOW_BRANCH[0], MIST_HIGH_BRANCH[0]) * shade * voidDark, GLARE_MIST[0]);
        fogG = Mth.lerp(glare, Mth.lerp(height, MIST_LOW_BRANCH[1], MIST_HIGH_BRANCH[1]) * shade * voidDark, GLARE_MIST[1]);
        fogB = Mth.lerp(glare, Mth.lerp(height, MIST_LOW_BRANCH[2], MIST_HIGH_BRANCH[2]) * shade * voidDark, GLARE_MIST[2]);
    }
}
