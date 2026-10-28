package com.rickr.backrooms.world.anomaly.poolrooms;

import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.layout.PoolroomsAnomalyPlan;
import com.rickr.backrooms.world.layout.PoolroomsLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;

/**
 * A dark corner of the Poolrooms (see PoolroomsAnomalyPlan): stay in one long enough and you drift
 * into the Sanguine Reservoir, the red copy of the level.
 *
 * <p>Canon: Wikidot's Level 37.1, "The Sanguine Reservoir"
 * (https://backrooms-wiki.wikidot.com/level-37-1): an exact copy of Level 37 in which "all
 * lighting fades away", the water turns viscous "as if not letting go", and once you notice,
 * "retracing their steps will be useless". Lightless corners in otherwise lit rooms come from the
 * Level 37 journal on both wikis (https://backrooms-wiki.wikidot.com/level-37,
 * https://backrooms.fandom.com/wiki/Level_37); Fandom's 37.1 is likewise reached by wading
 * through "dark, dingy areas", "typically without one taking notice".
 *
 * <p>While you stay in the nook the client fades the light out and the chants grow
 * ({@code DarkCornerEffect}). After 20-30 s ({@link PoolroomsAnomalyPlan#driftTicks}) darkness
 * closes in, and a couple of seconds later you are moved {@link PoolroomsLayout#RESERVOIR_OFFSET}
 * blocks east, onto the same spot of the identical red copy: same look, same momentum, nothing on
 * screen jumps. Walk out of the nook before that and nothing happens. There is no dark corner in
 * the Reservoir, so there is no way back; its drains still lead on to Level 37.
 */
final class DarkCornerBehavior implements AnomalyBehavior {
    /** Darkness sets in this many ticks before the drift, so the move itself happens in the black. */
    private static final int DARKNESS_LEAD = 50;
    /** How long the darkness lasts: through the move and a few seconds into the Reservoir. */
    private static final int DARKNESS_TICKS = 140;

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        int drift = PoolroomsAnomalyPlan.driftTicks(site);
        if (ticksInside == drift - DARKNESS_LEAD) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_TICKS, 0, false, false, false));
        } else if (ticksInside == drift) {
            drift(player);
        }
    }

    /** Moves the player onto their own spot in the Reservoir (only from the original level). */
    private static void drift(ServerPlayer player) {
        double toX = player.getX() + PoolroomsLayout.RESERVOIR_OFFSET;
        if (PoolroomsLayout.inReservoir(player.getX()) || !PoolroomsLayout.inReservoir(toX)) {
            return;
        }
        // The destination is far away: hold its chunk loaded for the arrival, like vanilla's /tp.
        player.serverLevel().getChunkSource().addRegionTicket(TicketType.POST_TELEPORT,
                new ChunkPos(BlockPos.containing(toX, player.getY(), player.getZ())), 1, player.getId());
        Teleports.shift(player, PoolroomsLayout.RESERVOIR_OFFSET, 0.0, 0.0);
        player.playNotifySound(ModSounds.POOL_DRIFT.get(), SoundSource.AMBIENT, 0.9f, 1.0f);
    }
}
