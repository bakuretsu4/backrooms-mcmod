package com.rickr.backrooms.escape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rickr.backrooms.world.BackroomsLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * "I arrived here and couldn't get out": for every level on the path, arrive the way a player
 * coming out of the previous level's exit does (same code path as Travel: preferred arrival, else
 * the spiral search for a safe column), check the arrival spot is somewhere a player can stand,
 * then search on foot (see {@link Reach}) through the real generated blocks for the level's exit
 * trigger. Each (level, seed) is its own test, so a failure names the seed and the coordinates.
 *
 * <p>Levels 369 and 490 belong to someone else and are only reported (FriendLevelsReportTest);
 * Level 227 has no exit by design (Level227NoclipTest covers its way out).
 */
class LevelEscapabilityTest {
    /** Every level checked here, with the level whose exit leads into it on the normal path. */
    private static final BackroomsLevel[][] PATH = {
            {BackroomsLevel.LEVEL_0, null},
            {BackroomsLevel.LEVEL_1, BackroomsLevel.LEVEL_0},
            {BackroomsLevel.LEVEL_2, BackroomsLevel.LEVEL_1},
            {BackroomsLevel.LEVEL_4, BackroomsLevel.LEVEL_2},
            {BackroomsLevel.POOLROOMS, BackroomsLevel.LEVEL_4},
            {BackroomsLevel.LEVEL_37, BackroomsLevel.POOLROOMS},
            {BackroomsLevel.LEVEL_7, BackroomsLevel.LEVEL_369},
            {BackroomsLevel.LEVEL_39, BackroomsLevel.LEVEL_7},
            {BackroomsLevel.LEVEL_94, BackroomsLevel.LEVEL_39},
            {BackroomsLevel.LEVEL_9, BackroomsLevel.LEVEL_490},
    };

    @TestFactory
    Stream<DynamicTest> everyArrivalReachesTheExit() {
        List<DynamicTest> tests = new ArrayList<>();
        for (BackroomsLevel[] step : PATH) {
            for (long worldSeed : Escape.SEEDS) {
                BackroomsLevel level = step[0];
                BackroomsLevel from = step[1];
                tests.add(DynamicTest.dynamicTest(level.getSerializedName() + " seed " + worldSeed,
                        () -> checkArrival(level, from, worldSeed)));
            }
        }
        return tests.stream();
    }

    /** Arrives on {@code level} from {@code from}'s exit (or from reality, at random) and walks to the exit. */
    static Escape.Outcome run(BackroomsLevel level, @Nullable BackroomsLevel from, long worldSeed) {
        SplittableRandom random = new SplittableRandom(worldSeed ^ 0x9E3779B97F4A7C15L * (level.ordinal() + 1));
        int[] at = from != null
                ? Escape.exitCentreOf(from, worldSeed, random)
                : new int[] {random.nextInt(-20_000, 20_000), random.nextInt(-20_000, 20_000)};
        long seed = Escape.layoutSeed(worldSeed, level);
        Escape.Arrival arrival = Escape.arrive(level, seed, at[0], at[1], from);
        return Escape.check(arrival, Escape.exitOf(level));
    }

    private static void checkArrival(BackroomsLevel level, @Nullable BackroomsLevel from, long worldSeed) {
        long start = System.nanoTime();
        Escape.Outcome outcome = run(level, from, worldSeed);
        String line = level.getSerializedName() + " seed " + worldSeed + " (layout seed " + outcome.arrival().seed() + "): "
                + outcome.describe() + " [" + (System.nanoTime() - start) / 1_000_000 + " ms]";
        String status = !outcome.ok() ? "FAIL " : outcome.arrival().fallback() ? "WARN " : "ok   ";
        System.out.println("ESCAPE " + status + line);
        assertTrue(outcome.arrivalSafe(), "Arrival spot is not standable: " + line);
        assertTrue(outcome.result().reached(), "Exit not reachable: " + line);
    }

    /**
     * Every exit leads where the documented path says (BackroomsLevel's javadoc). Note: Level94Rules'
     * class doc, Level94Houses and the l94_dark_void block comment still say the dark door leads to
     * Level 9; the code (and BackroomsLevel) say Level 490. This test pins the code's answer.
     */
    @Test
    void exitsFollowTheDocumentedPath() {
        assertEquals(BackroomsLevel.LEVEL_1, BackroomsLevel.LEVEL_0.next());
        assertEquals(BackroomsLevel.LEVEL_2, BackroomsLevel.LEVEL_1.next());
        assertEquals(BackroomsLevel.LEVEL_4, BackroomsLevel.LEVEL_2.next());
        assertEquals(BackroomsLevel.POOLROOMS, BackroomsLevel.LEVEL_4.next());
        assertEquals(BackroomsLevel.LEVEL_37, BackroomsLevel.POOLROOMS.next());
        assertEquals(BackroomsLevel.LEVEL_369, BackroomsLevel.LEVEL_37.next());
        assertEquals(BackroomsLevel.LEVEL_7, BackroomsLevel.LEVEL_369.next());
        assertEquals(BackroomsLevel.LEVEL_39, BackroomsLevel.LEVEL_7.next());
        assertEquals(BackroomsLevel.LEVEL_94, BackroomsLevel.LEVEL_39.next());
        assertEquals(BackroomsLevel.LEVEL_490, BackroomsLevel.LEVEL_94.next());
        assertEquals(BackroomsLevel.LEVEL_9, BackroomsLevel.LEVEL_490.next());
        assertEquals(null, BackroomsLevel.LEVEL_9.next(), "Level 9's underpass leads back to reality");
        // Every level is reached exactly once along the path (except Level 227, which is off it).
        BackroomsLevel at = BackroomsLevel.LEVEL_0;
        int steps = 0;
        while (at != null) {
            steps++;
            at = at.next();
        }
        assertEquals(BackroomsLevel.values().length - 1, steps, "the path should visit every level but 227 once");
    }

    /** Falling out of a level only ever happens where the level means it to (the fall triggers). */
    @Test
    void fallTriggersSitBelowTheFloor() {
        for (BackroomsLevel level : new BackroomsLevel[] {BackroomsLevel.LEVEL_0, BackroomsLevel.LEVEL_1, BackroomsLevel.LEVEL_4,
                BackroomsLevel.POOLROOMS, BackroomsLevel.LEVEL_37, BackroomsLevel.LEVEL_39}) {
            assertTrue(level.transferBelowY() < level.layout().floorY(),
                    level.getSerializedName() + ": transferBelowY " + level.transferBelowY() + " is above the floor");
        }
        for (BackroomsLevel level : new BackroomsLevel[] {BackroomsLevel.LEVEL_2, BackroomsLevel.LEVEL_7, BackroomsLevel.LEVEL_9,
                BackroomsLevel.LEVEL_94, BackroomsLevel.LEVEL_227, BackroomsLevel.LEVEL_369, BackroomsLevel.LEVEL_490}) {
            assertEquals(Integer.MIN_VALUE, level.transferBelowY(), level.getSerializedName() + " is never left by falling");
        }
    }
}
