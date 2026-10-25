package com.rickr.backrooms.camera;

import com.rickr.backrooms.entity.Watcher;
import com.rickr.backrooms.network.CameraSyncPayload;
import com.rickr.backrooms.network.CameraTogglePayload;
import com.rickr.backrooms.registry.ModAttachments;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side camcorder rules.
 *
 * <ul>
 *   <li>Inside the Backrooms the battery drains: a full battery lasts 40 minutes on its own.
 *       The flashlight adds 50% drain, NightShot adds 200%.</li>
 *   <li>Back in the real world it recharges fully in 2 minutes.</li>
 *   <li>At 0% the camera dies: flashlight and NightShot cut out and the view goes nearly black.</li>
 * </ul>
 */
public final class CameraService {
    private static final float DRAIN_PER_TICK = CameraData.MAX_BATTERY / (40 * 60 * 20);
    private static final float NIGHT_SHOT_EXTRA = 2.0f;
    private static final float FLASHLIGHT_EXTRA = 0.5f;
    private static final float RECHARGE_PER_TICK = CameraData.MAX_BATTERY / (2 * 60 * 20);
    /** Exit signal reaches 0 at this distance (blocks) and 1 when standing at the exit. */
    private static final double SIGNAL_RANGE = 256.0;

    public static CameraData get(ServerPlayer player) {
        return player.getData(ModAttachments.CAMERA);
    }

    public static void tick(ServerPlayer player) {
        CameraData data = get(player);
        BackroomsLevel level = BackroomsLevel.fromDimension(player.level().dimension());
        int signalStep = Math.max(0, data.lastSentSignal);

        if (level != null) {
            float multiplier = 1.0f + (data.nightShot() ? NIGHT_SHOT_EXTRA : 0.0f) + (data.flashlight() ? FLASHLIGHT_EXTRA : 0.0f);
            // Level 9: a Watcher nearby fries electronics, the battery drains 3x faster.
            if (level == BackroomsLevel.LEVEL_9 && Watcher.interferesWith(player)) {
                multiplier *= Watcher.BATTERY_DRAIN_MULTIPLIER;
            }
            data.setBattery(data.battery() - DRAIN_PER_TICK * multiplier);
            data.setTapeTicks(data.tapeTicks() + 1);
            if (data.battery() <= 15.0f && !data.warned15) {
                data.warned15 = true;
                player.playNotifySound(ModSounds.BATTERY_LOW.get(), SoundSource.MASTER, 0.8f, 1.0f);
            }
            if (data.battery() <= 5.0f && !data.warned5) {
                data.warned5 = true;
                player.playNotifySound(ModSounds.BATTERY_LOW.get(), SoundSource.MASTER, 1.0f, 0.8f);
            }
            if (player.tickCount % 10 == 0) {
                long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), level);
                int[] exit = level.layout().exits().nearest(seed, player.getBlockX(), player.getBlockZ());
                double distance = Math.sqrt(player.distanceToSqr(exit[0] + 0.5, player.getY(), exit[1] + 0.5));
                signalStep = Math.round((float) Math.max(0.0, 1.0 - distance / SIGNAL_RANGE) * 20.0f);
            }
        } else if (data.battery() < CameraData.MAX_BATTERY) {
            data.setBattery(data.battery() + RECHARGE_PER_TICK);
        }

        boolean toggled = data.nightShot() != data.lastSentNightShot || data.flashlight() != data.lastSentFlashlight;
        boolean changed = toggled
                || (int) (data.battery() * 10.0f) != data.lastSentBatteryTenths
                || signalStep != data.lastSentSignal;
        if (changed && (toggled || player.tickCount % 5 == 0)) {
            sync(player, data, signalStep);
        }
    }

    /** NightShot or flashlight key pressed. Nothing turns on with a dead battery. */
    public static void toggle(ServerPlayer player, int which) {
        CameraData data = get(player);
        if (data.dead()) {
            player.playNotifySound(ModSounds.BATTERY_LOW.get(), SoundSource.MASTER, 0.6f, 0.7f);
            return;
        }
        if (which == CameraTogglePayload.NIGHT_SHOT) {
            data.setNightShot(!data.nightShot());
            player.playNotifySound(ModSounds.REC_BEEP.get(), SoundSource.MASTER, 0.6f, data.nightShot() ? 1.2f : 0.9f);
        } else if (which == CameraTogglePayload.FLASHLIGHT) {
            data.setFlashlight(!data.flashlight());
            player.playNotifySound(ModSounds.FLASHLIGHT_CLICK.get(), SoundSource.MASTER, 0.8f, data.flashlight() ? 1.1f : 0.9f);
        }
        sync(player, data, data.lastSentSignal);
    }

    /** Swap in a fresh battery: back to 100%. */
    public static void replaceBattery(ServerPlayer player) {
        setBattery(player, CameraData.MAX_BATTERY);
    }

    public static void setBattery(ServerPlayer player, float percent) {
        CameraData data = get(player);
        data.setBattery(percent);
        player.playNotifySound(ModSounds.REC_BEEP.get(), SoundSource.MASTER, 1.0f, 1.0f);
        sync(player, data, data.lastSentSignal);
    }

    /** A fresh recording starts each time you noclip into a level. */
    public static void startTape(ServerPlayer player) {
        CameraData data = get(player);
        data.setTapeTicks(0);
        sync(player, data, data.lastSentSignal);
    }

    public static void forceSync(ServerPlayer player) {
        CameraData data = get(player);
        sync(player, data, data.lastSentSignal);
    }

    private static void sync(ServerPlayer player, CameraData data, int signalStep) {
        int step = Math.max(0, signalStep);
        data.lastSentBatteryTenths = (int) (data.battery() * 10.0f);
        data.lastSentNightShot = data.nightShot();
        data.lastSentFlashlight = data.flashlight();
        data.lastSentSignal = step;
        PacketDistributor.sendToPlayer(player,
                new CameraSyncPayload(data.battery(), data.nightShot(), data.flashlight(), step / 20.0f, data.tapeTicks()));
    }

    private CameraService() {}
}
