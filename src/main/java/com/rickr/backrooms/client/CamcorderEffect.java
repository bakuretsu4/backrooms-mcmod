package com.rickr.backrooms.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.camera.HandheldCamera;
import com.rickr.backrooms.client.intro.IntroCutscene;
import com.rickr.backrooms.client.light.BackroomsLights;
import com.rickr.backrooms.client.water.UnderwaterView;
import com.rickr.backrooms.entity.Bacteria;
import com.rickr.backrooms.entity.Smiler;
import com.rickr.backrooms.network.LevelEventPayload;
import com.rickr.backrooms.network.TransitionPayload;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Travel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Turns the whole screen into camcorder footage while you are in the Backrooms.
 *
 * <p>How it works: Minecraft already supports full-screen "post effects" (the spectator creeper /
 * enderman views). We load our own, assets/backrooms/shaders/post/camcorder.json, through
 * {@code GameRenderer.loadEffect}. Vanilla runs it every frame after the world and your hand are
 * drawn but before the HUD. The chain is: bloom (bright-pass + three blur sizes), then one big
 * pass that does the lens, motion blur, chromatic aberration, the fallback flashlight, the grade
 * and the glitch-only VHS effects. Each frame we feed it live values.
 *
 * <p>The fallback flashlight needs to know how far away each pixel is. Vanilla wipes the depth
 * buffer before drawing your hand, so right after the world finishes we copy the world's depth into
 * the effect's "scenedepth" buffer ({@link #captureDepth}).
 */
public final class CamcorderEffect {
    public static final ResourceLocation CHAIN = Backrooms.id("shaders/post/camcorder.json");
    private static final float TRANSITION_MS = 2500.0f;
    /** Waking up on the carpet after the fall (eyes opening, sitting up). */
    public static final float WAKE_MS = 4500.0f;
    private static final double DANGER_RANGE = 32.0;
    /** Smilers feed danger from this far, and the colour fringing starts at the closer range. */
    private static final double SMILER_RANGE = 24.0;
    private static final double SMILER_CHROMA_RANGE = 12.0;
    /** Film grain when nothing is going wrong. The reference footage has none; try 0.01 for "very faint". */
    private static final float BASE_GRAIN = 0.0f;
    /** Lightning: how long the white flash takes to die away (ms). */
    private static final float LIGHTNING_MS = 900.0f;

    /** Smoothed 0..1 "something is near" value; also read by the HUD and camera shake. */
    private static float danger;
    private static float dangerTarget;
    /** Smoothed 0..1 colour fringing from a nearby Smiler (the shader's Chroma uniform). */
    private static float chroma;
    private static boolean loadFailed;

    public static float danger() {
        return danger;
    }

    /** In the Backrooms, or filming the real world during the noclip intro. */
    public static boolean wanted(Minecraft mc) {
        return mc.level != null && mc.player != null && (Travel.inBackrooms(mc.level) || IntroCutscene.filming());
    }

    /** 1 at the moment you wake up on the carpet, easing to 0 over {@link #WAKE_MS}. */
    public static float wakeAmount() {
        if (CameraClientState.transitionKind != TransitionPayload.WAKE_UP) {
            return 0.0f;
        }
        long since = System.currentTimeMillis() - CameraClientState.transitionStartMs;
        return since < 0 || since > WAKE_MS ? 0.0f : 1.0f - since / WAKE_MS;
    }

    private static PostChain activeChain(Minecraft mc) {
        PostChain chain = mc.gameRenderer.currentEffect();
        return chain != null && CHAIN.toString().equals(chain.getName()) ? chain : null;
    }

