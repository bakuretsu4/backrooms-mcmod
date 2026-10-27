package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.SkinStealer;
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

/**
 * The Skin-stealer, built in code: a tall (about 2.8 blocks), gaunt figure with pale yellow skin
 * stretched over its bones, arms so long its fingers hang by its shins, and a narrow head with
 * deep eye sockets, each holding a small white eye far back in the dark.
 *
 * <pre>
 *         _____
 *        |  ▀  |  ← scalp of stolen skin, slipped askew, hair hanging off it
 *        |(·)(·)|    sunken white eyes (the face is built AROUND two holes, so they are real hollows)
 *   ‾‾\  \_▄▄_/  /‾‾ ← stolen face hanging off one side of the head like a loose mask
 *      \  |||| /     ribs, collarbones and spine knobs under tight yellow skin
 *     |≡≡[ ⌒⌒ ]≡≡|   loose sleeves of skin on the arms, a sagging skin "apron" over chest and belly,
 *     |  [    ]  |   a shawl of skin over the back, flaps over the thighs: all of it hangs from
 *     |  |    |  |   the body and swings as it moves, a beat behind
 *    ///  |  |  \\\  long fingers, three joints each
 * </pre>
 *
 * <p>The stolen skin is the point of the design, so every piece of it is a separate part that
 * hangs straight down under gravity (it counter-rotates when the body leans) and lags behind
 * the movement.
 *
 * <p>Model space: units are pixels, +Y down, front is -Z, feet at y = 24. Texture 128x128 units
 * (PNG twice that): its own yellow skin, the stolen skin (outside and the raw inner side), hair,
 * bone-tight skin, dark socket, eye white, teeth, nails.
 */
