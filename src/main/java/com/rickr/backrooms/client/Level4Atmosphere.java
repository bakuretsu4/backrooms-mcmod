package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;

/**
 * The air and light of Level 4 (the Abandoned Office): an endless empty office lit by its windows,
 * which are blacked out yet still glow (canon). There is no weather and no time of day.
 *
 * <ul>
 *   <li><b>Window glow.</b> The ribbon windows give light {@value #WINDOW_LIGHT}. With Veil the
 *       real light comes from them as soft, shadowed area lights (client/light/Level4Lights): a
 *       cool gradient that is bright at the window wall and falls off into the floor plate, with
 *       the columns and partitions shading it. This lightmap fill only adds a low cool bounce on
 *       top (kept low so white walls and columns no longer clip) and a faint cool "nothing is lit"
 *       floor so the deep interior stays readable rather than black.</li>
 *   <li><b>Haze.</b> A slight cool indoor haze: the far end of a big open floor fades into a dark
 *       blue-grey.</li>
 * </ul>
 *
 * <p>Under Iris/shader packs none of this runs (the pack lights by block light itself): there the
 * window's emission ({@value #WINDOW_LIGHT}, less behind blinds or tape) and the troffers' (10) carry
 * the look.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level4Atmosphere {
    /**
     * Block light of a bare office window's glass (registry/ModBlocks OFFICE_WINDOW). Every window's
     * glass face is marked this much in its model (neoforge_data), and nothing else on Level 4 gives
     * this much (troffers and coffers 10, pendants 9, covered windows 10-11, so the air in front of
     * a window has at most 11). So "block light {@value} or more" is the glass itself. Must match
     * ModBlocks and the window models.
     */
    public static final int WINDOW_LIGHT = 12;

    // --- Lightmap (Veil mode) -------------------------------------------------------------------
    /**
     * "No light at all": a dim cool grey, not black. The deep interior of a big floor gets no
     * block light, and canon Level 4 is dim there but never pitch dark (the glow from the windows
     * fills the building), so this keeps carpet and columns readable far from any window.
     */
    private static final float UNLIT_R = 0.040f;
    private static final float UNLIT_G = 0.045f;
    private static final float UNLIT_B = 0.058f;
    /**
     * Fill curve over block light (0 .. {@link #WINDOW_LIGHT}): MAX_FILL * (light / 12)^FILL_FALLOFF.
     * The air right in front of a window (11) gets ~0.17, 3 blocks in (9) ~0.11, 7 blocks in ~0.03.
     * Around a lit troffer (10) the ceiling gets ~0.13 and the floor (~6) ~0.04. Low on purpose: the
     * Veil window and troffer lights make the gradient and the pools; this is only their bounce
     * (it was 0.34 at 2.4 over light 15, which with the old lights clipped every white surface).
     */
    private static final float MAX_FILL = 0.20f;
    private static final float FILL_FALLOFF = 2.2f;
    /** Cool overcast blue-grey for the window glow (fluorescent white is cool too). */
    private static final float TINT_R = 0.80f;
    private static final float TINT_G = 0.87f;
    private static final float TINT_B = 1.0f;
    /**
     * The window glass itself: its own cold glow, the brightest surface on its wall (the walls
     * around a window get only the low fill: the window lights shine away from their own wall).
     * The glass face is marked block light {@value #WINDOW_LIGHT} in its model (neoforge_data, no
     * ambient occlusion): an opaque cube's faces otherwise take the darker light of the air in front
     * (first test: the glass read as dull slate, darker than the wall). The highlight shoulder
     * (AtmosphereGrade ROLLOFF_4) keeps its gradient from clipping.
     */
    private static final float GLASS_FILL = 0.85f;
    /** Without Veil (and without Iris), vanilla light near the windows is a little too bright for the dim office. */
    private static final float FALLBACK_DIM = 0.8f;

    // --- Fog --------------------------------------------------------------------------------------
    /** Subtle: the far end of a big floor fades, the room you stand in stays clear. */
    private static final float FOG_START = 12.0f;
    private static final float FOG_END = 96.0f;
    /** #101318: a dark, cool blue-grey (not black: the building's interior is dim, not void). */
    private static final float FOG_R = 16 / 255.0f;
    private static final float FOG_G = 19 / 255.0f;
    private static final float FOG_B = 24 / 255.0f;

    /** True when the client is in Level 4. */
    public static boolean inLevel4() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_4;
    }

    /** Level 4's lightmap cell for {@code blockLight} when Veil lights the scene (see ClientLighting). */
    static void applyFill(Vector3f colors, int blockLight) {
        float b = Mth.clamp(blockLight / (float) WINDOW_LIGHT, 0.0f, 1.0f);
        float fill = blockLight >= WINDOW_LIGHT ? GLASS_FILL : MAX_FILL * (float) Math.pow(b, FILL_FALLOFF);
        colors.set(UNLIT_R + fill * TINT_R, UNLIT_G + fill * TINT_G, UNLIT_B + fill * TINT_B);
    }

    /** Without Veil: dim the vanilla light a little, with a faint cool cast. */
    static void adjustFallback(Vector3f colors) {
        colors.set(colors.x * FALLBACK_DIM * 0.95f, colors.y * FALLBACK_DIM * 0.98f, colors.z * FALLBACK_DIM);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE || !inLevel4()) {
            return;
        }
        event.setNearPlaneDistance(FOG_START);
        event.setFarPlaneDistance(Math.min(FOG_END, event.getFarPlaneDistance()));
        event.setCanceled(true); // required for NeoForge to use our distances
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inLevel4() || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        event.setRed(FOG_R);
        event.setGreen(FOG_G);
        event.setBlue(FOG_B);
    }

    private Level4Atmosphere() {}
}
