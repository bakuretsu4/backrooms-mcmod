package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> every client in Level 228: one imitator slot (world/Level228Imitator). {@code phase}
 * 0 means the slot is empty (it is gone). Otherwise: what it is doing ({@code phase}, and for the
 * wrong phase which wrongness {@code kind}), whom it follows and watches ({@code watcher}), whose
 * face it wears ({@code wearer}), and where it stands in REAL maze coordinates. The client never
 * draws it there, only at that point's images in the mirrors (client/level228/Level228ImitatorClient
 * through the reflections' stand-in renderer), so every viewer sees the same figure in the same glass.
 * {@code behind}: the rare moment it stands right behind the watcher.
 */
public record Level228ImitatorPayload(int slot, int phase, int kind, boolean behind, UUID watcher, UUID wearer,
        float x, float y, float z, float yaw) implements CustomPacketPayload {
    public static final Type<Level228ImitatorPayload> TYPE = new Type<>(Backrooms.id("level228_imitator"));
    // Ten fields: more than StreamCodec.composite takes, so written out by hand.
    public static final StreamCodec<ByteBuf, Level228ImitatorPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                FriendlyByteBuf out = new FriendlyByteBuf(buf);
                out.writeByte(p.slot);
                out.writeByte(p.phase);
                out.writeByte(p.kind);
                out.writeBoolean(p.behind);
                out.writeUUID(p.watcher);
                out.writeUUID(p.wearer);
                out.writeFloat(p.x);
                out.writeFloat(p.y);
                out.writeFloat(p.z);
                out.writeFloat(p.yaw);
            },
            buf -> {
                FriendlyByteBuf in = new FriendlyByteBuf(buf);
                return new Level228ImitatorPayload(in.readByte(), in.readByte(), in.readByte(), in.readBoolean(),
                        in.readUUID(), in.readUUID(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