    /** Once per client tick: make sure the effect is on exactly when we're in the Backrooms. */
    public static void tick(Minecraft mc) {
        boolean want = wanted(mc);
        boolean active = activeChain(mc) != null;
        if (want && !active && !loadFailed) {
            // Vanilla clears post effects whenever the camera entity changes (respawn, dimension
            // change), so re-apply whenever it's missing.
            mc.gameRenderer.loadEffect(CHAIN);
            loadFailed = activeChain(mc) == null;
        } else if (!want && active) {
            mc.gameRenderer.shutdownEffect();
        }

        dangerTarget = 0.0f;
        float chromaTarget = 0.0f;
        if (want) {
            for (Bacteria bacteria : mc.level.getEntitiesOfClass(Bacteria.class, mc.player.getBoundingBox().inflate(DANGER_RANGE))) {
                float closeness = (float) (1.0 - bacteria.distanceTo(mc.player) / DANGER_RANGE);
                float value = Mth.clamp(closeness, 0.0f, 1.0f) * bacteria.dangerWeight();
                dangerTarget = Math.max(dangerTarget, value);
            }
            // Level 9: Watchers (near / staring / charging) and Observers (a little unease).
            dangerTarget = Math.max(dangerTarget, WatcherInterference.danger());
            // Smilers: the usual danger, plus colour fringing that gets strong when one is close.
            for (Smiler smiler : mc.level.getEntitiesOfClass(Smiler.class, mc.player.getBoundingBox().inflate(SMILER_RANGE))) {
                float distance = smiler.distanceTo(mc.player);
                float closeness = Mth.clamp(1.0f - distance / (float) SMILER_RANGE, 0.0f, 1.0f);
                dangerTarget = Math.max(dangerTarget, closeness * smiler.dangerWeight());
                float near = Mth.clamp(1.0f - distance / (float) SMILER_CHROMA_RANGE, 0.0f, 1.0f);
                chromaTarget = Math.max(chromaTarget, near * near * smiler.dangerWeight());
            }
        }
        danger += (dangerTarget - danger) * (dangerTarget > danger ? 0.25f : 0.05f);
        chroma += (chromaTarget - chroma) * (chromaTarget > chroma ? 0.3f : 0.08f);
    }

    /** Resource reloads (F3+T) rebuild shaders, so allow another attempt. */
    public static void onResourcesReloaded() {
        loadFailed = false;
    }

    /** Called right after the world is drawn, before vanilla clears depth for the hand. */
    public static void captureDepth(Minecraft mc) {
        PostChain chain = activeChain(mc);
        if (chain == null) {
            return;
        }
        RenderTarget main = mc.getMainRenderTarget();
        RenderTarget sceneDepth = chain.getTempTarget("scenedepth");
        if (sceneDepth != null) {
            sceneDepth.copyDepthFrom(main);
            main.bindWrite(false);
        }
    }

