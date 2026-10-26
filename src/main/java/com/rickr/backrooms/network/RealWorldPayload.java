package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> owning client, in the real world: whether you are filming with a camcorder, and how
 * thin reality has worn around you (0..1; alone in the dark, or filming it). The client turns it
 * into the camcorder look, the hum and the yellowing (see world/RealWorldEntries).
 */
public record RealWorldPayload(boolean recording, float thin) implements CustomPacketPayload {
    public static final Type<RealWorldPayload> TYPE = new Type<>(Backrooms.id("real_world"));
    public static final StreamCodec<ByteBuf, RealWorldPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RealWorldPayload::recording,
            ByteBufCodecs.FLOAT, RealWorldPayload::thin,
            RealWorldPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