public class SkinStealerModel extends HierarchicalModel<SkinStealer> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Backrooms.id("skin_stealer"), "main");

    private static final int[] BODY = {0, 0, 64, 64};
    private static final int[] STOLEN = {64, 0, 64, 64};
    private static final int[] STOLEN_INNER = {0, 64, 64, 32};
    private static final int[] HAIR = {64, 64, 32, 32};
    private static final int[] BONE = {96, 64, 32, 32};
    private static final int[] SOCKET = {0, 96, 16, 16};
    private static final int[] EYE = {16, 96, 16, 16};
    private static final int[] TEETH = {32, 96, 16, 16};
    private static final int[] NAILS = {48, 96, 16, 16};
    private static final int[] MOUTH = {64, 96, 16, 16};

    // About 3 blocks standing straight; its usual stoop brings it under its 2.7-block hitbox.
    private static final float THIGH = 10.0f;
    private static final float SHIN = 9.5f;
    private static final float HIP_Y = 24.0f - THIGH - SHIN - 0.8f;
    private static final float TORSO = 14.0f;
    private static final float UPPER_ARM = 12.0f;
    private static final float FOREARM = 11.5f;
    private static final float PALM = 3.6f;
    private static final float[] PHALANX = {2.3f, 2.0f, 1.7f};

    private final ModelPart root;
    private final ModelPart hips;
    private final ModelPart waist;
    private final ModelPart ribcage;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart[] thigh = new ModelPart[2];
    private final ModelPart[] shin = new ModelPart[2];
    private final ModelPart[] foot = new ModelPart[2];
    private final ModelPart[] arm = new ModelPart[2];
    private final ModelPart[] elbow = new ModelPart[2];
    private final ModelPart[] wrist = new ModelPart[2];
    private final ModelPart[][][] finger = new ModelPart[2][4][PHALANX.length];
    private final ModelPart[] cuff = new ModelPart[2];
    private final ModelPart[] thighFlap = new ModelPart[2];
    private final ModelPart skinBack;
    private final ModelPart skinFront;
    private final ModelPart bellyFlap;
    private final ModelPart skinSkirt;
    private final ModelPart faceFlap;
    private final ModelPart scalp;

    public SkinStealerModel(ModelPart root) {
        this.root = root;
        this.hips = root.getChild("hips");
        this.waist = hips.getChild("waist");
        this.ribcage = waist.getChild("ribcage");
        ModelPart shoulders = waist.getChild("shoulders");
        this.neck = shoulders.getChild("neck");
        this.head = neck.getChild("head");
        this.jaw = head.getChild("jaw");
        this.faceFlap = head.getChild("face_flap");
        this.scalp = head.getChild("scalp");
        for (int s = 0; s < 2; s++) {
            String side = s == 0 ? "right" : "left";
            thigh[s] = hips.getChild("thigh_" + side);
            shin[s] = thigh[s].getChild("shin");
            foot[s] = shin[s].getChild("foot");
            thighFlap[s] = thigh[s].getChild("flap");
            arm[s] = shoulders.getChild("arm_" + side);
            elbow[s] = arm[s].getChild("elbow");
            cuff[s] = elbow[s].getChild("cuff");
            wrist[s] = elbow[s].getChild("wrist");
            for (int f = 0; f < 4; f++) {
                ModelPart p = wrist[s];
                for (int j = 0; j < PHALANX.length; j++) {
                    p = p.getChild(j == 0 ? "finger" + f : "p" + j);
                    finger[s][f][j] = p;
                }
            }
        }
        this.skinBack = shoulders.getChild("skin_back");
        this.skinFront = shoulders.getChild("skin_front");
        this.bellyFlap = skinFront.getChild("belly_flap");
        this.skinSkirt = waist.getChild("skin_skirt");
    }

    // ================================================================== building

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        RandomSource rng = RandomSource.create(0x5C1257EAL);

        PartDefinition hips = root.addOrReplaceChild("hips", CubeListBuilder.create(), PartPose.offset(0, HIP_Y, 0));
        pelvis(hips, rng);
        for (int s = 0; s < 2; s++) {
            leg(hips, s, rng);
        }
        PartDefinition waist = hips.addOrReplaceChild("waist", CubeListBuilder.create(), PartPose.ZERO);
        torso(waist, rng);
        PartDefinition shoulders = waist.addOrReplaceChild("shoulders", CubeListBuilder.create(), PartPose.offset(0, -TORSO, 0));
        collar(shoulders, rng);
        for (int s = 0; s < 2; s++) {
            arm(shoulders, s, rng);
        }
        PartDefinition neck = shoulders.addOrReplaceChild("neck", CubeListBuilder.create(), PartPose.offset(0, -0.4f, -0.6f));
        rod(neck, "neck_rod", rng, BODY, 0, 0, 0, 0, -4.6f, -1.3f, 2.0f);
        rod(neck, "tendon_r", rng, BONE, -0.8f, 0.2f, -0.6f, -0.4f, -4.0f, -1.9f, 0.6f);
        rod(neck, "tendon_l", rng, BONE, 0.8f, 0.2f, -0.6f, 0.4f, -4.0f, -1.9f, 0.6f);
        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0, -4.6f, -1.3f));
        head(head, rng);
        stolenSkin(shoulders, waist, rng);
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static void pelvis(PartDefinition hips, RandomSource rng) {
        box(hips, "pelvis", rng, BODY, -3.0f, -1.5f, -1.6f, 6.0f, 3.0f, 3.2f);
        // Hip bones pushing out under the skin.
        lump(hips, "crest_r", rng, BONE, -2.6f, -1.2f, -0.6f, 1.6f, 1.8f, 1.8f);
        lump(hips, "crest_l", rng, BONE, 2.6f, -1.2f, -0.6f, 1.6f, 1.8f, 1.8f);
    }

    /** A long thin leg: thigh, knobbly knee, shin, and a long bony foot with toes. */
    private static void leg(PartDefinition hips, int s, RandomSource rng) {
        float sign = s == 0 ? -1.0f : 1.0f;
        String side = s == 0 ? "right" : "left";
        PartDefinition thigh = hips.addOrReplaceChild("thigh_" + side, CubeListBuilder.create(), PartPose.offset(sign * 2.0f, 0.6f, 0));
        rod(thigh, "bone", rng, BODY, 0, 0, 0, 0, THIGH, 0.2f, 2.5f);
        lump(thigh, "muscle", rng, BODY, 0.0f, 3.5f, -0.3f, 2.9f, 5.0f, 2.8f);
        PartDefinition shin = thigh.addOrReplaceChild("shin", CubeListBuilder.create(), PartPose.offset(0, THIGH, 0.2f));
        lump(shin, "knee", rng, BONE, 0.0f, 0.0f, -0.4f, 2.6f, 2.6f, 2.4f);
        rod(shin, "bone", rng, BODY, 0, 0, 0, 0, SHIN, 0.0f, 2.0f);
        rod(shin, "shinbone", rng, BONE, 0, 1.0f, -0.9f, 0, SHIN - 1.5f, -0.7f, 0.8f);
        PartDefinition foot = shin.addOrReplaceChild("foot", CubeListBuilder.create(), PartPose.offset(0, SHIN, 0));
        lump(foot, "heel", rng, BONE, 0.0f, 0.2f, 0.4f, 1.9f, 1.6f, 2.0f);
        box(foot, "sole", rng, BODY, -1.2f, -0.2f, -6.2f, 2.4f, 1.0f, 6.4f);
        for (int t = 0; t < 4; t++) {
            float tx = (t - 1.5f) * 0.6f;
            rod(foot, "toe" + t, rng, t == 0 ? NAILS : BODY, tx, 0.4f, -6.0f, tx * 1.2f, 0.75f, -7.6f - (t == 1 ? 0.3f : 0.0f), 0.5f);
        }
        // Loose stolen skin hanging over the front of the thigh, like a torn trouser leg.
        PartDefinition flap = thigh.addOrReplaceChild("flap", CubeListBuilder.create(), PartPose.offset(0, 0.4f, -1.6f));
        box(flap, "outer", rng, STOLEN, -1.9f, 0.0f, -0.3f, 3.8f, 9.0f, 0.3f);
        box(flap, "inner", rng, STOLEN_INNER, -1.8f, 0.0f, 0.0f, 3.6f, 8.6f, 0.15f);
        box(flap, "tatter", rng, STOLEN, sign * 0.4f, 9.0f, -0.3f, 1.3f, 2.5f, 0.3f);
    }

    /**
     * The trunk: a pinched waist, a narrow ribcage (its own part, so it can breathe) with the
     * ribs showing as bars across the front, a row of spine knobs down the back.
     */
    private static void torso(PartDefinition waist, RandomSource rng) {
        box(waist, "belly", rng, BODY, -2.2f, -6.5f, -1.3f, 4.4f, 6.5f, 2.6f);
        lump(waist, "gut", rng, BODY, 0.0f, -3.0f, -1.0f, 3.4f, 3.0f, 1.4f);
        PartDefinition ribs = waist.addOrReplaceChild("ribcage", CubeListBuilder.create(), PartPose.offset(0, -9.5f, 0));
        box(ribs, "chest", rng, BODY, -3.4f, -5.0f, -2.0f, 6.8f, 9.5f, 4.0f);
        for (int r = 0; r < 6; r++) {
            float y = -3.6f + r * 1.45f;
            float half = 3.5f - Math.abs(r - 1.5f) * 0.25f;
            // Each rib: from the side, curving forward to meet the breastbone a little lower.
            rod(ribs, "rib_r" + r, rng, BONE, -half, y, -0.6f, -0.4f, y + 0.9f, -2.25f, 0.55f);
            rod(ribs, "rib_l" + r, rng, BONE, half, y, -0.6f, 0.4f, y + 0.9f, -2.25f, 0.55f);
        }
        rod(ribs, "sternum", rng, BONE, 0.0f, -4.8f, -2.2f, 0.0f, 3.2f, -2.3f, 0.8f);
        for (int k = 0; k < 7; k++) {
            lump(waist, "spine" + k, rng, BONE, 0.0f, -2.0f - k * 2.1f, 1.4f + (k > 2 ? 0.7f : 0.0f), 1.0f, 1.0f, 0.9f);
        }
        // Shoulder blades sticking out at the back.
        lump(ribs, "blade_r", rng, BONE, -2.0f, -3.0f, 2.0f, 2.4f, 3.2f, 0.9f);
        lump(ribs, "blade_l", rng, BONE, 2.0f, -3.0f, 2.0f, 2.4f, 3.2f, 0.9f);
    }

    private static void collar(PartDefinition shoulders, RandomSource rng) {
        rod(shoulders, "collar_r", rng, BONE, -0.4f, 0.2f, -1.9f, -4.6f, -0.2f, -1.0f, 0.7f);
        rod(shoulders, "collar_l", rng, BONE, 0.4f, 0.2f, -1.9f, 4.6f, -0.2f, -1.0f, 0.7f);
        lump(shoulders, "trap_r", rng, BODY, -2.6f, -0.2f, 0.4f, 3.4f, 1.6f, 2.6f);
        lump(shoulders, "trap_l", rng, BODY, 2.6f, -0.2f, 0.4f, 3.4f, 1.6f, 2.6f);
    }

    /**
     * A very long arm: shoulder knob, upper arm in a loose sleeve of stolen skin, elbow, forearm,
     * a big hand with four three-jointed fingers ending in dark nails, and a thumb.
     */
    private static void arm(PartDefinition shoulders, int s, RandomSource rng) {
        float sign = s == 0 ? -1.0f : 1.0f;
        String side = s == 0 ? "right" : "left";
        PartDefinition arm = shoulders.addOrReplaceChild("arm_" + side, CubeListBuilder.create(), PartPose.offset(sign * 4.7f, 0.3f, -0.2f));
        lump(arm, "shoulder", rng, BONE, 0.0f, 0.3f, 0.0f, 2.4f, 2.2f, 2.4f);
        rod(arm, "bone", rng, BODY, 0, 0, 0, 0, UPPER_ARM, 0.0f, 1.9f);
        // The sleeve: two loose sheets of skin, wider than the arm, front and back.
        box(arm, "sleeve_front", rng, STOLEN, -1.7f, 1.0f, -1.5f, 3.4f, 9.5f, 0.3f);
        box(arm, "sleeve_back", rng, STOLEN, -1.7f, 1.2f, 1.2f, 3.4f, 9.0f, 0.3f);
        box(arm, "sleeve_side", rng, STOLEN, sign * 1.5f - 0.15f, 1.0f, -1.3f, 0.3f, 8.5f, 2.6f);
        PartDefinition elbow = arm.addOrReplaceChild("elbow", CubeListBuilder.create(), PartPose.offset(0, UPPER_ARM, 0));
        lump(elbow, "knob", rng, BONE, 0.0f, 0.0f, 0.5f, 2.0f, 2.0f, 2.0f);
        rod(elbow, "bone", rng, BODY, 0, 0, 0, 0, FOREARM, 0.0f, 1.6f);
        rod(elbow, "tendon", rng, BONE, sign * 0.5f, 1.5f, -0.6f, 0.2f, FOREARM - 1.0f, -0.6f, 0.5f);
        // The end of the sleeve has slid down to the elbow and hangs there in a loose cuff.
        PartDefinition cuff = elbow.addOrReplaceChild("cuff", CubeListBuilder.create(), PartPose.offset(0, -0.5f, 0));
        box(cuff, "front", rng, STOLEN, -1.6f, 0.0f, -1.4f, 3.2f, 4.0f, 0.3f);
        box(cuff, "back", rng, STOLEN_INNER, -1.5f, 0.0f, 1.1f, 3.0f, 3.2f, 0.3f);
        PartDefinition wrist = elbow.addOrReplaceChild("wrist", CubeListBuilder.create(), PartPose.offset(0, FOREARM, 0));
        box(wrist, "palm", rng, BODY, -1.45f, 0.0f, -0.55f, 2.9f, PALM, 1.1f);
        for (int f = 0; f < 4; f++) {
            float fx = (f - 1.5f) * 0.72f;
            PartDefinition p = wrist.addOrReplaceChild("finger" + f, CubeListBuilder.create(), PartPose.offset(fx, PALM, 0));
            float lengthen = 1.0f - Math.abs(f - 1.5f) * 0.1f;
            for (int j = 0; j < PHALANX.length; j++) {
                if (j > 0) {
                    p = p.addOrReplaceChild("p" + j, CubeListBuilder.create(), PartPose.offset(0, PHALANX[j - 1] * lengthen, 0));
                }
                rod(p, "bone", rng, j == PHALANX.length - 1 ? NAILS : BODY, 0, 0, 0, 0, PHALANX[j] * lengthen, 0, 0.75f - j * 0.08f);
            }
        }
        rod(wrist, "thumb_a", rng, BODY, -sign * 1.6f, 0.6f, -0.3f, -sign * 2.3f, 2.6f, -1.2f, 0.75f);
        rod(wrist, "thumb_b", rng, NAILS, -sign * 2.3f, 2.6f, -1.2f, -sign * 2.4f, 4.2f, -1.6f, 0.65f);
    }

    /**
     * The head, built around two real holes for the eyes. Above them a high cranium and an
     * overhanging brow; beside and below them the edges of the sockets, cheekbones and a narrow
     * nose bridge; behind them a dark box (the hollow), and inside it, far back, two small white
     * eyes. Below: teeth and a long jaw that can drop open.
     */
    private static void head(PartDefinition head, RandomSource rng) {
        box(head, "cranium", rng, BODY, -3.0f, -10.0f, -3.0f, 6.0f, 5.0f, 6.0f);
        lump(head, "occiput", rng, BODY, 0.0f, -7.6f, 2.6f, 5.4f, 5.2f, 3.2f);
        box(head, "brow", rng, BONE, -3.1f, -5.6f, -3.5f, 6.2f, 0.9f, 1.2f);
        box(head, "back", rng, BODY, -3.0f, -5.0f, -1.2f, 6.0f, 3.0f, 4.0f);
        box(head, "socket", rng, SOCKET, -2.6f, -5.0f, -1.9f, 5.2f, 1.7f, 0.8f);
        box(head, "eye_r", rng, EYE, -1.9f, -4.55f, -2.2f, 1.0f, 0.7f, 0.4f);
        box(head, "eye_l", rng, EYE, 0.9f, -4.55f, -2.2f, 1.0f, 0.7f, 0.4f);
        box(head, "nose", rng, BODY, -0.5f, -5.0f, -3.3f, 1.0f, 2.7f, 1.6f);
        box(head, "orbit_r", rng, BODY, -3.0f, -5.0f, -3.0f, 0.75f, 1.7f, 1.2f);
        box(head, "orbit_l", rng, BODY, 2.25f, -5.0f, -3.0f, 0.75f, 1.7f, 1.2f);
        box(head, "cheeks", rng, BONE, -3.0f, -3.3f, -2.9f, 6.0f, 1.1f, 1.8f);
        box(head, "upper_jaw", rng, BODY, -2.6f, -2.2f, -2.4f, 5.2f, 0.8f, 4.4f);
        box(head, "teeth_top", rng, TEETH, -2.1f, -1.5f, -2.5f, 4.2f, 0.6f, 0.4f);
        box(head, "mouth", rng, MOUTH, -2.0f, -1.5f, -2.0f, 4.0f, 1.8f, 2.6f);
        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create(), PartPose.offset(0, -1.6f, 1.2f));
        box(jaw, "bone", rng, BODY, -2.4f, 0.0f, -3.9f, 4.8f, 1.4f, 4.4f);
        lump(jaw, "chin", rng, BODY, 0.0f, 1.4f, -3.3f, 2.4f, 1.6f, 1.5f);
        box(jaw, "teeth", rng, TEETH, -2.0f, -0.5f, -3.85f, 4.0f, 0.6f, 0.4f);

        // The stolen face, slipped off and hanging from the right temple like a loose mask.
        PartDefinition flap = head.addOrReplaceChild("face_flap", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.0f, -6.6f, -1.6f, 0.0f, 0.0f, 0.25f));
        box(flap, "outer", rng, STOLEN, -0.3f, 0.0f, -2.4f, 0.3f, 6.2f, 4.6f);
        box(flap, "inner", rng, STOLEN_INNER, 0.0f, 0.2f, -2.2f, 0.15f, 5.8f, 4.2f);
        box(flap, "lip", rng, STOLEN, -0.3f, 6.2f, -1.6f, 0.3f, 1.4f, 2.0f);

        // A scalp of stolen skin sitting askew on top, with long hair hanging off its edges.
        PartDefinition scalp = head.addOrReplaceChild("scalp", CubeListBuilder.create(), PartPose.offsetAndRotation(0.4f, -10.0f, 0.4f, 0.12f, 0.2f, -0.16f));
        box(scalp, "skin", rng, STOLEN, -3.4f, -0.6f, -3.3f, 6.8f, 0.8f, 6.8f);
        for (int h = 0; h < 11; h++) {
            float a = h / 11.0f * Mth.TWO_PI + rng.nextFloat() * 0.3f;
            if (Mth.sin(a) < -0.6f) {
                continue; // nothing over the face
            }
            float hx = Mth.cos(a) * 3.2f;
            float hz = -Mth.sin(a) * 3.2f;
            float len = 4.0f + rng.nextFloat() * 6.0f;
            rod(scalp, "hair" + h, rng, HAIR, hx, 0.0f, hz, hx * 1.15f, len, hz * 1.15f + 0.6f, 0.45f + rng.nextFloat() * 0.3f);
        }
    }

    /**
     * The stolen skin worn over the body: a shawl over the back, the front hanging open like an
     * unbuttoned coat (two sagging lapels of skin, uneven and torn, with the yellow ribs showing
     * between them), a loose pouch of belly skin, and a skirt of skin over the back of the hips.
     * Each sheet has the outside of the skin on the outer face and the raw inside behind it.
     */
    private static void stolenSkin(PartDefinition shoulders, PartDefinition waist, RandomSource rng) {
        PartDefinition back = shoulders.addOrReplaceChild("skin_back", CubeListBuilder.create(), PartPose.offset(0, -0.4f, 2.5f));
        box(back, "outer", rng, STOLEN, -4.8f, 0.0f, 0.0f, 9.6f, 12.5f, 0.35f);
        box(back, "inner", rng, STOLEN_INNER, -4.6f, 0.2f, -0.15f, 9.2f, 12.0f, 0.15f);
        // Ragged lower edge.
        for (int t = 0; t < 4; t++) {
            float tx = -4.0f + t * 2.4f + rng.nextFloat() * 0.8f;
            box(back, "tatter" + t, rng, STOLEN, tx, 12.4f, 0.0f, 1.2f + rng.nextFloat(), 1.5f + rng.nextFloat() * 3.0f, 0.35f);
        }
        rod(back, "fold", rng, STOLEN, -4.0f, 5.0f, 0.5f, 4.2f, 5.6f, 0.5f, 0.7f);

        PartDefinition front = shoulders.addOrReplaceChild("skin_front", CubeListBuilder.create(), PartPose.offset(0, 0.6f, -2.5f));
        // Right lapel: longer, hangs past the hips, a sagging fold across the chest.
        box(front, "lapel_r", rng, STOLEN, -4.4f, 0.0f, -0.35f, 3.3f, 11.0f, 0.35f);
        box(front, "lapel_r_in", rng, STOLEN_INNER, -4.2f, 0.2f, 0.0f, 3.0f, 10.6f, 0.15f);
        rod(front, "fold_r", rng, STOLEN, -4.2f, 3.4f, -0.6f, -1.3f, 4.4f, -0.6f, 0.75f);
        box(front, "tatter_r", rng, STOLEN, -3.6f, 11.0f, -0.35f, 1.4f, 2.6f, 0.35f);
        // Left lapel: shorter, its corner curling outward.
        box(front, "lapel_l", rng, STOLEN, 1.1f, 0.0f, -0.35f, 3.3f, 9.2f, 0.35f);
        box(front, "lapel_l_in", rng, STOLEN_INNER, 1.3f, 0.2f, 0.0f, 3.0f, 8.8f, 0.15f);
        rod(front, "fold_l", rng, STOLEN, 1.3f, 4.0f, -0.6f, 4.2f, 3.3f, -0.6f, 0.75f);
        rod(front, "curl_l", rng, STOLEN, 4.2f, 9.2f, -0.2f, 4.6f, 10.4f, -1.0f, 0.6f);
        // The belly: a loose pouch of someone else's belly skin, hanging off the left lapel's edge,
        // with their navel still on it.
        PartDefinition belly = front.addOrReplaceChild("belly_flap", CubeListBuilder.create(), PartPose.offset(0.4f, 8.2f, -0.1f));
        box(belly, "outer", rng, STOLEN, -2.6f, 0.0f, -0.35f, 4.6f, 4.8f, 0.35f);
        box(belly, "inner", rng, STOLEN_INNER, -2.4f, 0.0f, 0.0f, 4.2f, 4.5f, 0.15f);
        rod(belly, "sag", rng, STOLEN, -2.5f, 3.6f, -0.6f, 1.9f, 3.9f, -0.6f, 0.8f);
        box(belly, "navel", rng, MOUTH, -0.6f, 2.0f, -0.45f, 0.8f, 0.7f, 0.1f);
        box(belly, "tatter", rng, STOLEN, -1.0f, 4.8f, -0.35f, 1.6f, 2.0f, 0.35f);

        PartDefinition skirt = waist.addOrReplaceChild("skin_skirt", CubeListBuilder.create(), PartPose.offset(0, -1.6f, 1.8f));
        box(skirt, "outer", rng, STOLEN, -3.6f, 0.0f, 0.0f, 7.2f, 7.5f, 0.35f);
        box(skirt, "inner", rng, STOLEN_INNER, -3.4f, 0.2f, -0.15f, 6.8f, 7.0f, 0.15f);
    }

    // ---- helpers ---------------------------------------------------------------------------

    private static void rod(PartDefinition parent, String name, RandomSource rng, int[] swatch,
                            float ax, float ay, float az, float bx, float by, float bz, float thick) {
        float length = Mth.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
        int[] uv = uv(rng, swatch, 4 * thick + 1, thick + length + 1);
        ClumpShapes.rod(parent, name, uv[0], uv[1], ax, ay, az, bx, by, bz, thick);
    }

    private static void box(PartDefinition parent, String name, RandomSource rng, int[] swatch,
                            float x, float y, float z, float w, float h, float d) {
        int[] uv = uv(rng, swatch, 2 * (w + d) + 1, d + h + 1);
        parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(uv[0], uv[1]).addBox(x, y, z, w, h, d), PartPose.ZERO);
    }

    private static void lump(PartDefinition parent, String name, RandomSource rng, int[] swatch,
                             float x, float y, float z, float w, float h, float d) {
        int[] uv = uv(rng, swatch, 2 * (w + d) + 6, d + h + 6);
        ClumpShapes.lump(parent, name, uv[0], uv[1], x, y, z, w, h, d, rng.nextFloat() * 0.4f, rng.nextFloat() * 0.4f, rng.nextFloat() * 0.4f);
    }

    private static int[] uv(RandomSource rng, int[] swatch, float needW, float needH) {
        int spareU = Math.max(0, swatch[2] - Mth.ceil(needW));
        int spareV = Math.max(0, swatch[3] - Mth.ceil(needH));
        return new int[] {swatch[0] + rng.nextInt(spareU + 1), swatch[1] + rng.nextInt(spareV + 1)};
    }

    // ================================================================== animation

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(SkinStealer entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float partial = ageInTicks - entity.tickCount;
        float stalk = entity.poseWeight(SkinStealer.Mode.STALK, partial);
        float frozen = entity.poseWeight(SkinStealer.Mode.FROZEN, partial);
        float charge = entity.poseWeight(SkinStealer.Mode.CHARGE, partial);
        float retreat = entity.poseWeight(SkinStealer.Mode.RETREAT, partial);
        float hungry = entity.isHungry() ? 1.0f : 0.0f;
        float amount = Math.min(1.0f, limbSwingAmount * 1.4f);
        float cycle = limbSwing * 0.42f;
        int seed = entity.getId() * 53;

        // --- Posture: a slight stoop when calm, a deep crouch when stalking, rigid when caught ----
        float lean = 0.18f + 0.1f * hungry + 0.45f * stalk + 0.5f * charge + 0.3f * retreat - 0.15f * frozen;
        float kneeBend = 0.15f + 0.5f * stalk + 0.3f * charge;
        float breath = Mth.sin(ageInTicks * 0.07f);
        waist.xRot = lean + 0.02f * breath;
        waist.zRot = ClumpShapes.drift(seed, ageInTicks, 70, 0.05f) * (1.0f - frozen);
        ribcage.xScale = 1.0f + 0.03f * breath;
        ribcage.zScale = 1.0f + 0.05f * breath;
        // Long, slow strides; the body sinks and rises with them.
        float stride = Mth.cos(cycle) * 0.65f * amount;
        hips.y += kneeBend * 3.0f - Math.abs(Mth.sin(cycle)) * 0.8f * amount;
        hips.zRot = Mth.sin(cycle) * 0.05f * amount;
        for (int s = 0; s < 2; s++) {
            float dir = s == 0 ? 1.0f : -1.0f;
            float legSwing = stride * dir;
            thigh[s].xRot = legSwing - kneeBend;
            shin[s].xRot = Math.max(0.0f, Mth.sin(cycle * dir + 0.6f * dir)) * 0.9f * amount + kneeBend * 1.8f;
            foot[s].xRot = -(thigh[s].xRot + shin[s].xRot) * 0.8f; // keeps the sole flat
            // The skin over the thigh lags behind the swing.
            thighFlap[s].xRot = -legSwing * 0.6f - thigh[s].xRot * 0.3f + 0.05f * Mth.sin(ageInTicks * 0.1f + s);
        }

        // --- Head -----------------------------------------------------------------------------
        float tilt = ClumpShapes.drift(seed + 3, ageInTicks, 90, 0.25f);
        neck.xRot = -lean * 0.6f;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.8f;
        head.xRot = headPitch * Mth.DEG_TO_RAD * 0.6f - lean * 0.4f + 0.1f * stalk;
        // Caught in your gaze: the head tilts far over to one side and holds there.
        head.zRot = tilt * (1.0f - frozen) + 0.55f * frozen;
        // The jaw hangs a little open; it works when it mimics a voice and gapes when it charges.
        float mimicAge = ageInTicks - entity.mimicStart();
        float talk = mimicAge >= 0.0f && mimicAge < 45.0f ? Mth.sin(mimicAge / 45.0f * Mth.PI) : 0.0f;
        jaw.xRot = 0.08f + 0.35f * charge + talk * (0.25f + 0.2f * Mth.abs(Mth.sin(mimicAge * 0.9f)));
        if (talk > 0.0f) {
            head.zRot += 0.2f * talk * Mth.sin(mimicAge * 0.3f);
        }

        // --- Arms: they hang straight down under gravity whatever the lean -----------------------
        float hang = -lean;
        for (int s = 0; s < 2; s++) {
            float dir = s == 0 ? 1.0f : -1.0f;
            float sway = -stride * dir * 0.35f;
            arm[s].xRot = hang + sway + 0.05f * Mth.sin(ageInTicks * 0.05f + s);
            arm[s].zRot = dir * (0.05f + 0.02f * breath);
            elbow[s].xRot = -0.12f - 0.25f * stalk;
            wrist[s].xRot = -0.1f;
            // Charging: arms up and forward, reaching.
            if (charge > 0.0f) {
                arm[s].xRot += charge * (-1.25f + 0.25f * Mth.sin(ageInTicks * 0.35f + s * 2.0f) - hang);
                elbow[s].xRot -= charge * 0.35f;
                arm[s].zRot += dir * 0.15f * charge;
            }
        }
        // The tearing attack: both arms rake down, one after the other.
        if (attackTime > 0.0f) {
            float rake = Mth.sin(attackTime * Mth.PI);
            float rake2 = Mth.sin(Math.min(1.0f, attackTime * 1.3f) * Mth.PI);
            arm[0].xRot += -1.8f * rake + 1.2f * attackTime;
            arm[1].xRot += -1.8f * rake2 + 1.0f * attackTime;
            elbow[0].xRot -= 0.5f * rake;
            elbow[1].xRot -= 0.5f * rake2;
        }
        // Fingers: loose and twitching, spread wide to grab when it charges.
        for (int s = 0; s < 2; s++) {
            for (int f = 0; f < 4; f++) {
                float curl = 0.22f + ClumpShapes.twitch(seed + 10 + s * 4 + f, ageInTicks, 11 + f, 0.18f) * (1.0f - frozen);
                curl = Mth.lerp(charge, curl, -0.12f);
                for (int j = 0; j < PHALANX.length; j++) {
                    finger[s][f][j].xRot = curl * (1.0f + j * 0.3f);
                }
                finger[s][f][0].zRot = (f - 1.5f) * 0.06f * (1.0f + charge * 2.0f);
            }
        }

        // --- The stolen skin: hangs plumb, a beat behind the body ------------------------------
        float lag = Mth.sin(cycle - 0.8f) * amount;
        float swing = 0.04f * Mth.sin(ageInTicks * 0.06f);
        skinFront.xRot = -lean + 0.06f + 0.12f * lag + swing;
        bellyFlap.xRot = 0.05f + 0.18f * Mth.sin(cycle - 1.4f) * amount - 0.03f * breath;
        skinBack.xRot = -lean * 0.85f + 0.12f + 0.15f * lag + swing;
        skinSkirt.xRot = 0.2f + 0.2f * Math.abs(lag) + swing;
        faceFlap.zRot += 0.15f * Mth.sin(ageInTicks * 0.08f) + 0.2f * lag - head.zRot * 0.6f;
        faceFlap.xRot = -head.xRot * 0.5f;
        scalp.zRot += 0.03f * Mth.sin(ageInTicks * 0.05f);
        for (int s = 0; s < 2; s++) {
            cuff[s].xRot = -arm[s].xRot * 0.5f + 0.15f * Mth.sin(cycle - 0.5f + s * Mth.PI) * amount;
        }
    }
}
