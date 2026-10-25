package com.rickr.backrooms.camera;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.FlashlightStatesPayload;
import com.rickr.backrooms.world.BackroomsLevel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tells every player in a Backrooms dimension whose flashlight is on, so each client can draw the
 * other players' beams (with real Veil lights).
 *
 * <p>Every server tick we build a snapshot per Backrooms dimension (player -> flashlight on) and
 * compare it with the last one we sent. Only when something changed (someone toggled, a battery
 * died, someone joined or left the floor) do we send the new snapshot to everyone on that floor.
 * {@link CameraService} stays untouched: we just read {@link CameraData#flashlight()}.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class FlashlightBroadcaster {
    /** What we last sent for each dimension (insertion order = player list order). */
    private static final Map<ResourceKey<Level>, List<FlashlightStatesPayload.Entry>> LAST_SENT = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (BackroomsLevel.fromDimension(level.dimension()) == null) {
                continue;
            }
            List<FlashlightStatesPayload.Entry> snapshot = snapshot(level);
            List<FlashlightStatesPayload.Entry> previous = LAST_SENT.get(level.dimension());
            if (!snapshot.equals(previous)) {
                LAST_SENT.put(level.dimension(), snapshot);
                PacketDistributor.sendToPlayersInDimension(level, new FlashlightStatesPayload(snapshot));
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendCurrent(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendCurrent(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendCurrent(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_SENT.clear();
    }

    /** Sends the player the snapshot of the floor they are on now (an empty one outside the Backrooms). */
    private static void sendCurrent(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<FlashlightStatesPayload.Entry> snapshot = BackroomsLevel.fromDimension(level.dimension()) != null
                ? snapshot(level) : List.of();
        PacketDistributor.sendToPlayer(player, new FlashlightStatesPayload(snapshot));
    }

    private static List<FlashlightStatesPayload.Entry> snapshot(ServerLevel level) {
        List<ServerPlayer> players = level.players();
        List<FlashlightStatesPayload.Entry> entries = new ArrayList<>(players.size());
        for (ServerPlayer player : players) {
            entries.add(new FlashlightStatesPayload.Entry(player.getUUID(), CameraService.get(player).flashlight()));
        }
        return entries;
    }

    private FlashlightBroadcaster() {}
}
