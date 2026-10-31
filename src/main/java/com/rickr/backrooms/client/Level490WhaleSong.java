package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModSounds;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Whales singing somewhere in Level 490's storm, before you ever see one: every minute or two a
 * call from far off in some direction (now and then deeper, farther ones), and sometimes another
 * answers from elsewhere a few seconds later. Each comes as late as sound takes to cross that far
 * (Level490ChainEvents#hear). The recordings are NOAA's (tools/vendor/noaa).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level490WhaleSong {
    private static final Random RANDOM = new Random();
    private static long nextMs;

    private Level490WhaleSong() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.player == null || mc.isPaused() || !Level490Atmosphere.inLevel490()) {
            nextMs = now + 20_000;
            return;
        }
        if (now < nextMs) {
            return;
        }
        nextMs = now + 40_000 + RANDOM.nextInt(100_000);
        double angle = RANDOM.nextDouble() * Math.PI * 2;
        call(mc, angle, 0);
        if (RANDOM.nextFloat() < 0.35f) {
            // Another answers, from somewhere else.
            call(mc, angle + Math.PI * (0.4 + RANDOM.nextDouble() * 1.2), 5_000 + RANDOM.nextInt(7_000));
        }
    }

    private static void call(Minecraft mc, double angle, long laterMs) {
        boolean far = RANDOM.nextFloat() < 0.3f;
        double d = far ? 3000 + RANDOM.nextDouble() * 2000 : 1200 + RANDOM.nextDouble() * 2000;
        double x = mc.player.getX() + Math.cos(angle) * d;
        double z = mc.player.getZ() + Math.sin(angle) * d;
        float volume = (float) Math.max(0.35, Math.min(0.9, 1.2 - d / 5000.0));
        Level490ChainEvents.hear((far ? ModSounds.LEVEL490_WHALE_FAR : ModSounds.LEVEL490_WHALE_CALL).get(), x, z, volume,
                0.9f + RANDOM.nextFloat() * 0.15f, laterMs);
    }
}
