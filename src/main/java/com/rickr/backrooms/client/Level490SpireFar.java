package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.layout.Level490Layout;
import com.rickr.backrooms.world.layout.Level490Spire;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Level 490's spires where the fog has taken their blocks: you steer by the spire, so it has to
 * show from the arrival island, about 300 blocks off, where the haze has swallowed everything
 * else. Each spire within a few kilometres is drawn as its outline (its vertebrae and ribs, from
 * world/layout/Level490Spire): a shape a little paler than the haze, so it stands dark against the
 * brighter storm clouds, its moon side lit; faint where its blocks still show and fuller as the fog
 * takes them; lit up when lightning flashes near it.
 *
 * <p>Drawn after the world with the depth test, so anything nearer (an island, a chain) still hides
 * it; pulled a few blocks toward the camera (and shrunk to match, so it looks the same size) so its
 * own fogged blocks don't.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level490SpireFar {
    /** Nearer than this the blocks show well enough alone. */
    private static final double NEAR = 110.0;
    /** By here the fog has the blocks: the outline is at full strength. */
    private static final double FULL = 300.0;
    private static final double REACH = 3500.0;
    private static final double PULL = 10.0;

    private static final float MOON_X = (float) Math.cos(Math.toRadians(35));
    private static final float MOON_Z = (float) Math.sin(Math.toRadians(35));

    private Level490SpireFar() {}

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, float[] fog, float add, float alpha) {
        out.addVertex(pose, x, y, z).setColor(fog[0] + add, fog[1] + add, fog[2] + add * 1.1f, alpha);
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || !Level490Atmosphere.inLevel490()
                || !Level490ClientState.hasSeed()) {
            return;
        }
        long seed = Level490ClientState.seed();
        Vec3 cam = event.getCamera().getPosition();
        Minecraft mc = Minecraft.getInstance();
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.debugQuads();
        VertexConsumer out = null;
        int region = Level490Layout.REGION;
        int rx = Math.floorDiv((int) cam.x, region);
        int rz = Math.floorDiv((int) cam.z, region);
        for (int i = rx - 4; i <= rx + 4; i++) {
            for (int j = rz - 4; j <= rz + 4; j++) {
                int[] site = Level490Layout.siteCentre(seed, i * region + region / 2, j * region + region / 2);
                double d = Math.hypot(site[0] + 0.5 - cam.x, site[1] + 0.5 - cam.z);
                if (d < NEAR || d > REACH) {
                    continue;
                }
                float glow = Level490Lightning.cloudGlow(site[0], site[1]);
                float fogged = (float) Math.min(1.0, (d - NEAR) / (FULL - NEAR));
                float alpha = (float) Math.min(1.0, 0.85 * fogged * Math.exp(-Math.max(0.0, d - FULL) / 2500.0) + glow * 0.6);
                if (alpha < 0.02f) {
                    continue;
                }
                if (out == null) {
                    out = buffers.getBuffer(type);
                }
                Level490Spire spire = Level490Layout.spireAt(seed, site[0], site[1]);
                float[] fog = Level490Atmosphere.fogColour(new float[3]);
                draw(out, pose, spire.outline(), cam, d, fog, glow, alpha);
                for (float[][] rib : spire.ribLines()) {
                    draw(out, pose, rib, cam, d, fog, glow, alpha);
                }
            }
        }
        if (out != null) {
            buffers.endBatch(type);
        }
    }

    /**
     * A line of {x, y, z, half width} points as a ribbon of quads facing the camera: the haze's
     * colour, a little paler toward the moon (pale stone through fog), bright in a flash.
     */
    private static void draw(VertexConsumer out, PoseStack.Pose pose, float[][] outline, Vec3 cam, double d,
                             float[] fog, float glow, float alpha) {
        float s = (float) ((d - PULL) / d);
        for (int k = 0; k + 1 < outline.length; k++) {
            float[] a = outline[k];
            float[] b = outline[k + 1];
            float ax = (float) (a[0] - cam.x) * s;
            float ay = (float) (a[1] - cam.y) * s;
            float az = (float) (a[2] - cam.z) * s;
            float bx = (float) (b[0] - cam.x) * s;
            float by = (float) (b[1] - cam.y) * s;
            float bz = (float) (b[2] - cam.z) * s;
            // Sideways: across both the line itself and the line of sight.
            float dx = bx - ax;
            float dy = by - ay;
            float dz = bz - az;
            float mx = (ax + bx) * 0.5f;
            float my = (ay + by) * 0.5f;
            float mz = (az + bz) * 0.5f;
            float sx = dy * mz - dz * my;
            float sy = dz * mx - dx * mz;
            float sz = dx * my - dy * mx;
            float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-6f) {
                continue;
            }
            sx /= sl;
            sy /= sl;
            sz /= sl;
            // Which edge faces the moon (low in the sky, azimuth 35 degrees: see Level490Sky).
            float moon = sx * MOON_X + sz * MOON_Z;
            float lit = 0.03f + 0.05f * Math.max(0.0f, moon) + 0.45f * glow;
            float dark = 0.015f + 0.05f * Math.max(0.0f, -moon) + 0.45f * glow;
            float wa = a[3] * s;
            float wb = b[3] * s;
            vertex(out, pose, ax - sx * wa, ay - sy * wa, az - sz * wa, fog, dark, alpha);
            vertex(out, pose, ax + sx * wa, ay + sy * wa, az + sz * wa, fog, lit, alpha);
            vertex(out, pose, bx + sx * wb, by + sy * wb, bz + sz * wb, fog, lit, alpha);
            vertex(out, pose, bx - sx * wb, by - sy * wb, bz - sz * wb, fog, dark, alpha);
        }
    }
}
