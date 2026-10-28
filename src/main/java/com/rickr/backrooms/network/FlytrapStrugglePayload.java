package com.rickr.backrooms.network;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.VenusFlytrap;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Client -> server: the player trapped inside a giant Venus flytrap pressed attack or jump (one
 * struggle). Sent by client/Level227FloraClient, which takes the presses straight off the keys,
 * because from inside the trap the game's own attack handling can't be relied on to hit anything.
 *
 * <p>Registered here rather than in ModNetwork so the Level 227 flora stays in its own files.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public record FlytrapStrugglePayload() implements CustomPacketPayload {
    public static final FlytrapStrugglePayload INSTANCE = new FlytrapStrugglePayload();
    public static final Type<FlytrapStrugglePayload> TYPE = new Type<>(Backrooms.id("flytrap_struggle"));
    public static final StreamCodec<ByteBuf, FlytrapStrugglePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToServer(TYPE, STREAM_CODEC, (payload, context) -> {
            // Only counts for the trap the sender is actually inside (the server checks again).
            if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof VenusFlytrap trap) {
                trap.struggle(player);
            }
        });
    }
}
