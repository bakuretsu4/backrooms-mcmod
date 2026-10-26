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
 * Eyes under the water in the Poolrooms / Level 37: a clear, tinted view instead of vanilla's
 * murky blue. The fog colour and distance come from the level's {@link WaterStyle}; vanilla's
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
    /** Fog brightness in a pitch-dark spot vs a fully lit one (the water isn't a lamp). */
    private static final float DARK_FOG = 0.18f;

    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    /** Eased 0..1 "eyes under water", for the camcorder. */
    private static float amount;

    /** The water style where the camera is (regardless of whether our renderer runs), or null. */
    @Nullable
    private static WaterStyle levelStyle() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : WaterStyle.at(BackroomsLevel.fromDimension(mc.level.dimension()),
                mc.gameRenderer.getMainCamera().getPosition().x);
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
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        WaterStyle style = levelStyle();
        if (event.getType() != FogType.WATER || style == null) {
            return;
        }
        event.setNearPlaneDistance(FOG_NEAR);
        event.setFarPlaneDistance(style.fogDistance());
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true); // required for NeoForge to use our distances
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        WaterStyle style = levelStyle();
        if (style == null || event.getCamera().getFluidInCamera() != FogType.WATER) {
            return;
        }
        // The fog is light scattered by the water: dark in a dark room, full colour where it's lit.
        float light = lightAt(event.getCamera().getPosition());
        float k = Mth.lerp(light, DARK_FOG, 1.0f);
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
        for (int i = 0; i < 32; i++) {
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
        return eye.y + 32.0;
    }

    private UnderwaterView() {}
}
