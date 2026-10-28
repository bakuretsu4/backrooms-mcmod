package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a Level 0 local power outage started, or (with {@code restore}) ended. Sent by
 * world/FixtureOutages; the client's client/light/Level0Power makes the lamps die and come back.
 *
 * @param restore    true = "outage {@code id} is over, re-strike its lamps now" (the rest is unused)
 * @param id         the outage's number (from FixtureOutages)
 * @param pattern    FixtureOutages.Pattern's ordinal: how the lamps die
 * @param x          middle of a circle outage
 * @param z          middle of a circle outage
 * @param radius     radius of a circle outage (blocks); unused for lists
 * @param elapsedMs  how long ago it started (non-zero for players who arrive while it runs)
 * @param durationMs how long it lasts from its start; 0 or less = until restored
 * @param blocks     list outages: the fixture blocks (BlockPos.asLong); empty for circles
 * @param delaysMs   list outages: when each of those dies, ms after the start
 */
public record LocalOutagePayload(boolean restore, int id, int pattern, double x, double z, float radius,
                                 long elapsedMs, int durationMs, long[] blocks, int[] delaysMs) implements CustomPacketPayload {
    public static final Type<LocalOutagePayload> TYPE = new Type<>(Backrooms.id("local_outage"));
    public static final StreamCodec<ByteBuf, LocalOutagePayload> STREAM_CODEC = StreamCodec.of(LocalOutagePayload::write, LocalOutagePayload::read);

    /** The message that ends outage {@code id}. */
    public static LocalOutagePayload restore(int id) {
        return new LocalOutagePayload(true, id, 0, 0.0, 0.0, 0.0f, 0L, 0, new long[0], new int[0]);
    }

    private static void write(ByteBuf buf, LocalOutagePayload p) {
        buf.writeBoolean(p.restore);
        ByteBufCodecs.VAR_INT.encode(buf, p.id);
        if (p.restore) {
            return;
        }
        ByteBufCodecs.VAR_INT.encode(buf, p.pattern);
        buf.writeDouble(p.x);
        buf.writeDouble(p.z);
        buf.writeFloat(p.radius);
        ByteBufCodecs.VAR_LONG.encode(buf, p.elapsedMs);
        ByteBufCodecs.VAR_INT.encode(buf, p.durationMs);
        ByteBufCodecs.VAR_INT.encode(buf, p.blocks.length);
        for (int i = 0; i < p.blocks.length; i++) {
            buf.writeLong(p.blocks[i]);
            ByteBufCodecs.VAR_INT.encode(buf, i < p.delaysMs.length ? p.delaysMs[i] : 0);
        }
    }

    private static LocalOutagePayload read(ByteBuf buf) {
        boolean restore = buf.readBoolean();
        int id = ByteBufCodecs.VAR_INT.decode(buf);
        if (restore) {
            return restore(id);
        }
        int pattern = ByteBufCodecs.VAR_INT.decode(buf);
        double x = buf.readDouble();
        double z = buf.readDouble();
        float radius = buf.readFloat();
        long elapsed = ByteBufCodecs.VAR_LONG.decode(buf);
        int duration = ByteBufCodecs.VAR_INT.decode(buf);
        int count = Math.min(ByteBufCodecs.VAR_INT.decode(buf), 4096);
        long[] blocks = new long[count];
        int[] delays = new int[count];
        for (int i = 0; i < count; i++) {
            blocks[i] = buf.readLong();
            delays[i] = ByteBufCodecs.VAR_INT.decode(buf);
        }
        return new LocalOutagePayload(false, id, pattern, x, z, radius, elapsed, duration, blocks, delays);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
