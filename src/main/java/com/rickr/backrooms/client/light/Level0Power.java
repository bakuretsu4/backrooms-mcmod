package com.rickr.backrooms.client.light;

import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.network.LevelEventPayload;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Where the power is out on Level 0, and how the lamps die and come back.
 *
 * <p>Two things can cut the power:
 * <ul>
 *   <li><b>The blackout event</b> (the server's BLACKOUT): a deliberate dying wave. It starts at a
 *       point near you and rolls outward at {@value #WAVE_SPEED} blocks per second, each tube
 *       popping with a short stutter as the front reaches it, so the lights you can see die over
 *       one to two seconds. Then a partial recovery, decided per 24-block patch of the floor
 *       ({@link #CELL}): about half the patches come back 20-60 s after the wave, a quarter when
 *       the event ends, and a quarter stay dead for another 2-4 minutes. In the patches still
 *       dark, one tube in four re-strikes dimly on its emergency ballast after a few seconds,
 *       and every standby emergency lamp switches itself on (see {@link FixturePower#levelOf}).</li>
 *   <li><b>Local outages</b> (world/FixtureOutages on the server, sent by LocalOutagePayload):
 *       one room, a corridor or a distant district. A circle (CUT, DIE_STUTTER or WAVE) or a
 *       list of fixture blocks each with its own delay. They end on their own or when the server
 *       restores them; the tubes then re-strike with a stutter (a WAVE comes back outside-in).</li>
 * </ul>
 *
 * <p>Everything is a pure function of the event's or outage's start time and the lamp's position,
 * so every player sees the same tubes die in the same order. Two views of it:
 * {@link #lightFactor} for one lamp (sharp: stutters, pops, re-strikes; drives Veil, the glowing
 * faces and the tube sounds) and {@link #powerAt} for any spot (smooth: drives the bounce fill,
 * far ceiling panels and the camcorder's exposure).
 */
public final class Level0Power {
    /** The blackout's dying wave rolls outward this fast (blocks per second). */
    static final double WAVE_SPEED = 22.0;
    /** A local WAVE outage is a little slower, so you can watch it come down a corridor. */
    static final double OUTAGE_WAVE_SPEED = 18.0;
    /** The blackout starts at a point inside the 32-block square you stand in, up to 12 blocks from its middle. */
    private static final int ORIGIN_CELL = 32;
    private static final double ORIGIN_JITTER = 24.0;
    /** Recovery is decided per patch this size (blocks), so dark areas are whole districts, not a checkerboard. */
    static final int CELL = 24;
    /** Out of 256: patches that come back early (20-60 s after the wave) ... */
    private static final int EARLY_SHARE = 128;
    /** ... and (the next) patches that come back when the event ends; the rest linger dark. */
    private static final int AT_END_SHARE = 64;
    private static final long EARLY_MIN_MS = 20_000L;
    private static final long EARLY_SPREAD_MS = 40_000L;
    private static final long LINGER_MIN_MS = 120_000L;
    private static final long LINGER_SPREAD_MS = 120_000L;
    /** Patches coming back at the end of the event don't all strike at once. */
    private static final long END_SPREAD_MS = 2_500L;
    /** In a patch that stays dark, one tube in four comes back dimly on its emergency ballast ... */
    private static final float EMERGENCY_LEVEL = 0.3f;
    /** ... this long after the wave passed it. */
    private static final long EMERGENCY_MIN_MS = 3_000L;
    private static final long EMERGENCY_SPREAD_MS = 3_000L;
    /** The bounce fill of a dark patch with its few emergency tubes (powerAt). */
    private static final float EMERGENCY_FILL = 0.08f;
    /** A tube pops: it stutters this long (plus up to 200 ms) before it dies. */
    private static final long POP_MS = 120L;
    /** Coming back, a tube stutters for 300-900 ms before it holds. */
    private static final long RESTRIKE_MIN_MS = 300L;
    private static final long RESTRIKE_SPREAD_MS = 600L;
    /** Outages never last longer than this, even if nobody ends them. */
    static final long MAX_OUTAGE_MS = 600_000L;

    /** Pattern numbers, the same as world/FixtureOutages.Pattern's order. */
    static final int CUT = 0;
    static final int DIE_STUTTER = 1;
    static final int WAVE = 2;

    /** One local outage, as the client sees it. */
    static final class Outage {
        final int id;
        final int pattern;
        final double x;
        final double z;
        final float radius;
        final long startMs;
        long endMs;
        /** List outages: fixture block -> its own delay (ms). Null for circles. */
        @Nullable
        final Long2IntOpenHashMap delays;
        /** List outages: the area the listed blocks cover (for the power map). */
        double minX;
        double minZ;
        double maxX;
        double maxZ;

        Outage(int id, int pattern, double x, double z, float radius, long startMs, long endMs, @Nullable Long2IntOpenHashMap delays) {
            this.id = id;
            this.pattern = pattern;
            this.x = x;
            this.z = z;
            this.radius = radius;
            this.startMs = startMs;
            this.endMs = endMs;
            this.delays = delays;
        }
    }

    private static final List<Outage> OUTAGES = new ArrayList<>();

    // The blackout wave (Level 0's BLACKOUT event).
    private static long waveStartMs = Long.MIN_VALUE;
    private static long waveEndMs;
    private static double waveX;
    private static double waveZ;
    private static long waveSalt;

    private Level0Power() {}

    // ------------------------------------------------------------------ state

    /**
     * Follows the floor's event. Call once per client tick while on Level 0, with where the
     * camera is (a new blackout starts its wave near you).
     */
    static void tick(long now, double camX, double camZ) {
        boolean blackout = FixturePower.activeEvent(now) == LevelEventPayload.BLACKOUT;
        if (blackout && CameraClientState.eventStartMs != waveStartMs) {
            startWave(CameraClientState.eventStartMs, camX, camZ);
        }
        if (waveStartMs != Long.MIN_VALUE) {
            if (blackout) {
                waveEndMs = Math.max(CameraClientState.eventEndMs, waveStartMs);
            } else {
                waveEndMs = Math.min(waveEndMs, now);
            }
            // Long after the last lingering patch came back: forget the wave.
            if (!blackout && now > waveEndMs + LINGER_MIN_MS + LINGER_SPREAD_MS + 5_000L) {
                waveStartMs = Long.MIN_VALUE;
            }
        }
        OUTAGES.removeIf(o -> now > o.endMs + restoreSpreadMs(o) + RESTRIKE_MIN_MS + RESTRIKE_SPREAD_MS + 1_000L);
    }

    private static void startWave(long startMs, double camX, double camZ) {
        waveStartMs = startMs;
        waveEndMs = Math.max(CameraClientState.eventEndMs, startMs);
        // The same for everyone within a minute's latency of each other.
        waveSalt = FixturePower.mix(startMs / 60_000L ^ 0x6C8E9CF570932BD5L);
        int cx = Math.floorDiv((int) Math.floor(camX), ORIGIN_CELL);
        int cz = Math.floorDiv((int) Math.floor(camZ), ORIGIN_CELL);
        long h = FixturePower.mix(cx * 0x9E3779B97F4A7C15L ^ cz * 0xC2B2AE3D27D4EB4FL ^ waveSalt);
        waveX = cx * ORIGIN_CELL + ORIGIN_CELL / 2.0 + ((h & 0xFFFF) / 65535.0 - 0.5) * ORIGIN_JITTER;
        waveZ = cz * ORIGIN_CELL + ORIGIN_CELL / 2.0 + (((h >>> 16) & 0xFFFF) / 65535.0 - 0.5) * ORIGIN_JITTER;
    }

    /** New dimension or logout: forget everything. */
    static void reset() {
        OUTAGES.clear();
        waveStartMs = Long.MIN_VALUE;
    }

    /** A local outage arrived from the server (see LocalOutagePayload). */
    public static void start(int id, int pattern, double x, double z, float radius, long elapsedMs, int durationMs,
                             long @Nullable [] blocks, int @Nullable [] delaysMs) {
        long now = System.currentTimeMillis();
        long startMs = now - Math.max(0L, elapsedMs);
        long endMs = durationMs > 0 ? startMs + durationMs : startMs + MAX_OUTAGE_MS;
        Long2IntOpenHashMap delays = null;
        Outage outage;
        if (blocks != null && blocks.length > 0) {
            delays = new Long2IntOpenHashMap(blocks.length);
            double minX = Double.MAX_VALUE;
            double minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxZ = -Double.MAX_VALUE;
            for (int i = 0; i < blocks.length; i++) {
                delays.put(blocks[i], delaysMs != null && i < delaysMs.length ? Math.max(0, delaysMs[i]) : 0);
                int bx = BlockPos.getX(blocks[i]);
                int bz = BlockPos.getZ(blocks[i]);
                minX = Math.min(minX, bx);
                minZ = Math.min(minZ, bz);
                maxX = Math.max(maxX, bx + 1);
                maxZ = Math.max(maxZ, bz + 1);
            }
            outage = new Outage(id, pattern, (minX + maxX) / 2.0, (minZ + maxZ) / 2.0,
                    (float) (Math.max(maxX - minX, maxZ - minZ) / 2.0), startMs, endMs, delays);
            outage.minX = minX;
            outage.minZ = minZ;
            outage.maxX = maxX;
            outage.maxZ = maxZ;
        } else {
            outage = new Outage(id, pattern, x, z, radius, startMs, endMs, null);
        }
        OUTAGES.removeIf(o -> o.id == id);
        OUTAGES.add(outage);
        FixturePower.OutageListener listener = FixturePower.outageListener;
        if (listener != null) {
            listener.onOutage(id, outage.x, outage.z, outage.radius, startMs);
        }
    }

    /** The server restored this outage: its tubes re-strike now. */
    public static void end(int id) {
        long now = System.currentTimeMillis();
        for (Outage outage : OUTAGES) {
            if (outage.id == id) {
                outage.endMs = Math.min(outage.endMs, Math.max(now, outage.startMs));
            }
        }
    }

    static long waveStartMs() {
        return waveStartMs;
    }

    static double waveX() {
        return waveX;
    }

    static double waveZ() {
        return waveZ;
    }

    /** True while any outage or the blackout wave is in play (the power map needs work). */
    static boolean anything() {
        return waveStartMs != Long.MIN_VALUE || !OUTAGES.isEmpty();
    }

    // ------------------------------------------------------------------ one lamp

    /**
     * 0..1: how much power this Level 0 lamp has right now (1 = untouched). Sharp: a tube pops
     * with a stutter and is then dark, and re-strikes with a stutter.
     */
    static float lightFactor(FixtureLight light, long now) {
        if (!anything()) {
            return 1.0f;
        }
        float f = 1.0f;
        if (waveStartMs != Long.MIN_VALUE) {
            f = Math.min(f, waveLight(light, now));
        }
        for (int i = 0; i < OUTAGES.size() && f > 0.0f; i++) {
            f = Math.min(f, outageLight(OUTAGES.get(i), light, now));
        }
        return f;
    }

    private static float waveLight(FixtureLight light, long now) {
        long h = FixturePower.mix(light.key ^ waveSalt ^ 0x4F1BBCDCBFA53E0AL);
        long dieAt = waveStartMs + (long) (Math.hypot(light.x - waveX, light.z - waveZ) / WAVE_SPEED * 1000.0) + (h & 0x7F);
        long backAt = cellBackMs(light.x, light.z);
        long restrike = RESTRIKE_MIN_MS + ((h >>> 8) & 0xFFFF) % RESTRIKE_SPREAD_MS;
        float base = timeline(h, now, dieAt, POP_MS + ((h >>> 24) & 0xFF) % 200L, backAt, restrike);
        if (base < 1.0f && now >= dieAt && now < backAt && !earlyCell(light.x, light.z)
                && tubeLike(light.type) && ((h >>> 40) & 3L) == 0L) {
            // One tube in four in a dark patch re-strikes dimly on its emergency ballast.
            long emergencyAt = dieAt + EMERGENCY_MIN_MS + ((h >>> 44) & 0xFFFF) % EMERGENCY_SPREAD_MS;
            if (now >= emergencyAt) {
                return now < emergencyAt + 400L ? (flick(h ^ 0x55L, now) ? EMERGENCY_LEVEL : 0.0f) : EMERGENCY_LEVEL;
            }
        }
        return base;
    }

    private static float outageLight(Outage o, FixtureLight light, long now) {
        long h = FixturePower.mix(light.key ^ (long) o.id * 0x9E3779B97F4A7C15L ^ 0x2F7B3D1A5C9E8B47L);
        long delay;
        double d = Math.hypot(light.x - o.x, light.z - o.z);
        if (o.delays != null) {
            int found = -1;
            if (light.blocks != null) {
                for (long block : light.blocks) {
                    if (o.delays.containsKey(block)) {
                        found = Math.max(found, o.delays.get(block));
                    }
                }
            } else if (o.delays.containsKey(light.key)) {
                found = o.delays.get(light.key);
            }
            if (found < 0) {
                return 1.0f;
            }
            delay = found;
        } else {
            if (d > o.radius) {
                return 1.0f;
            }
            delay = dieDelay(o.pattern, d, h);
        }
        long dieAt = o.startMs + delay;
        long backAt = o.endMs + restoreDelay(o, d, h);
        long restrike = RESTRIKE_MIN_MS + ((h >>> 8) & 0xFFFF) % RESTRIKE_SPREAD_MS;
        return timeline(h, now, dieAt, stutterMs(o.pattern, h), backAt, restrike);
    }

    /** When, after the start, a lamp at distance d dies. */
    private static long dieDelay(int pattern, double d, long h) {
        return switch (pattern) {
            case WAVE -> (long) (d / OUTAGE_WAVE_SPEED * 1000.0) + (h & 0x7F);
            case DIE_STUTTER -> (h & 0xFFFF) % 600L;
            default -> (h & 0xFFFF) % 250L;
        };
    }

    /** How long a lamp stutters before it dies. */
    private static long stutterMs(int pattern, long h) {
        return switch (pattern) {
            case DIE_STUTTER -> 400L + ((h >>> 24) & 0xFFFF) % 800L;
            case WAVE -> POP_MS + ((h >>> 24) & 0xFF) % 200L;
            default -> ((h >>> 24) & 0xFF) % 80L;
        };
    }

    /** When, after the outage ends, a lamp comes back: a WAVE outside-in, the rest in a loose spread. */
    private static long restoreDelay(Outage o, double d, long h) {
        if (o.pattern == WAVE && o.delays == null) {
            return (long) (Math.max(0.0, o.radius - d) / OUTAGE_WAVE_SPEED * 1000.0) + ((h >>> 32) & 0x7F);
        }
        return ((h >>> 32) & 0xFFFF) % 1_500L;
    }

    private static long restoreSpreadMs(Outage o) {
        return o.pattern == WAVE ? (long) (o.radius / OUTAGE_WAVE_SPEED * 1000.0) + 128L : 1_500L;
    }

    /**
     * The shape of every outage in time: on, a stutter of {@code stutterMs} ending at
     * {@code dieAt}, dark, then from {@code backAt} a re-strike stutter of {@code restrikeMs}, on.
     */
    private static float timeline(long h, long now, long dieAt, long stutterMs, long backAt, long restrikeMs) {
        if (now < dieAt - stutterMs) {
            return 1.0f;
        }
        if (now < dieAt) {
            return flick(h, now) ? 1.0f : 0.0f;
        }
        if (now < backAt) {
            return 0.0f;
        }
        if (now < backAt + restrikeMs) {
            return flick(h ^ 0x3CL, now) ? 1.0f : 0.0f;
        }
        return 1.0f;
    }

    /** A fast, irregular on/off (a starter trying to strike): toggles every 50-150 ms, on about half the time. */
    private static boolean flick(long seed, long now) {
        long slot = now / (50L + (seed & 0x3F) + ((seed >>> 6) & 0x3F));
        return (FixturePower.mix(seed + slot * 0x9E3779B97F4A7C15L) & 0xFF) >= 120;
    }

    private static boolean tubeLike(FixtureType type) {
        return type == FixtureType.FLUORESCENT_LIGHT || type == FixtureType.L0_TROFFER_STRIP;
    }

    private static long cellHash(double x, double z) {
        long cx = Math.floorDiv((long) Math.floor(x), CELL);
        long cz = Math.floorDiv((long) Math.floor(z), CELL);
        return FixturePower.mix(cx * 0x632BE59BD9B4E019L ^ cz * 0x9E3779B97F4A7C15L ^ waveSalt);
    }

    private static boolean earlyCell(double x, double z) {
        return (cellHash(x, z) & 0xFF) < EARLY_SHARE;
    }

    /** When the blackout's power comes back in the patch at (x, z). */
    private static long cellBackMs(double x, double z) {
        long h = cellHash(x, z);
        int roll = (int) (h & 0xFF);
        long atEnd = waveEndMs + ((h >>> 8) & 0xFFFF) % END_SPREAD_MS;
        if (roll < EARLY_SHARE) {
            return Math.min(waveStartMs + EARLY_MIN_MS + ((h >>> 24) & 0xFFFF) % EARLY_SPREAD_MS, atEnd);
        }
        if (roll < EARLY_SHARE + AT_END_SHARE) {
            return atEnd;
        }
        return waveEndMs + LINGER_MIN_MS + ((h >>> 24) & 0xFFFF) % LINGER_SPREAD_MS;
    }

    // ------------------------------------------------------------------ any spot (smooth)

    /**
     * 0..1: how much power there is at (x, z) right now, smooth (no stutters; a short fade where
     * a lamp would pop). For the bounce fill, far ceiling panels, exposure and standby emergency
     * lamps.
     */
    static float powerAt(double x, double z, long now) {
        if (!anything()) {
            return 1.0f;
        }
        float f = 1.0f;
        if (waveStartMs != Long.MIN_VALUE) {
            long dieAt = waveStartMs + (long) (Math.hypot(x - waveX, z - waveZ) / WAVE_SPEED * 1000.0);
            long backAt = cellBackMs(x, z);
            float v = smoothTimeline(now, dieAt, backAt);
            if (now >= dieAt + EMERGENCY_MIN_MS && now < backAt && !earlyCell(x, z)) {
                v = Math.max(v, EMERGENCY_FILL);
            }
            f = Math.min(f, v);
        }
        for (int i = 0; i < OUTAGES.size(); i++) {
            Outage o = OUTAGES.get(i);
            if (o.delays != null) {
                f = Math.min(f, listPower(o, x, z, now));
                continue;
            }
            double d = Math.hypot(x - o.x, z - o.z);
            float weight = (float) smooth(o.radius + 2.0, o.radius - 2.0, d);
            if (weight <= 0.0f) {
                continue;
            }
            long delay = o.pattern == WAVE ? (long) (d / OUTAGE_WAVE_SPEED * 1000.0) : 0L;
            long back = o.endMs + (o.pattern == WAVE ? (long) (Math.max(0.0, o.radius - d) / OUTAGE_WAVE_SPEED * 1000.0) : 700L);
            float v = smoothTimeline(now, o.startMs + delay, back);
            f = Math.min(f, 1.0f - weight * (1.0f - v));
        }
        return f;
    }

    /** A list outage's power at (x, z): each listed lamp darkens the area within ~5 blocks of it, at its own moment. */
    private static float listPower(Outage o, double x, double z, long now) {
        if (x < o.minX - LIST_REACH || x > o.maxX + LIST_REACH || z < o.minZ - LIST_REACH || z > o.maxZ + LIST_REACH) {
            return 1.0f;
        }
        float f = 1.0f;
        var it = o.delays.long2IntEntrySet().fastIterator();
        while (it.hasNext()) {
            var entry = it.next();
            long block = entry.getLongKey();
            double d = Math.hypot(x - (BlockPos.getX(block) + 0.5), z - (BlockPos.getZ(block) + 0.5));
            if (d >= LIST_REACH) {
                continue;
            }
            float weight = (float) smooth(LIST_REACH, 1.5, d);
            float v = smoothTimeline(now, o.startMs + entry.getIntValue(), o.endMs + 700L);
            f = Math.min(f, 1.0f - weight * (1.0f - v));
        }
        return f;
    }

    /** How far (blocks) a listed lamp's outage darkens the bounce fill around it. */
    private static final double LIST_REACH = 5.0;

    private static float smoothTimeline(long now, long dieAt, long backAt) {
        if (now < dieAt) {
            return 1.0f;
        }
        if (now < backAt) {
            return 1.0f - (float) Math.min(1.0, (now - dieAt) / 250.0);
        }
        return (float) Math.min(1.0, (now - backAt) / 600.0);
    }

    private static double smooth(double edge0, double edge1, double v) {
        double t = Math.max(0.0, Math.min(1.0, (v - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }
}
