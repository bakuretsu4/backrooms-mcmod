package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * How hard it rains in Level 490 (client side): always, swelling and easing in slow waves. The
 * rain itself is our own ({@link Level490Rain}, after the play-test: "too vanilla looking, less
 * heavy"), so Minecraft's is kept off here; {@link Level490Ambience} plays its hiss.
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
        // Steady, never stopping; two slow swells on top that rarely line up.
        float want = (float) (0.5 + 0.3 * (0.5 + 0.5 * Math.sin(s / 17.0) * Math.sin(s / 41.0 + 2.0)));
        rain += (want - rain) * 0.05f;
        mc.level.setRainLevel(0.0f);
    }
}
