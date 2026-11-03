package com.rickr.backrooms.world.layout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
 * walls, rooms and crates stand right outside the stairwell door and can seal it (seed
 * -7273252675751258371: the stairwell at -9351, -6398 opened into a 2-block pocket walled in by
 * the corridor block).
 */
class Level1ClearingTest {
    private static final long STUCK_SEED = -7273252675751258371L;
    private static final long[] SEEDS = {STUCK_SEED, 1L, 42L, -7L, 0x5EEDL, 2024L};
    /** Exit regions tested along each axis, centred on the origin. */
    private static final int REGIONS = 24;

    private final Level1Layout layout = (Level1Layout) BackroomsLevel.LEVEL_1.layout();

    @Test
    void theSealedStairwellOpensOntoFloor() {
        ExitLocator exits = layout.exits();
        assertArrayEquals(new int[] {-9351, -6398}, exits.nearest(STUCK_SEED, -9351, -6398));
        // The door (ring 5, -9351 -6403) opens onto a 2-block pocket at -9352..-9351, -6404 that
        // the corridor block walled in on every other side. All of it is now open floor.
        int[][] pocket = {{-9352, -6404}, {-9351, -6404}};
        int[][] pocketWalls = {{-9353, -6404}, {-9350, -6404}, {-9352, -6405}, {-9351, -6405}};
        for (int[] c : pocket) {
            assertTrue(Level1Areas.corridorZone(STUCK_SEED, c[0], c[1]), "the case no longer overlaps a corridor block");
            assertOpenFloor(STUCK_SEED, c[0], c[1]);
        }
        int floor = layout.floorY();
        for (int[] c : pocketWalls) {
            assertOpenFloor(STUCK_SEED, c[0], c[1]);
            // Worlds made before the fix have the corridor block's wall here; Level1ClearingRepair
            // must pick these columns and know what the old build put there.
            assertTrue(layout.clearingInCorridorBlock(STUCK_SEED, c[0], c[1]), "repair would skip " + c[0] + " " + c[1]);
            BlockState[] old = new BlockState[1];
            layout.fillCorridorBlock(STUCK_SEED, c[0], c[1], (y, state) -> {
                if (y == floor + 1) {
                    old[0] = state;
                }
            });
            assertTrue(!passable(old[0]), "the old build was open at " + c[0] + " " + c[1] + " (" + old[0] + ")");
        }
        assertTrue(layout.chunkTouchesClearing(STUCK_SEED, Math.floorDiv(-9351, 16) * 16, Math.floorDiv(-6404, 16) * 16));
        assertTrue(!layout.chunkTouchesClearing(STUCK_SEED, Math.floorDiv(-9351, 16) * 16 + 32, Math.floorDiv(-6404, 16) * 16));
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
