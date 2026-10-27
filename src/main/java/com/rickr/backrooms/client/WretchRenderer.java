package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.Wretch;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Draws the Wretch with its code-built model ({@link WretchModel}). */
public class WretchRenderer extends MobRenderer<Wretch, WretchModel> {
    private static final ResourceLocation TEXTURE = Backrooms.id("textures/entity/wretch.png");

    public WretchRenderer(EntityRendererProvider.Context context) {
        super(context, new WretchModel(context.bakeLayer(WretchModel.LAYER)), 0.4f);
    }

    @Override
    public ResourceLocation getTextureLocation(Wretch entity) {
        return TEXTURE;
    }
}
