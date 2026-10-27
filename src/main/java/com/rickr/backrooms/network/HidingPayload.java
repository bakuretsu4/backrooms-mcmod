package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server -> every client: who is hiding in furniture right now. Each packet is a full snapshot
 * (there are only ever a few hiders), sent whenever anyone gets in, gets out or holds their breath.
 * Other clients use it to draw nothing where a hider's body waits; the hider's own client uses
 * its entry for the view inside the furniture.
 */
public record HidingPayload(List<Entry> entries) implements CustomPacketPayload {
    /**
     * One hider.
     *
     * @param entityId   the player's entity id
     * @param anchor     the furniture they're in (so a click on it is known to pull them out)
     * @param camera     where their view sits, inside the furniture
     * @param yaw        the way they look straight out of it
     * @param breathHeld true while they hold their breath
     */
    public record Entry(int entityId, BlockPos anchor, Vec3 camera, float yaw, boolean breathHeld) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.of(
                (buf, e) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, e.entityId());
                    BlockPos.STREAM_CODEC.encode(buf, e.anchor());
                    buf.writeDouble(e.camera().x);
                    buf.writeDouble(e.camera().y);
                    buf.writeDouble(e.camera().z);
                    buf.writeFloat(e.yaw());
                    buf.writeBoolean(e.breathHeld());
                },
                buf -> new Entry(ByteBufCodecs.VAR_INT.decode(buf), BlockPos.STREAM_CODEC.decode(buf),
                        new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readBoolean()));
    }

    public static final Type<HidingPayload> TYPE = new Type<>(Backrooms.id("hiding"));
    public static final StreamCodec<ByteBuf, HidingPayload> STREAM_CODEC = StreamCodec.composite(
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(256)), HidingPayload::entries,
            HidingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
