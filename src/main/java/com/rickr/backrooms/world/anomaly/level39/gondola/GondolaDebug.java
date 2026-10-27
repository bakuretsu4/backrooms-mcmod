package com.rickr.backrooms.world.anomaly.level39.gondola;

import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.level9.Level9Sites;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The operator test for the chairlift ({@code /backrooms gondola ride}): boards the waiting chair of
 * the nearest lift station exactly as a right-click would, so the ride can be tested without the
 * mouse. The real right-click path ({@link GondolaChairBlock}) is untouched.
 */
public final class GondolaDebug {
    /** The station must be this close (blocks) to the player. */
    public static final double RANGE = 32.0;

    private GondolaDebug() {}

    /** Starts the ride; returns null on success, or why it couldn't. */
    @Nullable
    public static Component ride(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_9) {
            return Component.literal("Run this in Level 9, next to a gondola station (/backrooms anomaly locate gondola).");
        }
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_9);
        AnomalySite site = BackroomsLevel.LEVEL_9.layout().nearestAnomaly(seed, player.getBlockX(), player.getBlockZ(), Level9Sites.GONDOLA);
        if (site == null) {
            return Component.literal("No gondola line found nearby (/backrooms anomaly locate gondola).");
        }
        GondolaLine line = GondolaLine.of(site);
        BlockPos chair = line.stationChairPos();
        double d = Math.sqrt(player.distanceToSqr(chair.getX() + 0.5, chair.getY() + 0.5, chair.getZ() + 0.5));
        if (d > RANGE) {
            return Component.literal("The nearest station chair is at " + chair.toShortString() + ", " + (int) d
                    + " blocks away; stand within " + (int) RANGE + ".");
        }
        if (!level.isLoaded(chair)) {
            return Component.literal("The station isn't loaded.");
        }
        if (player.isPassenger()) {
            player.stopRiding();
        }
        // Same as right-clicking the waiting chair (even if it is already out on the cable).
        GondolaChair.board(level, player, line, chair);
        return null;
    }
}
