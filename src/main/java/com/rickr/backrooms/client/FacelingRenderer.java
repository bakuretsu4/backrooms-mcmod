package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.Faceling;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a Faceling with its code-built {@link FacelingModel}. Each one wears one of four plain
 * outfits; Partial facelings use the same outfit with faint features pressed into the blank face.
 * Hostile ones look exactly the same: that is the point.
 */
public class FacelingRenderer extends MobRenderer<Faceling, FacelingModel> {
    /** [outfit][0 = true faceling, 1 = partial]. */
    private static final ResourceLocation[][] TEXTURES = new ResourceLocation[Faceling.OUTFITS][2];

    static {
        for (int outfit = 0; outfit < Faceling.OUTFITS; outfit++) {
            TEXTURES[outfit][0] = Backrooms.id("textures/entity/faceling_" + outfit + ".png");
            TEXTURES[outfit][1] = Backrooms.id("textures/entity/faceling_" + outfit + "_partial.png");
        }
    }

    public FacelingRenderer(EntityRendererProvider.Context context) {
        super(context, new FacelingModel(context.bakeLayer(FacelingModel.LAYER)), 0.45f);
    }

    @Override
    public ResourceLocation getTextureLocation(Faceling entity) {
        return TEXTURES[entity.outfit()][entity.isPartial() ? 1 : 0];
    }

    /** The model is about 2.06 blocks tall; this brings it to its 1.9-block hitbox. */
    @Override
    protected void scale(Faceling entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.93f, 0.93f, 0.93f);
    }
}
