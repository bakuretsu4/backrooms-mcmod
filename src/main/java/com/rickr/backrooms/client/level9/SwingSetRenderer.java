package com.rickr.backrooms.client.level9;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.yard.SwingSetBlock;
import com.rickr.backrooms.block.yard.SwingSetBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the two seats of a Level 9 swing set and their chains, hanging from the top bar and
 * swaying gently on their own ({@link SwingSetBlockEntity#seatAngle}: no wind, nobody on them).
 * The frame itself is the middle block's normal block model.
 *
 * <p>Every vertex is worked out here by hand (the swing about the bar, then the turn for FACING)
 * and written through two reused scratch vectors, so drawing allocates nothing per frame (the
 * PoseStack push/pop and the vertex helpers that take a matrix would each make new objects).
 * Model units are pixels, in the middle block's frame for FACING = north (the bar runs along x).
 */
public class SwingSetRenderer implements BlockEntityRenderer<SwingSetBlockEntity> {
    private static final ResourceLocation TEXTURE = Backrooms.id("textures/entity/l9_swing.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);

    /** Where the chains hang from (the underside of the bar), in pixels. */
    private static final float PIVOT_Y = 29.5f;
    private static final float PIVOT_Z = 8.0f;
    /** Seat centres along the bar (the set's middle block spans 0-16; the seats sit a little apart). */
    private static final float[] SEAT_X = {1.0f, 15.0f};
    private static final float SEAT_HALF_W = 3.75f;
    private static final float SEAT_HALF_D = 1.75f;
    private static final float SEAT_Y0 = 8.0f;
    private static final float SEAT_Y1 = 9.0f;
    private static final float CHAIN_HALF = 0.2f;
    private static final float CHAIN_INSET = 0.5f;
    // Texture regions (0-1): chains on the left half, the rubber seat on the right.
    private static final float[] CHAIN_UV = {0.0f, 0.0f, 0.5f, 0.5f};
    private static final float[] SEAT_UV = {0.5f, 0.0f, 1.0f, 0.5f};

    // Per-call state (render thread only), kept in fields so nothing is allocated per frame.
    private final Vector3f pos = new Vector3f();
    private final Vector3f nrm = new Vector3f();
    private Matrix4f poseMatrix;
    private Matrix3f normalMatrix;
    private VertexConsumer out;
    private int light;
    private Direction facing = Direction.NORTH;
    private float swingCos = 1.0f;
    private float swingSin;

    public SwingSetRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SwingSetBlockEntity swing, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BlockState state = swing.getBlockState();
        if (!(state.getBlock() instanceof SwingSetBlock) || swing.getLevel() == null) {
            return;
        }
        facing = state.getValue(SwingSetBlock.FACING);
        PoseStack.Pose pose = poseStack.last();
        poseMatrix = pose.pose();
        normalMatrix = pose.normal();
        out = buffers.getBuffer(RENDER_TYPE);
        light = packedLight;
        float ticks = swing.getLevel().getGameTime() + partialTick;
        for (int seat = 0; seat < 2; seat++) {
            float angle = SwingSetBlockEntity.seatAngle(swing.getBlockPos(), seat, ticks);
            swingCos = Mth.cos(angle);
            swingSin = Mth.sin(angle);
            float cx = SEAT_X[seat];
            box(cx - SEAT_HALF_W, SEAT_Y0, PIVOT_Z - SEAT_HALF_D, cx + SEAT_HALF_W, SEAT_Y1, PIVOT_Z + SEAT_HALF_D, SEAT_UV);
            for (int end = -1; end <= 1; end += 2) {
                float x = cx + end * (SEAT_HALF_W - CHAIN_INSET);
                box(x - CHAIN_HALF, SEAT_Y1, PIVOT_Z - CHAIN_HALF, x + CHAIN_HALF, PIVOT_Y, PIVOT_Z + CHAIN_HALF, CHAIN_UV);
            }
        }
        out = null;
    }

    /** One box (pixels, north frame, before the swing), all six faces. */
    private void box(float x0, float y0, float z0, float x1, float y1, float z1, float[] uv) {
        quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, uv);   // bottom
        quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, uv);    // top
        quad(x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, 0, 0, -1, uv);   // north
        quad(x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, 0, 0, 1, uv);    // south
        quad(x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, -1, 0, 0, uv);   // west
        quad(x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, 1, 0, 0, uv);    // east
    }

    private void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                      float dx, float dy, float dz, float nx, float ny, float nz, float[] uv) {
        vertex(ax, ay, az, nx, ny, nz, uv[0], uv[1]);
        vertex(bx, by, bz, nx, ny, nz, uv[0], uv[3]);
        vertex(cx, cy, cz, nx, ny, nz, uv[2], uv[3]);
        vertex(dx, dy, dz, nx, ny, nz, uv[2], uv[1]);
    }

    /** Swing about the bar, turn for FACING about the block centre, then into the pose; emit. */
    private void vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
        // The swing: a turn about the bar (the x axis through the pivot). Positive = toward the front.
        float ry = y - PIVOT_Y;
        float rz = z - PIVOT_Z;
        float sy = PIVOT_Y + ry * swingCos - rz * swingSin;
        float sz = PIVOT_Z + ry * swingSin + rz * swingCos;
        float sny = ny * swingCos - nz * swingSin;
        float snz = ny * swingSin + nz * swingCos;
        // FACING: the same quarter turns (clockwise seen from above) as the blockstate's "y".
        float wx;
        float wz;
        float wnx;
        float wnz;
        switch (facing) {
            case EAST -> { wx = 16.0f - sz; wz = x; wnx = -snz; wnz = nx; }
            case SOUTH -> { wx = 16.0f - x; wz = 16.0f - sz; wnx = -nx; wnz = -snz; }
            case WEST -> { wx = sz; wz = 16.0f - x; wnx = snz; wnz = -nx; }
            default -> { wx = x; wz = sz; wnx = nx; wnz = snz; }
        }
        poseMatrix.transformPosition(wx / 16.0f, sy / 16.0f, wz / 16.0f, pos);
        normalMatrix.transform(wnx, sny, wnz, nrm);
        out.addVertex(pos.x(), pos.y(), pos.z(), 0xFFFFFFFF, u, v, OverlayTexture.NO_OVERLAY, light, nrm.x(), nrm.y(), nrm.z());
    }

    /** The whole set, so the seats don't vanish when only the middle block leaves the screen. */
    @Override
    public AABB getRenderBoundingBox(SwingSetBlockEntity swing) {
        return swing.renderBounds();
    }
}
