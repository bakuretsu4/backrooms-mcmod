package com.rickr.backrooms.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Small building blocks shared by the Level 227 animal models (wolf, stag, squirrel), the same two
 * tricks the Hound model uses: a rod from point A to point B (for fur tufts, teeth, horns, claws
 * and whiskers) and a two-bone leg solver that puts a paw or hoof exactly where the gait wants it.
 *
 * <p>Model space, as everywhere in vanilla models: units are pixels (1/16 block), +Y points down,
 * the front faces -Z.
 */
final class L227ModelKit {

    /**
     * One straight square rod from A to B, textured from (texU, texV). The cube is modelled along
     * +Y from its pivot, then turned so +Y points at B.
     */
    static PartDefinition rod(PartDefinition parent, String name, int texU, int texV,
                              float ax, float ay, float az, float bx, float by, float bz, float thickness) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-3f) {
            length = 1.0e-3f;
        }
        float xRot = (float) Math.asin(Mth.clamp(dz / length, -1.0f, 1.0f));
        float zRot = (float) Mth.atan2(-dx / length, dy / length);
        float half = thickness / 2.0f;
        return parent.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(texU, texV).addBox(-half, 0.0f, -half, thickness, length, thickness),
                PartPose.offsetAndRotation(ax, ay, az, xRot, 0.0f, zRot));
    }

    /**
     * A tapering chain of rods through the points {@code pts} (x, y, z triples), thickness going
     * from {@code from} to {@code to}: horns, tails, long hairs. Parts are named name_0, name_1...
     */
    static void chain(PartDefinition parent, String name, int texU, int texV, float[] pts, float from, float to) {
        int n = pts.length / 3 - 1;
        for (int i = 0; i < n; i++) {
            float t = n == 1 ? 0.0f : i / (float) (n - 1);
            rod(parent, name + "_" + i, texU, texV, pts[i * 3], pts[i * 3 + 1], pts[i * 3 + 2],
                    pts[i * 3 + 3], pts[i * 3 + 4], pts[i * 3 + 5], Mth.lerp(t, from, to));
        }
    }

    /**
     * Two-bone solver: bends {@code upper} and {@code lower} (both modelled along +Y) so the end of
     * {@code lower} lands at (dy, dz) from the upper joint, in the parent's frame. {@code jointBack}:
     * the middle joint points backward (+Z, an elbow) instead of forward (-Z, a knee).
     */
    static void reach(ModelPart upper, ModelPart lower, float dy, float dz, float l1, float l2, boolean jointBack) {
        float d = Mth.sqrt(dy * dy + dz * dz);
        d = Mth.clamp(d, Math.abs(l1 - l2) + 0.01f, (l1 + l2) * 0.999f);
        float toTarget = (float) Mth.atan2(dz, dy);
        float beta = (float) Math.acos(Mth.clamp((l1 * l1 + d * d - l2 * l2) / (2.0f * l1 * d), -1.0f, 1.0f));
        float gamma = (float) Math.acos(Mth.clamp((l1 * l1 + l2 * l2 - d * d) / (2.0f * l1 * l2), -1.0f, 1.0f));
        float bend = Mth.PI - gamma;
        // atan2(dz, dy) is the angle from +Y towards +Z; xRot turns +Y towards +Z for positive
        // angles, so the bone points at the target with xRot = toTarget.
        if (jointBack) {
            upper.xRot = toTarget + beta;
            lower.xRot = -bend;
        } else {
            upper.xRot = toTarget - beta;
            lower.xRot = bend;
        }
    }

    /**
     * Places a two-bone leg so its end lands at model-space height {@code targetY} and {@code
     * offsetZ} forward/back of its joint (joint given in body space), working through the body's
     * pitch. Same maths as the Hound's legs.
     */
    static void placeLeg(ModelPart body, ModelPart upper, ModelPart lower, float jointY, float jointZ,
                         float targetY, float offsetZ, float l1, float l2, boolean jointBack) {
        float b = body.xRot;
        float cos = Mth.cos(b);
        float sin = Mth.sin(b);
        float worldY = body.y + jointY * cos - jointZ * sin;
        float dyWorld = targetY - worldY;
        float dzWorld = offsetZ;
        float dy = dyWorld * cos + dzWorld * sin;
        float dz = -dyWorld * sin + dzWorld * cos;
        reach(upper, lower, dy, dz, l1, l2, jointBack);
    }

    /** Ease in and out (0..1 → 0..1). */
    static float smooth(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private L227ModelKit() {}
}
