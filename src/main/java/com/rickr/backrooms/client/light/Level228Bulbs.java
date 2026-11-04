package com.rickr.backrooms.client.light;

import com.rickr.backrooms.block.Level228BulbBlock;
import com.rickr.backrooms.client.Level228ClientState;
import com.rickr.backrooms.world.layout.Level228Plan;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Level 228's light sources (backrooms:l228_bulb, placed by the layout at every live or failing
 * bulb): which fixture type each is, how bright it burns right now, and the light around you.
 *
 * <ul>
 *   <li><b>Glow</b> ({@link #glow}): a pure function of the layout seed, the bulb's kind, state
 *       and PHASE, and the world's game time, so every client agrees (game time is synced by the
 *       server) and a bulb and all its mirror images (the layout gives them one PHASE) burn and
 *       fail together. The same number drives the Veil light (BackroomsLights) and the glowing
 *       glass of the bulb prop (client/level228's prop renderer), so lens and light never
 *       disagree. A live bulb burns steadily at its own age (0.80-1.0, old bulbs are dimmer); the
 *       whole building's supply sags a percent or two now and then (every bulb together). A
 *       failing bulb lives in episodes of 3-8 s: mostly it holds, sometimes its contact chatters
 *       for a moment, sometimes it drops out for a second or two and re-strikes with a few
 *       flashes, sometimes it browns down and wavers. A filament can't switch instantly: it heats
 *       in ~25 ms and cools in ~60 ms, which is what makes it read as a bulb and not an LED.</li>
 *   <li><b>The light around you</b> ({@link #lit}, {@link #colourR} ..., {@link #outShare}):
 *       from the fixture list, which BackroomsLights keeps with and without Veil, so it also works
 *       under an Iris shader pack. Level228Atmosphere uses it for the fog's warmth and for the one
 *       flicker a shader pack can show: its block light is static, so when the bulb lighting your
 *       spot drops out the camcorder's exposure dips with it.</li>
 * </ul>
 */
public final class Level228Bulbs {
    /** Lights nearer than this count for the light around you (blocks). */
    private static final double AROUND = 14.0;
    /** How much light (sum of brightness / (1 + distance squared)) counts as "fully lit". */
    private static final double FULLY_LIT = 0.16;
    /** Easing per tick of the light around you (the fog and grade follow it, about 1 s). */
    private static final float EASE = 0.10f;
    /** Faster easing of the "how much of your light is out" share: it must follow the flicker. */
    private static final float OUT_EASE = 0.55f;

    private static float lit;
    private static float colR = 1.0f;
    private static float colG = 0.70f;
    private static float colB = 0.43f;
    private static float outShare;

    private Level228Bulbs() {}

    /** True for the five Level 228 light types. */
    public static boolean isBulb(FixtureType type) {
        return type == FixtureType.L228_BULB || type == FixtureType.L228_CANDLE || type == FixtureType.L228_MARQUEE
                || type == FixtureType.L228_SIGN || type == FixtureType.L228_COLD;
    }

    /** The fixture type for a {@code backrooms:l228_bulb} state, by its {@code kind}. */
    static FixtureType typeOf(BlockState state) {
        if (!state.hasProperty(Level228BulbBlock.KIND)) {
            return FixtureType.L228_BULB;
        }
        return switch (state.getValue(Level228BulbBlock.KIND)) {
            case Level228Plan.LIGHT_CANDLE -> FixtureType.L228_CANDLE;
            case Level228Plan.LIGHT_MARQUEE -> FixtureType.L228_MARQUEE;
            case Level228Plan.LIGHT_SIGN -> FixtureType.L228_SIGN;
            case Level228Plan.LIGHT_COLD -> FixtureType.L228_COLD;
            default -> FixtureType.L228_BULB;
        };
    }

    /** The layout's LIGHT_* kind of a Level 228 fixture type. */
    static int kindOf(FixtureType type) {
        return switch (type) {
            case L228_CANDLE -> Level228Plan.LIGHT_CANDLE;
            case L228_MARQUEE -> Level228Plan.LIGHT_MARQUEE;
            case L228_SIGN -> Level228Plan.LIGHT_SIGN;
            case L228_COLD -> Level228Plan.LIGHT_COLD;
            default -> Level228Plan.LIGHT_BULB;
        };
    }

    /** FixtureLight#bulb for a light block: state in bits 4+, phase in bits 0-3 (lit, phase 0 without the properties). */
    static int packed(BlockState state) {
        if (state == null || !state.hasProperty(Level228BulbBlock.STATE)) {
            return 0;
        }
        return state.getValue(Level228BulbBlock.STATE) << 4 | state.getValue(Level228BulbBlock.PHASE);
    }

    /**
     * {@link #glow} of one light for this tick (BackroomsLights: the Veil light's level). Sampled
     * half way through the tick: the Veil light holds it for a whole tick, so this sits midway
     * through the filament's easing that the bulb's glass shows frame by frame.
     */
    static float glowOf(FixtureLight light, long gameTime) {
        return glow(Level228ClientState.seed(), kindOf(light.type), light.bulb >> 4, light.bulb & 15, gameTime, 0.5f);
    }

    /** True for a failing bulb (its glow toggles: BackroomsLights plays its chatter on the toggles). */
    static boolean failing(FixtureLight light) {
        return light.bulb >> 4 == Level228Plan.LIGHT_FAILING;
    }

    // ==========================================================================================
    // The glow
    // ==========================================================================================

    /** One slot of the failing pattern is one game tick (50 ms). */
    private static final double HEAT_SLOTS = 0.5;  // ~25 ms to heat up
    private static final double COOL_SLOTS = 1.2;  // ~60 ms to cool down

    /**
     * How bright a Level 228 light burns right now, 0 (dark) .. 1. Pure: the same for every
     * client and every frame of the same game time. Allocation-free; cheap enough to call for
     * every bulb prop every frame.
     *
     * @param kind  Level228Plan.LIGHT_BULB .. LIGHT_COLD
     * @param state Level228Plan.LIGHT_LIT, LIGHT_FAILING or LIGHT_DEAD (0)
     * @param phase 0..15, the bulb's own pattern and age
     */
    public static float glow(long layoutSeed, int kind, int state, int phase, long gameTime, float partialTick) {
        if (state == Level228Plan.LIGHT_DEAD) {
            return 0.0f;
        }
        long h = FixturePower.mix(layoutSeed ^ 0x228B0B5L * (phase + 1) ^ (long) kind << 40);
        // Old bulbs burn dimmer and a touch browner; signs and the hallway tubes are steady.
        float age = kind == Level228Plan.LIGHT_SIGN ? 1.0f : 0.80f + 0.20f * ((h >>> 8) & 0xFF) / 255.0f;
        float level = age * supply(layoutSeed, gameTime, partialTick);
        if (state != Level228Plan.LIGHT_FAILING) {
            return level;
        }
        double now = raw(h, gameTime);
        double before = raw(h, gameTime - 1);
        // The filament easing toward this slot's level since the slot began (heats fast, cools slow).
        double tau = now > before ? HEAT_SLOTS : COOL_SLOTS;
        double g = now + (before - now) * Math.exp(-Math.max(0.0, Math.min(1.0, partialTick)) / tau);
        return (float) (level * g);
    }

    /**
     * The building's supply: everything on one old circuit, so every bulb sags together by a
     * percent or two, slowly (a few long, smooth swells a minute).
     */
    private static float supply(long seed, long gameTime, float partialTick) {
        double t = (gameTime + partialTick) / 20.0;
        double p = ((seed >>> 13) & 0xFFFF) / 65536.0 * Math.PI * 2.0;
        return (float) (1.0 - 0.012 * (1.0 + Math.sin(t * 0.31 + p)) * (0.5 + 0.5 * Math.sin(t * 0.073 + 2.0 * p)));
    }

    /** The failing bulb's level (0..1) during one game tick, before the filament's easing. */
    private static double raw(long h, long tick) {
        long episode = 60L + (h & 0x7FFF) % 101L;               // 3-8 s
        long t = tick + ((h >>> 20) & 0xFFFFF);
        long index = Math.floorDiv(t, episode);
        long in = Math.floorMod(t, episode);
        long e = FixturePower.mix(h ^ index * 0x9E3779B97F4A7C15L);
        int kind = (int) ((e & 0xFF) % 100);
        if (kind < 45) {
            return 1.0;                                       // holds
        }
        long start = ((e >>> 8) & 0xFFFF) % Math.max(1L, episode - 30L);
        if (kind < 70) {
            // Chatter: 6-20 ticks of the contact making and breaking, with half-power catches.
            long len = 6L + ((e >>> 24) & 0xFF) % 15L;
            if (in < start || in >= start + len) {
                return 1.0;
            }
            long c = FixturePower.mix(e ^ in * 0xBF58476D1CE4E5B9L);
            int r = (int) (c & 0xFF);
            return r < 110 ? 0.0 : r < 160 ? 0.35 : 1.0;
        }
        if (kind < 88) {
            // Drop-out: dark for 10-40 ticks, then a few quick flashes as it re-strikes.
            long dark = 10L + ((e >>> 24) & 0xFF) % 31L;
            if (in < start || in >= start + dark + 6L) {
                return 1.0;
            }
            if (in < start + dark) {
                return 0.0;
            }
            long k = in - start - dark;
            return k == 1 || k == 3 || k == 5 ? 1.0 : 0.0;
        }
        // Browning: the whole episode at 35-55 %, wavering slowly as the bad contact heats.
        double base = 0.35 + 0.20 * ((e >>> 24) & 0xFF) / 255.0;
        return base + 0.10 * Math.sin(in * 0.37 + ((e >>> 40) & 0xFF));
    }

    // ==========================================================================================
    // The light around you
    // ==========================================================================================

    /** 0..1: how lit your spot is by Level 228's lights, eased. */
    public static float lit() {
        return lit;
    }

    /** The colour of the light around you (brightest channel 1): warm under bulbs, cold in the exit hallway. */
    public static float colourR() {
        return colR;
    }

    public static float colourG() {
        return colG;
    }

    public static float colourB() {
        return colB;
    }

    /**
     * 0..1: how much of the light that should reach your spot is out right now (a failing bulb
     * that is dark or browned down), lightly eased. Under a shader pack (static block light) the
     * atmosphere dips the exposure by it.
     */
    public static float outShare() {
        return outShare;
    }

    /** Client tick, in Level 228, after BackroomsLights has set every light's level and distance. */
    static void tickAround(List<FixtureLight> byDistance) {
        double r = 0.0;
        double g = 0.0;
        double b = 0.0;
        double sum = 0.0;
        double full = 0.0;
        for (int i = 0; i < byDistance.size(); i++) {
            FixtureLight light = byDistance.get(i);
            if (light.distSq > AROUND * AROUND) {
                break; // nearest first
            }
            if (!isBulb(light.type)) {
                continue;
            }
            double w = light.type.brightness / (1.0 + light.distSq);
            full += w;
            double lw = w * light.level;
            r += light.type.red * lw;
            g += light.type.green * lw;
            b += light.type.blue * lw;
            sum += lw;
        }
        if (sum > 1.0e-6) {
            double max = Math.max(r, Math.max(g, b));
            colR += ((float) (r / max) - colR) * EASE;
            colG += ((float) (g / max) - colG) * EASE;
            colB += ((float) (b / max) - colB) * EASE;
        }
        lit += ((float) Math.min(1.0, sum / FULLY_LIT) - lit) * EASE;
        float out = full > 1.0e-6 ? (float) Math.max(0.0, 1.0 - sum / full) : 0.0f;
        // Out of the bulbs' range the share means nothing: fade it with how lit the spot could be.
        out *= (float) Math.min(1.0, full / FULLY_LIT);
        outShare += (out - outShare) * OUT_EASE;
    }

    /** Leaving the level: back to the defaults. */
    static void reset() {
        lit = 0.0f;
        colR = 1.0f;
        colG = 0.70f;
        colB = 0.43f;
        outShare = 0.0f;
    }
}
