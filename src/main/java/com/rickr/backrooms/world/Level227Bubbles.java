package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.entity.VoidBubble;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Lets a fallen bubble ({@link VoidBubble}) drift down by a branch edge now and then: very rarely,
 * as canon has it. Once a minute, for each player in Level 227, a 1 in {@value #CHANCE} chance
 * (about one every quarter of an hour of play) if no other bubble is within
 * {@value #SPACING} blocks of them.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level227Bubbles {
    private static final int EVERY_TICKS = 1200;
    private static final int CHANCE = 15;
    private static final double SPACING = 192.0;

    private static final RandomSource RANDOM = RandomSource.create();

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_227
                || level.getGameTime() % EVERY_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || RANDOM.nextInt(CHANCE) != 0) {
                continue;
            }
            AABB around = player.getBoundingBox().inflate(SPACING, 256.0, SPACING);
            if (level.getEntitiesOfClass(VoidBubble.class, around).isEmpty()) {
                VoidBubble.spawnNearEdge(level, player, RANDOM, false);
            }
        }
    }

    private Level227Bubbles() {}
}
