package com.rickr.backrooms.world.anomaly.level37;

import com.rickr.backrooms.player.SanityService;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.layout.Level37AnomalyPlan;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code level_37/looping_tunnel}: a stretch of dark round tunnel that keeps bringing you back.
 * The journal on both wikis' Level 37 page speaks of "looping hallways and nonsensical
 * corridors" (https://backrooms-wiki.wikidot.com/level-37, https://backrooms.fandom.com/wiki/Level_37).
 *
 * <p>The site is two cells of vaulted corridor that look exactly alike (their side passages are
 * walled up, see {@link Level37AnomalyPlan}). Walk in and keep going: halfway through the second
 * cell you are quietly moved back one cell ({@link Teleports#shift}, so nothing on screen jumps)
 * and the tunnel goes on as before. "Deeper" is the way you came in; turn round and you walk
 * straight back out. After a few rounds (2 to 5, per tunnel) it lets you through. Each round
 * costs a little sanity, and the client dims the light a little more.
 */
final class LoopingTunnel implements AnomalyBehavior {
    /** Sanity each round costs: the slow dread of a place that doesn't end. */
    private static final float ROUND_COST = 2.0f;

    /** Which way a player is heading (+1 / -1 along the tunnel), and how many rounds so far. */
    private static final class Walk {
        final int dir;
        int rounds;
        double lastAlong;

        Walk(int dir, double along) {
            this.dir = dir;
            this.lastAlong = along;
        }
    }

    private final Map<UUID, Walk> walks = new HashMap<>();

    @Override
    public void enter(ServerPlayer player, AnomalySite site, long seed) {
        double along = Level37AnomalyPlan.loopAlong(site, player.getX(), player.getZ());
        // Came in at the low end: deeper is up the tunnel, and the other way round.
        walks.put(player.getUUID(), new Walk(along < Level37AnomalyPlan.LOOP_SHIFT ? 1 : -1, along));
    }

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        Walk walk = walks.get(player.getUUID());
        if (walk == null) {
            return;
        }
        double along = Level37AnomalyPlan.loopAlong(site, player.getX(), player.getZ());
        if (walk.rounds < rounds(site)) {
            // The line halfway through the second cell, seen from where you came in.
            double line = walk.dir > 0 ? 1.5 * Level37AnomalyPlan.LOOP_SHIFT : 0.5 * Level37AnomalyPlan.LOOP_SHIFT;
            boolean crossed = walk.dir > 0 ? walk.lastAlong < line && along >= line
                    : walk.lastAlong > line && along <= line;
            if (crossed) {
                double back = -walk.dir * Level37AnomalyPlan.LOOP_SHIFT;
                boolean alongX = Level37AnomalyPlan.loopAlongX(site);
                Teleports.shift(player, alongX ? back : 0.0, 0.0, alongX ? 0.0 : back);
                along += back;
                walk.rounds++;
                if (!player.isCreative()) {
                    SanityService.drain(player, ROUND_COST);
                }
            }
        }
        walk.lastAlong = along;
    }

    @Override
    public void leave(ServerPlayer player, AnomalySite site, long seed) {
        walks.remove(player.getUUID());
    }

    /** How many times this tunnel brings you round before it lets you through (2..5). */
    static int rounds(AnomalySite site) {
        return 2 + (int) Math.floorMod(site.salt(), 4L);
    }
}
