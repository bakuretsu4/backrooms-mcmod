package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client in Level 39: a loud sound went off at (x, y, z) and its echo rolls through the
 * town; at {@code strength} 0.3 or more the echo builds into a quake (see {@code world/Level39Quakes}).
 * The client plays the whole show itself (echoes, rumble, shaking, dust; see
 * {@code client/Level39QuakeEffects}), so this one small message is all that is sent.
 *
 * @param strength how hard it hits THIS player, 0..1 (already weakened by their distance)
 */
public record Level39QuakePayload(int x, int y, int z, float strength) implements CustomPacketPayload {
    public static final Type<Level39QuakePayload> TYPE = new Type<>(Backrooms.id("level39_quake"));
    public static final StreamCodec<ByteBuf, Level39QuakePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Level39QuakePayload::x,
            ByteBufCodecs.VAR_INT, Level39QuakePayload::y,
            ByteBufCodecs.VAR_INT, Level39QuakePayload::z,
            ByteBufCodecs.FLOAT, Level39QuakePayload::strength,
            Level39QuakePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
