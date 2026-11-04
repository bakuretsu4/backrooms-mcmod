package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> every client in Level 228: where "the Other" (world/Level228Hunt) is in REAL maze
 * coordinates and what it is doing. The client never draws it there; it draws it only at that
 * point's images inside the mirrors (client/level228/Level228Other). {@code seed} is the level's
 * layout seed, carried here so the client can ask Level228Plan for the image volumes without
 * waiting on any other sync. {@code hunted} is true only in the packet sent to the player it is
 * after (only they get the held-breath silence and the catch).
 */
public record Level228HuntPayload(long seed, int state, float x, float y, float z, float yaw, boolean hunted)
        implements CustomPacketPayload {
    public static final Type<Level228HuntPayload> TYPE = new Type<>(Backrooms.id("level228_hunt"));
    // Seven fields: one more than StreamCodec.composite takes, so written out by hand.
    public static final StreamCodec<ByteBuf, Level228HuntPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                FriendlyByteBuf out = new FriendlyByteBuf(buf);
                out.writeLong(p.seed);
                out.writeVarInt(p.state);
                out.writeFloat(p.x);
                out.writeFloat(p.y);
                out.writeFloat(p.z);
                out.writeFloat(p.yaw);
                out.writeBoolean(p.hunted);
            },
            buf -> {
                FriendlyByteBuf in = new FriendlyByteBuf(buf);
                return new Level228HuntPayload(in.readLong(), in.readVarInt(), in.readFloat(), in.readFloat(), in.readFloat(),
                        in.readFloat(), in.readBoolean());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
