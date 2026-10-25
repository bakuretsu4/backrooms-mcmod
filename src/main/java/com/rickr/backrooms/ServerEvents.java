package com.rickr.backrooms;

import com.rickr.backrooms.camera.CameraService;
import com.rickr.backrooms.command.BackroomsCommand;
import com.rickr.backrooms.entity.BacteriaSpawner;
import com.rickr.backrooms.player.PlayerGameplay;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.LevelEvents;
import com.rickr.backrooms.world.NoclipIntro;
import com.rickr.backrooms.world.Travel;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-side game rules: noclipping in, falling through exits, camcorder ticking, spawning. */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class ServerEvents {

    /**
     * Noclipping: suffocating inside a block in the real world doesn't hurt you, it phases you out
     * of reality into Level 0, just like the original creepypasta.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getSource().is(DamageTypes.IN_WALL)
                && !Travel.inBackrooms(player.level())) {
            event.setCanceled(true);
            Travel.enterBackrooms(player); // no-op while the intro is already playing
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        CameraService.tick(player);
        NoclipIntro.tick(player);
        BackroomsLevel level = BackroomsLevel.fromDimension(player.level().dimension());
        if (level != null && player.getY() < level.transferBelowY()) {
            Travel.useExit(player, level);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BacteriaSpawner.tick(level);
            BackroomsLevel floor = BackroomsLevel.fromDimension(level.dimension());
            if (floor != null) {
                LevelEvents.tick(level, floor);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CameraService.forceSync(player);
            LevelEvents.sendCurrent(player);
            PlayerGameplay.forceSync(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NoclipIntro.forget(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CameraService.forceSync(player);
            LevelEvents.sendCurrent(player);
            PlayerGameplay.forceSync(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LevelEvents.sendCurrent(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LevelEvents.reset();
        BacteriaSpawner.reset();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BackroomsCommand.register(event.getDispatcher());
    }

    private ServerEvents() {}
}
