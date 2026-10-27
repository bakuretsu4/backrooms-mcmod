package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> the one drifted player ({@code world/RealityDrift}): the blocks that look different
 * for them right now (an empty list once their reality snaps back).
 *
 * <p>The client only uses it to ignore right-clicks on those blocks. Without it, clicking the fake
 * door would make your own game swing it open for a moment (doors open on your side straight
 * away, before the server answers), and a click on a fake wall could place a chalk mark in thin
 * air on the server. With it, the fake door simply never opens.
 */
public record DriftPayload(List<BlockPos> faked) implements CustomPacketPayload {
    public static final Type<DriftPayload> TYPE = new Type<>(Backrooms.id("drift"));
    public static final StreamCodec<ByteBuf, DriftPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(512)), DriftPayload::faked,
            DriftPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
