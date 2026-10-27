package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.Hound;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Draws a Hound with its code-built {@link HoundModel}. Wide, soft shadow: it is long and low. */
public class HoundRenderer extends MobRenderer<Hound, HoundModel> {
    private static final ResourceLocation TEXTURE = Backrooms.id("textures/entity/hound.png");

    public HoundRenderer(EntityRendererProvider.Context context) {
        super(context, new HoundModel(context.bakeLayer(HoundModel.LAYER)), 0.65f);
    }

    @Override
    public ResourceLocation getTextureLocation(Hound entity) {
        return TEXTURE;
    }
}
