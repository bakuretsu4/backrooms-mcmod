package com.rickr.backrooms.client;

import static com.rickr.backrooms.client.FigureBoxes.box;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rickr.backrooms.entity.Penitent;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A Penitent (after the play-test, from the references "capirote + chains": the hooded nazarenos of
 * Holy Week and the Maltese penitents dragging chains from their ankles): a tall pointed hood of
 * dark cloth falling over the shoulders and down the chest, two eye holes and nothing to see in
 * them; a long dark robe with a rope belt; bare feet with iron cuffs and a heavy chain between the
 * ankles, a length of it trailing behind; the walkers and watchers carry a tall candle, lit, and some
 * a heavy chain across their shoulders. Built from boxes in code (feet at 0, facing +Z). Its pose
 * comes from the entity:
 * <ul>
 *   <li>kneeling in chains: bent forward, head bowed, its wrists chained to a ring in the ground;</li>
 *   <li>praying: kneeling upright, hands raised together before its bowed head;</li>
 *   <li>walking: slow, a little bowed, the robe swaying;</li>
 *   <li>standing: still, its head turning after you.</li>
 * </ul>
 */
public class PenitentRenderer extends EntityRenderer<Penitent> {
    private static final int ROBE = FigureBoxes.rgb(0.10f, 0.09f, 0.085f);
    /** The hood: a deep oxblood red, the one colour in the level that isn't grey. */
    private static final int HOOD = FigureBoxes.rgb(0.30f, 0.07f, 0.06f);
    private static final int HOOD_DARK = FigureBoxes.rgb(0.22f, 0.05f, 0.045f);
    private static final int VOID = FigureBoxes.rgb(0.008f, 0.007f, 0.008f);
    private static final int HAND = FigureBoxes.rgb(0.42f, 0.38f, 0.34f);
    private static final int IRON = FigureBoxes.rgb(0.20f, 0.20f, 0.22f);
    private static final int ROPE = FigureBoxes.rgb(0.40f, 0.35f, 0.26f);
    private static final int WAX = FigureBoxes.rgb(0.72f, 0.68f, 0.58f);
    private static final int FLAME = FigureBoxes.rgb(1.0f, 0.78f, 0.45f);

