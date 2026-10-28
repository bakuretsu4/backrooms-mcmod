package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.layout.Level490Layout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Level 490's floating debris (after Farum Azula's): chunks of rock and broken masonry hanging in
 * the air between the islands, bobbing a little and slowly turning, from pebbles to boulders.
 * Not blocks: drawn each frame with the rock texture, lit like the level (moonlight, and lightning
 * through the lightmap) and fogged like everything else. Where each chunk floats depends only on
 * its place (and the layout seed, so none sit in an island, a bridge or a chain): the same chunks
 * every visit.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level490Debris {
    /** Chunks are placed one per cell (most cells), over this far around the camera. */
    private static final int CELL = 11;
    private static final int REACH = 230;
    /** Re-placed when the camera has moved this far. */
    private static final double RESCAN = 10.0;

    /** One chunk: centre, half size (x, y, z), the axis it turns about, turn speed and phase. */
    private record Chunk(double x, double y, double z, Vector3f half, Vector3f axis, float speed, float phase,
                         Vector3f half2, Vector3f offset2) {}

    private static final List<Chunk> CHUNKS = new ArrayList<>();
    private static double placedX = Double.NaN;
    private static double placedZ = Double.NaN;
    private static long placedSeed;

    private Level490Debris() {}

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || !Level490Atmosphere.inLevel490()
                || !Level490ClientState.hasSeed()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        place(cam);
        if (CHUNKS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        TextureAtlasSprite rock = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(Backrooms.id("block/l490_rock"));
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS);
        VertexConsumer out = buffers.getBuffer(type);
        double t = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
        // Lit by the level's ambient light (no block light): the lightmap's moonlight and lightning.
        int light = LightTexture.pack(0, 0);
        for (Chunk c : CHUNKS) {
            Quaternionf turn = new Quaternionf().fromAxisAngleRad(c.axis, (float) (t * c.speed + c.phase));
            float bob = (float) Math.sin(t * 0.35 + c.phase) * 0.6f;
            Vector3f centre = new Vector3f((float) (c.x - cam.x), (float) (c.y - cam.y) + bob, (float) (c.z - cam.z));
            box(out, pose, centre, c.half, turn, rock, light);
            Vector3f second = new Vector3f(c.offset2).rotate(turn).add(centre);
            box(out, pose, second, c.half2, turn, rock, light);
        }
        buffers.endBatch(type);
    }

    /** (Re)places the chunks around the camera once it has moved far enough. */
    private static void place(Vec3 cam) {
        long seed = Level490ClientState.seed();
        if (!Double.isNaN(placedX) && seed == placedSeed && Math.hypot(cam.x - placedX, cam.z - placedZ) < RESCAN) {
            return;
        }
        placedX = cam.x;
        placedZ = cam.z;
        placedSeed = seed;
        CHUNKS.clear();
        int c0 = Math.floorDiv((int) cam.x - REACH, CELL);
        int c1 = Math.floorDiv((int) cam.x + REACH, CELL);
        int d0 = Math.floorDiv((int) cam.z - REACH, CELL);
        int d1 = Math.floorDiv((int) cam.z + REACH, CELL);
        for (int i = c0; i <= c1; i++) {
            for (int j = d0; j <= d1; j++) {
                long h = Hash.of(seed, i, j, 4995);
                if (Hash.unit(h) > 0.5) {
                    continue;
                }
                double x = (i + Hash.unit(Hash.mix(h))) * CELL;
                double z = (j + Hash.unit(Hash.mix(h + 1))) * CELL;
                if (Math.hypot(x - cam.x, z - cam.z) > REACH) {
                    continue;
                }
                // Mostly near the islands' height, some far above and below.
                double u = Hash.unit(Hash.mix(h + 2));
                double y = Level490Layout.DECK_Y + (u - 0.5) * 2 * (u < 0.15 || u > 0.85 ? 120 : 45);
                // Sizes: many small, a few boulders, rarely a huge broken slab.
                double s = Hash.unit(Hash.mix(h + 3));
                float size = (float) (s < 0.6 ? 0.3 + s * 1.2 : s < 0.95 ? 1.0 + (s - 0.6) * 6 : 3.2 + (s - 0.95) * 50);
                if (!Level490Layout.openAir(seed, x, y, z, size * 2)) {
                    continue;
                }
                Vector3f half = new Vector3f(size * (0.6f + 0.6f * (float) Hash.unit(Hash.mix(h + 4))),
                        size * (0.5f + 0.5f * (float) Hash.unit(Hash.mix(h + 5))), size * (0.6f + 0.6f * (float) Hash.unit(Hash.mix(h + 6))));
                Vector3f half2 = new Vector3f(half).mul(0.55f + 0.3f * (float) Hash.unit(Hash.mix(h + 7)));
                Vector3f offset2 = new Vector3f(half.x * 0.7f, half.y * 0.5f, -half.z * 0.4f);
                Vector3f axis = new Vector3f((float) Hash.unit(Hash.mix(h + 8)) - 0.5f, (float) Hash.unit(Hash.mix(h + 9)) - 0.5f,
                        (float) Hash.unit(Hash.mix(h + 10)) - 0.5f).normalize();
                float speed = (0.02f + 0.08f * (float) Hash.unit(Hash.mix(h + 11))) / (0.6f + size * 0.4f);
                CHUNKS.add(new Chunk(x, y, z, half, axis, speed, (float) Hash.unit(Hash.mix(h + 12)) * 6.28f, half2, offset2));
            }
        }
    }

    private static final int[][] FACES = {
            {0, 1, 3, 2, 0}, {4, 6, 7, 5, 1}, {0, 4, 5, 1, 2}, {2, 3, 7, 6, 3}, {0, 2, 6, 4, 4}, {1, 5, 7, 3, 5}};
    private static final float[][] NORMALS = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};

    /** A box turned by {@code turn}, every face showing the whole rock texture. */
    private static void box(VertexConsumer out, PoseStack.Pose pose, Vector3f centre, Vector3f half, Quaternionf turn,
                            TextureAtlasSprite sprite, int light) {
        Vector3f[] corners = new Vector3f[8];
        for (int k = 0; k < 8; k++) {
            corners[k] = new Vector3f((k & 4) != 0 ? half.x : -half.x, (k & 2) != 0 ? half.y : -half.y, (k & 1) != 0 ? half.z : -half.z)
                    .rotate(turn).add(centre);
        }
        Matrix3f normalMatrix = new Matrix3f().rotation(turn);
        float[][] uv = {{sprite.getU0(), sprite.getV0()}, {sprite.getU1(), sprite.getV0()}, {sprite.getU1(), sprite.getV1()}, {sprite.getU0(), sprite.getV1()}};
        for (int f = 0; f < 6; f++) {
            Vector3f n = new Vector3f(NORMALS[f][0], NORMALS[f][1], NORMALS[f][2]).mul(normalMatrix);
            for (int v = 0; v < 4; v++) {
                Vector3f p = corners[FACES[f][v]];
                out.addVertex(pose, p.x, p.y, p.z)
                        .setColor(255, 255, 255, 255)
                        .setUv(uv[v][0], uv[v][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, n.x, n.y, n.z);
            }
        }
    }
}
