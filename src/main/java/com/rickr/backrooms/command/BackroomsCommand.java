package com.rickr.backrooms.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rickr.backrooms.camera.CameraService;
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
 * /backrooms enter [targets] [level]   noclip players into a level: 0-2, or 3 = the Poolrooms (default: Level 0)
 * /backrooms escape [targets]          send players back to reality
 * /backrooms battery [targets] [pct]   set camcorder batteries (default: full)
 * /backrooms locate                    distance/direction to the nearest exit (for testing)
 * /backrooms event <blackout|flicker|alarm|intercom|lightning|twist|end> [seconds]   start/stop an event on this floor
 * /backrooms spawn bacteria [count]   spawn Bacteria about 12 blocks in front of you (for testing)
 * </pre>
 */
public final class BackroomsCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("backrooms")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("enter")
                        .executes(ctx -> enter(ctx, java.util.List.of(ctx.getSource().getPlayerOrException()), 0))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> enter(ctx, EntityArgument.getPlayers(ctx, "targets"), 0))
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, BackroomsLevel.values().length - 1))
                                        .executes(ctx -> enter(ctx, EntityArgument.getPlayers(ctx, "targets"), IntegerArgumentType.getInteger(ctx, "level"))))))
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
                .then(Commands.literal("locate").executes(BackroomsCommand::locate))
                .then(Commands.literal("event")
                        .then(eventNode("blackout", LevelEventPayload.BLACKOUT, 30))
                        .then(eventNode("flicker", LevelEventPayload.FLICKER, 8))
                        .then(eventNode("alarm", LevelEventPayload.ALARM, 20))
                        .then(eventNode("intercom", LevelEventPayload.INTERCOM, 8))
                        .then(eventNode("lightning", LevelEventPayload.LIGHTNING, 2))
                        .then(eventNode("twist", LevelEventPayload.TWIST, 20))
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

    private static int enter(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, int levelNumber) {
        BackroomsLevel level = BackroomsLevel.values()[levelNumber];
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
        int[] exit = level.layout().exits().nearest(BackroomsSeeds.layoutSeed(serverLevel, level), player.getBlockX(), player.getBlockZ());
        int dist = (int) Math.sqrt(player.distanceToSqr(exit[0], player.getY(), exit[1]));
        ctx.getSource().sendSuccess(() -> Component.literal("Nearest exit: " + exit[0] + ", " + exit[1] + " (" + dist + " blocks)"), false);
        return dist;
    }

    private BackroomsCommand() {}
}
