package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.Wretch;
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
 * The Wretch, built in code: a starved person, all ribs, spine knobs and knobbly joints, hunched
 * over and hugging itself, shaking. Patches of its skin are gone (it picked them off): raw red
 * plates sit on the arms, chest, back, thighs and face, with a few curled strips of skin still
 * peeling away at their edges. Its eyes are wide and bloodshot in deep sockets, its mouth hangs
 * open, its hair is falling out in clumps.
 *
 * <pre>
 *        ,(O O),      wide eyes in hollow sockets, open mouth, patchy hair
 *         \ ▄ /
 *       __/‾‾‾\__      knobbly shoulders, collarbones
 *      /  \≡≡≡/  \     ribs, with raw patches; arms crossed, hands clutching its own arms
 *     (  ==X==  )
 *        |   |         hunched forward, thin legs bent at the knee
 *        /   \
 * </pre>
 *
 * <p>Four poses blend smoothly into each other (weights eased on the entity): shuffling and
 * rocking (WANDER), kneeling at the water's edge lapping it up (DRINK), running with its arms
 * flailing over its head (FLEE), and crouched, swiping wildly (CORNERED). A constant tremor runs
 * through all of them.
 *
 * <p>Model space: units are pixels, +Y down, front is -Z, feet at y = 24. Texture 128x128 units
 * (PNG twice that): skin, raw flesh, skin over bone, hair, dark socket, bloodshot eye, teeth,
 * mouth, nails, and the rag of cloth round its hips.
 */
