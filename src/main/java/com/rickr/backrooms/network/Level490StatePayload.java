package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client in Level 490: the level's layout seed. With it the client works out where the
 * colossal chains hang ({@code world/layout/Level490Layout#chainsNear}), so the sky can draw the
 * ones beyond render distance where they really are. Sent by {@code world/Level490Sync}.
 */
public record Level490StatePayload(long layoutSeed) implements CustomPacketPayload {
    public static final Type<Level490StatePayload> TYPE = new Type<>(Backrooms.id("level490_state"));
    public static final StreamCodec<ByteBuf, Level490StatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Level490StatePayload::layoutSeed,
            Level490StatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
