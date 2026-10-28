package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.L227Stag;
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
 * Draws a Level 227 stag with its code-built {@link L227StagModel}, and its red eyes a second time
 * at full brightness, so in the dim forest the first thing you see of one is two red points.
 */
public class L227StagRenderer extends MobRenderer<L227Stag, L227StagModel> {
    private static final ResourceLocation TEXTURE = Backrooms.id("textures/entity/l227_stag.png");
    private static final ResourceLocation EYES = Backrooms.id("textures/entity/l227_stag_eyes.png");

    public L227StagRenderer(EntityRendererProvider.Context context) {
        super(context, new L227StagModel(context.bakeLayer(L227StagModel.LAYER)), 1.0f);
        addLayer(new EyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(L227Stag entity) {
        return TEXTURE;
    }

    private static final class EyesLayer extends RenderLayer<L227Stag, L227StagModel> {
        EyesLayer(RenderLayerParent<L227Stag, L227StagModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, L227Stag entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible()) {
                return;
            }
            getParentModel().renderToBuffer(poseStack, buffers.getBuffer(RenderType.eyes(EYES)),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }
}
