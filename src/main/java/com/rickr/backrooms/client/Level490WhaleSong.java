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
 * Level 490's whales singing: every minute or so one of the whales round you calls (client/
 * Level490Whales: a pod's or the Great Whale's deeper voice), and sometimes another answers a few
 * seconds later; with none near, a call comes from far off in the storm. Each comes as late as sound
 * takes to cross that far (Level490ChainEvents#hear). The recordings are NOAA's (tools/vendor/noaa).
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
        nextMs = now + 30_000 + RANDOM.nextInt(70_000);
        java.util.List<Level490Whales.Whale> whales = Level490ClientState.hasSeed()
                ? Level490Whales.whales(Level490ClientState.seed(), mc.level.getGameTime(), mc.player.getEyePosition())
                : java.util.List.of();
        if (whales.isEmpty()) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            call(mc, angle, 0);
            if (RANDOM.nextFloat() < 0.35f) {
                // Another answers, from somewhere else.
                call(mc, angle + Math.PI * (0.4 + RANDOM.nextDouble() * 1.2), 5_000 + RANDOM.nextInt(7_000));
            }
            return;
        }
        Level490Whales.Whale first = whales.get(RANDOM.nextInt(whales.size()));
        sing(first, 0);
        if (whales.size() > 1 && RANDOM.nextFloat() < 0.5f) {
            Level490Whales.Whale other = whales.get(RANDOM.nextInt(whales.size()));
            if (other != first) {
                sing(other, 4_000 + RANDOM.nextInt(6_000));
            }
        }
    }

    /** A whale in sight sings: the Great Whale low and slow, the others as recorded. */
    private static void sing(Level490Whales.Whale w, long laterMs) {
        Minecraft mc = Minecraft.getInstance();
        double d = Math.hypot(w.x() - mc.player.getX(), w.z() - mc.player.getZ());
        float volume = (float) Math.max(0.4, Math.min(1.0, 1.25 - d / 4000.0));
        Level490ChainEvents.hear((w.great() ? ModSounds.LEVEL490_WHALE_FAR : ModSounds.LEVEL490_WHALE_CALL).get(), w.x(), w.z(), volume,
                w.great() ? 0.7f + RANDOM.nextFloat() * 0.1f : 0.9f + RANDOM.nextFloat() * 0.15f, laterMs);
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
