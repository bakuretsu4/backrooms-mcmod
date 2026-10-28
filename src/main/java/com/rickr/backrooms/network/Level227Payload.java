package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: something the tree of Level 227 does that the client has to show (the sounds,
 * the falling leaves and the camera shake are all played by {@code client/Level227Audio}).
 *
 * @param kind     one of the constants below
 * @param ticks    how long it lasts
 * @param strength 0..1, how hard (a lurch can be a shiver or a real heave)
 */
public record Level227Payload(int kind, int ticks, float strength) implements CustomPacketPayload {
    /** The whole tree lurches in the void: a vast groan, leaves raining down, a slight shake (everyone in the level). */
    public static final int LURCH = 0;
    /** The tree is about to shake you loose: a few seconds of a building groan and shudder, then the noclip. */
    public static final int UNSTABLE = 1;
    /** In a forest or field of another level: something is dragging you away into Level 227. */
    public static final int DRAG = 2;

    public static final Type<Level227Payload> TYPE = new Type<>(Backrooms.id("level227"));
    public static final StreamCodec<ByteBuf, Level227Payload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Level227Payload::kind,
            ByteBufCodecs.VAR_INT, Level227Payload::ticks,
            ByteBufCodecs.FLOAT, Level227Payload::strength,
            Level227Payload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
