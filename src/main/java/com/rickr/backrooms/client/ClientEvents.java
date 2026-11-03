package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.anomaly.level2.Level2Spin;
import com.rickr.backrooms.client.camera.HandheldCamera;
import com.rickr.backrooms.config.ClientSettings;
import com.rickr.backrooms.network.CameraTogglePayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-only per-tick / per-frame hooks (game event bus): keys, the camcorder post effect and
 * the handheld camera. HUD tweaks live in {@code client/hud/BackroomsHudEvents}.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    /** World FOV multiplier in the Backrooms (wide camcorder lens), capped at {@link #MAX_FOV}. */
    private static final double FOV_MULTIPLIER = 1.2;
    private static final double MAX_FOV = 110.0;
    /**
     * Level 2: the lens closes in by this share at full spin strength (everywhere in the corridor;
     * half at the exit), on top of the wide lens, so the walls press in: a 70 degree setting with
     * the wide lens (84) ends up near 67.
     */
    private static final double L2_FOV_NARROW = 0.20;
    /** ...and breathes by this share (+/-3.5%) over a slow, uneven ~11 s cycle. */
    private static final double L2_FOV_BREATH = 0.035;
    private static final double L2_FOV_BREATH_SECONDS = 11.0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        CamcorderEffect.tick(mc);
        HandheldCamera.tick(mc);
        boolean inBackrooms = CamcorderEffect.wanted(mc);

        // F5 is blocked: the Backrooms are filmed from your own eyes only.
        if (inBackrooms && mc.options.getCameraType() != CameraType.FIRST_PERSON) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }

        while (ClientSetup.NIGHT_SHOT_KEY.consumeClick()) {
            if (inBackrooms) {
                PacketDistributor.sendToServer(new CameraTogglePayload(CameraTogglePayload.NIGHT_SHOT));
            }
        }
        while (ClientSetup.FLASHLIGHT_KEY.consumeClick()) {
            if (inBackrooms) {
                PacketDistributor.sendToServer(new CameraTogglePayload(CameraTogglePayload.FLASHLIGHT));
            }
        }
        while (ClientSetup.OSD_KEY.consumeClick()) {
            CamcorderHud.toggleOsd();
        }
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        CamcorderEffect.updateUniforms(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            CamcorderEffect.captureDepth(Minecraft.getInstance());
        }
    }

    /**
     * Wide camcorder lens (can be turned off on the SETTINGS screen). Only the world view; the held
     * item keeps its FOV. In Level 2 the lens then narrows a little and breathes with the spin
     * (times the comfort scale, so tilt 0 leaves the lens exactly as set).
     */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov() && CamcorderEffect.wanted(Minecraft.getInstance())) {
            if (ClientSettings.WIDE_LENS.get()) {
                event.setFOV(Math.min(event.getFOV() * FOV_MULTIPLIER, MAX_FOV));
            }
            if (Level2Spin.active()) {
                float squeeze = Level2Spin.strength() * ClientSettings.tilt();
                if (squeeze > 0.0f) {
                    float partialTick = (float) event.getPartialTick();
                    double seconds = (Minecraft.getInstance().level.getGameTime() + partialTick) / 20.0;
                    double breath = 0.7 * Math.sin(seconds * Math.PI * 2.0 / L2_FOV_BREATH_SECONDS)
                            + 0.3 * Math.sin(Level2Spin.phase(partialTick) * 3.0);
                    event.setFOV(event.getFOV() * (1.0 - squeeze * (L2_FOV_NARROW + L2_FOV_BREATH * breath)));
                }
            }
            // The motion blur needs the real FOV to turn degrees of rotation into screen distance.
            HandheldCamera.setFov(event.getFOV());
        }
    }

    /** No sprint / speed FOV kick in the Backrooms: a camcorder lens doesn't zoom when you run. */
    @SubscribeEvent
    public static void onFovModifier(ComputeFovModifierEvent event) {
        if (CamcorderEffect.wanted(Minecraft.getInstance())) {
            event.setNewFovModifier(1.0f);
        }
    }

    /** Handheld camera: smoothed look, speed-driven shake, turn/strafe roll, events, wake-up intro. */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (!CamcorderEffect.wanted(mc)) {
            return;
        }
        HandheldCamera.apply(mc, event);
        Level7Client.modifyAngles(event);
        CamcorderEffect.updateCameraUniforms(mc);
    }

    private ClientEvents() {}
}
