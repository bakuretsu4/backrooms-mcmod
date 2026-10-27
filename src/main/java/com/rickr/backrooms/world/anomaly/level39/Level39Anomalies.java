package com.rickr.backrooms.world.anomaly.level39;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.anomaly.Anomalies;
import com.rickr.backrooms.world.layout.Level39AnomalyPlan;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Server behaviours of Level 39's anomalies (shapes in {@link Level39Sites}, where they are in
 * {@code world/layout/Level39AnomalyPlan}). Kinds without a behaviour here (the inverted, merged,
 * cut, floating, odd-furniture and flooded houses) are all in their blocks, built by
 * {@code Level39Houses}; the screaming houses are a pure hallucination played by the client.
 * Hydrolitis ({@link Hydrolitis}), the echoes ({@link Echoes}) and the M.E.G. camp's stash
 * ({@link MegCamp}) run from their own events.
 *
 * <p>Test commands: {@code /backrooms window wake} wakes the nearest facade window right now;
 * {@code /backrooms hole stir} sets the nearest hole making noises (a Hound follows);
 * {@code /backrooms gondola ride} (in Level 9, within 32 blocks of a lift station) boards its chair.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level39Anomalies {
    private static final HostileWindows WINDOWS = new HostileWindows();
    private static final HoleSounds HOLES = new HoleSounds();

    public static void register() {
        Anomalies.register(Level39Sites.HOSTILE_WINDOWS, WINDOWS);
        Anomalies.register(Level39Sites.HOLE_SOUNDS, HOLES);
        Anomalies.register(Level39Sites.VANISHING_DOOR, new VanishingDoors());
        Anomalies.register(Level39Sites.BIGGER_INSIDE, new BiggerInside());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(command());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("backrooms")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("window").then(Commands.literal("wake").executes(Level39Anomalies::wakeWindow)))
                .then(Commands.literal("hole").then(Commands.literal("stir").executes(Level39Anomalies::stirHole)))
                .then(Commands.literal("gondola").then(Commands.literal("ride").executes(Level39Anomalies::rideGondola)));
    }

    /** Boards the nearest Level 9 lift station's chair (within 32 blocks), as a right-click would. */
    private static int rideGondola(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Component problem = com.rickr.backrooms.world.anomaly.level39.gondola.GondolaDebug.ride(player);
        if (problem != null) {
            ctx.getSource().sendFailure(problem);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Boarded the chairlift. Six seconds to the whiteout."), false);
        return 1;
    }

    private static int wakeWindow(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        if (BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_39) {
            ctx.getSource().sendFailure(Component.literal("Run this in Level 39, near the superstructure."));
            return 0;
        }
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_39);
        var site = Level39AnomalyPlan.nearest(seed, player.getBlockX(), player.getBlockZ(), Level39Sites.HOSTILE_WINDOWS);
        if (site == null || site.distanceTo(player.getX(), player.getZ()) > 2.0) {
            ctx.getSource().sendFailure(Component.literal("Stand within ~10 blocks of an infested stretch of the facade "
                    + "(/backrooms anomaly locate hostile_windows)."));
            return 0;
        }
        int woken = HostileWindows.wakeAround(level, site, seed, player, true);
        ctx.getSource().sendSuccess(() -> Component.literal(woken > 0 ? "A window wakes." : "No lit window within 10 blocks."), false);
        return woken;
    }

    private static int stirHole(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        if (BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_39) {
            ctx.getSource().sendFailure(Component.literal("Run this in Level 39, near a hole."));
            return 0;
        }
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_39);
        var site = Level39AnomalyPlan.nearest(seed, player.getBlockX(), player.getBlockZ(), Level39Sites.HOLE_SOUNDS);
        if (site == null || site.distanceTo(player.getX(), player.getZ()) > 48.0) {
            ctx.getSource().sendFailure(Component.literal("No hole within 48 blocks (/backrooms anomaly locate hole_sounds)."));
            return 0;
        }
        HOLES.start(level, site);
        ctx.getSource().sendSuccess(() -> Component.literal("The hole at " + site.anchorX() + ", " + site.anchorZ()
                + " starts making noises. Run."), false);
        return 1;
    }

    private Level39Anomalies() {}
}
