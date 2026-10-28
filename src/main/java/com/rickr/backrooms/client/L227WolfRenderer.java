package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.L227Wolf;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a Level 227 wolf with its code-built {@link L227WolfModel}, plus its eyes a second time at
 * full brightness: a faint pale glint that shows in the shade under the canopy before the rest of
 * it does.
 */
public class L227WolfRenderer extends MobRenderer<L227Wolf, L227WolfModel> {
    private static final ResourceLocation TEXTURE = Backrooms.id("textures/entity/l227_wolf.png");
    private static final ResourceLocation EYES = Backrooms.id("textures/entity/l227_wolf_eyes.png");

    public L227WolfRenderer(EntityRendererProvider.Context context) {
        super(context, new L227WolfModel(context.bakeLayer(L227WolfModel.LAYER)), 0.75f);
        addLayer(new EyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(L227Wolf entity) {
        return TEXTURE;
    }

    /** The eye glint (only the eye texels are non-black in the eyes texture, drawn additively). */
    private static final class EyesLayer extends RenderLayer<L227Wolf, L227WolfModel> {
        EyesLayer(RenderLayerParent<L227Wolf, L227WolfModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, L227Wolf entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible()) {
                return;
            }
            getParentModel().renderToBuffer(poseStack, buffers.getBuffer(RenderType.eyes(EYES)),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }
}
