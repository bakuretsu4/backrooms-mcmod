package com.rickr.backrooms.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rickr.backrooms.client.level228.Level228Reflections;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two moments inside a living entity's draw that Level 228's mirror stand-ins need and no event
 * offers: right after the model is posed (a reflection's head can tilt sideways, which the
 * vanilla pose never does) and right before the model space is left (the imitator's eyes catch
 * the light: two tiny quads that must follow the posed head). Both are no-ops unless
 * client/level228/Level228Reflections is drawing a stand-in at that moment.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Shadow
    protected EntityModel<?> model;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
                    shift = At.Shift.AFTER))
    private void backrooms$afterSetupAnim(LivingEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                                          MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        Level228Reflections.afterSetupAnim(model);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void backrooms$beforeModelPop(LivingEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                                          MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        Level228Reflections.beforeModelPop(model, poseStack, buffer);
    }
}
