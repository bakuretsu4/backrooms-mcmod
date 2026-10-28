package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.L227Doe;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Draws a Level 227 doe with its code-built {@link L227DoeModel}. A small, soft shadow: she is slender. */
public class L227DoeRenderer extends MobRenderer<L227Doe, L227DoeModel> {
    private static final ResourceLocation TEXTURE = Backrooms.id("textures/entity/l227_doe.png");

    public L227DoeRenderer(EntityRendererProvider.Context context) {
        super(context, new L227DoeModel(context.bakeLayer(L227DoeModel.LAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(L227Doe entity) {
        return TEXTURE;
    }
}
