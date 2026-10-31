package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client in Level 490: the hung ruin of cell (i, j), generation {@code gen}, falls at game
 * tick {@code start} (world/Level490Hung, world/Level490Falls). Sent when the server makes one fall,
 * and for every such fall still remembered when you arrive, so you never see a fallen ruin hanging.
 */
public record Level490FallPayload(int i, int j, long gen, long start) implements CustomPacketPayload {
    public static final Type<Level490FallPayload> TYPE = new Type<>(Backrooms.id("level490_fall"));
    public static final StreamCodec<ByteBuf, Level490FallPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Level490FallPayload::i,
            ByteBufCodecs.VAR_INT, Level490FallPayload::j,
            ByteBufCodecs.VAR_LONG, Level490FallPayload::gen,
            ByteBufCodecs.VAR_LONG, Level490FallPayload::start,
            Level490FallPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
