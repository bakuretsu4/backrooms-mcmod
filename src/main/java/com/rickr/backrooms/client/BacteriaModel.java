package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.Bacteria;
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
import net.minecraft.world.entity.Entity;

/**
 * Kane Pixels' Bacteria, built in code instead of Blockbench.
 *
 * <p>The shape: a flat, lumpy, blob-like crown of a head sitting right on a straight shoulder bar;
 * a long thin spine; arms so long their three-pronged claws rest on the floor; short splayed legs.
 * Every "bone" is 2-3 thin wires tangled around each other, crossing at uneven spots, with random
 * kinks and the odd loose loop, like a messy bundle of cable. In the torso, near-black organs
 * hang in the wires: sinew fanning in from the shoulders, two ribbed sacs that breathe, a heart
 * that beats faster once it has seen you, a waist of sinew strands with a stomach knot, and a
 * lumpy pelvis ({@link #organs}). All of it comes from fixed random seeds, so every client sees
 * the same creature.
 *
 * <pre>
 *            ▄█▀▄▀█▄         head: lumpy base, two lobes bulging up and out, a knob in the middle
 *        ════════╤════════   shoulder bar (2 braided wires)
 *        ║     \\ | //    ║   sinew fanning in; spine and arms: 3 tangled wires each
 *        ║     (≡)♥(≡)   ║   ribbed sacs and the heart
 *        ║      |@|      ║   waist of sinew with the stomach knot, pelvis knot below
 *        ║      ╱ ╲      ║   short legs, feet apart
 *       /|\    ╱   ╲    /|\  claws on the floor
 * </pre>
 *
 * <p>Size: standing up straight it is 54 px (about 3.4 blocks) tall, but its hitbox is only 2.9
 * blocks so it fits through the 3-block doorways. Under any low ceiling (doorways, all of Level 2)
 * the model stoops: it bends forward at the hips and bends its knees until the head clears the
 * ceiling. {@link Bacteria#headroom} tells it how much space there is.
 *
 * <p>Model space: units are pixels (1/16 block), +Y points down, the front faces -Z, the ground is
 * at y = 24.
 */
