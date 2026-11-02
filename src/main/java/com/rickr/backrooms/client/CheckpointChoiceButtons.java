package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.CheckpointChoicePayload;
import com.rickr.backrooms.player.SafeRoomClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Shared checkpoints on the death screen. When you die on a level where you have your own
 * checkpoint AND a teammate saved one you could use, the server tells us their name
 * ({@code CheckpointOptionsPayload} -> {@link SafeRoomClientState#offeredSharedName}) and we add
 * two buttons under Minecraft's own ones:
 * <ul>
 *   <li>"Respawn: my checkpoint"</li>
 *   <li>"Respawn: NAME's checkpoint"</li>
 * </ul>
 * Each one tells the server the choice and then respawns, just like the vanilla Respawn button
 * (which still works and keeps your own checkpoint).
 *
 * <p>We add the buttons from {@link ScreenEvent.Init.Post} (NeoForge calls it every time a screen
 * builds its buttons, also after a window resize) instead of changing Minecraft's DeathScreen.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class CheckpointChoiceButtons {
    /** Vanilla keeps its death screen buttons greyed out for the first second (20 ticks), so a
     *  click meant for the game doesn't respawn you by accident; ours do the same. */
    private static final int ENABLE_AFTER_TICKS = 20;
    /** Vanilla's buttons: 200 wide, Respawn at height/4 + 72, Title Screen 24 below it. Ours follow. */
    private static final int BUTTON_WIDTH = 200;
    private static final int FIRST_BUTTON_Y = 120;
    private static final int ROW = 24;

    /** The death screen our buttons are on (null when none is open) and how long it has been open. */
    private static DeathScreen screen;
    private static int ticksOpen;
    private static Button ownButton;
    private static Button sharedButton;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof DeathScreen deathScreen)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String name = SafeRoomClientState.offeredSharedName;
        // Hardcore has no respawning at all ("Spectate world"), so nothing to choose there.
        if (name == null || mc.level == null || mc.level.getLevelData().isHardcore()) {
            return;
        }
        if (deathScreen != screen) {
            screen = deathScreen; // a new death, not just a resize: start the 1-second wait again
            ticksOpen = 0;
        }
        int x = deathScreen.width / 2 - BUTTON_WIDTH / 2;
        int y = deathScreen.height / 4 + FIRST_BUTTON_Y;
        ownButton = Button.builder(Component.translatable("deathScreen.backrooms.respawn_own"), button -> choose(false))
                .bounds(x, y, BUTTON_WIDTH, 20).build();
        sharedButton = Button.builder(Component.translatable("deathScreen.backrooms.respawn_shared", name), button -> choose(true))
                .bounds(x, y + ROW, BUTTON_WIDTH, 20).build();
        boolean ready = ticksOpen >= ENABLE_AFTER_TICKS;
        ownButton.active = ready;
        sharedButton.active = ready;
        event.addListener(ownButton);
        event.addListener(sharedButton);
    }

    /** Counts the death screen's open time, and lets go of it once it closes. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (screen == null) {
            return;
        }
        if (Minecraft.getInstance().screen != screen) {
            forgetScreen();
            return;
        }
        ticksOpen++;
        if (ticksOpen == ENABLE_AFTER_TICKS) {
            ownButton.active = true;
            sharedButton.active = true;
        }
    }

    /** Tell the server which checkpoint, then ask to respawn (what vanilla's Respawn button does). */
    private static void choose(boolean shared) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ownButton.active = false; // one click only
        sharedButton.active = false;
        // Sent first, on the same connection as the respawn request, so the server sets the
        // respawn point before it respawns you.
        PacketDistributor.sendToServer(new CheckpointChoicePayload(shared));
        mc.player.respawn();
    }

    /** You respawned (Minecraft makes a new player object then): the offer is used up. */
    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.Clone event) {
        SafeRoomClientState.offeredSharedName = null;
    }

    /** Leaving the server: forget everything about this death. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SafeRoomClientState.offeredSharedName = null;
        forgetScreen();
    }

    private static void forgetScreen() {
        screen = null;
        ownButton = null;
        sharedButton = null;
    }

    private CheckpointChoiceButtons() {}
}
