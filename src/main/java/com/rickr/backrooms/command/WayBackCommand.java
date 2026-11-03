package com.rickr.backrooms.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.layout.Level39Houses;
import com.rickr.backrooms.world.layout.Level39Tunnels;
import com.rickr.backrooms.world.layout.Level94Layout;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /backrooms wayback [sunlit|clay]} (for testing): the side ways back to the level that
 * sends you ahead of the path (see BackroomsLevel). In Level 39 the nearest sunlit dead end of the
 * tunnels (back to the Poolrooms) and the nearest clay front door in town (back to Level 9); in
 * Level 94 the nearest clay front door (back to Level 9). Without an argument it only says where
 * they are; {@code sunlit} or {@code clay} takes you there (in front of the light, or beside the
 * door).
 */
final class WayBackCommand {
    /** How far Level 94's search reaches (blocks; it works out whole plan squares). */
    private static final int LEVEL94_RADIUS = 1200;

    private WayBackCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("wayback")
                .executes(ctx -> run(ctx, null))
                .then(Commands.literal("sunlit").executes(ctx -> run(ctx, "sunlit")))
                .then(Commands.literal("clay").executes(ctx -> run(ctx, "clay")));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, @Nullable String go) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BackroomsLevel floor = BackroomsLevel.fromDimension(level.dimension());
        int x = player.getBlockX();
        int z = player.getBlockZ();
        BlockPos sunlit = null;
        BlockPos clay = null;
        if (floor == BackroomsLevel.LEVEL_39) {
            long seed = BackroomsSeeds.layoutSeed(level, floor);
            sunlit = Level39Tunnels.nearestSunlitEnd(seed, x, z);
            clay = Level39Houses.nearestClayDoor(seed, x, z);
        } else if (floor == BackroomsLevel.LEVEL_94) {
            int[] door = Level94Layout.nearestClayDoor(BackroomsSeeds.layoutSeed(level, floor), x, z, LEVEL94_RADIUS);
            if (door != null) {
                // The door's y comes from the world: the house stands on its own pad on the hills.
                clay = lowerDoorHalf(level, door[0], door[1]);
            }
        } else {
            ctx.getSource().sendFailure(Component.literal("Only Level 39 and Level 94 have ways back here (to the Poolrooms and Level 9)."));
            return 0;
        }
        BlockPos sunlitAt = sunlit;
        BlockPos clayAt = clay;
        if (go == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    (floor == BackroomsLevel.LEVEL_39 ? "Sunlit dead end (to the Poolrooms): " + describe(sunlitAt, player) + "\n" : "")
                            + "Clay door (to Level 9): " + describe(clayAt, player)), false);
            return sunlitAt != null || clayAt != null ? 1 : 0;
        }
        BlockPos target = go.equals("sunlit") ? sunlitAt : clayAt;
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("None found nearby."));
            return 0;
        }
        BlockPos stand = go.equals("sunlit") ? target : besideDoor(level, target);
        player.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        ctx.getSource().sendSuccess(() -> Component.literal("At " + stand.getX() + ", " + stand.getY() + ", " + stand.getZ()), false);
        return 1;
    }

    private static String describe(@Nullable BlockPos pos, ServerPlayer player) {
        if (pos == null) {
            return "none nearby";
        }
        int dist = (int) Math.hypot(pos.getX() + 0.5 - player.getX(), pos.getZ() + 0.5 - player.getZ());
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " (" + dist + " blocks)";
    }

    /** The lower half of the door standing in column (x, z) (the column is generated if it wasn't yet). */
    private static BlockPos lowerDoorHalf(ServerLevel level, int x, int z) {
        for (int y = level.getMaxBuildHeight() - 1; y > level.getMinBuildHeight(); y--) {
            BlockState s = level.getBlockState(new BlockPos(x, y, z));
            if (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                return new BlockPos(x, y, z);
            }
        }
        return new BlockPos(x, level.getSeaLevel(), z);
    }

    /** A free spot right beside a door (in front of it or behind it), so you don't stand in it. */
    private static BlockPos besideDoor(ServerLevel level, BlockPos door) {
        BlockState state = level.getBlockState(door);
        Direction facing = state.getBlock() instanceof DoorBlock ? state.getValue(DoorBlock.FACING) : Direction.NORTH;
        for (Direction d : new Direction[] {facing.getOpposite(), facing}) {
            BlockPos p = door.relative(d);
            if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) {
                return p;
            }
        }
        return door.relative(facing.getOpposite());
    }
}
