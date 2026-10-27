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
import com.rickr.backrooms.world.layout.Level39Layout;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * The megalophobia moment: from anywhere in a town you see the four colossal facades of the
 * superstructure around you, ranks upon ranks of glowing windows looming out of the fog, even
 * though they stand hundreds of blocks away, far beyond the loaded chunks.
 *
 * <p><b>What is drawn:</b> each of the four inner facades of the current cell as a few big
 * textured quads (y 73-245), using a picture of the real facade: 6-block bays with a 3 x 5
 * window in a heavy dark frame, 7-block storeys separated by a dark spandrel and a grey string
 * course, sooty brick piers (darkening to near-black toward the top), the arched top storey and
 * the cornice ({@code tools/anomaly_assets/level39_audio.py} paints them to match
 * {@code Level39Layout} and the real blocks). Then the same quads again with a tight glow halo
 * round each window added on top (light), the stair towers as dark slabs breaking the ranks, a
 * faint glow rising off the top of each facade, and bright mist piled up at its foot.
 *
 * <p><b>Fog:</b> the real terrain fog would hide anything this far away, so these quads fade by
 * their own, much gentler curves: the brick and glass fade into the fog colour over ~340 blocks
 * (the wall stays a dark mass against the bright mist), the window glow over ~520 blocks. Far
 * facades read as glowing ranks of windows in the mist, near ones are crisp.
 *
 * <p><b>Two passes, never doubled over real blocks:</b>
 * <ol>
 *   <li><b>With the sky</b> ({@link #renderSky}): everything, with no depth test. All the terrain is
 *       drawn after the sky, so wherever real blocks exist (a house, the real facade near you)
 *       they simply cover it. The whole picture is shrunk toward the camera so it fits inside the
 *       camera's far plane: shrinking toward the eye doesn't change where anything appears.</li>
 *   <li><b>After the terrain</b> ({@link #onRenderStage}), depth-tested: only where the real facade
 *       is so far away that the terrain fog has turned it into flat grey. There the far facade
 *       fades in over the fogged blocks, so the windows keep glowing continuously from the real
 *       blocks out to the horizon. Near you (inside the fog's reach) this pass is invisible and
 *       the real blocks show. Its quads are pulled a hair toward the camera (0.5% of the distance)
 *       so they win the depth test against the real facade without moving on screen; houses
 *       and trees in front still hide them properly.</li>
 * </ol>
 * About a thousand vertices per pass, rebuilt each frame (their fade depends on where you
 * stand): cheap. Not drawn in the tunnels or outside a town.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level39FarFacade {
    private static final ResourceLocation BODY = Backrooms.id("textures/environment/l39_facade_far.png");
    private static final ResourceLocation TOP = Backrooms.id("textures/environment/l39_facade_far_top.png");
    private static final ResourceLocation BODY_GLOW = Backrooms.id("textures/environment/l39_facade_far_glow.png");
    private static final ResourceLocation TOP_GLOW = Backrooms.id("textures/environment/l39_facade_far_top_glow.png");
    /** The same light, blurred into the fog: what the windows look like far away. */
    private static final ResourceLocation BODY_GLOW_SOFT = Backrooms.id("textures/environment/l39_facade_far_glow_soft.png");
    private static final ResourceLocation TOP_GLOW_SOFT = Backrooms.id("textures/environment/l39_facade_far_top_glow_soft.png");
    private static final ResourceLocation[] TEXTURES = {BODY, TOP, BODY_GLOW, TOP_GLOW, BODY_GLOW_SOFT, TOP_GLOW_SOFT};
    /** GL ids the mipmaps were made for (a resource reload makes new textures). */
    private static final int[] PREPARED = {-1, -1, -1, -1, -1, -1};
    /** What a layer of the facade pictures is: brick and glass, the windows' crisp light, or their light diffused. */
    private static final int ALBEDO = 0;
    private static final int GLOW_SHARP = 1;
    private static final int GLOW_SOFT = 2;

    // --- Geometry (must match Level39Layout) -------------------------------------------------------
    /** The body pictures cover 16 bays across and 8 storeys up; the top picture 16 bays x 11 rows. */
    private static final float TILE_WIDTH = 16 * Level39Layout.BAY;
    private static final float TILE_HEIGHT = 8 * Level39Layout.STOREY;
    /** Storey 0 starts on the first block above the ground (its granite string course). */
    private static final int BODY_BOTTOM = Level39Layout.GROUND_Y + 1;
    /**
     * Top of storey 22: above it the arched top storey (23, y 234-240), the cornice (241-243) and
     * the parapet (244), so the top strip ends at y 245.
     */
    private static final int BODY_TOP = BODY_BOTTOM + 23 * Level39Layout.STOREY;
    private static final int TOP_TOP = BODY_TOP + 11;
    /** Rows of quads (4 storeys each) so the fade can follow the distance up the facade. */
    private static final int[] ROW_Y = {BODY_BOTTOM, BODY_BOTTOM + 28, BODY_BOTTOM + 56, BODY_BOTTOM + 84,
            BODY_BOTTOM + 112, BODY_BOTTOM + 140, BODY_TOP};
    /** Along the facade, one quad every this many blocks. */
    private static final float SEGMENT = 32.0f;
    /** The town side of the pier blocks, in cell-local coordinates (+ and - side). */
    private static final float PLANE_POS = Level39Layout.TOWN_HALF;
    private static final float PLANE_NEG = -(Level39Layout.TOWN_HALF - 1);
    private static final float ALONG_MIN = -(Level39Layout.TOWN_HALF - 1);
    private static final float ALONG_MAX = Level39Layout.TOWN_HALF;
    /** Stair towers: every 128 blocks along, 13 wide, standing 8 blocks out, body to y 252 + a low cap. */
    private static final int TOWER_SPACING = 128;
    private static final int TOWER_COUNT = 3; // j = -3..3
    /** Tower blocks reach this far either side of the centre column (13 blocks wide). */
    private static final float TOWER_HALF = 6.0f;
    private static final float TOWER_DEPTH = 8.0f;
    private static final float TOWER_TOP = 255.0f;

    // --- Look ---------------------------------------------------------------------------------------
    /**
     * Brick and glass fade into the fog over this many blocks (e-folding distance): slowly enough
     * that even across the town the facade stays a dark masonry mass against the bright mist ...
     */
    private static final float ALBEDO_FOG = 340.0f;
    /** ... the window glow over this many: light carries further through mist than shapes do. */
    private static final float GLOW_FOG = 520.0f;
    private static final float GLOW_GAIN = 0.9f;
    /**
     * The brick darkens with height (soot), like the real blocks (brick, then sooty, then black
     * from about 55% of the height): brightness multiplier at the bottom and the top, and the
     * heights it runs between.
     */
    private static final float SOOT_LOW = 1.0f;
    private static final float SOOT_HIGH = 0.45f;
    private static final float SOOT_FROM_Y = 115.0f;
    private static final float SOOT_TO_Y = 168.0f;
    /** The glow rising off the facade tops into the mist: height and strength. */
    private static final float SKIRT_HEIGHT = 22.0f;
    private static final float SKIRT_GAIN = 0.35f;
    /**
     * Light mist piled up at the foot of each facade (the fog is brightest at the base, as in the
     * photo): height and strength at the ground.
     */
    private static final float MIST_HEIGHT = 60.0f;
    private static final float MIST_GAIN = 0.32f;
    /**
     * Light diffusing in the fog: up to DIFFUSE_FROM blocks the windows are crisp; by DIFFUSE_TO a
     * soft halo of light has bloomed round them (the photo's windows bleed into the mist). The
     * crisp windows never vanish completely: CORE_KEEP of them stays under the halo, so each
     * window still reads as a distinct bright window at 150-400 blocks.
     */
    private static final float DIFFUSE_FROM = 140.0f;
    private static final float DIFFUSE_TO = 460.0f;
    private static final float CORE_KEEP = 0.6f;
    /** Strength of the diffused halo compared with the crisp windows. */
    private static final float SOFT_GAIN = 0.75f;
    /** The upper storeys fade into the haze under the clouds: from this height, fully by that one, this much. */
    private static final float HAZE_FROM_Y = 150.0f;
    private static final float HAZE_TO_Y = 250.0f;
    private static final float HAZE_STRENGTH = 0.65f;
    /** Irregularity of the windows' light over the wall: brightness +-, and warm/cool shift. */
    private static final float IRREGULAR = 0.18f;
    private static final float TINT = 0.06f;
    /** The pictures are 16 bays wide. */
    private static final int BAYS_PER_TILE = 16;
    /** Stair tower brick (dark) and the faint glow of their stair windows. */
    private static final float TOWER_R = 0.05f;
    private static final float TOWER_G = 0.048f;
    private static final float TOWER_B = 0.046f;
    private static final float TOWER_SLIT_GAIN = 0.3f;
    /** The rain dims everything a little. */
    private static final float RAIN_DIM = 0.3f;
    /** Pass 2 fades in between these shares of the terrain fog's far distance. */
    private static final float NEAR_FADE_FROM = 0.55f;
    private static final float NEAR_FADE_TO = 1.0f;
    /** Pass 2: pull every vertex this share toward the camera (wins the depth test, same screen spot). */
    private static final float PASS2_PULL = 0.995f;
    /** The furthest any facade point can be from a camera inside the town (blocks). */
    private static final float MAX_REACH = 1400.0f;

    /** Draw state for one frame (set by draw(), read by the vertex helpers). */
    private static double camX;
    private static double camY;
    private static double camZ;
    private static float scale;
    private static float fadeFrom;
    private static float fadeTo;
    private static float dim;

    /** True when there is a town around the camera to draw the facades of. */
    private static boolean visible(Vec3 eye) {
        return Level39ClientState.inLevel39() && !Level39ClientState.inTunnels(eye)
                && Level39Layout.inTown(Mth.floor(eye.x), Mth.floor(eye.z));
    }

    /** Pass 1, from the sky: everything, behind all terrain. */
    static void renderSky(Matrix4f view, Camera camera) {
        Vec3 eye = camera.getPosition();
        if (!visible(eye)) {
            return;
        }
        float far = Minecraft.getInstance().gameRenderer.getDepthFar();
        draw(view, eye, Math.min(1.0f, 0.9f * far / MAX_REACH), 0.0f, 0.0f);
    }

    /** Pass 2, after the terrain: where the real facade has faded into the fog. */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Vec3 eye = event.getCamera().getPosition();
        if (!visible(eye)) {
            return;
        }
        float fogEnd = Level39Atmosphere.fogEnd();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        draw(event.getModelViewMatrix(), eye, PASS2_PULL, fogEnd * NEAR_FADE_FROM, fogEnd * NEAR_FADE_TO);
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    /**
     * Draws the four facades. {@code scale} shrinks everything toward the camera; {@code from}..
     * {@code to} is the near fade (0, 0 = no near fade).
     */
    private static void draw(Matrix4f view, Vec3 eye, float drawScale, float from, float to) {
        camX = eye.x;
        camY = eye.y;
        camZ = eye.z;
        scale = drawScale;
        fadeFrom = from;
        fadeTo = to;
        dim = 1.0f - RAIN_DIM * Level39Weather.rain();
        int cellX = Mth.floor(eye.x) - Level39Layout.local(Mth.floor(eye.x));
        int cellZ = Mth.floor(eye.z) - Level39Layout.local(Mth.floor(eye.z));
        prepareTextures();

        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        // Brick and glass over the fog (normal blending).
        RenderSystem.defaultBlendFunc();
        drawFaces(view, cellX, cellZ, false, ALBEDO, BODY);
        drawFaces(view, cellX, cellZ, true, ALBEDO, TOP);
        // The windows' light on top (adds light): crisp nearby, blooming into the fog far away.
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        drawFaces(view, cellX, cellZ, false, GLOW_SHARP, BODY_GLOW);
        drawFaces(view, cellX, cellZ, true, GLOW_SHARP, TOP_GLOW);
        drawFaces(view, cellX, cellZ, false, GLOW_SOFT, BODY_GLOW_SOFT);
        drawFaces(view, cellX, cellZ, true, GLOW_SOFT, TOP_GLOW_SOFT);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        drawSkirts(view, cellX, cellZ);
        drawBaseMist(view, cellX, cellZ);
        RenderSystem.defaultBlendFunc();
        drawTowers(view, cellX, cellZ);

        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    /** One picture (body or top strip; brick, crisp light or soft light) on all four facades. */
    private static void drawFaces(Matrix4f view, int cellX, int cellZ, boolean top, int layer, ResourceLocation texture) {
        BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int rows = top ? 1 : ROW_Y.length - 1;
        for (int face = 0; face < 4; face++) {
            float plane = (face & 1) == 0 ? PLANE_POS : PLANE_NEG;
            for (int r = 0; r < rows; r++) {
                // Each band of storeys (and each face) starts the picture a different whole number
                // of bays along, so its pattern of dim and dark windows doesn't repeat visibly.
                float shift = bayShift(face, top ? 99 : r) / (float) BAYS_PER_TILE;
                for (float a0 = ALONG_MIN; a0 < ALONG_MAX; a0 += SEGMENT) {
                    float a1 = Math.min(ALONG_MAX, a0 + SEGMENT);
                    float y0 = top ? BODY_TOP : ROW_Y[r];
                    float y1 = top ? TOP_TOP : ROW_Y[r + 1];
                    float v0 = top ? 1.0f : (BODY_TOP - y0) / TILE_HEIGHT;
                    float v1 = top ? 0.0f : (BODY_TOP - y1) / TILE_HEIGHT;
                    float u0 = a0 / TILE_WIDTH + shift;
                    float u1 = a1 / TILE_WIDTH + shift;
                    texVertex(quads, view, face, cellX, cellZ, plane, a0, y0, u0, v0, layer);
                    texVertex(quads, view, face, cellX, cellZ, plane, a1, y0, u1, v0, layer);
                    texVertex(quads, view, face, cellX, cellZ, plane, a1, y1, u1, v1, layer);
                    texVertex(quads, view, face, cellX, cellZ, plane, a0, y1, u0, v1, layer);
                }
            }
        }
        MeshData mesh = quads.build();
        if (mesh != null) {
            RenderSystem.setShaderTexture(0, texture);
            BufferUploader.drawWithShader(mesh);
        }
    }

    /** A fixed pseudo-random whole number of bays (0..15) for one face and band of storeys. */
    private static int bayShift(int face, int band) {
        return (int) (hash(face * 131 + band * 17 + 7) & (BAYS_PER_TILE - 1));
    }

    private static long hash(long v) {
        long h = v * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 29);
    }

    /** Smooth value noise in about -1..1 over a lattice of the given cell size (blocks). */
    private static float noise(double a, double b, double cell, int salt) {
        double x = a / cell;
        double y = b / cell;
        int ix = Mth.floor(x);
        int iy = Mth.floor(y);
        float fx = (float) (x - ix);
        float fy = (float) (y - iy);
        fx = fx * fx * (3.0f - 2.0f * fx);
        fy = fy * fy * (3.0f - 2.0f * fy);
        float v00 = lattice(ix, iy, salt);
        float v10 = lattice(ix + 1, iy, salt);
        float v01 = lattice(ix, iy + 1, salt);
        float v11 = lattice(ix + 1, iy + 1, salt);
        return Mth.lerp(fy, Mth.lerp(fx, v00, v10), Mth.lerp(fx, v01, v11));
    }

    private static float lattice(int x, int y, int salt) {
        long h = hash(((long) x * 73856093L) ^ ((long) y * 19349663L) ^ ((long) salt * 83492791L));
        return ((h >>> 40) & 0xFFFF) / 32767.5f - 1.0f;
    }

    /** The glow rising off the top of each facade into the mist: bright at the cornice, gone above. */
    private static void drawSkirts(Matrix4f view, int cellX, int cellZ) {
        BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int face = 0; face < 4; face++) {
            float plane = (face & 1) == 0 ? PLANE_POS : PLANE_NEG;
            for (float a0 = ALONG_MIN; a0 < ALONG_MAX; a0 += SEGMENT) {
                float a1 = Math.min(ALONG_MAX, a0 + SEGMENT);
                colorVertex(quads, view, face, cellX, cellZ, plane, a0, TOP_TOP, 0.85f, 0.9f, 1.0f, SKIRT_GAIN, true);
                colorVertex(quads, view, face, cellX, cellZ, plane, a1, TOP_TOP, 0.85f, 0.9f, 1.0f, SKIRT_GAIN, true);
                colorVertex(quads, view, face, cellX, cellZ, plane, a1, TOP_TOP + SKIRT_HEIGHT, 0.85f, 0.9f, 1.0f, 0.0f, true);
                colorVertex(quads, view, face, cellX, cellZ, plane, a0, TOP_TOP + SKIRT_HEIGHT, 0.85f, 0.9f, 1.0f, 0.0f, true);
            }
        }
        MeshData mesh = quads.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    /** Bright mist piled up at the foot of each facade, fading out upward. */
    private static void drawBaseMist(Matrix4f view, int cellX, int cellZ) {
        BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float bottom = BODY_BOTTOM - 1.0f;
        for (int face = 0; face < 4; face++) {
            // A little in front of the wall, so it lies over the wall's foot rather than in it.
            float plane = (face & 1) == 0 ? PLANE_POS - 3.0f : PLANE_NEG + 3.0f;
            for (float a0 = ALONG_MIN; a0 < ALONG_MAX; a0 += SEGMENT) {
                float a1 = Math.min(ALONG_MAX, a0 + SEGMENT);
                colorVertex(quads, view, face, cellX, cellZ, plane, a0, bottom, 0.78f, 0.80f, 0.84f, MIST_GAIN, true);
                colorVertex(quads, view, face, cellX, cellZ, plane, a1, bottom, 0.78f, 0.80f, 0.84f, MIST_GAIN, true);
                colorVertex(quads, view, face, cellX, cellZ, plane, a1, bottom + MIST_HEIGHT, 0.78f, 0.80f, 0.84f, 0.0f, true);
                colorVertex(quads, view, face, cellX, cellZ, plane, a0, bottom + MIST_HEIGHT, 0.78f, 0.80f, 0.84f, 0.0f, true);
            }
        }
        MeshData mesh = quads.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    /**
     * The stair towers: dark brick slabs standing out of the facade every 128 blocks (front and two
     * sides), with the faint glow of a column of stair windows down the middle.
     */
    private static void drawTowers(Matrix4f view, int cellX, int cellZ) {
        BufferBuilder slabs = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int face = 0; face < 4; face++) {
            boolean positive = (face & 1) == 0;
            float back = positive ? PLANE_POS : PLANE_NEG;
            float front = positive ? PLANE_POS - TOWER_DEPTH : PLANE_NEG + TOWER_DEPTH;
            for (int j = -TOWER_COUNT; j <= TOWER_COUNT; j++) {
                float c = j * TOWER_SPACING;
                float l = c - TOWER_HALF;
                float r = c + TOWER_HALF + 1.0f;
                float y0 = BODY_BOTTOM;
                // Front.
                towerQuad(slabs, view, face, cellX, cellZ, front, l, front, r, y0, TOWER_TOP);
                // The two sides, from the facade out to the front.
                towerQuad(slabs, view, face, cellX, cellZ, back, l, front, l, y0, TOWER_TOP);
                towerQuad(slabs, view, face, cellX, cellZ, back, r, front, r, y0, TOWER_TOP);
            }
        }
        MeshData mesh = slabs.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        // The stair windows' glow, added on top.
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder slits = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int face = 0; face < 4; face++) {
            boolean positive = (face & 1) == 0;
            float front = (positive ? PLANE_POS - TOWER_DEPTH : PLANE_NEG + TOWER_DEPTH) + (positive ? -0.05f : 0.05f);
            for (int j = -TOWER_COUNT; j <= TOWER_COUNT; j++) {
                float c = j * TOWER_SPACING + 0.5f;
                float y0 = BODY_BOTTOM + 8.0f;
                float y1 = TOWER_TOP - 8.0f;
                colorVertex(slits, view, face, cellX, cellZ, front, c - 0.6f, y0, 0.9f, 0.92f, 1.0f, TOWER_SLIT_GAIN, true);
                colorVertex(slits, view, face, cellX, cellZ, front, c + 0.6f, y0, 0.9f, 0.92f, 1.0f, TOWER_SLIT_GAIN, true);
                colorVertex(slits, view, face, cellX, cellZ, front, c + 0.6f, y1, 0.9f, 0.92f, 1.0f, TOWER_SLIT_GAIN, true);
                colorVertex(slits, view, face, cellX, cellZ, front, c - 0.6f, y1, 0.9f, 0.92f, 1.0f, TOWER_SLIT_GAIN, true);
            }
        }
        MeshData slitMesh = slits.build();
        if (slitMesh != null) {
            BufferUploader.drawWithShader(slitMesh);
        }
        RenderSystem.defaultBlendFunc();
    }

    /** A vertical dark quad from (plane p0, along a0) to (plane p1, along a1), y0..y1. */
    private static void towerQuad(BufferBuilder quads, Matrix4f view, int face, int cellX, int cellZ,
                                  float p0, float a0, float p1, float a1, float y0, float y1) {
        colorVertex(quads, view, face, cellX, cellZ, p0, a0, y0, TOWER_R, TOWER_G, TOWER_B, 1.0f, false);
        colorVertex(quads, view, face, cellX, cellZ, p1, a1, y0, TOWER_R, TOWER_G, TOWER_B, 1.0f, false);
        colorVertex(quads, view, face, cellX, cellZ, p1, a1, y1, TOWER_R, TOWER_G, TOWER_B, 1.0f, false);
        colorVertex(quads, view, face, cellX, cellZ, p0, a0, y1, TOWER_R, TOWER_G, TOWER_B, 1.0f, false);
    }

    // ------------------------------------------------------------------------------ vertex helpers

    private static void texVertex(BufferBuilder quads, Matrix4f view, int face, int cellX, int cellZ, float plane,
                                  float along, float y, float u, float v, int layer) {
        double wx = worldX(face, cellX, plane, along);
        double wz = worldZ(face, cellZ, plane, along);
        double rx = wx - camX;
        double ry = y - camY;
        double rz = wz - camZ;
        boolean glow = layer != ALBEDO;
        float alpha = fade(rx, ry, rz, glow);
        // The upper storeys fade into the haze under the cloud ceiling.
        float haze = Mth.clamp((y - HAZE_FROM_Y) / (HAZE_TO_Y - HAZE_FROM_Y), 0.0f, 1.0f);
        alpha *= 1.0f - HAZE_STRENGTH * haze * haze * (3.0f - 2.0f * haze);
        float r;
        float g;
        float b;
        if (glow) {
            // Far away the light blooms into the fog: a diffused halo fades in over the windows,
            // and the crisp windows dim a little but stay as its bright core.
            float d = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
            float m = Mth.clamp((d - DIFFUSE_FROM) / (DIFFUSE_TO - DIFFUSE_FROM), 0.0f, 1.0f);
            m = m * m * (3.0f - 2.0f * m);
            alpha *= layer == GLOW_SHARP ? 1.0f - (1.0f - CORE_KEEP) * m : m * SOFT_GAIN;
            // A little irregularity over the wall: patches a bit dimmer or brighter, some a
            // touch warmer or cooler (different lamps in the gallery behind).
            double a = face < 2 ? wz : wx;
            float bright = 1.0f + IRREGULAR * noise(a, y, 46.0, face);
            float temp = TINT * noise(a, y, 90.0, face + 11);
            r = bright * (1.0f + temp);
            g = bright;
            b = bright * (1.0f - temp);
        } else {
            // Brick and glass darken with height (soot).
            float k = Mth.lerp(Mth.clamp((y - SOOT_FROM_Y) / (SOOT_TO_Y - SOOT_FROM_Y), 0.0f, 1.0f), SOOT_LOW, SOOT_HIGH);
            r = k;
            g = k;
            b = k;
        }
        quads.addVertex(view, (float) (rx * scale), (float) (ry * scale), (float) (rz * scale))
                .setUv(u, v).setColor(Math.min(r, 1.0f), Math.min(g, 1.0f), Math.min(b, 1.0f), alpha);
    }

    private static void colorVertex(BufferBuilder quads, Matrix4f view, int face, int cellX, int cellZ, float plane,
                                    float along, float y, float r, float g, float b, float gain, boolean glow) {
        double rx = worldX(face, cellX, plane, along) - camX;
        double ry = y - camY;
        double rz = worldZ(face, cellZ, plane, along) - camZ;
        float alpha = fade(rx, ry, rz, glow) * (glow ? gain / GLOW_GAIN : gain);
        quads.addVertex(view, (float) (rx * scale), (float) (ry * scale), (float) (rz * scale)).setColor(r, g, b, alpha);
    }

    /** Faces 0/1 are the +x/-x facades (along = z), faces 2/3 the +z/-z ones (along = x). */
    private static double worldX(int face, int cellX, float plane, float along) {
        return cellX + (face < 2 ? plane : along);
    }

    private static double worldZ(int face, int cellZ, float plane, float along) {
        return cellZ + (face < 2 ? along : plane);
    }

    /** How much of this point shows through the fog (and through pass 2's near fade). */
    private static float fade(double rx, double ry, double rz, boolean glow) {
        float d = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        float through = glow ? GLOW_GAIN * (float) Math.exp(-d / GLOW_FOG) : (float) Math.exp(-d / ALBEDO_FOG);
        if (fadeTo > 0.0f) {
            float t = Mth.clamp((d - fadeFrom) / (fadeTo - fadeFrom), 0.0f, 1.0f);
            through *= t * t * (3.0f - 2.0f * t);
        }
        return through * dim;
    }

    /**
     * The pictures are seen from very far away and at grazing angles, so they need mipmaps
     * (smaller, pre-blurred copies) and anisotropic filtering, or the window grid would shimmer
     * and smear into stripes. Minecraft loads plain textures without either; we set them up once
     * per texture (again after a resource reload, which makes new textures). The body pictures
     * repeat both ways (storey after storey); the top strips only across.
     */
    private static void prepareTextures() {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < TEXTURES.length; i++) {
            AbstractTexture texture = mc.getTextureManager().getTexture(TEXTURES[i]);
            int id = texture.getId();
            if (id == PREPARED[i]) {
                continue;
            }
            PREPARED[i] = id;
            boolean body = TEXTURES[i] == BODY || TEXTURES[i] == BODY_GLOW;
            GlStateManager._bindTexture(id);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 10);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, 10);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, body ? GL11.GL_REPEAT : GL12.GL_CLAMP_TO_EDGE);
            if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
                float max = GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
                GL11.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                        Math.min(MAX_ANISOTROPY, max));
            }
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GlStateManager._bindTexture(0);
        }
    }

    /** Anisotropic filtering samples (keeps the storeys apart when the wall is seen at a slant). */
    private static final float MAX_ANISOTROPY = 16.0f;

    private Level39FarFacade() {}
}
