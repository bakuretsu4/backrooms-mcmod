package com.rickr.backrooms.world.anomaly.level9;

import com.rickr.backrooms.world.anomaly.Anomalies;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.anomaly.level39.gondola.GondolaChair;
import com.rickr.backrooms.world.anomaly.level39.gondola.GondolaStation;
import com.rickr.backrooms.world.layout.Level9Layout;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server behaviours of Level 9's anomalies (shapes in {@link Level9Sites}, blocks in
 * {@code world/layout/Level9AnomalyPlan}, the fog event in {@link MangledFog}). Sources: Level 9
 * (https://backrooms-wiki.wikidot.com/level-9) and Level 9.1
 * (http://backrooms-mghc.wikidot.com/level-9-1).
 */
public final class Level9Anomalies {
    public static void register() {
        Anomalies.register(Level9Sites.GRASS_WALKWAY, new GrassWalkway());
        Anomalies.register(Level9Sites.FIELD_WALKWAY, new FieldWalkway());
        Anomalies.register(Level9Sites.CRIMSON_FIELD, new Field());
        Anomalies.register(Level9Sites.FOXHOLE, new Foxhole());
        Anomalies.register(Level9Sites.GONDOLA, new Gondola());
        Anomalies.register(Level9Sites.TWIN_HOUSES, new TwinHouses());
        Anomalies.register(Level9Sites.CLIPPED_HOUSES, new ClippedHouses());
    }

    /** Only things happening at ground level count (not someone on a roof). */
    private static boolean onGround(ServerPlayer player) {
        return player.getY() < LevelLayout.FLOOR_Y + 4;
    }

    /** The walkway in the suburb: its clearing is the way into Level 9.1. */
    private static final class GrassWalkway implements AnomalyBehavior {
        @Override
        public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
            if (onGround(player) && Level9Sites.atCrossing(site, player.getX(), player.getZ())) {
                CrimsonField.cross(player, site);
            }
        }
    }

    /** The same walkway in the field: its start is the way home. */
    private static final class FieldWalkway implements AnomalyBehavior {
        @Override
        public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
            if (onGround(player) && Level9Sites.atReturn(site, player.getX(), player.getZ())) {
                CrimsonField.goHome(player, site);
                return;
            }
            CrimsonField.fieldTick(player);
        }
    }

    /** The open field: the Gloom watches how far east you go. */
    private static final class Field implements AnomalyBehavior {
        @Override
        public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
            CrimsonField.fieldTick(player);
        }
    }

    /**
     * A foxhole: open the hatch (it is an ordinary wooden trapdoor) and drop in. Once nobody is
     * near any more it swings shut again, so the next person finds it the way you did.
     */
    private static final class Foxhole implements AnomalyBehavior {
        @Override
        public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
            double dx = player.getX() - (site.anchorX() + 0.5);
            double dz = player.getZ() - (site.anchorZ() + 0.5);
            if (player.getY() < LevelLayout.FLOOR_Y + 0.7 && Math.abs(dx) < 1.2 && Math.abs(dz) < 1.2) {
                CrimsonField.dropThroughFoxhole(player, site);
                return;
            }
            CrimsonField.fieldTick(player);
        }

        @Override
        public void siteTick(ServerLevel level, AnomalySite site, long seed) {
            BlockPos hatch = new BlockPos(site.anchorX(), LevelLayout.FLOOR_Y + 1, site.anchorZ());
            if (!level.isLoaded(hatch)) {
                return;
            }
            BlockState state = level.getBlockState(hatch);
            if (!(state.getBlock() instanceof TrapDoorBlock) || !state.getValue(TrapDoorBlock.OPEN)) {
                return;
            }
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(hatch.getX() + 0.5, player.getY(), hatch.getZ() + 0.5) < 6.0 * 6.0) {
                    return; // someone is still at it
                }
            }
            level.setBlock(hatch, state.setValue(TrapDoorBlock.OPEN, false), Block.UPDATE_ALL);
        }
    }

    /**
     * A chairlift line, the side entrance to Level 39 (world/anomaly/level39/gondola): the field
     * goes on as usual around it, except that the Gloom can't reach you once you're up on the
     * cable; the station puts out a new chair after each rider ({@link GondolaStation}).
     */
    private static final class Gondola implements AnomalyBehavior {
        @Override
        public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
            if (!GondolaChair.isRiding(player)) {
                CrimsonField.fieldTick(player);
            }
        }

        @Override
        public void siteTick(ServerLevel level, AnomalySite site, long seed) {
            GondolaStation.siteTick(level, site);
        }
    }

    /**
     * Twin houses: step through a house's back door into its little back hall and you are in the
     * other house's front hall, at the same spot, still walking. The two halls are built exactly
     * alike (door behind you, door ahead, same walls), so nothing on screen changes. The shift
     * happens when you cross a line just inside the back hall, walking away from the street, so
     * you can still walk out of the back hall into the yard the other way, and the front doors
     * always lead out.
     */
    private static final class TwinHouses implements AnomalyBehavior {
        private final Map<UUID, Double> lastV = new HashMap<>();

        @Override
        public void enter(ServerPlayer player, AnomalySite site, long seed) {
            lastV.put(player.getUUID(), Level9Sites.lotV(site, player.getZ()));
        }

        @Override
        public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
            double v = Level9Sites.lotV(site, player.getZ());
            Double before = lastV.put(player.getUUID(), v);
            int house = Level9Sites.twinHouseAt(site, player.getX());
            if (before == null || house < 0 || !onGround(player)
                    || !Level9Sites.inTwinHallColumns(Level9Sites.twinU(site, player.getX()))) {
                return;
            }
            double line = Level9Sites.TWIN_BACK_HALL_V + Level9Sites.TWIN_SHIFT_LINE;
            if (before < line && v >= line && v < Level9Sites.TWIN_BACK_HALL_V + 2) {
                Direction inward = Level9Sites.inward(site);
                double dv = Level9Sites.TWIN_FRONT_HALL_V - Level9Sites.TWIN_BACK_HALL_V;
                double dx = house == 0 ? Level9Layout.LW : -Level9Layout.LW;
                Teleports.shift(player, dx, 0.0, dv * inward.getStepZ());
                lastV.put(player.getUUID(), v + dv);
            }
        }

        @Override
        public void leave(ServerPlayer player, AnomalySite site, long seed) {
            lastV.remove(player.getUUID());
        }
    }

    /**
     * Clipped houses: the walls of one house standing inside the other's rooms aren't quite
     * there. Lean into one for a moment and you slip through it into the room behind (only if
     * that room has space for you).
     */
    private static final class ClippedHouses implements AnomalyBehavior {
        /** Pushing against a foreign wall this long (ticks) lets you through. */
        private static final int LEAN_TICKS = 14;
        /** Touching distance: from your middle to the wall's face (you are 0.3 wide each way). */
        private static final double TOUCH = 0.42;

        private final Map<UUID, Integer> leaning = new HashMap<>();

        @Override
        public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
            Direction facing = player.getDirection();
            int bx = player.getBlockX() + facing.getStepX();
            int bz = player.getBlockZ() + facing.getStepZ();
            double gap = facing.getAxis() == Direction.Axis.X
                    ? (facing.getStepX() > 0 ? bx - player.getX() : player.getX() - (bx + 1))
                    : (facing.getStepZ() > 0 ? bz - player.getZ() : player.getZ() - (bz + 1));
            int tx = bx + facing.getStepX();
            int tz = bz + facing.getStepZ();
            ServerLevel level = player.serverLevel();
            boolean pressing = onGround(player) && gap >= 0.0 && gap <= TOUCH && site.contains(tx, tz)
                    && Level9Sites.clipForeignWall(Level9Sites.clipU(site, bx), Level9Sites.clipV(site, bz))
                    && Level9Sites.clipRoom(Level9Sites.clipU(site, tx), Level9Sites.clipV(site, tz))
                    && free(level, tx, tz) && !free(level, bx, bz);
            if (!pressing) {
                leaning.remove(player.getUUID());
                return;
            }
            int held = leaning.merge(player.getUUID(), 1, Integer::sum);
            if (held < LEAN_TICKS) {
                return;
            }
            leaning.remove(player.getUUID());
            // Out the other side, as far from the wall as you were from it on this side.
            double step = 1.0 + 2.0 * Math.max(gap, 0.32);
            Teleports.shift(player, facing.getStepX() * step, 0.0, facing.getStepZ() * step);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHORUS_FRUIT_TELEPORT,
                    SoundSource.AMBIENT, 0.25f, 0.5f);
        }

        @Override
        public void leave(ServerPlayer player, AnomalySite site, long seed) {
            leaning.remove(player.getUUID());
        }

        /** Room for a player in column (x, z) on the ground floor: feet and head height free. */
        private static boolean free(ServerLevel level, int x, int z) {
            BlockPos feet = new BlockPos(x, LevelLayout.FLOOR_Y + 2, z);
            return level.isLoaded(feet)
                    && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
        }
    }

    private Level9Anomalies() {}
}
