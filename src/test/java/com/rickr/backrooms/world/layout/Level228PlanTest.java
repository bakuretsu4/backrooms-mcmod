package com.rickr.backrooms.world.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Travel;
import java.util.ArrayDeque;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

/**
 * The mirror trick of Level 228 (Level228Plan): what you see in the glass is real geometry built
 * behind it, so it must be built right. Image volumes never hold real space and are sealed from it;
 * every image block is the mirrored real block it reflects; the shared contract (imagesNear,
 * isImageSpace) agrees with what is built; the key sights exist; and from every arrival, 300+
 * blocks from the exit, there is a way on foot to the exit mirror.
 */
class Level228PlanTest {
    private static final long[] WORLD_SEEDS = {1L, 42L, -7L, 0x5EEDL, 2024L, 777L};
    /** Half-size of the square checked block by block per seed. */
    private static final int HALF = 150;

    /** The layout seed the escape tests give Level 228 for a world seed (Escape.layoutSeed, other package). */
    private static long seedOf(long worldSeed) {
        return new SplittableRandom(worldSeed * 31 + BackroomsLevel.LEVEL_228.ordinal()).nextLong();
    }

    private static int[] centre(long worldSeed) {
        SplittableRandom r = new SplittableRandom(worldSeed ^ 0x228L);
        return new int[] {r.nextInt(-20_000, 20_000), r.nextInt(-20_000, 20_000)};
    }

    private static boolean realAir(int column, int y) {
        return (column & Level228Plan.F_REAL) != 0 && Level228Plan.at(column, y) == Level228Plan.AIR;
    }

    /** Image volumes never contain real space, and no step leads from real air into anything but real air or a solid. */
    @Test
    void imageSpaceIsSealedFromRealSpace() {
        for (long worldSeed : WORLD_SEEDS) {
            long seed = seedOf(worldSeed);
            int[] c = centre(worldSeed);
            int images = 0;
            for (int x = c[0] - HALF; x <= c[0] + HALF; x++) {
                for (int z = c[1] - HALF; z <= c[1] + HALF; z++) {
                    int col = Level228Plan.column(seed, x, z);
                    boolean image = (col & Level228Plan.F_IMAGE) != 0;
                    assertTrue(!image || (col & Level228Plan.F_REAL) == 0, "image column flagged real at " + x + " " + z);
                    assertEquals(image, Level228Plan.isImageSpace(seed, x, z), "isImageSpace disagrees at " + x + " " + z);
                    if (image) {
                        images++;
                        for (int y = Level228Plan.FEET_Y; y <= Level228Plan.LINTEL_Y; y++) {
                            assertTrue(!Level228Plan.isRealSpace(seed, x, y, z), "real space inside an image at " + x + " " + y + " " + z);
                        }
                    }
                    if ((col & Level228Plan.F_REAL) == 0) {
                        continue;
                    }
                    for (int y = Level228Plan.FEET_Y; y <= Level228Plan.LINTEL_Y; y++) {
                        if (!realAir(col, y)) {
                            continue;
                        }
                        for (int d = 0; d < 4; d++) {
                            int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
                            int nz = z + (d == 2 ? 1 : d == 3 ? -1 : 0);
                            int n = Level228Plan.column(seed, nx, nz);
                            boolean ok = realAir(n, y) || Level228Plan.at(n, y) != Level228Plan.AIR;
                            assertTrue(ok, "real air at " + x + " " + y + " " + z + " opens into "
                                    + ((n & Level228Plan.F_IMAGE) != 0 ? "image" : "fill") + " air at " + nx + " " + nz);
                        }
                    }
                }
            }
            assertTrue(images > HALF * HALF / 2, "too few image columns for seed " + worldSeed + ": " + images);
        }
    }

