package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rickr.backrooms.Backrooms;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/**
 * Level 94's parked vehicles, built in code from boxes like {@link SmilerModel}: a rounded 1930s
 * sedan and a boxy white 1940s milk van. Drawn by {@link VehicleRenderer}.
 *
 * <p>Model space: units are pixels (1/16 block), the vehicle's middle is x = 0, its front points
 * to -z, and (as in every entity model) +y points DOWN. To keep the numbers readable the boxes
 * below are given as heights UP from the ground ({@code h0..h1}); {@link Parts#box} turns them
 * into model y.
 *
 * <p>Textures: every box takes its colour from a "swatch", a patch of one material in the texture
 * (paint, chrome, tyre rubber, glass...). A box's texture is unfolded from its corner in the usual
 * Minecraft way, so all boxes of one material can share one swatch as long as it is big enough
 * for the biggest of them. The few boxes with a picture on them (wheels, headlight lenses, the
 * van's lettered body and sliding doors) have a swatch of their own drawn to their exact size by
 * {@code tex_l94_vehicle*} in tools/gen_assets.py. Keep the swatch corners in sync with it.
 */
public class VehicleModel extends Model {
    public static final ModelLayerLocation CAR_LAYER = new ModelLayerLocation(Backrooms.id("l94_car"), "main");
    public static final ModelLayerLocation VAN_LAYER = new ModelLayerLocation(Backrooms.id("l94_milk_van"), "main");

    /** Size of both texture atlases in texture units (the PNGs are drawn at twice this). */
    private static final int TEX_SIZE = 256;

    /** The top-left corner of one material's patch in the texture. */
    private record Swatch(int u, int v) {}

    // Car atlas (l94_vehicle_red/black/cream.png share it; only the colours differ).
    private static final Swatch CAR_PAINT = new Swatch(0, 0);
    private static final Swatch CAR_FENDER = new Swatch(0, 80);
    private static final Swatch CAR_CHROME = new Swatch(72, 80);
    private static final Swatch CAR_GRILLE = new Swatch(152, 80);
    private static final Swatch CAR_TYRE = new Swatch(184, 80);
    private static final Swatch CAR_WHEEL = new Swatch(216, 80);
    private static final Swatch CAR_GLASS = new Swatch(0, 112);
    private static final Swatch CAR_LENS = new Swatch(64, 112);
    private static final Swatch CAR_TAIL = new Swatch(80, 112);

    // Milk van atlas (l94_vehicle_van.png).
    private static final Swatch VAN_BOX = new Swatch(0, 0);
    private static final Swatch VAN_PAINT = new Swatch(0, 96);
    private static final Swatch VAN_CHROME = new Swatch(160, 0);
    private static final Swatch VAN_TYRE = new Swatch(160, 16);
    private static final Swatch VAN_WHEEL = new Swatch(192, 16);
    private static final Swatch VAN_LENS = new Swatch(224, 16);
    private static final Swatch VAN_TAIL = new Swatch(240, 16);
    private static final Swatch VAN_GRILLE = new Swatch(160, 48);
    private static final Swatch VAN_DOOR = new Swatch(208, 48);
    private static final Swatch VAN_GLASS = new Swatch(0, 162);

    private final ModelPart root;

    public VehicleModel(ModelPart root) {
        // Cutout: the round wheels and headlight lenses are square planes with see-through corners.
        super(RenderType::entityCutout);
        this.root = root;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }

