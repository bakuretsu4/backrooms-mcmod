package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.light.FixturePower;
import com.rickr.backrooms.network.LevelEventPayload;
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
 * The air and light of Level 4 (the Abandoned Office) on a stormy night.
 *
 * <ul>
 *   <li><b>Window glow.</b> The ribbon windows are "blacked out, yet still glow" (canon): their
 *       blocks give light {@value #WINDOW_LIGHT}. With Veil the real light comes from them as soft,
 *       shadowed area lights (client/light/Level4Lights): a cool gradient that is bright at the
 *       window wall and falls off into the floor plate, with the columns and partitions shading
 *       it. This lightmap fill only adds a low cool bounce on top (kept low so white walls and
 *       columns no longer clip) and a faint cool "nothing is lit" floor so the deep interior stays
 *       readable rather than black.</li>
 *   <li><b>Lightning</b> (the STORM event): two quick white pops and a fade, sometimes a weaker
 *       second strike. The flash is added to the lightmap in proportion to block light, so the
 *       window glass and everything near the windows flashes cold white while windowless corners
 *       barely change (Level4Lights flares the window lights with it). The thunder is sent by the
 *       server a second or two later.</li>
 *   <li><b>Haze.</b> A slight cool indoor haze: the far end of a big open floor fades into a dark
 *       blue-grey, which lifts a little with each flash.</li>
 * </ul>
 *
 * <p>Under Iris/shader packs none of this runs (the pack lights by block light itself): there the
 * window's emission ({@value #WINDOW_LIGHT}, not 15) and the troffers' (10) carry the look.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level4Atmosphere {
    /**
     * Block light of the office windows (registry/ModBlocks OFFICE_WINDOW). Only the windows' own
     * faces see this much: the air in front of them has one less, the lit troffers give 10. So
     * "block light {@value} or more" is the glass itself. Must match ModBlocks.
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
     * (AtmosphereGrade ROLLOFF_4) keeps its gradient and rain streaks from clipping.
     */
    private static final float GLASS_FILL = 0.85f;
    /** How much a full flash adds at the window's light, and how fast it falls off with less light. */
    private static final float FLASH_FILL = 0.95f;
    private static final float FLASH_FALLOFF = 2.2f;
    private static final float FLASH_R = 0.86f;
    private static final float FLASH_G = 0.92f;
    private static final float FLASH_B = 1.0f;
    /** Without Veil (and without Iris), vanilla light near the windows is a little too bright for a night office. */
    private static final float FALLBACK_DIM = 0.8f;

    // --- Fog --------------------------------------------------------------------------------------
    /** Subtle: the far end of a big floor fades, the room you stand in stays clear. */
    private static final float FOG_START = 12.0f;
    private static final float FOG_END = 96.0f;
    /** #101318: a dark, cool blue-grey (not black: the building's interior is dim, not void). */
    private static final float FOG_R = 16 / 255.0f;
    private static final float FOG_G = 19 / 255.0f;
    private static final float FOG_B = 24 / 255.0f;
    /** What the fog brightens toward during a flash (scaled by the flash). */
    private static final float FOG_FLASH_R = 0.30f;
    private static final float FOG_FLASH_G = 0.33f;
    private static final float FOG_FLASH_B = 0.38f;
    private static final float FOG_FLASH_SHARE = 0.35f;

    /** True when the client is in Level 4. */
    public static boolean inLevel4() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_4;
    }

    /**
     * 0..1 brightness of the lightning outside right now. The flicker pattern of each strike comes
     * from the event's start time, so it is the same for everyone on the floor.
     */
    public static float flash(long nowMs) {
        if (FixturePower.activeEvent(nowMs) != LevelEventPayload.STORM) {
            return 0.0f;
        }
        long start = CameraClientState.eventStartMs;
        long t = nowMs - start;
        if (t < 0) {
            return 0.0f;
        }
        long h = FixturePower.mix(start ^ 0x4C3A9F01B2E6D5C7L);
        float v = strike(t, 0, 1.0f);
        if ((h & 0xFF) < 140) {
            // A second, weaker strike 0.6-1.3 s later.
            long second = 600 + ((h >>> 8) & 0xFFFF) % 700;
            v = Math.max(v, strike(t, second, 0.55f + ((h >>> 24) & 0xFF) / 255.0f * 0.3f));
        }
        return Mth.clamp(v, 0.0f, 1.0f);
    }

    /** One strike: a white pop, a short dip, a second pop, then a fade over ~half a second. */
    private static float strike(long t, long at, float peak) {
        long dt = t - at;
        if (dt < 0) return 0.0f;
        if (dt < 60) return peak;
        if (dt < 130) return peak * 0.2f;
        if (dt < 220) return peak * 0.85f;
        return peak * 0.85f * (float) Math.exp(-(dt - 220) / 240.0);
    }

    /** Level 4's lightmap cell for {@code blockLight} when Veil lights the scene (see ClientLighting). */
    static void applyFill(Vector3f colors, int blockLight) {
        float b = Mth.clamp(blockLight / (float) WINDOW_LIGHT, 0.0f, 1.0f);
        float fill = blockLight >= WINDOW_LIGHT ? GLASS_FILL : MAX_FILL * (float) Math.pow(b, FILL_FALLOFF);
        float flash = flash(System.currentTimeMillis()) * FLASH_FILL * (float) Math.pow(b, FLASH_FALLOFF);
        colors.set(UNLIT_R + fill * TINT_R + flash * FLASH_R,
                UNLIT_G + fill * TINT_G + flash * FLASH_G,
                UNLIT_B + fill * TINT_B + flash * FLASH_B);
    }

    /** Without Veil: dim the vanilla light a little, and add the flash the same way. */
    static void adjustFallback(Vector3f colors, int blockLight) {
        float b = Mth.clamp(blockLight / (float) WINDOW_LIGHT, 0.0f, 1.0f);
        float flash = flash(System.currentTimeMillis()) * 0.8f * b * b;
        colors.set(colors.x * FALLBACK_DIM * 0.95f + flash * FLASH_R,
                colors.y * FALLBACK_DIM * 0.98f + flash * FLASH_G,
                colors.z * FALLBACK_DIM + flash * FLASH_B);
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
        float f = flash(System.currentTimeMillis()) * FOG_FLASH_SHARE;
        event.setRed(Mth.lerp(f, FOG_R, FOG_FLASH_R));
        event.setGreen(Mth.lerp(f, FOG_G, FOG_FLASH_G));
        event.setBlue(Mth.lerp(f, FOG_B, FOG_FLASH_B));
    }

    private Level4Atmosphere() {}
}
