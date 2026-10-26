package com.rickr.backrooms.world.anomaly.level2;

import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.anomaly.level2.Level2Sites.Frame;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code level_2/loop_hall}: an unlocked office door in a tunnel wall that opens onto a long,
 * plain, office-like hallway lit by fluorescent panels, which "loops back around to the same door
 * one came from" (https://backrooms-wiki.wikidot.com/level-2, "looping hallways").
 *
 * <ul>
 *   <li>Two steps into the short passage behind the door you are quietly lifted 6 blocks onto an
 *       exact copy of it, which opens onto the real hallway built over the tunnel ceilings.</li>
 *   <li>Walk 16 blocks along the hallway either way and you are moved back 32 blocks. The lights
 *       and the closed office doors repeat every 16 blocks and look-alike copies of the door's
 *       passage every 32, so you never notice: you just keep passing the same door.</li>
 *   <li>Stepping back into its passage lowers you into the real tunnel.</li>
 * </ul>
 * The door and its copy are kept open or shut together ({@link DoorTwins}).
 */
final class LoopHallBehavior implements AnomalyBehavior {
    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        Frame f = Frame.of(site);
        int d = f.depth(player.getBlockX(), player.getBlockZ());
        int l = f.lat(player.getBlockX(), player.getBlockZ());
        double y = player.getY();
        int floor = Level2Sites.FLOOR_Y;
        int rise = Level2Sites.LOOP_RISE;
        if (Math.abs(d) <= 4 && Math.abs(l) <= Level2Sites.STUB_HALF + 1 && player.tickCount % 2 == 0) {
            DoorTwins.sync(player.serverLevel(), realDoor(site), copyDoor(site));
        }
        if (y >= floor + 0.5 && y < floor + 5) {
            // At the level: past the door, in the passage (or its stub of hallway) -> up to the copy.
            if ((d >= 2 && d <= 3 && l == 0) || (d >= 4 && d <= 5 && Math.abs(l) <= 3)) {
                Teleports.shift(player, 0, rise, 0);
            }
            return;
        }
        if (y < floor + rise + 0.5 || y >= floor + rise + 5) {
            return;
        }
        // Up by the hallway. Back into the doorway (or the tunnel copy behind it) -> down to the tunnel.
        if (d <= 0 && d >= -Level2Sites.STUB_HALF && Math.abs(l) <= Level2Sites.STUB_HALF + 1) {
            Teleports.shift(player, 0, -rise, 0);
            return;
        }
        if (d >= 4 && d <= 5) {
            double lat = f.lat(player.getX(), player.getZ());
            double half = Level2Sites.LOOP_PERIOD / 2.0;
            if (lat >= half) {
                Teleports.shift(player, f.dx(0, -Level2Sites.LOOP_PERIOD), 0, f.dz(0, -Level2Sites.LOOP_PERIOD));
            } else if (lat < -half) {
                Teleports.shift(player, f.dx(0, Level2Sites.LOOP_PERIOD), 0, f.dz(0, Level2Sites.LOOP_PERIOD));
            }
        }
    }

    @Override
    public void siteTick(ServerLevel level, AnomalySite site, long seed) {
        DoorTwins.sync(level, realDoor(site), copyDoor(site));
    }

    private static BlockPos realDoor(AnomalySite site) {
        return new BlockPos(site.anchorX(), Level2Sites.FLOOR_Y + 1, site.anchorZ());
    }

    private static BlockPos copyDoor(AnomalySite site) {
        return new BlockPos(site.anchorX(), Level2Sites.FLOOR_Y + 1 + Level2Sites.LOOP_RISE, site.anchorZ());
    }
}
