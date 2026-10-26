package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.player.Checkpoints;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.layout.Level37Layout;
import com.rickr.backrooms.world.layout.Level37Plan;
import com.rickr.backrooms.world.layout.PoolroomsLayout;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server rules of Level 37 ("Sublimity"), in and out:
 * <ul>
 *   <li><b>In:</b> somewhere in the Poolrooms a sunlit archway glows in a wall. Walk into its
 *       light (the back half of the passage) and you are in Level 37; the screen whitens and
 *       fades back in on the client ({@code client/Level37Atmosphere}), no tape glitch.</li>
 *   <li><b>Out:</b> the round pits of deep, dark water. Once you have dived a few blocks down
 *       one, a gentle current draws you down the shaft. Below
 *       {@link Level37Layout#TRANSFER_BELOW_Y} the usual exit check ({@code ServerEvents}) sends
 *       you on to Level 9.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level37Rules {
    /** The pit's current takes over this far under the surface (the water's top is y40). */
    private static final double PULL_BELOW_Y = 37.0;
    /** Extra downward speed added every tick while pulled (blocks/tick). */
    private static final double PULL_PER_TICK = 0.05;
    /** Fastest the current draws you down: a calm sinking, not the drain's violent suck. */
    private static final double MAX_SINK_SPEED = 0.45;
    /** How strongly you are steered toward the middle of the shaft. */
    private static final double CENTRE_PULL = 0.04;
    /** Feet must be on the archway's walkway (y42), not somewhere above it. */
    private static final double ARCHWAY_MIN_Y = PoolroomsLayout.SURFACE_Y + 0.5;
    private static final double ARCHWAY_MAX_Y = PoolroomsLayout.SURFACE_Y + 3.0;

    /** Players the pit is currently drawing down, so its low sound only plays when a pull starts. */
    private static final Set<UUID> SINKING = new HashSet<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        BackroomsLevel floor = BackroomsLevel.fromDimension(player.level().dimension());
        if (floor == BackroomsLevel.POOLROOMS) {
            checkArchway(player);
        } else if (floor == BackroomsLevel.LEVEL_37) {
            pitCurrent(player);
        } else {
            SINKING.remove(player.getUUID());
        }
    }

    /** Stepping into the light at the back of a sunlit archway: on to Level 37. */
    private static void checkArchway(ServerPlayer player) {
        if (player.getY() < ARCHWAY_MIN_Y || player.getY() > ARCHWAY_MAX_Y) {
            return;
        }
        PoolroomsLayout layout = (PoolroomsLayout) BackroomsLevel.POOLROOMS.layout();
        long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), BackroomsLevel.POOLROOMS);
        if (!layout.isSunlitThreshold(seed, player.getBlockX(), player.getBlockZ())) {
            return;
        }
        // A secret way down, but still a way down: the Poolrooms checkpoint no longer applies.
        Checkpoints.clear(player);
        if (Travel.sendToQuietly(player, BackroomsLevel.LEVEL_37, player.getBlockX(), player.getBlockZ())) {
            player.playNotifySound(ModSounds.LEVEL37_ARRIVE.get(), SoundSource.MASTER, 0.9f, 1.0f);
            player.sendSystemMessage(Component.translatable("message.backrooms.arrive.level_37").withStyle(s -> s.withItalic(true)));
        }
    }

    /** Deep in a pit's shaft: a slow current draws you down toward the way out. */
    private static void pitCurrent(ServerPlayer player) {
        if (player.getY() >= PULL_BELOW_Y || !player.isInWater()) {
            SINKING.remove(player.getUUID());
            return;
        }
        long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), BackroomsLevel.LEVEL_37);
        int[] pit = Level37Layout.plan().pitCentre(seed, player.getBlockX(), player.getBlockZ());
        // The pit is centred on a block corner; its shaft reaches PIT_RADIUS blocks out from it.
        double dx = pit[0] - player.getX();
        double dz = pit[1] - player.getZ();
        if (dx * dx + dz * dz > Level37Plan.PIT_RADIUS * Level37Plan.PIT_RADIUS) {
            SINKING.remove(player.getUUID());
            return;
        }
        if (SINKING.add(player.getUUID())) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.DRAIN_PULL.get(), SoundSource.AMBIENT, 0.5f, 0.6f);
        }
        Vec3 motion = player.getDeltaMovement();
        double down = Math.max(motion.y - PULL_PER_TICK, -MAX_SINK_SPEED);
        player.setDeltaMovement(dx * CENTRE_PULL, down, dz * CENTRE_PULL);
        // The client moves itself; this makes the server send it the new velocity.
        player.hurtMarked = true;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SINKING.remove(event.getEntity().getUUID());
    }

    private Level37Rules() {}
}
