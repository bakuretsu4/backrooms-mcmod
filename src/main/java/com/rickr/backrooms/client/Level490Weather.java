package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Level 490's rain (client side): it always pours. As in Level 39 ({@link Level39Weather}) we turn
 * the client world's rain on ourselves, so Minecraft's own rain falls, splashes on the stone and
 * patters on the ruins with its normal sounds, and stays off under roofs and inside the spire (the
 * height map). It swells and eases a little in slow waves. The level's biome allows rain
 * ({@code has_precipitation: true}); {@link Level490Ambience} plays the wide hiss of the downpour.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level490Weather {
    private static float rain;

    private Level490Weather() {}

    /** 0..1 how hard it is raining in Level 490 right now (0 elsewhere). */
    public static float rain() {
        return rain;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !Level490Atmosphere.inLevel490()) {
            rain = 0.0f;
            return;
        }
        double s = (System.currentTimeMillis() % 3_600_000L) / 1000.0;
        // Heavy all the time; two slow swells on top that rarely line up.
        float want = (float) (0.82 + 0.18 * (0.5 + 0.5 * Math.sin(s / 17.0) * Math.sin(s / 41.0 + 2.0)));
        rain += (want - rain) * 0.05f;
        mc.level.setRainLevel(rain);
    }
}
