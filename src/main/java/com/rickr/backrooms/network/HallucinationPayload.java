package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> owning client: how strong your Poolrooms hallucination is right now (0..1). */
public record HallucinationPayload(float dream) implements CustomPacketPayload {
    public static final Type<HallucinationPayload> TYPE = new Type<>(Backrooms.id("hallucination"));
    public static final StreamCodec<ByteBuf, HallucinationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, HallucinationPayload::dream,
            HallucinationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
