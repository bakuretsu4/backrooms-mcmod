package com.rickr.backrooms.client.menu;

import com.rickr.backrooms.Backrooms;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Swaps the vanilla title screen for {@link BackroomsTitleScreen}. (The menu music is chosen by
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

    private MenuEvents() {}
}
