package com.rickr.backrooms.world.anomaly.level4;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.anomaly.Anomalies;
import com.rickr.backrooms.world.layout.Level4AnomalyPlan;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Level 4's anomalies on the server: registers their behaviours, and runs the few things that
 * happen a moment AFTER something else (an elevator arriving a few seconds after its doors close,
 * the stairs letting you out somewhere else once the screen has gone dark).
 *
 * <p>The kinds and their canon:
 * <ul>
 *   <li>{@link LoopingStairwell}: stairs that always lead back to the same floor (Fandom, Level 4).</li>
 *   <li>{@link WindowAnomaly}: the clear window that lures and grabs (Wikidot, Entity 2 "Windows").</li>
 *   <li>{@link VanishingElevator}: an open elevator that moves when unseen and takes you elsewhere
 *       (Wikidot, Level 4; Fandom, Level 4; Wikidot, Desolation Level 4).</li>
 *   <li>{@link CubiclesAnomaly}: Level 4.3 "The Cubicles", which makes you want to stay (Fandom, Level 4.3).</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level4Anomalies {
    /** Something to do to a player later, if they are still online and still on Level 4. */
    private record Task(UUID player, long due, Consumer<ServerPlayer> action) {}

    private static final List<Task> TASKS = new ArrayList<>();

    /** Called from {@link Anomalies#bootstrap}. */
    public static void register() {
        Anomalies.register(Level4AnomalyPlan.LOOPING_STAIRWELL, new LoopingStairwell());
        Anomalies.register(Level4AnomalyPlan.WINDOW, new WindowAnomaly());
        Anomalies.register(Level4AnomalyPlan.VANISHING_ELEVATOR, new VanishingElevator());
        Anomalies.register(Level4AnomalyPlan.CUBICLES, new CubiclesAnomaly());
    }

    /** Runs {@code action} for this player in {@code ticks} ticks (skipped if they left Level 4 or logged out). */
    static void later(ServerPlayer player, int ticks, Consumer<ServerPlayer> action) {
        TASKS.add(new Task(player.getUUID(), player.server.getTickCount() + ticks, action));
    }

    /** True if the player is on Level 4 (the only place these anomalies exist). */
    static boolean onLevel4(ServerPlayer player) {
        return BackroomsLevel.fromDimension(player.level().dimension()) == BackroomsLevel.LEVEL_4;
    }

    /** Level 4's layout seed for this level. */
    static long seed(ServerLevel level) {
        return BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_4);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (TASKS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        // Copy the due ones out first: an action may schedule another task.
        List<Task> due = new ArrayList<>();
        for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            Task task = it.next();
            if (task.due() <= now) {
                due.add(task);
                it.remove();
            }
        }
        for (Task task : due) {
            ServerPlayer player = server.getPlayerList().getPlayer(task.player());
            if (player != null && onLevel4(player)) {
                task.action().accept(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        TASKS.removeIf(task -> task.player().equals(id));
        VanishingElevator.forget(id);
        WindowAnomaly.forget(id);
        CubiclesAnomaly.forget(id);
    }

    private Level4Anomalies() {}
}
