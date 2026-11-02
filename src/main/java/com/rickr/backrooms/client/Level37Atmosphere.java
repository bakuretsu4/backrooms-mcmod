package com.rickr.backrooms.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.light.BackroomsLights;
import com.rickr.backrooms.client.light.SunBeams;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;

/**
 * The air and light of Level 37 ("Sublimity"), and the way in.
 *
 * <ul>
 *   <li><b>Fill light</b> ({@link #applyFill}, called by {@link ClientLighting}): daylight bounced
 *       around white tile. Strong and soft, warm (#f1e9bf-ish), so lit rooms read bright and
 *       pale, while far from any opening (deep in a round tunnel, the end of a long corridor)
 *       it falls off to a dark olive, like the references.</li>
 *   <li><b>Haze:</b> a soft warm haze in the bright rooms (about as bright as lit tile, so dark
 *       tunnels fade into darkness instead of milky grey) that turns into dim olive gloom in the
 *       dark tunnels: the fog colour follows how much light is around you.</li>
 *   <li><b>Walking into the light:</b> in the Poolrooms, nearing a sunlit archway slowly whitens
 *       the screen (the move itself datamoshes, like every move between floors).</li>
 * </ul>
 * Under water the Water renderer's own fog applies instead.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level37Atmosphere implements LayeredDraw.Layer {
    // ---- Fill light ------------------------------------------------------------------------------
    /**
     * "No light at all": a very dark warm olive (the far end of a tunnel). Vanilla's brightness
     * slider lifts the lightmap after this (at the default 50% a 0.020 black came out ~0.077, a milky
     * grey on camera), so it starts near black: ~0.045 after the slider.
     */
    private static final float UNLIT_R = 0.0060f;
    private static final float UNLIT_G = 0.0063f;
    private static final float UNLIT_B = 0.0045f;
    /**
     * The bounce in a lit room. It is the SAME for every light level from {@link #FULL_FROM} up:
     * the level's hidden lights are placed only to say "this room is lit", so the light level
     * inside a lit room means nothing and the room reads evenly bright (the camera meters it the
     * same way, {@link #litShare}). The brightness differences inside a room come from the sun beams
     * and the face shading instead. (Curves are now lit smoothly per vertex, client/CurveTileRenderer,
     * so this is a look choice, no longer a workaround for per-block steps on round pillars.)
     */
    private static final float MAX_FILL = 0.52f;
    /** Light level from which a place counts as fully lit (rooms keep their hidden lights dense). */
    private static final float FULL_FROM = 6.0f;
    /** Below this it is pitch dark (the far end of a tunnel); in between the fill fades in. */
    private static final float DARK_BELOW = 1.0f;
    private static final float TINT_R = 1.0f;
    private static final float TINT_G = 0.955f;
    private static final float TINT_B = 0.83f;

    // ---- Haze ------------------------------------------------------------------------------------
    /**
     * The haze in a lit room: warm, and about as bright as lit tile on screen (not near-white), so
     * distant lit rooms stay as they are while the far end of a dark tunnel only greys a little
     * (it used to be 0.88 from 10 blocks: a tunnel 60 blocks away came out ~0.39 before the grade,
     * milky instead of dark). It starts a little later and thickens more slowly too.
     */
    private static final float HAZE_R = 0.52f;
    private static final float HAZE_G = 0.50f;
    private static final float HAZE_B = 0.43f;
    private static final float GLOOM_R = 0.16f;
    private static final float GLOOM_G = 0.165f;
    private static final float GLOOM_B = 0.12f;
    private static final float BRIGHT_FOG_START = 18.0f;
    private static final float BRIGHT_FOG_END = 160.0f;
    private static final float DARK_FOG_START = 4.0f;
    private static final float DARK_FOG_END = 70.0f;

    // ---- Glow ------------------------------------------------------------------------------------
    /** Overexposed warm white. */
    private static final int GLOW_RGB = 0xFFF6E2;
    /** In the Poolrooms the screen starts whitening this far from the archway's light (blocks) ... */
    private static final double APPROACH_FROM = 7.0;
    /** ... and is this white when you are right at it. */
    private static final float APPROACH_MAX = 0.85f;

    /**
     * 0..1: how bright it is around you: the smoothed share of the room fill at your eyes
     * ({@link #litShare}; the raw block light without Veil).
     */
    private static float brightness = 1.0f;
    private static float approach;

    /** True when the client is in Level 37. */
    public static boolean inLevel37() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_37;
    }

    /** Lightmap fill for Level 37 (Veil mode): see the class comment. */
    public static void applyFill(Vector3f colors, int blockLight) {
        float fill = MAX_FILL * litShare(blockLight);
        colors.set(UNLIT_R + fill * TINT_R, UNLIT_G + fill * TINT_G, UNLIT_B + fill * TINT_B);
    }

    /**
     * How much of the full room fill a block light level gets (0..1): everything from
     * {@link #FULL_FROM} up is fully lit, so a room's hidden lights at level 6 look exactly like
     * level 15. The camera's meters (CamcorderEffect's auto-exposure, EyeAdaptation) use this in
     * Level 37 instead of the raw light level, which made them open up by 13-30% in rooms that are
     * just as bright as any other (and flare by ~35% for a second when you turned to an opening).
     * Only true while Veil's lights run: without them the vanilla light curve is the light.
     */
    public static float litShare(int blockLight) {
        float t = Mth.clamp((blockLight - DARK_BELOW) / (FULL_FROM - DARK_BELOW), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /** True when the camera should meter Level 37 by {@link #litShare} (in Level 37 with Veil's lights on). */
    public static boolean metersByFill(Minecraft mc) {
        return mc.level != null && BackroomsLights.isVeilLightingActive()
                && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_37;
    }

    // ------------------------------------------------------------------------------------------
    // Ticking: brightness around you, the archway's pull of light
    // ------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        BackroomsLevel floor = mc.level == null ? null : BackroomsLevel.fromDimension(mc.level.dimension());
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (floor == BackroomsLevel.LEVEL_37) {
            BlockPos eyes = BlockPos.containing(mc.player.getEyePosition());
            int light = mc.level.getBrightness(LightLayer.BLOCK, eyes);
            float target = metersByFill(mc) ? litShare(light) : light / 15.0f;
            brightness += (target - brightness) * 0.05f;
        }
        float target = 0.0f;
        if (floor == BackroomsLevel.POOLROOMS) {
            Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
            for (SunBeams.Beam beam : SunBeams.beams()) {
                if (beam.archway()) {
                    double d = Math.sqrt(beam.distSq(eye));
                    float t = (float) Mth.clamp((APPROACH_FROM - d) / (APPROACH_FROM - 1.0), 0.0, 1.0);
                    target = Math.max(target, t * t * (3.0f - 2.0f * t) * APPROACH_MAX);
                }
            }
        }
        approach += (target - approach) * 0.15f;
    }

    // ------------------------------------------------------------------------------------------
    // Haze
    // ------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE || !inLevel37()) {
            return;
        }
        event.setNearPlaneDistance(Mth.lerp(brightness, DARK_FOG_START, BRIGHT_FOG_START));
        event.setFarPlaneDistance(Mth.lerp(brightness, DARK_FOG_END, BRIGHT_FOG_END));
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true); // required for NeoForge to use our distances
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inLevel37() || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float t = (float) Math.pow(brightness, 0.8);
        event.setRed(Mth.lerp(t, GLOOM_R, HAZE_R));
        event.setGreen(Mth.lerp(t, GLOOM_G, HAZE_G));
        event.setBlue(Mth.lerp(t, GLOOM_B, HAZE_B));
    }

    // ------------------------------------------------------------------------------------------
    // The white glow
    // ------------------------------------------------------------------------------------------

    /** Mod bus: draws the glow over everything else. */
    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Backrooms.id("level37_glow"), new Level37Atmosphere());
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        float alpha = approach;
        if (alpha <= 0.003f) {
            return;
        }
        int a = Mth.clamp((int) (alpha * 255.0f), 0, 255);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (a << 24) | GLOW_RGB);
    }
}
