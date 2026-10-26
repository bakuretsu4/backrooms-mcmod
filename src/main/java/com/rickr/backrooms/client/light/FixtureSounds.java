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
 * so the hum moves around you as you walk), and a flickering tube clicks when it toggles. Level 9
 * streetlamps use their own sounds: a quieter, shorter-range sodium hum and an electrical tick
 * when a stuttering lamp strikes again.
 */
final class FixtureSounds {
    /** How many fixtures hum at once (the nearest lit ones). */
    private static final int HUM_COUNT = 8;
    /** Fluorescent hum fades out linearly over this many blocks. */
    private static final double HUM_RANGE = 16.0;
    /**
     * Sodium hum range: a lamp is only heard when you're nearly under it, so the street stays
     * silent between lamps (matches "attenuation_distance": 12 in sounds.json).
     */
    private static final double SODIUM_HUM_RANGE = 12.0;
    /** A streetlamp re-striking clicks at most this often (ms), so a stutter doesn't machine-gun. */
    private static final long STREETLAMP_CLICK_GAP_MS = 220L;
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
    /** Streetlamp key -> when it last clicked (ms). */
    private static final Long2LongOpenHashMap LAST_CLICK = new Long2LongOpenHashMap();
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
            if (light.type.hum == FixtureType.Hum.NONE) {
                continue;
            }
            double range = humRange(light.type);
            if (light.distSq > range * range) {
                continue; // a short-range hum further away than its reach
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
            if (light.type.hum == FixtureType.Hum.SODIUM) {
                // Level 9: the streetlamps' buzz cuts out while the Mangled's fog is thick.
                hum.target *= com.rickr.backrooms.client.Level9Atmosphere.lampHumScale(System.currentTimeMillis());
            }
            if (light.type.hum == FixtureType.Hum.FLUORESCENT) {
                // Level 0: the tube hum fades away as you come near the Manila Room.
                hum.target *= com.rickr.backrooms.client.anomaly.level0.Level0AnomalyEffects.fixtureHumScale();
            }
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
        if (LAST_CLICK.size() > 256) {
            LAST_CLICK.clear();
        }
    }

    /** How far a fixture's hum carries (blocks). */
    static double humRange(FixtureType type) {
        return type.hum == FixtureType.Hum.SODIUM ? SODIUM_HUM_RANGE : HUM_RANGE;
    }

    private static int blinksThisTick;
    private static long blinkTick = -1;

    /**
     * Called when a flickering lamp switches; clicks if it's close enough to hear. Tubes click on
     * every toggle; a streetlamp ticks only when it strikes back on, and not too often.
     *
     * @param nowOn whether the lamp just turned on (true) or off (false)
     */
    static void blink(FixtureLight light, boolean nowOn) {
        if (blinkTick != tickCount) {
            blinkTick = tickCount;
            blinksThisTick = 0;
        }
        if (light.distSq > HUM_RANGE * HUM_RANGE || blinksThisTick >= BLINKS_PER_TICK) {
            return;
        }
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        if (light.type.isStreetlamp()) {
            long now = System.currentTimeMillis();
            if (!nowOn || now - LAST_CLICK.get(light.key) < STREETLAMP_CLICK_GAP_MS) {
                return;
            }
            LAST_CLICK.put(light.key, now);
            blinksThisTick++;
            float pitch = 0.85f + RANDOM.nextFloat() * 0.3f;
            sounds.play(new SimpleSoundInstance(ModSounds.LEVEL9_LAMP_FLICKER.get(), SoundSource.BLOCKS,
                    0.45f, pitch, RANDOM, light.soundX, light.soundY, light.soundZ));
            return;
        }
        if (light.type.hum != FixtureType.Hum.FLUORESCENT) {
            return; // silent fixtures (exit signs, porch lights...) don't click
        }
        blinksThisTick++;
        float pitch = 0.9f + RANDOM.nextFloat() * 0.2f;
        sounds.play(new SimpleSoundInstance(ModSounds.LIGHT_BLINK.get(), SoundSource.BLOCKS,
                0.55f, pitch, RANDOM, light.soundX, light.soundY, light.soundZ));
    }

    /**
     * Level 2: a series circuit just failed. One sharp electrical pop and fizz at (the nearest lamp
     * of) the circuit; it carries a little further than the hum (sounds.json: 24 blocks).
     */
    static void circuitPop(FixtureLight light) {
        float pitch = 0.85f + RANDOM.nextFloat() * 0.3f;
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(ModSounds.L2_CIRCUIT_POP.get(), SoundSource.BLOCKS,
                0.9f, pitch, RANDOM, light.soundX, light.soundY, light.soundZ));
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
        LAST_CLICK.clear();
    }

    private FixtureSounds() {}
}
