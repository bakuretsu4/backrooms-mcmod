package com.rickr.backrooms.entity;

import com.rickr.backrooms.registry.ModEntities;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.LevelEvents;
import com.rickr.backrooms.world.SafeRooms;
import com.rickr.backrooms.world.Level94Clock;
import com.rickr.backrooms.world.Travel;
import com.rickr.backrooms.world.layout.Level94Castle;
import net.minecraft.world.entity.Entity;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Brings Smilers out when the lights go out, on every floor that has blackouts (Levels 0-2).
 *
 * <p>Per group of players (everyone within {@value #GROUP_RADIUS} blocks counts as one group),
 * for the whole blackout:
 * <ul>
 *   <li>one Smiler a couple of seconds after the power cuts;</li>
 *   <li>in half of the blackouts, a second one 10 s in, from the other side of the group, so one
 *       person can't cover both with a single flashlight;</li>
 *   <li>that's all: a Smiler you burn away is not replaced until the next blackout. That is what
 *       makes the flashlight worth it;</li>
 *   <li>never in or near a safe room, never closer than {@value #MIN_DISTANCE} blocks to anyone.</li>
 * </ul>
 * When the lights come back on every blackout Smiler fades by itself ({@link Smiler}).
 *
 * <p><b>Level 94 at night</b> ({@link #tickLevel94Night}): there are no blackouts there; instead,
 * while it is night, Smilers come for anyone OUTSIDE (not in a house, not in the castle): up to
 * two around each player, a new one every 20-40 s, appearing 24-40 blocks away across the hills.
 * At dawn they fade away by themselves ({@link Smiler}).
 */
public final class SmilerSpawner {
    private static final Set<BackroomsLevel> FLOORS = EnumSet.of(BackroomsLevel.LEVEL_0, BackroomsLevel.LEVEL_1, BackroomsLevel.LEVEL_2);
    private static final int MIN_DISTANCE = 14;
    private static final int MAX_DISTANCE = 24;
    private static final double GROUP_RADIUS = 40.0;
    private static final int SAFE_ROOM_MARGIN = 8;
    /** Seconds into the blackout before the first and the (possible) second Smiler. */
    private static final int FIRST_AT = 2;
    private static final int SECOND_AT = 10;
    /** 1 in 6 Smilers is "bold" and needs twice as much light. */
    private static final int BOLD_CHANCE = 6;

    // Level 94 night.
    /** Smilers around each player outside at night (a group of players shares 2 per person). */
    private static final int NIGHT_PER_PLAYER = 2;
    /** A new one every 20-40 s; the first 10-20 s after dark. */
    private static final int NIGHT_EVERY_MIN = 20 * 20;
    private static final int NIGHT_EVERY_MAX = 40 * 20;
    private static final int NIGHT_FIRST_MIN = 10 * 20;
    private static final int NIGHT_FIRST_MAX = 20 * 20;
    /** Out on the open hills they are seen from far away, so they appear further out. */
    private static final int NIGHT_MIN_DISTANCE = 24;
    private static final int NIGHT_MAX_DISTANCE = 40;
    /** Game tick when each player's next night Smiler may come (cleared when the night ends). */
    private static final Map<UUID, Long> NIGHT_NEXT = new HashMap<>();

    private static final class State {
        boolean blackout;
        int seconds;
        boolean secondWave;
        /** How many Smilers each player's group has had this blackout (shared by everyone nearby). */
        final Map<UUID, Integer> sent = new HashMap<>();
    }

    private static final Map<ResourceKey<Level>, State> STATES = new HashMap<>();

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        BackroomsLevel floor = BackroomsLevel.fromDimension(level.dimension());
        if (floor == BackroomsLevel.LEVEL_94 && level.getDifficulty() != Difficulty.PEACEFUL) {
            tickLevel94Night(level, floor, level.getRandom());
            return;
        }
        if (floor == null || !FLOORS.contains(floor) || level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        State state = STATES.computeIfAbsent(level.dimension(), k -> new State());
        RandomSource random = level.getRandom();
        if (!LevelEvents.isBlackout(floor)) {
            state.blackout = false;
            return;
        }
        if (!state.blackout) {
            // The power just cut: decide now whether this blackout brings a second wave.
            state.blackout = true;
            state.seconds = 0;
            state.secondWave = random.nextBoolean();
            state.sent.clear();
        }
        state.seconds++;
        int wanted = state.seconds >= SECOND_AT && state.secondWave ? 2 : state.seconds >= FIRST_AT ? 1 : 0;
        if (wanted == 0) {
            return;
        }
        long seed = BackroomsSeeds.layoutSeed(level, floor);
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative() || SafeRooms.isSafe(player)
                    || state.sent.getOrDefault(player.getUUID(), 0) >= wanted) {
                continue;
            }
            List<Smiler> near = level.getEntitiesOfClass(Smiler.class, player.getBoundingBox().inflate(GROUP_RADIUS));
            if (spawnNear(level, floor, seed, player, near.isEmpty() ? null : near.get(0), random, MIN_DISTANCE, MAX_DISTANCE)) {
                // Everyone in this group shares the budget, so a group of four still gets at most two.
                for (ServerPlayer other : level.players()) {
                    if (other.distanceToSqr(player) <= GROUP_RADIUS * GROUP_RADIUS) {
                        state.sent.merge(other.getUUID(), 1, Integer::sum);
                    }
                }
            }
        }
    }

    /**
     * Level 94's night trigger (once a second): during the night proper (not dusk, not dawn), each
     * player who is outside gets a Smiler every 20-40 s until there are two around them.
     */
    private static void tickLevel94Night(ServerLevel level, BackroomsLevel floor, RandomSource random) {
        if (Level94Clock.phase(level) != Level94Clock.Phase.NIGHT) {
            NIGHT_NEXT.clear(); // day (or dawn): the next night starts with a fresh schedule
            return;
        }
        long now = level.getGameTime();
        long seed = BackroomsSeeds.layoutSeed(level, floor);
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative() || !isOutsideInLevel94(seed, player)) {
                continue;
            }
            Long next = NIGHT_NEXT.get(player.getUUID());
            if (next == null) {
                // Night has just fallen: the first one comes soon.
                NIGHT_NEXT.put(player.getUUID(), now + Mth.nextInt(random, NIGHT_FIRST_MIN, NIGHT_FIRST_MAX));
                continue;
            }
            if (now < next) {
                continue;
            }
            List<Smiler> near = level.getEntitiesOfClass(Smiler.class, player.getBoundingBox().inflate(GROUP_RADIUS));
            long players = level.players().stream()
                    .filter(p -> !p.isSpectator() && p.distanceToSqr(player) <= GROUP_RADIUS * GROUP_RADIUS).count();
            if (near.size() >= NIGHT_PER_PLAYER * players) {
                continue; // enough of them out here already; check again next second
            }
            if (spawnNear(level, floor, seed, player, near.isEmpty() ? null : near.get(0), random,
                    NIGHT_MIN_DISTANCE, NIGHT_MAX_DISTANCE)) {
                NIGHT_NEXT.put(player.getUUID(), now + Mth.nextInt(random, NIGHT_EVERY_MIN, NIGHT_EVERY_MAX));
            }
        }
    }

    /** Outside in Level 94: not in a house (safe room) and not inside the castle. Smilers use it too. */
    static boolean isOutsideInLevel94(long seed, Entity entity) {
        return !SafeRooms.isSafe(entity)
                && !Level94Castle.isInsideCastle(seed, entity.getBlockX(), entity.getBlockY(), entity.getBlockZ());
    }

    /**
     * One Smiler {@code minDistance}-{@code maxDistance} blocks from {@code player}. If another one
     * is already around, the new one comes from roughly the opposite side. False if no good spot
     * was found this time (it tries again next second).
     */
    private static boolean spawnNear(ServerLevel level, BackroomsLevel floor, long seed, ServerPlayer player,
                                     @Nullable Smiler existing, RandomSource random, int minDistance, int maxDistance) {
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle;
            if (existing != null) {
                double toExisting = Math.atan2(existing.getZ() - player.getZ(), existing.getX() - player.getX());
                angle = toExisting + Math.PI + (random.nextDouble() - 0.5) * 1.6;
            } else {
                angle = random.nextDouble() * Mth.TWO_PI;
            }
            int distance = minDistance + random.nextInt(maxDistance - minDistance + 1);
            int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            // Level 94: any open ground will do. (searchWalkable only accepts "safe arrival" spots,
            // which there means a road near houses, so out on the hills it never found anywhere.)
            BlockPos spot = floor == BackroomsLevel.LEVEL_94
                    ? openGround(floor.layout(), seed, x, z, 4)
                    : Travel.searchWalkable(floor.layout(), seed, x, z, 4);
            if (spot == null || !level.isLoaded(spot) || SafeRooms.isNearSafeRoom(level, spot, SAFE_ROOM_MARGIN)) {
                continue;
            }
            // Level 94: never inside the castle (it's the one place they don't go).
            if (floor == BackroomsLevel.LEVEL_94
                    && Level94Castle.isInsideCastle(seed, spot.getX(), spot.getY(), spot.getZ())) {
                continue;
            }
            boolean tooClose = level.players().stream().anyMatch(p -> p.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) < minDistance * minDistance);
            if (tooClose) {
                continue;
            }
            return spawn(level, spot, true, random.nextInt(BOLD_CHANCE) == 0) != null;
        }
        return false;
    }

    /** Spawns one Smiler at {@code spot}. Also used by {@code /backrooms spawn smiler}. */
    @Nullable
    public static Smiler spawn(ServerLevel level, BlockPos spot, boolean fromBlackout, boolean bold) {
        Smiler smiler = ModEntities.SMILER.get().create(level);
        if (smiler == null) {
            return null;
        }
        smiler.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0f, 0.0f);
        smiler.setFromBlackout(fromBlackout);
        smiler.setBold(bold);
        level.addFreshEntity(smiler);
        return smiler;
    }

    /** Server stopping: forget per-floor state. */
    public static void reset() {
        STATES.clear();
        NIGHT_NEXT.clear();
    }

    /** Nearest walkable column within {@code radius} of (x, z), as feet position; null if none. */
    @Nullable
    private static BlockPos openGround(com.rickr.backrooms.world.layout.LevelLayout layout, long seed, int x, int z, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    if (Math.max(Math.abs(ox), Math.abs(oz)) == r && layout.isWalkable(seed, x + ox, z + oz)) {
                        return new BlockPos(x + ox, layout.standY(seed, x + ox, z + oz), z + oz);
                    }
                }
            }
        }
        return null;
    }

    private SmilerSpawner() {}
}
