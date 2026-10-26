package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: step of noclipping into the Backrooms (client/intro/NoclipTransition): the
 * picture fails like a broken video stream and the real world smears into Level 0.
 */
public record IntroCutscenePayload(int phase) implements CustomPacketPayload {
    /** Still in the real world: the picture stutters and crumbles into low-bitrate blocks. */
    public static final int FREEZE = 0;
    /** Just moved to Level 0: hold the broken frame while it loads, then mosh it into Level 0. */
    public static final int ARRIVED = 1;

    public static final Type<IntroCutscenePayload> TYPE = new Type<>(Backrooms.id("intro_cutscene"));
    public static final StreamCodec<ByteBuf, IntroCutscenePayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(IntroCutscenePayload::new, IntroCutscenePayload::phase);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
