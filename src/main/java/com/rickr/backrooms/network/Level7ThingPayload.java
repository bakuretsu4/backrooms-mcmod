package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client, Level 7, testing only ({@code /backrooms thing}, see command/Level7ThingCommand):
 * the Thing is client-side (client/Level7Thing), so the server just tells the player's client to
 * make it pass by now, or to jump to full dread. In normal play the client does this on its own.
 *
 * @param kind {@link #PASS} or {@link #DREAD}
 */
public record Level7ThingPayload(int kind) implements CustomPacketPayload {
    /** Full dread, and a pass of whatever kind fits where you are, right now. */
    public static final int PASS = 0;
    /** Only full dread: the following passes and groans come at their most frequent and closest. */
    public static final int DREAD = 1;

    public static final Type<Level7ThingPayload> TYPE = new Type<>(Backrooms.id("level7_thing"));
    public static final StreamCodec<ByteBuf, Level7ThingPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Level7ThingPayload::kind,
            Level7ThingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