    /** Once per frame, before rendering: push live values into the shader. */
    public static void updateUniforms(Minecraft mc) {
        PostChain chain = activeChain(mc);
        if (chain == null) {
            return;
        }
        long now = System.currentTimeMillis();
        float seconds = (float) ((System.nanoTime() / 1.0e9) % 3600.0);
        long sinceTransition = now - CameraClientState.transitionStartMs;
        float transition = sinceTransition >= 0 && sinceTransition < TRANSITION_MS ? 1.0f - sinceTransition / TRANSITION_MS : 0.0f;
        transition = Math.max(transition, IntroCutscene.glitch());
        boolean dead = CameraClientState.battery <= 0.0f;
        // When Veil's real dynamic lights are on, the flashlight is a real light in the world, so
        // the screen-space fake one must stay off or the beam would be doubled.
        boolean fakeFlashlight = CameraClientState.flashlight && !dead && !BackroomsLights.isVeilLightingActive();

        chain.setUniform("Seconds", seconds);
        chain.setUniform("Danger", danger);
        chain.setUniform("Battery", CameraClientState.battery / 100.0f);
        chain.setUniform("NightShot", CameraClientState.nightShot ? 1.0f : 0.0f);
        chain.setUniform("Flashlight", fakeFlashlight ? flashlightFlicker(seconds) : 0.0f);
        chain.setUniform("Signal", CameraClientState.exitSignal);
        chain.setUniform("Transition", transition);
        chain.setUniform("Wake", wakeAmount());
        chain.setUniform("Alarm", alarmPulse(now));
        chain.setUniform("Flash", lightningFlash(now));
        chain.setUniform("Grain", BASE_GRAIN);
        // Poolrooms hallucination (0 = picture exactly unchanged).
        chain.setUniform("Dream", HallucinationClientEvents.shaderDream());
        // Eyes under the pool water (Poolrooms / Level 37): gentle sway (0 = picture exactly unchanged).
        chain.setUniform("Underwater", UnderwaterView.shaderUnderwater());
        // Level 9: electronics going haywire near a Watcher, and the fog draining colour with distance.
        chain.setUniform("Interference", WatcherInterference.interference());
        chain.setUniform("FogDrain", BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_9 ? 1.0f : 0.0f);
        // Low sanity: tape wear, desaturation and heartbeat edge darkening (0 = picture exactly unchanged).
        chain.setUniform("Sanity", SanityClientEffects.shaderSanity());
        chain.setUniform("SanityPulse", SanityClientEffects.heartbeatPulse());
        chain.setUniform("SanityGlitch", SanityClientEffects.vanishGlitch());
        // Smilers: strong colour fringing when one is close (0 = picture exactly unchanged).
        chain.setUniform("Chroma", chroma);
        // The level's mood: grade, haze, contact shadows, glow and breath (see AtmosphereGrade).
        AtmosphereGrade.apply(mc, chain);
        chain.setUniform("Near", GameRenderer.PROJECTION_Z_NEAR);
        chain.setUniform("Far", mc.gameRenderer.getDepthFar());
    }

    /**
     * Called right after {@link HandheldCamera} has picked this frame's view angles, so the motion
     * blur and flashlight lag match the picture being drawn (not last frame's).
     */
    public static void updateCameraUniforms(Minecraft mc) {
        PostChain chain = activeChain(mc);
        if (chain == null) {
            return;
        }
        chain.setUniform("BlurX", HandheldCamera.blurU());
        chain.setUniform("BlurY", HandheldCamera.blurV());
        chain.setUniform("FlashX", HandheldCamera.flashX());
        chain.setUniform("FlashY", HandheldCamera.flashY());
    }

    private static boolean eventActive(int kind, long now) {
        return CameraClientState.eventKind == kind && now < CameraClientState.eventEndMs;
    }

    /** 0..1 pulse for the faint red wash during the alarm event (the red light itself is the lighting's job). */
    private static float alarmPulse(long now) {
        if (!eventActive(LevelEventPayload.ALARM, now)) {
            return 0.0f;
        }
        return (float) (0.75 + 0.25 * Math.sin(now / 1000.0 * Math.PI * 1.2));
    }

    /** Lightning: two quick white pops, then a fade. 0..1. */
    private static float lightningFlash(long now) {
        if (CameraClientState.eventKind != LevelEventPayload.LIGHTNING) {
            return 0.0f;
        }
        long since = now - CameraClientState.eventStartMs;
        if (since < 0 || since > LIGHTNING_MS) {
            return 0.0f;
        }
        if (since < 90) {
            return 1.0f;
        }
        if (since < 170) {
            return 0.15f; // the brief dark gap between the two pops
        }
        return 0.8f * (1.0f - (since - 170) / (LIGHTNING_MS - 170.0f));
    }

    /** A cheap flashlight: it browns out for an instant now and then, more when the battery is low. */
    private static float flashlightFlicker(float seconds) {
        float lowBattery = CameraClientState.battery < 15.0f ? 1.0f : 0.25f;
        float n = Mth.frac(Mth.sin(Mth.floor(seconds * 14.0f) * 12.9898f) * 43758.547f);
        return n > 1.0f - 0.03f * lowBattery ? 0.35f : 1.0f;
    }

    private CamcorderEffect() {}
}
