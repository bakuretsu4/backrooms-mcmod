package com.rickr.backrooms.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.SharedSuggestionProvider;
import java.util.Arrays;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rickr.backrooms.camera.CameraService;
import com.rickr.backrooms.player.SanityService;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.LevelEvents;
import com.rickr.backrooms.world.Travel;
import com.rickr.backrooms.registry.ModEntities;
import com.rickr.backrooms.network.LevelEventPayload;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

/**
 * Operator tools for hosting sessions with friends:
 * <pre>
 * /backrooms enter [targets] [level]   noclip players into a level by name: level_0, level_1, level_2, level_4, poolrooms, level_37, level_9 (default: level_0)
 * /backrooms escape [targets]          send players back to reality
 * /backrooms battery [targets] [pct]   set camcorder batteries (default: full)
 * /backrooms sanity [targets] <0-100>   set sanity (for testing the low-sanity effects)
 * /backrooms locate                    distance/direction to the nearest exit (for testing)
 * /backrooms event <blackout|flicker|alarm|intercom|lightning|twist|fog|lampwave|storm|end> [seconds]   start/stop an event on this floor
 * /backrooms spawn bacteria [count]   spawn Bacteria about 12 blocks in front of you (for testing)
 * /backrooms locate saferoom          distance/direction to the nearest safe room (for testing)
 * </pre>
 */
