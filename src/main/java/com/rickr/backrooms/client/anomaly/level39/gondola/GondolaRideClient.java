package com.rickr.backrooms.client.anomaly.level39.gondola;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.anomaly.level9.Level9Frost;
import com.rickr.backrooms.world.anomaly.level39.gondola.GondolaChair;
import com.rickr.backrooms.world.anomaly.level39.gondola.GondolaLine;
import com.rickr.backrooms.world.anomaly.level39.gondola.GondolaRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Riding the chair up into the whiteout (client side). As the chair climbs:
 * <ul>
 *   <li>the fog closes in and turns from the field's dark murk to pure white, until you can barely
 *       see your own chair (and the black night sky fades out behind it);</li>
 *   <li>the picture goes milky and colourless (an anomaly mood over the field's own);</li>
 *   <li>frost creeps over the picture and your breath shows;</li>
 *   <li>the wind picks up and the cable creaks; the grip clatters over each tower's sheaves; and
 *       for the last seconds a rising, airy tone, until the server drops you into Level 39.</li>
 * </ul>
 * Everything runs on the chair's ride clock ({@link GondolaChair#rideTicks}), so it lines up with
 * where the chair actually is.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class GondolaRideClient {
    /** The whiteout's colour: a cold, faintly blue white. */
    private static final float WHITE_R = 0.86f;
    private static final float WHITE_G = 0.89f;
    private static final float WHITE_B = 0.93f;
    /** At full whiteout the fog is complete this close (blocks): only your own chair shows. */
    private static final float WHITEOUT_FAR = 2.5f;
    /** The whiteout starts at this share of the ride and is total at that one. */
    private static final float WHITEOUT_FROM = 0.15f;
    private static final float WHITEOUT_FULL = 0.92f;
    /** The rising tone starts at this share of the ride (it is ~3.5 s long). */
    private static final float TONE_AT = 0.45f;
    /**
     * The whiteout as a picture mood (see AtmosphereGrade.Mood): brighter, flat, almost no colour,
     * a milky lifted black, white haze filling the distance, soft glow.
     */
    private static final AtmosphereGrade.Mood WHITEOUT = new AtmosphereGrade.Mood(1.10f, 0.80f, 0.35f,
            1.0f, 1.0f, 1.02f, 1.0f, 1.0f, 1.0f,
            0.10f, 0.105f, 0.115f, WHITE_R, WHITE_G, WHITE_B, 0.0f, 0.30f, 0.92f, 0.05f, 0.45f, 0.012f);

    private static final RandomSource RANDOM = RandomSource.create();
    @Nullable
    private static GondolaLoopSound wind;
    @Nullable
    private static GondolaLoopSound creak;
    /** The chair whose ride the sounds below belong to, how far it had got, and whether the tone has played. */
    private static int rideId = -1;
    private static double lastA;
    private static boolean tonePlayed;

    @Nullable
    private static GondolaChair riding(Minecraft mc) {
        return mc.player != null && mc.player.getVehicle() instanceof GondolaChair chair && chair.line() != null ? chair : null;
    }

    /** 0..1 how white everything is. */
    private static float whiteout(GondolaChair chair, float partialTick) {
        float t = Mth.clamp((chair.progress(partialTick) - WHITEOUT_FROM) / (WHITEOUT_FULL - WHITEOUT_FROM), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    // ==========================================================================================
    // Fog and sky
    // ==========================================================================================

    /** Last in line (after the field's own fog), so the whiteout wins. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        GondolaChair chair = riding(Minecraft.getInstance());
        if (chair == null || event.getType() != FogType.NONE) {
            return;
        }
        float w = whiteout(chair, (float) event.getPartialTick());
        if (w <= 0.0f) {
            return;
        }
        float far = Mth.lerp((float) Math.sqrt(w), event.getFarPlaneDistance(), WHITEOUT_FAR);
        event.setNearPlaneDistance(Mth.lerp(w, Math.min(event.getNearPlaneDistance(), far * 0.5f), -1.0f));
        event.setFarPlaneDistance(far);
        event.setCanceled(true); // NeoForge only uses our distances when the event is cancelled
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        GondolaChair chair = riding(Minecraft.getInstance());
        if (chair == null || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float w = whiteout(chair, (float) event.getPartialTick());
        event.setRed(Mth.lerp(w, event.getRed(), WHITE_R));
        event.setGreen(Mth.lerp(w, event.getGreen(), WHITE_G));
        event.setBlue(Mth.lerp(w, event.getBlue(), WHITE_B));
    }

    /**
     * Level 9's sky is drawn black overhead whatever the fog does, so right after it a box of the
     * whiteout colour is drawn round the camera, as opaque as the whiteout is thick.
     */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        GondolaChair chair = riding(Minecraft.getInstance());
        if (chair == null) {
            return;
        }
        float w = whiteout(chair, event.getPartialTick().getGameTimeDeltaPartialTick(false));
        if (w <= 0.0f) {
            return;
        }
        drawWhiteBox(event.getModelViewMatrix(), w);
    }

    private static void drawWhiteBox(Matrix4f view, float alpha) {
        float r = 16.0f;
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // Six faces of a cube round the eye (winding doesn't matter: culling is off).
        float[][] faces = {
                {-r, -r, -r, r, -r, -r, r, -r, r, -r, -r, r},
                {-r, r, -r, -r, r, r, r, r, r, r, r, -r},
                {-r, -r, -r, -r, r, -r, r, r, -r, r, -r, -r},
                {-r, -r, r, r, -r, r, r, r, r, -r, r, r},
                {-r, -r, -r, -r, -r, r, -r, r, r, -r, r, -r},
                {r, -r, -r, r, r, -r, r, r, r, r, -r, r},
        };
        for (float[] f : faces) {
            for (int i = 0; i < 12; i += 3) {
                buf.addVertex(f[i], f[i + 1], f[i + 2]).setColor(WHITE_R, WHITE_G, WHITE_B, alpha);
            }
        }
        MeshData mesh = buf.build();
        if (mesh == null) {
            return;
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(view);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    // ==========================================================================================
    // Mood, frost, sounds
    // ==========================================================================================

    /** After the anomaly effects have set the field's mood (LOWEST), so the whiteout's mood wins. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        GondolaChair chair = riding(mc);
        if (chair == null) {
            rideId = -1;
            return;
        }
        GondolaLine line = chair.line();
        float p = chair.progress(0.0f);
        float w = whiteout(chair, 0.0f);
        AtmosphereGrade.setAnomalyMood(WHITEOUT, w);
        Level9Frost.request(Level9Frost.WALKWAY, 0.55f + 0.4f * p);
        if (mc.isPaused()) {
            return;
        }
        double a = line.rideA(chair.rideTicks(0.0f));
        if (chair.getId() != rideId) {
            rideId = chair.getId();
            lastA = a;
            tonePlayed = false;
        }
        wind = GondolaLoopSound.feed(wind, GondolaRegistry.WIND.get(), true, 0, 0, 0, 0.25f + 0.75f * p, 0.92f + 0.16f * p);
        creak = GondolaLoopSound.feed(creak, GondolaRegistry.CABLE_CREAK.get(), true, 0, 0, 0, 0.75f - 0.35f * w, 0.95f + 0.1f * p);
        // Over a tower's sheaves: the grip clatters across the wheel train just above your head.
        for (int k = 0; k <= line.towers; k++) {
            int ta = line.towerA(k);
            if (lastA < ta && a >= ta) {
                double x = line.worldX(ta, GondolaLine.LINE_HALF);
                double z = line.worldZ(ta, GondolaLine.LINE_HALF);
                mc.getSoundManager().play(new SimpleSoundInstance(GondolaRegistry.SHEAVE_CLACK.get(), SoundSource.AMBIENT,
                        1.0f, 0.95f + RANDOM.nextFloat() * 0.1f, RANDOM, x, line.towerCableY(k), z));
            }
        }
        lastA = a;
        if (!tonePlayed && p >= TONE_AT) {
            tonePlayed = true;
            mc.getSoundManager().play(new SimpleSoundInstance(GondolaRegistry.WHITEOUT.get().getLocation(), SoundSource.AMBIENT,
                    0.9f, 1.0f, RANDOM, false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
        }
    }

    private GondolaRideClient() {}
}
