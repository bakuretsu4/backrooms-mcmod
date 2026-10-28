package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.L227Doe;
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
 * The Level 227 doe, built in code: a slender, pale deer with legs and neck a little too long,
 * and a black scar-like seam running from the tip of her nose over her head, down the back of her
 * neck, along her spine and to the tip of her tail.
 *
 * <pre>
 *        ears
 *     ▄▄▀▀▄▄       scar ━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
 *    ▐ head ▌━━━┓                                   ┃
 *     ▀▀▀▀▀▀    ┃ neck                              ▼
 *               ┃      ▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄  tail
 *               ┗━━━━ ▐ chest     torso     rump ▌
 *                      ▀▀║▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀║▀▀
 *                        ║  long thin legs   ╲   (hocks bend back)
 *                        ▀                    ▀
 * </pre>
 *
 * <p>The scar is a chain of thin black strips lying along the midline, each between two raw,
 * pinkish lips. When she notices you it opens a crack (the strips widen and the lips part), and
 * when she takes someone through the branch it gapes wide for a moment.
 *
 * <p>Model space: pixels, +Y down, the front faces -Z, the ground at y = 24. The texture is
 * 256 x 256 for a 128 x 128 UV layout, made by {@code tools/anomaly_assets/level227_grazers.py}.
 */
