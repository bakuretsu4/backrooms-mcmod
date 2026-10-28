package com.rickr.backrooms.client;

import com.rickr.backrooms.world.layout.Level227Tree;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * With Veil: a strong white point light at the foot of the nearest sunbeams, so the forest around a
 * beam is lit by it (trunks bright on the side facing the shaft, shadows thrown away from it).
 * Only used while Veil's lights run ({@code BackroomsLights.isVeilLightingActive()}: not under
 * Iris), and freed as soon as you leave Level 227 or Veil's lights switch off.
 *
 * <p>At most {@value #MAX} lights (beams are far apart, so two is plenty), only for beams within
 * {@value #RANGE} blocks: Veil's shadows only cover ~32 blocks around the camera.
 */
public final class Level227BeamLights {
    private static final int MAX = 2;
    private static final double RANGE = 40.0;
    /** A few blocks above the ash, so it lights the ground around the beam and the trunks beside it. */
    private static final double LIFT = 3.0;
    private static final float BRIGHTNESS = 2.2f;
    private static final float R = 1.0f;
    private static final float G = 0.98f;
    private static final float B = 0.92f;

    private static final Level227Tree.Beam[] BEAMS = new Level227Tree.Beam[MAX];
    @SuppressWarnings("unchecked")
    private static final LightRenderHandle<PointLightData>[] HANDLES = new LightRenderHandle[MAX];
    private static int found;
    private static double camX;
    private static double camZ;

    private Level227BeamLights() {}

    static boolean active() {
        for (LightRenderHandle<PointLightData> h : HANDLES) {
            if (h != null) {
                return true;
            }
        }
        return false;
    }

    /** Client tick: one light per near beam, moved/created/freed as you walk. */
    static void tick(Vec3 camera) {
        VeilRenderer renderer = VeilRenderSystem.renderer();
        if (renderer == null) {
            return;
        }
        camX = camera.x;
        camZ = camera.z;
        found = 0;
        BEAMS[0] = null;
        BEAMS[1] = null;
        Level227Tree.forEachBeamNear(camera.x, camera.z, RANGE, Level227BeamLights::collect);
        for (int i = 0; i < MAX; i++) {
            Level227Tree.Beam beam = BEAMS[i];
            LightRenderHandle<PointLightData> handle = HANDLES[i];
            if (beam == null) {
                free(i);
                continue;
            }
            int ground = Level227Tree.topSurfaceY(beam.x(), beam.z());
            if (ground == Integer.MIN_VALUE) {
                free(i);
                continue;
            }
            if (handle == null || !handle.isValid()) {
                free(i);
                handle = renderer.getLightRenderer().addLight(new PointLightData()
                        .setColor(R, G, B)
                        .setBrightness(BRIGHTNESS)
                        .setOcclusionEnabled(true));
                HANDLES[i] = handle;
            }
            PointLightData data = handle.getLightData();
            data.setPosition(beam.x(), ground + LIFT, beam.z());
            data.setRadius((float) Math.min(28.0, beam.radius() * 2.0 + 10.0));
        }
    }

    /** Keeps the two nearest beams (by distance to their edge). */
    private static void collect(Level227Tree.Beam beam) {
        double d = edge(beam);
        if (found < MAX) {
            BEAMS[found++] = beam;
        } else {
            int far = edge(BEAMS[0]) > edge(BEAMS[1]) ? 0 : 1;
            if (d < edge(BEAMS[far])) {
                BEAMS[far] = beam;
            }
        }
    }

    private static double edge(@Nullable Level227Tree.Beam beam) {
        if (beam == null) {
            return Double.MAX_VALUE;
        }
        double dx = camX - beam.x();
        double dz = camZ - beam.z();
        return Math.sqrt(dx * dx + dz * dz) - beam.radius();
    }

    private static void free(int i) {
        if (HANDLES[i] != null) {
            HANDLES[i].free();
            HANDLES[i] = null;
        }
    }

    /** Leaving the level (or Veil switched off): give every light back. */
    static void freeAll() {
        for (int i = 0; i < MAX; i++) {
            free(i);
        }
    }
}
