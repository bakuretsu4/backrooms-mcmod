package com.rickr.backrooms.entity;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.Level227Flora;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.layout.Level227Layout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Plants the giant Venus flytraps of Level 227 at the flytrap spots of the layout (patches of
 * tall grass on the branch tops, Level227Layout.flytrapSpots), near players, and clears away the
 * ones nobody is near any more.
 *
 * <p>A trap is a fixed thing of the world, so each spot always has the same trap (same place, same
 * turn); the entity only exists while someone is close. New ones appear only out of reach
 * ({@link #MIN_DISTANCE} or more away), so a trap never springs up under your feet.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level227FloraSpawner {
    /** Spots within this distance of a player get their trap. */
    private static final double SPAWN_RADIUS = 72.0;
    /** ...but only if no player is this close (so it never appears in plain sight). */
    private static final double MIN_DISTANCE = 24.0;
    /** Traps with no player within this distance are removed. */
    private static final double KEEP_RADIUS = 112.0;
    /** At most this many traps round one player. */
    private static final int MAX_PER_PLAYER = 10;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 40 != 7
                || BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_227) {
            return;
        }
        List<ServerPlayer> players = level.players();
        removeForgotten(level, players);
        for (ServerPlayer player : players) {
            if (!player.isSpectator()) {
                plantNear(level, player, players);
            }
        }
    }

    private static void plantNear(ServerLevel level, ServerPlayer player, List<ServerPlayer> players) {
        AABB around = player.getBoundingBox().inflate(SPAWN_RADIUS + 4, 96, SPAWN_RADIUS + 4);
        List<VenusFlytrap> existing = level.getEntitiesOfClass(VenusFlytrap.class, around);
        if (existing.size() >= MAX_PER_PLAYER) {
            return;
        }
        List<Level227Layout.Spot> wanted = new ArrayList<>();
        Level227Layout.flytrapSpots(player.getX(), player.getZ(), SPAWN_RADIUS, wanted::add);
        int room = MAX_PER_PLAYER - existing.size();
        for (Level227Layout.Spot spot : wanted) {
            if (room <= 0) {
                return;
            }
            double x = spot.x() + 0.5;
            double z = spot.z() + 0.5;
            if (hasTrap(existing, x, z) || nearAnyPlayer(players, x, spot.y(), z, MIN_DISTANCE)) {
                continue;
            }
            VenusFlytrap trap = Level227Flora.VENUS_FLYTRAP.get().create(level);
            if (trap == null) {
                return;
            }
            trap.moveTo(x, spot.y(), z, spot.yaw(), 0.0f);
            trap.setYBodyRot(spot.yaw());
            trap.setYHeadRot(spot.yaw());
            trap.finalizeSpawn(level, level.getCurrentDifficultyAt(trap.blockPosition()), MobSpawnType.EVENT, null);
            level.addFreshEntity(trap);
            existing.add(trap);
            room--;
        }
    }

    private static boolean hasTrap(List<VenusFlytrap> traps, double x, double z) {
        for (VenusFlytrap trap : traps) {
            double dx = trap.getX() - x;
            double dz = trap.getZ() - z;
            if (dx * dx + dz * dz < 4.0) {
                return true;
            }
        }
        return false;
    }

    private static boolean nearAnyPlayer(List<ServerPlayer> players, double x, double y, double z, double distance) {
        for (ServerPlayer player : players) {
            if (player.distanceToSqr(x, y, z) < distance * distance) {
                return true;
            }
        }
        return false;
    }

    /** Removes traps nobody is near (they are planted again when someone comes back). Never one holding someone. */
    private static void removeForgotten(ServerLevel level, List<ServerPlayer> players) {
        if (level.getGameTime() % 200 != 7) {
            return;
        }
        List<VenusFlytrap> traps = new ArrayList<>();
        level.getEntities(Level227Flora.VENUS_FLYTRAP.get(), trap -> true).forEach(traps::add);
        for (VenusFlytrap trap : traps) {
            if (!trap.isVehicle() && !nearAnyPlayer(players, trap.getX(), trap.getY(), trap.getZ(), KEEP_RADIUS)) {
                trap.discard();
            }
        }
    }

    private Level227FloraSpawner() {}
}
