package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.anomaly.level2.Level2Spin;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The air of Level 2: a close, near-black fog that swallows the corridor about 18 blocks ahead, so
 * you only ever see a short length of it and never what is coming. Only at the exit recess and its
 * approach (where Level2Spin eases off) does it open up a little, so the way out can be seen.
 *
 * <p>The camcorder's haze (AtmosphereGrade LEVEL_2, from the depth buffer) darkens the distance
 * too; this vanilla fog is what makes the far end disappear for good. A shader pack (Iris) may
 * draw its own fog instead, and then the camcorder haze alone does it.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level2Atmosphere {
    /** The first few blocks round you stay clear: you can always see the wall you're touching. */
    private static final float FOG_START = 4.0f;
    /** Where the corridor is fully swallowed: everywhere in the corridor... */
    private static final float FOG_END = 18.0f;
    /** ...and at the exit and its approach (Level2Spin at its EXIT_CALM). */
    private static final float FOG_END_EXIT = 26.0f;
    /** #07080B: almost black, a hint of the cold blue-grey of the level's dark. */
    private static final float FOG_R = 7 / 255.0f;
    private static final float FOG_G = 8 / 255.0f;
    private static final float FOG_B = 11 / 255.0f;

    /** True when the client is in Level 2. */
    private static boolean inLevel2() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_2;
    }

    /** Where the fog swallows everything right now (blocks): Level 2's lamps fade out by then (VeilFixtures). */
    public static float fogEnd() {
        float corridor = Mth.clamp((Level2Spin.strength() - Level2Spin.EXIT_CALM) / (1.0f - Level2Spin.EXIT_CALM), 0.0f, 1.0f);
        return Mth.lerp(corridor, FOG_END_EXIT, FOG_END);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        // Vanilla keeps water / lava / powder snow fog, the sky pass and blindness/darkness.
        if (event.getType() != FogType.NONE || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN
                || !inLevel2() || blinded(event.getCamera())) {
            return;
        }
        float end = fogEnd();
        event.setNearPlaneDistance(FOG_START);
        event.setFarPlaneDistance(Math.min(end, event.getFarPlaneDistance()));
        event.setCanceled(true); // required for NeoForge to use our distances
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inLevel2() || event.getCamera().getFluidInCamera() != FogType.NONE || blinded(event.getCamera())) {
            return;
        }
        event.setRed(FOG_R);
        event.setGreen(FOG_G);
        event.setBlue(FOG_B);
    }

    private static boolean blinded(Camera camera) {
        return camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    private Level2Atmosphere() {}
}
