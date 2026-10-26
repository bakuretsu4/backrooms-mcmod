package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server, in the real world: "I'm still walking into a wall" (sent about twice a second
 * while you do). Only your client knows you're pressing forward against it; the server checks the
 * rest (dark, enclosed, a wall really there) in world/RealWorldEntries.
 */
public record WallPushPayload() implements CustomPacketPayload {
    public static final WallPushPayload INSTANCE = new WallPushPayload();
    public static final Type<WallPushPayload> TYPE = new Type<>(Backrooms.id("wall_push"));
    public static final StreamCodec<ByteBuf, WallPushPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
