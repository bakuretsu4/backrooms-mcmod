package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> the player who just died: "you can wake up at your own checkpoint OR at the one
 * {@code sharedName} saved". Sent only when both are usable, right before Minecraft's own
 * "you died" packet, so the death screen already knows when it opens and adds the two buttons.
 */
public record CheckpointOptionsPayload(String sharedName) implements CustomPacketPayload {
    public static final Type<CheckpointOptionsPayload> TYPE = new Type<>(Backrooms.id("checkpoint_options"));
    public static final StreamCodec<ByteBuf, CheckpointOptionsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CheckpointOptionsPayload::sharedName,
            CheckpointOptionsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
