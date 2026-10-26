package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.Level94Vehicles;
import com.rickr.backrooms.block.VehicleBlock;
import com.rickr.backrooms.block.VehicleBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws a parked Level 94 car or milk van at its anchor block ({@link VehicleBlock}). The block
 * state says which vehicle (KIND) and which way its front points (FACING); the model is built in
 * code ({@link VehicleModel}) and the textures come from {@code tex_l94_vehicle*} in gen_assets.
 */
public class VehicleRenderer implements BlockEntityRenderer<VehicleBlockEntity> {
    private static final ResourceLocation CAR_RED = Backrooms.id("textures/entity/l94_vehicle_red.png");
    private static final ResourceLocation CAR_BLACK = Backrooms.id("textures/entity/l94_vehicle_black.png");
    private static final ResourceLocation CAR_CREAM = Backrooms.id("textures/entity/l94_vehicle_cream.png");
    private static final ResourceLocation MILK_VAN = Backrooms.id("textures/entity/l94_vehicle_van.png");

    private final VehicleModel car;
    private final VehicleModel van;

    public VehicleRenderer(BlockEntityRendererProvider.Context context) {
        this.car = new VehicleModel(context.bakeLayer(VehicleModel.CAR_LAYER));
        this.van = new VehicleModel(context.bakeLayer(VehicleModel.VAN_LAYER));
    }

    @Override
    public void render(VehicleBlockEntity vehicle, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BlockState state = vehicle.getBlockState();
        if (!(state.getBlock() instanceof VehicleBlock)) {
            return;
        }
        Level94Vehicles.Kind kind = state.getValue(VehicleBlock.KIND);
        Direction facing = state.getValue(VehicleBlock.FACING);
        Direction right = facing.getClockWise();
        float side = Level94Vehicles.sideOffset(kind);

        poseStack.pushPose();
        // To the middle of the vehicle's footprint, on the ground (the bottom of the anchor block).
        poseStack.translate(0.5 + side * right.getStepX(), 0.0, 0.5 + side * right.getStepZ());
        // The model's front points north (-z); turn it to FACING (toYRot: south 0, west 90, north 180, east 270).
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - facing.toYRot()));
        // Model space has +Y pointing down (like every entity model), so flip it upright. Flipping x
        // too makes it a half turn instead of a mirror image, so the "MILK" lettering reads right.
        poseStack.scale(-1.0f / 16.0f, -1.0f / 16.0f, 1.0f / 16.0f);
        VehicleModel model = kind.isVan() ? van : car;
        model.renderToBuffer(poseStack, buffers.getBuffer(model.renderType(texture(kind))), packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private static ResourceLocation texture(Level94Vehicles.Kind kind) {
        return switch (kind) {
            case CAR_RED -> CAR_RED;
            case CAR_BLACK -> CAR_BLACK;
            case CAR_CREAM -> CAR_CREAM;
            case MILK_VAN -> MILK_VAN;
        };
    }

    /**
     * The vehicle is much bigger than its anchor block, so tell the game the whole footprint;
     * otherwise it would vanish as soon as the anchor block itself left the screen.
     */
    @Override
    public AABB getRenderBoundingBox(VehicleBlockEntity vehicle) {
        int r = Level94Vehicles.REACH;
        return new AABB(vehicle.getBlockPos()).inflate(r, 0, r).expandTowards(0, 3, 0);
    }

    /** Parked cars are landmarks on the open hills: keep drawing them from further than a chest. */
    @Override
    public int getViewDistance() {
        return 128;
    }
}
