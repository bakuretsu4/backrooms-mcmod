package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: you just arrived on a level; show the camcorder arrival card
 * ({@code client/hud/ArrivalCard}): the level's name, its one-line description and, if given, a
 * short line about how you got there. Only lang keys travel, so each client reads its own language.
 *
 * @param level   the level's name, e.g. "level_2" (the card reads {@code level.backrooms.<level>}
 *                and {@code message.backrooms.arrive.<level>})
 * @param noteKey lang key of the "how you got here" line ("You clipped through reality."), or "" for none
 * @param delayMs extra wait before the card comes up, for arrivals that bring their own fade
 *                (Level 37's white and black openings), so the card isn't hidden under it
 */
public record ArrivalCardPayload(String level, String noteKey, int delayMs) implements CustomPacketPayload {
    public static final Type<ArrivalCardPayload> TYPE = new Type<>(Backrooms.id("arrival_card"));
    public static final StreamCodec<ByteBuf, ArrivalCardPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ArrivalCardPayload::level,
            ByteBufCodecs.STRING_UTF8, ArrivalCardPayload::noteKey,
            ByteBufCodecs.VAR_INT, ArrivalCardPayload::delayMs,
            ArrivalCardPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
