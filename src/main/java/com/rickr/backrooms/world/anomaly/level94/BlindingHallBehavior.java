package com.rickr.backrooms.world.anomaly.level94;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.anomaly.AnomalyBehavior;
import com.rickr.backrooms.world.anomaly.AnomalyService;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level94AnomalyPlan;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import org.jetbrains.annotations.Nullable;

/**
 * {@code level_94/blinding_hall}: in the M.E.G.'s log
 * (https://backrooms-wiki.wikidot.com/m-e-g-the-discovery-of-level-94) an explorer chased by a
 * Robo-Man ran into a hall of "unnaturally bright, blinding levels of light", and the Robo-Man
 * lost track of him there.
 *
 * <p>While you stand in the hall nothing can keep you as its target: every few ticks anything
 * hunting you forgets you and stops, and nothing can pick you as a new target
 * ({@link #onChangeTarget}). Step out of the light and you can be seen again. The white-out
 * picture and the hum are the client's part (client/anomaly/level94).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class BlindingHallBehavior implements AnomalyBehavior {
    /** How far around the player hunters are made to forget them. */
    private static final double FORGET_RADIUS = 48.0;

    @Override
    public void tick(ServerPlayer player, AnomalySite site, long seed, int ticksInside) {
        if (ticksInside % 5 != 0 || !Level94AnomalyPlan.inHall(site, player.getX(), player.getY(), player.getZ())) {
            return;
        }
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(FORGET_RADIUS),
                hunter -> hunter.getTarget() == player)) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            // Mobs with "brains" (piglins, wardens...) keep their target in a memory instead.
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }

    /** True while {@code player} stands in a blinding hall, out of sight of anything that hunts. */
    public static boolean hides(ServerPlayer player) {
        @Nullable AnomalySite site = AnomalyService.currentSite(player);
        return site != null && Level94AnomalyPlan.inHall(site, player.getX(), player.getY(), player.getZ());
    }

    /** Nothing picks a target it can't see for the glare. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player && hides(player)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }
}
