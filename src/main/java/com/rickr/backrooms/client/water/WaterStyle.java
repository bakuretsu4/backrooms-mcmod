package com.rickr.backrooms.client.water;

import com.rickr.backrooms.client.Level39ClientState;
import com.rickr.backrooms.client.Level39Weather;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.layout.Level227Tree;
import com.rickr.backrooms.world.layout.PoolroomsLayout;
import net.minecraft.world.phys.Vec3;
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
 * @param darkFog         underwater fog brightness where no lamp or sky lights the water (0..1;
 *                        the pools are lit rooms, so a dark corner is very dark; the open sea has
 *                        its own dim light near the surface)
 * @param darkDepth       blocks under the surface where the water has gone fully black (0 = it
 *                        never darkens with depth: the pools are shallow)
 * @param darkFogDistance underwater fog distance once fully dark (the murk closes in)
 */
public record WaterStyle(int shallowColor, float shallowAlpha, int deepColor, float deepAlpha, float deepDistance,
                         int fogColor, float fogDistance, int reflectColor, float causticStrength,
                         float rippleStrength, float refraction, Vector3f sunDir, int sunColor, float sunGlints,
                         float clearDepth, float tintStrength, float brightness,
                         float darkFog, float darkDepth, float darkFogDistance) {

    /** Level 37's sun: the mean direction of its skylight / slit beams (the Level 37 layout follows it). */
    public static final Vector3f SUN_DIR_37 = new Vector3f(-0.45f, -0.8f, 0.35f).normalize();
    /** Warm daylight, sampled from the Level 37 references. */
    public static final int SUN_COLOR_37 = 0xF1E9BF;

    /** Light straight from above (the Poolrooms' skylights and ceiling tubes). */
    private static final Vector3f STRAIGHT_DOWN = new Vector3f(0.0f, -1.0f, 0.0f);

    /**
     * Level 227's sun: the Sun over the Void hangs 9000 blocks above the trunk ({@link Level227Tree#SUN_X}
     * ...), so its light comes almost straight down, leaning slightly outward the further you are
     * from the trunk (the sunbeams fall straight down too). Re-aimed at the camera once per tick
     * by {@link #aimLevel227Sun} (this one vector is changed in place: no garbage).
     */
    private static final Vector3f SUN_DIR_227 = new Vector3f(0.0f, -1.0f, 0.0f);
    /** The sunbeams' white with the faintest warmth (client/Level227Sunbeams). */
    private static final int SUN_COLOR_227 = 0xFFFCF0;

    /**
     * The Poolrooms keep their established vivid cyan: shallow #45c8d8, deep #0b4f6e, fog #2a7f8f
     * at ~16 blocks. Strong caustics (they matter most here), no sun glints. The colour fades in
     * slowly (clearDepth) so the tiles read near the pool edges, while the middle stays dark teal.
     */
    public static final WaterStyle POOLROOMS = new WaterStyle(
            0x45C8D8, 0.18f, 0x0B4F6E, 0.90f, 5.0f,
            0x2A7F8F, 16.0f, 0xD6E4E8, 0.85f,
            1.0f, 1.0f, STRAIGHT_DOWN, 0xE8F4FF, 0.0f,
            1.8f, 0.75f, 1.0f,
            0.18f, 0.0f, 16.0f);

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
            1.2f, 0.55f, 0.85f,
            0.18f, 0.0f, 20.0f);

    /**
     * The Sanguine Reservoir (the Poolrooms' red copy, see PoolroomsLayout): dark red water lit
     * only by red lamps. Wikidot Level 37.1: "a red tint in the water". Murky: short fog, the deep
     * colour almost black-red, weak caustics, darker than the room light; the ripples are slow
     * and heavy ("viscous").
     */
    public static final WaterStyle SANGUINE = new WaterStyle(
            0x8A2424, 0.30f, 0x2A0606, 0.95f, 3.5f,
            0x3A0A0A, 9.0f, 0x2A1C1C, 0.35f,
            0.45f, 0.7f, STRAIGHT_DOWN, 0xFFD0C8, 0.0f,
            1.2f, 0.85f, 0.8f,
            0.18f, 0.0f, 9.0f);

    /**
     * Level 39's tunnels (reference tunnel.png): clear, glassy, bright turquoise over a pale marble
     * and tile floor. Very transparent (the floor stays readable through the whole 2 blocks of
     * water), a calm surface with gentle caustics, the turquoise deepening only when you look down
     * the length of the tunnel. Lit from above by the invisible orbs: no sun glints.
     */
    public static final WaterStyle LEVEL_39 = new WaterStyle(
            0x48D8D2, 0.12f, 0x0F8C98, 0.78f, 7.0f,
            0x3CB8B8, 22.0f, 0xDCE6EA, 0.55f,
            0.35f, 0.6f, STRAIGHT_DOWN, 0xF2FBFF, 0.0f,
            2.4f, 0.6f, 1.15f,
            0.18f, 0.0f, 22.0f);

    /**
     * Level 39's stagnant segments, where the Hydrolitis plague lives: the same water gone still
     * and sick. A murky green film, short visibility, hardly any caustics (the orbs are dead).
     */
    public static final WaterStyle LEVEL_39_STAGNANT = new WaterStyle(
            0x4E8A6E, 0.32f, 0x123A2A, 0.95f, 3.0f,
            0x1C3A2C, 8.0f, 0x30403A, 0.15f,
            0.15f, 0.5f, STRAIGHT_DOWN, 0xD8F0E0, 0.0f,
            0.9f, 0.9f, 0.75f,
            0.10f, 0.0f, 8.0f);

    /**
     * Level 39's town puddles: still, dark water a block deep over mud. Almost a black mirror:
     * the screen-space reflection shows the glowing facades and the fog in it, as in the photo's
     * wet yard. No caustics; barely a ripple (the rain variant below ripples more).
     */
    public static final WaterStyle LEVEL_39_PUDDLE = new WaterStyle(
            0x2A2E30, 0.35f, 0x0C0F12, 0.92f, 1.5f,
            0x14181A, 6.0f, 0x8A9096, 0.0f,
            0.08f, 0.3f, STRAIGHT_DOWN, 0xE8EEF4, 0.0f,
            0.4f, 0.8f, 0.6f,
            0.18f, 0.0f, 6.0f);
    /** The same puddles while the indoor storm rains on them: the surface pocked with drops. */
    public static final WaterStyle LEVEL_39_PUDDLE_RAIN = new WaterStyle(
            0x2A2E30, 0.35f, 0x0C0F12, 0.92f, 1.5f,
            0x14181A, 6.0f, 0x8A9096, 0.0f,
            0.7f, 0.5f, STRAIGHT_DOWN, 0xE8EEF4, 0.0f,
            0.4f, 0.8f, 0.6f,
            0.18f, 0.0f, 6.0f);
    /** Puddles switch to the rippled look once the rain is this strong (0..1). */
    private static final float PUDDLE_RAIN_THRESHOLD = 0.3f;

    /**
     * Level 7, the open sea under the concrete ceiling: murky grey-green, never clear. A thin
     * layer over the pale silt of the shallows still shows the bottom, but six blocks of water
     * (the shelf) is already mostly the water's own colour and the deep is near black. The surface
     * mirrors the stained concrete above, and its ripples catch that grey light; the light patterns
     * on the bottom are faint (there is no sun, only the grey light from nowhere). Under the
     * surface the murk is dim teal and goes black about 110 blocks down, closing in as it does.
     */
    public static final WaterStyle LEVEL_7 = new WaterStyle(
            0x40594F, 0.35f, 0x0E1D1B, 0.97f, 6.0f,
            0x2C4844, 40.0f, 0x8E908C, 0.35f,
            1.1f, 0.9f, STRAIGHT_DOWN, 0xD8DEDC, 0.0f,
            1.5f, 0.8f, 0.9f,
            0.55f, 110.0f, 22.0f);

    /**
     * Level 227's ponds: forest pools under a giant canopy, tea-coloured by tannins from the leaves
     * and roots. In the shallows the water is clear amber-green (#8a8638) and the mossy bottom shows
     * through, warmed; it turns peaty, near-black green (#0e1a0c) within two or three blocks of
     * water (deepDistance 2.6), so a 4-deep middle is dark while a 1-deep rim stays see-through.
     * Where nothing is found to mirror, it mirrors the canopy: deep green (#34502a), never white.
     * A sheltered pool: subtle ripples (0.35), soft refraction (0.55), dappled caustics (0.55; they
     * brighten the lit bottom, so they show where light reaches and vanish in the shade), and
     * sparse glints of the real sun (0.6), which stands almost overhead: you catch them looking
     * down at the water near you, only where the scene is lit (the shader dims glints in shade).
     * Slightly darker than the scene (0.85: the forest floor is dim). Under the water a murky
     * green fog (#2e3b1c) closes in at 7 blocks; in a dark spot it keeps a quarter of its colour.
     */
    public static final WaterStyle LEVEL_227 = new WaterStyle(
            0x8A8638, 0.20f, 0x0E1A0C, 0.96f, 2.6f,
            0x2E3B1C, 7.0f, 0x34502A, 0.55f,
            0.35f, 0.55f, SUN_DIR_227, SUN_COLOR_227, 0.6f,
            0.9f, 0.75f, 0.85f,
            0.25f, 0.0f, 7.0f);

    /** Points {@link #LEVEL_227}'s sun direction from the Sun over the Void to (x, y, z). Client thread. */
    static void aimLevel227Sun(double x, double y, double z) {
        SUN_DIR_227.set((float) (x - Level227Tree.SUN_X), (float) (y - Level227Tree.SUN_Y), (float) (z - Level227Tree.SUN_Z))
                .normalize();
    }

    /** The water look of a level, or null when that level keeps plain vanilla water. */
    @Nullable
    public static WaterStyle forLevel(@Nullable BackroomsLevel level) {
        if (level == null) {
            return null;
        }
        return switch (level) {
            case POOLROOMS -> POOLROOMS;
            case LEVEL_37 -> LEVEL_37;
            case LEVEL_39 -> LEVEL_39;
            case LEVEL_7 -> LEVEL_7;
            case LEVEL_227 -> LEVEL_227;
            default -> null;
        };
    }

    /**
     * The water look around the camera at (x, y, z) of a level: the Poolrooms' Reservoir has its own
     * red water; in Level 39 the town puddles, the tunnels and their stagnant segments differ.
     * (The renderer uses one look per frame, chosen where you are: you never see both the
     * puddles and the tunnel water at once.)
     */
    @Nullable
    public static WaterStyle at(@Nullable BackroomsLevel level, double x, double y, double z) {
        if (level == BackroomsLevel.POOLROOMS && PoolroomsLayout.inReservoir(x)) {
            return SANGUINE;
        }
        if (level == BackroomsLevel.LEVEL_39) {
            Vec3 eye = new Vec3(x, y, z);
            if (!Level39ClientState.inTunnels(eye)) {
                return Level39Weather.rain() > PUDDLE_RAIN_THRESHOLD ? LEVEL_39_PUDDLE_RAIN : LEVEL_39_PUDDLE;
            }
            return Level39ClientState.inStagnantWater(eye) ? LEVEL_39_STAGNANT : LEVEL_39;
        }
        return forLevel(level);
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
