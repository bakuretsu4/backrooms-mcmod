package com.rickr.backrooms.client;

import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.light.BackroomsLights;
import com.rickr.backrooms.client.light.FixturePower;
import com.rickr.backrooms.network.LevelEventPayload;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

/**
 * How light behaves in the Backrooms, applied to Minecraft's lightmap (the 16x16 table that turns
 * a block's light level into a brightness).
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Veil lighting active</b> ({@link BackroomsLights#isVeilLightingActive()}): Veil's
 *       shadowed point lights make the pools of light, so the lightmap only keeps a very dim
 *       "distance fill": unlit surfaces sit at a blue-black ~#020005, and right next to a fixture
 *       the block light adds at most ~25% with a very steep falloff. This keeps far-away lamps
 *       (beyond Veil's ~28 block range) faintly visible without washing out the real lights.</li>
 *   <li><b>Fallback</b> (no Veil, or Iris installed): the old look. A steep curve makes pools under
 *       each fixture and near-black gaps; events kill, stutter or redden the lightmap directly.</li>
 * </ul>
 * Either way the camcorder applies on top: NightShot lifts the darkness, a dead battery leaves you
 * nearly blind.
 *
 * <p>The Poolrooms follow the same rules (dim rooms, bright pools of light) with a cool tint; their
 * glowing blocks are capped a little below white because the big atrium skylights would otherwise
 * clip and the bloom would haze the whole picture.
 *
 * <p>Level 9 (the Suburbs) is a blue-black night with a soft orange bounce around the sodium
 * streetlamps (cold green-white near mercury ones); see {@link #applyLevel9Fill}.
 */
public final class ClientLighting {
    private static final float FLOOR = 0.07f;

    // Veil mode: the colour of "no light at all" (blue-black, a hint of magenta) ...
    private static final float UNLIT_R = 0.010f;
    private static final float UNLIT_G = 0.003f;
    private static final float UNLIT_B = 0.024f;
    /** ... and the most the vanilla block light may add on top. */
    private static final float MAX_FILL = 0.25f;
    /** Exponent of the fill curve: light level 15 = full fill, 12 ~ 26%, 9 ~ 5%. */
    private static final float FILL_FALLOFF = 6.0f;

    /** Poolrooms: full-bright fixture faces stop here (bloom starts around 0.92; a little glow on the skylights is wanted). */
    private static final float POOLROOMS_EMISSIVE_MAX = 0.96f;
    /** Poolrooms fill: stronger and softer than the other levels (light level 12 ~ 22%, 9 ~ 9%). */
    private static final float POOLROOMS_MAX_FILL = 0.45f;
    private static final float POOLROOMS_FILL_FALLOFF = 3.0f;
    /** Level 2 fill: pale walls in narrow corridors bounce the lamp light along (level 10 ~ 13%). */
    private static final float LEVEL2_MAX_FILL = 0.40f;
    private static final float LEVEL2_FILL_FALLOFF = 3.5f;

    // Level 9 (the Suburbs at midnight).
    /**
     * "No light at all" outdoors: a very dark blue-black night (#020305-ish), a touch bluer than
     * the indoor levels so it reads as night air rather than a dark room.
     */
    private static final float LEVEL9_UNLIT_R = 0.006f;
    private static final float LEVEL9_UNLIT_G = 0.009f;
    private static final float LEVEL9_UNLIT_B = 0.020f;
    /**
     * Fill near the lamps. Lit heads give vanilla block light 15 about 6 blocks over the road, so
     * the road right under one sits around level 9 and the edge of the pool around 5. A soft
     * curve (exponent 2) makes that whole area pick up a little orange bounce: level 9 ~ 0.11,
     * level 5 ~ 0.04. Kept low so wet asphalt stays dark and glossy (Veil's pool sits on top)
     * instead of turning flat grey, and nothing gets near white.
     */
    private static final float LEVEL9_MAX_FILL = 0.30f;
    private static final float LEVEL9_FILL_FALLOFF = 2.0f;
    /** Sodium bounce: deep orange (no blue, so dark surfaces never turn grey). */
    private static final float SODIUM_R = 1.0f;
    private static final float SODIUM_G = 0.55f;
    private static final float SODIUM_B = 0.16f;
    /** Mercury bounce: cold green-white. */
    private static final float MERCURY_R = 0.62f;
    private static final float MERCURY_G = 0.95f;
    private static final float MERCURY_B = 0.80f;
    /**
     * Level 9 lamp heads glow just past the bloom threshold (~0.92) for a faint halo in the fog;
     * they're small, so this doesn't haze the picture the way a big skylight would.
     */
    private static final float LEVEL9_EMISSIVE_MAX = 0.95f;

