package com.rickr.backrooms.world.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Regression tests for two Level 37 structure bugs (docs/reference/level37-fixes):
 * <ul>
 *   <li>"Pillar is trying to go through the rectangle light in the ceiling": a room's row of round
 *       pillars stood right under one of its skylights, so the pillar ran up into the light with a
 *       square ceiling block on top of it. Checked generally: no curved piece (a pillar, a wavy or
 *       rounded wall, an arch) may stand under a ceiling skylight or next to one, just below the
 *       ceiling.</li>
 *   <li>"Entrance is cutting off the arch": a side passage into a tunnel hall (the hall of receding
 *       round arches) cut its arched tunnel straight through the leg of one of the arches, leaving
 *       the arch hanging; one near the far end opened into the side of the dark round tunnel.
 *       Checked: every ring arch that is built stands on whole legs, and the round tunnel at the far
 *       end has solid walls on both sides.</li>
 * </ul>
 *
 * <p>Level37Plan is a pure function of (seed, x, z), so this runs without Minecraft. It sweeps
 * every cell of two blocks of cells (one around the origin, so negative coordinates are covered,
 * one far out) for a set of seeds, on the plain plan (no anomalous areas).
 */
class Level37StructureOverlapTest {
    private static final long[] SEEDS = {1L, 42L, 0x5EEDL, -7L, 123456789L, 2024L, -987654321L, 0xBACC00L,
            31337L, Long.MAX_VALUE};
    /** North-west cell of each tested block of cells. */
    private static final int[][] BLOCKS = {{-12, -12}, {2711, -4093}};
    /** Cells along each side of a tested block. */
    private static final int BLOCK_CELLS = 24;
    private static final int MAX_EXAMPLES = 10;
    private static final int CELL = Level37Plan.CELL;
    private static final int MID = Level37Plan.MID;

    private final Level37Plan plan = new Level37Plan();
    private final int[] out = new int[64];
    private final int[] near = new int[64];

