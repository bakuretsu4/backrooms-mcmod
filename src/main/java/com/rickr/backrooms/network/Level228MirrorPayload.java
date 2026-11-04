package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client, Level 228, testing only ({@code /backrooms mirror ...}, see
 * command/Level228MirrorCommand): the reflections are client-side (client/level228), so the
 * server just tells the player's client to make one of its reflections lie next, or which render
 * stage to draw them in.
 *
 * @param kind one of the constants below
 */
public record Level228MirrorPayload(int kind) implements CustomPacketPayload {
    /** The next close reflection of you on screen replays your movements late for a few seconds. */
    public static final int LIE_LAG = 0;
    /** ... keeps its head turned to you until you look straight at it. */
    public static final int LIE_LOOK = 1;
    /** ... turns and walks off down the reflected corridor. */
    public static final int LIE_WALK = 2;
    /** ... stays standing when you move, and is gone the next time you look. */
    public static final int LIE_STILL = 3;
    /** Draw the reflections after the entities (the default). */
    public static final int STAGE_ENTITIES = 4;
    /** ... after the block entities. */
    public static final int STAGE_BLOCK_ENTITIES = 5;
    /** ... right after the solid blocks. */
    public static final int STAGE_SOLID_BLOCKS = 6;

    public static final Type<Level228MirrorPayload> TYPE = new Type<>(Backrooms.id("level228_mirror"));
    public static final StreamCodec<ByteBuf, Level228MirrorPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Level228MirrorPayload::kind,
            Level228MirrorPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
