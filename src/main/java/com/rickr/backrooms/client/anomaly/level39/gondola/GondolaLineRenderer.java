package com.rickr.backrooms.client.anomaly.level39.gondola;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.anomaly.AnomalyClientState;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level39.gondola.GondolaBuild;
import com.rickr.backrooms.world.anomaly.level39.gondola.GondolaChair;
import com.rickr.backrooms.world.anomaly.level39.gondola.GondolaChairBlock;
import com.rickr.backrooms.world.anomaly.level39.gondola.GondolaLine;
import com.rickr.backrooms.world.anomaly.level39.gondola.GondolaRegistry;
import com.rickr.backrooms.world.anomaly.level9.Level9Sites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * The moving parts of a Crimson Field chairlift, drawn by the client (the station and the towers
 * are real blocks):
 * <ul>
 *   <li><b>The cable:</b> a thick dark steel rope round the bull wheel, along both lines from tower
 *       head to tower head, sagging in each span, and on past the last tower up into the fog.
 *       Drawn as a narrow textured ribbon that always turns its face to the camera.</li>
 *   <li><b>The chairs:</b> empty chairs every {@value #CHAIR_SPACING} blocks on both lines, drawn
 *       with the chair block's own model, each swaying a little on its grip. The whole loop creeps
 *       round slowly, up on the right, back down on the left.</li>
 *   <li><b>Tall tower tops:</b> Level 9 is only 64 blocks high, so whatever a tower has above that
 *       (lattice, crossarm, sheaves) is drawn here with the same block models.</li>
 * </ul>
 * All of it comes from the site the server sent ({@link GondolaLine}); the terrain fog fades it
 * like everything else, so it simply disappears into the mist.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class GondolaLineRenderer {
    private static final ResourceLocation CABLE_TEXTURE = Backrooms.id("textures/entity/l39_gondola_cable.png");
    /** Half the cable's drawn width (blocks): a fat steel rope. */
    private static final float CABLE_HALF_WIDTH = 0.045f;
    /** One repeat of the cable texture covers this much cable (blocks). */
    private static final double CABLE_TEXTURE_LENGTH = 1.5;
    /** The cable is drawn in straight pieces about this long (blocks). */
    private static final double PIECE = 2.0;
    /** Pieces of the half-turn round the bull wheel. */
    private static final int WHEEL_PIECES = 10;
    /** Nothing farther than this is drawn (the field's fog has long since swallowed it). */
    private static final double CABLE_DISTANCE = 96.0;
    private static final double CHAIR_DISTANCE = 56.0;
    private static final double TOWER_DISTANCE = 80.0;
    /** Distance between chairs along the line (blocks). */
    public static final double CHAIR_SPACING = 12.0;
    /** The first chairs hang just past the station's frame tower (local a). */
    private static final double CHAIRS_FROM_A = GondolaLine.FRAME_A + 3.0;
    /** How fast the loop creeps round (blocks per second): slow enough to doubt it moves at all. */
    private static final double CREEP_SPEED = 0.5;
    /** Swing of the empty chairs on their grips (degrees). */
    private static final double SWAY_PITCH = 2.2;
    private static final double SWAY_ROLL = 1.3;
    /** A passing chair is left out this close (blocks) to a chair someone is riding on the same line. */
    private static final double RIDE_GAP = CHAIR_SPACING / 2.0;
    private static final int MAX_RIDES = 8;
    private static final double[] RIDE_A = new double[MAX_RIDES];

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || BackroomsLevel.fromDimension(mc.level.dimension()) != BackroomsLevel.LEVEL_9) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean drew = false;
        AnomalySite current = AnomalyClientState.current();
        boolean currentListed = false;
        for (AnomalySite site : AnomalyClientState.nearby()) {
            currentListed |= site.equals(current);
            if (site.kind().equals(Level9Sites.GONDOLA)) {
                draw(mc, GondolaLine.of(site), cam, partial, event.getPoseStack(), buffers);
                drew = true;
            }
        }
        if (current != null && !currentListed && current.kind().equals(Level9Sites.GONDOLA)) {
            draw(mc, GondolaLine.of(current), cam, partial, event.getPoseStack(), buffers);
            drew = true;
        }
        if (drew) {
            buffers.endBatch(RenderType.entityCutoutNoCull(CABLE_TEXTURE));
            buffers.endBatch(Sheets.cutoutBlockSheet());
        }
    }

    private static void draw(Minecraft mc, GondolaLine line, Vec3 cam, float partial, PoseStack pose, MultiBufferSource buffers) {
        if (line.distanceTo(cam.x, cam.z) > CABLE_DISTANCE + 16.0) {
            return;
        }
        BlockPos station = line.stationChairPos();
        int light = LevelRenderer.getLightColor(mc.level, station);
        drawCable(line, cam, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(CABLE_TEXTURE)), light);
        BlockRenderDispatcher blocks = mc.getBlockRenderer();
        drawTowerTops(mc, line, cam, pose, buffers, blocks);
        drawChairs(mc, line, cam, partial, pose, buffers, blocks, light);
    }

    // ==========================================================================================
    // The cable
    // ==========================================================================================

    private static void drawCable(GondolaLine line, Vec3 cam, PoseStack pose, VertexConsumer out, int light) {
        PoseStack.Pose last = pose.last();
        double y = GondolaLine.STATION_CABLE_Y;
        // Round the back of the bull wheel: from the up line (right) to the down line (left).
        double v = 0.0;
        for (int i = 0; i < WHEEL_PIECES; i++) {
            double t0 = Math.PI * i / WHEEL_PIECES;
            double t1 = Math.PI * (i + 1) / WHEEL_PIECES;
            double a0 = -GondolaLine.LINE_HALF * Math.sin(t0);
            double s0 = GondolaLine.LINE_HALF * Math.cos(t0);
            double a1 = -GondolaLine.LINE_HALF * Math.sin(t1);
            double s1 = GondolaLine.LINE_HALF * Math.cos(t1);
            v = piece(out, last, cam, line.worldX(a0, s0), y, line.worldZ(a0, s0), line.worldX(a1, s1), y, line.worldZ(a1, s1), v, light);
        }
        // Both lines, knot to knot (the wheel, the frame tower, each tower, the end in the fog).
        for (int side = -1; side <= 1; side += 2) {
            double s = side * GondolaLine.LINE_HALF;
            double from = 0.0;
            v = 0.0;
            for (int k = 0; k <= line.towers + 1; k++) {
                double to = k <= line.towers ? line.towerA(k) : line.endA();
                int pieces = Math.max(1, (int) Math.ceil((to - from) / PIECE));
                for (int p = 0; p < pieces; p++) {
                    double a0 = from + (to - from) * p / pieces;
                    double a1 = from + (to - from) * (p + 1) / pieces;
                    v = piece(out, last, cam, line.worldX(a0, s), line.cableY(a0), line.worldZ(a0, s),
                            line.worldX(a1, s), line.cableY(a1), line.worldZ(a1, s), v, light);
                }
                from = to;
            }
        }
    }

    /** One straight piece of cable as a ribbon facing the camera; returns the texture v at its end. */
    private static double piece(VertexConsumer out, PoseStack.Pose last, Vec3 cam,
                                double wx0, double wy0, double wz0, double wx1, double wy1, double wz1, double v0, int light) {
        double dx = wx1 - wx0;
        double dy = wy1 - wy0;
        double dz = wz1 - wz0;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double v1 = v0 + length / CABLE_TEXTURE_LENGTH;
        float x0 = (float) (wx0 - cam.x);
        float y0 = (float) (wy0 - cam.y);
        float z0 = (float) (wz0 - cam.z);
        float x1 = (float) (wx1 - cam.x);
        float y1 = (float) (wy1 - cam.y);
        float z1 = (float) (wz1 - cam.z);
        double mx = (x0 + x1) * 0.5;
        double my = (y0 + y1) * 0.5;
        double mz = (z0 + z1) * 0.5;
        if (mx * mx + my * my + mz * mz > CABLE_DISTANCE * CABLE_DISTANCE || length < 1.0e-4) {
            return v1;
        }
        // Sideways = along the cable x toward the camera, so the ribbon always shows its face.
        double sx = dy * -mz - dz * -my;
        double sy = dz * -mx - dx * -mz;
        double sz = dx * -my - dy * -mx;
        double inv = CABLE_HALF_WIDTH / Math.max(1.0e-6, Math.sqrt(sx * sx + sy * sy + sz * sz));
        float ox = (float) (sx * inv);
        float oy = (float) (sy * inv);
        float oz = (float) (sz * inv);
        vertex(out, last, x0 + ox, y0 + oy, z0 + oz, 0.0f, (float) v0, light);
        vertex(out, last, x0 - ox, y0 - oy, z0 - oz, 1.0f, (float) v0, light);
        vertex(out, last, x1 - ox, y1 - oy, z1 - oz, 1.0f, (float) v1, light);
        vertex(out, last, x1 + ox, y1 + oy, z1 + oz, 0.0f, (float) v1, light);
        return v1;
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose last, float x, float y, float z, float u, float v, int light) {
        out.addVertex(last, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(last, 0.0f, 1.0f, 0.0f);
    }

    // ==========================================================================================
    // The chairs
    // ==========================================================================================

    private static void drawChairs(Minecraft mc, GondolaLine line, Vec3 cam, float partial, PoseStack pose,
                                   MultiBufferSource buffers, BlockRenderDispatcher blocks, int light) {
        int rides = ridesOn(mc, line, partial);
        double seconds = (mc.level.getGameTime() + partial) / 20.0;
        double shift = (seconds * CREEP_SPEED) % CHAIR_SPACING;
        double end = line.endA();
        BlockState empty = GondolaRegistry.CHAIR.get().defaultBlockState();
        for (int side = -1; side <= 1; side += 2) {
            boolean up = side > 0;
            double s = side * GondolaLine.LINE_HALF;
            Direction facing = up ? line.dir : line.dir.getOpposite();
            BlockState state = empty.setValue(GondolaChairBlock.FACING, facing);
            // Up the right-hand line, back down the left: the down line creeps toward the station.
            double offset = up ? shift : CHAIR_SPACING - shift;
            for (double a = CHAIRS_FROM_A + offset; a < end; a += CHAIR_SPACING) {
                double gx = line.worldX(a, s);
                double gy = line.cableY(a);
                double gz = line.worldZ(a, s);
                double dx = gx - cam.x;
                double dy = gy - cam.y;
                double dz = gz - cam.z;
                if (dx * dx + dy * dy + dz * dz > CHAIR_DISTANCE * CHAIR_DISTANCE || up && nearRide(a, rides)) {
                    continue;
                }
                float pitch = (float) (SWAY_PITCH * Math.sin(seconds * 0.8 + a * 0.37));
                float roll = (float) (SWAY_ROLL * Math.sin(seconds * 0.55 + a * 0.71 + side));
                pose.pushPose();
                pose.translate(dx, dy, dz);
                GondolaChairRenderer.drawChair(blocks, state, facing, pitch, roll, pose, buffers, light);
                pose.popPose();
            }
        }
    }

    /** Collects where seats people are riding are on this line (local a); returns how many. */
    private static int ridesOn(Minecraft mc, GondolaLine line, float partial) {
        int n = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (n < MAX_RIDES && entity instanceof GondolaChair chair) {
                GondolaLine ride = chair.line();
                if (ride != null && ride.anchorX == line.anchorX && ride.anchorZ == line.anchorZ) {
                    RIDE_A[n++] = ride.rideA(chair.rideTicks(partial));
                }
            }
        }
        return n;
    }

    private static boolean nearRide(double a, int rides) {
        for (int i = 0; i < rides; i++) {
            if (Math.abs(a - RIDE_A[i]) < RIDE_GAP) {
                return true;
            }
        }
        return false;
    }

    // ==========================================================================================
    // Tower tops above the world's height limit
    // ==========================================================================================

    private static void drawTowerTops(Minecraft mc, GondolaLine line, Vec3 cam, PoseStack pose, MultiBufferSource buffers,
                                      BlockRenderDispatcher blocks) {
        BlockState lattice = GondolaRegistry.LATTICE.get().defaultBlockState();
        BlockState crossarm = GondolaBuild.crossarm(line);
        for (int k = 1; k <= line.towers; k++) {
            int head = line.towerHeadY(k);
            if (head <= GondolaLine.BUILD_TOP) {
                continue;
            }
            int a = line.towerA(k);
            int x = line.blockX(a, 0);
            int z = line.blockZ(a, 0);
            double dx = x + 0.5 - cam.x;
            double dz = z + 0.5 - cam.z;
            if (dx * dx + dz * dz > TOWER_DISTANCE * TOWER_DISTANCE) {
                continue;
            }
            int light = LevelRenderer.getLightColor(mc.level, new BlockPos(x, GondolaLine.BUILD_TOP, z));
            for (int y = GondolaLine.BUILD_TOP + 1; y < head; y++) {
                block(blocks, lattice, x, y, z, cam, pose, buffers, light);
            }
            block(blocks, crossarm, x, head, z, cam, pose, buffers, light);
            for (int s = -1; s <= 1; s += 2) {
                block(blocks, GondolaBuild.sheave(line, s), line.blockX(a, s), head, line.blockZ(a, s), cam, pose, buffers, light);
            }
        }
    }

    private static void block(BlockRenderDispatcher blocks, BlockState state, int x, int y, int z, Vec3 cam, PoseStack pose,
                              MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(x - cam.x, y - cam.y, z - cam.z);
        blocks.renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
    }

    private GondolaLineRenderer() {}
}
