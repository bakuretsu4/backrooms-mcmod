package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Level369Light;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import com.mojang.blaze3d.shaders.FogShape;
import org.joml.Vector3f;

/**
 * The air and light of Level 369 ("Comely"):
 * <ul>
 *   <li><b>Fill light</b> ({@link #applyFill}, called by {@link ClientLighting}): a soft, warm
 *       golden bounce wherever the skylights, windows and panels reach, easing smoothly with the
 *       light level so it never steps from block to block. Dim light (the dark rooms' glowing
 *       plants) leans cool green-cyan instead, so they read as bioluminescence, not as lamps.</li>
 *   <li><b>Haze:</b> warm cream air in the lit rooms, deep green-black gloom in the dark ones; the
 *       fog colour and distance follow how much light is around you.</li>
 *   <li><b>The drifting light</b> ({@link Level369Light}): as a band of it passes over you, the
 *       whole room warms and brightens to gold, the air turns golden and the camcorder's picture
 *       glows ({@link AtmosphereGrade}); a soft swell of warmth announces it. Then it falls back.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level369Atmosphere {
    // ---- Fill light ------------------------------------------------------------------------------
    /** No light at all: a very dark green-black. */
    private static final float UNLIT_R = 0.012f;
    private static final float UNLIT_G = 0.016f;
    private static final float UNLIT_B = 0.013f;
    private static final float MAX_FILL = 0.56f;
    /** The same without Veil, where this fill is all the light there is. */
    private static final float FALLBACK_FILL = 0.74f;
    /** Light level from which a place counts as fully lit ... */
    private static final float FULL_FROM = 12.0f;
    /** ... and below which there is no fill at all. */
    private static final float DARK_BELOW = 1.5f;
    /** The bounce in a fully lit room: warm gold off cream and peach plaster and green lawn. */
    private static final float WARM_R = 1.0f;
    private static final float WARM_G = 0.94f;
    private static final float WARM_B = 0.78f;
    /** The faint light of the glowing plants. */
    private static final float GLOW_R = 0.55f;
    private static final float GLOW_G = 0.95f;
    private static final float GLOW_B = 0.90f;
    /** At the heart of the drifting light the fill is this much stronger ... */
    private static final float LIGHT_BOOST = 0.55f;
    /** ... and this golden. */
    private static final float GOLD_R = 1.0f;
    private static final float GOLD_G = 0.84f;
    private static final float GOLD_B = 0.56f;
    /** The swell of warmth plays when the light where you stand rises past this. */
    private static final float ARRIVE_AT = 0.2f;

    // ---- Haze ------------------------------------------------------------------------------------
    private static final float HAZE_R = 0.92f;
    private static final float HAZE_G = 0.88f;
    private static final float HAZE_B = 0.78f;
    private static final float GLOOM_R = 0.035f;
    private static final float GLOOM_G = 0.06f;
    private static final float GLOOM_B = 0.055f;
    private static final float BRIGHT_FOG_START = 24.0f;
    private static final float BRIGHT_FOG_END = 190.0f;
    private static final float DARK_FOG_START = 3.0f;
    private static final float DARK_FOG_END = 40.0f;
    private static final float GOLDEN_HAZE_R = 0.98f;
    private static final float GOLDEN_HAZE_G = 0.84f;
    private static final float GOLDEN_HAZE_B = 0.58f;

    /** 0..1: how bright it is around you (smoothed vanilla block light at your eyes). */
    private static float brightness = 1.0f;
    /** 0..1: how much of the drifting light is on you (smoothed). */
    private static float light;
    /** The light's strength last tick, to hear it arrive. */
    private static float lastTarget;

    private Level369Atmosphere() {}

    /** True when the client is in Level 369. */
    public static boolean inLevel369() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_369;
    }

    /** How much of the drifting light is on you right now, 0..1 (for the camcorder's mood). */
    public static float light() {
        return inLevel369() ? light : 0.0f;
    }

    /**
     * Lightmap fill for Level 369: see the class comment. {@code fallback}: without Veil there are
     * no pools of light on top, so the fill is all there is and reaches a little higher.
     */
    public static void applyFill(Vector3f colors, int blockLight, boolean fallback) {
        float t = Mth.clamp((blockLight - DARK_BELOW) / (FULL_FROM - DARK_BELOW), 0.0f, 1.0f);
        float s = t * t * (3.0f - 2.0f * t);
        float fill = (fallback ? FALLBACK_FILL : MAX_FILL) * s * (1.0f + LIGHT_BOOST * light);
        // Warm where it's bright, the glowing plants' cool green where it's dim; golden in the light.
        float warm = Mth.clamp((t - 0.25f) / 0.5f, 0.0f, 1.0f);
        float r = Mth.lerp(light, Mth.lerp(warm, GLOW_R, WARM_R), GOLD_R);
        float g = Mth.lerp(light, Mth.lerp(warm, GLOW_G, WARM_G), GOLD_G);
        float b = Mth.lerp(light, Mth.lerp(warm, GLOW_B, WARM_B), GOLD_B);
        colors.set(UNLIT_R + fill * r, UNLIT_G + fill * g, UNLIT_B + fill * b);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !inLevel369()) {
            light = 0.0f;
            lastTarget = 0.0f;
            return;
        }
        BlockPos eyes = BlockPos.containing(mc.player.getEyePosition());
        float target = mc.level.getBrightness(LightLayer.BLOCK, eyes) / 15.0f;
        brightness += (target - brightness) * 0.05f;
        if (Level369ClientState.hasSeed()) {
            float wave = Level369Light.strength(Level369ClientState.seed(), mc.player.getX(), mc.player.getZ(),
                    mc.level.getGameTime());
            if (lastTarget < ARRIVE_AT && wave >= ARRIVE_AT) {
                mc.player.playSound(ModSounds.LEVEL37_ARRIVE.get(), 0.35f, 0.9f); // warmth swells in
            }
            lastTarget = wave;
            light += (wave - light) * 0.04f;
        }
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE || !inLevel369()) {
            return;
        }
        float b = brightness * brightness;
        event.setNearPlaneDistance(Mth.lerp(b, DARK_FOG_START, BRIGHT_FOG_START));
        event.setFarPlaneDistance(Mth.lerp(b, DARK_FOG_END, BRIGHT_FOG_END));
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true); // required for NeoForge to use our distances
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inLevel369() || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float t = (float) Math.pow(brightness, 1.2);
        event.setRed(Mth.lerp(light, Mth.lerp(t, GLOOM_R, HAZE_R), GOLDEN_HAZE_R));
        event.setGreen(Mth.lerp(light, Mth.lerp(t, GLOOM_G, HAZE_G), GOLDEN_HAZE_G));
        event.setBlue(Mth.lerp(light, Mth.lerp(t, GLOOM_B, HAZE_B), GOLDEN_HAZE_B));
    }
}
