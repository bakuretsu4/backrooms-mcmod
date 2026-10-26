package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> owning client: where the nearest real exit of your level is (sent when it changes), for
 * the subtle hints that point toward it: its faint sound and the marks only NightShot shows
 * ({@code client/hint/ExitHints}). {@code known} is false outside the Backrooms. Only the server
 * knows the floor plan's seed, so the client can't work this out itself.
 */
public record ExitHintPayload(boolean known, int x, int y, int z) implements CustomPacketPayload {
    public static final Type<ExitHintPayload> TYPE = new Type<>(Backrooms.id("exit_hint"));
    public static final StreamCodec<ByteBuf, ExitHintPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ExitHintPayload::known,
            ByteBufCodecs.VAR_INT, ExitHintPayload::x,
            ByteBufCodecs.VAR_INT, ExitHintPayload::y,
            ByteBufCodecs.VAR_INT, ExitHintPayload::z,
            ExitHintPayload::new);

    public static final ExitHintPayload NONE = new ExitHintPayload(false, 0, 0, 0);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
