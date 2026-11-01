package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.Bacteria;
import com.rickr.backrooms.entity.BacteriaPoseState;
import com.rickr.backrooms.entity.BacteriaRhythm;
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
    // Scary, not goofy. What frightens in the reference footage is restraint, then violence:
    // Kane Pixels' Bacteria stands dead still for seconds with its head slightly cocked, then
    // covers a corridor in under half a second, hunched, arms low and forward. So here:
    //  - Almost nothing moves most of the time. Standing, only its fingers and a very slow breath
    //    move. Every few seconds a "glitch" pops ONE thing (its head tilts, a hand clenches, a
    //    shoulder hitches, the spine jerks) in a single tick: never two at once, never in a rhythm.
    //  - Every motion is either slow (20-60 ticks, eased) or instant (1-2 ticks). Nothing between.
    //  - Its head locks onto you like an owl's: aimed exactly at your eyes in the world whatever
    //    the body does under it (aimHead). The head turns first; the body follows later.
    //  - Its arms are dead weight: springy pendulums that lag behind the body and overshoot once
    //    when it stops (swingArms), the claws hanging just above the floor.
    //  - It is lopsided: one shoulder lower and further forward, the leg on that side limps, the
    //    other arm more bent, the neck pushed forward like a vulture's.
    // Angles: degrees in the constants (times DEG = radians); time in ticks (20 a second). Model
    // space: +Y is down, the front is -Z, +X is its left.
    //
    // setupAnim runs every frame for every Bacteria on screen, so it never creates objects: only
    // numbers, the fields below, the arrays made once with the model, and each Bacteria's own
    // BacteriaPoseState (made once with the entity).

    private static final float DEG = Mth.DEG_TO_RAD;
    // How far its spine leans forward (degrees).
    private static final float IDLE_LEAN = 22.0f;
    private static final float WALK_LEAN = 30.0f;
    private static final float SEARCH_LEAN = 32.0f;
    private static final float CHASE_LEAN = 52.0f;
    private static final float CAGE_LEAN = 18.0f;
    /** The very slow calm breath moves the spine this much (degrees). */
    private static final float BREATH_DEG = 1.5f;
    /** Vulture neck: pushed forward of the chest (px) and tilted forward (degrees). */
    private static final float NECK_FORWARD = 2.0f;
    private static final float NECK_TILT = 12.0f;
    /** Lopsided: its low shoulder sits this much lower (px) and further forward (px). */
    private static final float LOW_SHOULDER_DROP = 1.25f;
    private static final float LOW_SHOULDER_FORWARD = 0.8f;
    /** Its hanging fingertips stay this high above the floor (px; 1.5 px = 0.09 blocks). */
    private static final float HAND_HOVER = 1.5f;
    // Head lock: how fast the head may turn toward what it looks at (degrees per tick).
    private static final float AIM_SLOW = 1.0f;     // calm: it notices you slowly...
    private static final float AIM_TRACK = 10.0f;   // ...but once its head is on you, it stays on you
    private static final float AIM_FAST = 45.0f;    // spotted: the snap onto you (1-2 ticks)
    private static final float AIM_FREE = 40.0f;    // nobody to look at: its own turns (slow or a snap)
    /** The head may turn this far from where the body faces (an owl, not a person). */
    private static final float HEAD_YAW_LIMIT = 150.0f;
    /** Nobody to look at: its head holds a direction, then turns every 100-200 ticks... */
    private static final float IDLE_TURN_PERIOD = 150.0f;
    private static final float IDLE_TURN_JITTER = 50.0f;
    /** ...either slowly, over this many ticks, or in a single tick (half of the turns each). */
    private static final float SLOW_TURN_TICKS = 30.0f;
    // Dead-weight arms: a damped spring, swinging with this period (ticks) and damping (0-1).
    // Calm: slow and loose (it lags the body by 4-5 ticks and overshoots once when it stops).
    private static final float CALM_ARM_PERIOD = 18.0f;
    private static final float CALM_ARM_DAMPING = 0.3f;
    // Running: quicker and stiffer, so the reaching grabs land with a short overshoot.
    private static final float CHASE_ARM_PERIOD = 10.0f;
    private static final float CHASE_ARM_DAMPING = 0.45f;
    // Frozen when it spots you: the arms drop to hanging quickly and heavily, without swinging.
    private static final float SETTLE_ARM_PERIOD = 10.0f;
    private static final float SETTLE_ARM_DAMPING = 0.8f;
    /** How hard speeding up or slowing down (and turning) throws the hanging arms about. */
    private static final float ARM_SHOVE = 0.6f;
    private static final float ARM_TURN = 0.3f;
    /** Moving, the arms trail behind by this much (radians per block/tick of speed). */
    private static final float ARM_DRAG = 1.4f;
    private static final float CHASE_ARM_DRAG = 0.4f;
    // Running, each arm swings between these two poses (raise, out, elbow bend, in degrees).
    // Worked out with a joint simulation in world space for leans of 52-58 degrees: reaching, the
    // claws are 0.45 blocks either side of its middle, 3.2 blocks ahead of its feet (2 ahead of
    // its shoulders) and 0.4-0.5 blocks up; pulled back, 0.55 to the side, 2.4 ahead, 0.2-0.35
    // up. So from the front the arms come almost straight at you, slightly closing together.
    private static final float CHASE_REACH_RAISE = 26.0f;
    private static final float CHASE_REACH_OUT = -10.0f;
    private static final float CHASE_REACH_BEND = -45.0f;
    private static final float CHASE_PULL_RAISE = -10.0f;
    private static final float CHASE_PULL_OUT = -9.0f;
    private static final float CHASE_PULL_BEND = -80.0f;
    /** Running: whatever the swing and its overshoot do, the arms stay within these (degrees). */
    private static final float CHASE_RAISE_MIN = -20.0f;
    private static final float CHASE_RAISE_MAX = 40.0f;
    private static final float CHASE_OUT_MIN = -16.0f;
    private static final float CHASE_OUT_MAX = 0.0f;
    /**
     * The claws never end up further than this out to either side of its middle (blocks), in any
     * pose: no spread arms, no T-pose. Only the cage (planted wide around you on purpose) and the
     * lunge's brief embrace (EMBRACE_SPREAD) go wider.
     */
    private static final float MAX_SPREAD = 1.0f;
    private static final float EMBRACE_SPREAD = 1.3f;
    /** Cage: the head's slow creep toward your face, snap in, hold and slow pull back: one cycle. */
    private static final float CAGE_HEAD_CYCLE = 110.0f;
    /** Test command, cage: the first whip comes at this tick, then (with loop) every WHIP_LOOP_TICKS. */
    private static final float WHIP_FIRST = 30.0f;
    private static final float WHIP_LOOP_TICKS = 50.0f;
    /** Test command with "loop": the feed replays this often; the peek alternates a normal and a long one. */
    private static final float FEED_LOOP_TICKS = Bacteria.FEED_TICKS + 18.0f;
    private static final float PEEK_LOOP_TICKS = Bacteria.PEEK_TICKS + 20.0f;
    private static final float PEEK_HOLD_LOOP_TICKS = Bacteria.PEEK_HOLD_TICKS + 20.0f;

    // What it is doing, as far as the model is concerned.
    private static final int CALM = 0;
    private static final int CHASE = 1;
    private static final int SEARCH = 2;
    private static final int SPOTTED = 3;
    private static final int CAGE = 4;
    private static final int FEED = 5;
    private static final int PEEK = 6;
    // How the head is aimed: its own way (FREE), or locked on someone, acquired slowly, fast or exactly.
    private static final int AIM_NONE = 0;
    private static final int LOCK_SLOW = 1;
    private static final int LOCK_FAST = 2;
    private static final int LOCK_EXACT = 3;
    // How the arms move: swinging dead weight (calm / running), settling heavily, or posed exactly (attacks).
    private static final int ARMS_CALM = 0;
    private static final int ARMS_CHASE = 1;
    private static final int ARMS_SETTLE = 2;
    private static final int ARMS_DIRECT = 3;

    // The pose being built this frame (radians; pixels for the offsets). Each state fills these
    // in, the attacks then bend them, the arms swing, the glitches pop, the head is aimed, and
    // apply() finally puts it all on the model parts. Arm arrays: index 0 = right arm, 1 = left.
    private float room;          // free height above the feet (px)
    private float squeeze;       // 0 = room to stand, 1 = squeezed under a 3-block ceiling
    private float drop;          // how far the hips sink because the knees are bent (px)
    private float hips;          // hip height (px)
    private float ceilingLean;   // the least forward lean that fits the head under the ceiling
    private float bob;           // extra up/down of the whole body (px, + is down)
    private float lean;          // spine forward lean
    private float sway;          // spine sideways lean (+ = to its left)
    private float spineShift;    // spine glitch: a 1 px sideways jerk
    private float thighR;
    private float thighL;
    private float shinR;
    private float shinL;
    private float neckX;
    private float neckZ;
    private float neckForward;   // px
    private float headRoll;      // tilt about its own nose (radians)
    private float headForward;   // px
    private float headYawFree;   // its own head direction when not locked: degrees from the body's facing
    private float headPitchFree; // ...and degrees down from level, in the world
    private float freeWeight;    // 0 = head fully on its target, 1 = fully its own way (feeding)
    private int aimMode;
    private int armMode;
    private int lowArm;          // which side is lopsided: 0 = right, 1 = left
    // The head's own rotation, worked out by aimHead.
    private float headX;
    private float headY;
    private float headZ;
    // How far forward the arm is raised, measured from hanging straight down in the world
    // (90 degrees = pointing straight ahead), and how far it swings out to its side from there.
    private final float[] raise = new float[2];
    private final float[] out = new float[2];
    private final float[] splay = new float[2];    // sideways tilt of a hanging arm
    private final float[] bend = new float[2];     // elbow: - bends it the natural way, + the wrong way
    private final float[] foreOut = new float[2];  // forearm tilted out to its side
    private final float[] curl = new float[2];     // whole hand curled in (+) or flung open (-)
    private final float[] spread = new float[2];   // claws fanned apart
    private final float[] hitch = new float[2];    // shoulder glitch: pulled up (px)
    private final float[] clench = new float[2];   // fingers clenched shut (degrees, on top of their own curl)
    private final float[][] fingerCurl = new float[2][3]; // each finger's own curl (degrees)
    private boolean fingersLive; // false = the fingers hold still at fingerRest
    private float fingerRest;
    // Scratch rotation matrices (3x3, row by row) for aiming the head and measuring the arms.
    private final float[] matA = new float[9];
    private final float[] matB = new float[9];
    private final float[] matParent = new float[9];
    private final float[] matWant = new float[9];
    private final float[] matSpine = new float[9];
    private final float[] matArm = new float[9];
    private final float[] matFore = new float[9];
    private final float[] matHand = new float[9];
    // Where the claws of the arm last measured are, from its middle (px): out to its side, and ahead.
    private float clawSide;
    private float clawAhead;

    @Override
    public void setupAnim(Bacteria entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        for (ModelPart part : animated) {
            part.resetPose();
        }
        BacteriaPoseState memory = entity.animState;
        float t = ageInTicks;
        float partial = ageInTicks - entity.tickCount;
        // Its own random clocks: the same id always gives the same moves, and no two ids match.
        int seed = BacteriaRhythm.seed(entity.getId());
        // Time since this Bacteria's last frame (0 if it is drawn twice in one frame). After a
        // long gap (it was out of sight) it starts fresh instead of swinging its arms wildly.
        boolean fresh = Float.isNaN(memory.lastAge) || t < memory.lastAge || t - memory.lastAge > 5.0f;
        float dt = fresh ? 0.0f : t - memory.lastAge;
        memory.lastAge = t;
        // 0 = standing still, 1 = walking or running: fades the stride in and out.
        float moving = Mth.clamp((limbSwingAmount - 0.02f) * 8.0f, 0.0f, 1.0f);
        float phase = entity.gaitPhase(partial);
        float gait = Mth.TWO_PI * phase;
        float breath = Mth.sin(Mth.TWO_PI * entity.breathPhase(partial));
        lowArm = BacteriaRhythm.hash(seed, 1) > 0.0f ? 0 : 1; // the same side as its limping leg

        // Under a low ceiling (doorways, all of Level 2) it stoops instead of poking through.
        room = entity.headroom(partial) * 16.0f - 1.5f; // free height in px, minus a margin
        float standing = HIP_HEIGHT + SPINE + NECK_AND_HEAD;
        squeeze = Mth.clamp((standing - room) / (standing - 46.5f), 0.0f, 1.0f);

        // --- Which pose, which attack is playing (its age in ticks, -1 = none), who it looks at --
        int state;
        float age = 0.0f;
        int occurrence = 0;    // tells repeats of a move apart, so each one goes a different way
        float lunge = -1.0f;
        float whip = -1.0f;
        int peekSide = 1;
        boolean peekHold = false;
        float near = 0.0f;
        float cageClock = t + CAGE_HEAD_CYCLE * BacteriaRhythm.unit(seed + 90, 0);
        int target;
        Bacteria.AnimPose forced = entity.forcedPose();
        if (forced != Bacteria.AnimPose.AUTO) {
            // Forced by /backrooms bacteria pose: it stands still (walk and run play on the spot)
            // and looks at the nearest player, so whoever films it gets the head lock.
            float f = entity.forcedAge(partial);
            boolean loop = entity.forcedLoop();
            float loopTicks = Bacteria.POSE_LOOP_TICKS;
            int cycle = loop ? (int) (f / loopTicks) : 0;
            float inCycle = loop ? f % loopTicks : f;
            target = memory.nearestId;
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
                    // With loop: the freeze, then straight into the run, every 2 s.
                    if (loop && inCycle >= Bacteria.SPOTTED_TICKS) {
                        state = CHASE;
                        moving = 1.0f;
                    } else {
                        state = SPOTTED;
                        age = inCycle;
                        occurrence = cycle;
                    }
                }
                case LUNGE -> {
                    // Runs at you, then holds you: the cage once the arms have closed.
                    lunge = inCycle;
                    occurrence = cycle;
                    state = lunge < Bacteria.LUNGE_TICKS ? CHASE : CAGE;
                    moving = state == CHASE ? 1.0f : 0.0f;
                    cageClock = inCycle - Bacteria.LUNGE_TICKS;
                }
                case CAGE -> {
                    state = CAGE;
                    cageClock = f;
                    if (!loop) {
                        whip = f - WHIP_FIRST;
                    } else if (f >= WHIP_FIRST) {
                        whip = (f - WHIP_FIRST) % WHIP_LOOP_TICKS;
                        occurrence = (int) ((f - WHIP_FIRST) / WHIP_LOOP_TICKS);
                    }
                }
                case FEED -> {
                    state = FEED;
                    age = loop ? f % FEED_LOOP_TICKS : f;
                }
                case PEEK -> {
                    state = PEEK;
                    age = f;
                    if (loop) {
                        // A normal peek, then one that stays out for a long time, and again.
                        float both = PEEK_LOOP_TICKS + PEEK_HOLD_LOOP_TICKS;
                        occurrence = (int) (f / both);
                        age = f % both;
                        if (age >= PEEK_LOOP_TICKS) {
                            age -= PEEK_LOOP_TICKS;
                            peekHold = true;
                        }
                    }
                    peekSide = BacteriaRhythm.hash(seed + 26, occurrence) > 0.0f ? 1 : -1;
                }
                default -> state = CALM; // IDLE
            }
        } else {
            Bacteria.Mode mode = entity.mode();
            boolean calm = mode == Bacteria.Mode.WANDER || mode == Bacteria.Mode.DRIFT;
            Bacteria.Action action = entity.action();
            float since = entity.actionAge(partial);
            occurrence = entity.actionStamp();
            boolean peeking = action == Bacteria.Action.PEEK_LEFT || action == Bacteria.Action.PEEK_RIGHT
                    || action == Bacteria.Action.PEEK_LEFT_HOLD || action == Bacteria.Action.PEEK_RIGHT_HOLD;
            peekHold = action == Bacteria.Action.PEEK_LEFT_HOLD || action == Bacteria.Action.PEEK_RIGHT_HOLD;
            target = -1;
            if (calm && action == Bacteria.Action.FEED && since < Bacteria.FEED_TICKS) {
                state = FEED;
                age = since;
                target = memory.nearestId;
            } else if (calm && peeking && since < (peekHold ? Bacteria.PEEK_HOLD_TICKS : Bacteria.PEEK_TICKS)) {
                state = PEEK;
                age = since;
                peekSide = action == Bacteria.Action.PEEK_LEFT || action == Bacteria.Action.PEEK_LEFT_HOLD ? 1 : -1;
                target = memory.nearestId;
            } else if (mode == Bacteria.Mode.CHASE) {
                state = entity.isCaging() ? CAGE : CHASE;
                if (action == Bacteria.Action.LUNGE) {
                    lunge = since;
                } else if (action == Bacteria.Action.WHIP) {
                    whip = since;
                }
                target = entity.preyId();
                // The last few blocks before it lunges, it hunches even lower.
                Entity prey = entity.level().getEntity(target);
                if (prey != null) {
                    near = Mth.clamp(((float) Bacteria.LUNGE_RANGE + 4.0f - entity.distanceTo(prey)) / 4.0f, 0.0f, 1.0f);
                }
            } else if (mode == Bacteria.Mode.SPOTTED) {
                state = SPOTTED;
                age = entity.modeAge(partial);
                occurrence = entity.modeStamp();
                target = entity.preyId();
            } else if (mode == Bacteria.Mode.SEARCH) {
                state = SEARCH;
            } else {
                state = CALM;
                target = memory.watchId;
            }
            peekHold &= state == PEEK;
        }

        // --- Build the pose --------------------------------------------------------------------
        switch (state) {
            case CHASE -> chase(moving, gait, phase, near);
            case SEARCH -> search(seed, t, moving, gait);
            case SPOTTED -> spotted(seed, age, occurrence);
            case CAGE -> cage(seed, t, cageClock);
            case FEED -> {
                calm(seed, t, 0.0f, gait, phase, breath);
                feed(seed, t, age);
            }
            case PEEK -> {
                calm(seed, t, 0.0f, gait, phase, breath);
                peek(age, peekSide, peekHold);
            }
            default -> calm(seed, t, moving, gait, phase, breath);
        }
        if (lunge >= 0.0f && lunge < 12.0f) {
            lunge(lunge);
        }
        if (whip >= 0.0f && whip < 9.5f) {
            whip(BacteriaRhythm.hash(seed + 27, occurrence) > 0.0f ? 0 : 1, whip);
        }
        swingArms(entity, memory, partial, dt, fresh);
        if (state != SPOTTED && !peekHold) {
            glitch(seed, t, state);
        }
        if (state == CHASE && armMode == ARMS_CHASE) {
            // Running, the arms reach forward and DOWN: never up, never out to the sides.
            for (int i = 0; i < 2; i++) {
                raise[i] = Mth.clamp(raise[i], CHASE_RAISE_MIN * DEG, CHASE_RAISE_MAX * DEG);
                out[i] = Mth.clamp(out[i], CHASE_OUT_MIN * DEG, CHASE_OUT_MAX * DEG);
            }
        }
        if (state != CAGE && state != PEEK) {
            // (Not the cage, planted wide around you on purpose, nor the peek, where the whole
            // body leans out sideways from behind cover and the arms just hang under it.)
            boolean embracing = lunge >= 5.0f && lunge < 9.0f;
            limitSpread(embracing ? EMBRACE_SPREAD : MAX_SPREAD);
        }
        fingers(seed, t);
        aimHead(entity, memory, partial, dt, fresh, target);
        apply();
        organs(entity, partial, ageInTicks);
    }

    // ------------------------------------------------------------------ the states

    /**
     * Nobody seen yet: standing (m = 0) or stalking (m = 1). Standing, only the fingers and a very
     * slow breath move: hunched 22 degrees, neck pushed forward, head tilted to one side, arms
     * hanging straight down with the claws just off the floor. If it can see you within 24
     * blocks its head slowly turns onto you and stays there; otherwise the head holds a direction
     * and now and then turns, slowly or in one tick. Stalking: a slow, limping 32-tick stride
     * (the limp and the mid-step pauses come from the entity's gait clock), each footfall
     * dropping the body a little, the arms swinging as dead weight.
     */
    private void calm(int seed, float t, float m, float gait, float phase, float breath) {
        stance(-12.5f * DEG, 25.0f * DEG);
        lean = (IDLE_LEAN + (WALK_LEAN - IDLE_LEAN) * m) * DEG + BREATH_DEG * DEG * breath * (1.0f - m);
        fitLean();
        stride(m, 22.0f * DEG, 40.0f * DEG, gait);
        sway = 2.5f * DEG * Mth.sin(gait) * m;
        // Feet land at a quarter and three quarters of the stride: the body drops 0.8 px in a
        // tick and settles back over 2.
        float u = 2.0f * (phase - 0.25f);
        float d = (u - Mth.floor(u)) * 0.5f * Bacteria.WALK_STRIDE_TICKS;
        bob = m * 0.8f * (d < 1.0f ? smooth(d) : 1.0f - smooth((d - 1.0f) / 2.0f));
        neckX = NECK_TILT * DEG;
        neckForward = NECK_FORWARD;
        headRoll = idleTilt(seed);
        headYawFree = holdTurn(seed + 5, t, IDLE_TURN_PERIOD, IDLE_TURN_JITTER, 20.0f, 60.0f);
        headPitchFree = 5.0f;
        hangingArms();
        aimMode = LOCK_SLOW;
    }

    /**
     * Running at you: a low predator sprint. Torso pitched 52 degrees forward (58 just before the
     * lunge), knees deeply bent, long strides (9-11 ticks, the entity breaks the rhythm a
     * little). The arms reach forward and DOWN in turns, each one stretching out ahead toward the
     * floor in front of you and then pulling back under it, like it is clawing itself along,
     * with the lag and overshoot of the arm springs; hands open, claws spread, close to the floor,
     * never out to the sides. The head floats, locked exactly on your eyes, while the body pumps.
     */
    private void chase(float m, float gait, float phase, float near) {
        stance(-30.0f * DEG, 55.0f * DEG);
        stride(m, 45.0f * DEG, 60.0f * DEG, gait);
        bob = 0.3f * (1.0f - Mth.cos(2.0f * gait)) * m;
        lean = (CHASE_LEAN + 6.0f * near) * DEG;
        fitLean();
        sway = 3.0f * DEG * Mth.sin(gait) * m;
        neckForward = NECK_FORWARD;
        for (int i = 0; i < 2; i++) {
            // Each arm reaches during one stride and pulls back during the next, the two arms
            // taking turns.
            float cycle = phase * 0.5f + 0.5f * i;
            boolean reaching = cycle - Mth.floor(cycle) < 0.5f;
            raise[i] = (reaching ? CHASE_REACH_RAISE : CHASE_PULL_RAISE) * DEG;
            out[i] = (reaching ? CHASE_REACH_OUT : CHASE_PULL_OUT) * DEG;
            bend[i] = (reaching ? CHASE_REACH_BEND : CHASE_PULL_BEND) * DEG;
            curl[i] = -10.0f * DEG;
            spread[i] = 25.0f * DEG;
        }
        fingersLive = false;
        fingerRest = -5.0f;
        armMode = ARMS_CHASE;
        aimMode = LOCK_EXACT;
    }

    /** Searching where it last saw you: hunched, its head holding still, then turning (slow or a snap). */
    private void search(int seed, float t, float m, float gait) {
        stance(-12.5f * DEG, 25.0f * DEG);
        stride(m, 25.0f * DEG, 40.0f * DEG, gait);
        lean = SEARCH_LEAN * DEG;
        fitLean();
        sway = 2.0f * DEG * Mth.sin(gait) * m;
        neckX = NECK_TILT * DEG;
        neckForward = NECK_FORWARD;
        headRoll = 0.6f * idleTilt(seed);
        headYawFree = holdTurn(seed + 32, t, 40.0f, 10.0f, 20.0f, 70.0f);
        headPitchFree = 12.0f;
        hangingArms();
        aimMode = AIM_NONE;
    }

    /**
     * It has seen you. Everything stops at once: its head snaps onto you in 1-2 ticks (the body
     * turns after it), then only the head moves, slowly tilting over to 35-45 degrees. It stands
     * hunched 27 degrees, arms hanging heavy just ahead of it with the claws just off the floor
     * (if they were swinging, they drop there at once, heavily, without swinging back), the claws
     * slowly fanning open. In the last 3 ticks the body drops 1.5 px and the claws spread wide,
     * and then it runs.
     */
    private void spotted(int seed, float age, int occurrence) {
        stance(-12.5f * DEG, 25.0f * DEG);
        lean = 27.0f * DEG;
        fitLean();
        neckX = NECK_TILT * DEG;
        neckForward = NECK_FORWARD;
        float x = Mth.clamp(age / Bacteria.SPOTTED_TICKS, 0.0f, 1.0f);
        float side = BacteriaRhythm.hash(seed + 25, occurrence) > 0.0f ? 1.0f : -1.0f;
        headRoll = side * (35.0f + 10.0f * BacteriaRhythm.unit(seed + 26, occurrence)) * DEG * x * x;
        float windUp = smooth((age - (Bacteria.SPOTTED_TICKS - 3.0f)) / 3.0f);
        bob = 1.5f * windUp;
        hangingArms();
        float open = smooth(x);
        for (int i = 0; i < 2; i++) {
            raise[i] += 5.0f * DEG; // a little forward: tense, not slack
            spread[i] = (6.0f + 14.0f * open + 15.0f * windUp) * DEG;
            curl[i] = (8.0f - 18.0f * windUp) * DEG;
        }
        fingersLive = false;
        fingerRest = 12.0f - 10.0f * open - 12.0f * windUp;
        armMode = ARMS_SETTLE;
        aimMode = LOCK_FAST;
    }

    /**
     * Holding you (the reference video's "arm cage"): it stands over you 1.5-2 blocks away, torso
     * fairly upright, its arms out past you and down, the claws planted on the floor 0.5-0.9
     * blocks either side of you (the shape was checked with a joint simulation from the held
     * player's camera; see the pass-1 notes). Slow and intimate: the claws creep along the floor
     * 0.1-0.2 blocks at a time over 25 ticks, and now and then a hand re-grips in a single tick.
     * The head creeps toward your face over 35 ticks, rolling over to 60-90 degrees as if
     * examining you, snaps in the last bit in 2 ticks, holds, then slowly pulls back. Its head
     * comes to 0.6 blocks from the held player's camera (0.4 at the closest stand-off).
     */
    private void cage(int seed, float t, float clock) {
        stance(-20.0f * DEG, 40.0f * DEG);
        lean = CAGE_LEAN * DEG;
        fitLean();
        neckX = 10.0f * DEG;
        for (int i = 0; i < 2; i++) {
            int s = seed + 60 + 10 * i;
            // Lifting the arm and folding the elbow together slide the claws along the floor.
            float lift = crawl(s, t, 1.0f, 3.0f);
            raise[i] = (25.0f + lift) * DEG;
            bend[i] = (22.0f + 2.6f * lift) * DEG;
            out[i] = (25.0f + crawl(s + 2, t, 0.5f, 2.5f)) * DEG;
            foreOut[i] = -30.0f * DEG;
            curl[i] = 30.0f * DEG;
            spread[i] = 8.0f * DEG;
        }
        // The head's creep, snap, hold and slow pull back.
        int n = Mth.floor(clock / CAGE_HEAD_CYCLE);
        float c = clock - n * CAGE_HEAD_CYCLE;
        float roll = (BacteriaRhythm.hash(seed + 85, n) > 0.0f ? 1.0f : -1.0f)
                * (60.0f + 30.0f * BacteriaRhythm.unit(seed + 86, n)) * DEG;
        float k;
        if (c < 35.0f) {
            k = 0.3f * smooth(c / 35.0f);
            headRoll = roll * smooth(c / 35.0f);
        } else if (c < 37.0f) {
            k = 0.3f + 0.7f * smooth((c - 35.0f) / 2.0f);
            headRoll = roll;
        } else if (c < 60.0f) {
            k = 1.0f;
            headRoll = roll;
        } else if (c < 95.0f) {
            k = 1.0f - smooth((c - 60.0f) / 35.0f);
            headRoll = roll * k;
        } else {
            k = 0.0f;
        }
        leanToward(k);
        armMode = ARMS_DIRECT;
        aimMode = LOCK_EXACT;
    }

    /**
     * After a kill: it folds nearly double over the body in 10 ticks, the head down at knee height
     * pecking, the arms planted in front like a mantis's forelegs; then it rises slowly (20 ticks),
     * its head coming up locked on the nearest other player. Built on top of the calm pose.
     */
    private void feed(int seed, float t, float age) {
        float rise = Bacteria.FEED_TICKS - 20.0f;
        float f = age < 10.0f ? smooth(age / 10.0f) : age < rise ? 1.0f : 1.0f - smooth((age - rise) / 20.0f);
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
        float peck = pulse(seed + 70, t, 5.0f, 0.8f, 1.0f, 1.0f, 2.0f, 8.0f, 15.0f);
        neckX = Mth.lerp(f, neckX, (50.0f + peck) * DEG);
        headRoll *= 1.0f - 0.7f * f;
        headYawFree = 0.0f;
        headPitchFree = 85.0f + peck;
        freeWeight = f;
        for (int i = 0; i < 2; i++) {
            raise[i] = Mth.lerp(f, raise[i], 80.0f * DEG);
            out[i] = Mth.lerp(f, out[i], 50.0f * DEG);
            splay[i] = Mth.lerp(f, splay[i], 0.0f);
            bend[i] = Mth.lerp(f, bend[i], 140.0f * DEG);
            foreOut[i] = Mth.lerp(f, foreOut[i], 45.0f * DEG);
            curl[i] = Mth.lerp(f, curl[i], 30.0f * DEG);
        }
        aimMode = LOCK_EXACT;
    }

    /**
     * Peeking round a pillar or door frame (Kane Pixels' doorway scene): the head comes out first,
     * slowly, tilted 35 degrees, and locks onto you; the torso leans out 40 degrees after it over
     * 25 ticks. It snaps back in 2 ticks, or (one peek in four) stays out without moving at all
     * for over 8 seconds first. {@code side}: +1 = to its left, -1 = to its right.
     */
    private void peek(float age, int side, boolean hold) {
        float back = (hold ? Bacteria.PEEK_HOLD_TICKS : Bacteria.PEEK_TICKS) - 2.0f;
        float e = age < 25.0f ? smooth(age / 25.0f) : age < back ? 1.0f : 1.0f - smooth((age - back) / 2.0f);
        float headFirst = age < back ? smooth(age / 18.0f) : e;
        sway += side * 40.0f * DEG * e;
        neckZ += side * 20.0f * DEG * headFirst;
        headRoll = Mth.lerp(headFirst, headRoll, side * 35.0f * DEG);
        if (hold && age >= 25.0f) {
            // The long one: not a breath, not a finger.
            lean = (IDLE_LEAN * DEG);
            fitLean();
            fingersLive = false;
        }
    }

    // ------------------------------------------------------------------ attacks

    /**
     * The lunge (Kane Pixels' corridor scene, 9 ticks), checked in world space with a joint
     * simulation:
     * <ul>
     * <li>0-2 wind up: it crouches 4 px lower and hunches to 56 degrees, gathering both arms in
     * under its body (claws 0.55 blocks either side, low, 2.6 ahead);</li>
     * <li>2-5 throw: it straightens to 50 degrees and both arms shoot forward and low at your legs
     * (claws 0.45 either side, 3.1 ahead);</li>
     * <li>5-7 the embrace: it rears up to 30 degrees and the arms open around you, hands at chest
     * height (1.2 blocks up), 1.1 blocks either side and 2.9 ahead, already curling inward;</li>
     * <li>7-9 they close down and in onto the floor beside you: the cage pose. From tick 9 the cage
     * (or, if it missed, the run) takes over over 3 more ticks.</li>
     * </ul>
     * The arms are never straight out sideways: at their widest (the embrace) the hands are well
     * ahead of the body, at chest height.
     */
    private void lunge(float a) {
        float cageDrop = legDrop(-20.0f * DEG, 40.0f * DEG);
        lean = key(a, lean, 56.0f * DEG, 50.0f * DEG, 30.0f * DEG, CAGE_LEAN * DEG);
        drop = key(a, drop, drop + 4.0f, drop + 1.0f, drop + 1.5f, cageDrop);
        bob = key(a, bob, 0.0f, 0.0f, 0.0f, 0.0f);
        neckX = key(a, neckX, 0.0f, 0.0f, 0.0f, 10.0f * DEG);
        thighR = key(a, thighR, -40.0f * DEG, -55.0f * DEG, -30.0f * DEG, -20.0f * DEG);
        thighL = key(a, thighL, -10.0f * DEG, 35.0f * DEG, 10.0f * DEG, -20.0f * DEG);
        shinR = key(a, shinR, 65.0f * DEG, 30.0f * DEG, 45.0f * DEG, 40.0f * DEG);
        shinL = key(a, shinL, 75.0f * DEG, 50.0f * DEG, 45.0f * DEG, 40.0f * DEG);
        for (int i = 0; i < 2; i++) {
            raise[i] = key(a, raise[i], -2.0f * DEG, 26.0f * DEG, 35.0f * DEG, 25.0f * DEG);
            out[i] = key(a, out[i], -9.0f * DEG, -10.0f * DEG, 20.0f * DEG, 25.0f * DEG);
            bend[i] = key(a, bend[i], -82.0f * DEG, -45.0f * DEG, -50.0f * DEG, 22.0f * DEG);
            splay[i] = key(a, splay[i], 0.0f, 0.0f, 0.0f, 0.0f);
            foreOut[i] = key(a, foreOut[i], 0.0f, 0.0f, -40.0f * DEG, -30.0f * DEG);
            curl[i] = key(a, curl[i], 25.0f * DEG, -10.0f * DEG, -15.0f * DEG, 30.0f * DEG);
            spread[i] = key(a, spread[i], 5.0f * DEG, 20.0f * DEG, 30.0f * DEG, 8.0f * DEG);
        }
        armMode = ARMS_DIRECT;
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
        raise[i] = whipKey(a, raise[i], 100.0f * DEG, 100.0f * DEG);
        out[i] = whipKey(a, out[i], -20.0f * DEG, -50.0f * DEG);
        bend[i] = whipKey(a, bend[i], 135.0f * DEG, 105.0f * DEG);
        foreOut[i] = whipKey(a, foreOut[i], -20.0f * DEG, 20.0f * DEG);
        splay[i] = whipKey(a, splay[i], 0.0f, 0.0f);
        curl[i] = whipKey(a, curl[i], -15.0f * DEG, -15.0f * DEG);
        spread[i] = whipKey(a, spread[i], 25.0f * DEG, 25.0f * DEG);
        armMode = ARMS_DIRECT;
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
     * The upper body bending in toward the held player's face, {@code e} from 0 (not at all) to
     * 1 (all the way: 20 degrees more lean, 40 more neck, the head 6 px forward). The arms lift
     * and the elbows fold to match, so the claws stay planted instead of sinking into the floor.
     */
    private void leanToward(float e) {
        if (e <= 0.0f) {
            return;
        }
        neckX += 40.0f * DEG * e;
        headForward += 6.0f * e;
        lean += 20.0f * DEG * e;
        for (int i = 0; i < 2; i++) {
            raise[i] += 10.0f * DEG * e;
            bend[i] += 35.0f * DEG * e;
        }
    }

    // ------------------------------------------------------------------ life: glitches, fingers

    /**
     * The rare glitches (same clock as the clicks the entity plays, see BacteriaRhythm).
     * Calm: every 3-7 s exactly ONE thing pops in a single tick (the head tilts 25-40 degrees, a
     * hand clenches, a shoulder hitches up, or the spine jerks 1 px sideways), then it either
     * holds 10-40 ticks and snaps back, or creeps back over 20-40 ticks. Caging: the same clock
     * makes a hand re-grip, in and out in two ticks. Running: every 1.5-3 s the head tilts 20-30
     * degrees in a tick and creeps back over 10, or an arm skips down for one tick; and on its
     * own clock every 1-2 s one hand snatches shut at the air for 2 ticks.
     */
    private void glitch(int seed, float t, int state) {
        int gs = seed + BacteriaRhythm.GLITCH_SEED;
        if (state == CHASE) {
            int b = BacteriaRhythm.slot(gs, t, BacteriaRhythm.CHASE_GLITCH_PERIOD, BacteriaRhythm.CHASE_GLITCH_JITTER);
            float a = t - BacteriaRhythm.slotStart(gs, b, BacteriaRhythm.CHASE_GLITCH_PERIOD, BacteriaRhythm.CHASE_GLITCH_JITTER);
            float sign = BacteriaRhythm.hash(gs + 205, b) > 0.0f ? 1.0f : -1.0f;
            if (BacteriaRhythm.unit(gs + 210, b) < 0.6f) {
                float e = a < 1.0f ? a : 1.0f - smooth((a - 1.0f) / 10.0f);
                headRoll += sign * (20.0f + 10.0f * BacteriaRhythm.unit(gs + 201, b)) * DEG * e;
            } else {
                raise[sign > 0.0f ? 1 : 0] -= 12.0f * DEG * blip(a);
            }
            int grab = gs + 77;
            int g = BacteriaRhythm.slot(grab, t, 30.0f, 10.0f);
            float ga = t - BacteriaRhythm.slotStart(grab, g, 30.0f, 10.0f);
            int hand = BacteriaRhythm.hash(grab + 1, g) > 0.0f ? 1 : 0;
            float ge = ga < 1.0f ? ga : Math.max(0.0f, 2.0f - ga);
            clench[hand] += 70.0f * ge;
            curl[hand] += 25.0f * DEG * ge;
            return;
        }
        int b = BacteriaRhythm.slot(gs, t, BacteriaRhythm.CALM_GLITCH_PERIOD, BacteriaRhythm.CALM_GLITCH_JITTER);
        float a = t - BacteriaRhythm.slotStart(gs, b, BacteriaRhythm.CALM_GLITCH_PERIOD, BacteriaRhythm.CALM_GLITCH_JITTER);
        float sign = BacteriaRhythm.hash(gs + 205, b) > 0.0f ? 1.0f : -1.0f;
        int side = sign > 0.0f ? 1 : 0;
        float size = BacteriaRhythm.unit(gs + 201, b);
        if (state == CAGE) {
            float e = a < 1.0f ? a : Math.max(0.0f, 2.0f - a);
            clench[side] += 50.0f * e;
            bend[side] += 6.0f * DEG * e;
            return;
        }
        boolean creep = BacteriaRhythm.hash(gs + 202, b) > 0.0f;
        float hold = 10.0f + 30.0f * BacteriaRhythm.unit(gs + 203, b);
        float back = 20.0f + 20.0f * BacteriaRhythm.unit(gs + 204, b);
        float e = a < 1.0f ? a : creep ? 1.0f - smooth((a - 1.0f) / back) : a < 1.0f + hold ? 1.0f : Math.max(0.0f, 2.0f + hold - a);
        switch (BacteriaRhythm.glitchChannel(gs, b)) {
            case BacteriaRhythm.HEAD_ROLL -> headRoll += sign * (25.0f + 15.0f * size) * DEG * e;
            case BacteriaRhythm.CLENCH -> clench[side] += (40.0f + 20.0f * size) * e;
            case BacteriaRhythm.SHOULDER -> hitch[side] = 1.5f * e;
            default -> spineShift = sign * e;
        }
    }

    /** 1 for one tick, then back to 0 over the next: a single-tick skip. */
    private static float blip(float a) {
        return a < 1.0f ? 1.0f : Math.max(0.0f, 2.0f - a);
    }

    /**
     * Fingers carry the life: each finger slowly curls and uncurls on its own 30-80 tick cycle by
     * 15-35 degrees, plus any clench from a glitch. When they hold still they stay at fingerRest.
     */
    private void fingers(int seed, float t) {
        for (int i = 0; i < 2; i++) {
            for (int f = 0; f < 3; f++) {
                float c = fingerRest;
                if (fingersLive) {
                    int s = seed + 400 + 3 * i + f;
                    float period = 30.0f + 50.0f * BacteriaRhythm.unit(s, 0);
                    float amp = 15.0f + 20.0f * BacteriaRhythm.unit(s, 1);
                    c = amp * (0.5f - 0.5f * Mth.cos(Mth.TWO_PI * (t / period + BacteriaRhythm.unit(s, 2))));
                }
                fingerCurl[i][f] = c + clench[i];
            }
        }
    }

    // ------------------------------------------------------------------ weight: the arms

    /**
     * The arms as dead weight. In the calm and running poses the angles built above are only
     * where each arm would like to hang; the arm itself is a damped spring pulled toward it, so it
     * swings there late (3-6 ticks behind), overshoots once and settles. When the body speeds up
     * or brakes, the arms swing back or forward; when it turns, they swing out. Moving, they trail
     * behind. In the attack poses the arms are posed exactly (and the springs simply follow).
     * Frozen when it has spotted you, they drop heavily to hanging, with no swing and no shove.
     */
    private void swingArms(Bacteria entity, BacteriaPoseState memory, float partial, float dt, boolean fresh) {
        // How the body moves: its speed forward and its turning, from frame to frame.
        double x = Mth.lerp(partial, entity.xo, entity.getX());
        double z = Mth.lerp(partial, entity.zo, entity.getZ());
        float yaw = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
        float forwardAccel = 0.0f;
        float turnAccel = 0.0f;
        if (fresh) {
            memory.lastForwardSpeed = 0.0f;
            memory.lastYawRate = 0.0f;
        } else if (dt > 1.0e-3f) {
            float r = yaw * DEG;
            float forward = (float) (((x - memory.lastX) * -Mth.sin(r) + (z - memory.lastZ) * Mth.cos(r)) / dt);
            float yawRate = Mth.wrapDegrees(yaw - memory.lastYaw) / dt;
            forwardAccel = Mth.clamp((forward - memory.lastForwardSpeed) / dt, -0.2f, 0.2f);
            turnAccel = Mth.clamp((yawRate - memory.lastYawRate) / dt, -30.0f, 30.0f) * DEG;
            memory.lastForwardSpeed = forward;
            memory.lastYawRate = yawRate;
        }
        memory.lastX = x;
        memory.lastZ = z;
        memory.lastYaw = yaw;

        if (armMode == ARMS_DIRECT || fresh || !memory.armsSet) {
            for (int i = 0; i < 2; i++) {
                memory.raise[i] = raise[i];
                memory.out[i] = out[i];
                memory.bend[i] = bend[i];
                memory.raiseSpeed[i] = 0.0f;
                memory.outSpeed[i] = 0.0f;
                memory.bendSpeed[i] = 0.0f;
            }
            memory.armsSet = true;
            return;
        }
        if (dt > 0.0f) {
            boolean running = armMode == ARMS_CHASE;
            boolean settling = armMode == ARMS_SETTLE;
            float period = running ? CHASE_ARM_PERIOD : settling ? SETTLE_ARM_PERIOD : CALM_ARM_PERIOD;
            float dampingRatio = running ? CHASE_ARM_DAMPING : settling ? SETTLE_ARM_DAMPING : CALM_ARM_DAMPING;
            float w = Mth.TWO_PI / period;
            float stiffness = w * w;
            float damping = 2.0f * dampingRatio * w;
            float trail = settling ? 0.0f : (running ? CHASE_ARM_DRAG : ARM_DRAG) * memory.lastForwardSpeed;
            float shove = settling ? 0.0f : ARM_SHOVE * forwardAccel;
            float turn = settling ? 0.0f : ARM_TURN * turnAccel;
            int steps = Mth.ceil(dt / 0.5f);
            float h = dt / steps;
            for (int i = 0; i < 2; i++) {
                float side = i == 0 ? -1.0f : 1.0f;
                float raiseGoal = raise[i] - trail;
                float bendGoal = bend[i] + 0.5f * trail;
                for (int k = 0; k < steps; k++) {
                    memory.raiseSpeed[i] += (stiffness * (raiseGoal - memory.raise[i]) - damping * memory.raiseSpeed[i] - shove) * h;
                    memory.raise[i] += memory.raiseSpeed[i] * h;
                    memory.outSpeed[i] += (stiffness * (out[i] - memory.out[i]) - damping * memory.outSpeed[i] + side * turn) * h;
                    memory.out[i] += memory.outSpeed[i] * h;
                    memory.bendSpeed[i] += (stiffness * (bendGoal - memory.bend[i]) - damping * memory.bendSpeed[i] + 0.5f * shove) * h;
                    memory.bend[i] += memory.bendSpeed[i] * h;
                }
            }
        }
        for (int i = 0; i < 2; i++) {
            raise[i] = memory.raise[i];
            out[i] = memory.out[i];
            bend[i] = memory.bend[i];
        }
    }

    // ------------------------------------------------------------------ intent: the head

    /**
     * Aims the head. Locked on someone, it points at their eyes in the WORLD: the angle is worked
     * out from where its head really is this frame, and then everything the spine and neck are
     * doing is taken back off, so the body can lurch and pump underneath while the head floats,
     * dead still and aimed (the scariest thing in the footage). It may turn up to 150 degrees
     * from where the body faces. How quickly it gets onto its target depends on the state (slowly
     * while calm, a 1-2 tick snap when it spots you, exactly while it runs or holds you). With
     * nobody to look at it holds its own direction (headYawFree / headPitchFree).
     */
    private void aimHead(Bacteria entity, BacteriaPoseState memory, float partial, float dt, boolean fresh, int targetId) {
        float bodyYaw = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
        // The rotation the head inherits: spine, then neck.
        rotation(matA, sway, 0.0f, lean);
        rotation(matB, neckZ, 0.0f, neckX);
        multiply(matParent, matA, matB);

        Entity target = aimMode != AIM_NONE && targetId >= 0 ? entity.level().getEntity(targetId) : null;
        float wantYaw;
        float wantPitch;
        float rate;
        if (target != null) {
            // Where its head is in model space: hips, up the spine, the neck, about 4.6 px up it.
            float hipY = 24.0f - HIP_HEIGHT + drop + bob;
            float nx = matA[1] * -SPINE + matA[2] * -neckForward;
            float ny = hipY + matA[4] * -SPINE + matA[5] * -neckForward;
            float nz = matA[7] * -SPINE + matA[8] * -neckForward;
            float hz = -0.4f - headForward;
            float px = nx + matParent[1] * -4.6f + matParent[2] * hz;
            float py = ny + matParent[4] * -4.6f + matParent[5] * hz;
            float pz = nz + matParent[7] * -4.6f + matParent[8] * hz;
            // ...and in the world (the renderer turns the model to the body's yaw and flips it).
            float r = bodyYaw * DEG;
            float cos = Mth.cos(r);
            float sin = Mth.sin(r);
            double worldX = Mth.lerp(partial, entity.xo, entity.getX()) + (px * cos + pz * sin) / 16.0f;
            double worldY = Mth.lerp(partial, entity.yo, entity.getY()) + (24.0f - py) / 16.0f;
            double worldZ = Mth.lerp(partial, entity.zo, entity.getZ()) + (px * sin - pz * cos) / 16.0f;
            double dx = Mth.lerp(partial, target.xo, target.getX()) - worldX;
            double dy = Mth.lerp(partial, target.yo, target.getY()) + target.getEyeHeight() - worldY;
            double dz = Mth.lerp(partial, target.zo, target.getZ()) - worldZ;
            wantYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
            wantPitch = (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
            if (aimMode == LOCK_EXACT) {
                rate = Float.MAX_VALUE;
            } else if (aimMode == LOCK_FAST) {
                rate = AIM_FAST;
            } else {
                boolean onTarget = Math.abs(Mth.wrapDegrees(wantYaw - memory.aimYaw)) + Math.abs(wantPitch - memory.aimPitch) < 4.0f;
                rate = onTarget ? AIM_TRACK : AIM_SLOW;
            }
        } else {
            wantYaw = bodyYaw + headYawFree;
            wantPitch = headPitchFree;
            rate = AIM_FREE;
        }
        if (fresh || !memory.aimed) {
            memory.aimYaw = wantYaw;
            memory.aimPitch = wantPitch;
            memory.aimed = true;
        } else {
            float step = rate == Float.MAX_VALUE ? 360.0f : rate * dt;
            memory.aimYaw = Mth.approachDegrees(memory.aimYaw, wantYaw, step);
            memory.aimPitch = Mth.approach(memory.aimPitch, wantPitch, step);
        }
        float yaw = Mth.clamp(Mth.wrapDegrees(memory.aimYaw - bodyYaw), -HEAD_YAW_LIMIT, HEAD_YAW_LIMIT);
        float pitch = Mth.clamp(memory.aimPitch, -70.0f, 85.0f);
        if (freeWeight > 0.0f) {
            yaw = Mth.lerp(freeWeight, yaw, headYawFree);
            pitch = Mth.lerp(freeWeight, pitch, headPitchFree);
        }
        // Where the head should point, in the body's space: turn, then look down, then tilt about
        // its own nose. Take off what it inherits, and read the head's own angles back out.
        rotation(matA, 0.0f, yaw * DEG, pitch * DEG);
        rotation(matB, headRoll, 0.0f, 0.0f);
        multiply(matWant, matA, matB);
        multiplyTransposed(matA, matParent, matWant);
        headX = (float) Mth.atan2(matA[7], matA[8]);
        headY = (float) Math.asin(Mth.clamp(-matA[6], -1.0f, 1.0f));
        headZ = (float) Mth.atan2(matA[3], matA[0]);
    }

    /** {@code m} = the rotation a ModelPart with these zRot, yRot, xRot applies (Z, then Y, then X). */
    private static void rotation(float[] m, float z, float y, float x) {
        float cz = Mth.cos(z);
        float sz = Mth.sin(z);
        float cy = Mth.cos(y);
        float sy = Mth.sin(y);
        float cx = Mth.cos(x);
        float sx = Mth.sin(x);
        m[0] = cz * cy;
        m[1] = cz * sy * sx - sz * cx;
        m[2] = cz * sy * cx + sz * sx;
        m[3] = sz * cy;
        m[4] = sz * sy * sx + cz * cx;
        m[5] = sz * sy * cx - cz * sx;
        m[6] = -sy;
        m[7] = cy * sx;
        m[8] = cy * cx;
    }

    /** {@code out} = a * b. */
    private static void multiply(float[] out, float[] a, float[] b) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                out[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
            }
        }
    }

    /** {@code out} = (a turned back the other way) * b. */
    private static void multiplyTransposed(float[] out, float[] a, float[] b) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                out[r * 3 + c] = a[r] * b[c] + a[3 + r] * b[3 + c] + a[6 + r] * b[6 + c];
            }
        }
    }

    // ------------------------------------------------------------------ pose helpers

    /**
     * Starts a pose: knees bent by these angles (more under a low ceiling), the hips dropped to
     * match, and everything else back to neutral.
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
        spineShift = 0.0f;
        neckX = 0.0f;
        neckZ = 0.0f;
        neckForward = 0.0f;
        headRoll = 0.0f;
        headForward = 0.0f;
        headYawFree = 0.0f;
        headPitchFree = 0.0f;
        freeWeight = 0.0f;
        aimMode = AIM_NONE;
        armMode = ARMS_CALM;
        fingersLive = true;
        fingerRest = 10.0f;
        for (int i = 0; i < 2; i++) {
            raise[i] = 0.0f;
            out[i] = 0.0f;
            splay[i] = 0.0f;
            bend[i] = 0.0f;
            foreOut[i] = 0.0f;
            curl[i] = 0.0f;
            spread[i] = 0.0f;
            hitch[i] = 0.0f;
            clench[i] = 0.0f;
        }
    }

    /** The legs' stride: thighs swing {@code thigh} each way, the knee folds up by {@code shin} as the leg swings forward. */
    private void stride(float m, float thigh, float shin, float gait) {
        float swing = Mth.sin(gait);
        float lift = Mth.cos(gait);
        thighR += thigh * swing * m;
        thighL -= thigh * swing * m;
        shinR += m * shin * Math.max(0.0f, -lift);
        shinL += m * shin * Math.max(0.0f, lift);
    }

    /**
     * Arms hanging as dead weight, claws just above the floor and 0.9 blocks either side of its
     * middle, lopsided: the arm on the low shoulder rests slightly bent the wrong way at the
     * elbow, the other one a little bent.
     */
    private void hangingArms() {
        for (int i = 0; i < 2; i++) {
            raise[i] = reachFor(i);
            out[i] = 0.0f;
            splay[i] = 0.0f;
            bend[i] = (i == lowArm ? 7.0f : -8.0f) * DEG;
            curl[i] = 8.0f * DEG;
            spread[i] = 6.0f * DEG;
        }
    }

    /**
     * Keeps both claws within {@code maxBlocks} of its middle, out to the side, measured in the
     * world (whatever the spine's lean does to the arm angles). If a claw is further out, the arm
     * is turned back in: swung in (out) if the arm reaches forward, tilted in (splay) if it hangs.
     */
    private void limitSpread(float maxBlocks) {
        float limit = maxBlocks * 16.0f;
        for (int i = 0; i < 2; i++) {
            for (int k = 0; k < 3; k++) {
                measureClaw(i);
                float excess = clawSide - limit;
                if (excess <= 0.0f) {
                    break;
                }
                float lever = Math.abs(clawAhead - SPINE * Mth.sin(lean));
                if (lever >= 12.0f) {
                    out[i] -= excess / lever;
                } else {
                    splay[i] -= excess / ARM_REACH;
                }
            }
        }
    }

    /**
     * Where arm {@code i}'s claw tips are, from the hips, in px: {@link #clawSide} out to that
     * arm's side, {@link #clawAhead} ahead. The same chain of joints the model parts use.
     */
    private void measureClaw(int i) {
        float side = i == 0 ? -1.0f : 1.0f;
        rotation(matSpine, sway, 0.0f, lean);
        rotation(matA, -side * splay[i], -side * out[i], -(raise[i] + lean));
        multiply(matArm, matSpine, matA);
        rotation(matA, -side * foreOut[i], 0.0f, bend[i]);
        multiply(matFore, matArm, matA);
        rotation(matA, 0.0f, 0.0f, curl[i]);
        multiply(matHand, matFore, matA);
        // The shoulder: up the spine to the shoulder bar, out to this side (and the lopsided offset).
        float sy = -SPINE + (i == lowArm ? LOW_SHOULDER_DROP : 0.0f) - hitch[i];
        float sz = i == lowArm ? -LOW_SHOULDER_FORWARD : 0.0f;
        float x = spineShift + matSpine[0] * side * SHOULDER_HALF + matSpine[1] * sy + matSpine[2] * sz;
        float z = matSpine[6] * side * SHOULDER_HALF + matSpine[7] * sy + matSpine[8] * sz;
        // Upper arm, forearm, then the middle claw (the same points the arm parts are built from).
        x += matArm[0] * side * 1.5f + matArm[1] * UPPER_ARM;
        z += matArm[6] * side * 1.5f + matArm[7] * UPPER_ARM;
        x += matFore[0] * side * 1.5f + matFore[1] * FOREARM - matFore[2] * 0.5f;
        z += matFore[6] * side * 1.5f + matFore[7] * FOREARM - matFore[8] * 0.5f;
        x += matHand[0] * side * 0.9f + matHand[1] * 4.6f;
        z += matHand[6] * side * 0.9f + matHand[7] * 4.6f;
        clawSide = side * x;
        clawAhead = -z;
    }

    /** Its own slight head tilt, the same side and size every time (10-20 degrees). */
    private static float idleTilt(int seed) {
        return (BacteriaRhythm.hash(seed, 2) > 0.0f ? 1.0f : -1.0f) * (10.0f + 10.0f * BacteriaRhythm.unit(seed, 3)) * DEG;
    }

    /** How far the hips sink (px) with the thigh and the shin (relative to the thigh) bent so. */
    private static float legDrop(float thigh, float shin) {
        return THIGH + SHIN - (THIGH * Mth.cos(thigh) + SHIN * Mth.cos(thigh + shin));
    }

    /** Under a low ceiling it has to lean at least this far forward to fit. */
    private void fitLean() {
        lean = Math.max(lean, ceilingLean);
    }

    /**
     * How far forward arm {@code i} must hang for its claws to stay just above the floor instead
     * of sinking into it (that is what makes it look like it walks on crutches when it hunches).
     */
    private float reachFor(int i) {
        float shoulder = hips + SPINE * Mth.cos(lean) - (i == lowArm ? LOW_SHOULDER_DROP : 0.0f) - HAND_HOVER;
        return (float) Math.acos(Mth.clamp(shoulder / ARM_REACH, 0.0f, 1.0f));
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
        spine.x += spineShift;
        neck.xRot = neckX;
        neck.zRot = neckZ;
        neck.z -= neckForward;
        head.xRot = headX;
        head.yRot = headY;
        head.zRot = headZ;
        head.z -= headForward;
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? -1.0f : 1.0f; // the right arm is on the -X side
            if (i == lowArm) {
                arms[i].y += LOW_SHOULDER_DROP;
                arms[i].z -= LOW_SHOULDER_FORWARD;
            }
            arms[i].y -= hitch[i];
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
                fingers[i][f].xRot = -fingerCurl[i][f] * DEG;
            }
        }
    }

    /**
     * The organs move smoothly, so they read as alive inside the still wires. The heart's speed
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

    // ------------------------------------------------------------------ timing

    /**
     * Its own head direction (degrees, lo to hi either way): held, then at the start of every slot
     * (period +- jitter ticks) turned to a new one, half the time slowly over SLOW_TURN_TICKS
     * (eased in and out), half the time in a single tick.
     */
    private static float holdTurn(int seed, float t, float period, float jitter, float lo, float hi) {
        int b = BacteriaRhythm.slot(seed, t, period, jitter);
        float a = t - BacteriaRhythm.slotStart(seed, b, period, jitter);
        float ease = BacteriaRhythm.hash(seed + 7, b) > 0.0f ? SLOW_TURN_TICKS : 1.0f;
        float from = spread(BacteriaRhythm.hash(seed, b - 1), lo, hi);
        float to = spread(BacteriaRhythm.hash(seed, b), lo, hi);
        return from + (to - from) * smooth(a / ease);
    }

    /**
     * A slow creep (degrees, lo to hi either way): every 35-55 ticks a new value, reached over 25
     * ticks. Used for the caging claws walking along the floor.
     */
    private static float crawl(int seed, float t, float lo, float hi) {
        int b = BacteriaRhythm.slot(seed, t, 45.0f, 10.0f);
        float a = t - BacteriaRhythm.slotStart(seed, b, 45.0f, 10.0f);
        float from = spread(BacteriaRhythm.hash(seed, b - 1), lo, hi);
        float to = spread(BacteriaRhythm.hash(seed, b), lo, hi);
        return from + (to - from) * smooth(a / 25.0f);
    }

    /**
     * A sudden move now and then (degrees, always positive): at the start of every slot it goes to
     * lo..hi in {@code rise} ticks, holds {@code hold} ticks and comes back in {@code fall}.
     */
    private static float pulse(int seed, float t, float period, float jitter, float rise, float hold, float fall, float lo, float hi) {
        int b = BacteriaRhythm.slot(seed, t, period, jitter);
        float a = t - BacteriaRhythm.slotStart(seed, b, period, jitter);
        float e = a < rise ? smooth(a / rise) : a < rise + hold ? 1.0f : 1.0f - smooth((a - rise - hold) / fall);
        return e * (lo + (hi - lo) * BacteriaRhythm.unit(seed + 1, b));
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

    /** Lunge timeline: from {@code base} to the keys at ticks 2, 5, 7 and 9, then back to base by 12. */
    private static float key(float a, float base, float k2, float k5, float k7, float k9) {
        if (a < 2.0f) {
            return Mth.lerp(smooth(a / 2.0f), base, k2);
        }
        if (a < 5.0f) {
            return Mth.lerp(smooth((a - 2.0f) / 3.0f), k2, k5);
        }
        if (a < 7.0f) {
            return Mth.lerp(smooth((a - 5.0f) / 2.0f), k5, k7);
        }
        if (a < 9.0f) {
            return Mth.lerp(smooth((a - 7.0f) / 2.0f), k7, k9);
        }
        return Mth.lerp(smooth((a - 9.0f) / 3.0f), k9, base);
    }
}