    private static int activeEvent() {
        return System.currentTimeMillis() < CameraClientState.eventEndMs ? CameraClientState.eventKind : LevelEventPayload.NONE;
    }

    /** 0..1 red-light pulse while the alarm event runs (fallback mode). */
    public static float alarmPulse() {
        if (activeEvent() != LevelEventPayload.ALARM) {
            return 0.0f;
        }
        double t = System.currentTimeMillis() / 1000.0;
        return (float) (0.75 + 0.25 * Math.sin(t * Math.PI * 1.2));
    }

    private static boolean lightsOff() {
        int event = activeEvent();
        if (event == LevelEventPayload.BLACKOUT) {
            return true;
        }
        if (event == LevelEventPayload.FLICKER) {
            long bucket = System.currentTimeMillis() / 90;
            long h = bucket * 0x9E3779B97F4A7C15L;
            h ^= h >>> 29;
            return (h & 7) < 3;
        }
        return false;
    }

    /**
     * @param blockLight 0..15, the block light level this lightmap cell is for
     * @param floor      which Backrooms level we're on (null = treat like Level 0)
     */
    public static void apply(Vector3f colors, int blockLight, boolean fullBright, @Nullable BackroomsLevel floor) {
        if (BackroomsLights.isVeilLightingActive()) {
            if (fullBright) {
                // Emissive fixtures glow white, and all go dark together in a blackout.
                float power = FixturePower.mainsPower() * (FixturePower.flickering(System.currentTimeMillis()) ? 0.5f : 1.0f);
                float v = Math.max(0.05f, power);
                if (floor == BackroomsLevel.POOLROOMS) {
                    v = Math.min(v, POOLROOMS_EMISSIVE_MAX);
                } else if (floor == BackroomsLevel.LEVEL_9) {
                    v = Math.min(v, LEVEL9_EMISSIVE_MAX);
                }
                colors.set(v, v, v);
            } else {
                applyVeilFill(colors, blockLight, floor);
            }
        } else {
            // Garage, pipe corridors and Poolrooms are lit by long strips: gentler falloff than the
            // Lobby's pools. The Poolrooms may be a touch brighter, but unlit rooms stay dark.
            float falloff = floor == BackroomsLevel.LEVEL_1 ? 1.0f
                    : floor == BackroomsLevel.LEVEL_2 ? 1.35f
                    : floor == BackroomsLevel.POOLROOMS ? 1.25f
                    // Level 9: tall lamps put little block light on the ground, so a gentler curve.
                    : floor == BackroomsLevel.LEVEL_9 ? 1.3f
                    : 1.8f;
            applyFallback(colors, falloff);
        }
        applyCamcorder(colors);
    }

