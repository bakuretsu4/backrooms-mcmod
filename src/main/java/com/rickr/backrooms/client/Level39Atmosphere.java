package com.rickr.backrooms.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.layout.Level39AnomalyPlan;
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
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * How Level 39 ("Megalophobia") looks: the dimension effects for {@code backrooms:megalophobia}
 * (its dimension type points here), plus its fog and light.
 *
 * <ul>
 *   <li><b>Sky:</b> no vanilla sky, sun, moon or clouds. Instead {@link Level39Sky}: a dull grey
 *       vault, the superstructure's own cloud ceiling at y 250-290 lit from below, and the four
 *       colossal facades of the town drawn far beyond the loaded chunks ({@link Level39FarFacade}).</li>
 *   <li><b>Fog</b> (the 1940s photo): dense, cold and luminous. In mid-town it is a mid grey (the
 *       mill's light scattered through the mist) and you see about a hundred blocks; houses and
 *       poles stand against it as soft silhouettes. Toward the superstructure it turns pale silver
 *       white, so the light seems to hang in the air the way it bleeds into the mist in the photo.
 *       During the indoor storm it closes in a little. In the tunnels the air is clear and pale
 *       turquoise; in stagnant (Hydrolitis) segments it is dark and greenish. Up in the cloud
 *       ceiling (arriving by gondola) it is a grey whiteout.</li>
 *   <li><b>Light</b> ({@link #applyFill}, through {@code ClientLighting}): a moody but clearly
 *       readable cool night in mid-town, brighter near the facades (their windows light the
 *       street, without washing it out), dimmer and warmer inside the houses (a roof over your
 *       head), and bright and even in the tunnels where the invisible orbs shine (dark only in
 *       stagnant segments, whose orbs are dead).</li>
 * </ul>
 * The values that depend on where you stand (how near the facade, tunnel or town) are eased every
 * tick, so walking toward the wall or dropping down a hole never makes the picture jump.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level39Atmosphere extends DimensionSpecialEffects {
    // --- Fog colours (0..1). Taken from the photo's greys: the fog is a cold neutral grey. ---------
    /**
     * Mid-town at night: a luminous mid grey (#3D4045), the mill's light scattered through the
     * whole mist. Dark things (houses, poles, fences) stand out against it as soft silhouettes,
     * as in the photo, instead of everything sinking into black.
     */
    private static final float TOWN_FOG_R = 0.24f;
    private static final float TOWN_FOG_G = 0.25f;
    private static final float TOWN_FOG_B = 0.27f;
    /** Right at the superstructure: the fog lit by the windows (#AEB3BA), a pale silvery white. */
    private static final float GLOW_FOG_R = 0.68f;
    private static final float GLOW_FOG_G = 0.70f;
    private static final float GLOW_FOG_B = 0.73f;
    /** Tunnels: pale turquoise air over the water, lit by the orbs (not white: the vault must not wash out). */
    private static final float TUNNEL_FOG_R = 0.48f;
    private static final float TUNNEL_FOG_G = 0.62f;
    private static final float TUNNEL_FOG_B = 0.64f;
    /** Stagnant tunnel segments: orbs dead, a dark green film on everything. */
    private static final float STAGNANT_FOG_R = 0.06f;
    private static final float STAGNANT_FOG_G = 0.10f;
    private static final float STAGNANT_FOG_B = 0.085f;
    /** Inside the cloud ceiling: dull grey whiteout. */
    private static final float CLOUD_FOG_R = 0.30f;
    private static final float CLOUD_FOG_G = 0.31f;
    private static final float CLOUD_FOG_B = 0.33f;
    /** The storm darkens the town fog by this much. */
    private static final float RAIN_FOG_DARKEN = 0.18f;

    // --- Fog distances (blocks) --------------------------------------------------------------------
    /** Mid-town: the next few houses clear, the street fading out by ~120 blocks. */
    private static final float TOWN_FOG_START = 3.0f;
    private static final float TOWN_FOG_END = 120.0f;
    /**
     * At the facade the fog is a little thicker (it glows, but it hides more). Not too thick: the
     * bright fog right at the wall must not wash out the brick and muntins in front of you.
     */
    private static final float FACADE_FOG_START = 4.0f;
    private static final float FACADE_FOG_END = 96.0f;
    /** During the indoor storm the fog end comes this much closer (fraction). */
    private static final float RAIN_FOG_CLOSER = 0.25f;
    private static final float TUNNEL_FOG_START = 8.0f;
    private static final float TUNNEL_FOG_END = 90.0f;
    private static final float STAGNANT_FOG_START = 1.5f;
    private static final float STAGNANT_FOG_END = 34.0f;
    private static final float CLOUD_FOG_START = 0.0f;
    private static final float CLOUD_FOG_END = 9.0f;
    /** Facade proximity is measured over this many blocks: further out counts as mid-town. */
    public static final float PROXIMITY_RANGE = 140.0f;

    // --- Light (lightmap colours, 0..1) -------------------------------------------------------------
    /**
     * "No light at all" in mid-town: a dim but clearly readable cool night (brighter than Level 9's
     * streets). The whole town is lit softly by the mill's glow in the fog, so even far from any
     * lamp you see the clapboard, the fences and the ground.
     */
    private static final float TOWN_UNLIT_R = 0.17f;
    private static final float TOWN_UNLIT_G = 0.18f;
    private static final float TOWN_UNLIT_B = 0.21f;
    /** ... and right under the facade: everything washed in cool window light (but not white). */
    private static final float FACADE_UNLIT_R = 0.30f;
    private static final float FACADE_UNLIT_G = 0.315f;
    private static final float FACADE_UNLIT_B = 0.345f;
    /**
     * Inside a house (a roof over your head), and everywhere in the bigger-inside interiors: the
     * mill light only comes in through the windows, so it is dimmer, warmer and less blue, but
     * wallpaper, furniture and the bulb-studded ceilings stay readable.
     */
    private static final float INDOOR_UNLIT_R = 0.20f;
    private static final float INDOOR_UNLIT_G = 0.18f;
    private static final float INDOOR_UNLIT_B = 0.16f;
    /** Town block-light fill (lit house windows): soft. */
    private static final float TOWN_MAX_FILL = 0.32f;
    private static final float TOWN_FILL_FALLOFF = 2.0f;
    /** Indoors the few lit bulbs (light 8) give a wider, warmer pool: a softer curve. */
    private static final float INDOOR_MAX_FILL = 0.45f;
    private static final float INDOOR_FILL_FALLOFF = 1.4f;
    /**
     * Tunnels: lit evenly everywhere by the orbs every ten blocks (canon: lit by invisible orbs),
     * so even "unlit" marble is bright; the orbs' own block light adds a little more on top. Kept
     * under clipping: white marble must keep its blue-grey veins and the water its turquoise.
     */
    private static final float TUNNEL_UNLIT_R = 0.40f;
    private static final float TUNNEL_UNLIT_G = 0.43f;
    private static final float TUNNEL_UNLIT_B = 0.445f;
    private static final float TUNNEL_MAX_FILL = 0.24f;
    private static final float TUNNEL_FILL_FALLOFF = 1.0f;
    /** A stagnant segment's orbs are dead: there it is really dark. */
    private static final float STAGNANT_UNLIT_R = 0.025f;
    private static final float STAGNANT_UNLIT_G = 0.035f;
    private static final float STAGNANT_UNLIT_B = 0.03f;
    /** Fill tints: cool white in town, warm bulbs indoors, turquoise-white in the tunnels. */
    private static final float TOWN_TINT_R = 0.84f;
    private static final float TOWN_TINT_G = 0.90f;
    private static final float TOWN_TINT_B = 1.0f;
    private static final float INDOOR_TINT_R = 1.0f;
    private static final float INDOOR_TINT_G = 0.84f;
    private static final float INDOOR_TINT_B = 0.62f;
    private static final float TUNNEL_TINT_R = 0.86f;
    private static final float TUNNEL_TINT_G = 0.98f;
    private static final float TUNNEL_TINT_B = 1.0f;
    /** Without Veil the facade glow is added on top of the vanilla curve, at most this much. */
    private static final float FALLBACK_FACADE_LIFT = 0.12f;
    /** A roof is looked for this far above your head (blocks). */
    private static final int ROOF_SEARCH = 14;

    /** How fast the zone values ease toward the new place (share per tick). */
    private static final float EASE = 0.06f;

    // Eased every client tick; read by the fog, the lightmap and the sky.
    private static float proximity;
    private static float tunnel;
    private static float stagnant;
    private static float cloud;
    private static float indoors;

    public Level39Atmosphere() {
        // No clouds (NaN cloud height: we draw our own ceiling), has ground, our own sky.
        super(Float.NaN, true, SkyType.NORMAL, false, false);
    }

    /** Mod bus: Level 39's dimension type uses {@code "effects": "backrooms:megalophobia"}. */
    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Backrooms.id("megalophobia"), new Level39Atmosphere());
    }

    /** 0..1: how near the superstructure the camera is (eased). */
    public static float proximity() {
        return proximity;
    }

    /** 0..1: how much the camera is in the tunnels (eased). */
    public static float tunnel() {
        return tunnel;
    }

    /** 0..1: how much the camera is in a stagnant tunnel segment (eased). */
    public static float stagnant() {
        return stagnant;
    }

    /** 0..1: how much the camera is indoors (a roof over its head, in town; eased). */
    public static float indoors() {
        return indoors;
    }

    /** 0..1: how deep in the cloud ceiling the camera is (eased). */
    public static float cloud() {
        return cloud;
    }

    // ==========================================================================================
    // Dimension effects
    // ==========================================================================================

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor; // the fog colour comes from onFogColor below
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
        Level39Sky.render(modelViewMatrix, camera, partialTick);
        setupFog.run(); // put the sky fog back the way vanilla leaves it
        return true;
    }

    /**
     * The lightmap. Sky light never reaches anything here (no skylight), so a sky level of 15 only
     * comes from full-bright faces; {@code ClientLighting} handles both modes (Veil / fallback)
     * and calls {@link #applyFill} for the Veil fill.
     */
    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker,
                                     float skyLight, int pixelX, int pixelY, Vector3f colors) {
        ClientLighting.apply(colors, pixelX, pixelY == 15, BackroomsLevel.LEVEL_39);
    }

    /**
     * Veil mode lightmap fill for Level 39 (see the class comment). Town: a readable cool night
     * that brightens toward the facades, dimmer and warmer indoors. Tunnels: bright and even
     * everywhere (the orbs), except in stagnant segments, whose orbs are dead.
     */
    static void applyFill(Vector3f colors, int blockLight) {
        float b = Mth.clamp(blockLight / 15.0f, 0.0f, 1.0f);
        float p = proximity;
        float in = indoors;
        float townFill = Mth.lerp(in, TOWN_MAX_FILL * (float) Math.pow(b, TOWN_FILL_FALLOFF),
                INDOOR_MAX_FILL * (float) Math.pow(b, INDOOR_FILL_FALLOFF));
        float tr = Mth.lerp(in, Mth.lerp(p, TOWN_UNLIT_R, FACADE_UNLIT_R), INDOOR_UNLIT_R)
                + townFill * Mth.lerp(in, TOWN_TINT_R, INDOOR_TINT_R);
        float tg = Mth.lerp(in, Mth.lerp(p, TOWN_UNLIT_G, FACADE_UNLIT_G), INDOOR_UNLIT_G)
                + townFill * Mth.lerp(in, TOWN_TINT_G, INDOOR_TINT_G);
        float tb = Mth.lerp(in, Mth.lerp(p, TOWN_UNLIT_B, FACADE_UNLIT_B), INDOOR_UNLIT_B)
                + townFill * Mth.lerp(in, TOWN_TINT_B, INDOOR_TINT_B);
        float tunnelFill = TUNNEL_MAX_FILL * (float) Math.pow(b, TUNNEL_FILL_FALLOFF);
        float s = stagnant;
        float ur = Mth.lerp(s, TUNNEL_UNLIT_R, STAGNANT_UNLIT_R) + tunnelFill * TUNNEL_TINT_R;
        float ug = Mth.lerp(s, TUNNEL_UNLIT_G, STAGNANT_UNLIT_G) + tunnelFill * TUNNEL_TINT_G;
        float ub = Mth.lerp(s, TUNNEL_UNLIT_B, STAGNANT_UNLIT_B) + tunnelFill * TUNNEL_TINT_B;
        float t = tunnel;
        colors.set(Math.min(1.0f, Mth.lerp(t, tr, ur)), Math.min(1.0f, Mth.lerp(t, tg, ug)), Math.min(1.0f, Mth.lerp(t, tb, ub)));
    }

    /**
     * Fallback (no Veil): the vanilla curve already lit things near lamps; never let anything sink
     * below the same readable floor as in Veil mode, and add the cool facade glow on top.
     */
    static void adjustFallback(Vector3f colors) {
        Vector3f floor = FALLBACK_FLOOR;
        applyFill(floor, 0);
        float lift = FALLBACK_FACADE_LIFT * proximity * (1.0f - tunnel) * (1.0f - indoors);
        colors.set(Math.min(1.0f, Math.max(colors.x, floor.x) + lift * TOWN_TINT_R),
                Math.min(1.0f, Math.max(colors.y, floor.y) + lift * TOWN_TINT_G),
                Math.min(1.0f, Math.max(colors.z, floor.z) + lift * TOWN_TINT_B));
    }

    /** Scratch for {@link #adjustFallback} (the lightmap is built on the render thread only). */
    private static final Vector3f FALLBACK_FLOOR = new Vector3f();

    // ==========================================================================================
    // Zone tracking
    // ==========================================================================================

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Level39ClientState.inLevel39()) {
            return;
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        boolean inTunnels = Level39ClientState.inTunnels(eye);
        proximity += (Level39ClientState.facadeProximity(eye, PROXIMITY_RANGE) - proximity) * EASE;
        tunnel += ((inTunnels ? 1.0f : 0.0f) - tunnel) * EASE * 2.0f;
        stagnant += ((Level39ClientState.inStagnantWater(eye) ? 1.0f : 0.0f) - stagnant) * EASE;
        float cloudTarget = (float) Mth.clamp(Math.min(eye.y - Level39ClientState.CLOUD_BOTTOM,
                Level39ClientState.CLOUD_TOP - eye.y) / 6.0, 0.0, 1.0);
        cloud += (cloudTarget - cloud) * EASE * 3.0f;
        if (mc.level.getGameTime() % 5 == 0) {
            // The bigger-inside interiors (the far-east copy region) are all indoors, whatever
            // stands over your head there.
            roofOverhead = !inTunnels && (Level39AnomalyPlan.isCopy(Mth.floor(eye.x)) || hasRoof(mc, eye));
        }
        indoors += ((roofOverhead ? 1.0f : 0.0f) - indoors) * EASE * 2.0f;
    }

    /** Last result of the roof check (made every 5 ticks). */
    private static boolean roofOverhead;

    /**
     * True when a solid block hangs over the camera within {@link #ROOF_SEARCH} blocks: you are
     * inside a house (leaves and the thin wires don't count: they don't render solid).
     */
    private static boolean hasRoof(Minecraft mc, Vec3 eye) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int x = Mth.floor(eye.x);
        int z = Mth.floor(eye.z);
        int y0 = Mth.floor(eye.y) + 1;
        for (int y = y0; y < y0 + ROOF_SEARCH; y++) {
            cursor.set(x, y, z);
            BlockState state = mc.level.getBlockState(cursor);
            if (!state.isAir() && state.isSolidRender(mc.level, cursor)) {
                return true;
            }
        }
        return false;
    }

    // ==========================================================================================
    // Fog
    // ==========================================================================================

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        // Leave the vanilla fog alone under water / in lava / powder snow, and for the sky pass
        // (Level39Sky fades its own layers into the fog colour).
        if (event.getType() != FogType.NONE || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN
                || !Level39ClientState.inLevel39()) {
            return;
        }
        float rain = Level39Weather.rain();
        float townStart = Mth.lerp(proximity, TOWN_FOG_START, FACADE_FOG_START);
        float townEnd = Mth.lerp(proximity, TOWN_FOG_END, FACADE_FOG_END) * (1.0f - RAIN_FOG_CLOSER * rain);
        float tunnelStart = Mth.lerp(stagnant, TUNNEL_FOG_START, STAGNANT_FOG_START);
        float tunnelEnd = Mth.lerp(stagnant, TUNNEL_FOG_END, STAGNANT_FOG_END);
        float start = Mth.lerp(tunnel, townStart, tunnelStart);
        float end = Mth.lerp(tunnel, townEnd, tunnelEnd);
        start = Mth.lerp(cloud, start, CLOUD_FOG_START);
        end = Mth.lerp(cloud, end, CLOUD_FOG_END);
        end = Math.min(end, event.getFarPlaneDistance());
        event.setNearPlaneDistance(start);
        event.setFarPlaneDistance(end);
        // A sphere, so the facade climbing far overhead fogs the same as the street.
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true); // required for NeoForge to use our distances
    }

    /** The terrain fog's far distance right now (the far facade fades in where the real one fades out). */
    public static float fogEnd() {
        float townEnd = Mth.lerp(proximity, TOWN_FOG_END, FACADE_FOG_END) * (1.0f - RAIN_FOG_CLOSER * Level39Weather.rain());
        return Mth.lerp(tunnel, townEnd, Mth.lerp(stagnant, TUNNEL_FOG_END, STAGNANT_FOG_END));
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!Level39ClientState.inLevel39() || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float[] c = fogColour(new float[3]);
        event.setRed(c[0]);
        event.setGreen(c[1]);
        event.setBlue(c[2]);
    }

    /** The fog colour right now, written into {@code out} (the sky fades into it too). */
    public static float[] fogColour(float[] out) {
        // A curve on the proximity: the glow only really takes over in the last stretch to the wall.
        float glow = proximity * proximity;
        float dark = 1.0f - RAIN_FOG_DARKEN * Level39Weather.rain();
        float r = Mth.lerp(glow, TOWN_FOG_R, GLOW_FOG_R) * dark;
        float g = Mth.lerp(glow, TOWN_FOG_G, GLOW_FOG_G) * dark;
        float b = Mth.lerp(glow, TOWN_FOG_B, GLOW_FOG_B) * dark;
        float ur = Mth.lerp(stagnant, TUNNEL_FOG_R, STAGNANT_FOG_R);
        float ug = Mth.lerp(stagnant, TUNNEL_FOG_G, STAGNANT_FOG_G);
        float ub = Mth.lerp(stagnant, TUNNEL_FOG_B, STAGNANT_FOG_B);
        r = Mth.lerp(tunnel, r, ur);
        g = Mth.lerp(tunnel, g, ug);
        b = Mth.lerp(tunnel, b, ub);
        out[0] = Mth.lerp(cloud, r, CLOUD_FOG_R);
        out[1] = Mth.lerp(cloud, g, CLOUD_FOG_G);
        out[2] = Mth.lerp(cloud, b, CLOUD_FOG_B);
        return out;
    }
}
