package com.rickr.backrooms.client;

import com.rickr.backrooms.config.ClientSettings;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.camera.HandheldCamera;
import com.rickr.backrooms.client.anomaly.level2.Level2Spin;
import com.rickr.backrooms.client.intro.NoclipTransition;
import com.rickr.backrooms.client.light.BackroomsLights;
import com.rickr.backrooms.client.water.UnderwaterView;
import com.rickr.backrooms.entity.Bacteria;
import com.rickr.backrooms.entity.Smiler;
import com.rickr.backrooms.network.LevelEventPayload;
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
 *
 * <p>The FOOTAGE LOOK setting makes it look like a cheap old camera's footage (the shader's footage
 * stage), and the 4:3 FRAME setting puts it between black bars ({@link #framed}). The camera's
 * auto-exposure is worked out here: it chases how bright it is around you on a spring that
 * overshoots, so walking from a dark corridor into a lit room the picture flares, dips and settles
 * (it "hunts"); in the dark it turns its gain up, and the shader's noise with it.
 */
public final class CamcorderEffect {
    /** All our chains live here: camcorder.json (HIGH), camcorder_medium.json, camcorder_low.json. */
    private static final String CHAIN_PREFIX = Backrooms.id("shaders/post/camcorder").toString();
    private static final float TRANSITION_MS = 2500.0f;
    private static final double DANGER_RANGE = 32.0;
    /** Smilers feed danger from this far, and the colour fringing starts at the closer range. */
    private static final double SMILER_RANGE = 24.0;
    private static final double SMILER_CHROMA_RANGE = 12.0;
    /** Film grain when nothing is going wrong. The reference footage has none; try 0.01 for "very faint". */
    private static final float BASE_GRAIN = 0.0f;
    /** Lightning: how long the white flash takes to die away (ms). */
    private static final float LIGHTNING_MS = 900.0f;
    /**
     * Level 2's peripheral swirl: how far the picture's corners turn (radians) at full spin
     * strength and comfort scale, back and forth with the spin; the middle never turns.
     */
    private static final float SPIN_CORNER_RAD = (float) Math.toRadians(7.0);
    /**
     * Level 2's "walls closing in": at each heartbeat (SanityClientEffects, 70 BPM when calm, up to
     * 160 in a panic) the edges of the picture are drawn this much further in (4% at the corners,
     * nothing in the middle), softened so the beat swells instead of snapping.
     */
    private static final float SPIN_SQUEEZE = 0.04f;
    /** How fast the squeeze follows the heartbeat (per second): ~0.1 s, a swell, not a jolt. */
    private static final float SPIN_SQUEEZE_FOLLOW = 10.0f;
    /** The tunnel vignette's slow pulse: +/-12% of its strength over ~7 s. */
    private static final float SPIN_VIGNETTE_PULSE = 0.12f;
    private static final float SPIN_VIGNETTE_PULSE_SECONDS = 7.0f;
    private static float squeeze;
    private static long squeezeNanos;

    /** Smoothed 0..1 "something is near" value; also read by the HUD and camera shake. */
    private static float danger;
    private static float dangerTarget;
    /** Smoothed 0..1 colour fringing from a nearby Smiler (the shader's Chroma uniform). */
    private static float chroma;
    /** The chain that failed to load (bad shader edit): not retried every tick until resources reload. */
    private static String failedChain;

    // ---- The footage look's auto-exposure ---------------------------------------------------------
    /** Exposure the camera wants in full light, and in the dark (it opens up, never quite enough). */
    private static final float EXPOSURE_BRIGHT = 1.0f;
    private static final float EXPOSURE_DARK = 1.5f;
    /** The spring (per second squared) and its damping (per second): under-damped, so it hunts. */
    private static final float EXPOSURE_SPRING = 7.0f;
    private static final float EXPOSURE_DAMPING = 2.4f;
    private static float exposure = EXPOSURE_BRIGHT;
    private static float exposureSpeed;
    private static long exposureNanos;

    public static float danger() {
        return danger;
    }

    /** In the Backrooms, noclipping into them, or filming the real world with a camcorder. */
    public static boolean wanted(Minecraft mc) {
        return mc.level != null && mc.player != null
                && (Travel.inBackrooms(mc.level) || NoclipTransition.active() || CameraClientState.realRecording);
    }

    /** True when the picture is in the 4:3 frame right now: the HUD then keeps inside it (OsdStyle). */
    public static boolean framed(Minecraft mc) {
        return ClientSettings.FOOTAGE_LOOK.get() && ClientSettings.FRAME_4_3.get() && wanted(mc);
    }

    /** The post chain for the QUALITY setting; they differ only in how many glow (blur) passes they run. */
    private static ResourceLocation wantedChain() {
        return Backrooms.id("shaders/post/" + ClientSettings.quality().postChain + ".json");
    }

    private static PostChain activeChain(Minecraft mc) {
        PostChain chain = mc.gameRenderer.currentEffect();
        return chain != null && chain.getName().startsWith(CHAIN_PREFIX) ? chain : null;
    }

    /** Once per client tick: make sure the right effect is on exactly when we're in the Backrooms. */
    public static void tick(Minecraft mc) {
        boolean want = wanted(mc);
        PostChain active = activeChain(mc);
        String chain = wantedChain().toString();
        if (want && (active == null || !active.getName().equals(chain)) && !chain.equals(failedChain)) {
            // Vanilla clears post effects whenever the camera entity changes (respawn, dimension
            // change), so re-apply whenever it's missing; a QUALITY change swaps the chain.
            mc.gameRenderer.loadEffect(wantedChain());
            failedChain = activeChain(mc) == null ? chain : null;
        } else if (!want && active != null) {
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
            // Level 490: the creature close behind you, or a hand coming down near you.
            dangerTarget = Math.max(dangerTarget, Level490Creature.danger());
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
        failedChain = null;
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
        // On disconnect the level and player are gone a frame before the tick drops the effect;
        // the uniforms below read both, so skip that last frame instead of crashing.
        if (chain == null || mc.level == null || mc.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        float seconds = (float) ((System.nanoTime() / 1.0e9) % 3600.0);
        long sinceEscape = now - CameraClientState.escapeStartMs;
        float transition = sinceEscape >= 0 && sinceEscape < TRANSITION_MS ? 1.0f - sinceEscape / TRANSITION_MS : 0.0f;
        // Filming the dark in the real world: the tape breaks up more and more as reality thins.
        if (CameraClientState.realRecording) {
            transition = Math.max(transition, CameraClientState.realThin * CameraClientState.realThin);
        }
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
        chain.setUniform("Alarm", alarmPulse(now));
        // Level 1's lights-out under a shader pack, which still lights the world by the dead tubes'
        // block light: the picture itself is pressed toward black (0 = picture exactly unchanged).
        chain.setUniform("Blackout", Level1Atmosphere.screenCrush());
        // Penance has its own lightning (Level490Lightning): the picture flashes as much as the strike is near.
        chain.setUniform("Flash", BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_490
                ? Level490Lightning.flash() * 0.6f : lightningFlash(now));
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
        // The footage look (a setting): on/off, the 4:3 frame, and the camera's hunting auto-exposure.
        boolean footage = ClientSettings.FOOTAGE_LOOK.get();
        float autoExposure = footage && !CameraClientState.nightShot ? autoExposure(mc) : 1.0f;
        chain.setUniform("Footage", footage ? 1.0f : 0.0f);
        chain.setUniform("Bars", ClientSettings.FRAME_4_3.get() ? 1.0f : 0.0f);
        chain.setUniform("AutoExposure", autoExposure);
        chain.setUniform("Gain", Mth.clamp((autoExposure - EXPOSURE_BRIGHT) / (EXPOSURE_DARK - EXPOSURE_BRIGHT), 0.0f, 1.0f));
        chain.setUniform("Near", GameRenderer.PROJECTION_Z_NEAR);
        chain.setUniform("Far", mc.gameRenderer.getDepthFar());
        updateSpinUniforms(mc, chain);
    }

    /**
     * Level 2's spin in the picture (0 everywhere else):
     * <ul>
     *   <li>SpinTwist: the corners turn back and forth with the shared spin phase (two harmonics)
     *       and lean further while the turning surges or lurches back (its rate), so the walls at
     *       the edges of the screen visibly slide round you; the middle stays put.</li>
     *   <li>SpinSqueeze: the edges are drawn in with every heartbeat.</li>
     *   <li>SpinVignette: tunnel vision, the edges near black, pulsing slowly.</li>
     * </ul>
     * The swirl, the squeeze and the vignette's pulse are motion, so they take the comfort scale;
     * the vignette itself doesn't move and stays at tilt 0.
     */
    private static void updateSpinUniforms(Minecraft mc, PostChain chain) {
        long now = System.nanoTime();
        float dt = squeezeNanos == 0L ? 0.0f : Math.min((now - squeezeNanos) / 1.0e9f, 0.1f);
        squeezeNanos = now;
        float twist = 0.0f;
        float squeezeGoal = 0.0f;
        float vignette = 0.0f;
        if (Level2Spin.active()) {
            float strength = Level2Spin.strength();
            float tilt = ClientSettings.tilt();
            float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);
            double phase = Level2Spin.phase(partialTick);
            double lurch = Level2Spin.spinRate(partialTick) / Level2Spin.baseRate() - 1.0;
            double swirl = 0.55 * Math.sin(2.0 * phase + 0.6) + 0.30 * Math.sin(3.236 * phase + 2.0) + 0.35 * lurch;
            twist = SPIN_CORNER_RAD * strength * tilt * (float) Mth.clamp(swirl, -1.0, 1.0);
            squeezeGoal = SPIN_SQUEEZE * strength * tilt * SanityClientEffects.heartbeatPulse();
            double seconds = (mc.level.getGameTime() + partialTick) / 20.0;
            vignette = strength * (1.0f + SPIN_VIGNETTE_PULSE * tilt
                    * (float) Math.sin(seconds * Math.PI * 2.0 / SPIN_VIGNETTE_PULSE_SECONDS));
        }
        squeeze += (squeezeGoal - squeeze) * Math.min(1.0f, SPIN_SQUEEZE_FOLLOW * dt);
        chain.setUniform("SpinTwist", twist);
        chain.setUniform("SpinSqueeze", squeeze);
        chain.setUniform("SpinVignette", vignette);
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

    /**
     * The camera's auto-exposure this frame: it wants to open up in the dark and close down in
     * the light (how bright it is at your eyes), and chases that on an under-damped spring, so it
     * overshoots and settles a moment later.
     */
    private static float autoExposure(Minecraft mc) {
        long now = System.nanoTime();
        float dt = exposureNanos == 0L ? 0.0f : Math.min((now - exposureNanos) / 1.0e9f, 0.1f);
        exposureNanos = now;
        net.minecraft.core.BlockPos eyes = net.minecraft.core.BlockPos.containing(mc.player.getEyePosition());
        int light = Math.max(mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, eyes),
                mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, eyes));
        // Level 37's rooms are evenly lit from light 6 up, so it meters the fill there, not the raw
        // light level (which held the camera 13-30% too open in perfectly bright rooms).
        float bright = Level37Atmosphere.metersByFill(mc) ? Level37Atmosphere.litShare(light)
                : Mth.clamp((light - 2.0f) / 9.0f, 0.0f, 1.0f);
        float target = Mth.lerp(bright * bright * (3.0f - 2.0f * bright), EXPOSURE_DARK, EXPOSURE_BRIGHT);
        exposureSpeed += (EXPOSURE_SPRING * (target - exposure) - EXPOSURE_DAMPING * exposureSpeed) * dt;
        exposure = Mth.clamp(exposure + exposureSpeed * dt, 0.75f, 1.8f);
        return exposure;
    }

    /** A cheap flashlight: it browns out for an instant now and then, more when the battery is low. */
    private static float flashlightFlicker(float seconds) {
        float lowBattery = CameraClientState.battery < 15.0f ? 1.0f : 0.25f;
        float n = Mth.frac(Mth.sin(Mth.floor(seconds * 14.0f) * 12.9898f) * 43758.547f);
        return n > 1.0f - 0.03f * lowBattery ? 0.35f : 1.0f;
    }

    private CamcorderEffect() {}
}
