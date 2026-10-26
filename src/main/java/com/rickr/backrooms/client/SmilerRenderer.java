package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.entity.Smiler;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a Smiler as a billboard: the face is turned to look straight at the camera every frame,
 * so wherever you stand it is looking at you.
 *
 * <p>Everything is drawn at full brightness, so it glows in total darkness and no lamp or
 * flashlight changes how it looks. The face is drawn twice: once solid (so it always shows up),
 * then again with additive "eyes" blending on top, which pushes it to pure glowing white for the
 * camcorder's bloom to pick up.
 *
 * <p>With NightShot on, the black smoky body under the face is drawn too (only on your screen).
 */
public class SmilerRenderer extends EntityRenderer<Smiler> {
    private static final ResourceLocation TEXTURE = Backrooms.id("textures/entity/smiler.png");
    private static final ResourceLocation BODY_TEXTURE = Backrooms.id("textures/entity/smiler_body.png");

    private final SmilerModel model;

    public SmilerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new SmilerModel(context.bakeLayer(SmilerModel.LAYER));
        this.shadowRadius = 0.0f; // nothing there to cast a shadow
    }

    @Override
    public void render(Smiler entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        model.setupAnim(entity, 0.0f, 0.0f, entity.tickCount + partialTick, 0.0f, 0.0f);
        poseStack.pushPose();
        poseStack.translate(0.0, Smiler.FACE_HEIGHT, 0.0);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        // Model space has +Y pointing down (like every entity model), so flip it upright.
        poseStack.scale(-1.0f, -1.0f, 1.0f);

        if (CameraClientState.nightShot) {
            model.show(false, true);
            model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(BODY_TEXTURE)),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
        model.show(true, false);
        model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.eyes(TEXTURE)),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(Smiler entity) {
        return TEXTURE;
    }
}
