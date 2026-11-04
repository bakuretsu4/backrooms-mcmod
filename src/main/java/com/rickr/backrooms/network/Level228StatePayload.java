package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client on arriving in Level 228 (and on login, respawn, and every change of the
 * shifting passages or secret panels): the layout seed and the shared {@code ShiftState}
 * (world/layout/Level228Plan), so every client draws the same maze the server collides with.
 * Sent by {@code world/Level228Rules}.
 */
public record Level228StatePayload(long layoutSeed, long shiftBits, int shiftVersion) implements CustomPacketPayload {
    public static final Type<Level228StatePayload> TYPE = new Type<>(Backrooms.id("level228_state"));
    public static final StreamCodec<ByteBuf, Level228StatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Level228StatePayload::layoutSeed,
            ByteBufCodecs.VAR_LONG, Level228StatePayload::shiftBits,
            ByteBufCodecs.VAR_INT, Level228StatePayload::shiftVersion,
            Level228StatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
