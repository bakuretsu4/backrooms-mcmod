package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.CeilingFanBlock;
import com.rickr.backrooms.world.layout.Level0Street;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Draws the big industrial ceiling fan of Level 0's street hall (block/CeilingFanBlock): a rod up
 * to the ceiling, a squat motor housing, and eight long flat blades, about 12 blocks across, slowly
 * turning (once every 3 seconds). All boxes, coloured by their vertices on a plain patch of the
 * ceiling-tile texture, so it adds nothing to the atlas. Like the film's: dark grey metal.
 */
public final class CeilingFanRenderer implements BlockEntityRenderer<CeilingFanBlock.Entity> {
    private static final ResourceLocation TEX = Backrooms.id("textures/block/ceiling_tile.png");
    private static final float U = 0.5f;
    private static final float V = 0.5f;
    private static final int BLADES = 8;
    /** Blade length from the hub (blocks), its width and thickness. */
    private static final float BLADE_LEN = 6.0f;
    private static final float BLADE_W = 0.9f;
    private static final float BLADE_T = 0.08f;
    /** A turn takes this long (seconds). */
    private static final float TURN_SECONDS = 3.0f;
    private static final float METAL = 0.30f;
    private static final float METAL_DARK = 0.20f;

    public CeilingFanRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CeilingFanBlock.Entity fan, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
                       int overlay) {
        VertexConsumer out = buffers.getBuffer(RenderType.entitySolid(TEX));
        long time = fan.getLevel() == null ? 0 : fan.getLevel().getGameTime();
        // Each fan turns a little out of step with the others (its position seeds the phase).
        float phase = (fan.getBlockPos().asLong() & 0xFF) * 1.4f;
        float angle = ((time + partialTick) / 20.0f / TURN_SECONDS * 360.0f + phase) % 360.0f;
        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        // The rod up to the ceiling and the motor housing.
        int rod = Level0Street.FAN_ROD;
        box(out, pose, -0.06f, 0.0f, -0.06f, 0.06f, rod + 0.5f, 0.06f, METAL_DARK, light, overlay);
        box(out, pose, -0.38f, -0.22f, -0.38f, 0.38f, 0.22f, 0.38f, METAL, light, overlay);
        box(out, pose, -0.2f, -0.34f, -0.2f, 0.2f, -0.22f, 0.2f, METAL_DARK, light, overlay);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        for (int i = 0; i < BLADES; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(i * 360.0f / BLADES));
            // A slight pitch, like real blades, so they catch the light differently.
            pose.mulPose(Axis.XP.rotationDegrees(8.0f));
            box(out, pose, 0.3f, -BLADE_T, -BLADE_W / 2, 0.3f + BLADE_LEN, BLADE_T, BLADE_W / 2,
                    i % 2 == 0 ? METAL : METAL * 0.92f, light, overlay);
            pose.popPose();
        }
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(CeilingFanBlock.Entity fan) {
        return new AABB(fan.getBlockPos()).inflate(BLADE_LEN + 1.0, Level0Street.FAN_ROD + 1.0, BLADE_LEN + 1.0);
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public boolean shouldRenderOffScreen(CeilingFanBlock.Entity fan) {
        return true;
    }

    /** A solid box with all six faces, flat-coloured grey {@code c}. */
    private static void box(VertexConsumer out, PoseStack pose, float x0, float y0, float z0, float x1, float y1, float z1,
                            float c, int light, int overlay) {
        PoseStack.Pose p = pose.last();
        quad(out, p, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, c * 1.1f, light, overlay);
        quad(out, p, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, c * 0.7f, light, overlay);
        quad(out, p, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1, c * 0.85f, light, overlay);
        quad(out, p, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, c * 0.85f, light, overlay);
        quad(out, p, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, c * 0.95f, light, overlay);
        quad(out, p, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0, c * 0.95f, light, overlay);
    }

    private static void quad(VertexConsumer out, PoseStack.Pose p, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz, float c,
                             int light, int overlay) {
        vertex(out, p, ax, ay, az, nx, ny, nz, c, light, overlay);
        vertex(out, p, bx, by, bz, nx, ny, nz, c, light, overlay);
        vertex(out, p, cx, cy, cz, nx, ny, nz, c, light, overlay);
        vertex(out, p, dx, dy, dz, nx, ny, nz, c, light, overlay);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose p, float x, float y, float z, float nx, float ny, float nz,
                               float c, int light, int overlay) {
        out.addVertex(p, x, y, z).setColor(c, c, c * 1.02f, 1.0f).setUv(U, V).setOverlay(overlay).setLight(light)
                .setNormal(p, nx, ny, nz);
    }
}
