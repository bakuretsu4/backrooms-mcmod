package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.Smiler;
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
 * The Smiler, built in code from flat planes (boxes with zero depth) textured with soft glowing
 * sprites from smiler.png. {@link SmilerRenderer} turns it to face the camera every frame, so
 * wherever you stand it is looking at you.
 *
 * <pre>
 *  \       ( o )         ( o )       /     two egg-shaped glowing eyes
 *   \\                             //      a huge crescent grin: 21 long glowing teeth, the
 *    \\\\                       ////       corners reaching up past the eyes
 *       \\\\\\\\\\\|||||/////////
 * </pre>
 *
 * <p>Like the camcorder photos it's based on, it's mostly light: white-hot centres, warm orange
 * glow, red and blue fringes, and a faint haze around the whole face so from far away it's just a
 * smudge of light in the dark. Behind the face hangs a tall slab of pure black, the body: in the
 * dark you can't see it, but against a lit wall it's a black hole in the shape of a doorway.
 *
 * <p>Model space: units are pixels (1/16 block), +Y points down, the face's centre is the origin
 * (1.7 blocks above its feet). Texture: 64x64 units (the PNG is 4x that, for smooth sprites).
 */
public class SmilerModel extends HierarchicalModel<Smiler> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Backrooms.id("smiler"), "main");

    private static final int TEETH = 21;
    private static final float GRIN_HALF_WIDTH = 9.6f;
    private static final float GRIN_DROP = 9.5f;
    private static final float GRIN_CORNER_Y = -4.0f;
    /** The eye sprites are drawn this much bigger than their 6x7 plane. */
    private static final float EYE_SIZE = 1.3f;
    /** Tooth length at each position: long in the middle, short at the corners. */
    private static final float[] TOOTH_LENGTH = new float[TEETH];

    static {
        for (int i = 0; i < TEETH; i++) {
            float k = Mth.lerp(i / (TEETH - 1.0f), -1.0f, 1.0f);
            TOOTH_LENGTH[i] = 0.55f + 0.65f * (1.0f - k * k);
        }
    }

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart face;
    private final ModelPart rightEye;
    private final ModelPart leftEye;
    private final ModelPart grin;
    private final ModelPart[] teeth = new ModelPart[TEETH];
    /** 0..1, how brightly it glows this frame (fades in, flickers in the light, fades out). */
    private float glow = 1.0f;

    public SmilerModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.face = root.getChild("face");
        this.rightEye = face.getChild("right_eye");
        this.leftEye = face.getChild("left_eye");
        this.grin = face.getChild("grin");
        for (int i = 0; i < TEETH; i++) {
            teeth[i] = grin.getChild("tooth" + i);
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // The body: a black slab 1 block wide, from above its head down to the floor.
        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 20).addBox(-8.0f, -11.0f, 0.0f, 16.0f, 40.0f, 0.0f), PartPose.ZERO);

        PartDefinition face = root.addOrReplaceChild("face", CubeListBuilder.create(), PartPose.ZERO);
        // A faint warm haze behind the whole face.
        face.addOrReplaceChild("haze", CubeListBuilder.create()
                .texOffs(16, 0).addBox(-12.0f, -9.0f, 0.0f, 24.0f, 18.0f, 0.0f), PartPose.ZERO);
        // Eggs leaning in toward each other at the top.
        eye(face, "right_eye", -4.6f, 0.2f);
        eye(face, "left_eye", 4.6f, -0.2f);

        // Grin: each tooth stands square to the curve, so they fan out like the photo.
        PartDefinition grin = face.addOrReplaceChild("grin", CubeListBuilder.create(), PartPose.ZERO);
        for (int i = 0; i < TEETH; i++) {
            float x = Mth.lerp(i / (TEETH - 1.0f), -GRIN_HALF_WIDTH, GRIN_HALF_WIDTH);
            grin.addOrReplaceChild("tooth" + i,
                    CubeListBuilder.create().texOffs(12, 0).addBox(-1.0f, -3.0f, 0.0f, 2.0f, 6.0f, 0.0f),
                    PartPose.offsetAndRotation(x, grinY(x), 0.0f, 0.0f, 0.0f, grinAngle(x)));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void eye(PartDefinition face, String name, float x, float tilt) {
        face.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.0f, -3.5f, 0.0f, 6.0f, 7.0f, 0.0f),
                PartPose.offsetAndRotation(x, -5.5f, 0.0f, 0.0f, 0.0f, tilt));
    }

    /** The curve of the grin: corners high (level with the eyes), middle low (+Y is down). */
    private static float grinY(float x) {
        float k = x / GRIN_HALF_WIDTH;
        return GRIN_CORNER_Y + GRIN_DROP * (1.0f - k * k);
    }

    /** Slope of the curve at x, as an angle. */
    private static float grinAngle(float x) {
        return (float) Math.atan(-2.0f * GRIN_DROP * x / (GRIN_HALF_WIDTH * GRIN_HALF_WIDTH));
    }

    @Override
    public ModelPart root() {
        return root;
    }

    /** Draw only the glowing face (additive pass) or only the black body (normal pass). */
    public void show(boolean showFace, boolean showBody) {
        face.visible = showFace;
        body.visible = showBody;
    }

    /** How brightly the face glows this frame, 0..1. Valid after {@link #setupAnim}. */
    public float glow() {
        return glow;
    }

    @Override
    public void setupAnim(Smiler entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float partial = ageInTicks - entity.tickCount;
        float since = entity.clientModeAge(partial);
        Smiler.Mode mode = entity.mode();
        int seed = entity.getId() * 37;
        float squint = Mth.clamp(entity.burn() * 1.3f, 0.0f, 1.0f);

        // Glow: fades in as it appears, shudders in the light, dies away when it's driven off.
        float open = mode == Smiler.Mode.APPEAR ? smooth(Mth.clamp((since - 4.0f) / 30.0f, 0.0f, 1.0f)) : 1.0f;
        glow = open * (0.92f + 0.08f * Mth.sin(ageInTicks * 0.13f));
        if (mode == Smiler.Mode.FLINCH) {
            glow *= 0.65f + Math.abs(twitch(seed + 9, ageInTicks, 1, 0.35f));
        }

        // Eyes: open slowly, blink now and then, narrow to slits as the light burns it.
        float blink = blinking(seed, ageInTicks) ? 0.1f : 1.0f;
        float eyeScale = (0.35f + 0.65f * open) * blink * (1.0f - 0.75f * squint) * (mode == Smiler.Mode.LUNGE ? 1.25f : 1.0f);
        rightEye.yScale = Math.max(0.05f, eyeScale) * EYE_SIZE;
        leftEye.yScale = Math.max(0.05f, eyeScale) * EYE_SIZE;
        rightEye.xScale = EYE_SIZE;
        leftEye.xScale = EYE_SIZE;

        // Grin: a bold one's is wider; it spreads as it appears, tightens in the light, and its
        // teeth lengthen when it lunges. Each tooth twitches a little on its own, so the grin
        // never looks quite still.
        grin.xScale = (entity.isBold() ? 1.22f : 1.0f) * (0.5f + 0.5f * open) * (1.0f - 0.25f * squint);
        float toothStretch = (entity.isBold() ? 1.15f : 1.0f) * (mode == Smiler.Mode.LUNGE ? 1.45f : 1.0f);
        for (int i = 0; i < TEETH; i++) {
            teeth[i].yScale = TOOTH_LENGTH[i] * toothStretch * (1.0f + twitch(seed + 20 + i, ageInTicks, 4, 0.08f));
        }

        // In the light it shudders.
        if (mode == Smiler.Mode.FLINCH) {
            face.x += twitch(seed, ageInTicks, 1, 0.6f);
            face.y += twitch(seed + 1, ageInTicks, 1, 0.4f);
            face.zRot += twitch(seed + 2, ageInTicks, 2, 0.1f);
        }
        // Otherwise it drifts very slightly, as if hanging in the air.
        face.y += Mth.sin(ageInTicks * 0.05f + seed) * 0.3f;

        // Dispel: shrivels to nothing, twisting, as the glow dies.
        if (mode == Smiler.Mode.DISPEL) {
            float k = Mth.clamp(since / 20.0f, 0.0f, 1.0f);
            float scale = 1.0f - k * k;
            face.xScale = scale;
            face.yScale = scale;
            face.zRot += k * 1.4f + twitch(seed + 3, ageInTicks, 1, 0.2f * k);
            face.x += twitch(seed + 4, ageInTicks, 1, 1.2f * k);
            glow *= 1.0f - k;
        }
    }

    private static float smooth(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    /** Blinks for 3 ticks at an uneven rhythm (every 3-6 s), different for each Smiler. */
    private static boolean blinking(int seed, float ageInTicks) {
        int period = 60 + Math.floorMod(seed, 60);
        return Math.floorMod((int) ageInTicks + seed, period) < 3;
    }

    /** Deterministic jitter that changes value every {@code period} ticks. */
    private static float twitch(int seed, float t, int period, float amplitude) {
        int bucket = Mth.floor(t / period);
        long h = (long) (seed * 0x9E3779B1L) ^ (bucket * 0x85EBCA77L);
        h ^= h >>> 15;
        h *= 0x2C1B3C6DL;
        h ^= h >>> 12;
        float unit = (h & 0xFFFF) / 65535.0f;
        return (unit * 2.0f - 1.0f) * amplitude;
    }
}
