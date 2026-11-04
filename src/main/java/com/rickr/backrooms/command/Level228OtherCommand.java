package com.rickr.backrooms.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Level228Hunt;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Level 228's Other on demand, for testing (world/Level228Hunt):
 * <ul>
 *   <li>{@code /backrooms other near}: it enters the maze 9-15 blocks from you, hunting you now
 *       (no first-appearance delay). Look into the mirrors around you to find it.</li>
 *   <li>{@code /backrooms other far}: the same, 64-96 blocks off (how it normally arrives).</li>
 *   <li>{@code /backrooms other catch}: it has you: the silence, the crack, the white-out and the
 *       move deeper into the maze.</li>
 *   <li>{@code /backrooms other off}: gone, and it stays gone until one of the above.</li>
 * </ul>
 */
final class Level228OtherCommand {
    private Level228OtherCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("other")
                .then(Commands.literal("near").executes(ctx -> enter(ctx, true)))
                .then(Commands.literal("far").executes(ctx -> enter(ctx, false)))
                .then(Commands.literal("catch").executes(Level228OtherCommand::grab))
                .then(Commands.literal("off").executes(Level228OtherCommand::off));
    }

    private static ServerPlayer here(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return BackroomsLevel.fromDimension(player.level().dimension()) == BackroomsLevel.LEVEL_228 ? player : null;
    }

    private static int enter(CommandContext<CommandSourceStack> ctx, boolean near) throws CommandSyntaxException {
        ServerPlayer player = here(ctx);
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Run this in Level 228."));
            return 0;
        }
        if (!Level228Hunt.debugEnter(player, near)) {
            ctx.getSource().sendFailure(Component.literal("No reachable corridor found for it there; try again or move."));
            return 0;
        }
        double d = Level228Hunt.distanceTo(player);
        String what = player.isCreative() ? " (it ignores creative players: switch to survival)" : "";
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "The Other is in the mirrors, %.0f blocks away%s.", d, what)), false);
        return 1;
    }

    private static int grab(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = here(ctx);
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Run this in Level 228."));
            return 0;
        }
        Level228Hunt.debugCatch(player);
        ctx.getSource().sendSuccess(() -> Component.literal("It has you."), false);
        return 1;
    }

    private static int off(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = here(ctx);
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Run this in Level 228."));
            return 0;
        }
        Level228Hunt.debugOff(player);
        ctx.getSource().sendSuccess(() -> Component.literal("The Other is gone (until /backrooms other near|far|catch)."), false);
        return 1;
    }
}
