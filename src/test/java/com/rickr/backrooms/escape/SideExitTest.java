package com.rickr.backrooms.escape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.ClayDoors;
import com.rickr.backrooms.world.Level227Noclip;
import com.rickr.backrooms.world.layout.Level39Houses;
import com.rickr.backrooms.world.layout.Level39Layout;
import com.rickr.backrooms.world.layout.Level39Tunnels;
import com.rickr.backrooms.world.layout.Level94Layout;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * The line through the levels and the side ways off it (BackroomsLevel's javadoc):
 * <ul>
 *   <li>the line ({@link BackroomsLevel#next()}) visits every level but 227 exactly once, in the
 *       order of the wiki's numbers, and ends in reality;</li>
 *   <li>the rule for side ways: the level you land in always has a way back to the level you left.
 *       Going back down the line needs nothing (walk it forward again); every side way that skips
 *       ahead needs a side way straight back;</li>
 *   <li>Level 227 is off the line and only drops you where the line still leads back to the levels
 *       you are dragged in from (9 and 94);</li>
 *   <li>the ways back are really generated: Level 39's sunlit dead ends and clay doors, Level 94's
 *       clay doors.</li>
 * </ul>
 */
class SideExitTest {
    /** The line, in order (reality before Level 0 and after the last). */
    private static final List<BackroomsLevel> LINE = List.of(BackroomsLevel.LEVEL_0, BackroomsLevel.LEVEL_1,
            BackroomsLevel.LEVEL_2, BackroomsLevel.LEVEL_4, BackroomsLevel.LEVEL_7, BackroomsLevel.LEVEL_9,
            BackroomsLevel.POOLROOMS, BackroomsLevel.LEVEL_37, BackroomsLevel.LEVEL_39, BackroomsLevel.LEVEL_94,
            BackroomsLevel.LEVEL_369, BackroomsLevel.LEVEL_490);

    /** One side way off the line: where it is, where it leads, and what it is. */
    private record Side(BackroomsLevel from, BackroomsLevel to, String what) {}

    /**
     * Every side way in the mod (keep this list in step with BackroomsLevel's javadoc and the
     * README's "The way out"). Level 227's ways in and out are checked separately.
     */
    private static final List<Side> SIDE = List.of(
            new Side(BackroomsLevel.POOLROOMS, BackroomsLevel.LEVEL_39, "the sunlit archway (Level37Rules)"),
            new Side(BackroomsLevel.LEVEL_9, BackroomsLevel.LEVEL_94, "a clay front door (ClayDoors)"),
            new Side(BackroomsLevel.LEVEL_9, BackroomsLevel.LEVEL_39, "the Crimson Field's gondola (Travel.sendToLevel39Sky)"),
            new Side(BackroomsLevel.LEVEL_9, BackroomsLevel.LEVEL_2, "a Crimson Field foxhole (CrimsonField)"),
            new Side(BackroomsLevel.LEVEL_9, BackroomsLevel.LEVEL_0, "the Crimson Field's gloom (CrimsonField)"),
            new Side(BackroomsLevel.LEVEL_37, BackroomsLevel.LEVEL_0, "a void opening (VoidOpening)"),
            new Side(BackroomsLevel.LEVEL_37, BackroomsLevel.POOLROOMS, "a dark opening (DarkOpening)"),
            new Side(BackroomsLevel.LEVEL_94, BackroomsLevel.LEVEL_7, "a flooded house (Level94Rules)"),
            new Side(BackroomsLevel.LEVEL_94, BackroomsLevel.LEVEL_1, "the light behind the throne (Level94Rules)"),
            new Side(BackroomsLevel.LEVEL_94, BackroomsLevel.LEVEL_9, "a clay front door (ClayDoors)"),
            new Side(BackroomsLevel.LEVEL_39, BackroomsLevel.LEVEL_9, "a clay front door in town (ClayDoors)"),
            new Side(BackroomsLevel.LEVEL_39, BackroomsLevel.POOLROOMS, "a sunlit dead end of the tunnels (Level39Rules)"));

    @Test
    void theLineVisitsEveryLevelOnceInTheWikisOrder() {
        List<BackroomsLevel> walked = new ArrayList<>();
        Set<BackroomsLevel> seen = EnumSet.noneOf(BackroomsLevel.class);
        for (BackroomsLevel at = BackroomsLevel.LEVEL_0; at != null; at = at.next()) {
            assertTrue(seen.add(at), "the line comes back to " + at + ": " + walked);
            walked.add(at);
        }
        assertEquals(LINE, walked, "the line (BackroomsLevel.next) is not the documented order");
        Set<BackroomsLevel> off = EnumSet.complementOf(EnumSet.copyOf(walked));
        assertEquals(EnumSet.of(BackroomsLevel.LEVEL_227), off, "only Level 227 is off the line");
    }

    @Test
    void everySideWayAheadHasAWayStraightBack() {
        StringBuilder missing = new StringBuilder();
        for (Side side : SIDE) {
            assertNotEquals(side.from().next(), side.to(), side.what() + " is the main exit, not a side way");
            if (LINE.indexOf(side.to()) < LINE.indexOf(side.from())) {
                continue; // back down the line: walking it forward again gets you there
            }
            boolean back = SIDE.stream().anyMatch(s -> s.from() == side.to() && s.to() == side.from());
            if (!back) {
                missing.append("\n  ").append(side.from()).append(" -> ").append(side.to()).append(" (").append(side.what())
                        .append(") has no way back from ").append(side.to());
            }
        }
        assertEquals("", missing.toString(), "side ways ahead without a way back:" + missing);
    }

    @Test
    void clayDoorsLeadWhereTheSideWaysSay() {
        for (BackroomsLevel level : BackroomsLevel.values()) {
            BackroomsLevel to = ClayDoors.destination(level);
            boolean listed = SIDE.stream().anyMatch(s -> s.from() == level && s.to() == to && s.what().contains("clay"));
            assertTrue(to == null || listed, "a clay door in " + level + " leads to " + to + ", which isn't a listed side way");
        }
        assertEquals(BackroomsLevel.LEVEL_94, ClayDoors.destination(BackroomsLevel.LEVEL_9));
        assertEquals(BackroomsLevel.LEVEL_9, ClayDoors.destination(BackroomsLevel.LEVEL_94));
        assertEquals(BackroomsLevel.LEVEL_9, ClayDoors.destination(BackroomsLevel.LEVEL_39));
    }

    /** Level 227 drags you in from Level 9 and 94; it may only drop you where the line leads back to one of them. */
    @Test
    void level227OnlyDropsYouWhereTheLineLeadsBack() {
        int last = Math.max(LINE.indexOf(BackroomsLevel.LEVEL_9), LINE.indexOf(BackroomsLevel.LEVEL_94));
        RandomSource random = RandomSource.create(2270L);
        for (int i = 0; i < 20_000; i++) {
            BackroomsLevel to = Level227Noclip.dangerousLevel(random);
            int at = LINE.indexOf(to);
            assertTrue(at >= 0 && at <= last, "Level 227 dropped someone in " + to + ", past Level 94 or off the line");
        }
    }

    /** The ways back exist in the generated world: within a few towns of Level 39, and near any point of Level 94. */
    @TestFactory
    Stream<DynamicTest> theWaysBackAreGenerated() {
        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            long worldSeed = Escape.SEEDS[i];
            tests.add(DynamicTest.dynamicTest("level_39 sunlit dead end, seed " + worldSeed, () -> level39SunlitEnd(worldSeed)));
            tests.add(DynamicTest.dynamicTest("level_39 clay door, seed " + worldSeed, () -> level39ClayDoor(worldSeed)));
            tests.add(DynamicTest.dynamicTest("level_94 clay door, seed " + worldSeed, () -> level94ClayDoor(worldSeed)));
        }
        return tests.stream();
    }

    private static void level39SunlitEnd(long worldSeed) {
        long seed = Escape.layoutSeed(worldSeed, BackroomsLevel.LEVEL_39);
        LevelLayout layout = BackroomsLevel.LEVEL_39.layout();
        BlockPos arch = null;
        for (int k = 0; k < 9 && arch == null; k++) {
            arch = Level39Tunnels.nearestSunlitEnd(seed, (k % 3 - 1) * Level39Layout.CELL, (k / 3 - 1) * Level39Layout.CELL);
        }
        assertNotNull(arch, "seed " + worldSeed + ": no sunlit dead end in nine towns of Level 39");
        VoxelWorld world = new VoxelWorld(layout, seed, arch.getX(), arch.getZ(), 2, s -> false);
        String where = "seed " + worldSeed + ", sunlit arch at " + arch.toShortString() + ": " + Escape.blocksAround(world, arch.getX(), arch.getY(), arch.getZ());
        assertTrue(Reach.kind(world, arch.getX(), arch.getY(), arch.getZ()) != 0, "you can't stand in the arch: " + where);
        boolean daylight = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState s = world.state(arch.getX() + d.getStepX(), arch.getZ() + d.getStepZ(), arch.getY());
            daylight |= s != null && s.is(ModBlocks.L37_SUNLIT_VOID.get());
        }
        assertTrue(daylight, "no wall of daylight behind the arch: " + where);
    }

    private static void level39ClayDoor(long worldSeed) {
        long seed = Escape.layoutSeed(worldSeed, BackroomsLevel.LEVEL_39);
        BlockPos door = null;
        for (int k = 0; k < 3 && door == null; k++) {
            door = Level39Houses.nearestClayDoor(seed, k * Level39Layout.CELL, 0);
        }
        assertNotNull(door, "seed " + worldSeed + ": no clay door in three towns of Level 39");
        assertClayDoorAt(BackroomsLevel.LEVEL_39.layout(), seed, door.getX(), door.getY(), door.getZ(), "seed " + worldSeed + ", Level 39");
    }

    private static void level94ClayDoor(long worldSeed) {
        long seed = Escape.layoutSeed(worldSeed, BackroomsLevel.LEVEL_94);
        int[] door = Level94Layout.nearestClayDoor(seed, 0, 0, 1500);
        assertNotNull(door, "seed " + worldSeed + ": no clay door within 1500 blocks in Level 94");
        System.out.println("WAYBACK level_94 seed " + worldSeed + ": nearest clay door to 0,0 at " + door[0] + " " + door[1]
                + " (" + (int) Math.hypot(door[0], door[1]) + " blocks)");
        LevelLayout layout = BackroomsLevel.LEVEL_94.layout();
        BlockState[] column = column(layout, seed, door[0], door[1]);
        int y = -1;
        for (int i = 0; i < column.length && y < 0; i++) {
            if (isLowerClayDoor(column[i])) {
                y = i;
            }
        }
        assertTrue(y >= 0, "seed " + worldSeed + ": no clay door in Level 94's column " + door[0] + " " + door[1]);
        assertClayDoorAt(layout, seed, door[0], y, door[1], "seed " + worldSeed + ", Level 94");
    }

    /** A clay door's lower half at (x, y, z), with its upper half above it and room to walk up to it on one side. */
    private static void assertClayDoorAt(LevelLayout layout, long seed, int x, int y, int z, String where) {
        BlockState[] column = column(layout, seed, x, z);
        assertTrue(isLowerClayDoor(column[y]), where + ": no clay door at " + x + " " + y + " " + z + " (found " + column[y] + ")");
        assertTrue(column[y + 1] != null && column[y + 1].is(ModBlocks.L94_CLAY_DOOR.get()), where + ": the clay door has no upper half");
        VoxelWorld world = new VoxelWorld(layout, seed, x, z, 2, s -> false);
        boolean approach = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            approach |= Reach.kind(world, x + d.getStepX(), y, z + d.getStepZ()) != 0;
        }
        assertTrue(approach, where + ": nowhere to stand beside the clay door at " + x + " " + y + " " + z);
    }

    private static boolean isLowerClayDoor(BlockState s) {
        return s != null && s.is(ModBlocks.L94_CLAY_DOOR.get()) && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    /** The final blocks of one column, as the chunk generator writes them (later writes win). */
    private static BlockState[] column(LevelLayout layout, long seed, int x, int z) {
        BlockState[] out = new BlockState[layout.worldHeight()];
        layout.fillColumn(seed, x, z, (y, state) -> {
            if (y >= 0 && y < out.length) {
                out[y] = state;
            }
        });
        return out;
    }
}