public class WretchModel extends HierarchicalModel<Wretch> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Backrooms.id("wretch"), "main");

    private static final int[] SKIN = {0, 0, 64, 64};
    private static final int[] RAW = {64, 0, 64, 64};
    private static final int[] BONE = {0, 64, 32, 32};
    private static final int[] HAIR = {32, 64, 32, 32};
    private static final int[] SOCKET = {64, 64, 16, 16};
    private static final int[] EYE = {80, 64, 16, 16};
    private static final int[] TEETH = {96, 64, 16, 16};
    private static final int[] MOUTH = {112, 64, 16, 16};
    private static final int[] CLOTH = {80, 80, 48, 48};
    private static final int[] NAILS = {64, 80, 16, 16};

    private static final float THIGH = 6.2f;
    private static final float SHIN = 6.0f;
    private static final float HIP_Y = 24.0f - THIGH - SHIN - 0.4f;
    private static final float TORSO = 9.0f;
    private static final float UPPER_ARM = 6.0f;
    private static final float FOREARM = 5.8f;
    private static final float PALM = 2.1f;

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
    private final ModelPart[][] finger = new ModelPart[2][4];
    private final ModelPart[][] fingerTip = new ModelPart[2][4];
    private final ModelPart[] peel = new ModelPart[4];

    public WretchModel(ModelPart root) {
        this.root = root;
        this.hips = root.getChild("hips");
        this.waist = hips.getChild("waist");
        this.ribcage = waist.getChild("ribcage");
        ModelPart shoulders = waist.getChild("shoulders");
        this.neck = shoulders.getChild("neck");
        this.head = neck.getChild("head");
        this.jaw = head.getChild("jaw");
        for (int s = 0; s < 2; s++) {
            String side = s == 0 ? "right" : "left";
            thigh[s] = hips.getChild("thigh_" + side);
            shin[s] = thigh[s].getChild("shin");
            foot[s] = shin[s].getChild("foot");
            arm[s] = shoulders.getChild("arm_" + side);
            elbow[s] = arm[s].getChild("elbow");
            wrist[s] = elbow[s].getChild("wrist");
            for (int f = 0; f < 4; f++) {
                finger[s][f] = wrist[s].getChild("finger" + f);
                fingerTip[s][f] = finger[s][f].getChild("tip");
            }
        }
        peel[0] = arm[0].getChild("peel");
        peel[1] = elbow[1].getChild("peel");
        peel[2] = ribcage.getChild("peel");
        peel[3] = thigh[1].getChild("peel");
    }

    // ================================================================== building

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        RandomSource rng = RandomSource.create(0x0E7C4EDL);

        PartDefinition hips = root.addOrReplaceChild("hips", CubeListBuilder.create(), PartPose.offset(0, HIP_Y, 0));
        box(hips, "pelvis", rng, SKIN, -2.2f, -1.2f, -1.2f, 4.4f, 2.4f, 2.4f);
        lump(hips, "crest_r", rng, BONE, -1.9f, -1.0f, -0.6f, 1.2f, 1.3f, 1.3f);
        lump(hips, "crest_l", rng, BONE, 1.9f, -1.0f, -0.6f, 1.2f, 1.3f, 1.3f);
        // A rag of cloth still tied round the hips: all that is left of its clothes.
        box(hips, "rag", rng, CLOTH, -2.4f, -0.6f, -1.35f, 4.8f, 2.2f, 2.7f);
        for (int s = 0; s < 2; s++) {
            leg(hips, s, rng);
        }
        PartDefinition waist = hips.addOrReplaceChild("waist", CubeListBuilder.create(), PartPose.ZERO);
        torso(waist, rng);
        PartDefinition shoulders = waist.addOrReplaceChild("shoulders", CubeListBuilder.create(), PartPose.offset(0, -TORSO, 0));
        rod(shoulders, "collar_r", rng, BONE, -0.3f, 0.3f, -1.4f, -2.9f, 0.0f, -0.7f, 0.5f);
        rod(shoulders, "collar_l", rng, BONE, 0.3f, 0.3f, -1.4f, 2.9f, 0.0f, -0.7f, 0.5f);
        for (int s = 0; s < 2; s++) {
            arm(shoulders, s, rng);
        }
        PartDefinition neck = shoulders.addOrReplaceChild("neck", CubeListBuilder.create(), PartPose.offset(0, -0.2f, -0.4f));
        rod(neck, "neck", rng, SKIN, 0, 0, 0, 0, -2.2f, -0.8f, 1.2f);
        rod(neck, "tendon_r", rng, BONE, -0.5f, 0.2f, -0.4f, -0.3f, -2.0f, -1.2f, 0.4f);
        rod(neck, "tendon_l", rng, BONE, 0.5f, 0.2f, -0.4f, 0.3f, -2.0f, -1.2f, 0.4f);
        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0, -2.2f, -0.8f));
        head(head, rng);
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static void leg(PartDefinition hips, int s, RandomSource rng) {
        float sign = s == 0 ? -1.0f : 1.0f;
        String side = s == 0 ? "right" : "left";
        PartDefinition thigh = hips.addOrReplaceChild("thigh_" + side, CubeListBuilder.create(), PartPose.offset(sign * 1.3f, 0.6f, 0));
        rod(thigh, "bone", rng, SKIN, 0, 0, 0, 0, THIGH, 0.1f, 1.5f);
        PartDefinition shin = thigh.addOrReplaceChild("shin", CubeListBuilder.create(), PartPose.offset(0, THIGH, 0.1f));
        lump(shin, "knee", rng, BONE, 0.0f, 0.0f, -0.3f, 1.8f, 1.8f, 1.7f);
        rod(shin, "bone", rng, SKIN, 0, 0, 0, 0, SHIN, 0.0f, 1.25f);
        rod(shin, "shinbone", rng, BONE, 0, 0.8f, -0.6f, 0, SHIN - 1.0f, -0.5f, 0.5f);
        PartDefinition foot = shin.addOrReplaceChild("foot", CubeListBuilder.create(), PartPose.offset(0, SHIN, 0));
        lump(foot, "ankle", rng, BONE, 0.0f, 0.0f, 0.2f, 1.3f, 1.1f, 1.3f);
        box(foot, "sole", rng, SKIN, -0.8f, 0.0f, -3.4f, 1.6f, 0.6f, 3.8f);
        for (int t = 0; t < 4; t++) {
            float tx = (t - 1.5f) * 0.4f;
            rod(foot, "toe" + t, rng, NAILS, tx, 0.3f, -3.3f, tx * 1.2f, 0.5f, -4.2f, 0.35f);
        }
        if (s == 0) {
            patch(thigh, "raw_a", rng, -0.9f, 1.5f, -0.8f, 1.6f, 2.6f, 0.0f, 0.0f);
            patch(shin, "raw_b", rng, sign * 0.7f, 2.0f, -0.3f, 1.2f, 1.8f, 0.0f, Mth.HALF_PI);
        } else {
            patch(thigh, "raw_a", rng, -0.5f, 3.2f, -0.8f, 1.2f, 1.6f, 0.0f, 0.0f);
            PartDefinition peel = thigh.addOrReplaceChild("peel", CubeListBuilder.create(), PartPose.offset(0.3f, 3.0f, -0.85f));
            box(peel, "strip", rng, SKIN, -0.4f, -1.4f, -0.12f, 0.8f, 1.4f, 0.12f);
        }
    }

    /**
     * Pinched belly, ribcage with every rib showing (its own part, so it heaves with the panicked
     * breathing), spine knobs and shoulder blades jutting out of the back, raw patches front and back.
     */
    private static void torso(PartDefinition waist, RandomSource rng) {
        box(waist, "belly", rng, SKIN, -1.6f, -4.2f, -0.8f, 3.2f, 4.2f, 1.8f);
        PartDefinition ribs = waist.addOrReplaceChild("ribcage", CubeListBuilder.create(), PartPose.offset(0, -6.6f, 0));
        box(ribs, "chest", rng, SKIN, -2.3f, -2.6f, -1.4f, 4.6f, 5.4f, 2.8f);
        for (int r = 0; r < 5; r++) {
            float y = -2.0f + r * 1.05f;
            float half = 2.45f - Math.abs(r - 1.0f) * 0.12f;
            rod(ribs, "rib_r" + r, rng, BONE, -half, y, -0.3f, -0.3f, y + 0.6f, -1.6f, 0.4f);
            rod(ribs, "rib_l" + r, rng, BONE, half, y, -0.3f, 0.3f, y + 0.6f, -1.6f, 0.4f);
        }
        rod(ribs, "sternum", rng, BONE, 0, -2.4f, -1.55f, 0, 2.6f, -1.6f, 0.5f);
        lump(ribs, "blade_r", rng, BONE, -1.3f, -1.6f, 1.45f, 1.7f, 2.4f, 0.7f);
        lump(ribs, "blade_l", rng, BONE, 1.3f, -1.6f, 1.45f, 1.7f, 2.4f, 0.7f);
        for (int k = 0; k < 7; k++) {
            lump(waist, "spine" + k, rng, BONE, 0.0f, -1.0f - k * 1.3f, 0.95f + (k > 2 ? 0.5f : 0.0f), 0.8f, 0.8f, 0.8f);
        }
        patch(ribs, "raw_chest", rng, 0.8f, -1.5f, -1.48f, 1.6f, 2.0f, 0.0f, 0.0f);
        patch(ribs, "raw_back", rng, -1.2f, 0.4f, 1.42f, 2.0f, 2.4f, 0.0f, Mth.PI);
        patch(waist, "raw_side", rng, -1.62f, -3.0f, 0.0f, 1.4f, 2.0f, 0.0f, -Mth.HALF_PI);
        PartDefinition peel = ribs.addOrReplaceChild("peel", CubeListBuilder.create(), PartPose.offset(1.6f, -1.6f, -1.5f));
        box(peel, "strip", rng, SKIN, -0.5f, -1.6f, -0.12f, 1.0f, 1.6f, 0.12f);
    }

    /** A thin arm with knobbly joints and a small bony hand (palm, four two-part fingers, thumb). */
    private static void arm(PartDefinition shoulders, int s, RandomSource rng) {
        float sign = s == 0 ? -1.0f : 1.0f;
        String side = s == 0 ? "right" : "left";
        PartDefinition arm = shoulders.addOrReplaceChild("arm_" + side, CubeListBuilder.create(), PartPose.offset(sign * 2.8f, 0.3f, -0.1f));
        lump(arm, "shoulder", rng, BONE, 0.0f, 0.2f, 0.0f, 1.6f, 1.5f, 1.6f);
        rod(arm, "bone", rng, SKIN, 0, 0, 0, 0, UPPER_ARM, 0.0f, 1.2f);
        PartDefinition elbow = arm.addOrReplaceChild("elbow", CubeListBuilder.create(), PartPose.offset(0, UPPER_ARM, 0));
        lump(elbow, "knob", rng, BONE, 0.0f, 0.0f, 0.3f, 1.4f, 1.4f, 1.4f);
        rod(elbow, "bone", rng, SKIN, 0, 0, 0, 0, FOREARM, 0.0f, 1.05f);
        PartDefinition wrist = elbow.addOrReplaceChild("wrist", CubeListBuilder.create(), PartPose.offset(0, FOREARM, 0));
        box(wrist, "palm", rng, SKIN, -0.9f, 0.0f, -0.35f, 1.8f, PALM, 0.7f);
        for (int f = 0; f < 4; f++) {
            float fx = (f - 1.5f) * 0.45f;
            PartDefinition finger = wrist.addOrReplaceChild("finger" + f, CubeListBuilder.create(), PartPose.offset(fx, PALM, 0));
            rod(finger, "bone", rng, SKIN, 0, 0, 0, 0, 1.3f, 0, 0.42f);
            PartDefinition tip = finger.addOrReplaceChild("tip", CubeListBuilder.create(), PartPose.offset(0, 1.3f, 0));
            rod(tip, "bone", rng, NAILS, 0, 0, 0, 0, 1.1f, 0, 0.38f);
        }
        rod(wrist, "thumb", rng, SKIN, -sign * 0.9f, 0.3f, -0.2f, -sign * 1.3f, 1.6f, -0.7f, 0.42f);
        if (s == 0) {
            // Right arm: a big raw patch on the upper arm, a skin strip curling off its edge.
            patch(arm, "raw", rng, sign * 0.62f, 1.2f, 0.0f, 1.3f, 3.6f, 0.0f, sign * Mth.HALF_PI);
            PartDefinition peel = arm.addOrReplaceChild("peel", CubeListBuilder.create(), PartPose.offset(sign * 0.65f, 1.2f, -0.3f));
            box(peel, "strip", rng, SKIN, -0.06f, -1.5f, -0.4f, 0.12f, 1.5f, 0.8f);
            patch(elbow, "raw_fore", rng, 0.0f, 2.0f, -0.55f, 0.9f, 1.5f, 0.0f, 0.0f);
        } else {
            patch(elbow, "raw", rng, 0.0f, 1.0f, -0.55f, 1.0f, 3.0f, 0.0f, 0.0f);
            PartDefinition peel = elbow.addOrReplaceChild("peel", CubeListBuilder.create(), PartPose.offset(0.2f, 4.0f, -0.55f));
            box(peel, "strip", rng, SKIN, -0.4f, 0.0f, -0.12f, 0.8f, 1.6f, 0.12f);
        }
    }

    /**
     * The head, built around two real holes for the eyes (like the Skin-stealer's) but with big,
     * wide-open bloodshot eyes near the front of the sockets: panic, not menace. A slack open
     * mouth, hollow cheeks, a raw patch on one cheek and on the scalp, thin hair in clumps.
     */
    private static void head(PartDefinition head, RandomSource rng) {
        box(head, "cranium", rng, SKIN, -2.4f, -6.2f, -2.4f, 4.8f, 3.0f, 4.8f);
        lump(head, "occiput", rng, SKIN, 0.0f, -4.6f, 1.9f, 4.2f, 3.6f, 2.0f);
        box(head, "brow", rng, BONE, -2.45f, -3.4f, -2.6f, 4.9f, 0.6f, 0.8f);
        box(head, "back", rng, SKIN, -2.4f, -3.2f, -1.0f, 4.8f, 2.2f, 3.4f);
        box(head, "socket", rng, SOCKET, -2.1f, -3.2f, -1.6f, 4.2f, 1.4f, 0.6f);
        box(head, "eye_r", rng, EYE, -1.75f, -3.05f, -2.05f, 1.15f, 1.0f, 0.5f);
        box(head, "eye_l", rng, EYE, 0.6f, -3.05f, -2.05f, 1.15f, 1.0f, 0.5f);
        // Small dark pupils staring out of the whites (wide open: it is terrified).
        box(head, "pupil_r", rng, SOCKET, -1.42f, -2.78f, -2.12f, 0.45f, 0.45f, 0.1f);
        box(head, "pupil_l", rng, SOCKET, 0.95f, -2.78f, -2.12f, 0.45f, 0.45f, 0.1f);
        box(head, "nose", rng, SKIN, -0.4f, -3.2f, -2.75f, 0.8f, 1.9f, 1.1f);
        box(head, "orbit_r", rng, SKIN, -2.4f, -3.2f, -2.4f, 0.5f, 1.4f, 0.9f);
        box(head, "orbit_l", rng, SKIN, 1.9f, -3.2f, -2.4f, 0.5f, 1.4f, 0.9f);
        box(head, "cheeks", rng, BONE, -2.4f, -1.8f, -2.3f, 4.8f, 0.7f, 1.4f);
        box(head, "upper_jaw", rng, SKIN, -2.0f, -1.1f, -2.0f, 4.0f, 0.5f, 3.6f);
        box(head, "teeth_top", rng, TEETH, -1.5f, -0.65f, -2.05f, 3.0f, 0.45f, 0.3f);
        box(head, "mouth", rng, MOUTH, -1.5f, -0.6f, -1.7f, 3.0f, 1.4f, 2.4f);
        patch(head, "raw_cheek", rng, -1.9f, -2.2f, -2.45f, 1.0f, 1.3f, 0.0f, 0.0f);
        patch(head, "raw_scalp", rng, 0.9f, -6.22f, 0.4f, 1.8f, 1.6f, -Mth.HALF_PI, 0.0f);
        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create(), PartPose.offset(0, -0.8f, 1.0f));
        box(jaw, "bone", rng, SKIN, -1.9f, 0.0f, -3.0f, 3.8f, 1.0f, 3.4f);
        lump(jaw, "chin", rng, SKIN, 0.0f, 0.8f, -2.5f, 1.8f, 1.0f, 1.0f);
        box(jaw, "teeth", rng, TEETH, -1.4f, -0.4f, -2.95f, 2.8f, 0.45f, 0.3f);
        // Patchy hair: clumps on one side and at the back only; the rest pulled out.
        for (int h = 0; h < 9; h++) {
            float a = 0.4f + h / 9.0f * 3.6f + rng.nextFloat() * 0.2f;
            float hx = Mth.cos(a) * 2.3f;
            float hz = Mth.sin(a) * 2.3f;
            float len = 2.0f + rng.nextFloat() * 4.0f;
            rod(head, "hair" + h, rng, HAIR, hx, -6.0f, hz, hx * 1.25f, -6.0f + len, hz * 1.2f + 0.4f, 0.35f + rng.nextFloat() * 0.25f);
        }
    }

    // ---- helpers ---------------------------------------------------------------------------

    /**
     * A raw patch: a thin plate of raw flesh (w x h), laid on a surface, turned by xRot/yRot so it
     * faces the right way (yRot = 0 faces front, PI/2 faces the side...). Pivot at its centre.
     */
    private static void patch(PartDefinition parent, String name, RandomSource rng,
                              float x, float y, float z, float w, float h, float xRot, float yRot) {
        int[] uv = uv(rng, RAW, 2 * (w + 0.15f) + 1, h + 1.2f);
        parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(uv[0], uv[1]).addBox(-w / 2.0f, -h / 2.0f, -0.08f, w, h, 0.15f),
                PartPose.offsetAndRotation(x, y, z, xRot, yRot, 0.0f));
    }

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
    public void setupAnim(Wretch entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float partial = ageInTicks - entity.tickCount;
        float drink = entity.poseWeight(Wretch.Mode.DRINK, partial);
        float flee = entity.poseWeight(Wretch.Mode.FLEE, partial);
        float corner = entity.poseWeight(Wretch.Mode.CORNERED, partial);
        float calm = Math.max(0.0f, 1.0f - drink - flee - corner);
        int seed = entity.getId() * 29;
        float amount = Math.min(1.0f, limbSwingAmount * 1.5f);
        float cycle = limbSwing * (0.6f + 0.25f * flee);

        // The tremor: never stops, worse when frightened.
        float fear = 0.3f + 0.7f * Math.min(1.0f, flee + corner);
        float shake = Mth.sin(ageInTicks * 2.3f) * 0.02f * fear + ClumpShapes.twitch(seed, ageInTicks, 2, 0.015f) * fear;

        // --- Body -------------------------------------------------------------------------------
        float rock = Mth.sin(ageInTicks * 0.11f) * 0.07f;
        waist.xRot = calm * (0.55f + rock) + drink * 1.15f + flee * 0.3f + corner * 0.7f + shake;
        waist.zRot = shake * 0.8f + calm * ClumpShapes.drift(seed + 1, ageInTicks, 60, 0.06f);
        // Panicked breathing: fast and shallow when afraid, slow when calm.
        float breath = Mth.sin(ageInTicks * (0.12f + 0.45f * fear));
        ribcage.xScale = 1.0f + 0.04f * breath;
        ribcage.zScale = 1.0f + 0.07f * breath;
        hips.y += drink * 5.2f + corner * 1.6f;

        // --- Legs ------------------------------------------------------------------------------
        float strideSize = 0.45f + 0.5f * flee;
        for (int s = 0; s < 2; s++) {
            float dir = s == 0 ? 1.0f : -1.0f;
            // It limps: the left leg takes shorter steps.
            float stride = Mth.cos(cycle) * strideSize * amount * dir * (s == 1 ? 0.7f : 1.0f);
            float knee = 0.2f * calm + 0.45f * corner + 0.1f * flee;
            thigh[s].xRot = (stride - knee) * (1.0f - drink) + drink * -1.35f;
            shin[s].xRot = (Math.max(0.0f, Mth.sin(cycle * dir)) * 0.8f * amount + knee * 1.8f) * (1.0f - drink) + drink * 2.75f;
            foot[s].xRot = -(thigh[s].xRot + shin[s].xRot) * 0.7f;
            thigh[s].zRot = dir * 0.04f * corner;
        }

        // --- Head: looks about nervously; jaw slack, wide open when it screams or drinks --------
        float glance = ClumpShapes.twitch(seed + 2, ageInTicks, 13, 0.5f) * calm;
        neck.xRot = -waist.xRot * 0.65f;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD * (1.0f - drink) + glance + corner * 0.25f * Mth.sin(ageInTicks * 1.9f)
                + Mth.sin(ageInTicks * 3.1f) * 0.03f * fear;
        head.xRot = (headPitch * Mth.DEG_TO_RAD * 0.7f - waist.xRot * 0.45f) * (1.0f - drink) + drink * 0.55f
                - flee * 0.3f + Mth.sin(ageInTicks * 0.4f) * 0.12f * drink;
        head.zRot = shake * 2.0f + calm * ClumpShapes.drift(seed + 3, ageInTicks, 50, 0.2f);
        // The head is built at a comfortable size for its face details; drawn at 80% so it sits
        // in proportion on the starved body.
        head.xScale = 0.8f;
        head.yScale = 0.8f;
        head.zScale = 0.8f;
        float sob = Mth.abs(Mth.sin(ageInTicks * 0.5f));
        jaw.xRot = 0.18f * calm + 0.55f * flee + corner * (0.3f + 0.25f * sob) + drink * (0.2f + 0.2f * Mth.abs(Mth.sin(ageInTicks * 0.4f)));

        // --- Arms ------------------------------------------------------------------------------
        for (int s = 0; s < 2; s++) {
            float dir = s == 0 ? 1.0f : -1.0f;
            // Hugging itself: upper arms a little forward, forearms folded across the chest.
            float hugArm = -0.25f - waist.xRot;
            float hugElbowX = s == 0 ? -1.85f : -2.0f;
            float hugElbowY = s == 0 ? -0.95f : 0.95f;
            // Drinking: arms reach down to the water, cupped.
            float drinkArm = -0.35f - waist.xRot * 0.2f;
            // Fleeing: flailing over the head.
            float flail = Mth.sin(ageInTicks * 0.95f + s * Mth.PI);
            float fleeArm = -2.5f + 0.6f * flail;
            // Cornered: guarding in front, ready to swipe.
            float guardArm = -1.3f - 0.2f * Mth.sin(ageInTicks * 0.7f + s);

            arm[s].xRot = calm * hugArm + drink * drinkArm + flee * fleeArm + corner * guardArm + shake;
            arm[s].zRot = dir * (calm * 0.12f + flee * (0.35f + 0.2f * flail) + corner * 0.2f);
            elbow[s].xRot = calm * hugElbowX + drink * -0.15f + flee * (-0.5f + 0.4f * flail) + corner * -0.7f;
            elbow[s].yRot = calm * hugElbowY;
            wrist[s].xRot = calm * 0.3f + drink * 0.5f;
            // Clutching (hug), cupping (drink), clawing (cornered), splayed (flee).
            float grip = calm * (0.9f + ClumpShapes.twitch(seed + 6 + s, ageInTicks, 6, 0.15f))
                    + drink * 0.7f + corner * 0.5f - flee * 0.2f;
            for (int f = 0; f < 4; f++) {
                float flutter = Mth.sin(ageInTicks * 1.7f + f * 1.3f + s) * 0.08f * fear;
                finger[s][f].xRot = grip + flutter;
                fingerTip[s][f].xRot = grip * 0.9f + flutter;
            }
        }
        // Swiping when cornered: the vanilla attack timer drives one wild rake.
        if (attackTime > 0.0f) {
            float rake = Mth.sin(attackTime * Mth.PI);
            int s = entity.getId() % 2;
            arm[s].xRot += -1.0f * rake;
            arm[s].zRot += (s == 0 ? -0.6f : 0.6f) * rake;
            elbow[s].xRot += 0.6f * rake;
        }

        // Strips of skin peeling off the raw patches flutter as it moves.
        for (int i = 0; i < peel.length; i++) {
            peel[i].xRot = 0.5f + 0.25f * Mth.sin(ageInTicks * 0.3f + i * 1.7f) * (0.3f + amount);
        }
    }
}
