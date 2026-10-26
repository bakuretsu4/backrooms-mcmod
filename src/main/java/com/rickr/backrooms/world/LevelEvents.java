package com.rickr.backrooms.world;

import com.rickr.backrooms.network.LevelEventPayload;
import com.rickr.backrooms.registry.ModSounds;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Random floor-wide events, like in the found-footage videos: the power cuts out, the lights
 * stutter, the alarm goes off, a garbled PA announcement plays, thunder rolls (Level 1) or the
 * whole corridor tilts (Level 2). One event at a time per floor; everyone on that floor
 * experiences it together. The server runs the clock; clients only render it.
 *
 * <p>Between events, each player now and then hears a lone knock or creak somewhere far away.
 *
 * <p>The Poolrooms are the calm exception: no random events and no knocks, just the water. An
 * operator can still force an event there with the command (it then runs and ends normally).
 *
 * <p>Level 9 (the Suburbs) has its own two events and no knocks (its horror is the silence):
 * LAMP_WAVE, a stutter rolling down the streetlamps that leaves some of them dead for a while,
 * and FOG_ROLL, the fog closing in to ~12 blocks while something huge rumbles far away in it.
 * The clients render both (lamps, fog, rumble sounds and shake) from the event payload alone.
 *
 * <p>Level 4 (the Abandoned Office) has no scares, only its storm: every 1-3 minutes lightning
 * strikes outside (STORM). The clients flash the windows ({@code Level4Atmosphere}); the server
 * sends the thunder a second or two later.
 */
public final class LevelEvents {
    /** The first event comes about a minute and a half after someone arrives on a floor. */
    private static final int FIRST_DELAY = 90 * 20;
    private static final int ALARM_SOUND_PERIOD = 6 * 20;
    private static final int INTERCOM_TICKS = 8 * 20;
    private static final int LIGHTNING_TICKS = 30;
    private static final int TWIST_TICKS = 20 * 20;
    /** The PA line repeats as a fainter echo this many ticks later, from a different direction. */
    private static final int INTERCOM_ECHO_DELAY = 7;
    /** Level 4 storm: the flashes take ~2 s, the thunder comes 1-3.5 s after the first one. */
    private static final int STORM_TICKS = 6 * 20;

    private static final class State {
        int kind = LevelEventPayload.NONE;
        long startTick;
        long endTick;
        long nextTick = -1;
        /** How many announcements this floor has heard: each one sounds a bit more wrong. */
        int intercomCount;
        float intercomPitch = 1.0f;
        long echoTick = -1;
        /** The worst (third) announcement is followed straight away by flickering lights. */
        boolean flickerAfter;
        /** Level 4: when the thunder of the current lightning strike arrives. */
        long thunderTick = -1;
    }

    private static final Map<BackroomsLevel, State> STATES = new EnumMap<>(BackroomsLevel.class);

    public static void tick(ServerLevel level, BackroomsLevel floor) {
        long now = level.getGameTime();
        State state = STATES.computeIfAbsent(floor, f -> new State());
        RandomSource random = level.getRandom();
        if (level.players().isEmpty()) {
            // Nobody here: restart the "first event" countdown for whoever comes next.
            state.nextTick = -1;
            return;
        }
        if (state.nextTick < 0) {
            // Level 4's storm starts soon after you arrive: the rain should not keep you waiting.
            int first = floor == BackroomsLevel.LEVEL_4 ? 25 * 20 : FIRST_DELAY;
            state.nextTick = now + first + random.nextInt(20 * 20) - 10 * 20;
        }

        // The Poolrooms are a quiet dream, Level 4 a safe haven and Level 37 pure calm: no power
        // cuts, alarms or footsteps-in-the-dark there. (Level 4's only events are its storms.)
        boolean calm = floor == BackroomsLevel.POOLROOMS || floor == BackroomsLevel.LEVEL_4 || floor == BackroomsLevel.LEVEL_37;
        // Knocks and creaks are indoor sounds; the Suburbs stay silent between their own events.
        if (!calm && floor != BackroomsLevel.LEVEL_9) {
            tickAmbience(level, random);
        }

        if (state.kind != LevelEventPayload.NONE) {
            tickRunning(level, state, now);
            if (now >= state.endTick) {
                end(level, floor, state, now);
            }
            return;
        }

        if ((!calm || floor == BackroomsLevel.LEVEL_4) && now >= state.nextTick) {
            startRandom(level, floor, state, random);
        }
    }

    // ------------------------------------------------------------------ running events

    private static void tickRunning(ServerLevel level, State state, long now) {
        if (state.kind == LevelEventPayload.ALARM && (state.endTick - now) % ALARM_SOUND_PERIOD == 0) {
            level.players().forEach(p -> p.playNotifySound(ModSounds.ALARM.get(), SoundSource.AMBIENT, 0.7f, 1.0f));
        }
        if (state.kind == LevelEventPayload.INTERCOM && now == state.echoTick) {
            // Echo: same line, quieter, from further away, as if bouncing down the halls.
            for (ServerPlayer player : level.players()) {
                playDistant(player, ModSounds.INTERCOM, 34, 2.0f, state.intercomPitch, level.getRandom());
            }
        }
        if (state.kind == LevelEventPayload.STORM && now == state.thunderTick) {
            // Thunder after the flash: light is faster than sound. Far off, from a random side.
            for (ServerPlayer player : level.players()) {
                playDistant(player, ModSounds.LEVEL4_THUNDER, 30, 4.0f, 0.85f + level.getRandom().nextFloat() * 0.25f, level.getRandom());
            }
        }
    }

