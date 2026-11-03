package com.rickr.backrooms.world.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rickr.backrooms.world.BackroomsLevel;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

/**
 * Level 2's cramped, twisting corridor must stay walkable without jumping (Level 2 switches the
 * jump key off). For several seeds and corridors, built from the real blocks the layout writes:
 * <ul>
 *   <li>(a) the arrival column is open, level, with room for a body, and along the whole corridor
 *       (outside the detours, which have their own way round) the centre column and one beside it
 *       are walkable, with nothing to collide with at body height;</li>
 *   <li>(b) a twist stretch ({@link Level2Twist#amount} > 0) lies within 30 blocks of every
 *       arrival, and stretches cover a third to a half of the corridor;</li>
 *   <li>(c) a banked stretch's floor (built as half slabs) only ever steps half a block between
 *       neighbouring walkable columns, and the floor the layout reports ({@link LevelLayout#feetY})
 *       is the floor it builds.</li>
 * </ul>
 */
class Level2TwistLayoutTest {
    private static final long[] SEEDS = {1L, 42L, -7L, 0x5EEDL, 2024L, 987654321L};
    /** Corridors (bands) tested per seed, around the origin. */
    private static final int BANDS = 3;
    /** Blocks of corridor tested per band, centred on x = 0. */
    private static final int LENGTH = 640;
    /** Arrivals tried per seed. */
    private static final int ARRIVALS = 40;
    /** A twist stretch must be at most this far (along the corridor) from an arrival. */
    private static final int TWIST_NEAR = 30;

    private final Level2Layout layout = (Level2Layout) BackroomsLevel.LEVEL_2.layout();
    private final BlockState[] column = new BlockState[64];

    @Test
    void arrivalsAreOpenLevelAndNearATwist() {
        for (long seed : SEEDS) {
            SplittableRandom rng = new SplittableRandom(seed);
            for (int i = 0; i < ARRIVALS; i++) {
                int x = rng.nextInt(-20000, 20000);
                int z = rng.nextInt(-2000, 2000);
                BlockPos at = layout.preferredArrival(seed, x, z, null);
                String where = "seed " + seed + " arriving at " + at.getX() + " " + at.getY() + " " + at.getZ();
                assertTrue(layout.isSafeArrival(seed, at.getX(), at.getZ()), where + ": not a safe arrival column");
                assertEquals(LevelLayout.FLOOR_Y + 1, at.getY(), where + ": not on the floor");
                assertEquals(at.getY(), layout.feetY(seed, at.getX(), at.getZ()), 1e-9, where + ": on a raised step");
                build(seed, at.getX(), at.getZ());
                assertTrue(clear(at.getY()) && clear(at.getY() + 1), where + ": no room for a body");
                assertTrue(solidTop(at.getY() - 1), where + ": nothing to stand on");
                boolean near = false;
                for (int d = -TWIST_NEAR; d <= TWIST_NEAR && !near; d++) {
                    near = Level2Twist.amount(seed, at.getX() + d, at.getZ()) > 0.0f;
                }
                assertTrue(near, where + ": no twist stretch within " + TWIST_NEAR + " blocks");
            }
        }
    }

    @Test
    void twistStretchesCoverAThirdToAHalfOfTheCorridor() {
        for (long seed : SEEDS) {
            int c = Level2Line.centre(seed, 0);
            int twisting = 0;
            int total = 20000;
            for (int x = 0; x < total; x++) {
                if (Level2Twist.amount(seed, x, c) > 0.0f) {
                    twisting++;
                }
            }
            double share = twisting / (double) total;
            assertTrue(share >= 0.30 && share <= 0.50, "seed " + seed + ": twist stretches cover " + share);
            assertEquals(0.0f, Level2Twist.amount(seed, 0, c + Level2Line.REACH + 2), "twisting between corridors");
        }
    }

    @Test
    void theLaneStaysTwoWideAndBankedFloorsStepByHalves() {
        int raised = 0;
        for (long seed : SEEDS) {
            for (int band = -BANDS / 2; band <= BANDS / 2; band++) {
                int c = Level2Line.centre(seed, band);
                double[][] feet = new double[LENGTH][3];
                boolean[][] walk = new boolean[LENGTH][3];
                for (int i = 0; i < LENGTH; i++) {
                    int x = i - LENGTH / 2;
                    boolean detour = Level2Detours.isDetour(Level2Line.segType(seed, band, Level2Line.segment(seed, band, x)));
                    for (int dz = -1; dz <= 1; dz++) {
                        int z = c + dz;
                        String where = "seed " + seed + " at " + x + " " + z;
                        walk[i][dz + 1] = layout.isWalkable(seed, x, z);
                        feet[i][dz + 1] = layout.feetY(seed, x, z);
                        if (!walk[i][dz + 1]) {
                            continue;
                        }
                        build(seed, x, z);
                        double built = builtFeet();
                        assertEquals(feet[i][dz + 1], built, 1e-9, where + ": the floor is built at " + built
                                + " but reported at " + feet[i][dz + 1]);
                        int y = (int) Math.floor(built);
                        boolean onSlab = built != y;
                        assertTrue(clear(y + 1) && (onSlab ? clear(y + 2) : clear(y)), where + ": no room for a body above the floor");
                        if (built > LevelLayout.FLOOR_Y + 1) {
                            raised++;
                        }
                    }
                    if (!detour) {
                        String where = "seed " + seed + " at " + x + " " + c;
                        assertTrue(walk[i][1] && (walk[i][0] || walk[i][2]), where + ": the lane is not two wide");
                    }
                }
                for (int i = 0; i < LENGTH; i++) {
                    for (int k = 0; k < 3; k++) {
                        if (!walk[i][k]) {
                            continue;
                        }
                        String where = "seed " + seed + " at " + (i - LENGTH / 2) + " " + (c + k - 1);
                        if (i + 1 < LENGTH && walk[i + 1][k]) {
                            assertTrue(Math.abs(feet[i + 1][k] - feet[i][k]) <= 0.5, where + ": a step of more than half a block along the lane");
                        }
                        if (k < 2 && walk[i][k + 1]) {
                            assertTrue(Math.abs(feet[i][k + 1] - feet[i][k]) <= 0.5, where + ": a step of more than half a block across the lane");
                        }
                    }
                }
            }
        }
        assertTrue(raised > 0, "no banked stretch raised a floor anywhere: the half-step check tested nothing");
    }

    // ------------------------------------------------------------------------------------------

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
        return s != null && !s.isEmpty() && s.max(net.minecraft.core.Direction.Axis.Y) >= 1.0 - 1e-6;
    }

    /** The height you stand at on the built column: on the floor block, or on a slab or block above it. */
    private double builtFeet() {
        int y = LevelLayout.FLOOR_Y + 1;
        while (y < column.length - 1) {
            VoxelShape s = shape(y);
            if (s == null || s.isEmpty()) {
                return y;
            }
            double top = s.max(net.minecraft.core.Direction.Axis.Y);
            if (top < 1.0 - 1e-6) {
                return y + top;
            }
            y++;
        }
        return y;
    }
}
