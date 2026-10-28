package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.L227Boar;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Draws a Level 227 boar with its code-built {@link L227BoarModel}. A big shadow: it is the largest animal. */
public class L227BoarRenderer extends MobRenderer<L227Boar, L227BoarModel> {
    private static final ResourceLocation TEXTURE = Backrooms.id("textures/entity/l227_boar.png");

    public L227BoarRenderer(EntityRendererProvider.Context context) {
        super(context, new L227BoarModel(context.bakeLayer(L227BoarModel.LAYER)), 1.8f);
    }

    @Override
    public ResourceLocation getTextureLocation(L227Boar entity) {
        return TEXTURE;
    }
}
