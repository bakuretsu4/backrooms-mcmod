package com.rickr.backrooms.world.anomaly.level2;

import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code level_2/warm_door}: a steel door in the corridor wall that opens onto bare concrete
 * (world/layout/Level2Oddities). It does nothing to you. Walking up to it you only feel that the
 * air is warm (a quiet line above the hotbar); the client adds a soft warmth to the picture and
 * something like a boiler breathing behind the door.
 */
final class WarmDoorBehavior implements AnomalyBehavior {
    @Override
    public void enter(ServerPlayer player, AnomalySite site, long seed) {
        player.displayClientMessage(Component.translatable("message.backrooms.l2.warm_door"), true);
    }
}
