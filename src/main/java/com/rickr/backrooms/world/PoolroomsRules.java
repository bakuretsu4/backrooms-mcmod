package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.layout.PoolroomsLayout;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Server rules of the Poolrooms' exit: the drain.
 *
 * <p>Each exit is a 5x5 water shaft at the bottom of a deep pool. Once you sink into it (below
 * y39, in the water, inside the shaft's footprint) the current grabs you and drags you down.
 * Below {@link BackroomsLevel#transferBelowY()} the usual exit check in ServerEvents takes over and
 * you escape.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class PoolroomsRules {
    /** You have to be this deep before the drain grabs you (the pool floor is y36). */
    private static final double PULL_BELOW_Y = 39.0;
    /** Extra downward speed added every tick while pulled (blocks/tick). */
    private static final double PULL_PER_TICK = 0.08;
    /** Fastest the current drags you down (blocks/tick), so the trip still takes a moment. */
    private static final double MAX_SINK_SPEED = 0.6;
    /** How strongly you are steered toward the middle of the shaft, so you don't scrape the walls. */
    private static final double CENTRE_PULL = 0.05;

    /** Players the drain is currently pulling, so the gurgle only plays when a pull starts. */
    private static final Set<UUID> PULLING = new HashSet<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int[] drain = drainPulling(player);
        if (drain == null) {
            PULLING.remove(player.getUUID());
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
     * The centre {x, z} of the drain that is pulling this player right now, or null: they must be
     * in the Poolrooms, in the water, below y39 and inside the shaft's 5x5 footprint.
     */
    @Nullable
    private static int[] drainPulling(ServerPlayer player) {
        if (player.isSpectator()
                || BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.POOLROOMS
                || player.getY() >= PULL_BELOW_Y || !player.isInWater()) {
            return null;
        }
        long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), BackroomsLevel.POOLROOMS);
        int[] exit = BackroomsLevel.POOLROOMS.layout().exits().nearest(seed, player.getBlockX(), player.getBlockZ());
        // The shaft covers the blocks exit-2 .. exit+2, i.e. 2.5 blocks either side of the centre.
        double reach = PoolroomsLayout.SHAFT_RADIUS + 0.5;
        boolean inside = Math.abs(player.getX() - (exit[0] + 0.5)) < reach
                && Math.abs(player.getZ() - (exit[1] + 0.5)) < reach;
        return inside ? exit : null;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PULLING.remove(event.getEntity().getUUID());
    }

    private PoolroomsRules() {}
}
