package com.rickr.backrooms.entity;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.Level39Mobs;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.SafeRooms;
import com.rickr.backrooms.world.layout.Level39Layout;
import com.rickr.backrooms.world.layout.Level39Tunnels;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Where Level 39's Clumps, Skin-stealers and Wretches turn up. Called once a second per player
 * from {@link Level39Spawner#onLevelTick} (which already skips Peaceful, spectators and players in
 * safe rooms), and only does anything while the player is down in the tunnels (feet between
 * y {@value #ZONE_BOTTOM} and {@value #ZONE_TOP}).
 * <ul>
 *   <li><b>Clumps</b>: up to {@value #CLUMPS_PER_PLAYER} within {@value #COUNT_RANGE} blocks;
 *       below that a one-in-{@value #CLUMP_CHANCE} chance per second to hide a new one in a drain
 *       or a side alcove {@value #CLUMP_MIN_DISTANCE}-{@value #CLUMP_MAX_DISTANCE} blocks away,
 *       where nobody is looking, never two within {@value #CLUMP_SPACING} blocks.</li>
 *   <li><b>Wretches</b> (moderate): up to {@value #WRETCHES_PER_PLAYER} within
 *       {@value #COUNT_RANGE} blocks; one-in-{@value #WRETCH_CHANCE} per second, alone or in a pair.</li>
 *   <li><b>Skin-stealers</b> (rare): at most one within {@value #SKIN_STEALER_RANGE} blocks; a
 *       one-in-{@value #SKIN_STEALER_CHANCE} chance per second (about every 2.5 minutes).</li>
 * </ul>
 * Also the operator test commands {@code /backrooms spawn clump [ceiling|alcove]},
 * {@code /backrooms spawn skin_stealer [hungry|fed]} and {@code /backrooms spawn wretch [count]}.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class TunnelDwellers {
    public static final int ZONE_BOTTOM = Level39Layout.TUNNEL_FLOOR_Y - 2;
    public static final int ZONE_TOP = Level39Layout.TUNNEL_VAULT_TOP + 2;
    public static final int COUNT_RANGE = 64;

    public static final int CLUMPS_PER_PLAYER = 3;
    private static final int CLUMP_CHANCE = 6;
    private static final int CLUMP_MIN_DISTANCE = 20;
    private static final int CLUMP_MAX_DISTANCE = 56;
    private static final int CLUMP_SPACING = 16;

    public static final int WRETCHES_PER_PLAYER = 3;
    private static final int WRETCH_CHANCE = 10;

    private static final int SKIN_STEALER_RANGE = 128;
    private static final int SKIN_STEALER_CHANCE = 150;

    /** Where a drain clump's feet go: its body fills the bottom of the 2x2 drain shaft and pokes into the vault. */
    private static final double DRAIN_FEET_Y = Level39Layout.TUNNEL_VAULT_TOP - 1.7;
    /** Alcove clumps sit this far behind the alcove's centre, toward its back wall. */
    private static final double ALCOVE_DEPTH = 0.5;

    /** The hook {@link Level39Spawner} calls for every player, once a second. */
    public static void tick(ServerLevel level, long seed, ServerPlayer player, Level39Spawner.Zone zone) {
        if (zone != Level39Spawner.Zone.TUNNELS || player.getY() < ZONE_BOTTOM || player.getY() > ZONE_TOP) {
            return;
        }
        RandomSource random = player.getRandom();
        AABB area = player.getBoundingBox().inflate(COUNT_RANGE, 16.0, COUNT_RANGE);
        if (random.nextInt(CLUMP_CHANCE) == 0 && level.getEntitiesOfClass(Clump.class, area).size() < CLUMPS_PER_PLAYER) {
            hideClump(level, seed, player, CLUMP_MIN_DISTANCE, CLUMP_MAX_DISTANCE, true, null);
        }
        if (random.nextInt(WRETCH_CHANCE) == 0 && level.getEntitiesOfClass(Wretch.class, area).size() < WRETCHES_PER_PLAYER) {
            BlockPos spot = Level39Spawner.findTunnelSpot(level, seed, player, Level39Spawner.MIN_DISTANCE, 44.0, true);
            if (spot != null) {
                spawnWretches(level, spot, random.nextInt(4) == 0 ? 2 : 1);
            }
        }
        if (random.nextInt(SKIN_STEALER_CHANCE) == 0) {
            AABB wide = player.getBoundingBox().inflate(SKIN_STEALER_RANGE, 24.0, SKIN_STEALER_RANGE);
            if (level.getEntitiesOfClass(SkinStealer.class, wide).isEmpty()) {
                BlockPos spot = Level39Spawner.findTunnelSpot(level, seed, player, 32.0, 56.0, true);
                if (spot != null && clearAbove(level, spot, 3)) {
                    Level39Mobs.SKIN_STEALER.get().spawn(level, spot, MobSpawnType.EVENT);
                }
            }
        }
    }

    // ---- Clumps ------------------------------------------------------------------------------

    /**
     * Hides a Clump in a drain or a side alcove {@code min..max} blocks (sideways) from the player.
     *
     * @param hidden only where no player can see the hiding place
     * @param only   only this kind of hiding place (null: either)
     */
    @Nullable
    public static Clump hideClump(ServerLevel level, long seed, ServerPlayer player, int min, int max, boolean hidden,
                                  @Nullable Clump.Hide only) {
        List<Level39Tunnels.Feature> spots = new ArrayList<>();
        int px = Mth.floor(player.getX());
        int pz = Mth.floor(player.getZ());
        Level39Layout.tunnelFeaturesNear(seed, px, pz, max, feature -> {
            boolean wanted = feature.kind() == Level39Tunnels.Kind.DRAIN || feature.kind() == Level39Tunnels.Kind.SIDE_ARCH;
            if (wanted && (only == null || hideOf(feature) == only)) {
                double dx = feature.x() - px;
                double dz = feature.z() - pz;
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance >= min && distance <= max) {
                    spots.add(feature);
                }
            }
        });
        RandomSource random = player.getRandom();
        for (int attempt = 0; attempt < 6 && !spots.isEmpty(); attempt++) {
            Level39Tunnels.Feature feature = spots.remove(random.nextInt(spots.size()));
            Clump clump = tryHide(level, feature, hidden);
            if (clump != null) {
                return clump;
            }
        }
        return null;
    }

    private static Clump.Hide hideOf(Level39Tunnels.Feature feature) {
        return feature.kind() == Level39Tunnels.Kind.DRAIN ? Clump.Hide.CEILING : Clump.Hide.ALCOVE;
    }

    @Nullable
    private static Clump tryHide(ServerLevel level, Level39Tunnels.Feature feature, boolean hidden) {
        Clump.Hide hide = hideOf(feature);
        Vec3 feet;
        float yaw = feature.facing() != null ? feature.facing().toYRot() : 0.0f;
        if (hide == Clump.Hide.CEILING) {
            // Drains are 2x2: (x, z) is the corner block, so the middle is one block further in.
            feet = new Vec3(feature.x() + 1.0, DRAIN_FEET_Y, feature.z() + 1.0);
            BlockPos shaft = new BlockPos(feature.x(), feature.y(), feature.z());
            if (!level.isLoaded(shaft) || !level.getBlockState(shaft).getCollisionShape(level, shaft).isEmpty()) {
                return null; // not generated yet, or blocked
            }
        } else {
            float rad = yaw * Mth.DEG_TO_RAD;
            // facing points out toward the tunnel, so "deeper" is the opposite way.
            feet = new Vec3(feature.x() + 0.5 + Mth.sin(rad) * ALCOVE_DEPTH, feature.y(), feature.z() + 0.5 - Mth.cos(rad) * ALCOVE_DEPTH);
            BlockPos floor = new BlockPos(feature.x(), feature.y(), feature.z());
            if (!level.isLoaded(floor) || !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) {
                return null;
            }
        }
        BlockPos at = BlockPos.containing(feet);
        if (SafeRooms.isNearSafeRoom(level, at, Level39Spawner.SAFE_ROOM_MARGIN)) {
            return null;
        }
        if (!level.getEntitiesOfClass(Clump.class, new AABB(at).inflate(CLUMP_SPACING)).isEmpty()) {
            return null; // one per stretch of tunnel
        }
        for (ServerPlayer other : level.players()) {
            if (other.distanceToSqr(feet) < 12.0 * 12.0) {
                return null; // never right next to someone
            }
        }
        if (hidden && Level39Spawner.anyoneCanSee(level, at)) {
            return null;
        }
        return Clump.place(level, hide, feet, yaw);
    }

    // ---- Wretches and Skin-stealers ----------------------------------------------------------

    /** {@code count} wretches huddled around {@code center}. Returns how many appeared. */
    public static int spawnWretches(ServerLevel level, BlockPos center, int count) {
        RandomSource random = level.getRandom();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            BlockPos spot = i == 0 ? center : Level39Spawner.standableNear(level,
                    center.getX() + random.nextInt(5) - 2, center.getY(), center.getZ() + random.nextInt(5) - 2, 2);
            if (Level39Mobs.WRETCH.get().spawn(level, spot != null ? spot : center, MobSpawnType.EVENT) != null) {
                spawned++;
            }
        }
        return spawned;
    }

    /** Room for a tall body: {@code blocks} free blocks from the feet up. */
    private static boolean clearAbove(ServerLevel level, BlockPos feet, int blocks) {
        BlockPos.MutableBlockPos pos = feet.mutable();
        for (int i = 0; i < blocks; i++) {
            pos.setY(feet.getY() + i);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ---- /backrooms spawn clump|skin_stealer|wretch -----------------------------------------

    /** Brigadier merges this into the existing /backrooms command (same trick as Level9Spawner). */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(spawnCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> spawnCommand() {
        return Commands.literal("backrooms")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("clump")
                                .executes(ctx -> spawnClumpCommand(ctx, null))
                                .then(Commands.literal("ceiling").executes(ctx -> spawnClumpCommand(ctx, Clump.Hide.CEILING)))
                                .then(Commands.literal("alcove").executes(ctx -> spawnClumpCommand(ctx, Clump.Hide.ALCOVE))))
                        .then(Commands.literal("skin_stealer")
                                .executes(ctx -> spawnSkinStealerCommand(ctx, true))
                                .then(Commands.literal("hungry").executes(ctx -> spawnSkinStealerCommand(ctx, true)))
                                .then(Commands.literal("fed").executes(ctx -> spawnSkinStealerCommand(ctx, false))))
                        .then(Commands.literal("wretch")
                                .executes(ctx -> spawnWretchCommand(ctx, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 10))
                                        .executes(ctx -> spawnWretchCommand(ctx, IntegerArgumentType.getInteger(ctx, "count"))))));
    }

    /**
     * Hides a Clump in the nearest drain or alcove within 40 blocks (visible or not). With no
     * tunnel hiding place around (e.g. testing outside Level 39) it hangs from the ceiling above a
     * spot 6 blocks ahead, or sits on the floor there if the ceiling is too high.
     */
    private static int spawnClumpCommand(CommandContext<CommandSourceStack> ctx, @Nullable Clump.Hide only) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        Clump clump = null;
        if (BackroomsLevel.fromDimension(level.dimension()) == BackroomsLevel.LEVEL_39) {
            long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_39);
            clump = hideClump(level, seed, player, 0, 40, false, only);
        }
        if (clump == null) {
            clump = clumpAhead(level, player, only);
        }
        if (clump == null) {
            ctx.getSource().sendFailure(Component.literal("No room for a Clump here."));
            return 0;
        }
        Clump placed = clump;
        ctx.getSource().sendSuccess(() -> Component.literal("Hid a Clump (" + (placed.isCeiling() ? "drain/ceiling" : "alcove/floor")
                + ") at " + placed.blockPosition().toShortString() + ". Sneak to stay unheard."), true);
        return 1;
    }

    @Nullable
    private static Clump clumpAhead(ServerLevel level, ServerPlayer player, @Nullable Clump.Hide only) {
        BlockPos ground = groundAhead(player, 6.0);
        float yaw = player.getYRot() + 180.0f; // facing back toward the player
        if (only != Clump.Hide.ALCOVE) {
            BlockPos.MutableBlockPos pos = ground.mutable();
            for (int up = 3; up <= 8; up++) {
                pos.setY(ground.getY() + up);
                if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                    Vec3 feet = new Vec3(ground.getX() + 0.5, pos.getY() - 1.8 + 0.2, ground.getZ() + 0.5);
                    return Clump.place(level, Clump.Hide.CEILING, feet, yaw);
                }
            }
        }
        return Clump.place(level, Clump.Hide.ALCOVE, Vec3.atBottomCenterOf(ground), yaw);
    }

    private static int spawnSkinStealerCommand(CommandContext<CommandSourceStack> ctx, boolean hungry) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos spot = groundAhead(player, 14.0);
        SkinStealer stealer = Level39Mobs.SKIN_STEALER.get().spawn(player.serverLevel(), spot, MobSpawnType.COMMAND);
        if (stealer == null) {
            ctx.getSource().sendFailure(Component.literal("Could not spawn a Skin-stealer."));
            return 0;
        }
        stealer.setHungry(hungry);
        ctx.getSource().sendSuccess(() -> Component.literal("Spawned a " + (hungry ? "hungry" : "fed (docile)")
                + " Skin-stealer at " + spot.toShortString() + ". It only hunts players who are alone."), true);
        return 1;
    }

    private static int spawnWretchCommand(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos spot = groundAhead(player, 10.0);
        int done = spawnWretches(player.serverLevel(), spot, count);
        ctx.getSource().sendSuccess(() -> Component.literal("Spawned " + done + " Wretch(es) at " + spot.toShortString()
                + ". Light, noise or walking up to them makes them flee."), true);
        return done;
    }

    /** A spot to stand on about {@code distance} blocks ahead of the player (their feet if none). */
    private static BlockPos groundAhead(ServerPlayer player, double distance) {
        Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
        if (look.lengthSqr() < 1.0e-4) {
            look = new Vec3(0.0, 0.0, 1.0);
        }
        Vec3 ahead = player.position().add(look.normalize().scale(distance));
        BlockPos spot = Level39Spawner.standableNear(player.serverLevel(), Mth.floor(ahead.x), Mth.floor(player.getY()), Mth.floor(ahead.z), 8);
        return spot != null ? spot : player.blockPosition();
    }

    private TunnelDwellers() {}
}