    /**
     * Every image block is the mirrored real block it maps to, and that real block is never itself
     * image space (images are of the real maze only). Checked on the built blocks, every state
     * mirrored by the image's flips.
     */
    @Test
    void imagesMirrorTheRealMaze() {
        Level228Layout layout = (Level228Layout) BackroomsLevel.LEVEL_228.layout();
        Level228Plan.Image[] out = new Level228Plan.Image[256];
        for (int i = 0; i < out.length; i++) {
            out[i] = new Level228Plan.Image();
        }
        BlockState[] img = new BlockState[64];
        BlockState[] real = new BlockState[64];
        for (long worldSeed : WORLD_SEEDS) {
            long seed = seedOf(worldSeed);
            int[] c = centre(worldSeed);
            int n = Level228Plan.imagesNear(seed, c[0], c[1], 60, out);
            assertTrue(n > 10, "too few image volumes near " + c[0] + " " + c[1] + ": " + n);
            assertTrue(n < out.length, "imagesNear filled the whole array: raise it");
            int depth3 = 0;
            for (int k = 0; k < n; k++) {
                Level228Plan.Image im = out[k];
                assertTrue(im.flipX == 1 || im.flipX == -1, "flipX");
                assertTrue(im.flipZ == 1 || im.flipZ == -1, "flipZ");
                assertEquals(Level228Plan.FLOOR_Y, im.minY);
                assertEquals(Level228Plan.CEILING_Y, im.maxY);
                if (im.depth >= 3) {
                    depth3++;
                }
                for (int x = im.minX; x <= im.maxX; x++) {
                    for (int z = im.minZ; z <= im.maxZ; z++) {
                        String where = "image block " + x + " " + z + " (depth " + im.depth + ", maps to " + im.realX(x) + " "
                                + im.realZ(z) + ")";
                        int col = Level228Plan.column(seed, x, z);
                        assertTrue((col & Level228Plan.F_IMAGE) != 0, "imagesNear box not built as image: " + where);
                        int rx = im.realX(x);
                        int rz = im.realZ(z);
                        // The inverse map gets back.
                        assertEquals(x, (rx - im.offX) * im.flipX, where);
                        assertEquals(z, (rz - im.offZ) * im.flipZ, where);
                        assertTrue(!Level228Plan.isImageSpace(seed, rx, rz), "maps into image space: " + where);
                        int mask = 0x1FFF;
                        assertEquals(Level228Plan.realColumn(seed, rx, rz) & mask, col & mask, where);
                        fill(layout, seed, x, z, img);
                        fill(layout, seed, rx, rz, real);
                        for (int y = im.minY; y <= im.maxY; y++) {
                            BlockState want = real[y];
                            if (want != null && im.flipX < 0) {
                                want = want.mirror(Mirror.FRONT_BACK);
                            }
                            if (want != null && im.flipZ < 0) {
                                want = want.mirror(Mirror.LEFT_RIGHT);
                            }
                            assertEquals(want, img[y], where + " y" + y);
                        }
                    }
                }
            }
            assertTrue(depth3 > 0 || Level228Plan.nearestFacingMirrors(seed, c[0], c[1], 3, 20) != null,
                    "no depth-3 images anywhere near seed " + worldSeed);
        }
    }

    private static void fill(Level228Layout layout, long seed, int x, int z, BlockState[] into) {
        java.util.Arrays.fill(into, null);
        layout.fillColumn(seed, x, z, (y, state) -> into[y] = state);
    }

    /** The sights the level is about exist within a short walk anywhere. */
    @Test
    void keySightsExist() {
        for (long worldSeed : WORLD_SEEDS) {
            long seed = seedOf(worldSeed);
            int[] c = centre(worldSeed);
            int[] facing = Level228Plan.nearestFacingMirrors(seed, c[0], c[1], 3, 12);
            assertNotNull(facing, "no facing mirrors 3 deep within 12 cells, seed " + worldSeed);
            assertTrue(Level228Plan.isWalkable(seed, facing[0], facing[1]), "facing-mirror corridor spot not walkable");
            int[] fake = Level228Plan.nearestFalseBranch(seed, c[0], c[1], 12);
            assertNotNull(fake, "no false branch within 12 cells, seed " + worldSeed);
            assertTrue(Level228Plan.isWalkable(seed, fake[0], fake[1]), "false-branch node not walkable");
            // A false branch: the face is glass and the node across from it is open.
            int framed = 0;
            for (int x = c[0] - HALF; x <= c[0] + HALF; x++) {
                for (int z = c[1] - HALF; z <= c[1] + HALF; z++) {
                    int col = Level228Plan.column(seed, x, z);
                    if ((col & Level228Plan.F_REAL) != 0 && Level228Plan.at(col, Level228Plan.FEET_Y) == Level228Plan.AIR
                            && Level228Plan.at(col, Level228Plan.LINTEL_Y) == Level228Plan.FRAME) {
                        framed++;
                    }
                }
            }
            assertTrue(framed > 0, "no gap framed like a mirror near seed " + worldSeed);
        }
    }

