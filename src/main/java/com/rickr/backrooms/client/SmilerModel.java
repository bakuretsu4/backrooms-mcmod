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
import net.minecraft.util.RandomSource;

/**
 * The Smiler, built in code from flat planes (boxes with zero depth). It is drawn as a billboard:
 * {@link SmilerRenderer} turns it to face the camera every frame, so the face always looks at you.
 *
 * <pre>
 *     ▄██▄          ▄██▄        two almond eyes, slanting up at the outer corners
 *     ▀██▀          ▀██▀
 *  ▀▄                    ▄▀     corners of the grin hooking up toward the cheeks
 *    ▀█▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄█▀       a wide grin: 15 teeth along a curve between two thin lips
 * </pre>
 *
 * <p>The face is about 1 block wide. Below it hangs a body you only see with NightShot: a black,
 * flickering, roughly human shape made of ragged wisps, like black fire.
 *
 * <p>Model space: units are pixels (1/16 block), +Y points down, the face's centre is the origin.
 */
public class SmilerModel extends HierarchicalModel<Smiler> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Backrooms.id("smiler"), "main");

    private static final int TEETH = 15;
    private static final float GRIN_HALF_WIDTH = 6.6f;
    private static final int WISPS = 14;

    private final ModelPart root;
    private final ModelPart face;
    private final ModelPart rightEye;
    private final ModelPart leftEye;
    private final ModelPart grin;
    private final ModelPart body;
    private final ModelPart[] wisps;

    public SmilerModel(ModelPart root) {
        this.root = root;
        this.face = root.getChild("face");
        this.rightEye = face.getChild("right_eye");
        this.leftEye = face.getChild("left_eye");
        this.grin = face.getChild("grin");
        this.body = root.getChild("body");
        ModelPart wispRoot = body.getChild("wisps");
        this.wisps = new ModelPart[WISPS];
        for (int i = 0; i < WISPS; i++) {
            wisps[i] = wispRoot.getChild("wisp" + i);
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition face = root.addOrReplaceChild("face", CubeListBuilder.create(), PartPose.ZERO);
        // Eyes: an almond = a wide middle bar plus a narrower bar above and below it. The outer
        // corners tilt up (zRot), which is what makes the look so unfriendly.
        eye(face, "right_eye", -3.9f, 0.22f);
        eye(face, "left_eye", 3.9f, -0.22f);

        // Grin: teeth along a curve (corners high, middle low), a thin lip above and below, and
        // a short hook at each corner reaching up toward the cheeks.
        PartDefinition grin = face.addOrReplaceChild("grin", CubeListBuilder.create(), PartPose.ZERO);
        for (int i = 0; i < TEETH; i++) {
            float x = Mth.lerp(i / (TEETH - 1.0f), -GRIN_HALF_WIDTH, GRIN_HALF_WIDTH);
            float y = grinY(x);
            float h = 1.3f + 0.6f * (1.0f - (x / GRIN_HALF_WIDTH) * (x / GRIN_HALF_WIDTH));
            grin.addOrReplaceChild("tooth" + i,
                    CubeListBuilder.create().texOffs(0, 0).addBox(-0.36f, -h / 2.0f, 0.0f, 0.72f, h, 0.0f),
                    PartPose.offsetAndRotation(x, y, 0.0f, 0.0f, 0.0f, grinAngle(x)));
        }
        int lipPieces = 10;
        for (int i = 0; i < lipPieces; i++) {
            float x0 = Mth.lerp(i / (float) lipPieces, -GRIN_HALF_WIDTH - 0.4f, GRIN_HALF_WIDTH + 0.4f);
            float x1 = Mth.lerp((i + 1) / (float) lipPieces, -GRIN_HALF_WIDTH - 0.4f, GRIN_HALF_WIDTH + 0.4f);
            float xm = (x0 + x1) / 2.0f;
            float upper = 1.1f + 0.15f * (1.0f - (xm / GRIN_HALF_WIDTH) * (xm / GRIN_HALF_WIDTH));
            float lower = 1.1f + 0.5f * (1.0f - (xm / GRIN_HALF_WIDTH) * (xm / GRIN_HALF_WIDTH));
            segment(grin, "lip_top" + i, x0, grinY(x0) - upper, x1, grinY(x1) - upper, 0.28f);
            segment(grin, "lip_bottom" + i, x0, grinY(x0) + lower, x1, grinY(x1) + lower, 0.28f);
        }
        for (int side = -1; side <= 1; side += 2) {
            float cx = side * (GRIN_HALF_WIDTH + 0.4f);
            float cy = grinY(cx);
            segment(grin, side < 0 ? "hook_right" : "hook_left", cx, cy, cx + side * 1.4f, cy - 2.2f, 0.32f);
        }

        // NightShot body: below the face (the face sits 27 px above the feet).
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 8).addBox(-3.5f, 4.0f, 0.0f, 7.0f, 11.0f, 0.0f)    // chest
                        .texOffs(0, 8).addBox(-2.6f, 2.0f, 0.0f, 5.2f, 3.0f, 0.0f)     // neck, up into the face
                        .texOffs(0, 8).addBox(-3.0f, 14.5f, 0.0f, 6.0f, 3.5f, 0.0f)    // hips
                        .texOffs(0, 8).addBox(-2.8f, 17.5f, 0.0f, 2.0f, 9.5f, 0.0f)    // legs
                        .texOffs(0, 8).addBox(0.8f, 17.5f, 0.0f, 2.0f, 9.5f, 0.0f)
                        .texOffs(0, 8).addBox(-6.0f, 4.5f, 0.0f, 1.6f, 15.0f, 0.0f)    // long arms
                        .texOffs(0, 8).addBox(4.4f, 4.5f, 0.0f, 1.6f, 15.0f, 0.0f)
                        .texOffs(0, 8).addBox(-6.6f, 19.0f, 0.0f, 0.5f, 3.0f, 0.0f)    // claws
                        .texOffs(0, 8).addBox(-5.4f, 19.0f, 0.0f, 0.5f, 3.4f, 0.0f)
                        .texOffs(0, 8).addBox(4.9f, 19.0f, 0.0f, 0.5f, 3.4f, 0.0f)
                        .texOffs(0, 8).addBox(6.1f, 19.0f, 0.0f, 0.5f, 3.0f, 0.0f),
                PartPose.ZERO);
        // Ragged wisps along the outline, like black flames licking upward. Fixed seed: everyone
        // sees the same shape (only the flicker is animated).
        PartDefinition wisps = body.addOrReplaceChild("wisps", CubeListBuilder.create(), PartPose.ZERO);
        RandomSource rng = RandomSource.create(0x5311E5L);
        for (int i = 0; i < WISPS; i++) {
            float side = i % 2 == 0 ? -1.0f : 1.0f;
            float y = 3.0f + rng.nextFloat() * 22.0f;
            float x = side * (y < 17.5f ? 3.4f + rng.nextFloat() * 3.0f : 1.0f + rng.nextFloat() * 2.2f);
            float h = 1.6f + rng.nextFloat() * 2.4f;
            wisps.addOrReplaceChild("wisp" + i,
                    CubeListBuilder.create().texOffs(0, 8).addBox(-0.4f, -h, 0.0f, 0.8f, h, 0.0f),
                    PartPose.offsetAndRotation(x, y, 0.0f, 0.0f, 0.0f, side * (0.3f + rng.nextFloat() * 0.5f)));
        }
        return LayerDefinition.create(mesh, 32, 32);
    }

    private static void eye(PartDefinition face, String name, float x, float tilt) {
        face.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.1f, -0.75f, 0.0f, 4.2f, 1.5f, 0.0f)
                        .texOffs(0, 0).addBox(-1.4f, -1.25f, 0.0f, 2.8f, 0.5f, 0.0f)
                        .texOffs(0, 0).addBox(-1.4f, 0.75f, 0.0f, 2.8f, 0.5f, 0.0f),
                PartPose.offsetAndRotation(x, -3.3f, 0.0f, 0.0f, 0.0f, tilt));
    }

    /** The curve of the grin: corners high, middle low (+Y is down). */
    private static float grinY(float x) {
        float k = x / GRIN_HALF_WIDTH;
        return 1.2f + 2.4f * (1.0f - k * k);
    }

    /** Slope of the curve at x, as an angle, so each tooth stands square to the curve. */
    private static float grinAngle(float x) {
        return (float) Math.atan(-4.8f * x / (GRIN_HALF_WIDTH * GRIN_HALF_WIDTH));
    }

    /** A thin flat bar from A to B. */
    private static void segment(PartDefinition parent, String name, float ax, float ay, float bx, float by, float thickness) {
        float dx = bx - ax;
        float dy = by - ay;
        float length = Mth.sqrt(dx * dx + dy * dy);
        parent.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(0, 0).addBox(-length / 2.0f, -thickness / 2.0f, 0.0f, length, thickness, 0.0f),
                PartPose.offsetAndRotation((ax + bx) / 2.0f, (ay + by) / 2.0f, 0.0f, 0.0f, 0.0f, (float) Math.atan2(dy, dx)));
    }

    @Override
    public ModelPart root() {
        return root;
    }

    /** Draw only the glowing face (normal vision) or only the body (the NightShot pass). */
    public void show(boolean showFace, boolean showBody) {
        face.visible = showFace;
        body.visible = showBody;
    }

    @Override
    public void setupAnim(Smiler entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float partial = ageInTicks - entity.tickCount;
        float since = entity.clientModeAge(partial);
        Smiler.Mode mode = entity.mode();
        int seed = entity.getId() * 37;
        float burn = entity.burn();
        float squint = Mth.clamp(burn * 1.3f, 0.0f, 1.0f);

        // Eyes open slowly when it appears; blink now and then; squint as the light burns it.
        float open = mode == Smiler.Mode.APPEAR ? smooth(Mth.clamp((since - 6.0f) / 24.0f, 0.0f, 1.0f)) : 1.0f;
        float blink = blinking(seed, ageInTicks) ? 0.08f : 1.0f;
        float eyeScale = open * blink * (1.0f - 0.75f * squint) * (mode == Smiler.Mode.LUNGE ? 1.3f : 1.0f);
        rightEye.yScale = Math.max(0.02f, eyeScale);
        leftEye.yScale = Math.max(0.02f, eyeScale);

        // Grin: a bold one's is wider; it stretches open when lunging and tightens in the light.
        float width = (entity.isBold() ? 1.25f : 1.0f)
                * (mode == Smiler.Mode.APPEAR ? 0.3f + 0.7f * open : 1.0f)
                * (1.0f - 0.3f * squint)
                * (1.0f + 0.04f * Mth.sin(ageInTicks * 0.1f));
        grin.xScale = width;
        grin.yScale = (entity.isBold() ? 1.15f : 1.0f) * (mode == Smiler.Mode.LUNGE ? 1.7f : 1.0f);

        // In the light it shudders.
        if (mode == Smiler.Mode.FLINCH) {
            face.x += twitch(seed, ageInTicks, 1, 0.6f);
            face.y += twitch(seed + 1, ageInTicks, 1, 0.4f);
            face.zRot += twitch(seed + 2, ageInTicks, 2, 0.12f);
        }

        // Dispel: shrivels to nothing, twisting.
        float scale = 1.0f;
        if (mode == Smiler.Mode.DISPEL) {
            float k = Mth.clamp(since / 20.0f, 0.0f, 1.0f);
            scale = 1.0f - k * k;
            face.zRot += k * 1.4f + twitch(seed + 3, ageInTicks, 1, 0.2f * k);
            face.x += twitch(seed + 4, ageInTicks, 1, 1.2f * k);
        }
        face.xScale = scale;
        face.yScale = scale;
        face.zScale = scale;
        body.xScale = scale;
        body.yScale = scale;

        // NightShot body: the whole shape wavers, the wisps flicker like flames.
        body.x += twitch(seed + 5, ageInTicks, 3, 0.4f);
        for (int i = 0; i < wisps.length; i++) {
            wisps[i].yScale = 0.5f + Math.abs(twitch(seed + 10 + i, ageInTicks, 2, 1.0f));
            wisps[i].zRot += twitch(seed + 40 + i, ageInTicks, 3, 0.25f);
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
