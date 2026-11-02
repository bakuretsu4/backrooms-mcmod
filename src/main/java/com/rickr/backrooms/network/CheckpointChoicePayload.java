package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server, from the death screen: "wake me up at the shared checkpoint" ({@code shared}
 * true) or "at my own one" (false). The client sends it just before Minecraft's own "respawn"
 * request. Both travel down the same connection and the server handles them in the order they
 * arrived, so the respawn point is already set when the respawn happens.
 */
public record CheckpointChoicePayload(boolean shared) implements CustomPacketPayload {
    public static final Type<CheckpointChoicePayload> TYPE = new Type<>(Backrooms.id("checkpoint_choice"));
    public static final StreamCodec<ByteBuf, CheckpointChoicePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CheckpointChoicePayload::shared,
            CheckpointChoicePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
