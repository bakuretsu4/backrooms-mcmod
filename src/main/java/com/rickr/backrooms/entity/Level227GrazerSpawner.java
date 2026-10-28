package com.rickr.backrooms.entity;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.Level227Grazers;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.layout.Level227Layout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Where the grazers of Level 227 "Megaflora" live. Runs once a second, only in
 * {@code backrooms:level_227}, and places animals near each player itself (no vanilla mob
 * spawning), always out of sight, never close to anyone, and with a cap around each player. All
 * three are never saved: this keeps the forest stocked as players move.
 * <ul>
 *   <li><b>Bears</b> sleep in the bear caves World carves into the branches
 *       ({@link Level227Layout#caveSpots}): one bear per cave, placed when a player comes within
 *       {@value #CAVE_RADIUS} blocks but is not yet inside, lying facing out of the cave mouth.
 *       Kill a cave's bear and the cave stays empty for {@value #CAVE_EMPTY_MINUTES} minutes.</li>
 *   <li><b>Does</b> graze in the open forest ({@link Level227Layout#forestSpots}): up to
 *       {@value #DOES_PER_PLAYER} around a player, roughly one every {@value #DOE_CHANCE} seconds
 *       below that.</li>
 *   <li><b>Boars</b> lounge in the open forest: up to {@value #BOARS_PER_PLAYER} around a player
 *       (sometimes a pair), roughly one every {@value #BOAR_CHANCE} seconds below that, most of
 *       them on their backs.</li>
 * </ul>
 * Nothing appears in a player's first minute in the level (they arrive in peace). On Peaceful the
 * dangerous two (bears and does) stay away; the boars, which are food, still come.
 *
 * <p>Also adds the test commands {@code /backrooms spawn l227_bear [awake]},
 * {@code /backrooms spawn l227_doe} and {@code /backrooms spawn l227_boar [standing]} (operators).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level227GrazerSpawner {
    /** Nothing spawns within this many blocks of any player. */
    public static final int MIN_DISTANCE = 24;
    /** A newly arrived player gets this long (ticks) before any animal is placed near them. */
    private static final int ARRIVAL_GRACE_TICKS = 60 * 20;

    // --- Bears ---------------------------------------------------------------------------------
    /** Caves within this many blocks of a player get their bear. */
    public static final int CAVE_RADIUS = 96;
    /** Caves are checked this often (ticks) per player: they don't move. */
    private static final int CAVE_CHECK_INTERVAL = 100;
    /** A cave whose bear was killed stays empty this long. */
    public static final int CAVE_EMPTY_MINUTES = 10;
    /** No more than this many bears around one player, whatever the caves. */
    public static final int BEARS_PER_PLAYER = 3;
    /** A bear counts as this cave's if its home is this close to the cave's middle. */
    private static final double HOME_MATCH = 4.0;

    // --- Does and boars ------------------------------------------------------------------------
    public static final int DOES_PER_PLAYER = 2;
    public static final int DOE_CHANCE = 40;
    public static final int BOARS_PER_PLAYER = 3;
    public static final int BOAR_CHANCE = 30;
    /** One boar spawn in this many is a pair. */
    private static final int BOAR_PAIR_ONE_IN = 3;
    /** Most boars are found lounging on their backs; the rest are up and about. */
    private static final float BOAR_LOUNGING = 0.7f;
    private static final double COUNT_RANGE = 112.0;
    private static final double SPAWN_MIN = 32.0;
    private static final double SPAWN_MAX = 72.0;

    /** When each player was first seen in Level 227 this visit (game time). */
    private static final Map<UUID, Long> ARRIVED = new HashMap<>();
    /** Caves (packed x/z) whose bear was killed, and when. */
    private static final Map<Long, Long> CAVE_EMPTIED = new HashMap<>();

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20 != 0
                || BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_227) {
            return;
        }
        long now = level.getGameTime();
        boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            Long arrived = ARRIVED.putIfAbsent(player.getUUID(), now);
            if (arrived == null || now - arrived < ARRIVAL_GRACE_TICKS) {
                continue; // the first minute is quiet
            }
            if (!peaceful && (now + player.getId()) % CAVE_CHECK_INTERVAL < 20) {
                stockCaves(level, player, now);
            }
            RandomSource random = player.getRandom();
            if (!peaceful && random.nextInt(DOE_CHANCE) == 0) {
                spawnInForest(level, player, Level227Grazers.DOE.get(), L227Doe.class, DOES_PER_PLAYER, 1, false);
            }
            if (random.nextInt(BOAR_CHANCE) == 0) {
                int count = random.nextInt(BOAR_PAIR_ONE_IN) == 0 ? 2 : 1;
                spawnInForest(level, player, Level227Grazers.BOAR.get(), L227Boar.class, BOARS_PER_PLAYER, count, true);
            }
        }
    }

    // ---- Bears in their caves -----------------------------------------------------------------

    private static void stockCaves(ServerLevel level, ServerPlayer player, long now) {
        AABB around = player.getBoundingBox().inflate(CAVE_RADIUS + 16.0, 96.0, CAVE_RADIUS + 16.0);
        List<L227Bear> bears = level.getEntitiesOfClass(L227Bear.class, around);
        if (bears.size() >= BEARS_PER_PLAYER) {
            return;
        }
        List<Level227Layout.Spot> caves = new ArrayList<>();
        Level227Layout.caveSpots(player.getX(), player.getZ(), CAVE_RADIUS, caves::add);
        int placed = bears.size();
        for (Level227Layout.Spot cave : caves) {
            if (placed >= BEARS_PER_PLAYER) {
                return;
            }
            Long emptied = CAVE_EMPTIED.get(caveKey(cave.x(), cave.z()));
            if (emptied != null && now - emptied < CAVE_EMPTY_MINUTES * 60L * 20L) {
                continue;
            }
            if (hasBear(bears, cave)) {
                continue;
            }
            BlockPos floor = new BlockPos(cave.x(), cave.y(), cave.z());
            // Never in front of anyone: not while a player is in or at the mouth of the cave.
            if (!level.isLoaded(floor) || tooClose(level, Vec3.atBottomCenterOf(floor), cave.size() + 14.0)
                    || Level39Spawner.anyoneCanSee(level, floor)) {
                continue;
            }
            BlockPos spot = Level39Spawner.standableNear(level, cave.x(), cave.y(), cave.z(), 3);
            if (spot == null) {
                continue;
            }
            L227Bear bear = Level227Grazers.BEAR.get().spawn(level, spot, MobSpawnType.EVENT);
            if (bear != null) {
                bear.setHome(spot, cave.yaw());
                bears.add(bear);
                placed++;
            }
        }
    }

    private static boolean hasBear(List<L227Bear> bears, Level227Layout.Spot cave) {
        for (L227Bear bear : bears) {
            BlockPos home = bear.home();
            if (home != null && Math.abs(home.getX() - cave.x()) <= HOME_MATCH && Math.abs(home.getZ() - cave.z()) <= HOME_MATCH) {
                return true;
            }
        }
        return false;
    }

    /** A dead bear leaves its cave empty for a while (it isn't refilled the moment you walk off). */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof L227Bear bear && !bear.level().isClientSide && bear.home() != null) {
            BlockPos home = bear.home();
            CAVE_EMPTIED.put(caveKey(home.getX(), home.getZ()), bear.level().getGameTime());
        }
    }

    private static long caveKey(int x, int z) {
        // Rounded to the cave's neighbourhood so the bear's home (a block or two off) matches the cave.
        return BlockPos.asLong(Math.floorDiv(x, 8), 0, Math.floorDiv(z, 8));
    }

    // ---- Does and boars in the open forest --------------------------------------------------

    private static <T extends net.minecraft.world.entity.Mob> void spawnInForest(ServerLevel level, ServerPlayer player,
            EntityType<T> type, Class<T> kind, int cap, int count, boolean boar) {
        AABB area = player.getBoundingBox().inflate(COUNT_RANGE, 64.0, COUNT_RANGE);
        int have = level.getEntitiesOfClass(kind, area).size();
        if (have >= cap) {
            return;
        }
        BlockPos spot = findForestSpot(level, player, type);
        if (spot == null) {
            return;
        }
        RandomSource random = player.getRandom();
        for (int i = 0; i < Math.min(count, cap - have); i++) {
            BlockPos at = i == 0 ? spot : Level39Spawner.standableNear(level, spot.getX() + random.nextInt(9) - 4, spot.getY(),
                    spot.getZ() + random.nextInt(9) - 4, 3);
            if (at == null || !level.noCollision(type.getSpawnAABB(at.getX() + 0.5, at.getY(), at.getZ() + 0.5))) {
                continue;
            }
            T mob = type.spawn(level, at, MobSpawnType.EVENT);
            if (mob != null) {
                mob.setYRot(random.nextFloat() * 360.0f);
                mob.yBodyRot = mob.getYRot();
                if (boar && mob instanceof L227Boar b && random.nextFloat() >= BOAR_LOUNGING) {
                    b.standUp();
                }
            }
        }
    }

    /**
     * An open forest spot {@value #SPAWN_MIN}-{@value #SPAWN_MAX} blocks from the player (from
     * World's plan), loaded, with room for the animal, out of everyone's sight and not close to
     * anyone. Null if none was found this time.
     */
    @Nullable
    private static BlockPos findForestSpot(ServerLevel level, ServerPlayer player, EntityType<?> type) {
        List<Level227Layout.Spot> spots = new ArrayList<>();
        Level227Layout.forestSpots(player.getX(), player.getZ(), SPAWN_MAX, spots::add);
        RandomSource random = player.getRandom();
        for (int attempt = 0; attempt < 6 && !spots.isEmpty(); attempt++) {
            Level227Layout.Spot s = spots.remove(random.nextInt(spots.size()));
            double dx = s.x() + 0.5 - player.getX();
            double dz = s.z() + 0.5 - player.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < SPAWN_MIN) {
                attempt--;
                continue;
            }
            BlockPos spot = Level39Spawner.standableNear(level, s.x(), s.y(), s.z(), 3);
            if (spot == null || tooClose(level, Vec3.atBottomCenterOf(spot), MIN_DISTANCE)
                    || !level.noCollision(type.getSpawnAABB(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5))
                    || Level39Spawner.anyoneCanSee(level, spot)) {
                continue;
            }
            return spot;
        }
        return null;
    }

    private static boolean tooClose(ServerLevel level, Vec3 at, double distance) {
        for (ServerPlayer other : level.players()) {
            if (!other.isSpectator() && other.distanceToSqr(at) < distance * distance) {
                return true;
            }
        }
        return false;
    }

    // ---- Bookkeeping ----------------------------------------------------------------------------

    /** A new visit to Level 227 starts a new quiet first minute. */
    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        ARRIVED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ARRIVED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ARRIVED.clear();
        CAVE_EMPTIED.clear();
    }

    // ---- /backrooms spawn l227_bear|l227_doe|l227_boar ----------------------------------------

    /** Brigadier merges this into the existing /backrooms command (same trick as Level39Spawner). */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(spawnCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> spawnCommand() {
        return Commands.literal("backrooms")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("l227_bear")
                                .executes(ctx -> spawnBear(ctx, false))
                                .then(Commands.literal("awake").executes(ctx -> spawnBear(ctx, true))))
                        .then(Commands.literal("l227_doe")
                                .executes(ctx -> spawnAhead(ctx, Level227Grazers.DOE.get(), false)))
                        .then(Commands.literal("l227_boar")
                                .executes(ctx -> spawnAhead(ctx, Level227Grazers.BOAR.get(), false))
                                .then(Commands.literal("standing").executes(ctx -> spawnAhead(ctx, Level227Grazers.BOAR.get(), true)))));
    }

    /** A bear about 10 blocks ahead, asleep facing you (or awake and coming). */
    private static int spawnBear(CommandContext<CommandSourceStack> ctx, boolean awake) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos spot = groundAhead(player, 10.0);
        L227Bear bear = Level227Grazers.BEAR.get().spawn(player.serverLevel(), spot, MobSpawnType.COMMAND);
        if (bear == null) {
            return 0;
        }
        bear.setHome(spot, player.getYRot() + 180.0f);
        if (awake) {
            bear.wakeNow();
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Spawned a " + (awake ? "waking" : "sleeping") + " bear at " + spot.toShortString()), true);
        return 1;
    }

    private static int spawnAhead(CommandContext<CommandSourceStack> ctx, EntityType<?> type, boolean standing) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        // Does are spawned well beyond their 4-block reach, so the command doesn't drop you at once.
        BlockPos spot = groundAhead(player, type == Level227Grazers.DOE.get() ? 12.0 : 10.0);
        var mob = type.spawn(player.serverLevel(), spot, MobSpawnType.COMMAND);
        if (mob == null) {
            return 0;
        }
        if (standing && mob instanceof L227Boar boar) {
            boar.standUp();
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Spawned " + type.getDescription().getString() + " at " + spot.toShortString()), true);
        return 1;
    }

    private static BlockPos groundAhead(ServerPlayer player, double distance) {
        Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
        if (look.lengthSqr() < 1.0e-4) {
            look = new Vec3(0.0, 0.0, 1.0);
        }
        Vec3 ahead = player.position().add(look.normalize().scale(distance));
        BlockPos spot = Level39Spawner.standableNear(player.serverLevel(), Mth.floor(ahead.x), Mth.floor(player.getY()), Mth.floor(ahead.z), 8);
        return spot != null ? spot : player.blockPosition();
    }

    private Level227GrazerSpawner() {}
}
