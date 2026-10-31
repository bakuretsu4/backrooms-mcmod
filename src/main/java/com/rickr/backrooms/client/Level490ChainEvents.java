package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.Level490StrikePayload;
import com.rickr.backrooms.registry.ModSounds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * What Level 490's colossal chains do (client side):
 * <ul>
 *   <li><b>Jolts:</b> now and then (the server picks when and which, world/Level490Storm) a far
 *       chain is snatched down as if something pulled it from below, then bounces back and settles
 *       ({@link #pull}, used by Level490SkyChains), and its clank reaches you later, rolling like
 *       thunder.</li>
 *   <li><b>Creaks:</b> near a chain you hear it groan under its load now and then, from the chain
 *       itself.</li>
 *   <li>{@link #hear}: plays a far sound as late as sound takes to come that far (343 blocks a
 *       second), from its direction; also used for the hung ruins falling (Level490SkyHung).</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level490ChainEvents {
    private static final double SOUND_SPEED = 343.0;
    /** How far a jolt snatches a chain down (blocks), and how long until it has settled. */
    private static final float JOLT_DEPTH = 18.0f;
    private static final double JOLT_SECONDS = 7.0;
    /** Creaks are heard within this far of a chain, more often the nearer you are. */
    private static final double CREAK_REACH = 110.0;

    private record Jolt(int x, int z, long startMs) {}

    private record Far(long atMs, SoundEvent sound, float volume, float pitch, double dirX, double dirZ) {}

    private static final List<Jolt> JOLTS = new ArrayList<>();
    private static final List<Far> SOUNDS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static long nextCreakMs;

    private Level490ChainEvents() {}

    /** Network (main thread): the server jerked the chain at the payload's (x, z). */
    static void jolt(Level490StrikePayload payload) {
        JOLTS.add(new Jolt(payload.x(), payload.z(), System.currentTimeMillis()));
        Minecraft mc = Minecraft.getInstance();
        double d = Math.hypot(payload.x() - mc.player.getX(), payload.z() - mc.player.getZ());
        float volume = (float) Math.max(0.3, Math.min(1.0, 1.3 - d / 2400.0));
        hear(ModSounds.LEVEL490_CHAIN_JOLT.get(), payload.x(), payload.z(), volume, 0.85f + new Random(payload.seed()).nextFloat() * 0.2f, 0);
    }

    /**
     * Plays {@code sound} from (x, z) as late as it takes sound to come from there, plus
     * {@code extraMs} (for something that happens later, like a fall reaching the clouds).
     */
    static void hear(SoundEvent sound, double x, double z, float volume, float pitch, long extraMs) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        double dx = x - mc.player.getX();
        double dz = z - mc.player.getZ();
        double d = Math.max(1.0, Math.hypot(dx, dz));
        long at = System.currentTimeMillis() + extraMs + (long) (d / SOUND_SPEED * 1000.0);
        SOUNDS.add(new Far(at, sound, volume, pitch, dx / d, dz / d));
    }

    /**
     * How far the chain hanging at (x, z) is pulled down right now (blocks, 0 when still): snatched
     * down hard, then bouncing back and settling.
     */
    static float pull(double x, double z) {
        if (JOLTS.isEmpty()) {
            return 0.0f;
        }
        long now = System.currentTimeMillis();
        for (Jolt j : JOLTS) {
            if (j.x == (int) x && j.z == (int) z) {
                double t = (now - j.startMs) / 1000.0;
                if (t < 0.25) {
                    return (float) (-JOLT_DEPTH * t / 0.25);
                }
                double s = t - 0.25;
                return (float) (-JOLT_DEPTH * Math.exp(-s * 0.6) * Math.cos(s * 2.6));
            }
        }
        return 0.0f;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        JOLTS.removeIf(j -> now - j.startMs > JOLT_SECONDS * 1000);
        if (mc.player == null || !Level490Atmosphere.inLevel490()) {
            SOUNDS.clear();
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        Iterator<Far> it = SOUNDS.iterator();
        while (it.hasNext()) {
            Far f = it.next();
            if (now >= f.atMs) {
                it.remove();
                // From its direction: the sound sits a few blocks off that way.
                mc.getSoundManager().play(new SimpleSoundInstance(f.sound, SoundSource.AMBIENT, f.volume, f.pitch,
                        RandomSource.create(), eye.x + f.dirX * 10, eye.y + 3, eye.z + f.dirZ * 10));
            }
        }
        if (mc.isPaused() || now < nextCreakMs || !Level490ClientState.hasSeed()) {
            return;
        }
        int[] nearest = null;
        double best = CREAK_REACH;
        for (int[] c : Level490ClientState.chains(eye.x, eye.z)) {
            double d = Math.hypot(c[0] - eye.x, c[1] - eye.z);
            if (d < best) {
                best = d;
                nearest = c;
            }
        }
        if (nearest == null) {
            nextCreakMs = now + 2000;
            return;
        }
        // From the chain itself, somewhere above or below you; nearer chains groan more often.
        mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.LEVEL490_CHAIN_CREAK.get(), SoundSource.AMBIENT,
                1.0f, 0.8f + RANDOM.nextFloat() * 0.3f, RandomSource.create(),
                nearest[0], eye.y - 10 + RANDOM.nextDouble() * 35, nearest[1]));
        nextCreakMs = now + (long) (4000 + RANDOM.nextDouble() * (6000 + 120 * best));
    }
}
