package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: this player's camcorder state.
 *
 * @param battery    0..100
 * @param nightShot  infrared mode on
 * @param flashlight head flashlight on
 * @param exitSignal 0..1, how close the nearest exit is (the static calms down as you approach)
 * @param tapeTicks  ticks recorded on the current tape (HUD counter)
 */
public record CameraSyncPayload(float battery, boolean nightShot, boolean flashlight, float exitSignal, long tapeTicks)
        implements CustomPacketPayload {
    public static final Type<CameraSyncPayload> TYPE = new Type<>(Backrooms.id("camera_sync"));
    public static final StreamCodec<ByteBuf, CameraSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, CameraSyncPayload::battery,
            ByteBufCodecs.BOOL, CameraSyncPayload::nightShot,
            ByteBufCodecs.BOOL, CameraSyncPayload::flashlight,
            ByteBufCodecs.FLOAT, CameraSyncPayload::exitSignal,
            ByteBufCodecs.VAR_LONG, CameraSyncPayload::tapeTicks,
            CameraSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
