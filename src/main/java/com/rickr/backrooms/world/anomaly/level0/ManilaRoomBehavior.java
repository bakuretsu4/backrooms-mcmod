package com.rickr.backrooms.world.anomaly.level0;

import com.rickr.backrooms.player.SanityService;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level0ManilaRoom;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Manila Room (https://backrooms-wiki.wikidot.com/manila-room): the one place in Level 0 where
 * the hum fades, a piano plays from nowhere (client side) and the Isolation Effect lets up, kept
 * stocked by volunteers. Its blocks are unbreakable, which fits "the interior never changes".
 * Here on the server: sitting in it calms you, slowly bringing your sanity back (more slowly than
 * a real safe room, which also keeps monsters out; this room doesn't promise that).
 */
public final class ManilaRoomBehavior implements AnomalyBehavior {
    private static final float SANITY_PER_SECOND = 0.3f;

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        if (ticksInside % 20 == 19 && Level0ManilaRoom.isInside(site, player.getBlockX(), player.getBlockZ())) {
            SanityService.restore(player, SANITY_PER_SECOND);
        }
    }
}
