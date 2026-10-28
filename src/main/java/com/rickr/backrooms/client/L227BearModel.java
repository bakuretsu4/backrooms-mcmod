package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.L227Bear;
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
 * The Level 227 bear, built in code: a long, heavy, shaggy body on six legs, and fifteen eyes.
 *
 * <pre>
 *                 hump (moss, ferns)
 *          ▄▄▄▄▄▄▄██████▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄
 *   ◉◉ ◉  ▐ neck ▌ chest ▐    mid    ▐   hips  ▌
 *  ◉◉◉◉◉◉ ▐      ▌       ▐           ▐         ▌
 *   snout  ▀▀▀▀▀▀ ║  ║ ▀▀▀▀ ║  ║ ▀▀▀▀▀ ║  ║ ▀▀▀    six legs: front, middle, rear
 *                 ▀▀ ▀▀     ▀▀ ▀▀      ▀▀ ▀▀
 * </pre>
 *
 * <p>The body is three pieces in a chain (hips, middle, chest), so it can curl up asleep and rear
 * up on its back legs to maul. The hips are the root and pivot on the rear hip joints, which is
 * where it rears from. Each leg is a thick upper and lower leg and a broad paw with four long
 * black claws; they walk in a tripod gait (front-left, middle-right and rear-left together, then
 * the other three), which is how insects walk and looks deeply wrong on a bear.
 *
 * <p>The eyes: a cluster of fifteen small glossy black eyes over the face, the forehead, the
 * temples and the top of the snout, in three sizes and slightly uneven. Each has a furred lid
 * that rolls up when the eye opens, one eye after another in a fixed shuffled order: asleep all
 * are shut, stirring five peek open and look around, awake all fifteen stare. A faint eyeshine
 * is drawn by {@link L227BearRenderer}'s glow layer (closed lids cover it).
 *
 * <p>Shag: about seventy hanging fur strands down the flanks, belly and neck; moss pads, little
 * ferns and bracket fungi grow on its back and shoulder (it has slept here a long time).
 *
 * <p>Model space: pixels, +Y down, the front faces -Z, the ground at y = 24. The texture is
 * 512 x 512 for a 256 x 256 UV layout (2 texels per pixel), made by
 * {@code tools/anomaly_assets/level227_grazers.py}, which follows the UV notes at each box.
 */
