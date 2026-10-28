package com.rickr.backrooms.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The far renderer of Level 227: the planet-sized tree beyond the loaded terrain, its roots above
 * the world, the Sun and its bubbles, the mist and the black void, all in one pass per frame.
 *
 * <p><b>The problem.</b> The tree is ~14,000 blocks across and its roots climb to y 7000; the
 * game draws real blocks only a couple of hundred blocks out, and its depth range ends soon after.
 * So the far tree is drawn into a picture of its own and laid in behind and among the real blocks.
 *
 * <p><b>Each frame</b> (at {@code AFTER_BLOCK_ENTITIES}: all opaque terrain and entities are drawn,
 * water and particles are not yet):
 * <ol>
 *   <li><b>The far picture</b> ({@link #far}): the tree meshes, built once ({@link Level227FarMesh})
 *       and kept on the GPU, drawn with {@code megaflora_far}. It has its own depth, logarithmic
 *       out to 65,536 blocks (the game's own ends a few hundred blocks out), so the trunk, branches and roots hide each
 *       other correctly however far away they are. Then the Sun and bubbles
 *       ({@link Level227Heavens}) into the same picture, hidden by the roots where they cross.</li>
 *   <li><b>Lay it in</b> ({@code megaflora_composite}, one full-screen pass): for every pixel it
 *       compares the real terrain's distance with the far picture's and paints the sky, the void
 *       and the far tree wherever no real block is in front.</li>
 * </ol>
 *
 * <p><b>Lining up with the real blocks.</b> Both come from the same plan ({@code Level227Tree}),
 * so they are the same shapes. Still, the far tubes are smooth and the blocks are blocks: two
 * versions of one branch must never show at once. So inside the loaded terrain the far tree is
 * not drawn at all (its pixels are thrown away before shading, which also keeps it cheap); it
 * fades in only over the last band before the edge of the loaded chunks, while the real blocks
 * there fade out into it. Above and below the world (roots, the trunk in the void) there are no
 * blocks, so the far tree shows everywhere there. The mist is the same curve as the terrain's fog
 * ({@link Level227Atmosphere}), read from the same fog numbers, so the seam doesn't show.
 *
 * <p><b>Cost.</b> The meshes are built once on a background thread (~0.6 s; 1.40 M near-detail and
 * 0.39 M far-detail vertices, ~34 MB, logged at build time) and uploaded a few cells per frame.
 * Per frame: cells outside the view or wholly inside the loaded terrain are skipped, the rest drawn
 * nearest first (so hidden pixels are skipped before shading), near detail only within
 * {@value #HIGH_DISTANCE} blocks; then a few dozen quads for the heavens and one full-screen pass.
 * Measured on an RTX 4070 at 1920x1080 with the same shaders and meshes: 0.15-0.36 ms for the
 * tree, under 0.01 ms for the heavens, 0.11-0.13 ms for the composite.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level227Far {
    private static final Logger LOGGER = LoggerFactory.getLogger("backrooms/l227far");
    /** How far the far picture's (logarithmic) depth reaches (blocks). */
    static final float DEPTH_RANGE = 65536.0f;
    /** Within this distance of a cell the near level of detail is used. */
    private static final float HIGH_DISTANCE = 1600.0f;
    /** Cells uploaded per frame once the build is done (spreads the upload over a few frames). */
    private static final int UPLOADS_PER_FRAME = 6;
    /** Free everything after this many ticks outside Level 227. */
    private static final int FREE_AFTER_TICKS = 200;
    /**
     * Inside the world, the far geometry starts this far inside the loaded terrain's edge (blocks):
     * nearer pixels are thrown away before shading. It must not be drawn half-faded either: the
     * far picture keeps only its nearest surface, so a faded canopy mass there would hide the far
     * tree behind it and show bright sky instead (white puffs round the real canopies). Cells
     * wholly inside the margin plus CELL_SKIP_EXTRA aren't drawn at all.
     */
    private static final float INNER_MARGIN = 40.0f;
    private static final float CELL_SKIP_EXTRA = 56.0f;
    /** The open air's own haze: light fades by e over this many blocks. */
    private static final float OPEN_AIR = 7000.0f;
    /** How far (blocks) the farthest branch tips drift as the giant tree flexes. */
    private static final float SWAY = 14.0f;
    /** Daylight on the far tree (diffuse, misty, a touch green). */
    private static final float[] LIGHT = {0.82f, 0.88f, 0.80f};
    /**
     * Mist layers: {height, half-thickness, density per block, brightness}: the pale mist floor
     * just above the void, faint bands between the branch tiers, the high haze among the roots.
     */
    private static final float[][] LAYERS = {
            {40.0f, 55.0f, 1.0f / 1500.0f, 1.0f},
            {330.0f, 40.0f, 1.0f / 900.0f, 1.05f},
            {610.0f, 35.0f, 1.0f / 1100.0f, 1.1f},
            {3200.0f, 1400.0f, 1.0f / 7000.0f, 1.15f},
    };
    private static final String[] LAYER_NAMES = {"LayerA", "LayerB", "LayerC", "LayerD"};

    @Nullable
    private static ShaderInstance farShader;
    @Nullable
    private static ShaderInstance glowShader;
    @Nullable
    private static ShaderInstance compositeShader;

    /** The far picture (colour + linear depth) and a copy of the scene's depth. */
    @Nullable
    private static TextureTarget far;
    @Nullable
    private static TextureTarget sceneDepth;
    @Nullable
    private static VertexBuffer screenQuad;

    /** The tree meshes being built, waiting to upload, and on the GPU. */
    @Nullable
    private static CompletableFuture<List<Level227FarMesh.Cell>> building;
    private static final List<Level227FarMesh.Cell> PENDING = new ArrayList<>();
    private static final List<GpuCell> CELLS = new ArrayList<>();
    private static boolean failed;
    private static long buildStart;
    private static int ticksAway;

    /** One uploaded cell: its box and both levels of detail (either may be null when empty). */
    private record GpuCell(int layer, float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                           @Nullable VertexBuffer high, @Nullable VertexBuffer low) {}

    // Scratch (render thread only).
    private static final Matrix4f CLIP = new Matrix4f();
    private static final Matrix4f INV_PROJECTION = new Matrix4f();
    private static final Vector4f SUN = new Vector4f();
    private static final Vector4f[] PLANES = {new Vector4f(), new Vector4f(), new Vector4f(), new Vector4f()};
    /** The cells to draw this frame, nearest first (grown when the cell count grows, never shrunk). */
    private static VertexBuffer[] drawOrder = new VertexBuffer[0];
    private static float[] drawDistance = new float[0];

    private Level227Far() {}

    /** True when the shaders loaded (otherwise Level227Atmosphere draws a plain sky). */
    static boolean available() {
        return farShader != null && glowShader != null && compositeShader != null;
    }

    /** Mod bus: our three shaders. A broken shader only logs an error; the level then has a plain sky. */
    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        farShader = null;
        glowShader = null;
        compositeShader = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Backrooms.id("megaflora_far"),
                    DefaultVertexFormat.POSITION_COLOR_NORMAL), shader -> farShader = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Backrooms.id("megaflora_glow"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), shader -> glowShader = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Backrooms.id("megaflora_composite"),
                    DefaultVertexFormat.POSITION), shader -> compositeShader = shader);
        } catch (Exception e) {
            LOGGER.error("[{}] Level 227 far shaders failed to load; the level gets a plain sky", Backrooms.MOD_ID, e);
        }
    }

    /** Starts the build when you arrive; frees the GPU a while after you leave. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Level227Atmosphere.inLevel227()) {
            ticksAway = 0;
            if (available() && building == null && CELLS.isEmpty() && PENDING.isEmpty() && !failed) {
                buildStart = System.nanoTime();
                building = CompletableFuture.supplyAsync(Level227FarMesh::build, Util.backgroundExecutor());
            }
        } else if (++ticksAway == FREE_AFTER_TICKS) {
            freeAll();
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || !available()
                || !Level227Atmosphere.inLevel227()) {
            return;
        }
        uploadSome();
        render(event);
    }

    // ==========================================================================================
    // Per frame
    // ==========================================================================================

    private static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        ensureTargets(main);
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        Matrix4f view = event.getModelViewMatrix();
        Matrix4f projection = event.getProjectionMatrix();
        // The loaded terrain reaches about this far (chunk centres within the render distance).
        float loaded = Math.max(48.0f, (mc.options.getEffectiveRenderDistance() - 0.5f) * 16.0f);
        double seconds = (System.nanoTime() / 1.0e9) % 10_000.0;

        // 1. The far picture: tree, then the heavens over it.
        // Clearing only clears depth while depth writes are on (whoever drew last may have left them off).
        RenderSystem.depthMask(true);
        far.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        far.clear(Minecraft.ON_OSX);
        far.bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        drawTree(view, projection, eye, loaded, seconds);

        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        // Premultiplied: glows add light (alpha 0), bubbles cover what is behind them.
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        ShaderInstance glow = glowShader;
        setAtmosphere(glow, eye, loaded, seconds);
        Level227Heavens.draw(glow, view, projection, camera, seconds, loaded);

        // 2. Lay it in behind / among the real blocks.
        main.bindWrite(true);
        sceneDepth.copyDepthFrom(main); // (leaves no framebuffer bound)
        main.bindWrite(true);
        ShaderInstance composite = compositeShader;
        composite.setSampler("SceneDepth", sceneDepth.getDepthTextureId());
        composite.setSampler("FarColor", far.getColorTextureId());
        composite.setSampler("FarDepth", far.getDepthTextureId());
        composite.setDefaultUniforms(VertexFormat.Mode.QUADS, view, projection, mc.getWindow());
        set(composite, "InvProjMat").set(INV_PROJECTION.set(projection).invert());
        setAtmosphere(composite, eye, loaded, seconds);
        setSunOnScreen(composite, view, projection, eye, seconds);
        RenderSystem.disableDepthTest();
        // The pass outputs (colour x coverage, coverage); the picture's own alpha is kept.
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        composite.apply();
        screenQuad.bind();
        screenQuad.draw();
        VertexBuffer.unbind();
        composite.clear();

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    /** The tree cells in view, near detail close by, the rest coarse. */
    private static void drawTree(Matrix4f view, Matrix4f projection, Vec3 eye, float loaded, double seconds) {
        if (CELLS.isEmpty()) {
            return;
        }
        ShaderInstance shader = farShader;
        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, view, projection, Minecraft.getInstance().getWindow());
        setAtmosphere(shader, eye, loaded, seconds);
        set(shader, "Sway").set(SWAY, loaded * 2.5f);
        set(shader, "LightColor").set(LIGHT[0], LIGHT[1], LIGHT[2]);
        CLIP.set(projection).mul(view);
        for (int i = 0; i < 4; i++) {
            // Left, right, bottom, top: no near/far planes (the tree is far past the game's far plane).
            CLIP.frustumPlane(Matrix4fc.PLANE_NX + i, PLANES[i]);
        }
        float inner = loaded - INNER_MARGIN - CELL_SKIP_EXTRA;
        if (drawOrder.length < CELLS.size()) {
            drawOrder = new VertexBuffer[CELLS.size()];
            drawDistance = new float[CELLS.size()];
        }
        int count = 0;
        for (GpuCell cell : CELLS) {
            float x0 = (float) (cell.minX() - eye.x);
            float y0 = (float) (cell.minY() - eye.y);
            float z0 = (float) (cell.minZ() - eye.z);
            float x1 = (float) (cell.maxX() - eye.x);
            float y1 = (float) (cell.maxY() - eye.y);
            float z1 = (float) (cell.maxZ() - eye.z);
            if (!inView(x0, y0, z0, x1, y1, z1)) {
                continue;
            }
            // Wholly inside the loaded terrain and the world's height: the real blocks are there.
            float farX = Math.max(Math.abs(x0), Math.abs(x1));
            float farZ = Math.max(Math.abs(z0), Math.abs(z1));
            if (farX * farX + farZ * farZ < inner * inner && cell.minY() > 0.0f && cell.maxY() < 768.0f) {
                continue;
            }
            float dx = Math.max(0.0f, Math.max(x0, -x1));
            float dy = Math.max(0.0f, Math.max(y0, -y1));
            float dz = Math.max(0.0f, Math.max(z0, -z1));
            float distance = dx * dx + dy * dy + dz * dz;
            VertexBuffer mesh = distance < HIGH_DISTANCE * HIGH_DISTANCE && cell.high() != null ? cell.high() : cell.low();
            if (mesh == null) {
                continue;
            }
            // Keep the list sorted nearest first (insertion: a few dozen cells), so the near
            // geometry fills the depth first and the GPU skips shading what it hides.
            int at = count++;
            while (at > 0 && drawDistance[at - 1] > distance) {
                drawOrder[at] = drawOrder[at - 1];
                drawDistance[at] = drawDistance[at - 1];
                at--;
            }
            drawOrder[at] = mesh;
            drawDistance[at] = distance;
        }
        shader.apply();
        for (int i = 0; i < count; i++) {
            drawOrder[i].bind();
            drawOrder[i].draw();
            drawOrder[i] = null;
        }
        VertexBuffer.unbind();
        shader.clear();
    }

    /** False when the box (camera-relative) is wholly outside one side of the view. */
    private static boolean inView(float x0, float y0, float z0, float x1, float y1, float z1) {
        for (Vector4f p : PLANES) {
            float x = p.x >= 0.0f ? x1 : x0;
            float y = p.y >= 0.0f ? y1 : y0;
            float z = p.z >= 0.0f ? z1 : z0;
            if (p.x * x + p.y * y + p.z * z + p.w < 0.0f) {
                return false;
            }
        }
        return true;
    }

    /** The mist, sky and light numbers shared by the three shaders. */
    private static void setAtmosphere(ShaderInstance shader, Vec3 eye, float loaded, double seconds) {
        float[] high = Level227Atmosphere.highHaze();
        float[] zenith = Level227Atmosphere.zenith();
        float[] dark = Level227Atmosphere.voidColor();
        set(shader, "Eye").set((float) eye.x, (float) eye.y, (float) eye.z);
        set(shader, "Time").set((float) seconds);
        set(shader, "DepthRange").set(DEPTH_RANGE);
        set(shader, "Loaded").set(loaded, INNER_MARGIN);
        set(shader, "OpenAir").set(OPEN_AIR);
        // Toward the void the mist is as dark as the void itself.
        set(shader, "MistLow").set(dark[0], dark[1], dark[2]);
        set(shader, "MistHigh").set(high[0], high[1], high[2]);
        set(shader, "Zenith").set(zenith[0], zenith[1], zenith[2]);
        set(shader, "VoidColor").set(dark[0], dark[1], dark[2]);
        set(shader, "SunPos").set((float) Level227Heavens.sunX(seconds), (float) Level227Heavens.sunY(seconds),
                (float) Level227Heavens.sunZ(seconds));
        for (int i = 0; i < LAYERS.length; i++) {
            set(shader, LAYER_NAMES[i]).set(LAYERS[i][0], LAYERS[i][1], LAYERS[i][2], LAYERS[i][3]);
        }
    }

    /**
     * Where the Sun is on screen and how far, for the composite's glare: it looks at a few points
     * over the Sun's disc to see whether it shines through a gap, and if so blooms over the roots.
     */
    private static void setSunOnScreen(ShaderInstance shader, Matrix4f view, Matrix4f projection, Vec3 eye, double seconds) {
        SUN.set((float) (Level227Heavens.sunX(seconds) - eye.x), (float) (Level227Heavens.sunY(seconds) - eye.y),
                (float) (Level227Heavens.sunZ(seconds) - eye.z), 1.0f);
        view.transform(SUN);
        float depth = -SUN.z;
        projection.transform(SUN);
        boolean ahead = SUN.w > 1.0f;
        float u = ahead ? SUN.x / SUN.w * 0.5f + 0.5f : -10.0f;
        float v = ahead ? SUN.y / SUN.w * 0.5f + 0.5f : -10.0f;
        // The looks spread over most of the disc (vertical screen units; the shader corrects width).
        float spacing = ahead ? 0.5f * projection.m11() * Level227Heavens.SUN_RADIUS * 0.6f / depth : 0.0f;
        set(shader, "SunScreen").set(u, v, ahead ? 1.0f : 0.0f, spacing);
        set(shader, "SunDepth").set(depth);
    }

    private static AbstractUniform set(ShaderInstance shader, String name) {
        return shader.safeGetUniform(name);
    }

    // ==========================================================================================
    // GPU resources
    // ==========================================================================================

    private static void ensureTargets(RenderTarget main) {
        if (far == null) {
            far = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            sceneDepth = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            if (main.isStencilEnabled()) {
                sceneDepth.enableStencil(); // the depth formats must match for the copy
            }
        } else if (far.width != main.width || far.height != main.height) {
            far.resize(main.width, main.height, Minecraft.ON_OSX);
            sceneDepth.resize(main.width, main.height, Minecraft.ON_OSX);
        }
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
    }

    /** Takes the finished build and uploads a few cells per frame. */
    private static void uploadSome() {
        if (building != null && building.isDone()) {
            try {
                PENDING.addAll(building.join());
                int high = 0;
                int low = 0;
                for (Level227FarMesh.Cell cell : PENDING) {
                    high += cell.parts[Level227FarMesh.HIGH].vertices;
                    low += cell.parts[Level227FarMesh.LOW].vertices;
                }
                LOGGER.info("[{}] Level 227 far tree built in {} ms: {} cells, {} near-detail vertices, {} far-detail vertices ({} MB)",
                        Backrooms.MOD_ID, (System.nanoTime() - buildStart) / 1_000_000, PENDING.size(), high, low,
                        (high + low) * DefaultVertexFormat.POSITION_COLOR_NORMAL.getVertexSize() / (1024 * 1024));
            } catch (Exception e) {
                LOGGER.error("[{}] Level 227 far tree failed to build; only the sky will show", Backrooms.MOD_ID, e);
                failed = true;
            }
            building = null;
        }
        for (int n = 0; n < UPLOADS_PER_FRAME && !PENDING.isEmpty(); n++) {
            Level227FarMesh.Cell cell = PENDING.remove(PENDING.size() - 1);
            VertexBuffer high = upload(cell.parts[Level227FarMesh.HIGH]);
            VertexBuffer low = upload(cell.parts[Level227FarMesh.LOW]);
            Level227FarMesh.free(cell);
            if (high != null || low != null) {
                CELLS.add(new GpuCell(cell.layer, cell.minX, cell.minY, cell.minZ, cell.maxX, cell.maxY, cell.maxZ, high, low));
            }
        }
    }

    @Nullable
    private static VertexBuffer upload(Level227FarMesh.Part part) {
        if (part.data == null) {
            return null;
        }
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(part.data); // also frees the mesh data
        part.data = null;
        VertexBuffer.unbind();
        return buffer;
    }

    private static void freeAll() {
        if (building != null) {
            // Let it finish in the background, then drop it.
            building.thenAccept(cells -> Minecraft.getInstance().execute(() -> cells.forEach(Level227FarMesh::free)));
            building = null;
        }
        PENDING.forEach(Level227FarMesh::free);
        PENDING.clear();
        for (GpuCell cell : CELLS) {
            if (cell.high() != null) {
                cell.high().close();
            }
            if (cell.low() != null) {
                cell.low().close();
            }
        }
        CELLS.clear();
        if (far != null) {
            far.destroyBuffers();
            sceneDepth.destroyBuffers();
            far = null;
            sceneDepth = null;
        }
        failed = false;
    }
}
