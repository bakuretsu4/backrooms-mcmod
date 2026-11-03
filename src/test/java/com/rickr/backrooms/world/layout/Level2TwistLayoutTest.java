package com.rickr.backrooms.world.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rickr.backrooms.world.BackroomsLevel;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

/**
 * Level 2 is one straight, one-block corridor that twists all the way along, and you always arrive
 * far from an exit. For several seeds and corridors, built from the real blocks the layout writes:
 * <ul>
 *   <li>every arrival (preferred, and the fallback search's hint) is on an open, level lane with
 *       room for a body, at least 500 blocks from the nearest exit along the corridor, both ways;</li>
 *   <li>exits are 1250-1650 blocks apart;</li>
 *   <li>the lane is the only walkable column besides the exit recess (nothing branches off), it has
 *       2 blocks of headroom under a ceiling at y43, and its collision-free width at body height is
 *       0.8-1.3 blocks;</li>
 *   <li>the twist is at full strength everywhere but at the exits, and its angle turns smoothly.</li>
 * </ul>
 */
class Level2TwistLayoutTest {
    private static final long[] SEEDS = {1L, 42L, -7L, 0x5EEDL, 2024L, 987654321L, Long.MIN_VALUE + 3, 31337L};
    /** Corridors (bands) tested per seed, around the origin. */
    private static final int BANDS = 3;
    /** Blocks of corridor tested per band, centred on x = 0. */
    private static final int LENGTH = 1600;
    /** Arrivals tried per seed. */
    private static final int ARRIVALS = 60;
    /** The nearest exit must be at least this far from any arrival. */
    private static final int EXIT_AWAY = 500;

    private final Level2Layout layout = (Level2Layout) BackroomsLevel.LEVEL_2.layout();
    private final BlockState[] column = new BlockState[64];

    @Test
    void everyArrivalIsOpenAndFarFromTheExits() {
        for (long seed : SEEDS) {
            SplittableRandom rng = new SplittableRandom(seed);
            for (int i = 0; i < ARRIVALS; i++) {
                int x = rng.nextInt(-200_000, 200_000);
                int z = rng.nextInt(-20_000, 20_000);
                BlockPos at = layout.preferredArrival(seed, x, z, null);
                String where = "seed " + seed + " sent to " + x + " " + z + ", arriving at " + at.getX() + " " + at.getY() + " " + at.getZ();
                assertTrue(layout.isSafeArrival(seed, at.getX(), at.getZ()), where + ": not a safe arrival column");
                assertEquals(LevelLayout.FLOOR_Y + 1, at.getY(), where + ": not on the floor");
                build(seed, at.getX(), at.getZ());
                assertTrue(clear(at.getY()) && clear(at.getY() + 1), where + ": no room for a body");
                assertTrue(solidTop(at.getY() - 1), where + ": nothing to stand on");
                assertFarFromExits(seed, at.getX(), at.getZ(), where);
                // The fallback search starts from the hint: that too must be far from the exits.
                int[] hint = layout.arrivalHint(seed, x, z);
                assertTrue(layout.isSafeArrival(seed, hint[0], hint[1]), where + ": the arrival hint is not a safe column");
                assertFarFromExits(seed, hint[0], hint[1], where + " (hint " + hint[0] + ")");
            }
        }
    }

    private void assertFarFromExits(long seed, int x, int z, String where) {
        int band = Level2Line.band(z);
        int j0 = Level2Line.exitCell(seed, band, x);
        int before = Integer.MIN_VALUE;
        int after = Integer.MAX_VALUE;
        for (int j = j0 - 2; j <= j0 + 2; j++) {
            int ex = Level2Line.exitX(seed, band, j);
            if (ex <= x) {
                before = Math.max(before, ex);
            } else {
                after = Math.min(after, ex);
            }
        }
        assertTrue(x - before >= EXIT_AWAY, where + ": an exit only " + (x - before) + " blocks back");
        assertTrue(after - x >= EXIT_AWAY, where + ": an exit only " + (after - x) + " blocks ahead");
        int[] exit = layout.exitPosition(seed, x, z);
        assertTrue(Math.abs(exit[0] - x) >= EXIT_AWAY, where + ": exitPosition only " + Math.abs(exit[0] - x) + " blocks away");
    }

    @Test
    void exitsAreFarApart() {
        for (long seed : SEEDS) {
            for (int band = -BANDS / 2; band <= BANDS / 2; band++) {
                for (int j = -20; j < 20; j++) {
                    int gap = Level2Line.exitX(seed, band, j + 1) - Level2Line.exitX(seed, band, j);
                    assertTrue(gap >= 1250 && gap <= 1650, "seed " + seed + " band " + band + ": exits " + gap + " apart");
                }
            }
        }
    }

