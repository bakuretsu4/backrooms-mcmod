package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: you escaped back to reality. The tape glitches and stops ("STOP"). Moves
 * between floors don't use this: they all datamosh ({@code IntroCutscenePayload}).
 */
public record EscapePayload() implements CustomPacketPayload {
    public static final Type<EscapePayload> TYPE = new Type<>(Backrooms.id("escape"));
    public static final StreamCodec<ByteBuf, EscapePayload> STREAM_CODEC = StreamCodec.unit(new EscapePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
