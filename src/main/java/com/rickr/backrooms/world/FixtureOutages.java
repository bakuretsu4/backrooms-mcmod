package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.LocalOutagePayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Local power outages on Level 0: one room's lamps dying as you walk in, a corridor's tubes going
 * out one by one behind you, a distant district going dark while you watch. Server code calls
 * these; every player in the dimension is told (LocalOutagePayload), and their client makes the
 * lamps pop, stutter, die and later re-strike (client/light/Level0Power). The lamps' vanilla light
 * never changes (no block updates); the outage is purely what the players see and hear.
 *
 * <pre>{@code
 * // The lamps within 9 blocks of the room's middle die as you enter, until you leave:
 * int id = FixtureOutages.start(level, roomCentre, 9.0f, 0, FixtureOutages.Pattern.DIE_STUTTER);
 * ...
 * FixtureOutages.end(level, id); // they re-strike
 *
 * // A corridor's tubes going out one by one behind you (ceiling fixture blocks + delays):
 * FixtureOutages.startList(level, tubeBlocks, new int[] {0, 700, 1400, 2100}, 30_000, FixtureOutages.Pattern.CUT);
 * }</pre>
 *
 * Main-server-thread only (like all world code).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class FixtureOutages {
    /** How the lamps die. They always come back with a short re-strike stutter. */
    public enum Pattern {
        /** All out within a quarter second, in a ragged order (a breaker tripping). */
        CUT,
        /** Each lamp stutters for 0.4-1.2 s, then goes out (a failing supply). */
        DIE_STUTTER,
        /**
         * A dying wave rolling outward from the middle (~18 blocks per second), each tube popping
         * with a short stutter as it is reached. Comes back the other way, from the edge inward.
         */
        WAVE
    }

    /** Nothing lasts longer than this, even if nobody ends it (the clients cap it the same way). */
    public static final int MAX_DURATION_MS = 600_000;

    private record Active(int id, Pattern pattern, double x, double z, float radius, long startMs, long endMs,
                          long[] blocks, int[] delays) {
        boolean covers(BlockPos pos) {
            if (blocks.length == 0) {
                double dx = pos.getX() + 0.5 - x;
                double dz = pos.getZ() + 0.5 - z;
                return dx * dx + dz * dz <= (double) radius * radius;
            }
            // A list covers the area around its lamps (a lamp lights ~4 blocks around it).
            for (long block : blocks) {
                if (Math.abs(BlockPos.getX(block) - pos.getX()) <= 4 && Math.abs(BlockPos.getZ(block) - pos.getZ()) <= 4) {
                    return true;
                }
            }
            return false;
        }

        LocalOutagePayload payload(long now) {
            int duration = endMs == Long.MAX_VALUE ? 0 : (int) Math.min(Integer.MAX_VALUE, endMs - startMs);
            return new LocalOutagePayload(false, id, pattern.ordinal(), x, z, radius, now - startMs, duration, blocks, delays);
        }
    }

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final Map<ResourceKey<Level>, List<Active>> ACTIVE = new HashMap<>();

    /**
     * Cuts the power in a circle. Returns the outage's id (for {@link #end}).
     *
     * @param center     the middle (only x and z matter: Level 0 is one storey, plus its tall rooms)
     * @param radius     blocks; lamps within it die
     * @param durationMs how long until the lamps come back by themselves; 0 or less = until {@link #end}
     *                   (capped at {@link #MAX_DURATION_MS})
     */
    public static int start(ServerLevel level, BlockPos center, float radius, int durationMs, Pattern pattern) {
        return add(level, new Active(NEXT_ID.getAndIncrement(), pattern, center.getX() + 0.5, center.getZ() + 0.5,
                Math.max(0.5f, radius), System.currentTimeMillis(), 0L, new long[0], new int[0]), durationMs);
    }

    /**
     * Cuts the power of exactly these fixtures (BlockPos#asLong of any block of a lamp; one block of
     * a strip takes the whole strip), each after its own delay in ms ({@code dieDelayMs[i]}, 0 when
     * missing). Good for one room (flood-fill it first, so nothing in the next room dies) or a
     * corridor dying lamp by lamp. Returns the outage's id.
     */
    public static int startList(ServerLevel level, long[] fixtureBlocks, int[] dieDelayMs, int durationMs, Pattern pattern) {
        long[] blocks = fixtureBlocks.clone();
        int[] delays = new int[blocks.length];
        double sx = 0.0;
        double sz = 0.0;
        for (int i = 0; i < blocks.length; i++) {
            delays[i] = i < dieDelayMs.length ? Math.max(0, dieDelayMs[i]) : 0;
            sx += BlockPos.getX(blocks[i]) + 0.5;
            sz += BlockPos.getZ(blocks[i]) + 0.5;
        }
        int n = Math.max(1, blocks.length);
        return add(level, new Active(NEXT_ID.getAndIncrement(), pattern, sx / n, sz / n, 0.0f,
                System.currentTimeMillis(), 0L, blocks, delays), durationMs);
    }

    /** Ends an outage now: its lamps re-strike (a WAVE from the edge inward). Unknown ids are ignored. */
    public static void end(ServerLevel level, int id) {
        List<Active> list = ACTIVE.get(level.dimension());
        if (list == null || !list.removeIf(a -> a.id == id)) {
            return;
        }
        PacketDistributor.sendToPlayersInDimension(level, LocalOutagePayload.restore(id));
    }

    /**
     * True if the power is out at {@code pos}: inside a running local outage, or during this
     * floor's blackout event. Use it so a new event never stacks on another.
     */
    public static boolean isOut(ServerLevel level, BlockPos pos) {
        BackroomsLevel floor = BackroomsLevel.fromDimension(level.dimension());
        if (floor != null && LevelEvents.isBlackout(floor)) {
            return true;
        }
        for (Active active : live(level)) {
            if (active.covers(pos)) {
                return true;
            }
        }
        return false;
    }

    /** How many local outages are running in this dimension right now. */
    public static int activeCount(ServerLevel level) {
        return live(level).size();
    }

    private static int add(ServerLevel level, Active active, int durationMs) {
        long end = durationMs > 0 ? active.startMs + Math.min(durationMs, MAX_DURATION_MS) : active.startMs + MAX_DURATION_MS;
        Active timed = new Active(active.id, active.pattern, active.x, active.z, active.radius, active.startMs,
                durationMs > 0 ? end : Long.MAX_VALUE, active.blocks, active.delays);
        live(level).add(timed);
        PacketDistributor.sendToPlayersInDimension(level, timed.payload(active.startMs));
        return active.id;
    }

    /** The running outages of a dimension, without the ones that have run out. */
    private static List<Active> live(ServerLevel level) {
        List<Active> list = ACTIVE.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
        long now = System.currentTimeMillis();
        list.removeIf(a -> now >= Math.min(a.endMs, a.startMs + MAX_DURATION_MS));
        return list;
    }

    /** A player arriving while outages run sees them too (already part way through). */
    private static void sendRunning(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Active active : live(level)) {
            PacketDistributor.sendToPlayer(player, active.payload(now));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendRunning(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendRunning(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    private FixtureOutages() {}
}