    /**
     * A rounded 1930s sedan, 2 blocks wide, about 4.5 long and 1.8 tall: a narrow body with a long
     * hood and a sloping fastback, separate fenders over the wheels joined by running boards, round
     * headlights beside the grille and a spare wheel on the back.
     */
    public static LayerDefinition createCarLayer() {
        MeshDefinition mesh = new MeshDefinition();
        Parts p = new Parts(mesh.getRoot());

        // Body: the low tub, the long hood in front and the cabin on top.
        p.box(CAR_PAINT, -11, 6, -12, 11, 17, 31);
        p.box(CAR_PAINT, -8.5f, 8, -33, 8.5f, 17, -12);
        p.box(CAR_PAINT, -7.5f, 17, -32, 7.5f, 18.5f, -12);   // rounded hood top
        p.box(CAR_PAINT, -10.5f, 17, -12, 10.5f, 27, 12);
        p.box(CAR_PAINT, -10, 27, -10, 10, 29, 11);           // roof, inset so the top looks rounded
        p.box(CAR_PAINT, -9, 9, 31, 9, 17, 33);               // rounded tail
        // Fastback: one slab sloping from the back of the roof (13 back, 12 down) to the trunk lid,
        // with a small rear window on it.
        PartDefinition slope = p.slope(CAR_PAINT, -10.5f, 10.5f, 29, 11, Mth.sqrt(13 * 13 + 12 * 12), 10, -(float) Math.atan2(12, 13));
        slope.addOrReplaceChild("rear_window", CubeListBuilder.create().texOffs(CAR_GLASS.u, CAR_GLASS.v)
                .addBox(-6, -0.3f, 2.5f, 12, 0.3f, 6), PartPose.ZERO);

        // Grille, bumpers, hood ornament, door handles.
        p.box(CAR_GRILLE, -6, 7, -34, 6, 17, -33);
        p.box(CAR_CHROME, -15, 4, -36, 15, 6.5f, -34);
        p.box(CAR_CHROME, -14, 4, 33, 14, 6, 35);
        p.box(CAR_CHROME, -0.5f, 18.5f, -33, 0.5f, 20, -31);
        p.pair(CAR_CHROME, 11, 15.5f, -9, 11.5f, 16, -7);
        p.pair(CAR_CHROME, 11, 15.5f, 3, 11.5f, 16, 5);

        // Windows: thin dark glass panes standing just proud of the cabin.
        p.box(CAR_GLASS, -9, 18.5f, -12.3f, 9, 26, -12);
        p.pair(CAR_GLASS, 10.5f, 19, -10, 10.8f, 25.5f, 0);
        p.pair(CAR_GLASS, 10.5f, 19, 2, 10.8f, 25.5f, 12);

        // Fenders (each an arch over its wheel with a rounded nose or tail) and the running boards.
        p.pair(CAR_FENDER, 10, 9, -28, 15, 13.5f, -15);
        p.pair(CAR_FENDER, 10.5f, 7, -31, 14.5f, 12, -28);
        p.pair(CAR_FENDER, 10, 9, 12, 15, 13.5f, 26);
        p.pair(CAR_FENDER, 10.5f, 7, 26, 14.5f, 12, 29);
        p.pair(CAR_FENDER, 10.5f, 6, -15, 15, 7, 12);

        // Round headlights on short chrome stalks between hood and fenders; red tail lamps.
        p.headlight(CAR_CHROME, CAR_LENS, 11, 16, -31, 3.5f, 6);
        p.headlight(CAR_CHROME, CAR_LENS, -11, 16, -31, 3.5f, 6);
        p.pair(CAR_CHROME, 10.5f, 12, -30, 11.5f, 14.5f, -29);
        p.pair(CAR_TAIL, 12, 12, 28.5f, 13.5f, 13.5f, 29.5f);

        // Wheels, and the spare standing upright on the back.
        float r = 6;
        p.wheel(CAR_TYRE, CAR_WHEEL, 12, r, -22, r, 4, 0);
        p.wheel(CAR_TYRE, CAR_WHEEL, -12, r, -22, r, 4, 0);
        p.wheel(CAR_TYRE, CAR_WHEEL, 12, r, 19, r, 4, 0);
        p.wheel(CAR_TYRE, CAR_WHEEL, -12, r, 19, r, 4, 0);
        p.wheel(CAR_TYRE, CAR_WHEEL, 0, 14, 35.2f, r, 4, Mth.HALF_PI);
        return LayerDefinition.create(mesh, TEX_SIZE, TEX_SIZE);
    }

    /**
     * A boxy white 1940s milk delivery van, 2.5 blocks wide, 5 long and nearly 3 tall: a short
     * rounded nose with round headlights on the fenders, a tall cab with a sliding door on each
     * side, and a big box body lettered "MILK".
     */
    public static LayerDefinition createVanLayer() {
        MeshDefinition mesh = new MeshDefinition();
        Parts p = new Parts(mesh.getRoot());

        // Nose (stepped in at the top so it reads as rounded), cab, lettered box body, roof.
        p.box(VAN_PAINT, -16, 8, -36, 16, 22, -22);
        p.box(VAN_PAINT, -16, 22, -33, 16, 25, -22);
        p.box(VAN_PAINT, -17, 8, -22, 17, 44, -6);
        p.box(VAN_BOX, -18, 8, -6, 18, 44, 38);
        p.box(VAN_PAINT, -18.5f, 44, -23, 18.5f, 46, 39);

        // Windscreen, grille, bumpers, tail lamps.
        p.box(VAN_GLASS, -14, 30, -22.5f, 14, 41, -22);
        p.box(VAN_GRILLE, -9, 10, -36.8f, 9, 20, -35.8f);
        p.box(VAN_CHROME, -19, 6, -39, 19, 9, -36);
        p.box(VAN_CHROME, -18, 6, 38, 18, 9, 40);
        p.pair(VAN_TAIL, 15, 12, 38, 17, 16, 38.6f);

        // Sliding doors (one each side of the cab, standing a pixel proud) with a black step under each.
        p.pair(VAN_DOOR, 17, 9, -20, 18, 40, -7);
        p.pair(VAN_TYRE, 17, 6, -20, 19, 7, -7);

        // Front fenders over the front wheels, round headlights sitting on them.
        p.pair(VAN_PAINT, 13.5f, 14.5f, -34, 19.5f, 17, -20);
        p.headlight(VAN_CHROME, VAN_LENS, 16.5f, 19, -32, 4, 7);
        p.headlight(VAN_CHROME, VAN_LENS, -16.5f, 19, -32, 4, 7);

        float r = 7;
        p.wheel(VAN_TYRE, VAN_WHEEL, 16.5f, r, -27, r, 5, 0);
        p.wheel(VAN_TYRE, VAN_WHEEL, -16.5f, r, -27, r, 5, 0);
        p.wheel(VAN_TYRE, VAN_WHEEL, 16.5f, r, 26, r, 5, 0);
        p.wheel(VAN_TYRE, VAN_WHEEL, -16.5f, r, 26, r, 5, 0);
        return LayerDefinition.create(mesh, TEX_SIZE, TEX_SIZE);
    }

