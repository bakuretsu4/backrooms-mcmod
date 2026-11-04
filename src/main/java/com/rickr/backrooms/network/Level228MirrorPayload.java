package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client, Level 228, testing only ({@code /backrooms mirror ...}, see
 * command/Level228MirrorCommand): the reflections are client-side (client/level228), so the
 * server just tells the player's client to make one of its reflections lie next, which render
 * stage to draw them in, or to report on them.
 *
 * @param kind one of the constants below
 */
public record Level228MirrorPayload(int kind) implements CustomPacketPayload {
    /** The next close reflection of you on screen replays your movements late for a few seconds (walk). */
    public static final int LIE_LAG = 0;
    /** ... turns its head to look at you while you stand still. */
    public static final int LIE_LOOK = 1;
    /** ... keeps walking a few steps when you stop. */
    public static final int LIE_WALK = 2;
    /** ... stops while you keep walking, and is gone the next time you look. */
    public static final int LIE_STOP = 3;
    /** Draw the reflections after the entities (the default). */
    public static final int STAGE_ENTITIES = 4;
    /** ... after the block entities. */
    public static final int STAGE_BLOCK_ENTITIES = 5;
    /** ... right after the solid blocks. */
    public static final int STAGE_SOLID_BLOCKS = 6;
    /** One of your reflections (out of view) is not there when you turn. */
    public static final int LIE_VANISH = 7;
    /** End the current lie and go back to the normal schedule. */
    public static final int LIE_OFF = 8;
    /** Print stand-in counts, hidden bodies, the lie state and the exit mirrors' flaws. */
    public static final int INFO = 9;

    public static final Type<Level228MirrorPayload> TYPE = new Type<>(Backrooms.id("level228_mirror"));
    public static final StreamCodec<ByteBuf, Level228MirrorPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Level228MirrorPayload::kind,
            Level228MirrorPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
