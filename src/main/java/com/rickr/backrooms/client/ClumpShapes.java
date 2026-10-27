package com.rickr.backrooms.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Small geometry kit shared by the three Level 39 tunnel creatures built in code: the Clump, the
 * Skin-stealer and the Wretch. Minecraft models are made only of boxes, so "a limb from here to
 * there" has to be worked out as one box that is long in one direction and then turned to point
 * the right way. These helpers do that maths once, so the model classes can talk in terms of
 * bones ("a forearm from the elbow to the wrist") instead of angles.
 *
 * <p>Model space everywhere: units are pixels (1/16 block), +Y points DOWN, the front is -Z.
 */
final class ClumpShapes {
    private ClumpShapes() {}

    /**
     * One straight square rod from A to B, textured from (u, v). The box is built along +Y from
     * its pivot and then turned so +Y points at B. With ModelPart's Z-Y-X rotation order and
     * yRot = 0, local +Y ends up at (-cos(x)sin(z), cos(x)cos(z), sin(x)); solving that for the
     * direction A-&gt;B gives the two angles below (the same trick as {@code BacteriaModel}).
     */
    static void rod(PartDefinition parent, String name, int u, int v,
                    float ax, float ay, float az, float bx, float by, float bz, float thickness) {
        rod(parent, name, u, v, ax, ay, az, bx, by, bz, thickness, thickness);
    }

    /** Like {@link #rod} but with a different width (x) and depth (z): flat bones, ribs, fingers. */
    static void rod(PartDefinition parent, String name, int u, int v,
                    float ax, float ay, float az, float bx, float by, float bz, float width, float depth) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-3f) {
            return;
        }
        float xRot = (float) Math.asin(Mth.clamp(dz / length, -1.0f, 1.0f));
        float zRot = (float) Mth.atan2(-dx / length, dy / length);
        parent.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).addBox(-width / 2.0f, 0.0f, -depth / 2.0f, width, length, depth),
                PartPose.offsetAndRotation(ax, ay, az, xRot, 0.0f, zRot));
    }

    /** The two angles (xRot, zRot) that turn a +Y-built part to point along (dx, dy, dz). */
    static float[] aim(float dx, float dy, float dz) {
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-4f) {
            return new float[] {0.0f, 0.0f};
        }
        return new float[] {
                (float) Math.asin(Mth.clamp(dz / length, -1.0f, 1.0f)),
                (float) Mth.atan2(-dx / length, dy / length)};
    }

    /**
     * A rounded lump centred on (x, y, z): a core box plus three slightly smaller boxes each
     * stretched along one axis, so the corners are cut off and it reads as round from any side.
     * Returns the lump's own part (pivot at its centre) so callers can scale it to make it pulse.
     */
    static PartDefinition lump(PartDefinition parent, String name, int u, int v,
                               float x, float y, float z, float w, float h, float d,
                               float xRot, float yRot, float zRot) {
        CubeListBuilder cubes = CubeListBuilder.create()
                .texOffs(u, v).addBox(-w * 0.4f, -h * 0.4f, -d * 0.4f, w * 0.8f, h * 0.8f, d * 0.8f)
                .texOffs(u + 2, v + 2).addBox(-w * 0.5f, -h * 0.3f, -d * 0.3f, w, h * 0.6f, d * 0.6f)
                .texOffs(u + 4, v + 1).addBox(-w * 0.3f, -h * 0.5f, -d * 0.3f, w * 0.6f, h, d * 0.6f)
                .texOffs(u + 1, v + 4).addBox(-w * 0.3f, -h * 0.3f, -d * 0.5f, w * 0.6f, h * 0.6f, d);
        return parent.addOrReplaceChild(name, cubes, PartPose.offsetAndRotation(x, y, z, xRot, yRot, zRot));
    }

    /** An empty part used as a joint: everything added to it turns around (x, y, z). */
    static PartDefinition joint(PartDefinition parent, String name, float x, float y, float z) {
        return parent.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offset(x, y, z));
    }

    static float[] normalize(float x, float y, float z) {
        float len = Mth.sqrt(x * x + y * y + z * z);
        return len < 1.0e-6f ? new float[] {0.0f, 1.0f, 0.0f} : new float[] {x / len, y / len, z / len};
    }

    static float[] cross(float[] a, float[] b) {
        return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /**
     * Deterministic jitter that holds one value for {@code period} ticks and then jumps to the
     * next, in -amplitude..amplitude. Same seed and time give the same value on every client.
     */
    static float twitch(int seed, float t, int period, float amplitude) {
        int bucket = Mth.floor(t / period);
        long h = (long) (seed * 0x9E3779B1L) ^ (bucket * 0x85EBCA77L);
        h ^= h >>> 15;
        h *= 0x2C1B3C6DL;
        h ^= h >>> 12;
        float unit = (h & 0xFFFF) / 65535.0f;
        return (unit * 2.0f - 1.0f) * amplitude;
    }

    /**
     * Like {@link #twitch} but eased: glides smoothly from one random value to the next over each
     * period, for slow organic drifting (a hanging arm turning, a head tilting).
     */
    static float drift(int seed, float t, int period, float amplitude) {
        float f = t / period;
        int bucket = Mth.floor(f);
        float k = f - bucket;
        k = k * k * (3.0f - 2.0f * k);
        float a = twitch(seed, bucket * (float) period, period, amplitude);
        float b = twitch(seed, (bucket + 1) * (float) period, period, amplitude);
        return Mth.lerp(k, a, b);
    }
}
