package com.rickr.backrooms.client.anomaly.level39.gondola;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rickr.backrooms.world.anomaly.level39.gondola.GondolaChair;
import com.rickr.backrooms.world.anomaly.level39.gondola.GondolaChairBlock;
import com.rickr.backrooms.world.anomaly.level39.gondola.GondolaLine;
import com.rickr.backrooms.world.anomaly.level39.gondola.GondolaRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;

/**
 * Draws the chair you ride in with the very same block model as the waiting chair at the station
 * (only with its safety bar down), so boarding looks like the chair simply starts moving.
 *
 * <p>{@link #drawChair} is shared with {@link GondolaLineRenderer}'s passing chairs: it hangs the
 * model from its grip on the cable and lets it swing a little about that grip.
 */
public class GondolaChairRenderer extends EntityRenderer<GondolaChair> {
    /** The chair model's grip on the cable, in its block, for a chair facing north (its seat hangs half a block east). */
    private static final double GRIP_X = 1.0;
    private static final double GRIP_Y = 2.0;
    private static final double GRIP_Z = 0.5;

    private final BlockRenderDispatcher blocks;

    public GondolaChairRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.0f; // hanging in the air: no shadow on the ground far below
    }

    @Override
    public void render(GondolaChair chair, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        GondolaLine line = chair.line();
        if (line != null) {
            double t = chair.rideTicks(partialTick);
            // A gentle fore-and-aft rock as the chair pulls away and the cable bounces over the sheaves.
            float rock = (float) (2.5 * Math.sin(t * 0.21) * Math.min(1.0, t / 25.0) + 1.5 * Math.sin(t * 0.53));
            BlockState state = GondolaRegistry.CHAIR.get().defaultBlockState()
                    .setValue(GondolaChairBlock.FACING, line.dir)
                    .setValue(GondolaChairBlock.LOWERED, true);
            pose.pushPose();
            // The entity stands at the bottom centre of the chair; its grip is straight above.
            pose.translate(0.0, GRIP_Y, 0.0);
            drawChair(blocks, state, line.dir, rock, 0.0f, pose, buffers, packedLight);
            pose.popPose();
        }
        super.render(chair, entityYaw, partialTick, pose, buffers, packedLight);
    }

    /**
     * Draws a chair whose grip is at the pose's origin: swung by {@code pitch} degrees fore-and-aft
     * (about the axis across the line) and {@code roll} degrees sideways.
     */
    static void drawChair(BlockRenderDispatcher blocks, BlockState state, Direction facing, float pitch, float roll,
                          PoseStack pose, MultiBufferSource buffers, int light) {
        Direction right = facing.getClockWise();
        pose.pushPose();
        if (pitch != 0.0f) {
            pose.mulPose(Axis.of(new Vector3f(right.getStepX(), 0.0f, right.getStepZ())).rotationDegrees(pitch));
        }
        if (roll != 0.0f) {
            pose.mulPose(Axis.of(new Vector3f(facing.getStepX(), 0.0f, facing.getStepZ())).rotationDegrees(roll));
        }
        // The blockstate turns the model about its block's centre, and the grip turns with it
        // (in the north-facing model +x is the chair's right and +z its back).
        double gx = 0.5 + (GRIP_X - 0.5) * right.getStepX() - (GRIP_Z - 0.5) * facing.getStepX();
        double gz = 0.5 + (GRIP_X - 0.5) * right.getStepZ() - (GRIP_Z - 0.5) * facing.getStepZ();
        pose.translate(-gx, -GRIP_Y, -gz);
        blocks.renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(GondolaChair chair) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
