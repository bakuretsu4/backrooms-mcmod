package com.rickr.backrooms.escape;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level0AnomalyPlan;
import com.rickr.backrooms.world.layout.Level0Layout;
import com.rickr.backrooms.world.layout.Level0SetPieces;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * "No sealed pockets" for Level 0's areas that are a maze of their own (the warren's slots and
 * rooms) or are entered only through corridors (the Atrium): in the real generated blocks, every
 * spot you can stand on inside the site gets out on foot into the surrounding maze, well past
 * the site's edge (searched with {@link Reach}, like the escapability tests), and every visit
 * point is a spot you can stand on. Plus the Atrium's shaft really is open up to the roof.
 */
class Level0AreaWalkabilityTest {
    private static final String[] KINDS = {Level0AnomalyPlan.WARREN, Level0AnomalyPlan.ATRIUM};
    /** How far past the site's edge counts as out in the maze. */
    private static final int OUT = 12;
    /** Window margin round the site for the search. */
    private static final int MARGIN = OUT + 40;

    @TestFactory
    Stream<DynamicTest> everySpotInsideReachesTheMaze() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String kind : KINDS) {
            for (long worldSeed : Escape.SEEDS) {
                tests.add(DynamicTest.dynamicTest(kind + " seed " + worldSeed, () -> check(kind, worldSeed)));
            }
        }
        return tests.stream();
    }

    private static void check(String kind, long worldSeed) {
        LevelLayout layout = BackroomsLevel.LEVEL_0.layout();
        long seed = Escape.layoutSeed(worldSeed, BackroomsLevel.LEVEL_0);
        SplittableRandom random = new SplittableRandom(worldSeed ^ kind.hashCode());
        AnomalySite site = layout.nearestAnomaly(seed, random.nextInt(-20_000, 20_000), random.nextInt(-20_000, 20_000), kind);
        assertNotNull(site, "no " + kind + " found for seed " + worldSeed);
        int cx = (site.minX() + site.maxX()) / 2;
        int cz = (site.minZ() + site.maxZ()) / 2;
        int half = Math.max(site.maxX() - site.minX(), site.maxZ() - site.minZ()) / 2 + MARGIN;
        VoxelWorld world = new VoxelWorld(layout, seed, cx, cz, half, s -> false);
        int feet = layout.floorY() + 1;
        String where = kind + " at " + site.minX() + " " + site.minZ() + ".." + site.maxX() + " " + site.maxZ() + " (seed " + worldSeed + ")";

        // Group the standable spots inside the site into pieces joined on the floor, then walk out
        // from one spot of each piece.
        int w = site.maxX() - site.minX() + 1;
        int d = site.maxZ() - site.minZ() + 1;
        int[] piece = new int[w * d];
        int pieces = 0;
        int standable = 0;
        int[] stack = new int[w * d];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                if (piece[i * d + j] != 0 || !stand(world, site.minX() + i, feet, site.minZ() + j)) {
                    continue;
                }
                pieces++;
                int top = 0;
                stack[top++] = i * d + j;
                piece[i * d + j] = pieces;
                while (top > 0) {
                    int k = stack[--top];
                    standable++;
                    int pi = k / d;
                    int pj = k % d;
                    for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int ni = pi + step[0];
                        int nj = pj + step[1];
                        if (ni >= 0 && nj >= 0 && ni < w && nj < d && piece[ni * d + nj] == 0
                                && stand(world, site.minX() + ni, feet, site.minZ() + nj)) {
                            piece[ni * d + nj] = pieces;
                            stack[top++] = ni * d + nj;
                        }
                    }
                }
                int sx = site.minX() + i;
                int sz = site.minZ() + j;
                Reach.Result result = Reach.search(world, sx, feet, sz, site.minX() - OUT - 4, cz,
                        (vw, x, y, z, fromY) -> x < site.minX() - OUT || x > site.maxX() + OUT || z < site.minZ() - OUT || z > site.maxZ() + OUT,
                        false);
                assertTrue(result.reached(), "sealed pocket in the " + where + ": from " + sx + " " + feet + " " + sz + ", "
                        + result.describe() + "\n    there: " + Escape.blocksAround(world, sx, feet, sz));
            }
        }
        System.out.println("AREA ok   " + where + ": " + standable + " standable spots in " + pieces + " piece(s), all reach the maze");
        assertTrue(standable > w * d / 4, "suspiciously little floor in the " + where + ": " + standable);

        if (kind.equals(Level0AnomalyPlan.ATRIUM)) {
            // Under the middle of the shaft: open air from the carpet up to just under the roof.
            for (int y = feet; y < Level0SetPieces.ROOF_Y; y++) {
                assertTrue((world.flags(cx, y, cz) & VoxelWorld.PASS) != 0, "the shaft is blocked at y" + y + " in the " + where);
            }
            assertTrue((world.flags(cx, Level0SetPieces.ROOF_Y, cz) & VoxelWorld.PASS) == 0, "the shaft has no roof in the " + where);
        }

        // Every visit point exists and is a spot you can stand on.
        for (String name : Level0AnomalyPlan.visitPoints(kind)) {
            double[] p = Level0AnomalyPlan.visitPoint((Level0Layout) layout, seed, site, name);
            assertNotNull(p, "no visit point " + name + " in the " + where);
            int px = (int) Math.floor(p[0]);
            int pz = (int) Math.floor(p[2]);
            assertTrue(stand(world, px, (int) Math.floor(p[1]), pz), "visit point " + name + " at " + px + " " + pz + " is not standable in the " + where);
        }
    }

    private static boolean stand(VoxelWorld world, int x, int y, int z) {
        return Reach.kind(world, x, y, z) != 0;
    }
}