    @Test
    void nothingStandsUpIntoASkylight() {
        int skylights = 0;
        int bad = 0;
        StringBuilder examples = new StringBuilder();
        for (long seed : SEEDS) {
            for (int[] block : BLOCKS) {
                for (int cx = block[0]; cx < block[0] + BLOCK_CELLS; cx++) {
                    for (int cz = block[1]; cz < block[1] + BLOCK_CELLS; cz++) {
                        if (!mayHaveSkylights(Level37Plan.kind(Level37Plan.room(seed, cx, cz)))) {
                            continue;
                        }
                        for (int lx = 0; lx < CELL; lx++) {
                            for (int lz = 0; lz < CELL; lz++) {
                                int x = cx * CELL + lx;
                                int z = cz * CELL + lz;
                                plan.column(seed, x, z, null, out);
                                int y = skylightRecess(out);
                                if (y < 0) {
                                    continue;
                                }
                                skylights++;
                                if (pieceUnderOrBeside(seed, x, z, y - 1)) {
                                    bad++;
                                    example(examples, bad, seed + ": " + x + "," + z + " (skylight at y" + y + ")");
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Level37StructureOverlapTest: " + skylights + " skylight columns, " + bad + " crowded");
        assertTrue(skylights > 0, "the sweep found no skylights at all");
        assertEquals(0, bad, "Level 37 skylights with a pillar or curved wall running up into them (seed: x,z):" + examples);
    }

    @Test
    void sidePassagesNeverCutATunnelHallsArches() {
        int halls = 0;
        int rings = 0;
        int dropped = 0;
        int bad = 0;
        StringBuilder examples = new StringBuilder();
        for (long seed : SEEDS) {
            for (int[] block : BLOCKS) {
                for (int cx = block[0]; cx < block[0] + BLOCK_CELLS; cx++) {
                    for (int cz = block[1]; cz < block[1] + BLOCK_CELLS; cz++) {
                        int room = Level37Plan.room(seed, cx, cz);
                        if (Level37Plan.kind(room) != Level37Plan.K_TUNNEL) {
                            continue;
                        }
                        halls++;
                        boolean alongX = Level37Plan.alongX(room);
                        for (int i = 0; i < Level37Plan.RING_ALONG.length; i++) {
                            int along = Level37Plan.RING_ALONG[i];
                            int radius = Level37Plan.RING_RADIUS[i];
                            if (!ringBuilt(seed, cx, cz, alongX, along)) {
                                dropped++;
                                continue;
                            }
                            rings++;
                            // Its legs: the solid wall either side of the opening, from the 2-thick
                            // window wall on the low side to the far wall.
                            for (int across = Level37Plan.BAND + 2; across < CELL; across++) {
                                int a = across - MID;
                                if ((a >= 0 ? a : -a - 1) < radius) {
                                    continue;
                                }
                                if (!solidAtHead(seed, cx, cz, alongX, along, across)) {
                                    bad++;
                                    example(examples, bad, seed + ": " + at(cx, cz, alongX, along, across)
                                            + " (leg of the ring arch " + (i + 1) + " cut)");
                                }
                            }
                        }
                        // The dark round tunnel at the far end: solid wall both sides of it.
                        for (int along = Level37Plan.FAR_TUNNEL_ALONG; along < CELL; along++) {
                            for (int across = Level37Plan.BAND; across < CELL; across++) {
                                if (across >= MID - 2 && across <= MID + 1) {
                                    continue;
                                }
                                if (!solidAtHead(seed, cx, cz, alongX, along, across)) {
                                    bad++;
                                    example(examples, bad, seed + ": " + at(cx, cz, alongX, along, across)
                                            + " (side of the round tunnel opened)");
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Level37StructureOverlapTest: " + halls + " tunnel halls, " + rings + " ring arches, "
                + dropped + " left out for a passage, " + bad + " cut columns");
        assertTrue(halls > 0, "the sweep found no tunnel halls at all");
        // Leaving a ring out is the exception (a passage forced into the middle of a side wall),
        // not a way of passing this test.
        assertTrue(dropped * 4 < rings, "too many ring arches left out: " + dropped + " of " + (rings + dropped));
        assertEquals(0, bad, "Level 37 tunnel halls whose arches or round tunnel are cut by a side passage (seed: x,z):"
                + examples);
    }

    // ---- Skylights ---------------------------------------------------------------------------

    /** Room kinds with ceiling slots; the spiral's light well and the domes' oculi are meant to frame what stands in them. */
    private static boolean mayHaveSkylights(int kind) {
        return kind == Level37Plan.K_ROOM || kind == Level37Plan.K_HALL || kind == Level37Plan.K_CORRIDOR
                || kind == Level37Plan.K_POOL || kind == Level37Plan.K_TUNNEL;
    }

    /** The recess of a ceiling skylight in this column (air under a downward sunlit void), or -1. */
    private static int skylightRecess(int[] codes) {
        for (int y = Level37Plan.FEET_Y + 2; y < Level37Plan.HIGHEST_Y; y++) {
            int above = codes[y + 1];
            if (codes[y] == Level37Plan.AIR && Level37Plan.type(above) == Level37Plan.VOID && Level37Plan.data(above) == 0) {
                return y;
            }
        }
        return -1;
    }

    /** True when column (x, z) or one of its eight neighbours holds a curved piece at height y. */
    private boolean pieceUnderOrBeside(long seed, int x, int z, int y) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                plan.column(seed, x + dx, z + dz, null, near);
                if (Level37Plan.type(near[y]) == Level37Plan.PIECE) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- Tunnel halls ------------------------------------------------------------------------

    /** True when the ring at {@code along} stands: its middle column has the arch's curved pieces overhead. */
    private boolean ringBuilt(long seed, int cx, int cz, boolean alongX, int along) {
        column(seed, cx, cz, alongX, along, MID);
        for (int y = Level37Plan.RING_SPRING_Y; y < 64; y++) {
            if (Level37Plan.type(out[y]) == Level37Plan.PIECE) {
                return true;
            }
        }
        return false;
    }

    /** True when the column is solid wall at head height (nothing was cut through it). */
    private boolean solidAtHead(long seed, int cx, int cz, boolean alongX, int along, int across) {
        column(seed, cx, cz, alongX, along, across);
        return Level37Plan.type(out[Level37Plan.FEET_Y + 1]) == Level37Plan.WALL;
    }

    private void column(long seed, int cx, int cz, boolean alongX, int along, int across) {
        plan.column(seed, cx * CELL + (alongX ? along : across), cz * CELL + (alongX ? across : along), null, out);
    }

    private static String at(int cx, int cz, boolean alongX, int along, int across) {
        return (cx * CELL + (alongX ? along : across)) + "," + (cz * CELL + (alongX ? across : along));
    }

    private static void example(StringBuilder examples, int count, String text) {
        if (count <= MAX_EXAMPLES) {
            examples.append("\n  ").append(text);
        }
    }
}
