package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> owning client: your sanity right now (0..100). */
public record SanityPayload(float sanity) implements CustomPacketPayload {
    public static final Type<SanityPayload> TYPE = new Type<>(Backrooms.id("sanity"));
    public static final StreamCodec<ByteBuf, SanityPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, SanityPayload::sanity,
            SanityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
