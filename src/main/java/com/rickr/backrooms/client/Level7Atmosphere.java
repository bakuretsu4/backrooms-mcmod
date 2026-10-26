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
import net.minecraft.util.Mth;
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
 * How Level 7 ("Thalassophobia") looks: the dimension effects for {@code backrooms:sea}, plus its fog.
 * <ul>
 *   <li><b>No sky:</b> a concrete ceiling over everything, so nothing is drawn above it.</li>
 *   <li><b>Light:</b> a dim, cold, grey light that comes from nowhere in particular over the water,
 *       fading toward black the deeper the camera goes (so in the deep your flashlight is all you
 *       have). The entrance room's tube adds its own light.</li>
 *   <li><b>Fog:</b> above the water the thick fog of "bone dust" the wikis describe, pale grey and
 *       close. Under the water a murky dark teal that gets darker and closer with depth. The
 *       picture grade ({@link AtmosphereGrade}) adds the matching haze on top.</li>
 * </ul>
 * The rest (depth, the Thing, the lurch when you drop through the hatch) is {@link Level7Client}.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level7Atmosphere extends DimensionSpecialEffects {
    /** Light over the water and in the shallows, with no lamp near. */
    private static final float AIR_R = 0.28f;
    private static final float AIR_G = 0.30f;
    private static final float AIR_B = 0.31f;
    /** The same at the bottom of the sea: almost nothing. */
    private static final float DEEP_R = 0.018f;
    private static final float DEEP_G = 0.024f;
    private static final float DEEP_B = 0.028f;
    /** The entrance room's tube: cold white. Smaller with Veil, which draws the tube's real light. */
    private static final float LAMP_R = 0.95f;
    private static final float LAMP_G = 0.97f;
    private static final float LAMP_B = 1.00f;
    private static final float VEIL_LAMP_FILL = 0.30f;
    private static final float FALLBACK_LAMP_FILL = 0.90f;
    private static final float LAMP_FALLOFF = 1.8f;
    private static final float EMISSIVE = 0.95f;

    /** The bone-dust fog above the water (blocks). */
    private static final float AIR_FOG_START = 1.0f;
    private static final float AIR_FOG_END = 46.0f;
    private static final float AIR_FOG_R = 0.30f;
    private static final float AIR_FOG_G = 0.31f;
    private static final float AIR_FOG_B = 0.305f;
    /** Under water: how far you see just below the surface, and at the bottom. */
    private static final float WATER_FOG_END_SHALLOW = 48.0f;
    private static final float WATER_FOG_END_DEEP = 26.0f;
    private static final float WATER_R = 0.055f;
    private static final float WATER_G = 0.110f;
    private static final float WATER_B = 0.120f;
    private static final float ABYSS_R = 0.004f;
    private static final float ABYSS_G = 0.007f;
    private static final float ABYSS_B = 0.009f;

    public Level7Atmosphere() {
        // No clouds, no ground (no horizon line drawn), no sky.
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    /** Mod bus: Level 7's dimension type uses {@code "effects": "backrooms:sea"}. */
    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Backrooms.id("sea"), new Level7Atmosphere());
    }

    /** True when the client is in Level 7. */
    public static boolean inLevel7() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_7;
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
        return true; // only the ceiling up there
    }

    /**
     * The lightmap. Level 7 has no sky light (pixelY is 0) except on full-bright faces, so this is
     * a dim ambient that darkens with the camera's depth, plus the lamp's block light.
     */
    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker,
                                     float skyLight, int pixelX, int pixelY, Vector3f colors) {
        if (pixelX == 15 && pixelY == 15) {
            colors.set(EMISSIVE, EMISSIVE, EMISSIVE);
        } else {
            float deep = Level7Client.deepness();
            boolean veil = BackroomsLights.isVeilLightingActive();
            float lamp = (veil ? VEIL_LAMP_FILL : FALLBACK_LAMP_FILL) * (float) Math.pow(pixelX / 15.0f, LAMP_FALLOFF);
            colors.set(Math.min(1.0f, Mth.lerp(deep, AIR_R, DEEP_R) + lamp * LAMP_R),
                    Math.min(1.0f, Mth.lerp(deep, AIR_G, DEEP_G) + lamp * LAMP_G),
                    Math.min(1.0f, Mth.lerp(deep, AIR_B, DEEP_B) + lamp * LAMP_B));
        }
        ClientLighting.applyCamcorder(colors);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || !inLevel7()) {
            return;
        }
        float view = event.getFarPlaneDistance();
        if (event.getType() == FogType.NONE) {
            event.setNearPlaneDistance(AIR_FOG_START);
            event.setFarPlaneDistance(Math.min(view, AIR_FOG_END));
            event.setFogShape(FogShape.CYLINDER);
        } else if (event.getType() == FogType.WATER) {
            event.setNearPlaneDistance(-4.0f);
            event.setFarPlaneDistance(Mth.lerp(Level7Client.deepness(), WATER_FOG_END_SHALLOW, WATER_FOG_END_DEEP));
            event.setFogShape(FogShape.SPHERE);
        } else {
            return;
        }
        event.setCanceled(true); // required for NeoForge to use our distances
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inLevel7()) {
            return;
        }
        FogType type = event.getCamera().getFluidInCamera();
        if (type == FogType.NONE) {
            event.setRed(AIR_FOG_R);
            event.setGreen(AIR_FOG_G);
            event.setBlue(AIR_FOG_B);
        } else if (type == FogType.WATER) {
            float deep = Level7Client.deepness();
            event.setRed(Mth.lerp(deep, WATER_R, ABYSS_R));
            event.setGreen(Mth.lerp(deep, WATER_G, ABYSS_G));
            event.setBlue(Mth.lerp(deep, WATER_B, ABYSS_B));
        }
    }
}
