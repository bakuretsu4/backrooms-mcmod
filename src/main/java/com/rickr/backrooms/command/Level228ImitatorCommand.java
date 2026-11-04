package com.rickr.backrooms.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Level228Imitator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Level 228's imitator on demand, for testing (world/Level228Imitator):
 * <ul>
 *   <li>{@code /backrooms imitator show}: an episode starts now with you as its watcher (any game
 *       mode, no cooldown): it stands behind you; find it in the mirrors, wearing your face or a
 *       teammate's, copying you, then copying wrong, then watching, then gone.</li>
 *   <li>{@code /backrooms imitator off}: every episode ends and none start until {@code show}.</li>
 * </ul>
 */
final class Level228ImitatorCommand {
    private Level228ImitatorCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("imitator")
                .then(Commands.literal("show").executes(Level228ImitatorCommand::show))
                .then(Commands.literal("off").executes(Level228ImitatorCommand::off));
    }

    private static ServerPlayer here(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_228) {
            ctx.getSource().sendFailure(Component.literal("Run this in Level 228."));
            return null;
        }
        return player;
    }

    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = here(ctx);
        if (player == null) {
            return 0;
        }
        if (!Level228Imitator.allowedFor(player)) {
            ctx.getSource().sendFailure(Component.literal("It never comes into the exit hallway or the final chamber."));
            return 0;
        }
        if (!Level228Imitator.debugShow(player)) {
            ctx.getSource().sendFailure(Component.literal("No open floor behind you for it; walk a few steps and try again."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "The imitator is behind you. Look into the mirrors (it is not in the room)."), false);
        return 1;
    }

    private static int off(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = here(ctx);
        if (player == null) {
            return 0;
        }
        Level228Imitator.debugOff(player);
        ctx.getSource().sendSuccess(() -> Component.literal("The imitator is gone (until /backrooms imitator show)."), false);
        return 1;
    }
}
