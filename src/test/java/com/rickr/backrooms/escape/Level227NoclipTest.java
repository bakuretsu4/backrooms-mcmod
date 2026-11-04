package com.rickr.backrooms.escape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Level227Noclip;
import com.rickr.backrooms.world.layout.Level227Tree;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Level 227 has no exit by design: the tree's motion noclips you out to a random level after a
 * while (Level227Noclip). So "escapable" here means: the noclip never sends you back into the
 * tree, every level it can send you to has somewhere safe to land wherever on the tree you were,
 * and from that landing the level's own exit can be reached. Also: every planned arrival point
 * on the tree is somewhere you can stand (not in the void under the branches).
 */
class Level227NoclipTest {
    /**
     * Every level the noclip may pick: everything up to Level 94 on the path (from there the path
     * still leads back to Level 9 or 94, where you were dragged in from). Never 227, 228, 369 or 490.
     */
    private static final BackroomsLevel[] OURS = {BackroomsLevel.LEVEL_0, BackroomsLevel.LEVEL_1, BackroomsLevel.LEVEL_2,
            BackroomsLevel.LEVEL_4, BackroomsLevel.POOLROOMS, BackroomsLevel.LEVEL_37, BackroomsLevel.LEVEL_7,
            BackroomsLevel.LEVEL_39, BackroomsLevel.LEVEL_94, BackroomsLevel.LEVEL_9};

    @Test
    void noclipOnlyPicksLevelsWithAWayBack() {
        Map<BackroomsLevel, Integer> picks = new EnumMap<>(BackroomsLevel.class);
        RandomSource random = RandomSource.create(227L);
        for (int i = 0; i < 20_000; i++) {
            picks.merge(Level227Noclip.dangerousLevel(random), 1, Integer::sum);
        }
        assertFalse(picks.containsKey(BackroomsLevel.LEVEL_227), "the noclip sent someone back into Level 227: " + picks);
        assertFalse(picks.containsKey(BackroomsLevel.LEVEL_369), "the noclip sent someone past Level 94 (369): " + picks);
        assertFalse(picks.containsKey(BackroomsLevel.LEVEL_228), "the noclip sent someone past Level 94 (228): " + picks);
        assertFalse(picks.containsKey(BackroomsLevel.LEVEL_490), "the noclip sent someone past Level 94 (490): " + picks);
        assertEquals(Set.of(OURS), picks.keySet(), "some level is never picked: " + picks);
        assertFalse(BackroomsLevel.LEVEL_227.layout().hasExits(), "Level 227 must report that it has no exit");
    }

    /** The noclip lands you via Travel.sendToQuietly(target, x, z) with your Level 227 x/z, from LEVEL_227. */
    @TestFactory
    Stream<DynamicTest> everyNoclipLandingCanReachItsExit() {
        List<DynamicTest> tests = new ArrayList<>();
        List<Level227Tree.Arrival> spots = Level227Tree.arrivals();
        for (BackroomsLevel target : OURS) {
            for (int i = 0; i < 3; i++) {
                long worldSeed = Escape.SEEDS[i];
                // Where on the tree you were: one of its planned arrival points (where people are).
                Level227Tree.Arrival from = spots.get(new SplittableRandom(worldSeed).nextInt(spots.size()));
                tests.add(DynamicTest.dynamicTest(target.getSerializedName() + " from tree spot " + from.x() + " " + from.z()
                        + " seed " + worldSeed, () -> {
                    long seed = Escape.layoutSeed(worldSeed, target);
                    Escape.Arrival arrival = Escape.arrive(target, seed, from.x(), from.z(), BackroomsLevel.LEVEL_227);
                    Escape.Outcome outcome = Escape.check(arrival, Escape.exitOf(target));
                    String line = "noclip 227 -> " + outcome.describe();
                    System.out.println("NOCLIP " + (outcome.ok() ? "ok   " : "FAIL ") + line);
                    assertTrue(outcome.arrivalSafe(), "Noclip landing is not standable: " + line);
                    assertTrue(outcome.result().reached(), "Exit not reachable after the noclip: " + line);
                }));
            }
        }
        return tests.stream();
    }

    /** Every planned arrival point on the tree (Level227Layout.arrivalPos) is a branch top you can stand on. */
    @Test
    void everyTreeArrivalPointIsStandable() {
        LevelLayout layout = BackroomsLevel.LEVEL_227.layout();
        StringBuilder bad = new StringBuilder();
        int checked = 0;
        int failed = 0;
        for (Level227Tree.Arrival a : Level227Tree.arrivals()) {
            checked++;
            BlockPos spot = layout.preferredArrival(0L, a.x(), a.z(), null);
            VoxelWorld world = new VoxelWorld(layout, 0L, a.x(), a.z(), 3, s -> false);
            boolean ok = spot != null && spot.getY() > Level227Tree.VOID_DEATH_Y && Reach.kind(world, spot.getX(), spot.getY(), spot.getZ()) != 0;
            if (!ok) {
                failed++;
                if (failed <= 8) {
                    bad.append("\n  arrival ").append(a.x()).append(' ').append(a.z()).append(" -> ").append(spot)
                            .append(spot == null ? "" : ": " + Escape.blocksAround(world, spot.getX(), spot.getY(), spot.getZ()));
                }
            }
        }
        System.out.println("L227ARRIVALS " + failed + " of " + checked + " tree arrival points are not standable" + bad);
        assertEquals(0, failed, failed + " of " + checked + " Level 227 arrival points are not standable:" + bad);
    }
}
