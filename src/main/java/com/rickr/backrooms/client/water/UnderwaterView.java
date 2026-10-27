package com.rickr.backrooms.client.water;

import com.mojang.blaze3d.shaders.FogShape;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.Flooding;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Eyes under the water in the Poolrooms, Level 37 and Level 7: a tinted view instead of vanilla's
 * murky blue. The fog colour and distance come from the level's {@link WaterStyle} (in Level 7's
 * deep sea also from how far under the surface you are); vanilla's
 * dark "underwater" screen overlay is switched off; the camcorder gets an {@code Underwater}
 * value (0..1) for a gentle wobble of the picture.
 *
 * <p>Only the camera-in-water case is touched here. The normal (air) fog of a level belongs to
 * its own atmosphere class, which must ignore {@code FogType.WATER} the same way this class
 * ignores everything else.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class UnderwaterView {
    /** Fog already starts a little in front of your eyes (vanilla uses -8). */
    private static final float FOG_NEAR = -1.0f;
    /** How far up {@link #surfaceAbove} climbs looking for the surface (Level 7's sea is 150 deep). */
    private static final int MAX_CLIMB = 256;
    /** However deep and dark, the fog keeps this much of its colour (black would hide the flashlight's edge). */
    private static final float MIN_FOG = 0.04f;

    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    /** Eased 0..1 "eyes under water", for the camcorder. */
    private static float amount;
    /** 0..1 how far toward the style's {@code darkDepth} the camera is (0 in the pools). */
    private static float dark;

    /** The water style where the camera is (regardless of whether our renderer runs), or null. */
    @Nullable
    private static WaterStyle levelStyle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        var eye = mc.gameRenderer.getMainCamera().getPosition();
        return WaterStyle.at(BackroomsLevel.fromDimension(mc.level.dimension()), eye.x, eye.y, eye.z);
    }

    /** True while the camera is inside water in a level whose water we draw. */
    static boolean cameraInWater() {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        return WaterRenderer.activeStyle() != null && camera.getFluidInCamera() == FogType.WATER;
    }

    /** 0..1 for the camcorder's {@code Underwater} uniform (0 = picture exactly unchanged). */
    public static float shaderUnderwater() {
        return amount;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        float target = cameraInWater() ? 1.0f : 0.0f;
        // Quick in (you dunk your head), a little slower out (water running off the lens).
        amount += (target - amount) * (target > amount ? 0.5f : 0.25f);
        if (amount < 0.002f) {
            amount = 0.0f;
        }
        dark = darkness();
    }

    /** Once a tick: how far down toward the black the camera is (measured from the surface above it). */
    private static float darkness() {
        Minecraft mc = Minecraft.getInstance();
        WaterStyle style = levelStyle();
        Camera camera = mc.gameRenderer.getMainCamera();
        if (style == null || style.darkDepth() <= 0.0f || camera.getFluidInCamera() != FogType.WATER) {
            return 0.0f;
        }
        Vec3 eye = camera.getPosition();
        double depth = surfaceAbove(mc.level, eye) - eye.y;
        return (float) Mth.clamp(depth / style.darkDepth(), 0.0, 1.0);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        WaterStyle style = levelStyle();
        if (event.getType() != FogType.WATER || style == null) {
            return;
        }
        event.setNearPlaneDistance(FOG_NEAR);
        event.setFarPlaneDistance(Mth.lerp(dark, style.fogDistance(), style.darkFogDistance()));
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true); // required for NeoForge to use our distances
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        WaterStyle style = levelStyle();
        if (style == null || event.getCamera().getFluidInCamera() != FogType.WATER) {
            return;
        }
        // The fog is light scattered by the water: dark in a dark room, full colour where it's lit,
        // and (in a deep sea) fading to black the further down you are.
        float light = lightAt(event.getCamera().getPosition());
        float k = Math.max(Mth.lerp(light, style.darkFog(), 1.0f) * (1.0f - dark), MIN_FOG);
        event.setRed(WaterStyle.red(style.fogColor()) * k);
        event.setGreen(WaterStyle.green(style.fogColor()) * k);
        event.setBlue(WaterStyle.blue(style.fogColor()) * k);
    }

    /** No dark blue vanilla water texture over the screen in the pool levels. */
    @SubscribeEvent
    public static void onBlockOverlay(RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.WATER && levelStyle() != null) {
            event.setCanceled(true);
        }
    }

    /** 0..1 brightest of block / sky light at a position. */
    private static float lightAt(Vec3 pos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return 1.0f;
        }
        CURSOR.set(pos.x, pos.y, pos.z);
        int light = Math.max(level.getBrightness(LightLayer.BLOCK, CURSOR), level.getBrightness(LightLayer.SKY, CURSOR));
        return light / 15.0f;
    }

    /**
     * World Y of the water surface above (or at) {@code eye}: climbs up through the water column
     * to the top water block (its fluid height) or to a shallow-water cell resting on it.
     */
    static double surfaceAbove(ClientLevel level, Vec3 eye) {
        CURSOR.set(eye.x, eye.y, eye.z);
        for (int i = 0; i < MAX_CLIMB; i++) {
            BlockState state = level.getBlockState(CURSOR);
            if (state.is(ModBlocks.SHALLOW_WATER.get()) || Flooding.isFlooded(state)) {
                return CURSOR.getY() + WaterSurfaces.SHALLOW_SURFACE;
            }
            FluidState fluid = state.getFluidState();
            if (!fluid.getType().isSame(Fluids.WATER)) {
                return CURSOR.getY(); // left the water: its bottom is the surface
            }
            BlockState above = level.getBlockState(CURSOR.move(0, 1, 0));
            if (!above.getFluidState().getType().isSame(Fluids.WATER) && !above.is(ModBlocks.SHALLOW_WATER.get())
                    && !Flooding.isFlooded(above)) {
                return CURSOR.getY() - 1 + fluid.getOwnHeight();
            }
        }
        return eye.y + MAX_CLIMB;
    }

    private UnderwaterView() {}
}
