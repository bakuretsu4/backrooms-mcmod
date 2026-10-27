package com.rickr.backrooms.world;

import com.rickr.backrooms.network.Level39QuakePayload;
import com.rickr.backrooms.world.layout.Level39Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Level 39's echo quakes (server side). Canon: the town is heavily echoed, and the echoes can
 * build up into magnitude-5 quakes.
 *
 * <p>Anything loud can start one through {@link #trigger}: the level's own ECHO_QUAKE event (a
 * superstructure boom far away, see {@link LevelEvents}) or a player knocking a house wall down
 * (the anomaly code calls it). The server only decides WHO feels it and HOW hard; every client
 * plays the show itself ({@code client/Level39QuakeEffects}): the boom, its echoes coming back off
 * the facades, and, if it is strong enough, the rumble building into a quake with shaking, falling
 * dust and rattling windows, 6-10 s long.
 *
 * <p>A quake needs time to settle: for {@link #COOLDOWN_TICKS} after one, new loud sounds only
 * echo. That stops a player hammering on walls from shaking the town non-stop.
 */
public final class Level39Quakes {
    /** From this strength on (0..1) the echo builds into a quake; below it you only hear echoes. */
    public static final float QUAKE_THRESHOLD = 0.3f;
    /** Players further than this (blocks) from the source feel nothing; the strength fades out toward it. */
    private static final double RANGE = 900.0;
    /** After a quake, new triggers only echo for this long (45 s). */
    private static final int COOLDOWN_TICKS = 45 * 20;
    /** How long the client's show lasts at most (echo build-up + quake), for the cooldown start. */
    private static final int SHOW_TICKS = 12 * 20;
    /** The superstructure booms the ECHO_QUAKE event sends come from this high up the facade. */
    private static final int BOOM_HEIGHT_MIN = 110;
    private static final int BOOM_HEIGHT_MAX = 200;

    /** Game time until which new triggers only echo (no quake). Server thread only. */
    private static long quietUntil = Long.MIN_VALUE;

    /**
     * A loud sound at {@code source} in Level 39 with {@code strength} 0..1 (wall knocking ~0.5, a
     * superstructure boom 0.7-1.0). Everyone within {@value #RANGE} blocks hears it echo; at
     * {@link #QUAKE_THRESHOLD} or more (after distance) it becomes a quake for them. Does nothing
     * outside Level 39.
     */
    public static void trigger(ServerLevel level, BlockPos source, float strength) {
        if (BackroomsLevel.fromDimension(level.dimension()) != BackroomsLevel.LEVEL_39) {
            return;
        }
        long now = level.getGameTime();
        float s = Mth.clamp(strength, 0.0f, 1.0f);
        if (now < quietUntil) {
            s = Math.min(s, QUAKE_THRESHOLD * 0.8f); // still settling: only an echo
        }
        boolean anyQuake = false;
        for (ServerPlayer player : level.players()) {
            double d = Math.sqrt(player.distanceToSqr(source.getX() + 0.5, source.getY() + 0.5, source.getZ() + 0.5));
            if (d > RANGE) {
                continue;
            }
            // Gentle falloff: the whole town is one huge echo chamber, so even far away it carries.
            float felt = s * (float) (1.0 - 0.6 * (d / RANGE));
            anyQuake |= felt >= QUAKE_THRESHOLD;
            PacketDistributor.sendToPlayer(player, new Level39QuakePayload(source.getX(), source.getY(), source.getZ(), felt));
        }
        if (anyQuake) {
            quietUntil = now + SHOW_TICKS + COOLDOWN_TICKS;
        }
    }

    /**
     * The ECHO_QUAKE event: a boom from high on the facade nearest to a random player (that is
     * where the superstructure "lives"), strong enough to build into a quake.
     */
    static void superstructureBoom(ServerLevel level, RandomSource random) {
        if (level.players().isEmpty()) {
            return;
        }
        ServerPlayer player = level.players().get(random.nextInt(level.players().size()));
        int x = player.getBlockX();
        int z = player.getBlockZ();
        int lx = Level39Layout.local(x);
        int lz = Level39Layout.local(z);
        int glass = Level39Layout.TOWN_HALF + 1;
        // The facade on the side the player is nearest to, a little along it.
        int along = random.nextInt(161) - 80;
        int bx;
        int bz;
        if (Math.abs(lx) >= Math.abs(lz)) {
            bx = x - lx + (lx >= 0 ? glass : -glass);
            bz = z + along;
        } else {
            bx = x + along;
            bz = z - lz + (lz >= 0 ? glass : -glass);
        }
        int y = Mth.nextInt(random, BOOM_HEIGHT_MIN, BOOM_HEIGHT_MAX);
        trigger(level, new BlockPos(bx, y, bz), 0.75f + random.nextFloat() * 0.25f);
    }

    /** Server stopping: forget the cooldown. */
    static void reset() {
        quietUntil = Long.MIN_VALUE;
    }

    private Level39Quakes() {}
}
