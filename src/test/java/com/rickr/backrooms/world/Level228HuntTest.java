package com.rickr.backrooms.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rickr.backrooms.world.layout.Level228Plan;
import com.rickr.backrooms.world.layout.LevelLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Level 228's Other (Level228Hunt), the parts that need no world: its pathfinding walks only real
 * corridor cells, step by connected step, from where it enters to you; and being "taken into the
 * mirror" always lands you on a real corridor cell (never image space) at least 80 blocks farther
 * from the nearest exit.
 */
class Level228HuntTest {
    private static final long[] SEEDS = {1L, 228L, -4_242_424_242L, 9_001L};

    /** A walkable real cell near (x, z) (square rings), or null. */
    @Nullable
    private static int[] walkableNear(long seed, int x, int z) {
        for (int r = 0; r <= 24; r++) {
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    if (Math.max(Math.abs(ox), Math.abs(oz)) == r && Level228Plan.isWalkable(seed, x + ox, z + oz)
                            && !Level228Plan.isImageSpace(seed, x + ox, z + oz)) {
                        return new int[] {x + ox, z + oz};
                    }
                }
            }
        }
        return null;
    }

    @Test
    void pathsRunThroughConnectedRealCells() {
        int planned = 0;
        for (long seed : SEEDS) {
            for (int k = 0; k < 6; k++) {
                int[] goal = walkableNear(seed, k * 211 - 400, k * 137 + 50);
                int[] start = goal == null ? null : walkableNear(seed, goal[0] + 70, goal[1] - 25);
                if (goal == null || start == null) {
                    continue;
                }
                int first = Level228Hunt.plan(seed, start[0], start[1], goal[0], goal[1], 0.5, 0.5);
                assertTrue(first >= 0, "no path from " + start[0] + "," + start[1] + " to " + goal[0] + "," + goal[1]
                        + " (seed " + seed + "): the maze is meant to be connected");
                planned++;
                int n = Level228Hunt.pathLen;
                assertEquals(start[0], Level228Hunt.PATH_X[0]);
                assertEquals(start[1], Level228Hunt.PATH_Z[0]);
                assertTrue(Math.abs(Level228Hunt.PATH_X[n - 1] - goal[0]) + Math.abs(Level228Hunt.PATH_Z[n - 1] - goal[1]) <= 2);
                for (int i = 0; i < n; i++) {
                    int x = Level228Hunt.PATH_X[i];
                    int z = Level228Hunt.PATH_Z[i];
                    assertFalse(Level228Plan.isImageSpace(seed, x, z), "path enters image space at " + x + "," + z);
                    if (i > 0) {
                        int dx = x - Level228Hunt.PATH_X[i - 1];
                        int dz = z - Level228Hunt.PATH_Z[i - 1];
                        assertEquals(1, Math.abs(dx) + Math.abs(dz), "path jumps at step " + i);
                        assertTrue(Level228Plan.canStep(seed, Level228Hunt.PATH_X[i - 1], Level228Hunt.PATH_Z[i - 1], dx, dz),
                                "path walks through a wall at " + x + "," + z);
                    }
                }
            }
        }
        assertTrue(planned >= 12, "too few test routes found: " + planned);
    }

    @Test
    void takenIntoTheMirrorLandsFartherFromTheExit() {
        LevelLayout layout = BackroomsLevel.LEVEL_228.layout();
        RandomSource random = RandomSource.create(228L);
        int checked = 0;
        for (long seed : SEEDS) {
            for (int k = 0; k < 5; k++) {
                // Start somewhere within ~150 blocks of an exit, where 80 more is always possible.
                int[] exit = layout.exitPosition(seed, k * 900 - 1800, k * 500);
                double a = k * 1.3;
                int[] start = walkableNear(seed, exit[0] + (int) (Math.cos(a) * 120), exit[1] + (int) (Math.sin(a) * 120));
                if (start == null) {
                    continue;
                }
                int[] e0 = layout.exitPosition(seed, start[0], start[1]);
                double from = Math.hypot(start[0] - e0[0], start[1] - e0[1]);
                BlockPos spot = Level228Hunt.farther(seed, start[0], start[1], random);
                assertNotNull(spot, "no landing found from " + start[0] + "," + start[1]);
                assertTrue(Level228Plan.isWalkable(seed, spot.getX(), spot.getZ()), "landing is not a real corridor cell");
                assertFalse(Level228Plan.isImageSpace(seed, spot.getX(), spot.getZ()), "landing is inside a reflection");
                int[] e1 = layout.exitPosition(seed, spot.getX(), spot.getZ());
                double to = Math.hypot(spot.getX() - e1[0], spot.getZ() - e1[1]);
                assertTrue(to >= from + 80, "landing only " + (to - from) + " blocks farther from the exit");
                checked++;
            }
        }
        assertTrue(checked >= 12, "too few starts checked: " + checked);
    }
}
