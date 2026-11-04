package com.rickr.backrooms.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rickr.backrooms.network.Level228MirrorPayload;
import com.rickr.backrooms.world.BackroomsLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Level 228's reflections on demand, for testing (they live on the client, client/level228):
 * <ul>
 *   <li>{@code /backrooms mirror lie <lag|look|walk|still>}: the next reflection of you that is on
 *       screen and within 10 blocks lies this way (instead of waiting one to three minutes).</li>
 *   <li>{@code /backrooms mirror stage <entities|block_entities|solid_blocks>}: which render
 *       stage the reflections are drawn in, should a shader pack misbehave in the default one
 *       (entities).</li>
 * </ul>
 */
final class Level228MirrorCommand {
    private Level228MirrorCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("mirror")
                .then(Commands.literal("lie")
                        .then(Commands.literal("lag").executes(ctx -> send(ctx, Level228MirrorPayload.LIE_LAG,
                                "Your next reflection close by will be a little late.")))
                        .then(Commands.literal("look").executes(ctx -> send(ctx, Level228MirrorPayload.LIE_LOOK,
                                "Your next reflection close by will keep watching you. Look away, then straight at it.")))
                        .then(Commands.literal("walk").executes(ctx -> send(ctx, Level228MirrorPayload.LIE_WALK,
                                "Your next reflection close by will walk off.")))
                        .then(Commands.literal("still").executes(ctx -> send(ctx, Level228MirrorPayload.LIE_STILL,
                                "Your next reflection close by will stay put. Walk off, look away, look back."))))
                .then(Commands.literal("stage")
                        .then(Commands.literal("entities").executes(ctx -> send(ctx, Level228MirrorPayload.STAGE_ENTITIES,
                                "Reflections drawn after entities (default).")))
                        .then(Commands.literal("block_entities").executes(ctx -> send(ctx, Level228MirrorPayload.STAGE_BLOCK_ENTITIES,
                                "Reflections drawn after block entities.")))
                        .then(Commands.literal("solid_blocks").executes(ctx -> send(ctx, Level228MirrorPayload.STAGE_SOLID_BLOCKS,
                                "Reflections drawn after solid blocks."))));
    }

    private static int send(CommandContext<CommandSourceStack> ctx, int kind, String message) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_228) {
            ctx.getSource().sendFailure(Component.literal("Run this in Level 228."));
            return 0;
        }
        PacketDistributor.sendToPlayer(player, new Level228MirrorPayload(kind));
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}
