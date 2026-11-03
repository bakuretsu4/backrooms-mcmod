package com.rickr.backrooms.client.anomaly.level2;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.Level2SpinPropBlock;
import com.rickr.backrooms.client.light.BackroomsLights;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * The moving, glowing part of Level 2's warning beacon (registry/Level2SpinBlocks SPIN_BEACON):
 * the block model is the steel housing, its hazard rim and the cage over the recess; this draws
 * <ul>
 *   <li>the amber lens, an octagonal dome in the recess, full-bright (emissive with or without a
 *       shader pack);</li>
 *   <li>the reflector's two hot spots turning round the lens, back to back (additive);</li>
 *   <li>without Veil's lights (not installed, or Iris) the two light beams themselves, sweeping
 *       round with the hot spots: crossed blades drawn as vanilla's translucent beacon beam (which
 *       shader packs draw with their own beacon-beam program, alpha and all) with a soft glow
 *       texture that fades to nothing across the beam and along it, so they read as light in the
 *       haze, never as a polygon. With Veil the beacon's real light cones (client/light/VeilFixtures)
 *       do that, with their own shadows and haze.</li>
 * </ul>
 * The sweep comes from {@link Level2SpinProps#beaconAngle}, the same angle the Veil cones use.
 * Nothing is allocated per frame.
 */
public final class SpinBeaconRenderer implements BlockEntityRenderer<Level2SpinPropBlock.Entity> {
    private static final ResourceLocation GLOW = Backrooms.id("textures/block/bulkhead_lamp.png");
    /** The lens dome: from the recess floor (the block's middle) this far out, this wide (blocks). */
    private static final float LENS_HEIGHT = 0.28f;
    private static final float LENS_RADIUS = 0.22f;
    /** Amber. */
    private static final float R = 1.0f;
    private static final float G = 0.58f;
    private static final float B = 0.16f;
    /** The beams (no Veil): length, width at the lens and at the far end, and their opacity. */
    private static final float BEAM_LENGTH = 3.2f;
    private static final float BEAM_NEAR = 0.08f;
    private static final float BEAM_FAR = 0.85f;
    /** Low: four blades overlap in the middle of each beam, and the texture's peak is 1. */
    private static final float BEAM_ALPHA = 0.22f;
    /** White glow, all the beam's shape in its alpha (tools/anomaly_assets/level2_spin.py). */
    private static final ResourceLocation BEAM = Backrooms.id("textures/entity/level2/l2_beam_glow.png");
    /**
     * Whether the drawn beams are used at all while Iris is installed. If a shader pack still draws
     * the beacon-beam type solid, set this false: under Iris the lens and its turning hot spots
     * then carry the beacon alone (they read well there).
     */
    private static final boolean BEAMS_UNDER_IRIS = true;
    private static final boolean IRIS = net.neoforged.fml.ModList.get().isLoaded("iris");
    /** The beams' reach for culling: the block plus the beam. */
    private static final double REACH = BEAM_LENGTH + 0.5;

    private static final float COS45 = 0.70710677f;
    private final Vector3f dir = new Vector3f();

    public SpinBeaconRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(Level2SpinPropBlock.Entity beacon, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (beacon.getLevel() == null) {
            return;
        }
        BlockPos pos = beacon.getBlockPos();
        Direction facing = beacon.getBlockState().getValue(Level2SpinPropBlock.FACING);
        double angle = Level2SpinProps.beaconAngle(pos.getX(), partialTick);
        float[] u = Level2SpinProps.u(facing);
        float[] v = Level2SpinProps.v(facing);
        float nx = facing.getStepX();
        float ny = facing.getStepY();
        float nz = facing.getStepZ();
        PoseStack.Pose p = pose.last();
        int full = LightTexture.FULL_BRIGHT;

        // The lens: eight sides and a cap, from the recess floor (the block's middle) outward.
        VertexConsumer lens = buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW));
        for (int i = 0; i < 8; i++) {
            float c0 = octCos(i);
            float s0 = octSin(i);
            float c1 = octCos(i + 1);
            float s1 = octSin(i + 1);
            float x0 = 0.5f + (u[0] * c0 + v[0] * s0) * LENS_RADIUS;
            float y0 = 0.5f + (u[1] * c0 + v[1] * s0) * LENS_RADIUS;
            float z0 = 0.5f + (u[2] * c0 + v[2] * s0) * LENS_RADIUS;
            float x1 = 0.5f + (u[0] * c1 + v[0] * s1) * LENS_RADIUS;
            float y1 = 0.5f + (u[1] * c1 + v[1] * s1) * LENS_RADIUS;
            float z1 = 0.5f + (u[2] * c1 + v[2] * s1) * LENS_RADIUS;
            float hx = nx * LENS_HEIGHT;
            float hy = ny * LENS_HEIGHT;
            float hz = nz * LENS_HEIGHT;
            // Side (the outward normal is roughly the middle of the two corners' directions).
            float mx = (x0 + x1) * 0.5f - 0.5f;
            float my = (y0 + y1) * 0.5f - 0.5f;
            float mz = (z0 + z1) * 0.5f - 0.5f;
            // (Its length is the rim's inradius; divide it out for the normal.)
            float inv = 1.0f / (LENS_RADIUS * 0.9238795f);
            vertex(lens, p, x0, y0, z0, 0.2f, 0.8f, mx * inv, my * inv, mz * inv, 0.75f, 0.8f, full, overlay);
            vertex(lens, p, x1, y1, z1, 0.8f, 0.8f, mx * inv, my * inv, mz * inv, 0.75f, 0.8f, full, overlay);
            vertex(lens, p, x1 + hx * 0.7f - mx * 0.3f, y1 + hy * 0.7f - my * 0.3f, z1 + hz * 0.7f - mz * 0.3f,
                    0.8f, 0.3f, mx * inv, my * inv, mz * inv, 0.9f, 0.8f, full, overlay);
            vertex(lens, p, x0 + hx * 0.7f - mx * 0.3f, y0 + hy * 0.7f - my * 0.3f, z0 + hz * 0.7f - mz * 0.3f,
                    0.2f, 0.3f, mx * inv, my * inv, mz * inv, 0.9f, 0.8f, full, overlay);
            // Cap: a thin wedge to the dome's tip (a degenerate quad: tip, tip, two rim corners).
            float tx = 0.5f + hx;
            float ty = 0.5f + hy;
            float tz = 0.5f + hz;
            vertex(lens, p, tx, ty, tz, 0.5f, 0.5f, nx, ny, nz, 1.0f, 0.85f, full, overlay);
            vertex(lens, p, x0 + hx * 0.7f - mx * 0.3f, y0 + hy * 0.7f - my * 0.3f, z0 + hz * 0.7f - mz * 0.3f,
                    0.2f, 0.3f, nx, ny, nz, 0.9f, 0.85f, full, overlay);
            vertex(lens, p, x1 + hx * 0.7f - mx * 0.3f, y1 + hy * 0.7f - my * 0.3f, z1 + hz * 0.7f - mz * 0.3f,
                    0.8f, 0.3f, nx, ny, nz, 0.9f, 0.85f, full, overlay);
            vertex(lens, p, tx, ty, tz, 0.5f, 0.5f, nx, ny, nz, 1.0f, 0.85f, full, overlay);
        }

        // The reflector's hot spots: additive.
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
        for (int k = 0; k < 2; k++) {
            hotSpot(glow, p, facing, angle + k * Math.PI, overlay);
        }
        if (!BackroomsLights.isVeilLightingActive() && (BEAMS_UNDER_IRIS || !IRIS)) {
            // Asked for after the hot spots are done: switching buffers may end the last batch.
            VertexConsumer beams = buffers.getBuffer(RenderType.beaconBeam(BEAM, true));
            for (int k = 0; k < 2; k++) {
                Level2SpinProps.sweep(facing, angle + k * Math.PI, Level2SpinProps.BEACON_LEAN, dir);
                beam(beams, p, nx, ny, nz);
            }
        }
    }

    /** A bright patch on the lens where the turning reflector points. */
    private static void hotSpot(VertexConsumer out, PoseStack.Pose p, Direction facing, double a, int overlay) {
        float[] u = Level2SpinProps.u(facing);
        float[] v = Level2SpinProps.v(facing);
        float c = (float) Math.cos(a);
        float s = (float) Math.sin(a);
        // Radial (where it points) and tangent directions across the axis.
        float rx = u[0] * c + v[0] * s;
        float ry = u[1] * c + v[1] * s;
        float rz = u[2] * c + v[2] * s;
        float tx = v[0] * c - u[0] * s;
        float ty = v[1] * c - u[1] * s;
        float tz = v[2] * c - u[2] * s;
        float nx = facing.getStepX();
        float ny = facing.getStepY();
        float nz = facing.getStepZ();
        float cx = 0.5f + rx * (LENS_RADIUS + 0.01f) + nx * LENS_HEIGHT * 0.45f;
        float cy = 0.5f + ry * (LENS_RADIUS + 0.01f) + ny * LENS_HEIGHT * 0.45f;
        float cz = 0.5f + rz * (LENS_RADIUS + 0.01f) + nz * LENS_HEIGHT * 0.45f;
        float w = 0.10f;
        float h = LENS_HEIGHT * 0.5f;
        int full = LightTexture.FULL_BRIGHT;
        // Both windings, so it shows from either side.
        for (int side = 0; side < 2; side++) {
            float sgn = side == 0 ? 1.0f : -1.0f;
            vertex(out, p, cx - tx * w * sgn - nx * h, cy - ty * w * sgn - ny * h, cz - tz * w * sgn - nz * h,
                    0.45f, 0.55f, rx, ry, rz, 1.0f, 1.0f, full, overlay);
            vertex(out, p, cx + tx * w * sgn - nx * h, cy + ty * w * sgn - ny * h, cz + tz * w * sgn - nz * h,
                    0.55f, 0.55f, rx, ry, rz, 1.0f, 1.0f, full, overlay);
            vertex(out, p, cx + tx * w * sgn + nx * h, cy + ty * w * sgn + ny * h, cz + tz * w * sgn + nz * h,
                    0.55f, 0.45f, rx, ry, rz, 1.0f, 1.0f, full, overlay);
            vertex(out, p, cx - tx * w * sgn + nx * h, cy - ty * w * sgn + ny * h, cz - tz * w * sgn + nz * h,
                    0.45f, 0.45f, rx, ry, rz, 1.0f, 1.0f, full, overlay);
        }
    }

    /**
     * One beam along {@link #dir}: two crossed blades from the lens outward, widening toward the
     * far end; the glow texture fades them out across and along (u across, v from the lens out).
     */
    private void beam(VertexConsumer out, PoseStack.Pose p, float nx, float ny, float nz) {
        float dx = dir.x;
        float dy = dir.y;
        float dz = dir.z;
        // A side direction across the beam: dir x facing (never zero: the beam leans 70 degrees off the axis).
        float sx = dy * nz - dz * ny;
        float sy = dz * nx - dx * nz;
        float sz = dx * ny - dy * nx;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx /= sl;
        sy /= sl;
        sz /= sl;
        // The other side direction: dir x side.
        float qx = dy * sz - dz * sy;
        float qy = dz * sx - dx * sz;
        float qz = dx * sy - dy * sx;
        float ox = 0.5f + nx * LENS_HEIGHT * 0.5f;
        float oy = 0.5f + ny * LENS_HEIGHT * 0.5f;
        float oz = 0.5f + nz * LENS_HEIGHT * 0.5f;
        float ex = ox + dx * BEAM_LENGTH;
        float ey = oy + dy * BEAM_LENGTH;
        float ez = oz + dz * BEAM_LENGTH;
        for (int blade = 0; blade < 2; blade++) {
            // Two blades crossed at 90 degrees, each turned 45 degrees off "flat on the surface".
            float bx = blade == 0 ? (sx + qx) * COS45 : (sx - qx) * COS45;
            float by = blade == 0 ? (sy + qy) * COS45 : (sy - qy) * COS45;
            float bz = blade == 0 ? (sz + qz) * COS45 : (sz - qz) * COS45;
            for (int side = 0; side < 2; side++) {
                float sgn = side == 0 ? 1.0f : -1.0f;
                beamVertex(out, p, ox - bx * BEAM_NEAR * sgn, oy - by * BEAM_NEAR * sgn, oz - bz * BEAM_NEAR * sgn, 0.0f, 0.0f);
                beamVertex(out, p, ox + bx * BEAM_NEAR * sgn, oy + by * BEAM_NEAR * sgn, oz + bz * BEAM_NEAR * sgn, 1.0f, 0.0f);
                beamVertex(out, p, ex + bx * BEAM_FAR * sgn, ey + by * BEAM_FAR * sgn, ez + bz * BEAM_FAR * sgn, 1.0f, 1.0f);
                beamVertex(out, p, ex - bx * BEAM_FAR * sgn, ey - by * BEAM_FAR * sgn, ez - bz * BEAM_FAR * sgn, 0.0f, 1.0f);
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox(Level2SpinPropBlock.Entity beacon) {
        return new AABB(beacon.getBlockPos()).inflate(REACH);
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    private static float octCos(int i) {
        return (float) Math.cos(i * Math.PI / 4.0 + Math.PI / 8.0);
    }

    private static float octSin(int i) {
        return (float) Math.sin(i * Math.PI / 4.0 + Math.PI / 8.0);
    }

    private static void beamVertex(VertexConsumer out, PoseStack.Pose p, float x, float y, float z, float tu, float tv) {
        out.addVertex(p, x, y, z).setColor(R, G, B, BEAM_ALPHA).setUv(tu, tv).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0.0f, 1.0f, 0.0f);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose p, float x, float y, float z, float tu, float tv,
                               float nx, float ny, float nz, float bright, float alpha, int light, int overlay) {
        out.addVertex(p, x, y, z).setColor(R * bright, G * bright, B * bright, alpha).setUv(tu, tv).setOverlay(overlay)
                .setLight(light).setNormal(p, nx, ny, nz);
    }
}
