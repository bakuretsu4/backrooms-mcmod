package com.rickr.backrooms.world.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Regression test for "I spawned in here, I can't get out": every walkable column of Level 4's floor
 * plan must be connected to the ring aisles (and with them to the rest of the level). A floor area
 * that can't reach any ring aisle is a sealed room: a player who arrives there is trapped.
 *
 * <p>Level4Plan is a pure function of (seed, x, z), so this runs without Minecraft. For a few seeds
 * it builds the walkability of a block of zones (doors and doorways passable, walls, glass, windows
 * and furniture solid; the exit stairwell towers solid and their clearings open, as Level4Layout
 * builds them), splits it into connected areas, and fails on every area that has no ring-aisle
 * column. Areas cut off by the edge of the tested block are skipped (they may continue outside it).
 */
class Level4PlanWalkabilityTest {
    private static final long[] SEEDS = {1L, 42L, 0x5EEDL, -7L, 123456789L, 2024L, -987654321L, 0xBACC00L,
            31337L, Long.MAX_VALUE, Long.MIN_VALUE + 1, 777L};
    /** Zones tested along each axis (centred on the origin, so negative coordinates are covered too). */
    private static final int ZONES = 12;

    @Test
    void everyFloorReachesARingAisle() {
        int sealed = 0;
        StringBuilder examples = new StringBuilder();
        for (long seed : SEEDS) {
            sealed += sealedAreas(seed, examples);
        }
        assertEquals(0, sealed, "Level 4 has sealed floor areas (seed: x,z of one column each):" + examples);
    }

    /** Counts the sealed areas for one seed, adding a few examples to {@code examples}. */
    private static int sealedAreas(long seed, StringBuilder examples) {
        int size = ZONES * Level4Plan.CELL;
        int origin = -size / 2;
        boolean[] walk = new boolean[size * size];
        boolean[] ring = new boolean[size * size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int x = origin + i;
                int z = origin + j;
                int d = Level4Plan.EXITS.distance(seed, x, z);
                boolean w;
                if (d <= LevelLayout.STAIRWELL_RADIUS) {
                    w = false; // the stairwell tower (its door leads down and out: it is an exit, not floor)
                } else if (d <= Level4Plan.CLEARING_RADIUS) {
                    w = true;  // the open clearing round it
                } else {
                    w = Level4Plan.walkable(Level4Plan.column(seed, x, z));
                }
                walk[i * size + j] = w;
                int lx = Math.floorMod(x, Level4Plan.CELL);
                int lz = Math.floorMod(z, Level4Plan.CELL);
                ring[i * size + j] = w && (lx <= 3 || lx >= Level4Plan.CELL - 3 || lz <= 3 || lz >= Level4Plan.CELL - 3);
            }
        }
        int[] queue = new int[size * size];
        boolean[] seen = new boolean[size * size];
        int sealed = 0;
        for (int start = 0; start < walk.length; start++) {
            if (!walk[start] || seen[start]) {
                continue;
            }
            // Flood one connected area.
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            seen[start] = true;
            boolean reachesRing = false;
            boolean touchesEdge = false;
            while (head < tail) {
                int cell = queue[head++];
                int i = cell / size;
                int j = cell % size;
                reachesRing |= ring[cell];
                if (i == 0 || j == 0 || i == size - 1 || j == size - 1) {
                    touchesEdge = true;
                }
                int[] next = {i > 0 ? cell - size : -1, i < size - 1 ? cell + size : -1, j > 0 ? cell - 1 : -1, j < size - 1 ? cell + 1 : -1};
                for (int n : next) {
                    if (n >= 0 && walk[n] && !seen[n]) {
                        seen[n] = true;
                        queue[tail++] = n;
                    }
                }
            }
            if (!reachesRing && !touchesEdge) {
                sealed++;
                if (sealed <= 3) {
                    examples.append("\n  ").append(seed).append(": ").append(origin + start / size).append(',').append(origin + start % size)
                            .append(" (").append(tail).append(" columns)");
                }
            }
        }
        System.out.println("Level4PlanWalkabilityTest seed " + seed + ": " + sealed + " sealed areas");
        return sealed;
    }
}
