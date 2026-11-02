package com.rickr.backrooms.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level0Stack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * The floors of Level 0's stack (world/layout/Level0Stack) that aren't there. Five are built; this
 * draws {@value #BELOW} more below them and {@value #ABOVE} above, and every one is a copy of a
 * real one. The blocks of three of the real floors (y35, y50 and y55; the entry floor and the
 * hidden room's floor are one-offs) are tessellated by Minecraft's own block renderer, with the
 * light, ambient occlusion and face culling they have where they really stand, into one vertex
 * buffer per floor and render type, and drawn again a story apart, taking turns so no two floors
 * in a row are the same. So from above and below they look like what they copy: carpet, light
 * panels (some dead), partitions with their baseboards, the odd chair. They darken the further
 * they are from the real floors, into black, and about one in four is dimmer, as if most of its
 * lights were out.
 *
 * <p>Built when the stack's chunks are in, and once more {@value #SETTLE_TICKS} ticks later (the
 * client's lighting arrives after the blocks). The client anomaly effect (SetPieceEffects.Stack)
 * tells this which stack is near every tick.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level0StackRenderer {
    static final int BELOW = 24;
    static final int ABOVE = 18;
    /** The real floors that are copied (carpet layers). */
    private static final int[] SOURCES = {35, 50, 55};
    /** Forget the stack when nothing has asked for it for this long (ticks). */
    private static final int FORGET_TICKS = 40;
    private static final int SETTLE_TICKS = 60;
    private static final RenderType[] TYPES = {RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout()};

    @Nullable
    private static AnomalySite site;
    private static long askedAt;
    @Nullable
    private static AnomalySite built;
    /** [source][render type]; null where a floor has nothing of that type. */
    @Nullable
    private static VertexBuffer[][] meshes;
    private static long builtAt;
    private static boolean settled;

    private Level0StackRenderer() {}

    /** Called every tick while a stack is near. */
    public static void near(AnomalySite stack) {
        site = stack;
        ClientLevel level = Minecraft.getInstance().level;
        askedAt = level == null ? 0 : level.getGameTime();
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        boolean onLevel0 = level != null && BackroomsLevel.fromDimension(level.dimension()) == BackroomsLevel.LEVEL_0;
        if (!onLevel0 || site == null || level.getGameTime() - askedAt > FORGET_TICKS) {
            release();
            site = null;
            return;
        }
        if (built == null || !built.equals(site)) {
            if (!loaded(level, site)) {
                return;
            }
            build(mc, level, site);
            settled = false;
        } else if (!settled && level.getGameTime() - builtAt >= SETTLE_TICKS) {
            build(mc, level, site);
            settled = true;
        }
        draw(event);
    }

    private static boolean loaded(ClientLevel level, AnomalySite s) {
        return level.hasChunk(s.minX() >> 4, s.minZ() >> 4) && level.hasChunk(s.maxX() >> 4, s.minZ() >> 4)
                && level.hasChunk(s.minX() >> 4, s.maxZ() >> 4) && level.hasChunk(s.maxX() >> 4, s.maxZ() >> 4);
    }

    private static void release() {
        if (meshes != null) {
            for (VertexBuffer[] row : meshes) {
                for (VertexBuffer vb : row) {
                    if (vb != null) {
                        vb.close();
                    }
                }
            }
            meshes = null;
        }
        built = null;
    }

    // ---- Building ------------------------------------------------------------------------------------

    private static void build(Minecraft mc, ClientLevel level, AnomalySite s) {
        release();
        BlockRenderDispatcher blocks = mc.getBlockRenderer();
        RandomSource random = RandomSource.create();
        PoseStack pose = new PoseStack();
        meshes = new VertexBuffer[SOURCES.length][TYPES.length];
        for (int i = 0; i < SOURCES.length; i++) {
            for (int t = 0; t < TYPES.length; t++) {
                meshes[i][t] = tessellate(blocks, level, s, SOURCES[i], TYPES[t], random, pose);
            }
        }
        built = s;
        builtAt = level.getGameTime();
    }

    /** The blocks of the real floor whose carpet is at y {@code f} (ring and outer wall), in one render type. */
    @Nullable
    private static VertexBuffer tessellate(BlockRenderDispatcher blocks, ClientLevel level, AnomalySite s, int f, RenderType type,
                                           RandomSource random, PoseStack pose) {
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, type.format());
        for (int x = s.minX(); x <= s.maxX(); x++) {
            for (int z = s.minZ(); z <= s.maxZ(); z++) {
                int[] l = Level0Stack.local(s, x, z);
                if (l == null || l[2] < Level0Stack.RING_MIN) {
                    continue;
                }
                for (int y = f; y < f + Level0Stack.STORY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getRenderShape() != RenderShape.MODEL) {
                        continue;
                    }
                    BakedModel model = blocks.getBlockModel(state);
                    ModelData data = model.getModelData(level, pos, state, level.getModelData(pos));
                    random.setSeed(state.getSeed(pos));
                    if (!model.getRenderTypes(state, random, data).contains(type)) {
                        continue;
                    }
                    pose.pushPose();
                    pose.translate(x - s.minX(), y - f, z - s.minZ());
                    blocks.renderBatched(state, pos, level, pose, buf, true, random, data, type);
                    pose.popPose();
                }
            }
        }
        MeshData mesh = buf.build();
        if (mesh == null) {
            return null;
        }
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(mesh);
        VertexBuffer.unbind();
        return vb;
    }

    // ---- Drawing -------------------------------------------------------------------------------------

    private static void draw(RenderLevelStageEvent event) {
        Vec3 cam = event.getCamera().getPosition();
        for (int t = 0; t < TYPES.length; t++) {
            RenderType type = TYPES[t];
            type.setupRenderState();
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                if (shader.CHUNK_OFFSET != null) {
                    shader.CHUNK_OFFSET.set(0.0f, 0.0f, 0.0f);
                }
                for (int k = 1; k <= BELOW; k++) {
                    copy(event, cam, shader, t, Level0Stack.LOWEST_REAL - k * Level0Stack.STORY, k, true);
                }
                for (int k = 1; k <= ABOVE; k++) {
                    copy(event, cam, shader, t, Level0Stack.HIGHEST_REAL_CEILING + 1 + (k - 1) * Level0Stack.STORY, k, false);
                }
            }
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            type.clearRenderState();
        }
        VertexBuffer.unbind();
    }

    /** One copied floor, its carpet at y {@code f}, {@code k} floors from the real ones. */
    private static void copy(RenderLevelStageEvent event, Vec3 cam, ShaderInstance shader, int t, int f, int k, boolean below) {
        VertexBuffer vb = meshes[Math.floorMod(k + (below ? 1 : 0), SOURCES.length)][t];
        if (vb == null) {
            return;
        }
        boolean dim = Hash.unit(Hash.mix(built.salt() ^ (f * 0x9E3779B97F4A7C15L))) < 0.25;
        float b = (float) Math.exp(-k / (below ? 9.0 : 7.0)) * (dim ? 0.35f : 1.0f);
        if (b < 0.03f) {
            return;
        }
        RenderSystem.setShaderColor(b, b, b, 1.0f);
        Matrix4f mv = new Matrix4f(event.getModelViewMatrix())
                .translate((float) (built.minX() - cam.x), (float) (f - cam.y), (float) (built.minZ() - cam.z));
        vb.bind();
        vb.drawWithShader(mv, event.getProjectionMatrix(), shader);
    }
}
