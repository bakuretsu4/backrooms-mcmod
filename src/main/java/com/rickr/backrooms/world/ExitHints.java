package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.ExitHintPayload;
import com.rickr.backrooms.world.layout.LevelLayout;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tells each player where the nearest real exit of their level is, for the subtle hints that point
 * at it on the client: a faint sound coming from it, and marks only the camcorder's NightShot shows
 * (see {@code client/hint/ExitHints}). The torn notes in supply containers use {@link #nearestExit}
 * too. Secret ways (the Poolrooms' sunlit archway, Level 9's clay doors, the throne light) are never
 * hinted: only each level's ordinary way on.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class ExitHints {
    /** Re-checked this often (ticks): the nearest exit only changes when you have walked a long way. */
    private static final int EVERY = 40;
    /** What each player was last told, so nothing is sent while it stays the same. */
    private static final Map<UUID, ExitHintPayload> SENT = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % EVERY != 0) {
            return;
        }
        BackroomsLevel floor = BackroomsLevel.fromDimension(player.level().dimension());
        ExitHintPayload now = ExitHintPayload.NONE;
        if (floor != null) {
            int[] exit = nearestExit(player.serverLevel(), floor, player.getBlockX(), player.getBlockZ());
            now = new ExitHintPayload(true, exit[0], exit[1], exit[2]);
        }
        if (!now.equals(SENT.get(player.getUUID()))) {
            SENT.put(player.getUUID(), now);
            PacketDistributor.sendToPlayer(player, now);
        }
    }

    /** {x, y, z} of the exit nearest to column (x, z) of this level. */
    public static int[] nearestExit(net.minecraft.server.level.ServerLevel level, BackroomsLevel floor, int x, int z) {
        LevelLayout layout = floor.layout();
        long seed = BackroomsSeeds.layoutSeed(level, floor);
        int[] exit = layout.exitPosition(seed, x, z);
        return new int[] {exit[0], layout.exitY(seed, exit[0], exit[1]), exit[1]};
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    private ExitHints() {}
}
