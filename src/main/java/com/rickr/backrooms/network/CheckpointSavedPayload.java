package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> owning client: "you just saved your checkpoint at a radio". It carries no data; the
 * camcorder shows "CHECKPOINT SAVED" for a few seconds when it arrives.
 */
public record CheckpointSavedPayload() implements CustomPacketPayload {
    public static final CheckpointSavedPayload INSTANCE = new CheckpointSavedPayload();
    public static final Type<CheckpointSavedPayload> TYPE = new Type<>(Backrooms.id("checkpoint_saved"));
    public static final StreamCodec<ByteBuf, CheckpointSavedPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