public class L227BearModel extends HierarchicalModel<L227Bear> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Backrooms.id("l227_bear"), "main");

    private static final float GROUND = 24.0f;
    /** The rear hip joint (the root's pivot) when standing: 15 px above the ground. */
    private static final float HIP_Y = 9.0f;
    private static final float HIP_Z = 16.0f;
    /** Lying down, the belly rests on the floor: the hips drop this far. */
    private static final float LIE_DROP = 11.5f;
    private static final float UPPER = 7.5f;
    private static final float LOWER = 6.0f;
    /** Every leg's joint is this far above the ground when standing (= UPPER + LOWER + paw). */
    private static final float LEG_HEIGHT = 15.0f;
    /** How many eyes. Canon. */
    public static final int EYE_COUNT = 15;

    /**
     * The eyes: x, y, z on the head, size (0 large, 1 medium, 2 small) and which way it faces
     * (0 forward, 1 up, 2 left side, 3 right side). Mirrored pairs, then a lone one in the middle.
     */
    private static final float[][] EYES = {
            // The main pair, just above the snout.
            {-3.0f, -1.9f, -11.0f, 0, 0}, {3.1f, -1.8f, -11.0f, 0, 0},
            // On the cheeks either side of the snout.
            {-5.0f, 2.3f, -11.5f, 1, 0}, {4.9f, 2.1f, -11.5f, 1, 0},
            // Inner brow.
            {-1.4f, -3.9f, -11.0f, 1, 0}, {1.5f, -3.7f, -11.0f, 1, 0},
            // Outer brow, small.
            {-5.0f, -4.2f, -11.0f, 2, 0}, {4.8f, -4.4f, -11.0f, 2, 0},
            // The one in the middle of the forehead.
            {0.1f, -5.3f, -11.0f, 1, 0},
            // Two on top of the snout, looking up.
            {-1.7f, -0.5f, -13.4f, 2, 1}, {1.8f, -0.5f, -12.9f, 2, 1},
            // Temples: one on each side of the head.
            {-7.0f, -1.6f, -6.6f, 1, 3}, {7.0f, -1.8f, -6.4f, 1, 2},
            // Crown: two on top of the skull, looking up.
            {-2.0f, -7.2f, -7.6f, 2, 1}, {1.9f, -7.2f, -8.3f, 2, 1},
    };
    /** Eye sizes (px) by size class, and the order they open in (a fixed shuffle). */
    private static final float[] EYE_SIZE = {2.2f, 1.6f, 1.2f};
    private static final int[] OPEN_ORDER = {0, 1, 8, 4, 3, 13, 6, 11, 2, 9, 5, 14, 7, 12, 10};

    private final ModelPart root;
    private final ModelPart hips;
    private final ModelPart mid;
    private final ModelPart chest;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart[] ears = new ModelPart[2];
    private final ModelPart[] upper = new ModelPart[6];
    private final ModelPart[] lower = new ModelPart[6];
    private final ModelPart[] paw = new ModelPart[6];
    private final ModelPart[] eye = new ModelPart[EYE_COUNT];
    private final ModelPart[] lid = new ModelPart[EYE_COUNT];

    public L227BearModel(ModelPart root) {
        this.root = root;
        this.hips = root.getChild("hips");
        this.mid = hips.getChild("mid");
        this.chest = mid.getChild("chest");
        this.neck = chest.getChild("neck");
        this.head = neck.getChild("head");
        this.jaw = head.getChild("jaw");
        this.ears[0] = head.getChild("ear_r");
        this.ears[1] = head.getChild("ear_l");
        ModelPart[] owners = {chest, chest, mid, mid, hips, hips};
        for (int i = 0; i < 6; i++) {
            upper[i] = owners[i].getChild(legName(i));
            lower[i] = upper[i].getChild("lower");
            paw[i] = lower[i].getChild("paw");
        }
        for (int i = 0; i < EYE_COUNT; i++) {
            eye[i] = head.getChild("eye" + i);
            lid[i] = eye[i].getChild("lid");
        }
    }

    /** Leg order: 0 front right, 1 front left, 2 middle right, 3 middle left, 4 rear right, 5 rear left. */
    private static String legName(int i) {
        return new String[] {"front", "middle", "rear"}[i / 2] + (i % 2 == 0 ? "_r" : "_l");
    }

    // ================================================================== building

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rootDef = mesh.getRoot();
        RandomSource rng = RandomSource.create(0x227BEA2L);

        // Hips: the root, pivoting on the rear hip joints, a little lower than the shoulders.
        // UV (0,0): 18 x 14.5 x 16. Rounded top UV (0,128): 14 x 1.5 x 12; rump UV (166,128): 14 x 11 x 1.5.
        PartDefinition hips = rootDef.addOrReplaceChild("hips", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-9.0f, -11.0f, -8.0f, 18, 14.5f, 16)
                        .texOffs(0, 128).addBox(-7.0f, -12.5f, -7.0f, 14, 1.5f, 12)
                        .texOffs(166, 128).addBox(-7.0f, -9.5f, 8.0f, 14, 11, 1.5f),
                PartPose.offset(0.0f, HIP_Y, HIP_Z));
        // Middle: the long belly between the middle and rear legs. UV (70,0): 19 x 16.5 x 14.
        // Rounded top UV (54,128): 15 x 1.5 x 12.
        PartDefinition mid = hips.addOrReplaceChild("mid", CubeListBuilder.create()
                        .texOffs(70, 0).addBox(-9.5f, -8.5f, -14.0f, 19, 16.5f, 14)
                        .texOffs(54, 128).addBox(-7.5f, -10.0f, -13.0f, 15, 1.5f, 12),
                PartPose.offset(0.0f, -4.5f, -8.0f));
        // Chest: deep, under a massive shoulder hump. UV (140,0): 20 x 18.5 x 12; hump UV (206,0):
        // 15 x 5 x 9; the slope from hump to flanks UV (110,128): 16 x 1.5 x 11.5.
        PartDefinition chest = mid.addOrReplaceChild("chest", CubeListBuilder.create()
                        .texOffs(140, 0).addBox(-10.0f, -9.0f, -12.0f, 20, 18.5f, 12)
                        .texOffs(206, 0).addBox(-7.5f, -14.0f, -11.0f, 15, 5, 9)
                        .texOffs(110, 128).addBox(-8.0f, -10.5f, -12.0f, 16, 1.5f, 11.5f),
                PartPose.offset(0.0f, -0.5f, -14.0f));
        // Neck: thick, sloping down to the head. UV (0,34): 12 x 11 x 8.
        PartDefinition neck = chest.addOrReplaceChild("neck", CubeListBuilder.create()
                        .texOffs(0, 34).addBox(-6.0f, -6.0f, -8.0f, 12, 11, 8),
                PartPose.offsetAndRotation(0.0f, -3.0f, -11.0f, 0.35f, 0.0f, 0.0f));
        head(neck, rng);

        // Legs: front pair on the chest, middle pair on the mid piece, rear pair on the hips.
        for (int side = -1; side <= 1; side += 2) {
            String s = side < 0 ? "_r" : "_l";
            leg(chest, "front" + s, 0, side * 9.0f, 5.0f, -6.5f, rng);
            leg(mid, "middle" + s, 1, side * 8.5f, 4.5f, -7.0f, rng);
            leg(hips, "rear" + s, 2, side * 8.0f, 0.0f, 0.0f, rng);
        }

        // The shag: strands hanging from the flanks and belly of each body piece and the neck.
        shag(hips, "shag", rng, 9.0f, -11.0f, 3.5f, -8.0f, 8.0f, 14);
        shag(mid, "shag", rng, 9.5f, -8.0f, 8.0f, -14.0f, 0.0f, 14);
        shag(chest, "shag", rng, 10.0f, -9.0f, 9.5f, -12.0f, 0.0f, 14);
        shag(neck, "shag", rng, 6.0f, -6.0f, 5.0f, -8.0f, 0.0f, 8);
        // Bristly fur standing up along the hump and back.
        for (int i = 0; i < 12; i++) {
            float x = (rng.nextFloat() - 0.5f) * 12.0f;
            float z = -10.5f + rng.nextFloat() * 8.5f;
            strand(chest, "bristle" + i, rng, x, -13.8f, z, x * 1.15f, -16.5f - rng.nextFloat() * 1.5f, z + 2.5f, 1.2f);
        }
        overgrowth(hips, mid, chest, rng);
        return LayerDefinition.create(mesh, 256, 256);
    }

    /** Skull, snout, jaw, ears, teeth and the fifteen eyes. */
    private static void head(PartDefinition neck, RandomSource rng) {
        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create()
                        // Skull: broad, a lot of face for fifteen eyes. UV (42,34): 14 x 11.5 x 11.
                        .texOffs(42, 34).addBox(-7.0f, -6.0f, -11.0f, 14, 11.5f, 11)
                        // Snout. UV (94,34): 7.5 x 5.5 x 8.
                        .texOffs(94, 34).addBox(-3.75f, -0.5f, -18.0f, 7.5f, 5.5f, 8)
                        // Nose pad. UV (126,34): 4 x 2.5 x 1.
                        .texOffs(126, 34).addBox(-2.0f, -0.6f, -18.6f, 4, 2.5f, 1)
                        // Inside of the mouth, seen when it roars. UV (186,34): 5.6 x 2 x 6.
                        .texOffs(186, 34).addBox(-2.8f, 3.0f, -17.0f, 5.6f, 2, 6)
                        // Heavy cheeks either side of the snout. UV (154,34): 12 x 4 x 3.
                        .texOffs(154, 34).addBox(-6.0f, 1.0f, -11.5f, 12, 4, 3)
                        // A rounded crown on the skull. UV (0,142): 12 x 1.2 x 9.
                        .texOffs(0, 142).addBox(-6.0f, -7.2f, -10.0f, 12, 1.2f, 9)
                        // Heavy jowls hanging under the back of the skull. UV (44,142): 13 x 2 x 6.
                        .texOffs(44, 142).addBox(-6.5f, 5.5f, -9.0f, 13, 2, 6),
                PartPose.offsetAndRotation(0.0f, 2.0f, -7.0f, -0.3f, 0.0f, 0.0f));

        // Upper fangs along the front of the snout.
        for (int k = 0; k < 4; k++) {
            float x = -2.4f + k * 1.6f;
            float length = (k == 0 || k == 3) ? 2.2f : 1.0f;
            segment(head, "fang_u" + k, 200, 44, x, 4.6f, -17.3f, x * 0.95f, 4.6f + length, -17.1f, 0.7f);
        }
        // The jaw, hinged under the back of the snout. UV (126,40): 6 x 2.5 x 7.
        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create()
                        .texOffs(126, 40).addBox(-3.0f, 0.0f, -7.0f, 6, 2.5f, 7),
                PartPose.offset(0.0f, 4.6f, -10.5f));
        for (int k = 0; k < 2; k++) {
            float x = k == 0 ? -2.2f : 2.2f;
            segment(jaw, "fang_l" + k, 200, 44, x, 0.2f, -6.5f, x * 0.9f, -1.8f, -6.6f, 0.7f);
        }
        // Small round ears. UV (154,42): 3 x 3 x 1.5.
        for (int side = -1; side <= 1; side += 2) {
            head.addOrReplaceChild(side < 0 ? "ear_r" : "ear_l", CubeListBuilder.create()
                            .texOffs(154, 42).addBox(-1.5f, -3.0f, -0.75f, 3, 3, 1.5f),
                    PartPose.offsetAndRotation(side * 5.5f, -5.5f, -2.5f, -0.2f, 0.0f, side * 0.35f));
        }
        // A ruff of fur at the back of the skull and under the jaw line.
        for (int i = 0; i < 10; i++) {
            float a = i / 9.0f;
            float x = Mth.lerp(a, -6.9f, 6.9f);
            strand(head, "ruff" + i, rng, x, 5.0f, -2.0f + rng.nextFloat() * 2.0f, x * 1.1f, 8.5f + rng.nextFloat() * 2.0f, 0.5f, 1.4f);
        }

        // The fifteen eyes.
        for (int i = 0; i < EYE_COUNT; i++) {
            float[] e = EYES[i];
            int size = (int) e[3];
            float s = EYE_SIZE[size];
            int facing = (int) e[4];
            float xRot = facing == 1 ? -Mth.HALF_PI : 0.0f;
            float yRot = facing == 2 ? -Mth.HALF_PI : facing == 3 ? Mth.HALF_PI : 0.0f;
            // Socket: a dark, wet, hairless ring of skin in the fur around the eye.
            // UV by size: (40,100) (48,100) (54,100).
            float so = s + 0.9f;
            head.addOrReplaceChild("socket" + i, CubeListBuilder.create()
                            .texOffs(size == 0 ? 40 : size == 1 ? 48 : 54, 100).addBox(-so / 2.0f, -so / 2.0f, -0.15f, so, so, 0.2f),
                    PartPose.offsetAndRotation(e[0], e[1], e[2], xRot, yRot, 0.0f));
            // Eyeball: glossy black, standing out of the fur. UV by size: (0,100) (8,100) (14,100).
            int eu = size == 0 ? 0 : size == 1 ? 8 : 14;
            PartDefinition ball = head.addOrReplaceChild("eye" + i, CubeListBuilder.create()
                            .texOffs(eu, 100).addBox(-s / 2.0f, -s / 2.0f, -0.5f, s, s, 0.8f),
                    PartPose.offsetAndRotation(e[0], e[1], e[2], xRot, yRot, 0.0f));
            // Lid: furred skin hinged along the top; it rolls up (shrinks upward) as the eye opens.
            // UV by size: (20,100) (28,100) (34,100).
            int lu = size == 0 ? 20 : size == 1 ? 28 : 34;
            ball.addOrReplaceChild("lid", CubeListBuilder.create()
                            .texOffs(lu, 100).addBox(-s / 2.0f - 0.2f, 0.0f, -0.3f, s + 0.4f, s + 0.25f, 0.3f),
                    PartPose.offset(0.0f, -s / 2.0f - 0.12f, -0.55f));
        }
    }

    /**
     * One leg: a thick upper leg, a lower leg, and a broad flat paw with four long hooked black
     * claws. {@code pair}: 0 front, 1 middle, 2 rear (each pair has its own UVs).
     */
    private static void leg(PartDefinition owner, String name, int pair, float x, float y, float z, RandomSource rng) {
        // Upper: UV (0|26|52, 58): 6 x 8.5 x 6. Lower: UV (0|22|44, 74): 5 x 6.5 x 5.
        // Paw: UV (0|28|56, 88): 6 x 2 x 7.
        PartDefinition up = owner.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(pair * 26, 58).addBox(-3.0f, -1.0f, -3.0f, 6, UPPER + 1.0f, 6),
                PartPose.offset(x, y, z));
        PartDefinition low = up.addOrReplaceChild("lower", CubeListBuilder.create()
                        .texOffs(pair * 22, 74).addBox(-2.5f, -0.5f, -2.5f, 5, LOWER + 0.5f, 5),
                PartPose.offset(0.0f, UPPER, 0.0f));
        PartDefinition p = low.addOrReplaceChild("paw", CubeListBuilder.create()
                        .texOffs(pair * 28, 88).addBox(-3.0f, -0.5f, -5.0f, 6, 2, 7),
                PartPose.offset(0.0f, LOWER, 0.0f));
        // Long fur hanging over the back of the upper leg (the "trousers").
        for (int k = 0; k < 3; k++) {
            float sx = -2.4f + k * 2.4f;
            strand(up, "fur" + k, rng, sx, 1.0f, 2.8f, sx * 1.1f, UPPER + 1.5f, 3.6f, 1.3f);
        }
        // Claws: UV (230,34). Long, curved down into the ground.
        for (int c = 0; c < 4; c++) {
            float cx = -2.25f + c * 1.5f;
            float length = (c == 1 || c == 2) ? 2.4f : 1.9f;
            segment(p, "claw" + c + "a", 230, 34, cx, 0.4f, -4.6f, cx * 1.08f, 0.7f, -4.6f - length, 0.65f);
            segment(p, "claw" + c + "b", 230, 34, cx * 1.08f, 0.7f, -4.6f - length, cx * 1.1f, 1.55f, -5.3f - length, 0.45f);
        }
    }

    /**
     * Shaggy strands hanging from the two flanks and the belly of a box spanning x in
     * [-halfW, halfW], y in [top, bottom], z in [z0, z1].
     */
    private static void shag(PartDefinition part, String prefix, RandomSource rng, float halfW, float top, float bottom,
                             float z0, float z1, int perSide) {
        int n = 0;
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < perSide; i++) {
                float z = Mth.lerp((i + rng.nextFloat()) / perSide, z0 + 0.6f, z1 - 0.6f);
                // Two rows: along the lower flank and in a band higher up.
                boolean low = (i % 3) != 0;
                float y = low ? bottom - 1.2f - rng.nextFloat() * 1.5f : Mth.lerp(0.45f + rng.nextFloat() * 0.15f, top, bottom);
                float x = side * (halfW + 0.15f);
                float length = low ? 4.0f + rng.nextFloat() * 3.0f : 3.0f + rng.nextFloat() * 2.0f;
                strand(part, prefix + n++, rng, x, y, z, x + side * (0.6f + rng.nextFloat() * 0.8f), y + length,
                        z + 0.8f + rng.nextFloat() * 1.2f, 1.2f + rng.nextFloat() * 0.5f);
            }
        }
        // Belly fringe.
        for (int i = 0; i < perSide / 2; i++) {
            float z = Mth.lerp((i + rng.nextFloat()) / (perSide / 2.0f), z0 + 0.6f, z1 - 0.6f);
            float x = (rng.nextFloat() - 0.5f) * halfW * 1.6f;
            strand(part, prefix + n++, rng, x, bottom - 0.3f, z, x * 1.05f, bottom + 3.0f + rng.nextFloat() * 2.5f,
                    z + 1.0f, 1.3f + rng.nextFloat() * 0.4f);
        }
    }

    /** One strand of fur from the root to the tip, in two tapering pieces. UV (160..248, 58..90). */
    private static void strand(PartDefinition parent, String name, RandomSource rng,
                               float rx, float ry, float rz, float tx, float ty, float tz, float thickness) {
        int u = 160 + rng.nextInt(88);
        int v = 58 + rng.nextInt(32);
        float mx = Mth.lerp(0.5f, rx, tx) + (rng.nextFloat() - 0.5f) * 0.6f;
        float my = Mth.lerp(0.5f, ry, ty);
        float mz = Mth.lerp(0.5f, rz, tz) + (rng.nextFloat() - 0.5f) * 0.6f;
        segment(parent, name + "_a", u, v, rx, ry, rz, mx, my, mz, thickness);
        segment(parent, name + "_b", u, v, mx, my, mz, tx, ty, tz, thickness * 0.7f);
    }

    /** Moss pads, small ferns and bracket fungi growing on its back and flank. */
    private static void overgrowth(PartDefinition hips, PartDefinition mid, PartDefinition chest, RandomSource rng) {
        // Moss pads: UV (0,112). Flat, just above the back.
        float[][] pads = {
                {0, -1.0f, -12.2f, -2.0f, 9, 7}, {0, 3.5f, -12.2f, 2.5f, 6, 5},
                {1, -2.0f, -9.7f, -7.0f, 10, 8}, {1, 4.0f, -9.7f, -3.0f, 5, 6},
                {2, 1.5f, -13.7f, -6.0f, 8, 6}, {1, -4.5f, -9.7f, -10.5f, 4, 5},
        };
        PartDefinition[] owners = {hips, mid, chest};
        for (int i = 0; i < pads.length; i++) {
            float[] p = pads[i];
            owners[(int) p[0]].addOrReplaceChild("moss" + i, CubeListBuilder.create()
                            .texOffs(0, 112).addBox(-p[4] / 2.0f, -0.8f, -p[5] / 2.0f, p[4], 0.8f, p[5]),
                    PartPose.offsetAndRotation(p[1], p[2], p[3], (rng.nextFloat() - 0.5f) * 0.1f, (rng.nextFloat() - 0.5f) * 0.6f, (rng.nextFloat() - 0.5f) * 0.1f));
        }
        // Little ferns on the hump and the hips: fronds splayed out of one crown. UV (40,112).
        fern(chest, "fern0", rng, -3.5f, -13.9f, -5.0f, 5);
        fern(chest, "fern1", rng, 4.0f, -13.9f, -8.5f, 4);
        fern(hips, "fern2", rng, 2.5f, -12.4f, -1.0f, 5);
        // Bracket fungi stepping up the left shoulder. UV (60,112): 2.6 x 0.7 x 2.6.
        for (int i = 0; i < 4; i++) {
            float y = -2.0f + i * 1.6f;
            float z = -9.0f + i * 1.1f;
            chest.addOrReplaceChild("fungus" + i, CubeListBuilder.create()
                            .texOffs(60, 112).addBox(0.0f, -0.35f, -1.3f, 2.6f, 0.7f, 2.6f),
                    PartPose.offsetAndRotation(10.0f, y, z, 0.0f, 0.0f, 0.12f));
        }
    }

    /** A small fern: {@code fronds} flat leaves arching out and down from a crown. */
    private static void fern(PartDefinition parent, String name, RandomSource rng, float x, float y, float z, int fronds) {
        PartDefinition crown = parent.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offset(x, y, z));
        for (int f = 0; f < fronds; f++) {
            float yaw = f * Mth.TWO_PI / fronds + rng.nextFloat() * 0.5f;
            float length = 4.0f + rng.nextFloat() * 2.0f;
            crown.addOrReplaceChild("frond" + f, CubeListBuilder.create()
                            .texOffs(40, 112).addBox(-0.9f, -0.1f, -length, 1.8f, 0.2f, length),
                    PartPose.rotation(-0.55f - rng.nextFloat() * 0.25f, yaw, 0.0f));
        }
    }

    /**
     * One straight square rod from A to B, textured from (texU, texV). The cube is modelled along
     * +Y from its pivot, then turned so +Y points at B (the same trick as {@code HoundModel}).
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

    // ================================================================== animation

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(L227Bear entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float partial = ageInTicks - entity.tickCount;
        L227Bear.Mode mode = entity.mode();
        float time = entity.modeTime(partial);
        float rear = 0.0f;
        float jawOpen = 0.0f;
        if (mode == L227Bear.Mode.MAUL) {
            // Up on the back legs over 9 ticks, the paws come down hard at 10-11, then it recovers.
            if (time < 9.0f) {
                rear = smooth(time / 9.0f);
            } else if (time < 11.5f) {
                rear = 1.0f - 1.2f * smooth((time - 9.0f) / 2.5f);
            } else {
                rear = -0.2f * (1.0f - smooth(Math.min(1.0f, (time - 11.5f) / 10.0f)));
            }
            jawOpen = Math.max(0.0f, rear) * 0.8f;
        } else if (mode == L227Bear.Mode.WAKE) {
            // The roar: the jaw drops wide while it heaves itself up.
            jawOpen = time > 6.0f && time < 28.0f ? Mth.sin((time - 6.0f) / 22.0f * Mth.PI) : 0.0f;
        }
        int sideSeed = (entity.getId() & 1) == 0 ? 1 : -1;
        animate(ageInTicks + (entity.getId() & 63), limbSwing, limbSwingAmount, entity.rise(partial), entity.eyes(partial),
                rear, jawOpen, netHeadYaw, headPitch, mode == L227Bear.Mode.STIR, sideSeed);
    }

    /**
     * Poses the model. Kept apart from {@link #setupAnim} so the pose depends only on these
     * numbers (that is also how the art tools render contact sheets of it).
     *
     * @param t          time in ticks (only for breathing, twitches and blinks)
     * @param walk       distance walked (limbSwing) and {@code walkAmount} how fast (0..1)
     * @param rise       0 lying curled up, 1 standing
     * @param eyesOpen   0 every eye shut, 1 all fifteen open
     * @param rear       rearing up to maul (1 up, negative = slammed down)
     * @param jawOpen    0..1
     * @param stirring   half awake: the open eyes look around, the head lifts
     * @param curlSide   +1 or -1: which way it curls when it sleeps
     */
    public void animate(float t, float walk, float walkAmount, float rise, float eyesOpen, float rear, float jawOpen,
                        float headYawDeg, float headPitchDeg, boolean stirring, int curlSide) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float lie = 1.0f - rise;
        float amount = Mth.clamp(walkAmount * 1.5f, 0.0f, 1.0f) * rise;

        // --- Body -----------------------------------------------------------------------------
        hips.y += lie * LIE_DROP;
        // Breathing: slow and deep asleep, quicker awake.
        float breath = Mth.sin(t * Mth.lerp(rise, 0.075f, 0.16f));
        hips.y += breath * Mth.lerp(rise, 0.35f, 0.15f);
        chest.xRot += breath * 0.012f;
        // Asleep, the long body curls into a crescent, the head tucked along the front paws.
        mid.yRot += 0.12f * lie * curlSide;
        chest.yRot += 0.16f * lie * curlSide;
        neck.yRot += 0.42f * lie * curlSide;
        neck.xRot += 0.32f * lie;
        head.zRot += 0.32f * lie * curlSide;
        head.xRot += 0.18f * lie;
        // Walking: a rolling heave, the shoulders and hips rocking against each other.
        float cycles = walk * 0.12f;
        float phase = cycles - Mth.floor(cycles);
        hips.zRot += Mth.sin(Mth.TWO_PI * phase) * 0.035f * amount;
        chest.zRot -= Mth.sin(Mth.TWO_PI * phase) * 0.05f * amount;
        hips.y -= Math.abs(Mth.sin(Mth.TWO_PI * phase)) * 0.6f * amount;
        // Rearing: the whole body tips up on the rear hip joints, the chest straightens.
        hips.xRot -= Math.max(0.0f, rear) * 0.62f - Math.min(0.0f, rear) * 0.18f;
        mid.xRot -= rear * 0.1f;
        chest.xRot -= rear * 0.12f;

        // --- Legs -----------------------------------------------------------------------------
        float hipsPitch = hips.xRot;
        float midPitch = hipsPitch + mid.xRot;
        float chestPitch = midPitch + chest.xRot;
        // Asleep: front legs stretched forward under the head, middle legs folded out to the sides,
        // rear legs tucked back along the hips. {upper xRot, upper zRot, lower xRot} per pair.
        float[][] lying = {{-1.35f, 0.15f, 0.25f}, {0.15f, 1.15f, -1.9f}, {1.25f, 0.2f, -0.55f}};
        for (int i = 0; i < 6; i++) {
            int pair = i / 2;
            int side = (i % 2 == 0) ? -1 : 1;
            float chain = pair == 0 ? chestPitch : pair == 1 ? midPitch : hipsPitch;
            // Tripod gait: front-left, middle-right, rear-left together; the other three half a step later.
            boolean groupA = i == 1 || i == 2 || i == 5;
            float p = phase + (groupA ? 0.0f : 0.5f);
            p -= Mth.floor(p);
            float swing;
            float lift;
            if (p < 0.55f) {
                swing = Mth.lerp(p / 0.55f, -0.42f, 0.42f);  // planted: the paw slides back
                lift = 0.0f;
            } else {
                float s = (p - 0.55f) / 0.45f;
                swing = Mth.lerp(smooth(s), 0.42f, -0.42f);   // swinging forward through the air
                lift = Mth.sin(Mth.PI * s);
            }
            float standX = swing * amount - chain;           // legs stay upright whatever the body does
            float standLower = lift * 0.9f * amount;
            float standZ = -side * 0.06f;
            if (rear > 0.0f && pair < 2) {
                // Reared up: front and middle legs reach out, claws forward, and come down hard.
                float reach = pair == 0 ? 1.0f : 0.7f;
                standX = Mth.lerp(rear, standX, -chain - 1.25f * reach + Mth.sin(t * 0.9f + i) * 0.08f);
                standLower = Mth.lerp(rear, standLower, 0.9f * reach);
                standZ += rear * side * -0.25f;
            } else if (rear > 0.0f) {
                // Rear legs bend a little under the weight.
                standLower += rear * 0.25f;
                standX -= rear * 0.1f;
            }
            upper[i].xRot = Mth.lerp(lie, standX, lying[pair][0]);
            upper[i].zRot = Mth.lerp(lie, standZ, side * -lying[pair][1]);
            lower[i].xRot = Mth.lerp(lie, standLower, lying[pair][2]);
            // Paws stay flat on the ground (and lie flat when folded).
            float net = chain * rise + upper[i].xRot + lower[i].xRot;
            paw[i].xRot = -net + (rear > 0.0f && pair < 2 ? -0.5f * rear : 0.0f) + lift * 0.4f * amount;
            paw[i].zRot = -upper[i].zRot * 0.8f;
        }

        // --- Neck and head --------------------------------------------------------------------
        float yaw = headYawDeg * Mth.DEG_TO_RAD * rise;
        float pitch = headPitchDeg * Mth.DEG_TO_RAD * rise;
        neck.yRot += yaw * 0.45f;
        head.yRot += yaw * 0.4f;
        neck.xRot += pitch * 0.4f - (chestPitch) * 0.5f * rise;
        head.xRot += pitch * 0.3f;
        // Low, swaying head while it walks.
        neck.xRot += Mth.sin(Mth.TWO_PI * phase * 2.0f) * 0.04f * amount;
        if (stirring) {
            // Half awake: the head lifts off the paws a little and turns, slow and heavy.
            neck.xRot -= 0.22f;
            head.zRot -= 0.18f * curlSide;
            neck.yRot += Mth.sin(t * 0.05f) * 0.15f;
        }
        if (rear > 0.0f) {
            neck.xRot += rear * 0.3f; // head stays down on its target while it rears
        }
        jaw.xRot = jawOpen * 0.75f;
        head.xRot -= jawOpen * 0.18f;
        for (int k = 0; k < 2; k++) {
            ears[k].xRot += Mth.sin(t * 0.11f + k * 2.0f) * 0.05f + (jawOpen > 0.3f ? 0.35f : 0.0f);
        }

        // --- Eyes -----------------------------------------------------------------------------
        for (int i = 0; i < EYE_COUNT; i++) {
            float open = Mth.clamp(eyesOpen * EYE_COUNT - OPEN_ORDER[i], 0.0f, 1.0f);
            // Awake, each eye blinks now and then on its own (never all at once).
            float blinkWave = Mth.sin(t * 0.031f + i * 2.39f) * Mth.sin(t * 0.017f + i * 1.13f);
            if (blinkWave > 0.93f) {
                open *= 0.0f;
            }
            float closed = 1.0f - open;
            lid[i].visible = closed > 0.02f;
            lid[i].yScale = Math.max(0.02f, closed);
            // Open eyes don't sit still: each looks about on its own, faster while stirring.
            float speed = stirring ? 0.21f : 0.06f;
            eye[i].xRot += Mth.sin(t * speed + i * 1.7f) * 0.18f * open;
            eye[i].yRot += Mth.sin(t * speed * 0.8f + i * 2.9f) * 0.22f * open;
        }
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
