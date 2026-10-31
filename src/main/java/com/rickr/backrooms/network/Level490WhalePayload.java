package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client in Level 490: the Great Whale comes down beside the spire to (x, y, z) at game
 * tick {@code start}, its head along {@code yaw} (degrees, from +x toward +z) so its eye is on the
 * player at the top (client/Level490Whales#rise, sent by world/Level490Falls).
 */
public record Level490WhalePayload(int x, int y, int z, long start, float yaw) implements CustomPacketPayload {
    public static final Type<Level490WhalePayload> TYPE = new Type<>(Backrooms.id("level490_whale"));
    public static final StreamCodec<ByteBuf, Level490WhalePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Level490WhalePayload::x,
            ByteBufCodecs.VAR_INT, Level490WhalePayload::y,
            ByteBufCodecs.VAR_INT, Level490WhalePayload::z,
            ByteBufCodecs.VAR_LONG, Level490WhalePayload::start,
            ByteBufCodecs.FLOAT, Level490WhalePayload::yaw,
            Level490WhalePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