    /**
     * Arrivals from Level 94 step out of a mirror (next to a pane) at least 300 blocks from the
     * nearest exit, and there is a walk through the maze (Level228Plan.canStep) to that exit's
     * mirror.
     */
    @Test
    void arrivalsAreFarFromTheExitAndCanReachIt() {
        LevelLayout layout = BackroomsLevel.LEVEL_228.layout();
        for (long worldSeed : WORLD_SEEDS) {
            long seed = seedOf(worldSeed);
            SplittableRandom r = new SplittableRandom(worldSeed);
            for (int t = 0; t < 3; t++) {
                int x = r.nextInt(-20_000, 20_000);
                int z = r.nextInt(-20_000, 20_000);
                Travel.Arrival a = Travel.arrival(layout, seed, x, z, BackroomsLevel.LEVEL_94, true);
                BlockPos spot = a.spot();
                assertTrue(a.exact(), "not a mirror arrival at " + spot);
                assertTrue(Level228Plan.isWalkable(seed, spot.getX(), spot.getZ()), "arrival not walkable: " + spot);
                boolean byGlass = false;
                for (int d = 0; d < 4; d++) {
                    int nx = spot.getX() + (d == 0 ? 1 : d == 1 ? -1 : 0);
                    int nz = spot.getZ() + (d == 2 ? 1 : d == 3 ? -1 : 0);
                    int col = Level228Plan.column(seed, nx, nz);
                    int at = Level228Plan.at(col, Level228Plan.FEET_Y);
                    byGlass |= at == Level228Plan.MIRROR_X || at == Level228Plan.MIRROR_Z;
                }
                assertTrue(byGlass, "arrival is not against a mirror: " + spot);
                int[] exit = layout.exitPosition(seed, spot.getX(), spot.getZ());
                double d = Math.hypot(spot.getX() - exit[0], spot.getZ() - exit[1]);
                assertTrue(d >= 300, "arrival " + spot + " only " + d + " from exit " + exit[0] + " " + exit[1]);
                assertTrue(walk(seed, spot.getX(), spot.getZ(), exit[0], exit[1]),
                        "no walk from " + spot + " to the exit front " + exit[0] + " " + exit[1]);
                assertTrue(exitBeside(seed, exit[0], exit[1]), "no exit mirror beside the exit front " + exit[0] + " " + exit[1]);
            }
        }
    }

    private static boolean exitBeside(long seed, int x, int z) {
        for (int d = 0; d < 4; d++) {
            int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
            int nz = z + (d == 2 ? 1 : d == 3 ? -1 : 0);
            int at = Level228Plan.at(Level228Plan.column(seed, nx, nz), Level228Plan.FEET_Y);
            if (at == Level228Plan.EXIT_X || at == Level228Plan.EXIT_Z) {
                return true;
            }
        }
        return false;
    }

    /** Breadth-first walk over canStep in a window around both ends. */
    private static boolean walk(long seed, int sx, int sz, int tx, int tz) {
        int margin = 300;
        int x0 = Math.min(sx, tx) - margin;
        int z0 = Math.min(sz, tz) - margin;
        int w = Math.abs(sx - tx) + 2 * margin + 1;
        int h = Math.abs(sz - tz) + 2 * margin + 1;
        boolean[] seen = new boolean[w * h];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {sx, sz});
        seen[(sx - x0) * h + (sz - z0)] = true;
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            if (p[0] == tx && p[1] == tz) {
                return true;
            }
            for (int d = 0; d < 4; d++) {
                int dx = d == 0 ? 1 : d == 1 ? -1 : 0;
                int dz = d == 2 ? 1 : d == 3 ? -1 : 0;
                int nx = p[0] + dx;
                int nz = p[1] + dz;
                if (nx < x0 || nz < z0 || nx >= x0 + w || nz >= z0 + h || seen[(nx - x0) * h + (nz - z0)]) {
                    continue;
                }
                if (Level228Plan.canStep(seed, p[0], p[1], dx, dz)) {
                    seen[(nx - x0) * h + (nz - z0)] = true;
                    queue.add(new int[] {nx, nz});
                }
            }
        }
        return false;
    }

    /** One exit per exit region, never reflected anywhere. */
    @Test
    void exitMirrorsAreNeverReflected() {
        for (long worldSeed : WORLD_SEEDS) {
            long seed = seedOf(worldSeed);
            int[] c = centre(worldSeed);
            int[] exit = Level228Plan.nearestExit(seed, c[0], c[1]);
            for (int x = exit[0] - 40; x <= exit[0] + 40; x++) {
                for (int z = exit[1] - 40; z <= exit[1] + 40; z++) {
                    int col = Level228Plan.column(seed, x, z);
                    if ((col & Level228Plan.F_IMAGE) == 0) {
                        continue;
                    }
                    for (int y = Level228Plan.FEET_Y; y <= Level228Plan.LINTEL_Y; y++) {
                        int at = Level228Plan.at(col, y);
                        assertTrue(at != Level228Plan.EXIT_X && at != Level228Plan.EXIT_Z, "exit mirror reflected at " + x + " " + z);
                    }
                }
            }
        }
    }
}
