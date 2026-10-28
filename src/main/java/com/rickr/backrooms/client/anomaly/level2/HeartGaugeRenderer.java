package com.rickr.backrooms.client.anomaly.level2;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.Level2HeartGaugeBlock;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.client.SanityClientEffects;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws the needle of the heart gauge (block/Level2HeartGaugeBlock): the case and the dial are an
 * ordinary block model, only the needle is drawn here, every frame, from YOUR heart rate (the BPM
 * the camcorder HUD shows, from {@link SanityClientEffects}). The dial reads 0-16 "kPa x10", and
 * the needle points at your BPM divided by ten: 7 when you are calm (70 BPM), up into the red at
 * 16 when you are losing it (160 BPM). It kicks a few degrees with every beat, in time with the
 * heartbeat you hear when you are panicking.
 *
 * <p>The needle is one thin tapered quad in front of the dial, plus the black hub over its root.
 * It samples a plain patch of the dial texture and is coloured by its vertices, so it needs no
 * texture of its own.
 */
public final class HeartGaugeRenderer implements BlockEntityRenderer<Level2HeartGaugeBlock.Level2HeartGaugeBlockEntity> {
    private static final ResourceLocation DIAL = Backrooms.id("textures/block/l2_heart_dial.png");
    /** A plain cream patch of the dial (no ticks or numbers), tinted by the vertex colours. */
    private static final float U = 0.5f;
    private static final float V = 0.33f;
    /** The dial's middle in the model (pixels, as the gauge faces north) and just in front of its face. */
    private static final float CX = 8.0f / 16.0f;
    private static final float CY = 9.0f / 16.0f;
    private static final float FACE_Z = 10.36f / 16.0f;
    /** The needle's length past the hub, its tail, and its half-widths at the root and the tip (blocks). */
    private static final float LENGTH = 3.0f / 16.0f;
    private static final float TAIL = 0.7f / 16.0f;
    private static final float ROOT = 0.24f / 16.0f;
    private static final float TIP = 0.07f / 16.0f;
    private static final float HUB = 0.38f / 16.0f;
    /** The dial's scale: 0 at -135 degrees (from straight up, clockwise), full at +135. */
    private static final float SWEEP_FROM = -135.0f;
    private static final float SWEEP = 270.0f;
    /** BPM at the dial's 0 and at its 16 (the scale is "kPa x10": the needle reads your BPM / 10). */
    private static final float BPM_LOW = 0.0f;
    private static final float BPM_HIGH = 160.0f;
    /** How far (degrees) the needle kicks with each beat. */
    private static final float KICK = 7.0f;

    public HeartGaugeRenderer(BlockEntityRendererProvider.Context context) {
    }

    /** The needle's angle (degrees clockwise from straight up) for the local player's heart right now. */
    static float needleAngle() {
        float t = Mth.clamp((SanityClientEffects.bpm() - BPM_LOW) / (BPM_HIGH - BPM_LOW), 0.0f, 1.0f);
        return SWEEP_FROM + SWEEP * t + KICK * SanityClientEffects.heartbeatPulse();
    }

    @Override
    public void render(Level2HeartGaugeBlock.Level2HeartGaugeBlockEntity gauge, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        Direction facing = gauge.getBlockState().getValue(WallMountBlock.FACING);
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(DIAL));
        pose.pushPose();
        // Turn the way the block model is turned (the blockstate's "y": north 0, east 90, ... clockwise from above).
        pose.translate(0.5f, 0.0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.get2DDataValue() * 90.0f + 180.0f));
        pose.translate(-0.5f, 0.0f, -0.5f);
        pose.translate(CX, CY, FACE_Z);
        // The hub first, square to the dial, then the needle turned to its reading.
        quad(out, pose.last(), -HUB, -HUB, HUB, -HUB, HUB, HUB, -HUB, HUB, -0.0004f, 0.07f, 0.065f, 0.06f, light, overlay);
        // Clockwise as you look at the dial (you look along +z at a north-facing gauge).
        pose.mulPose(Axis.ZP.rotationDegrees(needleAngle()));
        quad(out, pose.last(), -ROOT, -TAIL, ROOT, -TAIL, TIP, LENGTH, -TIP, LENGTH, 0.0f, 0.62f, 0.09f, 0.07f, light, overlay);
        pose.popPose();
    }

    private static void quad(VertexConsumer out, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float x2, float y2,
                             float x3, float y3, float z, float r, float g, float b, int light, int overlay) {
        vertex(out, pose, x0, y0, z, r, g, b, light, overlay);
        vertex(out, pose, x1, y1, z, r, g, b, light, overlay);
        vertex(out, pose, x2, y2, z, r, g, b, light, overlay);
        vertex(out, pose, x3, y3, z, r, g, b, light, overlay);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, float r, float g, float b,
                               int light, int overlay) {
        out.addVertex(pose, x, y, z).setColor(r, g, b, 1.0f).setUv(U, V).setOverlay(overlay).setLight(light)
                .setNormal(pose, 0.0f, 0.0f, -1.0f);
    }
}
