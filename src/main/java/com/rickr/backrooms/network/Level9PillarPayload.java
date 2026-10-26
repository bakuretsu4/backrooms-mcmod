package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client, Level 9: the Mangled fog's black pillar (see
 * {@code world/anomaly/level9/MangledFog}). The pillar is only drawn by the clients (it is far off
 * in the fog, where real blocks would be fogged away), so they need to know where it stands.
 *
 * @param phase one of the constants below
 * @param x     the column the pillar stands on (and its crater is in)
 * @param z     see x
 */
public record Level9PillarPayload(int phase, int x, int z) implements CustomPacketPayload {
    /** No pillar (or it has faded away). */
    public static final int NONE = 0;
    /** The pillar stands at (x, z). */
    public static final int STANDING = 1;
    /** It just vanished, leaving a crater at (x, z): the ground shakes. */
    public static final int CRATER = 2;

    public static final Type<Level9PillarPayload> TYPE = new Type<>(Backrooms.id("level9_pillar"));
    public static final StreamCodec<ByteBuf, Level9PillarPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Level9PillarPayload::phase,
            ByteBufCodecs.VAR_INT, Level9PillarPayload::x,
            ByteBufCodecs.VAR_INT, Level9PillarPayload::z,
            Level9PillarPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
