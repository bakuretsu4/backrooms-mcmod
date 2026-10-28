package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.L227Boar;
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
 * The Level 227 boar, built in code: the largest animal in the level, a five-block hill of a pig.
 *
 * <pre>
 *                ▲▲▲▲▲▲▲▲▲▲▲▲ mane of bristles
 *   tusks ╮ ▄▄▄▄█████████████▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄
 *     ╰╮  ▐ head ▐ shoulders ▐   barrel     ▐ rump ▌~ tail
 *  disc▐  ▐  ◦   ▐ (hump)    ▐              ▐      ▌
 *     ╯   ▀▀▀▀▀▀ ▀▀█▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀█▀▀▀▀▀
 *    jaw          ▐█▌ short, massive legs ▐█▌
 * </pre>
 *
 * <p>A huge wedge of a head with a wet snout disc, tiny deep-set eyes, two pairs of tusks (the
 * lower pair long and curving up and out past the snout), a high bristly hump over the
 * shoulders, a tall mane all down the spine, a pale wrinkled belly, short thick legs on split
 * hooves, and a rope of a tail with a tuft.
 *
 * <p>It spends most of its time on its back: {@link #animate} folds the legs, lowers it onto its
 * belly and then rolls the whole body over onto its back (legs in the air, belly up, the head
 * lolling with the snout to the sky). Thrashing, it rocks and twists violently, kicks with all
 * four legs and swings its head and tusks.
 *
 * <p>Model space: pixels, +Y down, the front faces -Z, the ground at y = 24. The texture is
 * 512 x 512 for a 256 x 256 UV layout, made by {@code tools/anomaly_assets/level227_grazers.py}.
 */
public class L227BoarModel extends HierarchicalModel<L227Boar> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Backrooms.id("l227_boar"), "main");

    /** The body's pivot (its middle) standing, on its belly (the belly on the floor) and on its back. */
    private static final float BODY_Y = -4.5f;
    private static final float BODY_Y_BELLY = 9.0f;
    private static final float BODY_Y_BACK = 6.0f;
    /** Extra drop at the middle of the roll, when it is on its side (the half-width is smaller). */
    private static final float ROLL_SIDE_DROP = 1.5f;
    private static final float UPPER = 9.0f;
    private static final float LOWER = 7.0f;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart tail;
    private final ModelPart[] ears = new ModelPart[2];
    private final ModelPart[] upper = new ModelPart[4];
    private final ModelPart[] lower = new ModelPart[4];
    private final ModelPart[] hoof = new ModelPart[4];

    public L227BoarModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.jaw = head.getChild("jaw");
        this.tail = body.getChild("tail");
        this.ears[0] = head.getChild("ear_r");
        this.ears[1] = head.getChild("ear_l");
        String[] legs = {"front_r", "front_l", "rear_r", "rear_l"};
        for (int i = 0; i < 4; i++) {
            upper[i] = body.getChild(legs[i]);
            lower[i] = upper[i].getChild("lower");
            hoof[i] = lower[i].getChild("hoof");
        }
    }

    // ================================================================== building

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rootDef = mesh.getRoot();
        RandomSource rng = RandomSource.create(0x227B0A2L);

        PartDefinition body = rootDef.addOrReplaceChild("body", CubeListBuilder.create()
                        // Barrel. UV (0,0): 26 x 24 x 38.
                        .texOffs(0, 0).addBox(-13.0f, -12.0f, -20.0f, 26, 24, 38)
                        // Shoulders: the high hump. UV (128,0): 28 x 27 x 16.
                        .texOffs(128, 0).addBox(-14.0f, -15.0f, -28.0f, 28, 27, 16)
                        // Rump, lower than the hump. UV (128,46): 24 x 22 x 10.
                        .texOffs(128, 46).addBox(-12.0f, -11.0f, 14.0f, 24, 22, 10)
                        // The belly, sagging. UV (0,66): 22 x 3 x 28.
                        .texOffs(0, 66).addBox(-11.0f, 12.0f, -16.0f, 22, 3, 28)
                        // The crest under the mane. UV (0,100): 7 x 6 x 30.
                        .texOffs(0, 100).addBox(-3.5f, -18.0f, -28.0f, 7, 6, 30),
                PartPose.offset(0.0f, BODY_Y, 0.0f));

        head(body, rng);

        // Mane: tall bristles all down the crest, longest over the shoulders.
        for (int i = 0; i < 34; i++) {
            float z = -27.5f + i * 0.95f + rng.nextFloat() * 0.5f;
            float x = (rng.nextFloat() - 0.5f) * 6.0f;
            float tall = z < -10.0f ? 8.0f + rng.nextFloat() * 3.0f : 5.0f + rng.nextFloat() * 3.0f - (z + 10.0f) * 0.1f;
            bristle(body, "mane" + i, rng, x, -17.6f, z, x * 1.3f, -17.6f - tall, z + 2.5f + rng.nextFloat() * 1.5f, 1.6f);
        }
        // Coarse bristles scattered down the flanks and over the rump.
        for (int i = 0; i < 26; i++) {
            int side = (i & 1) == 0 ? -1 : 1;
            float z = -26.0f + rng.nextFloat() * 48.0f;
            float y = -10.0f + rng.nextFloat() * 18.0f;
            float x = side * (z < -12.0f ? 14.1f : z > 14.0f ? 12.1f : 13.1f);
            bristle(body, "flank" + i, rng, x, y, z, x + side * 1.0f, y + 2.5f + rng.nextFloat() * 2.5f, z + 1.5f, 1.2f);
        }
        // Moss grown over the rump and back, and a couple of ferns: it lies still a long time.
        float[][] pads = {{-7.0f, -11.7f, 4.0f, 9, 9}, {6.0f, -11.7f, -6.0f, 8, 12}, {0.0f, -10.7f, 18.0f, 14, 7}};
        for (int i = 0; i < pads.length; i++) {
            float[] p = pads[i];
            body.addOrReplaceChild("moss" + i, CubeListBuilder.create()
                            .texOffs(0, 180).addBox(-p[3] / 2.0f, -0.8f, -p[4] / 2.0f, p[3], 0.8f, p[4]),
                    PartPose.offsetAndRotation(p[0], p[1], p[2], 0.0f, (rng.nextFloat() - 0.5f) * 0.4f, 0.0f));
        }
        fern(body, "fern0", rng, -7.0f, -11.9f, 8.0f, 5);
        fern(body, "fern1", rng, 6.0f, -10.9f, 19.0f, 4);

        // Legs. Upper UV (0|34,156): 8 x 10.5 x 8; lower UV (68|94,156): 6 x 7.5 x 6; hoof UV (136,136): 6.6 x 2.5 x 6.6.
        for (int side = -1; side <= 1; side += 2) {
            String s = side < 0 ? "_r" : "_l";
            leg(body, "front" + s, 0, side * 10.0f, 10.0f, -20.0f);
            leg(body, "rear" + s, 1, side * 9.5f, 10.0f, 15.0f);
        }

        // The tail: a rope with a tuft, hanging off the rump. UV rope (166,136) 1.6 x 9 x 1.6; tuft (180,136) 3 x 5 x 3.
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                        .texOffs(166, 136).addBox(-0.8f, 0.0f, -0.8f, 1.6f, 9, 1.6f),
                PartPose.offsetAndRotation(0.0f, -8.0f, 24.0f, 0.3f, 0.0f, 0.0f));
        tail.addOrReplaceChild("tuft", CubeListBuilder.create()
                        .texOffs(180, 136).addBox(-1.5f, 0.0f, -1.5f, 3, 5, 3),
                PartPose.offset(0.0f, 8.5f, 0.0f));
        return LayerDefinition.create(mesh, 256, 256);
    }

    /** The head: skull, snout and disc, jaw, cheeks, ears, eyes, four tusks and a bristly beard. */
    private static void head(PartDefinition body, RandomSource rng) {
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                        // Skull. UV (76,100): 20 x 18 x 12.
                        .texOffs(76, 100).addBox(-10.0f, -9.0f, -12.0f, 20, 18, 12)
                        // The face, narrower: the head tapers like a wedge. UV (140,100): 16 x 14.5 x 8.
                        .texOffs(140, 100).addBox(-8.0f, -6.0f, -19.0f, 16, 14.5f, 8)
                        // Snout. UV (188,100): 12 x 10 x 10.
                        .texOffs(188, 100).addBox(-6.0f, -2.0f, -29.0f, 12, 10, 10)
                        // The snout disc, flat and wet. UV (232,100): 10 x 8 x 1.5.
                        .texOffs(232, 100).addBox(-5.0f, -1.5f, -30.5f, 10, 8, 1.5f)
                        // Inside of the mouth, seen when it squeals. UV (206,120): 9 x 2 x 14.
                        .texOffs(206, 120).addBox(-4.5f, 6.0f, -27.0f, 9, 2, 14),
                PartPose.offsetAndRotation(0.0f, -6.0f, -27.0f, 0.25f, 0.0f, 0.0f));
        // Warty cheeks. UV (52,136): 3 x 9 x 10.
        for (int side = -1; side <= 1; side += 2) {
            head.addOrReplaceChild(side < 0 ? "cheek_r" : "cheek_l", CubeListBuilder.create()
                            .texOffs(52, 136).addBox(-1.5f, -4.5f, -5.0f, 3, 9, 10),
                    PartPose.offsetAndRotation(side * 9.0f, 3.0f, -10.0f, 0.0f, side * -0.12f, 0.0f));
            // Ears: pointed, flopping forward. UV (80,136): 5 x 6 x 1.
            head.addOrReplaceChild(side < 0 ? "ear_r" : "ear_l", CubeListBuilder.create()
                            .texOffs(80, 136).addBox(-2.5f, -6.0f, -0.5f, 5, 6, 1),
                    PartPose.offsetAndRotation(side * 7.5f, -8.5f, -3.0f, -0.55f, 0.0f, side * 0.45f));
            // Small, deep-set eyes. UV (94,136): 0.4 x 1.6 x 1.6.
            head.addOrReplaceChild(side < 0 ? "eye_r" : "eye_l", CubeListBuilder.create()
                            .texOffs(94, 136).addBox(side < 0 ? -0.4f : 0.0f, -0.8f, -0.8f, 0.4f, 1.6f, 1.6f),
                    PartPose.offset(side * 10.0f, -3.5f, -9.0f));
            // Upper tusks: out of the sides of the snout, curling up and back. UV (100,136).
            tusk(head, "tusk_u" + (side < 0 ? "_r" : "_l"), side, 6.2f, 5.0f, -25.0f,
                    new float[][] {{1.5f, -1.5f, -1.5f}, {1.0f, -3.0f, 0.5f}, {0.3f, -2.5f, 2.0f}}, 2.2f);
        }
        // Jaw, hinged under the skull. UV (0,136): 11 x 4 x 15.
        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create()
                        .texOffs(0, 136).addBox(-5.5f, 0.0f, -15.0f, 11, 4, 15),
                PartPose.offset(0.0f, 7.5f, -12.0f));
        // Lower tusks: the big ones, from the jaw, sweeping up and out past the snout.
        for (int side = -1; side <= 1; side += 2) {
            tusk(jaw, "tusk_l" + (side < 0 ? "_r" : "_l"), side, 5.0f, 0.5f, -13.0f,
                    new float[][] {{1.5f, -3.0f, -2.5f}, {2.5f, -4.0f, -1.5f}, {2.0f, -4.0f, 1.5f}, {0.5f, -3.0f, 3.0f}}, 2.8f);
        }
        // A beard of bristles under the jaw and along the jowls.
        for (int i = 0; i < 12; i++) {
            float x = -5.5f + i;
            bristle(jaw, "beard" + i, rng, x, 4.0f, -6.0f - rng.nextFloat() * 5.0f, x * 1.1f, 8.0f + rng.nextFloat() * 2.0f,
                    -5.0f - rng.nextFloat() * 4.0f, 1.3f);
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 6; i++) {
                float y = -6.0f + i * 2.5f;
                float x = side * 10.2f;
                bristle(head, "jowl" + (side < 0 ? "r" : "l") + i, rng, x, y, -2.0f - rng.nextFloat() * 3.0f,
                        x + side * 2.0f, y + 3.5f, 1.5f, 1.4f);
            }
        }
    }

    /**
     * A curved tusk from (x, y, z) in a chain of pieces, each step {dx, dy, dz} (dx mirrored to the
     * side), tapering from {@code thickness}. UV (100,136): ivory.
     */
    private static void tusk(PartDefinition parent, String name, int side, float x, float y, float z, float[][] steps, float thickness) {
        float ax = side * x;
        float ay = y;
        float az = z;
        for (int i = 0; i < steps.length; i++) {
            float bx = ax + side * steps[i][0];
            float by = ay + steps[i][1];
            float bz = az + steps[i][2];
            float th = thickness * (1.0f - 0.18f * i);
            segment(parent, name + "_" + i, 100, 136, ax, ay, az, bx, by, bz, th);
            ax = bx;
            ay = by;
            az = bz;
        }
    }

    private static void leg(PartDefinition body, String name, int pair, float x, float y, float z) {
        PartDefinition up = body.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(pair * 34, 156).addBox(-4.0f, -1.5f, -4.0f, 8, UPPER + 1.5f, 8),
                PartPose.offset(x, y, z));
        PartDefinition low = up.addOrReplaceChild("lower", CubeListBuilder.create()
                        .texOffs(68 + pair * 26, 156).addBox(-3.0f, -0.5f, -3.0f, 6, LOWER + 0.5f, 6),
                PartPose.offset(0.0f, UPPER, 0.0f));
        // Split hoof with two dew claws behind.
        PartDefinition h = low.addOrReplaceChild("hoof", CubeListBuilder.create()
                        .texOffs(136, 136).addBox(-3.3f, 0.0f, -3.8f, 6.6f, 2.5f, 6.6f),
                PartPose.offset(0.0f, LOWER, 0.0f));
        for (int side = -1; side <= 1; side += 2) {
            segment(h, "dew" + (side < 0 ? "r" : "l"), 136, 136, side * 2.0f, 0.2f, 2.6f, side * 2.2f, 1.6f, 3.8f, 1.2f);
        }
    }

    /** One coarse bristle, in two tapering pieces. UV (120..250, 156..196). */
    private static void bristle(PartDefinition parent, String name, RandomSource rng,
                                float rx, float ry, float rz, float tx, float ty, float tz, float thickness) {
        int u = 120 + rng.nextInt(130);
        int v = 156 + rng.nextInt(28);
        float mx = Mth.lerp(0.5f, rx, tx) + (rng.nextFloat() - 0.5f) * 0.6f;
        float my = Mth.lerp(0.5f, ry, ty);
        float mz = Mth.lerp(0.5f, rz, tz) + (rng.nextFloat() - 0.5f) * 0.6f;
        segment(parent, name + "_a", u, v, rx, ry, rz, mx, my, mz, thickness);
        segment(parent, name + "_b", u, v, mx, my, mz, tx, ty, tz, thickness * 0.65f);
    }

    /** A small fern: flat fronds arching out from a crown. UV (56,180). */
    private static void fern(PartDefinition parent, String name, RandomSource rng, float x, float y, float z, int fronds) {
        PartDefinition crown = parent.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offset(x, y, z));
        for (int f = 0; f < fronds; f++) {
            float yaw = f * Mth.TWO_PI / fronds + rng.nextFloat() * 0.5f;
            float length = 5.0f + rng.nextFloat() * 2.5f;
            crown.addOrReplaceChild("frond" + f, CubeListBuilder.create()
                            .texOffs(56, 180).addBox(-1.0f, -0.1f, -length, 2.0f, 0.2f, length),
                    PartPose.rotation(-0.5f - rng.nextFloat() * 0.3f, yaw, 0.0f));
        }
    }

    /** One straight square rod from A to B (the same trick as {@code HoundModel}). */
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
    public void setupAnim(L227Boar entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float partial = ageInTicks - entity.tickCount;
        animate(ageInTicks + (entity.getId() & 127), limbSwing, limbSwingAmount, entity.lie(partial), entity.roll(partial),
                entity.thrash(partial), entity.rootHead(partial), entity.rollSide(), netHeadYaw, headPitch);
    }

    /**
     * Poses the model from plain numbers (also used by the art tools' contact sheets).
     *
     * @param lie     0 standing, 1 legs folded, down on its belly
     * @param roll    0 on its belly, 1 rolled right over onto its back
     * @param thrash  0..1 how violently it is thrashing
     * @param rootHd  0..1 head down, rooting in the ground with its tusks
     * @param side    +1 or -1: which way it rolls over
     */
    public void animate(float t, float walk, float walkAmount, float lie, float roll, float thrash, float rootHd, int side,
                        float headYawDeg, float headPitchDeg) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float stand = 1.0f - lie;
        float amount = Mth.clamp(walkAmount * 1.8f, 0.0f, 1.0f) * stand;
        float cycles = walk * 0.11f;
        float phase = cycles - Mth.floor(cycles);
        float onBack = Mth.clamp(roll, 0.0f, 1.0f);

        // --- Body: down onto the belly, then over onto the back ---------------------------------
        body.y = Mth.lerp(lie, BODY_Y, Mth.lerp(onBack, BODY_Y_BELLY, BODY_Y_BACK)) + Mth.sin(onBack * Mth.PI) * ROLL_SIDE_DROP;
        body.zRot = side * onBack * Mth.PI;
        // Breathing: the belly heaves, slow and deep on its back (it snores).
        float breath = Mth.sin(t * Mth.lerp(onBack, 0.12f, 0.06f));
        body.y += breath * Mth.lerp(onBack, 0.2f, 0.5f) * (onBack > 0.5f ? -1.0f : 1.0f);
        // Walking: heavy side-to-side roll.
        body.zRot += Mth.sin(Mth.TWO_PI * phase) * 0.04f * amount;
        body.y -= Math.abs(Mth.sin(Mth.TWO_PI * phase)) * 0.7f * amount;
        // Thrashing: rocking hard from side to side, bucking, twisting.
        float th = thrash;
        body.zRot += (Mth.sin(t * 0.95f) * 0.42f + Mth.sin(t * 2.1f) * 0.12f) * th;
        body.xRot += Mth.sin(t * 1.3f + 1.0f) * 0.14f * th;
        body.y -= Math.abs(Mth.sin(t * 0.95f)) * 2.5f * th * (1.0f - onBack);

        // --- Legs ------------------------------------------------------------------------------
        float[] gait = {0.0f, 0.5f, 0.5f, 0.0f};
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            int ls = (i % 2 == 0) ? -1 : 1;
            float p = phase + gait[i];
            p -= Mth.floor(p);
            float swing = Mth.sin(Mth.TWO_PI * p) * 0.55f * amount;
            float lift = Math.max(0.0f, -Mth.cos(Mth.TWO_PI * p)) * amount;
            // Standing / walking.
            float ux = swing;
            float lx = lift * 0.7f;
            float uz = 0.0f;
            // Folded under it on the belly: front legs tucked back, hind legs tucked forward.
            ux = Mth.lerp(lie, ux, front ? 1.45f : -1.45f);
            lx = Mth.lerp(lie, lx, front ? -2.2f : 2.2f);
            // On its back the legs come up into the air, a little apart and bent, and twitch.
            float twitch = Mth.sin(t * 0.21f + i * 1.9f) > 0.9f ? Mth.sin(t * 1.4f + i) * 0.25f : 0.0f;
            ux = Mth.lerp(onBack, ux, (front ? -0.25f : 0.3f) + twitch);
            lx = Mth.lerp(onBack, lx, (front ? 0.55f : -0.5f) + twitch * 0.5f);
            uz = Mth.lerp(onBack, uz, ls * -0.32f);
            // Kicking wildly while it thrashes.
            ux += Mth.sin(t * 1.9f + i * 1.7f) * 0.75f * th;
            lx += Math.max(0.0f, Mth.sin(t * 2.3f + i * 2.1f)) * 0.9f * th;
            uz += Mth.sin(t * 1.5f + i) * 0.2f * th;
            upper[i].xRot = ux - (front ? 0.0f : 0.0f);
            upper[i].zRot = uz;
            lower[i].xRot = lx;
            hoof[i].xRot = -(ux + lx) * stand * (1.0f - onBack);
        }

        // --- Head -------------------------------------------------------------------------------
        float yaw = headYawDeg * Mth.DEG_TO_RAD * stand;
        float pitch = headPitchDeg * Mth.DEG_TO_RAD * stand;
        head.yRot += yaw * 0.4f;
        head.xRot += pitch * 0.3f;
        // Rooting: head down, snout and tusks ploughing the ground, swinging slowly.
        head.xRot += rootHd * 0.55f + Mth.sin(t * 0.25f) * 0.08f * rootHd;
        head.yRot += Mth.sin(t * 0.07f) * 0.3f * rootHd;
        // Walking: the head nods with the stride.
        head.xRot += Mth.sin(Mth.TWO_PI * phase * 2.0f) * 0.05f * amount;
        // On its back: the head lolls back, snout to the sky, and falls to one side.
        head.xRot += onBack * 0.95f;
        head.zRot += onBack * side * 0.35f;
        // Thrashing: the head whips from side to side, tusks swinging.
        head.yRot += Mth.sin(t * 1.15f) * 0.55f * th;
        head.xRot += Mth.sin(t * 1.7f + 0.5f) * 0.25f * th;
        // The jaw: slack when asleep on its back, wide open when it squeals.
        jaw.xRot = onBack * 0.18f + th * (0.55f + Mth.sin(t * 2.4f) * 0.15f) + rootHd * Math.max(0.0f, Mth.sin(t * 0.5f)) * 0.12f;
        // Ears flop and twitch.
        for (int k = 0; k < 2; k++) {
            ears[k].xRot += Mth.sin(t * 0.09f + k * 2.0f) * 0.06f + th * Mth.sin(t * 2.0f + k) * 0.3f;
        }
        // Tail: a lazy swing; a frantic whip while thrashing.
        tail.zRot += Mth.sin(t * 0.13f) * 0.25f + Mth.sin(t * 1.9f) * 0.6f * th;
        tail.xRot += onBack * -0.4f;
    }
}
