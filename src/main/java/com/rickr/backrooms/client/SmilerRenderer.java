package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.Smiler;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/**
 * Draws a Smiler as a billboard: turned to look straight at the camera every frame, so wherever
 * you stand it is looking at you.
 *
 * <p>Two passes, in this order:
 * <ol>
 *   <li>The smoke: a few ragged black puffs, see-through, unlit, and not writing depth
 *       ({@link SmilerRenderTypes#SMOKE}), so even your flashlight beam doesn't light it: against
 *       a lit wall you see a dark, churning smudge around the face.</li>
 *   <li>The face: drawn with additive "eyes" blending at full brightness. Additive means the
 *       black parts of the sprites add nothing and the glow only ever brightens what's behind
 *       it, like light. The camcorder's bloom then smears it into a haze.</li>
 * </ol>
 * The smoke must go first: these render types are drawn in the order they are requested, and
 * smoke drawn over the glow would dim it.
 */
public class SmilerRenderer extends EntityRenderer<Smiler> {
    static final ResourceLocation TEXTURE = Backrooms.id("textures/entity/smiler.png");

    private final SmilerModel model;

    public SmilerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new SmilerModel(context.bakeLayer(SmilerModel.LAYER));
        this.shadowRadius = 0.0f; // nothing solid there to cast a shadow
    }

    @Override
    public void render(Smiler entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        model.setupAnim(entity, 0.0f, 0.0f, entity.tickCount + partialTick, 0.0f, 0.0f);
        float glow = model.glow();
        poseStack.pushPose();
        poseStack.translate(0.0, Smiler.FACE_HEIGHT, 0.0);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        // Model space has +Y pointing down (like every entity model), so flip it upright.
        poseStack.scale(-1.0f, -1.0f, 1.0f);

        model.show(false, true);
        model.renderToBuffer(poseStack, buffers.getBuffer(SmilerRenderTypes.SMOKE),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.colorFromFloat(model.smokeDensity(), 1.0f, 1.0f, 1.0f));
        model.show(true, false);
        model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.eyes(TEXTURE)),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.colorFromFloat(1.0f, glow, glow, glow));
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(Smiler entity) {
        return TEXTURE;
    }
}
