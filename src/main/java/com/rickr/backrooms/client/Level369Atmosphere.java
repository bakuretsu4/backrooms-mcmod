package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.registry.Level369Blocks;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Level369Light;
import com.rickr.backrooms.world.layout.Level369Plan;
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
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import com.mojang.blaze3d.shaders.FogShape;
import org.joml.Vector3f;

/**
 * The air and light of Level 369 ("Comely"), after the user's two pictures:
 * <ul>
 *   <li><b>Each kind of room has its own light</b> ({@link #room}): the window hallways a bright,
 *       cool white whose long corridors dissolve into glowing haze far ahead; the pillar halls a
 *       low golden sunset, warm amber air; the skylit rooms a soft cream daylight. Walking from
 *       one into another blends over a second or two. The camcorder's mood follows the same
 *       weights ({@link AtmosphereGrade}).</li>
 *   <li><b>Fill light</b> ({@link #applyFill}, called by {@link ClientLighting}): a soft bounce in
 *       the room's colour wherever the skylights, windows and panels reach, easing smoothly with
 *       the light level so it never steps from block to block. Dim light (the dark rooms' glowing
 *       plants) leans cool green-cyan instead, so they read as bioluminescence, not as lamps.</li>
 *   <li><b>Haze:</b> the room's air colour in the lit rooms, deep green-black gloom in the dark
 *       ones; the fog colour and distance follow how much light is around you.</li>
 *   <li><b>The drifting light</b> ({@link Level369Light}): as a band of it passes over you, the
 *       whole room warms and brightens to gold, the air turns golden and the camcorder's picture
 *       glows; a soft swell of warmth announces it. Then it falls back.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level369Atmosphere {
    // ---- Fill light ------------------------------------------------------------------------------
    /** No light at all: a very dark green-black. */
    private static final float UNLIT_R = 0.012f;
    private static final float UNLIT_G = 0.016f;
    private static final float UNLIT_B = 0.013f;
    /** Calibrated with the camcorder moods against the user's pictures (see AtmosphereGrade's LEVEL_369). */
    private static final float MAX_FILL = 0.44f;
    /** The same without Veil, where this fill is all the light there is. */
    private static final float FALLBACK_FILL = 0.58f;
    /** Light level from which a place counts as fully lit ... */
    private static final float FULL_FROM = 12.0f;
    /** ... and below which there is no fill at all. */
    private static final float DARK_BELOW = 1.5f;
    /** The bounce in a skylit room: warm cream off pastel plaster and green lawn. */
    private static final float WARM_R = 1.0f;
    private static final float WARM_G = 0.94f;
    private static final float WARM_B = 0.78f;
    /** In a window hallway: cool, clean white daylight. */
    private static final float HALL_R = 0.95f;
    private static final float HALL_G = 0.98f;
    private static final float HALL_B = 1.0f;
    private static final float HALL_BOOST = 0.0f;
    /** In a pillar hall: low golden sunset light. */
    private static final float PILLAR_R = 1.0f;
    private static final float PILLAR_G = 0.84f;
    private static final float PILLAR_B = 0.62f;
    /** The faint light of the glowing plants. */
    private static final float GLOW_R = 0.55f;
    private static final float GLOW_G = 0.95f;
    private static final float GLOW_B = 0.90f;
    /** At the heart of the drifting light the fill is this much stronger (the camera lifts it too) ... */
    private static final float LIGHT_BOOST = 0.15f;
    /** ... and this golden. */
    private static final float GOLD_R = 1.0f;
    private static final float GOLD_G = 0.84f;
    private static final float GOLD_B = 0.56f;
    /** The swell of warmth plays when the light where you stand rises past this. */
    private static final float ARRIVE_AT = 0.2f;

    // ---- Haze ------------------------------------------------------------------------------------
    // A darker tint of each room's light, so the distance softens into colour instead of white.
    private static final float HAZE_R = 0.60f;
    private static final float HAZE_G = 0.58f;
    private static final float HAZE_B = 0.52f;
    /** A hallway's air: pale with a breath of blue, so a long corridor ends in soft light. */
    private static final float HALL_HAZE_R = 0.70f;
    private static final float HALL_HAZE_G = 0.73f;
    private static final float HALL_HAZE_B = 0.78f;
    /** A pillar hall's air: warm amber. */
    private static final float PILLAR_HAZE_R = 0.62f;
    private static final float PILLAR_HAZE_G = 0.48f;
    private static final float PILLAR_HAZE_B = 0.36f;
    private static final float GLOOM_R = 0.035f;
    private static final float GLOOM_G = 0.06f;
    private static final float GLOOM_B = 0.055f;
    private static final float BRIGHT_FOG_START = 24.0f;
    private static final float BRIGHT_FOG_END = 190.0f;
    /** In a hallway the fog closes in sooner: the corridor's far end is light, not a wall. */
    private static final float HALL_FOG_START = 8.0f;
    private static final float HALL_FOG_END = 120.0f;
    private static final float DARK_FOG_START = 3.0f;
    private static final float DARK_FOG_END = 40.0f;
    private static final float GOLDEN_HAZE_R = 0.68f;
    private static final float GOLDEN_HAZE_G = 0.56f;
    private static final float GOLDEN_HAZE_B = 0.40f;
    /** How fast the room's light follows you from one kind of room into another (per tick). */
    private static final float ROOM_FOLLOW = 0.03f;

    /** 0..1: how bright it is around you (smoothed vanilla block light at your eyes). */
    private static float brightness = 1.0f;
    /** 0..1: how much of the drifting light is on you (smoothed). */
    private static float light;
    /** The light's strength last tick, to hear it arrive. */
    private static float lastTarget;
    /** Smoothed weights of the kinds of room around you (Level369Plan.HALL, PILLAR, GARDEN, DARK). */
    private static final float[] ROOM = {0.0f, 0.0f, 1.0f, 0.0f};

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

    /** How much of the light around you is that of a room of {@code kind} (Level369Plan.HALL ...), 0..1. */
    public static float room(int kind) {
        return inLevel369() ? ROOM[kind] : 0.0f;
    }

    /**
     * Lightmap fill for Level 369: see the class comment. {@code fallback}: without Veil there are
     * no pools of light on top, so the fill is all there is and reaches a little higher.
     */
    public static void applyFill(Vector3f colors, int blockLight, boolean fallback) {
        float t = Mth.clamp((blockLight - DARK_BELOW) / (FULL_FROM - DARK_BELOW), 0.0f, 1.0f);
        float s = t * t * (3.0f - 2.0f * t);
        float fill = (fallback ? FALLBACK_FILL : MAX_FILL) * s * (1.0f + LIGHT_BOOST * light);
        // The room's colour where it's bright, the glowing plants' cool green where it's dim;
        // golden in the drifting light.
        float warm = Mth.clamp((t - 0.25f) / 0.5f, 0.0f, 1.0f);
        float hall = ROOM[Level369Plan.HALL];
        float pillar = ROOM[Level369Plan.PILLAR];
        float rest = Math.max(0.0f, 1.0f - hall - pillar);
        fill *= 1.0f + HALL_BOOST * hall;
        float roomR = hall * HALL_R + pillar * PILLAR_R + rest * WARM_R;
        float roomG = hall * HALL_G + pillar * PILLAR_G + rest * WARM_G;
        float roomB = hall * HALL_B + pillar * PILLAR_B + rest * WARM_B;
        float r = Mth.lerp(light, Mth.lerp(warm, GLOW_R, roomR), GOLD_R);
        float g = Mth.lerp(light, Mth.lerp(warm, GLOW_G, roomG), GOLD_G);
        float b = Mth.lerp(light, Mth.lerp(warm, GLOW_B, roomB), GOLD_B);
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
            long seed = Level369ClientState.seed();
            float wave = Level369Light.strength(seed, mc.player.getX(), mc.player.getZ(), mc.level.getGameTime());
            if (lastTarget < ARRIVE_AT && wave >= ARRIVE_AT) {
                mc.player.playSound(ModSounds.LEVEL37_ARRIVE.get(), 0.35f, 0.9f); // warmth swells in
            }
            lastTarget = wave;
            light += (wave - light) * 0.04f;
            int kind = Level369Plan.roomKind(seed, mc.player.getBlockX(), mc.player.getBlockZ());
            for (int k = 0; k < ROOM.length; k++) {
                ROOM[k] += ((k == kind ? 1.0f : 0.0f) - ROOM[k]) * ROOM_FOLLOW;
            }
        }
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE || !inLevel369()) {
            return;
        }
        float b = brightness * brightness;
        float hall = ROOM[Level369Plan.HALL];
        float start = Mth.lerp(hall, BRIGHT_FOG_START, HALL_FOG_START);
        float end = Mth.lerp(hall, BRIGHT_FOG_END, HALL_FOG_END);
        event.setNearPlaneDistance(Mth.lerp(b, DARK_FOG_START, start));
        event.setFarPlaneDistance(Mth.lerp(b, DARK_FOG_END, end));
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true); // required for NeoForge to use our distances
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inLevel369() || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float t = (float) Math.pow(brightness, 1.2);
        float hall = ROOM[Level369Plan.HALL];
        float pillar = ROOM[Level369Plan.PILLAR];
        float rest = Math.max(0.0f, 1.0f - hall - pillar);
        float hazeR = hall * HALL_HAZE_R + pillar * PILLAR_HAZE_R + rest * HAZE_R;
        float hazeG = hall * HALL_HAZE_G + pillar * PILLAR_HAZE_G + rest * HAZE_G;
        float hazeB = hall * HALL_HAZE_B + pillar * PILLAR_HAZE_B + rest * HAZE_B;
        event.setRed(Mth.lerp(light, Mth.lerp(t, GLOOM_R, hazeR), GOLDEN_HAZE_R));
        event.setGreen(Mth.lerp(light, Mth.lerp(t, GLOOM_G, hazeG), GOLDEN_HAZE_G));
        event.setBlue(Mth.lerp(light, Mth.lerp(t, GLOOM_B, hazeB), GOLDEN_HAZE_B));
    }

    /**
     * The meadow's sway marker (mod bus): the grass and flowers' block colour, white with green
     * one step down. The cutout shader (assets/minecraft/shaders/core/rendertype_cutout_mipped.vsh)
     * sways only vertices of this colour, and draws them white. Without that shader (Iris, a shader
     * pack) the plants are 1/255 less green, which nobody can see.
     */
    public static final int SWAY_MARKER = 0xFFFEFF;

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> SWAY_MARKER, Level369Blocks.FLOWER.get(), Level369Blocks.TUFT.get());
    }
}
