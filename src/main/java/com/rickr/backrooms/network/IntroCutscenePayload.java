package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: step of the "noclip" intro, the Kane Pixels moment where you are filming in
 * the real world, the footage breaks up, and you drop through into the Backrooms.
 */
public record IntroCutscenePayload(int phase) implements CustomPacketPayload {
    /** Still in the real world: the camcorder starts recording, then reality glitches apart. */
    public static final int FILMING = 0;
    /** Just teleported to Level 0: fall through the dark and crash through the ceiling. */
    public static final int FALLING = 1;

    public static final Type<IntroCutscenePayload> TYPE = new Type<>(Backrooms.id("intro_cutscene"));
    public static final StreamCodec<ByteBuf, IntroCutscenePayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(IntroCutscenePayload::new, IntroCutscenePayload::phase);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
