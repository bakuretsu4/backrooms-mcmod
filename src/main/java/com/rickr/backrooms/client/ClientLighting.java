package com.rickr.backrooms.client;

import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.light.BackroomsLights;
import com.rickr.backrooms.client.light.FixturePower;
import com.rickr.backrooms.client.light.Level0Shading;
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
 * <p>Level 0 (see {@link #applyLevel0Fill}): with Veil its lamps are the real light (pools cut
 * block-exact by the walls, client/light/Level0LightRenderer) and the lightmap only adds a weak
 * bounce under them; a screen pass darkens that bounce in corners and at wall feet and removes it
 * where the power is out. Without Veil the fill follows the vanilla block light and is the whole
 * light: brightest under a panel, darker between panels, in corners and behind pillars (block
 * light flows around walls), black in the dark wings.
 *
 * <p>The Poolrooms follow the same rules (dim rooms, bright pools of light) with a cool tint; their
 * glowing blocks are capped a little below white because the big atrium skylights would otherwise
 * clip and the bloom would haze the whole picture.
 *
 * <p>Level 9 (the Suburbs) is a blue-black night with a soft orange bounce around the sodium
 * streetlamps (cold green-white near mercury ones); see {@link #applyLevel9Fill}.
 *
 * <p>Level 4 (the Abandoned Office) has its own cold fill, which also carries the lightning flash
 * of its storm; see {@link Level4Atmosphere}.
 *
 * <p>Level 39 (Megalophobia) is a blue-grey night that turns cool and bright near the glowing
 * facades, with bright even tunnels below; see {@link Level39Atmosphere#applyFill}.
 *
 * <p>Level 94 and Level 227 (Megaflora) don't use this class: they are lit by their sky, so their
 * own dimension effects build the whole lightmap (Level 227's misty green forest light, never
 * black on the forest floor, white near the sunbeams: {@link Level227Lighting#adjustLightmap}).
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
    /**
     * Level 2 fill. Beyond the Veil lamps' range this is all that lights the corridor, so it
     * carries the far end of the receding row of pools: the patch of wall and floor at each lamp's
     * foot (light level 7-9) keeps a dim warm glow (level 9 ~ 12%, 7 ~ 7%), while the gaps between
     * lamps and the pipe chases (level 5 and below, ~3% and less) stay dark. A soft curve
     * (exponent 2.2) so the pools fade out gradually rather than as hard blobs.
     */
    private static final float LEVEL2_MAX_FILL = 0.37f;
    private static final float LEVEL2_FILL_FALLOFF = 2.2f;
    /** The bounce of the warm caged lamps off grey-beige concrete: amber, not orange. */
    private static final float LEVEL2_TINT_R = 1.0f;
    private static final float LEVEL2_TINT_G = 0.84f;
    private static final float LEVEL2_TINT_B = 0.62f;
    /**
     * When the section of corridor you stand in loses its circuit, the fill drops to this share
     * (the dead lamps' vanilla light never changes, so this is what makes the section go dark).
     */
    private static final float LEVEL2_DEAD_SECTION_FILL = 0.2f;
    /**
     * Level 2's "readable dark" (the 2026-10 industrial overhaul): a faint cold light everywhere
     * the corridor's lamps could reach at all, even when they are dead, so a dead section still
     * shows the floor edge, the wall line and the pipes' silhouettes (the camera's eye catching the
     * last of the light). A cold blue-grey, so darkness reads cold and empty, not rust-brown.
     * Numbers: concrete walls (~0.55) and floor (~0.47) times this (~0.11 brightness) and the face
     * shading land a dead section at about 0.03-0.06 on camera (lead's target), the ceiling near
     * black. Under working lamps it is a small part of the light.
     */
    private static final float LEVEL2_AMBIENT_R = 0.100f;
    private static final float LEVEL2_AMBIENT_G = 0.110f;
    private static final float LEVEL2_AMBIENT_B = 0.148f;
    /**
     * The cold light fades in with the block light up to this level: places no lamp reaches at all
     * (block light 0: the void, deep chases, the far end of the trench gutter) stay pitch black.
     */
    private static final float LEVEL2_AMBIENT_FULL_FROM = 4.0f;
    /** In a blackout the cold light keeps this share (the emergency lamps light the way). */
    private static final float LEVEL2_AMBIENT_BLACKOUT = 0.6f;
    /**
     * Without Veil the fill is the whole lamp light: under a caged lamp (light 9-10) ~0.30-0.37,
     * the floor between lamps (7) ~0.17. A dead section keeps a little more of it than with Veil
     * (no emergency-lamp pools to find the way by).
     */
    private static final float LEVEL2_FALLBACK_MAX_FILL = 0.80f;
    private static final float LEVEL2_FALLBACK_FALLOFF = 1.9f;
    private static final float LEVEL2_FALLBACK_DEAD_FILL = 0.12f;

    // Level 0 (Kane's Lobby): dim, uneven office light; whole dark wings near black.
    /**
     * "No light at all" in Level 0: practically black, a hint warm (red over green over blue, so
     * it never reads olive), so dark wings and doorways read as solid black holes. Not the
     * blue-black of other levels.
     */
    private static final float LEVEL0_UNLIT_R = 0.004f;
    private static final float LEVEL0_UNLIT_G = 0.0036f;
    private static final float LEVEL0_UNLIT_B = 0.0026f;
    /**
     * With Veil the lamps themselves light Level 0 (client/light: pools cut block-exact by every
     * wall), and the lightmap only adds a weak, soft BOUNCE under them: the light coming off walls
     * and ceiling. At full strength ({@link #LEVEL0_FULL_FROM} and up) it is this much; with the
     * 3-high ceiling the floor under a full tube (block light 9-10) gets ~0.18, the floor between
     * pools (7) ~0.10, a corner away from the lamps (5) ~0.05, which the screen pass then darkens
     * further right at the wall (Level0LightRenderer). Dark wings get nothing: they stay black.
     */
    private static final float LEVEL0_MAX_FILL = 0.20f;
    /** The bounce falls off gently with the block light (it is diffuse light, not a pool). */
    private static final float LEVEL0_BOUNCE_CURVE = 2.0f;
    /** Of the bounce, this share fades out in a straight line (keeps the last blocks of reach faintly lit). */
    private static final float LEVEL0_BOUNCE_LINEAR = 0.10f;
    /**
     * Without Veil the fill is the whole light, so it makes the pools itself: under a tube ~0.68,
     * between tubes ~0.24, corners and wall feet away from the panels (light 5) ~0.06, and black
     * from light 2 down (the far side of doorways, dark wings).
     */
    private static final float LEVEL0_FALLBACK_MAX_FILL = 0.97f;
    /**
     * Light level from which the fill stops growing. Above it are only the upper walls and the
     * ceiling right beside a panel: a troffer shines down, so those must not get brighter than
     * the floor under it (they would glow, the opposite of a real room).
     */
    private static final float LEVEL0_FULL_FROM = 10.0f;
    /**
     * Light level at and below which it is black. With Veil level 1, so the bounce fades out
     * gradually at the end of a tube's reach; without Veil level 2, so corners, the far side of
     * doorways and the mouths of dark wings really go dark.
     */
    private static final float LEVEL0_DARK_BELOW = 1.0f;
    private static final float LEVEL0_FALLBACK_DARK_BELOW = 2.0f;
    /**
     * Without Veil: how steeply the direct light falls between those two (1 = straight line).
     * 3.5 is steep: the pool right under a tube stands out clearly, about 2.8 times as bright as
     * the floor between tubes, so the eye sees each panel's patch of light instead of an even carpet.
     */
    private static final float LEVEL0_FILL_CURVE = 3.5f;
    /**
     * Without Veil: bounce light, a weak glow that fades out evenly (a straight line) instead of
     * falling steeply like the direct light. Kept low so corners and wall feet (light 5) sit near
     * black instead of a flat grey.
     */
    private static final float LEVEL0_BOUNCE = 0.09f;
    /**
     * The bounce off yellow wallpaper and carpet: warm, but the yellow mostly comes from the
     * textures. Green a touch under red and blue cut less than before, so the textures' yellow
     * ends up a soft yellow-beige instead of olive (see AtmosphereGrade's LEVEL_0 for the numbers).
     */
    private static final float LEVEL0_TINT_R = 1.0f;
    private static final float LEVEL0_TINT_G = 0.975f;
    private static final float LEVEL0_TINT_B = 0.92f;
    /** During a FLICKER event the whole fill stutters between these (the panels light the halls). */
    private static final float LEVEL0_FLICKER_DARK = 0.35f;
    private static final float LEVEL0_FLICKER_LIT = 0.9f;
    /**
     * How much the fill drops when ALL the tube light around you is out (a faulty tube right over
     * you in a dark moment takes away about 40% of it, so the room dims by about a fifth; see
     * BackroomsLights#level0TubeShare). Never a full-screen strobe.
     */
    private static final float LEVEL0_TUBE_DIP = 0.5f;

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
    /** Level 39: full-bright faces (gallery lights, lit glass) stop just under the bloom threshold. */
    private static final float LEVEL39_EMISSIVE_MAX = 0.85f;
    /**
     * Level 9.1, the Crimson Field, has no lamps at all, so "unlit" there would be a black screen.
     * A faint cold glow from the overcast sky instead: the grass reads as dark blue-grey stalks,
     * darker than the mist behind them.
     */
    private static final float FIELD_UNLIT_R = 0.05f;
    private static final float FIELD_UNLIT_G = 0.06f;
    private static final float FIELD_UNLIT_B = 0.085f;

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
        return event == LevelEventPayload.FLICKER && flickerDark();
    }

    /** During a FLICKER event: whether the lights are in a dark moment right now (shared stutter). */
    private static boolean flickerDark() {
        long bucket = System.currentTimeMillis() / 90;
        long h = bucket * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        return (h & 7) < 3;
    }

    private static boolean isLevel0(@Nullable BackroomsLevel floor) {
        return floor == null || floor == BackroomsLevel.LEVEL_0;
    }

    /**
     * How bright full-bright fixture faces are: all dark together in a blackout, half in a
     * flicker. On Level 0 with Veil the power is out place by place, so there the glowing panels
     * are switched dark one by one (BackroomsLights) and far away by the screen pass
     * (Level0LightRenderer), not all at once here.
     */
    private static float emissivePower() {
        float power = Level0Shading.screenPassRunning() ? 1.0f : FixturePower.mainsPower();
        return Math.max(0.05f, power * (FixturePower.flickering(System.currentTimeMillis()) ? 0.5f : 1.0f));
    }

    /**
     * @param blockLight 0..15, the block light level this lightmap cell is for
     * @param floor      which Backrooms level we're on (null = treat like Level 0)
     */
    public static void apply(Vector3f colors, int blockLight, boolean fullBright, @Nullable BackroomsLevel floor) {
        if (BackroomsLights.isVeilLightingActive()) {
            if (fullBright) {
                // Emissive fixtures glow white, and all go dark together in a blackout.
                float v = emissivePower();
                if (floor == BackroomsLevel.POOLROOMS) {
                    v = Math.min(v, POOLROOMS_EMISSIVE_MAX);
                } else if (floor == BackroomsLevel.LEVEL_9) {
                    v = Math.min(v, LEVEL9_EMISSIVE_MAX);
                } else if (floor == BackroomsLevel.LEVEL_39) {
                    // Level 39's gallery lights and lit glass: kept under the bloom threshold, so
                    // up close the facade shows its muntins, brick and stone instead of a white-out.
                    v = Math.min(v, LEVEL39_EMISSIVE_MAX);
                }
                colors.set(v, v, v);
            } else {
                applyVeilFill(colors, blockLight, floor);
            }
        } else if (isLevel0(floor)) {
            // Level 0 without Veil: the same uneven fill, a little stronger since there are no
            // pools of Veil light on top; the panels themselves glow white.
            if (fullBright) {
                float v = emissivePower();
                colors.set(v, v, v);
            } else {
                applyLevel0Fill(colors, blockLight, LEVEL0_FALLBACK_MAX_FILL, LEVEL0_FALLBACK_DARK_BELOW,
                        LEVEL0_FILL_CURVE, LEVEL0_BOUNCE, FixturePower.mainsPower());
            }
        } else if (floor == BackroomsLevel.LEVEL_2) {
            // Level 2 without Veil: the amber fill is the lamp light, the cold readable dark under it.
            if (fullBright) {
                float v = emissivePower();
                colors.set(v, v, v);
            } else {
                applyLevel2Fill(colors, blockLight, LEVEL2_FALLBACK_MAX_FILL, LEVEL2_FALLBACK_FALLOFF,
                        LEVEL2_FALLBACK_DEAD_FILL, true);
            }
        } else {
            // Garage and Poolrooms are lit by long strips: gentler falloff than the
            // Lobby's pools. The Poolrooms may be a touch brighter, but unlit rooms stay dark.
            float falloff = floor == BackroomsLevel.LEVEL_1 ? 1.0f
                    : floor == BackroomsLevel.POOLROOMS ? 1.25f
                    // Level 9: tall lamps put little block light on the ground, so a gentler curve.
                    : floor == BackroomsLevel.LEVEL_9 ? 1.3f
                    // Level 4: dim windows and panel islands; a softer curve keeps both readable.
                    : floor == BackroomsLevel.LEVEL_4 ? 1.5f
                    // Level 37: daylight bounced around white tile; soft and bright.
                    : floor == BackroomsLevel.LEVEL_37 ? 1.1f
                    // Level 39: big light-15 facades and orbs; a soft curve keeps the dim town readable.
                    : floor == BackroomsLevel.LEVEL_39 ? 1.3f
                    : 1.8f;
            applyFallback(colors, falloff);
            if (floor == BackroomsLevel.LEVEL_4) {
                Level4Atmosphere.adjustFallback(colors, blockLight);
            } else if (floor == BackroomsLevel.LEVEL_39) {
                Level39Atmosphere.adjustFallback(colors); // readable floor + cool glow near the facades
            }
        }
        applyCamcorder(colors);
    }

    /** Veil mode: just a dim, steep fill; Veil does the real lighting. */
    private static void applyVeilFill(Vector3f colors, int blockLight, @Nullable BackroomsLevel floor) {
        if (isLevel0(floor)) {
            // A soft bounce under the lamps. Where the power is out the screen pass removes it
            // place by place; only if that pass isn't running does the power at you dim it all.
            applyLevel0Fill(colors, blockLight, LEVEL0_MAX_FILL, LEVEL0_DARK_BELOW, LEVEL0_BOUNCE_CURVE,
                    LEVEL0_BOUNCE_LINEAR, Level0Shading.screenPassRunning() ? 1.0f : FixturePower.mainsPower());
            return;
        }
        if (floor == BackroomsLevel.LEVEL_2) {
            applyLevel2Fill(colors, blockLight, LEVEL2_MAX_FILL, LEVEL2_FILL_FALLOFF, LEVEL2_DEAD_SECTION_FILL, false);
            return;
        }
        if (floor == BackroomsLevel.LEVEL_9) {
            applyLevel9Fill(colors, blockLight);
            return;
        }
        if (floor == BackroomsLevel.LEVEL_4) {
            Level4Atmosphere.applyFill(colors, blockLight); // cold window glow + lightning flashes
            return;
        }
        if (floor == BackroomsLevel.LEVEL_37) {
            Level37Atmosphere.applyFill(colors, blockLight); // warm daylight bounce off white tile
            return;
        }
        if (floor == BackroomsLevel.LEVEL_39) {
            Level39Atmosphere.applyFill(colors, blockLight); // facade glow, orb-lit tunnels
            return;
        }
        float b = Mth.clamp(blockLight / 15.0f, 0.0f, 1.0f);
        // White tile bounces light around, so the Poolrooms get a stronger, softer fill: rooms with
        // a tube read bright (and so does the pool water, which client/water draws from the lit
        // room behind it).
        // Places far from any light stay dark (block light 0 = no fill).
        float maxFill = MAX_FILL;
        float falloff = FILL_FALLOFF;
        if (floor == BackroomsLevel.POOLROOMS) {
            maxFill = POOLROOMS_MAX_FILL;
            falloff = POOLROOMS_FILL_FALLOFF;
        }
        float fill = maxFill * (float) Math.pow(b, falloff);
        // The fill dies with the lights (blackout fade) and dims with the alarm; a flicker halves it.
        fill *= FixturePower.mainsPower() * FixturePower.alarmDim();
        if (FixturePower.flickering(System.currentTimeMillis())) {
            fill *= 0.5f;
        }
        // Warm yellow by default, neutral grey concrete in the garage and a cool lavender-blue in
        // the Poolrooms (shaded white tile in the footage reads ~#A9A3A8).
        float tr = 1.0f;
        float tg = 0.92f;
        float tb = 0.55f;
        if (floor == BackroomsLevel.LEVEL_1) {
            tr = 0.96f;
            tg = 0.96f;
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
     * Level 2 (both modes). Two parts:
     * <ul>
     *   <li>The amber fill: the caged lamps' bounce, following the vanilla block light (curve
     *       {@code falloff}, up to {@code maxFill}). It dies with the blackout, dims with the alarm,
     *       stutters with a flicker, and drops to {@code deadFill} when the corridor section you
     *       stand in lost its circuit (BackroomsLights#level2LampShare). Where battery lights take
     *       over (the brownout's violet, the emergency lamps' red or amber) it takes their colour.</li>
     *   <li>The cold readable dark ({@link #LEVEL2_AMBIENT_R}): a faint blue-grey under everything
     *       a lamp reaches at all, dead or alive, so a dead section is dark but never a black void.</li>
     * </ul>
     * With Veil the lamps' own pools (client/light) sit on top; without Veil this is all the light.
     */
    private static void applyLevel2Fill(Vector3f colors, int blockLight, float maxFill, float falloff, float deadFill,
                                        boolean fallback) {
        float b = Mth.clamp(blockLight / 15.0f, 0.0f, 1.0f);
        float power = FixturePower.mainsPower();
        float fill = maxFill * (float) Math.pow(b, falloff);
        fill *= power * FixturePower.alarmDim();
        if (FixturePower.flickering(System.currentTimeMillis())) {
            // Without Veil the fill is the lamp light, so it stutters with the lamps instead of just halving.
            fill *= fallback ? (flickerDark() ? LEVEL0_FLICKER_DARK : LEVEL0_FLICKER_LIT) : 0.5f;
        }
        // The section of corridor you're in went dark (its series circuit popped): so does the fill.
        fill *= Mth.lerp(BackroomsLights.level2LampShare(), deadFill, 1.0f);
        // Amber from the caged lamps, the battery lights' colour where they take over.
        float battery = BackroomsLights.emergencyShare();
        float tr = Mth.lerp(battery, LEVEL2_TINT_R, BackroomsLights.emergencyRed());
        float tg = Mth.lerp(battery, LEVEL2_TINT_G, BackroomsLights.emergencyGreen());
        float tb = Mth.lerp(battery, LEVEL2_TINT_B, BackroomsLights.emergencyBlue());
        // During the alarm the fill turns red.
        float alarm = FixturePower.alarm();
        tr = Mth.lerp(alarm, tr, 1.0f);
        tg = Mth.lerp(alarm, tg, 0.12f);
        tb = Mth.lerp(alarm, tb, 0.10f);
        // The cold readable dark: wherever any lamp's light reaches, a little less in a blackout.
        float ambient = Mth.clamp(blockLight / LEVEL2_AMBIENT_FULL_FROM, 0.0f, 1.0f)
                * Mth.lerp(power, LEVEL2_AMBIENT_BLACKOUT, 1.0f);
        colors.set(Math.min(1.0f, UNLIT_R + ambient * LEVEL2_AMBIENT_R + fill * tr),
                Math.min(1.0f, UNLIT_G + ambient * LEVEL2_AMBIENT_G + fill * tg),
                Math.min(1.0f, UNLIT_B + ambient * LEVEL2_AMBIENT_B + fill * tb));
    }

    /**
     * Level 0 (both modes). The fill follows the vanilla block light: from {@value #LEVEL0_FULL_FROM}
     * up (beside a panel) it is full, below that it falls along a curve ({@code curve}) plus a
     * straight-line share ({@code linear}), and at {@code darkBelow} and below it is black. Block
     * light flows around walls and drops by one per block, so it is brightest under the panels and
     * darker in corners and behind pillars. With Veil it is only the soft bounce under the lamps;
     * without Veil it is the whole light. Panels flicker and dim with the alarm together, so the
     * fill follows them, and it takes {@code power} (where the power is out, see FixturePower).
     */
    private static void applyLevel0Fill(Vector3f colors, int blockLight, float maxFill, float darkBelow, float curve,
                                        float linear, float power) {
        float t = Mth.clamp((blockLight - darkBelow) / (LEVEL0_FULL_FROM - darkBelow), 0.0f, 1.0f);
        // The curved part (the pools) plus a weak, evenly fading part (the last of the reach).
        float fill = maxFill * ((float) Math.pow(t, curve) + linear * t);
        fill *= power * FixturePower.alarmDim();
        // A faulty tube near you cutting out takes part of the light around you with it.
        fill *= 1.0f - LEVEL0_TUBE_DIP * (1.0f - BackroomsLights.level0TubeShare());
        if (FixturePower.flickering(System.currentTimeMillis())) {
            // The fill is most of the light here, so it stutters with the tubes instead of just halving.
            fill *= flickerDark() ? LEVEL0_FLICKER_DARK : LEVEL0_FLICKER_LIT;
        }
        // During the alarm the halls turn red.
        float alarm = FixturePower.alarm();
        float tr = Mth.lerp(alarm, LEVEL0_TINT_R, 1.0f);
        float tg = Mth.lerp(alarm, LEVEL0_TINT_G, 0.12f);
        float tb = Mth.lerp(alarm, LEVEL0_TINT_B, 0.10f);
        colors.set(Math.min(1.0f, LEVEL0_UNLIT_R + fill * tr), Math.min(1.0f, LEVEL0_UNLIT_G + fill * tg),
                Math.min(1.0f, LEVEL0_UNLIT_B + fill * tb));
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
        if (Level9Atmosphere.inField()) {
            colors.set(FIELD_UNLIT_R + fill * tr, FIELD_UNLIT_G + fill * tg, FIELD_UNLIT_B + fill * tb);
            return;
        }
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

    /** How far NightShot lifts every lightmap colour toward white. */
    private static final float NIGHTSHOT_LIFT = 0.4f;

    /**
     * The NightShot lift in the lightmap right now (0 when it is off or the battery is dead).
     * Level 0's screen pass leaves this part of the light alone (client/light/Level0LightRenderer).
     */
    public static float lightmapLift() {
        return CameraClientState.battery > 0.0f && CameraClientState.nightShot ? NIGHTSHOT_LIFT : 0.0f;
    }

    /** NightShot lifts everything toward white; a dead battery darkens it all. Level 94's own lightmap uses it too. */
    static void applyCamcorder(Vector3f colors) {
        if (CameraClientState.battery <= 0.0f) {
            colors.mul(0.25f);
        } else if (CameraClientState.nightShot) {
            float lift = NIGHTSHOT_LIFT;
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
