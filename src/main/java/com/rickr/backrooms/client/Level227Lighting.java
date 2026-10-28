package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.light.BackroomsLights;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Level227Sun;
import com.rickr.backrooms.world.layout.Level227Tree;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * How Level 227 ("Megaflora") is lit: the lightmap and the camcorder grade.
 *
 * <p><b>Light.</b> The dimension has a sky stuck at noon, so the vanilla sky light already knows
 * where the light gets in: 15 under open sky (a sunbeam's gap, the top of the canopy, an edge),
 * one less for every layer of leaves above you, and fading off sideways under the giant branches
 * overhead. This lightmap turns that number into the forest's light ({@link #adjustLightmap}):
 * <ul>
 *   <li>open sky: a pale, misty grey-green daylight, a little below white;</li>
 *   <li>under the canopy: greener and dimmer with every layer (light that came through leaves);</li>
 *   <li>deep under a branch (no sky light at all): a dark mossy green-grey that is NEVER black, so
 *       the forest floor always stays walkable;</li>
 *   <li>block light (the amber resin seeps, the yellow-green GammaPali): a warm glow on top. With
 *       Veil those glows are real lights too (client/light/FixtureType), so the lightmap only adds a
 *       little; without Veil it draws the whole pool.</li>
 * </ul>
 * It looks the same with or without Veil, like Level 94: this level is lit by its sky, not by the
 * dark fill of the indoor levels ({@code ClientLighting}).
 *
 * <p><b>Glare.</b> Near a sunbeam everything brightens toward white as your eyes fail ({@link #sunGlare});
 * inside one, the grade goes blinding ({@link #mood}). The dimension effects (client/Level227Atmosphere)
 * call {@link #adjustLightmap}; {@code AtmosphereGrade} calls {@link #mood}.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level227Lighting {
    // --- Lightmap (colours 0..1) ----------------------------------------------------------------
    /** Under open sky: pale misty daylight with a faint green cast, a little under white. */
    private static final float OPEN_R = 0.90f;
    private static final float OPEN_G = 0.97f;
    private static final float OPEN_B = 0.86f;
    /** Light that came through leaves: the colour multiplier at sky light 0 (fades to 1 at 15). */
    private static final float CANOPY_R = 0.74f;
    private static final float CANOPY_G = 1.00f;
    private static final float CANOPY_B = 0.72f;
    /**
     * The darkest the forest gets (sky light 0, deep under a branch): a dark mossy grey-green,
     * about #11180F (the grade's lifted black brings it to ~#161F14). Never black: you can always
     * see the ground you walk on.
     */
    private static final float SHADE_FLOOR = 0.10f;
    /**
     * Sky light curve: 15 = full; 12 (two or three leaf layers) ~0.72; 8 (under thick canopy or
     * at the edge of a branch's shadow) ~0.41; 4 ~0.20; 0 = the floor.
     */
    private static final float SKY_FALLOFF = 1.7f;
    /** Resin and GammaPali glow: warm amber. */
    private static final float GLOW_R = 1.0f;
    private static final float GLOW_G = 0.78f;
    private static final float GLOW_B = 0.48f;
    /** With Veil the glows are real lights: only a soft extra bounce from the lightmap ... */
    private static final float VEIL_GLOW_FILL = 0.22f;
    private static final float VEIL_GLOW_FALLOFF = 2.2f;
    /** ... without Veil the lightmap makes the whole pool. */
    private static final float FALLBACK_GLOW_FILL = 0.75f;
    private static final float FALLBACK_GLOW_FALLOFF = 1.6f;
    /** Full-bright faces (GammaPali bulbs, resin's glowing overlay): just under the bloom threshold. */
    private static final float EMISSIVE = 0.92f;
    /** At full glare (standing in a beam) the whole lightmap moves this far toward white. */
    private static final float GLARE_WHITE = 0.45f;

    // --- Glare ----------------------------------------------------------------------------------
    /** Within this many blocks of a beam's edge the light starts to dazzle ... */
    private static final double GLARE_REACH = 24.0;
    /** ... up to this much just outside it (1 = inside). */
    private static final float NEAR_GLARE = 0.25f;
    /** Per tick, how far the glare / void / height values move toward their goals. */
    private static final float EASE = 0.12f;

    // --- Grade moods ----------------------------------------------------------------------------
    // exposure, contrast, saturation | shadow tint | highlight tint | lift | haze colour, start,
    // density, max | contact shadows, glow, breath (see AtmosphereGrade.Mood)
    /**
     * The forest floor (forest.png): misty and soft. Lush green but a little short of full colour,
     * cool green shadows, slightly warm highlights, lifted mossy blacks, contact shadows at the
     * roots and a soft glow on wet leaves. Almost no haze of its own (max 0.08): the camcorder's
     * haze would lie over every sky-depth pixel too, and the far composite (sky, black void, far
     * tree, client/Level227Atmosphere) already carries the distance mist; a strong haze here washed
     * the void out to pale grey.
     */
    private static final AtmosphereGrade.Mood FOREST = new AtmosphereGrade.Mood(1.0f, 1.02f, 0.94f,
            0.90f, 1.02f, 0.94f, 1.01f, 1.01f, 0.95f,
            0.020f, 0.028f, 0.022f, 0.55f, 0.64f, 0.57f, 14.0f, 0.016f, 0.08f, 0.34f, 0.22f, 0.010f);
    /** The higher sections and the canopy top: clearer, cooler, paler air, colossal branches fading into it. */
    private static final AtmosphereGrade.Mood HIGH = new AtmosphereGrade.Mood(1.02f, 1.0f, 0.90f,
            0.92f, 1.0f, 1.0f, 1.02f, 1.01f, 0.97f,
            0.024f, 0.030f, 0.030f, 0.70f, 0.77f, 0.76f, 24.0f, 0.010f, 0.08f, 0.28f, 0.26f, 0.008f);
    /** Falling into the void: colour draining, black air closing in. */
    private static final AtmosphereGrade.Mood VOID = new AtmosphereGrade.Mood(1.0f, 1.08f, 0.70f,
            0.90f, 0.95f, 1.0f, 1.0f, 1.0f, 1.0f,
            0.004f, 0.005f, 0.006f, 0.02f, 0.025f, 0.025f, 6.0f, 0.030f, 0.85f, 0.30f, 0.15f, 0.012f);
    /** Inside a sunbeam: blinding. Over-exposed, colour bleached out, white haze right up to your face. */
    private static final AtmosphereGrade.Mood SUN = new AtmosphereGrade.Mood(1.55f, 0.85f, 0.60f,
            1.0f, 1.0f, 1.0f, 1.0f, 0.99f, 0.95f,
            0.12f, 0.12f, 0.11f, 1.0f, 0.99f, 0.95f, 0.0f, 0.12f, 0.92f, 0.0f, 0.70f, 0.020f);

    private static float glare;
    private static float voidness;
    private static float high;
    /** Client copy of the sun exposure (ticks), for the visuals; the server has the real one. */
    private static int exposure;
    /** Nearest beam edge distance (blocks, 0 inside), refreshed each tick; MAX_VALUE if none near. */
    private static double beamDistance = Double.MAX_VALUE;
    /** The beam that distance is to (null if none within 64 blocks). */
    @Nullable
    private static Level227Tree.Beam nearest;

    // Scratch for the per-tick beam search (no allocation).
    private static double scanX;
    private static double scanZ;
    private static double scanBest;
    @Nullable
    private static Level227Tree.Beam scanBeam;

    private Level227Lighting() {}

    public static boolean inLevel227() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_227;
    }

    /** 0..1: how much the sun dazzles you now (rises near a beam, 1 inside one). */
    public static float sunGlare() {
        return glare;
    }

    /** 0..1 how far into the warning + disintegration the client thinks you are (visuals only). */
    public static float exposure() {
        return Mth.clamp(exposure / (float) (Level227Sun.WARN_TICKS + Level227Sun.BURN_TICKS), 0.0f, 1.0f);
    }

    /** Distance from the camera to the nearest sunbeam's edge (0 inside), or MAX_VALUE if none within 64 blocks. */
    public static double beamDistance() {
        return beamDistance;
    }

    /** The sunbeam nearest the camera (within 64 blocks), or null. */
    @Nullable
    public static Level227Tree.Beam nearestBeam() {
        return nearest;
    }

    /** 0..1: how high up the tree you are (the "higher sections", from y 480 to 560). */
    public static float high() {
        return high;
    }

    /** 0..1: how far you are into the void (falling below the branches). */
    public static float voidness() {
        return voidness;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused() || !inLevel227()) {
            if (mc.level == null || !inLevel227()) {
                glare = voidness = high = 0.0f;
                exposure = 0;
                beamDistance = Double.MAX_VALUE;
                nearest = null;
            }
            return;
        }
        ClientLevel level = mc.level;
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        // Nearest beam edge (sideways) within 64 blocks; beams are vertical columns.
        scanX = eye.x;
        scanZ = eye.z;
        scanBest = Double.MAX_VALUE;
        scanBeam = null;
        Level227Tree.forEachBeamNear(eye.x, eye.z, 64.0, Level227Lighting::nearestBeam);
        beamDistance = scanBest;
        nearest = scanBeam;
        boolean lit = Level227Sun.inSunlight(level, eye.x, eye.y, eye.z);
        exposure = lit && mc.player.isAlive() ? exposure + 1 : Math.max(0, exposure - 3);
        float glareGoal = lit ? 1.0f
                : beamDistance < GLARE_REACH ? NEAR_GLARE * (float) Math.pow(1.0 - beamDistance / GLARE_REACH, 2.0) : 0.0f;
        glare += (glareGoal - glare) * (lit ? 0.3f : EASE);
        float highGoal = Mth.clamp((float) ((eye.y - 480.0) / 80.0), 0.0f, 1.0f);
        high += (highGoal - high) * EASE * 0.3f;
        // In the void: over a column with no branch top, and either well below the branches or
        // clearly falling (standing at an edge looking out is not the void).
        boolean overVoid = Level227Tree.topSurfaceY(eye.x, eye.z) == Integer.MIN_VALUE;
        float voidGoal = !overVoid ? 0.0f
                : eye.y < 90.0 ? 1.0f : Mth.clamp((mc.player.fallDistance - 6.0f) / 20.0f, 0.0f, 1.0f);
        voidness += (voidGoal - voidness) * EASE * 0.5f;
    }

    private static void nearestBeam(Level227Tree.Beam b) {
        double d = Math.max(0.0, Math.sqrt((scanX - b.x()) * (scanX - b.x()) + (scanZ - b.z()) * (scanZ - b.z())) - b.radius());
        if (d < scanBest) {
            scanBest = d;
            scanBeam = b;
        }
    }

    /**
     * The lightmap for one (block light, sky light) pair: pixelX is the block light 0..15,
     * pixelY the sky light 0..15 (see the class comment for what the numbers mean here).
     */
    public static void adjustLightmap(ClientLevel level, float partialTicks, int pixelX, int pixelY, Vector3f colors) {
        if (pixelX == 15 && pixelY == 15) {
            colors.set(EMISSIVE, EMISSIVE, EMISSIVE);
        } else {
            float s = pixelY / 15.0f;
            float lit = SHADE_FLOOR + (1.0f - SHADE_FLOOR) * (float) Math.pow(s, SKY_FALLOFF);
            // The less sky you see, the more of your light came through leaves: greener.
            float r = lit * OPEN_R * Mth.lerp(s, CANOPY_R, 1.0f);
            float g = lit * OPEN_G * Mth.lerp(s, CANOPY_G, 1.0f);
            float b = lit * OPEN_B * Mth.lerp(s, CANOPY_B, 1.0f);
            boolean veil = BackroomsLights.isVeilLightingActive();
            float glow = (veil ? VEIL_GLOW_FILL : FALLBACK_GLOW_FILL)
                    * (float) Math.pow(pixelX / 15.0f, veil ? VEIL_GLOW_FALLOFF : FALLBACK_GLOW_FALLOFF);
            colors.set(Math.min(1.0f, r + glow * GLOW_R), Math.min(1.0f, g + glow * GLOW_G), Math.min(1.0f, b + glow * GLOW_B));
        }
        // The sun dazzles: everything slides toward white.
        float w = glare * GLARE_WHITE;
        if (w > 0.0f) {
            colors.set(Mth.lerp(w, colors.x, 1.0f), Mth.lerp(w, colors.y, 1.0f), Mth.lerp(w, colors.z, 1.0f));
        }
        applyCamcorder(colors);
    }

    /**
     * The camcorder on top: a dead battery leaves you nearly blind; NightShot lifts the dark parts
     * (the deep shade under a branch) and hardly touches daylight, so it never washes the forest out.
     */
    private static void applyCamcorder(Vector3f colors) {
        if (CameraClientState.battery <= 0.0f) {
            colors.mul(0.25f);
        } else if (CameraClientState.nightShot) {
            float lum = (colors.x + colors.y + colors.z) / 3.0f;
            float lift = 0.4f * (1.0f - lum) * (1.0f - lum);
            colors.set(colors.x + (1.0f - colors.x) * lift, colors.y + (1.0f - colors.y) * lift, colors.z + (1.0f - colors.z) * lift);
        }
    }

    /** The grade for {@code AtmosphereGrade}: the forest, clearing with height, black in the void, white in the sun. */
    public static AtmosphereGrade.Mood mood() {
        // Glare squared: walking up to a beam (glare up to 0.25) barely touches the grade (6%), so
        // the void and the far tree stay dark; only standing in the light whites everything out.
        return FOREST.lerp(HIGH, high).lerp(VOID, voidness).lerp(SUN, glare * glare);
    }
}
