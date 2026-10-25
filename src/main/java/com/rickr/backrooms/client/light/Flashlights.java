package com.rickr.backrooms.client.light;

import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.network.FlashlightStatesPayload;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.light.data.SpotLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

/**
 * Head-mounted flashlights as real Veil spot lights: yours from the camera, and everyone else's
 * from their eyes (on/off comes from {@link FlashlightStatesPayload}).
 *
 * <ul>
 *   <li>Two cones: a wide soft spill (45 degree half-angle) and a narrow hotspot (~14 degrees),
 *       both reaching 25 blocks, dim warm white, with block shadows and a little dust in the beam.</li>
 *   <li>The beam doesn't snap to your view: each frame it turns 70%-per-tick of the way toward
 *       where you look (frame-rate independent), so it trails like a torch held in a hand.</li>
 *   <li>Below 15% battery it browns out now and then.</li>
 * </ul>
 */
final class Flashlights {
    private static final float RANGE = 25.0f;
    private static final float WIDE_HALF_ANGLE = (float) Math.toRadians(45.0);
    private static final float HOT_HALF_ANGLE = 0.25f;
    private static final float WIDE_BRIGHTNESS = 0.7f;
    private static final float HOT_BRIGHTNESS = 0.6f;
    private static final float WIDE_INSCATTER = 0.3f;
    /** Warm-white bulb. */
    private static final float RED = 1.0f;
    private static final float GREEN = 0.93f;
    private static final float BLUE = 0.8f;
    /** Held a little below the eyes/lens. */
    private static final float DROP = 0.15f;
    /** Fraction of the remaining turn covered per tick (the hand-held lag). */
    private static final float FOLLOW_PER_TICK = 0.7f;
    /** Other players' beams beyond this are skipped (they couldn't reach us anyway). */
    private static final double OTHERS_RANGE = RANGE + 24.0;

    /** One player's pair of cones plus their smoothed aim. */
    private static final class Beam {
        @Nullable
        LightRenderHandle<SpotLightData> wide;
        @Nullable
        LightRenderHandle<SpotLightData> hot;
        final Quaternionf aim = new Quaternionf();
        boolean aimed;
        float lastScale = -1.0f;

        void update(LightRenderer lights, double x, double y, double z, Vector3f look, float follow, float scale) {
            if (wide == null || !wide.isValid() || hot == null || !hot.isValid()) {
                free();
                wide = lights.addLight(cone(WIDE_HALF_ANGLE, WIDE_HALF_ANGLE, WIDE_INSCATTER));
                hot = lights.addLight(cone(HOT_HALF_ANGLE, HOT_HALF_ANGLE * 0.8f, 0.0f));
                lastScale = -1.0f;
            }
            // Target orientation: local +Z along the look direction (lookAlong aims -Z, so reverse it).
            float upY = Math.abs(look.y) > 0.99f ? 0.0f : 1.0f;
            float upZ = 1.0f - upY;
            TARGET.identity().lookAlong(-look.x, -look.y, -look.z, 0.0f, upY, upZ);
            if (!aimed) {
                aim.set(TARGET);
                aimed = true;
            } else {
                aim.slerp(TARGET, follow);
            }
            apply(wide.getLightData(), x, y, z);
            apply(hot.getLightData(), x, y, z);
            if (scale != lastScale) {
                wide.getLightData().setBrightness(WIDE_BRIGHTNESS * scale);
                hot.getLightData().setBrightness(HOT_BRIGHTNESS * scale);
                lastScale = scale;
            }
        }

        private void apply(SpotLightData data, double x, double y, double z) {
            data.getPositionMutable().set(x, y, z);
            data.getOrientationMutable().set(aim);
        }

        void free() {
            if (wide != null) {
                wide.free();
                wide = null;
            }
            if (hot != null) {
                hot.free();
                hot = null;
            }
        }
    }

    private static final Quaternionf TARGET = new Quaternionf();
    private static final Vector3f LOOK = new Vector3f();
    private static final Beam LOCAL = new Beam();
    private static boolean localOn;
    private static final Map<UUID, Beam> OTHERS = new HashMap<>();
    private static final Set<UUID> SEEN = new HashSet<>();
    private static long lastFrameNs;

