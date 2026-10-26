package com.rickr.backrooms.client.water;

import com.rickr.backrooms.world.BackroomsLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * How the water of one level looks: its colours, how quickly it turns opaque with depth, the
 * underwater fog, how strong the moving light patterns (caustics) are, and the sun.
 *
 * <p>All colours are 0xRRGGBB. Every value the water shader uses comes from here, so this is the
 * one place to tune the look of a level's water.
 *
 * @param shallowColor    colour of a thin layer of water (over a bright tiled floor)
 * @param shallowAlpha    how much of that colour covers the floor in very shallow water (0..1)
 * @param deepColor       colour once you look through a lot of water
 * @param deepAlpha       how opaque deep water gets (0..1; 1 = you can't see the bottom at all)
 * @param deepDistance    blocks of water (along your line of sight) until it reads fully "deep"
 * @param fogColor        underwater fog colour (eyes in the water)
 * @param fogDistance     underwater fog: blocks until everything fades into the fog colour
 * @param reflectColor    what the surface mirrors when the screen-space reflection finds
 *                        nothing (the ceiling / walls of the level)
 * @param causticStrength brightness of the moving light network on the floor under the water
 * @param rippleStrength  how bumpy the surface is (0 = mirror flat)
 * @param refraction      how far the floor seen through the water wobbles
 * @param sunDir          direction the light travels (pointing DOWN); the caustics are projected
 *                        along it, and the sun glints use it
 * @param sunColor        colour of the sun glints
 * @param sunGlints       0 = no glints (indoor tubes only), 1 = sharp sun sparkles
 * @param clearDepth      blocks of water (line of sight) over which the water colour fades in:
 *                        larger = the floor stays readable further from the edge
 * @param tintStrength    how strongly the floor seen through the water takes the shallow colour
 *                        (0 = not tinted at all, 1 = full tint)
 * @param brightness      overall brightness of the lit water (1 = as the room light gives it)
 */
public record WaterStyle(int shallowColor, float shallowAlpha, int deepColor, float deepAlpha, float deepDistance,
                         int fogColor, float fogDistance, int reflectColor, float causticStrength,
                         float rippleStrength, float refraction, Vector3f sunDir, int sunColor, float sunGlints,
                         float clearDepth, float tintStrength, float brightness) {

    /** Level 37's sun: the mean direction of its skylight / slit beams (the Level 37 layout follows it). */
    public static final Vector3f SUN_DIR_37 = new Vector3f(-0.45f, -0.8f, 0.35f).normalize();
    /** Warm daylight, sampled from the Level 37 references. */
    public static final int SUN_COLOR_37 = 0xF1E9BF;

    /** Light straight from above (the Poolrooms' skylights and ceiling tubes). */
    private static final Vector3f STRAIGHT_DOWN = new Vector3f(0.0f, -1.0f, 0.0f);

    /**
     * The Poolrooms keep their established vivid cyan: shallow #45c8d8, deep #0b4f6e, fog #2a7f8f
     * at ~16 blocks. Strong caustics (they matter most here), no sun glints. The colour fades in
     * slowly (clearDepth) so the tiles read near the pool edges, while the middle stays dark teal.
     */
    public static final WaterStyle POOLROOMS = new WaterStyle(
            0x45C8D8, 0.18f, 0x0B4F6E, 0.90f, 5.0f,
            0x2A7F8F, 16.0f, 0xD6E4E8, 0.85f,
            1.0f, 1.0f, STRAIGHT_DOWN, 0xE8F4FF, 0.0f,
            1.8f, 0.75f, 1.0f);

    /**
     * Level 37: clear, calm sea-green over cream tile, matched to the AnarkyMusic renders
     * (shallow #349981, deep #175a2d): very transparent, only lightly tinted, a little darker
     * than the room light, gentle caustics so the tile grid stays readable. Lighter underwater
     * fog with long visibility (#3a8f78, ~20 blocks), warm sun glints.
     */
    public static final WaterStyle LEVEL_37 = new WaterStyle(
            0x349981, 0.22f, 0x175A2D, 0.85f, 4.5f,
            0x3A8F78, 20.0f, 0xE9E6D2, 0.7f,
            0.6f, 0.9f, SUN_DIR_37, SUN_COLOR_37, 0.8f,
            1.2f, 0.55f, 0.85f);

    /** The water look of a level, or null when that level keeps plain vanilla water. */
    @Nullable
    public static WaterStyle forLevel(@Nullable BackroomsLevel level) {
        if (level == null) {
            return null;
        }
        return switch (level) {
            case POOLROOMS -> POOLROOMS;
            case LEVEL_37 -> LEVEL_37;
            default -> null;
        };
    }

    static float red(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255.0f;
    }

    static float green(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255.0f;
    }

    static float blue(int rgb) {
        return (rgb & 0xFF) / 255.0f;
    }
}
