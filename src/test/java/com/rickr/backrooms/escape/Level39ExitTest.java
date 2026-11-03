package com.rickr.backrooms.escape;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rickr.backrooms.registry.Level39Blocks;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level39.Level39Sites;
import com.rickr.backrooms.world.anomaly.level39.WindowShadow;
import com.rickr.backrooms.world.layout.Level39AnomalyPlan;
import com.rickr.backrooms.world.layout.Level39Layout;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.SplittableRandom;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

/**
 * Level 39's way out is a ground-storey mill window with a Window in it: it grabs you and, if you
 * hold on, pulls you through to Level 94. Windows up the wall can't grab. These pin down that
 * every infested stretch has such a window, and that {@code /backrooms locate}, the exit sound and
 * the camcorder (all {@code exitPosition}) lead you to open ground in front of the nearest one.
 */
class Level39ExitTest {
    private static final LevelLayout LAYOUT = BackroomsLevel.LEVEL_39.layout();
    /** Towns checked per seed (cell indices). */
    private static final int[][] CELLS = {{0, 0}, {3, -2}, {-5, 7}};

    @Test
    void everyInfestedStretchHasAGroundWindowThatGrabs() {
        int sites = 0;
        int lit = 0;
        int inhabited = 0;
        for (long worldSeed : Escape.SEEDS) {
            long seed = Escape.layoutSeed(worldSeed, BackroomsLevel.LEVEL_39);
            for (int[] cell : CELLS) {
                Set<AnomalySite> found = windowSitesOf(seed, cell[0], cell[1]);
                assertFalse(found.isEmpty(), "seed " + worldSeed + " town " + cell[0] + "," + cell[1] + " has no infested stretch");
                for (AnomalySite site : found) {
                    sites++;
                    boolean grabs = false;
                    for (int bay = Level39Sites.firstBay(site); bay <= Level39Sites.lastBay(site); bay++) {
                        for (int storey = 0; storey < Level39Sites.HUNTING_STOREYS; storey++) {
                            if (!Level39AnomalyPlan.windowLit(seed, site, bay, storey)) {
                                continue;
                            }
                            lit++;
                            if (Level39AnomalyPlan.windowInhabited(seed, site, bay, storey)) {
                                inhabited++;
                                grabs |= storey == 0;
                            }
                        }
                    }
                    assertTrue(grabs, "seed " + worldSeed + ": " + site + " has no inhabited, lit ground-storey window");
                }
            }
        }
        // The guarantee only adds a Window where a stretch had none on the ground storey: the
        // seeded share (55%) of inhabited lit windows barely moves.
        double share = inhabited / (double) lit;
        assertTrue(share > 0.5 && share < 0.62, "inhabited share of lit windows " + share + " over " + sites + " stretches");
    }

