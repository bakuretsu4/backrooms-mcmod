package com.rickr.backrooms.client;

import com.rickr.backrooms.entity.Watcher;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.data.SpotLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.Iterator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jetbrains.annotations.Nullable;

/**
 * Each Watcher's searchlight as real Veil lights:
 * <ul>
 *   <li>a spot light from the pupil (shadows, visible in the fog via inscattering), whose cone
 *       narrows and brightens when it stares or charges, like the drawn cone in {@link WatcherRenderer};</li>
 *   <li>a point light just above the spot where the beam lands, so there's always a clear pool of
 *       light on the street or wall, even where the spot light gets weak.</li>
 * </ul>
 *
 * <p>Why so bright: Veil fades a light with (1 - distance/range)^3, and wet asphalt is very dark, so
 * a street 12-15 blocks below the eye only gets a few percent of the light's brightness.
 *
 * <p>Only called while Veil lighting is active ({@code WatcherInterference} checks), because this
 * class uses Veil's types.
 */
final class WatcherSearchlights {
    private static final double RANGE = 96.0;
    /** How far the spot light reaches. Long, so the street below is well inside its falloff. */
    private static final float SPOT_DISTANCE = 48.0f;
    private static final float SPOT_BRIGHTNESS = 9.0f;
    private static final float SPOT_INSCATTER = 0.5f;
    /** The pool light: radius and brightness, a little above the surface the beam hits. */
    private static final float POOL_RADIUS = 6.0f;
    private static final float POOL_BRIGHTNESS = 1.4f;
    private static final double POOL_LIFT = 0.7;

    private static final class Lights {
        @Nullable
        LightRenderHandle<SpotLightData> spot;
        @Nullable
        LightRenderHandle<PointLightData> pool;

        void free() {
            if (spot != null) {
                spot.free();
                spot = null;
            }
            if (pool != null) {
                pool.free();
                pool = null;
            }
        }
    }

    private static final Int2ObjectOpenHashMap<Lights> LIGHTS = new Int2ObjectOpenHashMap<>();
    private static final IntOpenHashSet SEEN = new IntOpenHashSet();
    private static final Quaternionf AIM = new Quaternionf();

    static void frame(float partialTick, ClientLevel level, Vec3 camera) {
        VeilRenderer renderer = VeilRenderSystem.renderer();
        if (renderer == null) {
            return;
        }
        LightRenderer lightRenderer = renderer.getLightRenderer();
        SEEN.clear();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof Watcher watcher) || watcher.distanceToSqr(camera) > RANGE * RANGE) {
                continue;
            }
            int id = watcher.getId();
            SEEN.add(id);
            Lights lights = LIGHTS.computeIfAbsent(id, k -> new Lights());
            // Veil drops every light on a resource reload: re-create any handle that went invalid.
            if (lights.spot == null || !lights.spot.isValid() || lights.pool == null || !lights.pool.isValid()) {
                lights.free();
                lights.spot = lightRenderer.addLight(new SpotLightData()
                        .setDistance(SPOT_DISTANCE)
                        .setOcclusionEnabled(true)
                        .setInscatteringStrength(SPOT_INSCATTER));
                lights.pool = lightRenderer.addLight(new PointLightData()
                        .setRadius(POOL_RADIUS)
                        .setOcclusionEnabled(true));
            }
            update(lights, watcher, partialTick);
        }
        Iterator<Int2ObjectOpenHashMap.Entry<Lights>> it = LIGHTS.int2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            Int2ObjectOpenHashMap.Entry<Lights> entry = it.next();
            if (!SEEN.contains(entry.getIntKey())) {
                entry.getValue().free();
                it.remove();
            }
        }
    }

    private static void update(Lights lights, Watcher watcher, float partialTick) {
        Watcher.State state = watcher.state();
        float charge = state == Watcher.State.CHARGE ? watcher.charge() : 0.0f;
        float strength = WatcherRenderer.lightStrength(state, charge);
        float halfAngle = WatcherRenderer.halfAngle(state, charge);
        // Cold white, heating up toward a searing pinkish white as it charges.
        float r = 0.84f + 0.16f * charge;
        float g = 0.9f - 0.06f * charge;
        float b = 1.0f - 0.1f * charge;

        Vec3 look = watcher.getViewVector(partialTick);
        // Start just outside the eyeball so the eye itself doesn't block its own light.
        Vec3 origin = WatcherRenderer.pupilPosition(watcher, partialTick).add(look.scale(0.2));
        SpotLightData spot = lights.spot.getLightData();
        spot.getPositionMutable().set(origin.x, origin.y, origin.z);
        // Local +Z along the look direction (lookAlong aims -Z, so reverse it), same as the flashlights.
        float upY = Math.abs(look.y) > 0.99 ? 0.0f : 1.0f;
        AIM.identity().lookAlong((float) -look.x, (float) -look.y, (float) -look.z, 0.0f, upY, 1.0f - upY);
        spot.getOrientationMutable().set(AIM);
        spot.setSize(halfAngle);
        spot.setAngle(halfAngle * 0.6f); // soft edge band
        spot.setColor(r, g, b);
        spot.setBrightness(SPOT_BRIGHTNESS * strength);
        spot.markDirty();

        PointLightData pool = lights.pool.getLightData();
        BlockHitResult hit = WatcherRenderer.beamHit(watcher, partialTick, SPOT_DISTANCE);
        if (hit != null) {
            Vec3 at = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(POOL_LIFT));
            pool.setPosition(at.x, at.y, at.z);
            pool.setColor(r, g, b);
            pool.setBrightness(POOL_BRIGHTNESS * strength);
        } else {
            pool.setBrightness(0.0f);
        }
        pool.markDirty();
    }

    static void freeAll() {
        for (Lights lights : LIGHTS.values()) {
            lights.free();
        }
        LIGHTS.clear();
    }

    private WatcherSearchlights() {}
}
