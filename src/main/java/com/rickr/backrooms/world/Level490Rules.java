package com.rickr.backrooms.world;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.player.CheckpointData;
import com.rickr.backrooms.registry.ModAttachments;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.layout.Level490Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server rules of Level 490, "Penance": falling.
 * <ul>
 *   <li><b>The leap:</b> stepping off the great round platform where the structure stands (within
 *       {@link Level490Layout#LEAP_REACH} of its centre) is the way out, to Level 9: the one fall
 *       that doesn't bring you back.</li>
 *   <li><b>Any other fall:</b> once you sink into the clouds (below {@link Level490Layout#FALL_Y})
 *       the picture breaks up and you wake where you last used a shelter's radio, or on the
 *       arrival platform if you never did. No damage: the level is hard enough.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Level490Rules {
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getY() >= Level490Layout.FALL_Y
                || BackroomsLevel.fromDimension(player.level().dimension()) != BackroomsLevel.LEVEL_490
                || Travel.isMoving(player) || !player.isAlive()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long seed = BackroomsSeeds.layoutSeed(level, BackroomsLevel.LEVEL_490);
        int[] site = Level490Layout.siteCentre(seed, player.getBlockX(), player.getBlockZ());
        if (Math.hypot(player.getX() - site[0], player.getZ() - site[1]) <= Level490Layout.LEAP_REACH) {
            Travel.useExit(player, BackroomsLevel.LEVEL_490);
            return;
        }
        // Back to your checkpoint on this level, or to the region's arrival platform.
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
            p.sendSystemMessage(Component.translatable("message.backrooms.level490.fell").withStyle(s -> s.withItalic(true)));
        });
    }

    private Level490Rules() {}
}
