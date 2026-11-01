package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client in Level 490: the creature attacking (world/Level490Hunt). {@code kind}:
 * {@link #OVER_EDGE} (on the walk: an arm rises out of the clouds over the edge at (fx, fy, fz)) or
 * {@link #SLAM} (on the tower: from its body): a hand comes down on (tx, ty, tz) at game tick
 * {@code land}, rests there, and lifts away at {@code lift}; {@link #THROW}; {@link #SHAKE}.
 */
public record Level490ReachPayload(int kind, float fx, float fy, float fz, float tx, float ty, float tz, long land, long lift)
        implements CustomPacketPayload {
    public static final int OVER_EDGE = 0;
    public static final int SLAM = 1;
    /** A chunk of masonry hurled from (fx, fy, fz), landing at (tx, ty, tz) at {@code land}. */
    public static final int THROW = 2;
    /** It shakes the tower, gripping it at (fx, fy, fz), from {@code land} to {@code lift}. */
    public static final int SHAKE = 3;
    public static final Type<Level490ReachPayload> TYPE = new Type<>(Backrooms.id("level490_reach"));
    public static final StreamCodec<ByteBuf, Level490ReachPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeByte(p.kind());
        buf.writeFloat(p.fx());
        buf.writeFloat(p.fy());
        buf.writeFloat(p.fz());
        buf.writeFloat(p.tx());
        buf.writeFloat(p.ty());
        buf.writeFloat(p.tz());
        buf.writeLong(p.land());
        buf.writeLong(p.lift());
    }, buf -> new Level490ReachPayload(buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readLong(), buf.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
