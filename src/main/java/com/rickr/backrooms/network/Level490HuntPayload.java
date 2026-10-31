package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client in Level 490: what the creature hunting you is doing (world/Level490Hunt): its
 * stage, where its head is, and {@code near} (0..1: how close it is to having you, for the
 * camcorder's distress). Sent a few times a second while it is about; the client glides its body
 * there (client/Level490Creature).
 */
public record Level490HuntPayload(int stage, float x, float y, float z, float near) implements CustomPacketPayload {
    public static final Type<Level490HuntPayload> TYPE = new Type<>(Backrooms.id("level490_hunt"));
    public static final StreamCodec<ByteBuf, Level490HuntPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Level490HuntPayload::stage,
            ByteBufCodecs.FLOAT, Level490HuntPayload::x,
            ByteBufCodecs.FLOAT, Level490HuntPayload::y,
            ByteBufCodecs.FLOAT, Level490HuntPayload::z,
            ByteBufCodecs.FLOAT, Level490HuntPayload::near,
            Level490HuntPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
