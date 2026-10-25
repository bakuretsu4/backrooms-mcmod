package com.rickr.backrooms.entity;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModEntities;
import com.rickr.backrooms.world.BackroomsLevel;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Who lives in Level 9 and where they appear. Runs once a second, only in Level 9:
 * <ul>
 *   <li>Watchers: up to {@value #WATCHERS_PER_PLAYER} within {@value #WATCHER_COUNT_RANGE} blocks of
 *       each player. New ones appear over a road 40-64 blocks away, where the player can't see them
 *       appear.</li>
 *   <li>Observers: now and then, one per player, in a dark spot 25-40 blocks away.</li>
 * </ul>
 * Also adds the test command {@code /backrooms spawn watcher|observer [targets]} (operators).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level9Spawner {
    public static final int WATCHERS_PER_PLAYER = 2;
    public static final int WATCHER_COUNT_RANGE = 96;
    private static final double WATCHER_MIN_DISTANCE = 40.0;
    private static final double WATCHER_MAX_DISTANCE = 64.0;
    /** One-in-N chance per second to add a Watcher when a player is below the cap. */
    private static final int WATCHER_SPAWN_CHANCE = 8;

    private static final double OBSERVER_MIN_DISTANCE = 25.0;
    private static final double OBSERVER_MAX_DISTANCE = 40.0;
    /** One-in-N chance per second for an Observer once the cooldown is over (~1.5 min on average). */
    private static final int OBSERVER_SPAWN_CHANCE = 90;
    /** Quiet time after an Observer vanished. */
    private static final int OBSERVER_COOLDOWN = 90 * 20;
    /** Observers only stand where the block light is at most this (the darkest spots). */
    private static final int OBSERVER_MAX_LIGHT = 5;

    /** Each player's current Observer and cooldown. Server thread only. */
    private static final Map<UUID, ObserverSlot> OBSERVERS = new HashMap<>();

    private static final class ObserverSlot {
        @Nullable
        UUID observerId;
        int cooldown = OBSERVER_COOLDOWN / 2;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20 != 0
                || BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_9) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            tickWatchers(level, player);
            tickObserver(level, player);
        }
    }

    // ---- Watchers --------------------------------------------------------------------------

    private static void tickWatchers(ServerLevel level, ServerPlayer player) {
        if (player.getRandom().nextInt(WATCHER_SPAWN_CHANCE) != 0) {
            return;
        }
        int nearby = level.getEntitiesOfClass(Watcher.class, player.getBoundingBox().inflate(WATCHER_COUNT_RANGE, 64.0, WATCHER_COUNT_RANGE)).size();
        if (nearby < WATCHERS_PER_PLAYER) {
            trySpawnWatcher(level, player, WATCHER_MIN_DISTANCE, WATCHER_MAX_DISTANCE, true);
        }
    }

    /**
     * Places a Watcher over a road {@code min..max} blocks from the player, at cruise altitude.
     *
     * @param hidden only where the player can't see it pop into existence
     */
    public static boolean trySpawnWatcher(ServerLevel level, ServerPlayer player, double min, double max, boolean hidden) {
        RandomSource random = player.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = min + random.nextDouble() * (max - min);
            Vec3 spot = streetSpotNear(level, Mth.floor(player.getX() + Math.cos(angle) * distance),
                    Mth.floor(player.getZ() + Math.sin(angle) * distance));
            if (spot == null) {
                continue;
            }
            if (hidden && clearLine(level, player, player.getEyePosition(), spot.add(0.0, Watcher.HALF_SIZE, 0.0))) {
                continue;
            }
            if (!level.getEntitiesOfClass(Watcher.class, new net.minecraft.world.phys.AABB(spot, spot).inflate(24.0)).isEmpty()) {
                continue; // don't stack them up
            }
            return spawnWatcherAt(level, spot, random.nextDouble() * Math.PI * 2.0);
        }
        return false;
    }

    private static boolean spawnWatcherAt(ServerLevel level, Vec3 spot, double heading) {
        Watcher watcher = ModEntities.WATCHER.get().create(level);
        if (watcher == null) {
            return false;
        }
        watcher.moveTo(spot.x, spot.y, spot.z, 0.0f, 0.0f);
        watcher.startPatrol(heading);
        return level.addFreshEntity(watcher);
    }

    /** A road column with open air above it within 6 blocks of (x, z), at cruise altitude. */
    @Nullable
    private static Vec3 streetSpotNear(ServerLevel level, int x, int z) {
        for (int r = 0; r <= 6; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue; // only the ring at distance r (inner rings were checked already)
                    }
                    if (Watcher.isStreet(level, x + dx, z + dz) && Watcher.isOpenAir(level, x + dx, z + dz)) {
                        return new Vec3(x + dx + 0.5, Watcher.CRUISE_Y, z + dz + 0.5);
                    }
                }
            }
        }
        return null;
    }

    // ---- Observers -------------------------------------------------------------------------

    private static void tickObserver(ServerLevel level, ServerPlayer player) {
        ObserverSlot slot = OBSERVERS.computeIfAbsent(player.getUUID(), id -> new ObserverSlot());
        if (slot.observerId != null) {
            if (level.getEntity(slot.observerId) instanceof Observer observer && observer.isAlive()) {
                return; // still standing out there
            }
            slot.observerId = null;
            slot.cooldown = OBSERVER_COOLDOWN;
        }
        slot.cooldown = Math.max(0, slot.cooldown - 20);
        if (slot.cooldown == 0 && player.getRandom().nextInt(OBSERVER_SPAWN_CHANCE) == 0) {
            spawnObserver(level, player, false);
        }
    }

    /**
     * Puts an Observer in the darkest open-air spot found 25-40 blocks from the player. Spots the
     * player has a clear view of score higher (it wants to be seen... eventually), and so do spots
     * outside their current view (it's already there when you turn around).
     *
     * @param relaxed for the test command: accept lit spots too
     */
    public static boolean spawnObserver(ServerLevel level, ServerPlayer player, boolean relaxed) {
        RandomSource random = player.getRandom();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = OBSERVER_MIN_DISTANCE + random.nextDouble() * (OBSERVER_MAX_DISTANCE - OBSERVER_MIN_DISTANCE);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            Double feetY = groundUnderOpenSky(level, x, z);
            if (feetY == null) {
                continue;
            }
            int light = level.getBrightness(LightLayer.BLOCK, BlockPos.containing(x, feetY + 1.5, z));
            if (!relaxed && light > OBSERVER_MAX_LIGHT) {
                continue;
            }
            Vec3 feet = new Vec3(x + 0.5, feetY, z + 0.5);
            Vec3 head = feet.add(0.0, Observer.HEAD_HEIGHT, 0.0);
            double score = (15 - light) + random.nextDouble() * 2.0;
            if (clearLine(level, player, eye, head)) {
                score += 6.0;
            }
            Vec3 toSpot = head.subtract(eye).multiply(1.0, 0.0, 1.0).normalize();
            if (toSpot.dot(look) < 0.5) {
                score += 3.0;
            }
            if (score > bestScore) {
                bestScore = score;
                best = feet;
            }
        }
        if (best == null) {
            return false;
        }
        Observer observer = ModEntities.OBSERVER.get().create(level);
        if (observer == null) {
            return false;
        }
        observer.moveTo(best.x, best.y, best.z, 0.0f, 0.0f);
        observer.setup(player);
        if (!level.addFreshEntity(observer)) {
            return false;
        }
        ObserverSlot slot = OBSERVERS.computeIfAbsent(player.getUUID(), id -> new ObserverSlot());
        slot.observerId = observer.getUUID();
        return true;
    }

    /**
     * Feet height for someone standing in column (x, z) at street level (y40-42 ground: road,
     * sidewalk slab, lawn) with nothing but leaves overhead, so never inside a house or under a roof.
     * Null if there's no such spot.
     */
    @Nullable
    private static Double groundUnderOpenSky(ServerLevel level, int x, int z) {
        if (!level.isLoaded(new BlockPos(x, 40, z))) {
            return null;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (top < 39 || top > 42) {
            return null;
        }
        BlockPos ground = new BlockPos(x, top, z);
        BlockState state = level.getBlockState(ground);
        VoxelShape shape = state.getCollisionShape(level, ground);
        if (shape.isEmpty() || !level.getFluidState(ground).isEmpty()) {
            return null;
        }
        for (int y = top + 1; y <= top + 3; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return null;
            }
        }
        return top + shape.max(Direction.Axis.Y);
    }

    private static boolean clearLine(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.MISS;
    }

    // ---- Bookkeeping -----------------------------------------------------------------------

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        OBSERVERS.remove(event.getEntity().getUUID()); // their Observer removes itself (no victim)
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        OBSERVERS.clear();
    }

    // ---- /backrooms spawn watcher|observer [targets] ----------------------------------------

    /** Brigadier merges this into the existing /backrooms command (same trick as /backrooms hallucinate). */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(spawnCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> spawnCommand() {
        return Commands.literal("backrooms")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("watcher")
                                .executes(ctx -> spawnWatchers(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> spawnWatchers(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
                        .then(Commands.literal("observer")
                                .executes(ctx -> spawnObservers(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> spawnObservers(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"))))));
    }

    /** A Watcher over a road 16-32 blocks away (visible or not); if there's no road, 16 blocks ahead, 7 up. */
    private static int spawnWatchers(CommandSourceStack source, Collection<ServerPlayer> players) {
        int count = 0;
        for (ServerPlayer player : players) {
            ServerLevel level = player.serverLevel();
            boolean done = trySpawnWatcher(level, player, 16.0, 32.0, false);
            if (!done) {
                Vec3 ahead = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(16.0);
                Vec3 spot = player.position().add(ahead.x, 0.0, ahead.z);
                double y = Math.max(Watcher.CRUISE_Y, player.getY() + 7.0);
                double heading = Math.atan2(ahead.z, ahead.x);
                done = spawnWatcherAt(level, new Vec3(spot.x, y, spot.z), heading);
            }
            if (done) {
                count++;
            }
        }
        int spawned = count;
        source.sendSuccess(() -> Component.literal("Spawned " + spawned + " Watcher(s)."), true);
        return spawned;
    }

    /** An Observer for each target (ignores cooldown and light level). */
    private static int spawnObservers(CommandSourceStack source, Collection<ServerPlayer> players) {
        int count = 0;
        for (ServerPlayer player : players) {
            if (spawnObserver(player.serverLevel(), player, true)) {
                count++;
            } else {
                source.sendFailure(Component.literal("No open ground 25-40 blocks from " + player.getGameProfile().getName() + "."));
            }
        }
        int spawned = count;
        if (spawned > 0) {
            source.sendSuccess(() -> Component.literal("Spawned " + spawned + " Observer(s) (only their target can see them)."), true);
        }
        return spawned;
    }

    private Level9Spawner() {}
}
