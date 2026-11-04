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
 *   <li>{@code /backrooms mirror lie <look|lag|walk|stop|vanish|off>}: the next of your
 *       reflections that suits it (close by, outside the final chamber and exit hallway) lies
 *       this way, as soon as its moment comes, instead of waiting one to three minutes;
 *       {@code off} ends the current lie.</li>
 *   <li>{@code /backrooms mirror info}: stand-ins drawn, real bodies hidden behind mirrors, the
 *       lie state, and what each exit mirror gets wrong (reveals the answer: testing only).</li>
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
                        .then(Commands.literal("look").executes(ctx -> send(ctx, Level228MirrorPayload.LIE_LOOK,
                                "Stand still for a few seconds near your reflections; one turned away from you will slowly look at you.")))
                        .then(Commands.literal("lag").executes(ctx -> send(ctx, Level228MirrorPayload.LIE_LAG,
                                "Walk past your reflections: the nearest one on screen will fall a little behind.")))
                        .then(Commands.literal("walk").executes(ctx -> send(ctx, Level228MirrorPayload.LIE_WALK,
                                "Walk, then stop in view of your reflection: it will keep walking a few steps.")))
                        .then(Commands.literal("stop").executes(ctx -> send(ctx, Level228MirrorPayload.LIE_STOP,
                                "Walk in view of your reflection: it will stop. Look away, look back.")))
                        .then(Commands.literal("vanish").executes(ctx -> send(ctx, Level228MirrorPayload.LIE_VANISH,
                                "One of your reflections out of view will be missing when you turn.")))
                        .then(Commands.literal("off").executes(ctx -> send(ctx, Level228MirrorPayload.LIE_OFF,
                                "Reflections honest again (until the next one)."))))
                .then(Commands.literal("info").executes(ctx -> send(ctx, Level228MirrorPayload.INFO, "Mirror report:")))
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
