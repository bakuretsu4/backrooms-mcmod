package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.L227Squirrel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a Level 227 squirrel: the code-built {@link L227SquirrelModel}, then its fluorescent fur a
 * second time at full brightness, so a swarm reads as dozens of glowing green shapes darting
 * through the undergrowth. The glow breathes slowly, and burns brighter once they attack.
 *
 * <p>A clinging squirrel is drawn exactly at its spot on the player it hangs on (worked out from
 * that player's smoothed position this frame), not where its own, less frequent position updates
 * put it, so it never trails behind someone running.
 */
public class L227SquirrelRenderer extends MobRenderer<L227Squirrel, L227SquirrelModel> {
    private static final ResourceLocation TEXTURE = Backrooms.id("textures/entity/l227_squirrel.png");
    private static final ResourceLocation GLOW = Backrooms.id("textures/entity/l227_squirrel_glow.png");

    public L227SquirrelRenderer(EntityRendererProvider.Context context) {
        super(context, new L227SquirrelModel(context.bakeLayer(L227SquirrelModel.LAYER)), 0.25f);
        addLayer(new GlowLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(L227Squirrel entity) {
        return TEXTURE;
    }

    @Override
    public void render(L227Squirrel entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        Player target = entity.clingTarget();
        if (target == null) {
            super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
            return;
        }
        float bodyYaw = Mth.rotLerp(partialTick, target.yBodyRotO, target.yBodyRot);
        Vec3 offset = L227Squirrel.clingOffset(entity.clingSlot(), bodyYaw);
        double x = Mth.lerp(partialTick, target.xo, target.getX()) + offset.x - Mth.lerp(partialTick, entity.xo, entity.getX());
        double y = Mth.lerp(partialTick, target.yo, target.getY()) + offset.y - Mth.lerp(partialTick, entity.yo, entity.getY());
        double z = Mth.lerp(partialTick, target.zo, target.getZ()) + offset.z - Mth.lerp(partialTick, entity.zo, entity.getZ());
        // Face into the body. These are client-side copies used only for drawing this frame.
        float yaw = L227Squirrel.clingYaw(entity.clingSlot(), bodyYaw);
        entity.yBodyRot = yaw;
        entity.yBodyRotO = yaw;
        entity.yHeadRot = yaw;
        entity.yHeadRotO = yaw;
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
        poseStack.popPose();
    }

    /** The fluorescent fur: only the glowing hair tips are bright in the glow texture, drawn additively. */
    private static final class GlowLayer extends RenderLayer<L227Squirrel, L227SquirrelModel> {
        GlowLayer(RenderLayerParent<L227Squirrel, L227SquirrelModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, L227Squirrel entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible()) {
                return;
            }
            boolean attacking = entity.state() == L227Squirrel.LEAP || entity.state() == L227Squirrel.CLING;
            float breathe = 0.5f + 0.5f * Mth.sin((ageInTicks + entity.getId() * 7) * (attacking ? 0.5f : 0.08f));
            float glow = attacking ? 0.85f + 0.15f * breathe : 0.55f + 0.25f * breathe;
            getParentModel().renderToBuffer(poseStack, buffers.getBuffer(RenderType.eyes(GLOW)),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.colorFromFloat(1.0f, glow, glow, glow));
        }
    }
}