    @Test
    void theCorridorIsOneStraightTightLane() {
        for (long seed : SEEDS) {
            for (int band = -BANDS / 2; band <= BANDS / 2; band++) {
                int c = Level2Line.centre(seed, band);
                for (int i = 0; i < LENGTH; i++) {
                    int x = i - LENGTH / 2;
                    String where = "seed " + seed + " at " + x + " " + c;
                    int j = Level2Line.nearestExit(seed, band, x);
                    int ex = Level2Line.exitX(seed, band, j);
                    int exitSide = Level2Line.exitSide(seed, band, j);
                    // Nothing else is walkable: no rooms, no doorways, no detours.
                    for (int dz = -Level2Line.REACH; dz <= Level2Line.REACH; dz++) {
                        boolean recess = x == ex && dz * exitSide > 0 && Math.abs(dz) < Level2Line.BAY_BACK;
                        assertEquals(dz == 0 || recess, layout.isWalkable(seed, x, c + dz), where + " dz " + dz + ": walkable off the lane");
                    }
                    build(seed, x, c);
                    assertTrue(clear(LevelLayout.FLOOR_Y + 1) && clear(LevelLayout.FLOOR_Y + 2), where + ": something solid in the lane");
                    assertTrue(solidTop(LevelLayout.FLOOR_Y), where + ": no floor");
                    assertTrue(!clear(LevelLayout.FLOOR_Y + 3), where + ": no ceiling at y43");
                    // (The inverted_pipe oddity stands its pipe in the wall cells on purpose: about 1.34 there.)
                    var site = layout.anomalyAt(seed, x, c);
                    boolean pipe = site != null && site.kind().equals(com.rickr.backrooms.world.anomaly.level2.Level2Sites.INVERTED_PIPE)
                            && site.anchorX() == x;
                    if (x != ex && !pipe) {
                        double width = laneWidth(seed, x, c);
                        assertTrue(width >= 0.8 - 1e-6 && width <= 1.3 + 1e-6, where + ": the lane is " + width + " wide");
                    }
                }
            }
        }
    }

    @Test
    void theTwistTurnsEverywhereButAtTheExits() {
        for (long seed : SEEDS) {
            for (int band = -BANDS / 2; band <= BANDS / 2; band++) {
                int c = Level2Line.centre(seed, band);
                double turned = 0.0;
                double last = Level2Twist.helix(seed, -LENGTH / 2, c);
                for (int x = -LENGTH / 2; x < LENGTH / 2; x++) {
                    int ex = Level2Line.exitX(seed, band, Level2Line.nearestExit(seed, band, x));
                    float amount = Level2Twist.amount(seed, x, c);
                    if (Math.abs(x - ex) > Level2Twist.EXIT_CALM + 6) {
                        assertEquals(1.0f, amount, 1e-6, "seed " + seed + " at " + x + ": the twist is not at full strength");
                    } else if (Math.abs(x - ex) <= Level2Twist.EXIT_CALM) {
                        assertEquals(0.0f, amount, 1e-6, "seed " + seed + " at " + x + ": the exit is not calm");
                    }
                    double h = Level2Twist.helix(seed, x, c);
                    double step = Math.abs(h - last);
                    step = Math.min(step, 2.0 * Math.PI - step);
                    assertTrue(step < 0.6, "seed " + seed + " at " + x + ": the twist jumps by " + step + " radians");
                    turned += step;
                    last = h;
                }
                // About one turn per 40-60 blocks on average: over 1600 blocks, well over 15 turns.
                assertTrue(turned > 15 * 2.0 * Math.PI, "seed " + seed + " band " + band + ": only " + turned / (2 * Math.PI) + " turns");
            }
        }
    }

    // ------------------------------------------------------------------------------------------

    /**
     * The collision-free width across the lane at x: the lane's own block plus the free space
     * beside it in each wall cell, at the narrowest height a standing body (41.0-42.8) passes.
     */
    private double laneWidth(long seed, int x, int c) {
        double narrowest = Double.MAX_VALUE;
        BlockState[][] walls = new BlockState[2][];
        for (int s = 0; s < 2; s++) {
            build(seed, x, c + (s == 0 ? -1 : 1));
            walls[s] = column.clone();
        }
        for (double h = LevelLayout.FLOOR_Y + 1.05; h < LevelLayout.FLOOR_Y + 2.8; h += 0.1) {
            double width = 1.0;
            for (int s = 0; s < 2; s++) {
                width += gap(walls[s][(int) Math.floor(h)], h - Math.floor(h), s == 0);
            }
            narrowest = Math.min(narrowest, width);
        }
        return narrowest;
    }

    /** Free space (blocks) in a wall cell between the lane and the first thing at height {@code y} (0..1 in the block). */
    private static double gap(BlockState state, double y, boolean north) {
        if (state == null) {
            return 1.0;
        }
        VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
        double free = 1.0;
        for (AABB box : shape.toAabbs()) {
            if (box.minY > y || box.maxY < y) {
                continue;
            }
            // The lane is south of a north wall cell (z = 1 is its lane side), north of a south one (z = 0).
            free = Math.min(free, north ? 1.0 - box.maxZ : box.minZ);
        }
        return Math.max(0.0, free);
    }

    /** Builds column (x, z) into {@link #column} (null = air). */
    private void build(long seed, int x, int z) {
        java.util.Arrays.fill(column, null);
        layout.fillColumn(seed, x, z, (y, state) -> {
            if (y >= 0 && y < column.length) {
                column[y] = state;
            }
        });
    }

    private VoxelShape shape(int y) {
        BlockState state = column[y];
        return state == null ? null : state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
    }

    /** Nothing to collide with in block y of the built column. */
    private boolean clear(int y) {
        VoxelShape s = shape(y);
        return s == null || s.isEmpty();
    }

    /** Block y has a top you can stand on (full height). */
    private boolean solidTop(int y) {
        VoxelShape s = shape(y);
        return s != null && !s.isEmpty() && s.max(Direction.Axis.Y) >= 1.0 - 1e-6;
    }
}
