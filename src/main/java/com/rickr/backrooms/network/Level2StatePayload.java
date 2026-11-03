package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client in Level 2: the level's layout seed. The corridor's twist stretches are pure
 * functions of (seed, x, z) ({@code world/layout/Level2Twist}), but only the server knows the
 * seed. With it the client can tell how strongly the stretch you stand in twists, which way it
 * banks and where its lamps sit round the corridor (the spin illusion: client/anomaly/level2/Level2Spin).
 *
 * <p>Sent by {@code world/Level39Sync} (the shared arrival hook) whenever someone arrives in Level 2.
 */
public record Level2StatePayload(long layoutSeed) implements CustomPacketPayload {
    public static final Type<Level2StatePayload> TYPE = new Type<>(Backrooms.id("level2_state"));
    public static final StreamCodec<ByteBuf, Level2StatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Level2StatePayload::layoutSeed,
            Level2StatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
