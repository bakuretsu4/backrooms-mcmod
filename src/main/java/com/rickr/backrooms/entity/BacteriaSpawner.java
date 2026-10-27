package com.rickr.backrooms.entity;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModEntities;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.LevelEvents;
import com.rickr.backrooms.world.Travel;
import com.rickr.backrooms.player.SanityService;
import com.rickr.backrooms.world.SafeRooms;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.jetbrains.annotations.Nullable;

/**
 * There is only ever ONE Bacteria, and only in Level 0 (any floor with a {@code maxBacteriaPerPlayer}
 * above 0). We place it ourselves instead of using vanilla spawning: always 28-48 blocks from a
 * player, out of everyone's sight (never popping into view).
 *
 * <ul>
 *   <li><b>Arriving:</b> while there is none, a roll each second brings it (about once in
 *       {@code bacteriaSpawnChance} seconds; three times as often in a blackout, up to three times
 *       as often when someone's sanity is low).</li>
 *   <li><b>Following you around:</b> the level is endless, so one Bacteria left wandering would
 *       soon be far behind everyone. When it is only wandering, nobody can see it, nobody is
 *       within {@value #RELOCATE_DISTANCE} blocks and it hasn't hunted for {@value #RELOCATE_AFTER_S} s,
 *       it quietly moves to a hidden spot near a player (a different one each time in multiplayer).
 *       If you leave it behind (its area unloads) it catches up a while later, sooner than it first came.</li>
 *   <li><b>Only one:</b> any other Bacteria that comes back from the save is removed ({@link #onJoin});
 *       one from a spawn egg or a command is left alone.</li>
 *   <li>After it gives up a chase, the players near it get a quiet minute ({@link #quietAround}):
 *       it won't move next to them right away.</li>
 *   <li>Safe rooms: never placed in one or within {@value #SAFE_ROOM_MARGIN} blocks of one, and
 *       players inside one don't draw it.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class BacteriaSpawner {
    private static final int MIN_DISTANCE = 28;
    private static final int MAX_DISTANCE = 48;
    /** Players further than this from a spawn spot can't see it pop in anyway. */
    private static final double VIEW_CHECK_RADIUS = 64.0;
    /** Quiet time after an escape. */
    private static final int QUIET_TICKS = 60 * 20;
    private static final double QUIET_RADIUS = 48.0;
    /** No spawns within this many blocks (sideways) of a safe room. */
    private static final int SAFE_ROOM_MARGIN = 12;
    /** It moves near a player once everyone is further than this from it... */
    private static final int RELOCATE_DISTANCE = 64;
    /** ...and it has been wandering (not hunting) for this long. */
    private static final int RELOCATE_AFTER_S = 45;
    /** Its entity missing (chunk unloaded, killed) for this long: it is gone, a new one may come. */
    private static final int MISSING_S = 20;
    /** After being left behind it finds you this many times sooner than a first arrival. */
    private static final int COME_BACK_FASTER = 4;

    /** Player -> game time until which it won't come for them. Cleared when the server stops. */
    private static final Map<UUID, Long> QUIET_UNTIL = new HashMap<>();

    /** The one Bacteria of each level. */
    private static final class One {
        @Nullable
        UUID id;
        /** Last game tick it was hunting (or arrived). */
        long lastActive;
        /** Game tick its entity was first found missing, or -1. */
        long missingSince = -1;
        /** Which player it went to last, so in multiplayer it takes turns. */
        int nextTarget;
        /** It was left behind (its area unloaded): it comes back soon, not after the usual wait. */
        boolean comeBack;
    }

    private static final Map<ResourceKey<Level>, One> ONES = new HashMap<>();

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        BackroomsLevel floor = BackroomsLevel.fromDimension(level.dimension());
        if (floor == null || level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL || floor.maxBacteriaPerPlayer() <= 0) {
            return;
        }
        long now = level.getGameTime();
        QUIET_UNTIL.values().removeIf(until -> until <= now);
        One one = ONES.computeIfAbsent(level.dimension(), k -> new One());
        RandomSource random = level.getRandom();
        long seed = BackroomsSeeds.layoutSeed(level, floor);
        List<ServerPlayer> targets = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (canHaveSpawns(player) && !QUIET_UNTIL.containsKey(player.getUUID())) {
                targets.add(player);
            }
        }

        Bacteria current = one.id == null ? null : level.getEntity(one.id) instanceof Bacteria b && b.isAlive() ? b : null;
        if (current == null && one.id != null) {
            if (one.missingSince < 0) {
                one.missingSince = now;
            }
            if (now - one.missingSince < MISSING_S * 20L) {
                return; // probably just its chunk reloading: give it a moment
            }
            one.id = null; // left behind: it comes back soon
            one.comeBack = true;
        }
        if (current != null) {
            one.missingSince = -1;
            if (current.mode() != Bacteria.Mode.WANDER) {
                one.lastActive = now;
                return;
            }
            if (targets.isEmpty() || now - one.lastActive < RELOCATE_AFTER_S * 20L) {
                return;
            }
            boolean anyoneNear = level.players().stream().anyMatch(p -> !p.isSpectator()
                    && p.distanceToSqr(current) < (double) RELOCATE_DISTANCE * RELOCATE_DISTANCE);
            if (anyoneNear || anyoneCanSee(level, current.blockPosition())) {
                return;
            }
            BlockPos spot = hiddenSpotNear(level, floor, seed, nextTarget(one, targets), random);
            if (spot != null) {
                current.getNavigation().stop();
                current.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                one.lastActive = now;
            }
            return;
        }
        // None yet: roll for it to arrive.
        if (targets.isEmpty()) {
            return;
        }
        int chance = LevelEvents.isBlackout(floor) ? Math.max(1, floor.bacteriaSpawnChance() / 3) : floor.bacteriaSpawnChance();
        if (one.comeBack) {
            chance = Math.max(1, chance / COME_BACK_FASTER);
        }
        float pressure = 1.0f;
        for (ServerPlayer player : targets) {
            pressure = Math.max(pressure, SanityService.spawnPressure(player));
        }
        if (random.nextInt(Math.max(1, Math.round(chance / pressure))) != 0) {
            return;
        }
        BlockPos spot = hiddenSpotNear(level, floor, seed, nextTarget(one, targets), random);
        if (spot == null) {
            return;
        }
        Entity spawned = ModEntities.BACTERIA.get().spawn(level, spot, MobSpawnType.EVENT);
        if (spawned != null) {
            one.comeBack = false;
            one.id = spawned.getUUID();
            one.lastActive = now;
            one.missingSince = -1;
        }
    }

    /** Takes turns between the players it may come for. */
    private static ServerPlayer nextTarget(One one, List<ServerPlayer> targets) {
        ServerPlayer target = targets.get(Math.floorMod(one.nextTarget, targets.size()));
        one.nextTarget++;
        return target;
    }

    /** A spot 28-48 blocks from {@code player} that nobody can see, or null if this try found none. */
    @Nullable
    private static BlockPos hiddenSpotNear(ServerLevel level, BackroomsLevel floor, long seed, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE);
            int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos spot = Travel.searchWalkable(floor.layout(), seed, x, z, 6);
            if (spot == null || !level.isLoaded(spot) || floor.layout().isQuietArea(seed, spot.getX(), spot.getZ())) {
                continue;
            }
            boolean tooClose = level.players().stream().anyMatch(p -> p.distanceToSqr(spot.getX(), spot.getY(), spot.getZ()) < MIN_DISTANCE * MIN_DISTANCE);
            // Never in or right next to a safe room, so nothing comes out of its doorway.
            if (!tooClose && !SafeRooms.isNearSafeRoom(level, spot, SAFE_ROOM_MARGIN) && !anyoneCanSee(level, spot)) {
                return spot;
            }
        }
        return null;
    }

    /**
     * Only one. A Bacteria coming back from the save (its chunk reloading, the world reopening):
     * on a floor without Bacteria it is removed; on Level 0 it becomes the one if there is none
     * yet, otherwise it is removed. Fresh spawns (a spawn egg, a command) are left alone: someone
     * is testing.
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Bacteria bacteria) || !(event.getLevel() instanceof ServerLevel level)
                || !event.loadedFromDisk()) {
            return;
        }
        BackroomsLevel floor = BackroomsLevel.fromDimension(level.dimension());
        if (floor == null) {
            return;
        }
        if (floor.maxBacteriaPerPlayer() <= 0) {
            event.setCanceled(true);
            return;
        }
        One one = ONES.computeIfAbsent(level.dimension(), k -> new One());
        if (one.id == null) {
            one.id = bacteria.getUUID();
            one.lastActive = level.getGameTime();
            one.missingSince = -1;
        } else if (!one.id.equals(bacteria.getUUID())) {
            event.setCanceled(true);
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

    /** Server stopping: forget the quiet timers and which Bacteria was the one. */
    public static void reset() {
        QUIET_UNTIL.clear();
        ONES.clear();
    }

    /** Players in a safe room, or hiding in furniture, don't count: it never comes for them. */
    private static boolean canHaveSpawns(ServerPlayer player) {
        return !player.isSpectator() && !player.isCreative() && !SafeRooms.isSafe(player)
                && !com.rickr.backrooms.player.HidingService.isHidden(player);
    }

    /**
     * True if any nearby player has a clear line to the spot (its legs or its head): distance
     * alone is not enough to keep it out of view down a long straight hallway. VISUAL shapes let chain-link fences count as see-through.
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
