package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.L227Bear;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a Level 227 bear with its code-built {@link L227BearModel}, plus a faint eyeshine: the
 * fifteen eyes catch what little light reaches the cave, so in the dark you see them open one by
 * one. The glow texture is black everywhere except the pupils, and a closed lid hides its eye's
 * glow (the lids are drawn first and the glow is depth-tested against them).
 */
public class L227BearRenderer extends MobRenderer<L227Bear, L227BearModel> {
    private static final ResourceLocation TEXTURE = Backrooms.id("textures/entity/l227_bear.png");
    private static final RenderType EYESHINE = RenderType.eyes(Backrooms.id("textures/entity/l227_bear_eyes.png"));

    public L227BearRenderer(EntityRendererProvider.Context context) {
        super(context, new L227BearModel(context.bakeLayer(L227BearModel.LAYER)), 1.3f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYESHINE;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(L227Bear entity) {
        return TEXTURE;
    }
}
