package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> owning client: whether you are standing in a safe room right now (sent when it
 * changes). Only the server knows the floor plan's seed, so the client can't work this out itself.
 */
public record SafeRoomPayload(boolean inSafeRoom) implements CustomPacketPayload {
    public static final Type<SafeRoomPayload> TYPE = new Type<>(Backrooms.id("safe_room"));
    public static final StreamCodec<ByteBuf, SafeRoomPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SafeRoomPayload::inSafeRoom,
            SafeRoomPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
