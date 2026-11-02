package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client, Level 9, testing only: start one of the street's client-side effects right
 * now instead of waiting for it ({@code /backrooms event lampsbehind} and {@code /backrooms event
 * dogs}, see command/Level9StreetCommand). In normal play the clients start these on their own.
 *
 * @param kind {@link #LAMPS_BEHIND} or {@link #DOGS}
 */
public record Level9StreetPayload(int kind) implements CustomPacketPayload {
    /** Streetlamps you walk past start going out behind you (client/level9/LampsBehind). */
    public static final int LAMPS_BEHIND = 0;
    /** A dog chorus that stops dead, then silence (client/level9/DogsGoQuiet). */
    public static final int DOGS = 1;

    public static final Type<Level9StreetPayload> TYPE = new Type<>(Backrooms.id("level9_street"));
    public static final StreamCodec<ByteBuf, Level9StreetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Level9StreetPayload::kind,
            Level9StreetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
