package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.camera.HandheldCamera;
import com.rickr.backrooms.client.intro.IntroCutscene;
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

    /** Wide camcorder lens (can be turned off on the SETTINGS screen). Only the world view; the held item keeps its FOV. */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov() && CamcorderEffect.wanted(Minecraft.getInstance())) {
            if (ClientSettings.WIDE_LENS.get()) {
                event.setFOV(Math.min(event.getFOV() * FOV_MULTIPLIER, MAX_FOV));
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
        if (IntroCutscene.active()) {
            IntroCutscene.modifyAngles(event);
        }
        CamcorderEffect.updateCameraUniforms(mc);
    }

    private ClientEvents() {}
}
