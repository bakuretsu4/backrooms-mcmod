package com.rickr.backrooms.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.light.BackroomsLights;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Level94Clock;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
import org.joml.Vector3f;

/**
 * How Level 94 ("Motion") looks and sounds on the client, following its own day and night
 * ({@link Level94Clock}). It is the dimension effects for {@code backrooms:motion} (the level's
 * dimension type points at it) and also listens to a few client events:
 * <ul>
 *   <li><b>Sky:</b> the painted sky with its sun, clouds and far-off castle ({@link Level94Sky}).
 *       No vanilla sun, moon or clouds.</li>
 *   <li><b>Light</b> ({@link #adjustLightmapColors}): bright, slightly warm and almost shadowless
 *       outdoors by day, dimmer but readable inside the houses, orange at dusk, and at night a very
 *       low dark-blue moonlight with warm light round the lamps. It deliberately does NOT use the
 *       dark fill of the indoor levels ({@code ClientLighting}): this level is lit by its sky.
 *       It looks the same with or without Veil, because Veil's lights are added on top of this
 *       lightmap; with Veil the house lamps are real Veil lights, so the lightmap only adds a
 *       little lamp glow, and without Veil the lightmap draws the lamp pools itself.</li>
 *   <li><b>Fog:</b> a soft pale-blue haze far away by day (the screenshots are hazy), warm at dusk,
 *       closer and dark blue at night. Its colour is the sky's horizon colour, so they meet.</li>
 *   <li><b>Ambience:</b> the biome plays a soft wind loop all the time. On top of that, by day a
 *       faint synthetic bird now and then, and at night, every half minute or so, distant cartoon
 *       laughter echoing over the hills. These are one-off sounds started here, so there is no
 *       loop to switch over at dusk and dawn: the next sound is simply the right one.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level94Atmosphere extends DimensionSpecialEffects {
    // --- Light (lightmap colours, 0..1) ----------------------------------------------------------
    /** Sunlight: white with a touch of warmth. */
    private static final float SUN_R = 1.00f;
    private static final float SUN_G = 0.97f;
    private static final float SUN_B = 0.90f;
    /** Daytime light even where little sky reaches (deep inside a house): dim but readable. */
    private static final float DAY_FLOOR = 0.24f;
    /** Exponent of the sky-light curve: outdoors (15) = full, a room lit by its windows (~11) about half. */
    private static final float SKY_FALLOFF = 2.2f;
    /** Dusk light: the sunlight turns orange as it goes. */
    private static final float DUSK_R = 1.00f;
    private static final float DUSK_G = 0.70f;
    private static final float DUSK_B = 0.48f;
    /** Moonlight at night (no moon to see, but something lights the hills): very low, dark blue. */
    private static final float MOON_R = 0.045f;
    private static final float MOON_G = 0.060f;
    private static final float MOON_B = 0.125f;
    /** The darkest it gets anywhere at night. */
    private static final float NIGHT_FLOOR_R = 0.010f;
    private static final float NIGHT_FLOOR_G = 0.012f;
    private static final float NIGHT_FLOOR_B = 0.026f;
    /** Lamp light: warm. */
    private static final float LAMP_R = 1.00f;
    private static final float LAMP_G = 0.80f;
    private static final float LAMP_B = 0.55f;
    /** With Veil the lamps are real lights, so the lightmap adds only a soft glow round them ... */
    private static final float VEIL_LAMP_FILL = 0.35f;
    private static final float VEIL_LAMP_FALLOFF = 2.2f;
    /** ... without Veil the lightmap makes the whole pool of lamp light. */
    private static final float FALLBACK_LAMP_FILL = 0.95f;
    private static final float FALLBACK_LAMP_FALLOFF = 1.6f;
    /** Full-bright faces (the TV's static, lamp bulbs, the throne light): just under the bloom limit. */
    private static final float EMISSIVE = 0.95f;

    // --- Fog (blocks) ----------------------------------------------------------------------------
    /** By day the haze starts at this fraction of the view distance and is thick at the edge. */
    private static final float DAY_FOG_START = 0.12f;
    /** At night the dark closes in: fog from 4 blocks, solid by 80 (or the view distance). */
    private static final float NIGHT_FOG_START = 4.0f;
    private static final float NIGHT_FOG_END = 80.0f;

    // --- Ambience --------------------------------------------------------------------------------
    /** A bird every 7-18 s by day, somewhere 8-12 blocks off, quiet. */
    private static final int BIRD_MIN_TICKS = 7 * 20;
    private static final int BIRD_MAX_TICKS = 18 * 20;
    private static final float BIRD_VOLUME = 0.6f;
    /** Laughter every 25-60 s at night (not dusk or dawn), ~40 blocks off across the hills. */
    private static final int LAUGH_MIN_TICKS = 25 * 20;
    private static final int LAUGH_MAX_TICKS = 60 * 20;
    private static final float LAUGH_VOLUME = 4.0f; // > 1 only makes it carry further (64 blocks)
    private static final double LAUGH_DISTANCE = 40.0;

    private static final RandomSource RANDOM = RandomSource.create();
    private static int ticksToNextSound = -1;
    @Nullable
    private static Level94Clock.Phase soundPhase;

    public Level94Atmosphere() {
        // No clouds (NaN cloud height), has ground, a normal sky (drawn by renderSky below).
        super(Float.NaN, true, SkyType.NORMAL, false, false);
    }

    /** Mod bus: Level 94's dimension type uses {@code "effects": "backrooms:motion"}. */
    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Backrooms.id("motion"), new Level94Atmosphere());
    }

    /** True when the client is in Level 94. */
    public static boolean inLevel94() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_94;
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

    /** No vanilla sunrise glow: the painted sky does its own dusk. */
    @Override
    @Nullable
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return null;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        Level94Sky.render(level, partialTick, modelViewMatrix, camera);
        setupFog.run(); // put the sky fog back the way vanilla leaves it
        return true;
    }

    /**
     * The lightmap: for every pair of sky light (pixelY, 0..15) and block light (pixelX, 0..15)
     * the colour things are drawn with. The dimension is stuck at noon for Minecraft, so sky
     * light 15 just means "under the open sky"; how bright that is comes from our clock.
     */
    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker,
                                     float skyLight, int pixelX, int pixelY, Vector3f colors) {
        float day = Level94Clock.daylight(level, partialTicks);
        if (pixelX == 15 && pixelY == 15) {
            colors.set(EMISSIVE, EMISSIVE, EMISSIVE);
        } else {
            float sky = (float) Math.pow(pixelY / 15.0f, SKY_FALLOFF);
            // Daylight: full outdoors, never below the floor indoors; turning orange at dusk/dawn.
            float lit = DAY_FLOOR + (1.0f - DAY_FLOOR) * sky;
            float sunset = Level94Sky.sunsetAmount(level, partialTicks);
            float dr = Mth.lerp(sunset, SUN_R, DUSK_R) * lit;
            float dg = Mth.lerp(sunset, SUN_G, DUSK_G) * lit;
            float db = Mth.lerp(sunset, SUN_B, DUSK_B) * lit;
            // Night: a little dark-blue moonlight outdoors, almost nothing indoors.
            float nr = NIGHT_FLOOR_R + MOON_R * sky;
            float ng = NIGHT_FLOOR_G + MOON_G * sky;
            float nb = NIGHT_FLOOR_B + MOON_B * sky;
            // Lamps: warm, on top (they hardly show by day, and make the pools of light at night).
            boolean veil = BackroomsLights.isVeilLightingActive();
            float lamp = (veil ? VEIL_LAMP_FILL : FALLBACK_LAMP_FILL)
                    * (float) Math.pow(pixelX / 15.0f, veil ? VEIL_LAMP_FALLOFF : FALLBACK_LAMP_FALLOFF);
            colors.set(Math.min(1.0f, Mth.lerp(day, nr, dr) + lamp * LAMP_R),
                    Math.min(1.0f, Mth.lerp(day, ng, dg) + lamp * LAMP_G),
                    Math.min(1.0f, Mth.lerp(day, nb, db) + lamp * LAMP_B));
        }
        // The camcorder's NightShot (and a dead battery) only matter in the dark.
        if (day < 0.5f) {
            ClientLighting.applyCamcorder(colors);
        }
    }

    // ==========================================================================================
    // Fog
    // ==========================================================================================

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        // Leave the vanilla fog alone under water / in lava / powder snow, and for the sky pass
        // (the painted sky does its own horizon).
        if (event.getType() != FogType.NONE || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || !inLevel94()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        float day = Level94Clock.daylight(level, (float) event.getPartialTick());
        float view = event.getFarPlaneDistance();
        event.setNearPlaneDistance(Mth.lerp(day, NIGHT_FOG_START, view * DAY_FOG_START));
        event.setFarPlaneDistance(Mth.lerp(day, Math.min(view, NIGHT_FOG_END), view));
        event.setFogShape(FogShape.CYLINDER);
        event.setCanceled(true); // required for NeoForge to use our distances
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inLevel94() || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        Vector3f horizon = Level94Sky.horizonColor(Minecraft.getInstance().level, (float) event.getPartialTick(), new Vector3f());
        event.setRed(horizon.x);
        event.setGreen(horizon.y);
        event.setBlue(horizon.z);
    }

    // ==========================================================================================
    // Ambience
    // ==========================================================================================

    /** Birds by day, laughter at night: one sound now and then, chosen by the time of day. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused() || !inLevel94()) {
            soundPhase = null;
            return;
        }
        Level94Clock.Phase phase = Level94Clock.phase(mc.level);
        if (phase != soundPhase) {
            // Day turned to dusk, night to dawn...: start counting afresh for the new kind of sound.
            soundPhase = phase;
            ticksToNextSound = nextDelay(phase);
        }
        if (ticksToNextSound < 0 || --ticksToNextSound > 0) {
            return;
        }
        ticksToNextSound = nextDelay(phase);
        if (phase == Level94Clock.Phase.DAY) {
            play(mc, ModSounds.L94_BIRDS.get(), 8.0 + RANDOM.nextDouble() * 4.0, 3.0 + RANDOM.nextDouble() * 4.0,
                    BIRD_VOLUME, 0.92f + RANDOM.nextFloat() * 0.2f);
        } else if (phase == Level94Clock.Phase.NIGHT) {
            // Pitch varies a lot: a different "voice" each time.
            play(mc, ModSounds.L94_LAUGH.get(), LAUGH_DISTANCE, RANDOM.nextDouble() * 6.0,
                    LAUGH_VOLUME, 0.8f + RANDOM.nextFloat() * 0.35f);
        }
    }

    /** Ticks until the next sound in this phase; -1 = none (dusk and dawn are silent). */
    private static int nextDelay(Level94Clock.Phase phase) {
        return switch (phase) {
            case DAY -> Mth.nextInt(RANDOM, BIRD_MIN_TICKS, BIRD_MAX_TICKS);
            case NIGHT -> Mth.nextInt(RANDOM, LAUGH_MIN_TICKS, LAUGH_MAX_TICKS);
            default -> -1;
        };
    }

    /** One sound from a random direction, {@code distance} blocks off and {@code up} blocks up. */
    private static void play(Minecraft mc, SoundEvent sound, double distance, double up, float volume, float pitch) {
        Vec3 eye = mc.player.getEyePosition();
        float angle = RANDOM.nextFloat() * Mth.TWO_PI;
        mc.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.AMBIENT, volume, pitch, RANDOM,
                eye.x + Mth.cos(angle) * distance, eye.y + up, eye.z + Mth.sin(angle) * distance));
    }
}
