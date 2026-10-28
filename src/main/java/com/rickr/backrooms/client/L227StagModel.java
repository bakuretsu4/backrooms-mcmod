package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.L227Stag;
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

import static com.rickr.backrooms.client.L227ModelKit.chain;
import static com.rickr.backrooms.client.L227ModelKit.rod;

/**
 * The Level 227 stag, built in code: a deer far bigger than any real one, too long in the leg,
 * with a shaggy throat mane, very long pale horns, five thin tails, and red eyes.
 *
 * <pre>
 *              horns, long, sweeping up then forward ⟋⟋
 *            ╱╱                                         tails (five) ≈≈
 *   muzzle ═ skull ═ neck (mane ≋ under it) ═ withers ═ chest ═══ haunch ≈≈≈
 *                                                ║   ║        ║   ║
 *                                     long legs, hocks back, cloven hooves
 * </pre>
 *
 * <p>The horns are what it kills with. Grazing or alert they rise up and back over its head like
 * an enormous crown; when it lowers its head to charge, the curve brings their tips forward and
 * they become a pair of lances at chest height. That swing is the whole warning, so the model
 * makes it big and slow.
 *
 * <p>Legs: two-bone solved like the wolf's and the Hound's ({@link L227ModelKit#placeLeg}), plus a
 * long pastern/cannon bone under each to the hoof, so the leg has the deer's high hock.
 *
 * <p>Model space: pixels, +Y down, front -Z, ground at y = 24. Texture 256 x 256 for a 128 x 128
 * UV layout ({@code tools/anomaly_assets/level227_predators.py}); a second texture holds only
 * the eyes, drawn full-bright red.
 */
