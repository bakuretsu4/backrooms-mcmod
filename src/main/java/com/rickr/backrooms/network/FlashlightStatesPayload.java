package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: whose head flashlight is on, for every player in your Backrooms dimension.
 * Each packet is a full snapshot, so the client simply replaces what it knew before. The client
 * uses it to draw other players' flashlight beams.
 *
 * <p>The received state lives in {@link #CLIENT_ON}: a plain Java set with no client imports, so
 * this class is safe to load on a dedicated server too.
 */
public record FlashlightStatesPayload(List<Entry> entries) implements CustomPacketPayload {
    /** One player's flashlight state. */
    public record Entry(UUID player, boolean on) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Entry::player,
                ByteBufCodecs.BOOL, Entry::on,
                Entry::new);
    }

    public static final Type<FlashlightStatesPayload> TYPE = new Type<>(Backrooms.id("flashlight_states"));
    public static final StreamCodec<ByteBuf, FlashlightStatesPayload> STREAM_CODEC = StreamCodec.composite(
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(256)), FlashlightStatesPayload::entries,
            FlashlightStatesPayload::new);

    /** Client side: UUIDs of the players whose flashlight is currently on (written on the main thread). */
    public static final Set<UUID> CLIENT_ON = ConcurrentHashMap.newKeySet();

    /** Called by the packet handler on the client: replace our snapshot with the new one. */
    public void applyOnClient() {
        CLIENT_ON.clear();
        for (Entry entry : entries) {
            if (entry.on()) {
                CLIENT_ON.add(entry.player());
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
