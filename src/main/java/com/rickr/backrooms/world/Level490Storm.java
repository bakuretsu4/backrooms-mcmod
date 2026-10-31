package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.Level490StrikePayload;
import com.rickr.backrooms.world.layout.Level490Layout;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Level 490's storm (server side): picks where lightning strikes and tells everyone in the level,
 * so friends see the same bolt and hear its thunder each as late as their own distance makes it
 * (client/Level490Lightning). Most strikes are far off in the storm, some inside the clouds, now
 * and then one close by or on one of the colossal chains. Between them, every minute or two, one of
 * the far colossal chains jerks taut as if something pulled it from below (client/Level490ChainEvents).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level490Storm {
    /** Seconds between strikes. */
    private static final int MIN_GAP = 5;
    private static final int MAX_GAP = 16;
    private static long nextTick = -1;
    /** Seconds between chain jolts. */
    private static final int JOLT_MIN_GAP = 40;
    private static final int JOLT_MAX_GAP = 110;
    private static long nextJolt = -1;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_490) {
            return;
        }
        List<ServerPlayer> players = level.players();
        long now = level.getGameTime();
        if (players.isEmpty()) {
            nextTick = -1;
            nextJolt = -1;
            return;
        }
        RandomSource random = level.getRandom();
        if (nextJolt < 0) {
            nextJolt = now + 400 + random.nextInt(800);
        }
        if (now >= nextJolt) {
            nextJolt = now + (JOLT_MIN_GAP + random.nextInt(JOLT_MAX_GAP - JOLT_MIN_GAP + 1)) * 20L;
            Level490StrikePayload jolt = jolt(level, players.get(random.nextInt(players.size())), random);
            if (jolt != null) {
                for (ServerPlayer player : players) {
                    PacketDistributor.sendToPlayer(player, jolt);
                }
            }
        }
        if (nextTick < 0) {
            nextTick = now + 60 + random.nextInt(100);
        }
        if (now < nextTick) {
            return;
        }
        nextTick = now + (MIN_GAP + random.nextInt(MAX_GAP - MIN_GAP + 1)) * 20L;
        ServerPlayer near = players.get(random.nextInt(players.size()));
        Level490StrikePayload strike = pick(level, near, random);
        for (ServerPlayer player : players) {
            PacketDistributor.sendToPlayer(player, strike);
        }
    }

    private static Level490StrikePayload pick(ServerLevel level, ServerPlayer near, RandomSource random) {
        long seed = random.nextLong();
        double roll = random.nextDouble();
        if (roll < 0.1) {
            // A colossal chain within reach, if there is one.
            long layoutSeed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_490);
            int[][] found = new int[1][];
            Level490Layout.chainsNear(layoutSeed, near.getBlockX(), near.getBlockZ(), 700, (cx, cz, phase) -> {
                if (found[0] == null || random.nextInt(3) == 0) {
                    found[0] = new int[] {cx, cz};
                }
            });
            if (found[0] != null) {
                return new Level490StrikePayload(seed, found[0][0], found[0][1], Level490StrikePayload.CHAIN);
            }
        }
        boolean cloud = roll >= 0.1 && roll < 0.45;
        double r = random.nextDouble();
        // Mostly far away; a few within a few hundred blocks, rarely close.
        double distance = cloud ? 500 + random.nextDouble() * 2500
                : r < 0.06 ? 180 + random.nextDouble() * 220 : r < 0.3 ? 400 + random.nextDouble() * 500 : 900 + random.nextDouble() * 1700;
        double angle = random.nextDouble() * Math.PI * 2;
        int x = near.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = near.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        return new Level490StrikePayload(seed, x, z, cloud ? Level490StrikePayload.CLOUD : Level490StrikePayload.BOLT);
    }

    /**
     * A colossal chain 350-2400 blocks from the player: far enough that it is drawn in the sky (only
     * those can move), near enough to see and hear.
     */
    private static Level490StrikePayload jolt(ServerLevel level, ServerPlayer near, RandomSource random) {
        long layoutSeed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_490);
        int[][] found = new int[1][];
        int[] count = {0};
        Level490Layout.chainsNear(layoutSeed, near.getBlockX(), near.getBlockZ(), 2400, (cx, cz, phase) -> {
            double d = Math.hypot(cx - near.getX(), cz - near.getZ());
            if (d >= 350 && d <= 2400 && random.nextInt(++count[0]) == 0) {
                found[0] = new int[] {cx, cz};
            }
        });
        return found[0] == null ? null : new Level490StrikePayload(random.nextLong(), found[0][0], found[0][1], Level490StrikePayload.JOLT);
    }

    private Level490Storm() {}
}
