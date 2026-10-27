package com.rickr.backrooms.world.anomaly.level39;

import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.layout.Level39AnomalyPlan;
import com.rickr.backrooms.world.layout.Level39HouseSpec;
import com.rickr.backrooms.world.layout.Level39Houses;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

/**
 * Canon: some houses are bigger on the inside. A BIGGER_INSIDE house's front door opens into a
 * small entry hall; a couple of steps in, you are no longer in the house you saw from the street
 * but in its far larger interior, built in the copy region far to the east
 * ({@link Level39AnomalyPlan#BIG_DX} blocks away). The entry hall is built identically in both
 * places and the move keeps where you look and how you walk ({@link Teleports#shift}), so you
 * never see the jump. Walk back out through the hall and the same thing happens the other way:
 * you leave by the real house's front door.
 *
 * <p>The hall is {@code Level39Houses.entryHall}: a rectangle, the direction "inward" (from the
 * door into the house) and a shift line across it. Crossing the line inward by
 * {@value #MARGIN} blocks in the real house takes you in; crossing it outward by the same margin
 * in the interior brings you back. The gap between the two keeps you from bouncing.
 */
final class BiggerInside implements AnomalyBehavior {
    private static final double MARGIN = 0.35;
    private static final int CACHE_LIMIT = 64;
    /** Chunks kept ready round the other side's hall (radius in chunks). */
    private static final int PRELOAD_RADIUS = 3;

    private final Map<UUID, Double> lastDepth = new HashMap<>();
    private final Map<AnomalySite, Level39HouseSpec> houses = new LinkedHashMap<>();

    @Override
    public void enter(ServerPlayer player, AnomalySite site, long seed) {
        Level39HouseSpec h = house(seed, site);
        if (h != null) {
            lastDepth.put(player.getUUID(), depth(Level39Houses.entryHall(h), player, site));
        }
    }

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        Level39HouseSpec h = house(seed, site);
        if (h == null) {
            return;
        }
        Level39Houses.EntryHall hall = Level39Houses.entryHall(h);
        boolean copy = Level39AnomalyPlan.isCopy(site.minX());
        if (ticksInside % 20 == 0) {
            keepOtherSideLoaded(player, hall, copy);
        }
        double d = depth(hall, player, site);
        Double before = lastDepth.put(player.getUUID(), d);
        if (before == null || !inHall(hall, player, site)) {
            return;
        }
        if (!copy && before < MARGIN && d >= MARGIN) {
            Teleports.shift(player, Level39AnomalyPlan.BIG_DX, 0.0, 0.0);
        } else if (copy && before > -MARGIN && d <= -MARGIN) {
            Teleports.shift(player, -Level39AnomalyPlan.BIG_DX, 0.0, 0.0);
        }
    }

    /**
     * Keeps the chunks round the hall on the OTHER side generated and loaded while you are near, so
     * when you step through, the place you arrive in is already there (no blank world for a moment).
     */
    private static void keepOtherSideLoaded(ServerPlayer player, Level39Houses.EntryHall hall, boolean copy) {
        int x = (hall.x0() + hall.x1()) / 2 + (copy ? 0 : Level39AnomalyPlan.BIG_DX);
        BlockPos pos = new BlockPos(x, hall.feetY(), (hall.z0() + hall.z1()) / 2);
        player.serverLevel().getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(pos), PRELOAD_RADIUS, pos);
    }

    @Override
    public void leave(ServerPlayer player, AnomalySite site, long seed) {
        lastDepth.remove(player.getUUID());
    }

    @Nullable
    private Level39HouseSpec house(long seed, AnomalySite site) {
        Level39HouseSpec h = houses.get(site);
        if (h == null) {
            if (houses.size() > CACHE_LIMIT) {
                houses.clear();
            }
            h = Level39AnomalyPlan.houseOf(seed, site);
            if (h != null) {
                houses.put(site, h);
            }
        }
        return h;
    }

    /** How far past the shift line (blocks, inward positive) the player is, in the hall's own coordinates. */
    private static double depth(Level39Houses.EntryHall hall, ServerPlayer player, AnomalySite site) {
        Direction in = hall.inward();
        int step = in.getAxisDirection().getStep();
        double c = in.getAxis() == Direction.Axis.X ? originalX(player.getX(), site) : player.getZ();
        // shiftLine is the first block row past the line; its near edge is the line itself.
        double line = step > 0 ? hall.shiftLine() : hall.shiftLine() + 1;
        return (c - line) * step;
    }

    private static boolean inHall(Level39Houses.EntryHall hall, ServerPlayer player, AnomalySite site) {
        double x = originalX(player.getX(), site);
        double z = player.getZ();
        return x >= hall.x0() - 0.3 && x <= hall.x1() + 1.3 && z >= hall.z0() - 0.3 && z <= hall.z1() + 1.3
                && player.getY() >= hall.feetY() - 0.6 && player.getY() < hall.feetY() + 2.5;
    }

    /** The hall is described in the real house's coordinates; in the interior, shift back to compare. */
    private static double originalX(double x, AnomalySite site) {
        return Level39AnomalyPlan.isCopy(site.minX()) ? x - Level39AnomalyPlan.BIG_DX : x;
    }
}