    private static void startRandom(ServerLevel level, BackroomsLevel floor, State state, RandomSource random) {
        int roll = random.nextInt(100);
        int kind;
        int duration;
        switch (floor) {
            case LEVEL_0 -> {
                if (roll < 30) { kind = LevelEventPayload.BLACKOUT; duration = (25 + random.nextInt(20)) * 20; }
                else if (roll < 60) { kind = LevelEventPayload.FLICKER; duration = (6 + random.nextInt(6)) * 20; }
                else if (roll < 85) { kind = LevelEventPayload.INTERCOM; duration = INTERCOM_TICKS; }
                else { kind = LevelEventPayload.ALARM; duration = (15 + random.nextInt(15)) * 20; }
            }
            case LEVEL_1 -> {
                if (roll < 35) { kind = LevelEventPayload.LIGHTNING; duration = LIGHTNING_TICKS; }
                else if (roll < 60) { kind = LevelEventPayload.BLACKOUT; duration = (20 + random.nextInt(20)) * 20; }
                else if (roll < 85) { kind = LevelEventPayload.FLICKER; duration = (6 + random.nextInt(6)) * 20; }
                else { kind = LevelEventPayload.INTERCOM; duration = INTERCOM_TICKS; }
            }
            case LEVEL_9 -> {
                if (roll < 55) { kind = LevelEventPayload.LAMP_WAVE; duration = (20 + random.nextInt(15)) * 20; }
                else { kind = LevelEventPayload.FOG_ROLL; duration = (40 + random.nextInt(31)) * 20; }
            }
            case LEVEL_4 -> {
                kind = LevelEventPayload.STORM;
                duration = STORM_TICKS;
            }
            default -> {
                if (roll < 35) { kind = LevelEventPayload.TWIST; duration = TWIST_TICKS; }
                else if (roll < 70) { kind = LevelEventPayload.FLICKER; duration = (5 + random.nextInt(5)) * 20; }
                else if (roll < 85) { kind = LevelEventPayload.BLACKOUT; duration = (15 + random.nextInt(15)) * 20; }
                else { kind = LevelEventPayload.INTERCOM; duration = INTERCOM_TICKS; }
            }
        }
        start(level, state, kind, duration);
    }

    /** Begins an event: plays its opening sounds and tells every client on the floor. */
    private static void start(ServerLevel level, State state, int kind, int duration) {
        long now = level.getGameTime();
        RandomSource random = level.getRandom();
        state.kind = kind;
        state.startTick = now;
        state.endTick = now + duration;
        switch (kind) {
            case LevelEventPayload.BLACKOUT ->
                    level.players().forEach(p -> p.playNotifySound(ModSounds.POWER_DOWN.get(), SoundSource.AMBIENT, 1.0f, 1.0f));
            case LevelEventPayload.INTERCOM -> {
                // Escalation: clean -> lower and warped -> slow and broken (+ flicker afterwards).
                int stage = state.intercomCount % 3;
                state.intercomCount++;
                state.intercomPitch = switch (stage) {
                    case 0 -> 1.0f;
                    case 1 -> 0.86f;
                    default -> 0.7f;
                };
                state.flickerAfter = stage == 2;
                state.echoTick = now + INTERCOM_ECHO_DELAY;
                for (ServerPlayer player : level.players()) {
                    playDistant(player, ModSounds.INTERCOM, 18, 3.0f, state.intercomPitch, random);
                }
            }
            case LevelEventPayload.LIGHTNING -> {
                for (ServerPlayer player : level.players()) {
                    playDistant(player, ModSounds.THUNDER, 28, 4.0f, 0.8f + random.nextFloat() * 0.3f, random);
                }
            }
            case LevelEventPayload.STORM -> state.thunderTick = now + 20 + random.nextInt(50);
            case LevelEventPayload.TWIST ->
                    level.players().forEach(p -> p.playNotifySound(ModSounds.RADIO_STATIC.get(), SoundSource.AMBIENT, 0.6f, 1.0f));
            default -> {}
        }
        broadcast(level, state, now);
    }

    private static void end(ServerLevel level, BackroomsLevel floor, State state, long now) {
        int ended = state.kind;
        if (ended == LevelEventPayload.BLACKOUT) {
            level.players().forEach(p -> p.playNotifySound(ModSounds.POWER_UP.get(), SoundSource.AMBIENT, 1.0f, 1.0f));
        }
        if (ended == LevelEventPayload.TWIST) {
            level.players().forEach(LevelEvents::stopRadioStatic);
        }
        state.nextTick = now + gap(floor, level.getRandom());
        if (ended == LevelEventPayload.INTERCOM && state.flickerAfter) {
            state.flickerAfter = false;
            start(level, state, LevelEventPayload.FLICKER, (6 + level.getRandom().nextInt(4)) * 20);
            return;
        }
        state.kind = LevelEventPayload.NONE;
        broadcast(level, state, now);
    }

