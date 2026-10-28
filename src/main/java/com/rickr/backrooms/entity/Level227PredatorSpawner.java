package com.rickr.backrooms.entity;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.player.HidingService;
import com.rickr.backrooms.registry.Level227Predators;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.SafeRooms;
import com.rickr.backrooms.world.layout.Level227Tree;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who hunts in Level 227 "Megaflora" and where they turn up. Runs once a second, only in
 * {@code backrooms:level_227}, never on Peaceful. Like the other spawners it places monsters near
 * players itself (no vanilla mob spawning), always out of sight, at a fair distance, with caps.
 *
 * <ul>
 *   <li><b>A minute's grace.</b> Nothing hunts you for the first {@value #ARRIVAL_GRACE_TICKS}
 *       ticks after you arrive (the wanderer gets to look up at the tree first).</li>
 *   <li><b>Wolves</b>, only while you are in the higher sections: a pack of 3-5 now and then
 *       (about one every {@value #WOLF_CHANCE} seconds while none is within
 *       {@value #COUNT_RANGE} blocks), 40-64 blocks away and also up high.</li>
 *   <li><b>Stags</b>, anywhere on the branches: one at a time per player, about one every
 *       {@value #STAG_CHANCE} seconds while none is near, 36-60 blocks away.</li>
 *   <li><b>Squirrel swarms</b>: very rare. Only after {@value #SWARM_SETTLE_TICKS} ticks in the
 *       level, about once in {@value #SWARM_CHANCE} seconds, never twice within
 *       {@value #SWARM_COOLDOWN_TICKS} ticks for the same player, and only one swarm in the whole
 *       world at a time ({@link SquirrelSwarm}).</li>
 * </ul>
 * World caps keep a crowded server bounded: {@value #MAX_WOLVES} wolves and {@value #MAX_STAGS}
 * stags in all, and the swarm's own {@value SquirrelSwarm#MAX_LIVE} squirrels.
 *
 * <p>Despawning: none of them is saved. Wolves and stags that aren't hunting are cleared by vanilla
 * once nobody is near (see their {@code removeWhenFarAway}); a swarm sends its squirrels back into
 * the forest when it ends.
 *
 * <p>Test commands (operators): {@code /backrooms spawn l227_wolf [count]} (a pack),
 * {@code /backrooms spawn l227_stag}, {@code /backrooms spawn l227_squirrels [count]} (a swarm on
 * you, anywhere).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level227PredatorSpawner {
    /** One minute after arriving before anything may appear. */
    public static final int ARRIVAL_GRACE_TICKS = 1200;
    public static final int MIN_DISTANCE = 28;
    public static final int COUNT_RANGE = 112;

    // --- Wolves ------------------------------------------------------------------------------
    private static final int WOLF_CHANCE = 150;
    private static final int PACK_MIN = 3;
    private static final int PACK_EXTRA = 3;
    private static final double WOLF_MIN_DISTANCE = 40.0;
    private static final double WOLF_MAX_DISTANCE = 64.0;
    public static final int MAX_WOLVES = 24;

    // --- Stags -------------------------------------------------------------------------------
    private static final int STAG_CHANCE = 200;
    private static final double STAG_MIN_DISTANCE = 36.0;
    private static final double STAG_MAX_DISTANCE = 60.0;
    public static final int MAX_STAGS = 8;

    // --- Squirrel swarms ---------------------------------------------------------------------
    /** Four minutes in the level before the first swarm may come. */
    private static final int SWARM_SETTLE_TICKS = 4800;
    /** One in this many seconds: about one swarm per 40 minutes spent in the level. */
    private static final int SWARM_CHANCE = 2400;
    /** Twenty minutes between swarms for the same player. */
    private static final int SWARM_COOLDOWN_TICKS = 24000;

    /** When each player arrived in Level 227 (game time). Server thread only. */
    private static final Map<UUID, Long> ARRIVED = new HashMap<>();
    /** When the last swarm came for each player (game time). */
    private static final Map<UUID, Long> LAST_SWARM = new HashMap<>();

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // Swarms run every tick, in any world (a spawn-egg squirrel can start one anywhere).
        SquirrelSwarm.tickAll(level);
        if (level.getGameTime() % 20 != 0 || level.getDifficulty() == Difficulty.PEACEFUL
                || BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_227) {
            return;
        }
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            long arrived = ARRIVED.computeIfAbsent(player.getUUID(), id -> now);
            if (player.isSpectator() || SafeRooms.isSafe(player) || HidingService.isHidden(player)
                    || now - arrived < ARRIVAL_GRACE_TICKS) {
                continue; // nothing new shows up for someone resting, hiding, or who just arrived
            }
            RandomSource random = player.getRandom();
            if (Level227Tree.isLevelHigh(player.getY()) && random.nextInt(WOLF_CHANCE) == 0) {
                tickWolves(level, player);
            }
            if (random.nextInt(STAG_CHANCE) == 0) {
                tickStags(level, player);
            }
            if (now - arrived >= SWARM_SETTLE_TICKS && random.nextInt(SWARM_CHANCE) == 0) {
                tickSwarm(level, player, now);
            }
        }
    }

    // ---- arrival times ----------------------------------------------------------------------

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getTo() == BackroomsLevel.LEVEL_227.dimension()) {
            markArrival(event.getEntity().getUUID(), event.getEntity().level().getGameTime());
        } else {
            ARRIVED.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // Logging back in counts as arriving: you get your minute again.
        markArrival(event.getEntity().getUUID(), event.getEntity().level().getGameTime());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        markArrival(event.getEntity().getUUID(), event.getEntity().level().getGameTime());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ARRIVED.remove(event.getEntity().getUUID());
    }

    /** A world closed (singleplayer quits, servers stop): nothing carries over to the next one. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ARRIVED.clear();
        LAST_SWARM.clear();
        SquirrelSwarm.clearAll();
    }

    private static void markArrival(UUID id, long time) {
        ARRIVED.put(id, time);
    }

    // ---- wolves -----------------------------------------------------------------------------

    private static void tickWolves(ServerLevel level, ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(COUNT_RANGE, 64.0, COUNT_RANGE);
        if (!level.getEntitiesOfClass(L227Wolf.class, area).isEmpty()
                || countAll(level, L227Wolf.class) + PACK_MIN > MAX_WOLVES) {
            return;
        }
        BlockPos spot = findSpot(level, player, WOLF_MIN_DISTANCE, WOLF_MAX_DISTANCE, true);
        if (spot != null) {
            spawnPack(level, spot, PACK_MIN + player.getRandom().nextInt(PACK_EXTRA));
        }
    }

    /** A pack of {@code count} wolves standing loosely together around {@code center}. */
    public static int spawnPack(ServerLevel level, BlockPos center, int count) {
        RandomSource random = level.getRandom();
        int pack = L227Wolf.newPackId();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            BlockPos spot = i == 0 ? center : Level39Spawner.standableNear(level,
                    center.getX() + random.nextInt(7) - 3, center.getY() + 1, center.getZ() + random.nextInt(7) - 3, 3);
            L227Wolf wolf = Level227Predators.WOLF.get().spawn(level, spot != null ? spot : center, MobSpawnType.EVENT);
            if (wolf != null) {
                wolf.setPack(pack);
                spawned++;
            }
        }
        return spawned;
    }

    // ---- stags ------------------------------------------------------------------------------

    private static void tickStags(ServerLevel level, ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(COUNT_RANGE, 64.0, COUNT_RANGE);
        if (!level.getEntitiesOfClass(L227Stag.class, area).isEmpty() || countAll(level, L227Stag.class) >= MAX_STAGS) {
            return;
        }
        BlockPos spot = findSpot(level, player, STAG_MIN_DISTANCE, STAG_MAX_DISTANCE, false);
        if (spot != null) {
            Level227Predators.STAG.get().spawn(level, spot, MobSpawnType.EVENT);
        }
    }

    // ---- squirrels --------------------------------------------------------------------------

    private static void tickSwarm(ServerLevel level, ServerPlayer player, long now) {
        Long last = LAST_SWARM.get(player.getUUID());
        if (last != null && now - last < SWARM_COOLDOWN_TICKS) {
            return;
        }
        // Only on the forest floor of a branch top, where they live (not out on a bare limb).
        int feet = Level227Tree.topSurfaceY(player.getX(), player.getZ());
        if (feet == Integer.MIN_VALUE || Math.abs(feet - player.getY()) > 3.0 || SquirrelSwarm.isHunting(player)) {
            return;
        }
        if (SquirrelSwarm.start(level, player, SquirrelSwarm.TOTAL, true) != null) {
            LAST_SWARM.put(player.getUUID(), now);
        }
    }

    // ---- spots ------------------------------------------------------------------------------

    /**
     * A place to stand on a branch top {@code min..max} blocks (sideways) from the player, on
     * roughly the same branch (within 24 blocks of height), outside any sunbeam, never in sight of
     * anyone and not near another player. {@code high}: only in the higher sections (wolves).
     */
    @Nullable
    public static BlockPos findSpot(ServerLevel level, ServerPlayer player, double min, double max, boolean high) {
        RandomSource random = player.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = min + random.nextDouble() * (max - min);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            int feet = Level227Tree.topSurfaceY(x, z);
            if (feet == Integer.MIN_VALUE || Math.abs(feet - player.getY()) > 24.0
                    || (high && !Level227Tree.isLevelHigh(feet)) || Level227Tree.beamAt(x, z) != null) {
                continue;
            }
            BlockPos spot = Level39Spawner.standableNear(level, x, feet, z, 4);
            if (spot != null && fair(level, spot)) {
                return spot;
            }
        }
        return null;
    }

    private static boolean fair(ServerLevel level, BlockPos spot) {
        Vec3 at = Vec3.atBottomCenterOf(spot);
        for (ServerPlayer other : level.players()) {
            if (other.distanceToSqr(at) < MIN_DISTANCE * MIN_DISTANCE) {
                return false;
            }
        }
        return !SafeRooms.isNearSafeRoom(level, spot, Level39Spawner.SAFE_ROOM_MARGIN) && !Level39Spawner.anyoneCanSee(level, spot);
    }

    private static int countAll(ServerLevel level, Class<? extends net.minecraft.world.entity.Entity> type) {
        int count = 0;
        for (var entity : level.getAllEntities()) {
            if (type.isInstance(entity)) {
                count++;
            }
        }
        return count;
    }

    // ---- /backrooms spawn l227_* ------------------------------------------------------------

    /** Brigadier merges this into the existing /backrooms command (same trick as Level39Spawner). */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(spawnCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> spawnCommand() {
        return Commands.literal("backrooms")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("l227_wolf")
                                .executes(ctx -> spawnWolves(ctx, 4))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 8))
                                        .executes(ctx -> spawnWolves(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
                        .then(Commands.literal("l227_stag")
                                .executes(Level227PredatorSpawner::spawnStag))
                        .then(Commands.literal("l227_squirrels")
                                .executes(ctx -> startSwarm(ctx, SquirrelSwarm.TOTAL))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, SquirrelSwarm.TOTAL))
                                        .executes(ctx -> startSwarm(ctx, IntegerArgumentType.getInteger(ctx, "count"))))));
    }

    /** A pack about 20 blocks in front of you. */
    private static int spawnWolves(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos spot = groundAhead(player, 20.0);
        int done = spawnPack(player.serverLevel(), spot, count);
        ctx.getSource().sendSuccess(() -> Component.literal("Spawned a pack of " + done + " wolves at " + spot.toShortString()), true);
        return done;
    }

    /** A stag about 24 blocks in front of you. */
    private static int spawnStag(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos spot = groundAhead(player, 24.0);
        boolean done = Level227Predators.STAG.get().spawn(player.serverLevel(), spot, MobSpawnType.COMMAND) != null;
        ctx.getSource().sendSuccess(() -> Component.literal(done ? "Spawned a stag at " + spot.toShortString() : "No room for a stag"), true);
        return done ? 1 : 0;
    }

    /** A squirrel swarm of {@code count} on you, right now (only one per world at a time). */
    private static int startSwarm(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean started = SquirrelSwarm.start(player.serverLevel(), player, count, true) != null;
        ctx.getSource().sendSuccess(() -> Component.literal(started
                ? "A swarm of " + count + " squirrels is coming for you (survival mode only)"
                : "A swarm is already running in this world"), true);
        return started ? 1 : 0;
    }

    /** A spot to stand on about {@code distance} blocks ahead of the player (their feet if none). */
    private static BlockPos groundAhead(ServerPlayer player, double distance) {
        Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
        if (look.lengthSqr() < 1.0e-4) {
            look = new Vec3(0.0, 0.0, 1.0);
        }
        Vec3 ahead = player.position().add(look.normalize().scale(distance));
        int x = Mth.floor(ahead.x);
        int z = Mth.floor(ahead.z);
        int top = player.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int startY = Math.abs(top - player.getY()) < 12 ? top : Mth.floor(player.getY());
        BlockPos spot = Level39Spawner.standableNear(player.serverLevel(), x, startY, z, 8);
        return spot != null ? spot : player.blockPosition();
    }

    private Level227PredatorSpawner() {}
}
