package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server in Level 490: a chained player pulled at their chains once (pressed jump). */
public record Level490StrugglePayload() implements CustomPacketPayload {
    public static final Type<Level490StrugglePayload> TYPE = new Type<>(Backrooms.id("level490_struggle"));
    public static final StreamCodec<ByteBuf, Level490StrugglePayload> STREAM_CODEC = StreamCodec.unit(new Level490StrugglePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