    public PenitentRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.3f;
    }

    @Override
    public void render(Penitent entity, float entityYaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        if (entity.isVanishing()) {
            return; // gone between one step and the next
        }
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot) - bodyYaw;
        float age = entity.tickCount + partialTick;
        VertexConsumer out = buffers.getBuffer(WatcherRenderTypes.SOLID);
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        switch (entity.pose()) {
            case Penitent.KNEEL -> kneel(poses, out, light, age, entity.getId(), true);
            case Penitent.PRAY -> kneel(poses, out, light, age, entity.getId(), false);
            default -> stand(poses, out, light, age, entity, headYaw, partialTick);
        }
        poses.popPose();
        super.render(entity, entityYaw, partialTick, poses, buffers, light);
    }

    private static void stand(PoseStack poses, VertexConsumer out, int light, float age, Penitent entity, float headYaw, float partialTick) {
        boolean walking = entity.pose() == Penitent.WALK;
        float walk = walking ? entity.walkAnimation.position(partialTick) : 0;
        float speed = walking ? entity.walkAnimation.speed(partialTick) : 0;
        float step = Mth.sin(walk * 0.6f) * speed;
        // Bare feet, shuffling, iron cuffs, the chain between them sagging, a length trailing behind.
        PoseStack.Pose p = poses.last();
        float lf = 0.12f * step;
        box(p, out, -0.17f, 0.0f, -0.08f + lf, -0.05f, 0.08f, 0.14f + lf, HAND, light);
        box(p, out, 0.05f, 0.0f, -0.08f - lf, 0.17f, 0.08f, 0.14f - lf, HAND, light);
        box(p, out, -0.18f, 0.06f, -0.09f + lf, -0.04f, 0.14f, 0.07f + lf, IRON, light);
        box(p, out, 0.04f, 0.06f, -0.09f - lf, 0.18f, 0.14f, 0.07f - lf, IRON, light);
        FigureBoxes.chain(poses, out, -0.11f, 0.1f, lf, 0.11f, 0.1f, -lf, IRON, light);
        FigureBoxes.chain(poses, out, 0.11f, 0.08f, -0.06f - lf, 0.25f, 0.02f, -0.9f, IRON, light);
        FigureBoxes.chain(poses, out, 0.25f, 0.02f, -0.9f, 0.05f, 0.02f, -1.5f, IRON, light);
        poses.pushPose();
        if (walking) {
            poses.translate(0, Math.abs(Mth.sin(walk * 0.6f)) * 0.025f * speed, 0);
            poses.mulPose(Axis.XP.rotationDegrees(5));
        }
        p = poses.last();
        // The robe to the ankles, swaying as it walks.
        poses.pushPose();
        poses.translate(0, 0.95f, 0);
        poses.mulPose(Axis.XP.rotationDegrees(step * 4));
        box(poses.last(), out, -0.26f, -0.8f, -0.2f, 0.26f, 0.0f, 0.21f, ROBE, light);
        poses.popPose();
        box(p, out, -0.23f, 0.95f, -0.16f, 0.23f, 1.42f, 0.16f, ROBE, light);
        box(p, out, -0.25f, 0.9f, -0.18f, 0.25f, 0.97f, 0.18f, ROPE, light); // the rope belt
        box(p, out, 0.12f, 0.6f, 0.17f, 0.15f, 0.92f, 0.2f, ROPE, light);   // its knotted end
        boolean candle = entity.pose() != Penitent.KNEEL && entity.getId() % 3 != 0;
        boolean yoke = !candle && entity.getId() % 2 == 0;
        if (candle) {
            // Both hands before it holding a tall candle, lit.
            box(p, out, -0.18f, 1.02f, 0.1f, 0.18f, 1.22f, 0.3f, ROBE, light);
            box(p, out, -0.06f, 1.05f, 0.28f, 0.06f, 1.16f, 0.34f, HAND, light);
            box(p, out, -0.025f, 0.85f, 0.29f, 0.025f, 1.75f, 0.33f, WAX, light);
            float flicker = 0.02f * Mth.sin(age * 0.9f + entity.getId());
            box(p, out, -0.015f, 1.75f, 0.3f, 0.015f, 1.82f + flicker, 0.32f, FLAME, LightTexture.FULL_BRIGHT);
        } else {
            box(p, out, -0.21f, 1.02f, 0.1f, 0.21f, 1.22f, 0.33f, ROBE, light);
            box(p, out, -0.06f, 1.05f, 0.31f, 0.06f, 1.18f, 0.36f, HAND, light);
        }
        // The capirote: the cloth falling over the shoulders and down the chest, the tall cone above.
        poses.pushPose();
        poses.translate(0, 1.4f, 0);
        poses.mulPose(Axis.YP.rotationDegrees(-Mth.clamp(headYaw, -70, 70)));
        poses.mulPose(Axis.XP.rotationDegrees(walking ? 12 : 6 + 3 * Mth.sin(age * 0.02f + entity.getId())));
        capirote(poses.last(), out, light);
        poses.popPose();
        if (yoke) {
            // A heavy chain across the shoulders, hanging down each side.
            FigureBoxes.chain(poses, out, -0.4f, 1.0f, 0.02f, -0.28f, 1.46f, 0.0f, IRON, light);
            FigureBoxes.chain(poses, out, -0.28f, 1.46f, 0.0f, 0.28f, 1.46f, 0.0f, IRON, light);
            FigureBoxes.chain(poses, out, 0.28f, 1.46f, 0.0f, 0.4f, 1.0f, 0.02f, IRON, light);
        }
        poses.popPose();
    }

    /**
     * The hood from the neck at 0: cloth falling to a point down the chest and over the shoulders,
     * the head, and the stiff cone rising high above it; two eye holes, dark, in front (+Z).
     */
    private static void capirote(PoseStack.Pose h, VertexConsumer out, int light) {
        box(h, out, -0.25f, -0.32f, -0.17f, 0.25f, 0.04f, 0.19f, HOOD_DARK, light);   // over the shoulders
        box(h, out, -0.15f, -0.48f, 0.12f, 0.15f, -0.3f, 0.21f, HOOD_DARK, light);     // the bib, down the chest
        box(h, out, -0.06f, -0.56f, 0.13f, 0.06f, -0.46f, 0.2f, HOOD_DARK, light);
        box(h, out, -0.16f, 0.0f, -0.16f, 0.16f, 0.36f, 0.17f, HOOD, light);          // the head
        // The cone, stepping in as it rises.
        float[][] tiers = {{0.36f, 0.62f, 0.15f}, {0.62f, 0.88f, 0.12f}, {0.88f, 1.1f, 0.09f}, {1.1f, 1.28f, 0.06f}, {1.28f, 1.4f, 0.035f}};
        for (float[] t : tiers) {
            box(h, out, -t[2], t[0], -t[2], t[2], t[1], t[2], HOOD, light);
        }
        // Eye holes, and the dark seen through them.
        box(h, out, -0.1f, 0.18f, 0.169f, -0.03f, 0.23f, 0.172f, VOID, light);
        box(h, out, 0.03f, 0.18f, 0.169f, 0.1f, 0.23f, 0.172f, VOID, light);
    }

    /** Kneeling: chained (bent low, wrists chained to a ring in the ground ahead) or praying (upright, hands raised). */
    private static void kneel(PoseStack poses, VertexConsumer out, int light, float age, int id, boolean chained) {
        PoseStack.Pose p = poses.last();
        // Shins on the ground behind, the lap over them, the robe pooled round.
        box(p, out, -0.25f, 0.0f, -0.48f, 0.25f, 0.22f, 0.12f, ROBE, light);
        box(p, out, -0.26f, 0.12f, -0.30f, 0.26f, 0.5f, 0.22f, ROBE, light);
        // A slow breath: the body rises and sinks.
        float breath = Mth.sin(age * 0.05f + id) * 1.5f;
        poses.pushPose();
        poses.translate(0, 0.48f, -0.04f);
        poses.mulPose(Axis.XP.rotationDegrees((chained ? 32 : 4) + breath));
        PoseStack.Pose t = poses.last();
        box(t, out, -0.24f, 0.0f, -0.16f, 0.24f, 0.58f, 0.16f, ROBE, light);
        box(t, out, -0.27f, 0.42f, -0.16f, 0.27f, 0.6f, 0.16f, ROBE, light);
        if (chained) {
            // Arms hanging forward to the ground, wrists in iron.
            box(t, out, -0.22f, 0.12f, 0.1f, -0.1f, 0.5f, 0.28f, ROBE, light);
            box(t, out, 0.1f, 0.12f, 0.1f, 0.22f, 0.5f, 0.28f, ROBE, light);
            box(t, out, -0.21f, 0.04f, 0.13f, -0.11f, 0.13f, 0.25f, HAND, light);
            box(t, out, 0.11f, 0.04f, 0.13f, 0.21f, 0.13f, 0.25f, HAND, light);
            box(t, out, -0.23f, 0.12f, 0.11f, -0.09f, 0.17f, 0.27f, IRON, light);
            box(t, out, 0.09f, 0.12f, 0.11f, 0.23f, 0.17f, 0.27f, IRON, light);
        } else {
            // Sleeves up before the chest, hands pressed together before the face.
            box(t, out, -0.14f, 0.28f, 0.12f, 0.14f, 0.52f, 0.32f, ROBE, light);
            box(t, out, -0.05f, 0.5f, 0.2f, 0.05f, 0.7f, 0.28f, HAND, light);
        }
        poses.pushPose();
        poses.translate(0, 0.58f, 0);
        poses.mulPose(Axis.XP.rotationDegrees(chained ? 28 : 22));
        capirote(poses.last(), out, light);
        poses.popPose();
        // The wrists' chains, down to a ring in the ground ahead (in the body's frame).
        poses.popPose();
        if (chained) {
            float lean = (float) Math.toRadians(32 + breath);
            for (int side = -1; side <= 1; side += 2) {
                // Where the wrist is, from the torso's frame: (x, 0.13 up, 0.19 forward) turned by the lean.
                float wy = 0.48f + 0.13f * Mth.cos(lean) - 0.19f * Mth.sin(lean);
                float wz = -0.04f + 0.13f * Mth.sin(lean) + 0.19f * Mth.cos(lean);
                FigureBoxes.chain(poses, out, side * 0.16f, wy, wz, side * 0.05f, 0.08f, 0.85f, IRON, light);
            }
            box(poses.last(), out, -0.09f, 0.0f, 0.78f, 0.09f, 0.1f, 0.94f, IRON, light);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(Penitent entity) {
        return WatcherRenderTypes.WHITE;
    }
}