public class L227StagModel extends HierarchicalModel<L227Stag> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Backrooms.id("l227_stag"), "main");

    private static final float GROUND = 24.0f;
    private static final float BODY_Y = 0.0f;
    private static final float BODY_Z = 0.0f;
    private static final float SHOULDER_X = 4.2f, SHOULDER_Y = 3.0f, SHOULDER_Z = -9.0f;
    private static final float HIP_X = 4.0f, HIP_Y = 2.0f, HIP_Z = 10.0f;
    private static final float UPPER_ARM = 9.0f, FOREARM = 8.5f, PASTERN = 4.0f;
    private static final float THIGH = 10.0f, SHIN = 9.0f, CANNON = 7.5f;
    private static final float HOOF = 1.8f;
    /** The hock stands this far behind its hoof. */
    private static final float HOCK_BACK = 2.2f;
    private static final float NECK_PITCH = -0.95f;
    private static final float HEAD_PITCH = 1.15f;
    private static final float TAIL_PITCH = -0.85f;
    private static final int TAILS = 5;

    // UV regions for rods.
    private static final int HORN_U = 104, HORN_V = 28;
    private static final int SHAG_U = 104, SHAG_V = 66;
    private static final int TAIL_U = 40, TAIL_V = 74;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart earRight;
    private final ModelPart earLeft;
    private final ModelPart[][] tails = new ModelPart[TAILS][3];
    private final ModelPart[] upper = new ModelPart[4];
    private final ModelPart[] lower = new ModelPart[4];
    /** The pastern (front) or cannon (hind) bone under each leg, and its hoof. */
    private final ModelPart[] cannon = new ModelPart[4];
    private final ModelPart[] hoof = new ModelPart[4];

    public L227StagModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.neck = body.getChild("neck");
        this.head = neck.getChild("head");
        this.jaw = head.getChild("jaw");
        this.earRight = head.getChild("ear_right");
        this.earLeft = head.getChild("ear_left");
        for (int i = 0; i < TAILS; i++) {
            tails[i][0] = body.getChild("tail" + i);
            tails[i][1] = tails[i][0].getChild("mid");
            tails[i][2] = tails[i][1].getChild("tip");
        }
        String[] limbs = {"right_arm", "left_arm", "right_leg", "left_leg"};
        for (int i = 0; i < 4; i++) {
            upper[i] = body.getChild(limbs[i]);
            lower[i] = upper[i].getChild("lower");
            cannon[i] = lower[i].getChild("cannon");
            hoof[i] = cannon[i].getChild("hoof");
        }
    }

    // ================================================================== building

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        RandomSource rng = RandomSource.create(0x227_57A6L);

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        // Chest and shoulders: deep. UV (0,0).
                        .texOffs(0, 0).addBox(-6.0f, -6.0f, -13.0f, 12, 12, 13)
                        // Haunches: a little narrower, the belly tucked. UV (50,0).
                        .texOffs(50, 0).addBox(-5.5f, -5.5f, -0.5f, 11, 11, 13)
                        // The hump of the withers. UV (0,28).
                        .texOffs(0, 28).addBox(-4.0f, -8.5f, -12.0f, 8, 3, 10),
                PartPose.offset(0.0f, BODY_Y, BODY_Z));
        // Shaggy hair over the withers and along the spine.
        for (int k = 0; k < 12; k++) {
            float z = -12.0f + k * 2.0f;
            float top = z < -2.0f ? -8.3f : -5.4f;
            rod(body, "ridge" + k, SHAG_U + rng.nextInt(12), SHAG_V + rng.nextInt(20), (rng.nextFloat() - 0.5f) * 2.0f, top + 0.6f, z,
                    (rng.nextFloat() - 0.5f) * 3.0f, top - 1.4f, z + 2.4f, 1.3f + rng.nextFloat() * 0.5f);
        }

        PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create()
                        // Thick, muscled. UV (38,28).
                        .texOffs(38, 28).addBox(-3.5f, -4.0f, -12.0f, 7, 8, 13),
                PartPose.offsetAndRotation(0.0f, -4.0f, -11.5f, NECK_PITCH, 0.0f, 0.0f));
        // The mane: long coarse hair hanging from the throat.
        for (int k = 0; k < 16; k++) {
            float z = -11.0f + k * 0.75f;
            float x = (rng.nextFloat() - 0.5f) * 5.0f;
            rod(neck, "mane" + k, SHAG_U + rng.nextInt(12), SHAG_V + rng.nextInt(20), x, 3.4f, z,
                    x * 1.2f, 8.0f + rng.nextFloat() * 4.0f, z + 2.5f + rng.nextFloat() * 2.0f, 1.4f + rng.nextFloat() * 0.6f);
        }
        head(neck, rng);

        for (int side = -1; side <= 1; side += 2) {
            leg(body, side < 0 ? "right_arm" : "left_arm", side, true, rng);
            leg(body, side < 0 ? "right_leg" : "left_leg", side, false, rng);
        }
        for (int i = 0; i < TAILS; i++) {
            tail(body, i, rng);
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static void head(PartDefinition neck, RandomSource rng) {
        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create()
                        // Skull. UV (78,28).
                        .texOffs(78, 28).addBox(-3.0f, -3.5f, -5.0f, 6, 6, 7)
                        // Long muzzle. UV (0,44).
                        .texOffs(0, 44).addBox(-2.1f, -1.5f, -11.5f, 4.2f, 4.5f, 7)
                        // Bulging red eyes on the sides. UV (58,50).
                        .texOffs(58, 50).addBox(-3.5f, -2.2f, -4.2f, 1, 1.2f, 1.4f)
                        .texOffs(58, 50).addBox(2.5f, -2.2f, -4.2f, 1, 1.2f, 1.4f),
                PartPose.offsetAndRotation(0.0f, -0.5f, -11.0f, HEAD_PITCH, 0.0f, 0.0f));
        head.addOrReplaceChild("jaw", CubeListBuilder.create()
                        // UV (24,50).
                        .texOffs(24, 50).addBox(-1.8f, 0.0f, -6.5f, 3.6f, 1.4f, 6.5f),
                PartPose.offset(0.0f, 3.0f, -4.8f));
        // Ears: long, set wide, to the sides. UV (46,50) / (52,50).
        head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(46, 50).addBox(-0.9f, -4.5f, -0.4f, 1.8f, 4.5f, 0.8f),
                PartPose.offsetAndRotation(-2.6f, -2.8f, 0.6f, -0.3f, 0.0f, -1.05f));
        head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(52, 50).addBox(-0.9f, -4.5f, -0.4f, 1.8f, 4.5f, 0.8f),
                PartPose.offsetAndRotation(2.6f, -2.8f, 0.6f, -0.3f, 0.0f, 1.05f));
        // Chin beard.
        for (int k = 0; k < 5; k++) {
            float x = -1.2f + k * 0.6f;
            rod(head, "beard" + k, SHAG_U + rng.nextInt(12), SHAG_V + rng.nextInt(20), x, 3.0f, -7.0f + k * 0.4f,
                    x, 6.5f + rng.nextFloat() * 1.5f, -6.0f + k * 0.5f, 1.0f);
        }
        horns(head, rng);
    }

    /**
     * The horns: each a main beam of six tapering pieces sweeping up, out and back over the head,
     * then curling forward into a long point, with four tines. Gnarled: every point jitters a
     * little, differently on each side.
     */
    private static void horns(PartDefinition head, RandomSource rng) {
        float[][] beam = {
                {1.8f, -3.4f, -1.0f}, {3.6f, -8.0f, 0.8f}, {5.6f, -13.0f, 1.4f}, {6.9f, -18.0f, -0.4f},
                {7.1f, -22.5f, -4.2f}, {6.2f, -25.6f, -9.2f}, {4.9f, -26.6f, -15.0f},
        };
        for (int side = -1; side <= 1; side += 2) {
            String s = side < 0 ? "r" : "l";
            float[] pts = new float[beam.length * 3];
            for (int i = 0; i < beam.length; i++) {
                float jitter = i == 0 ? 0.0f : 0.5f;
                pts[i * 3] = side * beam[i][0] + (rng.nextFloat() - 0.5f) * jitter;
                pts[i * 3 + 1] = beam[i][1] + (rng.nextFloat() - 0.5f) * jitter;
                pts[i * 3 + 2] = beam[i][2] + (rng.nextFloat() - 0.5f) * jitter;
            }
            chain(head, "horn_" + s, HORN_U + rng.nextInt(10), HORN_V + rng.nextInt(16), pts, 2.4f, 0.7f);
            // Tines: brow tine forward over the face, two forward from the beam, one swept back.
            float[][] tines = {
                    {1, 0.45f, 2.6f, -1.0f, -7.0f, 1.1f},
                    {2, 0.6f, 2.0f, -2.0f, -6.0f, 1.0f},
                    {3, 0.5f, 2.2f, -1.0f, -5.5f, 0.9f},
                    {2, 0.3f, 1.4f, -3.5f, 5.0f, 0.8f},
            };
            for (int k = 0; k < tines.length; k++) {
                int from = (int) tines[k][0];
                float f = tines[k][1];
                float ax = Mth.lerp(f, pts[from * 3], pts[from * 3 + 3]);
                float ay = Mth.lerp(f, pts[from * 3 + 1], pts[from * 3 + 4]);
                float az = Mth.lerp(f, pts[from * 3 + 2], pts[from * 3 + 5]);
                float bx = ax + side * tines[k][2];
                float by = ay + tines[k][3];
                float bz = az + tines[k][4];
                float mx = (ax + bx) * 0.5f + side * 0.4f;
                float my = (ay + by) * 0.5f + 0.5f;
                float mz = (az + bz) * 0.5f;
                chain(head, "tine_" + s + k, HORN_U + rng.nextInt(10), HORN_V + rng.nextInt(16),
                        new float[]{ax, ay, az, mx, my, mz, bx, by, bz}, tines[k][5], 0.45f);
            }
        }
    }

    /** A leg: upper bone, lower bone, the long pastern (front) or cannon (hind), and a cloven hoof. */
    private static void leg(PartDefinition body, String name, int side, boolean front, RandomSource rng) {
        float l1 = front ? UPPER_ARM : THIGH;
        float l2 = front ? FOREARM : SHIN;
        float l3 = front ? PASTERN : CANNON;
        int r = side < 0 ? 0 : 1;
        PartDefinition top;
        if (front) {
            // UV (0,58) / (15,58).
            top = body.addOrReplaceChild(name, CubeListBuilder.create().texOffs(r * 15, 58)
                    .addBox(-1.7f, -1.0f, -1.9f, 3.4f, l1 + 1.0f, 3.8f), PartPose.offset(side * SHOULDER_X, SHOULDER_Y, SHOULDER_Z));
        } else {
            // UV (48,58) / (66,58): the big hams.
            top = body.addOrReplaceChild(name, CubeListBuilder.create().texOffs(48 + r * 18, 58)
                    .addBox(-2.0f, -1.0f, -2.5f, 4, l1 + 0.5f, 5), PartPose.offset(side * HIP_X, HIP_Y, HIP_Z));
        }
        PartDefinition mid = top.addOrReplaceChild("lower", CubeListBuilder.create()
                        // Front UV (30,58) / (39,58); hind (84,58) / (94,58).
                        .texOffs(front ? 30 + r * 9 : 84 + r * 10, 58)
                        .addBox(front ? -1.0f : -1.2f, 0.0f, front ? -1.1f : -1.3f, front ? 2 : 2.4f, l2, front ? 2.2f : 2.6f),
                PartPose.offset(0.0f, l1, 0.0f));
        PartDefinition bone = mid.addOrReplaceChild("cannon", CubeListBuilder.create()
                        // Hind cannon UV (0,74) / (7,74); front pastern (14,74) / (21,74).
                        .texOffs(front ? 14 + r * 7 : r * 7, 74).addBox(-0.8f, 0.0f, -0.9f, 1.6f, l3, 1.8f),
                PartPose.offset(0.0f, l2, 0.0f));
        PartDefinition hoof = bone.addOrReplaceChild("hoof", CubeListBuilder.create()
                        // UV (28,74), all four.
                        .texOffs(28, 74).addBox(-1.1f, 0.0f, -1.8f, 2.2f, HOOF, 2.6f),
                PartPose.offset(0.0f, l3, 0.0f));
        // Dewclaws at the back, and the split of the cloven hoof.
        rod(hoof, "dewclaw", 28, 80, 0.0f, -0.8f, 0.8f, 0.0f, 0.4f, 1.6f, 0.6f);
        rod(hoof, "cleft", 28, 80, 0.0f, 0.2f, -1.9f, 0.0f, HOOF, -1.9f, 0.25f);
        if (!front) {
            // Hair feathering at the back of the hocks.
            rod(mid, "feather", SHAG_U + rng.nextInt(12), SHAG_V + rng.nextInt(20), 0.0f, l2 - 2.5f, 1.2f, 0.0f, l2 + 0.5f, 2.2f, 1.2f);
        }
    }

    /** One of the five thin tails: three pieces so it can whip, with a tuft at the end. */
    private static void tail(PartDefinition body, int i, RandomSource rng) {
        float spread = (i - (TAILS - 1) / 2.0f) * 0.3f;
        PartDefinition t0 = body.addOrReplaceChild("tail" + i, CubeListBuilder.create()
                        // UV (40,74) for every piece.
                        .texOffs(TAIL_U, TAIL_V).addBox(-0.55f, -0.55f, 0.0f, 1.1f, 1.1f, 4.5f),
                PartPose.offsetAndRotation(spread * 4.0f, -4.6f + Math.abs(spread) * 1.5f, 12.4f, TAIL_PITCH, spread, 0.0f));
        PartDefinition t1 = t0.addOrReplaceChild("mid", CubeListBuilder.create()
                        .texOffs(TAIL_U, TAIL_V).addBox(-0.45f, -0.45f, 0.0f, 0.9f, 0.9f, 4.5f),
                PartPose.offset(0.0f, 0.0f, 4.3f));
        PartDefinition t2 = t1.addOrReplaceChild("tip", CubeListBuilder.create()
                        .texOffs(TAIL_U, TAIL_V).addBox(-0.4f, -0.4f, 0.0f, 0.8f, 0.8f, 4.0f),
                PartPose.offset(0.0f, 0.0f, 4.3f));
        for (int k = 0; k < 3; k++) {
            float a = k * 2.1f + rng.nextFloat();
            rod(t2, "tuft" + k, SHAG_U + rng.nextInt(12), SHAG_V + rng.nextInt(20), 0.0f, 0.0f, 3.0f,
                    Mth.cos(a) * 1.0f, Mth.sin(a) * 1.0f, 6.0f + rng.nextFloat() * 1.5f, 1.0f);
        }
    }

    // ================================================================== animation

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(L227Stag entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float partial = ageInTicks - entity.tickCount;
        pose(entity.mode(), entity.lower(partial), entity.alert(partial), limbSwing, limbSwingAmount, ageInTicks,
                netHeadYaw, headPitch, entity.getId());
    }

    /** Poses the stag from plain numbers (the contact-sheet tool calls this without an entity). */
    public void pose(L227Stag.Mode mode, float lowered, float alert, float limbSwing, float limbSwingAmount,
                     float ageInTicks, float netHeadYaw, float headPitch, int id) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float amount = Mth.clamp(limbSwingAmount * 1.2f, 0.0f, 1.0f);
        boolean charging = mode == L227Stag.Mode.CHARGE;
        if (charging) {
            amount = 1.0f;
        }
        float gallop = charging ? 1.0f : Mth.clamp((limbSwingAmount - 0.5f) * 3.0f, 0.0f, 1.0f);
        int seed = id * 37;
        float t = ageInTicks + (seed & 63);
        boolean stuck = mode == L227Stag.Mode.STUCK;
        boolean threat = mode == L227Stag.Mode.THREAT;
        // Grazing: standing still with nothing to do, head down to the moss.
        float graze = mode == L227Stag.Mode.GRAZE ? Mth.clamp(1.0f - amount * 3.0f, 0.0f, 1.0f) : 0.0f;

        // --- Body -------------------------------------------------------------------------
        float cycles = limbSwing * Mth.lerp(gallop, 0.1f, 0.075f);
        float phase = cycles - Mth.floor(cycles);
        body.y += -Math.abs(Mth.sin(Mth.TWO_PI * phase)) * 0.5f * amount * (1.0f - gallop);
        body.xRot += Mth.sin(Mth.TWO_PI * phase) * 0.12f * gallop * amount;
        body.y += Mth.sin(Mth.TWO_PI * phase + 1.2f) * 1.6f * gallop * amount;
        body.y += Mth.sin(t * 0.08f) * 0.12f; // breathing
        if (threat || stuck) {
            body.xRot += 0.07f; // weight on the forehand
            body.y += 0.6f;
        }
        if (stuck) {
            body.zRot += Mth.sin(t * 0.9f) * 0.05f;
        }

        // --- Legs -------------------------------------------------------------------------
        float[] walkOffset = {0.75f, 0.25f, 0.5f, 0.0f};
        float[] gallopOffset = {0.6f, 0.5f, 0.1f, 0.0f};
        float duty = Mth.lerp(gallop, 0.64f, 0.36f);
        float stride = Mth.lerp(gallop, 7.0f, 15.0f) * amount;
        float liftHeight = Mth.lerp(gallop, 2.8f, 5.0f) * amount;
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            int side = (i % 2 == 0) ? -1 : 1;
            float p = phase + Mth.lerp(gallop, walkOffset[i], gallopOffset[i]);
            p -= Mth.floor(p);
            float dz;
            float lift;
            if (p < duty) {
                dz = -stride * 0.5f + stride * (p / duty);
                lift = 0.0f;
            } else {
                float s = (p - duty) / (1.0f - duty);
                dz = stride * 0.5f - stride * L227ModelKit.smooth(s);
                lift = Mth.sin(Mth.PI * s) * liftHeight;
            }
            if (threat && i == 0) {
                // Pawing: the right forehoof lifts and scrapes back along the wood, over and over.
                float paw = (t * 0.1f) % 1.0f;
                lift = Mth.sin(Mth.PI * Math.min(1.0f, paw * 2.0f)) * 3.5f;
                dz = Mth.lerp(paw, -3.5f, 3.0f);
            }
            if (stuck) {
                dz = front ? -2.5f : 2.5f; // legs braced, pulling back
            }
            float restZ = front ? -0.8f : 0.8f;
            if (front) {
                float fetlock = GROUND - HOOF - PASTERN * 0.95f - lift;
                L227ModelKit.placeLeg(body, upper[i], lower[i], SHOULDER_Y, SHOULDER_Z, fetlock, restZ + dz + 0.6f,
                        UPPER_ARM, FOREARM, true);
            } else {
                float hock = GROUND - HOOF - CANNON * 0.95f - lift;
                L227ModelKit.placeLeg(body, upper[i], lower[i], HIP_Y, HIP_Z, hock, restZ + dz + HOCK_BACK,
                        THIGH, SHIN, false);
            }
            upper[i].zRot = -side * 0.04f;
            float net = body.xRot + upper[i].xRot + lower[i].xRot;
            float back = front ? 0.6f : HOCK_BACK;
            float bone = front ? PASTERN : CANNON;
            float bonePitch = (float) Math.atan2(-back, bone * 0.95f);
            float curl = lift > 0.0f ? 0.6f * Math.min(1.0f, lift / 3.0f) : 0.0f;
            cannon[i].xRot = -net + bonePitch + curl;
            hoof[i].xRot = -bonePitch - curl * 1.2f;
        }

        // --- Neck and head ----------------------------------------------------------------
        float yaw = netHeadYaw * Mth.DEG_TO_RAD;
        float pitch = headPitch * Mth.DEG_TO_RAD;
        neck.xRot += pitch * 0.35f - body.xRot;
        neck.yRot += yaw * 0.4f;
        head.yRot += yaw * 0.4f;
        head.xRot += pitch * 0.25f;
        // Grazing: neck right down, head tipped to the ground, chewing.
        neck.xRot += graze * 1.25f;
        head.xRot += graze * 0.35f;
        jaw.xRot = graze * Math.max(0.0f, Mth.sin(t * 0.5f)) * 0.18f;
        // Alert: head flung high, ears forward.
        neck.xRot -= alert * 0.3f;
        // Lowered: the horns come forward as lances. Slow and huge, it is the warning.
        neck.xRot += lowered * 0.75f;
        head.xRot += lowered * 0.3f;
        if (stuck) {
            // Wrenching at the horns stuck in the wood.
            neck.yRot += Mth.sin(t * 0.8f) * 0.32f;
            head.zRot += Mth.sin(t * 1.1f + 0.5f) * 0.25f;
            neck.xRot += Mth.sin(t * 0.55f) * 0.1f;
        } else if (threat) {
            // Tossing the horns low, side to side.
            head.zRot += Mth.sin(t * 0.25f) * 0.18f;
        }
        // Head carried steady while it runs: undo the body's rock.
        neck.xRot -= Mth.sin(Mth.TWO_PI * phase) * 0.12f * gallop * amount;
        float earsForward = Math.max(alert, lowered);
        earRight.xRot -= earsForward * 0.6f;
        earLeft.xRot -= earsForward * 0.6f;
        earRight.zRot += earsForward * 0.4f + Mth.sin(t * 0.17f) * 0.06f;
        earLeft.zRot -= earsForward * 0.4f + Mth.sin(t * 0.19f + 1.0f) * 0.06f;

        // --- The tails: five thin whips ---------------------------------------------------------
        float flag = Math.max(alert, threat ? 0.7f : 0.0f);   // raised in alarm
        for (int i = 0; i < TAILS; i++) {
            float off = i * 1.3f;
            float speed = charging ? 0.6f : stuck ? 0.45f : 0.08f;
            float swing = charging ? 0.35f : stuck ? 0.4f : 0.18f;
            tails[i][0].xRot += flag * 1.0f + (charging ? 0.7f : 0.0f) + Mth.sin(t * speed * 0.7f + off) * swing * 0.4f;
            tails[i][0].yRot += Mth.sin(t * speed + off) * swing;
            tails[i][1].yRot += Mth.sin(t * speed + off - 0.9f) * swing * 1.2f;
            tails[i][1].xRot += 0.25f + Mth.sin(t * speed + off - 0.6f) * swing * 0.3f;
            tails[i][2].yRot += Mth.sin(t * speed + off - 1.8f) * swing * 1.4f;
            tails[i][2].xRot += 0.2f - flag * 0.4f;
        }
    }
}
