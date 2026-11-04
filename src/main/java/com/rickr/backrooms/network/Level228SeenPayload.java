package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server in Level 228: "I can see the Other in a mirror right now". Only the client
 * knows exactly what is on screen (the view, the panes and frames between), so it decides and
 * repeats this a few times a second while it holds; the server freezes the Other until the
 * reports stop for a moment (world/Level228Hunt#seen). No fields: the message is the fact.
 */
public record Level228SeenPayload() implements CustomPacketPayload {
    public static final Level228SeenPayload INSTANCE = new Level228SeenPayload();
    public static final Type<Level228SeenPayload> TYPE = new Type<>(Backrooms.id("level228_seen"));
    public static final StreamCodec<ByteBuf, Level228SeenPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
