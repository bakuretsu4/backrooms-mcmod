package com.rickr.backrooms.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.Level227PlantBlock;
import com.rickr.backrooms.client.water.VanillaWaterHider;
import com.rickr.backrooms.registry.Level369Blocks;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.layout.Level369Plan;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Level 369's meadow up close as real grass: within {@value #RADIUS} blocks of the camera every
 * lawn column with a flower or tuft (world/layout/Level369Plan's FLOWER and TUFT) grows
 * {@value #BLADES} or so thin blades, each a tapered strip of three segments to a point, about
 * waist high, facing anywhere and curving over a little, shaded smoothly from a deep shadowed
 * green at the root to a sunlit yellow-green tip. No texture, so no pixelated cutout edges. Flower
 * columns add a few flower heads on stems near the top of the grass (blue cornflowers, red
 * daisies, pink cosmos, white daisies), and the buttercup tufts a few tiny yellow ones.
 *
 * <p>The meshes are built per 16 x 16 tile (a chunk) on a background thread, from the blocks and
 * their light at the feet layer, and seeded per column so nothing ever reshuffles; a tile is
 * rebuilt when those blocks or their light change. Shader: assets/backrooms/shaders/core/l369_grass
 * (the block meadow's wind, vanilla's lightmap and fog, and Veil's lights through its albedo and
 * normal markers). Past {@value #FULL} blocks the blades thin out (each blade has a random rank,
 * and the highest ranks sink into the lawn first) while the block meadow rises in their place
 * (assets/minecraft/shaders/core/rendertype_cutout_mipped.vsh, uniform MeadowFade), so the far
 * field stays the cheap panels with no seam. Each tile's blades are split by rank into
 * {@value #LAYERS} buffers, and a far tile skips the ones that would be sunk anyway.
 *
 * <p>Nothing is drawn (and the block meadow stays whole) under an Iris shader pack, which draws the
 * blocks its own way, under Sodium, whose chunk shaders never sink the block meadow, or until every
 * tile within reach has been built (just after arriving).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level369Grass {
    private static final Logger LOGGER = LoggerFactory.getLogger(Level369Grass.class);
    /** How far the blades reach from the camera (blocks); the block meadow is all there is beyond. */
    static final float RADIUS = 32.0f;
    /** Full density up to here; from here to {@link #RADIUS} the blades thin out and the panels rise. */
    static final float FULL = 20.0f;
    /** Tiles are built this far beyond the radius, so they are ready before they're needed ... */
    private static final float BUILD_MARGIN = 16.0f;
    /** ... and freed this far beyond it. */
    private static final float FORGET_MARGIN = 28.0f;
    /** Tiles building at once on the background thread. */
    private static final int MAX_BUILDING = 3;
    /** Tiles whose blocks and light are compared with what they were built from, per tick. */
    private static final int CHECKS_PER_TICK = 2;
    /** Blades per grass column (flower columns have fewer, plus the flowers). */
    private static final int BLADES = 20;
    /** The rank bands of the tile buffers: a buffer is drawn only where its lowest rank still shows. */
    private static final float[] LAYER_FROM = {0.0f, 0.3f, 0.6f};
    private static final int LAYERS = LAYER_FROM.length;
    /** Matches the shader: a blade shows while its rank is below density x this. */
    private static final float RANK_REACH = 1.2f;
    private static final int FEET_Y = Level369Plan.FEET_Y;
    private static final VertexFormat FORMAT = DefaultVertexFormat.POSITION_COLOR_LIGHTMAP;
    private static final boolean SODIUM = ModList.get().isLoaded("sodium") || ModList.get().isLoaded("embeddium");
    private static final long SALT = 0x369_6A55_B1ADL;

    // Blade colour from root to tip (as the block meadow's textures, tools/anomaly_assets/level369.py).
    private static final float[] FOOT = {34, 72, 30};
    private static final float[] MID = {76, 136, 50};
    private static final float[] TIP = {168, 200, 88};
    /** The stems' green, foot and top. */
    private static final float[] STEM_FOOT = {44, 84, 34};
    private static final float[] STEM_TOP = {86, 130, 54};
    /** Flower heads, per FLOWER kind: petal base, petal edge, centre; then TUFT kind 3's buttercups. */
    private static final float[][][] HEADS = {
            {{58, 76, 196}, {120, 156, 250}, {70, 40, 110}},     // blue cornflower
            {{176, 18, 34}, {236, 64, 70}, {92, 40, 18}},        // red daisy
            {{214, 104, 158}, {248, 184, 214}, {236, 186, 56}},  // pink cosmos
            {{226, 222, 210}, {252, 250, 244}, {236, 186, 40}},  // white daisy
            {{232, 188, 30}, {252, 226, 80}, {200, 150, 20}},    // buttercup
    };
    /** Per head kind: petals, radius (blocks), cup (how far the petal tips rise), centre size (of the radius). */
    private static final float[][] HEAD_SHAPE = {
            {10, 0.115f, 0.45f, 0.22f},
            {16, 0.15f, 0.12f, 0.30f},
            {8, 0.16f, 0.18f, 0.24f},
            {16, 0.14f, 0.10f, 0.30f},
            {5, 0.055f, 0.35f, 0.30f},
    };
    private static final int BUTTERCUP = 4;

    @Nullable
    private static ShaderInstance shader;
    private static final Long2ObjectOpenHashMap<Tile> TILES = new Long2ObjectOpenHashMap<>();
    private static final ConcurrentLinkedQueue<Built> FINISHED = new ConcurrentLinkedQueue<>();
    private static int building;
    /** Drawing this frame: every tile within reach is built (the block meadow sinks near the camera). */
    private static boolean active;
    private static int checkCursor;

    private Level369Grass() {}

    /** Mod bus: the grass shader. If it fails to load, the block meadow stays as it is. */
    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        shader = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Backrooms.id("l369_grass"), FORMAT), s -> shader = s);
        } catch (Exception e) {
            LOGGER.error("[{}] Level 369 grass shader failed to load; the meadow keeps its block panels", Backrooms.MOD_ID, e);
        }
    }

    // ---- Tiles ---------------------------------------------------------------------------------------

    private static final class Tile {
        final int cx;
        final int cz;
        /** By rank band; all null until first built (or where a band is empty). */
        final VertexBuffer[] layers = new VertexBuffer[LAYERS];
        boolean built;
        boolean building;
        /** The blocks and light the meshes were built from. */
        long signature;

        Tile(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
        }

        void close() {
            for (int i = 0; i < LAYERS; i++) {
                if (layers[i] != null) {
                    layers[i].close();
                    layers[i] = null;
                }
            }
            built = false;
        }
    }

    /** What a tile is built from: per column (x + 16 z) 0, or 1 | kind << 2 (flower), 2 | kind << 2 (tuft); and its light. */
    private record Snapshot(int[] plants, int[] light, long signature) {}

    private record Built(Tile tile, long signature, MeshData[] meshes, ByteBufferBuilder[] memory) {
        void free() {
            for (int i = 0; i < LAYERS; i++) {
                if (meshes[i] != null) {
                    meshes[i].close();
                }
                memory[i].close();
            }
        }
    }

    private static boolean enabled(Minecraft mc) {
        return shader != null && !SODIUM && mc.player != null && Level369Atmosphere.inLevel369() && !VanillaWaterHider.shaderPackInUse();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled(mc)) {
            releaseAll();
            return;
        }
        ClientLevel level = mc.level;
        upload();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        int reach = (int) Math.ceil((RADIUS + FORGET_MARGIN) / 16.0f) + 1;
        int ccx = Mth.floor(cam.x) >> 4;
        int ccz = Mth.floor(cam.z) >> 4;
        boolean ready = true;
        List<Tile> wanted = new ArrayList<>();
        for (int cx = ccx - reach; cx <= ccx + reach; cx++) {
            for (int cz = ccz - reach; cz <= ccz + reach; cz++) {
                float near = nearest(cam, cx, cz);
                if (near > RADIUS + BUILD_MARGIN || !level.hasChunk(cx, cz)) {
                    continue;
                }
                long key = ChunkPos.asLong(cx, cz);
                Tile tile = TILES.get(key);
                if (tile == null) {
                    tile = new Tile(cx, cz);
                    TILES.put(key, tile);
                }
                if (!tile.built) {
                    ready &= near > RADIUS;
                    if (!tile.building) {
                        wanted.add(tile);
                    }
                }
            }
        }
        wanted.sort(Comparator.comparingDouble(t -> nearest(cam, t.cx, t.cz)));
        for (Tile tile : wanted) {
            if (building >= MAX_BUILDING) {
                break;
            }
            Snapshot snap = snapshot(level, tile.cx, tile.cz);
            if (snap != null) {
                schedule(tile, snap);
            }
        }
        // Built tiles: compare a couple a tick with their blocks and light now; forget the far ones.
        List<Tile> all = new ArrayList<>(TILES.values());
        for (int i = 0; i < Math.min(CHECKS_PER_TICK, all.size()); i++) {
            checkCursor = (checkCursor + 1) % all.size();
            Tile tile = all.get(checkCursor);
            if (tile.built && !tile.building) {
                Snapshot snap = snapshot(level, tile.cx, tile.cz);
                if (snap != null && snap.signature() != tile.signature) {
                    schedule(tile, snap);
                }
            }
        }
        for (Iterator<Tile> it = TILES.values().iterator(); it.hasNext(); ) {
            Tile tile = it.next();
            if (nearest(cam, tile.cx, tile.cz) > RADIUS + FORGET_MARGIN || !level.hasChunk(tile.cx, tile.cz)) {
                tile.close();
                it.remove();
            }
        }
        active = ready;
    }

    /** Horizontal distance from the camera to the nearest point of tile (cx, cz). */
    private static float nearest(Vec3 cam, int cx, int cz) {
        double dx = Math.max(Math.max((cx << 4) - cam.x, cam.x - ((cx << 4) + 16)), 0.0);
        double dz = Math.max(Math.max((cz << 4) - cam.z, cam.z - ((cz << 4) + 16)), 0.0);
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    @Nullable
    private static Snapshot snapshot(ClientLevel level, int cx, int cz) {
        LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
        if (chunk == null) {
            return null;
        }
        int[] plants = new int[256];
        int[] light = new int[256];
        long sig = 0x369L;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 256; i++) {
            pos.set((cx << 4) + (i & 15), FEET_Y, (cz << 4) + (i >> 4));
            BlockState state = chunk.getBlockState(pos);
            int code = 0;
            if (state.is(Level369Blocks.FLOWER.get())) {
                code = 1 | state.getValue(Level227PlantBlock.KIND) << 2;
            } else if (state.is(Level369Blocks.TUFT.get())) {
                code = 2 | state.getValue(Level227PlantBlock.KIND) << 2;
            }
            if (code != 0) {
                plants[i] = code;
                light[i] = LevelRenderer.getLightColor(level, pos);
            }
            sig = Hash.mix(sig + code * 0x9E3779B97F4A7C15L + light[i]);
        }
        return new Snapshot(plants, light, sig);
    }

    private static void schedule(Tile tile, Snapshot snap) {
        tile.building = true;
        building++;
        CompletableFuture.runAsync(() -> FINISHED.add(build(tile, snap)), Util.backgroundExecutor()).exceptionally(e -> {
            LOGGER.error("[{}] Level 369 grass tile {} {} failed to build", Backrooms.MOD_ID, tile.cx, tile.cz, e);
            FINISHED.add(new Built(tile, snap.signature(), new MeshData[LAYERS], emptyMemory()));
            return null;
        });
    }

    private static ByteBufferBuilder[] emptyMemory() {
        ByteBufferBuilder[] memory = new ByteBufferBuilder[LAYERS];
        for (int i = 0; i < LAYERS; i++) {
            memory[i] = new ByteBufferBuilder(64);
        }
        return memory;
    }

    /** Render thread: hands finished meshes to their tiles (or drops them if the tile is gone). */
    private static void upload() {
        Built b;
        while ((b = FINISHED.poll()) != null) {
            building--;
            Tile tile = b.tile();
            tile.building = false;
            if (TILES.get(ChunkPos.asLong(tile.cx, tile.cz)) != tile) {
                b.free();
                continue;
            }
            tile.close();
            for (int i = 0; i < LAYERS; i++) {
                MeshData mesh = b.meshes()[i];
                if (mesh != null) {
                    VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    vb.bind();
                    vb.upload(mesh);
                    tile.layers[i] = vb;
                }
                b.memory()[i].close();
            }
            VertexBuffer.unbind();
            tile.signature = b.signature();
            tile.built = true;
        }
    }

    private static void releaseAll() {
        active = false;
        if (!TILES.isEmpty()) {
            for (Tile tile : TILES.values()) {
                tile.close();
            }
            TILES.clear();
        }
        // Builds still running come back to tiles that are gone and are dropped then.
        Built b;
        while ((b = FINISHED.poll()) != null) {
            building--;
            b.free();
        }
    }

    // ---- Drawing -------------------------------------------------------------------------------------

    /** Before the frame: tell the block meadow whether to sink near the camera. */
    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        ShaderInstance cutout = GameRenderer.getRendertypeCutoutMippedShader();
        if (cutout != null) {
            boolean on = active && enabled(Minecraft.getInstance());
            cutout.safeGetUniform("MeadowFade").set(on ? RADIUS : 0.0f, RADIUS - FULL);
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS || !active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ShaderInstance s = shader;
        if (s == null || !enabled(mc)) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        mc.gameRenderer.lightTexture().turnOnLightLayer();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        s.setDefaultUniforms(VertexFormat.Mode.QUADS, event.getModelViewMatrix(), event.getProjectionMatrix(), mc.getWindow());
        s.safeGetUniform("Meadow").set(RADIUS, FULL);
        s.apply();
        for (Tile tile : TILES.values()) {
            if (!tile.built) {
                continue;
            }
            float near = nearest(cam, tile.cx, tile.cz);
            if (near >= RADIUS) {
                continue;
            }
            int x = tile.cx << 4;
            int z = tile.cz << 4;
            if (!event.getFrustum().isVisible(new AABB(x - 1, FEET_Y - 0.1, z - 1, x + 17, FEET_Y + 1.8, z + 17))) {
                continue;
            }
            float t = Mth.clamp((near - FULL) / (RADIUS - FULL), 0.0f, 1.0f);
            float density = 1.0f - t * t * (3.0f - 2.0f * t);
            if (s.CHUNK_OFFSET != null) {
                s.CHUNK_OFFSET.set((float) (x - cam.x), (float) (FEET_Y - cam.y), (float) (z - cam.z));
                s.CHUNK_OFFSET.upload();
            }
            for (int i = 0; i < LAYERS; i++) {
                if (tile.layers[i] != null && density * RANK_REACH > LAYER_FROM[i]) {
                    tile.layers[i].bind();
                    tile.layers[i].draw();
                }
            }
        }
        VertexBuffer.unbind();
        s.clear();
        RenderSystem.enableCull();
        mc.gameRenderer.lightTexture().turnOffLightLayer();
    }

    // ---- Building (background thread) -----------------------------------------------------------------

    private static Built build(Tile tile, Snapshot snap) {
        ByteBufferBuilder[] memory = new ByteBufferBuilder[LAYERS];
        BufferBuilder[] out = new BufferBuilder[LAYERS];
        for (int i = 0; i < LAYERS; i++) {
            memory[i] = new ByteBufferBuilder(i == 0 ? 512 * 1024 : 256 * 1024);
            out[i] = new BufferBuilder(memory[i], VertexFormat.Mode.QUADS, FORMAT);
        }
        Rng rng = new Rng();
        for (int i = 0; i < 256; i++) {
            int code = snap.plants()[i];
            if (code != 0) {
                column(out, rng, tile.cx << 4, tile.cz << 4, i & 15, i >> 4, code, snap.light()[i]);
            }
        }
        MeshData[] meshes = new MeshData[LAYERS];
        for (int i = 0; i < LAYERS; i++) {
            meshes[i] = out[i].build();
        }
        return new Built(tile, snap.signature(), meshes, memory);
    }

    /** One lawn column, local (lx, lz) in the tile at (x0, z0). */
    private static void column(BufferBuilder[] out, Rng rng, int x0, int z0, int lx, int lz, int code, int light) {
        int wx = x0 + lx;
        int wz = z0 + lz;
        rng.seed(Hash.of(SALT, wx, wz, code));
        boolean flower = (code & 3) == 1;
        int kind = code >> 2;
        // Broad, gentle swells in the meadow's height, so the field isn't a flat-topped carpet.
        float swell = 0.86f + 0.26f * swell(wx, wz);
        float scale = swell * (!flower && kind == 0 ? 0.9f : !flower && kind == 1 ? 1.08f : 1.0f);
        int blades = flower ? BLADES - 4 : !flower && kind == 1 ? BLADES + 3 : BLADES;
        for (int b = 0; b < blades; b++) {
            blade(out, rng, lx + rng.next(), lz + rng.next(), scale, light);
        }
        if (flower) {
            int heads = kind == 1 ? 3 : 4;
            for (int h = 0; h < heads; h++) {
                float height = (1.0f + 0.28f * rng.next()) * swell;
                flowerHead(out[0], rng, lx + 0.15f + 0.7f * rng.next(), lz + 0.15f + 0.7f * rng.next(), height, kind, 0.25f * rng.next(), light);
            }
        } else if (kind == 3) {
            int heads = 2 + (rng.next() < 0.5f ? 1 : 0);
            for (int h = 0; h < heads; h++) {
                float height = (0.55f + 0.35f * rng.next()) * swell;
                flowerHead(out[0], rng, lx + 0.15f + 0.7f * rng.next(), lz + 0.15f + 0.7f * rng.next(), height, BUTTERCUP, 0.3f * rng.next(), light);
            }
        }
    }

    private static final float[] LEVELS = {0.0f, 0.38f, 0.72f};

    /** One blade from root (x, z): three segments up to a point, curving over along its face. */
    private static void blade(BufferBuilder[] out, Rng rng, float x, float z, float scale, int light) {
        float rank = rng.next();
        BufferBuilder buf = out[rank < LAYER_FROM[1] ? 0 : rank < LAYER_FROM[2] ? 1 : 2];
        float height = (0.9f + 0.4f * rng.next()) * scale;
        if (rng.next() < 0.22f) {
            height *= 0.55f + 0.3f * rng.next(); // undergrowth
        }
        float width = 0.05f + 0.07f * rng.next();
        float angle = rng.next() * Mth.TWO_PI;
        float sx = Mth.cos(angle);
        float sz = Mth.sin(angle);
        // Curves over across its face, a little to one side or the other.
        float lean = (0.08f + 0.3f * rng.next()) * height * (rng.next() < 0.5f ? -1.0f : 1.0f);
        float nx = -sz * lean;
        float nz = sx * lean;
        // Colour: each blade a little brighter or darker, yellower or bluer; short ones sit in the
        // shade of the tall; faces turned toward the light a little brighter.
        float bright = (0.76f + 0.21f * rng.next()) * (0.78f + 0.22f * Math.min(height / 1.3f, 1.0f))
                * (0.84f + 0.16f * Math.abs(Mth.sin(angle - 0.7f)));
        float hue = rng.next() * 2.0f - 1.0f;
        int a = (int) (rank * 255.0f);
        float px = 0, py = 0, pz = 0, phw = 0;
        float[] c = new float[3];
        float[] pc = new float[3];
        for (int k = 0; k <= LEVELS.length; k++) {
            float t = k < LEVELS.length ? LEVELS[k] : 1.0f;
            float bend = t * t;
            float cx = x + nx * bend;
            float cy = height * t * (1.0f - 0.25f * Math.abs(lean / height) * bend);
            float cz = z + nz * bend;
            float hw = 0.5f * width * (1.0f - (float) Math.pow(t, 2.2));
            gradient(t, bright, hue, c);
            if (k > 0) {
                if (k < LEVELS.length) {
                    quad(buf, px - sx * phw, py, pz - sz * phw, pc, px + sx * phw, py, pz + sz * phw, pc,
                            cx + sx * hw, cy, cz + sz * hw, c, cx - sx * hw, cy, cz - sz * hw, c, a, light);
                } else {
                    quad(buf, px - sx * phw, py, pz - sz * phw, pc, px + sx * phw, py, pz + sz * phw, pc,
                            cx, cy, cz, c, cx, cy, cz, c, a, light);
                }
            }
            px = cx;
            py = cy;
            pz = cz;
            phw = hw;
            System.arraycopy(c, 0, pc, 0, 3);
        }
    }

    /** Root (t 0) to tip (t 1): shadowed green, fresh green, sunlit yellow-green. */
    private static void gradient(float t, float bright, float hue, float[] out) {
        float[] from = t < 0.5f ? FOOT : MID;
        float[] to = t < 0.5f ? MID : TIP;
        float u = t < 0.5f ? t * 2.0f : (t - 0.5f) * 2.0f;
        for (int i = 0; i < 3; i++) {
            out[i] = Mth.lerp(u, from[i], to[i]) * bright;
        }
        out[0] *= 1.0f + 0.10f * hue * t;
        out[2] *= 1.0f - 0.12f * hue;
    }

    /** A flower on its stem from (x, z), the head at {@code height}. */
    private static void flowerHead(BufferBuilder buf, Rng rng, float x, float z, float height, int kind, float rank, int light) {
        int a = (int) (rank * 255.0f);
        float[][] colours = HEADS[kind];
        float[] shape = HEAD_SHAPE[kind];
        float r = shape[1] * (0.85f + 0.3f * rng.next());
        // The stem leans a little; the head nods over the same way.
        float leanAngle = rng.next() * Mth.TWO_PI;
        float lean = (0.04f + 0.1f * rng.next()) * height;
        float hx = x + Mth.cos(leanAngle) * lean;
        float hz = z + Mth.sin(leanAngle) * lean;
        float stemW = kind == BUTTERCUP ? 0.012f : 0.02f;
        float side = leanAngle + Mth.HALF_PI;
        float wx = Mth.cos(side) * stemW;
        float wz = Mth.sin(side) * stemW;
        float mx = x + (hx - x) * 0.3f;
        float mz = z + (hz - z) * 0.3f;
        float my = height * 0.55f;
        float[] midC = mix(STEM_FOOT, STEM_TOP, 0.55f);
        quad(buf, x - wx, 0, z - wz, STEM_FOOT, x + wx, 0, z + wz, STEM_FOOT, mx + wx, my, mz + wz, midC, mx - wx, my, mz - wz, midC, a, light);
        quad(buf, mx - wx, my, mz - wz, midC, mx + wx, my, mz + wz, midC, hx + wx * 0.7f, height, hz + wz * 0.7f, STEM_TOP,
                hx - wx * 0.7f, height, hz - wz * 0.7f, STEM_TOP, a, light);
        // The head's frame: facing up, tipped toward the lean by 0.2-0.7 radians.
        float tilt = 0.2f + 0.5f * rng.next();
        float ux = Mth.cos(leanAngle) * Mth.sin(tilt);
        float uy = Mth.cos(tilt);
        float uz = Mth.sin(leanAngle) * Mth.sin(tilt);
        // e1: horizontal, across the lean; e2 = up x e1.
        float e1x = -Mth.sin(leanAngle);
        float e1z = Mth.cos(leanAngle);
        float e2x = uy * e1z;
        float e2y = uz * e1x - ux * e1z;
        float e2z = -uy * e1x;
        int petals = (int) shape[0];
        float cup = shape[2] * r;
        float spin = rng.next() * Mth.TWO_PI;
        float bright = 0.9f + 0.15f * rng.next();
        float[] base = scale(colours[0], bright);
        float[] edge = scale(colours[1], bright);
        float halfWidth = Mth.PI / petals * (kind == 2 || kind == BUTTERCUP ? 1.0f : 0.9f);
        for (int p = 0; p < petals; p++) {
            float ang = spin + p * Mth.TWO_PI / petals + (rng.next() - 0.5f) * 0.15f;
            float len = r * (0.88f + 0.24f * rng.next());
            // Five corners: from the centre out to its widest, then a rounded tip.
            float[] tip = petalPoint(hx, height, hz, ux, uy, uz, e1x, e1z, e2x, e2y, e2z, ang, len, cup);
            float[] tipLeft = petalPoint(hx, height, hz, ux, uy, uz, e1x, e1z, e2x, e2y, e2z, ang - halfWidth * 0.6f, len * 0.92f, cup * 0.9f);
            float[] tipRight = petalPoint(hx, height, hz, ux, uy, uz, e1x, e1z, e2x, e2y, e2z, ang + halfWidth * 0.6f, len * 0.92f, cup * 0.9f);
            float[] left = petalPoint(hx, height, hz, ux, uy, uz, e1x, e1z, e2x, e2y, e2z, ang - halfWidth, len * 0.5f, cup * 0.35f);
            float[] right = petalPoint(hx, height, hz, ux, uy, uz, e1x, e1z, e2x, e2y, e2z, ang + halfWidth, len * 0.5f, cup * 0.35f);
            float[] inner = petalPoint(hx, height, hz, ux, uy, uz, e1x, e1z, e2x, e2y, e2z, ang, len * 0.12f, 0.0f);
            float[] sideC = mix(base, edge, 0.45f);
            quad(buf, inner[0], inner[1], inner[2], base, left[0], left[1], left[2], sideC, tipLeft[0], tipLeft[1], tipLeft[2], edge,
                    tip[0], tip[1], tip[2], edge, a, light);
            quad(buf, inner[0], inner[1], inner[2], base, tip[0], tip[1], tip[2], edge, tipRight[0], tipRight[1], tipRight[2], edge,
                    right[0], right[1], right[2], sideC, a, light);
        }
        // The centre: a small raised disc, darker at its rim.
        float cr = r * shape[3];
        float lift = r * 0.12f;
        float[] centre = scale(colours[2], bright);
        float[] rim = scale(colours[2], bright * 0.7f);
        float[] top = {hx + ux * lift, height + uy * lift, hz + uz * lift};
        int segments = 7;
        for (int k = 0; k < segments; k++) {
            float a0 = k * Mth.TWO_PI / segments;
            float a1 = (k + 1) * Mth.TWO_PI / segments;
            float[] p0 = petalPoint(hx, height, hz, ux, uy, uz, e1x, e1z, e2x, e2y, e2z, a0, cr, lift * 0.3f);
            float[] p1 = petalPoint(hx, height, hz, ux, uy, uz, e1x, e1z, e2x, e2y, e2z, a1, cr, lift * 0.3f);
            quad(buf, top[0], top[1], top[2], centre, p0[0], p0[1], p0[2], rim, p1[0], p1[1], p1[2], rim, top[0], top[1], top[2], centre, a, light);
        }
    }

    /** A point of a head: {@code len} out from its centre at angle {@code ang} in its plane, raised {@code up} along its axis. */
    private static float[] petalPoint(float cx, float cy, float cz, float ux, float uy, float uz, float e1x, float e1z,
                                      float e2x, float e2y, float e2z, float ang, float len, float up) {
        float c = Mth.cos(ang) * len;
        float s = Mth.sin(ang) * len;
        return new float[] {cx + e1x * c + e2x * s + ux * up, cy + e2y * s + uy * up, cz + e1z * c + e2z * s + uz * up};
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[] {Mth.lerp(t, a[0], b[0]), Mth.lerp(t, a[1], b[1]), Mth.lerp(t, a[2], b[2])};
    }

    private static float[] scale(float[] a, float s) {
        return new float[] {a[0] * s, a[1] * s, a[2] * s};
    }

    private static void quad(BufferBuilder buf, float x0, float y0, float z0, float[] c0, float x1, float y1, float z1, float[] c1,
                             float x2, float y2, float z2, float[] c2, float x3, float y3, float z3, float[] c3, int a, int light) {
        vertex(buf, x0, y0, z0, c0, a, light);
        vertex(buf, x1, y1, z1, c1, a, light);
        vertex(buf, x2, y2, z2, c2, a, light);
        vertex(buf, x3, y3, z3, c3, a, light);
    }

    private static void vertex(BufferBuilder buf, float x, float y, float z, float[] c, int a, int light) {
        buf.addVertex(x, y, z).setColor(channel(c[0]), channel(c[1]), channel(c[2]), a).setLight(light);
    }

    private static int channel(float v) {
        return Mth.clamp(Math.round(v), 0, 255);
    }

    /** Smooth value noise in [0, 1) on a 7-block lattice. */
    private static float swell(int x, int z) {
        float fx = x / 7.0f;
        float fz = z / 7.0f;
        int ix = Mth.floor(fx);
        int iz = Mth.floor(fz);
        float tx = fx - ix;
        float tz = fz - iz;
        tx = tx * tx * (3.0f - 2.0f * tx);
        tz = tz * tz * (3.0f - 2.0f * tz);
        float a = (float) Hash.unit(SALT, ix, iz, 1);
        float b = (float) Hash.unit(SALT, ix + 1, iz, 1);
        float c = (float) Hash.unit(SALT, ix, iz + 1, 1);
        float d = (float) Hash.unit(SALT, ix + 1, iz + 1, 1);
        return Mth.lerp(tz, Mth.lerp(tx, a, b), Mth.lerp(tx, c, d));
    }

    /** SplitMix64: cheap, and reseeded per column so every column always grows the same. */
    private static final class Rng {
        private long state;

        void seed(long seed) {
            state = seed;
        }

        float next() {
            state += 0x9E3779B97F4A7C15L;
            return (Hash.mix(state) >>> 40) * 0x1.0p-24f;
        }
    }
}
