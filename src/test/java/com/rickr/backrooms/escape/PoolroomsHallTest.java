package com.rickr.backrooms.escape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.PoolroomsAreas;
import com.rickr.backrooms.world.layout.PoolroomsLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;

/**
 * The Poolrooms' exit hall and big anomalous areas, built from the real generated blocks:
 * <ul>
 *   <li>at every exit, from the walkway round the hall you can walk (no jumping on land, as in
 *       the game) down to the pool and up the long stair into the daylit niche, the way out;</li>
 *   <li>hints and /backrooms locate point at that niche ({@code exitPosition}, {@code exitY});</li>
 *   <li>no water anywhere in a hall or an area touches air at its side or below (it would flow
 *       when the chunk loads).</li>
 * </ul>
 */
class PoolroomsHallTest {
    private static final PoolroomsLayout LAYOUT = (PoolroomsLayout) BackroomsLevel.POOLROOMS.layout();
    /** Exit regions checked per seed, along each axis round the origin. */
    private static final int REGIONS = 3;
    /** Half-size of the window round a hall: its footprint (25) and some of the rooms round it. */
    private static final int HALL_WINDOW = 40;
    private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};

    @Test
    void everyHallClimbsToItsWayOut() {
        List<String> bad = new ArrayList<>();
        int halls = 0;
        for (long worldSeed : Escape.SEEDS) {
            long seed = Escape.layoutSeed(worldSeed, BackroomsLevel.POOLROOMS);
            for (int rx = -REGIONS / 2; rx <= REGIONS / 2; rx++) {
                for (int rz = -REGIONS / 2; rz <= REGIONS / 2; rz++) {
                    int ex = LAYOUT.exits().exitX(seed, rx, rz);
                    int ez = LAYOUT.exits().exitZ(seed, rx, rz);
                    halls++;
                    int[] niche = LAYOUT.exitPosition(seed, ex, ez);
                    int feet = LAYOUT.exitY(seed, niche[0], niche[1]);
                    if (!LAYOUT.isHallExit(seed, niche[0], feet, niche[1])) {
                        bad.add("seed " + seed + " hall " + ex + " " + ez + ": exitPosition " + niche[0] + " " + feet + " " + niche[1]
                                + " is not the way out");
                        continue;
                    }
                    VoxelWorld world = new VoxelWorld(LAYOUT, seed, ex, ez, HALL_WINDOW, s -> false);
                    // Start on the walkway round the hall (ring 25), at the level's height.
                    int sx = ex + 25;
                    int sz = ez;
                    Reach.Result down = Reach.search(world, sx, PoolroomsLayout.SURFACE_Y + 1, sz, ex, ez,
                            (w, x, y, z, fromY) -> y <= 7 && Math.max(Math.abs(x - ex), Math.abs(z - ez)) <= 20, false);
                    Reach.Result out = Reach.search(world, sx, PoolroomsLayout.SURFACE_Y + 1, sz, niche[0], niche[1],
                            (w, x, y, z, fromY) -> LAYOUT.isHallExit(w.seed, x, y, z), false);
                    if (!down.reached() || !out.reached()) {
                        bad.add("seed " + seed + " hall " + ex + " " + ez + ": to the pool deck " + down.describe()
                                + "; to the way out " + out.describe());
                    }
                }
            }
        }
        assertEquals(List.of(), bad, bad.size() + " of " + halls + " halls can't be climbed");
    }

    @Test
    void waterNeverTouchesAirInHallsAndAreas() {
        List<String> bad = new ArrayList<>();
        int areas = 0;
        for (long worldSeed : Escape.SEEDS) {
            long seed = Escape.layoutSeed(worldSeed, BackroomsLevel.POOLROOMS);
            for (int rx = -REGIONS / 2; rx <= REGIONS / 2; rx++) {
                for (int rz = -REGIONS / 2; rz <= REGIONS / 2; rz++) {
                    int ex = LAYOUT.exits().exitX(seed, rx, rz);
                    int ez = LAYOUT.exits().exitZ(seed, rx, rz);
                    checkWater(seed, ex - 26, ez - 26, ex + 26, ez + 26, "hall " + ex + " " + ez, bad);
                }
            }
            List<AnomalySite> found = new ArrayList<>();
            LAYOUT.anomaliesNear(seed, 0, 0, 600, site -> {
                if (site.kind().equals(PoolroomsAreas.SKY_COURT) || site.kind().equals(PoolroomsAreas.GLOW_HALL)
                        || site.kind().equals(PoolroomsAreas.TANGLE)) {
                    found.add(site);
                }
            });
            areas += found.size();
            for (AnomalySite site : found) {
                checkWater(seed, site.minX() - 2, site.minZ() - 2, site.maxX() + 2, site.maxZ() + 2,
                        site.kind() + " at " + site.minX() + " " + site.minZ(), bad);
            }
        }
        assertTrue(areas > 0, "no sky court, glow hall or tangle within 600 blocks of the origin for any seed");
        assertEquals(List.of(), bad.subList(0, Math.min(bad.size(), 12)), bad.size() + " leaks");
    }

    /** Every water block in the box has something other than air beside and under it. */
    private static void checkWater(long seed, int minX, int minZ, int maxX, int maxZ, String where, List<String> bad) {
        int w = maxX - minX + 1;
        int d = maxZ - minZ + 1;
        int height = LAYOUT.worldHeight();
        BlockState[][] columns = new BlockState[w * d][];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                BlockState[] column = new BlockState[height];
                LAYOUT.fillColumn(seed, minX + i, minZ + j, (y, state) -> {
                    if (y >= 0 && y < height) {
                        column[y] = state.isAir() ? null : state;
                    }
                });
                columns[i * d + j] = column;
            }
        }
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                BlockState[] column = columns[i * d + j];
                for (int y = 0; y < height; y++) {
                    if (column[y] == null || !column[y].getFluidState().is(Fluids.WATER)) {
                        continue;
                    }
                    for (int[] s : SIDES) {
                        int ni = i + s[0];
                        int nj = j + s[2];
                        int ny = y + s[1];
                        if (ni < 0 || nj < 0 || ni >= w || nj >= d || ny < 0) {
                            continue; // outside the box: checked from the other side, if at all
                        }
                        if (columns[ni * d + nj][ny] == null) {
                            bad.add("seed " + seed + " " + where + ": water at " + (minX + i) + " " + y + " " + (minZ + j)
                                    + " next to air at " + (minX + ni) + " " + ny + " " + (minZ + nj));
                            return;
                        }
                    }
                }
            }
        }
    }
}
