package com.rickr.backrooms.client.level228;

import com.mojang.blaze3d.shaders.FogShape;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.ClientLighting;
import com.rickr.backrooms.client.Level228ClientState;
import com.rickr.backrooms.client.light.BackroomsLights;
import com.rickr.backrooms.client.light.FixturePower;
import com.rickr.backrooms.client.light.Level228Bulbs;
import com.rickr.backrooms.client.water.VanillaWaterHider;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.layout.Level228Plan;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * How Level 228 v2 (the Hall of Mirrors, an abandoned carnival mirror maze) looks: the dimension
 * effects for {@code backrooms:mirrors}, the fog and the camcorder grade.
 *
 * <ul>
 *   <li><b>Light.</b> Warm dim incandescent bulbs (~2500 K), many dead, a few failing, pools of
 *       warm light with real darkness between (client/light/Level228Bulbs). Under Veil they are
 *       shadowed point lights and the lightmap only adds a weak warm bounce; without Veil (and
 *       under an Iris pack, which lights by block light itself) the block light of the invisible
 *       bulb blocks IS the light, so the lightmap's block-light curve here is the pool. Its colour
 *       follows the light around you: warm under bulbs, pale and cold in the exit hallway. There
 *       is no colour anywhere else: v1's green/blue/red sectors are gone.</li>
 *   <li><b>Fog.</b> Dark warm grey air. A mirrored colonnade must show four to six repetitions
 *       of itself and then lose them: vanilla's terrain fog clears for the first few blocks and is
 *       opaque by 24 in the maze zones (the sealed images behind their perimeter mirrors end 24
 *       blocks behind the glass; zone-dependent, see {@link Look}); under a shader pack, which
 *       draws its own fog, the camcorder's haze (same colour) does the hiding.</li>
 *   <li><b>Grade.</b> Neutral, very slightly warm, deep blacks that are not crushed (a tiny
 *       neutral lift), faded camcorder colour, the bulbs blooming softly. Per renderer: a shader
 *       pack lights the dark surfaces by block light alone, so exposure is up; Veil already lights
 *       the pools, so no boost; vanilla in between. Under a pack the static block light can't
 *       flicker, so the exposure dips with the share of your light that a failing bulb has just
 *       taken away ({@link Level228Bulbs#outShare}).</li>
 *   <li><b>Zones.</b> Each zone shifts the look a little ({@link Look}): the Glass Labyrinth is
 *       darker, colder and closer; the final chamber tight and deep; the exit hallway cold, clearer
 *       and longer. Eased over about a second as you walk from one to the next.</li>
 * </ul>
 *
 * <p>Tunables are the constants below and the {@link #LOOKS} table. Check with F3 (the
 * "L228" line: zone, light, renderer) and compare screenshots against the targets: a lit
 * corridor's mean luminance about 25-45 / 255 under both renderers.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level228Atmosphere extends DimensionSpecialEffects {
    /** Full-bright faces (bulb glass, signs): just under white, so their own colour survives the bloom. */
    private static final float EMISSIVE = 0.92f;
    /** "No light at all": a deep warm black, not zero (the grade's lift keeps it off pure black). */
    private static final float UNLIT = 0.010f;
    /**
     * Without Veil the block light is the light: (light / 15)^FALLOFF times FILL. Under a bulb
     * (block light 9 on the floor) that is ~0.43, halfway to the next live bulb (4-5) ~0.12-0.15:
     * a pool falling off into real darkness.
     */
    private static final float FALLBACK_FILL = 0.9f;
    private static final float FALLBACK_FALLOFF = 1.45f;
    /**
     * With Veil: only a weak bounce under the Veil pools (more washes the pools out). 0.11 since
     * the bulbs' block light went up one level (Level228BulbBlock), so the bounce stays as it was.
     */
    private static final float VEIL_FILL = 0.11f;
    private static final float VEIL_FALLOFF = 2.2f;
    /** How much the fill takes the colour of the light around you (0 = white). */
    private static final float FILL_COLOUR = 0.65f;
    /** The fog's brightness in the dark and in a lit pool (times the zone's fog colour). */
    private static final float FOG_DARK = 1.0f;
    private static final float FOG_LIT = 1.7f;
    /** How far the fog takes the colour of the nearby light (a red EXIT sign, a cold tube). */
    private static final float FOG_TINT = 0.18f;
    /** Exposure dip under a pack / without Veil when all the light at your spot is out. */
    private static final float FLICKER_DIP = 0.45f;
    /** Zone look easing per tick (about a second to settle). */
    private static final float LOOK_EASE = 0.06f;
    /** ... but the fog thickens this fast per tick (about a quarter second). */
    private static final float FOG_THICKEN = 0.35f;

    /**
     * One zone's look: vanilla fog start/end (blocks) and colour (dark), exposure multiplier,
     * contrast, saturation, shadow tint, highlight tint, haze density / max, black lift, and the
     * camcorder haze under a shader pack (start, density, max), which draws its own fog we can't
     * shorten, so there the haze has to do the hiding. Last, the shader-pack exposure multiplier
     * and contrast: Complementary lights only by block light, and the zones' materials take it
     * very differently (the Alhambra's pale plaster and tile throw it back, the Crystal Maze's
     * dark wood and the Glass Labyrinth's black steel swallow it), so the pack's exposure is set
     * per zone from in-game readings (2026-11 review: Alhambra 26-50 with Iris, Crystal 6-16,
     * Funhouse 6-20, Glass 7-18, chamber 13-14; targets >= 25 in lit corridors, Glass >= 18,
     * chamber ~30). Then the Veil-only exposure multiplier: round 2 (fresh world, the denser
     * bulbs of Crystal and Funhouse) read Veil at Crystal 113, Funhouse 93, Alhambra 66, foyer 47,
     * chamber 20, glass 22 while the pack path was right everywhere. Together with the bulbs'
     * weaker Veil light (FixtureType.L228_BULB 1.5 -> 1.0, which only Veil uses) this brings Veil to
     * about Crystal 53, Funhouse 52, Alhambra 42, foyer 33, chamber 28, glass unchanged.
     */
    private record Look(float fogStart, float fogEnd, float fogR, float fogG, float fogB, float exposure,
                        float contrast, float saturation, float shR, float shG, float shB,
                        float hiR, float hiG, float hiB, float hazeDensity, float hazeMax, float lift,
                        float packStart, float packDensity, float packMax, float packExposure, float packContrast,
                        float veilExposure) {}

    /*
     * The four maze zones (Crystal, Alhambra, Funhouse, Glass): the sealed image volumes behind
     * their perimeter mirrors end Level228Plan.FOG_DEPTH (24) blocks behind the glass, so seen in a
     * mirror right beside you the end of the "infinite" colonnade is ~24.5 blocks away. The fog
     * must be fully opaque by then (vanilla: linear 3.5 -> 24, so at pitch 3 you still see six or
     * seven repetitions of the columns fading out); under a shader pack the haze reaches ~95 % by
     * 24 (from 8 blocks at 0.22 per block: 58 % at 12, 83 % at 16), so no half-fogged flat end shows.
     */
    private static final float MAZE_FOG_START = 3.5f;
    private static final float MAZE_FOG_END = 24.0f;
    private static final float MAZE_PACK_START = 8.0f;
    private static final float MAZE_PACK_DENSITY = 0.22f;
    private static final float MAZE_PACK_MAX = 0.97f;

    private static final Look CRYSTAL = new Look(MAZE_FOG_START, MAZE_FOG_END, 0.050f, 0.044f, 0.037f, 1.0f,
            1.10f, 0.86f, 0.99f, 0.99f, 1.0f, 1.03f, 1.0f, 0.93f, 0.032f, 0.58f, 0.0060f,
            MAZE_PACK_START, MAZE_PACK_DENSITY, MAZE_PACK_MAX, 1.50f, 1.02f, 0.70f);
    /** Index = Level228Plan.Zone ordinal. */
    private static final Look[] LOOKS = {
            // FOYER: the ticket hall is a little roomier and lighter, the fog a touch further off
            // (its images are its own whole room, not a sealed volume that ends).
            new Look(8.0f, 46.0f, 0.052f, 0.045f, 0.037f, 1.02f, 1.08f, 0.88f, 0.99f, 0.99f, 1.0f, 1.03f, 1.0f, 0.93f,
                    0.028f, 0.55f, 0.0062f, 5.0f, 0.034f, 0.55f, 1.05f, 1.06f, 1.05f),
            CRYSTAL,
            // ALHAMBRA: painted plaster and tile throw a warmer, slightly amber light back (and
            // the most light: under a pack it is the one zone that is turned down).
            new Look(MAZE_FOG_START, MAZE_FOG_END, 0.053f, 0.045f, 0.035f, 1.0f, 1.08f, 0.88f, 1.0f, 0.99f, 0.98f,
                    1.04f, 1.0f, 0.90f, 0.030f, 0.56f, 0.0060f, MAZE_PACK_START, MAZE_PACK_DENSITY, MAZE_PACK_MAX,
                    0.85f, 1.08f, 0.95f),
            // FUNHOUSE: faded stripes and paint: a little more colour.
            new Look(MAZE_FOG_START, MAZE_FOG_END, 0.050f, 0.044f, 0.038f, 1.0f, 1.10f, 0.92f, 0.99f, 0.99f, 1.0f,
                    1.03f, 1.0f, 0.93f, 0.036f, 0.60f, 0.0060f, MAZE_PACK_START, MAZE_PACK_DENSITY, MAZE_PACK_MAX,
                    1.40f, 1.04f, 0.84f),
            // GLASS: the darkest zone: black steel and glass, cold faint spill, the fog close. The
            // second round doubled its lit cold spills (~7 -> 12 per hall) for the shader pack, so
            // the Veil/plain exposure drops 0.92 -> 0.80 to hold Veil's good 39-41 reading, and the
            // pack multiplier rises 1.55 -> 1.78 to keep the pack's own boost (0.80 x 1.78 = 1.42).
            new Look(MAZE_FOG_START, MAZE_FOG_END, 0.036f, 0.039f, 0.044f, 0.80f, 1.12f, 0.80f, 0.97f, 0.99f, 1.04f,
                    1.0f, 1.0f, 0.98f, 0.045f, 0.66f, 0.0050f, MAZE_PACK_START, MAZE_PACK_DENSITY, MAZE_PACK_MAX,
                    1.78f, 1.02f, 1.0f),
            // CHAMBER: candle bulbs in a small dark room: tight, deep, warm, but the three mirrors'
            // reflections must stay clearly readable (the puzzle's flaws are subtle).
            new Look(4.0f, 26.0f, 0.040f, 0.033f, 0.026f, 0.96f, 1.14f, 0.84f, 1.0f, 0.98f, 0.96f, 1.05f, 0.99f, 0.88f,
                    0.050f, 0.64f, 0.0045f, 5.0f, 0.060f, 0.64f, 1.20f, 1.04f, 1.40f),
            // EXIT_HALL: cold light from outside: pale blue-grey air, clearer and longer.
            new Look(8.0f, 52.0f, 0.052f, 0.057f, 0.066f, 1.04f, 1.06f, 0.80f, 0.96f, 0.99f, 1.05f, 0.98f, 1.0f, 1.04f,
                    0.022f, 0.50f, 0.0065f, 5.0f, 0.026f, 0.50f, 1.0f, 1.06f, 1.0f),
            // BACKSTAGE: narrow plywood passages: close, warm, dim.
            new Look(4.0f, 28.0f, 0.048f, 0.041f, 0.033f, 0.97f, 1.10f, 0.84f, 1.0f, 0.99f, 0.98f, 1.04f, 1.0f, 0.91f,
                    0.040f, 0.62f, 0.0055f, 5.0f, 0.048f, 0.62f, 1.25f, 1.06f, 1.15f),
            CRYSTAL, // NONE (the sealed image spaces, the edge of the plan)
    };

    // Per renderer: exposure, haze start (blocks), contact shadows, glow.
    private static final float EXPOSURE_PACK = 1.30f;
    private static final float EXPOSURE_VEIL = 1.05f;
    private static final float EXPOSURE_PLAIN = 1.15f;
    private static final float HAZE_START = 6.0f;
    private static final float AO_VEIL = 0.34f;
    private static final float AO = 0.28f;
    private static final float GLOW = 0.24f;
    private static final float BREATH = 0.010f;

    // The eased look (current zone) and what the fog/grade read, rebuilt once a tick.
    private static float fogStart = CRYSTAL.fogStart;
    private static float fogEnd = CRYSTAL.fogEnd;
    private static float fogR = CRYSTAL.fogR;
    private static float fogG = CRYSTAL.fogG;
    private static float fogB = CRYSTAL.fogB;
    private static float exposure = CRYSTAL.exposure;
    private static float contrast = CRYSTAL.contrast;
    private static float saturation = CRYSTAL.saturation;
    private static float shR = CRYSTAL.shR;
    private static float shG = CRYSTAL.shG;
    private static float shB = CRYSTAL.shB;
    private static float hiR = CRYSTAL.hiR;
    private static float hiG = CRYSTAL.hiG;
    private static float hiB = CRYSTAL.hiB;
    private static float hazeDensity = CRYSTAL.hazeDensity;
    private static float hazeMax = CRYSTAL.hazeMax;
    private static float lift = CRYSTAL.lift;
    private static float packStart = CRYSTAL.packStart;
    private static float packDensity = CRYSTAL.packDensity;
    private static float packMax = CRYSTAL.packMax;
    private static float packExposure = CRYSTAL.packExposure;
    private static float packContrast = CRYSTAL.packContrast;
    private static float veilExposure = CRYSTAL.veilExposure;

    private static Level228Plan.Zone zone = Level228Plan.Zone.NONE;
    private static AtmosphereGrade.Mood mood = buildMood(false, false);
    /** An Iris shader pack draws the world (asked once a tick, here only). */
    private static boolean packDraws;

    public Level228Atmosphere() {
        // No clouds, no sky (the maze has a ceiling), no ground line.
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    /** Mod bus: Level 228's dimension type uses {@code "effects": "backrooms:mirrors"}. */
    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Backrooms.id("mirrors"), new Level228Atmosphere());
    }

    /** True when the client is in Level 228. */
    public static boolean inLevel228() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_228;
    }

    /** The zone the camera is in (updated once a tick; NONE outside Level 228 or before the seed arrives). */
    public static Level228Plan.Zone zone() {
        return zone;
    }

    /** The camcorder's mood for Level 228 right now (AtmosphereGrade#levelMood). */
    public static AtmosphereGrade.Mood mood() {
        return mood;
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor; // set in onFogColor below
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    @Nullable
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return null;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        return false; // SkyType.NONE: vanilla draws nothing, the ceiling hides it anyway
    }

    /**
     * The lightmap. pixelX is the block light (0..15); there is no sky light in here, so a sky
     * level of 15 only comes from full-bright faces (the bulbs' glowing glass, the signs).
     */
    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker,
                                     float skyLight, int pixelX, int pixelY, Vector3f colors) {
        float power = FixturePower.mainsPower();
        if (pixelY == 15) {
            float v = EMISSIVE * Math.max(0.05f, power);
            colors.set(v, v, v);
        } else {
            boolean veil = BackroomsLights.isVeilLightingActive();
            float fill = (veil ? VEIL_FILL : FALLBACK_FILL) * power * FixturePower.alarmDim()
                    * (float) Math.pow(Mth.clamp(pixelX, 0, 15) / 15.0f, veil ? VEIL_FALLOFF : FALLBACK_FALLOFF);
            colors.set(UNLIT + fill * tinted(Level228Bulbs.colourR()),
                    UNLIT * 0.92f + fill * tinted(Level228Bulbs.colourG()),
                    UNLIT * 0.80f + fill * tinted(Level228Bulbs.colourB()));
        }
        ClientLighting.applyCamcorder(colors);
    }

    private static float tinted(float channel) {
        return 1.0f - FILL_COLOUR + FILL_COLOUR * channel;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!inLevel228() || mc.player == null) {
            packDraws = false;
            zone = Level228Plan.Zone.NONE;
            return;
        }
        packDraws = VanillaWaterHider.shaderPackInUse();
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        zone = Level228ClientState.hasSeed()
                ? Level228Plan.zoneAt(Level228ClientState.seed(), Mth.floor(eye.x), Mth.floor(eye.z)) : Level228Plan.Zone.NONE;
        Look t = LOOKS[zone.ordinal()];
        // Thickening is fast (a few ticks), thinning slow: walking from the foyer into a maze
        // zone must not show the end of a sealed image for the second the fog takes to settle.
        fogStart = fogStart > t.fogStart ? thicken(fogStart, t.fogStart) : ease(fogStart, t.fogStart);
        fogEnd = fogEnd > t.fogEnd ? thicken(fogEnd, t.fogEnd) : ease(fogEnd, t.fogEnd);
        fogR = ease(fogR, t.fogR);
        fogG = ease(fogG, t.fogG);
        fogB = ease(fogB, t.fogB);
        exposure = ease(exposure, t.exposure);
        contrast = ease(contrast, t.contrast);
        saturation = ease(saturation, t.saturation);
        shR = ease(shR, t.shR);
        shG = ease(shG, t.shG);
        shB = ease(shB, t.shB);
        hiR = ease(hiR, t.hiR);
        hiG = ease(hiG, t.hiG);
        hiB = ease(hiB, t.hiB);
        hazeDensity = ease(hazeDensity, t.hazeDensity);
        hazeMax = ease(hazeMax, t.hazeMax);
        lift = ease(lift, t.lift);
        packStart = packStart > t.packStart ? thicken(packStart, t.packStart) : ease(packStart, t.packStart);
        packDensity = packDensity < t.packDensity ? thicken(packDensity, t.packDensity) : ease(packDensity, t.packDensity);
        packMax = packMax < t.packMax ? thicken(packMax, t.packMax) : ease(packMax, t.packMax);
        packExposure = ease(packExposure, t.packExposure);
        packContrast = ease(packContrast, t.packContrast);
        veilExposure = ease(veilExposure, t.veilExposure);
        mood = buildMood(packDraws, !packDraws && BackroomsLights.isVeilLightingActive());
    }

    private static float ease(float from, float to) {
        return from + (to - from) * LOOK_EASE;
    }

    private static float thicken(float from, float to) {
        return from + (to - from) * FOG_THICKEN;
    }

    /** The fog's colour right now: the zone's dark air, lighter in a pool, a hint of the nearby light's colour. */
    private static float fogChannel(float base, float lightChannel) {
        float k = FOG_DARK + (FOG_LIT - FOG_DARK) * Level228Bulbs.lit();
        return base * k * (1.0f - FOG_TINT + FOG_TINT * lightChannel);
    }

    /**
     * The grade for the eased zone look and the renderer. The haze is the fog's colour (so under
     * a shader pack, whose own fog we don't control, the camcorder's haze carries the depth).
     */
    private static AtmosphereGrade.Mood buildMood(boolean pack, boolean veil) {
        float base = pack ? EXPOSURE_PACK : veil ? EXPOSURE_VEIL : EXPOSURE_PLAIN;
        // Static block light can't flicker: dip the exposure with the light a failing bulb took away.
        float dip = veil ? 1.0f : 1.0f - FLICKER_DIP * Level228Bulbs.outShare();
        float hazeR = fogChannel(fogR, Level228Bulbs.colourR());
        float hazeG = fogChannel(fogG, Level228Bulbs.colourG());
        float hazeB = fogChannel(fogB, Level228Bulbs.colourB());
        return new AtmosphereGrade.Mood(base * exposure * dip * (pack ? packExposure : veil ? veilExposure : 1.0f),
                pack ? packContrast : contrast, saturation,
                shR, shG, shB, hiR, hiG, hiB,
                lift, lift * 0.92f, lift * 0.82f,
                hazeR, hazeG, hazeB, pack ? packStart : HAZE_START, pack ? packDensity : hazeDensity, pack ? packMax : hazeMax,
                veil ? AO_VEIL : AO, GLOW, BREATH);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || !inLevel228()) {
            return;
        }
        event.setNearPlaneDistance(fogStart);
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), fogEnd));
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true); // required for NeoForge to use our distances
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inLevel228() || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        event.setRed(fogChannel(fogR, Level228Bulbs.colourR()));
        event.setGreen(fogChannel(fogG, Level228Bulbs.colourG()));
        event.setBlue(fogChannel(fogB, Level228Bulbs.colourB()));
    }

    /** F3 screen (the event only fires while it is open): zone, light and renderer, for checking the look in game. */
    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (!inLevel228()) {
            return;
        }
        event.getLeft().add(String.format(java.util.Locale.ROOT, "L228 %s: lit %.2f out %.2f light %.2f %.2f %.2f fog %.0f-%.0f exp %.2f%s",
                zone, Level228Bulbs.lit(), Level228Bulbs.outShare(), Level228Bulbs.colourR(), Level228Bulbs.colourG(),
                Level228Bulbs.colourB(), fogStart, fogEnd, mood.exposure(),
                packDraws ? " (shader pack)" : BackroomsLights.isVeilLightingActive() ? " (Veil)" : " (no Veil)"));
    }
}
