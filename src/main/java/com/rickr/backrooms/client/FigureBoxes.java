package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Coloured boxes for the code-built figures of Level 490 (the Penitents and the pilgrim), drawn
 * into {@link WatcherRenderTypes#SOLID} the way the Observer is: each box one flat colour, lit by
 * the world's light at the figure.
 */
final class FigureBoxes {
    private FigureBoxes() {}

    static int rgb(float r, float g, float b) {
        return WatcherModel.argb(r, g, b);
    }

    /** An axis-aligned box from (x0, y0, z0) to (x1, y1, z1) in the pose's space, outward normals. */
    static void box(PoseStack.Pose pose, VertexConsumer out, float x0, float y0, float z0, float x1, float y1, float z1, int colour,
                    int light) {
        face(pose, out, colour, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        face(pose, out, colour, light, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        face(pose, out, colour, light, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        face(pose, out, colour, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        face(pose, out, colour, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        face(pose, out, colour, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
    }

    /**
     * A chain hanging from a to b: small links one after another, each turned a quarter from the
     * last, as boxes about the line.
     */
    static void chain(PoseStack poses, VertexConsumer out, float ax, float ay, float az, float bx, float by, float bz, int colour,
                      int light) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int links = Math.max(2, Math.round(len / 0.09f));
        for (int k = 0; k < links; k++) {
            float t = (k + 0.5f) / links;
            poses.pushPose();
            poses.translate(ax + dx * t, ay + dy * t, az + dz * t);
            // Point the link's long side along the chain.
            float yaw = (float) Math.atan2(dx, dz);
            float pitch = (float) Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz));
            poses.mulPose(com.mojang.math.Axis.YP.rotation(yaw));
            poses.mulPose(com.mojang.math.Axis.XP.rotation(pitch));
            poses.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(k % 2 == 0 ? 0 : 90));
            float l = len / links * 0.62f;
            box(poses.last(), out, -0.035f, -0.012f, -l, 0.035f, 0.012f, l, colour, light);
            poses.popPose();
        }
    }

    private static void face(PoseStack.Pose pose, VertexConsumer out, int colour, int light, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(pose, out, ax, ay, az, colour, light, nx, ny, nz);
        vertex(pose, out, bx, by, bz, colour, light, nx, ny, nz);
        vertex(pose, out, cx, cy, cz, colour, light, nx, ny, nz);
        vertex(pose, out, dx, dy, dz, colour, light, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z, int colour, int light,
                               float nx, float ny, float nz) {
        out.addVertex(pose, x, y, z)
                .setColor(colour)
                .setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