public final class BackroomsCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("backrooms")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("enter")
                        .executes(ctx -> enter(ctx, java.util.List.of(ctx.getSource().getPlayerOrException()), BackroomsLevel.LEVEL_0))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> enter(ctx, EntityArgument.getPlayers(ctx, "targets"), BackroomsLevel.LEVEL_0))
                                .then(Commands.argument("level", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(BackroomsLevel.values()).map(BackroomsLevel::getSerializedName), builder))
                                        .executes(ctx -> enter(ctx, EntityArgument.getPlayers(ctx, "targets"), levelArgument(ctx))))))
                .then(Commands.literal("escape")
                        .executes(ctx -> escape(java.util.List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> escape(EntityArgument.getPlayers(ctx, "targets")))))
                .then(Commands.literal("battery")
                        .executes(ctx -> battery(java.util.List.of(ctx.getSource().getPlayerOrException()), 100))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> battery(EntityArgument.getPlayers(ctx, "targets"), 100))
                                .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> battery(EntityArgument.getPlayers(ctx, "targets"), IntegerArgumentType.getInteger(ctx, "percent"))))))
                .then(Commands.literal("sanity")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                .executes(ctx -> sanity(ctx, java.util.List.of(ctx.getSource().getPlayerOrException()), IntegerArgumentType.getInteger(ctx, "value"))))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> sanity(ctx, EntityArgument.getPlayers(ctx, "targets"), IntegerArgumentType.getInteger(ctx, "value"))))))
                .then(Commands.literal("locate").executes(BackroomsCommand::locate)
                        .then(Commands.literal("saferoom").executes(BackroomsCommand::locateSafeRoom)))
                .then(Commands.literal("event")
                        .then(eventNode("blackout", LevelEventPayload.BLACKOUT, 30))
                        .then(eventNode("flicker", LevelEventPayload.FLICKER, 8))
                        .then(eventNode("alarm", LevelEventPayload.ALARM, 20))
                        .then(eventNode("intercom", LevelEventPayload.INTERCOM, 8))
                        .then(eventNode("lightning", LevelEventPayload.LIGHTNING, 2))
                        .then(eventNode("twist", LevelEventPayload.TWIST, 20))
                        .then(eventNode("fog", LevelEventPayload.FOG_ROLL, 50))
                        .then(eventNode("lampwave", LevelEventPayload.LAMP_WAVE, 25))
                        .then(eventNode("storm", LevelEventPayload.STORM, 6))
                        .then(Commands.literal("end").executes(ctx -> event(ctx, LevelEventPayload.NONE, 0))))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("bacteria")
                                .executes(ctx -> spawnBacteria(ctx, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 10))
                                        .executes(ctx -> spawnBacteria(ctx, IntegerArgumentType.getInteger(ctx, "count")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> eventNode(String name, int kind, int defaultSeconds) {
        return Commands.literal(name)
                .executes(ctx -> event(ctx, kind, defaultSeconds))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                        .executes(ctx -> event(ctx, kind, IntegerArgumentType.getInteger(ctx, "seconds"))));
    }

    private static int event(CommandContext<CommandSourceStack> ctx, int kind, int seconds) {
        ServerLevel level = ctx.getSource().getLevel();
        BackroomsLevel floor = BackroomsLevel.fromDimension(level.dimension());
        if (floor == null) {
            ctx.getSource().sendFailure(Component.literal("Run this inside the Backrooms (or with /execute in backrooms:level_N / backrooms:poolrooms)."));
            return 0;
        }
        LevelEvents.force(level, floor, kind, seconds * 20);
        return 1;
    }

    private static final SimpleCommandExceptionType UNKNOWN_LEVEL = new SimpleCommandExceptionType(
            Component.literal("Unknown level. Try level_0, level_1, level_2, level_4, poolrooms, level_37 or level_9."));

    private static BackroomsLevel levelArgument(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BackroomsLevel level = BackroomsLevel.byName(StringArgumentType.getString(ctx, "level"));
        if (level == null) {
            throw UNKNOWN_LEVEL.create();
        }
        return level;
    }

    private static int enter(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, BackroomsLevel level) {
        for (ServerPlayer player : players) {
            if (level == BackroomsLevel.LEVEL_0 && !Travel.inBackrooms(player.level())) {
                Travel.enterBackrooms(player); // the full noclip intro
            } else {
                Travel.sendTo(player, level, player.getBlockX(), player.getBlockZ());
            }
        }
        return players.size();
    }

    private static int escape(Collection<ServerPlayer> players) {
        players.forEach(Travel::escape);
        return players.size();
    }

    private static int battery(Collection<ServerPlayer> players, int percent) {
        players.forEach(player -> CameraService.setBattery(player, percent));
        return players.size();
    }

    /**
     * Spawns Bacteria about 12 blocks in front of you, on a walkable spot if this is a Backrooms
     * floor. Note: they only hunt players in survival or adventure mode.
     */
    private static int spawnBacteria(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        Vec3 ahead = player.position().add(Vec3.directionFromRotation(0.0f, player.getYRot()).scale(12.0));
        BlockPos spot = BlockPos.containing(ahead);
        BackroomsLevel floor = BackroomsLevel.fromDimension(level.dimension());
        if (floor != null) {
            BlockPos walkable = Travel.searchWalkable(floor.layout(), BackroomsSeeds.layoutSeed(level, floor), spot.getX(), spot.getZ(), 6);
            spot = walkable != null ? walkable : player.blockPosition();
        }
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            if (ModEntities.BACTERIA.get().spawn(level, spot, MobSpawnType.COMMAND) != null) {
                spawned++;
            }
        }
        int done = spawned;
        BlockPos at = spot;
        ctx.getSource().sendSuccess(() -> Component.literal("Spawned " + done + " Bacteria at " + at.toShortString()), true);
        return spawned;
    }

    private static int locate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BackroomsLevel level = BackroomsLevel.fromDimension(player.level().dimension());
        if (level == null) {
            ctx.getSource().sendFailure(Component.literal("You are not in the Backrooms."));
            return 0;
        }
        ServerLevel serverLevel = player.serverLevel();
        int[] exit = level.layout().exitPosition(BackroomsSeeds.layoutSeed(serverLevel, level), player.getBlockX(), player.getBlockZ());
        int dist = (int) Math.sqrt(player.distanceToSqr(exit[0], player.getY(), exit[1]));
        ctx.getSource().sendSuccess(() -> Component.literal("Nearest exit: " + exit[0] + ", " + exit[1] + " (" + dist + " blocks)"), false);
        return dist;
    }

    /** Searches outward in growing squares (every 2nd column, safe rooms are at least 7 wide) for the nearest safe room. */
    private static int locateSafeRoom(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BackroomsLevel level = BackroomsLevel.fromDimension(player.level().dimension());
        if (level == null) {
            ctx.getSource().sendFailure(Component.literal("You are not in the Backrooms."));
            return 0;
        }
        long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), level);
        int px = player.getBlockX();
        int pz = player.getBlockZ();
        for (int r = 0; r <= 400; r += 2) {
            for (int ox = -r; ox <= r; ox += 2) {
                for (int oz = -r; oz <= r; oz += 2) {
                    if (Math.max(Math.abs(ox), Math.abs(oz)) != r || !level.layout().isSafeRoom(seed, px + ox, pz + oz)) {
                        continue;
                    }
                    int x = px + ox;
                    int z = pz + oz;
                    int dist = (int) Math.sqrt((double) ox * ox + (double) oz * oz);
                    ctx.getSource().sendSuccess(() -> Component.literal("Nearest safe room: " + x + ", " + z + " (" + dist + " blocks)"), false);
                    return Math.max(dist, 1);
                }
            }
        }
        ctx.getSource().sendFailure(Component.literal("No safe room within 400 blocks."));
        return 0;
    }

    private BackroomsCommand() {}

    /** Sets sanity exactly (synced to the HUD right away). */
    private static int sanity(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, int value) {
        for (ServerPlayer player : players) {
            SanityService.set(player, value);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Sanity set to " + value + " for " + players.size() + " player(s)."), true);
        return players.size();
    }
}
