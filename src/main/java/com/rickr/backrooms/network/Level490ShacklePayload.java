package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client in Level 490: you are chained to the pillar whose top is at (x, y, z) on a
 * punishment platform ({@code on}), or free again (client/Level490Shackles).
 */
public record Level490ShacklePayload(int x, int y, int z, boolean on) implements CustomPacketPayload {
    public static final Type<Level490ShacklePayload> TYPE = new Type<>(Backrooms.id("level490_shackle"));
    public static final StreamCodec<ByteBuf, Level490ShacklePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Level490ShacklePayload::x,
            ByteBufCodecs.VAR_INT, Level490ShacklePayload::y,
            ByteBufCodecs.VAR_INT, Level490ShacklePayload::z,
            ByteBufCodecs.BOOL, Level490ShacklePayload::on,
            Level490ShacklePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
