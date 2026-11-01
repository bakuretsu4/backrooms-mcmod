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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

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
    // It moves like the Bacteria in the owner's reference video (PixelNaut's "The NEW Backrooms
    // Add-ON is INCREDIBLY SCARY", the XP Games add-on; local://bacteria_ref2_motion.md):
    //  - It stands tall and upright, its long arms bent forward like a mantis's forelegs, claws on
    //    the floor in front of it. Only the claws slide slowly about.
    //  - It walks like a mantis on bent, wide-apart legs, one arm cocked forward, the other a
    //    spider leg reaching back to the floor.
    //  - It RUNS upright in long loping strides while its arms vault it along like spider legs:
    //    one claw planted far out to the side, the other whipped up and over the top.
    //  - Seeing you, it doesn't freeze: it bends right over to look, swings round and runs.
    //    Close in, it stops in a wide stance, stares for a moment, then leaps at you with its
    //    arms flung straight out, into the cage of arms.
    //  - Its head stays locked on you (aimHead), aimed at your eyes in the world.
    //
    // Pass 6: smooth, not clunky (local://bacteria_clunk_diag.md, local://bacteria_smooth_study.md).
    // The poses are the same; only the motion between them changed:
    //  - Long strides (2 blocks walking, 2.6 running: an 8-tick stride at the run instead of 3.7).
    //  - Every looping keyframe table is played through a smooth curve that keeps moving through
    //    each key (cyclic(), a Catmull-Rom spline), instead of easing to a stop at every key.
    //  - Down an arm, the forearm runs a little behind the upper arm and the hand behind the
    //    forearm (overlap), and the fingers curl through the run instead of freezing.
    //  - The legs place their feet with real geometry (gaitLegs), so a planted foot stays put on
    //    the floor while the body moves over it; the planted claws are dragged back the same way.
    //  - Every joint is shown through a little spring (springs()), so a change of pose (walk to
    //    run, run to stop, the leap into the cage...) flows from wherever the joint was, at the
    //    speed it was moving, instead of snapping or starting from a standstill.
    //  - The body bobs, rocks and twists (hips one way, shoulders the other) with every step as
    //    smooth waves, a beat after each foot lands.
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
    /**
     * Each footfall dips the head this much (degrees), a tick after the foot lands, and rocks it
     * this much to one side and then the other, both as smooth waves.
     */
    private static final float STEP_JOLT_PITCH = 2.5f;
    private static final float STEP_JOLT_ROLL = 2.0f;
    // Head lock. The head's aim is a spring too: (stiffness in Hz, damping, top speed in degrees
    // a tick) for each way of looking. Locked on you (running, staring, leaping, holding you) it
    // is stiff, so it stays on your eyes within a frame or two but never snaps; calm, it notices
    // you slowly and then tracks you; spotting you, it whips round in 2-3 ticks.
    private static final float AIM_EXACT_HZ = 6.0f;
    private static final float AIM_EXACT_DAMP = 0.85f;
    private static final float AIM_EXACT_SPEED = 60.0f;
    private static final float AIM_FAST_HZ = 3.5f;
    private static final float AIM_FAST_DAMP = 0.8f;
    private static final float AIM_FAST_SPEED = 45.0f;
    private static final float AIM_TRACK_HZ = 2.5f;     // calm, once its head is on you...
    private static final float AIM_TRACK_SPEED = 10.0f;
    private static final float AIM_SLOW_HZ = 1.0f;      // ...but it notices you slowly
    private static final float AIM_SLOW_SPEED = 1.0f;
    private static final float AIM_CALM_DAMP = 0.9f;
    private static final float AIM_FREE_HZ = 2.5f;      // nobody to look at: its own turns
    private static final float AIM_FREE_DAMP = 0.9f;
    private static final float AIM_FREE_SPEED = 40.0f;
    /** The head may turn this far from where the body faces; it slows to a stop over the last HEAD_YAW_SOFT degrees. */
    private static final float HEAD_YAW_LIMIT = 150.0f;
    private static final float HEAD_YAW_SOFT = 25.0f;
    /** Searching, its head turns now and then, either slowly over this many ticks or in one tick. */
    private static final float SLOW_TURN_TICKS = 30.0f;

    // The springs every joint is shown through (springs()): stiffness in Hz (how quickly it
    // follows), damping (1 = settles without overshooting, lower = a little swing past and back),
    // after local://bacteria_smooth_study.md section 5.4. A spring here follows a steadily moving
    // pose without lagging behind it; it only smooths sudden changes and adds a little follow-
    // through when a joint stops. The body is heavy (no wobble), the forearm and hand swing on.
    private static final float BODY_HZ = 2.0f;          // spine lean and rock, the step's weight
    private static final float BODY_DAMP = 0.9f;
    private static final float NECK_HZ = 2.5f;          // neck, head roll and push
    private static final float NECK_DAMP = 0.8f;
    private static final float HEAD_PITCH_HZ = 4.0f;    // looking down/up on top of the aim (the flinch)
    private static final float UPPER_ARM_HZ = 3.0f;
    private static final float UPPER_ARM_DAMP = 0.7f;
    private static final float FOREARM_HZ = 2.5f;
    private static final float FOREARM_DAMP = 0.5f;
    private static final float HAND_HZ = 4.0f;          // hand curl, claw spread, fingers
    private static final float HAND_DAMP = 0.35f;
    private static final float LEG_HZ = 3.0f;           // legs between poses (stepping, they follow their feet exactly)
    private static final float LEG_DAMP = 0.9f;
    /** The leap's fling and the swipe's strike keep their snap: their arm follows this stiffly. */
    private static final float STRIKE_HZ = 10.0f;
    /** A planted arm is this many times stiffer (on top of 1x), so its claw stays put on the floor. */
    private static final float PLANT_STIFFEN = 3.0f;
    /** When it is hit, the body springs are this many times stiffer, so the flinch is quick. */
    private static final float HURT_STIFFEN = 2.0f;
    // Top speeds (degrees a tick; px a tick for the pushes). No arm joint ever turns faster than
    // ARM_MAX_SPEED except in the leap's fling and the swipe's strike; the legs at a run need a
    // little more. A pose that jumps is followed at no more than TARGET_MAX_SPEED.
    private static final float ARM_MAX_SPEED = 25.0f;
    private static final float LEG_MAX_SPEED = 40.0f;
    private static final float BODY_MAX_SPEED = 25.0f;
    private static final float HEAD_PITCH_MAX_SPEED = 40.0f;
    private static final float STRIKE_MAX_SPEED = 360.0f;
    private static final float TARGET_MAX_SPEED = 40.0f;
    private static final float PUSH_MAX_SPEED = 3.0f;
    private static final float PUSH_TARGET_MAX_SPEED = 4.0f;
    // The springs' numbers in BacteriaPoseState: two per arm value (right, left), three fingers a
    // hand, then the body.
    private static final int CH_RAISE = 0;
    private static final int CH_OUT = 2;
    private static final int CH_BEND = 4;
    private static final int CH_FORE = 6;
    private static final int CH_SPLAY = 8;
    private static final int CH_CURL = 10;
    private static final int CH_SPREAD = 12;
    private static final int CH_FINGER = 14;            // 14-19
    private static final int CH_LEAN = 20;
    private static final int CH_SWAY = 21;
    private static final int CH_NECK_X = 22;
    private static final int CH_NECK_Z = 23;
    private static final int CH_NECK_FORWARD = 24;
    private static final int CH_HEAD_FORWARD = 25;
    private static final int CH_HEAD_ROLL = 26;
    private static final int CH_HEAD_PITCH = 27;
    private static final int CH_LEG_SPLAY = 28;
    private static final int CH_THIGH = 29;             // 29 right, 30 left
    private static final int CH_SHIN = 31;              // 31 right, 32 left
    private static final int CH_GAIT = 33;
    private static final int CH_FREE = 34;

    // Arm poses are (raise, out, elbow bend, forearm out), in degrees. The claw and elbow positions
    // in the comments are in world blocks at the drawn size, from its middle: (out to its side,
    // ahead, up). Every pose was worked out for the 1.5 times longer arms (upper arm 1.48 blocks,
    // forearm and claws 1.73) with the joint-by-joint simulation. Looping tables are (phase,
    // value) keys over one cycle, the last key repeating the first, played with cyclic().
    //
    // Standing: both arms bent forward like a mantis's forelegs, the elbows out in front at about
    // chest height (1.6-2.0 up, 1.6 ahead) and the forearms hanging down to the claws on the floor
    // in front of it, slowly cycling through three places: on the floor (0.8 out, 1.1 ahead),
    // reached forward and lifted (1.3 ahead, 0.35 up), drawn back (0.6 ahead, on the floor). One
    // cycle takes 36 ticks; the left arm runs 0.4 of a cycle behind the right.
    private static final float[] IDLE_PHASE = {0.0f, 1.0f / 3.0f, 2.0f / 3.0f, 1.0f};
    private static final float[] IDLE_RAISE = {75.0f, 90.0f, 67.0f, 75.0f};
    private static final float[] IDLE_BEND = {90.0f, 100.0f, 98.0f, 90.0f};
    private static final float IDLE_OUT = 5.0f;
    private static final float IDLE_ARM_TICKS = 36.0f;
    private static final float IDLE_ARM_OFFSET = 0.4f;
    /** Standing, one foot (its own side) is forward of the other: (thigh, shin) of each leg. */
    private static final float IDLE_FRONT_THIGH = -15.0f;
    private static final float IDLE_FRONT_SHIN = 25.0f;
    private static final float IDLE_BACK_THIGH = 2.0f;
    private static final float IDLE_BACK_SHIN = 12.0f;
    // Walking, mantis-like, one arm cycle per two strides (40 ticks), the arms half a cycle
    // apart, never still. Each arm in turn:
    //   0    plants its claw on the floor out to the side (1.25 out, level with its hips)
    //   0.10 drags it back as the body walks on (claw 1.2 out, 0.4 behind: the claw stays put)
    //   0.28 lifts it, folding the elbow
    //   0.48 cocked forward: elbow up at head height in front (0.9-1.1 out, 1.65 ahead, 2.05 up),
    //        claws hanging and dangling about 0.4 above the floor
    //   0.64 still cocked, the claws swinging on a little further
    //   0.84 reaches out and down to plant again.
    private static final float[] WALK_ARM_PHASE = {0.0f, 0.10f, 0.28f, 0.48f, 0.64f, 0.84f, 1.0f};
    private static final float[] WALK_ARM_RAISE = {67.0f, 61.0f, 84.0f, 100.0f, 97.0f, 90.0f, 67.0f};
    private static final float[] WALK_ARM_OUT = {60.0f, 76.0f, 34.0f, 15.0f, 18.0f, 30.0f, 60.0f};
    private static final float[] WALK_ARM_BEND = {105.0f, 105.0f, 130.0f, 105.0f, 112.0f, 115.0f, 105.0f};
    private static final float[] WALK_ARM_FORE = {0.0f, -20.0f, -10.0f, -20.0f, -20.0f, -10.0f, 0.0f};
    private static final float[] WALK_ARM_CURL = {8.0f, 10.0f, 0.0f, -5.0f, 5.0f, 0.0f, 8.0f};
    /** Walking, the claw is on the floor from 0 to this point of the arm cycle (then the overlap starts). */
    private static final float WALK_PLANT_END = 0.10f;
    /** Walking, the body leans this far (degrees) toward the arm on the floor. */
    private static final float WALK_WEIGHT_SHIFT = 2.0f;
    // Running arms are spider legs: big angular "Λ"s out to the sides, the elbow up and out, the
    // forearm down and out to the claw on the floor. One arm cycle is two strides (16 ticks); the
    // arm plants at 0:
    //   0    plant      elbow 1.85 out, 1.7 up (about level with the shoulder, 1.8 up); claw
    //                   2.1 out, 0.7 ahead, on the floor; the elbow a sharp 100 degrees
    //   0.115, 0.23     the claw stays where it planted while the body runs on over it: in the
    //                   body's view it is dragged back along the floor to 0.5 behind (no skating)
    //   0.36 lift       the claw comes up behind and out
    //   0.52 over-swing whipped up over the top: elbow well above the shoulder (2.6 up, 1.4 out),
    //                   claw 1.95 out, 0.95 up
    //   0.70 reach      the Λ in the air, out wide
    //   0.86 descend    on its way down to the plant
    // Claws 2.05-2.45 out over the cycle (2.65 for the odd arm), 0-1.1 up; the highest point (an
    // elbow) is 2.65. The hand curls (RUN_ARM_CURL) and the fingers close and open (RUN_FINGER)
    // through the cycle.
    private static final float[] RUN_ARM_PHASE = {0.0f, 0.115f, 0.23f, 0.36f, 0.52f, 0.70f, 0.86f, 1.0f};
    private static final float[] RUN_ARM_RAISE = {79.0f, 73.0f, 68.0f, 90.0f, 120.0f, 90.0f, 84.0f, 79.0f};
    private static final float[] RUN_ARM_OUT = {62.0f, 80.0f, 103.0f, 96.0f, 40.0f, 55.0f, 55.0f, 62.0f};
    private static final float[] RUN_ARM_BEND = {80.0f, 80.0f, 76.0f, 110.0f, 115.0f, 80.0f, 80.0f, 80.0f};
    private static final float[] RUN_ARM_FORE = {-40.0f, -40.0f, -40.0f, -20.0f, -35.0f, -15.0f, -30.0f, -40.0f};
    private static final float[] RUN_ARM_CURL = {10.0f, 12.0f, 15.0f, -5.0f, -15.0f, -15.0f, 0.0f, 10.0f};
    private static final float[] RUN_FINGER = {0.0f, 10.0f, 20.0f, 30.0f, 15.0f, -5.0f, -10.0f, 0.0f};
    /** Running, the claw is on the floor from 0 to this point of the arm cycle. */
    private static final float RUN_PLANT_END = 0.23f;
    // Overlap: the forearm follows the upper arm this far behind (a fraction of the arm cycle:
    // 1.3 ticks at the run), the hand and fingers this far. (Not while the claw is on the floor:
    // there every joint keeps the claw where it planted.)
    private static final float FOREARM_LAG = 0.08f;
    private static final float HAND_LAG = 0.15f;
    // With a wall close on that side (Bacteria.NARROW_CHECK), the planting arm is pulled in and
    // forward this much, so the claw plants ahead (1.25 out, 1.75 ahead) instead of in the wall.
    private static final float NARROW_RAISE_ADD = 8.0f;
    private static final float NARROW_OUT_ADD = -40.0f;
    // Each run one arm is different: either it plants further out and ahead...
    private static final float ASYM_BEND = -8.0f;
    // ...or it is carried late (a fraction of the arm cycle).
    private static final float ASYM_DELAY_MIN = 0.2f;
    private static final float ASYM_DELAY_MAX = 0.4f;
    // The legs (gaitLegs): each foot is planted for part of the stride, sliding back under the
    // body exactly as fast as the body moves (so it never skates), then lifted and swung forward
    // in an arc. Walking it is planted 55 % of the stride (both feet down for a moment at each
    // step), running 30 % (a moment with both feet in the air). The hips are this much lower (px)
    // than standing straight, the knees bent like a stool's; the feet lift this high (px); the
    // running foot folds up early behind it (skew); and it starts its swing still moving back
    // (push, a share of its planted speed) so there is no jerk as it leaves the floor. Between a
    // walk and a run every one of these follows the speed.
    private static final float WALK_STANCE = 0.55f;
    private static final float RUN_STANCE = 0.30f;
    private static final float WALK_CROUCH = 4.5f;
    private static final float RUN_CROUCH = 5.0f;
    private static final float WALK_LIFT = 4.0f;
    private static final float RUN_LIFT = 5.0f;
    private static final float RUN_LIFT_SKEW = 0.3f;
    private static final float WALK_SWING_PUSH = 0.5f;
    private static final float RUN_SWING_PUSH = 0.3f;
    /** The legs never quite straighten: the foot stays this far (px) inside their full length. */
    private static final float LEG_REACH_MARGIN = 0.4f;
    /** How far the legs splay out sideways (degrees): feet wide apart. */
    private static final float IDLE_LEG_SPLAY = 6.0f;
    private static final float WALK_LEG_SPLAY = 10.0f;
    private static final float RUN_LEG_SPLAY = 6.0f;
    // With every step (smooth waves, a beat after each foot lands): the body dips (px), the spine
    // rocks toward the planted foot (degrees, a little after it), and the hips turn toward the
    // forward leg (degrees) while the shoulders turn back the other way by a share of that.
    private static final float WALK_BOB = 1.5f;
    private static final float RUN_BOB = 0.6f;
    private static final float WALK_ROCK = 4.0f;
    private static final float RUN_ROCK = 3.0f;
    private static final float ROCK_LAG = 0.06f;
    private static final float WALK_HIP_YAW = 6.0f;
    private static final float RUN_HIP_YAW = 8.0f;
    private static final float SHOULDER_COUNTER = 0.7f;
    // Stopped close to you: a wide crouched stance, both arms planted as spider legs (elbows 1.8
    // out and 1.75 up, claws 1.85 out and 0.55 ahead, on the floor).
    private static final float TRIPOD_RAISE = 78.0f;
    private static final float TRIPOD_OUT = 60.0f;
    private static final float TRIPOD_BEND = 82.0f;
    private static final float TRIPOD_FORE = -20.0f;
    // The leap: both arms flung straight out to the sides (a T, for 2-3 ticks only: claws 3.65
    // out at about head height, 2.2 up at most), head pushed forward. The fling's springs are
    // critically damped with no lead (see springs()), so the arms go straight out without
    // swinging up past the T. (Leaning 20 degrees, a "raise" of 90 would tip the straight arms up
    // to 3.1 blocks: through the ceiling.)
    private static final float LEAP_RAISE = 70.0f;
    private static final float LEAP_OUT = 85.0f;
    private static final float LEAP_HEAD_FORWARD = 3.0f;
    // The swipe (8 ticks): in 2 ticks the arm winds up, cocked up and out to its side like a
    // mantis's strike (elbow 2.65 up, claws 2.6 out at 1.25 up); it strikes across the front in
    // 1.5 ticks (claws to 2.0 ahead on the other side, at chest height), swings on past (follow-
    // through) for a tick, then comes back over 3.5 ticks.
    private static final float SWIPE_TICKS = 8.0f;
    private static final float SWIPE_WIND_END = 2.0f;
    private static final float SWIPE_STRIKE_END = 3.5f;
    private static final float SWIPE_FOLLOW_END = 4.5f;
    private static final float SWIPE_COCK_RAISE = 110.0f;
    private static final float SWIPE_COCK_OUT = 65.0f;
    private static final float SWIPE_COCK_BEND = 90.0f;
    private static final float SWIPE_ACROSS_RAISE = 115.0f;
    private static final float SWIPE_ACROSS_OUT = -45.0f;
    private static final float SWIPE_ACROSS_BEND = 85.0f;
    private static final float SWIPE_FOLLOW_RAISE = 118.0f;
    private static final float SWIPE_FOLLOW_OUT = -60.0f;
    private static final float SWIPE_FOLLOW_BEND = 80.0f;
    // Hit: head thrown back, torso arched back, arms flung out, for 5 ticks, then on as before.
    // (Only a little raise: the long arms flung up would poke through the ceiling.)
    private static final float HURT_TICKS = 5.0f;
    private static final float HURT_LEAN = -15.0f;
    private static final float HURT_PITCH = -50.0f;
    private static final float HURT_OUT = 30.0f;
    private static final float HURT_RAISE = 5.0f;
    // Spotting you (8 ticks): bends right over (60 degrees) with its head rolled and looking
    // down, while one arm swings out wide to its side like a spider's leg, the elbow at
    // shoulder height, the claws spread at chest height (elbow 1.5 out, 1.9 up; claws 2.55 out,
    // 1.1 up; with the springs' swing included, no part of the arm above 2.1 blocks), while it
    // swings round; then straightens into the run. (Pass 6 had the arm thrown up and forward:
    // it reached 3.8 blocks ahead and 2.8 up, straight at whoever it spotted.)
    private static final float TURN_ROLL = 50.0f;
    private static final float TURN_PITCH = 30.0f;
    private static final float TURN_ARM_RAISE = 75.0f;
    private static final float TURN_ARM_OUT = 85.0f;
    private static final float TURN_ARM_BEND = 100.0f;
    private static final float TURN_ARM_FORE = 30.0f;
    private static final float TURN_ARM_SPREAD = 35.0f;
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
     * No claw ever ends up much more than this far out to its side (world blocks at the drawn
     * size), except in the cage, the peek, the leap's T and the swipe. The spider-leg arms of the
     * run reach about 2.05-2.45 (2.65 for the odd arm), the walk 1.2-1.55. It is a soft limit: a
     * claw heading past it is slowed over the last SPREAD_KNEE blocks rather than stopped dead.
     */
    private static final float MAX_SPREAD = 2.8f;
    private static final float SPREAD_KNEE = 0.4f;
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

    // Creeping round a hiding place (Bacteria.Mode.CREEP, and the test pose "creep"), after the
    // locker scene of the owner's reference video (local://bacteria_locker_motion.md). It moves
    // by changing its grip: its claws take hold of the walls and the furniture (gripControl) and
    // stay exactly where they took hold while the body moves (holdArms), the arm angles worked
    // out from the hold (solveArm). Its hands are never both off a surface.
    private static final float CREEP_LEAN = 12.0f;
    private static final float CREEP_STAND_LEAN = 15.0f;
    /** It leans this far toward the hand it holds on with (degrees). */
    private static final float CREEP_SWAY = 2.0f;
    /** Craning in through the open door: lean (degrees), more neck (degrees), neck and head pushed in (px). */
    private static final float CRANE_LEAN = 40.0f;
    private static final float CRANE_NECK = 20.0f;
    private static final float CRANE_NECK_FORWARD = 5.0f;
    private static final float CRANE_HEAD_FORWARD = 4.0f;
    /** The test pose "creep": the "door" is this far in front of it (blocks); one round of the loop lasts the prowl plus this. */
    private static final float CREEP_POSE_DOOR = 1.3f;
    private static final float CREEP_POSE_TAIL = 40.0f;
    /** The test pose's hider's eyes (blocks above the floor): a locker's. */
    private static final float CREEP_POSE_EYE = 0.8f;
    // Where it takes hold (blocks, from the middle of the door on the floor; along the door to
    // its own side, out from the door, up): braced either side of the door, wrapped round the
    // furniture's side, holding the furniture next door, reaching across the front, at the door's
    // edges before it opens it. GRIP_JITTER: each new hold moves a little (blocks).
    private static final float BRACE_ALONG = 0.75f;
    private static final float BRACE_UP = 1.35f;
    private static final float WRAP_ALONG = 0.62f;
    private static final float WRAP_BACK = 0.3f;
    private static final float WRAP_UP = 1.3f;
    private static final float NEIGHBOUR_ALONG = 1.35f;
    private static final float NEIGHBOUR_UP = 1.45f;
    private static final float ACROSS_ALONG = 0.3f;
    private static final float ACROSS_UP = 1.25f;
    private static final float DOOR_EDGE_ALONG = 0.48f;
    private static final float DOOR_EDGE_UP = 1.3f;
    private static final float GRIP_JITTER = 0.15f;
    /** Opening the door: the other arm plants on the floor this far out to its side and ahead (blocks). */
    private static final float RIP_PLANT_OUT = 1.5f;
    private static final float RIP_PLANT_AHEAD = 0.6f;
    // Re-gripping (ticks): a slow reach of GRIP_REACH_MIN-MAX, then a hold of GRIP_HOLD_MIN-MAX;
    // the other hand may only let go GRIP_HANDOVER ticks after this one took hold. While it
    // walks (searching, or on its way to the hiding place) the reaches and holds are quicker.
    private static final float GRIP_REACH_MIN = 20.0f;
    private static final float GRIP_REACH_MAX = 30.0f;
    private static final float GRIP_HOLD_MIN = 40.0f;
    private static final float GRIP_HOLD_MAX = 100.0f;
    private static final float GRIP_HANDOVER = 10.0f;
    private static final float GRIP_MOVING_REACH = 8.0f;
    private static final float GRIP_MOVING_HOLD_MIN = 12.0f;
    private static final float GRIP_MOVING_HOLD_MAX = 24.0f;
    /** A hold whose place changed is only given up after it has lasted this long (ticks). */
    private static final float GRIP_SETTLE = 15.0f;
    /** Holding the furniture next door, the other hand only lets go this long into it (ticks: once the first has taken hold). */
    private static final float FREEZE_LET_GO = 45.0f;
    /** A claw is lifted this far off the surface (out, up; blocks) midway through a reach. */
    private static final float GRIP_LIFT_OUT = 0.25f;
    private static final float GRIP_LIFT_UP = 0.15f;
    private static final float ACROSS_LIFT_OUT = 0.45f;
    /** After finding nothing to hold, a claw looks again this much later (ticks). */
    private static final float GRIP_RETRY = 10.0f;
    // Feeling for a hold (findHold): from the shoulder, along 5 directions (degrees from straight
    // ahead toward that arm's side) at 4 heights above its feet (blocks; the lowest one finds the
    // tops of one-block furniture: lockers, cabinets, desks); a hold must be on an upright face
    // 0.8-2.6 blocks from the shoulder and about 1.0-1.9 above its feet. It is let go if the
    // shoulder gets more than GRIP_LET_GO from it or it falls GRIP_BEHIND behind the shoulder.
    private static final float[] GRIP_ANGLES = {-30.0f, 0.0f, 30.0f, 60.0f, 90.0f};
    private static final float[] GRIP_HEIGHTS = {1.45f, 1.15f, 1.75f, 0.85f};
    private static final float GRIP_NEAR = 0.8f;
    private static final float GRIP_FAR = 2.6f;
    private static final float GRIP_STEP = 0.1f;
    private static final float GRIP_START = 0.3f;
    private static final float GRIP_LOW = 0.95f;
    private static final float GRIP_HIGH = 1.9f;
    private static final float GRIP_LET_GO = 2.95f;
    private static final float GRIP_BEHIND = 0.8f;
    /**
     * Holding on, an elbow never goes higher than ELBOW_TOP blocks above its feet (about the top of
     * its head), and stays ELBOW_CEILING_MARGIN under whatever ceiling is lower than that.
     */
    private static final float ELBOW_TOP = 2.3f;
    private static final float ELBOW_CEILING_MARGIN = 0.25f;
    // Working out the arm from the hold (solveArm): this many correcting passes, then up to this
    // many nudges (each turning an angle by IK_NUDGE radians to measure, steadied by IK_DAMPING)
    // until the claw is within IK_CLOSE px (under a hundredth of a block). The elbow points out
    // to its own side, away from its middle (the wide "Λ" of the reference, bracing either side of
    // the locker), tipped up by ELBOW_POLE_UP; if that would put it above the limit it tries
    // IK_POLES more directions, round its side down to ELBOW_POLE_DOWN below level (and if none is
    // low enough, the lowest one). A try that leaves the elbow over the limit counts as missing by
    // IK_OVER_COST times as much again, and the nudges never lift an elbow over it.
    private static final int IK_PASSES = 3;
    private static final int IK_POLISH = 4;
    private static final int IK_POLES = 8;
    private static final float ELBOW_POLE_UP = 0.7f;
    private static final float ELBOW_POLE_DOWN = 1.2f;
    private static final float IK_OVER_COST = 4.0f;
    /**
     * A hold more than GRIP_SIDE_ANGLE degrees round from straight ahead (alongside the body) must
     * be at least GRIP_SIDE_FAR blocks from the shoulder: any closer and the long forearm would fold
     * the elbow in against the ribs instead of out wide.
     */
    private static final float GRIP_SIDE_ANGLE = 72.0f;
    private static final float GRIP_SIDE_FAR = 2.0f;
    private static final float IK_NUDGE = 0.002f;
    private static final float IK_DAMPING = 20.0f;
    private static final float IK_CLOSE = 0.15f;
    // Hand shapes (curl and claw spread in degrees, fingers' own curl in degrees): hooked over an
    // edge, flat on a face, open while reaching, relaxed while hanging.
    private static final float HOOK_CURL = 40.0f;
    private static final float HOOK_SPREAD = 8.0f;
    private static final float HOOK_FINGERS = 40.0f;
    private static final float FLAT_CURL = -5.0f;
    private static final float FLAT_SPREAD = 28.0f;
    private static final float FLAT_FINGERS = 5.0f;
    private static final float REACH_CURL = -10.0f;
    private static final float REACH_SPREAD = 30.0f;
    /** A free arm hanging while the other holds on: the claws resting on the floor just ahead. */
    private static final float HANG_RAISE = 66.0f;
    private static final float HANG_OUT = -7.0f;
    private static final float HANG_BEND = 102.0f;
    /** Opening the door: the arm flung out wide before it plants on the floor. */
    private static final float RIP_SWING_RAISE = 60.0f;
    private static final float RIP_SWING_OUT = 85.0f;
    // What each arm wants this frame (gripControl): to hang in its pose, hold on near a place,
    // or plant its claw on the floor at a place.
    private static final int WISH_FREE = 0;
    private static final int WISH_HOLD = 1;
    private static final int WISH_FLOOR = 2;
    // The parts of creeping round a hiding place, as the model plays them.
    private static final int CREEP_ARRIVING = 0;
    private static final int CREEP_PROWL = 1;
    private static final int CREEP_RIP = 2;
    // The head's spring while it creeps: slow and steady, no faster than AIM_CREEP_SPEED.
    private static final float AIM_CREEP_HZ = 1.0f;
    private static final float AIM_CREEP_SPEED = 1.0f;

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
    private static final int CREEP = 9;
    // How the head is aimed: its own way, or locked on someone, acquired slowly, fast or exactly.
    private static final int AIM_NONE = 0;
    private static final int LOCK_SLOW = 1;
    private static final int LOCK_FAST = 2;
    private static final int LOCK_EXACT = 3;
    private static final int LOCK_CREEP = 4;

    // The pose being built this frame (radians; pixels for the offsets). Each state fills these
    // in as targets, the one-shot moves bend them, the springs carry the joints toward them, the
    // step's waves are added, the head is aimed, and apply() finally puts it all on the model
    // parts. Arm arrays: index 0 = right arm, 1 = left.
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
    private float armCurl;
    private float armFinger;
    // Running, the fingers close and open through the arm's swing (fingerCycle, degrees) instead
    // of their own slow curling.
    private boolean fingersCycle;
    private final float[] fingerCycle = new float[2];
    // How much each arm's claw is planted on the floor this frame (0..1): planted arms follow
    // their pose more stiffly and turn back against the shoulders' twist, so the claw stays put.
    private final float[] armPlant = new float[2];
    // The step's twist: hips (the legs) turn one way, the shoulders (the spine) the other.
    private float hipYaw;
    private float spineYaw;
    // Walking/running: how much the legs are stepping (1) rather than standing in a pose (0), how
    // far the hips are lowered while they step (px), and how far between a walk (0) and a run (1).
    private float gaitTarget;
    private float gaitCrouch;
    // How much the step's waves show this frame (the springs' version of gaitTarget).
    private float gaitWeight;
    // The leg angles footAngles() worked out (radians).
    private float footThigh;
    private float footShin;
    private float runness;
    // Which arm is striking (the swipe; -1 = none), and whether both are (the leap's fling):
    // those keep their snap.
    private int strikeArm;
    private boolean strikeBoth;
    // The springs' bookkeeping for this frame (see spring()).
    private BacteriaPoseState springMemory;
    private float springDt;
    private boolean springFresh;
    // How fast the last spring's pose itself was moving (per tick).
    private float springTargetVel;
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
    // ...and the full point of a claw (model px), worked out by clawPoint.
    private float clawMX;
    private float clawMY;
    private float clawMZ;
    // ...and how high its elbow is (model y; up is smaller), from the same chain.
    private float clawElbowY;
    // Where the body is in the world this frame (eased), and which way it faces (radians).
    private double bodyX;
    private double bodyY;
    private double bodyZ;
    private float bodyYawRad;
    // A world point turned into model space (modelPoint), and back (worldPoint).
    private float modelX;
    private float modelY;
    private float modelZ;
    private double worldX;
    private double worldY;
    private double worldZ;
    // Holding on (gripControl / holdArms): whether the grip controller runs this frame, what
    // each arm wants and near which place, whether it may re-grip on its own, how long its reach
    // takes (0 = its own rhythm) and how far it is lifted off midway; how firmly each arm is pinned
    // to its hold (0..1), the claw's place this frame (world), and its hand's finger curl.
    private boolean gripping;
    private boolean gripMoving;
    private final int[] wish = new int[2];
    private final double[] wishX = new double[2];
    private final double[] wishY = new double[2];
    private final double[] wishZ = new double[2];
    private final boolean[] wishRegrip = new boolean[2];
    private final float[] wishReach = new float[2];
    private final float[] wishLift = new float[2];
    private final float[] gripExact = new float[2];
    private final double[] clawGoalX = new double[2];
    private final double[] clawGoalY = new double[2];
    private final double[] clawGoalZ = new double[2];
    private boolean fingersHold;
    private final float[] fingerHold = new float[2];
    // The highest an elbow may go (model y; up is smaller), from the ceiling.
    private float elbowCeilingY;
    // Creeping round a hiding place this frame: which part, how far into it (ticks), the prowl's
    // length and side, the middle of the door on the floor (world), the way the door faces, and
    // the place the head looks at (the hider's eyes).
    private int creepPart;
    private float creepAge;
    private int creepLength;
    private int creepSide;
    private double doorX;
    private double doorY;
    private double doorZ;
    private float openX;
    private float openZ;
    private boolean aimPointOn;
    private double aimPX;
    private double aimPY;
    private double aimPZ;
    // Feeling for a hold (findHold): the block looked at, and the best hold found.
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    private double holdX;
    private double holdY;
    private double holdZ;
    private float holdNX;
    private float holdNZ;
    private int holdKind;
    // The arm angles solveArm worked out (radians).
    private float ikRaise;
    private float ikOut;
    private float ikSplay;
    private float ikBend;

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
        // How fast it moves (eased by the entity, so it doesn't wobble with the server's position
        // updates) drives everything about the stride: 0 = standing still, 1 = walking or
        // running fades the stride in and out; between its walk and its run the stride changes
        // with the speed (runness).
        float speed = entity.gaitSpeed(partial);
        float moving = smooth((speed - 0.005f) / 0.04f);
        runness = smooth((speed - Bacteria.WALK_SPEED_PER_TICK) / (Bacteria.RUN_SPEED_PER_TICK - Bacteria.WALK_SPEED_PER_TICK));
        float stride = entity.strideFor(speed);
        float phase = entity.gaitPhase(partial);
        // A tick after each foot lands, as a share of the stride (the body's dip comes then).
        float strideTicks = Math.min(stride / Math.max(speed, 1.0e-3f), 60.0f);
        float stepLag = Math.min(1.0f / strideTicks, 0.15f);
        strikeArm = -1;
        strikeBoth = false;
        float breath = Mth.sin(Mth.TWO_PI * entity.breathPhase(partial));
        lowArm = BacteriaRhythm.hash(seed, 1) > 0.0f ? 0 : 1;
        // Where the body is in the world this frame, and which way it faces (for holding on to
        // walls, and for aiming the head at a place).
        bodyX = Mth.lerp(partial, entity.xo, entity.getX());
        bodyY = Mth.lerp(partial, entity.yo, entity.getY());
        bodyZ = Mth.lerp(partial, entity.zo, entity.getZ());
        bodyYawRad = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot) * DEG;
        gripping = false;
        gripMoving = speed > 0.04f;
        aimPointOn = false;

        // Under a low ceiling (doorways, all of Level 2) it stoops instead of poking through.
        // (The model is drawn smaller than its own pixels, so a block of headroom is more of them.)
        room = entity.headroom(partial) * 16.0f / SCALE - 1.5f; // free height in model px, minus a margin
        float standing = HIP_HEIGHT + SPINE + NECK_AND_HEAD;
        squeeze = Mth.clamp((standing - room) / (standing - 46.5f), 0.0f, 1.0f);
        // An elbow stays under the ceiling (model y: up is smaller).
        elbowCeilingY = 24.0f - Math.min(entity.headroom(partial) - ELBOW_CEILING_MARGIN, ELBOW_TOP) * 16.0f / SCALE;

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
            // The odd arm of the run stays the same arm for the whole test (a new one every loop
            // would make an arm jump mid-stride).
            runStamp = 0;
            switch (forced) {
                case WALK -> state = CALM;
                case CHASE -> {
                    state = CHASE;
                    if (loop && inCycle >= CHASE_SWIPE_AT) {
                        swipe = inCycle - CHASE_SWIPE_AT;
                        occurrence = cycle;
                    }
                }
                case SPOTTED -> {
                    // With loop: the turn, then straight into the run, every 2 s.
                    if (loop && inCycle >= Bacteria.SPOTTED_TICKS) {
                        state = CHASE;
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
                case CREEP -> {
                    // In place, in front of whatever is ahead of it (the "door" CREEP_POSE_DOOR
                    // ahead): the prowl, then opening the door and craning in; with loop, again.
                    state = CREEP;
                    target = -1;
                    creepLength = Bacteria.CREEP_POSE_TICKS;
                    float round = creepLength + Bacteria.RIP_PULL_TICK + CREEP_POSE_TAIL;
                    float a = loop ? f % round : f;
                    creepPart = a < creepLength ? CREEP_PROWL : CREEP_RIP;
                    creepAge = a < creepLength ? a : a - creepLength;
                    creepSide = Bacteria.creepSide(entity.getId(), creepLength);
                    openX = Mth.sin(bodyYawRad);
                    openZ = -Mth.cos(bodyYawRad);
                    doorX = bodyX - openX * CREEP_POSE_DOOR;
                    doorY = bodyY;
                    doorZ = bodyZ - openZ * CREEP_POSE_DOOR;
                    aimPX = doorX - openX * 0.3;
                    aimPY = doorY + CREEP_POSE_EYE;
                    aimPZ = doorZ - openZ * 0.3;
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
                // The last few blocks before the leap, it leans in a little more. (Both
                // positions eased to this frame, so the lean doesn't step at each tick.)
                Entity prey = entity.level().getEntity(target);
                if (prey != null) {
                    double dx = Mth.lerp(partial, prey.xo, prey.getX()) - Mth.lerp(partial, entity.xo, entity.getX());
                    double dz = Mth.lerp(partial, prey.zo, prey.getZ()) - Mth.lerp(partial, entity.zo, entity.getZ());
                    float d = (float) Math.sqrt(dx * dx + dz * dz);
                    near = Mth.clamp(((float) Bacteria.LUNGE_RANGE + 3.0f - d) / 3.0f, 0.0f, 1.0f);
                }
            } else if (mode == Bacteria.Mode.SPOTTED) {
                state = SPOTTED;
                age = entity.modeAge(partial);
                occurrence = entity.modeStamp();
                target = entity.preyId();
            } else if (mode == Bacteria.Mode.SEARCH) {
                state = SEARCH;
            } else if (mode == Bacteria.Mode.CREEP) {
                // Creeping round the hiding place: getting there, the prowl (from the moment it
                // got there), then opening it (both told by the server as one-shot moves).
                state = CREEP;
                creepLength = Math.max(1, entity.creepLength());
                creepSide = Bacteria.creepSide(entity.getId(), creepLength);
                boolean current = occurrence >= entity.modeStamp();
                if (current && action == Bacteria.Action.CREEP_RIP) {
                    creepPart = CREEP_RIP;
                    creepAge = since;
                } else if (current && action == Bacteria.Action.CREEP) {
                    creepPart = CREEP_PROWL;
                    creepAge = since;
                } else {
                    creepPart = CREEP_ARRIVING;
                    creepAge = entity.modeAge(partial);
                }
                Direction open = entity.creepOpen();
                BlockPos spot = entity.creepSpot();
                openX = open.getStepX();
                openZ = open.getStepZ();
                doorX = spot.getX() + 0.5 + openX * 0.5;
                doorY = spot.getY();
                doorZ = spot.getZ() + 0.5 + openZ * 0.5;
                aimPX = doorX - openX * 0.3;
                aimPY = doorY + entity.creepEye();
                aimPZ = doorZ - openZ * 0.3;
            } else {
                state = CALM;
                target = memory.watchId;
            }
            peekHold &= state == PEEK;
        }

        // --- Build the pose --------------------------------------------------------------------
        switch (state) {
            case CHASE -> run(seed, t, moving, phase, stride, near, memory, runStamp, partial);
            case SEARCH -> {
                calm(seed, t, moving, phase, stride, breath);
                searchHead(seed, t);
                // Searching, it feels its way along a wall with the hand on that side.
                wallGrip(entity, memory, seed, t, -1);
            }
            case CREEP -> creep(entity, memory, seed, t, moving, phase, stride, breath);
            case SPOTTED -> turn(seed, age, occurrence);
            case STARE -> stare(seed);
            case LEAP -> leap(age);
            case CAGE -> cage(seed, t, cageClock);
            case FEED -> {
                calm(seed, t, 0.0f, phase, stride, breath);
                feed(seed, t, age);
            }
            case PEEK -> {
                calm(seed, t, 0.0f, phase, stride, breath);
                peek(age, peekSide, peekHold);
            }
            default -> calm(seed, t, moving, phase, stride, breath);
        }
        if (whip >= 0.0f && whip < 9.5f) {
            whip(BacteriaRhythm.hash(seed + 27, occurrence) > 0.0f ? 0 : 1, whip);
        }
        boolean swiping = swipe >= 0.0f && swipe < SWIPE_TICKS && state != CAGE;
        if (swiping) {
            swipe(BacteriaRhythm.hash(seed + 28, occurrence) > 0.0f ? 0 : 1, swipe);
        }
        boolean hurt = entity.hurtTime > 0;
        if (hurt) {
            flinch(10.0f - entity.hurtTime + partial);
        }
        if (state != CAGE && state != PEEK && state != LEAP && state != CREEP && !swiping) {
            // (Not the cage, planted wide around you on purpose, the peek, where the whole body
            // leans out from cover, nor the leap's brief T and the swipe, which are meant to be wide.)
            limitSpread(MAX_SPREAD);
        }
        fingers(seed, t);
        // Everything above is where the joints should be; the springs carry them there from
        // where they were (one-shot moves included), then the step's waves go on top.
        springs(memory, dt, fresh, hurt);
        stepWaves(phase, stepLag);
        if (gripping) {
            // Held claws stay exactly where they took hold, whatever the body did this frame.
            holdArms(memory);
        }
        aimHead(entity, memory, partial, dt, fresh, target);
        noteClaws(memory);
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
     * <p>Walking: mantis-like. Long, slow steps (a 20-tick stride at its 2 blocks/s) on bent knees
     * with the feet wide, the body leaning 15 degrees. Each arm in turn plants its claw out to the
     * side and drags it back as the body walks on, lifts, cocks forward with the claws dangling
     * and reaches down to plant again, the two arms half a cycle apart and never still; the body
     * leans a little toward the arm on the floor.
     */
    private void calm(int seed, float t, float m, float phase, float stride, float breath) {
        stance(-8.0f * DEG, 15.0f * DEG);
        // One foot forward of the other (always the same one for this Bacteria).
        boolean rightFront = BacteriaRhythm.hash(seed, 4) > 0.0f;
        thighR = (rightFront ? IDLE_FRONT_THIGH : IDLE_BACK_THIGH) * DEG;
        shinR = (rightFront ? IDLE_FRONT_SHIN : IDLE_BACK_SHIN) * DEG;
        thighL = (rightFront ? IDLE_BACK_THIGH : IDLE_FRONT_THIGH) * DEG;
        shinL = (rightFront ? IDLE_BACK_SHIN : IDLE_FRONT_SHIN) * DEG;
        drop = Math.min(legDrop(thighR, shinR), legDrop(thighL, shinL));
        hips = HIP_HEIGHT - drop;
        gaitLegs(phase, m, stride);
        legSplay = Mth.lerp(m, IDLE_LEG_SPLAY, WALK_LEG_SPLAY) * DEG;
        lean = (IDLE_LEAN + (WALK_LEAN - IDLE_LEAN) * m) * DEG + BREATH_DEG * DEG * breath * (1.0f - m);
        fitLean();
        neckX = NECK_TILT * DEG;
        neckForward = NECK_FORWARD;
        headRoll = restingTilt(seed) + HEAD_ROLL_DRIFT * DEG * noise(seed + 5, t, HEAD_ROLL_TICKS);
        headPitchFree = HEAD_PITCH_DOWN;
        for (int i = 0; i < 2; i++) {
            idleArm(t, i);
            float r = armRaise;
            float o = armOut;
            float b = armBend;
            float f = armFore;
            float c = 8.0f;
            if (m > 0.0f) {
                // The arm cycle is two strides; the arms are half a cycle apart. Each arm plants
                // its claw as the foot on the other side lands.
                float ap = (phase - 0.5f) * 0.5f + 0.5f * i;
                ap -= Mth.floor(ap);
                walkArm(ap);
                r = Mth.lerp(m, r, armRaise);
                o = Mth.lerp(m, o, armOut);
                b = Mth.lerp(m, b, armBend);
                f = Mth.lerp(m, f, armFore);
                c = Mth.lerp(m, c, armCurl);
                armPlant[i] = m * (1.0f - window(ap, WALK_PLANT_END, WALK_PLANT_END + 0.06f, 0.92f, 0.99f));
                if (i == 0) {
                    // The body leans a little toward the arm on the floor: right claw down, lean
                    // right (-); half a cycle later the left (+). A smooth wave, not a switch.
                    sway = -WALK_WEIGHT_SHIFT * DEG * m * Mth.cos(Mth.TWO_PI * (ap - 0.05f));
                }
            }
            raise[i] = r * DEG;
            out[i] = o * DEG;
            bend[i] = b * DEG;
            foreOut[i] = f * DEG;
            curl[i] = c * DEG;
            spread[i] = 6.0f * DEG;
        }
        aimMode = LOCK_SLOW;
    }

    /**
     * One standing arm (into armRaise/armOut/armBend/armFore): its claws cycle smoothly through
     * the three places in front of it (on the floor, forward and lifted, drawn back) over
     * IDLE_ARM_TICKS, never stopping at any of them.
     */
    private void idleArm(float t, int i) {
        float c = t / IDLE_ARM_TICKS + IDLE_ARM_OFFSET * i;
        armRaise = cyclic(c, IDLE_PHASE, IDLE_RAISE);
        armBend = cyclic(c, IDLE_PHASE, IDLE_BEND);
        armOut = IDLE_OUT;
        armFore = 0.0f;
    }

    /**
     * One walking arm at {@code p} of its cycle (into armRaise/armOut/armBend/armFore/armCurl):
     * the forearm and hand follow a little behind the upper arm, except while the claw is on the
     * floor, where every joint keeps the claw where it planted.
     */
    private void walkArm(float p) {
        float lag = window(p, WALK_PLANT_END, WALK_PLANT_END + 0.10f, 0.86f, 0.98f);
        float pf = p - FOREARM_LAG * lag;
        float ph = p - HAND_LAG * lag;
        armRaise = cyclic(p, WALK_ARM_PHASE, WALK_ARM_RAISE);
        armOut = cyclic(p, WALK_ARM_PHASE, WALK_ARM_OUT);
        armBend = cyclic(pf, WALK_ARM_PHASE, WALK_ARM_BEND);
        armFore = cyclic(pf, WALK_ARM_PHASE, WALK_ARM_FORE);
        armCurl = cyclic(ph, WALK_ARM_PHASE, WALK_ARM_CURL);
    }

    /** Its own resting head tilt: always to the same side, 20-32 degrees. */
    private static float restingTilt(int seed) {
        return (BacteriaRhythm.hash(seed, 2) > 0.0f ? 1.0f : -1.0f)
                * (HEAD_TILT_MIN + (HEAD_TILT_MAX - HEAD_TILT_MIN) * BacteriaRhythm.unit(seed, 3)) * DEG;
    }

    /**
     * Running: upright (15 degrees, 18 in the last 3 blocks), long loping strides (a 10-tick
     * stride at its 5 blocks/s) on bent knees, feet a little apart, the body dipping, rocking and
     * twisting with each step (stepWaves). The arms are spider legs: each plants far out to the
     * side (about 2 blocks) as a big angular Λ, the elbow about level with the shoulder; the claw
     * stays where it planted while the body runs on over it, then the arm whips up over the top
     * and out to plant again, the forearm and hand trailing behind and the fingers closing and
     * opening; one arm cycle every 2 strides (16 ticks), the arms taking turns, the right arm
     * planting as the left foot lands. Each run one arm is a little different. Next to a wall the
     * claw on that side plants ahead instead of out. Stood still (m = 0) the arms come to the
     * planted spider stance. The head stays locked on your eyes, tilted.
     */
    private void run(int seed, float t, float m, float phase, float stride, float near, BacteriaPoseState memory,
                     int runStamp, float partial) {
        stance(-25.0f * DEG, 50.0f * DEG);
        gaitLegs(phase, m, stride);
        legSplay = RUN_LEG_SPLAY * DEG;
        lean = (RUN_LEAN + (RUN_NEAR_LEAN - RUN_LEAN) * near) * DEG;
        fitLean();
        neckX = NECK_TILT * DEG;
        neckForward = NECK_FORWARD;
        headRoll = restingTilt(seed) + RUN_HEAD_ROLL * DEG * noise(seed + 23, t, RUN_HEAD_ROLL_TICKS);
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
            // Around the plant (not mid-swing) the planted claw may go further out and ahead (the
            // odd arm) or ahead instead of out to the side (a wall on that side).
            float plantWeight = smooth(Math.abs(armPhase - 0.55f) / 0.3f);
            if (i == oddArm && oddAhead) {
                armBend += ASYM_BEND * plantWeight;
            }
            float wall = Mth.lerp(partial, memory.narrowO[i], memory.narrow[i]) * plantWeight;
            armRaise += NARROW_RAISE_ADD * wall;
            armOut += NARROW_OUT_ADD * wall;
            raise[i] = Mth.lerp(m, TRIPOD_RAISE, armRaise) * DEG;
            out[i] = Mth.lerp(m, TRIPOD_OUT, armOut) * DEG;
            bend[i] = Mth.lerp(m, TRIPOD_BEND, armBend) * DEG;
            foreOut[i] = Mth.lerp(m, TRIPOD_FORE, armFore) * DEG;
            curl[i] = Mth.lerp(m, 10.0f, armCurl) * DEG;
            spread[i] = 25.0f * DEG;
            fingerCycle[i] = m * armFinger;
            armPlant[i] = m * (1.0f - window(armPhase, RUN_PLANT_END, RUN_PLANT_END + 0.07f, 0.90f, 0.99f));
        }
        fingersCycle = true;
        aimMode = LOCK_EXACT;
    }

    /**
     * The running arm at {@code p} of its cycle (into armRaise/armOut/armBend/armFore/armCurl/
     * armFinger), through smooth curves; the forearm follows the upper arm FOREARM_LAG behind and
     * the hand and fingers HAND_LAG behind, except while the claw is on the floor.
     */
    private void runArm(float p) {
        float lag = window(p, RUN_PLANT_END, RUN_PLANT_END + 0.10f, 0.86f, 0.98f);
        float pf = p - FOREARM_LAG * lag;
        float ph = p - HAND_LAG * lag;
        armRaise = cyclic(p, RUN_ARM_PHASE, RUN_ARM_RAISE);
        armOut = cyclic(p, RUN_ARM_PHASE, RUN_ARM_OUT);
        armBend = cyclic(pf, RUN_ARM_PHASE, RUN_ARM_BEND);
        armFore = cyclic(pf, RUN_ARM_PHASE, RUN_ARM_FORE);
        armCurl = cyclic(ph, RUN_ARM_PHASE, RUN_ARM_CURL);
        armFinger = cyclic(ph, RUN_ARM_PHASE, RUN_FINGER);
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
     * head rolled and looking down, one arm swinging out wide to its side with the claws spread;
     * from tick 6 it straightens into the run, both arms going to the running plant.
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
                f = Mth.lerp(over, f, TURN_ARM_FORE);
            }
            raise[i] = r * DEG;
            out[i] = o * DEG;
            bend[i] = b * DEG;
            foreOut[i] = f * DEG;
            spread[i] = (i == up ? Mth.lerp(over, 20.0f, TURN_ARM_SPREAD) : 20.0f) * DEG;
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
        // The fling keeps its snap: both arms follow it stiffly (see springs()).
        strikeBoth = true;
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

    // ------------------------------------------------------------------ creeping round a hiding place

    // Scratch for the grip controller: the shoulder (model px), the best hold's score so far,
    // and whether held hands lie flat this frame (opening the door).
    private float shX;
    private float shY;
    private float shZ;
    private double holdScore;
    private boolean flatHold;

    /**
     * Creeping round a hiding place it saw someone get into (local://bacteria_locker_motion.md):
     * <p>Getting there: an ordinary walk, then a slow creep; the hand on the side of the hiding
     * place feels its way along the wall, and if it has to come round a corner, that hand hooks
     * round the corner while the body waits (the server stops it there).
     * <p>The prowl, in front of the furniture (upright, leaning a little, the head on the hider's
     * eyes, turning slowly, no twitching), by the beats of Bacteria.creepBeat: braced with both
     * hands either side of the door, re-gripping slowly one hand at a time; edging to one side
     * with that hand wrapped round the furniture's side; holding the furniture or wall next door
     * with one hand, the other hanging, nearly frozen; the hanging hand takes hold again and the
     * other reaches across the front; braced again; then still at the door, a hand on each edge.
     * <p>Opening it: one arm flung out wide and planted on the floor, the other hand flat on the
     * door frame; the body cranes in between them (14 ticks), and holds.
     */
    private void creep(Bacteria entity, BacteriaPoseState memory, int seed, float t, float m, float phase,
                       float stride, float breath) {
        calm(seed, t, m, phase, stride, breath);
        headRoll = restingTilt(seed); // steady: no drift, no twitch
        aimPointOn = true;
        aimMode = LOCK_CREEP;
        fingersLive = false;
        flatHold = false;
        for (int i = 0; i < 2; i++) {
            wish[i] = WISH_FREE;
            wishRegrip[i] = false;
            wishReach[i] = 0.0f;
            wishLift[i] = GRIP_LIFT_OUT;
        }
        if (creepPart == CREEP_ARRIVING) {
            // On its way: the hand on the hiding place's side feels its way along the wall.
            double toward = (doorX - bodyX) * Mth.cos(bodyYawRad) + (doorZ - bodyZ) * Mth.sin(bodyYawRad);
            wallGrip(entity, memory, seed, t, toward >= 0.0 ? 1 : 0);
            return;
        }
        lean = CREEP_LEAN * DEG + BREATH_DEG * DEG * breath;
        int s = creepSide;
        int sArm = s > 0 ? 1 : 0;   // the arm on that side (1 = its left)
        int oArm = 1 - sArm;
        hang(0);
        hang(1);
        if (creepPart == CREEP_PROWL) {
            int beat = Bacteria.creepBeat(creepLength, creepAge);
            float inBeat = creepAge - Bacteria.creepBeatStart(creepLength, beat);
            switch (beat) {
                case Bacteria.BEAT_WRAP -> {
                    wishAt(sArm, s * WRAP_ALONG, -WRAP_BACK, WRAP_UP, false);
                    wishAt(oArm, -s * BRACE_ALONG, 0.02f, BRACE_UP, false);
                }
                case Bacteria.BEAT_FREEZE -> {
                    wishAt(sArm, s * NEIGHBOUR_ALONG, 0.02f, NEIGHBOUR_UP, false);
                    // The other hand keeps its hold until this one has the furniture next door,
                    // then hangs.
                    if (inBeat < FREEZE_LET_GO) {
                        wishAt(oArm, -s * BRACE_ALONG, 0.02f, BRACE_UP, false);
                    }
                }
                case Bacteria.BEAT_ACROSS -> {
                    wishAt(oArm, -s * BRACE_ALONG, 0.02f, BRACE_UP, false);
                    if (inBeat < GRIP_REACH_MIN) {
                        wishAt(sArm, s * NEIGHBOUR_ALONG, 0.02f, NEIGHBOUR_UP, false);
                    } else {
                        // (It only lets go once the other hand has held on for a moment.)
                        wishAt(sArm, -s * ACROSS_ALONG, 0.02f, ACROSS_UP, false);
                        wishReach[sArm] = 25.0f;
                        wishLift[sArm] = ACROSS_LIFT_OUT;
                    }
                }
                case Bacteria.BEAT_STAND -> {
                    lean = CREEP_STAND_LEAN * DEG;
                    wishAt(0, -DOOR_EDGE_ALONG, 0.02f, DOOR_EDGE_UP, false);
                    wishAt(1, DOOR_EDGE_ALONG, 0.02f, DOOR_EDGE_UP, false);
                }
                default -> {
                    wishAt(0, -BRACE_ALONG, 0.02f, BRACE_UP, true);
                    wishAt(1, BRACE_ALONG, 0.02f, BRACE_UP, true);
                }
            }
        } else {
            creepRip(s, sArm, oArm);
        }
        fitLean();
        gripControl(entity.level(), memory, seed, t);
        // It leans a little toward the hand it holds on with.
        float balance = 0.0f;
        for (int i = 0; i < 2; i++) {
            if (memory.gripMode[i] == BacteriaPoseState.GRIP_HOLDING) {
                balance += i == 1 ? 0.5f : -0.5f;
            }
        }
        sway += CREEP_SWAY * DEG * balance;
    }

    /**
     * Opening the door ({@code creepAge} ticks in): the arm on the far side ({@code oArm}) is
     * flung out wide (a strike, it keeps its snap) and then planted on the floor out to its side;
     * the other hand stays on the door's edge, now flat against the frame; the body cranes in
     * between them. After the pull (test pose only: in the game the chase takes over) it
     * straightens up and lets go.
     */
    private void creepRip(int s, int sArm, int oArm) {
        float r = creepAge;
        float back = smooth((r - Bacteria.RIP_PULL_TICK) / 10.0f);
        if (back < 0.3f) {
            wishAt(sArm, s * DOOR_EDGE_ALONG, 0.02f, DOOR_EDGE_UP, false);
            flatHold = true;
            if (r < Bacteria.RIP_SWING_END) {
                raise[oArm] = RIP_SWING_RAISE * DEG;
                out[oArm] = RIP_SWING_OUT * DEG;
                bend[oArm] = 0.0f;
                curl[oArm] = -15.0f * DEG;
                spread[oArm] = 30.0f * DEG;
                strikeArm = oArm;
            } else {
                float ahead = RIP_PLANT_AHEAD;
                float outSide = (oArm == 1 ? 1.0f : -1.0f) * RIP_PLANT_OUT;
                wish[oArm] = WISH_FLOOR;
                wishX[oArm] = bodyX + Mth.cos(bodyYawRad) * outSide - Mth.sin(bodyYawRad) * ahead;
                wishY[oArm] = bodyY;
                wishZ[oArm] = bodyZ + Mth.sin(bodyYawRad) * outSide + Mth.cos(bodyYawRad) * ahead;
                wishReach[oArm] = Bacteria.RIP_PLANT_END - Bacteria.RIP_SWING_END;
                wishLift[oArm] = 0.0f;
            }
        }
        float crane = smooth((r - Bacteria.RIP_PLANT_END) / (Bacteria.RIP_CRANE_END - Bacteria.RIP_PLANT_END)) * (1.0f - back);
        lean = Mth.lerp(crane, CREEP_STAND_LEAN, CRANE_LEAN) * DEG;
        neckX += CRANE_NECK * DEG * crane;
        neckForward += CRANE_NECK_FORWARD * crane;
        headForward += CRANE_HEAD_FORWARD * crane;
    }

    /**
     * Feeling its way along a wall with one hand (searching, or on its way to a hiding place):
     * the hand on the side with a wall close by (preferably {@code preferArm}, -1 = either) holds
     * on a little ahead of it, and takes a new hold further on as the body walks past the old one.
     * The other arm carries on as it was.
     */
    private void wallGrip(Bacteria entity, BacteriaPoseState memory, int seed, float t, int preferArm) {
        int arm = -1;
        if (preferArm >= 0 && memory.narrow[preferArm] > 0.5f) {
            arm = preferArm;
        } else if (Math.max(memory.narrow[0], memory.narrow[1]) > 0.5f) {
            arm = memory.narrow[1] > memory.narrow[0] ? 1 : 0;
        }
        for (int i = 0; i < 2; i++) {
            wish[i] = WISH_FREE;
            wishRegrip[i] = gripMoving;
            wishReach[i] = 0.0f;
            wishLift[i] = GRIP_LIFT_OUT;
        }
        if (arm >= 0) {
            float side = arm == 1 ? 1.0f : -1.0f;
            float aheadBy = gripMoving ? 1.2f : 0.7f;
            float fx = -Mth.sin(bodyYawRad);
            float fz = Mth.cos(bodyYawRad);
            float lx = Mth.cos(bodyYawRad) * side;
            float lz = Mth.sin(bodyYawRad) * side;
            wish[arm] = WISH_HOLD;
            wishX[arm] = bodyX + fx * aheadBy + lx * 1.1;
            wishY[arm] = bodyY + 1.45;
            wishZ[arm] = bodyZ + fz * aheadBy + lz * 1.1;
        }
        gripControl(entity.level(), memory, seed, t);
    }

    /** Arm {@code i} wants to hold on near this place: along the door, out from it, up from the floor (blocks). */
    private void wishAt(int i, float along, float outFrom, float up, boolean regrip) {
        // "Along" runs toward its left as it faces the door.
        wish[i] = WISH_HOLD;
        wishX[i] = doorX - openZ * along + openX * outFrom;
        wishY[i] = doorY + up;
        wishZ[i] = doorZ + openX * along + openZ * outFrom;
        wishRegrip[i] = regrip;
    }

    /** A free arm hanging, its claws resting on the floor just ahead. */
    private void hang(int i) {
        raise[i] = HANG_RAISE * DEG;
        out[i] = HANG_OUT * DEG;
        splay[i] = 0.0f;
        bend[i] = HANG_BEND * DEG;
        foreOut[i] = 0.0f;
        curl[i] = 8.0f * DEG;
        spread[i] = 6.0f * DEG;
    }

    /**
     * The grip controller: for each arm, from what it wants this frame (wish), decides whether to
     * keep holding, reach for a new hold, or let go, and poses the arm. A reach starts from where
     * the claw really is and travels to the new hold along an arc lifted off the surface (slowly:
     * 20-30 ticks, or as the beat says), easing the arm from its own pose onto the hold; a hold
     * lasts 40-100 ticks (re-gripping only where the beat allows). One hand only lets go once the
     * other has held on for GRIP_HANDOVER ticks. A held claw stays exactly where it took hold
     * (holdArms puts it back there after the springs and the step's waves). Arm angles come from
     * the hold (solveArm); the hand hooks over an edge or lies flat on a face. Nothing is created.
     */
    private void gripControl(Level level, BacteriaPoseState m, int seed, float t) {
        gripping = true;
        for (int i = 0; i < 2; i++) {
            int other = 1 - i;
            int mode = m.gripMode[i];
            if (wish[i] == WISH_FREE) {
                m.gripMode[i] = BacteriaPoseState.GRIP_FREE;
            } else {
                boolean handover = m.gripMode[other] != BacteriaPoseState.GRIP_REACHING
                        && (m.gripMode[other] == BacteriaPoseState.GRIP_FREE || t - m.holdStart[other] >= GRIP_HANDOVER);
                if (mode == BacteriaPoseState.GRIP_FREE) {
                    if (t >= m.senseAfter[i] && handover) {
                        startReach(level, m, seed, i, t);
                    }
                } else if (mode == BacteriaPoseState.GRIP_HOLDING) {
                    shoulderModel(i);
                    toWorld(shX, shY, shZ);
                    double gx = m.gripX[i] - worldX;
                    double gy = m.gripY[i] - worldY;
                    double gz = m.gripZ[i] - worldZ;
                    boolean lost = Math.sqrt(gx * gx + gy * gy + gz * gz) > GRIP_LET_GO;
                    boolean behind = -gx * Mth.sin(bodyYawRad) + gz * Mth.cos(bodyYawRad) < -GRIP_BEHIND;
                    double wx = wishX[i] - m.wantX[i];
                    double wy = wishY[i] - m.wantY[i];
                    double wz = wishZ[i] - m.wantZ[i];
                    boolean moved = !gripMoving && wx * wx + wy * wy + wz * wz > 0.16 && t - m.holdStart[i] >= GRIP_SETTLE;
                    boolean due = wishRegrip[i] && t - m.holdStart[i] >= m.holdLen[i];
                    if (lost) {
                        m.gripMode[i] = BacteriaPoseState.GRIP_FREE; // out of reach: let go at once
                    } else if ((behind || moved || due) && handover) {
                        startReach(level, m, seed, i, t);
                    }
                } else if (t - m.reachStart[i] >= m.reachLen[i]) {
                    m.gripMode[i] = BacteriaPoseState.GRIP_HOLDING;
                    m.holdStart[i] = t;
                    m.gripLanded++;
                }
            }
            poseHold(m, i, t);
        }
        // The fingers take the hand's shape while a hand holds on (otherwise they curl on their own).
        fingersHold = m.gripMode[0] != BacteriaPoseState.GRIP_FREE || m.gripMode[1] != BacteriaPoseState.GRIP_FREE;
    }

    /** Starts arm {@code i}'s reach for a new hold near what it wants (or on the floor), or leaves it free if there is none. */
    private void startReach(Level level, BacteriaPoseState m, int seed, int i, float t) {
        int n = m.gripCount[i];
        if (wish[i] == WISH_FLOOR) {
            holdX = wishX[i];
            holdY = wishY[i];
            holdZ = wishZ[i];
            holdNX = 0.0f;
            holdNZ = 0.0f;
            holdKind = BacteriaPoseState.HOLD_FLOOR;
        } else {
            // Each new hold moves a little, so the re-grips shift its grip a few degrees.
            double jx = wishRegrip[i] ? GRIP_JITTER * BacteriaRhythm.hash(seed + 930 + i, n) : 0.0;
            double jy = wishRegrip[i] ? 0.8 * GRIP_JITTER * BacteriaRhythm.hash(seed + 940 + i, n) : 0.0;
            double jz = wishRegrip[i] ? GRIP_JITTER * BacteriaRhythm.hash(seed + 950 + i, n) : 0.0;
            if (!findHold(level, i, wishX[i] + jx, wishY[i] + jy, wishZ[i] + jz)) {
                m.gripMode[i] = BacteriaPoseState.GRIP_FREE;
                m.senseAfter[i] = t + GRIP_RETRY;
                return;
            }
        }
        if (m.clawsKnown) {
            m.fromX[i] = m.clawX[i];
            m.fromY[i] = m.clawY[i];
            m.fromZ[i] = m.clawZ[i];
        } else {
            m.fromX[i] = holdX;
            m.fromY[i] = holdY;
            m.fromZ[i] = holdZ;
        }
        m.gripX[i] = holdX;
        m.gripY[i] = holdY;
        m.gripZ[i] = holdZ;
        m.gripNX[i] = holdNX;
        m.gripNZ[i] = holdNZ;
        m.gripKind[i] = holdKind;
        m.wantX[i] = wishX[i];
        m.wantY[i] = wishY[i];
        m.wantZ[i] = wishZ[i];
        m.reachStart[i] = t;
        if (wishReach[i] > 0.0f) {
            m.reachLen[i] = wishReach[i];
        } else if (gripMoving) {
            m.reachLen[i] = GRIP_MOVING_REACH;
        } else {
            m.reachLen[i] = Mth.lerp(BacteriaRhythm.unit(seed + 910 + i, n), GRIP_REACH_MIN, GRIP_REACH_MAX);
        }
        m.holdLen[i] = gripMoving
                ? Mth.lerp(BacteriaRhythm.unit(seed + 900 + i, n), GRIP_MOVING_HOLD_MIN, GRIP_MOVING_HOLD_MAX)
                : Mth.lerp(BacteriaRhythm.unit(seed + 900 + i, n), GRIP_HOLD_MIN, GRIP_HOLD_MAX);
        m.gripMode[i] = BacteriaPoseState.GRIP_REACHING;
        m.gripCount[i] = n + 1;
    }

    /**
     * Poses arm {@code i} for this frame from its hold: where the claw should be (the hold, or on
     * its way there along the lifted arc), how firmly the arm is pinned to it, the hand's shape,
     * and the arm angles that put the claw there (with the pose being built: holdArms corrects
     * them for the body as finally shown).
     */
    private void poseHold(BacteriaPoseState m, int i, float t) {
        int mode = m.gripMode[i];
        if (mode == BacteriaPoseState.GRIP_FREE) {
            gripExact[i] = 0.0f;
            fingerHold[i] = 10.0f;
            return;
        }
        int kind = m.gripKind[i];
        double gx = m.gripX[i];
        double gy = m.gripY[i];
        double gz = m.gripZ[i];
        float open = 0.0f;
        float exact = 1.0f;
        if (mode == BacteriaPoseState.GRIP_REACHING) {
            float u = Mth.clamp((t - m.reachStart[i]) / Math.max(m.reachLen[i], 1.0f), 0.0f, 1.0f);
            float s = smooth(u);
            float lift = Mth.sin(Mth.PI * u);
            open = lift;
            // From its own pose onto the hold over the first half of the reach.
            exact = smooth(u * 2.0f);
            float liftOut = kind == BacteriaPoseState.HOLD_FLOOR ? 0.0f : wishLift[i];
            float liftUp = kind == BacteriaPoseState.HOLD_FLOOR ? 0.3f : GRIP_LIFT_UP;
            gx = Mth.lerp(s, m.fromX[i], gx) + m.gripNX[i] * liftOut * lift;
            gy = Mth.lerp(s, m.fromY[i], gy) + liftUp * lift;
            gz = Mth.lerp(s, m.fromZ[i], gz) + m.gripNZ[i] * liftOut * lift;
        }
        clawGoalX[i] = gx;
        clawGoalY[i] = gy;
        clawGoalZ[i] = gz;
        // The hand: hooked over an edge, flat on a face (or on the door frame), planted on the
        // floor; open while it reaches.
        boolean hooked = !flatHold && (kind == BacteriaPoseState.HOLD_EDGE || kind == BacteriaPoseState.HOLD_TOP);
        float handCurl = kind == BacteriaPoseState.HOLD_FLOOR ? 10.0f : hooked ? HOOK_CURL : FLAT_CURL;
        float handSpread = kind == BacteriaPoseState.HOLD_FLOOR ? 20.0f : hooked ? HOOK_SPREAD : FLAT_SPREAD;
        float handFingers = kind == BacteriaPoseState.HOLD_FLOOR ? 10.0f : hooked ? HOOK_FINGERS : FLAT_FINGERS;
        curl[i] = Mth.lerp(open, handCurl, REACH_CURL) * DEG;
        spread[i] = Mth.lerp(open, handSpread, REACH_SPREAD) * DEG;
        fingerHold[i] = Mth.lerp(open, handFingers, 0.0f);
        toModel(gx, gy, gz);
        solveArm(i, modelX, modelY, modelZ);
        raise[i] = Mth.lerp(exact, raise[i], ikRaise);
        out[i] = Mth.lerp(exact, out[i], ikOut);
        splay[i] = Mth.lerp(exact, splay[i], ikSplay);
        bend[i] = Mth.lerp(exact, bend[i], ikBend);
        foreOut[i] = Mth.lerp(exact, foreOut[i], 0.0f);
        armPlant[i] = Math.max(armPlant[i], exact);
        gripExact[i] = exact;
    }

    /**
     * After the springs and the step's waves: each arm pinned to a hold is solved again for the
     * body exactly as it will be shown, so its claw stays on the hold (no sliding), and the
     * springs are told, so the arm carries on smoothly from there when it lets go.
     */
    private void holdArms(BacteriaPoseState m) {
        for (int i = 0; i < 2; i++) {
            float e = gripExact[i];
            if (e <= 0.0f) {
                continue;
            }
            toModel(clawGoalX[i], clawGoalY[i], clawGoalZ[i]);
            solveArm(i, modelX, modelY, modelZ);
            raise[i] = Mth.lerp(e, raise[i], ikRaise);
            out[i] = Mth.lerp(e, out[i], ikOut);
            splay[i] = Mth.lerp(e, splay[i], ikSplay);
            bend[i] = Mth.lerp(e, bend[i], ikBend);
            foreOut[i] = Mth.lerp(e, foreOut[i], 0.0f);
            pinSpring(m, CH_RAISE + i, raise[i], e);
            pinSpring(m, CH_OUT + i, out[i], e);
            pinSpring(m, CH_SPLAY + i, splay[i], e);
            pinSpring(m, CH_BEND + i, bend[i], e);
            pinSpring(m, CH_FORE + i, foreOut[i], e);
        }
    }

    /** Tells spring {@code ch} that it is shown at {@code value} (its speed dies away by {@code e}). */
    private static void pinSpring(BacteriaPoseState m, int ch, float value, float e) {
        m.springPos[ch] = value;
        m.springVel[ch] *= 1.0f - e;
    }

    /**
     * Feels for a hold for arm {@code i} near the place ({@code px}, {@code py}, {@code pz}): from
     * the shoulder along GRIP_ANGLES (straight ahead round to that arm's side) at GRIP_HEIGHTS
     * above its feet, out to GRIP_FAR, the first solid block each way. A hold must be on an
     * upright face with air in front of it, 0.8-2.6 blocks from the shoulder. An upright edge
     * (a corner, the side of a locker) or a top edge (the top of furniture 1-1.9 blocks high) is
     * better than a flat face: the claws can hook over it. The best one (an edge, near the
     * place, about 1.5 blocks up) goes into holdX/Y/Z, holdNX/NZ (which way the face faces) and
     * holdKind. Only done when a claw is about to take a new hold, never every frame.
     */
    private boolean findHold(Level level, int i, double px, double py, double pz) {
        float side = i == 0 ? -1.0f : 1.0f;
        shoulderModel(i);
        toWorld(shX, shY, shZ);
        double sx = worldX;
        double sy = worldY;
        double sz = worldZ;
        float fx = -Mth.sin(bodyYawRad);
        float fz = Mth.cos(bodyYawRad);
        float lx = Mth.cos(bodyYawRad) * side;
        float lz = Mth.sin(bodyYawRad) * side;
        holdScore = -1.0e9;
        boolean found = false;
        for (float h : GRIP_HEIGHTS) {
            double y = bodyY + h;
            int cy = Mth.floor(y);
            for (float angle : GRIP_ANGLES) {
                float a = angle * DEG;
                float dx = fx * Mth.cos(a) + lx * Mth.sin(a);
                float dz = fz * Mth.cos(a) + lz * Mth.sin(a);
                int pcx = Mth.floor(sx + dx * GRIP_START);
                int pcz = Mth.floor(sz + dz * GRIP_START);
                if (solid(level, pcx, cy, pcz)) {
                    continue; // starts inside something
                }
                for (float d = GRIP_START + GRIP_STEP; d <= GRIP_FAR; d += GRIP_STEP) {
                    int cx = Mth.floor(sx + dx * d);
                    int cz = Mth.floor(sz + dz * d);
                    if (cx == pcx && cz == pcz) {
                        continue;
                    }
                    if (solid(level, cx, cy, cz)) {
                        found |= tryHold(level, cx, cy, cz, pcx, pcz, sx, sy, sz, dx, dz, y, px, py, pz);
                        break;
                    }
                    pcx = cx;
                    pcz = cz;
                }
            }
        }
        return found;
    }

    /**
     * One ray of findHold entered solid block (cx, cy, cz) from (pcx, cz/pcz): works out which
     * face it hit and where, whether that face will do, what kind of hold it is, and keeps it if
     * it is the best so far. True if it was kept.
     */
    private boolean tryHold(Level level, int cx, int cy, int cz, int pcx, int pcz, double sx, double sy, double sz,
                            float dx, float dz, double y, double px, double py, double pz) {
        int hx = cx;
        int hz = cz;
        int nx = 0;
        int nz = 0;
        if (cx != pcx && cz != pcz) {
            // It crossed a corner diagonally: which of the two faces did it really hit?
            if (solid(level, cx, cy, pcz)) {
                hz = pcz;
                nx = pcx - cx;
            } else if (solid(level, pcx, cy, cz)) {
                hx = pcx;
                nz = pcz - cz;
            } else if (Math.abs(dx) > Math.abs(dz)) {
                nx = pcx - cx;
            } else {
                nz = pcz - cz;
            }
        } else if (cx != pcx) {
            nx = pcx - cx;
        } else {
            nz = pcz - cz;
        }
        if (solid(level, hx + nx, cy, hz + nz)) {
            return false; // no air in front of that face
        }
        double hitX;
        double hitZ;
        if (nx != 0) {
            hitX = nx > 0 ? hx + 1.0 : hx;
            hitZ = sz + dz * (hitX - sx) / dx;
        } else {
            hitZ = nz > 0 ? hz + 1.0 : hz;
            hitX = sx + dx * (hitZ - sz) / dz;
        }
        double hitY = y;
        double ex = hitX - sx;
        double ey = hitY - sy;
        double ez = hitZ - sz;
        double reach = Math.sqrt(ex * ex + ey * ey + ez * ez);
        if (reach < GRIP_NEAR || reach > GRIP_FAR) {
            return false;
        }
        // Not right alongside the body unless it is far enough out for the elbow to stay wide.
        float faceX = -Mth.sin(bodyYawRad);
        float faceZ = Mth.cos(bodyYawRad);
        double ahead = ex * faceX + ez * faceZ;
        if (ahead < Math.sqrt(ex * ex + ez * ez) * Mth.cos(GRIP_SIDE_ANGLE * DEG) && reach < GRIP_SIDE_FAR) {
            return false;
        }
        // Along the face, toward the shoulder: is the block beside it open (an upright edge)?
        int tx = nz != 0 ? 1 : 0;
        int tz = nx != 0 ? 1 : 0;
        if ((sx - hitX) * tx + (sz - hitZ) * tz < 0.0) {
            tx = -tx;
            tz = -tz;
        }
        int kind = BacteriaPoseState.HOLD_FACE;
        boolean low = hitY - bodyY < GRIP_LOW;
        boolean topOpen = !solid(level, hx, cy + 1, hz) && cy + 1 - bodyY >= GRIP_LOW && cy + 1 - bodyY <= GRIP_HIGH;
        if (low && !topOpen) {
            return false; // too low to hold on to (only the top of low furniture will do)
        }
        if (!low && !solid(level, hx + tx, cy, hz + tz) && !solid(level, hx + tx + nx, cy, hz + tz + nz)) {
            kind = BacteriaPoseState.HOLD_EDGE;
            if (tx != 0) {
                hitX = tx > 0 ? hx + 0.95 : hx + 0.05;
            } else {
                hitZ = tz > 0 ? hz + 0.95 : hz + 0.05;
            }
        } else if (topOpen) {
            // The top edge of something low enough to reach over: the claws hook over it.
            kind = BacteriaPoseState.HOLD_TOP;
            hitY = cy + 1.02;
            hitX -= nx * 0.1;
            hitZ -= nz * 0.1;
        } else {
            // A flat face: the claws rest just on it.
            hitX += nx * 0.04;
            hitZ += nz * 0.04;
        }
        double qx = hitX - px;
        double qy = hitY - py;
        double qz = hitZ - pz;
        double score = (kind != BacteriaPoseState.HOLD_FACE ? 1.0 : 0.0) - 0.3 * Math.sqrt(qx * qx + qy * qy + qz * qz)
                - 0.5 * Math.abs(hitY - bodyY - 1.5);
        if (score <= holdScore) {
            return false;
        }
        holdScore = score;
        holdX = hitX;
        holdY = hitY;
        holdZ = hitZ;
        holdNX = nx;
        holdNZ = nz;
        holdKind = kind;
        return true;
    }

    /** True if block (x, y, z) has something solid to hold. */
    private boolean solid(Level level, int x, int y, int z) {
        probe.set(x, y, z);
        return !level.getBlockState(probe).getCollisionShape(level, probe).isEmpty();
    }

    /** Arm {@code i}'s shoulder joint (model px, into shX/shY/shZ) with this frame's body (and matSpine set). */
    private void shoulderModel(int i) {
        float side = i == 0 ? -1.0f : 1.0f;
        rotation(matSpine, sway, spineYaw, lean);
        float sy = -SPINE + (i == lowArm ? LOW_SHOULDER_DROP : 0.0f);
        float sz = i == lowArm ? -LOW_SHOULDER_FORWARD : 0.0f;
        shX = matSpine[0] * side * SHOULDER_HALF + matSpine[1] * sy + matSpine[2] * sz;
        shY = 24.0f - HIP_HEIGHT + drop + bob + matSpine[3] * side * SHOULDER_HALF + matSpine[4] * sy + matSpine[5] * sz;
        shZ = matSpine[6] * side * SHOULDER_HALF + matSpine[7] * sy + matSpine[8] * sz;
    }

    /**
     * The arm angles (into ikRaise/ikOut/ikSplay/ikBend) that put arm {@code i}'s claw tip on
     * model point ({@code tx}, {@code ty}, {@code tz}) with this frame's body and hand curl. A
     * two-bone reach: the elbow sits where the upper arm and forearm meet (law of cosines),
     * pointing out to its own side and a little up like a spider's leg (never above about the top
     * of its head, nor near a low ceiling). The joints are a little offset from the bones' lines, so it measures where
     * the claw really ends up and corrects (keeping the closest try), then nudges the raise,
     * swing and elbow a few times until the claw is within a tiny fraction of a block.
     */
    private void solveArm(int i, float tx, float ty, float tz) {
        float side = i == 0 ? -1.0f : 1.0f;
        float c = curl[i];
        float a = Mth.sqrt(2.25f + UPPER_ARM * UPPER_ARM);
        float hx = side * (1.5f + 0.9f * ARM_STRETCH);
        float hy = FOREARM + CLAW_TIP * Mth.cos(c);
        float hz = -0.5f + CLAW_TIP * Mth.sin(c);
        float b = Mth.sqrt(hx * hx + hy * hy + hz * hz);
        float ax = tx;
        float ay = ty;
        float az = tz;
        float bestMiss = Float.MAX_VALUE;
        float bestRaise = 0.0f;
        float bestOut = 0.0f;
        float bestSplay = 0.0f;
        float bestBend = 0.0f;
        for (int pass = 0; pass < IK_PASSES; pass++) {
            shoulderModel(i);
            aimArm(side, ax, ay, az, a, b);
            clawPoint(i, ikRaise, ikOut, ikSplay, ikBend, 0.0f, c);
            float mx = tx - clawMX;
            float my = ty - clawMY;
            float mz = tz - clawMZ;
            float miss = Mth.sqrt(mx * mx + my * my + mz * mz) + IK_OVER_COST * Math.max(elbowCeilingY - clawElbowY, 0.0f);
            if (miss < bestMiss) {
                bestMiss = miss;
                bestRaise = ikRaise;
                bestOut = ikOut;
                bestSplay = ikSplay;
                bestBend = ikBend;
            }
            ax += mx;
            ay += my;
            az += mz;
        }
        ikRaise = bestRaise;
        ikOut = bestOut;
        ikSplay = bestSplay;
        ikBend = bestBend;
        polishArm(i, tx, ty, tz, c);
    }

    /**
     * Nudges ikRaise, ikOut and ikBend (damped least squares: a cautious Newton step on the three
     * angles, measured by moving each a little) until the claw is within IK_CLOSE px of the point.
     * A nudge that would lift the elbow over the limit is not taken (better a claw a hair off its
     * hold than an elbow sticking up over the head).
     */
    private void polishArm(int i, float tx, float ty, float tz, float c) {
        for (int it = 0; it < IK_POLISH; it++) {
            clawPoint(i, ikRaise, ikOut, ikSplay, ikBend, 0.0f, c);
            float ex = tx - clawMX;
            float ey = ty - clawMY;
            float ez = tz - clawMZ;
            if (ex * ex + ey * ey + ez * ez < IK_CLOSE * IK_CLOSE) {
                return;
            }
            float elbowBefore = clawElbowY;
            float cx = clawMX;
            float cy = clawMY;
            float cz = clawMZ;
            // How the claw moves for a small turn of each angle (columns r, o, b).
            clawPoint(i, ikRaise + IK_NUDGE, ikOut, ikSplay, ikBend, 0.0f, c);
            float rx = (clawMX - cx) / IK_NUDGE;
            float ry = (clawMY - cy) / IK_NUDGE;
            float rz = (clawMZ - cz) / IK_NUDGE;
            clawPoint(i, ikRaise, ikOut + IK_NUDGE, ikSplay, ikBend, 0.0f, c);
            float ox = (clawMX - cx) / IK_NUDGE;
            float oy = (clawMY - cy) / IK_NUDGE;
            float oz = (clawMZ - cz) / IK_NUDGE;
            clawPoint(i, ikRaise, ikOut, ikSplay, ikBend + IK_NUDGE, 0.0f, c);
            float bx = (clawMX - cx) / IK_NUDGE;
            float by = (clawMY - cy) / IK_NUDGE;
            float bz = (clawMZ - cz) / IK_NUDGE;
            // (J^T J + damping) step = J^T miss, solved by Cramer's rule.
            float a00 = rx * rx + ry * ry + rz * rz + IK_DAMPING;
            float a01 = rx * ox + ry * oy + rz * oz;
            float a02 = rx * bx + ry * by + rz * bz;
            float a11 = ox * ox + oy * oy + oz * oz + IK_DAMPING;
            float a12 = ox * bx + oy * by + oz * bz;
            float a22 = bx * bx + by * by + bz * bz + IK_DAMPING;
            float g0 = rx * ex + ry * ey + rz * ez;
            float g1 = ox * ex + oy * ey + oz * ez;
            float g2 = bx * ex + by * ey + bz * ez;
            float det = a00 * (a11 * a22 - a12 * a12) - a01 * (a01 * a22 - a12 * a02) + a02 * (a01 * a12 - a11 * a02);
            if (Math.abs(det) < 1.0e-6f) {
                return;
            }
            float s0 = (g0 * (a11 * a22 - a12 * a12) - a01 * (g1 * a22 - a12 * g2) + a02 * (g1 * a12 - a11 * g2)) / det;
            float s1 = (a00 * (g1 * a22 - a12 * g2) - g0 * (a01 * a22 - a12 * a02) + a02 * (a01 * g2 - g1 * a02)) / det;
            float s2 = (a00 * (a11 * g2 - g1 * a12) - a01 * (a01 * g2 - g1 * a02) + g0 * (a01 * a12 - a11 * a02)) / det;
            clawPoint(i, ikRaise + s0, ikOut + s1, ikSplay, ikBend + s2, 0.0f, c);
            if (clawElbowY < elbowCeilingY && clawElbowY < elbowBefore) {
                return;
            }
            ikRaise += s0;
            ikOut += s1;
            ikBend += s2;
        }
    }

    /**
     * One pass of solveArm: from the shoulder (shX/shY/shZ, matSpine set) the arm of bones
     * {@code a} and {@code b} to point ({@code tx}, {@code ty}, {@code tz}).
     */
    private void aimArm(float side, float tx, float ty, float tz, float a, float b) {
        float dx = tx - shX;
        float dy = ty - shY;
        float dz = tz - shZ;
        float d = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1.0e-3f) {
            dy = 1.0f;
            d = 1.0f;
        }
        float ux = dx / d;
        float uy = dy / d;
        float uz = dz / d;
        float dc = Mth.clamp(d, Math.abs(a - b) + 0.5f, a + b - 0.5f);
        float cosA = Mth.clamp((a * a + dc * dc - b * b) / (2.0f * a * dc), -1.0f, 1.0f);
        float sinA = Mth.sqrt(1.0f - cosA * cosA);
        // The elbow: out to its own side and a little up, or tipped further down round its side
        // as far as it must be to stay under the limit (head-top height, or a low ceiling).
        float ex = shX;
        float ey = shY;
        float ez = shZ;
        boolean placed = false;
        for (int k = 0; k <= IK_POLES; k++) {
            float w = (float) k / IK_POLES;
            float px = side;
            float py = -ELBOW_POLE_UP + (ELBOW_POLE_UP + ELBOW_POLE_DOWN) * w;
            float dot = px * ux + py * uy;
            float qx = px - dot * ux;
            float qy = py - dot * uy;
            float qz = -dot * uz;
            float ql = Mth.sqrt(qx * qx + qy * qy + qz * qz);
            if (ql < 1.0e-3f) {
                continue;
            }
            float nx = shX + ux * a * cosA + qx / ql * a * sinA;
            float ny = shY + uy * a * cosA + qy / ql * a * sinA;
            float nz = shZ + uz * a * cosA + qz / ql * a * sinA;
            // Keep the lowest elbow so far, in case none is under the limit.
            if (!placed || ny > ey) {
                placed = true;
                ex = nx;
                ey = ny;
                ez = nz;
            }
            if (ny >= elbowCeilingY) {
                break;
            }
        }
        // The upper arm's and the forearm's directions, in the chest's own frame (the spine's
        // lean, rock and twist taken back off: matSpine turned the other way).
        float ux1 = (ex - shX) / a;
        float uy1 = (ey - shY) / a;
        float uz1 = (ez - shZ) / a;
        float fx0 = shX + ux * dc - ex;
        float fy0 = shY + uy * dc - ey;
        float fz0 = shZ + uz * dc - ez;
        float fl = Math.max(Mth.sqrt(fx0 * fx0 + fy0 * fy0 + fz0 * fz0), 1.0e-4f);
        float yx = matSpine[0] * ux1 + matSpine[3] * uy1 + matSpine[6] * uz1;
        float yy = matSpine[1] * ux1 + matSpine[4] * uy1 + matSpine[7] * uz1;
        float yz = matSpine[2] * ux1 + matSpine[5] * uy1 + matSpine[8] * uz1;
        float gx = (matSpine[0] * fx0 + matSpine[3] * fy0 + matSpine[6] * fz0) / fl;
        float gy = (matSpine[1] * fx0 + matSpine[4] * fy0 + matSpine[7] * fz0) / fl;
        float gz = (matSpine[2] * fx0 + matSpine[5] * fy0 + matSpine[8] * fz0) / fl;
        // The arm's own frame: Y along the upper arm, Z the way the forearm bends, X = Y x Z.
        float along = gx * yx + gy * yy + gz * yz;
        float zx = gx - along * yx;
        float zy = gy - along * yy;
        float zz = gz - along * yz;
        float zl = Mth.sqrt(zx * zx + zy * zy + zz * zz);
        if (zl < 1.0e-4f) {
            // Straight arm: any bend direction will do; take the one facing forward.
            zx = -yx * yz;
            zy = -yy * yz;
            zz = 1.0f - yz * yz;
            float l = Math.max(Mth.sqrt(zx * zx + zy * zy + zz * zz), 1.0e-4f);
            zx /= l;
            zy /= l;
            zz /= l;
        } else {
            zx /= zl;
            zy /= zl;
            zz /= zl;
        }
        float xx = yy * zz - yz * zy;
        float xy = yz * zx - yx * zz;
        float xz = yx * zy - yy * zx;
        // Read the ModelPart angles (Z, then Y, then X) back out of that frame.
        float turn = (float) Math.asin(Mth.clamp(-xz, -1.0f, 1.0f));
        float pitch = (float) Mth.atan2(yz, zz);
        float roll = (float) Mth.atan2(xy, xx);
        ikRaise = -pitch - lean;
        ikOut = -side * turn;
        ikSplay = -side * roll;
        ikBend = (float) Mth.atan2(zl, along);
    }

    /** A world point into model space (modelX/Y/Z, px), for this frame's body. */
    private void toModel(double wx, double wy, double wz) {
        float c = Mth.cos(bodyYawRad);
        float s = Mth.sin(bodyYawRad);
        double dx = wx - bodyX;
        double dz = wz - bodyZ;
        modelX = (float) ((dx * c + dz * s) * 16.0 / SCALE);
        modelY = (float) (24.0 - (wy - bodyY) * 16.0 / SCALE);
        modelZ = (float) ((dx * s - dz * c) * 16.0 / SCALE);
    }

    /** A model point (px) into the world (worldX/Y/Z), for this frame's body (the renderer turns it to the body's yaw and flips it). */
    private void toWorld(float mx, float my, float mz) {
        float c = Mth.cos(bodyYawRad);
        float s = Mth.sin(bodyYawRad);
        worldX = bodyX + (mx * c + mz * s) / 16.0f * SCALE;
        worldY = bodyY + (24.0f - my) / 16.0f * SCALE;
        worldZ = bodyZ + (mx * s - mz * c) / 16.0f * SCALE;
    }

    /** Notes where each claw tip is shown this frame (world), so the next reach starts from there. */
    private void noteClaws(BacteriaPoseState m) {
        for (int i = 0; i < 2; i++) {
            clawPoint(i, raise[i], out[i], splay[i], bend[i], foreOut[i], curl[i]);
            toWorld(clawMX, clawMY, clawMZ);
            m.clawX[i] = worldX;
            m.clawY[i] = worldY;
            m.clawZ[i] = worldZ;
        }
        m.clawsKnown = true;
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
     * {@code i} winds up for 2 ticks, cocked up and out to its side like a mantis's strike (the
     * anticipation), strikes across the front in 1.5 ticks, swings on past for a tick (the
     * follow-through), then goes back to what it was doing over 3.5. The strike itself keeps its
     * snap (strikeArm). The other arm carries on.
     */
    private void swipe(int i, float a) {
        float r = raise[i];
        float o = out[i];
        float b = bend[i];
        if (a < SWIPE_WIND_END) {
            float k = smooth(a / SWIPE_WIND_END);
            raise[i] = Mth.lerp(k, r, SWIPE_COCK_RAISE * DEG);
            out[i] = Mth.lerp(k, o, SWIPE_COCK_OUT * DEG);
            bend[i] = Mth.lerp(k, b, SWIPE_COCK_BEND * DEG);
        } else if (a < SWIPE_STRIKE_END) {
            float k = smooth((a - SWIPE_WIND_END) / (SWIPE_STRIKE_END - SWIPE_WIND_END));
            raise[i] = Mth.lerp(k, SWIPE_COCK_RAISE, SWIPE_ACROSS_RAISE) * DEG;
            out[i] = Mth.lerp(k, SWIPE_COCK_OUT, SWIPE_ACROSS_OUT) * DEG;
            bend[i] = Mth.lerp(k, SWIPE_COCK_BEND, SWIPE_ACROSS_BEND) * DEG;
            strikeArm = i;
        } else if (a < SWIPE_FOLLOW_END) {
            float k = smooth((a - SWIPE_STRIKE_END) / (SWIPE_FOLLOW_END - SWIPE_STRIKE_END));
            raise[i] = Mth.lerp(k, SWIPE_ACROSS_RAISE, SWIPE_FOLLOW_RAISE) * DEG;
            out[i] = Mth.lerp(k, SWIPE_ACROSS_OUT, SWIPE_FOLLOW_OUT) * DEG;
            bend[i] = Mth.lerp(k, SWIPE_ACROSS_BEND, SWIPE_FOLLOW_BEND) * DEG;
            strikeArm = i;
        } else {
            float k = smooth((a - SWIPE_FOLLOW_END) / (SWIPE_TICKS - SWIPE_FOLLOW_END));
            raise[i] = Mth.lerp(k, SWIPE_FOLLOW_RAISE * DEG, r);
            out[i] = Mth.lerp(k, SWIPE_FOLLOW_OUT * DEG, o);
            bend[i] = Mth.lerp(k, SWIPE_FOLLOW_BEND * DEG, b);
        }
        splay[i] = 0.0f;
        foreOut[i] = 0.0f;
        curl[i] = -10.0f * DEG;
        spread[i] = 30.0f * DEG;
        armPlant[i] = 0.0f;
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

    // ------------------------------------------------------------------ fingers, springs

    /**
     * Each finger slowly curls and uncurls on its own 30-80 tick cycle by 15-35 degrees. Running,
     * they close and open with the arm's swing instead (fingerCycle), each a little different.
     * When they hold still they stay at fingerRest.
     */
    private void fingers(int seed, float t) {
        for (int i = 0; i < 2; i++) {
            for (int f = 0; f < 3; f++) {
                float c = fingerRest;
                if (fingersHold) {
                    c = fingerHold[i] + 3.0f * (f - 1);
                } else if (fingersCycle) {
                    c = fingerCycle[i] + 4.0f * (f - 1);
                } else if (fingersLive) {
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
     * Carries every joint from where it was shown last frame toward this frame's pose, each
     * through its own spring (spring()). A pose that moves steadily is followed without lagging;
     * a sudden change (a new state, the start or end of a one-shot move, a limit) is followed
     * smoothly at a believable speed, keeping whatever speed the joint already had, so nothing
     * snaps and nothing starts from a standstill. The upper arm leads, the forearm swings on a
     * little after it, the hand and fingers a little more; the body is heavy and doesn't wobble.
     * Planted arms, stepping legs, the leap's fling and the swipe's strike follow stiffly.
     */
    private void springs(BacteriaPoseState memory, float dt, boolean fresh, boolean hurt) {
        springMemory = memory;
        springDt = dt;
        springFresh = fresh || !memory.springsReady;
        memory.springsReady = true;
        float armSpeed = ARM_MAX_SPEED * DEG;
        float target = TARGET_MAX_SPEED * DEG;
        for (int i = 0; i < 2; i++) {
            boolean strike = strikeBoth || strikeArm == i;
            float stiff = 1.0f + PLANT_STIFFEN * armPlant[i];
            float upperHz = strike ? STRIKE_HZ : UPPER_ARM_HZ * stiff;
            float upperDamp = strike ? 1.0f : UPPER_ARM_DAMP;
            float foreHz = strike ? STRIKE_HZ : FOREARM_HZ * stiff;
            // A planted forearm is damped more too, so it doesn't swing the claw about.
            float foreDamp = strike ? 1.0f : Mth.lerp(armPlant[i], FOREARM_DAMP, 0.9f);
            float handHz = strike ? STRIKE_HZ : HAND_HZ;
            float handDamp = strike ? 1.0f : HAND_DAMP;
            float top = strike ? STRIKE_MAX_SPEED * DEG : armSpeed;
            // The fling and the strike get no lead from the pose's own speed: a lead overshoots
            // when a fast move stops (the leap's arms swung up past the T, above the head).
            float follow = strike ? 0.0f : target;
            raise[i] = spring(CH_RAISE + i, raise[i], upperHz, upperDamp, top, follow);
            out[i] = spring(CH_OUT + i, out[i], upperHz, upperDamp, top, follow);
            splay[i] = spring(CH_SPLAY + i, splay[i], upperHz, upperDamp, top, follow);
            bend[i] = spring(CH_BEND + i, bend[i], foreHz, foreDamp, top, follow);
            foreOut[i] = spring(CH_FORE + i, foreOut[i], foreHz, foreDamp, top, follow);
            curl[i] = spring(CH_CURL + i, curl[i], handHz, handDamp, top, follow);
            spread[i] = spring(CH_SPREAD + i, spread[i], handHz, handDamp, top, follow);
            for (int f = 0; f < 3; f++) {
                // (Finger curls are in degrees.)
                fingerCurl[i][f] = spring(CH_FINGER + 3 * i + f, fingerCurl[i][f], HAND_HZ, HAND_DAMP,
                        ARM_MAX_SPEED, TARGET_MAX_SPEED);
            }
        }
        float bodyHz = hurt ? BODY_HZ * HURT_STIFFEN : BODY_HZ;
        float bodySpeed = BODY_MAX_SPEED * DEG;
        lean = spring(CH_LEAN, lean, bodyHz, BODY_DAMP, bodySpeed, target);
        sway = spring(CH_SWAY, sway, bodyHz, BODY_DAMP, bodySpeed, target);
        legSplay = spring(CH_LEG_SPLAY, legSplay, BODY_HZ, BODY_DAMP, bodySpeed, target);
        neckX = spring(CH_NECK_X, neckX, NECK_HZ, NECK_DAMP, bodySpeed, target);
        neckZ = spring(CH_NECK_Z, neckZ, NECK_HZ, NECK_DAMP, bodySpeed, target);
        headRoll = spring(CH_HEAD_ROLL, headRoll, NECK_HZ, NECK_DAMP, bodySpeed, target);
        neckForward = spring(CH_NECK_FORWARD, neckForward, NECK_HZ, NECK_DAMP, PUSH_MAX_SPEED, PUSH_TARGET_MAX_SPEED);
        headForward = spring(CH_HEAD_FORWARD, headForward, NECK_HZ, NECK_DAMP, PUSH_MAX_SPEED, PUSH_TARGET_MAX_SPEED);
        // (Degrees.)
        headPitchAdd = spring(CH_HEAD_PITCH, headPitchAdd, HEAD_PITCH_HZ, NECK_DAMP, HEAD_PITCH_MAX_SPEED, TARGET_MAX_SPEED);
        freeWeight = spring(CH_FREE, freeWeight, NECK_HZ, 1.0f, 0.5f, 1.0f);
        // Between poses the legs move like the body (a spring); once it is stepping they follow
        // their feet exactly, so a planted foot stays put on the floor (blended in as the stride
        // fades in, so there is no jump either way).
        float exact = Mth.clamp(gaitTarget * 4.0f, 0.0f, 1.0f);
        float legSpeed = LEG_MAX_SPEED * DEG;
        thighR = legSpring(CH_THIGH, thighR, exact, legSpeed, target * 2.0f);
        thighL = legSpring(CH_THIGH + 1, thighL, exact, legSpeed, target * 2.0f);
        shinR = legSpring(CH_SHIN, shinR, exact, legSpeed, target * 2.0f);
        shinL = legSpring(CH_SHIN + 1, shinL, exact, legSpeed, target * 2.0f);
        float gait = spring(CH_GAIT, gaitTarget, BODY_HZ, 1.0f, 0.5f, 1.0f);
        // How far the hips sink: from the legs as shown (so the feet stay on the floor), or, while
        // stepping (both feet may be off the floor mid-run), the stepping crouch.
        float standing = Math.min(legDrop(thighR, shinR), legDrop(thighL, shinL));
        drop = Mth.lerp(Mth.clamp(gait, 0.0f, 1.0f), standing, gaitCrouch);
        gaitWeight = Mth.clamp(gait, 0.0f, 1.0f);
    }

    /**
     * A leg's spring, pulled onto its pose by {@code exact} (0 = just the spring, 1 = exactly the
     * pose, moving at the pose's own speed), so a stepping foot doesn't slide.
     */
    private float legSpring(int ch, float target, float exact, float maxSpeed, float targetSpeed) {
        float shown = spring(ch, target, LEG_HZ, LEG_DAMP, maxSpeed, targetSpeed);
        if (exact <= 0.0f || springFresh || springDt <= 0.0f) {
            return shown;
        }
        BacteriaPoseState m = springMemory;
        shown = Mth.lerp(exact, shown, target);
        m.springPos[ch] = shown;
        m.springVel[ch] = Mth.lerp(exact, m.springVel[ch], springTargetVel);
        return shown;
    }

    /**
     * One spring (a second-order follower, after t3ssel8r's "Giving Personality to Procedural
     * Animations"): moves value number {@code ch} from where it was shown last frame toward
     * {@code target}. {@code hz}: how quickly it follows; {@code damp}: 1 = no overshoot, less =
     * a little swing past and back. It is fed how fast the target itself moves, so it keeps up
     * with a steadily moving pose instead of trailing it; but if the target moves faster than
     * {@code targetSpeed} a tick it has jumped (a new state, a one-shot starting or ending), and
     * then it gets no such lead, only the spring, so it doesn't fling past its new pose. It never
     * moves faster than {@code maxSpeed} a tick. Returns what to show this frame.
     */
    private float spring(int ch, float target, float hz, float damp, float maxSpeed, float targetSpeed) {
        BacteriaPoseState m = springMemory;
        if (springFresh) {
            m.springPos[ch] = target;
            m.springVel[ch] = 0.0f;
            m.springLastTarget[ch] = target;
            return target;
        }
        float dt = springDt;
        if (dt <= 0.0f) {
            return m.springPos[ch]; // drawn twice this frame: show the same again
        }
        float w = Mth.TWO_PI * hz / 20.0f;  // per tick
        float k1 = 2.0f * damp / w;
        float k2 = 1.0f / (w * w);
        float targetVel = (target - m.springLastTarget[ch]) / dt;
        if (Math.abs(targetVel) > targetSpeed) {
            targetVel = 0.0f;
        }
        m.springLastTarget[ch] = target;
        springTargetVel = targetVel;
        // (Keeps it steady even at a low frame rate.)
        float k2s = Math.max(k2, Math.max(dt * dt / 2.0f + dt * k1 / 2.0f, dt * k1));
        float pos = m.springPos[ch] + dt * m.springVel[ch];
        float vel = m.springVel[ch] + dt * (target + k1 * targetVel - pos - k1 * m.springVel[ch]) / k2s;
        m.springPos[ch] = pos;
        m.springVel[ch] = Mth.clamp(vel, -maxSpeed, maxSpeed);
        return pos;
    }

    /**
     * The step's waves, on top of the springs, while it walks or runs (gaitWeight): the body
     * dips a beat ({@code stepLag}) after each foot lands; the spine rocks toward the planted foot;
     * the hips turn toward the forward leg and the shoulders the other way (a planted arm is
     * turned back against that, so its claw doesn't slide); and the head dips and rocks with the
     * steps. All smooth waves (cosines), never a jump.
     */
    private void stepWaves(float phase, float stepLag) {
        float w = gaitWeight;
        if (w <= 0.0f) {
            return;
        }
        float g = runness;
        // Lowest a beat after each foot lands (at 0 and half a stride).
        float dip = 0.5f + 0.5f * Mth.cos(2.0f * Mth.TWO_PI * (phase - stepLag));
        bob = w * Mth.lerp(g, WALK_BOB, RUN_BOB) * dip;
        // The right foot is planted in the first part of the stride: rock to the right (-) then.
        sway -= w * Mth.lerp(g, WALK_ROCK, RUN_ROCK) * DEG * Mth.sin(Mth.TWO_PI * (phase - ROCK_LAG));
        // The right leg is forward when it lands (phase 0): hips turned right side forward (-).
        hipYaw = -w * Mth.lerp(g, WALK_HIP_YAW, RUN_HIP_YAW) * DEG * Mth.cos(Mth.TWO_PI * phase);
        spineYaw = -SHOULDER_COUNTER * hipYaw;
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? -1.0f : 1.0f;
            out[i] += side * spineYaw * armPlant[i];
        }
        headPitchAdd += w * STEP_JOLT_PITCH * dip;
        headRoll += w * STEP_JOLT_ROLL * DEG * Mth.sin(Mth.TWO_PI * (phase - stepLag));
    }

    /**
     * A smooth looping curve through keyframes (a Catmull-Rom spline): {@code phases} run from 0
     * to 1 and {@code values} are the keys, the last repeating the first. Unlike easing from key
     * to key, it keeps moving through each key, so a joint never stops dead at one.
     */
    private static float cyclic(float p, float[] phases, float[] values) {
        p -= Mth.floor(p);
        int n = phases.length - 1;
        int k = 0;
        while (k < n - 1 && p >= phases[k + 1]) {
            k++;
        }
        float h = phases[k + 1] - phases[k];
        float u = (p - phases[k]) / h;
        float u2 = u * u;
        float u3 = u2 * u;
        float m0 = tangent(k, phases, values, n) * h;
        float m1 = tangent(k + 1, phases, values, n) * h;
        return (2.0f * u3 - 3.0f * u2 + 1.0f) * values[k] + (u3 - 2.0f * u2 + u) * m0
                + (-2.0f * u3 + 3.0f * u2) * values[k + 1] + (u3 - u2) * m1;
    }

    /** How steeply the curve passes through key {@code i} (looping round): from its two neighbours. */
    private static float tangent(int i, float[] phases, float[] values, int n) {
        int j = i % n;
        float tPrev = j == 0 ? phases[n - 1] - 1.0f : phases[j - 1];
        float yPrev = values[j == 0 ? n - 1 : j - 1];
        return (values[j + 1] - yPrev) / (phases[j + 1] - tPrev);
    }

    /** 0 before {@code on0}, easing up to 1 by {@code on1}, 1 until {@code off0}, easing back to 0 by {@code off1}. */
    private static float window(float p, float on0, float on1, float off0, float off1) {
        if (p < on0 || p >= off1) {
            return 0.0f;
        }
        if (p < on1) {
            return smooth((p - on0) / (on1 - on0));
        }
        return p < off0 ? 1.0f : 1.0f - smooth((p - off0) / (off1 - off0));
    }

    /** {@code x} unchanged up to {@code limit - soft}, then slowing smoothly so it never quite passes {@code limit}. */
    private static float softLimit(float x, float limit, float soft) {
        float a = Math.abs(x);
        float start = limit - soft;
        if (a <= start) {
            return x;
        }
        return Math.copySign(start + soft * (1.0f - (float) Math.exp(-(a - start) / soft)), x);
    }

    // ------------------------------------------------------------------ intent: the head

    /**
     * Aims the head. Locked on someone, it points at their eyes in the WORLD: the angle is worked
     * out from where its head really is this frame, and then everything the spine and neck are
     * doing is taken back off, so the head stays aimed whatever the body does. It may turn up to
     * 150 degrees from where the body faces (slowing to a stop near the limit). The aim is a
     * spring whose stiffness depends on the state: slowly noticing you while calm, a 2-3 tick
     * whip round when it spots you, stiff (on your eyes within a frame or two, but never snapping)
     * while it runs or holds you. With nobody to look at it holds its own direction (headYawFree /
     * headPitchFree). headPitchAdd then tips it further down or up (bending over to look; thrown
     * back when hit; the step's dip).
     */
    private void aimHead(Bacteria entity, BacteriaPoseState memory, float partial, float dt, boolean fresh, int targetId) {
        float bodyYaw = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
        // The rotation the head inherits: spine (with the shoulders' twist), then neck.
        rotation(matA, sway, spineYaw, lean);
        rotation(matB, neckZ, 0.0f, neckX);
        multiply(matParent, matA, matB);

        Entity target = aimMode != AIM_NONE && targetId >= 0 ? entity.level().getEntity(targetId) : null;
        float wantYaw;
        float wantPitch;
        float hz;
        float damp;
        float topSpeed;
        if (target != null || aimPointOn) {
            // Where its head is in model space: hips, up the spine, the neck, about 4.6 px up it.
            float hipY = 24.0f - HIP_HEIGHT + drop + bob;
            float nx = matA[1] * -SPINE + matA[2] * -neckForward;
            float ny = hipY + matA[4] * -SPINE + matA[5] * -neckForward;
            float nz = matA[7] * -SPINE + matA[8] * -neckForward;
            float hz0 = -0.4f - headForward;
            float px = nx + matParent[1] * -4.6f + matParent[2] * hz0;
            float py = ny + matParent[4] * -4.6f + matParent[5] * hz0;
            float pz = nz + matParent[7] * -4.6f + matParent[8] * hz0;
            // ...and in the world (the renderer turns the model to the body's yaw and flips it).
            float r = bodyYaw * DEG;
            float cos = Mth.cos(r);
            float sin = Mth.sin(r);
            double worldX = Mth.lerp(partial, entity.xo, entity.getX()) + (px * cos + pz * sin) / 16.0f * SCALE;
            double worldY = Mth.lerp(partial, entity.yo, entity.getY()) + (24.0f - py) / 16.0f * SCALE;
            double worldZ = Mth.lerp(partial, entity.zo, entity.getZ()) + (px * sin - pz * cos) / 16.0f * SCALE;
            // Its target's eyes, or the place it looks at (the hider's eyes inside the furniture).
            double dx = (target != null ? Mth.lerp(partial, target.xo, target.getX()) : aimPX) - worldX;
            double dy = (target != null ? Mth.lerp(partial, target.yo, target.getY()) + target.getEyeHeight() : aimPY) - worldY;
            double dz = (target != null ? Mth.lerp(partial, target.zo, target.getZ()) : aimPZ) - worldZ;
            wantYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
            wantPitch = (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
            if (aimMode == LOCK_EXACT) {
                hz = AIM_EXACT_HZ;
                damp = AIM_EXACT_DAMP;
                topSpeed = AIM_EXACT_SPEED;
            } else if (aimMode == LOCK_FAST) {
                hz = AIM_FAST_HZ;
                damp = AIM_FAST_DAMP;
                topSpeed = AIM_FAST_SPEED;
            } else if (aimMode == LOCK_CREEP) {
                // Creeping: its head stays on the hiding place, turning slowly and steadily.
                hz = AIM_CREEP_HZ;
                damp = 1.0f;
                topSpeed = AIM_CREEP_SPEED;
            } else {
                boolean onTarget = Math.abs(Mth.wrapDegrees(wantYaw - memory.aimYaw)) + Math.abs(wantPitch - memory.aimPitch) < 4.0f;
                hz = onTarget ? AIM_TRACK_HZ : AIM_SLOW_HZ;
                damp = AIM_CALM_DAMP;
                topSpeed = onTarget ? AIM_TRACK_SPEED : AIM_SLOW_SPEED;
            }
        } else {
            wantYaw = bodyYaw + headYawFree;
            wantPitch = headPitchFree;
            hz = AIM_FREE_HZ;
            damp = AIM_FREE_DAMP;
            topSpeed = AIM_FREE_SPEED;
        }
        if (fresh || !memory.aimed) {
            memory.aimYaw = wantYaw;
            memory.aimPitch = wantPitch;
            memory.aimYawSpeed = 0.0f;
            memory.aimPitchSpeed = 0.0f;
            memory.aimYawWanted = wantYaw;
            memory.aimPitchWanted = wantPitch;
            memory.aimed = true;
        } else if (dt > 0.0f) {
            // The same spring as spring(), worked on the angle to go (so it turns the short way round).
            float w = Mth.TWO_PI * hz / 20.0f;
            float k1 = 2.0f * damp / w;
            float k2s = Math.max(1.0f / (w * w), Math.max(dt * dt / 2.0f + dt * k1 / 2.0f, dt * k1));
            // (A target that jumps gives no lead, as in spring().)
            float yawTargetSpeed = Mth.wrapDegrees(wantYaw - memory.aimYawWanted) / dt;
            float pitchTargetSpeed = (wantPitch - memory.aimPitchWanted) / dt;
            if (Math.abs(yawTargetSpeed) > AIM_EXACT_SPEED) {
                yawTargetSpeed = 0.0f;
            }
            if (Math.abs(pitchTargetSpeed) > AIM_EXACT_SPEED) {
                pitchTargetSpeed = 0.0f;
            }
            memory.aimYawWanted = wantYaw;
            memory.aimPitchWanted = wantPitch;
            memory.aimYaw += dt * memory.aimYawSpeed;
            memory.aimPitch += dt * memory.aimPitchSpeed;
            float yawToGo = Mth.wrapDegrees(wantYaw - memory.aimYaw);
            float pitchToGo = wantPitch - memory.aimPitch;
            memory.aimYawSpeed += dt * (yawToGo + k1 * (yawTargetSpeed - memory.aimYawSpeed)) / k2s;
            memory.aimPitchSpeed += dt * (pitchToGo + k1 * (pitchTargetSpeed - memory.aimPitchSpeed)) / k2s;
            memory.aimYawSpeed = Mth.clamp(memory.aimYawSpeed, -topSpeed, topSpeed);
            memory.aimPitchSpeed = Mth.clamp(memory.aimPitchSpeed, -topSpeed, topSpeed);
            memory.aimYaw = Mth.wrapDegrees(memory.aimYaw);
        }
        float yaw = softLimit(Mth.wrapDegrees(memory.aimYaw - bodyYaw), HEAD_YAW_LIMIT, HEAD_YAW_SOFT);
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
        hipYaw = 0.0f;
        spineYaw = 0.0f;
        gaitTarget = 0.0f;
        gaitCrouch = 0.0f;
        aimMode = AIM_NONE;
        fingersLive = true;
        fingersCycle = false;
        fingersHold = false;
        fingerRest = 10.0f;
        for (int i = 0; i < 2; i++) {
            raise[i] = 0.0f;
            out[i] = 0.0f;
            splay[i] = 0.0f;
            bend[i] = 0.0f;
            foreOut[i] = 0.0f;
            curl[i] = 0.0f;
            spread[i] = 0.0f;
            armPlant[i] = 0.0f;
        }
    }

    /**
     * Walking or running legs (faded in by {@code m} over the stance already set): each foot is
     * placed with real geometry. While planted it slides back under the body exactly as fast as
     * the body moves forward ({@code stride} blocks per full stride), so it stays put on the
     * floor; then it lifts and swings forward in an arc to land ahead again. The right leg is at
     * {@code phase} (0 = its foot lands), the left half a stride later. Planted time, hip height,
     * lift and the rest go from the walk's to the run's with the speed (runness). The thigh and
     * shin angles that put each foot there are worked out by footAngles().
     */
    private void gaitLegs(float phase, float m, float stride) {
        if (m <= 0.0f) {
            return;
        }
        float g = runness;
        float stanceShare = Mth.lerp(g, WALK_STANCE, RUN_STANCE);
        float crouch = Mth.lerp(g, WALK_CROUCH, RUN_CROUCH) + 3.0f * squeeze;
        float lift = Mth.lerp(g, WALK_LIFT, RUN_LIFT);
        float skew = g * RUN_LIFT_SKEW;
        float push = Mth.lerp(g, WALK_SWING_PUSH, RUN_SWING_PUSH);
        // How far (px) the foot slides back while planted: as far as the body goes meanwhile.
        float step = stride * stanceShare * 16.0f / SCALE;
        float hipHeight = HIP_HEIGHT - crouch;
        for (int leg = 0; leg < 2; leg++) {
            float p = phase + 0.5f * leg;
            p -= Mth.floor(p);
            float z;     // where the foot is, ahead (-) or behind (+) the hip (px)
            float depth; // how far below the hip (px)
            if (p < stanceShare) {
                z = -step / 2.0f + step * p / stanceShare;
                depth = hipHeight;
            } else {
                // The swing: from behind to ahead along a smooth curve that leaves and meets the
                // planted foot's backward slide without a jerk, lifted in an arc (running, the
                // foot folds up early, behind it).
                float u = (p - stanceShare) / (1.0f - stanceShare);
                float slide = step / stanceShare * (1.0f - stanceShare) * push;
                float u2 = u * u;
                float u3 = u2 * u;
                z = (2.0f * u3 - 3.0f * u2 + 1.0f) * (step / 2.0f) + (u3 - 2.0f * u2 + u) * slide
                        + (-2.0f * u3 + 3.0f * u2) * (-step / 2.0f) + (u3 - u2) * slide;
                float arc = (1.0f - skew) * 4.0f * u * (1.0f - u) + skew * 6.75f * u * (1.0f - u) * (1.0f - u);
                depth = hipHeight - lift * arc;
            }
            footAngles(z, depth);
            if (leg == 0) {
                thighR = Mth.lerp(m, thighR, footThigh);
                shinR = Mth.lerp(m, shinR, footShin);
            } else {
                thighL = Mth.lerp(m, thighL, footThigh);
                shinL = Mth.lerp(m, shinL, footShin);
            }
        }
        gaitTarget = m;
        gaitCrouch = crouch;
    }

    /**
     * The thigh and shin angles (into footThigh/footShin) that put the foot {@code z} px ahead (-)
     * or behind (+) the hip and {@code depth} px below it, the knee bending forward (two-bone
     * reach: the law of cosines on the thigh, the shin and the hip-to-foot line).
     */
    private void footAngles(float z, float depth) {
        float reach = Math.min(Mth.sqrt(z * z + depth * depth), THIGH + SHIN - LEG_REACH_MARGIN);
        float line = (float) Mth.atan2(z, depth);
        float atHip = (float) Math.acos(Mth.clamp((THIGH * THIGH + reach * reach - SHIN * SHIN) / (2.0f * THIGH * reach), -1.0f, 1.0f));
        float atFoot = (float) Math.acos(Mth.clamp((SHIN * SHIN + reach * reach - THIGH * THIGH) / (2.0f * SHIN * reach), -1.0f, 1.0f));
        footThigh = line - atHip;
        footShin = atHip + atFoot;
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
     * Keeps both claws within about {@code maxBlocks} of its middle, out to the side, measured in
     * the world (whatever the spine's lean does to the arm angles). It is soft: a claw going past
     * MAX_SPREAD - SPREAD_KNEE is slowed more and more instead of hitting a wall, so the arm
     * never jerks to a stop. The arm is turned back in: swung in (out) if it reaches forward,
     * tilted in (splay) if it hangs.
     */
    private void limitSpread(float maxBlocks) {
        float limit = maxBlocks * 16.0f / SCALE; // in model px
        float knee = SPREAD_KNEE * 16.0f / SCALE;
        for (int i = 0; i < 2; i++) {
            for (int k = 0; k < 3; k++) {
                measureClaw(i);
                float excess = clawSide - softLimit(clawSide, limit, knee);
                if (excess <= 0.01f) {
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
        clawPoint(i, raise[i], out[i], splay[i], bend[i], foreOut[i], curl[i]);
        clawSide = (i == 0 ? -1.0f : 1.0f) * clawMX;
        clawAhead = -clawMZ;
    }

    /**
     * Where arm {@code i}'s middle claw tip is (model px, into clawMX/MY/MZ) with these arm
     * angles and this frame's body: the same chain of joints the model parts use (hips, up the
     * spine with its lean, rock and twist, the shoulder, upper arm, forearm, hand).
     */
    private void clawPoint(int i, float r, float o, float sp, float bd, float fo, float cu) {
        float side = i == 0 ? -1.0f : 1.0f;
        rotation(matSpine, sway, spineYaw, lean);
        rotation(matA, -side * sp, -side * o, -(r + lean));
        multiply(matArm, matSpine, matA);
        rotation(matA, -side * fo, 0.0f, bd);
        multiply(matFore, matArm, matA);
        rotation(matA, 0.0f, 0.0f, cu);
        multiply(matHand, matFore, matA);
        // The shoulder: up the spine to the shoulder bar, out to this side (and the lopsided offset).
        float sy = -SPINE + (i == lowArm ? LOW_SHOULDER_DROP : 0.0f);
        float sz = i == lowArm ? -LOW_SHOULDER_FORWARD : 0.0f;
        float x = matSpine[0] * side * SHOULDER_HALF + matSpine[1] * sy + matSpine[2] * sz;
        float y = 24.0f - HIP_HEIGHT + drop + bob + matSpine[3] * side * SHOULDER_HALF + matSpine[4] * sy + matSpine[5] * sz;
        float z = matSpine[6] * side * SHOULDER_HALF + matSpine[7] * sy + matSpine[8] * sz;
        // Upper arm, forearm, then the middle claw (the same points the arm parts are built from).
        x += matArm[0] * side * 1.5f + matArm[1] * UPPER_ARM;
        y += matArm[3] * side * 1.5f + matArm[4] * UPPER_ARM;
        z += matArm[6] * side * 1.5f + matArm[7] * UPPER_ARM;
        clawElbowY = y;
        x += matFore[0] * side * 1.5f + matFore[1] * FOREARM - matFore[2] * 0.5f;
        y += matFore[3] * side * 1.5f + matFore[4] * FOREARM - matFore[5] * 0.5f;
        z += matFore[6] * side * 1.5f + matFore[7] * FOREARM - matFore[8] * 0.5f;
        x += matHand[0] * side * 0.9f * ARM_STRETCH + matHand[1] * CLAW_TIP;
        y += matHand[3] * side * 0.9f * ARM_STRETCH + matHand[4] * CLAW_TIP;
        z += matHand[6] * side * 0.9f * ARM_STRETCH + matHand[7] * CLAW_TIP;
        clawMX = x;
        clawMY = y;
        clawMZ = z;
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
        // The step's twist: the hips (legs) turn one way, the shoulders (spine) the other.
        rightLeg.yRot = hipYaw;
        leftLeg.yRot = hipYaw;
        spine.yRot = spineYaw;
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
     * Smooth noise from about -1 to 1: a new random value every {@code period} ticks, with a
     * curve through them that keeps gliding (it never stops at one and sets off again).
     */
    private static float noise(int seed, float t, float period) {
        float x = t / period;
        int b = Mth.floor(x);
        float u = x - b;
        float y0 = BacteriaRhythm.hash(seed, b - 1);
        float y1 = BacteriaRhythm.hash(seed, b);
        float y2 = BacteriaRhythm.hash(seed, b + 1);
        float y3 = BacteriaRhythm.hash(seed, b + 2);
        // A Catmull-Rom curve through the four nearest values.
        return 0.5f * (2.0f * y1 + (y2 - y0) * u + (2.0f * y0 - 5.0f * y1 + 4.0f * y2 - y3) * u * u
                + (3.0f * y1 - y0 - 3.0f * y2 + y3) * u * u * u);
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

}
