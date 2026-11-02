package com.rickr.backrooms.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.layout.Level9Houses;
import com.rickr.backrooms.world.layout.Level9Layout;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /backrooms house9 <kind>} (Level 9, for testing): teleports you onto the front path of
 * the nearest house of that plan (classic, ranch, attic, basement, boarded, half_built,
 * upside_down, repeated), or the nearest safe house, clay door or long hall, looking at its
 * front door. Plans that can be locked only count houses whose door is open.
 */
final class Level9HouseCommand {
    private Level9HouseCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("house9")
                .then(Commands.argument("kind", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Level9Houses.KINDS, builder))
                        .executes(ctx -> go(ctx, StringArgumentType.getString(ctx, "kind"))));
    }

    private static int go(CommandContext<CommandSourceStack> ctx, String kind) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        if (BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_9
                || !(BackroomsLevel.LEVEL_9.layout() instanceof Level9Layout layout)) {
            ctx.getSource().sendFailure(Component.literal("Run this on Level 9."));
            return 0;
        }
        if (!Level9Houses.KINDS.contains(kind)) {
            ctx.getSource().sendFailure(Component.literal("Unknown house kind. Kinds: " + Level9Houses.KINDS));
            return 0;
        }
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_9);
        int[] spot = Level9Houses.locate(layout, seed, player.getBlockX(), player.getBlockZ(), kind);
        if (spot == null) {
            ctx.getSource().sendFailure(Component.literal("No " + kind + " house within " + Level9Houses.LOCATE_RADIUS
                    + " blocks. Walk on a bit and try again."));
            return 0;
        }
        int dist = (int) Math.hypot(spot[0] - player.getX(), spot[1] - player.getZ());
        // The front path is a concrete block at y41, so you stand at y42.
        player.teleportTo(level, spot[0] + 0.5, 42.0, spot[1] + 0.5, spot[2], 0.0f);
        player.resetFallDistance();
        ctx.getSource().sendSuccess(() -> Component.literal("Nearest " + kind + " house: front path at "
                + spot[0] + ", 42, " + spot[1] + " (" + dist + " blocks from where you were)"), false);
        return 1;
    }
}
