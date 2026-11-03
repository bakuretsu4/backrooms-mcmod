package com.rickr.backrooms.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rickr.backrooms.network.Level7ThingPayload;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.layout.Level7Layout;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Level 7's Thing on demand, for testing (it lives on the client, client/Level7Thing):
 * <ul>
 *   <li>{@code /backrooms thing}: full dread, and it passes by you now. Swimming at the surface it
 *       glides under you (the shadow, the swell, the bubbles); with your head under water at least
 *       7 blocks down it crosses in front of you, slides by beneath you, or (deep down) over you.</li>
 *   <li>{@code /backrooms thing dread}: only full dread (groans and passes at their most frequent
 *       and closest from now on, until you leave the water for a while).</li>
 * </ul>
 */
final class Level7ThingCommand {
    private Level7ThingCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("thing")
                .executes(ctx -> send(ctx, Level7ThingPayload.PASS, "Something vast turns toward you."))
                .then(Commands.literal("dread").executes(ctx -> send(ctx, Level7ThingPayload.DREAD, "It knows you are here.")));
    }

    private static int send(CommandContext<CommandSourceStack> ctx, int kind, String message) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_7) {
            ctx.getSource().sendFailure(Component.literal("Run this in Level 7."));
            return 0;
        }
        if (!player.isInWater() || player.getY() >= Level7Layout.CEILING_BOTTOM_Y) {
            ctx.getSource().sendFailure(Component.literal("Get in the sea first (drop through the hatch)."));
            return 0;
        }
        PacketDistributor.sendToPlayer(player, new Level7ThingPayload(kind));
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}
