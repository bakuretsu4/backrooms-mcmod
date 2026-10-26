package com.rickr.backrooms.client.menu;

import com.rickr.backrooms.Backrooms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Swaps the vanilla title screen for {@link BackroomsTitleScreen}, and puts a "Backrooms
 * Settings..." button in the pause menu. (The menu music is chosen by
 * {@link com.rickr.backrooms.client.BackroomsMusic}.)
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class MenuEvents {
    /** Whenever Minecraft opens its title screen (game start, leaving a world...), open ours instead. */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen screen = event.getNewScreen();
        // Exactly TitleScreen, not a subclass: another mod's own title screen is left alone.
        if (screen != null && screen.getClass() == TitleScreen.class) {
            event.setNewScreen(new BackroomsTitleScreen());
        }
    }

    /**
     * The camera settings matter most while you're playing, so the pause menu gets a small button
     * in its top-left corner (out of the way of vanilla's buttons and other mods' ones in the middle).
     */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        // showsPauseMenu() is false for the F3+Esc "pause without menu" screen: no button there.
        if (event.getScreen() instanceof PauseScreen pause && pause.showsPauseMenu()) {
            event.addListener(Button.builder(Component.translatable("menu.backrooms.settings.pause_button"),
                            button -> Minecraft.getInstance().setScreen(new SettingsScreen(pause)))
                    .bounds(6, 6, 140, 20)
                    .build());
        }
    }

    private MenuEvents() {}
}
