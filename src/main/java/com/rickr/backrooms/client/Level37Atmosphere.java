package com.rickr.backrooms.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.rickr.backrooms.Backrooms;
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
 *   <li><b>Haze:</b> a soft warm-white haze in the bright rooms that turns into dim olive gloom in
 *       the dark tunnels: the fog colour follows how much light is around you.</li>
 *   <li><b>Walking into the light:</b> in the Poolrooms, nearing a sunlit archway slowly whitens
 *       the screen (the move itself datamoshes, like every move between floors).</li>
 * </ul>
 * Under water the Water renderer's own fog applies instead.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level37Atmosphere implements LayeredDraw.Layer {
    // ---- Fill light ------------------------------------------------------------------------------
    /** "No light at all": a very dark warm olive (the far end of a tunnel, #2d2e20 darkened). */
    private static final float UNLIT_R = 0.020f;
    private static final float UNLIT_G = 0.021f;
    private static final float UNLIT_B = 0.015f;
    /**
     * The bounce in a lit room. It is the SAME for every light level from {@link #FULL_FROM} up:
     * vanilla light falls by one level per block, and a fill that followed it stepped visibly from
     * block to block up every round pillar (curved pieces are lit per block). The brightness
     * differences inside a room come from the sun beams instead.
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
    private static final float HAZE_R = 0.88f;
    private static final float HAZE_G = 0.85f;
    private static final float HAZE_B = 0.75f;
    private static final float GLOOM_R = 0.16f;
    private static final float GLOOM_G = 0.165f;
    private static final float GLOOM_B = 0.12f;
    private static final float BRIGHT_FOG_START = 10.0f;
    private static final float BRIGHT_FOG_END = 120.0f;
    private static final float DARK_FOG_START = 4.0f;
    private static final float DARK_FOG_END = 70.0f;

    // ---- Glow ------------------------------------------------------------------------------------
    /** Overexposed warm white. */
    private static final int GLOW_RGB = 0xFFF6E2;
    /** In the Poolrooms the screen starts whitening this far from the archway's light (blocks) ... */
    private static final double APPROACH_FROM = 7.0;
    /** ... and is this white when you are right at it. */
    private static final float APPROACH_MAX = 0.85f;

    /** 0..1: how bright it is around you (smoothed vanilla block light at your eyes). */
    private static float brightness = 1.0f;
    private static float approach;

    /** True when the client is in Level 37. */
    public static boolean inLevel37() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_37;
    }

    /** Lightmap fill for Level 37 (Veil mode): see the class comment. */
    public static void applyFill(Vector3f colors, int blockLight) {
        float t = Mth.clamp((blockLight - DARK_BELOW) / (FULL_FROM - DARK_BELOW), 0.0f, 1.0f);
        float fill = MAX_FILL * t * t * (3.0f - 2.0f * t);
        colors.set(UNLIT_R + fill * TINT_R, UNLIT_G + fill * TINT_G, UNLIT_B + fill * TINT_B);
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
            float target = mc.level.getBrightness(LightLayer.BLOCK, eyes) / 15.0f;
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
