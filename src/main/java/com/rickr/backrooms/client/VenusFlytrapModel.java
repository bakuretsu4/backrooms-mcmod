package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.VenusFlytrap;
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
 * The giant Venus flytrap, built in code. Units are model pixels (16 to a block); like every
 * entity model, +y points DOWN and the ground is at y = 24.
 *
 * <p>Parts:
 * <ul>
 *   <li>The rosette: a low crown with seven long leaves lying flat round it.</li>
 *   <li>The stalk: six segments from the crown up to the trap, stretched and bent every frame to
 *       follow the trap's height (it shoots up five blocks when it lifts you).</li>
 *   <li>The trap (head): two lobes hinged along the midrib (the X axis), 3 blocks long. Each lobe
 *       is a curved shell of three plates (A from the hinge tilting outward, B upright, C curling
 *       back in), so shut, the lobes make a hollow about 2 blocks tall: room for a person
 *       standing inside. The plates are boxes, but the texture cuts each lobe to a rounded,
 *       slightly ragged half-oval (its edges are see-through), so nothing about it is square.
 *       Short, thin teeth stand along that rounded rim, leaning inward; three trigger hairs and
 *       a few grass tufts grow on each lobe's inner face (the tufts only while it lies open).</li>
 * </ul>
 * One lobe is built facing +z (bulging outward to +z, the hollow on its -z side); the other is
 * the same lobe turned half round. Opening swings both outward about the hinge: lying open they
 * make a shallow dish about 3 by 4.7 blocks, almost flat in the grass and painted like the forest
 * floor (moss, leaf litter) so you hardly see it.
 *
 * <p>Textures (client/VenusFlytrapRenderer picks one): venus_flytrap.png, the camouflage worn while
 * it lies waiting, and venus_flytrap_fed.png, the red inside shown from the moment it snaps; both
 * 256 x 256 for this 128 x 128 UV layout (two texels per model pixel), drawn by
 * tools/anomaly_assets/level227_flora.py, which must use the same {@link #RIM_REACH} and
 * {@link #LOBE_HALF_WIDTH} for the rounded outline. The render type does not cull back faces, so
 * the captive sees the inside of the lobes all round.
 */
public class VenusFlytrapModel extends HierarchicalModel<VenusFlytrap> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Backrooms.id("venus_flytrap"), "main");

    private static final int STALK_SEGMENTS = 6;
    /** Each lobe plate is this long (pixels); A tilts out and C back in by {@link #CURL}. */
    private static final float PLATE = 13.0f;
    private static final float CURL = 40.0f * Mth.DEG_TO_RAD;
    /** Open, the lobes flatten out: the plates keep only this much of their curl. */
    private static final float OPEN_CURL = 0.3f;
    /** The lobes' swing about the hinge when lying fully open (outward, toward the ground). */
    private static final float OPEN_ANGLE = 85.0f * Mth.DEG_TO_RAD;
    /** The lobe's outline: half its width at the hinge, and how far along the lobe the rim reaches
     *  at the middle (as a fraction of the three plates). Along the lobe at fraction p the half
     *  width is LOBE_HALF_WIDTH * sqrt(1 - (p / RIM_REACH)^2): a half oval. */
    private static final float LOBE_HALF_WIDTH = 24.0f;
    private static final float RIM_REACH = 0.97f;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart lobeRight;
    private final ModelPart lobeLeft;
    private final ModelPart[] stalk = new ModelPart[STALK_SEGMENTS];
    /** The three plates (A, B, C) of each lobe, right then left. */
    private final ModelPart[] plates = new ModelPart[6];
    /** The grass tufts on each lobe's inner face (shown only while it lies open). */
    private final ModelPart[] tufts = new ModelPart[4];

    public VenusFlytrapModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.lobeRight = head.getChild("lobe_right");
        this.lobeLeft = head.getChild("lobe_left");
        for (int i = 0; i < STALK_SEGMENTS; i++) {
            stalk[i] = root.getChild("stalk" + i);
        }
        ModelPart[] lobes = {lobeRight, lobeLeft};
        for (int i = 0; i < 2; i++) {
            plates[i * 3] = lobes[i].getChild("a");
            plates[i * 3 + 1] = plates[i * 3].getChild("b");
            plates[i * 3 + 2] = plates[i * 3 + 1].getChild("c");
            tufts[i * 2] = plates[i * 3].getChild("tufts");
            tufts[i * 2 + 1] = plates[i * 3 + 1].getChild("tufts");
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Rosette: the crown, and seven leaves (blade 10 wide, 30 long) lying round it, tips raised a little.
        PartDefinition base = root.addOrReplaceChild("base",
                CubeListBuilder.create().texOffs(30, 46).addBox(-6.0f, -3.0f, -6.0f, 12, 3, 12),
                PartPose.offset(0.0f, 24.0f, 0.0f));
        for (int i = 0; i < 7; i++) {
            float yaw = i * Mth.TWO_PI / 7.0f + (i % 2) * 0.2f;
            base.addOrReplaceChild("leaf" + i,
                    CubeListBuilder.create().texOffs(0, 70).addBox(-5.0f, -1.0f, 0.0f, 10, 1, 30),
                    PartPose.offsetAndRotation(0.0f, -0.4f, 0.0f, 0.10f + (i % 3) * 0.04f, yaw, 0.0f));
        }

        // Stalk segments: set every frame (position, bend and length) by setupAnim.
        for (int i = 0; i < STALK_SEGMENTS; i++) {
            root.addOrReplaceChild("stalk" + i,
                    CubeListBuilder.create().texOffs(0, 46).addBox(-3.5f, -16.0f, -3.5f, 7, 16, 7),
                    PartPose.ZERO);
        }

        // The trap. Both lobes hang off the hinge at the head's origin.
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0.0f, 17.6f, 0.0f));
        lobe(head.addOrReplaceChild("lobe_right", CubeListBuilder.create(), PartPose.ZERO), 0.0f);
        lobe(head.addOrReplaceChild("lobe_left", CubeListBuilder.create(), PartPose.rotation(0.0f, Mth.PI, 0.0f)), 1.4f);
        return LayerDefinition.create(mesh, 128, 128);
    }

    /**
     * One lobe, bulging toward +z: three plates 48 wide (cut round by the texture), teeth along
     * the rounded rim, trigger hairs and grass tufts on the inside (-z). {@code toothShift}
     * staggers the teeth of the two lobes so they interlock.
     */
    private static void lobe(PartDefinition lobe, float toothShift) {
        CubeListBuilder[] plate = new CubeListBuilder[3];
        for (int k = 0; k < 3; k++) {
            plate[k] = CubeListBuilder.create().texOffs(0, 15 * k).addBox(-24.0f, -PLATE, -1.0f, 48, 13, 2);
        }
        // Trigger hairs on B's inner face.
        plate[1].texOffs(120, 12).addBox(-10.0f, -8.0f, -3.0f, 0.5f, 0.5f, 2)
                .texOffs(120, 12).addBox(2.0f, -5.0f, -3.0f, 0.5f, 0.5f, 2)
                .texOffs(120, 12).addBox(11.0f, -9.0f, -3.0f, 0.5f, 0.5f, 2);
        // Teeth: thin and short, each standing on the rounded rim where it crosses its plate, a
        // little bent inward (the upper piece sits further toward the hollow).
        float length = 3 * PLATE;
        for (int k = 0; k < 13; k++) {
            float x = -21.6f + toothShift + k * 3.6f;
            float reach = RIM_REACH * Mth.sqrt(Math.max(0.0f, 1.0f - (x / LOBE_HALF_WIDTH) * (x / LOBE_HALF_WIDTH)));
            float s = reach * length;
            int on = Math.min(2, (int) (s / PLATE));
            float y = -(s - on * PLATE);
            plate[on].texOffs(120, 0).addBox(x - 0.25f, y - 3.0f, -0.6f, 0.5f, 3, 0.5f)
                    .texOffs(124, 0).addBox(x - 0.25f, y - 5.5f, -1.2f, 0.5f, 2.5f, 0.5f);
        }
        PartDefinition a = lobe.addOrReplaceChild("a", plate[0], PartPose.rotation(-CURL, 0.0f, 0.0f));
        PartDefinition b = a.addOrReplaceChild("b", plate[1], PartPose.offsetAndRotation(0.0f, -PLATE, 0.0f, CURL, 0.0f, 0.0f));
        b.addOrReplaceChild("c", plate[2], PartPose.offsetAndRotation(0.0f, -PLATE, 0.0f, CURL, 0.0f, 0.0f));
        a.addOrReplaceChild("tufts", tufts(-9.0f, -6.0f, 7.0f, -9.0f), PartPose.ZERO);
        b.addOrReplaceChild("tufts", tufts(-4.0f, -5.0f, 10.0f, -8.0f), PartPose.ZERO);
    }

    /** Two grass tufts on a plate's inner face: each two crossed flat planes 5 px high. */
    private static CubeListBuilder tufts(float x1, float y1, float x2, float y2) {
        CubeListBuilder c = CubeListBuilder.create();
        for (float[] at : new float[][] {{x1, y1}, {x2, y2}}) {
            c.texOffs(0, 104).addBox(at[0] - 2.5f, at[1], -6.0f, 5, 0, 5)
                    .texOffs(20, 104).addBox(at[0], at[1] - 2.5f, -6.0f, 0, 5, 5);
        }
        return c;
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(VenusFlytrap trap, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float partial = ageInTicks - trap.tickCount;
        float hinge = trap.hingeHeight(partial) * 16.0f;
        float open = trap.openness(partial);

        // A slow sway once it stands up (more the higher it is), plus a jerk when someone struggles.
        float rise = Mth.clamp((hinge - VenusFlytrap.HINGE_REST * 16.0f) / ((VenusFlytrap.HINGE_TOP - VenusFlytrap.HINGE_REST) * 16.0f), 0.0f, 1.0f);
        float shake = trap.shakeTicks() > 0 ? (trap.shakeTicks() - partial) / 6.0f : 0.0f;
        float bendX = rise * (4.0f * Mth.sin(ageInTicks * 0.045f) + 2.5f * shake * Mth.sin(ageInTicks * 2.7f));
        float bendZ = rise * (3.0f * Mth.sin(ageInTicks * 0.037f + 1.3f) + 2.0f * shake * Mth.cos(ageInTicks * 3.1f));

        // The stalk: points along a curve from the crown (y 24) up to the hinge, bowed sideways by
        // the sway; each segment is stretched to reach the next point.
        float lastPitch = 0.0f;
        float lastRoll = 0.0f;
        for (int i = 0; i < STALK_SEGMENTS; i++) {
            float s0 = i / (float) STALK_SEGMENTS;
            float s1 = (i + 1) / (float) STALK_SEGMENTS;
            float x0 = bendX * Mth.sin(Mth.PI * s0);
            float z0 = bendZ * Mth.sin(Mth.PI * s0);
            float x1 = bendX * Mth.sin(Mth.PI * s1);
            float z1 = bendZ * Mth.sin(Mth.PI * s1);
            float dx = x1 - x0;
            float dy = -hinge * (s1 - s0);
            float dz = z1 - z0;
            float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            ModelPart part = stalk[i];
            part.visible = hinge > 7.5f;
            part.setPos(x0, 24.0f - hinge * s0, z0);
            // Turns the segment's own "up" (0, -1, 0) onto the direction to the next point: the
            // pitch about x first, then the roll about z (the order ModelPart applies them in).
            float nx = dx / length;
            float ny = dy / length;
            float nz = dz / length;
            part.xRot = (float) -Math.asin(Mth.clamp(nz, -1.0f, 1.0f));
            part.yRot = 0.0f;
            part.zRot = (float) Mth.atan2(nx, -ny);
            part.yScale = length / 16.0f;
            // Thinner toward the top.
            part.xScale = 1.0f - 0.25f * s0;
            part.zScale = 1.0f - 0.25f * s0;
            lastPitch = part.xRot;
            lastRoll = part.zRot;
        }

        // The trap sits on the stalk's tip and leans with it.
        head.setPos(0.0f, 24.0f - hinge, 0.0f);
        head.xRot = lastPitch * 0.8f;
        head.zRot = lastRoll * 0.8f + shake * 0.08f * Mth.sin(ageInTicks * 2.3f);
        head.yRot = rise * 0.15f * Mth.sin(ageInTicks * 0.021f);

        float swing = -OPEN_ANGLE * open;
        float jerk = shake * 0.06f * Mth.sin(ageInTicks * 3.3f);
        lobeRight.xRot = swing - Math.abs(jerk);
        lobeLeft.xRot = swing - Math.abs(jerk);
        // Shut, the plates curl into a hollow; open, they flatten into a shallow dish in the grass.
        float curl = CURL * (1.0f - (1.0f - OPEN_CURL) * open);
        for (int i = 0; i < 2; i++) {
            plates[i * 3].xRot = -curl;
            plates[i * 3 + 1].xRot = curl;
            plates[i * 3 + 2].xRot = curl;
        }
        // The grass on the lobes is part of the disguise: shut, it would only poke the captive.
        for (ModelPart tuft : tufts) {
            tuft.visible = open > 0.6f;
        }
    }
}
