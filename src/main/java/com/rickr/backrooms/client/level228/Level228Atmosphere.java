package com.rickr.backrooms.client.level228;

import com.mojang.blaze3d.shaders.FogShape;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.AtmosphereGrade;
import com.rickr.backrooms.client.ClientLighting;
import com.rickr.backrooms.client.light.BackroomsLights;
import com.rickr.backrooms.client.light.FixturePower;
import com.rickr.backrooms.client.light.Level228Lamps;
import com.rickr.backrooms.client.water.VanillaWaterHider;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
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
 * How Level 228 (the Maze of Mirrors) looks: the dimension effects for {@code backrooms:mirrors}.
 *
 * <ul>
 *   <li><b>Light.</b> Sectors lit blue, green or red in varying shades by the panels in the ceiling,
 *       mostly dark between them. With Veil the panels are coloured, shadowed point lights
 *       (client/light: {@code FixtureType.L228_*}, {@link Level228Lamps}) and the lightmap only
 *       adds a dim bounce; without Veil the lightmap fill is the light. Either way the fill and
 *       the dark take the colour of the light around you ({@link Level228Lamps#glowRed()} ...),
 *       since the lightmap can only have one colour: in a red sector even the dark is a red dark.</li>
 *   <li><b>Fog.</b> Short: a mirror corridor shows its first three or so images (each seen through
 *       one more pane of green glass, so each a little darker) and then recedes into dark air of
 *       the sector's colour. Vanilla's fog does that without a shader pack; the camcorder's haze
 *       ({@link #mood()}, the same colour) does it with one, where the pack draws its own fog.</li>
 *   <li><b>Grade.</b> Colour is kept (a touch more saturation than the textures), the shadows lean
 *       toward the sector's colour and the coloured panels bloom softly. Under an Iris shader pack
 *       the pack lights the maze with its own uncoloured (warm) block light, so the grade leans
 *       much harder toward the sector colour there and the haze is thicker: with the emissive
 *       coloured panel faces, the colour still reads.</li>
 * </ul>
 * The exit mirror is black full-bright glass with a faint cold shimmer (its texture) and the
 * faintest cold glow on the floor in front of it ({@code FixtureType.L228_EXIT}).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level228Atmosphere extends DimensionSpecialEffects {
    /** Full-bright faces (the panels, the exit): just under white, so the colour survives the bloom. */
    private static final float EMISSIVE = 0.92f;
    // The first tuning (near-black unlit, a thin fill, fog and haze from 2-3 blocks in near-black
    // air, a hard pull toward the sector colour) put a corridor at 2-4 / 255 on camera in both
    // renderers. The maze must read: corridors, glass, your reflections and the receding copies,
    // in coloured pools with dimmer gaps, never black (a corridor around 25-45 / 255).
    /** "No light at all": a dim glow of the sector's colour, so even a gap between pools shows its walls. */
    private static final float UNLIT = 0.012f;
    private static final float UNLIT_TINT = 0.035f;
    /**
     * With Veil: the lightmap's bounce (the Veil lights make the pools; this only carries what is
     * past their budget, faintly, in the sector's own colour). Round 2's 0.38 on top of the many
     * overlapping Veil lights washed the maze out to a near-white pink (~120 / 255).
     */
    private static final float VEIL_FILL = 0.10f;
    private static final float VEIL_FALLOFF = 2.4f;
    /** Without Veil the fill is the light: bright under the panels (light 13-15), dimmer between (9-11). */
    private static final float FALLBACK_FILL = 0.95f;
    private static final float FALLBACK_FALLOFF = 1.5f;
    /** How coloured the fill is: 1 = the sector colour exactly, 0 = white (without Veil / with Veil). */
    private static final float FILL_COLOUR = 0.80f;
    private static final float VEIL_FILL_COLOUR = 1.0f;

    // Fog (vanilla terrain fog; no shader pack): clear for a corridor's length, gone by FOG_END,
    // so a mirror corridor shows three or four images and then recedes into coloured air.
    private static final float FOG_START = 8.0f;
    private static final float FOG_END = 44.0f;
    /** The fog's colour: the sector colour this bright, lighter where you stand in the light. */
    private static final float FOG_DARK = 0.07f;
    private static final float FOG_LIT = 0.13f;

    /**
     * The camcorder grade's split-tone pull toward the sector colour: how far (0 = none), without
     * and with a shader pack (a pack's warm block light has to be pulled round harder; too hard and
     * it only darkens the picture).
     */
    private static final float TONE_PULL = 0.30f;
    private static final float TONE_PULL_PACK = 0.50f;
    /** The camcorder haze: from this far, this thick per block, at most this much (without / with a pack). */
    private static final float HAZE_START = 6.0f;
    private static final float HAZE_DENSITY = 0.035f;
    private static final float HAZE_MAX = 0.55f;
    private static final float HAZE_START_PACK = 5.0f;
    private static final float HAZE_DENSITY_PACK = 0.045f;
    private static final float HAZE_MAX_PACK = 0.62f;
    /** The haze's colour: the sector colour this bright (a lit, coloured air, not black); dimmer with Veil. */
    private static final float HAZE_LIGHT = 0.11f;
    private static final float VEIL_HAZE_LIGHT = 0.06f;

    /** The grade for the light around you, rebuilt once a tick (the camcorder reads it every frame). */
    private static AtmosphereGrade.Mood mood = buildMood(0.20f, 0.42f, 1.0f, false, false);
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
     * level of 15 only comes from full-bright faces (the panels and the exit mirror).
     */
    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker,
                                     float skyLight, int pixelX, int pixelY, Vector3f colors) {
        // A level event (blackout, alarm) takes the panels and their light down with the Veil lights.
        float power = FixturePower.mainsPower();
        if (pixelY == 15) {
            float v = EMISSIVE * Math.max(0.05f, power);
            colors.set(v, v, v);
        } else {
            float r = Level228Lamps.glowRed();
            float g = Level228Lamps.glowGreen();
            float b = Level228Lamps.glowBlue();
            boolean veil = BackroomsLights.isVeilLightingActive();
            float fill = (veil ? VEIL_FILL : FALLBACK_FILL) * power * FixturePower.alarmDim()
                    * (float) Math.pow(Math.max(0, Math.min(15, pixelX)) / 15.0f, veil ? VEIL_FALLOFF : FALLBACK_FALLOFF);
            float colour = veil ? VEIL_FILL_COLOUR : FILL_COLOUR;
            colors.set(UNLIT + UNLIT_TINT * r + fill * tinted(r, colour),
                    UNLIT + UNLIT_TINT * g + fill * tinted(g, colour),
                    UNLIT + UNLIT_TINT * b + fill * tinted(b, colour));
        }
        ClientLighting.applyCamcorder(colors);
    }

    private static float tinted(float channel, float colour) {
        return 1.0f - colour + colour * channel;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!inLevel228()) {
            packDraws = false;
            return;
        }
        packDraws = VanillaWaterHider.shaderPackInUse();
        mood = buildMood(Level228Lamps.glowRed(), Level228Lamps.glowGreen(), Level228Lamps.glowBlue(), packDraws,
                !packDraws && BackroomsLights.isVeilLightingActive());
    }

    /**
     * The grade for a sector colour (r, g, b, brightest channel 1), per renderer:
     * <ul>
     *   <li><b>Shader pack</b> (checked in game, round 2: 33-48 / 255, readable sectors; keep):
     *       exposure up (the pack lights the dark surfaces by block light alone), a firmer pull
     *       toward the sector colour, a lit coloured haze.</li>
     *   <li><b>Veil</b>: the coloured Veil lights already carry the colour and the light, so no
     *       exposure boost (1.30 washed it out), a little more contrast and colour so the pools stay
     *       saturated and fall off to dim coloured shadow, a dimmer haze, firmer contact shadows.</li>
     *   <li><b>Neither</b> (no Veil, no pack): the lightmap fill is the light; the round-2 values.</li>
     * </ul>
     * Always: a lifted black in the sector's colour (never fully black), soft bloom, calm auto-iris.
     */
    private static AtmosphereGrade.Mood buildMood(float r, float g, float b, boolean pack, boolean veil) {
        float pull = pack ? TONE_PULL_PACK : TONE_PULL;
        float hiPull = pull * 0.5f;
        float haze = veil ? VEIL_HAZE_LIGHT : HAZE_LIGHT;
        float lift = veil ? 0.008f : 0.014f;
        return new AtmosphereGrade.Mood(pack ? 1.25f : veil ? 1.0f : 1.30f, veil ? 1.10f : 1.04f,
                pack ? 1.15f : veil ? 1.18f : 1.10f,
                tone(r, pull), tone(g, pull), tone(b, pull),
                tone(r, hiPull), tone(g, hiPull), tone(b, hiPull),
                lift * r, lift * g, lift * b,
                haze * r, haze * g, haze * b,
                pack ? HAZE_START_PACK : HAZE_START, pack ? HAZE_DENSITY_PACK : HAZE_DENSITY, pack ? HAZE_MAX_PACK : HAZE_MAX,
                veil ? 0.32f : 0.22f, 0.22f, 0.010f);
    }

    /**
     * A split-tone multiplier for one channel of the sector colour: 1 when {@code pull} is 0; at
     * full pull 0.55 for a channel the light lacks, 1.15 for its own (the picture's brightness
     * stays about the same while its colour turns).
     */
    private static float tone(float channel, float pull) {
        return 1.0f + pull * (0.55f + 0.6f * channel - 1.0f);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || !inLevel228()) {
            return;
        }
        event.setNearPlaneDistance(FOG_START);
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), FOG_END));
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true); // required for NeoForge to use our distances
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inLevel228() || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float k = FOG_DARK + (FOG_LIT - FOG_DARK) * Level228Lamps.lit();
        event.setRed(Level228Lamps.glowRed() * k);
        event.setGreen(Level228Lamps.glowGreen() * k);
        event.setBlue(Level228Lamps.glowBlue() * k);
    }

    /** F3 screen (the event only fires while it is open): the light around you, for checking the sectors in game. */
    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (!inLevel228()) {
            return;
        }
        event.getLeft().add(String.format(java.util.Locale.ROOT, "L228 light: %.2f %.2f %.2f lit %.2f%s",
                Level228Lamps.glowRed(), Level228Lamps.glowGreen(), Level228Lamps.glowBlue(), Level228Lamps.lit(),
                packDraws ? " (shader pack)" : BackroomsLights.isVeilLightingActive() ? " (Veil)" : " (no Veil)"));
    }
}
