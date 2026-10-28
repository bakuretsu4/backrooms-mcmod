package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.L227Wolf;
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
 * The Level 227 wolf, built in code: a big, gaunt, shaggy wolf whose snout is split down the
 * middle. Seen from the side it is nearly a wolf; then the two halves of its muzzle swing apart
 * sideways like a pair of shears, and each half is lined along its inner edge with two rows of
 * teeth pointing inward. Behind them, a wet throat.
 *
 * <pre>
 *            ears ▲▲      mane ≋≋≋ hackles ˄˄˄˄˄˄˄˄˄˄˄˄˄˄˄
 *   jaw L ═╗  skull ═ neck ═ chest ══════ waist ═══ hips ═══ tail ≈≈≈
 *   jaw R ═╝                 ║  ║              ║   ║
 *   (seen from above, the jaws open like this:  ⟨ )               paws
 * </pre>
 *
 * <p>The legs are canine: front legs straight-ish with the elbow back, hind legs with the knee
 * forward and a long near-vertical hock. A two-bone solver ({@link L227ModelKit#placeLeg}) puts
 * every paw where the gait wants it, so the paws stay planted whatever the body does.
 *
 * <p>Model space: pixels, +Y down, the front faces -Z, the ground at y = 24. The texture is 256 x
 * 256 for a 128 x 128 UV layout, made by {@code tools/anomaly_assets/level227_predators.py}, which
 * follows the UV map noted at each box. A second texture with only the eyes is drawn full-bright.
 */
public class L227WolfModel extends HierarchicalModel<L227Wolf> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Backrooms.id("l227_wolf"), "main");

    private static final float GROUND = 24.0f;
    private static final float BODY_Y = 9.0f;
    private static final float BODY_Z = -1.0f;
    // Joints in body space.
    private static final float SHOULDER_X = 3.4f, SHOULDER_Y = 2.0f, SHOULDER_Z = -8.5f;
    private static final float HIP_X = 3.3f, HIP_Y = 1.0f, HIP_Z = 11.0f;
    private static final float UPPER_ARM = 8.0f, FOREARM = 7.5f;
    private static final float THIGH = 7.0f, SHIN = 6.5f, HOCK = 5.5f;
    /** The wrist rides this high when the paw is flat (paw plus a short pastern). */
    private static final float WRIST_HEIGHT = 2.0f;
    private static final float PAW_HEIGHT = 1.2f;
    /** The hock sits this far behind its paw. */
    private static final float HOCK_BACK = 1.6f;
    private static final float NECK_PITCH = -0.62f;
    private static final float HEAD_PITCH = 0.5f;
    private static final float TAIL_PITCH = -0.75f;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart jawRight;
    private final ModelPart jawLeft;
    private final ModelPart earRight;
    private final ModelPart earLeft;
    private final ModelPart[] tail = new ModelPart[3];
    private final ModelPart[] upper = new ModelPart[4];
    private final ModelPart[] lower = new ModelPart[4];
    /** Front legs: the paw. Hind legs: the hock (the paw hangs off it, {@link #hindPaw}). */
    private final ModelPart[] end = new ModelPart[4];
    private final ModelPart[] hindPaw = new ModelPart[2];

    public L227WolfModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.neck = body.getChild("neck");
        this.head = neck.getChild("head");
        this.jawRight = head.getChild("jaw_right");
        this.jawLeft = head.getChild("jaw_left");
        this.earRight = head.getChild("ear_right");
        this.earLeft = head.getChild("ear_left");
        tail[0] = body.getChild("tail");
        tail[1] = tail[0].getChild("tail1");
        tail[2] = tail[1].getChild("tail2");
        String[] limbs = {"right_arm", "left_arm", "right_leg", "left_leg"};
        for (int i = 0; i < 4; i++) {
            upper[i] = body.getChild(limbs[i]);
            lower[i] = upper[i].getChild("lower");
            end[i] = lower[i].getChild(i < 2 ? "paw" : "hock");
        }
        hindPaw[0] = end[2].getChild("paw");
        hindPaw[1] = end[3].getChild("paw");
    }

    // ================================================================== building

    // UV regions for the rods (fur tufts, teeth, claws, tongue): see the python generator.
    private static final int FUR_U = 104, FUR_V = 48;
    private static final int TOOTH_U = 72, TOOTH_V = 70;
    private static final int CLAW_U = 88, CLAW_V = 70;
    private static final int TONGUE_U = 72, TONGUE_V = 80;

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        RandomSource rng = RandomSource.create(0x227_B01FL);

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        // Chest: deep, narrow, the ribs under the fur. UV (0,0).
                        .texOffs(0, 0).addBox(-4.5f, -5.0f, -12.0f, 9, 10, 12)
                        // Waist: tucked up, the belly lean. UV (44,0).
                        .texOffs(44, 0).addBox(-3.5f, -4.2f, -0.5f, 7, 8, 8)
                        // Hips. UV (76,0).
                        .texOffs(76, 0).addBox(-4.0f, -4.6f, 7.0f, 8, 9, 8)
                        // The mane: a heavy ruff over the shoulders. UV (0,24).
                        .texOffs(0, 24).addBox(-5.5f, -6.2f, -13.0f, 11, 8, 6),
                PartPose.offset(0.0f, BODY_Y, BODY_Z));
        fur(body, rng);

        PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create()
                        // UV (36,24).
                        .texOffs(36, 24).addBox(-3.0f, -3.0f, -7.0f, 6, 6, 8),
                PartPose.offsetAndRotation(0.0f, -1.5f, -11.5f, NECK_PITCH, 0.0f, 0.0f));
        // The ruff carries on round the throat.
        for (int i = 0; i < 10; i++) {
            float a = (i / 9.0f - 0.5f) * 2.6f;
            float x = Mth.sin(a) * 3.2f;
            float y = Mth.cos(a) * 2.8f;
            rod(neck, "ruff" + i, FUR_U + rng.nextInt(16), FUR_V + rng.nextInt(30), x, y, -3.0f - rng.nextFloat() * 3.0f,
                    x * 1.5f, y + 3.0f + rng.nextFloat() * 2.0f, -1.0f + rng.nextFloat() * 2.0f, 1.3f + rng.nextFloat() * 0.6f);
        }
        head(neck, rng);

        for (int side = -1; side <= 1; side += 2) {
            frontLeg(body, side < 0 ? "right_arm" : "left_arm", side, rng);
            hindLeg(body, side < 0 ? "right_leg" : "left_leg", side, rng);
        }
        tail(body, rng);
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Shaggy fur over the body: hackles along the spine, a fringe under the belly, a heavy mane. */
    private static void fur(PartDefinition body, RandomSource rng) {
        int i = 0;
        // Hackles: a ridge of coarse tufts standing up along the back, swept back.
        for (float z = -12.5f; z <= 14.0f; z += 1.25f, i++) {
            float top = z < -0.5f ? -5.0f : z < 7.0f ? -4.2f : -4.6f;
            float x = (rng.nextFloat() - 0.5f) * 1.6f;
            float lift = z < -5.0f ? 3.2f : 2.2f;
            rod(body, "hackle" + i, FUR_U + rng.nextInt(16), FUR_V + rng.nextInt(30), x, top + 0.6f, z,
                    x * 1.4f, top - lift - rng.nextFloat() * 1.2f, z + 1.6f + rng.nextFloat(), 1.2f + rng.nextFloat() * 0.6f);
        }
        // The mane: long tufts falling over the shoulders and down the chest.
        for (int k = 0; k < 18; k++) {
            float a = (k / 17.0f) * Mth.PI;               // over the top, side to side
            float x = -Mth.cos(a) * 5.4f;
            float y = -Mth.sin(a) * 5.6f + 1.0f;
            float z = -12.5f + rng.nextFloat() * 5.0f;
            rod(body, "mane" + k, FUR_U + rng.nextInt(16), FUR_V + rng.nextInt(30), x, y, z,
                    x * 1.25f, y + 3.5f + rng.nextFloat() * 2.5f, z + 2.0f + rng.nextFloat() * 1.5f, 1.4f + rng.nextFloat() * 0.7f);
        }
        // Belly fringe and the long hair at the back of the chest.
        for (int k = 0; k < 14; k++) {
            float z = -11.0f + k * 1.5f;
            float bottom = z < -0.5f ? 5.0f : 3.8f;
            float x = (k % 2 == 0 ? -1 : 1) * (1.0f + rng.nextFloat() * 2.5f);
            rod(body, "fringe" + k, FUR_U + rng.nextInt(16), FUR_V + rng.nextInt(30), x, bottom - 0.6f, z,
                    x * 1.1f, bottom + 1.6f + rng.nextFloat() * 1.6f, z + 1.2f, 1.1f + rng.nextFloat() * 0.5f);
        }
        // Flank tufts breaking up the box outline.
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 7; k++) {
                float z = -10.0f + k * 3.6f;
                float half = z < -0.5f ? 4.5f : z < 7.0f ? 3.5f : 4.0f;
                float y = -2.5f + rng.nextFloat() * 5.0f;
                rod(body, "flank" + side + "_" + k, FUR_U + rng.nextInt(16), FUR_V + rng.nextInt(30), side * (half - 0.4f), y, z,
                        side * (half + 0.9f), y + 1.8f, z + 1.8f, 1.2f + rng.nextFloat() * 0.5f);
            }
        }
    }

    /**
     * The head: skull, brow, tall ears, and the split muzzle. Each jaw half is hinged at its outer
     * back corner, so opening swings its front outward; teeth line the inner faces in two rows.
     */
    private static void head(PartDefinition neck, RandomSource rng) {
        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create()
                        // Skull. UV (66,24).
                        .texOffs(66, 24).addBox(-3.5f, -3.5f, -6.0f, 7, 7, 7)
                        // Heavy brow over the eyes. UV (92,40).
                        .texOffs(92, 40).addBox(-3.6f, -3.9f, -6.4f, 7.2f, 1.4f, 2)
                        // The throat behind the jaws: only seen when they open. UV (60,40).
                        .texOffs(60, 40).addBox(-2.2f, -1.2f, -7.4f, 4.4f, 3.6f, 3),
                PartPose.offsetAndRotation(0.0f, 0.0f, -6.5f, HEAD_PITCH, 0.0f, 0.0f));
        // Cheek ruff, swept back.
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 5; k++) {
                float y = -1.5f + k * 1.1f;
                rod(head, "cheek" + side + "_" + k, FUR_U + rng.nextInt(16), FUR_V + rng.nextInt(30), side * 3.2f, y, -3.0f,
                        side * 4.6f, y + 0.8f, 0.5f + rng.nextFloat(), 1.2f);
            }
        }
        // Ears: tall, pointed, set high and wide; they flatten when it hunts. UV (76,40) / (84,40).
        head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(76, 40).addBox(-1.3f, -5.0f, -0.6f, 2.6f, 5, 1.2f),
                PartPose.offsetAndRotation(-2.3f, -3.2f, -2.0f, -0.15f, 0.0f, -0.32f));
        head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(84, 40).addBox(-1.3f, -5.0f, -0.6f, 2.6f, 5, 1.2f),
                PartPose.offsetAndRotation(2.3f, -3.2f, -2.0f, -0.15f, 0.0f, 0.32f));
        jaw(head, "jaw_right", -1, rng);
        jaw(head, "jaw_left", 1, rng);
        // A long wet tongue lying in the throat.
        rod(head, "tongue", TONGUE_U, TONGUE_V, 0.0f, 1.6f, -5.5f, 0.0f, 2.2f, -10.5f, 1.6f);
    }

    /**
     * One half of the muzzle ({@code side} -1 right, +1 left): the snout half, its nose half, and
     * two rows of teeth on the inner face. The pivot is the hinge at the outer back corner.
     */
    private static void jaw(PartDefinition head, String name, int side, RandomSource rng) {
        // Inner face at x = 0 in head space, outer at |x| = 2.6. UV (0,40) right, (22,40) left;
        // nose halves (44,40) / (52,40).
        int uv = side < 0 ? 0 : 22;
        float hingeX = side * 2.6f;
        float boxX = side < 0 ? 0.0f : -2.6f;
        PartDefinition jaw = head.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(uv, 40).addBox(boxX, -1.4f, -7.0f, 2.6f, 4.2f, 7)
                        // The nose half, narrower and lower: the snout tapers to a point.
                        .texOffs(side < 0 ? 44 : 52, 40).addBox(side < 0 ? 0.4f : -2.6f, -0.9f, -8.6f, 2.2f, 3.0f, 1.6f),
                PartPose.offset(hingeX, 0.4f, -5.6f));
        // Teeth: two rows along the inner face pointing inward, the front ones longest.
        float innerX = side < 0 ? 2.6f : -2.6f;   // the inner face, in the jaw's own frame
        int t = 0;
        for (int row = 0; row < 2; row++) {
            float y = row == 0 ? -0.8f : 2.1f;
            for (int k = 0; k < 7; k++) {
                float z = -0.8f - k * 1.05f;
                float length = 1.0f + (k / 6.0f) * 0.9f + rng.nextFloat() * 0.4f;
                float tilt = (rng.nextFloat() - 0.5f) * 0.5f;
                rod(jaw, "tooth" + t++, TOOTH_U + rng.nextInt(10), TOOTH_V + rng.nextInt(4), innerX + side * 0.2f, y, z,
                        innerX - side * length, y + tilt + (row == 0 ? 0.3f : -0.3f), z - 0.35f, 0.5f + rng.nextFloat() * 0.15f);
            }
        }
        // Two fangs at the tip, crossing when closed.
        rod(jaw, "fang_a", TOOTH_U, TOOTH_V, innerX + side * 0.3f, -0.4f, -7.4f, innerX - side * 2.1f, 0.4f, -8.2f, 0.7f);
        rod(jaw, "fang_b", TOOTH_U + 4, TOOTH_V, innerX + side * 0.3f, 1.8f, -7.6f, innerX - side * 2.0f, 1.1f, -8.3f, 0.7f);
    }

    /** A front leg: upper arm, forearm, and a big paw with four claws. {@code side}: -1 right, +1 left. */
    private static void frontLeg(PartDefinition body, String name, int side, RandomSource rng) {
        int uv = side < 0 ? 0 : 12;
        PartDefinition arm = body.addOrReplaceChild(name, CubeListBuilder.create()
                        // UV (0,56) / (12,56).
                        .texOffs(uv, 56).addBox(-1.4f, -0.6f, -1.4f, 2.8f, UPPER_ARM + 0.6f, 2.8f),
                PartPose.offset(side * SHOULDER_X, SHOULDER_Y, SHOULDER_Z));
        rod(arm, "feather", FUR_U + rng.nextInt(16), FUR_V + rng.nextInt(30), 0.0f, 2.0f, 1.0f, 0.0f, 6.5f, 2.4f, 1.3f);
        PartDefinition fore = arm.addOrReplaceChild("lower", CubeListBuilder.create()
                        // UV (24,56) / (32,56).
                        .texOffs(24 + (side < 0 ? 0 : 8), 56).addBox(-1.0f, 0.0f, -1.0f, 2, FOREARM, 2),
                PartPose.offset(0.0f, UPPER_ARM, 0.0f));
        PartDefinition paw = fore.addOrReplaceChild("paw", CubeListBuilder.create()
                        // UV (0,70), shared by all four paws.
                        .texOffs(0, 70).addBox(-1.3f, WRIST_HEIGHT - PAW_HEIGHT - 0.2f, -2.6f, 2.6f, PAW_HEIGHT + 0.2f, 3.4f),
                PartPose.offset(0.0f, FOREARM, 0.0f));
        claws(paw, WRIST_HEIGHT - 0.2f, -2.6f);
    }

    /** A hind leg: thigh, shin, the long hock, and the paw. */
    private static void hindLeg(PartDefinition body, String name, int side, RandomSource rng) {
        int uv = side < 0 ? 0 : 14;
        PartDefinition thigh = body.addOrReplaceChild(name, CubeListBuilder.create()
                        // UV (40,56) / (54,56).
                        .texOffs(40 + uv, 56).addBox(-1.7f, -0.8f, -1.8f, 3.4f, THIGH + 0.8f, 3.6f),
                PartPose.offset(side * HIP_X, HIP_Y, HIP_Z));
        rod(thigh, "breeches", FUR_U + rng.nextInt(16), FUR_V + rng.nextInt(30), 0.0f, 1.0f, 1.6f, 0.0f, 6.0f, 3.2f, 1.6f);
        PartDefinition shin = thigh.addOrReplaceChild("lower", CubeListBuilder.create()
                        // UV (68,56) / (77,56).
                        .texOffs(68 + (side < 0 ? 0 : 9), 56).addBox(-1.1f, 0.0f, -1.1f, 2.2f, SHIN, 2.2f),
                PartPose.offset(0.0f, THIGH, 0.0f));
        PartDefinition hock = shin.addOrReplaceChild("hock", CubeListBuilder.create()
                        // UV (86,56) / (94,56).
                        .texOffs(86 + (side < 0 ? 0 : 8), 56).addBox(-0.9f, 0.0f, -0.9f, 1.8f, HOCK, 1.8f),
                PartPose.offset(0.0f, SHIN, 0.0f));
        PartDefinition paw = hock.addOrReplaceChild("paw", CubeListBuilder.create()
                        .texOffs(0, 70).addBox(-1.3f, -PAW_HEIGHT * 0.5f, -2.6f, 2.6f, PAW_HEIGHT + 0.2f, 3.4f),
                PartPose.offset(0.0f, HOCK, 0.0f));
        claws(paw, PAW_HEIGHT * 0.5f + 0.4f, -2.6f);
    }

    private static void claws(PartDefinition paw, float y, float z) {
        for (int c = 0; c < 4; c++) {
            float x = -1.0f + c * 0.66f;
            rod(paw, "claw" + c, CLAW_U + c * 2, CLAW_V, x, y - 0.5f, z + 0.2f, x * 1.1f, y + 0.1f, z - 1.2f, 0.45f);
        }
    }

    /** A long bushy tail in three parts so it can swing and curl. */
    private static void tail(PartDefinition body, RandomSource rng) {
        PartDefinition t0 = body.addOrReplaceChild("tail", CubeListBuilder.create()
                        // UV (14,70).
                        .texOffs(14, 70).addBox(-1.5f, -1.5f, 0.0f, 3, 3, 6),
                PartPose.offsetAndRotation(0.0f, -2.8f, 14.5f, TAIL_PITCH, 0.0f, 0.0f));
        PartDefinition t1 = t0.addOrReplaceChild("tail1", CubeListBuilder.create()
                        // UV (32,70).
                        .texOffs(32, 70).addBox(-1.8f, -1.8f, 0.0f, 3.6f, 3.6f, 6),
                PartPose.offset(0.0f, 0.0f, 5.6f));
        PartDefinition t2 = t1.addOrReplaceChild("tail2", CubeListBuilder.create()
                        // UV (52,70).
                        .texOffs(52, 70).addBox(-1.6f, -1.6f, 0.0f, 3.2f, 3.2f, 5.5f),
                PartPose.offset(0.0f, 0.0f, 5.6f));
        PartDefinition[] parts = {t0, t1, t2};
        for (int p = 0; p < 3; p++) {
            for (int k = 0; k < 5; k++) {
                float a = k / 5.0f * Mth.TWO_PI + rng.nextFloat() * 0.6f;
                float r = 1.4f;
                float x = Mth.cos(a) * r;
                float y = Mth.sin(a) * r;
                float z = 1.0f + rng.nextFloat() * 3.5f;
                rod(parts[p], "fluff" + k, FUR_U + rng.nextInt(16), FUR_V + rng.nextInt(30), x, y, z,
                        x * 1.9f, y * 1.9f + 0.4f, z + 2.4f, 1.3f + rng.nextFloat() * 0.5f);
            }
        }
    }

    // ================================================================== animation

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(L227Wolf entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float partial = ageInTicks - entity.tickCount;
        pose(entity.mode(), entity.jaw(partial), entity.crouch(partial), entity.howl(partial), attackTime,
                limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, entity.getId());
    }

    /**
     * Poses the whole wolf. Split from {@link #setupAnim} so the pose only depends on plain numbers
     * (the contact-sheet tool calls it without a live entity).
     */
    public void pose(L227Wolf.Mode mode, float jawOpen, float crouch, float howl, float attack,
                     float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, int id) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float snap = attack > 0.0f ? Mth.sin(attack * Mth.PI) : 0.0f;
        float jaws = Math.max(jawOpen, snap);
        float amount = Mth.clamp(limbSwingAmount * 1.3f, 0.0f, 1.0f);
        boolean running = mode == L227Wolf.Mode.RUSH || mode == L227Wolf.Mode.CHASE || mode == L227Wolf.Mode.FLEE;
        float gallop = running ? 1.0f : Mth.clamp((limbSwingAmount - 0.45f) * 3.0f, 0.0f, 1.0f);
        int seed = id * 31;
        float t = ageInTicks + (seed & 63);

        // --- Body -------------------------------------------------------------------------
        float cycles = limbSwing * Mth.lerp(gallop, 0.14f, 0.1f);
        float phase = cycles - Mth.floor(cycles);
        body.y += crouch * 3.2f;
        body.xRot += crouch * 0.06f;
        body.y += -Math.abs(Mth.sin(Mth.TWO_PI * phase)) * 0.45f * amount * (1.0f - gallop);
        body.xRot += Mth.sin(Mth.TWO_PI * phase) * 0.11f * gallop * amount;
        body.y += Mth.sin(Mth.TWO_PI * phase + 1.2f) * 1.0f * gallop * amount;
        // Panting flanks.
        body.y += Mth.sin(t * (mode == L227Wolf.Mode.ROAM ? 0.15f : 0.45f)) * 0.12f;
        if (howl > 0.0f) {
            // Sitting back on its haunches to howl.
            body.xRot -= howl * 0.28f;
            body.y += howl * 1.2f;
        }
        if (mode == L227Wolf.Mode.FLEE || mode == L227Wolf.Mode.RETREAT) {
            body.xRot += 0.05f;
        }

        // --- Legs -------------------------------------------------------------------------
        // Order: right front, left front, right hind, left hind. Walk = lateral sequence;
        // gallop = rotary (hinds close together, then fronts).
        float[] walkOffset = {0.75f, 0.25f, 0.5f, 0.0f};
        float[] gallopOffset = {0.62f, 0.5f, 0.12f, 0.0f};
        float duty = Mth.lerp(gallop, 0.62f, 0.38f);
        float stride = Mth.lerp(gallop, 5.5f, 11.0f) * amount;
        float liftHeight = Mth.lerp(gallop, 2.0f, 3.6f) * amount;
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            int side = (i % 2 == 0) ? -1 : 1;
            float p = phase + Mth.lerp(gallop, walkOffset[i], gallopOffset[i]);
            p -= Mth.floor(p);
            float dz;
            float lift;
            if (p < duty) {
                float s = p / duty;
                dz = -stride * 0.5f + stride * s;
                lift = 0.0f;
            } else {
                float s = (p - duty) / (1.0f - duty);
                float eased = L227ModelKit.smooth(s);
                dz = stride * 0.5f - stride * eased;
                lift = Mth.sin(Mth.PI * s) * liftHeight;
            }
            float restZ = front ? -0.6f : 0.4f;
            if (howl > 0.0f && !front) {
                restZ -= howl * 3.0f; // hind paws tucked under to sit
            }
            if (crouch > 0.0f) {
                restZ += (front ? -1.0f : 1.0f) * crouch; // a wide, low stance
            }
            float jointY = front ? SHOULDER_Y : HIP_Y;
            float jointZ = front ? SHOULDER_Z : HIP_Z;
            if (front) {
                L227ModelKit.placeLeg(body, upper[i], lower[i], jointY, jointZ, GROUND - WRIST_HEIGHT - lift, restZ + dz,
                        UPPER_ARM, FOREARM, true);
            } else {
                // Aim the knee-shin pair at the hock, which stands behind and above the paw.
                L227ModelKit.placeLeg(body, upper[i], lower[i], jointY, jointZ, GROUND - PAW_HEIGHT - HOCK * 0.96f - lift,
                        restZ + dz + HOCK_BACK, THIGH, SHIN, false);
            }
            upper[i].zRot = -side * 0.06f;
            float net = body.xRot + upper[i].xRot + lower[i].xRot;
            if (front) {
                // Paw flat on the ground; it curls as it lifts.
                end[i].xRot = -net + (lift > 0.0f ? 0.7f * Math.min(1.0f, lift / 3.0f) : 0.0f);
            } else {
                // The hock leans forward down to its paw; the paw stays flat.
                float hockPitch = (float) Math.atan2(-HOCK_BACK, HOCK * 0.96f);
                end[i].xRot = -net + hockPitch;
                hindPaw[i - 2].xRot = -hockPitch + (lift > 0.0f ? 0.5f * Math.min(1.0f, lift / 3.0f) : 0.0f);
            }
        }

        // --- Neck and head ----------------------------------------------------------------
        float yaw = netHeadYaw * Mth.DEG_TO_RAD;
        float pitch = headPitch * Mth.DEG_TO_RAD;
        neck.xRot += pitch * 0.5f - body.xRot * 0.6f + crouch * 0.45f;
        neck.yRot += yaw * 0.5f;
        head.yRot += yaw * 0.45f;
        head.xRot += pitch * 0.3f - crouch * 0.2f;
        float sway = 1.0f - amount * 0.7f;
        neck.yRot += Mth.sin(t * 0.04f) * 0.15f * sway;
        head.zRot += Mth.sin(t * 0.06f + 1.0f) * 0.06f * sway;
        neck.xRot -= Mth.sin(Mth.TWO_PI * phase) * 0.08f * gallop * amount;
        switch (mode) {
            case SEARCH -> {
                neck.xRot += 0.5f;
                head.xRot += 0.2f + Mth.sin(t * 0.8f) * 0.05f;
            }
            case RUSH, CHASE -> neck.xRot += 0.25f; // head low and level, driving forward
            case PACE -> neck.yRot += Mth.sin(t * 0.12f) * 0.3f;
            default -> {
            }
        }
        // The howl: neck thrown back, muzzle to the sky.
        neck.xRot -= howl * 0.95f;
        head.xRot -= howl * 0.55f;
        neck.xRot -= snap * 0.3f;

        // --- The jaws: they spread sideways ---------------------------------------------------
        float chatter = mode == L227Wolf.Mode.CIRCLE ? Math.max(0.0f, Mth.sin(t * 0.9f)) * 0.08f : 0.0f;
        float spread = jaws * 0.95f + chatter;
        jawRight.yRot = spread;
        jawLeft.yRot = -spread;
        // Each half also rolls outward a little, showing the teeth rows.
        jawRight.zRot = -spread * 0.3f;
        jawLeft.zRot = spread * 0.3f;

        // --- Ears ---------------------------------------------------------------------------
        boolean hunting = mode != L227Wolf.Mode.ROAM;
        float flat = hunting ? 0.55f : 0.0f;
        earRight.xRot += flat;
        earLeft.xRot += flat;
        earRight.zRot -= Mth.sin(t * 0.11f) * 0.05f;
        earLeft.zRot += Mth.sin(t * 0.13f + 0.7f) * 0.05f;

        // --- Tail -----------------------------------------------------------------------------
        float tailLift = switch (mode) {
            case FLEE, RETREAT -> -0.6f;                     // tucked
            case RUSH, CHASE -> 0.55f;                       // streaming out behind
            case CIRCLE, HOWL -> 0.3f;                       // held stiff and level
            default -> 0.0f;
        };
        tail[0].xRot += tailLift + Mth.sin(Mth.TWO_PI * phase) * 0.1f * amount;
        tail[0].yRot += Mth.sin(t * 0.09f) * 0.12f + Mth.sin(Mth.TWO_PI * phase) * 0.1f * amount;
        tail[1].xRot += 0.18f - tailLift * 0.3f + Mth.sin(t * 0.09f - 0.6f) * 0.06f;
        tail[1].yRot += Mth.sin(t * 0.09f - 0.7f) * 0.14f;
        tail[2].xRot += 0.12f - tailLift * 0.2f;
        tail[2].yRot += Mth.sin(t * 0.09f - 1.4f) * 0.16f;
    }
}
