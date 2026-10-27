package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.Clump;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the Clump. Two things differ from a normal mob: the body always faces the open side of
 * its hiding place (not wherever its head happens to point), so the long arm's maths in
 * {@link ClumpModel} can rely on that; and a clump hanging in a drain casts no shadow.
 */
public class ClumpRenderer extends MobRenderer<Clump, ClumpModel> {
    private static final ResourceLocation TEXTURE = Backrooms.id("textures/entity/clump.png");

    public ClumpRenderer(EntityRendererProvider.Context context) {
        super(context, new ClumpModel(context.bakeLayer(ClumpModel.LAYER)), 0.8f);
    }

    @Override
    public void render(Clump entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        shadowRadius = entity.isCeiling() ? 0.0f : 0.8f;
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    protected void setupRotations(Clump entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
        super.setupRotations(entity, poseStack, bob, entity.facingYaw(), partialTick, scale);
    }

    @Override
    public ResourceLocation getTextureLocation(Clump entity) {
        return TEXTURE;
    }
}
