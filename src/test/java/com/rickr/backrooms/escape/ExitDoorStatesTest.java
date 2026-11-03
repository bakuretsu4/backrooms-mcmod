package com.rickr.backrooms.escape;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rickr.backrooms.registry.Level39Blocks;
import com.rickr.backrooms.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import org.junit.jupiter.api.Test;

/**
 * Doors on the way out may be shut at any moment (Level 4's looping stairwell shuts its fire door
 * when nobody is near, players close doors behind them), so a closed exit door must never be a
 * locked one: every door and hatch the exit routes go through has to open by hand, and the
 * escapability search must see a closed one as a way through (it does: ReachModelTest).
 */
class ExitDoorStatesTest {
    @Test
    void exitRouteDoorsOpenByHand() {
        assertOpensByHand(ModBlocks.LOBBY_DOOR.get(), "Level 0's stairwell door");
        assertOpensByHand(ModBlocks.FIRE_DOOR.get(), "Level 4's stairwell fire door");
        assertOpensByHand(ModBlocks.L94_HOUSE_DOOR.get(), "Level 94's dark-door house door");
        assertOpensByHand(Level39Blocks.HATCH.get(), "Level 39's shaft hatch (tunnels -> town)");
        assertOpensByHand(ModBlocks.L7_HATCH.get(), "Level 7's room hatch");
        assertOpensByHand(ModBlocks.L94_CLAY_DOOR.get(), "the clay door (Level 9 -> 94, and back from 94 and 39)");
    }

    private static void assertOpensByHand(Block block, String what) {
        boolean opens;
        if (block instanceof DoorBlock door) {
            opens = door.type().canOpenByHand();
        } else if (block instanceof TrapDoorBlock trapdoor) {
            opens = VoxelWorld.trapdoorOpensByHand(trapdoor);
        } else {
            opens = false;
        }
        assertTrue(opens, what + " (" + block + ") can't be opened by hand: a closed one would trap players");
    }
}
