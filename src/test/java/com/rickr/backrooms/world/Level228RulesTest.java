package com.rickr.backrooms.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rickr.backrooms.world.Level228Rules.Member;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Level 228's server rules that don't need a running server: the group exit (who still holds the
 * others back) and when a body counts as pushing against a pane (the exit mirrors, secret panels).
 */
class Level228RulesTest {
    private static Member ready() {
        return new Member(true, false, true);
    }

    private static Member walking() {
        return new Member(true, false, false);
    }

    @Test
    void theGroupLeavesWhenEveryActivePlayerIsReady() {
        assertEquals(-1, Level228Rules.stillMissing(List.of()), "an empty level: nobody waiting");
        assertEquals(-1, Level228Rules.stillMissing(List.of(walking(), walking())), "nobody ready yet");
        assertEquals(0, Level228Rules.stillMissing(List.of(ready())), "a solo player goes at once");
        assertEquals(1, Level228Rules.stillMissing(List.of(ready(), walking())), "waiting for one other");
        assertEquals(2, Level228Rules.stillMissing(List.of(ready(), walking(), ready(), walking())), "waiting for two others");
        assertEquals(0, Level228Rules.stillMissing(List.of(ready(), ready(), ready())), "all ready: go together");
        // Dead or spectating players never hold the group back.
        assertEquals(0, Level228Rules.stillMissing(List.of(ready(), new Member(false, false, false))), "a dead player");
        assertEquals(0, Level228Rules.stillMissing(List.of(ready(), new Member(true, true, false))), "a spectator");
        assertEquals(-1, Level228Rules.stillMissing(List.of(new Member(true, true, true))), "a ready spectator is nobody");
    }

    /**
     * The in-game sequence that once went wrong: A waits in the vestibule; B comes out of the
     * correct mirror at the far end of the hallway and walks down it: nobody goes, however long.
     * B steps into the vestibule: after the hold, both go in the same tick. A teammate brushing
     * the vestibule's edge for less than the hold sends nobody. B logging out lets A go.
     */
    @Test
    void theGroupGateWaitsForTheWholeGroup() {
        Level228Rules.GroupGate gate = new Level228Rules.GroupGate();
        // A ready, B in the maze, then in the hallway (still not ready): never.
        for (int t = 0; t < 200; t++) {
            assertFalse(gate.step(2, 1), "went while B was still walking (tick " + t + ")");
            assertEquals(1, gate.missing);
        }
        // B steps into the vestibule: the hold, then both go on the same tick.
        int went = -1;
        for (int t = 0; t < 20 && went < 0; t++) {
            if (gate.step(2, 2)) {
                went = t;
            }
        }
        assertEquals(Level228Rules.GroupGate.READY_HOLD - 1, went, "the group should go after the hold");
        // B in and out of the doorway for a moment: the hold starts over.
        Level228Rules.GroupGate edge = new Level228Rules.GroupGate();
        for (int t = 0; t < 30; t++) {
            boolean in = t % (Level228Rules.GroupGate.READY_HOLD - 1) != 0;
            assertFalse(edge.step(2, in ? 2 : 1), "a moment in the doorway sent the group (tick " + t + ")");
        }
        // A waits, B disconnects (drops out of the count): A goes after the hold.
        Level228Rules.GroupGate alone = new Level228Rules.GroupGate();
        for (int t = 0; t < 50; t++) {
            assertFalse(alone.step(2, 1));
        }
        boolean left = false;
        for (int t = 0; t < Level228Rules.GroupGate.READY_HOLD; t++) {
            left |= alone.step(1, 1);
        }
        assertTrue(left, "B logged out and A still waits");
    }

    @Test
    void pushingAPaneNeedsContactOverlapAndFacing() {
        // A pane in the plane x = 15.5 spanning z 41..43; a body at x < 15.5 facing +x.
        double plane = 15.5;
        AABB against = new AABB(15.5 - 0.0625 - 0.6, 41, 41.7, 15.5 - 0.0625, 42.8, 42.3);
        Vec3 east = new Vec3(1, 0, 0);
        assertTrue(Level228Rules.pushes(against, east, 0, plane, 41, 43), "body against the glass, facing it");
        assertFalse(Level228Rules.pushes(against, new Vec3(0, 0, 1), 0, plane, 41, 43), "facing along the wall");
        assertFalse(Level228Rules.pushes(against, new Vec3(-1, 0, 0), 0, plane, 41, 43), "back to the glass");
        AABB away = against.move(-0.5, 0, 0);
        assertFalse(Level228Rules.pushes(away, east, 0, plane, 41, 43), "half a block away");
        AABB beside = against.move(0, 0, 1.6);
        assertFalse(Level228Rules.pushes(beside, east, 0, plane, 41, 43), "in front of the pier, not the glass");
        // The other side, and a pane along x (plane z).
        AABB west = new AABB(15.5 + 0.0625, 41, 41.7, 15.5 + 0.0625 + 0.6, 42.8, 42.3);
        assertTrue(Level228Rules.pushes(west, new Vec3(-1, 0, 0), 0, plane, 41, 43), "from the other side");
        AABB south = new AABB(11.2, 41, 19.5 - 0.0625 - 0.6, 11.8, 42.8, 19.5 - 0.0625);
        assertTrue(Level228Rules.pushes(south, new Vec3(0, 0, 1), 1, 19.5, 11, 13), "a pane in the plane z");
    }
}
