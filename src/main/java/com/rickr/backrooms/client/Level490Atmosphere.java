package com.rickr.backrooms.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.light.BackroomsLights;
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
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * How Level 490 ("Penance") looks: the dimension effects for {@code backrooms:penance}.
 * <ul>
 *   <li><b>Sky:</b> the storm, the moon and the sea of cloud below ({@link Level490Sky}).</li>
 *   <li><b>Light:</b> a cold, dim moonlight on everything, so the night stays readable (the
 *       reference stills are dark but clear); the lanterns that still burn throw small warm pools;
 *       lightning lights the whole level for an instant.</li>
 *   <li><b>Fog:</b> a thin blue-grey haze: the next platforms are clear, the far ones fade into
 *       it, and so do the chains rising into the storm.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level490Atmosphere extends DimensionSpecialEffects {
    /** Moonlight with no lantern near. */
    private static final float MOON_R = 0.150f;
    private static final float MOON_G = 0.163f;
    private static final float MOON_B = 0.195f;
    /** A burning lantern: warm. Smaller with Veil, which draws the lantern's real light. */
    private static final float LAMP_R = 1.00f;
    private static final float LAMP_G = 0.74f;
    private static final float LAMP_B = 0.45f;
    private static final float VEIL_LAMP_FILL = 0.30f;
    private static final float FALLBACK_LAMP_FILL = 0.75f;
    private static final float LAMP_FALLOFF = 1.7f;
    /** A lightning flash adds this much cold light to everything. */
    private static final float FLASH_R = 0.42f;
    private static final float FLASH_G = 0.45f;
    private static final float FLASH_B = 0.52f;
    private static final float EMISSIVE = 0.95f;

    private static final float FOG_START = 14.0f;
    private static final float FOG_END = 260.0f;
    static final float FOG_R = 0.075f;
    static final float FOG_G = 0.083f;
    static final float FOG_B = 0.105f;

    public Level490Atmosphere() {
        // No vanilla clouds (we draw our own), no ground line, our own sky.
        super(Float.NaN, false, SkyType.NORMAL, false, false);
    }

    /** Mod bus: Level 490's dimension type uses {@code "effects": "backrooms:penance"}. */
    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Backrooms.id("penance"), new Level490Atmosphere());
    }

    /** True when the client is in Level 490. */
    public static boolean inLevel490() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_490;
    }

    /** 0..1: the lightning flash right now (the same two pops as the camcorder's). */
    static float flash() {
        return CamcorderEffect.lightningFlash(System.currentTimeMillis());
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
        Level490Sky.render(modelViewMatrix, camera);
        setupFog.run(); // put the sky fog back the way vanilla leaves it
        return true;
    }

    /** The lightmap: no sky light here, so moonlight everywhere plus the lanterns' block light. */
    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker,
                                     float skyLight, int pixelX, int pixelY, Vector3f colors) {
        if (pixelX == 15 && pixelY == 15) {
            colors.set(EMISSIVE, EMISSIVE, EMISSIVE);
        } else {
            boolean veil = BackroomsLights.isVeilLightingActive();
            float lamp = (veil ? VEIL_LAMP_FILL : FALLBACK_LAMP_FILL) * (float) Math.pow(pixelX / 15.0f, LAMP_FALLOFF);
            float flash = flash();
            colors.set(Math.min(1.0f, MOON_R + lamp * LAMP_R + flash * FLASH_R),
                    Math.min(1.0f, MOON_G + lamp * LAMP_G + flash * FLASH_G),
                    Math.min(1.0f, MOON_B + lamp * LAMP_B + flash * FLASH_B));
        }
        ClientLighting.applyCamcorder(colors);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || !inLevel490()) {
            return;
        }
        event.setNearPlaneDistance(FOG_START);
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), FOG_END));
        // A sphere, so the chains climbing far overhead fade into it like the far platforms.
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true); // required for NeoForge to use our distances
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inLevel490() || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float[] c = fogColour(new float[3]);
        event.setRed(c[0]);
        event.setGreen(c[1]);
        event.setBlue(c[2]);
    }

    /** The fog colour right now (brighter in a lightning flash), written into {@code out}. */
    static float[] fogColour(float[] out) {
        float f = flash() * 0.6f;
        out[0] = FOG_R + f * 0.30f;
        out[1] = FOG_G + f * 0.32f;
        out[2] = FOG_B + f * 0.36f;
        return out;
    }
}
