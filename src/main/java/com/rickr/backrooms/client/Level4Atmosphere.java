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
 *   <li><b>Window glow.</b> The rainy windows give off block light, and Level 4's lightmap fill
 *       turns that into a faint cold blue-grey glow that reaches 3-5 blocks into the room: the
 *       floor and radiators by the windows stay dimly visible while the middle of a big dark floor
 *       is near black. The same fill gives a soft bounce under the lit ceiling panels (Veil makes
 *       the actual pools of light).</li>
 *   <li><b>Lightning</b> (the STORM event): two quick white pops and a fade, sometimes a weaker
 *       second strike. The flash is added to the lightmap in proportion to block light, so the
 *       window glass and everything near the windows flashes cold white while windowless corners
 *       barely change. The thunder is sent by the server a second or two later.</li>
 *   <li><b>Haze.</b> A slight cool indoor haze: distant parts of the open floors fade into a
 *       near-black blue-grey, which lifts a little with each flash.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level4Atmosphere {
    // --- Lightmap (Veil mode) -------------------------------------------------------------------
    /** "No light at all": near black with a cold cast. */
    private static final float UNLIT_R = 0.008f;
    private static final float UNLIT_G = 0.009f;
    private static final float UNLIT_B = 0.017f;
    /**
     * Fill curve: windows give light 15, so the air right in front of them has 14 (~0.29 fill), the
     * floor 3 blocks in ~level 11 (~0.16), 7 blocks in ~0.06. Under a lit panel (light 11) the
     * ceiling around it gets ~0.17 and the floor ~0.05: a soft bounce, Veil does the rest.
     */
    private static final float MAX_FILL = 0.34f;
    private static final float FILL_FALLOFF = 2.4f;
    /** Cold overcast blue-grey for the window glow (fluorescent white is cool too). */
    private static final float TINT_R = 0.78f;
    private static final float TINT_G = 0.86f;
    private static final float TINT_B = 1.0f;
    /**
     * The window glass itself: its model marks the glass face with block light 15 (neoforge_data),
     * and no other surface on Level 4 sees light 15 (the window blocks give 15, so the air around
     * them has at most 14). That lightmap cell is the glass's own dim cold glow.
     */
    private static final float GLASS_FILL = 0.48f;
    /** How much a full flash adds at block light 15, and how fast it falls off with less light. */
    private static final float FLASH_FILL = 0.95f;
    private static final float FLASH_FALLOFF = 2.2f;
    private static final float FLASH_R = 0.86f;
    private static final float FLASH_G = 0.92f;
    private static final float FLASH_B = 1.0f;
    /** Without Veil, the vanilla light near windows is far too bright for a night office. */
    private static final float FALLBACK_DIM = 0.7f;

    // --- Fog --------------------------------------------------------------------------------------
    private static final float FOG_START = 8.0f;
    private static final float FOG_END = 80.0f;
    /** #0B0D12: near-black with a cold tint. */
    private static final float FOG_R = 11 / 255.0f;
    private static final float FOG_G = 13 / 255.0f;
    private static final float FOG_B = 18 / 255.0f;
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
        float b = Mth.clamp(blockLight / 15.0f, 0.0f, 1.0f);
        float fill = blockLight >= 15 ? GLASS_FILL : MAX_FILL * (float) Math.pow(b, FILL_FALLOFF);
        float flash = flash(System.currentTimeMillis()) * FLASH_FILL * (float) Math.pow(b, FLASH_FALLOFF);
        colors.set(UNLIT_R + fill * TINT_R + flash * FLASH_R,
                UNLIT_G + fill * TINT_G + flash * FLASH_G,
                UNLIT_B + fill * TINT_B + flash * FLASH_B);
    }

    /** Without Veil: dim the vanilla light a little, and add the flash the same way. */
    static void adjustFallback(Vector3f colors, int blockLight) {
        float b = Mth.clamp(blockLight / 15.0f, 0.0f, 1.0f);
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
