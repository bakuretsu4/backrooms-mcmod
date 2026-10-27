package com.rickr.backrooms.entity;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.Level39Mobs;
import com.rickr.backrooms.player.HidingService;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.SafeRooms;
import com.rickr.backrooms.world.layout.Level39Layout;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Who lives in Level 39 "Megalophobia" and where they turn up. Runs once a second, only in
 * {@code backrooms:level_39}, and never on Peaceful. Like the other spawners it places monsters
 * near players itself instead of using vanilla mob spawning, so every spawn is out of sight and at
 * a fair distance, with a cap per player.
 *
 * <p>Two zones, decided by the player's height:
 * <ul>
 *   <li><b>Tunnels</b> (below the town ground): a Hound now and then, never a pack. Up to
 *       {@value #TUNNEL_HOUNDS_PER_PLAYER} within {@value #TUNNEL_COUNT_RANGE} blocks of each player;
 *       below that cap a one-in-{@value #TUNNEL_HOUND_CHANCE} chance per second (about one every
 *       {@value #TUNNEL_HOUND_CHANCE} seconds) to add one 24-44 blocks away, on a tunnel rim or in
 *       the water.</li>
 *   <li><b>Town</b> (on the ground): Hounds are rare (at most {@value #TOWN_HOUNDS_PER_PLAYER}, about
 *       one every ten minutes, only in a dark spot), Facelings are rare too: roughly every two
 *       minutes a few appear 32-56 blocks away, up to {@value #TOWN_FACELINGS_PER_PLAYER} around a
 *       player. One spawn in four is a hostile group of 3-7 ("Hostile without a cause").</li>
 * </ul>
 * Nothing appears near a safe room, within {@value #MIN_DISTANCE} blocks of any player, or where
 * anyone can see it appear.
 *
 * <p>Other Level 39 entities (Clumps, Skin-stealers, Wretches) hook in through the marked line in
 * {@link #onLevelTick}, and can reuse the public spot finders here ({@link #findTunnelSpot},
 * {@link #findTownSpot}, {@link #anyoneCanSee}).
 *
 * <p>Also adds the test commands {@code /backrooms spawn hound [count]} and
 * {@code /backrooms spawn faceling [hostile] [count]} (operators).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level39Spawner {
    /** Where a player is: down in the flooded tunnels, or up in the town. */
    public enum Zone { TUNNELS, TOWN }

    /** Below this feet height a player counts as being in the tunnels (the town ground is at 72). */
    public static final int TUNNEL_ZONE_TOP = Level39Layout.GROUND_Y - 12;

    public static final int MIN_DISTANCE = 24;
    /** No spawns within this many blocks (sideways) of a safe room. */
    public static final int SAFE_ROOM_MARGIN = 12;
    private static final double VIEW_CHECK_RADIUS = 64.0;

    // --- Hounds in the tunnels ---------------------------------------------------------------
    public static final int TUNNEL_HOUNDS_PER_PLAYER = 1;
    public static final int TUNNEL_COUNT_RANGE = 64;
    private static final int TUNNEL_HOUND_CHANCE = 90;
    private static final double TUNNEL_MAX_DISTANCE = 44.0;

    // --- The town ----------------------------------------------------------------------------
    public static final int TOWN_HOUNDS_PER_PLAYER = 1;
    private static final int TOWN_HOUND_CHANCE = 600;
    /** Town hounds only appear where the block light is at most this: the dark middle of town. */
    private static final int TOWN_HOUND_MAX_LIGHT = 6;
    public static final int TOWN_FACELINGS_PER_PLAYER = 4;
    private static final int FACELING_CHANCE = 120;
    /** One faceling spawn in this many is a hostile group. */
    private static final int HOSTILE_GROUP_ONE_IN = 4;
    /** Never a second hostile group while one is already this close. */
    private static final double HOSTILE_GROUP_SPACING = 96.0;
    public static final int TOWN_COUNT_RANGE = 96;
    private static final double TOWN_MIN_DISTANCE = 32.0;
    private static final double TOWN_MAX_DISTANCE = 56.0;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20 != 0
                || level.getDifficulty() == Difficulty.PEACEFUL
                || BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_39) {
            return;
        }
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_39);
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || SafeRooms.isSafe(player) || HidingService.isHidden(player)) {
                continue; // nothing new shows up for someone resting in a safe room or hiding in furniture
            }
            Zone zone = zoneOf(player);
            if (zone == Zone.TUNNELS) {
                tickTunnelHounds(level, seed, player);
            } else {
                tickTown(level, player);
            }
            // Clumps, Skin-stealers and Wretches (tunnels only): see TunnelDwellers.
            TunnelDwellers.tick(level, seed, player, zone);
        }
    }

    /** Which zone this player is in. */
    public static Zone zoneOf(Entity entity) {
        return entity.getY() < TUNNEL_ZONE_TOP ? Zone.TUNNELS : Zone.TOWN;
    }

    // ---- Hounds in the tunnels --------------------------------------------------------------

    private static void tickTunnelHounds(ServerLevel level, long seed, ServerPlayer player) {
        if (player.getRandom().nextInt(TUNNEL_HOUND_CHANCE) != 0) {
            return;
        }
        AABB area = player.getBoundingBox().inflate(TUNNEL_COUNT_RANGE, 24.0, TUNNEL_COUNT_RANGE);
        if (level.getEntitiesOfClass(Hound.class, area).size() >= TUNNEL_HOUNDS_PER_PLAYER) {
            return;
        }
        BlockPos spot = findTunnelSpot(level, seed, player, MIN_DISTANCE, TUNNEL_MAX_DISTANCE, true);
        if (spot != null) {
            Level39Mobs.HOUND.get().spawn(level, spot, MobSpawnType.EVENT);
        }
    }

    // ---- The town ---------------------------------------------------------------------------

    private static void tickTown(ServerLevel level, ServerPlayer player) {
        RandomSource random = player.getRandom();
        AABB area = player.getBoundingBox().inflate(TOWN_COUNT_RANGE, 48.0, TOWN_COUNT_RANGE);
        if (random.nextInt(TOWN_HOUND_CHANCE) == 0
                && level.getEntitiesOfClass(Hound.class, area).size() < TOWN_HOUNDS_PER_PLAYER) {
            BlockPos spot = findTownSpot(level, player, TOWN_MIN_DISTANCE, TOWN_MAX_DISTANCE, true, TOWN_HOUND_MAX_LIGHT);
            if (spot != null) {
                Level39Mobs.HOUND.get().spawn(level, spot, MobSpawnType.EVENT);
            }
        }
        if (random.nextInt(FACELING_CHANCE) == 0
                && level.getEntitiesOfClass(Faceling.class, area).size() < TOWN_FACELINGS_PER_PLAYER) {
            BlockPos spot = findTownSpot(level, player, TOWN_MIN_DISTANCE, TOWN_MAX_DISTANCE, true, 15);
            if (spot == null) {
                return;
            }
            boolean hostile = random.nextInt(HOSTILE_GROUP_ONE_IN) == 0 && !hostileGroupNear(level, spot);
            // Strangers come alone or in pairs; the hostile ones in a pack of 3-7.
            int count = hostile ? 3 + random.nextInt(5) : 1 + (random.nextInt(3) == 0 ? 1 : 0);
            spawnFacelings(level, spot, count, hostile);
        }
    }

    private static boolean hostileGroupNear(ServerLevel level, BlockPos spot) {
        AABB around = new AABB(spot).inflate(HOSTILE_GROUP_SPACING);
        for (Faceling faceling : level.getEntitiesOfClass(Faceling.class, around)) {
            if (faceling.isHostile()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Spawns {@code count} facelings standing loosely together around {@code center} (each on its
     * own patch of ground within a few blocks). Returns how many appeared.
     */
    public static int spawnFacelings(ServerLevel level, BlockPos center, int count, boolean hostile) {
        RandomSource random = level.getRandom();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            BlockPos spot = i == 0 ? center : standableNear(level,
                    center.getX() + random.nextInt(7) - 3, center.getY(), center.getZ() + random.nextInt(7) - 3, 3);
            if (spot == null) {
                spot = center;
            }
            Faceling faceling = Level39Mobs.FACELING.get().spawn(level, spot, MobSpawnType.EVENT);
            if (faceling != null) {
                faceling.setHostile(hostile);
                spawned++;
            }
        }
        return spawned;
    }

    // ---- Spot finders (public so other Level 39 spawns can share them) ----------------------

    /**
     * A spot in a tunnel {@code min..max} blocks (sideways) from the player: on a tiled rim or on
     * the floor under the water. Null if none was found this time.
     *
     * @param hidden only where no player can see it appear
     */
    @Nullable
    public static BlockPos findTunnelSpot(ServerLevel level, long seed, ServerPlayer player, double min, double max, boolean hidden) {
        RandomSource random = player.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = min + random.nextDouble() * (max - min);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            if (!Level39Layout.tunnelAt(seed, x, z)) {
                continue;
            }
            // Start the search either at the water floor or up at the rims, so both get used.
            int startY = Level39Layout.TUNNEL_FLOOR_Y + 1 + random.nextInt(4);
            BlockPos spot = standableNear(level, x, startY, z, Level39Layout.TUNNEL_VAULT_TOP - Level39Layout.TUNNEL_FLOOR_Y);
            if (spot == null || spot.getY() < Level39Layout.TUNNEL_FLOOR_Y || spot.getY() >= Level39Layout.TUNNEL_VAULT_TOP) {
                continue;
            }
            if (fairSpot(level, spot, hidden)) {
                return spot;
            }
        }
        return null;
    }

    /**
     * A spot on the town ground {@code min..max} blocks (sideways) from the player, under the open
     * sky (not inside a house or on a roof), with block light at most {@code maxLight}.
     */
    @Nullable
    public static BlockPos findTownSpot(ServerLevel level, ServerPlayer player, double min, double max, boolean hidden, int maxLight) {
        RandomSource random = player.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = min + random.nextDouble() * (max - min);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            if (!Level39Layout.inTown(x, z) || !level.isLoaded(new BlockPos(x, Level39Layout.GROUND_Y, z))) {
                continue;
            }
            // The highest solid block must be the ground itself (a fence post or a step at most):
            // anything higher means a house, a tree or a pole stands here.
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (top < Level39Layout.GROUND_Y || top > Level39Layout.GROUND_Y + 2) {
                continue;
            }
            BlockPos spot = standableNear(level, x, top, z, 2);
            if (spot == null || level.getBrightness(LightLayer.BLOCK, spot.above()) > maxLight) {
                continue;
            }
            if (fairSpot(level, spot, hidden)) {
                return spot;
            }
        }
        return null;
    }

    /** Not near a safe room, not right next to a player, and (if {@code hidden}) out of everyone's sight. */
    private static boolean fairSpot(ServerLevel level, BlockPos spot, boolean hidden) {
        for (ServerPlayer other : level.players()) {
            if (other.distanceToSqr(Vec3.atBottomCenterOf(spot)) < MIN_DISTANCE * MIN_DISTANCE) {
                return false;
            }
        }
        if (SafeRooms.isNearSafeRoom(level, spot, SAFE_ROOM_MARGIN)) {
            return false;
        }
        return !hidden || !anyoneCanSee(level, spot);
    }

    /**
     * The first place to stand found scanning down from {@code (x, y, z)} for {@code depth}
     * blocks: solid underfoot, and room for feet and head (water counts as room, so the flooded
     * tunnel floor works). Null if the chunk isn't loaded or nothing fits.
     */
    @Nullable
    public static BlockPos standableNear(ServerLevel level, int x, int y, int z, int depth) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        if (!level.isLoaded(pos)) {
            return null;
        }
        for (int dy = 0; dy <= depth * 2; dy++) {
            // Scan y, y-1, y+1, y-2, y+2... so the nearest fit wins.
            int py = y + ((dy & 1) == 0 ? -(dy / 2) : (dy + 1) / 2);
            if (isStandable(level, pos.set(x, py, z))) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static boolean isStandable(ServerLevel level, BlockPos.MutableBlockPos feet) {
        int y = feet.getY();
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
            return false;
        }
        feet.setY(y + 1);
        boolean headClear = level.getBlockState(feet).getCollisionShape(level, feet).isEmpty();
        feet.setY(y - 1);
        boolean floor = !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty();
        feet.setY(y);
        return headClear && floor;
    }

    /**
     * True if any nearby player has a clear line to the spot (its legs or its head). VISUAL
     * shapes let fences and glass count as see-through.
     */
    public static boolean anyoneCanSee(ServerLevel level, BlockPos spot) {
        Vec3 low = Vec3.atBottomCenterOf(spot).add(0.0, 0.6, 0.0);
        Vec3 high = Vec3.atBottomCenterOf(spot).add(0.0, 1.8, 0.0);
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

    // ---- /backrooms spawn hound|faceling ----------------------------------------------------

    /** Brigadier merges this into the existing /backrooms command (same trick as Level9Spawner). */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(spawnCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> spawnCommand() {
        return Commands.literal("backrooms")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("hound")
                                .executes(ctx -> spawnHounds(ctx, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 10))
                                        .executes(ctx -> spawnHounds(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
                        .then(Commands.literal("faceling")
                                .executes(ctx -> spawnFacelingCommand(ctx, 1, false))
                                .then(Commands.literal("hostile")
                                        .executes(ctx -> spawnFacelingCommand(ctx, 5, true))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 10))
                                                .executes(ctx -> spawnFacelingCommand(ctx, IntegerArgumentType.getInteger(ctx, "count"), true))))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 10))
                                        .executes(ctx -> spawnFacelingCommand(ctx, IntegerArgumentType.getInteger(ctx, "count"), false)))));
    }

    /** Hounds about 14 blocks in front of you. They only hunt players in survival or adventure. */
    private static int spawnHounds(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos spot = groundAhead(player, 14.0);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            if (Level39Mobs.HOUND.get().spawn(level, spot, MobSpawnType.COMMAND) != null) {
                spawned++;
            }
        }
        int done = spawned;
        ctx.getSource().sendSuccess(() -> Component.literal("Spawned " + done + " Hound(s) at " + spot.toShortString()), true);
        return spawned;
    }

    /** Facelings about 12 blocks in front of you: Strangers by default, or a hostile pack. */
    private static int spawnFacelingCommand(CommandContext<CommandSourceStack> ctx, int count, boolean hostile) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos spot = groundAhead(player, 12.0);
        int done = spawnFacelings(player.serverLevel(), spot, count, hostile);
        ctx.getSource().sendSuccess(() -> Component.literal("Spawned " + done + (hostile ? " hostile" : " Stranger") + " Faceling(s) at " + spot.toShortString()), true);
        return done;
    }

    /** A spot to stand on about {@code distance} blocks ahead of the player (their feet if none). */
    private static BlockPos groundAhead(ServerPlayer player, double distance) {
        Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
        if (look.lengthSqr() < 1.0e-4) {
            look = new Vec3(0.0, 0.0, 1.0);
        }
        Vec3 ahead = player.position().add(look.normalize().scale(distance));
        BlockPos spot = standableNear(player.serverLevel(), Mth.floor(ahead.x), Mth.floor(player.getY()), Mth.floor(ahead.z), 8);
        return spot != null ? spot : player.blockPosition();
    }

    private Level39Spawner() {}
}
