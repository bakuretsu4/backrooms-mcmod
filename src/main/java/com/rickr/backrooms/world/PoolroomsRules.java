package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.layout.PoolroomsLayout;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Server rules of the Poolrooms' way out.
 *
 * <p>The exit is the great pool hall ({@code PoolroomsHall}): climb its long stair and step into
 * the niche of daylight at the top, and you go on to the next level of the path
 * ({@link Travel#useExit}, so {@link BackroomsLevel#next()} decides where).
 *
 * <p>Worlds made before the hall still have drains where the halls would be: a 5x5 water shaft at
 * the bottom of a deep pool, down to y24. Their current still works there (only where that shaft
 * really is water: a new hall has open air at that height), and sinking below y30 in it takes you
 * on, so nobody is stranded in an old world.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class PoolroomsRules {
    // ---- Old drains -----------------------------------------------------------------------------
    /** You have to be this deep before a drain grabs you (its pool's floor is y36). */
    private static final double PULL_BELOW_Y = 39.0;
    /** Below this, in a drain's shaft, you are through. */
    private static final double DRAIN_EXIT_BELOW_Y = 30.0;
    /** A drain's shaft is water down to here (its dark bottom is one lower). */
    private static final int DRAIN_SHAFT_LOW_Y = 24;
    /** The shaft covers the blocks exit-2 .. exit+2, i.e. 2.5 blocks either side of the centre. */
    private static final double SHAFT_REACH = 2.5;
    /** Extra downward speed added every tick while pulled (blocks/tick). */
    private static final double PULL_PER_TICK = 0.08;
    /** Fastest the current drags you down (blocks/tick), so the trip still takes a moment. */
    private static final double MAX_SINK_SPEED = 0.6;
    /** How strongly you are steered toward the middle of the shaft, so you don't scrape the walls. */
    private static final double CENTRE_PULL = 0.05;

    /** Players a drain is currently pulling, so the gurgle only plays when a pull starts. */
    private static final Set<UUID> PULLING = new HashSet<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isSpectator() || BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.POOLROOMS) {
            PULLING.remove(player.getUUID());
            return;
        }
        long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), BackroomsLevel.POOLROOMS);
        PoolroomsLayout layout = (PoolroomsLayout) BackroomsLevel.POOLROOMS.layout();
        if (layout.isHallExit(seed, player.getBlockX(), player.getBlockY(), player.getBlockZ())) {
            Travel.useExit(player, BackroomsLevel.POOLROOMS);
            return;
        }
        oldDrain(player, seed);
    }

    /** An old world's drain: the current drags you down its shaft, and below y30 you are through. */
    private static void oldDrain(ServerPlayer player, long seed) {
        int[] drain = drainPulling(player, seed);
        if (drain == null) {
            PULLING.remove(player.getUUID());
            return;
        }
        if (player.getY() < DRAIN_EXIT_BELOW_Y) {
            Travel.useExit(player, BackroomsLevel.POOLROOMS);
            return;
        }
        // Start of a pull: the drain gurgles (everyone nearby hears it, including you).
        if (PULLING.add(player.getUUID())) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.DRAIN_PULL.get(), SoundSource.AMBIENT, 1.0f, 1.0f);
        }

        Vec3 motion = player.getDeltaMovement();
        double down = Math.max(motion.y - PULL_PER_TICK, -MAX_SINK_SPEED);
        double towardX = (drain[0] + 0.5 - player.getX()) * CENTRE_PULL;
        double towardZ = (drain[1] + 0.5 - player.getZ()) * CENTRE_PULL;
        player.setDeltaMovement(towardX, down, towardZ);
        // The client is in charge of its own movement; this flag makes the server send the new
        // velocity to the player's game (the same trick knockback uses).
        player.hurtMarked = true;
    }

    /**
     * The centre {x, z} of the old drain that is pulling this player right now, or null: they must
     * be in the water, below y39, inside the shaft's 5x5 footprint, and the shaft must really be
     * there (water at its bottom).
     */
    @Nullable
    private static int[] drainPulling(ServerPlayer player, long seed) {
        if (player.getY() >= PULL_BELOW_Y || !player.isInWater()) {
            return null;
        }
        int[] exit = BackroomsLevel.POOLROOMS.layout().exits().nearest(seed, player.getBlockX(), player.getBlockZ());
        boolean inside = Math.abs(player.getX() - (exit[0] + 0.5)) < SHAFT_REACH
                && Math.abs(player.getZ() - (exit[1] + 0.5)) < SHAFT_REACH;
        if (!inside) {
            return null;
        }
        BlockPos bottom = new BlockPos(exit[0], DRAIN_SHAFT_LOW_Y, exit[1]);
        return player.level().getFluidState(bottom).is(FluidTags.WATER) ? exit : null;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PULLING.remove(event.getEntity().getUUID());
    }

    private PoolroomsRules() {}
}
