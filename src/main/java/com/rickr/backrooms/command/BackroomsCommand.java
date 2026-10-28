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
import com.rickr.backrooms.entity.Smiler;
import com.rickr.backrooms.entity.SmilerSpawner;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.LevelEvents;
import com.rickr.backrooms.world.FixtureOutages;
import com.rickr.backrooms.world.Travel;
import com.rickr.backrooms.world.RealityDrift;
import org.jetbrains.annotations.Nullable;
import com.rickr.backrooms.world.anomaly.AnomalyService;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level0.Level0LocalEvents;
import com.rickr.backrooms.world.layout.Level0AnomalyPlan;
import com.rickr.backrooms.world.layout.Level0Layout;
import com.rickr.backrooms.world.Level94Clock;
import com.rickr.backrooms.world.Level94Sync;
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
 * /backrooms enter [targets] [level]   noclip players into a level by name: level_0, level_1, level_2, level_4, poolrooms, level_37, level_9, level_94, level_7, level_39, level_490, level_227 (default: level_0)
 * /backrooms escape [targets]          send players back to reality
 * /backrooms battery [targets] [pct]   set camcorder batteries (default: full)
 * /backrooms sanity [targets] <0-100>   set sanity (for testing the low-sanity effects)
 * /backrooms locate                    distance/direction to the nearest exit (for testing)
 * /backrooms event <blackout|flicker|alarm|intercom|lightning|twist|fog|lampwave|storm|water|end> [seconds]   start/stop an event on this floor
 * /backrooms drift <player> [sealed|door|lights|end]   separate realities: that player sees the room differently from the nearest friend (end = snap back)
 * /backrooms spawn bacteria [count]   spawn Bacteria about 12 blocks in front of you (for testing)
 * /backrooms spawn smiler [count|bold]  spawn Smilers about 16 blocks in front of you (start a blackout first)
 * /backrooms locate saferoom          distance/direction to the nearest safe room (for testing)
 * /backrooms time94 <day|dusk|night|dawn>   Level 94: jump its day/night clock to the start of that phase (for testing)
 * /backrooms anomaly locate <kind>     where the nearest anomalous area of that kind is on this floor (for testing)
 * /backrooms anomaly here              which anomalous area you are standing in
 * /backrooms anomaly visit <kind> <point>   Level 0: jump to a named spot of the nearest site of that kind (for testing)
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
                // Anomalous areas: where is the nearest one of a kind / what am I standing in?
                .then(Commands.literal("anomaly")
                        .then(Commands.literal("here").executes(BackroomsCommand::anomalyHere))
                        .then(Commands.literal("locate")
                                .then(Commands.argument("kind", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> {
                                            BackroomsLevel floor = BackroomsLevel.fromDimension(ctx.getSource().getLevel().dimension());
                                            return SharedSuggestionProvider.suggest(floor == null ? java.util.List.of() : floor.layout().anomalyKinds(), builder);
                                        })
                                        .executes(ctx -> anomalyLocate(ctx, StringArgumentType.getString(ctx, "kind")))))
                        // Testing: teleport to a named spot (the atrium's ladder, the Front Office's rug...) of the
                        // nearest site of a kind. The kind is "level_0/..." (a resource-location-like argument,
                        // since plain words can't hold the slash).
                        .then(Commands.literal("visit")
                                .then(Commands.argument("kind", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                BackroomsLevel.LEVEL_0.layout().anomalyKinds().stream()
                                                        .filter(k -> !Level0AnomalyPlan.visitPoints(k).isEmpty()), builder))
                                        .then(Commands.argument("point", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Level0AnomalyPlan.visitPoints(
                                                        net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "kind").getPath()), builder))
                                                .executes(ctx -> anomalyVisit(ctx,
                                                        net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "kind").getPath(),
                                                        StringArgumentType.getString(ctx, "point")))))))
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
                        .then(eventNode("water", LevelEventPayload.OBTRUSIVE_WATER, 30))
                        // Level 39: rain from the superstructure's cloud ceiling, and a boom whose echoes build into a quake.
                        .then(eventNode("rain", LevelEventPayload.INDOOR_RAIN, 180))
                        .then(eventNode("quake", LevelEventPayload.ECHO_QUAKE, 12))
                        .then(Commands.literal("end").executes(ctx -> event(ctx, LevelEventPayload.NONE, 0))))
                // Level 0 local power outages around you (world/FixtureOutages), for testing.
                .then(Commands.literal("outage")
                        .then(outageNode("cut", FixtureOutages.Pattern.CUT))
                        .then(outageNode("stutter", FixtureOutages.Pattern.DIE_STUTTER))
                        .then(outageNode("wave", FixtureOutages.Pattern.WAVE))
                        .then(Commands.literal("end").executes(BackroomsCommand::outageEnd)))
                // Level 0 local light events (world/anomaly/level0/Level0LocalEvents): let one happen
                // to you at the next right moment, skipping the 4-8 minute wait. For testing.
                .then(Commands.literal("l0event")
                        .then(Commands.literal("room").executes(ctx -> l0Event(ctx, Level0LocalEvents.Kind.ROOM)))
                        .then(Commands.literal("corridor").executes(ctx -> l0Event(ctx, Level0LocalEvents.Kind.CORRIDOR)))
                        .then(Commands.literal("district").executes(ctx -> l0Event(ctx, Level0LocalEvents.Kind.DISTRICT))))
                // Separate realities: force one for a player (with the nearest other player, or alone), or end it.
                .then(Commands.literal("drift")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> drift(ctx, null))
                                .then(Commands.literal("sealed").executes(ctx -> drift(ctx, RealityDrift.Variant.SEALED)))
                                .then(Commands.literal("door").executes(ctx -> drift(ctx, RealityDrift.Variant.DOOR)))
                                .then(Commands.literal("lights").executes(ctx -> drift(ctx, RealityDrift.Variant.LIGHTS)))
                                .then(Commands.literal("end").executes(ctx -> report(ctx,
                                        RealityDrift.endByCommand(EntityArgument.getPlayer(ctx, "player")))))))
                // Level 94 (Sky slice): jump the day/night clock to the start of a phase.
                .then(Commands.literal("time94")
                        .then(Commands.literal("day").executes(ctx -> time94(ctx, 0)))
                        .then(Commands.literal("dusk").executes(ctx -> time94(ctx, Level94Clock.DAY_TICKS - Level94Clock.DUSK_TICKS)))
                        .then(Commands.literal("night").executes(ctx -> time94(ctx, Level94Clock.DAY_TICKS)))
                        .then(Commands.literal("dawn").executes(ctx -> time94(ctx, Level94Clock.CYCLE - Level94Clock.DAWN_TICKS))))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("bacteria")
                                .executes(ctx -> spawnBacteria(ctx, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 10))
                                        .executes(ctx -> spawnBacteria(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
                        .then(Commands.literal("smiler")
                                .executes(ctx -> spawnSmilers(ctx, 1, false))
                                .then(Commands.literal("bold").executes(ctx -> spawnSmilers(ctx, 1, true)))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 10))
                                        .executes(ctx -> spawnSmilers(ctx, IntegerArgumentType.getInteger(ctx, "count"), false))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> eventNode(String name, int kind, int defaultSeconds) {
        return Commands.literal(name)
                .executes(ctx -> event(ctx, kind, defaultSeconds))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                        .executes(ctx -> event(ctx, kind, IntegerArgumentType.getInteger(ctx, "seconds"))));
    }

    /** The last outage started by the command, for {@code /backrooms outage end}. */
    private static int lastOutage;

    private static LiteralArgumentBuilder<CommandSourceStack> outageNode(String name, FixtureOutages.Pattern pattern) {
        return Commands.literal(name)
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 200))
                        .executes(ctx -> outage(ctx, pattern, IntegerArgumentType.getInteger(ctx, "radius"), 0))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                                .executes(ctx -> outage(ctx, pattern, IntegerArgumentType.getInteger(ctx, "radius"),
                                        IntegerArgumentType.getInteger(ctx, "seconds")))));
    }

    /** Cuts the power in a circle around you (no seconds = until {@code /backrooms outage end}). */
    private static int outage(CommandContext<CommandSourceStack> ctx, FixtureOutages.Pattern pattern, int radius, int seconds) {
        lastOutage = FixtureOutages.start(ctx.getSource().getLevel(), BlockPos.containing(ctx.getSource().getPosition()),
                radius, seconds * 1000, pattern);
        ctx.getSource().sendSuccess(() -> Component.literal("Outage " + lastOutage + " started"), false);
        return 1;
    }

    private static int outageEnd(CommandContext<CommandSourceStack> ctx) {
        FixtureOutages.end(ctx.getSource().getLevel(), lastOutage);
        return 1;
    }

    private static int l0Event(CommandContext<CommandSourceStack> ctx, Level0LocalEvents.Kind kind) throws CommandSyntaxException {
        String hint = Level0LocalEvents.force(ctx.getSource().getPlayerOrException(), kind);
        ctx.getSource().sendSuccess(() -> Component.literal(hint), false);
        return 1;
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

    private static int drift(CommandContext<CommandSourceStack> ctx, @Nullable RealityDrift.Variant variant) throws CommandSyntaxException {
        return report(ctx, RealityDrift.force(EntityArgument.getPlayer(ctx, "player"), variant));
    }

    /** Shows a drift command's answer as success (grey) or failure (red). */
    private static int report(CommandContext<CommandSourceStack> ctx, RealityDrift.CommandResult result) {
        if (!result.success()) {
            ctx.getSource().sendFailure(Component.literal(result.message()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    private static int anomalyHere(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AnomalySite site = AnomalyService.siteAt(player.serverLevel(), player.getBlockX(), player.getBlockZ());
        ctx.getSource().sendSuccess(() -> Component.literal(site == null ? "No anomaly here." : "In " + site.kind()
                + " (" + site.minX() + "," + site.minZ() + " to " + site.maxX() + "," + site.maxZ() + ")"), false);
        return site == null ? 0 : 1;
    }

    private static int anomalyLocate(CommandContext<CommandSourceStack> ctx, String kind) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BackroomsLevel floor = BackroomsLevel.fromDimension(level.dimension());
        if (floor == null) {
            ctx.getSource().sendFailure(Component.literal("Run this inside the Backrooms."));
            return 0;
        }
        AnomalySite site = floor.layout().nearestAnomaly(BackroomsSeeds.layoutSeed(level, floor), player.getBlockX(), player.getBlockZ(), kind);
        if (site == null) {
            ctx.getSource().sendFailure(Component.literal("No " + kind + " found nearby. Kinds here: " + floor.layout().anomalyKinds()));
            return 0;
        }
        int dist = (int) site.distanceTo(player.getX(), player.getZ());
        ctx.getSource().sendSuccess(() -> Component.literal(site.kind() + " at " + site.anchorX() + ", " + site.anchorZ()
                + " (covers " + site.minX() + "," + site.minZ() + " to " + site.maxX() + "," + site.maxZ() + "), " + dist + " blocks away"), false);
        return 1;
    }

    /** Teleports the player to a named spot of the nearest Level 0 site of a kind (see Level0AnomalyPlan#visitPoint). */
    private static int anomalyVisit(CommandContext<CommandSourceStack> ctx, String kind, String point) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        if (BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_0) {
            ctx.getSource().sendFailure(Component.literal("Run this on Level 0."));
            return 0;
        }
        Level0Layout layout = (Level0Layout) BackroomsLevel.LEVEL_0.layout();
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_0);
        AnomalySite site = layout.nearestAnomaly(seed, player.getBlockX(), player.getBlockZ(), kind);
        if (site == null) {
            ctx.getSource().sendFailure(Component.literal("No " + kind + " found nearby."));
            return 0;
        }
        double[] spot = Level0AnomalyPlan.visitPoint(layout, seed, site, point);
        if (spot == null) {
            ctx.getSource().sendFailure(Component.literal("No point '" + point + "' for " + kind + ". Points: " + Level0AnomalyPlan.visitPoints(kind)));
            return 0;
        }
        float pitch = spot.length > 4 ? (float) spot[4] : 0.0f;
        player.teleportTo(level, spot[0], spot[1], spot[2], (float) spot[3], pitch);
        player.resetFallDistance();
        ctx.getSource().sendSuccess(() -> Component.literal("Visiting " + kind + " / " + point + " at "
                + (int) Math.floor(spot[0]) + ", " + (int) Math.floor(spot[1]) + ", " + (int) Math.floor(spot[2])), false);
        return 1;
    }

    private static final SimpleCommandExceptionType UNKNOWN_LEVEL = new SimpleCommandExceptionType(
            Component.literal("Unknown level. Try level_0, level_1, level_2, level_4, poolrooms, level_37, level_9, level_94, level_7 or level_39."));

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
                Travel.sendTo(player, level, player.getBlockX(), player.getBlockZ(), Travel.arrivalMessage(level));
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
        BlockPos spot = spotAhead(player, 12.0);
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

    /**
     * Spawns Smilers about 16 blocks in front of you. They behave like blackout ones, except that
     * they don't need a blackout: they fade after 90 s, or burn away at once in a lit spot. For a
     * proper test start a blackout first: {@code /backrooms event blackout 60}.
     */
    private static int spawnSmilers(CommandContext<CommandSourceStack> ctx, int count, boolean bold) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos spot = spotAhead(player, 16.0);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            if (SmilerSpawner.spawn(player.serverLevel(), spot, Smiler.Origin.COMMAND, bold) != null) {
                spawned++;
            }
        }
        int done = spawned;
        ctx.getSource().sendSuccess(() -> Component.literal("Spawned " + done + (bold ? " bold" : "") + " Smiler(s) at " + spot.toShortString()), true);
        return spawned;
    }

    /** A walkable spot about {@code distance} blocks in front of the player (their feet if none). */
    private static BlockPos spotAhead(ServerPlayer player, double distance) {
        ServerLevel level = player.serverLevel();
        Vec3 ahead = player.position().add(Vec3.directionFromRotation(0.0f, player.getYRot()).scale(distance));
        BlockPos spot = BlockPos.containing(ahead);
        BackroomsLevel floor = BackroomsLevel.fromDimension(level.dimension());
        if (floor != null) {
            BlockPos walkable = Travel.searchWalkable(floor.layout(), BackroomsSeeds.layoutSeed(level, floor), spot.getX(), spot.getZ(), 6);
            spot = walkable != null ? walkable : player.blockPosition();
        }
        return spot;
    }

    private static int locate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BackroomsLevel level = BackroomsLevel.fromDimension(player.level().dimension());
        if (level == null) {
            ctx.getSource().sendFailure(Component.literal("You are not in the Backrooms."));
            return 0;
        }
        if (!level.layout().hasExits()) {
            // Level 227: no door, no stairwell. The tree drifts, and sooner or later you noclip out.
            ctx.getSource().sendSuccess(() -> Component.literal("This level has no exit. The tree is always moving: sooner or later it noclips you to another level."), false);
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

    // ---- Level 94 (Sky slice) ------------------------------------------------------------------

    /**
     * {@code /backrooms time94 <phase>}: moves Level 94's clock so that it is now {@code cycleTick}
     * ticks into the day (0 = morning), and tells every client there at once.
     */
    private static int time94(CommandContext<CommandSourceStack> ctx, long cycleTick) {
        ServerLevel level = ctx.getSource().getServer().getLevel(BackroomsLevel.LEVEL_94.dimension());
        if (level == null) {
            ctx.getSource().sendFailure(Component.literal("Level 94 isn't loaded."));
            return 0;
        }
        Level94Clock.setStart(level, level.getGameTime() - cycleTick);
        Level94Sync.sendToAll(level);
        ctx.getSource().sendSuccess(() -> Component.literal("Level 94 is now at: " + Level94Clock.phase(level)), true);
        return 1;
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
