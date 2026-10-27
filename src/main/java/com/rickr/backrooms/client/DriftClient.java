package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The client side of separate realities (the rules live in {@code world/RealityDrift}). The fake
 * blocks themselves are ordinary block updates, so the game already draws, lights and collides
 * with them; all this class adds is "right-clicking a block that isn't really there does nothing".
 *
 * <p>Why that's needed: a door opens on your own screen the moment you click it, before the server
 * has answered. A fake door would swing open (with its creak) and then shut again. Cancelling the
 * click here keeps it a door that simply never opens.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class DriftClient {
    /** The blocks the server is showing differently to us right now (usually empty). */
    private static final Set<BlockPos> FAKED = new HashSet<>();

    /** From {@code DriftPayload}: the full list of our fake blocks (empty when the drift ends). */
    public static void receive(List<BlockPos> faked) {
        FAKED.clear();
        FAKED.addAll(faked);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // In single player the built-in server fires this event in the same game too: only act
        // for the client's own copy of the world.
        if (!FAKED.isEmpty() && event.getLevel().isClientSide() && FAKED.contains(event.getPos())) {
            event.setCanceled(true);
            // FAIL (not PASS) so the game doesn't go on to use the held item in the air instead.
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        FAKED.clear();
    }

    private DriftClient() {}
}
