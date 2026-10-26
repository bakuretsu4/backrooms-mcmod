package com.rickr.backrooms.client.menu;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/**
 * Swaps the vanilla title screen for {@link BackroomsTitleScreen}, and the menu music for the
 * Level 0 fluorescent hum.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class MenuEvents {
    /**
     * The Level 0 hum as menu "music": no pause between plays (min/max delay 0), so it loops, and
     * it replaces whatever music is playing.
     */
    private static final Music MENU_HUM = new Music(ModSounds.LEVEL0_HUM, 0, 0, true);

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
     * Out of a world (our menu and every screen opened from it: options, world list, servers...)
     * the hum plays instead of the vanilla menu music, so it doesn't cut in and out as you click
     * around. Once a world is loaded, the hum is stopped so it doesn't carry on into the game.
     */
    @SubscribeEvent
    public static void onSelectMusic(SelectMusicEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null && event.getOriginalMusic() == Musics.MENU) {
            event.setMusic(MENU_HUM);
            return;
        }
        Music music = event.getMusic();
        SoundInstance playing = event.getPlayingMusic();
        if (music != null && !music.replaceCurrentMusic() && playing != null
                && playing.getLocation().equals(ModSounds.LEVEL0_HUM.getId())) {
            // Same music, but allowed to cut the hum off (the game then waits its usual random delay).
            event.setMusic(new Music(music.getEvent(), music.getMinDelay(), music.getMaxDelay(), true));
        }
    }

    private MenuEvents() {}
}
