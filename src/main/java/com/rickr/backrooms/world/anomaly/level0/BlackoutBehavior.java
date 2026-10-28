package com.rickr.backrooms.world.anomaly.level0;

import com.rickr.backrooms.player.SanityService;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.FixtureOutages;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * A Blackout Zone (https://backrooms-wiki.wikidot.com/level-0): a whole section of the maze with
 * no light, rough walls, ankle-deep fluid on recessed floors and, instead of the buzz, "intense
 * silence". The place itself is built by the layout (no lamps, so no hum; the fluid slows you a
 * little and squelches under your feet) and the client plays the buzz that "doesn't stay in one
 * place". Here on the server:
 * <ul>
 *   <li><b>It notices you.</b> When you step in, the lights of the maze behind you die in a wave
 *       rolling outward from where you crossed the edge (world/FixtureOutages), so the lit halls
 *       you came from are gone too and it's the flashlight or nothing. The far side of the zone
 *       keeps its lights: those are the way out, if you can find them.</li>
 *   <li><b>It lets you go.</b> A minute later, or fifteen seconds after everyone has left, the
 *       lights come back from the outside in (a lit edge creeping back is a beacon). Then the zone
 *       rests for two minutes before it can do it again.</li>
 *   <li><b>It wears on you.</b> The silence and the dark drain your sanity a little faster than
 *       ordinary darkness does (on top of SanityService's own drain).</li>
 * </ul>
 */
public final class BlackoutBehavior implements AnomalyBehavior {
    private static final float SANITY_DRAIN_PER_SECOND = 0.1f;
    /** The dying wave reaches this far (blocks) from where you stepped in. */
    private static final float WAVE_RADIUS = 18.0f;
    /** The lights come back this long after the wave even if you're still inside (ticks)... */
    private static final long BACK_AFTER = 60L * 20;
    /** ...or this long after the last person left the zone. */
    private static final long BACK_AFTER_LEAVING = 15L * 20;
    /** After the lights are back the zone waits this long before it can take them again. */
    private static final long REST = 120L * 20;
    /** Safety net: FixtureOutages ends the outage by itself after this long (ms) if nobody does. */
    private static final int LONGEST_MS = 90_000;

    /** One zone's wave: its outage id (-1: none) and timings (game ticks). */
    private static final class Zone {
        int outage = -1;
        long startedAt;
        long lastInside;
        long restUntil;
    }

    private final Map<AnomalySite, Zone> zones = new HashMap<>();

    @Override
    public void enter(ServerPlayer player, AnomalySite site, long seed) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (zones.size() > 32 && !zones.containsKey(site)) {
            zones.values().removeIf(z -> z.outage < 0);
        }
        Zone zone = zones.computeIfAbsent(site, s -> new Zone());
        zone.lastInside = now;
        if (zone.outage >= 0 && now - zone.startedAt > LONGEST_MS / 50) {
            zone.outage = -1; // everyone wandered off far away: FixtureOutages ended it by itself
        }
        int ceiling = BackroomsLevel.LEVEL_0.layout().ceilingY();
        BlockPos edge = new BlockPos(player.getBlockX(), ceiling - 1, player.getBlockZ());
        if (zone.outage >= 0 || now < zone.restUntil || FixtureOutages.isOut(level, edge)) {
            return; // already dark, resting, or a blackout has the lights anyway
        }
        zone.outage = FixtureOutages.start(level, edge, WAVE_RADIUS, LONGEST_MS, FixtureOutages.Pattern.WAVE);
        zone.startedAt = now;
    }

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        if (ticksInside % 20 != 19) {
            return;
        }
        Zone zone = zones.get(site);
        if (zone != null) {
            zone.lastInside = player.serverLevel().getGameTime();
        }
        if (!player.isCreative()) {
            SanityService.drain(player, SANITY_DRAIN_PER_SECOND);
        }
    }

    @Override
    public void siteTick(ServerLevel level, AnomalySite site, long seed) {
        Zone zone = zones.get(site);
        if (zone == null || zone.outage < 0) {
            return;
        }
        long now = level.getGameTime();
        if (now - zone.startedAt >= BACK_AFTER || now - zone.lastInside >= BACK_AFTER_LEAVING) {
            FixtureOutages.end(level, zone.outage); // a WAVE comes back from the edge inward
            zone.outage = -1;
            zone.restUntil = now + REST;
        }
    }
}
