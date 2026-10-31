package com.rickr.backrooms.client;

import static com.rickr.backrooms.client.FigureBoxes.box;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rickr.backrooms.entity.Pilgrim;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * The pilgrim (after the play-test, from the reference "burdened traveller", like Elden Ring's
 * wandering merchants): an old man bent nearly double under an enormous pack, wrapped in layers of
 * rags, a scarf over most of his face, leaning on a staff. The pack is a wooden frame taller than he
 * is, piled with bundles, a rolled blanket, a crate and sacks; pots and a bell dangle from it and a
 * lantern hangs from a pole over the top, glowing. Built from boxes in code (feet at 0, facing +Z).
 *
 * <p>What he says shows in a speech bubble over his head (after the play-test: "a chat bubble
 * above their heads"), long enough to read, then fades.
 */
public class PilgrimRenderer extends EntityRenderer<Pilgrim> {
    private static final int RAGS = FigureBoxes.rgb(0.28f, 0.24f, 0.19f);
    private static final int RAGS_DARK = FigureBoxes.rgb(0.19f, 0.17f, 0.14f);
    private static final int RAGS_PALE = FigureBoxes.rgb(0.40f, 0.37f, 0.31f);
    private static final int SCARF = FigureBoxes.rgb(0.33f, 0.16f, 0.13f);
    private static final int SKIN = FigureBoxes.rgb(0.52f, 0.42f, 0.36f);
    private static final int BEARD = FigureBoxes.rgb(0.60f, 0.59f, 0.56f);
    private static final int EYE = FigureBoxes.rgb(0.04f, 0.035f, 0.035f);
    private static final int WOOD = FigureBoxes.rgb(0.27f, 0.20f, 0.13f);
    private static final int CRATE = FigureBoxes.rgb(0.36f, 0.27f, 0.17f);
    private static final int SACK = FigureBoxes.rgb(0.44f, 0.38f, 0.27f);
    private static final int BLANKET = FigureBoxes.rgb(0.36f, 0.14f, 0.12f);
    private static final int CLOTH = FigureBoxes.rgb(0.25f, 0.27f, 0.30f);
    private static final int IRON = FigureBoxes.rgb(0.15f, 0.15f, 0.16f);
    private static final int BRONZE = FigureBoxes.rgb(0.45f, 0.33f, 0.16f);
    private static final int FLAME = FigureBoxes.rgb(1.0f, 0.76f, 0.42f);
    /** The bubble: a dark rounded card, pale text. */
    private static final int BUBBLE = 0xD8141416;
    private static final int BUBBLE_TEXT = 0xFFE6E2D6;
    private static final int BUBBLE_WIDTH = 160;

    public PilgrimRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5f;
    }

    @Override
    public void render(Pilgrim entity, float entityYaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot) - bodyYaw;
        float age = entity.tickCount + partialTick;
        VertexConsumer out = buffers.getBuffer(WatcherRenderTypes.SOLID);
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        PoseStack.Pose p = poses.last();
        // Legs wrapped in rags, the hem of a long ragged coat.
        box(p, out, -0.19f, 0.0f, -0.08f, -0.03f, 0.5f, 0.1f, RAGS_DARK, light);
        box(p, out, 0.03f, 0.0f, -0.08f, 0.19f, 0.5f, 0.1f, RAGS_DARK, light);
        box(p, out, -0.26f, 0.3f, -0.18f, 0.26f, 0.95f, 0.2f, RAGS, light);
        box(p, out, -0.27f, 0.3f, 0.12f, -0.05f, 0.62f, 0.21f, RAGS_PALE, light); // a torn flap
        box(p, out, 0.12f, 0.38f, -0.19f, 0.27f, 0.7f, -0.12f, RAGS_PALE, light);
        // Bent nearly double under the pack, breathing hard.
        poses.pushPose();
        poses.translate(0, 0.92f, -0.02f);
        poses.mulPose(Axis.XP.rotationDegrees(34 + Mth.sin(age * 0.07f) * 1.5f));
        PoseStack.Pose t = poses.last();
        box(t, out, -0.25f, 0.0f, -0.16f, 0.25f, 0.55f, 0.17f, RAGS, light);
        box(t, out, -0.27f, 0.4f, -0.17f, 0.27f, 0.6f, 0.18f, RAGS_DARK, light); // shoulders, a shawl
        box(t, out, -0.2f, 0.05f, 0.16f, 0.2f, 0.4f, 0.19f, RAGS_PALE, light);   // a strap across the chest
        // Arms: the right forward to the staff, the left gripping a strap.
        box(t, out, 0.24f, 0.12f, -0.04f, 0.36f, 0.56f, 0.12f, RAGS, light);
        box(t, out, 0.25f, 0.02f, 0.06f, 0.35f, 0.14f, 0.18f, SKIN, light);
        box(t, out, -0.36f, 0.12f, -0.04f, -0.24f, 0.56f, 0.1f, RAGS, light);
        box(t, out, -0.35f, 0.04f, 0.02f, -0.25f, 0.14f, 0.14f, SKIN, light);
        // The head, pushed forward and turned up to look at you; a hood of rags, a scarf over the face.
        poses.pushPose();
        poses.translate(0, 0.58f, 0.1f);
        poses.mulPose(Axis.XP.rotationDegrees(-30));
        poses.mulPose(Axis.YP.rotationDegrees(-Mth.clamp(headYaw, -55, 55)));
        PoseStack.Pose h = poses.last();
        box(h, out, -0.13f, 0.0f, -0.12f, 0.13f, 0.27f, 0.13f, SKIN, light);
        box(h, out, -0.16f, 0.14f, -0.16f, 0.16f, 0.33f, 0.12f, RAGS_DARK, light);  // the hood
        box(h, out, -0.16f, -0.02f, -0.15f, -0.12f, 0.3f, 0.1f, RAGS_DARK, light);
        box(h, out, 0.12f, -0.02f, -0.15f, 0.16f, 0.3f, 0.1f, RAGS_DARK, light);
        box(h, out, -0.14f, -0.03f, 0.06f, 0.14f, 0.13f, 0.16f, SCARF, light);     // the scarf
        box(h, out, -0.05f, -0.12f, 0.08f, 0.06f, -0.02f, 0.14f, BEARD, light);   // beard under it
        box(h, out, -0.08f, 0.16f, 0.13f, -0.03f, 0.19f, 0.135f, EYE, light);
        box(h, out, 0.03f, 0.16f, 0.13f, 0.08f, 0.19f, 0.135f, EYE, light);
        box(h, out, -0.1f, 0.2f, 0.13f, 0.1f, 0.22f, 0.134f, BEARD, light);      // grey brows
        poses.popPose();
        // The pack on his back: a frame taller than he is, piled high.
        poses.pushPose();
        poses.translate(0, 0.0f, -0.17f);
        PoseStack.Pose k = poses.last();
        box(k, out, -0.36f, -0.25f, -0.06f, -0.3f, 2.05f, 0.0f, WOOD, light);      // the frame's poles
        box(k, out, 0.3f, -0.25f, -0.06f, 0.36f, 2.05f, 0.0f, WOOD, light);
        box(k, out, -0.36f, 0.25f, -0.07f, 0.36f, 0.31f, 0.0f, WOOD, light);       // crossbars
        box(k, out, -0.36f, 1.2f, -0.07f, 0.36f, 1.26f, 0.0f, WOOD, light);
        box(k, out, -0.3f, -0.2f, -0.55f, 0.3f, 0.32f, -0.06f, SACK, light);       // sacks at the bottom
        box(k, out, -0.38f, -0.15f, -0.5f, -0.18f, 0.18f, -0.2f, CLOTH, light);
        box(k, out, -0.28f, 0.32f, -0.62f, 0.28f, 0.85f, -0.07f, CRATE, light);     // a crate
        box(k, out, -0.29f, 0.56f, -0.63f, 0.29f, 0.6f, -0.06f, WOOD, light);
        box(k, out, -0.34f, 0.85f, -0.58f, 0.34f, 1.35f, -0.06f, CLOTH, light);     // a big bundle in cloth
        box(k, out, -0.05f, 0.85f, -0.6f, 0.05f, 1.36f, -0.05f, RAGS_PALE, light); // its cord
        box(k, out, -0.42f, 1.35f, -0.4f, 0.42f, 1.62f, -0.12f, BLANKET, light);    // a rolled blanket across the top
        box(k, out, -0.2f, 1.62f, -0.48f, 0.22f, 1.9f, -0.1f, SACK, light);
        // A pole over the top with a lantern hanging from its end, swinging.
        box(k, out, -0.03f, 1.9f, -0.06f, 0.03f, 2.35f, 0.0f, WOOD, light);
        box(k, out, -0.03f, 2.3f, 0.0f, 0.03f, 2.35f, 0.45f, WOOD, light);
        float swing = Mth.sin(age * 0.09f) * 0.03f;
        box(k, out, -0.01f, 2.12f, 0.42f + swing, 0.01f, 2.3f, 0.44f + swing, IRON, light);
        box(k, out, -0.07f, 1.92f, 0.37f + swing, 0.07f, 2.12f, 0.51f + swing, IRON, light);
        box(k, out, -0.06f, 1.94f, 0.38f + swing, 0.06f, 2.1f, 0.5f + swing, FLAME, LightTexture.FULL_BRIGHT);
        // Pots and a bell dangling from the frame's sides.
        float clank = Mth.sin(age * 0.11f + 1.0f) * 0.02f;
        box(k, out, 0.36f, 0.85f + clank, -0.3f, 0.52f, 1.0f + clank, -0.14f, IRON, light);
        box(k, out, 0.4f, 0.55f - clank, -0.42f, 0.5f, 0.68f - clank, -0.32f, IRON, light);
        box(k, out, -0.5f, 0.95f + clank, -0.3f, -0.37f, 1.08f + clank, -0.18f, BRONZE, light);
        box(k, out, -0.47f, 1.08f + clank, -0.27f, -0.4f, 1.12f + clank, -0.21f, BRONZE, light);
        poses.popPose();
        poses.popPose();
        // The staff, planted ahead of him.
        box(p, out, 0.32f, 0.0f, 0.44f, 0.37f, 1.55f, 0.49f, WOOD, light);
        box(p, out, 0.3f, 1.5f, 0.42f, 0.39f, 1.6f, 0.51f, RAGS_PALE, light);
        poses.popPose();
        bubble(entity, poses, buffers);
        super.render(entity, entityYaw, partialTick, poses, buffers, light);
    }

    /**
     * What he is saying, in a bubble over his head facing you: a dark card with a little tail
     * pointing down at him, the words wrapped; it shows for longer the more there is to read, fading
     * in and out. Only when you are near enough to hear him.
     */
    private void bubble(Pilgrim entity, PoseStack poses, MultiBufferSource buffers) {
        String key = entity.speechKey();
        if (key == null || entityRenderDispatcher.distanceToSqr(entity) > 24 * 24) {
            return;
        }
        Component words = Component.translatable(key);
        int length = words.getString().length();
        int lasts = 70 + length * 2;
        int age = entity.speechAge();
        if (age > lasts) {
            return;
        }
        float alpha = Math.min(1.0f, Math.min((lasts - age) / 12.0f, (age + 1) / 4.0f));
        Font font = getFont();
        List<FormattedCharSequence> lines = font.split(words, BUBBLE_WIDTH);
        int w = 0;
        for (FormattedCharSequence l : lines) {
            w = Math.max(w, font.width(l));
        }
        int lineH = font.lineHeight + 1;
        int h = lines.size() * lineH;
        poses.pushPose();
        poses.translate(0, 3.0f, 0);
        poses.mulPose(entityRenderDispatcher.cameraOrientation());
        poses.scale(0.025f, -0.025f, 0.025f);
        Matrix4f m = poses.last().pose();
        int bg = ((int) (((BUBBLE >>> 24) & 0xff) * alpha) << 24) | (BUBBLE & 0xffffff);
        float x0 = -w / 2.0f - 5;
        float x1 = w / 2.0f + 5;
        float y0 = -h - 6;
        float y1 = 2;
        VertexConsumer card = buffers.getBuffer(RenderType.textBackground());
        quad(card, m, x0 + 2, y0, x1 - 2, y1, bg);
        quad(card, m, x0, y0 + 2, x0 + 2, y1 - 2, bg); // rounded corners: the sides inset
        quad(card, m, x1 - 2, y0 + 2, x1, y1 - 2, bg);
        // The tail, pointing down at him.
        card.addVertex(m, -5, y1, 0).setColor(bg).setLight(LightTexture.FULL_BRIGHT);
        card.addVertex(m, 0, y1 + 6, 0).setColor(bg).setLight(LightTexture.FULL_BRIGHT);
        card.addVertex(m, 0, y1 + 6, 0).setColor(bg).setLight(LightTexture.FULL_BRIGHT);
        card.addVertex(m, 5, y1, 0).setColor(bg).setLight(LightTexture.FULL_BRIGHT);
        int text = ((int) (255 * alpha) << 24) | (BUBBLE_TEXT & 0xffffff);
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence l = lines.get(i);
            font.drawInBatch(l, -font.width(l) / 2.0f, y0 + 4 + i * lineH, text, false, m, buffers, Font.DisplayMode.POLYGON_OFFSET, 0,
                    LightTexture.FULL_BRIGHT);
        }
        poses.popPose();
    }

    private static void quad(VertexConsumer card, Matrix4f m, float x0, float y0, float x1, float y1, int colour) {
        card.addVertex(m, x0, y0, 0).setColor(colour).setLight(LightTexture.FULL_BRIGHT);
        card.addVertex(m, x0, y1, 0).setColor(colour).setLight(LightTexture.FULL_BRIGHT);
        card.addVertex(m, x1, y1, 0).setColor(colour).setLight(LightTexture.FULL_BRIGHT);
        card.addVertex(m, x1, y0, 0).setColor(colour).setLight(LightTexture.FULL_BRIGHT);
    }

    @Override
    protected boolean shouldShowName(Pilgrim entity) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(Pilgrim entity) {
        return WatcherRenderTypes.WHITE;
    }
}
