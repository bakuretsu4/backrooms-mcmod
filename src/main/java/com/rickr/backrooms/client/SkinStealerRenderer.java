package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.SkinStealer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Draws the Skin-stealer with its code-built model ({@link SkinStealerModel}). */
public class SkinStealerRenderer extends MobRenderer<SkinStealer, SkinStealerModel> {
    private static final ResourceLocation TEXTURE = Backrooms.id("textures/entity/skin_stealer.png");

    public SkinStealerRenderer(EntityRendererProvider.Context context) {
        super(context, new SkinStealerModel(context.bakeLayer(SkinStealerModel.LAYER)), 0.6f);
    }

    @Override
    public ResourceLocation getTextureLocation(SkinStealer entity) {
        return TEXTURE;
    }
}
