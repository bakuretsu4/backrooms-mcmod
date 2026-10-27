package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.Hound;
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
 * The Hound, built in code: a starved human body running on all fours.
 *
 * <pre>
 *        hair ≋≋≋╗
 *     ╔══skull═══╣  neck    shoulder blades   vertebrae        hip bones
 *     ║ teeth ▼▼ ╠═════╗ ▄▀▀▄▀▀▄▀▀▄▀▀▄▀▀▄▀▀▄▀▀▄▀▀▄▀▀▄▀▀▄▀▀▄▄▄▄
 *     ║  jaw ▲▲  ║     ║ ribcage (ribs show) ║ waist ║ pelvis  ▐
 *     ╚══════════╝      ╲                  ╱          ╲        ╱
 *        long arms,      ╲elbows back     ╱ knees fwd  ╲      ╱
 *        flat hands ▄▄▄▄ ╱                  feet on the balls, heels up
 * </pre>
 *
 * <p>Everything is real anatomy pushed too far: arms and legs too long, so the elbows and knees
 * stick out high when it creeps; ribs, spine and shoulder blades pressing through the skin; a
 * skull with a jaw hinged far back so the mouth opens enormously; needle teeth top and bottom;
 * long hooked claws on hands and feet. A curtain of long black hair (about 70 strands, each a
 * few thin bent pieces) hangs over the face, and swings forward when the jaw opens.
 *
 * <p>The legs are placed by a small two-bone solver ({@link #reach}): the model decides where
 * each hand and foot should be (planted on the floor or swinging forward), and the solver bends
 * shoulder/elbow or hip/knee to put it exactly there. So the paws stay on the ground whatever the
 * body does: crouching to stalk, rocking in a gallop, cringing back.
 *
 * <p>Model space: units are pixels (1/16 block), +Y points down, the front faces -Z, the ground
 * is at y = 24. The texture is 256 x 256 for a 128 x 128 UV layout (2 texels per pixel), made by
 * {@code tools/anomaly_assets/level39_mobs1.py}, which follows the UV map noted at each box.
 */
public class HoundModel extends HierarchicalModel<Hound> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Backrooms.id("hound"), "main");

    private static final float GROUND = 24.0f;
    /** The body pivot (middle of the back) at rest: 15 px above the ground. */
    private static final float BODY_Y = 9.0f;
    private static final float BODY_Z = 2.5f;
    private static final float BODY_PITCH = 0.05f;
    // Joints in body space.
    private static final float SHOULDER_X = 4.0f, SHOULDER_Y = -0.5f, SHOULDER_Z = -9.5f;
    private static final float HIP_X = 3.2f, HIP_Y = 0.5f, HIP_Z = 8.0f;
    private static final float UPPER_ARM = 9.0f, FOREARM = 8.5f;
    private static final float THIGH = 9.5f, SHIN = 9.5f;
    /** The wrist sits this far above the floor when the palm is flat. */
    private static final float WRIST_HEIGHT = 0.6f;
    /** The ankle sits this high when it stands on the balls of its feet. */
    private static final float ANKLE_HEIGHT = 2.8f;
    /** Foot pitch: toes down, heel up. */
    private static final float FOOT_PITCH = 0.55f;
    /** Where the hands and feet rest relative to their joints (forward is negative). */
    private static final float HAND_REST_Z = -1.5f;
    private static final float FOOT_REST_Z = 1.5f;
    private static final float NECK_PITCH = 0.55f;
    private static final float HEAD_PITCH = -0.35f;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart hairFront;
    private final ModelPart hairSides;
    private final ModelPart hairBack;
    private final ModelPart[] upper = new ModelPart[4];
    private final ModelPart[] lower = new ModelPart[4];
    private final ModelPart[] end = new ModelPart[4];

    public HoundModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.neck = body.getChild("neck");
        this.head = neck.getChild("head");
        this.jaw = head.getChild("jaw");
        this.hairFront = head.getChild("hair_front");
        this.hairSides = head.getChild("hair_sides");
        this.hairBack = head.getChild("hair_back");
        String[] limbs = {"right_arm", "left_arm", "right_leg", "left_leg"};
        for (int i = 0; i < 4; i++) {
            upper[i] = body.getChild(limbs[i]);
            lower[i] = upper[i].getChild("lower");
            end[i] = lower[i].getChild(i < 2 ? "hand" : "foot");
        }
    }

    // ================================================================== building

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        RandomSource rng = RandomSource.create(0x40D7_39L);

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        // Ribcage: deep and narrow. UV (0,0).
                        .texOffs(0, 0).addBox(-4.5f, -4.0f, -11.0f, 9, 7, 10)
                        // Waist: sucked in under the ribs, the back sagging. UV (0,18).
                        .texOffs(0, 18).addBox(-3.0f, -3.0f, -1.0f, 6, 5, 7)
                        // Pelvis: bony, higher than the waist. UV (40,0).
                        .texOffs(40, 0).addBox(-4.0f, -4.5f, 5.0f, 8, 6, 6),
                PartPose.offsetAndRotation(0.0f, BODY_Y, BODY_Z, BODY_PITCH, 0.0f, 0.0f));
        skeleton(body, rng);

        PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create()
                        // UV (70,0).
                        .texOffs(70, 0).addBox(-1.75f, -1.75f, -5.5f, 3.5f, 3.5f, 6),
                PartPose.offsetAndRotation(0.0f, -2.2f, -10.5f, NECK_PITCH, 0.0f, 0.0f));
        // Stringy neck muscles running from behind the ears down into the chest.
        for (int side = -1; side <= 1; side += 2) {
            segment(neck, "sinew" + side, 34, 48, side * 1.6f, -0.6f, -5.2f, side * 2.4f, 0.6f, 0.4f, 0.8f);
        }
        head(neck, rng);

        for (int side = -1; side <= 1; side += 2) {
            arm(body, side < 0 ? "right_arm" : "left_arm", side, rng);
            leg(body, side < 0 ? "right_leg" : "left_leg", side, rng);
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Bones showing through: vertebrae, shoulder blades, ribs, hip bones. */
    private static void skeleton(PartDefinition body, RandomSource rng) {
        // Vertebrae: a knobbly ridge all along the back, following the dip at the waist.
        int i = 0;
        for (float z = -10.4f; z <= 10.2f; z += 1.45f, i++) {
            float top = z < -1.0f ? -4.0f : z < 5.0f ? -3.0f : -4.5f;
            float size = z < -1.0f ? 1.4f : 1.1f;
            body.addOrReplaceChild("vertebra" + i, CubeListBuilder.create().texOffs(36, 50)
                            .addBox(-size / 2.0f, -0.9f, -0.55f, size, 1.1f, 1.1f),
                    PartPose.offsetAndRotation(0.0f, top, z, (rng.nextFloat() - 0.5f) * 0.3f, 0.0f, 0.0f));
        }
        for (int side = -1; side <= 1; side += 2) {
            String s = side < 0 ? "r" : "l";
            // Shoulder blades: flat plates standing proud of the back.
            body.addOrReplaceChild("scapula_" + s, CubeListBuilder.create().texOffs(36, 54)
                            .addBox(-1.7f, -0.4f, -2.1f, 3.4f, 0.8f, 4.2f),
                    PartPose.offsetAndRotation(side * 2.5f, -4.1f, -8.6f, 0.15f, side * 0.12f, side * 0.42f));
            // Ribs: five arcs down each flank, pressing through. Each is two short rods.
            for (int r = 0; r < 5; r++) {
                float z = -9.9f + r * 1.85f;
                float sweep = 0.5f + r * 0.12f;
                segment(body, "rib_" + s + r + "a", 36, 58, side * 4.45f, -3.2f, z, side * 4.75f, 0.0f, z + sweep, 0.7f);
                segment(body, "rib_" + s + r + "b", 36, 58, side * 4.75f, 0.0f, z + sweep, side * 3.6f, 2.95f, z + sweep * 1.6f, 0.6f);
            }
            // Hip bones jutting out above the thighs.
            body.addOrReplaceChild("ilium_" + s, CubeListBuilder.create().texOffs(36, 50)
                            .addBox(-0.7f, -0.8f, -1.4f, 1.4f, 1.6f, 2.8f),
                    PartPose.offsetAndRotation(side * 3.9f, -3.6f, 6.4f, 0.2f, 0.0f, side * 0.3f));
            // Trapezius: cords from the neck out to each shoulder.
            segment(body, "trapezius_" + s, 34, 48, side * 1.0f, -4.0f, -10.2f, side * 3.8f, -1.6f, -9.2f, 1.1f);
        }
    }

    /**
     * The head: a long skull, a heavy upper mouth, a jaw hinged far back (so it gapes), needle
     * teeth on both, a dark wet mouth inside, and the hair.
     */
    private static void head(PartDefinition neck, RandomSource rng) {
        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create()
                        // Skull. UV (40,16).
                        .texOffs(40, 16).addBox(-3.0f, -3.5f, -6.5f, 6, 6, 7)
                        // Upper mouth, wider than the skull: the start of the huge mouth. UV (70,12).
                        .texOffs(70, 12).addBox(-3.2f, 0.5f, -7.5f, 6.4f, 2, 6)
                        // Inside of the mouth (gums, throat): only seen when the jaw drops. UV (64,64).
                        .texOffs(64, 64).addBox(-2.8f, 1.6f, -7.0f, 5.6f, 2.5f, 5.5f)
                        // Back of the skull.
                        .texOffs(36, 54).addBox(-2.5f, -4.0f, -1.5f, 5, 2, 2.5f),
                PartPose.offsetAndRotation(0.0f, 0.0f, -5.5f, HEAD_PITCH, 0.0f, 0.0f));

        // Upper teeth: a row across the front and down both sides, long and uneven.
        int t = 0;
        for (int k = 0; k < 6; k++) {
            float x = -2.6f + k * 1.04f;
            tooth(head, "tooth_u" + t++, rng, x, 2.5f, -7.1f, 1.0f);
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 4; k++) {
                tooth(head, "tooth_u" + t++, rng, side * 2.9f, 2.5f, -6.1f + k * 1.25f, 1.0f);
            }
        }

        // The jaw, hinged near the back of the mouth. UV (70,22).
        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create()
                        .texOffs(70, 22).addBox(-3.0f, 0.0f, -6.5f, 6, 1.8f, 6.5f),
                PartPose.offset(0.0f, 2.6f, -0.8f));
        t = 0;
        for (int k = 0; k < 5; k++) {
            tooth(jaw, "tooth_l" + t++, rng, -2.2f + k * 1.1f, 0.0f, -6.1f, -1.0f);
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 4; k++) {
                tooth(jaw, "tooth_l" + t++, rng, side * 2.6f, 0.0f, -5.0f + k * 1.2f, -1.0f);
            }
        }

        hair(head, rng);
    }

    /** One needle tooth hanging down ({@code dir} 1) or standing up ({@code dir} -1). UV (64,56). */
    private static void tooth(PartDefinition parent, String name, RandomSource rng, float x, float y, float z, float dir) {
        float length = 1.3f + rng.nextFloat() * 1.1f;
        float tilt = (rng.nextFloat() - 0.5f) * 0.35f;
        segment(parent, name, 64, 56, x, y - dir * 0.3f, z, x + tilt * 0.6f, y + dir * length, z - 0.15f, 0.55f + rng.nextFloat() * 0.2f);
    }

    /**
     * Long, lank black hair, in three groups so each can swing on its own: a curtain over the
     * face, the sides, and the back falling over the neck. Strands are 3 thin pieces with a slight
     * wave, of uneven length and thickness; a scalp cap covers the skull top. UV: hair (96..128, 0..64).
     */
    private static void hair(PartDefinition head, RandomSource rng) {
        head.addOrReplaceChild("scalp", CubeListBuilder.create().texOffs(96, 50)
                .addBox(-3.3f, -4.1f, -6.9f, 6.6f, 0.8f, 7.6f), PartPose.ZERO);

        // Front: hangs from the hairline down in front of the whole face.
        PartDefinition front = head.addOrReplaceChild("hair_front", CubeListBuilder.create(), PartPose.offset(0.0f, -3.6f, -6.8f));
        for (int i = 0; i < 26; i++) {
            float x = -3.3f + i * (6.6f / 25.0f) + (rng.nextFloat() - 0.5f) * 0.25f;
            float length = 8.0f + rng.nextFloat() * 4.5f;
            float rootZ = (rng.nextFloat() - 0.2f) * 0.9f;
            float tipX = x * 1.18f + (rng.nextFloat() - 0.5f) * 0.9f;
            float tipZ = -1.3f - rng.nextFloat() * 0.8f;
            strand(front, "s" + i, rng, x, 0.0f, rootZ, tipX, length, tipZ, -0.6f);
        }
        // Sides: over the cheeks and ears, hanging past the jaw.
        PartDefinition sides = head.addOrReplaceChild("hair_sides", CubeListBuilder.create(), PartPose.offset(0.0f, -3.6f, -3.0f));
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 13; i++) {
                float z = -3.6f + i * 0.33f + (rng.nextFloat() - 0.5f) * 0.3f;
                float x = side * (2.6f + rng.nextFloat() * 0.6f);
                float length = 7.0f + rng.nextFloat() * 4.0f;
                strand(sides, "s" + side + "_" + i, rng, x, 0.0f, z, side * (3.7f + rng.nextFloat() * 1.0f), length, z + (rng.nextFloat() - 0.5f) * 1.2f, 0.0f);
            }
        }
        // Back: from the crown, falling down over the nape.
        PartDefinition back = head.addOrReplaceChild("hair_back", CubeListBuilder.create(), PartPose.offset(0.0f, -3.6f, 0.0f));
        for (int i = 0; i < 18; i++) {
            float x = -3.0f + (i % 9) * 0.75f + (rng.nextFloat() - 0.5f) * 0.3f;
            float z = i < 9 ? -1.5f : 0.4f;
            float length = 6.0f + rng.nextFloat() * 4.0f;
            strand(back, "s" + i, rng, x, -0.2f, z, x * 1.25f, length * 0.75f, z + length * 0.55f, 0.0f);
        }
    }

    /** One wavy strand of hair from the root to (tipX, tipY, tipZ), bowed by {@code bowZ}. */
    private static void strand(PartDefinition parent, String name, RandomSource rng,
                               float rx, float ry, float rz, float tipX, float tipY, float tipZ, float bowZ) {
        float thickness = 0.55f + rng.nextFloat() * 0.35f;
        int u = 96 + rng.nextInt(26);
        int v = rng.nextInt(36);
        float px = rx, py = ry, pz = rz;
        for (int p = 1; p <= 3; p++) {
            float f = p / 3.0f;
            float bow = Mth.sin(Mth.PI * f) * bowZ;
            float wave = p == 3 ? 0.0f : (rng.nextFloat() - 0.5f) * 0.5f;
            float nx = Mth.lerp(f, rx, tipX) + wave;
            float ny = Mth.lerp(f, ry, tipY);
            float nz = Mth.lerp(f, rz, tipZ) + bow + wave * 0.5f;
            segment(parent, name + "_" + p, u, v, px, py, pz, nx, ny, nz, thickness * (1.0f - 0.18f * p));
            px = nx;
            py = ny;
            pz = nz;
        }
    }

    /**
     * An arm: long thin upper arm and forearm with knobbly elbow and wrist, a flat hand with four
     * long fingers ending in hooked claws, and a thumb. {@code side}: -1 right (-X), +1 left.
     */
    private static void arm(PartDefinition body, String name, int side, RandomSource rng) {
        int uv = side < 0 ? 0 : 8;
        PartDefinition arm = body.addOrReplaceChild(name, CubeListBuilder.create()
                        // UV (0,32) / (8,32).
                        .texOffs(uv, 32).addBox(-1.0f, -0.5f, -1.0f, 2, UPPER_ARM + 0.5f, 2)
                        // Deltoid: a little meat left on the shoulder.
                        .texOffs(34, 48).addBox(-1.4f, -1.2f, -1.4f, 2.8f, 3.2f, 2.8f),
                PartPose.offset(side * SHOULDER_X, SHOULDER_Y, SHOULDER_Z));
        segment(arm, "sinew", 34, 48, side * 0.9f, 1.5f, -0.6f, side * 0.7f, UPPER_ARM - 1.0f, -0.4f, 0.7f);
        PartDefinition fore = arm.addOrReplaceChild("lower", CubeListBuilder.create()
                        // UV (16,32) / (24,32).
                        .texOffs(16 + uv, 32).addBox(-0.9f, 0.0f, -0.9f, 1.8f, FOREARM, 1.8f)
                        // Elbow knob, sticking out behind.
                        .texOffs(36, 50).addBox(-1.1f, -1.0f, -0.6f, 2.2f, 2.0f, 2.0f),
                PartPose.offset(0.0f, UPPER_ARM, 0.0f));
        segment(fore, "tendon_a", 34, 48, -0.7f, 1.0f, -0.8f, -0.4f, FOREARM - 0.6f, -0.7f, 0.55f);
        segment(fore, "tendon_b", 34, 48, 0.7f, 1.2f, -0.7f, 0.5f, FOREARM - 0.8f, -0.6f, 0.5f);
        PartDefinition hand = fore.addOrReplaceChild("hand", CubeListBuilder.create()
                        // Palm. UV (16,46).
                        .texOffs(16, 46).addBox(-1.5f, -0.4f, -2.6f, 3, 1, 3)
                        .texOffs(36, 50).addBox(-1.0f, -0.9f, -0.8f, 2, 1, 1.6f),
                PartPose.offset(0.0f, FOREARM, 0.0f));
        for (int f = 0; f < 4; f++) {
            float x = -1.2f + f * 0.8f;
            float length = 2.6f + (f == 1 || f == 2 ? 0.7f : 0.0f) + rng.nextFloat() * 0.4f;
            float tipX = x * 1.3f;
            float tipZ = -2.6f - length;
            segment(hand, "finger" + f, 34, 48, x, 0.1f, -2.4f, tipX, 0.3f, tipZ, 0.6f);
            // Claw, UV (80,56): long and hooked down into the floor.
            segment(hand, "claw" + f, 80, 56, tipX, 0.3f, tipZ, tipX * 1.05f, 0.85f, tipZ - 1.6f, 0.45f);
        }
        segment(hand, "thumb", 34, 48, side * 1.3f, 0.1f, -1.2f, side * 2.4f, 0.3f, -2.6f, 0.6f);
        segment(hand, "thumb_claw", 80, 56, side * 2.4f, 0.3f, -2.6f, side * 2.6f, 0.8f, -3.7f, 0.42f);
    }

    /**
     * A leg: thigh with a ropey muscle, a bony knee, a long shin, and a long human foot it walks
     * on the ball of, heel up, toes ending in claws.
     */
    private static void leg(PartDefinition body, String name, int side, RandomSource rng) {
        int uv = side < 0 ? 0 : 12;
        PartDefinition thigh = body.addOrReplaceChild(name, CubeListBuilder.create()
                        // UV (32,32) / (44,32).
                        .texOffs(32 + uv, 32).addBox(-1.3f, -0.5f, -1.3f, 2.6f, THIGH + 0.5f, 2.6f),
                PartPose.offset(side * HIP_X, HIP_Y, HIP_Z));
        segment(thigh, "muscle", 34, 48, side * 0.2f, 0.5f, -1.2f, 0.0f, THIGH - 1.5f, -1.0f, 1.3f);
        PartDefinition shin = thigh.addOrReplaceChild("lower", CubeListBuilder.create()
                        // UV (0,46) / (8,46).
                        .texOffs(uv == 0 ? 0 : 8, 46).addBox(-1.0f, 0.0f, -1.0f, 2, SHIN, 2)
                        // Kneecap, forward.
                        .texOffs(36, 50).addBox(-1.0f, -1.0f, -1.6f, 2, 2, 1.4f),
                PartPose.offset(0.0f, THIGH, 0.0f));
        segment(shin, "calf", 34, 48, 0.0f, 1.0f, 0.8f, 0.0f, SHIN - 2.5f, 0.6f, 1.2f);
        PartDefinition foot = shin.addOrReplaceChild("foot", CubeListBuilder.create()
                        // Sole, reaching forward. UV (16,52).
                        .texOffs(16, 52).addBox(-1.2f, -0.6f, -4.5f, 2.4f, 1.2f, 5)
                        // Heel bone.
                        .texOffs(36, 50).addBox(-0.9f, -0.8f, -0.2f, 1.8f, 1.4f, 1.6f),
                PartPose.offset(0.0f, SHIN, 0.0f));
        for (int toe = 0; toe < 4; toe++) {
            float x = -0.9f + toe * 0.6f;
            float length = 1.2f + (toe == 0 ? 0.4f : 0.0f) + rng.nextFloat() * 0.3f;
            segment(foot, "toe" + toe, 34, 48, x, 0.1f, -4.4f, x * 1.1f, 0.4f, -4.4f - length, 0.55f);
            segment(foot, "claw" + toe, 80, 56, x * 1.1f, 0.4f, -4.4f - length, x * 1.12f, 1.0f, -5.4f - length, 0.4f);
        }
    }

    /**
     * One straight square rod from A to B, textured from (texU, texV). The cube is modelled along
     * +Y from its pivot, then turned so +Y points at B (the same trick as {@code BacteriaModel}).
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
    public void setupAnim(Hound entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float partial = ageInTicks - entity.tickCount;
        Hound.Mode mode = entity.mode();
        float crouch = entity.crouch(partial);
        float bite = attackTime > 0.0f ? Mth.sin(attackTime * Mth.PI) : 0.0f;
        float jawOpen = Math.max(entity.jaw(partial), bite);
        boolean swimming = entity.isInWater() && !entity.onGround();
        boolean lunging = mode == Hound.Mode.LUNGE;
        float amount = Mth.clamp(limbSwingAmount * 1.3f, 0.0f, 1.0f);
        // 0 = walking gait, 1 = full gallop.
        float gallop = mode == Hound.Mode.CHASE ? 1.0f : Mth.clamp((limbSwingAmount - 0.45f) * 3.0f, 0.0f, 1.0f);
        int seed = entity.getId() * 31;
        float t = ageInTicks + (seed & 63);

        // --- Body -------------------------------------------------------------------------
        float cycles = limbSwing * Mth.lerp(gallop, 0.155f, 0.105f);
        float phase = cycles - Mth.floor(cycles);
        body.y += crouch * 3.0f;
        body.xRot += crouch * 0.12f;
        // Walking: a small bob twice per stride. Galloping: the whole body rocks and heaves.
        body.y += -Math.abs(Mth.sin(Mth.TWO_PI * phase)) * 0.5f * amount * (1.0f - gallop);
        body.xRot += Mth.sin(Mth.TWO_PI * phase) * 0.13f * gallop * amount;
        body.y += Mth.sin(Mth.TWO_PI * phase + 1.2f) * 1.1f * gallop * amount;
        // Breathing: ribs heave, faster when hunting.
        float breathRate = mode == Hound.Mode.PROWL ? 0.12f : 0.3f;
        body.y += Mth.sin(t * breathRate) * 0.15f;
        switch (mode) {
            case GROWL -> {
                // Shoulders dropped, the whole body trembling with the growl.
                body.xRot += 0.1f;
                body.zRot += Mth.sin(t * 2.3f) * 0.02f;
            }
            case COWED -> {
                // Cringing: rear dropped, front raised a little, body pulled back.
                body.xRot -= 0.16f;
                body.y += 1.5f;
            }
            case LUNGE -> body.xRot -= 0.32f;
            default -> {
            }
        }
        if (swimming) {
            body.xRot -= 0.15f;
        }

        // --- Legs -------------------------------------------------------------------------
        // Leg order: right arm, left arm, right leg, left leg. Walk = lateral sequence (hind,
        // front, other hind, other front); gallop = rotary (hinds close together, then fronts).
        float[] walkOffset = {0.75f, 0.25f, 0.5f, 0.0f};
        float[] gallopOffset = {0.6f, 0.47f, 0.12f, 0.0f};
        float duty = Mth.lerp(gallop, 0.65f, 0.4f);
        float stride = Mth.lerp(gallop, 5.0f, 9.5f) * amount;
        float liftHeight = Mth.lerp(gallop, 2.2f, 3.8f) * amount;
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            int side = (i % 2 == 0) ? -1 : 1;
            float p = phase + Mth.lerp(gallop, walkOffset[i], gallopOffset[i]);
            p -= Mth.floor(p);
            float dz;
            float lift;
            if (p < duty) {
                // Planted: the paw slides back under the body as it moves forward.
                float s = p / duty;
                dz = -stride * 0.5f + stride * s;
                lift = 0.0f;
            } else {
                // Swinging forward through the air.
                float s = (p - duty) / (1.0f - duty);
                float eased = s * s * (3.0f - 2.0f * s);
                dz = stride * 0.5f - stride * eased;
                lift = Mth.sin(Mth.PI * s) * liftHeight;
            }
            if (swimming) {
                // Paddling: each paw draws a fast circle.
                float a = t * 0.5f + i * Mth.HALF_PI;
                dz = Mth.sin(a) * 4.0f;
                lift = 2.0f + Mth.cos(a) * 2.5f;
            }
            if (lunging) {
                // Front paws thrown forward with the claws out, hind legs stretched back.
                dz = front ? -7.5f : 5.0f;
                lift = front ? 6.5f : 1.0f;
            }
            float restZ = front ? HAND_REST_Z : FOOT_REST_Z;
            if (mode == Hound.Mode.COWED && front) {
                restZ -= 2.0f; // front paws braced out ahead
            }
            if (mode == Hound.Mode.STALK || mode == Hound.Mode.GROWL) {
                restZ += front ? -0.5f : 0.5f; // a wide, low stance
            }
            float jointY = front ? SHOULDER_Y : HIP_Y;
            float jointZ = front ? SHOULDER_Z : HIP_Z;
            float floor = front ? WRIST_HEIGHT : ANKLE_HEIGHT;
            placeLimb(i, jointY, jointZ, GROUND - floor - lift, restZ + dz,
                    front ? UPPER_ARM : THIGH, front ? FOREARM : SHIN, front);
            upper[i].zRot = -side * (front ? 0.12f : 0.08f);
            float net = body.xRot + upper[i].xRot + lower[i].xRot;
            if (front) {
                // Palm flat on the floor; the fingers curl a little when the hand lifts.
                end[i].xRot = -net + (lift > 0.0f ? 0.5f * Math.min(1.0f, lift / 3.0f) : 0.0f);
            } else {
                // On the balls of the feet; the foot flicks back as it pushes off.
                end[i].xRot = FOOT_PITCH - net + (lift > 0.0f ? 0.4f * Math.min(1.0f, lift / 3.0f) : 0.0f);
            }
        }

        // --- Neck and head ----------------------------------------------------------------
        float yaw = netHeadYaw * Mth.DEG_TO_RAD;
        float pitch = headPitch * Mth.DEG_TO_RAD;
        neck.xRot += crouch * 0.3f + pitch * 0.5f - body.xRot * 0.6f;
        neck.yRot += yaw * 0.5f;
        head.yRot += yaw * 0.45f;
        head.xRot += pitch * 0.3f;
        // Restless head sway: slow, searching, side to side (less when running).
        float sway = 1.0f - amount * 0.6f;
        neck.yRot += Mth.sin(t * 0.045f) * 0.22f * sway;
        head.zRot += Mth.sin(t * 0.07f + 1.0f) * 0.1f * sway;
        head.xRot += Mth.sin(t * 0.05f + 2.0f) * 0.05f * sway;
        // Running: the head stays steady while the body heaves.
        neck.xRot -= Mth.sin(Mth.TWO_PI * phase) * 0.1f * gallop * amount;
        switch (mode) {
            case GROWL -> {
                neck.xRot += 0.18f;
                head.zRot += Mth.sin(t * 3.1f) * 0.045f;
                head.xRot -= 0.1f;
            }
            case COWED -> {
                // Head low and turned away, but one eye still on you.
                neck.xRot += 0.35f;
                neck.yRot += 0.45f * (seed % 2 == 0 ? 1.0f : -1.0f);
                head.xRot += 0.2f;
            }
            case LUNGE -> neck.xRot -= 0.35f;
            case SEARCH -> {
                // Nose to the floor, sniffing.
                neck.xRot += 0.45f;
                head.xRot += 0.15f + Mth.sin(t * 0.9f) * 0.05f;
            }
            default -> {
            }
        }
        if (swimming) {
            neck.xRot -= 0.4f; // head held up out of the water
        }
        // The bite: a fast snap forward.
        neck.xRot -= bite * 0.45f;

        // --- Jaw ----------------------------------------------------------------------------
        float chatter = mode == Hound.Mode.GROWL ? Mth.sin(t * 2.0f) * 0.06f : 0.0f;
        jaw.xRot = jawOpen * 1.15f + Math.max(0.0f, chatter);
        head.xRot -= jawOpen * 0.2f; // the skull tips back as it gapes

        // --- Hair: hangs with gravity, streams back when running, parts when the jaw opens ---
        float headPitchTotal = body.xRot + neck.xRot + head.xRot;
        float wind = amount * (0.25f + 0.35f * gallop) + (lunging ? 0.5f : 0.0f);
        hairFront.xRot = -headPitchTotal * 0.9f - jawOpen * 0.55f + wind + Mth.sin(t * 0.09f) * 0.04f;
        hairFront.zRot = -head.zRot * 0.8f + Mth.sin(t * 0.063f + 0.5f) * 0.035f;
        hairSides.xRot = -headPitchTotal * 0.85f + wind * 0.8f + Mth.sin(t * 0.08f + 1.0f) * 0.035f;
        hairSides.zRot = -head.zRot * 0.8f;
        hairBack.xRot = -headPitchTotal * 0.5f + wind * 0.6f;
        if (mode == Hound.Mode.GROWL || entity.isEnraged()) {
            // Bristling: the hair shivers.
            hairFront.zRot += Mth.sin(t * 1.7f) * 0.03f;
            hairSides.xRot += Mth.sin(t * 1.9f) * 0.03f;
        }
    }

    /**
     * Puts paw {@code i} at the given height (model space, ground = 24 minus clearance) and the
     * given forward/back offset from its joint, working through the body's tilt.
     */
    private void placeLimb(int i, float jointY, float jointZ, float targetY, float offsetZ, float l1, float l2, boolean elbowBack) {
        float b = body.xRot;
        float cos = Mth.cos(b);
        float sin = Mth.sin(b);
        // The joint in model space.
        float worldY = body.y + jointY * cos - jointZ * sin;
        // The target relative to the joint in model space, then turned into the body's frame.
        float dyWorld = targetY - worldY;
        float dzWorld = offsetZ;
        float dy = dyWorld * cos + dzWorld * sin;
        float dz = -dyWorld * sin + dzWorld * cos;
        reach(upper[i], lower[i], dy, dz, l1, l2, elbowBack);
    }

    /**
     * Two-bone solver: bends {@code upper} and {@code lower} (both modelled along +Y) so the end of
     * {@code lower} lands at (dy, dz) from the upper joint. {@code jointBack}: the middle joint
     * points backward (+Z, elbows) instead of forward (knees).
     */
    private static void reach(ModelPart upper, ModelPart lower, float dy, float dz, float l1, float l2, boolean jointBack) {
        float d = Mth.sqrt(dy * dy + dz * dz);
        d = Mth.clamp(d, Math.abs(l1 - l2) + 0.01f, (l1 + l2) * 0.999f);
        float toTarget = (float) Mth.atan2(dz, dy);
        float beta = (float) Math.acos(Mth.clamp((l1 * l1 + d * d - l2 * l2) / (2.0f * l1 * d), -1.0f, 1.0f));
        float gamma = (float) Math.acos(Mth.clamp((l1 * l1 + l2 * l2 - d * d) / (2.0f * l1 * l2), -1.0f, 1.0f));
        float bend = Mth.PI - gamma;
        if (jointBack) {
            upper.xRot = toTarget + beta;
            lower.xRot = -bend;
        } else {
            upper.xRot = toTarget - beta;
            lower.xRot = bend;
        }
    }
}