    /** Called every frame (render thread) while Veil lighting is active. */
    static void frame(Camera camera, float partialTick, ClientLevel level, @Nullable Entity self) {
        VeilRenderer renderer = VeilRenderSystem.renderer();
        if (renderer == null) {
            return;
        }
        LightRenderer lights = renderer.getLightRenderer();

        // Frame-rate independent smoothing: after t ticks, (1 - 0.7)^t of the turn is left.
        long now = System.nanoTime();
        float dtTicks = lastFrameNs == 0 ? 1.0f : Math.min((now - lastFrameNs) / 50_000_000.0f, 5.0f);
        lastFrameNs = now;
        float follow = 1.0f - (float) Math.pow(1.0f - FOLLOW_PER_TICK, dtTicks);

        // Our own flashlight, from the camera.
        boolean on = CameraClientState.flashlight && CameraClientState.battery > 0.0f;
        if (on) {
            Vec3 pos = camera.getPosition();
            Vector3f up = camera.getUpVector();
            LOOK.set(camera.getLookVector());
            if (!localOn) {
                LOCAL.aimed = false; // switching on: start pointing where we look
            }
            // Near a Neighborhood Watcher the beam also drops out (Level 9 electronics interference).
            long nowMs = System.currentTimeMillis();
            LOCAL.update(lights, pos.x - up.x * DROP, pos.y - up.y * DROP, pos.z - up.z * DROP, LOOK, follow,
                    batteryFlicker(nowMs) * com.rickr.backrooms.client.WatcherInterference.flashlightFactor(nowMs));
        } else if (localOn) {
            LOCAL.free();
        }
        localOn = on;

        // Everyone else's, from their eyes (no lag needed beyond our own smoothing of their aim).
        SEEN.clear();
        Vec3 cam = camera.getPosition();
        for (AbstractClientPlayer player : level.players()) {
            UUID id = player.getUUID();
            if (player == self || player.isSpectator() || !FlashlightStatesPayload.CLIENT_ON.contains(id)
                    || player.distanceToSqr(cam) > OTHERS_RANGE * OTHERS_RANGE) {
                continue;
            }
            Vec3 eye = player.getEyePosition(partialTick);
            Vec3 dir = player.calculateViewVector(player.getViewXRot(partialTick), player.getViewYRot(partialTick));
            LOOK.set((float) dir.x, (float) dir.y, (float) dir.z);
            Beam beam = OTHERS.computeIfAbsent(id, k -> new Beam());
            beam.update(lights, eye.x, eye.y - DROP, eye.z, LOOK, follow, 1.0f);
            SEEN.add(id);
        }
        Iterator<Map.Entry<UUID, Beam>> it = OTHERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Beam> entry = it.next();
            if (!SEEN.contains(entry.getKey())) {
                entry.getValue().free();
                it.remove();
            }
        }
    }

    /**
     * Low battery: every 60 ms there's a small chance the bulb browns out (more under 5%).
     * Above 15% it's steady.
     */
    private static float batteryFlicker(long nowMs) {
        float battery = CameraClientState.battery;
        if (battery >= 15.0f) {
            return 1.0f;
        }
        long h = FixturePower.mix(nowMs / 60 * 0x2545F4914F6CDD1DL);
        int roll = (int) (h & 0x3FF); // 0..1023
        int chance = battery < 5.0f ? 110 : 40; // ~11% / ~4% of 60 ms slots
        if (roll < chance / 4) {
            return 0.0f; // the odd full dropout
        }
        return roll < chance ? 0.35f : 1.0f;
    }

    /** Frees every beam (dimension change, logout, leaving the Backrooms). */
    static void freeAll() {
        LOCAL.free();
        localOn = false;
        for (Beam beam : OTHERS.values()) {
            beam.free();
        }
        OTHERS.clear();
        lastFrameNs = 0;
    }

    private static SpotLightData cone(float halfAngle, float softEdge, float inscatter) {
        return new SpotLightData()
                .setSize(halfAngle)
                .setAngle(softEdge)
                .setDistance(RANGE)
                .setColor(RED, GREEN, BLUE)
                .setBrightness(0.0f)
                .setOcclusionEnabled(true)
                .setInscatteringStrength(inscatter);
    }

    private Flashlights() {}
}
