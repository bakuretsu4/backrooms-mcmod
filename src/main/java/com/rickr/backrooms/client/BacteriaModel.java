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
 * <p>Size: standing up straight the model is 54 px (3.4 blocks) tall, but the renderer draws it at
 * {@link Bacteria#BODY_SCALE} (2.5 blocks) so its head clears the 3-block ceilings, and the hitbox
 * matches. Under any lower ceiling (all of Level 2) the model stoops: it bends forward at the hips
 * and bends its knees until the head clears the ceiling. {@link Bacteria#headroom} tells it how
 * much space there is.
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
    // Pass 5: the arms (and only the arms) are 1.5 times as long as the reference model's, so
    // that, like the Bacteria in the owner's reference video, it can hold them as big spider legs
    // (elbow up and out, forearm down to a claw on the floor far out to the side). Everything
    // along the arm (the bones, the muscle on them, the hand and claws) is stretched by this;
    // nothing gets thicker.
    private static final float ARM_STRETCH = 1.5f;
    private static final float UPPER_ARM = 21.0f * ARM_STRETCH;  // 31.5 px, 1.48 blocks as drawn
    private static final float FOREARM = 20.0f * ARM_STRETCH;    // 30 px, 1.41 blocks as drawn
    private static final float CLAW_KNUCKLE = 2.6f * ARM_STRETCH; // wrist to the claws' middle bend
    private static final float CLAW_TIP = 4.6f * ARM_STRETCH;     // wrist to the claw tips (6.9 px)
    /** Shoulder to claw tips, arm hanging straight (68.4 px, 3.2 blocks as drawn). */
    private static final float ARM_REACH = UPPER_ARM + FOREARM + CLAW_TIP;
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
        // The bones are stretched (ARM_STRETCH) but keep the same number of wire pieces and
        // twists, so they draw exactly the same random numbers as before: the legs and body that
        // are built after the arms come out exactly as they always did.
        braid(arm, "upper", rng, 0, 0, 0, side * 1.5f, UPPER_ARM, 0, 3, 1.4f, 14.0f, WIRE, ARM_STRETCH);
        PartDefinition forearm = arm.addOrReplaceChild("forearm", CubeListBuilder.create(), PartPose.offset(side * 1.5f, UPPER_ARM, 0));
        braid(forearm, "lower", rng, 0, 0, 0, side * 1.5f, FOREARM, -0.5f, 3, 1.3f, 14.0f, WIRE, ARM_STRETCH);
        // Three claw fingers, splayed like a rake, each with a slight bend in the middle. They
        // hang off a "hand" at the wrist, one "finger" part each, so the animation can curl the
        // hand and spread the claws. The points are measured from the wrist (wx, FOREARM, -0.5);
        // the whole hand is ARM_STRETCH times its old size, the wires just as thin.
        float wx = side * 1.5f;
        PartDefinition hand = forearm.addOrReplaceChild("hand", CubeListBuilder.create(), PartPose.offset(wx, FOREARM, -0.5f));
        for (int f = -1; f <= 1; f++) {
            float mx = (f * 1.1f + side * 0.4f) * ARM_STRETCH;
            float mz = (0.2f + f * 0.3f) * ARM_STRETCH;
            float tx = (f * 2.4f + side * 0.9f) * ARM_STRETCH;
            float tz = (-f * f * 0.8f + f * 0.6f) * ARM_STRETCH;
            PartDefinition finger = hand.addOrReplaceChild("finger" + (f + 1), CubeListBuilder.create(), PartPose.ZERO);
            wire(finger, "claw_a", 0.0f, 0.0f, 0.0f, mx, CLAW_KNUCKLE, mz, WIRE * 0.8f);
            wire(finger, "claw_b", mx, CLAW_KNUCKLE, mz, tx, CLAW_TIP, tz, WIRE * 0.7f);
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
        // The muscle masses sit at the same places along the (stretched) bone and are stretched
        // with it, but no thicker. The deltoid stays at the shoulder.
        float k = ARM_STRETCH;
        float ex = side * 1.5f;
        blob(arm, "flesh_deltoid", 0, 44, rng, side * 0.5f, 1.4f, 0.0f, 3.4f, 3.0f, 3.0f, 0.1f, 0.0f, -side * 0.25f);
        blob(arm, "flesh_upper", 0, 44, rng, side * 0.8f, 6.0f * k, -0.3f, 2.3f, 6.5f * k, 2.1f, 0.08f, 0.2f, -side * 0.08f);
        blob(arm, "flesh_upper_taper", 0, 44, rng, side * 1.2f, 11.8f * k, 0.1f, 1.4f, 5.0f * k, 1.3f, -0.05f, 0.0f, side * 0.04f);
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
            float mx = (f * 1.1f + side * 0.4f) * k;
            blob(hand.getChild("finger" + (f + 1)), "flesh_knuckle", 0, 0, rng, mx, CLAW_KNUCKLE, (0.2f + f * 0.3f) * k, 0.9f, 0.9f, 0.9f, 0.0f, 0.0f, 0.0f);
        }
        wire(hand, "flesh_thumb_a", 0.0f, 0.0f, 0.0f, -side * 0.6f * k, 1.6f * k, 1.5f * k, WIRE * 0.8f);
        wire(hand, "flesh_thumb_b", -side * 0.6f * k, 1.6f * k, 1.5f * k, -side * 0.9f * k, 3.6f * k, 2.1f * k, WIRE * 0.7f);
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
        braid(parent, name, rng, ax, ay, az, bx, by, bz, strands, radius, period, thickness, 1.0f);
    }

    /**
     * The same, for a bone {@code stretch} times longer than it was first laid out: the twists
     * and the wire pieces are that much longer too, so there are just as many of them (and the
     * same random numbers are used) and the bone looks the same, only longer.
     */
    private static void braid(PartDefinition parent, String name, RandomSource rng,
                              float ax, float ay, float az, float bx, float by, float bz,
                              int strands, float radius, float period, float thickness, float stretch) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        float[] dir = {dx / length, dy / length, dz / length};
        // Two directions at right angles to the bone, to twist around.
        float[] u = perpendicular(dir[0], dir[1], dir[2]);
        float[] v = cross(dir[0], dir[1], dir[2], u);
        int twists = Math.max(1, Math.round(length / (period * stretch)));
        // Pieces are about 2.5 px long (times the stretch), not tied to the twist, so the kinks
        // fall unevenly.
        int pieces = Math.max(4, Math.round(length / (2.5f * stretch)));
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
    // Pass 3: it moves like the Bacteria in the owner's reference video (PixelNaut's "The NEW
    // Backrooms Add-ON is INCREDIBLY SCARY", the XP Games add-on), worked out frame by frame in
    // local://bacteria_ref2_motion.md:
    //  - It stands tall and upright, leaning on its long arms like two crutches, claws on the
    //    floor in front of it. Only the claws slide slowly back and forth.
    //  - It walks upright on wide-apart legs, its arms held out to the sides like a spider's, the
    //    elbows high, each claw planted on the floor and re-planted on its own rhythm.
    //  - It RUNS upright too: short, very quick high-knee steps under its body (a 5-tick stride),
    //    while its arms vault it along like spider legs: one claw planted out to the side and
    //    ahead, the other whipped up and over the top to plant further on.
    //  - Seeing you, it doesn't freeze: it bends right over to look, swings round and runs.
    //    Close in, it stops dead in a wide stance, stares for a moment, then leaps at you with its
    //    arms flung straight out, into the cage of arms.
    //  - Its head stays locked on you (aimHead), aimed at your eyes in the world.
    // Angles: degrees in the constants (times DEG = radians); time in ticks (20 a second). Model
    // space: +Y is down, the front is -Z, +X is its left. Arm keyframes were checked with a
    // joint-by-joint simulation of this model (claw positions are given in blocks from the hips:
    // out to its side, ahead, up).
    //
    // setupAnim runs every frame for every Bacteria on screen, so it never creates objects: only
    // numbers, the fields below, the arrays made once with the model, and each Bacteria's own
    // BacteriaPoseState (made once with the entity).

    private static final float DEG = Mth.DEG_TO_RAD;
    /**
     * The renderer draws the whole model at this size (so its head clears a 3-block ceiling); the
     * numbers in this class are still in the model's own pixels. Anything measured in the world
     * (where its head is, how far out its claws are) is multiplied by it.
     */
    private static final float SCALE = Bacteria.BODY_SCALE;
    // How far its spine leans forward (degrees). Upright, slightly forward; it never hunches.
    private static final float IDLE_LEAN = 10.0f;
    private static final float WALK_LEAN = 15.0f;
    private static final float RUN_LEAN = 15.0f;
    private static final float RUN_NEAR_LEAN = 18.0f;   // in the last 3 blocks before the leap
    private static final float TURN_LEAN = 60.0f;       // bending right over to look, when it spots you
    private static final float STARE_LEAN = 5.0f;
    private static final float LEAP_LEAN = 20.0f;
    private static final float CAGE_LEAN = 25.0f;
    /** The slow calm breath moves the spine this much (degrees). */
    private static final float BREATH_DEG = 1.0f;
    /**
     * The vulture neck: pushed forward of the chest (px) and angled forward (degrees), so the
     * head box sits out in front of the chest. The head lock tips the head itself back to face you.
     */
    private static final float NECK_FORWARD = 2.0f;
    private static final float NECK_TILT = 32.0f;
    /** Lopsided: its low shoulder sits this much lower (px) and further forward (px). */
    private static final float LOW_SHOULDER_DROP = 1.25f;
    private static final float LOW_SHOULDER_FORWARD = 0.8f;
    // Head: with nobody to look at it looks this far down (degrees). It always holds its head
    // tilted to one side (its own side, 20-32 degrees), the tilt drifting slowly a few degrees;
    // running, the drift is a bit bigger and faster.
    private static final float HEAD_PITCH_DOWN = 15.0f;
    private static final float HEAD_TILT_MIN = 20.0f;
    private static final float HEAD_TILT_MAX = 32.0f;
    private static final float HEAD_ROLL_DRIFT = 6.0f;
    private static final float HEAD_ROLL_TICKS = 60.0f;
    private static final float RUN_HEAD_ROLL = 10.0f;
    private static final float RUN_HEAD_ROLL_TICKS = 25.0f;
    /** Each footfall jolts the head this much down (degrees) and tips it this much to one side, alternately. */
    private static final float STEP_JOLT_PITCH = 3.0f;
    private static final float STEP_JOLT_ROLL = 2.5f;
    // Head lock: how fast the head may turn toward what it looks at (degrees per tick).
    private static final float AIM_SLOW = 1.0f;     // calm: it notices you slowly...
    private static final float AIM_TRACK = 10.0f;   // ...but once its head is on you, it stays on you
    private static final float AIM_FAST = 45.0f;    // spotted: the snap onto you (1-2 ticks)
    private static final float AIM_FREE = 40.0f;    // nobody to look at: its own turns
    /** The head may turn this far from where the body faces. */
    private static final float HEAD_YAW_LIMIT = 150.0f;
    /** Searching, its head turns now and then, either slowly over this many ticks or in one tick. */
    private static final float SLOW_TURN_TICKS = 30.0f;

    // Arm poses are (raise, out, elbow bend, forearm out), in degrees. The claw and elbow positions
    // in the comments are in world blocks at the drawn size, from its middle: (out to its side,
    // ahead, up). Pass 5: every pose below was worked out again for the 1.5 times longer arms
    // (upper arm 1.48 blocks, forearm and claws 1.73) with the joint-by-joint simulation.
    //
    // Standing: both arms bent forward like a mantis's forelegs, the elbows out in front at about
    // chest height (1.6-2.0 up, 1.6 ahead) and the forearms hanging down to the claws on the floor
    // in front of it, slowly cycling through three places: on the floor (0.8 out, 1.1 ahead),
    // reached forward and lifted (1.3 ahead, 0.35 up), drawn back (0.6 ahead, on the floor). One
    // cycle takes 36 ticks; the left arm runs 0.4 of a cycle behind the right.
    private static final float[] IDLE_RAISE = {75.0f, 90.0f, 67.0f};
    private static final float[] IDLE_BEND = {90.0f, 100.0f, 98.0f};
    private static final float IDLE_OUT = 5.0f;
    private static final float IDLE_ARM_TICKS = 36.0f;
    private static final float IDLE_ARM_OFFSET = 0.4f;
    /** Standing, one foot (its own side) is forward of the other: (thigh, shin) of each leg. */
    private static final float IDLE_FRONT_THIGH = -15.0f;
    private static final float IDLE_FRONT_SHIN = 25.0f;
    private static final float IDLE_BACK_THIGH = 2.0f;
    private static final float IDLE_BACK_SHIN = 12.0f;
    // Walking, mantis-like: one arm cocked forward, the elbow up at head height in front (0.8
    // out, 1.7 ahead, 2.3 up) and the forearm hanging down and back so the claws dangle 0.7 above
    // the floor; the other arm a spider leg reaching out and back to the floor (elbow 1.9 out and
    // 1.4 up, claw 1.2 out and 0.4 behind). The two swap every stride, slowly, and the body shifts
    // its weight toward the arm on the floor.
    private static final float WALK_COCK_RAISE = 100.0f;
    private static final float WALK_COCK_OUT = 15.0f;
    private static final float WALK_COCK_BEND = 110.0f;
    private static final float WALK_COCK_FORE = -20.0f;
    private static final float WALK_BACK_RAISE = 60.0f;
    private static final float WALK_BACK_OUT = 80.0f;
    private static final float WALK_BACK_BEND = 100.0f;
    private static final float WALK_BACK_FORE = -20.0f;
    /** Walking, the arms swap over this fraction of the arm cycle, then hold. */
    private static final float WALK_SWAP = 0.15f;
    /** Walking, the body leans this far (degrees) toward the arm on the floor. */
    private static final float WALK_WEIGHT_SHIFT = 4.0f;
    // Running arms are spider legs: big angular "Λ"s out to the sides, the elbow up and out, the
    // forearm down and out to the claw on the floor. Keyframes over one arm cycle (2 strides); the
    // arm plants at 0:
    //   0    plant      elbow 1.8 out, 1.8 up (the shoulder is 1.94 up); claw 2.1 out, 0.6 ahead,
    //                   on the floor; the elbow a sharp 98 degrees
    //   0.25 push       the claw drags back in under the body (1.4 out) and lifts
    //   0.40 over-swing whipped up over the top: elbow well above the shoulder (2.6 up, 1.2 out),
    //                   claw 2.1 out, 1.2 up
    //   0.55 reach      the Λ in the air, out wide: elbow 1.65 out, 2.15 up; claw 2.45 out, 0.65 up
    //   0.80 descend    on its way down to the plant
    // Claws 1.4-2.45 out over the cycle, 0-1.3 up; the highest point (an elbow) is 2.7.
    private static final float[] RUN_ARM_PHASE = {0.0f, 0.25f, 0.40f, 0.55f, 0.80f, 1.0f};
    private static final float[] RUN_ARM_RAISE = {78.0f, 78.0f, 120.0f, 90.0f, 84.0f, 78.0f};
    private static final float[] RUN_ARM_OUT = {60.0f, 45.0f, 40.0f, 55.0f, 55.0f, 60.0f};
    private static final float[] RUN_ARM_BEND = {82.0f, 120.0f, 115.0f, 80.0f, 80.0f, 82.0f};
    private static final float[] RUN_ARM_FORE = {-20.0f, -20.0f, -40.0f, 30.0f, 20.0f, -20.0f};
    // With a wall close on that side (Bacteria.NARROW_CHECK), the claw plants ahead instead (1.0
    // out, 1.3 ahead), the elbow up in front.
    private static final float NARROW_RAISE = 85.0f;
    private static final float NARROW_OUT = 10.0f;
    private static final float NARROW_BEND = 100.0f;
    // Each run one arm is different: either it plants further out and ahead...
    private static final float ASYM_RAISE = 2.0f;
    private static final float ASYM_BEND = -10.0f;
    // ...or it is carried late (a fraction of the arm cycle).
    private static final float ASYM_DELAY_MIN = 0.2f;
    private static final float ASYM_DELAY_MAX = 0.4f;
    // Legs: keyframes over one stride for one leg (the other leg half a stride later):
    // contact, then 25%, 50%, 75% (thigh: - = forward; shin: + = the knee folds).
    // Running: a low scurry on bent knees (so the shoulders come down to the level of the planted
    // elbows): the foot lands under the hip, the stance leg sweeps back, the foot folds up behind,
    // then the knee is driven up high in front.
    private static final float[] RUN_THIGH = {-35.0f, 5.0f, -15.0f, -70.0f};
    private static final float[] RUN_SHIN = {55.0f, 50.0f, 100.0f, 95.0f};
    // Walking: short steps on knees that stay well bent, like a stool.
    private static final float[] WALK_THIGH = {-28.0f, -10.0f, 12.0f, -35.0f};
    private static final float[] WALK_SHIN = {40.0f, 35.0f, 40.0f, 70.0f};
    /** How far the legs splay out sideways (degrees): feet wide apart. */
    private static final float IDLE_LEG_SPLAY = 6.0f;
    private static final float WALK_LEG_SPLAY = 10.0f;
    private static final float RUN_LEG_SPLAY = 6.0f;
    /** Each foot landing drops the body this far (px), easing back over a tick. */
    private static final float RUN_BOB = 0.8f;
    private static final float WALK_BOB = 0.6f;
    /** Running, the body rocks this far (degrees) toward the leg it stands on, once per stride. */
    private static final float STRIDE_SWAY = 3.0f;
    // Stopped close to you: a wide crouched stance, both arms planted as spider legs (elbows 1.8
    // out and 1.75 up, claws 1.85 out and 0.55 ahead, on the floor).
    private static final float TRIPOD_RAISE = 78.0f;
    private static final float TRIPOD_OUT = 60.0f;
    private static final float TRIPOD_BEND = 82.0f;
    private static final float TRIPOD_FORE = -20.0f;
    // The leap: both arms flung straight out to the sides (a T, for 2-3 ticks only: claws 3.65
    // out, 2.35 up, under the ceiling), head pushed forward. (Leaning 20 degrees, a "raise" of 90
    // would tip the straight arms up to 3.1 blocks: through the ceiling.)
    private static final float LEAP_RAISE = 75.0f;
    private static final float LEAP_OUT = 85.0f;
    private static final float LEAP_HEAD_FORWARD = 3.0f;
    // The swipe: one arm cocked up and out to its side like a mantis's strike (elbow 2.65 up,
    // claws 2.6 out at 1.25 up), swept across the front in 1.5 ticks (claws to 2.0 ahead on the
    // other side, at chest height), then back over 4 ticks.
    private static final float SWIPE_TICKS = 6.0f;
    private static final float SWIPE_COCK_RAISE = 110.0f;
    private static final float SWIPE_COCK_OUT = 65.0f;
    private static final float SWIPE_COCK_BEND = 90.0f;
    private static final float SWIPE_ACROSS_RAISE = 115.0f;
    private static final float SWIPE_ACROSS_OUT = -45.0f;
    private static final float SWIPE_ACROSS_BEND = 85.0f;
    // Hit: head thrown back, torso arched back, arms flung out, for 5 ticks, then on as before.
    // (Only a little raise: the long arms flung up would poke through the ceiling.)
    private static final float HURT_TICKS = 5.0f;
    private static final float HURT_LEAN = -15.0f;
    private static final float HURT_PITCH = -50.0f;
    private static final float HURT_OUT = 30.0f;
    private static final float HURT_RAISE = 5.0f;
    // Spotting you (8 ticks): bends right over with its head rolled and looking down, one arm
    // thrown up and out ahead of it (its claw no higher than 2.55 blocks: under the ceiling),
    // while it swings round; then straightens into the run.
    private static final float TURN_ROLL = 50.0f;
    private static final float TURN_PITCH = 30.0f;
    private static final float TURN_ARM_RAISE = 110.0f;
    private static final float TURN_ARM_OUT = 50.0f;
    private static final float TURN_ARM_BEND = 20.0f;
    // Holding you (from 1.5 blocks away): crouched, leaning 25 degrees, the long arms close round
    // you like a cage. The upper arms run out and down from its shoulders to the elbows beside you,
    // just in front of your face and below it (1.35 out, 1.45 ahead, 1.2 up), so from your camera
    // they rise from the lower corners of the screen up to its shoulders; the forearms go on down
    // past you to the claws on the floor behind you (0.8 either side of you, 1.05 behind you).
    // No part of the arm comes within 1 block of your camera. Checked from the held player's
    // camera at the drawn size.
    private static final float CAGE_RAISE = 55.0f;
    private static final float CAGE_OUT = 33.0f;
    private static final float CAGE_BEND = 9.0f;
    private static final float CAGE_FORE = -55.0f;
    /** Caging, lifting the arm by 1 degree folds the elbow by this much, so the claw slides along the floor. */
    private static final float CAGE_CRAWL_BEND = 1.0f;
    /** Peeking, the arms tilt back against the sideways lean by this fraction of it. */
    private static final float PEEK_ARM_COUNTER = 0.35f;
    /**
     * No claw ever ends up more than this far out to its side (world blocks at the drawn size),
     * except in the cage, the peek, the leap's T and the swipe. The spider-leg arms of the run
     * reach about 1.4-2.45 (2.7 for the odd arm), the walk 1.2-1.65.
     */
    private static final float MAX_SPREAD = 2.8f;
    /** Changing pose (walk to run, run to stop...), the arms move to the new one over this many ticks. */
    private static final float BLEND_TICKS = 3.0f;
    private static final float QUICK_BLEND_TICKS = 2.0f;
    /** Cage: the head's slow creep toward your face, snap in, hold and slow pull back: one cycle. */
    private static final float CAGE_HEAD_CYCLE = 110.0f;
    /** Test command, cage: the first whip comes at this tick, then (with loop) every WHIP_LOOP_TICKS. */
    private static final float WHIP_FIRST = 30.0f;
    private static final float WHIP_LOOP_TICKS = 50.0f;
    /** Test command, chase with loop: a swipe at this tick of every 2-second loop. */
    private static final float CHASE_SWIPE_AT = 20.0f;
    /** Test command with "loop": the feed replays this often; the peek alternates a normal and a long one. */
    private static final float FEED_LOOP_TICKS = Bacteria.FEED_TICKS + 18.0f;
    private static final float PEEK_LOOP_TICKS = Bacteria.PEEK_TICKS + 20.0f;
    private static final float PEEK_HOLD_LOOP_TICKS = Bacteria.PEEK_HOLD_TICKS + 20.0f;

    /** How far the legs splay out sideways this frame (radians). */
    private float legSplay;

    // What it is doing, as far as the model is concerned.
    private static final int CALM = 0;
    private static final int CHASE = 1;
    private static final int SEARCH = 2;
    private static final int SPOTTED = 3;
    private static final int CAGE = 4;
    private static final int FEED = 5;
    private static final int PEEK = 6;
    private static final int STARE = 7;
    private static final int LEAP = 8;
    // How the head is aimed: its own way, or locked on someone, acquired slowly, fast or exactly.
    private static final int AIM_NONE = 0;
    private static final int LOCK_SLOW = 1;
    private static final int LOCK_FAST = 2;
    private static final int LOCK_EXACT = 3;

    // The pose being built this frame (radians; pixels for the offsets). Each state fills these
    // in, a change of state is blended in, the one-shot moves bend them, the head is aimed, and
    // apply() finally puts it all on the model parts. Arm arrays: index 0 = right arm, 1 = left.
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
    private float neckForward;   // px
    private float headRoll;      // tilt about its own nose (radians)
    private float headForward;   // px
    private float headPitchAdd;  // extra look down (+) or up (-) on top of its aim (degrees)
    private float headYawFree;   // its own head direction when not locked: degrees from the body's facing
    private float headPitchFree; // ...and degrees down from level, in the world
    private float freeWeight;    // 0 = head fully on its target, 1 = fully its own way (feeding)
    private int aimMode;
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
    private final float[] bend = new float[2];     // elbow: - bends it the natural way, + the spider way (elbow up, forearm down)
    private final float[] foreOut = new float[2];  // forearm tilted out to its side
    private final float[] curl = new float[2];     // whole hand curled in (+) or flung open (-)
    private final float[] spread = new float[2];   // claws fanned apart
    private final float[][] fingerCurl = new float[2][3]; // each finger's own curl (degrees)
    private boolean fingersLive; // false = the fingers hold still at fingerRest
    private float fingerRest;
    // One arm's pose worked out by idleArm / runArm (degrees).
    private float armRaise;
    private float armOut;
    private float armBend;
    private float armFore;
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
        // long gap (it was out of sight) it starts fresh instead of blending from an old pose.
        boolean fresh = Float.isNaN(memory.lastAge) || t < memory.lastAge || t - memory.lastAge > 5.0f;
        float dt = fresh ? 0.0f : t - memory.lastAge;
        memory.lastAge = t;
        // 0 = standing still, 1 = walking or running: fades the stride in and out.
        float moving = Mth.clamp((limbSwingAmount - 0.02f) * 8.0f, 0.0f, 1.0f);
        float phase = entity.gaitPhase(partial);
        float breath = Mth.sin(Mth.TWO_PI * entity.breathPhase(partial));
        lowArm = BacteriaRhythm.hash(seed, 1) > 0.0f ? 0 : 1;

        // Under a low ceiling (doorways, all of Level 2) it stoops instead of poking through.
        // (The model is drawn smaller than its own pixels, so a block of headroom is more of them.)
        room = entity.headroom(partial) * 16.0f / SCALE - 1.5f; // free height in model px, minus a margin
        float standing = HIP_HEIGHT + SPINE + NECK_AND_HEAD;
        squeeze = Mth.clamp((standing - room) / (standing - 46.5f), 0.0f, 1.0f);

        // --- Which pose, which one-shot move is playing (its age in ticks, -1 = none), who it
        // looks at ----------------------------------------------------------------------------
        int state;
        float age = 0.0f;
        int occurrence = 0;    // tells repeats of a move apart, so each one goes a different way
        float whip = -1.0f;
        float swipe = -1.0f;
        int peekSide = 1;
        boolean peekHold = false;
        float near = 0.0f;
        int runStamp = entity.modeStamp();
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
            runStamp = cycle;
            moving = 0.0f;
            switch (forced) {
                case WALK -> {
                    state = CALM;
                    moving = 1.0f;
                }
                case CHASE -> {
                    state = CHASE;
                    moving = 1.0f;
                    if (loop && inCycle >= CHASE_SWIPE_AT) {
                        swipe = inCycle - CHASE_SWIPE_AT;
                        occurrence = cycle;
                    }
                }
                case SPOTTED -> {
                    // With loop: the turn, then straight into the run, every 2 s.
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
                    // The stop and stare, the leap, then the cage once the arms have closed.
                    float stare = Bacteria.POSE_STARE_TICKS;
                    if (inCycle < stare) {
                        state = STARE;
                    } else if (inCycle < stare + Bacteria.LUNGE_TICKS) {
                        state = LEAP;
                        age = inCycle - stare;
                    } else {
                        state = CAGE;
                        cageClock = inCycle - stare - Bacteria.LUNGE_TICKS;
                    }
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
            if (action == Bacteria.Action.SWIPE) {
                swipe = since;
            }
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
                target = entity.preyId();
                if (entity.isCaging()) {
                    state = CAGE;
                    if (action == Bacteria.Action.WHIP) {
                        whip = since;
                    }
                } else if (action == Bacteria.Action.LUNGE && since < Bacteria.LUNGE_TICKS + 2.0f) {
                    state = LEAP;
                    age = since;
                } else if (action == Bacteria.Action.STARE && since < 12.0f) {
                    state = STARE;
                } else {
                    state = CHASE;
                }
                // The last few blocks before the leap, it leans in a little more.
                Entity prey = entity.level().getEntity(target);
                if (prey != null) {
                    near = Mth.clamp(((float) Bacteria.LUNGE_RANGE + 3.0f - entity.distanceTo(prey)) / 3.0f, 0.0f, 1.0f);
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
            case CHASE -> run(seed, t, moving, phase, near, memory, runStamp);
            case SEARCH -> {
                calm(seed, t, moving, phase, breath);
                searchHead(seed, t);
            }
            case SPOTTED -> turn(seed, age, occurrence);
            case STARE -> stare(seed);
            case LEAP -> leap(age);
            case CAGE -> cage(seed, t, cageClock);
            case FEED -> {
                calm(seed, t, 0.0f, phase, breath);
                feed(seed, t, age);
            }
            case PEEK -> {
                calm(seed, t, 0.0f, phase, breath);
                peek(age, peekSide, peekHold);
            }
            default -> calm(seed, t, moving, phase, breath);
        }
        blendArms(memory, state, t, fresh);
        if (whip >= 0.0f && whip < 9.5f) {
            whip(BacteriaRhythm.hash(seed + 27, occurrence) > 0.0f ? 0 : 1, whip);
        }
        boolean swiping = swipe >= 0.0f && swipe < SWIPE_TICKS && state != CAGE;
        if (swiping) {
            swipe(BacteriaRhythm.hash(seed + 28, occurrence) > 0.0f ? 0 : 1, swipe);
        }
        if (entity.hurtTime > 0) {
            flinch(10.0f - entity.hurtTime + partial);
        }
        if (state != CAGE && state != PEEK && state != LEAP && !swiping) {
            // (Not the cage, planted wide around you on purpose, the peek, where the whole body
            // leans out from cover, nor the leap's brief T and the swipe, which are meant to be wide.)
            limitSpread(MAX_SPREAD);
        }
        fingers(seed, t);
        aimHead(entity, memory, partial, dt, fresh, target);
        apply();
        organs(entity, partial, ageInTicks);
    }

    // ------------------------------------------------------------------ the states

    /**
     * Nobody seen yet: standing (m = 0) or walking (m = 1).
     * <p>Standing: upright (leaning 10 degrees), one foot a little forward of the other, feet
     * apart, the neck pushed forward with the head held tilted to one side. Both arms are bent
     * forward like a mantis's forelegs, elbows out in front, the forearms hanging down to the claws
     * on the floor in front of it, and they slowly cycle: claws on the floor, reached forward and
     * lifted, drawn back. That, the fingers and a slow breath are all that moves. If it can see you
     * within 24 blocks its head slowly turns onto you and stays.
     * <p>Walking: mantis-like. Short steps on bent knees with the feet wide (a 12-tick stride at its
     * 2 blocks/s), the body leaning 15 degrees. One arm is cocked forward, elbow up and the claws
     * hanging, while the other reaches out and back to the floor like a spider's leg; they swap
     * every stride and the body shifts its weight toward the arm on the floor. Each footfall jolts
     * the head a little.
     */
    private void calm(int seed, float t, float m, float phase, float breath) {
        stance(-8.0f * DEG, 15.0f * DEG);
        // One foot forward of the other (always the same one for this Bacteria).
        boolean rightFront = BacteriaRhythm.hash(seed, 4) > 0.0f;
        thighR = (rightFront ? IDLE_FRONT_THIGH : IDLE_BACK_THIGH) * DEG;
        shinR = (rightFront ? IDLE_FRONT_SHIN : IDLE_BACK_SHIN) * DEG;
        thighL = (rightFront ? IDLE_BACK_THIGH : IDLE_FRONT_THIGH) * DEG;
        shinL = (rightFront ? IDLE_BACK_SHIN : IDLE_FRONT_SHIN) * DEG;
        drop = Math.min(legDrop(thighR, shinR), legDrop(thighL, shinL));
        hips = HIP_HEIGHT - drop;
        if (m > 0.0f) {
            legsFromTable(phase, WALK_THIGH, WALK_SHIN, m);
        }
        legSplay = Mth.lerp(m, IDLE_LEG_SPLAY, WALK_LEG_SPLAY) * DEG;
        lean = (IDLE_LEAN + (WALK_LEAN - IDLE_LEAN) * m) * DEG + BREATH_DEG * DEG * breath * (1.0f - m);
        fitLean();
        bob = m * footfall(phase, WALK_BOB, Bacteria.WALK_STRIDE_TICKS);
        neckX = NECK_TILT * DEG;
        neckForward = NECK_FORWARD;
        headRoll = restingTilt(seed) + HEAD_ROLL_DRIFT * DEG * noise(seed + 5, t, HEAD_ROLL_TICKS);
        stepJolt(phase, Bacteria.WALK_STRIDE_TICKS, m);
        headPitchFree = HEAD_PITCH_DOWN;
        float weight = 0.0f;
        for (int i = 0; i < 2; i++) {
            idleArm(t, i);
            float r = armRaise;
            float o = armOut;
            float b = armBend;
            float f = armFore;
            if (m > 0.0f) {
                // The arm cycle is two strides; the arms are half a cycle apart.
                float q = phase * 0.5f + 0.5f * i;
                q -= Mth.floor(q);
                float cocked = q < 0.5f ? smoothCos(q / WALK_SWAP) : 1.0f - smoothCos((q - 0.5f) / WALK_SWAP);
                weight += (i == 0 ? 1.0f : -1.0f) * cocked;
                r = Mth.lerp(m, r, Mth.lerp(cocked, WALK_BACK_RAISE, WALK_COCK_RAISE));
                o = Mth.lerp(m, o, Mth.lerp(cocked, WALK_BACK_OUT, WALK_COCK_OUT));
                b = Mth.lerp(m, b, Mth.lerp(cocked, WALK_BACK_BEND, WALK_COCK_BEND));
                f = Mth.lerp(m, f, Mth.lerp(cocked, WALK_BACK_FORE, WALK_COCK_FORE));
            }
            raise[i] = r * DEG;
            out[i] = o * DEG;
            bend[i] = b * DEG;
            foreOut[i] = f * DEG;
            curl[i] = 8.0f * DEG;
            spread[i] = 6.0f * DEG;
        }
        // Right arm cocked = the left arm is the one on the floor: lean toward the left (+).
        sway = WALK_WEIGHT_SHIFT * DEG * weight * m;
        aimMode = LOCK_SLOW;
    }

    /**
     * One standing arm (into armRaise/armOut/armBend): its claws cycle through the three places
     * in front of it (on the floor, forward and lifted, drawn back) over IDLE_ARM_TICKS.
     */
    private void idleArm(float t, int i) {
        float c = t / IDLE_ARM_TICKS + IDLE_ARM_OFFSET * i;
        float x = (c - Mth.floor(c)) * 3.0f;
        int k = Math.min(2, (int) x);
        float u = smoothCos(x - k);
        int next = k == 2 ? 0 : k + 1;
        armRaise = Mth.lerp(u, IDLE_RAISE[k], IDLE_RAISE[next]);
        armBend = Mth.lerp(u, IDLE_BEND[k], IDLE_BEND[next]);
        armOut = IDLE_OUT;
        armFore = 0.0f;
    }

    /** Its own resting head tilt: always to the same side, 20-32 degrees. */
    private static float restingTilt(int seed) {
        return (BacteriaRhythm.hash(seed, 2) > 0.0f ? 1.0f : -1.0f)
                * (HEAD_TILT_MIN + (HEAD_TILT_MAX - HEAD_TILT_MIN) * BacteriaRhythm.unit(seed, 3)) * DEG;
    }

    /** Each footfall jolts the head: a little down, and tipped to one side then the other. */
    private void stepJolt(float phase, float strideTicks, float m) {
        float jolt = footfall(phase, 1.0f, strideTicks) * m;
        int step = Mth.floor(phase * 2.0f);
        headPitchAdd += STEP_JOLT_PITCH * jolt;
        headRoll += ((step & 1) == 0 ? 1.0f : -1.0f) * STEP_JOLT_ROLL * DEG * jolt;
    }

    /**
     * Running: upright (15 degrees, 18 in the last 3 blocks), a fast, low scurry of short
     * high-knee steps on bent knees under its body, feet a little apart, a drop as each foot lands
     * and a rock toward the leg it stands on. The arms are spider legs: each plants far out to the
     * side (about 2 blocks) as a big angular Λ, the elbow about level with the shoulder,
     * drags back under the body, then whips up over the top and out to plant again; one arm cycle
     * every 2 strides, the arms taking turns, the right arm planting as the left foot lands. Each
     * run one arm is a little different. Next to a wall the claw on that side plants ahead instead
     * of out. Stood still (m = 0) the arms come to the planted spider stance. The head stays locked
     * on your eyes, tilted, jolting with the steps.
     */
    private void run(int seed, float t, float m, float phase, float near, BacteriaPoseState memory, int runStamp) {
        stance(-25.0f * DEG, 50.0f * DEG);
        legsFromTable(phase, RUN_THIGH, RUN_SHIN, m);
        legSplay = RUN_LEG_SPLAY * DEG;
        lean = (RUN_LEAN + (RUN_NEAR_LEAN - RUN_LEAN) * near) * DEG;
        fitLean();
        sway = STRIDE_SWAY * DEG * Mth.sin(Mth.TWO_PI * phase) * m;
        bob = m * footfall(phase, RUN_BOB, Bacteria.RUN_STRIDE_TICKS);
        neckX = NECK_TILT * DEG;
        neckForward = NECK_FORWARD;
        headRoll = restingTilt(seed) + RUN_HEAD_ROLL * DEG * noise(seed + 23, t, RUN_HEAD_ROLL_TICKS);
        stepJolt(phase, Bacteria.RUN_STRIDE_TICKS, m);
        int oddArm = BacteriaRhythm.hash(seed + 600, runStamp) > 0.0f ? 0 : 1;
        boolean oddAhead = BacteriaRhythm.hash(seed + 601, runStamp) > 0.0f;
        float oddDelay = ASYM_DELAY_MIN + (ASYM_DELAY_MAX - ASYM_DELAY_MIN) * BacteriaRhythm.unit(seed + 602, runStamp);
        for (int i = 0; i < 2; i++) {
            // The right arm plants as the left foot lands (half a stride in), the left arm one
            // stride later; the arm cycle is two strides.
            float armPhase = (phase - 0.5f) * 0.5f + 0.5f * i;
            if (i == oddArm && !oddAhead) {
                armPhase -= oddDelay;
            }
            armPhase -= Mth.floor(armPhase);
            runArm(armPhase);
            // Near the plant (not mid-swing) the planted claw may go further ahead (the odd arm)
            // or ahead instead of out to the side (a wall on that side).
            float plantWeight = smooth(Math.abs(armPhase - 0.45f) / 0.25f);
            if (i == oddArm && oddAhead) {
                armRaise += ASYM_RAISE * plantWeight;
                armBend += ASYM_BEND * plantWeight;
            }
            float wall = memory.narrow[i] * plantWeight;
            float r = Mth.lerp(wall, armRaise, NARROW_RAISE);
            float o = Mth.lerp(wall, armOut, NARROW_OUT);
            float b = Mth.lerp(wall, armBend, NARROW_BEND);
            float f = Mth.lerp(wall, armFore, 0.0f);
            raise[i] = Mth.lerp(m, TRIPOD_RAISE, r) * DEG;
            out[i] = Mth.lerp(m, TRIPOD_OUT, o) * DEG;
            bend[i] = Mth.lerp(m, TRIPOD_BEND, b) * DEG;
            foreOut[i] = Mth.lerp(m, TRIPOD_FORE, f) * DEG;
            // The claws spread as they reach and flatten as they plant.
            boolean planting = armPhase < 0.08f || armPhase > 0.95f;
            curl[i] = (planting ? 10.0f : -10.0f) * DEG;
            spread[i] = 25.0f * DEG;
        }
        fingersLive = false;
        fingerRest = -5.0f;
        aimMode = LOCK_EXACT;
    }

    /** The running arm at {@code p} of its cycle (into armRaise/armOut/armBend/armFore), between the keyframes. */
    private void runArm(float p) {
        int k = 0;
        while (k < RUN_ARM_PHASE.length - 2 && p >= RUN_ARM_PHASE[k + 1]) {
            k++;
        }
        float u = smoothCos((p - RUN_ARM_PHASE[k]) / (RUN_ARM_PHASE[k + 1] - RUN_ARM_PHASE[k]));
        armRaise = Mth.lerp(u, RUN_ARM_RAISE[k], RUN_ARM_RAISE[k + 1]);
        armOut = Mth.lerp(u, RUN_ARM_OUT[k], RUN_ARM_OUT[k + 1]);
        armBend = Mth.lerp(u, RUN_ARM_BEND[k], RUN_ARM_BEND[k + 1]);
        armFore = Mth.lerp(u, RUN_ARM_FORE[k], RUN_ARM_FORE[k + 1]);
    }

    /** Searching where it last saw you: its head holds still, then turns (slowly or in a snap). */
    private void searchHead(int seed, float t) {
        headYawFree = holdTurn(seed + 32, t, 40.0f, 10.0f, 20.0f, 70.0f);
        headPitchFree = 12.0f;
        aimMode = AIM_NONE;
    }

    /**
     * It has seen you (8 ticks, no freeze): its head snaps onto you in 1-2 ticks while the body
     * swings round (the entity turns it); from tick 2 it bends right over (60 degrees) to look,
     * head rolled and looking down, one arm thrown up and out; from tick 6 it straightens into
     * the run, both arms going to the running plant.
     */
    private void turn(int seed, float age, int occurrence) {
        stance(-12.0f * DEG, 25.0f * DEG);
        legSplay = IDLE_LEG_SPLAY * DEG;
        neckX = NECK_TILT * DEG;
        neckForward = NECK_FORWARD;
        float over = age < 2.0f ? 0.0f : age < 6.0f ? smooth((age - 2.0f) / 4.0f) : 1.0f - smooth((age - 6.0f) / 2.0f);
        float into = smooth((age - 6.0f) / 2.0f); // how far into the run pose
        lean = (IDLE_LEAN + (TURN_LEAN - IDLE_LEAN) * over + (RUN_LEAN - IDLE_LEAN) * into * (1.0f - over)) * DEG;
        fitLean();
        float side = BacteriaRhythm.hash(seed + 25, occurrence) > 0.0f ? 1.0f : -1.0f;
        headRoll = restingTilt(seed) * (1.0f - over) + side * TURN_ROLL * DEG * over;
        headPitchAdd = TURN_PITCH * over;
        int up = side > 0.0f ? 1 : 0;
        for (int i = 0; i < 2; i++) {
            float r = Mth.lerp(into, IDLE_RAISE[0], RUN_ARM_RAISE[0]);
            float o = Mth.lerp(into, IDLE_OUT, RUN_ARM_OUT[0]);
            float b = Mth.lerp(into, IDLE_BEND[0], RUN_ARM_BEND[0]);
            float f = Mth.lerp(into, 0.0f, RUN_ARM_FORE[0]);
            if (i == up) {
                r = Mth.lerp(over, r, TURN_ARM_RAISE);
                o = Mth.lerp(over, o, TURN_ARM_OUT);
                b = Mth.lerp(over, b, TURN_ARM_BEND);
                f = Mth.lerp(over, f, 0.0f);
            }
            raise[i] = r * DEG;
            out[i] = o * DEG;
            bend[i] = b * DEG;
            foreOut[i] = f * DEG;
            spread[i] = 20.0f * DEG;
        }
        aimMode = LOCK_FAST;
    }

    /** Stopped close to you: upright, crouched in a wide stance, both arms planted as spider legs, dead still, head on you. */
    private void stare(int seed) {
        stance(-25.0f * DEG, 50.0f * DEG);
        legSplay = WALK_LEG_SPLAY * DEG;
        lean = STARE_LEAN * DEG;
        fitLean();
        neckX = NECK_TILT * DEG;
        neckForward = NECK_FORWARD;
        headRoll = restingTilt(seed);
        tripodArms();
        fingersLive = false;
        fingerRest = 0.0f;
        aimMode = LOCK_EXACT;
    }

    /**
     * The leap (3 ticks): in the first tick both arms fling straight out to the sides (the T, for
     * 2-3 ticks only) and the head pushes forward; the entity covers the gap in the next two;
     * then the cage takes over.
     */
    private void leap(float a) {
        stance(-10.0f * DEG, 10.0f * DEG); // legs stretched out in the air
        float k = smooth(a);
        lean = Mth.lerp(k, STARE_LEAN, LEAP_LEAN) * DEG;
        fitLean();
        neckX = NECK_TILT * DEG;
        neckForward = NECK_FORWARD;
        headForward = LEAP_HEAD_FORWARD * k;
        for (int i = 0; i < 2; i++) {
            raise[i] = Mth.lerp(k, TRIPOD_RAISE, LEAP_RAISE) * DEG;
            out[i] = Mth.lerp(k, TRIPOD_OUT, LEAP_OUT) * DEG;
            bend[i] = Mth.lerp(k, TRIPOD_BEND, 0.0f) * DEG;
            foreOut[i] = Mth.lerp(k, TRIPOD_FORE, 0.0f) * DEG;
            curl[i] = -15.0f * DEG;
            spread[i] = 30.0f * DEG;
        }
        fingersLive = false;
        fingerRest = -10.0f;
        aimMode = LOCK_EXACT;
    }

    /**
     * Holding you (the reference video's "arm cage"): it stands over you 1.5 blocks away, crouched
     * and leaning 25 degrees, its long arms closed round you: the upper arms out and down to the
     * elbows beside you, a little below and in front of your face, the forearms on down past you
     * to the claws planted on the floor about 0.8 blocks either side of you and 1 block behind you
     * (all checked from the held player's camera at the drawn size: from there the arms rise from
     * the lower corners of the screen up to its shoulders). Slow and intimate: the claws creep
     * along the floor a little at a time over 25 ticks. The head creeps toward your face over 35
     * ticks, rolling over to 60-90 degrees as if examining you, snaps in the last bit in 2 ticks,
     * holds, then slowly pulls back; it comes to about 0.35 blocks from the held player's camera.
     */
    private void cage(int seed, float t, float clock) {
        stance(-25.0f * DEG, 50.0f * DEG);
        lean = CAGE_LEAN * DEG;
        fitLean();
        neckX = 10.0f * DEG;
        for (int i = 0; i < 2; i++) {
            int s = seed + 60 + 10 * i;
            // Lifting the arm and folding the elbow together slide the claws along the floor.
            float lift = crawl(s, t, 1.0f, 3.0f);
            raise[i] = (CAGE_RAISE + lift) * DEG;
            bend[i] = (CAGE_BEND + CAGE_CRAWL_BEND * lift) * DEG;
            out[i] = (CAGE_OUT + crawl(s + 2, t, 0.5f, 2.5f)) * DEG;
            foreOut[i] = CAGE_FORE * DEG;
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
        aimMode = LOCK_EXACT;
    }

    /**
     * After a kill: it folds nearly double over the body in 10 ticks, the head down at knee height
     * pecking, the long arms folded up like a mantis's forelegs (elbows 2.5 blocks out in front at
     * shoulder height, forearms folded back down to the claws on the floor 1 block in front of it);
     * then it rises slowly (20 ticks), its head coming up locked on the nearest other player. Built
     * on top of the calm pose.
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
            raise[i] = Mth.lerp(f, raise[i], 85.0f * DEG);
            out[i] = Mth.lerp(f, out[i], 40.0f * DEG);
            splay[i] = Mth.lerp(f, splay[i], 0.0f);
            bend[i] = Mth.lerp(f, bend[i], 145.0f * DEG);
            foreOut[i] = Mth.lerp(f, foreOut[i], 20.0f * DEG);
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
        float leanOut = side * 40.0f * DEG * e;
        sway += leanOut;
        // Leaning out tips the arms with it: the arm on that side would push its claws into the
        // floor and the other would lift right off. Tilting both arms back a third of the way
        // keeps the claws on the floor (checked with the joint simulation).
        for (int i = 0; i < 2; i++) {
            float armSide = i == 0 ? -1.0f : 1.0f;
            splay[i] -= armSide * PEEK_ARM_COUNTER * leanOut;
        }
        neckZ += side * 20.0f * DEG * headFirst;
        headRoll = Mth.lerp(headFirst, headRoll, side * 35.0f * DEG);
        if (hold && age >= 25.0f) {
            // The long one: not a breath, not a finger, the claws still on the floor.
            lean = IDLE_LEAN * DEG;
            fitLean();
            fingersLive = false;
            for (int i = 0; i < 2; i++) {
                raise[i] = IDLE_RAISE[0] * DEG;
                bend[i] = IDLE_BEND[0] * DEG;
            }
        }
    }

    // ------------------------------------------------------------------ one-shot moves

    /**
     * One arm whips across your face (in the cage): in 1.5 ticks the upper arm swings down and
     * back and the forearm folds up, the claws out to its side at your head height; in the next
     * 2.5 the forearm sweeps up and across in front of your face, like a scythe, to the other side,
     * stays a moment, sweeps back and plants again (9.5 ticks in all). {@code i}: 0 = right arm, 1 =
     * left. The path was simulated from the held player's camera with the long arms: the claws
     * cross about half a block in front of it at about eye height (a third of a block while its
     * head is leaning right in), never through it, your body or its own.
     */
    private void whip(int i, float a) {
        raise[i] = whipKey(a, raise[i], -20.0f * DEG, -20.0f * DEG);
        out[i] = whipKey(a, out[i], 15.0f * DEG, 5.0f * DEG);
        bend[i] = whipKey(a, bend[i], -150.0f * DEG, -160.0f * DEG);
        foreOut[i] = whipKey(a, foreOut[i], -30.0f * DEG, 45.0f * DEG);
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
     * The swipe (its melee hit, and what it does to the spot where it heard a noise): arm
     * {@code i} snaps out to its side at chest height in half a tick, sweeps across the front in
     * 1.5 ticks, then goes back to what it was doing over 4. The other arm stays planted.
     */
    private void swipe(int i, float a) {
        float r = raise[i];
        float o = out[i];
        float b = bend[i];
        if (a < 0.5f) {
            float k = smooth(a / 0.5f);
            raise[i] = Mth.lerp(k, r, SWIPE_COCK_RAISE * DEG);
            out[i] = Mth.lerp(k, o, SWIPE_COCK_OUT * DEG);
            bend[i] = Mth.lerp(k, b, SWIPE_COCK_BEND * DEG);
        } else if (a < 2.0f) {
            float k = smooth((a - 0.5f) / 1.5f);
            raise[i] = Mth.lerp(k, SWIPE_COCK_RAISE, SWIPE_ACROSS_RAISE) * DEG;
            out[i] = Mth.lerp(k, SWIPE_COCK_OUT, SWIPE_ACROSS_OUT) * DEG;
            bend[i] = Mth.lerp(k, SWIPE_COCK_BEND, SWIPE_ACROSS_BEND) * DEG;
        } else {
            float k = smooth((a - 2.0f) / (SWIPE_TICKS - 2.0f));
            raise[i] = Mth.lerp(k, SWIPE_ACROSS_RAISE * DEG, r);
            out[i] = Mth.lerp(k, SWIPE_ACROSS_OUT * DEG, o);
            bend[i] = Mth.lerp(k, SWIPE_ACROSS_BEND * DEG, b);
        }
        splay[i] = 0.0f;
        foreOut[i] = 0.0f;
        curl[i] = -10.0f * DEG;
        spread[i] = 30.0f * DEG;
    }

    /**
     * Hit: its head is thrown back (looking up 50 degrees), the torso arches back and the arms
     * fling out, over 2 ticks, then it is straight back to what it was doing over 3 ({@code a} =
     * ticks since the hit, from the vanilla hurt timer every client already gets).
     */
    private void flinch(float a) {
        if (a >= HURT_TICKS) {
            return;
        }
        float e = a < 2.0f ? smooth(a / 2.0f) : 1.0f - smooth((a - 2.0f) / (HURT_TICKS - 2.0f));
        lean = Mth.lerp(e, lean, HURT_LEAN * DEG);
        headPitchAdd += HURT_PITCH * e;
        for (int i = 0; i < 2; i++) {
            out[i] += HURT_OUT * DEG * e;
            raise[i] += HURT_RAISE * DEG * e;
        }
    }

    /**
     * The upper body bending in toward the held player's face, {@code e} from 0 (not at all) to
     * 1 (all the way: 10 degrees more lean, 30 more neck, the head 2 px forward). The arms lift
     * and the elbows fold to match, so the claws stay planted instead of sinking into the floor.
     */
    private void leanToward(float e) {
        if (e <= 0.0f) {
            return;
        }
        neckX += 30.0f * DEG * e;
        headForward += 2.0f * e;
        lean += 10.0f * DEG * e;
        for (int i = 0; i < 2; i++) {
            raise[i] += 10.0f * DEG * e;
            bend[i] += 12.0f * DEG * e;
        }
    }

    // ------------------------------------------------------------------ fingers, blending

    /**
     * Each finger slowly curls and uncurls on its own 30-80 tick cycle by 15-35 degrees. When they
     * hold still they stay at fingerRest.
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
                fingerCurl[i][f] = c;
            }
        }
    }

    /**
     * When the pose changes (walk to run, run to the stop, the turn to the run, the leap to the
     * cage...) the arms move from where they were to the new pose over 2-3 ticks (1 for the
     * leap's fling) instead of jumping there in one frame.
     */
    private void blendArms(BacteriaPoseState memory, int state, float t, boolean fresh) {
        if (fresh || !memory.shown) {
            memory.lastState = state;
            memory.blendStart = t - 100.0f;
        } else if (state != memory.lastState) {
            for (int i = 0; i < 2; i++) {
                memory.fromRaise[i] = memory.shownRaise[i];
                memory.fromOut[i] = memory.shownOut[i];
                memory.fromBend[i] = memory.shownBend[i];
                memory.fromSplay[i] = memory.shownSplay[i];
                memory.fromForeOut[i] = memory.shownForeOut[i];
            }
            memory.blendStart = t;
            memory.lastState = state;
        }
        float ticks = state == LEAP ? 1.0f : state == STARE || state == CAGE || state == CHASE ? QUICK_BLEND_TICKS : BLEND_TICKS;
        float k = smooth((t - memory.blendStart) / ticks);
        for (int i = 0; i < 2; i++) {
            if (k < 1.0f) {
                raise[i] = Mth.lerp(k, memory.fromRaise[i], raise[i]);
                out[i] = Mth.lerp(k, memory.fromOut[i], out[i]);
                bend[i] = Mth.lerp(k, memory.fromBend[i], bend[i]);
                splay[i] = Mth.lerp(k, memory.fromSplay[i], splay[i]);
                foreOut[i] = Mth.lerp(k, memory.fromForeOut[i], foreOut[i]);
            }
            memory.shownRaise[i] = raise[i];
            memory.shownOut[i] = out[i];
            memory.shownBend[i] = bend[i];
            memory.shownSplay[i] = splay[i];
            memory.shownForeOut[i] = foreOut[i];
        }
        memory.shown = true;
    }

    // ------------------------------------------------------------------ intent: the head

    /**
     * Aims the head. Locked on someone, it points at their eyes in the WORLD: the angle is worked
     * out from where its head really is this frame, and then everything the spine and neck are
     * doing is taken back off, so the head stays aimed whatever the body does. It may turn up to
     * 150 degrees from where the body faces. How quickly it gets onto its target depends on the
     * state (slowly while calm, a 1-2 tick snap when it spots you, exactly while it runs or holds
     * you). With nobody to look at it holds its own direction (headYawFree / headPitchFree).
     * headPitchAdd then tips it further down or up (bending over to look; thrown back when hit).
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
            double worldX = Mth.lerp(partial, entity.xo, entity.getX()) + (px * cos + pz * sin) / 16.0f * SCALE;
            double worldY = Mth.lerp(partial, entity.yo, entity.getY()) + (24.0f - py) / 16.0f * SCALE;
            double worldZ = Mth.lerp(partial, entity.zo, entity.getZ()) + (px * sin - pz * cos) / 16.0f * SCALE;
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
        pitch = Mth.clamp(pitch + headPitchAdd, -80.0f, 89.0f);
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
        neckX = 0.0f;
        neckZ = 0.0f;
        neckForward = 0.0f;
        headRoll = 0.0f;
        headForward = 0.0f;
        headPitchAdd = 0.0f;
        headYawFree = 0.0f;
        headPitchFree = 0.0f;
        freeWeight = 0.0f;
        legSplay = 0.0f;
        aimMode = AIM_NONE;
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
        }
    }

    /**
     * The legs from a 4-key stride table (contact, 25%, 50%, 75%), the right leg at {@code phase}
     * and the left half a stride later, faded in by {@code m} over the stance already set. The
     * hips then sit as high as the lower foot allows, so the body rises and falls with the steps.
     */
    private void legsFromTable(float phase, float[] thigh, float[] shin, float m) {
        float pr = phase - Mth.floor(phase);
        float pl = pr + 0.5f;
        pl -= Mth.floor(pl);
        thighR = Mth.lerp(m, thighR, cycle4(pr, thigh) * DEG);
        shinR = Mth.lerp(m, shinR, cycle4(pr, shin) * DEG);
        thighL = Mth.lerp(m, thighL, cycle4(pl, thigh) * DEG);
        shinL = Mth.lerp(m, shinL, cycle4(pl, shin) * DEG);
        drop = Math.min(legDrop(thighR, shinR), legDrop(thighL, shinL));
        hips = HIP_HEIGHT - drop;
    }

    /** A value from 4 keys spread evenly over one cycle, eased between them. */
    private static float cycle4(float p, float[] keys) {
        float x = p * 4.0f;
        int k = Math.min(3, (int) x);
        float u = smoothCos(x - k);
        return Mth.lerp(u, keys[k], keys[(k + 1) & 3]);
    }

    /** Each foot landing (at 0 and half a stride) drops the body {@code amount} px, easing back over a tick. */
    private static float footfall(float phase, float amount, float strideTicks) {
        float u = 2.0f * phase;
        float ticksSince = (u - Mth.floor(u)) * 0.5f * strideTicks;
        return amount * (1.0f - smooth(ticksSince));
    }

    /** Both arms in the stop stance: planted wide out to the sides, the claws flat on the floor. */
    private void tripodArms() {
        for (int i = 0; i < 2; i++) {
            raise[i] = TRIPOD_RAISE * DEG;
            out[i] = TRIPOD_OUT * DEG;
            bend[i] = TRIPOD_BEND * DEG;
            foreOut[i] = TRIPOD_FORE * DEG;
            curl[i] = 10.0f * DEG;
            spread[i] = 20.0f * DEG;
        }
    }

    /**
     * Keeps both claws within {@code maxBlocks} of its middle, out to the side, measured in the
     * world (whatever the spine's lean does to the arm angles). If a claw is further out, the arm
     * is turned back in: swung in (out) if the arm reaches forward, tilted in (splay) if it hangs.
     */
    private void limitSpread(float maxBlocks) {
        float limit = maxBlocks * 16.0f / SCALE; // in model px
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
        float sy = -SPINE + (i == lowArm ? LOW_SHOULDER_DROP : 0.0f);
        float sz = i == lowArm ? -LOW_SHOULDER_FORWARD : 0.0f;
        float x = matSpine[0] * side * SHOULDER_HALF + matSpine[1] * sy + matSpine[2] * sz;
        float z = matSpine[6] * side * SHOULDER_HALF + matSpine[7] * sy + matSpine[8] * sz;
        // Upper arm, forearm, then the middle claw (the same points the arm parts are built from).
        x += matArm[0] * side * 1.5f + matArm[1] * UPPER_ARM;
        z += matArm[6] * side * 1.5f + matArm[7] * UPPER_ARM;
        x += matFore[0] * side * 1.5f + matFore[1] * FOREARM - matFore[2] * 0.5f;
        z += matFore[6] * side * 1.5f + matFore[7] * FOREARM - matFore[8] * 0.5f;
        x += matHand[0] * side * 0.9f * ARM_STRETCH + matHand[1] * CLAW_TIP;
        z += matHand[6] * side * 0.9f * ARM_STRETCH + matHand[7] * CLAW_TIP;
        clawSide = side * x;
        clawAhead = -z;
    }

    /** How far the hips sink (px) with the thigh and the shin (relative to the thigh) bent so. */
    private static float legDrop(float thigh, float shin) {
        return THIGH + SHIN - (THIGH * Mth.cos(thigh) + SHIN * Mth.cos(thigh + shin));
    }

    /** Under a low ceiling it has to lean at least this far forward to fit. */
    private void fitLean() {
        lean = Math.max(lean, ceilingLean);
    }

    /** Puts the pose built this frame onto the model parts. */
    private void apply() {
        body.y += drop + bob;
        rightLeg.xRot = thighR;
        leftLeg.xRot = thighL;
        // Feet apart: each leg splays out to its own side (the right leg is on the -X side).
        rightLeg.zRot = legSplay;
        leftLeg.zRot = -legSplay;
        rightShin.xRot = shinR;
        leftShin.xRot = shinL;
        spine.xRot = lean;
        spine.zRot = sway;
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
     * The organs move smoothly, so they read as alive inside the wires. The heart's speed tells
     * you its mood: slow while wandering, racing once it has seen you.
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
     * Smooth noise from -1 to 1: a new random value every {@code period} ticks, with a soft glide
     * from one to the next (never a jump).
     */
    private static float noise(int seed, float t, float period) {
        float x = t / period;
        int b = Mth.floor(x);
        float w = smoothCos(x - b);
        float a = BacteriaRhythm.hash(seed, b);
        return a + (BacteriaRhythm.hash(seed, b + 1) - a) * w;
    }

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

    /** 0..1 eased at both ends along a half cosine (clamped): the softest ease, for cycles. */
    private static float smoothCos(float x) {
        return 0.5f - 0.5f * Mth.cos(Mth.clamp(x, 0.0f, 1.0f) * Mth.PI);
    }
}
