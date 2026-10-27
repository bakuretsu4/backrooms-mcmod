package com.rickr.backrooms.client.light;

import com.rickr.backrooms.config.ClientSettings;
import com.rickr.backrooms.entity.FlareEntity;
import com.rickr.backrooms.entity.GlowstickEntity;
import com.rickr.backrooms.entity.ThrownLight;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Veil point lights of thrown flares and glowsticks (one occluded point light each), the same
 * way {@link VeilFixtures} lights the lamps. Only used while Veil lighting runs; without Veil
 * (Sodium) the sticks still draw their glowing tip/tube, just without light around them.
 *
 * <p>Budget: every tick (before the fixtures are picked) the nearest few thrown lights are picked
 * here, and {@link BackroomsLights} gives the fixtures that many fewer slots, so the total never
 * goes over the quality's light budget.
 *
 * <ul>
 *   <li>Flare: bright red-orange, radius 12, a fast uneven flicker like a real flame; it stutters
 *       and dies over the 3 s sputter.</li>
 *   <li>Glowstick: soft green, radius 5, steady; it fades over its last 30 s.</li>
 * </ul>
 */
final class ThrownLights {
    /** At most this many thrown lights get a Veil light (and never more than a third of the budget). */
    private static final int MAX = 8;
    /** Thrown lights further than this from the camera are skipped. */
    private static final double RANGE = 40.0;

    private static final float FLARE_RADIUS = 12.0f;
    private static final float FLARE_BRIGHTNESS = 1.6f;
    private static final float FLARE_R = 1.0f;
    private static final float FLARE_G = 0.32f;
    private static final float FLARE_B = 0.12f;

    private static final float GLOW_RADIUS = 5.0f;
    private static final float GLOW_BRIGHTNESS = 0.55f;
    private static final float GLOW_R = 0.35f;
    private static final float GLOW_G = 1.0f;
    private static final float GLOW_B = 0.45f;

    /** One light per entity id; kept between ticks so Veil isn't asked to re-upload all lights. */
    private static final Int2ObjectOpenHashMap<Glow> ACTIVE = new Int2ObjectOpenHashMap<>();
    /** The thrown lights picked this tick (nearest first). */
    private static final List<ThrownLight> PICKED = new ArrayList<>();
    private static final List<ThrownLight> CANDIDATES = new ArrayList<>();

    private static final class Glow {
        @Nullable
        LightRenderHandle<PointLightData> point;
        boolean seen;

        boolean valid() {
            return point != null && point.isValid();
        }

        void free() {
            if (point != null) {
                point.free();
                point = null;
            }
        }
    }

    /** How many of the light budget's slots the thrown lights use this tick. */
    static int reserved() {
        return PICKED.size();
    }

    /** Client tick: picks the nearest lit thrown lights and frees the lights of the ones no longer picked. */
    static void tick(ClientLevel level, Vec3 camera) {
        PICKED.clear();
        CANDIDATES.clear();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof ThrownLight thrown && gives(thrown) && thrown.distanceToSqr(camera) < RANGE * RANGE) {
                CANDIDATES.add(thrown);
            }
        }
        int limit = Math.min(MAX, ClientSettings.quality().maxLights / 3);
        if (CANDIDATES.size() > limit) {
            CANDIDATES.sort(Comparator.comparingDouble(e -> e.distanceToSqr(camera)));
        }
        for (int i = 0; i < CANDIDATES.size() && i < limit; i++) {
            PICKED.add(CANDIDATES.get(i));
        }
        CANDIDATES.clear();

        for (Glow glow : ACTIVE.values()) {
            glow.seen = false;
        }
        for (ThrownLight thrown : PICKED) {
            ACTIVE.computeIfAbsent(thrown.getId(), id -> new Glow()).seen = true;
        }
        ACTIVE.int2ObjectEntrySet().removeIf(e -> {
            if (!e.getValue().seen) {
                e.getValue().free();
                return true;
            }
            return false;
        });
    }

    /** Per frame: moves each light with its stick and sets the flicker/fade. */
    static void frame(float partialTick, long nowMs) {
        VeilRenderer renderer = VeilRenderSystem.renderer();
        if (renderer == null || PICKED.isEmpty()) {
            return;
        }
        LightRenderer lights = renderer.getLightRenderer();
        for (ThrownLight thrown : PICKED) {
            Glow glow = ACTIVE.get(thrown.getId());
            if (glow == null) {
                continue;
            }
            boolean flare = thrown instanceof FlareEntity;
            if (!glow.valid()) {
                glow.free();
                glow.point = lights.addLight(new PointLightData()
                        .setRadius(flare ? FLARE_RADIUS : GLOW_RADIUS)
                        .setColor(flare ? FLARE_R : GLOW_R, flare ? FLARE_G : GLOW_G, flare ? FLARE_B : GLOW_B)
                        .setBrightness(0.0f)
                        // Shadows, so the light stays in the room it lies in.
                        .setOcclusionEnabled(true));
            }
            Vec3 at = thrown.lightPosition(partialTick);
            PointLightData data = glow.point.getLightData();
            data.setPosition(at.x, at.y, at.z);
            data.setBrightness(brightness(thrown, partialTick, nowMs));
        }
    }

    /** Frees every thrown light (dimension change, logout, Veil reload). */
    static void freeAll() {
        for (Glow glow : ACTIVE.values()) {
            glow.free();
        }
        ACTIVE.clear();
        PICKED.clear();
    }

    private static boolean gives(ThrownLight thrown) {
        if (thrown instanceof FlareEntity flare) {
            return flare.lit();
        }
        return thrown instanceof GlowstickEntity glowstick && glowstick.glow(0.0f) > 0.0f;
    }

    private static float brightness(ThrownLight thrown, float partialTick, long nowMs) {
        if (thrown instanceof FlareEntity flare) {
            // Two fast waves at odd speeds plus a random jitter: a flame never repeats exactly.
            // The entity id shifts the waves so two flares side by side don't flicker in step.
            double t = nowMs / 1000.0 + flare.getId() * 1.7;
            float flicker = 0.86f + 0.07f * (float) Math.sin(t * 29.0) + 0.05f * (float) Math.sin(t * 53.0 + 1.3)
                    + 0.04f * flare.level().getRandom().nextFloat();
            float left = flare.sputterLeft(partialTick);
            if (flare.sputtering() && flare.level().getRandom().nextFloat() < 0.35f) {
                left *= 0.25f; // the dying flame catches and drops out
            }
            return FLARE_BRIGHTNESS * flicker * left;
        }
        if (thrown instanceof GlowstickEntity glowstick) {
            float g = glowstick.glow(partialTick);
            return GLOW_BRIGHTNESS * Mth.clamp(g * g * (3.0f - 2.0f * g), 0.0f, 1.0f);
        }
        return 0.0f;
    }

    private ThrownLights() {}
}
