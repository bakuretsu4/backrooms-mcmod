package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rickr.backrooms.entity.Observer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Observer: a tall, too-thin, featureless figure, almost pure black so it reads as a silhouette
 * against the fog, with two faint pale points for eyes. Built from a few stretched boxes in code
 * (feet at 0, head top at ~2.7 blocks, facing +Z = toward you). Barely moves: a slow sway and a tilt
 * of the head now and then.
 */
public class ObserverRenderer extends EntityRenderer<Observer> {
    private static final int BODY = WatcherModel.argb(0.018f, 0.016f, 0.022f);
    private static final int EYES = WatcherModel.argb(0.62f, 0.64f, 0.60f);

    public ObserverRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(Observer entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        if (entity.isVanishing()) {
            return; // gone in an instant
        }
        float yaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
        float age = entity.tickCount + partialTick;
        VertexConsumer out = buffers.getBuffer(WatcherRenderTypes.SOLID);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        // An almost imperceptible sway from the ankles.
        poseStack.mulPose(Axis.ZP.rotationDegrees(0.8f * Mth.sin(age * 0.021f + entity.getId())));
        PoseStack.Pose pose = poseStack.last();
        int light = packedLight;

        box(pose, out, -0.17f, 0.0f, -0.06f, -0.04f, 1.42f, 0.06f, light);   // legs: long and thin
        box(pose, out, 0.04f, 0.0f, -0.06f, 0.17f, 1.42f, 0.06f, light);
        box(pose, out, -0.20f, 1.40f, -0.09f, 0.20f, 2.28f, 0.09f, light);   // narrow torso
        box(pose, out, -0.29f, 1.02f, -0.05f, -0.21f, 2.24f, 0.05f, light);  // arms hang past the hips
        box(pose, out, 0.21f, 1.02f, -0.05f, 0.29f, 2.24f, 0.05f, light);
        box(pose, out, -0.04f, 2.28f, -0.04f, 0.04f, 2.38f, 0.04f, light);   // neck

        // The head tilts a little, slowly, as if curious.
        poseStack.pushPose();
        poseStack.translate(0.0f, 2.38f, 0.0f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(9.0f * Mth.sin(age * 0.013f + 1.3f * entity.getId())));
        PoseStack.Pose head = poseStack.last();
        box(head, out, -0.11f, 0.0f, -0.12f, 0.11f, 0.32f, 0.12f, light);
        // Two faint pale eyes, slightly glowing (full-bright) so they show in the dark.
        int bright = LightTexture.FULL_BRIGHT;
        quadFront(head, out, -0.065f, 0.17f, -0.035f, 0.205f, 0.1205f, bright);
        quadFront(head, out, 0.035f, 0.17f, 0.065f, 0.205f, 0.1205f, bright);
        poseStack.popPose();

        poseStack.popPose();
    }

    /** An axis-aligned box with outward normals. */
    private static void box(PoseStack.Pose pose, VertexConsumer out, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
        // +Z (front) and -Z (back)
        face(pose, out, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        face(pose, out, light, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        // +X and -X
        face(pose, out, light, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        face(pose, out, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        // +Y and -Y
        face(pose, out, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        face(pose, out, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
    }

    private static void face(PoseStack.Pose pose, VertexConsumer out, int light, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(pose, out, ax, ay, az, BODY, light, nx, ny, nz);
        vertex(pose, out, bx, by, bz, BODY, light, nx, ny, nz);
        vertex(pose, out, cx, cy, cz, BODY, light, nx, ny, nz);
        vertex(pose, out, dx, dy, dz, BODY, light, nx, ny, nz);
    }

    /** A small square facing +Z (an eye), just in front of the head's face. */
    private static void quadFront(PoseStack.Pose pose, VertexConsumer out, float x0, float y0, float x1, float y1, float z, int light) {
        vertex(pose, out, x0, y0, z, EYES, light, 0, 0, 1);
        vertex(pose, out, x1, y0, z, EYES, light, 0, 0, 1);
        vertex(pose, out, x1, y1, z, EYES, light, 0, 0, 1);
        vertex(pose, out, x0, y1, z, EYES, light, 0, 0, 1);
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

    @Override
    public ResourceLocation getTextureLocation(Observer entity) {
        return WatcherRenderTypes.WHITE;
    }
}
