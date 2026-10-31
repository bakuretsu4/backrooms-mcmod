package com.rickr.backrooms.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.Level490Blocks;
import com.rickr.backrooms.world.layout.Level490Layout;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * The colossal chains above the top of the world (y 512), near you: blocks stop there, so from the
 * top of the spire the chains would end over your head. They go on up here, link for link as the
 * blocks would be (world/layout/Level490Layout#chainIron), built from the chain-iron block's own
 * texture and drawn as solid blocks are (after the play-test: drawn in the sky they looked
 * "translucent"), up to the storm's floor (y {@value #SOLID_TOP}); above that, inside the storm,
 * {@link Level490SkyChains} carries them on.
 *
 * <p>Each chain is built once into a vertex buffer (only the faces that can be seen) and kept while
 * you are near it.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level490ChainTops {
    /** Where the solid chains end (the storm's floor; Level490SkyChains draws on from there). */
    static final int SOLID_TOP = 600;
    private static final int FROM = Level490Layout.WORLD_HEIGHT;

    private static final Long2ObjectOpenHashMap<VertexBuffer> BUILT = new Long2ObjectOpenHashMap<>();
    private static long builtSeed;

    private Level490ChainTops() {}

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return;
        }
        if (!Level490Atmosphere.inLevel490() || !Level490ClientState.hasSeed()) {
            clear();
            return;
        }
        if (builtSeed != Level490ClientState.seed()) {
            clear();
            builtSeed = Level490ClientState.seed();
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        double reach = mc.options.getEffectiveRenderDistance() * 16.0 + 40;
        LongOpenHashSet near = new LongOpenHashSet();
        Matrix4f base = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(event.getPoseStack().last().pose());
        RenderType solid = RenderType.solid();
        boolean setUp = false;
        for (int[] c : Level490ClientState.chains(cam.x, cam.z)) {
            if (Math.hypot(c[0] - cam.x, c[1] - cam.z) > reach) {
                continue;
            }
            long k = key(c[0], c[1]);
            near.add(k);
            VertexBuffer vb = BUILT.get(k);
            if (vb == null) {
                vb = build(mc, c[0], c[1], c[2]);
                BUILT.put(k, vb);
            }
            if (!setUp) {
                solid.setupRenderState();
                setUp = true;
            }
            Matrix4f mv = new Matrix4f(base).translate((float) (c[0] - cam.x), (float) (FROM - cam.y), (float) (c[1] - cam.z));
            vb.bind();
            vb.drawWithShader(mv, event.getProjectionMatrix(), GameRenderer.getRendertypeSolidShader());
        }
        if (setUp) {
            VertexBuffer.unbind();
            solid.clearRenderState();
        }
        // Let go of the chains left behind.
        BUILT.long2ObjectEntrySet().removeIf(e -> {
            if (!near.contains(e.getLongKey())) {
                e.getValue().close();
                return true;
            }
            return false;
        });
    }

    private static void clear() {
        for (VertexBuffer vb : BUILT.values()) {
            vb.close();
        }
        BUILT.clear();
    }

    /** The chain's iron from the top of the world to the storm, relative to (x, FROM, z). */
    private static VertexBuffer build(Minecraft mc, int x, int z, int phase) {
        int r = Level490Layout.CHAIN_REACH;
        LongOpenHashSet iron = new LongOpenHashSet();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                final int fx = dx;
                final int fz = dz;
                Level490Layout.chainIron(dx, dz, phase, FROM, SOLID_TOP, y -> iron.add(BlockPos.asLong(fx, y, fz)));
            }
        }
        TextureAtlasSprite sprite = mc.getBlockRenderer().getBlockModel(Level490Blocks.CHAIN_IRON.get().defaultBlockState()).getParticleIcon();
        // Lit as the open air beside the chain at the top of the world is.
        int light = LevelRenderer.getLightColor(mc.level, new BlockPos(x + r + 2, FROM - 1, z));
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        iron.forEach(p -> {
            int bx = BlockPos.getX(p);
            int by = BlockPos.getY(p) - FROM;
            int bz = BlockPos.getZ(p);
            int ay = BlockPos.getY(p);
            // Only the faces with air beside them (and none at the bottom: blocks are there).
            if (!iron.contains(BlockPos.asLong(bx, ay + 1, bz))) face(buf, sprite, light, bx, by, bz, 0, 1, 0, 1.0f);
            if (ay > FROM && !iron.contains(BlockPos.asLong(bx, ay - 1, bz))) face(buf, sprite, light, bx, by, bz, 0, -1, 0, 0.5f);
            if (!iron.contains(BlockPos.asLong(bx, ay, bz - 1))) face(buf, sprite, light, bx, by, bz, 0, 0, -1, 0.8f);
            if (!iron.contains(BlockPos.asLong(bx, ay, bz + 1))) face(buf, sprite, light, bx, by, bz, 0, 0, 1, 0.8f);
            if (!iron.contains(BlockPos.asLong(bx - 1, ay, bz))) face(buf, sprite, light, bx, by, bz, -1, 0, 0, 0.6f);
            if (!iron.contains(BlockPos.asLong(bx + 1, ay, bz))) face(buf, sprite, light, bx, by, bz, 1, 0, 0, 0.6f);
        });
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        MeshData mesh = buf.build();
        vb.bind();
        if (mesh != null) {
            vb.upload(mesh);
        }
        VertexBuffer.unbind();
        return vb;
    }

    /** One face of the unit block at (x, y, z), facing (nx, ny, nz), shaded as Minecraft shades block faces. */
    private static void face(BufferBuilder buf, TextureAtlasSprite s, int light, int x, int y, int z, int nx, int ny, int nz, float shade) {
        float[][] c;
        if (ny == 1) {
            c = new float[][] {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
        } else if (ny == -1) {
            c = new float[][] {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}};
        } else if (nz == -1) {
            c = new float[][] {{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}};
        } else if (nz == 1) {
            c = new float[][] {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}};
        } else if (nx == -1) {
            c = new float[][] {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}};
        } else {
            c = new float[][] {{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}};
        }
        float[][] uv = {{s.getU0(), s.getV1()}, {s.getU1(), s.getV1()}, {s.getU1(), s.getV0()}, {s.getU0(), s.getV0()}};
        for (int i = 0; i < 4; i++) {
            buf.addVertex(x + c[i][0], y + c[i][1], z + c[i][2])
                    .setColor(shade, shade, shade, 1.0f)
                    .setUv(uv[i][0], uv[i][1])
                    .setLight(light)
                    .setNormal(nx, ny, nz);
        }
    }
}
