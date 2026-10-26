package com.rickr.backrooms.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Level 94's own day and night: 8 minutes of day (the last 30 seconds are dusk, when the siren
 * music falters and the sky turns orange), then 3 minutes of night (the last 20 seconds are dawn).
 *
 * <p>Minecraft's clock can't do this per dimension, so the level's dimension type has a fixed
 * time and everything that cares (sky, light, music, monsters) asks this class instead. The cycle
 * is counted in game ticks from a start tick: the server keeps it in the level's saved data and
 * restarts it at morning when someone arrives in an empty Level 94, so you never step in at
 * midnight; clients get it from the server ({@link #setClientStart}).
 */
public final class Level94Clock {
    public static final int DAY_TICKS = 8 * 60 * 20;
    /** The end of the day: music falters and stops, the sky turns orange, then dark. */
    public static final int DUSK_TICKS = 30 * 20;
    public static final int NIGHT_TICKS = 3 * 60 * 20;
    /** The end of the night: the sky pales again. Still night as far as monsters go. */
    public static final int DAWN_TICKS = 20 * 20;
    public static final int CYCLE = DAY_TICKS + NIGHT_TICKS;

    public enum Phase { DAY, DUSK, NIGHT, DAWN }

    /** The start tick as told by the server (client side only). */
    private static volatile long clientStart;

    private Level94Clock() {}

    /** Ticks since this cycle's morning, 0 .. CYCLE - 1. */
    public static long cycleTick(Level level) {
        long start = level.isClientSide() ? clientStart : start((ServerLevel) level);
        return Math.floorMod(level.getGameTime() - start, (long) CYCLE);
    }

    public static Phase phase(Level level) {
        long t = cycleTick(level);
        if (t < DAY_TICKS - DUSK_TICKS) return Phase.DAY;
        if (t < DAY_TICKS) return Phase.DUSK;
        if (t < CYCLE - DAWN_TICKS) return Phase.NIGHT;
        return Phase.DAWN;
    }

    /** Night or dawn: the dangerous part, when the music is silent and things come out. */
    public static boolean isNight(Level level) {
        Phase phase = phase(level);
        return phase == Phase.NIGHT || phase == Phase.DAWN;
    }

    /** 1 in full day, 0 in full night, smooth through dusk and dawn. */
    public static float daylight(Level level, float partialTick) {
        float t = cycleTick(level) + partialTick;
        if (t < DAY_TICKS - DUSK_TICKS) return 1.0f;
        if (t < DAY_TICKS) return 1.0f - smooth((t - (DAY_TICKS - DUSK_TICKS)) / DUSK_TICKS);
        if (t < CYCLE - DAWN_TICKS) return 0.0f;
        return smooth((t - (CYCLE - DAWN_TICKS)) / DAWN_TICKS);
    }

    /** 0 until dusk starts, rising to 1 when night begins; stays 1 through the night. For sky colours. */
    public static float duskProgress(Level level, float partialTick) {
        float t = cycleTick(level) + partialTick;
        if (t < DAY_TICKS - DUSK_TICKS) return 0.0f;
        if (t < DAY_TICKS) return (t - (DAY_TICKS - DUSK_TICKS)) / DUSK_TICKS;
        return 1.0f;
    }

    /**
     * How loud the siren music plays: 1 all day. In the first half of dusk it falters (drops out
     * for moments, then for longer) and fades; silent for the rest of dusk and all night.
     */
    public static float musicVolume(Level level) {
        long t = cycleTick(level);
        long duskStart = DAY_TICKS - DUSK_TICKS;
        if (t < duskStart) return 1.0f;
        float u = (t - duskStart) / (DUSK_TICKS * 0.5f);
        if (u >= 1.0f) return 0.0f;
        // Dropouts: in every 10-tick slot, silent if a fixed hash of the slot is below u.
        long slot = (t - duskStart) / 10;
        double hash = Hash.unit(Hash.mix(slot * 0x9E3779B97F4A7C15L));
        return hash < u ? 0.0f : 1.0f - u * u;
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    // ---- Server side --------------------------------------------------------------------------

    /** The tick this cycle's first morning began (server side). */
    public static long start(ServerLevel level) {
        return data(level).start;
    }

    /** Morning now. Call when someone arrives in an empty Level 94. */
    public static void restartAtMorning(ServerLevel level) {
        Data data = data(level);
        data.start = level.getGameTime();
        data.setDirty();
    }

    /** Moves the cycle's start tick (the {@code /backrooms time94} test command). Server side. */
    public static void setStart(ServerLevel level, long start) {
        Data data = data(level);
        data.start = start;
        data.setDirty();
    }

    /** Client side: the server's start tick (see the sync payload). */
    public static void setClientStart(long start) {
        clientStart = start;
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Data::new, Data::load, null), "backrooms_level94_clock");
    }

    private static final class Data extends SavedData {
        long start;

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            data.start = tag.getLong("start");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putLong("start", start);
            return tag;
        }
    }
}
