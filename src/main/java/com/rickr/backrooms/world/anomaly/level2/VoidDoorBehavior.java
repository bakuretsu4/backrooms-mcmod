package com.rickr.backrooms.world.anomaly.level2;

import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.anomaly.level2.Level2Sites.Frame;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code level_2/void_door}: the rare black-window door that is not locked
 * (https://backrooms-wiki.wikidot.com/level-2, "void rooms": an "infinitely wide, infinitely long
 * space" with no sound and no light; the doorway is the only light source and the only way back).
 *
 * <ul>
 *   <li>Behind the door is a short black passage. Two steps in, you are quietly moved 30 blocks
 *       down onto an exact copy of it, whose far end opens onto the void: a black floor and black
 *       sky with nothing in between.</li>
 *   <li>The void "never ends": walk 20 blocks from the door in any direction and you are moved
 *       back 40 blocks, keeping where you look and how you move. Leaving across the far edge also
 *       shifts you half a lap sideways, so walking straight on doesn't lead you back into the
 *       doorway's box from behind too soon; walking back the same way undoes both moves exactly.</li>
 *   <li>The only light is the copy of the tunnel lamp shining out of the doorway behind you; the
 *       wrap keeps you within ~28 blocks of it, so it always shines and stays in view from
 *       anywhere in front of the door. Walking back through it puts you back in the real tunnel,
 *       just as seamlessly.</li>
 * </ul>
 * Both doors are kept open or shut together ({@link DoorTwins}).
 */
final class VoidDoorBehavior implements AnomalyBehavior {
    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        Frame f = Frame.of(site);
        int d = f.depth(player.getBlockX(), player.getBlockZ());
        int l = f.lat(player.getBlockX(), player.getBlockZ());
        double y = player.getY();
        int floor = Level2Sites.FLOOR_Y;
        boolean nearDoor = Math.abs(d) <= 4 && Math.abs(l) <= Level2Sites.STUB_HALF + 1;
        if (nearDoor && player.tickCount % 2 == 0) {
            DoorTwins.sync(player.serverLevel(), realDoor(site), copyDoor(site));
        }
        if (y >= floor + 0.5 && y < floor + 5) {
            // At the level: deep enough in the passage that the door is behind you -> into the void.
            if (d >= 2 && d <= 7 && Math.abs(l) <= 2) {
                Teleports.shift(player, 0, -Level2Sites.VOID_DROP, 0);
            }
            return;
        }
        if (y <= floor - Level2Sites.VOID_DROP || y >= floor - 1) {
            return; // not in the void's air (can't really happen)
        }
        // In the void. Back through the copy of the door (or anywhere in the tunnel copy) -> home.
        if (d <= 0 && d >= -Level2Sites.STUB_HALF && Math.abs(l) <= Level2Sites.STUB_HALF + 1) {
            Teleports.shift(player, 0, Level2Sites.VOID_DROP, 0);
            return;
        }
        if (!nearDoor && player.tickCount % 10 == 0) {
            keepDoorwayOpen(player.serverLevel(), site);
        }
        wrap(player, f);
    }

    /**
     * Out in the void, the open doorway is your only light and your only way back, so it never
     * stays shut while you are out there: if somebody closed it, it swings open again (and the
     * real door with it, see {@link DoorTwins}).
     */
    private static void keepDoorwayOpen(ServerLevel level, AnomalySite site) {
        BlockPos pos = copyDoor(site);
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock door && !state.getValue(DoorBlock.OPEN)) {
            door.setOpen(null, level, state, pos, true);
        }
    }

    /** Past an edge of the square around the door: move back across it (see the class comment). */
    private static void wrap(ServerPlayer player, Frame f) {
        double depth = f.depth(player.getX(), player.getZ());
        double lat = f.lat(player.getX(), player.getZ());
        double half = Level2Sites.VOID_WRAP / 2.0;
        double moveDepth = 0.0;
        double moveLat = 0.0;
        if (depth >= half) {
            moveDepth = -Level2Sites.VOID_WRAP;
            moveLat = half;
        } else if (depth < -half) {
            moveDepth = Level2Sites.VOID_WRAP;
            moveLat = -half;
        }
        double newLat = lat + moveLat;
        if (newLat >= half) {
            moveLat -= Level2Sites.VOID_WRAP;
        } else if (newLat < -half) {
            moveLat += Level2Sites.VOID_WRAP;
        }
        if (moveDepth != 0.0 || moveLat != 0.0) {
            Teleports.shift(player, f.dx(moveDepth, moveLat), 0.0, f.dz(moveDepth, moveLat));
        }
    }

    @Override
    public void siteTick(ServerLevel level, AnomalySite site, long seed) {
        DoorTwins.sync(level, realDoor(site), copyDoor(site));
    }

    /** Lower half of the door in the tunnel wall. */
    private static BlockPos realDoor(AnomalySite site) {
        return new BlockPos(site.anchorX(), Level2Sites.FLOOR_Y + 1, site.anchorZ());
    }

    /** Lower half of its copy in the void. */
    private static BlockPos copyDoor(AnomalySite site) {
        return new BlockPos(site.anchorX(), Level2Sites.FLOOR_Y + 1 - Level2Sites.VOID_DROP, site.anchorZ());
    }
}
