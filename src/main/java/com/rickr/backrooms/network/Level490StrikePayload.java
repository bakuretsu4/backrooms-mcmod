package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client in Level 490: lightning struck at (x, z). Every client builds the same bolt from
 * {@code seed} (client/Level490Lightning) and hears the thunder as late as its own distance makes it.
 */
public record Level490StrikePayload(long seed, int x, int z, int kind) implements CustomPacketPayload {
    /** A bolt from the storm down into the cloud sea. */
    public static final int BOLT = 0;
    /** Lightning inside the clouds: the clouds light up, no bolt shows. */
    public static final int CLOUD = 1;
    /** A bolt that strikes one of the colossal chains. */
    public static final int CHAIN = 2;

    public static final Type<Level490StrikePayload> TYPE = new Type<>(Backrooms.id("level490_strike"));
    public static final StreamCodec<ByteBuf, Level490StrikePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Level490StrikePayload::seed,
            ByteBufCodecs.VAR_INT, Level490StrikePayload::x,
            ByteBufCodecs.VAR_INT, Level490StrikePayload::z,
            ByteBufCodecs.VAR_INT, Level490StrikePayload::kind,
            Level490StrikePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
