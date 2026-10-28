package com.rickr.backrooms.client.camera;

import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.config.ClientSettings;
import com.rickr.backrooms.client.CamcorderEffect;
import com.rickr.backrooms.network.LevelEventPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The "someone is holding this camera" feel. Only the rendered view moves; your real aim, the
 * server and other players never see any of it.
 *
 * <ul>
 *   <li><b>Smoothed look:</b> the view follows the mouse with a little lag, like a heavy camcorder.</li>
 *   <li><b>Handheld shake:</b> smooth noise wobble whose strength ("trauma") follows your speed:
 *       a gentle ~1 degree sway when standing, up to ~9 degrees of roll when running.</li>
 *   <li><b>Roll:</b> the horizon tilts into turns and strafes.</li>
 *   <li><b>Events:</b> Level 2's TWIST event slowly leans the world ~3.5 degrees and back; Bacteria
 *       nearby makes your hands shake; the wake-up intro lies you on the carpet.</li>
 * </ul>
 *
 * <p>All easing uses {@link #ease}, an exponential follow measured in game ticks, so the feel is
 * identical at 30 or 300 FPS.
 */
public final class HandheldCamera {
    // --- Smoothed look -------------------------------------------------------------------------
    // How heavy it is comes from the SETTINGS screen: ClientSettings.lookSmoothing() is the fraction
    // of the gap between view and aim still left after one tick (0 = instant, 0.7 was the old feel).

    // --- Handheld shake ------------------------------------------------------------------------
    /** Peak shake angle in degrees at intensity 1. Kept small: big wobble while running gets tiring fast. */
    private static final float SHAKE_AMPLITUDE_DEG = 2.0f;
    /** Roll shakes a bit more than pitch/yaw; it is what reads as "handheld". */
    private static final float SHAKE_ROLL_MULT = 1.2f;
    /** Trauma and noise speed ease toward their goals leaving 93% of the gap per tick (~0.7 s). */
    private static final float TRAUMA_SMOOTHING = 0.93f;
    /** Trauma bounds: 0.5 standing (~0.5 degree breathing), 0.8 max (~1.3 degrees when sprinting). */
    private static final float TRAUMA_MIN = 0.5f;
    private static final float TRAUMA_MAX = 0.8f;
    /** How much movement speed raises trauma (speed in blocks/tick x6). */
    private static final float TRAUMA_PER_SPEED = 0.35f;
    /** Fastest the shake noise may move; lower = slow sway instead of jitter. */
    private static final float MAX_NOISE_SPEED = 0.5f;
    /** How far along the noise curve we travel per tick at noise speed 1 (lower = slower sway). */
    private static final float NOISE_FREQ = 0.6f;
    /** Extra trauma added at full danger (Bacteria right next to you). */
    private static final float DANGER_TRAUMA = 0.8f;

    // --- Roll ----------------------------------------------------------------------------------
    /** Roll follows its goal leaving 80% of the gap per tick. */
    private static final float ROLL_SMOOTHING = 0.8f;
    /** Degrees of roll per degree-per-tick of turning. */
    private static final float TURN_ROLL_PER_DEG = 0.3f;
    /** Degrees of roll per block-per-tick of sideways movement (strafe speed ~0.2 -> ~3 degrees). */
    private static final float STRAFE_ROLL_PER_BLOCK = 15.0f;
    /** Turn roll and strafe roll are each clamped to this many degrees. */
    private static final float ROLL_LIMIT_DEG = 7.0f;

    // --- TWIST event (Level 2) -----------------------------------------------------------------
    /**
     * How far the corridor leans at the height of the event. Was 20 degrees (the whole view tipped
     * over); now a barely-there 3.5 degrees: you notice the pipes aren't level any more before you
     * notice the camera moved, which is the unsettling part.
     */
    private static final float TWIST_ROLL_DEG = 3.5f;
    /** Ramp time at the start and end of the event: a slow lean in and out, never a jolt. */
    private static final float TWIST_RAMP_MS = 5000.0f;
    private static final float TWIST_SMOOTHING = 0.9f;

    // --- Motion blur and fallback flashlight lag -------------------------------------------------
    /** Virtual shutter time in seconds: blur length = rotation speed x shutter. 1/40 s reads "camcorder". */
    private static final float SHUTTER_SECONDS = 1.0f / 40.0f;
    /** Longest blur streak, as a fraction of the screen. */
    private static final float MAX_BLUR_UV = 0.08f;
    /** Rotations bigger than this in one frame are teleports / respawns: no blur. */
    private static final float SNAP_DEG = 60.0f;
    /** The flashlight beam catches up with your aim at 0.7 x frame-length-in-ticks per frame. */
    private static final float BEAM_FOLLOW_PER_TICK = 0.7f;

    // Per-tick movement (from the client tick).
    private static float speedPerTick;
    private static float sidewaysPerTick;

    // Per-frame state.
    private static boolean initialised;
    private static long lastFrameNs;
    private static float smoothYaw;
    private static float smoothPitch;
    private static float trauma = TRAUMA_MIN;
    private static float noiseSpeed = 0.1f;
    private static float noiseY;
    private static float turnRoll;
    private static float strafeRoll;
    private static float twist;
    private static float lastRenderYaw;
    private static float lastRenderPitch;
    private static float beamYaw;
    private static float beamPitch;
    private static float blurU;
    private static float blurV;
    private static float flashX;
    private static float flashY;
    /** Vertical field of view of the world render, degrees (updated from ComputeFov). */
    private static float fovDeg = 70.0f;

    /** Once per client tick: measure how fast we move and how much of it is sideways. */
    public static void tick(Minecraft mc) {
        Player player = mc.player;
        if (player == null || !CamcorderEffect.wanted(mc)) {
            reset();
            return;
        }
        double dx = player.getX() - player.xo;
        double dz = player.getZ() - player.zo;
        float speed = (float) Math.sqrt(dx * dx + dz * dz);
        if (speed > 2.0f) { // teleported, not walking
            dx = dz = speed = 0.0f;
        }
        // Your right-hand direction for yaw y is (-cos y, -sin y) in Minecraft's X/Z.
        float yawRad = player.getYRot() * Mth.DEG_TO_RAD;
        speedPerTick = speed;
        sidewaysPerTick = (float) (dx * -Mth.cos(yawRad) + dz * -Mth.sin(yawRad));
    }

    public static void setFov(double fov) {
        fovDeg = (float) fov;
    }

    /** Screen-space blur vector (texture units) for the shader's rotational motion blur. */
    public static float blurU() {
        return blurU;
    }

    public static float blurV() {
        return blurV;
    }

    /** Where the lagging flashlight beam points, relative to the screen centre (texture units). */
    public static float flashX() {
        return flashX;
    }

    public static float flashY() {
        return flashY;
    }

    private static void reset() {
        initialised = false;
        lastFrameNs = 0L;
        speedPerTick = sidewaysPerTick = 0.0f;
        trauma = TRAUMA_MIN;
        noiseSpeed = 0.1f;
        turnRoll = strafeRoll = twist = 0.0f;
        blurU = blurV = flashX = flashY = 0.0f;
    }

    /** Exponential follow: the fraction of the gap to close this frame, given "gap left per tick". */
    private static float ease(float leftPerTick, float dTicks) {
        return 1.0f - (float) Math.pow(leftPerTick, dTicks);
    }

    /** Once per frame: replace the view angles with the handheld ones. */
    public static void apply(Minecraft mc, ViewportEvent.ComputeCameraAngles event) {
        long now = System.nanoTime();
        float dt = lastFrameNs == 0L ? 0.0f : Math.min((now - lastFrameNs) / 1.0e9f, 0.25f);
        lastFrameNs = now;
        float dTicks = dt * 20.0f;

        float aimYaw = event.getYaw();
        float aimPitch = event.getPitch();
        if (!initialised) {
            smoothYaw = beamYaw = lastRenderYaw = aimYaw;
            smoothPitch = beamPitch = lastRenderPitch = aimPitch;
            initialised = true;
        }

        // Smoothed look. Yaw is wrapped so a turn past 180 degrees goes the short way round.
        float prevSmoothYaw = smoothYaw;
        float follow = ease(ClientSettings.lookSmoothing(), dTicks);
        smoothYaw += Mth.wrapDegrees(aimYaw - smoothYaw) * follow;
        smoothYaw = aimYaw - Mth.wrapDegrees(aimYaw - smoothYaw); // keep it near the aim's number range
        smoothPitch += (aimPitch - smoothPitch) * follow;

        // Turn roll from how fast the view yaws (degrees per tick), strafe roll from sideways speed.
        float yawPerTick = dTicks > 1.0e-3f ? Mth.wrapDegrees(smoothYaw - prevSmoothYaw) / dTicks : 0.0f;
        float rollEase = ease(ROLL_SMOOTHING, dTicks);
        float tilt = ClientSettings.tilt();
        turnRoll += (Mth.clamp(yawPerTick * TURN_ROLL_PER_DEG, -ROLL_LIMIT_DEG, ROLL_LIMIT_DEG) * tilt - turnRoll) * rollEase;
        strafeRoll += (Mth.clamp(sidewaysPerTick * STRAFE_ROLL_PER_BLOCK, -ROLL_LIMIT_DEG, ROLL_LIMIT_DEG) * tilt - strafeRoll) * rollEase;

        // Handheld shake: trauma grows gently with speed (capped low), intensity = trauma^2.
        float danger = CamcorderEffect.danger();
        float speed6 = speedPerTick * 6.0f;
        float traumaGoal = Mth.clamp(TRAUMA_PER_SPEED * speed6, TRAUMA_MIN, TRAUMA_MAX) + danger * danger * DANGER_TRAUMA;
        float noiseSpeedGoal = Mth.clamp(0.2f * speed6, 0.1f, MAX_NOISE_SPEED);
        float traumaEase = ease(TRAUMA_SMOOTHING, dTicks);
        trauma += (traumaGoal - trauma) * traumaEase;
        noiseSpeed += (noiseSpeedGoal - noiseSpeed) * traumaEase;
        noiseY += noiseSpeed * dTicks * NOISE_FREQ;
        if (noiseY > 10000.0f) {
            noiseY -= 10000.0f; // keep float precision
        }
        float shake = ClientSettings.shake();
        float intensity = trauma * trauma * SHAKE_AMPLITUDE_DEG * shake;
        float shakePitch = GradientNoise.sample(1, noiseY) * intensity;
        float shakeYaw = GradientNoise.sample(73, noiseY) * intensity;
        float shakeRoll = GradientNoise.sample(146, noiseY) * intensity * SHAKE_ROLL_MULT;

        // Shaky hands when something is close: fast small jitter on top of the wobble.
        float t = (float) ((now / 1.0e9) % 3600.0);
        float jitter = danger * danger * shake;
        shakeRoll += Mth.sin(t * 23.0f) * 1.2f * jitter;
        shakeYaw += Mth.sin(t * 31.0f) * 0.8f * jitter;
        shakePitch += Mth.cos(t * 27.0f) * 0.8f * jitter;

        twist += (twistGoal() - twist) * ease(TWIST_SMOOTHING, dTicks);

        float yaw = smoothYaw + shakeYaw;
        float pitch = smoothPitch + shakePitch;
        float roll = event.getRoll() + turnRoll + strafeRoll + shakeRoll + twist * TWIST_ROLL_DEG;

        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setRoll(roll);

        updateBlurAndBeam(mc, yaw, pitch, aimYaw, aimPitch, dt, dTicks);
    }

    /** 0 normally; ramps to +/-1 during the Level 2 TWIST event and back before it ends. */
    private static float twistGoal() {
        long ms = System.currentTimeMillis();
        if (CameraClientState.eventKind != LevelEventPayload.TWIST || ms >= CameraClientState.eventEndMs) {
            return 0.0f;
        }
        float in = (ms - CameraClientState.eventStartMs) / TWIST_RAMP_MS;
        float out = (CameraClientState.eventEndMs - ms) / TWIST_RAMP_MS;
        float x = Mth.clamp(Math.min(in, out), 0.0f, 1.0f);
        // Alternate the direction from one event to the next.
        float sign = ((CameraClientState.eventStartMs / 1000L) & 1L) == 0L ? 1.0f : -1.0f;
        return sign * x * x * (3.0f - 2.0f * x);
    }

    private static void updateBlurAndBeam(Minecraft mc, float yaw, float pitch, float aimYaw, float aimPitch, float dt, float dTicks) {
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        float tanV = (float) Math.tan(fovDeg * 0.5f * Mth.DEG_TO_RAD);
        float tanH = tanV * aspect;

        // Rotational motion blur: how far the picture would smear during the virtual shutter.
        float dYaw = Mth.wrapDegrees(yaw - lastRenderYaw);
        float dPitch = pitch - lastRenderPitch;
        lastRenderYaw = yaw;
        lastRenderPitch = pitch;
        float u = 0.0f;
        float v = 0.0f;
        // A minimised window has zero width (tanH = 0): dividing by it would give NaN, and the
        // smoothing below would then keep that NaN forever, smearing the whole picture into one
        // flat colour. So no blur unless the projection is valid.
        if (dt > 1.0e-4f && tanH > 1.0e-4f && tanV > 1.0e-4f && Math.abs(dYaw) < SNAP_DEG && Math.abs(dPitch) < SNAP_DEG) {
            float yawSmear = dYaw / dt * SHUTTER_SECONDS * Mth.DEG_TO_RAD;
            float pitchSmear = dPitch / dt * SHUTTER_SECONDS * Mth.DEG_TO_RAD;
            // Turning right slides the picture left (-u); looking down slides it up (+v, GL y points up).
            u = -yawSmear / (2.0f * tanH);
            v = pitchSmear / (2.0f * tanV);
            float len = Mth.sqrt(u * u + v * v);
            if (len > MAX_BLUR_UV) {
                u *= MAX_BLUR_UV / len;
                v *= MAX_BLUR_UV / len;
            }
            float blurAmount = ClientSettings.motionBlur(); // SETTINGS screen: 0 = no smear
            u *= blurAmount;
            v *= blurAmount;
        }
        // A little smoothing so per-frame mouse noise doesn't make the blur flicker.
        float blurEase = ease(0.3f, dTicks);
        blurU += (u - blurU) * blurEase;
        blurV += (v - blurV) * blurEase;
        if (!Float.isFinite(blurU) || !Float.isFinite(blurV)) {
            blurU = blurV = 0.0f; // never let a bad frame stick
        }

        // The fallback (screen-space) flashlight beam trails your aim, like a torch in your hand.
        float beamFollow = Math.min(1.0f, BEAM_FOLLOW_PER_TICK * dTicks);
        beamYaw += Mth.wrapDegrees(aimYaw - beamYaw) * beamFollow;
        beamPitch += (aimPitch - beamPitch) * beamFollow;
        float offYaw = Mth.clamp(Mth.wrapDegrees(beamYaw - yaw), -45.0f, 45.0f) * Mth.DEG_TO_RAD;
        float offPitch = Mth.clamp(beamPitch - pitch, -45.0f, 45.0f) * Mth.DEG_TO_RAD;
        flashX = tanH > 1.0e-4f ? Mth.clamp((float) Math.tan(offYaw) / (2.0f * tanH), -0.25f, 0.25f) : 0.0f;
        flashY = tanV > 1.0e-4f ? Mth.clamp(-(float) Math.tan(offPitch) / (2.0f * tanV), -0.25f, 0.25f) : 0.0f;
    }

    private HandheldCamera() {}
}
