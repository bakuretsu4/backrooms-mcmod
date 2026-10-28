package com.rickr.backrooms.client.light;

import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
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
 *
 * <p>Level 0's tubes are treated with extra care, since their buzz is most of what you hear
 * there:
 * <ul>
 *   <li>every tube has its own buzz, pitch and loudness (see {@link FixtureHumSound}); faulty
 *       ones (stuttering, restarting or dying, see BackroomsLights#tubeFault) buzz rougher;</li>
 *   <li>a tube's buzz fades away faster with distance than the plain straight-line fade, so you
 *       hear the tube you stand under clearly, the next ones less, and a stretch with no lit tube
 *       goes almost quiet: pockets of silence between the lit areas;</li>
 *   <li>a tube dropping out ticks (its starter), striking again clicks and catches, and a dying
 *       tube's starter ticks now and then as it keeps trying.</li>
 * </ul>
 */
final class FixtureSounds {
    /** How many fixtures hum at once (the nearest lit ones). */
    private static final int HUM_COUNT = 8;
    /** Up to this many hums play at full volume; with more, each is turned down (see tick). */
    private static final int HUMS_AT_FULL = 3;
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
    /** Level 0 tubes: a tube's buzz carries this far (matches "attenuation_distance": 14) ... */
    private static final double L0_HUM_RANGE = 14.0;
    /** ... and only this many of the nearest are heard at once. */
    private static final int L0_HUM_COUNT = 6;
    /**
     * Level 0: how much buzz (the sum of each hum's volume squared, i.e. its power) may play before
     * all of them are turned down together. 2.0 is about two tubes right above you.
     */
    private static final float L0_FULL_LOAD = 2.0f;
    /** A dying Level 0 tube this close (blocks) ... */
    private static final double DYING_TICK_RANGE = 10.0;
    /** ... has its starter tick on average once in this many client ticks (about every 7 s). */
    private static final int DYING_TICK_ODDS = 140;

    private static final Long2ObjectOpenHashMap<FixtureHumSound> HUMS = new Long2ObjectOpenHashMap<>();
    /** Lamp key -> tick its hum may be retried (after the sound engine dropped it). */
    private static final Long2LongOpenHashMap RETRY_AT = new Long2LongOpenHashMap();
    private static final Long2LongOpenHashMap DARK_SINCE = new Long2LongOpenHashMap();
    /** Streetlamp key -> when it last clicked (ms). */
    private static final Long2LongOpenHashMap LAST_CLICK = new Long2LongOpenHashMap();
    private static final LongOpenHashSet WANTED = new LongOpenHashSet();
    private static final RandomSource RANDOM = RandomSource.create();
    private static long tickCount;
    /** Whether we are on Level 0 (set every tick; read by blink, which runs just before tick). */
    private static boolean level0;

    /**
     * @param byDistance all lights, already sorted nearest first (with {@link FixtureLight#distSq} set)
     */
    static void tick(List<FixtureLight> byDistance) {
        tickCount++;
        Minecraft mc = Minecraft.getInstance();
        SoundManager sounds = mc.getSoundManager();
        level0 = mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_0;
        WANTED.clear();
        double maxSq = HUM_RANGE * HUM_RANGE;
        int count = level0 ? L0_HUM_COUNT : HUM_COUNT;
        for (FixtureLight light : byDistance) {
            if (light.distSq > maxSq || WANTED.size() >= count) {
                break;
            }
            if (light.type.hum == FixtureType.Hum.NONE) {
                continue;
            }
            boolean tube = level0 && isLevel0Tube(light.type);
            double range = tube ? L0_HUM_RANGE : humRange(light.type);
            if (light.distSq > range * range) {
                continue; // a short-range hum further away than its reach
            }
            int fault = tube ? BackroomsLights.tubeFault(light.key) : FixturePower.TUBE_OK;
            // A dying tube only glows dimly, but its ballast is working as hard as ever: it buzzes
            // with the mains power, not with its weak glow.
            float power = fault == FixturePower.TUBE_DYING ? FixturePower.levelOf(light) : light.level;
            if (power > LIT) {
                DARK_SINCE.remove(light.key);
            } else if (!DARK_SINCE.containsKey(light.key)) {
                DARK_SINCE.put(light.key, tickCount);
            }
            boolean darkTooLong = power <= LIT
                    && tickCount - DARK_SINCE.get(light.key) > DARK_TICKS_BEFORE_STOP;
            if (darkTooLong) {
                continue;
            }
            float want = Math.min(1.0f, power);
            if (light.type.hum == FixtureType.Hum.SODIUM) {
                // Level 9: the streetlamps' buzz cuts out while the Mangled's fog is thick.
                want *= com.rickr.backrooms.client.Level9Atmosphere.lampHumScale(System.currentTimeMillis());
            }
            if (light.type.hum == FixtureType.Hum.FLUORESCENT) {
                // Level 0: the tube hum fades away as you come near the Manila Room.
                want *= com.rickr.backrooms.client.anomaly.level0.Level0AnomalyEffects.fixtureHumScale();
            }
            if (tube) {
                // On top of Minecraft's straight-line fade, fade once more with distance: together
                // the buzz falls off with the square of the distance (about half at 4 blocks, a
                // tenth at 10), so the space between lit areas goes nearly quiet.
                want *= 1.0f - (float) (Math.sqrt(light.distSq) / L0_HUM_RANGE);
                if (fault == FixturePower.TUBE_DYING && power > LIT && light.distSq < DYING_TICK_RANGE * DYING_TICK_RANGE
                        && RANDOM.nextInt(DYING_TICK_ODDS) == 0) {
                    // Its starter keeps trying to strike it properly.
                    starterTick(light, 0.5f);
                }
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
                hum = new FixtureHumSound(light, want, tube, fault != FixturePower.TUBE_OK);
                HUMS.put(light.key, hum);
                sounds.play(hum);
            }
            hum.target = want;
        }
        // Many lamps close together (a lit office, a bright Level 0 hall) would stack up to a loud
        // drone that buries everything else. Like a mixing desk, turn each one down as more play,
        // so the hum's overall loudness stays about the same however dense the lights are. On
        // Level 0 this goes by how much buzz there really is (the far tubes are already quiet):
        // their summed power is held at about that of two tubes right above you.
        float density;
        if (level0) {
            float load = 0.0f;
            var keys = WANTED.iterator();
            while (keys.hasNext()) {
                FixtureHumSound hum = HUMS.get(keys.nextLong());
                if (hum != null) {
                    load += hum.target * hum.target;
                }
            }
            density = load > L0_FULL_LOAD ? (float) Math.sqrt(L0_FULL_LOAD / load) : 1.0f;
        } else {
            density = WANTED.size() > HUMS_AT_FULL ? (float) Math.sqrt((double) HUMS_AT_FULL / WANTED.size()) : 1.0f;
        }
        if (density < 1.0f) {
            var keys = WANTED.iterator();
            while (keys.hasNext()) {
                FixtureHumSound hum = HUMS.get(keys.nextLong());
                if (hum != null) {
                    hum.target *= density;
                }
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

    /** A Level 0 ceiling tube (a single troffer or a corridor strip), whose buzz and clicks are its own. */
    private static boolean isLevel0Tube(FixtureType type) {
        return type == FixtureType.FLUORESCENT_LIGHT || type == FixtureType.L0_TROFFER_STRIP;
    }

    /** A tube's glow starter ticking: a tiny dry click at the fixture. */
    private static void starterTick(FixtureLight light, float volume) {
        float pitch = 0.9f + RANDOM.nextFloat() * 0.25f;
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(ModSounds.L0_TUBE_TICK.get(), SoundSource.BLOCKS,
                volume, pitch, RANDOM, light.soundX, light.soundY, light.soundZ));
    }

    private static int blinksThisTick;
    private static long blinkTick = -1;

    /**
     * Called when a flickering lamp switches; clicks if it's close enough to hear. Tubes click on
     * every toggle; a streetlamp ticks only when it strikes back on, and not too often. A Level 0
     * tube dropping out only ticks (its starter), and clicks and catches when it strikes again.
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
        if (level0 && isLevel0Tube(light.type)) {
            if (!nowOn) {
                starterTick(light, 0.7f);
                return;
            }
            // The real click-and-buzz of a tube catching, a touch softer than on other levels:
            // Level 0 is quiet, and a stuttering tube strikes many times in a row.
            float pitch = 0.9f + RANDOM.nextFloat() * 0.2f;
            sounds.play(new SimpleSoundInstance(ModSounds.LIGHT_BLINK.get(), SoundSource.BLOCKS,
                    0.42f, pitch, RANDOM, light.soundX, light.soundY, light.soundZ));
            return;
        }
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
