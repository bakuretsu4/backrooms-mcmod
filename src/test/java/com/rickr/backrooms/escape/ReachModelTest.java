package com.rickr.backrooms.escape;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.function.BiFunction;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.Test;

/**
 * The escapability tests are only worth something if the movement model says "no" where a player
 * really is stuck. These hand-built worlds check the rules the level tests rely on: walls stop
 * you, wooden doors don't, iron doors do; with the Backrooms' jump key off a one-block ledge is a
 * wall but stairs and half steps are not; where jumping is allowed (Levels 490 and 227) one block
 * and a gap of two are fine, two blocks are not; a ladder or water gets you up anywhere; a
 * 1.5-block drop under a door's lintel can't be squeezed through.
 */
class ReachModelTest {
    private static final int FLOOR = LevelLayout.FLOOR_Y;
    /** The goal: anywhere at x >= 10 (the far side of whatever the test puts in the way). */
    private static final Reach.Goal FAR_SIDE = (w, x, y, z, fromY) -> x >= 10 && Reach.kind(w, x, y, z) != 0;

    /** A level built from a lambda: (x, z) -> what to add on top of a flat stone floor at y40. */
    private static final class Plan extends LevelLayout {
        private final BiFunction<Integer, Integer, BlockState[]> extra;

        Plan(BiFunction<Integer, Integer, BlockState[]> extra) {
            super(8, new ExitLocator(64, 8, 1));
            this.extra = extra;
        }

        @Override
        public void fillColumn(long seed, int x, int z, Column out) {
            out.set(FLOOR, Blocks.STONE.defaultBlockState());
            BlockState[] above = extra.apply(x, z);
            if (above != null) {
                for (int i = 0; i < above.length; i++) {
                    if (above[i] != null) {
                        out.set(FLOOR + 1 + i, above[i]);
                    }
                }
            }
        }

        @Override
        public boolean isWalkable(long seed, int x, int z) {
            return true;
        }
    }

    /** Reachable in a level where jumping is off (all but 490 and 227). */
    private static boolean reaches(BiFunction<Integer, Integer, BlockState[]> extra) {
        return reaches(extra, false);
    }

    private static boolean reaches(BiFunction<Integer, Integer, BlockState[]> extra, boolean jumping) {
        VoxelWorld w = new VoxelWorld(new Plan(extra), 0L, 0, 0, 24, s -> false);
        // Start standing at x = 0, z = 0, on whatever is built there.
        int y = FLOOR + 1;
        while (Reach.kind(w, 0, y, 0) == 0) {
            y++;
            assertTrue(y < FLOOR + 10, "test world has nowhere to stand at the start");
        }
        return Reach.search(w, 0, y, 0, 20, 0, FAR_SIDE, jumping).reached();
    }

    private static BlockState[] wall(int height) {
        BlockState[] column = new BlockState[height];
        java.util.Arrays.fill(column, Blocks.STONE.defaultBlockState());
        return column;
    }

    /** A wall across the world at x = 5, with {@code gap} in it at z = 0 (null = solid). */
    private static BiFunction<Integer, Integer, BlockState[]> wallWith(BlockState[] gap) {
        return (x, z) -> x != 5 ? null : z == 0 && gap != null ? gap : wall(6);
    }

    private static BlockState[] door(net.minecraft.world.level.block.Block block) {
        BlockState lower = block.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        return new BlockState[] {lower, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Blocks.STONE.defaultBlockState(),
                Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState()};
    }

    @Test
    void wallsStopYou() {
        assertFalse(reaches(wallWith(null)));
    }

    @Test
    void woodenDoorsOpenIronDoorsDont() {
        assertTrue(reaches(wallWith(door(Blocks.OAK_DOOR))));
        assertFalse(reaches(wallWith(door(Blocks.IRON_DOOR))));
    }

    @Test
    void withoutJumpingALedgeIsAWall() {
        assertFalse(reaches((x, z) -> x >= 5 ? wall(1) : null));
        // Two half-slab steps up are fine.
        BlockState slab = Blocks.STONE_SLAB.defaultBlockState();
        assertTrue(reaches((x, z) -> x == 5 ? new BlockState[] {slab} : x >= 6 ? wall(1) : null));
        // So is a stair, even with no headroom for a jump.
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState stairs = Blocks.STONE_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.EAST);
        assertTrue(reaches((x, z) -> x < 5 ? new BlockState[] {null, null, stone}
                : new BlockState[] {x == 5 ? stairs : stone, null, null, stone}));
    }

    @Test
    void withJumpingOneBlockIsAJumpTwoAreNot() {
        assertTrue(reaches((x, z) -> x >= 5 ? wall(1) : null, true));
        assertFalse(reaches((x, z) -> x >= 5 ? wall(2) : null, true));
        // A one-block step up under a ceiling 3 blocks above the floor: the jump hits your head.
        BlockState stone = Blocks.STONE.defaultBlockState();
        assertFalse(reaches((x, z) -> x < 5 ? new BlockState[] {null, null, stone} : new BlockState[] {stone, null, null, stone}, true));
    }

    @Test
    void gapsNeedAJump() {
        // A raised walkway (3 blocks over the floor) with a pit `width` columns wide at x = 5...
        java.util.function.IntFunction<BiFunction<Integer, Integer, BlockState[]>> pit =
                width -> (x, z) -> x >= 5 && x < 5 + width ? null : wall(3);
        assertFalse(reaches(pit.apply(1)), "a one-column gap without jumping");
        assertTrue(reaches(pit.apply(1), true));
        assertTrue(reaches(pit.apply(2), true));
        assertFalse(reaches(pit.apply(3), true), "a three-column gap is too far to jump");
    }

    @Test
    void laddersAndWaterGetYouUp() {
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST);
        // A 5-block cliff at x >= 5 with a ladder on its face (x = 4) at z = 0.
        assertFalse(reaches((x, z) -> x >= 5 ? wall(5) : null));
        assertTrue(reaches((x, z) -> x >= 5 ? wall(5) : x == 4 && z == 0 ? new BlockState[] {ladder, ladder, ladder, ladder, ladder} : null));
        // A pool 5 deep up to the cliff top lets you swim up and climb out.
        BlockState water = Blocks.WATER.defaultBlockState();
        assertTrue(reaches((x, z) -> x >= 5 ? wall(5) : x >= 1 ? new BlockState[] {water, water, water, water, water} : null));
    }

    @Test
    void cannotDropUnderALintel() {
        // You stand on a 1.5-block-high platform (stone + slab); the door beyond sits on the floor
        // with wall above it at floor + 3: your head can't get under the lintel.
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState slab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.BOTTOM);
        BlockState[] door = door(Blocks.OAK_DOOR);
        assertFalse(reaches((x, z) -> x < 5 ? new BlockState[] {stone, slab} : x == 5 ? (z == 0 ? door : wall(6)) : null));
    }
}
