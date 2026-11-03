package com.rickr.backrooms.client.anomaly.level2;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.Level2SpinPropBlock;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The blades of Level 2's wall fan (registry/Level2SpinBlocks WALL_FAN). The housing, grille, hub
 * and struts are the block's own model; this draws only the five-bladed disc behind the grille,
 * turning with the corridor's spin ({@link Level2SpinProps#fanAngle}). One textured square (the
 * same blade texture as the old static fan housing), both sides, lit by the light in the lane in
 * front of it (the fan's own cell is inside the wall).
 */
public final class WallFanRenderer implements BlockEntityRenderer<Level2SpinPropBlock.Entity> {
    private static final ResourceLocation BLADES = Backrooms.id("textures/block/l2_fan_blades.png");
    /** The blades sit 5.6 px behind the grille, i.e. 0.15 of a block in front of the block's middle. */
    private static final float DEPTH = 0.15f;
    /** Half the blade disc's square (7 px of the 8 px half-block). */
    private static final float RADIUS = 0.4375f;

    private final BlockPos.MutableBlockPos front = new BlockPos.MutableBlockPos();

    public WallFanRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(Level2SpinPropBlock.Entity fan, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
                       int overlay) {
        Level level = fan.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = fan.getBlockPos();
        Direction facing = fan.getBlockState().getValue(Level2SpinPropBlock.FACING);
        int lit = LevelRenderer.getLightColor(level, front.setWithOffset(pos, facing));
        double angle = Level2SpinProps.fanAngle(pos.getX(), pos.getY(), pos.getZ(), partialTick);
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        float[] u = Level2SpinProps.u(facing);
        float[] v = Level2SpinProps.v(facing);
        // The disc's two half-diagonals, turned: a = u', b = v' (scaled to the radius).
        float ax = (u[0] * c + v[0] * s) * RADIUS;
        float ay = (u[1] * c + v[1] * s) * RADIUS;
        float az = (u[2] * c + v[2] * s) * RADIUS;
        float bx = (v[0] * c - u[0] * s) * RADIUS;
        float by = (v[1] * c - u[1] * s) * RADIUS;
        float bz = (v[2] * c - u[2] * s) * RADIUS;
        float nx = facing.getStepX();
        float ny = facing.getStepY();
        float nz = facing.getStepZ();
        float ox = 0.5f + nx * DEPTH;
        float oy = 0.5f + ny * DEPTH;
        float oz = 0.5f + nz * DEPTH;

        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(BLADES));
        PoseStack.Pose p = pose.last();
        vertex(out, p, ox - ax - bx, oy - ay - by, oz - az - bz, 0, 1, nx, ny, nz, lit, overlay);
        vertex(out, p, ox + ax - bx, oy + ay - by, oz + az - bz, 1, 1, nx, ny, nz, lit, overlay);
        vertex(out, p, ox + ax + bx, oy + ay + by, oz + az + bz, 1, 0, nx, ny, nz, lit, overlay);
        vertex(out, p, ox - ax + bx, oy - ay + by, oz - az + bz, 0, 0, nx, ny, nz, lit, overlay);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose p, float x, float y, float z, float tu, float tv,
                               float nx, float ny, float nz, int light, int overlay) {
        out.addVertex(p, x, y, z).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(tu, tv).setOverlay(overlay).setLight(light)
                .setNormal(p, nx, ny, nz);
    }
}
