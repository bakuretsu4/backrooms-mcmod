package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client in Level 39: the level's layout seed. The town, its tunnels and the facade are
 * pure functions of (seed, x, z) ({@code world/layout/Level39Layout}), but only the server knows
 * the seed. With it the client can ask the same questions itself: which tunnel water is stagnant
 * (murkier water), where the facade's windows are (Veil lights, the far facade), where the holes
 * are (the warning sounds).
 *
 * <p>Sent by {@code world/Level39Sync} whenever someone arrives in Level 39.
 */
public record Level39StatePayload(long layoutSeed) implements CustomPacketPayload {
    public static final Type<Level39StatePayload> TYPE = new Type<>(Backrooms.id("level39_state"));
    public static final StreamCodec<ByteBuf, Level39StatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Level39StatePayload::layoutSeed,
            Level39StatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
