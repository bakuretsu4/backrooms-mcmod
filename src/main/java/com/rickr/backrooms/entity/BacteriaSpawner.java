package com.rickr.backrooms.entity;

import com.rickr.backrooms.registry.ModEntities;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.LevelEvents;
import com.rickr.backrooms.world.Travel;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Spawns Bacteria near players instead of using vanilla mob spawning, because the Backrooms are
 * bright everywhere and we want precise control: a cap per player, a spawn chance per level, and
 * spawns always 28-48 blocks away, out of everyone's sight (never popping into view).
 *
 * <p>Fairness rules:
 * <ul>
 *   <li>A group gets the same spawn rate as one player: each player's roll is divided by the
 *       number of players around them, so four friends together don't get four times the monsters.</li>
 *   <li>After a Bacteria gives up a chase, the players near it get a quiet minute
 *       ({@link #quietAround}): no new spawn right on the heels of an escape.</li>
 * </ul>
 */
public final class BacteriaSpawner {
    private static final int MIN_DISTANCE = 28;
    private static final int MAX_DISTANCE = 48;
    private static final double COUNT_RADIUS = 80.0;
    /** Players this close together count as one group for the spawn roll. */
    private static final double GROUP_RADIUS = 32.0;
    /** Players further than this from a spawn spot can't see it pop in anyway. */
    private static final double VIEW_CHECK_RADIUS = 64.0;
    /** Quiet time after an escape. */
    private static final int QUIET_TICKS = 60 * 20;
    private static final double QUIET_RADIUS = 48.0;

    /** Player -> game time until which nothing spawns for them. Cleared when the server stops. */
    private static final Map<UUID, Long> QUIET_UNTIL = new HashMap<>();

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        BackroomsLevel floor = BackroomsLevel.fromDimension(level.dimension());
        if (floor == null || level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            return;
        }
        if (floor.maxBacteriaPerPlayer() <= 0) {
            return; // a floor without monsters (the Poolrooms)
        }
        long now = level.getGameTime();
        QUIET_UNTIL.values().removeIf(until -> until <= now);
        RandomSource random = level.getRandom();
        long seed = BackroomsSeeds.layoutSeed(level, floor);
        for (ServerPlayer player : level.players()) {
            if (!canHaveSpawns(player) || QUIET_UNTIL.containsKey(player.getUUID())) {
                continue;
            }
            // In a blackout the Bacteria come out three times as often.
            int chance = LevelEvents.isBlackout(floor) ? Math.max(1, floor.bacteriaSpawnChance() / 3) : floor.bacteriaSpawnChance();
            if (random.nextInt(chance * groupSize(level, player)) != 0) {
                continue;
            }
            AABB area = player.getBoundingBox().inflate(COUNT_RADIUS);
            if (level.getEntitiesOfClass(Bacteria.class, area).size() >= floor.maxBacteriaPerPlayer()) {
                continue;
            }
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE);
            int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos spot = Travel.searchWalkable(floor.layout(), seed, x, z, 6);
            if (spot == null || !level.isLoaded(spot)) {
                continue;
            }
            boolean tooClose = level.players().stream().anyMatch(p -> p.distanceToSqr(spot.getX(), spot.getY(), spot.getZ()) < MIN_DISTANCE * MIN_DISTANCE);
            if (!tooClose && !anyoneCanSee(level, spot)) {
                ModEntities.BACTERIA.get().spawn(level, spot, MobSpawnType.EVENT);
            }
        }
    }

    /** Called by a Bacteria that just gave up a chase at {@code pos}. */
    public static void quietAround(ServerLevel level, Vec3 pos) {
        long until = level.getGameTime() + QUIET_TICKS;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) < QUIET_RADIUS * QUIET_RADIUS) {
                QUIET_UNTIL.put(player.getUUID(), until);
            }
        }
    }

    /** Server stopping: forget the quiet timers. */
    public static void reset() {
        QUIET_UNTIL.clear();
    }

    private static boolean canHaveSpawns(ServerPlayer player) {
        return !player.isSpectator() && !player.isCreative();
    }

    /** This player plus everyone near them who could also roll a spawn. Always at least 1. */
    private static int groupSize(ServerLevel level, ServerPlayer player) {
        int count = 0;
        for (ServerPlayer other : level.players()) {
            if (canHaveSpawns(other) && other.distanceToSqr(player) < GROUP_RADIUS * GROUP_RADIUS) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    /**
     * True if any nearby player has a clear line to the spawn spot (its legs or its head). The
     * Level 1 parking halls are wider than the minimum spawn distance, so distance alone is not
     * enough to keep spawns out of view. VISUAL shapes let chain-link fences count as see-through.
     */
    private static boolean anyoneCanSee(ServerLevel level, BlockPos spot) {
        Vec3 low = Vec3.atBottomCenterOf(spot).add(0.0, 1.0, 0.0);
        Vec3 high = Vec3.atBottomCenterOf(spot).add(0.0, 2.5, 0.0);
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.distanceToSqr(low) > VIEW_CHECK_RADIUS * VIEW_CHECK_RADIUS) {
                continue;
            }
            Vec3 eye = player.getEyePosition();
            if (clearLine(level, eye, low, player) || clearLine(level, eye, high, player)) {
                return true;
            }
        }
        return false;
    }

    private static boolean clearLine(ServerLevel level, Vec3 from, Vec3 to, ServerPlayer viewer) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, viewer)).getType() == HitResult.Type.MISS;
    }

    private BacteriaSpawner() {}
}
