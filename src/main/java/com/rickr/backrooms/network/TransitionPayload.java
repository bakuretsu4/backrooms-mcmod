package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: play the tape-glitch transition (noclip into a level, or escaping). */
public record TransitionPayload(int kind) implements CustomPacketPayload {
    public static final int NOCLIP = 0;
    public static final int ESCAPE = 1;

    public static final Type<TransitionPayload> TYPE = new Type<>(Backrooms.id("transition"));
    public static final StreamCodec<ByteBuf, TransitionPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(TransitionPayload::new, TransitionPayload::kind);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