    /** Veil mode: just a dim, steep fill; Veil does the real lighting. */
    private static void applyVeilFill(Vector3f colors, int blockLight, @Nullable BackroomsLevel floor) {
        if (floor == BackroomsLevel.LEVEL_9) {
            applyLevel9Fill(colors, blockLight);
            return;
        }
        float b = Mth.clamp(blockLight / 15.0f, 0.0f, 1.0f);
        // White tile bounces light around, so the Poolrooms get a stronger, softer fill: rooms with
        // a tube read bright, and the water (which Veil's lights don't touch) still glows cyan near
        // lamps. Level 2's narrow pale corridors bounce light the same way, a little less strongly.
        // Places far from any light stay dark (block light 0 = no fill).
        float maxFill = MAX_FILL;
        float falloff = FILL_FALLOFF;
        if (floor == BackroomsLevel.POOLROOMS) {
            maxFill = POOLROOMS_MAX_FILL;
            falloff = POOLROOMS_FILL_FALLOFF;
        } else if (floor == BackroomsLevel.LEVEL_2) {
            maxFill = LEVEL2_MAX_FILL;
            falloff = LEVEL2_FILL_FALLOFF;
        }
        float fill = maxFill * (float) Math.pow(b, falloff);
        // The fill dies with the lights (blackout fade) and dims with the alarm; a flicker halves it.
        fill *= FixturePower.mainsPower() * FixturePower.alarmDim();
        if (FixturePower.flickering(System.currentTimeMillis())) {
            fill *= 0.5f;
        }
        // Warm yellow in the Lobby, neutral grey concrete in the garage, a neutral cool white in
        // the pipes (no purple wash: the lavender is in the wall paint already) and a cool
        // lavender-blue in the Poolrooms (shaded white tile in the footage reads ~#A9A3A8).
        float tr = 1.0f;
        float tg = 0.92f;
        float tb = 0.55f;
        if (floor == BackroomsLevel.LEVEL_1) {
            tr = 0.96f;
            tg = 0.96f;
            tb = 1.0f;
        } else if (floor == BackroomsLevel.LEVEL_2) {
            tr = 0.90f;
            tg = 0.90f;
            tb = 1.0f;
        } else if (floor == BackroomsLevel.POOLROOMS) {
            tr = 0.80f;
            tg = 0.86f;
            tb = 1.0f;
        }
        // During the alarm the little fill there is turns red.
        float alarm = FixturePower.alarm();
        tr = Mth.lerp(alarm, tr, 1.0f);
        tg = Mth.lerp(alarm, tg, 0.12f);
        tb = Mth.lerp(alarm, tb, 0.10f);
        colors.set(UNLIT_R + fill * tr, UNLIT_G + fill * tg, UNLIT_B + fill * tb);
    }

    /**
     * Level 9: a gentle coloured bounce around the streetlamps on a blue-black night. The colour
     * is orange sodium, shifted toward cold mercury green-white by how much of the lamp light
     * around you is mercury ({@link BackroomsLights#mercuryShare()}): the lightmap has one colour
     * for the whole screen, and the pool you're standing in matters most.
     */
    private static void applyLevel9Fill(Vector3f colors, int blockLight) {
        float b = Mth.clamp(blockLight / 15.0f, 0.0f, 1.0f);
        float fill = LEVEL9_MAX_FILL * (float) Math.pow(b, LEVEL9_FILL_FALLOFF);
        // Lamps that die in the wave or flicker keep their vanilla block light (that only changes
        // when the block does), so the fill can't follow single lamps; it is faint enough that a
        // dead lamp's patch just reads as the last of the bounce. A blackout would still kill it.
        fill *= FixturePower.mainsPower();
        float m = BackroomsLights.mercuryShare();
        float tr = Mth.lerp(m, SODIUM_R, MERCURY_R);
        float tg = Mth.lerp(m, SODIUM_G, MERCURY_G);
        float tb = Mth.lerp(m, SODIUM_B, MERCURY_B);
        colors.set(LEVEL9_UNLIT_R + fill * tr, LEVEL9_UNLIT_G + fill * tg, LEVEL9_UNLIT_B + fill * tb);
    }

    /** Fallback (no Veil): the vanilla lightmap does all the lighting. */
    private static void applyFallback(Vector3f colors, float falloff) {
        if (lightsOff()) {
            colors.set(FLOOR, FLOOR, FLOOR);
        } else {
            colors.set(curve(colors.x, falloff), curve(colors.y, falloff), curve(colors.z, falloff));
            float alarm = alarmPulse();
            if (alarm > 0.0f) {
                float lum = (colors.x + colors.y + colors.z) / 3.0f;
                colors.set(Mth.lerp(alarm, colors.x, lum * 1.15f + 0.02f),
                        Mth.lerp(alarm, colors.y, lum * 0.18f),
                        Mth.lerp(alarm, colors.z, lum * 0.15f));
            }
        }
    }

    /** NightShot lifts everything toward white; a dead battery darkens it all. */
    private static void applyCamcorder(Vector3f colors) {
        if (CameraClientState.battery <= 0.0f) {
            colors.mul(0.25f);
        } else if (CameraClientState.nightShot) {
            float lift = 0.4f;
            colors.set(colors.x + (1.0f - colors.x) * lift,
                    colors.y + (1.0f - colors.y) * lift,
                    colors.z + (1.0f - colors.z) * lift);
        }
    }

    private static float curve(float c, float falloff) {
        return FLOOR + (1.0f - FLOOR) * (float) Math.pow(Math.max(c, 0.0f), falloff);
    }

    private ClientLighting() {}
}
