package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.VenusFlytrap;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the giant Venus flytrap with its code-built {@link VenusFlytrapModel}. Lying in wait it
 * wears its camouflage (moss and leaf litter on dull green-maroon lobes) and casts no shadow, so
 * nothing gives it away; from the snap on it shows its red inside and the stalk casts a shadow.
 * It never tips over when it dies (a rooted plant just wilts in place).
 */
public class VenusFlytrapRenderer extends MobRenderer<VenusFlytrap, VenusFlytrapModel> {
    private static final ResourceLocation CAMOUFLAGE = Backrooms.id("textures/entity/venus_flytrap.png");
    private static final ResourceLocation FED = Backrooms.id("textures/entity/venus_flytrap_fed.png");

    public VenusFlytrapRenderer(EntityRendererProvider.Context context) {
        super(context, new VenusFlytrapModel(context.bakeLayer(VenusFlytrapModel.LAYER)), 0.9f);
        this.shadowStrength = 0.6f;
    }

    /** Red only while it is shut on someone (snap, lift, hold): the flash you see as it closes. */
    private static boolean showsRed(VenusFlytrap entity) {
        VenusFlytrap.Phase phase = entity.phase();
        return phase == VenusFlytrap.Phase.SNAP || phase == VenusFlytrap.Phase.LIFT || phase == VenusFlytrap.Phase.HOLD;
    }

    @Override
    public ResourceLocation getTextureLocation(VenusFlytrap entity) {
        return showsRed(entity) ? FED : CAMOUFLAGE;
    }

    @Override
    protected float getShadowRadius(VenusFlytrap entity) {
        return entity.phase() == VenusFlytrap.Phase.IDLE ? 0.0f : super.getShadowRadius(entity);
    }

    /** Only the turn to face its yaw: no death tip-over, no shaking. */
    @Override
    protected void setupRotations(VenusFlytrap entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - yBodyRot));
        if (entity.deathTime > 0) {
            // Dying: it sags into the ground.
            poseStack.translate(0.0f, -Math.min(1.0f, (entity.deathTime + partialTick) / 20.0f) * 0.4f, 0.0f);
        }
    }
}