public class BacteriaModel extends HierarchicalModel<Bacteria> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Backrooms.id("bacteria"), "main");

    private static final float WIRE = 0.55f;

    // Proportions (pixels), measured from the reference model.
    private static final float HIP_HEIGHT = 21.6f;      // hips above the ground
    private static final float SPINE = 23.2f;           // hips to shoulder bar
    private static final float NECK_AND_HEAD = 9.4f;    // shoulder bar to the top of the head
    private static final float SHOULDER_HALF = 10.3f;   // shoulder bar half width
    private static final float UPPER_ARM = 21.0f;
    private static final float FOREARM = 20.0f;
    private static final float ARM_REACH = 45.5f;       // shoulder to claw tips, arm hanging straight
    private static final float THIGH = 11.0f;
    private static final float SHIN = 10.6f;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart spine;
    private final ModelPart chest;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart rightForearm;
    private final ModelPart leftArm;
    private final ModelPart leftForearm;
    private final ModelPart rightLeg;
    private final ModelPart rightShin;
    private final ModelPart leftLeg;
    private final ModelPart leftShin;
    private final ModelPart heart;
    private final ModelPart lungRight;
    private final ModelPart lungLeft;
    private final ModelPart stomach;
    // Index 0 = right arm, 1 = left arm (made once here, so setupAnim never has to).
    private final ModelPart[] arms;
    private final ModelPart[] forearms;
    private final ModelPart[] hands;
    private final ModelPart[][] fingers = new ModelPart[2][3];
    /** Every part setupAnim moves; only these need putting back to rest each frame. */
    private final ModelPart[] animated;

    public BacteriaModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.spine = body.getChild("spine");
        this.chest = spine.getChild("chest");
        this.neck = chest.getChild("neck");
        this.head = neck.getChild("head");
        this.rightArm = chest.getChild("right_arm");
        this.rightForearm = rightArm.getChild("forearm");
        this.leftArm = chest.getChild("left_arm");
        this.leftForearm = leftArm.getChild("forearm");
        this.rightLeg = body.getChild("right_leg");
        this.rightShin = rightLeg.getChild("shin");
        this.leftLeg = body.getChild("left_leg");
        this.leftShin = leftLeg.getChild("shin");
        this.heart = spine.getChild("heart");
        this.lungRight = spine.getChild("lung_right");
        this.lungLeft = spine.getChild("lung_left");
        this.stomach = spine.getChild("stomach");
        this.arms = new ModelPart[] {rightArm, leftArm};
        this.forearms = new ModelPart[] {rightForearm, leftForearm};
        this.hands = new ModelPart[] {rightForearm.getChild("hand"), leftForearm.getChild("hand")};
        for (int i = 0; i < 2; i++) {
            for (int f = 0; f < 3; f++) {
                fingers[i][f] = hands[i].getChild("finger" + f);
            }
        }
        this.animated = new ModelPart[] {body, spine, chest, neck, head, rightArm, rightForearm, leftArm, leftForearm,
                hands[0], hands[1], fingers[0][0], fingers[0][1], fingers[0][2], fingers[1][0], fingers[1][1], fingers[1][2],
                rightLeg, rightShin, leftLeg, leftShin, heart, lungRight, lungLeft, stomach};
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        RandomSource rng = RandomSource.create(0xBAC7E81AL);

        // The body part sits at the hips; everything above hangs off the spine.
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0, 24 - HIP_HEIGHT, 0));
        PartDefinition spine = body.addOrReplaceChild("spine", CubeListBuilder.create(), PartPose.ZERO);
        braid(spine, "spine", rng, 0, 0, 0, 0, -SPINE, 0, 3, 1.6f, 11.0f, WIRE * 1.1f);
        // A couple of loose wires hanging off the pelvis, like frayed cable.
        wire(spine, "loose_a", 0.5f, -1.0f, 0.3f, 2.5f, 5.0f, -0.5f, WIRE * 0.7f);
        wire(spine, "loose_b", -0.5f, -0.5f, 0.0f, -1.8f, 4.0f, 1.0f, WIRE * 0.7f);
        // Organs get their own random numbers, so adding them didn't reshuffle the wires.
        organs(spine, RandomSource.create(0x0B6A115L));

        // Chest = the shoulder bar at the top of the spine. Arms and neck hang off it.
        PartDefinition chest = spine.addOrReplaceChild("chest", CubeListBuilder.create(), PartPose.offset(0, -SPINE, 0));
        braid(chest, "bar", rng, -SHOULDER_HALF, 0, 0, SHOULDER_HALF, 0, 0, 2, 0.6f, 7.0f, WIRE);

        PartDefinition neck = chest.addOrReplaceChild("neck", CubeListBuilder.create(), PartPose.ZERO);
        braid(neck, "neck", rng, 0, 0, 0, 0, -2.6f, -0.4f, 2, 0.4f, 3.0f, WIRE);
        head(neck, rng);

        arm(chest, "right_arm", rng, -1);
        arm(chest, "left_arm", rng, 1);

        leg(body, "right_leg", rng, -1);
        leg(body, "left_leg", rng, 1);

        // Flesh: sinew and lumps that fill the wire skeleton out, like the pencil sketch. Again
        // its own random numbers, so the wires you already approved stay exactly the same.
        RandomSource fill = RandomSource.create(0xF1E5BL);
        fleshUpper(chest, neck.getChild("head"), neck, fill);
        for (int side = -1; side <= 1; side += 2) {
            PartDefinition arm = chest.getChild(side < 0 ? "right_arm" : "left_arm");
            fleshArm(arm, arm.getChild("forearm"), fill, side);
            PartDefinition leg = body.getChild(side < 0 ? "right_leg" : "left_leg");
            fleshLeg(leg, leg.getChild("shin"), fill, side);
        }

        return LayerDefinition.create(mesh, 64, 64);
    }

    /**
     * The head: a soft, lumpy blob wider than it is tall, shaped like a squashed crown. It is
     * made of overlapping rounded lumps ({@link #blob}) of different sizes, each turned a little
     * at random, so the outline is bumpy instead of straight-edged: a rounded middle with a
     * sagging underside, a lobe bulging up and out on each side, and a round knob on top.
     */
    private static void head(PartDefinition neck, RandomSource rng) {
        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0, -2.6f, -0.4f));
        blob(head, "middle", 0, 0, rng, 0.0f, -1.6f, 0.0f, 7.0f, 3.0f, 6.0f, 0.0f, 0.2f, 0.0f);
        blob(head, "belly", 0, 0, rng, 0.4f, -0.3f, 0.3f, 5.0f, 2.0f, 4.5f, 0.0f, -0.3f, 0.0f);
        for (int side = -1; side <= 1; side += 2) {
            String s = side < 0 ? "right" : "left";
            // zRot tilts each lobe so its outer end rises: the crown points.
            blob(head, "lobe_" + s, 0, 0, rng, side * 4.6f, -2.5f, 0.1f, 4.6f, 3.2f, 5.0f, 0.0f, side * 0.25f, -side * 0.5f);
            blob(head, "lobe_" + s + "_tip", 0, 0, rng, side * 6.8f, -4.0f, 0.3f, 3.0f, 2.6f, 3.6f, 0.2f, -side * 0.3f, -side * 0.9f);
        }
        blob(head, "knob", 0, 0, rng, 0.0f, -4.2f, -0.3f, 3.2f, 3.0f, 3.2f, 0.0f, 0.4f, 0.0f);
    }

    /**
     * The organs in the middle of the body, after the pencil sketch: sinew fanning down from the
     * shoulders into the chest, two ribbed sacs at the bottom of the chest, a narrow waist of
     * vertical sinew strands, and a lumpy knot at the pelvis with a bead hanging off it. Spine
     * space: hips at y = 0, shoulder bar at y = -23.2, front is -Z.
     * <pre>
     *      \\  \ /  //       sinew fanning in from the shoulders
     *       (≡) ♥ (≡)        two ribbed sacs (they breathe) with the heart between them (it beats)
     *         |||||          waist: bundle of sinew strands
     *         ||@||          stomach knot in the bundle (it churns)
     *         (###)o         pelvis knot, with a bead hanging on a strand
     * </pre>
     * Everything uses the flesh part of bacteria.png (y 40-63), which is the same near-black as
     * the wires, so the body reads as one colour; the ridges on the sacs are black wire.
     */
    private static void organs(PartDefinition spine, RandomSource rng) {
        // Sinew: strands from along the shoulder bar, converging on the breastbone.
        float[] fromX = {-7.0f, -3.8f, 3.6f, 6.8f};
        for (int i = 0; i < fromX.length; i++) {
            float sx = fromX[i];
            float ex = Math.signum(sx) * 0.9f;
            float mx = (sx + ex) / 2.0f + (rng.nextFloat() - 0.5f) * 1.2f;
            float my = -19.5f + (rng.nextFloat() - 0.5f) * 1.0f;
            tube(spine, "sinew" + i + "_a", sx, -22.6f, -0.3f, mx, my, -1.4f, 0.8f);
            tube(spine, "sinew" + i + "_b", mx, my, -1.4f, ex, -15.5f, -1.9f, 0.7f);
        }

        // Ribbed sacs at the bottom of the chest, one each side. Their ridges are children of the
        // sac, so they swell with it when it breathes.
        for (int side = -1; side <= 1; side += 2) {
            String name = side < 0 ? "lung_right" : "lung_left";
            PartDefinition sac = blob(spine, name, 16, side < 0 ? 40 : 48, rng,
                    side * 2.6f, -14.4f, -1.3f, 3.2f, 3.6f, 3.0f, 0.15f, side * 0.25f, side * -0.15f);
            ridges(sac, name, rng, side);
        }
        // The heart, tucked between the sacs, a little in front: small, but you can see it beat.
        blob(spine, "heart", 0, 40, rng, 0.3f, -14.0f, -2.5f, 2.2f, 2.6f, 2.0f, 0.3f, 0.4f, -0.3f);
        tube(spine, "vessel_right", 0.0f, -15.0f, -2.5f, -1.8f, -15.6f, -1.6f, 0.5f);
        tube(spine, "vessel_left", 0.6f, -15.1f, -2.6f, 1.9f, -15.4f, -1.5f, 0.5f);

        // Waist: a bundle of sinew strands running from under the sacs down to the pelvis, each
        // wandering a little, crossing the spine wires.
        for (int i = 0; i < 5; i++) {
            float x0 = -1.8f + i * 0.9f + (rng.nextFloat() - 0.5f) * 0.5f;
            float z0 = -0.4f - rng.nextFloat() * 1.4f;
            float px = x0 * 1.4f, py = -12.6f, pz = z0 - 0.4f;
            for (int k = 1; k <= 3; k++) {
                float y = -12.6f + k * 3.3f;
                // Narrowest in the middle of the waist, wider again at the pelvis.
                float spread = k == 3 ? 1.3f : 0.8f;
                float x = x0 * spread + (rng.nextFloat() - 0.5f) * 0.7f;
                float z = z0 + (rng.nextFloat() - 0.5f) * 0.6f;
                tube(spine, "waist" + i + "_" + k, px, py, pz, x, y, z, 0.6f + rng.nextFloat() * 0.35f);
                px = x;
                py = y;
                pz = z;
            }
        }
        // Stomach: a knot tangled into the bundle.
        blob(spine, "stomach", 32, 40, rng, -0.3f, -7.6f, -1.5f, 2.6f, 3.0f, 2.4f, 0.1f, -0.3f, 0.25f);

        // Pelvis: a lumpy knot, and a bead hanging from it on a short strand (left hip).
        blob(spine, "pelvis", 32, 48, rng, 0.2f, -1.6f, -0.8f, 3.4f, 2.2f, 2.6f, 0.0f, 0.3f, 0.1f);
        blob(spine, "pelvis_lump", 32, 48, rng, -1.2f, -2.4f, -1.4f, 2.0f, 1.6f, 1.8f, 0.4f, 0.0f, -0.3f);
        tube(spine, "bead_strand", 1.6f, -1.0f, -0.9f, 2.4f, 1.6f, -1.3f, 0.5f);
        blob(spine, "bead", 0, 52, rng, 2.5f, 2.2f, -1.3f, 1.3f, 1.4f, 1.3f, 0.0f, 0.0f, 0.0f);
    }

    /**
     * Three black ridges wrapped around the front and outer side of a sac (local space, centred),
     * like the striped ribbing in the sketch. The ring gets smaller toward the top and bottom so
     * it follows the round shape; each ring is a little crooked.
     */
    private static void ridges(PartDefinition sac, String name, RandomSource rng, int side) {
        float[] ys = {-1.1f, -0.1f, 0.9f};
        for (int r = 0; r < ys.length; r++) {
            float y = ys[r];
            float k = Mth.sqrt(Math.max(0.2f, 1.0f - (y / 2.0f) * (y / 2.0f)));
            float px = 0, py = 0, pz = 0;
            for (int i = 0; i <= 4; i++) {
                // From the inner side round the front (-Z) to the outer side.
                float a = (-1.6f + i * 0.8f) * -side;
                float x = Mth.sin(a) * 1.75f * k;
                float z = -Mth.cos(a) * 1.6f * k;
                float yy = y + (rng.nextFloat() - 0.5f) * 0.3f;
                if (i > 0) {
                    wire(sac, name + "_ridge" + r + "_" + i, px, py, pz, x, yy, z, 0.4f);
                }
                px = x;
                py = yy;
                pz = z;
            }
        }
    }

    /** A fleshy tube from A to B (sinew, vessels): like {@link #wire}, but with the organ texture. */
    private static void tube(PartDefinition parent, String name, float ax, float ay, float az, float bx, float by, float bz, float thickness) {
        segment(parent, name, 48, 40, ax, ay, az, bx, by, bz, thickness);
    }

    /**
     * A rounded lump about sx x sy x sz centred on (cx, cy, cz), textured from (texU, texV).
     * Three overlapping boxes, each full size along one axis and shorter along the other two,
     * fill the middle but leave every corner cut off, so the lump looks rounded from any side.
     * Each box also gets a small random tilt, which makes the surface bumpy. Returns the lump, so
     * details can be attached to it.
     */
    private static PartDefinition blob(PartDefinition parent, String name, int texU, int texV, RandomSource rng,
                                       float cx, float cy, float cz, float sx, float sy, float sz, float rx, float ry, float rz) {
        PartDefinition lump = parent.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offsetAndRotation(cx, cy, cz, rx, ry, rz));
        float[][] boxes = {{sx, sy * 0.7f, sz * 0.7f}, {sx * 0.7f, sy, sz * 0.7f}, {sx * 0.7f, sy * 0.7f, sz}};
        for (int i = 0; i < boxes.length; i++) {
            float bx = boxes[i][0], by = boxes[i][1], bz = boxes[i][2];
            lump.addOrReplaceChild("part" + i,
                    CubeListBuilder.create().texOffs(texU, texV).addBox(-bx / 2.0f, -by / 2.0f, -bz / 2.0f, bx, by, bz),
                    PartPose.rotation((rng.nextFloat() - 0.5f) * 0.3f, (rng.nextFloat() - 0.5f) * 0.3f, (rng.nextFloat() - 0.5f) * 0.3f));
        }
        return lump;
    }

    /** {@code side}: -1 = right (the -X side, as in vanilla models), +1 = left. */
    private static void arm(PartDefinition chest, String name, RandomSource rng, int side) {
        PartDefinition arm = chest.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offset(side * SHOULDER_HALF, 0, 0));
        braid(arm, "upper", rng, 0, 0, 0, side * 1.5f, UPPER_ARM, 0, 3, 1.4f, 14.0f, WIRE);
        PartDefinition forearm = arm.addOrReplaceChild("forearm", CubeListBuilder.create(), PartPose.offset(side * 1.5f, UPPER_ARM, 0));
        braid(forearm, "lower", rng, 0, 0, 0, side * 1.5f, FOREARM, -0.5f, 3, 1.3f, 14.0f, WIRE);
        // Three claw fingers, splayed like a rake, each with a slight bend in the middle. They
        // hang off a "hand" at the wrist, one "finger" part each, so the animation can curl the
        // hand and spread the claws. The points are measured from the wrist (wx, FOREARM, -0.5).
        float wx = side * 1.5f;
        PartDefinition hand = forearm.addOrReplaceChild("hand", CubeListBuilder.create(), PartPose.offset(wx, FOREARM, -0.5f));
        for (int f = -1; f <= 1; f++) {
            float mx = f * 1.1f + side * 0.4f;
            float tx = f * 2.4f + side * 0.9f;
            float tz = -f * f * 0.8f + f * 0.6f;
            PartDefinition finger = hand.addOrReplaceChild("finger" + (f + 1), CubeListBuilder.create(), PartPose.ZERO);
            wire(finger, "claw_a", 0.0f, 0.0f, 0.0f, mx, 2.6f, 0.2f + f * 0.3f, WIRE * 0.8f);
            wire(finger, "claw_b", mx, 2.6f, 0.2f + f * 0.3f, tx, 4.6f, tz, WIRE * 0.7f);
        }
    }

    private static void leg(PartDefinition body, String name, RandomSource rng, int side) {
        PartDefinition leg = body.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offset(side * 1.5f, 0, 0));
        braid(leg, "thigh", rng, 0, 0, 0, side * 2.2f, THIGH, -0.5f, 3, 1.0f, 11.0f, WIRE);
        PartDefinition shin = leg.addOrReplaceChild("shin", CubeListBuilder.create(), PartPose.offset(side * 2.2f, THIGH, -0.5f));
        braid(shin, "lower", rng, 0, 0, 0, side * 2.3f, SHIN, 0.5f, 2, 0.9f, 11.0f, WIRE);
        // A thin pointed foot: one wire forward, one short spur back.
        wire(shin, "toe", side * 2.3f, SHIN, 0.5f, side * 2.5f, SHIN, -2.5f, WIRE * 0.8f);
        wire(shin, "spur", side * 2.3f, SHIN, 0.5f, side * 2.2f, SHIN - 0.3f, 1.8f, WIRE * 0.7f);
    }

    // ------------------------------------------------------------------ flesh (after the sketch)
    // Every part here is named "flesh_...". Muscle and sinew use the flesh texture, which is the
    // same near-black as the wires. Nothing here is animated on its own: it hangs off the
    // existing bones and moves with them.

    /**
     * Shoulders and neck: thick sinew from the base of the neck out to each shoulder (the broad,
     * sloping shoulders in the sketch), a lump of muscle under the neck, and a fuller neck. The
     * head gets a few extra flat, ragged lobes so it looks more like a frilly mass of flesh.
     */
    private static void fleshUpper(PartDefinition chest, PartDefinition head, PartDefinition neck, RandomSource rng) {
        for (int side = -1; side <= 1; side += 2) {
            sinew(chest, "flesh_collar" + side, rng, side * 0.5f, -1.4f, 0.0f, side * 9.6f, 0.4f, 0.1f, 4, 0.5f, 1.5f, 1.0f);
            sinew(neck, "flesh_neck" + side, rng, side * 0.6f, 0.2f, 0.1f, side * 0.5f, -2.6f, -0.4f, 2, 0.2f, 1.1f, 0.9f);
        }
        blob(chest, "flesh_chest", 0, 44, rng, 0.0f, 1.4f, -0.7f, 3.8f, 2.4f, 2.6f, 0.2f, 0.0f, 0.0f);
        blob(head, "flesh_frill_a", 0, 0, rng, -2.8f, -5.0f, 1.4f, 2.6f, 1.1f, 2.0f, 0.4f, 0.3f, 0.5f);
        blob(head, "flesh_frill_b", 0, 0, rng, 2.6f, -4.8f, -1.7f, 2.2f, 1.0f, 2.4f, -0.5f, -0.2f, -0.4f);
        blob(head, "flesh_frill_c", 0, 0, rng, -6.2f, -1.6f, -1.8f, 2.0f, 1.0f, 2.6f, 0.3f, 0.6f, 0.9f);
        blob(head, "flesh_frill_d", 0, 0, rng, 6.0f, -1.4f, 1.9f, 2.4f, 1.0f, 2.0f, -0.4f, -0.5f, -0.8f);
    }

    /**
     * Arm, after the sketch: a heavy, tapering mass of muscle on the upper arm (thick at the
     * shoulder, thin at the elbow), a knobbly elbow, a forearm of thin sinew strands with a vein
     * winding around them, a knot at the wrist, and bony claws: a bead at each knuckle and a short
     * thumb pointing back.
     */
    private static void fleshArm(PartDefinition arm, PartDefinition forearm, RandomSource rng, int side) {
        float ex = side * 1.5f;
        blob(arm, "flesh_deltoid", 0, 44, rng, side * 0.5f, 1.4f, 0.0f, 3.4f, 3.0f, 3.0f, 0.1f, 0.0f, -side * 0.25f);
        blob(arm, "flesh_upper", 0, 44, rng, side * 0.8f, 6.0f, -0.3f, 2.3f, 6.5f, 2.1f, 0.08f, 0.2f, -side * 0.08f);
        blob(arm, "flesh_upper_taper", 0, 44, rng, side * 1.2f, 11.8f, 0.1f, 1.4f, 5.0f, 1.3f, -0.05f, 0.0f, side * 0.04f);
        for (int i = 0; i < 3; i++) {
            float a = Mth.TWO_PI * i / 3.0f;
            float ox = Mth.sin(a) * 0.9f;
            float oz = Mth.cos(a) * 0.8f;
            sinew(arm, "flesh_arm_sinew" + i, rng, side * 0.4f + ox, 0.6f, oz, ex + ox * 0.3f, UPPER_ARM, oz * 0.3f, 4, 0.7f, 1.0f, 0.55f);
        }
        blob(arm, "flesh_elbow", 0, 44, rng, ex, UPPER_ARM, 0.0f, 1.7f, 1.6f, 1.7f, 0.3f, 0.0f, 0.2f);

        float wx = side * 1.5f;
        for (int i = 0; i < 3; i++) {
            float a = Mth.TWO_PI * i / 3.0f + 0.5f;
            float ox = Mth.sin(a) * 0.7f;
            float oz = Mth.cos(a) * 0.6f;
            sinew(forearm, "flesh_fore_sinew" + i, rng, ox, 0.5f, oz, wx + ox * 0.4f, FOREARM - 0.5f, -0.5f + oz * 0.4f, 4, 0.5f, 0.6f, 0.45f);
        }
        sinew(forearm, "flesh_vein", rng, side * -0.8f, 1.0f, -0.6f, wx + side * 0.6f, FOREARM - 1.0f, -0.2f, 6, 0.8f, 0.4f, 0.4f);
        blob(forearm, "flesh_wrist", 0, 44, rng, wx, FOREARM, -0.5f, 1.5f, 1.3f, 1.5f, 0.0f, 0.3f, 0.0f);
        PartDefinition hand = forearm.getChild("hand");
        for (int f = -1; f <= 1; f++) {
            // Same knuckle points as the claws in arm(), measured from the wrist.
            float mx = f * 1.1f + side * 0.4f;
            blob(hand.getChild("finger" + (f + 1)), "flesh_knuckle", 0, 0, rng, mx, 2.6f, 0.2f + f * 0.3f, 0.9f, 0.9f, 0.9f, 0.0f, 0.0f, 0.0f);
        }
        wire(hand, "flesh_thumb_a", 0.0f, 0.0f, 0.0f, -side * 0.6f, 1.6f, 1.5f, WIRE * 0.8f);
        wire(hand, "flesh_thumb_b", -side * 0.6f, 1.6f, 1.5f, -side * 0.9f, 3.6f, 2.1f, WIRE * 0.7f);
    }

    /**
     * Leg, after the sketch: a muscle mass on the thigh, sinew strands down both segments, a
     * knobbly knee, and beads of flesh growing along the shin and at the ankle.
     */
    private static void fleshLeg(PartDefinition leg, PartDefinition shin, RandomSource rng, int side) {
        float kx = side * 2.2f;
        blob(leg, "flesh_thigh", 0, 44, rng, side * 0.9f, 4.3f, -0.2f, 2.4f, 7.0f, 2.2f, 0.05f, 0.1f, -side * 0.15f);
        for (int i = 0; i < 2; i++) {
            float ox = (i == 0 ? -0.6f : 0.6f);
            sinew(leg, "flesh_leg_sinew" + i, rng, ox, 0.5f, -0.3f, kx + ox * 0.4f, THIGH, -0.5f, 3, 0.5f, 0.9f, 0.6f);
        }
        blob(leg, "flesh_knee", 0, 44, rng, kx, THIGH, -0.5f, 2.1f, 1.9f, 2.1f, 0.3f, 0.2f, 0.1f);

        float ax = side * 2.3f;
        for (int i = 0; i < 2; i++) {
            float ox = (i == 0 ? -0.4f : 0.4f);
            sinew(shin, "flesh_shin_sinew" + i, rng, ox, 0.6f, 0.0f, ax + ox * 0.5f, SHIN - 0.6f, 0.5f, 3, 0.4f, 0.6f, 0.45f);
        }
        // Beads: one outside the shin about halfway down, one at the ankle; a third, smaller one
        // at a random height so the two legs don't match.
        blob(shin, "flesh_bead_mid", 0, 52, rng, side * 1.6f, 5.2f, 0.1f, 1.4f, 1.5f, 1.4f, 0.0f, 0.0f, 0.0f);
        blob(shin, "flesh_ankle", 0, 52, rng, ax, SHIN - 1.2f, 0.5f, 1.3f, 1.1f, 1.3f, 0.0f, 0.4f, 0.0f);
        float t = 0.2f + rng.nextFloat() * 0.6f;
        blob(shin, "flesh_bead_small", 0, 52, rng, ax * t - side * 0.6f, SHIN * t, -0.6f, 0.9f, 1.0f, 0.9f, 0.0f, 0.0f, 0.0f);
    }

    /**
     * A strand of sinew from A to B: {@code pieces} straight bits with the bends nudged sideways
     * at random by up to {@code wobble} px, getting thinner from {@code thickA} to {@code thickB}.
     */
    private static void sinew(PartDefinition parent, String name, RandomSource rng,
                              float ax, float ay, float az, float bx, float by, float bz,
                              int pieces, float wobble, float thickA, float thickB) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        float[] u = perpendicular(dx / length, dy / length, dz / length);
        float[] v = cross(dx / length, dy / length, dz / length, u);
        float px = ax, py = ay, pz = az;
        for (int p = 1; p <= pieces; p++) {
            float t = (float) p / pieces;
            float su = p == pieces ? 0.0f : (rng.nextFloat() - 0.5f) * 2.0f * wobble;
            float sv = p == pieces ? 0.0f : (rng.nextFloat() - 0.5f) * 2.0f * wobble;
            float nx = ax + dx * t + u[0] * su + v[0] * sv;
            float ny = ay + dy * t + u[1] * su + v[1] * sv;
            float nz = az + dz * t + u[2] * su + v[2] * sv;
            float thick = Mth.lerp((p - 0.5f) / pieces, thickA, thickB);
            tube(parent, name + "_" + p, px, py, pz, nx, ny, nz, thick);
            px = nx;
            py = ny;
            pz = nz;
        }
    }

    /**
     * Several wires running from A to B and twisting around each other, messily. Each wire
     * corkscrews around the straight A-B line, but every wire gets its own thickness of twist,
     * its own twisting speed and a slight bow, so they cross at uneven spots instead of a neat
     * repeating pattern. Every bend point is also nudged at random, and now and then one is
     * pushed out further (a kink). Near A and B the twist shrinks to nothing, so the wires still
     * meet at the joints. Sometimes a short loose loop of wire curls off the bone.
     */
    private static void braid(PartDefinition parent, String name, RandomSource rng,
                              float ax, float ay, float az, float bx, float by, float bz,
                              int strands, float radius, float period, float thickness) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        float[] dir = {dx / length, dy / length, dz / length};
        // Two directions at right angles to the bone, to twist around.
        float[] u = perpendicular(dir[0], dir[1], dir[2]);
        float[] v = cross(dir[0], dir[1], dir[2], u);
        int twists = Math.max(1, Math.round(length / period));
        // Pieces are about 2.5 px long, not tied to the twist, so the kinks fall unevenly.
        int pieces = Math.max(4, Math.round(length / 2.5f));
        for (int s = 0; s < strands; s++) {
            float r = radius * (0.65f + rng.nextFloat() * 0.7f);
            float rate = twists * (0.7f + rng.nextFloat() * 0.6f);
            float phase = Mth.TWO_PI * s / strands + (rng.nextFloat() - 0.5f) * 1.2f;
            float bow = (rng.nextFloat() - 0.5f) * 0.9f * radius;
            float px = ax, py = ay, pz = az;
            for (int p = 1; p <= pieces; p++) {
                float t = p == pieces ? 1.0f : (p + (rng.nextFloat() - 0.5f) * 0.6f) / pieces;
                float squeeze = Math.min(1.0f, 2.5f * Mth.sin(Mth.PI * t)); // 0 at both ends
                float kink = rng.nextFloat() < 0.12f ? 1.5f : 1.0f;
                float a = Mth.TWO_PI * rate * t + phase;
                float su = (Mth.sin(a) * r + (rng.nextFloat() - 0.5f) * r * 0.8f + bow * Mth.sin(Mth.PI * t)) * squeeze * kink;
                float sv = (Mth.cos(a) * r * 0.7f + (rng.nextFloat() - 0.5f) * r * 0.8f) * squeeze * kink;
                float along = (rng.nextFloat() - 0.5f) * 0.4f * squeeze;
                float nx = ax + dx * t + dir[0] * along + u[0] * su + v[0] * sv;
                float ny = ay + dy * t + dir[1] * along + u[1] * su + v[1] * sv;
                float nz = az + dz * t + dir[2] * along + u[2] * su + v[2] * sv;
                wire(parent, name + "_" + s + "_" + p, px, py, pz, nx, ny, nz, thickness * (0.7f + rng.nextFloat() * 0.6f));
                px = nx;
                py = ny;
                pz = nz;
            }
        }
        if (length > 8.0f && rng.nextFloat() < 0.4f) {
            // A loose loop: three short wires curling off the bone and back toward it.
            // Each step is (sideways, along the bone, front/back) from the point where it starts.
            float t = 0.3f + rng.nextFloat() * 0.4f;
            float side = rng.nextBoolean() ? 1.0f : -1.0f;
            float[][] steps = {{0, 0, 0}, {side * 1.6f, 0.4f, 0.8f}, {side * 2.4f, 2.2f, 0.2f}, {side * 1.0f, 3.2f, -0.6f}};
            float[][] points = new float[steps.length][3];
            for (int i = 0; i < steps.length; i++) {
                for (int k = 0; k < 3; k++) {
                    float start = k == 0 ? ax + dx * t : k == 1 ? ay + dy * t : az + dz * t;
                    points[i][k] = start + u[k] * steps[i][0] + dir[k] * steps[i][1] + v[k] * steps[i][2];
                }
            }
            for (int i = 1; i < points.length; i++) {
                float[] a = points[i - 1];
                float[] b = points[i];
                wire(parent, name + "_loop" + i, a[0], a[1], a[2], b[0], b[1], b[2], thickness * 0.7f);
            }
        }
    }

    /** Any unit vector at right angles to (x, y, z). */
    private static float[] perpendicular(float x, float y, float z) {
        // Cross with whichever world axis is least parallel to the bone.
        float[] c = Math.abs(z) < 0.9f ? cross(x, y, z, new float[] {0, 0, 1}) : cross(x, y, z, new float[] {1, 0, 0});
        float len = Mth.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]);
        return new float[] {c[0] / len, c[1] / len, c[2] / len};
    }

    private static float[] cross(float x, float y, float z, float[] o) {
        return new float[] {y * o[2] - z * o[1], z * o[0] - x * o[2], x * o[1] - y * o[0]};
    }

    /** One straight black wire from A to B. */
    private static void wire(PartDefinition parent, String name, float ax, float ay, float az, float bx, float by, float bz, float thickness) {
        segment(parent, name, 40, 24, ax, ay, az, bx, by, bz, thickness);
    }

    /**
     * One straight square rod from A to B, textured from (texU, texV). The cube is modelled along
     * +Y from its pivot, then rotated so +Y points at B. With ModelPart's Z-Y-X rotation order and
     * yRot = 0, the local +Y axis maps to (-cos(x)sin(z), cos(x)cos(z), sin(x)), which gives the
     * two angles below.
     */
    private static void segment(PartDefinition parent, String name, int texU, int texV,
                                float ax, float ay, float az, float bx, float by, float bz, float thickness) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-3f) {
            return;
        }
        float xRot = (float) Math.asin(Mth.clamp(dz / length, -1.0f, 1.0f));
        float zRot = (float) Mth.atan2(-dx / length, dy / length);
        float half = thickness / 2.0f;
        parent.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(texU, texV).addBox(-half, 0.0f, -half, thickness, length, thickness),
                PartPose.offsetAndRotation(ax, ay, az, xRot, 0.0f, zRot));
    }

    @Override
    public ModelPart root() {
        return root;
    }

    // ------------------------------------------------------------------ animation
    //
    // How it moves comes from a frame-by-frame study of the reference videos (Kane Pixels' found
    // footage and the scariest Minecraft versions). What makes it frightening there:
    //  - The motion itself is smooth, but its poses SNAP: the head holds a tilt, jumps to a new
    //    one in a tick or two, and holds again. (The old version ran the whole body as choppy
    //    stop-motion, which only looked laggy.)
    //  - Every limb runs on its own clock, and every Bacteria on its own seed (its entity id), so
    //    no two limbs and no two Bacteria ever move in step.
    //  - Dead still once it has seen you; then everything at once: the lunge, the arms thrown
    //    wide, the cage of arms around you, an arm whipping across your face, the head snapping in.
    // Angles are written in degrees (times DEG turns them into radians); times are in ticks (20 a
    // second). Model space: +Y is down, the front is -Z, +X is its left.
    //
    // setupAnim runs every frame for every Bacteria on screen, so it never creates objects: only
    // numbers, the fields below, and the arrays made once in the constructor.

    private static final float DEG = Mth.DEG_TO_RAD;
    /** The calm sway and breathing period, the same as the lungs' calm breathing. */
    private static final float BREATH_TICKS = 70.0f;
    /** The faint shiver of the spine, neck and forearms while calm: smooth, about 10 a second. */
    private static final float TREMOR = 1.2f * DEG;
    /** Test command with "loop": the feed and the peek replay this often (ticks). */
    private static final float FEED_LOOP_TICKS = Bacteria.FEED_TICKS + 18.0f;
    private static final float PEEK_LOOP_TICKS = Bacteria.PEEK_TICKS + 20.0f;
    /** Test command, SPOTTED with "loop": it twitches out of the freeze at this tick of each loop. */
    private static final float SPOTTED_TWITCH_AT = 34.0f;
    /** The head lean-in snaps in over 2 ticks, holds this long, then pulls back over 4. */
    private static final float LEAN_IN_HOLD = 24.0f;

    // What it is doing, as far as the model is concerned.
    private static final int CALM = 0;
    private static final int CHASE = 1;
    private static final int SEARCH = 2;
    private static final int SPOTTED = 3;
    private static final int CAGE = 4;
    private static final int FEED = 5;
    private static final int PEEK = 6;

    // The pose being built this frame (radians; pixels for the offsets). Each state below fills
    // these in, the one-shot moves (lunge, whip, lean-in, twitch) then bend them, and apply()
    // finally puts them on the model parts. Arm angles: index 0 = right arm, 1 = left arm.
    private float room;          // free height above the feet (px)
    private float squeeze;       // 0 = room to stand, 1 = squeezed under a 3-block ceiling
    private float drop;          // how far the hips sink because the knees are bent (px)
    private float hips;          // hip height (px)
    private float ceilingLean;   // the least forward lean that fits the head under the ceiling
    private float bob;           // extra up/down of the whole body (px, + is down)
    private float lean;          // spine forward lean
    private float sway;          // spine sideways lean (+ = to its left)
    private float thighR;
    private float thighL;
    private float shinR;
    private float shinL;
    private float neckX;
    private float neckZ;
    private float headPitchW;    // head pitch measured in the world, whatever the spine does
    private float headYaw;
    private float headRoll;
    private float headForward;   // px
    // How far forward the arm is raised, measured from hanging straight down in the world
    // (90 degrees = pointing straight ahead), and how far it swings out to its side from there.
    private final float[] raise = new float[2];
    private final float[] out = new float[2];
    private final float[] splay = new float[2];    // sideways tilt of a hanging arm
    private final float[] bend = new float[2];     // elbow: + folds the forearm down/back
    private final float[] foreOut = new float[2];  // forearm tilted out to its side
    private final float[] curl = new float[2];     // claws curled in (+) or flung open (-)
    private final float[] spread = new float[2];   // claws fanned apart

    @Override
    public void setupAnim(Bacteria entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        for (ModelPart part : animated) {
            part.resetPose();
        }
        float t = ageInTicks;
        float partial = ageInTicks - entity.tickCount;
        // Its own random clocks: the same id always gives the same moves, and no two ids match.
        int seed = entity.getId() * 0x632BE5AB + 0x1B873593;
        float yaw = netHeadYaw * DEG;
        float pitch = headPitch * DEG;
        // 0 = standing still, 1 = walking or running: fades the stride in and out.
        float moving = Mth.clamp((limbSwingAmount - 0.02f) * 8.0f, 0.0f, 1.0f);
        float gait = Mth.TWO_PI * entity.gaitPhase(partial);

        // Under a low ceiling (doorways, all of Level 2) it stoops instead of poking through.
        room = entity.headroom(partial) * 16.0f - 1.5f; // free height in px, minus a margin
        float standing = HIP_HEIGHT + SPINE + NECK_AND_HEAD;
        squeeze = Mth.clamp((standing - room) / (standing - 46.5f), 0.0f, 1.0f);

        // --- Which pose, and which one-shot moves are playing (their age in ticks, -1 = none) --
        int state;
        float age = 0.0f;
        int occurrence = 0;    // tells repeats of a move apart, so each one goes a different way
        float lunge = -1.0f;
        float whip = -1.0f;
        float leanIn = -1.0f;
        float twitch = -1.0f;
        int twitchId = 0;
        int peekSide = 1;
        float near = 0.0f;
        Bacteria.AnimPose forced = entity.forcedPose();
        if (forced != Bacteria.AnimPose.AUTO) {
            // Forced by /backrooms bacteria pose. It stands still, so walk and run play on the spot.
            float f = entity.forcedAge(partial);
            boolean loop = entity.forcedLoop();
            float loopTicks = Bacteria.POSE_LOOP_TICKS;
            int cycle = loop ? (int) (f / loopTicks) : 0;
            float inCycle = loop ? f % loopTicks : f;
            moving = 0.0f;
            switch (forced) {
                case WALK -> {
                    state = CALM;
                    moving = 1.0f;
                }
                case CHASE -> {
                    state = CHASE;
                    moving = 1.0f;
                }
                case SPOTTED -> {
                    state = SPOTTED;
                    age = inCycle;
                    occurrence = cycle;
                    if (loop) {
                        twitch = inCycle - SPOTTED_TWITCH_AT;
                        twitchId = cycle;
                    }
                }
                case LUNGE -> {
                    // Runs at you, then holds you: the cage once the arms have closed.
                    lunge = inCycle;
                    occurrence = cycle;
                    state = lunge < Bacteria.LUNGE_TICKS ? CHASE : CAGE;
                    moving = state == CHASE ? 1.0f : 0.0f;
                }
                case CAGE -> {
                    state = CAGE;
                    occurrence = cycle;
                    if (!loop) {
                        whip = f - 10.0f;    // one whip, then one lean-in
                        leanIn = f - 40.0f;
                    } else if (cycle % 2 == 0) {
                        whip = inCycle;      // whip, lean-in, whip, lean-in...
                    } else {
                        leanIn = inCycle;
                    }
                }
                case FEED -> {
                    state = FEED;
                    age = loop ? f % FEED_LOOP_TICKS : f;
                }
                case PEEK -> {
                    state = PEEK;
                    age = loop ? f % PEEK_LOOP_TICKS : f;
                    occurrence = loop ? (int) (f / PEEK_LOOP_TICKS) : 0;
                    peekSide = hash(seed + 26, occurrence) > 0.0f ? 1 : -1;
                }
                default -> state = CALM; // IDLE
            }
        } else {
            Bacteria.Mode mode = entity.mode();
            boolean calm = mode == Bacteria.Mode.WANDER || mode == Bacteria.Mode.DRIFT;
            Bacteria.Action action = entity.action();
            float since = entity.actionAge(partial);
            occurrence = entity.actionStamp();
            if (calm && action == Bacteria.Action.FEED && since < Bacteria.FEED_TICKS) {
                state = FEED;
                age = since;
            } else if (calm && (action == Bacteria.Action.PEEK_LEFT || action == Bacteria.Action.PEEK_RIGHT)
                    && since < Bacteria.PEEK_TICKS) {
                state = PEEK;
                age = since;
                peekSide = action == Bacteria.Action.PEEK_LEFT ? 1 : -1;
            } else if (mode == Bacteria.Mode.CHASE) {
                state = entity.isCaging() ? CAGE : CHASE;
                if (action == Bacteria.Action.LUNGE) {
                    lunge = since;
                } else if (action == Bacteria.Action.WHIP) {
                    whip = since;
                } else if (action == Bacteria.Action.LEAN_IN) {
                    leanIn = since;
                }
                // The last few blocks before it lunges, it hunches lower and opens its arms.
                Entity prey = entity.level().getEntity(entity.preyId());
                if (prey != null) {
                    near = Mth.clamp(((float) Bacteria.LUNGE_RANGE + 4.0f - entity.distanceTo(prey)) / 4.0f, 0.0f, 1.0f);
                }
            } else if (mode == Bacteria.Mode.SPOTTED) {
                state = SPOTTED;
                age = entity.modeAge(partial);
                occurrence = entity.modeStamp();
            } else if (mode == Bacteria.Mode.SEARCH) {
                state = SEARCH;
            } else {
                state = CALM;
            }
            if (entity.previousMode() == Bacteria.Mode.SPOTTED && mode != Bacteria.Mode.SPOTTED) {
                // The freeze breaks with one violent full-body twitch.
                twitch = entity.modeAge(partial);
                twitchId = entity.modeStamp();
            }
        }

        // --- Build the pose ------------------------------------------------------------------
        switch (state) {
            case CHASE -> chase(seed, t, moving, gait, yaw, pitch, near);
            case SEARCH -> search(seed, t, moving, gait);
            case SPOTTED -> spotted(seed, age, occurrence, yaw, pitch);
            case CAGE -> cage(seed, t, yaw, pitch);
            case FEED -> {
                calm(seed, t, 0.0f, gait, yaw, pitch);
                feed(seed, t, age);
            }
            case PEEK -> {
                calm(seed, t, 0.0f, gait, yaw, pitch);
                peek(t, age, peekSide);
            }
            default -> calm(seed, t, moving, gait, yaw, pitch);
        }
        if (lunge >= 0.0f) {
            lunge(lunge);
        }
        if (whip >= 0.0f) {
            whip(hash(seed + 27, occurrence) > 0.0f ? 0 : 1, whip);
        }
        if (leanIn >= 0.0f) {
            leanIn(seed, t, leanIn);
        }
        if (twitch >= 0.0f) {
            twitch(seed, twitchId, twitch);
        }
        apply();
        organs(entity, partial, ageInTicks);
    }

    // ------------------------------------------------------------------ the states

    /**
     * Nobody seen: standing about (m = 0) or strolling (m = 1). Never quite still: a slow sway and
     * breathing, a faint smooth tremor, a head that snaps to a new tilt every half second or so
     * and now and then jerks round to one side, a forearm that spasms. Knees always bent, claws
     * on the floor. Walking: a slow 27-tick stride, arms swinging on their own slow rhythm (not
     * with the legs), and now and then one arm flung out sideways.
     */
    private void calm(int seed, float t, float m, float gait, float yaw, float pitch) {
        stance(-10.0f * DEG, 25.0f * DEG);
        float breath = Mth.TWO_PI * t / BREATH_TICKS;
        lean = (12.0f + 5.5f * m) * DEG + 2.0f * DEG * Mth.sin(breath + 1.3f) + noise(seed + 1, t, 2.0f) * TREMOR;
        sway = 5.0f * DEG * Mth.sin(breath) * (1.0f - 0.5f * m) + noise(seed + 2, t, 2.0f) * TREMOR;
        // Stride: thighs swing 25 degrees each way; the knee folds up as the leg swings forward.
        float swing = Mth.sin(gait);
        float lift = Mth.cos(gait);
        thighR += 25.0f * DEG * swing * m;
        thighL -= 25.0f * DEG * swing * m;
        shinR += m * (-10.0f * DEG + 35.0f * DEG * Math.max(0.0f, -lift));
        shinL += m * (-10.0f * DEG + 35.0f * DEG * Math.max(0.0f, lift));
        bob = 0.25f * (1.0f - Mth.cos(2.0f * gait)) * m; // up to half a pixel, once per step
        neckX = noise(seed + 3, t, 2.0f) * TREMOR;
        neckZ = noise(seed + 4, t, 2.0f) * TREMOR;
        // A new head tilt every 8-14 ticks (6-10 while walking), reached in 1.5 ticks and held.
        headRoll = m < 0.5f
                ? snap(seed + 5, t, 11.0f, 1.5f, 1.5f, 15.0f, 30.0f)
                : snap(seed + 6, t, 8.0f, 1.0f, 1.5f, 15.0f, 25.0f);
        // Every 20-60 ticks the head jerks 25-60 degrees to one side in a tick, holds, comes back.
        headYaw = yaw + pulse(seed + 7, t, 40.0f, 10.0f, 1.0f, 6.0f, 5.0f, 25.0f, 60.0f);
        headPitchW = pitch * 0.5f;
        float reach = floorReach();
        float sweepTicks = 35.0f + 5.0f * hash(seed + 8, 0); // 30-40 ticks, different for each one
        for (int i = 0; i < 2; i++) {
            raise[i] = reach + 25.0f * DEG * m * Mth.sin(Mth.TWO_PI * (t / sweepTicks + 0.37f * i));
            splay[i] = (4.5f + 1.5f * hash(seed + 9 + i, 0)) * DEG;
            bend[i] = noise(seed + 11 + i, t, 2.0f) * TREMOR;
            curl[i] = (10.0f + 6.0f * noise(seed + 13 + i, t, 20.0f)) * DEG;
            spread[i] = 5.0f * DEG * noise(seed + 15 + i, t, 25.0f);
        }
        // Walking: about once every 2-4 s one arm flings out sideways (60-80 degrees in 3 ticks).
        float fling = pulse(seed + 17, t, 60.0f, 10.0f, 3.0f, 4.0f, 6.0f, 60.0f, 80.0f) * m;
        splay[fling > 0.0f ? 0 : 1] += Math.abs(fling);
        // Every 15-40 ticks one forearm spasms forward 10-25 degrees and holds for a moment.
        float spasm = pulse(seed + 18, t, 27.5f, 6.0f, 1.0f, 4.5f, 2.0f, 10.0f, 25.0f);
        bend[spasm > 0.0f ? 0 : 1] -= Math.abs(spasm);
    }

    /**
     * Running at you: an 11-tick stride, hunched 28-35 degrees (more in the last blocks before the
     * lunge, {@code near}), both arms reaching straight out at you, about level, and flailing on
     * their own: mostly big side-to-side sweeps, plus dips downward and a fast jitter. Never up:
     * the hands stay below about 25 degrees above level (checked with the joint simulation for
     * every lean and sweep, because a raised arm on a leaning body points higher than it looks).
     * The head aims at you and rolls.
     */
    private void chase(int seed, float t, float m, float gait, float yaw, float pitch, float near) {
        stance(-15.0f * DEG, 20.0f * DEG);
        float swing = Mth.sin(gait);
        float lift = Mth.cos(gait);
        thighR += 40.0f * DEG * swing * m;
        thighL -= 40.0f * DEG * swing * m;
        shinR += m * (-10.0f * DEG + 60.0f * DEG * Math.max(0.0f, -lift));
        shinL += m * (-10.0f * DEG + 60.0f * DEG * Math.max(0.0f, lift));
        bob = 0.5f * (1.0f - Mth.cos(2.0f * gait)) * m;
        lean = (31.5f + 8.5f * near) * DEG + noise(seed + 20, t, 6.0f) * 3.0f * DEG;
        floorReach();
        sway = noise(seed + 21, t, 7.0f) * 4.0f * DEG;
        headPitchW = pitch + noise(seed + 22, t, 8.0f) * 10.0f * DEG;
        headYaw = yaw;
        headRoll = noise(seed + 23, t, 6.0f) * 20.0f * DEG + snap(seed + 24, t, 8.0f, 1.5f, 1.0f, 20.0f, 30.0f);
        for (int i = 0; i < 2; i++) {
            int s = seed + 40 + 10 * i;
            float sweepTicks = 14.0f + 2.0f * hash(s, 0);     // 12-16 ticks
            float swingTicks = 11.0f + 2.0f * hash(s + 1, 0); // 9-13 ticks
            // Straight out at you, about level (aimed a little down if you are lower); the up/down
            // flail only ever dips the arm DOWN from there, and it is capped at level.
            float dip = 0.5f + 0.5f * noise(s + 2, t, sweepTicks); // 0..1
            raise[i] = Mth.clamp(95.0f * DEG - pitch * 0.6f - dip * 25.0f * DEG + noise(s + 3, t, 4.0f) * 8.0f * DEG,
                    50.0f * DEG, 90.0f * DEG);
            // The big flail is side to side; close to you the arms open wide, ready to close around you.
            out[i] = Math.min(75.0f * DEG, (15.0f + 40.0f * near) * DEG
                    + noise(s + 4, t, swingTicks) * 40.0f * DEG * (1.0f - 0.5f * near)
                    + noise(s + 5, t, 3.5f) * 12.0f * DEG);
            // Forearms droop a little (never cocked upward), with their own slow wobble.
            bend[i] = (12.0f + 6.0f * noise(s + 6, t, 6.0f)) * DEG;
            spread[i] = (12.0f + 6.0f * noise(s + 7, t, 5.0f)) * DEG;
            curl[i] = noise(s + 8, t, 4.0f) * 15.0f * DEG;
        }
    }

    /** Searching: hunched, the head jerking left and right down the halls, one arm reaching ahead. */
    private void search(int seed, float t, float m, float gait) {
        stance(-12.0f * DEG, 25.0f * DEG);
        float swing = Mth.sin(gait);
        float lift = Mth.cos(gait);
        thighR += 30.0f * DEG * swing * m;
        thighL -= 30.0f * DEG * swing * m;
        shinR += m * 45.0f * DEG * Math.max(0.0f, -lift);
        shinL += m * 45.0f * DEG * Math.max(0.0f, lift);
        lean = 34.0f * DEG + noise(seed + 30, t, 6.0f) * 2.0f * DEG;
        float reach = floorReach();
        sway = noise(seed + 31, t, 9.0f) * 3.0f * DEG;
        neckX = noise(seed + 3, t, 2.0f) * TREMOR;
        // Up to 70 degrees left or right, a new direction every 10-14 ticks, snapped in 1.5 ticks.
        headYaw = snap(seed + 32, t, 12.0f, 2.0f, 1.5f, 20.0f, 70.0f);
        headRoll = snap(seed + 33, t, 12.0f, 2.0f, 1.5f, 5.0f, 17.0f);
        headPitchW = 11.0f * DEG;
        for (int i = 0; i < 2; i++) {
            raise[i] = reach;
            splay[i] = (4.5f + 1.5f * hash(seed + 9 + i, 0)) * DEG;
            curl[i] = 15.0f * DEG;
        }
        // The right arm reaches ahead, dragging its claws along the floor; the fingers twitch.
        raise[0] += 20.0f * DEG;
        bend[0] = 14.0f * DEG + noise(seed + 34, t, 3.0f) * 11.0f * DEG;
        raise[1] += noise(seed + 35, t, 14.0f) * 10.0f * DEG;
    }

    /**
     * It has seen you. A head cock of 20-30 degrees in 2 ticks, then nothing: no sway, no tremor,
     * not a twitch. Only the head follows you, slowly (the server turns it 1.5 degrees a tick).
     */
    private void spotted(int seed, float age, int occurrence, float yaw, float pitch) {
        stance(-10.0f * DEG, 25.0f * DEG);
        lean = 4.0f * DEG;
        float reach = floorReach();
        headYaw = yaw;
        headPitchW = pitch * 0.5f;
        headRoll = spread(hash(seed + 25, occurrence), 20.0f, 30.0f) * DEG * smooth(age / 2.0f);
        for (int i = 0; i < 2; i++) {
            raise[i] = reach;
            splay[i] = (4.5f + 1.5f * hash(seed + 9 + i, 0)) * DEG;
            curl[i] = 10.0f * DEG;
        }
    }

    /**
     * Holding you (the reference video's "arm cage", key poses 05-07): it stands over you 1.5-2
     * blocks away, torso fairly upright (18 degrees), knees a little bent, so its head stays about
     * 1.25 blocks in front of you; only the lean-in brings it close. Each arm runs from its
     * shoulder out and down to an elbow at about your shoulder height, a little out to the side
     * and in front of you, and the forearm goes down to the floor, claws planted 0.5-0.9 blocks
     * to your left and right and about half a block in front of you. Seen by the player being
     * held, the two arms rise from the bottom corners of the screen up to its shoulders.
     * Every 6-12 ticks an arm lifts and re-plants its claws somewhere else along the floor.
     * All of this was computed with a joint-by-joint simulation of the model (including the lean
     * of the spine the arms hang from), from where the held player's camera is.
     */
    private void cage(int seed, float t, float yaw, float pitch) {
        stance(-20.0f * DEG, 40.0f * DEG);
        lean = 18.0f * DEG + noise(seed + 50, t, 8.0f) * 3.0f * DEG;
        floorReach();
        sway = noise(seed + 51, t, 9.0f) * 3.0f * DEG;
        neckX = 10.0f * DEG;
        headYaw = yaw;
        headPitchW = pitch;
        headRoll = noise(seed + 52, t, 2.0f) * 5.0f * DEG;
        for (int i = 0; i < 2; i++) {
            int s = seed + 60 + 10 * i;
            // Re-planting: the arm swings in or out (the claws slide 0.5-0.9 blocks to the side)
            // and lifts a little, with the elbow folding to match so the claws stay near the floor.
            float lift = snap(s, t, 9.0f, 1.5f, 2.0f, 2.0f, 6.0f);
            raise[i] = 25.0f * DEG + lift;
            bend[i] = 22.0f * DEG + 2.6f * lift + noise(s + 1, t, 5.0f) * 3.0f * DEG;
            out[i] = 25.0f * DEG + snap(s + 2, t, 9.0f, 1.5f, 2.0f, 4.0f, 10.0f);
            foreOut[i] = -30.0f * DEG + noise(s + 3, t, 6.0f) * 6.0f * DEG;
            curl[i] = 30.0f * DEG + noise(s + 4, t, 4.0f) * 10.0f * DEG;
            spread[i] = 8.0f * DEG;
        }
    }

    /**
     * After a kill: it folds nearly double over the body in 10 ticks, the head down at knee height
     * pecking every 4-6 ticks, the arms planted in front like a mantis's forelegs; then it rises
     * again over 12 ticks. Built on top of the calm pose, which it blends back into.
     */
    private void feed(int seed, float t, float age) {
        float rise = Bacteria.FEED_TICKS - 12.0f;
        float f = age < 10.0f ? smooth(age / 10.0f) : age < rise ? 1.0f : 1.0f - smooth((age - rise) / 12.0f);
        if (f <= 0.0f) {
            return;
        }
        float thigh = -45.0f * DEG;
        float shin = 90.0f * DEG;
        thighR = Mth.lerp(f, thighR, thigh);
        thighL = Mth.lerp(f, thighL, thigh);
        shinR = Mth.lerp(f, shinR, shin);
        shinL = Mth.lerp(f, shinL, shin);
        drop = Mth.lerp(f, drop, legDrop(thigh, shin));
        lean = Mth.lerp(f, lean, 80.0f * DEG);
        float peck = Math.abs(pulse(seed + 70, t, 5.0f, 0.8f, 1.0f, 1.0f, 2.0f, 8.0f, 15.0f));
        neckX = Mth.lerp(f, neckX, 50.0f * DEG + peck);
        headPitchW = Mth.lerp(f, headPitchW, 85.0f * DEG + peck);
        headRoll *= 1.0f - 0.7f * f;
        headYaw *= 1.0f - f;
        for (int i = 0; i < 2; i++) {
            raise[i] = Mth.lerp(f, raise[i], 80.0f * DEG);
            out[i] = Mth.lerp(f, out[i], 50.0f * DEG);
            splay[i] = Mth.lerp(f, splay[i], 0.0f);
            bend[i] = Mth.lerp(f, bend[i], 140.0f * DEG);
            foreOut[i] = Mth.lerp(f, foreOut[i], 45.0f * DEG);
            curl[i] = Mth.lerp(f, curl[i], 30.0f * DEG);
        }
    }

    /**
     * Peeking round a pillar or door frame (Kane Pixels' doorway scene): the torso leans out
     * 40 degrees over 10 ticks, the head leading; it holds there, bobbing slightly; then it
     * snaps back in 3 ticks. {@code side}: +1 = to its left, -1 = to its right.
     */
    private void peek(float t, float age, int side) {
        float back = Bacteria.PEEK_TICKS - 3.0f;
        float e = age < 10.0f ? easeOut(age / 10.0f) : age < back ? 1.0f : 1.0f - smooth((age - back) / 3.0f);
        float headFirst = age < back ? easeOut(age / 6.0f) : e;
        sway += side * 40.0f * DEG * e;
        neckZ += side * 20.0f * DEG * headFirst;
        headRoll += side * 20.0f * DEG * e;
        bob += 0.4f * Mth.sin(Mth.TWO_PI * t / 12.0f) * e;
    }

    // ------------------------------------------------------------------ one-shot moves

    /**
     * The lunge (Kane Pixels' corridor scene, about 9 ticks): wind up (hunch, head down, arms
     * drawn in), throw both arms out wide, then swing them forward and in as it reaches you. At 9
     * ticks the cage takes over, which the arms settle into over 3 more ticks (the "plant").
     */
    private void lunge(float a) {
        if (a >= 12.0f) {
            return;
        }
        lean = key(a, lean, lean + 10.0f * DEG, lean + 10.0f * DEG, lean + 5.0f * DEG);
        headPitchW = key(a, headPitchW, headPitchW + 15.0f * DEG, headPitchW - 20.0f * DEG, headPitchW);
        neckX = key(a, neckX, 0.0f, 0.0f, 0.0f);
        // Legs at full stride for the leap, then landing into the crouch.
        thighR = key(a, thighR, -30.0f * DEG, -45.0f * DEG, -20.0f * DEG);
        thighL = key(a, thighL, 20.0f * DEG, 35.0f * DEG, 0.0f);
        shinR = key(a, shinR, 30.0f * DEG, 20.0f * DEG, 50.0f * DEG);
        shinL = key(a, shinL, 40.0f * DEG, 60.0f * DEG, 50.0f * DEG);
        drop = key(a, drop, 0.8f, 0.6f, 2.0f);
        bob = key(a, bob, 0.0f, 0.0f, 0.0f);
        for (int i = 0; i < 2; i++) {
            raise[i] = key(a, raise[i], 35.0f * DEG, 70.0f * DEG, 100.0f * DEG);
            out[i] = key(a, out[i], 25.0f * DEG, 82.0f * DEG, 35.0f * DEG);
            bend[i] = key(a, bend[i], -45.0f * DEG, -10.0f * DEG, 30.0f * DEG);
            splay[i] = key(a, splay[i], 0.0f, 0.0f, 0.0f);
            foreOut[i] = key(a, foreOut[i], 0.0f, 0.0f, 0.0f);
            curl[i] = key(a, curl[i], 20.0f * DEG, -15.0f * DEG, 40.0f * DEG);
            spread[i] = key(a, spread[i], 0.0f, 25.0f * DEG, 5.0f * DEG);
        }
    }

    /**
     * One arm whips across your face: in 1.5 ticks it lifts out to its side at your head height,
     * in the next 2.5 it sweeps across in front of your face to the other side, stays a moment,
     * sweeps back and plants again (9.5 ticks in all). {@code i}: 0 = right arm, 1 = left. The path
     * was simulated from the held player's camera: the claws cross about half a block in front of
     * it at about eye height (a quarter of a block at the closest stand-off), never through it or
     * through your body.
     */
    private void whip(int i, float a) {
        if (a >= 9.5f) {
            return;
        }
        raise[i] = whipKey(a, raise[i], 100.0f * DEG, 100.0f * DEG);
        out[i] = whipKey(a, out[i], -20.0f * DEG, -50.0f * DEG);
        bend[i] = whipKey(a, bend[i], 135.0f * DEG, 105.0f * DEG);
        foreOut[i] = whipKey(a, foreOut[i], -20.0f * DEG, 20.0f * DEG);
        splay[i] = whipKey(a, splay[i], 0.0f, 0.0f);
        curl[i] = whipKey(a, curl[i], -15.0f * DEG, -15.0f * DEG);
        spread[i] = whipKey(a, spread[i], 25.0f * DEG, 25.0f * DEG);
    }

    /** Whip timeline: base, lifted out to the side at 1.5, across the face at 4-5, lifted out again at 7.5, base at 9.5. */
    private static float whipKey(float a, float base, float lifted, float across) {
        if (a < 1.5f) {
            return Mth.lerp(smooth(a / 1.5f), base, lifted);
        }
        if (a < 4.0f) {
            return Mth.lerp(smooth((a - 1.5f) / 2.5f), lifted, across);
        }
        if (a < 5.0f) {
            return across;
        }
        if (a < 7.5f) {
            return Mth.lerp(smooth((a - 5.0f) / 2.5f), across, lifted);
        }
        return Mth.lerp(smooth((a - 7.5f) / 2.0f), lifted, base);
    }

    /**
     * The head snaps in at your face in 2 ticks (the whole upper body bends 20 degrees more and
     * the neck 40), holds there shivering, then pulls back. Its head comes to about 0.6 blocks
     * from the held player's camera (0.4 at the closest stand-off).
     */
    private void leanIn(int seed, float t, float a) {
        float e = a < 2.0f ? smooth(a / 2.0f)
                : a < 2.0f + LEAN_IN_HOLD ? 1.0f : 1.0f - smooth((a - 2.0f - LEAN_IN_HOLD) / 4.0f);
        if (e <= 0.0f) {
            return;
        }
        neckX += 40.0f * DEG * e;
        headForward += 6.0f * e;
        lean += 20.0f * DEG * e;
        headRoll += noise(seed + 80, t, 1.5f) * 5.0f * DEG * e;
        // The shoulders come down and forward with it: lift the arms a bit and fold the elbows
        // more so the claws stay planted on the floor instead of sinking into it.
        for (int i = 0; i < 2; i++) {
            raise[i] += 10.0f * DEG * e;
            bend[i] += 35.0f * DEG * e;
        }
    }

    /** Coming out of the freeze: every limb jerks 15-25 degrees in 1 tick and back in 2. */
    private void twitch(int seed, int id, float a) {
        if (a >= 3.0f) {
            return;
        }
        float e = a < 1.0f ? smooth(a) : 1.0f - smooth((a - 1.0f) / 2.0f);
        lean += e * kick(seed, id, 0);
        sway += e * kick(seed, id, 1);
        headYaw += e * kick(seed, id, 2);
        headRoll += e * kick(seed, id, 3);
        thighR += e * kick(seed, id, 4);
        thighL += e * kick(seed, id, 5);
        for (int i = 0; i < 2; i++) {
            raise[i] += e * kick(seed, id, 6 + i);
            out[i] += e * kick(seed, id, 8 + i);
            bend[i] += e * kick(seed, id, 10 + i);
        }
    }

    private static float kick(int seed, int id, int limb) {
        return spread(hash(seed + 100 + limb, id), 15.0f, 25.0f) * DEG;
    }

    // ------------------------------------------------------------------ pose helpers

    /**
     * Starts a pose: knees bent by these angles (more under a low ceiling), the hips dropped to
     * match, and every other angle back to zero.
     */
    private void stance(float thigh, float shin) {
        float knee = 0.35f * squeeze;
        thighR = thighL = thigh - knee;
        shinR = shinL = shin + 2.0f * knee;
        drop = legDrop(thigh - knee, shin + 2.0f * knee);
        hips = HIP_HEIGHT - drop;
        // The least forward lean that gets hips + spine + head under the ceiling.
        float fit = (room - hips) / (SPINE + NECK_AND_HEAD);
        ceilingLean = fit >= 1.0f ? 0.0f : (float) Math.acos(Mth.clamp(fit, 0.2f, 1.0f));
        bob = 0.0f;
        lean = 0.0f;
        sway = 0.0f;
        neckX = 0.0f;
        neckZ = 0.0f;
        headPitchW = 0.0f;
        headYaw = 0.0f;
        headRoll = 0.0f;
        headForward = 0.0f;
        for (int i = 0; i < 2; i++) {
            raise[i] = 0.0f;
            out[i] = 0.0f;
            splay[i] = 0.0f;
            bend[i] = 0.0f;
            foreOut[i] = 0.0f;
            curl[i] = 0.0f;
            spread[i] = 0.0f;
        }
    }

    /** How far the hips sink (px) with the thigh and the shin (relative to the thigh) bent so. */
    private static float legDrop(float thigh, float shin) {
        return THIGH + SHIN - (THIGH * Mth.cos(thigh) + SHIN * Mth.cos(thigh + shin));
    }

    /**
     * Applies the stoop under a low ceiling to the lean, then returns how far forward the arms
     * must hang for the claws to rest on the floor instead of sinking into it (that is what makes
     * it look like it walks on crutches when it hunches).
     */
    private float floorReach() {
        lean = Math.max(lean, ceilingLean);
        float shoulderHeight = hips + SPINE * Mth.cos(lean);
        return (float) Math.acos(Mth.clamp(shoulderHeight / ARM_REACH, 0.0f, 1.0f));
    }

    /** Puts the pose built this frame onto the model parts. */
    private void apply() {
        body.y += drop + bob;
        rightLeg.xRot = thighR;
        leftLeg.xRot = thighL;
        rightShin.xRot = shinR;
        leftShin.xRot = shinL;
        spine.xRot = lean;
        spine.zRot = sway;
        neck.xRot = neckX;
        neck.zRot = neckZ;
        // The head keeps its own pitch whatever the spine and neck do.
        head.xRot = headPitchW - lean - neckX;
        head.yRot = headYaw;
        head.zRot = headRoll;
        head.z -= headForward;
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? -1.0f : 1.0f; // the right arm is on the -X side
            // The arm turns forward first (xRot), then out to the side (yRot). raise is measured in
            // the world, so the spine's lean is taken back off.
            arms[i].xRot = -(raise[i] + lean);
            arms[i].yRot = -side * out[i];
            arms[i].zRot = -side * splay[i];
            forearms[i].xRot = bend[i];
            forearms[i].zRot = -side * foreOut[i];
            hands[i].xRot = curl[i];
            for (int f = 0; f < 3; f++) {
                fingers[i][f].zRot = (1 - f) * spread[i]; // the outer fingers fan away from the middle one
            }
        }
    }

    /**
     * The organs move smoothly, so they read as alive inside the twitching wires. The heart's speed
     * tells you its mood: slow while wandering, racing once it has seen you.
     */
    private void organs(Bacteria entity, float partial, float ageInTicks) {
        float beat = heartbeat(entity.heartPhase(partial));
        float heartScale = 1.0f + 0.16f * beat;
        heart.xScale = heartScale;
        heart.yScale = heartScale;
        heart.zScale = heartScale;
        float breath = 0.5f - 0.5f * Mth.cos(Mth.TWO_PI * entity.breathPhase(partial));
        breathe(lungRight, breath);
        breathe(lungLeft, breath);
        // The stomach churns: a slow lopsided wobble.
        stomach.zRot += Mth.sin(ageInTicks * 0.07f) * 0.12f;
        stomach.xScale = 1.0f + Mth.sin(ageInTicks * 0.05f + 1.3f) * 0.05f;
    }

    private static void breathe(ModelPart lung, float breath) {
        lung.xScale = 1.0f + 0.12f * breath;
        lung.zScale = 1.0f + 0.12f * breath;
        lung.yScale = 1.0f + 0.05f * breath;
    }

    /** "Lub-dub": a strong beat and a weaker one just after it, then rest. 0..1. */
    private static float heartbeat(float phase) {
        float p = phase - Mth.floor(phase);
        float lub = Mth.sin(Mth.TWO_PI * p);
        float dub = Mth.sin(Mth.TWO_PI * (p - 0.18f));
        float a = lub > 0.0f ? lub * lub * lub * lub * lub * lub * lub * lub : 0.0f;
        float b = dub > 0.0f ? dub * dub * dub * dub * dub * dub * dub * dub : 0.0f;
        return Math.min(1.0f, a + 0.6f * b);
    }

    // ------------------------------------------------------------------ noise and timing

    /** A fixed random number from -1 to 1 for (seed, n): the same inputs always give the same number. */
    private static float hash(int seed, int n) {
        int h = seed * 0x9E3779B1 + n * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 32767.5f - 1.0f;
    }

    /**
     * Smooth noise from -1 to 1: a new random value every {@code period} ticks, with a soft
     * (cosine) glide from one to the next. Never jumps, so it never looks like frame jitter.
     */
    private static float noise(int seed, float t, float period) {
        float x = t / period;
        int b = Mth.floor(x);
        float w = 0.5f - 0.5f * Mth.cos((x - b) * Mth.PI);
        float a = hash(seed, b);
        return a + (hash(seed, b + 1) - a) * w;
    }

    /**
     * Hold-then-snap: a random angle (lo to hi degrees, either way) held for a while, then a quick
     * {@code ease}-tick move to the next one. The changes come every {@code period} ticks, each one
     * up to {@code jitter} ticks early or late, so the rhythm is never regular. Returns radians.
     */
    private static float snap(int seed, float t, float period, float jitter, float ease, float lo, float hi) {
        int b = bucket(seed, t, period, jitter);
        float s = smooth((t - bucketStart(seed, b, period, jitter)) / ease);
        float from = spread(hash(seed, b - 1), lo, hi);
        float to = spread(hash(seed, b), lo, hi);
        return (from + (to - from) * s) * DEG;
    }

    /**
     * A sudden move now and then: at the start of every slot ({@code period} ticks, give or take
     * {@code jitter}) it goes to a random angle (lo to hi degrees, the sign is random too) in
     * {@code rise} ticks, holds {@code hold} ticks and comes back in {@code fall}. Returns radians;
     * callers use the sign to pick a side.
     */
    private static float pulse(int seed, float t, float period, float jitter, float rise, float hold, float fall, float lo, float hi) {
        int b = bucket(seed, t, period, jitter);
        float a = t - bucketStart(seed, b, period, jitter);
        float e = a < rise ? smooth(a / rise) : a < rise + hold ? 1.0f : 1.0f - smooth((a - rise - hold) / fall);
        return e * spread(hash(seed + 1, b), lo, hi) * DEG;
    }

    /** When slot {@code b} starts: every {@code period} ticks, each start moved by up to +-jitter. */
    private static float bucketStart(int seed, int b, float period, float jitter) {
        return b * period + jitter * hash(seed ^ 0x5BD1E995, b);
    }

    /** Which slot tick {@code t} is in (jitter must be under half the period). */
    private static int bucket(int seed, float t, float period, float jitter) {
        int b = Mth.floor(t / period);
        if (t < bucketStart(seed, b, period, jitter)) {
            return b - 1;
        }
        return t >= bucketStart(seed, b + 1, period, jitter) ? b + 1 : b;
    }

    /** -1..1 to a size between lo and hi with the same sign. */
    private static float spread(float u, float lo, float hi) {
        return Math.copySign(lo + (hi - lo) * Math.abs(u), u);
    }

    /** 0..1 eased at both ends (clamped). */
    private static float smooth(float x) {
        float c = Mth.clamp(x, 0.0f, 1.0f);
        return c * c * (3.0f - 2.0f * c);
    }

    /** 0..1 fast at first, slowing to a stop (clamped). */
    private static float easeOut(float x) {
        float c = 1.0f - Mth.clamp(x, 0.0f, 1.0f);
        return 1.0f - c * c;
    }

    /** Lunge timeline: from {@code base} to the keys at ticks 2, 5 and 9, then back to base by 12. */
    private static float key(float a, float base, float k2, float k5, float k9) {
        if (a < 2.0f) {
            return Mth.lerp(smooth(a / 2.0f), base, k2);
        }
        if (a < 5.0f) {
            return Mth.lerp(smooth((a - 2.0f) / 3.0f), k2, k5);
        }
        if (a < 9.0f) {
            return Mth.lerp(smooth((a - 5.0f) / 4.0f), k5, k9);
        }
        return Mth.lerp(smooth((a - 9.0f) / 3.0f), k9, base);
    }
}
