package com.rickr.backrooms.world.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.Test;

/**
 * Level4DoorRepair rebuilds walled-up door and doorway columns (from a broken build) with
 * {@link Level4Layout#fillPlanColumn}, writing every block between the floor and the ceiling. That
 * is only a repair if what it writes there is a way through: a door you can open by hand, or open
 * air, at both body heights (floor + 1 and floor + 2). This checks every DOOR / DOORWAY column the
 * repair would touch in a block of the plan, for a few seeds, and that it never puts anything
 * solid where a body goes.
 */
class Level4DoorRepairTest {
    private static final long[] SEEDS = {1L, 42L, -7L, 0x5EEDL};
    private static final int SPAN = 600;

    @Test
    void repairedColumnsAreAlwaysAWayThrough() {
        Level4Layout layout = (Level4Layout) BackroomsLevel.LEVEL_4.layout();
        int floor = layout.floorY();
        int checked = 0;
        int blocked = 0;
        StringBuilder examples = new StringBuilder();
        for (long seed : SEEDS) {
            for (int x = -SPAN / 2; x < SPAN / 2; x++) {
                for (int z = -SPAN / 2; z < SPAN / 2; z++) {
                    int kind = Level4Plan.kind(Level4Plan.column(seed, x, z));
                    if (kind != Level4Plan.DOOR && kind != Level4Plan.DOORWAY || !layout.buildsFromPlan(seed, x, z)) {
                        continue;
                    }
                    checked++;
                    BlockState[] body = new BlockState[2];
                    layout.fillPlanColumn(seed, x, z, (y, state) -> {
                        if (y == floor + 1 || y == floor + 2) {
                            body[y - floor - 1] = state;
                        }
                    });
                    for (BlockState state : body) {
                        if (!passable(state)) {
                            blocked++;
                            if (blocked <= 5) {
                                examples.append("\n  seed ").append(seed).append(' ').append(x).append(' ').append(z).append(": ").append(state);
                            }
                            break;
                        }
                    }
                }
            }
        }
        assertTrue(checked > 100, "too few door columns found to test (" + checked + ")");
        assertEquals(0, blocked, blocked + " of " + checked + " repaired Level 4 door columns would still block the way:" + examples);
    }

    private static boolean passable(BlockState state) {
        if (state == null || state.isAir()) {
            return true;
        }
        if (state.getBlock() instanceof DoorBlock door) {
            return door.type().canOpenByHand();
        }
        return state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()).isEmpty();
    }
}
