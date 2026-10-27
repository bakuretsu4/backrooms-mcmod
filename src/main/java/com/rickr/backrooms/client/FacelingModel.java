package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.Faceling;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A Faceling, built in code: a smooth, slim, adult mannequin in plain clothes, with nothing
 * where the face should be.
 *
 * <p>Every body part is a smooth "skin" box with a slightly bigger clothing box around it
 * (shirt, sleeves, trousers, shoes, collar). Which clothes show is decided by the texture: a
 * transparent sleeve or trouser leg simply isn't there, so one model wears short or long sleeves,
 * and the hair cap only shows on outfits that have hair. The head is softened with a few bevel
 * boxes (sides, crown, a slightly raised blank face and a chin) so it reads as smooth and rounded,
 * CGI-like, rather than as a block.
 *
 * <p>Joints: hips (body) → waist (torso) → chest → neck → head; shoulders → elbows → wrists;
 * hips → knees → ankles. Poses ({@link #setupAnim}): a slow, stiff walk; standing so still it
 * only breathes; a head that turns and tilts to follow you; a hand (sometimes both) coming up to
 * trace the blank face; and the hostile sprint, leaning forward, arms pumping.
 *
 * <p>Model space: units are pixels, +Y down, the front faces -Z, ground at y = 24. The textures
 * are 256 x 256 for this 128 x 128 UV layout, made by {@code tools/anomaly_assets/level39_mobs1.py},
 * which follows the UV map noted at each box.
 */
public class FacelingModel extends HierarchicalModel<Faceling> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Backrooms.id("faceling"), "main");

    private static final float HIP_Y = 11.5f;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart torso;
    private final ModelPart chest;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart rightForearm;
    private final ModelPart rightHand;
    private final ModelPart leftArm;
    private final ModelPart leftForearm;
    private final ModelPart leftHand;
    private final ModelPart rightLeg;
    private final ModelPart rightShin;
    private final ModelPart rightShoe;
    private final ModelPart leftLeg;
    private final ModelPart leftShin;
    private final ModelPart leftShoe;

    public FacelingModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.torso = body.getChild("torso");
        this.chest = torso.getChild("chest");
        this.neck = chest.getChild("neck");
        this.head = neck.getChild("head");
        this.rightArm = chest.getChild("right_arm");
        this.rightForearm = rightArm.getChild("forearm");
        this.rightHand = rightForearm.getChild("hand");
        this.leftArm = chest.getChild("left_arm");
        this.leftForearm = leftArm.getChild("forearm");
        this.leftHand = leftForearm.getChild("hand");
        this.rightLeg = root.getChild("right_leg");
        this.rightShin = rightLeg.getChild("shin");
        this.rightShoe = rightShin.getChild("shoe");
        this.leftLeg = root.getChild("left_leg");
        this.leftShin = leftLeg.getChild("shin");
        this.leftShoe = leftShin.getChild("shoe");
    }

    // ================================================================== building

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        // Pelvis (skin) UV (44,16); trousers' seat and belt UV (52,28).
                        .texOffs(44, 16).addBox(-3.5f, -2.5f, -1.8f, 7, 3, 3.6f)
                        .texOffs(52, 28).addBox(-3.7f, -2.7f, -1.95f, 7.4f, 3.2f, 3.9f),
                PartPose.offset(0.0f, HIP_Y, 0.0f));
        PartDefinition torso = body.addOrReplaceChild("torso", CubeListBuilder.create()
                        // Abdomen (skin) UV (24,16); shirt over it UV (28,28).
                        .texOffs(24, 16).addBox(-3.0f, -4.0f, -1.6f, 6, 4, 3.2f)
                        .texOffs(28, 28).addBox(-3.3f, -4.2f, -1.85f, 6.6f, 4.4f, 3.7f),
                PartPose.offset(0.0f, -2.5f, 0.0f));
        PartDefinition chest = torso.addOrReplaceChild("chest", CubeListBuilder.create()
                        // Chest (skin) UV (0,16); shirt UV (0,28); collar UV (76,28).
                        .texOffs(0, 16).addBox(-3.75f, -5.0f, -1.9f, 7.5f, 5, 3.8f)
                        .texOffs(0, 28).addBox(-4.0f, -5.2f, -2.1f, 8, 5.4f, 4.2f)
                        .texOffs(76, 28).addBox(-2.2f, -5.6f, -1.6f, 4.4f, 0.8f, 3.2f),
                PartPose.offset(0.0f, -4.0f, 0.0f));
        PartDefinition neck = chest.addOrReplaceChild("neck", CubeListBuilder.create()
                        // UV (108,0).
                        .texOffs(108, 0).addBox(-1.0f, -1.8f, -1.0f, 2, 1.8f, 2),
                PartPose.offset(0.0f, -5.0f, 0.0f));
        neck.addOrReplaceChild("head", CubeListBuilder.create()
                        // Skull UV (0,0). Its front face is the face area (blank, or faint features).
                        .texOffs(0, 0).addBox(-3.2f, -7.0f, -3.4f, 6.4f, 7, 6.8f)
                        // Bevels that round the head off: sides (28,0), crown (56,0), chin (56,8).
                        .texOffs(28, 0).addBox(-3.5f, -6.3f, -3.0f, 7, 5.6f, 6)
                        .texOffs(56, 0).addBox(-2.8f, -7.4f, -3.0f, 5.6f, 0.6f, 6)
                        .texOffs(56, 8).addBox(-2.6f, -1.6f, -3.6f, 5.2f, 1.6f, 3)
                        // The blank face, very slightly raised, like a mask without features. UV (108,6).
                        .texOffs(108, 6).addBox(-2.7f, -6.2f, -3.65f, 5.4f, 4.8f, 0.3f)
                        // Hair cap: transparent on bald outfits. UV (80,0).
                        .texOffs(80, 0).addBox(-3.4f, -7.3f, -3.3f, 6.8f, 3.5f, 6.9f),
                PartPose.offset(0.0f, -1.6f, 0.0f));

        for (int side = -1; side <= 1; side += 2) {
            arm(chest, side < 0 ? "right_arm" : "left_arm", side);
            leg(root, side < 0 ? "right_leg" : "left_leg", side);
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static void arm(PartDefinition chest, String name, int side) {
        PartDefinition arm = chest.addOrReplaceChild(name, CubeListBuilder.create()
                        // Upper arm (skin) UV (0,40); sleeve UV (10,40).
                        .texOffs(0, 40).addBox(-1.1f, -0.6f, -1.1f, 2.2f, 5.5f, 2.2f)
                        .texOffs(10, 40).addBox(-1.35f, -0.8f, -1.35f, 2.7f, 4, 2.7f),
                PartPose.offset(side * 4.6f, -4.4f, 0.0f));
        PartDefinition forearm = arm.addOrReplaceChild("forearm", CubeListBuilder.create()
                        // Forearm (skin) UV (22,40); long sleeve UV (32,40), transparent on short sleeves.
                        .texOffs(22, 40).addBox(-0.95f, 0.0f, -0.95f, 1.9f, 5, 1.9f)
                        .texOffs(32, 40).addBox(-1.2f, 0.0f, -1.2f, 2.4f, 4.2f, 2.4f),
                PartPose.offset(0.0f, 4.9f, 0.0f));
        forearm.addOrReplaceChild("hand", CubeListBuilder.create()
                        // Palm UV (44,40); fingers held together, mitten-smooth, UV (54,40); thumb UV (64,40).
                        .texOffs(44, 40).addBox(-0.9f, 0.0f, -1.1f, 1.8f, 2.4f, 2.2f)
                        .texOffs(54, 40).addBox(-0.85f, 2.4f, -1.05f, 1.7f, 1.4f, 2.1f)
                        .texOffs(64, 40).addBox(-0.35f - side * 0.55f, 0.4f, -1.6f, 0.7f, 1.6f, 0.7f),
                PartPose.offset(0.0f, 5.0f, 0.0f));
    }

    private static void leg(PartDefinition root, String name, int side) {
        PartDefinition thigh = root.addOrReplaceChild(name, CubeListBuilder.create()
                        // Thigh (skin) UV (0,52); trouser leg UV (14,52).
                        .texOffs(0, 52).addBox(-1.5f, 0.0f, -1.5f, 3, 6.5f, 3)
                        .texOffs(14, 52).addBox(-1.75f, -0.2f, -1.75f, 3.5f, 6.7f, 3.5f),
                PartPose.offset(side * 1.8f, HIP_Y, 0.0f));
        PartDefinition shin = thigh.addOrReplaceChild("shin", CubeListBuilder.create()
                        // Shin (skin) UV (30,52); trouser leg UV (42,52).
                        .texOffs(30, 52).addBox(-1.2f, 0.0f, -1.2f, 2.4f, 5.5f, 2.4f)
                        .texOffs(42, 52).addBox(-1.45f, 0.0f, -1.45f, 2.9f, 5, 2.9f),
                PartPose.offset(0.0f, 6.5f, 0.0f));
        shin.addOrReplaceChild("shoe", CubeListBuilder.create()
                        // UV (56,52).
                        .texOffs(56, 52).addBox(-1.4f, 0.0f, -3.0f, 2.8f, 1.3f, 4.4f),
                PartPose.offset(0.0f, 4.7f, 0.0f));
    }

    // ================================================================== animation

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Faceling entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float partial = ageInTicks - entity.tickCount;
        float run = entity.runWeight(partial);
        float touch = entity.touchWeight(partial);
        float stare = entity.stareWeight(partial);
        float amount = Math.min(1.0f, limbSwingAmount);
        int seed = entity.getId() * 131;
        float t = ageInTicks + (seed & 127);

        // --- Legs: slow stiff steps, or long running strides -----------------------------------
        float walkFreq = Mth.lerp(run, 0.55f, 0.75f);
        float legSwing = Mth.lerp(run, 0.65f, 1.3f) * amount;
        float c = Mth.cos(limbSwing * walkFreq);
        float s = Mth.sin(limbSwing * walkFreq);
        rightLeg.xRot = c * legSwing;
        leftLeg.xRot = -c * legSwing;
        // Knees bend only while the leg swings forward (when running, a lot).
        float kneeBend = Mth.lerp(run, 0.45f, 1.5f) * amount;
        rightShin.xRot = Math.max(0.0f, s) * kneeBend;
        leftShin.xRot = Math.max(0.0f, -s) * kneeBend;
        // Feet stay flat-ish.
        rightShoe.xRot = -rightLeg.xRot - rightShin.xRot * 0.6f;
        leftShoe.xRot = -leftLeg.xRot - leftShin.xRot * 0.6f;

        // --- Body: stiff when walking; leaning hard into the sprint ------------------------------
        torso.xRot = run * 0.38f;
        body.y += Math.abs(c) * -0.6f * run * amount;
        body.yRot = c * 0.06f * amount * (1.0f - run);
        // Breathing, so slight you'd only notice it when it stands still.
        chest.xRot += Mth.sin(t * 0.06f) * 0.015f;
        float breath = Mth.sin(t * 0.06f) * 0.1f;
        chest.y += breath * 0.3f;
        // Strangers walk with an odd, slightly lagging sway.
        torso.zRot = Mth.sin(limbSwing * walkFreq * 0.5f) * 0.03f * amount * (1.0f - run);

        // --- Arms: barely swinging; pumping when it runs ----------------------------------------
        float armSwing = Mth.lerp(run, 0.18f, 1.2f) * amount;
        rightArm.xRot = -c * armSwing;
        leftArm.xRot = c * armSwing;
        rightArm.zRot = 0.05f + run * 0.2f;
        leftArm.zRot = -0.05f - run * 0.2f;
        float elbow = Mth.lerp(run, 0.12f, 1.3f);
        rightForearm.xRot = -elbow;
        leftForearm.xRot = -elbow;
        // Hands curl slightly when running, hang open otherwise.
        rightHand.xRot = -0.1f * run;
        leftHand.xRot = -0.1f * run;

        // --- Head -----------------------------------------------------------------------------
        float yaw = netHeadYaw * Mth.DEG_TO_RAD;
        float pitch = headPitch * Mth.DEG_TO_RAD;
        head.yRot = yaw * Mth.lerp(stare, 0.75f, 1.0f);
        head.xRot = pitch * 0.8f - torso.xRot * 0.9f * run - run * 0.15f;
        // Staring: a slow quizzical tilt, as if trying to understand what you are.
        head.zRot = stare * 0.22f * (seed % 2 == 0 ? 1.0f : -1.0f) + stare * Mth.sin(t * 0.03f) * 0.05f;
        // Now and then a tiny sudden jerk of the head (not while running).
        head.yRot += jerk(seed, t, 47, 0.18f) * (1.0f - run);
        head.zRot += jerk(seed + 7, t, 61, 0.1f) * (1.0f - run);
        // Running: the head bobs and jitters with each stride.
        head.zRot += Mth.sin(limbSwing * walkFreq * 2.0f) * 0.08f * run * amount;

        // --- Touching its own face --------------------------------------------------------------
        if (touch > 0.001f) {
            float a = entity.actionTime(partial);
            boolean both = seed % 3 == 0;
            touchFace(rightArm, rightForearm, rightHand, -1, touch, a);
            if (both) {
                touchFace(leftArm, leftForearm, leftHand, 1, touch, a + 9.0f);
            }
            // The head bows into the hand and turns slightly, feeling for what isn't there.
            head.xRot += 0.22f * touch;
            head.zRot += 0.1f * touch * Mth.sin(a * 0.05f);
            head.yRot *= 1.0f - 0.7f * touch;
        }

        // --- The swing of an attack: a clumsy overhead claw --------------------------------------
        if (attackTime > 0.0f) {
            float swing = Mth.sin(attackTime * Mth.PI);
            rightArm.xRot -= swing * 1.9f;
            rightForearm.xRot = Mth.lerp(swing, rightForearm.xRot, -0.3f);
            leftArm.xRot -= swing * 1.2f;
        }
    }

    /**
     * Brings one hand up to the face and moves the fingertips slowly over it. The pose comes from
     * working the angles out for this skeleton: the upper arm raised forward and turned inward,
     * the elbow folded so the hand lands on the front of the head.
     */
    private static void touchFace(ModelPart arm, ModelPart forearm, ModelPart hand, int side, float weight, float time) {
        float trace = Mth.sin(time * 0.11f);
        float trace2 = Mth.sin(time * 0.07f + 1.3f);
        arm.xRot = Mth.lerp(weight, arm.xRot, -1.9f + trace2 * 0.08f);
        arm.yRot = Mth.lerp(weight, arm.yRot, side * 0.5f + trace * 0.12f);
        arm.zRot = Mth.lerp(weight, arm.zRot, side * -0.05f);
        forearm.xRot = Mth.lerp(weight, forearm.xRot, -1.4f + trace * 0.1f);
        // The fingers press and slide over the blank face.
        hand.xRot = Mth.lerp(weight, hand.xRot, -0.35f + trace2 * 0.15f);
        hand.yRot = Mth.lerp(weight, hand.yRot, -side * 0.4f);
    }

    /** A small sudden head movement: a new random value every {@code period} ticks, one bucket in four. */
    private static float jerk(int seed, float t, int period, float amplitude) {
        int bucket = Mth.floor(t / period);
        long h = (seed * 0x9E3779B1L) ^ (bucket * 0x85EBCA77L);
        h ^= h >>> 15;
        h *= 0x2C1B3C6DL;
        h ^= h >>> 12;
        float unit = (h & 0xFFFF) / 65535.0f;
        boolean active = ((h >>> 16) & 3) == 0;
        return active ? (unit * 2.0f - 1.0f) * amplitude : 0.0f;
    }
}
