package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.light.FixturePower;
import com.rickr.backrooms.network.LevelEventPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Level 39's indoor storm on the client. Canon: the superstructure has its own climate and its
 * own clouds, so it rains on the town even though the town is "inside".
 *
 * <p>The server runs the INDOOR_RAIN floor event ({@code world/LevelEvents}, 2-5 minutes every
 * 10-20 minutes or so; force it with {@code /backrooms event rain}). While it runs we turn the
 * client world's rain on ourselves: Minecraft's normal rain then falls from the sky (out of our
 * cloud ceiling, see {@link Level39Sky}), splashes on the ground and patters on roofs with its
 * normal sounds, and is kept off under roofs and in the tunnels by the height map as usual. On
 * top of that {@link Level39Audio} plays the wide hiss of rain on the whole enormous space.
 *
 * <p>The rain eases in over the first {@value #RAIN_IN_MS} ms and out over the last
 * {@value #RAIN_OUT_MS} ms. The level's biome must allow rain ({@code has_precipitation: true}).
 * Puddles are always there (real water blocks in the ground); they ripple while it rains
 * ({@code client/water/WaterStyle}).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level39Weather {
    /** How long the rain takes to build up and to die away (ms). */
    private static final float RAIN_IN_MS = 25000.0f;
    private static final float RAIN_OUT_MS = 20000.0f;
    /** The strongest it rains (Minecraft rain level 0..1). Not a downpour: steady indoor rain. */
    private static final float MAX_RAIN = 0.85f;

    private static float rain;

    /** 0..1 how hard it is raining in Level 39 right now (0 elsewhere). */
    public static float rain() {
        return rain;
    }

    /** 0..1 target from the event's timing alone, so arriving mid-storm gives the same value. */
    private static float target(long nowMs) {
        if (FixturePower.activeEvent(nowMs) != LevelEventPayload.INDOOR_RAIN) {
            return 0.0f;
        }
        float in = (nowMs - CameraClientState.eventStartMs) / RAIN_IN_MS;
        float out = (CameraClientState.eventEndMs - nowMs) / RAIN_OUT_MS;
        float t = Mth.clamp(Math.min(in, out), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !Level39ClientState.inLevel39()) {
            rain = 0.0f;
            return;
        }
        float want = target(System.currentTimeMillis());
        // A little extra easing on top, so a forced start/stop never snaps.
        rain += (want - rain) * 0.05f;
        if (rain < 0.002f && want == 0.0f) {
            rain = 0.0f;
        }
        // Set every tick: the server never sends weather for this dimension, and if anything ever
        // did, our value wins straight away.
        mc.level.setRainLevel(rain * MAX_RAIN);
        mc.level.setThunderLevel(0.0f);
    }

    private Level39Weather() {}
}
