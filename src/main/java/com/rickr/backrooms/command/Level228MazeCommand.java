package com.rickr.backrooms.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.Level228Rules;
import com.rickr.backrooms.world.layout.Level228Plan;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Level 228's sights on demand, for checking them in game (world/layout/Level228Plan):
 * <ul>
 *   <li>{@code /backrooms maze <foyer|crystal|alhambra|funhouse|glass|chamber|exithall|backstage>}:
 *       jump to the start of that zone (a room's entrance cell, facing in);</li>
 *   <li>{@code /backrooms maze shift}: force a shift now (never closes a pane on anyone);</li>
 *   <li>{@code /backrooms maze answer}: which chamber mirror is the accurate one, and each
 *       wrong one's flaw.</li>
 * </ul>
 */
final class Level228MazeCommand {
    private static final String[] FLAWS = {"0.5 s late", "item in the wrong hand", "a worn piece missing",
            "head nods out of time", "not mirrored", "its candle is out", "the ticket stub is missing"};

    private Level228MazeCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("maze");
        String[] names = {"foyer", "crystal", "alhambra", "funhouse", "glass", "chamber", "exithall", "backstage"};
        Level228Plan.Zone[] zones = {Level228Plan.Zone.FOYER, Level228Plan.Zone.CRYSTAL, Level228Plan.Zone.ALHAMBRA,
                Level228Plan.Zone.FUNHOUSE, Level228Plan.Zone.GLASS, Level228Plan.Zone.CHAMBER, Level228Plan.Zone.EXIT_HALL,
                Level228Plan.Zone.BACKSTAGE};
        for (int i = 0; i < names.length; i++) {
            Level228Plan.Zone zone = zones[i];
            String name = names[i];
            node.then(Commands.literal(name).executes(ctx -> jump(ctx, name, zone)));
        }
        return node.then(Commands.literal("shift").executes(Level228MazeCommand::shift))
                .then(Commands.literal("answer").executes(Level228MazeCommand::answer));
    }

    private static ServerPlayer inLevel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_228) {
            ctx.getSource().sendFailure(Component.literal("Run this in Level 228."));
            return null;
        }
        return player;
    }

    private static int jump(CommandContext<CommandSourceStack> ctx, String name, Level228Plan.Zone zone) throws CommandSyntaxException {
        ServerPlayer player = inLevel(ctx);
        if (player == null) {
            return 0;
        }
        long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), BackroomsLevel.LEVEL_228);
        double[] at = Level228Plan.zoneStart(seed, zone);
        player.teleportTo(player.serverLevel(), at[0], at[1], at[2], (float) at[3], 0f);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Level 228 %s at %.1f %.0f %.1f.", name, at[0], at[1], at[2])), false);
        return 1;
    }

    private static int shift(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = inLevel(ctx);
        if (player == null) {
            return 0;
        }
        int bit = Level228Rules.tryShift(player.serverLevel(), true);
        if (bit < 0) {
            ctx.getSource().sendFailure(Component.literal("Nothing can shift: someone stands at every shifting panel."));
            return 0;
        }
        String[] rooms = {"Crystal Maze", "Alhambra Hall", "Funhouse", "Glass Labyrinth"};
        boolean on = Level228Rules.shift(player.serverLevel()).on(bit);
        ctx.getSource().sendSuccess(() -> Component.literal("Shifted the " + rooms[bit / 2] + "'s class " + bit % 2
                + (on ? " (shifted)" : " (back)") + "."), false);
        return 1;
    }

    private static int answer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = inLevel(ctx);
        if (player == null) {
            return 0;
        }
        long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), BackroomsLevel.LEVEL_228);
        StringBuilder sb = new StringBuilder("Final chamber (mirrors A, B, C from north to south): ");
        for (int m = 0; m < 3; m++) {
            int flaw = Level228Plan.exitFlaw(seed, m);
            sb.append((char) ('A' + m)).append(flaw < 0 ? " ACCURATE" : " wrong (" + FLAWS[flaw] + ")").append(m < 2 ? ", " : ".");
        }
        String text = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return Level228Plan.correctExitMirror(seed) + 1;
    }
}
