package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client on arriving in Level 228 (and on login or respawn there): the level's layout
 * seed, so the client knows the floor plan ({@code world/layout/Level228Plan}): where the image
 * volumes behind the glass are, for the reflections. Sent by {@code world/Level228Rules}.
 */
public record Level228StatePayload(long layoutSeed) implements CustomPacketPayload {
    public static final Type<Level228StatePayload> TYPE = new Type<>(Backrooms.id("level228_state"));
    public static final StreamCodec<ByteBuf, Level228StatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Level228StatePayload::layoutSeed,
            Level228StatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
