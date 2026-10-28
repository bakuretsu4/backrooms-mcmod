package com.rickr.backrooms.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.light.BackroomsLights;
import com.rickr.backrooms.world.layout.Level227Tree;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

/**
 * The sunbeams of Level 227, drawn in the loaded world: blinding white shafts of the Sun over the
 * Void's light falling straight down through gaps in the canopy onto bleached ash. (Beyond the
 * loaded terrain the far renderer, client/Level227Far*, draws their far silhouettes.)
 *
 * <p>A shaft is a vertical cylinder of lit air. It is drawn the cheap way that still looks
 * volumetric: a ribbon that always turns to face you, as wide as the shaft, whose brightness
 * across it follows how much air your eye looks through at that point (the chord of a circle,
 * brightest down the middle, fading to nothing at the edges), with a narrower, brighter core
 * inside it. Everything is added on top of the picture (additive blending), so it glows instead
 * of covering things, and it is depth-tested, so trunks and leaves in front of a beam hide it.
 * On top of that:
 * <ul>
 *   <li><b>Heat shimmer:</b> the ribbons waver a little sideways, faster near the ground.</li>
 *   <li><b>Motes:</b> specks of dust drifting up through the light and twinkling.</li>
 *   <li><b>Bleached ground:</b> a soft white glow on the ash where the beam lands.</li>
 *   <li>With Veil, a strong white light at the foot of the nearest beams, so the forest around a
 *       beam is lit by it ({@link Level227BeamLights}).</li>
 * </ul>
 * The rising hum is played by {@link Level227Audio}; the white-out when you stand in a beam is the
 * grade ({@link Level227Lighting#mood}).
 *
 * <p>Cost: a handful of beams are ever in range (24 in the whole tree), each a few hundred quads
 * built per frame: far under a tenth of a millisecond.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level227Sunbeams {
    /** Beams further than this (sideways) are left to the far renderer. */
    private static final double RANGE = 320.0;
    /** Columns across the ribbon and the band height (blocks) of its pieces. */
    private static final int COLUMNS = 10;
    private static final float BAND = 12.0f;
    /** The shaft's light: white with the faintest warmth. */
    private static final float R = 1.0f;
    private static final float G = 0.99f;
    private static final float B = 0.94f;
    /** Brightness (alpha, added) down the middle of the outer shaft and of the core. */
    private static final float SHAFT_ALPHA = 0.30f;
    private static final float CORE_ALPHA = 0.45f;
    /** The core is this share of the shaft's width. */
    private static final float CORE_WIDTH = 0.55f;
    /** The shaft is brightest near the ground and thins out over this many blocks upward. */
    private static final float GLOW_HEIGHT = 140.0f;
    /** ... and dissolves into the sky over its last this-many blocks (no hard top end). */
    private static final float TOP_FADE = 200.0f;
    /**
     * Beyond {@value #SPREAD_FROM} blocks the shaft widens by 1x per {@value #SPREAD_SCALE} blocks
     * (and dims as much): a soft glow far off instead of a thin white needle (4x wide at 310 blocks).
     */
    private static final double SPREAD_FROM = 40.0;
    private static final float SPREAD_SCALE = 90.0f;
    /** The bright core fades out between 30 and 180 blocks away. */
    private static final double CORE_FADE_FROM = 30.0;
    private static final float CORE_FADE_LENGTH = 150.0f;
    /** Motes per beam, the height they drift through, and their size. */
    private static final int MOTES = 90;
    private static final float MOTE_HEIGHT = 36.0f;
    private static final float MOTE_SIZE = 0.07f;

    private Level227Sunbeams() {}

    // Per-frame state for the beam callback (no allocation per beam).
    private static Matrix4f view;
    private static double camX;
    private static double camY;
    private static double camZ;
    private static float time;
    private static float fogStart;
    private static float fogEnd;
    private static BufferBuilder buffer;

    /** Each tick: the Veil lights at the foot of the nearest beams (only while Veil lights the scene). */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean here = mc.level != null && mc.player != null && Level227Lighting.inLevel227();
        if (here && BackroomsLights.isVeilLightingActive()) {
            Level227BeamLights.tick(mc.gameRenderer.getMainCamera().getPosition());
        } else if (Level227BeamLights.active()) {
            Level227BeamLights.freeAll();
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !Level227Lighting.inLevel227()) {
            return;
        }
        Vec3 eye = event.getCamera().getPosition();
        view = event.getModelViewMatrix();
        camX = eye.x;
        camY = eye.y;
        camZ = eye.z;
        time = (float) ((System.currentTimeMillis() % 3_600_000L) / 1000.0);
        fogStart = RenderSystem.getShaderFogStart();
        fogEnd = RenderSystem.getShaderFogEnd();

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Level227Tree.forEachBeamNear(camX, camZ, RANGE, Level227Sunbeams::drawBeam);
        MeshData mesh = buffer.build(); // null when no beam was near
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        buffer = null;

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    /** One beam: the shaft ribbon, its core, the shimmer, the motes and the glow on the ground. */
    private static void drawBeam(Level227Tree.Beam beam) {
        double dx = camX - beam.x();
        double dz = camZ - beam.z();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float radius = (float) beam.radius();
        int ground = Level227Tree.topSurfaceY(beam.x(), beam.z());
        float bottom = ground == Integer.MIN_VALUE ? (float) camY - 200.0f : ground - 1.0f;
        float top = (float) beam.topY();
        // Fog: beyond the fog the far renderer takes over; light pierces fog, so never fully gone.
        float fog = fogEnd > fogStart ? 1.0f - 0.8f * Mth.clamp((float) ((dist - fogStart) / (fogEnd - fogStart)), 0.0f, 1.0f) : 1.0f;
        // Inside the shaft the ribbon would cut through your face: fade it out as you step in
        // (the grade's white-out takes over there).
        float inside = Mth.clamp((float) ((dist - radius * 0.6) / (radius * 0.6)), 0.0f, 1.0f);
        float strength = fog * inside;
        if (strength <= 0.01f) {
            drawGround(beam, bottom, radius, fog);
            return;
        }
        // The ribbon's sideways axis: perpendicular to the line from the beam to the camera.
        double len = Math.max(dist, 1e-3);
        float px = (float) (-dz / len);
        float pz = (float) (dx / len);
        // Far off, a 4-7 block shaft would shrink to a hard white needle: let it spread into a wider,
        // fainter glow instead (same light spread over more air), and drop the bright core.
        float spread = 1.0f + (float) Math.max(0.0, dist - SPREAD_FROM) / SPREAD_SCALE;
        float core = Mth.clamp(1.0f - (float) (dist - CORE_FADE_FROM) / CORE_FADE_LENGTH, 0.0f, 1.0f);
        ribbon(beam, px, pz, radius * spread, bottom, top, SHAFT_ALPHA * strength / spread, 0.0f, 2.0f);
        if (core > 0.0f) {
            ribbon(beam, px, pz, radius * CORE_WIDTH, bottom, top, CORE_ALPHA * strength * core, 1.7f, 1.0f);
        }
        motes(beam, radius, bottom, strength);
        drawGround(beam, bottom, radius, fog);
    }

    /**
     * A camera-facing ribbon {@code halfWidth} wide each side of the beam's axis, from bottom to top.
     * Across it the brightness is the chord of the circle (sqrt(1 - u^2)) raised to
     * {@code softness} (higher = softer, wider-looking edges); up it the light is strongest low
     * down and thins out with height. {@code phase} offsets the shimmer.
     */
    private static void ribbon(Level227Tree.Beam beam, float px, float pz, float halfWidth, float bottom, float top,
                               float alpha, float phase, float softness) {
        float cx = (float) (beam.x() - camX);
        float cz = (float) (beam.z() - camZ);
        // Only the part of the shaft anywhere near the camera's height needs fine bands; far up and
        // far down the bands get longer (they are thin there anyway).
        for (float y0 = bottom; y0 < top; ) {
            float step = BAND * (1.0f + Math.abs(y0 - (float) camY) / 60.0f);
            float y1 = Math.min(top, y0 + step);
            float a0 = alpha * heightFade(y0, bottom, top);
            float a1 = alpha * heightFade(y1, bottom, top);
            float w0 = shimmer(y0, phase);
            float w1 = shimmer(y1, phase);
            for (int c = 0; c < COLUMNS; c++) {
                float u0 = -1.0f + 2.0f * c / COLUMNS;
                float u1 = -1.0f + 2.0f * (c + 1) / COLUMNS;
                float k0 = (float) Math.pow(chord(u0), softness);
                float k1 = (float) Math.pow(chord(u1), softness);
                vertex(cx + px * (u0 * halfWidth + w0), y0, cz + pz * (u0 * halfWidth + w0), a0 * k0);
                vertex(cx + px * (u1 * halfWidth + w0), y0, cz + pz * (u1 * halfWidth + w0), a0 * k1);
                vertex(cx + px * (u1 * halfWidth + w1), y1, cz + pz * (u1 * halfWidth + w1), a1 * k1);
                vertex(cx + px * (u0 * halfWidth + w1), y1, cz + pz * (u0 * halfWidth + w1), a1 * k0);
            }
            y0 = y1;
        }
    }

    /** How much air the eye looks through at position u (-1..1) across a cylinder. */
    private static float chord(float u) {
        return (float) Math.sqrt(Math.max(0.0f, 1.0f - u * u));
    }

    /**
     * Brightest near the ground, thinning out with height, and dissolving into the sky over the
     * last {@value #TOP_FADE} blocks instead of ending in a hard line.
     */
    private static float heightFade(float y, float bottom, float top) {
        float low = 0.15f + 0.85f * (float) Math.exp(-(y - bottom) / GLOW_HEIGHT);
        return low * Mth.clamp((top - y) / TOP_FADE, 0.0f, 1.0f);
    }

    /** Heat shimmer: a small sideways waver, quicker and stronger near the hot ground. */
    private static float shimmer(float y, float phase) {
        return 0.12f * Mth.sin(y * 0.21f + time * 2.7f + phase) + 0.06f * Mth.sin(y * 0.53f - time * 4.1f + phase * 2.0f);
    }

    /** Dust motes drifting slowly up through the lower part of the beam, twinkling. */
    private static void motes(Level227Tree.Beam beam, float radius, float bottom, float strength) {
        float cx = (float) (beam.x() - camX);
        float cz = (float) (beam.z() - camZ);
        int seed = (int) (beam.x() * 31 + beam.z() * 17);
        for (int i = 0; i < MOTES; i++) {
            // Fixed place in the beam per mote (golden-angle spiral), rising and wrapping round.
            float a = (i * 2.39996f) + seed;
            float r = radius * 0.92f * (float) Math.sqrt((i + 0.5f) / MOTES);
            float rise = ((i * 7.31f + time * (0.35f + (i % 5) * 0.06f)) % MOTE_HEIGHT);
            float mx = cx + Mth.cos(a + time * 0.05f) * r + 0.3f * Mth.sin(time * 0.7f + i);
            float mz = cz + Mth.sin(a + time * 0.05f) * r + 0.3f * Mth.cos(time * 0.6f + i * 1.3f);
            float my = bottom + 0.5f + rise;
            float twinkle = 0.5f + 0.5f * Mth.sin(time * (2.0f + (i % 7) * 0.4f) + i * 1.7f);
            float fade = Mth.sin(Mth.PI * rise / MOTE_HEIGHT); // appear low, vanish high
            float alpha = 0.9f * strength * twinkle * fade;
            if (alpha < 0.03f) {
                continue;
            }
            billboard(mx, my, mz, MOTE_SIZE, alpha);
        }
    }

    /** A tiny square facing the camera (sideways and up), centred at (x, y, z) camera-relative. */
    private static void billboard(float x, float y, float z, float size, float alpha) {
        float len = Mth.sqrt(x * x + z * z);
        float sx = len > 1e-3f ? -z / len * size : size;
        float sz = len > 1e-3f ? x / len * size : 0.0f;
        vertex(x - sx, y - size, z - sz, alpha);
        vertex(x + sx, y - size, z + sz, alpha);
        vertex(x + sx, y + size, z + sz, alpha);
        vertex(x - sx, y + size, z - sz, alpha);
    }

    /** The bleached ash circle glowing where the light lands (a flat disc, soft at its rim). */
    private static void drawGround(Level227Tree.Beam beam, float bottom, float radius, float fog) {
        float cx = (float) (beam.x() - camX);
        float cz = (float) (beam.z() - camZ);
        float y = bottom + 1.06f;
        int segments = 24;
        float rim = radius * 1.25f;
        for (int s = 0; s < segments; s++) {
            float a0 = Mth.TWO_PI * s / segments;
            float a1 = Mth.TWO_PI * (s + 1) / segments;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            // Inner bright disc (a quad from the centre, degenerate at the middle) ...
            vertex(cx, y, cz, 0.30f * fog);
            vertex(cx, y, cz, 0.30f * fog);
            vertex(cx + c1 * radius, y, cz + s1 * radius, 0.22f * fog);
            vertex(cx + c0 * radius, y, cz + s0 * radius, 0.22f * fog);
            // ... and a ring fading out past the edge of the beam (light scattered off the ash).
            vertex(cx + c0 * radius, y, cz + s0 * radius, 0.22f * fog);
            vertex(cx + c1 * radius, y, cz + s1 * radius, 0.22f * fog);
            vertex(cx + c1 * rim, y, cz + s1 * rim, 0.0f);
            vertex(cx + c0 * rim, y, cz + s0 * rim, 0.0f);
        }
    }

    private static void vertex(float x, float y, float z, float alpha) {
        buffer.addVertex(view, x, (float) (y - camY), z).setColor(R, G, B, Mth.clamp(alpha, 0.0f, 1.0f));
    }
}
