package com.rickr.backrooms.world.anomaly.level0;

import com.rickr.backrooms.player.SanityService;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import net.minecraft.server.level.ServerPlayer;

/**
 * A Blackout Zone (https://backrooms-wiki.wikidot.com/level-0): a whole section of the maze with
 * no light, rough walls, ankle-deep fluid on recessed floors and, instead of the buzz, "intense
 * silence". The place itself is built by the layout (no lamps, so no hum; the fluid slows you a
 * little and squelches under your feet) and the client plays the buzz that "doesn't stay in one
 * place". Here on the server: the silence and the dark wear on you, a little faster than ordinary
 * darkness does (that drain comes on top of SanityService's own).
 */
public final class BlackoutBehavior implements AnomalyBehavior {
    private static final float SANITY_DRAIN_PER_SECOND = 0.1f;

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        if (ticksInside % 20 == 19 && !player.isCreative()) {
            SanityService.drain(player, SANITY_DRAIN_PER_SECOND);
        }
    }
}
