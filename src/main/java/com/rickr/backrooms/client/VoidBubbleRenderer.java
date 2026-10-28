package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.VoidBubble;
import com.rickr.backrooms.world.BackroomsLevel;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a fallen void bubble: a sphere 3 blocks across, like a soap bubble the size of a car.
 *
 * <ul>
 *   <li>Its surface shows a glimpse of the level it mirrors (a blurred panorama per level, made by
 *       tools/anomaly_assets/level227_audio.py), rushing across the sphere very fast (canon: the
 *       bubbles' surfaces move very fast).</li>
 *   <li>Toward its rim it turns iridescent and more solid, the colours of a thin film sliding
 *       round with time, like light on a soap bubble seen edge-on.</li>
 *   <li>It glows on its own (full-bright), and with no face culling you also see the far inside of
 *       the sphere through the near side, rushing the other way.</li>
 * </ul>
 */
public class VoidBubbleRenderer extends EntityRenderer<VoidBubble> {
    private static final int RINGS = 16;
    private static final int SEGMENTS = 28;
    /** How fast the reflection rushes round (texture widths per second) and the sphere turns (radians per second). */
    private static final float RUSH = 0.35f;
    private static final float SPIN = 0.25f;
    /** Opacity: a see-through middle, a nearly solid rim. */
    private static final float CENTRE_ALPHA = 0.55f;
    private static final float RIM_ALPHA = 0.95f;

    private static final Map<BackroomsLevel, ResourceLocation> GLIMPSES = new EnumMap<>(BackroomsLevel.class);

    public VoidBubbleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(VoidBubble bubble, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        // The bubble's own smooth path (the same on every client), relative to where Minecraft put it.
        Vec3 centre = bubble.centreAt(partialTick);
        double ex = Mth.lerp(partialTick, bubble.xo, bubble.getX());
        double ey = Mth.lerp(partialTick, bubble.yo, bubble.getY());
        double ez = Mth.lerp(partialTick, bubble.zo, bubble.getZ());
        Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
        // The camera seen from the bubble's centre (for the rim's iridescence).
        float vx = (float) (cam.x - centre.x);
        float vy = (float) (cam.y - centre.y);
        float vz = (float) (cam.z - centre.z);
        float vl = Mth.sqrt(vx * vx + vy * vy + vz * vz);
        if (vl > 1e-4f) {
            vx /= vl;
            vy /= vl;
            vz /= vl;
        }
        float time = (bubble.level().getGameTime() % 24_000L + partialTick) / 20.0f;
        float spin = time * SPIN;

        poseStack.pushPose();
        poseStack.translate(centre.x - ex, centre.y - ey, centre.z - ez);
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucentEmissive(getTextureLocation(bubble)));
        PoseStack.Pose pose = poseStack.last();
        float r = VoidBubble.RADIUS;
        for (int ring = 0; ring < RINGS; ring++) {
            float lat0 = Mth.PI * ring / RINGS;
            float lat1 = Mth.PI * (ring + 1) / RINGS;
            for (int seg = 0; seg < SEGMENTS; seg++) {
                float lon0 = Mth.TWO_PI * seg / SEGMENTS + spin;
                float lon1 = Mth.TWO_PI * (seg + 1) / SEGMENTS + spin;
                float u0 = (float) seg / SEGMENTS + time * RUSH;
                float u1 = (float) (seg + 1) / SEGMENTS + time * RUSH;
                float v0 = (float) ring / RINGS;
                float v1 = (float) (ring + 1) / RINGS;
                vertex(buffer, pose, r, lat0, lon0, u0, v0, vx, vy, vz, time);
                vertex(buffer, pose, r, lat0, lon1, u1, v0, vx, vy, vz, time);
                vertex(buffer, pose, r, lat1, lon1, u1, v1, vx, vy, vz, time);
                vertex(buffer, pose, r, lat1, lon0, u0, v1, vx, vy, vz, time);
            }
        }
        poseStack.popPose();
        super.render(bubble, yaw, partialTick, poseStack, buffers, packedLight);
    }

    /** One point of the sphere: its reflection tinted by a thin film that shows most at the rim. */
    private static void vertex(VertexConsumer b, PoseStack.Pose pose, float r, float lat, float lon, float u, float v,
                               float vx, float vy, float vz, float time) {
        float sinLat = Mth.sin(lat);
        float nx = sinLat * Mth.cos(lon);
        float ny = Mth.cos(lat);
        float nz = sinLat * Mth.sin(lon);
        // Fresnel: 0 where the surface faces you, 1 where you see it edge-on (front or back side).
        float facing = Math.abs(nx * vx + ny * vy + nz * vz);
        float rim = 1.0f - facing;
        rim = rim * rim;
        // Thin-film colour: a hue that slides with the angle, the height on the sphere and time.
        float hue = rim * 1.4f + ny * 0.25f + time * 0.07f;
        float fr = 0.5f + 0.5f * Mth.cos(Mth.TWO_PI * (hue + 0.00f));
        float fg = 0.5f + 0.5f * Mth.cos(Mth.TWO_PI * (hue + 0.33f));
        float fb = 0.5f + 0.5f * Mth.cos(Mth.TWO_PI * (hue + 0.67f));
        float film = 0.15f + 0.75f * rim;
        float cr = Mth.lerp(film, 1.0f, 0.55f + 0.45f * fr);
        float cg = Mth.lerp(film, 1.0f, 0.55f + 0.45f * fg);
        float cb = Mth.lerp(film, 1.0f, 0.55f + 0.45f * fb);
        float alpha = Mth.lerp(rim, CENTRE_ALPHA, RIM_ALPHA);
        b.addVertex(pose, nx * r, ny * r, nz * r).setColor(cr, cg, cb, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(VoidBubble bubble) {
        return GLIMPSES.computeIfAbsent(bubble.target(),
                level -> Backrooms.id("textures/entity/void_bubble/" + level.getSerializedName() + ".png"));
    }
}
