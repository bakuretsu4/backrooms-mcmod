package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.L227Squirrel;
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

import static com.rickr.backrooms.client.L227ModelKit.rod;

/**
 * The Level 227 squirrel, built in code: a squirrel, almost. A little too long in the body, with
 * big glossy black eyes, long orange incisors that never quite close, hooked claws, and a huge
 * S-curved tail, all in a coat of fur that glows a sickly fluorescent green (the glow is a second,
 * full-bright texture: see {@link L227SquirrelRenderer}).
 *
 * <p>Poses follow the squirrel's synced state: the bounding gallop (both hind feet land together
 * ahead of the front ones), sitting bolt upright to stare, the leap with every claw out, and
 * clinging flat against a body, head buried, biting.
 *
 * <p>Kept light on purpose (about 60 boxes): up to forty of them can be on screen at once.
 *
 * <p>Model space: pixels, +Y down, front -Z, ground at y = 24. Texture 128 x 128 for a 64 x 64 UV
 * layout ({@code tools/anomaly_assets/level227_predators.py}).
 */
public class L227SquirrelModel extends HierarchicalModel<L227Squirrel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Backrooms.id("l227_squirrel"), "main");

    private static final float GROUND = 24.0f;
    private static final float BODY_Y = 18.6f;
    private static final float ARM_X = 1.4f, ARM_Y = 1.4f, ARM_Z = -3.0f;
    private static final float HIP_X = 2.0f, HIP_Y = 0.8f, HIP_Z = 3.6f;
    private static final float UPPER_ARM = 2.8f, FOREARM = 2.4f;
    private static final float THIGH = 3.0f, SHIN = 2.4f;
    private static final float HAND = 0.6f, FOOT = 0.8f;

    private static final int FLUFF_U = 20, FLUFF_V = 44;
    private static final int TOOTH_U = 38, TOOTH_V = 12;
    private static final int CLAW_U = 58, CLAW_V = 22;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart[] tail = new ModelPart[4];
    private final ModelPart[] upper = new ModelPart[4];
    private final ModelPart[] lower = new ModelPart[4];
    private final ModelPart[] end = new ModelPart[4];

    public L227SquirrelModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        tail[0] = body.getChild("tail");
        tail[1] = tail[0].getChild("tail1");
        tail[2] = tail[1].getChild("tail2");
        tail[3] = tail[2].getChild("tail3");
        String[] limbs = {"right_arm", "left_arm", "right_leg", "left_leg"};
        for (int i = 0; i < 4; i++) {
            upper[i] = body.getChild(limbs[i]);
            lower[i] = upper[i].getChild("lower");
            end[i] = lower[i].getChild(i < 2 ? "hand" : "foot");
        }
    }

    // ================================================================== building

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        RandomSource rng = RandomSource.create(0x227_5C1L);

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        // Torso, a little too long. UV (0,0).
                        .texOffs(0, 0).addBox(-2.0f, -2.0f, -4.5f, 4, 4, 7)
                        // Big haunches for the bounding run. UV (22,0).
                        .texOffs(22, 0).addBox(-2.5f, -2.3f, 1.5f, 5, 4.5f, 4.5f),
                PartPose.offset(0.0f, BODY_Y, 0.0f));
        // Fluff along the back and flanks, so the outline is fur, not a box.
        for (int k = 0; k < 8; k++) {
            float z = -4.0f + k * 1.3f;
            float x = (k % 2 == 0 ? -1 : 1) * (0.6f + rng.nextFloat() * 1.0f);
            rod(body, "fluff" + k, FLUFF_U + rng.nextInt(18), FLUFF_V + rng.nextInt(12), x, -1.6f, z,
                    x * 1.6f, -2.8f - rng.nextFloat() * 0.6f, z + 1.4f, 1.1f + rng.nextFloat() * 0.3f);
        }

        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                        // Skull. UV (0,12).
                        .texOffs(0, 12).addBox(-2.0f, -2.4f, -3.6f, 4, 3.8f, 3.8f)
                        // Blunt snout. UV (16,12).
                        .texOffs(16, 12).addBox(-1.2f, -0.9f, -5.2f, 2.4f, 2.2f, 1.8f)
                        // Big bulging black eyes. UV (26,12).
                        .texOffs(26, 12).addBox(-2.3f, -1.9f, -2.9f, 1, 1.4f, 1.4f)
                        .texOffs(26, 12).addBox(1.3f, -1.9f, -2.9f, 1, 1.4f, 1.4f)
                        // Lower jaw. UV (46,12).
                        .texOffs(46, 12).addBox(-0.9f, 1.2f, -4.9f, 1.8f, 0.6f, 1.6f),
                PartPose.offset(0.0f, -0.8f, -4.4f));
        // Incisors: two long orange-yellow chisels top, two below, never quite closed.
        rod(head, "incisor_ul", TOOTH_U, TOOTH_V, -0.35f, 0.9f, -5.0f, -0.35f, 2.6f, -5.4f, 0.55f);
        rod(head, "incisor_ur", TOOTH_U + 3, TOOTH_V, 0.35f, 0.9f, -5.0f, 0.35f, 2.6f, -5.4f, 0.55f);
        rod(head, "incisor_ll", TOOTH_U, TOOTH_V + 2, -0.3f, 1.5f, -4.8f, -0.3f, 0.6f, -5.3f, 0.5f);
        rod(head, "incisor_lr", TOOTH_U + 3, TOOTH_V + 2, 0.3f, 1.5f, -4.8f, 0.3f, 0.6f, -5.3f, 0.5f);
        // Ears with long tufts. UV (32,12).
        for (int side = -1; side <= 1; side += 2) {
            PartDefinition ear = head.addOrReplaceChild(side < 0 ? "ear_right" : "ear_left",
                    CubeListBuilder.create().texOffs(32, 12).addBox(-0.7f, -2.0f, -0.3f, 1.4f, 2, 0.6f),
                    PartPose.offsetAndRotation(side * 1.3f, -2.2f, -1.0f, -0.2f, 0.0f, side * 0.25f));
            rod(ear, "tuft", FLUFF_U + rng.nextInt(18), FLUFF_V + rng.nextInt(12), 0.0f, -1.8f, 0.0f, side * 0.4f, -3.4f, 0.4f, 0.7f);
            // Whiskers.
            rod(head, "whisker" + side, TOOTH_U + 6, TOOTH_V, side * 1.1f, 0.0f, -4.6f, side * 3.2f, 0.4f, -4.0f, 0.18f);
        }

        for (int side = -1; side <= 1; side += 2) {
            arm(body, side < 0 ? "right_arm" : "left_arm", side);
            leg(body, side < 0 ? "right_leg" : "left_leg", side, rng);
        }
        tail(body, rng);
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void arm(PartDefinition body, String name, int side) {
        int r = side < 0 ? 0 : 1;
        PartDefinition arm = body.addOrReplaceChild(name, CubeListBuilder.create()
                        // UV (0,22) / (6,22).
                        .texOffs(r * 6, 22).addBox(-0.6f, -0.3f, -0.6f, 1.2f, UPPER_ARM + 0.3f, 1.2f),
                PartPose.offset(side * ARM_X, ARM_Y, ARM_Z));
        PartDefinition fore = arm.addOrReplaceChild("lower", CubeListBuilder.create()
                        // UV (12,22) / (17,22).
                        .texOffs(12 + r * 5, 22).addBox(-0.5f, 0.0f, -0.5f, 1, FOREARM, 1),
                PartPose.offset(0.0f, UPPER_ARM, 0.0f));
        PartDefinition hand = fore.addOrReplaceChild("hand", CubeListBuilder.create(), PartPose.offset(0.0f, FOREARM, 0.0f));
        for (int c = 0; c < 3; c++) {
            float x = -0.4f + c * 0.4f;
            rod(hand, "claw" + c, CLAW_U + c * 2, CLAW_V, x, 0.0f, 0.0f, x * 1.2f, HAND, -1.1f, 0.3f);
        }
    }

    private static void leg(PartDefinition body, String name, int side, RandomSource rng) {
        int r = side < 0 ? 0 : 1;
        PartDefinition thigh = body.addOrReplaceChild(name, CubeListBuilder.create()
                        // UV (22,22) / (31,22).
                        .texOffs(22 + r * 9, 22).addBox(-0.9f, -0.6f, -1.3f, 1.8f, THIGH + 0.6f, 2.6f),
                PartPose.offset(side * HIP_X, HIP_Y, HIP_Z));
        PartDefinition shin = thigh.addOrReplaceChild("lower", CubeListBuilder.create()
                        // UV (48,30) / (52,30).
                        .texOffs(48 + r * 4, 30).addBox(-0.45f, 0.0f, -0.45f, 0.9f, SHIN, 0.9f),
                PartPose.offset(0.0f, THIGH, 0.0f));
        PartDefinition foot = shin.addOrReplaceChild("foot", CubeListBuilder.create()
                        // Long hind foot. UV (40,22) / (49,22).
                        .texOffs(40 + r * 9, 22).addBox(-0.6f, 0.0f, -2.8f, 1.2f, FOOT, 3.2f),
                PartPose.offset(0.0f, SHIN, 0.0f));
        for (int c = 0; c < 3; c++) {
            float x = -0.4f + c * 0.4f;
            rod(foot, "claw" + c, CLAW_U + c * 2, CLAW_V, x, 0.4f, -2.7f, x * 1.2f, FOOT, -3.8f, 0.3f);
        }
        rod(thigh, "fluff", FLUFF_U + rng.nextInt(18), FLUFF_V + rng.nextInt(12), side * 0.6f, 0.6f, 1.0f, side * 1.2f, 2.4f, 2.2f, 1.0f);
    }

    /** The tail: four ever bigger pieces of fluff curling up over the back and out again. */
    private static void tail(PartDefinition body, RandomSource rng) {
        PartDefinition t0 = body.addOrReplaceChild("tail", CubeListBuilder.create()
                        // UV (0,30).
                        .texOffs(0, 30).addBox(-1.3f, -1.3f, 0.0f, 2.6f, 2.6f, 3.5f),
                PartPose.offsetAndRotation(0.0f, -1.0f, 5.6f, 1.0f, 0.0f, 0.0f));
        PartDefinition t1 = t0.addOrReplaceChild("tail1", CubeListBuilder.create()
                        // UV (13,30).
                        .texOffs(13, 30).addBox(-2.0f, -2.0f, 0.0f, 4, 4, 4),
                PartPose.offsetAndRotation(0.0f, 0.0f, 3.2f, 0.45f, 0.0f, 0.0f));
        PartDefinition t2 = t1.addOrReplaceChild("tail2", CubeListBuilder.create()
                        // UV (30,30).
                        .texOffs(30, 30).addBox(-2.3f, -2.3f, 0.0f, 4.6f, 4.6f, 4.2f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 3.8f, 0.5f, 0.0f, 0.0f));
        PartDefinition t3 = t2.addOrReplaceChild("tail3", CubeListBuilder.create()
                        // UV (0,40).
                        .texOffs(0, 40).addBox(-2.0f, -2.0f, 0.0f, 4, 4, 3.6f),
                PartPose.offsetAndRotation(0.0f, 0.0f, 4.0f, -0.9f, 0.0f, 0.0f));
        PartDefinition[] parts = {t0, t1, t2, t3};
        float[] radius = {1.2f, 1.9f, 2.2f, 1.9f};
        for (int p = 0; p < 4; p++) {
            int n = p == 0 ? 2 : 4;
            for (int k = 0; k < n; k++) {
                float a = k / (float) n * Mth.TWO_PI + rng.nextFloat() * 0.8f;
                float x = Mth.cos(a) * radius[p];
                float y = Mth.sin(a) * radius[p];
                float z = 0.6f + rng.nextFloat() * 2.4f;
                rod(parts[p], "fluff" + k, FLUFF_U + rng.nextInt(18), FLUFF_V + rng.nextInt(12), x * 0.6f, y * 0.6f, z,
                        x * 1.45f, y * 1.45f, z + 1.6f, 1.2f + rng.nextFloat() * 0.3f);
            }
        }
    }

    // ================================================================== animation

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(L227Squirrel entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        pose(entity.state(), limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, entity.getId());
    }

    /** Poses the squirrel from plain numbers (the contact-sheet tool calls this without an entity). */
    public void pose(byte state, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, int id) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float t = ageInTicks + (id * 13 & 63);
        float amount = Mth.clamp(limbSwingAmount * 1.4f, 0.0f, 1.0f);
        float yaw = netHeadYaw * Mth.DEG_TO_RAD;
        float pitch = headPitch * Mth.DEG_TO_RAD;
        head.yRot += yaw * 0.7f;
        head.xRot += pitch * 0.6f;

        switch (state) {
            case L227Squirrel.SIT -> poseSit(t);
            case L227Squirrel.LEAP -> poseLeap(t);
            case L227Squirrel.CLING -> poseCling(t);
            default -> poseRun(limbSwing, amount, t);
        }
    }

    /** The bound: hind feet land together ahead of the front ones, the spine flexing like a spring. */
    private void poseRun(float limbSwing, float amount, float t) {
        float cycles = limbSwing * 0.22f;
        float phase = cycles - Mth.floor(cycles);
        float flex = Mth.sin(Mth.TWO_PI * phase);
        body.y += -Math.abs(Mth.sin(Mth.TWO_PI * phase + 0.6f)) * 1.4f * amount;
        body.xRot += flex * 0.22f * amount;
        // Nervous twitching when still.
        head.xRot += (1.0f - amount) * Mth.sin(t * 0.7f) * 0.05f;
        head.yRot += (1.0f - amount) * Mth.sin(t * 0.37f) * 0.2f;
        float[] offset = {0.0f, 0.06f, 0.5f, 0.54f};
        float stride = 4.0f * amount;
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            float p = phase + offset[i];
            p -= Mth.floor(p);
            float dz;
            float lift;
            if (p < 0.45f) {
                dz = -stride * 0.5f + stride * (p / 0.45f);
                lift = 0.0f;
            } else {
                float s = (p - 0.45f) / 0.55f;
                dz = stride * 0.5f - stride * L227ModelKit.smooth(s);
                lift = Mth.sin(Mth.PI * s) * 1.6f * amount;
            }
            if (front) {
                L227ModelKit.placeLeg(body, upper[i], lower[i], ARM_Y, ARM_Z, GROUND - HAND - lift, -0.4f + dz,
                        UPPER_ARM, FOREARM, true);
            } else {
                L227ModelKit.placeLeg(body, upper[i], lower[i], HIP_Y, HIP_Z, GROUND - FOOT - lift, -1.4f + dz,
                        THIGH, SHIN, false);
            }
            float net = body.xRot + upper[i].xRot + lower[i].xRot;
            end[i].xRot = -net;
        }
        // The tail flows behind in a wave.
        tail[0].xRot += -0.25f * amount + Mth.sin(Mth.TWO_PI * phase) * 0.15f * amount;
        tail[1].xRot += Mth.sin(Mth.TWO_PI * phase - 0.8f) * 0.15f * amount;
        tail[2].xRot += Mth.sin(Mth.TWO_PI * phase - 1.6f) * 0.12f * amount - 0.2f * amount;
        tail[3].xRot += Mth.sin(t * 0.3f) * 0.08f;
        tail[1].yRot += Mth.sin(t * 0.11f) * 0.12f;
    }

    /** Bolt upright on its haunches, forepaws clasped at the chest, staring. Only the tail moves. */
    private void poseSit(float t) {
        body.xRot = -1.15f;
        body.y += 1.2f;
        body.z += 2.2f;
        head.xRot += 1.05f;
        // Front paws held up at the chest.
        for (int i = 0; i < 2; i++) {
            upper[i].xRot = -0.9f;
            lower[i].xRot = -1.3f;
            end[i].xRot = 0.6f;
        }
        // Hind legs folded under, feet flat.
        for (int i = 2; i < 4; i++) {
            L227ModelKit.placeLeg(body, upper[i], lower[i], HIP_Y, HIP_Z, GROUND - FOOT, -2.0f, THIGH, SHIN, false);
            end[i].xRot = -(body.xRot + upper[i].xRot + lower[i].xRot);
        }
        // The body leans back, so the tail has to come further up to lie along the spine.
        tail[0].xRot += 1.2f;
        tail[1].xRot -= 0.1f;
        tail[3].xRot += Mth.sin(t * 0.5f) * 0.25f;   // the twitch
    }

    /** In the air: stretched out, all four sets of claws reaching. */
    private void poseLeap(float t) {
        body.xRot = -0.2f;
        body.y -= 2.0f;
        for (int i = 0; i < 2; i++) {
            upper[i].xRot = -1.6f;
            lower[i].xRot = -0.3f;
            end[i].xRot = 0.3f;
        }
        for (int i = 2; i < 4; i++) {
            upper[i].xRot = 1.3f;
            lower[i].xRot = 0.4f;
            end[i].xRot = -0.2f;
        }
        upper[0].zRot = 0.35f;
        upper[1].zRot = -0.35f;
        head.xRot += 0.2f;
        tail[0].xRot = -0.1f;
        tail[1].xRot = 0.0f;
        tail[2].xRot = 0.1f;
        tail[3].xRot = Mth.sin(t * 0.9f) * 0.2f;
    }

    /**
     * Clinging flat against a body: nose up, belly facing in, legs splayed and hooked in, head
     * turned in to bite, tail hanging. The renderer puts the model's front against the player.
     */
    private void poseCling(float t) {
        body.xRot = -1.5f;
        body.y -= 1.0f;
        body.z += 1.0f;
        float chew = Mth.sin(t * 1.3f);
        head.xRot += 0.75f + chew * 0.12f;
        head.yRot += Mth.sin(t * 0.43f) * 0.3f;
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            int side = (i % 2 == 0) ? -1 : 1;
            upper[i].xRot = front ? -0.9f : 0.2f;
            upper[i].zRot = side * (front ? 0.9f : 0.75f) + Mth.sin(t * 0.8f + i) * 0.08f;
            lower[i].xRot = front ? -0.8f : 0.9f;
            end[i].xRot = 0.6f;
        }
        // With the body nose-up, the rump points down and the back faces away from the player:
        // the tail hangs down along that side, curling out away from the body.
        tail[0].xRot = 0.35f;
        tail[1].xRot = 0.15f + Mth.sin(t * 0.25f) * 0.1f;
        tail[2].xRot = 0.25f;
        tail[3].xRot = 0.35f;
    }
}