public class L227DoeModel extends HierarchicalModel<L227Doe> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Backrooms.id("l227_doe"), "main");

    private static final float BODY_Y = 5.0f;
    private static final float FRONT_UPPER = 7.0f;
    private static final float FRONT_LOWER = 7.2f;
    private static final float THIGH = 7.0f;
    private static final float CANNON = 8.8f;
    /** Resting neck lean (forward), and how far it bends down to graze. */
    private static final float NECK_REST = 0.52f;
    private static final float NECK_GRAZE = 1.6f;

    /**
     * The scar's pieces: owner (0 body, 1 neck, 2 head, 3 tail), centre x/y/z, length and which
     * way it runs (0 along Z, 1 along Y).
     */
    private static final float[][] SCAR = {
            {2, 0.0f, -1.45f, -5.5f, 4.0f, 0},   // down the muzzle to the nose
            {2, 0.0f, -2.55f, -1.25f, 4.5f, 0},  // over the skull
            {1, 0.0f, -5.5f, 1.85f, 11.0f, 1},   // down the back of the neck (upper)
            {1, 0.0f, -0.5f, 1.85f, 1.2f, 1},    // and its base
            {0, 0.0f, -4.05f, -6.5f, 7.0f, 0},   // over the withers
            {0, 0.0f, -3.55f, 0.5f, 7.0f, 0},    // along the spine
            {0, 0.0f, -4.25f, 7.0f, 6.0f, 0},    // over the rump
            {3, 0.0f, 2.0f, 1.6f, 4.0f, 1},      // down the tail to its tip
    };

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart tail;
    private final ModelPart[] ears = new ModelPart[2];
    private final ModelPart[] upper = new ModelPart[4];
    private final ModelPart[] lower = new ModelPart[4];
    private final ModelPart[] hoof = new ModelPart[4];
    private final ModelPart[] scar = new ModelPart[SCAR.length];
    private final ModelPart[] lipL = new ModelPart[SCAR.length];
    private final ModelPart[] lipR = new ModelPart[SCAR.length];

    public L227DoeModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.neck = body.getChild("neck");
        this.head = neck.getChild("head");
        this.jaw = head.getChild("jaw");
        this.tail = body.getChild("tail");
        this.ears[0] = head.getChild("ear_r");
        this.ears[1] = head.getChild("ear_l");
        String[] legs = {"front_r", "front_l", "rear_r", "rear_l"};
        for (int i = 0; i < 4; i++) {
            upper[i] = body.getChild(legs[i]);
            lower[i] = upper[i].getChild("lower");
            hoof[i] = lower[i].getChild("hoof");
        }
        ModelPart[] owners = {body, neck, head, tail};
        for (int i = 0; i < SCAR.length; i++) {
            ModelPart owner = owners[(int) SCAR[i][0]];
            scar[i] = owner.getChild("scar" + i);
            lipL[i] = owner.getChild("scar" + i + "_lip_l");
            lipR[i] = owner.getChild("scar" + i + "_lip_r");
        }
    }

    // ================================================================== building

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rootDef = mesh.getRoot();

        PartDefinition body = rootDef.addOrReplaceChild("body", CubeListBuilder.create()
                        // Torso: slim. UV (0,0): 7 x 7.5 x 18.
                        .texOffs(0, 0).addBox(-3.5f, -3.5f, -9.0f, 7, 7.5f, 18)
                        // Chest: deeper at the front. UV (52,0): 7.2 x 9 x 7.
                        .texOffs(52, 0).addBox(-3.6f, -4.0f, -10.0f, 7.2f, 9, 7)
                        // Rump: the haunches. UV (82,0): 7.6 x 7.7 x 6.
                        .texOffs(82, 0).addBox(-3.8f, -4.2f, 4.0f, 7.6f, 7.7f, 6),
                PartPose.offset(0.0f, BODY_Y, 0.0f));

        PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create()
                        // Long neck, modelled upright. UV (0,26): 3.4 x 12 x 3.8.
                        .texOffs(0, 26).addBox(-1.7f, -12.0f, -2.0f, 3.4f, 12, 3.8f)
                        // Thicker throat at its base. UV (16,26): 3.8 x 4 x 2.6.
                        .texOffs(16, 26).addBox(-1.9f, -4.0f, -2.6f, 3.8f, 4, 2.6f),
                PartPose.offsetAndRotation(0.0f, -2.5f, -8.5f, NECK_REST, 0.0f, 0.0f));

        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create()
                        // Skull. UV (30,26): 4.4 x 4 x 4.5.
                        .texOffs(30, 26).addBox(-2.2f, -2.5f, -3.5f, 4.4f, 4, 4.5f)
                        // Long, narrow muzzle. UV (50,26): 3 x 2.6 x 4.
                        .texOffs(50, 26).addBox(-1.5f, -1.4f, -7.5f, 3, 2.6f, 4)
                        // Black nose. UV (66,26): 2 x 1.3 x 0.5.
                        .texOffs(66, 26).addBox(-1.0f, -1.5f, -7.9f, 2, 1.3f, 0.5f),
                PartPose.offsetAndRotation(0.0f, -11.5f, -0.4f, -NECK_REST + 0.3f, 0.0f, 0.0f));
        // Lower jaw (chews while she grazes). UV (66,30): 2.6 x 0.8 x 4.
        head.addOrReplaceChild("jaw", CubeListBuilder.create()
                        .texOffs(66, 30).addBox(-1.3f, 0.0f, -4.0f, 2.6f, 0.8f, 4),
                PartPose.offset(0.0f, 1.2f, -3.3f));
        // Big ears, set out to the sides. UV (82,26): 2.2 x 4.5 x 0.5.
        for (int side = -1; side <= 1; side += 2) {
            head.addOrReplaceChild(side < 0 ? "ear_r" : "ear_l", CubeListBuilder.create()
                            .texOffs(82, 26).addBox(-1.1f, -4.5f, -0.25f, 2.2f, 4.5f, 0.5f),
                    PartPose.offsetAndRotation(side * 1.8f, -2.2f, 0.2f, 0.15f, 0.0f, side * 0.75f));
            // Large dark eyes on the sides of the skull. UV (90,26): 0.3 x 1.2 x 1.2.
            head.addOrReplaceChild(side < 0 ? "eye_r" : "eye_l", CubeListBuilder.create()
                            .texOffs(90, 26).addBox(side < 0 ? -0.3f : 0.0f, -0.6f, -0.6f, 0.3f, 1.2f, 1.2f),
                    PartPose.offset(side * 2.2f, -0.9f, -1.9f));
        }

        // The tail: short, held down; the white underside shows when she flags it. UV (112,0): 2.4 x 4 x 1.5.
        body.addOrReplaceChild("tail", CubeListBuilder.create()
                        .texOffs(112, 0).addBox(-1.2f, 0.0f, 0.0f, 2.4f, 4, 1.5f),
                PartPose.offsetAndRotation(0.0f, -3.4f, 9.8f, 0.35f, 0.0f, 0.0f));

        // Legs. Front: UV (0,44) upper 2.2 x 7.5 x 2.4, (10,44) lower 1.2 x 7.7 x 1.2.
        // Rear: UV (24,44) thigh 2.8 x 8 x 3.2, (38,44) cannon 1.2 x 9.3 x 1.2. Hooves UV (16,44): 1.4 x 1.3 x 1.6.
        for (int side = -1; side <= 1; side += 2) {
            String s = side < 0 ? "_r" : "_l";
            PartDefinition fu = body.addOrReplaceChild("front" + s, CubeListBuilder.create()
                            .texOffs(0, 44).addBox(-1.1f, -0.5f, -1.2f, 2.2f, FRONT_UPPER + 0.5f, 2.4f),
                    PartPose.offset(side * 2.2f, 3.5f, -7.0f));
            PartDefinition fl = fu.addOrReplaceChild("lower", CubeListBuilder.create()
                            .texOffs(10, 44).addBox(-0.6f, -0.5f, -0.6f, 1.2f, FRONT_LOWER + 0.5f, 1.2f)
                            // The knobbly knee, and the two dew claws at the back above the hoof.
                            .texOffs(10, 44).addBox(-0.8f, -0.8f, -0.8f, 1.6f, 1.6f, 1.6f)
                            .texOffs(16, 44).addBox(-0.55f, FRONT_LOWER - 1.6f, 0.45f, 0.4f, 0.6f, 0.5f)
                            .texOffs(16, 44).addBox(0.15f, FRONT_LOWER - 1.6f, 0.45f, 0.4f, 0.6f, 0.5f),
                    PartPose.offset(0.0f, FRONT_UPPER, 0.0f));
            fl.addOrReplaceChild("hoof", CubeListBuilder.create()
                            .texOffs(16, 44).addBox(-0.7f, 0.0f, -0.9f, 1.4f, 1.3f, 1.6f),
                    PartPose.offset(0.0f, FRONT_LOWER, 0.0f));

            PartDefinition ru = body.addOrReplaceChild("rear" + s, CubeListBuilder.create()
                            .texOffs(24, 44).addBox(-1.4f, -1.0f, -1.6f, 2.8f, THIGH + 1.0f, 3.2f),
                    PartPose.offset(side * 2.3f, 2.5f, 7.0f));
            PartDefinition rl = ru.addOrReplaceChild("lower", CubeListBuilder.create()
                            .texOffs(38, 44).addBox(-0.6f, -0.5f, -0.6f, 1.2f, CANNON + 0.5f, 1.2f)
                            // The point of the hock sticking out behind, and the dew claws.
                            .texOffs(38, 44).addBox(-0.75f, -1.0f, -0.4f, 1.5f, 1.8f, 1.5f)
                            .texOffs(16, 44).addBox(-0.55f, CANNON - 1.6f, 0.45f, 0.4f, 0.6f, 0.5f)
                            .texOffs(16, 44).addBox(0.15f, CANNON - 1.6f, 0.45f, 0.4f, 0.6f, 0.5f),
                    PartPose.offset(0.0f, THIGH, 0.0f));
            rl.addOrReplaceChild("hoof", CubeListBuilder.create()
                            .texOffs(16, 44).addBox(-0.7f, 0.0f, -0.9f, 1.4f, 1.3f, 1.6f),
                    PartPose.offset(0.0f, CANNON, 0.0f));
        }

        // The scar: a black core strip between two raw lips. UV core (0,64), lips (0,84).
        PartDefinition[] owners = {body, neck, head, body.getChild("tail")};
        for (int i = 0; i < SCAR.length; i++) {
            float[] s = SCAR[i];
            PartDefinition owner = owners[(int) s[0]];
            boolean alongZ = s[5] == 0;
            float len = s[4];
            CubeListBuilder core = CubeListBuilder.create().texOffs(0, 64);
            CubeListBuilder lips = CubeListBuilder.create().texOffs(0, 84);
            if (alongZ) {
                core.addBox(-0.35f, -0.25f, -len / 2.0f, 0.7f, 0.35f, len);
                lips.addBox(-0.25f, -0.2f, -len / 2.0f, 0.5f, 0.3f, len);
            } else {
                core.addBox(-0.35f, -len / 2.0f, -0.1f, 0.7f, len, 0.35f);
                lips.addBox(-0.25f, -len / 2.0f, -0.05f, 0.5f, len, 0.3f);
            }
            owner.addOrReplaceChild("scar" + i, core, PartPose.offset(s[1], s[2], s[3]));
            owner.addOrReplaceChild("scar" + i + "_lip_l", lips, PartPose.offset(s[1] + 0.55f, s[2], s[3]));
            owner.addOrReplaceChild("scar" + i + "_lip_r", lips, PartPose.offset(s[1] - 0.55f, s[2], s[3]));
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    // ================================================================== animation

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(L227Doe entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float partial = ageInTicks - entity.tickCount;
        animate(ageInTicks + (entity.getId() & 127), limbSwing, limbSwingAmount, entity.graze(partial),
                entity.alert(partial), entity.scar(partial), netHeadYaw, headPitch);
    }

    /**
     * Poses the model from plain numbers (also used by the art tools' contact sheets).
     *
     * @param graze 0 head up, 1 head down to the ground, nibbling
     * @param alert 0 relaxed, 1 frozen, staring at you, ears forward, tail up
     * @param open  how far the scar has opened (0 closed, 1 gaping)
     */
    public void animate(float t, float walk, float walkAmount, float graze, float alert, float open,
                        float headYawDeg, float headPitchDeg) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float amount = Mth.clamp(walkAmount * 1.6f, 0.0f, 1.0f);
        float relaxed = 1.0f - alert;

        // --- Body: a light bob as she walks; front dips a little to graze ----------------------
        float cycles = walk * 0.16f;
        float phase = cycles - Mth.floor(cycles);
        body.y -= Math.abs(Mth.sin(Mth.TWO_PI * phase)) * 0.4f * amount;
        body.xRot += graze * 0.08f;
        body.y += graze * 0.6f;
        body.y += Mth.sin(t * 0.1f) * 0.08f; // breathing

        // --- Legs: diagonal pairs (front right with rear left) ---------------------------------
        float[] offset = {0.0f, 0.5f, 0.5f, 0.0f};
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            float p = phase + offset[i];
            p -= Mth.floor(p);
            float swing = Mth.sin(Mth.TWO_PI * p) * 0.5f * amount;
            float lift = Math.max(0.0f, Mth.cos(Mth.TWO_PI * p)) * amount;
            if (front) {
                // Graceful high step: the knee folds back as the leg comes forward.
                upper[i].xRot = swing - body.xRot - graze * 0.06f;
                lower[i].xRot = lift * 0.9f;
                hoof[i].xRot = -(upper[i].xRot + lower[i].xRot + body.xRot) + lift * 0.3f;
            } else {
                // Hind legs: thigh forward, the hock bent back.
                upper[i].xRot = -0.32f + swing - body.xRot;
                lower[i].xRot = 0.62f - lift * 0.5f;
                hoof[i].xRot = -(upper[i].xRot + lower[i].xRot + body.xRot);
            }
        }

        // --- Neck and head ------------------------------------------------------------------------
        float yaw = headYawDeg * Mth.DEG_TO_RAD;
        float pitch = headPitchDeg * Mth.DEG_TO_RAD;
        // Grazing: the long neck sweeps down to the floor, the head turned to nibble.
        neck.xRot += graze * NECK_GRAZE - body.xRot;
        head.xRot += graze * 0.35f;
        neck.yRot += graze * Mth.sin(t * 0.03f) * 0.25f;
        // Alert: neck straight up, head level, turned on you. A slight, wrong tilt of the head.
        neck.xRot -= alert * 0.22f;
        neck.yRot += yaw * 0.5f * (1.0f - graze);
        head.yRot += yaw * 0.45f * (1.0f - graze);
        head.xRot += pitch * 0.5f * (1.0f - graze);
        head.zRot += alert * 0.18f + open * 0.12f;
        // Chewing while grazing.
        jaw.xRot = graze * Math.max(0.0f, Mth.sin(t * 0.6f)) * 0.22f;

        // --- Ears and tail -----------------------------------------------------------------------
        for (int k = 0; k < 2; k++) {
            int side = k == 0 ? -1 : 1;
            // Relaxed they droop out sideways and flick now and then; alert they swivel forward, up.
            float flick = Mth.sin(t * 0.07f + k * 2.3f) > 0.96f ? Mth.sin(t * 1.7f) * 0.4f : 0.0f;
            ears[k].zRot += side * (-0.35f * alert) + flick * relaxed;
            ears[k].yRot += side * -0.5f * alert;
        }
        // Tail: flicks while relaxed; flagged up behind her when alert (the white underside shows).
        tail.xRot += Mth.sin(t * 0.21f) * 0.12f * relaxed + alert * 1.9f;

        // --- The scar ------------------------------------------------------------------------------
        float width = 1.0f + open * 2.6f;
        for (int i = 0; i < SCAR.length; i++) {
            scar[i].xScale = width;
            // The lips part as it opens (and quiver very slightly when it is open).
            float part = 0.55f + 0.35f * (width - 1.0f) + Mth.sin(t * 0.9f + i) * 0.03f * open;
            lipL[i].x = SCAR[i][1] + part;
            lipR[i].x = SCAR[i][1] - part;
        }
    }
}
