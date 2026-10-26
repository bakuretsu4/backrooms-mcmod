package com.rickr.backrooms.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.light.FixturePower;
import com.rickr.backrooms.client.anomaly.level9.CrimsonFieldClient;
import com.rickr.backrooms.network.LevelEventPayload;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The air of Level 9 (the Suburbs): thick, low, dark grey-green fog, and the FOG_ROLL event.
 *
 * <ul>
 *   <li><b>Normal fog:</b> starts a few blocks out and is solid by ~34 blocks, so in the street
 *       you see about 28-34 blocks: the next lamp or two, then nothing. The fog colour is very
 *       dark (#0B0E0D), so lit things glow out of it instead of the fog glowing itself. The
 *       light pools around you (under 10 blocks away) are barely touched.</li>
 *   <li><b>FOG_ROLL event</b> (the Mangled's fog, see {@code world/anomaly/level9/MangledFog}):
 *       first a sudden cold (frost, breath: {@code client/anomaly/level9}), then over 6 s the fog
 *       closes in to ~12 blocks and the streetlamps' hum cuts out ({@link #lampHumScale}). A few
 *       times while it lasts, something huge rumbles far away in it (The Mangled moving) and the
 *       camera shakes lightly. The fog lifts over the last 8 s of the event.</li>
 *   <li><b>Level 9.1, the Crimson Field</b> (far east in this same dimension): a closer, colder
 *       mist that turns crimson with each pulse of the Gloom ({@code CrimsonFieldClient}).</li>
 * </ul>
 *
 * <p>The rumbles are scheduled here on the client from the event's start time, so the sound and
 * the shake always line up; the server only says "fog roll is on" (same payload as every event).
 *
 * <p>Also registers the {@code backrooms:suburbs} dimension effects (Level 9's dimension type
 * points at it): the same {@link BackroomsDimensionEffects} as the other levels, which draws the
 * black starry sky when it sees it is in Level 9.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level9Atmosphere {
    // --- Fog distances (blocks) ------------------------------------------------------------------
    /** Fog begins here: the area right around you (and the pool you stand in) stays clear. */
    private static final float FOG_START = 4.0f;
    /** Fully fogged here: ~28-34 blocks of visibility down the street. */
    private static final float FOG_END = 34.0f;
    /** During FOG_ROLL. */
    private static final float ROLL_FOG_START = 1.0f;
    private static final float ROLL_FOG_END = 12.0f;
    private static final float ROLL_IN_MS = 6000.0f;
    private static final float ROLL_OUT_MS = 8000.0f;
    /** The Mangled's fog: first the temperature drops, then (this much later) the fog rolls in. */
    private static final float CHILL_MS = 2500.0f;

    // --- Fog colour ------------------------------------------------------------------------------
    /** #0B0E0D: near-black with a damp grey-green cast. */
    private static final float FOG_R = 11 / 255.0f;
    private static final float FOG_G = 14 / 255.0f;
    private static final float FOG_B = 13 / 255.0f;
    /** #121715: the rolled-in fog is a touch lighter, so it reads as a wall rather than darkness. */
    private static final float ROLL_FOG_R = 18 / 255.0f;
    private static final float ROLL_FOG_G = 23 / 255.0f;
    private static final float ROLL_FOG_B = 21 / 255.0f;
    /**
     * Level 9.1, the Crimson Field: a closer, colder mist (#1C2024) ... A dark grey rather than
     * black, so the grass stands out against it as dark stalks instead of vanishing.
     */
    private static final float FIELD_FOG_START = 2.0f;
    private static final float FIELD_FOG_END = 28.0f;
    private static final float FIELD_FOG_R = 28 / 255.0f;
    private static final float FIELD_FOG_G = 32 / 255.0f;
    private static final float FIELD_FOG_B = 36 / 255.0f;
    /** ... that turns crimson (#4A0A0A) while the Gloom glows. */
    private static final float GLOOM_FOG_R = 74 / 255.0f;
    private static final float GLOOM_FOG_G = 10 / 255.0f;
    private static final float GLOOM_FOG_B = 10 / 255.0f;

    // --- Rumbles ---------------------------------------------------------------------------------
    /** At most this many rumbles per fog roll ("a few"). */
    private static final int MAX_RUMBLES = 4;
    /** The first comes 4.5-7.5 s in (once the fog is thick), then every 9-17 s. */
    private static final long FIRST_RUMBLE_MS = 4500L;
    /** No rumble starts in the last this-many ms (the fog is clearing). */
    private static final long RUMBLE_QUIET_END_MS = 10000L;
    /** How far away the rumble plays (blocks). Volume 4 makes it carry 64 blocks, so it's faint. */
    private static final double RUMBLE_DISTANCE = 36.0;
    private static final float RUMBLE_VOLUME = 4.0f;
    /** Light shake: peak degrees, how long it lasts and how fast it swells. */
    private static final float SHAKE_DEG = 0.6f;
    private static final float SHAKE_MS = 4500.0f;
    private static final float SHAKE_RISE_MS = 400.0f;

    private static final RandomSource RANDOM = RandomSource.create();
    /** The fog roll the rumble count below belongs to (its start time). */
    private static long rollStartMs = Long.MIN_VALUE;
    private static int rumblesPlayed;
    /** When the last rumble started (system ms), for the camera shake; 0 = none. */
    private static long shakeStartMs;

    /** Mod bus: Level 9's dimension type uses {@code "effects": "backrooms:suburbs"}. */
    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Backrooms.id("suburbs"), new BackroomsDimensionEffects());
    }

    /** True when the client is in Level 9. */
    public static boolean inLevel9() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_9;
    }

    /**
     * 0..1 how far the FOG_ROLL event has closed in right now: after the first 2.5 s (the cold
     * comes first) it eases in over 6 s, holds, and eases out over the last 8 s. 0 when no fog
     * roll is running.
     */
    public static float fogRoll(long nowMs) {
        if (FixturePower.activeEvent(nowMs) != LevelEventPayload.FOG_ROLL) {
            return 0.0f;
        }
        float in = (nowMs - CameraClientState.eventStartMs - CHILL_MS) / ROLL_IN_MS;
        float out = (CameraClientState.eventEndMs - nowMs) / ROLL_OUT_MS;
        float t = Mth.clamp(Math.min(in, out), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /** The fog colour right now (the sky uses it for its horizon so the two meet seamlessly). */
    public static float fogRed(float roll) {
        return Mth.lerp(roll, FOG_R, ROLL_FOG_R);
    }

    public static float fogGreen(float roll) {
        return Mth.lerp(roll, FOG_G, ROLL_FOG_G);
    }

    public static float fogBlue(float roll) {
        return Mth.lerp(roll, FOG_B, ROLL_FOG_B);
    }

    /** The fog colour right now (the sky uses it for its horizon so the two meet seamlessly). */
    public static float[] fogColour(long nowMs) {
        if (inField()) {
            float pulse = CrimsonFieldClient.gloomPulse(0.0f) * (0.6f + 0.4f * CrimsonFieldClient.proximity());
            return new float[] {Mth.lerp(pulse, FIELD_FOG_R, GLOOM_FOG_R), Mth.lerp(pulse, FIELD_FOG_G, GLOOM_FOG_G),
                    Mth.lerp(pulse, FIELD_FOG_B, GLOOM_FOG_B)};
        }
        float roll = fogRoll(nowMs);
        return new float[] {fogRed(roll), fogGreen(roll), fogBlue(roll)};
    }

    /** True when the player is out in Level 9.1, the Crimson Field. */
    public static boolean inField() {
        return CrimsonFieldClient.inField(Minecraft.getInstance());
    }

    /**
     * How loud the streetlamps may hum (0..1): as the Mangled's fog thickens their buzz cuts out,
     * and the silence is total ("away from the main suburb, the hum stops").
     */
    public static float lampHumScale(long nowMs) {
        if (!inLevel9()) {
            return 1.0f;
        }
        return Mth.clamp(1.0f - fogRoll(nowMs) * 2.0f, 0.0f, 1.0f);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        // Leave the vanilla fog alone under water / in lava / powder snow.
        if (event.getType() != FogType.NONE || !inLevel9()) {
            return;
        }
        if (inField()) {
            event.setNearPlaneDistance(FIELD_FOG_START);
            event.setFarPlaneDistance(FIELD_FOG_END);
        } else {
            float roll = fogRoll(System.currentTimeMillis());
            event.setNearPlaneDistance(Mth.lerp(roll, FOG_START, ROLL_FOG_START));
            event.setFarPlaneDistance(Mth.lerp(roll, FOG_END, ROLL_FOG_END));
        }
        // A sphere (not the vanilla cylinder) so the lamp heads above you fog the same as the road.
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true); // required for NeoForge to use our distances
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inLevel9() || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float[] colour = fogColour(System.currentTimeMillis());
        event.setRed(colour[0]);
        event.setGreen(colour[1]);
        event.setBlue(colour[2]);
    }

    /** Schedules the far-off rumbles while the fog roll runs. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.player == null || !inLevel9() || inField() || FixturePower.activeEvent(now) != LevelEventPayload.FOG_ROLL) {
            return;
        }
        long start = CameraClientState.eventStartMs;
        if (start != rollStartMs) {
            // A new fog roll (or we just arrived in one): skip rumbles that are already over.
            rollStartMs = start;
            rumblesPlayed = 0;
            while (rumblesPlayed < MAX_RUMBLES && rumbleAt(start, rumblesPlayed) < now - 1000L) {
                rumblesPlayed++;
            }
        }
        if (rumblesPlayed >= MAX_RUMBLES) {
            return;
        }
        long at = rumbleAt(start, rumblesPlayed);
        if (at > CameraClientState.eventEndMs - RUMBLE_QUIET_END_MS) {
            rumblesPlayed = MAX_RUMBLES; // too close to the end: no more this time
            return;
        }
        if (now >= at) {
            rumblesPlayed++;
            playRumble(mc);
            shakeStartMs = now;
        }
    }

    /**
     * When rumble number {@code index} of the fog roll that started at {@code startMs} happens.
     * Derived from the start time alone, so arriving mid-event gives the same schedule.
     */
    private static long rumbleAt(long startMs, int index) {
        long at = startMs + FIRST_RUMBLE_MS + Math.floorMod(mixSeed(startMs), 3000L);
        for (int i = 1; i <= index; i++) {
            at += 9000L + Math.floorMod(mixSeed(startMs + i * 7919L), 8000L);
        }
        return at;
    }

    private static long mixSeed(long value) {
        return FixturePower.mix(value ^ 0x3C6EF372FE94F82BL);
    }

    /** One deep rumble from a random direction, far out in the fog. */
    private static void playRumble(Minecraft mc) {
        Vec3 eye = mc.player.getEyePosition();
        float angle = RANDOM.nextFloat() * Mth.TWO_PI;
        double x = eye.x + Mth.cos(angle) * RUMBLE_DISTANCE;
        double z = eye.z + Mth.sin(angle) * RUMBLE_DISTANCE;
        float pitch = 0.8f + RANDOM.nextFloat() * 0.2f;
        mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.LEVEL9_DISTANT_RUMBLE.get(), SoundSource.AMBIENT,
                RUMBLE_VOLUME, pitch, RANDOM, x, eye.y, z));
    }

    /**
     * Light camera shake while a rumble rolls through. Runs after the handheld camera
     * (lowest priority) and only nudges the angles it already set.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shakeStartMs == 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        float since = now - shakeStartMs;
        if (since >= SHAKE_MS || !inLevel9()) {
            shakeStartMs = 0L;
            return;
        }
        // Swell in quickly, die away slowly, like a heavy footfall rolling through the ground.
        float envelope = Math.min(1.0f, since / SHAKE_RISE_MS) * (1.0f - since / SHAKE_MS);
        envelope *= envelope;
        float t = since / 1000.0f;
        // A few unrelated sine waves = a rough, non-repeating tremble.
        float pitch = (Mth.sin(t * 61.0f) * 0.6f + Mth.sin(t * 13.0f) * 0.4f) * SHAKE_DEG * envelope;
        float yaw = (Mth.sin(t * 47.0f + 1.3f) * 0.5f + Mth.sin(t * 9.0f) * 0.5f) * SHAKE_DEG * 0.6f * envelope;
        float roll = (Mth.sin(t * 53.0f + 2.1f) * 0.6f + Mth.sin(t * 11.0f) * 0.4f) * SHAKE_DEG * envelope;
        event.setPitch(event.getPitch() + pitch);
        event.setYaw(event.getYaw() + yaw);
        event.setRoll(event.getRoll() + roll);
    }

    private Level9Atmosphere() {}
}
