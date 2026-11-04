package com.rickr.backrooms.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.layout.Level228Plan;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Level 228's sights on demand, for checking them in game (world/layout/Level228Plan). Each jumps
 * you to the nearest one:
 * <ul>
 *   <li>{@code /backrooms maze facing}: the middle of a corridor between two facing mirrors, at
 *       least three images deep on both sides (look sideways into the glass);</li>
 *   <li>{@code /backrooms maze branch}: the junction in front of a false branch (the corridor
 *       that seems to run on is a mirror);</li>
 *   <li>{@code /backrooms maze exit}: in front of the black exit mirror (walk into it to leave).</li>
 * </ul>
 */
final class Level228MazeCommand {
    /** How many cells (18 blocks) around you the facing mirrors and false branches are looked for. */
    private static final int RINGS = 24;

    private Level228MazeCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("maze")
                .then(Commands.literal("facing").executes(ctx -> jump(ctx, "facing")))
                .then(Commands.literal("branch").executes(ctx -> jump(ctx, "branch")))
                .then(Commands.literal("exit").executes(ctx -> jump(ctx, "exit")));
    }

    private static int jump(CommandContext<CommandSourceStack> ctx, String what) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_228) {
            ctx.getSource().sendFailure(Component.literal("Run this in Level 228."));
            return 0;
        }
        long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), BackroomsLevel.LEVEL_228);
        int x = player.getBlockX();
        int z = player.getBlockZ();
        int[] spot = switch (what) {
            case "facing" -> Level228Plan.nearestFacingMirrors(seed, x, z, 3, RINGS);
            case "branch" -> Level228Plan.nearestFalseBranch(seed, x, z, RINGS);
            default -> Level228Plan.nearestExit(seed, x, z);
        };
        if (spot == null) {
            ctx.getSource().sendFailure(Component.literal("None within " + RINGS * Level228Plan.CELL + " blocks."));
            return 0;
        }
        player.teleportTo(player.serverLevel(), spot[0] + 0.5, Level228Plan.FEET_Y, spot[1] + 0.5, player.getYRot(), player.getXRot());
        double d = Math.hypot(spot[0] - x, spot[1] - z);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Level 228 %s at %d %d %d (%.0f blocks from where you were).", what, spot[0], Level228Plan.FEET_Y, spot[1], d)), false);
        return 1;
    }
}
