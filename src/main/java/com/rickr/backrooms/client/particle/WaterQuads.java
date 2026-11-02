package com.rickr.backrooms.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Shared bits of the Level 227 splash particles ({@link WaterDropletParticle},
 * {@link WaterBubbleParticle}, {@link WaterSurfaceParticle}).
 *
 * <p>Why our own quad drawing: vanilla's particle quad makes a few small Java objects per particle
 * every frame (a rotation, a vector per corner). A splash can put a hundred particles on screen, so
 * these draw their corners straight from the camera's own vectors and allocate nothing. Light is
 * looked up once per tick (not per frame) with one reused block position. All of this runs on the
 * client's main thread only (particles tick and draw there), so the shared scratch is safe.
 */
final class WaterQuads {
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    private WaterQuads() {}

    /** Packed light (block + sky) at a position, as vanilla particles use it. */
    static int light(ClientLevel level, double x, double y, double z) {
        CURSOR.set(x, y, z);
        return level.hasChunkAt(CURSOR) ? LevelRenderer.getLightColor(level, CURSOR) : 0;
    }

    /**
     * Height of the water surface in the block at (x, y, z) if that point is under it, else NaN.
     * (Only that block: enough to tell whether a drop or bubble is in the water this tick.)
     */
    static double waterTop(ClientLevel level, double x, double y, double z) {
        CURSOR.set(x, y, z);
        FluidState fluid = level.getFluidState(CURSOR);
        if (!fluid.is(FluidTags.WATER)) {
            return Double.NaN;
        }
        double top = CURSOR.getY() + fluid.getHeight(level, CURSOR);
        return y < top ? top : Double.NaN;
    }

    /**
     * A square facing the camera, centred on (x, y, z), {@code size} blocks from centre to edge.
     * Same corners and winding as vanilla's particle quad (camera right = minus the left vector).
     */
    static void billboard(VertexConsumer out, Camera camera, double x, double y, double z, float size,
                          float u0, float u1, float v0, float v1, float r, float g, float b, float a, int light) {
        Vec3 eye = camera.getPosition();
        float cx = (float) (x - eye.x);
        float cy = (float) (y - eye.y);
        float cz = (float) (z - eye.z);
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        float lx = left.x() * size;
        float ly = left.y() * size;
        float lz = left.z() * size;
        float ux = up.x() * size;
        float uy = up.y() * size;
        float uz = up.z() * size;
        out.addVertex(cx - lx - ux, cy - ly - uy, cz - lz - uz).setUv(u1, v1).setColor(r, g, b, a).setLight(light);
        out.addVertex(cx - lx + ux, cy - ly + uy, cz - lz + uz).setUv(u1, v0).setColor(r, g, b, a).setLight(light);
        out.addVertex(cx + lx + ux, cy + ly + uy, cz + lz + uz).setUv(u0, v0).setColor(r, g, b, a).setLight(light);
        out.addVertex(cx + lx - ux, cy + ly - uy, cz + lz - uz).setUv(u0, v1).setColor(r, g, b, a).setLight(light);
    }

    /**
     * A square lying flat on the water at height y, {@code size} blocks from centre to edge, turned
     * by the angle whose cosine/sine are given. Wound to face up (seen from above the water).
     */
    static void flat(VertexConsumer out, Camera camera, double x, double y, double z, float size, float cos, float sin,
                     float u0, float u1, float v0, float v1, float r, float g, float b, float a, int light) {
        Vec3 eye = camera.getPosition();
        float cx = (float) (x - eye.x);
        float cy = (float) (y - eye.y);
        float cz = (float) (z - eye.z);
        // The square's own x and z axes, turned and scaled.
        float ax = cos * size;
        float az = sin * size;
        float bx = -sin * size;
        float bz = cos * size;
        out.addVertex(cx - ax - bx, cy, cz - az - bz).setUv(u0, v0).setColor(r, g, b, a).setLight(light);
        out.addVertex(cx - ax + bx, cy, cz - az + bz).setUv(u0, v1).setColor(r, g, b, a).setLight(light);
        out.addVertex(cx + ax + bx, cy, cz + az + bz).setUv(u1, v1).setColor(r, g, b, a).setLight(light);
        out.addVertex(cx + ax - bx, cy, cz + az - bz).setUv(u1, v0).setColor(r, g, b, a).setLight(light);
    }
}
