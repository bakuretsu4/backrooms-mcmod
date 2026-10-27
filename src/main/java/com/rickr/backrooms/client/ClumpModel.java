package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.Clump;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The Clump, built in code: a person-sized knot of human arms and legs wound around each other.
 *
 * <pre>
 *        _/‾‾\__/‾\_         the mass: ~30 limbs wrapped around a dark, wet core, each limb an
 *      /‾ \\  //  ‾\         upper part, a knobbly elbow or knee, a lower part and a hand that
 *     |  ==\\//==   |        grips the limbs around it (or a foot, or a stump)
 *      \__//  \\__ /
 *        | |  | |            4 bait arms hanging out of the front (swaying, fingers curling)
 *        | |  | |
 *        w w  w w            + one very long arm (6 blocks, 12 segments) coiled away inside,
 *                              which shoots out, curves onto its prey and grips with 3 long fingers
 * </pre>
 *
 * <p>Everything comes from fixed random seeds, so every client sees the same tangle. The mass
 * barely moves (it breathes, and shivers just before it strikes); the life is in the arms.
 *
 * <p>The long arm is a chain of 12 bones. To make its tip land exactly on the prey, every joint
 * bends by the same angle, so the arm forms a circular arc whose chord (the straight line from the
 * mouth of the hole to the prey) is exactly the prey's distance. While it is still flying out
 * (shorter than that distance) it simply points straight at them.
 *
 * <p>Model space: units are pixels (1/16 block), +Y points down, the front (the open side of its
 * hole) is -Z, the feet (bottom of the hitbox) are at y = 24. Texture: 128x128 units (the PNG is
 * twice that), in swatches: pale skin, bruised skin, dark core, hands, nails, wrinkled joints.
 */
