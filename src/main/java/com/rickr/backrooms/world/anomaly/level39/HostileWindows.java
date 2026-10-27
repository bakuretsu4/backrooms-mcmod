package com.rickr.backrooms.world.anomaly.level39;

import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level39AnomalyPlan;
import com.rickr.backrooms.world.layout.Level39Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An infested stretch of the superstructure's facade (canon: its walls are infested with Window
 * entities, the most hostile of their kind). Once a second, for everyone walking near the wall, the
 * inhabited windows of the two lowest storeys within {@value #WAKE_RANGE} blocks wake up: a
 * {@link WindowShadow} appears in the glass and takes it from there.
 *
 * <p>Which windows are inhabited is a pure function of the seed ({@code Level39AnomalyPlan
 * .windowInhabited}), so the same windows always wake; boarded and bricked windows never do.
 */
final class HostileWindows implements AnomalyBehavior {
    /** A window wakes when someone's eyes come this close to the middle of its glass. */
    static final double WAKE_RANGE = 10.0;
    /** No more than this many awake windows around one person at a time. */
    private static final int MAX_AWAKE_NEAR = 3;

    @Override
    public void siteTick(ServerLevel level, AnomalySite site, long seed) {
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !site.near(player.getBlockX(), player.getBlockZ(), 2)
                    || player.getY() > Level39Sites.glassBottom(Level39Sites.HUNTING_STOREYS) + 4) {
                continue;
            }
            wakeAround(level, site, seed, player, false);
        }
    }

    /**
     * Wakes the inhabited windows near {@code player}. With {@code force} the nearest lit window
     * wakes even if nobody lives in it (the test command).
     */
    static int wakeAround(ServerLevel level, AnomalySite site, long seed, ServerPlayer player, boolean force) {
        Vec3 eye = player.getEyePosition();
        int awake = level.getEntitiesOfClass(WindowShadow.class, player.getBoundingBox().inflate(16.0)).size();
        if (awake >= MAX_AWAKE_NEAR && !force) {
            return 0;
        }
        int centre = Level39Sites.bayAt(site, Level39Sites.along(site, eye.x, eye.z));
        int first = Math.max(Level39Sites.firstBay(site), centre - 2);
        int last = Math.min(Level39Sites.lastBay(site), centre + 2);
        int woken = 0;
        double bestD = Double.MAX_VALUE;
        int bestBay = Integer.MIN_VALUE;
        int bestStorey = 0;
        for (int bay = first; bay <= last; bay++) {
            for (int storey = 0; storey < Level39Sites.HUNTING_STOREYS; storey++) {
                double along = Level39Sites.bayCentre(site, bay);
                Vec3 glass = new Vec3(Level39Sites.worldX(site, along, 0.0), Level39Sites.glassBottom(storey) + 2.5,
                        Level39Sites.worldZ(site, along, 0.0));
                double d = eye.distanceTo(glass);
                if (d > WAKE_RANGE || !lit(level, site, seed, bay, storey)) {
                    continue;
                }
                if (force) {
                    if (d < bestD) {
                        bestD = d;
                        bestBay = bay;
                        bestStorey = storey;
                    }
                    continue;
                }
                if (!Level39AnomalyPlan.windowInhabited(seed, site, bay, storey) || occupied(level, glass)) {
                    continue;
                }
                WindowShadow.spawn(level, site, bay, storey);
                woken++;
                if (awake + woken >= MAX_AWAKE_NEAR) {
                    return woken;
                }
            }
        }
        if (force && bestBay != Integer.MIN_VALUE) {
            double along = Level39Sites.bayCentre(site, bestBay);
            Vec3 glass = new Vec3(Level39Sites.worldX(site, along, 0.0), Level39Sites.glassBottom(bestStorey) + 2.5,
                    Level39Sites.worldZ(site, along, 0.0));
            if (!occupied(level, glass)) {
                WindowShadow.spawn(level, site, bestBay, bestStorey);
                woken++;
            }
        }
        return woken;
    }

    /** True for a lit window (not boarded, not bricked, not a stair tower) whose chunk is loaded. */
    private static boolean lit(ServerLevel level, AnomalySite site, long seed, int bay, int storey) {
        int column = Level39Sites.glassColumn(site);
        int along = Level39Sites.bayColumn(site, bay);
        boolean xSide = Level39Sites.xSide(site);
        int x = xSide ? column : along;
        int z = xSide ? along : column;
        int y = Level39Sites.glassBottom(storey) + 2;
        return level.isLoaded(new BlockPos(x, y, z)) && Level39Layout.facadeWindowAt(seed, x, y, z);
    }

    private static boolean occupied(ServerLevel level, Vec3 glass) {
        return !level.getEntitiesOfClass(WindowShadow.class, new AABB(glass, glass).inflate(1.0, 3.0, 1.0)).isEmpty();
    }
}
