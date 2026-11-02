package com.rickr.backrooms.client.level9;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Level 9: the dogs go quiet. Somewhere down the streets 2-4 dogs start barking, overlapping,
 * each from its own direction 22-48 blocks away (their starts staggered by up to a second).
 * Then, 3.5-6.5 s in, every one of them stops on the same tick, in the middle of a bark, and the
 * night goes silent: for {@link #HUSH_MIN_TICKS}-{@link #HUSH_MAX_TICKS} (3-5.5 s) the crickets
 * duck almost to nothing ({@link Level9Soundscape}) and no far-off sound plays
 * (audio/DistantAmbience holds its timer). Then the night slowly comes back.
 *
 * <p>When: Level 9's DistantAmbience schedule (a sound every 24-50 s) picks it like one of its
 * sounds, about one pick in eight, but never within {@link #COOLDOWN_TICKS} (3 min) of the last
 * one: so roughly every 5 minutes. {@code /backrooms event dogs} starts it at once.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class DogsGoQuiet {
    private static final int MAX_DOGS = 4;
    private static final int CUT_MIN_TICKS = 70;
    private static final int CUT_MAX_TICKS = 130;
    private static final int STAGGER_TICKS = 20;
    static final int HUSH_MIN_TICKS = 60;
    static final int HUSH_MAX_TICKS = 110;
    private static final int COOLDOWN_TICKS = 3600;
    private static final double MIN_DISTANCE = 22.0;
    private static final double MAX_DISTANCE = 48.0;
    /** How loud the crickets stay during the silence (0..1). */
    private static final float HUSH_LEVEL = 0.06f;

    private static final RandomSource RANDOM = RandomSource.create();
    private static final SoundInstance[] DOGS = new SoundInstance[MAX_DOGS];
    private static int dogCount;
    /** Ticks until the barking is cut (while barking), or until the silence ends (while hushed). */
    private static int barkTicks = -1;
    private static int hushTicks = -1;
    private static int cooldown = COOLDOWN_TICKS / 2;

    private DogsGoQuiet() {}

    /** May DistantAmbience start the chorus now? (On Level 9, nothing running, cooled down.) */
    public static boolean ready() {
        return barkTicks < 0 && hushTicks < 0 && cooldown <= 0;
    }

    /** True from the first bark until the silence after them is over. */
    public static boolean active() {
        return barkTicks >= 0 || hushTicks >= 0;
    }

    /** True during the silence after the dogs stop (DistantAmbience plays nothing). */
    public static boolean hushed() {
        return hushTicks >= 0;
    }

    /** How loud the night bed may be right now (0..1): almost nothing in the silence. */
    public static float ambienceLevel() {
        return hushed() ? HUSH_LEVEL : 1.0f;
    }

    /** Starts the chorus (Level 9 only; ignored while one is already going). */
    public static void start() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || active()
                || BackroomsLevel.fromDimension(mc.level.dimension()) != BackroomsLevel.LEVEL_9) {
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        dogCount = 2 + RANDOM.nextInt(MAX_DOGS - 1);
        float base = RANDOM.nextFloat() * Mth.TWO_PI;
        for (int i = 0; i < dogCount; i++) {
            // Spread round you, but not evenly: more like a few yards along the same streets.
            float angle = base + i * (Mth.TWO_PI / dogCount) + (RANDOM.nextFloat() - 0.5f) * 1.2f;
            double distance = Mth.nextDouble(RANDOM, MIN_DISTANCE, MAX_DISTANCE);
            int delay = i == 0 ? 0 : RANDOM.nextInt(STAGGER_TICKS);
            DOGS[i] = new SimpleSoundInstance(ModSounds.L9_DOG_CHORUS.get().getLocation(), SoundSource.AMBIENT,
                    1.1f + RANDOM.nextFloat() * 0.3f, 0.88f + RANDOM.nextFloat() * 0.24f, RANDOM, false, delay,
                    SoundInstance.Attenuation.LINEAR,
                    eye.x + Mth.cos(angle) * distance, eye.y + RANDOM.nextFloat() * 2.0, eye.z + Mth.sin(angle) * distance, false);
            mc.getSoundManager().play(DOGS[i]);
        }
        barkTicks = Mth.nextInt(RANDOM, CUT_MIN_TICKS, CUT_MAX_TICKS);
        hushTicks = -1;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean here = mc.level != null && mc.player != null
                && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_9;
        if (!here) {
            if (active()) {
                stopDogs(mc);
                barkTicks = -1;
                hushTicks = -1;
            }
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (barkTicks >= 0 && --barkTicks < 0) {
            // All at once, mid-bark.
            stopDogs(mc);
            hushTicks = Mth.nextInt(RANDOM, HUSH_MIN_TICKS, HUSH_MAX_TICKS);
        } else if (hushTicks >= 0 && --hushTicks < 0) {
            cooldown = COOLDOWN_TICKS;
        }
    }

    private static void stopDogs(Minecraft mc) {
        for (int i = 0; i < dogCount; i++) {
            @Nullable SoundInstance dog = DOGS[i];
            if (dog != null) {
                mc.getSoundManager().stop(dog);
                DOGS[i] = null;
            }
        }
        dogCount = 0;
    }
}