public class ClumpModel extends HierarchicalModel<Clump> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Backrooms.id("clump"), "main");

    // Texture swatches (u, v, width, height) in texture units.
    private static final int[] SKIN = {0, 0, 64, 64};
    private static final int[] BRUISE = {64, 0, 64, 64};
    private static final int[] CORE = {0, 64, 64, 64};
    private static final int[] HAND = {64, 64, 32, 32};
    private static final int[] NAIL = {96, 64, 32, 32};
    private static final int[] JOINT = {64, 96, 64, 32};

    // The mass: an ellipsoid (radii in px) whose centre is 0.9 blocks above the feet.
    private static final float RX = 12.5f;
    private static final float RY = 13.5f;
    private static final float RZ = 11.5f;
    private static final float MASS_Y = 24.0f - 14.4f;

    // Bait arms.
    private static final int BAIT = 4;
    private static final float BAIT_UPPER = 15.0f;
    private static final float BAIT_FORE = 14.0f;
    private static final float PALM = 4.0f;
    private static final float[] BAIT_X = {-7.0f, -2.6f, 2.4f, 7.2f};

    // The long arm.
    private static final int SEGMENTS = 12;
    private static final float SEGMENT = Clump.ARM_LENGTH_PX / SEGMENTS;
    private static final int CLAWS = 3;
    private static final float[] CLAW_PHALANX = {3.6f, 3.0f, 2.4f};

    /** How many of the mass's hands twitch on their own. */
    private static final int TWITCHERS = 8;

    private final ModelPart root;
    private final ModelPart mass;
    private final ModelPart[] baitShoulder = new ModelPart[BAIT];
    private final ModelPart[] baitElbow = new ModelPart[BAIT];
    private final ModelPart[] baitWrist = new ModelPart[BAIT];
    private final ModelPart[][] baitKnuckle = new ModelPart[BAIT][4];
    private final ModelPart[][] baitTip = new ModelPart[BAIT][4];
    private final ModelPart[] segment = new ModelPart[SEGMENTS];
    private final ModelPart[] bone = new ModelPart[SEGMENTS];
    private final ModelPart[][] claw = new ModelPart[CLAWS][CLAW_PHALANX.length];
    private final List<ModelPart> twitchHands = new ArrayList<>();

    public ClumpModel(ModelPart root) {
        this.root = root;
        this.mass = root.getChild("mass");
        for (int k = 0; k < BAIT; k++) {
            baitShoulder[k] = mass.getChild("bait" + k);
            baitElbow[k] = baitShoulder[k].getChild("elbow");
            baitWrist[k] = baitElbow[k].getChild("wrist");
            for (int f = 0; f < 4; f++) {
                baitKnuckle[k][f] = baitWrist[k].getChild("finger" + f);
                baitTip[k][f] = baitKnuckle[k][f].getChild("tip");
            }
        }
        ModelPart parent = root;
        for (int i = 0; i < SEGMENTS; i++) {
            segment[i] = parent.getChild("seg" + i);
            bone[i] = segment[i].getChild("bone");
            parent = segment[i];
        }
        ModelPart hand = parent.getChild("claws");
        for (int c = 0; c < CLAWS; c++) {
            ModelPart p = hand.getChild("claw" + c);
            for (int j = 0; j < CLAW_PHALANX.length; j++) {
                p = p.getChild("p" + j);
                claw[c][j] = p;
            }
        }
        for (int i = 0; i < TWITCHERS; i++) {
            twitchHands.add(mass.getChild("limb" + i).getChild("hand"));
        }
    }

    // ================================================================== building

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition mass = root.addOrReplaceChild("mass", CubeListBuilder.create(), PartPose.offset(0, MASS_Y, 0));

        RandomSource rng = RandomSource.create(0xC1A3B5L);
        core(mass, rng);
        int limbs = 46;
        for (int i = 0; i < limbs; i++) {
            // The first TWITCHERS limbs always end in a hand (those hands twitch).
            limb(mass, "limb" + i, rng, i < TWITCHERS, spiralDirection(i, limbs, rng));
        }
        for (int k = 0; k < BAIT; k++) {
            baitArm(mass, k, RandomSource.create(0xBA17L + k * 7919L));
        }
        longArm(root, RandomSource.create(0x10A6A3L));
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** The dark, wet core that shows in the gaps between the limbs. */
    private static void core(PartDefinition mass, RandomSource rng) {
        for (int i = 0; i < 8; i++) {
            // Sized and placed so the core stays inside the shell of limbs: it should only show
            // as darkness in the gaps, never bulge out past them.
            float w = 8.0f + rng.nextFloat() * 4.0f;
            float h = 8.0f + rng.nextFloat() * 4.0f;
            float d = 8.0f + rng.nextFloat() * 4.0f;
            float[] dir = randomDirection(rng);
            float r = i == 0 ? 0.0f : 0.2f + rng.nextFloat() * 0.2f;
            int[] uv = uv(rng, CORE, 2 * (w + d) + 5, d + h + 5);
            ClumpShapes.lump(mass, "core" + i, uv[0], uv[1], dir[0] * RX * r, dir[1] * RY * r, dir[2] * RZ * r,
                    w, h, d, rng.nextFloat() * 1.5f, rng.nextFloat() * 1.5f, rng.nextFloat() * 1.5f);
        }
    }

    /**
     * One limb wrapped over the surface of the mass: it comes out of the core, runs along the
     * surface, bends at an elbow (or knee), runs on, and ends in a hand gripping the tangle, a
     * foot, or a stump. Points are kept on the ellipsoid shell (at a random "layer" so some limbs
     * lie over others), so the limbs hug the shape instead of sticking out.
     */
    private static void limb(PartDefinition mass, String name, RandomSource rng, boolean forceHand, float[] start) {
        boolean leg = !forceHand && rng.nextFloat() < 0.3f;
        int[] skin = rng.nextFloat() < 0.7f ? SKIN : BRUISE;
        float layer = 0.92f + rng.nextFloat() * 0.2f;
        float[] p0 = shell(start[0], start[1], start[2], layer * 0.8f);
        float[] tangent = tangentAt(p0, randomDirection(rng));
        float upper = leg ? 11.0f + rng.nextFloat() * 3.0f : 9.0f + rng.nextFloat() * 3.0f;
        float lower = leg ? 10.0f + rng.nextFloat() * 2.0f : 8.0f + rng.nextFloat() * 3.0f;
        float thickUpper = leg ? 3.8f : 2.8f;
        float thickLower = leg ? 3.0f : 2.3f;

        float[] p1 = shell(p0[0] + tangent[0] * upper, p0[1] + tangent[1] * upper, p0[2] + tangent[2] * upper, layer);
        // Bend at the elbow: turn the direction 30-80 degrees toward another random tangent.
        float[] other = tangentAt(p1, randomDirection(rng));
        float bend = 0.5f + rng.nextFloat() * 0.9f;
        float[] t2 = ClumpShapes.normalize(
                tangent[0] * Mth.cos(bend) + other[0] * Mth.sin(bend),
                tangent[1] * Mth.cos(bend) + other[1] * Mth.sin(bend),
                tangent[2] * Mth.cos(bend) + other[2] * Mth.sin(bend));
        float[] p2 = shell(p1[0] + t2[0] * lower, p1[1] + t2[1] * lower, p1[2] + t2[2] * lower, layer);

        PartDefinition part = ClumpShapes.joint(mass, name, 0, 0, 0);
        rodUv(part, "upper", rng, skin, p0, p1, thickUpper);
        rodUv(part, "lower", rng, skin, p1, p2, thickLower);
        float joint = thickUpper * 1.25f;
        int[] juv = uv(rng, JOINT, 4 * joint + 5, 2 * joint + 5);
        ClumpShapes.lump(part, "joint", juv[0], juv[1], p1[0], p1[1], p1[2], joint, joint, joint,
                rng.nextFloat(), rng.nextFloat(), rng.nextFloat());

        float[] dir = ClumpShapes.normalize(p2[0] - p1[0], p2[1] - p1[1], p2[2] - p1[2]);
        float[] normal = outward(p2);
        float end = rng.nextFloat();
        if (forceHand || (!leg && end < 0.8f)) {
            PartDefinition hand = ClumpShapes.joint(part, "hand", p2[0], p2[1], p2[2]);
            gripHand(hand, rng, dir, normal, 0.9f + rng.nextFloat() * 0.5f);
        } else if (leg && end < 0.7f) {
            foot(part, rng, skin, p2, dir, normal);
        } else {
            // A stump: the limb just ends in a rounded knob.
            int[] suv = uv(rng, JOINT, 4 * thickLower + 5, 2 * thickLower + 5);
            ClumpShapes.lump(part, "stump", suv[0], suv[1], p2[0], p2[1], p2[2], thickLower * 1.1f, thickLower * 1.1f, thickLower * 1.1f, 0.3f, 0.2f, 0.1f);
        }
    }

    /**
     * A hand at the origin of {@code hand}, pointing along {@code dir}, palm toward {@code -normal}
     * (the mass): a palm and four two-part fingers that curl down around whatever is underneath,
     * plus a thumb. {@code curl} in radians per finger joint.
     */
    private static void gripHand(PartDefinition hand, RandomSource rng, float[] dir, float[] normal, float curl) {
        float[] side = ClumpShapes.normalize(ClumpShapes.cross(dir, normal)[0], ClumpShapes.cross(dir, normal)[1], ClumpShapes.cross(dir, normal)[2]);
        float[] palmEnd = {dir[0] * PALM, dir[1] * PALM, dir[2] * PALM};
        rodFlat(hand, "palm", rng, HAND, new float[] {0, 0, 0}, palmEnd, 3.2f, 1.3f);
        for (int f = 0; f < 4; f++) {
            float off = (f - 1.5f) * 0.8f;
            float[] base = add(palmEnd, scale(side, off));
            float len1 = 2.3f + rng.nextFloat() * 0.6f - Math.abs(f - 1.5f) * 0.3f;
            float len2 = 1.9f + rng.nextFloat() * 0.4f;
            float c1 = curl * (0.8f + rng.nextFloat() * 0.4f);
            // Bending toward -normal: a mix of "straight on" and "down into the mass".
            float[] d1 = ClumpShapes.normalize(dir[0] * Mth.cos(c1) - normal[0] * Mth.sin(c1),
                    dir[1] * Mth.cos(c1) - normal[1] * Mth.sin(c1), dir[2] * Mth.cos(c1) - normal[2] * Mth.sin(c1));
            float[] k1 = add(base, scale(d1, len1));
            float c2 = c1 + curl * (0.8f + rng.nextFloat() * 0.4f);
            float[] d2 = ClumpShapes.normalize(dir[0] * Mth.cos(c2) - normal[0] * Mth.sin(c2),
                    dir[1] * Mth.cos(c2) - normal[1] * Mth.sin(c2), dir[2] * Mth.cos(c2) - normal[2] * Mth.sin(c2));
            float[] k2 = add(k1, scale(d2, len2));
            rodUv(hand, "f" + f + "a", rng, HAND, base, k1, 0.95f);
            rodUv(hand, "f" + f + "b", rng, NAIL, k1, k2, 0.85f);
        }
        float[] thumbBase = scale(side, -1.6f);
        float[] thumbTip = add(thumbBase, add(scale(dir, 2.4f), add(scale(side, -0.8f), scale(normal, -0.8f))));
        rodUv(hand, "thumb", rng, HAND, thumbBase, thumbTip, 1.0f);
    }

    /** A bare foot at the end of a leg: heel knob, a flat sole bent forward, and a row of toes. */
    private static void foot(PartDefinition part, RandomSource rng, int[] skin, float[] ankle, float[] dir, float[] normal) {
        int[] huv = uv(rng, JOINT, 4 * 3.0f + 5, 2 * 3.0f + 5);
        ClumpShapes.lump(part, "heel", huv[0], huv[1], ankle[0], ankle[1], ankle[2], 3.0f, 3.2f, 3.0f, 0.2f, 0.4f, 0.1f);
        // The sole turns 90 degrees off the shin, like an ankle.
        float[] forward = ClumpShapes.normalize(normal[0] * 0.7f + dir[0] * 0.3f, normal[1] * 0.7f + dir[1] * 0.3f, normal[2] * 0.7f + dir[2] * 0.3f);
        float[] toesAt = add(ankle, scale(forward, 5.0f));
        rodFlat(part, "sole", rng, skin, ankle, toesAt, 3.2f, 1.6f);
        float[] side = ClumpShapes.normalize(ClumpShapes.cross(forward, dir)[0], ClumpShapes.cross(forward, dir)[1], ClumpShapes.cross(forward, dir)[2]);
        for (int t = 0; t < 4; t++) {
            float[] base = add(toesAt, scale(side, (t - 1.5f) * 0.75f));
            rodUv(part, "toe" + t, rng, NAIL, base, add(base, scale(forward, 1.2f + (t == 0 ? 0.4f : 0.0f))), 0.75f);
        }
    }

    /**
     * A bait arm, hanging from the front-bottom of the mass. Built straight down along +Y so
     * "hanging" is the rest pose: shoulder joint -&gt; upper arm -&gt; elbow joint -&gt; forearm -&gt;
     * wrist joint -&gt; palm and four two-joint fingers (+ thumb). All joints are animated.
     */
    private static void baitArm(PartDefinition mass, int k, RandomSource rng) {
        float sx = BAIT_X[k];
        float sy = RY * 0.62f + rng.nextFloat() * 1.5f;
        float sz = -RZ * 0.5f + rng.nextFloat() * 1.5f;
        PartDefinition shoulder = mass.addOrReplaceChild("bait" + k, CubeListBuilder.create(), PartPose.offset(sx, sy, sz));
        int[] skin = k % 2 == 0 ? SKIN : BRUISE;
        int[] duv = uv(rng, JOINT, 4 * 4.2f + 5, 2 * 4.2f + 5);
        ClumpShapes.lump(shoulder, "deltoid", duv[0], duv[1], 0.0f, 1.0f, 0.0f, 4.2f, 4.0f, 4.0f, 0.2f, 0.3f, 0.1f);
        rodUv(shoulder, "upper", rng, skin, new float[] {0, 0, 0}, new float[] {0.3f, BAIT_UPPER, 0.2f}, 3.0f);
        // A few lumps of muscle and a vein, so the arm isn't a plain stick.
        int[] muv = uv(rng, skin, 4 * 3.6f + 5, 3.6f + 7.0f + 5);
        ClumpShapes.lump(shoulder, "biceps", muv[0], muv[1], 0.0f, BAIT_UPPER * 0.4f, -0.6f, 3.6f, 7.0f, 3.4f, 0.05f, 0.0f, 0.03f);
        rodUv(shoulder, "vein", rng, BRUISE, new float[] {-1.2f, 2.0f, -1.0f}, new float[] {-1.0f, BAIT_UPPER - 2.0f, -1.3f}, 0.5f);

        PartDefinition elbow = shoulder.addOrReplaceChild("elbow", CubeListBuilder.create(), PartPose.offset(0.3f, BAIT_UPPER, 0.2f));
        int[] euv = uv(rng, JOINT, 4 * 3.3f + 5, 2 * 3.3f + 5);
        ClumpShapes.lump(elbow, "knob", euv[0], euv[1], 0.0f, 0.0f, 0.4f, 3.3f, 3.3f, 3.3f, 0.3f, 0.0f, 0.2f);
        rodUv(elbow, "fore", rng, skin, new float[] {0, 0, 0}, new float[] {-0.2f, BAIT_FORE, 0.0f}, 2.5f);
        int[] fuv = uv(rng, skin, 4 * 2.9f + 5, 2.9f + 6.0f + 5);
        ClumpShapes.lump(elbow, "forearm_muscle", fuv[0], fuv[1], 0.0f, BAIT_FORE * 0.3f, 0.2f, 2.9f, 6.0f, 2.8f, 0.0f, 0.0f, 0.0f);

        PartDefinition wrist = elbow.addOrReplaceChild("wrist", CubeListBuilder.create(), PartPose.offset(-0.2f, BAIT_FORE, 0.0f));
        rodFlat(wrist, "palm", rng, HAND, new float[] {0, 0, 0}, new float[] {0, PALM, 0}, 3.4f, 1.4f);
        for (int f = 0; f < 4; f++) {
            float fx = (f - 1.5f) * 0.85f;
            PartDefinition knuckle = wrist.addOrReplaceChild("finger" + f, CubeListBuilder.create(), PartPose.offset(fx, PALM, 0.0f));
            float len1 = 2.6f + rng.nextFloat() * 0.5f - Math.abs(f - 1.5f) * 0.35f;
            float len2 = 2.2f + rng.nextFloat() * 0.4f;
            rodUv(knuckle, "a", rng, HAND, new float[] {0, 0, 0}, new float[] {0, len1, 0}, 0.95f);
            PartDefinition tip = knuckle.addOrReplaceChild("tip", CubeListBuilder.create(), PartPose.offset(0.0f, len1, 0.0f));
            rodUv(tip, "b", rng, NAIL, new float[] {0, 0, 0}, new float[] {0, len2, 0}, 0.85f);
        }
        rodUv(wrist, "thumb", rng, HAND, new float[] {-1.7f, 0.6f, 0.0f}, new float[] {-2.4f, 3.2f, -0.9f}, 1.0f);
    }

    /**
     * The long arm: 12 bones in a chain, starting at the root (moved to the mouth of the hole each
     * frame). Each "seg" is a joint; its "bone" child holds the visible rod so the rod can be
     * stretched (yScale) as the arm extends without stretching everything further down. A knuckle
     * lump at each joint, and three long jointed fingers at the end.
     */
    private static void longArm(PartDefinition root, RandomSource rng) {
        PartDefinition parent = root;
        for (int i = 0; i < SEGMENTS; i++) {
            float y = i == 0 ? 0.0f : SEGMENT;
            PartDefinition seg = parent.addOrReplaceChild("seg" + i, CubeListBuilder.create(), PartPose.offset(0, y, 0));
            float thick = Mth.lerp(i / (float) (SEGMENTS - 1), 3.0f, 1.7f);
            PartDefinition bone = seg.addOrReplaceChild("bone", CubeListBuilder.create(), PartPose.ZERO);
            int[] skin = i % 3 == 1 ? BRUISE : SKIN;
            rodUv(bone, "rod", rng, skin, new float[] {0, 0, 0}, new float[] {0, SEGMENT, 0}, thick);
            // A sinew strand twisting along each bone.
            rodUv(bone, "sinew", rng, BRUISE, new float[] {thick * 0.45f, 1.0f, 0.0f}, new float[] {-thick * 0.3f, SEGMENT - 1.0f, thick * 0.4f}, 0.6f);
            if (i > 0) {
                float knob = thick * 1.35f;
                int[] juv = uv(rng, JOINT, 4 * knob + 5, 2 * knob + 5);
                ClumpShapes.lump(seg, "knuckle", juv[0], juv[1], 0.0f, 0.0f, 0.0f, knob, knob * 0.9f, knob, rng.nextFloat(), rng.nextFloat(), 0.0f);
            }
            parent = seg;
        }
        PartDefinition hand = parent.addOrReplaceChild("claws", CubeListBuilder.create(), PartPose.offset(0, SEGMENT, 0));
        int[] wuv = uv(rng, JOINT, 4 * 2.6f + 5, 2 * 2.6f + 5);
        ClumpShapes.lump(hand, "wrist", wuv[0], wuv[1], 0.0f, 0.5f, 0.0f, 2.6f, 2.4f, 2.6f, 0.0f, 0.5f, 0.0f);
        for (int c = 0; c < CLAWS; c++) {
            // Each finger's base is turned around the arm's own axis (120 degrees apart).
            PartDefinition p = hand.addOrReplaceChild("claw" + c, CubeListBuilder.create(), PartPose.rotation(0.0f, c * Mth.TWO_PI / CLAWS, 0.0f));
            float y = 1.0f;
            for (int j = 0; j < CLAW_PHALANX.length; j++) {
                p = p.addOrReplaceChild("p" + j, CubeListBuilder.create(), PartPose.offset(0.0f, y, j == 0 ? -0.9f : 0.0f));
                rodUv(p, "rod", rng, j == CLAW_PHALANX.length - 1 ? NAIL : HAND, new float[] {0, 0, 0}, new float[] {0, CLAW_PHALANX[j], 0}, 1.1f - j * 0.15f);
                y = CLAW_PHALANX[j];
            }
        }
    }

    // ---- small vector and texture helpers -------------------------------------------------

    private static void rodUv(PartDefinition parent, String name, RandomSource rng, int[] swatch, float[] a, float[] b, float thick) {
        rodFlat(parent, name, rng, swatch, a, b, thick, thick);
    }

    private static void rodFlat(PartDefinition parent, String name, RandomSource rng, int[] swatch, float[] a, float[] b, float width, float depth) {
        float length = Mth.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]) + (b[2] - a[2]) * (b[2] - a[2]));
        int[] uv = uv(rng, swatch, 2 * (width + depth) + 1, depth + length + 1);
        ClumpShapes.rod(parent, name, uv[0], uv[1], a[0], a[1], a[2], b[0], b[1], b[2], width, depth);
    }

    /** A random texture offset inside {@code swatch} with room for a box needing (needW x needH). */
    private static int[] uv(RandomSource rng, int[] swatch, float needW, float needH) {
        int spareU = Math.max(0, swatch[2] - Mth.ceil(needW));
        int spareV = Math.max(0, swatch[3] - Mth.ceil(needH));
        return new int[] {swatch[0] + rng.nextInt(spareU + 1), swatch[1] + rng.nextInt(spareV + 1)};
    }

    /**
     * The i-th of {@code count} directions spread evenly over a sphere (a golden-angle spiral),
     * nudged a little at random. Random directions bunch up and leave bald patches; this way the
     * limbs cover the whole mass.
     */
    private static float[] spiralDirection(int i, int count, RandomSource rng) {
        float y = 1.0f - 2.0f * (i + 0.5f) / count + (rng.nextFloat() - 0.5f) * 0.08f;
        y = Mth.clamp(y, -0.99f, 0.99f);
        float a = i * 2.3999632f + (rng.nextFloat() - 0.5f) * 0.4f;
        float r = Mth.sqrt(1.0f - y * y);
        return new float[] {Mth.cos(a) * r, y, Mth.sin(a) * r};
    }

    private static float[] randomDirection(RandomSource rng) {
        float y = rng.nextFloat() * 2.0f - 1.0f;
        float a = rng.nextFloat() * Mth.TWO_PI;
        float r = Mth.sqrt(1.0f - y * y);
        return new float[] {Mth.cos(a) * r, y, Mth.sin(a) * r};
    }

    /** The point on the (scaled) ellipsoid shell in the direction of (x, y, z) from the centre. */
    private static float[] shell(float x, float y, float z, float scale) {
        float k = Mth.sqrt((x / RX) * (x / RX) + (y / RY) * (y / RY) + (z / RZ) * (z / RZ));
        if (k < 1.0e-5f) {
            return new float[] {0.0f, RY * scale, 0.0f};
        }
        return new float[] {x / k * scale, y / k * scale, z / k * scale};
    }

    /** The shell's outward normal at a point on it. */
    private static float[] outward(float[] p) {
        return ClumpShapes.normalize(p[0] / (RX * RX), p[1] / (RY * RY), p[2] / (RZ * RZ));
    }

    /** {@code v} with its outward part removed: a direction that runs along the surface at p. */
    private static float[] tangentAt(float[] p, float[] v) {
        float[] n = outward(p);
        float dot = v[0] * n[0] + v[1] * n[1] + v[2] * n[2];
        return ClumpShapes.normalize(v[0] - n[0] * dot, v[1] - n[1] * dot, v[2] - n[2] * dot);
    }

    private static float[] add(float[] a, float[] b) {
        return new float[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static float[] scale(float[] a, float s) {
        return new float[] {a[0] * s, a[1] * s, a[2] * s};
    }

    // ================================================================== animation

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Clump entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float partial = ageInTicks - entity.tickCount;
        Clump.State state = entity.clientState();
        boolean ceiling = entity.isCeiling();
        float still = entity.stillness(partial);
        float sway = 1.0f - still;
        int seed = entity.getId() * 37;

        animateMass(entity, state, still, ageInTicks, seed);
        for (int k = 0; k < BAIT; k++) {
            animateBait(entity, k, ceiling, state, sway, ageInTicks, seed + k * 11);
        }
        animateLure(entity, ceiling, ageInTicks);
        animateLongArm(entity, ceiling, state, partial, ageInTicks);
    }

    /** Slow breathing; a fast shiver while it tenses; heaving while it drags something in. */
    private void animateMass(Clump entity, Clump.State state, float still, float age, int seed) {
        float breath = Mth.sin(age * 0.06f);
        mass.xScale = 1.0f + 0.015f * breath;
        mass.zScale = 1.0f + 0.015f * breath;
        mass.yScale = 1.0f + 0.02f * Mth.sin(age * 0.06f + 1.0f);
        if (state == Clump.State.TENSE) {
            mass.x += ClumpShapes.twitch(seed, age, 1, 0.35f);
            mass.z += ClumpShapes.twitch(seed + 1, age, 1, 0.35f);
        } else if (state == Clump.State.DRAG) {
            float heave = Mth.sin(age * 0.45f);
            mass.xScale += 0.05f * heave;
            mass.zScale += 0.05f * heave;
            mass.yScale -= 0.04f * heave;
        }
        // Gripping hands on the mass flex now and then, out of step with each other.
        for (int i = 0; i < twitchHands.size(); i++) {
            ModelPart hand = twitchHands.get(i);
            float twitch = ClumpShapes.twitch(seed + 50 + i, age, 9 + i * 3, 0.35f);
            float gate = ClumpShapes.twitch(seed + 80 + i, age, 9 + i * 3, 1.0f) > 0.4f ? 1.0f : 0.0f;
            hand.xRot = twitch * gate * (1.0f - 0.7f * still);
            hand.zRot = ClumpShapes.drift(seed + 90 + i, age, 40, 0.15f);
        }
    }

    /**
     * The bait. Hanging (drain): the arms dangle and swing like pendulums, fingers slowly curling
     * and opening. Lying (alcove): the arms reach out across the floor, fingers drumming. When it
     * listens they slow down; when it tenses they go rigid with the fingers splayed (the tell).
     */
    private void animateBait(Clump entity, int k, boolean ceiling, Clump.State state, float sway, float age, int seed) {
        ModelPart shoulder = baitShoulder[k];
        ModelPart elbow = baitElbow[k];
        ModelPart wrist = baitWrist[k];
        float phase = k * 1.7f;
        if (ceiling) {
            shoulder.xRot = 0.08f + sway * (0.12f * Mth.sin(age * 0.045f + phase) + ClumpShapes.drift(seed, age, 90, 0.12f));
            shoulder.zRot = (BAIT_X[k] < 0 ? 0.05f : -0.05f) + sway * 0.08f * Mth.sin(age * 0.033f + phase * 1.3f);
            shoulder.yRot = sway * ClumpShapes.drift(seed + 1, age, 120, 0.4f);
            elbow.xRot = -0.12f - sway * 0.1f * (1.0f + Mth.sin(age * 0.05f + phase));
            wrist.xRot = -0.1f + sway * 0.12f * Mth.sin(age * 0.07f + phase);
        } else {
            // Reaching out of the alcove along the floor, slightly fanned.
            shoulder.xRot = -1.42f + sway * 0.04f * Mth.sin(age * 0.04f + phase);
            shoulder.yRot = (k - 1.5f) * 0.22f + sway * ClumpShapes.drift(seed + 1, age, 100, 0.12f);
            elbow.xRot = 0.12f + sway * 0.05f * Mth.sin(age * 0.05f + phase);
            wrist.xRot = 0.55f;
        }
        for (int f = 0; f < 4; f++) {
            float curl;
            if (state == Clump.State.TENSE || state == Clump.State.STRIKE) {
                curl = -0.25f; // splayed, rigid
            } else if (state == Clump.State.LISTEN) {
                // Beckoning: a slow wave running across the fingers.
                curl = 0.25f + 0.55f * Math.max(0.0f, Mth.sin(age * 0.22f + f * 0.7f + phase));
            } else if (ceiling) {
                curl = 0.35f + 0.3f * sway * Mth.sin(age * 0.04f + phase + f * 0.25f);
            } else {
                // Drumming on the tiles.
                curl = 0.2f + 0.45f * sway * Math.max(0.0f, Mth.sin(age * 0.16f + f * 1.1f + phase));
            }
            curl += ClumpShapes.twitch(seed + 20 + f, age, 7, 0.12f) * sway;
            baitKnuckle[k][f].xRot = curl;
            baitTip[k][f].xRot = curl * 1.2f;
        }
    }

    /**
     * Lures (started by an entity event from the server): CLAP brings the two inner arms up and
     * claps the hands together three times; POUND swings the outer arm back and slams it into the
     * wall (or, lying in an alcove, raises it and slaps the floor) twice.
     */
    private void animateLure(Clump entity, boolean ceiling, float age) {
        if (entity.lureKind() == 0) {
            return;
        }
        float p = (age - entity.lureStart()) / 26.0f;
        if (p < 0.0f || p > 1.0f) {
            return;
        }
        float envelope = Mth.sin(p * Mth.PI); // ease in and out of the pose
        if (entity.lureKind() == 1 && ceiling) {
            float open = Mth.sin(p * 3.0f * Mth.PI);
            open *= open;
            for (int k = 1; k <= 2; k++) {
                float out = BAIT_X[k] < 0 ? 1.0f : -1.0f;
                baitShoulder[k].xRot = -0.9f * envelope;
                baitShoulder[k].zRot = out * (0.55f * open - 0.08f) * envelope;
                baitElbow[k].xRot = -0.5f * envelope;
                baitWrist[k].zRot = out * -0.6f * envelope;
            }
        } else {
            int k = entity.lureKind() == 1 ? 1 : 3;
            float q = (p * 2.0f) % 1.0f;
            // Slow wind-up, then a fast slam.
            float lift = q < 0.7f ? Mth.sin(q / 0.7f * Mth.HALF_PI) : 1.0f - (q - 0.7f) / 0.3f;
            if (ceiling) {
                float out = BAIT_X[k] < 0 ? 1.0f : -1.0f;
                baitShoulder[k].zRot = out * 1.3f * lift * envelope;
                baitElbow[k].xRot = -0.6f * lift * envelope;
            } else {
                baitShoulder[k].xRot -= 0.9f * lift * envelope;
                baitElbow[k].xRot -= 0.5f * lift * envelope;
            }
        }
    }

    /**
     * Points the long arm. The root goes to the mouth of the hole; the chord to the target is
     * worked out in model space (undoing the renderer's turn to the clump's facing); then every
     * joint bends by the same angle so the arc's ends are exactly the mouth and the target.
     */
    private void animateLongArm(Clump entity, boolean ceiling, Clump.State state, float partial, float age) {
        float ext = entity.armExtension(partial);
        boolean visible = ext > 0.01f;
        segment[0].visible = visible;
        if (!visible) {
            return;
        }
        Vec3 mouth = Clump.mouthOffset(ceiling, entity.facingYaw());
        // The mouth in model space (the body is drawn facing facingYaw, so it is fixed there).
        float mx = 0.0f;
        float my = 24.0f - (float) mouth.y * 16.0f;
        float mz = ceiling ? 0.0f : -0.75f * 16.0f;
        segment[0].x = mx;
        segment[0].y = my;
        segment[0].z = mz;

        Vec3 target = entity.armTargetPoint(partial);
        float tx;
        float ty;
        float tz;
        if (target == null) {
            tx = mx;
            ty = ceiling ? my + 48.0f : my;
            tz = ceiling ? mz : mz - 48.0f;
        } else {
            Vec3 d = target.subtract(entity.getPosition(partial));
            float yaw = entity.facingYaw() * Mth.DEG_TO_RAD;
            float fwd = (float) (-d.x * Mth.sin(yaw) + d.z * Mth.cos(yaw));
            float left = (float) (d.x * Mth.cos(yaw) + d.z * Mth.sin(yaw));
            tx = left * 16.0f;
            ty = 24.0f - (float) d.y * 16.0f;
            tz = -fwd * 16.0f;
        }
        float dx = tx - mx;
        float dy = ty - my;
        float dz = tz - mz;
        float chord = Mth.sqrt(dx * dx + dy * dy + dz * dz);

        // While reeling someone in the arm shortens with them (slack stays small).
        float length = Clump.ARM_LENGTH_PX * ext;
        if (state == Clump.State.DRAG) {
            length = Math.min(length, chord * 1.15f + 12.0f);
        }
        float piece = length / SEGMENTS;
        float bend = arcBend(piece, chord);

        float[] aim = ClumpShapes.aim(dx, dy, dz);
        segment[0].xRot = aim[0] - (SEGMENTS - 1) * bend / 2.0f;
        segment[0].zRot = aim[1];
        float writhe = state == Clump.State.DRAG ? 0.07f : 0.035f;
        for (int i = 0; i < SEGMENTS; i++) {
            if (i > 0) {
                segment[i].y = piece;
                segment[i].xRot = bend + writhe * Mth.sin(age * 0.5f - i * 0.8f);
                // A little twist too, alternating so it never adds up and throws the tip off target.
                segment[i].yRot = (i % 2 == 0 ? 1.0f : -1.0f) * writhe * 0.4f * Mth.cos(age * 0.4f - i * 0.6f);
            }
            bone[i].yScale = piece / SEGMENT;
        }

        // The three fingers: spread wide as it strikes, coiled tight around its prey while it drags.
        float grip = state == Clump.State.DRAG ? Math.min(1.0f, (entity.clientStateTicks() + partial) / 4.0f) : 0.0f;
        for (int c = 0; c < CLAWS; c++) {
            for (int j = 0; j < CLAW_PHALANX.length; j++) {
                float open = j == 0 ? -0.75f : -0.1f;
                float closed = j == 0 ? -0.2f : 1.05f;
                claw[c][j].xRot = Mth.lerp(grip, open, closed) + 0.08f * Mth.sin(age * 0.6f + c + j);
            }
        }
    }

    /**
     * The equal bend per joint (radians) that makes a chain of {@link #SEGMENTS} pieces of length
     * {@code piece} span exactly {@code chord}: the chord of such an arc is
     * piece * sin(n*b/2) / sin(b/2), which shrinks as b grows, so a short binary search finds b.
     * Zero if the arm is too short to reach (then it just points straight at the target).
     */
    private static float arcBend(float piece, float chord) {
        if (chord >= piece * SEGMENTS * 0.999f) {
            return 0.0f;
        }
        float lo = 0.0f;
        float hi = Mth.TWO_PI / SEGMENTS * 0.98f;
        for (int i = 0; i < 18; i++) {
            float mid = (lo + hi) / 2.0f;
            float span = piece * Mth.sin(SEGMENTS * mid / 2.0f) / Mth.sin(mid / 2.0f);
            if (span > chord) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2.0f;
    }
}
