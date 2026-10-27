package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.FlareEntity;
import com.rickr.backrooms.entity.GlowstickEntity;
import com.rickr.backrooms.entity.ThrownLight;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a thrown flare (a small red stick with a glowing tip) or glowstick (a thin green tube)
 * as a few code-built boxes, like the Watcher and Observer are drawn. The flare's tip and the
 * glowstick's tube are drawn full-bright, so they glow even in a pitch-black blackout (also
 * without Veil, where there is no real light around them).
 *
 * <p>The stick lies along its local Z axis, turned to the way it was thrown; it tumbles end over
 * end while flying.
 */
public class ThrownLightRenderer<T extends ThrownLight> extends EntityRenderer<T> {
    /** A plain white texture: the colour comes from the vertices. */
    private static final ResourceLocation WHITE = Backrooms.id("textures/entity/thrown_light.png");
    private static final float TUMBLE_DEGREES_PER_TICK = 35.0f;

    public ThrownLightRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0, 0.04, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        if (!entity.lying()) {
            poseStack.mulPose(Axis.XP.rotationDegrees((entity.tickCount + partialTick) * TUMBLE_DEGREES_PER_TICK));
        }
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        PoseStack.Pose pose = poseStack.last();
        if (entity instanceof FlareEntity flare) {
            // Red paper body with a black striker cap at the back.
            box(buffer, pose, 0.035f, -0.17f, 0.12f, 0.78f, 0.10f, 0.07f, packedLight);
            box(buffer, pose, 0.037f, -0.19f, -0.17f, 0.10f, 0.10f, 0.10f, packedLight);
            float flame = flare.lit() ? flare.sputterLeft(partialTick) : 0.0f;
            if (flame > 0.0f) {
                // Hot white-orange core: full-bright, dimming as it sputters.
                box(buffer, pose, 0.03f, 0.12f, 0.18f, 1.0f, 0.55f + 0.25f * flame, 0.3f * flame, LightTexture.FULL_BRIGHT);
            } else {
                // Burnt-out end: dark grey ash, lit by the world like the rest of the stick.
                box(buffer, pose, 0.03f, 0.12f, 0.16f, 0.18f, 0.16f, 0.15f, packedLight);
            }
        } else if (entity instanceof GlowstickEntity glowstick) {
            float glow = 0.15f + 0.85f * glowstick.glow(partialTick);
            box(buffer, pose, 0.022f, -0.13f, 0.13f, 0.35f * glow, 1.0f * glow, 0.45f * glow, LightTexture.FULL_BRIGHT);
            // Little dark plastic end caps.
            box(buffer, pose, 0.026f, -0.15f, -0.13f, 0.12f, 0.14f, 0.12f, packedLight);
            box(buffer, pose, 0.026f, 0.13f, 0.15f, 0.12f, 0.14f, 0.12f, packedLight);
        }
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
    }

    /** A square-section box from z0 to z1, {@code half} wide each way around the Z axis. */
    private static void box(VertexConsumer b, PoseStack.Pose pose, float half, float z0, float z1,
                            float r, float g, float bl, int light) {
        float h = half;
        // +Y, -Y
        quad(b, pose, -h, h, z0, h, h, z0, h, h, z1, -h, h, z1, 0, 1, 0, r, g, bl, light);
        quad(b, pose, -h, -h, z1, h, -h, z1, h, -h, z0, -h, -h, z0, 0, -1, 0, r, g, bl, light);
        // +X, -X
        quad(b, pose, h, -h, z0, h, -h, z1, h, h, z1, h, h, z0, 1, 0, 0, r, g, bl, light);
        quad(b, pose, -h, -h, z1, -h, -h, z0, -h, h, z0, -h, h, z1, -1, 0, 0, r, g, bl, light);
        // +Z, -Z ends
        quad(b, pose, -h, -h, z1, h, -h, z1, h, h, z1, -h, h, z1, 0, 0, 1, r, g, bl, light);
        quad(b, pose, h, -h, z0, -h, -h, z0, -h, h, z0, h, h, z0, 0, 0, -1, r, g, bl, light);
    }

    private static void quad(VertexConsumer b, PoseStack.Pose pose,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz, float r, float g, float bl, int light) {
        vertex(b, pose, x0, y0, z0, 0.0f, 0.0f, nx, ny, nz, r, g, bl, light);
        vertex(b, pose, x1, y1, z1, 1.0f, 0.0f, nx, ny, nz, r, g, bl, light);
        vertex(b, pose, x2, y2, z2, 1.0f, 1.0f, nx, ny, nz, r, g, bl, light);
        vertex(b, pose, x3, y3, z3, 0.0f, 1.0f, nx, ny, nz, r, g, bl, light);
    }

    private static void vertex(VertexConsumer b, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, float r, float g, float bl, int light) {
        b.addVertex(pose, x, y, z).setColor(r, g, bl, 1.0f).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return WHITE;
    }
}
