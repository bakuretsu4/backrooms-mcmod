package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.player.CheckpointData;
import com.rickr.backrooms.registry.ModAttachments;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.layout.Level490Layout;
import com.rickr.backrooms.world.layout.Level490Spire;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server rules of Level 490, "Penance": falling.
 * <ul>
 *   <li><b>The leap:</b> stepping off the collar of the colossal chain at the very top of the climb
 *       ({@link Level490Spire#LEAP_Y}) is the way out, to Level 9: the one fall that doesn't bring
 *       you back, wherever it ends.</li>
 *   <li><b>Any other fall:</b> once you sink into the clouds (below {@link Level490Layout#FALL_Y}),
 *       or land on something after falling {@value #BIG_FALL} blocks or more (off a ledge onto the
 *       island, off a ladder), the picture breaks up and you wake where you last used a radio, or
 *       on the arrival island if you never did. No damage: the level is hard enough.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level490Rules {
    /** A fall at least this long, landing on something, counts as falling off. */
    private static final float BIG_FALL = 12.0f;
    /** A fall that started this high is the leap (only the chain's collar is up there). */
    private static final int LEAP_FROM = Level490Spire.LEAP_Y - 2;
    /** Highest point of each player's current fall (reset whenever they stand, climb or swim). */
    private static final Map<UUID, Double> FALL_TOP = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_490) {
            FALL_TOP.remove(player.getUUID());
            return;
        }
        double y = player.getY();
        if (player.onGround() || player.onClimbable() || player.isInWater() || player.getAbilities().flying) {
            FALL_TOP.put(player.getUUID(), y);
        } else {
            FALL_TOP.merge(player.getUUID(), y, Math::max);
        }
        if (Travel.isMoving(player) || !player.isAlive()) {
            return;
        }
        // Stepped off the collar: the leap, as soon as you are falling (whatever you would land on).
        if (!player.onGround() && !player.onClimbable() && FALL_TOP.getOrDefault(player.getUUID(), y) >= LEAP_FROM
                && y < Level490Spire.LEAP_Y - 3 && !player.getAbilities().flying) {
            FALL_TOP.remove(player.getUUID());
            Travel.useExit(player, BackroomsLevel.LEVEL_490);
            return;
        }
        if (y >= Level490Layout.FALL_Y) {
            return;
        }
        fell(player);
    }

    /** Landing after a long fall: no damage, it counts as falling off (or as the leap, from the top). */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getDistance() < BIG_FALL
                || BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_490
                || player.isCreative() || player.isSpectator()) {
            return;
        }
        event.setCanceled(true);
        if (!Travel.isMoving(player)) {
            fell(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FALL_TOP.remove(event.getEntity().getUUID());
    }

    private static void fell(ServerPlayer player) {
        if (FALL_TOP.getOrDefault(player.getUUID(), player.getY()) >= LEAP_FROM) {
            FALL_TOP.remove(player.getUUID());
            Travel.useExit(player, BackroomsLevel.LEVEL_490);
            return;
        }
        // Back to your checkpoint on this level, or to the region's arrival island.
        ServerLevel level = player.serverLevel();
        CheckpointData checkpoint = player.getData(ModAttachments.CHECKPOINT);
        Vec3 spot;
        float yaw;
        if (checkpoint.isSet() && checkpoint.dimension().equals(level.dimension())) {
            spot = Vec3.atBottomCenterOf(checkpoint.pos());
            yaw = checkpoint.yaw();
        } else {
            BlockPos arrival = Travel.findSafeSpot(level, BackroomsLevel.LEVEL_490, player.getBlockX(), player.getBlockZ());
            spot = Vec3.atBottomCenterOf(arrival);
            yaw = player.getYRot();
        }
        Travel.moshTo(player, p -> {
            p.setDeltaMovement(Vec3.ZERO);
            Teleports.to(p, spot.x, spot.y, spot.z, yaw, 0.0f);
            FALL_TOP.put(p.getUUID(), spot.y);
            p.sendSystemMessage(Component.translatable("message.backrooms.level490.fell").withStyle(s -> s.withItalic(true)));
        });
    }

    private Level490Rules() {}
}
