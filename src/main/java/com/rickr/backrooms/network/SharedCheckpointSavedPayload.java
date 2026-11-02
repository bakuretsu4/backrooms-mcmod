package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> a teammate on the same level: "{@code name} just saved a checkpoint you can use".
 * The camcorder shows "NAME SAVED A CHECKPOINT" for a few seconds when it arrives.
 */
public record SharedCheckpointSavedPayload(String name) implements CustomPacketPayload {
    public static final Type<SharedCheckpointSavedPayload> TYPE = new Type<>(Backrooms.id("shared_checkpoint_saved"));
    public static final StreamCodec<ByteBuf, SharedCheckpointSavedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SharedCheckpointSavedPayload::name,
            SharedCheckpointSavedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
