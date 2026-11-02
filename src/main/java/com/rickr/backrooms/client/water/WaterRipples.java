package com.rickr.backrooms.client.water;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;

/**
 * Rings spreading on our drawn water surface where something fell in ({@link WaterSplashes} adds
 * them; {@link WaterRenderer} hands them to the {@code pool_water} shader every frame). The shader
 * bends the surface into a short train of ring waves around each impact, so the reflection,
 * the refraction of the bottom and the caustics all wobble outward from the splash, not just
 * the splash particles on top.
 *
 * <p>Only the {@value #SLOTS} most recent impacts are kept (a new one takes a free slot, or the
 * oldest): enough for a splash and a few followers, and a fixed handful of shader numbers. Plain
 * arrays, written and read on the client's main thread only (ticks and frames both run there).
 */
final class WaterRipples {
    /** How many impacts the shader knows at once (uniforms Impact0..Impact3 + ImpactY). */
    static final int SLOTS = 4;
    /** Seconds a ring lives (the shader fades it out over its last half second). */
    private static final float LIFE_SECONDS = 2.6f;
    private static final long LIFE_NS = (long) (LIFE_SECONDS * 1.0e9);

    private static final String[] NAMES = {"Impact0", "Impact1", "Impact2", "Impact3"};
    private static final double[] X = new double[SLOTS];
    private static final double[] Y = new double[SLOTS];
    private static final double[] Z = new double[SLOTS];
    private static final float[] STRENGTH = new float[SLOTS];
    private static final long[] START = new long[SLOTS];

    private WaterRipples() {}

    /**
     * Something hit the water surface (height y) at (x, z). {@code strength} 0..1: 0.3 a small
     * plop, 1 a belly-flop.
     */
    static void add(double x, double y, double z, float strength) {
        long now = System.nanoTime();
        int slot = 0;
        long oldest = Long.MAX_VALUE;
        for (int i = 0; i < SLOTS; i++) {
            if (STRENGTH[i] <= 0.0f || now - START[i] > LIFE_NS) {
                slot = i; // a free slot
                break;
            }
            if (START[i] < oldest) {
                oldest = START[i];
                slot = i;
            }
        }
        X[slot] = x;
        Y[slot] = y;
        Z[slot] = z;
        STRENGTH[slot] = Math.max(0.0f, Math.min(1.0f, strength));
        START[slot] = now;
    }

    /** Forget every ring (left the level). */
    static void clear() {
        for (int i = 0; i < SLOTS; i++) {
            STRENGTH[i] = 0.0f;
        }
    }

    /**
     * Sets the shader's impact uniforms for this frame: per slot (x - camera x, z - camera z,
     * seconds since the impact, strength; strength 0 = no ring), and every slot's surface height
     * minus the camera's in {@code ImpactY}.
     */
    static void apply(ShaderInstance shader, Vec3 eye) {
        long now = System.nanoTime();
        float y0 = 0.0f;
        float y1 = 0.0f;
        float y2 = 0.0f;
        float y3 = 0.0f;
        for (int i = 0; i < SLOTS; i++) {
            float age = (now - START[i]) / 1.0e9f;
            boolean live = STRENGTH[i] > 0.0f && age < LIFE_SECONDS;
            float relY = live ? (float) (Y[i] - eye.y) : 0.0f;
            shader.safeGetUniform(NAMES[i]).set(live ? (float) (X[i] - eye.x) : 0.0f, live ? (float) (Z[i] - eye.z) : 0.0f,
                    live ? age : 0.0f, live ? STRENGTH[i] : 0.0f);
            switch (i) {
                case 0 -> y0 = relY;
                case 1 -> y1 = relY;
                case 2 -> y2 = relY;
                default -> y3 = relY;
            }
        }
        shader.safeGetUniform("ImpactY").set(y0, y1, y2, y3);
    }
}
