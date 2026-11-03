package com.rickr.backrooms.escape;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.layout.Level94Castle;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

/**
 * Level 94's way on is a dark-doored house. Two things a player relies on, checked for every exit
 * region in a block of the world: the spot /backrooms locate, the exit sound and the camcorder
 * signal point at ({@link LevelLayout#exitPosition}, documented as "the cell in front of the
 * nearest dark-door house's door") really is in front of a dark door; and from there you can
 * walk through the door into the dark.
 */
class Level94ExitTest {
    private static final int REGIONS = 5;
    /** Level94Layout.EXIT_REGION (package-private there): one exit house per square this size. */
    private static final int EXIT_REGION = 400;
    /** How far round the door to look for the house's dark (it fills the house behind the door). */
    private static final int LOOK = 12;
    /** Examples listed per kind of failure. */
    private static final int EXAMPLES = 6;

    @Test
    void locatePointsAtEnterableDarkDoors() {
        LevelLayout layout = BackroomsLevel.LEVEL_94.layout();
        StringBuilder flooded = new StringBuilder();
        StringBuilder sealed = new StringBuilder();
        int checked = 0;
        int floodedCount = 0;
        int sealedCount = 0;
        for (long worldSeed : new long[] {1L, 42L, -7L, 0x5EEDL}) {
            long seed = Escape.layoutSeed(worldSeed, BackroomsLevel.LEVEL_94);
            Set<Long> seen = new HashSet<>();
            for (int rx = -REGIONS; rx <= REGIONS; rx++) {
                for (int rz = -REGIONS; rz <= REGIONS; rz++) {
                    int[] door = layout.exitPosition(seed, rx * EXIT_REGION + EXIT_REGION / 2, rz * EXIT_REGION + EXIT_REGION / 2);
                    if (!seen.add(((long) door[0] << 32) ^ (door[1] & 0xFFFFFFFFL))) {
                        continue;
                    }
                    checked++;
                    VoxelWorld world = new VoxelWorld(layout, seed, door[0], door[1], 40,
                            s -> s.is(ModBlocks.L94_DARK_VOID.get()));
                    String where = "\n  layout seed " + seed + ": door front at " + door[0] + " " + door[1];
                    if (!near(world, door, ModBlocks.L94_DARK_VOID.get().defaultBlockState())) {
                        floodedCount++;
                        if (floodedCount <= EXAMPLES) {
                            flooded.append(where).append(near(world, door, ModBlocks.L94_FLOOD_DARK.get().defaultBlockState())
                                    ? " is a FLOODED house (leads back to Level 7)" : " has no dark house at all");
                        }
                        continue;
                    }
                    int y = standingY(world, door[0], door[1]);
                    Reach.Result r = y < 0 ? null : Reach.search(world, door[0], y, door[1], door[0], door[1],
                            (w, x, yy, z, fromY) -> yy >= 0 && ((w.flags(x, yy, z) & VoxelWorld.GOAL) != 0
                                    || (w.flags(x, yy + 1, z) & VoxelWorld.GOAL) != 0), false);
                    if (r == null || !r.reached()) {
                        sealedCount++;
                        if (sealedCount <= EXAMPLES) {
                            sealed.append(where).append(r == null ? ": nowhere to stand in front of the door"
                                    : ": can't get through the door into the dark; " + r.describe()
                                            + "\n    " + Escape.blocksAround(world, r.nearX(), r.nearY(), r.nearZ()));
                        }
                    }
                }
            }
        }
        String located = "Level 94: " + floodedCount + " of " + checked + " located exits are not dark-door houses (first "
                + EXAMPLES + "):" + flooded;
        String enterable = "Level 94: " + sealedCount + " of " + (checked - floodedCount)
                + " dark doors can't be walked through from their garden path (first " + EXAMPLES + "):" + sealed;
        System.out.println("L94EXITS " + located + "\nL94EXITS " + enterable);
        int notDark = floodedCount;
        int notEnterable = sealedCount;
        assertAll(() -> assertEquals(0, notDark, located), () -> assertEquals(0, notEnterable, enterable));
    }

    /**
     * Every arrival finds open road to stand on, never Travel's unchecked fallback: from Level 39's
     * exits, from anywhere, and right at a castle, where the hilltop houses have drifted apart into
     * fragments and the crossroads can have no house in sight (seeds 1 and 2024 once landed unchecked).
     */
    @Test
    void arrivalsAlwaysFindOpenRoad() {
        LevelLayout layout = BackroomsLevel.LEVEL_94.layout();
        StringBuilder bad = new StringBuilder();
        int checked = 0;
        int failed = 0;
        for (long worldSeed : Escape.SEEDS) {
            long seed = Escape.layoutSeed(worldSeed, BackroomsLevel.LEVEL_94);
            SplittableRandom random = new SplittableRandom(worldSeed ^ 0x94A5L);
            List<int[]> points = new ArrayList<>();
            for (int k = 0; k < 3; k++) {
                points.add(Escape.exitCentreOf(BackroomsLevel.LEVEL_39, worldSeed, random));
                points.add(new int[] {random.nextInt(-20_000, 20_000), random.nextInt(-20_000, 20_000)});
            }
            points.add(Level94Castle.nearest(seed, random.nextInt(-20_000, 20_000), random.nextInt(-20_000, 20_000)));
            for (int[] at : points) {
                checked++;
                Escape.Arrival a = Escape.arrive(BackroomsLevel.LEVEL_94, seed, at[0], at[1], BackroomsLevel.LEVEL_39);
                VoxelWorld world = new VoxelWorld(layout, seed, a.x(), a.z(), 3, s -> false);
                if (a.fallback() || Reach.kind(world, a.x(), a.feetBlock(), a.z()) == 0) {
                    if (++failed <= EXAMPLES) {
                        bad.append("\n  layout seed ").append(seed).append(" sent to ").append(at[0]).append(' ').append(at[1])
                                .append(": ").append(a.describe()).append("\n    ")
                                .append(Escape.blocksAround(world, a.x(), a.feetBlock(), a.z()));
                    }
                }
            }
        }
        assertEquals(0, failed, "Level 94: " + failed + " of " + checked + " arrivals found nowhere safe (first "
                + EXAMPLES + "):" + bad);
    }

    private static boolean near(VoxelWorld world, int[] door, BlockState target) {
        for (int dx = -LOOK; dx <= LOOK; dx++) {
            for (int dz = -LOOK; dz <= LOOK; dz++) {
                boolean[] found = {false};
                world.layout.fillColumn(world.seed, door[0] + dx, door[1] + dz, (y, s) -> found[0] |= s == target);
                if (found[0]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Lowest feet block at (x, z) a player could stand in, or -1. */
    private static int standingY(VoxelWorld world, int x, int z) {
        for (int y = 1; y < world.height - 2; y++) {
            if (Reach.kind(world, x, y, z) != 0) {
                return y;
            }
        }
        return -1;
    }
}
