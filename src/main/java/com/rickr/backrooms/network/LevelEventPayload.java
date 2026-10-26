package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: an event is happening on your floor (or ended, with {@code kind == NONE}).
 *
 * @param kind           one of the constants below
 * @param remainingTicks how long it still lasts
 * @param elapsedTicks   how long it has already been running (non-zero when you arrive mid-event)
 */
public record LevelEventPayload(int kind, int remainingTicks, int elapsedTicks) implements CustomPacketPayload {
    public static final int NONE = 0;
    /** Every light on the floor dies. Only your flashlight works. */
    public static final int BLACKOUT = 1;
    /** Lights stutter on and off. */
    public static final int FLICKER = 2;
    /** Red emergency lighting and a distant alarm. */
    public static final int ALARM = 3;
    /** A distorted PA announcement echoes through the floor (audio only; may be followed by a FLICKER). */
    public static final int INTERCOM = 4;
    /** Level 1 only: thunder outside and a white flash. */
    public static final int LIGHTNING = 5;
    /** Level 2 only: the world tilts (camera roll) for ~20 s while radios hiss. */
    public static final int TWIST = 6;
    /**
     * Level 9 only, the Mangled's fog: a sudden cold (frost, your breath), then thick fog rolls in
     * over ~6 s (you see ~12 blocks) and the lamps fall silent; something huge rumbles far away,
     * a black pillar may rise down a street with booms and a garbled voice (see
     * {@link Level9PillarPayload}); it clears again before the event ends.
     */
    public static final int FOG_ROLL = 7;
    /**
     * Level 9 only: a wave of stuttering runs down the streetlamps; some lamps behind it die and
     * only come back near the end.
     */
    public static final int LAMP_WAVE = 8;
    /**
     * Level 4 only: lightning outside the office windows. The windows (and everything near them)
     * flash cold white a couple of times, and the thunder rolls in a second or two later.
     */
    public static final int STORM = 9;
    /**
     * The Poolrooms and Level 37 only: the water turns "obtrusive" (both wikis, Level 37) and
     * drowns out every other sound for a while (client/anomaly/poolrooms/PoolAcoustics). Numbered
     * apart from the others so new events added for other levels never clash with it.
     */
    public static final int OBTRUSIVE_WATER = 37;

    public static final Type<LevelEventPayload> TYPE = new Type<>(Backrooms.id("level_event"));
    public static final StreamCodec<ByteBuf, LevelEventPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LevelEventPayload::kind,
            ByteBufCodecs.VAR_INT, LevelEventPayload::remainingTicks,
            ByteBufCodecs.VAR_INT, LevelEventPayload::elapsedTicks,
            LevelEventPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
