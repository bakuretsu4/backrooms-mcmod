package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> owning client: your sprint energy (in ticks, 0..300) and whether you're exhausted.
 */
public record EnergyPayload(int energy, boolean exhausted) implements CustomPacketPayload {
    public static final Type<EnergyPayload> TYPE = new Type<>(Backrooms.id("energy"));
    public static final StreamCodec<ByteBuf, EnergyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EnergyPayload::energy,
            ByteBufCodecs.BOOL, EnergyPayload::exhausted,
            EnergyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
