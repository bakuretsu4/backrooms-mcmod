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
