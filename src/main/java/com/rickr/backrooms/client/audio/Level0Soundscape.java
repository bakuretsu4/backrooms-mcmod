package com.rickr.backrooms.client.audio;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.light.FixturePower;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.BiomeAmbientSoundsHandler;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * What Level 0 sounds like beyond the single tubes: the room tone that falls silent in the dark,
 * and the sounds of its events and set pieces.
 *
 * <ul>
 *   <li><b>Silence when dark.</b> The room tone (the level's background loop: mains hum, air,
 *       far ventilation) belongs to a building with its lights on. Every tick
 *       {@code client/light/FixtureSounds} tells us how much tube light there is around you
 *       ({@link #reportTubeLight}); where it is dark (a dark district, the dark wing, a room
 *       whose lights died) the room tone sinks about 18 dB, and in a blackout, with the mains
 *       off, about 30 dB: practically nothing, so the hum of the tubes that still burn IS the
 *       soundscape and darkness is quiet. It fades in decibels, down in about a second and back
 *       up over two, so it never pumps or jumps.</li>
 *   <li><b>The blackout and the power coming back</b> get Level 0 versions (the server plays the
 *       same event sound on every level; here we swap it as it arrives): the hum of the whole
 *       floor sagging while ballasts pop one after another, each further away and duller, and
 *       later far contactors and the mains swelling back in the walls. The tubes near you pop and
 *       strike again on their own, see FixtureSounds.</li>
 *   <li><b>Set pieces and hidden rooms</b> call the methods below every tick while you are
 *       near: the Front Office's alarm, the Atrium's air, sounds from up the Shaft Hall's shafts,
 *       the hidden rooms' radio, clock and stillness. Each loop fades in by itself and fades out
 *       once nobody asks for it any more; nobody has to stop anything.</li>
 * </ul>
 * Sounds and their loudness: tools/anomaly_assets/level0_spaces_audio.py.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level0Soundscape {
    // --- Silence when dark -----------------------------------------------------------------------
    /** Room tone level (dB) with no lit tube near you ... */
    private static final float DARK_DB = -18.0f;
    /** ... and how much deeper it sinks there during a blackout (the mains are off: no air, nothing). */
    private static final float BLACKOUT_EXTRA_DB = -12.0f;
    /**
     * The tube light measure (see {@link #reportTubeLight}) at or below which it is "dark", and at
     * or above which the room tone plays in full. One healthy tube right above you is about 0.8;
     * a lit tube 8 blocks off about 0.25 (a dim district: the tone half way down).
     */
    private static final float LIGHT_DARK = 0.03f;
    private static final float LIGHT_FULL = 0.5f;
    /** Seconds (time constants) for the room tone to fall when it gets dark, and to come back. */
    private static final float FALL_SECONDS = 0.7f;
    private static final float RISE_SECONDS = 2.0f;

    // --- Set pieces --------------------------------------------------------------------------------
    /** One shaft sound (Shaft Hall or Atrium) at most every this many ticks ... */
    private static final int ABOVE_MIN_TICKS = 25 * 20;
    /** ... plus up to this many more. */
    private static final int ABOVE_SPREAD_TICKS = 35 * 20;
    /** A district going dark further away than this is heard from this far, in its direction. */
    private static final double DISTRICT_MAX = 56.0;
    /** An outage this big (radius, blocks) at least this far from you is a district going dark (heard). */
    private static final float DISTRICT_MIN_RADIUS = 9.0f;
    private static final double DISTRICT_MIN_DISTANCE = 24.0;
    /** The still room: a chair creaks every 20-40 s while you are within this many blocks of it. */
    private static final double STILL_NEAR = 7.0;
    private static final int STILL_MIN_TICKS = 20 * 20;
    private static final int STILL_SPREAD_TICKS = 20 * 20;
    /** A loop that never really started (the sound engine was full) is retried after this many ticks. */
    private static final int LOOP_RETRY_TICKS = 40;

    /** Hidden-room tones (see {@link #hiddenRoom}). */
    public enum HiddenTone {
        /** An old radio: a waltz drifting in and out of static, a voice reading the same message. */
        RADIO,
        /** A wall clock that skips a beat now and then. */
        CLOCK,
        /** The stillness of a room shut for years; the room tone outside sinks while you are in it. */
        SEALED,
        /** Near-silence, and now and then a chair creaking as if someone just stood up. */
        STILL,
        /** The office behind the Front Office's drawn door: its clock and a live PA microphone's hum. */
        OFFICE
    }

    private static final RandomSource RANDOM = RandomSource.create();
    private static long tick;
    private static boolean onLevel0;

    private static float tubeLight = 1.0f;
    private static long tubeLightTick = Long.MIN_VALUE;
    /** The room tone's level right now, in dB (0 = as mixed). */
    private static float roomToneDb;
    private static long duckTick = Long.MIN_VALUE;
    private static float duckRequest = 1.0f;

    /** A loop a set piece keeps asking for, and the tick it was started. */
    private record Loop(FadingLoopSound sound, long startedTick) {}

    private static final Long2ObjectOpenHashMap<Loop> LOOPS = new Long2ObjectOpenHashMap<>();
    /** Loop key -> the tick before which it is not retried (the engine dropped it). */
    private static final Long2LongOpenHashMap RETRY_AT = new Long2LongOpenHashMap();
    private static long nextAboveTick;
    private static long nextStillTick;
    /** Shaft holes asked for this tick: one is picked when a shaft sound is due. */
    private static final double[] HOLES = new double[16 * 3];
    private static int holeCount;
    private static long holesTick = Long.MIN_VALUE;

    // ==============================================================================================
    // Silence when dark
    // ==============================================================================================

    /**
     * How much tube light is around you right now (Level 0, every tick, from FixtureSounds): the sum
     * over the lit tubes near you of their power times a falloff with distance. 0 = dark.
     */
    public static void reportTubeLight(float light) {
        tubeLight = light;
        tubeLightTick = tick;
    }

    /**
     * Asks for the room tone at no more than {@code level} (0..1) this tick, e.g. inside a sealed
     * room. The quietest request wins; when nobody asks, it comes back by itself.
     */
    public static void duckRoomTone(float level) {
        if (duckTick != tick) {
            duckTick = tick;
            duckRequest = level;
        } else {
            duckRequest = Math.min(duckRequest, level);
        }
    }

    /** The room tone's volume factor wanted right now (asked by its {@link DuckedLoopSound} every tick). */
    private static double roomToneGain() {
        return Math.pow(10.0, roomToneDb / 20.0);
    }

    /** The level the room tone should sink to right now (dB). */
    private static float roomToneTarget() {
        // "Asked within the last 2 ticks", written so the Long.MIN_VALUE start can't overflow.
        float light = tick <= tubeLightTick + 2 ? tubeLight : 1.0f;
        float lit = smoothstep(LIGHT_DARK, LIGHT_FULL, light);
        // The power where you stand: off in a blackout (until its recovery reaches you) and inside
        // a local outage. With it off, the dark sinks deeper still: no air handling, nothing.
        Vec3 ear = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float power = Math.min(FixturePower.mainsPower(), FixturePower.level0PowerAt(ear.x, ear.z));
        float mainsOff = 1.0f - power;
        float db = (1.0f - lit) * (DARK_DB + BLACKOUT_EXTRA_DB * mainsOff);
        if (tick <= duckTick + 2) {
            db = Math.min(db, 20.0f * (float) Math.log10(Math.max(duckRequest, 0.001f)));
        }
        return db;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    // ==============================================================================================
    // Set pieces and hidden rooms (call every tick while wanted)
    // ==============================================================================================

    /**
     * The Front Office: its failing fire horn, heard about 80 blocks off, and close by the red
     * beacon's motor. Call every tick while the player is within about 96 blocks and the alarm
     * should sound; {@code strength} 0..1 (0 or no call = it fades away).
     */
    public static void frontOfficeAlarm(double x, double y, double z, float strength) {
        long key = keyOf(x, y, z);
        loop(key, ModSounds.L0_OFFICE_ALARM.get(), x, y, z, strength);
        loop(key, ModSounds.L0_BEACON_MOTOR.get(), x, y, z, strength);
    }

    /**
     * The Atrium: cold air moving high up in the shaft, and now and then something heard from the
     * dark above. Call every tick while the player is within about 48 blocks.
     */
    public static void atrium(double x, double floorY, double topY, double z) {
        double airY = Math.min(floorY + 12.0, topY - 3.0);
        loop(keyOf(x, airY, z), ModSounds.L0_ATRIUM_AIR.get(), x, airY, z, 1.0f);
        shaftHole(x, topY, z);
    }

    /**
     * One shaft of the Shaft Hall (or the Atrium) whose top is at {@code topY}. Call every tick for
     * each shaft within about 40 blocks; every 25-60 s one of them gets a sound from up there (a
     * scrape, a knock, a creak, grit trickling, a far thump).
     */
    public static void shaftHole(double x, double topY, double z) {
        if (holesTick != tick) {
            holesTick = tick;
            holeCount = 0;
        }
        if (holeCount < HOLES.length / 3) {
            HOLES[holeCount * 3] = x;
            HOLES[holeCount * 3 + 1] = topY;
            HOLES[holeCount * 3 + 2] = z;
            holeCount++;
        }
    }

    /**
     * A hidden room's tone at (x, y, z); {@code key} tells rooms apart (e.g. BlockPos.asLong of the
     * spot). Call every tick while the player is within about 16 blocks.
     */
    public static void hiddenRoom(long key, HiddenTone kind, double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        boolean near = mc.player != null && mc.player.distanceToSqr(x, y, z) < STILL_NEAR * STILL_NEAR;
        switch (kind) {
            case RADIO -> loop(key, ModSounds.L0_RADIO.get(), x, y, z, 1.0f);
            case CLOCK -> loop(key, ModSounds.L0_CLOCK_TICK.get(), x, y, z, 1.0f);
            case OFFICE -> loop(key, ModSounds.L0_HIDDEN_OFFICE.get(), x, y, z, 1.0f);
            case SEALED -> {
                loop(key, ModSounds.L0_SEALED_ROOM.get(), x, y, z, 1.0f);
                if (near) {
                    duckRoomTone(0.35f);
                }
            }
            case STILL -> {
                if (near) {
                    duckRoomTone(0.2f);
                    if (tick >= nextStillTick) {
                        nextStillTick = tick + STILL_MIN_TICKS + RANDOM.nextInt(STILL_SPREAD_TICKS);
                        double a = RANDOM.nextDouble() * Math.PI * 2.0;
                        double r = 1.0 + RANDOM.nextDouble() * 2.0;
                        oneShot(ModSounds.L0_STILL_CREAK.get(), x + Math.cos(a) * r, y, z + Math.sin(a) * r,
                                0.9f, 0.9f + RANDOM.nextFloat() * 0.2f);
                    }
                } else {
                    // Walking in, the first creak waits a while: never the moment you arrive.
                    nextStillTick = Math.max(nextStillTick, tick + STILL_MIN_TICKS / 2);
                }
            }
        }
    }

    static {
        // Level 0's local outages (L0Shadows' FixturePower.OutageListener has one listener, and it
        // is ours): a district going dark far from you is heard as its contactor dropping out. The
        // payload may arrive on another thread, so the sound is played on the game's.
        FixturePower.outageListener = (id, x, z, radius, startMs) ->
                Minecraft.getInstance().execute(() -> onOutage(x, z, radius, startMs));
    }

    /** An outage just started: a big one (a district) far enough off is heard from where it is. */
    private static void onOutage(double x, double z, float radius, long startMs) {
        Minecraft mc = Minecraft.getInstance();
        if (!onLevel0 || mc.player == null || radius < DISTRICT_MIN_RADIUS
                || System.currentTimeMillis() - startMs > 3000L) {
            return; // a room, a corridor (their tubes pop on their own), or one long over when you came
        }
        Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
        double dx = x - ear.x;
        double dz = z - ear.z;
        if (dx * dx + dz * dz >= DISTRICT_MIN_DISTANCE * DISTRICT_MIN_DISTANCE) {
            districtDown(x, BackroomsLevel.LEVEL_0.layout().ceilingY() - 0.5, z);
        }
    }

    /**
     * A district going dark far away (a local outage): its contactor dropping out and a muffled
     * cascade of ballasts. Beyond about 56 blocks it is placed 56 blocks out in its direction, so
     * it is still heard, faintly, from the right side.
     */
    public static void districtDown(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
        double dx = x - ear.x;
        double dz = z - ear.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > DISTRICT_MAX) {
            x = ear.x + dx / d * DISTRICT_MAX;
            z = ear.z + dz / d * DISTRICT_MAX;
        }
        oneShot(ModSounds.L0_DISTRICT_DOWN.get(), x, y, z, 1.0f, 0.9f + RANDOM.nextFloat() * 0.15f);
    }

    /**
     * A looping sound at (x, y, z) at {@code volume} (0..1). Call every tick while it should play;
     * it fades in over about a second and fades out once you stop calling. {@code key} tells
     * emitters apart (the same key with a different sound is a different emitter).
     */
    public static void loop(long key, SoundEvent sound, double x, double y, double z, float volume) {
        if (!onLevel0) {
            return;
        }
        long id = key ^ (sound.getLocation().hashCode() * 0x9E3779B97F4A7C15L);
        Loop loop = LOOPS.get(id);
        Minecraft mc = Minecraft.getInstance();
        if (loop != null && (loop.sound().isStopped()
                || (tick - loop.startedTick() > 20 && !mc.getSoundManager().isActive(loop.sound())))) {
            // It faded out and stopped, or the sound engine never played it (too many sounds).
            if (!loop.sound().isStopped()) {
                RETRY_AT.put(id, tick + LOOP_RETRY_TICKS);
            }
            LOOPS.remove(id);
            loop = null;
        }
        if (loop == null) {
            if (RETRY_AT.get(id) > tick) {
                return;
            }
            loop = new Loop(new FadingLoopSound(sound, false, x, y, z, 0.05f), tick);
            LOOPS.put(id, loop);
            mc.getSoundManager().play(loop.sound());
        }
        loop.sound().feed(volume);
    }

    /** A one-off sound at (x, y, z) (Level 0 only). */
    public static void oneShot(SoundEvent sound, double x, double y, double z, float volume, float pitch) {
        if (!onLevel0) {
            return;
        }
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.AMBIENT, volume, pitch,
                RANDOM, x, y, z));
    }

    private static long keyOf(double x, double y, double z) {
        return BlockPos.asLong(Mth.floor(x), Mth.floor(y), Mth.floor(z));
    }

    // ==============================================================================================
    // Ticking
    // ==============================================================================================

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        tick++;
        Minecraft mc = Minecraft.getInstance();
        boolean now = mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_0;
        if (now != onLevel0) {
            onLevel0 = now;
            LOOPS.clear(); // their sounds fade out by themselves, unfed
            RETRY_AT.clear();
            roomToneDb = now ? roomToneTarget() : 0.0f; // arrive at the right level, no fade from full
        }
        if (!now || mc.isPaused()) {
            return;
        }
        float target = roomToneTarget();
        float seconds = target < roomToneDb ? FALL_SECONDS : RISE_SECONDS;
        roomToneDb += (target - roomToneDb) * (1.0f - (float) Math.exp(-0.05f / seconds));

        if (holesTick < tick - 40) {
            // No shaft near you for two seconds: the next time you come to one, wait again first.
            nextAboveTick = 0;
        }
        if (holesTick >= tick - 1 && holeCount > 0 && tick >= nextAboveTick) {
            if (nextAboveTick != 0) {
                int i = RANDOM.nextInt(holeCount) * 3;
                oneShot(ModSounds.L0_SHAFT_ABOVE.get(), HOLES[i], HOLES[i + 1] - 1.0, HOLES[i + 2],
                        1.0f, 0.85f + RANDOM.nextFloat() * 0.25f);
            }
            // The first one waits a while after you arrive (nextAboveTick 0 = never played).
            nextAboveTick = tick + ABOVE_MIN_TICKS + RANDOM.nextInt(ABOVE_SPREAD_TICKS);
        }
        if (LOOPS.size() > 64) {
            LOOPS.long2ObjectEntrySet().removeIf(e -> e.getValue().sound().isStopped());
        }
        if (RETRY_AT.size() > 64) {
            RETRY_AT.clear();
        }
    }

    /**
     * Just before a sound starts: wrap Level 0's room tone so we can sink it in the dark, and swap
     * the generic blackout and power-up sounds for Level 0's own.
     */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        // The dimension itself, not onLevel0: the biome starts its loop on the very first tick in
        // the new level, before our own tick has noticed we arrived.
        Minecraft mc = Minecraft.getInstance();
        if (sound == null || mc.level == null || BackroomsLevel.fromDimension(mc.level.dimension()) != BackroomsLevel.LEVEL_0) {
            return;
        }
        if (sound instanceof BiomeAmbientSoundsHandler.LoopSoundInstance loop
                && sound.getLocation().equals(ModSounds.LEVEL0_HUM.getId())) {
            event.setSound(new DuckedLoopSound(loop, Level0Soundscape::roomToneGain, 1.0f, (float) roomToneGain()));
            return;
        }
        SoundEvent swap = null;
        if (sound.getLocation().equals(ModSounds.POWER_DOWN.getId())) {
            swap = ModSounds.L0_BLACKOUT_WAVE.get();
        } else if (sound.getLocation().equals(ModSounds.POWER_UP.getId())) {
            swap = ModSounds.L0_POWER_RESTORE.get();
        }
        if (swap != null) {
            // In your head, like the server's version: the whole floor, not one spot.
            event.setSound(new SimpleSoundInstance(swap.getLocation(), SoundSource.AMBIENT, 1.0f, 1.0f, RANDOM,
                    false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
        }
    }

    private Level0Soundscape() {}
}
