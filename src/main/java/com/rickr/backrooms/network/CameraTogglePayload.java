package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the player pressed a camcorder key. The server decides whether it turns on. */
public record CameraTogglePayload(int toggle) implements CustomPacketPayload {
    public static final int NIGHT_SHOT = 0;
    public static final int FLASHLIGHT = 1;

    public static final Type<CameraTogglePayload> TYPE = new Type<>(Backrooms.id("camera_toggle"));
    public static final StreamCodec<ByteBuf, CameraTogglePayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(CameraTogglePayload::new, CameraTogglePayload::toggle);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
