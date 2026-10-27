package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server, only while hiding: jump was pressed (get out), or sneak was pressed or let go
 * (hold your breath / breathe again). While you hide, your movement keys are switched off on your
 * own client, so vanilla never sends these keys to the server by itself.
 */
public record HideInputPayload(int action) implements CustomPacketPayload {
    public static final int LEAVE = 0;
    public static final int HOLD_BREATH = 1;
    public static final int BREATHE = 2;

    public static final Type<HideInputPayload> TYPE = new Type<>(Backrooms.id("hide_input"));
    public static final StreamCodec<ByteBuf, HideInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HideInputPayload::action,
            HideInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
