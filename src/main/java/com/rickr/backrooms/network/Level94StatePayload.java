package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client in Level 94: when this level's day began ({@code clockStart}, a game tick; see
 * {@code world/Level94Clock}) and where your nearest castle stands, so the client can paint the
 * right sky and put the far-off castle on the horizon in the right direction. Only the server
 * knows the layout seed, so the client can't work out either by itself.
 *
 * <p>Sent by {@code world/Level94Sync} on arrival, when the clock restarts and when your nearest
 * castle changes.
 */
public record Level94StatePayload(long clockStart, int castleX, int castleZ) implements CustomPacketPayload {
    public static final Type<Level94StatePayload> TYPE = new Type<>(Backrooms.id("level94_state"));
    public static final StreamCodec<ByteBuf, Level94StatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Level94StatePayload::clockStart,
            ByteBufCodecs.VAR_INT, Level94StatePayload::castleX,
            ByteBufCodecs.VAR_INT, Level94StatePayload::castleZ,
            Level94StatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