    /** Quiet time between events: Level 0 is patient, deeper floors are restless. */
    private static int gap(BackroomsLevel floor, RandomSource random) {
        return switch (floor) {
            case LEVEL_0 -> (50 + random.nextInt(26)) * 20;
            case LEVEL_1 -> (45 + random.nextInt(26)) * 20;
            case LEVEL_2 -> (30 + random.nextInt(21)) * 20;
            // Never starts events by itself; this only matters after an operator forced one.
            case POOLROOMS, LEVEL_37 -> (60 + random.nextInt(30)) * 20;
            // Level 4: a lightning strike outside every 1-3 minutes.
            case LEVEL_4 -> (60 + random.nextInt(121)) * 20;
            case LEVEL_9 -> (40 + random.nextInt(31)) * 20;
        };
    }

    // ------------------------------------------------------------------ ambience

    /** About once every 35 s per player: a knock or a creak somewhere you can't see. */
    private static void tickAmbience(ServerLevel level, RandomSource random) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (random.nextInt(35) == 0) {
                Holder<SoundEvent> sound = random.nextBoolean() ? ModSounds.DISTANT_KNOCK : ModSounds.DISTANT_CREAK;
                playDistant(player, sound, 14 + random.nextInt(10), 1.6f, 0.85f + random.nextFloat() * 0.3f, random);
            }
        }
    }

    /**
     * Plays a sound to one player from a point {@code distance} blocks away in a random direction,
     * so it reads as "somewhere down the hall". Volume above 1 extends the hearing range
     * (16 blocks per 1.0), which is how we keep far sounds audible.
     */
    private static void playDistant(ServerPlayer player, Holder<SoundEvent> sound, double distance, float volume, float pitch, RandomSource random) {
        float angle = random.nextFloat() * Mth.TWO_PI;
        double x = player.getX() + Mth.cos(angle) * distance;
        double z = player.getZ() + Mth.sin(angle) * distance;
        double y = player.getEyeY() + 1.0;
        player.connection.send(new ClientboundSoundPacket(sound, SoundSource.AMBIENT, x, y, z, volume, pitch, random.nextLong()));
    }

    private static void stopRadioStatic(ServerPlayer player) {
        player.connection.send(new ClientboundStopSoundPacket(ModSounds.RADIO_STATIC.getId(), SoundSource.AMBIENT));
    }

    // ------------------------------------------------------------------ public API

    /** Current event on this player's floor, e.g. right after they arrive. */
    public static void sendCurrent(ServerPlayer player) {
        BackroomsLevel floor = BackroomsLevel.fromDimension(player.level().dimension());
        State state = floor == null ? null : STATES.get(floor);
        long now = player.serverLevel().getGameTime();
        if (state == null || state.kind == LevelEventPayload.NONE) {
            PacketDistributor.sendToPlayer(player, new LevelEventPayload(LevelEventPayload.NONE, 0, 0));
        } else {
            PacketDistributor.sendToPlayer(player, payload(state, now));
        }
        if (state == null || state.kind != LevelEventPayload.TWIST) {
            stopRadioStatic(player); // left Level 2 mid-twist: don't carry the hiss along
        }
    }

    /** True while the lights are out on this floor (monsters get bolder). */
    public static boolean isBlackout(BackroomsLevel floor) {
        State state = STATES.get(floor);
        return state != null && state.kind == LevelEventPayload.BLACKOUT;
    }

    /** Operator command: start an event right now ({@code NONE} ends the current one). */
    public static void force(ServerLevel level, BackroomsLevel floor, int kind, int durationTicks) {
        State state = STATES.computeIfAbsent(floor, f -> new State());
        long now = level.getGameTime();
        if (kind == LevelEventPayload.NONE) {
            if (state.kind != LevelEventPayload.NONE) {
                state.flickerAfter = false;
                end(level, floor, state, now);
            }
            return;
        }
        if (state.kind == LevelEventPayload.TWIST) {
            level.players().forEach(LevelEvents::stopRadioStatic);
        }
        start(level, state, kind, durationTicks);
    }

    /** Server stopping: forget per-world state. */
    public static void reset() {
        STATES.clear();
    }

    private static LevelEventPayload payload(State state, long now) {
        return new LevelEventPayload(state.kind,
                (int) Math.max(0, state.endTick - now),
                (int) Math.max(0, now - state.startTick));
    }

    private static void broadcast(ServerLevel level, State state, long now) {
        LevelEventPayload payload = state.kind == LevelEventPayload.NONE
                ? new LevelEventPayload(LevelEventPayload.NONE, 0, 0)
                : payload(state, now);
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private LevelEvents() {}
}
