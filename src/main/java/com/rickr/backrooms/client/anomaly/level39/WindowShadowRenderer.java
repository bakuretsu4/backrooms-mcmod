package com.rickr.backrooms.client.anomaly.level39;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.anomaly.level39.Level39Sites;
import com.rickr.backrooms.world.anomaly.level39.WindowShadow;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws a {@link WindowShadow} as three thin layers in the mill window (3 x 5 blocks):
 * <ol>
 *   <li>the <b>lustrous light</b>, just in front of the glass: an additive glow that swells as the
 *       window wakes and pulses, slow and inviting while it lures, fast and harsh while it hunts;</li>
 *   <li>the <b>blinds</b> just behind the frosted pane: old yellowed slats, trembling;</li>
 *   <li>the <b>Window Shadow</b> behind them: a dark silhouette blocking the bright gallery's light
 *       (long arms, small torso, bent legs), arms hanging while it waits, pressed flat to the glass
 *       while it hunts.</li>
 * </ol>
 * Textures: {@code textures/entity/l39_window_glow.png}, {@code l39_window_shadow.png} (two
 * frames side by side) and {@code l39_window_blinds.png}, from {@code tools/anomaly_assets/level39_anoms.py}.
 */
public class WindowShadowRenderer extends EntityRenderer<WindowShadow> {
    private static final ResourceLocation GLOW = Backrooms.id("textures/entity/l39_window_glow.png");
    private static final ResourceLocation SHADOW = Backrooms.id("textures/entity/l39_window_shadow.png");
    private static final ResourceLocation BLINDS = Backrooms.id("textures/entity/l39_window_blinds.png");
    private static final float HALF_W = 1.5f;
    private static final float H = Level39Sites.GLASS_HEIGHT;

    /** Depths (blocks, negative = into the wall) behind the glass pane, which sits at most 0.75 in. */
    private static final float BLINDS_Z = -0.80f;
    private static final float SHADOW_Z = -0.90f;

    public WindowShadowRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(WindowShadow entity) {
        return SHADOW;
    }

    @Override
    public void render(WindowShadow w, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        float t = w.clientStateTicks() + partialTick;
        float age = w.tickCount + partialTick;
        byte state = w.state();
        float glow;
        float presence;
        int frame = 0;
        float tremble;
        switch (state) {
            case WindowShadow.WAKING -> {
                float k = Mth.clamp(t / WindowShadow.WAKE_TICKS, 0.0f, 1.0f);
                glow = k * (0.75f + 0.25f * Mth.sin(age * 0.12f));
                presence = k * k * 0.7f;
                tremble = 0.4f * k;
            }
            case WindowShadow.LURKING -> {
                glow = 0.78f + 0.22f * Mth.sin(age * 0.105f);
                presence = 0.72f + 0.06f * Mth.sin(age * 0.05f);
                tremble = 0.35f;
            }
            case WindowShadow.HUNTING -> {
                glow = 0.85f + 0.15f * Mth.sin(age * 0.45f);
                presence = 0.95f;
                // The arms go up and down against the glass, not quite in rhythm.
                frame = Mth.sin(age * 0.21f) + 0.4f * Mth.sin(age * 0.53f) > -0.3f ? 1 : 0;
                tremble = 1.0f;
            }
            case WindowShadow.GRABBING -> {
                glow = 1.0f;
                presence = 1.0f;
                frame = 1;
                tremble = 1.4f;
            }
            case WindowShadow.PASSIVE -> {
                glow = 0.25f + 0.05f * Mth.sin(age * 0.05f);
                presence = 0.3f;
                tremble = 0.05f;
            }
            default -> { // FADING
                float k = 1.0f - Mth.clamp(t / WindowShadow.FADE_TICKS, 0.0f, 1.0f);
                glow = 0.6f * k;
                presence = 0.6f * k;
                tremble = 0.2f * k;
            }
        }
        pose.pushPose();
        // Local +Z points out of the glass toward the town.
        pose.mulPose(Axis.YP.rotationDegrees(-w.facing().toYRot()));
        PoseStack.Pose p = pose.last();
        int full = LightTexture.FULL_BRIGHT;

        // 1. The light: additive, so black in the texture adds nothing.
        float g = Mth.clamp(glow, 0.0f, 1.0f);
        quad(p, buffers.getBuffer(RenderType.eyes(GLOW)), 0.0f, 0.0f, 0.004f, 0.0f, 1.0f, g, g, g * 0.94f, 1.0f, full);

        // 2. The shadow, breathing sideways a little. It stands BEHIND the frosted pane (the pane is
        // set into the glass block; translucent blocks are drawn after entities, so anything drawn in
        // front of the pane would hide it and let the bright gallery show through instead: a pale
        // ghost). Behind the pane it blocks the gallery's light and the glass frosts it over: a dark
        // shape pressed against the window. Drawn twice so it is dense enough to read at a distance.
        if (presence > 0.01f) {
            float sway = 0.05f * Mth.sin(age * 0.04f);
            float u0 = frame * 0.5f;
            VertexConsumer shadow = buffers.getBuffer(RenderType.entityTranslucent(SHADOW));
            for (int i = 0; i < 2; i++) {
                quad(p, shadow, sway, 0.0f, SHADOW_Z, u0, u0 + 0.5f, 0.35f, 0.35f, 0.4f, presence, full);
            }
        }

        // 3. The blinds, trembling, just behind the pane and in front of the shadow.
        float jx = tremble * 0.012f * (Mth.sin(age * 2.3f) + 0.6f * Mth.sin(age * 3.7f + 1.0f));
        float jy = tremble * 0.018f * Mth.sin(age * 1.9f + 0.4f);
        quad(p, buffers.getBuffer(RenderType.entityTranslucent(BLINDS)), jx, jy, BLINDS_Z, 0.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 0.95f, full);
        pose.popPose();
        super.render(w, entityYaw, partialTick, pose, buffers, packedLight);
    }

    /** One 3 x 5 quad facing +Z at depth {@code z}, offset by (dx, dy), using texture columns u0..u1. */
    private static void quad(PoseStack.Pose p, VertexConsumer vc, float dx, float dy, float z, float u0, float u1,
                             float r, float g, float b, float a, int light) {
        float x0 = -HALF_W + dx;
        float x1 = HALF_W + dx;
        float y0 = dy;
        float y1 = H + dy;
        vertex(p, vc, x0, y0, z, u0, 1.0f, r, g, b, a, light);
        vertex(p, vc, x1, y0, z, u1, 1.0f, r, g, b, a, light);
        vertex(p, vc, x1, y1, z, u1, 0.0f, r, g, b, a, light);
        vertex(p, vc, x0, y1, z, u0, 0.0f, r, g, b, a, light);
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v,
                               float r, float g, float b, float a, int light) {
        vc.addVertex(p, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(p, 0.0f, 0.0f, 1.0f);
    }
}