    /** Adds named boxes to the model; every box needs a unique name, so this counts them. */
    private static final class Parts {
        private final PartDefinition root;
        private int count;

        Parts(PartDefinition root) {
            this.root = root;
        }

        private String next() {
            return "part" + count++;
        }

        /** A box from (x0, h0, z0) to (x1, h1, z1), with h measured up from the ground. */
        void box(Swatch swatch, float x0, float h0, float z0, float x1, float h1, float z1) {
            root.addOrReplaceChild(next(), CubeListBuilder.create().texOffs(swatch.u, swatch.v)
                    .addBox(x0, -h1, z0, x1 - x0, h1 - h0, z1 - z0), PartPose.ZERO);
        }

        /** The same box on both sides of the vehicle: as given (x0..x1 > 0) and mirrored to -x1..-x0. */
        void pair(Swatch swatch, float x0, float h0, float z0, float x1, float h1, float z1) {
            box(swatch, x0, h0, z0, x1, h1, z1);
            box(swatch, -x1, h0, z0, -x0, h1, z1);
        }

        /**
         * A slab tilted down towards the back: its top edge starts at height {@code h}, position
         * {@code z} and runs {@code length} pixels at {@code angle} radians (negative = sloping
         * down), {@code thickness} deep below that line. Returns it so things can sit on it.
         */
        PartDefinition slope(Swatch swatch, float x0, float x1, float h, float z, float length, float thickness, float angle) {
            return root.addOrReplaceChild(next(), CubeListBuilder.create().texOffs(swatch.u, swatch.v)
                    .addBox(x0, 0, 0, x1 - x0, thickness, length), PartPose.offsetAndRotation(0, -h, z, angle, 0, 0));
        }

        /**
         * A round headlight: a small chrome bucket with a flat round lens on its front. The lens is
         * a square plane whose corners are see-through in the texture; it is bigger than the
         * bucket so the bucket's square corners stay hidden behind it.
         */
        void headlight(Swatch chrome, Swatch lens, float x, float h, float zFront, float bucket, float lensSize) {
            float b = bucket / 2;
            box(chrome, x - b, h - b, zFront, x + b, h + b, zFront + 3);
            root.addOrReplaceChild(next(), CubeListBuilder.create().texOffs(lens.u, lens.v)
                    .addBox(x - lensSize / 2, -h - lensSize / 2, zFront - 0.05f, lensSize, lensSize, 0), PartPose.ZERO);
        }

        /**
         * A wheel whose axle runs along x (or along z if {@code yRot} is a quarter turn, for the
         * spare): the tyre's tread is four slabs turned 45 degrees apart, which makes an octagon,
         * and each side gets a flat round disc (tyre, rim and hub cap) with see-through corners.
         */
        void wheel(Swatch tyre, Swatch disc, float x, float h, float z, float radius, float width, float yRot) {
            PartDefinition wheel = root.addOrReplaceChild(next(), CubeListBuilder.create(),
                    PartPose.offsetAndRotation(x, -h, z, 0, yRot, 0));
            float tread = 2 * radius * (float) Math.tan(Math.PI / 8);
            for (int k = 0; k < 4; k++) {
                wheel.addOrReplaceChild("tread" + k, CubeListBuilder.create().texOffs(tyre.u, tyre.v)
                                .addBox(-width / 2, -radius, -tread / 2, width, 2 * radius, tread),
                        PartPose.rotation(k * Mth.PI / 4, 0, 0));
            }
            // The discs sit a hair outside the tread so the two never flicker against each other.
            float side = width / 2 + 0.02f;
            wheel.addOrReplaceChild("outer", CubeListBuilder.create().texOffs(disc.u, disc.v)
                    .addBox(side, -radius, -radius, 0, 2 * radius, 2 * radius), PartPose.ZERO);
            wheel.addOrReplaceChild("inner", CubeListBuilder.create().texOffs(disc.u, disc.v)
                    .addBox(-side, -radius, -radius, 0, 2 * radius, 2 * radius), PartPose.ZERO);
        }
    }
}
