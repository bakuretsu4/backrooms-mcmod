package com.rickr.backrooms.client.level228;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.Level228ClientState;
import com.rickr.backrooms.client.light.Level228Bulbs;
import com.rickr.backrooms.mixin.client.FrustumInvoker;
import com.rickr.backrooms.world.layout.Level228Plan;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Arrays;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Level 228's static props: every column, frame, arch, floor and ceiling decal, light fitting and
 * piece of carnival clutter the floor plan places ({@link Level228Plan#propsInChunk}), drawn from
 * the generated meshes ({@link Level228PropMeshes}) and texture sheets (textures/level228/props_*,
 * outside the block atlas, which is full).
 *
 * <p><b>How.</b> Each 16 x 16 chunk column's props are turned into one vertex buffer per sheet,
 * once, and kept while you are near; each frame only the buffers of chunks in view are drawn, with
 * vanilla's {@code entityCutoutNoCull} render type and shader. So shader packs (Iris) run them
 * through their entity program and plain Veil lights them like any entity; nothing is
 * recomputed per frame. A chunk is built a slice at a time (about {@value #BUILD_BUDGET_NS} ns of
 * work per frame, nearest chunks first) so arriving never hitches. Light is baked per vertex from
 * the level's light at the build (smoothly interpolated between blocks, half a block out along
 * the surface, so a column darkens toward the dark between two bulbs); it is rebuilt once when the
 * client's lighting has settled, and again whenever the light round a chunk changes (a shifting
 * panel, a light placed). Bulbs flicker in Veil's lights and in the glow below, not in block light,
 * so baking is exact.
 *
 * <p><b>Glow.</b> Bulb glass, candle flames and exit-sign faces are redrawn every frame (a few
 * thousand quads) with the brightness {@link Level228Bulbs#glow} gives them: full bright and warm
 * when lit (so packs bloom them), dull grey glass when dead, with an additive halo.
 *
 * <p><b>Mirror surfaces</b> and see-through glass are drawn later, sorted, by
 * {@link Level228MirrorSurfaces}, which reads the glass panes collected here.
 *
 * <p>Iris: the static buffers are drawn straight from their VertexBuffers, which Iris handles like
 * any vanilla VBO draw: {@code entityCutoutNoCull}'s shader comes from GameRenderer's getter, which
 * Iris overrides with the pack's gbuffers_entities program, and a BufferBuilder made for
 * NEW_ENTITY while a pack is on is extended to Iris's entity format (tangents, mid-UV, entity id
 * 0) at build. So no per-frame streaming through Iris's buffer source is needed; switching a pack
 * on or off rebuilds the buffers. They are drawn at AFTER_ENTITIES: still before Iris's deferred
 * passes (run at the start of translucent terrain), so packs light and shade them like the
 * world; AFTER_TRANSLUCENT_BLOCKS would be after the deferred passes, and AFTER_BLOCK_ENTITIES is
 * re-fired by Iris inside its shadow pass. Nothing is drawn in the shadow pass (the matrices
 * there are the light's).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level228Props {
    /** Chunks are kept built (and drawn) within this distance of the camera, blocks. */
    static final double RADIUS = 44.0;
    /** Bulbs glow (per frame) within this distance. */
    private static final double GLOW_RADIUS = 40.0;
    private static final long BUILD_BUDGET_NS = 2_500_000L;
    /** Rebuild a chunk once this long after it was first built (the client's light arrives late). */
    private static final int SETTLE_TICKS = 50;
    private static final int MAX_PROPS = 8192;
    /** How far out along a surface the light is taken (blocks): the air in front of it. */
    private static final float LIGHT_PROBE = 0.35f;

    private static final Long2ObjectOpenHashMap<Chunk> CHUNKS = new Long2ObjectOpenHashMap<>();
    /** The same chunks as a plain array, so per-frame loops need no iterator object. */
    private static Chunk[] all = new Chunk[64];
    private static int allN;
    /**
     * How far outside its chunk a chunk's props read the grid (floor decals look three blocks
     * ahead, wall faces one to the side): a shifting column this close can change them.
     */
    private static final int SHIFT_REACH = 4;
    private static final Logger LOGGER = LoggerFactory.getLogger("backrooms/l228props");
    /** Chunks drawn this frame (filled each frame, grown never shrunk). */
    static Chunk[] drawn = new Chunk[64];
    static int drawnCount;

    private static final Level228Plan.Prop[] PROPS = new Level228Plan.Prop[MAX_PROPS];
    private static final int[] MESHES = new int[4];
    private static final Matrix4f MV = new Matrix4f();
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static final LightGrid GRID = new LightGrid();

    private static final Job JOB = new Job();
    /** The build in progress (always {@link #JOB}), or null. */
    @Nullable
    private static Job job;
    private static ByteBufferBuilder[] bytes = new ByteBufferBuilder[0];
    private static ResourceLocation[] sheetLocations = new ResourceLocation[0];
    private static RenderType[] cutTypes = new RenderType[0];
    private static RenderType[] haloTypes = new RenderType[0];
    @Nullable
    private static Level228PropMeshes.Region halo;
    private static boolean texturesRegistered;
    /** The level the chunks were built in (a new ClientLevel comes with every dimension change or rejoin). */
    @Nullable
    private static ClientLevel builtLevel;
    /** Whether the memory use after the first full build round has been logged (once per fresh start). */
    private static boolean statsLogged;
    private static long builtSeed;
    private static int builtShiftVersion = Integer.MIN_VALUE;
    private static long builtShiftBits;
    private static boolean builtWithPack;

    static {
        for (int i = 0; i < MAX_PROPS; i++) {
            PROPS[i] = new Level228Plan.Prop();
        }
    }

    private Level228Props() {}

    // ---- Chunk state ------------------------------------------------------------------------------

    /** One chunk column's props: buffers per sheet, and what is drawn per frame. */
    static final class Chunk {
        final int cx;
        final int cz;
        VertexBuffer[] vbo;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        boolean built;
        boolean dirty = true;
        boolean settled;
        long builtAt;
        /** Index in {@link #all}. */
        int slot;
        /** Whether the props read a column that changes with the shift state (rebuilt when it changes). */
        boolean dynamic;
        /** Bytes of vertex data in {@link #vbo}. */
        int gpuBytes;
        long lightSum;
        // Emitters: x y z size per emitter (world), kind/state/phase packed, baked light, this frame's glow.
        int emitN;
        float[] emit = new float[0];
        int[] emitInfo = new int[0];
        int[] emitLight = new int[0];
        float[] emitGlow = new float[0];
        // Glow-layer vertices: x y z u v nx ny nz shade, and sheet << 16 | emitter.
        int glowN;
        float[] glow = new float[0];
        int[] glowRef = new int[0];
        // Glass-layer vertices (booth window): x y z u v nx ny nz shade, sheet, light.
        int glassN;
        float[] glass = new float[0];
        int[] glassSheet = new int[0];
        int[] glassLight = new int[0];
        // See-through glass panels (PANEL kind GLASS): axis plane min max y0 y1 per pane, and noise.
        int paneN;
        float[] pane = new float[0];
        int[] paneNoise = new int[0];
        // Building scratch.
        int nextGlowN, nextGlassN, nextPaneN, nextEmitN;

        Chunk(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
        }

        void close() {
            if (vbo != null) {
                for (VertexBuffer v : vbo) {
                    if (v != null) {
                        v.close();
                    }
                }
                vbo = null;
            }
        }
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private static void addChunk(long key, Chunk c) {
        CHUNKS.put(key, c);
        if (allN == all.length) {
            all = Arrays.copyOf(all, allN * 2);
        }
        c.slot = allN;
        all[allN++] = c;
    }

    /** Drops a chunk; the last one in {@link #all} takes its slot. */
    private static void removeChunk(Chunk c) {
        CHUNKS.remove(key(c.cx, c.cz));
        Chunk last = all[--allN];
        all[c.slot] = last;
        last.slot = c.slot;
        all[allN] = null;
        c.close();
    }

    // ---- Events -----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            // New meshes (and sheet textures, which the texture manager reloads itself).
            Level228PropMeshes.invalidate();
            Level228PropCatalog.invalidate();
            RenderSystem.recordRenderCall(Level228Props::releaseAll);
        });
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !Level228ClientState.inLevel228()) {
            if (texturesRegistered || !CHUNKS.isEmpty()) {
                releaseAll();
            }
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        long now = mc.level.getGameTime();
        // Once a second: let go of chunks left behind; re-check the light round the near ones.
        if (now % 20 == 0) {
            double keep = RADIUS + 24.0;
            // Downward, so the chunk swapped into a removed slot has already been looked at.
            for (int i = allN - 1; i >= 0; i--) {
                Chunk c = all[i];
                double dx = c.cx * 16 + 8 - cam.x;
                double dz = c.cz * 16 + 8 - cam.z;
                if (dx * dx + dz * dz > keep * keep && (job == null || job.chunk != c)) {
                    removeChunk(c);
                } else if (c.built && !c.dirty) {
                    if (!c.settled && now - c.builtAt >= SETTLE_TICKS) {
                        c.settled = true;
                        c.dirty = true;
                    } else if (lightSum(mc.level, c) != c.lightSum) {
                        c.dirty = true;
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES && stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !Level228ClientState.inLevel228() || !Level228ClientState.hasSeed() || Level228Iris.shadowPass()) {
            return;
        }
        if (!Level228PropMeshes.ensureLoaded(mc.getResourceManager())) {
            return;
        }
        if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            prepare(mc, level);
            Vec3 cam = event.getCamera().getPosition();
            build(level, cam);
            collectDrawn(event, cam);
            drawStatic(event, cam);
        } else {
            drawGlow(event, level);
        }
    }

    // ---- Setup ------------------------------------------------------------------------------------

    private static void prepare(Minecraft mc, ClientLevel level) {
        String[] names = Level228PropMeshes.sheetNames();
        if (!texturesRegistered || sheetLocations.length != names.length) {
            TextureManager tm = mc.getTextureManager();
            sheetLocations = new ResourceLocation[names.length];
            cutTypes = new RenderType[names.length];
            haloTypes = new RenderType[names.length];
            bytes = new ByteBufferBuilder[names.length];
            for (int i = 0; i < names.length; i++) {
                sheetLocations[i] = Backrooms.id("textures/level228/props_" + names[i] + ".png");
                tm.register(sheetLocations[i], new Level228PropTexture(sheetLocations[i]));
                cutTypes[i] = RenderType.entityCutoutNoCull(sheetLocations[i]);
                haloTypes[i] = RenderType.eyes(sheetLocations[i]);
                bytes[i] = new ByteBufferBuilder(1 << 18);
            }
            halo = Level228PropMeshes.region("de_halo");
            texturesRegistered = true;
        }
        long seed = Level228ClientState.seed();
        Level228Plan.ShiftState shift = Level228ClientState.shift();
        boolean pack = Level228Iris.packInUse();
        if (level != builtLevel || seed != builtSeed || pack != builtWithPack) {
            // Another level (dimension change, rejoin), another maze, or another vertex format: start again.
            clearChunks();
            builtLevel = level;
            builtSeed = seed;
            builtWithPack = pack;
            builtShiftBits = shift.bits;
            builtShiftVersion = shift.version;
        } else if (shift.version != builtShiftVersion || shift.bits != builtShiftBits) {
            // A passage shifted or a secret panel opened (shared, server-side): rebuild in place
            // only the chunks whose props read a shifting column (panels MIRROR <-> OPENING, the
            // secret panels, their wall faces and decals); the old buffers stay drawn until each
            // new one is ready. The mirror surfaces need nothing here: Level228MirrorSurfaces asks
            // mirrorsNear with the new state every tick. A chunk whose light the change affects
            // is caught by the light check in onClientTick.
            for (int i = 0; i < allN; i++) {
                if (all[i].dynamic) {
                    all[i].dirty = true;
                }
            }
            builtShiftBits = shift.bits;
            builtShiftVersion = shift.version;
        }
    }

    private static void clearChunks() {
        if (job != null) {
            job.abandon();
            job = null;
        }
        for (int i = 0; i < allN; i++) {
            all[i].close();
            all[i] = null;
        }
        allN = 0;
        CHUNKS.clear();
        drawnCount = 0;
        statsLogged = false;
    }

    static void releaseAll() {
        clearChunks();
        builtLevel = null;
        if (texturesRegistered) {
            TextureManager tm = Minecraft.getInstance().getTextureManager();
            for (ResourceLocation loc : sheetLocations) {
                tm.release(loc);
            }
            for (ByteBufferBuilder b : bytes) {
                if (b != null) {
                    b.close();
                }
            }
            bytes = new ByteBufferBuilder[0];
            texturesRegistered = false;
        }
        Level228MirrorSurfaces.release();
    }

    static ResourceLocation[] sheets() {
        return sheetLocations;
    }

    // ---- Building ---------------------------------------------------------------------------------

    /**
     * The chunk build in progress: one buffer builder per sheet, filled a slice per frame. One
     * instance, reused for every build (a BufferBuilder can't be reused; one per sheet per build).
     */
    private static final class Job {
        Chunk chunk;
        int count;
        int next;
        BufferBuilder[] builders = new BufferBuilder[0];
        ClientLevel level;

        Job reset(Chunk chunk, int count, ClientLevel level) {
            this.chunk = chunk;
            this.count = count;
            this.level = level;
            this.next = 0;
            if (builders.length != bytes.length) {
                builders = new BufferBuilder[bytes.length];
            } else {
                Arrays.fill(builders, null);
            }
            return this;
        }

        BufferBuilder builder(int sheet) {
            BufferBuilder b = builders[sheet];
            if (b == null) {
                b = new BufferBuilder(bytes[sheet], VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
                builders[sheet] = b;
            }
            return b;
        }

        void abandon() {
            for (int s = 0; s < builders.length; s++) {
                BufferBuilder b = builders[s];
                if (b != null) {
                    MeshData m = b.build();
                    if (m != null) {
                        m.close();
                    }
                    builders[s] = null;
                }
            }
            release();
        }

        /** Lets go of the chunk and level (the level must not outlive a dimension change here). */
        void release() {
            chunk = null;
            level = null;
        }
    }

    private static void build(ClientLevel level, Vec3 cam) {
        long start = System.nanoTime();
        while (System.nanoTime() - start < BUILD_BUDGET_NS) {
            if (job == null) {
                Chunk next = nextToBuild(level, cam);
                if (next == null) {
                    logMemoryOnce();
                    return;
                }
                job = start(level, next);
                if (job == null) {
                    continue;
                }
            }
            Job j = job;
            while (j.next < j.count && System.nanoTime() - start < BUILD_BUDGET_NS) {
                emitProp(j, PROPS[j.next++]);
            }
            if (j.next >= j.count) {
                finish(j);
                job = null;
            }
        }
    }

    /** The nearest chunk within reach that has props and is unbuilt or stale; null if none. */
    @Nullable
    private static Chunk nextToBuild(ClientLevel level, Vec3 cam) {
        int ccx = (int) Math.floor(cam.x) >> 4;
        int ccz = (int) Math.floor(cam.z) >> 4;
        int r = (int) Math.ceil(RADIUS / 16.0);
        Chunk best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = ccx + dx;
                int cz = ccz + dz;
                double ox = Math.max(Math.abs(cx * 16 + 8 - cam.x) - 8, 0);
                double oz = Math.max(Math.abs(cz * 16 + 8 - cam.z) - 8, 0);
                double d = ox * ox + oz * oz;
                if (d > RADIUS * RADIUS || d >= bestD || !level.hasChunk(cx, cz)) {
                    continue;
                }
                Chunk c = CHUNKS.get(key(cx, cz));
                if (c != null && !c.dirty) {
                    continue;
                }
                if (c == null) {
                    c = new Chunk(cx, cz);
                    addChunk(key(cx, cz), c);
                }
                best = c;
                bestD = d;
            }
        }
        return best;
    }

    @Nullable
    private static Job start(ClientLevel level, Chunk c) {
        int count = Level228Plan.propsInChunk(builtSeed, Level228ClientState.shift(), c.cx, c.cz, PROPS);
        c.dirty = false;
        c.dynamic = readsShift(builtSeed, c.cx, c.cz);
        if (count <= 0) {
            c.close();
            c.built = true;
            c.gpuBytes = 0;
            c.builtAt = level.getGameTime();
            c.emitN = c.glowN = c.glassN = c.paneN = 0;
            c.lightSum = lightSum(level, c);
            return null;
        }
        int y0 = Integer.MAX_VALUE;
        int y1 = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            y0 = Math.min(y0, PROPS[i].y);
            y1 = Math.max(y1, PROPS[i].y);
        }
        GRID.fill(level, c.cx * 16 - 4, y0 - 1, c.cz * 16 - 4, 24, y1 - y0 + 7, 24);
        c.nextGlowN = c.nextGlassN = c.nextPaneN = c.nextEmitN = 0;
        c.minY = Float.MAX_VALUE;
        c.maxY = -Float.MAX_VALUE;
        return JOB.reset(c, Math.min(count, MAX_PROPS), level);
    }

    /** Whether any column within {@link #SHIFT_REACH} of the chunk changes with the shift state. */
    private static boolean readsShift(long seed, int cx, int cz) {
        int x0 = cx * 16 - SHIFT_REACH;
        int z0 = cz * 16 - SHIFT_REACH;
        int x1 = cx * 16 + 15 + SHIFT_REACH;
        int z1 = cz * 16 + 15 + SHIFT_REACH;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (Level228Plan.isDynamic(seed, x, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Logs (debug) the vertex memory of the built chunks once the first full build round is
     * done: per chunk max and average and the total on the GPU, and the per-frame lists kept in
     * the heap. Waits for a round that built something (chunks may not have arrived yet).
     */
    private static void logMemoryOnce() {
        if (statsLogged) {
            return;
        }
        int n = 0;
        int max = 0;
        long total = 0;
        long heap = 0;
        for (int i = 0; i < allN; i++) {
            Chunk c = all[i];
            if (!c.built || c.gpuBytes == 0) {
                continue;
            }
            n++;
            max = Math.max(max, c.gpuBytes);
            total += c.gpuBytes;
            heap += 4L * (c.emit.length + c.emitInfo.length + c.emitLight.length + c.emitGlow.length + c.glow.length
                    + c.glowRef.length + c.glass.length + c.glassSheet.length + c.glassLight.length + c.pane.length
                    + c.paneNoise.length);
        }
        if (n == 0) {
            return;
        }
        statsLogged = true;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Level 228 props: {} chunks built; vertex bytes per chunk max {}, avg {}; total {} (GPU), "
                    + "{} in per-frame glow/glass lists (heap); Iris format: {}", n, max, total / n, total, heap, builtWithPack);
        }
    }

    private static void finish(Job j) {
        Chunk c = j.chunk;
        VertexBuffer[] vbo = c.vbo != null && c.vbo.length == bytes.length ? c.vbo : new VertexBuffer[bytes.length];
        if (c.vbo != null && c.vbo != vbo) {
            c.close();
        }
        int gpu = 0;
        for (int s = 0; s < bytes.length; s++) {
            BufferBuilder b = j.builders[s];
            MeshData mesh = b == null ? null : b.build();
            if (mesh == null) {
                if (vbo[s] != null) {
                    vbo[s].close();
                    vbo[s] = null;
                }
                continue;
            }
            MeshData.DrawState state = mesh.drawState();
            gpu += state.vertexCount() * state.format().getVertexSize();
            if (vbo[s] == null) {
                vbo[s] = new VertexBuffer(VertexBuffer.Usage.STATIC);
            }
            vbo[s].bind();
            vbo[s].upload(mesh);
        }
        VertexBuffer.unbind();
        c.vbo = vbo;
        c.gpuBytes = gpu;
        c.glowN = c.nextGlowN;
        c.glassN = c.nextGlassN;
        c.paneN = c.nextPaneN;
        c.emitN = c.nextEmitN;
        if (c.emitGlow.length < c.emitN) {
            c.emitGlow = new float[c.emitN];
        }
        boolean first = !c.built;
        c.built = true;
        if (first) {
            c.builtAt = j.level.getGameTime();
        }
        c.lightSum = lightSum(j.level, c);
        j.release();
    }

    /** A cheap fingerprint of the light round a chunk's props (block light on a sparse grid). */
    private static long lightSum(ClientLevel level, Chunk c) {
        long sum = 0;
        int y0 = Level228Plan.FEET_Y;
        for (int x = 1; x < 16; x += 3) {
            for (int z = 1; z < 16; z += 3) {
                for (int y = y0; y <= y0 + 3; y += 2) {
                    POS.set(c.cx * 16 + x, y, c.cz * 16 + z);
                    sum = sum * 31 + level.getBrightness(LightLayer.BLOCK, POS) * 17 + level.getBrightness(LightLayer.SKY, POS);
                }
            }
        }
        return sum;
    }

    /** Turns one prop's meshes into vertices (static) and per-frame lists (glow, glass, panes). */
    private static void emitProp(Job j, Level228Plan.Prop p) {
        Chunk c = j.chunk;
        if (p.type == Level228Plan.P_PANEL && Level228PropCatalog.panelKind(p) == Level228Plan.PANEL_GLASS) {
            addPane(c, p);
        }
        int n = Level228PropCatalog.meshes(p, MESHES);
        boolean flip = (p.flags & Level228Plan.PF_FLIP) != 0;
        int rot = p.rot & 3;
        float ax = p.x + 0.5f;
        float az = p.z + 0.5f;
        for (int k = 0; k < n; k++) {
            Level228PropMeshes.Mesh mesh = Level228PropMeshes.get(MESHES[k]);
            int emitBase = c.nextEmitN;
            for (int e = 0; e < mesh.emitters(); e++) {
                addEmitter(c, p, mesh, e, flip, rot, ax, az);
            }
            for (Level228PropMeshes.Part part : mesh.parts) {
                if (part.sheet >= bytes.length) {
                    continue;
                }
                if (part.layer == Level228PropMeshes.LAYER_CUT) {
                    emitStatic(j.builder(part.sheet), c, mesh, part, flip, rot, ax, p.y, az);
                } else if (part.layer == Level228PropMeshes.LAYER_GLOW) {
                    emitDynamic(c, mesh, part, flip, rot, ax, p.y, az, true, emitBase);
                } else {
                    emitDynamic(c, mesh, part, flip, rot, ax, p.y, az, false, emitBase);
                }
            }
        }
    }

    private static final float[] TMP = new float[6];

    /** Prop-local point and normal -> world: mirror across the pivot, quarter turns, anchor. */
    private static void transform(Level228PropMeshes.Mesh mesh, float[] pos, float[] nrm, int i, boolean flip, int rot,
                                  float ax, float ay, float az) {
        float lx = pos[i * 3];
        float ly = pos[i * 3 + 1];
        float lz = pos[i * 3 + 2];
        float nx = nrm == null ? 0 : nrm[i * 3];
        float ny = nrm == null ? 0 : nrm[i * 3 + 1];
        float nz = nrm == null ? 0 : nrm[i * 3 + 2];
        if (flip) {
            lx = 2.0f * mesh.pivotX - lx;
            nx = -nx;
        }
        float dx = lx - 0.5f;
        float dz = lz - 0.5f;
        float wx, wz, wnx, wnz;
        switch (rot) {
            case 1 -> { wx = -dz; wz = dx; wnx = -nz; wnz = nx; }
            case 2 -> { wx = -dx; wz = -dz; wnx = -nx; wnz = -nz; }
            case 3 -> { wx = dz; wz = -dx; wnx = nz; wnz = -nx; }
            default -> { wx = dx; wz = dz; wnx = nx; wnz = nz; }
        }
        TMP[0] = ax + wx;
        TMP[1] = ay + ly;
        TMP[2] = az + wz;
        TMP[3] = wnx;
        TMP[4] = ny;
        TMP[5] = wnz;
    }

    private static void emitStatic(BufferBuilder b, Chunk c, Level228PropMeshes.Mesh mesh, Level228PropMeshes.Part part,
                                   boolean flip, int rot, float ax, float ay, float az) {
        float ox = c.cx * 16;
        float oz = c.cz * 16;
        for (int v = 0; v < part.vertices; v++) {
            transform(mesh, part.pos, part.normal, v, flip, rot, ax, ay, az);
            float x = TMP[0], y = TMP[1], z = TMP[2], nx = TMP[3], ny = TMP[4], nz = TMP[5];
            int light = GRID.sample(x + nx * LIGHT_PROBE, y + ny * LIGHT_PROBE, z + nz * LIGHT_PROBE);
            int s = (int) (part.shade[v] * 255.0f);
            int colour = 0xFF000000 | s << 16 | s << 8 | s;
            b.addVertex(x - ox, y, z - oz, colour, part.uv[v * 2], part.uv[v * 2 + 1], OverlayTexture.NO_OVERLAY, light, nx, ny, nz);
            c.minY = Math.min(c.minY, y);
            c.maxY = Math.max(c.maxY, y);
        }
    }

    private static void emitDynamic(Chunk c, Level228PropMeshes.Mesh mesh, Level228PropMeshes.Part part, boolean flip, int rot,
                                    float ax, float ay, float az, boolean glow, int emitBase) {
        int n = glow ? c.nextGlowN : c.nextGlassN;
        int need = (n + part.vertices) * 9;
        if (glow) {
            if (c.glow.length < need) {
                c.glow = java.util.Arrays.copyOf(c.glow, Math.max(need, c.glow.length * 2));
                c.glowRef = java.util.Arrays.copyOf(c.glowRef, Math.max(n + part.vertices, c.glowRef.length * 2));
            }
        } else if (c.glass.length < need) {
            c.glass = java.util.Arrays.copyOf(c.glass, Math.max(need, c.glass.length * 2));
            c.glassSheet = java.util.Arrays.copyOf(c.glassSheet, Math.max(n + part.vertices, c.glassSheet.length * 2));
            c.glassLight = java.util.Arrays.copyOf(c.glassLight, Math.max(n + part.vertices, c.glassLight.length * 2));
        }
        float[] out = glow ? c.glow : c.glass;
        for (int v = 0; v < part.vertices; v++) {
            transform(mesh, part.pos, part.normal, v, flip, rot, ax, ay, az);
            int o = (n + v) * 9;
            System.arraycopy(TMP, 0, out, o, 3);
            out[o + 3] = part.uv[v * 2];
            out[o + 4] = part.uv[v * 2 + 1];
            out[o + 5] = TMP[3];
            out[o + 6] = TMP[4];
            out[o + 7] = TMP[5];
            out[o + 8] = part.shade[v];
            if (glow) {
                c.glowRef[n + v] = part.sheet << 16 | (emitBase + (part.emitter[v] & 0xFF));
            } else {
                c.glassSheet[n + v] = part.sheet;
                c.glassLight[n + v] = GRID.sample(TMP[0] + TMP[3] * LIGHT_PROBE, TMP[1] + TMP[4] * LIGHT_PROBE,
                        TMP[2] + TMP[5] * LIGHT_PROBE);
            }
        }
        if (glow) {
            c.nextGlowN = n + part.vertices;
        } else {
            c.nextGlassN = n + part.vertices;
        }
    }

    /**
     * One emitter: its world position and look. A prop with one emitter (a bulb, a sconce, a
     * sign) takes the light kind, state and phase the plan gave it; the facade's marquee has
     * dozens, each its own (most dead, some failing, few lit, more of them dead the worse the
     * marquee's state), worked out from the prop's noise so every copy in the mirrors agrees.
     */
    private static void addEmitter(Chunk c, Level228Plan.Prop p, Level228PropMeshes.Mesh mesh, int e, boolean flip, int rot,
                                   float ax, float az) {
        int n = c.nextEmitN;
        if (c.emitInfo.length <= n) {
            int cap = Math.max(16, c.emitInfo.length * 2);
            c.emit = java.util.Arrays.copyOf(c.emit, cap * 4);
            c.emitInfo = java.util.Arrays.copyOf(c.emitInfo, cap);
            c.emitLight = java.util.Arrays.copyOf(c.emitLight, cap);
        }
        transform(mesh, mesh.emitterPos, null, e, flip, rot, ax, p.y, az);
        c.emit[n * 4] = TMP[0];
        c.emit[n * 4 + 1] = TMP[1];
        c.emit[n * 4 + 2] = TMP[2];
        c.emit[n * 4 + 3] = mesh.emitterSize[e];
        int kind = p.flags >> Level228Plan.PF_LIGHT_KIND_SHIFT & 7;
        int state = p.flags >> Level228Plan.PF_LIGHT_STATE_SHIFT & 3;
        int phase = p.flags >> Level228Plan.PF_LIGHT_PHASE_SHIFT & 15;
        boolean lightProp = p.type == Level228Plan.P_BULB || p.type == Level228Plan.P_SCONCE || p.type == Level228Plan.P_EXIT_SIGN;
        if (!lightProp) {
            kind = mesh.emitterKind[e];
        }
        // Only the marquee's bulbs each have their own state; a chandelier's candles all follow
        // its one light (the exit puzzle's unlit candle must be all out, not half).
        if (mesh.emitters() > 1 && p.type == Level228Plan.P_FACADE) {
            int h = mix(p.noise * 31 + e);
            float roll = (h >>> 8 & 0xFFFF) / 65535.0f;
            float lit = state == Level228Plan.LIGHT_LIT ? 0.3f : state == Level228Plan.LIGHT_FAILING ? 0.1f : 0.0f;
            float failing = state == Level228Plan.LIGHT_DEAD ? 0.0f : 0.2f;
            state = roll < lit ? Level228Plan.LIGHT_LIT : roll < lit + failing ? Level228Plan.LIGHT_FAILING : Level228Plan.LIGHT_DEAD;
            phase = (phase + (h & 15)) & 15;
        }
        c.emitInfo[n] = kind | state << 4 | phase << 8;
        c.emitLight[n] = GRID.sample(TMP[0], TMP[1], TMP[2]);
        c.nextEmitN = n + 1;
    }

    private static int mix(int h) {
        h ^= h >>> 16;
        h *= 0x7FEB352D;
        h ^= h >>> 15;
        h *= 0x846CA68B;
        h ^= h >>> 16;
        return h;
    }

    /** A clear glass panel: drawn by the mirror pass from its plane and span (like a mirror). */
    private static void addPane(Chunk c, Level228Plan.Prop p) {
        int n = c.nextPaneN;
        if (c.paneNoise.length <= n) {
            int cap = Math.max(8, c.paneNoise.length * 2);
            c.pane = java.util.Arrays.copyOf(c.pane, cap * 6);
            c.paneNoise = java.util.Arrays.copyOf(c.paneNoise, cap);
        }
        // Local span x -0.5..2.5 (column centre to column centre) at z 0.5, turned like the mesh.
        float ax = p.x + 0.5f;
        float az = p.z + 0.5f;
        int rot = p.rot & 3;
        float a0, a1;
        int axis;
        float plane;
        switch (rot) {
            case 1 -> { axis = 0; plane = ax; a0 = az - 1.0f; a1 = az + 2.0f; }
            case 2 -> { axis = 1; plane = az; a0 = ax - 2.0f; a1 = ax + 1.0f; }
            case 3 -> { axis = 0; plane = ax; a0 = az - 2.0f; a1 = az + 1.0f; }
            default -> { axis = 1; plane = az; a0 = ax - 1.0f; a1 = ax + 2.0f; }
        }
        c.pane[n * 6] = axis;
        c.pane[n * 6 + 1] = plane;
        c.pane[n * 6 + 2] = a0;
        c.pane[n * 6 + 3] = a1;
        c.pane[n * 6 + 4] = p.y + 0.10f;
        c.pane[n * 6 + 5] = p.y + 2.92f;
        c.paneNoise[n] = p.noise;
        c.nextPaneN = n + 1;
    }

    // ---- Drawing ----------------------------------------------------------------------------------

    private static void collectDrawn(RenderLevelStageEvent event, Vec3 cam) {
        FrustumInvoker frustum = (FrustumInvoker) event.getFrustum();
        drawnCount = 0;
        for (int k = 0; k < allN; k++) {
            Chunk c = all[k];
            if (!c.built || c.minY > c.maxY && c.paneN == 0 && c.emitN == 0) {
                continue;
            }
            double x0 = c.cx * 16;
            double z0 = c.cz * 16;
            double ox = Math.max(Math.abs(x0 + 8 - cam.x) - 8, 0);
            double oz = Math.max(Math.abs(z0 + 8 - cam.z) - 8, 0);
            if (ox * ox + oz * oz > RADIUS * RADIUS) {
                continue;
            }
            double y0 = c.minY <= c.maxY ? c.minY : Level228Plan.FEET_Y;
            double y1 = c.minY <= c.maxY ? c.maxY : Level228Plan.CEILING_Y;
            if (!frustum.backrooms$cubeInFrustum(x0 - 1, y0 - 1, z0 - 1, x0 + 17, y1 + 1, z0 + 17)) {
                continue;
            }
            if (drawnCount == drawn.length) {
                drawn = java.util.Arrays.copyOf(drawn, drawn.length * 2);
            }
            drawn[drawnCount++] = c;
        }
    }

    private static void drawStatic(RenderLevelStageEvent event, Vec3 cam) {
        Matrix4f view = event.getModelViewMatrix();
        Matrix4f proj = event.getProjectionMatrix();
        for (int s = 0; s < cutTypes.length; s++) {
            boolean setUp = false;
            ShaderInstance shader = null;
            for (int i = 0; i < drawnCount; i++) {
                Chunk c = drawn[i];
                if (c.vbo == null || s >= c.vbo.length || c.vbo[s] == null) {
                    continue;
                }
                if (!setUp) {
                    cutTypes[s].setupRenderState();
                    shader = RenderSystem.getShader();
                    setUp = true;
                    if (shader == null) {
                        break;
                    }
                }
                MV.set(view).translate((float) (c.cx * 16 - cam.x), (float) -cam.y, (float) (c.cz * 16 - cam.z));
                c.vbo[s].bind();
                c.vbo[s].drawWithShader(MV, proj, shader);
            }
            if (setUp) {
                VertexBuffer.unbind();
                cutTypes[s].clearRenderState();
            }
        }
    }

    // Per-kind glow colours (Level228Bulbs' matching Veil lights): bulb, candle, marquee, sign, cold.
    private static final float[][] GLOW = {
            {1.0f, 0.70f, 0.43f}, {1.0f, 0.62f, 0.33f}, {1.0f, 0.72f, 0.45f}, {1.0f, 1.0f, 1.0f}, {0.74f, 0.84f, 1.0f}};
    private static final float DEAD_GLASS = 0.62f;

    /** Bulb glass and sign faces at this frame's brightness, then their halos (after translucent blocks). */
    private static void drawGlow(RenderLevelStageEvent event, ClientLevel level) {
        if (drawnCount == 0) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        long time = level.getGameTime();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long seed = builtSeed;
        boolean any = false;
        for (int i = 0; i < drawnCount; i++) {
            Chunk c = drawn[i];
            for (int e = 0; e < c.emitN; e++) {
                int info = c.emitInfo[e];
                c.emitGlow[e] = Level228Bulbs.glow(seed, info & 15, info >> 4 & 15, info >> 8 & 15, time, pt);
            }
            any |= c.glowN > 0 || c.emitN > 0;
        }
        if (!any) {
            return;
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        float cx = (float) cam.x;
        float cy = (float) cam.y;
        float cz = (float) cam.z;
        double r2 = GLOW_RADIUS * GLOW_RADIUS;
        for (int s = 0; s < cutTypes.length; s++) {
            VertexConsumer out = null;
            for (int i = 0; i < drawnCount; i++) {
                Chunk c = drawn[i];
                for (int v = 0; v < c.glowN; v += 4) {
                    int ref = c.glowRef[v];
                    if (ref >>> 16 != s) {
                        continue;
                    }
                    int o = v * 9;
                    float dx = c.glow[o] - cx, dy = c.glow[o + 1] - cy, dz = c.glow[o + 2] - cz;
                    if (dx * dx + dy * dy + dz * dz > r2) {
                        continue;
                    }
                    if (out == null) {
                        out = buffers.getBuffer(cutTypes[s]);
                    }
                    int e = ref & 0xFFFF;
                    float g = c.emitGlow[e];
                    int kind = c.emitInfo[e] & 15;
                    float[] col = GLOW[Math.min(kind, GLOW.length - 1)];
                    int light;
                    float r, gg, b;
                    if (g < 0.03f) {
                        // Dead: dusty grey glass in the room's own light.
                        light = c.emitLight[e];
                        r = gg = b = DEAD_GLASS;
                    } else {
                        light = LightTexture.FULL_BRIGHT;
                        float k = 0.3f + 0.7f * g;
                        r = col[0] * k;
                        gg = col[1] * k;
                        b = col[2] * k;
                    }
                    for (int q = 0; q < 4; q++) {
                        int oo = (v + q) * 9;
                        float sh = g < 0.03f ? c.glow[oo + 8] : 1.0f;
                        out.addVertex(c.glow[oo] - cx, c.glow[oo + 1] - cy, c.glow[oo + 2] - cz,
                                packColour(r * sh, gg * sh, b * sh, 1.0f), c.glow[oo + 3], c.glow[oo + 4],
                                OverlayTexture.NO_OVERLAY, light, c.glow[oo + 5], c.glow[oo + 6], c.glow[oo + 7]);
                    }
                }
            }
            if (out != null) {
                buffers.endBatch(cutTypes[s]);
            }
        }
        drawHalos(buffers, camera, cx, cy, cz, r2);
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    /** Soft additive glow round each lit emitter, facing the camera. */
    private static void drawHalos(MultiBufferSource.BufferSource buffers, Camera camera, float cx, float cy, float cz, double r2) {
        Level228PropMeshes.Region h = halo;
        if (h == null || h.sheet() >= haloTypes.length) {
            return;
        }
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        VertexConsumer out = null;
        for (int i = 0; i < drawnCount; i++) {
            Chunk c = drawn[i];
            for (int e = 0; e < c.emitN; e++) {
                float g = c.emitGlow[e];
                if (g < 0.03f) {
                    continue;
                }
                float x = c.emit[e * 4] - cx;
                float y = c.emit[e * 4 + 1] - cy;
                float z = c.emit[e * 4 + 2] - cz;
                if (x * x + y * y + z * z > r2) {
                    continue;
                }
                if (out == null) {
                    out = buffers.getBuffer(haloTypes[h.sheet()]);
                }
                float size = c.emit[e * 4 + 3] * (0.55f + 0.45f * g);
                float[] col = GLOW[Math.min(c.emitInfo[e] & 15, GLOW.length - 1)];
                // Signs glow red from their face (the texture is red); their halo is the red light.
                float r = (c.emitInfo[e] & 15) == Level228PropMeshes.EMIT_SIGN ? 1.0f : col[0];
                float gg = (c.emitInfo[e] & 15) == Level228PropMeshes.EMIT_SIGN ? 0.12f : col[1];
                float b = (c.emitInfo[e] & 15) == Level228PropMeshes.EMIT_SIGN ? 0.08f : col[2];
                float k = 0.5f * g;
                int colour = packColour(r * k, gg * k, b * k, 1.0f);
                float lx = left.x() * size, ly = left.y() * size, lz = left.z() * size;
                float ux = up.x() * size, uy = up.y() * size, uz = up.z() * size;
                halo(out, x + lx + ux, y + ly + uy, z + lz + uz, colour, h.u0(), h.v0());
                halo(out, x + lx - ux, y + ly - uy, z + lz - uz, colour, h.u0(), h.v1());
                halo(out, x - lx - ux, y - ly - uy, z - lz - uz, colour, h.u1(), h.v1());
                halo(out, x - lx + ux, y - ly + uy, z - lz + uz, colour, h.u1(), h.v0());
            }
        }
        if (out != null) {
            buffers.endBatch(haloTypes[h.sheet()]);
        }
    }

    private static void halo(VertexConsumer out, float x, float y, float z, int colour, float u, float v) {
        out.addVertex(x, y, z, colour, u, v, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0.0f, 1.0f, 0.0f);
    }

    static int packColour(float r, float g, float b, float a) {
        int ri = (int) (Math.min(1.0f, Math.max(0.0f, r)) * 255.0f);
        int gi = (int) (Math.min(1.0f, Math.max(0.0f, g)) * 255.0f);
        int bi = (int) (Math.min(1.0f, Math.max(0.0f, b)) * 255.0f);
        int ai = (int) (Math.min(1.0f, Math.max(0.0f, a)) * 255.0f);
        return ai << 24 | ri << 16 | gi << 8 | bi;
    }

    // ---- Light ------------------------------------------------------------------------------------

    /**
     * The block and sky light of the cells round a chunk, sampled once per build, and interpolated
     * smoothly between cell centres. Cells inside opaque blocks (walls, the sealed image volumes'
     * shells) take the brightest of their neighbours, so a prop touching a wall isn't black where
     * its probe dips into it.
     */
    static final class LightGrid {
        int ox, oy, oz, sx, sy, sz;
        float[] block = new float[0];
        float[] sky = new float[0];
        private boolean[] solid = new boolean[0];

        void fill(ClientLevel level, int ox, int oy, int oz, int sx, int sy, int sz) {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            int n = sx * sy * sz;
            if (block.length < n) {
                block = new float[n];
                sky = new float[n];
                solid = new boolean[n];
            }
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        int i = (x * sy + y) * sz + z;
                        POS.set(ox + x, oy + y, oz + z);
                        BlockState st = level.getBlockState(POS);
                        solid[i] = st.isSolidRender(level, POS);
                        block[i] = level.getBrightness(LightLayer.BLOCK, POS);
                        sky[i] = level.getBrightness(LightLayer.SKY, POS);
                    }
                }
            }
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        int i = (x * sy + y) * sz + z;
                        if (!solid[i]) {
                            continue;
                        }
                        float b = 0, s = 0;
                        for (int d = 0; d < 6; d++) {
                            int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
                            int ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0);
                            int nz = z + (d == 4 ? 1 : d == 5 ? -1 : 0);
                            if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) {
                                continue;
                            }
                            int j = (nx * sy + ny) * sz + nz;
                            if (!solid[j]) {
                                b = Math.max(b, block[j]);
                                s = Math.max(s, sky[j]);
                            }
                        }
                        block[i] = b;
                        sky[i] = s;
                    }
                }
            }
        }

        /** Packed lightmap coordinates at a world point (fractional light, as smooth lighting gives). */
        int sample(float wx, float wy, float wz) {
            float fx = wx - ox - 0.5f;
            float fy = wy - oy - 0.5f;
            float fz = wz - oz - 0.5f;
            int x0 = clamp((int) Math.floor(fx), sx - 2);
            int y0 = clamp((int) Math.floor(fy), sy - 2);
            int z0 = clamp((int) Math.floor(fz), sz - 2);
            float tx = Math.min(1.0f, Math.max(0.0f, fx - x0));
            float ty = Math.min(1.0f, Math.max(0.0f, fy - y0));
            float tz = Math.min(1.0f, Math.max(0.0f, fz - z0));
            float b = 0, s = 0;
            for (int c = 0; c < 8; c++) {
                int dx = c & 1, dy = c >> 1 & 1, dz = c >> 2 & 1;
                float w = (dx == 1 ? tx : 1 - tx) * (dy == 1 ? ty : 1 - ty) * (dz == 1 ? tz : 1 - tz);
                int i = ((x0 + dx) * sy + (y0 + dy)) * sz + (z0 + dz);
                b += block[i] * w;
                s += sky[i] * w;
            }
            int u = Math.min(240, Math.round(b * 16.0f));
            int v = Math.min(240, Math.round(s * 16.0f));
            return u | v << 16;
        }

        private static int clamp(int i, int max) {
            return i < 0 ? 0 : Math.min(i, Math.max(0, max));
        }
    }
}
