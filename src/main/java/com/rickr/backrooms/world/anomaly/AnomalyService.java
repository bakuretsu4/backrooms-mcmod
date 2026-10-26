package com.rickr.backrooms.world.anomaly;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.AnomalyPayload;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Runs the anomalies on the server: works out which site each player is standing in (a pure
 * function of the floor plan, so it costs no world access), calls that kind's
 * {@link AnomalyBehavior} on entry, every tick and on exit, gives every site near a player its
 * once-a-second {@link AnomalyBehavior#siteTick}, and keeps the client informed.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class AnomalyService {
    /** Sites this close to a player (blocks) get their siteTick and are sent to the client. */
    public static final int SITE_TICK_RANGE = 64;
    private static final int NEARBY_SYNC_RANGE = 48;

    private static final class State {
        @Nullable
        AnomalySite site;
        @Nullable
        BackroomsLevel floor;
        long seed;
        int ticks;
        List<AnomalySite> lastNearby = List.of();
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    /** The site the player was in at their last tick, or null. */
    @Nullable
    public static AnomalySite currentSite(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state == null ? null : state.site;
    }

    /** The site covering (x, z) on the player's current level, or null (also outside the Backrooms). */
    @Nullable
    public static AnomalySite siteAt(ServerLevel level, int x, int z) {
        BackroomsLevel floor = BackroomsLevel.fromDimension(level.dimension());
        if (floor == null) {
            return null;
        }
        return floor.layout().anomalyAt(BackroomsSeeds.layoutSeed(level, floor), x, z);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        ServerLevel level = player.serverLevel();
        BackroomsLevel floor = BackroomsLevel.fromDimension(level.dimension());
        AnomalySite site = null;
        long seed = 0;
        if (floor != null && !player.isSpectator()) {
            seed = BackroomsSeeds.layoutSeed(level, floor);
            site = floor.layout().anomalyAt(seed, player.getBlockX(), player.getBlockZ());
        }
        boolean changed = !Objects.equals(site, state.site) || floor != state.floor;
        if (changed) {
            if (state.site != null && state.floor != null) {
                AnomalyBehavior old = Anomalies.get(state.site.kind());
                if (old != null) {
                    old.leave(player, state.site, state.seed);
                }
            }
            state.site = site;
            state.floor = floor;
            state.seed = seed;
            state.ticks = 0;
            if (site != null) {
                AnomalyBehavior now = Anomalies.get(site.kind());
                if (now != null) {
                    now.enter(player, site, seed);
                }
            }
        }
        if (site != null) {
            AnomalyBehavior now = Anomalies.get(site.kind());
            if (now != null) {
                now.tick(player, site, seed, state.ticks);
            }
            state.ticks++;
        }
        if (changed || player.tickCount % 20 == 0) {
            sync(player, state, floor, seed, changed);
        }
    }

    private static void sync(ServerPlayer player, State state, @Nullable BackroomsLevel floor, long seed, boolean force) {
        List<AnomalySite> nearby = new ArrayList<>();
        if (floor != null) {
            floor.layout().anomaliesNear(seed, player.getBlockX(), player.getBlockZ(), NEARBY_SYNC_RANGE, nearby::add);
        }
        if (force || !nearby.equals(state.lastNearby)) {
            state.lastNearby = List.copyOf(nearby);
            PacketDistributor.sendToPlayer(player, new AnomalyPayload(Optional.ofNullable(state.site), state.lastNearby));
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20 != 0 || level.players().isEmpty()) {
            return;
        }
        BackroomsLevel floor = BackroomsLevel.fromDimension(level.dimension());
        if (floor == null) {
            return;
        }
        long seed = BackroomsSeeds.layoutSeed(level, floor);
        Set<AnomalySite> sites = new LinkedHashSet<>();
        for (ServerPlayer player : level.players()) {
            floor.layout().anomaliesNear(seed, player.getBlockX(), player.getBlockZ(), SITE_TICK_RANGE, sites::add);
        }
        for (AnomalySite site : sites) {
            AnomalyBehavior behavior = Anomalies.get(site.kind());
            if (behavior != null) {
                behavior.siteTick(level, site, seed);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            State state = STATES.remove(player.getUUID());
            if (state != null && state.site != null) {
                AnomalyBehavior old = Anomalies.get(state.site.kind());
                if (old != null) {
                    old.leave(player, state.site, state.seed);
                }
            }
        }
    }

    private AnomalyService() {}
}
