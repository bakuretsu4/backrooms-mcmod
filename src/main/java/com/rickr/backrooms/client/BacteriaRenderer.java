package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.entity.Bacteria;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class BacteriaRenderer extends MobRenderer<Bacteria, BacteriaModel> {
    private static final ResourceLocation TEXTURE = Backrooms.id("textures/entity/bacteria.png");

    public BacteriaRenderer(EntityRendererProvider.Context context) {
        super(context, new BacteriaModel(context.bakeLayer(BacteriaModel.LAYER)), 0.5f);
        addLayer(new NightShotLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(Bacteria entity) {
        return TEXTURE;
    }

    /**
     * Infrared: the black wires reflect the camcorder's IR lamp, so while YOUR NightShot is on the
     * Bacteria is drawn a second time in pale grey at full brightness, even in total darkness.
     * This is purely visual and per-player: it doesn't change what the Bacteria can see.
     */
    private static final class NightShotLayer extends RenderLayer<Bacteria, BacteriaModel> {
        private static final ResourceLocation IR_TEXTURE = Backrooms.id("textures/entity/bacteria_ir.png");

        NightShotLayer(RenderLayerParent<Bacteria, BacteriaModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, Bacteria entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!CameraClientState.nightShot || entity.isInvisible()) {
                return;
            }
            getParentModel().renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(IR_TEXTURE)),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }
}
