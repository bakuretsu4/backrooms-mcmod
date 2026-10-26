package com.rickr.backrooms.client.anomaly.level9;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.light.FixturePower;
import com.rickr.backrooms.network.Level9PillarPayload;
import com.rickr.backrooms.network.LevelEventPayload;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.layout.LevelLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4fStack;

/**
 * The Mangled's fog on the client (https://backrooms-wiki.wikidot.com/entity-63): the sudden cold
 * at its start, the "massive black pillar" down the street, and the quake when it vanishes.
 *
 * <ul>
 *   <li><b>Cold:</b> as the fog event begins, frost races over the edges of the picture and your
 *       breath shows; it eases to a lighter frost once the fog is in, and thaws as it lifts.</li>
 *   <li><b>Pillar:</b> a towering column of pitch black on the street where the server says it
 *       stands ({@link Level9PillarPayload}), reaching up into the sky. It is drawn without fog, so
 *       it looms out of the murk from far away, but anything nearer still hides it. It fades in
 *       over a second and out over two.</li>
 *   <li><b>Quake:</b> when it vanishes, leaving its crater, the camera shakes, harder the nearer
 *       you are.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class MangledPillar {
    private static final ResourceLocation TEXTURE = Backrooms.id("textures/block/l9_pillar.png");
    private static final float RADIUS = 2.6f;
    /** Far above the world's top: it should look like it never ends. */
    private static final float HEIGHT = 150.0f;
    private static final int SEGMENTS = 16;
    private static final int BANDS = 15;
    private static final float APPEAR_MS = 1200.0f;
    private static final float VANISH_MS = 2000.0f;
    private static final float QUAKE_MS = 3500.0f;
    private static final float QUAKE_DEG = 1.4f;
    private static final double QUAKE_RANGE = 110.0;
    /** Fog event timing (matches Level9Atmosphere: the chill comes first, the fog after). */
    private static final long CHILL_PEAK_MS = 1200L;
    private static final long CHILL_HOLD_MS = 4000L;
    private static final long CHILL_SETTLE_MS = 3000L;
    private static final float FOG_FROST = 0.35f;
    private static final long THAW_BEFORE_END_MS = 8000L;

    private static volatile boolean standing;
    private static volatile int pillarX;
    private static volatile int pillarZ;
    private static volatile long changedMs;
    /** Alpha when it started vanishing (so a half-faded-in pillar fades out from there). */
    private static float vanishFrom;
    private static volatile long quakeStartMs;

    /** From the network thread (via ModNetwork). */
    public static void receive(int phase, int x, int z) {
        long now = System.currentTimeMillis();
        boolean nowStanding = phase == Level9PillarPayload.STANDING;
        if (nowStanding != standing || x != pillarX || z != pillarZ) {
            vanishFrom = alpha(now);
            changedMs = now;
        }
        pillarX = x;
        pillarZ = z;
        standing = nowStanding;
        if (phase == Level9PillarPayload.CRATER) {
            quakeStartMs = now;
        }
    }

    private static float alpha(long now) {
        float since = now - changedMs;
        if (standing) {
            return Math.min(1.0f, since / APPEAR_MS);
        }
        return Math.max(0.0f, vanishFrom * (1.0f - since / VANISH_MS));
    }

    private static boolean inSuburb(Minecraft mc) {
        return mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_9
                && !CrimsonFieldClient.inField(mc);
    }

    /** The cold of the fog's first seconds. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || BackroomsLevel.fromDimension(mc.level.dimension()) != BackroomsLevel.LEVEL_9) {
            standing = false;
            vanishFrom = 0.0f;
            return;
        }
        long now = System.currentTimeMillis();
        if (!inSuburb(mc) || FixturePower.activeEvent(now) != LevelEventPayload.FOG_ROLL) {
            return;
        }
        long elapsed = now - CameraClientState.eventStartMs;
        float frost;
        if (elapsed < CHILL_PEAK_MS) {
            frost = elapsed / (float) CHILL_PEAK_MS;
        } else if (elapsed < CHILL_HOLD_MS) {
            frost = 1.0f;
        } else {
            float t = Math.min(1.0f, (elapsed - CHILL_HOLD_MS) / (float) CHILL_SETTLE_MS);
            frost = Mth.lerp(t, 1.0f, FOG_FROST);
        }
        frost *= Mth.clamp((CameraClientState.eventEndMs - now) / (float) THAW_BEFORE_END_MS, 0.0f, 1.0f);
        Level9Frost.request(Level9Frost.FOG, frost * 0.85f);
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        float alpha = alpha(now);
        if (alpha <= 0.003f || !inSuburb(mc)) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        float cx = (float) (pillarX + 0.5 - cam.x);
        float cz = (float) (pillarZ + 0.5 - cam.z);
        float bottom = (float) (LevelLayout.FLOOR_Y + 1 - cam.y);
        float seconds = (now % 600_000L) / 1000.0f;

        // Draw in camera-relative world space with the level's own view matrix.
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        BufferBuilder quads = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float bandHeight = HEIGHT / BANDS;
        for (int j = 0; j < BANDS; j++) {
            float y0 = bottom + j * bandHeight;
            float y1 = y0 + bandHeight;
            // Fades into the black sky at the very top.
            float a0 = alpha * Math.min(1.0f, (BANDS - j) / 3.0f);
            float a1 = alpha * Math.min(1.0f, (BANDS - j - 1) / 3.0f);
            for (int i = 0; i < SEGMENTS; i++) {
                float ang0 = i * Mth.TWO_PI / SEGMENTS;
                float ang1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
                float r00 = radius(ang0, j, seconds);
                float r10 = radius(ang1, j, seconds);
                float r01 = radius(ang0, j + 1, seconds);
                float r11 = radius(ang1, j + 1, seconds);
                float u0 = i / (float) SEGMENTS * 4.0f;
                float u1 = (i + 1) / (float) SEGMENTS * 4.0f;
                float v0 = j * bandHeight / 6.0f;
                float v1 = (j + 1) * bandHeight / 6.0f;
                quads.addVertex(cx + Mth.cos(ang0) * r00, y0, cz + Mth.sin(ang0) * r00).setUv(u0, v0).setColor(1.0f, 1.0f, 1.0f, a0);
                quads.addVertex(cx + Mth.cos(ang1) * r10, y0, cz + Mth.sin(ang1) * r10).setUv(u1, v0).setColor(1.0f, 1.0f, 1.0f, a0);
                quads.addVertex(cx + Mth.cos(ang1) * r11, y1, cz + Mth.sin(ang1) * r11).setUv(u1, v1).setColor(1.0f, 1.0f, 1.0f, a1);
                quads.addVertex(cx + Mth.cos(ang0) * r01, y1, cz + Mth.sin(ang0) * r01).setUv(u0, v1).setColor(1.0f, 1.0f, 1.0f, a1);
            }
        }
        BufferUploader.drawWithShader(quads.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    /** A slightly ragged, slowly writhing outline: smoke more than stone. */
    private static float radius(float angle, int band, float seconds) {
        return RADIUS * (1.0f + 0.07f * Mth.sin(angle * 3.0f + seconds * 0.6f + band * 0.9f)
                + 0.04f * Mth.sin(angle * 7.0f - seconds * 1.1f + band * 1.7f));
    }

    /** The quake: a heavy shake as the pillar vanishes. Runs after the handheld camera. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (quakeStartMs == 0L) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float since = System.currentTimeMillis() - quakeStartMs;
        if (since >= QUAKE_MS || mc.player == null || !inSuburb(mc)) {
            quakeStartMs = 0L;
            return;
        }
        double d = Math.sqrt(mc.player.distanceToSqr(pillarX + 0.5, mc.player.getY(), pillarZ + 0.5));
        float near = (float) Mth.clamp(1.0 - d / QUAKE_RANGE, 0.0, 1.0);
        float envelope = Math.min(1.0f, since / 250.0f) * (1.0f - since / QUAKE_MS);
        float strength = envelope * envelope * QUAKE_DEG * (0.25f + 0.75f * near);
        float t = since / 1000.0f;
        event.setPitch(event.getPitch() + (Mth.sin(t * 57.0f) * 0.6f + Mth.sin(t * 19.0f) * 0.4f) * strength);
        event.setYaw(event.getYaw() + (Mth.sin(t * 43.0f + 0.7f) * 0.5f + Mth.sin(t * 11.0f) * 0.5f) * strength * 0.7f);
        event.setRoll(event.getRoll() + (Mth.sin(t * 61.0f + 2.0f) * 0.6f + Mth.sin(t * 7.0f) * 0.4f) * strength);
    }

    private MangledPillar() {}
}
