package com.rickr.backrooms.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.rickr.backrooms.network.Level9StreetPayload;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Level 9 street effects on demand, for testing (they are client-side, so the server just tells
 * every player on Level 9 to start one now):
 * <ul>
 *   <li>{@code /backrooms event lampsbehind}: the next streetlamps you walk past start going out
 *       behind your back, one every 2-3.5 s (up to five), and come back once you are far away or
 *       have looked back at them for a while (client/level9/LampsBehind).</li>
 *   <li>{@code /backrooms event dogs}: 2-4 dogs start barking far off, stop dead together 3.5-6.5 s
 *       later, and the night goes silent for 3-5.5 s (client/level9/DogsGoQuiet).</li>
 * </ul>
 */
final class Level9StreetCommand {
    private Level9StreetCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> lampsBehind() {
        return Commands.literal("lampsbehind").executes(ctx -> send(ctx, Level9StreetPayload.LAMPS_BEHIND, "Streetlamps behind you will start going out."));
    }

    static LiteralArgumentBuilder<CommandSourceStack> dogs() {
        return Commands.literal("dogs").executes(ctx -> send(ctx, Level9StreetPayload.DOGS, "Dogs start barking somewhere..."));
    }

    private static int send(CommandContext<CommandSourceStack> ctx, int kind, String message) {
        ServerLevel level = ctx.getSource().getLevel();
        if (BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_9) {
            ctx.getSource().sendFailure(Component.literal("Run this on Level 9."));
            return 0;
        }
        Level9StreetPayload payload = new Level9StreetPayload(kind);
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}
