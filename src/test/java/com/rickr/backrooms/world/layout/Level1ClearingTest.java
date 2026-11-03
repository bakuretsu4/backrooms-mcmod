package com.rickr.backrooms.world.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.ExitLocator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.Test;

/**
 * Level 1's exits are placed without looking at the corridor blocks, so an exit's clearing (the
 * open apron around the stairwell, rings 6 to 8) can overlap one. The clearing must still be built
 * as open floor there, which is what {@link Level1Layout#isWalkable} says: otherwise the block's
 * walls, rooms and crates stand right outside the stairwell door and can seal it (first seen with
 * seed -7273252675751258371, when exits were 384 blocks apart: the stairwell at -9351, -6398 opened
 * into a 2-block pocket walled in by the corridor block).
 */
class Level1ClearingTest {
    private static final long[] SEEDS = {-7273252675751258371L, 1L, 42L, -7L, 0x5EEDL, 2024L};
    /** Exit regions tested along each axis, centred on the origin. */
    private static final int REGIONS = 24;

    private final Level1Layout layout = (Level1Layout) BackroomsLevel.LEVEL_1.layout();

    /**
     * Every clearing column where the corridor block used to stand a wall (the sealed-stairwell
     * case): open floor now, and Level1ClearingRepair finds it (the column, and its chunk) to fix
     * worlds built before the fix.
     */
    @Test
    void clearingsOverCorridorWallsAreOpenAndRepaired() {
        ExitLocator exits = layout.exits();
        int floor = layout.floorY();
        int walled = 0;
        for (long seed : SEEDS) {
            for (int rx = -REGIONS / 2; rx < REGIONS / 2; rx++) {
                for (int rz = -REGIONS / 2; rz < REGIONS / 2; rz++) {
                    int ex = exits.exitX(seed, rx, rz);
                    int ez = exits.exitZ(seed, rx, rz);
                    for (int dx = -8; dx <= 8; dx++) {
                        for (int dz = -8; dz <= 8; dz++) {
                            int x = ex + dx;
                            int z = ez + dz;
                            if (Math.max(Math.abs(dx), Math.abs(dz)) <= LevelLayout.STAIRWELL_RADIUS
                                    || !Level1Areas.corridorZone(seed, x, z)) {
                                continue;
                            }
                            BlockState[] old = new BlockState[1];
                            layout.fillCorridorBlock(seed, x, z, (y, state) -> {
                                if (y == floor + 1) {
                                    old[0] = state;
                                }
                            });
                            if (passable(old[0])) {
                                continue;
                            }
                            walled++;
                            assertOpenFloor(seed, x, z);
                            assertTrue(layout.clearingInCorridorBlock(seed, x, z), "repair would skip seed " + seed + " " + x + " " + z);
                            assertTrue(layout.chunkTouchesClearing(seed, Math.floorDiv(x, 16) * 16, Math.floorDiv(z, 16) * 16),
                                    "repair would skip the chunk of seed " + seed + " " + x + " " + z);
                        }
                    }
                    // Two chunks east of the clearing's east edge (radius 8) is beyond it (exits are 1024 apart).
                    int farX = Math.floorDiv(ex + 8, 16) * 16 + 32;
                    assertTrue(!layout.chunkTouchesClearing(seed, farX, Math.floorDiv(ez, 16) * 16),
                            "a chunk well clear of the clearing counts as touching it (seed " + seed + ")");
                }
            }
        }
        assertTrue(walled > 100, "too few clearing columns over corridor walls to test (" + walled + ")");
    }

    @Test
    void everyClearingIsOpenFloor() {
        ExitLocator exits = layout.exits();
        int inCorridorBlocks = 0;
        int blocked = 0;
        StringBuilder examples = new StringBuilder();
        for (long seed : SEEDS) {
            for (int rx = -REGIONS / 2; rx < REGIONS / 2; rx++) {
                for (int rz = -REGIONS / 2; rz < REGIONS / 2; rz++) {
                    int ex = exits.exitX(seed, rx, rz);
                    int ez = exits.exitZ(seed, rx, rz);
                    for (int dx = -8; dx <= 8; dx++) {
                        for (int dz = -8; dz <= 8; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) <= LevelLayout.STAIRWELL_RADIUS) {
                                continue;
                            }
                            int x = ex + dx;
                            int z = ez + dz;
                            if (Level1Areas.corridorZone(seed, x, z)) {
                                inCorridorBlocks++;
                            }
                            String why = whyNotOpenFloor(seed, x, z);
                            if (why != null && ++blocked <= 5) {
                                examples.append("\n  seed ").append(seed).append(' ').append(x).append(' ').append(z).append(": ").append(why);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(inCorridorBlocks > 1000, "too few clearing columns inside corridor blocks to test (" + inCorridorBlocks + ")");
        assertEquals(0, blocked, blocked + " Level 1 clearing columns are not open floor:" + examples);
    }

    private void assertOpenFloor(long seed, int x, int z) {
        String why = whyNotOpenFloor(seed, x, z);
        assertEquals(null, why, "seed " + seed + " " + x + " " + z);
    }

    /** Null if the column is walkable (plan) and built as solid floor with room for a body above. */
    private String whyNotOpenFloor(long seed, int x, int z) {
        if (!layout.isWalkable(seed, x, z)) {
            return "isWalkable says no";
        }
        int floor = layout.floorY();
        BlockState[] built = new BlockState[3];
        layout.fillColumn(seed, x, z, (y, state) -> {
            if (y >= floor && y <= floor + 2) {
                built[y - floor] = state;
            }
        });
        if (passable(built[0])) {
            return "no floor (" + built[0] + ")";
        }
        for (int i = 1; i <= 2; i++) {
            if (!passable(built[i])) {
                return "floor+" + i + " is " + built[i];
            }
        }
        return null;
    }

    private static boolean passable(BlockState state) {
        return state == null || state.isAir()
                || state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()).isEmpty();
    }
}