    @Test
    void locatedExitIsInFrontOfANearestGroundWindowThatGrabs() {
        for (long worldSeed : Escape.SEEDS) {
            long seed = Escape.layoutSeed(worldSeed, BackroomsLevel.LEVEL_39);
            SplittableRandom random = new SplittableRandom(worldSeed ^ 0x39E1L);
            for (int k = 0; k < 2; k++) {
                int x = random.nextInt(-20, 21) * Level39Layout.CELL + random.nextInt(-400, 401);
                int z = random.nextInt(-20, 21) * Level39Layout.CELL + random.nextInt(-400, 401);
                Escape.Arrival arrival = Escape.arrive(BackroomsLevel.LEVEL_39, seed, x, z, null);
                String where = "seed " + worldSeed + ", from " + arrival.x() + " " + arrival.z();
                int[] exit = LAYOUT.exitPosition(seed, arrival.x(), arrival.z());
                int ex = exit[0];
                int ez = exit[1];
                where += ", located exit " + ex + " " + ez;

                // Open town ground, feet low enough for the grab (WindowShadow.canGrab).
                assertTrue(LAYOUT.isWalkable(seed, ex, ez), where + ": not open town ground");
                assertFalse(Level39Layout.inStairTower(ex, ez), where + ": inside a stair tower");
                double feet = LAYOUT.feetY(seed, ex, ez);
                assertTrue(feet >= Level39Layout.GROUND_Y + 0.5 && feet < Level39Sites.glassBottom(0) + Level39Sites.GLASS_HEIGHT,
                        where + ": feet at " + feet);

                // Within grab reach of an inhabited, lit ground-storey window.
                int bay = Escape.grabWindowAt(LAYOUT, seed, ex, ez);
                assertNotEquals(Integer.MIN_VALUE, bay, where + ": no inhabited, lit ground window can grab here");
                AnomalySite site = LAYOUT.anomalyAt(seed, ex, ez);
                double front = Level39Sites.inFront(site, ex + 0.5, ez + 0.5);
                assertTrue(front > 0.0 && front < WindowShadow.GRAB_RANGE, where + ": " + front + " in front of the glass");

                // The real blocks: that window is glass (not boarded, bricked or a tower) on all its rows.
                int glass = Level39Sites.glassColumn(site);
                for (int side = -1; side <= 1; side++) {
                    int along = Level39Sites.bayColumn(site, bay) + side;
                    int gx = Level39Sites.xSide(site) ? glass : along;
                    int gz = Level39Sites.xSide(site) ? along : glass;
                    for (int y = Level39Sites.glassBottom(0); y < Level39Sites.glassBottom(0) + Level39Sites.GLASS_HEIGHT; y++) {
                        BlockState state = blockAt(seed, gx, y, gz);
                        assertTrue(state != null && state.is(Level39Blocks.MILL_WINDOW.get()),
                                where + ": window block at " + gx + " " + y + " " + gz + " is " + state);
                    }
                }

                // The nearest such window in this town: none with open ground right in front is nearer.
                double chosen = frontDistance(site, bay, arrival.x(), arrival.z());
                double best = nearestGrabWindow(seed, arrival.x(), arrival.z());
                assertTrue(chosen <= best + 1e-6, where + ": a nearer ground window that grabs is " + best + " away, not " + chosen);

                // And you can walk there from where you arrived.
                Escape.Outcome outcome = Escape.check(arrival, new Escape.Exit("the located exit spot", s -> false,
                        (w, gx, gy, gz, fromY) -> gx == ex && gz == ez));
                assertTrue(outcome.ok(), where + ": " + outcome.describe());
            }
        }
    }

    /** The infested stretches along the four walls of one town. */
    private static Set<AnomalySite> windowSitesOf(long seed, int cellX, int cellZ) {
        Set<AnomalySite> out = new LinkedHashSet<>();
        LAYOUT.anomaliesNear(seed, cellX * Level39Layout.CELL, cellZ * Level39Layout.CELL, 500, s -> {
            if (Level39Sites.HOSTILE_WINDOWS.equals(s.kind())) {
                out.add(s);
            }
        });
        return out;
    }

    /** Distance from (x, z) to the floor point 1.5 blocks in front of the middle of a bay's window. */
    private static double frontDistance(AnomalySite site, int bay, int x, int z) {
        double along = Level39Sites.bayCentre(site, bay);
        double dx = Level39Sites.worldX(site, along, 1.5) - x;
        double dz = Level39Sites.worldZ(site, along, 1.5) - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Brute force over the town's stretches: the nearest grabbing ground window with open ground right in front. */
    private static double nearestGrabWindow(long seed, int x, int z) {
        double best = Double.MAX_VALUE;
        for (AnomalySite site : windowSitesOf(seed, Math.floorDiv(x + Level39Layout.CELL / 2, Level39Layout.CELL),
                Math.floorDiv(z + Level39Layout.CELL / 2, Level39Layout.CELL))) {
            for (int bay = Level39Sites.firstBay(site); bay <= Level39Sites.lastBay(site); bay++) {
                double along = Level39Sites.bayColumn(site, bay) + 0.5;
                int fx = (int) Math.floor(Level39Sites.worldX(site, along, 1.5));
                int fz = (int) Math.floor(Level39Sites.worldZ(site, along, 1.5));
                if (Level39AnomalyPlan.windowLit(seed, site, bay, 0) && Level39AnomalyPlan.windowInhabited(seed, site, bay, 0)
                        && LAYOUT.isWalkable(seed, fx, fz)) {
                    best = Math.min(best, frontDistance(site, bay, x, z));
                }
            }
        }
        return best;
    }

    private static BlockState blockAt(long seed, int x, int y, int z) {
        BlockState[] found = new BlockState[1];
        LAYOUT.fillColumn(seed, x, z, (yy, state) -> {
            if (yy == y) {
                found[0] = state;
            }
        });
        return found[0];
    }
}
