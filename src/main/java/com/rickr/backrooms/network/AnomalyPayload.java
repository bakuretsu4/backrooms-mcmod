package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The anomalous area you're in (if any) and the ones close by, so the client can play their sounds,
 * tint the picture, etc. Sent when the current site changes and refreshed every second.
 */
public record AnomalyPayload(Optional<AnomalySite> current, List<AnomalySite> nearby) implements CustomPacketPayload {
    public static final Type<AnomalyPayload> TYPE = new Type<>(Backrooms.id("anomaly"));
    public static final StreamCodec<ByteBuf, AnomalyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(AnomalySite.STREAM_CODEC), AnomalyPayload::current,
            AnomalySite.STREAM_CODEC.apply(ByteBufCodecs.list(32)), AnomalyPayload::nearby,
            AnomalyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
