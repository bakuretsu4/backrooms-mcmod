package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client in Level 369: the level's layout seed (so the client can tell where the drifting
 * light is, {@code world/Level369Light}) and how much you have forgotten, 0..1 (the camcorder's
 * readouts fade with it). Sent on arrival and once a second by {@code world/Level369Forgetting}.
 */
public record Level369StatePayload(long layoutSeed, float forgotten) implements CustomPacketPayload {
    public static final Type<Level369StatePayload> TYPE = new Type<>(Backrooms.id("level369_state"));
    public static final StreamCodec<ByteBuf, Level369StatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Level369StatePayload::layoutSeed,
            ByteBufCodecs.FLOAT, Level369StatePayload::forgotten,
            Level369StatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
