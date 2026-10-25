package com.rickr.backrooms.client.light;

import com.rickr.backrooms.registry.ModSounds;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The soundscape of the lights: the nearest few lit fixtures each buzz on their own (positional,
 * so the hum moves around you as you walk), and a flickering tube clicks when it toggles.
 */
final class FixtureSounds {
    /** How many fixtures hum at once (the nearest lit ones). */
    private static final int HUM_COUNT = 8;
    /** Hum fades out linearly over this many blocks. */
    private static final double HUM_RANGE = 16.0;
    /** A lamp is "lit" for the hum above this power. */
    private static final float LIT = 0.05f;
    /** After the lamp has been dark this many ticks the hum is stopped (not just muted). */
    private static final int DARK_TICKS_BEFORE_STOP = 10;
    /** Max blink clicks per tick, so a big flicker doesn't machine-gun. */
    private static final int BLINKS_PER_TICK = 2;

    private static final Long2ObjectOpenHashMap<FixtureHumSound> HUMS = new Long2ObjectOpenHashMap<>();
    /** Lamp key -> tick its hum may be retried (after the sound engine dropped it). */
    private static final Long2LongOpenHashMap RETRY_AT = new Long2LongOpenHashMap();
    private static final Long2LongOpenHashMap DARK_SINCE = new Long2LongOpenHashMap();
    private static final LongOpenHashSet WANTED = new LongOpenHashSet();
    private static final RandomSource RANDOM = RandomSource.create();
    private static long tickCount;

    /**
     * @param byDistance all lights, already sorted nearest first (with {@link FixtureLight#distSq} set)
     */
    static void tick(List<FixtureLight> byDistance) {
        tickCount++;
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        WANTED.clear();
        double maxSq = HUM_RANGE * HUM_RANGE;
        for (FixtureLight light : byDistance) {
            if (light.distSq > maxSq || WANTED.size() >= HUM_COUNT) {
                break;
            }
            if (!light.type.hums) {
                continue;
            }
            if (light.level > LIT) {
                DARK_SINCE.remove(light.key);
            } else if (!DARK_SINCE.containsKey(light.key)) {
                DARK_SINCE.put(light.key, tickCount);
            }
            boolean darkTooLong = light.level <= LIT
                    && tickCount - DARK_SINCE.get(light.key) > DARK_TICKS_BEFORE_STOP;
            if (darkTooLong) {
                continue;
            }
            WANTED.add(light.key);
            FixtureHumSound hum = HUMS.get(light.key);
            if (hum != null && hum.released()) {
                // It was fading out; let it finish and start a fresh loop.
                HUMS.remove(light.key);
                hum = null;
            }
            if (hum != null && (hum.isStopped() || (!sounds.isActive(hum) && hum.target > 0.0f))) {
                // The sound engine dropped it (too many sounds playing?): try again a bit later.
                HUMS.remove(light.key);
                RETRY_AT.put(light.key, tickCount + 60);
                hum = null;
            }
            if (hum == null) {
                if (RETRY_AT.get(light.key) > tickCount) {
                    continue;
                }
                hum = new FixtureHumSound(light, light.level);
                HUMS.put(light.key, hum);
                sounds.play(hum);
            }
            hum.target = Math.min(1.0f, light.level);
        }
        // Release hums that are no longer among the nearest lit lamps.
        var it = HUMS.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var entry = it.next();
            FixtureHumSound hum = entry.getValue();
            if (!WANTED.contains(entry.getLongKey())) {
                hum.release();
            }
            if (hum.isStopped()) {
                it.remove();
            }
        }
        if (RETRY_AT.size() > 256) {
            RETRY_AT.clear();
        }
        if (DARK_SINCE.size() > 256) {
            DARK_SINCE.clear();
        }
    }

    private static int blinksThisTick;
    private static long blinkTick = -1;

    /** Called when a flickering lamp switches; clicks if it's close enough to hear. */
    static void blink(FixtureLight light) {
        if (blinkTick != tickCount) {
            blinkTick = tickCount;
            blinksThisTick = 0;
        }
        if (!light.type.hums || light.distSq > HUM_RANGE * HUM_RANGE || blinksThisTick >= BLINKS_PER_TICK) {
            return;
        }
        blinksThisTick++;
        float pitch = 0.9f + RANDOM.nextFloat() * 0.2f;
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(ModSounds.LIGHT_BLINK.get(), SoundSource.BLOCKS,
                0.55f, pitch, RANDOM, light.soundX, light.soundY, light.soundZ));
    }

    /** Silences every hum at once (dimension change, logout). */
    static void stopAll() {
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        for (FixtureHumSound hum : HUMS.values()) {
            sounds.stop(hum);
        }
        HUMS.clear();
        RETRY_AT.clear();
        DARK_SINCE.clear();
    }

    private FixtureSounds() {}
}
